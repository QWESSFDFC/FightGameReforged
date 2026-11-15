package cn.gfhnv.game.entity;

import cn.gfhnv.game.damage.DamageType;
import cn.gfhnv.game.data.DataField;
import cn.gfhnv.game.data.DataFlatten;
import cn.gfhnv.game.data.NoConfig;
import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entityController.PlayerController;
import cn.gfhnv.game.entityController.UniversalController;
import cn.gfhnv.game.event.DamageEvent;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.HpLossEvent;
import cn.gfhnv.game.event.HpRestorationEvent;
import cn.gfhnv.game.interfaces.IDefenceIgnore;
import cn.gfhnv.game.interfaces.IModifyDamage;
import cn.gfhnv.game.interfaces.IShowSpecialMes;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.configLoadingSystem.DataKeys;
import cn.gfhnv.game.system.configLoadingSystem.GameRules;
import cn.gfhnv.game.system.configLoadingSystem.RuleDefaults;
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
 *     <li><b>战斗属性</b>：生命值（hp）、攻击（baseAttack）、防御（baseDefence）、速度（baseSpeed）、
 *     生命上限（baseHpMax）、
 *     暴击率/爆伤（baseCriticalRate / baseCriticalDamage）；</li>
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
    /**
     * 五行法力表的固定顺序（金木水火土）。
     * <p>
     * ⚠️ <b>不能用</b> {@link ElementSort#values()}：那里还有第 6 个常量
     * {@link ElementSort#UNIVERSAL}，它<b>没有</b>五行法力，遍历 {@code values()} 会凭空多造一条。
     */
    private static final List<ElementSort> MANA_ELEMENTS = List.of(
            ElementSort.METAL, ElementSort.WOOD, ElementSort.WATER, ElementSort.FIRE, ElementSort.DIRT);
    /**
     * 「基础减伤」的来源键（<b>每只实体各一个</b>，2026-11-15 第 64 批从 static 改成实例）。
     * <p>
     * 它只给旧写法 {@code setDamageAbsorbedPercent(0.75)} 用：那是一个"整只实体一个数"的旧接口，
     * 现在等价的写法是 {@code addDamageReduction(来源, 0.75)}。
     */
    private final DamageReductionSource baseDamageReduction = new DamageReductionSource("基础减伤");
    /**
     * 减伤来源（<b>乘算叠加</b>）：承伤倍率 = Π(1 − 每个减伤)，见 {@link #getDamageTakenMultiplier()}。
     */
    private final List<DamageReduction> damageReductions = new ArrayList<>();
    private final List<IModifyDamage> damageModifiers = new ArrayList<>();
    /**
     * 属性组件：面板组（{@code hp} / {@code baseHpMax} / {@code baseAttack} / {@code baseDefence} /
     * {@code baseSpeed} /
     * {@code baseCriticalRate} 与 3 个成长系数）、五行组（5 抗性 + 5 单元素穿透 + 5 元素增伤 +
     * 5 法力成长系数）、全局组（{@code penetration} 全属性穿透、{@code enhance} 全属性增伤、
     * {@code baseCriticalDamage} 暴击伤害）、13 个 {@code *Enhance*} 运行时加成（容器化：见 {@link PropertyEnhance}），
     * 以及 {@code alive} / {@code anticipating} 两个状态位。
     * <p>
     * <b>{@code @DataFlatten} 是关键</b>：它让组件的字段被<b>无前缀</b>并进本对象的复合标签，
     * 所以 {@code /data get entity @s} 里看到的仍是 {@code hp}、{@code fireResistance}、{@code penetration}、
     * {@code metalManaGrow}…… 与这些字段还写在本类里时逐个字符一致（数据名是对外 API，见 TIPS §5.10）。
     * <p>
     * 本类保留全部 public getter/setter —— 它们的方法名是 460 个调用点与模组依赖的 API，
     * 只是转发到本组件。
     * <p>
     * 组件在构造时拿到<b>本对象</b>：`setHp` 要用它发事件、拿最终上限夹血，
     * {@code setHpEnhance*} 那一路要用它 {@link #renewHp() 把当前生命夹回新上限}。
     */
    @DataFlatten
    private final AttributeProfile attributes = new AttributeProfile(this);
    /**
     * 身上的效果列表。<b>对外数据名是 {@code effects}</b>（Java 字段名 {@code entityEffectList} 又长又绕）。
     * <p>
     * <b>不进配置文件</b>：对象列表加元素要构造合法的游戏对象（还得补 id、走合并语义），
     * 该用 {@code /effect} 或角色自己的构造函数，配置里写不了。
     */
    @NoConfig("对象列表：加元素要构造合法对象并补 id，请用 /effect 或角色自己的构造函数")
    @DataField("effects")
    private final EffectContainer entityEffectList = new EffectContainer();
    private IShowSpecialMes showSpecialMes;
    /**
     * 当前战斗引用。
     * <p>
     * <b>不进配置文件</b>：这是<b>运行期上下文</b>，不是模板属性；注册表模板本来就是 {@code null}，
     * 写进配置没有任何可生效的对象。
     */
    @NoConfig("运行期上下文（当前战斗引用），注册表模板上本来就是 null，配置里写它没有意义")
    private Fight participateFight;
    /**
     * 当前回合条目。
     * <p>
     * <b>不进配置文件</b>：同 {@link #participateFight}，是运行期上下文，不是模板属性。
     */
    @NoConfig("运行期上下文（当前回合），不是模板属性")
    private TurnEntry presentTurn;
    /**
     * 行动控制器（技能表住在里面）。
     * <p>
     * <b>不进配置文件</b>：控制器带技能列表与 AI 状态，是<b>行为对象</b>不是数据
     * （{@code /data get} 里刻意跳过它，见 {@code notes_for_llm/subsystems/70-DATA.md} §5.10.1）。
     */
    @NoConfig("行为对象（带技能表与 AI 状态），/data 那边也刻意不 dump")
    private UniversalController controller;
    private String description;

    /**
     * 构造一个空白的生物（所有属性取默认值）。
     */
    public LivingThing() {

    }

    /**
     * 复制构造器（深拷贝）。复制大部分战斗属性、效果列表、法力、背包、Tag 权重，
     * 并让控制器自己复制一份（{@link UniversalController#copy(LivingThing)} ——
     * 每种控制器重写它，所以副本的控制器类型与原来一致）。
     * <p>
     * 战斗上下文（participateFight）与当前回合（presentTurn）不会被复制。
     *
     * @param other 被复制的生物
     */
    public LivingThing(LivingThing other) {
        super(other.getName(), other.getId(), other.getLevel());
        // 属性组件里"面板属性"的那一半（五行组 5 抗性 + 5 法力成长，全局组 penetration / enhance / baseCriticalDamage）
        // 跟着副本走；五行组的 5 单元素穿透 / 5 元素增伤是临时属性，刻意不带过去（见 AttributeProfile#copyFrom）。
        this.attributes.copyFrom(other.attributes);
        this.description = other.description;
        this.setType(other.getType());
        // alive 不复制：它是"状态"，按项目规则（面板复制、状态清零）副本一律从存活开始。
        this.setShowSpecialMes(other.getShowSpecialMes());
        this.damageModifiers.addAll(other.damageModifiers);
        this.entityEffectList.replaceAll(other.getEntityEffectList());
        this.damageReductions.addAll(other.damageReductions);
        this.participateFight = null;
        this.presentTurn = null;
        // 控制器复制交给它自己（每种控制器重写 copy(LivingThing)）——
        // 以前这里是一串 instanceof，每加一种控制器都得回来改本类，漏改就静默退化成随机 AI。
        // 与技能那边的 Skill#copy() 是同一个套路。
        this.controller = other.getController() == null ? null : other.getController().copy(this);
        if (this.controller != null && this.controller.getiInitialize() != null) {
            this.controller.getiInitialize().initialize(this.controller);
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
        this.attributes.setElement(yu);
        this.setHpGrow(hp);
        this.setAttackGrow(atk);
        this.setDefenceGrow(defence);
        this.attributes.setFireResistance(fireResistance);
        this.attributes.setWaterResistance(waterResistance);
        this.attributes.setMetalResistance(metalResistance);
        this.attributes.setWoodResistance(woodResistance);
        this.attributes.setDirtResistance(dirtResistance);
        this.setType(type);
        attributes.setAlive(true);
        this.attributes.setDefenceLoss(0);
        // 构造期直接铺基础值：不走 setHp（那会发 HpRestorationEvent、还会拿"还没铺好的"上限去夹）
        this.attributes.initPanel(
                (long) ((l - 1) * getHpGrow() + formulaHpBase()),
                (long) ((l - 1) * getDefenceGrow() + formulaDefenceBase()),
                (long) (formulaAttackBase() + getAttackGrow() * (l - 1)));
        this.attributes.setBaseSpeed(speed);
        switch (this.getElementSort()) {
            case METAL -> {
                this.setMetalManaGrow(20);
                this.setWoodManaGrow(4);
                this.setWaterManaGrow(10);
                this.setFireManaGrow(1);
                this.setDirtManaGrow(10);
            }
            case WOOD -> {
                this.setMetalManaGrow(1);
                this.setWoodManaGrow(20);
                this.setWaterManaGrow(10);
                this.setFireManaGrow(10);
                this.setDirtManaGrow(4);
            }
            case WATER -> {
                this.setMetalManaGrow(10);
                this.setWoodManaGrow(10);
                this.setWaterManaGrow(20);
                this.setFireManaGrow(4);
                this.setDirtManaGrow(1);
            }
            case FIRE -> {
                this.setMetalManaGrow(4);
                this.setWoodManaGrow(10);
                this.setWaterManaGrow(1);
                this.setFireManaGrow(20);
                this.setDirtManaGrow(10);
            }
            case DIRT -> {
                this.setMetalManaGrow(10);
                this.setWoodManaGrow(1);
                this.setWaterManaGrow(4);
                this.setFireManaGrow(10);
                this.setDirtManaGrow(20);
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
        this.attributes.setElement(u);
    }

    /**
     * 构造一个仅指定速度的生物（其余属性使用默认值）。
     *
     * @param speed 速度
     */
    public LivingThing(long speed) {
        this.attributes.setBaseSpeed(speed);

    }

    /**
     * 用具名链式构造器造一个生物（推荐写法；内部仍然走下面的 14 参构造器，行为逐字节一致）。
     * <p>
     * 14 个位置参数里有 6 个连着的 {@code double}，顺序错一个不会报错、只会让数值悄悄错掉；
     * 用 Builder 可以写成 {@code LivingThing.builder("名", "id").level(125).element(DIRT)...build()}。
     *
     * @param name 名称
     * @param id   唯一标识
     * @return 构造器
     */
    public static LivingThingBuilder builder(String name, String id) {
        return new LivingThingBuilder(name, id);
    }

    /**
     * @return 面板生命公式的基数（默认 {@value RuleDefaults#FORMULA_HP_BASE}，
     * 可在 {@code GameRules.json} 的 {@code formula.hpBase} 改；改它 = 重新标定）
     */
    private static long formulaHpBase() {
        return GameRules.getLong(DataKeys.Rule.Formula.HP_BASE);
    }

    /**
     * @return 面板防御公式的基数（默认 {@value RuleDefaults#FORMULA_DEFENCE_BASE}）
     */
    private static long formulaDefenceBase() {
        return GameRules.getLong(DataKeys.Rule.Formula.DEFENCE_BASE);
    }

    /**
     * @return 面板攻击公式的基数（默认 {@value RuleDefaults#FORMULA_ATTACK_BASE}）
     */
    private static long formulaAttackBase() {
        return GameRules.getLong(DataKeys.Rule.Formula.ATTACK_BASE);
    }

    /**
     * @return 主元素法力的基数（默认 {@value RuleDefaults#MANA_MAIN_BASE}）
     */
    private static long manaMainBase() {
        return GameRules.getLong(DataKeys.Rule.Mana.MAIN_BASE);
    }

    /**
     * @return 其余元素法力的基数（默认 {@value RuleDefaults#MANA_OTHER_BASE}）
     */
    private static long manaOtherBase() {
        return GameRules.getLong(DataKeys.Rule.Mana.OTHER_BASE);
    }

    /**
     * 给一个减伤来源起个<b>能看懂的名字</b>，好让 {@code /data} 里看得见"这条减伤是谁给的"。
     * <p>
     * {@link #getDamageReductions()} 里的 {@code source} 是按对象身份比较的键，往往是个
     * 匿名对象，{@code DataBridge} 会把它跳过，于是 {@code /data} 里只剩 {@code {percent:0.04d}} ——
     * 完全看不出是【醉意】还是药水（用户 2026-09 实测反馈过）。
     * <p>
     * 取名顺序：本项目自己的 {@link DamageReductionSource} → 有效果/技能/物品就读它们的 id/名字
     * → 是字符串就直接用 → 都不认识就退回类名。它<b>只用于显示</b>，不参与任何判断。
     *
     * @param source 减伤来源；调用方已保证非 {@code null}
     * @return 可读名字
     */
    static String describeReductionSource(Object source) {
        if (source instanceof DamageReductionSource named) {
            return named.toString();
        }
        if (source instanceof Effect effect) {
            return effect.getID() == null ? effect.getClass().getSimpleName() : effect.getID();
        }
        if (source instanceof Skill skill) {
            return skill.getName() == null ? skill.getClass().getSimpleName() : skill.getName();
        }
        if (source instanceof Item item) {
            return item.getId() == null ? item.getClass().getSimpleName() : item.getId();
        }
        if (source instanceof CharSequence text) {
            return text.toString();
        }
        return source.getClass().getSimpleName();
    }

    /**
     * 挂一条属性加成（效果/技能用）。同一个（类型 + 来源）重复挂等于更新，是幂等的。
     *
     * @param type    属性类型
     * @param percent 百分比（0.2 = +20%）
     * @param amount  固定值
     * @param source  来源对象（按身份比较，摘除时用；效果通常传 {@code this}）
     */
    public void addPropertyEnhance(PropertyEnhanceType type, double percent, double amount, Object source) {
        this.attributes.addPropertyEnhance(type, percent, amount, source);
        if (type == PropertyEnhanceType.MAX_HP) {
            renewHp();
        }
    }

    /**
     * 摘掉某个来源挂的全部属性加成（幂等；摘两次也不会把数值减成负数）。
     *
     * @param source 来源对象
     * @return 是否真的摘掉了东西
     */
    public boolean removePropertyEnhance(Object source) {
        boolean removed = this.attributes.removePropertyEnhances(source);
        if (removed) {
            renewHp();
        }
        return removed;
    }

    /**
     * @return 当前所有属性加成条目（{@code /data} 里就是它）
     */
    public List<PropertyEnhance> getPropertyEnhances() {
        return this.attributes.getPropertyEnhances();
    }

    /**
     * @param type 属性类型
     * @return 该类型所有条目的百分比之和
     */
    public double getPropertyEnhancePercent(PropertyEnhanceType type) {
        return this.attributes.percentOf(type);
    }

    /**
     * @param type 属性类型
     * @return 该类型所有条目的固定值之和
     */
    public double getPropertyEnhanceAmount(PropertyEnhanceType type) {
        return this.attributes.amountOf(type);
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
     * <b>统一治疗入口</b>：把 {@code amount} 按「受到的治疗量加成」放大后回血，并夹在上限内。
     * <p>
     * <b>为什么要有它</b>：以前治疗散落在各处（技能、物品、效果）写着
     * {@code setHp(getHp() + n)}，{@code healEnhancePercent} 无处生效。现在凡是"治疗"都走这里：
     * 实际回血 = {@code amount × (1 + attributes.percentOf(PropertyEnhanceType.HEAL))}。
     * <p>
     * <b>它不负责</b>：伤害、生命上限变化、消耗生命 —— 那些仍直接 {@link #setHp(long)}。
     *
     * @param amount 原始治疗量（正数；{@code <= 0} 直接返回 0）
     * @return 实际回了多少（已按上限夹取，可能小于放大后的值）
     */
    public long heal(long amount) {
        if (amount <= 0 || !isAlive()) {
            // 2026-11-15 第 76 批（作者要求）：死人不回血 —— 复活请走 setHp（那是另一条语义）
            return 0;
        }
        long before = getHp();
        long boosted = (long) (amount * (1 + attributes.percentOf(PropertyEnhanceType.HEAL)));
        setHp(before + boosted);
        long healed = getHp() - before;
        // 治疗结算完毕（第 74 批新增事件）：带申请量与实际回血量，供播报/统计/联动使用
        cn.gfhnv.game.event.EventBus.post(new cn.gfhnv.game.event.EntityHealEvent(this, amount, healed));
        return healed;
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
     * @return 生物附加的固定额外伤害值
     */
    public long getExtraDamage() {
        return attributes.getExtraDamage();
    }

    /**
     * 设置生物附加的固定额外伤害值。
     *
     * @param extraDamage 固定额外伤害值
     */
    public void setExtraDamage(long extraDamage) {
        this.attributes.setExtraDamage(extraDamage);
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
     * @return 单体伤害倍率 = 面板基础值 × **Π(每个来源的乘数)**
     */
    public double getIndividualMultipleArea() {
        return attributes.getIndividualMultipleArea();
    }

    /**
     * 设置单体伤害倍率的<b>面板基础值</b>（来源乘数不受影响）。
     *
     * @param individualMultipleArea 单体伤害倍率（面板基础值）
     */
    public void setIndividualMultipleArea(double individualMultipleArea) {
        this.attributes.setIndividualMultipleArea(individualMultipleArea);
    }

    /**
     * 挂一个「单体伤害倍率」的来源乘数（与 {@link #addDamageReduction(Object, double)} 同形）。
     * <p>
     * <b>按来源身份覆盖</b>：同一个 {@code source} 再挂一次会<b>替换</b>旧值，不会叠两份 ——
     * 这样"同一个 buff 被刷新"不会越叠越高。
     *
     * @param source 来源对象（按 {@code ==} 身份比较；{@code null} 直接忽略）
     * @param factor 乘数（1.0 = 不变；1.5 = 加五成；0.5 = 减半）
     */
    public void addIndividualMultipleArea(Object source, double factor) {
        this.attributes.addIndividualMultipleArea(source, factor);
    }

    /**
     * 摘掉某个来源的「单体伤害倍率」乘数。
     *
     * @param source 来源对象（按 {@code ==} 身份比较）
     * @return 有没有摘掉
     */
    public boolean removeIndividualMultipleArea(Object source) {
        return this.attributes.removeIndividualMultipleArea(source);
    }

    /**
     * 清空所有「单体伤害倍率」来源乘数（面板基础值不动）。
     * <p>
     * 由技能/机制挂上来的乘数走的是这里 —— 它们不走效果生命周期，战斗结束时没人摘，
     * 留着会渗进下一局（与减伤同一个理由，见 {@code whenFightEnds}）。
     */
    public void clearIndividualMultipleAreaModifiers() {
        this.attributes.clearIndividualMultipleAreaModifiers();
    }

    /**
     * 链式设置火抗性。
     *
     * @param fireResistance 火抗性
     * @return 当前生物实例
     */
    public LivingThing facSetFireResistance(double fireResistance) {
        this.attributes.setFireResistance(fireResistance);
        return this;
    }

    /**
     * 链式设置水抗性。
     *
     * @param waterResistance 水抗性
     * @return 当前生物实例
     */
    public LivingThing facSetWaterResistance(double waterResistance) {
        this.attributes.setWaterResistance(waterResistance);
        return this;
    }

    /**
     * 链式设置金抗性。
     *
     * @param metalResistance 金抗性
     * @return 当前生物实例
     */
    public LivingThing facSetMetalResistance(double metalResistance) {
        this.attributes.setMetalResistance(metalResistance);
        return this;
    }

    /**
     * 链式设置木抗性。
     *
     * @param woodResistance 木抗性
     * @return 当前生物实例
     */
    public LivingThing facSetWoodResistance(double woodResistance) {
        this.attributes.setWoodResistance(woodResistance);
        return this;
    }

    /**
     * 链式设置土抗性。
     *
     * @param dirtResistance 土抗性
     * @return 当前生物实例
     */
    public LivingThing facSetDirtResistance(double dirtResistance) {
        this.attributes.setDirtResistance(dirtResistance);
        return this;
    }

    /**
     * @param element 五行之一（{@link #MANA_ELEMENTS} 里的元素，调用方负责保证）
     * @return 该元素的法力成长系数
     */
    private double manaGrowOf(ElementSort element) {
        return switch (element) {
            case METAL -> this.attributes.getMetalManaGrow();
            case WOOD -> this.attributes.getWoodManaGrow();
            case WATER -> this.attributes.getWaterManaGrow();
            case FIRE -> this.attributes.getFireManaGrow();
            case DIRT -> this.attributes.getDirtManaGrow();
            // UNIVERSAL 不是五行之一（也不在 MANA_ELEMENTS 里），没有成长系数
            case UNIVERSAL -> 0;
        };
    }

    /**
     * 按元素类型获取对应的法力。
     *
     * @param elementSortNeeded 需要的元素类型
     * @return 对应的法力实例；若不存在返回 {@code null}
     */
    public Mana getMana(ElementSort elementSortNeeded) {
        for (Mana mana : getManas()) {
            if (mana.getElementSort().equals(elementSortNeeded)) return mana;
        }
        return null;
    }

    /**
     * 初始化五行法力。主元素法力上限为 {@code 成长 * (等级-1) + 200}，其余元素为
     * {@code 成长 * (等级-1) + 20}。
     * <p>
     * 那两个基数可以在 {@code config/gameConfig/GameRules.json} 的 {@code mana} 段里改
     * （{@code mainBase} / {@code otherBase}）；不写就用 {@link RuleDefaults} 里的出厂值。
     * <p>
     * 法力表<b>恒为 5 条、顺序固定</b>（见 {@link #MANA_ELEMENTS}）；元素属性不是五行之一
     * （{@link ElementSort#UNIVERSAL}，或者还没设元素）时表格为空 —— 不凭空造 5 条。
     */
    public void initialMana() {
        ElementSort main = this.getElementSort();
        List<Mana> rebuilt = new ArrayList<>(MANA_ELEMENTS.size());
        if (MANA_ELEMENTS.contains(main)) {
            long levelFactor = this.getLevel() - 1;
            for (ElementSort element : MANA_ELEMENTS) {
                long base = element == main ? manaMainBase() : manaOtherBase();
                rebuilt.add(new Mana(this.manaGrowOf(element) * levelFactor + base, element));
            }
        }
        this.attributes.setManas(rebuilt);
    }

    /**
     * 每回合恢复法力。每个元素法力按 {@code 等级/100 * 成长 + 100} 恢复，
     * 主元素额外恢复 {@code 等级} 点法力。
     */
    public void recoverManaEveryTurn() {
        for (Mana mana : getManas()) {
            if (mana.getElementSort().equals(ElementSort.METAL)) {
                mana.setAmount(mana.getAmount() + this.getLevel() / 100.00 * this.getMetalManaGrow() + 100);
            }
            if (this.getElementSort().equals(ElementSort.METAL)) {
                mana.setAmount(mana.getAmount() + this.getLevel());
            }
            if (mana.getElementSort().equals(ElementSort.WOOD)) {
                mana.setAmount(mana.getAmount() + this.getLevel() / 100.00 * this.getWoodManaGrow() + 100);
            }
            if (this.getElementSort().equals(ElementSort.WOOD)) {
                mana.setAmount(mana.getAmount() + this.getLevel());
            }
            if (mana.getElementSort().equals(ElementSort.WATER)) {
                mana.setAmount(mana.getAmount() + this.getLevel() / 100.00 * this.getWaterManaGrow() + 100);
            }
            if (this.getElementSort().equals(ElementSort.WATER)) {
                mana.setAmount(mana.getAmount() + this.getLevel());
            }
            if (mana.getElementSort().equals(ElementSort.FIRE)) {
                mana.setAmount(mana.getAmount() + this.getLevel() / 100.00 * this.getFireManaGrow() + 100);
            }
            if (this.getElementSort().equals(ElementSort.FIRE)) {
                mana.setAmount(mana.getAmount() + this.getLevel());
            }
            if (mana.getElementSort().equals(ElementSort.DIRT)) {
                mana.setAmount(mana.getAmount() + this.getLevel() / 100.00 * this.getDirtManaGrow() + 100);
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
        return attributes.getManas();
    }

    /**
     * 设置五行法力列表。
     *
     * @param manas 法力列表
     */
    public void setManas(List<Mana> manas) {
        this.attributes.setManas(manas);
    }

    /**
     * @return 生命成长系数
     */
    public double getHpGrow() {
        return attributes.getHpGrow();
    }

    /**
     * 设置生命成长系数。
     *
     * @param hpGrow 生命成长系数
     */
    public void setHpGrow(double hpGrow) {
        this.attributes.setHpGrow(hpGrow);
    }

    /**
     * @return 攻击成长系数
     */
    public double getAttackGrow() {
        return attributes.getAttackGrow();
    }

    /**
     * 设置攻击成长系数。
     *
     * @param attackGrow 攻击成长系数
     */
    public void setAttackGrow(double attackGrow) {
        this.attributes.setAttackGrow(attackGrow);
    }

    /**
     * @return 防御成长系数
     */
    public double getDefenceGrow() {
        return attributes.getDefenceGrow();
    }

    /**
     * 设置防御成长系数。
     *
     * @param defenceGrow 防御成长系数
     */
    public void setDefenceGrow(double defenceGrow) {
        this.attributes.setDefenceGrow(defenceGrow);
    }

    /**
     * @return 元素属性（金木水火土）
     */
    public ElementSort getElementSort() {
        return attributes.getElement();
    }

    /**
     * 设置元素属性。
     *
     * @param elementSort 元素属性
     */
    public void setElementSort(ElementSort elementSort) {
        this.attributes.setElement(elementSort);
    }

    /**
     * @return 金法力成长系数
     */
    public double getMetalManaGrow() {
        return attributes.getMetalManaGrow();
    }

    /**
     * 设置金法力成长系数。
     *
     * @param metalManaGrow 金法力成长系数
     */
    public void setMetalManaGrow(double metalManaGrow) {
        this.attributes.setMetalManaGrow(metalManaGrow);
    }

    /**
     * @return 木法力成长系数
     */
    public double getWoodManaGrow() {
        return attributes.getWoodManaGrow();
    }

    /**
     * 设置木法力成长系数。
     *
     * @param woodManaGrow 木法力成长系数
     */
    public void setWoodManaGrow(double woodManaGrow) {
        this.attributes.setWoodManaGrow(woodManaGrow);
    }

    /**
     * @return 水法力成长系数
     */
    public double getWaterManaGrow() {
        return attributes.getWaterManaGrow();
    }

    /**
     * 设置水法力成长系数。
     *
     * @param waterManaGrow 水法力成长系数
     */
    public void setWaterManaGrow(double waterManaGrow) {
        this.attributes.setWaterManaGrow(waterManaGrow);
    }

    /**
     * @return 火法力成长系数
     */
    public double getFireManaGrow() {
        return attributes.getFireManaGrow();
    }

    /**
     * 设置火法力成长系数。
     *
     * @param fireManaGrow 火法力成长系数
     */
    public void setFireManaGrow(double fireManaGrow) {
        this.attributes.setFireManaGrow(fireManaGrow);
    }

    /**
     * @return 土法力成长系数
     */
    public double getDirtManaGrow() {
        return attributes.getDirtManaGrow();
    }

    /**
     * 设置土法力成长系数。
     *
     * @param dirtManaGrow 土法力成长系数
     */
    public void setDirtManaGrow(double dirtManaGrow) {
        this.attributes.setDirtManaGrow(dirtManaGrow);
    }

    /**
     * 链式设置生命上限。
     *
     * @param hpMax 生命上限
     * @return 当前生物实例
     */
    public LivingThing facSetHpMax(long hpMax) {
        this.attributes.setBaseHpMax(hpMax);
        return this;
    }

    /**
     * 链式设置基础暴击伤害倍率加成。
     *
     * @param criticalDamage 基础暴击伤害倍率加成
     * @return 当前生物实例
     */
    public LivingThing facSetCriticalDamage(double criticalDamage) {
        this.attributes.setBaseCriticalDamage(criticalDamage);
        return this;
    }

    /**
     * 链式设置基础暴击率。
     *
     * @param criticalRate 基础暴击率
     * @return 当前生物实例
     */
    public LivingThing facSetCriticalRate(double criticalRate) {
        this.attributes.setBaseCriticalRate(criticalRate);
        return this;
    }

    /**
     * 链式设置存活状态。
     *
     * @param alive 是否存活
     * @return 当前生物实例
     */
    public LivingThing facSetAlive(boolean alive) {
        attributes.setAlive(alive);
        return this;
    }

    /**
     * 链式设置全属性穿透。
     *
     * @param penetration 全属性穿透
     * @return 当前生物实例
     */
    public LivingThing facSetPenetration(double penetration) {
        this.attributes.setPenetration(penetration);
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
     * @param defence 防御力
     * @return 当前生物实例
     */
    public LivingThing facSetDefence(long defence) {
        this.attributes.setBaseDefence(defence);
        return this;
    }

    /**
     * 链式设置速度。
     *
     * @param speed 速度
     * @return 当前生物实例
     */
    public LivingThing facSetSpeed(long speed) {
        this.attributes.setBaseSpeed(speed);
        return this;
    }

    /**
     * 链式设置攻击力。
     *
     * @param attack 攻击力
     * @return 当前生物实例
     */
    public LivingThing facSetAttack(long attack) {
        this.attributes.setBaseAttack(attack);
        return this;
    }

    /**
     * 链式设置全属性增伤百分比。
     *
     * @param enhance 全属性增伤百分比
     * @return 当前生物实例
     */
    public LivingThing facSetEnhance(double enhance) {
        this.attributes.setEnhance(enhance);
        return this;
    }

    /**
     * 链式设置防御削减百分比。
     *
     * @param defenseLoss 防御削减百分比
     * @return 当前生物实例
     */
    public LivingThing facSetDefenceLoss(double defenseLoss) {
        this.attributes.setDefenceLoss(defenseLoss);
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
        return attributes.getFireResistance();
    }

    /**
     * 设置火抗性。
     *
     * @param fireResistance 火抗性
     */
    public void setFireResistance(double fireResistance) {
        this.attributes.setFireResistance(fireResistance);
    }

    /**
     * @return 水抗性
     */
    public double getWaterResistance() {
        return attributes.getWaterResistance();
    }

    /**
     * 设置水抗性。
     *
     * @param waterResistance 水抗性
     */
    public void setWaterResistance(double waterResistance) {
        this.attributes.setWaterResistance(waterResistance);
    }

    /**
     * @return 金抗性
     */
    public double getMetalResistance() {
        return attributes.getMetalResistance();
    }

    /**
     * 设置金抗性。
     *
     * @param metalResistance 金抗性
     */
    public void setMetalResistance(double metalResistance) {
        this.attributes.setMetalResistance(metalResistance);
    }

    /**
     * @return 木抗性
     */
    public double getWoodResistance() {
        return attributes.getWoodResistance();
    }

    /**
     * 设置木抗性。
     *
     * @param woodResistance 木抗性
     */
    public void setWoodResistance(double woodResistance) {
        this.attributes.setWoodResistance(woodResistance);
    }

    /**
     * @return 土抗性
     */
    public double getDirtResistance() {
        return attributes.getDirtResistance();
    }

    /**
     * 设置土抗性。
     *
     * @param dirtResistance 土抗性
     */
    public void setDirtResistance(double dirtResistance) {
        this.attributes.setDirtResistance(dirtResistance);
    }

    /**
     * @return 最终暴击率（基础暴击率 × (1 + 暴击率增强百分比) + 暴击率增强固定值）
     */
    public double getCriticalRate() {
        return attributes.getBaseCriticalRate() * (1 + attributes.percentOf(PropertyEnhanceType.CRITICAL_RATE))
                + attributes.amountOf(PropertyEnhanceType.CRITICAL_RATE);
    }

    /**
     * 设置基础暴击率。
     *
     * @param criticalRate 基础暴击率
     */
    public void setCriticalRate(double criticalRate) {
        this.attributes.setBaseCriticalRate(criticalRate);
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
     * @return 带阵营的名字（例如 {@code 张三（我方）}）—— 实现见 {@link EntityDisplay#nameWithSide}
     */
    public String getNameWithSide() {
        return EntityDisplay.nameWithSide(this);
    }

    /**
     * @return 带短标识的名字（例如 {@code 至黑之剑，盗火行者#0a1b2c}）—— 实现见 {@link EntityDisplay#nameWithUuid}
     */
    public String getNameWithUuid() {
        return EntityDisplay.nameWithUuid(this);
    }

    /**
     * @return 带短标识与阵营的名字（例如 {@code 至黑之剑，盗火行者#0a1b2c（我方）}）——
     * 实现见 {@link EntityDisplay#nameWithUuidAndSide}
     */
    public String getNameWithUuidAndSide() {
        return EntityDisplay.nameWithUuidAndSide(this);
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
        this.entityEffectList.replaceAll(entityEffectList);
    }

    /**
     * 追加一个效果（<b>不过滤、不合并</b>）。给"我确定要原样放进去"的调用方用；
     * 常规施加效果请用 {@link #addEffect(Effect)}（同 id 会合并/刷新）。
     *
     * @param effect 效果
     */
    public void addEffectDirect(Effect effect) {
        this.entityEffectList.addEffect(effect);
    }

    /**
     * 批量摘除效果（净化类效果用）。<b>删之前调用方要自己调 {@code whenLastTimeEnd}</b>。
     *
     * @param filter 判定
     * @return 有没有摘掉东西
     */
    public boolean removeEffectsIf(java.util.function.Predicate<Effect> filter) {
        return this.entityEffectList.removeEffectsIf(filter);
    }

    /**
     * 整表清空效果（{@code whenFightEnds} 与调试命令用；调用方负责先跑收尾）。
     */
    public void clearEffects() {
        this.entityEffectList.clearEffects();
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
                    // 叠加也发一次"效果命中"（第 74 批新增事件）：扩展拿到的仍是同一个实例
                    cn.gfhnv.game.event.EventBus.post(new cn.gfhnv.game.event.EffectAppliedEvent(target, existing));
                } else {
                    // 2026-11-15 第 78 批（分析 K1）：更高等级替换旧实例之前，必须先让旧实例收尾
                    // （它 initialEffect 时加上的数值要靠 whenLastTimeEnd 减回去），否则属性永久残留。
                    existing.whenLastTimeEnd(target);
                    target.entityEffectList.setEffect(i, effect);
                    effect.initialEffect(target);
                    cn.gfhnv.game.event.EventBus.post(new cn.gfhnv.game.event.EffectAppliedEvent(target, effect));
                }
                return;
            }
        }
        target.entityEffectList.addEffect(effect);
        effect.initialEffect(target);
        cn.gfhnv.game.event.EventBus.post(new cn.gfhnv.game.event.EffectAppliedEvent(target, effect));
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
        this.entityEffectList.removeEffect(ef);
        // 单条移除（第 74 批新增事件）：批量清理（removeEffectsIf / 战斗结束清空）不逐条发
        cn.gfhnv.game.event.EventBus.post(new cn.gfhnv.game.event.EffectRemovedEvent(this, ef));
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
        addDamageReduction(this.baseDamageReduction, damageAbsorbedPercent);
    }

    /**
     * 增加一个减伤来源（<b>乘算</b>叠加）。
     * <p>
     * 承伤倍率 = Π(1 − 每个减伤)：50% 与 25% 两个来源 → 只受 37.5% 伤害。
     * 同一个来源（按对象身份判断）重复添加只保留最后一次，所以技能反复触发不会越叠越多。
     * <p>
     * <b>负数 = 增伤</b>（2026-10-24 起）：{@code -0.5} 表示"受到的伤害 +50%"（承伤倍率 ×1.5），
     * 千冶·刃的【煞火缠身】就是这么写的。比例夹在 {@code [-1, 1]}：
     * 正的那一头最多减到 0 伤害（{@link #takeDamage(DamageEvent)} 不会算出负伤害 = 治疗），
     * 负的那一头最多翻倍。
     * <p>
     * ⚠️ 旧实现在这里把负数<b>静默丢掉</b>（钳制 {@code [0,1]} + 只保留 {@code > 0}），
     * 于是"写 -0.5 想增伤"会看起来完全没效果 —— 改这个语义时<b>两处都得动</b>。
     *
     * @param source  来源，按对象身份区分（效果实例、技能、装备都可以）
     * @param percent 减伤比例（0.25 = 少受 25%；{@code -0.5} = 多受 50%）
     */
    public void addDamageReduction(Object source, double percent) {
        if (source == null) {
            return;
        }
        removeDamageReduction(source);
        // 2026-10-24：允许负数 —— 负的减伤 = 增伤（-0.5 → 承伤 ×1.5）。
        // 旧实现在这里 Math.max(0, ...) + if (>0) 把负数静默丢掉了，写 -0.5 会看起来完全没效果。
        double clamped = Math.max(-1, Math.min(1, percent));
        if (clamped != 0) {
            damageReductions.add(new DamageReduction(source, describeReductionSource(source), clamped));
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
     * 承伤倍率：{@code Π(1 − 每个减伤)}。
     * <p>
     * 例：50% 与 25% → {@code 0.5 × 0.75 = 0.375}（只受 37.5% 伤害），而不是相加的 25%。
     * 伤害计算（{@link cn.gfhnv.game.damage.DamageCalculate}）用的就是这个值。
     * <p>
     * <b>可以 &gt; 1</b>（2026-10-24 起）：负数减伤 = 增伤，例如一个 {@code -0.5} 的来源 →
     * 倍率 1.5（多受 50%）。<b>下界仍然是 0</b> —— 减伤叠满只会把伤害压到 0，
     * 不能算出负伤害（负数会被 {@link #takeDamage(DamageEvent)} 当成治疗）。
     *
     * @return 承伤倍率；没有任何减伤时是 1；有增伤时 &gt; 1
     */
    public double getDamageTakenMultiplier() {
        double multiplier = 1;
        for (DamageReduction reduction : damageReductions) {
            multiplier *= (1 - reduction.percent());
        }
        // 上界不再夹：负数减伤 = 增伤，倍率可以 > 1。下界仍是 0 —— 减伤叠满只能把伤害压到 0，
        // 不能算出负伤害（负数会被 takeDamage(DamageEvent) 当成治疗）。
        return Math.max(0, multiplier);
    }

    /**
     * @return 全属性穿透
     */
    public double getPenetration() {
        return attributes.getPenetration();
    }

    /**
     * 设置全属性穿透。
     *
     * @param penetration 全属性穿透
     */
    public void setPenetration(double penetration) {
        this.attributes.setPenetration(penetration);
    }

    /**
     * @param element 元素
     * @return 该元素的有效抗性（全属性条目 + 该元素条目相加）
     */
    public double getResistance(cn.gfhnv.game.system.ElementSort element) {
        return this.attributes.getResistance(element);
    }

    /**
     * 写基础抗性（配置键 {@code metalResistance} 等走这里）。
     *
     * @param element 元素
     * @param value   抗性
     */
    public void setResistance(cn.gfhnv.game.system.ElementSort element, double value) {
        this.attributes.setResistance(element, value);
    }

    /**
     * 挂一条临时抗性（效果/技能用；同（元素 + 来源）重复挂等于更新）。
     *
     * @param element 元素（{@code null} = 全属性）
     * @param amount  平值
     * @param source  来源对象（按身份比较，摘除时用）
     */
    public void addResistance(cn.gfhnv.game.system.ElementSort element, double amount, Object source) {
        this.attributes.addResistance(element, amount, source);
    }

    /**
     * 摘掉某个来源挂的全部抗性（幂等）。
     *
     * @param source 来源对象
     * @return 是否真的摘掉了东西
     */
    public boolean removeResistance(Object source) {
        return this.attributes.removeResistance(source);
    }

    /**
     * @return 当前所有抗性条目（{@code /data} 里就是它）
     */
    public List<ElementValue> getResistances() {
        return this.attributes.getResistances();
    }

    /**
     * @param element 元素
     * @return 只对该元素生效的增伤（不含全属性增伤 {@code enhance}）
     */
    public double getElementDamageEnhance(cn.gfhnv.game.system.ElementSort element) {
        return this.attributes.getElementDamageEnhance(element);
    }

    /**
     * 挂一条临时增伤（效果/技能用；同（元素 + 来源）重复挂等于更新）。
     *
     * @param element 元素（{@code null} = 全属性）
     * @param amount  平值
     * @param source  来源对象（按身份比较，摘除时用）
     */
    public void addDamageEnhance(cn.gfhnv.game.system.ElementSort element, double amount, Object source) {
        this.attributes.addDamageEnhance(element, amount, source);
    }

    /**
     * 摘掉某个来源挂的全部增伤（幂等）。
     *
     * @param source 来源对象
     * @return 是否真的摘掉了东西
     */
    public boolean removeDamageEnhance(Object source) {
        return this.attributes.removeDamageEnhance(source);
    }

    /**
     * @return 当前所有增伤条目（{@code /data} 里就是它）
     */
    public List<ElementValue> getDamageEnhances() {
        return this.attributes.getDamageEnhances();
    }

    /**
     * @param element 伤害元素
     * @return 只对该元素生效的穿透（不含全属性条目）
     */
    public double getElementPenetration(cn.gfhnv.game.system.ElementSort element) {
        return this.attributes.getElementPenetration(element);
    }

    /**
     * 挂一条临时穿透（效果/技能用；同（元素 + 来源）重复挂等于更新）。
     *
     * @param element 元素（{@code null} = 全属性）
     * @param amount  平值
     * @param source  来源对象（按身份比较，摘除时用）
     */
    public void addPenetration(cn.gfhnv.game.system.ElementSort element, double amount, Object source) {
        this.attributes.addPenetration(element, amount, source);
    }

    /**
     * 摘掉某个来源挂的全部穿透（幂等）。
     *
     * @param source 来源对象
     * @return 是否真的摘掉了东西
     */
    public boolean removePenetration(Object source) {
        return this.attributes.removePenetration(source);
    }

    /**
     * @return 当前所有穿透条目（{@code /data} 里就是它）
     */
    public List<ElementValue> getPenetrations() {
        return this.attributes.getPenetrations();
    }

    /**
     * @return 最终生命上限（基础上限 × (1 + 生命增强百分比) + 生命增强固定值）
     */
    public long getHpMax() {
        return (long) (attributes.getBaseHpMax() * (1 + attributes.percentOf(PropertyEnhanceType.MAX_HP))
                + attributes.amountOf(PropertyEnhanceType.MAX_HP));
    }

    /**
     * 设置基础生命上限。
     *
     * @param hpMax 基础生命上限
     */
    public void setHpMax(long hpMax) {
        this.attributes.setBaseHpMax(hpMax);
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
            attributes.setAlive(false);
        }
        if (getHp() > 0) {
            attributes.setAlive(true);
        }
        return attributes.isAlive();
    }

    /**
     * 直接设置存活状态。
     *
     * @param b {@code true} 存活，{@code false} 死亡
     */
    public void setAlive(boolean b) {
        attributes.setAlive(b);
    }

    /**
     * @return 全属性增伤百分比
     */
    public double getEnhance() {
        return attributes.getEnhance();
    }

    /**
     * 设置全属性增伤百分比。
     *
     * @param enhance 全属性增伤百分比
     */
    public void setEnhance(double enhance) {
        this.attributes.setEnhance(enhance);
    }

    /**
     * @return 防御削减百分比
     */
    public double getDefenceLoss() {
        return attributes.getDefenceLoss();
    }

    /**
     * 设置防御削减百分比。
     *
     * @param defenseLoss 防御削减百分比
     */
    public void setDefenceLoss(double defenseLoss) {
        this.attributes.setDefenceLoss(defenseLoss);
    }

    /**
     * @return 最终攻击力（基础攻击 × (1 + 攻击增强百分比) + 攻击增强固定值）
     */
    public long getAttack() {
        return (long) (attributes.getBaseAttack() * (1 + attributes.percentOf(PropertyEnhanceType.ATTACK))
                + attributes.amountOf(PropertyEnhanceType.ATTACK));
    }

    /**
     * 设置基础攻击力。
     *
     * @param attack 基础攻击力
     */
    public void setAttack(long attack) {
        this.attributes.setBaseAttack(attack);
    }

    /**
     * 受到伤害。根据 {@link DamageEvent} 计算新生命值，并依次经过全部
     * {@link IModifyDamage 伤害修正器}（见 {@link #modifyIncomingDamage}）后设置。
     * <p>
     * 结算完把<b>实际掉了多少血</b>写回 {@link Damage#setAppliedDamage(long)}：
     * 那是 {@code setHp} 前后差，已经被生命值夹取（0 / 上限）与免死类机制影响过。
     * 它和"公式算出来的值"是两件事 —— 攻击行显示的仍是后者。
     *
     * @param da 伤害事件
     */
    public void takeDamage(DamageEvent da) {
        long hpBefore = this.getHp();
        long newHp = hpBefore - da.getDamage().getCalculateDamageResult();
        newHp = modifyIncomingDamage(newHp, da);
        this.setHp(newHp);
        da.getDamage().setAppliedDamage(hpBefore - this.getHp());
        // 打印不在这里做：攻击行的完整文案（攻击者/目标/伤害/剩余 HP/技能名）统一在
        // makeDamage 里打，其它"直接调 getDamage"的场合（自测等）不需要刷屏。
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
     * @param hpGrow 生命成长系数
     * @return 当前生物实例
     */
    public LivingThing facSetHpGrow(double hpGrow) {
        this.setHpGrow(hpGrow);
        return this;
    }

    /**
     * 链式设置攻击成长系数。
     *
     * @param attackGrow 攻击成长系数
     * @return 当前生物实例
     */
    public LivingThing facSetAttackGrow(double attackGrow) {
        this.setAttackGrow(attackGrow);
        return this;
    }

    /**
     * 链式设置防御成长系数。
     *
     * @param defenceGrow 防御成长系数
     * @return 当前生物实例
     */
    public LivingThing facSetDefenceGrow(double defenceGrow) {
        this.setDefenceGrow(defenceGrow);
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
     * @param metalManaGrow 金法力成长系数
     * @return 当前生物实例
     */
    public LivingThing facSetMetalManaGrow(double metalManaGrow) {
        this.setMetalManaGrow(metalManaGrow);
        return this;
    }

    /**
     * 链式设置木法力成长系数。
     *
     * @param woodManaGrow 木法力成长系数
     * @return 当前生物实例
     */
    public LivingThing facSetWoodManaGrow(double woodManaGrow) {
        this.setWoodManaGrow(woodManaGrow);
        return this;
    }

    /**
     * 链式设置水法力成长系数。
     *
     * @param waterManaGrow 水法力成长系数
     * @return 当前生物实例
     */
    public LivingThing facSetWaterManaGrow(double waterManaGrow) {
        this.setWaterManaGrow(waterManaGrow);
        return this;
    }

    /**
     * 链式设置火法力成长系数。
     *
     * @param fireManaGrow 火法力成长系数
     * @return 当前生物实例
     */
    public LivingThing facSetFireManaGrow(double fireManaGrow) {
        this.setFireManaGrow(fireManaGrow);
        return this;
    }

    /**
     * 链式设置土法力成长系数。
     *
     * @param dirtManaGrow 土法力成长系数
     * @return 当前生物实例
     */
    public LivingThing facSetDirtManaGrow(double dirtManaGrow) {
        this.setDirtManaGrow(dirtManaGrow);
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
     * 把生物重置为"可再次参战"：重置回合、补满生命、清除效果、清掉临时属性、重置技能冷却并恢复法力。
     * <p>
     * <b>只应在整场战斗结束时调用</b>（{@code FightEndEventListener}）。
     * 单个生物死亡离场时请用 {@link #whenLeaveFight(Fight)} ——
     * 本方法里的 {@code setHp(getHpMax())} 会把已经死掉的生物复活。
     */
    public void whenFightEnds() {


        setPresentTurn(null);
        setHp((long) getHpMax());
        // ⚠️ 顺序要紧：**先**让每个效果自己收尾，**再** resetTemporary() 统一清零。
        // 反过来会让"减回去"落在已经被清零的字段上 → 战后属性加成变**负值**
        // （例如 -0.2），而 `HpEnhanceEffect` 的 renewHp() 还会把血夹到 0.8×上限。
        // 注意：resetTemporary() 不清 `enhance` / `defenceLoss`（它们是面板属性），
        // 所以效果把它们减回去仍然是有意义的。
        for (Effect effect : new ArrayList<>(getEntityEffectList())) effect.whenLastTimeEnd(this);
        this.setEntityEffectList(new ArrayList<>());
        // 临时属性全在属性组件里（五元素穿透/增伤 + 13 个 *Enhance* + 死字段 + 两个状态位）——
        // 实体这边不再有任何"清零"职责，面板属性也不在这张表里（它们跟着副本走）。
        this.attributes.resetTemporary();
        // 效果走完之后再扫一遍减伤：技能/机制挂上来的那些**不走效果生命周期**，
        // 战斗结束时没人摘，留着会渗进下一局 —— 最典型的是白厄【灾厄】那 75%
        // （`CalamitySoulscorchEdict` 挂、`Counterattack` 摘），变身打到一半战斗结束就带走了。
        // 清空是安全的：生产环境里**没有"构造期就永久挂着"的减伤**
        // （`baseDamageReduction` 只有旧写法 `setDamageAbsorbedPercent` 会加，而它没人调），
        // 层数减伤与二阶段免伤由 FlameReaver 在自己的 whenFightStart 重新挂，醉意由效果自己摘。
        this.clearDamageReductions();
        // 「单体伤害倍率」的来源乘数同理：技能挂上来的、不走效果生命周期，
        // 战斗结束没人摘就会渗进下一局（面板基础值不动，它由角色构造器重算）。
        this.clearIndividualMultipleAreaModifiers();
        for (Skill skill : getController().getSkills()) skill.setNowCoolDown(0);
        for (Mana mana : getManas()) mana.setAmount(mana.getAmountMax());
    }

    /**
     * 使用技能对目标造成伤害（构造 {@link DamageEvent} 并调用目标的 {@link #getDamage}）。
     * <p>
     * <b>攻击行的打印统一在这里做</b>：{@code A#短标识（我方）攻击了B#短标识（敌方）  -伤害  → HP 当前/上限  【技能名】}。
     * 以前是"各技能自己 print 前缀 + makeDamage 打伤害 + getDamage 打剩余 HP"三段拼的，
     * 结果<b>只要哪个技能漏了那句 print，日志里就会冒出一行没有主语的 "  -1109  → HP …"</b>
     * （实测踩过：{@code Counterattack} 的 6 次追加攻击）。收进来之后谁也漏不了，
     * 顺带还能把技能名一起打出来。
     * <p>
     * 攻守双方都带阵营（见 {@link #getNameWithSide()}）：双方可能有同名生物
     * （典型是各一只【残破容器】），不带阵营根本分不出是谁打谁。
     *
     * @param attacked 被攻击目标
     * @param skill    使用的技能（用来显示技能名；可为 {@code null}）
     */
    public void makeDamage(LivingThing attacked, Skill skill) {
        // 兼容重载（模组老写法 user.makeDamage(target, this)）：没传类型就按战技算。
        // 它只影响"伤害类型"这个标签，不影响任何数值；新代码请传显式类型。
        makeDamage(attacked, skill, DamageType.SpecialSkill);
    }

    /**
     * 使用技能对目标造成伤害，并声明这一击的<b>伤害类型</b>（可以多个，全都生效）。
     * <p>
     * 类型由技能自己传（{@code user.makeDamage(target, this, DamageType.CommonAttack)}）——
     * 不做按类自动推断：同一个技能类在不同形态/来源下可能算不同类型。
     *
     * @param attacked    被攻击目标
     * @param skill       使用的技能；可为 {@code null}
     * @param damageTypes 伤害类型（不传按 {@link DamageType#SpecialSkill} 算）
     */
    public void makeDamage(LivingThing attacked, Skill skill, DamageType... damageTypes) {
        DamageEvent damageEvent = new DamageEvent(this, attacked, skill, damageTypes);
        EventBus.post(damageEvent);
        attacked.takeDamage(damageEvent);
        // 显示**"应该造成的伤害"**（伤害公式算出来的那一刀），不是"这一击实际掉了多少血"。
        // 理由（2026-10-24 用户拍板）：90000 打在只剩 400 血的敌人身上，打"实际掉血"会显示 -400，
        // 看不出这一刀的威力；显示 -90000 才是玩家想看的数。
        // 代价：被免死/高额免伤拦下时，这一行的数字会大于"HP 前后差" —— 那是**故意的**。
        // "实际掉血"现在记在 Damage#getAppliedDamage() 里（不再只活在这一行的计算中间）。
        EntityDisplay.printAttackLine(this, attacked, skill, damageEvent.getDamage().getCalculateDamageResult(),
                damageEvent.getDamage().isCritical());
    }

    /**
     * @return 最终速度（基础速度 × (1 + 速度增强百分比) + 速度增强固定值）
     */
    public long getSpeed() {
        return (long) (attributes.getBaseSpeed() * (1 + attributes.percentOf(PropertyEnhanceType.SPEED))
                + attributes.amountOf(PropertyEnhanceType.SPEED));
    }

    /**
     * 设置基础速度。
     *
     * @param speed 基础速度
     */
    public void setSpeed(long speed) {
        this.attributes.setBaseSpeed(speed);
    }

    /**
     * @return 最终防御力（基础防御 × (1 + 防御增强百分比) + 防御增强固定值）
     */
    public long getDefence() {
        return (long) (attributes.getBaseDefence() * (1 + attributes.percentOf(PropertyEnhanceType.DEFENCE))
                + attributes.amountOf(PropertyEnhanceType.DEFENCE));
    }

    /**
     * 设置基础防御力。
     *
     * @param defence 基础防御力
     */
    public void setDefence(long defence) {
        this.attributes.setBaseDefence(defence);
    }

    /**
     * @return 当前生命值
     */
    public long getHp() {
        return attributes.getHp();
    }

    /**
     * 设置生命值。若减少则发布 {@link HpLossEvent}，若增加则发布 {@link HpRestorationEvent}；
     * 最终值被限制在 0 与生命上限之间。
     *
     * @param hp 新的生命值
     */
    public void setHp(long hp) {
        this.attributes.setHp(hp);
    }

    /**
     * @return 最终暴击伤害倍率加成
     */
    public double getCriticalDamage() {
        return attributes.getBaseCriticalDamage() * (1 + attributes.percentOf(PropertyEnhanceType.CRITICAL_DAMAGE))
                + attributes.amountOf(PropertyEnhanceType.CRITICAL_DAMAGE);
    }

    /**
     * 设置基础暴击伤害倍率加成。
     *
     * @param criticalDamage 基础暴击伤害倍率加成
     */
    public void setCriticalDamage(double criticalDamage) {
        this.attributes.setBaseCriticalDamage(criticalDamage);
    }

    /**
     * 把一个「即将结算的新生命值」依次交给所有伤害修正器处理。
     * <p>
     * 受到伤害（{@link #takeDamage(DamageEvent)}）与伤害试算
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
        return attributes.isAnticipating();
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
        boolean previous = attributes.isAnticipating();
        attributes.setAnticipating(true);
        try {
            return action.get();
        } finally {
            attributes.setAnticipating(previous);
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
     * <p>
     * {@code source} 是<b>按对象身份</b>比较的键（加/删都靠它，见
     * {@link #addDamageReduction(Object, double)}），它本身往往不是"数据对象"
     * （最常见的是一句 {@code new Object()}），所以 {@code /data} 里看不到它。
     * 为此额外带一个<b>可读的名字</b> {@code sourceName}：
     * 它只是给人看的，不参与任何判断。
     *
     * @param source     来源对象（按身份区分；{@code /data} 看不到）
     * @param sourceName 来源的可读名字（给 {@code /data} 看）
     * @param percent    减伤比例（已夹到 [0,1]）
     */
    public record DamageReduction(Object source, String sourceName, double percent) {
    }
}
