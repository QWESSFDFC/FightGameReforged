package cn.gfhnv.game.officialStuff.customAction;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.interfaces.ISpecialAction;
import cn.gfhnv.game.system.fight.Fight;

public class SkipTurn implements ISpecialAction {
    @Override
    public void execute(Fight fight, LivingThing user) {
        System.out.printf(user.getName() + "跳过回合");
    }
}
