package cn.gfhnv.game.eventListener;

import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.FightPastOneTurnEvent;
import cn.gfhnv.game.event.FightStartEvent;
import cn.gfhnv.game.system.fight.TurnManager;


public class FightStartEventListener {
    @SubscribeEvent
    public void onFightStartEvent(FightStartEvent event) {
        FightTurnPastListener listener = new FightTurnPastListener();
        EventBus.register(listener);
        EventBus.register(new FightEndEventListener(listener));
        TurnManager.init(event.getFight());
        for (LivingThing livingThing : event.getFight().getAllEntities()) {
            // 把战斗上下文接到实体上：LivingThing#getParticipateFight() 此前一直是 null
            // （字段与读写方法都在，却没有人赋值），于是任何实体都没法知道"我在打哪一场"，
            // 也就没法判断"谁是同阵营"。中途召唤的实体由召唤方补一次同样的初始化。
            livingThing.setParticipateFight(event.getFight());
            livingThing.whenFightStart(event.getFight());
        }
        EventBus.post(new FightPastOneTurnEvent(event.getFight()));

    }
}
