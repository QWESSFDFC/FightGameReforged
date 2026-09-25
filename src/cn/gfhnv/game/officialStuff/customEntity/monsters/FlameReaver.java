package cn.gfhnv.game.officialStuff.customEntity.monsters;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entityController.FixOrderController;
import cn.gfhnv.game.officialStuff.customEffect.flameReaverEffects.ContainerReward;
import cn.gfhnv.game.officialStuff.customEffect.flameReaverEffects.LockedRite;
import cn.gfhnv.game.officialStuff.customEffect.flameReaverEffects.PainEntanglement;
import cn.gfhnv.game.officialStuff.customEffect.flameReaverEffects.SacrificeRite;
import cn.gfhnv.game.officialStuff.customEntity.summons.BrokenContainer;
import cn.gfhnv.game.officialStuff.customSkill.flameReaverSkills.*;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.fight.TurnEntry;
import cn.gfhnv.game.system.fight.TurnManager;

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
 * 未实现（第二步）：完整容器、二阶段、【沉默的悲叹】、【镣锁】与【为我设奠】复活。
 *
 * @author AI（DeepSeek）生成
 */
public class FlameReaver extends LivingThing {

    /**
     * 减伤来源键：【永别的决绝】的层数减伤。用固定键，方便"层数变化时重设"而不是累加。
     */
    public static final Object DAMAGE_REDUCTION_KEY = new Object();

    /**
     * 减伤来源键：二阶段的【高额免伤】。
     * <p>
     * 与 {@link #DAMAGE_REDUCTION_KEY} <b>分开</b>：两者会同时存在（层数减伤 + 阶段免伤），
     * 而"同一来源键重复添加是幂等的"，共用一个键会互相覆盖。
     */
    public static final Object PHASE_TWO_REDUCTION_KEY = new Object();

    /* ---------------- 可调数值（初版，进游戏手感不对就改这里） ---------------- */

    /**
     * 每次召唤消耗自身最大生命的比例（转成【苦痛缠绕】记账）。
     * <p>
     * 比"回收比例"小是故意的：吸收时按容器的生命上限回血（最多
     * {@link BrokenContainer#HP_RATIO} 那么大的最大生命），所以净收益靠的是"容器活得久"，
     * 而不是"召唤得贵"。代价定得太高会让 BOSS 自己把自己耗死。
     */
    private static final double SUMMON_HP_COST_RATE = 0.03;

    /**
     * 每层【灾难之力】提供的加伤比例。
     */
    private static final double DISASTER_POWER_ATTACK_BONUS = 0.08;

    /**
     * 【永别的决绝】初始减伤层数。
     */
    private static final int DAMAGE_REDUCTION_LAYERS = 2;

    /**
     * 【永别的决绝】每层减伤比例（乘算叠加）。
     */
    private static final double DAMAGE_REDUCTION_PER_LAYER = 0.25;

    /**
     * 场上容器的数量上限（超过就不再召唤）。
     */
    private static final int CONTAINER_LIMIT = 4;

    /**
     * 每次召唤时，出现【完整容器】的概率（其余为【残破容器】）。
     * <p>
     * 完整容器是"奖励线"：击杀者拿额外回合 + 增伤；放着不管则被 BOSS 吸收并额外充能。
     * 概率不宜高 —— 它是给玩家的机会，不该变成常态。
     */
    private static final double COMPLETE_CONTAINER_CHANCE = 0.34;

    /**
     * 二阶段触发血量比例（掉到这个比例以下就切换阶段）。
     */
    private static final double PHASE_TWO_HP_THRESHOLD = 0.5;

    /**
     * 二阶段的【高额免伤】（乘算叠加到其他减伤上，所以 0.7 表示"只受 30%"）。
     * <p>
     * 官方阶段二是"躲到后排 + 召分身顶前"；本项目按用户要求改成<b>高额免伤</b>，
     * 语义上等价于"你打不到它本体"。
     */
    private static final double PHASE_TWO_DAMAGE_REDUCTION = 0.7;

    /* ---------------- 状态 ---------------- */

    private int disasterPower = 0;

    /**
     * 剩下的"共祭窗口"回合数（由 {@code TangledPathsOfMourning} 在场上没有容器时开启）。
     * <p>
     * 窗口大于 0 时，<b>新召唤出来的容器直接进入【共祭】</b> ——
     * 这样"相混的道途"不会因为"玩家清场太快"而变成空放，玩家也就永远看不到
     * 协同攻击与吸收这一整套机制。
     */
    private int sacrificeWindow = 0;

    private int damageReductionLayers = DAMAGE_REDUCTION_LAYERS;

    /**
     * 是否已进入二阶段。
     */
    private boolean phaseTwo = false;

    /**
     * 是否处在【沉默的悲叹】的蓄力中（下一次行动要放【莫因舍弃而哭泣】）。
     */
    private boolean charging = false;

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

    public FlameReaver(FlameReaver other) {
        super(other);
        this.disasterPower = other.disasterPower;
        this.damageReductionLayers = other.damageReductionLayers;
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
     * 基础生命上限（不靠等级成长系数去凑）：150 级时成长公式只给到 1.8 万，太脆。
     */
    private static final long BASE_HP_MAX = 80000;

    /**
     * 构造盗火行者。
     *
     * @param l 等级
     */
    public FlameReaver(long l) {
        super("至黑之剑，盗火行者", "flameReaver", 0.2, 0.2, 0.2, 0.2, 0.2,
                140, l, "boss", 120, 60, 25, ElementSort.FIRE);
        this.setDescription("伴随黑潮而来、狩猎泰坦火种的无名剑士，无人知晓其真身。"
                + "身负不可思议的力量，剑技也近乎无懈可击。");
        // 血量直接给上限：召唤容器是按"最大生命的百分比"扣血的，底子太薄会自己把自己耗死
        this.setHpMax(BASE_HP_MAX);
        this.setHp(BASE_HP_MAX);
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
        this.damageReductionLayers = DAMAGE_REDUCTION_LAYERS;
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

    /* ------------------------------------------------------------------
     * 灾难之力
     * ------------------------------------------------------------------ */

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
        this.setEnhance(disasterPower * DISASTER_POWER_ATTACK_BONUS);
    }

    /* ------------------------------------------------------------------
     * 苦痛缠绕
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

    /* ------------------------------------------------------------------
     * 召唤与吸收
     * ------------------------------------------------------------------ */

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
     * @return 召唤出的容器；场上已达上限或战斗为空时返回 {@code null}
     */
    public BrokenContainer summonContainer(Fight fight, BrokenContainer.Kind kind) {
        if (fight == null) {
            return null;
        }
        List<BrokenContainer> alive = getAliveContainers(fight);
        if (alive.size() >= CONTAINER_LIMIT) {
            System.out.println(getName() + "的容器已达上限（" + CONTAINER_LIMIT + "），不再召唤");
            return null;
        }
        long cost = (long) (getHpMax() * SUMMON_HP_COST_RATE);
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
        fight.addEnemy(container);
        // addEnemy 会把它加入 allEntities 并排好回合；但「战斗开始」那个钩子只在开局
        // 遍历 allEntities 时调用一次（FightStartEventListener），**中途召唤出来的实体收不到**。
        // 所以这里显式补一次入场初始化：接上战斗上下文 + 调 whenFightStart。
        container.setParticipateFight(fight);
        container.whenFightStart(fight);
        // 处在"共祭窗口"内时，新容器直接进入共祭（让相混的道途不空放）
        if (sacrificeWindow > 0) {
            container.enterSacrifice(SacrificeRite.DEFAULT_LAST_TIME, getUUID());
            consumeSacrificeWindow();
        }
        System.out.println(getName() + "消耗" + realCost + "点生命召唤了【" + kind.displayName()
                + "】（苦痛缠绕 " + getPainTangled() + "）");
        return container;
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
        BrokenContainer.Kind kind = Math.random() < COMPLETE_CONTAINER_CHANCE
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
        long cost = (long) (getHpMax() * SUMMON_HP_COST_RATE);
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
            System.out.println("【完整容器】被击碎，但击杀者已经倒下，奖励未发放（" + killer.getName() + "）");
            return;
        }
        killer.addEffect(new ContainerReward(ContainerReward.DEFAULT_ENHANCE,
                ContainerReward.DEFAULT_LAST_TIME).setOrigin(getUUID()));
        grantExtraTurn(killer);
    }

    /**
     * 让 {@code beneficiary} 立刻多行动一次。
     * <p>
     * 做法是给它在<b>当前时间点</b>排一个新回合（{@code needTime = 0}）：
     * {@code TurnManager.sort()} 之后它就会排在队首附近，于是"额外回合"自然发生
     * （回合循环轮到谁就一定会调一次 {@code act()}，所以这一条就是实打实多一次行动）。
     * <b>不要</b>通过改 {@code ActionSignal} 实现（那会污染回合推进的语义）。
     *
     * @param beneficiary 获得额外回合的生物
     */
    public void grantExtraTurn(LivingThing beneficiary) {
        // TurnManager.getPresentTime() 已经保证不为 null（未初始化时返回 ZERO）
        TurnManager.getTurns().add(new TurnEntry(beneficiary, BigDecimal.ZERO, TurnManager.getPresentTime()));
        TurnManager.sort();
        System.out.println("【" + beneficiary.getName() + "】获得了一个额外回合");
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
     * 容器"一同攻击"是让它们用<b>自己的控制器</b>出手：它们的技能表里就是
     * 【共祭 · 亡死的黑云】与【共祭 · 将尽的命数】。
     *
     * @param fight 当前战斗
     * @return 参与并已被吸收的容器数量
     */
    public int absorbSacrificedContainers(Fight fight) {
        return absorbSacrificedContainers(fight, getSacrificedContainers(fight));
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
        for (BrokenContainer container : sacrificed) {
            if (!container.isAlive()) {
                continue;
            }
            System.out.println("【残破容器】与" + getName() + "一同发起攻击");
            container.getController().act(fight);
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
            this.setHp(getHp() + heal);
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
        System.out.println(getName() + "吸收了【残破容器】，回复" + heal + "点生命并获得1层【灾难之力】");
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
        this.addDamageReduction(DAMAGE_REDUCTION_KEY, damageReductionLayers * DAMAGE_REDUCTION_PER_LAYER);
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
     * 每回合检查一次是否该进入二阶段。
     * <p>
     * <b>为什么用 {@code updateSelf} 而不是监听受伤事件</b>：本项目没有"血量变化"的专门事件
     * （{@code HpLossEvent} 只是通知，扣血路径分散），而这个钩子每回合都会被调用，
     * 判一次血量足够及时，且不会在伤害结算中途改变减伤状态。
     * <p>
     * 注意它<b>每个回合对全场所有实体</b>都会调用，不是只给自己 —— 这里只读自己的血量，所以没问题。
     */
    @Override
    public void updateSelf() {
        super.updateSelf();
        if (!phaseTwo && getHpMax() > 0 && (double) getHp() / getHpMax() <= PHASE_TWO_HP_THRESHOLD) {
            enterPhaseTwo();
        }
    }

    /**
     * 进入二阶段：获得【高额免伤】并把轮转换成二阶段表（多出蓄力与大招）。
     * <p>
     * 官方阶段二是"躲到后排 + 召唤分身顶在前面"；本项目按用户决定改成<b>高额免伤</b>。
     */
    private void enterPhaseTwo() {
        this.phaseTwo = true;
        this.addDamageReduction(PHASE_TWO_REDUCTION_KEY, PHASE_TWO_DAMAGE_REDUCTION);
        System.out.println("【" + getName() + "】进入二阶段：获得高额免伤（"
                + Math.round(PHASE_TWO_DAMAGE_REDUCTION * 100) + "%）");
        if (getController() instanceof FixOrderController controller) {
            controller.setRotationByName(rotationNames());
        }
    }

    /**
     * 把二阶段的减伤摘掉（战斗结束时复位）。
     */
    private void clearPhaseTwo() {
        this.phaseTwo = false;
        this.charging = false;
        this.removeDamageReduction(PHASE_TWO_REDUCTION_KEY);
    }

    /**
     * 把自己<b>下一个还在时间轴上</b>的回合延后。
     * <p>
     * 官方「沉默的悲叹」：进入蓄力状态时"自身的下次行动也会延后 100%"。
     * 用框架现成的 {@link TurnManager#delayByPercent(BigDecimal, TurnEntry)} 实现
     * （它把回合条目的 {@code needTime} 乘上 {@code 1 + percent}）。
     *
     * @param percent 延后比例（{@code 1.0} = 延后 100%）
     */
    public void delayNextOwnTurn(BigDecimal percent) {
        TurnEntry next = TurnManager.getNextTurnOf(this);
        if (next == null) {
            // 时间轴上没有自己的回合（例如刚行动完还没排新的），延后无处施加 —— 不算错，跳过
            System.out.println("【" + getName() + "】时间轴上没有待执行的回合，延后未生效");
            return;
        }
        TurnManager.delayByPercent(percent, next);
        System.out.println("【" + getName() + "】的下次行动延后 " + Math.round(percent.doubleValue() * 100) + "%");
    }
}
