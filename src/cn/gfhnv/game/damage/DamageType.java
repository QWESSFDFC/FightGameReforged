package cn.gfhnv.game.damage;

/**
 * 伤害类型。一次伤害可以带<b>多个</b>类型（见 {@link Damage#getDamageTypes()}），
 * 计算伤害加成时<b>全都生效</b>。
 * <p>
 * 由<b>技能在造成伤害时显式传入</b>，不做按类自动推断 ——
 * 同一个技能类在不同形态/来源下可能算不同类型，猜错会静默影响伤害加成：
 *
 * <pre>{@code
 * user.makeDamage(target, this, DamageType.CommonAttack);
 * }</pre>
 *
 * @author AI（DeepSeek）生成；设计稿作者：仓库作者
 */
public enum DamageType {
    /**
     * 持续伤害：每回合结算一次、通常由效果驱动（例如【侵蚀】）。
     */
    DOT,
    /**
     * 普通攻击。
     */
    CommonAttack,
    /**
     * 战技：主动回合里放的非普攻、非终结技招式（BOSS 的招式、模组的主动技也算）。
     */
    SpecialSkill,
    /**
     * 终结技（大招）。
     */
    UltimateAttack,
    /**
     * 追加攻击：反击、追打、"额外 N 段"这类不是主动回合里那次行动的伤害。
     */
    AdditionAttack,
    /**
     * 系统伤害：不来自任何技能的伤害（消耗生命值、剧本/命令直接扣血等）。
     */
    SystemDamage
}
