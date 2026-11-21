package cn.gfhnv.game.entity;

import cn.gfhnv.game.damage.Damage;
import cn.gfhnv.game.damage.DamageSource;
import cn.gfhnv.game.damage.DamageType;
import cn.gfhnv.game.event.DamageEvent;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.interfaces.IAdditionAttackBonus;
import cn.gfhnv.game.interfaces.IModifyDamage;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>实体战斗组件</b>（2026-11-21 第 88 批）：每个 {@link LivingThing} 各持有一个，
 * 专门放"战斗/伤害"这一摊 —— 伤害入口、减伤/易伤、伤害修正器都在这里，
 * 不再继续堆回 {@code LivingThing}。
 * <p>
 * <b>为什么需要它</b>：{@code LivingThing} 已经有属性、效果、背包、控制器等职责；
 * 继续往里加伤害方法会让它变成"什么都管的上帝类"。这个组件只依赖 owner，
 * 不反过来持有别的实体的引用；复制实体时组件也复制一份自己的列表。
 * <p>
 * <b>边界</b>：HP/治疗/死亡判定、{@code AttributeProfile}、效果容器、行动控制器
 * 仍然属于 {@link LivingThing}，本组件不接管它们。
 *
 * @author AI（DeepSeek）生成
 */
public final class CombatComponent {

    /**
     * 组件宿主（反向引用，不进 {@code /data} / 配置）。
     */
    @cn.gfhnv.game.data.NoData
    private final LivingThing owner;
    /**
     * 「基础减伤」来源键：旧写法 {@code setDamageAbsorbedPercent(...)} 用。
     */
    private final DamageReductionSource baseDamageReduction =
            new DamageReductionSource("基础减伤");
    /**
     * 减伤/易伤来源。正数进减伤乘区，负数进易伤乘区。
     */
    private final List<LivingThing.DamageReduction> damageReductions = new ArrayList<>();
    /**
     * 挨打一方的伤害修正器链（免死、血量下限等）。
     */
    private final List<IModifyDamage> damageModifiers = new ArrayList<>();

    /**
     * @param owner 宿主实体
     */
    public CombatComponent(LivingThing owner) {
        this.owner = owner;
    }

    /**
     * 复制另一份组件的战斗状态（列表各拿一份，不共享）。
     *
     * @param other 被复制的组件
     */
    public void copyFrom(CombatComponent other) {
        if (other == null) {
            return;
        }
        this.damageModifiers.clear();
        this.damageModifiers.addAll(other.damageModifiers);
        this.damageReductions.clear();
        this.damageReductions.addAll(other.damageReductions);
    }

    /* ------------------------------------------------------------------
     * 伤害入口
     * ------------------------------------------------------------------ */

    /**
     * 受到伤害：按 {@link DamageEvent} 算新生命值，经过伤害修正器后写回，并记录实际掉血。
     *
     * @param da 伤害事件
     */
    public void takeDamage(DamageEvent da) {
        boolean aliveBefore = owner.isAlive();
        long hpBefore = owner.getHp();
        long newHp = hpBefore - da.getDamage().getCalculateDamageResult();
        newHp = modifyIncomingDamage(newHp, da);
        owner.setHp(newHp);
        da.getDamage().setAppliedDamage(hpBefore - owner.getHp());
        // 2026-11-21 第 84 批：HSRActor 受击回能（AOE 只算 5，单挑 10）
        if (owner instanceof HSRActor hsr) {
            boolean aoe = da.getDamage().getDamageSource() instanceof Skill sourceSkill
                    && sourceSkill.getAims() > 1;
            hsr.gainEnergyOnDamaged(aoe);
        }
        // 击杀回能：击杀者必须是 HSRActor
        if (aliveBefore && !owner.isAlive()
                && da.getAttacker() instanceof HSRActor killer) {
            killer.gainEnergyOnKill();
        }
        // 打印不在这里做：攻击行的完整文案统一在 makeDamage 里打。
    }

    /**
     * 使用技能对目标造成伤害（构造 {@link DamageEvent} 并调用目标的 {@link #takeDamage}）。
     * <p>
     * 攻击行统一在这里打印，避免各技能漏打；返回这一刀的 {@link Damage}，
     * 需要"实际伤害"的机制直接读 {@link Damage#getAppliedDamage()}。
     *
     * @param attacked 被攻击目标
     * @param skill    使用的技能（可为 {@code null}）
     * @return 这一刀的 Damage
     */
    public Damage makeDamage(LivingThing attacked, Skill skill) {
        return makeDamage(attacked, skill, DamageType.SpecialSkill);
    }

    /**
     * 使用技能对目标造成伤害，并声明伤害类型（可以多个）。
     *
     * @param attacked    被攻击目标
     * @param skill       使用的技能；可为 {@code null}
     * @param damageTypes 伤害类型（不传按 {@link DamageType#SpecialSkill} 算）
     * @return 这一刀的 Damage
     */
    public Damage makeDamage(LivingThing attacked, Skill skill, DamageType... damageTypes) {
        // 2026-11-21 第 82 批（千冶·刃二魂）：我方角色施放终结技造成的伤害，被视为追加攻击。
        DamageType[] effectiveTypes = damageTypes;
        if (damageTypes != null && getTeamAdditionAttackDamageBonus() > 0
                && java.util.Arrays.asList(damageTypes).contains(DamageType.UltimateAttack)
                && !java.util.Arrays.asList(damageTypes).contains(DamageType.AdditionAttack)) {
            effectiveTypes = java.util.Arrays.copyOf(damageTypes, damageTypes.length + 1);
            effectiveTypes[damageTypes.length] = DamageType.AdditionAttack;
        }
        DamageEvent damageEvent = new DamageEvent(owner, attacked, skill, effectiveTypes);
        EventBus.post(damageEvent);
        attacked.takeDamage(damageEvent);
        EntityDisplay.printAttackLine(owner, attacked, skill,
                damageEvent.getDamage().getCalculateDamageResult(),
                damageEvent.getDamage().isCritical());
        return damageEvent.getDamage();
    }

    /**
     * 造成一次固定原始值的真实伤害（{@link ElementSort#UNIVERSAL}）。
     * <p>
     * 原始值由调用方给定，之后走完整公式：免增伤/抗性，吃防御/减伤/易伤，且不暴击。
     *
     * @param attacked    被攻击目标
     * @param skill       使用的技能（可为 {@code null}）
     * @param rawAmount   固定原始值（负值按 0 算）
     * @param damageTypes 伤害类型
     */
    public void makeTrueDamage(LivingThing attacked, Skill skill, double rawAmount, DamageType... damageTypes) {
        DamageEvent damageEvent = new DamageEvent(owner, attacked, skill,
                ElementSort.UNIVERSAL, rawAmount, damageTypes);
        EventBus.post(damageEvent);
        attacked.takeDamage(damageEvent);
        EntityDisplay.printAttackLine(owner, attacked, skill,
                damageEvent.getDamage().getCalculateDamageResult(), false);
    }

    /**
     * 造成一次固定原始值的 DOT 伤害（不暴击，但吃增伤/防御/抗性/易伤/减伤）。
     *
     * @param attacked    被攻击目标
     * @param source      伤害来源（效果 / 系统 / 技能）
     * @param elementSort 伤害元素
     * @param rawAmount   固定原始值（负值按 0 算）
     * @param damageTypes 伤害类型（通常传 {@link DamageType#DOT}）
     */
    public void makeDotDamage(LivingThing attacked, DamageSource source,
                              ElementSort elementSort, double rawAmount, DamageType... damageTypes) {
        DamageEvent damageEvent = new DamageEvent(owner, attacked, source, elementSort,
                rawAmount, damageTypes);
        EventBus.post(damageEvent);
        attacked.takeDamage(damageEvent);
        EntityDisplay.printAttackLine(owner, attacked, source,
                damageEvent.getDamage().getCalculateDamageResult(), false);
    }

    /**
     * 把一个「即将结算的新生命值」依次交给所有伤害修正器处理。
     *
     * @param newHp 已经算好的新生命值
     * @param da    伤害事件
     * @return 修正后的新生命值
     */
    public long modifyIncomingDamage(long newHp, DamageEvent da) {
        long result = newHp;
        for (IModifyDamage modifier : damageModifiers) {
            result = modifier.damageModify(result, da);
        }
        return result;
    }

    /* ------------------------------------------------------------------
     * 伤害修正器
     * ------------------------------------------------------------------ */

    /**
     * 追加一个伤害修正器（同一个对象不重复加）。
     *
     * @param modifier 修正器
     */
    public void addModifyDamage(IModifyDamage modifier) {
        if (modifier == null || modifier == IModifyDamage.DEFAULT) {
            return;
        }
        if (!damageModifiers.contains(modifier)) {
            damageModifiers.add(modifier);
        }
    }

    /**
     * 按对象身份移除一个伤害修正器。
     *
     * @param modifier 修正器
     * @return 有没有移除
     */
    public boolean removeModifyDamage(IModifyDamage modifier) {
        return modifier != null && damageModifiers.removeIf(each -> each == modifier);
    }

    /**
     * 清空所有伤害修正器。
     */
    public void clearModifyDamage() {
        damageModifiers.clear();
    }

    /**
     * @return 伤害修正器列表（副本）
     */
    public List<IModifyDamage> getModifyDamageList() {
        return new ArrayList<>(damageModifiers);
    }

    /**
     * 用一条修正器替换全部修正器链。
     *
     * @param modifyDamage 修正器
     */
    public void setModifyDamage(IModifyDamage modifyDamage) {
        damageModifiers.clear();
        addModifyDamage(modifyDamage);
    }

    /* ------------------------------------------------------------------
     * 减伤 / 易伤
     * ------------------------------------------------------------------ */

    /**
     * @return 总减伤比例（{@code 1 − 减伤乘区}，不含易伤）
     */
    public double getDamageAbsorbedPercent() {
        return 1 - getDamageTakenMultiplier();
    }

    /**
     * 设置「基础减伤」（替换旧值，不进易伤区）。
     *
     * @param damageAbsorbedPercent 减伤比例（0.25 = 少受 25%）
     */
    public void setDamageAbsorbedPercent(double damageAbsorbedPercent) {
        addDamageReduction(baseDamageReduction, damageAbsorbedPercent);
    }

    /**
     * 增加一个承伤修正来源：正数进减伤乘区（乘算），负数进易伤乘区（相加）。
     *
     * @param source  来源（按对象身份区分）
     * @param percent 正数 = 减伤；负数 = 易伤
     */
    public void addDamageReduction(Object source, double percent) {
        if (source == null) {
            return;
        }
        removeDamageReduction(source);
        double clamped = Math.max(-1, Math.min(1, percent));
        if (clamped != 0) {
            damageReductions.add(new LivingThing.DamageReduction(
                    source, LivingThing.describeReductionSource(source), clamped));
        }
    }

    /**
     * 移除一个承伤来源。
     *
     * @param source 来源
     * @return 有没有移除
     */
    public boolean removeDamageReduction(Object source) {
        return source != null
                && damageReductions.removeIf(reduction -> reduction.source() == source);
    }

    /**
     * 清空全部承伤来源。
     */
    public void clearDamageReductions() {
        damageReductions.clear();
    }

    /**
     * @return 全部承伤来源（副本）
     */
    public List<LivingThing.DamageReduction> getDamageReductions() {
        return new ArrayList<>(damageReductions);
    }

    /**
     * 减伤乘区：{@code Π(1 − 每个正减伤)}。
     *
     * @return 减伤乘区；没有减伤时是 1
     */
    public double getDamageTakenMultiplier() {
        double multiplier = 1;
        for (LivingThing.DamageReduction reduction : damageReductions) {
            if (reduction.percent() > 0) {
                multiplier *= (1 - reduction.percent());
            }
        }
        return Math.max(0, multiplier);
    }

    /**
     * 易伤乘区：{@code 1 + Σ|负减伤|}。
     *
     * @return 易伤乘区；没有易伤时是 1
     */
    public double getDamageVulnerabilityMultiplier() {
        double bonus = 0;
        for (LivingThing.DamageReduction reduction : damageReductions) {
            if (reduction.percent() < 0) {
                bonus += -reduction.percent();
            }
        }
        return 1 + bonus;
    }

    /**
     * 己方追加攻击伤害光环（同类取最大，不叠乘）。
     *
     * @return 追加攻击伤害加成（0.75 = +75%；没有返回 0）
     */
    public double getTeamAdditionAttackDamageBonus() {
        Fight fight = owner.getParticipateFight();
        if (fight == null || !fight.isOurSide(owner)) {
            return 0;
        }
        double bonus = 0;
        for (LivingThing ally : fight.getFighterList()) {
            if (ally != null && ally.isAlive()
                    && ally instanceof IAdditionAttackBonus provider) {
                bonus = Math.max(bonus, provider.getAdditionAttackDamageBonus());
            }
        }
        return bonus;
    }
}
