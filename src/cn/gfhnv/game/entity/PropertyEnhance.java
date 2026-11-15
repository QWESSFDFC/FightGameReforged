package cn.gfhnv.game.entity;

/**
 * 一条「属性加成」：类型 + 百分比 + 固定值 + <b>来源</b>。
 * <p>
 * 它替代了以前 13 个 {@code *Enhance*} 标量。换掉的真正好处是<b>来源</b>：
 * 旧写法是"加的时候 +x、结束的时候 −x"，一旦某条收尾路径没跑到（净化、换实例、重复收尾），
 * 那个数字就永远留在身上（`80-EFFECTS.md` 的 K1/K2/K4）。带来源之后收尾是
 * "把这个来源的条目全摘掉"，摘两次也不会变负。
 * <p>
 * {@code source} 按<b>对象身份</b>比较（效果实例用 {@code this}）；它不进 {@code /data}。
 *
 * @param type    加在哪个属性上
 * @param percent 百分比（{@code 0.2} = +20%）
 * @param amount  固定值（{@code +2} 点）
 * @param source  来源对象（按身份比较；用于整批摘除）
 * @author AI（DeepSeek）生成；设计稿作者：仓库作者
 */
public record PropertyEnhance(PropertyEnhanceType type, double percent, double amount, Object source) {
}
