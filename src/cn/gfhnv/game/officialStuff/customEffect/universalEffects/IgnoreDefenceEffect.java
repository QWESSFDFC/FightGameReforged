package cn.gfhnv.game.officialStuff.customEffect.universalEffects;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.effect.EffectTags;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.interfaces.IDefenceIgnore;


/**
 * 无视防御效果：持有者攻击时会削减目标的有效防御。
 * <p>
 * 通过实现 {@link IDefenceIgnore} 参与伤害计算 —— 伤害计算只认那个接口，
 * 不认识本类，所以模组写自己的穿甲效果一样生效。
 */
public class IgnoreDefenceEffect extends Effect implements IDefenceIgnore {
    private double percent = 0;
    private long amount = 0;


    public IgnoreDefenceEffect(IgnoreDefenceEffect effect) {
        super(effect.getID());
        // 副本也是同一种效果，UNIVERSAL 标签要一起复制，否则副本 isUniversal() 会变成 false
        this.getEffectTagsList().add(EffectTags.UNIVERSAL);
        this.setLastTime(effect.getLastTime());
        this.setLevel(effect.getLevel());
        this.amount = effect.amount;
        this.getEffectTagsList().add(EffectTags.POSITIVE);
        this.percent = effect.percent;
    }

    public IgnoreDefenceEffect() {
        super("ignoreDefenceEffect");
        this.getEffectTagsList().add(EffectTags.UNIVERSAL);
        this.getEffectTagsList().add(EffectTags.POSITIVE);

    }

    /**
     * 构造一个指定等级与持续回合的忽略防御效果。
     *
     * @param level    效果等级
     * @param lastTime 持续回合数
     */
    public IgnoreDefenceEffect(int level, int lastTime) {
        super("ignoreDefenceEffect", level, lastTime);
        this.getEffectTagsList().add(EffectTags.UNIVERSAL);
        this.getEffectTagsList().add(EffectTags.POSITIVE);
    }

    public double getPercent() {
        return percent;
    }

    public void setPercent(double percent) {
        this.percent = percent;
    }

    @Override
    public double getIgnoreDefencePercent() {
        return percent;
    }

    @Override
    public long getIgnoreDefenceAmount() {
        return amount;
    }

    @Override
    public Effect copy() {
        return new IgnoreDefenceEffect(this);
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    @Override
    public void comeIntoEffect(LivingThing thing) {

    }

}