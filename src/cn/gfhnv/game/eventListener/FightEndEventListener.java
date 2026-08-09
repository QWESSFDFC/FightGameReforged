package cn.gfhnv.game.eventListener;

import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.FightEndEvent;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.system.fight.TurnManager;
import cn.gfhnv.game.world.World;

import java.util.List;

public class FightEndEventListener {
    private FightTurnPastListener fightTurnPastListener;

    public FightEndEventListener(FightTurnPastListener fightTurnPastListener) {
        this.fightTurnPastListener = fightTurnPastListener;
    }

    @SubscribeEvent
    public void worldTurnEventListener(FightEndEvent fightEndEvent) {
        TurnManager.getTurns().clear();
        for (LivingThing entity : fightEndEvent.getFight().getAllEntities()) {
            if (entity != null) {
                entity.whenFightEnds();

            }
        }
        EventBus.unregister(this);
        EventBus.unregister(fightTurnPastListener);
        World.turnTimer = 0;
        if (fightEndEvent.isPlayerWin()) {
            System.out.println();
            System.out.println("恭喜你在战斗中获得了胜利.如果战斗有奖励而且你背包空间够,你会获得奖励");
            List<LivingThing> fighters = fightEndEvent.getFight().getFighterList();
            List<Item> rewards = fightEndEvent.getFight().getRewardList();
            boolean result = true;
            for (LivingThing living : fighters) {
                if (rewards.isEmpty()) {
                    System.out.println("没有奖励");
                    break;
                }
                if (living != null) {
                    for (Item item : rewards) {
                        if (item == null) continue;
                        if (!living.getInventory().addItem(item.copy())) result = false;
                    }
                }
            }
            if (!result) {
                System.out.println("空间不足.部分物品没有存储");
            }
        }
        if (!fightEndEvent.isPlayerWin()) {
            System.out.println("你在战斗中失败了.游戏目前没有失败惩罚");
        }


    }
}
