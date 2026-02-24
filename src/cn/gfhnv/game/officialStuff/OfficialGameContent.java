package cn.gfhnv.game.officialStuff;

import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.mod.Mod;
import cn.gfhnv.game.officialStuff.customEffect.DamageEnhanceEffect;
import cn.gfhnv.game.officialStuff.customEffect.HealthRestoreEffect;
import cn.gfhnv.game.officialStuff.customEntity.CommonInsect;
import cn.gfhnv.game.officialStuff.customEntity.InsectBoss;
import cn.gfhnv.game.officialStuff.customEntity.PlayerOne;
import cn.gfhnv.game.officialStuff.customEvent.InsectBossDeathEventListener;
import cn.gfhnv.game.officialStuff.customItem.ANiceSword;

public class OfficialGameContent extends Mod {
    public OfficialGameContent() {//请模组加载时把模组内容在invokeWhenLoaded方法中添加到模组的各个List中.不要学这个
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
