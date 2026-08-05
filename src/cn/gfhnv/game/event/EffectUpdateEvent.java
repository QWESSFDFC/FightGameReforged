package cn.gfhnv.game.event;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.system.fight.TurnEntry;

public class EffectUpdateEvent extends Event {
    private final LivingThing updatedThing;
    private final TurnEntry turnEntry;

    public EffectUpdateEvent(LivingThing updatedThing, TurnEntry turnEntry) {
        this.updatedThing = updatedThing;
        this.turnEntry = turnEntry;
    }

    public TurnEntry getTurnEntry() {
        return turnEntry;
    }

    public LivingThing getUpdatedThing() {
        return updatedThing;
    }
}
