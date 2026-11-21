package cn.gfhnv.game.officialStuff.customEntity.players;

import cn.gfhnv.game.data.NoConfig;
import cn.gfhnv.game.data.NoData;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entity.HSRActor;
import cn.gfhnv.game.entity.Player;
import cn.gfhnv.game.entity.PropertyEnhanceType;
import cn.gfhnv.game.entityController.PlayerController;
import cn.gfhnv.game.entityController.UniversalController;
import cn.gfhnv.game.event.DamageEvent;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.eventListener.FightTurnPastListener;
import cn.gfhnv.game.interfaces.IModifyDamage;
import cn.gfhnv.game.officialStuff.customEvent.phainonEvents.AwakenEndEvent;
import cn.gfhnv.game.officialStuff.customEvent.phainonEvents.FightStartAndSelectEventListener;
import cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.LastAttack;
import cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills.NormalSkill;
import cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills.UltimateAttack;
import cn.gfhnv.game.officialStuff.customSkill.universalSkill.CommonAttack;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.ActionSignal;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.fight.TurnEntry;
import cn.gfhnv.game.system.fight.TurnManager;
import cn.gfhnv.game.world.World;

import java.util.ArrayList;
import java.util.List;

public class Phainon extends HSRActor {

    /**
     * 「变身」那两笔加成（攻击 +80%、生命上限 +270%）的来源键：
     * 大招 {@code UltimateAttack} 挂、退出协议 {@code AwakeEndListener} 按它摘。
     */
    public static final Object AWAKEN_ENHANCE_SOURCE = new Object();

    /**
     * 「额外能力等级」那笔攻击加成的来源键：等级变了就整条替换（幂等），整场结束由 resetTemporary 清掉。
     */
    public static final Object TIER_ENHANCE_SOURCE = new Object();

    /**
     * 「灾厄·弑魂焚诏」带来的减伤在 {@code LivingThing} 减伤列表里的来源键。
     * <p>
     * 用同一个键增删（{@code addDamageReduction/removeDamageReduction}），
     * 而不是去加减一个总减伤数字：减伤是<b>乘算</b>叠加的，
     * 手写 {@code set(getDamageAbsorbedPercent() + 0.75)} 会和别的减伤来源互相污染、重复计算。
     */
    public static final Object SOULSCORCH_DAMAGE_REDUCTION =
            new cn.gfhnv.game.entity.DamageReductionSource("白厄·灾厄");
    private static boolean isListenerRegister = false;
    private final FightStartAndSelectEventListener fightStartAndSelectEventListener = new FightStartAndSelectEventListener();
    /**
     * 变身期间排进时间轴的那些额外回合（{@link UltimateAttack#comeToEffect} 建的，
     * 最多 8 个 {@code isExtra} 条目）。
     * <p>
     * 变身被打断时要按引用把它们从时间轴上摘掉：清空技能表只能让白厄「没招可用」，
     * 清 {@link #extraTurns} 只是改个计数，两者都拦不住已经排在时间轴上的额外回合条目。
     * 手里没有引用就只能靠 {@code isExtra} 去扫，会误伤别的体系排的额外回合。
     */
    private final List<TurnEntry> awakenExtraTurns = new ArrayList<>();
    private int coreflame = 0;
    private int coreflameMax = 15;
    private int soulscorch;
    private int scourge = 0;
    private int scourgeMax = 8;
    private boolean awaken = false;
    private int extraAbilityTier = 0;
    private int appliedExtraAbilityTier = 0;
    /**
     * 变身前的普通技能表备份：退出变身时用它还原控制器。
     * <p>
     * 它不是运行时真正装备的技能（觉醒期间控制器里是觉醒三招），也不是配置项，
     * 所以 {@code /data} 不显示它，避免和菜单/控制器对不上。
     */
    @NoData
    private List<Skill> skills;
    private boolean absorbDamage = false;
    private boolean pendingLastAttack = false;
    /**
     * 「变身被打断」那一刻<b>还剩几个额外回合</b>；{@code -1} 表示这一刀不是被打断的那一刀。
     * <p>
     * 为什么需要它：中止变身的那个动作（{@link #clearAwakenExtraTurns()} +
     * {@link #finalizeAwakenByInterrupt()}）必须<b>自己完成退出协议</b> ——
     * 原有的退出协议挂在时间轴上第 8 条额外回合（{@code UltimateAttack} 的 {@code lastestOne}）的
     * 收尾队列里，而中止变身干的正是"把那些回合条目从时间轴上摘掉"，
     * 于是"中止变身"这个动作恰好取消了变身的退出协议：技能表没人清、{@code AwakenEndEvent}
     * 没人发、白厄就留在觉醒状态里（用户 2026-10 实测的「白厄无敌」就是这么来的）。
     * <p>
     * 但退出协议（{@link AwakeEndListener#end}）会把 {@link #extraTurns} 清零，而
     * {@link LastAttack} 的倍率公式 {@code 13 × (1 − 剩余回合 × 0.125)} 读的就是那个计数 ——
     * 先清零再挥刀的话，「被打断的那一刀」会从按剩余回合数缩水变成<b>满倍率 13</b>，
     * 那是改平衡。所以这里把打断瞬间的剩余回合数<b>冻结一份</b>，
     * 让 {@link LastAttack} 在"被打断"这条路上只认它
     * （见 {@link #getInterruptedRemainingExtraTurns()}）。
     * <p>
     * <b>不进 {@code /data}、也不进配置文件</b>（与 {@code LivingThing#presentTurn} 同一类东西）：
     * 它是运行期上下文，不是模板属性，dump 出来只会平白多出一个数据键。
     */
    @NoData
    @NoConfig("运行期上下文（这一刀是不是被打断的那一刀、当时还剩几个额外回合），不是模板属性，也不进 /data")
    private int interruptedRemainingExtraTurns = -1;
    private int extraTurns = 0;
    /**
     * 星魂 2 的物理抗性穿透来源键。
     */
    private static final Object E2_PENETRATION_SOURCE = new Object();
    /**
     * 本场战斗里被消灭的敌方目标数（星魂 1 的额外回合速度成长用）。
     */
    @NoData
    @NoConfig("运行期计数（本场击杀数），不是模板属性")
    private int enemyDeaths;
    /**
     * 击杀计数器（监听 `EntityDeathEvent`）。
     */
    private final EnemyDeathCounter enemyDeathCounter = new EnemyDeathCounter();

    /**
     * 星魂 6「亘古长升，蚀火残阳」：战斗开始时额外获得的【火种】点数。
     */
    public static final int E6_COREFLAME_ON_START = 6;

    /**
     * 白厄不使用 HSRActor 的普通能量 —— 他的特殊资源是【火种】。
     * <p>
     * 这一条必须覆盖：否则 {@code LivingThing#takeDamage}（受击/击杀）与
     * {@code Skill#grantEnergyAfterUse}（普攻/战技）都会按 HSRActor 规则给他攒能量，
     * 攒过 160 之后还会挂上【能量溢出】效果，在回合头打印一行占位内容。
     */
    @Override
    protected boolean usesEnergy() {
        return false;
    }

    public Phainon(Phainon phainon1) {
        super(phainon1);
        awaken = phainon1.awaken;
        extraAbilityTier = phainon1.extraAbilityTier;
        appliedExtraAbilityTier = phainon1.appliedExtraAbilityTier;
        absorbDamage = phainon1.absorbDamage;
        extraTurns = phainon1.extraTurns;
        pendingLastAttack = phainon1.pendingLastAttack;
        interruptedRemainingExtraTurns = phainon1.interruptedRemainingExtraTurns;
        skills = new ArrayList<>(this.getController().getSkills());
        coreflameMax = phainon1.coreflameMax;
        soulscorch = phainon1.soulscorch;
        scourge = phainon1.scourge;
        scourgeMax = phainon1.scourgeMax;
        coreflame = phainon1.coreflame;
        awakenExtraTurns.clear();
        awakenExtraTurns.addAll(phainon1.awakenExtraTurns);
        this.setModifyDamage(soulscorchDeathWard());
        this.setShowSpecialMes(user -> {
            if (user instanceof Phainon phainon) {
                if (phainon.awaken)
                    System.out.println("毁伤数量" + phainon.getScourge() + "|||剩余额外回合数" + phainon.getExtraTurns());
                else System.out.println("当前火种数" + phainon.getCoreflame());
            }
        });
    }

    public Phainon(long l) {
        super("白厄", "phainon", 0.7, 0, 0, 0, 0, 120, l, "player", 29, 40, 25, ElementSort.FIRE);
        // 玩家角色的基础暴击率 5% + 基础暴伤 50%（2026-11-15 第 71/73 批，用户定；
        // 只给默认用 PlayerController 的玩家实体加，怪物 / 召唤物没有暴击机制）
        this.setCriticalRate(0.05);
        this.setCriticalDamage(0.5);
        List<Skill> skillList = new ArrayList<>();
        skillList.add(new CommonAttack(0.0, 1.0, 0.0, 1));
        skillList.getFirst().setName("普通攻击:逐火救世,行则将至");
        this.setMass(60);
        this.setDescription("这是白厄.");
        coreflame = 15;
        skillList.add(new NormalSkill());
        skillList.add(new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills.UltimateAttack());
        this.getInventory().addSlot(63);
        this.setController(new PlayerController(skillList, this));
        this.skills = skillList;
        this.setModifyDamage(soulscorchDeathWard());
        this.setShowSpecialMes(user -> {
            if (user instanceof Phainon phainon) {
                if (phainon.awaken)
                    System.out.println("毁伤数量" + phainon.getScourge() + "|||剩余额外回合数" + phainon.getExtraTurns());
                else System.out.println("当前火种数" + phainon.getCoreflame());
            }
        });

    }

    /**
     * <b>自测专用装配入口</b>：把白厄的控制器从 {@link PlayerController} 换成
     * <b>不会读标准输入</b>的 {@link UniversalController}（技能表是原控制器那一份的副本）。
     * <p>
     * ⚠️ <b>只给自测 / 探针用，不要在游戏流程里调用。</b>正常玩法里白厄必须由玩家操作，
     * 这个方法一调，他就变成一个"每个回合什么都不做"的木桩
     * （技能表为空 → {@code UniversalController#act} 直接打印"没行动"并返回）。
     * <p>
     * <b>它为什么必须存在</b>：{@code Phainon} 的出厂装配（{@code new Phainon(level)}）
     * 挂的是 {@link PlayerController}，而它每回合都会 {@code GameMain.SCANNER.nextLine()}。
     * 无人值守地跑<b>真实回合循环</b>时那里会抛 {@code NoSuchElementException}
     * （自测里 stdin 是空的），于是"整条变身时间轴"根本没法在自测里驱动起来 ——
     * 之前那几轮"复现不了用户日志"就是这么来的。命令系统与输入方式<b>一个字都没改</b>，
     * 这里只是给自测换一个控制器。
     * <p>
     * 换完之后 {@link LivingThing} 的复制构造器会调 {@code UniversalController#copy(...)} 重建，
     * 所以进战斗副本照样不会去读标准输入（这句原来写的是"那一张类型表认得这个类型"——
     * 那张表 2026-10-24 已删，改成每种控制器自己重写 {@code copy(LivingThing)}）。
     *
     * @param phainon 要装配的白厄实例
     * @return 同一个实例（便于链式书写）
     */
    public static Phainon forSelfTest(Phainon phainon) {
        phainon.setController(new UniversalController(new ArrayList<>(phainon.getController().getSkills()), phainon));
        return phainon;
    }

    /**
     * 白厄的「免死」修正器：觉醒状态下受到的致死伤害会被拦下，血量锁 1，
     * 并在本回合末尾追加一次 {@link LastAttack}，由那一击正式退出变身。
     * <p>
     * 没有「一场只生效一次」的限制：只要还在变身中，致死伤害一律被拦下。
     * {@link #isPendingLastAttack()} 只用来防止同一回合重复排队最后一击
     * （排过一次之后，后续致死伤害仍然锁 1 血，但不会再多挥一刀）。
     * <p>
     * 判定期间（{@code pendingLastAttack} 已置位 / 伤害试算）只返回锁 1 的血量、不改状态。
     * <p>
     * 只挂在 {@code LivingThing} 的伤害修正器链上（{@code getDamage} / 伤害试算），
     * 攻击方算伤害时不经过这里，所以不会重复触发。
     * <p>
     * 判断对象一律取 {@link DamageEvent#getAttackedEntity()}，不要捕获构造它的那个实例：
     * 副本的觉醒状态可能和原体不同步，认错对象会误判。
     * <p>
     * 伤害试算（{@code isAnticipating()}）期间只算数、不改状态：
     * AI 预判一次致死伤害就会把这一次免死花掉的话，等于 AI 光靠「看一眼」就能破掉免死。
     * <p>
     * 触发时先 {@link #clearAwakenExtraTurns() 中止还没走完的额外回合}，
     * 再由 {@link #finalizeAwakenByInterrupt()} 冻结剩余回合数、清【弑魂之炽】与 75% 减伤。
     * 这一步<b>不退出变身</b>：{@code awaken}、技能表和攻击 +80% 都留着，
     * 好让「最后一击」先吃到变身加成。
     * <p>
     * 然后把「最后一击」排进<b>当前回合</b>的收尾队列；它挥完所有目标后，才在自己的
     * {@code comeToEffect} 末尾发 {@link AwakenEndEvent}，由 {@link AwakeEndListener#end}
     * 退出变身并按 +25% 生命上限回血。这样既不会因为摘掉额外回合而漏掉退出协议，
     * 也不会让最后一击提前失去变身加成。
     * <p>
     * 倍率按 {@link #getInterruptedRemainingExtraTurns() 打断那一刻剩余的额外回合数}算。
     * <p>
     * 试算期间（{@code isAnticipating()}）绝不改状态、也绝不排队。
     *
     * @return 免死用的伤害修正器
     */
    private IModifyDamage soulscorchDeathWard() {
        return new IModifyDamage() {
            @Override
            public long damageModify(long newHp, DamageEvent da) {
                if (da.getAttackedEntity() instanceof Phainon phainon && phainon.awaken && newHp <= 0) {
                    if (phainon.isPendingLastAttack() || phainon.isAnticipating()) {
                        // 「已经在等最后一击」那一支只锁血：同一回合里的第二次致死不再多排一刀。
                        // 退出协议由下面第一次排队那一步自己收口（finalizeAwakenByInterrupt），
                        // 所以标志不会因为"那一击排晚了"而卡死。
                        return 1;
                    }
                    newHp = 1;
                    phainon.setPendingLastAttack(true);
                    // 顺序要紧：先中止变身（摘掉时间轴上还没走的额外回合，并由这个动作自己完成退出协议），
                    // 再把 LastAttack 插进当前回合末尾 —— 它会在 comeToEffect 里按那个冻结值算倍率。
                    phainon.clearAwakenExtraTurns();
                    phainon.finalizeAwakenByInterrupt();
                    TurnEntry armingTurn = FightTurnPastListener.getPresentTurn();
                    armingTurn.addAct(phainon, (fight, user) -> {
                        List<LivingThing> availableTargets;
                        if (fight.getEnemiesList().contains(phainon))
                            availableTargets = new ArrayList<>(fight.getFighterList());
                        else {
                            availableTargets = new ArrayList<>(fight.getEnemiesList());
                        }

                        if (!availableTargets.isEmpty())
                            World.prototypeCopyOf(LastAttack.class).comeToEffect(fight, phainon, availableTargets);
                    }, cn.gfhnv.game.system.fight.ActEntry.PRIORITY_LAST);
                }
                return newHp;
            }
        };
    }

    public int getSoulscorch() {
        return soulscorch;
    }

    public void setSoulscorch(int soulscorch) {
        this.soulscorch = soulscorch;
    }

    public boolean isAbsorbDamage() {
        return absorbDamage;
    }

    public void setAbsorbDamage(boolean absorbDamage) {
        this.absorbDamage = absorbDamage;
    }

    public void addScourge(int i) {
        this.setScourge(Math.min(scourge + i, scourgeMax));
    }

    public void removeScourge(int i) {
        this.setScourge(Math.max(0, scourge - i));
    }

    /**
     * 星魂 1：额外回合的「基础速度继承比例」。
     * <p>
     * 1 魂起为 200%，本场每消灭 1 个敌方目标再 +1.5%，最多 +84%（即最高 284%）。
     * 未到 1 魂时是常规 100%。
     *
     * @return 速度继承比例（1.0 = 常规；2.0 = 200%）
     */
    public double getExtraTurnSpeedRatio() {
        if (getEidolon() < 1) {
            return 1.0;
        }
        return 2.0 + Math.min(0.84, 0.015 * enemyDeaths);
    }

    /**
     * 星魂 2：物理属性抗性穿透 +20%（本项目无"物理"独立元素，按全元素穿透实现）。
     */
    @Override
    protected void onEidolonChanged() {
        if (getEidolon() >= 2) {
            addPenetration(null, 0.2, E2_PENETRATION_SOURCE);
        } else {
            removePenetration(E2_PENETRATION_SOURCE);
        }
    }

    /**
     * 监听敌方死亡，给星魂 1 的额外回合速度计数。
     */
    public final class EnemyDeathCounter {
        @cn.gfhnv.game.annotation.SubscribeEvent
        public void onDeath(cn.gfhnv.game.event.EntityDeathEvent event) {
            Fight fight = getParticipateFight();
            if (fight != null && fight.getEnemiesList().contains(event.getEntity())) {
                enemyDeaths++;
            }
        }
    }

    public int getExtraTurns() {
        return extraTurns;
    }

    public void setExtraTurns(int extraTurns) {
        this.extraTurns = extraTurns;
    }

    /**
     * @return 变身期间排进时间轴的那些额外回合条目（活引用）
     */
    public List<TurnEntry> getAwakenExtraTurns() {
        return awakenExtraTurns;
    }

    /**
     * 中止变身：把还没走完的额外回合从时间轴上摘掉。
     * <p>
     * 变身被致命伤害打断时用。只清技能表或只清计数都不够：
     * 已经排在时间轴上的条目照样会被 {@code turnLoop} 取出来执行，
     * 而 {@code AwakeEndListener} 会把技能表<b>还原</b>成常规技能（不是留空），
     * 于是白厄还会带着全套常规技能继续行动若干回合。
     * <p>
     * 按引用摘除，不按 {@code isExtra} 扫描 —— 那个标记别的体系也在用。
     * <p>
     * <b>它只管"摘条目"这一件事</b>：退出协议（发 {@link AwakenEndEvent}）由
     * {@link #finalizeAwakenByInterrupt()} 单独负责，因为
     * {@code whenFightEnds()} 也要摘条目、却<b>不能</b>在那里补发退出事件
     * （战斗都结束了，退出协议里那条"给白厄再排一个回合"对结束中的时间轴没有意义）。
     * 打断变身那条路必须连着调这两个方法，顺序不能反 —— 见 {@link #soulscorchDeathWard()}。
     */
    public void clearAwakenExtraTurns() {
        for (TurnEntry entry : awakenExtraTurns) {
            TurnManager.getTurns().remove(entry);
        }
        awakenExtraTurns.clear();
    }

    /**
     * 「变身被打断」的<b>预处理</b>：冻结剩余回合数，并清掉【弑魂之炽】与 75% 减伤。
     * <p>
     * <b>它不退出变身</b>：{@code awaken}、觉醒技能表、攻击 +80% / 生命上限 +270% 都保留，
     * 因为紧接着要挥出的 {@link LastAttack} 还要吃这些加成。真正的退出协议由
     * {@link LastAttack#comeToEffect} 在全部伤害结算完之后发 {@link AwakenEndEvent} 触发，
     * 那时才把 {@code awaken} 置 false、还原技能表/名字并按 +25% 生命上限回血。
     * <p>
     * <b>为什么要先冻结剩余回合数</b>：退出协议（{@link AwakeEndListener#end}）会把
     * {@link #extraTurns} 清零，而 {@link LastAttack} 的倍率是按"还剩几个额外回合"算的
     * （{@code 13 × (1 − 剩余回合 × 0.125)}，下限压到 7）。先把打断那一刻的实时值冻结下来，
     * 「被打断的那一刀」才不会从缩水倍率变成满倍率 13 —— <b>公式与所有数值一个都没动</b>。
     * <p>
     * 正常路径（走完 8 个额外回合）永远不经过这里：那时 {@code extraTurns} 已经被
     * 各条额外回合自己减到 0，最后一击按 {@code extraTurns == 0} 出满倍率。
     */
    public void finalizeAwakenByInterrupt() {
        this.interruptedRemainingExtraTurns = this.extraTurns;
        // 2026-11-21 第 82 批：原文"如果卡厄斯兰那在上述目标行动完前退出变身，反击不释放，
        // 清除弑魂之炽和对应的减伤"。
        this.clearSoulscorchState();
    }

    /**
     * 清掉【弑魂之炽】相关的全部状态（层数 / 减伤 / 待行动名单）。
     * <p>
     * 退出变身（正常结束或被打断）都要调用：否则反击还会按旧名单触发、
     * 75% 减伤也会留在身上。
     */
    public void clearSoulscorchState() {
        this.soulscorch = 0;
        this.absorbDamage = false;
        this.removeDamageReduction(SOULSCORCH_DAMAGE_REDUCTION);
        if (getController() != null) {
            for (Skill skill : getController().getSkills()) {
                if (skill instanceof cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.CalamitySoulscorchEdict edict) {
                    edict.getWillAct().clear();
                }
            }
        }
    }

    /**
     * @return 被打断那一刻还剩几个额外回合；{@code -1} 表示这一刀不是"被打断的那一刀"
     */
    public int getInterruptedRemainingExtraTurns() {
        return interruptedRemainingExtraTurns;
    }

    /**
     * @param interruptedRemainingExtraTurns 打断那一刻剩余的额外回合数；{@code -1} 表示没有被打断
     */
    public void setInterruptedRemainingExtraTurns(int interruptedRemainingExtraTurns) {
        this.interruptedRemainingExtraTurns = interruptedRemainingExtraTurns;
    }

    public boolean isPendingLastAttack() {
        return pendingLastAttack;
    }

    public void setPendingLastAttack(boolean pendingLastAttack) {
        this.pendingLastAttack = pendingLastAttack;
    }

    public int getScourgeMax() {
        return scourgeMax;
    }

    public void setScourgeMax(int scourgeMax) {
        this.scourgeMax = scourgeMax;
    }

    public int getScourge() {
        return scourge;
    }

    public void setScourge(int scourge) {
        this.scourge = Math.min(scourgeMax, scourge);
    }

    public List<Skill> getSkills() {
        return skills;
    }

    public void setSkills(List<Skill> skills) {
        this.skills = skills;
    }

    public FightStartAndSelectEventListener getSelectEventListener() {
        return fightStartAndSelectEventListener;
    }

    public int getCoreflame() {
        return coreflame;
    }

    /**
     * 设置【火种】。未到 6 魂时夹在 {@code 0 … coreflameMax}；
     * 6 魂起<b>不再夹上限</b>（星魂 6：可溢出点数不再拥有上限），但下界仍是 0。
     *
     * @param coreflame 新火种数
     */
    public void setCoreflame(int coreflame) {
        int capped = getEidolon() >= 6 ? coreflame : Math.min(coreflameMax, coreflame);
        this.coreflame = Math.max(0, capped);
    }

    /**
     * 增加/减少【火种】（结果按 {@link #setCoreflame(int)} 的规则夹取）。
     *
     * @param amount 增量（可为负）
     */
    public void addCoreflame(int amount) {
        setCoreflame(this.coreflame + amount);
    }

    public int getCoreflameMax() {
        return coreflameMax;
    }

    public void setCoreflameMax(int coreflameMax) {
        this.coreflameMax = coreflameMax;
    }

    public boolean isListenerRegister() {
        return isListenerRegister;
    }

    public void setListenerRegister(boolean listenerRegister) {
        isListenerRegister = listenerRegister;
    }

    @Override
    public void whenFightEnds() {
        super.whenFightEnds();

        // 属性加成已经由 super.whenFightEnds() 的 resetTemporary() 清空（容器化之后不再需要减回去，
        // 旧写法在这里减 0.5 会把 heal/attack 变成负值 —— 那是 K3 那类坑）
        appliedExtraAbilityTier = 0;
        extraAbilityTier = 0;
        coreflame = 0;
        scourge = 0;
        soulscorch = 0;
        pendingLastAttack = false;
        EventBus.unregister(this.enemyDeathCounter);
        enemyDeaths = 0;
        interruptedRemainingExtraTurns = -1;
        this.absorbDamage = false;
        this.clearAwakenExtraTurns();
        if (this.awaken) {
            EventBus.post(new AwakenEndEvent(this));
        }
        isListenerRegister = false;
        this.awaken = false;
        EventBus.unregister(this.fightStartAndSelectEventListener);
        this.setShowSpecialMes(user -> {
            if (user instanceof Phainon phainon) {
                if (phainon.isAwaken())
                    System.out.println("毁伤数量" + phainon.getScourge() + "|||剩余额外回合数" + phainon.getExtraTurns());
                else System.out.println("当前火种数" + phainon.getCoreflame());
            }
        });
        this.getController().setActionSignal(ActionSignal.NORMAL);
        for (Skill skill : getController().getSkills()) {
            if (skill instanceof cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills.UltimateAttack) {
                EventBus.unregister(((UltimateAttack) skill).getAwakeEndListener());
                break;
            }
        }
    }

    @Override
    public void whenFightStart(Fight fight) {
        super.whenFightStart(fight);
        if (!isListenerRegister()) EventBus.register(this.fightStartAndSelectEventListener);
        EventBus.register(this.enemyDeathCounter);
        isListenerRegister = true;

        this.setExtraAbilityTier(this.getExtraAbilityTier() + 1);
        this.addCoreflame(1);
        // 星魂 6：战斗开始时额外获得 6 点【火种】。
        // 6 魂起 setCoreflame 不再夹上限，所以这里会真的叠在初始火种上。
        if (getEidolon() >= 6) {
            this.addCoreflame(E6_COREFLAME_ON_START);
        }
    }

    @Override
    public void updateSelf() {
        super.updateSelf();
        if (awaken) {
            this.removeEffectsIf(effect -> {
                if (effect.isNegative()) {
                    effect.whenLastTimeEnd(this);
                    return true;
                }
                return false;
            });

        }

        // 按来源整条替换：等级升/降都只留一条，重复调用是幂等的
        this.addPropertyEnhance(PropertyEnhanceType.ATTACK, extraAbilityTier * 0.5, 0, TIER_ENHANCE_SOURCE);
        appliedExtraAbilityTier = extraAbilityTier;


    }

    public int getExtraAbilityTier() {
        return extraAbilityTier;
    }

    public void setExtraAbilityTier(int extraAbilityTier) {
        this.extraAbilityTier = extraAbilityTier;
    }

    @Override
    public LivingThing copy() {
        return new Phainon(this);
    }

    public boolean isAwaken() {
        return awaken;
    }

    public void setAwaken(boolean awaken) {
        this.awaken = awaken;
    }
}
