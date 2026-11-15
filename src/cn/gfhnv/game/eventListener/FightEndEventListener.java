package cn.gfhnv.game.eventListener;

import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.FightEndEvent;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.system.command.CommandManager;
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
        // AI 添加：让回合驱动循环停下来。否则 /endfight 这类「战斗中途结束」的场景里，
        // 循环会在清理完之后再跑一圈，而 isDriving 仍为 true，下一场战斗的驱动会被它挡掉。
        fightTurnPastListener.setDriving(false);
        // AI 添加：把「当前战斗」从命令系统里注销，避免命令继续往已经结束的战斗里找目标
        CommandManager.clearCurrentFight();
        // ⚠️ 收尾放进 finally：任何实体 whenFightEnds() 抛异常，监听器也必须注销、
        // 临时实体也必须离开 World.things —— 否则下一局会带着上一局的残留跑。
        try {
            for (LivingThing entity : fightEndEvent.getFight().getAllEntities()) {
                if (entity != null) {
                    entity.whenFightEnds();
                }
            }
            grantRewards(fightEndEvent);
        } finally {
            EventBus.unregister(this);
            EventBus.unregister(fightTurnPastListener);
            TurnManager.getTurns().clear();
            World.turnTimer = 0;
            // 2026-11-15 第 76 批（作者要求）：战斗实体是纯临时实体 —— 战斗一结束就离开 World.things，
            // 否则 /list 与 @e 还能选中上一局的副本，并且它们一直吊着整场 Fight 不能回收。
            for (LivingThing entity : fightEndEvent.getFight().getAllEntities()) {
                if (entity != null) {
                    World.removeThing(entity);
                }
            }
            // 奖励物品在选择时也被 addThing 进了运行时列表；它们同样是临时对象。
            for (Item item : fightEndEvent.getFight().getRewardList()) {
                if (item != null) {
                    World.removeThing(item);
                }
            }
        }
    }

    /**
     * 发放奖励（只做"往本场副本背包里塞"这一件事）。
     * <p>
     * ⚠️ 战斗实体是纯临时对象：奖励只进本场副本的背包，**下一局会随副本一起被丢弃**。
     * 目前没有存档 / 持久背包，所以"奖励"在玩法上还没有真正的去处（作者确认的现状）。
     *
     * @param fightEndEvent 战斗结束事件
     */
    private void grantRewards(FightEndEvent fightEndEvent) {
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
                        if (living.getInventory().addItem(item.copy())) {
                            // 奖励已入包（第 74 批新增事件）：没放进去的不发
                            EventBus.post(new cn.gfhnv.game.event.RewardGrantedEvent(living, item));
                        } else {
                            result = false;
                        }
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
