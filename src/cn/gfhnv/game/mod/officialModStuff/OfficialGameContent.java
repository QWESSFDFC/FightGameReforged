package cn.gfhnv.game.mod.officialModStuff;

import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.mod.Mod;
import cn.gfhnv.game.mod.officialModStuff.customEffect.HealthRestoreEffect;
import cn.gfhnv.game.mod.officialModStuff.customEntity.InsectBoss;
import cn.gfhnv.game.mod.officialModStuff.customEntity.PlayerOne;
import cn.gfhnv.game.mod.officialModStuff.customEvent.InsectBossDeathEventListener;

public class OfficialGameContent extends Mod {
    public OfficialGameContent() {
        super("game_official_content:",new OfficialModInformation());
        this.addEntity(new PlayerOne());
        this.addEntity(new InsectBoss());
        this.addEffect(new HealthRestoreEffect());
        System.out.println("OfficialGameContent Initialized.");
        EventBus.register(new InsectBossDeathEventListener());
    }
}
