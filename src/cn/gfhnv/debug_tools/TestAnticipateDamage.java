package cn.gfhnv.debug_tools;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEntity.monsters.CommonInsect;
import cn.gfhnv.game.officialStuff.customEntity.players.ActorLiXiaoYan;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;

import java.util.ArrayList;
import java.util.List;

public class TestAnticipateDamage {
    static void main() {
        for (Skill skill : new ActorLiXiaoYan(100).getController().getSkills()) {
            System.out.println("技能：" + skill.getName());
            LivingThing player = new ActorLiXiaoYan(100);//这是等级
            LivingThing monster = new CommonInsect(100L);//这是等级
            monster.setHpMax(2155555);
            monster.setHp(2155555);
            System.out.println(monster.getHp() + "/" + monster.getHpMax());
            List<LivingThing> players = new ArrayList<>();
            players.add(player);
            List<LivingThing> monsters = new ArrayList<>();
            monsters.add(monster);
            Fight fight = new Fight(monsters, null, players);

            long beforeHp = monster.getHp();
            long anticipated = skill.getAnticipatedDamage(monster, player);
            System.out.println("预估伤害：" + anticipated);
            long predictedRemain = Math.max(0, beforeHp - anticipated);
            System.out.println("预估剩余HP：" + predictedRemain);

            skill.comeToEffect(fight, player, monsters);

            long actualDamage = beforeHp - monster.getHp();
            if (actualDamage < 0) actualDamage = 0;
            System.out.println("实际伤害：" + actualDamage);
            System.out.println("实际剩余HP：" + monster.getHp());
            System.out.println("------------------------");
        }
    }
}