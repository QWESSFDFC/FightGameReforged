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
            /* ---------------- 初始法力骨架 ---------------- */
            row(DataKeys.Rule.Mana.MAIN_BASE, "mana", RuleDefaults.MANA_MAIN_BASE,
                    "主元素上限 = 成长 × (等级-1) + 本值"),
            row(DataKeys.Rule.Mana.OTHER_BASE, "mana", RuleDefaults.MANA_OTHER_BASE,
                    "其余元素上限 = 成长 × (等级-1) + 本值"),
            /* ---------------- 盗火行者与容器 ---------------- */
            row(DataKeys.Rule.FlameReaver.SUMMON_HP_COST_RATE, "flameReaver",
                    RuleDefaults.FLAME_REAVER_SUMMON_HP_COST_RATE,
                    "每次召唤消耗自身最大生命的比例"),
            row(DataKeys.Rule.FlameReaver.DISASTER_POWER_ATTACK_BONUS, "flameReaver",
                    RuleDefaults.FLAME_REAVER_DISASTER_POWER_ATTACK_BONUS,
                    "每层【灾难之力】提供的加伤比例"),
            row(DataKeys.Rule.FlameReaver.DAMAGE_REDUCTION_LAYERS, "flameReaver",
                    RuleDefaults.FLAME_REAVER_DAMAGE_REDUCTION_LAYERS,
                    "【永别的决绝】初始减伤层数"),
            row(DataKeys.Rule.FlameReaver.DAMAGE_REDUCTION_PER_LAYER, "flameReaver",
                    RuleDefaults.FLAME_REAVER_DAMAGE_REDUCTION_PER_LAYER,
                    "【永别的决绝】每层减伤比例"),
            row(DataKeys.Rule.FlameReaver.CONTAINER_LIMIT, "flameReaver",
                    RuleDefaults.FLAME_REAVER_CONTAINER_LIMIT,
                    "场上容器数量上限（0 = 不限）"),
            row(DataKeys.Rule.FlameReaver.COMPLETE_CONTAINER_CHANCE, "flameReaver",
                    RuleDefaults.FLAME_REAVER_COMPLETE_CONTAINER_CHANCE,
                    "每次召唤出现【完整容器】的概率"),
            row(DataKeys.Rule.FlameReaver.PHASE_TWO_DAMAGE_REDUCTION, "flameReaver",
                    RuleDefaults.FLAME_REAVER_PHASE_TWO_DAMAGE_REDUCTION,
                    "二阶段的【高额免伤】"),
            row(DataKeys.Rule.FlameReaver.BASE_HP_MAX, "flameReaver",
                    RuleDefaults.FLAME_REAVER_BASE_HP_MAX,
                    "盗火行者的基础生命上限（只在构造时读一次）"),
            row(DataKeys.Rule.FlameReaver.CONTAINER_HP_RATIO, "flameReaver",
                    RuleDefaults.FLAME_REAVER_CONTAINER_HP_RATIO,
                    "容器生命占盗火行者最大生命的比例"),
            row(DataKeys.Rule.FlameReaver.CONTAINER_ATTACK_RATIO, "flameReaver",
                    RuleDefaults.FLAME_REAVER_CONTAINER_ATTACK_RATIO,
                    "容器攻击占盗火行者攻击的比例"),
            row(DataKeys.Rule.FlameReaver.COMPLETE_CONTAINER_HP_RATIO, "flameReaver",
                    RuleDefaults.FLAME_REAVER_COMPLETE_CONTAINER_HP_RATIO,
                    "【完整容器】生命占盗火行者最大生命的比例"),
            /* ---------------- 虫皇 ---------------- */
            row(DataKeys.Rule.InsectBoss.BASE_HP, "insectBoss", RuleDefaults.INSECT_BOSS_BASE_HP,
                    "虫皇的基础生命上限（只在构造时读一次）"),
            /* ---------------- 李晓焰 ---------------- */
            row(DataKeys.Rule.ActorLiXiaoYan.IGNITION_MAX, "actorLiXiaoYan",
                    RuleDefaults.LI_XIAO_YAN_IGNITION_MAX, "【燃点】的常规上限"),
            row(DataKeys.Rule.ActorLiXiaoYan.LOW_HP_IGNITION_MAX, "actorLiXiaoYan",
                    RuleDefaults.LI_XIAO_YAN_LOW_HP_IGNITION_MAX, "生命值偏低时的【燃点】上限"),
            row(DataKeys.Rule.ActorLiXiaoYan.LOW_HP_THRESHOLD, "actorLiXiaoYan",
                    RuleDefaults.LI_XIAO_YAN_LOW_HP_THRESHOLD, "判定「生命值偏低」的比例"),
            row(DataKeys.Rule.ActorLiXiaoYan.HIGH_IGNITION, "actorLiXiaoYan",
                    RuleDefaults.LI_XIAO_YAN_HIGH_IGNITION, "开始吃「高燃点加成」的层数"),
            row(DataKeys.Rule.ActorLiXiaoYan.HIGH_IGNITION_BONUS_RATE, "actorLiXiaoYan",
                    RuleDefaults.LI_XIAO_YAN_HIGH_IGNITION_BONUS_RATE,
                    "高燃点时每个技能额外追加的伤害（按最大生命的比例）"),
            row(DataKeys.Rule.ActorLiXiaoYan.AREA_PER_IGNITION, "actorLiXiaoYan",
                    RuleDefaults.LI_XIAO_YAN_AREA_PER_IGNITION, "每层【燃点】提供的单体倍率加成"),
            row(DataKeys.Rule.ActorLiXiaoYan.MEMORIZE_TRIGGER, "actorLiXiaoYan",
                    RuleDefaults.LI_XIAO_YAN_MEMORIZE_TRIGGER, "触发免死的层数"),
            row(DataKeys.Rule.ActorLiXiaoYan.MEMORIZE_COST, "actorLiXiaoYan",
                    RuleDefaults.LI_XIAO_YAN_MEMORIZE_COST, "触发免死时消耗的层数"),
            row(DataKeys.Rule.ActorLiXiaoYan.MEMORIZE_HEAL_RATE, "actorLiXiaoYan",
                    RuleDefaults.LI_XIAO_YAN_MEMORIZE_HEAL_RATE, "触发免死时回复的最大生命比例"),
            row(DataKeys.Rule.ActorLiXiaoYan.RESET_IGNITION, "actorLiXiaoYan",
                    RuleDefaults.LI_XIAO_YAN_RESET_IGNITION, "战斗结束时回到的层数"));
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
