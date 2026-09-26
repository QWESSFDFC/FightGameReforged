package cn.gfhnv.game.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 列表标签：对应本项目的集合与数组（顺序保留）。
 * <p>
 * 与 MC 不同，这里<b>不要求</b>元素类型一致（MC 要求同类型）—— 本项目的列表里
 * 混着对象与数字并不稀奇，宽松一点少一半报错。
 *
 * @author AI（DeepSeek）生成
 */
public final class NbtList extends NbtTag {

    /**
     * 元素。
     */
    private final List<NbtTag> values = new ArrayList<>();

    /**
     * @param tag 追加的元素；{@code null} 忽略
     * @return 当前列表（链式）
     */
    public NbtList add(NbtTag tag) {
        if (tag != null) {
            values.add(tag);
        }
        return this;
    }

    /**
     * 在指定位置插入。
     *
     * @param index 下标（{@code 0} 到 {@link #size()}）
     * @param tag   元素
     * @throws IndexOutOfBoundsException 下标越界
     */
    public void insert(int index, NbtTag tag) {
        values.add(index, tag);
    }

    /**
     * @param index 下标
     * @return 元素
     */
    public NbtTag get(int index) {
        return values.get(index);
    }

    /**
     * @param index 下标
     * @return 被移除的元素
     */
    public NbtTag remove(int index) {
        return values.remove(index);
    }

    /**
     * @return 元素个数
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

    /**
     * @return 元素视图（可读可改，注意会直接改到这个列表）
     */
    public List<NbtTag> values() {
        return values;
    }

    @Override
    public NbtTagType type() {
        return NbtTagType.LIST;
    }

    @Override
    public String toSnbt() {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(values.get(i).toSnbt());
        }
        return builder.append(']').toString();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof NbtList tag && tag.values.equals(values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(NbtTagType.LIST, values);
    }
}
