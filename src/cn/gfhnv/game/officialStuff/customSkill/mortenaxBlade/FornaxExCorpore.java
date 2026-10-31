package cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.interfaces.IAiScoreBonus;
import cn.gfhnv.game.officialStuff.customEffect.mortenaxBladeEffects.BalefireBindEffect;
import cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.mana.Mana;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;

import java.util.ArrayList;
import java.util.List;

/**
 * 千冶·刃 · 终结技「骸骨当炉，血肉即薪」（设计稿）：<b>开结界</b>的那一招。
 * <p>
 * 效果（按设计稿的中文数值）：
 * <ol>
 *     <li>敌方全体陷入【煞火缠身】（防御 -30%，2 回合）；</li>
 *     <li>消耗<b>生命上限 20%</b> 的生命值展开结界（血不够时降到 1）；</li>
 *     <li>结界期间：千冶·刃进入【无量忿怒】（暴击率 +20% / 暴击伤害 +60%）、
 *     换技能表（强化普攻 / 解放战技 / 全新终结技）、受到致命攻击不会倒下；</li>
 *     <li>行动序列上出现倒计时（固定 70 速度），到点解除结界。</li>
 * </ol>
 * <p>
 * <b>倒计时用 {@code CountdownEntity}</b>（不是"给千冶·刃自己插额外回合"）：额外回合的行动者是本体，
 * 会和本体的回合混在一条轴上（排序 / 延后 / {@code getNextTurnOf} 全污染），
 * 详见 {@code project_analyses/MORTENAX-BLADE-2026-10.md} §三.1。
 * <p>
 * <b>"敌人受伤 +50%" 这一条今天没做</b>（`getDamageTakenMultiplier` 夹在 `[0,1]`，见分析文档 §三.3）。
 *
 * @author AI（DeepSeek）生成；设计稿作者：仓库作者
 */
public class FornaxExCorpore extends Skill implements IAiScoreBonus {

    /**
     * 展开结界消耗的生命上限比例（设计：20%）。
     */
    public static final double HP_COST_RATIO = 0.2;

    /**
     * 【煞火缠身】持续回合数（设计：2）。
     */
    public static final int BALEFIRE_LAST_TIME = 2;

    /**
     * 倒计时的固定速度（设计：70）。
     */
    public static final long COUNTDOWN_SPEED = 70;

    /**
     * AI 评分加成：能量满时 AI 有多想开结界（远大于普通攻击的典型分数 10）。
     */
    public static final double AI_PRIORITY = 100;

    /**
     * 这一招回多少能量（设计稿英文版：{@code Energy gain: 5}）。
     */
    public static final long ENERGY_GAIN = 5;

    public FornaxExCorpore() {
        super("骸骨当炉，血肉即薪",
                "使敌方全体陷入【煞火缠身】，随后消耗生命上限20%的生命值展开结界。"
                        + "结界持续期间进入【无量忿怒】：暴击率+20%、暴击伤害+60%，普攻强化、解放战技、"
                        + "获得全新终结技；受到致命攻击时不会倒下，但会解除结界并回复自身50%生命上限的生命值。",
                0, 0, 0, 0);
        this.setCoolDown(0);
        this.setConsumedMana(new Mana(0, ElementSort.FIRE));
        this.getTags().put(TagType.ATTACK, new Tag(4));
    }

    /**
     * 复制构造器（配置打在模板上，副本靠 {@code copy()} 白送）。
     *
     * @param other 被复制的技能
     */
    public FornaxExCorpore(FornaxExCorpore other) {
        super(other);
    }

    @Override
    public Skill copy() {
        return new FornaxExCorpore(this);
    }

    /**
     * 门槛：只有千冶·刃能用，**能量攒满**（160）而且<b>还没在【无量忿怒】里</b>。
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
        return !blade.isInfiniteFury()
                && blade.getEnergy() >= blade.getEnergyMax()
                && super.canUse(fight, user);
    }

    /**
     * 带目标列表的门槛：与 {@link #canUse(Fight, LivingThing)} 一致
     * （AI 与 {@code Skill#use(...)} 走的都是三参版本）。
     *
     * @param fight   当前战斗
     * @param user    使用者
     * @param targets 目标（本技能是给自己开的，忽略）
     * @return 能不能放
     */
    @Override
    public boolean canUse(Fight fight, LivingThing user, List<LivingThing> targets) {
        return canUse(fight, user) && super.canUse(fight, user, targets);
    }

    /**
     * 带目标列表的入口 —— {@code Skill#use(fight, user, targets)} 与 AI 走的是<b>这一个</b>。
     * <p>
     * 本技能是"给自己开结界"（{@code aims = 0}），目标列表被忽略。
     *
     * @param fight   当前战斗
     * @param user    使用者
     * @param targets 目标（忽略）
     */
    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> targets) {
        ignite(fight, user);
    }

    /**
     * 无目标列表的入口（{@code aims = 0} 时控制器调的是这个）。
     *
     * @param fight 当前战斗
     * @param user  使用者
     */
    /**
     * <b>AI 评分加成</b>：能量满、还没进结界 → 加 {@link #AI_PRIORITY} 分，让思维 AI 愿意开结界。
     * <p>
     * 不加的话这个技能在 AI 眼里是 0 伤害、目标是自己（满血）→ 永远打不过普攻 ——
     * 和白厄的大招是同一个坑（见 {@code IAiScoreBonus} 的接口注释）。
     *
     * @param fight  当前战斗（本方法不用，允许 null）
     * @param self   施法者
     * @param target 目标（本技能对自己，AI 传进来的是 self）
     * @return 加多少分
     */
    @Override
    public double aiScoreBonus(Fight fight, LivingThing self, LivingThing target) {
        if (!(self instanceof MortenaxBlade blade)) {
            return 0;
        }
        if (blade.isInfiniteFury() || blade.getEnergy() < blade.getEnergyMax()) {
            return 0;
        }
        return AI_PRIORITY;
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user) {
        ignite(fight, user);
    }

    /**
     * 真正开结界的那一段（两个入口共用，避免两份逻辑漂移）。
     *
     * @param fight 当前战斗
     * @param user  使用者
     */
    private void ignite(Fight fight, LivingThing user) {
        if (!(user instanceof MortenaxBlade blade)) {
            return;
        }
        // ① 敌方全体【煞火缠身】（防御 -30%）
        for (LivingThing enemy : new ArrayList<>(fight.getOpponentList(blade))) {
            enemy.addEffect(new BalefireBindEffect(BALEFIRE_LAST_TIME).setOrigin(blade.getUUID()));
        }
        // ② 消耗生命上限 20%（不够就降到 1）—— 这是"消耗"，不走 heal，也不触发治疗加成
        long cost = (long) (blade.getHpMax() * HP_COST_RATIO);
        // 走 consumeHp：不足则降到 1
        blade.consumeHp(cost);
        // ③ 付能量（清掉一个 energyMax，溢出留下），再按设计稿回一点（Energy gain: 5）
        blade.spendUltimateEnergy();
        blade.gainEnergy(ENERGY_GAIN);
        // ④ 进【无量忿怒】：状态位 + 暴击加成 + 换技能表 + 倒计时
        blade.beginInfiniteFury(fight);
    }
}
