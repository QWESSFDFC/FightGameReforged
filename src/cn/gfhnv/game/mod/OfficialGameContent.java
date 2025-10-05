package cn.gfhnv.game.mod;
import cn.gfhnv.game.entity.Entity;
import cn.gfhnv.game.event.Event;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.mod.officialModStuff.InsectBoss;
import cn.gfhnv.game.mod.officialModStuff.PlayerOne;
import cn.gfhnv.game.mod.officialModStuff.customEvent.InsectBossDeathEvent;
import cn.gfhnv.game.mod.officialModStuff.customEvent.InsectBossDeathEventListener;

import java.util.ArrayList;
import java.util.List;
public class OfficialGameContent extends Mod {
    public OfficialGameContent() {
        super("game_official_content:");
        List<Entity> entities=new ArrayList<>();
        entities.add(new PlayerOne());
        entities.add(new InsectBoss());
        setEntityList(entities);
        EventBus.post(new Event());
        EventBus.register(new InsectBossDeathEventListener());

    }



}
