package cn.gfhnv.game.officialStuff.customEffect.universalEffects;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.effect.EffectTags;
import cn.gfhnv.game.entity.LivingThing;

public class AttackEnhance extends Effect {
    private boolean isOn = false;
    private double percent = 0;
    private long amount = 0;


    /**
     * 构造一个攻击增强效果：百分比与固定值都在这一个构造函数里。
     * <p>
     * 两个数值放在同一个函数里，同一个数字不会有第二种解释 ——
     * 写满 3 个参数时位置是固定的（百分比、固定值、持续回合）：
     * <pre>
     * new AttackEnhance(0.2, 0, 3)   3 回合内攻击 +20%
     * new AttackEnhance(0, 2, 3)     3 回合内攻击 +2 点
     * new AttackEnhance(0.5, 3, 3)   3 回合内攻击 +50% 且 +3 点
     * </pre>
     *
     * @param percent  攻击增强百分比（0.2 表示 +20%）
     * @param amount   攻击增强固定值（+N 点）
     * @param lastTime 持续回合数
     */
    public AttackEnhance(double percent, long amount, int lastTime) {
        super("attackEnhanceEffect");
        this.getEffectTagsList().add(EffectTags.UNIVERSAL);
        this.getEffectTagsList().add(EffectTags.POSITIVE);
        this.percent = percent;
        this.amount = amount;
        this.setLastTime(lastTime);
    }

    /**
     * 只加百分比的简写：{@code new AttackEnhance(0.2, 3)} = 3 回合内攻击 +20%。
     * <p>
     * 它是 2 个参数、上面那个是 3 个参数，两者永远不会互相匹配。
     *
     * @param percent 百分比（0.2 表示 +20%）
     * @param latTime 持续回合数
     */
    public AttackEnhance(double percent, int latTime) {
        this(percent, 0, latTime);
    }

    public AttackEnhance(AttackEnhance attackEnhance) {
        super(attackEnhance.getID());
        // 副本也是同一种效果，UNIVERSAL 标签要一起复制，否则副本 isUniversal() 会变成 false
        this.getEffectTagsList().add(EffectTags.UNIVERSAL);
        this.setLastTime(attackEnhance.getLastTime());
        this.setLevel(attackEnhance.getLevel());
        this.getEffectTagsList().add(EffectTags.POSITIVE);
        this.setOrigin(attackEnhance.getOrigin());
        this.amount = attackEnhance.amount;
        this.percent = attackEnhance.percent;
        this.isOn = attackEnhance.isOn;

    }

    @Override
    public void comeIntoEffect(LivingThing thing) {
        if (!isOn) {
            thing.setAttackEnhanceAmount(thing.getAttackEnhanceAmount() + amount);
            thing.setAttackEnhancePercent(thing.getAttackEnhancePercent() + percent);
        }
        isOn=true;
    }

    @Override
    public void whenLastTimeEnd(LivingThing thing) {
        isOn = false;
        thing.setAttackEnhanceAmount(thing.getAttackEnhanceAmount() - amount);
        thing.setAttackEnhancePercent(thing.getAttackEnhancePercent() - percent);
    }

    @Override
    public Effect copy() {
        return new AttackEnhance(this);
    }

}