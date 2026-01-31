package cn.gfhnv.game.event;

import cn.gfhnv.game.system.fight.Fight;

public class FightPastOneTurnEvent extends Event {
    private Fight fight;

    public FightPastOneTurnEvent(Fight fight) {
        this.fight = fight;
    }

    public Fight getFight() {
        return fight;
    }

    public void setFight(Fight fight) {
        this.fight = fight;
    }
}
