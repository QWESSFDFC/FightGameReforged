package cn.gfhnv.game.entity.entityController;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entity.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class UniversalController {
    private List<Skill> skills;
    private LivingThing owner;

    public UniversalController(UniversalController universalController, LivingThing owner) {
        this.owner = owner;
        this.skills = universalController.skills;
    }

    public UniversalController(List<Skill> skills, LivingThing owner) {
        this.owner = owner;
        this.skills = skills;
    }

    public List<Skill> getSkills() {
        return skills;
    }

    public void setSkills(List<Skill> skills) {
        this.skills = skills;
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
        Skill[] canUseSkil = new Skill[approachableSkill.size()];
        canUseSkil = approachableSkill.toArray(canUseSkil);
        if (approachableSkill.isEmpty()) {
            System.out.println(getOwner().getName() + "没行动");
            return;
        }
        Random rand = new Random();
        Skill selectedSkill = canUseSkil[rand.nextInt(approachableSkill.size())];
        if (selectedSkill.getAims() == 0) {
            selectedSkill.useSkill(fight, owner);
            return;
        }
        if (fight.getEnemiesList().contains(owner)) {
            List<LivingThing> targetEnemies = new ArrayList<>();
            LivingThing[] attackableEnemies = fight.getFighterList().toArray(new LivingThing[0]);
            while (targetEnemies.size() < selectedSkill.getAims()) {
                targetEnemies.add(attackableEnemies[rand.nextInt(attackableEnemies.length)]);
            }
            selectedSkill.useSkill(fight, getOwner(), targetEnemies);
            return;
        }
        if (fight.getFighterList().contains(owner)) {
            List<LivingThing> targetEnemies = new ArrayList<>();
            LivingThing[] attackableEnemies = fight.getEnemiesList().toArray(new LivingThing[0]);
            while (targetEnemies.size() < selectedSkill.getAims()) {
                targetEnemies.add(attackableEnemies[rand.nextInt(attackableEnemies.length)]);
            }
            selectedSkill.useSkill(fight, getOwner(), targetEnemies);
        }
    }
}
