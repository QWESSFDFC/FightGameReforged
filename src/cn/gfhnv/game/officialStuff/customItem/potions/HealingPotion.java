package cn.gfhnv.game.officialStuff.customItem.potions;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.HealthRestoreEffect;

/**
 * 治疗药水：使用后立刻回复生命（{@code HealthRestoreEffect(2, 1)} = 210 点，并在该回合结束时再回一次）。
 *
 * @author AI（DeepSeek）生成
 */
public class HealingPotion extends EffectPotion {

    /**
     * 构造治疗药水。
     */
    public HealingPotion() {
        super("治疗药水", "使用后立刻回复 210 点生命", "healingPotion");
    }

    /**
     * 复制构造器（走 {@link Item#Item(Item)}，id 与堆叠数一起复制）。
     *
     * @param other 被复制的药水
     */
    public HealingPotion(HealingPotion other) {
        super(other);
    }

    @Override
    protected Effect createEffect() {
        return new HealthRestoreEffect(2, 1);
    }

    @Override
    public Item copy() {
        return new HealingPotion(this);
    }
}
