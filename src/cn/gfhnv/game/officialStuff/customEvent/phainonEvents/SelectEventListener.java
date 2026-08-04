package cn.gfhnv.game.officialStuff.customEvent.phainonEvents;

import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.SelectTargetEvent;
import cn.gfhnv.game.officialStuff.customEntity.players.Phainon;

public class SelectEventListener {
    @SubscribeEvent
    public void listen(SelectTargetEvent event){
       for (LivingThing livingThing:event.getTargets()){
           if (livingThing instanceof Phainon){
               ((Phainon) livingThing).setSpark(Math.min(((Phainon) livingThing).getSpark_max(), ((Phainon) livingThing).getSpark())+1);
           }
       }
    }
}
