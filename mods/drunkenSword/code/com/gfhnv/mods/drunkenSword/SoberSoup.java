package com.gfhnv.mods.drunkenSword;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.system.fight.Fight;

/**
 * 【醒酒汤】—— 使用后清空全部【醉意】，每层换成一点治疗。
 * <p>
 * 它是【醉意】的"变现出口"：血量吃紧时不用硬攒层数，把酒醒掉换命。
 * 每层 {@value #HEAL_PERCENT_PER_STACK}、最多 {@link Drunkenness#MAX_STACKS} 层，
 * 所以最多回 40% 最大生命 —— 比治疗药水的固定值更依赖"你攒了多少"。
 *
 * @author AI（DeepSeek）生成
 */
public class SoberSoup extends Item {

    /**
     * 每层【醉意】回复的最大生命百分比。
     */
    public static final double HEAL_PERCENT_PER_STACK = 0.04;

    /**
     * 构造【醒酒汤】。
     */
    public SoberSoup() {
        super("醒酒汤", "清空自身全部【醉意】，每层回复 4% 最大生命（最多 40%）。", "soberSoup");
    }

    /**
     * 复制构造器。
     *
     * @param other 被复制的物品
     */
    public SoberSoup(SoberSoup other) {
        super(other);
    }

    @Override
    public Item copy() {
        return new SoberSoup(this);
    }

    /**
     * 使用效果：花光层数换治疗。
     *
     * @param user  使用者
     * @param fight 当前战斗（本物品用不到，但接口要求）
     */
    @Override
    public void comeToEffect(LivingThing user, Fight fight) {
        if (user == null) {
            return;
        }
        int stacks = Drunkenness.takeAll(user);
        if (stacks <= 0) {
            System.out.println(user.getName() + "喝下【醒酒汤】，但没有【醉意】可解");
            return;
        }
        long heal = (long) (user.getHpMax() * HEAL_PERCENT_PER_STACK * stacks);
        user.setHp(user.getHp() + heal);
        System.out.println(user.getName() + "喝下【醒酒汤】，清空 " + stacks + " 层【醉意】，回复 "
                + heal + " 点生命（剩余 " + user.getHp() + "/" + user.getHpMax() + "）");
    }
}
