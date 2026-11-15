package cn.gfhnv.game.event;

import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.fight.TurnEntry;

/**
 * <b>一个回合正式开始</b>：行动者已确定、冷却已减 1、法力已回复，回合头**即将**打印。
 * <p>
 * 此刻改行动者的状态（加效果、改属性、换控制器）会直接出现在本回合的回合头与动作里；
 * 想"跳过这一回合"请改 {@link TurnEntry#setActionSignal}，而不是在这里取消事件。
 * <p>
 * 发布点：{@code FightTurnPastListener#fightTurnPastOne(...)} 打印回合头之前。
 *
 * @author AI（DeepSeek）生成
 */
public class FightTurnStartEvent extends Event {

    private final Fight fight;
    private final TurnEntry turn;

    public FightTurnStartEvent(Fight fight, TurnEntry turn) {
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
     * @return 本回合的条目（行动者、时间、信号都在里面）
     */
    public TurnEntry getTurn() {
        return turn;
    }
}
