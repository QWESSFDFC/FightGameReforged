package cn.gfhnv.game.officialStuff.customSkill.universalSkill;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.mana.Mana;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;

import java.util.List;

public class RestorationHealthSkill extends Skill {
    private int neededManaScale;

    public RestorationHealthSkill(double hpMagnification, double atkMagnification, double defMagnification, int aims, int neededManaScale) {
        super("生命值恢复", "恢复生命值.1冷却", hpMagnification, atkMagnification, defMagnification, aims);
        this.neededManaScale = neededManaScale;
        this.setForEnemies(false);
        this.getTags().put(TagType.HEAL, new Tag(1));
        this.setCoolDown(1);
    }

    public RestorationHealthSkill(RestorationHealthSkill restorationHealthSkill) {
        super(restorationHealthSkill);
    }

    @Override
    public Skill copy() {
        return new RestorationHealthSkill(this);
    }

    @Override
    public boolean canUse(Fight fight, LivingThing user, List<LivingThing> enemies) {
        for (Mana mana : user.getManas()) {
            if (mana.getElementSort().equals(user.getElementSort())) {
                if (mana.getAmount() >= this.neededManaScale) {
                    mana.setAmount(mana.getAmount() - neededManaScale);
                    return true;
                }
            }
        }
        System.out.println("能量不足");
        return false;
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        long restoredHP = (long) (user.getHp() * this.getHpMagnification() + user.getDefence() * this.getDefMagnification() + user.getAttack() * this.getAtkMagnification());
        for (LivingThing e : enemies) {
            e.setHp(e.getHp() + restoredHP);
            System.out.println(user.getName() + "为" + e.getName() + "恢复了" + restoredHP + "点生命值");
        }

    }
}
