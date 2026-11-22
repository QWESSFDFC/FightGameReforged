package cn.gfhnv.game.entity;

import cn.gfhnv.game.damage.DamageType;

/**
 * 一条「按伤害类型」的增伤：伤害类型 + 平值 + 来源。
 * <p>
 * 与 {@link ElementValue}（按元素）分开住：元素增伤和“只对追加攻击”的增伤是两条不同维度，
 * 不能混在同一个列表里。{@code damageType == null} 表示对所有伤害类型生效（兼容用），
 * 实际内容通常都带具体类型。
 *
 * @param damageType 伤害类型；{@code null} = 所有类型
 * @param amount     平值（{@code 0.8} = +80%）
 * @param source     来源对象（按身份比较；效果用 {@code this}）
 * @author AI（DeepSeek）生成
 */
public record DamageTypeValue(DamageType damageType, double amount, Object source) {
}
