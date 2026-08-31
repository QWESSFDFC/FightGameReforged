package cn.gfhnv.game.entityController;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.SelectTargetEvent;
import cn.gfhnv.game.interfaces.IInitialize;
import cn.gfhnv.game.interfaces.ISpecialAction;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.ActionSignal;
import cn.gfhnv.game.system.fight.Fight;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class UniversalController {
    private List<Skill> skills = new ArrayList<>();
    private LivingThing owner;
    private ISpecialAction specialAction;
    private ActionSignal actionSignal = ActionSignal.NORMAL;
private IInitialize iInitialize=null;

    public IInitialize getiInitialize() {
        return iInitialize;
    }

    public void setiInitialize(IInitialize iInitialize) {
        this.iInitialize = iInitialize;
    }

    //不会使用物品.懒得写.
    public UniversalController(UniversalController universalController, LivingThing owner) {
        this.owner = owner;
        if (universalController.getiInitialize()!=null) this.setiInitialize(universalController.getiInitialize().copy());
        for (Skill skill : universalController.skills) {
            if (universalController.skills != null) {
                skills.add(skill.copy());

            }
        }

    }

    public UniversalController(List<Skill> skills, LivingThing owner) {
        this.owner = owner;
        this.skills = skills;
    }

    public ActionSignal getActionSignal() {
        return actionSignal;
    }

    public void setActionSignal(ActionSignal actionSignal) {
        this.actionSignal = actionSignal;
    }

    public ISpecialAction getSpecialAction() {
        return specialAction;
    }

    public void setSpecialAction(ISpecialAction specialAction) {
        this.specialAction = specialAction;
    }

    public List<Skill> getSkills() {
        return skills;
    }

    public void setSkills(List<Skill> skills) {
        List<Skill> skills1 = new ArrayList<>();
        for (Skill skill : skills) {
            skills1.add(skill.copy());
        }
        this.skills = skills1;
    }

    public LivingThing getOwner() {
        return owner;
    }

    public void setOwner(LivingThing owner) {
        this.owner = owner;
    }


    public void act(Fight fight) {
        List<Skill> approachableSkill = new ArrayList<>();
        for (Skill skill : getSkills()) {
            if (skill.canUse(fight, getOwner()) && skill.canUse(fight, getOwner(), null)) {
                approachableSkill.add(skill);
            }
        }
        if (approachableSkill.isEmpty()) {
            System.out.println(getOwner().getName() + "没行动");
            return;
        }
        Random rand = new Random();
        Skill selectedSkill = approachableSkill.get(rand.nextInt(approachableSkill.size()));
        if (selectedSkill.getAims() == 0) {
            selectedSkill.use(fight, owner);
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

    public void useItem(Fight fight) {


    }
}
