package cn.gfhnv.game.event;

import cn.gfhnv.game.system.fight.Fight;

public class PhysicsStateUpdateEvent extends Event {
    private final Fight fight;

    public PhysicsStateUpdateEvent(Fight fight) {
        this.fight = fight;
    }

    public Fight getFight() {
        return fight;
    }
}
