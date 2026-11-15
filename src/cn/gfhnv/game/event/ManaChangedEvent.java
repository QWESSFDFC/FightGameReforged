package cn.gfhnv.game.event;

import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.mana.Mana;

/**
 * <b>某一系法力值变化</b>（消耗 / 回复 / 直接设置）。
 * <p>
 * 注意：{@link Mana} 本身不持有拥有者，所以事件只带"哪一系、前后值"；
 * 需要知道是谁的法力时，在监听器里用 {@link Mana} 实例去 {@code LivingThing#getManas()} 里比对。
 * <p>
 * 发布点：{@code Mana#setAmount(double)}（每回合回蓝、技能扣蓝都经过它）。
 *
 * @author AI（DeepSeek）生成
 */
public class ManaChangedEvent extends Event {

    private final Mana mana;
    private final double before;
    private final double after;

    public ManaChangedEvent(Mana mana, double before, double after) {
        this.mana = mana;
        this.before = before;
        this.after = after;
    }

    /**
     * @return 变化的那份法力（含元素与上限）
     */
    public Mana getMana() {
        return mana;
    }

    /**
     * @return 变化前的值
     */
    public double getBefore() {
        return before;
    }

    /**
     * @return 变化后的值
     */
    public double getAfter() {
        return after;
    }

    /**
     * @return 这一系法力对应的元素
     */
    public ElementSort getElementSort() {
        return mana.getElementSort();
    }
}
