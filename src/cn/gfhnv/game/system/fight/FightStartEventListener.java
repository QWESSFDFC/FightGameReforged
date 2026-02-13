package cn.gfhnv.game.system.fight;

import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.FightPastOneTurnEvent;
import cn.gfhnv.game.event.FightStartEvent;

public class FightStartEventListener {
    @SubscribeEvent
    public void onFightStartEvent(FightStartEvent event) {
        FightTurnPastListener listener = new FightTurnPastListener();
        EventBus.register(listener);
        EventBus.register(new FightEndEventListener(listener));
        TurnManager.initialQueue(event.getFight().getAllEntities());
        EventBus.post(new FightPastOneTurnEvent(event.getFight()));
    }
}
