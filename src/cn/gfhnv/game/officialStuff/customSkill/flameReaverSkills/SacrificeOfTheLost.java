package cn.gfhnv.game.officialStuff.customSkill.flameReaverSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver;
import cn.gfhnv.game.officialStuff.customEntity.summons.BrokenContainer;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;

import java.util.List;

/**
 * 【幽冥的悼念】—— 收束招式（官方阶段一第四招）。
 * <p>
 * 官方：与【残破容器】一同施放【亡死的黑云】或【将尽的命数】，随后吸收【残破容器】
 * 治疗自身，并获得【灾难之力】。
 * <p>
 * 本项目：让场上处于【共祭】的容器先各自出手（它们的技能表里就是
 * 【共祭 · 亡死的黑云】与【共祭 · 将尽的命数】），然后逐个吸收 ——
 * 按【苦痛缠绕】回血 + 获得【灾难之力】+ 容器离场。
 * <p>
 * 它<b>单独占一个回合</b>（官方顺序的第 4 步），不要把它和【相混的道途】合并：
 * 第 3 步只负责"上共祭"，第 4 步才是"协同攻击 + 吸收"，中间隔着玩家一个回合 ——
 * 玩家可以趁机把共祭容器打掉，让这一招落空（这就是清场收益的来源）。
 *
 * @author AI（DeepSeek）生成
 */
public class SacrificeOfTheLost extends FlameReaverSkill {

    /**
     * 构造技能：作用于自身（不造成伤害）。
     */
    public SacrificeOfTheLost() {
        super("幽冥的悼念", "与【残破容器】一同施放亡死的黑云或将尽的命数，随后吸收它们治疗自身并获得【灾难之力】。", 0, 0);
    }

    /**
     * 复制构造器。
     *
     * @param other 被复制的技能
     */
    public SacrificeOfTheLost(SacrificeOfTheLost other) {
        super(other);
    }

    @Override
    public Skill copy() {
        return new SacrificeOfTheLost(this);
    }

    /**
     * 只有场上存在【共祭】容器时才放得出来；没有就顺延到下一招。
     * <p>
     * 正常情况下轮转第 3 步【相混的道途】刚给容器上过共祭（或处在"共祭窗口"里，
     * 新召唤的容器直接算共祭），所以这一步通常都有活干，不会白占回合。
     */
    @Override
    public boolean canUse(Fight fight, LivingThing user) {
        return user instanceof FlameReaver reaver && !reaver.getSacrificedContainers(fight).isEmpty();
    }

    @Override
    public boolean canUse(Fight fight, LivingThing user, List<LivingThing> enemies) {
        return super.canUse(fight, user, enemies) && canUse(fight, user);
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user) {
        if (!(user instanceof FlameReaver reaver)) {
            return;
        }
        List<BrokenContainer> sacrificed = reaver.getSacrificedContainers(fight);
        if (sacrificed.isEmpty()) {
            System.out.println(reaver.getName() + "想施放【幽冥的悼念】，但没有处于共祭的【残破容器】");
            return;
        }
        System.out.println(reaver.getName() + "施放了【幽冥的悼念】");
        reaver.absorbSacrificedContainers(fight);
    }
}
