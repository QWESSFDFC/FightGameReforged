package cn.gfhnv.game.officialStuff.customItem.potions;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.AttackEnhance;

/**
 * 攻击药水：使用后 3 回合内攻击 +20%。
 *
 * @author AI（DeepSeek）生成
 */
public class AttackPotion extends EffectPotion {

    /**
     * 构造攻击药水。
     */
    public AttackPotion() {
        super("攻击药水", "使用后 3 回合内攻击 +20%", "attackPotion");
    }

    /**
     * 复制构造器（走 {@link Item#Item(Item)}，id 与堆叠数一起复制）。
     *
     * @param other 被复制的药水
     */
    public AttackPotion(AttackPotion other) {
        super(other);
    }

    @Override
    protected Effect createEffect() {
        return new AttackEnhance(0.2, 3);
    }

    @Override
    public Item copy() {
        return new AttackPotion(this);
    }
}
