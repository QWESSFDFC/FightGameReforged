package cn.gfhnv.debug_tools.actionBarTest;

import cn.gfhnv.debug_tools.actionBarTest.eventsAndListeners.TurnPastEvent;
import cn.gfhnv.debug_tools.actionBarTest.eventsAndListeners.TurnPastListener;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.officialStuff.customEntity.monsters.InsectBoss;
import cn.gfhnv.game.officialStuff.customEntity.players.ActorLiXiaoYan;
import cn.gfhnv.game.system.fight.Fight;

import java.util.ArrayList;
import java.util.List;

public class TestMain {
    public static void main(String[] args) {
        ActorLiXiaoYan actorLiXiaoYan=new ActorLiXiaoYan(100);
        InsectBoss insectBoss=new InsectBoss(100);
        LivingThing livingThing=new ActorLiXiaoYan(100);
        livingThing.facSetLevel(100).facSetSpeed(100).facSetName("1");
        LivingThing livingThing2=new ActorLiXiaoYan(100);
        livingThing2.facSetLevel(100).facSetName("2").facSetSpeed(200);
        LivingThing livingThing3=new ActorLiXiaoYan(100);
        livingThing3.facSetLevel(100).facSetSpeed(300).facSetName("3");
        List<LivingThing> livingThings=new ArrayList<>();
        livingThings.add(livingThing);
        livingThings.add(livingThing2);
        livingThings.add(livingThing3);
        List<LivingThing> livingThings2=new ArrayList<>();
        livingThings2.add(actorLiXiaoYan);
        livingThings2.add(insectBoss);
        Fight fight=new Fight(livingThings2,null,livingThings);
        EventBus.register(new TurnPastListener());
EventBus.post(new TurnPastEvent(fight));

    }
}
