package cn.gfhnv.game.event;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;

/**
 * <b>一个效果挂到了实体身上</b>（新增或同 id 替换/叠加计数之后）。
 * <p>
 * 适合做效果播报、计数器、联动机制。要"拒绝"某个效果，请在调用方判断再决定加不加。
 * <p>
 * 发布点：{@code LivingThing#addEffect(LivingThing, Effect)}。
 *
 * @author AI（DeepSeek）生成
 */
public class EffectAppliedEvent extends Event {

    private final LivingThing target;
    private final Effect effect;

    public EffectAppliedEvent(LivingThing target, Effect effect) {
        this.target = target;
        this.effect = effect;
    }

    /**
     * @return 效果持有者
     */
    public LivingThing getTarget() {
        return target;
    }

    /**
     * @return 挂上的效果（可能是被合并/替换后的实例）
     */
    public Effect getEffect() {
        return effect;
    }
}
