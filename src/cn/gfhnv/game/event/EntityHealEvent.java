package cn.gfhnv.game.event;

import cn.gfhnv.game.entity.LivingThing;

/**
 * <b>一次治疗结算完毕</b>：治疗量已按"受到的治疗加成"放大、并按生命上限夹取。
 * <p>
 * 适合做治疗播报、吸血/反噬、统计。想<b>阻止</b>治疗请在治疗方（技能/效果）里判断，
 * 本事件在回血之后发布，只做通知。
 * <p>
 * 发布点：{@code LivingThing#heal(long)}（全项目统一治疗入口）。
 *
 * @author AI（DeepSeek）生成
 */
public class EntityHealEvent extends Event {

    private final LivingThing target;
    private final long requested;
    private final long healed;

    public EntityHealEvent(LivingThing target, long requested, long healed) {
        this.target = target;
        this.requested = requested;
        this.healed = healed;
    }

    /**
     * @return 被治疗的实体
     */
    public LivingThing getTarget() {
        return target;
    }

    /**
     * @return 申请的治疗量（未加成、未夹取）
     */
    public long getRequested() {
        return requested;
    }

    /**
     * @return 实际回了多少血（加成后、夹取后）
     */
    public long getHealed() {
        return healed;
    }
}
