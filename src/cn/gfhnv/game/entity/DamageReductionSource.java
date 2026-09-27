package cn.gfhnv.game.entity;

/**
 * 减伤来源的<b>具名身份键</b>：既能按对象身份区分，又能让 {@code /data} 看出是谁给的。
 * <p>
 * {@link LivingThing#addDamageReduction(Object, double)} 是按<b>对象身份</b>（{@code ==}）
 * 增删来源的，所以"跨技能共用的那一份减伤"必须有一个全项目唯一的常量对象当键 ——
 * 以前这些键都写成 {@code new Object()}，代价是 {@code /data} 里
 * {@code damageReductions} 只能显示出 {@code {percent:0.04d}}，
 * <b>完全看不出这条减伤是【醉意】还是白厄的【灾厄】</b>（用户 2026-09 实测反馈）。
 * <p>
 * 换成这个类之后，{@code addDamageReduction} 会把这个名字带进
 * {@link LivingThing.DamageReduction#sourceName()}，于是 {@code /data} 里就能读到：
 * <pre>
 * damageReductions:[{sourceName:"醉意",percent:0.04d}]
 * </pre>
 * 注意：<b>{@code equals} 没有被重写</b> —— 它靠的仍然是对象身份，
 * 所以"两个名字相同的来源"依然是两个独立来源，不会互相覆盖。
 *
 * @author AI（DeepSeek）生成
 */
public final class DamageReductionSource {

    /**
     * 给人看的名字（只用于显示，不参与判断）。
     */
    private final String name;

    /**
     * @param name 名字；{@code null} 时退回一个占位名
     */
    public DamageReductionSource(String name) {
        this.name = name == null ? "未命名来源" : name;
    }

    @Override
    public String toString() {
        return name;
    }
}
