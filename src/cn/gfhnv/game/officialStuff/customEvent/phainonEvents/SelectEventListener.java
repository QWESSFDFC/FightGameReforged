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
               ((Phainon) livingThing).setPyroheart(Math.min(((Phainon) livingThing).getPyroheart_max(), ((Phainon) livingThing).getPyroheart())+1);
               System.out.println(livingThing.getName()+"获得了一个火种");
           }
       }
    }
}
