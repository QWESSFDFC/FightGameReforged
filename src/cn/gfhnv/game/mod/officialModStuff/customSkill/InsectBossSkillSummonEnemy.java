package cn.gfhnv.game.mod.officialModStuff.customSkill;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entity.skill.Skill;
import cn.gfhnv.game.mod.officialModStuff.customEntity.CommonInsect;
import cn.gfhnv.game.system.fight.Fight;

public class InsectBossSkillSummonEnemy extends Skill {

    public InsectBossSkillSummonEnemy() {
        super("分裂", "分裂其他虫子,没有数量上限", 0, 0, 0);
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user) {
        System.out.println("Boss分裂了");
        if (fight.getEnemiesList().contains(user)) {
            fight.getEnemiesList().add(new CommonInsect(user.getLevel()));
            return;
        }
        fight.getFighterList().add(new CommonInsect(user.getLevel()));
    }
}
