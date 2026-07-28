package cn.gfhnv.game.officialStuff;

import cn.gfhnv.game.mod.Mod;
import cn.gfhnv.game.officialStuff.customEffect.actorLiXiaoYanEffects.MemorizedHp;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.DamageEnhanceEffect;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.Frozen;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.HealthRestoreEffect;
import cn.gfhnv.game.officialStuff.customEntity.monsters.CommonInsect;
import cn.gfhnv.game.officialStuff.customEntity.monsters.IceInsect;
import cn.gfhnv.game.officialStuff.customEntity.monsters.InsectBoss;
import cn.gfhnv.game.officialStuff.customEntity.players.ActorLiXiaoYan;
import cn.gfhnv.game.officialStuff.customEntity.players.PlayerOne;
import cn.gfhnv.game.officialStuff.customItem.ANiceSword;

public class OfficialGameContent extends Mod {
    public OfficialGameContent() {//请模组加载时把模组内容在invokeWhenLoaded方法中添加到模组的各个List中.不要学这个
        super("game_official_content", new OfficialModInformation());
        this.addItem(new ANiceSword());
        this.addEntity(new PlayerOne(125));
        this.addEffect(new DamageEnhanceEffect());
        this.addEffect(new MemorizedHp());
        this.addEntity(new ActorLiXiaoYan(125));
        this.addEntity(new InsectBoss(150));
        this.addEntity(new CommonInsect(150L));
        this.addEffect(new HealthRestoreEffect());
        this.addEffect(new Frozen());
        this.addEntity(new IceInsect(150));
        System.out.println("游戏自带内容加载完成");

    }
}
