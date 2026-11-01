package cn.gfhnv.game.eventListener;

import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EffectUpdateEvent;

import java.util.Iterator;
import java.util.List;

public class EffectEventListener {
    /**
     * 每回合末给<b>当前行动者</b>结算一次效果。
     * <p>
     * <b>`lastTime` 的口径（2026-10-25 统一）</b>：它是<b>"还会 tick 几次"</b>（剩余结算次数），
     * 不是"还能活几个回合"。
     * <ul>
     *     <li>`lastTime = N` ⇒ 接下来的 <b>N</b> 个"持有者回合末"各结算一次，第 N 次结算完<b>当场收尾移除</b>；</li>
     *     <li>`lastTime = 0`（或负数）⇒ 一次都不再 tick，落到下面的防守分支直接收尾；</li>
     *     <li>挂上那一刻的 {@code initialEffect} <b>不算</b> tick（那是"获得即生效"）。</li>
     * </ul>
     * 旧写法是"先结算、后减 1、下一拍才判到期"⇒ `lastTime = N` 实际活 N+1 个回合末，
     * `/effect list` 显示的"剩余 N 回合"也一直偏低一档（`80-EFFECTS.md` 的 K15）。
     *
     * @param event 效果更新事件（带"当前回合条目"，用它区分额外回合）
     */
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
            int remaining = ef.getLastTime();
            if (remaining <= 0) {
                // 一次都没得 tick 了（例如 lastTime 设为 0）：直接收尾
                ef.whenLastTimeEnd(thing);
                effectList.remove(ef);
                continue;
            }
            // 结算这一次，然后把"还会 tick 几次"减 1；减到 0 说明用完了 → 同一次 tick 里收尾移除
            ef.comeIntoEffect(thing);
            ef.setLastTime(--remaining);
            if (remaining <= 0) {
                ef.whenLastTimeEnd(thing);
                effectList.remove(ef);
            }
        }
    }
}
