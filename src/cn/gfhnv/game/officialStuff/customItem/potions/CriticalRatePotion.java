package cn.gfhnv.game.officialStuff.customItem.potions;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.CriticalRateEnhanceEffect;

/**
 * 暴击药水：使用后 3 回合内暴击率 +20%。
 *
 * @author AI（DeepSeek）生成
 */
public class CriticalRatePotion extends EffectPotion {

    /**
     * 构造暴击药水。
     */
    public CriticalRatePotion() {
        super("暴击药水", "使用后 3 回合内暴击率 +20%", "criticalRatePotion");
    }

    /**
     * 复制构造器（走 {@link Item#Item(Item)}，id 与堆叠数一起复制）。
     *
     * @param other 被复制的药水
     */
    public CriticalRatePotion(CriticalRatePotion other) {
        super(other);
    }

    @Override
    protected Effect createEffect() {
        return new CriticalRateEnhanceEffect(0.2, 3);
    }

    @Override
    public Item copy() {
        return new CriticalRatePotion(this);
    }
}
