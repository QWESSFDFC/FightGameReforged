package cn.gfhnv.game.system.thinkingSystem;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entityController.UniversalController;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.fight.TurnEntry;
import cn.gfhnv.game.system.fight.TurnManager;

import java.util.ArrayList;
import java.util.List;

public class ThinkingController extends UniversalController {
    public ThinkingController(UniversalController universalController, LivingThing owner) {
        super(universalController, owner);
    }

    public ThinkingController(List<Skill> skills, LivingThing owner) {
        super(skills, owner);
    }

    /*
     * 大概设计
     * 先检查自身状态
     * 再检查敌人状态
     * 预测会受到的伤害(以敌方可使用的伤害最高的技能为标准(也算效果造成的伤害减少))和可以造成的伤害
     * 如果可以获得胜利就直接攻击
     * 接着如果血量会被秒杀就看看能不能回血或加防御力(以受到攻击后剩余血量高的为主)
     * 如果血量健康就使用可以加伤害的物品或技能(如果有)------>使用物品的系统没做呢.预计会做
     * 以自身tag和技能/物品tag判断  结合自身各个tag优先级判断
     * 再比较物品加的数值高低和持续时间
     *算权重,比较,选高的那个
     *一定有攻击的
     * */
    @Override
    public void act(Fight fight) {
        if (getOwner().getTags().isEmpty()) {
            //无法思考采用默认控制器
            super.act(fight);
            return;
        }
        double attackWeight = 1;//
        double defenseWeight = 0;//
        double healthWeight = 0;
        double manaRestorationWeight = 0;
        double damageEnhanceWeight = 0;
        double hpPercent = getOwner().getHp() / getOwner().getHpMax();
        double anticipatedDamage = 0;
        boolean nextTurnIsEnemy = false;
        TurnEntry nextTurn = TurnManager.getTurns().getFirst();
        if (fight.getOpponentList(getOwner()).contains(nextTurn.getLivingThing())) {
            nextTurnIsEnemy = true;
        }
        if (nextTurnIsEnemy) {
            List<Long> damages = new ArrayList<>();
            List<Skill> eUsableSkills = new ArrayList<>();
            for (Skill skill : nextTurn.getLivingThing().getController().getSkills()) {
                if (skill == null) continue;
                if (skill.canUse(fight, nextTurn.getLivingThing()) && skill.canUse(fight, nextTurn.getLivingThing(), null)) {
                    damages.add(skill.getAnticipatedDamage(getOwner(), nextTurn.getLivingThing()));
                }
            }
            damages.sort((o1, o2) -> {
                if (o1 > o2) return -1;
                if (o1 < o2) return 1;
                return 0;
            });
            anticipatedDamage = damages.getFirst();
        }
        long attackDamage = 0;
        LivingThing target = getOwner();
        List<Skill> skills = new ArrayList<>();
        for (Skill skill : getOwner().getController().getSkills()) {
            if (skill.canUse(fight, getOwner()) && skill.canUse(fight, getOwner(), null)) {
                skills.add(skill);
            }
        }
        List<Skill> attackSkill = new ArrayList<>();
        List<LivingThing> livingThings = new ArrayList<>(fight.getOpponentList(getOwner()));
        livingThings.sort((o1, o2) -> {
            if (o1.getHp() > o2.getHp()) return 1;
            if (o1.getHp() < o2.getHp()) return -1;
            return 0;
        });
        target = livingThings.getFirst();

        for (Skill skill : skills) {
            if (!skill.isForEnemies()) continue;
            attackSkill.add(skill);

        }


        attackWeight = getWeight(TagType.ATTACK);
        defenseWeight = getWeight(TagType.DEFENCE);
        healthWeight = getWeight(TagType.HEAL);
        manaRestorationWeight = getWeight(TagType.RESTORATION_MANA);
        damageEnhanceWeight = getWeight(TagType.DAMAGE_ENHANCE);
        System.out.printf(this.getOwner().getName() + "正在思考");
        if (getOwner().getHp() - anticipatedDamage <= 0) healthWeight = healthWeight * 10;
        double total = attackWeight + defenseWeight + healthWeight + manaRestorationWeight + damageEnhanceWeight;
        double attackPos = attackWeight / total;
        double defendPos = defenseWeight / total;
        double healPos = healthWeight / total;
        double manaPos = manaRestorationWeight / total;
        double damageEnPos = damageEnhanceWeight / total;


        //无法思考采用默认控制器
        System.out.println(getOwner().getName() + "无法思考");
        super.act(fight);
    }

    private double getWeight(TagType tagType) {
        if (!getOwner().getTags().containsKey(tagType)) return 0;
        else return getOwner().getTags().get(tagType).getWeight();
    }

}

