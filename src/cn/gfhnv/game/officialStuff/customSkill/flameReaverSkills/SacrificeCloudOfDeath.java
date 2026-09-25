package cn.gfhnv.game.officialStuff.customSkill.flameReaverSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;

import java.util.List;

/**
 * 【共祭 · 亡死的黑云】—— 残破容器的招式。
 * <p>
 * 官方：对指定我方单体<b>及其相邻目标</b>造成<b>少量</b>伤害（相邻 → 本项目映射为目标数 3）。
 * 它只在【残破容器】与盗火行者一同攻击（{@code 幽冥的悼念} 的结算）时出手，
 * 所以倍率比 BOSS 本体低一档。
 *
 * @author AI（DeepSeek）生成
 */
public class SacrificeCloudOfDeath extends FlameReaverSkill {

    /**
     * 攻击力倍率。
     * <p>
     * 标尺见 {@code CloudOfDeath}（普通攻击 200~300；换算常数：打白厄每 1.0 倍率 ≈ 923 伤害）。
     * 容器是"少量"伤害，再低一档：{@code 0.12 × 923 ≈ 110}。
     */
    private static final double ATK_MAGNIFICATION = 0.12;

    /**
     * 构造技能：3 目标（相对于 BOSS 本体再低一档的"少量"）。
     */
    public SacrificeCloudOfDeath() {
        super("共祭 · 亡死的黑云", "对指定我方单体及其相邻目标造成少量火属性伤害。",
                ATK_MAGNIFICATION, 3);
    }

    /**
     * 复制构造器。
     *
     * @param other 被复制的技能
     */
    public SacrificeCloudOfDeath(SacrificeCloudOfDeath other) {
        super(other);
    }

    @Override
    public Skill copy() {
        return new SacrificeCloudOfDeath(this);
    }

    @Override
    public boolean canUse(Fight fight, LivingThing user, List<LivingThing> enemies) {
        return super.canUse(fight, user, enemies) && !fightingSideOf(fight, user).isEmpty();
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        attackAllTargets(user, enemies);
    }
}
