package cn.gfhnv.game.event;

import cn.gfhnv.game.entity.LivingThing;

import java.util.List;

public class SelectTargetEvent extends Event {
    private final LivingThing selector;
    private final List<LivingThing> targets;

    public SelectTargetEvent(LivingThing selector, List<LivingThing> targets) {
        this.selector = selector;
        this.targets = targets;
    }

    public LivingThing getSelector() {
        return selector;
    }

    public List<LivingThing> getTargets() {
        return targets;
    }
}
