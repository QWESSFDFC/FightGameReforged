package cn.gfhnv.game.officialStuff.customSkill.flameReaverSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEffect.flameReaverEffects.Erosion;
import cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver;
import cn.gfhnv.game.officialStuff.customEntity.summons.BrokenContainer;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;

import java.util.ArrayList;
import java.util.List;

/**
 * 【将尽的命数】—— 盗火行者的全体攻击。
 * <p>
 * 官方：对我方全体造成物理属性伤害，被击中目标<b>降低生命值的一部分转化为【侵蚀】</b>。
 * 本项目映射为火属性，侵蚀落成 {@link Erosion}。
 * <p>
 * 「降低生命值的一部分」的落地方式：<b>按这一击造成的伤害占目标生命上限的比例</b>
 * 决定侵蚀强度（见 {@link Erosion#getRate()}），最低
 * {@value #MIN_EROSION_RATE}、最高 {@value #MAX_EROSION_RATE}。
 * 也就是说"打得越狠，后续流失越快"，与官方的语义一致。
 * <p>
 * 同样承担「分离的哀痛」的前半段：施放时消耗生命值召唤【残破容器】并记账。
 *
 * @author AI（DeepSeek）生成
 */
public class FateDrawsNear extends FlameReaverSkill {

    /**
     * 侵蚀持续回合数。
     */
    private static final int EROSION_LAST_TIME = 3;

    /**
     * 侵蚀强度的下限（这一击几乎没打出伤害时也留一点）。
     */
    private static final double MIN_EROSION_RATE = 0.05;

    /**
     * 侵蚀强度的上限（防止一刀把后续流失比例堆到离谱）。
     */
    private static final double MAX_EROSION_RATE = 0.4;

    /**
     * 攻击力倍率。
     * <p>
     * 标尺同 {@link CloudOfDeath}（普通攻击 200~300；<b>换算常数：打白厄每 1.0 倍率 ≈ 923 伤害</b>）：
     * 本技能打全体，单体取低档 —— {@code 0.25 × 923 ≈ 230}。
     */
    private static final double ATK_MAGNIFICATION = 0.25;

    /**
     * 构造技能：全体目标。
     */
    public FateDrawsNear() {
        super("将尽的命数", "对我方全体造成火属性伤害，被击中目标降低生命值的一部分转化为【侵蚀】。",
                ATK_MAGNIFICATION, -1);
    }

    /**
     * 复制构造器。
     *
     * @param other 被复制的技能
     */
    public FateDrawsNear(FateDrawsNear other) {
        super(other);
    }

    @Override
    public Skill copy() {
        return new FateDrawsNear(this);
    }

    @Override
    public boolean canUse(Fight fight, LivingThing user, List<LivingThing> enemies) {
        return super.canUse(fight, user, enemies) && !fightingSideOf(fight, user).isEmpty();
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        // 吸收不在这里做（见 `CloudOfDeath` 的说明：会和【幽冥的悼念】抢容器）
        // 先记下开打前的血量，才能算出"这一击降低了多少生命值"
        List<Long> hpBefore = new ArrayList<>();
        if (enemies != null) {
            for (LivingThing target : enemies) {
                hpBefore.add(target == null ? 0L : target.getHp());
            }
        }
        attackAllTargets(user, enemies);
        if (enemies != null) {
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
        if (user instanceof FlameReaver reaver) {
            // 同上：按概率产出【残破容器】或【完整容器】
            BrokenContainer container = reaver.summonRandomContainer(fight);
            reaver.rememberSummon(this, container);
        }
    }
}
