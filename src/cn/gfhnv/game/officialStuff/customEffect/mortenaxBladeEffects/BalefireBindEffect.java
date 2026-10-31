package cn.gfhnv.game.officialStuff.customEffect.mortenaxBladeEffects;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.effect.EffectTags;
import cn.gfhnv.game.entity.LivingThing;

/**
 * 千冶·刃 · 【煞火缠身】：防御力 -30%、受到的伤害 +50%（设计稿），持续 2 回合。
 * <p>
 * <b>两个数值怎么实现</b>：
 * <ul>
 *     <li><b>防御 -30%</b> —— 现成的 {@code defenceLoss}（百分比防御削减，{@code DamageCalculate} 读它打折防御），
 *     进来加、到期减；</li>
 *     <li><b>受到的伤害 +50%</b> —— 往 {@code damageReductions} 里加一个<b>负数</b>来源
 *     （{@code addDamageReduction(this, -0.5)}）。2026-10-24 起承伤倍率允许 &gt; 1，
 *     所以负减伤 = 增伤（见 {@code LivingThing#getDamageTakenMultiplier()} 的注释）。</li>
 * </ul>
 * 两个都"进来加、到期减"，形状与 {@code CriticalDMGEnhanceEffect} 一致。
 *
 * @author AI（DeepSeek）生成；设计稿作者：仓库作者
 */
public class BalefireBindEffect extends Effect {

    /**
     * 防御削减比例（设计：30%）。
     */
    public static final double DEFENCE_LOSS = 0.3;

    /**
     * 受到伤害的增加比例（设计：+50%；写进减伤表时用负数 {@code -0.5}）。
     */
    public static final double DAMAGE_TAKEN_INCREASE = 0.5;

    /**
     * 是否已经"加过"（防重复进出把数值加两遍）。
     */
    private boolean isOn = false;

    /**
     * @param lastTime 持续回合数（设计：2）
     */
    public BalefireBindEffect(int lastTime) {
        super("balefireBindEffect");
        this.getEffectTagsList().add(EffectTags.UNIVERSAL);
        // 对持有者不利 → 负面；能量攒满时会被「百炼骨」清掉（千冶·刃自己的不好说，但这确实是负面效果）
        this.getEffectTagsList().add(EffectTags.NEGATIVE);
        this.setName("煞火缠身");
        this.setLastTime(lastTime);
    }

    /**
     * 复制构造器。
     *
     * @param other 被复制的效果
     */
    public BalefireBindEffect(BalefireBindEffect other) {
        super(other.getID());
        this.getEffectTagsList().add(EffectTags.UNIVERSAL);
        this.getEffectTagsList().add(EffectTags.NEGATIVE);
        this.setName("煞火缠身");
        this.setLastTime(other.getLastTime());
        this.setLevel(other.getLevel());
        this.setOrigin(other.getOrigin());
        this.isOn = other.isOn;
    }

    @Override
    public Effect copy() {
        return new BalefireBindEffect(this);
    }

    @Override
    public void comeIntoEffect(LivingThing thing) {
        if (!isOn) {
            thing.setDefenseLoss(thing.getDefenseLoss() + DEFENCE_LOSS);
            // 负数 = 增伤：受到伤害 ×(1 + 0.5)
            thing.addDamageReduction(this, -DAMAGE_TAKEN_INCREASE);
        }
        isOn = true;
    }

    @Override
    public void whenLastTimeEnd(LivingThing thing) {
        thing.setDefenseLoss(thing.getDefenseLoss() - DEFENCE_LOSS);
        thing.removeDamageReduction(this);
        isOn = false;
    }
}
