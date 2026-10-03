package cn.gfhnv.game.system.configLoadingSystem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用生成器：照着同一张 {@link KeySpec} 表把对象 dump 成"键 → 值"。
 * <p>
 * 它替掉的是 {@code ConfigDefaultWriter} 里那两个手写的 {@code put(...)} 循环
 * （{@code collectBaseValues} 27 处 + {@code collectDerivedValues} 4 处）。
 * 关键在于它<b>与 {@link SpecPatcher} 共用同一张表</b>：
 * "写出去的"与"读得回来的"必然一致，不再需要靠自测事后对齐。
 * <p>
 * 序列化（缩进、转义、数字后缀）不在这里 —— 这里只产出"键 → Java 值"，
 * 排版交给 {@code ConfigDefaultWriter} 的 JSON 输出器，两边职责不混。
 *
 * @author AI（DeepSeek）生成
 */
public final class SpecWriter {

    /**
     * 工具类，不允许实例化。
     */
    private SpecWriter() {
    }

    /**
     * 按表采集一个对象的键值。
     * <p>
     * 跳过两类行：① {@link KeySpec#read()} 为 {@code null}（那是只能写、不能生成的块，
     * 例如 {@code manaGrow} 汇总块 —— 生成器写的是五个扁平键）；
     * ② {@code read} 返回 {@code null}（契约：{@code null} = 这个键没有默认值）。
     *
     * @param target 对象
     * @param specs  键表（顺序 = 写出去的顺序）
     * @param <T>    对象类型
     * @return 键 → Java 值（{@code String} / {@code Long} / {@code Double} / {@code ElementSort} …）
     */
    public static <T> Map<String, Object> collect(T target, List<KeySpec<T>> specs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (KeySpec<T> spec : specs) {
            if (spec.read() == null) {
                continue;
            }
            Object value = spec.read().apply(target);
            if (value == null) {
                continue;
            }
            values.put(spec.name(), value);
        }
        return values;
    }
}
