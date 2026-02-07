package cn.gfhnv.game.system.fight;

import cn.gfhnv.game.Thing;
import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.entity.Entity;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.inventory.Inventory;
import cn.gfhnv.game.item.Item;
public class FightEndEventListener {
    @SubscribeEvent
    public void worldTurnEventListener(FightEndEvent fightEndEvent) {
        TurnManager.setPastTimes(0);
        EventBus.unregister(this);
        EventBus.unregister(new FightTurnPastListener());
        if (fightEndEvent.isPlayerWin()){
            System.out.println("恭喜你在战斗中获得了胜利.如果战斗有奖励而且你背包空间够,你会获得奖励");
                for (Thing thing:fightEndEvent.getFight().getFighterList()){
                    if (thing instanceof LivingThing){
                        for (Item item:fightEndEvent.getFight().getRewardList()){
                        if (thing.getInventory().addItem(item)){
                            fightEndEvent.getFight().getRewardList().remove(item);
                        }
                    }
                }
        }
         if (!fightEndEvent.getFight().getRewardList().isEmpty()){
             System.out.println("空间不足.部分物品没有存储");
         }
    }
        if (!fightEndEvent.getFight().getRewardList().isEmpty()){
            System.out.println("你在战斗中失败了.游戏目前没有失败惩罚");
        }
        for (Thing thing:fightEndEvent.getFight().getFighterList()){
            if (thing instanceof LivingThing){
                ((LivingThing) thing).setHp((long) ((LivingThing) thing).getHpMax());
            }
        }
}
}
