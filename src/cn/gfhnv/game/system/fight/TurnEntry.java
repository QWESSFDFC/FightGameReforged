package cn.gfhnv.game.system.fight;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.interfaces.ISpecialAction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


public class TurnEntry {
    private LivingThing livingThing;
    private BigDecimal startTime;
    private BigDecimal needTime;
    private List<ISpecialAction> iSpecialActionList = new ArrayList<>();
    private boolean isExtra = false;
 private ActionSignal actionSignal;

    public ActionSignal getActionSignal() {
        return actionSignal;
    }

    public void setActionSignal(ActionSignal actionSignal) {
        this.actionSignal = actionSignal;
    }

    public TurnEntry(LivingThing livingThing, BigDecimal needTime, BigDecimal startTime) {
        this.livingThing = livingThing;
        this.needTime = needTime;
        this.startTime = startTime;
        this.actionSignal=livingThing.getController().getActionSignal();
    }
 public TurnEntry(LivingThing livingThing, BigDecimal needTime, BigDecimal startTime,ActionSignal actionSignal){
     this.livingThing = livingThing;
     this.needTime = needTime;
     this.startTime = startTime;
     this.actionSignal=actionSignal;
 }
    public boolean isExtra() {
        return isExtra;
    }

    public TurnEntry setExtra(boolean extra) {
        isExtra = extra;
        return this;
    }

    public List<ISpecialAction> getiSpecialActionList() {
        return iSpecialActionList;
    }

    public void setiSpecialActionList(List<ISpecialAction> iSpecialActionList) {
        this.iSpecialActionList = iSpecialActionList;
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
