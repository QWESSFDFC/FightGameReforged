package cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.officialStuff.customEntity.players.Phainon;
import cn.gfhnv.game.officialStuff.customEvent.phainonEvents.AwakeEndListener;
import cn.gfhnv.game.officialStuff.customEvent.phainonEvents.AwakenEndEvent;
import cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.LastAttack;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.ActionSignal;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.fight.TurnEntry;
import cn.gfhnv.game.system.fight.TurnManager;
import cn.gfhnv.game.system.mana.Mana;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class UltimateAttack extends Skill {
    private AwakeEndListener awakeEndListener;

    public UltimateAttack() {
        super("永劫燔世,其将背负", "description", 0, 0, 0, 0);
        this.setCoolDown(0);
        this.setConsumedMana(new Mana(0, ElementSort.UNIVERSAL));
        awakeEndListener = new AwakeEndListener();
    }

    public AwakeEndListener getAwakeEndListener() {
        return awakeEndListener;
    }

    public void setAwakeEndListener(AwakeEndListener awakeEndListener) {
        this.awakeEndListener = awakeEndListener;
    }

    @Override
    public Skill copy() {
        return new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills.UltimateAttack();
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
        if (user instanceof Phainon) {
            ((Phainon) user).setAwaken(true);
            ((Phainon) user).setCoreflame(((Phainon) user).getCoreflame() - 12);
            List<Skill> awakenSkills = new ArrayList<>();
            user.getController().setSkills(awakenSkills);
            EventBus.register(awakeEndListener);
            EventBus.unregister(((Phainon) user).getSelectEventListener());
            user.getController().setActionSignal(ActionSignal.WITHOUT_NEW_TURN);
        }
        BigDecimal needTime = BigDecimal.valueOf(10000).divide(BigDecimal.valueOf(user.getSpeed()).multiply(BigDecimal.valueOf(0.6)), 10, RoundingMode.HALF_UP);
        TurnManager.getTurns().add(new TurnEntry(user, BigDecimal.ZERO, TurnManager.getPresentTime()).setExtra(true));//1
        TurnManager.getTurns().add(new TurnEntry(user, needTime, TurnManager.getPresentTime()).setExtra(true));//2
        TurnManager.getTurns().add(new TurnEntry(user, needTime.multiply(BigDecimal.TWO), TurnManager.getPresentTime()).setExtra(true));//3
        TurnManager.getTurns().add(new TurnEntry(user, needTime.multiply(BigDecimal.valueOf(3)), TurnManager.getPresentTime()).setExtra(true));//4
        TurnManager.getTurns().add(new TurnEntry(user, needTime.multiply(BigDecimal.valueOf(4)), TurnManager.getPresentTime()).setExtra(true));//5
        TurnManager.getTurns().add(new TurnEntry(user, needTime.multiply(BigDecimal.valueOf(5)), TurnManager.getPresentTime()).setExtra(true));//6
        TurnManager.getTurns().add(new TurnEntry(user, needTime.multiply(BigDecimal.valueOf(6)), TurnManager.getPresentTime()).setExtra(true));//7
        TurnEntry lastestOne = new TurnEntry(user, needTime.multiply(BigDecimal.valueOf(7)), TurnManager.getPresentTime()).setExtra(true);//8
        lastestOne.setActionSignal(ActionSignal.SKIP_WITHOUT_NEW_TURN);
        lastestOne.getiSpecialActionList().add((fight1, user1) -> {
            System.out.println("变身结束");
            user1.getController().getSkills().clear();
            List<LivingThing> availableTargets;
            if (fight1.getEnemiesList().contains(user1)) availableTargets=new ArrayList<>(fight1.getFighterList());
            else {
                availableTargets=new ArrayList<>(fight1.getEnemiesList());
            }
           if (user1 instanceof Phainon) {
               if (!availableTargets.isEmpty()) new LastAttack().comeToEffect(fight1, user1, availableTargets);
           }

        });

    }
}
