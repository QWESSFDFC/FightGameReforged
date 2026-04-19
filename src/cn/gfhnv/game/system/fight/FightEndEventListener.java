package cn.gfhnv.game.system.fight;

import cn.gfhnv.game.Thing;
import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.world.World;

public class FightEndEventListener {
    private FightTurnPastListener fightTurnPastListener;

    public FightEndEventListener(FightTurnPastListener fightTurnPastListener) {
        this.fightTurnPastListener = fightTurnPastListener;
    }

    @SubscribeEvent
    public void worldTurnEventListener(FightEndEvent fightEndEvent) {
        TurnManager.actionQueue.clear();
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
            for (Thing thing : fightEndEvent.getFight().getFighterList()) {
                if (thing instanceof LivingThing) {
                    fightEndEvent.getFight().getRewardList().removeIf(item -> ((LivingThing) thing).getInventory().addItem(item));
                }
            }
            if (!fightEndEvent.getFight().getRewardList().isEmpty()) {
                System.out.println("空间不足.部分物品没有存储");
            }
        }
        if (!fightEndEvent.isPlayerWin()) {
            System.out.println("你在战斗中失败了.游戏目前没有失败惩罚");
        }


    }
}
