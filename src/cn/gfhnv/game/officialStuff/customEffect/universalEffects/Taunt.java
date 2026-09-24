package cn.gfhnv.game.officialStuff.customEffect.universalEffects;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.effect.EffectTags;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.interfaces.ITaunt;

/**
 * 嘲讽效果：持有期间会被对手「优先当成目标」。
 * <p>
 * 它<b>不改任何属性</b>，只是一个标记（实现 {@link ITaunt}）：
 * 目标策略 {@code TargetStrategies.tauntAware(...)} 会认出这个标记，
 * 把带嘲讽的目标排到候选列表最前面（等级越高越优先，同等级保持原顺序）。
 * 所以要让嘲讽生效，控制器得把目标策略设成 tauntAware 的组合
 * （{@code FixOrderController#setTargetStrategy}）。
 * <p>
 * 软嘲讽：只是「优先被打」，范围技能照样会打到别人；想要硬嘲讽（只能打它），
 * 自己实现一个 {@link cn.gfhnv.game.interfaces.TargetStrategy} 返回嘲讽目标即可。
 * <p>
 * 用法：{@code /effect @s add taunt}（1 回合）、{@code taunt(3)}（3 回合）、
 * {@code taunt(2,3)}（等级 2 = 更高优先级，3 回合）。
 *
 * @author AI（DeepSeek）生成
 */
public class Taunt extends Effect implements ITaunt {

    /**
     * 构造 1 回合、等级 1 的嘲讽。
     */
    public Taunt() {
        this(1, 1);
    }

    /**
     * 构造指定持续回合、等级 1 的嘲讽。
     *
     * @param lastTime 持续回合数
     */
    public Taunt(int lastTime) {
        this(1, lastTime);
    }

    /**
     * 构造嘲讽效果。
     *
     * @param level    嘲讽优先级（等级越高越优先被打）
     * @param lastTime 持续回合数
     */
    public Taunt(int level, int lastTime) {
        super("tauntEffect");
        this.getEffectTagsList().add(EffectTags.UNIVERSAL);
        this.getEffectTagsList().add(EffectTags.POSITIVE);
        this.setLevel(level);
        this.setLastTime(lastTime);
    }

    /**
     * 复制构造器。UNIVERSAL 标签要一起复制，否则副本会被当成「非通用效果」。
     *
     * @param other 被复制的嘲讽
     */
    public Taunt(Taunt other) {
        super(other.getID());
        this.getEffectTagsList().add(EffectTags.UNIVERSAL);
        this.getEffectTagsList().add(EffectTags.POSITIVE);
        this.setLevel(other.getLevel());
        this.setLastTime(other.getLastTime());
        this.setOrigin(other.getOrigin());
    }

    @Override
    public int getTauntLevel() {
        return getLevel();
    }

    /**
     * 嘲讽只是标记，没有「每回合做什么」。
     * <p>
     * 这里显式重写一个空实现：基类 {@link Effect#comeIntoEffect(LivingThing)} 是占位方法，
     * 会打印「这里写效果具体内容........请重写这个方法」，而标记型效果每回合刷这行没有意义。
     *
     * @param thing 持有者
     */
    @Override
    public void comeIntoEffect(LivingThing thing) {
        // 标记型效果：目标策略直接读 ITaunt，这里不需要做任何事
    }

    @Override
    public Effect copy() {
        return new Taunt(this);
    }
}
