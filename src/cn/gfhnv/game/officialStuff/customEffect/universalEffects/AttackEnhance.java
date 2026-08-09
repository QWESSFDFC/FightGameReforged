package cn.gfhnv.game.officialStuff.customEffect.universalEffects;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;

public class AttackEnhance extends Effect {
    private boolean isOn = false;
    private double percent = 0;
    private long amount = 0;


    public AttackEnhance(double percent, int latTime) {
        super("attackEnhanceEffect");
        this.percent = percent;
        this.setLastTime(latTime);
    }

    public AttackEnhance(long amount, int lastTime) {
        super("attackEnhanceEffect");
        this.amount = amount;
        this.setLastTime(lastTime);
    }

    public AttackEnhance(AttackEnhance attackEnhance) {
        super(attackEnhance.getID());
        this.setLastTime(attackEnhance.getLastTime());
        this.setLevel(attackEnhance.getLevel());
        this.setOrigin(attackEnhance.getOrigin());
        this.amount = attackEnhance.amount;
        this.percent = attackEnhance.percent;
        this.isOn = attackEnhance.isOn;

    }

    @Override
    public void comeIntoEffect(LivingThing thing) {
        if (!isOn) {
            thing.setAttackEnhanceAmount(thing.getAttackEnhanceAmount()+amount);
            thing.setAttackEnhancePercent(thing.getAttackEnhancePercent()+percent);
        }
    }

    @Override
    public void whenLastTimeEnd(LivingThing thing) {
        isOn = false;
        thing.setAttackEnhanceAmount(thing.getAttackEnhanceAmount()-amount);
        thing.setAttackEnhancePercent(thing.getAttackEnhancePercent()-percent);
    }

    @Override
    public Effect copy() {
        return new AttackEnhance(this);
    }
}
