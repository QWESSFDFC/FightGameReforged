package cn.gfhnv.game.officialStuff.customSkill.universalSkill;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.Frozen;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.mana.Mana;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;

import java.util.List;

public class Freeze extends Skill {
    public Freeze() {
        super("冰冻", "冰冻目标1回合", 0, 7.5, 0, 2);
        this.setCoolDown(2);
        this.setConsumedMana(new Mana(10, ElementSort.WATER));
        this.getTags().put(TagType.ATTACK, new Tag(1));
    }

    @Override
    public Skill copy() {
        return new Freeze();
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        System.out.println(user.getName() + "冰冻了" + enemies.get(0).getName());
        if (enemies.size() == 2) System.out.printf(user.getName() + "冰冻了" + enemies.get(1).getName());
        for (LivingThing livingThing : enemies) {
            livingThing.addEffect(new Frozen().setOrigin(user.getUUID()));
        }
    }
}
