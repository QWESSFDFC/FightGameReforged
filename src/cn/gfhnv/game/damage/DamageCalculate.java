package cn.gfhnv.game.damage;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.CalculateDamageEndEvent;
import cn.gfhnv.game.event.CalculateDamageGetStatusEvent;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;

/**
 * 伤害计算工具类。把一次伤害从"倍率"算到"实际数字"分两段做。
 * <p>
 * <b>第 1 段：原始值</b>（{@link Damage#getRawDamageAmount()}）
 * <pre>
 * raw = 生命上限 × hp倍率 + 攻击 × atk倍率 + 防御 × 防御倍率 + 技能额外伤害 + 使用者额外伤害
 * </pre>
 * 它<b>不带</b>任何系数，也不取整 —— 倍率乘生命上限经常是小数，提前取整会让最终伤害漂移。
 * <p>
 * <b>第 2 段：公式结果</b>（{@link Damage#getCalculateDamageResult()}）
 * <pre>
 * raw
 *   × (1 + 增伤)                      // 真实伤害不乘
 *   × (1 − 有效抗性)                   // 真实伤害不乘；有效抗性 = 抗性 − 穿透（先抵后乘）
 *   × 承伤倍率                         // Π(1 − 每个减伤)，乘算叠加，见 LivingThing#getDamageTakenMultiplier
 *   × (等级 × 10 + 200) / (等级 × 10 + 200 + 目标有效防御)
 *   × 单体伤害倍率                     // 面板基础值 × Π(各来源乘数)，见 LivingThing#getIndividualMultipleArea
 *   × 暴击倍率
 * </pre>
 * 结果不会小于 0：负伤害会被 {@code LivingThing#getDamage} 当成治疗。
 * <p>
 * <b>真实伤害</b>（{@link ElementSort#UNIVERSAL}）：免属性抗性、免单元素增伤、免<b>全局增伤</b>，
 * 但<b>防御与减伤照算</b>（作者 2026-11-08 拍板）。
 * <p>
 * <b>两个事件</b>都在这里发：{@link CalculateDamageGetStatusEvent}（读任何数值<b>之前</b>）、
 * {@link CalculateDamageEndEvent}（系数全部读完、开始相乘<b>之前</b>）。
 * 后者的时机是刻意的：监听到它时改攻击者/目标的属性<b>不会</b>影响这一击。
 *
 * @author gfhnv；AI（DeepSeek）改造成两段式
 */
public class DamageCalculate {

    /**
     * 等级防御系数（公式里的 {@code ×10}）。
     * <p>
     * <b>只读一次</b>：这是伤害计算的热路径，不能每次 {@code GameRules.getDouble(...)}
     * 做一次 Map 查询。取值时机是"本类第一次被加载"，而规则表在游戏启动最开始就加载并冻结了
     * （见 {@code ConfigLoader#loadGameRules()}），所以这里读到的一定是最终值。
     * <p>
     * 出厂值 {@value cn.gfhnv.game.system.configLoadingSystem.RuleDefaults#FORMULA_LEVEL_DEFENCE_FACTOR}；
     * 改写它 = 让 {@code PROJECT-ANALYSIS-2026-09.md} §4.2 那套"每 1.0 倍率 ≈ 880 伤害"的标定作废。
     */
    private static final double LEVEL_DEFENCE_FACTOR = cn.gfhnv.game.system.configLoadingSystem.GameRules
            .getDouble(cn.gfhnv.game.system.configLoadingSystem.DataKeys.Rule.Formula.LEVEL_DEFENCE_FACTOR);

    /**
     * 等级防御常数（公式里的 {@code +200}），同上只读一次。
     */
    private static final double LEVEL_DEFENCE_BASE = cn.gfhnv.game.system.configLoadingSystem.GameRules
            .getDouble(cn.gfhnv.game.system.configLoadingSystem.DataKeys.Rule.Formula.LEVEL_DEFENCE_BASE);
    /**
     * 自测开关：关掉之后 {@code resolve} 永远不掷暴击。
     * <p>
     * <b>为什么需要它</b>：玩家角色有 5% 基础暴击率之后，任何"比较两次伤害"的自测断言都会
     * 随着掷骰变成**间歇性失败**（一次暴击 1.5 倍，比例立刻变）。生产环境恒为 {@code true}；
     * 只有 {@code TestCommandSystem} 在启动时把它关掉，而专门验证暴击的用例再临时打开。
     */
    private static boolean criticalEnabled = true;

    private DamageCalculate() {
    }

    /**
     * 只给自测用：开关暴击掷骰。
     *
     * @param enabled 是否允许暴击
     */
    public static void setCriticalEnabledForSelfTest(boolean enabled) {
        criticalEnabled = enabled;
    }

    /**
     * @return 当前是否允许暴击（生产恒 true）
     */
    public static boolean isCriticalEnabled() {
        return criticalEnabled;
    }

    /**
     * 只要数字的便捷入口：按"施法者元素 + 默认伤害类型"算一条伤害并返回公式结果。
     * <p>
     * 它等价于 {@code new Damage(attacker, target, skill).getCalculateDamageResult()}，
     * 走的是同一条路（自测与不关心类型/元素的调用方用）。
     *
     * @param attacker     攻击者
     * @param targetEntity 受害实体
     * @param skill        技能
     * @return 公式算出来的伤害值
     */
    public static long calculate(LivingThing attacker, LivingThing targetEntity, Skill skill) {
        return new Damage(attacker, targetEntity, skill).getCalculateDamageResult();
    }

    /**
     * 算一条伤害：先出原始值，再套整条公式。两段都在这里做完，中间不能被别处插一脚
     * （{@code Skill#extraDamage} 是技能自己临时改的，必须在同一次调用里读掉）。
     *
     * @param damage 伤害载体（提供来源、目标、元素）
     * @param skill  造成伤害的技能（提供倍率与额外伤害）；{@code null} 时倍率全按 0 算
     * @return 两段数值
     */
    static Result resolve(Damage damage, Skill skill) {
        LivingThing attacker = damage.getSourceEntity();
        LivingThing targetEntity = damage.getTargetEntity();
        EventBus.post(new CalculateDamageGetStatusEvent(attacker, targetEntity));
        // ⚠️ 这一段（到 EventBus.post(End) 之前）负责把**所有**参与计算的数值读完 ——
        // 与改造前逐行一致。监听 CalculateDamageEndEvent 的人改属性不该影响这一击。
        double rawDamageAmount = rawAmount(attacker, skill);
        double criticalRate = attacker.getCriticalRate();
        double criticalMultiplier = 1;
        if (criticalEnabled && Math.random() <= criticalRate) {
            criticalMultiplier += attacker.getCriticalDamage();
            // 只给显示用：数字已经进了 criticalMultiplier，这里记一下"这一击暴击了"
            damage.setCritical(true);
        }
        double resistance = 0;
        double penetration = attacker.getPenetration();
        // 减伤是乘算叠加的（50% + 25% → 只受 37.5%），且已夹在 [0,1]，见 LivingThing#getDamageTakenMultiplier
        double damageTakenMultiplier = targetEntity.getDamageTakenMultiplier();
        double enhance = attacker.getEnhance();
        double individualMultipleArea = attacker.getIndividualMultipleArea();
        long level = attacker.getLevel();
        double defenseLoss = targetEntity.getDefenceLoss();
        // 无视防御由「效果实现 IDefenceIgnore」提供，这里只读汇总值（不认识具体效果类）
        defenseLoss += attacker.getIgnoreDefencePercent();
        double lossAmount = attacker.getIgnoreDefenceAmount();
        double targetDefence = targetEntity.getDefence() * (1 - defenseLoss) - lossAmount;
        if (targetDefence <= 0) {
            targetDefence = 0;
        }
        if (!damage.isTrueDamage()) {
            switch (damage.getElementSort()) {
                case DIRT -> {
                    resistance = targetEntity.getDirtResistance();
                    enhance += attacker.getElementDamageEnhance(ElementSort.DIRT);
                    penetration += attacker.getElementPenetration(ElementSort.DIRT);
                }
                case FIRE -> {
                    resistance = targetEntity.getFireResistance();
                    enhance += attacker.getElementDamageEnhance(ElementSort.FIRE);
                    penetration += attacker.getElementPenetration(ElementSort.FIRE);
                }
                case WOOD -> {
                    resistance = targetEntity.getWoodResistance();
                    enhance += attacker.getElementDamageEnhance(ElementSort.WOOD);
                    penetration += attacker.getElementPenetration(ElementSort.WOOD);
                }
                case METAL -> {
                    resistance = targetEntity.getMetalResistance();
                    enhance += attacker.getElementDamageEnhance(ElementSort.METAL);
                    penetration += attacker.getElementPenetration(ElementSort.METAL);
                }
                case WATER -> {
                    resistance = targetEntity.getWaterResistance();
                    enhance += attacker.getElementDamageEnhance(ElementSort.WATER);
                    penetration += attacker.getElementPenetration(ElementSort.WATER);
                }
                default -> {
                    resistance = 0;
                }
            }
        }
        EventBus.post(new CalculateDamageEndEvent(attacker, targetEntity));
        // 抗性/穿透：**先抵后乘** —— 有效抗性 = 抗性 − 穿透，再按 (1 − 有效抗性) 乘进去。
        // ⚠️ 两个夹紧都是必须的（2026-10-04 用户确认的语义）：
        //   ① 穿透只能把抗性**抵到 0 为止**（`Math.max(0, …)`）——
        //      否则"抗性 0 + 穿透 50%"会算成有效抗性 −0.5、伤害 ×1.5，变成额外增伤，不是"抵消抗性"；
        //   ② 抗性本来就 ≤0（弱点）时**原样保留**，穿透不去碰它 ——
        //      没有抗性可抵消时穿透就该无效，而不是把弱点放大。
        // 负穿透当 0（不会反过来变成减伤）；乘数本身夹在 ≥0。
        double effectiveResistance = resistance > 0
                ? Math.max(0, resistance - Math.max(0, penetration))
                : resistance;
        // 真实伤害：免抗性、免元素增伤、免全局增伤（防御与减伤照乘，作者 2026-11-08 拍板）
        boolean trueDamage = damage.isTrueDamage();
        double resistanceMultiplier = trueDamage ? 1 : Math.max(0, 1 - effectiveResistance);
        double enhanceMultiplier = trueDamage ? 1 : 1 + enhance;
        double finalDamage = rawDamageAmount
                * enhanceMultiplier
                * resistanceMultiplier
                * damageTakenMultiplier
                * ((level * LEVEL_DEFENCE_FACTOR + LEVEL_DEFENCE_BASE)
                / (level * LEVEL_DEFENCE_FACTOR + LEVEL_DEFENCE_BASE + targetDefence))
                * individualMultipleArea
                * criticalMultiplier;
        // 伤害永不为负：负数会被 LivingThing#getDamage 当成治疗
        return new Result(rawDamageAmount, Math.max(0, (long) finalDamage));
    }

    /**
     * 原始值：倍率乘上属性之后、未算防御与减伤。
     *
     * @param attacker 攻击者
     * @param skill    技能（可为 {@code null}，那倍率全按 0）
     * @return 原始值（可能是小数）
     */
    private static double rawAmount(LivingThing attacker, Skill skill) {
        double hpMagnification = skill == null ? 0 : skill.getHpMagnification();
        double atkMagnification = skill == null ? 0 : skill.getAtkMagnification();
        double dfkMagnification = skill == null ? 0 : skill.getDefMagnification();
        long skillExtraDamage = skill == null ? 0 : skill.getExtraDamage();
        return attacker.getHpMax() * hpMagnification
                + atkMagnification * attacker.getAttack()
                + attacker.getDefence() * dfkMagnification
                + skillExtraDamage
                + attacker.getExtraDamage();
    }

    /**
     * 一次伤害的两段数值。原始值保留小数（见类注释：提前取整会让最终伤害漂移）。
     *
     * @param rawDamageAmount       原始值
     * @param calculateDamageResult 套完公式的结果（≥ 0，已取整）
     */
    public record Result(double rawDamageAmount, long calculateDamageResult) {
    }
}
