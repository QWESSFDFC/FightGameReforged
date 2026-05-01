package cn.gfhnv.game.event;

import cn.gfhnv.game.damage.Damage;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entity.skill.Skill;

public class DamageEvent extends Event {
    private final Damage damage;
    private final LivingThing attacker;
    private final LivingThing attackedEntity;
    private final Skill skill;
    public DamageEvent(LivingThing attacker, LivingThing attackedEntity, Skill skill) {
        this.attacker = attacker;
        this.attackedEntity = attackedEntity;
        this.skill = skill;
        damage = new Damage(attacker, attackedEntity, skill);
    }

    public Damage getDamage() {
        return damage;
    }

    public LivingThing getAttacker() {
        return attacker;
    }

    public LivingThing getAttackedEntity() {
        return attackedEntity;
    }


    public Skill getSkill() {
        return skill;
    }
}
