package cn.gfhnv.game.damage;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 一次伤害的载体。
 * <p>
 * <b>三段数值</b>，一条伤害从左到右流过：
 * <ol>
 *     <li>{@link #getRawDamageAmount()} —— <b>原始值</b>：技能倍率乘上属性之后的值，
 *     <b>没算</b>防御 / 减伤 / 抗性。它保留小数（倍率 × 生命上限经常不是整数）；</li>
 *     <li>{@link #getCalculateDamageResult()} —— <b>公式算完的值</b>：过完整条伤害公式（取整、夹到 ≥ 0）。</li>
 *     <li>{@link #getAppliedDamage()} —— <b>实际掉了多少血</b>：由 {@code setHp} 前后差算出来，
 *     会被生命值夹取 / 免死这类机制影响。</li>
 * </ol>
 * 为什么要分成三个：旧实现只有一个 {@code damageAmount}，它同时被当成
 * ① 日志里"应该造成多少"、② 结算时"扣多少血"、③ AI 预测的伤害 三种用途。
 * 分开之后日志、结算、AI 各读自己那一个，不再互相顶替。
 * <p>
 * <b>伤害类型</b>可以有多个（{@link #getDamageTypes()}），由技能在造成伤害时显式传入。
 * <b>伤害元素</b>是 {@link ElementSort#UNIVERSAL} 时表示<b>真实伤害</b>：
 * 免属性抗性、免单元素增伤、免全局增伤，但<b>防御与减伤照算</b>（见 {@link DamageCalculate}）。
 *
 * @author AI（DeepSeek）生成；设计稿作者：仓库作者
 */
public class Damage {

    /**
     * 伤害来源实体（谁打的）；系统伤害（没有施加者）时可以是 {@code null}。
     */
    private final LivingThing sourceEntity;
    /**
     * 受害实体（谁挨的）。
     */
    private final LivingThing targetEntity;
    /**
     * 伤害元素。{@link ElementSort#UNIVERSAL} = <b>真实伤害</b>。
     */
    private final ElementSort elementSort;
    /**
     * 伤害原因 / 方式（"用什么打的"）。
     */
    private final DamageSource damageSource;
    /**
     * 这次伤害的类型，至少一个。
     */
    private final Set<DamageType> damageTypes;
    /**
     * 原始值：倍率乘上属性之后、未算防御与减伤。
     */
    private final double rawDamageAmount;
    /**
     * 套完伤害公式之后的值（≥ 0，已取整）。
     */
    private long calculateDamageResult;
    /**
     * 实际造成的伤害（目标生命值的实际变化量）。结算前是 0。
     */
    private long appliedDamage;
    /**
     * 这一击有没有<b>触发暴击</b>（只在伤害计算里写；给攻击行显示"（暴击）"用）。
     * <p>
     * 它不是数值——倍率已经乘进 {@link #calculateDamageResult}；这个布尔只是让玩家看得见 5% 的暴击率真的生效了。
     */
    private boolean critical;

    /**
     * 由技能造成的一次伤害：伤害元素默认取<b>施法者的元素属性</b>（旧行为）。
     *
     * @param sourceEntity 伤害来源实体
     * @param targetEntity 受害实体
     * @param skill        造成伤害的技能（提供倍率与额外伤害）
     * @param damageTypes  伤害类型（不传按 {@link DamageType#SpecialSkill} 算）
     */
    public Damage(LivingThing sourceEntity, LivingThing targetEntity, Skill skill,
                  DamageType... damageTypes) {
        this(sourceEntity, targetEntity, skill, elementOf(sourceEntity), damageTypes);
    }

    /**
     * 同上，但<b>显式指定伤害元素</b>（例如"土属性角色放火技能"；
     * 传 {@link ElementSort#UNIVERSAL} 就是真实伤害）。
     *
     * @param sourceEntity 伤害来源实体
     * @param targetEntity 受害实体
     * @param skill        造成伤害的技能
     * @param elementSort  伤害元素
     * @param damageTypes  伤害类型（不传按 {@link DamageType#SpecialSkill} 算）
     */
    public Damage(LivingThing sourceEntity, LivingThing targetEntity, Skill skill,
                  ElementSort elementSort, DamageType... damageTypes) {
        this.sourceEntity = sourceEntity;
        this.targetEntity = targetEntity;
        this.elementSort = elementSort == null ? ElementSort.UNIVERSAL : elementSort;
        this.damageSource = skill;
        this.damageTypes = typesOf(damageTypes);
        DamageCalculate.Result result = DamageCalculate.resolve(this, skill);
        this.rawDamageAmount = result.rawDamageAmount();
        this.calculateDamageResult = result.calculateDamageResult();
    }

    /**
     * @param sourceEntity 来源实体（可为 {@code null}）
     * @return 来源实体的元素属性；没有来源或没设元素时按真实伤害（{@link ElementSort#UNIVERSAL}）
     */
    private static ElementSort elementOf(LivingThing sourceEntity) {
        if (sourceEntity == null || sourceEntity.getElementSort() == null) {
            return ElementSort.UNIVERSAL;
        }
        return sourceEntity.getElementSort();
    }

    /**
     * @param damageTypes 调用方传进来的类型
     * @return 不可变的类型集合；没传时是单元素 {@link DamageType#SpecialSkill}
     */
    private static Set<DamageType> typesOf(DamageType... damageTypes) {
        EnumSet<DamageType> set = EnumSet.noneOf(DamageType.class);
        if (damageTypes != null) {
            for (DamageType type : damageTypes) {
                if (type != null) {
                    set.add(type);
                }
            }
        }
        if (set.isEmpty()) {
            set.add(DamageType.SpecialSkill);
        }
        return Collections.unmodifiableSet(set);
    }

    /**
     * @return 伤害来源实体（谁打的）；系统伤害时是 {@code null}
     */
    public LivingThing getSourceEntity() {
        return sourceEntity;
    }

    /**
     * @return 受害实体（谁挨的）
     */
    public LivingThing getTargetEntity() {
        return targetEntity;
    }

    /**
     * @return 伤害元素；{@link ElementSort#UNIVERSAL} 表示真实伤害
     */
    public ElementSort getElementSort() {
        return elementSort;
    }

    /**
     * @return 真实伤害（元素是 {@link ElementSort#UNIVERSAL}）
     */
    public boolean isTrueDamage() {
        return elementSort == ElementSort.UNIVERSAL;
    }

    /**
     * @return 伤害原因 / 方式（"用什么打的"）
     */
    public DamageSource getDamageSource() {
        return damageSource;
    }

    /**
     * @return 伤害原因的可读名字；没有来源时返回 {@code "未知来源"}
     */
    public String getDamageSourceName() {
        if (damageSource == null) {
            return "未知来源";
        }
        String name = damageSource.sourceName();
        return name == null ? damageSource.getClass().getSimpleName() : name;
    }

    /**
     * @return 这次伤害的类型（不可变集合，至少一个）
     */
    public Set<DamageType> getDamageTypes() {
        return damageTypes;
    }

    /**
     * @param type 要判断的类型
     * @return 这次伤害是不是包含该类型
     */
    public boolean hasDamageType(DamageType type) {
        return damageTypes.contains(type);
    }

    /**
     * @return 原始值：倍率乘上属性之后、未算防御与减伤（可能是小数）
     */
    public double getRawDamageAmount() {
        return rawDamageAmount;
    }

    /**
     * @return 套完伤害公式之后的值（≥ 0，已取整）
     */
    public long getCalculateDamageResult() {
        return calculateDamageResult;
    }

    /**
     * @return 伤害公式算出来的值（旧字段名 {@code damageAmount} 的兼容入口，
     * 等价于 {@link #getCalculateDamageResult()}）
     */
    public long getDamageAmount() {
        return calculateDamageResult;
    }

    /**
     * 覆盖"公式算出来的值"。自测用它造"必死一击"；正常流程里不该有人调。
     *
     * @param damageAmount 新值
     */
    public void setDamageAmount(long damageAmount) {
        this.calculateDamageResult = damageAmount;
    }

    /**
     * @return 实际造成的伤害（目标生命值的实际变化量）；结算前是 0
     */
    /**
     * @return 这一击有没有触发暴击
     */
    public boolean isCritical() {
        return critical;
    }

    /**
     * 由 {@code DamageCalculate#resolve} 在算出倍率时回写。
     *
     * @param critical 是否暴击
     */
    public void setCritical(boolean critical) {
        this.critical = critical;
    }

    public long getAppliedDamage() {
        return appliedDamage;
    }

    /**
     * 写入"实际造成的伤害"。只由 {@link LivingThing#getDamage} 在结算时调用。
     *
     * @param appliedDamage 目标生命值的实际变化量
     */
    public void setAppliedDamage(long appliedDamage) {
        this.appliedDamage = appliedDamage;
    }

    @Override
    public String toString() {
        return "Damage{" +
                "来源=" + getDamageSourceName() +
                ", 类型=" + damageTypes +
                ", 元素=" + elementSort +
                ", 原始=" + rawDamageAmount +
                ", 计算=" + calculateDamageResult +
                ", 实际=" + appliedDamage +
                '}';
    }
}
