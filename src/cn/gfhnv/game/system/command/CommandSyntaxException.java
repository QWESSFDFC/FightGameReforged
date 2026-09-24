package cn.gfhnv.game.system.command;

/**
 * 命令解析/执行过程中抛出的语法异常。
 * <p>
 * 设计参考《我的世界》Java 版 Brigadier 的 {@code CommandSyntaxException}：
 * <ul>
 *     <li>异常同时携带「上下文（{@link #getContext()}）」与「错误位置（{@link #getCursor()}）」，
 *     因此可以像 MC 一样打印出 {@code xxx<--[HERE]} 这样的定位信息；</li>
 *     <li>{@link #getMessage()} 返回<b>完整</b>可读文本（含定位与用法提示），
 *     {@link #getRawMessage()} 返回不含定位的短文本，便于日志记录。</li>
 * </ul>
 * <p>
 * 建议在命令实现中尽量使用 {@link #create(String)} 等静态工厂，
 * 而不是自己拼错误字符串，这样所有报错风格保持一致。
 *
 * @author AI（DeepSeek）生成
 */
public class CommandSyntaxException extends Exception {

    /**
     * 光标定位标记，与 MC 一致：错误位置以 {@code <--[HERE]} 标出。
     */
    public static final String CONTEXT_MARK = "<--[HERE]";

    /**
     * 错误发生处的原始输入片段（可为 {@code null}，表示无上下文）。
     */
    private final String context;

    /**
     * 错误在 {@link #context} 中的字符下标（基于 0，按 UTF-16 计）。
     */
    private final int cursor;

    /**
     * 不含定位信息的错误描述。
     */
    private final String rawMessage;

    /**
     * 用法提示（可为 {@code null}）。通常由调度器在解析失败时补上，
     * 例如 {@code /kill <目标>}。
     */
    private String usage;

    /**
     * 构造一个语法异常。
     *
     * @param rawMessage 不含定位信息的错误描述
     * @param context    错误发生处的原始输入片段，可为 {@code null}
     * @param cursor     错误在 {@code context} 中的字符下标；负数表示不定位
     */
    public CommandSyntaxException(String rawMessage, String context, int cursor) {
        super(rawMessage);
        this.rawMessage = rawMessage == null ? "未知的命令错误" : rawMessage;
        this.context = context;
        this.cursor = cursor;
    }

    /**
     * 在指定读取器的当前位置构造一个错误。
     *
     * @param reader  正在读取的输入
     * @param message 错误描述
     * @return 语法异常
     */
    public static CommandSyntaxException at(StringReader reader, String message) {
        if (reader == null) {
            return new CommandSyntaxException(message, null, -1);
        }
        return new CommandSyntaxException(message, reader.getString(), reader.getCursor());
    }

    /**
     * 在指定读取器的指定位置构造一个错误。
     *
     * @param reader  正在读取的输入
     * @param cursor  错误位置
     * @param message 错误描述
     * @return 语法异常
     */
    public static CommandSyntaxException at(StringReader reader, int cursor, String message) {
        if (reader == null) {
            return new CommandSyntaxException(message, null, -1);
        }
        return new CommandSyntaxException(message, reader.getString(), cursor);
    }

    /**
     * 构造一个不定位的语法异常。
     *
     * @param message 错误描述
     * @return 语法异常
     */
    public static CommandSyntaxException create(String message) {
        return new CommandSyntaxException(message, null, -1);
    }

    /**
     * 构造「未知命令」异常。
     *
     * @param command 命令名
     * @return 语法异常
     */
    public static CommandSyntaxException unknownCommand(String command) {
        return create("未知的命令：" + command);
    }

    /**
     * 构造「需要一个字面量」异常。
     *
     * @param reader   输入读取器
     * @param expected 期望的字面量
     * @return 语法异常
     */
    public static CommandSyntaxException expectedLiteral(StringReader reader, String expected) {
        return at(reader, "此处需要字面量「" + expected + "」，实际读到「" + reader.peekWord() + "」");
    }

    /**
     * 构造「输入意外结束」异常。
     *
     * @param reader   输入读取器
     * @param expected 期望内容的可读描述，例如「一个整数」
     * @return 语法异常
     */
    public static CommandSyntaxException expectedInput(StringReader reader, String expected) {
        return at(reader, "此处需要" + expected + "，但输入已经结束");
    }

    /**
     * @return 不含定位信息的错误描述
     */
    public String getRawMessage() {
        return rawMessage;
    }

    /* ------------------------------------------------------------------
     * 静态工厂：常用的错误构造方式
     * ------------------------------------------------------------------ */

    /**
     * @return 错误发生处的原始输入片段；可能为 {@code null}
     */
    public String getContext() {
        return context;
    }

    /**
     * @return 错误在 {@link #getContext()} 中的字符下标；负数表示不定位
     */
    public int getCursor() {
        return cursor;
    }

    /**
     * @return 用法提示；可能为 {@code null}
     */
    public String getUsage() {
        return usage;
    }

    /**
     * 补上用法提示。若已有用法提示则不会被覆盖。
     *
     * @param usage 用法提示，例如 {@code /kill <目标>}
     * @return 当前异常自身（便于链式书写）
     */
    public CommandSyntaxException withUsage(String usage) {
        if (this.usage == null && usage != null && !usage.isBlank()) {
            this.usage = usage;
        }
        return this;
    }

    /**
     * 与 MC 一致：把错误位置用 {@link #CONTEXT_MARK} 标出。
     *
     * @return 带定位的上下文；无法定位时返回 {@code null}
     */
    public String getContextWithMark() {
        if (context == null || cursor < 0) {
            return null;
        }
        int index = Math.min(cursor, context.length());
        return context.substring(0, index) + CONTEXT_MARK + context.substring(index);
    }

    /**
     * @return 完整错误信息：错误描述 + （若有）定位 + （若有）用法提示
     */
    @Override
    public String getMessage() {
        StringBuilder builder = new StringBuilder(rawMessage);
        String marked = getContextWithMark();
        if (marked != null) {
            builder.append(": ").append(marked);
        }
        if (usage != null && !usage.isBlank()) {
            builder.append("（用法：").append(usage).append("）");
        }
        return builder.toString();
    }
}
