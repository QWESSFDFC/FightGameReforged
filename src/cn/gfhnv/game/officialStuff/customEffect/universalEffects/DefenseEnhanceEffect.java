package cn.gfhnv.game.officialStuff.customEffect.universalEffects;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.effect.EffectTags;
import cn.gfhnv.game.entity.LivingThing;

public class DefenseEnhanceEffect extends Effect {
    private double percent = 0;
    private long amount = 0;
    private boolean isOn = false;

    public DefenseEnhanceEffect(DefenseEnhanceEffect effect) {
        super(effect.getID());
        this.setLastTime(effect.getLastTime());
        this.setLevel(effect.getLevel());
        this.amount = effect.amount;
        this.getEffectTagsList().add(EffectTags.POSITIVE);
        this.percent = effect.percent;
        this.isOn = effect.isOn;
    }

    public DefenseEnhanceEffect() {
        super("defenseEnhanceEffect");
        this.getEffectTagsList().add(EffectTags.POSITIVE);
        this.setLastTime(1);
    }

    public DefenseEnhanceEffect(String id, int level, int lastTime) {
        super(id, level, lastTime);
        this.getEffectTagsList().add(EffectTags.POSITIVE);

    }

    @Override
    public Effect copy() {
        return new DefenseEnhanceEffect(this);
    }

    @Override
    public void comeIntoEffect(LivingThing thing) {
        if (!isOn) {
            thing.setDefenceEnhanceAmount(thing.getDefenceEnhanceAmount() + amount);
            thing.setDefenceEnhancePercent(thing.getDefenceEnhancePercent() + percent);
        }
        isOn = true;
    }

    @Override
    public void whenLastTimeEnd(LivingThing thing) {
        thing.setDefenceEnhanceAmount(thing.getDefenceEnhanceAmount() - amount);
        thing.setDefenceEnhancePercent(thing.getDefenceEnhancePercent() - percent);
        isOn = false;
    }
}
