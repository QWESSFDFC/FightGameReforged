package cn.gfhnv.game.system.command;

/**
 * 「字符串」参数类型：读取文本，支持引号，或直接吃掉整行剩余内容。
 * <p>
 * 三种模式（对应 MC 的 {@code string()} / {@code greedyString()}）：
 * <ul>
 *     <li>{@link #word()}：只读一个词（等价于 {@link WordArgumentType#word()}）；</li>
 *     <li>{@link #string()}：一个词，或 {@code "带空格的文本"}；</li>
 *     <li>{@link #greedyString()}：把剩余整行都吃掉，<b>只能作为最后一个参数</b>。</li>
 * </ul>
 *
 * @author AI（DeepSeek）生成
 */
public class StringArgumentType implements ArgumentType<String> {

    /**
     * 读取模式。
     */
    private final Mode mode;

    /**
     * 构造一个字符串参数类型。
     *
     * @param mode 读取模式
     */
    public StringArgumentType(Mode mode) {
        this.mode = mode == null ? Mode.QUOTABLE_PHRASE : mode;
    }

    /**
     * @return 只读一个词的字符串参数类型
     */
    public static StringArgumentType word() {
        return new StringArgumentType(Mode.SINGLE_WORD);
    }

    /**
     * @return 一个词或一段带引号文本的字符串参数类型
     */
    public static StringArgumentType string() {
        return new StringArgumentType(Mode.QUOTABLE_PHRASE);
    }

    /**
     * @return 吃掉整行剩余内容的字符串参数类型
     */
    public static StringArgumentType greedyString() {
        return new StringArgumentType(Mode.GREEDY_PHRASE);
    }

    /**
     * @return 读取模式
     */
    public Mode getMode() {
        return mode;
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        switch (mode) {
            case SINGLE_WORD -> {
                return reader.readWord();
            }
            case GREEDY_PHRASE -> {
                return reader.readString();
            }
            default -> {
                return reader.readQuotedString();
            }
        }
    }

    @Override
    public String toString() {
        return switch (mode) {
            case SINGLE_WORD -> "word";
            case GREEDY_PHRASE -> "greedyString";
            default -> "string";
        };
    }

    /**
     * 读取模式。
     */
    public enum Mode {
        /**
         * 只读一个词。
         */
        SINGLE_WORD,
        /**
         * 一个词或一段带引号的文本。
         */
        QUOTABLE_PHRASE,
        /**
         * 剩余整行。
         */
        GREEDY_PHRASE
    }
}
