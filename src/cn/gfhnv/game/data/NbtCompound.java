package cn.gfhnv.game.data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 复合标签：一堆「键 → 标签」，<b>保留插入顺序</b>（和 MC 一样，方便对照输出）。
 * <p>
 * 对应本项目的对象与 {@code Map}。
 *
 * @author AI（DeepSeek）生成
 */
public final class NbtCompound extends NbtTag {

    /**
     * 键值对（{@link LinkedHashMap}：输出顺序稳定，读日志时不会每次都不一样）。
     */
    private final Map<String, NbtTag> values = new LinkedHashMap<>();

    /**
     * @param key   键
     * @param value 值；{@code null} 表示删除这个键
     * @return 当前复合标签（链式）
     */
    public NbtCompound put(String key, NbtTag value) {
        if (key == null) {
            return this;
        }
        if (value == null) {
            values.remove(key);
        } else {
            values.put(key, value);
        }
        return this;
    }

    /**
     * @param key 键
     * @return 值；没有则为 {@code null}
     */
    public NbtTag get(String key) {
        return values.get(key);
    }

    /**
     * @param key 键
     * @return 是否有这个键
     */
    public boolean has(String key) {
        return values.containsKey(key);
    }

    /**
     * @param key 键
     * @return 被移除的值；没有则为 {@code null}
     */
    public NbtTag remove(String key) {
        return values.remove(key);
    }

    /**
     * @return 全部键（顺序同上）
     */
    public Set<String> keySet() {
        return values.keySet();
    }

    /**
     * @return 键值对视图（可读可改）
     */
    public Map<String, NbtTag> values() {
        return values;
    }

    /**
     * @return 键的个数
     */
    public int size() {
        return values.size();
    }

    /**
     * @return 是否为空
     */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    @Override
    public NbtTagType type() {
        return NbtTagType.COMPOUND;
    }

    @Override
    public String toSnbt() {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, NbtTag> entry : values.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append(keyText(entry.getKey())).append(':').append(entry.getValue().toSnbt());
        }
        return builder.append('}').toString();
    }

    /**
     * 键的文本形式：简单键不加引号（{@code hp}），带特殊字符的加引号。
     *
     * @param key 键
     * @return 文本
     */
    private static String keyText(String key) {
        boolean simple = !key.isEmpty();
        for (int i = 0; i < key.length() && simple; i++) {
            char c = key.charAt(i);
            simple = Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '+' || c == '.';
        }
        return simple ? key : new NbtString(key).toSnbt();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof NbtCompound tag && tag.values.equals(values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(NbtTagType.COMPOUND, values);
    }
}
