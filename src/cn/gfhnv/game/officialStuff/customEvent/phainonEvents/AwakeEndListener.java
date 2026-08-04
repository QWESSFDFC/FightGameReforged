package cn.gfhnv.game.officialStuff.customEvent.phainonEvents;

import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.event.Event;
import cn.gfhnv.game.event.EventBus;

public class AwakeEndListener {
    @SubscribeEvent
    public void end(AwakenEndEvent endEvent){
        endEvent.getPhainon().setAwaken(false);
        endEvent.getPhainon().getController().setSkills(endEvent.getPhainon().getSkills());
        System.out.println("白厄再次踏上轮回.....");
        EventBus.unregister(this);
    }
}
