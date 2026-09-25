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

    /**
     * 基础生命上限。显式给值，不再靠等级成长系数去凑
     * （成长公式是 {@code (等级-1) × 系数 + 200}，改等级会连带把血量放大到无法预期的量级）。
     */
    private static final long BASE_HP_MAX = 80000;

    public InsectBoss(long l) {
        super("虫皇", "insectBoss", -0.1, 0.2, 0.8, 0.2, 0.3, 110, l, "insect", 4000, 7, 20, METAL);
        this.setHpMax(BASE_HP_MAX);
        this.setHp(BASE_HP_MAX);
        List<Skill> skills = new ArrayList<>();
        this.setDescription("这是虫皇.可以普通攻击和分裂出普通虫子,无上限");
        skills.add(new InsectBossSkillSummonEnemy());
        skills.add(new CommonAttack(0, 1, 0, 2));
        this.setController(new UniversalController(skills, this));
        this.setMass(1250);
        this.getInventory().addSlot(63);
    }

    public InsectBoss(InsectBoss insectBoss) {
        super(insectBoss);
    }

    @Override
    public LivingThing copy() {
        return new InsectBoss(this);
    }

}
