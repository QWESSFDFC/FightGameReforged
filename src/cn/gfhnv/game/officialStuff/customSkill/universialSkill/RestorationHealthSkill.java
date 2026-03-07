package cn.gfhnv.game.officialStuff.customSkill.universialSkill;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entity.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.mana.Mana;

import java.util.List;

public class RestorationHealthSkill extends Skill {
    private int neededManaScale;

    public RestorationHealthSkill(double hpMagnification, double atkMagnification, double defMagnification, int aims, int neededManaScale) {
        super("生命值恢复", "恢复生命值.1冷却", hpMagnification, atkMagnification, defMagnification, aims);
        this.neededManaScale = neededManaScale;
        this.setForEnemies(false);
        this.setCoolDown(1);
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
        for (LivingThing e : enemies) {
            e.setHp((long) (e.getHp() + user.getHp() * this.getHpMagnification() + user.getDfk() * this.getDefMagnification() + user.getAfk() * this.getAtkMagnification()));
        }
    }
}
