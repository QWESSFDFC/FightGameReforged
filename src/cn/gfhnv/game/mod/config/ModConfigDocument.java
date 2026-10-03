package cn.gfhnv.game.mod.config;

import cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 模组的配置文档：按<b>路径</b>读 {@code config/data/<模组id>.json} 里的值。
 * <p>
 * <b>路径写法</b>：用 {@code /} 分隔，例如 {@code entity/speed}。
 * 读不到（没有这个路径、或者类型不对）一律<b>返回你给的默认值</b>，不抛异常 ——
 * 模组作者不用为了"配置可能没写"写一堆判空。
 * <p>
 * <b>文档的根就是"你自己的分组"</b>：{@code getInt("initialStacks", 2)} 读的是
 * {@code config/data/<你的modid>.json} 里 {@code <你的modid>/initialStacks} 的值 ——
 * 写全路径（{@code getInt("<你的modid>/initialStacks", 2)}）也认，两种写法等价。
 * <p>
 * <b>模组分组里的字段名由模组自己定</b>：游戏不校验、不映射、出错也不报 ——
 * 因为那是模组自己的旋钮。游戏只保证"路径查找 + 缺失返回默认值"。
 * <h2>能读到什么、不能读到什么</h2>
 * <ul>
 *     <li>自己的分组：文档的根（上面说的两种写法都行）；</li>
 *     <li>{@code common/…}：<b>共享区</b>，所有模组都能读到（它是"大家都看得见的公共数据"）；</li>
 *     <li>别的模组的分组：<b>读不到</b>（返回默认值）—— 模组之间的配置互相隔离。</li>
 * </ul>
 *
 * @author AI（DeepSeek）生成
 */
public final class ModConfigDocument {

    /**
     * 共享区分组名（所有模组都能读）。
     */
    public static final String COMMON = "common";

    /**
     * 这个文档属于哪个模组（诊断用）。
     */
    private final String modId;
    /**
     * 这个模组自己的分组（<b>文档的根</b>：没有配置文件时就是一个空对象）。
     */
    private final JSONObject root;
    /**
     * 共享区分组（{@code common} 段；没有时是空对象）。
     */
    private final JSONObject common;

    /**
     * @param modId  模组 id
     * @param root   这个模组自己的分组（{@code null} 视为空对象）
     * @param common 共享区分组（{@code null} 视为空对象）
     */
    public ModConfigDocument(String modId, JSONObject root, JSONObject common) {
        this.modId = modId;
        this.root = root == null ? new JSONObject() : root;
        this.common = common == null ? new JSONObject() : common;
    }

    /**
     * @return 一个空文档（没有配置文件时用）
     */
    public static ModConfigDocument empty() {
        return new ModConfigDocument("", new JSONObject(), new JSONObject());
    }

    /**
     * 从文档的根开始、跳过前面几段路径取值。
     *
     * @param container 从哪个对象开始
     * @param parts     路径段
     * @param skip      前几段不再查找（已经在参数里定位过了）
     * @return 值；路径不存在或中途不是对象时返回 {@code null}
     */
    private static Object findByParts(JSONObject container, String[] parts, int skip) {
        if (container == null || skip >= parts.length) {
            return null;
        }
        Object current = container.opt(parts[skip]);
        for (int i = skip + 1; i < parts.length; i++) {
            if (!(current instanceof JSONObject object)) {
                return null;
            }
            current = object.opt(parts[i]);
        }
        return current;
    }

    /**
     * @return 这个文档属于哪个模组
     */
    public String modId() {
        return modId;
    }

    /* ------------------------------------------------------------------
     * 读值（读不到返回默认值）
     * ------------------------------------------------------------------ */

    /**
     * @return 这个模组自己的分组（<b>只读</b>；不要改它）
     */
    public JSONObject raw() {
        return root;
    }

    /**
     * @param path         路径（{@code /} 分隔）
     * @param defaultValue 读不到时的默认值
     * @return 整数值
     */
    public long getLong(String path, long defaultValue) {
        Long value = EntityDataPatcher.asLong(opt(path));
        return value == null ? defaultValue : value;
    }

    /**
     * @param path         路径
     * @param defaultValue 读不到时的默认值
     * @return 整数值
     */
    public int getInt(String path, int defaultValue) {
        Long value = EntityDataPatcher.asLong(opt(path));
        return value == null ? defaultValue : value.intValue();
    }

    /**
     * @param path         路径
     * @param defaultValue 读不到时的默认值
     * @return 浮点值（整数写法也认）
     */
    public double getDouble(String path, double defaultValue) {
        Double value = EntityDataPatcher.asDouble(opt(path));
        return value == null ? defaultValue : value;
    }

    /**
     * @param path         路径
     * @param defaultValue 读不到时的默认值
     * @return 布尔值（只认 JSON 的 {@code true}/{@code false}，字符串不算）
     */
    public boolean getBoolean(String path, boolean defaultValue) {
        Object value = opt(path);
        return value instanceof Boolean flag ? flag : defaultValue;
    }

    /**
     * @param path         路径
     * @param defaultValue 读不到时的默认值
     * @return 字符串
     */
    public String getString(String path, String defaultValue) {
        Object value = opt(path);
        return value instanceof String text ? text : defaultValue;
    }

    /**
     * 读一个字符串列表（JSON 数组）。数组里非字符串的元素会被跳过。
     *
     * @param path         路径
     * @param defaultValue 读不到时的默认值
     * @return 列表（新的、可修改的列表）
     */
    public List<String> getStringList(String path, List<String> defaultValue) {
        Object value = opt(path);
        if (!(value instanceof JSONArray array)) {
            return defaultValue == null ? new ArrayList<>() : new ArrayList<>(defaultValue);
        }
        List<String> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            Object element = array.opt(i);
            if (element instanceof String text) {
                result.add(text);
            }
        }
        return result;
    }

    /**
     * @param path 路径
     * @return 用户到底写没写这个路径
     */
    public boolean has(String path) {
        return opt(path) != null;
    }

    /* ------------------------------------------------------------------
     * 路径查找
     * ------------------------------------------------------------------ */

    /**
     * @param path 路径
     * @return 以这个路径为根的子对象（没有或不是对象时返回空文档，用起来不用判空）
     */
    public ModConfigDocument section(String path) {
        Object value = opt(path);
        return value instanceof JSONObject object
                ? new ModConfigDocument(modId, object, common) : empty();
    }

    /**
     * 按路径取值。
     * <p>
     * 查找范围写死为：<b>自己的分组</b>（文档的根）与 <b>共享区</b> {@code common/}。
     * 别的模组的分组一律读不到（模组之间互相隔离）。
     * <p>
     * 自己的分组<b>两种写法都认</b>：{@code initialStacks} 与 {@code <模组id>/initialStacks}
     * —— 后者是"照着文件里的路径念一遍"，前者是最省事的写法。
     *
     * @param path 路径
     * @return 值；读不到返回 {@code null}
     */
    private Object opt(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        String[] parts = path.split("/");
        // ① 自己的分组：第一段就是模组 id 时跳过它（根已经是那个分组了）
        int skip = 0;
        if (parts.length > 1 && parts[0] != null && parts[0].equals(modId)) {
            skip = 1;
        }
        // ② 共享区：从 common 段里找
        if (parts[skip] != null && COMMON.equals(parts[skip])) {
            return findByParts(common, parts, skip + 1);
        }
        // ③ 其余一律从"自己的分组"里找（别的模组的分组因此天然读不到）
        return findByParts(root, parts, skip);
    }

    /**
     * @return 自己的分组里出现过的顶层键（诊断用）
     */
    public List<String> topLevelKeys() {
        return Collections.unmodifiableList(new ArrayList<>(root.keySet()));
    }

    /**
     * @return 共享区里出现过的顶层键（诊断用）
     */
    public List<String> commonKeys() {
        return Collections.unmodifiableList(new ArrayList<>(common.keySet()));
    }

    @Override
    public String toString() {
        return "ModConfigDocument(" + modId + ", 自己的分组 " + root.keySet()
                + ", 共享区 " + common.keySet() + ")";
    }
}
