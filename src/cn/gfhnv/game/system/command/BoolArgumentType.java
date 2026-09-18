package cn.gfhnv.game.system.command;

/**
 * 「布尔值」参数类型。
 * <p>
 * 为了照顾中文输入习惯，{@code true}/{@code false} 之外还额外接受
 * {@code 是}/{@code 否}、{@code 开}/{@code 关}（大小写不敏感）。
 * 输出给玩家时请用 {@link #format(boolean)}，保证提示文本统一为 {@code true}/{@code false}。
 *
 * @author AI（DeepSeek）生成
 */
public class BoolArgumentType implements ArgumentType<Boolean> {

    /**
     * 共享实例（本类无状态）。
     */
    private static final BoolArgumentType INSTANCE = new BoolArgumentType();

    /**
     * 构造一个布尔参数类型。
     */
    public BoolArgumentType() {
    }

    /**
     * @return 布尔参数类型
     */
    public static BoolArgumentType bool() {
        return INSTANCE;
    }

    /**
     * 把布尔值格式化为统一的命令行文本。
     *
     * @param value 布尔值
     * @return {@code "true"} 或 {@code "false"}
     */
    public static String format(boolean value) {
        return value ? "true" : "false";
    }

    @Override
    public Boolean parse(StringReader reader) throws CommandSyntaxException {
        String word = reader.readWord();
        if (word.equalsIgnoreCase("true") || word.equals("是") || word.equals("开")) {
            return Boolean.TRUE;
        }
        if (word.equalsIgnoreCase("false") || word.equals("否") || word.equals("关")) {
            return Boolean.FALSE;
        }
        throw CommandSyntaxException.at(reader, "「" + word + "」不是布尔值，只能填 true / false（也接受 是/否、开/关）");
    }

    @Override
    public String toString() {
        return "bool";
    }
}
