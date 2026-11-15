package cn.gfhnv.game.event;

import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.fight.TurnEntry;

/**
 * <b>一个回合结束</b>：这条回合的行动队列（开场 → 正式行动 → 重排 → 收尾）已经全部执行完。
 * <p>
 * 与 {@link FightTurnStartEvent} 成对。注意：下回合的条目通常在"重排"动作里就排好了，
 * 所以这里已经能看到时间轴的下一步。
 * <p>
 * 发布点：{@code FightTurnPastListener#fightTurnPastOne(...)} 的 {@code runActQueue} 之后。
 *
 * @author AI（DeepSeek）生成
 */
public class FightTurnEndEvent extends Event {

    private final Fight fight;
    private final TurnEntry turn;

    public FightTurnEndEvent(Fight fight, TurnEntry turn) {
        this.fight = fight;
        this.turn = turn;
    }

    /**
     * @return 当前战斗
     */
    public Fight getFight() {
        return fight;
    }

    /**
     * @return 刚结束的回合条目
     */
    public TurnEntry getTurn() {
        return turn;
    }
}
