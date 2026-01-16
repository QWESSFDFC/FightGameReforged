package cn.gfhnv.game.system.fight;

import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.FightStartEvent;
import cn.gfhnv.game.event.WorldTurnEvent;
import cn.gfhnv.game.world.World;

public class FightSystem {
    public static void nextTurn(FightStartEvent event) {
        System.out.printf("NEXT TURN START");
        EventBus.post(new WorldTurnEvent(event.getWorld()));
    }
}
