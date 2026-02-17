package cn.gfhnv.game.mod.officialModStuff.customEntity;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entity.entityController.UniversalController;
import cn.gfhnv.game.entity.skill.Skill;
import cn.gfhnv.game.mod.officialModStuff.customSkill.CommonAttack;
import cn.gfhnv.game.mod.officialModStuff.customSkill.InsectBossSkillSummonEnemy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static cn.gfhnv.game.system.ElementSort.METAL;

public class InsectBoss extends LivingThing {
    public InsectBoss(long l) {
        super("insectBoss", "insectBoss", 0.1, 0.2, 0.8, 0.2, 0.3, 150, l, "insect", 400, 10, 20, METAL, 1, 5, 1);
        List<Skill> skills = new ArrayList<>();
        skills.add(new InsectBossSkillSummonEnemy());
        skills.add(new CommonAttack(0, 0.9, 0));
        this.setController(new UniversalController(skills, this));
        this.setMass(BigDecimal.valueOf(1250));
        this.getInventory().addSlot(63);
    }


}
