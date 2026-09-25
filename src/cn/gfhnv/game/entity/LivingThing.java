package cn.gfhnv.game.entity;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entityController.FixOrderController;
import cn.gfhnv.game.entityController.PlayerController;
import cn.gfhnv.game.entityController.UniversalController;
import cn.gfhnv.game.event.DamageEvent;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.HpLossEvent;
import cn.gfhnv.game.event.HpRestorationEvent;
import cn.gfhnv.game.interfaces.IDefenceIgnore;
import cn.gfhnv.game.interfaces.IModifyDamage;
import cn.gfhnv.game.interfaces.IShowSpecialMes;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.ActionSignal;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.fight.TurnEntry;
import cn.gfhnv.game.system.mana.Mana;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;
import cn.gfhnv.game.system.thinkingSystem.ThinkingController;
import cn.gfhnv.game.system.thinkingSystem.ThinkingControllerAI;
import cn.gfhnv.game.world.World;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 生物基类。表示游戏中具有生命值、攻击、防御、速度、五行抗性、元素法力等战斗属性的实体，
 * 继承自 {@link Entity}。
 * <p>
 * 核心特性：
 * <ul>
 *     <li><b>战斗属性</b>：生命值（hp）、攻击（attack）、防御（defence）、速度（speed）、生命上限（hpMax）、
 *     暴击率/爆伤（getCriticalRATE / criticalDMG）；</li>
 *     <li><b>五行系统</b>：拥有金木水火土五种元素抗性、穿透与元素法力（{@link Mana}），
 *     伤害计算时根据元素属性（{@link ElementSort}）取对应抗性/增伤/穿透；</li>
 *     <li><b>效果列表</b>：可携带多个 {@link Effect}（Buff/Debuff），由 {@link #addEffect} 管理叠加；</li>
 *     <li><b>增强属性</b>：attack/defence/speed/hp/critical 等均有对应的百分比与固定值增强字段，getter 会综合返回最终值；</li>
 *     <li><b>控制器</b>：通过 {@link UniversalController}（或其子类
 *     {@link PlayerController}、{@link ThinkingController}、{@link ThinkingControllerAI}）决定每回合行动；</li>
 *     <li><b>伤害修正</b>：{@link IModifyDamage} 接口允许对受到的伤害做自定义修正；</li>
 *     <li><b>特殊状态显示</b>：{@link IShowSpecialMes} 用于在回合内输出角色的特殊状态信息。</li>
 * </ul>
 * 提供大量 {@code facSetXxx} 链式工厂方法，方便快速构建生物实例。
 *
 * @author gfhnv
 */
public class LivingThing extends Entity {
    /**
     * 「基础减伤」在 {@link #damageReductions} 里的来源键。
     * <p>
     * 只有 {@link #setDamageAbsorbedPercent(double)} 用它，用来兼容旧写法（直接设置一个总减伤）。
     */
    private static final Object BASE_DAMAGE_REDUCTION = new Object();
    /**
     * 减伤来源（<b>乘算叠加</b>）：承伤倍率 = Π(1 − 每个减伤)，见 {@link #getDamageTakenMultiplier()}。
     */
    private final List<DamageReduction> damageReductions = new ArrayList<>();
    private final List<IModifyDamage> damageModifiers = new ArrayList<>();
    public long extraDamage = 0;
    private IShowSpecialMes showSpecialMes;
    private double fireResistance, waterResistance, metalResistance, woodResistance, dirtResistance, criticalDMG, getCriticalRATE;
    private long hp, defence, speed, attack, hpMax;
    private boolean Alive = true;
    private List<Effect> entityEffectList = new ArrayList<>();
    private double penetration = 0;//全属性穿透
    private double metalPenetration, woodPenetration, waterPenetration, firePenetration, dirtPenetration;
    private double hpGrowNumber;
    private double atkGrowNumber;
    private double dfkGrowNumber;
    private ElementSort elementSort;
    private double metalManaGrowNumber;
    private double woodManaGrowNumber;
    private double waterManaGrowNumber;
    private double fireManaGrowNumber;
    private double dirtManaGrowNumber;
    private double attackEnhancePercent, defenceEnhancePercent, speedEnhancePercent, hpEnhancePercent, criticalDMGEnhancePercent, criticalDMGEnhanceAmount, criticalRateEnhancePercent, criticalRateEnhanceAmount;
    private long attackEnhanceAmount, defenceEnhanceAmount, speedEnhanceAmount, hpEnhanceAmount;
    private List<Mana> manas = new ArrayList<>();//一个实体可以拥有多个Mana
    private double enhance;//全属性
    private double metalDamageEnhance, woodDamageEnhance, waterDamageEnhance, fireDamageEnhance, dirtDamageEnhance;
    private double defenseLoss;
    private Fight participateFight;
    private TurnEntry presentTurn;
    private UniversalController controller;
    private String description;
    private double individualMultipleArea = 1;
    /**
     * 是否正在做伤害试算（只读预测）。见 {@link #modifyIncomingDamage}。
     */
    private boolean anticipating = false;

    public LivingThing() {

    }

    /**
     * 复制构造器（深拷贝）。复制大部分战斗属性、效果列表、法力、背包、Tag 权重，
     * 并根据原控制器类型重建对应的控制器（PlayerController / ThinkingControllerAI / UniversalController）。
     * <p>
     * 战斗上下文（participateFight）与当前回合（presentTurn）不会被复制。
     *
     * @param other 被复制的生物
     */
    public LivingThing(LivingThing other) {
        super(other.getName(), other.getId(), other.getLevel());
        this.fireResistance = other.fireResistance;
        this.waterResistance = other.waterResistance;
        this.metalResistance = other.metalResistance;
        this.elementSort = other.elementSort;
        this.woodResistance = other.woodResistance;
        this.dirtResistance = other.dirtResistance;
        this.description = other.description;
        this.speed = other.speed;
        this.setType(other.getType());
        this.Alive = other.Alive;
        this.defenseLoss = other.defenseLoss;
        this.enhance = other.enhance;
        this.setHpGrowNumber(other.getHpGrowNumber());
        this.setAtkGrowNumber(other.getAtkGrowNumber());
        this.setDfkGrowNumber(other.getDfkGrowNumber());
        this.setMetalManaGrowNumber(other.getMetalManaGrowNumber());
        this.setWoodManaGrowNumber(other.getWoodManaGrowNumber());
        this.setWaterManaGrowNumber(other.getWaterManaGrowNumber());
        this.setFireManaGrowNumber(other.getFireManaGrowNumber());
        this.setDirtManaGrowNumber(other.getDirtManaGrowNumber());
        this.hp = other.hp;
        this.defence = other.defence;
        this.attack = other.attack;
        this.hpMax = other.hpMax;
        this.setShowSpecialMes(other.getShowSpecialMes());
        this.damageModifiers.addAll(other.damageModifiers);
        this.criticalDMG = other.criticalDMG;
        this.getCriticalRATE = other.getCriticalRATE;
        this.entityEffectList = new ArrayList<>(other.entityEffectList);
        this.penetration = other.penetration;
        this.damageReductions.addAll(other.damageReductions);
        this.participateFight = null;
        this.presentTurn = null;
        if (other.getController() instanceof PlayerController) {
            this.controller = new PlayerController(other.controller.getSkills(), this);
        } else if (other.getController() instanceof ThinkingControllerAI) {
            this.controller = new ThinkingControllerAI(other.controller.getSkills(), this);
        } else if (other.getController() instanceof FixOrderController fixOrderController) {
            // 固定顺序的怪物：复制之后必须还是固定顺序，否则一进战斗就退化成随机 AI
            this.controller = new FixOrderController(fixOrderController, this);
        } else {
            this.controller = new UniversalController(other.controller, this);
        }
        if (this.controller.getiInitialize() != null) this.controller.getiInitialize().initialize(this.controller);
        if (!other.getManas().isEmpty()) {
            for (Mana mana : other.getManas()) {
                this.getManas().add(new Mana(mana));
            }
        }
        this.setInventory(other.getInventory().copy());
        if (!other.getTags().isEmpty()) {
            Map<TagType, Tag> newMap = new EnumMap<>(TagType.class);
            for (Map.Entry<TagType, Tag> entry : other.getTags().entrySet()) {
                newMap.put(entry.getKey(), entry.getValue().copy());
            }
            this.setTags(newMap);
        }
    }

    /**
     * 构造一个完整的生物实例，按等级与成长系数初始化属性，并按元素属性初始化五行法力。
     *
     * @param name            名称
     * @param id              唯一标识
     * @param fireResistance  火抗性
     * @param waterResistance 水抗性
     * @param metalResistance 金抗性
     * @param woodResistance  木抗性
     * @param dirtResistance  土抗性
     * @param speed           速度
     * @param l               等级
     * @param type            类型
     * @param hp              生命成长系数
     * @param atk             攻击成长系数
     * @param defence         防御成长系数
     * @param yu              元素属性（金木水火土）
     */
    public LivingThing(String name, String id, double fireResistance, double waterResistance, double metalResistance, double woodResistance, double dirtResistance, long speed, long l, String type, double hp, double atk, double defence, ElementSort yu) {
        super(name, id, l);
        this.elementSort = yu;
        this.setHpGrowNumber(hp);
        this.setAtkGrowNumber(atk);
        this.setDfkGrowNumber(defence);
        this.fireResistance = fireResistance;
        this.waterResistance = waterResistance;
        this.metalResistance = metalResistance;
        this.woodResistance = woodResistance;
        this.dirtResistance = dirtResistance;
        this.setType(type);
        Alive = true;
        this.speed = speed;
        this.defenseLoss = 0;
        this.enhance = 0;
        this.hp = (long) ((l - 1) * getHpGrowNumber() + 200);
        this.defence = (long) ((l - 1) * getDfkGrowNumber() + 200);
        this.attack = (long) (110 + getAtkGrowNumber() * (l - 1));
        this.hpMax = this.hp;
        switch (this.getElementSort()) {
            case METAL -> {
                this.setMetalManaGrowNumber(20);
                this.setWoodManaGrowNumber(4);
                this.setWaterManaGrowNumber(10);
                this.setFireManaGrowNumber(1);
                this.setDirtManaGrowNumber(10);
            }
            case WOOD -> {
                this.setMetalManaGrowNumber(1);
                this.setWoodManaGrowNumber(20);
                this.setWaterManaGrowNumber(10);
                this.setFireManaGrowNumber(10);
                this.setDirtManaGrowNumber(4);
            }
            case WATER -> {
                this.setMetalManaGrowNumber(10);
                this.setWoodManaGrowNumber(10);
                this.setWaterManaGrowNumber(20);
                this.setFireManaGrowNumber(4);
                this.setDirtManaGrowNumber(1);
            }
            case FIRE -> {
                this.setMetalManaGrowNumber(4);
                this.setWoodManaGrowNumber(10);
                this.setWaterManaGrowNumber(1);
                this.setFireManaGrowNumber(20);
                this.setDirtManaGrowNumber(10);
            }
            case DIRT -> {
                this.setMetalManaGrowNumber(10);
                this.setWoodManaGrowNumber(1);
                this.setWaterManaGrowNumber(4);
                this.setFireManaGrowNumber(10);
                this.setDirtManaGrowNumber(20);
            }
        }
        this.initialMana();
    }

    /**
     * 构造一个仅指定名称、id、等级与元素属性的生物（其余属性使用默认值）。
     *
     * @param name 名称
     * @param id   唯一标识
     * @param l    等级
     * @param u    元素属性
     */
    public LivingThing(String name, String id, long l, ElementSort u) {
        super(name, id, l);
        this.elementSort = u;
    }

    /**
     * 构造一个仅指定速度的生物（其余属性使用默认值）。
     *
     * @param speed 速度
     */
    public LivingThing(long speed) {
        this.speed = speed;

    }

    /**
     * 将当前生命值限制在一次生命上限内（溢出部分被截断）。
     */
    public void renewHp() {
        if (this.getHp() > this.getHpMax()) {
            this.setHp(this.getHpMax());
        }
    }

    /**
     * @return 攻击增强百分比
     */
    public double getAttackEnhancePercent() {
        return attackEnhancePercent;
    }

    /**
     * 设置攻击增强百分比。
     *
     * @param attackEnhancePercent 攻击增强百分比
     */
    public void setAttackEnhancePercent(double attackEnhancePercent) {
        this.attackEnhancePercent = attackEnhancePercent;
    }

    /**
     * @return 生命增强固定值
     */
    public long getHpEnhanceAmount() {
        return hpEnhanceAmount;
    }

    /**
     * 设置生命增强固定值，并立即将当前生命值限制在新的上限内。
     *
     * @param hpEnhanceAmount 生命增强固定值
     */
    public void setHpEnhanceAmount(long hpEnhanceAmount) {
        this.hpEnhanceAmount = hpEnhanceAmount;
        this.renewHp();
    }

    /**
     * @return 防御增强百分比
     */
    public double getDefenceEnhancePercent() {
        return defenceEnhancePercent;
    }

    /**
     * 设置防御增强百分比。
     *
     * @param defenceEnhancePercent 防御增强百分比
     */
    public void setDefenceEnhancePercent(double defenceEnhancePercent) {
        this.defenceEnhancePercent = defenceEnhancePercent;
    }

    /**
     * @return 速度增强百分比
     */
    public double getSpeedEnhancePercent() {
        return speedEnhancePercent;
    }

    /**
     * 设置速度增强百分比。
     *
     * @param speedEnhancePercent 速度增强百分比
     */
    public void setSpeedEnhancePercent(double speedEnhancePercent) {
        this.speedEnhancePercent = speedEnhancePercent;
    }

    /**
     * @return 生命增强百分比
     */
    public double getHpEnhancePercent() {
        return hpEnhancePercent;
    }

    /**
     * 设置生命增强百分比，并立即将当前生命值限制在新的上限内。
     *
     * @param hpEnhancePercent 生命增强百分比
     */
    public void setHpEnhancePercent(double hpEnhancePercent) {
        this.hpEnhancePercent = hpEnhancePercent;
        this.renewHp();
    }

    /**
     * @return 攻击增强固定值
     */
    public long getAttackEnhanceAmount() {
        return attackEnhanceAmount;
    }

    /**
     * 设置攻击增强固定值。
     *
     * @param attackEnhanceAmount 攻击增强固定值
     */
    public void setAttackEnhanceAmount(long attackEnhanceAmount) {
        this.attackEnhanceAmount = attackEnhanceAmount;
    }

    /**
     * @return 防御增强固定值
     */
    public long getDefenceEnhanceAmount() {
        return defenceEnhanceAmount;
    }

    /**
     * 设置防御增强固定值。
     *
     * @param defenceEnhanceAmount 防御增强固定值
     */
    public void setDefenceEnhanceAmount(long defenceEnhanceAmount) {
        this.defenceEnhanceAmount = defenceEnhanceAmount;
    }

    /**
     * @return 速度增强固定值
     */
    public long getSpeedEnhanceAmount() {
        return speedEnhanceAmount;
    }

    /**
     * 设置速度增强固定值。
     *
     * @param speedEnhanceAmount 速度增强固定值
     */
    public void setSpeedEnhanceAmount(long speedEnhanceAmount) {
        this.speedEnhanceAmount = speedEnhanceAmount;
    }

    /**
     * @return 暴击伤害增强百分比
     */
    public double getCriticalDMGEnhancePercent() {
        return criticalDMGEnhancePercent;
    }

    /**
     * 设置暴击伤害增强百分比。
     *
     * @param criticalDMGEnhancePercent 暴击伤害增强百分比
     */
    public void setCriticalDMGEnhancePercent(double criticalDMGEnhancePercent) {
        this.criticalDMGEnhancePercent = criticalDMGEnhancePercent;
    }

    /**
     * @return 暴击率增强百分比
     */
    public double getCriticalRateEnhancePercent() {
        return criticalRateEnhancePercent;
    }

    /**
     * 设置暴击率增强百分比。
     *
     * @param criticalRateEnhancePercent 暴击率增强百分比
     */
    public void setCriticalRateEnhancePercent(double criticalRateEnhancePercent) {
        this.criticalRateEnhancePercent = criticalRateEnhancePercent;
    }

    /**
     * @return 暴击率增强固定值
     */
    public double getCriticalRateEnhanceAmount() {
        return criticalRateEnhanceAmount;
    }

    /**
     * 设置暴击率增强固定值。
     *
     * @param criticalRateEnhanceAmount 暴击率增强固定值
     */
    public void setCriticalRateEnhanceAmount(double criticalRateEnhanceAmount) {
        this.criticalRateEnhanceAmount = criticalRateEnhanceAmount;
    }

    /**
     * @return 暴击伤害增强固定值
     */
    public double getCriticalDMGEnhanceAmount() {
        return criticalDMGEnhanceAmount;
    }

    /**
     * 设置暴击伤害增强固定值。
     *
     * @param criticalDMGEnhanceAmount 暴击伤害增强固定值
     */
    public void setCriticalDMGEnhanceAmount(double criticalDMGEnhanceAmount) {
        this.criticalDMGEnhanceAmount = criticalDMGEnhanceAmount;
    }

    /**
     * @return 特殊状态显示接口
     */
    public IShowSpecialMes getShowSpecialMes() {
        return showSpecialMes;
    }

    /**
     * 设置特殊状态显示接口。
     *
     * @param showSpecialMes 特殊状态显示接口
     */
    public void setShowSpecialMes(IShowSpecialMes showSpecialMes) {
        this.showSpecialMes = showSpecialMes;
    }

    /**
     * @return 金元素穿透
     */
    public double getMetalPenetration() {
        return metalPenetration;
    }

    /**
     * 设置金元素穿透。
     *
     * @param metalPenetration 金元素穿透
     */
    public void setMetalPenetration(double metalPenetration) {
        this.metalPenetration = metalPenetration;
    }

    /**
     * 按元素类型获取对应的法力。
     *
     * @param elementSortNeeded 需要的元素类型
     * @return 对应的法力实例；若不存在返回 {@code null}
     */
    public Mana getMana(ElementSort elementSortNeeded) {
        for (Mana mana : this.manas) {
            if (mana.getElementSort().equals(elementSortNeeded)) return mana;
        }
        return null;
    }

    /**
     * 初始化五行法力。主元素法力上限为 {@code 成长 * (等级-1) + 200}，其余元素为
     * {@code 成长 * (等级-1) + 20}。
     */
    public void initialMana() {
        this.manas = new ArrayList<>();
        switch (this.getElementSort()) {
            case METAL -> {
                manas.add(new Mana(this.metalManaGrowNumber * (this.getLevel() - 1) + 200, ElementSort.METAL));
                manas.add(new Mana(this.woodManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.WOOD));
                manas.add(new Mana(this.waterManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.WATER));
                manas.add(new Mana(this.fireManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.FIRE));
                manas.add(new Mana(this.dirtManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.DIRT));
            }
            case WOOD -> {
                manas.add(new Mana(this.metalManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.METAL));
                manas.add(new Mana(this.woodManaGrowNumber * (this.getLevel() - 1) + 200, ElementSort.WOOD));
                manas.add(new Mana(this.waterManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.WATER));
                manas.add(new Mana(this.fireManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.FIRE));
                manas.add(new Mana(this.dirtManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.DIRT));
            }
            case WATER -> {
                manas.add(new Mana(this.metalManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.METAL));
                manas.add(new Mana(this.woodManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.WOOD));
                manas.add(new Mana(this.waterManaGrowNumber * (this.getLevel() - 1) + 200, ElementSort.WATER));
                manas.add(new Mana(this.fireManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.FIRE));
                manas.add(new Mana(this.dirtManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.DIRT));
            }
            case FIRE -> {
                manas.add(new Mana(this.metalManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.METAL));
                manas.add(new Mana(this.woodManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.WOOD));
                manas.add(new Mana(this.waterManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.WATER));
                manas.add(new Mana(this.fireManaGrowNumber * (this.getLevel() - 1) + 200, ElementSort.FIRE));
                manas.add(new Mana(this.dirtManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.DIRT));
            }
            case DIRT -> {
                manas.add(new Mana(this.metalManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.METAL));
                manas.add(new Mana(this.woodManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.WOOD));
                manas.add(new Mana(this.waterManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.WATER));
                manas.add(new Mana(this.fireManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.FIRE));
                manas.add(new Mana(this.dirtManaGrowNumber * (this.getLevel() - 1) + 200, ElementSort.DIRT));
            }
        }

    }

    /**
     * 每回合恢复法力。每个元素法力按 {@code 等级/100 * 成长 + 100} 恢复，
     * 主元素额外恢复 {@code 等级} 点法力。
     */
    public void recoverManaEveryTurn() {
        for (Mana mana : manas) {
            if (mana.getElementSort().equals(ElementSort.METAL)) {
                mana.setAmount(mana.getAmount() + this.getLevel() / 100.00 * this.getMetalManaGrowNumber() + 100);
            }
            if (this.getElementSort().equals(ElementSort.METAL)) {
                mana.setAmount(mana.getAmount() + this.getLevel());
            }
            if (mana.getElementSort().equals(ElementSort.WOOD)) {
                mana.setAmount(mana.getAmount() + this.getLevel() / 100.00 * this.getWoodManaGrowNumber() + 100);
            }
            if (this.getElementSort().equals(ElementSort.WOOD)) {
                mana.setAmount(mana.getAmount() + this.getLevel());
            }
            if (mana.getElementSort().equals(ElementSort.WATER)) {
                mana.setAmount(mana.getAmount() + this.getLevel() / 100.00 * this.getWaterManaGrowNumber() + 100);
            }
            if (this.getElementSort().equals(ElementSort.WATER)) {
                mana.setAmount(mana.getAmount() + this.getLevel());
            }
            if (mana.getElementSort().equals(ElementSort.FIRE)) {
                mana.setAmount(mana.getAmount() + this.getLevel() / 100.00 * this.getFireManaGrowNumber() + 100);
            }
            if (this.getElementSort().equals(ElementSort.FIRE)) {
                mana.setAmount(mana.getAmount() + this.getLevel());
            }
            if (mana.getElementSort().equals(ElementSort.DIRT)) {
                mana.setAmount(mana.getAmount() + this.getLevel() / 100.00 * this.getDirtManaGrowNumber() + 100);
            }
            if (this.getElementSort().equals(ElementSort.DIRT)) {
                mana.setAmount(mana.getAmount() + this.getLevel());
            }
        }


    }

    /**
     * @return 五行法力列表
     */
    public List<Mana> getManas() {
        return manas;
    }

    /**
     * 设置五行法力列表。
     *
     * @param manas 法力列表
     */
    public void setManas(List<Mana> manas) {
        this.manas = manas;
    }

    /**
     * @return 生命成长系数
     */
    public double getHpGrowNumber() {
        return hpGrowNumber;
    }

    /**
     * 设置生命成长系数。
     *
     * @param hpGrowNumber 生命成长系数
     */
    public void setHpGrowNumber(double hpGrowNumber) {
        this.hpGrowNumber = hpGrowNumber;
    }

    /**
     * @return 攻击成长系数
     */
    public double getAtkGrowNumber() {
        return atkGrowNumber;
    }

    /**
     * 设置攻击成长系数。
     *
     * @param atkGrowNumber 攻击成长系数
     */
    public void setAtkGrowNumber(double atkGrowNumber) {
        this.atkGrowNumber = atkGrowNumber;
    }

    /**
     * @return 防御成长系数
     */
    public double getDfkGrowNumber() {
        return dfkGrowNumber;
    }

    /**
     * 设置防御成长系数。
     *
     * @param dfkGrowNumber 防御成长系数
     */
    public void setDfkGrowNumber(double dfkGrowNumber) {
        this.dfkGrowNumber = dfkGrowNumber;
    }

    /**
     * @return 元素属性（金木水火土）
     */
    public ElementSort getElementSort() {
        return elementSort;
    }

    /**
     * 设置元素属性。
     *
     * @param elementSort 元素属性
     */
    public void setElementSort(ElementSort elementSort) {
        this.elementSort = elementSort;
    }

    /**
     * @return 金法力成长系数
     */
    public double getMetalManaGrowNumber() {
        return metalManaGrowNumber;
    }

    /**
     * 设置金法力成长系数。
     *
     * @param metalManaGrowNumber 金法力成长系数
     */
    public void setMetalManaGrowNumber(double metalManaGrowNumber) {
        this.metalManaGrowNumber = metalManaGrowNumber;
    }

    /**
     * @return 木法力成长系数
     */
    public double getWoodManaGrowNumber() {
        return woodManaGrowNumber;
    }

    /**
     * 设置木法力成长系数。
     *
     * @param woodManaGrowNumber 木法力成长系数
     */
    public void setWoodManaGrowNumber(double woodManaGrowNumber) {
        this.woodManaGrowNumber = woodManaGrowNumber;
    }

    /**
     * @return 水法力成长系数
     */
    public double getWaterManaGrowNumber() {
        return waterManaGrowNumber;
    }

    /**
     * 设置水法力成长系数。
     *
     * @param waterManaGrowNumber 水法力成长系数
     */
    public void setWaterManaGrowNumber(double waterManaGrowNumber) {
        this.waterManaGrowNumber = waterManaGrowNumber;
    }

    /**
     * @return 火法力成长系数
     */
    public double getFireManaGrowNumber() {
        return fireManaGrowNumber;
    }

    /**
     * 设置火法力成长系数。
     *
     * @param fireManaGrowNumber 火法力成长系数
     */
    public void setFireManaGrowNumber(double fireManaGrowNumber) {
        this.fireManaGrowNumber = fireManaGrowNumber;
    }

    /**
     * @return 土法力成长系数
     */
    public double getDirtManaGrowNumber() {
        return dirtManaGrowNumber;
    }

    /**
     * 设置土法力成长系数。
     *
     * @param dirtManaGrowNumber 土法力成长系数
     */
    public void setDirtManaGrowNumber(double dirtManaGrowNumber) {
        this.dirtManaGrowNumber = dirtManaGrowNumber;
    }

    /**
     * @return 土元素穿透
     */
    public double getDirtPenetration() {
        return dirtPenetration;
    }

    /**
     * 设置土元素穿透。
     *
     * @param dirtPenetration 土元素穿透
     */
    public void setDirtPenetration(double dirtPenetration) {
        this.dirtPenetration = dirtPenetration;
    }

    /**
     * @return 当前回合条目
     */
    public TurnEntry getPresentTurn() {
        return presentTurn;
    }

    /**
     * 设置当前回合条目。
     *
     * @param presentTurn 回合条目
     */
    public void setPresentTurn(TurnEntry presentTurn) {
        this.presentTurn = presentTurn;
    }

    /**
     * @return 火元素穿透
     */
    public double getFirePenetration() {
        return firePenetration;
    }

    /**
     * 设置火元素穿透。
     *
     * @param firePenetration 火元素穿透
     */
    public void setFirePenetration(double firePenetration) {
        this.firePenetration = firePenetration;
    }

    /**
     * @return 水元素穿透
     */
    public double getWaterPenetration() {
        return waterPenetration;
    }

    /**
     * 设置水元素穿透。
     *
     * @param waterPenetration 水元素穿透
     */
    public void setWaterPenetration(double waterPenetration) {
        this.waterPenetration = waterPenetration;
    }

    /**
     * @return 木元素穿透
     */
    public double getWoodPenetration() {
        return woodPenetration;
    }

    /**
     * 设置木元素穿透。
     *
     * @param woodPenetration 木元素穿透
     */
    public void setWoodPenetration(double woodPenetration) {
        this.woodPenetration = woodPenetration;
    }

    /**
     * @return 金元素法力
     */
    public Mana getMetalMana() {
        return this.getMana(ElementSort.METAL);
    }

    /**
     * @return 木元素法力
     */
    public Mana getWoodMana() {
        return this.getMana(ElementSort.WOOD);
    }

    /**
     * @return 水元素法力
     */
    public Mana getWaterMana() {
        return this.getMana(ElementSort.WATER);
    }

    /**
     * @return 火元素法力
     */
    public Mana getFireMana() {
        return this.getMana(ElementSort.FIRE);
    }

    /**
     * @return 土元素法力
     */
    public Mana getDirtMana() {
        return this.getMana(ElementSort.DIRT);
    }

    /**
     * @return 金元素伤害增强
     */
    public double getMetalDamageEnhance() {
        return metalDamageEnhance;
    }

    /**
     * 设置金元素伤害增强。
     *
     * @param metalDamageEnhance 金元素伤害增强
     */
    public void setMetalDamageEnhance(double metalDamageEnhance) {
        this.metalDamageEnhance = metalDamageEnhance;
    }

    /**
     * @return 木元素伤害增强
     */
    public double getWoodDamageEnhance() {
        return woodDamageEnhance;
    }

    /**
     * 设置木元素伤害增强。
     *
     * @param woodDamageEnhance 木元素伤害增强
     */
    public void setWoodDamageEnhance(double woodDamageEnhance) {
        this.woodDamageEnhance = woodDamageEnhance;
    }

    /**
     * @return 水元素伤害增强
     */
    public double getWaterDamageEnhance() {
        return waterDamageEnhance;
    }

    /**
     * 设置水元素伤害增强。
     *
     * @param waterDamageEnhance 水元素伤害增强
     */
    public void setWaterDamageEnhance(double waterDamageEnhance) {
        this.waterDamageEnhance = waterDamageEnhance;
    }

    /**
     * @return 火元素伤害增强
     */
    public double getFireDamageEnhance() {
        return fireDamageEnhance;
    }

    /**
     * 设置火元素伤害增强。
     *
     * @param fireDamageEnhance 火元素伤害增强
     */
    public void setFireDamageEnhance(double fireDamageEnhance) {
        this.fireDamageEnhance = fireDamageEnhance;
    }

    /**
     * @return 土元素伤害增强
     */
    public double getDirtDamageEnhance() {
        return dirtDamageEnhance;
    }

    /**
     * 设置土元素伤害增强。
     *
     * @param dirtDamageEnhance 土元素伤害增强
     */
    public void setDirtDamageEnhance(double dirtDamageEnhance) {
        this.dirtDamageEnhance = dirtDamageEnhance;
    }

    /**
     * @return 生物附加的固定额外伤害值
     */
    public long getExtraDamage() {
        return extraDamage;
    }

    /**
     * 设置生物附加的固定额外伤害值。
     *
     * @param extraDamage 固定额外伤害值
     */
    public void setExtraDamage(long extraDamage) {
        this.extraDamage = extraDamage;
    }

    /**
     * 帧更新钩子。
     * <p>
     * <b>设计意图：模拟"游戏每帧更新所有实体"</b>——{@code FightTurnPastListener} 在<b>每个回合</b>
     * 处理完当前行动者之后，会对<b>全场所有实体</b>各调用一次本方法，等价于主循环里的
     * {@code Update()}。所以它<b>不是</b>"这个实体的回合到了"的回调。
     * <p>
     * 由此推出两条写法约定：
     * <ul>
     *     <li><b>可以</b>在这里做"每帧检查"（血量阈值、状态同步之类）——
     *     自己判断"我是不是已经做过了"即可（例如用 {@code appliedXxx} 之类标记）；</li>
     *     <li><b>不要</b>用它来数"自己过了几个回合"（那是"每帧"不是"每回合"）。
     *     需要按回合计时的状态请做成 {@link cn.gfhnv.game.effect.Effect}：
     *     {@code EffectEventListener} 会统一递减 {@code lastTime} 并在到期时移除。</li>
     * </ul>
     */
    public void updateSelf() {
    }

    /**
     * @return 单体伤害倍率
     */
    public double getIndividualMultipleArea() {
        return individualMultipleArea;
    }

    /**
     * 设置单体伤害倍率。
     *
     * @param individualMultipleArea 单体伤害倍率
     */
    public void setIndividualMultipleArea(double individualMultipleArea) {
        this.individualMultipleArea = individualMultipleArea;
    }

    /**
     * 创建一个空的生物实例（子类可重写此方法返回自己的工厂实例）。
     *
     * @return 新的生物实例
     */
    public LivingThing livingThingFactory() {
        return new LivingThing();
    }

    /**
     * 链式设置等级。
     *
     * @param level 等级
     * @return 当前生物实例
     */
    public LivingThing facSetLevel(Long level) {
        this.setLevel(level);
        return this;
    }

    /**
     * 链式设置火抗性。
     *
     * @param fireResistance 火抗性
     * @return 当前生物实例
     */
    public LivingThing facSetFireResistance(double fireResistance) {
        this.fireResistance = fireResistance;
        return this;
    }

    /**
     * 链式设置水抗性。
     *
     * @param waterResistance 水抗性
     * @return 当前生物实例
     */
    public LivingThing facSetWaterResistance(double waterResistance) {
        this.waterResistance = waterResistance;
        return this;
    }

    /**
     * 链式设置金抗性。
     *
     * @param metalResistance 金抗性
     * @return 当前生物实例
     */
    public LivingThing facSetMetalResistance(double metalResistance) {
        this.metalResistance = metalResistance;
        return this;
    }

    /**
     * 链式设置木抗性。
     *
     * @param woodResistance 木抗性
     * @return 当前生物实例
     */
    public LivingThing facSetWoodResistance(double woodResistance) {
        this.woodResistance = woodResistance;
        return this;
    }

    /**
     * 链式设置土抗性。
     *
     * @param dirtResistance 土抗性
     * @return 当前生物实例
     */
    public LivingThing facSetDirtResistance(double dirtResistance) {
        this.dirtResistance = dirtResistance;
        return this;
    }

    /**
     * 链式设置生命上限。
     *
     * @param hpMax 生命上限
     * @return 当前生物实例
     */
    public LivingThing facSetHpMax(long hpMax) {
        this.hpMax = hpMax;
        return this;
    }

    /**
     * 链式设置基础暴击伤害倍率加成。
     *
     * @param criticalDMG 基础暴击伤害倍率加成
     * @return 当前生物实例
     */
    public LivingThing facSetCriticalDMG(double criticalDMG) {
        this.criticalDMG = criticalDMG;
        return this;
    }

    /**
     * 链式设置基础暴击率。
     *
     * @param criticalRATE 基础暴击率
     * @return 当前生物实例
     */
    public LivingThing facSetCriticalRATE(double criticalRATE) {
        this.getCriticalRATE = criticalRATE;
        return this;
    }

    /**
     * 链式设置存活状态。
     *
     * @param alive 是否存活
     * @return 当前生物实例
     */
    public LivingThing facSetAlive(boolean alive) {
        this.Alive = alive;
        return this;
    }

    /**
     * 链式设置全属性穿透。
     *
     * @param chuantong 全属性穿透
     * @return 当前生物实例
     */
    public LivingThing facSetChuantong(double chuantong) {
        this.penetration = chuantong;
        return this;
    }

    /**
     * 链式设置「基础减伤」（等价于 {@link #setDamageAbsorbedPercent(double)}）。
     *
     * @param damageAbsorbedPercent 减伤比例（0.25 = 少受 25% 伤害）
     * @return 当前生物实例
     */
    public LivingThing facSetDamageAbsorbedPercent(double damageAbsorbedPercent) {
        this.setDamageAbsorbedPercent(damageAbsorbedPercent);
        return this;
    }

    /**
     * 链式设置生命值。
     *
     * @param hp 生命值
     * @return 当前生物实例
     */
    public LivingThing facSetHp(long hp) {
        this.setHp(hp);
        return this;
    }

    /**
     * 链式设置防御力。
     *
     * @param dfk 防御力
     * @return 当前生物实例
     */
    public LivingThing facSetDfk(long dfk) {
        this.defence = dfk;
        return this;
    }

    /**
     * 链式设置速度。
     *
     * @param speed 速度
     * @return 当前生物实例
     */
    public LivingThing facSetSpeed(long speed) {
        this.speed = speed;
        return this;
    }

    /**
     * 链式设置攻击力。
     *
     * @param afk 攻击力
     * @return 当前生物实例
     */
    public LivingThing facSetAfk(long afk) {
        this.attack = afk;
        return this;
    }

    /**
     * 链式设置全属性增伤百分比。
     *
     * @param enhance 全属性增伤百分比
     * @return 当前生物实例
     */
    public LivingThing facSetEnhance(double enhance) {
        this.enhance = enhance;
        return this;
    }

    /**
     * 链式设置防御削减百分比。
     *
     * @param defenseLoss 防御削减百分比
     * @return 当前生物实例
     */
    public LivingThing facSetDefenseLoss(double defenseLoss) {
        this.defenseLoss = defenseLoss;
        return this;
    }

    /**
     * 链式设置描述。
     *
     * @param description 描述
     * @return 当前生物实例
     */
    public LivingThing facSetDescription(String description) {
        this.description = description;
        return this;
    }

    /**
     * @return 生物描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置生物描述。
     *
     * @param description 描述
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return 控制该生物行动的控制器
     */
    public UniversalController getController() {
        return controller;
    }

    /**
     * 设置控制该生物行动的控制器。
     *
     * @param controller 控制器
     */
    public void setController(UniversalController controller) {
        this.controller = controller;
    }

    /**
     * @return 火抗性
     */
    public double getFireResistance() {
        return fireResistance;
    }

    /**
     * 设置火抗性。
     *
     * @param fireResistance 火抗性
     */
    public void setFireResistance(double fireResistance) {
        this.fireResistance = fireResistance;
    }

    /**
     * @return 水抗性
     */
    public double getWaterResistance() {
        return waterResistance;
    }

    /**
     * 设置水抗性。
     *
     * @param waterResistance 水抗性
     */
    public void setWaterResistance(double waterResistance) {
        this.waterResistance = waterResistance;
    }

    /**
     * @return 金抗性
     */
    public double getMetalResistance() {
        return metalResistance;
    }

    /**
     * 设置金抗性。
     *
     * @param metalResistance 金抗性
     */
    public void setMetalResistance(double metalResistance) {
        this.metalResistance = metalResistance;
    }

    /**
     * @return 木抗性
     */
    public double getWoodResistance() {
        return woodResistance;
    }

    /**
     * 设置木抗性。
     *
     * @param woodResistance 木抗性
     */
    public void setWoodResistance(double woodResistance) {
        this.woodResistance = woodResistance;
    }

    /**
     * @return 土抗性
     */
    public double getDirtResistance() {
        return dirtResistance;
    }

    /**
     * 设置土抗性。
     *
     * @param dirtResistance 土抗性
     */
    public void setDirtResistance(double dirtResistance) {
        this.dirtResistance = dirtResistance;
    }

    /**
     * @return 最终暴击率（基础暴击率 × (1 + 暴击率增强百分比) + 暴击率增强固定值）
     */
    public double getGetCriticalRATE() {
        return getCriticalRATE * (1 + criticalRateEnhancePercent) + criticalRateEnhanceAmount;
    }

    /**
     * 设置基础暴击率。
     *
     * @param getCriticalRATE 基础暴击率
     */
    public void setGetCriticalRATE(double getCriticalRATE) {
        this.getCriticalRATE = getCriticalRATE;
    }

    /**
     * @return 当前参与的战斗上下文
     */
    public Fight getParticipateFight() {
        return participateFight;
    }

    /**
     * 设置当前参与的战斗上下文。
     *
     * @param participateFight 战斗上下文
     */
    public void setParticipateFight(Fight participateFight) {
        this.participateFight = participateFight;
    }

    /**
     * @return 当前携带的效果列表（Buff/Debuff）
     */
    public List<Effect> getEntityEffectList() {
        return entityEffectList;
    }

    /**
     * 设置当前携带的效果列表。
     *
     * @param entityEffectList 效果列表
     */
    public void setEntityEffectList(List<Effect> entityEffectList) {
        this.entityEffectList = entityEffectList;
    }

    /**
     * 为指定目标添加效果。若目标已存在同类效果（{@link Effect#equals} 判定），
     * 等级相同或更高则延长持续时间，否则用新效果替换并触发 {@link Effect#initialEffect}。
     * <p>
     * 进入列表前会把效果的 id 补成注册表里的完整 id（见
     * {@link cn.gfhnv.game.world.World#applyRegisteredId(Effect)}）：
     * 效果通常是技能里 {@code new} 出来的，构造器里只有短 id，
     * 而 {@link Effect#equals} 是按 id 判定的 —— 不补全就会和「从注册表复制出来的同种效果」
     * 被当成两种，叠加/刷新逻辑失效。这一步必须在 {@code equals} 判定<b>之前</b>做。
     *
     * @param target 被施加效果的目标
     * @param effect 要添加的效果
     */
    public void addEffect(LivingThing target, Effect effect) {
        if (target == null || effect == null) {
            return;
        }
        World.applyRegisteredId(effect);
        for (int i = 0; i < target.entityEffectList.size(); i++) {
            Effect existing = target.entityEffectList.get(i);
            if (existing.equals(effect)) {
                if (existing.getLevel() >= effect.getLevel()) {
                    existing.setLastTime(existing.getLastTime() + effect.getLastTime());
                } else {

                    target.entityEffectList.set(i, effect);
                    effect.initialEffect(target);
                }
                return;
            }
        }
        target.entityEffectList.add(effect);
        effect.initialEffect(target);
    }

    /**
     * 为当前生物添加效果。叠加规则与 {@link #addEffect(LivingThing, Effect)} 相同，
     * id 归一也在那里做。
     *
     * @param effect 要添加的效果
     */
    public void addEffect(Effect effect) {
        addEffect(this, effect);
    }

    /**
     * 从当前生物身上移除指定效果。
     *
     * @param ef 要移除的效果
     */
    public void removeEffect(Effect ef) {
        this.entityEffectList.remove(ef);
    }

    /**
     * @return 总减伤比例（{@code 1 − 承伤倍率}）。
     * <p>
     * 例如两个减伤 50% 与 25%：承伤倍率 0.5 × 0.75 = 0.375，所以这里是 0.625。
     * 注意减伤是<b>乘算</b>叠加的，不是相加 —— 想要逐项明细看 {@link #getDamageReductions()}。
     */
    public double getDamageAbsorbedPercent() {
        return 1 - getDamageTakenMultiplier();
    }

    /**
     * 设置「基础减伤」（替换掉上一次用它设置的值）。
     * <p>
     * 兼容旧写法：{@code setDamageAbsorbedPercent(0.75)} 表示少受 75% 伤害。
     * 它会作为一个独立来源参与乘算，比例被夹到 [0,1]；
     * 想要多个来源叠加请用 {@link #addDamageReduction(Object, double)}。
     *
     * @param damageAbsorbedPercent 减伤比例（0.25 = 少受 25% 伤害）
     */
    public void setDamageAbsorbedPercent(double damageAbsorbedPercent) {
        addDamageReduction(BASE_DAMAGE_REDUCTION, damageAbsorbedPercent);
    }

    /**
     * 增加一个减伤来源（<b>乘算</b>叠加）。
     * <p>
     * 承伤倍率 = Π(1 − 每个减伤)：50% 与 25% 两个来源 → 只受 37.5% 伤害。
     * 同一个来源（按对象身份判断）重复添加只保留最后一次，所以技能反复触发不会越叠越多；
     * 比例会被夹到 [0,1]，因此减伤叠再多也只会把伤害压到 0，<b>不会算成负数</b>
     * （负数会被 {@link #getDamage(DamageEvent)} 当成治疗）。
     *
     * @param source  来源，按对象身份区分（效果实例、技能、装备都可以）
     * @param percent 减伤比例（0.25 = 少受 25% 伤害）
     */
    public void addDamageReduction(Object source, double percent) {
        if (source == null) {
            return;
        }
        removeDamageReduction(source);
        double clamped = Math.max(0, Math.min(1, percent));
        if (clamped > 0) {
            damageReductions.add(new DamageReduction(source, clamped));
        }
    }

    /**
     * 移除一个减伤来源。
     *
     * @param source 来源（与添加时是同一个对象）
     * @return 是否真的移除了
     */
    public boolean removeDamageReduction(Object source) {
        return source != null && damageReductions.removeIf(reduction -> reduction.source() == source);
    }

    /**
     * 清空全部减伤来源。
     */
    public void clearDamageReductions() {
        damageReductions.clear();
    }

    /**
     * @return 全部减伤来源（副本，改它不影响本体）
     */
    public List<DamageReduction> getDamageReductions() {
        return new ArrayList<>(damageReductions);
    }

    /**
     * 承伤倍率：{@code Π(1 − 每个减伤)}，最后夹在 [0,1]。
     * <p>
     * 例：50% 与 25% → {@code 0.5 × 0.75 = 0.375}（只受 37.5% 伤害），而不是相加的 25%。
     * 伤害计算（{@link cn.gfhnv.game.damage.DamageCalculate}）用的就是这个值。
     *
     * @return 承伤倍率；没有任何减伤时是 1
     */
    public double getDamageTakenMultiplier() {
        double multiplier = 1;
        for (DamageReduction reduction : damageReductions) {
            multiplier *= (1 - reduction.percent());
        }
        return Math.max(0, Math.min(1, multiplier));
    }

    /**
     * @return 全属性穿透
     */
    public double getPenetration() {
        return penetration;
    }

    /**
     * 设置全属性穿透。
     *
     * @param penetration 全属性穿透
     */
    public void setPenetration(double penetration) {
        this.penetration = penetration;
    }

    /**
     * @return 最终生命上限（基础上限 × (1 + 生命增强百分比) + 生命增强固定值）
     */
    public long getHpMax() {
        return (long) (hpMax * (1 + hpEnhancePercent) + hpEnhanceAmount);
    }

    /**
     * 设置基础生命上限。
     *
     * @param hpMax 基础生命上限
     */
    public void setHpMax(long hpMax) {
        this.hpMax = hpMax;
    }

    /**
     * 判断生物是否存活。若生命值小于等于 0 则标记为死亡并重置控制器的动作信号与特殊动作。
     *
     * @return {@code true} 表示存活，{@code false} 表示死亡
     */
    public boolean isAlive() {
        if (getHp() <= 0) {
            this.getController().setActionSignal(ActionSignal.NORMAL);
            this.getController().setSpecialAction(null);
            Alive = false;
        }
        if (getHp() > 0) {
            Alive = true;
        }
        return Alive;
    }

    /**
     * 直接设置存活状态。
     *
     * @param b {@code true} 存活，{@code false} 死亡
     */
    public void setAlive(boolean b) {
        this.Alive = b;
    }

    /**
     * @return 全属性增伤百分比
     */
    public double getEnhance() {
        return enhance;
    }

    /**
     * 设置全属性增伤百分比。
     *
     * @param enhance 全属性增伤百分比
     */
    public void setEnhance(double enhance) {
        this.enhance = enhance;
    }

    /**
     * @return 防御削减百分比
     */
    public double getDefenseLoss() {
        return defenseLoss;
    }

    /**
     * 设置防御削减百分比。
     *
     * @param defenseLoss 防御削减百分比
     */
    public void setDefenseLoss(double defenseLoss) {
        this.defenseLoss = defenseLoss;
    }

    /**
     * @return 最终攻击力（基础攻击 × (1 + 攻击增强百分比) + 攻击增强固定值）
     */
    public long getAttack() {
        return (long) (attack * (1 + attackEnhancePercent) + attackEnhanceAmount);
    }

    /**
     * 设置基础攻击力。
     *
     * @param attack 基础攻击力
     */
    public void setAttack(long attack) {
        this.attack = attack;
    }

    /**
     * 受到伤害。根据 {@link DamageEvent} 计算新生命值，并依次经过全部
     * {@link IModifyDamage 伤害修正器}（见 {@link #modifyIncomingDamage}）后设置。
     *
     * @param da 伤害事件
     */
    public void getDamage(DamageEvent da) {
        long newHp = this.getHp() - da.getDamage().getDamageAmount();
        newHp = modifyIncomingDamage(newHp, da);
        this.setHp(newHp);
        System.out.print("剩余HP" + this.getHp());
    }

    /**
     * 复制生物（深拷贝）。
     *
     * @return 生物的副本
     */
    public LivingThing copy() {
        throw new RuntimeException("请重写此方法..类" + this.getClass().getName());
    }

    /**
     * 链式设置等级。
     *
     * @param level 等级
     * @return 当前生物实例
     */
    public LivingThing facSetLevel(long level) {
        this.setLevel(level);
        return this;
    }

    /**
     * 链式设置名称。
     *
     * @param name 名称
     * @return 当前生物实例
     */
    public LivingThing facSetName(String name) {
        this.setName(name);
        return this;
    }

    /**
     * 链式设置 id。
     *
     * @param id 唯一标识
     * @return 当前生物实例
     */
    public LivingThing facSetId(String id) {
        this.setId(id);
        return this;
    }

    /**
     * 链式设置生命成长系数。
     *
     * @param hpGrowNumber 生命成长系数
     * @return 当前生物实例
     */
    public LivingThing facSetHpGrowNumber(double hpGrowNumber) {
        this.setHpGrowNumber(hpGrowNumber);
        return this;
    }

    /**
     * 链式设置攻击成长系数。
     *
     * @param atkGrowNumber 攻击成长系数
     * @return 当前生物实例
     */
    public LivingThing facSetAtkGrowNumber(double atkGrowNumber) {
        this.setAtkGrowNumber(atkGrowNumber);
        return this;
    }

    /**
     * 链式设置防御成长系数。
     *
     * @param dfkGrowNumber 防御成长系数
     * @return 当前生物实例
     */
    public LivingThing facSetDfkGrowNumber(double dfkGrowNumber) {
        this.setDfkGrowNumber(dfkGrowNumber);
        return this;
    }

    /**
     * 链式设置元素属性。
     *
     * @param elementSort 元素属性
     * @return 当前实体实例
     */
    public Entity facSetElementSort(ElementSort elementSort) {
        this.setElementSort(elementSort);
        return this;
    }

    /**
     * 链式设置金法力成长系数。
     *
     * @param metalManaGrowNumber 金法力成长系数
     * @return 当前生物实例
     */
    public LivingThing facSetMetalManaGrowNumber(double metalManaGrowNumber) {
        this.setMetalManaGrowNumber(metalManaGrowNumber);
        return this;
    }

    /**
     * 链式设置木法力成长系数。
     *
     * @param woodManaGrowNumber 木法力成长系数
     * @return 当前生物实例
     */
    public LivingThing facSetWoodManaGrowNumber(double woodManaGrowNumber) {
        this.setWoodManaGrowNumber(woodManaGrowNumber);
        return this;
    }

    /**
     * 链式设置水法力成长系数。
     *
     * @param waterManaGrowNumber 水法力成长系数
     * @return 当前生物实例
     */
    public LivingThing facSetWaterManaGrowNumber(double waterManaGrowNumber) {
        this.setWaterManaGrowNumber(waterManaGrowNumber);
        return this;
    }

    /**
     * 链式设置火法力成长系数。
     *
     * @param fireManaGrowNumber 火法力成长系数
     * @return 当前生物实例
     */
    public LivingThing facSetFireManaGrowNumber(double fireManaGrowNumber) {
        this.setFireManaGrowNumber(fireManaGrowNumber);
        return this;
    }

    /**
     * 链式设置土法力成长系数。
     *
     * @param dirtManaGrowNumber 土法力成长系数
     * @return 当前生物实例
     */
    public LivingThing facSetDirtManaGrowNumber(double dirtManaGrowNumber) {
        this.setDirtManaGrowNumber(dirtManaGrowNumber);
        return this;
    }

    /**
     * 链式设置类型。
     *
     * @param type 类型
     * @return 当前生物实例
     */
    public LivingThing facSetType(String type) {
        this.setType(type);
        return this;
    }

    /**
     * 子类可重写此方法，在回合中输出特殊状态信息。
     */
    public void showSpecialStatus() {
    }

    /**
     * 战斗开始时的钩子方法。子类可重写此方法执行开局逻辑（如注册事件监听器等）。
     * <p>
     * 注意：本钩子只在<b>开局</b>由 {@code FightStartEventListener} 遍历
     * {@code Fight#getAllEntities()} 调用一次。战斗中<b>中途加入</b>的实体
     * （技能召唤出来的召唤物等，走 {@code Fight#addEnemy/addFighter}）<b>不会</b>收到，
     * 需要召唤方在加入后显式调用一次（范例：{@code FlameReaver#summonContainer}）。
     *
     * @param fight 当前战斗上下文
     */
    public void whenFightStart(Fight fight) {
    }

    /**
     * 生物<b>离开战斗</b>时的钩子（死亡、被移出阵营列表时调用）。
     * <p>
     * <b>与 {@link #whenFightEnds()} 的分工</b>（这两个钩子曾经是同一个，导致死亡时被复活）：
     * <ul>
     *     <li>{@code whenLeaveFight}：单个生物离场。<b>只做离场结算与清理，绝不复位血量</b> ——
     *     对已死的生物复位血量等于把它复活；而它已经从阵营列表里被摘掉，
     *     会变成"不在任何阵营却能继续出手"的幽灵实体（目标解析还会落到错误的一侧）。</li>
     *     <li>{@link #whenFightEnds()}：整场战斗结束。那是<b>重置</b>（补满血、清效果、清冷却），
     *     为下一场做准备，只会发给还留在 {@code getAllEntities()} 里的生物。</li>
     * </ul>
     * 默认不做事。召唤物这类需要"通知召唤者"的实体应当重写它。
     *
     * @param fight 当前战斗上下文
     */
    public void whenLeaveFight(Fight fight) {
    }

    /**
     * 链式设置五行法力列表。
     *
     * @param manas 法力列表
     * @return 当前生物实例
     */
    public LivingThing facSetManas(List<Mana> manas) {
        this.setManas(manas);
        return this;
    }

    /**
     * 把生物重置为"可再次参战"：重置回合、补满生命、清除效果、重置技能冷却并恢复法力。
     * <p>
     * <b>只应在整场战斗结束时调用</b>（{@code FightEndEventListener}）。
     * 单个生物死亡离场时请用 {@link #whenLeaveFight(Fight)} ——
     * 本方法里的 {@code setHp(getHpMax())} 会把已经死掉的生物复活。
     */
    public void whenFightEnds() {


        setPresentTurn(null);
        setHp((long) getHpMax());
        for (Effect effect : getEntityEffectList()) effect.whenLastTimeEnd(this);
        this.setEntityEffectList(new ArrayList<>());
        for (Skill skill : getController().getSkills()) skill.setNowCoolDown(0);
        for (Mana mana : getManas()) mana.setAmount(mana.getAmountMax());
    }

    /**
     * 使用技能对目标造成伤害（构造 {@link DamageEvent} 并调用目标的 {@link #getDamage}）。
     *
     * @param attacked 被攻击目标
     * @param skill    使用的技能
     */
    public void makeDamage(LivingThing attacked, Skill skill) {
        DamageEvent damageEvent = new DamageEvent(this, attacked, skill);
        EventBus.post(damageEvent);
        System.out.print("造成了" + damageEvent.getDamage().getDamageAmount());
        attacked.getDamage(damageEvent);

    }

    /**
     * @return 最终速度（基础速度 × (1 + 速度增强百分比) + 速度增强固定值）
     */
    public long getSpeed() {
        return (long) (speed * (1 + speedEnhancePercent) + speedEnhanceAmount);
    }

    /**
     * 设置基础速度。
     *
     * @param speed 基础速度
     */
    public void setSpeed(long speed) {
        this.speed = speed;
    }

    /**
     * @return 最终防御力（基础防御 × (1 + 防御增强百分比) + 防御增强固定值）
     */
    public long getDefence() {
        return (long) (defence * (1 + defenceEnhancePercent) + defenceEnhanceAmount);
    }

    /**
     * 设置基础防御力。
     *
     * @param defence 基础防御力
     */
    public void setDefence(long defence) {
        this.defence = defence;
    }

    /**
     * @return 当前生命值
     */
    public long getHp() {
        return hp;
    }

    /**
     * 设置生命值。若减少则发布 {@link HpLossEvent}，若增加则发布 {@link HpRestorationEvent}；
     * 最终值被限制在 0 与生命上限之间。
     *
     * @param hp 新的生命值
     */
    public void setHp(long hp) {
        if (hp < getHp()) {
            EventBus.post(new HpLossEvent(getHp() - hp, this));
        }
        if (hp > getHp()) {
            EventBus.post(new HpRestorationEvent(hp - getHp(), this));
        }
        this.hp = Math.min(this.getHpMax(), hp);
        if (this.hp < 0) {
            this.hp = 0;
        }
    }

    /**
     * @return 最终暴击伤害倍率加成
     */
    public double getCriticalDMG() {
        return criticalDMG * (1 + criticalDMGEnhancePercent) + criticalDMGEnhanceAmount;
    }

    /**
     * 设置基础暴击伤害倍率加成。
     *
     * @param criticalDMG 基础暴击伤害倍率加成
     */
    public void setCriticalDMG(double criticalDMG) {
        this.criticalDMG = criticalDMG;
    }

    /**
     * 把一个「即将结算的新生命值」依次交给所有伤害修正器处理。
     * <p>
     * 受到伤害（{@link #getDamage(DamageEvent)}）与伤害试算
     * （{@link cn.gfhnv.game.skill.Skill#getAnticipatedDamage}）都走这里，
     * 保证「预测的伤害」和「实际掉的伤害」一致。
     * <p>
     * 试算期间 {@link #isAnticipating()} 为 {@code true}：修正器可以照常返回修正后的血量
     * （预测值才准），但<b>不能改动任何状态</b>（消耗次数、排技能、加效果……），
     * 否则 AI 只是「看一眼」就会把一次性的免死/复活提前花掉。
     *
     * @param newHp 已经算好的新生命值（{@code 当前HP − 伤害}）
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

    /**
     * @return 是否正在做「伤害试算」（只读，见 {@link #modifyIncomingDamage}）
     */
    public boolean isAnticipating() {
        return anticipating;
    }

    /**
     * 把一段代码标记为「伤害试算」并执行。
     * <p>
     * 只有 {@link cn.gfhnv.game.skill.Skill#getAnticipatedDamage} 会用它；
     * 用 try/finally 保证异常时开关也会复位。
     *
     * @param action 试算过程
     * @param <T>    返回值类型
     * @return 试算结果
     */
    public <T> T anticipating(Supplier<T> action) {
        boolean previous = anticipating;
        anticipating = true;
        try {
            return action.get();
        } finally {
            anticipating = previous;
        }
    }

    /**
     * 增加一个伤害修正器（会追加到末尾，按添加顺序依次套用）。
     * <p>
     * 之所以改成列表：以前只有一个槽位，第二个想挂钩的系统一 {@code set} 就把前一个顶掉了
     * （例如「免死」「血量下限」这类机制同时存在时）。
     *
     * @param modifier 修正器；{@code null} 与 {@link IModifyDamage#DEFAULT} 会被忽略
     */
    public void addModifyDamage(IModifyDamage modifier) {
        if (modifier == null || modifier == IModifyDamage.DEFAULT) {
            return;
        }
        damageModifiers.add(modifier);
    }

    /**
     * 移除一个伤害修正器（按对象身份判断）。
     *
     * @param modifier 修正器（与添加时是同一个对象）
     * @return 是否真的移除了
     */
    public boolean removeModifyDamage(IModifyDamage modifier) {
        return modifier != null && damageModifiers.removeIf(each -> each == modifier);
    }

    /**
     * 清空全部伤害修正器。
     */
    public void clearModifyDamage() {
        damageModifiers.clear();
    }

    /**
     * @return 全部伤害修正器（副本，按套用顺序）
     */
    public List<IModifyDamage> getModifyDamageList() {
        return new ArrayList<>(damageModifiers);
    }

    /**
     * @return 把全部修正器串起来的一个视图；没有修正器时返回 {@link IModifyDamage#DEFAULT}
     * <p>
     * 兼容旧接口。新代码请直接用 {@link #modifyIncomingDamage(long, DamageEvent)}。
     */
    public IModifyDamage getModifyDamage() {
        if (damageModifiers.isEmpty()) {
            return IModifyDamage.DEFAULT;
        }
        if (damageModifiers.size() == 1) {
            return damageModifiers.get(0);
        }
        final List<IModifyDamage> chain = new ArrayList<>(damageModifiers);
        return new IModifyDamage() {
            @Override
            public long damageModify(long newHp, DamageEvent da) {
                long result = newHp;
                for (IModifyDamage modifier : chain) {
                    result = modifier.damageModify(result, da);
                }
                return result;
            }
        };
    }

    /**
     * 替换掉全部伤害修正器（等价于「清空 + 添加这一个」）。
     * <p>
     * 兼容旧写法：单个修正器仍然可以这样设置。想再挂一个请用
     * {@link #addModifyDamage(IModifyDamage)}。
     *
     * @param modifyDamage 伤害修正接口
     */
    public void setModifyDamage(IModifyDamage modifyDamage) {
        damageModifiers.clear();
        addModifyDamage(modifyDamage);
    }

    /**
     * 汇总身上所有「无视防御」效果（{@link IDefenceIgnore}）提供的百分比。
     * <p>
     * 汇总放在实体这一层，伤害计算就只需要读数字，不必认识具体的效果类
     * （以前是伤害计算里 {@code instanceof IgnoreDefenceEffect}，属于框架反向依赖官方内容）。
     *
     * @return 无视防御的百分比之和（0.5 表示无视目标 50% 防御）
     */
    public double getIgnoreDefencePercent() {
        double total = 0;
        for (Effect effect : entityEffectList) {
            if (effect instanceof IDefenceIgnore ignore) {
                total += ignore.getIgnoreDefencePercent();
            }
        }
        return total;
    }

    /* ------------------------------------------------------------------
     * 无视防御（由效果提供，见 IDefenceIgnore）
     * ------------------------------------------------------------------ */

    /**
     * 汇总身上所有「无视防御」效果（{@link IDefenceIgnore}）提供的固定值。
     *
     * @return 无视防御的固定值之和
     */
    public long getIgnoreDefenceAmount() {
        long total = 0;
        for (Effect effect : entityEffectList) {
            if (effect instanceof IDefenceIgnore ignore) {
                total += ignore.getIgnoreDefenceAmount();
            }
        }
        return total;
    }

    /**
     * 一个减伤来源（{@link LivingThing#getDamageReductions()} 的元素）。
     *
     * @param source  来源对象（按身份区分）
     * @param percent 减伤比例（已夹到 [0,1]）
     */
    public record DamageReduction(Object source, double percent) {
    }
}
