package cn.gfhnv.game.officialStuff.customEffect.universalEffects;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.effect.EffectTags;


public class IgnoreDefenceEffect extends Effect {
    private double percent = 0;
    private long amount = 0;


    public IgnoreDefenceEffect(IgnoreDefenceEffect effect) {
        super(effect.getID());
        this.setLastTime(effect.getLastTime());
        this.setLevel(effect.getLevel());
        this.amount = effect.amount;
        this.getEffectTagsList().add(EffectTags.POSITIVE);
        this.percent = effect.percent;
    }

    public IgnoreDefenceEffect() {
        super("ignoreDefenceEffect");
        this.getEffectTagsList().add(EffectTags.POSITIVE);

    }

    public double getPercent() {
        return percent;
    }

    public void setPercent(double percent) {
        this.percent = percent;
    }

    @Override
    public Effect copy() {
        return new IgnoreDefenceEffect(this);
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }


}
