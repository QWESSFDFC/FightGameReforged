package cn.gfhnv.game.entityController;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.skill.Skill;


import java.util.*;

public class FixOrderController extends UniversalController {

   public FixOrderController(List<Skill> skills, LivingThing owner){
       super(skills, owner);
   }
   private Queue<Skill> skillQueue=new LinkedList<>();

    public Queue<Skill> getSkillQueue() {
        return skillQueue;
    }
}
