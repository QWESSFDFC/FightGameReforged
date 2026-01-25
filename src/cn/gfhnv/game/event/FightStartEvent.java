package cn.gfhnv.game.event;

import cn.gfhnv.game.entity.Entity;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.system.fight.Fight;

import java.util.List;

public class FightStartEvent extends Event {
    private final Fight fight;
    public FightStartEvent(Fight fight) {
        this.fight = fight;
    }

    public Fight getFight() {
        return fight;
    }

}
