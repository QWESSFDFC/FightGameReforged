package cn.gfhnv.game.officialStuff.customEvent.phainonEvents;

import cn.gfhnv.game.event.Event;
import cn.gfhnv.game.officialStuff.customEntity.players.Phainon;

public class AwakenEndEvent extends Event {
    private final Phainon phainon;

    public AwakenEndEvent(Phainon phainon) {
        this.phainon = phainon;
    }

    public Phainon getPhainon() {
        return phainon;
    }

}
