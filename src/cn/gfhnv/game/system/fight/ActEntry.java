package cn.gfhnv.game.system.fight;

import cn.gfhnv.game.interfaces.ISpecialAction;

/**
 * 回合内的一条<b>行动条目</b>（2026-11-14 第 59 批，Step 6 第二步）。
 * <p>
 * 一个回合里现在<b>所有</b>要执行的事情都是条目：开场动作、正式行动、重排/额外行动、收尾动作
 * （{@code RunQueue} 见 {@code FightTurnPastListener#runActQueue}）。规则就是作者定的四条：
 * <ul>
 *     <li><b>一个接一个</b>：取队首 → 执行 → 再取；</li>
 *     <li><b>执行中可以追加</b>：追加只影响还没执行的（循环会继续取）；</li>
 *     <li><b>执行前可以移除</b>：直接把条目从队列里删掉即可（{@code SKIP} 一类）；</li>
 *     <li><b>有优先级</b>：数值大的先执行；<b>同优先级按入队顺序</b>（稳定排序）。</li>
 * </ul>
 * <b>默认优先级</b>保持原来"开场 > 正式行动 > 收尾"的三段顺序（见下面的常量），
 * 所以这次改造对现有机制的<b>顺序零变化</b>；需要插队的技能/效果用 {@code addAct(action, priority)} 自己排。
 *
 * @param action   要做的事
 * @param priority 优先级（越大越先执行；同优先级按入队顺序）
 * @param label    日志/排错用的可读标识（可为 {@code null}）
 * @author AI（DeepSeek）生成；设计稿作者：仓库作者
 */
public record ActEntry(ISpecialAction action, int priority, String label) {

    /**
     * 开场动作的优先级（原来的 {@code firstExecuteList}）。
     */
    public static final int PRIORITY_FIRST = 1000;

    /**
     * 回合头检查（存活检查 / 特殊状态显示）的优先级：排在开场动作之后、正式行动之前。
     */
    public static final int PRIORITY_CHECK = 900;

    /**
     * 正式行动的优先级（原来的 {@code controller.act}）。
     */
    public static final int PRIORITY_NORMAL = 0;

    /**
     * 重排 / 额外行动的优先级：排在正式行动之后、收尾动作之前
     * （原来的"按信号决定要不要排下一回合 / WITHOUT_NEW_TURN 再动一次"）。
     */
    public static final int PRIORITY_RESCHEDULE = -500;

    /**
     * 收尾动作的优先级（原来的 {@code lastExecuteList}）。
     */
    public static final int PRIORITY_LAST = -1000;

    /**
     * @param action 要做的事
     * @return 一条"正式行动"优先级的条目
     */
    public static ActEntry of(ISpecialAction action) {
        return new ActEntry(action, PRIORITY_NORMAL, null);
    }
}
