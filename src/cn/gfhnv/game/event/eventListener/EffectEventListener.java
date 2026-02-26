package cn.gfhnv.game.event.eventListener;

import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EffectUpdateEvent;
import cn.gfhnv.game.event.FightPastOneTurnEvent;

import java.util.Iterator;
import java.util.List;

public class EffectEventListener {
    @SubscribeEvent
    public void effectTimer(EffectUpdateEvent event) {
            LivingThing thing = event.getUpdatedThing();
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
