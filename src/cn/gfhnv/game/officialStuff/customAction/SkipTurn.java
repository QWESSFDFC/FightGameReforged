package cn.gfhnv.game.officialStuff.customAction;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.interfaces.ISpecialAction;
import cn.gfhnv.game.system.fight.Fight;

public class SkipTurn implements ISpecialAction {
    @Override
    public void execute(Fight fight, LivingThing user) {
        // 原来用的 printf + 字符串拼接:名字里含 '%' 会抛 UnknownFormatConversionException,且不会换行
        System.out.println(user.getName() + "跳过回合");
    }
}
