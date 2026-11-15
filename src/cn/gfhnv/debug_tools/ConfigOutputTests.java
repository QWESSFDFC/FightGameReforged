package cn.gfhnv.debug_tools;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.mod.Mod;
import cn.gfhnv.game.skill.Skill;

import java.util.ArrayList;
import java.util.Arrays;

import static cn.gfhnv.debug_tools.TestKit.*;
import static cn.gfhnv.debug_tools.TestProbes.*;
import static cn.gfhnv.debug_tools.TestSupport.*;

/**
 * 自测：**配置**。
 * <p>
 * 主题：配置流程不该刷屏 / 模组配置。
 * <p>
 * 由 {@code TestCommandSystem#main} 按顺序调用 {@code ConfigOutputTests#runAll()}。
 */
public final class ConfigOutputTests {

    private ConfigOutputTests() {
    }

    /**
     * <b>启动期输出的静默策略</b>（2026-10-03，用户原话"把项目运行时不必要的提示删了"）。
     * <p>
     * 钉住三件事：① 开关名没写错、默认是静默；② <b>"一切正常"的播报默认一行不打</b>，
     * 但记账仍然累积（所以"应用 N 项"那条总量判据还在）；③ 汇总那一行恰好一行、
     * 且带出"应用 / 跳过"两个口径。顺带确认"跳过 / 错误"那一路<b>不受开关影响</b>。
     */
    private static void testConfigOutputSilence() throws Exception {
        section("启动播报：正常路径默认静默 + verbose 开关");

        Boolean originalForced = forcedVerbose();
        try {
            check("静默策略：系统属性名就是文档里写的那一个（" + cn.gfhnv.game.system.configLoadingSystem
                            .ConfigOutput.VERBOSE_PROPERTY + "）",
                    "dsh.config.verbose".equals(cn.gfhnv.game.system.configLoadingSystem
                            .ConfigOutput.VERBOSE_PROPERTY));
            cn.gfhnv.game.system.configLoadingSystem.ConfigOutput.setVerboseForTest(false);
            check("静默策略：默认（开关关着）就是静默", !cn.gfhnv.game.system.configLoadingSystem
                    .ConfigOutput.verbose());

            /* ---------- ① info 静静吞掉，problem / noteworthy 照旧出声 ---------- */
            java.io.ByteArrayOutputStream sink = new java.io.ByteArrayOutputStream();
            java.io.PrintStream original = System.out;
            String printed;
            try {
                System.setOut(new java.io.PrintStream(sink, true, "UTF-8"));
                cn.gfhnv.game.system.configLoadingSystem.ConfigOutput.info("这条不该出现");
                cn.gfhnv.game.system.configLoadingSystem.ConfigOutput
                        .problem("[配置跳过] 这一条必须出现");
                cn.gfhnv.game.system.configLoadingSystem.ConfigOutput
                        .noteworthy("[配置提醒] 这一条也必须出现");
            } finally {
                System.setOut(original);
                printed = sink.toString("UTF-8");
            }
            check("静默策略：info（纯成功播报）默认一行不打 —— 实际输出 " + printed.lines().count() + " 行",
                    printed.lines().count() == 2 && !printed.contains("这条不该出现"));
            check("静默策略：跳过 / 错误 / 提醒不受开关影响（诊断能力一个字没少）",
                    printed.contains("[配置跳过] 这一条必须出现")
                            && printed.contains("[配置提醒] 这一条也必须出现"));

            /* ---------- ② 明细静默了，但记账还在；汇总恰好一行 ---------- */
            cn.gfhnv.game.system.configLoadingSystem.ConfigOutput.resetTallies();
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Report report =
                    new cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Report("自测-静默策略");
            report.applied("probe", "base.level", "125");
            sink = new java.io.ByteArrayOutputStream();
            String detail;
            String summary;
            try {
                System.setOut(new java.io.PrintStream(sink, true, "UTF-8"));
                report.print();
                detail = sink.toString("UTF-8");
                summary = cn.gfhnv.game.system.configLoadingSystem.ConfigOutput.printPatchSummary();
            } finally {
                System.setOut(original);
            }
            check("静默策略：补丁器的逐条明细默认静默（原来那句「应用 1 项，影响 0 个模板」不再出现）",
                    detail.isEmpty());
            check("静默策略：但记账仍然保留，汇总那一行把「应用 1 项」带出来了 —— " + summary,
                    summary != null && summary.contains("[配置]") && summary.contains("应用 1 项")
                            && summary.contains("跳过 0 项") && summary.contains("自测-静默策略"));
            int after = cn.gfhnv.game.system.configLoadingSystem.ConfigOutput.tallyCount();
            check("静默策略：汇总打完之后记账清空（下次启动从零开始，" + after + " 条剩余）", after == 0);

            /* ---------- ③ 开关打开时，明细照原样打回来（"能看回全量的路"） ---------- */
            cn.gfhnv.game.system.configLoadingSystem.ConfigOutput.setVerboseForTest(true);
            cn.gfhnv.game.system.configLoadingSystem.ConfigOutput.resetTallies();
            java.io.ByteArrayOutputStream verboseSink = new java.io.ByteArrayOutputStream();
            String verboseText;
            try {
                System.setOut(new java.io.PrintStream(verboseSink, true, "UTF-8"));
                cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Report verboseReport =
                        new cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher
                                .Report("自测-verbose");
                verboseReport.applied("probe", "base.level", "125");
                verboseReport.print();
            } finally {
                System.setOut(original);
                verboseText = verboseSink.toString("UTF-8");
            }
            check("静默策略：开关打开后逐条明细照原样打回来（应用 N 项 / 影响 M 个模板）—— "
                            + verboseText.trim(),
                    verboseText.contains("应用 1 项，跳过 0 项，影响 0 个模板"));
            cn.gfhnv.game.system.configLoadingSystem.ConfigOutput.resetTallies();
        } finally {
            cn.gfhnv.game.system.configLoadingSystem.ConfigOutput.setVerboseForTest(originalForced);
        }
        check("静默策略收尾：自测没有把开关留在打开状态（否则下次启动会刷一堆明细）",
                !isVerboseForced());
    }

    /**
     * 模组配置（{@code config/data/<模组id>.json} + {@code @ModConfig} / {@code ModDataAware}）。
     * <p>
     * 这一段的重点是<b>不给模组加载链引入新风险</b>，所以除了"能读到"之外，
     * 一半的断言都在验"出错时会怎样"：
     * <ul>
     *     <li>没有配置文件 → 照样调用 {@code applyConfig}，文档是空的，<b>不报错</b>；</li>
     *     <li>没实现接口的模组 → 正常跳过，一个字都不用改；</li>
     *     <li>模组在 {@code applyConfig} 里抛异常（含 {@link Error}）→ 只跳过它自己；</li>
     *     <li>配置文件整个坏掉 → 只打印，不抛。</li>
     * </ul>
     * 磁盘那几条用例把工作目录切到 {@code out/} 下的临时目录里跑，
     * <b>真 {@code config/} 目录一个字都不碰</b>（跑完把工作目录换回来）。
     */
    private static void testModConfig() throws Exception {
        section("模组配置（阶段 4）");

        /* ---------- ① ModConfigDocument 的语义（不碰磁盘） ---------- */
        // 文档的根就是"这个模组自己的分组"（ConfigLoader 从整份文件里摘出来交给模组），
        // common 段单独给一份 —— 与真实链路一致。
        org.json.JSONObject ownGroup = new org.json.JSONObject(
                "{\"initialStacks\":4,"
                        + "\"skills\":{\"frostSword\":{\"requiredStacks\":3}},"
                        + "\"names\":[\"甲\",\"乙\",7],\"flag\":true,\"speed\":2.5}");
        org.json.JSONObject commonGroup = new org.json.JSONObject(
                "{\"containerLimit\":8,\"entities\":{\"game_official_content:playerOne\":"
                        + "{\"base\":{\"baseSpeed\":222}}}}");
        cn.gfhnv.game.mod.config.ModConfigDocument document =
                new cn.gfhnv.game.mod.config.ModConfigDocument("drunkenSword", ownGroup, commonGroup);
        check("模组文档：读得到自己的分组（含默认值回退）",
                document.getInt("drunkenSword/initialStacks", 2) == 4
                        && document.getInt("drunkenSword/skills/frostSword/requiredStacks", 9) == 3
                        && document.getInt("drunkenSword/noSuchKey", 7) == 7);
        check("模组文档：路径只写一段时也读得到自己的分组（最自然的写法不该读不到）",
                document.getInt("initialStacks", 2) == 4);
        check("模组文档：共享区 common 读得到（所有模组都看得见）",
                document.getInt("common/containerLimit", 0) == 8);
        check("模组文档：读不到别的模组的分组（配置互相隔离）",
                new cn.gfhnv.game.mod.config.ModConfigDocument("drunkenSword",
                        new org.json.JSONObject("{\"secret\":1}"), null)
                        .getInt("otherMod/secret", -1) == -1);
        check("模组文档：类型不对时返回默认值，而不是抛异常",
                document.getString("drunkenSword/speed", "回退").equals("回退")
                        && document.getBoolean("drunkenSword/initialStacks", false) == false
                        && document.getLong("drunkenSword/flag", 5L) == 5L);
        check("模组文档：has() 能区分「写了 0」和「没写」",
                document.has("drunkenSword/initialStacks") && !document.has("drunkenSword/没写"));
        check("模组文档：字符串列表会跳过非字符串元素",
                document.getStringList("drunkenSword/names", new ArrayList<>())
                        .equals(Arrays.asList("甲", "乙")));
        check("模组文档：空文档上所有读取都返回默认值（没有配置文件时的行为）",
                cn.gfhnv.game.mod.config.ModConfigDocument.empty().getInt("任意/路径", 3) == 3
                        && !cn.gfhnv.game.mod.config.ModConfigDocument.empty().has("任意/路径"));

        /* ---------- ② 接口是可选的：不改 Mod 的抽象契约 ---------- */
        // 契约 2026-10-03 改过：加了可选的 defaultConfig()（默认返回 null，用来"首次运行生成默认配置文件"）。
        // 但"模组作者只需要实现 1 个方法"这条**没变** —— 所以断言拆成"必须实现几个 / 可选几个"，
        // 而不是简单放宽容忍度（否则以后往接口上乱加方法也没人拦）。
        java.lang.reflect.Method[] awareMethods =
                cn.gfhnv.game.mod.config.ModDataAware.class.getDeclaredMethods();
        int mustImplement = 0;
        int optionalCount = 0;
        for (java.lang.reflect.Method method : awareMethods) {
            if (method.isDefault()) optionalCount++;
            else mustImplement++;
        }
        check("模组契约：ModDataAware 只有 1 个<b>必须实现</b>的方法（实扫 " + awareMethods.length
                        + " 个：必须 " + mustImplement + " / 可选 " + optionalCount + "）",
                mustImplement == 1 && optionalCount == 1);
        check("模组契约：defaultConfig() 是<b>可选</b>的（不实现也编得过），默认返回 null = 不生成配置"
                        + "—— 用它就能让模组「首次运行自带一份默认配置文件」",
                new cn.gfhnv.game.mod.config.ModDataAware() {
                    @Override
                    public void applyConfig(cn.gfhnv.game.mod.config.ModConfigDocument config) {
                    }
                }.defaultConfig() == null);
        check("模组契约：ModDataAware 是纯可选接口（没有往 Mod 上加抽象方法，现有模组零改动）"
                        + "—— 诊断 抽象方法数=" + java.util.Arrays.stream(Mod.class.getDeclaredMethods())
                        .filter(method -> java.lang.reflect.Modifier.isAbstract(method.getModifiers()))
                        .count() + " 接口可赋值="
                        + cn.gfhnv.game.mod.config.ModDataAware.class.isAssignableFrom(Mod.class),
                java.util.Arrays.stream(Mod.class.getDeclaredMethods())
                        .noneMatch(method -> java.lang.reflect.Modifier.isAbstract(method.getModifiers()))
                        && !cn.gfhnv.game.mod.config.ModDataAware.class
                        .isAssignableFrom(Mod.class));
        // 用"反射读注解"这条链拿到注解里的值：光有 RUNTIME 可见性不等于"游戏真的在读它"
        // （2026-10-03 修：修之前 ConfigLoader 只认 Mod.getMOD_ID()，这条断言是假的）。
        String annotatedIdByReflection = AnnotatedProbeMod.class
                .getAnnotation(cn.gfhnv.game.mod.config.ModConfig.class).id().trim();
        check("模组契约：@ModConfig 是运行期可见的注解，而且游戏真的用反射读它（不再是纯文档）"
                        + "—— 诊断 反射读到=" + annotatedIdByReflection + " 加载器解析出="
                        + cn.gfhnv.game.system.configLoadingSystem.ConfigLoader
                        .resolveModConfigId(new AnnotatedProbeMod("notTheSameModId")),
                cn.gfhnv.game.mod.config.ModConfig.class
                        .isAnnotationPresent(java.lang.annotation.Retention.class)
                        && cn.gfhnv.game.mod.config.ModConfig.class
                        .getAnnotation(java.lang.annotation.Retention.class).value()
                        == java.lang.annotation.RetentionPolicy.RUNTIME
                        // 关键的一条：反射读到的那个值，正是加载器解析出来的分组名（同一条链）
                        && cn.gfhnv.game.system.configLoadingSystem.ConfigLoader
                        .resolveModConfigId(new AnnotatedProbeMod("notTheSameModId"))
                        .equals(annotatedIdByReflection));
        check("模组配置：有 @ModConfig 就用注解里的分组名（与 MOD_ID 不同也以注解为准，首尾空白去掉）"
                        + "—— 诊断 MOD_ID=notTheSameModId 注解=「  annotatedGroup  」解析出="
                        + cn.gfhnv.game.system.configLoadingSystem.ConfigLoader
                        .resolveModConfigId(new AnnotatedProbeMod("notTheSameModId")),
                cn.gfhnv.game.system.configLoadingSystem.ConfigLoader
                        .resolveModConfigId(new AnnotatedProbeMod("notTheSameModId"))
                        .equals("annotatedGroup"));
        check("模组配置：没有 @ModConfig 的模组照旧用 MOD_ID 当分组名（现有 3 个模组零改动、行为不回归）"
                        + "—— 诊断 解析出=" + cn.gfhnv.game.system.configLoadingSystem.ConfigLoader
                        .resolveModConfigId(new ProbeMod("plainIdMod")),
                cn.gfhnv.game.system.configLoadingSystem.ConfigLoader
                        .resolveModConfigId(new ProbeMod("plainIdMod")).equals("plainIdMod"));
        check("模组配置：注解 id 只有空白时退回 MOD_ID（空白串不算「写了分组名」）"
                        + "—— 诊断 解析出=" + cn.gfhnv.game.system.configLoadingSystem.ConfigLoader
                        .resolveModConfigId(new BlankAnnotatedProbeMod("blankIdMod")),
                cn.gfhnv.game.system.configLoadingSystem.ConfigLoader
                        .resolveModConfigId(new BlankAnnotatedProbeMod("blankIdMod")).equals("blankIdMod"));
        check("模组配置：注解与 MOD_ID 都没有时解析不出分组名（null，交给调用方跳过，不抛异常）",
                cn.gfhnv.game.system.configLoadingSystem.ConfigLoader
                        .resolveModConfigId(new ProbeMod(null)) == null
                        && cn.gfhnv.game.system.configLoadingSystem.ConfigLoader
                        .resolveModConfigId(new ProbeMod("")) == null
                        && cn.gfhnv.game.system.configLoadingSystem.ConfigLoader
                        .resolveModConfigId(null) == null);

        /* ---------- ③ 真读一份文件（走"显式文件"那个入口，不碰真 config/） ---------- */
        java.io.File tempDir = new java.io.File("./out/tmpModConfig");
        deleteDirectory(tempDir);
        if (!tempDir.mkdirs() && !tempDir.isDirectory()) {
            fail("建不了临时目录：" + tempDir.getPath());
            return;
        }
        java.io.File dataDir = new java.io.File(tempDir, "config/data");
        String goodJson = "{\"version\":1,"
                + "\"common\":{\"entities\":{\"game_official_content:playerOne\":"
                + "{\"base\":{\"baseSpeed\":222}}}},"
                + "\"drunkenSword\":{\"initialStacks\":4}}";
        try {
            if (!dataDir.mkdirs() && !dataDir.isDirectory()) {
                fail("建不了临时配置目录：" + dataDir.getPath());
            }
            java.io.File goodFile = new java.io.File(dataDir, "drunkenSword.json");
            java.nio.file.Files.writeString(goodFile.toPath(), goodJson,
                    java.nio.charset.StandardCharsets.UTF_8);
            // 坏 JSON：整份都读不了，但只该打印
            java.io.File brokenFile = new java.io.File(dataDir, "brokenMod.json");
            java.nio.file.Files.writeString(brokenFile.toPath(), "{ 这不是 JSON ",
                    java.nio.charset.StandardCharsets.UTF_8);
            // 空对象：能解析（什么都没有），照样调用 applyConfig
            java.io.File emptyFile = new java.io.File(dataDir, "emptyMod.json");
            java.nio.file.Files.writeString(emptyFile.toPath(), "{}",
                    java.nio.charset.StandardCharsets.UTF_8);

            LivingThing speedProbe = entityTemplateOf("game_official_content:playerOne");
            long speedBefore = speedProbe.getSpeed();

            ProbeMod drunk = new ProbeMod("drunkenSword");
            check("模组配置：有配置文件时读到自己的分组（initialStacks = 4）",
                    cn.gfhnv.game.system.configLoadingSystem.ConfigLoader
                            .loadModData(drunk, goodFile) && drunk.stacks == 4 && drunk.called);
            check("模组配置：common 段当更高优先级的补丁打进了官方模板（速度 "
                            + speedBefore + " → " + speedProbe.getSpeed() + "）",
                    speedProbe.getSpeed() == 222L);
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher
                    .applyJson("{\"entities\":{\"game_official_content:playerOne\":"
                            + "{\"base\":{\"baseSpeed\":" + speedBefore + "}}}}", "自测-还原速度");
            check("模组配置：还原之后速度回到原样（后面的用例看到的是干净注册表）",
                    speedProbe.getSpeed() == speedBefore);

            // D6：common 段支持 entities + skills（规则段做不到，必须显式说清而不是静默）
            java.io.File skillsCommonFile = new java.io.File(dataDir, "skillsCommonMod.json");
            java.nio.file.Files.writeString(skillsCommonFile.toPath(),
                    "{\"version\":1,\"common\":{\"skills\":{\"game_official_content:playerOne#枪射击\":"
                            + "{\"atkMagnification\":4.5}},\"flameReaver\":{\"containerLimit\":8}}}",
                    java.nio.charset.StandardCharsets.UTF_8);
            Skill skillProbe = cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher
                    .find("game_official_content:playerOne#枪射击");
            double atkMagBefore = skillProbe == null ? 0 : skillProbe.getAtkMagnification();
            ProbeMod skillsCommonMod = new ProbeMod("skillsCommonMod");
            check("模组配置（D6）：common 段里的 skills 真的打进技能了（以前只认 entities，写了没反应）",
                    cn.gfhnv.game.system.configLoadingSystem.ConfigLoader
                            .loadModData(skillsCommonMod, skillsCommonFile)
                            && skillProbe != null && skillProbe.getAtkMagnification() == 4.5);
            cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.applyJson(
                    "{\"skills\":{\"game_official_content:playerOne#枪射击\":"
                            + "{\"atkMagnification\":" + atkMagBefore + "}}}", "自测-还原技能倍率");
            check("模组配置（D6）：还原之后技能倍率回到原样（后面的用例看到干净注册表）",
                    skillProbe != null && skillProbe.getAtkMagnification() == atkMagBefore);
            ProbeMod empty = new ProbeMod("emptyMod");
            check("模组配置：配置是空对象时照样调用 applyConfig（文档是空的，不报错）",
                    cn.gfhnv.game.system.configLoadingSystem.ConfigLoader
                            .loadModData(empty, emptyFile) && empty.called && empty.stacks == 2);

            ProbeMod broken = new ProbeMod("brokenMod");
            boolean brokenFileExists = brokenFile.isFile();
            boolean brokenResult = cn.gfhnv.game.system.configLoadingSystem.ConfigLoader
                    .loadModData(broken, brokenFile);
            // 返回的是"文件在不在"，不是"解析成功没有" —— 坏文件照样算"有配置文件"，
            // 只是内容没生效（读不到值就走默认值），并且只打印不抛。
            check("模组配置：整份 JSON 坏掉时只打印、不抛，模组拿到的还是空文档 —— 诊断 文件在="
                            + brokenFileExists + " 返回=" + brokenResult
                            + " 被调用=" + broken.called + " 层数=" + broken.stacks,
                    brokenFileExists && brokenResult && broken.called && broken.stacks == 2);

            ProbeMod missing = new ProbeMod("noSuchModFile");
            check("模组配置：没有配置文件时也调用 applyConfig（模组不用写「有没有文件」的分支）",
                    !cn.gfhnv.game.system.configLoadingSystem.ConfigLoader.loadModData(missing,
                            new java.io.File(dataDir, "noSuchModFile.json"))
                            && missing.called && missing.stacks == 2);

            Mod plainMod = new PlainProbeMod("drunkenSword");
            check("模组配置：没实现 ModDataAware 的模组正常跳过（现有模组零改动）",
                    cn.gfhnv.game.system.configLoadingSystem.ConfigLoader
                            .loadModData(plainMod, goodFile)
                            && !(plainMod instanceof cn.gfhnv.game.mod.config.ModDataAware));

            ProbeMod thrower = new ProbeMod("drunkenSword", true);
            boolean survived;
            try {
                cn.gfhnv.game.system.configLoadingSystem.ConfigLoader
                        .loadModData(thrower, goodFile);
                survived = true;
            } catch (Throwable t) {
                survived = false;
            }
            check("模组配置：applyConfig 抛异常（含 Error）只跳过它自己，不会带崩加载链"
                    + "（文档 §8.3 第 10 条要堵的就是这条路）", survived && thrower.called);

            /* ---- 注解 > MOD_ID：真读文件（分组名、文件名都跟注解走） ---- */
            java.io.File annotatedFile = new java.io.File(dataDir, "annotatedGroup.json");
            java.nio.file.Files.writeString(annotatedFile.toPath(),
                    "{\"annotatedGroup\":{\"initialStacks\":4},\"notTheSameModId\":{\"initialStacks\":9}}",
                    java.nio.charset.StandardCharsets.UTF_8);
            ProbeMod annotated = new AnnotatedProbeMod("notTheSameModId");
            check("模组配置：@ModConfig 的分组名与 MOD_ID 不同时，读的是注解那一段"
                            + "（initialStacks = 4，不是 MOD_ID 那一段的 9）",
                    cn.gfhnv.game.system.configLoadingSystem.ConfigLoader
                            .loadModData(annotated, annotatedFile)
                            && annotated.called && annotated.stacks == 4);

            java.io.File blankFile = new java.io.File(dataDir, "blankIdMod.json");
            java.nio.file.Files.writeString(blankFile.toPath(),
                    "{\"blankIdMod\":{\"initialStacks\":5}}", java.nio.charset.StandardCharsets.UTF_8);
            ProbeMod blankAnnotated = new BlankAnnotatedProbeMod("blankIdMod");
            check("模组配置：注解 id 是空白串时读的是 MOD_ID 那一段（退回现有行为，initialStacks = 5）",
                    cn.gfhnv.game.system.configLoadingSystem.ConfigLoader
                            .loadModData(blankAnnotated, blankFile)
                            && blankAnnotated.called && blankAnnotated.stacks == 5);

            java.io.File plainFile = new java.io.File(dataDir, "plainIdMod.json");
            java.nio.file.Files.writeString(plainFile.toPath(),
                    "{\"plainIdMod\":{\"initialStacks\":6}}", java.nio.charset.StandardCharsets.UTF_8);
            ProbeMod noAnnotation = new ProbeMod("plainIdMod");
            check("模组配置：不带注解的模组照旧按 MOD_ID 找到自己那一段（initialStacks = 6，不回归）",
                    cn.gfhnv.game.system.configLoadingSystem.ConfigLoader
                            .loadModData(noAnnotation, plainFile)
                            && noAnnotation.called && noAnnotation.stacks == 6);

            /* ---- 游戏真正走的那条入口：文件名 = 注解里的分组名 ---- */
            // loadModData(Mod) 读的是 ./config/data/<分组名>.json（相对工作目录，JVM 起来后改不了），
            // 而自测不许往真 config/ 里写文件。所以借"分组名可以带相对路径"这一点，让
            // ./config/data/../../out/tmpModConfig/annotatedGroup.json 字面上正好落在自测自己的临时目录里：
            // 只有"文件名跟注解走"这个实现才读得到它（跟 MOD_ID 走的话那个文件根本不存在）。
            java.io.File defaultEntryFile = new java.io.File(tempDir, "annotatedGroup.json");
            java.nio.file.Files.writeString(defaultEntryFile.toPath(),
                    "{\"../../out/tmpModConfig/annotatedGroup\":{\"initialStacks\":7}}",
                    java.nio.charset.StandardCharsets.UTF_8);
            ProbeMod defaultEntry = new AnnotatedPathProbeMod("自测:这个模组id绝对没有配置文件");
            check("模组配置：默认入口 loadModData(mod) 的文件名与分组名都跟注解走"
                            + "（注解 id 写成相对路径，落在 out/ 临时目录里，真 config/ 全程只读）",
                    cn.gfhnv.game.system.configLoadingSystem.ConfigLoader.loadModData(defaultEntry)
                            && defaultEntry.called && defaultEntry.stacks == 7);

            /* ---- 两者都没有：跳过这个模组，不抛、不崩 ---- */
            ProbeMod noId = new ProbeMod(null);
            boolean noIdSurvived;
            boolean noIdResult;
            try {
                noIdResult = cn.gfhnv.game.system.configLoadingSystem.ConfigLoader
                        .loadModData(noId, goodFile);
                noIdSurvived = true;
            } catch (Throwable t) {
                noIdResult = true;
                noIdSurvived = false;
            }
            check("模组配置：注解与 MOD_ID 都没有时跳过这个模组（返回 false、不调用 applyConfig、不抛异常）",
                    noIdSurvived && !noIdResult && !noId.called);
            check("模组配置：两者都没有时默认入口也照常返回（打印一行提示，不抛异常、不崩）",
                    !cn.gfhnv.game.system.configLoadingSystem.ConfigLoader
                            .loadModData(new ProbeMod("")));

            // 真路径的形状：默认入口读的是 ./config/data/<配置分组名>.json（没注解时就是模组 id）
            check("模组配置：默认入口按「配置文件名 = 配置分组名」找文件（真 config/ 只读）",
                    !cn.gfhnv.game.system.configLoadingSystem.ConfigLoader
                            .loadModData(new ProbeMod("这个模组一定没有配置文件")));
        } finally {
            deleteDirectory(tempDir);
        }
        check("模组配置：临时目录用完就删干净（真 config/ 目录全程只读）", !tempDir.exists());
    }

    /**
     * 跑本主题的全部用例（顺序 = 下面列的顺序）。
     */
    public static void runAll() throws Exception {
        testConfigOutputSilence();
        testModConfig();
    }
}
