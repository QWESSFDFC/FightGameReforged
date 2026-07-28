package cn.gfhnv.game.damage;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.skill.Skill;

public class DamageCalculate {

    public static long calculate(LivingThing attacker, LivingThing targetEntity, Skill skill) {
        double criticalRate = attacker.getGetCriticalRATE();
        double criticalDamageEnhance = 1;
        if (Math.random() <= criticalRate) criticalDamageEnhance += attacker.getCriticalDMG();
        double resistance = 0;
        double penetration = 0;
        penetration = attacker.getPenetration();
        double damageAbsorbed = targetEntity.getDamageAbsorbedPercent();
        double enhance = attacker.getEnhance();
        double attack = attacker.getAfk();
        double hp = attacker.getHp();
        double attackerDefence = attacker.getDfk();
        double individualMultipleArea = attacker.getIndividualMultipleArea();
        double hpMagnification = skill.getHpMagnification();
        double atkMagnification = skill.getAtkMagnification();
        double dfkMagnification = skill.getDefMagnification();
        long level = attacker.getLevel();
        long extraDamage = skill.getExtraDamage();
        long attackerExtraDamage = attacker.getExtraDamage();
        cn.gfhnv.game.system.ElementSort elementSort = attacker.getElementSort();
        double targetDefence = targetEntity.getDfk();
        double defenseLoss = targetEntity.getDefenseLoss();
        switch (elementSort) {
            case DIRT -> {
                resistance = targetEntity.getDirtResistance();
                enhance += attacker.getDirtDamageEnhance();
            }
            case FIRE -> {
                resistance = targetEntity.getFireResistance();
                enhance += attacker.getFireDamageEnhance();
            }
            case WOOD -> {
                resistance = targetEntity.getWoodResistance();
                enhance += attacker.getWoodDamageEnhance();
            }
            case METAL -> {
                resistance = targetEntity.getMetalResistance();
                enhance += attacker.getMetalDamageEnhance();
            }
            case WATER -> {
                resistance = targetEntity.getWaterResistance();
                enhance += attacker.getWaterDamageEnhance();
            }
            default -> {
                resistance = 0;
            }
        }

        return (long) (((hp * hpMagnification + atkMagnification * attack + attackerDefence * dfkMagnification + extraDamage + attackerExtraDamage) * (1 + enhance) * (1 - resistance + penetration) * (1 - damageAbsorbed) * ((level * 10 + 200) / (level * 10 + 200 + targetDefence * (1 - defenseLoss)))) * individualMultipleArea * criticalDamageEnhance);
    }
}
