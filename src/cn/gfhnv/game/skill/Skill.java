package cn.gfhnv.game.skill;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.DamageEvent;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.mana.Mana;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 技能基类。
 * <p>
 * 技能是战斗中可执行的动作单元，由以下核心要素组成：
 * <ul>
 *     <li><b>倍率</b>：生命值倍率（hpMagnification）、攻击力倍率（atkMagnification）、防御力倍率（defMagnification），
 *     用于代入 {@link cn.gfhnv.game.damage.DamageCalculate#calculate} 计算伤害；</li>
 *     <li><b>目标数（aims）</b>：{@code 0}=作用于自身、{@code -1}=作用于全体、正数=选择 N 个目标；</li>
 *     <li><b>冷却</b>：coolDown 为总冷却回合数，nowCoolDown 为当前剩余冷却；</li>
 *     <li><b>消耗</b>：consumedMana 指定释放所需消耗的元素法力（为 {@code null} 时表示无消耗）；</li>
 *     <li><b>目标阵营</b>：isForEnemies 决定技能默认作用对象是敌方还是己方；</li>
 *     <li><b>AI 权重</b>：tags 记录该技能对应的行为 Tag 权重，供思考系统（ThinkingController）决策使用。</li>
 * </ul>
 * <p>
 * 子类通常需要重写 {@link #comeToEffect(Fight, LivingThing)} 或
 * {@link #comeToEffect(Fight, LivingThing, List)} 来实现技能效果，并使用 {@link #canUse(Fight, LivingThing)}
 * 系列方法自定义释放条件。两个重载方法只需重写其中一个，尽量不直接调用 comeToEffect。
 *
 * @author gfhnv
 */
public class Skill {
    private String name;
    private String description;
    private double hpMagnification = 0;
    private double atkMagnification = 0;
    private double defMagnification = 0;
    private int aims;
    private int coolDown;
    private int nowCoolDown = 0;
    private boolean isForEnemies = true;
    private Mana consumedMana;
    private long extraDamage = 0;//多倍率时把其他倍率计算的结果加到这里.伤害计算后重置为零
    private Map<TagType, Tag> tags = new EnumMap<>(TagType.class);

    /**
     * 复制构造器（深拷贝）。用于将一个技能实例复制为独立的新实例，
     * 避免不同实体之间共享同一个可变技能对象。
     *
     * @param skill 被复制的技能
     */
    public Skill(Skill skill) {
        this.name = skill.getName();
        this.description = skill.getDescription();
        this.hpMagnification = skill.getHpMagnification();
        this.atkMagnification = skill.getAtkMagnification();
        this.defMagnification = skill.getDefMagnification();
        this.aims = skill.getAims();
        this.coolDown = skill.getCoolDown();
        this.nowCoolDown = skill.getNowCoolDown();
        this.isForEnemies = skill.isForEnemies();
        this.consumedMana = skill.consumedMana;
        this.extraDamage = skill.getExtraDamage();

    }

    /**
     * 构造一个基础技能。
     *
     * @param name             技能名称
     * @param description      技能描述
     * @param hpMagnification  生命值倍率（伤害计算中乘以使用者生命上限的系数）
     * @param atkMagnification 攻击力倍率（伤害计算中乘以使用者攻击力的系数）
     * @param defMagnification 防御力倍率（伤害计算中乘以使用者防御力的系数）
     * @param aims             目标数量：0=自身；-1=全体；正数=指定数量的目标
     */
    public Skill(String name, String description, double hpMagnification, double atkMagnification, double defMagnification, int aims) {
        this.name = name;
        this.description = description;
        this.hpMagnification = hpMagnification;
        this.atkMagnification = atkMagnification;
        this.defMagnification = defMagnification;
        this.aims = aims;
    }


    /**
     * 复制技能。默认调用复制构造器 {@link #Skill(Skill)}；
     * 子类若持有额外可变状态，应重写此方法返回正确的副本。
     *
     * @return 技能的深拷贝实例
     */
    public Skill copy() {
        return new Skill(this);
    }

    /**
     * @return 该技能是否默认作用于敌方。{@code true} 表示目标是敌人，{@code false} 表示目标是己方。
     */
    public boolean isForEnemies() {
        return isForEnemies;
    }

    /**
     * 设置技能默认的目标阵营。
     *
     * @param forEnemies {@code true} 表示目标是敌人，{@code false} 表示目标是己方
     */
    public void setForEnemies(boolean forEnemies) {
        isForEnemies = forEnemies;
    }

    /**
     * @return 当前剩余冷却回合数（0 表示冷却已结束，可以使用）
     */
    public int getNowCoolDown() {
        return nowCoolDown;
    }

    /**
     * 设置当前剩余冷却回合数。
     *
     * @param nowCoolDown 剩余冷却回合数
     */
    public void setNowCoolDown(int nowCoolDown) {
        this.nowCoolDown = nowCoolDown;
    }

    /**
     * @return 技能的总冷却回合数
     */
    public int getCoolDown() {
        return coolDown;
    }

    /**
     * 设置技能的总冷却回合数。
     *
     * @param coolDown 总冷却回合数
     */
    public void setCoolDown(int coolDown) {
        this.coolDown = coolDown;
    }

    /**
     * @return 技能的目标数量：0=自身；-1=全体；正数=指定数量的目标
     */
    public int getAims() {
        return aims;
    }

    /**
     * 设置技能的目标数量。
     *
     * @param aims 目标数量：0=自身；-1=全体；正数=指定数量的目标
     */
    public void setAims(int aims) {
        this.aims = aims;
    }

    /**
     * 判断技能是否可以在指定战斗中由 user 释放（带目标列表版本）。
     * <p>
     * 判定条件：冷却已结束（nowCoolDown <= 0）且法力足够。若 {@link #getConsumedMana()} 为 {@code null} 表示无法力消耗。
     * 子类可通过重写此方法追加自定义释放条件。
     *
     * @param fight   当前战斗上下文
     * @param user    技能使用者
     * @param enemies 手动传入的目标列表（部分子类需要据此判定，可为 {@code null}）
     * @return 是否可以释放
     */
    public boolean canUse(Fight fight, LivingThing user, List<LivingThing> enemies) {
        boolean canUse = false;
        boolean canUseMana = false;
        for (Mana mana : user.getManas()) {
            if (this.getConsumedMana() == null) {
                canUseMana = true;
                break;
            }
            if (consumedMana.getElementSort().equals(ElementSort.UNIVERSAL)) {
                if (mana.getAmount() >= consumedMana.getAmount()) {
                    canUseMana = true;

                    break;
                }
            }
            if (mana.getElementSort().equals(consumedMana.getElementSort())) {
                canUseMana = mana.getAmount() >= consumedMana.getAmount();

                break;
            }
        }
        if (canUseMana && nowCoolDown <= 0) {
            canUse = true;
        }
        return canUse;
    }

    /**
     * 判断技能是否可以在指定战斗中由 user 释放（无目标列表版本）。
     * <p>
     * 判定条件与 {@link #canUse(Fight, LivingThing, List)} 相同。适用于无需关心目标即可判定的技能（例如作用于自身的技能）。
     * <b>注意</b>：调用方应在 canUse 返回 {@code true} 并执行 {@link #use(Fight, LivingThing)} 后消耗法力。
     *
     * @param fight 当前战斗上下文
     * @param user  技能使用者
     * @return 是否可以释放
     */
    public boolean canUse(Fight fight, LivingThing user) {
        boolean canUse = false;
        boolean canUseMana = false;
        for (Mana mana : user.getManas()) {
            if (this.getConsumedMana() == null) {
                canUseMana = true;
                break;
            }
            if (consumedMana.getElementSort().equals(ElementSort.UNIVERSAL)) {
                if (mana.getAmount() >= consumedMana.getAmount()) {
                    canUseMana = true;

                    break;
                }
            }
            if (mana.getElementSort().equals(consumedMana.getElementSort())) {
                canUseMana = mana.getAmount() >= consumedMana.getAmount();

                break;
            }
        }
        if (canUseMana && nowCoolDown <= 0) {
            canUse = true;
        }
        return canUse;
    }

    /**
     * 尝试使用技能（带目标列表版本）。
     * <p>
     * 若 {@link #canUse(Fight, LivingThing, List)} 通过，则：
     * <ol>
     *     <li>调用 {@link #comeToEffect(Fight, LivingThing, List)} 执行技能效果；</li>
     *     <li>扣除所需的法力（若存在消耗）；</li>
     *     <li>将当前冷却设置为 {@code coolDown + 1}（进入冷却）。</li>
     * </ol>
     *
     * @param fight   当前战斗上下文
     * @param user    技能使用者
     * @param enemies 技能实际作用的敌人/目标列表
     * @return 是否成功释放
     */
    public boolean use(Fight fight, LivingThing user, List<LivingThing> enemies) {
        if (this.canUse(fight, user, enemies)) {
            this.comeToEffect(fight, user, enemies);
            for (Mana mana : user.getManas()) {
                if (this.getConsumedMana() == null) {
                    break;
                }
                if (consumedMana.getElementSort().equals(ElementSort.UNIVERSAL)) {
                    if (mana.getAmount() >= consumedMana.getAmount()) {
                        if (nowCoolDown <= 0) {
                            mana.setAmount(mana.getAmount() - consumedMana.getAmount());
                        }
                        break;
                    }
                }
                if (mana.getElementSort().equals(consumedMana.getElementSort())) {
                    if (nowCoolDown <= 0) {
                        mana.setAmount(mana.getAmount() - consumedMana.getAmount());
                    }
                    break;
                }
            }
            this.setNowCoolDown(this.getCoolDown() + 1);
            return true;
        }
        return false;
    }

    /**
     * 尝试使用技能（无目标列表版本，适用于作用于自身或无需目标的情况）。
     * <p>
     * 若 {@link #canUse(Fight, LivingThing)} 通过，则执行效果、扣除法力，
     * 并将当前冷却设置为 {@code coolDown}。与三参版本的差异在于冷却设置方式。
     *
     * @param fight 当前战斗上下文
     * @param user  技能使用者
     * @return 是否成功释放
     */
    public boolean use(Fight fight, LivingThing user) {
        if (this.canUse(fight, user)) {
            this.comeToEffect(fight, user);

            for (Mana mana : user.getManas()) {
                if (this.getConsumedMana() == null) {
                    break;
                }
                if (consumedMana.getElementSort().equals(ElementSort.UNIVERSAL)) {
                    if (mana.getAmount() >= consumedMana.getAmount()) {
                        if (nowCoolDown <= 0) {
                            mana.setAmount(mana.getAmount() - consumedMana.getAmount());
                        }
                        break;
                    }
                }
                if (mana.getElementSort().equals(consumedMana.getElementSort())) {
                    if (nowCoolDown <= 0) {
                        mana.setAmount(mana.getAmount() - consumedMana.getAmount());
                    }
                    break;
                }
            }
            this.setNowCoolDown(this.getCoolDown());
            return true;
        }
        return false;
    }

    /**
     * 预测使用该技能对目标造成的伤害值（不实际结算）。
     * <p>
     * 内部通过构造 {@link DamageEvent} 走完整的伤害计算流程，并应用目标的伤害修正接口
     * （{@link cn.gfhnv.game.interfaces.IModifyDamage}）后计算差值。供 AI 决策系统评估技能收益使用。
     *
     * @param attackedEntity 被攻击方
     * @param attacker       攻击方（技能使用者）
     * @return 预测造成的伤害值
     */
    public long getAnticipatedDamage(LivingThing attackedEntity, LivingThing attacker) {
        DamageEvent da = new DamageEvent(attacker, attackedEntity, this);
        long newHp = attackedEntity.getHp() - da.getDamage().getDamageAmount();
        newHp = attackedEntity.getModifyDamage().damageModify(newHp, da);
        return attackedEntity.getHp() - newHp;
    }

    /**
     * 技能效果入口（无目标列表版本）。
     * <p>
     * 子类应根据自身需求重写本方法或 {@link #comeToEffect(Fight, LivingThing, List)} 中的<b>其中一个</b>来实现技能逻辑。
     * 不要在 comeToEffect 中直接调用它自身（请通过 {@link #use(Fight, LivingThing)} 进入）。
     *
     * @param fight 当前战斗上下文
     * @param user  技能使用者
     */
    public void comeToEffect(Fight fight, LivingThing user) {
    }

    /**
     * 技能效果入口（带目标列表版本）。
     * <p>
     * 子类应根据自身需求重写本方法或 {@link #comeToEffect(Fight, LivingThing)} 中的<b>其中一个</b>来实现技能逻辑，
     * 例如对 {@code enemies} 中的目标逐一施加伤害。推荐优先重写本方法。
     *
     * @param fight   当前战斗上下文
     * @param user    技能使用者
     * @param enemies 技能实际作用的敌人/目标列表
     */
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
    }

    /**
     * @return 技能名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置技能名称。
     *
     * @param name 技能名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return 技能描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置技能描述。
     *
     * @param description 技能描述
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return 生命值倍率（伤害计算中乘以使用者生命上限的系数）
     */
    public double getHpMagnification() {
        return hpMagnification;
    }

    /**
     * 设置生命值倍率。
     *
     * @param hpMagnification 生命值倍率
     */
    public void setHpMagnification(double hpMagnification) {
        this.hpMagnification = hpMagnification;
    }

    /**
     * @return 攻击力倍率（伤害计算中乘以使用者攻击力的系数）
     */
    public double getAtkMagnification() {
        return atkMagnification;
    }

    /**
     * 设置攻击力倍率。
     *
     * @param atkMagnification 攻击力倍率
     */
    public void setAtkMagnification(double atkMagnification) {
        this.atkMagnification = atkMagnification;
    }

    /**
     * @return 防御力倍率（伤害计算中乘以使用者防御力的系数）
     */
    public double getDefMagnification() {
        return defMagnification;
    }

    /**
     * 设置防御力倍率。
     *
     * @param defMagnification 防御力倍率
     */
    public void setDefMagnification(double defMagnification) {
        this.defMagnification = defMagnification;
    }

    /**
     * @return 释放技能所需消耗的元素法力（{@code null} 表示无消耗）
     */
    public Mana getConsumedMana() {
        return consumedMana;
    }

    /**
     * 设置释放技能所需消耗的元素法力。
     *
     * @param consumedMana 消耗的法力（{@code null} 表示无消耗）
     */
    public void setConsumedMana(Mana consumedMana) {
        this.consumedMana = consumedMana;
    }

    /**
     * @return 技能附加的固定额外伤害值
     */
    public long getExtraDamage() {
        return extraDamage;
    }

    /**
     * 设置技能附加的固定额外伤害值。
     *
     * @param extraDamage 固定额外伤害值
     */
    public void setExtraDamage(long extraDamage) {
        this.extraDamage = extraDamage;
    }

    /**
     * @return 技能对应的行为 Tag 权重表，供思考系统（ThinkingController）决策使用
     */
    public Map<TagType, Tag> getTags() {
        return tags;
    }
}
