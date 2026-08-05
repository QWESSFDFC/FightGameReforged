package cn.gfhnv.game.officialStuff.customEffect.universalEffects;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;

public class HealthRestoreEffect extends Effect {
    private double baseNum = 10;
    private double enhanceNum = 100;

    public HealthRestoreEffect(int level, int lastTime) {
        super("healthRestoreEffect", level, lastTime);
    }

    public HealthRestoreEffect(HealthRestoreEffect effect) {
        super(effect.getID());
        this.setLastTime(effect.getLastTime());
        this.setLevel(effect.getLevel());
        this.baseNum = effect.baseNum;
        this.enhanceNum = effect.enhanceNum;
    }

    public HealthRestoreEffect() {
        super("healthRestoreEffect");
    }

    @Override
    public Effect copy() {
        return new HealthRestoreEffect(this);
    }

    public double getBaseNum() {
        return baseNum;
    }

    public void setBaseNum(double baseNum) {
        this.baseNum = baseNum;
    }

    public double getEnhanceNum() {
        return enhanceNum;
    }

    public void setEnhanceNum(double enhanceNum) {
        this.enhanceNum = enhanceNum;
    }

    @Override
    public void comeIntoEffect(LivingThing thing) {
        long hp1 = thing.getHp();
        thing.setHp((long) (Math.min(thing.getHpMax(), thing.getHp()) + this.getLevel() * enhanceNum + baseNum));
        long hp2 = thing.getHp() - hp1;
    }

}
