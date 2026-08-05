package cn.gfhnv.game.officialStuff.customEffect.actorLiXiaoYanEffects;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.officialStuff.customEntity.players.ActorLiXiaoYan;
import cn.gfhnv.game.officialStuff.customEvent.LiXiaoYanEvents.DamageEventListener;

public class MemorizedHp extends Effect {
    private DamageEventListener liXiaoYanEventListener;

    public MemorizedHp() {
        super("memorizedHp", 1, 3);
    }

    public MemorizedHp(MemorizedHp effect) {
        super(effect.getID());
        this.setLastTime(effect.getLastTime());
        this.setLevel(effect.getLevel());
        this.liXiaoYanEventListener = new DamageEventListener();
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