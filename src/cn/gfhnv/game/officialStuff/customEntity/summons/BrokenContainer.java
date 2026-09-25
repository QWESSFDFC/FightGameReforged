package cn.gfhnv.game.officialStuff.customEntity.summons;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entityController.FixOrderController;
import cn.gfhnv.game.event.DamageEvent;
import cn.gfhnv.game.officialStuff.customEffect.flameReaverEffects.SacrificeRite;
import cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver;
import cn.gfhnv.game.officialStuff.customSkill.flameReaverSkills.SacrificeCloudOfDeath;
import cn.gfhnv.game.officialStuff.customSkill.flameReaverSkills.SacrificeFateDrawsNear;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.mana.Mana;

import java.util.ArrayList;
import java.util.List;

/**
 * 【残破容器】—— 盗火行者的召唤物。
 * <p>
 * 官方机制（本项目按火属性映射）：
 * <ul>
 *     <li><b>为我祷咏</b>：抵抗控制类负面状态；被吸收时与盗火行者一同发起攻击；</li>
 *     <li><b>共祭 · 亡死的黑云</b>：对指定我方单体及其相邻目标造成少量伤害
 *     （本项目相邻目标映射为「目标数 3」）；</li>
 *     <li><b>共祭 · 将尽的命数</b>：对我方全体造成少量伤害并挂【侵蚀】；</li>
 *     <li><b>为我设奠</b>（阶段二）：处于【镣锁】时受到致命攻击会被重新召唤 —— 本次未实现。</li>
 * </ul>
 * <p>
 * 它与盗火行者之间靠一个回引联系（{@link #getOwner()}）：
 * 容器被消灭 / 被吸收时反过来通知 BOSS 掉减伤层数、记苦痛缠绕、加灾难之力。
 *
 * @author AI（DeepSeek）生成
 */
public class BrokenContainer extends LivingThing {

    /**
     * 控制类负面状态的效果 id 前缀：这些效果挂不上来（对应官方的「抵抗控制类负面状态」）。
     */
    private static final String[] CONTROL_EFFECT_IDS = {
            "frozenEffect"
    };

    /**
     * 召唤它的盗火行者。
     */
    private final FlameReaver owner;

    /**
     * 容器种类（残破 / 完整）。
     */
    private final Kind kind;

    /**
     * 是否还活着。
     * <p>
     * <b>为什么要自己记一个布尔量</b>：框架的 {@code isAlive()} 靠"血量 ≤ 0"判断，
     * 但容器的血量会被 BOSS 用来做召唤代价记账等操作，内部状态不太适合直接当判据；
     * 而且被吸收时需要"立刻判定死亡"（不能再出手），显式标记更清楚。
     */
    private boolean alive = true;

    /**
     * 最后一次攻击它的生物（用来判断"完整容器被谁击杀"）。
     */
    private LivingThing lastAttacker;

    /**
     * 是否处于【共祭】状态。
     * <p>
     * 用效果 {@link SacrificeRite} 存这个状态（而不是本类自己的布尔字段）：
     * 回合递减交给框架的 {@code EffectEventListener}，并且带上施加者（盗火行者）的 UUID。
     * <p>
     * <b>为什么不能自己数回合</b>：{@code updateSelf} 是"每回合对全场所有实体"调用的，
     * 数出来的回合数会偏快 —— 之前就是因此出现"我方回合里共祭状态就结束了"。
     */
    public static boolean hasSacrificeRite(LivingThing thing) {
        return findSacrificeRite(thing) != null;
    }

    /**
     * 取出身上的【共祭】效果。
     *
     * @param thing 生物
     * @return 效果实例；没有则返回 {@code null}
     */
    public static SacrificeRite findSacrificeRite(LivingThing thing) {
        if (thing == null || thing.getEntityEffectList() == null) {
            return null;
        }
        for (Effect effect : thing.getEntityEffectList()) {
            if (effect instanceof SacrificeRite rite) {
                return rite;
            }
        }
        return null;
    }

    /**
     * 移除身上的【共祭】效果（吸收完毕、或被净化时调用）。
     *
     * @param thing 生物
     * @return 是否真的移除了
     */
    public static boolean removeSacrificeRite(LivingThing thing) {
        if (thing == null || thing.getEntityEffectList() == null) {
            return false;
        }
        boolean removed = thing.getEntityEffectList()
                .removeIf(effect -> effect instanceof SacrificeRite);
        if (removed) {
            System.out.println("【残破容器】的共祭状态结束了");
        }
        return removed;
    }

    /**
     * @return 是否处于【共祭】状态
     */
    public boolean isSacrifice() {
        return hasSacrificeRite(this);
    }

    /**
     * 进入【共祭】状态：挂上 {@link SacrificeRite} 效果。
     * <p>
     * 同时挂两层保护：
     * <ul>
     *     <li>绕过本类"抵抗控制类负面状态"的检查（共祭是召唤者自己的标记，不该被自己抵抗）；</li>
     *     <li>用<b>召唤者的 UUID</b> 当 origin —— 查得出来这是谁上的共祭。</li>
     * </ul>
     *
     * @param turns  维持回合数
     * @param origin 施加者 UUID（盗火行者的 {@code getUUID()}）
     */
    public void enterSacrifice(int turns, String origin) {
        SacrificeRite rite = new SacrificeRite(turns);
        rite.setOrigin(origin == null ? "flameReaver" : origin);
        this.addSacrificeRite(rite);
        System.out.println("【残破容器】进入共祭状态（维持 " + rite.getLastTime() + " 回合）");
    }

    /**
     * 直接挂上共祭效果（跳过抗性检查）。
     *
     * @param rite 共祭效果
     */
    private void addSacrificeRite(SacrificeRite rite) {
        if (rite == null) {
            return;
        }
        for (int i = 0; i < getEntityEffectList().size(); i++) {
            Effect existing = getEntityEffectList().get(i);
            if (existing instanceof SacrificeRite) {
                // 已经共祭了：只刷新持续时间，别叠两份
                existing.setLastTime(Math.max(existing.getLastTime(), rite.getLastTime()));
                return;
            }
        }
        getEntityEffectList().add(rite);
        rite.initialEffect(this);
    }

    /**
     * 是否已经被 BOSS 吸收（吸收而死不算"被玩家消灭"，不掉减伤层数）。
     */
    private boolean absorbed = false;

    /**
     * 死亡是否已经通知过 BOSS（回合循环的离场钩子只会调一次，这里再兜一层防重复结算）。
     */
    private boolean deathNotified = false;

    /**
     * 召唤它时盗火行者消耗掉的生命值（官方：【苦痛缠绕】的记账单位）。
     * <p>
     * 被吸收时按这笔账回血，而不是按容器的最大生命 —— 两者不一定相等
     * （召唤代价是 BOSS 最大生命的固定比例，容器生命是另一个比例）。
     */
    private long painCost = 0;

    /**
     * 容器的生命上限占盗火行者最大生命的比例。
     * <p>
     * 12%：容器太脆的话"吸收"永远来不及发生（玩家一个多目标技能就清场），
     * 苦痛缠绕的账本也就形同虚设；太厚又会让清场变成负担。12% 大约要玩家花 2~3 次攻击。
     */
    public static final double HP_RATIO = 0.12;

    /**
     * 容器的攻击占盗火行者攻击的比例。
     */
    public static final double ATTACK_RATIO = 0.3;

    /**
     * 【完整容器】的生命占盗火行者最大生命的比例（比残破容器厚得多，所以更难清掉）。
     */
    public static final double COMPLETE_HP_RATIO = 0.25;

    /**
     * 容器种类。
     * <p>
     * 官方里这是<b>两种不同的召唤物</b>：残破容器是"泄压阀"（被消灭掉减伤层数、可被共祭回收），
     * 完整容器是"奖励线"（击杀者拿额外回合 + 增伤，没被击杀则被 BOSS 吸收额外充能）。
     * 本项目用同一个类 + 种类标记实现，避免重复一整套技能表与生命周期代码。
     */
    public enum Kind {
        /**
         * 残破容器：BOSS 自己召唤的肉盾 / 资源。
         */
        BROKEN("残破容器", "brokenContainer"),
        /**
         * 完整容器：给玩家的奖励线。
         */
        COMPLETE("完整容器", "completeContainer");

        private final String displayName;
        private final String id;

        Kind(String displayName, String id) {
            this.displayName = displayName;
            this.id = id;
        }

        /**
         * @return 显示名
         */
        public String displayName() {
            return displayName;
        }

        /**
         * @return 注册用 id
         */
        public String id() {
            return id;
        }
    }

    /**
     * 召唤构造器：按盗火行者的等级与"容器系数"生成一只【残破容器】。
     * <p>
     * 容器系数：生命 = BOSS 最大生命的 {@link #HP_RATIO}，攻击 = BOSS 攻击的 {@link #ATTACK_RATIO}。
     *
     * @param owner 召唤它的盗火行者
     */
    public BrokenContainer(FlameReaver owner) {
        this(owner, Kind.BROKEN);
    }

    /**
     * 召唤构造器（可指定种类）。
     *
     * @param owner 召唤它的盗火行者
     * @param kind  容器种类
     */
    public BrokenContainer(FlameReaver owner, Kind kind) {
        super(kind.displayName(), kind.id(), 0.2, 0.2, 0.2, 0.2, 0.2,
                120, owner.getLevel(), "summon", 4, 6, 3, ElementSort.FIRE);
        this.owner = owner;
        this.kind = kind;
        this.setDescription(kind == Kind.COMPLETE
                ? "自无穷灾难中倒映的残像，比寻常残破容器更完整、也更难击碎。"
                : "自无穷灾难中倒映的残像，皆是寄宿着破坏意志的祸端。");
        // 血量与攻击按盗火行者的数值折算（基础构造器给的是成长公式的默认值）
        double hpRatio = kind == Kind.COMPLETE ? COMPLETE_HP_RATIO : HP_RATIO;
        this.setHpMax((long) (owner.getHpMax() * hpRatio));
        this.setHp(this.getHpMax());
        this.setAttack((long) (owner.getAttack() * ATTACK_RATIO));
        List<Skill> skills = new ArrayList<>();
        // 共祭容器自己有招：与 BOSS 一同出手时由它的控制器释放（见 相混的道途 / 分离的哀痛）
        skills.add(new SacrificeCloudOfDeath());
        skills.add(new SacrificeFateDrawsNear());
        this.setController(new FixOrderController(skills, this));
    }

    /**
     * 复制构造器。
     * <p>
     * {@code owner} 没法复制（副本的召唤者可能是别人），这里保留<b>同一个</b>召唤者引用：
     * 容器是被 BOSS 现场 {@code new} 出来的，不参与注册表模板复制。
     *
     * @param other 被复制的容器
     */
    public BrokenContainer(BrokenContainer other) {
        super(other);
        this.owner = other.owner;
        this.kind = other.kind;
        this.absorbed = other.absorbed;
        this.deathNotified = other.deathNotified;
        this.painCost = other.painCost;
        this.alive = other.alive;
        this.lastAttacker = other.lastAttacker;
    }

    /**
     * @return 容器种类
     */
    public Kind getKind() {
        return kind;
    }

    /**
     * @return 是否是【完整容器】
     */
    public boolean isComplete() {
        return kind == Kind.COMPLETE;
    }

    /**
     * @return 召唤它时盗火行者消耗掉的生命值（【苦痛缠绕】的记账）
     */
    public long getPainCost() {
        return painCost;
    }

    /**
     * 设置召唤代价（由 {@link FlameReaver#summonContainer(cn.gfhnv.game.system.fight.Fight)} 写入）。
     *
     * @param painCost 消耗掉的生命值
     */
    public void setPainCost(long painCost) {
        this.painCost = Math.max(0, painCost);
    }

    @Override
    public LivingThing copy() {
        return new BrokenContainer(this);
    }

    /**
     * @return 召唤它的盗火行者
     */
    public FlameReaver getOwner() {
        return owner;
    }

    /**
     * @return 是否已经被 BOSS 吸收
     */
    public boolean isAbsorbed() {
        return absorbed;
    }

    @Override
    public void addEffect(LivingThing target, Effect effect) {
        // 为我祷咏：抵抗控制类负面状态
        if (effect != null && effect.isNegative()) {
            for (String controlId : CONTROL_EFFECT_IDS) {
                if (controlId.equals(effect.getId())) {
                    System.out.println("【残破容器】抵抗了控制类负面状态「" + controlId + "」");
                    return;
                }
            }
        }
        super.addEffect(target, effect);
    }

    @Override
    public void addEffect(Effect effect) {
        addEffect(this, effect);
    }

    /**
     * 被吸收（由 {@link FlameReaver#absorbContainer} 调用）：标记成因，
     * 让离场结算 {@link #whenLeaveFight(Fight)} 按"吸收"而不是"被玩家消灭"处理。
     */
    public void markAbsorbed() {
        this.absorbed = true;
    }

    /**
     * 立即让容器离场（被吸收时用）。
     * <p>
     * 与本类自己维护的 {@link #alive} 一起改：只把血设成 0 的话，
     * 框架的 {@code isAlive()} 要等下一次被调用才会更新标记。
     */
    public void absorb() {
        this.alive = false;
        this.setHp(0);
        this.isAlive();
    }

    /**
     * @return 是否还活着（吸收后立刻为 {@code false}）
     */
    @Override
    public boolean isAlive() {
        if (!alive) {
            return false;
        }
        return super.isAlive();
    }

    /**
     * 受伤时记下"是谁打的" —— 完整容器被击杀时要给<b>击杀者</b>发奖励。
     * <p>
     * 框架没有 {@code lastAttacker} 这种字段，而 {@code DamageEvent} 里带着攻击方，
     * 所以这里在伤害结算的入口顺手记一笔。
     *
     * @param da 伤害事件
     */
    @Override
    public void getDamage(DamageEvent da) {
        if (da != null) {
            rememberAttacker(da.getAttacker());
        }
        super.getDamage(da);
    }

    /**
     * 记录最后一次攻击它的生物（用于"完整容器被击杀 → 击杀者拿奖励"）。
     *
     * @param attacker 攻击者
     */
    public void rememberAttacker(LivingThing attacker) {
        if (attacker != null) {
            this.lastAttacker = attacker;
        }
    }

    /**
     * @return 最后一次攻击它的生物（没人打过则为 {@code null}）
     */
    public LivingThing getLastAttacker() {
        return lastAttacker;
    }

    /**
     * 死亡归宿的两种原因。
     */
    public enum DeathReason {
        /**
         * 被玩家消灭 —— 盗火行者掉 1 层【永别的决绝】减伤，残破容器被消灭还会 −1 灾难之力。
         */
        KILLED,
        /**
         * 被盗火行者吸收 —— 只走苦痛缠绕回血 + 充能，不掉减伤层数。
         */
        ABSORBED
    }

    /**
     * 容器<b>离场</b>（被打死 / 被吸收，回合循环把它移出阵营列表时调用）。
     * <p>
     * 这里只做两件事：<b>通知盗火行者</b>（掉减伤层数等），以及<b>清掉自身残留</b>。
     * <b>绝不复位血量</b> —— 它已经死了，补血等于把它复活；而它已经不在任何阵营列表里，
     * 复活后会变成"不属于任何阵营却能继续出手"的幽灵实体：{@code Fight#getOpponentList}
     * 会落到"不在 fighterList → 返回 enemiesList"的分支，<b>把自己的 BOSS 当目标打</b>
     * （实测踩过：日志里出现"残破容器攻击了至黑之剑"、BOSS 被自己的【侵蚀】烧）。
     * <p>
     * 官方原文「为我设奠」里"被重新召唤"是<b>阶段二</b>的显式机制（消耗 1 层灾难之力 + 生命值），
     * 应由 {@link FlameReaver} 主动 {@code new} 一只新的来实现，不依赖任何自动复活。
     *
     * @param fight 当前战斗
     */
    @Override
    public void whenLeaveFight(Fight fight) {
        if (!deathNotified && owner != null) {
            deathNotified = true;
            owner.onContainerDeath(this, absorbed ? DeathReason.ABSORBED : DeathReason.KILLED, fight);
        }
        setPresentTurn(null);
        for (Effect effect : getEntityEffectList()) {
            effect.whenLastTimeEnd(this);
        }
        this.setEntityEffectList(new ArrayList<>());
        if (getController() != null) {
            for (Skill skill : getController().getSkills()) {
                skill.setNowCoolDown(0);
            }
        }
        for (Mana mana : getManas()) {
            mana.setAmount(mana.getAmountMax());
        }
    }
}
