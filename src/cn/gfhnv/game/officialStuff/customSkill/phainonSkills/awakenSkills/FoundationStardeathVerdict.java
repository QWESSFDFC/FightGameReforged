package cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEntity.players.Phainon;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

public class FoundationStardeathVerdict extends Skill {
    public FoundationStardeathVerdict() {
        super("支柱-死星天裁", "解除自身所有负面效果，随后造成最多等同于卡厄斯兰那1170%攻击力的火属性伤害。", 0, 0.45, 0, -1);
        this.setCoolDown(0);
        this.getTags().put(TagType.ATTACK, new Tag(5));
    }

    @Override
    public boolean canUse(Fight fight, LivingThing user, List<LivingThing> enemies) {
        if (user instanceof Phainon phainon) {
            return phainon.getScourge() >= 1;
        }
        return false;
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        List<LivingThing> e = new ArrayList<>(enemies);
        user.setHp((long) (user.getHp() + user.getHpMax() * 0.2));
        ListIterator<Effect> listedIterator = user.getEntityEffectList().listIterator();
        while (listedIterator.hasNext()) {
            Effect effect = listedIterator.next();
            if (effect.isNegative()) {
                effect.whenLastTimeEnd(user);
                listedIterator.remove();
            }
        }
        if (user instanceof Phainon phainon) {
            int scourge = Math.min(phainon.getScourge(), 4);//消耗的数量
            phainon.setScourge(phainon.getScourge() - scourge);
            int attackTimes = 6 * scourge;

            for (int i = 1; i <= attackTimes; i++) {
                e.removeIf(livingThing -> !livingThing.isAlive());
                if (e.isEmpty()) {
                    this.setAtkMagnification(0.45);
                    return;
                }
                Collections.shuffle(e);
                LivingThing livingThing = e.getFirst();
                System.out.print(user.getName() + "攻击了" + livingThing.getName());
                user.makeDamage(livingThing, this);
            }
            if (scourge != 4) {
                this.setAtkMagnification(0.45);
                return;
            }
            this.setAtkMagnification((double) 6 / e.size());
            for (LivingThing livingThing : e) {
                System.out.print(user.getName() + "攻击了" + livingThing.getName());
                user.makeDamage(livingThing, this);
            }

        }
        this.setAtkMagnification(0.45);
    }

    @Override
    public Skill copy() {
        return new FoundationStardeathVerdict();
    }
}
