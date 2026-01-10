package cn.gfhnv.game.event.eventListener;

import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.PhysicsStateUpdateEvent;
import cn.gfhnv.game.event.WorldTurnEvent;

public class WorldTurnEventListener {
@SubscribeEvent
    public void worldTurnEventListner(WorldTurnEvent worldTurnEvent){
    EventBus.post(new PhysicsStateUpdateEvent(worldTurnEvent.getWorld()));
}
}
