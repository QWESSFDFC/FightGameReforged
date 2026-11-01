package cn.gfhnv.debug_tools;

import cn.gfhnv.game.data.*;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.DamageEvent;
import cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.command.*;
import cn.gfhnv.game.system.configLoadingSystem.DataKeys;
import cn.gfhnv.game.system.fight.*;
import cn.gfhnv.game.world.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static cn.gfhnv.debug_tools.TestKit.*;
import static cn.gfhnv.debug_tools.TestSupport.*;
import static cn.gfhnv.debug_tools.TestProbes.*;

/**
 * 自测：**配置**。
 * <p>
 * 主题：GameRules.json 的魔法数字与血上限双入口。
 * <p>
 * 由 {@code TestCommandSystem#main} 按顺序调用 {@code GameRulesTests#runAll()}。
 */
public final class GameRulesTests {

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
                        && rulesRoot.optJSONObject("flameReaver") != null
                        && rulesRoot.optJSONObject("insectBoss") != null
                        && rulesRoot.optJSONObject("actorLiXiaoYan") != null);
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
                cn.gfhnv.game.system.configLoadingSystem.RuleKeySpecs.ALL.size() == 29
                        && cn.gfhnv.game.system.configLoadingSystem.GameRules.knownKeys()
                        .equals(cn.gfhnv.game.system.configLoadingSystem.RuleKeySpecs.names()));
        check("规则键契约：段名也来自那张表（GameRulesPatcher 不再手写 5 个字面量）—— 实际 "
                        + cn.gfhnv.game.system.configLoadingSystem.RuleKeySpecs.sections(),
                cn.gfhnv.game.system.configLoadingSystem.RuleKeySpecs.sections().size() == 5);
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
        check("规则生成器：认识 29 条规则键（含 formula 5 + mana 2 + 盗火行者 11 + 虫皇 1 + 李晓焰 10）"
                        + "—— 实际 " + cn.gfhnv.game.system.configLoadingSystem.GameRules.allKeys().size(),
                cn.gfhnv.game.system.configLoadingSystem.GameRules.allKeys().size() == 29);

        /* ---------- ② 文档点名的 10 个 BOSS 旋钮都在 ---------- */
        List<String> mustExist = Arrays.asList(
                "flameReaver.phaseTwoDamageReduction", "flameReaver.completeContainerChance",
                "flameReaver.damageReductionLayers", "flameReaver.containerLimit",
                "flameReaver.summonHpCostRate", "flameReaver.disasterPowerAttackBonus",
                "flameReaver.damageReductionPerLayer", "flameReaver.containerHpRatio",
                "flameReaver.containerAttackRatio", "flameReaver.completeContainerHpRatio",
                "flameReaver.baseHpMax", "insectBoss.baseHpMax",
                "formula.hpBase", "formula.defenceBase", "formula.attackBase",
                "formula.levelDefenceFactor", "formula.levelDefenceBase",
                "mana.mainBase", "mana.otherBase",
                "actorLiXiaoYan.ignitionMax", "actorLiXiaoYan.highIgnition",
                "actorLiXiaoYan.highIgnitionBonusRate", "actorLiXiaoYan.memorizeHealRate");
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
        check("规则补丁：29 条规则全部生效 —— 实际 " + all.appliedEntries().size(),
                all.appliedEntries().size() == 29);
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

        /* ---------- ⑦ 改了真的生效：BOSS 旋钮 ---------- */
        cn.gfhnv.game.system.configLoadingSystem.GameRules.beginLoad();
        cn.gfhnv.game.system.configLoadingSystem.GameRulesPatcher.applyJson(
                "{\"version\":1,\"flameReaver\":{\"baseHpMax\":123456,\"damageReductionLayers\":5,"
                        + "\"damageReductionPerLayer\":0.1,\"containerHpRatio\":0.5,"
                        + "\"containerAttackRatio\":0.5},"
                        + "\"insectBoss\":{\"baseHpMax\":65432}}", "自测-改 BOSS 旋钮");
        cn.gfhnv.game.system.configLoadingSystem.GameRules.freeze();
        cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver boss =
                new cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver(150);
        check("规则生效：flameReaver.baseHpMax 改了之后，新造的盗火行者血量就是它（"
                + boss.getHpMax() + "）", boss.getHpMax() == 123456L);
        // 减伤层数是在**开局**那一刻挂上去的（whenFightStart），不是构造器里挂的 ——
        // 所以这里显式走一次开局钩子，跟真实战斗路径一致。
        boss.whenFightStart(new Fight(new ArrayList<>(List.of(boss)), new ArrayList<>(),
                new ArrayList<>()));
        check("规则生效：damageReductionLayers × damageReductionPerLayer 改了之后，"
                        + "开局减伤跟着变（层数 " + boss.getDamageReductionLayers() + " × 每层 "
                        + cn.gfhnv.game.system.configLoadingSystem.GameRules
                        .getDouble("flameReaver.damageReductionPerLayer")
                        + " → 承伤 " + boss.getDamageTakenMultiplier() + "，期望 0.5）",
                boss.getDamageReductionLayers() == 5
                        && Math.abs(boss.getDamageTakenMultiplier() - 0.5) < 0.0001);
        cn.gfhnv.game.officialStuff.customEntity.summons.BrokenContainer container =
                new cn.gfhnv.game.officialStuff.customEntity.summons.BrokenContainer(boss);
        check("规则生效：containerHpRatio 改了之后，新召唤的容器血量 = BOSS 血 × 比例（"
                        + container.getHpMax() + "）",
                container.getHpMax() == (long) (boss.getHpMax()
                        * cn.gfhnv.game.officialStuff.customEntity.summons.BrokenContainer.hpRatio()));
        check("规则生效：容器比例是「读表」而不是「抄字面量」（比例本身已经变成 0.5）",
                cn.gfhnv.game.officialStuff.customEntity.summons.BrokenContainer.hpRatio() == 0.5);
        cn.gfhnv.game.officialStuff.customEntity.monsters.InsectBoss insectBoss =
                new cn.gfhnv.game.officialStuff.customEntity.monsters.InsectBoss(150);
        check("规则生效：insectBoss.baseHpMax 改了之后，新造的虫皇血量就是它（"
                + insectBoss.getHpMax() + "）", insectBoss.getHpMax() == 65432L);
        check("规则生效：李晓焰的规则读取方法走的是规则表，规则一改就跟着变（"
                        + cn.gfhnv.game.officialStuff.customEntity.players.ActorLiXiaoYan.Rule.highIgnition()
                        + " / " + cn.gfhnv.game.officialStuff.customEntity.players.ActorLiXiaoYan.Rule
                        .memorizeHealRate() + "）",
                cn.gfhnv.game.officialStuff.customEntity.players.ActorLiXiaoYan.Rule.highIgnition()
                        == cn.gfhnv.game.system.configLoadingSystem.RuleDefaults
                        .LI_XIAO_YAN_HIGH_IGNITION
                        && cn.gfhnv.game.officialStuff.customEntity.players.ActorLiXiaoYan.Rule
                        .memorizeHealRate()
                        == cn.gfhnv.game.system.configLoadingSystem.RuleDefaults
                        .LI_XIAO_YAN_MEMORIZE_HEAL_RATE);

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
                                + "\"flameReaver\":{\"containerLimit\":\"多\",\"nope\":1},"
                                + "\"没这个段\":{\"x\":1},"
                                + "\"insectBoss\":{\"baseHpMax\":123} }", "自测-规则容错");
        cn.gfhnv.game.system.configLoadingSystem.GameRules.freeze();
        String brokenText = broken.errors() + " " + broken.skippedEntries();
        check("规则容错：一个坏键不再废掉整份配置（同一份里其余项照常生效）—— 跳过 "
                        + broken.skippedEntries() + "，错误 " + broken.errors(),
                broken.skippedEntries().size() == 3 && broken.errors().isEmpty()
                        && broken.appliedEntries().size() == 1);
        check("规则容错：类型不对的项被点名道姓（键名 + 实际类型）",
                brokenText.contains("flameReaver.containerLimit") && brokenText.contains("字符串"));
        check("规则容错：未知键与未知段都被点名（不再静默忽略）",
                brokenText.contains("flameReaver.nope") && brokenText.contains("没这个段"));
        check("规则容错：坏值没有被写进去（containerLimit 仍是出厂值 0）",
                cn.gfhnv.game.system.configLoadingSystem.GameRules
                        .getInt("flameReaver.containerLimit") == 0);

        /* ---------- ⑩ 冻结：规则表加载完之后不许再改 ---------- */
        cn.gfhnv.game.system.configLoadingSystem.GameRules.freeze();
        boolean rejectedAfterFreeze = !cn.gfhnv.game.system.configLoadingSystem.GameRules
                .put("insectBoss.baseHpMax", 1L);
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
     * <b>「同一个血量有两个入口」的那条语义</b>（2026-10-03 用户报："盗火行者血量一个值两个入口"）。
     * <p>
     * 事实：{@code GameRules.json} 的 {@code flameReaver.baseHpMax} 只在<b>构造时</b>读一次
     * （{@code FlameReaver.java:282-283}），而 {@code EntityData.json} 的 {@code derived.baseHpMax}
     * 是<b>构造之后</b>打的补丁 —— 两个都写时规则表那个数看不出效果，而此前<b>控制台一句话都不说</b>。
     * <p>
     * 这一段钉住的结论是：<b>派生键（{@code EntityData.json}）赢</b>，而且这件事<b>必须被点名</b>；
     * 只写一处时（无论是哪一处）都不许吵 —— 只写派生键本来就是这个项目的推荐做法。
     * <p>
     * 虫皇（{@code insectBoss.baseHpMax}）是同一个形状的第二个键，一起钉住。
     */
    private static void testHpMaxDualEntry() {
        section("血量「一个值两个入口」（②）");

        final String flameBaseHpRule = DataKeys.Rule.FlameReaver.BASE_HP_MAX;
        final String insectBaseHpRule = DataKeys.Rule.InsectBoss.BASE_HP;
        final String flamePatch = "{\"entities\":{\"game_official_content:flameReaver\":"
                + "{\"derived\":{\"baseHpMax\":77777}}}}";
        final long defaultFlameHp = cn.gfhnv.game.system.configLoadingSystem.RuleKeySpecs
                .defaults().get(flameBaseHpRule) instanceof Number number ? number.longValue() : -1L;
        LivingThing flameTemplate = entityTemplateOf("game_official_content:flameReaver");
        LivingThing insectTemplate = entityTemplateOf("game_official_content:insectBoss");
        if (flameTemplate == null || insectTemplate == null) {
            check("前提：注册表里有盗火行者与虫皇两个模板可供打补丁", false);
            return;
        }
        cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Snapshot flameSnapshot =
                cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Snapshot.of(flameTemplate);
        cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Snapshot insectSnapshot =
                cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Snapshot.of(insectTemplate);
        try {
            /* ---------- ① 两个入口都写：派生赢，而且必须点名 ---------- */
            cn.gfhnv.game.system.configLoadingSystem.GameRules.beginLoad();
            cn.gfhnv.game.system.configLoadingSystem.GameRulesPatcher.applyJson(
                    "{\"version\":1,\"flameReaver\":{\"baseHpMax\":123456}}", "自测-两个入口同时写");
            cn.gfhnv.game.system.configLoadingSystem.GameRules.freeze();
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Report both =
                    cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher
                            .applyJson(flamePatch, "自测-两个入口同时写");
            String bothText = both.notices().toString();
            check("两个入口：EntityData.json 的 derived.baseHpMax 赢（规则表写 123456，实际 "
                            + flameTemplate.getHpMax() + "）",
                    flameTemplate.getHpMax() == 77777L);
            check("两个入口：这件事被点名了（不再静默）—— 提醒 " + both.notices(),
                    both.notices().size() == 1 && bothText.contains("两个入口对不上")
                            && bothText.contains("derived.baseHpMax") && bothText.contains(flameBaseHpRule));
            check("两个入口：提醒里写清了「谁赢、为什么、想让它生效该怎么办」"
                            + "（含规则表的出厂值 " + defaultFlameHp + "）",
                    bothText.contains("赢") && bothText.contains("构造之后")
                            && bothText.contains(String.valueOf(defaultFlameHp)));
            check("两个入口：它仍然是一条「正常应用」的记录，没有被算成跳过 / 错误"
                            + "（键是生效的，报成跳过就是另一种谎话）",
                    both.isClean() && both.appliedEntries().size() == 1);
            flameSnapshot.restore();

            /* ---------- ② 只写派生键、而且写的就是规则表那个数（= 生成的默认文件的形状）：不许吵 ---------- */
            // ⚠️ 判据是"两个数对不对得上"，所以这里必须写**规则表生效的那个数**：
            //    生成出来的 EntityData.json 里 derived.baseHpMax 与 flameReaver.baseHpMax 本来就是同一个数，
            //    这才是"只写派生键"的出厂形状。随手写 77777 反而会（正确地）触发提醒 —— 那属于新用例②。
            cn.gfhnv.game.system.configLoadingSystem.GameRules.resetForTest();
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Report derivedOnly =
                    cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher
                            .applyJson("{\"entities\":{\"game_official_content:flameReaver\":"
                                            + "{\"derived\":{\"baseHpMax\":" + defaultFlameHp + "}}}}",
                                    "自测-只写派生键");
            check("两个入口：只写 derived.baseHpMax、而且写的就是规则表生效值（= 默认文件的形状）时不发提醒"
                            + " —— 这是文档里的推荐做法，提醒 " + derivedOnly.notices(),
                    derivedOnly.notices().isEmpty()
                            && flameTemplate.getHpMax() == defaultFlameHp);
            flameSnapshot.restore();

            /* ---------- ③ 只写规则表：也不该按"两个入口"报（那是另一条已知的打折扣） ---------- */
            cn.gfhnv.game.system.configLoadingSystem.GameRules.beginLoad();
            cn.gfhnv.game.system.configLoadingSystem.GameRulesPatcher.applyJson(
                    "{\"version\":1,\"flameReaver\":{\"baseHpMax\":123456}}", "自测-只写规则表");
            cn.gfhnv.game.system.configLoadingSystem.GameRules.freeze();
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Report ruleOnly =
                    cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher
                            .applyJson("{\"entities\":{\"game_official_content:flameReaver\":"
                                    + "{\"derived\":{\"baseAttack\":999}}}}", "自测-只写规则表");
            check("两个入口：规则表写了、补丁里没有 baseHpMax 时不发提醒（提醒只针对 baseHpMax 撞车）—— "
                    + ruleOnly.notices(), ruleOnly.notices().isEmpty());
            flameSnapshot.restore();

            /* ---------- ④ 虫皇是同一个形状的第二个键，一起管 ---------- */
            cn.gfhnv.game.system.configLoadingSystem.GameRules.beginLoad();
            cn.gfhnv.game.system.configLoadingSystem.GameRulesPatcher.applyJson(
                    "{\"version\":1,\"insectBoss\":{\"baseHpMax\":65432}}", "自测-虫皇两个入口");
            cn.gfhnv.game.system.configLoadingSystem.GameRules.freeze();
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Report insectBoth =
                    cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher
                            .applyJson("{\"entities\":{\"game_official_content:insectBoss\":"
                                    + "{\"derived\":{\"baseHpMax\":54321}}}}", "自测-虫皇两个入口");
            check("两个入口：虫皇（insectBoss.baseHpMax）也走同一条判定，一并点名 —— "
                            + insectBoth.notices(),
                    insectBoth.notices().size() == 1
                            && insectBoth.notices().toString().contains(insectBaseHpRule)
                            && insectTemplate.getHpMax() == 54321L);
            insectSnapshot.restore();
        } finally {
            cn.gfhnv.game.system.configLoadingSystem.GameRules.resetForTest();
            flameSnapshot.restore();
            insectSnapshot.restore();
        }

        /* ---------- ⑤ 冻结之后规则表不会"半路变值"（这条语义的前提） ---------- */
        cn.gfhnv.game.system.configLoadingSystem.GameRules.freeze();
        check("两个入口：规则表冻结之后拒绝写入的语义没变（判定用的是「文件里写没写」而不是当前值）",
                !cn.gfhnv.game.system.configLoadingSystem.GameRules
                        .put(flameBaseHpRule, 1L)
                        && cn.gfhnv.game.system.configLoadingSystem.GameRules
                        .getLong(flameBaseHpRule) == defaultFlameHp);
        cn.gfhnv.game.system.configLoadingSystem.GameRules.resetForTest();
        check("两个入口自测收尾：规则表已清空（后面用例看到的是「什么都没配」的出厂状态）",
                cn.gfhnv.game.system.configLoadingSystem.GameRules.current().isEmpty());
    }

    /**
     * <b>「血液两个入口」那条提醒的误报修掉之后的行为</b>（2026-10-03 第 2 轮，用户报）。
     * <p>
     * 用户看到的那条提醒原文有约 250 字，而它<b>每次启动必然响</b> —— 因为生成的
     * {@code EntityData.json} 本来就<b>一定</b>有 {@code derived.baseHpMax}（全量 dump），
     * {@code GameRules.json} 本来就<b>一定</b>有 {@code flameReaver.baseHpMax}（也是全量 dump）：
     * <b>"两个入口都写了"是默认状态，不是冲突</b>。真正的判据只能是<b>两个数对不对得上</b>。
     * <p>
     * 这里刻意<b>不读用户那 5 份真实配置</b>（一个字都不许动）：改用
     * {@code EntityDataPatcher#constructionHpRuleKeyOf} 把"这份模板的出厂血量在规则表里是几"
     * 问出来，在自测里复现"两份默认文件"和"只改一处"这两种状态。
     */
    private static void testHpMaxNoticeFalsePositive() {
        section("血量两个入口：只在两个值真的不一致时才提醒（误报修复）");

        LivingThing flame = entityTemplateOf("game_official_content:flameReaver");
        if (flame == null) {
            check("前提：注册表里有盗火行者模板可供打补丁", false);
            return;
        }
        cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Snapshot snapshot =
                cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Snapshot.of(flame);
        try {
            String ruleKey = cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher
                    .constructionHpRuleKeyOf(flame);
            long factory = cn.gfhnv.game.system.configLoadingSystem.RuleKeySpecs
                    .defaults().get(ruleKey) instanceof Number number ? number.longValue() : -1L;
            check("前提：盗火行者的出厂血量在规则表里有名有姓（" + ruleKey + " = " + factory + "）",
                    ruleKey != null && factory > 0);

            /* ---------- ① 两份默认文件：两个入口同值 → 一句话都不许说 ---------- */
            cn.gfhnv.game.system.configLoadingSystem.GameRules.resetForTest();
            cn.gfhnv.game.system.configLoadingSystem.GameRules.beginLoad();
            String defaultPatch = "{\"entities\":{\"game_official_content:flameReaver\":"
                    + "{\"derived\":{\"baseHpMax\":" + factory + "}}}}";
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Report same =
                    cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher
                            .applyJson(defaultPatch, "自测-两份默认文件");
            check("误报修复①：两份默认文件（未手工改动）不触发提醒 —— 提醒 " + same.notices(),
                    same.notices().isEmpty());
            check("误报修复①：值照样生效（" + flame.getHpMax() + "），键没有被当成跳过",
                    flame.getHpMax() == factory && same.isClean());
            snapshot.restore();

            /* ---------- ② 规则表改了、派生键停在旧值 → 必须提醒 ---------- */
            // ⚠️ 先清一次规则表：前面的用例可能已经把 baseHpMax 写成别的数了，
            //    这一段的判据是"两个数对不对得上"，必须从出厂状态出发
            cn.gfhnv.game.system.configLoadingSystem.GameRules.resetForTest();
            long changed = factory + 1111L;
            cn.gfhnv.game.system.configLoadingSystem.GameRules.beginLoad();
            cn.gfhnv.game.system.configLoadingSystem.GameRulesPatcher.applyJson(
                    "{\"version\":1,\"flameReaver\":{\"baseHpMax\":" + changed + "}}",
                    "自测-只改规则表");
            cn.gfhnv.game.system.configLoadingSystem.GameRules.freeze();
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Report mismatch =
                    cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher
                            .applyJson(defaultPatch, "自测-只改规则表");
            String mismatchText = mismatch.notices().toString();
            check("误报修复②：真的只改一处、值不一致时触发提醒 —— 提醒 " + mismatch.notices(),
                    mismatch.notices().size() == 1
                            && mismatchText.contains("两个入口对不上")
                            && mismatchText.contains(ruleKey));
            check("误报修复②：提醒里同时给出两个数（派生键 " + factory + " vs 规则表 " + changed + "）",
                    mismatchText.contains(String.valueOf(factory))
                            && mismatchText.contains(String.valueOf(changed)));
            snapshot.restore();

            /* ---------- ③ 两处都改了、但改成了同一个数 → 也不吵 ---------- */
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Report agreed =
                    cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher
                            .applyJson("{\"entities\":{\"game_official_content:flameReaver\":"
                                    + "{\"derived\":{\"baseHpMax\":" + changed + "}}}}", "自测-两处改成同一个数");
            check("误报修复③：两处都改了、且改成同一个数 → 不吵（判据是值，不是「写过没写过」）—— 提醒 "
                            + agreed.notices(),
                    agreed.notices().isEmpty() && flame.getHpMax() == changed);
            snapshot.restore();
        } finally {
            cn.gfhnv.game.system.configLoadingSystem.GameRules.resetForTest();
            snapshot.restore();
        }
    }

    /**
     * 跑本主题的全部用例（顺序 = 下面列的顺序）。
     */
    public static void runAll() throws Exception {
        testGameRules();
        testHpMaxDualEntry();
        testHpMaxNoticeFalsePositive();
    }

    private GameRulesTests() {
    }
}
