package cn.gfhnv.game.utils;

import java.io.Console;

/**
 * 控制台 ANSI 着色的极简工具（<b>不引入任何第三方库</b>，只用转义序列）。
 * <p>
 * <b>为什么要"能不能上色"要判断一遍</b>：Windows 的旧版 conhost（cmd.exe 默认宿主）
 * 需要显式打开虚拟终端处理（VT）才会解释 ANSI 转义序列，否则会把
 * {@code \u001B[91m} 原样打出来 —— 满屏乱码比没有颜色更糟。
 * 所以默认是 {@code auto}：只在"已知支持"的终端上色。
 * <p>
 * <b>跨平台</b>：转义序列本身是 POSIX 终端自古以来的标准，所以 Linux、macOS、
 * Termux（Android）这些终端<b>天然支持</b>，本类里也没有任何平台专属代码。
 * 这些系统上 {@code TERM} 几乎总是有值（{@code xterm-256color} 之类），
 * 自动识别因此会直接开启颜色；想关掉用 {@code -Ddsh.color=off}。
 * <p>
 * 三个开关（JVM 参数 {@code -Ddsh.color=...}）：
 * <ul>
 *     <li>{@code auto}（默认）—— 认出 Windows Terminal / Git Bash(MinTTY) / VS Code / IDEA /
 *     ConEmu / ANSICON / 各类 Unix 终端 这类宿主才上色；</li>
 *     <li>{@code on} / {@code true} / {@code 1} —— 强制上色（cmd 里先
 *     {@code reg add HKCU\Console /v VirtualTerminalLevel /t REG_DWORD /d 1} 打开 VT 之后就能用）；</li>
 *     <li>{@code off} / {@code false} / {@code 0} —— 强制纯文本。</li>
 * </ul>
 * {@code auto} 另外尊重两个约定：环境变量 {@code NO_COLOR}（非空即"别上色"，见 no-color.org）、
 * {@code TERM=dumb}，以及"输出被重定向到文件/管道"（{@code > log.txt}、{@code | tee}）——
 * 这几种情况都不上色，免得文件里混进一堆看不见的转义序列。
 * 强制 {@code on} 时一切照旧（想连重定向也染色就用它）。
 * <p>
 * 颜色本身只是"包一层转义 + {@link #RESET}"，没有任何状态，
 * 所以 {@link #paint(String, String)} 传进来的文本可以是任意内容。
 *
 * @author AI（DeepSeek）生成
 */
public final class ConsoleColor {

    /**
     * 复位（每段着色文本后面都要跟一个，否则后面所有输出都会被染色）。
     */
    public static final String RESET = "\u001B[0m";

    /**
     * 亮青：回合头、技能名。
     */
    public static final String CYAN = "\u001B[96m";

    /**
     * 亮红：伤害数字。
     */
    public static final String RED = "\u001B[91m";

    /**
     * 亮绿：治疗/回复。
     */
    public static final String GREEN = "\u001B[92m";

    /**
     * 亮黄：重要事件（阶段切换、奖励…）。
     */
    public static final String YELLOW = "\u001B[93m";

    /**
     * 亮洋红：持续伤害（【侵蚀】这类）。
     */
    public static final String MAGENTA = "\u001B[95m";

    /**
     * 灰：次要信息（HP、剩余回合…）。
     */
    public static final String DIM = "\u001B[90m";

    /**
     * 当前是否上色。
     */
    private static boolean enabled = detect();

    /**
     * 工具类，不允许实例化。
     */
    private ConsoleColor() {
    }

    /**
     * @return 当前是否上色
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * 手动开关（自测/调试用；正式流程由 {@code -Ddsh.color=} 决定）。
     *
     * @param value {@code true} 上色
     */
    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /**
     * 给一段文本上色。关闭着色时原样返回。
     *
     * @param color 颜色转义序列（本类的常量之一）
     * @param text  文本；{@code null} 时原样返回
     * @return 上色后的文本
     */
    public static String paint(String color, String text) {
        if (!enabled || text == null || text.isEmpty()) {
            return text;
        }
        return color + text + RESET;
    }

    /**
     * @param text 文本
     * @return 亮青文本
     */
    public static String cyan(String text) {
        return paint(CYAN, text);
    }

    /**
     * @param text 文本
     * @return 亮红文本
     */
    public static String red(String text) {
        return paint(RED, text);
    }

    /**
     * @param text 文本
     * @return 亮绿文本
     */
    public static String green(String text) {
        return paint(GREEN, text);
    }

    /**
     * @param text 文本
     * @return 亮黄文本
     */
    public static String yellow(String text) {
        return paint(YELLOW, text);
    }

    /**
     * @param text 文本
     * @return 亮洋红文本
     */
    public static String magenta(String text) {
        return paint(MAGENTA, text);
    }

    /**
     * @param text 文本
     * @return 灰色文本
     */
    public static String dim(String text) {
        return paint(DIM, text);
    }

    /**
     * 判断要不要上色。
     * <p>
     * 优先级：{@code -Ddsh.color=on|off} ＞ {@code NO_COLOR} ＞ 自动识别。
     *
     * @return 是否上色
     */
    private static boolean detect() {
        String setting = System.getProperty("dsh.color", "auto").trim().toLowerCase();
        if (setting.equals("on") || setting.equals("true") || setting.equals("1")) {
            return true;
        }
        if (setting.equals("off") || setting.equals("false") || setting.equals("0")) {
            return false;
        }
        if (isSet("NO_COLOR")) {
            return false;   // 社区约定：这个变量非空即"别上色"
        }
        if ("dumb".equalsIgnoreCase(System.getenv("TERM"))) {
            return false;   // dumb 终端本来就不认转义序列
        }
        boolean hostKnown = isSet("WT_SESSION")             // Windows Terminal
                || isSet("TERM_PROGRAM")                    // VS Code / mintty 等
                || isSet("TERMINAL_EMULATOR")               // JetBrains 系（IDEA 内置终端）
                || isSet("ConEmuANSI")                      // ConEmu
                || isSet("ANSICON")                         // ANSICON
                || isSet("MSYSTEM");                        // Git Bash / MinTTY / MSYS2
        if (!hostKnown && !isTerminal()) {
            // 既没有"这是终端"的证据，标准输出又不是 tty —— 基本就是 > log.txt / | tee，
            // 这时候上色只会把看不见的转义序列写进文件。
            // 之所以要 hostKnown 这一半：Windows 的 MinTTY（Git Bash）是"用管道模拟 pty"，
            // 原生程序问出来永远不是 tty，一刀切会把它误杀。
            return false;
        }
        return hostKnown || isSet("TERM");
    }

    /**
     * @param name 环境变量名
     * @return 变量存在且不是空串
     */
    private static boolean isSet(String name) {
        String value = System.getenv(name);
        return value != null && !value.isEmpty();
    }

    /**
     * 标准输出是不是接在终端上（{@code > log.txt}、{@code | tee log.txt} 这类重定向时为 false）。
     * <p>
     * Java 22 起 {@code System.console()} 即便被重定向也会返回对象（为的是能问
     * {@link Console#isTerminal()}），所以这里必须看 {@code isTerminal()}，
     * 不能只看是否为 {@code null}。它在 POSIX 上等价于 {@code isatty(stdin/stdout)}，
     * 在 Windows 上看标准输入输出是不是字符设备。
     *
     * @return 是否在终端上
     */
    private static boolean isTerminal() {
        Console console = System.console();
        return console != null && console.isTerminal();
    }
}
