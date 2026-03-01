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
        presentTurn = TurnManager.actionQueue.take();
        if (presentTurn.getLivingThing() == null) {
            TurnManager.nextTurn(fightPastOneTurnEvent.getFight());
            return;
        }
        if (!presentTurn.getLivingThing().isAlive()) {
            TurnManager.nextTurn(fightPastOneTurnEvent.getFight());
            return;
        }
        System.out.println();
        System.out.println("现在是" + presentTurn.getLivingThing().getName() + "的回合");
        presentTurn.getLivingThing().recoverManaEveryTurn();
        System.out.println("状态:HP:" + presentTurn.getLivingThing().getHp() + "/" + presentTurn.getLivingThing().getHpMax());
        System.out.println("能量");
        System.out.println("金" + presentTurn.getLivingThing().getMetalMana().getAmount() + "/" + presentTurn.getLivingThing().getMetalMana().getAmountMax());
        System.out.println("木" + presentTurn.getLivingThing().getWoodMana().getAmount() + "/" + presentTurn.getLivingThing().getWoodMana().getAmountMax());
        System.out.println("水" + presentTurn.getLivingThing().getWaterMana().getAmount() + "/" + presentTurn.getLivingThing().getWaterMana().getAmountMax());
        System.out.println("火" + presentTurn.getLivingThing().getFireMana().getAmount() + "/" + presentTurn.getLivingThing().getFireMana().getAmountMax());
        System.out.println("土" + presentTurn.getLivingThing().getDirtMana().getAmount() + "/" + presentTurn.getLivingThing().getDirtMana().getAmountMax());
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
