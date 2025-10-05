package cn.gfhnv.game.event.eventListener;
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
     List<Entity> entities=wd.getWorld().getEntityList();
     for (Entity en:entities){
         if (en instanceof LivingThing){
             List<Effect> effects=((LivingThing) en).getEntityEffectList();
             for (Effect ef:effects){
                 if (ef.getLastTime()==0){
                     ((LivingThing) en).removeEffect(ef);
                     System.out.println(en.getName()+"的"+ef.getID()+"持续时间到了.");
                 }
                 ef.setLastTime(ef.getLastTime()-1);
             }
         }
     }
     World.setEntityList(entities);
 }
}
