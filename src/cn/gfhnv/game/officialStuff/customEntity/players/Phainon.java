package cn.gfhnv.game.officialStuff.customEntity.players;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entity.Player;
import cn.gfhnv.game.entityController.PlayerController;
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
    public static final Object SOULSCORCH_DAMAGE_REDUCTION = new Object();
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
    private int extraTurns = 0;
    /**
     * 变身被打断的那一刻，还剩多少个额外回合（{@link #snapshotInterruptedLastAttack()}）。
     * <p>
     * 最后一击是「额外回合剩得越少、打得越狠」
     * （{@code atkMagnification = 13 × (1 − extraTurns × 0.125)}）。被打断时剩余额外回合会被
     * 清算掉、{@link #extraTurns} 一起清零，那一击就会从（例如）4.875× 直接变成满倍率 13× ——
     * 「挨打打断」反而比正常走完更疼。所以打断前先把当时的数字存下来给那一击用。
     */
    private int interruptedLastAttackSnapshot = 0;
    /**
     * 是否已经为「被打断的那一击」存过快照。
     */
    private boolean lastAttackSnapshotTaken = false;

    public Phainon(Phainon phainon1) {
        super(phainon1);
        isAwaken = phainon1.isAwaken;
        extraAbilityTier = phainon1.extraAbilityTier;
        appliedExtraAbilityTier = phainon1.appliedExtraAbilityTier;
        absorbDamage = phainon1.absorbDamage;
        extraTurns = phainon1.extraTurns;
        pendingLastAttack = phainon1.pendingLastAttack;
        interruptedLastAttackSnapshot = phainon1.interruptedLastAttackSnapshot;
        lastAttackSnapshotTaken = phainon1.lastAttackSnapshotTaken;
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
     * 再把「最后一击」排进<b>当前回合</b>的收尾队列：伤害照旧挥出去，
     * 挥完由 {@link LastAttack} 发 {@code AwakenEndEvent} 正式退出变身。
     *
     * @return 免死用的伤害修正器
     */
    private IModifyDamage soulscorchDeathWard() {
        return new IModifyDamage() {
            @Override
            public long damageModify(long newHp, DamageEvent da) {
                if (da.getAttackedEntity() instanceof Phainon phainon && phainon.isAwaken && newHp <= 0) {
                    if (phainon.isPendingLastAttack() || phainon.isAnticipating()) {
                        return 1;
                    }
                    newHp = 1;
                    phainon.setPendingLastAttack(true);
                    // 顺序要紧：先把当时的额外回合数存下来，再清算（清算会把 extraTurns 清零）
                    phainon.snapshotInterruptedLastAttack();
                    phainon.clearAwakenExtraTurns();
                    FightTurnPastListener.getPresentTurn().getLastExecuteList().add((fight, user) -> {
                        List<LivingThing> availableTargets;
                        if (fight.getEnemiesList().contains(phainon))
                            availableTargets = new ArrayList<>(fight.getFighterList());
                        else {
                            availableTargets = new ArrayList<>(fight.getEnemiesList());
                        }

                        if (!availableTargets.isEmpty())
                            new LastAttack().comeToEffect(fight, phainon, availableTargets);
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
     * 中止变身：把还没走完的额外回合从时间轴上摘掉，并把计数清零。
     * <p>
     * 变身被致命伤害打断时用。只清 {@code extraTurns} 或只清技能表都不够：
     * 已经排在时间轴上的条目照样会被 {@code turnLoop} 取出来执行，
     * 而 {@code AwakeEndListener} 会把技能表<b>还原</b>成常规技能（不是留空），
     * 于是白厄还会带着全套常规技能继续行动若干回合。
     * <p>
     * 按引用摘除，不按 {@code isExtra} 扫描 —— 那个标记别的体系也在用。
     * <p>
     * 注意它会连带把 {@link #extraTurns} 清零，所以「被打断后挥出的那一击」的倍率
     * 必须提前用 {@link #snapshotInterruptedLastAttack()} 存下来。
     */
    public void clearAwakenExtraTurns() {
        for (TurnEntry entry : awakenExtraTurns) {
            TurnManager.getTurns().remove(entry);
        }
        awakenExtraTurns.clear();
        extraTurns = 0;
    }

    /**
     * 把「被打断那一刻还剩多少额外回合」存下来，供接下来那一击
     * （{@link LastAttack}）算倍率用。要在 {@link #clearAwakenExtraTurns()} <b>之前</b>调用。
     * <p>
     * 每次变身只存一次：{@link AwakeEndListener} 在变身结束时复位快照，
     * 所以下一次变身会重新取。
     */
    public void snapshotInterruptedLastAttack() {
        if (lastAttackSnapshotTaken) {
            return;
        }
        interruptedLastAttackSnapshot = extraTurns;
        lastAttackSnapshotTaken = true;
    }

    /**
     * @return 有没有为「被打断的那一击」存过快照
     */
    public boolean hasInterruptedLastAttackSnapshot() {
        return lastAttackSnapshotTaken;
    }

    /**
     * @return 被打断那一刻剩余的额外回合数（没存过快照时为 0）
     */
    public int getInterruptedLastAttackSnapshot() {
        return interruptedLastAttackSnapshot;
    }

    /**
     * 丢弃被打断那一击的倍率快照（变身结束时用）。
     */
    public void clearInterruptedLastAttackSnapshot() {
        interruptedLastAttackSnapshot = 0;
        lastAttackSnapshotTaken = false;
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
        this.absorbDamage = false;
        this.clearAwakenExtraTurns();
        this.clearInterruptedLastAttackSnapshot();
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
}
