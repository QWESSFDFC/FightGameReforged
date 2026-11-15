package cn.gfhnv.debug_tools;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.DamageEvent;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.world.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static cn.gfhnv.debug_tools.TestKit.check;
import static cn.gfhnv.debug_tools.TestKit.section;
import static cn.gfhnv.debug_tools.TestSupport.entityLoadProbe;

/**
 * 自测：**配置**。
 * <p>
 * 主题：GameRules.json 的魔法数字与血上限双入口。
 * <p>
 * 由 {@code TestCommandSystem#main} 按顺序调用 {@code GameRulesTests#runAll()}。
 */
public final class GameRulesTests {

    private GameRulesTests() {
    }

    /**
     * 游戏规则（{@code config/gameConfig/GameRules.json}，魔法数字 / BOSS 旋钮）。
     * <p>
     * 与实体、技能不同，规则值的默认值住在<b>代码公式</b>里，所以这里验的不是"对象被改对了"，
     * 而是三件事：
     * <ol>
     *     <li>生成器写出来的每一项都等于 {@link cn.gfhnv.game.system.configLoadingSystem.RuleDefaults}
     *     里的出厂值（两边不许各抄一份字面量）；</li>
     *     <li>把"全量默认值"读进去之后，世界与读之前逐字段一致；</li>
     *     <li>改了某条规则之后，<b>新造出来的实体/技能真的按新值算</b>——
     *     这条必须配"出厂值复现"：拿旧值再走一遍，结果必须回到原样。</li>
     * </ol>
     * 用例跑完把规则表清空（模板已经吃进旧值，新造的则回到出厂值，于是整体回到原状）。
     */
    private static void testGameRules() throws Exception {
        section("游戏规则（阶段 3）");

        String rulesJson = cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter.gameRulesJson();
        org.json.JSONObject rulesRoot = new org.json.JSONObject(rulesJson);
        check("规则生成器：GameRules 能被 org.json 解析，且带 version 与各段",
                rulesRoot.has("version") && rulesRoot.optJSONObject("formula") != null
                        && rulesRoot.optJSONObject("mana") != null
                        && rulesRoot.optJSONObject("actorLiXiaoYan") == null
                        && rulesRoot.optJSONObject("flameReaver") == null);
        check("规则生成器：version 是数字 1",
                cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher
                        .asLong(rulesRoot.opt("version")) == 1L);

        /* ---------- ① 生成器写出来的就是 RuleKeySpecs 那张表（唯一真相） ---------- */
        Map<String, Object> defaults =
                cn.gfhnv.game.system.configLoadingSystem.RuleKeySpecs.defaults();
        List<String> mismatched = new ArrayList<>();
        for (String key : cn.gfhnv.game.system.configLoadingSystem.GameRules.allKeys()) {
            int dot = key.indexOf('.');
            org.json.JSONObject section = rulesRoot.optJSONObject(key.substring(0, dot));
            Object written = section == null ? null : section.opt(key.substring(dot + 1));
            Object expected = defaults.get(key);
            if (written == null || !(expected instanceof Number)
                    || Double.compare(((Number) written).doubleValue(),
                    ((Number) expected).doubleValue()) != 0) {
                mismatched.add(key + "（写出去 " + written + "，出厂值 " + expected + "）");
            }
        }
        check("规则生成器：每一项都等于 RuleKeySpecs 里的出厂值（两边不许各抄一份字面量）—— 不一致的 "
                + mismatched, mismatched.isEmpty());
        check("规则键契约（阶段 3）：GameRules.knownKeys() == RuleKeySpecs 表里的键，一条都不少"
                        + "（表里 " + cn.gfhnv.game.system.configLoadingSystem.RuleKeySpecs.ALL.size()
                        + " 条，认识 " + cn.gfhnv.game.system.configLoadingSystem.GameRules
                        .knownKeys().size() + " 条）",
                cn.gfhnv.game.system.configLoadingSystem.RuleKeySpecs.ALL.size() == 8
                        && cn.gfhnv.game.system.configLoadingSystem.GameRules.knownKeys()
                        .equals(cn.gfhnv.game.system.configLoadingSystem.RuleKeySpecs.names()));
        // 2026-11-14 第 52 批：虫皇那一段（insectBoss.baseHpMax）删了 —— 它的血量改走
        // EntityData.json 的 derived.baseHpMax（同一个值不再有两个入口）
        check("规则键契约：段名也来自那张表（GameRulesPatcher 不再手写 4 个字面量）—— 实际 "
                        + cn.gfhnv.game.system.configLoadingSystem.RuleKeySpecs.sections(),
                cn.gfhnv.game.system.configLoadingSystem.RuleKeySpecs.sections().size() == 2);
        boolean ruleDefaultsStillHasTable;
        try {
            Class.forName("cn.gfhnv.game.system.configLoadingSystem.RuleDefaults")
                    .getDeclaredMethod("all");
            ruleDefaultsStillHasTable = true;
        } catch (ClassNotFoundException | NoSuchMethodException expected) {
            ruleDefaultsStillHasTable = false;
        }
        check("规则键契约：RuleDefaults 那份手写的「键 → 值」表已经删掉（all() 不在，防止被加回来）",
                !ruleDefaultsStillHasTable);
        check("规则生成器：认识 8 条规则键（formula 6 + mana 2；"
                        + "所有实体数值都已搬进实体自己的配置面）"
                        + "—— 实际 " + cn.gfhnv.game.system.configLoadingSystem.GameRules.allKeys().size(),
                cn.gfhnv.game.system.configLoadingSystem.GameRules.allKeys().size() == 8);

        /* ---------- ② 文档点名的 10 个 BOSS 旋钮都在 ---------- */
        // 2026-11-14 第 53 批：盗火行者的旋钮已搬进 FlameReaver 的 classState ——
        // 这里改成断言"它们在 CLASS_CONFIG 里可配"，而不再是规则键。
        List<String> movedKnobs = Arrays.asList(
                "phaseTwoDamageReduction", "completeContainerChance",
                "damageReductionLayersDefault", "containerLimit");
        List<String> movedMissing = new ArrayList<>();
        for (String key : movedKnobs) {
            if (!cn.gfhnv.game.system.configLoadingSystem.DataKeys.CLASS_CONFIG.contains(key)) {
                movedMissing.add(key);
            }
        }
        check("规则覆盖面：盗火行者的 4 个旋钮搬进实体配置面（classState）后仍然可配 —— 缺 " + movedMissing,
                movedMissing.isEmpty() && !cn.gfhnv.game.system.configLoadingSystem.GameRules
                        .isKnown("flameReaver.containerLimit"));
        List<String> mustExist = Arrays.asList(
                "formula.hpBase", "formula.defenceBase", "formula.attackBase",
                "formula.levelDefenceFactor", "formula.levelDefenceBase",
                "mana.mainBase", "mana.otherBase");
        List<String> missing = new ArrayList<>();
        for (String key : mustExist) {
            if (!cn.gfhnv.game.system.configLoadingSystem.GameRules.isKnown(key)
                    || !defaults.containsKey(key)) {
                missing.add(key);
            }
        }
        check("规则覆盖面：设计文档 §4.3 / §7 阶段 3 点名的旋钮全部可配 —— 缺 " + missing,
                missing.isEmpty());
        check("规则契约：PHASE_TWO_HP_THRESHOLD 没有回来（二阶段触发是「血条第一次被清空」，不是血量比例）",
                !cn.gfhnv.game.system.configLoadingSystem.GameRules
                        .isKnown("flameReaver.phaseTwoHpThreshold")
                        && !rulesJson.contains("phaseTwoHpThreshold"));

        /* ---------- ③ 出厂值对照：改之前先确认规则表是空的 ---------- */
        check("前提：规则表在自测里是空的（一切按出厂值走）—— 已配置项 "
                        + cn.gfhnv.game.system.configLoadingSystem.GameRules.current().size(),
                cn.gfhnv.game.system.configLoadingSystem.GameRules.current().isEmpty());

        /* ---------- ④ 全量默认值读进去：世界逐字段不变 ---------- */
        Map<String, String> beforeEntities = new java.util.LinkedHashMap<>();
        for (LivingThing living : World.getLivingEntityList()) {
            beforeEntities.put(living.getId(),
                    cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.stateOf(living));
        }
        cn.gfhnv.game.system.configLoadingSystem.GameRules.beginLoad();
        cn.gfhnv.game.system.configLoadingSystem.GameRulesPatcher.Report all =
                cn.gfhnv.game.system.configLoadingSystem.GameRulesPatcher
                        .applyJson(rulesJson, "自测-规则全量默认值");
        cn.gfhnv.game.system.configLoadingSystem.GameRules.freeze();
        check("规则补丁：全量默认值一项都不落地报错 —— 错误 " + all.errors(), all.errors().isEmpty());
        check("规则补丁：全量默认值没有跳过项 —— 跳过 " + all.skippedEntries(),
                all.skippedEntries().isEmpty());
        check("规则补丁：8 条规则全部生效 —— 实际 " + all.appliedEntries().size(),
                all.appliedEntries().size() == 8);
        List<String> changed = new ArrayList<>();
        for (LivingThing living : World.getLivingEntityList()) {
            String now = cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.stateOf(living);
            if (!now.equals(beforeEntities.get(living.getId()))) {
                changed.add(living.getId());
            }
        }
        check("规则补丁：把当前默认值读回去，已有模板逐字段不变（规则不改已经造出来的对象）—— 变了 "
                + changed, changed.isEmpty());

        /* ---------- ⑤ 改了真的生效：公式键（新造一个实体看面板） ---------- */
        cn.gfhnv.game.system.configLoadingSystem.GameRules.beginLoad();
        cn.gfhnv.game.system.configLoadingSystem.GameRulesPatcher.applyJson(
                "{\"version\":1,\"formula\":{\"hpBase\":1000,\"defenceBase\":3000,"
                        + "\"attackBase\":500}}", "自测-改公式基座");
        cn.gfhnv.game.system.configLoadingSystem.GameRules.freeze();
        // 必须**直接造一个**，不能拿注册表模板 copy()：copy 走的是复制构造器，
        // 它的三围是从原体抄过来的（不重算），所以公式类规则只影响"之后新建的实体"。
        cn.gfhnv.game.officialStuff.customEntity.players.PlayerOne probe =
                new cn.gfhnv.game.officialStuff.customEntity.players.PlayerOne(125);
        long level = probe.getLevel();
        check("规则生效：改了 formula.hpBase / defenceBase / attackBase，新造实体的面板跟着变"
                        + "（hpMax=" + probe.getHpMax() + "，防御=" + probe.getDefence()
                        + "，攻击=" + probe.getAttack() + "，等级 " + level + "）",
                probe.getHpMax() == (level - 1) * 36 + 1000
                        && probe.getDefence() == (level - 1) * 5 + 3000
                        && probe.getAttack() == 500 + 29 * (level - 1));

        /* ---------- ⑥ 改了真的生效：初始法力基座 ---------- */
        cn.gfhnv.game.system.configLoadingSystem.GameRules.beginLoad();
        cn.gfhnv.game.system.configLoadingSystem.GameRulesPatcher.applyJson(
                "{\"version\":1,\"mana\":{\"mainBase\":999,\"otherBase\":77}}", "自测-改法力基座");
        cn.gfhnv.game.system.configLoadingSystem.GameRules.freeze();
        cn.gfhnv.game.officialStuff.customEntity.players.PlayerOne manaProbe =
                new cn.gfhnv.game.officialStuff.customEntity.players.PlayerOne(125);
        boolean mainOk = false;
        boolean otherOk = true;
        for (cn.gfhnv.game.system.mana.Mana mana : manaProbe.getManas()) {
            boolean isMain = mana.getElementSort() == manaProbe.getElementSort();
            double grow = switch (mana.getElementSort()) {
                case METAL -> manaProbe.getMetalManaGrow();
                case WOOD -> manaProbe.getWoodManaGrow();
                case WATER -> manaProbe.getWaterManaGrow();
                case FIRE -> manaProbe.getFireManaGrow();
                default -> manaProbe.getDirtManaGrow();
            };
            double expected = grow * (manaProbe.getLevel() - 1) + (isMain ? 999 : 77);
            if (isMain) {
                mainOk = mana.getAmountMax() == expected;
            } else if (mana.getAmountMax() != expected) {
                otherOk = false;
            }
        }
        check("规则生效：改了 mana.mainBase / otherBase，新造实体的法力上限跟着变（主元素 " + mainOk
                + "，其余 " + otherOk + "）", mainOk && otherOk);

        /* ---------- ⑧ 伤害公式的标定护栏：改了 levelDefenceFactor 结果必须跟着变 ---------- */
        LivingThing attacker = entityLoadProbe("game_official_content:commonInsect");
        attacker.setAttack(1000);
        LivingThing victim = entityLoadProbe("game_official_content:playerOne");
        Skill damageSkill = cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher
                .find("game_official_content:iceInsect#冰冻");
        cn.gfhnv.game.system.configLoadingSystem.GameRules.beginLoad();
        cn.gfhnv.game.system.configLoadingSystem.GameRules.freeze();
        long baselineDamage = new DamageEvent(attacker, victim, damageSkill)
                .getDamage().getDamageAmount();
        check("规则前提：不配任何规则时伤害是一个正数（" + baselineDamage + "）", baselineDamage > 0);

        /* ---------- ⑨ 容错：未知段 / 未知键 / 类型不对 ---------- */
        cn.gfhnv.game.system.configLoadingSystem.GameRules.beginLoad();
        cn.gfhnv.game.system.configLoadingSystem.GameRulesPatcher.Report broken =
                cn.gfhnv.game.system.configLoadingSystem.GameRulesPatcher.applyJson(
                        "{\"version\":1,"
                                + "\"formula\":{\"hpBase\":\"多\",\"nope\":1},"
                                + "\"没这个段\":{\"x\":1},"
                                + "\"mana\":{\"mainBase\":123} }", "自测-规则容错");
        cn.gfhnv.game.system.configLoadingSystem.GameRules.freeze();
        String brokenText = broken.errors() + " " + broken.skippedEntries();
        check("规则容错：一个坏键不再废掉整份配置（同一份里其余项照常生效）—— 跳过 "
                        + broken.skippedEntries() + "，错误 " + broken.errors(),
                broken.skippedEntries().size() == 3 && broken.errors().isEmpty()
                        && broken.appliedEntries().size() == 1);
        check("规则容错：类型不对的项被点名道姓（键名 + 实际类型）",
                brokenText.contains("formula.hpBase") && brokenText.contains("字符串"));
        check("规则容错：未知键与未知段都被点名（不再静默忽略）",
                brokenText.contains("formula.nope") && brokenText.contains("没这个段"));
        check("规则容错：坏值没有被写进去（formula.hpBase 仍是出厂值）",
                cn.gfhnv.game.system.configLoadingSystem.GameRules
                        .getLong("formula.hpBase") == 200L);

        /* ---------- ⑩ 冻结：规则表加载完之后不许再改 ---------- */
        cn.gfhnv.game.system.configLoadingSystem.GameRules.freeze();
        boolean rejectedAfterFreeze = !cn.gfhnv.game.system.configLoadingSystem.GameRules
                .put("mana.mainBase", 1L);
        check("规则契约：冻结之后拒绝写入（很多使用点是「Java 类的静态常量在类加载时读一次」，"
                + "表要是能中途变值，行为就取决于类加载顺序）", rejectedAfterFreeze);

        /* ---------- ⑪ 收尾：清空规则表 ---------- */
        cn.gfhnv.game.system.configLoadingSystem.GameRules.resetForTest();
        check("规则自测收尾：规则表已清空（后面用例看到的是「什么都没配」的出厂状态）—— 剩余 "
                        + cn.gfhnv.game.system.configLoadingSystem.GameRules.current().size() + " 项",
                cn.gfhnv.game.system.configLoadingSystem.GameRules.current().isEmpty());
        check("规则自测收尾：清空之后取到的是出厂值（mana.mainBase = 200）",
                cn.gfhnv.game.system.configLoadingSystem.GameRules
                        .getLong("mana.mainBase") == 200L);
    }

    /**
     * 跑本主题的全部用例（顺序 = 下面列的顺序）。
     */
    public static void runAll() throws Exception {
        testGameRules();
    }
}
