package cn.gfhnv.game.data;

import java.util.Objects;

/**
 * 双精度标签：对应本项目的 {@code double} 字段（各种百分比/倍率）。
 * 文本形式带 {@code d} 后缀（{@code 0.15d}，与 MC 一致）。
 *
 * @author AI（DeepSeek）生成
 */
public final class NbtDouble extends NbtTag {

    /**
     * 值。
     */
    private final double value;

    /**
     * @param value 双精度值
     */
    public NbtDouble(double value) {
        this.value = value;
    }

    /**
     * @return 双精度值
     */
    public double doubleValue() {
        return value;
    }

    @Override
    public NbtTagType type() {
        return NbtTagType.DOUBLE;
    }

    @Override
    public long asLong() {
        return (long) value;
    }

    @Override
    public double asDouble() {
        return value;
    }

    @Override
    public String toSnbt() {
        return value + "d";
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof NbtDouble tag && Double.compare(tag.value, value) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(NbtTagType.DOUBLE, value);
    }
}
