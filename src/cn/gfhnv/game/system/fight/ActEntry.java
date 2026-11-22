package cn.gfhnv.game.system.fight;

import cn.gfhnv.game.entity.LivingThing;
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
 * <p>
 * <b>行动者（2026-11-21 第 81 批，作者要求）</b>：每条条目都必须能回答"这条动作是<b>谁</b>做的"。
 * 以前执行时统一传 {@code TurnEntry#getLivingThing()}（当前回合的行动者）—— 对于"某人的免死/追打
 * 排在别人回合的收尾队列里"这类情况是错的：动作会被当成别人的回合行动执行。
 * 典型 bug：千冶·刃在【无量忿怒】期间被别人的回合打死，免死回血排进那个回合的收尾，
 * 执行者却是对方 → {@code user instanceof MortenaxBlade} 不成立 → 血量停在 1。
 * 现在条目自带 {@link #actor()}，执行时用它；{@code null} 才回退到回合行动者（兼容旧条目）。
 *
 * @param action   要做的事
 * @param priority 优先级（越大越先执行；同优先级按入队顺序）
 * @param label    日志/排错用的可读标识（可为 {@code null}）
 * @param actor    执行这条动作的实体（{@code null} = 回退到 {@code TurnEntry#getLivingThing()}）
 * @author AI（DeepSeek）生成；设计稿作者：仓库作者
 */
public record ActEntry(ISpecialAction action, int priority, String label, LivingThing actor) {

    /**
     * 星魂 2 的额外行动（例如白厄【支柱·死星天裁】满层后再选一次技能）：
     * 高于正式行动（0），低于回合头检查（900）。
     */
    public static final int PRIORITY_EIDOLON_EXTRA = 800;
    /**
     * 阶段转换（例如盗火行者进入二阶段）：<b>高于所有额外行动/正式行动</b>，
     * 保证“先切阶段、再让额外行动看到回满/免伤后的状态”。
     */
    public static final int PRIORITY_PHASE_TRANSITION = 2000;
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
     * 兼容旧调用方的三参构造（行动者为 {@code null}，执行时回退到回合行动者）。
     *
     * @param action   要做的事
     * @param priority 优先级
     * @param label    可读标识
     */
    public ActEntry(ISpecialAction action, int priority, String label) {
        this(action, priority, label, null);
    }

    /**
     * @param action 要做的事
     * @return 一条"正式行动"优先级的条目（行动者为 {@code null}，执行时回退到回合行动者）
     */
    public static ActEntry of(ISpecialAction action) {
        return new ActEntry(action, PRIORITY_NORMAL, null, null);
    }

    /**
     * @param actor  执行这条动作的实体
     * @param action 要做的事
     * @return 一条"正式行动"优先级的条目，行动者已指定
     */
    public static ActEntry of(LivingThing actor, ISpecialAction action) {
        return new ActEntry(action, PRIORITY_NORMAL, null, actor);
    }
}
