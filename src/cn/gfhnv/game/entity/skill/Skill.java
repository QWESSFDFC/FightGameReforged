package cn.gfhnv.game.entity.skill;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.mana.Mana;

import java.util.List;

public class Skill {
    private String name;
    private String description;
    private double hpMagnification = 0;
    private double atkMagnification = 0;
    private double defMagnification = 0;
    private int aims;
    private int coolDown;
    private int nowCoolDown = 0;
    private boolean isForEnemies = true;
    private Mana consumedMana;

    public Skill(String name, String description, double hpMagnification, double atkMagnification, double defMagnification, int aims) {
        this.name = name;
        this.description = description;
        this.hpMagnification = hpMagnification;
        this.atkMagnification = atkMagnification;
        this.defMagnification = defMagnification;
        this.aims = aims;
    }

    public boolean isForEnemies() {
        return isForEnemies;
    }

    public void setForEnemies(boolean forEnemies) {
        isForEnemies = forEnemies;
    }

    public int getNowCoolDown() {
        return nowCoolDown;
    }

    public void setNowCoolDown(int nowCoolDown) {
        this.nowCoolDown = nowCoolDown;
    }

    public int getCoolDown() {
        return coolDown;
    }

    public void setCoolDown(int coolDown) {
        this.coolDown = coolDown;
    }

    public int getAims() {
        return aims;
    }

    public void setAims(int aims) {
        this.aims = aims;
    }

    public boolean canUse(Fight fight, LivingThing user, List<LivingThing> enemies) {
        boolean canUse = false;
        boolean canUseMana = false;
        for (Mana mana : user.getManas()) {
            if (this.getConsumedMana() == null) {
                canUseMana = true;
                break;
            }
            if (consumedMana.getElementSort().equals(ElementSort.UNIVERSAL)) {
                if (mana.getAmount() >= consumedMana.getAmount()) {
                    canUseMana = true;

                    break;
                }
            }
            if (mana.getElementSort().equals(consumedMana.getElementSort())) {
                canUseMana = mana.getAmount() >= consumedMana.getAmount();

                break;
            }
        }
        if (canUseMana && nowCoolDown <= 0) {
            canUse = true;
        }
        return canUse;
    }

    //两个重写一个.如果true里面消耗mana
    public boolean canUse(Fight fight, LivingThing user) {
        boolean canUse = false;
        boolean canUseMana = false;
        for (Mana mana : user.getManas()) {
            if (this.getConsumedMana() == null) {
                canUseMana = true;
                break;
            }
            if (consumedMana.getElementSort().equals(ElementSort.UNIVERSAL)) {
                if (mana.getAmount() >= consumedMana.getAmount()) {
                    canUseMana = true;

                    break;
                }
            }
            if (mana.getElementSort().equals(consumedMana.getElementSort())) {
                canUseMana = mana.getAmount() >= consumedMana.getAmount();

                break;
            }
        }
        if (canUseMana && nowCoolDown <= 0) {
            canUse = true;
        }
        return canUse;
    }

    public boolean use(Fight fight, LivingThing user, List<LivingThing> enemies) {
        if (this.canUse(fight, user, enemies)) {
            this.comeToEffect(fight, user, enemies);
            this.setNowCoolDown(this.getCoolDown() + 1);
            for (Mana mana : user.getManas()) {
                if (this.getConsumedMana() == null) {
                    break;
                }
                if (consumedMana.getElementSort().equals(ElementSort.UNIVERSAL)) {
                    if (mana.getAmount() >= consumedMana.getAmount()) {
                        if (nowCoolDown <= 0) {
                            mana.setAmount(mana.getAmount() - consumedMana.getAmount());
                        }
                        break;
                    }
                }
                if (mana.getElementSort().equals(consumedMana.getElementSort())) {
                    if (nowCoolDown <= 0) {
                        mana.setAmount(mana.getAmount() - consumedMana.getAmount());
                    }
                    break;
                }
            }
            return true;
        }
        return false;
    }

    public boolean use(Fight fight, LivingThing user) {
        if (this.canUse(fight, user)) {
            this.comeToEffect(fight, user);
            this.setNowCoolDown(this.getCoolDown() + 1);
            for (Mana mana : user.getManas()) {
                if (this.getConsumedMana() == null) {
                    break;
                }
                if (consumedMana.getElementSort().equals(ElementSort.UNIVERSAL)) {
                    if (mana.getAmount() >= consumedMana.getAmount()) {
                        if (nowCoolDown <= 0) {
                            mana.setAmount(mana.getAmount() - consumedMana.getAmount());
                        }
                        break;
                    }
                }
                if (mana.getElementSort().equals(consumedMana.getElementSort())) {
                    if (nowCoolDown <= 0) {
                        mana.setAmount(mana.getAmount() - consumedMana.getAmount());
                    }
                    break;
                }
            }
            return true;
        }
        return false;
    }

    public void comeToEffect(Fight fight, LivingThing user) {
    }

    //两个重写一个.尽量不直接调用comeToEffect
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
    }

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

    public Mana getConsumedMana() {
        return consumedMana;
    }

    public void setConsumedMana(Mana consumedMana) {
        this.consumedMana = consumedMana;
    }
}
