package cn.gfhnv.game.officialStuff.customEffect.universalEffects;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.effect.EffectTags;
import cn.gfhnv.game.entity.LivingThing;

public class DefenseEnhanceEffect extends Effect {
    private double percent = 0;
    private long amount = 0;
    private boolean isOn = false;

    public DefenseEnhanceEffect(DefenseEnhanceEffect effect) {
        super(effect.getID());
        // 副本也是同一种效果，UNIVERSAL 标签要一起复制，否则副本 isUniversal() 会变成 false
        this.getEffectTagsList().add(EffectTags.UNIVERSAL);
        this.setLastTime(effect.getLastTime());
        this.setLevel(effect.getLevel());
        this.amount = effect.amount;
        this.getEffectTagsList().add(EffectTags.POSITIVE);
        this.percent = effect.percent;
        this.isOn = effect.isOn;
    }

    public DefenseEnhanceEffect() {
        super("defenseEnhanceEffect");
        this.getEffectTagsList().add(EffectTags.UNIVERSAL);
        this.getEffectTagsList().add(EffectTags.POSITIVE);
        this.setLastTime(1);
    }

    public DefenseEnhanceEffect(String id, int level, int lastTime) {
        super(id, level, lastTime);
        // 与其它「新建实例」构造器保持一致：本类效果是通用的，标签要跟上
        this.getEffectTagsList().add(EffectTags.UNIVERSAL);
        this.getEffectTagsList().add(EffectTags.POSITIVE);

    }

    /**
     * 构造一个指定等级与持续回合的防御增强效果（数量与百分比由 setter 另行设置）。
     *
     * @param level    效果等级
     * @param lastTime 持续回合数
     */
    public DefenseEnhanceEffect(int level, int lastTime) {
        super("defenseEnhanceEffect", level, lastTime);
        this.getEffectTagsList().add(EffectTags.UNIVERSAL);
        this.getEffectTagsList().add(EffectTags.POSITIVE);
    }

    @Override
    public Effect copy() {
        return new DefenseEnhanceEffect(this);
    }

    @Override
    public void comeIntoEffect(LivingThing thing) {
        if (!isOn) {
            thing.setDefenceEnhanceAmount(thing.getDefenceEnhanceAmount() + amount);
            thing.setDefenceEnhancePercent(thing.getDefenceEnhancePercent() + percent);
        }
        isOn = true;
    }

    @Override
    public void whenLastTimeEnd(LivingThing thing) {
        thing.setDefenceEnhanceAmount(thing.getDefenceEnhanceAmount() - amount);
        thing.setDefenceEnhancePercent(thing.getDefenceEnhancePercent() - percent);
        isOn = false;
    }

}