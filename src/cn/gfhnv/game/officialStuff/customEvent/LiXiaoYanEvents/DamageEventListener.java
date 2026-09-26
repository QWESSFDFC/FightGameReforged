package cn.gfhnv.game.officialStuff.customEvent.LiXiaoYanEvents;

import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.event.DamageEvent;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.officialStuff.customEffect.actorLiXiaoYanEffects.MemorizedHp;
import cn.gfhnv.game.officialStuff.customEntity.players.ActorLiXiaoYan;

/**
 * 【生命值锁定】期间"每受到一次攻击 +1 层【燃点】"的监听器。
 * <p>
 * 由 {@code actorLiXiaoYanSkills.UltimateAttack} 在挂上 {@link MemorizedHp} 时注册，
 * 效果到期时由 {@link MemorizedHp#whenLastTimeEnd} 注销。
 * <p>
 * <b>为什么要绑定到具体的效果实例</b>：监听器是<b>按身份</b>注册/注销的
 * （{@code EventBus.EventHandler#belongsTo} 用 {@code ==} 比较），而效果是<b>按 {@code id + origin} 合并</b>的 ——
 * 两者一旦脱钩（重复开大时新效果被合并丢弃、或效果被强制清掉），就会留下
 * 永远活着、每挨一下都加燃点的"幽灵监听器"（实测表现为"每受击 +N 层"）。
 * 所以这里持有自己的效果，每次触发前先确认"它还在对方身上"，不在就自注销。
 *
 * @author AI（DeepSeek）生成
 */
public class DamageEventListener {

    /**
     * 本监听器所属的【生命值锁定】效果；{@code null} 表示不绑定具体效果（手动 new / 拷贝构造用）。
     */
    private final MemorizedHp owner;

    /**
     * 不绑定效果的构造器（拷贝构造与自测用）。
     * <p>
     * 行为与绑定版略有不同：只要对方身上有<b>任意</b>【生命值锁定】就加燃点
     * （即"谁在我身上都算数"），保持与历史行为一致。
     */
    public DamageEventListener() {
        this(null);
    }

    /**
     * @param owner 所属的【生命值锁定】效果
     */
    public DamageEventListener(MemorizedHp owner) {
        this.owner = owner;
    }

    /**
     * 挨打时加燃点。
     *
     * @param event 伤害事件
     */
    @SubscribeEvent
    public void getIgnition(DamageEvent event) {
        if (!(event.getAttackedEntity() instanceof ActorLiXiaoYan victim)) {
            return;
        }
        if (owner == null) {
            if (!hasMemorizedHp(victim)) {
                return;
            }
        } else if (!isStillAttached(victim)) {
            // 我的效果已经不在对方身上了（被清掉/被替换，注销没走到）→ 自己注销，别当幽灵
            EventBus.unregister(this);
            return;
        }
        victim.setIgnition(victim.getIgnition() + 1);
    }

    /**
     * @param victim 挨打的李晓焰
     * @return 本监听器绑定的那个效果实例是否仍在对方的效果表里（<b>按身份</b>比较：
     * 同 {@code id + origin} 的新效果不算"我的"）
     */
    private boolean isStillAttached(ActorLiXiaoYan victim) {
        for (Effect effect : victim.getEntityEffectList()) {
            if (effect == owner) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param victim 挨打的李晓焰
     * @return 对方身上是否有任意一条【生命值锁定】
     */
    private boolean hasMemorizedHp(ActorLiXiaoYan victim) {
        for (Effect effect : victim.getEntityEffectList()) {
            if (effect instanceof MemorizedHp) {
                return true;
            }
        }
        return false;
    }
}
