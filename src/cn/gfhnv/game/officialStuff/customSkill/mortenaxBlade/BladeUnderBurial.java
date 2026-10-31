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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 千冶·刃 · 战技「刃下，归葬」（<b>【无量忿怒】期间才解放</b>）：
 * 消耗<b>生命上限 10%</b> 的生命值，对敌方全体造成 <b>72%</b> 生命上限的火属性伤害，
 * 并额外造成 4 次伤害，每次对<b>随机</b>敌方单体造成 <b>24%</b> 生命上限的伤害。
 * <p>
 * 设计稿见 `TODO.md`；两条限制也在设计里：血不足时降到 1；<b>未处于【无量忿怒】或血量 ≤ 1 时不能施放</b>。
 *
 * @author AI（DeepSeek）生成；设计稿作者：仓库作者
 */
public class BladeUnderBurial extends Skill implements IAiScoreBonus {

    /**
     * 施放消耗的生命上限比例（设计：10%）。
     */
    public static final double HP_COST_RATIO = 0.1;

    /**
     * 额外段数（设计：4 次）。
     */
    public static final int EXTRA_HITS = 4;

    /**
     * 每一段额外伤害的倍率（设计：24% 生命上限）。
     */
    public static final double EXTRA_HIT_MAGNIFICATION = 0.24;

    /**
     * 全体那段伤害的倍率（设计：72% 生命上限）。
     */
    public static final double MAIN_MAGNIFICATION = 0.72;

    /**
     * 这一招回多少能量（设计稿英文版：{@code Energy gain: 30}）。
     */
    public static final long ENERGY_GAIN = 30;

    /**
     * AI 评分加成（结界内可用时的优先级，比普攻的典型分数 10 高得多）。
     */
    public static final double AI_PRIORITY = 60;

    /**
     * 额外段随机选目标用的随机源（只影响"打谁"，不影响伤害）。
     */
    private static final Random RANDOM = new Random();

    public BladeUnderBurial() {
        super("刃下，归葬",
                "消耗等同于千冶·刃生命上限10%的生命值，对敌方全体造成等同于千冶·刃72%生命上限的火属性伤害，"
                        + "并额外造成4次伤害，每次对随机敌方单体造成等同于千冶·刃24%生命上限的火属性伤害。"
                        + "未处于【无量忿怒】状态或当前生命值小于等于1时无法施放。",
                MAIN_MAGNIFICATION, 0, 0, -1);
        this.setCoolDown(0);
        this.setConsumedMana(new Mana(0, ElementSort.FIRE));
        this.getTags().put(TagType.ATTACK, new Tag(6));
    }

    /**
     * 复制构造器（配置打在模板上，副本靠 {@code copy()} 白送）。
     *
     * @param other 被复制的技能
     */
    public BladeUnderBurial(BladeUnderBurial other) {
        super(other);
    }

    @Override
    public Skill copy() {
        return new BladeUnderBurial(this);
    }

    /**
     * 门槛：千冶·刃专用、<b>必须在【无量忿怒】里</b>、而且血量要 &gt; 1（设计原文）。
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
        return blade.isInfiniteFury() && blade.getHp() > 1 && super.canUse(fight, user);
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
     * <b>AI 评分加成</b>：能在结界里放出来 → 加 {@link #AI_PRIORITY} 分。
     * <p>
     * 它本身有 72% 生命上限的伤害，但伤害评分会按"目标血量/斩杀"算 —— 血量健康时可能输给普攻，
     * 而设计上它是【无量忿怒】期间的主要输出手段，所以给一个明确的优先分。
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
        if (!blade.isInfiniteFury() || blade.getHp() <= 1) {
            return 0;
        }
        return AI_PRIORITY;
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        if (user instanceof MortenaxBlade blade) {
            blade.gainEnergy(ENERGY_GAIN);
        }
        // ① 消耗生命上限 10%（不够就降到 1）—— 这是"消耗"，不走 heal
        long cost = (long) (user.getHpMax() * HP_COST_RATIO);
        if (user instanceof MortenaxBlade blade) {
            // 走 consumeHp：不足则降到 1（扣血规则集中在一处）
            blade.consumeHp(cost);
        } else {
            user.setHp(Math.max(1, user.getHp() - cost));
        }
        // ② 全体 72%
        List<LivingThing> targets = new ArrayList<>(enemies);
        for (LivingThing enemy : targets) {
            user.makeDamage(enemy, this);
        }
        // ③ 额外 4 段，每段随机打一个（倍率临时改成 24% 的副本 —— 不能改自己，模板是全局共享的）
        if (targets.isEmpty()) {
            return;
        }
        Skill extraHit = this.copy();
        extraHit.setHpMagnification(EXTRA_HIT_MAGNIFICATION);
        for (int i = 0; i < EXTRA_HITS; i++) {
            LivingThing victim = targets.get(RANDOM.nextInt(targets.size()));
            user.makeDamage(victim, extraHit);
        }
    }
}
