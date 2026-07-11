package cn.gfhnv.debug_tools.actionBarTest.eventsAndListeners;

import cn.gfhnv.game.event.Event;
import cn.gfhnv.game.system.fight.Fight;

public class TurnPastEvent extends Event {
    private final Fight fight;

    public TurnPastEvent(Fight fight) {
        this.fight = fight;

    }


    public Fight getFight() {
        return fight;
    }
}
