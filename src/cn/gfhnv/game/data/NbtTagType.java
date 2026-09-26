package cn.gfhnv.game.data;

/**
 * NBT 标签的类型（本项目只实现游戏里真正用得上的 7 种）。
 * <p>
 * 与 MC 的差别：<b>不做</b> {@code SHORT} / {@code FLOAT} / {@code BYTE_ARRAY} / {@code INT_ARRAY} ——
 * 本项目的字段只有 {@code int} / {@code long} / {@code double} / {@code boolean} / {@code String} / 枚举 / 集合，
 * 少几种类型就少一半边界情况。真要加时在这里补一项，其它类都按 {@link NbtTag#type()} 分派。
 *
 * @author AI（DeepSeek）生成
 */
public enum NbtTagType {

    /**
     * 单字节：本项目的 {@code boolean} 用它（{@code 1b} / {@code 0b}，与 MC 一致）。
     */
    BYTE,

    /**
     * 32 位整数：{@code int} 字段。
     */
    INT,

    /**
     * 64 位整数：{@code long} 字段（本项目大部分数值都是 long）。
     */
    LONG,

    /**
     * 双精度浮点：{@code double} 字段。
     */
    DOUBLE,

    /**
     * 字符串：{@code String} 与枚举（枚举存 {@code name()}）。
     */
    STRING,

    /**
     * 有序列表：集合、数组。
     */
    LIST,

    /**
     * 复合标签（键值对，保留插入顺序）：对象、{@code Map}。
     */
    COMPOUND
}
