package cn.gfhnv.game.officialStuff.customSkill.actorLiXiaoYanSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEntity.players.ActorLiXiaoYan;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.mana.Mana;

import java.util.List;
import java.util.Random;

public class PyrohemicPumping extends Skill {

    public PyrohemicPumping() {
        super("灼血泵动", "获得 2 层【燃点】。消耗李晓焰 当前生命值 20%（此消耗不会使生命值降至 1 以下）", 2, 0, 0, 1);
        this.setCoolDown(1);
        this.setConsumedMana(new Mana(20, ElementSort.FIRE));
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        boolean enhanced = false;
        if (user instanceof ActorLiXiaoYan) {
            ((ActorLiXiaoYan) user).setIgnition(((ActorLiXiaoYan) user).getIgnition() + 2);
        }
        if (user.getHp() / user.getHpMax() <= 0.5) {
            this.setExtraDamage((long) (this.getExtraDamage() * 1.5));
        }
        if (user.getHp() - user.getHp() * 0.2 > 1 && user.getHp() != 1) {
            user.setHp((long) (user.getHp() - user.getHp() * 0.2));
        }
        for (LivingThing livingThing : enemies) {
            System.out.print(user.getName() + "攻击了" + livingThing.getName());
            if (user instanceof ActorLiXiaoYan) {
                if (((ActorLiXiaoYan) user).getIgnition() >= 8)
                    setExtraDamage((long) (this.getExtraDamage() + user.getHpMax() * 0.5));
                enhanced = true;
            }
            user.makeDamage(livingThing, this);
        }
        if (user instanceof ActorLiXiaoYan) {
            if (((ActorLiXiaoYan) user).getIgnition() < 8) return;
            ((ActorLiXiaoYan) user).setIgnition(((ActorLiXiaoYan) user).getIgnition() - 1);
        }
        if (enhanced) {
            setExtraDamage((long) (this.getExtraDamage() - user.getHpMax() * 0.5));
        }
    }
}
