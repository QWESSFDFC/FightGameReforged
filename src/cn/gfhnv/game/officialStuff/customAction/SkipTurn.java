package cn.gfhnv.game.officialStuff.customAction;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.interfaces.ISpecialAction;
import cn.gfhnv.game.system.fight.Fight;

public class SkipTurn implements ISpecialAction {
    @Override
    public void execute(Fight fight, LivingThing user) {
        // 用 println 而不是 printf + 拼接：名字里可能含 '%'，会被当成格式符
        System.out.println(user.getName() + "跳过回合");
    }
}
