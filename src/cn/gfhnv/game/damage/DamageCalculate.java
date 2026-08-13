package cn.gfhnv.game.damage;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.CalculateDamageEndEvent;
import cn.gfhnv.game.event.CalculateDamageGetStatusEvent;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.IgnoreDefenceEffect;
import cn.gfhnv.game.skill.Skill;

/**
 * 伤害计算工具类。提供静态方法计算一次攻击造成的最终伤害值。
 * <p>
 * 计算流程：
 * <ol>
 *     <li>发布 {@link CalculateDamageGetStatusEvent}（允许外部在计算前修改攻击者/目标状态）；</li>
 *     <li>判定是否暴击（依据攻击者暴击率 {@code getGetCriticalRATE()}，暴击时伤害乘上爆伤 {@code getCriticalDMG()}）；</li>
 *     <li>根据攻击者元素属性（金木水火土）取对应的目标抗性、元素增伤与元素穿透；</li>
 *     <li>计算目标有效防御（考虑防御削减 {@code getDefenseLoss()} 与无视防御效果
 *     {@link IgnoreDefenceEffect}）；</li>
 *     <li>发布 {@link CalculateDamageEndEvent}（允许外部在计算完成后修正结果）；</li>
 *     <li>套用最终伤害公式并返回取整后的伤害值。</li>
 * </ol>
 * <p>
 * 最终伤害公式：
 * <pre>
 * (hp * hp倍率 + atk倍率 * 攻击 + 防御倍率 * 防御 + 技能额外伤害 + 使用者额外伤害)
 *   × (1 + 元素增伤)
 *   × (1 − 目标抗性 + 穿透)
 *   × (1 − 目标伤害吸收)
 *   × (等级 * 10 + 200) / (等级 * 10 + 200 + 目标有效防御)
 *   × 单体伤害倍率
 *   × 暴击倍率
 * </pre>
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
        double damageAbsorbed = targetEntity.getDamageAbsorbedPercent();
        double enhance = attacker.getEnhance();
        double attack = attacker.getAttack();
        double hp = attacker.getHp();
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
        double lossAmount = 0;
        if (!attacker.getEntityEffectList().isEmpty()) {
            for (Effect effect : attacker.getEntityEffectList()) {
                if (effect instanceof IgnoreDefenceEffect) {
                    defenseLoss += ((IgnoreDefenceEffect) effect).getPercent();
                    lossAmount += ((IgnoreDefenceEffect) effect).getAmount();
                }
            }
        }
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
        return (long) (((hp * hpMagnification + atkMagnification * attack + attackerDefence * dfkMagnification + extraDamage + attackerExtraDamage) * (1 + enhance) * (1 - resistance + penetration) * (1 - damageAbsorbed) * ((level * 10 + 200) / (level * 10 + 200 + targetDefence))) * individualMultipleArea * criticalDamageEnhance);
    }
}
