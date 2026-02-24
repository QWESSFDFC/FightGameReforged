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
        if (getOwner().getController().getSkills().isEmpty()) {
            System.out.println(getOwner().getName() + "没行动");
            return;
        }
        Skill[] canUseSkil = new Skill[skills.size()];
        canUseSkil = skills.toArray(canUseSkil);
        Random rand = new Random();
        Skill selectedSkill = canUseSkil[rand.nextInt(skills.size())];
        if (selectedSkill.getAims() == 0) {
            selectedSkill.comeToEffect(fight, getOwner());
            return;
        }
        if (fight.getEnemiesList().contains(owner)) {
            List<LivingThing> targetEnemies = new ArrayList<>();
            LivingThing[] attackableEnemies = fight.getFighterList().toArray(new LivingThing[0]);
            while (targetEnemies.size() < selectedSkill.getAims()) {
                targetEnemies.add(attackableEnemies[rand.nextInt(attackableEnemies.length)]);
            }
            selectedSkill.comeToEffect(fight, getOwner(), targetEnemies);
        }

    }
}
