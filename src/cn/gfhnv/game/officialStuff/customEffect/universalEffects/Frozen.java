package cn.gfhnv.game.officialStuff.customEffect.universalEffects;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customAction.SkipTurn;
import cn.gfhnv.game.system.fight.ActionSignal;

public class Frozen extends Effect {
    public Frozen() {
        super("frozenEffect");
        this.setLastTime(1);
    }

    @Override
    public void comeIntoEffect(LivingThing thing) {
        thing.getController().setActionSignal(ActionSignal.SPECIAL_ACTION);
        thing.getController().setSpecialAction(new SkipTurn());
        System.out.println(thing.getName() + "冰冻中");
    }

    @Override
    public void whenLastTimeEnd(LivingThing livingThing) {
        livingThing.getController().setActionSignal(ActionSignal.NORMAL);
        livingThing.getController().setSpecialAction(null);
    }
}
