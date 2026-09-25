package cn.gfhnv.game.officialStuff.customSkill.flameReaverSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEffect.flameReaverEffects.Erosion;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;

import java.util.ArrayList;
import java.util.List;

/**
 * 【共祭 · 将尽的命数】—— 残破容器的招式。
 * <p>
 * 官方：对我方全体造成<b>少量</b>伤害，被击中目标降低生命值的一部分转化为【侵蚀】。
 * 侵蚀强度同样按"这一击打掉了目标多少血"折算（见 {@link Erosion}）。
 *
 * @author AI（DeepSeek）生成
 */
public class SacrificeFateDrawsNear extends FlameReaverSkill {

    /**
     * 侵蚀持续回合数。
     */
    private static final int EROSION_LAST_TIME = 2;

    /**
     * 侵蚀强度下限。
     */
    private static final double MIN_EROSION_RATE = 0.03;

    /**
     * 侵蚀强度上限。
     */
    private static final double MAX_EROSION_RATE = 0.2;

    /**
     * 攻击力倍率。
     * <p>
     * 标尺见 {@code CloudOfDeath}（换算常数：打白厄每 1.0 倍率 ≈ 923 伤害）。
     * 容器打全体、又是"少量"：{@code 0.1 × 923 ≈ 92}。
     */
    private static final double ATK_MAGNIFICATION = 0.1;

    /**
     * 构造技能：全体目标（相对于 BOSS 本体再低一档的"少量"）。
     */
    public SacrificeFateDrawsNear() {
        super("共祭 · 将尽的命数", "对我方全体造成少量火属性伤害，被击中目标降低生命值的一部分转化为【侵蚀】。",
                ATK_MAGNIFICATION, -1);
    }

    /**
     * 复制构造器。
     *
     * @param other 被复制的技能
     */
    public SacrificeFateDrawsNear(SacrificeFateDrawsNear other) {
        super(other);
    }

    @Override
    public Skill copy() {
        return new SacrificeFateDrawsNear(this);
    }

    @Override
    public boolean canUse(Fight fight, LivingThing user, List<LivingThing> enemies) {
        return super.canUse(fight, user, enemies) && !fightingSideOf(fight, user).isEmpty();
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        List<Long> hpBefore = new ArrayList<>();
        if (enemies != null) {
            for (LivingThing target : enemies) {
                hpBefore.add(target == null ? 0L : target.getHp());
            }
        }
        attackAllTargets(user, enemies);
        if (enemies == null) {
            return;
        }
        for (int i = 0; i < enemies.size(); i++) {
            LivingThing target = enemies.get(i);
            if (target == null || !target.isAlive()) {
                continue;
            }
            long lost = Math.max(0, hpBefore.get(i) - target.getHp());
            double rate = target.getHpMax() > 0 ? (double) lost / target.getHpMax() : MIN_EROSION_RATE;
            rate = Math.max(MIN_EROSION_RATE, Math.min(MAX_EROSION_RATE, rate));
            target.addEffect(new Erosion(rate, EROSION_LAST_TIME).setOrigin(user.getUUID()));
            System.out.println(target.getName() + "感染了【侵蚀】（每回合流失已损失生命值的 "
                    + Math.round(rate * 100) + "%，持续 " + EROSION_LAST_TIME + " 回合）");
        }
    }
}
