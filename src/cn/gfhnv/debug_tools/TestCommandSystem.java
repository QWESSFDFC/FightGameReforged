package cn.gfhnv.debug_tools;

import cn.gfhnv.game.damage.DamageCalculate;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entityController.FixOrderController;
import cn.gfhnv.game.event.DamageEvent;
import cn.gfhnv.game.interfaces.IModifyDamage;
import cn.gfhnv.game.inventory.Slot;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.mod.Mod;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.Taunt;
import cn.gfhnv.game.officialStuff.customEntity.monsters.CommonInsect;
import cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver;
import cn.gfhnv.game.officialStuff.customEntity.players.PlayerOne;
import cn.gfhnv.game.officialStuff.customItem.ANiceSword;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.command.*;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.fight.TargetStrategies;
import cn.gfhnv.game.world.World;

import java.util.ArrayList;
import java.util.List;

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
     * 断言失败的条数。
     */
    private static int failures = 0;

    /**
     * 断言成功的条数。
     */
    private static int passes = 0;

    /**
     * 测试入口。
     *
     * @param args 忽略
     */
    public static void main(String[] args) {
        useUtf8Output();
        System.out.println("========== 命令系统自测开始 ==========");

        testStringReader();
        testEntitySelectorSyntax();
        testRegistrationAndExecution();
        testFixOrderController();
        testDamageReduction();
        testFlameReaverFactions();

        System.out.println("========== 自测结束：通过 " + passes + " 条，失败 " + failures + " 条 ==========");
        if (failures > 0) {
            System.exit(1);
        }
    }

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
     * 1. StringReader
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
     * 2. 实体选择器语法
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
     * 3. 注册 + 执行
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

    /* ------------------------------------------------------------------
     * 4. 固定顺序 AI / 目标策略 / 嘲讽
     * ------------------------------------------------------------------ */

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

    /* ------------------------------------------------------------------
     * 5. 减伤（乘算叠加）
     * ------------------------------------------------------------------ */

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
            attacker.setGetCriticalRATE(-1);   // 负暴击率 = 永不暴击，避免随机暴击干扰比例断言
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
            metalAttacker.setGetCriticalRATE(-1);
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
     * 执行一条命令并检查「成功/失败」是否符合预期。
     *
     * @param command  命令文本（可带前缀）
     * @param expected 期望是否成功
     */
    private static void run(String command, boolean expected) {
        CommandResult result = CommandManager.executeResult(command);
        System.out.println("  >>> " + command + "  =>  " + result);
        if (result.isSuccess() == expected) {
            passes++;
        } else {
            fail("命令「" + command + "」期望 " + (expected ? "成功" : "失败") + "，实际相反：" + result);
        }
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
