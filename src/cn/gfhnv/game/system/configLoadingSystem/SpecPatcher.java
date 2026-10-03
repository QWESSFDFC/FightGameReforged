package cn.gfhnv.game.system.configLoadingSystem;

import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 通用补丁器：照着 {@link KeySpec} 表把一个 JSON 补丁打进对象。
 * <p>
 * <b>它替掉的是一堆手写的</b> {@code applyXxx}（实体那边 27 处、技能那边 8 处）——
 * 那些手写调用与生成器里的 {@code put(...)} 是一对平行表，靠自测"事后对齐"，
 * 所以才会出现"声明了能配置、其实没人读"的键（见
 * {@code project_analyses/CONFIG-LOADING-DECOUPLING-2026-10.md} 的 D1）。
 * 现在"读得回来的"与"写得出去的"都只可能是 {@link KeySpec} 里那几行。
 * <p>
 * <b>三条纪律</b>（与实体/技能补丁器原来的行为逐字一致）：
 * <ol>
 *     <li><b>顺序 = 表里的顺序</b>：调用方按"等级 → 面板 → 派生值（{@code hp} 最后）"把表排好；</li>
 *     <li><b>逐项容错</b>：类型不对只跳过那一项并记账，一个坏键不废整份配置；</li>
 *     <li><b>块不进标量路径</b>：{@link KeySpec#write()} 为 {@code null} 的行由专门的块分支处理
 *     （{@code manaGrow} 要拆成五个扁平键、{@code inventorySlots} 减格子要判空格）。</li>
 * </ol>
 *
 * @author AI（DeepSeek）生成
 */
public final class SpecPatcher {

    /**
     * 工具类，不允许实例化。
     */
    private SpecPatcher() {
    }

    /**
     * 按表打补丁：逐行走"取值 → 类型收敛 → 写 → 记账"。
     *
     * @param target 目标对象
     * @param patch  补丁
     * @param specs  键表（顺序 = 应用顺序）
     * @param sink   记账口
     * @param id     目标 id（报错用）
     * @param <T>    目标类型
     * @return 真的被应用了的键名（调用方用它判断"要不要补一次派生值重算"）
     */
    public static <T> Set<String> patch(T target, JSONObject patch, List<KeySpec<T>> specs,
                                        Sink sink, String id) {
        Set<String> applied = new LinkedHashSet<>();
        for (KeySpec<T> spec : specs) {
            if (spec.write() == null) {
                // 块：走它自己的分支（调用方负责），不在这里当标量处理
                continue;
            }
            KeySource source = lookup(patch, spec.name());
            if (source == null) {
                continue;
            }
            Object raw = source.container().opt(source.key());
            KeySpec.Coerced coerced = spec.kind().coerce(raw);
            if (!coerced.isOk()) {
                sink.skipped(id, source.prefix() + source.key(), coerced.problem());
                continue;
            }
            spec.write().accept(target, coerced.value());
            sink.applied(id, source.prefix() + source.key(), String.valueOf(coerced.value()));
            applied.add(spec.name());
        }
        return applied;
    }

    /**
     * 按 {@link DataKeys#BARE_SECTIONS} 里的分组找这个键的取值来源。
     * <p>
     * 子块（{@code base} / {@code derived}）与"裸键"两种写法都认：{@code {"base":{…}}} 与
     * {@code {"speed":200}} 等价，同时出现时<b>子块优先</b>（生成器写的就是子块形式）。
     *
     * @param patch 实体补丁
     * @param key   规范数据名（或别名 {@code element}）
     * @return 键所在的容器 + 容器里的键；没写或认不出来返回 {@code null}
     */
    public static KeySource lookup(JSONObject patch, String key) {
        if (patch.has(key)) {
            return new KeySource(patch, key, "");
        }
        if (DataKeys.Alias.ELEMENT.equals(key) && patch.has(DataKeys.Base.ELEMENT_SORT)) {
            return new KeySource(patch, DataKeys.Base.ELEMENT_SORT, "");
        }
        JSONObject base = patch.optJSONObject(DataKeys.BASE);
        if (base != null && base.has(key)) {
            return new KeySource(base, key, DataKeys.BASE + DataKeys.SECTION_SEPARATOR);
        }
        JSONObject derived = patch.optJSONObject(DataKeys.DERIVED);
        if (derived != null && derived.has(key)) {
            return new KeySource(derived, key, DataKeys.DERIVED + DataKeys.SECTION_SEPARATOR);
        }
        return null;
    }

    /**
     * 找 {@code manaGrow} 块（{@code {"manaGrow":{"fire":20}}} 这种写法）。
     * <p>
     * <b>不能走 {@link #lookup}</b>：那个方法第一步是 {@code patch.has(key)}，而块名与键名
     * 恰好都是 {@code manaGrow} —— 于是它会把这个块<b>本身</b>当成"值"返回，
     * 调用方拿到的容器是整份补丁、里面唯一的键是块名，最后报一句"认不出这个元素（可用：…）"。
     * 这正是 2026-10-03 之前 {@code manaGrow} 块写法<b>从来没生效过</b>的原因（块名撞了键名）。
     *
     * @param patch 实体补丁
     * @return 块所在的容器 + 块里的键；没写返回 {@code null}
     */
    public static KeySource lookupManaGrowBlock(JSONObject patch) {
        JSONObject block = patch.optJSONObject(DataKeys.MANA_GROW_BLOCK);
        if (block != null) {
            return new KeySource(block, DataKeys.MANA_GROW_BLOCK, "");
        }
        JSONObject base = patch.optJSONObject(DataKeys.BASE);
        if (base != null && base.optJSONObject(DataKeys.MANA_GROW_BLOCK) != null) {
            return new KeySource(base.optJSONObject(DataKeys.MANA_GROW_BLOCK),
                    DataKeys.MANA_GROW_BLOCK, DataKeys.BASE + DataKeys.SECTION_SEPARATOR);
        }
        JSONObject derived = patch.optJSONObject(DataKeys.DERIVED);
        if (derived != null && derived.optJSONObject(DataKeys.MANA_GROW_BLOCK) != null) {
            return new KeySource(derived.optJSONObject(DataKeys.MANA_GROW_BLOCK),
                    DataKeys.MANA_GROW_BLOCK, DataKeys.DERIVED + DataKeys.SECTION_SEPARATOR);
        }
        return null;
    }

    /**
     * @param specs 键表
     * @param <T>   目标类型
     * @return 表里的全部键名
     */
    public static <T> Set<String> namesOf(List<KeySpec<T>> specs) {
        Set<String> names = new LinkedHashSet<>();
        for (KeySpec<T> spec : specs) {
            names.add(spec.name());
        }
        return names;
    }

    /**
     * 记账口：{@link EntityDataPatcher.Report} 与 {@link SkillDataPatcher.Report} 都实现它。
     * <p>
     * 抽出来的意义是「同一个通用补丁器能被两个补丁器共用」，而不是各自复制一份循环。
     *
     * @author AI（DeepSeek）生成
     */
    public interface Sink {

        /**
         * @param id    目标 id（报错用）
         * @param path  键路径（{@code base.speed} 这种）
         * @param value 写进去的值（文本形式）
         */
        void applied(String id, String path, String value);

        /**
         * @param id     目标 id
         * @param path   键路径
         * @param reason 为什么跳过
         */
        void skipped(String id, String path, String reason);
    }

    /**
     * "这个键写在哪一层"的答案：容器对象 + 容器里的键 + 展示用前缀。
     *
     * @param container 容器
     * @param key       容器里的键
     * @param prefix    展示用前缀（{@code base.} / {@code derived.} / 空串）
     * @author AI（DeepSeek）生成
     */
    public record KeySource(JSONObject container, String key, String prefix) {
    }
}
