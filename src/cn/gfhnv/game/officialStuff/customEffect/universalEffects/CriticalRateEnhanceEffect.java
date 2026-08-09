package cn.gfhnv.game.officialStuff.customEffect.universalEffects;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.effect.EffectTags;
import cn.gfhnv.game.entity.LivingThing;

public class CriticalRateEnhanceEffect extends Effect {
    private boolean isOn = false;
    private double percent = 0;
    private long amount = 0;


    public CriticalRateEnhanceEffect(double percent, int lastTime) {
        super("criticalRateEnhanceEffect");
        this.percent = percent;
        this.setLastTime(lastTime);
        this.getEffectTagsList().add(EffectTags.POSITIVE);
    }
    public CriticalRateEnhanceEffect(long amount, int lastTime) {
        super("criticalRateEnhanceEffect");
        this.amount=amount;
        this.getEffectTagsList().add(EffectTags.POSITIVE);
        this.setLastTime(lastTime);
    }

    public CriticalRateEnhanceEffect(CriticalRateEnhanceEffect enhance) {
        super(enhance.getID());
        this.setLastTime(enhance.getLastTime());
        this.setLevel(enhance.getLevel());
        this.setOrigin(enhance.getOrigin());
        this.getEffectTagsList().add(EffectTags.POSITIVE);
        this.amount = enhance.amount;
        this.percent = enhance.percent;
        this.isOn = enhance.isOn;

    }

    @Override
    public Effect copy() {
        return new CriticalRateEnhanceEffect(this);
    }
    @Override
    public void comeIntoEffect(LivingThing thing) {
        if (!isOn) {
            thing.setCriticalRateEnhanceAmount(thing.getCriticalRateEnhanceAmount()+amount);
            thing.setCriticalRateEnhancePercent(thing.getCriticalRateEnhancePercent()+percent);
        }
        isOn = true;
    }

    @Override
    public void whenLastTimeEnd(LivingThing thing) {
        thing.setCriticalRateEnhanceAmount(thing.getCriticalRateEnhanceAmount()-amount);
        thing.setCriticalRateEnhancePercent(thing.getCriticalRateEnhancePercent()-percent);
        isOn = false;
    }

}
