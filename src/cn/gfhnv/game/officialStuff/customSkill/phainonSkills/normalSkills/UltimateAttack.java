package cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.eventListener.FightTurnPastListener;
import cn.gfhnv.game.officialStuff.customEntity.players.Phainon;
import cn.gfhnv.game.officialStuff.customEvent.phainonEvents.AwakeEndListener;
import cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.*;
import cn.gfhnv.game.skill.Skill;
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
import java.util.List;

public class UltimateAttack extends Skill {
    private AwakeEndListener awakeEndListener;

    public UltimateAttack() {
        super("大招:永劫燔世,其将背负", "消耗12火种,变身.", 0, 0, 0, 0);
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
     *
     * @param other 被复制的技能
     */
    public UltimateAttack(UltimateAttack other) {
        super(other);
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

    @Override
    public boolean canUse(Fight fight, LivingThing user) {
        if (user instanceof Phainon) {
            return ((Phainon) user).getCoreflame() >= 12;
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
            ((Phainon) user).setCoreflame(((Phainon) user).getCoreflame() - 12);
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
            phainon.setExtraTurns(8);
            phainon.addScourge(4);
        }
        List<TurnEntry> awakenExtraTurns = user instanceof Phainon phainon ? phainon.getAwakenExtraTurns() : null;
        // 交给白厄登记（活引用）：变身被致命伤害打断时要照着这个列表把剩余额外回合从时间轴上摘掉
        if (awakenExtraTurns != null) awakenExtraTurns.clear();
        for (int i = 0; i <= 6; i++) {

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
            if (awakenExtraTurns != null) awakenExtraTurns.add(extraTurn);
        }

        TurnEntry lastestOne = new TurnEntry(user, needTime.multiply(BigDecimal.valueOf(7)), TurnManager.getPresentTime()).setExtra(true);//8
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
        if (awakenExtraTurns != null) awakenExtraTurns.add(lastestOne);
        TurnManager.sort();
    }
}
