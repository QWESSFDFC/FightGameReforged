package cn.gfhnv.game.system.command;

/**
 * 「长整数」参数类型。
 * <p>
 * 对应 MC 的 {@code long(min, max)}。本项目的生命值、攻击力等都用
 * {@code long} 表示，因此处理生物数值时建议用这个而不是 {@link IntegerArgumentType}。
 * <p>
 * 工厂方法名为 {@code longArg()} 而不是 {@code long()}，因为 {@code long} 是 Java 关键字，
 * 不能作为方法名。
 *
 * @author AI（DeepSeek）生成
 */
public class LongArgumentType implements ArgumentType<Long> {

    /**
     * 允许的最小值。
     */
    private final long minimum;

    /**
     * 允许的最大值。
     */
    private final long maximum;

    /**
     * 构造一个长整数参数类型。
     *
     * @param minimum 允许的最小值
     * @param maximum 允许的最大值
     */
    public LongArgumentType(long minimum, long maximum) {
        this.minimum = minimum;
        this.maximum = maximum;
    }

    /**
     * @return 任意长整数
     */
    public static LongArgumentType longArg() {
        return new LongArgumentType(Long.MIN_VALUE, Long.MAX_VALUE);
    }

    /**
     * @param min 允许的最小值
     * @return 有下限的长整数
     */
    public static LongArgumentType longArg(long min) {
        return new LongArgumentType(min, Long.MAX_VALUE);
    }

    /**
     * @param min 允许的最小值
     * @param max 允许的最大值
     * @return 有范围的长整数
     */
    public static LongArgumentType longArg(long min, long max) {
        if (min > max) {
            throw new IllegalArgumentException("最小值不能大于最大值：" + min + " > " + max);
        }
        return new LongArgumentType(min, max);
    }

    /**
     * @return 允许的最小值
     */
    public long getMinimum() {
        return minimum;
    }

    /**
     * @return 允许的最大值
     */
    public long getMaximum() {
        return maximum;
    }

    @Override
    public Long parse(StringReader reader) throws CommandSyntaxException {
        String text = CommandParseHelper.readNumberText(reader, "长整数");
        long value;
        try {
            value = Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw CommandParseHelper.notANumber(reader, text, "长整数");
        }
        if (value < minimum || value > maximum) {
            throw CommandParseHelper.outOfRange(reader, value, minimum, maximum);
        }
        return value;
    }

    @Override
    public String toString() {
        if (minimum == Long.MIN_VALUE && maximum == Long.MAX_VALUE) {
            return "long";
        }
        if (maximum == Long.MAX_VALUE) {
            return "long(>=" + minimum + ")";
        }
        return "long(" + minimum + ".." + maximum + ")";
    }
}
