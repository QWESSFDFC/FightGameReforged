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

    /**
     * 找某个生物「下一次<b>正常</b>行动」的时间轴条目。
     * <p>
     * <b>会跳过额外回合条目</b>（{@link TurnEntry#isExtra()} —— 白厄变身的连击、
     * 击杀【完整容器】的奖励回合都是这种）。那些条目的 {@code needTime} 是 0、
     * 排在 {@code presentTime} 上，天然是这个生物<b>最早</b>的一条；
     * 如果直接把它当"下次行动"返回，调用方（改 {@code startTime}/{@code needTime}、
     * 延后、冰冻）就会去改写别人的<b>奖励回合</b>本身 ——
     * 「灾厄-弑魂焚诏」让敌方全体立即行动时会把对方手里的额外回合顶掉。
     * <p>
     * 一个正常条目都没有时退回额外回合条目（总比什么都没有强）；
     * 连额外回合也没有就返回 {@code null} —— <b>调用方必须判空</b>。
     * 正常的 {@code null} 场合有两种：
     * <ol>
     *     <li>该生物<b>正在自己的回合里</b>：回合循环要等本回合跑完（{@code lastExecuteList} 之后）
     *     才给它排下一条，所以那一刻它不在时间轴上（见
     *     {@link cn.gfhnv.game.eventListener.FightTurnPastListener}）；</li>
     *     <li>战斗中途新召唤、还没排进时间轴的生物。</li>
     * </ol>
     *
     * @param livingThing 目标生物（允许为 {@code null}）
     * @return 下一次正常行动的条目；没有正常条目时退回额外回合条目；都没有则为 {@code null}
     */
    public static TurnEntry getNextTurnOf(LivingThing livingThing) {
        if (livingThing == null || turns == null || turns.isEmpty()) {
            return null;
        }
        TurnManager.sort();
        TurnEntry extraTurn = null;
        for (TurnEntry turn : turns) {
            if (turn == null || !livingThing.equals(turn.getLivingThing())) {
                continue;
            }
            if (!turn.isExtra()) {
                return turn;
            }
            if (extraTurn == null) {
                extraTurn = turn;
            }
        }
        return extraTurn;
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
