package cn.gfhnv.game.officialStuff.customEntity.players;

import cn.gfhnv.game.data.NoConfig;
import cn.gfhnv.game.data.NoData;
import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entity.Player;
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
import java.util.ListIterator;

public class Phainon extends Player {

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
    private int coreflame_max = 15;
    private int soulscorch;
    private int scourge = 0;
    private int scourge_max = 8;
    private boolean isAwaken = false;
    private int extraAbilityTier = 0;
    private int appliedExtraAbilityTier = 0;
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

    public Phainon(Phainon phainon1) {
        super(phainon1);
        isAwaken = phainon1.isAwaken;
        extraAbilityTier = phainon1.extraAbilityTier;
        appliedExtraAbilityTier = phainon1.appliedExtraAbilityTier;
        absorbDamage = phainon1.absorbDamage;
        extraTurns = phainon1.extraTurns;
        pendingLastAttack = phainon1.pendingLastAttack;
        interruptedRemainingExtraTurns = phainon1.interruptedRemainingExtraTurns;
        skills = new ArrayList<>(this.getController().getSkills());
        coreflame_max = phainon1.coreflame_max;
        soulscorch = phainon1.soulscorch;
        scourge = phainon1.scourge;
        scourge_max = phainon1.scourge_max;
        coreflame = phainon1.coreflame;
        awakenExtraTurns.clear();
        awakenExtraTurns.addAll(phainon1.awakenExtraTurns);
        this.setModifyDamage(soulscorchDeathWard());
        this.setShowSpecialMes(user -> {
            if (user instanceof Phainon phainon) {
                if (phainon.isAwaken)
                    System.out.println("毁伤数量" + phainon.getScourge() + "|||剩余额外回合数" + phainon.getExtraTurns());
                else System.out.println("当前火种数" + phainon.getCoreflame());
            }
        });
    }

    public Phainon(long l) {
        super("白厄", "phainon", 0.7, 0, 0, 0, 0, 120, l, "player", 29, 40, 25, ElementSort.FIRE);
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
                if (phainon.isAwaken)
                    System.out.println("毁伤数量" + phainon.getScourge() + "|||剩余额外回合数" + phainon.getExtraTurns());
                else System.out.println("当前火种数" + phainon.getCoreflame());
            }
        });

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
     * 触发时先 {@link #clearAwakenExtraTurns() 中止还没走完的额外回合}（变身被打断），
     * 由 {@link #finalizeAwakenByInterrupt()} <b>当场完成退出协议</b>
     * （发 {@link AwakenEndEvent}，清掉 {@code isAwaken} 与技能表、把 {@code extraTurns} 归零），
     * 再把「最后一击」排进<b>当前回合</b>的收尾队列：伤害照旧挥出去，
     * 倍率按 {@link #getInterruptedRemainingExtraTurns() 打断那一刻剩余的额外回合数}算。
     * <p>
     * <b>为什么退出协议不能继续挂在时间轴上</b>：它原来挂在第 8 条额外回合
     * （{@code UltimateAttack} 的 {@code lastestOne}）的收尾队列里 ——
     * 而"中止变身"干的正是把这些额外回合从时间轴上摘掉，
     * 于是中止变身的动作会把退出协议自己一起取消掉：技能表没人清、{@code AwakenEndEvent} 没人发，
     * 白厄就留在觉醒状态里（用户 2026-10 实测的「白厄无敌」）。
     * 现在退出由<b>中止变身的那个动作自己收口</b>，与时间轴上还剩什么条目无关。
     * <p>
     * 试算期间（{@code isAnticipating()}）绝不改状态、也绝不排队。
     *
     * @return 免死用的伤害修正器
     */
    private IModifyDamage soulscorchDeathWard() {
        return new IModifyDamage() {
            @Override
            public long damageModify(long newHp, DamageEvent da) {
                if (da.getAttackedEntity() instanceof Phainon phainon && phainon.isAwaken && newHp <= 0) {
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
                    armingTurn.getLastExecuteList().add((fight, user) -> {
                        List<LivingThing> availableTargets;
                        if (fight.getEnemiesList().contains(phainon))
                            availableTargets = new ArrayList<>(fight.getFighterList());
                        else {
                            availableTargets = new ArrayList<>(fight.getEnemiesList());
                        }

                        if (!availableTargets.isEmpty())
                            World.prototypeCopyOf(LastAttack.class).comeToEffect(fight, phainon, availableTargets);
                    });
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
        this.setScourge(Math.min(scourge + i, scourge_max));
    }

    public void removeScourge(int i) {
        this.setScourge(Math.max(0, scourge - i));
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
     * 「变身被打断」的<b>退出收口</b>：把退出协议就地走完，不再依赖时间轴上还剩什么条目。
     * <p>
     * <b>它解决的是什么</b>：退出协议原本挂在第 8 条额外回合
     * （{@code UltimateAttack} 的 {@code lastestOne}）的收尾队列里，而那条条目正是
     * {@link #clearAwakenExtraTurns()} 要摘掉的东西之一 ——
     * 于是"中止变身"这个动作恰好取消了变身的退出协议：技能表没人清、
     * {@code AwakenEndEvent} 没人发、{@code extraTurns} 没人归零，
     * 白厄留在觉醒状态里，而 {@link #soulscorchDeathWard()} 的「已经在等最后一击」那一支
     * 又只会把血锁在 1 → 表现就是用户 2026-10 实测的「白厄无敌」。
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
        //EventBus.post(new AwakenEndEvent(this)); 白厄在变身时释放最后一击.
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

    public int getScourge_max() {
        return scourge_max;
    }

    public void setScourge_max(int scourge_max) {
        this.scourge_max = scourge_max;
    }

    public int getScourge() {
        return scourge;
    }

    public void setScourge(int scourge) {
        this.scourge = Math.min(scourge_max, scourge);
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

    public void setCoreflame(int coreflame) {
        this.coreflame = Math.min(coreflame_max, coreflame);
    }

    public int getCoreflame_max() {
        return coreflame_max;
    }

    public void setCoreflame_max(int coreflame_max) {
        this.coreflame_max = coreflame_max;
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

        this.setAttackEnhancePercent(this.getAttackEnhancePercent() - appliedExtraAbilityTier * 0.5);
        appliedExtraAbilityTier = 0;
        extraAbilityTier = 0;
        coreflame = 0;
        scourge = 0;
        soulscorch = 0;
        pendingLastAttack = false;
        interruptedRemainingExtraTurns = -1;
        this.absorbDamage = false;
        this.clearAwakenExtraTurns();
        if (this.isAwaken) {
            EventBus.post(new AwakenEndEvent(this));
        }
        isListenerRegister = false;
        this.isAwaken = false;
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
        isListenerRegister = true;

        this.setExtraAbilityTier(this.getExtraAbilityTier() + 1);
        this.setCoreflame(this.getCoreflame() + 1);
    }

    @Override
    public void updateSelf() {
        super.updateSelf();
        if (isAwaken) {
            ListIterator<Effect> listedIterator = this.getEntityEffectList().listIterator();
            while (listedIterator.hasNext()) {
                Effect effect = listedIterator.next();
                if (effect.isNegative()) {
                    effect.whenLastTimeEnd(this);
                    listedIterator.remove();
                }
            }

        }

        this.setAttackEnhancePercent(this.getAttackEnhancePercent()
                + (extraAbilityTier - appliedExtraAbilityTier) * 0.5);
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
        return isAwaken;
    }

    public void setAwaken(boolean awaken) {
        isAwaken = awaken;
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
     * 换完之后 {@link LivingThing} 的复制构造器会把它按 {@code UniversalController} 重建
     * （那一张类型表认得这个类型），所以进战斗副本照样不会去读标准输入。
     *
     * @param phainon 要装配的白厄实例
     * @return 同一个实例（便于链式书写）
     */
    public static Phainon forSelfTest(Phainon phainon) {
        phainon.setController(new UniversalController(new ArrayList<>(phainon.getController().getSkills()), phainon));
        return phainon;
    }
}
