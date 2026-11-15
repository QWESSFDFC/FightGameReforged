package cn.gfhnv.game.entity;

/**
 * 一条「属性加成」作用的属性。
 * <p>
 * 每个常量对应一条<b>派生公式</b>（见 {@code LivingThing#getAttack()} 等）：
 * {@code 最终值 = 基础值 × (1 + Σpercent) + Σamount}。
 * 条目本身住在 {@code AttributeProfile#propertyEnhances}，带<b>来源</b>，按来源摘除。
 * <p>
 * ⚠️ 设计稿里的 {@code BASE_XX}（改<b>基础值</b>的那种）**暂未实现** ——
 * 现在没有任何机制需要它，加了就是死代码；等真需要时再加常量与"读基础值时叠加"的那层。
 *
 * @author AI（DeepSeek）生成；设计稿作者：仓库作者
 */
public enum PropertyEnhanceType {
    /**
     * 攻击力（{@code LivingThing#getAttack()}）。
     */
    ATTACK,
    /**
     * 防御力（{@code LivingThing#getDefence()}）。
     */
    DEFENCE,
    /**
     * 速度（{@code LivingThing#getSpeed()}）。
     */
    SPEED,
    /**
     * 生命上限（{@code LivingThing#getHpMax()}）。
     */
    MAX_HP,
    /**
     * 暴击率（{@code LivingThing#getCriticalRate()}）。
     */
    CRITICAL_RATE,
    /**
     * 暴击伤害（{@code LivingThing#getCriticalDamage()}）。
     */
    CRITICAL_DAMAGE,
    /**
     * 受到的治疗量加成（{@code LivingThing#heal(long)}）。
     */
    HEAL
}
