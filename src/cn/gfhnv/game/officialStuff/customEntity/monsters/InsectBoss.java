package cn.gfhnv.game.officialStuff.customEntity.monsters;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entityController.UniversalController;
import cn.gfhnv.game.officialStuff.customSkill.insectBossSkills.InsectBossSkillSummonEnemy;
import cn.gfhnv.game.officialStuff.customSkill.universalSkill.CommonAttack;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.configLoadingSystem.DataKeys;
import cn.gfhnv.game.system.configLoadingSystem.GameRules;

import java.util.ArrayList;
import java.util.List;

import static cn.gfhnv.game.system.ElementSort.METAL;

public class InsectBoss extends LivingThing {

    public InsectBoss(long l) {
        super("虫皇", "insectBoss", -0.1, 0.2, 0.8, 0.2, 0.3, 110, l, "insect", 4000, 7, 20, METAL);
        this.setHpMax(baseHpMax());
        this.setHp(baseHpMax());
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

    /**
     * 基础生命上限。显式给值，不再靠等级成长系数去凑
     * （成长公式是 {@code (等级-1) × 系数 + 200}，改等级会连带把血量放大到无法预期的量级）。
     * <p>
     * <b>构造时读一次</b>：它不是"公式的一部分"，而是"这个模板出厂时的血量"——
     * 想改一只<b>已经造出来</b>的虫皇的血，用 {@code EntityData.json} 的 {@code derived.hpMax}。
     * 配置键 {@code insectBoss.baseHpMax}，出厂值
     * {@value cn.gfhnv.game.system.configLoadingSystem.RuleDefaults#INSECT_BOSS_BASE_HP}。
     *
     * @return 基础生命上限
     */
    private static long baseHpMax() {
        return GameRules.getLong(DataKeys.Rule.InsectBoss.BASE_HP);
    }

    @Override
    public LivingThing copy() {
        return new InsectBoss(this);
    }

}
