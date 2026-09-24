package cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.SpeedEnhanceEffect;
import cn.gfhnv.game.officialStuff.customEntity.players.Phainon;
import cn.gfhnv.game.officialStuff.customEvent.phainonEvents.AwakenEndEvent;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.mana.Mana;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;

import java.util.List;

public class LastAttack extends Skill {
    public LastAttack() {
        super("最后一击", "最后一击", 0, 13, 0, -1);
        this.setConsumedMana(new Mana(0, ElementSort.UNIVERSAL));
        this.setCoolDown(0);
        this.getTags().put(TagType.ATTACK, new Tag(1));
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        if (user instanceof Phainon phainon) {
            // 最后一击的倍率跟着「还剩多少额外回合」走：剩得越少打得越狠，走完 8 个回合时是满倍率 13。
            // 变身被打断时剩余额外回合已被清算（extraTurns 归零），所以要优先用打断那一刻存下的快照，
            // 否则「挨打打断」会变成满倍率，反而比正常结束更疼。
            int remainingExtraTurns = phainon.hasInterruptedLastAttackSnapshot()
                    ? phainon.getInterruptedLastAttackSnapshot()
                    : phainon.getExtraTurns();
            this.setAtkMagnification(getAtkMagnification() * (1 - remainingExtraTurns * 0.125));
        }

        for (LivingThing livingThing : enemies) {
            System.out.print(user.getName() + "攻击了" + livingThing.getName());
            user.makeDamage(livingThing, this);

        }
        for (LivingThing livingThing : fight.getOpponentList(user)) {
            livingThing.addEffect(new SpeedEnhanceEffect(0.15, 1).setOrigin(user.getUUID()));
        }

        EventBus.post(new AwakenEndEvent((Phainon) user));
    }

    @Override
    public Skill copy() {
        return new LastAttack();
    }
}
