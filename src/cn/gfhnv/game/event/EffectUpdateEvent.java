package cn.gfhnv.game.event;

import cn.gfhnv.game.entity.LivingThing;

public class EffectUpdateEvent extends Event {
    private final LivingThing updatedThing;
    public EffectUpdateEvent(LivingThing updatedThing) {this.updatedThing = updatedThing;}
    public LivingThing getUpdatedThing() {return updatedThing;}
}
