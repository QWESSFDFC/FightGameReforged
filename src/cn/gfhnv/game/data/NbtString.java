package cn.gfhnv.game.data;

import java.util.Objects;

/**
 * 字符串标签：对应 {@code String} 与枚举（枚举存 {@code name()}）。
 * <p>
 * 文本形式带双引号并转义（{@code "白厄"}、{@code "a\"b"}）。
 * {@link #asString()} 返回<b>不带引号</b>的原文，方便直接拿去比较。
 *
 * @author AI（DeepSeek）生成
 */
public final class NbtString extends NbtTag {

    /**
     * 值。
     */
    private final String value;

    /**
     * @param value 字符串；{@code null} 当作空串
     */
    public NbtString(String value) {
        this.value = value == null ? "" : value;
    }

    /**
     * @return 原始字符串（不带引号）
     */
    public String stringValue() {
        return value;
    }

    @Override
    public NbtTagType type() {
        return NbtTagType.STRING;
    }

    @Override
    public String asString() {
        return value;
    }

    /**
     * 字符串标签也允许当数字读（写回数值字段时方便），解析失败会抛出清晰的错误。
     *
     * @return 长整值
     * @throws IllegalArgumentException 这个字符串不是整数
     */
    @Override
    public long asLong() {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("字符串 \"" + value + "\" 不是整数");
        }
    }

    @Override
    public double asDouble() {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("字符串 \"" + value + "\" 不是数字");
        }
    }

    @Override
    public boolean asBoolean() {
        String trimmed = value.trim();
        if ("true".equalsIgnoreCase(trimmed)) {
            return true;
        }
        if ("false".equalsIgnoreCase(trimmed)) {
            return false;
        }
        try {
            return Long.parseLong(trimmed) != 0L;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public String toSnbt() {
        StringBuilder builder = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> builder.append(c);
            }
        }
        return builder.append('"').toString();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof NbtString tag && tag.value.equals(value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(NbtTagType.STRING, value);
    }
}
