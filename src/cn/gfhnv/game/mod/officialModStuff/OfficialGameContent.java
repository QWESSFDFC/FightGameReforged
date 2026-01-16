package cn.gfhnv.game.mod.officialModStuff;
import cn.gfhnv.game.entity.Entity;
import cn.gfhnv.game.event.Event;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.mod.Mod;
import cn.gfhnv.game.mod.officialModStuff.customEvent.InsectBossDeathEventListener;

import java.util.ArrayList;
import java.util.List;
public class OfficialGameContent extends Mod {
    public OfficialGameContent() {
        super("game_official_content:");
       this.addEntity(new PlayerOne());
       this.addEntity(new InsectBoss());
        System.out.println("OfficialGameContent Initialized.");
        EventBus.register(new InsectBossDeathEventListener());
    }
}
