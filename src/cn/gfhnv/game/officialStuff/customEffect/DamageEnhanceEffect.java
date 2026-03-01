package cn.gfhnv.game.officialStuff.customEffect;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;

public class DamageEnhanceEffect extends Effect {
    private double enhanceN;
    private boolean isOn;

    public DamageEnhanceEffect() {
        super("damageEnhanceEffect");
    }

    public DamageEnhanceEffect(int level, int lastTime) {
        super("damageEnhanceEffect", level, lastTime);
    }

    @Override
    public void setLevel(int level) {
        super.setLevel(level);
        enhanceN = this.getLevel() * 0.5 + 0.9;
    }

    @Override
    public void comeIntoEffect(LivingThing thing) {
        if (!isOn) {
            thing.setEnhance(thing.getEnhance() + enhanceN);
        }
        this.isOn = true;
    }

    @Override
    public void whenLastTimeEnd(LivingThing livingThing) {
        livingThing.setEnhance(livingThing.getEnhance() - enhanceN);
        this.isOn = false;
    }
}
