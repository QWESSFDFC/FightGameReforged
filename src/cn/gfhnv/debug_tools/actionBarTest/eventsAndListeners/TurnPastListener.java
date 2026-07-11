package cn.gfhnv.debug_tools.actionBarTest.eventsAndListeners;

import cn.gfhnv.debug_tools.actionBarTest.TestTurnManager;
import cn.gfhnv.debug_tools.actionBarTest.Turn;
import cn.gfhnv.game.annotation.SubscribeEvent;

import java.math.BigDecimal;

public class TurnPastListener {
    @SubscribeEvent
    public void turnPast(TurnPastEvent event) throws InterruptedException {
        if (!TestTurnManager.isInitialized()){
            TestTurnManager.init(event.getFight());
            System.out.println("initialized");
        }
        TestTurnManager.setInitialized(true);
        TestTurnManager.sort();
        Turn a=TestTurnManager.getTurns().getFirst();
        if (TestTurnManager.getPresentTime().compareTo(BigDecimal.valueOf(100))!=-1){
            TestTurnManager.advanceByPercent(BigDecimal.valueOf(0.2),  TestTurnManager.getNextTurnOf(a.getActingEntity().transToLivingTing()));

        }

        TestTurnManager.setPresentTime(a.getNeedTime().add(TestTurnManager.getTurns().getFirst().getStartTime()));
        TestTurnManager.getTurns().remove(a);
        a.act(event.getFight());

    }

}
