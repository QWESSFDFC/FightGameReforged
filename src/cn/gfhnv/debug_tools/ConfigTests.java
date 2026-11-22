package cn.gfhnv.debug_tools;

import cn.gfhnv.game.data.DataBridge;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.mod.Mod;
import cn.gfhnv.game.officialStuff.customEntity.monsters.CommonInsect;
import cn.gfhnv.game.officialStuff.customEntity.players.ActorLiXiaoYan;
import cn.gfhnv.game.officialStuff.customEntity.players.Phainon;
import cn.gfhnv.game.officialStuff.customEntity.players.PlayerOne;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.configLoadingSystem.DataKeys;
import cn.gfhnv.game.world.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static cn.gfhnv.debug_tools.TestKit.check;
import static cn.gfhnv.debug_tools.TestKit.section;
import static cn.gfhnv.debug_tools.TestSupport.*;

/**
 * 自测：**配置**。
 * <p>
 * 主题：配置加载：默认值、补丁、classState 键、模组内容隔离、反射面。
 * <p>
 * 由 {@code TestCommandSystem#main} 按顺序调用 {@code ConfigTests#runAll()}。
 */
public final class ConfigTests {

    private ConfigTests() {
    }

    /**
     * 配置键契约 + 默认值生成器 + 实体数据补丁（外部数据加载的阶段 0 与阶段 1）。
     * <p>
     * <b>三条护栏</b>（防的是"改名/加字段"这类静默失效）：
     * <ol>
     *     <li><b>键名契约</b>：{@link DataKeys} 里的键必须真的对应 {@code LivingThing} 上的字段
     *     （数据名被改名 → 这里立刻红），类型也要对得上；</li>
     *     <li><b>dump ↔ 配置</b>：{@code /data} 里 dump 出来的每个键，要么能配置、要么在只读清单里、
     *     要么是角色/模组类自己的状态（逐条列在 {@link #SUBCLASS_STATE_KEYS}）——
     *     新加属性忘了登记会被这条抓住；</li>
     *     <li><b>13 参构造器签名</b>：参数个数、类型序列、参数名全部钉死
     *     （模组 {@code DrunkenSwordsman} 在用，不能"顺手优化"）。</li>
     * </ol>
     * <b>补丁用例一律在真模板上跑，跑完用快照还原</b>：注册表里存的就是模板本身，
     * 只测副本证明不了"真模板会被改对"。每个用例结束都还原，
     * 最后再断言"全部模板的状态与开头逐字符一致"。
     */
    private static void testConfigDefaultsAndPatch() throws Exception {
        section("配置键契约与默认值生成器（阶段 0）");

        /* ---------- ① DataKeys 自洽 + 与 /data 字段对照 ---------- */
        List<String> structuralProblems = new ArrayList<>();
        if (DataKeys.META.size() != DataKeys.groups().size()) {
            structuralProblems.add("能配置的键数与分组表不一致：" + DataKeys.META.size()
                    + " vs " + DataKeys.groups().size());
        }
        if (!DataKeys.META.keySet().equals(DataKeys.groups().keySet())) {
            structuralProblems.add("能配置的键与分组表的键集合不一致");
        }
        if (DataKeys.groups().keySet().stream().anyMatch(DataKeys.READ_ONLY::contains)) {
            structuralProblems.add("有键既在能配置的清单里、又在只读清单里");
        }
        if (!DataKeys.READ_ONLY.containsAll(Arrays.asList("uuid", "id", "controller", "manas"))) {
            structuralProblems.add("只读清单里少了身份/行为键");
        }
        if (!DataKeys.NOT_IN_DATA.equals(new java.util.HashSet<>(
                Arrays.asList("manaGrow", "inventorySlots")))) {
            structuralProblems.add("「不在 /data 里的配置项」只有 manaGrow 块与背包格数，实际是 "
                    + DataKeys.NOT_IN_DATA);
        }
        for (Map.Entry<String, DataKeys.KeyMeta> entry : DataKeys.META.entrySet()) {
            if (entry.getValue().note() == null || entry.getValue().note().isEmpty()) {
                structuralProblems.add("键 " + entry.getKey() + " 没有写说明");
            }
            if (!DataKeys.NOT_IN_DATA.contains(entry.getKey()) && !entry.getValue().inData()) {
                structuralProblems.add("键 " + entry.getKey() + " 标了「不在 /data 里」，但不在 NOT_IN_DATA 清单里");
            }
        }
        check("键名契约：DataKeys 自洽（键集合 / 只读清单 / 说明 / 不在 /data 的项）—— 问题 "
                + structuralProblems, structuralProblems.isEmpty());

        Map<String, java.lang.reflect.Field> declared = DataKeys.auditAgainst(LivingThing.class);
        List<String> audit = DataKeys.auditNotes(LivingThing.class, declared);
        check("键名契约：每个「在 /data 里」的配置键都能找到对应字段、且类型一致"
                + "（数据名被改名会在这里红）—— 问题 " + audit, audit.isEmpty());

        check("键名契约：五行清单与 ElementSort 的枚举值一一对应（少写一个元素会在这里红）",
                DataKeys.ELEMENTS.size() == ElementSort.values().length - 1
                        && Arrays.stream(ElementSort.values())
                        .filter(sort -> !"UNIVERSAL".equals(sort.name()))
                        .allMatch(sort -> DataKeys.ELEMENTS.contains(sort.name().toLowerCase())));

        check("别名：只剩 manaGrow（element 2026-10-24 起就是规范名，不再有别名）",
                "element".equals(DataKeys.canonical("element", false))
                        && "manaGrow".equals(DataKeys.canonical("manaGrow", false))
                        && "metalManaGrow".equals(DataKeys.canonical("metal", true))
                        && DataKeys.canonical("nope", false) == null);

        /* ---------- ② 默认值生成器（只读，不碰磁盘） ---------- */
        String entityJson = cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter.entityDataJson();
        String skillJson = cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter.skillDataJson();
        Map<String, List<String>> entityKeys =
                cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter.lastEntityKeys();
        Map<String, List<String>> skillKeys =
                cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter.lastSkillKeys();

        org.json.JSONObject entityRoot = new org.json.JSONObject(entityJson);
        check("生成器：EntityData 能被 org.json 解析，且带 entities 与 version",
                entityRoot.optJSONObject("entities") != null && entityRoot.has("version"));
        // version 必须是**数字**：写成字符串会触发加载器那条"不认识的版本"警告（实测踩过）
        check("生成器：version 是数字 1（不是字符串 \"1\"，否则加载时会刷一条假警告）",
                cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher
                        .asLong(entityRoot.opt("version")) == 1L);
        org.json.JSONObject skillRoot = new org.json.JSONObject(skillJson);
        check("生成器：SkillData 能被 org.json 解析（它是只读样例，本版游戏不读它）",
                skillRoot.optJSONObject("skills") != null);

        List<String> missingInOutput = new ArrayList<>();
        List<String> missingFromDump = new ArrayList<>();
        List<String> notConfigurable = new ArrayList<>();
        for (LivingThing living : World.getLivingEntityList()) {
            String id = living.getId();
            List<String> written = entityKeys.get(id);
            if (written == null) {
                // 只对官方内容要求"必须出现"：模组内容由 ConfigDefaultWriter 刻意排除
                // （用户意见"模组配置不能放在这个里面"，见 #testModContentIsNotInGameConfig）。
                if (cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter
                        .isOfficialContent(living)) {
                    missingInOutput.add(id);
                }
                continue;
            }
            for (String key : written) {
                if (!DataKeys.META.containsKey(key)) {
                    notConfigurable.add(id + "/" + key);
                }
                // penetration / 五个抗性 2026-11-14 容器化：配置键还在（lambda 读写列表），但没有同名字段
                boolean tableOnly = "inventorySlots".equals(key) || "penetration".equals(key)
                        || key.endsWith("Resistance");
                if (!tableOnly && !declared.containsKey(key)) {
                    missingFromDump.add(id + "/" + key);
                }
            }
        }
        check("生成器：注册表里每个<b>官方</b>实体都出现在输出里（模组内容刻意不写）—— 缺 "
                + missingInOutput, missingInOutput.isEmpty());
        check("生成器：写出去的每个键都在 DataKeys 表里 —— 多 " + notConfigurable, notConfigurable.isEmpty());
        check("生成器：写出去的每个键在 /data 里都有对应字段 —— 缺 " + missingFromDump,
                missingFromDump.isEmpty());

        /* ---------- ②-b 子类配置字段（classState 段） ---------- */
        // 用户意见："白厄的配置是否忘了?为什么没有火种等配置?" ——
        // 这一段证明它<b>在</b>默认文件里、写进 JSON <b>真的生效</b>。
        org.json.JSONObject phainonEntry = entityRoot.getJSONObject("entities")
                .optJSONObject("game_official_content:phainon");
        org.json.JSONObject classStateSection =
                phainonEntry == null ? null : phainonEntry.optJSONObject("classState");
        check("生成器（classState）：白厄的模板有 classState 段（火种/上限/灾厄/燃点那些键就在那里）",
                classStateSection != null);
        java.util.Set<String> expectedPhainonClassKeys = new java.util.TreeSet<>(Arrays.asList(
                "coreflame", "coreflameMax", "soulscorch", "scourge", "scourgeMax",
                "extraAbilityTier", "eidolon", "energy", "energyMax", "energyOverflowMax"));
        check("生成器（classState）：白厄那一段写全了 " + expectedPhainonClassKeys.size()
                        + " 个键 —— 实际 "
                        + (classStateSection == null ? "没有这一段" : classStateSection.keySet()),
                classStateSection != null
                        && classStateSection.keySet().equals(expectedPhainonClassKeys));
        boolean numbersNotSnbt = true;
        if (classStateSection != null) {
            for (String key : classStateSection.keySet()) {
                if (!(classStateSection.opt(key) instanceof Number)) {
                    numbersNotSnbt = false;
                }
            }
        }
        check("生成器（classState）：值是 JSON 数字，不是 SNBT 字符串"
                        + "（写成 \"15L\" 的话加载器读回来就是类型不对）",
                numbersNotSnbt);
        List<String> classStateMissing = new ArrayList<>();
        if (classStateSection != null) {
            for (String key : classStateSection.keySet()) {
                if (!cn.gfhnv.game.system.configLoadingSystem.DataKeys.CLASS_CONFIG.contains(key)) {
                    classStateMissing.add(key + "（不在 CLASS_CONFIG 清单里）");
                }
                // 子类字段刻意不在 declared 里：那个表是 DataKeys.auditAgainst(LivingThing.class)
                // 扫出来的通用字段清单，而 coreflame 只在 Phainon 上存在。
                // 所以这里查的是"这个模板自己的反射面"。
                boolean onSurface = false;
                LivingThing template = entityTemplateOf("game_official_content:phainon");
                if (template != null) {
                    for (cn.gfhnv.game.data.DataBridge.DataAccessor accessor
                            : cn.gfhnv.game.system.configLoadingSystem.ReflectionConfigBridge
                            .candidates(template)) {
                        if (accessor.name().equals(key)) {
                            onSurface = true;
                        }
                    }
                }
                if (!onSurface) {
                    classStateMissing.add(key + "（在白厄的 /data 里没有对应字段）");
                }
            }
        }
        check("生成器（classState）：写出去的每个键都在 CLASS_CONFIG 清单里、且在模板自己的 /data 里有字段 —— 问题 "
                + classStateMissing, classStateMissing.isEmpty());
        // 别的模板不该凭空多出这一段（"只对某个子类存在"这件事必须体现出来）
        List<String> wrongTemplates = new ArrayList<>();
        for (String id : entityRoot.getJSONObject("entities").keySet()) {
            org.json.JSONObject entry = entityRoot.getJSONObject("entities").optJSONObject(id);
            org.json.JSONObject section = entry == null ? null : entry.optJSONObject("classState");
            if (section == null) {
                continue;
            }
            // 2026-10-24：千冶·刃也有了 classState（能量 / 充能），加进允许清单
            if ("game_official_content:phainon".equals(id)
                    || "game_official_content:actorLiXiaoYan".equals(id)
                    || "game_official_content:mortenax_blade".equals(id)
                    || "game_official_content:flameReaver".equals(id)) {
                continue;
            }
            wrongTemplates.add(id);
        }
        check("生成器（classState）：只有真有这些字段的模板才有这一段（别的模板凭空多出来就是红的）—— 多 "
                + wrongTemplates, wrongTemplates.isEmpty());

        check("生成器：每个实体写出去的键数一致（同一个基类，字段清单相同）—— "
                        + (entityKeys.isEmpty() ? 0 : entityKeys.values().iterator().next().size()),
                !entityKeys.isEmpty() && entityKeys.values().stream()
                        .allMatch(keys -> keys.size() == entityKeys.values().iterator().next().size()));

        List<String> missingSkills = new ArrayList<>();
        for (LivingThing living : World.getLivingEntityList()) {
            if (living.getController() == null) {
                continue;
            }
            for (Skill skill : living.getController().getSkills()) {
                String key = living.getId() + "#" + skill.getName();
                // 同上：模组实体的技能刻意不进 SkillData.json
                if (!cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter
                        .isOfficialSkillKey(key)) {
                    continue;
                }
                if (!skillKeys.containsKey(key) && !skillKeys.containsKey(key + "#2")) {
                    missingSkills.add(key);
                }
            }
        }
        check("生成器：每个<b>官方</b>技能的「实体id#技能名」都出现在输出里（技能改名会在这里红）—— 缺 "
                + missingSkills, missingSkills.isEmpty());

        /* ---------- ③ dump ↔ 配置（两个口径都要比，只比字段清单会假绿） ---------- */
        List<String> unregistered = new ArrayList<>();
        java.util.Set<String> observedSubclassKeys = new java.util.TreeSet<>();
        for (LivingThing living : World.getLivingEntityList()) {
            boolean subclass = living.getClass() != PlayerOne.class
                    && living.getClass() != CommonInsect.class;
            for (String key : cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter
                    .dump(living).values().keySet()) {
                if (DataKeys.isConfigurable(key) || DataKeys.READ_ONLY.contains(key)) {
                    continue;
                }
                if (subclass) {
                    observedSubclassKeys.add(key);
                    continue;
                }
                unregistered.add(living.getId() + "/" + key);
            }
        }
        check("dump 契约：基类身份上 dump 出来的键全都能配置或在只读清单里 —— 没登记的 " + unregistered,
                unregistered.isEmpty());
        // 这条是"反向"的：只读清单里写着、但今天已经 dump 不出来的键，说明它其实不是数据键了。
        // 今天有 8 个（tags / effects / damageReductions 这类空集合被省略，加上行为/物理对象），
        // 所以这里只打印不断言，免得把"清单偏保守"变成假失败。
        java.util.Set<String> dumpedAnywhere = new java.util.TreeSet<>();
        for (LivingThing living : World.getLivingEntityList()) {
            dumpedAnywhere.addAll(cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter
                    .dump(living).values().keySet());
        }
        List<String> readOnlyNeverDumped = new ArrayList<>(DataKeys.READ_ONLY);
        readOnlyNeverDumped.removeAll(dumpedAnywhere);
        System.out.println("  [信息] 只读清单里今天没 dump 出来的键（空集合被省略 / 行为与物理对象）："
                + readOnlyNeverDumped);
        check("dump 契约：只读清单本身没有退化（uuid / id / controller 必须仍在清单里）",
                readOnlyNeverDumped.size() < DataKeys.READ_ONLY.size()
                        && readOnlyNeverDumped.contains("tags"));

        List<String> unexpectedSubclass = new ArrayList<>(observedSubclassKeys);
        unexpectedSubclass.removeAll(SUBCLASS_STATE_KEYS);
        check("dump 契约：角色/召唤物类自己的状态键没有冒出新面孔（新增就登记进 SUBCLASS_STATE_KEYS）—— 新 "
                + unexpectedSubclass, unexpectedSubclass.isEmpty());
        check("dump 契约：那几个已知的子类状态键确实还在（清单没烂掉）—— 实扫 "
                        + observedSubclassKeys,
                observedSubclassKeys.containsAll(Arrays.asList("ignition", "coreflame", "phaseTwo", "kind")));

        /* ---------- ④ 13 参构造器签名（模组在用，一个字都不许动） ---------- */
        // 注意：这个构造器**不是 public**（模组靠继承拿到它），所以必须用 getDeclaredConstructors()；
        // 另外 @DataFlatten 组件会让编译器在构造器的形参表前面塞一个隐藏参数
        // （getParameterTypes() 比 getParameters() 多算一个），所以按 getParameters() 数。
        // 找法按"形状"而不是按数字：这个构造器的头两个参数是名称与 id，最后一个参数是元素属性。
        // （数字在这里不可靠：@DataFlatten 组件让编译器插了一个合成参数，而这个参数在两个反射 API
        //   里的表现不一致 —— getParameterTypes() 不含它、getParameters() 含它。）
        java.lang.reflect.Constructor<?>[] constructors = LivingThing.class.getDeclaredConstructors();
        java.lang.reflect.Constructor<?> thirteen = null;
        for (java.lang.reflect.Constructor<?> candidate : constructors) {
            Class<?>[] types = candidate.getParameterTypes();
            if (types.length < 13 || types[0] != String.class || types[types.length - 1] != ElementSort.class) {
                continue;
            }
            if (thirteen == null || types.length > thirteen.getParameterTypes().length) {
                thirteen = candidate;
            }
        }
        check("构造器契约：13 参构造器还在（模组 DrunkenSwordsman 用它）—— 实际构造器 "
                + constructors.length + " 个", thirteen != null);
        if (thirteen == null) {
            return;
        }
        Class<?>[] expectedTypes = {String.class, String.class, double.class, double.class, double.class,
                double.class, double.class, long.class, long.class, String.class, double.class,
                double.class, double.class, ElementSort.class};
        Class<?>[] actualTypes = thirteen.getParameterTypes();
        boolean sameTypes = actualTypes.length == expectedTypes.length;
        for (int i = 0; sameTypes && i < expectedTypes.length; i++) {
            if (actualTypes[i] != expectedTypes[i]) {
                sameTypes = false;
            }
        }
        check("构造器契约：参数类型序列逐位一致（顺序改了，模组就传错位置）—— 实际 "
                + Arrays.toString(actualTypes), sameTypes);
        String[] expectedNames = {"name", "id", "fireResistance", "waterResistance", "metalResistance",
                "woodResistance", "dirtResistance", "speed", "l", "type", "hp", "atk", "defence", "yu"};
        java.lang.reflect.Parameter[] parameters = thirteen.getParameters();
        int nameOffset = parameters.length - expectedNames.length;
        boolean namesPresent = parameters.length > 0 && parameters[parameters.length - 1].isNamePresent();
        if (!namesPresent) {
            System.out.println("  [信息] 这份 class 没保留真实形参名（反射读到的是 " + parameters[0].getName()
                    + " 这种占位名）→ 参数名那条断言退化成「名字非空且互不相同」，类型与顺序仍逐位钉住");
        }
        boolean namesOk = true;
        java.util.Set<String> seenNames = new java.util.TreeSet<>();
        for (int i = 0; i < expectedNames.length; i++) {
            String actual = parameters[i + nameOffset].getName();
            if (actual == null || actual.isEmpty() || !seenNames.add(actual)) {
                namesOk = false;
            }
            if (namesPresent && !expectedNames[i].equals(actual)) {
                namesOk = false;
            }
        }
        check("构造器契约：参数名" + (namesPresent ? "逐位一致" : "非空且互不相同（class 里没保留真名，退化成结构检查）")
                + "（speed 与 l 类型相同，只比类型抓不住对调）—— 实际 "
                + Arrays.toString(Arrays.copyOfRange(parameters, nameOffset, parameters.length)), namesOk);

        /* ---------- ⑤ 补丁器：真模板上跑，跑完还原 ---------- */
        section("实体数据补丁（阶段 1）");

        Map<String, String> beforeAll = new java.util.LinkedHashMap<>();
        Map<String, cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Snapshot> snapshots =
                new java.util.LinkedHashMap<>();
        for (LivingThing living : World.getLivingEntityList()) {
            beforeAll.put(living.getId(),
                    cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.stateOf(living));
            snapshots.put(living.getId(),
                    cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Snapshot.of(living));
        }
        check("前提：注册表里有实体模板可供打补丁 —— " + beforeAll.size() + " 个", beforeAll.size() >= 9);

        try {
            /* ⑤-1 打一遍"当前全量默认值"：结果必须与打之前逐字符一致（"不写配置文件数值一个都不变"） */
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Report all =
                    cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher
                            .applyJson(entityJson, "自测-全量默认值");
            check("补丁：全量默认值一个键都不落地报错 —— 错误 " + all.errors(), all.errors().isEmpty());
            check("补丁：全量默认值没有跳过项 —— 跳过 " + all.skippedEntries(), all.skippedEntries().isEmpty());
            check("补丁：每个模板都被补到（含两种容器）—— 影响 " + all.patchedTemplates() + " 个模板",
                    all.patchedTemplates() == beforeAll.size());
            List<String> changed = new ArrayList<>();
            for (LivingThing living : World.getLivingEntityList()) {
                String now = cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.stateOf(living);
                if (!now.equals(beforeAll.get(living.getId()))) {
                    changed.add(living.getId() + "\n      旧 " + beforeAll.get(living.getId())
                            + "\n      新 " + now);
                }
            }
            check("补丁：把当前默认值打回去，数值逐字段不变（默认文件与构造器算出来的一致）—— 变了 "
                    + changed, changed.isEmpty());

            /* ⑤-2 只改速度：选人列表与战斗里都该是 200，其余字段不受影响 */
            LivingThing probe = entityTemplateOf("game_official_content:playerOne");
            long oldSpeed = probe.getSpeed();
            long oldHpMax = probe.getHpMax();
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Report one =
                    cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.applyJson(
                            "{\"entities\":{\"game_official_content:playerOne\":{\"base\":{\"baseSpeed\":200}}}}",
                            "自测-只改速度");
            check("补丁：只写一个键时，没有报错也没有跳过 —— " + one.errors() + one.skippedEntries(),
                    one.isClean() && one.appliedEntries().size() == 1);
            check("补丁：速度真的变成 200（旧 " + oldSpeed + "）", probe.getSpeed() == 200L);
            check("补丁：没写的字段一个字都没动（生命上限仍是 " + oldHpMax + "）",
                    probe.getHpMax() == oldHpMax);
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Snapshot.of(probe).restore();

            /* ⑤-3 改成长系数必须连带重算：派生值只在 setLevel 里算，不补一次就是"改了没反应"
             *     （注意**不要**同时写 derived.baseHpMax —— 那是有意覆盖公式结果的写法，
             *      会把重算出来的值再盖回去，看起来就像"改了没反应"） */
            LivingThing growth = entityTemplateOf("game_official_content:playerOne");
            long hpMaxBefore = growth.getHpMax();
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Report grown =
                    cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.applyJson(
                            "{\"entities\":{\"game_official_content:playerOne\":{\"base\":{\"hpGrow\":50.0}}}}",
                            "自测-改成长");
            check("补丁：只写成长系数时自动补了一次重算（报告里有 base.hpGrow）—— " + grown.appliedEntries(),
                    grown.isClean() && !grown.appliedEntries().isEmpty()
                            && grown.appliedEntries().get(0).contains("base.hpGrow"));
            check("补丁：改了 hpGrow，生命上限跟着变（旧 " + hpMaxBefore + " → 新 " + growth.getHpMax()
                            + "，公式 (等级-1)×50+200 = " + ((growth.getLevel() - 1) * 50 + 200) + "）",
                    growth.getHpMax() != hpMaxBefore
                            && growth.getHpMax() == (growth.getLevel() - 1) * 50 + 200);
            // 换一个成长系数再来一次：证明每次改成长都会重算，而不是"只生效一次"
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.applyJson(
                    "{\"entities\":{\"game_official_content:playerOne\":"
                            + "{\"base\":{\"attackGrow\":100.0}}}}", "自测-改攻击成长");
            check("补丁：改 attackGrow 也会重算攻击（旧 " + hpMaxBefore + " 那次之后 = "
                            + growth.getAttack() + "）",
                    growth.getAttack() == 110 + 100 * (growth.getLevel() - 1));
            snapshots.get("game_official_content:playerOne").restore();

            /* ⑤-3b D3 回归：派生值重算必须**读规则表**，而不是把 200 / 110 / 200 再抄一遍。
             *        抄字面量的后果：用户改了 GameRules.json 的 formula.hpBase 之后，
             *        "只写 hpGrow"的实体还是按旧公式算 —— 规则表在这条路径上失效。 */
            LivingThing ruleDriven = entityTemplateOf("game_official_content:playerOne");
            cn.gfhnv.game.system.configLoadingSystem.GameRules.beginLoad();
            cn.gfhnv.game.system.configLoadingSystem.GameRulesPatcher.applyJson(
                    "{\"version\":1,\"formula\":{\"hpBase\":400,\"attackBase\":40,\"defenceBase\":900}}",
                    "自测-重算走规则表");
            cn.gfhnv.game.system.configLoadingSystem.GameRules.freeze();
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.applyJson(
                    "{\"entities\":{\"game_official_content:playerOne\":{\"base\":{\"hpGrow\":10.0}}}}",
                    "自测-重算走规则表");
            long ruleLevel = ruleDriven.getLevel();
            long expectHp = (long) ((ruleLevel - 1) * ruleDriven.getHpGrow() + 400);
            long expectDefence = (long) ((ruleLevel - 1) * ruleDriven.getDefenceGrow() + 900);
            long expectAttack = (long) (40 + ruleDriven.getAttackGrow() * (ruleLevel - 1));
            check("重算（D3）：派生重算读的是规则表里的 formula.*，不是硬编码的 200/110/200"
                            + "（hpMax=" + ruleDriven.getHpMax() + " 期望 " + expectHp
                            + "；防御=" + ruleDriven.getDefence() + " 期望 " + expectDefence
                            + "；攻击=" + ruleDriven.getAttack() + " 期望 " + expectAttack + "）",
                    ruleDriven.getHpMax() == expectHp
                            && ruleDriven.getDefence() == expectDefence
                            && ruleDriven.getAttack() == expectAttack);
            // 规则表是全局静态的：用完必须清干净，否则后面「规则表是空的」那条前提会红
            cn.gfhnv.game.system.configLoadingSystem.GameRules.resetForTest();
            snapshots.get("game_official_content:playerOne").restore();

            LivingThing derived = entityTemplateOf("game_official_content:playerOne");
            long attackBeforeDerived = derived.getAttack();
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.applyJson(
                    "{\"entities\":{\"game_official_content:playerOne\":"
                            + "{\"derived\":{\"baseHpMax\":12345,\"baseAttack\":777}}}}", "自测-派生值");
            check("补丁：derived 的固定值覆盖公式结果（hpMax=" + derived.getHpMax()
                            + "，attack=" + derived.getAttack() + "，旧攻击 " + attackBeforeDerived + "）",
                    derived.getHpMax() == 12345L && derived.getAttack() == 777L);
            // setHp 会夹到 getHpMax()，所以"先设上限、后设当前血量"是唯一正确的顺序
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.applyJson(
                    "{\"entities\":{\"game_official_content:playerOne\":"
                            + "{\"derived\":{\"hp\":99999}}}}", "自测-血量顺序");
            check("补丁：只写 hp 时被现有上限夹住（顺序写死：baseHpMax 先、hp 后）—— hp="
                            + derived.getHp() + "，上限=" + derived.getHpMax(),
                    derived.getHp() == derived.getHpMax());
            snapshots.get("game_official_content:playerOne").restore();

            /* ⑤-4 副本必须带上补丁值（补丁打在模板上，copy() 白送） */
            LivingThing template = entityTemplateOf("game_official_content:playerOne");
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.applyJson(
                    "{\"entities\":{\"game_official_content:playerOne\":"
                            + "{\"base\":{\"fireResistance\":0.85}}}}", "自测-副本火抗");
            LivingThing copy = template.copy();
            check("补丁：copy() 出来的副本带上了补丁值（火抗 0.85）—— 副本火抗 "
                            + copy.getFireResistance() + "、模板火抗 " + template.getFireResistance(),
                    copy.getFireResistance() == 0.85 && template.getFireResistance() == 0.85);
            check("补丁：副本与模板是同一个类的不同实例（没有把模板本身交出去）",
                    copy != template && copy.getClass() == template.getClass());
            snapshots.get("game_official_content:playerOne").restore();

            /* ⑤-5 容错：未知键 / 类型不对 / 找不到实体 —— 报清楚，但都不断整份加载 */
            LivingThing bad = entityTemplateOf("game_official_content:playerOne");
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Report broken =
                    cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.applyJson(
                            "{\"entities\":{"
                                    + "\"game_official_content:playerOne\":{\"base\":{\"baseSpeed\":\"快\",\"nope\":1}},"
                                    + "\"game_official_content:noSuchEntity\":{\"base\":{\"baseSpeed\":1}},"
                                    + "\"commonInsect\":{\"base\":{\"baseSpeed\":95}}"
                                    + "}}", "自测-容错");
            String brokenText = broken.errors() + " " + broken.skippedEntries();
            check("容错：一个坏键不再废掉整份配置（同一份里其余项照常生效）—— 跳过 "
                            + broken.skippedEntries() + "，错误 " + broken.errors(),
                    broken.skippedEntries().size() == 2 && broken.errors().size() == 1
                            && broken.appliedEntries().size() == 1);
            check("容错：类型不对的项被点名道姓（键名 + 实际类型）",
                    brokenText.contains("base.baseSpeed") && brokenText.contains("字符串"));
            check("容错：未知键被点名（不再静默忽略）", brokenText.contains("base.nope"));
            check("容错：找不到的实体也被点名（改名 = 配置静默失效，这里要看得见）",
                    brokenText.contains("noSuchEntity"));
            check("容错：短名解析成功（注册表里只有一条 id 以它结尾）",
                    entityTemplateOf("game_official_content:commonInsect").getSpeed() == 95L);
            check("容错：坏值没有被写进去（速度还是原样）", bad.getSpeed() == 120L);
            snapshots.get("game_official_content:commonInsect").restore();

            /* ⑤-5b D1/D2 + Q2 回归：那批"看着像能配、其实这一版刻意不支持"的键
             *        必须显式报出来并说清理由 —— 不许静默，也不许再谎报"能配置"。
             *        摘掉的是：五元素 *Penetration / *DamageEnhance（临时属性，打完一局清零）
             *        与已删除的 extraDamage / individualMultipleArea 标量。 */
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Report notSupported =
                    cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.applyJson(
                            "{\"entities\":{\"game_official_content:playerOne\":{\"base\":"
                                    + "{\"metalPenetration\":0.5},"
                                    + "\"temporary\":{\"baseCriticalRate\":0.5}}}}",
                            "自测-不支持的键");
            String notSupportedText = notSupported.skippedEntries().toString();
            check("不支持键：五元素穿透被点名，而且说清它是「临时属性、配了不生效」"
                            + "（文案 2026-10-03 订正：老文案谎称\"只影响开局那一刻\"）",
                    notSupportedText.contains("metalPenetration")
                            && notSupportedText.contains("临时属性")
                            && notSupportedText.contains("配了不生效"));
            check("不支持键：这些键不再出现在「能配置」清单里（不再谎报能配）",
                    !cn.gfhnv.game.system.configLoadingSystem.DataKeys
                            .isConfigurable("metalPenetration")
                            && !cn.gfhnv.game.system.configLoadingSystem.DataKeys
                            .isConfigurable("dirtDamageEnhance")
                            && !cn.gfhnv.game.system.configLoadingSystem.DataKeys
                            .isConfigurable("extraDamage"));
            check("不支持键（D2）：写 temporary 块时得到的是「它不是配置块」，不是误导性的「未知键」",
                    notSupportedText.contains("不是一个配置块"));
            snapshots.get("game_official_content:playerOne").restore();

            /* ⑤-5c D4：TagConfig 的「出厂默认值」只有 Java 里那一份，钉住它的形状 ——
             *        它被改坏时会红，而用户合法地改了自己的 TagConfig.json 不会红。 */
            org.json.JSONObject tagsDefault = new org.json.JSONObject(
                    cn.gfhnv.game.system.configLoadingSystem.ConfigLoader.tagsConfigTemplate());
            int tagPairs = 0;
            for (String tagId : tagsDefault.keySet()) {
                tagPairs += tagsDefault.getJSONObject(tagId).keySet().size();
            }
            check("TagConfig 出厂默认值（D4）：7 个 id / 26 个键值对"
                            + "（删掉 TagConfig.json 时会照着它重建）—— 实际 "
                            + tagsDefault.keySet().size() + " 个 id、" + tagPairs + " 个键值对",
                    tagsDefault.keySet().size() == 7 && tagPairs == 26);

            /* ⑤-6 表驱动（阶段 2）：那 15 个"声明了能配、其实没人读"的键已经不再是两件事 ——
             *      EntityKeySpecs 里 write != null 的每一行都必须真的能被补丁器读回来。
             *      这是**反向断言**：以前只有"生成器写出去的 包含 META"，方向恰好是反的，
             *      所以"声明了不生效"永远绿。 */
            LivingThing specProbe = entityTemplateOf("game_official_content:playerOne");
            List<String> unreadable = new ArrayList<>();
            for (cn.gfhnv.game.system.configLoadingSystem.KeySpec<LivingThing> key
                    : cn.gfhnv.game.system.configLoadingSystem.EntityKeySpecs.ALL) {
                if (key.write() == null || key.kind()
                        == cn.gfhnv.game.system.configLoadingSystem.KeySpec.Kind.INVENTORY) {
                    continue;   // 块（manaGrow / inventorySlots）走专用分支，下面单独测
                }
                Object current = key.read() == null ? null : key.read().apply(specProbe);
                if (current == null) {
                    continue;   // 这个键没有默认值（例如 description 为空）
                }
                org.json.JSONObject single = new org.json.JSONObject();
                single.put("base", new org.json.JSONObject().put(key.name(), current));
                cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Report perKey =
                        new cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Report("自测-逐键");
                cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher
                        .patch(specProbe, single, perKey, "game_official_content:playerOne");
                if (!perKey.skippedEntries().isEmpty() || perKey.appliedEntries().isEmpty()) {
                    unreadable.add(key.name() + "：" + perKey.skippedEntries());
                }
            }
            check("表驱动：EntityKeySpecs 里每一行都真的读得回来"
                            + "（「声明能配却没人读」的键会在这里红）—— 读不回来的：" + unreadable,
                    unreadable.isEmpty());
            // 生成器那一侧：表里"读得出值"的每一行都必须出现在默认文件里（反向也要成立）
            org.json.JSONObject oneEntity = entityRoot.getJSONObject("entities")
                    .getJSONObject("game_official_content:playerOne");
            List<String> writtenKeys = new ArrayList<>();
            for (String section : new String[]{"base", "derived"}) {
                org.json.JSONObject block = oneEntity.optJSONObject(section);
                if (block != null) {
                    writtenKeys.addAll(block.keySet());
                }
            }
            List<String> notWritten = new ArrayList<>();
            for (cn.gfhnv.game.system.configLoadingSystem.KeySpec<LivingThing> key
                    : cn.gfhnv.game.system.configLoadingSystem.EntityKeySpecs.ALL) {
                if (key.read() != null && !writtenKeys.contains(key.name())) {
                    notWritten.add(key.name());
                }
            }
            check("表驱动：表里读得出值的每一行都出现在默认文件里（两边同一张表）—— 缺 " + notWritten,
                    writtenKeys.size() == 29 && notWritten.isEmpty());
            check("表驱动：DataKeys.META / groups() 与 EntityKeySpecs 是同一份（不许再手写第二张）",
                    cn.gfhnv.game.system.configLoadingSystem.DataKeys.META
                            .equals(cn.gfhnv.game.system.configLoadingSystem.EntityKeySpecs.meta())
                            && cn.gfhnv.game.system.configLoadingSystem.DataKeys.groups()
                            .equals(cn.gfhnv.game.system.configLoadingSystem.EntityKeySpecs.groups()));
            snapshots.get("game_official_content:playerOne").restore();

            /* ⑤-6b 五个面板键（Q2 留下的那一半）必须真的生效，而且 copy() 会带过去 ——
             *      它们是"面板属性"：配在模板上整局生效，不是打完一局就没的临时属性。 */
            LivingThing panel = entityTemplateOf("game_official_content:playerOne");
            double oldRate = panel.getCriticalRate();
            double oldDmg = panel.getCriticalDamage();
            double oldPenetration = panel.getPenetration();
            double oldLoss = panel.getDefenceLoss();
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.applyJson(
                    "{\"entities\":{\"game_official_content:playerOne\":{\"base\":"
                            + "{\"baseCriticalRate\":0.5,\"baseCriticalDamage\":1.5,"
                            + "\"penetration\":0.3,\"defenceLoss\":0.1}}}}", "自测-面板键");
            check("面板键：baseCriticalRate / baseCriticalDamage / penetration / defenceLoss 真的写进去了"
                            + "（以前它们在 META 里声明能配，写进去一点反应都没有）",
                    panel.getCriticalRate() == 0.5 && panel.getCriticalDamage() == 1.5
                            && panel.getPenetration() == 0.3 && panel.getDefenceLoss() == 0.1);
            LivingThing panelCopy = panel.copy();
            check("面板键：copy() 会把它们带过去（面板属性，不是打完一局就清零的临时属性）",
                    panelCopy.getCriticalRate() == 0.5 && panelCopy.getPenetration() == 0.3);
            panel.setCriticalRate(oldRate);
            panel.setCriticalDamage(oldDmg);
            panel.setPenetration(oldPenetration);
            panel.setDefenceLoss(oldLoss);
            snapshots.get("game_official_content:playerOne").restore();

            /* ⑤-6c 五行法力成长：五个扁平键以前只出现在默认文件里、补丁器从来不看它们
             *      （写进配置毫无反应）；manaGrow 块则一直有效。两条路都要能用。 */
            LivingThing growProbe = entityTemplateOf("game_official_content:playerOne");
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.applyJson(
                    "{\"entities\":{\"game_official_content:playerOne\":{\"base\":"
                            + "{\"metalManaGrow\":7.0,\"woodManaGrow\":8.0,\"waterManaGrow\":9.0,"
                            + "\"fireManaGrow\":10.0,\"dirtManaGrow\":11.0}}}}", "自测-扁平法力成长");
            check("五行法力成长：五个扁平键都能改（默认文件写的就是它们，改了必须有反应）—— "
                            + growProbe.getMetalManaGrow() + "/" + growProbe.getWoodManaGrow() + "/"
                            + growProbe.getWaterManaGrow() + "/" + growProbe.getFireManaGrow() + "/"
                            + growProbe.getDirtManaGrow(),
                    growProbe.getMetalManaGrow() == 7.0 && growProbe.getWoodManaGrow() == 8.0
                            && growProbe.getWaterManaGrow() == 9.0 && growProbe.getFireManaGrow() == 10.0
                            && growProbe.getDirtManaGrow() == 11.0);
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.applyJson(
                    "{\"entities\":{\"game_official_content:playerOne\":"
                            + "{\"manaGrow\":{\"metal\":3.5}}}}", "自测-法力成长块");
            check("五行法力成长：manaGrow 块写法仍然有效（metal → 3.5）—— 实际 "
                            + growProbe.getMetalManaGrow(),
                    growProbe.getMetalManaGrow() == 3.5);
            snapshots.get("game_official_content:playerOne").restore();

            /* ⑤-6 每个模板只补一次：同一轮里重复命中同一个模板时，第二遍必须被挡住
             *     （补丁里有 setLevel → initialMana()，那个方法会把整份法力列表重建，
             *      跑两次就是把打到一半的法力重置）。
             *     注意这里要**复用同一个 Report** —— {@code applyJson} 每次都会新建一份报告，
             *      去重表就没了；真实加载只有一个 Report，正是靠它兜住。 */
            LivingThing repeatProbe = entityTemplateOf("game_official_content:playerOne");
            org.json.JSONObject repeatPatch = new org.json.JSONObject(
                    "{\"base\":{\"hpGrow\":77.0}}");
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Report sameRun =
                    new cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Report("自测-只补一次");
            check("补丁：同一轮里第一次命中模板时返回 true（可以补）",
                    sameRun.markBound("game_official_content:playerOne", repeatProbe));
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher
                    .patch(repeatProbe, repeatPatch, sameRun, "game_official_content:playerOne");
            long hpMaxAfterFirst = repeatProbe.getHpMax();
            int appliedAfterFirst = sameRun.appliedEntries().size();
            boolean boundAgain = sameRun.markBound("game_official_content:playerOne", repeatProbe);
            if (boundAgain) {
                // 只有"同一轮里又命中同一个模板"才该继续补 —— 这里模拟的就是加载器被重复调用
                cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher
                        .patch(repeatProbe, repeatPatch, sameRun, "game_official_content:playerOne");
            }
            check("补丁：一轮里同一个模板只补一次（重复应用会把法力列表重建两次）—— 第一遍应用 "
                            + appliedAfterFirst + " 项、hpMax=" + hpMaxAfterFirst
                            + "；第二次 markBound=" + boundAgain + "，之后应用 " + sameRun.appliedEntries().size()
                            + " 项，hpGrow=" + repeatProbe.getHpGrow()
                            + " hpMax=" + repeatProbe.getHpMax(),
                    appliedAfterFirst == 1 && !boundAgain && sameRun.appliedEntries().size() == 1
                            && repeatProbe.getHpGrow() == 77.0
                            && repeatProbe.getHpMax() == hpMaxAfterFirst);
            snapshots.get("game_official_content:playerOne").restore();

            /* ⑤-7 默认值落盘（"只在文件缺失时写、绝不覆盖用户文件"）—— 全程在 out/tmpConfig 里跑，
             *     真正的 config/ 目录一个字都不碰。 */
            java.io.File tempDir = new java.io.File("./out/tmpConfig");
            deleteDirectory(tempDir);
            List<String> written = cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter
                    .writeAll(tempDir);
            java.io.File entityFile = new java.io.File(tempDir,
                    cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter.ENTITY_FILE_NAME);
            java.io.File skillFile = new java.io.File(tempDir,
                    cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter.SKILL_FILE_NAME);
            java.io.File rulesFile = new java.io.File(tempDir,
                    cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter.RULES_FILE_NAME);
            check("落盘：缺文件时写出 EntityData.json / SkillData.json / GameRules.json / 参考副本 —— 实际 "
                            + written.size() + " 个",
                    written.size() == 4 && entityFile.isFile() && skillFile.isFile()
                            && rulesFile.isFile());
            String firstText = java.nio.file.Files.readString(entityFile.toPath(),
                    java.nio.charset.StandardCharsets.UTF_8);
            check("落盘：写出来的是 UTF-8 的中文（同一份字节用 UTF-8 读得回来，不是乱码）",
                    firstText.contains("玩家一") && !firstText.contains("\uFFFD"));
            check("落盘：写出来的文本能被 org.json 解析",
                    new org.json.JSONObject(firstText).optJSONObject("entities") != null);
            // 留一份"刚写出来的完整文本"：后面自愈守卫要拿它当"已经全了"的对照物
            String firstSkillText = java.nio.file.Files.readString(skillFile.toPath(),
                    java.nio.charset.StandardCharsets.UTF_8);
            String firstRulesText = java.nio.file.Files.readString(rulesFile.toPath(),
                    java.nio.charset.StandardCharsets.UTF_8);
            // D5（自愈）：把两份改成"用户只改了一个键"的样子，再 writeAll ——
            //     缺的键要补齐，**已有的值一个字节都不许动**
            java.nio.file.Files.writeString(entityFile.toPath(),
                    "{\"entities\":{\"game_official_content:playerOne\":{\"base\":{\"baseSpeed\":321}}}}",
                    java.nio.charset.StandardCharsets.UTF_8);
            java.nio.file.Files.writeString(rulesFile.toPath(),
                    "{\"version\":1,\"formula\":{\"hpBase\":999}}",
                    java.nio.charset.StandardCharsets.UTF_8);
            List<String> second = cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter
                    .writeAll(tempDir);
            org.json.JSONObject healedEntity = new org.json.JSONObject(java.nio.file.Files
                    .readString(entityFile.toPath(), java.nio.charset.StandardCharsets.UTF_8));
            org.json.JSONObject healedRules = new org.json.JSONObject(java.nio.file.Files
                    .readString(rulesFile.toPath(), java.nio.charset.StandardCharsets.UTF_8));
            check("落盘自愈（D5）：写了半个键的文件会被补齐（缺啥补啥）—— 这一轮写了 " + second.size()
                            + " 个；补完 entities 里有 "
                            + healedEntity.getJSONObject("entities").keySet().size() + " 个实体、规则有 "
                            + healedRules.keySet() + " 段",
                    healedEntity.getJSONObject("entities").keySet().size() >= 9
                            && healedRules.has("mana") && healedRules.has("formula"));
            check("落盘自愈（D5）：用户已经写的值一个字都不许覆盖（speed 321 / hpBase 999 原样）—— 实际 "
                            + healedEntity.getJSONObject("entities")
                            .getJSONObject("game_official_content:playerOne")
                            .getJSONObject("base").opt("baseSpeed") + " / "
                            + healedRules.getJSONObject("formula").opt("hpBase"),
                    healedEntity.getJSONObject("entities")
                            .getJSONObject("game_official_content:playerOne")
                            .getJSONObject("base").optInt("baseSpeed") == 321
                            && healedRules.getJSONObject("formula").optInt("hpBase") == 999);
            List<String> third = cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter
                    .writeAll(tempDir);
            check("落盘自愈（D5）：补全之后再启动不会反复改用户的文件（现在连参考副本也不写了 —— "
                            + "它已经有内容）—— 这一轮写了 " + third.size() + " 个",
                    third.isEmpty());
            // 内容坏掉的文件不许"替他修"：一个字节都不动
            java.nio.file.Files.writeString(entityFile.toPath(), "{ 这不是 JSON ",
                    java.nio.charset.StandardCharsets.UTF_8);
            cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter.writeAll(tempDir);
            check("落盘自愈（D5）：内容不是合法 JSON 时跳过自愈，原样留着（不替用户\"修\"文件）",
                    "{ 这不是 JSON ".equals(java.nio.file.Files.readString(entityFile.toPath(),
                            java.nio.charset.StandardCharsets.UTF_8)));
            // D5 回归守卫：用户手上那份真实空壳（36 字节 {"version":1,"entities":{}}）是
            //     "在注册表还空着的时候被生成"的产物 —— 顶层键 entities 存在、里面一个键都没有。
            //     自愈必须能把它填满（旧 bug 就是这种文件永远补不上）。
            java.nio.file.Files.writeString(entityFile.toPath(),
                    "{\n  \"version\": 1,\n  \"entities\": {}\n}\n",
                    java.nio.charset.StandardCharsets.UTF_8);
            java.nio.file.Files.writeString(skillFile.toPath(),
                    "{\n  \"version\": 1,\n  \"skills\": {}\n}\n",
                    java.nio.charset.StandardCharsets.UTF_8);
            List<String> healedEmpty = cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter
                    .writeAll(tempDir);
            org.json.JSONObject shellEntity = new org.json.JSONObject(java.nio.file.Files
                    .readString(entityFile.toPath(), java.nio.charset.StandardCharsets.UTF_8));
            org.json.JSONObject shellSkill = new org.json.JSONObject(java.nio.file.Files
                    .readString(skillFile.toPath(), java.nio.charset.StandardCharsets.UTF_8));
            check("落盘自愈：空壳文件（{\"version\":1,\"entities\":{}} / skills 同理）会被填满，"
                            + "不再是 0 个 —— 实体 " + shellEntity.getJSONObject("entities").keySet().size()
                            + " 个 / 技能 " + shellSkill.getJSONObject("skills").keySet().size() + " 个；"
                            + "这一轮写了 " + healedEmpty.size() + " 个",
                    shellEntity.getJSONObject("entities").keySet().size() >= 9
                            && !shellSkill.getJSONObject("skills").isEmpty()
                            && shellSkill.getJSONObject("skills").keys().next()
                            .contains("#"));
            // D5 回归守卫：文件已经是全量时，自愈必须**一个字节都不写**（内容 + mtime 都要原样）
            byte[] fullBytes = java.nio.file.Files.readAllBytes(entityFile.toPath());
            java.nio.file.attribute.FileTime fullMtime = java.nio.file.Files
                    .getLastModifiedTime(entityFile.toPath());
            java.nio.file.Files.writeString(skillFile.toPath(), firstSkillText,
                    java.nio.charset.StandardCharsets.UTF_8);
            java.nio.file.Files.writeString(rulesFile.toPath(), firstRulesText,
                    java.nio.charset.StandardCharsets.UTF_8);
            java.util.List<String> untouched = cn.gfhnv.game.system.configLoadingSystem
                    .ConfigDefaultWriter.writeAll(tempDir);
            boolean byteIdentical = java.util.Arrays.equals(fullBytes,
                    java.nio.file.Files.readAllBytes(entityFile.toPath()));
            check("落盘自愈：文件已经全了时一个字节都不动（内容原样、mtime 原样；这一轮写了 "
                            + untouched.size() + " 个 = 一个都不写，参考副本走「缺内容才写」那条路）"
                            + "—— 内容一致 " + byteIdentical
                            + "、mtime 一致 " + fullMtime.equals(java.nio.file.Files
                            .getLastModifiedTime(entityFile.toPath())),
                    byteIdentical && fullMtime.equals(java.nio.file.Files
                            .getLastModifiedTime(entityFile.toPath()))
                            && untouched.isEmpty());
            deleteDirectory(tempDir);
            check("落盘：临时目录用完就删干净（不往仓库里留垃圾）", !tempDir.exists());

            /* ---------- ⑤-8 参考副本（EntityData.default.json）：
             *      它曾经的 bug 是"恒为空壳" —— 唯一写入口是 writeAll，而 writeAll 的触发点里
             *      第一个跑的是 loadGameRules（那一刻 World 注册表还空着），
             *      之后再没人重写它（自愈刻意不碰它），而 README 写着"删掉会重新生成一份全量默认值"。
             *      现在改由 healDefaultConfigFiles 在"注册表已满"之后补（见 writeReferenceIfNeeded）。
             *      这一段钉住三件事：空壳会被填满、有内容时不动、口径与 EntityData.json 一致。 */
            java.io.File referenceDir = new java.io.File("./out/tmpRefConfig");
            java.io.File referenceFile = new java.io.File(referenceDir,
                    cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter.REFERENCE_FILE_NAME);
            java.io.File referenceEntityFile = new java.io.File(referenceDir,
                    cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter.ENTITY_FILE_NAME);
            deleteDirectory(referenceDir);
            java.nio.file.Files.createDirectories(referenceDir.toPath());
            // 用户手上那份的真实样子（106 字节的空壳：有 entities 段但是空的）
            java.nio.file.Files.writeString(referenceFile.toPath(),
                    "{\"version\":1,\"entities\":{},\"skills\":{}}",
                    java.nio.charset.StandardCharsets.UTF_8);
            boolean shellRewritten = cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter
                    .writeReferenceIfNeeded(referenceFile,
                            cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter
                                    .REFERENCE_FILE_NAME);
            org.json.JSONObject healedReference = new org.json.JSONObject(java.nio.file.Files
                    .readString(referenceFile.toPath(), java.nio.charset.StandardCharsets.UTF_8));
            int referenceEntities = healedReference.optJSONObject("entities") == null
                    ? 0 : healedReference.getJSONObject("entities").keySet().size();
            check("参考副本（①）：空壳（{\"entities\":{}} 那份 106 字节的文件）会被按注册表重写成全量 —— "
                            + "写了 " + shellRewritten + "，现在 entities 里有 " + referenceEntities + " 个",
                    shellRewritten && referenceEntities >= 9);
            check("参考副本（①）：技能那一段也一起有内容（它不是我写空的那种半份）",
                    healedReference.optJSONObject(
                            cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter.SKILLS) != null
                            && !healedReference.getJSONObject(
                                    cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter.SKILLS)
                            .isEmpty());
            // 有内容时：一个字节都不动（它是导出物，每次启动都重写会把 mtime 变成假信号）
            byte[] referenceBytes = java.nio.file.Files.readAllBytes(referenceFile.toPath());
            boolean wroteAgain = cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter
                    .writeReferenceIfNeeded(referenceFile,
                            cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter
                                    .REFERENCE_FILE_NAME);
            check("参考副本（①）：已经有内容时一个字节都不动（返回 false、内容原样）",
                    !wroteAgain && java.util.Arrays.equals(referenceBytes,
                            java.nio.file.Files.readAllBytes(referenceFile.toPath())));
            // 口径一致：注册表里每个官方模板都在参考副本里，且 base / derived 的键与 EntityData.json 逐位相同
            cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter
                    .writeEntityData(referenceEntityFile);
            org.json.JSONObject referencePair = new org.json.JSONObject(java.nio.file.Files
                    .readString(referenceEntityFile.toPath(), java.nio.charset.StandardCharsets.UTF_8));
            List<String> referenceMismatch = new ArrayList<>();
            for (String id : healedReference.getJSONObject("entities").keySet()) {
                org.json.JSONObject fromReference = healedReference.getJSONObject("entities")
                        .getJSONObject(id);
                org.json.JSONObject fromEntityData = referencePair.optJSONObject("entities") == null
                        ? null : referencePair.getJSONObject("entities").optJSONObject(id);
                if (fromEntityData == null) {
                    referenceMismatch.add(id + "（EntityData.json 里没有这个模板）");
                    continue;
                }
                for (String section : new String[]{"base", "derived"}) {
                    org.json.JSONObject left = fromReference.optJSONObject(section);
                    org.json.JSONObject right = fromEntityData.optJSONObject(section);
                    if (left == null || right == null) {
                        referenceMismatch.add(id + "/" + section + "（有一边没有这一段）");
                        continue;
                    }
                    if (!left.keySet().equals(right.keySet())) {
                        referenceMismatch.add(id + "/" + section + " 键不一致：" + left.keySet()
                                + " vs " + right.keySet());
                    }
                    if (left.optLong("baseHpMax", -1) != right.optLong("baseHpMax", -1)) {
                        referenceMismatch.add(id + "/baseHpMax " + left.opt("baseHpMax") + " vs "
                                + right.opt("baseHpMax"));
                    }
                }
            }
            check("参考副本（①）：与 EntityData.json 是同一个口径（每个官方模板都在、base/derived 的键"
                            + "逐位相同、baseHpMax 数值相同）—— 不一致的 " + referenceMismatch,
                    referenceMismatch.isEmpty());
            check("参考副本（①）：写的是 UTF-8 中文（不是乱码）",
                    java.nio.file.Files.readString(referenceFile.toPath(),
                            java.nio.charset.StandardCharsets.UTF_8).contains("玩家一"));
            /* ---------- ⑤-9 参考副本的"说明键"与伪键守卫（用户 2026-10-03 要求）：
             *      它原来有一个伪数据键，键名是一整句中文 —— 看起来像数据、其实是人看的注释。
             *      现在改用下划线开头的惯例键 _note，并加一条守卫：任何"键名里带中文的伪键"都不许再有。
             *      这两条断言只读 out/ 下的临时文件，不碰 config/ 里的真实文件。 */
            Object referenceNoteRaw = healedReference.opt(
                    cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter.NOTE);
            check("参考副本（②）：有说明键 "
                            + cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter.NOTE
                            + "（下划线开头 = 不是数据），而且是<b>非空</b>字符串 —— 实为 "
                            + (referenceNoteRaw == null ? "null" : "非空"),
                    referenceNoteRaw instanceof String note
                            && !note.isBlank()
                            && note.contains(cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter
                            .ENTITY_FILE_NAME));
            List<String> pseudoKeyOffenders = new ArrayList<>();
            for (String key : healedReference.keySet()) {
                boolean isNote = cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter.NOTE
                        .equals(key);
                if (isNote) {
                    continue;
                }
                if (key.chars().anyMatch(c -> c > 0x7f)) {
                    pseudoKeyOffenders.add("顶层：" + key);
                }
            }
            for (String entityId : healedReference.getJSONObject(
                    cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter.SKILLS).keySet()) {
                if (entityId.chars().anyMatch(c -> c > 0x7f) && !entityId.startsWith("game_")) {
                    pseudoKeyOffenders.add("技能段：" + entityId);
                }
            }
            check("参考副本（②）：没有「中文括号说明」那类伪数据键（键名里不许出现非 ASCII，"
                            + "说明文字一律走 "
                            + cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter.NOTE
                            + "）—— 违规 " + pseudoKeyOffenders,
                    pseudoKeyOffenders.isEmpty());
            // 结构完整：技能段的段名就是 skills，且每个技能的键集合与 SkillData.json 逐位相同
            // （含 consumedMana 与 tags 两个块 —— 参考副本要如实反映全量结构）
            org.json.JSONObject referenceSkills = healedReference.getJSONObject(
                    cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter.SKILLS);
            org.json.JSONObject generatedSkills = new org.json.JSONObject(
                    cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter.skillDataJson())
                    .getJSONObject(
                            cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter.SKILLS);
            List<String> skillSectionMismatch = new ArrayList<>();
            if (!referenceSkills.keySet().equals(generatedSkills.keySet())) {
                skillSectionMismatch.add("技能清单不一致：参考副本 " + referenceSkills.length()
                        + " 个 vs 生成的 " + generatedSkills.length() + " 个");
            }
            for (String skillKey : referenceSkills.keySet()) {
                if (!generatedSkills.has(skillKey)) {
                    continue;
                }
                java.util.Set<String> left = referenceSkills.getJSONObject(skillKey).keySet();
                java.util.Set<String> right = generatedSkills.getJSONObject(skillKey).keySet();
                if (!left.equals(right)) {
                    skillSectionMismatch.add(skillKey + "：" + left + " vs " + right);
                }
            }
            check("参考副本（②）：技能段与 SkillData.json 逐键同构（含 consumedMana 与 tags 两个块，"
                            + "不再漏项）—— 不一致 " + skillSectionMismatch,
                    skillSectionMismatch.isEmpty());
            boolean hasTagsBlock = false;
            boolean hasNullMana = false;
            boolean hasManaBlock = false;
            for (String skillKey : referenceSkills.keySet()) {
                org.json.JSONObject oneSkill = referenceSkills.getJSONObject(skillKey);
                if (oneSkill.optJSONObject(
                        cn.gfhnv.game.system.configLoadingSystem.DataKeys.SkillKeys.WEIGHT_TAGS) != null) {
                    hasTagsBlock = true;
                }
                if (oneSkill.has(
                        cn.gfhnv.game.system.configLoadingSystem.DataKeys.SkillKeys.CONSUMED_MANA)) {
                    if (oneSkill.isNull(
                            cn.gfhnv.game.system.configLoadingSystem.DataKeys.SkillKeys.CONSUMED_MANA)) {
                        hasNullMana = true;
                    } else {
                        hasManaBlock = true;
                    }
                }
            }
            check("参考副本（②）：tags 块真的写出来了（AI 权重不再在参考副本里看不见）",
                    hasTagsBlock);
            check("参考副本（②）：consumedMana 两种形态都表达得出来 —— "
                            + "块写法 " + hasManaBlock + "、\"取消消耗\"的 null 写法 " + hasNullMana
                            + "（以前这一段整个省略，看不出来是\"不消耗\"还是\"没展开\"）",
                    hasManaBlock && hasNullMana);
            deleteDirectory(referenceDir);
            check("参考副本（①）：临时目录用完就删干净", !referenceDir.exists());
        } finally {
            for (cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Snapshot snapshot
                    : snapshots.values()) {
                snapshot.restore();
            }
        }

        List<String> afterRestore = new ArrayList<>();
        for (LivingThing living : World.getLivingEntityList()) {
            String now = cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.stateOf(living);
            if (!now.equals(beforeAll.get(living.getId()))) {
                afterRestore.add(living.getId());
            }
        }
        check("自测收尾：所有模板都还原成了补丁之前的样子（后面 483 条用例看到的是干净注册表）—— 没还原 "
                + afterRestore, afterRestore.isEmpty());
    }

    /**
     * 「反射驱动配置」可行性实验（2026-10-03）。
     * <p>
     * <b>它证明三件事</b>（对应
     * {@code project_analyses/current/CONFIG-LOADING-DECOUPLING-2026-10.md} 的「反射驱动可行性实验」一节）：
     * <ol>
     *     <li><b>反射能驱动配置</b>：键清单来自 {@code DataBridge} 的字段反射（
     *     {@code ReflectionConfigBridge.candidates}），默认值来自 {@code DataBridge.toTag}
     *     （= {@code /data get}），写入走 {@code DataBridge.applyTag}（= {@code /data merge}），
     *     配置层一行代码都不含键名；</li>
     *     <li><b>{@code @NoConfig} 挡得住、而且默认是开的</b>：{@code uuid} / {@code alive} /
     *     {@code presentTurn} 写不进去，而<b>没有注解的字段一律能配</b>；</li>
     *     <li><b>最强的那条</b>：给探针类加一个<b>全新的字段</b>
     *     （{@link ConfigReflectionProbeEntity#DEFAULT_RESONANCE}），不改任何配置层代码，
     *     它就出现在默认值 dump 里、写进 JSON 也真的生效。</li>
     * </ol>
     * 本方法只读地用过 {@code EntityDataPatcher}（打的是内存里现解析的 JSON），
     * 碰过的模板全部在 {@code finally} 里还原；探针实体也从注册表里删掉 ——
     * <b>不碰 {@code config/} 下的真文件</b>。
     */
    private static void testReflectionDrivenConfig() {
        section("反射驱动配置（实验：配置面寄生在 /data 的反射上）");

        /* ---------- ① 三个真实字段：criticalRate / speed / defenceLoss ---------- */
        // 2026-11-14 第 50 批：metalResistance 已容器化（/data 里变成 resistances 列表），
        // 这里换一个仍在 @DataFlatten 组件里、且有 setter 的面板标量来证明反射写入。
        LivingThing target = entityTemplateOf("game_official_content:playerOne");
        if (target == null) {
            return;
        }
        String beforeState = cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.stateOf(target);
        double beforeCritical = target.getCriticalRate();
        long beforeSpeed = target.getSpeed();
        double beforeLoss = target.getDefenceLoss();
        System.out.println("  [信息] 打补丁前：" + beforeState);
        try {
            // 用**另一个值**，这样"变了"这件事有信息量（写成原值什么都不变，断言会假绿）
            double newCritical = beforeCritical + 0.125;
            long newSpeed = beforeSpeed + 7;
            double newLoss = beforeLoss + 0.5;
            org.json.JSONObject patch = new org.json.JSONObject()
                    .put("baseCriticalRate", newCritical)
                    .put("baseSpeed", newSpeed)
                    .put("defenceLoss", newLoss);
            System.out.println("  [信息] 补丁（键名 = /data 数据名）：" + patch);
            cn.gfhnv.game.system.configLoadingSystem.ReflectionConfigBridge.Applied applied =
                    cn.gfhnv.game.system.configLoadingSystem.ReflectionConfigBridge.patch(target, patch);
            System.out.println("  [信息] 记账：应用 " + applied.applied() + "，跳过 " + applied.skipped());
            check("① 反射驱动：三个键全部写进去、没有跳过项 —— " + applied.applied().keySet(),
                    applied.skipped().isEmpty() && applied.applied().size() == 3);
            check("① 反射驱动：criticalRate 真的变了（+" + 0.125 + "）—— "
                            + beforeCritical + " → " + target.getCriticalRate(),
                    Math.abs(target.getCriticalRate() - (beforeCritical + 0.125)) < 1e-9);
            check("① 反射驱动：speed 真的变了（+7）—— " + beforeSpeed + " → " + target.getSpeed(),
                    target.getSpeed() == beforeSpeed + 7);
            check("① 反射驱动：defenceLoss 真的变了（+0.5，字段住在 @DataFlatten 组件里）—— "
                            + beforeLoss + " → " + target.getDefenceLoss(),
                    Math.abs(target.getDefenceLoss() - (beforeLoss + 0.5)) < 1e-9);

            /* ---------- ①-b 默认值 = 构造当时的字段值 ---------- */
            // 探针对象刚 new 出来、谁都没碰过：dump 出来多少，就是成员初始化器/构造器当时写的多少。
            Map<String, cn.gfhnv.game.data.NbtTag> fresh =
                    cn.gfhnv.game.system.configLoadingSystem.ReflectionConfigBridge
                            .defaults(ConfigReflectionProbeEntity.probe());
            check("① 默认值：dump 出来的 resonanceCoefficient 就是字段初始化器的值 "
                            + ConfigReflectionProbeEntity.DEFAULT_RESONANCE + "（不需要另建「出厂值」表）—— 实际 "
                            + fresh.get("resonanceCoefficient"),
                    fresh.containsKey("resonanceCoefficient")
                            && Math.abs(fresh.get("resonanceCoefficient").asDouble()
                            - ConfigReflectionProbeEntity.DEFAULT_RESONANCE) < 1e-9);
        } finally {
            target.setCriticalRate(beforeCritical);
            target.setSpeed(beforeSpeed);
            target.setDefenceLoss(beforeLoss);
        }
        check("① 还原：模板逐字段回到打补丁之前（自测不污染注册表）—— "
                        + (beforeState.equals(
                        cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.stateOf(target))),
                beforeState.equals(
                        cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.stateOf(target)));

        /* ---------- ② @NoConfig 挡得住 + 默认开放 ---------- */
        Map<String, String> blockedOfClass = cn.gfhnv.game.system.configLoadingSystem
                .ReflectionConfigBridge.blockedOf(LivingThing.class);
        System.out.println("  [信息] LivingThing 上被 @NoConfig 挡住的字段：" + blockedOfClass);
        List<String> noReason = new ArrayList<>();
        for (Map.Entry<String, String> entry : blockedOfClass.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                noReason.add(entry.getKey());
            }
        }
        boolean aliveBlockedInProfile;
        try {
            aliveBlockedInProfile = cn.gfhnv.game.entity.AttributeProfile.class.getDeclaredField("alive")
                    .isAnnotationPresent(cn.gfhnv.game.data.NoConfig.class);
        } catch (NoSuchFieldException e) {
            aliveBlockedInProfile = false;
        }
        check("② 挡板：uuid / presentTurn 在 LivingThing 的 @NoConfig 里，alive 在 AttributeProfile 里"
                        + "（2026-10-04 搬过去了，理由跟着搬）—— 实扫 " + blockedOfClass.keySet(),
                blockedOfClass.keySet().containsAll(Arrays.asList("uuid", "presentTurn"))
                        && aliveBlockedInProfile);
        check("② 挡板：每个 @NoConfig 都写了理由（防止有人只加注解不加说明）—— 没写理由的 " + noReason,
                noReason.isEmpty());
        // 反射面的候选规则是"非 @NoConfig + 标量"：集合/对象一律在配置面之外
        // （它们本来也没有 setter，硬塞进来只会得到"裸写字段"那种静默回归）
        List<String> nonScalarCandidates = new ArrayList<>();
        List<String> blockedNonScalar = new ArrayList<>();
        for (cn.gfhnv.game.data.DataBridge.DataAccessor accessor : cn.gfhnv.game.system
                .configLoadingSystem.ReflectionConfigBridge.candidates(target)) {
            if (!cn.gfhnv.game.system.configLoadingSystem.ReflectionConfigBridge
                    .isScalar(accessor.field())) {
                nonScalarCandidates.add(accessor.name());
            }
        }
        for (Map.Entry<String, String> entry : cn.gfhnv.game.system.configLoadingSystem
                .ReflectionConfigBridge.blocked(target).entrySet()) {
            if (entry.getValue().startsWith("非标量")) {
                blockedNonScalar.add(entry.getKey());
            }
        }
        check("② 可配置面只含标量（集合/对象一律在外面，否则会静默退化成「裸写字段」）—— 漏进来的 "
                        + nonScalarCandidates,
                nonScalarCandidates.isEmpty());
        check("② 非标量确实被点名挡住了（inventory / damageReductions / 物理对象 …）—— 实扫 "
                        + blockedNonScalar,
                blockedNonScalar.containsAll(Arrays.asList("inventory", "damageReductions",
                        "damageModifiers", "force", "position")));
        check("② 挡板表里没有「不知道该怎么算」的字段（每个被挡的都有理由）",
                cn.gfhnv.game.system.configLoadingSystem.ReflectionConfigBridge.blocked(target)
                        .values().stream().noneMatch(reason -> reason == null || reason.isEmpty()));

        // 真打一遍：uuid / alive / presentTurn / probeState 全被挡，字段值一个字都不许动
        LivingThing blockedTarget = entityTemplateOf("game_official_content:playerOne");
        boolean beforeAlive = blockedTarget.isAlive();
        String beforeUuid = blockedTarget.getUUID();
        Object beforeTurn = valueOfField(blockedTarget, "presentTurn");
        org.json.JSONObject blockedPatch = new org.json.JSONObject()
                .put("uuid", "改掉我")
                .put("alive", false)
                .put("presentTurn", "改掉我")
                .put("probeState", 999);
        cn.gfhnv.game.system.configLoadingSystem.ReflectionConfigBridge.Applied blocked =
                cn.gfhnv.game.system.configLoadingSystem.ReflectionConfigBridge
                        .patch(blockedTarget, blockedPatch);
        System.out.println("  [信息] 挡板记账：应用 " + blocked.applied() + "，跳过 " + blocked.skipped());
        check("② 挡板：uuid / alive / presentTurn / probeState 一个都没写进去 —— 跳过 "
                        + blocked.skipped().keySet(),
                blocked.applied().isEmpty() && blocked.skipped().size() == 4);
        check("② 挡板：被挡的字段值真的没动（alive / uuid / presentTurn 逐字段比对）",
                blockedTarget.isAlive() == beforeAlive
                        && beforeUuid.equals(blockedTarget.getUUID())
                        && valueOfField(blockedTarget, "presentTurn") == beforeTurn);

        // 默认开放：探针那个**没写任何注解**的 mass 能配（它只有 getter，裸写字段，所以单独看）
        ConfigReflectionProbeEntity openProbe = ConfigReflectionProbeEntity.probe();
        List<String> openKeys = ConfigReflectionProbeEntity.configurableKeys();
        System.out.println("  [信息] 探针的可配置键（" + openKeys.size() + " 个）：" + openKeys);
        check("② 默认开放：没有任何注解的字段一律在可配置清单里（resonanceCoefficient / mass / name …）—— "
                        + "resonanceCoefficient 在里面",
                openKeys.contains("resonanceCoefficient"));
        check("② 默认开放：只写了 @NoConfig 的 probeState 不在可配置清单里，"
                        + "但 /data 那一面照旧看得见它（注解只管配置面）",
                !openKeys.contains("probeState")
                        && cn.gfhnv.game.data.DataBridge.dataNames(ConfigReflectionProbeEntity.class)
                        .contains("probeState"));

        /* ---------- ③ 最强的那条：全新字段，改 0 处配置层代码 ---------- */
        ConfigReflectionProbeEntity probe = ConfigReflectionProbeEntity.probe();
        Map<String, cn.gfhnv.game.data.NbtTag> beforeDump =
                cn.gfhnv.game.system.configLoadingSystem.ReflectionConfigBridge.defaults(probe);
        double beforeResonance = probe.getResonanceCoefficient();
        System.out.println("  [信息] 探针默认值 dump 里有没有 resonanceCoefficient："
                + beforeDump.containsKey("resonanceCoefficient")
                + "，值 = " + beforeDump.get("resonanceCoefficient"));
        check("③ 新字段：(a) 它出现在默认值 dump 里，且值 = 字段初始化器的值（配置系统从没见过它）",
                beforeDump.containsKey("resonanceCoefficient")
                        && Math.abs(beforeDump.get("resonanceCoefficient").asDouble()
                        - beforeResonance) < 1e-9);
        check("③ 新字段：DataKeys 那张表里**没有**它（它不可能来自手写清单）",
                !DataKeys.isConfigurable("resonanceCoefficient")
                        && !DataKeys.META.containsKey("resonanceCoefficient"));
        check("③ 新字段：EntityKeySpecs 那张表里也没有它",
                cn.gfhnv.game.system.configLoadingSystem.EntityKeySpecs.ALL.stream()
                        .noneMatch(spec -> "resonanceCoefficient".equals(spec.name())));

        // (b) 写进 JSON 能生效 —— 走的是 EntityData.json 的真实入口
        org.json.JSONObject probeRoot = new org.json.JSONObject()
                .put("version", 1)
                .put("entities", new org.json.JSONObject().put(
                        ConfigReflectionProbeEntity.PROBE_ID,
                        new org.json.JSONObject().put("resonanceCoefficient", 12.25)));
        System.out.println("  [信息] 探针配置（就是 EntityData.json 的形状）：" + probeRoot);
        World.addEntity(probe);
        try {
            /* 走**真实的 /data merge 路**：
             * EntityDataPatcher 用的是手写键表，它按设计不认识 resonanceCoefficient ——
             * 那正是本次实验要消除的东西；所以这里直接调反射桥，
             * 而"接进 EntityDataPatcher"的代价单独在 ④ 里数。 */
            cn.gfhnv.game.system.configLoadingSystem.ReflectionConfigBridge.Applied probeApplied =
                    cn.gfhnv.game.system.configLoadingSystem.ReflectionConfigBridge
                            .patch(probe, probeRoot.getJSONObject("entities")
                                    .getJSONObject(ConfigReflectionProbeEntity.PROBE_ID));
            System.out.println("  [信息] 探针补丁记账：应用 " + probeApplied.applied() + "，跳过 "
                    + probeApplied.skipped());
            check("③ 新字段：(b) 反射桥（= /data merge 那条路）真的把它写进去了 —— "
                            + probeApplied.applied().keySet(),
                    probeApplied.isClean() && probeApplied.applied().size() == 1);
            check("③ 新字段：(b) 写进 JSON 真的生效 —— resonanceCoefficient "
                            + beforeResonance + " → " + probe.getResonanceCoefficient(),
                    Math.abs(probe.getResonanceCoefficient() - 12.25) < 1e-9);
            check("③ 新字段：(b) 回读也看得见（dump 出来是 12.25，/data get 同一个口径）",
                    Math.abs(cn.gfhnv.game.system.configLoadingSystem.ReflectionConfigBridge
                            .defaults(probe).get("resonanceCoefficient").asDouble() - 12.25) < 1e-9);

            /* ---------- ③-b 切换（第 3 步）：同一条配置走**正式入口**也生效 ---------- */
            // 切换**之前**：这条配置走 EntityDataPatcher 会被报成「未知键」
            // （实验文档 §11.2 那条"诚实的偏差"就是这么来的）。
            // 2026-10-03 第 3 步把反射兜底接进 EntityDataPatcher.patch，于是它经正式入口生效 ——
            // 这一条就是"加一个字段 = 0 处配置层改动"从"并列的新路"变成"正式入口"的证据。
            probe.setResonanceCoefficient(beforeResonance);
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Report report =
                    cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher
                            .apply(probeRoot, "自测-反射驱动探针");
            System.out.println("  [信息] 切换后：同一条配置走 EntityDataPatcher（正式入口）→ 应用 "
                    + report.appliedEntries() + "，跳过 " + report.skippedEntries());
            check("③ 切换（第 3 步）：同一条配置走**正式入口** EntityDataPatcher 真的生效了 —— "
                            + beforeResonance + " → " + probe.getResonanceCoefficient()
                            + "（应用 " + report.appliedEntries() + "，跳过 " + report.skippedEntries() + "）",
                    report.appliedEntries().size() == 1 && report.skippedEntries().isEmpty()
                            && Math.abs(probe.getResonanceCoefficient() - 12.25) < 1e-9);
            probe.setResonanceCoefficient(beforeResonance);
        } finally {
            World.removeEntity(probe);
        }
        check("③ 新字段：探针已从注册表移除（不污染后面的用例）",
                !World.getLivingEntityList().contains(probe));

        /* ---------- ④ "改了几处"的数字 ---------- */
        System.out.println();
        System.out.println("  [数字] 加一个全新可配置字段，配置层要改几处：");
        System.out.println("         · 反射路（本次实验）：0 处 —— 只加了 "
                + ConfigReflectionProbeEntity.class.getSimpleName()
                + ".resonanceCoefficient 这一个 Java 字段（外加它自己的 getter/setter）");
        System.out.println("         · 表驱动路（现在的正式实现）：1 个文件 1 行 —— "
                + "EntityKeySpecs 里加一行 spec(...)（键名/类型/读法/写法都得人再抄一遍）");
        check("④ 数字：探针字段在配置层的改动点 = 0（上面三条断言就是证据："
                        + "没有一行配置层代码提到 resonanceCoefficient）",
                !DataKeys.isConfigurable("resonanceCoefficient")
                        && cn.gfhnv.game.system.configLoadingSystem.EntityKeySpecs.ALL.stream()
                        .noneMatch(spec -> "resonanceCoefficient".equals(spec.name())));

        /* ---------- ⑤ 全面切换的代价：反射面比"该给人配的"大多少 ---------- */
        System.out.println();
        System.out.println("  [数字] 反射面 vs 手写表（第 1 步的实测口径 + 第 2 步之后的现状）：");
        java.util.Set<String> tableKeys = new java.util.TreeSet<>();
        for (cn.gfhnv.game.system.configLoadingSystem.KeySpec<LivingThing> spec
                : cn.gfhnv.game.system.configLoadingSystem.EntityKeySpecs.ALL) {
            tableKeys.add(spec.name());
        }
        java.util.Set<String> reflectionKeys = new java.util.TreeSet<>();
        for (cn.gfhnv.game.data.DataBridge.DataAccessor accessor : cn.gfhnv.game.system
                .configLoadingSystem.ReflectionConfigBridge.candidates(target)) {
            reflectionKeys.add(accessor.name());
        }
        java.util.Set<String> onlyReflection = new java.util.TreeSet<>(reflectionKeys);
        onlyReflection.removeAll(tableKeys);
        java.util.Set<String> onlyTable = new java.util.TreeSet<>(tableKeys);
        onlyTable.removeAll(reflectionKeys);
        System.out.println("         · 手写表 " + tableKeys.size() + " 个键（含 manaGrow 块与 inventorySlots）");
        System.out.println("         · 反射面 " + reflectionKeys.size() + " 个键");
        System.out.println("         · 只在表里、反射拿不到的（" + onlyTable.size() + " 个）：" + onlyTable);
        System.out.println("         · 只在反射里、表里没有的（第 1 步实测 "
                + REFLECTION_ONLY_SCALAR_KEYS.size() + " 个，第 2 步逐个写了 @NoConfig，现在是 "
                + onlyReflection.size() + " 个）：");
        System.out.println("           " + onlyReflection);
        Map<String, String> blockedNow = cn.gfhnv.game.system.configLoadingSystem
                .ReflectionConfigBridge.blocked(target);
        // 表专属（在 /data 里没有同名字段）的三个键：manaGrow 块、inventorySlots、以及
        // 2026-11-14 容器化后的 penetration（配置键照旧，但字段变成了 penetrations 列表）。
        java.util.Set<String> tableOnlyKeys = new java.util.TreeSet<>(
                java.util.Arrays.asList("manaGrow", "inventorySlots", "penetration",
                        "metalResistance", "woodResistance", "waterResistance",
                        "fireResistance", "dirtResistance"));
        check("⑤ 混血不变式（第 2 步之后）：反射面（candidates 口径）== 手写表的<b>标量</b>键"
                        + "（31 键 − 表专属的 " + tableOnlyKeys.size() + " 个：" + tableOnlyKeys + "）—— "
                        + reflectionKeys.size() + " vs " + (tableKeys.size() - tableOnlyKeys.size())
                        + "，「只在反射里」的 " + onlyReflection.size() + " 个",
                onlyReflection.isEmpty()
                        && onlyTable.equals(tableOnlyKeys)
                        && reflectionKeys.size() == tableKeys.size() - tableOnlyKeys.size());
        List<String> nonScalarBlocked = new ArrayList<>();
        for (Map.Entry<String, String> entry : blockedNow.entrySet()) {
            if (entry.getValue().startsWith("非标量")) {
                nonScalarBlocked.add(entry.getKey());
            }
        }
        System.out.println("         · 其中「非标量、由规则自动排除」的 " + nonScalarBlocked.size()
                + " 个（不需要注解）：" + nonScalarBlocked);
        System.out.println("         · 真要人判断的（标量但没进手写表）＝ 第 1 步 "
                + REFLECTION_ONLY_SCALAR_KEYS.size() + " 个 → 第 2 步全部判为「不能配」并逐条写了理由"
                + "（分类结果见 REFLECTION_ONLY_SCALAR_KEYS 的 javadoc）");

        /* ---------- ⑥ 守卫：把上面那份"人看的清单"变成断言（2026-10-03 第 1 步） ---------- */
        // 允许清单的构成（三段，缺一段这条断言就会红）：
        //   ① 能配的   —— EntityKeySpecs 那张手写表（31 键）
        //   ② 加注解的 —— 字段上的 @NoConfig（含 @DataFlatten 组件里的；实例口径才看得见组件）
        //   ③ 明确排除 —— DataKeys.READ_ONLY（/data 面承认、配置面不认）+ SUBCLASS_STATE_KEYS
        System.out.println();
        System.out.println("  [守卫] 反射面 包含 允许清单（能配的 ∪ 加 @NoConfig 的 ∪ 明确排除的）：");
        check("守卫（第 2 步）：反射面 − 手写表 − @NoConfig 现在是<b>空集</b>"
                        + "（那 24 个已逐个表态：全判\"不能配\"并写了理由）—— 实扫 " + onlyReflection,
                onlyReflection.isEmpty());
        java.util.Set<String> classified = new java.util.TreeSet<>(blockedNow.keySet());
        classified.retainAll(REFLECTION_ONLY_SCALAR_KEYS);
        check("守卫（第 2 步）：第 1 步现形的那 " + REFLECTION_ONLY_SCALAR_KEYS.size()
                        + " 个键<b>全部</b>落在 @NoConfig 清单里（逐个都写了理由，少一个就是漏表态）—— 实扫 "
                        + classified,
                classified.equals(REFLECTION_ONLY_SCALAR_KEYS));
        List<String> guardOffenders = new ArrayList<>();
        for (LivingThing living : World.getLivingEntityList()) {
            java.util.Set<String> allowed = new java.util.TreeSet<>(tableKeys);
            allowed.addAll(cn.gfhnv.game.system.configLoadingSystem.ReflectionConfigBridge
                    .blocked(living).keySet());
            allowed.addAll(DataKeys.READ_ONLY);
            allowed.addAll(SUBCLASS_STATE_KEYS);
            for (cn.gfhnv.game.data.DataBridge.DataAccessor accessor : cn.gfhnv.game.system
                    .configLoadingSystem.ReflectionConfigBridge.candidates(living)) {
                if (!allowed.contains(accessor.name())) {
                    guardOffenders.add(living.getId() + "/" + accessor.name());
                }
            }
        }
        check("守卫：每个模板的反射面都在允许清单里（新加标量字段没表态就红）—— 漏登记 " + guardOffenders,
                guardOffenders.isEmpty());
        System.out.println("  [守卫] 允许清单构成：能配的 " + tableKeys.size() + " 键（EntityKeySpecs 表）"
                + " ＋ 加 @NoConfig 的 " + blockedNow.size() + " 键（playerOne 口径，含组件；"
                + "其中非标量 " + nonScalarBlocked.size() + " 个由规则自动排除）"
                + " ＋ 明确排除的 " + (DataKeys.READ_ONLY.size() + SUBCLASS_STATE_KEYS.size()) + " 键"
                + "（DataKeys.READ_ONLY " + DataKeys.READ_ONLY.size()
                + " + SUBCLASS_STATE_KEYS " + SUBCLASS_STATE_KEYS.size() + "）");
        System.out.println("  [守卫] 覆盖范围＝注册表里全部 " + World.getLivingEntityList().size()
                + " 个模板（不只 playerOne）—— 子类状态键由 SUBCLASS_STATE_KEYS 兜住");

        /* ---------- ⑦ 切换语义（第 3 步）：段定位 / 明确排除 ---------- */
        // ① 段定位：写进 base 子块的新字段，记账里仍然带「base.」前缀（"第几段、哪个键"没丢）。
        //    子块名是编排信息（反射面拿不到），所以由 EntityDataPatcher 遍历 BARE_SECTIONS 传前缀。
        ConfigReflectionProbeEntity sectionProbe = ConfigReflectionProbeEntity.probe();
        cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Report sectionReport =
                new cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Report("自测-段定位");
        org.json.JSONObject sectionPatch = new org.json.JSONObject().put("base",
                new org.json.JSONObject().put("resonanceCoefficient", 7.5));
        java.util.Set<String> sectionApplied = cn.gfhnv.game.system.configLoadingSystem
                .ReflectionConfigBridge.patchExtra(sectionProbe, sectionPatch, sectionReport,
                        ConfigReflectionProbeEntity.PROBE_ID);
        System.out.println("  [信息] 反射兜底：写进 base 子块 → " + sectionReport.appliedEntries());
        check("⑦ 切换：反射兜底认得 base 子块、记账里带「base.」前缀（段定位没丢）—— "
                        + sectionReport.appliedEntries(),
                sectionApplied.contains("resonanceCoefficient")
                        && sectionReport.appliedEntries().toString().contains("base.resonanceCoefficient")
                        && Math.abs(sectionProbe.getResonanceCoefficient() - 7.5) < 1e-9);

        // ② 明确排除（闸门）：phaseTwo 在反射面上、也是标量，但它**没有 setter** ——
        //    放行它就是 70-DATA §5.10 点名的「裸写字段」那个坑（配置静默改掉 boss 的进程状态）。
        //    CLASS_RUNTIME 挡住了它，于是它照旧报「未知键」+ 为什么，一个字都没写进去。
        LivingThing boss = entityTemplateOf("game_official_content:flameReaver");
        if (boss != null) {
            Object beforePhase = valueOfField(boss, "phaseTwo");
            boolean onSurface = false;
            for (cn.gfhnv.game.data.DataBridge.DataAccessor accessor : cn.gfhnv.game.system
                    .configLoadingSystem.ReflectionConfigBridge.candidates(boss)) {
                if ("phaseTwo".equals(accessor.name())) {
                    onSurface = true;
                }
            }
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Report gate =
                    cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.applyJson(
                            "{\"entities\":{\"game_official_content:flameReaver\":"
                                    + "{\"phaseTwo\":true}}}", "自测-明确排除");
            String afterPhase = String.valueOf(valueOfField(boss, "phaseTwo"));
            System.out.println("  [信息] 明确排除：写 phaseTwo（它在反射面上： " + onSurface + "）→ 应用 "
                    + gate.appliedEntries() + "，跳过 " + gate.skippedEntries());
            check("⑦ 切换：CLASS_RUNTIME 里的子类运行时状态键<b>不会</b>被配置静默写进去"
                            + "（phaseTwo 连 setter 都没有，放行就是「裸写字段」那个坑）—— 值 "
                            + beforePhase + " → " + afterPhase,
                    onSurface && gate.appliedEntries().isEmpty()
                            && gate.skippedEntries().toString().contains("phaseTwo")
                            && String.valueOf(beforePhase).equals(afterPhase));
        }
    }

    /**
     * 角色 / 怪物类<b>自己的配置字段</b>（{@link DataKeys#CLASS_CONFIG}）能配、而且配了真的生效。
     * <p>
     * <b>它回答的是用户那句"白厄的配置是否忘了?为什么没有火种等配置?"</b>：
     * 以前 {@code CLASS_STATE} 把整批子类字段一起挡在配置面之外（为了堵"静默裸写字段"那个洞），
     * 于是"想配火种上限"这种正当需求也一起被挡了。现在按
     * <b>"有没有 setter、值是不是开局输入"</b>拆成两半，这里逐条证明：
     * <ul>
     *     <li>放行的那批<b>真的能从 JSON 写进去</b>（走 setter，钳制照旧生效）；</li>
     *     <li>挡住的那批<b>仍然一个字都写不进去</b>，而且会带上"为什么"；</li>
     *     <li>清单本身不许烂掉（放行的必须有 setter、两半不许重叠、并集 = {@code CLASS_STATE}）。</li>
     * </ul>
     * 用例一律在真模板上跑，跑完用快照还原。
     */
    private static void testClassStateConfigKeys() {
        section("角色/怪物自己的配置字段（classState：白厄的火种、李晓焰的燃点）");

        /* ---------- ① 清单不许烂掉 ---------- */
        List<String> manifestProblems = new ArrayList<>();
        for (String key : DataKeys.CLASS_CONFIG) {
            if (DataKeys.CLASS_RUNTIME.contains(key)) {
                manifestProblems.add(key + "（两半都登记了）");
            }
        }
        java.util.Set<String> union = new java.util.TreeSet<>(DataKeys.CLASS_CONFIG);
        union.addAll(DataKeys.CLASS_RUNTIME);
        if (!union.equals(new java.util.TreeSet<>(DataKeys.CLASS_STATE))) {
            manifestProblems.add("两半的并集 != CLASS_STATE（相差 "
                    + java.util.stream.Stream.concat(
                    new java.util.TreeSet<>(DataKeys.CLASS_STATE).stream(),
                    union.stream()).distinct().count() + " 项）");
        }
        check("classState：CLASS_CONFIG 与 CLASS_RUNTIME 不重叠、并集就是 CLASS_STATE（清单不许两边都漏）—— 问题 "
                + manifestProblems, manifestProblems.isEmpty());

        List<String> withoutSetter = new ArrayList<>();
        for (String key : DataKeys.CLASS_CONFIG) {
            String setterName = "set" + Character.toUpperCase(key.charAt(0)) + key.substring(1);
            boolean found = false;
            for (LivingThing living : World.getLivingEntityList()) {
                for (java.lang.reflect.Method method : living.getClass().getMethods()) {
                    if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                        found = true;
                    }
                }
            }
            if (!found) {
                withoutSetter.add(key + "（全注册表里都找不到 " + setterName + "()）");
            }
        }
        check("classState：放行的 " + DataKeys.CLASS_CONFIG.size()
                + " 个键<b>每一个都有 setter</b>（写回是按 Java 字段名拼 setXxx）—— 没有 setter 的 "
                + withoutSetter, withoutSetter.isEmpty());

        /* ---------- ② 白厄：火种 / 上限 / 灾厄 / 灾厄上限 ---------- */
        LivingThing phainonBase = entityTemplateOf("game_official_content:phainon");
        if (phainonBase == null) {
            return;
        }
        Phainon phainon = (Phainon) phainonBase;
        cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Snapshot phainonSnapshot = cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Snapshot.of(phainon);
        try {
            java.util.Set<String> onPhainon = new java.util.TreeSet<>();
            for (DataBridge.DataAccessor accessor
                    : cn.gfhnv.game.system.configLoadingSystem.ReflectionConfigBridge
                    .candidates(phainon)) {
                if (DataKeys.CLASS_CONFIG.contains(accessor.name())) {
                    onPhainon.add(accessor.name());
                }
            }
            check("classState：数据名就在反射面上（键名 = /data 数据名，不需要另起一套别名）—— 白厄身上有 "
                    + onPhainon, onPhainon.containsAll(Arrays.asList(
                    "coreflame", "coreflameMax", "soulscorch", "scourge", "scourgeMax")));

            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Report fire = cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.applyJson(
                    "{\"entities\":{\"game_official_content:phainon\":{\"classState\":"
                            + "{\"coreflameMax\":20,\"coreflame\":18,\"scourgeMax\":9,\"scourge\":5,"
                            + "\"soulscorch\":3}}}}", "自测-classState-白厄");
            System.out.println("  [信息] 白厄 classState 补丁：应用 " + fire.appliedEntries()
                    + "，跳过 " + fire.skippedEntries());
            check("classState 生效：classState 块里的 5 个键全部落地、没有跳过 —— " + fire.errors()
                            + fire.skippedEntries(),
                    fire.isClean() && fire.appliedEntries().size() == 5);
            check("classState 生效：coreflameMax 真的变成 20（旧值见快照）",
                    phainon.getCoreflameMax() == 20);
            check("classState 生效：coreflame 真的变成 18（setter 仍然夹到上限）",
                    phainon.getCoreflame() == 18);
            check("classState 生效：scourgeMax 9 / scourge 5 / soulscorch 3",
                    phainon.getScourgeMax() == 9 && phainon.getScourge() == 5
                            && phainon.getSoulscorch() == 3);
            // setter 的钳制还在：写超过上限的值会被压到上限（不是"裸写字段"）
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.applyJson("{\"entities\":{\"game_official_content:phainon\":"
                    + "{\"coreflame\":999}}}", "自测-classState-钳制");
            check("classState 生效：写超过上限的 coreflame 会被 setter 夹到 coreflameMax（钳制没被绕开）—— 999 → "
                    + phainon.getCoreflame(), phainon.getCoreflame() == phainon.getCoreflameMax());

            // 顺序保证：`JSONObject` 背后是 HashMap，**用户写的键顺序靠不住**，
            // 所以这一层必须自己保证"上限键先写"（`ReflectionConfigBridge#upperBoundFirst`）。
            // 这条把键**故意写反**（先值 99、后上限 21）：只要上限先应用，99 就会被夹到 21；
            // 反过来说，如果哪天有人把这层改成"照 JSON 顺序写"，这里会红。
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.applyJson("{\"entities\":{\"game_official_content:phainon\":"
                    + "{\"classState\":{\"coreflame\":99,\"coreflameMax\":21}}}}", "自测-classState-顺序");
            check("classState 生效：补丁里\"先值后上限\"也先抬上限（不靠 JSON 的键顺序）—— coreflame "
                            + phainon.getCoreflame() + " / coreflameMax " + phainon.getCoreflameMax(),
                    phainon.getCoreflameMax() == 21 && phainon.getCoreflame() == 21);

            // 星魂 6 会让 setCoreflame 不再夹上限，所以 eidolon 也必须排在 coreflame 之前写；
            // 这条故意把 eidolon 与 coreflame 放在同一个补丁里，验"同一个补丁里升魂 + 写超上限"能一起生效。
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.applyJson(
                    "{\"entities\":{\"game_official_content:phainon\":"
                            + "{\"classState\":{\"eidolon\":6,\"coreflame\":99}}}}",
                    "自测-classState-星魂顺序");
            check("classState 生效：同一补丁里先升 6 魂再写 99 火种（不被旧上限夹）—— eidolon "
                            + phainon.getEidolon() + " / coreflame " + phainon.getCoreflame(),
                    phainon.getEidolon() == 6 && phainon.getCoreflame() == 99);

            // 裸键写法等价（两种写法都认）
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Report bare = cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.applyJson(
                    "{\"entities\":{\"game_official_content:phainon\":{\"coreflame\":7}}}",
                    "自测-classState-裸键");
            check("classState 生效：不套 classState 块、直接写在实体下面也认（裸键写法）—— 应用 "
                            + bare.appliedEntries(),
                    bare.isClean() && bare.appliedEntries().size() == 1
                            && phainon.getCoreflame() == 7);
        } finally {
            phainonSnapshot.restore();
        }

        /* ---------- ③ 李晓焰：燃点 ---------- */
        LivingThing liXiaoYanBase = entityTemplateOf("game_official_content:actorLiXiaoYan");
        if (liXiaoYanBase instanceof ActorLiXiaoYan liXiaoYan) {
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Snapshot snapshot =
                    cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Snapshot.of(liXiaoYan);
            try {
                int max = liXiaoYan.getIgnitionMax();
                int wanted = Math.min(5, max);
                cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Report ignition =
                        cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.applyJson(
                                "{\"entities\":{\"game_official_content:actorLiXiaoYan\":"
                                        + "{\"classState\":{\"ignition\":" + wanted + "}}}}",
                                "自测-classState-燃点");
                check("classState 生效：李晓焰的 ignition 能配（上限 " + max + "，写 " + wanted
                                + "）—— 应用 " + ignition.appliedEntries() + "，错误 " + ignition.errors(),
                        ignition.appliedEntries().size() == 1 && liXiaoYan.getIgnition() == wanted);
            } finally {
                snapshot.restore();
            }
        }

        /* ---------- ④ 运行时状态仍然被挡（而且要说得出为什么） ---------- */
        List<String> runtimeNotesMissing = new ArrayList<>();
        for (String key : DataKeys.CLASS_RUNTIME) {
            String hint = DataKeys.hintFor(key);
            if (hint == null || hint.isBlank()) {
                runtimeNotesMissing.add(key);
            }
        }
        check("classState：继续挡住的 " + DataKeys.CLASS_RUNTIME.size()
                + " 个运行时状态键<b>每一个都写了理由</b>（不许只说「这个键不属于实体数据」）—— 没写理由的 "
                + runtimeNotesMissing, runtimeNotesMissing.isEmpty());

        cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Snapshot awakenSnapshot = cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Snapshot.of(phainon);
        try {
            boolean awakenOnSurface = false;
            for (DataBridge.DataAccessor accessor
                    : cn.gfhnv.game.system.configLoadingSystem.ReflectionConfigBridge
                    .candidates(phainon)) {
                if ("awaken".equals(accessor.name())) {
                    awakenOnSurface = true;
                }
            }
            boolean before = phainon.isAwaken();
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Report blocked = cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.applyJson(
                    "{\"entities\":{\"game_official_content:phainon\":"
                            + "{\"classState\":{\"awaken\":true,\"extraTurns\":9}}}}",
                    "自测-classState-运行时");
            System.out.println("  [信息] 运行时状态：写 isAwaken/extraTurns（awaken 在反射面上： "
                    + awakenOnSurface + "）→ 应用 " + blocked.appliedEntries()
                    + "，跳过 " + blocked.skippedEntries());
            check("classState：isAwaken / extraTurns 写不进去（还是「未知键 + 为什么」，不是静默生效）"
                            + "—— 觉醒状态 " + before + " → " + phainon.isAwaken(),
                    blocked.appliedEntries().isEmpty()
                            && blocked.skippedEntries().toString().contains("awaken")
                            && blocked.skippedEntries().toString().contains("extraTurns")
                            && String.valueOf(before).equals(String.valueOf(phainon.isAwaken())));
        } finally {
            awakenSnapshot.restore();
        }

        /* ---------- ⑤ 默认值：这一段必须出现在"当前全量默认值"里 ---------- */
        // 只"能配"不够 —— 用户看不见键名就等于没配（这一条防的正是"能配但没人知道"）。
        Map<String, cn.gfhnv.game.data.NbtTag> phainonClassState =
                cn.gfhnv.game.system.configLoadingSystem.ReflectionConfigBridge
                        .classStateValues(phainon);
        System.out.println("  [信息] classState 导出（白厄）：" + phainonClassState);
        check("classState：这一段的默认值能被 dump 出来（键名 + 当前值），白厄写出 "
                        + phainonClassState.size() + " 个键",
                phainonClassState.keySet().containsAll(Arrays.asList(
                        "coreflame", "coreflameMax", "soulscorch", "scourge", "scourgeMax",
                        "extraAbilityTier")));
    }

    /**
     * <b>模组内容不进游戏自己的配置</b>（{@code EntityData.json} / {@code SkillData.json}）。
     * <p>
     * 用户意见："模组配置不能放在这个里面"。自愈生成的那两份是<b>游戏自己</b>的配置，
     * 模组内容走 {@code config/data/<模组id>.json}（{@code @ModConfig} / {@code MOD_ID}）。
     * <p>
     * <b>怎么造出"一个模组内容"</b>：自测里没有加载 {@code mods/}（那是真游戏启动的事），
     * 所以这里现造一个最小模组（{@link Mod#addEntity} 会按 {@code MOD_ID} 给 id 加前缀），
     * 再往注册表里放一个官方实体 —— 两边同时存在，才能证明筛的是"谁注册的"而不是"id 长什么样"。
     * <p>
     * 探针的字段全部带 {@code @NoConfig}（见 {@link ConfigReflectionProbeEntity}），
     * 所以它出现在默认文件里只可能是"模组内容没被摘出去"。
     * 跑完把探针、模组、临时目录全部还原。
     */
    private static void testModContentIsNotInGameConfig() throws Exception {
        section("模组内容不进游戏自己的配置（EntityData / SkillData 只含官方内容）");

        cn.gfhnv.game.world.World.addMod(new Mod("selfTestMod") {
        });
        ConfigReflectionProbeEntity probe = ConfigReflectionProbeEntity.probe();
        Mod owner = cn.gfhnv.game.world.World.getModList()
                .get(cn.gfhnv.game.world.World.getModList().size() - 1);
        owner.addEntity(probe);
        // Mod#addEntity 只进"这个模组自己的表"（并给 id 加前缀）；进全局注册表要再走一次
        // registerItself() —— 真游戏里由模组加载链在 invokeWhenLoaded() 之后调它。
        owner.registerItself();
        String probeId = probe.getId();
        try {
            check("前提：探针以模组内容的身份进了注册表（id = " + probeId + "，拥有者 "
                            + owner.getMOD_ID() + "）",
                    probeId != null && probeId.startsWith("selfTestMod:")
                            && cn.gfhnv.game.world.World.getEntityList().contains(probe)
                            && !cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter
                            .isOfficialContent(probe));

            org.json.JSONObject entities =
                    new org.json.JSONObject(
                            cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter
                                    .entityDataJson()).getJSONObject("entities");
            org.json.JSONObject skills =
                    new org.json.JSONObject(
                            cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter
                                    .skillDataJson()).getJSONObject("skills");
            List<String> leaked = new ArrayList<>();
            if (entities.has(probeId)) {
                leaked.add("EntityData/" + probeId);
            }
            for (String key : skills.keySet()) {
                if (!cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter
                        .isOfficialSkillKey(key)) {
                    leaked.add("SkillData/" + key);
                }
            }
            check("模组内容：模组注册的实体<b>不</b>出现在 EntityData.json 里（id = " + probeId
                    + "）—— 泄漏 " + leaked, !entities.has(probeId));
            check("模组内容：SkillData.json 里<b>没有</b>任何非官方实体的技能 —— 泄漏 " + leaked,
                    leaked.isEmpty());
            check("模组内容：官方内容照旧写出去（没有「一刀切把内容都砍掉」）—— 实体 "
                            + entities.keySet().size() + " 个、技能 " + skills.keySet().size() + " 个",
                    entities.has("game_official_content:playerOne") && skills.length() > 0);

            // 真实写盘路径也要过一次（落盘那一步走的是同一份生成器）
            java.io.File tempDir = new java.io.File("./out/tmpOfficialOnly");
            deleteDirectory(tempDir);
            try {
                cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter.writeAll(tempDir);
                String entityText = java.nio.file.Files.readString(
                        new java.io.File(tempDir,
                                cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter
                                        .ENTITY_FILE_NAME).toPath(),
                        java.nio.charset.StandardCharsets.UTF_8);
                String skillText = java.nio.file.Files.readString(
                        new java.io.File(tempDir,
                                cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter
                                        .SKILL_FILE_NAME).toPath(),
                        java.nio.charset.StandardCharsets.UTF_8);
                check("模组内容：落盘的两份文件里也找不到模组内容（写盘路径与生成器同一口径）—— "
                                + probeId,
                        !entityText.contains(probeId) && !skillText.contains("selfTestMod:"));
            } finally {
                deleteDirectory(tempDir);
            }
        } finally {
            cn.gfhnv.game.world.World.removeEntity(probe);
            owner.removeEntity(probe);
            cn.gfhnv.game.world.World.removeMod(owner);
        }
        check("模组内容：用例跑完把探针与假模组从注册表摘掉了（不给后面的用例留污染）—— 注册表里还有 "
                        + (cn.gfhnv.game.world.World.getEntityList().contains(probe) ? "它" : "没有它"),
                !cn.gfhnv.game.world.World.getEntityList().contains(probe));

        testRealModContentIsNotInGameConfig();
    }

    /**
     * 跑本主题的全部用例（顺序 = 下面列的顺序）。
     */
    public static void runAll() throws Exception {
        testConfigDefaultsAndPatch();
        testClassStateConfigKeys();
        testModContentIsNotInGameConfig();
        testReflectionDrivenConfig();
    }
}
