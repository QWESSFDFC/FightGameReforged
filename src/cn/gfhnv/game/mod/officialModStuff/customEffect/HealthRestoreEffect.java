package cn.gfhnv.game.mod.officialModStuff.customEffect;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;

public class HealthRestoreEffect extends Effect {
    public HealthRestoreEffect() {
        super("healthRestoreEffect", 5, 4);
    }
    @Override
    public void comeIntoEffect(LivingThing thing) {
        long hp1=thing.getHp();
        thing.setHp((long) (Math.min(thing.getHpMax(), thing.getHp())+this.getLevel()* 2L));
        long hp2=thing.getHp()-hp1;
        System.out.println(thing.getName()+"回复了"+hp2);
    }
}
