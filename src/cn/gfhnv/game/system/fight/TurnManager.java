package cn.gfhnv.game.system.fight;

import cn.gfhnv.game.entity.Entity;
import cn.gfhnv.game.entity.LivingThing;

import java.util.*;
import java.util.stream.Collectors;

public class TurnManager {
    private static long pastTimes;
    public static long getPastTimes() {
        return pastTimes;
    }
    public static void pastTimesAdd(){
        pastTimes++;
    }
    public static void setPastTimes(long pastTimes) {
        TurnManager.pastTimes = pastTimes;
    }
    public static List<LivingThing> arrangeTurn(List<LivingThing> livingThings, int times) {
        List<TurnEntry> allEntries = new ArrayList<>();
        for (LivingThing livingThing : livingThings) {
            double actionPoint = 1000.0 / livingThing.getSpeed();
            for (int i = 0; i < times; i++) {
                allEntries.add(new TurnEntry(actionPoint * (pastTimes +i), livingThing));
            }
        }
     allEntries.sort((e1, e2) -> {
            int oValueComparison = Double.compare(e1.oValue, e2.oValue);
            if (oValueComparison != 0) {
                return oValueComparison;
            }
            return Integer.compare((int) e2.livingThing.getSpeed(), (int) e1.livingThing.getSpeed());
        });
        List<LivingThing> result = new ArrayList<>(allEntries.size());
        for (TurnEntry entry : allEntries) {
            result.add(entry.livingThing);
        }
        return result;
    }
    private static class TurnEntry {
        double oValue;
        LivingThing livingThing;
        TurnEntry(double oValue, LivingThing livingThing) {
            this.oValue = oValue;
            this.livingThing = livingThing;
        }
    }
}
