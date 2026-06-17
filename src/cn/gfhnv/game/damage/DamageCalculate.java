package cn.gfhnv.game.damage;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.skill.Skill;

public class DamageCalculate {
    public static long calculate(LivingThing attacker, LivingThing attackEntity, Skill skill) {
        double enhance = attacker.getEnhance();
        double metalDamageEnhance = attacker.getMetalDamageEnhance(), woodDamageEnhance = attacker.getWoodDamageEnhance(), waterDamageEnhance = attacker.getWaterDamageEnhance(), fireDamageEnhance = attacker.getFireDamageEnhance(), dirtDamageEnhance = attacker.getDirtDamageEnhance();
        double chuantong = attacker.getChuantong();
        double damageAbsorbed = attackEntity.getDamageAbsorbedPercent();
        double fireResistance = attackEntity.getFireResistance();
        double waterResistance = attackEntity.getWaterResistance();
        double metalResistance = attackEntity.getMetalResistance();
        double woodResistance = attackEntity.getWoodResistance();
        double dirtResistance = attackEntity.getDirtResistance();
        double atk = attacker.getAfk();
        double hp = attacker.getHp();
        double dfk = attacker.getDfk();
        double individualMultipleArea = attacker.getIndividualMultipleArea();
        double hpMagnification = skill.getHpMagnification();
        double atkMagnification = skill.getAtkMagnification();
        double dfkMagnification = skill.getDefMagnification();
        long l = attacker.getLevel();
        double critialDMG = -1;
        double criticalRATE = -1;
        long extraDamage = skill.getExtraDamage();
        long attackerExtraDamage = attacker.getExtraDamage();
        cn.gfhnv.game.system.ElementSort yuanshu = attacker.getElementSort();
        double dfk2 = attackEntity.getDfk();
        double dfkloss = attackEntity.getDefenseLoss();
        criticalRATE = (attacker).getGetCriticalRATE();
        critialDMG = (attacker).getCriticalDMG();
        if (Math.random() <= criticalRATE) {
            System.out.println("暴击!");
            return (long) switch (yuanshu) {
                case FIRE ->
                        (long) ((hp * hpMagnification + atkMagnification * atk + dfk * dfkMagnification + extraDamage + attackerExtraDamage) * (1 + enhance + fireDamageEnhance) * (1 - fireResistance + chuantong) * (1 - damageAbsorbed) * (1 + critialDMG) * ((double) (l * 10 + 200) / (l * 10 + 200 + dfk2 * (1 - dfkloss)))) * individualMultipleArea;
                case WATER ->
                        (long) ((hp * hpMagnification + atkMagnification * atk + dfk * dfkMagnification + extraDamage + attackerExtraDamage) * (1 + enhance + waterDamageEnhance) * (1 - waterResistance + chuantong) * (1 - damageAbsorbed) * (1 + critialDMG) * ((double) (l * 10 + 200) / (l * 10 + 200 + dfk2 * (1 - dfkloss)))) * individualMultipleArea;
                case DIRT ->
                        (long) ((hp * hpMagnification + atkMagnification * atk + dfk * dfkMagnification + extraDamage + attackerExtraDamage) * (1 + enhance + dirtDamageEnhance) * (1 - dirtResistance + chuantong) * (1 + critialDMG) * (1 - damageAbsorbed) * ((double) (l * 10 + 200) / (l * 10 + 200 + dfk2 * (1 - dfkloss)))) * individualMultipleArea;
                case METAL ->
                        (long) ((hp * hpMagnification + atkMagnification * atk + dfk * dfkMagnification + extraDamage + attackerExtraDamage) * (1 + enhance + metalDamageEnhance) * (1 - metalResistance + chuantong) * (1 + critialDMG) * (1 - damageAbsorbed) * ((double) (l * 10 + 200) / (l * 10 + 200 + dfk2 * (1 - dfkloss)))) * individualMultipleArea;
                case WOOD ->
                        (long) ((hp * hpMagnification + atkMagnification * atk + dfk * dfkMagnification + extraDamage + attackerExtraDamage) * (1 + enhance + woodDamageEnhance) * (1 - woodResistance + chuantong) * (1 + critialDMG) * (1 - damageAbsorbed) * ((double) (l * 10 + 200) / (l * 10 + 200 + dfk2 * (1 - dfkloss)))) * individualMultipleArea;
                default -> 0;
            };
        }
        return (long) switch (yuanshu) {
            case FIRE ->
                    (long) ((hp * hpMagnification + atkMagnification * atk + dfk * dfkMagnification + extraDamage + attackerExtraDamage) * (1 + enhance + fireDamageEnhance) * (1 - fireResistance + chuantong) * (1 - damageAbsorbed) * ((double) (l * 10 + 200) / (l * 10 + 200 + dfk2 * (1 - dfkloss)))) * individualMultipleArea;
            case WATER ->
                    (long) ((hp * hpMagnification + atkMagnification * atk + dfk * dfkMagnification + extraDamage + attackerExtraDamage) * (1 + enhance + waterDamageEnhance) * (1 - waterResistance + chuantong) * (1 - damageAbsorbed) * ((double) (l * 10 + 200) / (l * 10 + 200 + dfk2 * (1 - dfkloss)))) * individualMultipleArea;
            case DIRT ->
                    (long) ((hp * hpMagnification + atkMagnification * atk + dfk * dfkMagnification + extraDamage + attackerExtraDamage) * (1 + enhance + dirtDamageEnhance) * (1 - dirtResistance + chuantong) * (1 - damageAbsorbed) * ((double) (l * 10 + 200) / (l * 10 + 200 + dfk2 * (1 - dfkloss)))) * individualMultipleArea;
            case METAL ->
                    (long) ((hp * hpMagnification + atkMagnification * atk + dfk * dfkMagnification + extraDamage + attackerExtraDamage) * (1 + enhance + metalDamageEnhance) * (1 - metalResistance + chuantong) * (1 - damageAbsorbed) * ((double) (l * 10 + 200) / (l * 10 + 200 + dfk2 * (1 - dfkloss)))) * individualMultipleArea;
            case WOOD ->
                    (long) ((hp * hpMagnification + atkMagnification * atk + dfk * dfkMagnification + attackerExtraDamage + extraDamage) * (1 + enhance + woodDamageEnhance) * (1 - woodResistance + chuantong) * (1 - damageAbsorbed) * ((double) (l * 10 + 200) / (l * 10 + 200 + dfk2 * (1 - dfkloss)))) * individualMultipleArea;
            default -> 0;
        };
    }
}
