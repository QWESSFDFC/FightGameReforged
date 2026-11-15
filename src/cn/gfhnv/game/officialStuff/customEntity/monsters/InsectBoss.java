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
     * 出厂生命上限。显式给值，不再靠等级成长系数去凑。
     * <p>
     * <b>2026-11-14 第 52 批：不再走规则表</b>（与盗火行者同理：同一个值原来有两个入口）。
     * 要改一只模板的血量，用 {@code EntityData.json} 的 {@code derived.baseHpMax}。
     */
    public static final long DEFAULT_BASE_HP = 80000L;

    public InsectBoss(long l) {
        super("虫皇", "insectBoss", -0.1, 0.2, 0.8, 0.2, 0.3, 110, l, "insect", 4000, 7, 20, METAL);
        this.setHpMax(DEFAULT_BASE_HP);
        this.setHp(DEFAULT_BASE_HP);
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
