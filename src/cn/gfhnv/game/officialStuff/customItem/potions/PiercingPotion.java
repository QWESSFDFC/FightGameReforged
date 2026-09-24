package cn.gfhnv.game.officialStuff.customItem.potions;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.IgnoreDefenceEffect;

/**
 * 穿甲药水：使用后 3 回合内无视目标 50% 防御。
 * <p>
 * {@code IgnoreDefenceEffect} 的百分比要靠 setter 设（它的构造器只设等级与持续回合），
 * 伤害计算时由 {@code DamageCalculate} 读取。
 *
 * @author AI（DeepSeek）生成
 */
public class PiercingPotion extends EffectPotion {

    /**
     * 构造穿甲药水。
     */
    public PiercingPotion() {
        super("穿甲药水", "使用后 3 回合内无视目标 50% 防御", "piercingPotion");
    }

    /**
     * 复制构造器（走 {@link Item#Item(Item)}，id 与堆叠数一起复制）。
     *
     * @param other 被复制的药水
     */
    public PiercingPotion(PiercingPotion other) {
        super(other);
    }

    @Override
    protected Effect createEffect() {
        IgnoreDefenceEffect effect = new IgnoreDefenceEffect(1, 3);
        effect.setPercent(0.5);
        return effect;
    }

    @Override
    public Item copy() {
        return new PiercingPotion(this);
    }
}
