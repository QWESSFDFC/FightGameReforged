package cn.gfhnv.game.officialStuff.customEntity.monsters;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entityController.UniversalController;
import cn.gfhnv.game.officialStuff.customSkill.insectBossSkills.InsectBossSkillSummonEnemy;
import cn.gfhnv.game.officialStuff.customSkill.universalSkill.CommonAttack;
import cn.gfhnv.game.skill.Skill;

import java.util.ArrayList;
import java.util.List;

import static cn.gfhnv.game.system.ElementSort.METAL;

public class InsectBoss extends LivingThing {
    public InsectBoss(long l) {
        super("虫皇", "insectBoss", -0.1, 0.2, 0.8, 0.2, 0.3, 110, l, "insect", 4000, 7, 20, METAL);
        List<Skill> skills = new ArrayList<>();
        this.setDescription("这是虫皇.可以普通攻击和分裂出普通虫子,无上限");
        skills.add(new InsectBossSkillSummonEnemy());
        skills.add(new CommonAttack(0, 0.9, 0, 2));
        this.setController(new UniversalController(skills, this));
        this.setMass(1250);
        this.getInventory().addSlot(63);
    }


    @Override
    public LivingThing copy() {
        return new InsectBoss(this.getLevel());
    }

}
