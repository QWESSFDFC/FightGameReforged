package cn.gfhnv.game.eventListener;

import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EffectUpdateEvent;

import java.util.Iterator;
import java.util.List;

public class EffectEventListener {
    @SubscribeEvent
    public void effectTimer(EffectUpdateEvent event) {
        LivingThing thing = event.getUpdatedThing();
        List<Effect> effectList = thing.getEntityEffectList();
        // ⚠️ 遍历**快照**而不是活列表（2026-10-24）：效果生效期间可能给同一个对象**新挂**效果
        // （例如侵蚀跳伤害 → 受击监听器给攻击者挂【煞火缠身】），用 Iterator 遍历活列表会抛
        // ConcurrentModificationException。改成快照之后，本回合新挂上的效果只是"这一拍不 tick"，
        // 语义没变、也不会炸。
        for (Effect ef : new java.util.ArrayList<>(effectList)) {
            if (ef.isInfinity() || event.getTurnEntry().isExtra()) {
                ef.comeIntoEffect(thing);
                continue;
            }
            if (ef.getLastTime() <= 0) {
                ef.whenLastTimeEnd(thing);
                effectList.remove(ef);
            } else {
                ef.comeIntoEffect(thing);
                ef.setLastTime(ef.getLastTime() - 1);
            }
        }
    }
}
