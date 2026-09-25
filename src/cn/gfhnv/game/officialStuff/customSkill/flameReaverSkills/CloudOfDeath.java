package cn.gfhnv.game.officialStuff.customSkill.flameReaverSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver;
import cn.gfhnv.game.officialStuff.customEntity.summons.BrokenContainer;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;

import java.util.List;

/**
 * 【亡死的黑云】—— 盗火行者的常规攻击。
 * <p>
 * 官方：对指定我方单体<b>及其相邻目标</b>造成物理属性伤害（扩散）。
 * 本项目没有"相邻"概念，按用户决定<b>映射为目标数 3</b>，属性映射为火。
 * <p>
 * 同时承担官方「分离的哀痛」的前半段：<b>施放本技能时消耗生命值召唤【残破容器】</b>，
 * 消耗的生命值转成【苦痛缠绕】记账（见 {@link FlameReaver#summonContainer(Fight)}）。
 * <p>
 * <b>吸收不在这里做</b>：官方「分离的哀痛」允许本招式回收自己召唤的那批，
 * 但那会和轮转第 4 步的【幽冥的悼念】抢容器 —— 容器常在本招式里就被吃掉，
 * 第 4 步永远落空并打印"还用不了"。现在<b>吸收只由【幽冥的悼念】负责</b>，
 * 召唤批次仍然照记（{@link FlameReaver#rememberSummon}），便于以后需要时恢复。
 *
 * @author AI（DeepSeek）生成
 */
public class CloudOfDeath extends FlameReaverSkill {

    /**
     * 攻击力倍率。
     * <p>
     * <b>标尺（用户给的，按原游戏手感）</b>：普通攻击 200~300，600~1000 已属"怪物高伤害"。
     * <b>换算常数（实测，勿凭感觉改）</b>：打白厄时
     * <pre>
     * 每 1.0 倍率 ≈ 923 点伤害
     * 依据：本技能 4.3 倍率实测打出 3969 点（3969 / 4.3 = 923）
     * </pre>
     * <ul>
     *     <li>本技能 {@value #ATK_MAGNIFICATION} × 923 ≈ 277 —— 普通攻击档（200~300）；</li>
     *     <li>【将尽的命数】打全体，单体取 0.25 × 923 ≈ 230；</li>
     *     <li>【却是必要的苦难】单段 1.0 × 923 ≈ 923、收尾 1.2 × 923 ≈ 1100 —— 高伤档（600~1000）；</li>
     *     <li>容器招式再低一档（0.12 / 0.1）。</li>
     * </ul>
     */
    private static final double ATK_MAGNIFICATION = 0.3;

    /**
     * 构造技能：3 目标。
     */
    public CloudOfDeath() {
        super("亡死的黑云", "对指定我方单体及其相邻目标造成火属性伤害，并消耗生命值召唤【残破容器】。",
                ATK_MAGNIFICATION, 3);
    }

    /**
     * 复制构造器。
     *
     * @param other 被复制的技能
     */
    public CloudOfDeath(CloudOfDeath other) {
        super(other);
    }

    @Override
    public Skill copy() {
        return new CloudOfDeath(this);
    }

    @Override
    public boolean canUse(Fight fight, LivingThing user, List<LivingThing> enemies) {
        return super.canUse(fight, user, enemies) && !fightingSideOf(fight, user).isEmpty();
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> targets) {
        attackAllTargets(user, targets);
        if (user instanceof FlameReaver reaver) {
            // 按概率产出【残破容器】或【完整容器】（后者是给玩家的奖励线，见 FlameReaver#grantContainerReward）
            BrokenContainer container = reaver.summonRandomContainer(fight);
            reaver.rememberSummon(this, container);
        }
    }
}
