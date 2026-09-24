package cn.gfhnv.game.interfaces;

/**
 * 「无视防御」标记：实现它的效果会削减被攻击方的有效防御。
 * <p>
 * 伤害计算只认这个接口，<b>不认识具体是哪个效果</b>
 * （官方内容里是 {@code cn.gfhnv.game.officialStuff.customEffect.universalEffects.IgnoreDefenceEffect}），
 * 所以模组自己写一个「穿甲」效果，只要实现本接口就会被算进去
 * （见 {@link cn.gfhnv.game.entity.LivingThing#getIgnoreDefencePercent()}）。
 * <p>
 * 两个值都是<b>攻击者身上</b>的、对目标防御的削减：
 * 有效防御 = {@code 目标防御 × (1 − 百分比 − 目标自身的防御削减) − 固定值}（不会小于 0）。
 *
 * @author AI（DeepSeek）生成
 */
public interface IDefenceIgnore {

    /**
     * @return 无视的防御百分比（0.5 表示无视目标 50% 的防御）
     */
    double getIgnoreDefencePercent();

    /**
     * @return 无视的防御固定值（直接从目标防御里扣掉这么多点）
     */
    long getIgnoreDefenceAmount();
}
