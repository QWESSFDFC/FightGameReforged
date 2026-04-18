package cn.gfhnv.game.officialStuff.customEffect.universalEffects;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;

public class DamageEnhanceEffect extends Effect {
    private double enhanceN;
    private boolean isOn;
    private double baseNum;
    private double enhanceNum;

    public DamageEnhanceEffect() {
        super("damageEnhanceEffect");
        this.isOn = false;
        this.baseNum = 1.0;
        this.enhanceNum = 1.0;
        enhanceN = this.getLevel() * enhanceNum + baseNum;
    }

    public DamageEnhanceEffect(int level, int lastTime) {
        super("damageEnhanceEffect", level, lastTime);
        this.isOn = false;
        this.baseNum = 1.0;
        this.enhanceNum = 1.0;
        enhanceN = this.getLevel() * enhanceNum + baseNum;
    }

    public DamageEnhanceEffect(String id, int level, int lastTime, double baseNum, double enhanceNum) {
        super(id, level, lastTime);
        this.baseNum = baseNum;
        this.enhanceNum = enhanceNum;
        enhanceN = this.getLevel() * enhanceNum + baseNum;
    }

    @Override
    public void setLevel(int level) {
        super.setLevel(level);
        enhanceN = this.getLevel() * enhanceNum + baseNum;
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
