package cn.gfhnv.game.mod.officialModStuff.customEvent;

import cn.gfhnv.game.event.Event;
import cn.gfhnv.game.mod.officialModStuff.customEntity.InsectBoss;

public class InsectBossDeathEvent extends Event {
    private final InsectBoss insectBoss;

    public InsectBossDeathEvent(InsectBoss insectBoss) {
        this.insectBoss = insectBoss;
    }

    public InsectBoss getInsectBoss() {
        return insectBoss;
    }
}
