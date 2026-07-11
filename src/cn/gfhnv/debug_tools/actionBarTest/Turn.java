package cn.gfhnv.debug_tools.actionBarTest;

import cn.gfhnv.debug_tools.actionBarTest.eventsAndListeners.TurnPastEvent;
import cn.gfhnv.game.entity.Entity;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.system.fight.Fight;

import java.math.BigDecimal;

public class Turn {
    private Entity actingEntity;
    private BigDecimal startTime;
    private BigDecimal needTime;
    public Turn(Entity actingEntity, BigDecimal startTime,BigDecimal needTime) {
        this.actingEntity = actingEntity;
        this.startTime = startTime;
        this.needTime = needTime;
    }

    public Entity getActingEntity() {
        return actingEntity;
    }

    public BigDecimal getStartTime() {
        return startTime;
    }

    public BigDecimal getNeedTime() {
        return needTime;
    }

    public void setNeedTime(BigDecimal needTime) {
        this.needTime = needTime;
    }

    public void setStartTime(BigDecimal startTime) {
        this.startTime = startTime;
    }

    public void act(Fight fight){
        System.out.println(actingEntity.getName());
        System.out.println("startTime" +startTime);
        System.out.println("needTime"+needTime);
        System.out.println("actTime"+TestTurnManager.getPresentTime().toString());
        Turn turn=new Turn(actingEntity,TestTurnManager.getPresentTime(),BigDecimal.valueOf(10000/actingEntity.transToLivingTing().getSpeed()));
        TestTurnManager.getTurns().add(turn);
        System.out.println("now time"+ TestTurnManager.getPresentTime().toString());
        System.out.println("new turn");
        if (!( TestTurnManager.getPresentTime().compareTo(BigDecimal.valueOf(1000))!=-1)){
            EventBus.post(new TurnPastEvent(fight));
        }
    }
}
