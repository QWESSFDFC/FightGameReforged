package cn.gfhnv.game.officialStuff.customEntity.players;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entity.Player;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;

public class MortenaxBlade extends Player {
    //还没有写完,准备整理文档后让ai写
    public MortenaxBlade(long l) {
        super("千冶•刃", "mortenax_blade", 0.7, 0, 0, 0, 0, 125, l, "player", 60, 12, 6, ElementSort.FIRE);
        this.setDescription("这是千冶•刃");

    }

    public MortenaxBlade(MortenaxBlade mortenaxBlade) {
        super(mortenaxBlade);
    }

    @Override
    public LivingThing copy() {
        return new MortenaxBlade(this);
    }


    @Override
    public void whenFightEnds() {
        super.whenFightEnds();
    }

    @Override
    public void whenFightStart(Fight fight) {
        super.whenFightStart(fight);
    }
}
