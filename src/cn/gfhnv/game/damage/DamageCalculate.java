package cn.gfhnv.game.damage;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.CalculateDamageEndEvent;
import cn.gfhnv.game.event.CalculateDamageGetStatusEvent;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.skill.Skill;

/**
 * 伤害计算工具类。提供静态方法计算一次攻击造成的最终伤害值。
 * <p>
 * 计算流程：
 * <ol>
 *     <li>发布 {@link CalculateDamageGetStatusEvent}（允许外部在计算前修改攻击者/目标状态）；</li>
 *     <li>判定是否暴击（依据攻击者暴击率 {@code getGetCriticalRATE()}，暴击时伤害乘上爆伤 {@code getCriticalDMG()}）；</li>
 *     <li>根据攻击者元素属性（金木水火土）取对应的目标抗性、元素增伤与元素穿透；</li>
 *     <li>计算目标有效防御（考虑目标自身的防御削减 {@code getDefenseLoss()}，
 *     以及攻击者身上由 {@link cn.gfhnv.game.interfaces.IDefenceIgnore} 效果汇总出来的无视防御）；</li>
 *     <li>发布 {@link CalculateDamageEndEvent}（允许外部在计算完成后修正结果）；</li>
 *     <li>套用最终伤害公式并返回取整后的伤害值。</li>
 * </ol>
 * <p>
 * 最终伤害公式：
 * <pre>
 * (hp * hp倍率 + atk倍率 * 攻击 + 防御倍率 * 防御 + 技能额外伤害 + 使用者额外伤害)
 *   × (1 + 元素增伤)
 *   × (1 − 目标抗性) × (1 + 穿透)      // 抗性/穿透乘算；抗性为负（弱点）时受伤更多
 *   × 承伤倍率                        // Π(1 − 每个减伤)，乘算叠加，见 LivingThing#getDamageTakenMultiplier
 *   × (等级 * 10 + 200) / (等级 * 10 + 200 + 目标有效防御)
 *   × 单体伤害倍率
 *   × 暴击倍率
 * </pre>
 * 结果不会小于 0：负伤害会被 {@code LivingThing#getDamage} 当成治疗。
 * <p>
 * 减伤是<b>乘算</b>的：50% 与 25% 两个来源 → 只受 {@code 0.5 × 0.75 = 37.5%} 伤害，
 * 而不是相加的 25%。抗性与穿透同样乘算：抗性 50% + 穿透 50% → {@code 0.5 × 1.5 = 0.75}
 * （相加会得到 1.0，等于穿透完全抵消抗性，那是旧行为）。
 *
 * @author gfhnv
 */
public class DamageCalculate {

    /**
     * 计算一次攻击造成的最终伤害值（不实际结算，仅返回数值）。
     *
     * @param attacker     攻击者（技能使用者）
     * @param targetEntity 被攻击目标
     * @param skill        使用的技能（提供倍率与额外伤害）
     * @return 计算得到的最终伤害值
     */
    public static long calculate(LivingThing attacker, LivingThing targetEntity, Skill skill) {
        EventBus.post(new CalculateDamageGetStatusEvent(attacker, targetEntity));
        double criticalRate = attacker.getGetCriticalRATE();
        double criticalDamageEnhance = 1;
        if (Math.random() <= criticalRate) criticalDamageEnhance += attacker.getCriticalDMG();
        double resistance = 0;
        double penetration = 0;
        penetration = attacker.getPenetration();
        // 减伤是乘算叠加的（50% + 25% → 只受 37.5%），且已夹在 [0,1]，见 LivingThing#getDamageTakenMultiplier
        double damageTakenMultiplier = targetEntity.getDamageTakenMultiplier();
        double enhance = attacker.getEnhance();
        double attack = attacker.getAttack();
        double hp = attacker.getHpMax();
        double attackerDefence = attacker.getDefence();
        double individualMultipleArea = attacker.getIndividualMultipleArea();
        double hpMagnification = skill.getHpMagnification();
        double atkMagnification = skill.getAtkMagnification();
        double dfkMagnification = skill.getDefMagnification();
        long level = attacker.getLevel();
        long extraDamage = skill.getExtraDamage();
        long attackerExtraDamage = attacker.getExtraDamage();
        cn.gfhnv.game.system.ElementSort elementSort = attacker.getElementSort();
        double defenseLoss = targetEntity.getDefenseLoss();
        // 无视防御由「效果实现 IDefenceIgnore」提供，这里只读汇总值（不认识具体效果类）
        defenseLoss += attacker.getIgnoreDefencePercent();
        double lossAmount = attacker.getIgnoreDefenceAmount();
        double targetDefence = targetEntity.getDefence() * (1 - defenseLoss) - lossAmount;
        if (targetDefence <= 0) targetDefence = 0;
        switch (elementSort) {
            case DIRT -> {
                resistance = targetEntity.getDirtResistance();
                enhance += attacker.getDirtDamageEnhance();
                penetration += attacker.getDirtPenetration();
            }
            case FIRE -> {
                resistance = targetEntity.getFireResistance();
                enhance += attacker.getFireDamageEnhance();
                penetration += attacker.getFirePenetration();
            }
            case WOOD -> {
                resistance = targetEntity.getWoodResistance();
                enhance += attacker.getWoodDamageEnhance();
                penetration += attacker.getWoodPenetration();
            }
            case METAL -> {
                resistance = targetEntity.getMetalResistance();
                enhance += attacker.getMetalDamageEnhance();
                penetration += attacker.getMetalPenetration();
            }
            case WATER -> {
                resistance = targetEntity.getWaterResistance();
                enhance += attacker.getWaterDamageEnhance();
                penetration += attacker.getWaterPenetration();
            }
            default -> {
                resistance = 0;
            }
        }
        EventBus.post(new CalculateDamageEndEvent(attacker, targetEntity));
        // 抗性/穿透是**乘算**的：(1 − 抗性) × (1 + 穿透)
        // 抗性可以是负数（弱点 → 受伤更多），所以只夹「不为负」；
        // 穿透按「额外增伤」理解，负穿透当 0（不会反过来变成减伤）
        double resistanceMultiplier = Math.max(0, 1 - resistance) * (1 + Math.max(0, penetration));
        double base = hp * hpMagnification + atkMagnification * attack + attackerDefence * dfkMagnification
                + extraDamage + attackerExtraDamage;
        double damage = base
                * (1 + enhance)
                * resistanceMultiplier
                * damageTakenMultiplier
                * ((level * 10 + 200) / (level * 10 + 200 + targetDefence))
                * individualMultipleArea
                * criticalDamageEnhance;
        // 伤害永不为负：负数会被 LivingThing#getDamage 当成治疗
        return Math.max(0, (long) damage);
    }
}
