package cn.gfhnv.game.officialStuff.customEntity.monsters;

import cn.gfhnv.game.damage.DamageType;
import cn.gfhnv.game.data.NoConfig;
import cn.gfhnv.game.data.NoData;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entityController.FixOrderController;
import cn.gfhnv.game.event.DamageEvent;
import cn.gfhnv.game.eventListener.FightTurnPastListener;
import cn.gfhnv.game.interfaces.IModifyDamage;
import cn.gfhnv.game.officialStuff.customEffect.flameReaverEffects.LockedRite;
import cn.gfhnv.game.officialStuff.customEffect.flameReaverEffects.PainEntanglement;
import cn.gfhnv.game.officialStuff.customEffect.flameReaverEffects.SacrificeRite;
import cn.gfhnv.game.officialStuff.customEntity.summons.BrokenContainer;
import cn.gfhnv.game.officialStuff.customSkill.flameReaverSkills.*;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.ActionSignal;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.fight.TurnEntry;
import cn.gfhnv.game.system.fight.TurnManager;
import cn.gfhnv.game.utils.ConsoleColor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 【至黑之剑，盗火行者】—— 剧情 BOSS。
 * <p>
 * 官方机制在本项目的映射（本项目没有物理属性，全部改为<b>火属性</b>；
 * 「主目标及其相邻目标」映射为<b>目标数 3</b>）：
 * <ul>
 *     <li><b>灾难之力</b>：吸收【残破容器】获得，每层提高自身伤害；大招消耗全部层数换段数。
 *     <b>不设上限</b>（用户指定）。</li>
 *     <li><b>苦痛缠绕</b>：召唤容器时按消耗的生命值记账；吸收时按这本账回血。
 *     玩家杀掉容器 = 这笔账永远收不回来，这是"清召唤物"的核心收益。
 *     账本本身是挂在身上的效果 {@link PainEntanglement}（无限持续，可查可清）。</li>
 *     <li><b>永别的决绝</b>：身上若干层减伤，<b>每有 1 个残破容器被玩家消灭就掉 1 层</b>。</li>
 *     <li><b>共祭</b>：挂在【残破容器】身上的效果 {@link SacrificeRite}，
 *     origin = 本 BOSS 的 UUID。</li>
 *     <li><b>分离的哀痛 / 幽冥的悼念</b>：见
 *     {@code CloudOfDeath}、{@code FateDrawsNear}、{@code TangledPathsOfMourning} 的注释。</li>
 * </ul>
 * <b>2026-09 已全部实现</b>（这里原来写着"未实现（第二步）：完整容器、二阶段、【沉默的悲叹】、【镣锁】与【为我设奠】复活"，
 * 那句话已经过期，别再照它判断进度）：
 * <ul>
 *     <li><b>完整容器</b>：{@link BrokenContainer.Kind#COMPLETE}，击杀者拿【破容器之赏】+ 额外回合
 *     （{@link #grantContainerReward}）；放着不管则被吸收并额外充能；</li>
 *     <li><b>二阶段</b>：<b>血条第一次被清空</b>时切换（{@link #enterPhaseTwo}）——
 *     第一发致命伤害会被 {@link #phaseTwoDeathWard()} 拦下，所以它<b>不可能被一击秒掉</b>，
 *     一定会进二阶段，并且切换时生命<b>回满</b>；进入后获得
 *     {@value cn.gfhnv.game.system.configLoadingSystem.RuleDefaults#FLAME_REAVER_PHASE_TWO_DAMAGE_REDUCTION} 的免伤
 *     ——它与层数减伤<b>分属两个来源键</b>，会同时生效。保护只有一次，
 *     二阶段之后再被打到 0 才会真的倒下；</li>
 *     <li><b>【沉默的悲叹】</b>：{@code SilentLament} 蓄力，下一次行动放【莫因舍弃而哭泣】；</li>
 *     <li><b>【镣锁】</b>：{@link LockedRite}，由二阶段的【迷失的共祭】给新召唤的容器挂上；</li>
 *     <li><b>【为我设奠】复活</b>：{@link #tryReviveLocked} —— 处于【镣锁】的容器受到致命攻击时，
 *     消耗 1 层【灾难之力】与一定比例生命值<b>重新召唤一只新的</b>（官方原文就是"重新召唤"，不是把原体捞回来）。</li>
 * </ul>
 *
 * @author AI（DeepSeek）生成
 */
public class FlameReaver extends LivingThing {

    /**
     * 减伤来源键：【永别的决绝】的层数减伤。用固定键，方便"层数变化时重设"而不是累加。
     */
    public static final Object DAMAGE_REDUCTION_KEY =
            new cn.gfhnv.game.entity.DamageReductionSource("永别的决绝");

    /**
     * 减伤来源键：二阶段的【高额免伤】。
     * <p>
     * 与 {@link #DAMAGE_REDUCTION_KEY} <b>分开</b>：两者会同时存在（层数减伤 + 阶段免伤），
     * 而"同一来源键重复添加是幂等的"，共用一个键会互相覆盖。
     */
    public static final Object PHASE_TWO_REDUCTION_KEY =
            new cn.gfhnv.game.entity.DamageReductionSource("二阶段免伤");

    /* ---------------- 可调数值 ----------------
     * 这一组全部可在 config/gameConfig/GameRules.json 的 flameReaver 段里改；
     * 不写就用 RuleDefaults 里的出厂值（与下面注释里记的手感完全一致）。
     * 改它们不会动机制，只动手感 —— 机制性的常量（轮转顺序、共祭窗口长度……）
     * 仍然写在各自技能/方法的代码里。
     */
    /**
     * 击杀【完整容器】发的那个额外回合的<b>来源标识</b>（会打进日志）。
     * <p>
     * 白厄变身的连击与这个奖励<b>都是"额外回合"</b>，日志里长得一模一样 ——
     * 带上标识才看得出<b>到底哪一条先执行</b>，也是排查"奖励回合被顶掉 / 空过"的唯一抓手。
     */
    public static final String EXTRA_TURN_TAG = "完整容器奖励";
    /**
     * 【破容器之赏】的<b>显示名</b>（日志里就显示这个，不再是效果类名）。
     */
    public static final String CONTAINER_REWARD_NAME = "破容器之赏";
    /**
     * 【破容器之赏】在效果列表里的<b>初始</b> id。
     * <p>
     * ⚠️ 注意它只是个短 id：效果一旦进 {@code addEffect}，框架会按【类】把 id 补成完整 id
     * （{@code World#registeredIdOf} 按类查表 → 实际会变成
     * {@code game_official_content:damageEnhanceEffect}，见 {@code OfficialGameContent} 的注释）。
     * <b>所以真正把「破容器之赏」跟别的增伤区分开的是 {@link #CONTAINER_REWARD_NAME 显示名}，不是这个 id。</b>
     * <p>
     * 即便如此也不影响"每次击杀各自独立"：{@code Effect#equals} 判的是
     * {@code id + 是否无限 + origin}，三次击杀的 {@code origin} 不同，不会被合掉。
     */
    public static final String CONTAINER_REWARD_EFFECT_ID = "containerReward";
    /**
     * 【破容器之赏】的增伤比例（+40%）。
     */
    public static final double CONTAINER_REWARD_ENHANCE = 0.4;
    /**
     * 【破容器之赏】持续回合数。
     */
    public static final int CONTAINER_REWARD_LAST_TIME = 3;
    /**
     * 出厂生命上限（不靠等级成长系数去凑）：150 级时成长公式只给到 1.8 万，太脆。
     * <p>
     * <b>2026-11-14 第 52 批：这个数不再走规则表</b>（原来 `GameRules.json` 有一个
     * `flameReaver.baseHpMax`，而 `EntityData.json` 的 `derived.baseHpMax` 又有一个 —— 同一个值两个入口）。
     * 现在只剩后一个入口：构造器先按本常量铺底，配置要改就用 `derived.baseHpMax`。
     */
    public static final long DEFAULT_BASE_HP = 80000L;
    /**
     * 共祭那一轮"一同攻击"的<b>共同目标数</b>。
     * <p>
     * 官方原文是"与【残破容器】一同施放【亡死的黑云】<b>或</b>【将尽的命数】"，
     * 而亡死的黑云打"主目标及其相邻目标"= 本项目映射的 <b>3 目标</b>（见 {@code CloudOfDeath}）。
     * 取一个固定数（而不是"全体"）是为了让"清场"与"共祭"的代价看得见：
     * 目标少的队伍不会被一轮打穿。
     */
    private static final int JOINT_ATTACK_TARGETS = 3;
    /**
     * 「亡死的黑云」召唤出来、还没被它自己吸收的容器。
     * <p>
     * 官方「分离的哀痛」写的是"<b>曾被该攻击召唤</b>且处于【共祭】状态的残破容器"会一同攻击并被吸收 ——
     * 也就是<b>每个招式只回收它自己召唤的那批</b>。所以这里按招式分开记账，
     * 而不是"吸收场上所有容器"：后者会让容器只活一轮，玩家永远看不到协同攻击。
     */
    private final List<BrokenContainer> cloudOfDeathSummons = new ArrayList<>();
    /**
     * 「将尽的命数」召唤出来、还没被它自己吸收的容器（同上）。
     */
    private final List<BrokenContainer> fateDrawsNearSummons = new ArrayList<>();
    private int disasterPower = 0;
    /**
     * 剩下的"共祭窗口"回合数（由 {@code TangledPathsOfMourning} 在场上没有容器时开启）。
     * <p>
     * 窗口大于 0 时，<b>新召唤出来的容器直接进入【共祭】</b> ——
     * 这样"相混的道途"不会因为"玩家清场太快"而变成空放，玩家也就永远看不到
     * 协同攻击与吸收这一整套机制。
     */
    private int sacrificeWindow = 0;
    private int damageReductionLayers = 2;

    /* ---------------- 机制旋钮（2026-11-14 第 53 批：从 GameRules 搬进实例字段） ---------------- */
    /**
     * 【永别的决绝】的<b>出厂</b>减伤层数（classState 键 {@code damageReductionLayersDefault}）。
     * <p>
     * 与 {@link #damageReductionLayers} 分开：后者是<b>当前</b>层数（打掉就少，整局会变），
     * 这个是「开局该有几层」。
     */
    private int damageReductionLayersDefault = 2;
    /**
     * 每次召唤消耗自身最大生命的比例。
     */
    private double summonHpCostRate = 0.03;
    /**
     * 每层【灾难之力】提供的加伤比例。
     */
    private double disasterPowerAttackBonus = 0.08;
    /**
     * 【永别的决绝】每层减伤比例。
     */
    private double damageReductionPerLayer = 0.25;
    /**
     * 场上容器数量上限（{@code 0} = 不限）。
     */
    private int containerLimit = 0;
    /**
     * 每次召唤出现【完整容器】的概率。
     */
    private double completeContainerChance = 0.34;
    /**
     * 二阶段的【高额免伤】。
     */
    private double phaseTwoDamageReduction = 0.7;
    /**
     * 容器生命占盗火行者最大生命的比例。
     */
    private double containerHpRatio = 0.15;
    /**
     * 容器攻击占盗火行者攻击的比例。
     */
    private double containerAttackRatio = 0.4;

    /**
     * 二阶段的触发条件：<b>血条第一次被清空</b>（2026-10-03 起不再用血量比例阈值）。
     * <p>
     * 实现挂在伤害修正器上（{@link #phaseTwoDeathWard()}）：任何会把生命打到
     * {@code <= 0} 的伤害都会被拦下，改为立刻切阶段并把生命回满 ——
     * 所以盗火行者不可能被一击秒掉，一定能进二阶段。
     * 保护<b>只有一次</b>：{@code phaseTwo} 置位后那个修正器就不再拦。
     */
    /**
     * 【完整容器】生命占盗火行者最大生命的比例。
     */
    private double completeContainerHpRatio = 0.25;

    /* ---------------- 状态 ---------------- */
    /**
     * 是否已进入二阶段。
     */
    private boolean phaseTwo = false;
    /**
     * 转阶段是否已经排进当前回合的队列（防止同一回合多段伤害重复排队）。
     * <p>
     * 它是运行期状态，不是模板属性：不进 {@code /data}、不进配置。
     */
    @NoData
    @NoConfig("运行期状态（转阶段是否已排队），不是模板属性")
    private boolean phaseTwoPending = false;
    /**
     * 转阶段行动在回合队列里的优先级：必须<b>高于</b>额外行动（星魂 2 的 800）
     * 和正常行动（0），否则玩家会先看到"白厄的额外行动"，再看到 BOSS 回满血。
     */
    private static final int PHASE_TRANSITION_PRIORITY =
            cn.gfhnv.game.system.fight.ActEntry.PRIORITY_PHASE_TRANSITION;
    /**
     * 是否处在【沉默的悲叹】的蓄力中（下一次行动要放【莫因舍弃而哭泣】）。
     */
    private boolean charging = false;

    public FlameReaver(FlameReaver other) {
        super(other);
        // 二阶段的「切阶段保护」。super(other) 会把原体的修正器链一并复制过来，
        // 所以这里必须用 set（清空 + 只留这一个），否则副本身上会挂两条同样的保护
        this.setModifyDamage(phaseTwoDeathWard());
        this.disasterPower = other.disasterPower;
        this.damageReductionLayers = other.damageReductionLayers;
        this.damageReductionLayersDefault = other.damageReductionLayersDefault;
        this.summonHpCostRate = other.summonHpCostRate;
        this.disasterPowerAttackBonus = other.disasterPowerAttackBonus;
        this.damageReductionPerLayer = other.damageReductionPerLayer;
        this.containerLimit = other.containerLimit;
        this.completeContainerChance = other.completeContainerChance;
        this.phaseTwoDamageReduction = other.phaseTwoDamageReduction;
        this.containerHpRatio = other.containerHpRatio;
        this.containerAttackRatio = other.containerAttackRatio;
        this.completeContainerHpRatio = other.completeContainerHpRatio;
        // 【苦痛缠绕】是效果，已由 super(other) 的复制构造器一并复制（效果列表被复制）
        this.cloudOfDeathSummons.addAll(other.cloudOfDeathSummons);
        this.fateDrawsNearSummons.addAll(other.fateDrawsNearSummons);
        this.applyDisasterPower();
        this.applyDamageReductionLayers();
        List<Skill> skills = new ArrayList<>(this.getController().getSkills());
        // 轮转顺序见 rotationNames()（顺序即节奏）
        FixOrderController controller = new FixOrderController(skills, this);
        controller.setSkipUnusable(true);
        controller.setRotationByName(rotationNames());
        this.setController(controller);
    }

    /**
     * 构造盗火行者。
     *
     * @param l 等级
     */
    public FlameReaver(long l) {
        super("至黑之剑，盗火行者", "flameReaver", 0.7, 0.2, 0.2, 0.2, 0.2,
                200, l, "boss", 120, 60, 25, ElementSort.FIRE);
        this.setDescription("伴随黑潮而来、狩猎泰坦火种的无名剑士，无人知晓其真身。"
                + "身负不可思议的力量，剑技也近乎无懈可击。");
        // 血量直接给上限：召唤容器是按"最大生命的百分比"扣血的，底子太薄会自己把自己耗死
        this.setHpMax(DEFAULT_BASE_HP);
        this.setHp(DEFAULT_BASE_HP);
        List<Skill> skills = new ArrayList<>();
        skills.add(new CloudOfDeath());
        skills.add(new FateDrawsNear());
        skills.add(new TangledPathsOfMourning());
        skills.add(new SacrificeOfTheLost());
        skills.add(new NecessarySuffering());
        // 二阶段招式的类常驻技能表，靠 canUse() 限制"只有二阶段能放" ——
        // 这样切阶段时只需要换轮转表（setRotationByName），不用重建技能表、也不会丢冷却状态。
        skills.add(new SilentLament());
        skills.add(new MournNotAbandon());
        FixOrderController controller = new FixOrderController(skills, this);
        controller.setSkipUnusable(true);
        controller.setRotationByName(rotationNames());
        this.setController(controller);
        // 二阶段的「切阶段保护」：血条第一次被清空时拦下那一击并切阶段（见 phaseTwoDeathWard）
        this.setModifyDamage(phaseTwoDeathWard());
    }

    /**
     * @param targets 目标列表；可为 {@code null}
     * @return 里面是否还有活着的目标
     */
    private static boolean hasLivingTarget(List<LivingThing> targets) {
        if (targets == null) {
            return false;
        }
        for (LivingThing target : targets) {
            if (target != null && target.isAlive()) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return 每次召唤消耗自身最大生命的比例
     */
    public double getSummonHpCostRate() {
        return summonHpCostRate;
    }

    /**
     * @param summonHpCostRate 每次召唤消耗自身最大生命的比例
     */
    public void setSummonHpCostRate(double summonHpCostRate) {
        this.summonHpCostRate = summonHpCostRate;
    }

    /**
     * @return 每层【灾难之力】提供的加伤比例
     */
    public double getDisasterPowerAttackBonus() {
        return disasterPowerAttackBonus;
    }

    /**
     * @param disasterPowerAttackBonus 每层【灾难之力】提供的加伤比例
     */
    public void setDisasterPowerAttackBonus(double disasterPowerAttackBonus) {
        this.disasterPowerAttackBonus = disasterPowerAttackBonus;
    }

    /**
     * @return 【永别的决绝】初始减伤层数
     */
    public int getDamageReductionLayersDefault() {
        return damageReductionLayersDefault;
    }

    /**
     * @param damageReductionLayers 【永别的决绝】初始减伤层数（下一局开局生效）
     */
    public void setDamageReductionLayersDefault(int damageReductionLayersDefault) {
        this.damageReductionLayersDefault = damageReductionLayersDefault;
    }

    /**
     * @return 【永别的决绝】每层减伤比例
     */
    public double getDamageReductionPerLayer() {
        return damageReductionPerLayer;
    }

    /**
     * @param damageReductionPerLayer 【永别的决绝】每层减伤比例
     */
    public void setDamageReductionPerLayer(double damageReductionPerLayer) {
        this.damageReductionPerLayer = damageReductionPerLayer;
    }

    /**
     * @return 场上容器数量上限（{@code 0} = 不限）
     */
    public int getContainerLimit() {
        return containerLimit;
    }

    /**
     * @param containerLimit 场上容器数量上限（{@code 0} = 不限）
     */
    public void setContainerLimit(int containerLimit) {
        this.containerLimit = containerLimit;
    }

    /**
     * @return 每次召唤出现【完整容器】的概率
     */
    public double getCompleteContainerChance() {
        return completeContainerChance;
    }

    /**
     * @param completeContainerChance 每次召唤出现【完整容器】的概率
     */
    public void setCompleteContainerChance(double completeContainerChance) {
        this.completeContainerChance = completeContainerChance;
    }

    /**
     * @return 二阶段的【高额免伤】
     */
    public double getPhaseTwoDamageReduction() {
        return phaseTwoDamageReduction;
    }

    /**
     * @param phaseTwoDamageReduction 二阶段的【高额免伤】
     */
    public void setPhaseTwoDamageReduction(double phaseTwoDamageReduction) {
        this.phaseTwoDamageReduction = phaseTwoDamageReduction;
    }

    /**
     * @return 容器生命占盗火行者最大生命的比例
     */
    public double getContainerHpRatio() {
        return containerHpRatio;
    }

    /**
     * @param containerHpRatio 容器生命占盗火行者最大生命的比例
     */
    public void setContainerHpRatio(double containerHpRatio) {
        this.containerHpRatio = containerHpRatio;
    }

    /**
     * @return 容器攻击占盗火行者攻击的比例
     */
    public double getContainerAttackRatio() {
        return containerAttackRatio;
    }

    /**
     * @param containerAttackRatio 容器攻击占盗火行者攻击的比例
     */
    public void setContainerAttackRatio(double containerAttackRatio) {
        this.containerAttackRatio = containerAttackRatio;
    }

    /**
     * @return 【完整容器】生命占盗火行者最大生命的比例
     */
    public double getCompleteContainerHpRatio() {
        return completeContainerHpRatio;
    }

    /**
     * @param completeContainerHpRatio 【完整容器】生命占盗火行者最大生命的比例
     */
    public void setCompleteContainerHpRatio(double completeContainerHpRatio) {
        this.completeContainerHpRatio = completeContainerHpRatio;
    }

    /**
     * BOSS 的固定轮转顺序（按阶段不同）。
     * <p>
     * <b>阶段一</b>（官方顺序，来源：官方招式解析）：
     * <pre>
     * 1. 亡死的黑云      3 目标伤害 + 召唤 1 只容器
     * 2. 将尽的命数      全体伤害 + 侵蚀 + 召唤 1 只容器
     * 3. 相混的道途      让场上容器进入【共祭】
     * 4. 幽冥的悼念      共祭容器协同攻击 → 随后被吸收（回血 + 灾难之力）
     * 5. 却是必要的苦难  消耗全部灾难之力：N 段 + 收尾一击（可以空放）
     * </pre>
     * 第 3 步与第 4 步是<b>两个回合</b>，中间隔着玩家一个回合 —— 玩家可以趁机把共祭容器清掉，
     * 让第 4 步落空。这就是官方"清召唤物"博弈的落点，<b>不要把这两步合并</b>。
     * <p>
     * <b>阶段二</b>在阶段一的基础上把大招换成「蓄力 → 莫因舍弃而哭泣」：
     * <pre>
     * … → 沉默的悲叹（蓄力：拿灾难之力 + 下次行动放大招） → 莫因舍弃而哭泣（全体 → 按层数追加）
     * </pre>
     *
     * @return 轮转顺序（技能名）
     */
    private List<String> rotationNames() {
        if (!phaseTwo) {
            return List.of(
                    "亡死的黑云",
                    "将尽的命数",
                    "相混的道途",
                    "幽冥的悼念",
                    "却是必要的苦难");
        }
        return List.of(
                "亡死的黑云",
                "将尽的命数",
                "相混的道途",
                "幽冥的悼念",
                "沉默的悲叹",
                "莫因舍弃而哭泣");
    }

    @Override
    public LivingThing copy() {
        return new FlameReaver(this);
    }

    @Override
    public void whenFightStart(Fight fight) {
        super.whenFightStart(fight);
        this.disasterPower = 0;
        this.setPainTangled(0);
        this.damageReductionLayers = this.damageReductionLayersDefault;
        this.sacrificeWindow = 0;
        this.clearPhaseTwo();
        this.cloudOfDeathSummons.clear();
        this.fateDrawsNearSummons.clear();
        this.applyDisasterPower();
        this.applyDamageReductionLayers();
    }

    @Override
    public void whenFightEnds() {
        super.whenFightEnds();
        this.sacrificeWindow = 0;
        this.cloudOfDeathSummons.clear();
        this.fateDrawsNearSummons.clear();
        this.removeDamageReduction(DAMAGE_REDUCTION_KEY);
        this.clearPhaseTwo();
    }

    /* ------------------------------------------------------------------
     * 灾难之力
     * ------------------------------------------------------------------ */

    /**
     * 开启一个"共祭窗口"：接下来 {@code turns} 个自己的回合内，
     * 新召唤出来的容器<b>直接进入【共祭】</b>。
     * <p>
     * 由 {@code TangledPathsOfMourning} 在"场上还没有容器"时调用 ——
     * 让这一招像官方那样无条件生效，而不是空放。
     *
     * @param turns 窗口长度（按 BOSS 自己的回合计）
     */
    public void openSacrificeWindow(int turns) {
        this.sacrificeWindow = Math.max(this.sacrificeWindow, turns);
        System.out.println(getName() + "进入【共祭】的引导状态（" + sacrificeWindow + " 回合内召唤的容器直接共祭）");
    }

    /**
     * 消耗一次"共祭窗口"（每召唤一只容器消耗一次）。
     */
    private void consumeSacrificeWindow() {
        if (sacrificeWindow > 0) {
            sacrificeWindow--;
        }
    }

    /**
     * @return 当前【灾难之力】层数
     */
    public int getDisasterPower() {
        return disasterPower;
    }

    /**
     * 增加【灾难之力】（吸收容器时调用）。
     *
     * @param amount 增加层数
     */
    public void addDisasterPower(int amount) {
        if (amount <= 0) {
            return;
        }
        this.disasterPower += amount;
        System.out.println(getName() + "获得【灾难之力】，当前 " + disasterPower + " 层");
        applyDisasterPower();
    }

    /* ------------------------------------------------------------------
     * 苦痛缠绕
     * ------------------------------------------------------------------ */

    /**
     * 消耗【灾难之力】。
     *
     * @param amount 消耗层数
     * @return 实际消耗掉的层数
     */
    public int consumeDisasterPower(int amount) {
        int consumed = Math.min(Math.max(0, amount), disasterPower);
        this.disasterPower -= consumed;
        applyDisasterPower();
        return consumed;
    }

    /**
     * 把【灾难之力】层数换算成加伤。
     * <p>
     * <b>先减后加、每次按层数重算</b>：直接 {@code set(get() + 0.08)} 会在层数变化时累加出错
     * （掉层时无法还原）。
     */
    private void applyDisasterPower() {
        this.setEnhance(disasterPower * this.disasterPowerAttackBonus);
    }

    /* ------------------------------------------------------------------
     * 召唤与吸收
     * ------------------------------------------------------------------ */

    /**
     * @return 当前【苦痛缠绕】账本（累计算记、还没回收的生命值）
     * <p>
     * 账本本身是挂在身上的效果 {@link PainEntanglement}（无限持续、可查可清），
     * 不再用本类的一个 long 字段存。
     */
    public long getPainTangled() {
        return PainEntanglement.amountOf(this);
    }

    /**
     * 覆盖【苦痛缠绕】账本（供调试命令 / 后续阶段二使用）。
     *
     * @param painTangled 账本数值
     */
    public void setPainTangled(long painTangled) {
        PainEntanglement pain = PainEntanglement.of(this);
        if (pain == null) {
            if (painTangled <= 0) {
                return;
            }
            PainEntanglement.add(this, painTangled);
            return;
        }
        pain.setPain(painTangled);
    }

    /**
     * @return 场上还活着的残破容器
     * <p>
     * 用 {@link Fight#getOwnList(LivingThing)} 取"和 BOSS <b>同一侧</b>的单位"（含 BOSS 自己
     * 与它召唤的所有容器），再按类型筛出容器 —— 这是实测确认的语义，
     * 与"取目标用 {@code getOpponentList}"正好分工相反，别搞混。
     */
    public List<BrokenContainer> getAliveContainers(Fight fight) {
        List<BrokenContainer> containers = new ArrayList<>();
        if (fight == null) {
            return containers;
        }
        for (LivingThing livingThing : fight.getOwnList(this)) {
            if (livingThing instanceof BrokenContainer container && container.isAlive()) {
                containers.add(container);
            }
        }
        return containers;
    }

    /**
     * 召唤一只【残破容器】：消耗自身生命值，并把消耗掉的生命值记进【苦痛缠绕】。
     * <p>
     * 官方「分离的哀痛」：施放【亡死的黑云】或【将尽的命数】时消耗生命值召唤残破容器。
     *
     * @param fight 当前战斗
     * @return 召唤出的容器；场上已达上限或战斗为空时返回 {@code null}
     */
    public BrokenContainer summonContainer(Fight fight) {
        if (fight == null) {
            return null;
        }
        return summonContainer(fight, BrokenContainer.Kind.BROKEN);
    }

    /**
     * 召唤一只容器（可指定种类），并把消耗的生命值记进【苦痛缠绕】。
     *
     * @param fight 当前战斗
     * @param kind  容器种类（残破 / 完整）
     * @return 召唤出的容器；达到 {@code flameReaver.containerLimit}（非 0 时）或战斗为空时返回 {@code null}
     */
    public BrokenContainer summonContainer(Fight fight, BrokenContainer.Kind kind) {
        if (fight == null) {
            return null;
        }
        // containerLimit = 0 表示不限制（用户 2026-09 指定）
        int limit = this.containerLimit;
        if (limit > 0) {
            List<BrokenContainer> alive = getAliveContainers(fight);
            if (alive.size() >= limit) {
                System.out.println(getName() + "的容器已达上限（" + limit + "），不再召唤");
                return null;
            }
        }
        long cost = (long) (getHpMax() * this.summonHpCostRate);
        if (cost <= 0) {
            cost = 1;
        }
        // 消耗生命值：至少留 1 点，避免 BOSS 把自己召唤死
        long newHp = Math.max(1, getHp() - cost);
        long realCost = getHp() - newHp;
        this.setHp(newHp);
        // 官方：消耗的生命值转化为【苦痛缠绕】（挂在身上的账本效果）
        PainEntanglement.add(this, realCost);
        BrokenContainer container = new BrokenContainer(this, kind);
        // 官方：消耗的生命值转化为【苦痛缠绕】，吸收时按这笔账回血。
        // 所以把这次实际消耗记在容器身上，吸收时就能精确还多少（不必假设它等于最大生命）。
        container.setPainCost(realCost);
        // 【必须按召唤者所在的阵营入列】—— 盗火行者既能当敌方 BOSS，也能被玩家选成自己的角色
        // （它在同一个注册表里，选人/选敌人读的是同一份列表）。早先这里无条件 addEnemy：
        // 玩家侧的盗火行者召唤出来的容器会被塞进【敌方列表】，于是
        // Fight#getOpponentList(容器) 落到 else 分支返回【我方】——
        // 自家容器打自家队伍，还给自家 BOSS 叠上一条 origin 不同的【侵蚀】（实测踩过）。
        addSummonToOwnSide(fight, container);
        // addEnemy/addFighter 会把它加入 allEntities 并排好回合；但「战斗开始」那个钩子只在开局
        // 遍历 allEntities 时调用一次（FightStartEventListener），**中途召唤出来的实体收不到**。
        // 所以这里显式补一次入场初始化：接上战斗上下文 + 调 whenFightStart。
        container.setParticipateFight(fight);
        container.whenFightStart(fight);
        // 处在"共祭窗口"内时，新容器直接进入共祭（让相混的道途不空放）
        if (sacrificeWindow > 0) {
            container.enterSacrifice(SacrificeRite.DEFAULT_LAST_TIME, getUUID());
            consumeSacrificeWindow();
        }
        System.out.println(ConsoleColor.yellow(getName() + "消耗" + realCost + "点生命召唤了【" + kind.displayName()
                + "】（苦痛缠绕 " + getPainTangled() + "）"));
        return container;
    }

    /**
     * 把召唤出来的容器加进<b>召唤者自己那一侧</b>的阵营列表。
     * <p>
     * 这是必须的：{@link Fight#getOpponentList(LivingThing)} 的判定是
     * "在敌方列表里 → 返回我方；否则 → 返回敌方"。一个不在任何列表里的实体，
     * 会被当成"非敌方"，于是它把<b>敌方</b>当对手 —— 对玩家侧的盗火行者来说，
     * 那就是"自家容器打自家队伍"。所以召唤物必须和召唤者同侧。
     *
     * @param fight     当前战斗
     * @param container 召唤出来的容器
     */
    private void addSummonToOwnSide(Fight fight, BrokenContainer container) {
        if (fight.getFighterList().contains(this)) {
            fight.addFighter(container);
        } else {
            fight.addEnemy(container);
        }
    }

    /**
     * 给容器挂上【镣锁】状态（二阶段的【迷失的共祭】用）。
     *
     * @param container 容器
     */
    public void lockContainer(BrokenContainer container) {
        if (container == null) {
            return;
        }
        LockedRite locked = new LockedRite(LockedRite.DEFAULT_LAST_TIME);
        locked.setOrigin(getUUID());
        container.addEffect(locked);
        System.out.println("【" + container.getKind().displayName() + "】进入【镣锁】状态");
    }

    /**
     * 召唤若干只处于【镣锁】状态的残破容器（官方「迷失的共祭」）。
     * <p>
     * 官方：施放【沉默的悲叹】时，消耗生命值召唤处于【镣锁】状态的【残破容器】。
     *
     * @param fight 当前战斗
     * @param count 想召唤的数量（受场上容器上限限制）
     * @return 实际召唤出来的数量
     */
    public int summonLockedContainers(Fight fight, int count) {
        int summoned = 0;
        for (int i = 0; i < count; i++) {
            BrokenContainer container = summonContainer(fight, BrokenContainer.Kind.BROKEN);
            if (container == null) {
                break;
            }
            lockContainer(container);
            summoned++;
        }
        if (summoned > 0) {
            System.out.println("【迷失的共祭】" + getName() + "召唤了 " + summoned + " 只处于【镣锁】的容器");
        }
        return summoned;
    }

    /**
     * 按概率决定这次召唤出来的是【残破容器】还是【完整容器】。
     * <p>
     * 官方里完整容器是末日幻影独有的召唤物；本项目按用户要求把两个版本融合，
     * 所以让它按概率混在召唤里出现 —— 玩家看到它就该优先打（击杀有奖励），
     * 但放着不管会被 BOSS 吸收、额外充能。
     *
     * @param fight 当前战斗
     * @return 召唤出的容器（可能是完整容器）
     */
    public BrokenContainer summonRandomContainer(Fight fight) {
        BrokenContainer.Kind kind = Math.random() < this.completeContainerChance
                ? BrokenContainer.Kind.COMPLETE
                : BrokenContainer.Kind.BROKEN;
        return summonContainer(fight, kind);
    }

    /**
     * 判断容器是否处于【镣锁】状态。
     *
     * @param container 容器
     * @return 处于镣锁则 {@code true}
     */
    public boolean isLocked(BrokenContainer container) {
        return LockedRite.has(container);
    }

    /**
     * 尝试用【灾难之力】+ 生命值"重新召唤"一只被致命攻击的镣锁容器（官方「为我设奠」）。
     * <p>
     * 条件与代价（官方）：灾难之力层数不为 0 时，消耗 <b>1 层灾难之力</b>
     * 与<b>一定比例的生命值</b>，重新召唤一只处于【镣锁】状态的容器。
     *
     * @param fight 当前战斗
     * @return 成功则返回新容器；条件不满足返回 {@code null}
     */
    public BrokenContainer tryReviveLocked(Fight fight) {
        if (disasterPower <= 0) {
            return null;
        }
        long cost = (long) (getHpMax() * this.summonHpCostRate);
        if (getHp() <= cost) {
            // 血量不够，复活不起来（避免把自己耗死）
            System.out.println("【" + getName() + "】血量不足，无法重新召唤【镣锁】容器");
            return null;
        }
        consumeDisasterPower(1);
        BrokenContainer revived = summonContainer(fight, BrokenContainer.Kind.BROKEN);
        if (revived == null) {
            return null;
        }
        lockContainer(revived);
        System.out.println("【" + getName() + "】消耗1层【灾难之力】与生命值，重新召唤了处于【镣锁】的容器");
        return revived;
    }

    /**
     * 给击杀【完整容器】的玩家发奖励：<b>额外回合 + 增伤 buff</b>（官方末日幻影 3.4 的机制）。
     * <p>
     * 击杀者如果已经倒下则不发（见方法内注释）。
     * 注意额外回合是排在<b>当前时间点</b>上的，所以本方法必须在容器离场的那一刻就被调用
     * （回合循环里"移出阵营列表"之后立刻结算，见 {@code FightTurnPastListener}），
     * 拖到回合末尾就会让这一整个回合先跑完、奖励迟到。
     *
     * @param killer 击杀者
     */
    public void grantContainerReward(LivingThing killer) {
        if (killer == null) {
            return;
        }
        // 击杀者已经倒下（例如击杀容器的同一回合里被 BOSS 反杀）：奖励发了也用不上 ——
        // 增伤会随战斗结束立刻到期，排出来的额外回合会被 TurnManager#removeTheDeath 摘掉。
        // 与其在日志里报一条"获得了一个额外回合"的假消息，不如直接不发。
        if (!killer.isAlive()) {
            System.out.println("【完整容器】被击碎，但击杀者已经倒下，奖励未发放（" + killer.getNameWithSide() + "）");
            return;
        }
        killer.addEffect(new cn.gfhnv.game.officialStuff.customEffect.universalEffects.DamageEnhanceEffect(
                CONTAINER_REWARD_EFFECT_ID, 1, CONTAINER_REWARD_LAST_TIME, 0.0, CONTAINER_REWARD_ENHANCE)
                .setName(CONTAINER_REWARD_NAME)
                .setOrigin(getUUID()));
        grantExtraTurn(killer);
    }

    /**
     * 让 {@code beneficiary} 立刻多行动一次。
     * <p>
     * 做法是给它在<b>当前时间点</b>排一个新回合（{@code needTime = 0}）：
     * {@code TurnManager.sort()} 之后它就会排在队首附近，于是"额外回合"自然发生
     * （回合循环轮到谁就一定会调一次 {@code act()}，所以这一条就是实打实多一次行动）。
     * <b>不要</b>通过改 {@code ActionSignal} 实现（那会污染回合推进的语义）。
     * <p>
     * <b>必须标记 {@code setExtra(true)}</b>（与白厄的额外回合同一个约定）：
     * {@code EffectEventListener} 对额外回合只调 {@code comeIntoEffect}、
     * <b>不减 {@code lastTime}</b> —— 不然刚发出去的【破容器之赏】（3 回合）
     * 会在"被奖励的那次行动"里就少掉 1 回合，目标身上的【侵蚀】【宿醉】也会跟着多跳一次。
     * 顺带这个标记也让"觉醒中的白厄拿到奖励"时能触发他的额外回合反击
     * （{@code UltimateAttack} 里的判定就是看 {@code isExtra()}）。
     * <p>
     * <b>还要带 {@link TurnEntry#PRIORITY_EXTRA}</b>（2026-10-03 用户要求"这个奖励的额外回合
     * 应该直接行动"）：光把它排在 {@code presentTime} 上还不够 —— 时间打平时
     * {@code sort()} 原先按<b>速度</b>排，"立即行动"会被场上更快的单位抢走。
     * 带上优先级之后，同时间的一定是它先执行。
     *
     * @param beneficiary 获得额外回合的生物
     */
    public void grantExtraTurn(LivingThing beneficiary) {
        // TurnManager.getPresentTime() 已经保证不为 null（未初始化时返回 ZERO）
        TurnEntry extraTurn = new TurnEntry(beneficiary, BigDecimal.ZERO, TurnManager.getPresentTime())
                .setExtra(true)
                .setPriority(TurnEntry.PRIORITY_EXTRA)
                .setTag(EXTRA_TURN_TAG);
        TurnManager.getTurns().add(extraTurn);
        TurnManager.sort();
        System.out.println(ConsoleColor.yellow("【" + beneficiary.getName() + "】获得了一个额外回合【"
                + EXTRA_TURN_TAG + "】"));
    }

    /**
     * 记录某个招式这次召唤出来的容器（对应官方"曾被该攻击召唤"）。
     *
     * @param skill     召唤它的招式
     * @param container 本次召唤出来的容器
     */
    public void rememberSummon(Skill skill, BrokenContainer container) {
        if (skill == null || container == null) {
            return;
        }
        if (skill instanceof CloudOfDeath) {
            cloudOfDeathSummons.add(container);
        } else if (skill instanceof FateDrawsNear) {
            fateDrawsNearSummons.add(container);
        }
    }

    /**
     * 回收某个招式"上次召唤的"容器：把它们从时间轴上摘掉（离场）。
     * <p>
     * 官方「分离的哀痛」：再次施放该招式时，曾被它召唤且处于【共祭】的容器一同攻击，随后被吸收。
     * 召唤批次在这里清空，所以下一批容器要等下一次施放才入账。
     *
     * @param skill 正在施放的招式
     * @return 该招式上次召唤、并且此刻还活着的容器（按召唤顺序）
     */
    public List<BrokenContainer> drainSummonedContainers(Skill skill) {
        if (skill == null) {
            return new ArrayList<>();
        }
        List<BrokenContainer> batch;
        if (skill instanceof CloudOfDeath) {
            batch = new ArrayList<>(cloudOfDeathSummons);
            cloudOfDeathSummons.clear();
        } else if (skill instanceof FateDrawsNear) {
            batch = new ArrayList<>(fateDrawsNearSummons);
            fateDrawsNearSummons.clear();
        } else {
            return new ArrayList<>();
        }
        batch.removeIf(container -> !container.isAlive());
        return batch;
    }

    /**
     * 把【共祭】状态的容器交给「幽冥的悼念」一起出手。
     *
     * @param fight 当前战斗
     * @return 参与本次一同攻击的容器（按召唤顺序）
     * <p>
     * 判定依据是容器身上有没有 {@link SacrificeRite} 效果 ——
     * 也就是说玩家若用净化类手段把那层效果去掉，这一步就会落空。
     */
    public List<BrokenContainer> getSacrificedContainers(Fight fight) {
        List<BrokenContainer> sacrificed = new ArrayList<>();
        for (BrokenContainer container : getAliveContainers(fight)) {
            if (BrokenContainer.hasSacrificeRite(container)) {
                sacrificed.add(container);
            }
        }
        return sacrificed;
    }

    /**
     * 与【共祭】容器一同攻击，随后把它们吸收掉。
     * <p>
     * 官方「分离的哀痛 / 幽冥的悼念」的合并落地：再次施放【亡死的黑云】或【将尽的命数】时，
     * 处于【共祭】的残破容器<b>一同发起攻击</b>，随后<b>被自身吸收</b>
     * （按【苦痛缠绕】回血 + 获得【灾难之力】）。
     * <p>
     * 那一轮谁打哪儿：<b>BOSS 先用 {@link #pickJointTargets(Fight)} 选一次共同目标</b>，
     * 自己照着打一遍，再让每只容器用<b>自己的控制器</b>出手（技能表里是
     * 【共祭 · 亡死的黑云】/【共祭 · 将尽的命数】），但目标换成同一批
     * （{@code BrokenContainer#setJointTargets}）。
     *
     * @param fight 当前战斗
     * @return 参与并已被吸收的容器数量
     */
    public int absorbSacrificedContainers(Fight fight) {
        return absorbSacrificedContainers(fight, getSacrificedContainers(fight));
    }

    /**
     * 选出这一轮共祭的<b>共同目标</b>：对面最多 {@value #JOINT_ATTACK_TARGETS} 个存活单位。
     * <p>
     * 以前容器是各自 {@code controller.act()} 出手、目标各自随机挑的 ——
     * 同一轮共祭里几只容器各打各的（用户 2026-09 实测指出"应当攻击相同目标"）。
     *
     * @param fight 当前战斗
     * @return 共同目标列表（对面不足 3 个就全打；没有对手时为空列表）
     */
    public List<LivingThing> pickJointTargets(Fight fight) {
        List<LivingThing> targets = new ArrayList<>();
        if (fight == null) {
            return targets;
        }
        for (LivingThing each : fight.getOpponentList(this)) {
            if (each != null && each.isAlive() && targets.size() < JOINT_ATTACK_TARGETS) {
                targets.add(each);
            }
        }
        return targets;
    }

    /**
     * BOSS 本体也参与这一轮共祭攻击（官方："与容器<b>一同</b>施放"）。
     * <p>
     * 以前只让容器出手，日志里完全看不到 BOSS 的攻击（用户 2026-09 指出）。
     * 这里借【亡死的黑云】的倍率打一遍，但<b>不</b>调用它的 {@code comeToEffect} ——
     * 那一招会顺手消耗生命召唤容器，而紧接着的步骤就是把这些容器全部吸收掉。
     *
     * @param targets 共同目标
     */
    private void bossJoinsJointAttack(List<LivingThing> targets) {
        if (targets == null || targets.isEmpty()) {
            return;
        }
        Skill joint = new CloudOfDeath();
        for (LivingThing target : targets) {
            if (target == null || !target.isAlive()) {
                continue;
            }
            this.makeDamage(target, joint, DamageType.SpecialSkill);
        }
    }

    /**
     * 与指定的那批容器一同攻击，随后把它们吸收掉。
     * <p>
     * <b>为什么要限定批次</b>：官方「分离的哀痛」限定的是"<b>曾被该攻击召唤</b>且处于【共祭】"，
     * 所以亡死的黑云只回收自己召唤的那批，不会把将尽的命数召唤的也一起吃掉。
     * 不限定的话容器只活一轮，协同攻击与"共祭"的存在意义都会消失。
     *
     * @param fight      当前战斗
     * @param sacrificed 本次要结算的容器
     * @return 参与并已被吸收的容器数量
     */
    public int absorbSacrificedContainers(Fight fight, List<BrokenContainer> sacrificed) {
        if (sacrificed == null || sacrificed.isEmpty()) {
            return 0;
        }
        // ① 这一轮打哪儿，由 BOSS 定一次；容器跟着打同一批（官方是"与容器一同施放"）
        List<LivingThing> jointTargets = pickJointTargets(fight);
        // ② BOSS 自己也出手 —— 以前只让容器打，日志里看不到 BOSS 的攻击（用户 2026-09 指出）
        bossJoinsJointAttack(jointTargets);
        // ③ 每只共祭容器用<b>自己的控制器</b>出手（技能表里是【共祭 · 亡死的黑云】/【将尽的命数】），
        //    但目标换成上面那批共同目标
        for (BrokenContainer container : sacrificed) {
            if (!container.isAlive()) {
                continue;
            }
            String who = "【" + container.getKind().displayName() + "】与" + getName() + "一同发起攻击";
            if (!hasLivingTarget(jointTargets)) {
                // 共同目标是顺序结算的：BOSS 本体与前面的容器可能已经把它们全打倒了，
                // 这一击就落空了（官方也是"跟着同一击一起打"，目标先倒就是没得打）。
                // 显式说明一句，免得日志上只剩标题、看起来像卡住。
                System.out.println(who + "（共同目标已经倒下，这一击落空）");
                continue;
            }
            System.out.println(who);
            container.setJointTargets(jointTargets);
            try {
                container.getController().act(fight);
            } finally {
                container.setJointTargets(null);
            }
        }
        int absorbed = 0;
        for (BrokenContainer container : sacrificed) {
            if (container.isAlive()) {
                absorbContainer(container);
                absorbed++;
            }
        }
        return absorbed;
    }

    /**
     * 吸收一个容器：按【苦痛缠绕】回血、获得【灾难之力】，并让容器离场。
     * <p>
     * 回血上限是<b>这只容器当初的召唤代价</b>（{@link BrokenContainer#getPainCost()}），
     * 不是它的最大生命 —— 官方把"消耗的生命值"记账为【苦痛缠绕】，吸收时还的就是这笔账。
     * <p>
     * 吸收而死的容器<b>不掉</b>【永别的决绝】层数（那是"被消灭"才掉的），
     * 所以先 {@link BrokenContainer#markAbsorbed() 标记成因}再让它死。
     *
     * @param container 被吸收的容器
     * @return 实际回复的生命值
     */
    public long absorbContainer(BrokenContainer container) {
        if (container == null) {
            return 0;
        }
        // 从【苦痛缠绕】账本里取用：账本里有多少、这只容器当初花了多少，取小者
        long healPool = PainEntanglement.take(this, Math.max(0, container.getPainCost()));
        long heal = Math.max(0, Math.min(healPool, getHpMax() - getHp()));
        if (heal > 0) {
            this.heal(heal);
        }
        container.markAbsorbed();
        container.absorb();
        // 吸收完毕就把【共祭】标记摘掉（它是效果，也可以被玩家净化，所以这里不能假设它还在）
        BrokenContainer.removeSacrificeRite(container);
        // 完整容器被吸收时额外充能（官方末日幻影：完整容器"为 BOSS 充能"）
        if (container.isComplete()) {
            addDisasterPower(2);
            System.out.println("【完整容器】被吸收，" + getName() + "额外获得【灾难之力】");
        }
        addDisasterPower(1);
        System.out.println(ConsoleColor.green(getName() + "吸收了【残破容器】，回复" + heal + "点生命并获得1层【灾难之力】"));
        return heal;
    }

    /**
     * 容器死亡回调（由 {@link BrokenContainer#whenLeaveFight(Fight)} 调用）。
     * <p>
     * 官方「永别的决绝」：每有 1 个残破容器<b>被消灭</b>后失去 1 层减伤。
     * 被吸收不算"被消灭"，所以只有 {@link BrokenContainer.DeathReason#KILLED} 才掉层。
     *
     * @param container 死掉的容器
     * @param reason    死因
     */
    public void onContainerDeath(BrokenContainer container, BrokenContainer.DeathReason reason, Fight fight) {
        if (reason != BrokenContainer.DeathReason.KILLED) {
            return;
        }
        // ① 二阶段【为我设奠】：镣锁容器被致命攻击 → 消耗 1 层灾难之力 + 生命值重新召唤
        //    注意：复活的是一只<b>新</b>容器（官方原文就是"重新召唤"），不是把这只捞回来 ——
        //    捞回来会让它带着"已离开阵营列表"的身份，变成打自己人的幽灵实体（踩过）。
        if (isPhaseTwo() && isLocked(container)) {
            BrokenContainer revived = tryReviveLocked(fight);
            if (revived != null) {
                // 重新召唤成功：这一只的死亡不再结算减伤层数（官方也没写掉层）
                return;
            }
        }
        // ② 完整容器被击杀 → 击杀者拿奖励（额外回合 + 增伤）
        if (container.isComplete()) {
            LivingThing killer = container.getLastAttacker();
            if (killer != null) {
                grantContainerReward(killer);
            } else {
                System.out.println("【完整容器】被击碎，但没记到击杀者，奖励未发放");
            }
        }
        // ③ 【永别的决绝】：每有 1 个残破容器被消灭后失去 1 层
        if (damageReductionLayers > 0) {
            damageReductionLayers--;
            System.out.println("【" + container.getKind().displayName() + "】被消灭，" + getName()
                    + "失去1层【永别的决绝】（剩余 " + damageReductionLayers + " 层）");
        }
        applyDamageReductionLayers();
    }

    /* ------------------------------------------------------------------
     * 永别的决绝（减伤层数）
     * ------------------------------------------------------------------ */

    /**
     * @return 当前【永别的决绝】减伤层数
     */
    public int getDamageReductionLayers() {
        return damageReductionLayers;
    }

    /**
     * 把层数换算成减伤并<b>重设</b>到减伤列表上（同一来源键，幂等，不会累加）。
     */
    private void applyDamageReductionLayers() {
        if (damageReductionLayers <= 0) {
            this.removeDamageReduction(DAMAGE_REDUCTION_KEY);
            return;
        }
        this.addDamageReduction(DAMAGE_REDUCTION_KEY, damageReductionLayers * this.damageReductionPerLayer);
    }

    /* ------------------------------------------------------------------
     * 二阶段（高额免伤 + 换轮转）
     * ------------------------------------------------------------------ */

    /**
     * @return 是否已进入二阶段
     */
    public boolean isPhaseTwo() {
        return phaseTwo;
    }

    /**
     * @return 是否处在【沉默的悲叹】的蓄力中
     */
    public boolean isCharging() {
        return charging;
    }

    /**
     * 设置蓄力状态（由 {@code SilentLament} / {@code MournNotAbandon} 调用）。
     *
     * @param charging 是否蓄力中
     */
    public void setCharging(boolean charging) {
        this.charging = charging;
    }

    /**
     * 每回合检查一次，<b>兜底</b>切阶段。
     * <p>
     * 正常路径是伤害修正器（{@link #phaseTwoDeathWard()}）在挨打那一刻就切换；
     * 这里只兜"绕过伤害结算"的扣血（{@code /kill}、直接 {@code setHp(0)} 之类），
     * 保证"一定能进二阶段"。
     * <p>
     * 注意它<b>每个回合对全场所有实体</b>都会调用，不是只给自己 —— 这里只读自己的血量，所以没问题。
     */
    @Override
    public void updateSelf() {
        super.updateSelf();
        if (!phaseTwo && !phaseTwoPending && getHpMax() > 0 && getHp() <= 0) {
            schedulePhaseTwoTransition();
        }
    }

    /**
     * 把"转阶段"排进<b>当前回合</b>的行动队列；没有当前回合（自测 / 战斗已结束）时当场做。
     * <p>
     * 必须排在队列里、而不是在伤害修正器里直接回血：玩家看到的消息顺序是
     * <b>「攻击行（HP 0）→ 挡下致命一击、进入二阶段 → 生命回满」</b>，
     * 在播报之前继续结算的伤害都只是打在 0 血上的"鞭尸"。
     * <p>
     * 优先级 {@link #PHASE_TRANSITION_PRIORITY} 高于星魂 2 的额外行动（800），
     * 所以白厄的额外行动一定看到的是"已经回满、已经拿到免伤"的 BOSS。
     */
    private boolean schedulePhaseTwoTransition() {
        if (phaseTwo || phaseTwoPending) {
            return false;
        }
        phaseTwoPending = true;
        TurnEntry presentTurn = FightTurnPastListener.getPresentTurn();
        if (presentTurn == null) {
            // 自测 / 战斗已结束：没有回合可排，只能当场收口
            enterPhaseTwo();
            return true;
        }
        presentTurn.addAct(this, (fight, user) -> enterPhaseTwo(), PHASE_TRANSITION_PRIORITY);
        return false;
    }

    /**
     * 正式进入二阶段：<b>先播报，再回满血</b> + 获得【高额免伤】，并把轮转换成二阶段表。
     * <p>
     * 由 {@link #schedulePhaseTwoTransition()} 排进队列后调用；重复调用安全。
     * 播报必须在 {@code setHp} 之前：玩家要看到的顺序是"挡下致命一击"，
     * 然后才是"生命回满"。在播报之前承受的伤害都只打在 0 血上（鞭尸）。
     * <p>
     * 官方阶段二是"躲到后排 + 召唤分身顶在前面"；本项目按用户决定改成<b>高额免伤</b>。
     */
    private void enterPhaseTwo() {
        if (phaseTwo) {
            phaseTwoPending = false;
            return;
        }
        String phaseLine = ConsoleColor.yellow("【" + getName()
                + "】挡下致命一击，进入二阶段：生命回满，并获得高额免伤（"
                + Math.round(this.phaseTwoDamageReduction * 100) + "%）");
        System.out.println(phaseLine);
        phaseTwoPending = false;
        this.phaseTwo = true;
        this.setHp(getHpMax());
        this.addDamageReduction(PHASE_TWO_REDUCTION_KEY, this.phaseTwoDamageReduction);
        if (getController() instanceof FixOrderController controller) {
            controller.setRotationByName(rotationNames());
        }
    }

    /**
     * 二阶段的「切阶段保护」修正器：把<b>第一发致命伤害</b>照常结算成 0 血，
     * 然后排一条高优先级行动，等这一轮动作结算完再播报 + 回满。
     * <p>
     * 返回 {@code newHp} 而不是回满后的血量：攻击行会显示 {@code HP 0/80000}，
     * 播报之后才回满 —— 播报之前同一行动里继续结算的伤害都只是"鞭尸"。
     * 没有当前回合（自测 / 战斗已结束）时当场切阶段，这时返回回满后的血量，
     * 保持"这一击被整个吃掉"的旧语义。
     * 触发后 {@code phaseTwoPending} 置位，同一回合的多段伤害不会重复排队；
     * {@code phaseTwo} 置位后本修正器不再拦，二阶段该倒就倒。
     * <p>
     * <b>试算期间只算数、不改状态</b>（{@link LivingThing#isAnticipating()}）：
     * 返回 1 表示"这一下最多把它打到 1 血"，不会把这次保护花掉。
     *
     * @return 伤害修正器
     */
    private IModifyDamage phaseTwoDeathWard() {
        return new IModifyDamage() {
            @Override
            public long damageModify(long newHp, DamageEvent da) {
                // 认对象一律取事件里的挨打者，不要捕获构造它的那个实例：
                // 副本的阶段状态可能和原体不同步（与 Phainon#soulscorchDeathWard 同一个理由）
                if (da.getAttackedEntity() instanceof FlameReaver reaver
                        && !reaver.isPhaseTwo()
                        && newHp <= 0) {
                    if (reaver.isAnticipating()) {
                        return 1;
                    }
                    reaver.schedulePhaseTwoTransition();
                    // 有当前回合：这一击照常把血打到 0，回满由队列里的转阶段行动做（播报之后）；
                    // 没有当前回合：schedule 已经当场回满，把回满后的血量交回去（这一击被吃掉）。
                    return reaver.isPhaseTwo() ? reaver.getHp() : newHp;
                }
                return newHp;
            }
        };
    }

    /**
     * 把二阶段的减伤摘掉（战斗结束时复位）。
     */
    private void clearPhaseTwo() {
        this.phaseTwo = false;
        this.phaseTwoPending = false;
        this.charging = false;
        this.removeDamageReduction(PHASE_TWO_REDUCTION_KEY);
    }

    /**
     * 把自己<b>下一个</b>回合延后。
     * <p>
     * 官方「沉默的悲叹」：进入蓄力状态时"自身的下次行动也会延后 100%"。
     * <p>
     * 分两种情况：
     * <ol>
     *     <li><b>时间轴上已经有自己的回合</b>（例如别人提前替它排好了）：直接把这个条目延后；</li>
     *     <li><b>时间轴上没有</b>—— 正常就是"正在自己的回合里"：回合循环要到本回合跑完
     *     （{@code lastExecuteList} 之后）才给行动者排下一个条目，所以那一刻
     *     {@link TurnManager#getNextTurnOf} <b>必为 {@code null}</b>（这就是早期版本
     *     打印"延后未生效"、延后形同虚设的原因）。这里改成自己排下个回合，
     *     间隔取 {@code 时间轴基值/速度 × (1 + percent)}（基值见 GameRules 的 formula.turnTimeBase），
     *     并把<b>本回合</b>的信号设成 {@link ActionSignal#SKIP_WITHOUT_NEW_TURN} ——
     *     否则回合循环会再排一条正常间隔的，等于没延后（那样只会拿到两个回合）。</li>
     * </ol>
     *
     * @param percent 延后比例（{@code 1.0} = 延后 100%）
     */
    public void delayNextOwnTurn(BigDecimal percent) {
        String suffix = Math.round(percent.doubleValue() * 100) + "%";
        TurnEntry next = TurnManager.getNextTurnOf(this);
        if (next != null) {
            TurnManager.delayByPercent(percent, next);
            TurnManager.sort();
            System.out.println("【" + getName() + "】的下次行动延后 " + suffix);
            return;
        }
        TurnEntry present = FightTurnPastListener.getPresentTurn();
        if (present == null) {
            // 既没有待执行的回合、也不在任何回合里（理论上到不了这里），只能放弃延后
            System.out.println("【" + getName() + "】时间轴上没有待执行的回合，延后未生效");
            return;
        }
        BigDecimal needTime = TurnManager.needTimeOf(this)
                .multiply(BigDecimal.ONE.add(percent));
        TurnManager.getTurns().add(new TurnEntry(this, needTime, TurnManager.getPresentTime()));
        TurnManager.sort();
        present.setActionSignal(ActionSignal.SKIP_WITHOUT_NEW_TURN);
        System.out.println("【" + getName() + "】的下次行动延后 " + suffix);
    }
}
