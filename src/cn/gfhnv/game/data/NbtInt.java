package cn.gfhnv.game.data;

import java.util.Objects;

/**
 * 32 位整数标签：对应本项目的 {@code int} 字段。文本形式就是光秃秃的数字（{@code 20}）。
 *
 * @author AI（DeepSeek）生成
 */
public final class NbtInt extends NbtTag {

    /**
     * 值。
     */
    private final int value;

    /**
     * @param value 整数值
     */
    public NbtInt(int value) {
        this.value = value;
    }

    /**
     * @return 整数值
     */
    public int intValue() {
        return value;
    }

    @Override
    public NbtTagType type() {
        return NbtTagType.INT;
    }

    @Override
    public long asLong() {
        return value;
    }

    @Override
    public String toSnbt() {
        return Integer.toString(value);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof NbtInt tag && tag.value == value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(NbtTagType.INT, value);
    }
}
