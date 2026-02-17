package cn.gfhnv.game.mod.officialModStuff.customSkill;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entity.skill.Skill;
import cn.gfhnv.game.mod.officialModStuff.customEffect.DamageEnhanceEffect;
import cn.gfhnv.game.system.fight.Fight;

import java.util.Random;

public class CommonAttack extends Skill {
    public CommonAttack(double hpMagnification, double atkMagnification, double defMagnification) {
        super("普通攻击", "最普通的攻击", hpMagnification, atkMagnification, defMagnification);
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user) {
        user.addEffect(new DamageEnhanceEffect(1, 1));
        if (fight.getEnemiesList().contains(user)) {
            Random rand = new Random();
            LivingThing[] livingThings = fight.getFighterList().toArray(new LivingThing[0]);
            LivingThing target = livingThings[rand.nextInt(livingThings.length)];
            System.out.println(user.getName() + "攻击了" + target.getName());
            user.makeDamage(target, this);
            return;
        }
        Random rand = new Random();
        LivingThing[] livingThings = fight.getEnemiesList().toArray(new LivingThing[0]);
        LivingThing target = livingThings[rand.nextInt(livingThings.length)];
        System.out.println(user.getName() + "攻击了" + target.getName());
        user.makeDamage(target, this);
        return;
    }
}
