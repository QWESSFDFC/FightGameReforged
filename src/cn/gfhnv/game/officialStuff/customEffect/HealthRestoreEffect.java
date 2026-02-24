package cn.gfhnv.game.officialStuff.customEffect;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;

public class HealthRestoreEffect extends Effect {
    public HealthRestoreEffect(int level, int lastTime) {
        super("healthRestoreEffect", level, lastTime);
    }

    public HealthRestoreEffect() {
        super("healthRestoreEffect");
    }

    @Override
    public void comeIntoEffect(LivingThing thing) {
        long hp1 = thing.getHp();
        thing.setHp((long) (Math.min(thing.getHpMax(), thing.getHp()) + this.getLevel() * 2L));
        long hp2 = thing.getHp() - hp1;
    }
}
