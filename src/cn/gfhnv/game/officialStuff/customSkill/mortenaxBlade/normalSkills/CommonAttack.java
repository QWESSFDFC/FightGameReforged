package cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.normalSkills;

import cn.gfhnv.game.entity.LivingThing;

import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;

import java.util.List;

public class CommonAttack extends cn.gfhnv.game.officialStuff.customSkill.universalSkill.CommonAttack {

    public CommonAttack() {
        super(0.5, 0, 0, 1);
    }

    @Override
    public Skill copy() {
        return new cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.normalSkills.CommonAttack();
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        super.comeToEffect(fight, user, enemies);

    }
}
