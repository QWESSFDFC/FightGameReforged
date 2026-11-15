package cn.gfhnv.game.entity;

import cn.gfhnv.game.system.ElementSort;

/**
 * 一条「元素数值」：元素 + 平值 + <b>来源</b>。
 * <p>
 * <b>穿透与抗性共用它</b>（2026-11-08 作者第三条要求："那些抗性,可以使用一样的处理方式"）：
 * 两者的字段完全相同（元素 / 数值 / 来源），只是住在<b>两个不同的容器</b>里区分语义。
 * 分成两个类以后必然会被写成两份几乎相同的求和代码。
 * <p>
 * 语义约定：
 * <ul>
 *     <li>{@link #elementSort()} 为 {@code null} = <b>全属性</b>（穿透那边就是配置键 {@code penetration} 那条基础条目）；</li>
 *     <li>穿透/抗性的值都是<b>平值</b>：{@code 有效抗性 = max(0, 抗性 − 穿透)}；</li>
 *     <li>抗性允许负数（弱点）；</li>
 *     <li>{@link #source()} 按<b>对象身份</b>比较，基础条目用 {@link #BASE}。</li>
 * </ul>
 *
 * @param elementSort 元素（{@code null} = 全属性）
 * @param amount      数值（穿透 / 抗性都是平值）
 * @param source      来源对象（按身份比较；基础值用 {@link #BASE}）
 * @author AI（DeepSeek）生成；设计稿作者：仓库作者
 */
public record ElementValue(ElementSort elementSort, double amount, Object source) {

    /**
     * 基础值的来源标记：由配置/构造器写进来的那条（{@code copyFrom} 会带过去、{@code resetTemporary} 不动它）。
     */
    public static final Object BASE = new Object();

    /**
     * @return 是不是基础值（配置/构造器写的那一条）
     */
    public boolean isBase() {
        return this.source == BASE;
    }

    /**
     * @param element 要判定的元素
     * @return 这条条目是否对那个元素生效（全属性条目对所有元素都生效）
     */
    public boolean matches(ElementSort element) {
        return this.elementSort == null || this.elementSort == element;
    }
}
