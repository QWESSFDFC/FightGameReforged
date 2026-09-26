package cn.gfhnv.game.officialStuff.customSkill.flameReaverSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEffect.flameReaverEffects.Erosion;
import cn.gfhnv.game.officialStuff.customEntity.summons.BrokenContainer;
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
        // 共祭那一轮打 BOSS 指定的共同目标（不是控制器随机挑的），其余情况照旧
        enemies = jointTargetsOr(enemies, user);
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
        String origin = erosionOriginOf(user);
        for (int i = 0; i < enemies.size(); i++) {
            LivingThing target = enemies.get(i);
            if (target == null || !target.isAlive()) {
                continue;
            }
            long lost = Math.max(0, hpBefore.get(i) - target.getHp());
            double rate = target.getHpMax() > 0 ? (double) lost / target.getHpMax() : MIN_EROSION_RATE;
            rate = Math.max(MIN_EROSION_RATE, Math.min(MAX_EROSION_RATE, rate));
            // 按目标合并：同一个目标身上只留一条【侵蚀】，重复击中只刷新（见 Erosion#applyTo）
            Erosion applied = Erosion.applyTo(target, rate, EROSION_LAST_TIME, origin);
            System.out.println(target.getNameWithSide() + "感染了【侵蚀】（每回合流失已损失生命值的 "
                    + Math.round(applied.getRate() * 100) + "%，持续 " + applied.getLastTime() + " 回合）");
        }
    }

    /**
     * 【侵蚀】的 origin：<b>记施加者的 UUID</b>（{@link cn.gfhnv.game.effect.Effect#equals}
     * 用 id + origin 判等，同一个来源重复施加会合并刷新，而不是叠出第二条）。
     * <p>
     * 共祭是"容器与盗火行者<b>一同</b>施放"（官方原文），出手的虽然是容器，
     * 但这一击算 BOSS 给的 —— 所以这里取容器回引的 BOSS UUID。
     * 直接记容器自己的 UUID 会让每只共祭容器各叠一条【侵蚀】：同一个目标一回合跳 5 次
     * （实测踩过，日志里连续五行 `【侵蚀】…`）。
     *
     * @param user 施放者（共祭时是【残破容器】/【完整容器】）
     * @return 用于 origin 的 UUID
     */
    private static String erosionOriginOf(LivingThing user) {
        if (user instanceof BrokenContainer container && container.getOwner() != null) {
            return container.getOwner().getUUID();
        }
        return user.getUUID();
    }
}
