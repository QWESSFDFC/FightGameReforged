package cn.gfhnv.game.officialStuff.customSkill.actorLiXiaoYanSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entity.skill.Skill;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.actorLiXiaoYanEffects.MemorizedHp;
import cn.gfhnv.game.officialStuff.customEntity.players.ActorLiXiaoYan;
import cn.gfhnv.game.officialStuff.customEvent.LiXiaoYanEvents.DamageEventListener;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.mana.Mana;

import java.util.List;

public class UltimateAttack extends Skill {
    public UltimateAttack() {
        super("过载·白炽化", "对3个敌方造成等同于李晓焰 最大生命值 400% 的火属性伤害。\n" +
                "锁定当前生命值：施放终结技时，李晓焰的当前生命值比例将被“铭记”（例如施放时为 20% 生命值）。在接下来的 2 回合 内，她的生命值不会因任何原因（包括自己的技能消耗、敌方攻击、持续伤害）降至该比例以下。\n" +
                "在此期间，每受到一次攻击，获得 1 层【燃点】。", 4, 0, 0, 3);
        this.setCoolDown(4);
        this.setConsumedMana(new Mana(300, ElementSort.FIRE));
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        if (user instanceof ActorLiXiaoYan) {
            double rate = (double) user.getHp() / user.getHpMax();
            ((ActorLiXiaoYan) user).setMemorizedRate(rate);
            if (((ActorLiXiaoYan) user).getIgnition() >= 8)
                setExtraDamage((long) (this.getExtraDamage() + user.getHpMax() * 0.05));
        }
        for (LivingThing livingThing : enemies) {
            System.out.print(user.getName() + "攻击了" + livingThing.getName());
            user.makeDamage(livingThing, this);
        }
        MemorizedHp memorizedHp = new MemorizedHp();
        memorizedHp.setLiXiaoYanEventListener(new DamageEventListener());
        EventBus.register(memorizedHp.getLiXiaoYanEventListener());
        user.addEffect(memorizedHp);
        System.out.printf("生命值锁定生效中");
        if (user instanceof ActorLiXiaoYan) {
            ((ActorLiXiaoYan) user).setIgnition(((ActorLiXiaoYan) user).getIgnition() + 1);
        }
        if (user instanceof ActorLiXiaoYan) {
            if (((ActorLiXiaoYan) user).getIgnition() < 8) return;
            ((ActorLiXiaoYan) user).setIgnition(((ActorLiXiaoYan) user).getIgnition() - 1);
        }
    }
}
