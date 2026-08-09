package cn.gfhnv.game.officialStuff.customEffect.universalEffects;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.effect.EffectTags;
import cn.gfhnv.game.entity.LivingThing;

public class CriticalDMGEnhanceEffect extends Effect {

    private boolean isOn = false;
    private double percent = 0;
    private long amount = 0;


    public CriticalDMGEnhanceEffect(double percent, int lastTime) {
        super("criticalDMGEnhanceEffect");
        this.getEffectTagsList().add(EffectTags.POSITIVE);
        this.percent = percent;
        this.setLastTime(lastTime);
    }

    public CriticalDMGEnhanceEffect(long amount, int lastTime) {
        super("criticalDMGEnhanceEffect");
        this.getEffectTagsList().add(EffectTags.POSITIVE);
        this.amount = amount;
        this.setLastTime(lastTime);
    }

    public CriticalDMGEnhanceEffect(CriticalDMGEnhanceEffect enhance) {
        super(enhance.getID());
        this.setLastTime(enhance.getLastTime());
        this.getEffectTagsList().add(EffectTags.POSITIVE);
        this.setLevel(enhance.getLevel());
        this.setOrigin(enhance.getOrigin());
        this.amount = enhance.amount;
        this.percent = enhance.percent;
        this.isOn = enhance.isOn;

    }

    @Override
    public Effect copy() {
        return new CriticalDMGEnhanceEffect(this);
    }

    @Override
    public void comeIntoEffect(LivingThing thing) {
        if (!isOn) {
            thing.setCriticalDMGEnhanceAmount(thing.getCriticalDMGEnhanceAmount() + amount);
            thing.setCriticalDMGEnhancePercent(thing.getCriticalDMGEnhancePercent() + percent);
        }
        isOn = true;
    }

    @Override
    public void whenLastTimeEnd(LivingThing thing) {
        thing.setCriticalDMGEnhanceAmount(thing.getCriticalDMGEnhanceAmount() - amount);
        thing.setCriticalDMGEnhancePercent(thing.getCriticalDMGEnhancePercent() - percent);
        isOn = false;
    }
}
