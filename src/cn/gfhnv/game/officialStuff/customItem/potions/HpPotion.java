package cn.gfhnv.game.officialStuff.customItem.potions;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.HpEnhanceEffect;

/**
 * 生命药水：使用后 3 回合内生命上限 +20%。
 *
 * @author AI（DeepSeek）生成
 */
public class HpPotion extends EffectPotion {

    /**
     * 构造生命药水。
     */
    public HpPotion() {
        super("生命药水", "使用后 3 回合内生命上限 +20%", "hpPotion");
    }

    /**
     * 复制构造器（走 {@link Item#Item(Item)}，id 与堆叠数一起复制）。
     *
     * @param other 被复制的药水
     */
    public HpPotion(HpPotion other) {
        super(other);
    }

    @Override
    protected Effect createEffect() {
        return new HpEnhanceEffect(0.2, 3);
    }

    @Override
    public Item copy() {
        return new HpPotion(this);
    }
}
