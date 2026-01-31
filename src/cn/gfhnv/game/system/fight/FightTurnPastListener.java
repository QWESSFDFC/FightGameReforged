package cn.gfhnv.game.system.fight;

import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.event.FightPastOneTurnEvent;
import cn.gfhnv.game.event.WorldTurnEvent;

public class FightTurnPastListener {
    @SubscribeEvent
    public void worldTurnEventListener(FightPastOneTurnEvent fightPastOneTurnEvent) {}
}
