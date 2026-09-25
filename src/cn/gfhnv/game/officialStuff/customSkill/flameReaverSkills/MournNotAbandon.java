package cn.gfhnv.game.officialStuff.customSkill.flameReaverSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;

import java.util.ArrayList;
import java.util.List;

/**
 * 【莫因舍弃而哭泣】—— 二阶段的大招。
 * <p>
 * 官方：对我方全体造成伤害，<b>消耗所有【灾难之力】额外造成数次少量伤害</b>。
 * <p>
 * 与阶段一的【却是必要的苦难】<b>顺序相反</b>：
 * <ul>
 *     <li>阶段一：先按层数打多段 → <b>再</b>补一发全体；</li>
 *     <li>阶段二（本招式）：先打一发全体 → <b>再</b>按层数追加多段。</li>
 * </ul>
 * 所以阶段二的这一记"起手就很重"，层数越多后面的追加越疼。
 * 与阶段一相同，<b>没有灾难之力也放得出来</b>（只有那一记全体起手）。
 *
 * @author AI（DeepSeek）生成
 */
public class MournNotAbandon extends FlameReaverSkill {

    /**
     * 起手那一记全体伤害的倍率。
     */
    private static final double OPENING_MAGNIFICATION = 1.2;

    /**
     * 每一层【灾难之力】换来的追加段数倍率。
     */
    private static final double DAMAGE_PER_STACK_MAGNIFICATION = 1.0;

    /**
     * 构造技能：全体目标，倍率在 {@link #comeToEffect} 里动态设置。
     */
    public MournNotAbandon() {
        super("莫因舍弃而哭泣", "对我方全体造成伤害，消耗所有【灾难之力】额外造成数次少量伤害。", 1, -1);
    }

    /**
     * 复制构造器。
     *
     * @param other 被复制的技能
     */
    public MournNotAbandon(MournNotAbandon other) {
        super(other);
    }

    @Override
    public Skill copy() {
        return new MournNotAbandon(this);
    }

    /**
     * 二阶段才放得出来（阶段一的轮转表里也没有它）。
     */
    @Override
    public boolean canUse(Fight fight, LivingThing user, List<LivingThing> enemies) {
        if (!super.canUse(fight, user, enemies) || fight.getOpponentList(user).isEmpty()) {
            return false;
        }
        return user instanceof FlameReaver reaver && reaver.isPhaseTwo();
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        if (!(user instanceof FlameReaver reaver)) {
            attackAllTargets(user, enemies);
            return;
        }
        // 蓄力结束了
        reaver.setCharging(false);
        int stacks = reaver.consumeDisasterPower(reaver.getDisasterPower());

        // 第一段：起手全体伤害
        this.setAtkMagnification(OPENING_MAGNIFICATION);
        System.out.println(reaver.getName() + "施放【莫因舍弃而哭泣】，消耗 " + stacks + " 层【灾难之力】");
        System.out.println("  ——起手全体——");
        attackAllTargets(user, fightingSideOf(fight, user));

        // 第二段：按消耗的层数追加多段
        this.setAtkMagnification(DAMAGE_PER_STACK_MAGNIFICATION);
        for (int i = 0; i < stacks; i++) {
            List<LivingThing> targets = new ArrayList<>(fightingSideOf(fight, user));
            if (targets.isEmpty()) {
                break;
            }
            System.out.println("  ——追加第 " + (i + 1) + " 段——");
            attackAllTargets(user, targets);
        }

        // 复位，避免同一实例下次带着上次的倍率出手
        this.setAtkMagnification(1);
    }
}
