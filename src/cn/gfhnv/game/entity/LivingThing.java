package cn.gfhnv.game.entity;

import cn.gfhnv.game.data.DataField;
import cn.gfhnv.game.data.DataFlatten;
import cn.gfhnv.game.data.NoConfig;
import cn.gfhnv.game.data.NoData;
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
import cn.gfhnv.game.utils.ConsoleColor;
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
 *     暴击率/爆伤（criticalRate / criticalDMG）；</li>
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
     * 那 12 个 {@code *Enhance*} 运行时加成共同的 {@code @NoConfig} 理由。
     * <p>
     * <b>为什么 12 个字段共用一句话</b>：它们的生命周期完全一样（效果加减 → 结束清零 → 不复制），
     * 逐字抄 12 遍只会让"改一个忘十一个"更容易。真正的依据写在
     * {@link #clearTemporaryAttributes()} 的 javadoc 与复制构造器上。
     * <p>
     * <b>这句话必须是真的</b>（{@code @NoConfig} 的纪律：写不出真理由就不该挡）——
     * 两条依据：① 复制构造器只带面板属性、不带这 12 个；② {@link #clearTemporaryAttributes()}
     * 每局结束把它们清零。所以"配在注册表模板上"对任何一局战斗都不生效。
     */
    static final String ENHANCE_REASON =
            "运行时加成：由效果与技能\"进来加、走时减\"（战斗结束还会清零），且复制构造器不带"
                    + " → 配在模板上不生效，要加请用效果/技能";

    /**
     * 「基础减伤」在 {@link #damageReductions} 里的来源键。
     * <p>
     * 只有 {@link #setDamageAbsorbedPercent(double)} 用它，用来兼容旧写法（直接设置一个总减伤）。
     */
    private static final DamageReductionSource BASE_DAMAGE_REDUCTION = new DamageReductionSource("基础减伤");
    /**
     * 减伤来源（<b>乘算叠加</b>）：承伤倍率 = Π(1 − 每个减伤)，见 {@link #getDamageTakenMultiplier()}。
     */
    private final List<DamageReduction> damageReductions = new ArrayList<>();
    private final List<IModifyDamage> damageModifiers = new ArrayList<>();
    /**
     * 属性组件：五行组（5 抗性 + 5 单元素穿透 + 5 元素增伤 + 5 法力成长系数）与
     * 全局组（{@code penetration} 全属性穿透、{@code enhance} 全属性增伤、{@code criticalDMG} 暴击伤害）。
     * <p>
     * <b>{@code @DataFlatten} 是关键</b>：它让组件的字段被<b>无前缀</b>并进本对象的复合标签，
     * 所以 {@code /data get entity @s} 里看到的仍是 {@code fireResistance}、{@code penetration}、
     * {@code metalManaGrow}…… 与这些字段还写在本类里时逐个字符一致（数据名是对外 API，见 TIPS §5.10）。
     * <p>
     * 本类保留全部 public getter/setter —— 它们的方法名是 460 个调用点与模组依赖的 API，
     * 只是转发到本组件。
     */
    @DataFlatten
    private final AttributeProfile attributes = new AttributeProfile();
    /**
     * 单次额外伤害（{@code LivingThing} 这一侧的字段）。
     * <p>
     * <b>不进配置文件</b>（{@code @NoConfig}）：<b>死字段</b> —— 参与伤害公式却
     * <b>全项目零写入点</b>（永远是 0）。活着的那一套是 {@code Skill#extraDamage}，
     * 是另一个字段。允许配置它等于给用户一个"写了没反应"的键。
     */
    @NoConfig("死字段：全项目零写入点（活的那套是 Skill#extraDamage），配了不会有任何反应")
    public long extraDamage = 0;
    private IShowSpecialMes showSpecialMes;
    /**
     * 暴击率（基础值；实际暴击率见 {@link #getCriticalRate()}，还会叠加 {@code criticalRateEnhance*}）。
     * <p>
     * 对外数据名就是 {@code criticalRate}（Java 名与数据名一致，所以不需要 {@code @DataField}）。
     */
    private double criticalRate;
    private long hp, defence, speed, attack, hpMax;
    /**
     * 是否存活。<b>对外数据名是 {@code alive}</b>（与 Java 字段名一致，所以不需要 {@code @DataField}）。
     * <p>
     * <b>不进配置文件</b>（{@code @NoConfig}）：存活与否是<b>战斗结果</b>，不是模板属性 ——
     * 允许在 {@code EntityData.json} 里写它等于允许"注册表里的模板一开始就是死的"。
     * {@code /data merge} 照旧能改（调试要用）。
     */
    @NoConfig("存活与否是战斗结果，不是模板属性；写在 EntityData.json 里等于让模板一开始就是死的")
    private boolean alive = true;
    /**
     * 身上的效果列表。<b>对外数据名是 {@code effects}</b>（Java 字段名 {@code entityEffectList} 又长又绕）。
     * <p>
     * <b>不进配置文件</b>：对象列表加元素要构造合法的游戏对象（还得补 id、走合并语义），
     * 该用 {@code /effect} 或角色自己的构造函数，配置里写不了。
     */
    @NoConfig("对象列表：加元素要构造合法对象并补 id，请用 /effect 或角色自己的构造函数")
    @DataField("effects")
    private List<Effect> entityEffectList = new ArrayList<>();
    /**
     * 生命成长系数。<b>对外数据名是 {@code hpGrow}</b>（与 Java 字段名一致，所以不需要 {@code @DataField}）。
     */
    private double hpGrow;
    /**
     * 攻击成长系数。<b>对外数据名是 {@code attackGrow}</b>（顺带把自造缩写 {@code atk} 展开，
     * 与 {@code attack} 字段保持一致）。
     */
    private double attackGrow;
    /**
     * 防御成长系数。<b>对外数据名是 {@code defenceGrow}</b>（{@code dfk} 是自造缩写）。
     */
    private double defenceGrow;
    private ElementSort elementSort;
    /**
     * 12 个 {@code *Enhance*} 运行时加成：8 个百分比（本行）+ 4 个固定值（下一行）。
     * <p>
     * <b>不进配置文件</b>（{@code @NoConfig}，理由见 {@link #ENHANCE_REASON}）：
     * 它们不是模板属性 —— 效果与技能"进来加、走时减"（{@code AttackEnhance} /
     * {@code HpEnhanceEffect} / {@code CriticalRateEnhanceEffect} …，白厄变身也自己加减），
     * 战斗结束由 {@link #clearTemporaryAttributes()} 兜底清零；而且<b>复制构造器不带它们</b>
     * （副本只带面板属性），所以配在注册表模板上对任何一局战斗都不生效。要加请用效果/技能。
     */
    @NoConfig(ENHANCE_REASON)
    private double attackEnhancePercent, defenceEnhancePercent, speedEnhancePercent, hpEnhancePercent, criticalDMGEnhancePercent, criticalDMGEnhanceAmount, criticalRateEnhancePercent, criticalRateEnhanceAmount;
    /**
     * 12 个 {@code *Enhance*} 里那 4 个"固定值"（同一批，见上一行）。
     */
    @NoConfig(ENHANCE_REASON)
    private long attackEnhanceAmount, defenceEnhanceAmount, speedEnhanceAmount, hpEnhanceAmount;
    /**
     * 元素法力列表（一个实体可以拥有多个 Mana）。
     * <p>
     * <b>不进配置文件</b>：法力上限跟元素走，由 {@code initialMana()} 从元素属性算出来；
     * 单条法力该用 {@code /data modify manas[0].amount} 那类写法调，
     * 而不是让模板的整张法力表被配置覆盖（那会让"换元素后法力没跟着变"）。
     */
    @NoConfig("法力表由元素属性经 initialMana() 算出来；要调单条法力请用 /data modify manas[0].amount")
    private List<Mana> manas = new ArrayList<>();//一个实体可以拥有多个Mana
    private double defenseLoss;
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
     * （{@code /data get} 里刻意跳过它，见 {@code notes_for_llm/70-DATA.md} §5.10.1）。
     */
    @NoConfig("行为对象（带技能表与 AI 状态），/data 那边也刻意不 dump")
    private UniversalController controller;
    private String description;
    /**
     * 单次攻击的独立倍率区。
     * <p>
     * <b>面板属性</b>：{@link #LivingThing(LivingThing) 复制构造器}会带它、
     * {@link #clearTemporaryAttributes()} 不清它。
     * <p>
     * <b>不进配置文件</b>（{@code @NoConfig}）：它由<b>角色自己的构造器</b>按玩法算出来
     * （{@code ActorLiXiaoYan} 按燃点算），是<b>派生值</b>而不是可调旋钮 ——
     * 放开它等于让配置覆盖角色自己的算法（还会让默认配置文件多出一个键）。
     * <b>这一条是"判断"，不是"配了没用"</b>：与上面那 22 个临时属性不同，
     * 它真的会被副本带进战斗，所以理由必须写成"为什么不开放"。
     */
    @NoConfig("派生面板倍率：由角色构造器按玩法算出来（副本会带、战斗结束不清）——"
            + "开放它等于让配置覆盖角色自己的算法，要改请改那个角色的构造器")
    private double individualMultipleArea = 1;
    /**
     * 是否正在做伤害试算（只读预测）。见 {@link #modifyIncomingDamage}。
     * <p>
     * <b>不进 {@code /data}</b>：它只在 {@link #anticipating(Supplier)} 期间为 {@code true}，
     * 是"正在进行中的动作"而不是状态，dump 出来纯属噪音。
     */
    @NoData
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
        this.elementSort = other.elementSort;
        // 属性组件里"面板属性"的那一半（五行组 5 抗性 + 5 法力成长，全局组 penetration / enhance / criticalDMG）
        // 跟着副本走；五行组的 5 单元素穿透 / 5 元素增伤是临时属性，刻意不带过去（见 AttributeProfile#copyFrom）。
        this.attributes.copyFrom(other.attributes);
        this.description = other.description;
        this.speed = other.speed;
        this.setType(other.getType());
        this.alive = other.alive;
        this.defenseLoss = other.defenseLoss;
        this.setHpGrow(other.getHpGrow());
        this.setAttackGrow(other.getAttackGrow());
        this.setDefenceGrow(other.getDefenceGrow());
        this.hp = other.hp;
        this.defence = other.defence;
        this.attack = other.attack;
        this.hpMax = other.hpMax;
        this.setShowSpecialMes(other.getShowSpecialMes());
        this.damageModifiers.addAll(other.damageModifiers);
        this.criticalRate = other.criticalRate;
        this.entityEffectList = new ArrayList<>(other.entityEffectList);
        // 【面板属性】要复制，且**不**在 whenFightEnds() 的清零表里 ——
        // 它由角色自己的构造器按【燃点】算出来（ActorLiXiaoYan:122/224），不是效果给的临时加成。
        // 复制构造器与 clearTemporaryAttributes() 是互补的两张表，改一个记得看另一个。
        this.individualMultipleArea = other.individualMultipleArea;
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
        this.setHpGrow(hp);
        this.setAttackGrow(atk);
        this.setDefenceGrow(defence);
        this.attributes.setFireResistance(fireResistance);
        this.attributes.setWaterResistance(waterResistance);
        this.attributes.setMetalResistance(metalResistance);
        this.attributes.setWoodResistance(woodResistance);
        this.attributes.setDirtResistance(dirtResistance);
        this.setType(type);
        alive = true;
        this.speed = speed;
        this.defenseLoss = 0;
        this.hp = (long) ((l - 1) * getHpGrow() + formulaHpBase());
        this.defence = (long) ((l - 1) * getDefenceGrow() + formulaDefenceBase());
        this.attack = (long) (formulaAttackBase() + getAttackGrow() * (l - 1));
        this.hpMax = this.hp;
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
     * @return 主元素法力的基数（默认 {@value cn.gfhnv.game.system.configLoadingSystem.RuleDefaults#MANA_MAIN_BASE}）
     */
    private static long manaMainBase() {
        return cn.gfhnv.game.system.configLoadingSystem.GameRules
                .getLong(cn.gfhnv.game.system.configLoadingSystem.DataKeys.Rule.Mana.MAIN_BASE);
    }

    /**
     * @return 其余元素法力的基数（默认 {@value cn.gfhnv.game.system.configLoadingSystem.RuleDefaults#MANA_OTHER_BASE}）
     */
    private static long manaOtherBase() {
        return cn.gfhnv.game.system.configLoadingSystem.GameRules
                .getLong(cn.gfhnv.game.system.configLoadingSystem.DataKeys.Rule.Mana.OTHER_BASE);
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
    private static String describeReductionSource(Object source) {
        if (source instanceof DamageReductionSource named) {
            return named.toString();
        }
        if (source instanceof Effect effect) {
            return effect.getID() == null ? effect.getClass().getSimpleName() : effect.getID();
        }
        if (source instanceof Skill skill) {
            return skill.getName() == null ? skill.getClass().getSimpleName() : skill.getName();
        }
        if (source instanceof cn.gfhnv.game.item.Item item) {
            return item.getId() == null ? item.getClass().getSimpleName() : item.getId();
        }
        if (source instanceof CharSequence text) {
            return text.toString();
        }
        return source.getClass().getSimpleName();
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
        return attributes.getMetalPenetration();
    }

    /**
     * 设置金元素穿透。
     *
     * @param metalPenetration 金元素穿透
     */
    public void setMetalPenetration(double metalPenetration) {
        this.attributes.setMetalPenetration(metalPenetration);
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
     * <p>
     * 那两个基数可以在 {@code config/gameConfig/GameRules.json} 的 {@code mana} 段里改
     * （{@code mainBase} / {@code otherBase}）；不写就用 {@link RuleDefaults} 里的出厂值，
     * 与本方法原来的字面量逐字相等。
     */
    public void initialMana() {
        this.manas = new ArrayList<>();
        switch (this.getElementSort()) {
            case METAL -> {
                manas.add(new Mana(this.attributes.getMetalManaGrow() * (this.getLevel() - 1) + manaMainBase(), ElementSort.METAL));
                manas.add(new Mana(this.attributes.getWoodManaGrow() * (this.getLevel() - 1) + manaOtherBase(), ElementSort.WOOD));
                manas.add(new Mana(this.attributes.getWaterManaGrow() * (this.getLevel() - 1) + manaOtherBase(), ElementSort.WATER));
                manas.add(new Mana(this.attributes.getFireManaGrow() * (this.getLevel() - 1) + manaOtherBase(), ElementSort.FIRE));
                manas.add(new Mana(this.attributes.getDirtManaGrow() * (this.getLevel() - 1) + manaOtherBase(), ElementSort.DIRT));
            }
            case WOOD -> {
                manas.add(new Mana(this.attributes.getMetalManaGrow() * (this.getLevel() - 1) + manaOtherBase(), ElementSort.METAL));
                manas.add(new Mana(this.attributes.getWoodManaGrow() * (this.getLevel() - 1) + manaMainBase(), ElementSort.WOOD));
                manas.add(new Mana(this.attributes.getWaterManaGrow() * (this.getLevel() - 1) + manaOtherBase(), ElementSort.WATER));
                manas.add(new Mana(this.attributes.getFireManaGrow() * (this.getLevel() - 1) + manaOtherBase(), ElementSort.FIRE));
                manas.add(new Mana(this.attributes.getDirtManaGrow() * (this.getLevel() - 1) + manaOtherBase(), ElementSort.DIRT));
            }
            case WATER -> {
                manas.add(new Mana(this.attributes.getMetalManaGrow() * (this.getLevel() - 1) + manaOtherBase(), ElementSort.METAL));
                manas.add(new Mana(this.attributes.getWoodManaGrow() * (this.getLevel() - 1) + manaOtherBase(), ElementSort.WOOD));
                manas.add(new Mana(this.attributes.getWaterManaGrow() * (this.getLevel() - 1) + manaMainBase(), ElementSort.WATER));
                manas.add(new Mana(this.attributes.getFireManaGrow() * (this.getLevel() - 1) + manaOtherBase(), ElementSort.FIRE));
                manas.add(new Mana(this.attributes.getDirtManaGrow() * (this.getLevel() - 1) + manaOtherBase(), ElementSort.DIRT));
            }
            case FIRE -> {
                manas.add(new Mana(this.attributes.getMetalManaGrow() * (this.getLevel() - 1) + manaOtherBase(), ElementSort.METAL));
                manas.add(new Mana(this.attributes.getWoodManaGrow() * (this.getLevel() - 1) + manaOtherBase(), ElementSort.WOOD));
                manas.add(new Mana(this.attributes.getWaterManaGrow() * (this.getLevel() - 1) + manaOtherBase(), ElementSort.WATER));
                manas.add(new Mana(this.attributes.getFireManaGrow() * (this.getLevel() - 1) + manaMainBase(), ElementSort.FIRE));
                manas.add(new Mana(this.attributes.getDirtManaGrow() * (this.getLevel() - 1) + manaOtherBase(), ElementSort.DIRT));
            }
            case DIRT -> {
                manas.add(new Mana(this.attributes.getMetalManaGrow() * (this.getLevel() - 1) + manaOtherBase(), ElementSort.METAL));
                manas.add(new Mana(this.attributes.getWoodManaGrow() * (this.getLevel() - 1) + manaOtherBase(), ElementSort.WOOD));
                manas.add(new Mana(this.attributes.getWaterManaGrow() * (this.getLevel() - 1) + manaOtherBase(), ElementSort.WATER));
                manas.add(new Mana(this.attributes.getFireManaGrow() * (this.getLevel() - 1) + manaOtherBase(), ElementSort.FIRE));
                manas.add(new Mana(this.attributes.getDirtManaGrow() * (this.getLevel() - 1) + manaMainBase(), ElementSort.DIRT));
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
    public double getHpGrow() {
        return hpGrow;
    }

    /**
     * 设置生命成长系数。
     *
     * @param hpGrow 生命成长系数
     */
    public void setHpGrow(double hpGrow) {
        this.hpGrow = hpGrow;
    }

    /**
     * @return 攻击成长系数
     */
    public double getAttackGrow() {
        return attackGrow;
    }

    /**
     * 设置攻击成长系数。
     *
     * @param attackGrow 攻击成长系数
     */
    public void setAttackGrow(double attackGrow) {
        this.attackGrow = attackGrow;
    }

    /**
     * @return 防御成长系数
     */
    public double getDefenceGrow() {
        return defenceGrow;
    }

    /**
     * 设置防御成长系数。
     *
     * @param defenceGrow 防御成长系数
     */
    public void setDefenceGrow(double defenceGrow) {
        this.defenceGrow = defenceGrow;
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
     * @return 土元素穿透
     */
    public double getDirtPenetration() {
        return attributes.getDirtPenetration();
    }

    /**
     * 设置土元素穿透。
     *
     * @param dirtPenetration 土元素穿透
     */
    public void setDirtPenetration(double dirtPenetration) {
        this.attributes.setDirtPenetration(dirtPenetration);
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
        return attributes.getFirePenetration();
    }

    /**
     * 设置火元素穿透。
     *
     * @param firePenetration 火元素穿透
     */
    public void setFirePenetration(double firePenetration) {
        this.attributes.setFirePenetration(firePenetration);
    }

    /**
     * @return 水元素穿透
     */
    public double getWaterPenetration() {
        return attributes.getWaterPenetration();
    }

    /**
     * 设置水元素穿透。
     *
     * @param waterPenetration 水元素穿透
     */
    public void setWaterPenetration(double waterPenetration) {
        this.attributes.setWaterPenetration(waterPenetration);
    }

    /**
     * @return 木元素穿透
     */
    public double getWoodPenetration() {
        return attributes.getWoodPenetration();
    }

    /**
     * 设置木元素穿透。
     *
     * @param woodPenetration 木元素穿透
     */
    public void setWoodPenetration(double woodPenetration) {
        this.attributes.setWoodPenetration(woodPenetration);
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
        return attributes.getMetalDamageEnhance();
    }

    /**
     * 设置金元素伤害增强。
     *
     * @param metalDamageEnhance 金元素伤害增强
     */
    public void setMetalDamageEnhance(double metalDamageEnhance) {
        this.attributes.setMetalDamageEnhance(metalDamageEnhance);
    }

    /**
     * @return 木元素伤害增强
     */
    public double getWoodDamageEnhance() {
        return attributes.getWoodDamageEnhance();
    }

    /**
     * 设置木元素伤害增强。
     *
     * @param woodDamageEnhance 木元素伤害增强
     */
    public void setWoodDamageEnhance(double woodDamageEnhance) {
        this.attributes.setWoodDamageEnhance(woodDamageEnhance);
    }

    /**
     * @return 水元素伤害增强
     */
    public double getWaterDamageEnhance() {
        return attributes.getWaterDamageEnhance();
    }

    /**
     * 设置水元素伤害增强。
     *
     * @param waterDamageEnhance 水元素伤害增强
     */
    public void setWaterDamageEnhance(double waterDamageEnhance) {
        this.attributes.setWaterDamageEnhance(waterDamageEnhance);
    }

    /**
     * @return 火元素伤害增强
     */
    public double getFireDamageEnhance() {
        return attributes.getFireDamageEnhance();
    }

    /**
     * 设置火元素伤害增强。
     *
     * @param fireDamageEnhance 火元素伤害增强
     */
    public void setFireDamageEnhance(double fireDamageEnhance) {
        this.attributes.setFireDamageEnhance(fireDamageEnhance);
    }

    /**
     * @return 土元素伤害增强
     */
    public double getDirtDamageEnhance() {
        return attributes.getDirtDamageEnhance();
    }

    /**
     * 设置土元素伤害增强。
     *
     * @param dirtDamageEnhance 土元素伤害增强
     */
    public void setDirtDamageEnhance(double dirtDamageEnhance) {
        this.attributes.setDirtDamageEnhance(dirtDamageEnhance);
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
        this.attributes.setCriticalDMG(criticalDMG);
        return this;
    }

    /**
     * 链式设置基础暴击率。
     *
     * @param criticalRate 基础暴击率
     * @return 当前生物实例
     */
    public LivingThing facSetCriticalRate(double criticalRate) {
        this.criticalRate = criticalRate;
        return this;
    }

    /**
     * 链式设置存活状态。
     *
     * @param alive 是否存活
     * @return 当前生物实例
     */
    public LivingThing facSetAlive(boolean alive) {
        this.alive = alive;
        return this;
    }

    /**
     * 链式设置全属性穿透。
     *
     * @param chuantong 全属性穿透
     * @return 当前生物实例
     */
    public LivingThing facSetChuantong(double chuantong) {
        this.attributes.setPenetration(chuantong);
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
        this.attributes.setEnhance(enhance);
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
        return criticalRate * (1 + criticalRateEnhancePercent) + criticalRateEnhanceAmount;
    }

    /**
     * 设置基础暴击率。
     *
     * @param criticalRate 基础暴击率
     */
    public void setCriticalRate(double criticalRate) {
        this.criticalRate = criticalRate;
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
     * 名字后面跟上阵营，例如 {@code 张三（我方）}、{@code 火劫卫（敌方）}。
     * <p>
     * 战斗日志里同名生物经常分属两边（典型：双方各有一只【残破容器】），
     * 光看名字分不出谁是谁，所以攻击行这类输出统一带上阵营 ——
     * 判据借 {@link Fight#sideNameOf(LivingThing)}，与回合头同一套。
     * 阵营用灰色，免得抢了伤害数字和技能名的注意力。
     * <p>
     * 不在战斗里（选人界面之类，{@link #getParticipateFight()} 为 {@code null}）时只返回名字。
     *
     * @return 带阵营的名字
     */
    public String getNameWithSide() {
        String side = sideName();
        return side.isEmpty() ? getName() : getName() + "（" + ConsoleColor.dim(side) + "）";
    }

    /**
     * 名字后面跟上短标识，例如 {@code 至黑之剑，盗火行者#0a1b2c}。
     * <p>
     * 格式与回合头（{@code FightTurnPastListener}）逐字一致 —— 短标识本身来自
     * {@link Thing#getShortUuid()}，回合头也调它，所以两处永远是同一个值
     * （同名同阵营的镜像对局里，只有这一串能分出"这条日志是哪一个实例"）。
     * <p>
     * 与 {@link #getNameWithSide()} 是<b>两个维度</b>：阵营说"自己人还是对面"，
     * 短标识说"是哪一只"。攻击行这类点名双方、还可能出现同名实例的输出两个都要，
     * 写法是 {@code 名字#短标识（阵营）}，见 {@link #getNameWithUuidAndSide()}。
     * <p>
     * <b>本方法不着色</b>：它给"整行包一个颜色"的地方用（回合头把整行包在青色里），
     * 自带颜色的话内层的复位会把整行青色掐断。按段着色的战斗日志行请用
     * {@link #getNameWithUuidAndSide()}（那里的短标识自己带颜色）。
     *
     * @return 带短标识的名字
     */
    public String getNameWithUuid() {
        return getName() + "#" + getShortUuid();
    }

    /**
     * 名字后面跟上短标识与阵营，例如 {@code 至黑之剑，盗火行者#0a1b2c（我方）}。
     * <p>
     * 回合头的格式（{@code ─── 现在是 名字#短标识（我方）的回合 ───}），攻击行这类
     * <b>按段着色</b>的行复用它。短标识在前、阵营在后：先认出"是哪一只"，再看它站哪边。
     * <p>
     * 颜色照 {@code 60-COMBAT.md} 的 §5.5.2：<b>短标识青</b>（用户 2026-10-03 要求
     * "和回合头一样"，回合头整行是青的，所以短标识取同一个青）、
     * <b>阵营灰</b>（免得抢伤害数字与技能名的注意力）、名字本身不上色
     * （与其它日志行一致，别在这里另起一套）。
     *
     * @return 带短标识与阵营的名字
     */
    public String getNameWithUuidAndSide() {
        String side = sideName();
        return getName() + shortUuidTag() + (side.isEmpty() ? "" : "（" + ConsoleColor.dim(side) + "）");
    }

    /**
     * @return {@code #短标识}，按回合头的颜色上色（青）；关闭着色时原样返回
     */
    private String shortUuidTag() {
        return ConsoleColor.cyan("#" + getShortUuid());
    }

    /**
     * @return 这一侧的显示名（{@code 我方} / {@code 敌方}）；不在战斗里时返回空串
     */
    private String sideName() {
        Fight fight = getParticipateFight();
        return fight == null ? "" : fight.sideNameOf(this);
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
            alive = false;
        }
        if (getHp() > 0) {
            alive = true;
        }
        return alive;
    }

    /**
     * 直接设置存活状态。
     *
     * @param b {@code true} 存活，{@code false} 死亡
     */
    public void setAlive(boolean b) {
        this.alive = b;
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
     * 把【临时属性】清零。整场结束时由 {@link #whenFightEnds()} 调用。
     * <p>
     * <b>这张表和复制构造器是互补的</b>：复制构造器<b>只带面板属性</b>
     * （{@code individualMultipleArea} 与 {@link AttributeProfile#copyFrom} 带的那一批），
     * 剩下的一次性加成全部在这里清零。
     * 改一个记得看另一个 —— 这正是 {@code ENTITY-ATTRIBUTE-SPLIT-2026-10.md}
     * 第二节说的"把生命周期规则从靠记性变成靠结构"。
     * <p>
     * 这些字段按<b>谁在写</b>分三类（2026-10-03 逐个查过写入点）：
     * <ol>
     *     <li><b>效果写的</b>（12 个 {@code *Enhance*}）：{@code AttackEnhance}、
     *     {@code DefenseEnhanceEffect}、{@code SpeedEnhanceEffect}、{@code HpEnhanceEffect}、
     *     {@code CriticalDMGEnhanceEffect}、{@code CriticalRateEnhanceEffect} ——
     *     每个都是"进来加、走时减"的成对写法；白厄变身（{@code UltimateAttack} /
     *     {@code AwakeEndListener}）也自己加减这几笔。
     *     正常情况效果离场就已经减回去了，这里清一遍是<b>兜底</b>：
     *     效果没走完 {@code whenLastTimeEnd}、或战斗在变身中途结束时，残留会渗进下一局。</li>
     *     <li><b>预留未接线</b>（10 个五元素穿透 / 五元素增伤）：全项目<b>一个写入点都没有</b>，
     *     只被 {@code DamageCalculate} 读来加算。清零对现状是 no-op，
     *     写在这里是为了"将来接线时自动落进临时属性这一侧"。</li>
     *     <li><b>死字段</b>（{@code extraDamage}）：{@code LivingThing} 这一侧的
     *     {@code extraDamage} 参与伤害公式却<b>从无写入点</b>（永远是 0）；
     *     活的那套是 {@code Skill#extraDamage}，是另一个字段。同样先清零占位。</li>
     * </ol>
     * <p>
     * <b>故意不在表里的</b>：{@code individualMultipleArea}（面板·派生，由角色构造器算，
     * 清了会永久削弱该角色）、属性组件里的面板属性（5 抗性 / 5 法力成长 /
     * {@code penetration} / {@code enhance} / {@code criticalDMG} —— 它们跟着副本走）与
     * {@code anticipating}（只读试算标志，每回合自己复位）。
     * 减伤（{@code damageReductions}）也不在这张表里 —— 它不是标量属性，
     * 由 {@link #whenFightEnds()} 在效果收尾之后单独 {@link #clearDamageReductions()}。
     */
    private void clearTemporaryAttributes() {
        // ① 效果写的临时加成（成对加减）
        this.attackEnhancePercent = 0;
        this.attackEnhanceAmount = 0;
        this.defenceEnhancePercent = 0;
        this.defenceEnhanceAmount = 0;
        this.speedEnhancePercent = 0;
        this.speedEnhanceAmount = 0;
        this.hpEnhancePercent = 0;
        this.hpEnhanceAmount = 0;
        this.criticalDMGEnhancePercent = 0;
        this.criticalDMGEnhanceAmount = 0;
        this.criticalRateEnhancePercent = 0;
        this.criticalRateEnhanceAmount = 0;
        // ② 五元素穿透 / 五元素增伤（组件里的临时属性；抗性与法力成长不在这张表里，它们跟着副本走）
        this.attributes.resetTemporary();
        // ③ 死字段（活的那套在 Skill#extraDamage 上）
        this.extraDamage = 0;
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
        clearTemporaryAttributes();
        // 顺序要紧：**先**让每个效果自己收尾（它们在自己的 whenLastTimeEnd 里摘掉挂上去的减伤），
        // **再**统一打扫剩下的。反过来的话，效果收尾时看到的是已经被清空的列表。
        for (Effect effect : getEntityEffectList()) effect.whenLastTimeEnd(this);
        this.setEntityEffectList(new ArrayList<>());
        // 效果走完之后再扫一遍减伤：技能/机制挂上来的那些**不走效果生命周期**，
        // 战斗结束时没人摘，留着会渗进下一局 —— 最典型的是白厄【灾厄】那 75%
        // （`CalamitySoulscorchEdict` 挂、`Counterattack` 摘），变身打到一半战斗结束就带走了。
        // 清空是安全的：生产环境里**没有"构造期就永久挂着"的减伤**
        // （`BASE_DAMAGE_REDUCTION` 只有旧写法 `setDamageAbsorbedPercent` 会加，而它没人调），
        // 层数减伤与二阶段免伤由 FlameReaver 在自己的 whenFightStart 重新挂，醉意由效果自己摘。
        this.clearDamageReductions();
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
        DamageEvent damageEvent = new DamageEvent(this, attacked, skill);
        EventBus.post(damageEvent);
        long hpBefore = attacked.getHp();
        attacked.getDamage(damageEvent);
        // 打"实际掉血"而不是算出来的值：伤害修正器可能把这一击拦下（免死锁 1 血之类），
        // 显示实际值才和后面的 HP 对得上（侵蚀那边同理）。
        printAttackLine(attacked, skill, Math.max(0, hpBefore - attacked.getHp()));
    }

    /**
     * 打印一行攻击日志：{@code A#短标识（我方）攻击了B#短标识（敌方）  -伤害  → HP 当前/上限  【技能名】}。
     * <p>
     * 伤害标红、剩余 HP 标灰、技能名标青、<b>短标识标青</b>（与回合头同色）、阵营标灰
     * （着色能否生效见 {@link cn.gfhnv.game.utils.ConsoleColor}；不支持 ANSI 的控制台会自动
     * 退化成纯文本）。
     * <p>
     * 攻守双方<b>都带阵营与短标识</b>，与回合头同一套格式（见 {@link #getNameWithUuidAndSide()}）：
     * 同名生物不只分属两边，镜像对局里两边还可能<b>同名同阵营</b>（各有一只【残破容器】、
     * 两边都是"至黑之剑，盗火行者"），光有阵营仍然分不出谁打谁。
     *
     * @param attacked 被打的目标
     * @param skill    使用的技能；可为 {@code null}
     * @param damage   实际掉血量
     */
    private void printAttackLine(LivingThing attacked, Skill skill, long damage) {
        StringBuilder line = new StringBuilder(getNameWithUuidAndSide()).append("攻击了")
                .append(attacked.getNameWithUuidAndSide());
        line.append("  ").append(ConsoleColor.red("-" + damage));
        line.append("  ").append(ConsoleColor.dim(
                "→ HP " + attacked.getHp() + "/" + attacked.getHpMax()));
        if (skill != null && skill.getName() != null) {
            line.append("  ").append(ConsoleColor.cyan("【" + skill.getName() + "】"));
        }
        System.out.println(line);
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
        return attributes.getCriticalDMG() * (1 + criticalDMGEnhancePercent) + criticalDMGEnhanceAmount;
    }

    /**
     * 设置基础暴击伤害倍率加成。
     *
     * @param criticalDMG 基础暴击伤害倍率加成
     */
    public void setCriticalDMG(double criticalDMG) {
        this.attributes.setCriticalDMG(criticalDMG);
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
