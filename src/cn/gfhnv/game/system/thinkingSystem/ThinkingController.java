package cn.gfhnv.game.system.thinkingSystem;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entityController.UniversalController;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;

import javax.swing.text.html.HTML;
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
     * 比较(数值*0.6+时间*0.4)
     * 选高的那个
     *
     * */
    @Override
    public void act(Fight fight) {
        if (getOwner().getTags().isEmpty()) {
            //无法思考采用默认控制器
            super.act(fight);
            return;
        }
        double attackWeight =1;
        double defenseWeight = 0;
        double healthWeight = 0;
        double manaRestorationWeight = 0;
        double damageEnhanceWeight = 0;
        double hpPercent = getOwner().getHp()/getOwner().getHpMax();
        double anticipatedDamage = 0;

        List<Skill> skills = new ArrayList<>();
        for (Skill skill : getOwner().getController().getSkills()) {
            if (skill.canUse(fight, getOwner()) && skill.canUse(fight, getOwner(), null)){
                skills.add(skill);
            }
        }
        attackWeight=getWeight(TagType.ATTACK);
        defenseWeight=getWeight(TagType.DEFEND);
        healthWeight=getWeight(TagType.HEAL);
        manaRestorationWeight=getWeight(TagType.RESTORATION_MANA);
        damageEnhanceWeight=getWeight(TagType.DAMAGE_ENHANCE);
        System.out.printf(this.getOwner().getName() + "正在思考");
        //无法思考采用默认控制器
        System.out.println(getOwner().getName()+"无法思考");
        super.act(fight);
    }
    private double getWeight(TagType tagType) {if (!getOwner().getTags().containsKey(tagType)) return 0;
    else return getOwner().getTags().get(tagType).getWeight();
    }
}

