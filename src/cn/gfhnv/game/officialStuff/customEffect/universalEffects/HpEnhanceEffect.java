package cn.gfhnv.game.officialStuff.customEffect.universalEffects;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.effect.EffectTags;
import cn.gfhnv.game.entity.LivingThing;

public class HpEnhanceEffect extends Effect {
    private boolean isOn = false;
    private double percent = 0;
    private long amount = 0;


    public HpEnhanceEffect(double percent, int lastTime) {
        super("hpEnhanceEffect");
        this.percent = percent;this.getEffectTagsList().add(EffectTags.POSITIVE);
        this.setLastTime(lastTime);
    }
    public HpEnhanceEffect(long amount, int lastTime) {
        super("hpEnhanceEffect");
        this.amount=amount;
        this.setLastTime(lastTime);this.getEffectTagsList().add(EffectTags.POSITIVE);
    }

    public HpEnhanceEffect(HpEnhanceEffect enhance) {
        super(enhance.getID());
        this.setLastTime(enhance.getLastTime());
        this.setLevel(enhance.getLevel());
        this.setOrigin(enhance.getOrigin());
        this.amount = enhance.amount;
        this.percent = enhance.percent;
        this.isOn = enhance.isOn;
        this.getEffectTagsList().add(EffectTags.POSITIVE);
    }

    @Override
    public Effect copy() {
        return new HpEnhanceEffect(this);
    }
    @Override
    public void comeIntoEffect(LivingThing thing) {
        thing.renewHp();
        if (!isOn) {
            thing.setHpEnhanceAmount(thing.getHpEnhanceAmount()+amount);
            thing.setHpEnhancePercent(thing.getHpEnhancePercent()+percent);
        }
        isOn = true;
    }

    @Override
    public void whenLastTimeEnd(LivingThing thing) {
        thing.renewHp();
        thing.setHpEnhanceAmount(thing.getHpEnhanceAmount()-amount);
        thing.setHpEnhancePercent(thing.getHpEnhancePercent()-percent);
        isOn = false;
    }
}
