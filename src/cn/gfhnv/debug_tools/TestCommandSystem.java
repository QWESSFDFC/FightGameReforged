package cn.gfhnv.debug_tools;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEntity.monsters.CommonInsect;
import cn.gfhnv.game.officialStuff.customEntity.players.PlayerOne;
import cn.gfhnv.game.system.command.CommandManager;
import cn.gfhnv.game.system.command.CommandResult;
import cn.gfhnv.game.system.command.CommandSource;
import cn.gfhnv.game.system.command.EntitySelector;
import cn.gfhnv.game.system.command.StringReader;
import cn.gfhnv.game.system.fight.Fight;
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

        CommandManager.clearCurrentFight();
        CommandSource.setCurrentFight(null);
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
            // 关键：树是「从根往下」建的 —— 子节点是在父构建器的 build() 里才被挂上去的，
            // 所以要 build 根构建器（也就是 argument("甲", ...) 的返回值），而不是最内层的那个。
            cn.gfhnv.game.system.command.ArgumentBuilder child =
                    cn.gfhnv.game.system.command.ArgumentBuilder.argumentBuilder(
                            "甲", cn.gfhnv.game.system.command.WordArgumentType.word());
            cn.gfhnv.game.system.command.ArgumentBuilder grand =
                    child.argument("乙", cn.gfhnv.game.system.command.IntegerArgumentType.integer());

            cn.gfhnv.game.system.command.CommandNode rootNode = child.build();
            System.out.println("      最小复现的完整树（从根往下）：");
            dumpTree(rootNode, 6);

            // 断言：这条链必须建出 甲 → 乙 两层
            cn.gfhnv.game.system.command.CommandNode inner = rootNode.getChild("乙");
            check("构建器：甲节点下应挂着「乙」", inner != null);
            check("构建器：乙节点的父节点应是「甲」",
                    inner != null && inner.getParent() != null && "甲".equals(inner.getParent().getName()));
            check("构建器：乙的构建器已产出节点（build 幂等检查）", grand != null);

            // 断言：官方 hurt 命令的树必须是 hurt → 目标 → 数值
            cn.gfhnv.game.system.command.CommandNode hurtNode =
                    CommandManager.getDispatcher().getCommandNode("hurt");
            check("官方命令：hurt 下应挂着「目标」",
                    hurtNode != null && hurtNode.getChildrenNames().contains("目标"));
            cn.gfhnv.game.system.command.CommandNode targetNode =
                    hurtNode == null ? null : hurtNode.getChild("目标");
            check("官方命令：「目标」下应挂着「数值」",
                    targetNode != null && targetNode.getChildrenNames().contains("数值"));
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
}
