package cn.gfhnv.game.system.fight;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.FightPastOneTurnEvent;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;

public class TurnManager {
    public static PriorityBlockingQueue<TurnEntry> actionQueue = new PriorityBlockingQueue<>();
    private static long pastTimes;

    public static void initialQueue(List<LivingThing> list) {
        for (LivingThing lv1 : list) {
            lv1.setPresentTurn(new TurnEntry(0, lv1));
            TurnEntry nextEntry = new TurnEntry(0, lv1);
            actionQueue.offer(nextEntry);
        }
    }

    public static void nextTurn(Fight fight) {
        new Thread(() -> {
            try {
                Thread.sleep(1);
            } catch (InterruptedException ignored) {}
            EventBus.post(new FightPastOneTurnEvent(fight));
        }).start();
    }

    public static Queue<TurnEntry> getActionQueue() {
        return actionQueue;
    }
}
