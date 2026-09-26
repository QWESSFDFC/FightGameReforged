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
 * 「一剑霜寒十四州」—— 大招：花光全部【醉意】对<b>全体</b>敌人各砍一剑，随后自己宿醉。
 * <p>
 * 倍率 = {@value #BASE_MAGNIFICATION} + 每层 {@value #PER_STACK_MAGNIFICATION}（满 10 层时 2900%），
 * 但<b>必须攒到 {@value #REQUIRED_STACKS} 层才能放</b>。放完挂 {@value Hangover#DEFAULT_LAST_TIME} 回合
 * 【宿醉】（防御 −30%）：爆发一次，接下来两个回合更脆。
 * <p>
 * <b>重写 {@code canUse} 的坑</b>：控制器在判断"这个技能能不能用"时会调
 * {@code canUse(fight, owner, null)} —— 第三个参数<b>就是 {@code null}</b>
 * （{@code UniversalController:115}、{@code PlayerController:118} 都这么调）。
 * 所以重写时只能读 {@code user} 身上的状态，<b>不能解引用 {@code enemies}</b>，否则一进战斗就 NPE。
 *
 * @author AI（DeepSeek）生成
 */
public class FrostSword extends Skill {

    /**
     * 基础攻击力倍率。
     */
    public static final double BASE_MAGNIFICATION = 4.0;

    /**
     * 每层【醉意】追加的攻击力倍率。
     */
    public static final double PER_STACK_MAGNIFICATION = 2.5;

    /**
     * 施放所需的【醉意】层数下限。
     */
    public static final int REQUIRED_STACKS = 4;

    /**
     * 冷却回合数。
     * <p>
     * 和官方角色一样<b>不设冷却</b>（白厄的大招与最后一击都是 0 冷却）：
     * 这招的门槛是【醉意】层数，攒得出来就能放，攒不出来给了冷却也没用。
     */
    public static final int COOL_DOWN = 0;

    /**
     * 火法力消耗。
     */
    public static final int MANA_COST = 800;

    /**
     * 构造「一剑霜寒十四州」。
     * <p>
     * 目标数写 {@code -1} = 全体（{@code 0} = 自己、正数 = 需要选 N 个目标）。
     */
    public FrostSword() {
        super("一剑霜寒十四州", "需要至少 4 层【醉意】：消耗全部层数，"
                + "对敌方全体造成（400% + 每层 250%）攻击力的伤害，随后自身获得 2 回合【宿醉】。",
                0, BASE_MAGNIFICATION, 0, -1);
        this.setCoolDown(COOL_DOWN);
        this.setConsumedMana(new Mana(MANA_COST, ElementSort.FIRE));
        this.getTags().put(TagType.ATTACK, new Tag(6));
    }

    /**
     * 复制构造器。
     *
     * @param other 被复制的技能
     */
    public FrostSword(FrostSword other) {
        super(other);
    }

    @Override
    public Skill copy() {
        return new FrostSword(this);
    }

    /**
     * 施放条件：醉意层数够，且法力/冷却也满足（交给基类判断）。
     *
     * @param fight   当前战斗
     * @param user    使用者（酒剑仙）
     * @param enemies 本次的目标列表；<b>可能为 {@code null}</b>，不要解引用
     * @return 是否可施放
     */
    @Override
    public boolean canUse(Fight fight, LivingThing user, List<LivingThing> enemies) {
        return Drunkenness.stacksOf(user) >= REQUIRED_STACKS && super.canUse(fight, user, enemies);
    }

    /**
     * 花光醉意 → 按总倍率打全体 → 给自己挂【宿醉】。
     *
     * @param fight   当前战斗
     * @param user    使用者（酒剑仙）
     * @param enemies 本次的目标列表（目标数为 -1 时是敌方全体）
     */
    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        int stacks = Drunkenness.takeAll(user);
        System.out.println(user.getName() + "一剑霜寒十四州！消耗 " + stacks
                + " 层【醉意】（醉意减伤随之清零）");

        Skill strike = this.copy();
        strike.setAtkMagnification(BASE_MAGNIFICATION + PER_STACK_MAGNIFICATION * stacks);
        if (enemies != null) {
            for (LivingThing target : enemies) {
                if (target == null || !target.isAlive()) {
                    continue;
                }
                user.makeDamage(target, strike);
            }
        }

        // 宿醉：走 addEffect，这样 id 会被补成注册表里的完整 id，与效果注册表里那条一致
        user.addEffect(new Hangover());
        System.out.println(user.getName() + "酒醒人散，获得【宿醉】（防御 "
                + (int) (Hangover.DEFENCE_PERCENT * 100) + "%，持续 "
                + Hangover.DEFAULT_LAST_TIME + " 回合）");
    }
}
