package cn.gfhnv.game.system.fight;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.FightPastOneTurnEvent;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;

public class TurnManager {
    public static PriorityBlockingQueue<TurnEntry> actionQueue = new PriorityBlockingQueue<>();
    private static long pastTimes;


    private static BigDecimal presentTime;
    private static List<TurnEntry> turns=new ArrayList<>();
    private static boolean isInitialized=false;
    public static void sort(){
        turns.sort(((o1, o2) -> {
            if (o1.getNeedTime().add(o1.getStartTime()).compareTo(o2.getStartTime().add(o2.getNeedTime()))<0){return -1;}
            if (o1.getNeedTime().add(o1.getStartTime()).compareTo(o2.getStartTime().add(o2.getNeedTime()))==0){
                if (o1.getLivingThing().getSpeed()>o2.getLivingThing().getSpeed()){return 1;}
                if (o1.getLivingThing().getSpeed()<o2.getLivingThing().getSpeed()){return -1;}
                return 0;
            }
            if (o1.getNeedTime().add(o1.getStartTime()).compareTo(o2.getStartTime().add(o2.getNeedTime()))>0){return 1;}
            return 0;
        }));
    }
    public static void init(Fight fight) {
        presentTime=BigDecimal.ZERO;
        List<LivingThing> canActEntity =new ArrayList<>();
        for (LivingThing entity:fight.getAllEntities()){
            if(entity != null){
                canActEntity.add(entity);
            }
        }
        if(canActEntity.isEmpty()){return;}
        for (LivingThing livingThing: canActEntity){
            turns.add(new TurnEntry(livingThing, BigDecimal.valueOf(10000/livingThing.getSpeed()),TurnManager.getPresentTime()));
        }
        if (turns.isEmpty()){return;}
        isInitialized=true;
        sort();
    }
    public static void nextTurn(Fight fight) {
        EventBus.post(new FightPastOneTurnEvent(fight));
    }
    public static TurnEntry getNextTurnOf(LivingThing livingThing) {
        TurnEntry a;
        for (TurnEntry turn : turns) {
            a = turn;
            if (a.getLivingThing().equals(livingThing)) {
                return a;
            }
        }
        return null;
    }
    public static void advanceByPercent(BigDecimal percent,TurnEntry t) {
        if (t==null){return;}
        t.setNeedTime(t.getNeedTime().multiply(BigDecimal.ONE.subtract(percent)));
    }

    public static BigDecimal getPresentTime() {
        return presentTime;
    }

    public static void setPresentTime(BigDecimal presentTime) {
        TurnManager.presentTime = presentTime;
    }

    public static List<TurnEntry> getTurns() {
        return turns;
    }

    public static void setTurns(List<TurnEntry> turns) {
        TurnManager.turns = turns;
    }

    public static boolean isIsInitialized() {
        return isInitialized;
    }

    public static void setIsInitialized(boolean isInitialized) {
        TurnManager.isInitialized = isInitialized;
    }

    public static void advanceByAmount(BigDecimal amount, TurnEntry t) {
        if (t==null){return;}
        t.setNeedTime(t.getNeedTime().subtract(amount));
    }
    public static void delayByPercent(BigDecimal percent,TurnEntry t) {
        if (t==null){return;}
        t.setNeedTime(t.getNeedTime().multiply(BigDecimal.ONE.add(percent)));
    }
    public static void delayByAmount(BigDecimal amount,TurnEntry t) {
        if (t==null){return;}
        t.setNeedTime(t.getNeedTime().add(amount));
    }
    public static Queue<TurnEntry> getActionQueue() {
        return actionQueue;
    }
}
