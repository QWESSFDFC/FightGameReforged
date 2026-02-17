package cn.gfhnv.game.event.eventListener;

import cn.gfhnv.game.Thing;
import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.FightPastOneTurnEvent;
import cn.gfhnv.game.event.WorldTurnEvent;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.fight.FightTurnPastListener;
import cn.gfhnv.game.world.World;

import java.util.Iterator;
import java.util.List;

public class EffectEventListener {
        @SubscribeEvent
        public void effectTimer(FightPastOneTurnEvent event) {
            List<LivingThing> things = event.getFight().getAllEntities();
            for (LivingThing thing : things) {
                if (thing == null) continue;
                List<Effect> effectList = thing.getEntityEffectList();
                Iterator<Effect> iterator = effectList.iterator();
                while (iterator.hasNext()) {
                    Effect ef = iterator.next();
                    if (ef.getLastTime() <= 0) {
                        ef.whenLastTimeEnd(thing);
                        iterator.remove();
                    } else {
                        ef.comeIntoEffect(thing);
                        ef.setLastTime(ef.getLastTime() - 1);
                    }
            }
        }
    }
}
