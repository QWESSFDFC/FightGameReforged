package cn.gfhnv.game.damage;

import cn.gfhnv.game.entity.Entity;
import cn.gfhnv.game.entity.LivingThing;

public class Damage {
    private LivingThing attacker;
    private LivingThing attackedEntity;
    private Long damageAmount;

    public Damage(LivingThing attacker, LivingThing attackedEntity) {
        this.attacker = attacker;
        this.attackedEntity = (LivingThing) attackedEntity;
        damageAmount = DamageCalculate.calculate(attacker, (LivingThing) attackedEntity);
    }

    public LivingThing getAttacker() {
        return attacker;
    }

    public void setAttacker(LivingThing attacker) {
        this.attacker = attacker;
    }

    public Entity getAttackedEntity() {
        return attackedEntity;
    }

    public void setAttackedEntity(LivingThing attackedEntity) {
        this.attackedEntity = (LivingThing) attackedEntity;
    }

    public Long getDamageAmount() {
        return damageAmount;
    }

    public void setDamageAmount(Long damageAmount) {
        this.damageAmount = damageAmount;
    }
}
