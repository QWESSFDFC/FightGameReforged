package cn.gfhnv.game.eventListener;


import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EffectUpdateEvent;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.FightPastOneTurnEvent;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.ActionSignal;
import cn.gfhnv.game.system.fight.FightEndEvent;
import cn.gfhnv.game.system.fight.TurnEntry;
import cn.gfhnv.game.system.fight.TurnManager;
import cn.gfhnv.game.world.World;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class FightTurnPastListener {
    private TurnEntry presentTurn;

    @SubscribeEvent
    public void fightTurnPastOne(FightPastOneTurnEvent fightPastOneTurnEvent) throws InterruptedException {
        fightPastOneTurnEvent.getFight().getFighterList().removeIf(livingThing -> !livingThing.isAlive());
        fightPastOneTurnEvent.getFight().getEnemiesList().removeIf(livingThing -> !livingThing.isAlive());
        TurnManager.removeTheDeath();
        if (fightPastOneTurnEvent.getFight().getFighterList().isEmpty()) {
            EventBus.post(new FightEndEvent(false, fightPastOneTurnEvent.getFight()));
            return;
        }
        if (fightPastOneTurnEvent.getFight().getEnemiesList().isEmpty()) {
            EventBus.post(new FightEndEvent(true, fightPastOneTurnEvent.getFight()));
            return;
        }
        TurnManager.sort();
        presentTurn = TurnManager.getTurns().getFirst();
        TurnManager.getTurns().remove(presentTurn);
        TurnManager.setPresentTime(presentTurn.getNeedTime().add(presentTurn.getStartTime()));
        if (presentTurn.getLivingThing() == null) {
            TurnManager.nextTurn(fightPastOneTurnEvent.getFight());
            return;
        }
        if (!presentTurn.getLivingThing().isAlive()) {
            TurnManager.nextTurn(fightPastOneTurnEvent.getFight());
            return;
        }
        for (LivingThing entity : fightPastOneTurnEvent.getFight().getAllEntities()) {
            if (entity != null) {
                entity.updateSelf();
            }
        }
        for (Skill skill : presentTurn.getLivingThing().getController().getSkills()) {
            skill.setNowCoolDown(Math.max(0, skill.getNowCoolDown() - 1));
        }
        presentTurn.getLivingThing().recoverManaEveryTurn();
        System.out.println();
        World.turnTimer++;
        System.out.println("现在是" + presentTurn.getLivingThing().getName() + "的回合");
        System.out.println("状态:HP:" + presentTurn.getLivingThing().getHp() + "/" + presentTurn.getLivingThing().getHpMax());
        System.out.println("能量");
        System.out.println("金" + presentTurn.getLivingThing().getMetalMana().getAmount() + "/" + presentTurn.getLivingThing().getMetalMana().getAmountMax());
        System.out.println("木" + presentTurn.getLivingThing().getWoodMana().getAmount() + "/" + presentTurn.getLivingThing().getWoodMana().getAmountMax());
        System.out.println("水" + presentTurn.getLivingThing().getWaterMana().getAmount() + "/" + presentTurn.getLivingThing().getWaterMana().getAmountMax());
        System.out.println("火" + presentTurn.getLivingThing().getFireMana().getAmount() + "/" + presentTurn.getLivingThing().getFireMana().getAmountMax());
        System.out.println("土" + presentTurn.getLivingThing().getDirtMana().getAmount() + "/" + presentTurn.getLivingThing().getDirtMana().getAmountMax());
        if (presentTurn.getLivingThing().getController().getActionSignal().equals(ActionSignal.NORMAL)) { presentTurn.getLivingThing().getController().act(fightPastOneTurnEvent.getFight());}
        else if (presentTurn.getLivingThing().getController().getActionSignal().equals(ActionSignal.SPECIAL_ACTION)){presentTurn.getLivingThing().getController().getSpecialAction().execute(fightPastOneTurnEvent.getFight(),presentTurn.getLivingThing());}
        TurnEntry turn = new TurnEntry(presentTurn.getLivingThing(), BigDecimal.valueOf(10000)
                .divide(BigDecimal.valueOf(presentTurn.getLivingThing().getSpeed()), 10, RoundingMode.HALF_UP), TurnManager.getPresentTime());
        TurnManager.getTurns().add(turn);
        Thread.sleep(100);
        EventBus.post(new EffectUpdateEvent(presentTurn.getLivingThing()));
        TurnManager.nextTurn(fightPastOneTurnEvent.getFight());
    }
}
