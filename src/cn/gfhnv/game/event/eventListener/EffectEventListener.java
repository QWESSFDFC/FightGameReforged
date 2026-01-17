package cn.gfhnv.game.event.eventListener;
import cn.gfhnv.game.Thing;
import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.Entity;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.WorldTurnEvent;
import cn.gfhnv.game.world.World;
import java.util.List;
import java.util.Objects;
public class EffectEventListener {
 @SubscribeEvent
    public void effectTimer(WorldTurnEvent wd){
    List<Thing> things= World.getThings();
    for (Thing thing:things){
        if(thing instanceof LivingThing){
            List<Effect> effectList=((LivingThing) thing).getEntityEffectList();
            for (Effect ef:effectList){
                if (ef.getLastTime()<=0){
                    ((LivingThing) thing).removeEffect(ef);
                    System.out.println(((LivingThing) thing).getName()+"的"+ef.getID()+"持续时间到了.");
                }
                if (ef.getLastTime()>0){
                    ef.comeIntoEffect((LivingThing) thing);//效果生效
                }
                ef.setLastTime(ef.getLastTime()-1);
            }
        }
    }
 }
}
