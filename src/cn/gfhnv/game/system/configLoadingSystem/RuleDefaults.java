package cn.gfhnv.game.system.configLoadingSystem;

/**
 * 游戏规则的<b>出厂值常量</b>：每一条规则键在代码里的字面量，只此一份。
 * <p>
 * <b>它为什么还在</b>：这些常量被游戏代码与 {@code {@value}} Javadoc 直接引用
 * （{@code ActorLiXiaoYan} 的静态常量、{@code FlameReaver} / {@code LivingThing} /
 * {@code DamageCalculate} 的公式说明），而且 {@code {@value}} 要求它必须是编译期常量表达式 ——
 * 所以值留在具名常量里，"哪个键属于哪个段、出厂值是多少、是不是整数"这三件事搬去了
 * {@link RuleKeySpecs}（那张表引用的就是这里的常量）。
 * <p>
 * <b>2026-10-03 删掉了什么</b>：原来这里还有一份 {@code build()}（29 行 {@code map.put}）与
 * {@code all()} / {@code of()} / {@code number()} 三个访问器 —— 那是"同一个键空间被写第 3 遍"。
 * 现在 {@link GameRules} 直接查 {@link RuleKeySpecs}。
 * <p>
 * <b>改这里的值等于改游戏手感</b>：{@link DataKeys.Rule.Formula} 与 {@link DataKeys.Rule.Mana}
 * 两块尤其危险 —— 它们会让 {@code PROJECT-ANALYSIS-2026-09.md} §4.2 那套
 * "每 1.0 倍率 ≈ 880 伤害"的换算常数作废（改它 = 重新标定）。
 *
 * @author AI（DeepSeek）生成
 */
public final class RuleDefaults {

    /* ---------------- 伤害与面板公式的骨架 ---------------- */

    /**
     * 面板：{@code hp = (等级-1) × 生命成长 + 本值}。
     */
    public static final long FORMULA_HP_BASE = 200L;
    /**
     * 面板：{@code 防御 = (等级-1) × 防御成长 + 本值}。
     */
    public static final long FORMULA_DEFENCE_BASE = 200L;
    /**
     * 面板：{@code 攻击 = 本值 + 攻击成长 × (等级-1)}。
     */
    public static final long FORMULA_ATTACK_BASE = 110L;
    /**
     * 伤害：{@code ×(等级×本值 + base) / (等级×本值 + base + 目标防御)} 里的 factor。
     */
    public static final long FORMULA_LEVEL_DEFENCE_FACTOR = 10L;
    /**
     * 伤害：上面那个式子的 base。
     */
    public static final long FORMULA_LEVEL_DEFENCE_BASE = 200L;

    /* ---------------- 初始法力骨架 ---------------- */

    /**
     * 主元素上限 = {@code 成长 × (等级-1) + 本值}。
     */
    public static final long MANA_MAIN_BASE = 200L;
    /**
     * 其余元素上限 = {@code 成长 × (等级-1) + 本值}。
     */
    public static final long MANA_OTHER_BASE = 20L;

    /* ---------------- 盗火行者与容器 ---------------- */

    /**
     * 每次召唤消耗自身最大生命的比例。
     */
    public static final double FLAME_REAVER_SUMMON_HP_COST_RATE = 0.03;
    /**
     * 每层【灾难之力】提供的加伤比例。
     */
    public static final double FLAME_REAVER_DISASTER_POWER_ATTACK_BONUS = 0.08;
    /**
     * 【永别的决绝】初始减伤层数。
     */
    public static final int FLAME_REAVER_DAMAGE_REDUCTION_LAYERS = 2;
    /**
     * 【永别的决绝】每层减伤比例。
     */
    public static final double FLAME_REAVER_DAMAGE_REDUCTION_PER_LAYER = 0.25;
    /**
     * 场上容器数量上限（{@code 0} = 不限）。
     */
    public static final int FLAME_REAVER_CONTAINER_LIMIT = 0;
    /**
     * 每次召唤出现【完整容器】的概率。
     */
    public static final double FLAME_REAVER_COMPLETE_CONTAINER_CHANCE = 0.34;
    /**
     * 二阶段的【高额免伤】。
     */
    public static final double FLAME_REAVER_PHASE_TWO_DAMAGE_REDUCTION = 0.7;
    /**
     * 盗火行者的基础生命上限。
     */
    public static final long FLAME_REAVER_BASE_HP_MAX = 80000L;
    /**
     * 容器生命占盗火行者最大生命的比例。
     */
    public static final double FLAME_REAVER_CONTAINER_HP_RATIO = 0.15;
    /**
     * 容器攻击占盗火行者攻击的比例。
     */
    public static final double FLAME_REAVER_CONTAINER_ATTACK_RATIO = 0.4;
    /**
     * 【完整容器】生命占盗火行者最大生命的比例。
     */
    public static final double FLAME_REAVER_COMPLETE_CONTAINER_HP_RATIO = 0.25;

    /* ---------------- 虫皇 ---------------- */

    /**
     * 虫皇的基础生命上限。
     */
    public static final long INSECT_BOSS_BASE_HP = 80000L;

    /* ---------------- 李晓焰 ---------------- */

    /**
     * 【燃点】的常规上限。
     */
    public static final int LI_XIAO_YAN_IGNITION_MAX = 10;
    /**
     * 生命值偏低时的【燃点】上限。
     */
    public static final int LI_XIAO_YAN_LOW_HP_IGNITION_MAX = 15;
    /**
     * 判定"生命值偏低"的比例。
     */
    public static final double LI_XIAO_YAN_LOW_HP_THRESHOLD = 0.5;
    /**
     * 开始吃"高燃点加成"的层数。
     */
    public static final int LI_XIAO_YAN_HIGH_IGNITION = 8;
    /**
     * 高燃点时每个技能额外追加的伤害（按最大生命的比例）。
     */
    public static final double LI_XIAO_YAN_HIGH_IGNITION_BONUS_RATE = 0.6;
    /**
     * 每层【燃点】提供的单体倍率加成。
     */
    public static final double LI_XIAO_YAN_AREA_PER_IGNITION = 0.04;
    /**
     * 触发免死的层数。
     */
    public static final int LI_XIAO_YAN_MEMORIZE_TRIGGER = 10;
    /**
     * 触发免死时消耗的层数。
     */
    public static final int LI_XIAO_YAN_MEMORIZE_COST = 10;
    /**
     * 触发免死时回复的最大生命比例。
     */
    public static final double LI_XIAO_YAN_MEMORIZE_HEAL_RATE = 0.3;
    /**
     * 战斗结束时回到的层数。
     */
    public static final int LI_XIAO_YAN_RESET_IGNITION = 3;

    /**
     * 工具类，不允许实例化。
     */
    private RuleDefaults() {
    }
}
