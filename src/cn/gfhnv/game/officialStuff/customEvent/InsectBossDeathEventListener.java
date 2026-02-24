package cn.gfhnv.game.officialStuff.customEvent;

import cn.gfhnv.game.annotation.SubscribeEvent;

public class InsectBossDeathEventListener {
    @SubscribeEvent
    public void deathEventTrigger(InsectBossDeathEvent event) {
        System.out.println(event.getInsectBoss().isAlive());
    }
}
