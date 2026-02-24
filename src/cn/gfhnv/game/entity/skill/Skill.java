package cn.gfhnv.game.entity.skill;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.system.fight.Fight;

import java.util.List;

public class Skill {
    private String name;
    private String description;
    private double hpMagnification = 0;
    private double atkMagnification = 0;
    private double defMagnification = 0;
    private int aims;

    public int getAims() {
        return aims;
    }

    public void setAims(int aims) {
        this.aims = aims;
    }

    public Skill(String name, String description, double hpMagnification, double atkMagnification, double defMagnification,int aims) {
        this.name = name;
        this.description = description;
        this.hpMagnification = hpMagnification;
        this.atkMagnification = atkMagnification;
        this.defMagnification = defMagnification;
        this.aims = aims;
    }

    public void comeToEffect(Fight fight, LivingThing user) {
    }
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies){}
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getHpMagnification() {
        return hpMagnification;
    }

    public void setHpMagnification(double hpMagnification) {
        this.hpMagnification = hpMagnification;
    }

    public double getAtkMagnification() {
        return atkMagnification;
    }

    public void setAtkMagnification(double atkMagnification) {
        this.atkMagnification = atkMagnification;
    }

    public double getDefMagnification() {
        return defMagnification;
    }

    public void setDefMagnification(double defMagnification) {
        this.defMagnification = defMagnification;
    }
}
