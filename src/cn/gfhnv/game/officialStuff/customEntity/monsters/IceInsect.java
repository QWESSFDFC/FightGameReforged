package cn.gfhnv.game.officialStuff.customEntity.monsters;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entityController.UniversalController;
import cn.gfhnv.game.officialStuff.customSkill.universalSkill.CommonAttack;
import cn.gfhnv.game.officialStuff.customSkill.universalSkill.Freeze;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;

import java.util.ArrayList;
import java.util.List;

public class IceInsect extends LivingThing {//没有冰属性,使用水代替
 public IceInsect(long l){
     super("冰虫子", "iceInsect", -0.2, 0.6, 0.2, 0.2, 0.2, 120, l, "insect", 30, 5, 9, ElementSort.WATER, 4, 0.5, 2);
     List<Skill> skills = new ArrayList<>();
     this.setDescription("这是冰虫子.可以冰冻敌人");
     skills.add(new CommonAttack(0, 0.3, 0, 1));
     skills.add(new Freeze());
     this.setController(new UniversalController(skills, this));
 }
}
