package cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.eventListener.FightTurnPastListener;
import cn.gfhnv.game.interfaces.IAiScoreBonus;
import cn.gfhnv.game.officialStuff.customEntity.players.Phainon;
import cn.gfhnv.game.officialStuff.customEvent.phainonEvents.AwakeEndListener;
import cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.*;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.skill.SkillCoefficientTunable;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.ActionSignal;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.fight.TurnEntry;
import cn.gfhnv.game.system.fight.TurnManager;
import cn.gfhnv.game.system.mana.Mana;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;
import cn.gfhnv.game.world.World;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UltimateAttack extends Skill implements SkillCoefficientTunable, IAiScoreBonus {
    /**
     * 具名系数：开变身要烧掉几层【火种】。
     */
    private static final String COEFF_COREFLAME_COST = "coreflameCost";
    /**
     * 具名系数：变身后排几个额外回合。
     */
    private static final String COEFF_EXTRA_TURNS = "awakenExtraTurns";
    /**
     * 具名系数：<b>AI 评分加成</b>（出厂 {@code 100}）。
     * <p>
     * 这个技能 0 伤害、目标是自己，按 AI 原来的评分永远打不过普攻；这个数就是"AI 多想放它"。
     */
    private static final String COEFF_AI_PRIORITY = "aiPriority";

    private AwakeEndListener awakeEndListener;

    /**
     * 开变身要烧掉几层【火种】（出厂 {@code 12}）。<b>2026-10-24 起可外配</b>
     * （{@code SkillData.json} 的 {@code coreflameCost}）。
     */
    private int coreflameCost = 12;

    /**
     * 变身后排几个额外回合（出厂 {@code 8}，含最后一击那一刀）。<b>2026-10-24 起可外配</b>
     * （{@code SkillData.json} 的 {@code awakenExtraTurns}）。
     * <p>
     * 时间轴上的条目数就是这个数：前 {@code N-1} 个是普通额外回合，第 {@code N} 个是
     * "最后一击"那一刀（收尾队列里挥出去）。
     */
    private int awakenExtraTurns = 8;

    /**
     * AI 评分加成（出厂 {@code 100}）：见 {@link #aiScoreBonus}。
     * <b>2026-10-24 起可外配</b>（{@code SkillData.json} 的 {@code aiPriority}）。
     */
    private double aiPriority = 100;

    public UltimateAttack() {
        super("大招:永劫燔世,其将背负", "消耗12火种,变身.", 0, 0, 0, 0);
        // ⚠️ 这是"给自己"的变身技（aims=0），**不是打敌人的**。
        // 2026-10-24 用户实测发现它一直是 forEnemies=true（Skill 的字段默认值就是 true），
        // 于是 /data 里看着像"对敌方使用"。aims=0 时它虽然不影响打谁，但语义是错的。
        this.setForEnemies(false);
        this.setCoolDown(0);
        this.setConsumedMana(new Mana(0, ElementSort.UNIVERSAL));
        this.getTags().put(TagType.ATTACK, new Tag(10));
    }

    /**
     * 复制构造器。
     * <p>
     * {@link #copy()} 必须走这里，不能返回 {@code new UltimateAttack()}：那样会把
     * 冷却/消耗/权重还原成构造器里的出厂值，于是打在模板上的配置（{@code SkillData.json}）
     * <b>永远传不到选人时复制出去的副本</b>。
     * <p>
     * {@code awakeEndListener} 每个副本各持一个（出厂时为 {@code null}，
     * 在 {@link #comeToEffect} 里按 {@code user} 新建）：每个副本属于一个不同的白厄，
     * <b>不能共用</b> —— 监听器要带着"我服务的是哪只白厄"这个归属，
     * 否则同场两只白厄同时变身时，一只的 {@code AwakenEndEvent} 会把另一只的监听器也消耗掉。
     * <p>
     * ⚠️ <b>两个具名系数也要在这里手动带过去</b>（配置打在模板上、副本靠 {@code copy()} 白送）。
     *
     * @param other 被复制的技能
     */
    public UltimateAttack(UltimateAttack other) {
        super(other);
        this.coreflameCost = other.coreflameCost;
        this.awakenExtraTurns = other.awakenExtraTurns;
        this.aiPriority = other.aiPriority;
    }

    @Override
    public Map<String, Double> coefficientValues() {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put(COEFF_COREFLAME_COST, (double) coreflameCost);
        values.put(COEFF_EXTRA_TURNS, (double) awakenExtraTurns);
        values.put(COEFF_AI_PRIORITY, aiPriority);
        return values;
    }

    @Override
    public void setCoefficientValue(String name, double value) {
        switch (name) {
            case COEFF_COREFLAME_COST -> this.coreflameCost = Math.max(0, (int) value);
            case COEFF_EXTRA_TURNS -> this.awakenExtraTurns = Math.max(1, (int) value);
            case COEFF_AI_PRIORITY -> this.aiPriority = Math.max(0, value);
            default -> throw new IllegalArgumentException("大招:永劫燔世,其将背负没有叫「" + name + "」的具名系数");
        }
    }

    /**
     * @return 时间轴上要排几个额外回合（含最后一击那一刀；至少 1）
     */
    public int scheduledExtraTurns() {
        return Math.max(1, awakenExtraTurns);
    }

    public AwakeEndListener getAwakeEndListener() {
        return awakeEndListener;
    }

    public void setAwakeEndListener(AwakeEndListener awakeEndListener) {
        this.awakeEndListener = awakeEndListener;
    }

    @Override
    public Skill copy() {
        return new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills.UltimateAttack(this);
    }

    /**
     * <b>AI 评分加成</b>：火种够、而且还没变身 → 加 {@link #aiPriority} 分，让 AI 愿意开变身。
     * <p>
     * 为什么需要它：这个技能 {@code aims=0}、倍率全 0，AI 的 {@code evaluateAction} 给它算出来是
     * "权重 − 蓝耗惩罚"（目标是自己、满血，所以一点加分都没有），**永远打不过普攻** ——
     * 即便它的 {@code ATTACK} 权重是 10（那玩意儿只决定"选哪一类 Tag"）。
     * <p>
     * 返回 0 的两种情况都很重要：火种不够时不该开（不然会带着负火种变身），
     * 已经在觉醒里也不该再开（会重排时间轴）。
     *
     * @param fight  当前战斗（本方法不用，允许 {@code null}）
     * @param self   施法者
     * @param target 目标（这个技能是"对自己"，AI 传进来的是 {@code self}）
     * @return 加多少分
     */
    @Override
    public double aiScoreBonus(Fight fight, LivingThing self, LivingThing target) {
        if (!(self instanceof Phainon phainon)) {
            return 0;
        }
        if (phainon.isAwaken()) {
            return 0;
        }
        if (phainon.getCoreflame() < coreflameCost) {
            return 0;
        }
        return aiPriority;
    }

    /**
     * 带目标列表的可用性判断：<b>把火种门槛一起算进来</b>。
     * <p>
     * ⚠️ 基类版本只查"蓝够不够 + 冷却"，而这个技能的门槛是<u>火种</u>，只在
     * {@link #canUse(Fight, LivingThing)} 里查。{@code ThinkingControllerAI} 与
     * {@code Skill#use(...)} 走的都是<b>这个三参版本</b> —— 不覆盖的话
     * AI 会认为"0 火种也能放大招"，真放下去会带着负火种变身（{@code setCoreflame} 只夹上限）。
     *
     * @param fight   当前战斗
     * @param user    使用者
     * @param targets 目标（本技能不用；可能为 {@code null}）
     * @return 能不能放
     */
    @Override
    public boolean canUse(Fight fight, LivingThing user, List<LivingThing> targets) {
        return super.canUse(fight, user, targets) && canUse(fight, user);
    }

    @Override
    public boolean canUse(Fight fight, LivingThing user) {
        if (user instanceof Phainon) {
            return ((Phainon) user).getCoreflame() >= coreflameCost;
        }
        return false;
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user) {
        user.setAttackEnhancePercent(user.getAttackEnhancePercent() + 0.8);
        user.setHpEnhancePercent(user.getHpEnhancePercent() + 2.7);
        user.renewHp();
        user.setHp(user.getHpMax());
        user.setName("卡厄斯兰那");
        FightTurnPastListener.getPresentTurn().setActionSignal(ActionSignal.SKIP_WITHOUT_NEW_TURN);
        if (user instanceof Phainon) {
            ((Phainon) user).setAwaken(true);
            ((Phainon) user).setCoreflame(((Phainon) user).getCoreflame() - coreflameCost);
            List<Skill> awakenSkills = new ArrayList<>();
            // 觉醒技能一律取"注册表原型"的副本：配置（SkillData.json）打在原型上，
            // 只有 copy() 出来的副本才带着那些值（原型本身全场共用，不能直接拿去用）。
            awakenSkills.add(World.prototypeCopyOf(AwakenCommonAttack.class));
            awakenSkills.add(World.prototypeCopyOf(CalamitySoulscorchEdict.class));
            awakenSkills.add(World.prototypeCopyOf(FoundationStardeathVerdict.class));
            user.getController().setSkills(awakenSkills);
            // 归属只有在这里才拿得到（出厂时技能还不知道会被哪个白厄拿在手上）：
            // 监听器必须认得出"这是我的白厄"，见 AwakeEndListener#owner。
            this.awakeEndListener = new AwakeEndListener((Phainon) user);
            EventBus.register(awakeEndListener);
            user.getController().setActionSignal(ActionSignal.WITHOUT_NEW_TURN);
        }
        BigDecimal needTime = BigDecimal.valueOf(10000).divide(BigDecimal.valueOf(user.getSpeed()), 10, RoundingMode.HALF_UP);
        if (user instanceof Phainon phainon) {
            phainon.setExtraTurns(awakenExtraTurns);
            phainon.addScourge(4);
        }
        // 条目数 = scheduledExtraTurns()：前 N-1 个是普通额外回合，第 N 个是收尾那一刀。
        int scheduled = scheduledExtraTurns();
        List<TurnEntry> awakenEntries = user instanceof Phainon phainon ? phainon.getAwakenExtraTurns() : null;
        // 交给白厄登记（活引用）：变身被致命伤害打断时要照着这个列表把剩余额外回合从时间轴上摘掉
        if (awakenEntries != null) awakenEntries.clear();
        for (int i = 0; i <= scheduled - 2; i++) {

            TurnEntry extraTurn = new TurnEntry(user, needTime.multiply(BigDecimal.valueOf(i)), TurnManager.getPresentTime()).setExtra(true).addLastSpecialAction((fight1, user1) -> {
                if (user1 instanceof Phainon phainon) {
                    phainon.setExtraTurns(phainon.getExtraTurns() - 1);

                }
            }).addFirstAction((fight1, user1) -> {

                if (user1 instanceof Phainon) {
                    if (FightTurnPastListener.getPresentTurn().isExtra() && ((Phainon) user1).isAwaken() && ((Phainon) user1).getSoulscorch() > 0) {
                        List<LivingThing> anticipateEnemies = fight1.getOpponentList(user1);
                        World.prototypeCopyOf(Counterattack.class).comeToEffect(fight1, user1, anticipateEnemies);
                    }
                }
            }).setActionSignal(ActionSignal.WITHOUT_NEW_TURN);
            TurnManager.getTurns().add(extraTurn);
            if (awakenEntries != null) awakenEntries.add(extraTurn);
        }

        TurnEntry lastestOne = new TurnEntry(user, needTime.multiply(BigDecimal.valueOf(scheduled - 1)), TurnManager.getPresentTime()).setExtra(true);//8
        lastestOne.setActionSignal(ActionSignal.SKIP_WITHOUT_NEW_TURN);
        lastestOne.addFirstAction((fight1, user1) -> {

            if (user1 instanceof Phainon) {
                if (FightTurnPastListener.getPresentTurn().isExtra() && ((Phainon) user1).isAwaken() && ((Phainon) user1).getSoulscorch() > 0) {
                    List<LivingThing> anticipateEnemies = fight1.getOpponentList(user1);
                    World.prototypeCopyOf(Counterattack.class).comeToEffect(fight1, user1, anticipateEnemies);
                }
            }

        });
        lastestOne.getLastExecuteList().add((fight1, user1) -> {
            System.out.println("变身结束");
            user1.getController().getSkills().clear();
            List<LivingThing> availableTargets = fight1.getOpponentList(user1);
            if (user1 instanceof Phainon phainon) {
                phainon.setExtraTurns(phainon.getExtraTurns() - 1);
                phainon.setCoreflame(phainon.getCoreflame() + 3);
                if (!availableTargets.isEmpty())
                    World.prototypeCopyOf(LastAttack.class).comeToEffect(fight1, user1, availableTargets);
            }

        });
        TurnManager.getTurns().add(lastestOne);
        if (awakenEntries != null) awakenEntries.add(lastestOne);
        TurnManager.sort();
    }
}
