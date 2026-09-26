package cn.gfhnv.game.data;

/**
 * SNBT —— NBT 的文本形式（MC 的那套写法）。
 * <p>
 * 支持：复合标签 {@code {key:value}}、列表 {@code [a,b]}、双/单引号字符串（带 {@code \\} 转义）、
 * 数字后缀 {@code 1b} / {@code 20L} / {@code 1.5d}、{@code true} / {@code false}。
 * <p>
 * <b>放宽的一处</b>：不带引号的裸词也当成字符串（MC 要求字符串必须加引号）。
 * 这样 {@code /data modify ... set hello} 才有意义；代价是"看起来像数字的裸词"永远优先当数字。
 * <p>
 * <b>不做</b>：{@code {k:v}} 过滤器、数组 {@code [I;1,2]}、注释 —— 本版用不到。
 * 解析失败一律抛 {@link IllegalArgumentException}，消息里带出错位置与附近的原文。
 *
 * @author AI（DeepSeek）生成
 */
public final class Snbt {

    /**
     * 工具类，不允许实例化。
     */
    private Snbt() {
    }

    /**
     * 解析一段 SNBT 文本。
     *
     * @param text 文本，例如 {@code {hp:20L,name:"白厄"}}
     * @return 解析出来的标签
     * @throws IllegalArgumentException 语法错误（消息里有位置与附近原文）
     */
    public static NbtTag parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("要解析的 NBT 文本是 null");
        }
        Cursor cursor = new Cursor(text);
        NbtTag tag = cursor.readValue();
        cursor.skipWhitespace();
        if (!cursor.atEnd()) {
            throw cursor.error("值已经读完了，后面还有多余内容");
        }
        return tag;
    }

    /**
     * 解析一段 SNBT 文本，并要求根是复合标签（{@code /data merge} 用得上）。
     *
     * @param text 文本
     * @return 复合标签
     * @throws IllegalArgumentException 语法错误或根不是复合标签
     */
    public static NbtCompound parseCompound(String text) {
        NbtTag tag = parse(text);
        if (!(tag instanceof NbtCompound compound)) {
            throw new IllegalArgumentException("这里需要一个复合标签（用 { } 包起来），实际拿到的是 " + tag.type());
        }
        return compound;
    }

    /**
     * 解析用的游标。
     */
    private static final class Cursor {

        /**
         * 原文。
         */
        private final String text;

        /**
         * 当前位置。
         */
        private int pos;

        /**
         * @param text 原文
         */
        Cursor(String text) {
            this.text = text;
        }

        /**
         * @return 是否已到末尾
         */
        boolean atEnd() {
            return pos >= text.length();
        }

        /**
         * @return 当前字符；到末尾返回 {@code '\0'}
         */
        char peek() {
            return atEnd() ? '\0' : text.charAt(pos);
        }

        /**
         * 跳过空白。
         */
        void skipWhitespace() {
            while (!atEnd() && Character.isWhitespace(peek())) {
                pos++;
            }
        }

        /**
         * @param message 说明
         * @return 带位置信息的异常
         */
        IllegalArgumentException error(String message) {
            int from = Math.max(0, pos - 12);
            int to = Math.min(text.length(), pos + 12);
            return new IllegalArgumentException(message
                    + "（位置 " + pos + "，附近：" + text.substring(from, to) + "）");
        }

        /**
         * @return 一个值（复合/列表/字符串/数字/裸词）
         */
        NbtTag readValue() {
            skipWhitespace();
            if (atEnd()) {
                throw error("内容提前结束了，这里应该有一个值");
            }
            char c = peek();
            if (c == '{') {
                return readCompound();
            }
            if (c == '[') {
                return readList();
            }
            if (c == '"' || c == '\'') {
                return new NbtString(readQuoted(c));
            }
            return readBare();
        }

        /**
         * @return 复合标签
         */
        NbtCompound readCompound() {
            pos++;
            NbtCompound compound = new NbtCompound();
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return compound;
            }
            while (true) {
                skipWhitespace();
                if (atEnd()) {
                    throw error("复合标签没有闭合（缺 }）");
                }
                String key = (peek() == '"' || peek() == '\'') ? readQuoted(peek()) : readBareWord();
                skipWhitespace();
                if (peek() != ':') {
                    throw error("键「" + key + "」后面要跟一个冒号");
                }
                pos++;
                compound.put(key, readValue());
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    pos++;
                    continue;
                }
                if (c == '}') {
                    pos++;
                    return compound;
                }
                throw error("复合标签里出现了意外字符「" + c + "」（应当是 , 或 }）");
            }
        }

        /**
         * @return 列表
         */
        NbtList readList() {
            pos++;
            NbtList list = new NbtList();
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(readValue());
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    pos++;
                    continue;
                }
                if (c == ']') {
                    pos++;
                    return list;
                }
                throw error("列表里出现了意外字符「" + c + "」（应当是 , 或 ]）");
            }
        }

        /**
         * @param quote 引号字符
         * @return 引号内的原文（已处理转义）
         */
        String readQuoted(char quote) {
            pos++;
            StringBuilder builder = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw error("字符串没有闭合（缺 " + quote + "）");
                }
                char c = text.charAt(pos++);
                if (c == quote) {
                    return builder.toString();
                }
                if (c == '\\') {
                    if (atEnd()) {
                        throw error("转义符后面没有字符了");
                    }
                    char escaped = text.charAt(pos++);
                    switch (escaped) {
                        case 'n' -> builder.append('\n');
                        case 'r' -> builder.append('\r');
                        case 't' -> builder.append('\t');
                        default -> builder.append(escaped);
                    }
                    continue;
                }
                builder.append(c);
            }
        }

        /**
         * @return 一个裸词（不含空白、逗号、花括号、方括号、冒号）
         */
        String readBareWord() {
            int start = pos;
            while (!atEnd()) {
                char c = peek();
                if (Character.isWhitespace(c) || c == ',' || c == '{' || c == '}' || c == '[' || c == ']' || c == ':') {
                    break;
                }
                pos++;
            }
            return text.substring(start, pos);
        }

        /**
         * @return 按后缀与字面量猜出来的标签
         */
        NbtTag readBare() {
            String word = readBareWord();
            if (word.isEmpty()) {
                throw error("这里应该有一个值");
            }
            if ("true".equalsIgnoreCase(word)) {
                return new NbtByte(true);
            }
            if ("false".equalsIgnoreCase(word)) {
                return new NbtByte(false);
            }
            char last = word.charAt(word.length() - 1);
            String body = word.substring(0, word.length() - 1);
            if (last == 'b' || last == 'B') {
                try {
                    return new NbtByte(Byte.parseByte(body));
                } catch (NumberFormatException ignored) {
                    // 不是字节字面量，往下当普通数字/字符串试
                }
            }
            if (last == 'l' || last == 'L') {
                try {
                    return new NbtLong(Long.parseLong(body));
                } catch (NumberFormatException ignored) {
                    // 同上
                }
            }
            if (last == 'd' || last == 'D' || last == 'f' || last == 'F') {
                try {
                    return new NbtDouble(Double.parseDouble(body));
                } catch (NumberFormatException ignored) {
                    // 同上
                }
            }
            try {
                return NbtTag.of(Long.parseLong(word));
            } catch (NumberFormatException ignored) {
                // 不是整数，继续试小数
            }
            try {
                return new NbtDouble(Double.parseDouble(word));
            } catch (NumberFormatException ignored) {
                // 也不是小数 → 当裸字符串
            }
            return new NbtString(word);
        }
    }
}
