package cn.gfhnv.game.system.configLoadingSystem;

import cn.gfhnv.game.system.ElementSort;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 一条可配置键的完整声明：键名 / 分组 / 类型 / 怎么读 / 怎么写 / 一句话说明。
 * <p>
 * <b>它是什么</b>：整个配置加载子系统的<b>唯一真相</b>。一张表驱动四件事 ——
 * 生成默认文件（{@link SpecWriter}）、打补丁（{@link SpecPatcher}）、
 * 报"未知键"（{@link SpecPatcher#unknownKeys}）、以及 {@link DataKeys#META} 那套对外元数据。
 * <p>
 * <b>于是"新增一个可配置键"= 在这张表里加一行</b>（外加字段自己的 getter/setter）。
 * 以前要改 4 个文件 9~10 处：{@code DataKeys} 的常量 + {@code buildGroups} + {@code buildMeta} +
 * {@code ConfigDefaultWriter.collectXxxValues} 的 {@code put} + {@code EntityDataPatcher} 的
 * {@code applyXxx} + 五行字段的 4 个 {@code switch}。
 * <p>
 * <b>两条约定</b>（表驱动能成立的前提，写在最显眼的地方）：
 * <ul>
 *     <li>{@link #write} 为 {@code null} ⇒ 这个键<b>不</b>走通用标量补丁路径，
 *     由一个专门的"块"分支处理（现在的两个：{@code manaGrow} 与 {@code inventorySlots}）；</li>
 *     <li>{@link #read} 为 {@code null}，或者 {@code read} 返回 {@code null} ⇒
 *     生成器<b>不</b>写这个键（契约：{@code null} = 这个键没有默认值）。</li>
 * </ul>
 * <b>键名一个字都不能改</b>：它同时是 {@code /data} 的数据名与配置文件里的键名，
 * 改名等于让用户的配置静默失效（{@code notes_for_llm/70-DATA.md:24-25}）。
 *
 * @param name  键名（{@code /data} 数据名，对外契约）
 * @param group 分组（{@link DataKeys#GROUP_BASE} / {@link DataKeys#GROUP_DERIVED} /
 *              {@link DataKeys#GROUP_TEMPORARY}）
 * @param kind  值的种类（决定 JSON 里怎么校验）
 * @param read  对象 → 值（生成默认文件用；返回 {@code null} = 这个键不写出去）
 * @param write 值 → 对象（打补丁用；{@code null} = 由专门的块分支处理）
 * @param note  一句话说明（会被自测打印出来，所以写清楚"是什么"）
 * @param <T>   被读写的对象类型（实体 / 技能）
 * @author AI（DeepSeek）生成
 */
public record KeySpec<T>(
        String name,
        String group,
        Kind kind,
        Function<T, Object> read,
        BiConsumer<T, Object> write,
        String note) {

    /**
     * @param raw JSON 里的值
     * @return 整数值；不是整数返回 {@code null}（{@code 1.0} 这种整数值的浮点也认）
     */
    public static Long asLong(Object raw) {
        if (raw instanceof Byte || raw instanceof Short || raw instanceof Integer || raw instanceof Long) {
            return ((Number) raw).longValue();
        }
        if (raw instanceof Double || raw instanceof Float) {
            double value = ((Number) raw).doubleValue();
            return value == Math.floor(value) && !Double.isInfinite(value) ? (long) value : null;
        }
        return null;
    }

    /**
     * @param raw JSON 里的值
     * @return 浮点值；不是数字返回 {@code null}
     */
    public static Double asDouble(Object raw) {
        return raw instanceof Number number ? number.doubleValue() : null;
    }

    /**
     * @param raw JSON 里的值
     * @return 报错用的类型描述
     */
    public static String describe(Object raw) {
        if (raw == null) {
            return "null";
        }
        if (raw instanceof String) {
            return "字符串";
        }
        if (raw instanceof Number) {
            return "数字";
        }
        if (raw instanceof Boolean) {
            return "布尔值";
        }
        if (raw instanceof JSONObject) {
            return "对象";
        }
        if (raw instanceof JSONArray) {
            return "数组";
        }
        return raw.getClass().getSimpleName();
    }

    /**
     * @param text 文本
     * @return 元素枚举；认不出来返回 {@code null}
     */
    public static ElementSort elementOf(String text) {
        for (ElementSort sort : ElementSort.values()) {
            if (sort.name().equalsIgnoreCase(text)) {
                return sort;
            }
        }
        return null;
    }

    /**
     * @return 全部元素名（报错用）
     */
    public static String elementNames() {
        StringBuilder builder = new StringBuilder();
        for (ElementSort sort : ElementSort.values()) {
            if (builder.length() > 0) {
                builder.append(" / ");
            }
            builder.append(sort.name());
        }
        return builder.toString();
    }

    /**
     * 值的种类：决定 JSON 里怎么校验、以及"这个键在 {@code /data} 里吗"。
     * <p>
     * 前四种是标量；{@link #INVENTORY} / {@link #MANA_BLOCK} / {@link #TAGS} 是
     * <b>有语义的块</b> —— 块不该硬塞进标量表，它们各自有一个专门的 apply 分支
     * （{@code inventorySlots} 减格子要判"末尾格子是不是空的"，{@code manaGrow}
     * 要一个块拆成五个扁平键，技能权重块要整块覆盖）。表里仍然登记它们，
     * 是为了让"报未知键"这一件事也只有一张表。
     */
    public enum Kind {
        /**
         * 字符串。
         */
        STRING,
        /**
         * 整数（JSON 里的 {@code 1}；{@code 1.0} 也认，只要求是整数值）。
         */
        LONG,
        /**
         * {@code int} 范围的整数（技能的目标数 / 冷却这类字段，超出 {@code int} 就报类型不对）。
         */
        INT,
        /**
         * 布尔值（只认 JSON 的 {@code true} / {@code false}，字符串 {@code "true"} 不算）。
         */
        BOOLEAN,
        /**
         * 浮点数（整数写法也认）。
         */
        DOUBLE,
        /**
         * {@link ElementSort} 的枚举名（大小写不敏感）。
         */
        ELEMENT,
        /**
         * 背包格数：{@code /data} 里没有这个键（那一侧用 {@code inventory} 里的格子元素表达）。
         */
        INVENTORY,
        /**
         * 五行法力成长汇总块：{@code /data} 里是五个扁平的 {@code *ManaGrow}。
         */
        MANA_BLOCK,
        /**
         * 技能 AI 权重块（整块覆盖）。
         */
        TAGS;

        /**
         * @return 元数据里用的 Java 类型名（与 {@code LivingThing} 上的字段类型一致）
         */
        public String javaTypeName() {
            return switch (this) {
                case STRING, TAGS -> DataKeys.TYPE_STRING;
                case LONG, INVENTORY -> DataKeys.TYPE_LONG;
                case INT -> "int";
                case BOOLEAN -> "boolean";
                case DOUBLE -> DataKeys.TYPE_DOUBLE;
                case ELEMENT -> DataKeys.TYPE_ELEMENT;
                case MANA_BLOCK -> "ElementSort→double";
            };
        }

        /**
         * @return 这个键是否出现在 {@code /data} 里（自测的"dump 契约"口径）
         */
        public boolean inData() {
            return this != INVENTORY && this != MANA_BLOCK;
        }

        /**
         * 按种类把一个 JSON 值收成 Java 值。
         *
         * @param raw JSON 里的原始值
         * @return 收好的值；收不了时带上"为什么"（报错文案与拆开写时逐字一致）
         */
        public Coerced coerce(Object raw) {
            switch (this) {
                case STRING:
                    return raw instanceof String
                            ? Coerced.ok(raw)
                            : Coerced.bad("类型不对（要字符串，实际是 " + describe(raw) + "）");
                case LONG, INVENTORY: {
                    Long value = asLong(raw);
                    return value != null
                            ? Coerced.ok(value)
                            : Coerced.bad("类型不对（要整数，实际是 " + describe(raw) + "）");
                }
                case INT: {
                    Long value = asLong(raw);
                    return value != null && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE
                            ? Coerced.ok(value.intValue())
                            : Coerced.bad("类型不对（要整数，实际是 " + describe(raw) + "）");
                }
                case BOOLEAN:
                    return raw instanceof Boolean
                            ? Coerced.ok(raw)
                            : Coerced.bad("类型不对（要 true / false，实际是 " + describe(raw) + "）");
                case DOUBLE: {
                    Double value = asDouble(raw);
                    return value != null
                            ? Coerced.ok(value)
                            : Coerced.bad("类型不对（要数字，实际是 " + describe(raw) + "）");
                }
                case ELEMENT: {
                    if (!(raw instanceof String text)) {
                        return Coerced.bad("类型不对（要字符串，实际是 " + describe(raw) + "）");
                    }
                    ElementSort sort = elementOf(text);
                    return sort != null
                            ? Coerced.ok(sort)
                            : Coerced.bad("没有这个元素（可用：" + elementNames() + "）");
                }
                default:
                    return Coerced.bad("这个键没有标量写法（块要走它自己的分支）");
            }
        }
    }

    /**
     * 一次类型收敛的结果：成功带着值，失败带着"为什么"。
     *
     * @param value   收好的值（失败时为 {@code null}）
     * @param problem 失败原因（成功时为 {@code null}）
     * @author AI（DeepSeek）生成
     */
    public record Coerced(Object value, String problem) {

        /**
         * @param value 值
         * @return 成功
         */
        public static Coerced ok(Object value) {
            return new Coerced(value, null);
        }

        /**
         * @param problem 失败原因
         * @return 失败
         */
        public static Coerced bad(String problem) {
            return new Coerced(null, problem);
        }

        /**
         * @return 收好了吗
         */
        public boolean isOk() {
            return problem == null;
        }
    }
}
