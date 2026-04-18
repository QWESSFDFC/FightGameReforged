package cn.gfhnv.game.officialStuff.customEffect.universalEffects.actorLiXiaoYanEffects;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEntity.players.ActorLiXiaoYan;

public class MemorizedHp extends Effect {
    public MemorizedHp() {
        super("memorizedHp", 1, 3);
    }

    @Override
    public void comeIntoEffect(LivingThing thing) {
        // 不在此处修改血量，锁血逻辑已由 makeDamage 处理
    }

    @Override
    public void whenLastTimeEnd(LivingThing thing) {
        if (thing instanceof ActorLiXiaoYan) {
            ((ActorLiXiaoYan) thing).setMemorizedRate(-1);

        }
    }
}