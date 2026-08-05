package cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.officialStuff.customEntity.players.Phainon;
import cn.gfhnv.game.officialStuff.customEvent.phainonEvents.AwakeEndListener;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.ActionSignal;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.mana.Mana;

import java.util.ArrayList;
import java.util.List;

public class UltimateAttack extends Skill {
private AwakeEndListener awakeEndListener;

    public AwakeEndListener getAwakeEndListener() {
        return awakeEndListener;
    }

    public void setAwakeEndListener(AwakeEndListener awakeEndListener) {
        this.awakeEndListener = awakeEndListener;
    }

    public UltimateAttack() {
        super("永劫燔世,其将背负", "description", 0, 0, 0, 0);
        this.setCoolDown(0);
        this.setConsumedMana(new Mana(0, ElementSort.UNIVERSAL));
        awakeEndListener=new AwakeEndListener();
    }

    @Override
    public Skill copy() {
        return new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills.UltimateAttack();
    }

    @Override
    public boolean canUse(Fight fight, LivingThing user) {
        if (user instanceof Phainon){
            return ((Phainon) user).getPyroheart()>=12;
        }
        return false;
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user) {
        if (user instanceof Phainon){
            ((Phainon) user).setAwaken(true);
            ((Phainon) user).setPyroheart(((Phainon) user).getPyroheart()-12);
            List<Skill> awakenSkills=new ArrayList<>();
            user.getController().setSkills(awakenSkills);
            EventBus.register(awakeEndListener);
            EventBus.unregister(((Phainon) user).getSelectEventListener());
        user.getController().setActionSignal(ActionSignal.WITHOUT_NEW_TURN);
        }
    }
}
