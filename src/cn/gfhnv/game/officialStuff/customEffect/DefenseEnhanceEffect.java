package cn.gfhnv.game.officialStuff.customEffect;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;

public class DefenseEnhanceEffect extends Effect {
    private double baseNum;
    private double enhanceNum;
    private double enhanceN;
    private boolean isOn = false;

    public DefenseEnhanceEffect() {
        super("defenseEnhanceEffect");
        baseNum = 1.0;
        enhanceNum = 1.0;
        enhanceN = this.getLevel() * enhanceNum + baseNum;
    }

    public DefenseEnhanceEffect(String id, int level, int lastTime) {
        super(id, level, lastTime);
        enhanceNum = 1.0;
        baseNum = 1.0;
        enhanceN = this.getLevel() * enhanceNum + baseNum;
    }

    @Override
    public void comeIntoEffect(LivingThing thing) {
        if (!isOn) {
            thing.setDfk((long) (thing.getDfk() * (1 + enhanceN)));
        }
        isOn = true;
    }

    @Override
    public void whenLastTimeEnd(LivingThing thing) {
        thing.setDfk((long) (thing.getDfk() * (1 - enhanceN)));
        isOn = false;
    }
}
