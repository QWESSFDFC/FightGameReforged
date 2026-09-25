package cn.gfhnv.game.system.fight;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.FightPastOneTurnEvent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


public class TurnManager {

    private static BigDecimal presentTime;
    private static List<TurnEntry> turns = new ArrayList<>();
    private static boolean isInitialized = false;

    public static void removeTheDeath() {
        turns.removeIf(entry -> !entry.getLivingThing().isAlive());
    }

    public static void sort() {
        if (turns == null || turns.isEmpty()) {
            return;
        }
        turns.sort(Comparator
                .comparing((TurnEntry t) -> t.getStartTime().add(t.getNeedTime()))
                .thenComparing(t -> t.getLivingThing().getSpeed(), Comparator.reverseOrder())
        );
    }

    public static void init(Fight fight) {
        presentTime = BigDecimal.ZERO;
        List<LivingThing> canActEntity = new ArrayList<>();
        for (LivingThing entity : fight.getAllEntities()) {
            if (entity != null) {
                canActEntity.add(entity);
            }
        }
        if (canActEntity.isEmpty()) {
            return;
        }
        for (LivingThing livingThing : canActEntity) {
            turns.add(new TurnEntry(livingThing, BigDecimal.valueOf(10000)
                    .divide(BigDecimal.valueOf(livingThing.getSpeed()), 10, RoundingMode.HALF_UP), TurnManager.getPresentTime()));
        }
        if (turns.isEmpty()) {
            return;
        }
        isInitialized = true;
        sort();
    }

    public static void nextTurn(Fight fight) {
        EventBus.post(new FightPastOneTurnEvent(fight));
    }

    public static TurnEntry getNextTurnOf(LivingThing livingThing) {
        TurnEntry a;
        TurnManager.sort();
        for (TurnEntry turn : turns) {
            a = turn;
            if (a.getLivingThing().equals(livingThing)) {
                return a;
            }
        }
        return null;
    }

    public static void advanceByPercent(BigDecimal percent, TurnEntry t) {
        if (t == null) {
            return;
        }
        t.setNeedTime(t.getNeedTime().multiply(BigDecimal.ONE.subtract(percent)));
    }

    /**
     * @return 当前时间轴时间
     * <p>
     * <b>不会返回 {@code null}</b>：只有 {@link #init(Fight)} 之后 {@code presentTime} 才有值，
     * 而"先建好战斗、再往时间轴加实体"的场合（自测、手动加实体）不会调用 init。
     * 那些调用点会把返回值直接塞进 {@link TurnEntry} 的 {@code startTime}，
     * 一旦是 null，{@link #sort()} 里的 {@code startTime.add(needTime)} 就会抛 NPE。
     * 所以这里统一兜底成 {@link BigDecimal#ZERO}（时间轴的起点）。
     */
    public static BigDecimal getPresentTime() {
        return presentTime == null ? BigDecimal.ZERO : presentTime;
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
        if (t == null) {
            return;
        }
        t.setNeedTime(t.getNeedTime().subtract(amount));
    }

    public static void delayByPercent(BigDecimal percent, TurnEntry t) {
        if (t == null) {
            return;
        }
        t.setNeedTime(t.getNeedTime().multiply(BigDecimal.ONE.add(percent)));
    }

    public static void delayByAmount(BigDecimal amount, TurnEntry t) {
        if (t == null) {
            return;
        }
        t.setNeedTime(t.getNeedTime().add(amount));
    }

}
