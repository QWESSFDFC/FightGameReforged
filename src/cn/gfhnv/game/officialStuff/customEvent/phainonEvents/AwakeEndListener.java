package cn.gfhnv.game.officialStuff.customEvent.phainonEvents;

import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.Event;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.officialStuff.customEntity.players.Phainon;
import cn.gfhnv.game.system.fight.ActionSignal;

public class AwakeEndListener {
    @SubscribeEvent
    public void end(AwakenEndEvent endEvent){
        endEvent.getPhainon().setAwaken(false);
        endEvent.getPhainon().getController().setActionSignal(ActionSignal.NORMAL);
        endEvent.getPhainon().getController().setSkills(endEvent.getPhainon().getSkills());
        endEvent.getPhainon().setShowSpecialMes(user -> {
            if (user instanceof Phainon){
                System.out.println("当前火种数量:"+((Phainon) user).getPyroheart());
            }
        });
        EventBus.unregister(this);
        EventBus.register(endEvent.getPhainon().getSelectEventListener());
    }
}
