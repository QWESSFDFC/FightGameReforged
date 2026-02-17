package cn.gfhnv.game.entity.entityController;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entity.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;

import java.util.List;
import java.util.Random;

public class UniversalController {
    private List<Skill> skills;
    private LivingThing owner;
    private String input;

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

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public void act(Fight fight) {
        if (!owner.getController().skills.isEmpty()) {
            Skill[] canUseSkil = new Skill[skills.size()];
            canUseSkil = skills.toArray(canUseSkil);
            Random rand = new Random();
            canUseSkil[rand.nextInt(canUseSkil.length)].comeToEffect(fight, owner);
        }
    }

}
