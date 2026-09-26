package cn.gfhnv.game.system.command;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.logSystem.LogWriter;
import cn.gfhnv.game.world.World;

import java.util.List;
import java.util.Scanner;

/**
 * 命令系统总入口（静态门面）。
 * <p>
 * 这里就是「<b>预留的输入命令的方法</b>」所在。游戏原有的输入方式<b>没有改动</b>：
 * {@code GameMain}、{@link cn.gfhnv.game.entityController.PlayerController} 依旧用
 * {@code SCANNER.nextLine()} 读输入，只是在把输入交给原有逻辑之前，先问一句
 * 「这行是不是命令？」：
 *
 * <pre>{@code
 * input = GameMain.SCANNER.nextLine();
 * if (CommandManager.process(input)) {   // 是命令，已处理完毕
 *     continue;                          // 回到循环开头，继续读输入
 * }
 * // 不是命令 → 按原有逻辑处理
 * }</pre>
 *
 * <h2>可用的调用方式（按需要挑一个）</h2>
 * <table border="1">
 *     <caption>入口方法</caption>
 *     <tr><th>方法</th><th>用途</th></tr>
 *     <tr><td>{@link #isCommand(String)}</td><td>只判断是不是命令（不执行）</td></tr>
 *     <tr><td>{@link #process(String)}</td><td>是命令就执行并打印结果，返回是否已处理（推荐）</td></tr>
 *     <tr><td>{@link #execute(String)}</td><td>执行命令，返回影响到的对象数量（-1 表示失败，失败信息已打印）</td></tr>
 *     <tr><td>{@link #executeResult(String)}</td><td>执行命令并拿到完整 {@link CommandResult}（不打印）</td></tr>
 *     <tr><td>{@link #runLoop(Scanner, String)}</td><td>整段接管输入：一直读命令行，直到输入 {@code exit} / {@code 退出}</td></tr>
 * </table>
 *
 * <h2>命令写法</h2>
 * 输入以 {@code /} 或 {@code #} 开头即视为命令（两个前缀等价，用哪个都行）：
 * <pre>
 * /kill @e[type=CommonInsect]
 * /hurt @s 100
 * /help
 * #list
 * </pre>
 *
 * @author AI（DeepSeek）生成
 */
public final class CommandManager {

    /**
     * 主前缀：{@code /}（与 MC 一致）。
     */
    public static final char PREFIX = '/';

    /**
     * 备用前缀：{@code #}（避免与游戏里可能出现的其它输入冲突）。
     */
    public static final char ALT_PREFIX = '#';

    /**
     * 全局调度器。
     */
    private static final CommandDispatcher DISPATCHER = new CommandDispatcher();

    /**
     * 是否开启命令功能（关闭后 {@link #isCommand(String)} 恒为 {@code false}，游戏完全按原样运行）。
     */
    private static boolean enabled = true;

    /**
     * 是否把每条命令的执行都写进日志。
     */
    private static boolean logCommands = true;

    /**
     * 当前玩家生物（由 {@link #setPlayer(LivingThing)} 设置）。
     */
    private static LivingThing player = null;

    /**
     * 当前是否处于战斗中（由 {@code FightStartEventListener} / {@code FightEndEventListener} 维护）。
     */
    private static boolean inFight = false;
    /**
     * 是否已经初始化过（保证官方命令只注册一次）。
     */
    private static boolean initialized = false;

    /**
     * 工具类不允许实例化。
     */
    private CommandManager() {
    }

    /**
     * 初始化命令系统：注册官方命令。
     * <p>
     * 由 {@link cn.gfhnv.game.GameMain#gameInitialize()} 调用。重复调用是安全的：
     * 官方命令只会注册一次，不会把同一套分支挂两遍。
     */
    public static void initialize() {
        if (initialized) {
            LogWriter.writeLog("命令系统已经初始化过，跳过重复注册");
            return;
        }
        initialized = true;
        cn.gfhnv.game.officialStuff.customCommands.OfficialCommands.registerAll(DISPATCHER);
        LogWriter.writeLog("命令系统初始化完成，已注册命令：" + DISPATCHER.getCommandNames());
    }

    /**
     * 判断一行输入是不是命令。
     * <p>
     * 判断条件：命令功能已开启，且去掉首尾空白后第一个字符是 {@value #PREFIX} 或 {@value #ALT_PREFIX}。
     *
     * @param input 输入行
     * @return 是否为命令
     */
    public static boolean isCommand(String input) {
        if (!enabled || input == null) {
            return false;
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        char first = trimmed.charAt(0);
        return first == PREFIX || first == ALT_PREFIX;
    }

    /* ------------------------------------------------------------------
     * 输入命令的方法（预留入口）
     * ------------------------------------------------------------------ */

    /**
     * 处理一行输入：是命令就执行并把结果打印出来。
     * <p>
     * <b>推荐用法</b>——在原来的输入循环里加一行判断即可，不必改动其它逻辑。
     *
     * @param input 输入行
     * @return {@code true} 表示这一行是命令（已处理，调用方应跳过原有逻辑）；
     * {@code false} 表示不是命令，调用方按原样继续
     */
    public static boolean process(String input) {
        return processResult(input) != ProcessResult.NOT_A_COMMAND;
    }

    /**
     * 处理一行输入并返回详细结果（见 {@link ProcessResult}）。
     *
     * @param input 输入行
     * @return 处理结果
     */
    public static ProcessResult processResult(String input) {
        if (!isCommand(input)) {
            return ProcessResult.NOT_A_COMMAND;
        }
        CommandResult result = executeResult(input);
        if (!result.isSuccess()) {
            sendError(result.getError());
            return ProcessResult.FAILED;
        }
        if (result.getMessage() != null && !result.getMessage().isBlank()) {
            sendMessage(result.getMessage());
        }
        return ProcessResult.HANDLED;
    }

    /**
     * 执行一行命令，返回影响到的对象数量。
     * <p>
     * 失败时返回 {@code -1}，并把错误信息打印出来（不会抛异常），因此可以直接在游戏循环里用。
     *
     * @param input 命令文本（可以带前缀，也可以不带）
     * @return 影响到的对象数量；失败返回 {@code -1}
     */
    public static int execute(String input) {
        return execute(input, (Fight) null);
    }

    /**
     * 执行一行命令，并显式指定它所属的战斗。
     *
     * @param input 命令文本
     * @param fight 当前战斗（可为 {@code null}）
     * @return 影响到的对象数量；失败返回 {@code -1}
     */
    public static int execute(String input, Fight fight) {
        CommandResult result = executeResult(input, fight);
        if (!result.isSuccess()) {
            sendError(result.getError());
            return -1;
        }
        if (result.getMessage() != null && !result.getMessage().isBlank()) {
            sendMessage(result.getMessage());
        }
        return result.getResult();
    }

    /**
     * 执行一行命令并返回完整结果（<b>不</b>打印任何东西）。
     * <p>
     * 适合测试、模组内部调用、或者想自己决定怎么展示结果的场合。
     *
     * @param input 命令文本
     * @return 执行结果
     */
    public static CommandResult executeResult(String input) {
        return executeResult(input, null);
    }

    /**
     * 执行一行命令并返回完整结果（<b>不</b>打印任何东西），可指定所属战斗。
     *
     * @param input 命令文本
     * @param fight 当前战斗（{@code null} 表示沿用 {@link CommandSource} 里登记的那一场）
     * @return 执行结果
     */
    public static CommandResult executeResult(String input, Fight fight) {
        if (!enabled) {
            return CommandResult.failure(CommandSyntaxException.create("命令功能已被关闭（CommandManager.setEnabled(false)）"));
        }
        if (input == null || input.isBlank()) {
            return CommandResult.failure(CommandSyntaxException.create("没有输入任何命令"));
        }
        String command = CommandDispatcher.stripPrefix(input);
        if (command.isEmpty()) {
            return CommandResult.failure(CommandSyntaxException.create("「" + input.trim() + "」后面还没有写命令，例如 " + PREFIX + "help"));
        }
        if (fight != null) {
            CommandSource.setCurrentFight(fight);
        }
        CommandSource source = new CommandSource(player);
        try {
            int value = DISPATCHER.execute(command, source);
            if (logCommands) {
                LogWriter.writeLog("执行命令：" + command + " → 影响 " + value + " 个对象");
            }
            return CommandResult.success(value, null).withArguments(source.describeArguments());
        } catch (CommandSyntaxException e) {
            String firstWord = command.split("\\s+", 2)[0];
            CommandNode node = DISPATCHER.getCommandNode(firstWord);
            if (node != null) {
                // 用"建议用法"（含后续参数）而不是只回显命令名：单敲 /give 时能看到 /give <目标> <物品> [数量]
                e.withUsage(node.getSuggestedUsage());
            }
            LogWriter.writeLog("命令失败：" + command + " → " + e.getMessage());
            return CommandResult.failure(e).withArguments(source.describeArguments());
        } catch (RuntimeException e) {
            CommandSyntaxException wrapped = CommandSyntaxException.create(
                    "命令执行时发生内部错误：" + e.getClass().getSimpleName() + ": " + e.getMessage());
            LogWriter.writeLog("命令异常：" + command + " → " + wrapped.getMessage());
            return CommandResult.failure(wrapped);
        }
    }

    /**
     * 整段接管输入：循环读取命令并执行，直到输入 {@code exit} 或 {@code 退出}。
     * <p>
     * 这是「不改输入模式」之外的另一种用法——如果某个界面（例如调试菜单）只想提供命令行，
     * 直接调用本方法即可。
     *
     * @param scanner 输入源；{@code null} 时使用 {@link cn.gfhnv.game.GameMain#SCANNER}
     * @param prompt  每行前的提示文本；{@code null} 时不打印提示
     */
    public static void runLoop(Scanner scanner, String prompt) {
        Scanner effective = scanner == null ? cn.gfhnv.game.GameMain.SCANNER : scanner;
        if (effective == null) {
            return;
        }
        String hint = prompt == null ? "" : prompt;
        while (true) {
            if (!hint.isEmpty()) {
                System.out.print(hint);
            }
            if (!effective.hasNextLine()) {
                return;
            }
            String input = effective.nextLine().trim();
            if (input.equalsIgnoreCase("exit") || input.equals("退出")) {
                return;
            }
            if (input.isEmpty()) {
                continue;
            }
            execute(input);
        }
    }

    /**
     * 打印某条命令的用法（{@code help} 命令内部使用，也可以直接调用）。
     *
     * @param commandName 命令名；传 {@code null} 或空串时打印全部命令
     */
    public static void printUsage(String commandName) {
        if (commandName == null || commandName.isBlank()) {
            StringBuilder builder = new StringBuilder("可用命令：");
            for (String name : DISPATCHER.getCommandNames()) {
                CommandNode node = DISPATCHER.getCommandNode(name);
                builder.append(System.lineSeparator()).append("  ").append(PREFIX).append(name);
                if (node != null && !node.getChildren().isEmpty()) {
                    builder.append("  （子命令：").append(String.join("、", node.getChildrenNames())).append("）");
                }
            }
            builder.append(System.lineSeparator()).append("输入 ").append(PREFIX)
                    .append("help <命令名> 查看具体用法");
            sendMessage(builder.toString());
            return;
        }
        CommandNode node = DISPATCHER.getCommandNode(commandName);
        if (node == null) {
            sendError(CommandSyntaxException.create("没有这个命令：" + commandName));
            return;
        }
        List<String> children = node.getChildrenNames();
        StringBuilder builder = new StringBuilder();
        builder.append(node.getFullUsage());
        if (!children.isEmpty()) {
            builder.append(System.lineSeparator()).append("  可用的下一步：").append(String.join("、", children));
        }
        sendMessage(builder.toString());
    }

    /**
     * @return 当前玩家生物；可能为 {@code null}
     */
    public static LivingThing getPlayer() {
        return player;
    }

    /* ------------------------------------------------------------------
     * 状态设置
     * ------------------------------------------------------------------ */

    /**
     * 设置当前玩家生物（{@code @s} / {@code @p} 等选择器会以它为中心）。
     *
     * @param livingThing 玩家选中的角色；可为 {@code null}
     */
    public static void setPlayer(LivingThing livingThing) {
        player = livingThing;
    }

    /**
     * 让"当前玩家"跟着<b>正在行动的那个我方角色</b>走。
     * <p>
     * <b>为什么需要它</b>：{@link #setPlayer(LivingThing)} 只在 {@code GameMain} 的选人流程里调用，
     * 多角色队伍里<b>最后选的那个会一直占着"玩家"的位置</b>。于是你在酒剑仙的回合里敲
     * {@code /give @s drunkenSword:osmanthusWine}，东西会发给别人
     * （2026-09 实测踩到：日志里 {@code @s} 一直解析成白厄/卡厄斯兰那）。
     * <p>
     * <b>为什么带 ownSide 参数</b>：敌人的回合不该把 {@code @s} 改成敌人 ——
     * 否则你在敌方回合顺手敲一句 {@code /hurt @s 100} 就会打到对面身上。
     * 判据由调用方给（回合循环用 {@code Fight#isOurSide}），这里不重复判断阵营。
     *
     * @param actor   当前行动者；{@code null} 时什么都不做
     * @param ownSide 该行动者是否属于我方
     */
    public static void followActor(LivingThing actor, boolean ownSide) {
        if (actor != null && ownSide) {
            setPlayer(actor);
        }
    }

    /**
     * 标记「战斗开始」，并把这场战斗登记为当前战斗。
     * <p>
     * 建议在 {@code FightStartEventListener} 里调用；即使不调用，
     * {@link #execute(String, Fight)} 也会在每次执行前登记。
     *
     * @param fight 战斗
     */
    public static void setCurrentFight(Fight fight) {
        CommandSource.setCurrentFight(fight);
        inFight = fight != null;
    }

    /**
     * 标记「战斗结束」。
     */
    public static void clearCurrentFight() {
        CommandSource.setCurrentFight(null);
        inFight = false;
    }

    /**
     * @return 当前是否处于战斗中
     */
    public static boolean isInFight() {
        return inFight;
    }

    /**
     * @return 命令功能是否开启
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * 开启/关闭命令功能。关闭后所有输入都会被当成普通输入。
     *
     * @param value 是否开启
     */
    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /**
     * 设置是否把命令执行写进日志文件。
     *
     * @param value 是否记录
     */
    public static void setLogCommands(boolean value) {
        logCommands = value;
    }

    /**
     * @return 全局调度器（注册命令、查询补全都从这里进）
     */
    public static CommandDispatcher getDispatcher() {
        return DISPATCHER;
    }

    /**
     * 注册命令（模组/官方内容用）。
     *
     * @param commands 命令数组
     */
    public static void register(Command... commands) {
        DISPATCHER.register(commands);
    }

    /**
     * @return 已注册的命令名列表
     */
    public static List<String> getRegisteredCommandNames() {
        return DISPATCHER.getCommandNames();
    }

    /**
     * 把一行命令写进日志（调试用）。
     *
     * @param content 内容
     */
    public static void log(String content) {
        LogWriter.writeLog("[命令] " + content);
    }

    /**
     * 把字符串逐字符转成码点文本，用于诊断编码问题。
     *
     * @param text 文本
     * @return 形如 {@code U+006B U+0069 ...} 的文本
     */
    public static String codePointsOf(String text) {
        if (text == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder();
        text.codePoints().forEach(cp -> {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(String.format("U+%04X", cp));
        });
        return builder.toString();
    }

    /**
     * 输出一条命令消息（默认打印到标准输出与日志）。
     *
     * @param message 消息
     */
    public static void sendMessage(String message) {
        if (message == null) {
            return;
        }
        System.out.println(message);
        if (logCommands) {
            LogWriter.writeLog("[命令] " + message);
        }
    }

    /**
     * 输出一条命令错误（打印到标准错误与日志）。
     *
     * @param error 错误
     */
    public static void sendError(CommandSyntaxException error) {
        if (error == null) {
            return;
        }
        System.err.println("[命令错误] " + error.getMessage());
        LogWriter.writeLog("[命令错误] " + error.getMessage());
    }

    /**
     * 把命令系统当前状态转成可读文本（调试用）。
     *
     * @return 状态文本
     */
    public static String describeState() {
        return "CommandManager{enabled=" + enabled
                + ", inFight=" + inFight
                + ", player=" + (player == null ? "无" : player.getName())
                + ", commands=" + DISPATCHER.getCommandNames()
                + ", thingsInWorld=" + World.getThings().size()
                + "}";
    }

    /**
     * 检测「控制台 → JVM」这一段编码是否正常：能不能正确收到中文。
     * <p>
     * <b>为什么需要它</b>：命令系统支持中文参数名与筛选值（如 {@code @e[type=虫皇]}），
     * 而 Windows 的 {@code cmd.exe} 默认用 GBK（代码页 936），
     * 直接 {@code java -jar} 启动时中文可能在进入程序之前就变成 {@code ???}，
     * 表现为「明明有这个生物却选不中」。
     * <p>
     * 判断方式：把一段中文用<b>平台默认字符集</b>编码再解码回来，看是否还是原文
     * （这正是 JVM 读控制台字节时做的事）。不一致就说明两边编码没对齐。
     * <p>
     * 让控制台与 JVM 都用 UTF-8 可以改善这一点：{@code chcp 65001} +
     * {@code java -Dfile.encoding=UTF-8 -jar ...}，仓库根目录的
     * {@code 启动游戏-UTF8.bat} 就是干这个的。
     *
     * @return 是否能够正确接收中文
     */
    public static boolean canReceiveChinese() {
        String sample = "虫皇";
        java.nio.charset.Charset platform = java.nio.charset.Charset.defaultCharset();
        String roundTrip = new String(sample.getBytes(platform), platform);
        return sample.equals(roundTrip);
    }

    /**
     * 返回一段给玩家看的中文输入提示。
     * <p>
     * <b>注意</b>：这个方法检查的是「JVM 的字符集是否与中文相容」，
     * 它<b>不能</b>检查「控制台是否真的把中文送进来了」。在 Windows 的
     * {@code cmd.exe} 里，即使 JVM 侧全是 UTF-8，中文仍可能在原生控制台层被替换掉
     * （{@code ?} / {@code \uFFFD} / 空格等），此时本方法依然返回 true。
     * 所以命令里请优先使用 ASCII 简单类名（如 {@code @e[type=InsectBoss]}）。
     *
     * @return 提示文本
     */
    public static String describeEncoding() {
        java.nio.charset.Charset platform = java.nio.charset.Charset.defaultCharset();
        String head = "编码自检：JVM 字符集 " + platform.name()
                + (canReceiveChinese() ? "（中文在程序内部可用）" : "（与中文不相容）");
        return head + "；注意这只是 JVM 侧检查，"
                + "cmd.exe 可能仍在原生层丢掉中文输入，"
                + "所以命令里建议用 ASCII 选择器：@e[type=InsectBoss]、@e[type=CommonInsect] 等。";
    }

    /**
     * 「输入行处理结果」。
     */
    public enum ProcessResult {
        /**
         * 这一行<b>不是</b>命令，调用方应当按原有逻辑继续处理。
         */
        NOT_A_COMMAND,
        /**
         * 这一行是命令，并且已经处理完毕（成功或失败信息都已输出）。
         */
        HANDLED,
        /**
         * 这一行以命令前缀开头，但命令本身解析/执行失败（错误信息已输出）。
         */
        FAILED
    }
}
