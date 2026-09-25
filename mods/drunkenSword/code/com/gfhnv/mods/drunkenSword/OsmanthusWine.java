package com.gfhnv.mods.drunkenSword;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.system.fight.Fight;

/**
 * 【桂花酿】—— 使用后立刻 +{@value #STACK_GAIN} 层【醉意】，再回一点血。
 * <p>
 * 酒剑仙的"加速器"：光靠普攻一层一层攒太慢，带上几瓶桂花酿就能在一个回合内把层数推到大招线以上。
 * <p>
 * <b>物品必须重写 {@link #copy()}</b>：背包里放的是 {@code copy()} 出来的副本，
 * 而且堆叠判定按 id 走，所以副本要走 {@link Item#Item(Item)} 把 id 一起带过去
 * （用无参构造器新建的副本会掉回短 id，既不能和背包里的同种物品叠在一格，
 * {@code /give} 的完整 id 也认不出它）。
 *
 * @author AI（DeepSeek）生成
 */
public class OsmanthusWine extends Item {

    /**
     * 使用后获得的【醉意】层数。
     */
    public static final int STACK_GAIN = 4;

    /**
     * 使用后回复的最大生命百分比。
     */
    public static final double HEAL_PERCENT = 0.05;

    /**
     * 构造【桂花酿】。
     */
    public OsmanthusWine() {
        super("桂花酿", "使用后获得 4 层【醉意】（上限 10 层），并回复 5% 最大生命。", "osmanthusWine");
    }

    /**
     * 复制构造器。
     *
     * @param other 被复制的物品
     */
    public OsmanthusWine(OsmanthusWine other) {
        super(other);
    }

    @Override
    public Item copy() {
        return new OsmanthusWine(this);
    }

    /**
     * 使用效果：加层 + 回血。目标是使用者自己（{@link #isForEnemies()} 保持默认的 {@code false}）。
     *
     * @param user  使用者
     * @param fight 当前战斗（本物品用不到，但接口要求）
     */
    @Override
    public void comeToEffect(LivingThing user, Fight fight) {
        if (user == null) {
            return;
        }
        int stacks = Drunkenness.add(user, STACK_GAIN);
        long heal = (long) (user.getHpMax() * HEAL_PERCENT);
        user.setHp(user.getHp() + heal);
        System.out.println(user.getName() + "饮下【桂花酿】：【醉意】+" + STACK_GAIN
                + "（当前 " + stacks + "/" + Drunkenness.MAX_STACKS + " 层，减伤 "
                + (int) (Drunkenness.reductionOf(user) * 100) + "%），回复 " + heal + " 点生命");
    }
}
