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
    /**
     * 回合内的<b>行动队列</b>（一条队列，按 {@link ActEntry#priority()} 取队首）。
     * <p>
     * 原来的 {@code firstExecuteList} / {@code lastExecuteList} 变成它的两个<b>优先级视图</b>
     * （见 {@link #getFirstExecuteList()} / {@link #getLastExecuteList()}）：
     * 老调用方 {@code list.add(action)} 照样能用，只是落成 {@link ActEntry#PRIORITY_FIRST} /
     * {@link ActEntry#PRIORITY_LAST} 两种优先级的条目。
     */
    private final List<ActEntry> actQueue = new ArrayList<>();
    private LivingThing livingThing;
    private BigDecimal startTime;
    private BigDecimal needTime;
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

    /**
     * @return 开场动作的<b>优先级视图</b>（增删都落到 {@link ActEntry#PRIORITY_FIRST} 那一档）
     */
    public List<ISpecialAction> getFirstExecuteList() {
        return new BandView(ActEntry.PRIORITY_FIRST);
    }

    /**
     * @param firstExecuteList 新的开场动作表（整档替换）
     */
    public void setFirstExecuteList(List<ISpecialAction> firstExecuteList) {
        replaceBand(ActEntry.PRIORITY_FIRST, firstExecuteList);
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

    /**
     * @return 收尾动作的<b>优先级视图</b>（增删都落到 {@link ActEntry#PRIORITY_LAST} 那一档）
     */
    public List<ISpecialAction> getLastExecuteList() {
        return new BandView(ActEntry.PRIORITY_LAST);
    }

    /**
     * @param lastExecuteList 新的收尾动作表（整档替换）
     */
    public void setLastExecuteList(List<ISpecialAction> lastExecuteList) {
        replaceBand(ActEntry.PRIORITY_LAST, lastExecuteList);
    }

    public TurnEntry addLastSpecialAction(ISpecialAction iSpecialAction) {
        return addAct(iSpecialAction, ActEntry.PRIORITY_LAST);
    }

    public TurnEntry addFirstAction(ISpecialAction iSpecialAction) {
        return addAct(iSpecialAction, ActEntry.PRIORITY_FIRST);
    }

    /**
     * 往回合内队列里加一条行动条目。
     *
     * @param action   要做的事
     * @param priority 优先级（越大越先执行；同优先级按入队顺序）
     * @return 当前条目（链式）
     */
    public TurnEntry addAct(ISpecialAction action, int priority) {
        return addAct(this.livingThing, action, priority, null);
    }

    /**
     * 往回合内队列里加一条行动条目，并<b>显式指定行动者</b>。
     * <p>
     * 用于"这条动作属于 A、但排在 B 的回合里"的场景（免死回血、追打、反制等）；
     * 不指定时默认用回合行动者（老行为）。
     *
     * @param actor    执行这条动作的实体
     * @param action   要做的事
     * @param priority 优先级
     * @return 当前回合条目
     */
    public TurnEntry addAct(LivingThing actor, ISpecialAction action, int priority) {
        return addAct(actor, action, priority, null);
    }

    /**
     * 同 {@link #addAct(LivingThing, ISpecialAction, int)}，但带可读标签。
     *
     * @param actor    执行这条动作的实体
     * @param action   要做的事
     * @param priority 优先级
     * @param label    可读标识
     * @return 当前回合条目
     */
    public TurnEntry addAct(LivingThing actor, ISpecialAction action, int priority, String label) {
        if (action != null) {
            this.actQueue.add(new ActEntry(action, priority, label, actor));
        }
        return this;
    }

    /**
     * @return 回合内行动队列（活的；执行前想摘掉某条就 {@code removeIf} 它）
     */
    public List<ActEntry> getActQueue() {
        return this.actQueue;
    }

    /**
     * 清空回合内行动队列。
     */
    public void clearActs() {
        this.actQueue.clear();
    }

    /**
     * 整档替换某个优先级下的动作。
     *
     * @param priority 优先级
     * @param actions  新动作表
     */
    private void replaceBand(int priority, List<ISpecialAction> actions) {
        this.actQueue.removeIf(entry -> entry.priority() == priority);
        if (actions != null) {
            for (ISpecialAction action : actions) {
                addAct(action, priority);
            }
        }
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
                ", actQueue=" + actQueue +
                ", startTime=" + startTime +
                ", needTime=" + needTime +
                ", isExtra=" + isExtra +
                ", priority=" + priority +
                ", tag=" + tag +
                ", actionSignal=" + actionSignal +
                '}';
    }

    /**
     * 某一个优先级档的<b>视图</b>：让老调用方继续把队列当 {@code List<ISpecialAction>} 用。
     */
    private final class BandView extends java.util.AbstractList<ISpecialAction> {

        /**
         * 这一档的优先级。
         */
        private final int band;

        private BandView(int band) {
            this.band = band;
        }

        /**
         * @return 这一档在整条队列里的下标列表
         */
        private java.util.List<Integer> indices() {
            java.util.List<Integer> result = new java.util.ArrayList<>();
            for (int i = 0; i < actQueue.size(); i++) {
                if (actQueue.get(i).priority() == band) {
                    result.add(i);
                }
            }
            return result;
        }

        @Override
        public ISpecialAction get(int index) {
            return actQueue.get(indices().get(index)).action();
        }

        @Override
        public int size() {
            return indices().size();
        }

        @Override
        public boolean add(ISpecialAction action) {
            return actQueue.add(new ActEntry(action, band, null, TurnEntry.this.livingThing));
        }

        @Override
        public void add(int index, ISpecialAction element) {
            add(element);
        }

        @Override
        public ISpecialAction set(int index, ISpecialAction element) {
            int real = indices().get(index);
            ISpecialAction old = actQueue.get(real).action();
            actQueue.set(real, new ActEntry(element, band, null, TurnEntry.this.livingThing));
            return old;
        }

        @Override
        public ISpecialAction remove(int index) {
            int real = indices().get(index);
            return actQueue.remove(real).action();
        }

        @Override
        public void clear() {
            actQueue.removeIf(entry -> entry.priority() == band);
        }
    }
}
