package cn.gfhnv.game.eventListener;


import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EffectUpdateEvent;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.FightPastOneTurnEvent;
import cn.gfhnv.game.interfaces.ISpecialAction;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.ActionSignal;
import cn.gfhnv.game.event.FightEndEvent;
import cn.gfhnv.game.system.fight.TurnEntry;
import cn.gfhnv.game.system.fight.TurnManager;
import cn.gfhnv.game.world.World;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class FightTurnPastListener {
    private static List<LivingThing> theDeath = new ArrayList<>();
    private static TurnEntry presentTurn;

    public static TurnEntry getPresentTurn() {
        return presentTurn;
    }

    @SubscribeEvent
    public void fightTurnPastOne(FightPastOneTurnEvent fightPastOneTurnEvent) throws InterruptedException {

        fightPastOneTurnEvent.getFight().getFighterList().removeIf(livingThing -> {
            if (!livingThing.isAlive()) {
                theDeath.add(livingThing);
                return true;
            }
            return false;
        });
        fightPastOneTurnEvent.getFight().getEnemiesList().removeIf(livingThing -> {
            if (!livingThing.isAlive()) {
                theDeath.add(livingThing);
                return true;
            }
            return false;
        });
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
        if (presentTurn.getLivingThing().getShowSpecialMes() != null) {
            presentTurn.getLivingThing().getShowSpecialMes().show(presentTurn.getLivingThing());
        }

        if (presentTurn.getActionSignal().equals(ActionSignal.NORMAL)) {
            presentTurn.getLivingThing().getController().act(fightPastOneTurnEvent.getFight());
        } else if (presentTurn.getActionSignal().equals(ActionSignal.SPECIAL_ACTION)) {
            presentTurn.getLivingThing().getController().getSpecialAction().execute(fightPastOneTurnEvent.getFight(), presentTurn.getLivingThing());
        }
        if (presentTurn.getActionSignal() != ActionSignal.WITHOUT_NEW_TURN&&!presentTurn.getActionSignal().equals(ActionSignal.SKIP_WITHOUT_NEW_TURN)) {
            TurnEntry turn = new TurnEntry(presentTurn.getLivingThing(), BigDecimal.valueOf(10000).divide(BigDecimal.valueOf(presentTurn.getLivingThing().getSpeed()), 10, RoundingMode.HALF_UP), TurnManager.getPresentTime());
            TurnManager.getTurns().add(turn);
        } else if (presentTurn.getActionSignal().equals(ActionSignal.WITHOUT_NEW_TURN)){
            presentTurn.getLivingThing().getController().act(fightPastOneTurnEvent.getFight());
        }
        if (!presentTurn.getiSpecialActionList().isEmpty()) {
            for (ISpecialAction iSpecialAction : presentTurn.getiSpecialActionList()) {
                iSpecialAction.execute(fightPastOneTurnEvent.getFight(), presentTurn.getLivingThing());
            }
        }
        EventBus.post(new EffectUpdateEvent(presentTurn.getLivingThing(), presentTurn));
        TurnManager.nextTurn(fightPastOneTurnEvent.getFight());
        for (LivingThing dead : theDeath) {
            dead.whenFightEnds();
        }
        theDeath.clear();
    }
}
