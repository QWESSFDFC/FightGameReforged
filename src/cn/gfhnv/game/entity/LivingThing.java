package cn.gfhnv.game.entity;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.event.DamageEvent;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.fight.TurnEntry;

import java.util.ArrayList;
import java.util.List;

import static cn.gfhnv.game.system.fight.TurnManager.actionQueue;

public class LivingThing extends Entity {
    private double fireResistance, waterResistance, metalResistance, woodResistance, dirtResistance, hpMax;
    private double hpMagnification, atkMagnification, dfkMagnification;
    private double criticalDMG;
    private double getCriticalRATE;
    private boolean Alive = true;
    private List<Effect> entityEffectList = new ArrayList<>();
    private double chuantong = 0;
    private double damageAbsorbedPercent = 0;
    private long hp, dfk, speed, afk;
    private double enhance;
    private double defenseLoss;
    private Fight participateFight;
    private TurnEntry presentTurn;

    public LivingThing(String name, String id, double fireResistance, double waterResistance, double metalResistance, double woodResistance, double dirtResistance, long speed, long l, String type, double hp, double atk, double dfk, ElementSort yu, double hpMagnification, double atkMagnification, double dfkMagnification) {
        super(name, id, l, yu);
        this.setHpGrowNumber(hp);
        this.setAtkGrowNumber(atk);
        this.setDfkGrowNumber(dfk);
        this.fireResistance = fireResistance;
        this.waterResistance = waterResistance;
        this.metalResistance = metalResistance;
        this.woodResistance = woodResistance;
        this.hpMagnification = hpMagnification;
        this.atkMagnification = atkMagnification;
        this.dfkMagnification = dfkMagnification;
        this.dirtResistance = dirtResistance;
        this.setType(type);
        Alive = true;
        this.hp = (long) ((l - 1) * getHpGrowNumber() + 200);
        this.dfk = (long) ((l - 1) * getDfkGrowNumber() + 200);
        this.speed = speed;
        this.afk = (long) (110 + getAtkGrowNumber() * (l - 1));
        this.defenseLoss = 0;
        this.enhance = 0;
        this.hpMax = this.hp;
    }

    public LivingThing(String name, String id, long l, ElementSort u) {
        super(name, id, l, u);
    }

    public LivingThing(long speed) {
        this.speed = speed;

    }

    public TurnEntry getPresentTurn() {
        return presentTurn;
    }

    public void setPresentTurn(TurnEntry presentTurn) {
        this.presentTurn = presentTurn;
    }

    public void onActionTaken() {
        double nextTime = this.getPresentTurn().getoValue() + 1000.0 / this.getSpeed();
        TurnEntry nextEntry = new TurnEntry(nextTime, this);
        actionQueue.offer(nextEntry);
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

    public double getDfkMagnification() {
        return dfkMagnification;
    }

    public void setDfkMagnification(double dfkMagnification) {
        this.dfkMagnification = dfkMagnification;
    }

    public double getFireResistance() {
        return fireResistance;
    }

    public void setFireResistance(double fireResistance) {
        this.fireResistance = fireResistance;
    }

    public double getWaterResistance() {
        return waterResistance;
    }

    public void setWaterResistance(double waterResistance) {
        this.waterResistance = waterResistance;
    }

    public double getMetalResistance() {
        return metalResistance;
    }

    public void setMetalResistance(double metalResistance) {
        this.metalResistance = metalResistance;
    }

    public double getWoodResistance() {
        return woodResistance;
    }

    public void setWoodResistance(double woodResistance) {
        this.woodResistance = woodResistance;
    }

    public double getDirtResistance() {
        return dirtResistance;
    }

    public void setDirtResistance(double dirtResistance) {
        this.dirtResistance = dirtResistance;
    }

    public double getGetCriticalRATE() {
        return getCriticalRATE;
    }

    public void setGetCriticalRATE(double getCriticalRATE) {
        this.getCriticalRATE = getCriticalRATE;
    }

    public Fight getParticipateFight() {
        return participateFight;
    }

    public void setParticipateFight(Fight participateFight) {
        this.participateFight = participateFight;
    }

    public List<Effect> getEntityEffectList() {
        return entityEffectList;
    }

    public void setEntityEffectList(List<Effect> entityEffectList) {
        this.entityEffectList = entityEffectList;
    }

    public void addEffect(LivingThing target, Effect effect) {
        if (target.getEntityEffectList().contains(effect)) {
            for (Effect e : target.entityEffectList) {
                if (e.equals(effect)) {
                    if (e.getLevel() >= effect.getLevel()) {
                        e.setLevel(effect.getLevel() + e.getLevel());
                        return;
                    }
                    target.entityEffectList.remove(e);
                    target.entityEffectList.add(effect);
                }
            }
        }
        target.entityEffectList.add(effect);
    }

    public void addEffect(Effect effect) {

        if (this.getEntityEffectList().contains(effect)) {
            for (Effect e : this.entityEffectList) {
                if (e.equals(effect)) {
                    if (e.getLevel() >= effect.getLevel()) {
                        e.setLevel(effect.getLevel() + e.getLevel());
                        return;
                    }
                    this.entityEffectList.remove(e);
                    this.entityEffectList.add(effect);
                }
            }
        }
        this.entityEffectList.add(effect);
    }

    public void removeEffect(Effect ef) {
        this.entityEffectList.remove(ef);
    }

    public double getDamageAbsorbedPercent() {
        return damageAbsorbedPercent;
    }

    public void setDamageAbsorbedPercent(double damageAbsorbedPercent) {
        this.damageAbsorbedPercent = damageAbsorbedPercent;
    }

    public double getChuantong() {
        return chuantong;
    }

    public void setChuantong(double chuantong) {
        this.chuantong = chuantong;
    }

    public double getHpMax() {
        return hpMax;
    }

    public void setHpMax(double hpMax) {
        this.hpMax = hpMax;
    }

    public boolean isAlive() {
        return Alive;
    }

    public void setAlive(boolean b) {
        this.Alive = b;
    }

    public double getEnhance() {
        return enhance;
    }

    public void setEnhance(double enhance) {
        this.enhance = enhance;
    }

    public double getDefenseLoss() {
        return defenseLoss;
    }

    public void setDefenseLoss(double defenseLoss) {
        this.defenseLoss = defenseLoss;
    }


    public long getAfk() {
        return afk;
    }

    public void setAfk(long afk) {
        this.afk = afk;
    }

    private void getDamage(DamageEvent da) {
        EventBus.post(da);
        this.hp -= da.getDamage().getDamageAmount();
    }

    public void makeDamage(LivingThing attacked) {
        EventBus.post(new DamageEvent(this, attacked));
        attacked.getDamage(new DamageEvent(this, attacked));
    }

    public long getSpeed() {
        return speed;
    }

    public void setSpeed(long speed) {
        this.speed = speed;
    }

    public long getDfk() {
        return dfk;
    }

    public void setDfk(long dfk) {
        this.dfk = dfk;
    }

    public long getHp() {
        return hp;
    }

    public void setHp(long hp) {
        this.hp = hp;
    }

    public double getCriticalDMG() {
        return criticalDMG;
    }

    public void setCriticalDMG(double criticalDMG) {
        this.criticalDMG = criticalDMG;
    }

    @Override
    public String toString() {
        return "LivingThing{" +
                "fireResistance=" + fireResistance +
                ", waterResistance=" + waterResistance +
                ", metalResistance=" + metalResistance +
                ", woodResistance=" + woodResistance +
                ", dirtResistance=\n" + dirtResistance +
                ", hpMax=" + hpMax +
                ", hpMagnification=" + hpMagnification +
                ", atkMagnification=" + atkMagnification +
                ", dfkMagnification=" + dfkMagnification +
                ", criticalDMG=" + criticalDMG +
                ", getCriticalRATE=\n" + getCriticalRATE +
                ", Alive=" + Alive +
                ", entityEffectList=" + entityEffectList +
                ", chuantong=" + chuantong +
                ", hp=" + hp +
                ", dfk=" + dfk +
                ", speed=" + speed +
                ", afk=" + afk +
                ", enhance=" + enhance +
                ", dfkloss=" + defenseLoss +
                '}';
    }
}
