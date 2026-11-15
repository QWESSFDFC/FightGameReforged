package cn.gfhnv.game.system.configLoadingSystem;

import java.util.*;

/**
 * <b>规则键的唯一定义处</b>：{@code GameRules.json} 里能写的每一个键都在这张表里占一行
 * （键名 / 段名 / 出厂值 / 说明）。
 * <p>
 * <b>它替掉了四份手写清单</b>（评估文档 §1.2 那张"同一个键空间被写了 5 遍"的图）：
 * {@code DataKeys.Rule.*}（键名常量，保留 —— 它就是对外契约）、
 * {@code RuleDefaults.build()}（29 行 {@code map.put}，<b>删了</b>）、
 * {@code GameRules.knownKeys()}（29 行 {@code keys.add}，<b>改成查表</b>）、
 * {@code GameRulesPatcher.SECTIONS}（5 个段名字面量，<b>改成从表里 distinct</b>）。
 * <p>
 * <b>出厂值仍然只有一份</b>：表里那一列引用的是 {@link RuleDefaults} 的具名常量 ——
 * 那些常量还被游戏代码与 {@code {@value}} Javadoc 引用着（{@code ActorLiXiaoYan} 的静态常量、
 * {@code FlameReaver} / {@code LivingThing} 的公式说明），所以它们留在原地，
 * 但"哪个键属于哪个段、出厂值是多少、是不是整数"这三件事从此只在这张表里说。
 * <p>
 * <b>规则键与实体/技能键不同</b>：规则的默认值不在任何对象上，而在代码公式里；它的实现路径是一张
 * 静态表（{@link GameRules}），不是对象补丁。所以这里用一张自己的 {@link RuleKey} 小表，
 * 而不是硬套 {@link KeySpec}（后者带着"读对象 / 写对象"两列，规则根本没有对象可读）。
 *
 * @author AI（DeepSeek）生成
 */
public final class RuleKeySpecs {

    /**
     * 这一版认识的全部规则键，<b>顺序 = {@code GameRules.json} 里的书写顺序</b>。
     */
    public static final List<RuleKey> ALL = List.of(
            /* ---------------- 伤害与面板公式的骨架 ---------------- */
            row(DataKeys.Rule.Formula.HP_BASE, "formula", RuleDefaults.FORMULA_HP_BASE,
                    "面板：hp = (等级-1) × 生命成长 + 本值"),
            row(DataKeys.Rule.Formula.DEFENCE_BASE, "formula", RuleDefaults.FORMULA_DEFENCE_BASE,
                    "面板：防御 = (等级-1) × 防御成长 + 本值"),
            row(DataKeys.Rule.Formula.ATTACK_BASE, "formula", RuleDefaults.FORMULA_ATTACK_BASE,
                    "面板：攻击 = 本值 + 攻击成长 × (等级-1)"),
            row(DataKeys.Rule.Formula.LEVEL_DEFENCE_FACTOR, "formula",
                    RuleDefaults.FORMULA_LEVEL_DEFENCE_FACTOR,
                    "伤害公式里 (等级×本值 + base) 的 factor"),
            row(DataKeys.Rule.Formula.LEVEL_DEFENCE_BASE, "formula",
                    RuleDefaults.FORMULA_LEVEL_DEFENCE_BASE,
                    "伤害公式里上面那个式子的 base"),
            row(DataKeys.Rule.Formula.TURN_TIME_BASE, "formula", RuleDefaults.TURN_TIME_BASE,
                    "时间轴：需要时间 = 本值 / 速度（速度下限 1；出厂 10000）"),
            /* ---------------- 初始法力骨架 ---------------- */
            row(DataKeys.Rule.Mana.MAIN_BASE, "mana", RuleDefaults.MANA_MAIN_BASE,
                    "主元素上限 = 成长 × (等级-1) + 本值"),
            row(DataKeys.Rule.Mana.OTHER_BASE, "mana", RuleDefaults.MANA_OTHER_BASE,
                    "其余元素上限 = 成长 × (等级-1) + 本值"));

    /**
     * 键名 → 那一行。
     */
    private static final Map<String, RuleKey> BY_KEY = buildByKey();

    /**
     * 工具类，不允许实例化。
     */
    private RuleKeySpecs() {
    }

    /**
     * @return 这一版认识的全部规则键（顺序 = 声明顺序）
     */
    public static Set<String> names() {
        return BY_KEY.keySet();
    }

    /**
     * @return 全部段名（按首次出现的顺序去重；{@code GameRulesPatcher} 就是拿它当段清单的）
     */
    public static List<String> sections() {
        Set<String> sections = new LinkedHashSet<>();
        for (RuleKey key : ALL) {
            sections.add(key.section());
        }
        return Collections.unmodifiableList(new ArrayList<>(sections));
    }

    /**
     * @return 规则键 → 出厂值
     */
    public static Map<String, Object> defaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        for (RuleKey key : ALL) {
            defaults.put(key.key(), key.defaultValue());
        }
        return Collections.unmodifiableMap(defaults);
    }

    /**
     * @param key 规则键
     * @return 那一行；不认识返回 {@code null}
     */
    public static RuleKey of(String key) {
        return BY_KEY.get(key);
    }

    /**
     * @param key 规则键
     * @return 这个键认不认识
     */
    public static boolean isKnown(String key) {
        return BY_KEY.containsKey(key);
    }

    /**
     * @param key 规则键
     * @return 出厂值；键不认识时返回 0
     */
    public static double number(String key) {
        RuleKey row = BY_KEY.get(key);
        return row == null ? 0.0 : row.number();
    }

    /**
     * @param key 规则键
     * @return 这个键该写成整数还是小数（{@code 1} 还是 {@code 0.34}）
     */
    public static boolean isInteger(String key) {
        RuleKey row = BY_KEY.get(key);
        return row != null && row.isInteger();
    }

    /**
     * @return 键名 → 那一行
     */
    private static Map<String, RuleKey> buildByKey() {
        Map<String, RuleKey> byKey = new LinkedHashMap<>();
        for (RuleKey key : ALL) {
            byKey.put(key.key(), key);
        }
        return Collections.unmodifiableMap(byKey);
    }

    /**
     * @return 一行声明
     */
    private static RuleKey row(String key, String section, Object defaultValue, String note) {
        return new RuleKey(key, section, defaultValue, note);
    }

    /**
     * 一条规则键的声明。
     *
     * @param key          规则键（{@code "段.键"}，见 {@link DataKeys.Rule}）—— <b>对外契约，一个字都不能改</b>
     * @param section      段名（{@code GameRules.json} 里的那一层）
     * @param defaultValue 出厂值（缺键时取它；也是默认文件里写出去的值）
     * @param note         一句话说明
     * @author AI（DeepSeek）生成
     */
    public record RuleKey(String key, String section, Object defaultValue, String note) {

        /**
         * @return 出厂值是不是整数型（默认文件里写成 {@code 1} 而不是 {@code 1.0}）
         */
        public boolean isInteger() {
            return defaultValue instanceof Long || defaultValue instanceof Integer
                    || defaultValue instanceof Short || defaultValue instanceof Byte;
        }

        /**
         * @return 出厂值的数值形式
         */
        public double number() {
            return defaultValue instanceof Number value ? value.doubleValue() : 0.0;
        }
    }
}
