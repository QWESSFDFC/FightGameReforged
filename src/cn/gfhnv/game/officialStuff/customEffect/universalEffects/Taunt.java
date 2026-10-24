package cn.gfhnv.game.officialStuff.customEffect.universalEffects;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.effect.EffectTags;
import cn.gfhnv.game.entity.LivingThing;

/**
 * <b>嘲讽</b>：让<b>被嘲讽者</b>在挑目标时优先攻击<b>施加者</b>。
 * <p>
 * <b>语义（用户 2026-10-04 定的）</b>：A、B 互为敌人，A 给 B 施加嘲讽 —— 之后
 * <b>B 释放攻击时优先攻击 A</b>，而且目标列表里<b>一定有 A</b>（对玩家、对 AI 都一样）。
 * 所以效果挂在 <b>B（被打的那个）</b>身上，而 {@link #getOrigin() origin} 记的是
 * <b>A（施加者）的 uuid</b> —— 判断"谁嘲讽了我"就看 origin。
 * <p>
 * ⚠️ <b>它不是"给持有者加优先级"</b>：持有者恰恰是被牵着走的那一方。
 * 旧实现把方向搞反了（查"目标身上有没有嘲讽"），于是嘲讽从来没生效过，
 * 这里连同 {@code ITaunt} 接口一起重做了。
 * <p>
 * <b>它算负面效果</b>（带 {@link EffectTags#NEGATIVE}）—— 被人牵着鼻子走对持有者是不利的。
 * ⚠️ 副作用：白厄变身期间 {@code updateSelf} 会清掉身上所有负面效果，所以
 * <b>变身中的白厄不会被嘲讽</b>。这是刻意的（变身就是"不受控"），不是 bug。
 * <p>
 * 它<b>不改任何属性</b>，纯粹是个标记；真正的排序在
 * {@code cn.gfhnv.game.system.fight.TargetStrategies}（默认策略已经自带嘲讽感知）。
 * <p>
 * 用法：{@code /effect @s add taunt}（1 回合）、{@code taunt(3)}（3 回合）、
 * {@code taunt(2,3)}（等级 2 = 更高优先级，3 回合）。
 *
 * @author AI（DeepSeek）生成
 */
public class Taunt extends Effect {

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
     * @param level    嘲讽优先级（等级越高越优先被选中）
     * @param lastTime 持续回合数
     */
    public Taunt(int level, int lastTime) {
        super("tauntEffect");
        this.getEffectTagsList().add(EffectTags.UNIVERSAL);
        // 嘲讽是**负面**效果：持有者失去了"想打谁打谁"的自由。
        this.getEffectTagsList().add(EffectTags.NEGATIVE);
        this.setLevel(level);
        this.setLastTime(lastTime);
    }

    /**
     * 复制构造器。标签、等级、剩余回合、<b>来源</b>都要带过去 ——
     * 来源尤其要紧：嘲讽靠 {@link #getOrigin()} 认"是谁在嘲讽我"，
     * 副本丢了 origin 就再也指不回施加者了。
     *
     * @param other 被复制的嘲讽
     */
    public Taunt(Taunt other) {
        super(other.getID());
        this.getEffectTagsList().add(EffectTags.UNIVERSAL);
        this.getEffectTagsList().add(EffectTags.NEGATIVE);
        this.setLevel(other.getLevel());
        this.setLastTime(other.getLastTime());
        this.setOrigin(other.getOrigin());
    }

    /**
     * 嘲讽只是标记，没有「每回合做什么」。
     * <p>
     * 这里显式重写一个空实现：基类 {@link Effect#comeIntoEffect(LivingThing)}
     * 是占位方法，会打印「这里写效果具体内容........请重写这个方法」，
     * 而标记型效果每回合刷这行没有意义。
     *
     * @param thing 持有者（被嘲讽的那个）
     */
    @Override
    public void comeIntoEffect(LivingThing thing) {
        // 标记型效果：排序由 TargetStrategies 读 origin 完成，这里不需要做任何事
    }

    @Override
    public Effect copy() {
        return new Taunt(this);
    }
}
