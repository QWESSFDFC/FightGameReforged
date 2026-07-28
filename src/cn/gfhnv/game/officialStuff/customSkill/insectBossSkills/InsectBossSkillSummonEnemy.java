package cn.gfhnv.game.officialStuff.customSkill.insectBossSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.HealthRestoreEffect;
import cn.gfhnv.game.officialStuff.customEntity.monsters.CommonInsect;
import cn.gfhnv.game.officialStuff.customEntity.monsters.IceInsect;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.mana.Mana;

public class InsectBossSkillSummonEnemy extends Skill {

    public InsectBossSkillSummonEnemy() {
        super("分裂", "分裂其他虫子,没有数量上限.冷却10", 0, 0, 0, 0);
        this.setCoolDown(10);
        this.setConsumedMana(new Mana(100, ElementSort.UNIVERSAL));
    }


    @Override
    public Skill copy() {
        return new InsectBossSkillSummonEnemy();
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user) {
        System.out.println("Boss分裂了");
        user.addEffect(new HealthRestoreEffect(2, 1));
        LivingThing t=new IceInsect(user.getLevel());
        if (Math.random()>=0.5) {t=new CommonInsect(user.getLevel());}
        if (fight.getEnemiesList().contains(user)) {
            fight.addEnemy(t);
            return;
        }
        fight.addFighter(t);
    }
}
