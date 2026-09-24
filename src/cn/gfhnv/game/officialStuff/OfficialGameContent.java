package cn.gfhnv.game.officialStuff;

import cn.gfhnv.game.mod.Mod;
import cn.gfhnv.game.officialStuff.customEffect.actorLiXiaoYanEffects.MemorizedHp;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.*;
import cn.gfhnv.game.officialStuff.customEntity.monsters.CommonInsect;
import cn.gfhnv.game.officialStuff.customEntity.monsters.IceInsect;
import cn.gfhnv.game.officialStuff.customEntity.monsters.InsectBoss;
import cn.gfhnv.game.officialStuff.customEntity.players.ActorLiXiaoYan;
import cn.gfhnv.game.officialStuff.customEntity.players.Phainon;
import cn.gfhnv.game.officialStuff.customEntity.players.PlayerOne;
import cn.gfhnv.game.officialStuff.customItem.ANiceSword;
import cn.gfhnv.game.officialStuff.customItem.potions.*;

/**
 * 官方（游戏自带）内容。
 * <p>
 * 这里注册的<b>通用效果</b>会出现在 {@code /effect} 命令的候选列表里
 * （判定依据是 {@link cn.gfhnv.game.effect.Effect#isUniversal()}）；
 * 角色专属效果（{@link MemorizedHp}）虽然也注册在效果表里，但不会被该命令施加。
 */
public class OfficialGameContent extends Mod {
    public OfficialGameContent() {//请模组加载时把模组内容在invokeWhenLoaded方法中添加到模组的各个List中.不要学这个
        super("game_official_content", new OfficialModInformation());
        this.addItem(new ANiceSword());

        // 效果药水：使用后给自己挂一个通用效果（见 customItem/potions/）
        this.addItem(new AttackPotion());
        this.addItem(new DefensePotion());
        this.addItem(new HpPotion());
        this.addItem(new SpeedPotion());
        this.addItem(new CriticalRatePotion());
        this.addItem(new CriticalDMGPotion());
        this.addItem(new HealingPotion());
        this.addItem(new PiercingPotion());

        this.addEntity(new PlayerOne(125));
        this.addEntity(new ActorLiXiaoYan(125));
        this.addEntity(new InsectBoss(150));
        this.addEntity(new CommonInsect(150L));
        this.addEntity(new IceInsect(150));
        this.addEntity(new Phainon(125));

        // 通用效果：任意生物都能获得，/effect 命令可用
        this.addEffect(new DamageEnhanceEffect());
        this.addEffect(new AttackEnhance(0.2, 3));
        this.addEffect(new DefenseEnhanceEffect(1, 3));
        this.addEffect(new HpEnhanceEffect(0.2, 3));
        this.addEffect(new SpeedEnhanceEffect(0.2, 3));
        this.addEffect(new CriticalRateEnhanceEffect(0.2, 3));
        this.addEffect(new CriticalDMGEnhanceEffect(0.5, 3));
        this.addEffect(new IgnoreDefenceEffect(1, 3));
        this.addEffect(new HealthRestoreEffect());
        this.addEffect(new Frozen());
        // 嘲讽：不改属性，只是让 TargetStrategies.tauntAware(...) 这类策略优先选中持有者
        this.addEffect(new Taunt());

        // 角色专属效果：注册进效果表，但不标记为通用，/effect 不会施加
        this.addEffect(new MemorizedHp());
        System.out.println("游戏自带内容加载完成");

    }
}
