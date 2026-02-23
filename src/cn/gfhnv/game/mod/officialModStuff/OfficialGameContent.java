package cn.gfhnv.game.mod.officialModStuff;

import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.mod.Mod;
import cn.gfhnv.game.mod.officialModStuff.customEffect.DamageEnhanceEffect;
import cn.gfhnv.game.mod.officialModStuff.customEffect.HealthRestoreEffect;
import cn.gfhnv.game.mod.officialModStuff.customEntity.CommonInsect;
import cn.gfhnv.game.mod.officialModStuff.customEntity.InsectBoss;
import cn.gfhnv.game.mod.officialModStuff.customEntity.PlayerOne;
import cn.gfhnv.game.mod.officialModStuff.customEvent.InsectBossDeathEventListener;
import cn.gfhnv.game.mod.officialModStuff.customItem.ANiceSword;

public class OfficialGameContent extends Mod {
    public OfficialGameContent() {
        super("game_official_content:", new OfficialModInformation());
        this.addEffect(new DamageEnhanceEffect());
        this.addItem(new ANiceSword());
        this.addEntity(new PlayerOne(125));
        this.addEntity(new InsectBoss(150));
        this.addEntity(new CommonInsect(150L));
        this.addEffect(new HealthRestoreEffect());
        System.out.println("游戏自带内容加载完成");
        EventBus.register(new InsectBossDeathEventListener());
    }
}
