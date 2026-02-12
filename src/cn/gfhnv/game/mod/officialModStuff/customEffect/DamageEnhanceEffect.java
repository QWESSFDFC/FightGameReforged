package cn.gfhnv.game.mod.officialModStuff.customEffect;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;

public class DamageEnhanceEffect extends Effect {
    public DamageEnhanceEffect() {super("damageEnhanceEffect");}
    public DamageEnhanceEffect(int level,int lastTime) {super("damageEnhanceEffect",level,lastTime);}
       private double enhanceN;
    @Override
    public void setLevel(int level) {
        super.setLevel(level);
        enhanceN=this.getLevel()*0.5+0.9;
    }
    @Override
    public void comeIntoEffect(LivingThing thing) {
        thing.setEnhance(thing.getEnhance() + enhanceN);
    }
    @Override
    public void whenLastTimeEnd(LivingThing livingThing) {
        livingThing.setEnhance(livingThing.getEnhance()-enhanceN);
    }
}
