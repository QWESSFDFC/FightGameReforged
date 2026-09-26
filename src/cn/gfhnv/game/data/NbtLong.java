package cn.gfhnv.game.data;

import java.util.Objects;

/**
 * 64 位整数标签：对应本项目的 {@code long} 字段（HP、攻击、防御这些大数值都是它）。
 * 文本形式带 {@code L} 后缀（{@code 7392L}，与 MC 一致）。
 *
 * @author AI（DeepSeek）生成
 */
public final class NbtLong extends NbtTag {

    /**
     * 值。
     */
    private final long value;

    /**
     * @param value 长整值
     */
    public NbtLong(long value) {
        this.value = value;
    }

    /**
     * @return 长整值
     */
    public long longValue() {
        return value;
    }

    @Override
    public NbtTagType type() {
        return NbtTagType.LONG;
    }

    @Override
    public long asLong() {
        return value;
    }

    @Override
    public String toSnbt() {
        return value + "L";
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof NbtLong tag && tag.value == value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(NbtTagType.LONG, value);
    }
}
