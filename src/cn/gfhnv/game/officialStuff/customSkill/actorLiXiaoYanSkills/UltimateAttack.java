package cn.gfhnv.game.officialStuff.customSkill.actorLiXiaoYanSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.officialStuff.customEffect.actorLiXiaoYanEffects.MemorizedHp;
import cn.gfhnv.game.officialStuff.customEntity.players.ActorLiXiaoYan;
import cn.gfhnv.game.officialStuff.customEvent.LiXiaoYanEvents.DamageEventListener;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.mana.Mana;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;

import java.util.List;

public class UltimateAttack extends Skill {
    public UltimateAttack() {
        super("过载·白炽化", "对3个敌方造成等同于李晓焰 最大生命值 400% 的火属性伤害。\n" +
                "锁定当前生命值：施放终结技时，李晓焰的当前生命值比例将被“铭记”（例如施放时为 20% 生命值）。在接下来的 2 回合 内，她的生命值不会因任何原因（包括自己的技能消耗、敌方攻击、持续伤害）降至该比例以下。\n" +
                "在此期间，每受到一次攻击，获得 1 层【燃点】。", 4, 0, 0, 3);
        this.setCoolDown(4);
        this.setConsumedMana(new Mana(300, ElementSort.FIRE));
        this.getTags().put(TagType.ATTACK, new Tag(5));
    }


    @Override
    public Skill copy() {
        return new UltimateAttack();
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        boolean enhanced = false;

        if (user instanceof ActorLiXiaoYan) {
            double rate = (double) user.getHp() / user.getHpMax();
            ((ActorLiXiaoYan) user).setMemorizedRate(rate);
            if (((ActorLiXiaoYan) user).getIgnition() >= 8)
                setExtraDamage((long) (this.getExtraDamage() + user.getHpMax() * 0.5));
            enhanced = true;
        }
        for (LivingThing livingThing : enemies) {
            System.out.print(user.getName() + "攻击了" + livingThing.getName());
            user.makeDamage(livingThing, this);
        }
        MemorizedHp memorizedHp = new MemorizedHp();
        memorizedHp.setLiXiaoYanEventListener(new DamageEventListener());
        EventBus.register(memorizedHp.getLiXiaoYanEventListener());
        // origin 记施加者实体的 UUID（而不是字面量 "self"）：同一个人重复放大招时，
        // Effect#equals（id + origin）会认出"还是同一条"，合并刷新而不是叠第二条
        user.addEffect(memorizedHp.setOrigin(user.getUUID()));
        System.out.printf("生命值锁定生效中");
        if (user instanceof ActorLiXiaoYan) {
            ((ActorLiXiaoYan) user).setIgnition(((ActorLiXiaoYan) user).getIgnition() + 1);
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
