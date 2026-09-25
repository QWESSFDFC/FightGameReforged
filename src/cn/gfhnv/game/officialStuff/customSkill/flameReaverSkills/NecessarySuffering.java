package cn.gfhnv.game.officialStuff.customSkill.flameReaverSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;

import java.util.List;

/**
 * 【却是必要的苦难】—— 盗火行者的大招。
 * <p>
 * 官方：<b>消耗所有【灾难之力】</b>对我方全体造成数次<b>少量</b>伤害，
 * <b>随后</b>对我方全体造成<b>少量</b>伤害。
 * 层数越多，前面的段数越多 —— 这就是"灾难之力"的兑现方式。
 * <p>
 * 本项目：段数 = 消耗掉的层数（不设上限，按用户决定），每段
 * {@value #DAMAGE_PER_STACK_MAGNIFICATION} 倍攻击力，
 * 收尾那一击 {@value #FINISH_MAGNIFICATION} 倍。
 * <p>
 * 没有灾难之力时也放得出来（只是只有收尾那一击），否则轮转会被卡住。
 *
 * @author AI（DeepSeek）生成
 */
public class NecessarySuffering extends FlameReaverSkill {

    /**
     * 每一层【灾难之力】换来的那一段伤害的倍率。
     * <p>
     * <b>标定规则（改任何倍率前必读）</b>：
     * <pre>
     * 换算常数（实测）：打白厄时，每 1.0 技能倍率 ≈ 923 点伤害
     * 依据：亡死的黑云 4.3 倍率实测打出 3969 点（3969 / 4.3 = 923）
     * 这个常数已经把防御系数、增伤、抗性都吃进去了，改倍率直接乘它即可
     * </pre>
     * <b>标尺（用户给的，按原游戏手感）</b>：普通攻击 200~300，600~1000 已属"怪物高伤害"。
     * 大招落高伤档：{@code 1.0 × 923 ≈ 923}。
     */
    private static final double DAMAGE_PER_STACK_MAGNIFICATION = 1.0;

    /**
     * 收尾那一击的倍率（比单段略重，保持"按层数多段 + 一记收尾"的层次）。
     */
    private static final double FINISH_MAGNIFICATION = 1.2;

    /**
     * 构造技能：全体目标，倍率在 {@link #comeToEffect} 里按段数动态设置。
     */
    public NecessarySuffering() {
        super("却是必要的苦难", "消耗所有【灾难之力】对我方全体造成数次少量伤害，随后对我方全体造成少量伤害。", 1, -1);
    }

    /**
     * 复制构造器。
     *
     * @param other 被复制的技能
     */
    public NecessarySuffering(NecessarySuffering other) {
        super(other);
    }

    @Override
    public Skill copy() {
        return new NecessarySuffering(this);
    }

    /**
     * 只要对面还有人就放得出来。
     * <p>
     * <b>不要给这一招加"灾难之力不足就不放"的闸门</b>（曾经加过，被用户否掉）：
     * 官方设定里这一招就是<b>可以空放</b>的 —— 0 层时只有收尾那一击。
     * 玩家清召唤物换来的收益，是"这一招打得轻"，而不是"这一招被延后"。
     */
    @Override
    public boolean canUse(Fight fight, LivingThing user, List<LivingThing> enemies) {
        return super.canUse(fight, user, enemies) && !fightingSideOf(fight, user).isEmpty();
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        if (!(user instanceof FlameReaver reaver)) {
            attackAllTargets(user, enemies);
            return;
        }
        int stacks = reaver.consumeDisasterPower(reaver.getDisasterPower());
        System.out.println(reaver.getName() + "施放【却是必要的苦难】，消耗 " + stacks + " 层【灾难之力】");

        // 第一段：按消耗的层数打多段少量伤害
        this.setAtkMagnification(DAMAGE_PER_STACK_MAGNIFICATION);
        for (int i = 0; i < stacks; i++) {
            List<LivingThing> targets = fightingSideOf(fight, user);
            if (targets.isEmpty()) {
                break;
            }
            System.out.println("  ——第 " + (i + 1) + " 段——");
            attackAllTargets(user, targets);
        }

        // 第二段：随后补一次全体伤害
        this.setAtkMagnification(FINISH_MAGNIFICATION);
        System.out.println("  ——收尾一击——");
        attackAllTargets(user, fightingSideOf(fight, user));

        // 复位，避免同一个技能实例下次带着上次的倍率出手（副本场景下实例会被复用）
        this.setAtkMagnification(1);
    }
}
