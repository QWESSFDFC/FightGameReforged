package cn.gfhnv.game.event;
import cn.gfhnv.game.damage.Damage;
import cn.gfhnv.game.entity.LivingThing;
public class DamageEvent extends Event {
    private final Damage damage;
    private final LivingThing attacker;
    private final LivingThing attackedEntity;
    public Damage getDamage() {
        return damage;
    }
    public LivingThing getAttacker() {
        return attacker;
    }
    public LivingThing getAttackedEntity() {
        return attackedEntity;
    }
    public DamageEvent(LivingThing attacker, LivingThing attackedEntity) {
        this.attacker = attacker;
        this.attackedEntity = attackedEntity;
        damage=new Damage(attacker,attackedEntity);
    }
}
