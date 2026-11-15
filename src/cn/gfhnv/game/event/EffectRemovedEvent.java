package cn.gfhnv.game.event;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;

/**
 * <b>一个效果被从实体身上摘掉</b>。
 * <p>
 * 只覆盖走 {@code LivingThing#removeEffect} 的<b>单条移除</b>；
 * 批量清理（{@code removeEffectsIf} / 战斗结束整体清空）为了不刷屏<b>不逐条发布</b>。
 * <p>
 * 发布点：{@code LivingThing#removeEffect(Effect)}。
 *
 * @author AI（DeepSeek）生成
 */
public class EffectRemovedEvent extends Event {

    private final LivingThing target;
    private final Effect effect;

    public EffectRemovedEvent(LivingThing target, Effect effect) {
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
     * @return 被摘掉的效果
     */
    public Effect getEffect() {
        return effect;
    }
}
