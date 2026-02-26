package cn.gfhnv.game.system.fight;

import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.entity.skill.Skill;
import cn.gfhnv.game.event.EffectUpdateEvent;
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
            return;
        }
        if (!this.presentTurn.getLivingThing().isAlive()) {
            TurnManager.nextTurn(fightPastOneTurnEvent.getFight());
            return;
        }
        System.out.println();
        System.out.println("现在是" + presentTurn.getLivingThing().getName() + "的回合");
        System.out.println("状态:HP:" + presentTurn.getLivingThing().getHp() + "/" + presentTurn.getLivingThing().getHpMax());
        presentTurn.getLivingThing().getController().act(fightPastOneTurnEvent.getFight());
        presentTurn.getLivingThing().onActionTaken();
        for (Skill skill : presentTurn.getLivingThing().getController().getSkills()) {
            skill.setNowCoolDown(Math.max(0, skill.getNowCoolDown() - 1));
        }
        Thread.sleep(100);
        EventBus.post(new EffectUpdateEvent(presentTurn.livingThing));
        TurnManager.nextTurn(fightPastOneTurnEvent.getFight());
    }
}
