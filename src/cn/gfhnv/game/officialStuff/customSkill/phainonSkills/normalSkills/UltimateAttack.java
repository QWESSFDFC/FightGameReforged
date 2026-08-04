package cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.Event;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.officialStuff.customEntity.players.Phainon;
import cn.gfhnv.game.officialStuff.customEvent.phainonEvents.AwakeEndListener;
import cn.gfhnv.game.officialStuff.customEvent.phainonEvents.AwakenEndEvent;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.mana.Mana;

import java.util.ArrayList;
import java.util.List;

public class UltimateAttack extends Skill {

    public UltimateAttack() {
        super("name", "description", 0, 0, 0, 0);
        this.setCoolDown(0);
        this.setConsumedMana(new Mana(0, ElementSort.UNIVERSAL));
    }

    @Override
    public Skill copy() {
        return new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills.UltimateAttack();
    }

    @Override
    public boolean canUse(Fight fight, LivingThing user) {
        if (user instanceof Phainon){
            return ((Phainon) user).getSpark()>=12;
        }
        return false;
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user) {
        if (user instanceof Phainon){
            ((Phainon) user).setAwaken(true);
            List<Skill> awakenSkills=new ArrayList<>();
            user.getController().setSkills(awakenSkills);
            EventBus.register(new AwakeEndListener());
        }
    }
}
