package cn.gfhnv.game.officialStuff.customEvent;

import cn.gfhnv.game.event.Event;
import cn.gfhnv.game.officialStuff.customEntity.InsectBoss;

public class InsectBossDeathEvent extends Event {
    private final InsectBoss insectBoss;

    public InsectBossDeathEvent(InsectBoss insectBoss) {
        this.insectBoss = insectBoss;
    }

    public InsectBoss getInsectBoss() {
        return insectBoss;
    }
}
