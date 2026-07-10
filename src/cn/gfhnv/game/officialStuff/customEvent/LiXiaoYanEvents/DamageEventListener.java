package cn.gfhnv.game.officialStuff.customEvent.LiXiaoYanEvents;

import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.event.DamageEvent;
import cn.gfhnv.game.officialStuff.customEffect.actorLiXiaoYanEffects.MemorizedHp;
import cn.gfhnv.game.officialStuff.customEntity.players.ActorLiXiaoYan;

public class DamageEventListener {
    @SubscribeEvent
    public void getIgnition(DamageEvent event) {
        boolean isActorLiXiaoYan = false;
        for (Effect effect : event.getAttackedEntity().getEntityEffectList()) {
            if (effect instanceof MemorizedHp) {
                isActorLiXiaoYan = true;
                break;
            }
        }
        if (event.getAttackedEntity() instanceof ActorLiXiaoYan && isActorLiXiaoYan) {
            ((ActorLiXiaoYan) event.getAttackedEntity()).setIgnition(((ActorLiXiaoYan) event.getAttackedEntity()).getIgnition() + 1);
        }
    }
}
