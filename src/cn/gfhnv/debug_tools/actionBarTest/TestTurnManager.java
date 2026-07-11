package cn.gfhnv.debug_tools.actionBarTest;

import cn.gfhnv.game.entity.Entity;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.system.fight.Fight;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class TestTurnManager {
    private static BigDecimal presentTime;
    private static List<Turn> turns = new ArrayList<>();
    private static boolean isInitialized = false;

    public static void sort() {
        turns.sort(((o1, o2) -> {
            if (o1.getNeedTime().add(o1.getStartTime()).compareTo(o2.getStartTime().add(o2.getNeedTime())) < 0) {
                return -1;
            }
            if (o1.getNeedTime().add(o1.getStartTime()).compareTo(o2.getStartTime().add(o2.getNeedTime())) == 0) {
                if (o1.getActingEntity().transToLivingTing().getSpeed() > o2.getActingEntity().transToLivingTing().getSpeed()) {
                    return 1;
                }
                if (o1.getActingEntity().transToLivingTing().getSpeed() < o2.getActingEntity().transToLivingTing().getSpeed()) {
                    return -1;
                }
                return 0;
            }
            if (o1.getNeedTime().add(o1.getStartTime()).compareTo(o2.getStartTime().add(o2.getNeedTime())) > 0) {
                return 1;
            }
            return 0;
        }));
    }

    public static void init(Fight fight) {
        presentTime = BigDecimal.ZERO;
        List<LivingThing> canActEnitity = new ArrayList<>();
        for (Entity entity : fight.getAllEntities()) {
            if (entity instanceof LivingThing) {
                canActEnitity.add((LivingThing) entity);
            }
        }
        if (canActEnitity.isEmpty()) {
            return;
        }
        for (LivingThing livingThing : canActEnitity) {
            turns.add(new Turn(livingThing, TestTurnManager.getPresentTime(), BigDecimal.valueOf(10000 / livingThing.transToLivingTing().getSpeed())));
        }
        if (turns.isEmpty()) {
            return;
        }
        sort();
    }

    public static BigDecimal getPresentTime() {
        return presentTime;
    }

    public static void setPresentTime(BigDecimal presentTime) {
        TestTurnManager.presentTime = presentTime;
    }

    public static List<Turn> getTurns() {
        return turns;
    }

    public static boolean isInitialized() {
        return isInitialized;
    }

    public static void setInitialized(boolean initialized) {
        isInitialized = initialized;
    }

    public static Turn getNextTurnOf(LivingThing livingThing) {
        Turn a;
        for (int i = 0; i < turns.size(); i++) {
            a = turns.get(i);
            if (a.getActingEntity().equals(livingThing)) {
                return a;
            }
        }
        return null;
    }

    public static void advanceByPercent(BigDecimal percent, Turn t) {
        if (t == null) {
            return;
        }
        t.setNeedTime(t.getNeedTime().multiply(BigDecimal.ONE.subtract(percent)));
    }

    public static void advanceByAmount(BigDecimal amount, Turn t) {
        if (t == null) {
            return;
        }
        t.setNeedTime(t.getNeedTime().subtract(amount));
    }

    public static void delayByPercent(BigDecimal percent, Turn t) {
        if (t == null) {
            return;
        }
        t.setNeedTime(t.getNeedTime().multiply(BigDecimal.ONE.add(percent)));
    }

    public static void delayByAmount(BigDecimal amount, Turn t) {
        if (t == null) {
            return;
        }
        t.setNeedTime(t.getNeedTime().add(amount));
    }
}
