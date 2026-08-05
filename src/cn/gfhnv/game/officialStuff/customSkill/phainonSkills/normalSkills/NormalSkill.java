package cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEntity.players.Phainon;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.mana.Mana;

import java.util.List;

public class NormalSkill extends Skill {

    public NormalSkill() {
        super("战技:黎明创世,地辟天开", "description", 0, 3, 0, 3);
        this.setCoolDown(0);
        this.setConsumedMana(new Mana(120, ElementSort.FIRE));
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        if (user instanceof Phainon) {
            ((Phainon) user).setCoreflame(Math.min(((Phainon) user).getCoreflame_max(), ((Phainon) user).getCoreflame() + 2));
            System.out.println(user.getName() + "获得了两个火种");
        }
        for (LivingThing livingThing : enemies) {
            System.out.print(user.getName() + "攻击了" + livingThing.getName());
            user.makeDamage(livingThing, this);

        }
    }
}
