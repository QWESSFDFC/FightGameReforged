package cn.gfhnv.game.officialStuff.customEffect.mortenaxBladeEffects;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.effect.EffectTags;
import cn.gfhnv.game.entity.LivingThing;

/**
 * 千冶·刃 · 【万淬心】给队友的增伤：结界期间我方目标造成的伤害提高 50%。
 * <p>
 * <b>为什么不直接用 {@code DamageEnhanceEffect}</b>（2026-10-24 用户日志反馈）：
 * 那个效果在"有显示名"时会播报 `增伤 +50%（2147483647 回合）` —— 因为结界期间用的是
 * {@code Integer.MAX_VALUE} 表示"不靠回合数结束"。数字很难看，也不该让玩家看见。
 * 专用类只做"进来加 {@code enhance}、到期减回去"，不打印、不涉及回合数语义。
 * <p>
 * 形状照抄 {@code DamageEnhanceEffect} / {@code CriticalDMGEnhanceEffect}（{@code isOn} 防重复进出）。
 *
 * @author AI（DeepSeek）生成；设计稿作者：仓库作者
 */
public class MortalBladeAllyEnhanceEffect extends Effect {

    /**
     * 增伤比例（设计：我方目标造成的伤害提高 50%）。
     */
    public static final double ENHANCE = 0.5;

    /**
     * 是否已经加过（防重复进出把数值加两遍）。
     */
    private boolean isOn;

    public MortalBladeAllyEnhanceEffect() {
        super("mortenaxBladeAllyEnhance");
        this.getEffectTagsList().add(EffectTags.UNIVERSAL);
        this.getEffectTagsList().add(EffectTags.POSITIVE);
        this.setName("万淬心");
        // 结界期间一直有效：结界的结束由倒计时/免死负责，这里不靠回合数
        this.setLastTime(Integer.MAX_VALUE);
    }

    /**
     * 复制构造器。
     *
     * @param other 被复制的效果
     */
    public MortalBladeAllyEnhanceEffect(MortalBladeAllyEnhanceEffect other) {
        super(other.getID());
        this.getEffectTagsList().add(EffectTags.UNIVERSAL);
        this.getEffectTagsList().add(EffectTags.POSITIVE);
        this.setName("万淬心");
        this.setLastTime(other.getLastTime());
        this.setLevel(other.getLevel());
        this.setOrigin(other.getOrigin());
        this.isOn = other.isOn;
    }

    @Override
    public Effect copy() {
        return new MortalBladeAllyEnhanceEffect(this);
    }

    @Override
    public void comeIntoEffect(LivingThing thing) {
        if (!isOn) {
            thing.setEnhance(thing.getEnhance() + ENHANCE);
        }
        isOn = true;
    }

    @Override
    public void whenLastTimeEnd(LivingThing thing) {
        thing.setEnhance(thing.getEnhance() - ENHANCE);
        isOn = false;
    }
}
