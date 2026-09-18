package cn.gfhnv.game.officialStuff.customSkill.actorLiXiaoYanSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEntity.players.ActorLiXiaoYan;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.mana.Mana;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;

import java.util.List;

public class PyrohemicPumping extends Skill {
    @Override
    public Skill copy() {
        return new PyrohemicPumping();
    }

    public PyrohemicPumping() {
        super("灼血泵动", "获得 2 层【燃点】。消耗李晓焰 当前生命值 20%（此消耗不会使生命值降至 1 以下）", 2, 0, 0, 1);
        this.setCoolDown(1);
        this.setConsumedMana(new Mana(20, ElementSort.FIRE));
        this.getTags().put(TagType.ATTACK, new Tag(3));
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        if (user instanceof ActorLiXiaoYan li) {
            li.setIgnition(li.getIgnition() + 2);
        }
        // 原来写的是 user.getHp() / user.getHpMax(),那是 long/long 的整数除法,
        // 结果只可能是 0(不满血时) 或 1(满血时), <= 0.5 因此恒成立,
        // 配合 extraDamage 没有重置点会逐次 ×1.5 指数膨胀。
        if ((double) user.getHp() / user.getHpMax() <= 0.5) {
            this.setExtraDamage((long) (this.getExtraDamage() * 1.5));
        }
        if (user.getHp() - user.getHp() * 0.2 > 1 && user.getHp() != 1) {
            user.setHp((long) (user.getHp() - user.getHp() * 0.2));
        }

        // 关键修复:加算只能做一次(原来写在循环里,多目标时会加 N 次、却只扣回 1 次),
        // 并且减算要用与加算同一时刻的燃点判断。
        boolean wasHigh = false;
        if (user instanceof ActorLiXiaoYan li && li.getIgnition() >= 8) {
            setExtraDamage((long) (this.getExtraDamage() + user.getHpMax() * 0.5));
            wasHigh = true;
        }

        for (LivingThing livingThing : enemies) {
            System.out.print(user.getName() + "攻击了" + livingThing.getName());
            user.makeDamage(livingThing, this);
        }

        if (user instanceof ActorLiXiaoYan li) {
            if (li.getIgnition() >= 8) {
                li.setIgnition(li.getIgnition() - 1);
            }
        }
        if (wasHigh) {
            setExtraDamage((long) (this.getExtraDamage() - user.getHpMax() * 0.5));
        }
    }
}
