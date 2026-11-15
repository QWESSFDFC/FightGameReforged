package cn.gfhnv.game.officialStuff.customItem.potions;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.CriticalDamageEnhanceEffect;

/**
 * 暴击伤害药水：使用后 3 回合内暴击伤害 +50%。
 *
 * @author AI（DeepSeek）生成
 */
public class CriticalDamagePotion extends EffectPotion {

    /**
     * 构造暴击伤害药水。
     */
    public CriticalDamagePotion() {
        super("暴击伤害药水", "使用后 3 回合内暴击伤害 +50%", "criticalDamagePotion");
    }

    /**
     * 复制构造器（走 {@link Item#Item(Item)}，id 与堆叠数一起复制）。
     *
     * @param other 被复制的药水
     */
    public CriticalDamagePotion(CriticalDamagePotion other) {
        super(other);
    }

    @Override
    protected Effect createEffect() {
        return new CriticalDamageEnhanceEffect(0.5, 3);
    }

    @Override
    public Item copy() {
        return new CriticalDamagePotion(this);
    }
}
