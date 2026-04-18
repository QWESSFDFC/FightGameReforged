package cn.gfhnv.game.event;

import cn.gfhnv.game.entity.LivingThing;

public class HpRestorationEvent extends Event {
    private long restorationScale;
    private LivingThing livingThing;

    public HpRestorationEvent(long restorationScale, LivingThing livingThing) {
        this.restorationScale = restorationScale;
        this.livingThing = livingThing;
    }

    public long getRestorationScale() {
        return restorationScale;
    }

    public LivingThing getLivingThing() {
        return livingThing;
    }
}
