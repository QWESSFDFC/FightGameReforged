package cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.DamageEnhanceEffect;
import cn.gfhnv.game.officialStuff.customEntity.players.Phainon;
import cn.gfhnv.game.officialStuff.customSkill.universalSkill.CommonAttack;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;

import java.util.List;

public class AwakenCommonAttack extends Skill {
    public AwakenCommonAttack() {
        super("普通攻击•创生•血棘渡亡", "最普通的攻击.无发动条件.获得2点【毁伤】",0, 2.5, 0, 3);
        this.setCoolDown(0);
    }

    public AwakenCommonAttack(AwakenCommonAttack commonAttack) {
        super(commonAttack);
    }

    @Override
    public Skill copy() {
        return new AwakenCommonAttack(this);
    }


    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        for (LivingThing livingThing : enemies) {
            System.out.print(user.getName() + "攻击了" + livingThing.getName());
            user.makeDamage(livingThing, this);
        }
        if (user instanceof Phainon){
            ((Phainon) user).addScourge(2);
        }
    }
}
