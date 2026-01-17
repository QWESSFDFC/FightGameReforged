package cn.gfhnv.game.damage;

import cn.gfhnv.game.entity.LivingThing;

public class DamageCalculate {
    public static long calculate(LivingThing attacker, LivingThing attackEntity) {
        double enhance = attacker.getEnhance();
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
        double hpMagnification = attacker.getHpMagnification();
        double atkMagnification = attacker.getAtkMagnification();
        double dfkMagnification = attacker.getDfkMagnification();
        long l = attacker.getLevel();
        double critialDMG = -1;
        double criticalRATE = -1;
        cn.gfhnv.game.system.ElementSort yuanshu = attacker.getElementSort();
        double dfk2 = attackEntity.getDfk();
        double dfkloss = attackEntity.getDfkloss();
        criticalRATE = (attacker).getGetCriticalRATE();
        critialDMG = (attacker).getCriticalDMG();
        if (Math.random() <= criticalRATE) {
            System.out.println("暴击!");
            return switch (yuanshu) {
                case FIRE ->
                        (long) ((hp * hpMagnification + atkMagnification * atk + dfk * dfkMagnification) * (1 + enhance) * (1 - fireResistance + chuantong) * (1 - damageAbsorbed) * (1 + critialDMG) * ((double) (l * 10 + 200) / (l * 10 + 200 + dfk2 * (1 - dfkloss))));
                case WATER ->
                        (long) ((hp * hpMagnification + atkMagnification * atk + dfk * dfkMagnification) * (1 + enhance) * (1 - waterResistance + chuantong) * (1 - damageAbsorbed) * (1 + critialDMG) * ((double) (l * 10 + 200) / (l * 10 + 200 + dfk2 * (1 - dfkloss))));
                case DIRT ->
                        (long) ((hp * hpMagnification + atkMagnification * atk + dfk * dfkMagnification) * (1 + enhance) * (1 - dirtResistance + chuantong) * (1 + critialDMG) * (1 - damageAbsorbed) * ((double) (l * 10 + 200) / (l * 10 + 200 + dfk2 * (1 - dfkloss))));
                case METAL ->
                        (long) ((hp * hpMagnification + atkMagnification * atk + dfk * dfkMagnification) * (1 + enhance) * (1 - metalResistance + chuantong) * (1 + critialDMG) * (1 - damageAbsorbed) * ((double) (l * 10 + 200) / (l * 10 + 200 + dfk2 * (1 - dfkloss))));
                case WOOD ->
                        (long) ((hp * hpMagnification + atkMagnification * atk + dfk * dfkMagnification) * (1 + enhance) * (1 - woodResistance + chuantong) * (1 + critialDMG) * (1 - damageAbsorbed) * ((double) (l * 10 + 200) / (l * 10 + 200 + dfk2 * (1 - dfkloss))));
                default -> 0;
            };
        }
        return switch (yuanshu) {
            case FIRE ->
                    (long) ((hp * hpMagnification + atkMagnification * atk + dfk * dfkMagnification) * (1 + enhance) * (1 - fireResistance + chuantong) * (1 - damageAbsorbed) * ((double) (l * 10 + 200) / (l * 10 + 200 + dfk2 * (1 - dfkloss))));
            case WATER ->
                    (long) ((hp * hpMagnification + atkMagnification * atk + dfk * dfkMagnification) * (1 + enhance) * (1 - waterResistance + chuantong) * (1 - damageAbsorbed) * ((double) (l * 10 + 200) / (l * 10 + 200 + dfk2 * (1 - dfkloss))));
            case DIRT ->
                    (long) ((hp * hpMagnification + atkMagnification * atk + dfk * dfkMagnification) * (1 + enhance) * (1 - dirtResistance + chuantong) * (1 - damageAbsorbed) * ((double) (l * 10 + 200) / (l * 10 + 200 + dfk2 * (1 - dfkloss))));
            case METAL ->
                    (long) ((hp * hpMagnification + atkMagnification * atk + dfk * dfkMagnification) * (1 + enhance) * (1 - metalResistance + chuantong) * (1 - damageAbsorbed) * ((double) (l * 10 + 200) / (l * 10 + 200 + dfk2 * (1 - dfkloss))));
            case WOOD ->
                    (long) ((hp * hpMagnification + atkMagnification * atk + dfk * dfkMagnification) * (1 + enhance) * (1 - woodResistance + chuantong) * (1 - damageAbsorbed) * ((double) (l * 10 + 200) / (l * 10 + 200 + dfk2 * (1 - dfkloss))));
            default -> 0;
        };
    }
}
