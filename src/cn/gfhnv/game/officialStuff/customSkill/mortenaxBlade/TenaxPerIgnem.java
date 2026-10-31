package cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.interfaces.IAiScoreBonus;
import cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.mana.Mana;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;

import java.util.List;

/**
 * 千冶·刃 · <b>全新终结技</b>「千冶铸一，万劫烬灭」（只在【无量忿怒】期间存在）：
 * 对敌方全体造成等同于自身<b>生命上限 350%</b> 的火属性伤害。
 * <p>
 * 设计稿见 `TODO.md`（中文版 350%；英文版 378%，**按中文**）。它和「骸骨当炉」一样要 160 能量，
 * 但门槛多一条：<b>必须在结界里</b>（否则技能表里根本没有它）。
 * <p>
 * <b>额外机制（2026-10-24 用户追加，设计稿里没有）</b>：打完这一击<b>生命恢复至上限</b>（加强续航）。
 * 注意它只挂在这一招上 —— 开结界的「骸骨当炉，血肉即薪」<b>不</b>回血（那一招反而要扣 20% 生命上限）。
 *
 * @author AI（DeepSeek）生成；设计稿作者：仓库作者
 */
public class TenaxPerIgnem extends Skill implements IAiScoreBonus {

    /**
     * 伤害倍率（设计：350% 生命上限）。
     */
    public static final double MAGNIFICATION = 3.5;

    /**
     * AI 评分加成（结界内 + 能量满时的优先级，远大于普通攻击的典型分数 10）。
     */
    public static final double AI_PRIORITY = 100;

    /**
     * 这一招回多少能量（设计稿英文版：{@code Energy gain: 5}）。
     */
    public static final long ENERGY_GAIN = 5;

    public TenaxPerIgnem() {
        super("千冶铸一，万劫烬灭", "对敌方全体造成等同于千冶·刃350%生命上限的火属性伤害。",
                MAGNIFICATION, 0, 0, -1);
        this.setCoolDown(0);
        this.setConsumedMana(new Mana(0, ElementSort.FIRE));
        this.getTags().put(TagType.ATTACK, new Tag(8));
    }

    /**
     * 复制构造器（配置打在模板上，副本靠 {@code copy()} 白送）。
     *
     * @param other 被复制的技能
     */
    public TenaxPerIgnem(TenaxPerIgnem other) {
        super(other);
    }

    @Override
    public Skill copy() {
        return new TenaxPerIgnem(this);
    }

    /**
     * 门槛：千冶·刃专用、在【无量忿怒】里、且能量攒满（160）。
     *
     * @param fight 当前战斗
     * @param user  使用者
     * @return 能不能放
     */
    @Override
    public boolean canUse(Fight fight, LivingThing user) {
        if (!(user instanceof MortenaxBlade blade)) {
            return false;
        }
        return blade.isInfiniteFury()
                && blade.getEnergy() >= blade.getEnergyMax()
                && super.canUse(fight, user);
    }

    /**
     * 带目标列表的门槛（AI 与 {@code Skill#use(...)} 走的是三参版本）。
     *
     * @param fight   当前战斗
     * @param user    使用者
     * @param targets 目标（本技能是全体，忽略）
     * @return 能不能放
     */
    @Override
    public boolean canUse(Fight fight, LivingThing user, List<LivingThing> targets) {
        return canUse(fight, user) && super.canUse(fight, user, targets);
    }

    /**
     * <b>AI 评分加成</b>：在结界里、能量又满了 → 加 {@link #AI_PRIORITY} 分（把它当"结界里的大招"放出去）。
     *
     * @param fight  当前战斗（本方法不用，允许 null）
     * @param self   施法者
     * @param target 目标
     * @return 加多少分
     */
    @Override
    public double aiScoreBonus(Fight fight, LivingThing self, LivingThing target) {
        if (!(self instanceof MortenaxBlade blade)) {
            return 0;
        }
        if (!blade.isInfiniteFury() || blade.getEnergy() < blade.getEnergyMax()) {
            return 0;
        }
        return AI_PRIORITY;
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        for (LivingThing enemy : enemies) {
            user.makeDamage(enemy, this);
        }
        if (user instanceof MortenaxBlade blade) {
            // 付能量：清掉一个 energyMax（溢出留下）
            blade.spendUltimateEnergy();
            blade.gainEnergy(ENERGY_GAIN);
            // 额外机制（用户 2026-10-24 追加）：打完这一击生命恢复至上限（续航）
            long before = blade.getHp();
            blade.setHp(blade.getHpMax());
            System.out.println(blade.getName() + "的【千冶铸一，万劫烬灭】打完，生命恢复至上限（"
                    + before + " → " + blade.getHp() + "）");
        }
    }
}
