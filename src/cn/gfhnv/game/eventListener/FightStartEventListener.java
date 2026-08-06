package cn.gfhnv.game.eventListener;

import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.FightPastOneTurnEvent;
import cn.gfhnv.game.event.FightStartEvent;
import cn.gfhnv.game.system.fight.TurnManager;


public class FightStartEventListener {
    @SubscribeEvent
    public void onFightStartEvent(FightStartEvent event) {
        FightTurnPastListener listener = new FightTurnPastListener();
        EventBus.register(listener);
        EventBus.register(new FightEndEventListener(listener));
        TurnManager.init(event.getFight());
        for (LivingThing livingThing : event.getFight().getAllEntities()) livingThing.whenFightStart(event.getFight());
        EventBus.post(new FightPastOneTurnEvent(event.getFight()));

    }
}
