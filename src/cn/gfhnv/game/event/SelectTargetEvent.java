package cn.gfhnv.game.event;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.system.fight.Fight;

import java.util.List;

public class SelectTargetEvent extends Event {
    private final LivingThing selector;
    private final List<LivingThing> targets;
    private final Fight fight;

    public SelectTargetEvent(LivingThing selector, List<LivingThing> targets, Fight fight) {
        this.selector = selector;
        this.targets = targets;
        this.fight = fight;
    }

    public LivingThing getSelector() {
        return selector;
    }

    public Fight getFight() {
        return fight;
    }

    public List<LivingThing> getTargets() {
        return targets;
    }
}
