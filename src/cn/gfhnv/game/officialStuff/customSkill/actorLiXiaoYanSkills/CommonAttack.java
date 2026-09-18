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
    public CommonAttack copy() {
        return new CommonAttack();
    }


    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        // 加算与减算必须用「同一时刻」的燃点判断（这里用 wasHigh 快照），
        // 否则会出现「加过却按未加成扣除」或「没加也扣」的配平错误。
        boolean wasHigh = false;
        if (user instanceof ActorLiXiaoYan li && li.getIgnition() >= 8) {
            setExtraDamage((long) (this.getExtraDamage() + user.getHpMax() * 0.5));
            wasHigh = true;
        }

        super.comeToEffect(fight, user, enemies);

        long lostHp = (long) (user.getHpMax() - user.getHp());
        user.setHp(user.getHp() + (long) (lostHp * 0.05));
        if (user instanceof ActorLiXiaoYan li) {
            li.setIgnition(li.getIgnition() + 1);
            // 燃点达到 8 层后不再继续累积(打一次涨一层也扣一层),净变化为 0
            if (li.getIgnition() >= 8) {
                li.setIgnition(li.getIgnition() - 1);
            }
        }

        // 只扣回「确实加过」的那一次（wasHigh 为 true 才扣），保证加算减算严格配对
        if (wasHigh) {
            setExtraDamage((long) (this.getExtraDamage() - user.getHpMax() * 0.5));
        }
    }
}
