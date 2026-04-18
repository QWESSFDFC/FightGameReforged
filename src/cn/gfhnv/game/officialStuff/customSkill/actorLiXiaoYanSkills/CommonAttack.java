package cn.gfhnv.game.officialStuff.customSkill.actorLiXiaoYanSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEntity.players.ActorLiXiaoYan;
import cn.gfhnv.game.system.fight.Fight;

import java.util.List;

public class CommonAttack extends cn.gfhnv.game.officialStuff.customSkill.universalSkill.CommonAttack {
    public CommonAttack() {
        super(1, 0, 0, 1);
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        if (user instanceof ActorLiXiaoYan) {
            if (((ActorLiXiaoYan) user).getIgnition()>=8) setExtraDamage((long) (this.getExtraDamage() +user.getHpMax()*0.05));
        }
        super.comeToEffect(fight, user, enemies);
        long lostHp = (long) (user.getHpMax() - user.getHp());
        user.setHp(user.getHp() + (long)(lostHp * 0.05));
        if (user instanceof ActorLiXiaoYan) { ((ActorLiXiaoYan) user).setIgnition(((ActorLiXiaoYan) user).getIgnition()+1);}
        if (user instanceof ActorLiXiaoYan) {
            if (((ActorLiXiaoYan) user).getIgnition()<8) return;
            ((ActorLiXiaoYan) user).setIgnition(((ActorLiXiaoYan) user).getIgnition()-1);}
    }
}
