package cn.gfhnv.game.event;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.item.Item;

/**
 * <b>战斗奖励已经放进某人的背包</b>（逐件发布）。
 * <p>
 * 背包满导致没放进去时<b>不发布</b>（那不是"已发放"）。
 * <p>
 * 发布点：{@code FightEndEventListener} 的奖励发放循环。
 *
 * @author AI（DeepSeek）生成
 */
public class RewardGrantedEvent extends Event {

    private final LivingThing receiver;
    private final Item item;

    public RewardGrantedEvent(LivingThing receiver, Item item) {
        this.receiver = receiver;
        this.item = item;
    }

    /**
     * @return 收到奖励的实体
     */
    public LivingThing getReceiver() {
        return receiver;
    }

    /**
     * @return 发下去的那一件（副本）
     */
    public Item getItem() {
        return item;
    }
}
