package cn.gfhnv.game.data;

import java.util.Objects;

/**
 * 字节标签：本项目的 {@code boolean} 与 {@code byte} 都用它。
 * <p>
 * 文本形式是 {@code 1b} / {@code 0b}（与 MC 一致）；解析时也接受 {@code true} / {@code false}。
 *
 * @author AI（DeepSeek）生成
 */
public final class NbtByte extends NbtTag {

    /**
     * 值。
     */
    private final byte value;

    /**
     * @param value 字节值
     */
    public NbtByte(byte value) {
        this.value = value;
    }

    /**
     * @param value 布尔值（真存 {@code 1}，假存 {@code 0}）
     */
    public NbtByte(boolean value) {
        this.value = (byte) (value ? 1 : 0);
    }

    /**
     * @return 字节值
     */
    public byte byteValue() {
        return value;
    }

    @Override
    public NbtTagType type() {
        return NbtTagType.BYTE;
    }

    @Override
    public long asLong() {
        return value;
    }

    @Override
    public boolean asBoolean() {
        return value != 0;
    }

    @Override
    public String toSnbt() {
        return value + "b";
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof NbtByte tag && tag.value == value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(NbtTagType.BYTE, value);
    }
}
