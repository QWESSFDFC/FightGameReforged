package cn.gfhnv.game.system.command;

/**
 * 「整数」参数类型。
 * <p>
 * 对应 MC 的 {@code integer(min, max)}，可选地限制取值范围：
 * <pre>{@code
 * IntegerArgumentType.integer()          // 任意 int
 * IntegerArgumentType.integer(1)         // 至少 1
 * IntegerArgumentType.integer(1, 99)     // 1 ~ 99
 * }</pre>
 *
 * @author AI（DeepSeek）生成
 */
public class IntegerArgumentType implements ArgumentType<Integer> {

    /**
     * 允许的最小值。
     */
    private final int minimum;

    /**
     * 允许的最大值。
     */
    private final int maximum;

    /**
     * 构造一个整数参数类型。
     *
     * @param minimum 允许的最小值
     * @param maximum 允许的最大值
     */
    public IntegerArgumentType(int minimum, int maximum) {
        this.minimum = minimum;
        this.maximum = maximum;
    }

    /**
     * @return 任意整数
     */
    public static IntegerArgumentType integer() {
        return new IntegerArgumentType(Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    /**
     * @param min 允许的最小值
     * @return 有下限的整数
     */
    public static IntegerArgumentType integer(int min) {
        return new IntegerArgumentType(min, Integer.MAX_VALUE);
    }

    /**
     * @param min 允许的最小值
     * @param max 允许的最大值
     * @return 有范围的整数
     */
    public static IntegerArgumentType integer(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("最小值不能大于最大值：" + min + " > " + max);
        }
        return new IntegerArgumentType(min, max);
    }

    /**
     * @return 允许的最小值
     */
    public int getMinimum() {
        return minimum;
    }

    /**
     * @return 允许的最大值
     */
    public int getMaximum() {
        return maximum;
    }

    @Override
    public Integer parse(StringReader reader) throws CommandSyntaxException {
        String text = CommandParseHelper.readNumberText(reader, "整数");
        int value;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw CommandParseHelper.notANumber(reader, text, "整数");
        }
        if (value < minimum || value > maximum) {
            throw CommandParseHelper.outOfRange(reader, value, minimum, maximum);
        }
        return value;
    }

    @Override
    public String toString() {
        if (minimum == Integer.MIN_VALUE && maximum == Integer.MAX_VALUE) {
            return "integer";
        }
        if (maximum == Integer.MAX_VALUE) {
            return "integer(>=" + minimum + ")";
        }
        return "integer(" + minimum + ".." + maximum + ")";
    }
}
