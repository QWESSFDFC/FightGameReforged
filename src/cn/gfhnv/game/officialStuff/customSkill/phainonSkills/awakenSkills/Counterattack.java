package cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.eventListener.FightTurnPastListener;
import cn.gfhnv.game.officialStuff.customEntity.players.Phainon;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;

import java.lang.foreign.MemorySegment;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

public class Counterattack extends Skill {
    @Override
    public Skill copy() {
        return new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.Counterattack();
    }
    public Counterattack() {
        super("灾厄•弑魂焚诏的反击", "灾厄•弑魂焚诏的反击",0, 0.4, 0, -1);
        this.setCoolDown(0);
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        int soulscorch=0;
        double randomMag=0.3;
        if (user instanceof Phainon phainon){ soulscorch=phainon.getSoulscorch();
            phainon.setSoulscorch(0);
            if (phainon.isAbsorbDamage()){ phainon.setDamageAbsorbedPercent(phainon.getDamageAbsorbedPercent()-0.75);
                phainon.setAbsorbDamage(false);}
        for(Skill skill:user.getController().getSkills()){
            if (skill instanceof CalamitySoulscorchEdict) ((CalamitySoulscorchEdict) skill).getWillAct().clear();

        }
        }

        this.setAtkMagnification(this.getAtkMagnification()*(1+soulscorch*0.2));
        randomMag=randomMag*(1+soulscorch*0.2);
        for (LivingThing livingThing:enemies){
            System.out.print(user.getName() + "攻击了" + livingThing.getName());
            user.makeDamage(livingThing, this);
        }
        this.setAtkMagnification(randomMag);
        for (int i=0;i<=3;i++){
            Collections.shuffle(enemies);
            user.makeDamage(enemies.getFirst(),this);
        }

        this.setAtkMagnification(0.4);

    }

}
