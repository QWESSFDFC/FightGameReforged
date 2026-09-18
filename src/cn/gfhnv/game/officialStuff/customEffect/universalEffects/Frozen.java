package cn.gfhnv.game.officialStuff.customEffect.universalEffects;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customAction.SkipTurn;
import cn.gfhnv.game.system.fight.ActionSignal;
import cn.gfhnv.game.system.fight.TurnEntry;
import cn.gfhnv.game.system.fight.TurnManager;

/**
 * 冰冻效果。持有期间该生物无法行动（轮到它时跳过该回合）。
 * <p>
 * 实现要点（顺序很关键，改动前请先读这里）：
 * <ol>
 *     <li><b>在 {@link #initialEffect} 里布置跳过，而不是在 {@link #comeIntoEffect} 里。</b>
 *     {@code addEffect()} 会立刻调用 {@code initialEffect()}，而 {@code comeIntoEffect()}
 *     要等到"受害者自己的回合结束"才被 {@code EffectUpdateEvent} 触发 —— 那时受害者
 *     已经白拿了一次行动。</li>
 *     <li><b>真正决定"这一回合做什么"的是待执行的那条 {@link TurnEntry} 的 actionSignal，
 *     不是 controller 的 actionSignal。</b>{@link TurnEntry} 在创建时就把 controller 的信号
 *     快照下来了，所以只改 controller 对已经排好的回合没有影响。</li>
 *     <li><b>{@link #whenLastTimeEnd} 不要清 specialAction。</b>本效果到期的那一刻，
 *     "被标成 SPECIAL_ACTION 但还没执行"的回合条目可能已经在队列里了；把 specialAction
 *     置 null 会让 {@code FightTurnPastListener} 执行时拿到 null 而抛 NPE。</li>
 * </ol>
 *
 * @author gfhnv
 */
public class Frozen extends Effect {
    public Frozen() {
        super("frozenEffect");
        this.setLastTime(1);
        this.setNegative(true);
    }

    public Frozen(int lastTime) {
        super("frozenEffect");
        this.setLastTime(lastTime);
        this.setNegative(true);
    }

    public Frozen(Frozen effect) {
        super(effect.getID());
        this.setLastTime(effect.getLastTime());
        this.setLevel(effect.getLevel());
        this.setNegative(true);
    }

    @Override
    public Effect copy() {
        return new Frozen(this);
    }

    /**
     * 获得效果的那一刻就布置好"跳过"。必须在这里做，不能只靠 {@link #comeIntoEffect}。
     *
     * @param thing 获得冰冻的生物
     */
    @Override
    public void initialEffect(LivingThing thing) {
        applyFrozen(thing);
        System.out.println(thing.getName() + "被冰冻");
    }

    /**
     * 该生物每个回合结束时重新确认一次"下一步要跳过"。
     * <p>
     * 这样即使信号中途被别的效果或事件改写（例如 {@code whenLastTimeEnd} 把 controller
     * 复位成 NORMAL），轮到它时依然会被跳过。{@code lastTime} 也就是"会被跳过几次自己的回合"。
     *
     * @param thing 持有冰冻的生物
     */
    @Override
    public void comeIntoEffect(LivingThing thing) {
        applyFrozen(thing);
        System.out.println(thing.getName() + "冰冻中");
    }

    /**
     * 布置跳过动作：把该生物下一条待执行的回合条目标记为 {@link ActionSignal#SPECIAL_ACTION}，
     * 并给它的控制器挂上 {@link SkipTurn} 作为要执行的动作。
     *
     * @param thing 被冰冻的生物
     */
    private void applyFrozen(LivingThing thing) {
        // getNextTurnOf 找不到时会返回 null（例如该生物已不在时间轴上），必须判空
        TurnEntry nextTurn = TurnManager.getNextTurnOf(thing);
        if (nextTurn != null) {
            nextTurn.setActionSignal(ActionSignal.SPECIAL_ACTION);
        }
        if (thing.getController() != null) {
            thing.getController().setSpecialAction(new SkipTurn());
        }
    }

    /**
     * 冰冻结束：只把控制器的行动信号复位。
     * <p>
     * <b>故意不清 {@code specialAction}</b> —— 清了会让"已被标记为 SPECIAL_ACTION、
     * 但还没执行"的回合条目在 {@code FightTurnPastListener} 里取到 null 而 NPE。
     * {@link SkipTurn} 不持有任何状态，留着无害；下次再被冰冻会直接覆盖成新的实例。
     *
     * @param livingThing 冰冻结束的生物
     */
    @Override
    public void whenLastTimeEnd(LivingThing livingThing) {
        if (livingThing.getController() != null) {
            livingThing.getController().setActionSignal(ActionSignal.NORMAL);
        }
    }
}
