package cn.gfhnv.game.system.fight;

import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.FightPastOneTurnEvent;

public class FightTurnPastListener {
    private TurnEntry presentTurn;

    @SubscribeEvent
    public void fightTurnPastOne(FightPastOneTurnEvent fightPastOneTurnEvent) throws InterruptedException {
        this.presentTurn = TurnManager.actionQueue.take();
        if (this.presentTurn.getLivingThing().getType().equals("player")) {
            this.presentTurn.getLivingThing().entityAct();
        } else {
            presentTurn.getLivingThing().entityAct();
        }
        int i=0;
        for (LivingThing livingThing:fightPastOneTurnEvent.getFight().getFighterList()) {
            if (!livingThing.isAlive()) {i++;}
        }
        if (i==fightPastOneTurnEvent.getFight().getFighterList().size()) {
            EventBus.post(new FightEndEvent(false,fightPastOneTurnEvent.getFight()) );
            return;
        }
        i=0;
        for (LivingThing livingThing:fightPastOneTurnEvent.getFight().getEnemiesList()) {
            if (!livingThing.isAlive()) {i++;}
        }
        if (i==fightPastOneTurnEvent.getFight().getEnemiesList().size()) {
            EventBus.post(new FightEndEvent(true,fightPastOneTurnEvent.getFight()) );
            return;
        }
        Thread.sleep(100);
        TurnManager.nextTurn(fightPastOneTurnEvent.getFight());
    }
}
