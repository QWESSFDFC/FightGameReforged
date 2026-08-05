package cn.gfhnv.game.officialStuff.customSkill.universalSkill;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.DamageEnhanceEffect;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.mana.Mana;

import java.util.List;

public class GunShoot extends Skill {

    public GunShoot() {
        super("枪射击", "射出多发子弹.伤害基于攻击力.1冷却", 0, 7.5, 0, 2);
        this.setCoolDown(1);
        this.setConsumedMana(new Mana(10, ElementSort.UNIVERSAL));
    }


    @Override
    public Skill copy() {
        return new GunShoot();
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        user.addEffect(new DamageEnhanceEffect(2, 1).setOrigin("gunShoot"));
        for (LivingThing livingThing : enemies) {
            System.out.print(user.getName() + "攻击了" + livingThing.getName());
            user.makeDamage(livingThing, this);
        }
    }
}
