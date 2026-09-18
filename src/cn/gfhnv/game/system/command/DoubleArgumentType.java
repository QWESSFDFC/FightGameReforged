package cn.gfhnv.game.system.command;

/**
 * 「浮点数」参数类型。
 * <p>
 * 对应 MC 的 {@code double(min, max)}。本项目的「增强百分比」「抗性」等都是
 * {@code double}，因此这类参数请用本类型。
 * <p>
 * 工厂方法名为 {@code doubleArg()} 而不是 {@code double()}，因为 {@code double}
 * 是 Java 关键字，不能作为方法名。
 *
 * @author AI（DeepSeek）生成
 */
public class DoubleArgumentType implements ArgumentType<Double> {

    /**
     * 允许的最小值。
     */
    private final double minimum;

    /**
     * 允许的最大值。
     */
    private final double maximum;

    /**
     * 构造一个浮点数参数类型。
     *
     * @param minimum 允许的最小值
     * @param maximum 允许的最大值
     */
    public DoubleArgumentType(double minimum, double maximum) {
        this.minimum = minimum;
        this.maximum = maximum;
    }

    /**
     * @return 任意浮点数
     */
    public static DoubleArgumentType doubleArg() {
        return new DoubleArgumentType(-Double.MAX_VALUE, Double.MAX_VALUE);
    }

    /**
     * @param min 允许的最小值
     * @return 有下限的浮点数
     */
    public static DoubleArgumentType doubleArg(double min) {
        return new DoubleArgumentType(min, Double.MAX_VALUE);
    }

    /**
     * @param min 允许的最小值
     * @param max 允许的最大值
     * @return 有范围的浮点数
     */
    public static DoubleArgumentType doubleArg(double min, double max) {
        if (min > max) {
            throw new IllegalArgumentException("最小值不能大于最大值：" + min + " > " + max);
        }
        return new DoubleArgumentType(min, max);
    }

    /**
     * @return 允许的最小值
     */
    public double getMinimum() {
        return minimum;
    }

    /**
     * @return 允许的最大值
     */
    public double getMaximum() {
        return maximum;
    }

    @Override
    public Double parse(StringReader reader) throws CommandSyntaxException {
        String text = CommandParseHelper.readNumberText(reader, "浮点数");
        double value;
        try {
            value = Double.parseDouble(text);
        } catch (NumberFormatException e) {
            throw CommandParseHelper.notANumber(reader, text, "浮点数");
        }
        if (Double.isNaN(value) || value < minimum || value > maximum) {
            throw CommandParseHelper.outOfRange(reader, value, minimum, maximum);
        }
        return value;
    }

    @Override
    public String toString() {
        if (minimum == -Double.MAX_VALUE && maximum == Double.MAX_VALUE) {
            return "double";
        }
        if (maximum == Double.MAX_VALUE) {
            return "double(>=" + minimum + ")";
        }
        return "double(" + minimum + ".." + maximum + ")";
    }
}
