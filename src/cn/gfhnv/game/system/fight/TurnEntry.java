package cn.gfhnv.game.system.fight;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.interfaces.ISpecialAction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


public class TurnEntry {

    /**
     * 普通回合的优先级（默认值）。
     */
    public static final int PRIORITY_NORMAL = 0;

    /**
     * 「额外回合」的优先级：<b>时间相同的时候要压过普通回合先手</b>。
     * <p>
     * 典型场景是击杀【完整容器】拿到的奖励回合：它和受益者的下一回合一样排在
     * {@code presentTime} 上，而 {@code TurnManager.sort()} 原先在时间打平时是<b>按速度</b>排的，
     * 于是"奖励回合"会被场上更快的单位抢先执行 —— 看起来像奖励没生效。
     * 时间相同（例如「灾厄-弑魂焚诏」把所有敌人拉到 {@code presentTime}）时，
     * 奖励回合必须先动。
     * <p>
     * ⚠️ 它和 {@link #isExtra()} 是<b>两件事</b>，别合并：
     * {@code isExtra} 管"这个回合<b>不推进</b>身上效果的剩余回合"（记账口径），
     * {@code priority} 管"时间打平时<b>谁先动</b>"（排序口径）。
     * 白厄变身的连击只用了前者，没有动排序。
     */
    public static final int PRIORITY_EXTRA = 100;

    private LivingThing livingThing;
    private BigDecimal startTime;
    private BigDecimal needTime;
    private List<ISpecialAction> lastExecuteList = new ArrayList<>();
    private List<ISpecialAction> firstExecuteList = new ArrayList<>();
    private boolean isExtra = false;
    private int priority = PRIORITY_NORMAL;
    /**
     * 这个回合条目<b>是谁给的</b>（给日志看的可读标识，例如"完整容器奖励"）。
     * <p>
     * <b>为什么需要它</b>：白厄变身的连击、击杀【完整容器】的奖励，**两者都是"额外回合"**，
     * 在日志里长得一模一样 —— 只有加上来源标识，才看得出<b>到底哪一条先执行</b>。
     * 这也是排查"奖励回合被顶掉 / 空过"这类问题的唯一抓手。
     * <p>
     * {@code null} = 没标识（普通回合、以及没标来源的额外回合）。
     */
    private String tag;
    private ActionSignal actionSignal;

    public TurnEntry(LivingThing livingThing, BigDecimal needTime, BigDecimal startTime) {
        this.livingThing = livingThing;
        this.needTime = needTime;
        this.startTime = startTime;
        this.actionSignal = livingThing.getController().getActionSignal();
    }

    public TurnEntry(LivingThing livingThing, BigDecimal needTime, BigDecimal startTime, ActionSignal actionSignal) {
        this.livingThing = livingThing;
        this.needTime = needTime;
        this.startTime = startTime;
        this.actionSignal = actionSignal;
    }

    public List<ISpecialAction> getFirstExecuteList() {
        return firstExecuteList;
    }

    public void setFirstExecuteList(List<ISpecialAction> firstExecuteList) {
        this.firstExecuteList = firstExecuteList;
    }

    public ActionSignal getActionSignal() {
        return actionSignal;
    }

    public TurnEntry setActionSignal(ActionSignal actionSignal) {
        this.actionSignal = actionSignal;
        return this;
    }

    public boolean isExtra() {
        return isExtra;
    }

    public TurnEntry setExtra(boolean extra) {
        isExtra = extra;
        return this;
    }

    /**
     * @return 排序优先级；时间（{@code startTime + needTime}）相同时，这个值大的先执行
     */
    public int getPriority() {
        return priority;
    }

    /**
     * 设置排序优先级。默认 {@link #PRIORITY_NORMAL}。
     * <p>
     * 只在时间打平时起作用，<b>压不过时间</b> —— 想让一个回合"插队"，仍然要把它排在
     * {@code presentTime} 上（例如 {@code FlameReaver#grantExtraTurn}）。
     *
     * @param priority 优先级，越大越先执行
     * @return 当前条目（链式）
     */
    public TurnEntry setPriority(int priority) {
        this.priority = priority;
        return this;
    }

    /**
     * @return 这个回合是谁给的（可读标识）；没标就是 {@code null}
     */
    public String getTag() {
        return tag;
    }

    /**
     * 给这个回合条目打一个<b>来源标识</b>，会出现在日志里（回合头与"获得额外回合"那一行）。
     * <p>
     * 用途是<b>让人一眼看出</b>"这一条额外回合是白厄大招的连击，还是击杀完整容器的奖励" ——
     * 两者在日志里本来就长得一样，不加标识根本没法判断谁排在前面。
     *
     * @param tag 可读标识（例如 {@code "完整容器奖励"}）
     * @return 当前条目（链式）
     */
    public TurnEntry setTag(String tag) {
        this.tag = tag;
        return this;
    }

    public List<ISpecialAction> getLastExecuteList() {
        return lastExecuteList;
    }

    public void setLastExecuteList(List<ISpecialAction> lastExecuteList) {
        this.lastExecuteList = lastExecuteList;
    }

    public TurnEntry addLastSpecialAction(ISpecialAction iSpecialAction) {
        lastExecuteList.add(iSpecialAction);
        return this;
    }

    public TurnEntry addFirstAction(ISpecialAction iSpecialAction) {
        firstExecuteList.add(iSpecialAction);
        return this;
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

    @Override
    public String toString() {
        return "TurnEntry{" +
                "livingThing=" + livingThing +
                ", firstExecuteList=" + firstExecuteList +
                ", startTime=" + startTime +
                ", needTime=" + needTime +
                ", lastExecuteList=" + lastExecuteList +
                ", isExtra=" + isExtra +
                ", priority=" + priority +
                ", tag=" + tag +
                ", actionSignal=" + actionSignal +
                '}';
    }
}
