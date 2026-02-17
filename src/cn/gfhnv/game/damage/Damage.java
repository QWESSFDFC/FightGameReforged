package cn.gfhnv.game.damage;

import cn.gfhnv.game.entity.Entity;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entity.skill.Skill;

public class Damage {
    private LivingThing attacker;
    private LivingThing attackedEntity;
    private Long damageAmount;
    private Skill skill;

    public Damage(LivingThing attacker, LivingThing attackedEntity, Skill skill) {
        this.attacker = attacker;
        this.skill = skill;
        this.attackedEntity = (LivingThing) attackedEntity;
        damageAmount = DamageCalculate.calculate(attacker, (LivingThing) attackedEntity, skill);
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
