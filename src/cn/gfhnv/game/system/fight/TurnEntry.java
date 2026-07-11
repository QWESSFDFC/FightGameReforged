package cn.gfhnv.game.system.fight;

import cn.gfhnv.game.entity.LivingThing;

import java.math.BigDecimal;


public class TurnEntry {
    private LivingThing livingThing;
    private BigDecimal startTime;
    private BigDecimal needTime;

    public TurnEntry(LivingThing livingThing, BigDecimal needTime, BigDecimal startTime) {
        this.livingThing = livingThing;
        this.needTime = needTime;
        this.startTime = startTime;
    }

    public BigDecimal getStartTime() {
        return startTime;
    }

    public void setStartTime(BigDecimal startTime) {
        this.startTime = startTime;
    }

    public BigDecimal getNeedTime() {
        return needTime;
    }

    public void setNeedTime(BigDecimal needTime) {
        this.needTime = needTime;
    }

    public LivingThing getLivingThing() {
        return livingThing;
    }

    public void setLivingThing(LivingThing livingThing) {
        this.livingThing = livingThing;
    }

}
