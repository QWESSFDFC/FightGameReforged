package cn.gfhnv.game.data;

/**
 * NBT 标签基类。
 * <p>
 * 标签树是"数据的表示"，<b>不是</b>游戏对象的真相 —— 本项目的真相在 Java 对象里
 * （{@code LivingThing} 之类），标签只是它的一个视图/补丁，见
 * {@link DataBridge} 与 {@code project_analyses/NBT-AND-DATA-COMMAND-2026-09.md}。
 * <p>
 * 文本形式（SNBT）由 {@link #toSnbt()} 给出，解析用 {@link Snbt#parse(String)}。
 * 数字后缀沿用 MC 的习惯：{@code 1b}（字节）、{@code 20L}（长整）、{@code 1.5d}（双精度）。
 *
 * @author AI（DeepSeek）生成
 */
public abstract class NbtTag {

    /**
     * @return 这个标签的类型
     */
    public abstract NbtTagType type();

    /**
     * @return MC 风格的文本形式（SNBT）
     */
    public abstract String toSnbt();

    /**
     * 取长整值。只有数字类标签能取。
     *
     * @return 长整值
     * @throws IllegalArgumentException 这个标签不是数字
     */
    public long asLong() {
        throw new IllegalArgumentException(type() + " 不是数字，取不出整数值：" + toSnbt());
    }

    /**
     * 取双精度值。
     *
     * @return 双精度值
     * @throws IllegalArgumentException 这个标签不是数字
     */
    public double asDouble() {
        return asLong();
    }

    /**
     * 取布尔值（非 0 即真）。
     *
     * @return 布尔值
     * @throws IllegalArgumentException 这个标签不是数字
     */
    public boolean asBoolean() {
        return asLong() != 0L;
    }

    /**
     * 取字符串值。字符串标签返回原文（不带引号），其它标签返回 SNBT 文本。
     *
     * @return 字符串
     */
    public String asString() {
        return toSnbt();
    }

    @Override
    public String toString() {
        return toSnbt();
    }

    /**
     * 把一个 Java 数值/字符串包成最合适的标签。
     *
     * @param value 值
     * @return 标签（{@code int} 装得下就是 {@link NbtInt}，否则 {@link NbtLong}）
     */
    public static NbtTag of(long value) {
        return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE
                ? new NbtInt((int) value) : new NbtLong(value);
    }

    /**
     * @param value 双精度值
     * @return 双精度标签
     */
    public static NbtTag of(double value) {
        return new NbtDouble(value);
    }

    /**
     * @param value 布尔值
     * @return 字节标签（{@code 1b} / {@code 0b}）
     */
    public static NbtTag of(boolean value) {
        return new NbtByte(value);
    }

    /**
     * @param value 字符串；{@code null} 当作空串
     * @return 字符串标签
     */
    public static NbtTag of(String value) {
        return new NbtString(value == null ? "" : value);
    }
}
