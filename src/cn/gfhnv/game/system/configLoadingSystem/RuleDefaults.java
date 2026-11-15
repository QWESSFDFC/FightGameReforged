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
    /**
     * 时间轴：{@code 需要时间 = 本值 / 速度}（速度下限 1）。10000 = 原先硬编码的那个数。
     */
    public static final long TURN_TIME_BASE = 10000L;

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


    /* ---------------- 虫皇 ---------------- */


    /* ---------------- 李晓焰 ---------------- */


    /**
     * 工具类，不允许实例化。
     */
    private RuleDefaults() {
    }
}
