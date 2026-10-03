package cn.gfhnv.debug_tools;

import cn.gfhnv.game.damage.DamageCalculate;
import cn.gfhnv.game.data.*;
import cn.gfhnv.game.entity.DamageReductionSource;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entityController.FixOrderController;
import cn.gfhnv.game.entityController.UniversalController;
import cn.gfhnv.game.event.DamageEvent;
import cn.gfhnv.game.interfaces.IModifyDamage;
import cn.gfhnv.game.interfaces.IModifyIgnitionMax;
import cn.gfhnv.game.inventory.Slot;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.mod.Mod;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.Taunt;
import cn.gfhnv.game.officialStuff.customEntity.monsters.CommonInsect;
import cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver;
import cn.gfhnv.game.officialStuff.customEntity.players.ActorLiXiaoYan;
import cn.gfhnv.game.officialStuff.customEntity.players.Phainon;
import cn.gfhnv.game.officialStuff.customEntity.players.PlayerOne;
import cn.gfhnv.game.officialStuff.customEntity.summons.BrokenContainer;
import cn.gfhnv.game.officialStuff.customItem.ANiceSword;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.command.*;
import cn.gfhnv.game.system.configLoadingSystem.DataKeys;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.fight.TargetStrategies;
import cn.gfhnv.game.system.fight.TurnManager;
import cn.gfhnv.game.world.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 命令系统自测程序（不需要手动玩游戏就能验证命令解析是否正常）。
 * <p>
 * 运行方式（在项目根目录）：
 * <pre>
 * javac -encoding UTF-8 -d out/cmdtest -classpath lib/json-20231013.jar (Get-ChildItem -Recurse src -Filter *.java)
 * java  -Dfile.encoding=UTF-8 -cp "out/cmdtest;lib/json-20231013.jar" cn.gfhnv.debug_tools.TestCommandSystem
 * </pre>
 * 或者直接把本类当成一个有 {@code main} 的入口，在 IDEA 里运行。
 * <p>
 * 它会：
 * <ol>
 *     <li>注册官方命令；</li>
 *     <li>造两个假生物放进一个假战斗（<b>不进游戏</b>，不碰回合系统）；</li>
 *     <li>跑一批命令，逐条打印「成功/失败 + 影响对象数 + 错误信息」；</li>
 *     <li>单独测一遍 {@link StringReader} 与 {@link EntitySelector} 的解析；</li>
 *     <li>最后打印通过/失败统计，任何一条不符合预期都返回非 0 退出码。</li>
 * </ol>
 * <p>
 * 说明：本类会真的改动它自己造出来的那两个生物，但不会写入 {@code World.things}
 * 之外的任何全局状态，也不会启动战斗循环，因此可以反复运行。
 *
 * @author AI（DeepSeek）生成
 */
public class TestCommandSystem {

    /**
     * 角色 / 召唤物类<b>自己的</b>状态键（不是 {@code LivingThing} 的通用属性，也不是配置该管的东西）。
     * <p>
     * 它们会出现在 {@code /data} 里，所以"dump 出来的键都能配置或在只读清单里"那条断言
     * 必须把它们排除；这里逐条列出来，是为了让"新增了一个子类状态键"这件事被人看见
     * （而不是悄悄混进"已知的"那一堆里）。
     * <p>
     * <b>2026-10-03 起它是"两个清单的并集"</b>（{@code DataKeys.CLASS_STATE} =
     * {@code CLASS_CONFIG} 已放行 + {@code CLASS_RUNTIME} 继续挡）：这一份口径只服务"dump 契约"
     * —— 配置层放不放行由那两个清单分别负责，别再用这一份去判断能不能配。
     */
    private static final java.util.Set<String> SUBCLASS_STATE_KEYS =
            cn.gfhnv.game.system.configLoadingSystem.DataKeys.CLASS_STATE;
    /**
     * <b>第 1 步的「现形清单」</b>：反射面（只算标量）里<b>手写表没有、也还没写
     * {@code @NoConfig}</b> 的那些键。
     * <p>
     * 口径（{@code 2026-10-03} 实测，{@code playerOne} 模板）：反射面 53 个标量键 − 手写表 31 个键
     * （含 {@code manaGrow} 块与 {@code inventorySlots} 这两个"表里有、反射面没有"的项）
     * = <b>29 个表里有的</b>，剩下的<b>这 24 个</b>就是"多出来的"。
     * <p>
     * 它从一份"人看的清单"变成了<b>断言</b>：第 ⑤ 组守卫要求这个集合与
     * {@code 反射面 − 手写表 − @NoConfig} <b>逐字相等</b> —— 多一个少一个都变红。
     * 于是"新加了一个标量字段"或"表里删了一行"这两件事都不可能悄悄发生。
     * <p>
     * 分类结果（第 2 步，逐条给了理由）：<b>没有一个该开放</b> ——
     * 10 个五元素 {@code *Penetration} / {@code *DamageEnhance}（临时属性，副本都不带）、
     * 12 个 {@code *Enhance*}（效果与技能在战斗中加减的运行时加成，战斗结束清零）、
     * {@code extraDamage}（死字段）、{@code individualMultipleArea}（角色构造器算出来的派生面板倍率）。
     * 所以第 2 步给这 24 个逐个写 {@code @NoConfig}，写完这条断言就翻成
     * "这 24 个<b>全部</b>在 {@code @NoConfig} 清单里"（清单继续留着当分类记录）。
     */
    private static final java.util.Set<String> REFLECTION_ONLY_SCALAR_KEYS =
            new java.util.TreeSet<>(Arrays.asList(
                    // ① 五元素穿透 / 五元素增伤（10 个）：临时属性，AttributeProfile#copyFrom 刻意不带
                    "metalPenetration", "woodPenetration", "waterPenetration",
                    "firePenetration", "dirtPenetration",
                    "metalDamageEnhance", "woodDamageEnhance", "waterDamageEnhance",
                    "fireDamageEnhance", "dirtDamageEnhance",
                    // ② *Enhance* 运行时加成（12 个）：效果与技能"进来加、走时减"，战斗结束清零
                    "attackEnhancePercent", "attackEnhanceAmount",
                    "defenceEnhancePercent", "defenceEnhanceAmount",
                    "speedEnhancePercent", "speedEnhanceAmount",
                    "hpEnhancePercent", "hpEnhanceAmount",
                    "criticalDMGEnhancePercent", "criticalDMGEnhanceAmount",
                    "criticalRateEnhancePercent", "criticalRateEnhanceAmount",
                    // ③ 死字段：全项目零写入点（活的那套是 Skill#extraDamage）
                    "extraDamage",
                    // ④ 派生面板倍率：由角色构造器算（清了会永久削弱该角色）
                    "individualMultipleArea"));
    /**
     * 断言失败的条数。
     */
    private static int failures = 0;
    /**
     * 断言成功的条数。
     */
    private static int passes = 0;

    /* ------------------------------------------------------------------
     * 1. StringReader
     * ------------------------------------------------------------------ */

    /**
     * 测试入口。
     *
     * @param args 忽略
     */
    public static void main(String[] args) throws Exception {
        useUtf8Output();
        System.out.println("========== 命令系统自测开始 ==========");

        testStringReader();
        testEntitySelectorSyntax();
        testRegistrationAndExecution();
        testFixOrderController();
        testDamageReduction();
        testFlameReaverFactions();
        testFlameReaverPhaseTwo();
        testLivingThingAttributeContract();
        testActorLiXiaoYan();
        testFollowActor();
        testDataCommand();
        testNestedEntityReference();
        testDataModify();
        testDataFiltersAndStorage();
        testConfigDefaultsAndPatch();
        testClassStateConfigKeys();
        testModContentIsNotInGameConfig();
        testReflectionDrivenConfig();
        testSkillDataPatch();
        testSkillCoefficients();
        testSkillRegistry();
        testSkillIdCoverage();
        testCombatLogColors();
        testGameRules();
        testHpMaxDualEntry();
        testHpMaxNoticeFalsePositive();
        testConfigOutputSilence();
        testModConfig();
        testSummonCommand();

        System.out.println("========== 自测结束：通过 " + passes + " 条，失败 " + failures + " 条 ==========");
        if (failures > 0) {
            System.exit(1);
        }
    }

    /* ------------------------------------------------------------------
     * 2. 实体选择器语法
     * ------------------------------------------------------------------ */

    /**
     * 把标准输出切成 UTF-8。
     * <p>
     * Windows 的 PowerShell 5 控制台默认是 GBK（代码页 936），直接打印中文会乱码。
     * 这里不改系统设置，只在进程内换掉 {@code System.out} 的编码。
     * 想彻底避免乱码，也可以在运行前执行 {@code chcp 65001}。
     */
    private static void useUtf8Output() {
        try {
            java.io.PrintStream utf8 = new java.io.PrintStream(
                    new java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8");
            System.setOut(utf8);
        } catch (java.io.UnsupportedEncodingException e) {
            System.out.println("[提示] 无法把输出切换为 UTF-8，中文可能显示为乱码：" + e.getMessage());
        }
    }

    /* ------------------------------------------------------------------
     * 3. 注册 + 执行
     * ------------------------------------------------------------------ */

    /**
     * 测试 {@link StringReader} 的读取行为。
     */
    private static void testStringReader() {
        section("StringReader 基础读取");
        try {
            // 注意：readWord() 只读到「词的末尾」，不会吃掉后面的空白。
            // 所以 "kill   @s  extra" 读完 @s 之后，getRemaining() 是「  extra」（带前导空格）。
            StringReader reader = new StringReader("kill   @s  extra");
            check("readWord 读到 kill", "kill".equals(reader.readWord()));
            check("readWord 自动跳过多余空白", "@s".equals(reader.readWord()));
            check("remain 为 extra（前导空格未消耗，比较前要 trim）",
                    "extra".equals(reader.getRemaining().trim()));

            // 想看「读完就没内容了」，用一段末尾没有空白的输入
            StringReader tail = new StringReader("kill @s");
            tail.readWord();
            tail.readWord();
            check("读完最后一个词（光标在末尾）canRead 为 false", !tail.canRead());

            // 尾部有空白时，canRead() 仍为 true —— 这是 Brigadier 的既定语义
            StringReader trailing = new StringReader("kill @s ");
            trailing.readWord();
            check("读完 kill 后还剩内容（尾部空白仍可读）", trailing.canRead());
            trailing.readWord();
            trailing.skipWhitespace();
            check("skipWhitespace 之后 canRead 为 false", !trailing.canRead());

            StringReader quoted = new StringReader("say \"hello world\" tail");
            check("readWord 读到 say", "say".equals(quoted.readWord()));
            check("readQuotedString 保留空格", "hello world".equals(quoted.readQuotedString()));
            check("引号后还能读到 tail", "tail".equals(quoted.readWord()));

            StringReader greedy = new StringReader("say  a b c  ");
            greedy.readWord();
            check("readString 吃掉整行", "a b c".equals(greedy.readString()));

            StringReader empty = new StringReader("   ");
            check("空输入 canRead 为 false", !empty.canRead());
        } catch (Exception e) {
            fail("StringReader 抛出异常：" + e);
        }
    }

    /* ------------------------------------------------------------------
     * 4. 固定顺序 AI / 目标策略 / 嘲讽
     * ------------------------------------------------------------------ */

    /**
     * 测试 {@link EntitySelector} 的语法解析（不求解实体）。
     */
    private static void testEntitySelectorSyntax() {
        section("实体选择器语法");
        try {
            EntitySelector selector = EntitySelector.fromString("@e[type=CommonInsect,limit=2,sort=nearest]");
            check("类型筛选解析正确", "CommonInsect".equals(selector.getTypeFilter()));
            check("limit 解析正确", selector.getLimit() == 2);
            check("sort 解析正确", selector.getSort() == EntitySelector.SortMode.NEAREST);

            EntitySelector self = EntitySelector.fromString("@s");
            check("@s 类型正确", self.getKind() == EntitySelector.SelectorKind.SELF);

            expectSyntaxError("@e[bad=1] 应当报错", () -> EntitySelector.fromString("@e[bad=1]"));
            expectSyntaxError("@x 应当报错", () -> EntitySelector.fromString("@x"));
            expectSyntaxError("不以 @ 开头应当报错", () -> EntitySelector.fromString("kill"));
        } catch (Exception e) {
            fail("选择器语法测试抛出异常：" + e);
        }
    }

    /* ------------------------------------------------------------------
     * 5. 减伤（乘算叠加）
     * ------------------------------------------------------------------ */

    /**
     * 注册官方命令，造一场假战斗，然后跑一批命令。
     */
    private static void testRegistrationAndExecution() {
        section("命令注册与执行");

        // 效果注册表：真实游戏里由 GameMain 把 OfficialGameContent 加进 World、
        // 再在 GameStartEvent 后 registerItself() 写进 World.getEffectList()。
        // 自测不启动游戏，所以这里手动走一遍同样的流程（放在 initialize() 之前，
        // 这样注册命令时打出的「可用效果」日志里就已经有内容了）。
        // 只需要效果表被填满：选择器用的是 World.getThings()（运行时对象），不受这些模板影响。
        // 【模组表也要加】——/give 与 /effect 的"短名只解析官方内容"靠模组表判断谁注册的，
        // 不加的话官方内容会被当成"没有模组认领"，规则就退化成全都放行。
        cn.gfhnv.game.officialStuff.OfficialGameContent officialContent =
                new cn.gfhnv.game.officialStuff.OfficialGameContent();
        World.addMod(officialContent);
        officialContent.registerItself();

        // 只初始化命令系统（不调用 GameMain.gameInitialize()，避免加载模组与配置）
        CommandManager.initialize();
        check("已注册 kill", CommandManager.getRegisteredCommandNames().contains("kill"));
        check("已注册 list", CommandManager.getRegisteredCommandNames().contains("list"));
        check("已注册 hurt", CommandManager.getRegisteredCommandNames().contains("hurt"));
        check("已注册 endfight", CommandManager.getRegisteredCommandNames().contains("endfight"));
        check("已注册 help", CommandManager.getRegisteredCommandNames().contains("help"));

        // 诊断输出：把命令树打印出来。命令解析出问题时，这一小段能立刻定位到
        // 「节点没挂上」「名字不对」「executor 没绑上」中的哪一种。
        System.out.println("  --- 命令树 ---");
        System.out.println("  根节点 " + describeNode(CommandManager.getDispatcher().getRoot())
                + " 根子节点=" + CommandManager.getDispatcher().getRoot().getChildrenNames());
        for (String name : CommandManager.getRegisteredCommandNames()) {
            cn.gfhnv.game.system.command.CommandNode node = CommandManager.getDispatcher().getCommandNode(name);
            System.out.println("  [" + name + "] 自身=" + describeNode(node));
            if (node != null) {
                for (cn.gfhnv.game.system.command.CommandNode child : node.getChildren()) {
                    System.out.println("      └─ " + describeNode(child));
                }
            }
        }
        System.out.println("  --- 命令树结束 ---");

        // 造两个生物并放进一个假战斗
        // 注意：这里直接 new + copy()，绕过了模组注册，所以 id 要自己补上
        // （真实游戏里生物来自 World 注册表，官方内容会加 game_official_content: 前缀）。
        LivingThing hero = new PlayerOne(125).copy();
        LivingThing bug = new CommonInsect(150L).copy();
        hero.setId("game_official_content:playerOne");
        bug.setId("game_official_content:commonInsect");
        hero.setHp(hero.getHpMax());
        bug.setHp(bug.getHpMax());

        List<LivingThing> fighters = new ArrayList<>();
        fighters.add(hero);
        List<LivingThing> enemies = new ArrayList<>();
        enemies.add(bug);
        Fight fight = new Fight(enemies, new ArrayList<>(), fighters);

        World.addThing(hero);
        World.addThing(bug);
        CommandManager.setPlayer(hero);
        CommandManager.setCurrentFight(fight);

        // 诊断：把「解析」与「取参数」拆开验证，方便定位到底哪一步丢了参数
        diagnoseParse("kill @e[type=CommonInsect]");
        diagnoseParse("hurt @s 100");
        diagnoseParse("list @e");
        // 诊断：不经过官方命令，直接测构建器建树（最小复现）
        diagnoseBuilder();

        run("kill @e[type=CommonInsect]", true);
        check("虫子被击杀（HP=0）", bug.getHp() == 0);
        check("虫子已死亡", !bug.isAlive());

        run("hurt @s 100", true);
        check("自己掉了血", hero.getHp() < hero.getHpMax());

        long before = hero.getHp();
        run("hurt @s -50", true);
        check("负数表示回血", hero.getHp() > before);

        run("list", true);
        run("list @e", true);
        run("list @e[name=*虫*]", true);
        run("help", true);
        run("help kill", true);

        // 上面已经把自己打伤、把虫子打死过，这里把虫子「复活」一下：
        // 下面的 @e[type=CommonInsect] 用例要求它已经在战斗里存活，
        // 否则选择器会因为「筛不出任何生物」而报错（那是另一条用例在测的东西）。
        bug.setHp(bug.getHpMax());
        bug.setAlive(true);
        check("虫子已复活", bug.isAlive());

        // 预期失败的用例
        run("nosuchcommand", false);
        run("kill", false);
        run("kill @e[bad=1]", false);
        run("hurt @s abc", false);
        run("hurt @s 1 2", false);
        run("kill @e[type=根本没有这个类型]", false);

        /* 参数节点的报错不能被吞成笼统的「无法继续解析」。
         * 典型场景：选择器少写了 @（用户实测踩过），这时选择器自己那句
         * 「实体选择器必须以 @ 开头」才是最该看到的。 */
        String missingAt = errorOf("data modify entity s hp set 1");
        check("少写 @ 的选择器：报的是选择器自己的原因，而不是「无法继续解析」",
                missingAt.contains("@") && !missingAt.contains("无法继续解析"));
        // 反过来：输入已经读完时仍然要说「命令不完整 + 完整用法」，不能被参数报错顶掉
        String giveMissingItem = errorOf("give @s");
        check("命令不完整：用法提示没有被参数报错顶掉",
                giveMissingItem.contains("命令不完整")
                        && giveMissingItem.contains("/give <目标> <物品>"));

        check("前缀 # 与 / 等价（不执行也不崩）", CommandManager.isCommand("#help"));
        check("普通输入不会被当成命令", !CommandManager.isCommand("yes"));
        check("process 对普通输入返回 false", !CommandManager.process("yes"));
        check("process 对 #help 返回 true", CommandManager.process("#help"));

        // 补全：命令名前缀补全 + 参数位置提示（尾随空白表示「准备输入下一个词」）
        List<String> suggestions = CommandManager.getDispatcher().getCompletionSuggestions("k");
        check("补全 k 得到 kill", suggestions.contains("kill"));
        List<String> afterKill = CommandManager.getDispatcher().getCompletionSuggestions("kill ");
        check("kill 后面的补全给出参数提示", afterKill.contains("<目标>"));
        List<String> withoutSpace = CommandManager.getDispatcher().getCompletionSuggestions("kill");
        check("没有尾随空白时不提示下一步（与 MC 一致）", withoutSpace.isEmpty());
        List<String> afterHurtTarget = CommandManager.getDispatcher()
                .getCompletionSuggestions("hurt @s ");
        check("hurt 选完目标后提示第二个参数", afterHurtTarget.contains("<数值>"));

        // 编码自检（Windows 控制台默认 GBK，中文参数值可能在进入程序前变成 ???）
        System.out.println("      " + CommandManager.describeEncoding());

        // 类型筛选的三种写法都必须能用：ASCII 简单类名 + 中文显示名 + id
        // 注意：选择器「一个都没选中」时会抛异常（不是返回空列表），
        // 所以这段必须在虫子还活着的时候跑（上面已经把它打死过一次，这里先复活）。
        bug.setHp(bug.getHpMax());
        bug.setAlive(true);
        cn.gfhnv.game.system.command.CommandSource probe =
                new cn.gfhnv.game.system.command.CommandSource(CommandManager.getPlayer());
        check("kill 节点仍然存在（构建器未回归）",
                CommandManager.getDispatcher().getCommandNode("kill") != null);
        try {
            // fromString 会抛 CommandSyntaxException（受检异常），必须放在 try 里
            cn.gfhnv.game.system.command.EntitySelector byClass =
                    cn.gfhnv.game.system.command.EntitySelector.fromString("@e[type=CommonInsect]");
            cn.gfhnv.game.system.command.EntitySelector byName =
                    cn.gfhnv.game.system.command.EntitySelector.fromString("@e[name=普通虫子]");
            cn.gfhnv.game.system.command.EntitySelector byId =
                    cn.gfhnv.game.system.command.EntitySelector.fromString(
                            "@e[type=game_official_content:commonInsect]");
            byClass.resolve(probe.getSelectorContext());
            byName.resolve(probe.getSelectorContext());
            byId.resolve(probe.getSelectorContext());
            check("筛选：ASCII 类名 @e[type=CommonInsect] 能选中虫子", !byClass.isEmpty());
            check("筛选：中文名 @e[name=普通虫子] 能选中虫子", !byName.isEmpty());
            check("筛选：id @e[type=game_official_content:commonInsect] 能选中虫子", !byId.isEmpty());
        } catch (Exception e) {
            fail("类型/名字筛选解析失败：" + e.getMessage());
        }

        // ---- 效果命令：/effect <目标> add|remove|list ----
        check("已注册 effect", CommandManager.getRegisteredCommandNames().contains("effect"));
        run("effect @s add frozen", true);
        check("冰冻效果已挂上", hasEffect(hero, "frozenEffect"));
        run("effect @s add damageEnhanceEffect 2 5", true);
        check("增伤效果已挂上", hasEffect(hero, "damageEnhanceEffect"));
        check("增伤效果等级为 2", effectLevelOf(hero, "damageEnhanceEffect") == 2);
        run("effect @s list", true);
        run("effect @s remove frozenEffect", true);
        check("冰冻效果已移除", !hasEffect(hero, "frozenEffect"));
        run("effect @s remove all", true);
        check("清空后身上没有效果", hero.getEntityEffectList().isEmpty());

        // 角色专属 / 不存在的效果都必须被拒绝
        run("effect @s add memorizedHp", false);
        check("角色专属效果没有被挂上", !hasEffect(hero, "memorizedHp"));
        run("effect @s add noSuchEffect", false);
        run("effect @s add", false);
        run("effect @s remove", false);
        run("effect @s bad", false);

        // ---- 效果名规则：与 /give 同一套（短名只认官方，模组效果必须写完整 id）----
        // 用自测专用、且【各自一个类】的探针效果，理由有两条：
        //   ① 它们和官方效果不是同一个类，短名撞车时才能验证"官方优先、且不算歧义"；
        //   ② World#fullIdOf 是按【类】查表补全 id 的，同一个类注册两条模板会互相盖掉 id。
        Mod effectProbeMod = new Mod("effectTestMod") {
        };
        ProbeModOnlyEffect modOnlyProbe = new ProbeModOnlyEffect();
        effectProbeMod.addEffect(modOnlyProbe);
        ProbeModSameNameEffect sameNameProbe = new ProbeModSameNameEffect();
        effectProbeMod.addEffect(sameNameProbe);
        World.addMod(effectProbeMod);
        effectProbeMod.registerItself();
        try {
            run("effect @s remove all", true);
            run("effect @s add frozenEffect", true);
            check("effect：短名只解析官方内容（模组同名效果不参与，因此不算歧义）",
                    hasEffectExactId(hero, "game_official_content:frozenEffect")
                            && !hasEffectExactId(hero, "effectTestMod:frozenEffect"));

            run("effect @s remove all", true);
            run("effect @s add effectTestMod:modOnlyEffect", true);
            check("effect：模组效果写完整 id 可以施加",
                    hasEffectExactId(hero, "effectTestMod:modOnlyEffect"));

            run("effect @s remove all", true);
            CommandResult effectShortName = CommandManager.executeResult("effect @s add modOnlyEffect");
            check("effect：模组效果写短名会被拒绝", !effectShortName.isSuccess());
            check("effect：拒绝时提示该写的完整 id", effectShortName.getError() != null
                    && effectShortName.getError().getMessage() != null
                    && effectShortName.getError().getMessage().contains("effectTestMod:modOnlyEffect"));
            run("effect @s remove all", true);
        } finally {
            World.removeEffect(modOnlyProbe);
            World.removeEffect(sameNameProbe);
            World.removeMod(effectProbeMod);
        }

        // 构造函数参数语法：效果名(参数,...)
        run("effect @s add CriticalDMGEnhanceEffect(1,5)", true);
        check("按构造函数参数创建成功", hasEffect(hero, "criticalDMGEnhanceEffect"));
        run("effect @s add frozen(2)", true);
        check("单参数构造函数（frozen(2)）可用", hasEffect(hero, "frozenEffect"));
        run("effect @s remove all", true);
        run("effect @s add frozen(1,2,3)", false);
        run("effect @s add frozen(abc)", false);
        run("effect @s add frozen(1", false);

        // ---- 数字的单位：2 个参数一定是百分比，固定值必须写满 3 个参数 ----
        run("effect @s remove all", true);
        long fixedBefore = hero.getAttackEnhanceAmount();
        double percentBefore = hero.getAttackEnhancePercent();

        run("effect @s add AttackEnhance(1,2)", true);
        check("AttackEnhance(1,2) 的 1 是百分比（percent +1.0）",
                Math.abs(hero.getAttackEnhancePercent() - (percentBefore + 1.0)) < 1e-9);
        check("AttackEnhance(1,2) 不会动固定值", hero.getAttackEnhanceAmount() == fixedBefore);
        run("effect @s remove all", true);
        check("remove all 会把改过的属性一起还回去",
                hero.getAttackEnhanceAmount() == fixedBefore
                        && Math.abs(hero.getAttackEnhancePercent() - percentBefore) < 1e-9);

        run("effect @s add AttackEnhance(0,2,2)", true);
        check("AttackEnhance(0,2,2) 的 2 是固定值（amount +2）",
                hero.getAttackEnhanceAmount() == fixedBefore + 2);
        check("AttackEnhance(0,2,2) 不会动百分比",
                Math.abs(hero.getAttackEnhancePercent() - percentBefore) < 1e-9);
        run("effect @s remove all", true);

        run("effect @s add AttackEnhance(0.5,3,2)", true);
        check("AttackEnhance(0.5,3,2) 两个数值都生效",
                Math.abs(hero.getAttackEnhancePercent() - (percentBefore + 0.5)) < 1e-9
                        && hero.getAttackEnhanceAmount() == fixedBefore + 3);
        run("effect @s remove all", true);

        // 守护：5 个「百分比 / 固定值」效果都只允许有 1 个双参数 + 1 个三参数构造函数
        Class<?>[] percentStyle = {
                cn.gfhnv.game.officialStuff.customEffect.universalEffects.AttackEnhance.class,
                cn.gfhnv.game.officialStuff.customEffect.universalEffects.HpEnhanceEffect.class,
                cn.gfhnv.game.officialStuff.customEffect.universalEffects.SpeedEnhanceEffect.class,
                cn.gfhnv.game.officialStuff.customEffect.universalEffects.CriticalRateEnhanceEffect.class,
                cn.gfhnv.game.officialStuff.customEffect.universalEffects.CriticalDMGEnhanceEffect.class
        };
        StringBuilder confused = new StringBuilder();
        for (Class<?> type : percentStyle) {
            int two = countConstructors(type, 2);
            int three = countConstructors(type, 3);
            if (two != 1 || three != 1) {
                if (confused.length() > 0) {
                    confused.append('、');
                }
                confused.append(type.getSimpleName()).append("(2参数=").append(two)
                        .append(",3参数=").append(three).append(')');
            }
        }
        check("5 个效果各只有 1 个双参数 + 1 个三参数构造函数"
                        + (confused.length() == 0 ? "" : "（异常：" + confused + "）"),
                confused.length() == 0);

        // isUniversal 判定基于标签（与 isInfinity 一致）
        check("通用效果带 UNIVERSAL 标签",
                templateOf("frozenEffect") != null && templateOf("frozenEffect").isUniversal()
                        && templateOf("frozenEffect").getEffectTagsList()
                        .contains(cn.gfhnv.game.effect.EffectTags.UNIVERSAL));
        check("角色专属效果不带 UNIVERSAL 标签",
                templateOf("memorizedHp") != null && !templateOf("memorizedHp").isUniversal());

        // 复制出来的副本必须还是通用的：实体复制走的是 effect.copy()，
        // 拷贝构造器漏掉 UNIVERSAL 的话，副本在运行时会被当成「非通用」。
        int universalCount = 0;
        StringBuilder lostTag = new StringBuilder();
        for (cn.gfhnv.game.effect.Effect template : cn.gfhnv.game.world.World.getEffectList()) {
            if (template == null || !template.isUniversal()) {
                continue;
            }
            universalCount++;
            if (!template.copy().isUniversal()) {
                if (lostTag.length() > 0) {
                    lostTag.append("、");
                }
                lostTag.append(template.getClass().getSimpleName());
            }
        }
        check("通用效果共 11 种（实际 " + universalCount + " 种）", universalCount == 11);
        check("每种通用效果的 copy() 副本都保留 UNIVERSAL 标签"
                        + (lostTag.length() == 0 ? "" : "（丢失：" + lostTag + "）"),
                universalCount > 0 && lostTag.length() == 0);
        check("副本的 id 与模板一致",
                templateOf("frozenEffect") != null
                        && templateOf("frozenEffect").copy().getID()
                        .equalsIgnoreCase(templateOf("frozenEffect").getID()));

        // ---- /execute as <目标> run <命令> ----
        check("已注册 execute", CommandManager.getRegisteredCommandNames().contains("execute"));

        long bugHpBefore = bug.getHp();
        long heroHpBefore = hero.getHp();

        // 内层 @s 必须变成「被指定的目标」，而不是原来的玩家
        run("execute as @e[type=CommonInsect] run hurt @s 10", true);
        check("execute as：内层 @s 指向虫子（虫子掉 10 血）", bug.getHp() == bugHpBefore - 10);
        check("execute as：玩家一没有掉血", hero.getHp() == heroHpBefore);

        // 外层 @s 仍然是玩家，内层选择器照常工作
        run("execute as @s run hurt @e[type=CommonInsect] 5", true);
        check("execute as：外层 @s 仍是玩家一（由玩家一发出伤害）", bug.getHp() == bugHpBefore - 15);

        // 嵌套：里层 @s 会变成虫子
        run("execute as @e[type=CommonInsect] run execute as @s run hurt @s 1", true);
        check("execute 嵌套：最里层 @s 仍然是虫子", bug.getHp() == bugHpBefore - 16);

        // 只读的内层命令也能跑（@s 换成虫子，输出的是虫子的状态）
        run("execute as @e[type=CommonInsect] run list", true);

        // 预期失败：写错/写漏/选不中/内层命令不存在
        run("execute as @s", false);
        run("execute as @s run", false);
        run("execute run list", false);
        run("execute as @e[type=根本没有这个类型] run list", false);
        run("execute as @s run nosuchcommand", false);

        // 嵌套上限：9 层 execute 必须被挡住（不然会一路递归到 StackOverflowError）
        StringBuilder tooDeep = new StringBuilder("execute as @s run hurt @s 1");
        for (int i = 0; i < 8; i++) {
            tooDeep.insert(0, "execute as @s run ");
        }
        run(tooDeep.toString(), false);
        check("execute 嵌套上限拦住了过深调用（虫子没被多打）", bug.getHp() == bugHpBefore - 16);

        // ---- id 归一：运行时对象的 id 必须和注册表一样是完整 id ----
        // 效果：技能/命令里 new 出来的效果，挂上身之后应当带 game_official_content: 前缀
        // （靠 LivingThing.addEffect 里的 World.applyRegisteredId）
        run("effect @s add frozen", true);
        check("运行时效果的 id 被补成完整 id",
                effectOf(hero, "game_official_content:frozenEffect") != null);
        run("effect @s remove all", true);
        check("清空后效果列表为空", hero.getEntityEffectList().isEmpty());

        // 实体：技能里直接 new 出来的生物（例如 Boss 分裂），进战斗时补全 id
        LivingThing summoned = new CommonInsect(100L);
        check("刚 new 出来的生物还是短 id（对照）", "commonInsect".equals(summoned.getId()));
        fight.addEnemy(summoned);
        check("进战斗后实体的 id 被补成完整 id",
                "game_official_content:commonInsect".equals(summoned.getId()));

        // 物品：注册表模板 copy() 之后必须保住完整 id（靠 ANiceSword 的拷贝构造器）
        cn.gfhnv.game.item.Item swordTemplate = null;
        for (cn.gfhnv.game.item.Item item : World.getItemList()) {
            if (item != null && item.getId() != null && item.getId().indexOf(':') >= 0) {
                swordTemplate = item;
                break;
            }
        }
        check("物品注册表里有带前缀的模板", swordTemplate != null);
        check("物品：模板 copy() 之后仍是完整 id",
                swordTemplate != null && swordTemplate.getId().equals(swordTemplate.copy().getId()));

        // ---- /give <目标> <物品> [数量] ----
        check("已注册 give", CommandManager.getRegisteredCommandNames().contains("give"));

        int slotsBefore = itemCountOf(hero);
        int unitsBefore = unitCountOf(hero);
        run("give @s aNiceSword", true);
        check("give：短名可用（+1 件，占 1 格）",
                unitCountOf(hero) == unitsBefore + 1 && itemCountOf(hero) == slotsBefore + 1);
        check("give：发的是副本，带完整注册表 id",
                "game_official_content:aNiceSword".equals(firstItemIdOf(hero)));

        run("give @s game_official_content:aNiceSword 3", true);
        check("give：完整 id + 数量可用（+3，共 4 件）", unitCountOf(hero) == unitsBefore + 4);

        run("give @s ANiceSword 2", true);
        check("give：简单类名可用（+2，共 6 件）", unitCountOf(hero) == unitsBefore + 6);
        check("give：同种物品叠在一格（6 件只占 1 格）", itemCountOf(hero) == slotsBefore + 1);
        check("give：那一格的堆叠数是 6", firstItemStackOf(hero) == unitsBefore + 6);

        boolean allFullId = true;
        for (Slot slot : hero.getInventory().getSlots()) {
            Item item = slot.getContainedItem();
            if (item != null && !"game_official_content:aNiceSword".equals(item.getId())) {
                allFullId = false;
            }
        }
        check("give：格子里的物品带完整的注册表 id", allFullId);

        // 用一次只消耗一个：PlayerController.useItem 走的就是 comeToEffect + removeOne
        Item firstItem = firstItemOf(hero);
        if (firstItem != null) {
            hero.getInventory().removeOne(firstItem);
        }
        check("用一次只少一个（还剩 5 件，仍在同一格）",
                unitCountOf(hero) == unitsBefore + 5 && itemCountOf(hero) == slotsBefore + 1);

        // 用光之后格子会被清空
        for (int i = 0; i < 5; i++) {
            Item left = firstItemOf(hero);
            if (left == null) {
                break;
            }
            hero.getInventory().removeOne(left);
        }
        check("用光之后那一格被清空", itemCountOf(hero) == slotsBefore);
        check("物品全部消耗完", unitCountOf(hero) == unitsBefore);

        // 预期失败
        run("give @s noSuchItem", false);
        run("give @s aNiceSword 0", false);
        run("give @s", false);
        run("give @e[type=CommonInsect] aNiceSword", false);

        // ---- 命令不完整时的提示：要给出"接下来该怎么写"，而不是只回显 /give ----
        // 注意失败结果的文本在 getError().getMessage() 里（getMessage() 是成功回显，失败时为 null）
        CommandResult incompleteGive = CommandManager.executeResult("give");
        check("命令不完整时提示完整用法", !incompleteGive.isSuccess()
                && incompleteGive.getError() != null
                && incompleteGive.getError().getMessage() != null
                && incompleteGive.getError().getMessage().contains("/give <目标> <物品>"));

        // ---- 物品名规则：短名/类名只解析官方内容，模组物品必须写完整 id（模仿 MC 的命名空间）----
        // 造一个"假模组"来验证：它的物品 id 会带上 itemTestMod: 前缀
        Mod itemProbeMod = new Mod("itemTestMod") {
        };
        ANiceSword modSword = new ANiceSword();          // 与官方那把剑同短名、同类名
        itemProbeMod.addItem(modSword);
        Item modOnlyItem = new ANiceSword();
        modOnlyItem.setId("modOnlyItem");                // 短名唯一，只能靠完整 id 拿到
        modOnlyItem.setName("模组专属物品");
        itemProbeMod.addItem(modOnlyItem);
        World.addMod(itemProbeMod);
        itemProbeMod.registerItself();
        try {
            int slotsBeforeProbe = itemCountOf(hero);
            int unitsBeforeProbe = unitCountOf(hero);

            // 现在注册表里"aNiceSword"能匹配到官方 + 模组两件，但短名只该命中官方那件
            run("give @s aNiceSword", true);
            check("give：短名只解析官方内容（模组同名物品不参与，因此不算歧义）",
                    "game_official_content:aNiceSword".equals(firstItemIdOf(hero)));

            run("give @s ANiceSword", true);
            check("give：类名同样只解析官方内容",
                    "game_official_content:aNiceSword".equals(firstItemIdOf(hero)));

            CommandResult modShortName = CommandManager.executeResult("give @s modOnlyItem");
            check("give：模组物品写短名会被拒绝", !modShortName.isSuccess());
            check("give：拒绝时提示该写的完整 id", modShortName.getError() != null
                    && modShortName.getError().getMessage() != null
                    && modShortName.getError().getMessage().contains("itemTestMod:modOnlyItem"));

            run("give @s itemTestMod:modOnlyItem", true);
            check("give：模组物品写完整 id 可以发", hasItemId(hero, "itemTestMod:modOnlyItem"));

            // 把探测用的物品收回来，别影响后面的用例
            while (itemCountOf(hero) > slotsBeforeProbe) {
                Item left = firstItemOf(hero);
                if (left == null) {
                    break;
                }
                hero.getInventory().removeOne(left);
            }
            check("探测用的物品已清干净", unitCountOf(hero) == unitsBeforeProbe);
        } finally {
            World.removeItem(modSword);
            World.removeItem(modOnlyItem);
            World.removeMod(itemProbeMod);
        }

        // ---- 官方内容里的效果药水（customItem/potions/）----
        // 每件注册物品都必须能 copy() 并保住完整 id（药水各自实现了拷贝构造器）
        StringBuilder badCopy = new StringBuilder();
        for (Item template : World.getItemList()) {
            if (template == null || template.getId() == null || template.getId().indexOf(':') < 0) {
                if (badCopy.length() > 0) {
                    badCopy.append('、');
                }
                badCopy.append(template == null ? "null" : template.getClass().getSimpleName() + "(没有完整 id)");
                continue;
            }
            try {
                if (!template.getId().equals(template.copy().getId())) {
                    if (badCopy.length() > 0) {
                        badCopy.append('、');
                    }
                    badCopy.append(template.getClass().getSimpleName() + "(copy 后 id 变了)");
                }
            } catch (RuntimeException e) {
                if (badCopy.length() > 0) {
                    badCopy.append('、');
                }
                badCopy.append(template.getClass().getSimpleName() + "(copy 抛异常)");
            }
        }
        check("每件注册物品都能 copy() 且保住完整 id"
                        + (badCopy.length() == 0 ? "" : "（有问题：" + badCopy + "）"),
                badCopy.length() == 0);
        check("注册表里有 9 件物品（1 把剑 + 8 瓶药水）", World.getItemList().size() == 9);

        // 攻击药水：走一遍「使用物品」的那一步（PlayerController.useItem 做的就是 comeToEffect）
        Item attackPotion = itemTemplateOf("attackPotion");
        check("注册表里有攻击药水", attackPotion != null);
        double attackPercentBefore = hero.getAttackEnhancePercent();
        if (attackPotion != null) {
            attackPotion.copy().comeToEffect(hero, fight);
        }
        check("攻击药水：使用后攻击百分比 +0.2",
                Math.abs(hero.getAttackEnhancePercent() - (attackPercentBefore + 0.2)) < 1e-9);
        cn.gfhnv.game.effect.Effect attackEffect = effectOf(hero, "attackEnhanceEffect");
        check("攻击药水：效果来源记的是这件物品的 id",
                attackEffect != null && "game_official_content:attackPotion".equals(attackEffect.getOrigin()));
        run("effect @s remove all", true);

        // 治疗药水：先掉 500 血，再喝一瓶
        Item healingPotion = itemTemplateOf("healingPotion");
        hero.setHp(hero.getHp() - 500);
        long hpBeforePotion = hero.getHp();
        if (healingPotion != null) {
            healingPotion.copy().comeToEffect(hero, fight);
        }
        check("治疗药水：使用后回血 210",
                hero.getHp() == Math.min(hero.getHpMax(), hpBeforePotion + 210));
        run("effect @s remove all", true);

        // 药水同样能堆叠
        int potionSlotsBefore = itemCountOf(hero);
        int potionUnitsBefore = unitCountOf(hero);
        run("give @s attackPotion 3", true);
        check("药水也能堆叠（3 瓶只占 1 格）",
                itemCountOf(hero) == potionSlotsBefore + 1 && unitCountOf(hero) == potionUnitsBefore + 3);

        CommandManager.clearCurrentFight();
        CommandSource.setCurrentFight(null);
    }

    /**
     * 测试 {@link FixOrderController}（固定技能顺序 + 指定下一个技能）、
     * 目标选择策略 {@link TargetStrategies} 与嘲讽效果 {@link Taunt}。
     */
    private static void testFixOrderController() {
        section("固定顺序 AI 与目标策略");
        List<String> actionLog = new ArrayList<>();
        try {
            // 一场小战斗：玩家一（我方） vs 甲虫 / 乙虫（敌方）
            LivingThing hero = new PlayerOne(125).copy();
            LivingThing bugA = new CommonInsect(100L).copy();
            LivingThing bugB = new CommonInsect(100L).copy();
            bugA.setName("甲虫");
            bugB.setName("乙虫");
            World.addThing(hero);
            World.addThing(bugA);
            World.addThing(bugB);
            Fight fight = new Fight(new ArrayList<>(List.of(bugA, bugB)), new ArrayList<>(),
                    new ArrayList<>(List.of(hero)));

            // 三个假技能：只往日志里记一笔，不产生任何战斗效果
            List<Skill> skills = new ArrayList<>();
            skills.add(new ProbeSkill("甲招", actionLog));
            skills.add(new ProbeSkill("乙招", actionLog));
            skills.add(new ProbeSkill("丙招", actionLog));
            FixOrderController controller = new FixOrderController(skills, hero);
            hero.setController(controller);
            check("默认轮转顺序 = 技能列表顺序",
                    String.join(",", controller.getRotationNames()).equals("甲招,乙招,丙招"));
            controller.setRotationByName("甲招", "乙招", "丙招");

            // ① 轮转：甲 → 乙 → 丙 → 甲
            controller.act(fight);
            controller.act(fight);
            controller.act(fight);
            controller.act(fight);
            check("固定顺序：按 甲→乙→丙 循环（第 4 次回到甲）",
                    String.join(",", actionLog).equals("甲招,乙招,丙招,甲招"));
            Skill peeked = controller.peekNextSkill();
            check("peekNextSkill 预知下一个是乙招", peeked != null && "乙招".equals(peeked.getName()));

            // ② 指定下一个技能（插入语义：不消耗轮转）
            check("forceNextSkill 对不存在的技能返回 false", !controller.forceNextSkill("不存在招"));
            check("forceNextSkill 对存在的技能返回 true", controller.forceNextSkill("丙招"));
            Skill peekedForced = controller.peekNextSkill();
            check("peekNextSkill 优先返回指定的技能",
                    peekedForced != null && "丙招".equals(peekedForced.getName()));
            actionLog.clear();
            controller.act(fight);
            check("指定的技能先放（丙招）", String.join(",", actionLog).equals("丙招"));
            actionLog.clear();
            controller.act(fight);
            check("插入语义：放完指定的技能后回到轮转里的乙招",
                    String.join(",", actionLog).equals("乙招"));

            // ③ 替换语义：指定技能顺手吃掉轮转里的下一步
            controller.setRotationByName("甲招", "乙招", "丙招");   // 游标回到甲招
            actionLog.clear();
            controller.act(fight);
            check("替换语义：先按轮转放甲招", String.join(",", actionLog).equals("甲招"));
            actionLog.clear();
            controller.forceNextSkill("甲招", true);                 // 指定甲招，并吃掉轮转里的乙招
            controller.act(fight);
            check("替换语义：这一步放的是甲招", String.join(",", actionLog).equals("甲招"));
            actionLog.clear();
            controller.act(fight);
            check("替换语义：轮转里的乙招被吃掉，下一个是丙招",
                    String.join(",", actionLog).equals("丙招"));

            // ④ 放不出来时顺延（把乙招冷却住）
            controller.setRotationByName("甲招", "乙招", "丙招");
            Skill beta = skillNamed(controller, "乙招");
            if (beta != null) {
                beta.setNowCoolDown(3);
            }
            actionLog.clear();
            controller.act(fight);
            controller.act(fight);
            check("放不出来时顺延到下一招（乙招在冷却 → 丙招）",
                    String.join(",", actionLog).equals("甲招,丙招"));
            if (beta != null) {
                beta.setNowCoolDown(0);
            }

            // ⑤ 拷贝之后必须还是固定顺序（否则选敌人时一 copy 就退化成随机 AI）
            LivingThing clone = hero.copy();
            check("拷贝后控制器类型不变（仍是 FixOrderController）",
                    clone.getController() instanceof FixOrderController);
            FixOrderController cloneController = clone.getController() instanceof FixOrderController
                    ? (FixOrderController) clone.getController() : null;
            check("拷贝后轮转顺序保留",
                    cloneController != null
                            && String.join(",", cloneController.getRotationNames()).equals("甲招,乙招,丙招"));

            // ⑥ 目标策略：first() 恒定选候选里的第一个
            List<Skill> attackSkills = new ArrayList<>();
            attackSkills.add(new ProbeSkill("点杀", actionLog, 1));
            FixOrderController attacker = new FixOrderController(attackSkills, hero);
            hero.setController(attacker);
            attacker.setRotationByName("点杀");
            attacker.setTargetStrategy(TargetStrategies.first());
            actionLog.clear();
            attacker.act(fight);
            check("目标策略 first()：打候选里的第一个（甲虫）",
                    actionLog.size() == 1 && actionLog.get(0).equals("点杀→甲虫"));

            // 冷却语义（统一后）：释放完 nowCoolDown = coolDown，所以 coolDown=0 的技能可以接着再用。
            // 这里同时也是「目标策略」那几条断言能连续跑的前提 —— 不能再有 coolDown + 1 那套规则。
            Skill pointKill = skillNamed(attacker, "点杀");
            check("冷却语义统一：带目标技能释放后 nowCoolDown = coolDown",
                    pointKill != null && pointKill.getCoolDown() == 0 && pointKill.getNowCoolDown() == 0);

            // ⑦ 嘲讽：把乙虫标成嘲讽目标，tauntAware 会把它排到最前
            bugB.addEffect(new Taunt(3));
            check("嘲讽等级从效果里读出来（Taunt(3) → 等级 1）",
                    TargetStrategies.tauntLevelOf(bugB) == 1);
            attacker.setTargetStrategy(TargetStrategies.tauntAware(TargetStrategies.first()));
            actionLog.clear();
            attacker.act(fight);
            check("嘲讽：优先打带嘲讽的乙虫（即使它不是第一个）",
                    actionLog.size() == 1 && actionLog.get(0).equals("点杀→乙虫"));

            // ⑧ 嘲讽等级高的更优先
            bugA.addEffect(new Taunt(2, 3));
            actionLog.clear();
            attacker.act(fight);
            check("嘲讽等级高的更优先（甲虫等级 2 > 乙虫等级 1）",
                    actionLog.size() == 1 && actionLog.get(0).equals("点杀→甲虫"));

            // ⑨ 嘲讽效果已注册（/effect 里能直接加）
            check("嘲讽效果已注册（/effect 可用）", templateOf("tauntEffect") != null);
        } catch (Exception e) {
            fail("固定顺序 AI 测试抛出异常：" + e);
        }
    }

    /**
     * 测试减伤：{@code LivingThing} 的多个减伤来源按<b>乘算</b>叠加
     * （50% 与 25% → 只受 37.5% 伤害），并且伤害永远不会算成负数
     * （负伤害会被 {@code getDamage} 当成治疗）。
     */
    private static void testDamageReduction() {
        section("减伤计算（乘算叠加）");
        try {
            LivingThing target = new CommonInsect(100L).copy();
            Object sourceA = new Object();
            Object sourceB = new Object();

            // 50% 与 25%：乘算 → 0.5 × 0.75 = 0.375（相加才是 0.25，那是错的）
            target.addDamageReduction(sourceA, 0.5);
            target.addDamageReduction(sourceB, 0.25);
            check("两个减伤 50% + 25% 乘算 → 承伤 0.375",
                    Math.abs(target.getDamageTakenMultiplier() - 0.375) < 1e-9);
            check("总减伤 = 1 − 承伤倍率 = 0.625",
                    Math.abs(target.getDamageAbsorbedPercent() - 0.625) < 1e-9);

            // 同一来源重复添加只算一次（技能反复触发不会越叠越多）
            target.addDamageReduction(sourceA, 0.5);
            check("同一来源重复添加不会叠两次",
                    Math.abs(target.getDamageTakenMultiplier() - 0.375) < 1e-9);

            // 移除一个来源
            target.removeDamageReduction(sourceB);
            check("移除一个来源后只剩 50% 减伤",
                    Math.abs(target.getDamageTakenMultiplier() - 0.5) < 1e-9);

            // 减伤来源要在 /data 里看得见（用户实测反馈过"只有 percent，看不出是谁给的"）
            target.clearDamageReductions();
            target.addDamageReduction(new DamageReductionSource("测试来源"), 0.25);
            NbtCompound reductionDump = DataBridge.toCompound(target);
            check("减伤来源在 /data 里带得出名字（不再是只有 percent）",
                    reductionDump.get("damageReductions") instanceof NbtList reductions
                            && reductions.size() == 1
                            && reductions.get(0) instanceof NbtCompound first
                            && "测试来源".equals(first.get("sourceName").asString())
                            && Math.abs(first.get("percent").asDouble() - 0.25) < 1e-9);
            // 按身份比较这一点不能被名字动摇：名字相同也是两个独立来源
            target.addDamageReduction(new DamageReductionSource("测试来源"), 0.1);
            check("同名但不同实例的来源仍然是两条（身份比较没被名字动摇）",
                    target.getDamageReductions().size() == 2);

            // 兼容旧写法：setDamageAbsorbedPercent(0.75) → 少受 75%
            target.clearDamageReductions();
            target.setDamageAbsorbedPercent(0.75);
            check("旧写法 setDamageAbsorbedPercent(0.75) → 承伤 0.25",
                    Math.abs(target.getDamageTakenMultiplier() - 0.25) < 1e-9);

            // 比例被夹到 [0,1]：减伤叠再多也只会压到 0，不会变成负数
            target.clearDamageReductions();
            target.addDamageReduction(sourceA, 1.5);
            check("减伤比例被夹到 1（承伤 0，不会变负数）",
                    target.getDamageTakenMultiplier() == 0);

            // 真实伤害计算：承伤 = 基础伤害 × 承伤倍率
            LivingThing attacker = new PlayerOne(125).copy();
            attacker.setCriticalRate(-1);   // 负暴击率 = 永不暴击，避免随机暴击干扰比例断言
            Skill probe = new ProbeSkill("测伤", new ArrayList<>(), 1, 1.0);
            LivingThing dummy = new CommonInsect(100L).copy();
            long base = DamageCalculate.calculate(attacker, dummy, probe);
            check("基础伤害大于 0（测试前提）", base > 0);

            dummy.addDamageReduction(sourceA, 0.5);
            long reduced = DamageCalculate.calculate(attacker, dummy, probe);
            check("减伤 50% 后伤害约为一半（取整误差 ≤ 2）",
                    Math.abs(reduced - base * 0.5) <= 2);

            dummy.clearDamageReductions();
            dummy.addDamageReduction(sourceB, 1.0);
            long zeroed = DamageCalculate.calculate(attacker, dummy, probe);
            check("减伤 100% 时伤害为 0（不是负数，也就不会变成治疗）", zeroed == 0);

            // ---- 抗性 / 穿透也是乘算：(1 − 抗性) × (1 + 穿透) ----
            // 虫子的元素是金，金属抗性 0.95；用同族虫子当靶子，数值可控
            LivingThing metalAttacker = new CommonInsect(100L).copy();
            metalAttacker.setCriticalRate(-1);
            LivingThing metalVictim = new CommonInsect(100L).copy();
            long resistant = DamageCalculate.calculate(metalAttacker, metalVictim, probe);
            check("抗性 95% 时伤害很低但大于 0（测试前提）", resistant > 0);

            metalAttacker.setPenetration(0.5);
            long pierced = DamageCalculate.calculate(metalAttacker, metalVictim, probe);
            check("穿透 50% → 伤害 ×1.5（乘算，不是和抗性相加）",
                    Math.abs(pierced - resistant * 1.5) <= 2);

            metalAttacker.setPenetration(0);
            metalVictim.setMetalResistance(0);
            long neutral = DamageCalculate.calculate(metalAttacker, metalVictim, probe);
            metalVictim.setMetalResistance(-0.5);
            long weak = DamageCalculate.calculate(metalAttacker, metalVictim, probe);
            check("抗性为负（弱点）→ 受伤 ×1.5（负抗性不会被夹掉）",
                    Math.abs(weak - neutral * 1.5) <= 2);

            // ---- 伤害修正器：可以有多个，按添加顺序依次套用 ----
            LivingThing tank = new CommonInsect(100L).copy();
            IModifyDamage halve = new IModifyDamage() {
                @Override
                public long damageModify(long newHp, DamageEvent da) {
                    return newHp / 2;
                }
            };
            IModifyDamage minusHundred = new IModifyDamage() {
                @Override
                public long damageModify(long newHp, DamageEvent da) {
                    return newHp - 100;
                }
            };
            DamageEvent sample = new DamageEvent(metalAttacker, tank, probe);
            tank.setModifyDamage(halve);
            tank.addModifyDamage(minusHundred);
            check("两个修正器都要生效：1000 → 500 → 400",
                    tank.modifyIncomingDamage(1000, sample) == 400);
            check("修正器列表里有 2 个", tank.getModifyDamageList().size() == 2);

            tank.setModifyDamage(halve);
            check("setModifyDamage 是替换而不是追加（1000 → 500）",
                    tank.modifyIncomingDamage(1000, sample) == 500
                            && tank.getModifyDamageList().size() == 1);

            tank.addModifyDamage(minusHundred);
            tank.removeModifyDamage(halve);
            check("移除一个修正器后只剩另一个（1000 → 900）",
                    tank.modifyIncomingDamage(1000, sample) == 900);

            // ---- 免死机制：致死伤害在 getDamage 里被修正器拦下 ----
            // 白厄的免死、李晓焰的复活都挂在同一条链上，这里用等价的探针验证链路
            tank.clearModifyDamage();
            tank.setHp(1);
            DamageEvent lethal = new DamageEvent(metalAttacker, tank, probe);
            lethal.getDamage().setDamageAmount(9999L);
            ProbeDeathWard ward = new ProbeDeathWard();
            tank.addModifyDamage(ward);
            tank.getDamage(lethal);
            check("免死把致死伤害拦下、血量锁在 1（这次是真实挨打，免死被消费）",
                    tank.getHp() == 1 && tank.isAlive() && ward.wasConsumed());

            // ---- 伤害试算必须是只读的：AI 预判一次不能就把免死花掉 ----
            LivingThing living = new CommonInsect(100L).copy();
            ProbeDeathWard guarded = new ProbeDeathWard();
            living.addModifyDamage(guarded);
            living.setHp(1);
            DamageEvent predictedEvent = new DamageEvent(metalAttacker, living, probe);
            predictedEvent.getDamage().setDamageAmount(9999L);
            long predicted = probe.getAnticipatedDamage(living, metalAttacker);
            check("试算时修正器确实被调用、并且能看出自己在试算（isAnticipating 为 true）",
                    guarded.wasCalledWhileAnticipating());
            check("试算走完整修正器链：预测伤害 = 0（1 血 − 9999，被锁回 1）",
                    predicted == 0);
            check("试算不改状态：免死没被花掉、试算结束后 isAnticipating() 复位",
                    guarded.wasConsumed() == false && !living.isAnticipating());
            living.getDamage(predictedEvent);
            check("试算之后再真挨打，免死照常触发（只读预测没有偷走次数）",
                    living.getHp() == 1 && guarded.wasConsumed());
            tank.removeModifyDamage(ward);
            tank.setHp(1);
            tank.getDamage(lethal);
            check("移除修正器后 1 血吃 9999 伤害会死（说明前面是修正器救的）",
                    tank.getHp() == 0 && !tank.isAlive());

            // ---- 血量下限：修正器只能把血往上拉，不能反过来加伤 ----
            LivingThing guardian = new CommonInsect(100L).copy();
            long guardianMaxHp = guardian.getHpMax();
            ProbeHpFloor floor = new ProbeHpFloor(0.5);
            guardian.addModifyDamage(floor);
            guardian.setHp(1);
            guardian.getDamage(lethal);
            check("血量下限：1 血吃致死伤害后被抬到 50% 生命上限",
                    guardian.getHp() == (long) (guardianMaxHp * 0.5));
            check("满血时不触发下限（修正器只抬高、不压低）",
                    guardian.modifyIncomingDamage(guardianMaxHp, lethal) == guardianMaxHp);
            guardian.removeModifyDamage(floor);
            check("移除下限修正器后列表为空", guardian.getModifyDamageList().isEmpty());

            // ---- 无视防御：只认 IDefenceIgnore 接口，模组自写的效果一样生效 ----
            LivingThing piercer = new CommonInsect(100L).copy();
            piercer.addEffect(new ProbeDefenceIgnoreEffect(0.5, 0));
            check("无视防御：接口实现被汇总（0.5）",
                    Math.abs(piercer.getIgnoreDefencePercent() - 0.5) < 1e-9);
            long normalHit = DamageCalculate.calculate(metalAttacker, metalVictim, probe);
            long ignoreHit = DamageCalculate.calculate(piercer, metalVictim, probe);
            check("无视防御确实让伤害变高", ignoreHit > normalHit);
        } catch (Exception e) {
            fail("减伤测试抛出异常：" + e);
        }
    }

    /**
     * 盗火行者的阵营判定自测。
     * <p>
     * 钉住一个实测踩到的 bug：召唤物【残破容器】与 BOSS 同属敌方阵营，
     * 所以任何"打对面"的代码都必须按 <b>user 所在阵营的对面</b> 取目标。
     * 曾经写成 {@code Fight#getOpponentList(user)}（它在 user 不在敌方列表时返回 enemiesList），
     * 导致目标里混进 BOSS 自己的召唤物 —— 日志表现为"BOSS 打自己的容器"、
     * "BOSS 被自己的【侵蚀】烧"。正确写法见
     * {@code FlameReaverSkill#fightingSideOf(Fight, LivingThing)}。
     *
     * @author AI（DeepSeek）生成
     */
    private static void testFlameReaverFactions() {
        System.out.println();
        System.out.println("-------- 盗火行者：阵营与目标 --------");
        try {
            FlameReaver boss =
                    new FlameReaver(150);
            LivingThing hero = new PlayerOne(125).copy();
            hero.setName("测试玩家");
            List<LivingThing> enemies = new ArrayList<>();
            enemies.add(boss);
            List<LivingThing> fighters = new ArrayList<>();
            fighters.add(hero);
            Fight fight = new Fight(enemies, new ArrayList<>(), fighters);

            cn.gfhnv.game.officialStuff.customEntity.summons.BrokenContainer container =
                    boss.summonContainer(fight);
            check("召唤容器成功（测试前提）", container != null);
            check("容器进了敌方阵营",
                    container != null && fight.getEnemiesList().contains(container));
            check("容器不在我方阵营",
                    container != null && !fight.getFighterList().contains(container));

            // 先把几个列表的真实内容打出来：这两个框架方法的命名有歧义，
            // 光看名字判断会来回改错（已经错过两次），必须靠实测输出定死。
            System.out.println("  [诊断] enemiesList = " + describeNames(fight.getEnemiesList()));
            System.out.println("  [诊断] fighterList = " + describeNames(fight.getFighterList()));
            System.out.println("  [诊断] getOpponentList(BOSS) = " + describeNames(fight.getOpponentList(boss)));
            System.out.println("  [诊断] getOwnList(BOSS)      = " + describeNames(fight.getOwnList(boss)));
            System.out.println("  [诊断] getOpponentList(容器) = " + describeNames(fight.getOpponentList(container)));
            System.out.println("  [诊断] getOwnList(容器)      = " + describeNames(fight.getOwnList(container)));

            // ① 语义自证（实测输出见上面的 [诊断]，别再靠方法名猜）：
            //    getOpponentList(entity) = entity 对面的实体列表
            //    getOwnList(entity)      = entity 自己一侧的实体列表（BOSS 用 = 含它自己的召唤物）
            List<LivingThing> oppositeOfBoss = fight.getOpponentList(boss);
            List<LivingThing> sameSideAsBoss = fight.getOwnList(boss);
            check("getOpponentList(BOSS) = 对面（只有玩家，不含自己人）",
                    oppositeOfBoss.size() == 1 && oppositeOfBoss.contains(hero));
            check("getOwnList(BOSS) = 自己一侧（含 BOSS 自己与它的容器）",
                    sameSideAsBoss.contains(boss)
                            && container != null && sameSideAsBoss.contains(container));
            check("自己一侧里没有玩家",
                    !sameSideAsBoss.contains(hero));

            // ② BOSS 要打的就是对面 —— 直接就是玩家队伍，不会混进召唤物
            check("BOSS 用 getOpponentList 取目标不会打到自己人",
                    !oppositeOfBoss.contains(container));

            // ③ 容器取目标同样是"它的对面"
            List<LivingThing> containerTargets = fight.getOpponentList(container);
            check("容器取目标 = 玩家队伍（不误伤 BOSS）",
                    containerTargets.size() == 1 && containerTargets.contains(hero)
                            && !containerTargets.contains(boss));

            // ④ 遍历"自己的召唤物"必须用 getOwnList + 类型过滤（就像 FlameReaver#getAliveContainers）
            List<LivingThing> ownSideContainers = new ArrayList<>();
            for (LivingThing each : sameSideAsBoss) {
                if (each instanceof cn.gfhnv.game.officialStuff.customEntity.summons.BrokenContainer) {
                    ownSideContainers.add(each);
                }
            }
            check("用 getOwnList + 类型过滤能筛出自己的容器",
                    ownSideContainers.size() == 1 && ownSideContainers.contains(container));

            // ⑤ 完整容器的奖励：额外回合 + 增伤 buff（官方末日幻影 3.4 的机制）
            //    额外回合的实现是"在【当前时间点】给受益者插一个 needTime=0 的回合条目"，
            //    所以断言就查这条目：必须是他的、而且立刻可执行（时间点 = 现在）。
            int entriesBefore = cn.gfhnv.game.system.fight.TurnManager.getTurns().size();
            // presentTime 在自测里是 null（没跑 TurnManager.init），实现会兜底成 ZERO，
            // 断言这边用同样的归一化，否则 compareTo(null) 会 NPE
            java.math.BigDecimal now = cn.gfhnv.game.system.fight.TurnManager.getPresentTime();
            if (now == null) {
                now = java.math.BigDecimal.ZERO;
            }
            boss.grantExtraTurn(hero);
            List<cn.gfhnv.game.system.fight.TurnEntry> turns =
                    cn.gfhnv.game.system.fight.TurnManager.getTurns();
            check("额外回合：时间轴上多了一个回合条目",
                    turns.size() == entriesBefore + 1);

            cn.gfhnv.game.system.fight.TurnEntry granted = null;
            for (cn.gfhnv.game.system.fight.TurnEntry entry : turns) {
                if (entry.getLivingThing() == hero
                        && entry.getNeedTime().compareTo(java.math.BigDecimal.ZERO) == 0
                        && entry.getStartTime().compareTo(now) == 0) {
                    granted = entry;
                }
            }
            check("额外回合：那一条属于受益者，且 needTime=0 / startTime=now（立刻可执行）",
                    granted != null);
            check("额外回合：排完序后它在队首（所以下一圈就会被取出来行动）",
                    !turns.isEmpty() && turns.getFirst() == granted);
            // 必须是 isExtra：额外回合不推进身上效果的剩余回合（EffectEventListener 按这个标记走）
            check("额外回合：标记了 isExtra（与白厄的额外回合同一个约定）",
                    granted != null && granted.isExtra());

            // ⑥ 2026-10-03 用户要求"这个奖励的额外回合应该直接行动（排在前面）"：
            //    光排在 presentTime 上不够 —— 时间打平时 sort() 原先按速度排，
            //    "立即行动"会被场上更快的单位抢走。所以给回合加了优先级。
            check("额外回合：带了 PRIORITY_EXTRA（时间打平时压过普通回合先手）",
                    granted != null
                            && granted.getPriority() == cn.gfhnv.game.system.fight.TurnEntry.PRIORITY_EXTRA);

            List<cn.gfhnv.game.system.fight.TurnEntry> timelineBefore =
                    new ArrayList<>(cn.gfhnv.game.system.fight.TurnManager.getTurns());
            LivingThing fastNormal = new CommonInsect(100L).copy();
            fastNormal.setName("快的普通回合");
            fastNormal.setSpeed(999);
            LivingThing slowPriority = new CommonInsect(100L).copy();
            slowPriority.setName("慢的奖励回合");
            slowPriority.setSpeed(1);
            cn.gfhnv.game.system.fight.TurnEntry normalEntry = new cn.gfhnv.game.system.fight.TurnEntry(
                    fastNormal, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO);
            cn.gfhnv.game.system.fight.TurnEntry priorityEntry = new cn.gfhnv.game.system.fight.TurnEntry(
                    slowPriority, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO)
                    .setPriority(cn.gfhnv.game.system.fight.TurnEntry.PRIORITY_EXTRA);
            cn.gfhnv.game.system.fight.TurnManager.getTurns().add(normalEntry);
            cn.gfhnv.game.system.fight.TurnManager.getTurns().add(priorityEntry);
            cn.gfhnv.game.system.fight.TurnManager.sort();
            // 只比这两条的相对顺序：时间轴上还有别的条目，不能拿 getFirst() 断言
            check("排序：时间相同时优先级大的先执行（优先级压过速度：速度 1 的奖励回合排到速度 999 前面）",
                    cn.gfhnv.game.system.fight.TurnManager.getTurns().indexOf(priorityEntry)
                            < cn.gfhnv.game.system.fight.TurnManager.getTurns().indexOf(normalEntry));

            // 优先级也打平时，快的先动 —— 老口径（TurnManager.init 全体同起点）不能被改坏
            priorityEntry.setPriority(cn.gfhnv.game.system.fight.TurnEntry.PRIORITY_NORMAL);
            cn.gfhnv.game.system.fight.TurnManager.sort();
            check("排序：优先级也相同时，速度快的先动（老口径保留）",
                    cn.gfhnv.game.system.fight.TurnManager.getTurns().indexOf(normalEntry)
                            < cn.gfhnv.game.system.fight.TurnManager.getTurns().indexOf(priorityEntry));

            // 优先级压不过时间：晚到的奖励回合不能越过时间更早的普通回合
            normalEntry.setNeedTime(java.math.BigDecimal.ONE);      // time = 1
            priorityEntry.setNeedTime(java.math.BigDecimal.TEN);    // time = 10
            priorityEntry.setPriority(cn.gfhnv.game.system.fight.TurnEntry.PRIORITY_EXTRA);
            cn.gfhnv.game.system.fight.TurnManager.sort();
            check("排序：优先级压不过时间（时间更晚的奖励回合仍然排在后面）",
                    cn.gfhnv.game.system.fight.TurnManager.getTurns().indexOf(normalEntry)
                            < cn.gfhnv.game.system.fight.TurnManager.getTurns().indexOf(priorityEntry));

            // 收尾：把探测条目摘掉，时间轴还原成进来时的样子
            cn.gfhnv.game.system.fight.TurnManager.getTurns().clear();
            cn.gfhnv.game.system.fight.TurnManager.getTurns().addAll(timelineBefore);
            cn.gfhnv.game.system.fight.TurnManager.sort();

            // ⑥ 增伤 buff：走 setEnhance（伤害公式的 (1+enhance)），并且只加一次
            //    先用一个干净的容器来量"一次奖励加了多少"
            LivingThing rewardProbe = new PlayerOne(125).copy();
            rewardProbe.setName("奖励探针");
            double enhanceBefore = rewardProbe.getEnhance();
            boss.grantContainerReward(rewardProbe);
            check("完整容器奖励：增伤按 ContainerReward 的比例加上去了（+0.4）",
                    Math.abs(rewardProbe.getEnhance() - (enhanceBefore + 0.4)) < 1e-9);
            double afterFirst = rewardProbe.getEnhance();
            // 再调一次"获得时"的钩子，验证 applied 标记挡住了重复叠加
            for (cn.gfhnv.game.effect.Effect each : rewardProbe.getEntityEffectList()) {
                if (each instanceof cn.gfhnv.game.officialStuff.customEffect.flameReaverEffects.ContainerReward) {
                    each.comeIntoEffect(rewardProbe);
                }
            }
            check("完整容器奖励：重复触发不会叠加（applied 标记）",
                    Math.abs(rewardProbe.getEnhance() - afterFirst) < 1e-9);

            // ⑦ 【沉默的悲叹】的"下次行动延后 100%"：两种路径都真的生效
            //    路径 A：时间轴上没有自己的回合（= 正在自己的回合里）→ 自己排一个双倍间隔的，
            //           并把本回合信号设成 SKIP_WITHOUT_NEW_TURN，免得循环再排一条正常间隔的
            cn.gfhnv.game.system.fight.TurnManager.getTurns()
                    .removeIf(t -> t != null && t.getLivingThing() == boss);
            java.math.BigDecimal normalNeed = java.math.BigDecimal.valueOf(10000)
                    .divide(java.math.BigDecimal.valueOf(boss.getSpeed()), 10, java.math.RoundingMode.HALF_UP);
            cn.gfhnv.game.system.fight.TurnEntry fakePresentTurn =
                    new cn.gfhnv.game.system.fight.TurnEntry(boss, normalNeed, java.math.BigDecimal.ZERO);
            cn.gfhnv.game.eventListener.FightTurnPastListener.setPresentTurn(fakePresentTurn);
            boss.delayNextOwnTurn(java.math.BigDecimal.ONE);
            cn.gfhnv.game.system.fight.TurnEntry delayed =
                    cn.gfhnv.game.system.fight.TurnManager.getNextTurnOf(boss);
            check("延后：时间轴上没有自己的回合时，自己排一条（间隔 ×2）",
                    delayed != null && delayed.getNeedTime()
                            .compareTo(normalNeed.multiply(java.math.BigDecimal.TWO)) == 0);
            check("延后：本回合信号被设成 SKIP_WITHOUT_NEW_TURN（否则循环还会再排一条）",
                    fakePresentTurn.getActionSignal() == cn.gfhnv.game.system.fight.ActionSignal.SKIP_WITHOUT_NEW_TURN);

            //    路径 B：时间轴上已经有自己的回合 → 直接把那一条的间隔再乘 2
            java.math.BigDecimal beforeDelay = delayed == null ? java.math.BigDecimal.ZERO : delayed.getNeedTime();
            boss.delayNextOwnTurn(java.math.BigDecimal.ONE);
            check("延后：已有待执行回合时，把那一条的间隔再乘 2",
                    delayed != null && delayed.getNeedTime()
                            .compareTo(beforeDelay.multiply(java.math.BigDecimal.TWO)) == 0);

            // 收尾：把这两条探测用的时间轴条目与自己塞的"当前回合"都还原掉
            cn.gfhnv.game.system.fight.TurnManager.getTurns()
                    .removeIf(t -> t != null && t.getLivingThing() == boss);
            cn.gfhnv.game.eventListener.FightTurnPastListener.setPresentTurn(null);

            // ⑧ 时间轴条目查询（getNextTurnOf）的三条约定：跳过额外回合、判空、不崩
            //    背景：旧实现返回「该实体排序后最早的一条」，而额外回合的 needTime=0，
            //    天然最早 —— 于是「灾厄-弑魂焚诏」让敌方全体立即行动时，会把对方手里的
            //    奖励回合（击杀完整容器 / 变身连击）当成「下次行动」顶掉。
            cn.gfhnv.game.system.fight.TurnManager.getTurns()
                    .removeIf(t -> t != null && t.getLivingThing() == hero);
            check("getNextTurnOf：不在时间轴上的生物返回 null（调用方必须自己判空）",
                    cn.gfhnv.game.system.fight.TurnManager.getNextTurnOf(hero) == null);
            check("getNextTurnOf：参数为 null 时也返回 null，不再抛 NPE",
                    cn.gfhnv.game.system.fight.TurnManager.getNextTurnOf(null) == null);

            java.math.BigDecimal heroNeed = java.math.BigDecimal.valueOf(10000)
                    .divide(java.math.BigDecimal.valueOf(hero.getSpeed()), 10, java.math.RoundingMode.HALF_UP);
            cn.gfhnv.game.system.fight.TurnEntry heroNormal =
                    new cn.gfhnv.game.system.fight.TurnEntry(hero, heroNeed, java.math.BigDecimal.ZERO);
            cn.gfhnv.game.system.fight.TurnEntry heroExtra =
                    new cn.gfhnv.game.system.fight.TurnEntry(hero, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO)
                            .setExtra(true);
            cn.gfhnv.game.system.fight.TurnManager.getTurns().add(heroNormal);
            cn.gfhnv.game.system.fight.TurnManager.getTurns().add(heroExtra);
            cn.gfhnv.game.system.fight.TurnManager.sort();
            check("额外回合的 needTime=0 确实排在同名生物的正常回合前面（这就是它会被顶掉的原因）",
                    cn.gfhnv.game.system.fight.TurnManager.getTurns().indexOf(heroExtra)
                            < cn.gfhnv.game.system.fight.TurnManager.getTurns().indexOf(heroNormal));
            check("getNextTurnOf：跳过额外回合条目，返回的是正常那条",
                    cn.gfhnv.game.system.fight.TurnManager.getNextTurnOf(hero) == heroNormal);

            // ⑨ 灾厄-弑魂焚诏「使敌方全体立即行动」：目标没有条目时现排一条（旧写法直接 NPE）
            cn.gfhnv.game.system.fight.TurnManager.getTurns()
                    .removeIf(t -> t != null && t.getLivingThing() == hero);
            cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.CalamitySoulscorchEdict edict =
                    new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.CalamitySoulscorchEdict();
            boolean edictSurvived = true;
            try {
                edict.comeToEffect(fight, boss, new ArrayList<>(java.util.List.of(hero)));
            } catch (RuntimeException e) {
                edictSurvived = false;
                System.out.println("  [诊断] 灾厄-弑魂焚诏 抛了 " + e);
            }
            check("灾厄-弑魂焚诏：目标不在时间轴上时现排一条，不再解引用 null（旧写法必 NPE）",
                    edictSurvived);
            cn.gfhnv.game.system.fight.TurnEntry scheduled =
                    cn.gfhnv.game.system.fight.TurnManager.getNextTurnOf(hero);
            check("灾厄-弑魂焚诏：现排的那条 needTime=0（才真的做得到「立即行动」）",
                    scheduled != null && scheduled.getNeedTime().compareTo(java.math.BigDecimal.ZERO) == 0);

            //    手里有额外回合时：改的必须是正常回合，额外回合条目一个字段都不能动
            cn.gfhnv.game.system.fight.TurnManager.getTurns()
                    .removeIf(t -> t != null && t.getLivingThing() == hero);
            cn.gfhnv.game.system.fight.TurnEntry keepNormal =
                    new cn.gfhnv.game.system.fight.TurnEntry(hero,
                            heroNeed.multiply(java.math.BigDecimal.TWO), java.math.BigDecimal.ZERO);
            cn.gfhnv.game.system.fight.TurnEntry keepExtra =
                    new cn.gfhnv.game.system.fight.TurnEntry(hero, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO)
                            .setExtra(true);
            cn.gfhnv.game.system.fight.TurnManager.getTurns().add(keepNormal);
            cn.gfhnv.game.system.fight.TurnManager.getTurns().add(keepExtra);
            edict.comeToEffect(fight, boss, new ArrayList<>(java.util.List.of(hero)));
            check("灾厄-弑魂焚诏：被拉到 now 的是正常回合，额外回合条目原封不动",
                    keepNormal.getNeedTime().compareTo(java.math.BigDecimal.ZERO) == 0
                            && keepExtra.getNeedTime().compareTo(java.math.BigDecimal.ZERO) == 0
                            && keepExtra.getStartTime().compareTo(java.math.BigDecimal.ZERO) == 0
                            && cn.gfhnv.game.system.fight.TurnManager.getNextTurnOf(hero) == keepNormal);

            // ⑩ 反击（灾厄-弑魂焚诏的反击）不再打尸体、不再在空表上炸
            LivingThing counterProbe = new PlayerOne(125).copy();
            counterProbe.setName("反击探针");
            counterProbe.setHp(100);
            long probeHpBefore = counterProbe.getHp();
            LivingThing corpseProbe = new PlayerOne(125).copy();
            corpseProbe.setName("尸体探针");
            corpseProbe.setHp(0);
            check("尸体探针确实被判定为已倒下（测试前提）", !corpseProbe.isAlive());

            boolean counterSurvivedEmpty = true;
            try {
                new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.Counterattack()
                        .comeToEffect(fight, counterProbe, new ArrayList<>());
            } catch (RuntimeException e) {
                counterSurvivedEmpty = false;
                System.out.println("  [诊断] 反击空表抛了 " + e);
            }
            check("反击：敌方列表为空时不抛 NoSuchElementException（旧的 enemies.getFirst() 必炸）",
                    counterSurvivedEmpty);

            java.util.List<LivingThing> counterTargets = new ArrayList<>();
            counterTargets.add(corpseProbe);
            // 另外两只也全设成 0 血：这里刻意不放活人 —— hero / boss 是这场战斗的实体，
            // 让它们挨一顿反击会污染后面用例的断言
            for (int i = 0; i < 2; i++) {
                LivingThing anotherCorpse = new PlayerOne(125).copy();
                anotherCorpse.setName("尸体探针" + (i + 2));
                anotherCorpse.setHp(0);
                counterTargets.add(anotherCorpse);
            }
            java.util.List<LivingThing> counterTargetsSnapshot = new ArrayList<>(counterTargets);
            new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.Counterattack()
                    .comeToEffect(fight, counterProbe, counterTargets);
            check("反击：不再就地 shuffle 调用方传进来的列表（现在只打自己抽的副本）",
                    counterTargets.equals(counterTargetsSnapshot));
            check("反击：目标全倒下时一个都不打（自身回血照旧生效，说明没在过滤处提前 return）",
                    corpseProbe.getHp() == 0 && counterProbe.getHp() > probeHpBefore);

            // 收尾：清掉这几个探测用的时间轴条目，别影响后面的用例
            cn.gfhnv.game.system.fight.TurnManager.getTurns()
                    .removeIf(t -> t != null && t.getLivingThing() == hero);

            // ⑧ 【侵蚀】按目标合并：不同来源重复施加只刷新同一条（否则一回合会连跳好几次）
            LivingThing erosionProbe = new PlayerOne(125).copy();
            erosionProbe.setName("侵蚀探针");
            cn.gfhnv.game.officialStuff.customEffect.flameReaverEffects.Erosion firstErosion =
                    cn.gfhnv.game.officialStuff.customEffect.flameReaverEffects.Erosion
                            .applyTo(erosionProbe, 0.05, 3, "来源甲");
            cn.gfhnv.game.officialStuff.customEffect.flameReaverEffects.Erosion secondErosion =
                    cn.gfhnv.game.officialStuff.customEffect.flameReaverEffects.Erosion
                            .applyTo(erosionProbe, 0.03, 2, "来源乙");
            int erosionCount = 0;
            for (cn.gfhnv.game.effect.Effect each : erosionProbe.getEntityEffectList()) {
                if (each instanceof cn.gfhnv.game.officialStuff.customEffect.flameReaverEffects.Erosion) {
                    erosionCount++;
                }
            }
            check("侵蚀：不同来源重复施加只留一条（刷新，而不是并列好几条）",
                    firstErosion != null && firstErosion == secondErosion && erosionCount == 1);
            check("侵蚀：刷新时强度取较大者、持续回合取较长者",
                    secondErosion != null && Math.abs(secondErosion.getRate() - 0.05) < 1e-9
                            && secondErosion.getLastTime() == 3);

            // ⑨ 共祭那一轮：BOSS 自己也参与攻击，而且所有容器打<b>同一批目标</b>
            //    （以前容器各自随机挑目标、BOSS 完全不攻击 —— 用户 2026-09 实测指出）
            List<LivingThing> jointTargets = boss.pickJointTargets(fight);
            check("共祭：共同目标由 BOSS 选一次（对面只有测试玩家时就是 1 个）",
                    jointTargets.size() == 1 && jointTargets.contains(hero));
            long hpBeforeJoint = hero.getHp();
            int absorbedByJoint = boss.absorbSacrificedContainers(fight, new ArrayList<>(List.of(container)));
            check("共祭：BOSS 本体也参与了这一轮攻击（测试玩家掉血），随后容器被吸收",
                    absorbedByJoint == 1 && hero.getHp() < hpBeforeJoint);

            // ⑩ 召唤物的阵营要跟着召唤者走：盗火行者也能被选成玩家角色（镜像对局），
            //    那时它的容器必须进【我方】—— 否则 getOpponentList(容器) 会落到 else 分支
            //    返回【我方】，变成"自家容器打自家队伍"，还会给自家 BOSS 叠一条 origin 不同的侵蚀。
            FlameReaver allyBoss = new FlameReaver(150);
            fight.addFighter(allyBoss);
            cn.gfhnv.game.officialStuff.customEntity.summons.BrokenContainer allyContainer =
                    allyBoss.summonContainer(fight);
            check("召唤物阵营：玩家侧 BOSS 召唤的容器进【我方】",
                    allyContainer != null && fight.getFighterList().contains(allyContainer));
            check("召唤物阵营：它的对手是【敌方】（不会打自己人）",
                    allyContainer != null && fight.getOpponentList(allyContainer).contains(boss)
                            && !fight.getOpponentList(allyContainer).contains(allyBoss));
            // 收尾：这两个探测实体排在时间轴上的回合条目也清掉
            cn.gfhnv.game.system.fight.TurnManager.getTurns().removeIf(t -> t != null
                    && (t.getLivingThing() == allyBoss || t.getLivingThing() == allyContainer));
        } catch (Exception e) {
            fail("阵营测试抛出异常：" + e);
        }
    }

    /**
     * 把一组生物的名字拼成一行，供诊断输出用。
     *
     * @param things 生物列表
     * @return 形如 {@code [甲, 乙]} 的字符串
     */
    private static String describeNames(List<LivingThing> things) {
        if (things == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < things.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(things.get(i) == null ? "null" : things.get(i).getName());
        }
        return builder.append(']').toString();
    }

    /**
     * 从控制器里按名字取技能实例（取的是控制器自己持有的那一份）。
     *
     * @param controller 控制器
     * @param name       技能名
     * @return 技能；找不到返回 {@code null}
     */
    private static Skill skillNamed(FixOrderController controller, String name) {
        for (Skill skill : controller.getSkills()) {
            if (skill != null && name.equals(skill.getName())) {
                return skill;
            }
        }
        return null;
    }

    /**
     * 判断生物身上是否有指定 id 的效果。
     * <p>
     * 比较时会去掉模组前缀：运行时效果的 id 已经被
     * {@link cn.gfhnv.game.world.World#applyRegisteredId(cn.gfhnv.game.effect.Effect)}
     * 补成了完整 id（{@code game_official_content:frozenEffect}），
     * 而用例里写的是短名（{@code frozenEffect}），两种写法都要能匹配上。
     *
     * @param livingThing 生物
     * @param effectId    效果 id（短名或完整 id）
     * @return 是否存在
     */
    private static boolean hasEffect(LivingThing livingThing, String effectId) {
        return effectOf(livingThing, effectId) != null;
    }

    /**
     * 取生物身上指定 id 的效果实例。
     *
     * @param livingThing 生物
     * @param effectId    效果 id（短名或完整 id）
     * @return 效果；找不到返回 {@code null}
     */
    private static cn.gfhnv.game.effect.Effect effectOf(LivingThing livingThing, String effectId) {
        for (cn.gfhnv.game.effect.Effect effect : livingThing.getEntityEffectList()) {
            if (effect == null || effect.getID() == null) {
                continue;
            }
            String id = effect.getID();
            if (effectId.equalsIgnoreCase(id) || effectId.equalsIgnoreCase(shortIdOf(id))) {
                return effect;
            }
        }
        return null;
    }

    /**
     * 判断生物身上有没有<b>精确</b> id 的效果（用来区分"官方"与"模组"的同名效果）。
     *
     * @param livingThing 生物
     * @param fullId      完整注册表 id
     * @return 是否存在
     */
    private static boolean hasEffectExactId(LivingThing livingThing, String fullId) {
        for (cn.gfhnv.game.effect.Effect effect : livingThing.getEntityEffectList()) {
            if (effect != null && fullId.equals(effect.getID())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 取生物身上指定 id 的效果等级。
     *
     * @param livingThing 生物
     * @param effectId    效果 id（短名或完整 id）
     * @return 等级；不存在返回 -1
     */
    private static int effectLevelOf(LivingThing livingThing, String effectId) {
        cn.gfhnv.game.effect.Effect effect = effectOf(livingThing, effectId);
        return effect == null ? -1 : effect.getLevel();
    }

    /**
     * 去掉 id 里的模组前缀（{@code game_official_content:xxx} → {@code xxx}）。
     *
     * @param id 完整 id
     * @return 短名
     */
    private static String shortIdOf(String id) {
        if (id == null) {
            return "";
        }
        int colon = id.indexOf(':');
        return colon >= 0 && colon + 1 < id.length() ? id.substring(colon + 1) : id;
    }

    /**
     * 数背包里占了几格（每格只要有东西就 +1，不看堆叠数量）。
     *
     * @param livingThing 生物
     * @return 占用的格子数
     */
    private static int itemCountOf(LivingThing livingThing) {
        int count = 0;
        for (Slot slot : livingThing.getInventory().getSlots()) {
            if (slot != null && slot.getContainedItem() != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * 数背包里一共有几件物品（把每格的堆叠数加起来）。
     * <p>
     * 同种物品会叠在一格，所以「件数」与「格数」是两个不同的量：
     * 给 6 把剑 → 1 格、6 件。
     *
     * @param livingThing 生物
     * @return 物品件数
     */
    private static int unitCountOf(LivingThing livingThing) {
        int count = 0;
        for (Slot slot : livingThing.getInventory().getSlots()) {
            if (slot != null && slot.getContainedItem() != null) {
                count += slot.getContainedItem().getStackNumber();
            }
        }
        return count;
    }

    /**
     * 取背包里第一件物品的堆叠数量。
     *
     * @param livingThing 生物
     * @return 堆叠数量；背包为空返回 0
     */
    private static int firstItemStackOf(LivingThing livingThing) {
        Item item = firstItemOf(livingThing);
        return item == null ? 0 : item.getStackNumber();
    }

    /**
     * 取背包里第一件物品（按格子顺序）。
     *
     * @param livingThing 生物
     * @return 物品；背包为空返回 {@code null}
     */
    private static Item firstItemOf(LivingThing livingThing) {
        for (Slot slot : livingThing.getInventory().getSlots()) {
            if (slot != null && slot.getContainedItem() != null) {
                return slot.getContainedItem();
            }
        }
        return null;
    }

    /**
     * 取背包里第一件物品的 id。
     *
     * @param livingThing 生物
     * @return 物品 id；背包为空返回空串
     */
    private static String firstItemIdOf(LivingThing livingThing) {
        Item item = firstItemOf(livingThing);
        return item == null || item.getId() == null ? "" : item.getId();
    }

    /**
     * 判断背包里有没有<b>精确</b> id 的物品（用来区分"官方"与"模组"的同名物品）。
     *
     * @param livingThing 生物
     * @param fullId      完整注册表 id
     * @return 是否存在
     */
    private static boolean hasItemId(LivingThing livingThing, String fullId) {
        for (Slot slot : livingThing.getInventory().getSlots()) {
            Item item = slot == null ? null : slot.getContainedItem();
            if (item != null && fullId.equals(item.getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从物品注册表里按「完整 id / 短名 / 简单类名」找模板（大小写不敏感）。
     *
     * @param name 物品名
     * @return 模板；找不到返回 {@code null}
     */
    private static Item itemTemplateOf(String name) {
        for (Item item : World.getItemList()) {
            if (item == null || item.getId() == null) {
                continue;
            }
            if (item.getId().equalsIgnoreCase(name)
                    || shortIdOf(item.getId()).equalsIgnoreCase(name)
                    || item.getClass().getSimpleName().equalsIgnoreCase(name)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 从效果注册表里取指定 id 的模板。
     *
     * @param effectId 效果 id
     * @return 模板；找不到返回 {@code null}
     */
    private static cn.gfhnv.game.effect.Effect templateOf(String effectId) {
        for (cn.gfhnv.game.effect.Effect effect : cn.gfhnv.game.world.World.getEffectList()) {
            if (effect == null) {
                continue;
            }
            String id = effect.getID() == null ? "" : effect.getID();
            // 注册进 World 的 id 带模组前缀（game_official_content:frozenEffect），
            // 所以除了全等，还要按「冒号后的后半段」与简单类名各匹配一次。
            int colon = id.indexOf(':');
            String shortId = colon >= 0 && colon + 1 < id.length() ? id.substring(colon + 1) : id;
            if (effectId.equalsIgnoreCase(id) || effectId.equalsIgnoreCase(shortId)
                    || effectId.equalsIgnoreCase(effect.getClass().getSimpleName())) {
                return effect;
            }
        }
        return null;
    }

    /**
     * 描述一个命令树节点（诊断用）。
     *
     * @param node 节点，可为 {@code null}
     * @return 可读描述
     */
    private static String describeNode(cn.gfhnv.game.system.command.CommandNode node) {
        if (node == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(node.getClass().getSimpleName())
                .append("(name=").append(node.getName()).append(')');
        builder.append(" usage=").append(node.getUsageText());
        builder.append(" 可执行=").append(node.isExecutable());
        builder.append(" 子节点=").append(node.getChildrenNames());
        return builder.toString();
    }

    /**
     * 诊断：不经过任何官方命令，直接用构建器搭一棵 {@code test <甲> <乙>} 的树，
     * 看嵌套的那一层是否真的挂上去了。
     * <p>
     * 这是「最小复现」：如果这里正常而官方命令不正常，问题就出在命令类<b>怎么用</b>构建器；
     * 如果这里也不正常，问题就在构建器本身。
     */
    private static void diagnoseBuilder() {
        System.out.println("  --- 构建器诊断（最小复现）---");
        cn.gfhnv.game.system.command.CommandDispatcher.setDebugParsing(true);
        try {
            // 最小复现：test <甲> <乙>
            // 关键：一层一个变量 —— argumentBuilder(...) 建出「甲」，
            // 再对它调 .argument("乙") 就把「乙」挂到「甲」下（返回值是新建出来的子节点）。
            cn.gfhnv.game.system.command.ArgumentBuilder child =
                    cn.gfhnv.game.system.command.ArgumentBuilder.argumentBuilder(
                            "甲", cn.gfhnv.game.system.command.WordArgumentType.word());
            cn.gfhnv.game.system.command.ArgumentBuilder grand =
                    child.argument("乙", cn.gfhnv.game.system.command.IntegerArgumentType.integer());

            // 构建器本身就是节点，直接当作树的根来检查
            cn.gfhnv.game.system.command.CommandNode rootNode = child;
            System.out.println("      最小复现的完整树（从根往下）：");
            dumpTree(rootNode, 6);

            // 断言：这条链必须建出 甲 → 乙 两层
            cn.gfhnv.game.system.command.CommandNode inner = rootNode.getChild("乙");
            check("构建器：甲节点下应挂着「乙」", inner != null);
            check("构建器：乙节点的父节点应是「甲」",
                    inner != null && inner.getParent() != null && "甲".equals(inner.getParent().getName()));
            check("构建器：链式结果的类型就是节点", rootNode instanceof cn.gfhnv.game.system.command.ArgumentBuilder);
            check("构建器：乙分支也已建出（不是懒加载）", grand != null && grand == inner);

            // 断言：官方 hurt 命令的树必须是 hurt → 目标 → 数值
            cn.gfhnv.game.system.command.CommandNode hurtNode =
                    CommandManager.getDispatcher().getCommandNode("hurt");
            check("官方命令：hurt 下应挂着「目标」",
                    hurtNode != null && hurtNode.getChildrenNames().contains("目标"));
            cn.gfhnv.game.system.command.CommandNode targetNode =
                    hurtNode == null ? null : hurtNode.getChild("目标");
            check("官方命令：「目标」下应挂着「数值」",
                    targetNode != null && targetNode.getChildrenNames().contains("数值"));

            // 断言：官方 effect 命令的树必须是 effect → 目标 → add/remove/list
            cn.gfhnv.game.system.command.CommandNode effectNode =
                    CommandManager.getDispatcher().getCommandNode("effect");
            check("官方命令：effect 下应挂着「目标」",
                    effectNode != null && effectNode.getChildrenNames().contains("目标"));
            cn.gfhnv.game.system.command.CommandNode effectTarget =
                    effectNode == null ? null : effectNode.getChild("目标");
            check("官方命令：effect 的「目标」下应挂着 add、remove、list",
                    effectTarget != null
                            && effectTarget.getChild("add") != null
                            && effectTarget.getChild("remove") != null
                            && effectTarget.getChild("list") != null);
            check("官方命令：effect 的「目标/list」应可执行",
                    effectTarget != null && effectTarget.getChild("list") != null
                            && effectTarget.getChild("list").isExecutable());
            check("官方命令：effect 的「目标/add」下应挂着「效果」",
                    effectTarget != null && effectTarget.getChild("add") != null
                            && effectTarget.getChild("add").getChild("效果") != null);
            check("官方命令：effect 的「效果」下应挂着「等级」",
                    effectTarget != null && effectTarget.getChild("add") != null
                            && effectTarget.getChild("add").getChild("效果") != null
                            && effectTarget.getChild("add").getChild("效果").getChild("等级") != null);
        } catch (Exception e) {
            System.out.println("      构建器诊断异常：" + e);
        } finally {
            cn.gfhnv.game.system.command.CommandDispatcher.setDebugParsing(false);
        }
    }

    /**
     * 递归打印一棵命令树（诊断用）。
     *
     * @param node   根
     * @param indent 缩进空格数
     */
    private static void dumpTree(cn.gfhnv.game.system.command.CommandNode node, int indent) {
        StringBuilder pad = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            pad.append(' ');
        }
        System.out.println("      " + pad + "└─ " + describeNode(node));
        for (cn.gfhnv.game.system.command.CommandNode child : node.getChildren()) {
            dumpTree(child, indent + 4);
        }
    }

    /**
     * 诊断：直接走一次调度器的「解析」阶段，然后直接查上下文里的参数。
     * <p>
     * 它把「解析出节点」与「取参数」两件事分开报告，
     * 这样就能区分「参数没解析出来」和「参数解析了但取不到」两种情况。
     * 注意：本方法不执行命令，因此不会改动任何生物。
     *
     * @param command 命令文本
     */
    private static void diagnoseParse(String command) {
        System.out.println("  --- 解析诊断：" + command + " ---");
        cn.gfhnv.game.system.command.CommandDispatcher dispatcher = CommandManager.getDispatcher();
        cn.gfhnv.game.system.command.CommandSource source =
                new cn.gfhnv.game.system.command.CommandSource(CommandManager.getPlayer());
        cn.gfhnv.game.system.command.CommandDispatcher.setDebugParsing(true);
        try {
            cn.gfhnv.game.system.command.CommandNode node = dispatcher.parse(command, source);
            System.out.println("      解析结果节点：" + describeNode(node));
            System.out.println("      节点用法：" + node.getFullUsage());
            System.out.println("      解析出的参数：" + source.describeArguments());
        } catch (Exception e) {
            System.out.println("      解析失败：" + e.getMessage());
            System.out.println("      此时参数表：" + source.describeArguments());
        } finally {
            cn.gfhnv.game.system.command.CommandDispatcher.setDebugParsing(false);
        }
    }

    /**
     * 数一个类里「参数个数为 count」的公共构造函数有几个。
     * <p>
     * 用来守护「同一个数字不会同时匹配两个构造函数」这条约定：
     * 通用效果里的百分比 / 固定值已经合并成 {@code (double percent, long amount, int lastTime)}，
     * 所以这类效果应当只有 1 个双参数构造器（只给百分比）和 1 个三参数构造器（百分比 + 固定值）。
     *
     * @param type  类
     * @param count 参数个数
     * @return 个数
     */
    private static int countConstructors(Class<?> type, int count) {
        int found = 0;
        for (java.lang.reflect.Constructor<?> constructor : type.getConstructors()) {
            if (constructor.getParameterCount() == count) {
                found++;
            }
        }
        return found;
    }

    /**
     * 盗火行者的<b>切阶段保护</b>（2026-10-03 用户要求）：
     * 二阶段的触发从"血量比例掉到 50%"改成<b>血条第一次被清空</b> ——
     * 那一发致命伤害被整个吃掉（不会死），生命回满，并拿到 70% 免伤。
     * 保护<b>只有一次</b>：二阶段里再被清空血条就真的倒下。
     */
    private static void testFlameReaverPhaseTwo() {
        section("盗火行者：切阶段保护（血条第一次清空时进二阶段）");
        try {
            cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver reaver =
                    new cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver(125);
            LivingThing attacker = new PlayerOne(125).copy();
            attacker.setCriticalRate(-1);   // 永不暴击，避免随机数干扰
            Skill probe = new ProbeSkill("测伤", new ArrayList<>(), 1, 1.0);

            check("前提：满血、且还没进二阶段（构造出来就是阶段一）",
                    reaver.getHp() == reaver.getHpMax() && !reaver.isPhaseTwo());

            // ① 血条打到 1，再来一发致死伤害 —— 应该被拦下并切成二阶段
            reaver.setHp(1);
            DamageEvent lethal = new DamageEvent(attacker, reaver, probe);
            lethal.getDamage().setDamageAmount(9999L);
            reaver.getDamage(lethal);
            check("致命伤害被挡下：没死，而且进二阶段了",
                    reaver.isPhaseTwo() && reaver.isAlive());
            check("进二阶段时生命回满（不是锁 1 血）",
                    reaver.getHp() == reaver.getHpMax());
            check("进二阶段后拿到 70% 免伤（承伤倍率 0.3）",
                    Math.abs(reaver.getDamageTakenMultiplier() - 0.3) < 1e-9);

            // ② 试算必须是只读的：AI 预判一次不能就把这次保护花掉
            cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver anticipatedTarget =
                    new cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver(125);
            anticipatedTarget.setHp(1);
            DamageEvent anticipated = new DamageEvent(attacker, anticipatedTarget, probe);
            anticipated.getDamage().setDamageAmount(9999L);
            long anticipatedHp = anticipatedTarget.anticipating(() ->
                    anticipatedTarget.modifyIncomingDamage(
                            anticipatedTarget.getHp() - anticipated.getDamage().getDamageAmount(),
                            anticipated));
            check("试算期间只报「最多打到 1 血」：不切阶段、不回血",
                    anticipatedHp == 1 && !anticipatedTarget.isPhaseTwo()
                            && anticipatedTarget.getHp() == 1);
            check("预判之后保护还在（试算没把它消费掉）",
                    !anticipatedTarget.isPhaseTwo());
            anticipatedTarget.getDamage(anticipated);
            check("预判过之后真挨一下，才切阶段并回满",
                    anticipatedTarget.isPhaseTwo()
                            && anticipatedTarget.getHp() == anticipatedTarget.getHpMax());

            // ③ 保护只有一次：二阶段里再被清空血条就真的倒下
            reaver.setHp(1);
            DamageEvent secondLethal = new DamageEvent(attacker, reaver, probe);
            secondLethal.getDamage().setDamageAmount(9999L);
            reaver.getDamage(secondLethal);
            check("保护只有一次：二阶段里再被清空血条就真的倒下",
                    !reaver.isAlive() && reaver.getHp() == 0);

            // ④ 兜底路径：绕过伤害结算直接把血设成 0，updateSelf 也会补上切阶段
            cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver bypassed =
                    new cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver(125);
            bypassed.setHp(0);
            bypassed.updateSelf();
            check("绕过伤害结算的扣血（直接 setHp(0)）也被 updateSelf 兜底切阶段并回满",
                    bypassed.isPhaseTwo() && bypassed.getHp() == bypassed.getHpMax());
        } catch (Exception e) {
            check("盗火行者的切阶段逻辑不该抛异常，实际抛了：" + e, false);
            e.printStackTrace();
        }
    }

    /**
     * 阶段 0：给实体属性清单<b>上锁</b>（见 `project_analyses/ENTITY-ATTRIBUTE-SPLIT-2026-10.md` 第七节）。
     * <p>
     * 这是"拆分实体属性"这件事<b>收益最大的那一步，且零风险</b> —— 不搬任何字段，
     * 只用反射把两条生命周期规则变成看得见的断言：
     * <ol>
     *     <li>{@link LivingThing#copy()} 必须把每个<b>标量属性</b>都带过去；</li>
     *     <li>{@link LivingThing#whenFightEnds()} 必须把临时状态复位。</li>
     * </ol>
     * 做法是把实体的每个标量字段都填上一个"和默认值不可能撞车"的探测值，再 {@code copy()}
     * 逐个比对 —— 漏掉的字段不会静默通过，要么修好，要么<b>显式</b>写进
     * {@code knownNotCopied}（那串名字就是看得见的 TODO）。
     * <p>
     * 以后新增一个属性字段却忘了在复制构造器里带上，这条测试会立刻变红。
     */
    private static void testLivingThingAttributeContract() {
        section("实体属性契约（阶段 0）：copy() 全字段 + whenFightEnds() 复位");
        try {
            // 「临时属性」：用户 2026-10-03 拍板 —— 这些是<b>一次性 / 战中的加成</b>，
            // copy() 刻意<b>不</b>带过去；对应地 whenFightEnds() 必须把它们清零（两张表互补）。
            // 23 个按「谁在写」分三类（2026-10-03 逐个查过写入点，细节见
            // LivingThing#clearTemporaryAttributes 的 javadoc）：
            //   ① 效果写的 12 个 *Enhance*（六个通用强化效果成对加减 + 白厄变身自己加减）
            //   ② 预留未接线的 10 个（五元素穿透 / 五元素增伤，全项目零写入点）
            //   ③ 死字段 1 个（extraDamage —— 活的那套是 Skill#extraDamage，另一个字段）
            java.util.Set<String> temporaryAttributes = new java.util.HashSet<>(java.util.Arrays.asList(
                    "extraDamage",
                    "metalPenetration", "woodPenetration", "waterPenetration",
                    "firePenetration", "dirtPenetration",
                    "metalDamageEnhance", "woodDamageEnhance", "waterDamageEnhance",
                    "fireDamageEnhance", "dirtDamageEnhance",
                    "attackEnhancePercent", "defenceEnhancePercent", "speedEnhancePercent",
                    "hpEnhancePercent", "criticalDMGEnhancePercent", "criticalDMGEnhanceAmount",
                    "criticalRateEnhancePercent", "criticalRateEnhanceAmount",
                    "attackEnhanceAmount", "defenceEnhanceAmount",
                    "speedEnhanceAmount", "hpEnhanceAmount"));
            // 刻意<b>不</b>复制的另一类：只读试算标志。副本必须是一个干净的新个体
            java.util.Set<String> notCopiedOnPurpose = new java.util.HashSet<>(
                    java.util.Arrays.asList("anticipating"));
            // 「面板属性」：与临时属性互补的那一半 —— 由角色自己的构造器/派生算法写出来
            // （individualMultipleArea 由 ActorLiXiaoYan 按【燃点】算），<b>必须复制</b>、
            // <b>绝不能清零</b>（清成 0 会永久削弱该角色）。今天就这一个。
            java.util.Set<String> panelAttributes = new java.util.HashSet<>(
                    java.util.Arrays.asList("individualMultipleArea"));

            PlayerOne template = new PlayerOne(125);
            List<ScalarSlot> scalars = scalarSlotsOf(template);
            check("前提：反射扫到了实体的标量字段（不是 0 个）—— 实扫 " + scalars.size() + " 个",
                    scalars.size() >= 30);
            // 扫描范围必须**包含 @DataFlatten 组件**：字段搬进组件之后，若这一步漏了，
            // 那些字段会从扫描里静默消失、契约断言退化成"永远通过"（假绿）。
            // 光断言"数量 ≥ 30"抓不住它，所以这里点名要求组件里的字段也在扫描结果里。
            List<String> scannedNames = new ArrayList<>();
            for (ScalarSlot slot : scalars) {
                scannedNames.add(slot.field().getName());
            }
            check("前提：扫描范围下潜进了 @DataFlatten 组件（搬走的属性没从契约里静默消失）",
                    scannedNames.containsAll(java.util.Arrays.asList(
                            "metalResistance", "metalPenetration", "metalDamageEnhance",
                            "metalManaGrow", "penetration", "enhance", "criticalDMG")));

            for (ScalarSlot slot : scalars) {
                if (notCopiedOnPurpose.contains(slot.field().getName())) continue;
                slot.field().setAccessible(true);
                slot.field().set(slot.owner(), probeValueFor(slot.field().getType()));
            }
            LivingThing copied = template.copy();
            List<ScalarSlot> copiedScalars = scalarSlotsOf(copied);

            List<String> missed = new ArrayList<>();
            List<String> unexpectedlyMissed = new ArrayList<>();
            for (int i = 0; i < scalars.size(); i++) {
                ScalarSlot before = scalars.get(i);
                ScalarSlot after = copiedScalars.get(i);
                before.field().setAccessible(true);
                after.field().setAccessible(true);
                Object expected = before.field().get(before.owner());
                Object actual = after.field().get(after.owner());
                boolean same = expected == null ? actual == null : expected.equals(actual);
                if (!same) {
                    missed.add(before.field().getName());
                    if (!temporaryAttributes.contains(before.field().getName())) {
                        unexpectedlyMissed.add(before.field().getName());
                    }
                }
            }
            check("copy()：没有『临时属性』之外的新漏字段（新属性忘了复制会被这条抓住）—— 新漏 "
                    + unexpectedlyMissed, unexpectedlyMissed.isEmpty());
            check("copy()：不复制集合与『临时属性』清单完全一致（不带 " + missed.size() + " / 清单 "
                            + temporaryAttributes.size() + "）—— 实际不带的：" + missed,
                    missed.size() == temporaryAttributes.size() && temporaryAttributes.containsAll(missed));

            check("copy()：面板属性 individualMultipleArea 被带过去了（它不在临时属性表里）",
                    copied.getIndividualMultipleArea() == template.getIndividualMultipleArea());

            List<String> staleNames = new ArrayList<>();
            for (String name : temporaryAttributes) {
                boolean found = false;
                for (ScalarSlot slot : scalars) {
                    if (slot.field().getName().equals(name)) found = true;
                }
                if (!found) staleNames.add(name);
            }
            check("copy()：临时属性清单里的名字都真的存在（字段改名后清单不会静默失效）—— 失效的 "
                    + staleNames, staleNames.isEmpty());

            for (int i = 0; i < scalars.size(); i++) {
                ScalarSlot before = scalars.get(i);
                if (!notCopiedOnPurpose.contains(before.field().getName())) continue;
                ScalarSlot after = copiedScalars.get(i);
                after.field().setAccessible(true);
                check("copy()：只读试算标志「" + before.field().getName() + "」刻意不带过去（副本要是干净的新个体）",
                        Boolean.FALSE.equals(after.field().get(after.owner())));
            }

            // ---- whenFightEnds()：临时状态必须复位 ----
            PlayerOne resetProbe = new PlayerOne(125);
            long hpMaxBefore = resetProbe.getHpMax();
            resetProbe.setHp(1);
            resetProbe.setPresentTurn(new cn.gfhnv.game.system.fight.TurnEntry(
                    resetProbe, java.math.BigDecimal.ONE, java.math.BigDecimal.ZERO));
            resetProbe.addDamageReduction(new DamageReductionSource("阶段0探针"), 0.5);
            for (cn.gfhnv.game.system.mana.Mana mana : resetProbe.getManas()) mana.setAmount(0);
            resetProbe.whenFightEnds();

            check("whenFightEnds()：生命补满", resetProbe.getHp() == hpMaxBefore);
            check("whenFightEnds()：当前回合被复位", resetProbe.getPresentTurn() == null);
            check("whenFightEnds()：效果列表被清空", resetProbe.getEntityEffectList().isEmpty());
            check("whenFightEnds()：法力回满", resetProbe.getManas().stream()
                    .allMatch(mana -> mana.getAmount() == mana.getAmountMax()));
            // 【已知缺口】PROJECT-ANALYSIS-2026-09 §6.4 N3：减伤没被清。
            // 这条断言记录的是"现状"，不是"应该" —— 阶段 2 把属性收进对象、各组件自带 reset() 之后，
            // 请把它翻成 isEmpty()，并同步删掉这段注释。
            check("whenFightEnds()：减伤被清空（技能挂上来的那些不走效果生命周期，不清会渗进下一局）",
                    resetProbe.getDamageReductions().isEmpty());

            // 用户 2026-10-03 问的："效果结算时不会重复减吗？"
            // **不会**：removeDamageReduction 是按【来源身份】在列表上 removeIf，
            // 不是数值累减 —— 所以"效果先摘自己的 + 统一清空"怎么叠加都不会重复扣。
            // 把两条路都走一遍，断言承伤倍率回到干净的 1.0（没残留、也没被减过头）。
            LivingThing reductionProbe = new PlayerOne(125);
            DamageReductionSource sharedReduction = new DamageReductionSource("重复扣探针");
            reductionProbe.addDamageReduction(sharedReduction, 0.5);
            reductionProbe.removeDamageReduction(sharedReduction);   // 效果自己的 whenLastTimeEnd 走这条
            reductionProbe.clearDamageReductions();                  // whenFightEnds 走这条
            reductionProbe.clearDamageReductions();                  // 再清一遍也不翻车
            check("减伤清理是幂等的：效果先摘 + 统一清空 + 再清一遍 → 列表空、承伤倍率回到 1.0（没有「重复减」）",
                    reductionProbe.getDamageReductions().isEmpty()
                            && reductionProbe.getDamageTakenMultiplier() == 1.0);

            // ---- 「临时属性」的重置契约 ----
            // 用户 2026-10-03 拍板：上面那 24 个是临时属性，所以 copy() 不带。
            // 但"临时"的另一半是<b>有重置义务</b>（见 ENTITY-ATTRIBUTE-SPLIT-2026-10 第二节的生命周期分类）：
            // 整场结束时它们必须回到默认值，否则会渗进下一局。
            PlayerOne temporaryProbe = new PlayerOne(125);
            List<ScalarSlot> temporarySlots = scalarSlotsOf(temporaryProbe);
            for (ScalarSlot slot : temporarySlots) {
                if (!temporaryAttributes.contains(slot.field().getName())) continue;
                slot.field().setAccessible(true);
                slot.field().set(slot.owner(), probeValueFor(slot.field().getType()));
            }
            for (ScalarSlot slot : temporarySlots) {
                if (!temporaryAttributes.contains(slot.field().getName())) continue;
                slot.field().setAccessible(true);
                check("前提：临时属性「" + slot.field().getName() + "」确实被设成了非默认值",
                        !isDefaultValue(slot.field().get(slot.owner())));
            }
            temporaryProbe.whenFightEnds();
            List<String> notReset = new ArrayList<>();
            for (ScalarSlot slot : temporarySlots) {
                if (!temporaryAttributes.contains(slot.field().getName())) continue;
                slot.field().setAccessible(true);
                if (!isDefaultValue(slot.field().get(slot.owner()))) notReset.add(slot.field().getName());
            }
            check("whenFightEnds()：临时属性全部复位（没复位的 = 会渗进下一局的）—— 没复位的 "
                    + notReset, notReset.isEmpty());

            // 面板属性反过来：整场结束**不能**把它清掉，否则那个角色被永久削弱
            PlayerOne panelProbe = new PlayerOne(125);
            panelProbe.setIndividualMultipleArea(7.5d);
            panelProbe.whenFightEnds();
            check("whenFightEnds()：面板属性 individualMultipleArea 不被清零（清了就是永久削弱角色）",
                    panelProbe.getIndividualMultipleArea() == 7.5d);
        } catch (Exception e) {
            check("实体属性契约测试不该抛异常，实际抛了：" + e, false);
            e.printStackTrace();
        }
    }

    /**
     * @param owner 目标类
     * @return {@code owner} <b>自己声明</b>的标量实例字段（跳过 static、容器与各种引用）
     */
    private static List<java.lang.reflect.Field> scalarFieldsOf(Class<?> owner) {
        List<java.lang.reflect.Field> result = new ArrayList<>();
        for (java.lang.reflect.Field field : owner.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            if (isScalarType(field.getType())) result.add(field);
        }
        return result;
    }

    /**
     * 实体身上的全部"标量属性槽位"：{@code LivingThing} 自己声明的 + 各 {@link DataFlatten} 组件里的。
     * <p>
     * 属性搬进组件之后，字段的"声明位置"从实体变成了组件 —— 但本测试要盯的是
     * "<b>属性有没有被复制 / 被复位</b>"，跟它住在哪个类里无关。少了这一步，
     * 搬走的字段会从扫描里静默消失，契约断言就会退化成"永远通过"（假绿）。
     * <p>
     * 范围刻意与 {@code LivingThing} 的字段清单一致：父类 {@code Entity} 的
     * {@code uuid} / {@code mass} 不在本契约里（{@code uuid} 本来就是 final、副本刻意换新的），
     * 顺手扫进来会变成两条假的"漏复制"。
     *
     * @param entity 实体实例
     * @return 每个标量属性一个槽位（顺序：实体自己的在前，然后是各组件）
     */
    private static List<ScalarSlot> scalarSlotsOf(LivingThing entity) {
        List<ScalarSlot> result = new ArrayList<>();
        for (java.lang.reflect.Field field : scalarFieldsOf(LivingThing.class)) {
            result.add(new ScalarSlot(entity, field));
        }
        for (Class<?> current = entity.getClass();
             current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (java.lang.reflect.Field field : current.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                if (!field.isAnnotationPresent(DataFlatten.class)) continue;
                if (isScalarType(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object component = field.get(entity);
                    if (component == null) continue;
                    for (java.lang.reflect.Field inner : scalarFieldsOf(component.getClass())) {
                        result.add(new ScalarSlot(component, inner));
                    }
                } catch (ReflectiveOperationException | RuntimeException e) {
                    check("反射应当能读到组件「" + field.getType().getSimpleName() + "」，实际抛了：" + e, false);
                }
            }
        }
        return result;
    }

    /**
     * @param type 字段类型
     * @return 是否属于"标量"（能被反射直接填探测值、也是漏复制缺陷的实际发生地）
     */
    private static boolean isScalarType(Class<?> type) {
        return type == double.class || type == long.class || type == int.class
                || type == boolean.class || type == String.class || type.isEnum();
    }

    /**
     * @param value 字段值
     * @return 是否是该类型的"默认值"（临时属性复位之后应该长成的样子）
     */
    private static boolean isDefaultValue(Object value) {
        if (value instanceof Double d) return d == 0d;
        if (value instanceof Long l) return l == 0L;
        if (value instanceof Integer i) return i == 0;
        if (value instanceof Boolean b) return !b;
        return value == null;
    }

    /**
     * 给标量字段挑一个"和默认值不可能撞车"的探测值。
     * <p>
     * 值本身不重要，<b>关键是它不能等于字段的默认值</b> —— 否则漏复制的字段会因为
     * "两边都是默认值"而假通过。所以这里用一组一眼能认出来的常量。
     *
     * @param type 字段类型
     * @return 探测值
     */
    private static Object probeValueFor(Class<?> type) {
        if (type == double.class) return 7.5d;
        if (type == long.class) return 4321L;
        if (type == int.class) return 4321;
        if (type == boolean.class) return true;
        if (type == String.class) return "契约探针";
        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            return constants.length > 1 ? constants[1] : constants[0];
        }
        return null;
    }

    /**
     * {@code target} 上是否挂着一个"能接住这个字段"的 setter。
     * <p>
     * 判据与 {@code DataBridge#trySetter} 完全一致：方法名 = {@code "set" + 字段名（首字母大写）}、
     * 只收一个参数、参数类型与字段类型相同。
     *
     * @param target 目标类（必须是字段所在类本身或它的子类 —— setter 都是 public 继承可见的）
     * @param field  字段
     * @return 有就返回 {@code true}
     */
    private static boolean hasMatchingSetter(Class<?> target, java.lang.reflect.Field field) {
        String expected = "set" + Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
        for (java.lang.reflect.Method method : target.getMethods()) {
            if (method.getName().equals(expected)
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == field.getType()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 李晓焰的【燃点】修复 + {@code World#fullIdOf} 的"同类多模板"问题（2026-09）。
     * <p>
     * 三处症状里，这里能断言的是两处（燃点下限、同类多模板 id）；
     * "大招在 7 层时倒扣额外伤害"与"重复开大泄漏监听器"属于技能内部行为，
     * 靠"加算/减算都用 wasHigh 快照"和"监听器绑定自己的效果实例"这两个写法保证，自测覆盖不到。
     */
    private static void testActorLiXiaoYan() {
        section("李晓焰：燃点下限与同类多模板 id 归一");

        ActorLiXiaoYan li = new ActorLiXiaoYan(125);

        // 免死会一次扣掉 MEMORIZE_COST 层：扣完必须夹在 0
        // （旧代码只夹上限，副本挨打时会把自己算成"模板值 − 10"，实测出现过 −7）
        li.setIgnition(ActorLiXiaoYan.Rule.memorizeTrigger());
        li.setIgnition(li.getIgnition() - ActorLiXiaoYan.Rule.memorizeCost());
        check("燃点扣到 0 而不是负数", li.getIgnition() == 0);
        li.setIgnition(-5);
        check("燃点被显式设成负数时夹到 0", li.getIgnition() == 0);

        // 上限随血量变化：满血 10，半血以下 15（getIgnitionMax 与 setIgnition 必须用同一判据）
        li.setHp(li.getHpMax());
        check("满血时燃点上限 = IGNITION_MAX", li.getIgnitionMax() == ActorLiXiaoYan.Rule.ignitionMax());
        li.setHp(li.getHpMax() / 4);
        check("低血时燃点上限 = LOW_HP_IGNITION_MAX", li.getIgnitionMax() == ActorLiXiaoYan.Rule.lowHpIgnitionMax());
        li.setIgnition(100);
        check("低血时燃点能一路设到上限", li.getIgnition() == ActorLiXiaoYan.Rule.lowHpIgnitionMax());

        testIgnitionMaxModifierChain();

        // 同一个类注册两条模板（【残破容器】/【完整容器】）时，
        // 运行时实例必须按"短名相同"补全 id，不能拿同类第一条硬套
        FlameReaver owner = new FlameReaver(150);
        BrokenContainer broken = new BrokenContainer(owner);
        BrokenContainer complete = new BrokenContainer(owner, BrokenContainer.Kind.COMPLETE);
        check("残破容器 id 归一到 brokenContainer",
                World.fullIdOf(broken).endsWith(":brokenContainer"));
        check("完整容器 id 归一到 completeContainer（不被同类第一条盖掉）",
                World.fullIdOf(complete).endsWith(":completeContainer"));
    }

    /**
     * 【燃点】上限修正链（{@link cn.gfhnv.game.interfaces.IModifyIgnitionMax}）—— 2026-10-03 新增的模组扩展点。
     * <p>
     * 模组「李晓焰加强」（{@code mods/liXiaoYanPlus}）就是靠它提高上限的，而 {@code mods/} 不参与自测编译，
     * 所以<b>这一步必须把"链本身"钉死</b>：① 链为空时数值与出厂值逐位相同（不能因为加了扩展点就变数值）；
     * ② 登记一个修正器之后，上限、{@code setIgnition} 的夹取、副本三者同时跟着变；
     * ③ 链是折叠语义（后一个拿前一个的结果）；④ 摘掉/清空之后回到出厂值。
     */
    private static void testIgnitionMaxModifierChain() {
        section("李晓焰：燃点上限修正链（模组扩展点）");

        ActorLiXiaoYan li = new ActorLiXiaoYan(125);
        int baseNormal = ActorLiXiaoYan.Rule.ignitionMax();
        int baseLow = ActorLiXiaoYan.Rule.lowHpIgnitionMax();

        // ① 出厂状态：链是空的，数值必须与"没有这个扩展点"时逐位相同
        check("燃点链：出厂时链是空的", ActorLiXiaoYan.getIgnitionMaxModifiers().isEmpty());
        li.setHp(li.getHpMax());
        check("燃点链：链为空时满血上限逐位等于出厂值（" + baseNormal + "）",
                li.getIgnitionMax() == baseNormal);
        li.setHp(li.getHpMax() / 4);
        check("燃点链：链为空时低血上限逐位等于出厂值（" + baseLow + "）",
                li.getIgnitionMax() == baseLow);

        // ② 登记一个 +5 的修正器：满血与低血两条分支都要跟着涨
        IModifyIgnitionMax plusFive =
                (baseMax, owner) -> baseMax + 5;
        ActorLiXiaoYan.addIgnitionMaxModifier(plusFive);
        check("燃点链：登记 null 与 DEFAULT 会被忽略（链长仍是 1）", ignoredModifiersAreRejected());
        check("燃点链：低血上限 = 出厂值 + 5（" + (baseLow + 5) + "）",
                li.getIgnitionMax() == baseLow + 5);
        li.setHp(li.getHpMax());
        check("燃点链：满血上限 = 出厂值 + 5（" + (baseNormal + 5) + "）",
                li.getIgnitionMax() == baseNormal + 5);
        li.setIgnition(9999);
        check("燃点链：setIgnition 夹的是修正后的上限（不是出厂值）",
                li.getIgnition() == baseNormal + 5);
        li.setHp(li.getHpMax() / 4);
        check("燃点链：副本也看得到链（静态链不用重新登记、copy() 不会漏）",
                ((ActorLiXiaoYan) li.copy()).getIgnitionMax() == baseLow + 5);

        // ③ 折叠语义：第二个修正器拿到的是第一个的返回值（(出厂值 + 5) × 2，不是 出厂值 × 2 + 5）
        IModifyIgnitionMax doubleIt = (baseMax, owner) -> baseMax * 2;
        ActorLiXiaoYan.addIgnitionMaxModifier(doubleIt);
        check("燃点链：两个修正器按登记顺序折叠（(低血上限 + 5) × 2 = " + ((baseLow + 5) * 2) + "）",
                li.getIgnitionMax() == (baseLow + 5) * 2);
        check("燃点链：修正器拿得到 owner（就是正在读上限的那个角色）",
                ownerPassedToModifierIsTheReader());
        check("燃点链：读上限不会把修正器消费掉（重复读结果一样）",
                li.getIgnitionMax() == (baseLow + 5) * 2 && li.getIgnitionMax() == (baseLow + 5) * 2);

        // ④ 摘掉与清空：必须回到出厂值，否则后面的用例看到的是被污染的角色
        check("燃点链：按对象身份摘掉第一个修正器", ActorLiXiaoYan.removeIgnitionMaxModifier(plusFive));
        check("燃点链：摘掉之后只剩 ×2（" + (baseLow * 2) + "）", li.getIgnitionMax() == baseLow * 2);
        check("燃点链：同一个修正器摘第二次返回 false（没有静默重复摘）",
                !ActorLiXiaoYan.removeIgnitionMaxModifier(plusFive));
        ActorLiXiaoYan.clearIgnitionMaxModifiers();
        check("燃点链收尾：清空之后链是空的，上限回到出厂值（后面用例看到干净状态）",
                ActorLiXiaoYan.getIgnitionMaxModifiers().isEmpty() && li.getIgnitionMax() == baseLow);
    }

    /**
     * @return {@code null} 与 {@link IModifyIgnitionMax#DEFAULT} 是不是都登记不进去
     */
    private static boolean ignoredModifiersAreRejected() {
        int before = ActorLiXiaoYan.getIgnitionMaxModifiers().size();
        ActorLiXiaoYan.addIgnitionMaxModifier(null);
        ActorLiXiaoYan.addIgnitionMaxModifier(IModifyIgnitionMax.DEFAULT);
        return ActorLiXiaoYan.getIgnitionMaxModifiers().size() == before;
    }

    /**
     * @return 修正器收到的 {@code owner} 是不是就是读上限的那个角色实例
     */
    private static boolean ownerPassedToModifierIsTheReader() {
        ActorLiXiaoYan probe = new ActorLiXiaoYan(125);
        List<ActorLiXiaoYan> seen = new ArrayList<>();
        IModifyIgnitionMax recorder = (baseMax, owner) -> {
            if (owner instanceof ActorLiXiaoYan actor) {
                seen.add(actor);
            }
            return baseMax;
        };
        ActorLiXiaoYan.addIgnitionMaxModifier(recorder);
        try {
            probe.getIgnitionMax();
            return seen.size() == 1 && seen.get(0) == probe;
        } finally {
            ActorLiXiaoYan.removeIgnitionMaxModifier(recorder);
        }
    }

    /**
     * 命令系统的 {@code @s} / {@code @p} 要跟着"当前行动者"走（2026-09 实测问题）。
     * <p>
     * 背景：{@link CommandManager#setPlayer} 只在 {@code GameMain} 的选人流程里调用，
     * 多角色队伍里<b>最后选的那个会一直占着"玩家"的位置</b> —— 日志里表现为
     * "酒剑仙的回合里敲 {@code /give @s …}，东西发给了白厄/卡厄斯兰那"。
     */
    private static void testFollowActor() {
        section("命令系统的 @s 跟随当前行动者");

        LivingThing previous = CommandManager.getPlayer();
        LivingThing ourFighter = new PlayerOne(125).copy();
        LivingThing enemy = new CommonInsect(150L).copy();

        CommandManager.setPlayer(ourFighter);
        CommandManager.followActor(enemy, true);
        check("我方行动者接手后，@s 指向它", CommandManager.getPlayer() == enemy);
        CommandManager.followActor(ourFighter, false);
        check("敌方回合不改 @s（否则 /hurt @s 会打到对面）", CommandManager.getPlayer() == enemy);
        CommandManager.followActor(null, true);
        check("行动者为 null 时不动 @s", CommandManager.getPlayer() == enemy);

        CommandManager.setPlayer(previous);
    }

    /**
     * NBT 数据层与 {@code /data} 命令（2026-09 新增）。
     * <p>
     * 覆盖三层：<b>SNBT 文本</b>（能不能解析/原样写回）、<b>路径</b>（{@code a.b[0].c}）、
     * <b>反射桥</b>（哪些字段进数据、写回会不会被 setter 钳制），最后跑两条真命令。
     * <p>
     * 这里特意断言了"<b>行为对象不进数据</b>"（{@code controller}/{@code force}）——
     * 那是这套设计最容易失控的地方：一旦让反射漫游进 controller 与闭包，
     * {@code /data get @s} 会变成几千行的内部实现倾倒。
     */
    private static void testDataCommand() {
        section("NBT 数据层与 /data 命令");

        // ① SNBT：解析、后缀、原样写回
        NbtTag parsed = Snbt.parse("{hp:20L,name:\"白厄\",alive:1b,rate:0.25d,list:[1,2,{x:3}]}");
        check("SNBT 能解析复合标签", parsed instanceof NbtCompound);
        NbtCompound compound = (NbtCompound) parsed;
        check("SNBT 的 20L 是长整", compound.get("hp") instanceof NbtLong && compound.get("hp").asLong() == 20L);
        check("SNBT 的字符串", "白厄".equals(compound.get("name").asString()));
        check("SNBT 的 1b 能当布尔读", compound.get("alive") != null && compound.get("alive").asBoolean());
        check("SNBT 的 0.25d 是双精度", compound.get("rate") != null && compound.get("rate").asDouble() == 0.25d);
        check("SNBT 的嵌套列表有 3 个元素",
                compound.get("list") instanceof NbtList list && list.size() == 3);
        check("SNBT 原样写回（键顺序与后缀都保留）",
                "{hp:20L,name:\"白厄\",alive:1b,rate:0.25d,list:[1,2,{x:3}]}".equals(parsed.toSnbt()));
        check("裸词当字符串（放宽，MC 要求加引号）", "hello".equals(Snbt.parse("hello").asString()));
        check("true 当字节 1", Snbt.parse("true").asLong() == 1L);
        expectSyntaxError("残缺的 SNBT 会报错（{hp:}）", () -> Snbt.parse("{hp:}"));
        expectSyntaxError("没闭合的复合标签会报错", () -> Snbt.parse("{hp:1"));

        // ② 路径
        NbtCompound tree = Snbt.parseCompound("{a:{b:[{c:1L},{c:2L}]}}");
        DataPath path = DataPath.parse("a.b[1].c");
        check("路径解析出 4 段", path.segments().size() == 4);
        check("路径能取到列表元素里的字段", path.get(tree) != null && path.get(tree).asLong() == 2L);
        check("路径不存在时返回 null", DataPath.parse("a.x").get(tree) == null);
        check("下标越界时返回 null", DataPath.parse("a.b[9]").get(tree) == null);
        expectSyntaxError("空下标会报错", () -> DataPath.parse("a[]"));

        // ③ 反射桥：读（数据 vs 行为）
        ActorLiXiaoYan probe = new ActorLiXiaoYan(125);
        NbtCompound data = DataBridge.toCompound(probe);
        check("数据里有 hp", data.get("hp") != null && data.get("hp").asLong() == probe.getHp());
        check("数据里有子类字段 ignition", data.get("ignition") != null && data.get("ignition").asLong() == probe.getIgnition());
        check("数据里有 name", data.get("name") != null && probe.getName().equals(data.get("name").asString()));
        // 数据名是对外 API：Java 字段名难看时用 @DataField 改名（见 TIPS §5.10）。
        // 这一批 Java 名已经订正过（2026-10-03），所以数据名与 Java 名现在一致 —— 但**数据名一个字都没变**。
        check("暴击率的数据名是 criticalRate（订正 Java 名之后仍然是这个键）",
                data.get("criticalRate") != null);
        check("存活标志的数据名是小写 alive（不是 Alive）",
                data.get("alive") != null && data.get("Alive") == null);
        // ⚠️ 这一条盯着**静默失效**：{@code DataBridge#trySetter} 是拿 Java 字段名拼 setter 名的
        // （{@code "set" + field.getName()}），所以字段改名时 setter 名必须跟着改 ——
        // 漏改的话 setter 找不到，写回会**退化成裸写字段**：命令照旧报成功、值也照样变，
        // 只是钳制与副作用没了（最阴的一种回归，2026-10-03 订正字段名时就差点踩上）。
        // 判据：把 setter 的名字换成反着的哨兵值 —— setter 生效时它会被**整体覆盖**，
        // 只裸写字段时哨兵还在。这里顺带把"这个字段真的能写"也验了。
        double beforeEnhance = probe.getEnhance();
        probe.setEnhance(1234.5d);
        check("写回契约：DataBridge#merge 走的是 setter（setter 生效时哨兵值被整体覆盖，"
                        + "不是只改了字段、把 setter 绕过去）",
                DataBridge.merge(probe, Snbt.parseCompound("{enhance:0.5}")) == 1
                        && probe.getEnhance() == 0.5d
                        && DataBridge.toCompound(probe).get("enhance").asDouble() == 0.5d);
        probe.setEnhance(beforeEnhance);
        check("改名之后照样能写（setter 是按 Java 字段名找的，所以 setCriticalRate 仍然命中）",
                DataBridge.merge(probe, Snbt.parseCompound("{criticalRate:0.5}")) == 1
                        && probe.getCriticalRate() == 0.5d
                        && DataBridge.toCompound(probe).get("criticalRate").asDouble() == 0.5d);
        check("效果列表与成长系数的数据名都换成了短名字",
                DataBridge.dataNames(probe.getClass()).containsAll(List.of(
                        "effects", "hpGrow", "attackGrow", "defenceGrow", "metalManaGrow")));
        // 上一条只钉住 criticalRate 一个；这里把规则推广到**每个**能写的标量字段（先扫实体自己，
        // 再下潜进 @DataFlatten 组件 —— 订正过名字的那 9 个正是靠这条一起兜住的）。
        // 两个例外（**只读**：dump 得出来、但从来不是"写入口"）：
        //   ignitionMax —— 派生值，getIgnitionMax() 按当前血量现算（低血 15 / 满血 10）；
        //   lastIgnition —— 内部记账（上一次的燃点，用来算【燃点】变动带来的面积增量）。
        // 它们本来就没有 setter，算进来只会变成假失败。
        java.util.Set<String> readOnlyScalars = new java.util.HashSet<>(
                java.util.Arrays.asList("ignitionMax", "lastIgnition"));
        List<String> missingSetters = new ArrayList<>();
        for (Class<?> type = probe.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                if (field.isAnnotationPresent(cn.gfhnv.game.data.NoData.class)) continue;
                if (field.isAnnotationPresent(cn.gfhnv.game.data.DataFlatten.class)) continue;
                if (!isScalarType(field.getType())) continue;
                if (java.lang.reflect.Modifier.isFinal(field.getModifiers())) continue;
                if (readOnlyScalars.contains(field.getName())) continue;
                if (hasMatchingSetter(probe.getClass(), field)) continue;
                missingSetters.add(field.getName());
            }
        }
        check("写回契约：每个 /data 能写的标量字段都挂着一个同名 setter（字段改名时 setter 必须跟着改）"
                + "—— 缺 setter 的：" + missingSetters, missingSetters.isEmpty());
        // 这条盯着一个踩过的坑：字段搬进 @DataFlatten 组件之后，组件类型必须进
        // DataBridge#isDataObject 白名单 —— 漏了的话整个组件在 /data 里**静默消失**
        // （不报错、只是查不到）。所以这里断言的是"组件字段真的 dump 出来了一个"，
        // 而不只是"数据名清单里有它"（清单是按字段算的，加不加白名单都一样）。
        // 同时确认组件本身不是一个键（键名是**无前缀**的组件字段名）。
        check("组件里的字段照常进数据视图（@DataFlatten 没有因为白名单漏了组件类型而静默消失）",
                data.get("metalResistance") != null && data.get("attributes") == null
                        && data.get("elements") == null);
        // 从实体搬进组件的三个「全局组」面板属性，也必须是**顶层**键（不是 attributes.xxx）
        check("搬进组件的全局组面板属性仍是顶层键（penetration / enhance / criticalDMG 不带前缀）",
                data.get("penetration") != null && data.get("enhance") != null
                        && data.get("criticalDMG") != null);
        check("试算标志 anticipating 被 @NoData 排除（它只在试算期间为 true，dump 出来是噪音）",
                !DataBridge.dataNames(probe.getClass()).contains("anticipating"));
        check("uuid 可见（只读，因为它是 final）", data.get("uuid") != null);
        check("controller 不进数据（行为不是数据）", data.get("controller") == null);
        check("物理对象不进数据", data.get("force") == null && data.get("velocity") == null);
        check("战斗引用不进数据", data.get("participateFight") == null);
        // 这条盯着一个踩过的坑：DataBridge 里若定义了一个叫 Slot 的内部类型，
        // 会把 import 的 inventory.Slot 遮住，背包就 dump 成空壳了。
        check("背包格照常进数据（Slot 没被同名内部类遮掉）",
                data.get("inventory") instanceof NbtCompound inventory && inventory.get("slots") instanceof NbtList slots
                        && slots.size() == 63);
        // "被引用的实体只留 uuid"那条规则**只对非根对象生效**：根对象照旧全量展开。
        // 这两条与下面 testNestedEntityReference() 里那几条是一对 —— 少了这里，
        // 把 /data get entity @s 本身压成一个 uuid 也没人拦得住。
        check("根对象仍然全量展开（不是只剩一个 uuid） —— 关键数目 " + data.size(),
                data.size() > 10 && data.get("name") != null && data.get("manas") != null);
        check("根对象自己的 uuid 照旧看得见（与\"被引用的实体只给 uuid\"不冲突）",
                data.get("uuid") != null);
        // 这条盯着"参数节点被挂两次"：data 下不该出现游离的「值」子节点。
        check("/data 下没有游离的「值」子节点",
                !CommandManager.getDispatcher().getCommandNode("data").getChildrenNames().contains("值"));

        // ④ 反射桥：写（能走 setter 就被 setter 的钳制管住）
        probe.setHp(probe.getHpMax());
        DataBridge.merge(probe, Snbt.parseCompound("{hp:99999999}"));
        check("写 hp 会被 setHp 钳到生命上限", probe.getHp() == probe.getHpMax());
        DataBridge.merge(probe, Snbt.parseCompound("{ignition:99}"));
        check("写 ignition 会被夹到上限", probe.getIgnition() == probe.getIgnitionMax());
        DataBridge.merge(probe, Snbt.parseCompound("{ignition:-5}"));
        check("写 ignition 负数会被夹到 0", probe.getIgnition() == 0);
        expectSyntaxError("写不存在的字段会报错", () -> DataBridge.merge(probe, Snbt.parseCompound("{noSuchField:1}")));
        expectSyntaxError("写 final 字段（uuid）会被拒绝", () -> DataBridge.merge(probe, Snbt.parseCompound("{uuid:\"x\"}")));

        // ⑤ 命令层（走真实解析与执行）
        LivingThing previous = CommandManager.getPlayer();
        CommandManager.setPlayer(probe);
        run("data get entity @s", true);
        run("data get entity @s hp", true);
        run("data get entity @s noSuchThing", false);
        run("data merge entity @s {hp:1}", true);
        check("命令写入真的生效了", probe.getHp() == 1L);
        run("data merge entity @s {hp: 2}", true);
        check("带空格的 NBT 也能解析（readBalanced）", probe.getHp() == 2L);
        run("data merge entity @s {noSuchField:1}", false);
        run("data merge entity @s {", false);
        run("data get entity @e[type=ThisTypeDoesNotExist]", false);
        run("data", false);
        CommandManager.setPlayer(previous);
    }

    /**
     * {@code /data get} 的<b>引用呈现</b>：根对象全量、被引用的实体只剩 {@code uuid}。
     * <p>
     * 起因是用户实测：{@code /execute as @e[type=FlameReaver] run data get entity @s} 打出来
     * {@code cloudOfDeathSummons:[{…一整个容器的完整 NBT…}]}、{@code lastAttacker:{…又套一层
     * 卡厄斯兰那的完整 NBT，含 skills:[…] 与 extraTurns:7…}} —— <b>引用套引用</b>，一条命令 5000+ 字符。
     * 那些被引用的实体自己有完整的字段表，想细看把它们当根再查一次就行
     * （{@code /data get entity <它> …}），没必要在别人的 dump 里再抄一遍。
     * <p>
     * 这里用探针摆两个形状：普通引用，以及<b>指回自己</b>的引用（后者同时证明这条规则
     * 在类型上就短路了、不递归 —— 真去递归会直接栈溢出）。
     * <p>
     * ⚠️ 只读导出变了，<b>写回没变</b>：{@code merge} / {@code modify} 走的是活对象，
     * 键名与语义一个都没动（这条由上面 {@code testDataCommand} 与 {@code testDataModify} 兜着）。
     */
    private static void testNestedEntityReference() {
        section("被引用的实体在 /data 里只留 uuid");

        NestedReferenceProbeEntity owner = new NestedReferenceProbeEntity("引用探针", "probe:nestedReference");
        NestedReferenceProbeEntity buddy = new NestedReferenceProbeEntity("被引用者", "probe:nestedReferenceBuddy");
        owner.setBuddy(buddy);
        owner.setSelf(owner);

        // 先证明"被引用者自己是个完整对象"：把它当根 dump，字段远不止一个 uuid。
        // 否则下面那条"只给 uuid"可能只是因为 buddy 本来就没什么字段，证明不了什么。
        NbtCompound buddyAsRoot = DataBridge.toCompound(buddy);
        check("对照组：被引用的那个实体自己当根时是全量展开的（" + buddyAsRoot.size() + " 个键）",
                buddyAsRoot.size() > 10 && buddyAsRoot.get("name") != null);

        NbtCompound dump = DataBridge.toCompound(owner);
        String text = dump.toSnbt();
        System.out.println("  [信息] 引用探针的键（" + dump.size() + " 个）：" + dump.keySet());
        System.out.println("  [信息] 根里的 buddy = " + dump.get("buddy")
                + "，根里的 self = " + dump.get("self"));

        check("根对象照旧全量展开：引用探针 dump 出来 " + dump.size()
                        + " 个键（不是被压成一个 uuid）",
                dump.size() > 10 && dump.get("name") != null && dump.get("hp") != null);
        check("被引用的实体只输出 uuid（buddy 那一格只有 uuid 一个键）—— 实际 "
                        + (dump.get("buddy") == null ? "（无）" : dump.get("buddy").toSnbt()),
                dump.get("buddy") instanceof NbtCompound buddyTag && buddyTag.size() == 1
                        && buddyTag.get("uuid") != null
                        && buddyTag.get("uuid").asString().equals(buddy.getUUID()));
        check("被引用者自己的字段一个都没混进来（整份 dump 里搜不到它的名字）—— 被引用者叫「"
                        + buddy.getName() + "」",
                !text.contains(buddy.getName()));
        check("指回自己的引用也只给 uuid（这条不成立就是递归，会直接栈溢出）",
                dump.get("self") instanceof NbtCompound selfTag && selfTag.size() == 1
                        && selfTag.get("uuid") != null
                        && selfTag.get("uuid").asString().equals(owner.getUUID()));
        check("同一份数据 dump 两次逐字符一致",
                text.equals(DataBridge.toCompound(owner).toSnbt()));
        check("写法上与只读导出无关的两条：短标识取自 uuid 前 6 位、长度够区分",
                owner.getShortUuid().equals(owner.getUUID().substring(0, 6))
                        && owner.getShortUuid().length() == 6);
    }

    /**
     * {@code /data modify}：改到路径深处（列表元素里的字段、映射、标量列表的增删）。
     * <p>
     * 这里刻意<b>不</b>去改 {@code skills[...]}：{@code controller} 不在"数据对象"名单里
     * （它是行为不是数据），所以技能表本来就不可达 —— 这是设计，不是 bug。
     */
    private static void testDataModify() {
        section("NBT 路径写入与 /data modify");

        LivingThing previous = CommandManager.getPlayer();
        ActorLiXiaoYan probe = new ActorLiXiaoYan(125);
        CommandManager.setPlayer(probe);

        // ① 深处写入：列表元素里的字段（manas[3] = 火法力）
        run("data modify entity @s manas[3].amount set 100", true);
        check("modify set 能改到 manas[3].amount", probe.getManas().get(3).getAmount() == 100.0d);
        run("data modify entity @s hp set 1", true);
        check("modify set 走 setter（hp 被钳制后确实是 1）", probe.getHp() == 1L);
        run("data modify entity @s ignition set 7", true);
        check("modify set 改 ignition", probe.getIgnition() == 7);
        run("data modify entity @s ignition set 12345", true);
        check("modify set 同样被 setIgnition 夹住", probe.getIgnition() == probe.getIgnitionMax());

        // ② 给身上挂一个效果，再改"效果列表里的元素"
        run("effect @s add frozen", true);
        check("测试前提：身上有一个效果", probe.getEntityEffectList().size() == 1);
        run("data modify entity @s effects[0].level set 3", true);
        check("modify set 能改到 effects[0].level", probe.getEntityEffectList().get(0).getLevel() == 3);

        // ③ 标量列表允许增删（枚举也算标量）；对象列表必须被拒
        int tagsBefore = probe.getEntityEffectList().get(0).getEffectTagsList().size();
        run("data modify entity @s effects[0].effectTagsList append POSITIVE", true);
        check("modify append 往枚举列表里加了一个",
                probe.getEntityEffectList().get(0).getEffectTagsList().size() == tagsBefore + 1);
        run("data modify entity @s effects[0].effectTagsList insert 0 POSITIVE", true);
        check("modify insert 插到了开头",
                probe.getEntityEffectList().get(0).getEffectTagsList().size() == tagsBefore + 2);
        run("data modify entity @s effects[0].effectTagsList prepend POSITIVE", true);
        check("modify prepend 也生效",
                probe.getEntityEffectList().get(0).getEffectTagsList().size() == tagsBefore + 3);
        run("data modify entity @s effects append {id:\"x\"}", false);

        // ④ 各种"应该报错"的情况
        run("data modify entity @s noSuch.path set 1", false);
        run("data modify entity @s manas[99].amount set 1", false);
        run("data modify entity @s manas set 1", false);
        run("data modify entity @s ignition merge {x:1}", false);
        run("data modify entity @s manas[3].amount set abc", false);
        run("data modify entity @s", false);
        run("data remove entity @s hp", false);

        run("effect @s remove all", true);
        CommandManager.setPlayer(previous);
    }

    /**
     * {@code {k:v}} 过滤、{@code [a:b]} 切片、{@code nbt=} 选择器、{@code storage}、{@code execute if data}
     * —— 2026-10-03 第二批（用户要的"MC 味道"剩下的部分）。
     */
    private static void testDataFiltersAndStorage() {
        section("NBT 过滤/切片/storage/if data");

        /* ① 路径：过滤与切片（纯标签层） */
        NbtCompound tree = Snbt.parseCompound(
                "{items:[{id:\"a\",n:1},{id:\"b\",n:2},{id:\"c\",n:3}]}");
        DataPath filtered = DataPath.parse("items[{id:\"b\"}].n");
        check("过滤 + 取字段", filtered.get(tree) != null && filtered.get(tree).asLong() == 2);
        check("过滤没命中 → null", DataPath.parse("items[{id:\"z\"}]").get(tree) == null);
        check("切片 [0:2] 拿到前两个",
                DataPath.parse("items[0:2]").get(tree) instanceof NbtList first && first.size() == 2);
        check("切片 [1:] 拿到后两个",
                DataPath.parse("items[1:]").get(tree) instanceof NbtList rest && rest.size() == 2);
        check("切片 [:] 拿到全部",
                DataPath.parse("items[:]").get(tree) instanceof NbtList all && all.size() == 3);
        expectSyntaxError("过滤只支持单键", () -> DataPath.parse("items[{id:\"a\",n:1}]"));
        DataPath.parse("items[{id:\"a\"}].n").setIn(tree, new NbtInt(99));
        check("过滤路径能定位到元素并写进去", DataPath.parse("items[{id:\"a\"}].n").get(tree).asLong() == 99);
        expectSyntaxError("切片只能读不能写", () -> DataPath.parse("items[0:1]").setIn(tree, new NbtInt(1)));

        /* ② storage（内存里的全局数据） */
        DataStorage.clear();
        run("data merge storage aiTest {count:1,name:\"测试\"}", true);
        run("data get storage aiTest count", true);
        run("data modify storage aiTest count set 7", true);
        check("storage：set 生效", DataStorage.of("aiTest").get("count") != null
                && DataStorage.of("aiTest").get("count").asLong() == 7);
        check("storage：merge 保留没提到的键", "测试".equals(DataStorage.of("aiTest").get("name").asString()));
        run("data merge storage aiTest {list:[]}", true);
        run("data modify storage aiTest list append 1", true);
        run("data modify storage aiTest list append 2", true);
        check("storage：append 进了列表", DataStorage.of("aiTest").get("list") instanceof NbtList list
                && list.size() == 2);
        run("data modify storage aiTest list insert 0 0", true);
        check("storage：insert 插到了开头",
                DataStorage.of("aiTest").get("list") instanceof NbtList after
                        && after.get(0).asLong() == 0 && after.size() == 3);
        run("data get storage noSuchStore", false);
        run("data get storage aiTest noSuchKey", false);
        run("data modify storage aiTest count append 3", false);

        /* ③ 选择器 nbt=：比的是"数据视图"，而且是标签精确比较 */
        FlameReaver boss = new FlameReaver(150);
        ActorLiXiaoYan hero = new ActorLiXiaoYan(125);
        List<LivingThing> enemies = new ArrayList<>();
        enemies.add(boss);
        List<LivingThing> fighters = new ArrayList<>();
        fighters.add(hero);
        Fight fight = new Fight(enemies, new ArrayList<>(), fighters);

        CommandResult byLevel = CommandManager.executeResult("list @e[nbt={level:125L}]", fight);
        check("nbt=：按长整字段筛出 125 级的目标", byLevel.isSuccess() && byLevel.getResult() == 1);
        // 注意：筛不到时选择器会报「没有选中任何生物」，所以这里断言的是"失败"而不是"影响 0 个"
        CommandResult byInt = CommandManager.executeResult("list @e[nbt={level:125}]", fight);
        check("nbt=：125 与 125L 不是同一个标签，所以筛不到（选择器报没选中）", !byInt.isSuccess());
        CommandResult byGrow = CommandManager.executeResult("list @e[nbt={hpGrow:58.0d}]", fight);
        check("nbt=：按 double 字段筛", byGrow.isSuccess() && byGrow.getResult() == 1);
        CommandResult byBoth = CommandManager.executeResult(
                "list @e[type=ActorLiXiaoYan,nbt={hpGrow:58.0d}]", fight);
        check("nbt= 能和 type= 一起用（都对才选中）", byBoth.isSuccess() && byBoth.getResult() == 1);
        CommandResult byMismatch = CommandManager.executeResult(
                "list @e[type=FlameReaver,nbt={hpGrow:58.0d}]", fight);
        check("nbt= 与 type= 都写对才选中（BOSS 不是 58 成长）", !byMismatch.isSuccess());

        /* ④ execute if data */
        LivingThing previous = CommandManager.getPlayer();
        CommandManager.setPlayer(hero);
        long before = hero.getHp();
        run("execute if data entity @s level run hurt @s 1", true);
        check("if data：条件成立时内层命令真的跑了", hero.getHp() == before - 1);
        run("execute if data entity @s noSuchField run hurt @s 1", true);
        check("if data：条件不成立时内层命令没有跑", hero.getHp() == before - 1);
        run("execute if data storage aiTest count run data modify storage aiTest count set 42", true);
        check("if data storage：条件成立后存储位被改", DataStorage.of("aiTest").get("count").asLong() == 42);
        run("execute if data storage aiTest noSuchKey run data modify storage aiTest count set 0", true);
        check("if data storage：条件不成立时不改", DataStorage.of("aiTest").get("count").asLong() == 42);
        run("execute if data entity @s", false);

        /* ⑤ 实体侧：过滤读得到、切片只读不写 */
        run("effect @s add frozen", true);
        run("data get entity @s effects[{id:\"game_official_content:frozenEffect\"}].level", true);
        run("data get entity @s manas[0:2]", true);
        run("data modify entity @s manas[0:2] set 1", false);
        run("data get entity @s manas[{amount:516.0d}].amount", true);
        run("effect @s remove all", true);

        /* ⑥ 路径走不通时，报错要说清"断在哪一段、为什么"。
         * 只说"没有数据 / 没有命中"的话，用的人分不清是字段名打错、下标越界，还是过滤没命中
         * ——典型场景：想 /data modify 一个身上还没有的效果，报错看起来像是命令语法不对。
         * 两条路要分别覆盖：/data get 走标签树，/data modify 走活对象。 */
        run("effect @s add frozen", true);
        String getMiss = errorOf("data get entity @s effects[{id:\"no:suchEffect\"}].level");
        check("get 过滤没命中：报错里列出了该列表现有的 id",
                getMiss.contains("game_official_content:frozenEffect"));
        String modifyMiss = errorOf("data modify entity @s effects[{id:\"no:suchEffect\"}].level set 1");
        check("modify 过滤没命中：报错里也列出了该列表现有的 id",
                modifyMiss.contains("game_official_content:frozenEffect"));
        String fieldMiss = errorOf("data get entity @s noSuchField");
        check("get 字段名打错：报错里列出这一层有哪些键",
                fieldMiss.contains("没有「noSuchField」") && fieldMiss.contains("uuid"));
        String indexMiss = errorOf("data get entity @s manas[99].amount");
        check("get 下标越界：报错里说出这个列表有几个元素", indexMiss.contains("5 个元素"));
        // 反向确认：命中的那条不会因为多了这段提示而改变行为
        run("data get entity @s effects[{id:\"game_official_content:frozenEffect\"}].level", true);
        run("effect @s remove all", true);

        /* ⑦ 2026-10-03 订正 9 个 Java 名之后，**`/data modify` 必须还能写这几个键**。
         * 这是改名最容易出事的地方：写回是按 Java 字段名拼 setter 名的，setter 名字没跟上就
         * 静默退化成"裸写字段"（命令照旧成功、值也变，只是钳制与副作用没了）。 */
        ActorLiXiaoYan renameProbe = new ActorLiXiaoYan(125);
        LivingThing renamePrevious = CommandManager.getPlayer();
        CommandManager.setPlayer(renameProbe);
        run("data modify entity @s hpGrow set 61", true);
        run("data modify entity @s attackGrow set 23", true);
        run("data modify entity @s defenceGrow set 6", true);
        run("data modify entity @s metalManaGrow set 7", true);
        run("data modify entity @s criticalRate set 0.25", true);
        run("data modify entity @s alive set 0b", true);
        // alive 要**直接读字段**：isAlive() 会按 getHp() 现算并覆盖字段（满血 → 永远 true），
        // 拿它当判据就等于没测（这条只想证明"这个键写得进去、而且走的是 setter"）。
        boolean aliveField = false;
        try {
            java.lang.reflect.Field aliveFieldRef = LivingThing.class.getDeclaredField("alive");
            aliveFieldRef.setAccessible(true);
            aliveField = aliveFieldRef.getBoolean(renameProbe);
        } catch (ReflectiveOperationException e) {
            check("反射读不到 LivingThing.alive 字段：" + e, false);
        }
        check("订正 Java 名之后 /data modify 仍能写这几个键（各自落在正确的字段上）",
                renameProbe.getHpGrow() == 61.0
                        && renameProbe.getAttackGrow() == 23.0
                        && renameProbe.getDefenceGrow() == 6.0
                        && renameProbe.getMetalManaGrow() == 7.0
                        && renameProbe.getCriticalRate() == 0.25d
                        && !aliveField);
        CommandManager.setPlayer(renamePrevious);

        CommandManager.setPlayer(previous);
    }

    /* ------------------------------------------------------------------
     * 技能数值外部加载（阶段 2）
     * ------------------------------------------------------------------ */

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

        check("别名：配置里认 element 与 manaGrow（规范名仍是数据名 elementSort / *ManaGrow）",
                "elementSort".equals(DataKeys.canonical("element", false))
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
                if (!"inventorySlots".equals(key) && !declared.containsKey(key)) {
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
                "coreflame", "coreflame_max", "soulscorch", "scourge", "scourge_max",
                "extraAbilityTier"));
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
            if ("game_official_content:phainon".equals(id)
                    || "game_official_content:actorLiXiaoYan".equals(id)) {
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
                            "{\"entities\":{\"game_official_content:playerOne\":{\"base\":{\"speed\":200}}}}",
                            "自测-只改速度");
            check("补丁：只写一个键时，没有报错也没有跳过 —— " + one.errors() + one.skippedEntries(),
                    one.isClean() && one.appliedEntries().size() == 1);
            check("补丁：速度真的变成 200（旧 " + oldSpeed + "）", probe.getSpeed() == 200L);
            check("补丁：没写的字段一个字都没动（生命上限仍是 " + oldHpMax + "）",
                    probe.getHpMax() == oldHpMax);
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Snapshot.of(probe).restore();

            /* ⑤-3 改成长系数必须连带重算：派生值只在 setLevel 里算，不补一次就是"改了没反应"
             *     （注意**不要**同时写 derived.hpMax —— 那是有意覆盖公式结果的写法，
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
                            + "{\"derived\":{\"hpMax\":12345,\"attack\":777}}}}", "自测-派生值");
            check("补丁：derived 的固定值覆盖公式结果（hpMax=" + derived.getHpMax()
                            + "，attack=" + derived.getAttack() + "，旧攻击 " + attackBeforeDerived + "）",
                    derived.getHpMax() == 12345L && derived.getAttack() == 777L);
            // setHp 会夹到 getHpMax()，所以"先设上限、后设当前血量"是唯一正确的顺序
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.applyJson(
                    "{\"entities\":{\"game_official_content:playerOne\":"
                            + "{\"derived\":{\"hp\":99999}}}}", "自测-血量顺序");
            check("补丁：只写 hp 时被现有上限夹住（顺序写死：hpMax 先、hp 后）—— hp="
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
                                    + "\"game_official_content:playerOne\":{\"base\":{\"speed\":\"快\",\"nope\":1}},"
                                    + "\"game_official_content:noSuchEntity\":{\"base\":{\"speed\":1}},"
                                    + "\"commonInsect\":{\"base\":{\"speed\":95}}"
                                    + "}}", "自测-容错");
            String brokenText = broken.errors() + " " + broken.skippedEntries();
            check("容错：一个坏键不再废掉整份配置（同一份里其余项照常生效）—— 跳过 "
                            + broken.skippedEntries() + "，错误 " + broken.errors(),
                    broken.skippedEntries().size() == 2 && broken.errors().size() == 1
                            && broken.appliedEntries().size() == 1);
            check("容错：类型不对的项被点名道姓（键名 + 实际类型）",
                    brokenText.contains("base.speed") && brokenText.contains("字符串"));
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
             *        与 extraDamage（死字段）。 */
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Report notSupported =
                    cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.applyJson(
                            "{\"entities\":{\"game_official_content:playerOne\":{\"base\":"
                                    + "{\"metalPenetration\":0.5},"
                                    + "\"temporary\":{\"criticalRate\":0.5}}}}",
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
            check("不支持键：extraDamage 被点名，而且说清它是「死字段」",
                    cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.applyJson(
                                    "{\"entities\":{\"game_official_content:playerOne\":"
                                            + "{\"base\":{\"extraDamage\":5}}}}", "自测-死字段")
                            .skippedEntries().toString().contains("死字段"));
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
             *      这是**反向断言**：以前只有"生成器写出去的 ⊆ META"，方向恰好是反的，
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
                    writtenKeys.size() == 30 && notWritten.isEmpty());
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
            double oldDmg = panel.getCriticalDMG();
            double oldEnhance = panel.getEnhance();
            double oldPenetration = panel.getPenetration();
            double oldLoss = panel.getDefenseLoss();
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.applyJson(
                    "{\"entities\":{\"game_official_content:playerOne\":{\"base\":"
                            + "{\"criticalRate\":0.5,\"criticalDMG\":1.5,\"enhance\":0.25,"
                            + "\"penetration\":0.3,\"defenseLoss\":0.1}}}}", "自测-面板键");
            check("面板键：criticalRate / criticalDMG / enhance / penetration / defenseLoss 真的写进去了"
                            + "（以前它们在 META 里声明能配，写进去一点反应都没有）",
                    panel.getCriticalRate() == 0.5 && panel.getCriticalDMG() == 1.5
                            && panel.getEnhance() == 0.25 && panel.getPenetration() == 0.3
                            && panel.getDefenseLoss() == 0.1);
            LivingThing panelCopy = panel.copy();
            check("面板键：copy() 会把它们带过去（面板属性，不是打完一局就清零的临时属性）",
                    panelCopy.getCriticalRate() == 0.5 && panelCopy.getEnhance() == 0.25
                            && panelCopy.getPenetration() == 0.3);
            panel.setCriticalRate(oldRate);
            panel.setCriticalDMG(oldDmg);
            panel.setEnhance(oldEnhance);
            panel.setPenetration(oldPenetration);
            panel.setDefenseLoss(oldLoss);
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
                    "{\"entities\":{\"game_official_content:playerOne\":{\"base\":{\"speed\":321}}}}",
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
                            && healedRules.has("flameReaver") && healedRules.has("actorLiXiaoYan"));
            check("落盘自愈（D5）：用户已经写的值一个字都不许覆盖（speed 321 / hpBase 999 原样）—— 实际 "
                            + healedEntity.getJSONObject("entities")
                            .getJSONObject("game_official_content:playerOne")
                            .getJSONObject("base").opt("speed") + " / "
                            + healedRules.getJSONObject("formula").opt("hpBase"),
                    healedEntity.getJSONObject("entities")
                            .getJSONObject("game_official_content:playerOne")
                            .getJSONObject("base").optInt("speed") == 321
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
                    if (left.optLong("hpMax", -1) != right.optLong("hpMax", -1)) {
                        referenceMismatch.add(id + "/hpMax " + left.opt("hpMax") + " vs "
                                + right.opt("hpMax"));
                    }
                }
            }
            check("参考副本（①）：与 EntityData.json 是同一个口径（每个官方模板都在、base/derived 的键"
                            + "逐位相同、hpMax 数值相同）—— 不一致的 " + referenceMismatch,
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

    /* ------------------------------------------------------------------
     * 技能具名系数（"倍率可变"的那一类）
     * ------------------------------------------------------------------ */

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
        // 攻击方用一个"元素已初始化"的模板副本（裸 LivingThing(long) 没有元素，
        // setLevel 里的 initialMana 会当场 NPE）
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
     * 让【支柱-死星天裁】对一只满血木桩打一次（【毁伤】1 层 = 只打 {@code hitsPerScourge} 段），
     * 返回木桩掉的血。
     * <p>
     * 木桩的血量写成 1000 万：段数一多就会把普通目标打死，而"目标死了就停手"
     * 会让两次对照的伤害被血量上限截断，比不出系数的作用。
     *
     * @param skill 技能（带着要验的那份系数）
     * @param user  施法者（白厄）
     * @return 木桩掉的血
     */
    private static long verdictDamage(
            cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.FoundationStardeathVerdict
                    skill,
            cn.gfhnv.game.officialStuff.customEntity.players.Phainon user) {
        LivingThing dummy = new PlayerOne(125).copy();
        dummy.setName("死星天裁木桩");
        dummy.setHpMax(10_000_000L);
        dummy.setHp(10_000_000L);
        Fight fight = new Fight(new ArrayList<>(List.of(dummy)), new ArrayList<>(),
                new ArrayList<>(List.of(user)));
        user.setScourge(1);
        long before = dummy.getHp();
        skill.comeToEffect(fight, user, new ArrayList<>(List.of(dummy)));
        return before - dummy.getHp();
    }

    /* ------------------------------------------------------------------
     * 技能 id 覆盖率（2026-10-03 第 2 轮：注册表覆盖到的技能必须条条有 id）
     * ------------------------------------------------------------------ */

    /**
     * 让【却是必要的苦难】对一只木桩打一次（先给 2 层【灾难之力】= 2 段 + 一记收尾），
     * 返回木桩掉的血。
     *
     * @param skill  技能（带着要验的那份系数）
     * @param reaver 盗火行者
     * @param wall   木桩
     * @return 木桩掉的血
     */
    private static long sufferingDamage(
            cn.gfhnv.game.officialStuff.customSkill.flameReaverSkills.NecessarySuffering skill,
            cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver reaver, LivingThing wall) {
        reaver.addDisasterPower(2);
        Fight fight = new Fight(new ArrayList<>(List.of(reaver)), new ArrayList<>(),
                new ArrayList<>(List.of(wall)));
        long before = wall.getHp();
        skill.comeToEffect(fight, reaver, fight.getOpponentList(reaver));
        return before - wall.getHp();
    }

    /* ------------------------------------------------------------------
     * 战斗日志的颜色（§5.5.2：短标识与回合头同色）
     * ------------------------------------------------------------------ */

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

    /* ------------------------------------------------------------------
     * 游戏规则（阶段 3）
     * ------------------------------------------------------------------ */

    /**
     * 战斗日志的颜色约定（{@code 60-COMBAT.md} §5.5.2）。
     * <p>
     * 用户 2026-10-03 实测："攻击时的短 uuid 没有颜色（应该和回合头一样）"。
     * 回合头是<b>整行</b>青色（短标识因此是青的），而攻击行 / 【侵蚀】行 / 回血行是<b>按段</b>着色的，
     * 上一轮加 {@code #短标识} 时只加了文字、没上色。这里钉住三件事：
     * <ol>
     *     <li>按段着色的那一份（{@code LivingThing#getNameWithUuidAndSide()}）里
     *     <b>短标识是青的</b>（与回合头同色）、<b>阵营还是灰的</b>（§5.5.2 第 4 点的理由）、
     *     名字本身不上色（与其它日志行一致，不引入第三种口径）；</li>
     *     <li>整行着色的那一份（{@code LivingThing#getNameWithUuid()}）<b>不带任何转义</b> ——
     *     回合头把它整行包在青色里，内层若自带复位，会把后半行"（我方）的回合 ───"的青色掐断；</li>
     *     <li>关掉着色时两者都退化成纯文本（{@code > log.txt} 里不许混进看不见的转义序列）。</li>
     * </ol>
     * 做完把着色开关恢复原样，不给后面的用例留全局状态。
     */
    private static void testCombatLogColors() {
        section("战斗日志颜色（短标识与回合头同色）");
        boolean colorBefore = cn.gfhnv.game.utils.ConsoleColor.isEnabled();
        LivingThing hero = new PlayerOne(125).copy();
        LivingThing bug = new CommonInsect(150L).copy();
        try {
            Fight fight = new Fight(new ArrayList<>(List.of(bug)), new ArrayList<>(),
                    new ArrayList<>(List.of(hero)));
            hero.setParticipateFight(fight);
            bug.setParticipateFight(fight);

            String tag = "#" + hero.getShortUuid();
            cn.gfhnv.game.utils.ConsoleColor.setEnabled(true);
            String colored = hero.getNameWithUuidAndSide();
            check("颜色：攻击行那一路（按段着色）的短标识是<b>青</b>色、与回合头同色 —— "
                            + colored.replace("\u001B", "<ESC>"),
                    colored.contains(cn.gfhnv.game.utils.ConsoleColor.CYAN + tag
                            + cn.gfhnv.game.utils.ConsoleColor.RESET)
                            && colored.startsWith(hero.getName()));
            check("颜色：阵营照旧是灰色（§5.5.2 第 4 点：免得抢伤害数字与技能名的注意力），名字本身不上色",
                    colored.contains("（" + cn.gfhnv.game.utils.ConsoleColor.DIM + "我方"
                            + cn.gfhnv.game.utils.ConsoleColor.RESET + "）")
                            && !colored.startsWith(cn.gfhnv.game.utils.ConsoleColor.CYAN));
            String plainForHeader = hero.getNameWithUuid();
            check("颜色：整行着色的那一份（回合头用它）不带任何转义，否则会把整行青色掐断 —— "
                            + plainForHeader,
                    plainForHeader.equals(hero.getName() + tag)
                            && plainForHeader.indexOf('\u001B') < 0);

            cn.gfhnv.game.utils.ConsoleColor.setEnabled(false);
            check("颜色：关掉着色后两者都退化成纯文本（重定向到文件时不混进转义序列）",
                    hero.getNameWithUuidAndSide().equals(hero.getName() + tag + "（我方）")
                            && hero.getNameWithUuid().equals(hero.getName() + tag));
        } finally {
            cn.gfhnv.game.utils.ConsoleColor.setEnabled(colorBefore);
        }
    }

    /* ------------------------------------------------------------------
     * 一个值两个入口（②）
     * ------------------------------------------------------------------ */

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
     * （{@code FlameReaver.java:282-283}），而 {@code EntityData.json} 的 {@code derived.hpMax}
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
                + "{\"derived\":{\"hpMax\":77777}}}}";
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
            check("两个入口：EntityData.json 的 derived.hpMax 赢（规则表写 123456，实际 "
                            + flameTemplate.getHpMax() + "）",
                    flameTemplate.getHpMax() == 77777L);
            check("两个入口：这件事被点名了（不再静默）—— 提醒 " + both.notices(),
                    both.notices().size() == 1 && bothText.contains("两个入口对不上")
                            && bothText.contains("derived.hpMax") && bothText.contains(flameBaseHpRule));
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
            //    生成出来的 EntityData.json 里 derived.hpMax 与 flameReaver.baseHpMax 本来就是同一个数，
            //    这才是"只写派生键"的出厂形状。随手写 77777 反而会（正确地）触发提醒 —— 那属于新用例②。
            cn.gfhnv.game.system.configLoadingSystem.GameRules.resetForTest();
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Report derivedOnly =
                    cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher
                            .applyJson("{\"entities\":{\"game_official_content:flameReaver\":"
                                            + "{\"derived\":{\"hpMax\":" + defaultFlameHp + "}}}}",
                                    "自测-只写派生键");
            check("两个入口：只写 derived.hpMax、而且写的就是规则表生效值（= 默认文件的形状）时不发提醒"
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
                                    + "{\"derived\":{\"attack\":999}}}}", "自测-只写规则表");
            check("两个入口：规则表写了、补丁里没有 hpMax 时不发提醒（提醒只针对 hpMax 撞车）—— "
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
                                    + "{\"derived\":{\"hpMax\":54321}}}}", "自测-虫皇两个入口");
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
     * {@code EntityData.json} 本来就<b>一定</b>有 {@code derived.hpMax}（全量 dump），
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
                    + "{\"derived\":{\"hpMax\":" + factory + "}}}}";
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
                                    + "{\"derived\":{\"hpMax\":" + changed + "}}}}", "自测-两处改成同一个数");
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
     * @return 当前"强制 verbose"的值（自测前后原样还原，别把状态留给别的用例）
     */
    private static Boolean forcedVerbose() {
        try {
            java.lang.reflect.Field field = cn.gfhnv.game.system.configLoadingSystem.ConfigOutput.class
                    .getDeclaredField("forced");
            field.setAccessible(true);
            return (Boolean) field.get(null);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /* ------------------------------------------------------------------
     * 模组配置（阶段 4）
     * ------------------------------------------------------------------ */

    /**
     * @return 现在是不是被自测强制成"详细播报"（判定同上）
     */
    private static boolean isVerboseForced() {
        return Boolean.TRUE.equals(forcedVerbose());
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
                        + "{\"base\":{\"speed\":222}}}}");
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
            if (method.isDefault()) optionalCount++; else mustImplement++;
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
                + "{\"base\":{\"speed\":222}}}},"
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
                            + "{\"base\":{\"speed\":" + speedBefore + "}}}}", "自测-还原速度");
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
     * 新造一个模板的副本，用来观察"现在的规则值算出来的面板"。
     * <p>
     * 必须用副本而不是注册表里的模板：模板是在规则加载之前造出来的，它的三围已经定型；
     * 规则里的 {@code formula.*} 只影响"之后新造出来的实体"（这正是设计里写的边界）。
     *
     * @param id 实体完整 id
     * @return 新造的副本
     */
    private static LivingThing entityLoadProbe(String id) {
        LivingThing template = entityTemplateOf(id);
        return template == null ? null : template.copy();
    }

    /**
     * 递归删掉一个目录（不存在就什么都不做）。
     * <p>
     * 只给自测的临时目录用：这类用例要在真实文件系统上验证"只在缺失时写盘"，
     * 所以必须自己收拾干净（成果目录是 gitignore 的 {@code out/}）。
     *
     * @param directory 目录
     */
    private static void deleteDirectory(java.io.File directory) {
        java.io.File[] children = directory.listFiles();
        if (children != null) {
            for (java.io.File child : children) {
                deleteDirectory(child);
            }
        }
        if (directory.exists() && !directory.delete()) {
            fail("临时目录删不掉：" + directory.getPath());
        }
    }

    /**
     * 按完整 id 取注册表里的实体模板。
     *
     * @param id 完整 id
     * @return 模板（找不到直接让断言失败，返回 {@code null}）
     */
    private static LivingThing entityTemplateOf(String id) {
        for (LivingThing living : World.getLivingEntityList()) {
            if (id.equals(living.getId())) {
                return living;
            }
        }
        fail("注册表里找不到实体模板「" + id + "」");
        return null;
    }

    /**
     * 「反射驱动配置」可行性实验（2026-10-03）。
     * <p>
     * <b>它证明三件事</b>（对应
     * {@code project_analyses/CONFIG-LOADING-DECOUPLING-2026-10.md} 的「反射驱动可行性实验」一节）：
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

        /* ---------- ① 三个真实字段：criticalRate / speed / metalResistance ---------- */
        LivingThing target = entityTemplateOf("game_official_content:playerOne");
        if (target == null) {
            return;
        }
        String beforeState = cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.stateOf(target);
        double beforeCritical = target.getCriticalRate();
        long beforeSpeed = target.getSpeed();
        double beforeMetal = target.getMetalResistance();
        System.out.println("  [信息] 打补丁前：" + beforeState);
        try {
            // 用**另一个值**，这样"变了"这件事有信息量（写成原值什么都不变，断言会假绿）
            double newCritical = beforeCritical + 0.125;
            long newSpeed = beforeSpeed + 7;
            double newMetal = beforeMetal + 0.5;
            org.json.JSONObject patch = new org.json.JSONObject()
                    .put("criticalRate", newCritical)
                    .put("speed", newSpeed)
                    .put("metalResistance", newMetal);
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
            check("① 反射驱动：metalResistance 真的变了（+0.5，字段住在 @DataFlatten 组件里）—— "
                            + beforeMetal + " → " + target.getMetalResistance(),
                    Math.abs(target.getMetalResistance() - (beforeMetal + 0.5)) < 1e-9);

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
            target.setMetalResistance(beforeMetal);
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
        check("② 挡板：uuid / alive / presentTurn 都在 @NoConfig 清单里（少一个就是配置面漏了）—— 实扫 "
                        + blockedOfClass.keySet(),
                blockedOfClass.keySet().containsAll(Arrays.asList("uuid", "alive", "presentTurn")));
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
        check("⑤ 混血不变式（第 2 步之后）：反射面（candidates 口径）== 手写表的<b>标量</b>键"
                        + "（31 键 − manaGrow 块 − inventorySlots）—— " + reflectionKeys.size()
                        + " vs " + (tableKeys.size() - 2) + "，「只在反射里」的 " + onlyReflection.size() + " 个",
                onlyReflection.isEmpty() && reflectionKeys.size() == tableKeys.size() - 2);
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
        System.out.println("  [守卫] 反射面 ⊆ 允许清单（能配的 ∪ 加 @NoConfig 的 ∪ 明确排除的）：");
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
                    "coreflame", "coreflame_max", "soulscorch", "scourge", "scourge_max")));

            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Report fire = cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.applyJson(
                    "{\"entities\":{\"game_official_content:phainon\":{\"classState\":"
                            + "{\"coreflame_max\":20,\"coreflame\":18,\"scourge_max\":9,\"scourge\":5,"
                            + "\"soulscorch\":3}}}}", "自测-classState-白厄");
            System.out.println("  [信息] 白厄 classState 补丁：应用 " + fire.appliedEntries()
                    + "，跳过 " + fire.skippedEntries());
            check("classState 生效：classState 块里的 5 个键全部落地、没有跳过 —— " + fire.errors()
                            + fire.skippedEntries(),
                    fire.isClean() && fire.appliedEntries().size() == 5);
            check("classState 生效：coreflame_max 真的变成 20（旧值见快照）",
                    phainon.getCoreflame_max() == 20);
            check("classState 生效：coreflame 真的变成 18（setter 仍然夹到上限）",
                    phainon.getCoreflame() == 18);
            check("classState 生效：scourge_max 9 / scourge 5 / soulscorch 3",
                    phainon.getScourge_max() == 9 && phainon.getScourge() == 5
                            && phainon.getSoulscorch() == 3);
            // setter 的钳制还在：写超过上限的值会被压到上限（不是"裸写字段"）
            cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.applyJson("{\"entities\":{\"game_official_content:phainon\":"
                    + "{\"coreflame\":999}}}", "自测-classState-钳制");
            check("classState 生效：写超过上限的 coreflame 会被 setter 夹到 coreflame_max（钳制没被绕开）—— 999 → "
                    + phainon.getCoreflame(), phainon.getCoreflame() == phainon.getCoreflame_max());

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
                            + "{\"classState\":{\"isAwaken\":true,\"extraTurns\":9}}}}",
                    "自测-classState-运行时");
            System.out.println("  [信息] 运行时状态：写 isAwaken/extraTurns（awaken 在反射面上： "
                    + awakenOnSurface + "）→ 应用 " + blocked.appliedEntries()
                    + "，跳过 " + blocked.skippedEntries());
            check("classState：isAwaken / extraTurns 写不进去（还是「未知键 + 为什么」，不是静默生效）"
                            + "—— 觉醒状态 " + before + " → " + phainon.isAwaken(),
                    blocked.appliedEntries().isEmpty()
                            && blocked.skippedEntries().toString().contains("isAwaken")
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
                        "coreflame", "coreflame_max", "soulscorch", "scourge", "scourge_max",
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
     * <b>真模组</b>（{@code mods/drunkenSword}）的实体与技能也不进游戏自己的配置。
     * <p>
     * 上一条用例用的是一个最小的假模组；这一条换成真的：直接复用模组自己的编译产物
     * （{@code mods/drunkenSword/bin}，<b>不重编、不改 {@code mods/} 下任何一个字节</b>），
     * 走真实的 {@code Mod#invokeWhenLoaded()} + {@code registerItself()}。
     * <p>
     * 产物不存在时（干净的检出）只打印一行信息跳过 —— 这条用例的价值在"有模组可加载"的环境里，
     * 而它<b>不是</b>唯一证据：上一条用例在任何环境下都会跑。
     */
    private static void testRealModContentIsNotInGameConfig() {
        java.io.File bin = new java.io.File("./mods/drunkenSword/bin");
        if (!bin.isDirectory()) {
            System.out.println("  [信息] 没有 mods/drunkenSword/bin（模组未编译），跳过真模组那条检查");
            return;
        }
        Mod mod = null;
        try (java.net.URLClassLoader loader = java.net.URLClassLoader.newInstance(
                new java.net.URL[]{bin.toURI().toURL()},
                Thread.currentThread().getContextClassLoader())) {
            Class<?> mainClass = Class.forName("com.gfhnv.mods.drunkenSword.DrunkenSwordMod",
                    true, loader);
            mod = (Mod) mainClass.getConstructor(cn.gfhnv.game.mod.ModInformation.class)
                    .newInstance(new cn.gfhnv.game.mod.ModInformation("酒剑仙", "自测", "验证用",
                            "com.gfhnv.mods.drunkenSword.DrunkenSwordMod", "1"));
            cn.gfhnv.game.world.World.addMod(mod);
            mod.invokeWhenLoaded();
            mod.registerItself();
            StringBuilder owned = new StringBuilder();
            for (cn.gfhnv.game.entity.Entity entity : mod.getEntityList()) {
                owned.append(entity.getId()).append(' ');
            }
            check("真模组：酒剑仙被注册进注册表（id = " + owned.toString().trim() + "）",
                    !mod.getEntityList().isEmpty()
                            && cn.gfhnv.game.world.World.getEntityList()
                            .contains(mod.getEntityList().get(0)));

            String entityJson = cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter
                    .entityDataJson();
            String skillJson = cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter
                    .skillDataJson();
            check("真模组：EntityData.json 里没有 {game_official_content 之外} 的模组内容（酒剑仙）—— "
                            + "含 drunkenSword = " + entityJson.contains("drunkenSword"),
                    !entityJson.contains("drunkenSword"));
            check("真模组：SkillData.json 里没有酒剑仙的技能（三个技能名一个都不该出现）",
                    !skillJson.contains("醉里挑灯看剑") && !skillJson.contains("举杯邀月")
                            && !skillJson.contains("一剑霜寒十四州"));
            check("真模组：官方内容照旧写出去（playerOne 两边都在）",
                    entityJson.contains("game_official_content:playerOne")
                            && skillJson.contains("game_official_content:playerOne"));
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException e) {
            check("真模组：加载 mods/drunkenSword 失败（这条红了说明模组契约被弄坏了）—— "
                    + e.getClass().getSimpleName() + ": " + e.getMessage(), false);
        } finally {
            if (mod != null) {
                for (cn.gfhnv.game.entity.Entity entity : new ArrayList<>(mod.getEntityList())) {
                    cn.gfhnv.game.world.World.removeEntity(entity);
                }
                cn.gfhnv.game.world.World.removeMod(mod);
            }
        }
    }

    /**
     * 读一个私有字段的值（自测里只为"没被动过"这件事取个快照）。
     *
     * @param target 对象
     * @param name   字段名
     * @return 值；读不到返回 {@code null}
     */
    private static Object valueOfField(Object target, String name) {
        for (Class<?> current = target.getClass(); current != null && current != Object.class;
             current = current.getSuperclass()) {
            try {
                java.lang.reflect.Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (ReflectiveOperationException | RuntimeException e) {
                continue;
            }
        }
        return null;
    }

    /**
     * {@code /summon}：默认我方、可指定敌方、命名空间规则、入场初始化补齐。
     * <p>
     * 放在最后跑：它会把两个探测实体注册进 {@code World}（实体注册表），
     * 后面的用例再查"可用实体"列表就会被它们污染。
     */
    private static void testSummonCommand() {
        section("召唤：/summon");

        FlameReaver boss = new FlameReaver(150);
        ActorLiXiaoYan hero = new ActorLiXiaoYan(125);
        List<LivingThing> enemies = new ArrayList<>();
        enemies.add(boss);
        List<LivingThing> fighters = new ArrayList<>();
        fighters.add(hero);
        Fight fight = new Fight(enemies, new ArrayList<>(), fighters);

        LivingThing previous = CommandManager.getPlayer();
        CommandManager.setPlayer(hero);

        int fightersBefore = fight.getFighterList().size();
        int enemiesBefore = fight.getEnemiesList().size();
        int turnsBefore = TurnManager.getTurns().size();

        /* ① 不写阵营 = 我方 */
        run("summon CommonInsect", fight, true);
        check("summon：不写阵营时默认加进我方", fight.getFighterList().size() == fightersBefore + 1);
        check("summon：敌方列表没被顺手加人", fight.getEnemiesList().size() == enemiesBefore);
        LivingThing summoned = fight.getFighterList().get(fight.getFighterList().size() - 1);
        check("summon：召唤出来的副本被补成完整 id",
                "game_official_content:commonInsect".equals(lastFighterId(fight)));
        boolean isTemplateItself = false;
        for (LivingThing registered : World.getLivingEntityList()) {
            if (registered == summoned) {
                isTemplateItself = true;
            }
        }
        check("summon：召唤的是注册表模板的副本，不是模板本身", !isTemplateItself);
        check("summon：进了 allEntities", fight.getAllEntities().contains(summoned));
        check("summon：排进了时间轴（下个回合就能行动）", TurnManager.getTurns().size() == turnsBefore + 1);
        check("summon：接上了战斗上下文（participateFight）", summoned.getParticipateFight() == fight);

        /* ② 指定阵营：英文与中文都认 */
        run("summon commonInsect enemy", fight, true);
        check("summon：enemy 进敌方列表", fight.getEnemiesList().size() == enemiesBefore + 1);
        run("summon CommonInsect 敌方", fight, true);
        check("summon：中文「敌方」也认", fight.getEnemiesList().size() == enemiesBefore + 2);
        run("summon CommonInsect 我方", fight, true);
        check("summon：中文「我方」也认", fight.getFighterList().size() == fightersBefore + 2);
        run("summon CommonInsect ally", fight, true);
        check("summon：ally 也当我方", fight.getFighterList().size() == fightersBefore + 3);

        /* ③ 错误分支与名字的三种写法 */
        run("summon", fight, false);
        run("summon NoSuchEntity", fight, false);
        String badSide = errorOf("summon CommonInsect 中间派", fight);
        check("summon：阵营填错时报错里给出可填的值", badSide.contains("our") && badSide.contains("enemy"));
        // 类名写法：BrokenContainer 忽略大小写正好等于短名 brokenContainer，于是命中残破容器
        run("summon BrokenContainer", fight, true);
        check("summon：类名写法可用（命中短名与它忽略大小写相同的那条）",
                "game_official_content:brokenContainer".equals(lastFighterId(fight)));
        run("summon brokenContainer", fight, true);
        check("summon：短名精确命中残破容器",
                "game_official_content:brokenContainer".equals(lastFighterId(fight)));
        run("summon completeContainer", fight, true);
        check("summon：同一类的另一条模板也能用短名召唤（不会被类名层挤掉）",
                "game_official_content:completeContainer".equals(lastFighterId(fight)));

        /* ④ 命名空间规则：短名/类名只解析官方内容，模组实体必须写完整 id */
        Mod summonProbeMod = new Mod("entityTestMod") {
        };
        ProbeSummonEntity sameNameAsOfficial = new ProbeSummonEntity();
        sameNameAsOfficial.setId("commonInsect");   // 与官方虫子同短名（不同类，所以不会抢它的 id）
        summonProbeMod.addEntity(sameNameAsOfficial);
        ProbeSummonEntity modOnly = new ProbeSummonEntity();
        modOnly.setId("modOnlySummon");             // 短名唯一，只能靠完整 id 拿到
        summonProbeMod.addEntity(modOnly);
        World.addMod(summonProbeMod);
        summonProbeMod.registerItself();

        int fightersBeforeProbe = fight.getFighterList().size();
        ProbeSummonEntity.fightStartCalls = 0;
        run("summon commonInsect", fight, true);
        check("summon：短名只解析官方内容（模组同名实体不参与，因此不算歧义）",
                "game_official_content:commonInsect".equals(lastFighterId(fight)));
        String modShortName = errorOf("summon modOnlySummon", fight);
        check("summon：模组实体写短名会被拒绝并提示该写的完整 id",
                modShortName.contains("entityTestMod:modOnlySummon"));
        run("summon entityTestMod:modOnlySummon", fight, true);
        check("summon：模组实体写完整 id 可以召唤",
                fight.getFighterList().size() == fightersBeforeProbe + 2);
        check("summon：入场时补调了 whenFightStart（中途加入的实体收不到框架那一次）",
                ProbeSummonEntity.fightStartCalls == 1);
        // 用类名找同类模板：两个探针的短名都跟类名不一样，于是按类名命中 2 个，
        // 报错里把两个可写的完整 id 都列出来（写哪一个都能召唤成功）
        String ambiguous = errorOf("summon ProbeSummonEntity", fight);
        check("summon：一个名字命中同类的多条模板时，报错里列出全部可写的完整 id",
                ambiguous.contains("entityTestMod:commonInsect")
                        && ambiguous.contains("entityTestMod:modOnlySummon"));

        /* ⑤ 不在战斗里要明确报错，而不是静默什么都不做 */
        CommandManager.clearCurrentFight();
        String noFight = errorOf("summon CommonInsect");
        check("summon：不在战斗里时明确报错", noFight.contains("不在战斗中"));

        CommandManager.setPlayer(previous);
    }

    /**
     * @param fight 战斗
     * @return 最后加进我方阵营的那个实体的 id；我方列表为空时返回 {@code null}
     */
    private static String lastFighterId(Fight fight) {
        List<LivingThing> fighters = fight.getFighterList();
        return fighters.isEmpty() ? null : fighters.get(fighters.size() - 1).getId();
    }

    /**
     * 执行一条命令并检查「成功/失败」是否符合预期。
     *
     * @param command  命令文本（可带前缀）
     * @param expected 期望是否成功
     */
    private static void run(String command, boolean expected) {
        run(command, null, expected);
    }

    /**
     * 同上，但显式指定命令所属的战斗。
     * <p>
     * {@code fight} 传 {@code null} 时沿用 {@link CommandSource} 里登记的那一场
     * —— 所以"当前战斗"是有粘性的：想测"没有战斗"的情况，先
     * {@link CommandManager#clearCurrentFight()}。
     *
     * @param command  命令文本（可带前缀）
     * @param fight    当前战斗；{@code null} 表示沿用已登记的那一场
     * @param expected 期望是否成功
     */
    private static void run(String command, Fight fight, boolean expected) {
        CommandResult result = CommandManager.executeResult(command, fight);
        System.out.println("  >>> " + command + "  =>  " + result);
        if (result.isSuccess() == expected) {
            passes++;
        } else {
            fail("命令「" + command + "」期望 " + (expected ? "成功" : "失败") + "，实际相反：" + result);
        }
    }

    /**
     * 跑一条命令并返回它的错误文本（成功时返回空串），顺便打印一行。
     *
     * @param command 命令文本
     * @return 错误文本
     */
    private static String errorOf(String command) {
        return errorOf(command, null);
    }

    /**
     * 同上，但显式指定命令所属的战斗。
     *
     * @param command 命令文本
     * @param fight   当前战斗；{@code null} 表示沿用已登记的那一场
     * @return 错误文本
     */
    private static String errorOf(String command, Fight fight) {
        CommandResult result = CommandManager.executeResult(command, fight);
        System.out.println("  >>> " + command + "  =>  " + result);
        return result.getError() == null ? "" : result.getError().getMessage();
    }

    /**
     * 断言某个操作会抛出 {@link cn.gfhnv.game.system.command.CommandSyntaxException}。
     *
     * @param message 断言说明
     * @param action  操作
     */
    private static void expectSyntaxError(String message, ThrowingAction action) {
        try {
            action.run();
            fail(message + "（但没有抛出异常）");
        } catch (Exception e) {
            System.out.println("  >>> 预期报错：" + e.getMessage());
            passes++;
        }
    }

    /**
     * 断言。
     *
     * @param message 断言说明
     * @param ok      是否成立
     */
    private static void check(String message, boolean ok) {
        if (ok) {
            passes++;
            System.out.println("  [OK] " + message);
        } else {
            fail(message);
        }
    }

    /**
     * 记录一次失败。
     *
     * @param message 失败说明
     */
    private static void fail(String message) {
        failures++;
        System.out.println("  [FAIL] " + message);
    }

    /**
     * 打印一个小节标题。
     *
     * @param title 标题
     */
    private static void section(String title) {
        System.out.println();
        System.out.println("-------- " + title + " --------");
    }

    /**
     * 可以抛异常的操作。
     *
     * @author AI（DeepSeek）生成
     */
    @FunctionalInterface
    private interface ThrowingAction {

        /**
         * 执行。
         *
         * @throws Exception 任意异常
         */
        void run() throws Exception;
    }

    /**
     * 一个"标量属性槽位"：字段本身 + <b>它该挂到哪个对象上</b>。
     * <p>
     * 为什么要带 owner：属性可能住在实体的 {@code @DataFlatten} 组件里，
     * 那时候 {@code field.set(实体, 值)} 会抛
     * {@code Can not set double field …AttributeProfile.metalResistance to …PlayerOne} ——
     * 必须把值写进<b>组件实例</b>（也就是 {@code 实体.attributes.metalResistance}）。
     *
     * @param owner 承载这个字段的对象（实体自己，或它的组件）
     * @param field 字段
     */
    private record ScalarSlot(Object owner, java.lang.reflect.Field field) {
    }

    /**
     * 一个"假模组"：实现了 {@link cn.gfhnv.game.mod.config.ModDataAware}，
     * 只为了验证配置加载链路，不注册任何内容。
     * <p>
     * 下面几个带 {@code @ModConfig} 注解的子类复用它这套"记下读到的值"的写法。
     * 每个注解场景要一个自己的类：{@code @ModConfig} <b>没有</b> {@code @Inherited}，
     * 注解必须写在模组主类自己头上（写在父类上游戏读不到 —— 这正是要钉住的边界之一）。
     *
     * @author AI（DeepSeek）生成
     */
    private static class ProbeMod extends Mod
            implements cn.gfhnv.game.mod.config.ModDataAware {

        /**
         * applyConfig 里要不要抛一个 {@link Error}（验证"最坏情况"也不会带崩加载链）。
         */
        private final boolean throwInConfig;
        /**
         * applyConfig 有没有被调用。
         */
        private boolean called = false;
        /**
         * 从配置里读到的层数（读不到就是默认值 2）。
         */
        private int stacks = 2;

        /**
         * @param modId 模组 id
         */
        private ProbeMod(String modId) {
            this(modId, false);
        }

        /**
         * @param modId         模组 id
         * @param throwInConfig applyConfig 里是否抛异常
         */
        private ProbeMod(String modId, boolean throwInConfig) {
            super(modId);
            this.throwInConfig = throwInConfig;
        }

        @Override
        public void applyConfig(cn.gfhnv.game.mod.config.ModConfigDocument config) {
            this.called = true;
            this.stacks = config.getInt("initialStacks", 2);
            if (throwInConfig) {
                throw new AssertionError("自测：故意在 applyConfig 里抛一个 Error");
            }
        }
    }

    /**
     * "没实现 ModDataAware"的假模组（用来验证"可选接口"这条契约：不实现就正常跳过）。
     *
     * @author AI（DeepSeek）生成
     */
    private static final class PlainProbeMod extends Mod {

        /**
         * @param modId 模组 id
         */
        private PlainProbeMod(String modId) {
            super(modId);
        }
    }

    /**
     * 带 {@link cn.gfhnv.game.mod.config.ModConfig} 注解的假模组：注解里的分组名
     * （{@code "  annotatedGroup  "}，故意带首尾空白）与构造器传进来的 {@code MOD_ID}
     * <b>故意不一样</b>，用来钉住「<b>注解 &gt; {@code MOD_ID}</b>」这条优先级，
     * 以及"注解 id 的首尾空白会被去掉"。
     *
     * @author AI（DeepSeek）生成
     */
    @cn.gfhnv.game.mod.config.ModConfig(id = "  annotatedGroup  ")
    private static final class AnnotatedProbeMod extends ProbeMod {

        /**
         * @param modId 模组 id（与注解里的分组名故意不同）
         */
        private AnnotatedProbeMod(String modId) {
            super(modId);
        }
    }

    /**
     * 注解 {@code id} 只有空白的假模组：钉住"空白串不算写了分组名"—— 退回 {@code MOD_ID}。
     *
     * @author AI（DeepSeek）生成
     */
    @cn.gfhnv.game.mod.config.ModConfig(id = "   ")
    private static final class BlankAnnotatedProbeMod extends ProbeMod {

        /**
         * @param modId 模组 id（注解 id 只有空白时用的就是它）
         */
        private BlankAnnotatedProbeMod(String modId) {
            super(modId);
        }
    }

    /**
     * 把注解里的分组名写成<b>相对路径</b>的假模组：{@code ./config/data/<注解 id>.json} 字面上正好落在
     * 自测自己的 {@code out/tmpModConfig/} 临时目录里，用来证明<b>游戏默认入口
     * （{@code loadModData(Mod)}）用的文件名也是注解 id</b>（跟 {@code MOD_ID} 走的话那个文件根本不存在），
     * 而且全程不往真 {@code config/} 里写任何文件。
     *
     * @author AI（DeepSeek）生成
     */
    @cn.gfhnv.game.mod.config.ModConfig(id = "../../out/tmpModConfig/annotatedGroup")
    private static final class AnnotatedPathProbeMod extends ProbeMod {

        /**
         * @param modId 模组 id（故意取一个绝对没有配置文件的名字）
         */
        private AnnotatedPathProbeMod(String modId) {
            super(modId);
        }
    }

    /**
     * 自测用的「免死」修正器。
     * <p>
     * 和白厄的免死（{@code Phainon#soulscorchDeathWard()}）同一套写法：致死伤害被拦下、
     * 血量锁 1，并用一个标记保证一场只触发一次；试算期间只算数、不消费。
     * 这里不依赖战斗上下文，所以可以脱离 {@code Fight} 单独验证链路。
     *
     * @author AI（DeepSeek）生成
     */
    private static class ProbeDeathWard implements IModifyDamage {

        /**
         * 免死是否已经被消费掉（试算不算消费）。
         */
        private boolean consumed = false;

        /**
         * 是否有过一次「在试算期间被调用」的记录。
         */
        private boolean calledWhileAnticipating = false;

        @Override
        public long damageModify(long newHp, DamageEvent da) {
            if (newHp > 0) {
                return newHp;
            }
            if (da.getAttackedEntity().isAnticipating()) {
                calledWhileAnticipating = true;
                return 1;
            }
            consumed = true;
            return 1;
        }

        /**
         * @return 免死是否已经被消费
         */
        boolean wasConsumed() {
            return consumed;
        }

        /**
         * @return 免死是否有过「在试算期间被调用」的记录
         */
        boolean wasCalledWhileAnticipating() {
            return calledWhileAnticipating;
        }
    }

    /**
     * 自测用的「血量下限」修正器（类似李晓焰的记忆生命）。
     *
     * @author AI（DeepSeek）生成
     */
    private static class ProbeHpFloor implements IModifyDamage {

        /**
         * 生命上限的比例下限。
         */
        private final double rate;

        /**
         * @param rate 生命上限的比例下限（0.5 表示不低于半血）
         */
        ProbeHpFloor(double rate) {
            this.rate = rate;
        }

        @Override
        public long damageModify(long newHp, DamageEvent da) {
            long minHp = (long) (da.getAttackedEntity().getHpMax() * rate);
            return Math.max(newHp, minHp);
        }
    }

    /**
     * 自测用的「无视防御」效果。
     * <p>
     * 它<b>不是</b>官方内容里的 {@code IgnoreDefenceEffect}，只实现了
     * {@link cn.gfhnv.game.interfaces.IDefenceIgnore}：用来证明伤害计算认的是接口，
     * 模组自己写的穿甲效果一样会被算进去。
     *
     * @author AI（DeepSeek）生成
     */
    private static class ProbeDefenceIgnoreEffect extends cn.gfhnv.game.effect.Effect
            implements cn.gfhnv.game.interfaces.IDefenceIgnore {

        /**
         * 无视防御的百分比。
         */
        private final double percent;

        /**
         * 无视防御的固定值。
         */
        private final long amount;

        /**
         * 构造效果。
         *
         * @param percent 无视防御百分比
         * @param amount  无视防御固定值
         */
        ProbeDefenceIgnoreEffect(double percent, long amount) {
            super("probeDefenceIgnoreEffect");
            this.percent = percent;
            this.amount = amount;
        }

        /**
         * 复制构造器。
         *
         * @param other 被复制的效果
         */
        ProbeDefenceIgnoreEffect(ProbeDefenceIgnoreEffect other) {
            super(other.getID());
            this.percent = other.percent;
            this.amount = other.amount;
        }

        @Override
        public double getIgnoreDefencePercent() {
            return percent;
        }

        @Override
        public long getIgnoreDefenceAmount() {
            return amount;
        }

        @Override
        public void comeIntoEffect(LivingThing thing) {
            // 标记型效果：不需要每回合做事（顺便避免基类占位实现打印提示）
        }

        @Override
        public cn.gfhnv.game.effect.Effect copy() {
            return new ProbeDefenceIgnoreEffect(this);
        }
    }

    /**
     * 自测用的假技能：不产生任何战斗效果，只把「用了哪一招、打了谁」记进日志。
     *
     * @author AI（DeepSeek）生成
     */
    private static class ProbeSkill extends Skill {

        /**
         * 日志（所有副本共享同一个列表）。
         */
        private final List<String> log;

        /**
         * 构造一个作用于自身的假技能（{@code aims = 0}）。
         *
         * @param name 技能名
         * @param log  日志
         */
        ProbeSkill(String name, List<String> log) {
            this(name, log, 0);
        }

        /**
         * 构造一个假技能。
         *
         * @param name 技能名
         * @param log  日志
         * @param aims 目标数（0=自身；正数=选 N 个目标）
         */
        ProbeSkill(String name, List<String> log, int aims) {
            super(name, "自测用假技能", 0, 0, 0, aims);
            this.log = log;
        }

        /**
         * 构造一个用于伤害试算的假技能（带攻击力倍率）。
         *
         * @param name             技能名
         * @param log              日志
         * @param aims             目标数
         * @param atkMagnification 攻击力倍率（伤害计算里乘在攻击力上）
         */
        ProbeSkill(String name, List<String> log, int aims, double atkMagnification) {
            super(name, "自测用假技能", 0, atkMagnification, 0, aims);
            this.log = log;
        }

        /**
         * 复制构造器。
         *
         * @param other 被复制的技能
         */
        ProbeSkill(ProbeSkill other) {
            super(other);
            this.log = other.log;
        }

        @Override
        public Skill copy() {
            return new ProbeSkill(this);
        }

        @Override
        public void comeToEffect(Fight fight, LivingThing user) {
            log.add(getName());
        }

        @Override
        public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
            StringBuilder builder = new StringBuilder(getName()).append('→');
            if (enemies != null) {
                for (int i = 0; i < enemies.size(); i++) {
                    if (i > 0) {
                        builder.append(',');
                    }
                    builder.append(enemies.get(i).getName());
                }
            }
            log.add(builder.toString());
        }
    }

    /**
     * 自测用的"模组实体"探针：验证 {@code /summon} 的命名空间规则与"入场初始化补调"。
     * <p>
     * 它被注册两次（见 {@code testSummonCommand}）：一次短名叫 {@code commonInsect}
     * （与官方虫子同名，用来验证"短名只解析官方内容"），一次叫 {@code modOnlySummon}
     * （短名唯一，用来验证"模组实体必须写完整 id"）。
     * <p>
     * {@link #fightStartCalls} 是静态计数器：召唤出来的是 {@code copy()} 的副本，
     * 计数放在实例上就统计不到了。
     */
    public static class ProbeSummonEntity extends LivingThing {

        /**
         * {@link #whenFightStart(Fight)} 被调用的次数。
         */
        public static int fightStartCalls = 0;

        /**
         * 构造探针实体。
         */
        public ProbeSummonEntity() {
            super("召唤探针", "summonProbe", 0.0, 0.0, 0.0, 0.0, 0.0,
                    100, 1L, "insect", 10, 10, 10, ElementSort.FIRE);
            // 控制器不能为 null：LivingThing 的拷贝构造器会照着它重建一个（见 TIPS §5.5）
            this.setController(new UniversalController(new ArrayList<>(), this));
        }

        /**
         * 复制构造器。参数类型必须写成 {@link ProbeSummonEntity}，否则 {@link #copy()} 会调到自己。
         *
         * @param other 被复制的实体
         */
        public ProbeSummonEntity(ProbeSummonEntity other) {
            super(other);
        }

        @Override
        public LivingThing copy() {
            return new ProbeSummonEntity(this);
        }

        @Override
        public void whenFightStart(Fight fight) {
            fightStartCalls++;
        }
    }

    /**
     * 自测用的"模组效果"探针 A：短名唯一（{@code modOnlyEffect}），官方效果里没有这个名字。
     * <p>
     * 用来验证「模组效果必须写完整 id」：写短名要被拒绝并提示 {@code effectTestMod:modOnlyEffect}，
     * 写完整 id 则能正常施加。
     * <p>
     * <b>为什么必须 public + 公开无参构造器</b>：{@code /effect} 是用反射构造实例的
     * （{@code getConstructor(...).newInstance(...)}），类或构造器不可访问会直接失败。
     * <b>为什么和 {@link ProbeModSameNameEffect} 分成两个类</b>：运行时补全 id 是按【类】查表的
     * （{@code World#fullIdOf}），同一个类注册两条模板会互相盖掉对方的 id。
     *
     * @author AI（DeepSeek）生成
     */
    public static class ProbeModOnlyEffect extends cn.gfhnv.game.effect.Effect {

        /**
         * 构造探针效果（只写名字，等级/持续回合由命令用 setter 补上）。
         */
        public ProbeModOnlyEffect() {
            super("modOnlyEffect");
            this.getEffectTagsList().add(cn.gfhnv.game.effect.EffectTags.UNIVERSAL);
            this.setLastTime(1);
        }

        /**
         * 复制构造器。
         *
         * @param other 被复制的效果
         */
        public ProbeModOnlyEffect(ProbeModOnlyEffect other) {
            super(other.getID());
            this.setLevel(other.getLevel());
            this.setLastTime(other.getLastTime());
            this.getEffectTagsList().add(cn.gfhnv.game.effect.EffectTags.UNIVERSAL);
        }

        @Override
        public cn.gfhnv.game.effect.Effect copy() {
            return new ProbeModOnlyEffect(this);
        }

        @Override
        public void comeIntoEffect(LivingThing thing) {
            // 探针效果：不需要每回合做事（顺便避免基类占位实现打印提示）
        }
    }

    /**
     * 自测用的"模组效果"探针 B：短名故意与官方 {@code Frozen} 撞车（都是 {@code frozenEffect}）。
     * <p>
     * 用来验证「短名只解析官方内容，且撞名不算歧义」：{@code /effect @s add frozenEffect}
     * 必须挂上官方的冰冻，而不是报"匹配到 2 个效果"。
     * 它与官方效果<b>不是同一个类</b>，所以也不会影响官方实例按类补全 id。
     *
     * @author AI（DeepSeek）生成
     */
    public static class ProbeModSameNameEffect extends cn.gfhnv.game.effect.Effect {

        /**
         * 构造探针效果（短名与官方冰冻相同）。
         */
        public ProbeModSameNameEffect() {
            super("frozenEffect");
            this.getEffectTagsList().add(cn.gfhnv.game.effect.EffectTags.UNIVERSAL);
            this.setLastTime(1);
        }

        /**
         * 复制构造器。
         *
         * @param other 被复制的效果
         */
        public ProbeModSameNameEffect(ProbeModSameNameEffect other) {
            super(other.getID());
            this.setLevel(other.getLevel());
            this.setLastTime(other.getLastTime());
            this.getEffectTagsList().add(cn.gfhnv.game.effect.EffectTags.UNIVERSAL);
        }

        @Override
        public cn.gfhnv.game.effect.Effect copy() {
            return new ProbeModSameNameEffect(this);
        }

        @Override
        public void comeIntoEffect(LivingThing thing) {
            // 探针效果：不需要每回合做事（顺便避免基类占位实现打印提示）
        }
    }
}
