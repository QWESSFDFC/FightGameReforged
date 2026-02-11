package cn.gfhnv.game.event;

import cn.gfhnv.game.system.fight.Fight;

public class FightStartEvent extends Event {
    private final Fight fight;

    public FightStartEvent(Fight fight) {
        this.fight = fight;
    }

    public Fight getFight() {
        return fight;
    }

}
