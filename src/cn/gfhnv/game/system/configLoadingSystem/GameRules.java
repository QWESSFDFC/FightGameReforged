package cn.gfhnv.game.system.configLoadingSystem;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 游戏规则表：{@code config/gameConfig/GameRules.json} 换来的那一张静态数值表。
 * <p>
 * <b>与实体/技能补丁的区别</b>：实体与技能的默认值住在对象上（构造器给的），所以"缺省"是天然的；
 * 而规则表的默认值住在代码公式里，所以取值必须写成
 * {@code GameRules.getDouble(键, 字面量)} —— <b>字面量就是出厂值</b>，
 * 文件里没写这个键就等价于现在的硬编码。
 * <p>
 * <b>三条纪律</b>：
 * <ol>
 *     <li><b>启动时加载一次，之后冻结</b>：{@link #load} 之外的写入会被拒绝。
 *     这条是为了避开一个真实的坑 —— 很多使用点是
 *     {@code private static final double X = GameRules.getDouble(...);}，
 *     也就是"在那个类第一次被加载时读一次"。如果规则表在启动之后还能改，
 *     那些常量就会在半路变值，行为取决于"哪个类先被加载"，这种 bug 无法复现；</li>
 *     <li><b>缺省值一律是代码里的字面量</b>：不接受"隐式 0"。取值接口都要求传默认值，
 *     所以不存在"忘了给默认值 → 悄悄变成 0"的路径；</li>
 *     <li><b>不改公式骨架的默认值</b>：{@link DataKeys.Rule.Formula} 与
 *     {@link DataKeys.Rule.Mana} 那几项默认值与源码里的字面量逐字相等 ——
 *     改了它们会让 {@code PROJECT-ANALYSIS-2026-09.md} §4.2 那套"每 1.0 倍率 ≈ 880 伤害"
 *     的标定全部作废（改它 = 重新标定）。</li>
 * </ol>
 *
 * @author AI（DeepSeek）生成
 */
public final class GameRules {

    /**
     * 已经生效的规则值（键 → 值）。
     */
    private static final Map<String, Object> VALUES = new LinkedHashMap<>();
    /**
     * 这一版认识的全部规则键（{@link Values#apply} 只认这些）。
     */
    private static final Set<String> KNOWN = knownKeys();
    /**
     * 规则表是否已经加载过（加载之后只读）。
     */
    private static boolean loaded = false;

    /**
     * 工具类，不允许实例化。
     */
    private GameRules() {
    }

    /* ------------------------------------------------------------------
     * 读取（缺键时一律回落到 RuleDefaults 里的出厂值）
     * ------------------------------------------------------------------ */

    /**
     * @param key 规则键（见 {@link DataKeys.Rule}）
     * @return 生效值；没配置过就是 {@link RuleKeySpecs} 里那一行的出厂值，键不认识则返回 0
     */
    public static double getDouble(String key) {
        Object raw = VALUES.get(key);
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        return RuleKeySpecs.number(key);
    }

    /**
     * @param key 规则键
     * @return 生效值；没配置过就是出厂值
     */
    public static long getLong(String key) {
        return (long) getDouble(key);
    }

    /**
     * @param key 规则键
     * @return 生效值；没配置过就是出厂值
     */
    public static int getInt(String key) {
        return (int) Math.round(getDouble(key));
    }

    /**
     * @param key 规则键
     * @return 这个键在默认文件里该写成整数还是小数（整数型写 {@code 1}，浮点型写 {@code 0.34}）
     */
    public static boolean isIntegerKey(String key) {
        return RuleKeySpecs.isInteger(key);
    }

    /**
     * @param key 规则键
     * @return 这个键是不是被配置文件显式写过
     */
    public static boolean isSet(String key) {
        return VALUES.containsKey(key);
    }

    /**
     * @return 这一版认识的全部规则键（自测用来核对"文档点名的键都在"）
     * <p>
     * <b>它不再是 29 行 {@code keys.add}</b>：直接查 {@link RuleKeySpecs} 那张唯一的表 ——
     * 加一条规则键只要在表里加一行，{@code knownKeys} / {@code known} / 默认文件生成
     * 三处自动跟上。
     */
    public static Set<String> knownKeys() {
        return RuleKeySpecs.names();
    }

    /**
     * @return 这一版认识的全部规则键（同 {@link #knownKeys()}，实例方法形式便于阅读）
     */
    public static Set<String> allKeys() {
        return KNOWN;
    }

    /**
     * @param key 规则键
     * @return 这个键认不认识
     */
    public static boolean isKnown(String key) {
        return KNOWN.contains(key);
    }

    /**
     * @return 已经生效的规则值（键 → 值），给自测与报告用
     */
    public static Map<String, Object> current() {
        return Collections.unmodifiableMap(VALUES);
    }

    /* ------------------------------------------------------------------
     * 加载 / 冻结 / 复位（只给 ConfigLoader 与自测用）
     * ------------------------------------------------------------------ */

    /**
     * 开始加载：清空当前值并标记"已加载"。
     * <p>
     * 只在游戏启动时（{@code ConfigLoader#loadGameRules()}）或自测里调用。
     * 调用之后 {@link #put} 仍然可用，直到 {@link #freeze()}。
     */
    public static void beginLoad() {
        VALUES.clear();
        loaded = true;
    }

    /**
     * 写入一条规则值（只在加载期间可用）。
     *
     * @param key   规则键
     * @param value 值（数字）
     * @return 是否写进去了（规则表已冻结、或者这个键不认识时返回 {@code false}）
     */
    public static boolean put(String key, Object value) {
        if (!loaded || !KNOWN.contains(key) || !(value instanceof Number)) {
            return false;
        }
        VALUES.put(key, value);
        return true;
    }

    /**
     * 冻结规则表：之后的 {@link #put} 一律失败。
     * <p>
     * 冻结的时机是"加载完成"，也就是所有实体都还没造出来之前 ——
     * 于是 {@code static final X = GameRules.getXxx(...)} 那种"类加载时读一次"的用法
     * 拿到的一定是最终值。
     */
    public static void freeze() {
        loaded = false;
    }

    /**
     * @return 规则表是不是已经加载过（自测用）
     */
    public static boolean isLoaded() {
        return loaded;
    }

    /**
     * 清空规则表并解冻（<b>只给自测用</b>：用例跑完必须让规则表回到"什么都没配"的状态）。
     */
    public static void resetForTest() {
        VALUES.clear();
        loaded = false;
    }
}
