package cn.gfhnv.game.system.fight;

import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.event.FightPastOneTurnEvent;

public class FightTurnPastListener {
    @SubscribeEvent
    public void fightTurnPastOne(FightPastOneTurnEvent fightPastOneTurnEvent) {
        TurnManager.pastTimesAdd();
        TurnManager.arrangeTurn(fightPastOneTurnEvent.getFight().getAllEntities(), 1);
    }
}
