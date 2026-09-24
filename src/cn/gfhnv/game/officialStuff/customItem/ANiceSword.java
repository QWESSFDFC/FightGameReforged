package cn.gfhnv.game.officialStuff.customItem;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.DamageEnhanceEffect;
import cn.gfhnv.game.system.fight.Fight;

public class ANiceSword extends Item {
    public ANiceSword() {
        super("一把剑", "使用后增加伤害1回合", "aNiceSword");
    }

    /**
     * 复制构造器。
     * <p>
     * 走 {@link Item#Item(Item)} 那条路（它会复制 id），这样从注册表模板
     * {@code copy()} 出来的实例仍然带完整的 {@code game_official_content:aNiceSword}，
     * 而不是回到构造器里写死的短名。
     *
     * @param other 被复制的物品
     */
    public ANiceSword(ANiceSword other) {
        super(other);
    }

    @Override
    public void comeToEffect(LivingThing user, Fight fight) {
        user.addEffect(new DamageEnhanceEffect(2, 1).setOrigin(this.getId()));
    }

    @Override
    public Item copy() {
        return new ANiceSword(this);
    }
}
