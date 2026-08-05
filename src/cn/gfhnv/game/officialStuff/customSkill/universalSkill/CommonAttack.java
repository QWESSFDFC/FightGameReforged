package cn.gfhnv.game.officialStuff.customSkill.universalSkill;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.DamageEnhanceEffect;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;

import java.util.List;

public class CommonAttack extends Skill {
    public CommonAttack(double hpMagnification, double atkMagnification, double defMagnification, int aim) {
        super("普通攻击", "最普通的攻击.无发动条件", hpMagnification, atkMagnification, defMagnification, aim);
        this.setCoolDown(0);
    }

    public CommonAttack(CommonAttack commonAttack) {
        super(commonAttack);
    }

    @Override
    public Skill copy() {
        return new CommonAttack(this);
    }


    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        user.addEffect(new DamageEnhanceEffect(1, 1).setOrigin("commonAttack"));
        for (LivingThing livingThing : enemies) {
            System.out.print(user.getName() + "攻击了" + livingThing.getName());
            user.makeDamage(livingThing, this);
        }
    }
}
