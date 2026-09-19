package cn.gfhnv.game.officialStuff.customEffect.universalEffects;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.effect.EffectTags;
import cn.gfhnv.game.entity.LivingThing;

public class HpEnhanceEffect extends Effect {
    private boolean isOn = false;
    private double percent = 0;
    private long amount = 0;


    /**
     * 构造一个生命上限增强效果：百分比与固定值都在这一个构造函数里。
     * <p>
     * 两个数值放在同一个函数里，同一个数字不会有第二种解释 ——
     * 写满 3 个参数时位置是固定的（百分比、固定值、持续回合）：
     * <pre>
     * new HpEnhanceEffect(0.2, 0, 3)   3 回合内生命上限 +20%
     * new HpEnhanceEffect(0, 100, 3)   3 回合内生命上限 +100 点
     * </pre>
     *
     * @param percent  生命上限增强百分比（0.2 表示 +20%）
     * @param amount   生命上限增强固定值（+N 点）
     * @param lastTime 持续回合数
     */
    public HpEnhanceEffect(double percent, long amount, int lastTime) {
        super("hpEnhanceEffect");
        this.getEffectTagsList().add(EffectTags.UNIVERSAL);
        this.getEffectTagsList().add(EffectTags.POSITIVE);
        this.percent = percent;
        this.amount = amount;
        this.setLastTime(lastTime);
    }

    /**
     * 只加百分比的简写：{@code new HpEnhanceEffect(0.2, 3)} = 3 回合内生命上限 +20%。
     * <p>
     * 它是 2 个参数、上面那个是 3 个参数，两者永远不会互相匹配。
     *
     * @param percent  百分比（0.2 表示 +20%）
     * @param lastTime 持续回合数
     */
    public HpEnhanceEffect(double percent, int lastTime) {
        this(percent, 0, lastTime);
    }

    public HpEnhanceEffect(HpEnhanceEffect enhance) {
        super(enhance.getID());
        // 副本也是同一种效果，UNIVERSAL 标签要一起复制，否则副本 isUniversal() 会变成 false
        this.getEffectTagsList().add(EffectTags.UNIVERSAL);
        this.setLastTime(enhance.getLastTime());
        this.setLevel(enhance.getLevel());
        this.setOrigin(enhance.getOrigin());
        this.amount = enhance.amount;
        this.percent = enhance.percent;
        this.isOn = enhance.isOn;
        this.getEffectTagsList().add(EffectTags.POSITIVE);
    }

    @Override
    public Effect copy() {
        return new HpEnhanceEffect(this);
    }

    @Override
    public void comeIntoEffect(LivingThing thing) {
        thing.renewHp();
        if (!isOn) {
            thing.setHpEnhanceAmount(thing.getHpEnhanceAmount() + amount);
            thing.setHpEnhancePercent(thing.getHpEnhancePercent() + percent);
        }
        isOn = true;
    }

    @Override
    public void whenLastTimeEnd(LivingThing thing) {
        thing.renewHp();
        thing.setHpEnhanceAmount(thing.getHpEnhanceAmount() - amount);
        thing.setHpEnhancePercent(thing.getHpEnhancePercent() - percent);
        isOn = false;
    }

}