package cn.gfhnv.game.officialStuff.customItem.potions;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.SpeedEnhanceEffect;

/**
 * 迅捷药水：使用后 3 回合内速度 +20%（速度决定时间轴上的出手频率）。
 *
 * @author AI（DeepSeek）生成
 */
public class SpeedPotion extends EffectPotion {

    /**
     * 构造迅捷药水。
     */
    public SpeedPotion() {
        super("迅捷药水", "使用后 3 回合内速度 +20%", "speedPotion");
    }

    /**
     * 复制构造器（走 {@link Item#Item(Item)}，id 与堆叠数一起复制）。
     *
     * @param other 被复制的药水
     */
    public SpeedPotion(SpeedPotion other) {
        super(other);
    }

    @Override
    protected Effect createEffect() {
        return new SpeedEnhanceEffect(0.2, 3);
    }

    @Override
    public Item copy() {
        return new SpeedPotion(this);
    }
}
