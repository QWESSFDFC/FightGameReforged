package cn.gfhnv.game.system.thinkingSystem;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entityController.UniversalController;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;

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
        System.out.printf(this.getOwner().getName() + "正在思考");
    }
}
