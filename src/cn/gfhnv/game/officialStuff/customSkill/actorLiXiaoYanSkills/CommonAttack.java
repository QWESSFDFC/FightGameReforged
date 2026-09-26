package cn.gfhnv.game.officialStuff.customSkill.actorLiXiaoYanSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEntity.players.ActorLiXiaoYan;
import cn.gfhnv.game.system.fight.Fight;

import java.util.List;

public class CommonAttack extends cn.gfhnv.game.officialStuff.customSkill.universalSkill.CommonAttack {

    /**
     * 每次普攻按"已损失生命"回复的比例。
     */
    private static final double LOST_HP_HEAL_RATE = 0.05;

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
        if (user instanceof ActorLiXiaoYan li && li.getIgnition() >= ActorLiXiaoYan.HIGH_IGNITION) {
            setExtraDamage((long) (this.getExtraDamage() + user.getHpMax() * ActorLiXiaoYan.HIGH_IGNITION_BONUS_RATE));
            wasHigh = true;
        }

        super.comeToEffect(fight, user, enemies);

        long lostHp = (long) (user.getHpMax() - user.getHp());
        user.setHp(user.getHp() + (long) (lostHp * LOST_HP_HEAL_RATE));
        if (user instanceof ActorLiXiaoYan li) {
            // 【燃点】一路攒到上限为止（10 层，血少时 15），封顶交给 setIgnition 的夹取。
            // 2026-09 加强前这里写的是"涨一层再扣一层"，于是普攻在 7 层就永远涨不动，
            // 而加成要 8 层、免死要 10 层 —— 等于只能靠挨打攒层。
            li.setIgnition(li.getIgnition() + 1);
        }

        // 只扣回「确实加过」的那一次（wasHigh 为 true 才扣），保证加算减算严格配对
        if (wasHigh) {
            setExtraDamage((long) (this.getExtraDamage() - user.getHpMax() * ActorLiXiaoYan.HIGH_IGNITION_BONUS_RATE));
        }
    }
}
