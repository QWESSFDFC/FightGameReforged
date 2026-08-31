package cn.gfhnv.game.entityController;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.SelectTargetEvent;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;


import java.util.*;

public class FixOrderController extends UniversalController {

   public FixOrderController(List<Skill> skills, LivingThing owner){
       super(skills, owner);
   }
   private Queue<Skill> skillQueue=new LinkedList<>();

    public Queue<Skill> getSkillQueue() {
        return skillQueue;
    }

    @Override
    public void act(Fight fight) {
       if (skillQueue.isEmpty()){
           if (this.getiInitialize()!=null) this.getiInitialize().initialize(this);
           if (skillQueue.isEmpty()) {
               System.out.println(getOwner().getName()+"没行动");
               return;
           }
       }
        Random rand = new Random();
        Skill selectedSkill=skillQueue.poll();
        if (selectedSkill.getAims() == 0) {
            selectedSkill.use(fight, getOwner());
            return;
        }
        boolean targetIsEnemy = selectedSkill.isForEnemies();
        List<LivingThing> candidates;
        if (targetIsEnemy) {candidates=new ArrayList<>(fight.getOpponentList(getOwner()));}
        else {
            candidates=new ArrayList<>(fight.getOwnList(getOwner()));
        }
        List<LivingThing> targets;
        if (selectedSkill.getAims() == -1) {
            targets = new ArrayList<>(candidates);
        } else {
            int aimCount = Math.min(selectedSkill.getAims(), candidates.size());
            Collections.shuffle(candidates, rand);
            targets = candidates.subList(0, aimCount);
        }
        selectedSkill.use(fight, getOwner(), targets);
        EventBus.post(new SelectTargetEvent(getOwner(), targets, fight));
    }
}
