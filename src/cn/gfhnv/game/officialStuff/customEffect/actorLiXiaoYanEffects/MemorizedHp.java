package cn.gfhnv.game.officialStuff.customEffect.actorLiXiaoYanEffects;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.officialStuff.customEntity.players.ActorLiXiaoYan;
import cn.gfhnv.game.officialStuff.customEvent.LiXiaoYanEvents.DamageEventListener;

/**
 * 【生命值锁定】—— 记住施放瞬间的生命值比例，效果存在期间生命值不会低于该比例。
 * <p>
 * 由李晓焰的大招（{@code actorLiXiaoYanSkills.UltimateAttack}）挂上：
 * <ul>
 *     <li>比例写在 {@link ActorLiXiaoYan#setMemorizedRate(double)} 上，由她的伤害修正器读取；</li>
 *     <li>效果存在期间，{@link DamageEventListener} 负责"每受到一次攻击 +1 层【燃点】"
 *     （监听器<b>绑定本效果实例</b>，见该类注释）；</li>
 *     <li>效果到期（{@link #whenLastTimeEnd(LivingThing)}）时把比例复位成 {@code -1} 并注销监听器。</li>
 * </ul>
 *
 * @author AI（DeepSeek）生成
 */
public class MemorizedHp extends Effect {

    private DamageEventListener liXiaoYanEventListener;

    /**
     * 构造一条【生命值锁定】。
     * <p>
     * 监听器在这里就创建好并<b>绑定本实例</b>（{@code new DamageEventListener(this)}），
     * 注册与否由施加者决定（见 {@code UltimateAttack#applyMemorizedHp}）。
     */
    public MemorizedHp() {
        super("memorizedHp", 1, 3);
        this.liXiaoYanEventListener = new DamageEventListener(this);
    }

    public MemorizedHp(MemorizedHp effect) {
        super(effect.getID());
        this.setLastTime(effect.getLastTime());
        this.setLevel(effect.getLevel());
        this.liXiaoYanEventListener = new DamageEventListener(this);
    }

    @Override
    public void comeIntoEffect(LivingThing thing) {

    }

    @Override
    public Effect copy() {
        return new MemorizedHp(this);
    }

    public DamageEventListener getLiXiaoYanEventListener() {
        return liXiaoYanEventListener;
    }

    public void setLiXiaoYanEventListener(DamageEventListener liXiaoYanEventListener) {
        this.liXiaoYanEventListener = liXiaoYanEventListener;
    }

    @Override
    public void whenLastTimeEnd(LivingThing thing) {
        if (thing instanceof ActorLiXiaoYan) {
            ((ActorLiXiaoYan) thing).setMemorizedRate(-1);
            if (liXiaoYanEventListener != null) {
                EventBus.unregister(liXiaoYanEventListener);
            }
        }
    }
}
