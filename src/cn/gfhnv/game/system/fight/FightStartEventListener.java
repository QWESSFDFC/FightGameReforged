package cn.gfhnv.game.system.fight;

import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.FightPastOneTurnEvent;
import cn.gfhnv.game.event.FightStartEvent;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.addAll;

public class FightStartEventListener {
    @SubscribeEvent
    public void onFightStartEvent(FightStartEvent event) {
        EventBus.register(new FightTurnPastListener());
        EventBus.register(new FightEndEventListener());
        TurnManager.arrangeTurn(event.getFight().getAllEntities(), 10);
        EventBus.post(new FightPastOneTurnEvent(event.getFight()));
    }
}
