package cn.gfhnv.game.officialStuff.customEvent.phainonEvents;

import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.FightStartEvent;
import cn.gfhnv.game.event.SelectTargetEvent;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.CriticalDMGEnhanceEffect;
import cn.gfhnv.game.officialStuff.customEntity.players.Phainon;

public class FightStartAndSelectEventListener {
    @SubscribeEvent
    public void listen(SelectTargetEvent event) {
        for (LivingThing livingThing : event.getTargets()) {
            if (livingThing instanceof Phainon) {
                ((Phainon) livingThing).setCoreflame(Math.min(((Phainon) livingThing).getCoreflame_max(), ((Phainon) livingThing).getCoreflame()) + 1);
                System.out.println(livingThing.getName() + "获得了一个火种");
                if (event.getFight().getFighterList().contains(event.getSelector()) && event.getFight().getFighterList().contains(livingThing)) {
                    livingThing.addEffect(new CriticalDMGEnhanceEffect(0.3, 3).setOrigin(livingThing.getUUID()));
                } else if (event.getFight().getEnemiesList().contains(event.getSelector()) && event.getFight().getEnemiesList().contains(livingThing)) {
                    livingThing.addEffect(new CriticalDMGEnhanceEffect(0.3, 3).setOrigin(livingThing.getUUID()));
                }
            }

        }
    }

    @SubscribeEvent
    public void listen2(FightStartEvent event) {
        for (LivingThing livingThing : event.getFight().getAllEntities()) {
            if (livingThing instanceof Phainon) {
                ((Phainon) livingThing).setExtraAbilityTier(((Phainon) livingThing).getExtraAbilityTier() + 1);
                ((Phainon) livingThing).setCoreflame(((Phainon) livingThing).getCoreflame() + 1);
            }
        }
    }
}
