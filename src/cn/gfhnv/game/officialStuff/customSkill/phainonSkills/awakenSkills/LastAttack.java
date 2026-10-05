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

    /**
     * 复制构造器。
     * <p>
     * {@link #copy()} 必须走这里，不能返回 {@code new LastAttack()}：那样会把倍率/冷却/消耗
     * 还原成构造器里的出厂值，于是打在模板上的配置（{@code SkillData.json}）
     * <b>永远传不到选人时复制出去的副本</b>。
     *
     * @param other 被复制的技能
     */
    public LastAttack(LastAttack other) {
        super(other);
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        if (user instanceof Phainon phainon) {
            // 最后一击的倍率跟着「还剩多少额外回合」走：剩得越少打得越狠，走完 8 个回合时是满倍率 13。
            // 被打断的那一刀用「打断那一刻冻结下来的剩余回合数」：退出协议（AwakeEndListener#end）
            // 已经把它清零了，再读实时值会变成满倍率 13（见 Phainon#finalizeAwakenByInterrupt）。
            // 公式、每剩余回合的 0.125 与下限 7 一个字都没动。
            int remainingExtraTurns = Math.min(7, phainon.getInterruptedRemainingExtraTurns() >= 0
                    ? phainon.getInterruptedRemainingExtraTurns()
                    : phainon.getExtraTurns());
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
        return new LastAttack(this);
    }
}
