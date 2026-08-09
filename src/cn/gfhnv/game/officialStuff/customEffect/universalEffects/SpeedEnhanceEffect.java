package cn.gfhnv.game.officialStuff.customEffect.universalEffects;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.effect.EffectTags;
import cn.gfhnv.game.entity.LivingThing;

public class SpeedEnhanceEffect extends Effect {
    private boolean isOn = false;
    private double percent = 0;
    private long amount = 0;


    public SpeedEnhanceEffect(double percent, int lastTime) {
        super("speedEnhanceEffect");
        this.percent = percent;
        this.setLastTime(lastTime);this.getEffectTagsList().add(EffectTags.POSITIVE);
    }
    public SpeedEnhanceEffect(long amount, int lastTime) {
        super("speedEnhanceEffect");
        this.amount=amount;
        this.setLastTime(lastTime);this.getEffectTagsList().add(EffectTags.POSITIVE);
    }

    public SpeedEnhanceEffect(SpeedEnhanceEffect enhance) {
        super(enhance.getID());
        this.setLastTime(enhance.getLastTime());
        this.setLevel(enhance.getLevel());
        this.setOrigin(enhance.getOrigin());
        this.amount = enhance.amount;
        this.percent = enhance.percent;this.getEffectTagsList().add(EffectTags.POSITIVE);
        this.isOn = enhance.isOn;

    }

    @Override
    public Effect copy() {
        return new SpeedEnhanceEffect(this);
    }
    @Override
    public void comeIntoEffect(LivingThing thing) {
        if (!isOn) {
            thing.setSpeedEnhanceAmount(thing.getSpeedEnhanceAmount()+amount);
            thing.setSpeedEnhancePercent(thing.getSpeedEnhancePercent()+percent);
        }
        isOn = true;
    }

    @Override
    public void whenLastTimeEnd(LivingThing thing) {
        thing.setSpeedEnhanceAmount(thing.getSpeedEnhanceAmount()-amount);
        thing.setSpeedEnhancePercent(thing.getSpeedEnhancePercent()-percent);
        isOn = false;
    }
}

