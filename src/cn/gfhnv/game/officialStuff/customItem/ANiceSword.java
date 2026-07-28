package cn.gfhnv.game.officialStuff.customItem;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.DamageEnhanceEffect;
import cn.gfhnv.game.system.fight.Fight;

public class ANiceSword extends Item {
    public ANiceSword() {
        super("一把剑", "使用后增加伤害1回合", "aNiceSword");
    }

    @Override
    public void comeToEffect(LivingThing user, Fight fight) {
        user.addEffect(new DamageEnhanceEffect(2, 1));
    }
}
