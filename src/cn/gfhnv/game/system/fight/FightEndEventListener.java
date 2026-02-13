package cn.gfhnv.game.system.fight;

import cn.gfhnv.game.Thing;
import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.entity.Entity;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.item.Item;

import java.util.Iterator;

public class FightEndEventListener {
    private FightTurnPastListener fightTurnPastListener;

    public FightEndEventListener(FightTurnPastListener fightTurnPastListener) {
        this.fightTurnPastListener = fightTurnPastListener;
    }

    @SubscribeEvent
    public void worldTurnEventListener(FightEndEvent fightEndEvent) {
        TurnManager.actionQueue.clear();
        for (Entity entity : fightEndEvent.getFight().getAllEntities()) {
            if (entity instanceof LivingThing) {
                ((LivingThing) entity).setPresentTurn(null);
            }
        }
        EventBus.unregister(this);
        EventBus.unregister(fightTurnPastListener);
        if (fightEndEvent.isPlayerWin()) {
            System.out.println("恭喜你在战斗中获得了胜利.如果战斗有奖励而且你背包空间够,你会获得奖励");
            for (Thing thing : fightEndEvent.getFight().getFighterList()) {
                if (thing instanceof LivingThing) {
                    Iterator<Item> iter = fightEndEvent.getFight().getRewardList().iterator();
                    while (iter.hasNext()) {
                        Item item = iter.next();
                        if (thing.getInventory().addItem(item)) {
                            iter.remove();
                        }
                    }
                }
            }
            if (!fightEndEvent.getFight().getRewardList().isEmpty()) {
                System.out.println("空间不足.部分物品没有存储");
            }
        }
        if (!fightEndEvent.isPlayerWin()) {
            System.out.println("你在战斗中失败了.游戏目前没有失败惩罚");
        }
        for (Thing thing : fightEndEvent.getFight().getFighterList()) {
            if (thing instanceof LivingThing) {
                ((LivingThing) thing).setHp((long) ((LivingThing) thing).getHpMax());
            }
        }
    }
}
