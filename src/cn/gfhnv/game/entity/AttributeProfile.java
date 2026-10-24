package cn.gfhnv.game.entity;

import cn.gfhnv.game.data.NoConfig;
import cn.gfhnv.game.data.NoData;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.HpLossEvent;
import cn.gfhnv.game.event.HpRestorationEvent;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.mana.Mana;

import java.util.ArrayList;
import java.util.List;

/**
 * 生物的「属性组件」：五行组 + 全局组。
 * <p>
 * <b>为什么单独一个类</b>：这些字段在 {@link LivingThing} 里原本是一堆散落的机械重复
 * （五行那一族是 5 个元素 × 4 种属性），收进来以后 {@code copyFrom} / {@code resetTemporary}
 * 各自只有一处，不会再加一个元素就漏抄一半。
 * <p>
 * <b>两类属性，两张互补的表</b>（规则见 {@code ENTITY-ATTRIBUTE-SPLIT-2026-10.md} 第二节）：
 * <ul>
 *     <li><b>面板属性</b> —— 配置 / 构造出来、跟着副本走：面板组的 6 个基础值
 *     （{@code hp} / {@code baseHpMax} / {@code baseAttack} / {@code baseDefence} / {@code baseSpeed} /
 *     {@code baseCriticalRate}）
 *     与 3 个成长系数 + {@code element}、法力表 {@code manas}，五行组的 5 抗性 + 5 法力成长，
 *     全局组的 {@code penetration} / {@code enhance} / {@code criticalDamage}。
 *     {@link #copyFrom} 要把它们带过去，{@link #resetTemporary} <b>不动</b>它们；</li>
 *     <li><b>临时属性</b> —— 效果/技能给的一次性加成：五行组的 5 单元素穿透 + 5 元素增伤，
 *     以及 12 个 {@code *Enhance*} 运行时加成。不复制，整场结束时由 {@link #resetTemporary} 清零。</li>
 * </ul>
 * <p>
 * <b>字段名就是 {@code /data} 的键名</b>（数据名是对外 API，见 TIPS §5.10）——
 * 所以这里用<b>显式命名</b>的字段，不用 {@code EnumMap}：用表的话字段名就没了，键名也就跟着没了。
 * {@link LivingThing} 用 {@code @DataFlatten} 把这些字段<b>无前缀</b>并进自己的复合标签，
 * 所以 {@code /data get entity @s} 里看到的仍是 {@code fireResistance}、{@code penetration}、
 * {@code metalManaGrow}…… 与它们还写在 {@code LivingThing} 里时逐个字符一致。
 * <p>
 * <b>访问入口只有 {@link LivingThing} 上的转发 getter/setter</b>（那 460 个调用点靠它们），
 * 本类的方法只给 {@code LivingThing} 自己用。
 *
 * @author AI（DeepSeek）生成
 */
public class AttributeProfile {

    /**
     * 那 10 个临时属性（5 单元素穿透 + 5 元素增伤）共同的 {@code @NoConfig} 理由。
     * <p>
     * <b>为什么 10 个字段共用一句话</b>：它们的生命周期完全一样（不复制、清零、零写入点），
     * 逐字抄 10 遍只会让"改一个忘九个"变得更容易。真正的依据写在
     * {@link #metalPenetration} 的 javadoc 与 {@code copyFrom} / {@code resetTemporary} 上。
     * <p>
     * <b>这句话必须是真的</b>（{@code @NoConfig} 的纪律：写不出真理由就不该挡）——
     * 三条依据：① {@link #copyFrom(AttributeProfile)} 刻意不带这 10 个；
     * ② {@link #resetTemporary()} 每局结束清零；③ 全项目零写入点（{@code DamageCalculate} 只读）。
     * 于是"配在注册表模板上"对任何一局战斗都不生效。
     */
    static final String TEMPORARY_REASON =
            "临时属性：copyFrom 刻意不带（副本从干净状态开始）、每局结束 resetTemporary 清零，"
                    + "且全项目零写入点（预留未接线）→ 配在模板上不生效，要加请用效果/技能";
    /**
     * 那 12 个 {@code *Enhance*} 运行时加成共同的 {@code @NoConfig} 理由。
     * <p>
     * <b>为什么 12 个字段共用一句话</b>：它们的生命周期完全一样（不复制、清零），
     * 逐字抄 12 遍只会让"改一个忘十一个"更容易。真正的依据写在
     * {@link #copyFrom(AttributeProfile)} 与 {@link #resetTemporary()} 上。
     * <p>
     * <b>这句话必须是真的</b>（{@code @NoConfig} 的纪律：写不出真理由就不该挡）——
     * 两条依据：① {@link #copyFrom(AttributeProfile)} 只带面板属性、不带这 12 个；
     * ② {@link #resetTemporary()} 每局结束把它们清零。所以"配在注册表模板上"
     * 对任何一局战斗都不生效。
     */
    static final String ENHANCE_REASON =
            "运行时加成：由效果与技能\"进来加、走时减\"（战斗结束还会清零），且 copyFrom 不带"
                    + " → 配在模板上不生效，要加请用效果/技能";
    /**
     * 宿主生物。<b>只为一件事而存在</b>：{@link #setHpEnhancePercent(double)} /
     * {@link #setHpEnhanceAmount(long)} 把生命上限调小之后，当前生命可能超出新上限，
     * 而"夹回新上限"这个动作只有实体做得到（{@link LivingThing#renewHp()}）。
     * <p>
     * <b>为什么要留在组件里</b>：{@code /data merge entity @s {hpEnhancePercent:…}} 的写回
     * 落在<b>字段所在的对象</b>上（就是本组件），如果这里没有那个副作用，
     * "游戏内效果改上限"与"{@code /data} 直接改上限"就会得到两种结果 ——
     * 而那正是这次拆分最不该改坏的东西（写回语义是对外契约）。
     * <p>
     * 构造时注入、之后不变；{@code @NoData} 让它<b>既不进 {@code /data} 也不进配置面</b>
     * （它不是数据，是一条反向引用）。
     */
    @NoData
    private final LivingThing host;
    /**
     * 单体伤害倍率的<b>多来源乘数</b>（和 {@code LivingThing} 的 {@code damageReductions} 同一套形状）。
     * <p>
     * 生效值 = {@link #individualMultipleArea 面板基础值} × **Π(每个来源的乘数)**，
     * 见 {@link #getIndividualMultipleArea()}。
     * <p>
     * <b>为什么要它</b>：以前"多个来源想一起改这个乘区"只能各自
     * {@code setIndividualMultipleArea(基础 × 自己那份)} —— 后来者会把先来者整个盖掉。
     * 这和减伤当年"手写 {@code set(getDamageAbsorbedPercent() + 0.75)}"是同一个坑，
     * 所以这边照减伤的修法来：各来源用 {@link #addIndividualMultipleArea(Object, double)}
     * 挂一份，**按来源身份**增删，互不覆盖。
     * <p>
     * <b>它是临时的</b>：不进 {@code /data}（对外数据键只有聚合后的
     * {@code individualMultipleArea}），战斗结束时清空 —— 见 {@code LivingThing#whenFightEnds}。
     * 面板基础值本身是<b>面板属性</b>，会被 {@code copy()} 带走、也不清零。
     * <p>
     * <b>为什么是 final</b>：只有 {@code add/remove/clear} 会动它，从不整表替换
     * （与 {@link #manas} 相反）。
     */
    @NoData
    private final List<AreaMultiple> individualMultipleAreaModifiers = new ArrayList<>();

    /* ------------------------------------------------------------------
     * ⓪ 面板组 · 基础值（面板属性：copyFrom 带过去，resetTemporary 不动）
     *
     * ⚠️ 本类的 getXxx() 一律是【基础值】；**最终值（乘上那 12 个 *Enhance*）在 LivingThing 上** ——
     * getBaseHpMax / getBaseAttack / getBaseDefence / getBaseSpeed / getBaseCriticalRate。
     * 两个类各有一个同名的 getter 是刻意的：`/data` 的键名必须与 Java 字段名一致
     * （数据名是对外 API），而"合并"这一步只有实体做得对。
     * ------------------------------------------------------------------ */
    /**
     * 当前生命值（对外数据名 {@code hp}）。
     * <p>
     * 写它要走 {@link #setHp(long)}：那里才有夹取（0 ≤ hp ≤ 最终上限）与
     * {@code HpLossEvent} / {@code HpRestorationEvent}。
     */
    private long hp;
    /**
     * 基础生命上限（对外数据名 {@code baseHpMax}；最终上限见 {@link LivingThing#getHpMax()}）。
     */
    private long baseHpMax;
    /**
     * 基础攻击力（对外数据名 {@code baseAttack}；最终值见 {@link LivingThing#getAttack()}）。
     */
    private long baseAttack;
    /**
     * 基础防御力（对外数据名 {@code baseDefence}；最终值见 {@link LivingThing#getDefence()}）。
     */
    private long baseDefence;
    /**
     * 基础速度（对外数据名 {@code baseSpeed}；最终值见 {@link LivingThing#getSpeed()}）。
     */
    private long baseSpeed;
    /**
     * 基础暴击率（对外数据名 {@code baseCriticalRate}；最终值见 {@link LivingThing#getCriticalRate()}）。
     */
    private double baseCriticalRate;
    /**
     * 生命成长系数（对外数据名 {@code hpGrow}）。
     */
    private double hpGrow;
    /**
     * 攻击成长系数（对外数据名 {@code attackGrow}）。
     */
    private double attackGrow;
    /**
     * 防御成长系数（对外数据名 {@code defenceGrow}）。
     */
    private double defenceGrow;
    /**
     * 元素属性（金木水火土）。对外数据名 {@code element}
     * （2026-10-24 之前叫 {@code elementSort}；那个名字当时还兼着"配置别名"的角色，现在没有别名了）。
     * <p>
     * <b>面板属性</b>：{@link #copyFrom} 带过去、{@link #resetTemporary} 不动 ——
     * "这个角色是什么元素"是身份的一部分，不是一局内的临时状态。
     */
    private ElementSort element;
    /**
     * 防御削减百分比（对外数据名 {@code defenceLoss}）。
     * <p>
     * <b>面板属性</b>：{@link #copyFrom} 带过去、{@link #resetTemporary} 不动；
     * 伤害计算时由 {@code DamageCalculate} 读它把防御打折。
     */
    private double defenceLoss;

    /* ------------------------------------------------------------------
     * ① 五行组 · 五行抗性（面板属性：复制，不清零）
     * ------------------------------------------------------------------ */

    /**
     * 金抗性。
     */
    private double metalResistance;
    /**
     * 木抗性。
     */
    private double woodResistance;
    /**
     * 水抗性。
     */
    private double waterResistance;
    /**
     * 火抗性。
     */
    private double fireResistance;
    /**
     * 土抗性。
     */
    private double dirtResistance;

    /* ------------------------------------------------------------------
     * ② 五行组 · 五元素穿透（临时属性：不复制，清零）
     *
     * 这 5 个 + ③ 那 5 个一共 10 个，是 2026-10-03「反射混血」第 2 步逐个判断过的那一批：
     * 结论是**都不能配**，所以每个字段都写了 @NoConfig（理由逐字相同，见下面那一行）。
     * ------------------------------------------------------------------ */

    /**
     * 金元素穿透。
     * <p>
     * <b>不进配置文件</b>（{@code @NoConfig}）：临时属性，而且<b>配了不生效</b> ——
     * {@link #copyFrom(AttributeProfile)} 刻意不带它（副本从干净状态开始）、
     * {@link #resetTemporary()} 每局结束清零，而全项目<b>零写入点</b>（预留未接线，
     * 只被伤害计算读来加算）。要加穿透请用效果/技能。
     */
    @NoConfig(TEMPORARY_REASON)
    private double metalPenetration;
    /**
     * 木元素穿透。
     */
    @NoConfig(TEMPORARY_REASON)
    private double woodPenetration;
    /**
     * 水元素穿透。
     */
    @NoConfig(TEMPORARY_REASON)
    private double waterPenetration;
    /**
     * 火元素穿透。
     */
    @NoConfig(TEMPORARY_REASON)
    private double firePenetration;
    /**
     * 土元素穿透。
     */
    @NoConfig(TEMPORARY_REASON)
    private double dirtPenetration;

    /* ------------------------------------------------------------------
     * ③ 五行组 · 五元素增伤（临时属性：不复制，清零）
     * ------------------------------------------------------------------ */

    /**
     * 金元素伤害增强。
     */
    @NoConfig(TEMPORARY_REASON)
    private double metalDamageEnhance;
    /**
     * 木元素伤害增强。
     */
    @NoConfig(TEMPORARY_REASON)
    private double woodDamageEnhance;
    /**
     * 水元素伤害增强。
     */
    @NoConfig(TEMPORARY_REASON)
    private double waterDamageEnhance;
    /**
     * 火元素伤害增强。
     */
    @NoConfig(TEMPORARY_REASON)
    private double fireDamageEnhance;
    /**
     * 土元素伤害增强。
     */
    @NoConfig(TEMPORARY_REASON)
    private double dirtDamageEnhance;

    /* ------------------------------------------------------------------
     * ④ 五行组 · 五行法力成长系数（面板属性：复制，不清零）
     * ------------------------------------------------------------------ */

    /**
     * 金法力成长系数。<b>对外数据名是 {@code metalManaGrow}</b>（下面四个同理）——
     * Java 名与数据名一致，所以这五个都不需要改名注解（{@code @DataField}）。
     */
    private double metalManaGrow;
    /**
     * 木法力成长系数。
     */
    private double woodManaGrow;
    /**
     * 水法力成长系数。
     */
    private double waterManaGrow;
    /**
     * 火法力成长系数。
     */
    private double fireManaGrow;
    /**
     * 土法力成长系数。
     */
    private double dirtManaGrow;

    /* ------------------------------------------------------------------
     * ⑤ 全局组（面板属性：复制，不清零）
     * ------------------------------------------------------------------ */

    /**
     * 全属性穿透：伤害计算时与「单元素穿透」<b>相加</b>（见 {@code DamageCalculate}）。
     * <p>
     * 它和 5 个单元素穿透不是一个生命周期：这个是面板属性，那 5 个是临时属性。
     */
    private double penetration;
    /**
     * 全属性增伤百分比。
     */
    private double enhance;
    /**
     * 基础暴击伤害倍率（对外数据名 {@code baseCriticalDamage}；最终值见
     * {@link LivingThing#getCriticalDMG()}，还会叠加 {@code criticalDamageEnhancePercent} /
     * {@code criticalDamageEnhanceAmount}）。
     */
    private double baseCriticalDamage;

    /* ------------------------------------------------------------------
     * ⑥ *Enhance* 运行时加成（临时属性：不复制，清零）
     *
     * 谁在写：六个通用强化效果（AttackEnhance / DefenseEnhanceEffect / SpeedEnhanceEffect /
     * HpEnhanceEffect / CriticalDMGEnhanceEffect / CriticalRateEnhanceEffect）成对加减，
     * 白厄变身（UltimateAttack / AwakeEndListener）也自己加减这几笔。
     * 12 个字段共用一句 @NoConfig 理由，见上面 ENHANCE_REASON。
     * ------------------------------------------------------------------ */

    /**
     * 攻击增强百分比。
     */
    @NoConfig(ENHANCE_REASON)
    private double attackEnhancePercent;
    /**
     * 防御增强百分比。
     */
    @NoConfig(ENHANCE_REASON)
    private double defenceEnhancePercent;
    /**
     * 速度增强百分比。
     */
    @NoConfig(ENHANCE_REASON)
    private double speedEnhancePercent;
    /**
     * 生命增强百分比。
     */
    @NoConfig(ENHANCE_REASON)
    private double hpEnhancePercent;
    /**
     * 暴击伤害增强百分比。
     */
    @NoConfig(ENHANCE_REASON)
    private double criticalDamageEnhancePercent;
    /**
     * 暴击伤害增强固定值。
     */
    @NoConfig(ENHANCE_REASON)
    private double criticalDamageEnhanceAmount;
    /**
     * 暴击率增强百分比。
     */
    @NoConfig(ENHANCE_REASON)
    private double criticalRateEnhancePercent;
    /**
     * 暴击率增强固定值。
     */
    @NoConfig(ENHANCE_REASON)
    private double criticalRateEnhanceAmount;
    /**
     * 攻击增强固定值。
     */
    @NoConfig(ENHANCE_REASON)
    private long attackEnhanceAmount;
    /**
     * 防御增强固定值。
     */
    @NoConfig(ENHANCE_REASON)
    private long defenceEnhanceAmount;
    /**
     * 速度增强固定值。
     */
    @NoConfig(ENHANCE_REASON)
    private long speedEnhanceAmount;
    /**
     * 生命增强固定值。
     */
    @NoConfig(ENHANCE_REASON)
    private long hpEnhanceAmount;

    /* ------------------------------------------------------------------
     * ⑧ 容器 · 法力表（面板属性：copyFrom 深拷贝，resetTemporary 不动）
     * ------------------------------------------------------------------ */

    /**
     * 元素法力列表（一个实体可以拥有多个 {@link Mana}）。对外数据名 {@code manas}。
     * <p>
     * <b>不进配置文件</b>（{@code @NoConfig}）：法力上限跟着元素属性走，由
     * {@link LivingThing#initialMana()} 从元素属性算出来；单条法力该用
     * {@code /data modify manas[0].amount} 那类写法调，而不是让模板的整张法力表被配置覆盖
     * （那会让"换元素后法力没跟着变"）。
     * <p>
     * <b>面板属性</b>：{@link #copyFrom} 会<b>深拷贝</b>它（每个 {@link Mana} 都是新对象，
     * 与它还写在实体上时复制构造器的行为一致）、{@link #resetTemporary} 不动它。
     * <p>
     * <b>为什么不是 final</b>：{@code initialMana()} 与 {@code setManas(...)} 都是<b>整表替换</b>。
     */
    @NoConfig("法力表由元素属性经 initialMana() 算出来；要调单条法力请用 /data modify manas[0].amount")
    private List<Mana> manas = new ArrayList<>();

    /* ------------------------------------------------------------------
     * ⑨ 派生面板倍率（面板属性：copyFrom 带过去，resetTemporary 不动）
     * ------------------------------------------------------------------ */

    /**
     * 单次攻击的独立倍率区<b>面板基础值</b>（对外数据名 {@code individualMultipleArea}）。
     * <p>
     * <b>生效值</b> = 这个基础值 × **Π(每个来源的乘数)**（{@link #getIndividualMultipleArea()}）——
     * 那么多来源的乘数挂在 {@link #individualMultipleAreaModifiers} 上，
     * 面板基础值本身由角色构造器按玩法算出来（{@code ActorLiXiaoYan} 按【燃点】算）。
     * <p>
     * <b>面板属性</b>：{@link #copyFrom} 带过去、{@link #resetTemporary} 不动。
     * <p>
     * <b>不进配置文件</b>（{@code @NoConfig}）：它是<b>派生值</b>而不是可调旋钮 ——
     * 放开它等于让配置覆盖角色自己的算法（还会让默认配置文件多出一个键）。
     * <b>这一条是"判断"，不是"配了没用"</b>：它真的会被副本带进战斗，所以理由必须写成"为什么不开放"。
     */
    @NoConfig("派生面板倍率：由角色构造器按玩法算出来（副本会带、战斗结束不清）——"
            + "开放它等于让配置覆盖角色自己的算法，要改请改那个角色的构造器")
    private double individualMultipleArea = 1;
    /**
     * 单次额外伤害（对外数据名 {@code extraDamage}）。
     * <p>
     * <b>死字段</b>：参与伤害公式（{@code DamageCalculate} 读它）却<b>全项目零写入点</b>，
     * 永远是 0；活着的那一套是 {@code Skill#extraDamage}，是<b>另一个</b>字段。
     * 所以别删（公式还在读），也别指望配置它 —— 配了不会有任何反应。
     * <p>
     * <b>临时属性</b>：{@link #copyFrom} 不带、{@link #resetTemporary} 清零 ——
     * 与它还写在实体上时（当时由 {@code LivingThing#clearTemporaryAttributes()} 清零）逐位一致。
     * <p>
     * <b>不进配置文件</b>（{@code @NoConfig}）：允许配置它等于给用户一个"写了没反应"的键。
     */
    @NoConfig("死字段：全项目零写入点（活的那套是 Skill#extraDamage），配了不会有任何反应")
    private long extraDamage = 0;
    /**
     * 是否存活。<b>临时状态</b>（用户 2026-10-04 确认）：{@code copy()} <b>不复制</b>、
     * 战斗结束清零回 {@code true}。
     * <p>
     * 对外数据名仍是 {@code alive}（{@code @DataFlatten} 把组件字段无前缀并进实体复合标签），
     * 与它还写在 {@code LivingThing} 里时逐字一致。
     * <p>
     * ⚠️ 行为变化一处：以前 {@code LivingThing} 的复制构造器会 <b>复制</b> {@code alive}，
     * 现在按"状态不复制"的规则，副本一律从<b>存活</b>开始。
     * {@code copy()} 只用于模板与选人（都是活着的），实际影响为零。
     */
    @NoConfig("存活与否是战斗结果，不是模板属性；写在 EntityData.json 里等于让模板一开始就是死的")
    private boolean alive = true;

    /* ------------------------------------------------------------------
     * ⑩ 死字段（临时属性：不复制，清零）
     * ------------------------------------------------------------------ */
    /**
     * 是否正在做"预判 / 伤害试算"（{@link LivingThing#anticipating(java.util.function.Supplier)} 期间为真）。
     * <p>
     * <b>临时状态</b>：{@code copy()} 不复制、结束清零；并且<b>不进 {@code /data}</b>
     * （它是"进行中的动作"，不是属性，dump 出来是噪音）。
     */
    @NoData
    private boolean anticipating = false;

    /* ------------------------------------------------------------------
     * ⑦ 状态（临时：不复制，结束清零）
     * ------------------------------------------------------------------ */

    /**
     * @param host 宿主生物（{@link LivingThing} 在字段初始化时注入自己）
     */
    public AttributeProfile(LivingThing host) {
        this.host = host;
    }

    /**
     * 从另一个组件复制<b>面板属性</b>：
     * 面板组的基础值（{@code hp} / {@code baseHpMax} / {@code baseAttack} / {@code baseDefence} /
     * {@code baseSpeed} / {@code baseCriticalRate} 与三个成长系数）、五行组的 5 抗性 + 5 法力成长，
     * 以及全局组的 {@code penetration} / {@code enhance} / {@code criticalDamage}。
     * <p>
     * <b>刻意不带临时属性</b>（5 单元素穿透、5 元素增伤与 12 个 {@code *Enhance*}）：
     * 那是效果/技能给的一次性加成，副本必须从干净的状态开始（与 {@link #resetTemporary} 是互补的两张表，
     * 改一个记得看另一个）。
     * <p>
     * {@code hp} 也照抄原值 —— 与它还在实体上时一致（复制构造器直接赋值，不走 {@link #setHp(long)} 的夹取）。
     *
     * @param other 被复制的组件
     */
    public void copyFrom(AttributeProfile other) {
        // 面板组：基础值
        this.hp = other.hp;
        this.baseHpMax = other.baseHpMax;
        this.baseAttack = other.baseAttack;
        this.baseDefence = other.baseDefence;
        this.baseSpeed = other.baseSpeed;
        this.baseCriticalRate = other.baseCriticalRate;
        this.hpGrow = other.hpGrow;
        this.attackGrow = other.attackGrow;
        this.defenceGrow = other.defenceGrow;
        this.element = other.element;
        this.defenceLoss = other.defenceLoss;
        // 派生面板倍率：基础值照抄，来源乘数也整表拷过去（元素是不可变 record，浅拷列表即可）
        this.individualMultipleArea = other.individualMultipleArea;
        this.individualMultipleAreaModifiers.clear();
        this.individualMultipleAreaModifiers.addAll(other.individualMultipleAreaModifiers);
        // 法力表：**深拷贝**（每个 Mana 都是新对象 —— 与它还写在实体上时复制构造器的行为一致；
        // 浅拷贝会让模板与副本共享同一批 Mana，一开打两边的蓝条就联动）
        this.manas.clear();
        for (Mana mana : other.manas) {
            this.manas.add(new Mana(mana));
        }
        // 五行组：抗性 + 法力成长
        this.metalResistance = other.metalResistance;
        this.woodResistance = other.woodResistance;
        this.waterResistance = other.waterResistance;
        this.fireResistance = other.fireResistance;
        this.dirtResistance = other.dirtResistance;
        this.metalManaGrow = other.metalManaGrow;
        this.woodManaGrow = other.woodManaGrow;
        this.waterManaGrow = other.waterManaGrow;
        this.fireManaGrow = other.fireManaGrow;
        this.dirtManaGrow = other.dirtManaGrow;
        // 全局组
        this.penetration = other.penetration;
        this.enhance = other.enhance;
        this.baseCriticalDamage = other.baseCriticalDamage;
    }

    /**
     * 铺一批<b>基础值</b>：{@code hp}、{@code baseHpMax}（= hp）、{@code defence}、{@code attack}。
     * <p>
     * 只给 {@link LivingThing} 的十三参构造器用：构造期必须<b>直接赋值</b>，不能走 {@link #setHp(long)}
     * —— 那个会发 {@code HpRestorationEvent}（刚造出来的模板不该通知任何监听器）、
     * 还会拿"还没铺好的"上限去夹。
     *
     * @param hp      生命值（同时也是基础生命上限）
     * @param defence 基础防御力
     * @param attack  基础攻击力
     */
    void initPanel(long hp, long defence, long attack) {
        this.hp = hp;
        this.baseHpMax = hp;
        this.baseDefence = defence;
        this.baseAttack = attack;
    }

    /**
     * 把<b>临时属性</b>清零：五行组的 5 单元素穿透 + 5 元素增伤、12 个 {@code *Enhance*} 运行时加成、
     * 死字段 {@code extraDamage}，以及两个状态位（{@code alive} 回到真、{@code anticipating} 回到假）。
     * <p>
     * <b>面板组的基础值与抗性、法力成长、全局组（{@code penetration} / {@code enhance} /
     * {@code criticalDamage}）、{@code element}、{@code defenceLoss}、{@code manas} 与
     * {@code individualMultipleArea} 都不清</b> —— 它们是面板属性，清掉就是永久削弱这个角色。
     * （{@code hp} 的"每局补满"由 {@link LivingThing#whenFightEnds()} 自己
     * {@code setHp(getHpMax())} 做，那是一次有意的复位、不是清零。）
     * <p>
     * 由 {@link LivingThing#whenFightEnds()} 在整场战斗结束时调用：
     * 这 23 个数值是"战斗中一次性给出去"的加成，留着会渗进下一局。
     * <p>
     * <b>这张表和 {@link #copyFrom(AttributeProfile)} 是互补的</b>：复制只带面板属性、
     * 这里只清临时属性。改一个记得看另一个 —— 这正是
     * {@code ENTITY-ATTRIBUTE-SPLIT-2026-10.md} 第二节说的"把生命周期规则从靠记性变成靠结构"。
     */
    public void resetTemporary() {
        this.metalPenetration = 0;
        this.woodPenetration = 0;
        this.waterPenetration = 0;
        this.firePenetration = 0;
        this.dirtPenetration = 0;
        this.metalDamageEnhance = 0;
        this.woodDamageEnhance = 0;
        this.waterDamageEnhance = 0;
        this.fireDamageEnhance = 0;
        this.dirtDamageEnhance = 0;
        // 死字段（活的那套在 Skill#extraDamage 上）
        this.extraDamage = 0;
        // 12 个 *Enhance*（效果与技能成对加减；这里清一遍是兜底）
        this.attackEnhancePercent = 0;
        this.attackEnhanceAmount = 0;
        this.defenceEnhancePercent = 0;
        this.defenceEnhanceAmount = 0;
        this.speedEnhancePercent = 0;
        this.speedEnhanceAmount = 0;
        this.hpEnhancePercent = 0;
        this.hpEnhanceAmount = 0;
        this.criticalDamageEnhancePercent = 0;
        this.criticalDamageEnhanceAmount = 0;
        this.criticalRateEnhancePercent = 0;
        this.criticalRateEnhanceAmount = 0;
        // 状态类（2026-10-04 搬进来的）：战斗结束回到"活着、不在试算"
        this.alive = true;
        this.anticipating = false;
    }

    /**
     * @return 是否存活
     */
    public boolean isAlive() {
        return alive;
    }

    /**
     * @param alive 是否存活
     */
    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    /**
     * @return 是否正在做"预判 / 伤害试算"
     */
    public boolean isAnticipating() {
        return anticipating;
    }

    /**
     * @param anticipating 是否正在做"预判 / 伤害试算"
     */
    public void setAnticipating(boolean anticipating) {
        this.anticipating = anticipating;
    }

    /**
     * @return 当前生命值（基础值，没有"合并"这一步）
     */
    public long getHp() {
        return hp;
    }

    /* ------------------------------------------------------------------
     * ⓪ 面板组 · 基础值
     * ------------------------------------------------------------------ */

    /**
     * 设置当前生命值：若减少则发布 {@link HpLossEvent}、若增加则发布 {@link HpRestorationEvent}；
     * 最终值被限制在 {@code [0, 最终生命上限]} 之间。
     * <p>
     * <b>副作用为什么在组件里</b>：{@code /data merge|modify} 的写回落点是<b>字段所在的那个对象</b>
     * （也就是本组件）。副作用要是留在实体的转发 setter 上，{@code /data} 那条路会<b>静默绕过</b>它
     * （事件不发、血不夹）。上限读 {@link LivingThing#getHpMax()} —— 它还要叠
     * {@code hpEnhance*}，那一步只有实体算得对。
     *
     * @param hp 新的生命值
     */
    public void setHp(long hp) {
        long before = this.hp;
        if (hp < before) {
            EventBus.post(new HpLossEvent(before - hp, this.host));
        }
        if (hp > before) {
            EventBus.post(new HpRestorationEvent(hp - before, this.host));
        }
        this.hp = Math.min(this.host.getHpMax(), hp);
        if (this.hp < 0) {
            this.hp = 0;
        }
    }

    /**
     * @return 基础生命上限（最终上限见 {@link LivingThing#getHpMax()}）
     */
    public long getBaseHpMax() {
        return baseHpMax;
    }

    /**
     * @param baseHpMax 基础生命上限
     */
    public void setBaseHpMax(long baseHpMax) {
        this.baseHpMax = baseHpMax;
    }

    /**
     * @return 基础攻击力（最终值见 {@link LivingThing#getAttack()}）
     */
    public long getBaseAttack() {
        return baseAttack;
    }

    /**
     * @param baseAttack 基础攻击力
     */
    public void setBaseAttack(long baseAttack) {
        this.baseAttack = baseAttack;
    }

    /**
     * @return 基础防御力（最终值见 {@link LivingThing#getDefence()}）
     */
    public long getBaseDefence() {
        return baseDefence;
    }

    /**
     * @param baseDefence 基础防御力
     */
    public void setBaseDefence(long baseDefence) {
        this.baseDefence = baseDefence;
    }

    /**
     * @return 基础速度（最终值见 {@link LivingThing#getSpeed()}）
     */
    public long getBaseSpeed() {
        return baseSpeed;
    }

    /**
     * @param baseSpeed 基础速度
     */
    public void setBaseSpeed(long baseSpeed) {
        this.baseSpeed = baseSpeed;
    }

    /**
     * @return 基础暴击率（最终值见 {@link LivingThing#getCriticalRate()}）
     */
    public double getBaseCriticalRate() {
        return baseCriticalRate;
    }

    /**
     * @param baseCriticalRate 基础暴击率
     */
    public void setBaseCriticalRate(double baseCriticalRate) {
        this.baseCriticalRate = baseCriticalRate;
    }

    /**
     * @return 生命成长系数
     */
    public double getHpGrow() {
        return hpGrow;
    }

    /**
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
     * @param defenceGrow 防御成长系数
     */
    public void setDefenceGrow(double defenceGrow) {
        this.defenceGrow = defenceGrow;
    }

    /**
     * @return 元素属性（金木水火土）
     */
    public ElementSort getElement() {
        return element;
    }

    /**
     * @param element 元素属性
     */
    public void setElement(ElementSort element) {
        this.element = element;
    }

    /**
     * @return 防御削减百分比
     */
    public double getDefenceLoss() {
        return defenceLoss;
    }

    /**
     * @param defenceLoss 防御削减百分比
     */
    public void setDefenceLoss(double defenceLoss) {
        this.defenceLoss = defenceLoss;
    }

    /**
     * @return 单次额外伤害（死字段，永远是 0；活的那套在 {@code Skill#extraDamage}）
     */
    public long getExtraDamage() {
        return extraDamage;
    }

    /**
     * @param extraDamage 单次额外伤害（只有 {@code /data} 之类的调试手段会写它）
     */
    public void setExtraDamage(long extraDamage) {
        this.extraDamage = extraDamage;
    }

    /**
     * @return 单体伤害倍率的<b>生效值</b> = 面板基础值 × Π(每个来源的乘数)
     */
    public double getIndividualMultipleArea() {
        double result = individualMultipleArea;
        for (AreaMultiple each : individualMultipleAreaModifiers) {
            result *= each.factor();
        }
        return result;
    }

    /* ------------------------------------------------------------------
     * ⑨ 派生面板倍率
     * ------------------------------------------------------------------ */

    /**
     * 设置单体伤害倍率的<b>面板基础值</b>（来源乘数不受影响）。
     *
     * @param individualMultipleArea 单体伤害倍率（面板基础值）
     */
    public void setIndividualMultipleArea(double individualMultipleArea) {
        this.individualMultipleArea = individualMultipleArea;
    }

    /**
     * 挂一个「单体伤害倍率」的来源乘数（与 {@code LivingThing#addDamageReduction(Object, double)} 同形）。
     * <p>
     * <b>按来源身份覆盖</b>：同一个 {@code source} 再挂一次会<b>替换</b>旧值，不会叠两份 ——
     * 这样"同一个 buff 被刷新"不会越叠越高。
     *
     * @param source 来源对象（按 {@code ==} 身份比较；{@code null} 直接忽略）
     * @param factor 乘数（1.0 = 不变；1.5 = 加五成；0.5 = 减半）
     */
    public void addIndividualMultipleArea(Object source, double factor) {
        if (source == null) {
            return;
        }
        for (int i = 0; i < individualMultipleAreaModifiers.size(); i++) {
            if (individualMultipleAreaModifiers.get(i).source() == source) {
                individualMultipleAreaModifiers.set(i,
                        new AreaMultiple(source, LivingThing.describeReductionSource(source), factor));
                return;
            }
        }
        individualMultipleAreaModifiers.add(
                new AreaMultiple(source, LivingThing.describeReductionSource(source), factor));
    }

    /**
     * 摘掉某个来源的「单体伤害倍率」乘数。
     *
     * @param source 来源对象（按 {@code ==} 身份比较）
     * @return 有没有摘掉
     */
    public boolean removeIndividualMultipleArea(Object source) {
        return source != null
                && individualMultipleAreaModifiers.removeIf(each -> each.source() == source);
    }

    /**
     * 清空所有「单体伤害倍率」来源乘数（面板基础值不动）。
     * <p>
     * 由技能/机制挂上来的乘数走的是这里 —— 它们不走效果生命周期，战斗结束时没人摘，
     * 留着会渗进下一局（与减伤同一个理由，见 {@code LivingThing#whenFightEnds}）。
     */
    public void clearIndividualMultipleAreaModifiers() {
        individualMultipleAreaModifiers.clear();
    }

    /**
     * @return 法力表<b>本体</b>（活的列表：{@code getManas().add(...)} 会直接改到它）
     */
    public List<Mana> getManas() {
        return manas;
    }

    /* ------------------------------------------------------------------
     * ⑧ 容器 · 法力表
     * ------------------------------------------------------------------ */

    /**
     * @param manas 新的整张法力表（{@link LivingThing#initialMana()} 与 {@code /data} 都走整表替换）
     */
    public void setManas(List<Mana> manas) {
        this.manas = manas;
    }

    /**
     * @return 金抗性
     */
    public double getMetalResistance() {
        return metalResistance;
    }

    /* ------------------------------------------------------------------
     * ① 五行抗性
     * ------------------------------------------------------------------ */

    /**
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
     * @param woodResistance 木抗性
     */
    public void setWoodResistance(double woodResistance) {
        this.woodResistance = woodResistance;
    }

    /**
     * @return 水抗性
     */
    public double getWaterResistance() {
        return waterResistance;
    }

    /**
     * @param waterResistance 水抗性
     */
    public void setWaterResistance(double waterResistance) {
        this.waterResistance = waterResistance;
    }

    /**
     * @return 火抗性
     */
    public double getFireResistance() {
        return fireResistance;
    }

    /**
     * @param fireResistance 火抗性
     */
    public void setFireResistance(double fireResistance) {
        this.fireResistance = fireResistance;
    }

    /**
     * @return 土抗性
     */
    public double getDirtResistance() {
        return dirtResistance;
    }

    /**
     * @param dirtResistance 土抗性
     */
    public void setDirtResistance(double dirtResistance) {
        this.dirtResistance = dirtResistance;
    }

    /**
     * @return 金元素穿透
     */
    public double getMetalPenetration() {
        return metalPenetration;
    }

    /* ------------------------------------------------------------------
     * ② 五元素穿透
     * ------------------------------------------------------------------ */

    /**
     * @param metalPenetration 金元素穿透
     */
    public void setMetalPenetration(double metalPenetration) {
        this.metalPenetration = metalPenetration;
    }

    /**
     * @return 木元素穿透
     */
    public double getWoodPenetration() {
        return woodPenetration;
    }

    /**
     * @param woodPenetration 木元素穿透
     */
    public void setWoodPenetration(double woodPenetration) {
        this.woodPenetration = woodPenetration;
    }

    /**
     * @return 水元素穿透
     */
    public double getWaterPenetration() {
        return waterPenetration;
    }

    /**
     * @param waterPenetration 水元素穿透
     */
    public void setWaterPenetration(double waterPenetration) {
        this.waterPenetration = waterPenetration;
    }

    /**
     * @return 火元素穿透
     */
    public double getFirePenetration() {
        return firePenetration;
    }

    /**
     * @param firePenetration 火元素穿透
     */
    public void setFirePenetration(double firePenetration) {
        this.firePenetration = firePenetration;
    }

    /**
     * @return 土元素穿透
     */
    public double getDirtPenetration() {
        return dirtPenetration;
    }

    /**
     * @param dirtPenetration 土元素穿透
     */
    public void setDirtPenetration(double dirtPenetration) {
        this.dirtPenetration = dirtPenetration;
    }

    /**
     * @return 金元素伤害增强
     */
    public double getMetalDamageEnhance() {
        return metalDamageEnhance;
    }

    /* ------------------------------------------------------------------
     * ③ 五元素增伤
     * ------------------------------------------------------------------ */

    /**
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
     * @param dirtDamageEnhance 土元素伤害增强
     */
    public void setDirtDamageEnhance(double dirtDamageEnhance) {
        this.dirtDamageEnhance = dirtDamageEnhance;
    }

    /**
     * @return 金法力成长系数
     */
    public double getMetalManaGrow() {
        return metalManaGrow;
    }

    /* ------------------------------------------------------------------
     * ④ 五行法力成长系数
     * ------------------------------------------------------------------ */

    /**
     * @param metalManaGrow 金法力成长系数
     */
    public void setMetalManaGrow(double metalManaGrow) {
        this.metalManaGrow = metalManaGrow;
    }

    /**
     * @return 木法力成长系数
     */
    public double getWoodManaGrow() {
        return woodManaGrow;
    }

    /**
     * @param woodManaGrow 木法力成长系数
     */
    public void setWoodManaGrow(double woodManaGrow) {
        this.woodManaGrow = woodManaGrow;
    }

    /**
     * @return 水法力成长系数
     */
    public double getWaterManaGrow() {
        return waterManaGrow;
    }

    /**
     * @param waterManaGrow 水法力成长系数
     */
    public void setWaterManaGrow(double waterManaGrow) {
        this.waterManaGrow = waterManaGrow;
    }

    /**
     * @return 火法力成长系数
     */
    public double getFireManaGrow() {
        return fireManaGrow;
    }

    /**
     * @param fireManaGrow 火法力成长系数
     */
    public void setFireManaGrow(double fireManaGrow) {
        this.fireManaGrow = fireManaGrow;
    }

    /**
     * @return 土法力成长系数
     */
    public double getDirtManaGrow() {
        return dirtManaGrow;
    }

    /**
     * @param dirtManaGrow 土法力成长系数
     */
    public void setDirtManaGrow(double dirtManaGrow) {
        this.dirtManaGrow = dirtManaGrow;
    }

    /**
     * @return 全属性穿透
     */
    public double getPenetration() {
        return penetration;
    }

    /* ------------------------------------------------------------------
     * ⑤ 全局组
     * ------------------------------------------------------------------ */

    /**
     * @param penetration 全属性穿透
     */
    public void setPenetration(double penetration) {
        this.penetration = penetration;
    }

    /**
     * @return 全属性增伤百分比
     */
    public double getEnhance() {
        return enhance;
    }

    /**
     * @param enhance 全属性增伤百分比
     */
    public void setEnhance(double enhance) {
        this.enhance = enhance;
    }

    /**
     * @return 基础暴击伤害倍率（不含 {@code criticalDamageEnhance*} 的加成）
     */
    public double getBaseCriticalDamage() {
        return baseCriticalDamage;
    }

    /**
     * @param baseCriticalDamage 基础暴击伤害倍率
     */
    public void setBaseCriticalDamage(double baseCriticalDamage) {
        this.baseCriticalDamage = baseCriticalDamage;
    }

    /**
     * @return 攻击增强百分比
     */
    public double getAttackEnhancePercent() {
        return attackEnhancePercent;
    }

    /* ------------------------------------------------------------------
     * ⑥ *Enhance* 运行时加成
     * ------------------------------------------------------------------ */

    /**
     * @param attackEnhancePercent 攻击增强百分比
     */
    public void setAttackEnhancePercent(double attackEnhancePercent) {
        this.attackEnhancePercent = attackEnhancePercent;
    }

    /**
     * @return 防御增强百分比
     */
    public double getDefenceEnhancePercent() {
        return defenceEnhancePercent;
    }

    /**
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
     * 设置生命增强百分比，并立即把当前生命夹回新的上限内
     * （{@link LivingThing#renewHp()}；上限变小之后旧生命可能超出）。
     *
     * @param hpEnhancePercent 生命增强百分比
     */
    public void setHpEnhancePercent(double hpEnhancePercent) {
        this.hpEnhancePercent = hpEnhancePercent;
        this.host.renewHp();
    }

    /**
     * @return 暴击伤害增强百分比
     */
    public double getCriticalDamageEnhancePercent() {
        return criticalDamageEnhancePercent;
    }

    /**
     * @param criticalDamageEnhancePercent 暴击伤害增强百分比
     */
    public void setCriticalDamageEnhancePercent(double criticalDamageEnhancePercent) {
        this.criticalDamageEnhancePercent = criticalDamageEnhancePercent;
    }

    /**
     * @return 暴击伤害增强固定值
     */
    public double getCriticalDamageEnhanceAmount() {
        return criticalDamageEnhanceAmount;
    }

    /**
     * @param criticalDamageEnhanceAmount 暴击伤害增强固定值
     */
    public void setCriticalDamageEnhanceAmount(double criticalDamageEnhanceAmount) {
        this.criticalDamageEnhanceAmount = criticalDamageEnhanceAmount;
    }

    /**
     * @return 暴击率增强百分比
     */
    public double getCriticalRateEnhancePercent() {
        return criticalRateEnhancePercent;
    }

    /**
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
     * @param criticalRateEnhanceAmount 暴击率增强固定值
     */
    public void setCriticalRateEnhanceAmount(double criticalRateEnhanceAmount) {
        this.criticalRateEnhanceAmount = criticalRateEnhanceAmount;
    }

    /**
     * @return 攻击增强固定值
     */
    public long getAttackEnhanceAmount() {
        return attackEnhanceAmount;
    }

    /**
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
     * @param speedEnhanceAmount 速度增强固定值
     */
    public void setSpeedEnhanceAmount(long speedEnhanceAmount) {
        this.speedEnhanceAmount = speedEnhanceAmount;
    }

    /**
     * @return 生命增强固定值
     */
    public long getHpEnhanceAmount() {
        return hpEnhanceAmount;
    }

    /**
     * 设置生命增强固定值，并立即把当前生命夹回新的上限内
     * （{@link LivingThing#renewHp()}；上限变小之后旧生命可能超出）。
     *
     * @param hpEnhanceAmount 生命增强固定值
     */
    public void setHpEnhanceAmount(long hpEnhanceAmount) {
        this.hpEnhanceAmount = hpEnhanceAmount;
        this.host.renewHp();
    }

    /**
     * 一条「单体伤害倍率」来源乘数。
     * <p>
     * {@code source} 是<b>按对象身份</b>比较的那个来源（效果 / 技能 / 任意 {@code Object}），
     * {@code sourceName} 只是给人看的名字（日志与排错用，不参与比较）。
     *
     * @param source     来源对象（按 {@code ==} 身份比较）
     * @param sourceName 来源的显示名
     * @param factor     乘数（1.0 = 不变；1.5 = 加五成）
     */
    public record AreaMultiple(Object source, String sourceName, double factor) {
    }
}
