package cn.gfhnv.game.system.command;

/**
 * 「单词」参数类型：读取一段不含空白的连续字符。
 * <p>
 * 对应 MC 的 {@code word()}，可选地限制字符集合（例如只允许
 * {@code [a-zA-Z0-9_.-]}，或只允许中文/字母）。若需要读取含空格的文本，
 * 请改用 {@link StringArgumentType#string()} 或 {@link StringArgumentType#greedyString()}。
 *
 * @author AI（DeepSeek）生成
 */
public class WordArgumentType implements ArgumentType<String> {

    /**
     * 无字符限制的共享实例（本类不可变，可安全共享）。
     */
    private static final WordArgumentType ANY = new WordArgumentType(false, false);

    /**
     * 是否只允许字母与数字。
     */
    private final boolean alphanumericOnly;

    /**
     * 是否禁止以下划线开头的词。
     */
    private final boolean excludeStartingUnderscore;

    /**
     * 构造一个单词参数类型。
     *
     * @param alphanumericOnly          是否只允许字母与数字
     * @param excludeStartingUnderscore 是否禁止以下划线开头
     */
    public WordArgumentType(boolean alphanumericOnly, boolean excludeStartingUnderscore) {
        this.alphanumericOnly = alphanumericOnly;
        this.excludeStartingUnderscore = excludeStartingUnderscore;
    }

    /**
     * @return 无字符限制的单词参数类型
     */
    public static WordArgumentType word() {
        return ANY;
    }

    /**
     * @return 只允许字母与数字的单词参数类型
     */
    public static WordArgumentType alphanumericWord() {
        return new WordArgumentType(true, false);
    }

    /**
     * @param excludeStartingUnderscore 是否禁止以下划线开头
     * @return 只允许字母与数字的单词参数类型
     */
    public static WordArgumentType alphanumericWord(boolean excludeStartingUnderscore) {
        return new WordArgumentType(true, excludeStartingUnderscore);
    }

    /**
     * 判断字符是否被允许。
     *
     * @param c 字符
     * @return 是否允许
     */
    private static boolean isAllowed(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        reader.skipWhitespace();
        if (!reader.canRead()) {
            throw CommandSyntaxException.expectedInput(reader, "一个单词");
        }
        int start = reader.getCursor();
        String word = reader.readWord();
        if (excludeStartingUnderscore && word.startsWith("_")) {
            throw CommandSyntaxException.at(reader, start, "单词不能以下划线开头");
        }
        if (alphanumericOnly) {
            for (int i = 0; i < word.length(); i++) {
                char c = word.charAt(i);
                if (!isAllowed(c)) {
                    throw CommandSyntaxException.at(reader, start + i,
                            "单词只能包含字母、数字与下划线，但出现了「" + c + "」");
                }
            }
        }
        return word;
    }

    @Override
    public String toString() {
        return alphanumericOnly ? "word(alphanumeric)" : "word";
    }
}
