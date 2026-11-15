package cn.gfhnv.game.event;

import cn.gfhnv.game.system.fight.ActEntry;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.fight.TurnEntry;

/**
 * <b>回合内的一条行动条目执行完毕</b>（开场 / 头检查 / 正式行动 / 重排 / 收尾，逐条发布）。
 * <p>
 * 想"按优先级观察每一步发生了什么"的扩展（战斗日志、调试面板、AI 复盘）监听它；
 * 需要拦截/改顺序请用 {@link ActEntry} 的优先级与 {@code TurnEntry#addAct}，这里只做通知。
 * <p>
 * 发布点：{@code FightTurnPastListener#runActQueue(Fight, TurnEntry)} 每执行完一条之后。
 *
 * @author AI（DeepSeek）生成
 */
public class TurnActionExecutedEvent extends Event {

    private final Fight fight;
    private final TurnEntry turn;
    private final ActEntry action;

    public TurnActionExecutedEvent(Fight fight, TurnEntry turn, ActEntry action) {
        this.fight = fight;
        this.turn = turn;
        this.action = action;
    }

    /**
     * @return 当前战斗
     */
    public Fight getFight() {
        return fight;
    }

    /**
     * @return 条目所属的回合
     */
    public TurnEntry getTurn() {
        return turn;
    }

    /**
     * @return 刚执行完的条目（含优先级与可读标签，可能为 {@code null} 标签）
     */
    public ActEntry getAction() {
        return action;
    }
}
