package cn.gfhnv.game.entity;

import cn.gfhnv.game.damage.Damage;
import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.event.DamageEvent;
import cn.gfhnv.game.event.Event;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.system.ElementSort;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LivingThing extends Entity {
    private double fireResistance,waterResistance,metalResistance,woodResistance,dirtResistance,hpMax;
    private double hpMagnification,atkMagnification,dfkMagnification;

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
    private double criticalDMG;
    private double getCriticalRATE;

    public void setCriticalDMG(double criticalDMG) {
        this.criticalDMG = criticalDMG;
    }

    public double getGetCriticalRATE() {
        return getCriticalRATE;
    }

    public void setGetCriticalRATE(double getCriticalRATE) {
        this.getCriticalRATE = getCriticalRATE;
    }
    private boolean Alive=true;
  private List<Effect> entityEffectList=new ArrayList<>();

    public List<Effect> getEntityEffectList() {
        return entityEffectList;
    }

    public void addEffect(LivingThing target, Effect effect){
    if (target.entityEffectList.getFirst()!=null){
    for (Effect e:target.entityEffectList){
        if (e.getID().equals(effect.getID())){
            if (e.getLevel()>effect.getLevel()){
                return;
            }
            target.entityEffectList.remove(e);
            target.entityEffectList.add(effect);
            return;
        }
        target.entityEffectList.add(effect);
        return;
    }}
    target.entityEffectList.add(effect);
}
public void ifEffectAlive(){
    this.entityEffectList.removeIf(e -> e.getLastTime() ==0);
}

    public void setEntityEffectList(List<Effect> entityEffectList) {
        this.entityEffectList = entityEffectList;
    }
    public void removeEffect(Effect ef){
        this.entityEffectList.remove(ef);
    }

    public LivingThing(String name, double x, double y, double z, String id, double fireResistance, double waterResistance, double metalResistance, double woodResistance, double dirtResistance, long speed, LivingThing fightEntity, long l, String type, double hp, double atk, double dfk, ElementSort yu, double hpMagnification, double atkMagnification, double dfkMagnification) {
        super(name, x, y, z, id,l,yu);
        this.setHpGrowNumber(hp);
        this.setAtkGrowNumber(atk);
        this.setDfkGrowNumber(dfk);
        this.fireResistance = fireResistance;
        this.waterResistance = waterResistance;
        this.metalResistance = metalResistance;
        this.woodResistance = woodResistance;
        this.hpMagnification=hpMagnification;
        this.atkMagnification=atkMagnification;
        this.dfkMagnification=dfkMagnification;
        this.dirtResistance = dirtResistance;
        this.setType(type);
        Alive = true;
        this.hp = (long) ((l-1)*getHpGrowNumber()+200);
        this.dfk = (long) ((l-1)*getDfkGrowNumber()+200);
        this.speed = speed;
        this.afk =(long) (110+getAtkGrowNumber()*(l-1));
        this.fightEntity = fightEntity;
        this.dfkloss=0;
        this.enhance=0;
        this.hpMax=this.hp;
    }
    private double chuantong=0;
    private double damageAbsorbedPercent=0;

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
    public LivingThing(String name, double x, double y, double z, String id, long l, ElementSort u) {
        super(name, x, y, z, id,l,u);
    }
    public boolean isAlive(){
        return  Alive;
    }
    public void setAlive(boolean b){
        this.Alive=b;
    }
    private long hp,dfk,speed,afk;
    private LivingThing fightEntity;
    private double enhance;
    private double dfkloss;
    public double getEnhance() {
        return enhance;
    }
    public void setEnhance(double enhance) {
        this.enhance = enhance;
    }
    public double getDfkloss() {
        return dfkloss;
    }
    public void setDfkloss(double dfkloss) {
        this.dfkloss = dfkloss;
    }

    public LivingThing getFightEntity() {
        return fightEntity;
    }
    public void setFightEntity(LivingThing fightEntity) {
        this.fightEntity = fightEntity;
    }
    public long getAfk() {
        return afk;
    }
private void getDamage(DamageEvent da){
    EventBus.post(da);
    this.hp-=da.getDamage().getDamageAmount();
}
public void makeDamage(LivingThing attacked){
  EventBus.post(new DamageEvent(this,attacked));
  attacked.getDamage(new DamageEvent(this,attacked));
}
    public void setAfk(long afk) {
        this.afk = afk;
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
                ", fightEntity=" + fightEntity +
                ", enhance=" + enhance +
                ", dfkloss=" + dfkloss +
                '}';
    }
}
