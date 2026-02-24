package cn.gfhnv.game.system.fight;

import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.FightPastOneTurnEvent;

public class FightTurnPastListener {
    private TurnEntry presentTurn;

    @SubscribeEvent
    public void fightTurnPastOne(FightPastOneTurnEvent fightPastOneTurnEvent) throws InterruptedException {
        fightPastOneTurnEvent.getFight().getFighterList().removeIf(livingThing -> !livingThing.isAlive());
        fightPastOneTurnEvent.getFight().getEnemiesList().removeIf(livingThing -> !livingThing.isAlive());

        if (fightPastOneTurnEvent.getFight().getFighterList().isEmpty()) {
            EventBus.post(new FightEndEvent(false, fightPastOneTurnEvent.getFight()));
            return;
        }


        if (fightPastOneTurnEvent.getFight().getEnemiesList().isEmpty()) {
            EventBus.post(new FightEndEvent(true, fightPastOneTurnEvent.getFight()));
            return;
        }
        this.presentTurn = TurnManager.actionQueue.take();
        if (presentTurn.getLivingThing() == null) {
            TurnManager.nextTurn(fightPastOneTurnEvent.getFight());
        }
        System.out.println("现在是" + presentTurn.getLivingThing().getName() + "的回合");
        System.out.println("状态:HP:" + presentTurn.getLivingThing().getHp() + "/" + presentTurn.getLivingThing().getHpMax());
        presentTurn.getLivingThing().getController().act(fightPastOneTurnEvent.getFight());
        presentTurn.getLivingThing().onActionTaken();
        Thread.sleep(100);
        TurnManager.nextTurn(fightPastOneTurnEvent.getFight());
    }
}
