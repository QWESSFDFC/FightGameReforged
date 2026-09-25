package com.gfhnv.mods.drunkenSword;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.mana.Mana;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;

import java.util.List;

/**
 * 「醉里挑灯看剑」—— 战技：花光全部【醉意】换成一次重击，并按花掉的层数回血。
 * <p>
 * 倍率 = {@value #BASE_MAGNIFICATION} + 每层 {@value #PER_STACK_MAGNIFICATION}，
 * 所以"攒满再打"永远比"有一层打一次"划算 —— 这是这套机制的核心取舍。
 * <p>
 * <b>实现要点</b>：倍率是<b>临时</b>的，不能直接改自己身上的
 * {@code atkMagnification}。控制器持有的技能实例是长期复用的，
 * 改一次字段就等于把这个技能永久变强（白厄的「最后一击」敢那样写，是因为它每次都是
 * {@code new} 出来的、只用一次）。这里的做法是 {@link #copy() 复制一份}，
 * 只在副本上改倍率，打完就丢。
 *
 * @author AI（DeepSeek）生成
 */
public class LanternSword extends Skill {

    /**
     * 基础攻击力倍率（花掉的层数为 0 时的倍率）。
     */
    public static final double BASE_MAGNIFICATION = 2.0;

    /**
     * 每层【醉意】追加的攻击力倍率。
     */
    public static final double PER_STACK_MAGNIFICATION = 1.5;

    /**
     * 每层【醉意】回复的最大生命百分比（满 10 层 = 回 30%）。
     */
    public static final double HEAL_PERCENT_PER_STACK = 0.03;

    /**
     * 冷却回合数。
     * <p>
     * 和官方角色一样<b>不设冷却</b>（白厄的战技/大招/最后一击冷却都是 0）：
     * 真正的门槛是"有多少层醉意"，冷却只会让循环变卡。
     */
    public static final int COOL_DOWN = 0;

    /**
     * 火法力消耗（酒剑仙是火属性，125 级时火法力上限 2680、每回合回 250 左右）。
     */
    public static final int MANA_COST = 300;

    /**
     * 构造「醉里挑灯看剑」。
     */
    public LanternSword() {
        super("醉里挑灯看剑", "消耗全部【醉意】：每层使本次伤害 +150% 攻击力（基础 200%），"
                + "并按每层 3% 最大生命回复自身。", 0, BASE_MAGNIFICATION, 0, 1);
        this.setCoolDown(COOL_DOWN);
        this.setConsumedMana(new Mana(MANA_COST, ElementSort.FIRE));
        this.getTags().put(TagType.ATTACK, new Tag(4));
    }

    /**
     * 复制构造器。
     *
     * @param other 被复制的技能
     */
    public LanternSword(LanternSword other) {
        super(other);
    }

    @Override
    public Skill copy() {
        return new LanternSword(this);
    }

    /**
     * 花光醉意 → 按总倍率打一次 → 按层数回血。
     *
     * @param fight   当前战斗
     * @param user    使用者（酒剑仙）
     * @param enemies 本次的目标列表
     */
    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        int stacks = Drunkenness.takeAll(user);
        System.out.println(user.getName() + "醉里挑灯看剑，倾泻 " + stacks
                + " 层【醉意】（醉意减伤随之清零）");

        Skill strike = this.copy();
        strike.setAtkMagnification(BASE_MAGNIFICATION + PER_STACK_MAGNIFICATION * stacks);
        if (enemies != null) {
            for (LivingThing target : enemies) {
                if (target == null || !target.isAlive()) {
                    continue;
                }
                System.out.print(user.getName() + "攻击了" + target.getName());
                user.makeDamage(target, strike);
                System.out.println();
            }
        }

        if (stacks > 0) {
            long heal = (long) (user.getHpMax() * HEAL_PERCENT_PER_STACK * stacks);
            user.setHp(user.getHp() + heal);
            System.out.println(user.getName() + "酒劲护体，回复了 " + heal
                    + " 点生命（剩余 " + user.getHp() + "/" + user.getHpMax() + "）");
        }
    }
}
