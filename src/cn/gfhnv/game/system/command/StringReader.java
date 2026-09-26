package cn.gfhnv.game.system.command;

/**
 * 命令输入读取器：在一条命令字符串上维护一个「光标」，供各个参数类型顺序读取。
 * <p>
 * 与《我的世界》Java 版 Brigadier 中的 {@code StringReader} 类似，它把
 * 「字符串切分」这件事从命令实现里抽出来，交给参数类型自己完成：
 * <pre>{@code
 * StringReader reader = new StringReader("say hello world");
 * String name = reader.readWord();      // "say"
 * String rest = reader.readString();    // "hello world"
 * reader.canRead();                     // false，已读完
 * }</pre>
 * <p>
 * 所有读取方法都会跳过前导空白；读取失败会抛出 {@link CommandSyntaxException}
 * 并携带定位信息。本类<b>不是</b>线程安全的。
 *
 * @author AI（DeepSeek）生成
 */
public class StringReader {

    /**
     * 被读取的完整字符串。
     */
    private final String string;

    /**
     * 当前光标位置（0 表示尚未读取任何字符）。
     */
    private int cursor;

    /**
     * 构造一个读取器。
     *
     * @param string 被读取的字符串，{@code null} 会被当作空串
     */
    public StringReader(String string) {
        this.string = string == null ? "" : string;
        this.cursor = 0;
    }

    /**
     * 判断是否为空白字符。
     *
     * @param c 待判断字符
     * @return 是否为空白
     */
    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    /**
     * @return 被读取的完整字符串
     */
    public String getString() {
        return string;
    }

    /**
     * @return 当前光标位置
     */
    public int getCursor() {
        return cursor;
    }

    /**
     * 设置光标位置。越界值会被截断到合法范围。
     *
     * @param cursor 新光标位置
     */
    public void setCursor(int cursor) {
        this.cursor = Math.max(0, Math.min(cursor, string.length()));
    }

    /**
     * @return 是否还有字符可读（空白不计）
     */
    public boolean canRead() {
        return canRead(cursor);
    }

    /**
     * 判断指定位置之后是否还有非空白字符。
     *
     * @param position 起始位置
     * @return 是否还有字符可读
     */
    public boolean canRead(int position) {
        if (position < 0 || position >= string.length()) {
            return false;
        }
        for (int i = position; i < string.length(); i++) {
            if (!isWhitespace(string.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 跳过所有空白字符。
     */
    public void skipWhitespace() {
        while (cursor < string.length() && isWhitespace(string.charAt(cursor))) {
            cursor++;
        }
    }

    /**
     * @return 当前字符；已到末尾返回 {@code '\0'}
     */
    public char peek() {
        return peek(0);
    }

    /**
     * @param offset 相对光标的偏移
     * @return 偏移处的字符；越界返回 {@code '\0'}
     */
    public char peek(int offset) {
        int index = cursor + offset;
        if (index < 0 || index >= string.length()) {
            return '\0';
        }
        return string.charAt(index);
    }

    /**
     * @return 从光标（含）到末尾的原始字符串
     */
    public String getRemaining() {
        return string.substring(cursor);
    }

    /**
     * 读取一段「成对括号」文本：从当前字符开始，一直读到最外层 {@code {}} / {@code []} 闭合，
     * 或遇到不在括号内的空白为止。<b>括号内的空格不会断开</b>，引号内的括号也不计数。
     * <p>
     * 给 {@code /data merge @s {hp: 20}} 这类"NBT 字面量"用（{@link #readWord()} 会在空格处断开，
     * 所以那个方法读不了这种参数）。解析交给 {@code Snbt}，本方法只管取字符。
     *
     * @return 读到的文本（已去首尾空白）
     * @throws CommandSyntaxException 输入已结束或括号没有闭合时抛出
     */
    public String readBalanced() throws CommandSyntaxException {
        skipWhitespace();
        if (cursor >= string.length()) {
            throw CommandSyntaxException.expectedInput(this, "一段 NBT 文本");
        }
        int start = cursor;
        int depth = 0;
        char quote = '\0';
        while (cursor < string.length()) {
            char c = string.charAt(cursor);
            if (quote != '\0') {
                if (c == quote) {
                    quote = '\0';
                }
                cursor++;
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                cursor++;
                continue;
            }
            if (c == '{' || c == '[') {
                depth++;
                cursor++;
                continue;
            }
            if (c == '}' || c == ']') {
                depth--;
                cursor++;
                if (depth <= 0) {
                    break;
                }
                continue;
            }
            if (depth == 0 && isWhitespace(c)) {
                break;
            }
            cursor++;
        }
        if (quote != '\0') {
            throw CommandSyntaxException.create("引号没有闭合：" + string.substring(start));
        }
        if (depth > 0) {
            throw CommandSyntaxException.create("括号没有闭合：" + string.substring(start));
        }
        return string.substring(start, cursor).trim();
    }

    /**
     * 读取一个「词」：一段不含空白的连续字符。
     * <p>
     * 读取前会跳过前导空白。若已经没有内容可读，抛出
     * {@link CommandSyntaxException#expectedInput(StringReader, String)}。
     *
     * @return 读取到的词
     * @throws CommandSyntaxException 输入已结束时抛出
     */
    public String readWord() throws CommandSyntaxException {
        skipWhitespace();
        if (cursor >= string.length()) {
            throw CommandSyntaxException.expectedInput(this, "一段文本");
        }
        int start = cursor;
        while (cursor < string.length() && !isWhitespace(string.charAt(cursor))) {
            cursor++;
        }
        return string.substring(start, cursor);
    }

    /**
     * 读取剩余全部内容（去首尾空白）。读取成功后光标停在末尾。
     * <p>
     * 对应 MC 的 {@code greedyString()}：用于「命令的最后一个参数吃掉整行」。
     *
     * @return 剩余内容
     * @throws CommandSyntaxException 输入已结束时抛出
     */
    public String readString() throws CommandSyntaxException {
        skipWhitespace();
        if (cursor >= string.length()) {
            throw CommandSyntaxException.expectedInput(this, "一段文本");
        }
        String result = string.substring(cursor).trim();
        cursor = string.length();
        return result;
    }

    /**
     * 读取带引号的字符串。
     * <p>
     * 支持 {@code "双引号"} 与 {@code '单引号'}；引号内可用 {@code \"} 转义。
     * 若当前字符不是引号，则退化为读取一个词（见 {@link #readWord()}）。
     * 对应 MC 的 {@code string()}：既能读 {@code hello}，也能读 {@code "hello world"}。
     *
     * @return 读取到的字符串（不含外层引号）
     * @throws CommandSyntaxException 引号未闭合或输入已结束时抛出
     */
    public String readQuotedString() throws CommandSyntaxException {
        skipWhitespace();
        if (cursor >= string.length()) {
            throw CommandSyntaxException.expectedInput(this, "一段文本");
        }
        char quote = string.charAt(cursor);
        if (quote != '"' && quote != '\'') {
            return readWord();
        }
        cursor++;
        StringBuilder builder = new StringBuilder();
        boolean escaped = false;
        while (cursor < string.length()) {
            char c = string.charAt(cursor);
            cursor++;
            if (escaped) {
                builder.append(c);
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == quote) {
                return builder.toString();
            }
            builder.append(c);
        }
        throw CommandSyntaxException.at(this, string.length(), "引号没有闭合");
    }

    /**
     * 查看接下来的一个词，但不移动光标。
     *
     * @return 接下来的词；没有则返回空串
     */
    public String peekWord() {
        int saved = cursor;
        try {
            skipWhitespace();
            int start = cursor;
            while (cursor < string.length() && !isWhitespace(string.charAt(cursor))) {
                cursor++;
            }
            return string.substring(start, cursor);
        } finally {
            cursor = saved;
        }
    }

    @Override
    public String toString() {
        return "StringReader{cursor=" + cursor + ", string='" + string + "'}";
    }
}
