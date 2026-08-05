package cn.gfhnv.game.officialStuff.customEffect.universalEffects;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;

public class DefenseEnhanceEffect extends Effect {
    private double baseNum;
    private double enhanceNum;
    private double enhanceN;
    private boolean isOn = false;

    public DefenseEnhanceEffect(DefenseEnhanceEffect effect) {
        super(effect.getID());
        this.setLastTime(effect.getLastTime());
        this.setLevel(effect.getLevel());
        this.baseNum = effect.baseNum;
        this.enhanceN = effect.enhanceN;
        this.enhanceNum = effect.enhanceNum;
        this.isOn = effect.isOn;
    }

    public DefenseEnhanceEffect() {
        super("defenseEnhanceEffect");
        baseNum = 1.0;
        enhanceNum = 1.0;
        enhanceN = this.getLevel() * enhanceNum + baseNum;
        this.setLastTime(1);
    }

    public DefenseEnhanceEffect(String id, int level, int lastTime) {
        super(id, level, lastTime);
        enhanceNum = 1.0;
        baseNum = 1.0;
        enhanceN = this.getLevel() * enhanceNum + baseNum;
    }

    @Override
    public Effect copy() {
        return new DefenseEnhanceEffect(this);
    }

    @Override
    public void comeIntoEffect(LivingThing thing) {
        if (!isOn) {
            thing.setDefence((long) (thing.getDefence() * (1 + enhanceN)));
        }
        isOn = true;
    }

    @Override
    public void whenLastTimeEnd(LivingThing thing) {
        thing.setDefence((long) (thing.getDefence() * (1 - enhanceN)));
        isOn = false;
    }
}
