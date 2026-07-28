package cn.gfhnv.game.interfaces;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.system.fight.Fight;

public interface ISpecialAction {
    void execute(Fight fight, LivingThing user);
}
