package cn.gfhnv.game.officialStuff.customEffect.flameReaverEffects;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;

/**
 * 【镣锁】—— 二阶段的【残破容器】状态。
 * <p>
 * 官方「为我设奠」：处于【镣锁】状态下的容器<b>受到致命攻击</b>时，
 * 若盗火行者的【灾难之力】层数不为 0，<b>立即被其重新召唤</b>并回到【镣锁】状态，
 * 但会让盗火行者<b>失去 1 层灾难之力和一定比例的生命值</b>；
 * 施放【莫因舍弃而哭泣】时，这些容器会被吸收，回复转化为【苦痛缠绕】的生命值。
 * <p>
 * 也就是说：镣锁容器是"用灾难之力和血量换来的肉盾"—— 玩家打不死它们，
 * 但每打死一次都在替玩家消耗 BOSS 的资源。这个换算就是二阶段的博弈。
 * <p>
 * 与 {@link SacrificeRite} 一样：注册进效果表、<b>不标 {@code UNIVERSAL}</b>
 * （所以 {@code /effect} 不能施加），{@code origin} 记录施加者（盗火行者的 UUID）。
 *
 * @author AI（DeepSeek）生成
 */
public class LockedRite extends Effect {

    /**
     * 状态 id（运行时会被补成 {@code game_official_content:lockedRite}）。
     */
    public static final String ID = "lockedRite";

    /**
     * 默认维持回合数。
     * <p>
     * 镣锁是二阶段的常驻状态，给足回合数（到期后容器就退化成普通容器）。
     */
    public static final int DEFAULT_LAST_TIME = 30;

    /**
     * 构造【镣锁】效果。
     *
     * @param lastTime 维持回合数
     */
    public LockedRite(int lastTime) {
        super(ID);
        this.setLastTime(Math.max(1, lastTime));
        this.setNegative(true);
    }

    /**
     * 复制构造器。
     *
     * @param other 被复制的效果
     */
    public LockedRite(LockedRite other) {
        super(other.getID());
        this.setLastTime(other.getLastTime());
        this.setLevel(other.getLevel());
        this.setNegative(true);
    }

    @Override
    public Effect copy() {
        return new LockedRite(this);
    }

    /**
     * 判断生物身上是否有【镣锁】。
     *
     * @param thing 生物
     * @return 有则 {@code true}
     */
    public static boolean has(LivingThing thing) {
        if (thing == null || thing.getEntityEffectList() == null) {
            return false;
        }
        for (Effect effect : thing.getEntityEffectList()) {
            if (effect instanceof LockedRite) {
                return true;
            }
        }
        return false;
    }

    /**
     * 移除身上的【镣锁】。
     *
     * @param thing 生物
     * @return 是否真的移除了
     */
    public static boolean remove(LivingThing thing) {
        if (thing == null || thing.getEntityEffectList() == null) {
            return false;
        }
        return thing.getEntityEffectList().removeIf(effect -> effect instanceof LockedRite);
    }

    /**
     * 标记型效果：不需要每回合做事（也避免基类占位实现打印提示）。
     *
     * @param thing 持有者
     */
    @Override
    public void comeIntoEffect(LivingThing thing) {
        // 标记型效果，不做任何事
    }
}
