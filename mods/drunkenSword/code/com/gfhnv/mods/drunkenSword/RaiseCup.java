package com.gfhnv.mods.drunkenSword;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;

import java.util.List;

/**
 * 「举杯邀月」—— 酒剑仙的普通攻击（单体，顺便攒 {@value #STACK_GAIN} 层【醉意】）。
 * <p>
 * 这是整套机制的发动机：不打人就攒不到醉意，攒不到醉意战技和大招就没有威力。
 * 无消耗、无冷却，所以每个回合都能用来"垫层"。
 *
 * @author AI（DeepSeek）生成
 */
public class RaiseCup extends Skill {

    /**
     * 攻击力倍率（{@code 3.0} = 300% 攻击力）。
     * <p>
     * 倍率在伤害公式里是 {@code 攻击力 × 本倍率} 这一项，量级参考官方技能：
     * 玩家一普攻 1.0、白厄战技 3.0、枪射击 7.5、白厄最后一击 13。
     */
    public static final double ATK_MAGNIFICATION = 3.0;

    /**
     * 每次施放获得的醉意层数。
     * <p>
     * 给到 2 层是为了让循环跑得动：大招门槛是 {@code FrostSword.REQUIRED_STACKS} 层，
     * 每回合 +1 时要四个回合才能开张，实战里往往等不到。
     */
    public static final int STACK_GAIN = 2;

    /**
     * 构造「举杯邀月」。
     */
    public RaiseCup() {
        super("举杯邀月", "对单体造成 300% 攻击力的伤害，并获得 2 层【醉意】。无消耗、无冷却。",
                0, ATK_MAGNIFICATION, 0, 1);
        this.setCoolDown(0);
        // 不设置 consumedMana：基类里 consumedMana 为 null 时视为"无消耗"
        this.getTags().put(TagType.ATTACK, new Tag(3));
    }

    /**
     * 复制构造器。控制器持有技能时会逐技能 {@code copy()}，所以每个技能类都必须能复制。
     *
     * @param other 被复制的技能
     */
    public RaiseCup(RaiseCup other) {
        super(other);
    }

    @Override
    public Skill copy() {
        return new RaiseCup(this);
    }

    /**
     * 先按倍率打一遍目标，再给自己加醉意。
     *
     * @param fight   当前战斗
     * @param user    使用者（酒剑仙）
     * @param enemies 本次的目标列表
     */
    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        if (enemies != null) {
            for (LivingThing target : enemies) {
                if (target == null || !target.isAlive()) {
                    continue;
                }
                System.out.print(user.getName() + "攻击了" + target.getName());
                user.makeDamage(target, this);
                System.out.println();
            }
        }
        int stacks = Drunkenness.add(user, STACK_GAIN);
        System.out.println(user.getName() + "举杯邀月，【醉意】+" + STACK_GAIN
                + "（当前 " + stacks + "/" + Drunkenness.MAX_STACKS + " 层，减伤 "
                + (int) (Drunkenness.reductionOf(user) * 100) + "%）");
    }
}
