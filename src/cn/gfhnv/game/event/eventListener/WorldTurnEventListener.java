package cn.gfhnv.game.event.eventListener;

import cn.gfhnv.game.Thing;
import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.PhysicsStateUpdateEvent;
import cn.gfhnv.game.event.WorldTurnEvent;
import cn.gfhnv.game.system.fight.TurnManager;
import cn.gfhnv.game.world.World;

public class WorldTurnEventListener {
    @SubscribeEvent
    public void worldTurnEventListener(WorldTurnEvent worldTurnEvent) {
        EventBus.post(new PhysicsStateUpdateEvent());
    }
}
