package cn.gfhnv.game.interfaces;

/**
 * <b>给己方提供"追加攻击伤害提高"的光环</b>（2026-11-21 第 82 批）。
 * <p>
 * 例：千冶·刃二魂「心若死灰，而炉火未熄」—— 我方目标造成的追加攻击伤害 +75%。
 * 由实体实现；伤害计算通过 {@link cn.gfhnv.game.entity.LivingThing#getTeamAdditionAttackDamageBonus()}
 * 读取"己方最强的那一份"（同类光环不叠乘，取最大）。
 *
 * @author AI（DeepSeek）生成
 */
public interface IAdditionAttackBonus {

    /**
     * @return 追加攻击伤害加成（0.75 = +75%；没有加成返回 0）
     */
    double getAdditionAttackDamageBonus();
}
