package cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.SpeedEnhanceEffect;
import cn.gfhnv.game.officialStuff.customEntity.players.Phainon;
import cn.gfhnv.game.officialStuff.customEvent.phainonEvents.AwakenEndEvent;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.mana.Mana;

import java.util.List;

public class LastAttack extends Skill {
    public LastAttack() {
        super("最后一击", "最后一击", 0, 9.6, 0, -1);
        this.setConsumedMana(new Mana(0, ElementSort.UNIVERSAL));
        this.setCoolDown(0);
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        if (user instanceof Phainon){
            this.setAtkMagnification(getAtkMagnification()*(1-((Phainon) user).getExtraTurns()*0.125));
        }

        for (LivingThing livingThing : enemies) {
            System.out.print(user.getName() + "攻击了" + livingThing.getName());
            user.makeDamage(livingThing, this);

        }

        if (fight.getEnemiesList().contains(user)) {
            for (LivingThing livingThing:fight.getEnemiesList()){
                livingThing.addEffect(new SpeedEnhanceEffect(0.15,1));
            }
        }else {
            for (LivingThing livingThing:fight.getFighterList()){
                livingThing.addEffect(new SpeedEnhanceEffect(0.15,1));
            }
        }
        EventBus.post(new AwakenEndEvent((Phainon) user));
    }
}
