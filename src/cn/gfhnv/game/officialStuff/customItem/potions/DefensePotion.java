package cn.gfhnv.game.officialStuff.customItem.potions;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.DefenseEnhanceEffect;

/**
 * 防御药水：使用后 3 回合内防御 +30%。
 *
 * @author AI（DeepSeek）生成
 */
public class DefensePotion extends EffectPotion {

    /**
     * 构造防御药水。
     */
    public DefensePotion() {
        super("防御药水", "使用后 3 回合内防御 +30%", "defensePotion");
    }

    /**
     * 复制构造器（走 {@link Item#Item(Item)}，id 与堆叠数一起复制）。
     *
     * @param other 被复制的药水
     */
    public DefensePotion(DefensePotion other) {
        super(other);
    }

    @Override
    protected Effect createEffect() {
        return new DefenseEnhanceEffect(0.3, 0, 3);
    }

    @Override
    public Item copy() {
        return new DefensePotion(this);
    }
}
