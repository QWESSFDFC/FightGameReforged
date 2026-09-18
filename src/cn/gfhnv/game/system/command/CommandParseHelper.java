package cn.gfhnv.game.system.command;

/**
 * 命令参数解析的公共小工具（包内使用）。
 * <p>
 * 把 {@link IntegerArgumentType} / {@link LongArgumentType} / {@link DoubleArgumentType}
 * 共用的「跳过空白 → 截取到下一个空白 → 解析 → 失败定位 → 范围检查」流程集中到一处，
 * 避免三个数值类型各写一遍。
 *
 * @author AI（DeepSeek）生成
 */
final class CommandParseHelper {

    /**
     * 工具类不允许实例化。
     */
    private CommandParseHelper() {
    }

    /**
     * 读取「当前这一段」数字文本。
     *
     * @param reader 输入读取器
     * @param what   类型的可读名称，用于报错（如「整数」）
     * @return 数字文本
     * @throws CommandSyntaxException 输入已结束时抛出
     */
    static String readNumberText(StringReader reader, String what) throws CommandSyntaxException {
        reader.skipWhitespace();
        if (!reader.canRead()) {
            throw CommandSyntaxException.expectedInput(reader, "一个" + what);
        }
        int start = reader.getCursor();
        while (reader.canRead() && !isBlank(reader.peek())) {
            reader.setCursor(reader.getCursor() + 1);
        }
        return reader.getString().substring(start, reader.getCursor());
    }

    /**
     * 判断字符是否为空白。
     *
     * @param c 字符
     * @return 是否为空白
     */
    static boolean isBlank(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    /**
     * 构造「不是合法数字」的异常。
     *
     * @param reader 输入读取器
     * @param text   用户输入的文本
     * @param what   类型的可读名称
     * @return 语法异常
     */
    static CommandSyntaxException notANumber(StringReader reader, String text, String what) {
        return CommandSyntaxException.at(reader, "「" + text + "」不是一个合法的" + what);
    }

    /**
     * 构造「超出范围」的异常。
     *
     * @param reader 输入读取器
     * @param value  实际值
     * @param min    允许的最小值
     * @param max    允许的最大值
     * @return 语法异常
     */
    static CommandSyntaxException outOfRange(StringReader reader, double value, double min, double max) {
        return CommandSyntaxException.at(reader, "数值 " + value + " 超出允许范围 [" + min + ", " + max + "]");
    }
}
