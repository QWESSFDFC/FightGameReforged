package cn.gfhnv.game.data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 全局数据存储 —— 对应 MC 的 {@code /data ... storage <id>}。
 * <p>
 * 用法：
 * <pre>
 * /data merge storage 我的计数 {killCount:1}
 * /data get storage 我的计数 killCount
 * /data modify storage 我的计数 killCount set 5
 * </pre>
 * <p>
 * <b>只在内存里</b>：用户 2026-09 明确说"存档没必要做"，所以退出游戏就没了 ——
 * 它的定位是"同一局里跨命令、跨实体传数据"，或者给模组当一个临时计数板使。
 * 想持久化的话，将来把 {@link #stores()} 接到一个 JSON 文件上即可（本版故意不做）。
 * <p>
 * 线程模型与命令系统一致：单线程，不需要同步。
 *
 * @author AI（DeepSeek）生成
 */
public final class DataStorage {

    /**
     * 所有存储位：id → 复合标签（{@link LinkedHashMap} 保证输出顺序稳定）。
     */
    private static final Map<String, NbtCompound> STORES = new LinkedHashMap<>();

    /**
     * 工具类，不允许实例化。
     */
    private DataStorage() {
    }

    /**
     * 取一个存储位，不存在就建一个空的（{@code merge} / {@code modify} 用这个）。
     *
     * @param id 存储位名字（自己起，例如模组 id）
     * @return 那个复合标签（<b>活的</b>，直接改它就是改存储）
     * @throws IllegalArgumentException id 为空
     */
    public static NbtCompound of(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("存储位的名字不能为空");
        }
        return STORES.computeIfAbsent(id.trim(), key -> new NbtCompound());
    }

    /**
     * 取一个存储位，不存在返回 {@code null}（{@code get} 用这个：只想看，不想建）。
     *
     * @param id 存储位名字
     * @return 那个复合标签；没有则为 {@code null}
     */
    public static NbtCompound get(String id) {
        return id == null ? null : STORES.get(id.trim());
    }

    /**
     * @param id 存储位名字
     * @return 是否存在
     */
    public static boolean has(String id) {
        return id != null && STORES.containsKey(id.trim());
    }

    /**
     * @return 现有存储位的名字（输出顺序 = 创建顺序）
     */
    public static Set<String> ids() {
        return STORES.keySet();
    }

    /**
     * @return 全部存储位（活的映射）
     */
    public static Map<String, NbtCompound> stores() {
        return STORES;
    }

    /**
     * 清空全部存储位（自测用；游戏里没有对应的命令）。
     */
    public static void clear() {
        STORES.clear();
    }
}
