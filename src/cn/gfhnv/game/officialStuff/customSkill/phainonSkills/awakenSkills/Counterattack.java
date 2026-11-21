package cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills;

import cn.gfhnv.game.damage.DamageType;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEntity.players.Phainon;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.skill.SkillCoefficientTunable;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;

import java.util.*;

/**
 * 灾厄·弑魂焚诏的反击（白厄变身期的反击）。
 * <p>
 * <b>2026-11-21 第 82 批按作者贴的游戏原文重做</b>：
 * <ul>
 *     <li>主刀：对<b>敌方全体</b>造成伤害；</li>
 *     <li>额外：再打 <b>4 段</b>，每段对敌方<b>随机单体</b>；</li>
 *     <li>每层【弑魂之炽】让本次反击的倍率提高"原倍率的 20%"（主刀与额外段都吃）；</li>
 *     <li>伤害类型 = <b>战技 + 追加攻击</b>（原文："视为造成了战技伤害以及追击攻击"）；</li>
 *     <li>结算完把【弑魂之炽】清零、解除对应减伤、清掉"还会行动"的名单。</li>
 * </ul>
 * <b>倍率数值沿用改造前的具名系数</b>（作者：倍率按现有代码；原文里的 20%/40%、15%/30% 不照搬）。
 * <p>
 * 触发时机由 {@link CalamitySoulscorchEdict} 负责：被标记的敌人全部行动后发动；
 * 额外回合开始时仍持有【弑魂之炽】也立即发动（见 {@code UltimateAttack} 的额外回合首动作）。
 */
public class Counterattack extends Skill implements SkillCoefficientTunable {

    /**
     * 具名系数：施法者回血比例（占自身生命上限）。
     */
    private static final String COEFF_HEAL_RATIO = "healRatio";
    /**
     * 具名系数：追击那几刀的基准倍率（再按层数放大）。
     */
    private static final String COEFF_EXTRA_HIT_MAGNIFICATION = "extraHitMagnification";
    /**
     * 具名系数：每层【弑魂之炽】给多少倍率。
     */
    private static final String COEFF_MAGNIFICATION_PER_STACK = "magnificationPerStack";
    /**
     * 具名系数：主刀之后追击几刀。
     */
    private static final String COEFF_EXTRA_HITS = "extraHits";

    /**
     * 施法者回血比例（占自身生命上限）。
     * <p>
     * ⚠️ 游戏原文没有这一段；这是改造前就有、且作者要求"倍率按现有代码"时保留的旧手感。
     */
    private double healRatio = 0.2;
    /**
     * 追击那几刀的基准倍率（再按层数放大）。
     */
    private double extraHitMagnification = 0.3;
    /**
     * 每层【弑魂之炽】给多少倍率（主刀与追击都吃这一份）。
     */
    private double magnificationPerStack = 0.2;
    /**
     * 主刀之后追击几刀（原文：额外 4 次）。
     */
    private int extraHits = 4;

    public Counterattack() {
        super("灾厄-弑魂焚诏的反击", "灾厄-弑魂焚诏的反击", 0, 1, 0, -1);
        this.setCoolDown(0);
        this.getTags().put(TagType.ATTACK, new Tag(1));
    }

    /**
     * 复制构造器。
     * <p>
     * {@link #copy()} 必须走这里，不能返回 {@code new Counterattack()}：那样会把
     * 冷却/消耗/权重还原成构造器里的出厂值，于是打在模板上的配置（{@code SkillData.json}）
     * <b>永远传不到选人时复制出去的副本</b>。
     *
     * @param other 被复制的技能
     */
    public Counterattack(Counterattack other) {
        super(other);
        this.healRatio = other.healRatio;
        this.extraHitMagnification = other.extraHitMagnification;
        this.magnificationPerStack = other.magnificationPerStack;
        this.extraHits = other.extraHits;
    }

    /**
     * 过滤出还活着的目标。
     *
     * @param things 候选目标（允许为 {@code null}）
     * @return 其中 {@link LivingThing#isAlive()} 为真的那些；<b>新列表</b>，不改动入参
     */
    private static List<LivingThing> aliveOf(List<LivingThing> things) {
        List<LivingThing> alive = new ArrayList<>();
        if (things == null) {
            return alive;
        }
        for (LivingThing thing : things) {
            if (thing != null && thing.isAlive()) {
                alive.add(thing);
            }
        }
        return alive;
    }

    @Override
    public Map<String, Double> coefficientValues() {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put(COEFF_HEAL_RATIO, healRatio);
        values.put(COEFF_EXTRA_HIT_MAGNIFICATION, extraHitMagnification);
        values.put(COEFF_MAGNIFICATION_PER_STACK, magnificationPerStack);
        values.put(COEFF_EXTRA_HITS, (double) extraHits);
        return values;
    }

    @Override
    public void setCoefficientValue(String name, double value) {
        switch (name) {
            case COEFF_HEAL_RATIO -> this.healRatio = value;
            case COEFF_EXTRA_HIT_MAGNIFICATION -> this.extraHitMagnification = value;
            case COEFF_MAGNIFICATION_PER_STACK -> this.magnificationPerStack = value;
            case COEFF_EXTRA_HITS -> this.extraHits = (int) value;
            default -> throw new IllegalArgumentException("灾厄-弑魂焚诏的反击没有叫「" + name + "」的具名系数");
        }
    }

    @Override
    public Skill copy() {
        return new Counterattack(this);
    }

    /**
     * 释放反击。
     * <p>
     * 主刀打全体；随后额外 {@link #extraHits} 段随机单体。两种情况都用
     * <b>战技 + 追加攻击</b> 两种伤害类型（原文："被视为造成了战技伤害以及追击攻击"）。
     *
     * @param fight   当前战斗
     * @param user    施法者（白厄）
     * @param enemies 敌方全体
     */
    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        int soulscorch = 0;
        // 旧手感：回血（原文没有，保留为可配置项）
        user.heal((long) (user.getHpMax() * healRatio));
        if (user instanceof Phainon phainon) {
            soulscorch = phainon.getSoulscorch();
            phainon.setSoulscorch(0);
            if (phainon.isAbsorbDamage()) {
                // 移除「灾厄」那一份减伤（按来源键，幂等）
                phainon.removeDamageReduction(Phainon.SOULSCORCH_DAMAGE_REDUCTION);
                phainon.setAbsorbDamage(false);
            }
            for (Skill skill : user.getController().getSkills()) {
                if (skill instanceof CalamitySoulscorchEdict soulscorchEdict) {
                    soulscorchEdict.getWillAct().clear();
                }
            }
        }

        double scale = 1 + soulscorch * magnificationPerStack;
        double originalMagnification = this.getAtkMagnification();
        // 主刀：敌方全体，倍率 = 原倍率 × (1 + 层数×20%)
        this.setAtkMagnification(originalMagnification * scale);
        List<LivingThing> aliveEnemies = aliveOf(enemies);
        for (LivingThing livingThing : aliveEnemies) {
            user.makeDamage(livingThing, this, DamageType.SpecialSkill, DamageType.AdditionAttack);
        }

        // 额外 4 段：随机单体，倍率 = 额外基准倍率 × (1 + 层数×20%)
        // 用副本，避免改到模板/全局共享的这一个实例。
        Skill extraHit = this.copy();
        extraHit.setAtkMagnification(extraHitMagnification * scale);
        for (int i = 0; i < extraHits; i++) {
            aliveEnemies.removeIf(target -> !target.isAlive());
            if (aliveEnemies.isEmpty()) {
                // 目标全倒下了：剩下的段数没有意义（旧写法在这里会抛 NoSuchElementException）
                break;
            }
            Collections.shuffle(aliveEnemies);
            user.makeDamage(aliveEnemies.getFirst(), extraHit,
                    DamageType.SpecialSkill, DamageType.AdditionAttack);
        }
        this.setAtkMagnification(originalMagnification);
    }
}
