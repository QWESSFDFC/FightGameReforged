package cn.gfhnv.game.officialStuff.customSkill.flameReaverSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEffect.flameReaverEffects.SacrificeRite;
import cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver;
import cn.gfhnv.game.officialStuff.customEntity.summons.BrokenContainer;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;

import java.util.List;

/**
 * 【相混的道途】—— 强化招式。
 * <p>
 * 官方：使【残破容器】进入【共祭】状态，在数次行动内施放【幽冥的悼念】。
 * <p>
 * 展开：处于【共祭】的容器会被盗火行者<b>一同带着攻击</b>，随后被<b>吸收</b>
 * （按【苦痛缠绕】回血 + 获得【灾难之力】）—— 见 {@code SacrificeOfTheLost}。
 * 所以这一招是"把容器变成可回收资产"的开关：玩家要么在吸收前把它们打掉
 * （BOSS 收不回血、也拿不到灾难之力），要么吃下这一轮集火。
 * <p>
 * 本项目实现成：给场上所有活着的残破容器挂上 {@link SacrificeRite} 效果
 * （origin = 盗火行者的 UUID）；场上还没有容器时改为开一个"共祭窗口"，
 * 窗口内新召唤的容器直接进入共祭。
 *
 * @author AI（DeepSeek）生成
 */
public class TangledPathsOfMourning extends FlameReaverSkill {

    /**
     * 场上还没有容器时，开一个这么长的"共祭窗口"（按回合算）：
     * 窗口内召唤出来的容器直接进入【共祭】。
     */
    private static final int SACRIFICE_WINDOW_TURNS = 2;

    /**
     * 构造技能：作用于自身（不造成伤害）。
     */
    public TangledPathsOfMourning() {
        super("相混的道途", "使【残破容器】进入【共祭】状态，在数次行动内施放【幽冥的悼念】。", 0, 0);
    }

    /**
     * 复制构造器。
     *
     * @param other 被复制的技能
     */
    public TangledPathsOfMourning(TangledPathsOfMourning other) {
        super(other);
    }

    @Override
    public Skill copy() {
        return new TangledPathsOfMourning(this);
    }

    /**
     * <b>永远放得出来</b>（不设前提条件）。
     * <p>
     * 官方这一招是无条件施放的强化技；更重要的是：如果给它加"场上必须有容器"的前提，
     * 玩家一清场它就永远落空，日志会变成"「相混的道途」还用不了 → 顺延"，
     * 下一招【幽冥的悼念】也跟着落空（没有共祭容器可收），连着两招白费。
     * <p>
     * 场上还没有容器时，本技能会立一个 {@link #SACRIFICE_WINDOW_TURNS 共祭窗口}：
     * 之后召唤出来的容器直接算处于【共祭】。
     */
    @Override
    public boolean canUse(Fight fight, LivingThing user) {
        return true;
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
        System.out.println(reaver.getName() + "施放了【相混的道途】");
        List<BrokenContainer> containers = reaver.getAliveContainers(fight);
        if (containers.isEmpty()) {
            // 还没有容器：开一个共祭窗口，等召唤出来的容器自动进入共祭
            reaver.openSacrificeWindow(SACRIFICE_WINDOW_TURNS);
            return;
        }
        for (BrokenContainer container : containers) {
            container.enterSacrifice(SacrificeRite.DEFAULT_LAST_TIME, reaver.getUUID());
        }
        // 这里只负责"上共祭"。协同攻击与吸收是下一步【幽冥的悼念】的事 ——
        // 官方顺序的第 3 步和第 4 步是<b>两个回合</b>，中间隔着玩家一个回合，
        // 玩家可以趁机把共祭容器打掉让第 4 步落空。合并成一个回合会把这个博弈抹掉。
    }
}
