package cn.gfhnv.game.entity;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.entityController.PlayerController;
import cn.gfhnv.game.entity.entityController.UniversalController;
import cn.gfhnv.game.entity.skill.Skill;
import cn.gfhnv.game.event.*;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.fight.TurnEntry;
import cn.gfhnv.game.system.mana.Mana;
import org.json.JSONArray;
import org.json.JSONObject;

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
    private double enhance;//全属性
    private double metalDamageEnhance,woodDamageEnhance,waterDamageEnhance,fireDamageEnhance,dirtDamageEnhance;
    private double defenseLoss;

    public double getMetalDamageEnhance() {
        return metalDamageEnhance;
    }

    public void setMetalDamageEnhance(double metalDamageEnhance) {
        this.metalDamageEnhance = metalDamageEnhance;
    }

    public double getWoodDamageEnhance() {
        return woodDamageEnhance;
    }

    public void setWoodDamageEnhance(double woodDamageEnhance) {
        this.woodDamageEnhance = woodDamageEnhance;
    }

    public double getWaterDamageEnhance() {
        return waterDamageEnhance;
    }

    public void setWaterDamageEnhance(double waterDamageEnhance) {
        this.waterDamageEnhance = waterDamageEnhance;
    }

    public double getFireDamageEnhance() {
        return fireDamageEnhance;
    }

    public void setFireDamageEnhance(double fireDamageEnhance) {
        this.fireDamageEnhance = fireDamageEnhance;
    }

    public double getDirtDamageEnhance() {
        return dirtDamageEnhance;
    }
    public long extraDamage=0;

    public void setExtraDamage(long extraDamage) {
        this.extraDamage = extraDamage;
    }

    public long getExtraDamage() {
        return extraDamage;
    }

    public void setDirtDamageEnhance(double dirtDamageEnhance) {
        this.dirtDamageEnhance = dirtDamageEnhance;
    }
    public void updateSelf(){}//每回合执行.可以写天赋等更新状态
    private Fight participateFight;
    private TurnEntry presentTurn;
    private UniversalController controller;
    private String description;
   private double individualMultipleArea=1;

    public double getIndividualMultipleArea() {
        return individualMultipleArea;
    }

    public void setIndividualMultipleArea(double individualMultipleArea) {
        this.individualMultipleArea = individualMultipleArea;
    }

    public LivingThing() {

    }

    public LivingThing(LivingThing other) {
        super(other.getName(), other.getId(), other.getLevel(), other.getElementSort());
        this.fireResistance = other.fireResistance;
        this.waterResistance = other.waterResistance;
        this.metalResistance = other.metalResistance;
        this.woodResistance = other.woodResistance;
        this.dirtResistance = other.dirtResistance;
        this.description = other.description;
        this.speed = other.speed;
        this.hpMagnification = other.hpMagnification;
        this.atkMagnification = other.atkMagnification;
        this.dfkMagnification = other.dfkMagnification;
        this.setType(other.getType());
        this.Alive = other.Alive;
        this.defenseLoss = other.defenseLoss;
        this.enhance = other.enhance;
        this.setHpGrowNumber(other.getHpGrowNumber());
        this.setAtkGrowNumber(other.getAtkGrowNumber());
        this.setDfkGrowNumber(other.getDfkGrowNumber());
        this.setMetalManaGrowNumber(other.getMetalManaGrowNumber());
        this.setWoodManaGrowNumber(other.getWoodManaGrowNumber());
        this.setWaterManaGrowNumber(other.getWaterManaGrowNumber());
        this.setFireManaGrowNumber(other.getFireManaGrowNumber());
        this.setDirtManaGrowNumber(other.getDirtManaGrowNumber());
        this.hp = other.hp;
        this.dfk = other.dfk;
        this.afk = other.afk;
        this.hpMax = other.hpMax;
        this.criticalDMG = other.criticalDMG;
        this.getCriticalRATE = other.getCriticalRATE;
        this.entityEffectList = new ArrayList<>(other.entityEffectList);
        this.chuantong = other.chuantong;
        this.damageAbsorbedPercent = other.damageAbsorbedPercent;
        this.participateFight = null;
        this.presentTurn = null;
        this.controller = new UniversalController(other.controller, this);
        if (other.getController() instanceof PlayerController) {
            this.controller = new PlayerController(other.controller.getSkills(), this);
        } else {
            this.controller = new UniversalController(other.controller, this);
        }
        if (!other.getManas().isEmpty()) {
            for (Mana mana : other.getManas()) {
                this.getManas().add(mana);
            }
        }
    }

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
        this.speed = speed;
        this.defenseLoss = 0;
        this.enhance = 0;
        this.hp = (long) ((l - 1) * getHpGrowNumber() + 200);
        this.dfk = (long) ((l - 1) * getDfkGrowNumber() + 200);
        this.afk = (long) (110 + getAtkGrowNumber() * (l - 1));
        this.hpMax = this.hp;
        switch (this.getElementSort()) {
            case METAL -> {
                this.setMetalManaGrowNumber(20);
                this.setWoodManaGrowNumber(4);
                this.setWaterManaGrowNumber(10);
                this.setFireManaGrowNumber(1);
                this.setDirtManaGrowNumber(10);
            }
            case WOOD -> {
                this.setMetalManaGrowNumber(1);
                this.setWoodManaGrowNumber(20);
                this.setWaterManaGrowNumber(10);
                this.setFireManaGrowNumber(10);
                this.setDirtManaGrowNumber(4);
            }
            case WATER -> {
                this.setMetalManaGrowNumber(10);
                this.setWoodManaGrowNumber(10);
                this.setWaterManaGrowNumber(20);
                this.setFireManaGrowNumber(4);
                this.setDirtManaGrowNumber(1);
            }
            case FIRE -> {
                this.setMetalManaGrowNumber(4);
                this.setWoodManaGrowNumber(10);
                this.setWaterManaGrowNumber(1);
                this.setFireManaGrowNumber(20);
                this.setDirtManaGrowNumber(10);
            }
            case DIRT -> {
                this.setMetalManaGrowNumber(10);
                this.setWoodManaGrowNumber(1);
                this.setWaterManaGrowNumber(4);
                this.setFireManaGrowNumber(10);
                this.setDirtManaGrowNumber(20);
            }
        }
        this.initialMana();
    }

    public LivingThing(String name, String id, long l, ElementSort u) {
        super(name, id, l, u);
    }

    public LivingThing(long speed) {
        this.speed = speed;

    }

    public LivingThing livingThingFactory() {
        return new LivingThing();
    }

    public LivingThing facSetLevel(Long level) {
        this.setLevel(level);
        return this;
    }

    public LivingThing facSetFireResistance(double fireResistance) {
        this.fireResistance = fireResistance;
        return this;
    }

    public LivingThing facSetWaterResistance(double waterResistance) {
        this.waterResistance = waterResistance;
        return this;
    }

    public LivingThing facSetMetalResistance(double metalResistance) {
        this.metalResistance = metalResistance;
        return this;
    }

    public LivingThing facSetWoodResistance(double woodResistance) {
        this.woodResistance = woodResistance;
        return this;
    }

    public LivingThing facSetDirtResistance(double dirtResistance) {
        this.dirtResistance = dirtResistance;
        return this;
    }

    public LivingThing facSetHpMax(double hpMax) {
        this.hpMax = hpMax;
        return this;
    }

    public LivingThing facSetHpMagnification(double hpMagnification) {
        this.hpMagnification = hpMagnification;
        return this;
    }

    public LivingThing facSetAtkMagnification(double atkMagnification) {
        this.atkMagnification = atkMagnification;
        return this;
    }

    public LivingThing facSetDfkMagnification(double dfkMagnification) {
        this.dfkMagnification = dfkMagnification;
        return this;
    }

    public LivingThing facSetCriticalDMG(double criticalDMG) {
        this.criticalDMG = criticalDMG;
        return this;
    }

    public LivingThing facSetCriticalRATE(double criticalRATE) {
        this.getCriticalRATE = criticalRATE;
        return this;
    }

    public LivingThing facSetAlive(boolean alive) {
        this.Alive = alive;
        return this;
    }

    public LivingThing facSetChuantong(double chuantong) {
        this.chuantong = chuantong;
        return this;
    }

    public LivingThing facSetDamageAbsorbedPercent(double damageAbsorbedPercent) {
        this.damageAbsorbedPercent = damageAbsorbedPercent;
        return this;
    }

    public LivingThing facSetHp(long hp) {
        this.setHp(hp);
        return this;
    }

    public LivingThing facSetDfk(long dfk) {
        this.dfk = dfk;
        return this;
    }

    public LivingThing facSetSpeed(long speed) {
        this.speed = speed;
        return this;
    }

    public LivingThing facSetAfk(long afk) {
        this.afk = afk;
        return this;
    }

    public LivingThing facSetEnhance(double enhance) {
        this.enhance = enhance;
        return this;
    }

    public LivingThing facSetDefenseLoss(double defenseLoss) {
        this.defenseLoss = defenseLoss;
        return this;
    }

    public LivingThing facSetDescription(String description) {
        this.description = description;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UniversalController getController() {
        return controller;
    }

    public void setController(UniversalController controller) {
        this.controller = controller;
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
        this.setPresentTurn(nextEntry);
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
                        e.setLastTime(effect.getLastTime() + e.getLevel());
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
        if (getHp() <= 0) {
            Alive = false;
        }
        if (getHp() > 0) {
            Alive = true;
        }
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

    public void getDamage(DamageEvent da) {
        long newHp = this.getHp() - da.getDamage().getDamageAmount();
        newHp = applyDamageModifiers(newHp, da);
        this.setHp(newHp);
        System.out.print("剩余HP" + this.getHp());
    }
    public LivingThing copy() {
        return new LivingThing(this);
    }
    /**
     * 子类可重写此方法，在血量被扣减前进行修正（如锁血、免死等）。
     * @param newHp 计算出的新血量（当前血量 - 伤害值）
     * @param da    伤害事件
     * @return 修正后的新血量
     */
    protected long applyDamageModifiers(long newHp, DamageEvent da) {
        return newHp;
    }

    public void makeDamage(LivingThing attacked, Skill skill) {
        DamageEvent damageEvent = new DamageEvent(this, attacked, skill);
        System.out.print("造成了" + damageEvent.getDamage().getDamageAmount());
        attacked.getDamage(damageEvent);

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
        if (hp<getHp()){
            EventBus.post(new HpLossEvent(getHp()-hp,this));
        }
        if (hp>getHp()){
            EventBus.post(new HpRestorationEvent(hp-getHp(),this));
        }
        this.hp = (long) Math.min(this.getHpMax(), hp);
        if (this.hp<0){
            this.hp=0;
        }
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
                ", dirtResistance=" + dirtResistance +
                ", hpMax=" + hpMax +
                ", hpMagnification=" + hpMagnification +
                ", atkMagnification=" + atkMagnification +
                ", dfkMagnification=" + dfkMagnification +
                ", criticalDMG=" + criticalDMG +
                ", getCriticalRATE=" + getCriticalRATE +
                ", Alive=" + Alive +
                ", entityEffectList=" + entityEffectList +
                ", chuantong=" + chuantong +
                ", damageAbsorbedPercent=" + damageAbsorbedPercent +
                ", hp=" + hp +
                ", dfk=" + dfk +
                ", speed=" + speed +
                ", afk=" + afk +
                ", enhance=" + enhance +
                ", defenseLoss=" + defenseLoss +
                ", participateFight=" + participateFight +
                ", presentTurn=" + presentTurn +
                ", controller=" + controller +
                ", description='" + description + '\'' +
                '}';
    }

}
