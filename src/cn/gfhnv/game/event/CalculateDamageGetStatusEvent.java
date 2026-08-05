package cn.gfhnv.game.event;

import cn.gfhnv.game.entity.LivingThing;

public class CalculateDamageGetStatusEvent extends Event {
    private final LivingThing attacker;
    private final LivingThing target;

    public CalculateDamageGetStatusEvent(LivingThing attacker, LivingThing target) {
        this.attacker = attacker;
        this.target = target;
    }

    public LivingThing getAttacker() {
        return attacker;
    }

    public LivingThing getTarget() {
        return target;
    }
}
