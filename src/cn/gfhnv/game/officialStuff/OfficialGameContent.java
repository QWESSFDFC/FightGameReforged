package cn.gfhnv.game.officialStuff;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.mod.Mod;
import cn.gfhnv.game.officialStuff.customEffect.actorLiXiaoYanEffects.MemorizedHp;
import cn.gfhnv.game.officialStuff.customEffect.flameReaverEffects.*;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.*;
import cn.gfhnv.game.officialStuff.customEntity.monsters.CommonInsect;
import cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver;
import cn.gfhnv.game.officialStuff.customEntity.monsters.IceInsect;
import cn.gfhnv.game.officialStuff.customEntity.monsters.InsectBoss;
import cn.gfhnv.game.officialStuff.customEntity.players.ActorLiXiaoYan;
import cn.gfhnv.game.officialStuff.customEntity.players.Phainon;
import cn.gfhnv.game.officialStuff.customEntity.players.PlayerOne;
import cn.gfhnv.game.officialStuff.customEntity.summons.BrokenContainer;
import cn.gfhnv.game.officialStuff.customItem.ANiceSword;
import cn.gfhnv.game.officialStuff.customItem.potions.*;
import cn.gfhnv.game.world.World;

/**
 * 官方（游戏自带）内容。
 * <p>
 * 这里注册的<b>通用效果</b>会出现在 {@code /effect} 命令的候选列表里
 * （判定依据是 {@link cn.gfhnv.game.effect.Effect#isUniversal()}）；
 * 角色专属效果（{@link MemorizedHp}）虽然也注册在效果表里，但不会被该命令施加。
 */
public class OfficialGameContent extends Mod {

    /**
     * 官方内容的模组 ID。注册时会被 {@code Mod.addXxx} 加到每个内容的 id 前缀上，
     * 所以官方物品的完整 id 形如 {@code game_official_content:aNiceSword}。
     */
    public static final String MOD_ID = "game_official_content";

    public OfficialGameContent() {//请模组加载时把模组内容在invokeWhenLoaded方法中添加到模组的各个List中.不要学这个
        super(MOD_ID, new OfficialModInformation());
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
        // 盗火行者（剧情 BOSS）与其召唤物【残破容器】。容器虽然由 BOSS 现场 new 出来，
        // 也要注册：Fight#addEnemy 会按 id 把短 id 补成完整注册 id。
        this.addEntity(new FlameReaver(150));
        this.addEntity(new BrokenContainer(new FlameReaver(150)));
        // 完整容器是同一个类的另一种"种类"（见 BrokenContainer.Kind）
        this.addEntity(new BrokenContainer(new FlameReaver(150), BrokenContainer.Kind.COMPLETE));

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

        // 角色专属效果：注册进效果表，但不标记为通用，/effect 不会施加。
        // 注册的另一个意义是让运行时实例的 id 被补成完整 id
        // （World#registeredIdOf 按【类】查表，没注册的运行时效果只能留着短 id）。
        this.addEffect(new MemorizedHp());
        // 盗火行者专属效果
        this.addEffect(new Erosion(1));                    // 侵蚀（挂在被击中的我方身上）
        this.addEffect(new SacrificeRite(SacrificeRite.DEFAULT_LAST_TIME));   // 共祭（挂在容器身上）
        this.addEffect(new PainEntanglement());            // 苦痛缠绕账本（挂在 BOSS 身上）
        this.addEffect(new LockedRite(LockedRite.DEFAULT_LAST_TIME));         // 镣锁（二阶段容器）
        this.addEffect(new ContainerReward(ContainerReward.DEFAULT_ENHANCE,
                ContainerReward.DEFAULT_LAST_TIME));       // 击杀完整容器的增伤 buff
        System.out.println("游戏自带内容加载完成");

    }

    /**
     * 判断一件物品是不是<b>官方内容</b>（命令里"能不能只写短名"就看这个）。
     * <p>
     * 判据是<b>谁注册的</b>：在模组表里找到认领这件物品的模组，看它是不是官方内容本身。
     * 不用"id 里带没带冒号"判断 —— 官方内容的 id 同样带前缀
     * （{@code game_official_content:...}），那样会把官方当成模组。
     * <p>
     * 没有模组认领的物品（例如测试里直接塞进 {@code World} 的临时物品）按官方处理，
     * 保持宽松，免得把临时内容的短名也一起禁掉。
     *
     * @param item 物品；可为 {@code null}
     * @return 是否为官方内容
     */
    public static boolean isOfficial(Item item) {
        if (item == null) {
            return false;
        }
        for (Mod mod : World.getModList()) {
            if (mod == null) {
                continue;
            }
            for (Item owned : mod.getItems()) {
                if (owned == item) {
                    return mod instanceof OfficialGameContent;
                }
            }
        }
        return true;
    }

    /**
     * 判断一个效果是不是<b>官方内容</b>。判据与 {@link #isOfficial(Item)} 相同。
     *
     * @param effect 效果；可为 {@code null}
     * @return 是否为官方内容
     */
    public static boolean isOfficial(Effect effect) {
        if (effect == null) {
            return false;
        }
        for (Mod mod : World.getModList()) {
            if (mod == null) {
                continue;
            }
            for (Effect owned : mod.getEffects()) {
                if (owned == effect) {
                    return mod instanceof OfficialGameContent;
                }
            }
        }
        return true;
    }
}
