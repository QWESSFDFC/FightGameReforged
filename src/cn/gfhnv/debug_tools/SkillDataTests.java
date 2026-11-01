package cn.gfhnv.debug_tools;

import cn.gfhnv.game.data.*;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entityController.UniversalController;
import cn.gfhnv.game.event.DamageEvent;
import cn.gfhnv.game.mod.Mod;
import cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver;
import cn.gfhnv.game.officialStuff.customEntity.players.Phainon;
import cn.gfhnv.game.officialStuff.customEntity.players.PlayerOne;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.command.*;
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
 * 自测：**技能/配置**。
 * <p>
 * 主题：技能数值外部加载、具名系数、注册表、id 覆盖、AI 权重。
 * <p>
 * 由 {@code TestCommandSystem#main} 按顺序调用 {@code SkillDataTests#runAll()}。
 */
public final class SkillDataTests {

    /**
     * 技能数值补丁（{@code config/gameConfig/SkillData.json}）。
     * <p>
     * 三条主线：
     * <ol>
     *     <li><b>键名契约</b>：每个注册技能的 {@code 实体id#技能名} 都能在生成器输出里找到
     *     （技能改名 = 配置静默失效，这里要立刻红）；</li>
     *     <li><b>默认值等价</b>：把"当前全量默认值"原样打回去，数值必须逐字段不变；</li>
     *     <li><b>改了真的生效</b>：倍率改 0 → 运行期倍率变 0；只写一个键 → 别的字段一个字不动；
     *     {@code copy()} 出来的副本带上补丁值。</li>
     * </ol>
     * 用例跑完把每个技能原样放回去（模板上的技能就是"选人时被复制出去的那一份"，
     * 改坏了会影响后面所有用例）。
     */
    private static void testSkillDataPatch() throws Exception {
        section("技能数值外部加载（阶段 2）");

        /* ---------- 快照：跑完必须还原 ---------- */
        Map<String, String> skillsBefore = new java.util.LinkedHashMap<>();
        Map<String, cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.SkillSnapshot> snapshots =
                new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Skill> entry
                : cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.all().entrySet()) {
            skillsBefore.put(entry.getKey(),
                    cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.stateOf(entry.getValue()));
            snapshots.put(entry.getKey(),
                    cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.SkillSnapshot
                            .of(entry.getValue()));
        }
        check("前提：注册表里的模板上有技能可供打补丁 —— " + skillsBefore.size() + " 个",
                skillsBefore.size() >= 15);

        String skillJson = cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter.skillDataJson();
        Map<String, List<String>> skillKeys =
                cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter.lastSkillKeys();
        org.json.JSONObject skillRoot = new org.json.JSONObject(skillJson);
        org.json.JSONObject skillsSection = skillRoot.optJSONObject("skills");

        /* ---------- ① 生成器与键名契约 ---------- */
        check("技能生成器：SkillData 能被 org.json 解析，且带 skills 段", skillsSection != null);
        check("技能生成器：version 是数字 1（不是字符串 \"1\"）",
                cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher
                        .asLong(skillRoot.opt("version")) == 1L);

        List<String> missingKeys = new ArrayList<>();
        List<String> missingInOutput = new ArrayList<>();
        for (Map.Entry<String, Skill> entry
                : cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.all().entrySet()) {
            if (!skillKeys.containsKey(entry.getKey())) {
                missingInOutput.add(entry.getKey());
                continue;
            }
            for (String key : Arrays.asList("aims", "coolDown", "hpMagnification",
                    "atkMagnification", "defMagnification", "forEnemies",
                    "consumedMana", "tags")) {
                if (!skillKeys.get(entry.getKey()).contains(key)) {
                    missingKeys.add(entry.getKey() + "/" + key);
                }
            }
        }
        check("技能键契约：每个技能的「实体id#技能名」都出现在生成器输出里（技能改名会在这里红）—— 缺 "
                + missingInOutput, missingInOutput.isEmpty());
        check("技能键契约：每个技能都写全 8 项（3 个倍率 / 目标数 / 冷却 / 阵营 / 消耗 / 权重）—— 缺 "
                + missingKeys, missingKeys.isEmpty());

        List<String> unparsable = new ArrayList<>();
        for (String key : skillsSection.keySet()) {
            org.json.JSONObject patch = skillsSection.optJSONObject(key);
            if (patch == null || !patch.has("atkMagnification") || !patch.has("consumedMana")
                    || !patch.has("tags")) {
                unparsable.add(key);
            }
        }
        // D7 守卫：isKnown() 与 patch() 必须是同一张表（加一个技能键只漏改一边就在这里红）
        List<String> skillKeyProblems = new ArrayList<>();
        for (String skillKeyName : new String[]{"aims", "coolDown", "hpMagnification",
                "atkMagnification", "defMagnification", "forEnemies", "consumedMana", "tags"}) {
            if (!cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.isKnown(skillKeyName)) {
                skillKeyProblems.add(skillKeyName + "（补丁器说它未知）");
            }
            if (cn.gfhnv.game.system.configLoadingSystem.SkillKeySpecs.of(skillKeyName) == null) {
                skillKeyProblems.add(skillKeyName + "（不在 SkillKeySpecs 表里）");
            }
        }
        check("技能键契约（D7）：isKnown() 认识的键 == SkillKeySpecs 表里的键"
                        + "（加一个技能键只漏改一边会在这里红）—— 对不上的：" + skillKeyProblems,
                skillKeyProblems.isEmpty()
                        && !cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher
                        .isKnown("nope这种不存在的键"));
        check("技能键契约：输出里每一项都是合法的补丁对象 —— 坏 " + unparsable, unparsable.isEmpty());

        /* 文档点名的那两处"倍率被系统性压平"的现状：默认值必须原样保留，只是变得可配 */
        org.json.JSONObject freezePatch =
                skillsSection.optJSONObject("game_official_content:iceInsect#冰冻");
        check("技能默认值：冰冻的 7.5 倍率原样写在默认文件里（§6.3 记录的现状，不许被\"顺手修正\"）",
                freezePatch != null
                        && freezePatch.optDouble("atkMagnification", -1) == 7.5
                        && freezePatch.optInt("aims", -99) == 2);
        Skill freezeSkill = cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher
                .find("game_official_content:iceInsect#冰冻");
        check("技能默认值：运行期的冰冻倍率就是 7.5（生成器没有美化它）",
                freezeSkill != null && freezeSkill.getAtkMagnification() == 7.5);
        Skill commonSkill = cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher
                .find("game_official_content:playerOne#普通攻击");
        check("技能默认值：普攻自带 DamageEnhanceEffect(1,1) 属于行为逻辑，没有被外置（代码里仍是字面量）"
                        + "—— 运行期倍率 " + (commonSkill == null ? "?" : commonSkill.getAtkMagnification()),
                commonSkill != null && commonSkill.getAtkMagnification() == 1.0
                        && commonSkill.getTags().containsKey(
                        cn.gfhnv.game.system.thinkingSystem.TagType.ATTACK));

        /* ---------- ② 把"当前全量默认值"打回去：数值必须逐字段不变 ---------- */
        cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.Report all =
                cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher
                        .applyJson(skillJson, "自测-技能全量默认值");
        check("技能补丁：全量默认值一个键都不落地报错 —— 错误 " + all.errors(), all.errors().isEmpty());
        check("技能补丁：全量默认值没有跳过项 —— 跳过 " + all.skippedEntries(),
                all.skippedEntries().isEmpty());
        check("技能补丁：每个技能实例都被补到 —— 影响 " + all.patchedSkills() + " 个技能",
                all.patchedSkills() == skillsBefore.size());
        List<String> changedSkills = new ArrayList<>();
        for (Map.Entry<String, Skill> entry
                : cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.all().entrySet()) {
            String now = cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher
                    .stateOf(entry.getValue());
            if (!now.equals(skillsBefore.get(entry.getKey()))) {
                changedSkills.add(entry.getKey() + "\n      旧 " + skillsBefore.get(entry.getKey())
                        + "\n      新 " + now);
            }
        }
        check("技能补丁：把当前默认值打回去，数值逐字段不变（含消耗与权重整块）—— 变了 "
                + changedSkills, changedSkills.isEmpty());

        /* ---------- ③ 改倍率真的生效；没写的字段一个字不动 ---------- */
        Skill gunShoot = cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher
                .find("game_official_content:iceInsect#冰冻");
        double atkBefore = gunShoot.getAtkMagnification();
        int aimsBefore = gunShoot.getAims();
        int coolDownBefore = gunShoot.getCoolDown();
        cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.Report one =
                cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.applyJson(
                        "{\"skills\":{\"game_official_content:iceInsect#冰冻\":"
                                + "{\"atkMagnification\":0.0}}}", "自测-技能只改倍率");
        check("技能补丁：只写一个键时没有报错也没有跳过 —— " + one.errors() + one.skippedEntries(),
                one.isClean() && one.appliedEntries().size() == 1);
        check("技能补丁：倍率真的变成 0（旧 " + atkBefore + "）", gunShoot.getAtkMagnification() == 0.0);
        check("技能补丁：没写的字段一个字都没动（目标数 " + aimsBefore + "、冷却 " + coolDownBefore + "）",
                gunShoot.getAims() == aimsBefore && gunShoot.getCoolDown() == coolDownBefore);
        // 改回去（用同一份配置把值写回来，证明"改了能改回来"）
        cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.applyJson(
                "{\"skills\":{\"game_official_content:iceInsect#冰冻\":"
                        + "{\"atkMagnification\":7.5}}}", "自测-技能改回倍率");
        check("技能补丁：改回去之后倍率恢复（" + gunShoot.getAtkMagnification() + "）",
                gunShoot.getAtkMagnification() == 7.5);

        /* 消耗块与权重块 */
        cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.applyJson(
                "{\"skills\":{\"game_official_content:insectBoss#分裂\":{\"consumedMana\":null}}}",
                "自测-取消消耗");
        Skill summonEnemy = cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher
                .find("game_official_content:insectBoss#分裂");
        check("技能补丁：consumedMana 写成 null = 取消消耗（不是\"保持原样\"）",
                summonEnemy != null && summonEnemy.getConsumedMana() == null);
        cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.applyJson(
                "{\"skills\":{\"game_official_content:insectBoss#分裂\":"
                        + "{\"consumedMana\":{\"amount\":7,\"element\":\"FIRE\"}}}}", "自测-改消耗");
        check("技能补丁：消耗块按 {amount,element} 生效（7 FIRE）",
                summonEnemy.getConsumedMana() != null
                        && summonEnemy.getConsumedMana().getAmount() == 7.0
                        && summonEnemy.getConsumedMana().getElementSort() == ElementSort.FIRE);
        cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.applyJson(
                "{\"skills\":{\"game_official_content:insectBoss#分裂\":"
                        + "{\"tags\":{\"HEAL\":2,\"DEFENCE\":1}}}}", "自测-改权重");
        check("技能补丁：权重块整块覆盖（原来只有 ATTACK，现在只剩 HEAL / DEFENCE）",
                summonEnemy.getTags().size() == 2
                        && summonEnemy.getTags().containsKey(
                        cn.gfhnv.game.system.thinkingSystem.TagType.HEAL)
                        && !summonEnemy.getTags().containsKey(
                        cn.gfhnv.game.system.thinkingSystem.TagType.ATTACK));
        snapshots.get("game_official_content:insectBoss#分裂").restore();

        /* ---------- ④ 副本必须带上补丁值 ---------- */
        Skill templateSkill = cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher
                .find("game_official_content:playerOne#枪射击");
        check("前提：玩家一的枪射击在模板上（模板技能 = 选人时被复制出去的那一份）",
                templateSkill != null && templateSkill.getAtkMagnification() == 7.5);
        cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.applyJson(
                "{\"skills\":{\"game_official_content:playerOne#枪射击\":"
                        + "{\"atkMagnification\":3.25}}}", "自测-技能副本");
        LivingThing playerTemplate = entityTemplateOf("game_official_content:playerOne");
        LivingThing playerCopy = playerTemplate.copy();
        Skill copied = null;
        Skill copiedTemplate = null;
        for (Skill skill : playerTemplate.getController().getSkills()) {
            if ("枪射击".equals(skill.getName())) {
                copiedTemplate = skill;
            }
        }
        for (Skill skill : playerCopy.getController().getSkills()) {
            if ("枪射击".equals(skill.getName())) {
                copied = skill;
            }
        }
        System.out.println("  [诊断] 模板技能=" + cn.gfhnv.game.system.configLoadingSystem
                .SkillDataPatcher.stateOf(copiedTemplate) + " / 副本技能="
                + cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.stateOf(copied)
                + " / 副本控制器=" + playerCopy.getController().getClass().getSimpleName());
        check("技能补丁：copy() 出来的副本带上了补丁值（3.25）—— 副本 "
                        + (copied == null ? "没找到技能" : copied.getAtkMagnification()),
                copied != null && copied.getAtkMagnification() == 3.25);
        check("技能补丁：副本里的技能与模板上的技能是两个实例（没有把模板对象交出去）",
                copied != null && copied != templateSkill);
        snapshots.get("game_official_content:playerOne#枪射击").restore();

        /* 覆盖面护栏：每一个技能的 copy() 都必须"带着当前数值复制"。
         * 只测一个类抓不住这件事 —— 曾经有 8 个类的 copy() 直接 return new Xxx()，
         * 于是打在模板上的配置永远传不到选人时复制出去的副本（"改了没反应"）。 */
        List<String> copyLosesValues = new ArrayList<>();
        for (Map.Entry<String, Skill> entry
                : cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.all().entrySet()) {
            Skill original = entry.getValue();
            double before = original.getAtkMagnification();
            double probe = before + 1.5;
            original.setAtkMagnification(probe);
            Skill duplicated = original.copy();
            if (duplicated.getAtkMagnification() != probe) {
                copyLosesValues.add(entry.getKey() + "（改后 " + probe + "，副本 "
                        + duplicated.getAtkMagnification() + "）");
            }
            // 用原值直接还原，不要 "probe - 1.5"：浮点加减不互逆，
            // 0.3 + 1.5 - 1.5 = 0.30000000000000004，会把后面的"逐字段不变"断言弄红
            original.setAtkMagnification(before);
        }
        check("技能契约：每个技能的 copy() 都带着当前数值复制（return new Xxx() 会让配置传不到副本）—— 丢值的 "
                + copyLosesValues, copyLosesValues.isEmpty());

        /* ---------- ⑤ 每个技能只补一次 ---------- */
        Skill repeatProbe = cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher
                .find("game_official_content:playerOne#枪射击");
        org.json.JSONObject repeatPatch = new org.json.JSONObject("{\"coolDown\":9}");
        cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.Report sameRun =
                new cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.Report("自测-技能只补一次");
        check("技能补丁：同一轮里第一次命中技能时返回 true（可以补）",
                sameRun.markBound("game_official_content:playerOne#枪射击", repeatProbe));
        cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher
                .patch(repeatProbe, repeatPatch, sameRun, "game_official_content:playerOne#枪射击");
        check("技能补丁：一轮里同一个技能只补一次（重复应用会把\"改了能改回来\"变成做不到）",
                repeatProbe.getCoolDown() == 9 && !sameRun.markBound("x#y", repeatProbe)
                        && sameRun.appliedEntries().size() == 1);
        snapshots.get("game_official_content:playerOne#枪射击").restore();

        /* ---------- ⑥ 容错：未知键 / 类型不对 / 找不到技能 / 找不到实体 ---------- */
        cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.Report broken =
                cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.applyJson(
                        "{\"skills\":{"
                                + "\"game_official_content:playerOne#枪射击\":"
                                + "{\"coolDown\":\"久\",\"nope\":1},"
                                + "\"game_official_content:playerOne#没这个技能\":{\"aims\":1},"
                                + "\"game_official_content:noSuchEntity#枪射击\":{\"aims\":1},"
                                + "\"game_official_content:playerOne#普通攻击\":{\"aims\":1}"
                                + "}}", "自测-技能容错");
        String brokenText = broken.errors() + " " + broken.skippedEntries();
        check("技能容错：一个坏键不再废掉整份配置（同一份里其余项照常生效）—— 跳过 "
                        + broken.skippedEntries() + "，错误 " + broken.errors(),
                broken.skippedEntries().size() == 2 && broken.errors().size() == 2
                        && broken.appliedEntries().size() == 1);
        check("技能容错：类型不对的项被点名道姓（键名 + 实际类型）",
                brokenText.contains("coolDown") && brokenText.contains("字符串"));
        check("技能容错：未知键被点名（不再静默忽略）", brokenText.contains("nope"));
        check("技能容错：找不到的技能被点名（技能改名 = 配置静默失效，这里要看得见）",
                brokenText.contains("没这个技能"));
        check("技能容错：找不到的实体也被点名", brokenText.contains("noSuchEntity"));
        check("技能容错：坏值没有被写进去（冷却还是 1）",
                cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher
                        .find("game_official_content:playerOne#枪射击").getCoolDown() == 1);
        snapshots.get("game_official_content:playerOne#普通攻击").restore();

        /* ---------- ⑦ 文档点名的技能族都在（覆盖面的护栏） ---------- */
        List<String> mustBePatchable = Arrays.asList(
                // 盗火行者那一套
                "game_official_content:flameReaver#亡死的黑云",
                "game_official_content:flameReaver#将尽的命数",
                "game_official_content:flameReaver#相混的道途",
                "game_official_content:flameReaver#幽冥的悼念",
                "game_official_content:flameReaver#沉默的悲叹",
                "game_official_content:flameReaver#莫因舍弃而哭泣",
                "game_official_content:flameReaver#却是必要的苦难",
                // 白厄那一套
                "game_official_content:phainon#普通攻击:逐火救世,行则将至",
                "game_official_content:phainon#战技:黎明创世,地辟天开",
                "game_official_content:phainon#大招:永劫燔世,其将背负",
                // 李晓焰那一套
                "game_official_content:actorLiXiaoYan#普通攻击",
                "game_official_content:actorLiXiaoYan#灼血泵动",
                "game_official_content:actorLiXiaoYan#过载·白炽化",
                // 虫皇与通用技能
                "game_official_content:insectBoss#分裂",
                "game_official_content:playerOne#普通攻击",
                "game_official_content:playerOne#枪射击",
                "game_official_content:iceInsect#冰冻",
                "game_official_content:playerOne#生命值恢复");
        List<String> notPatchable = new ArrayList<>();
        for (String key : mustBePatchable) {
            if (!skillsBefore.containsKey(key)) {
                notPatchable.add(key);
            }
        }
        check("技能覆盖面：文档点名的三族 + 虫皇 + 通用技能都能按「实体id#技能名」定位 —— 缺 "
                + notPatchable, notPatchable.isEmpty());

        /* ---------- ⑧ 战斗里真的按配置的倍率算伤害 ---------- */
        Skill damageProbe = cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher
                .find("game_official_content:iceInsect#冰冻");
        // 攻击方用一个"元素已初始化"的模板副本（裸 LivingThing(long) 没有元素：
        // setLevel 里的 initialMana 会把 5 条法力全按 otherBase 建，主元素拿不到 mainBase）
        LivingThing attacker = entityTemplateOf("game_official_content:commonInsect").copy();
        attacker.setAttack(1000);
        long fullDamage = new DamageEvent(attacker, playerTemplate, damageProbe).getDamage().getDamageAmount();
        cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.applyJson(
                "{\"skills\":{\"game_official_content:iceInsect#冰冻\":"
                        + "{\"atkMagnification\":0.0}}}", "自测-技能伤害为零");
        long zeroDamage = new DamageEvent(attacker, playerTemplate, damageProbe).getDamage().getDamageAmount();
        check("技能补丁：倍率改成 0 之后，伤害计算真的变成 0（旧 " + fullDamage + " → 新 " + zeroDamage + "）",
                fullDamage > 0 && zeroDamage == 0);
        snapshots.get("game_official_content:iceInsect#冰冻").restore();

        /* ---------- ⑨ 落盘：SkillData.json 也会被写出来，且只补缺、不覆盖已有值 ---------- */
        java.io.File tempDir = new java.io.File("./out/tmpConfigSkill");
        deleteDirectory(tempDir);
        List<String> written = cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter
                .writeAll(tempDir);
        java.io.File skillFile = new java.io.File(tempDir,
                cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter.SKILL_FILE_NAME);
        check("技能落盘：缺文件时写出 SkillData.json —— 实际写了 " + written.size() + " 个",
                written.size() == 4 && skillFile.isFile());
        String skillText = java.nio.file.Files.readString(skillFile.toPath(),
                java.nio.charset.StandardCharsets.UTF_8);
        check("技能落盘：写出来的是 UTF-8 的中文技能名（读得回来，不是乱码）",
                skillText.contains("枪射击") && !skillText.contains("\uFFFD"));
        check("技能落盘：写出来的文本能被加载器原样读回去（补丁语义 = 不改数值）",
                cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher
                        .applyJson(skillText, "自测-落盘回读").isClean());
        // 自愈（D5）：写成"只改了一个技能的倍率"的样子 —— 缺的要补上，改过的值不许动
        java.nio.file.Files.writeString(skillFile.toPath(),
                "{\"skills\":{\"game_official_content:playerOne#枪射击\":{\"atkMagnification\":4.25}}}",
                java.nio.charset.StandardCharsets.UTF_8);
        cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter.writeAll(tempDir);
        org.json.JSONObject healedSkills = new org.json.JSONObject(java.nio.file.Files
                .readString(skillFile.toPath(), java.nio.charset.StandardCharsets.UTF_8))
                .getJSONObject("skills");
        check("技能落盘自愈（D5）：只写了 1 个技能的配置会被补齐（缺啥补啥），已有值不覆盖"
                        + "—— 补完有 " + healedSkills.keySet().size() + " 个技能，枪射击倍率 "
                        + healedSkills.getJSONObject("game_official_content:playerOne#枪射击")
                        .opt("atkMagnification"),
                healedSkills.keySet().size() >= 25
                        && healedSkills.getJSONObject("game_official_content:playerOne#枪射击")
                        .optDouble("atkMagnification") == 4.25);
        deleteDirectory(tempDir);
        check("技能落盘：临时目录用完就删干净（不往仓库里留垃圾）", !tempDir.exists());

        /* ---------- ⑩ 收尾：所有技能还原 ---------- */
        List<String> notRestored = new ArrayList<>();
        for (Map.Entry<String, Skill> entry
                : cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.all().entrySet()) {
            String now = cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher
                    .stateOf(entry.getValue());
            if (!now.equals(skillsBefore.get(entry.getKey()))) {
                notRestored.add(entry.getKey() + "（" + now + "）");
            }
        }
        check("技能自测收尾：所有技能都还原成了补丁之前的样子（后面用例看到的是干净注册表）—— 没还原 "
                + notRestored, notRestored.isEmpty());
    }

    /**
     * <b>AI 评分加成</b>（{@code IAiScoreBonus}，2026-10-24 用户拍板）：让"0 伤害但很关键"的技能
     * （白厄的变身大招）也能被 {@code ThinkingControllerAI} 选中。
     * <p>
     * <b>原来为什么选不中</b>：{@code evaluateAction} 的分数是"权重 + 按伤害/血量算的 bonus − 蓝耗惩罚"。
     * 大招 {@code aims=0} → 目标是自己、满血 → 一个加分项都拿不到，预测伤害 0 → 也拿不到斩杀加分，
     * 最后只剩"权重 − 蓝耗惩罚"。**它的 {@code ATTACK} 权重是 10，但那玩意儿只决定"选哪一类 Tag"**，
     * 同一类里比的是分数 —— 所以它永远输给普攻。
     * <p>
     * 这里验四件事：① 火种够且没变身时，大招的分数**真的压过普攻**（差值 ≈ {@code aiPriority}）；
     * ② 火种不够 / 已经变身时**加分归零**（否则会带着负火种变身，或重排时间轴）；
     * ③ {@code aiPriority} 可配（它就是 {@code SkillData.json} 里那个键）；
     * ④ 三参 {@code canUse} 也认火种门槛（AI 与 {@code use(...)} 走的都是它）。
     */
    private static void testAiScoreBonus() {
        section("AI 评分加成：变身技能能被思维 AI 选中（原来永远输给普攻）");

        Phainon phainon = new Phainon(125);
        phainon.setCoreflame(15);
        cn.gfhnv.game.system.thinkingSystem.ThinkingControllerAI ai =
                new cn.gfhnv.game.system.thinkingSystem.ThinkingControllerAI(new ArrayList<>(), phainon);
        cn.gfhnv.game.officialStuff.customSkill.universalSkill.CommonAttack normalAttack =
                new cn.gfhnv.game.officialStuff.customSkill.universalSkill.CommonAttack(0.0, 1.0, 0.0, 1);
        cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills.UltimateAttack ultimate =
                new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills.UltimateAttack();
        LivingThing dummy = entityTemplateOf("game_official_content:commonInsect");

        double ultimateScore = ai.scoreOf(null, ultimate, phainon);   // aims=0 → AI 传的目标就是自己
        double attackScore = ai.scoreOf(null, normalAttack, dummy);
        check("AI 评分：火种够且没变身时，大招的分数压过普攻（差值 ≈ aiPriority 100）—— 实际 "
                        + ultimateScore + " vs " + attackScore,
                ultimateScore - attackScore > 50);

        // ② 两个"不该加"的条件
        Phainon poor = new Phainon(125);
        poor.setCoreflame(3);
        check("AI 评分：火种不够时加分归零（不然会带着负火种变身）—— 实际加分 "
                        + ultimate.aiScoreBonus(null, poor, poor),
                ultimate.aiScoreBonus(null, poor, poor) == 0);
        Phainon awakened = new Phainon(125);
        awakened.setCoreflame(15);
        awakened.setAwaken(true);
        check("AI 评分：已经觉醒时加分归零（不然会重排整条时间轴）—— 实际加分 "
                        + ultimate.aiScoreBonus(null, awakened, awakened),
                ultimate.aiScoreBonus(null, awakened, awakened) == 0);
        check("AI 评分：对非白厄一律 0（这个技能只属于他）—— 实际加分 "
                        + ultimate.aiScoreBonus(null, dummy, dummy),
                ultimate.aiScoreBonus(null, dummy, dummy) == 0);

        // ③ 可配：改 aiPriority 只影响差值
        cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills.UltimateAttack doubled =
                new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills.UltimateAttack();
        doubled.setCoefficientValue("aiPriority", 250d);
        check("AI 评分：aiPriority 可配（100 → 250 时分数正好多 150，其余项不受影响）—— 实际 "
                        + ai.scoreOf(null, doubled, phainon) + " vs " + ultimateScore,
                Math.abs(ai.scoreOf(null, doubled, phainon) - ultimateScore - 150d) < 1e-9);

        // ④ 三参 canUse 认火种门槛（AI 与 use(...) 都走它）
        check("AI 可用性：三参 canUse 也认火种门槛（3 层火种 → AI 眼里不可用）",
                !ultimate.canUse(null, poor, null) && ultimate.canUse(null, phainon, null));
    }

    /**
     * <b>白厄变身那一套数</b>（2026-10-24 用户拍板"可以外配"）：
     * {@code LastAttack} 的每回合折扣 {@code 0.125} / 上限 {@code 7}，
     * {@code UltimateAttack} 的 {@code 12} 火种 / {@code 8} 个额外回合。
     * <p>
     * 三条要证明的（与其它具名系数同一个标准）：
     * <ol>
     *     <li><b>出厂值逐位不变</b>：{@code 0.125 / 7 / 12 / 8} —— 外置之前它们写在方法体里，
     *     外置之后一个字都没动，所以"最后一击"与"变身时间轴"的行为与之前完全一致；</li>
     *     <li><b>改了真的改变行为</b>：折扣/上限走 {@code magnificationFor(...)} 的数值对照，
     *     火种走 {@code canUse} 的门槛对照，回合数走 {@code scheduledExtraTurns()}
     *     （时间轴条目数就是它，不再另写一个 8）；</li>
     *     <li><b>copy() 带着走</b>：配置打在模板上，副本靠 {@code copy()} 白送 —— 漏复制就是"改了没反应"。</li>
     * </ol>
     */
    private static void testAwakenNumbersExternalized() {
        section("白厄变身的四个数：0.125 / 上限 7 / 12 火种 / 8 个额外回合（已外配）");

        cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.LastAttack last =
                new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.LastAttack();
        cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills.UltimateAttack ultimate =
                new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills.UltimateAttack();

        check("出厂值：最后一击每剩余回合折扣 0.125、上限 7 —— 实际 perTurn="
                        + last.coefficientValues().get("lastAttackPerTurn")
                        + "、maxTurns=" + last.coefficientValues().get("lastAttackMaxTurns"),
                last.coefficientValues().get("lastAttackPerTurn") == 0.125d
                        && last.coefficientValues().get("lastAttackMaxTurns") == 7.0d);
        check("出厂值：大招烧 12 火种、排 8 个额外回合 —— 实际 coreflameCost="
                        + ultimate.coefficientValues().get("coreflameCost")
                        + "、awakenExtraTurns=" + ultimate.coefficientValues().get("awakenExtraTurns"),
                ultimate.coefficientValues().get("coreflameCost") == 12.0d
                        && ultimate.coefficientValues().get("awakenExtraTurns") == 8.0d);

        // ① 最后一击的倍率表（与 60-COMBAT.md §5.7 那张表逐位对齐）
        check("倍率表（出厂）：剩 0 个额外回合 = 满倍率 13，剩 7 个 = 1.625，剩 8 个被上限压成 1.625 —— 实际 "
                        + last.magnificationFor(0) + " / " + last.magnificationFor(7) + " / " + last.magnificationFor(8),
                Math.abs(last.magnificationFor(0) - 13.0d) < 1e-9
                        && Math.abs(last.magnificationFor(7) - 1.625d) < 1e-9
                        && Math.abs(last.magnificationFor(8) - 1.625d) < 1e-9);

        // ② 改两个系数 → 倍率真的变（折扣 0.25、上限 2：剩 5 个被压成 2 个 → 13 × (1 − 0.5) = 6.5）
        last.setCoefficientValue("lastAttackPerTurn", 0.25d);
        last.setCoefficientValue("lastAttackMaxTurns", 2d);
        check("外配生效：折扣改 0.25 / 上限改 2 之后，剩 5 个也按 2 个算（13 × (1 − 2×0.25) = 6.5）—— 实际 "
                        + last.magnificationFor(5),
                Math.abs(last.magnificationFor(5) - 6.5d) < 1e-9);

        // ③ 大招的火种门槛与额外回合数
        Phainon cheap = new Phainon(125);
        cheap.setCoreflame(3);
        check("外配生效：火种门槛按配置走（3 层火种 + 默认 12 → 放不出来；改成 3 → 放得出来）—— ",
                !ultimate.canUse(null, cheap)
                        && ultimateWithCost(3).canUse(null, cheap));
        check("外配生效：变身的额外回合数按配置走（默认 8 → 改 2 → 改 0 夹回 1）—— 实际 "
                        + ultimate.scheduledExtraTurns() + " / " + ultimateWithTurns(2).scheduledExtraTurns()
                        + " / " + ultimateWithTurns(0).scheduledExtraTurns(),
                ultimate.scheduledExtraTurns() == 8
                        && ultimateWithTurns(2).scheduledExtraTurns() == 2
                        && ultimateWithTurns(0).scheduledExtraTurns() == 1);

        // ③-b 技能阵营：大招是 aims=0 的"给自己"变身技，**不该是 forEnemies**
        //（`Skill` 的字段默认值是 true，2026-10-24 用户实测发现这条一直是 true）
        check("大招是「给自己」的变身技（aims=0 且 forEnemies=false）—— 实际 aims=" + ultimate.getAims()
                        + "、forEnemies=" + ultimate.isForEnemies(),
                ultimate.getAims() == 0 && !ultimate.isForEnemies());

        // ③-c 具名系数的**键名**必须就是 /data 里的字段名 —— 否则配置写一个名、dump 显示另一个名，
        // 用户按 dump 里的名字去配会"未知键"。（本轮就踩过：字段曾叫 awakenTurnCount，配置键是 awakenExtraTurns）
        List<String> missingDumpKeys = new ArrayList<>();
        for (cn.gfhnv.game.skill.Skill skill : List.of(last, ultimate)) {
            java.util.Set<String> dumpKeys = new java.util.HashSet<>(DataBridge.dataNames(skill.getClass()));
            for (String key : ((cn.gfhnv.game.skill.SkillCoefficientTunable) skill).coefficientValues().keySet()) {
                if (!dumpKeys.contains(key)) {
                    missingDumpKeys.add(skill.getClass().getSimpleName() + "：" + key);
                }
            }
        }
        check("具名系数的键名 == /data 里的字段名（配置键与 dump 键同一套名字，不留第二套）—— 对不上的 "
                + missingDumpKeys, missingDumpKeys.isEmpty());

        // ④ 副本带着配置走（漏复制 = "改了没反应"）
        cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills.UltimateAttack ultimateCopy =
                (cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills.UltimateAttack)
                        ultimateWithTurns(2).copy();
        cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.LastAttack lastCopy =
                (cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.LastAttack) last.copy();
        check("copy() 带着配置走：副本的额外回合数仍是 2、最后一击的折扣仍是 0.25 —— 实际 "
                        + ultimateCopy.scheduledExtraTurns() + " / "
                        + lastCopy.coefficientValues().get("lastAttackPerTurn"),
                ultimateCopy.scheduledExtraTurns() == 2
                        && lastCopy.coefficientValues().get("lastAttackPerTurn") == 0.25d);
    }

    /**
     * 技能具名系数（{@code cn.gfhnv.game.skill.SkillCoefficientTunable}）。
     * <p>
     * 一个技能可以有<b>多个</b>自己的数："每层【毁伤】打几段"、"满层那记收尾多大"、
     * "每层【弑魂之炽】给多少倍率"、"施法者回多少血"……它们住在本技能类的字段里，
     * 以<b>扁平具名键</b>写在 {@code SkillData.json} 的技能对象里（与 {@code aims} 并列），
     * 不再埋在 {@code comeToEffect} 的方法体里。公式本身（"层数 → 段数"这种算式）仍在代码里。
     * <p>
     * 三条线：
     * <ol>
     *     <li><b>出厂值逐位不变</b>：字段初始值 = 外置之前写在方法体里的那些字面量；</li>
     *     <li><b>改了真的改变伤害</b>：用数值对照（不是"读到了"）；</li>
     *     <li><b>多个系数互不串味</b>：改一个不动同技能的别的，也不动别的技能的同名系数。</li>
     * </ol>
     * ⚠️ 白厄的<b>觉醒技能</b>（死星天裁 / 血棘渡亡 / 弑魂焚诏 / 反击 / 最后一击）
     * 目前<b>不在注册表里</b>（{@code UltimateAttack} 里是 {@code new} 出来的），
     * 所以 {@code SkillData.json} 里还没有它们 —— 本用例用"直接给一个实例打补丁"
     * 证明机制是通的，缺口本身见
     * {@code project_analyses/EXTERNAL-DATA-LOADING-2026-10.md} 第十二节。
     */
    private static void testSkillCoefficients() throws Exception {
        section("技能具名系数（倍率可变的那一类）");

        /* ---------- ① 出厂值逐位等于"外置之前写在方法体里的字面量" ---------- */
        cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.FoundationStardeathVerdict
                verdict = new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills
                .FoundationStardeathVerdict();
        java.util.Map<String, Double> verdictDefaults = verdict.coefficientValues();
        check("具名系数：支柱-死星天裁 4 个出厂值与老字面量逐位相同（0.2 / 4 / 6 / 6）—— "
                        + verdictDefaults,
                verdictDefaults.size() == 4
                        && verdictDefaults.get("healRatio") == 0.2
                        && verdictDefaults.get("scourgeCostCap") == 4.0
                        && verdictDefaults.get("hitsPerScourge") == 6.0
                        && verdictDefaults.get("finishMagnification") == 6.0);

        java.util.Map<String, Double> counterDefaults =
                new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.Counterattack()
                        .coefficientValues();
        check("具名系数：灾厄-弑魂焚诏的反击 4 个出厂值（0.2 / 0.3 / 0.2 / 6）—— " + counterDefaults,
                counterDefaults.size() == 4
                        && counterDefaults.get("healRatio") == 0.2
                        && counterDefaults.get("extraHitMagnification") == 0.3
                        && counterDefaults.get("magnificationPerStack") == 0.2
                        && counterDefaults.get("extraHits") == 6.0);

        java.util.Map<String, Double> awakenDefaults =
                new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.AwakenCommonAttack()
                        .coefficientValues();
        check("具名系数：普通攻击-创生-血棘渡亡 2 个出厂值（0.2 / 4）—— " + awakenDefaults,
                awakenDefaults.size() == 2 && awakenDefaults.get("healRatio") == 0.2
                        && awakenDefaults.get("scourgeGain") == 4.0);

        java.util.Map<String, Double> edictDefaults =
                new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills
                        .CalamitySoulscorchEdict().coefficientValues();
        check("具名系数：灾厄-弑魂焚诏 2 个出厂值（1 层 / 0.75 减伤）—— " + edictDefaults,
                edictDefaults.size() == 2 && edictDefaults.get("soulscorchGain") == 1.0
                        && edictDefaults.get("absorbDamageReduction") == 0.75);

        java.util.Map<String, Double> sufferingDefaults =
                new cn.gfhnv.game.officialStuff.customSkill.flameReaverSkills.NecessarySuffering()
                        .coefficientValues();
        java.util.Map<String, Double> mournDefaults =
                new cn.gfhnv.game.officialStuff.customSkill.flameReaverSkills.MournNotAbandon()
                        .coefficientValues();
        check("具名系数：盗火行者那两招的每层倍率 / 收尾 / 起手出厂值（1.0 / 1.2、1.0 / 1.2）—— "
                        + sufferingDefaults + " " + mournDefaults,
                sufferingDefaults.get("damagePerStackMagnification") == 1.0
                        && sufferingDefaults.get("finishMagnification") == 1.2
                        && mournDefaults.get("damagePerStackMagnification") == 1.0
                        && mournDefaults.get("openingMagnification") == 1.2);

        /* 整数旋钮（NumericSkillTunable）现在也走这条路：一个键、值不变 */
        Skill restoration = cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher
                .find("game_official_content:playerOne#生命值恢复");
        java.util.Map<String, Double> restorationDefaults =
                restoration instanceof cn.gfhnv.game.skill.SkillCoefficientTunable tunable
                        ? tunable.coefficientValues() : java.util.Map.of();
        check("具名系数：整数旋钮 neededManaScale 在同一条路上、值还是 90 —— " + restorationDefaults,
                restorationDefaults.size() == 1 && restorationDefaults.get("neededManaScale") == 90.0);

        /* ---------- ② 写在技能对象里的扁平键被认下来（不是"未知键"） ---------- */
        cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.Report report =
                new cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.Report("自测-具名系数");
        cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.patch(verdict,
                new org.json.JSONObject("{\"hitsPerScourge\":1}"), report, "探针#支柱-死星天裁");
        check("具名系数：写在技能对象里的扁平键被认下来（账上有它、没有\"未知键\"）—— 应用 "
                        + report.appliedEntries() + "，跳过 " + report.skippedEntries(),
                report.errors().isEmpty() && report.skippedEntries().isEmpty()
                        && report.appliedEntries().size() == 1
                        && report.appliedEntries().getFirst().contains("hitsPerScourge")
                        && verdict.coefficientValues().get("hitsPerScourge") == 1.0);

        /* ---------- ③ 改了真的改变伤害（数值对照） ---------- */
        // 上面那把 verdict 已经被改成 1 段了，这里另起一把"出厂值"的当对照
        cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.FoundationStardeathVerdict
                sixHits = new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills
                .FoundationStardeathVerdict();
        cn.gfhnv.game.officialStuff.customEntity.players.Phainon phainon =
                new cn.gfhnv.game.officialStuff.customEntity.players.Phainon(125);
        phainon.setCriticalRate(0);
        cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.FoundationStardeathVerdict
                oneHit = new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills
                .FoundationStardeathVerdict();
        cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.patch(oneHit,
                new org.json.JSONObject("{\"hitsPerScourge\":1}"),
                new cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.Report("自测-具名系数伤害"),
                "探针#支柱-死星天裁");
        long dealtSix = verdictDamage(sixHits, phainon);
        long dealtOne = verdictDamage(oneHit, phainon);
        check("具名系数：hitsPerScourge 6 → 1，同一把刀打同一个木桩的伤害按段数缩水（"
                        + dealtSix + " → " + dealtOne + "）",
                dealtSix > 0 && dealtOne > 0 && dealtSix > dealtOne * 5 && dealtSix < dealtOne * 7);

        /* ---------- ④ 不配时逐位等于现状：默认文件里那一项原样打回来，伤害一个数都不差 ---------- */
        org.json.JSONObject defaultSkills = new org.json.JSONObject(
                cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter.skillDataJson())
                .getJSONObject("skills");
        String sufferingKey = "game_official_content:flameReaver#却是必要的苦难";
        org.json.JSONObject sufferingPatch = defaultSkills.optJSONObject(sufferingKey);
        check("具名系数：默认文件里已经有盗火行者那两招的具名系数（能改、也看得见）—— "
                        + (sufferingPatch == null ? "（没有这一项）" : sufferingPatch.keySet()),
                sufferingPatch != null && sufferingPatch.has("damagePerStackMagnification")
                        && sufferingPatch.has("finishMagnification"));

        cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver reaver =
                new cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver(150);
        reaver.setCriticalRate(0);
        LivingThing wall = new PlayerOne(125).copy();
        wall.setName("苦难木桩");
        wall.setHpMax(10_000_000L);
        wall.setHp(10_000_000L);

        cn.gfhnv.game.officialStuff.customSkill.flameReaverSkills.NecessarySuffering factorySkill =
                new cn.gfhnv.game.officialStuff.customSkill.flameReaverSkills.NecessarySuffering();
        long factoryDamage = sufferingDamage(factorySkill, reaver, wall);

        cn.gfhnv.game.officialStuff.customSkill.flameReaverSkills.NecessarySuffering patchedSkill =
                new cn.gfhnv.game.officialStuff.customSkill.flameReaverSkills.NecessarySuffering();
        cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.Report defaultReport =
                new cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.Report("自测-具名系数默认值");
        cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher
                .patch(patchedSkill, sufferingPatch, defaultReport, sufferingKey);
        long patchedDamage = sufferingDamage(patchedSkill, reaver, wall);
        check("具名系数：把默认文件里那一项原样打回来，伤害逐位不变（" + factoryDamage + " = "
                        + patchedDamage + "）—— 补丁只经手系数，没有别的副作用",
                factoryDamage > 0 && factoryDamage == patchedDamage
                        && defaultReport.skippedEntries().isEmpty());

        cn.gfhnv.game.officialStuff.customSkill.flameReaverSkills.NecessarySuffering weakened =
                new cn.gfhnv.game.officialStuff.customSkill.flameReaverSkills.NecessarySuffering();
        weakened.setCoefficientValue("damagePerStackMagnification", 0.0);
        long weakenedDamage = sufferingDamage(weakened, reaver, wall);
        check("具名系数：把 damagePerStackMagnification 改成 0，按层数的那两段伤害整段消失（"
                        + factoryDamage + " → " + weakenedDamage + "）",
                weakenedDamage > 0 && weakenedDamage * 2 < factoryDamage);

        /* ---------- ⑤ 多个具名系数互不串味 ---------- */
        cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.FoundationStardeathVerdict
                mixed = new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills
                .FoundationStardeathVerdict();
        cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.patch(mixed,
                new org.json.JSONObject("{\"healRatio\":0.5}"),
                new cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.Report("自测-系数串味"),
                "探针#支柱-死星天裁");
        java.util.Map<String, Double> mixedValues = mixed.coefficientValues();
        check("具名系数：改一个系数不动同一个技能的另外三个（" + mixedValues + "）",
                mixedValues.get("healRatio") == 0.5
                        && mixedValues.get("scourgeCostCap") == 4.0
                        && mixedValues.get("hitsPerScourge") == 6.0
                        && mixedValues.get("finishMagnification") == 6.0);

        cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.AwakenCommonAttack
                sameNameOtherSkill =
                new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills
                        .AwakenCommonAttack();
        cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.patch(sameNameOtherSkill,
                new org.json.JSONObject("{\"healRatio\":0.9}"),
                new cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.Report("自测-系数串味2"),
                "探针#普通攻击-创生-血棘渡亡");
        check("具名系数：同名系数在两个技能之间互不影响（血棘渡亡 0.9、死星天裁仍是 0.5）—— "
                        + sameNameOtherSkill.coefficientValues().get("healRatio") + " / "
                        + mixed.coefficientValues().get("healRatio"),
                sameNameOtherSkill.coefficientValues().get("healRatio") == 0.9
                        && mixed.coefficientValues().get("healRatio") == 0.5);

        Skill noCoefficient = cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher
                .find("game_official_content:iceInsect#冰冻");
        cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.Report unknownReport =
                new cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.Report("自测-系数写到别的技能");
        cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.patch(noCoefficient,
                new org.json.JSONObject("{\"hitsPerScourge\":3}"), unknownReport,
                "game_official_content:iceInsect#冰冻");
        check("具名系数：把系数写到不认识它的技能上，照旧被点名（不是静默忽略）—— 跳过 "
                        + unknownReport.skippedEntries(),
                unknownReport.skippedEntries().size() == 1
                        && unknownReport.skippedEntries().getFirst().contains("hitsPerScourge"));

        /* 副本要带着系数走（漏复制 = 选人时配置白打，阶段 2 踩过一次） */
        cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.FoundationStardeathVerdict
                copied = (cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills
                .FoundationStardeathVerdict) mixed.copy();
        check("具名系数：copy() 出来的副本带着系数（" + copied.coefficientValues() + "）",
                copied.coefficientValues().get("healRatio") == 0.5
                        && copied.coefficientValues().get("hitsPerScourge") == 6.0);

        /* 类型不对只跳过那一项，不改任何值 */
        cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.FoundationStardeathVerdict
                typed = new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills
                .FoundationStardeathVerdict();
        cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.Report badTypeReport =
                new cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.Report("自测-系数类型");
        cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.patch(typed,
                new org.json.JSONObject("{\"hitsPerScourge\":\"六\"}"), badTypeReport,
                "探针#支柱-死星天裁");
        check("具名系数：类型不对的那一项被跳过并点名，值还是出厂值（" + typed.coefficientValues()
                        + "）—— 跳过 " + badTypeReport.skippedEntries(),
                badTypeReport.skippedEntries().size() == 1
                        && typed.coefficientValues().get("hitsPerScourge") == 6.0);

        /* ---------- ⑥ 原缺口已补：觉醒技能进了注册表，SkillData.json 配得到它们 ---------- */
        // 旧断言是"现状记录：觉醒技能不在注册表里、默认文件里没有它们"。
        // 2026-10-03 加了技能注册表之后这条现状不成立了，于是改成"缺口已补"的正向断言。
        List<String> awakenKeysInFile = new ArrayList<>();
        for (String key : defaultSkills.keySet()) {
            if (key.startsWith("game_official_content:phainon#")
                    && (key.contains("死星天裁") || key.contains("血棘渡亡")
                    || key.contains("弑魂焚诏") || key.contains("最后一击"))) {
                awakenKeysInFile.add(key);
            }
        }
        check("技能注册表：白厄的觉醒技能现在写进 SkillData.json 了（旧缺口已补）—— "
                        + awakenKeysInFile,
                awakenKeysInFile.size() == 5
                        && awakenKeysInFile.contains("game_official_content:phainon#支柱-死星天裁")
                        && awakenKeysInFile.contains("game_official_content:phainon#最后一击"));

        Skill verdictPrototype = cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher
                .find("game_official_content:phainon#支柱-死星天裁");
        check("技能注册表：按「实体id#技能名」能定位到注册表里的觉醒技能原型 —— "
                        + (verdictPrototype == null ? "（找不到）" : verdictPrototype.getId()),
                verdictPrototype instanceof
                        cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills
                                .FoundationStardeathVerdict);

        // 数值对照：改配置文件里的那一项 → 运行时按 原型.copy() 取到的副本真的少打 5/6 的伤害；
        // 再把默认值打回去 → 与出厂实例逐位一致（不配时 = 现状）
        org.json.JSONObject verdictPatch = defaultSkills.optJSONObject(
                "game_official_content:phainon#支柱-死星天裁");
        check("技能注册表：默认文件里那一项写着出厂系数（看得见才改得动）—— "
                        + (verdictPatch == null ? "（没有这一项）" : verdictPatch.keySet()),
                verdictPatch != null && verdictPatch.optDouble("hitsPerScourge", -1) == 6.0);

        cn.gfhnv.game.officialStuff.customEntity.players.Phainon awakenUser =
                new cn.gfhnv.game.officialStuff.customEntity.players.Phainon(125);
        awakenUser.setCriticalRate(0);
        long factoryAwakenDamage = verdictDamage(
                new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills
                        .FoundationStardeathVerdict(), awakenUser);

        cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.applyJson(
                "{\"skills\":{\"game_official_content:phainon#支柱-死星天裁\":"
                        + "{\"hitsPerScourge\":1}}}", "自测-觉醒技能走配置");
        long configuredAwakenDamage = verdictDamage(
                (cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills
                        .FoundationStardeathVerdict) cn.gfhnv.game.world.World
                        .prototypeCopyOf(cn.gfhnv.game.officialStuff.customSkill.phainonSkills
                                .awakenSkills.FoundationStardeathVerdict.class), awakenUser);
        check("技能注册表：配置改 hitsPerScourge 6 → 1 之后，原型.copy() 出来的副本伤害按段数缩水（"
                        + factoryAwakenDamage + " → " + configuredAwakenDamage + "）",
                factoryAwakenDamage > 0 && configuredAwakenDamage > 0
                        && factoryAwakenDamage > configuredAwakenDamage * 5
                        && factoryAwakenDamage < configuredAwakenDamage * 7);

        cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher
                .patch(verdictPrototype, verdictPatch,
                        new cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.Report(
                                "自测-觉醒技能还原默认值"),
                        "game_official_content:phainon#支柱-死星天裁");
        long restoredAwakenDamage = verdictDamage(
                (cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills
                        .FoundationStardeathVerdict) cn.gfhnv.game.world.World
                        .prototypeCopyOf(cn.gfhnv.game.officialStuff.customSkill.phainonSkills
                                .awakenSkills.FoundationStardeathVerdict.class), awakenUser);
        check("技能注册表：把默认值原样打回原型，副本的伤害逐位等于出厂实例（不配时 = 现状）（"
                        + factoryAwakenDamage + " = " + restoredAwakenDamage + "）",
                factoryAwakenDamage == restoredAwakenDamage);
    }

    /**
     * 技能注册表（{@code World} 的第 4 张表，2026-10-03 加）。
     * <p>
     * 技能原来<b>没有 id</b>：它只属于"某只实体手上的控制器"，同一个技能类会被五只模板各造一份、
     * 倍率各不相同。于是"配置怎么定位一个技能"只能用 {@code <实体id>#<技能名>}，
     * 而那些<b>不在控制器里</b>的技能（白厄的觉醒技能由大招现场 {@code new}）谁都碰不到 ——
     * 这一轮把注册表和 id 补上了。
     * <p>
     * 四条线：
     * <ol>
     *     <li><b>id 口径</b>：显式 id 优先，没有才按类名派生（首字母小写），再加 {@code 模组id:} 前缀；</li>
     *     <li><b>撞名 fail loud</b>：同一个完整 id 落到两个不同的类上 → 直接抛异常，逼作者写显式 id；</li>
     *     <li><b>查找</b>：完整 id / 短名 / 类名 / 全限定类名，逐级退让，有歧义不猜；</li>
     *     <li><b>原型 + 副本</b>：配置打在原型上，运行时取 {@code 原型.copy()}。</li>
     * </ol>
     * 用例里用的是<b>项目里真实的那两对同名类</b>（{@code universalSkill.CommonAttack} 与
     * {@code actorLiXiaoYanSkills.CommonAttack}、两个包各自的 {@code UltimateAttack}），
     * 不是另造的假形状。
     */
    private static void testSkillRegistry() {
        section("技能注册表（id / 派生 / 撞名 fail loud）");

        /* ---------- ① 注册表里有什么 ---------- */
        List<Skill> registry = cn.gfhnv.game.world.World.getSkillList();
        List<String> classNames = new ArrayList<>();
        for (Skill skill : registry) {
            classNames.add(skill.getClass().getSimpleName());
        }
        check("技能注册表：显式登记的原型与控制器里的技能<b>同一张表</b>（按类去重后 " + registry.size()
                        + " 条）",
                registry.size() >= 20
                        && classNames.contains("AwakenCommonAttack")
                        && classNames.contains("LastAttack")
                        && classNames.contains("Freeze"));

        /* ---------- ① 完整 id / 短名 / 类名 / 全限定类名都查得到，而且是同一条 ---------- */
        Skill byFullId = cn.gfhnv.game.world.World.findSkill("game_official_content:awakenCommonAttack");
        Skill byShortId = cn.gfhnv.game.world.World.findSkill("awakenCommonAttack");
        Skill byClassName = cn.gfhnv.game.world.World.findSkill("AwakenCommonAttack");
        Skill byQualifiedName = cn.gfhnv.game.world.World.findSkill(
                "cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.AwakenCommonAttack");
        check("技能注册表：完整 id / 短名 / 类名 / 全限定类名查到的是同一条（"
                        + (byFullId == null ? "（找不到）" : byFullId.getId()) + "）",
                byFullId != null && byFullId == byShortId && byFullId == byClassName
                        && byFullId == byQualifiedName
                        && byFullId.getClass()
                        == cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills
                        .AwakenCommonAttack.class);
        check("技能注册表：查不到的名字返回 null（不抛异常、也不乱猜）",
                cn.gfhnv.game.world.World.findSkill("noSuchSkill") == null
                        && cn.gfhnv.game.world.World.findSkill("   ") == null
                        && cn.gfhnv.game.world.World.findSkill(null) == null);

        /* ---------- ② 派生规则：类名首字母小写 + 模组id: 前缀 ---------- */
        Skill freeze = cn.gfhnv.game.world.World.findSkill("game_official_content:freeze");
        check("技能注册表：没写显式 id 的按类名派生（Freeze → game_official_content:freeze）—— "
                        + (freeze == null ? "（找不到）" : freeze.getId()),
                freeze != null && freeze.getClass()
                        == cn.gfhnv.game.officialStuff.customSkill.universalSkill.Freeze.class);

        /* ---------- ③ 显式短 id 优先于类名派生（真实那一对之一） ---------- */
        Skill liCommon = cn.gfhnv.game.world.World
                .findSkill("game_official_content:liXiaoYanCommonAttack");
        Skill universalCommon = cn.gfhnv.game.world.World
                .findSkill("game_official_content:commonAttack");
        check("技能注册表：李晓焰的 CommonAttack 用的是<b>显式 id</b>，没有被派生出来的 commonAttack 顶掉"
                        + "（" + (liCommon == null ? "（找不到）" : liCommon.getId()) + " / "
                        + (universalCommon == null ? "（找不到）" : universalCommon.getId()) + "）",
                liCommon != null && universalCommon != null && liCommon != universalCommon
                        && liCommon.getClass()
                        == cn.gfhnv.game.officialStuff.customSkill.actorLiXiaoYanSkills.CommonAttack.class
                        && universalCommon.getClass()
                        == cn.gfhnv.game.officialStuff.customSkill.universalSkill.CommonAttack.class);

        /* ---------- ② 派生 id 撞名 → fail loud（真实那一对：两个包的 UltimateAttack） ---------- */
        Skill liUltimate = new cn.gfhnv.game.officialStuff.customSkill.actorLiXiaoYanSkills
                .UltimateAttack();
        check("技能注册表：李晓焰的大招自己写了显式 id（这就是它不跟白厄那个撞的原因）—— "
                        + liUltimate.getId(),
                "liXiaoYanUltimateAttack".equals(liUltimate.getId()));

        // 把显式 id 拿掉 = 假装作者没写 → 派生路径立刻复现撞名（用真实的两个类，不是造的形状）
        Skill stripped = new cn.gfhnv.game.officialStuff.customSkill.actorLiXiaoYanSkills
                .UltimateAttack();
        stripped.setId(null);
        Mod collideMod = new Mod("skillProbeMod") {
        };
        collideMod.addSkill(new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills
                .UltimateAttack());
        Throwable collision = null;
        try {
            collideMod.addSkill(stripped);
        } catch (RuntimeException e) {
            collision = e;
        }
        check("技能注册表：派生 id 撞名时<b>直接报错</b>（fail loud，逼作者写显式 id）—— "
                        + (collision == null ? "没有报错（这就是静默去重）" : collision.getMessage()),
                collision instanceof IllegalStateException
                        && collision.getMessage().contains("skillProbeMod:ultimateAttack")
                        && collision.getMessage().contains("setId"));
        check("技能注册表：撞名的那一个没有被半登记（id 还是空的，表里也没有它）",
                stripped.getId() == null && collideMod.getSkills().size() == 1);

        // 同一个类登记两遍不算撞（参数化技能本来就该共用一个 id）
        Mod sameClassMod = new Mod("skillProbeModSameClass") {
        };
        Throwable sameClassFailure = null;
        try {
            sameClassMod.addSkill(new cn.gfhnv.game.officialStuff.customSkill.universalSkill
                    .Freeze());
            sameClassMod.addSkill(new cn.gfhnv.game.officialStuff.customSkill.universalSkill
                    .Freeze());
        } catch (RuntimeException e) {
            sameClassFailure = e;
        }
        check("技能注册表：同一个类登记两遍不算撞名（参数化技能共用一个 id，两份实例 id 相同）—— "
                        + (sameClassFailure == null ? sameClassMod.getSkills().get(0).getId() : sameClassFailure),
                sameClassFailure == null && sameClassMod.getSkills().size() == 2
                        && "skillProbeModSameClass:freeze".equals(sameClassMod.getSkills().get(0).getId()));

        // 匿名类派生不出 id：也要报错，而不是给一个空 id
        Throwable anonymousFailure = null;
        try {
            collideMod.addSkill(new Skill("匿名招", "没有类名", 0, 0, 0, 0) {
            });
        } catch (RuntimeException e) {
            anonymousFailure = e;
        }
        check("技能注册表：匿名类派生不出 id 也报错（不许悄悄给个空 id）—— "
                        + (anonymousFailure == null ? "没有报错" : anonymousFailure.getMessage()),
                anonymousFailure instanceof IllegalStateException
                        && anonymousFailure.getMessage().contains("显式 id"));

        /* ---------- ③ 写法：显式 id 与派生 id 可以在同一个模组里共存 ---------- */
        Mod mixedMod = new Mod("skillProbeModMixed") {
        };
        mixedMod.addSkill(new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills
                .UltimateAttack());
        mixedMod.addSkill(new cn.gfhnv.game.officialStuff.customSkill.actorLiXiaoYanSkills
                .UltimateAttack());
        check("技能注册表：显式 id 那一份不与派生那一份撞（同一对类，一个派生一个显式）—— "
                        + mixedMod.getSkills().get(0).getId() + " / " + mixedMod.getSkills().get(1).getId(),
                "skillProbeModMixed:ultimateAttack".equals(mixedMod.getSkills().get(0).getId())
                        && "skillProbeModMixed:liXiaoYanUltimateAttack"
                        .equals(mixedMod.getSkills().get(1).getId()));

        /* ---------- ④ 归属与副本：配置打原型、运行时取副本 ---------- */
        Skill prototype = cn.gfhnv.game.world.World
                .findSkill("game_official_content:foundationStardeathVerdict");
        check("技能注册表：原型记着它归属哪只模板（配置键靠它）—— "
                        + cn.gfhnv.game.world.World.skillOwnerIdOf(prototype),
                prototype != null
                        && "game_official_content:phainon".equals(
                        cn.gfhnv.game.world.World.skillOwnerIdOf(prototype)));
        Skill copy = cn.gfhnv.game.world.World.prototypeCopyOf(
                cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills
                        .FoundationStardeathVerdict.class);
        check("技能注册表：原型.copy() 与原型是两个实例、但数值与 id 一致（副本带着配置走）",
                copy != null && copy != prototype
                        && copy.getId().equals(prototype.getId())
                        && cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher
                        .stateOf(copy).equals(cn.gfhnv.game.system.configLoadingSystem
                                .SkillDataPatcher.stateOf(prototype)));
        Throwable missingPrototype = null;
        try {
            cn.gfhnv.game.world.World.prototypeCopyOf(
                    cn.gfhnv.game.officialStuff.customSkill.universalSkill.Freeze.class);
        } catch (RuntimeException e) {
            missingPrototype = e;
        }
        check("技能注册表：没登记成原型的类取副本会报错（不许静默给一个出厂值实例）—— "
                        + (missingPrototype == null ? "没有报错" : missingPrototype.getMessage()),
                missingPrototype instanceof IllegalStateException);

        /* ---------- 没污染注册表：探针模组的东西要 registerItself() 才进全局表 ---------- */
        boolean leaked = false;
        for (Skill skill : cn.gfhnv.game.world.World.getSkillList()) {
            if (skill.getClass() == cn.gfhnv.game.officialStuff.customSkill.phainonSkills
                    .normalSkills.UltimateAttack.class
                    && skill.getId() != null && skill.getId().startsWith("skillProbeMod")) {
                leaked = true;
            }
        }
        check("技能注册表：探针模组没调 registerItself()，它的内容没有混进全局表（不给后面的用例留污染）",
                !leaked);
    }

    /**
     * 技能 id <b>覆盖率</b>：注册表覆盖到的技能，每一份实例都必须有 id。
     * <p>
     * <b>为什么要有这一条</b>：上一轮给技能加了 id（{@code World#findSkill} /
     * {@code Mod#addSkill}），但落 id 只落在"<b>同类第一条</b>"上 —— 而控制器里存的是技能的
     * <b>副本</b>（{@code UniversalController} 构造那一刻就 {@code copy()} 了一份），
     * 同一个类还会被多只模板各造一份（{@code universalSkill.CommonAttack} 有 5 份）。
     * 于是用户实测看到的是<b>半截</b>的样子：卡厄斯兰那的 {@code skills} 里
     * 「战技」「大招」有 id，「普通攻击」（那个类被别的模板先登记过）却是 {@code null} ——
     * {@code /data get} 出来就是一个没有 {@code id} 的技能。
     * <p>
     * 探针走两条线，都是<b>只读</b>的：
     * <ol>
     *     <li>注册表视图（{@code World#getSkillList()}）：条条有 id，且两两不同；</li>
     *     <li>每一只模板的控制器技能表（{@code World.getEntityList()} ∪ 各模组的实体表）：
     *     <b>每一份实例</b>都有 id —— 这一条才是用户在 {@code /data} 里看到的那一面。</li>
     * </ol>
     * 没 id 的会逐条打印出来（跑自测时能直接看到清单）。
     * <p>
     * <b>不在覆盖范围内的两类</b>（都不是"漏了 id"，别为了凑绿硬塞）：
     * <ul>
     *     <li>运行时的<b>纯参数载体</b>：例如 {@code FlameReaver#bossJoinsJointAttack} 里
     *     {@code new CloudOfDeath()} 只用来借倍率算一次伤害，它不进任何技能表、
     *     也不出现在 {@code /data} 里 —— id 对它没有意义；</li>
     *     <li>构造期留下、<b>没进控制器</b>的那份临时列表（{@code Phainon.skills} 在模板上）：
     *     它是"控制器副本"复制前的原物，运行时用的是控制器里那份（有 id），
     *     而模板不进 {@code World.things}，{@code /data get @e} 也看不到它。</li>
     * </ul>
     */
    private static void testSkillIdCoverage() {
        section("技能 id 覆盖率（注册表 + 各实体控制器的技能表）");

        /* ---------- ① 注册表视图：条条有 id、两两不同 ---------- */
        List<Skill> registry = World.getSkillList();
        List<String> registryWithoutId = new ArrayList<>();
        List<String> registryDuplicated = new ArrayList<>();
        Map<String, String> idToClass = new java.util.HashMap<>();
        for (Skill skill : registry) {
            String id = skill.getId();
            if (id == null || id.isEmpty()) {
                registryWithoutId.add(skill.getClass().getSimpleName() + "「" + skill.getName() + "」");
                continue;
            }
            String previous = idToClass.put(id, skill.getClass().getName());
            if (previous != null && !previous.equals(skill.getClass().getName())) {
                registryDuplicated.add(id + "：" + previous + " / " + skill.getClass().getName());
            }
        }
        check("技能 id 覆盖率：注册表视图 " + registry.size() + " 条，条条有 id —— 没 id 的 "
                        + registryWithoutId.size() + " 条 " + registryWithoutId,
                registryWithoutId.isEmpty());
        check("技能 id 覆盖率：注册表视图里的 id 两两不同（撞名的 " + registryDuplicated.size()
                        + " 条）" + registryDuplicated,
                registryDuplicated.isEmpty());

        /* ---------- ② 每一只模板的控制器技能表：每一份实例都有 id ---------- */
        List<cn.gfhnv.game.entity.Entity> templates = new ArrayList<>();
        for (cn.gfhnv.game.entity.Entity entity : World.getEntityList()) {
            if (!templates.contains(entity)) {
                templates.add(entity);
            }
        }
        for (Mod mod : World.getModList()) {
            for (cn.gfhnv.game.entity.Entity entity : mod.getEntityList()) {
                if (!templates.contains(entity)) {
                    templates.add(entity);
                }
            }
        }
        List<String> instancesWithoutId = new ArrayList<>();
        int instances = 0;
        for (cn.gfhnv.game.entity.Entity entity : templates) {
            if (!(entity instanceof LivingThing living) || living.getController() == null
                    || living.getController().getSkills() == null) {
                continue;
            }
            for (Skill skill : living.getController().getSkills()) {
                if (skill == null) {
                    continue;
                }
                instances++;
                if (skill.getId() == null || skill.getId().isEmpty()) {
                    instancesWithoutId.add(World.fullIdOf(living) + " 的 "
                            + skill.getClass().getSimpleName() + "「" + skill.getName() + "」");
                }
            }
        }
        System.out.println("  [探针] 模板 " + templates.size() + " 只；控制器里的技能实例 "
                + instances + " 份，没有 id 的是 " + instancesWithoutId.size() + " 份：");
        for (String one : instancesWithoutId) {
            System.out.println("    - " + one);
        }
        check("技能 id 覆盖率：注册表覆盖到的技能（显式原型 ∪ 各实体控制器里的技能）"
                        + instances + " 份实例，没有 id 的是 " + instancesWithoutId.size() + " 份",
                instances > 0 && instancesWithoutId.isEmpty());

        /* ---------- ③ 用户实测报过的那一组：白厄 → 卡厄斯兰那 ---------- */
        Phainon phainon = null;
        for (cn.gfhnv.game.entity.Entity entity : templates) {
            if (entity instanceof Phainon candidate) {
                phainon = candidate;
                break;
            }
        }
        check("技能 id 覆盖率：注册表里有白厄模板（下面那条实测复现靠它）", phainon != null);
        if (phainon == null) {
            return;
        }
        List<String> templateIds = new ArrayList<>();
        for (Skill skill : phainon.getController().getSkills()) {
            templateIds.add(String.valueOf(skill.getId()));
        }
        check("技能 id 覆盖率：白厄控制器里三条技能都有 id（被别的模板先登记过的「普通攻击」也在内）—— "
                        + templateIds,
                templateIds.size() == 3 && !templateIds.contains("null")
                        && templateIds.contains("game_official_content:commonAttack")
                        && templateIds.contains("game_official_content:normalSkill")
                        && templateIds.contains("game_official_content:ultimateAttack"));

        // 用户实测的是"卡厄斯兰那"（战斗里那份白厄副本），/data get 出来的是它 skills 字段里那三份。
        // 副本走的是"模板控制器 → copy()"这条路，所以这一条同时验了"id 会跟着副本走"。
        Phainon awakenCopy = (Phainon) phainon.copy();
        List<String> copyIds = new ArrayList<>();
        for (Skill skill : awakenCopy.getSkills()) {
            copyIds.add(String.valueOf(skill.getId()));
        }
        check("技能 id 覆盖率：白厄的副本（战斗里那份「卡厄斯兰那」）skills 里三条也都带 id —— "
                        + copyIds,
                copyIds.size() == 3 && !copyIds.contains("null")
                        && copyIds.contains("game_official_content:commonAttack"));
    }

    /**
     * <b>形态专属技能也必须能在 {@code SkillData.json} 里看到</b>（2026-10-25 用户实测踩到）。
     * <p>
     * 千冶·刃普通形态只有 2 条技能，另外 3 条（{@code 淬锋，断魄} / {@code 刃下，归葬} /
     * {@code 千冶铸一，万劫烬灭}）只在【无量忿怒】里换上去。它们平时不在控制器里，
     * 于是"实体 → 控制器 → 技能"这条收集路径**看不到它们** —— 表现为
     * {@code SkillData.json} 里根本没有这三个键，改倍率静默无效。
     * <p>
     * 修法是 {@code Mod#addSkill(owner, skill)} 登记成挂在模板名下的原型。这条守卫盯的就是它：
     * <b>模板上出现过的技能（控制器里的 + 原型表里的）都必须能在 {@code SkillDataPatcher#all()} 里找到键。</b>
     */
    private static void testSkillKeysCoverEveryFormSkill() {
        section("SkillData 键覆盖：只在战斗形态里出现的技能也要有键");

        java.util.Map<String, Skill> all = cn.gfhnv.game.system.configLoadingSystem
                .SkillDataPatcher.all();
        String[] expected = {
                "game_official_content:mortenax_blade#残锋，掠尽",
                "game_official_content:mortenax_blade#骸骨当炉，血肉即薪",
                "game_official_content:mortenax_blade#淬锋，断魄",
                "game_official_content:mortenax_blade#刃下，归葬",
                "game_official_content:mortenax_blade#千冶铸一，万劫烬灭",
        };
        java.util.List<String> missing = new ArrayList<>();
        for (String key : expected) {
            if (!all.containsKey(key) || all.get(key) == null) {
                missing.add(key);
            }
        }
        check("千冶·刃的 5 个技能都有 SkillData 键（含只在结界里的 3 个）—— 缺 " + missing,
                missing.isEmpty());

        // 更普适的一条：注册表里每一条"挂在实体名下"的技能，键都要能被解析回来
        int checked = 0;
        java.util.List<String> unresolved = new ArrayList<>();
        for (java.util.Map.Entry<String, Skill> entry : all.entrySet()) {
            if (!entry.getKey().startsWith("game_official_content:")) {
                continue;
            }
            checked++;
            if (cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.find(entry.getKey()) == null) {
                unresolved.add(entry.getKey());
            }
        }
        check("注册表里 " + checked + " 个键都能被 find() 解析回来 —— 解析不了 " + unresolved,
                checked > 0 && unresolved.isEmpty());
    }

    /**
     * 跑本主题的全部用例（顺序 = 下面列的顺序）。
     */
    public static void runAll() throws Exception {
        testSkillDataPatch();
        testSkillCoefficients();
        testAwakenNumbersExternalized();
        testAiScoreBonus();
        testSkillRegistry();
        testSkillIdCoverage();
        testSkillKeysCoverEveryFormSkill();
    }

    private SkillDataTests() {
    }
}
