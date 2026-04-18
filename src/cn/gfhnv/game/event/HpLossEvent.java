package cn.gfhnv.game.event;

import cn.gfhnv.game.entity.LivingThing;

public class HpLossEvent extends  Event {
    private long lostScale;
    private LivingThing livingThing;
    public HpLossEvent(long lostScale,LivingThing livingThing) {lostScale = lostScale;livingThing = livingThing;}

    public LivingThing getLivingThing() {
        return livingThing;
    }

    public long getLostScale() {
        return lostScale;
    }
}
