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
            // 变身被打断时只有时间轴上的条目被摘掉，extraTurns 保留打断那一刻的值，
            // 所以这里直接读实时值就等价于"打断时的剩余回合数"。
            // 上限压到 7：extraTurns = 8 时公式会算出 0 倍率（一个额外回合都没走完就被打断，
            // 那一刀会变成 0 伤害），并进 7 让提前打断也保底有 12.5% 的伤害。
            int remainingExtraTurns = Math.min(7, phainon.getExtraTurns());
            this.setAtkMagnification(getAtkMagnification() * (1 - remainingExtraTurns * 0.125));
        }

        for (LivingThing livingThing : enemies) {
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
