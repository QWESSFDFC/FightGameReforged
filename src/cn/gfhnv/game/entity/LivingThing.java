package cn.gfhnv.game.entity;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entityController.PlayerController;
import cn.gfhnv.game.entityController.UniversalController;
import cn.gfhnv.game.event.DamageEvent;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.HpLossEvent;
import cn.gfhnv.game.event.HpRestorationEvent;
import cn.gfhnv.game.interfaces.IShowSpecialMes;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.ActionSignal;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.fight.TurnEntry;
import cn.gfhnv.game.system.mana.Mana;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;
import cn.gfhnv.game.system.thinkingSystem.ThinkingController;

import java.util.*;

public class LivingThing extends Entity {
    public long extraDamage = 0;
    private IShowSpecialMes showSpecialMes;

    public IShowSpecialMes getShowSpecialMes() {
        return showSpecialMes;
    }

    public void setShowSpecialMes(IShowSpecialMes showSpecialMes) {
        this.showSpecialMes = showSpecialMes;
    }

    private double fireResistance, waterResistance, metalResistance, woodResistance, dirtResistance, hpMax, criticalDMG, getCriticalRATE;
    private long hp, defence, speed, attack;
    private boolean Alive = true;
    private List<Effect> entityEffectList = new ArrayList<>();
    private double penetration = 0;//全属性穿透
    private double metalPenetration, woodPenetration, waterPenetration, firePenetration, dirtPenetration;
    private double damageAbsorbedPercent = 0;
    private double hpGrowNumber;
    private double atkGrowNumber;
    private double dfkGrowNumber;
    private ElementSort elementSort;
    private double metalManaGrowNumber;
    private double woodManaGrowNumber;
    private double waterManaGrowNumber;
    private double fireManaGrowNumber;
    private double dirtManaGrowNumber;
    private List<Mana> manas = new ArrayList<>();//一个实体可以拥有多个Mana
    private double enhance;//全属性
    private double metalDamageEnhance, woodDamageEnhance, waterDamageEnhance, fireDamageEnhance, dirtDamageEnhance;
    private double defenseLoss;
    private Fight participateFight;
    private TurnEntry presentTurn;
    private UniversalController controller;
    private String description;
    private double individualMultipleArea = 1;


    public LivingThing() {

    }


    public LivingThing(LivingThing other) {
        super(other.getName(), other.getId(), other.getLevel());
        this.fireResistance = other.fireResistance;
        this.waterResistance = other.waterResistance;
        this.metalResistance = other.metalResistance;
        this.woodResistance = other.woodResistance;
        this.dirtResistance = other.dirtResistance;
        this.description = other.description;
        this.speed = other.speed;
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
        this.defence = other.defence;
        this.attack = other.attack;
        this.hpMax = other.hpMax;
        this.criticalDMG = other.criticalDMG;
        this.getCriticalRATE = other.getCriticalRATE;
        this.entityEffectList = new ArrayList<>(other.entityEffectList);
        this.penetration = other.penetration;
        this.damageAbsorbedPercent = other.damageAbsorbedPercent;
        this.participateFight = null;
        this.presentTurn = null;
        if (other.getController() instanceof PlayerController) {
            this.controller = new PlayerController(other.controller.getSkills(), this);
        } else if (other.getController() instanceof ThinkingController) {
            this.controller = new ThinkingController(other.controller.getSkills(), this);
        } else {
            this.controller = new UniversalController(other.controller, this);
        }
        if (!other.getManas().isEmpty()) {
            for (Mana mana : other.getManas()) {
                this.getManas().add(new Mana(mana));
            }
        }
        this.setInventory(other.getInventory().copy());
        if (other.getTags().isEmpty()) {
            Map<TagType, Tag> newMap = new EnumMap<>(TagType.class);
            for (Map.Entry<TagType, Tag> entry : other.getTags().entrySet()) {
                newMap.put(entry.getKey(), entry.getValue().copy());
            }
            this.setTags(newMap);
        }
    }


    public LivingThing(String name, String id, double fireResistance, double waterResistance, double metalResistance, double woodResistance, double dirtResistance, long speed, long l, String type, double hp, double atk, double defence, ElementSort yu) {
        super(name, id, l);
        this.elementSort = yu;
        this.setHpGrowNumber(hp);
        this.setAtkGrowNumber(atk);
        this.setDfkGrowNumber(defence);
        this.fireResistance = fireResistance;
        this.waterResistance = waterResistance;
        this.metalResistance = metalResistance;
        this.woodResistance = woodResistance;
        this.dirtResistance = dirtResistance;
        this.setType(type);
        Alive = true;
        this.speed = speed;
        this.defenseLoss = 0;
        this.enhance = 0;
        this.hp = (long) ((l - 1) * getHpGrowNumber() + 200);
        this.defence = (long) ((l - 1) * getDfkGrowNumber() + 200);
        this.attack = (long) (110 + getAtkGrowNumber() * (l - 1));
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
        super(name, id, l);
        this.elementSort = u;
    }


    public LivingThing(long speed) {
        this.speed = speed;

    }

    public double getMetalPenetration() {
        return metalPenetration;
    }

    public void setMetalPenetration(double metalPenetration) {
        this.metalPenetration = metalPenetration;
    }

    public Mana getMana(ElementSort elementSortNeeded) {
        for (Mana mana : this.manas) {
            if (mana.getElementSort().equals(elementSortNeeded)) return mana;
        }
        return null;
    }

    public void initialMana() {
        this.manas = new ArrayList<>();
        switch (this.getElementSort()) {
            case METAL -> {
                manas.add(new Mana(this.metalManaGrowNumber * (this.getLevel() - 1) + 200, ElementSort.METAL));
                manas.add(new Mana(this.woodManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.WOOD));
                manas.add(new Mana(this.waterManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.WATER));
                manas.add(new Mana(this.fireManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.FIRE));
                manas.add(new Mana(this.dirtManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.DIRT));
            }
            case WOOD -> {
                manas.add(new Mana(this.metalManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.METAL));
                manas.add(new Mana(this.woodManaGrowNumber * (this.getLevel() - 1) + 200, ElementSort.WOOD));
                manas.add(new Mana(this.waterManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.WATER));
                manas.add(new Mana(this.fireManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.FIRE));
                manas.add(new Mana(this.dirtManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.DIRT));
            }
            case WATER -> {
                manas.add(new Mana(this.metalManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.METAL));
                manas.add(new Mana(this.woodManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.WOOD));
                manas.add(new Mana(this.waterManaGrowNumber * (this.getLevel() - 1) + 200, ElementSort.WATER));
                manas.add(new Mana(this.fireManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.FIRE));
                manas.add(new Mana(this.dirtManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.DIRT));
            }
            case FIRE -> {
                manas.add(new Mana(this.metalManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.METAL));
                manas.add(new Mana(this.woodManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.WOOD));
                manas.add(new Mana(this.waterManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.WATER));
                manas.add(new Mana(this.fireManaGrowNumber * (this.getLevel() - 1) + 200, ElementSort.FIRE));
                manas.add(new Mana(this.dirtManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.DIRT));
            }
            case DIRT -> {
                manas.add(new Mana(this.metalManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.METAL));
                manas.add(new Mana(this.woodManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.WOOD));
                manas.add(new Mana(this.waterManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.WATER));
                manas.add(new Mana(this.fireManaGrowNumber * (this.getLevel() - 1) + 20, ElementSort.FIRE));
                manas.add(new Mana(this.dirtManaGrowNumber * (this.getLevel() - 1) + 200, ElementSort.DIRT));
            }
        }

    }

    public void recoverManaEveryTurn() {
        for (Mana mana : manas) {
            if (mana.getElementSort().equals(ElementSort.METAL)) {
                mana.setAmount(mana.getAmount() + this.getLevel() / 100.00 * this.getMetalManaGrowNumber() + 100);
            }
            if (this.getElementSort().equals(ElementSort.METAL)) {
                mana.setAmount(mana.getAmount() + this.getLevel());
            }
            if (mana.getElementSort().equals(ElementSort.WOOD)) {
                mana.setAmount(mana.getAmount() + this.getLevel() / 100.00 * this.getWoodManaGrowNumber() + 100);
            }
            if (this.getElementSort().equals(ElementSort.WOOD)) {
                mana.setAmount(mana.getAmount() + this.getLevel());
            }
            if (mana.getElementSort().equals(ElementSort.WATER)) {
                mana.setAmount(mana.getAmount() + this.getLevel() / 100.00 * this.getWaterManaGrowNumber() + 100);
            }
            if (this.getElementSort().equals(ElementSort.WATER)) {
                mana.setAmount(mana.getAmount() + this.getLevel());
            }
            if (mana.getElementSort().equals(ElementSort.FIRE)) {
                mana.setAmount(mana.getAmount() + this.getLevel() / 100.00 * this.getFireManaGrowNumber() + 100);
            }
            if (this.getElementSort().equals(ElementSort.FIRE)) {
                mana.setAmount(mana.getAmount() + this.getLevel());
            }
            if (mana.getElementSort().equals(ElementSort.DIRT)) {
                mana.setAmount(mana.getAmount() + this.getLevel() / 100.00 * this.getDirtManaGrowNumber() + 100);
            }
            if (this.getElementSort().equals(ElementSort.DIRT)) {
                mana.setAmount(mana.getAmount() + this.getLevel());
            }
        }


    }

    public List<Mana> getManas() {
        return manas;
    }

    public void setManas(List<Mana> manas) {
        this.manas = manas;
    }

    public double getHpGrowNumber() {
        return hpGrowNumber;
    }

    public void setHpGrowNumber(double hpGrowNumber) {
        this.hpGrowNumber = hpGrowNumber;
    }

    public double getAtkGrowNumber() {
        return atkGrowNumber;
    }

    public void setAtkGrowNumber(double atkGrowNumber) {
        this.atkGrowNumber = atkGrowNumber;
    }

    public double getDfkGrowNumber() {
        return dfkGrowNumber;
    }

    public void setDfkGrowNumber(double dfkGrowNumber) {
        this.dfkGrowNumber = dfkGrowNumber;
    }

    public ElementSort getElementSort() {
        return elementSort;
    }

    public void setElementSort(ElementSort elementSort) {
        this.elementSort = elementSort;
    }

    public double getMetalManaGrowNumber() {
        return metalManaGrowNumber;
    }

    public void setMetalManaGrowNumber(double metalManaGrowNumber) {
        this.metalManaGrowNumber = metalManaGrowNumber;
    }

    public double getWoodManaGrowNumber() {
        return woodManaGrowNumber;
    }

    public void setWoodManaGrowNumber(double woodManaGrowNumber) {
        this.woodManaGrowNumber = woodManaGrowNumber;
    }

    public double getWaterManaGrowNumber() {
        return waterManaGrowNumber;
    }

    public void setWaterManaGrowNumber(double waterManaGrowNumber) {
        this.waterManaGrowNumber = waterManaGrowNumber;
    }

    public double getFireManaGrowNumber() {
        return fireManaGrowNumber;
    }

    public void setFireManaGrowNumber(double fireManaGrowNumber) {
        this.fireManaGrowNumber = fireManaGrowNumber;
    }

    public double getDirtManaGrowNumber() {
        return dirtManaGrowNumber;
    }

    public void setDirtManaGrowNumber(double dirtManaGrowNumber) {
        this.dirtManaGrowNumber = dirtManaGrowNumber;
    }

    public double getDirtPenetration() {
        return dirtPenetration;
    }

    public void setDirtPenetration(double dirtPenetration) {
        this.dirtPenetration = dirtPenetration;
    }

    public TurnEntry getPresentTurn() {
        return presentTurn;
    }

    public void setPresentTurn(TurnEntry presentTurn) {
        this.presentTurn = presentTurn;
    }

    public double getFirePenetration() {
        return firePenetration;
    }

    public void setFirePenetration(double firePenetration) {
        this.firePenetration = firePenetration;
    }

    public double getWaterPenetration() {
        return waterPenetration;
    }

    public void setWaterPenetration(double waterPenetration) {
        this.waterPenetration = waterPenetration;
    }

    public double getWoodPenetration() {
        return woodPenetration;
    }

    public void setWoodPenetration(double woodPenetration) {
        this.woodPenetration = woodPenetration;
    }

    public Mana getMetalMana() {
        return this.getMana(ElementSort.METAL);
    }

    public Mana getWoodMana() {
        return this.getMana(ElementSort.WOOD);
    }

    public Mana getWaterMana() {
        return this.getMana(ElementSort.WATER);
    }

    public Mana getFireMana() {
        return this.getMana(ElementSort.FIRE);
    }

    public Mana getDirtMana() {
        return this.getMana(ElementSort.DIRT);
    }

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

    public void setDirtDamageEnhance(double dirtDamageEnhance) {
        this.dirtDamageEnhance = dirtDamageEnhance;
    }

    public long getExtraDamage() {
        return extraDamage;
    }

    public void setExtraDamage(long extraDamage) {
        this.extraDamage = extraDamage;
    }

    public void updateSelf() {
    }//每回合执行.可以写天赋等更新状态

    public double getIndividualMultipleArea() {
        return individualMultipleArea;
    }

    public void setIndividualMultipleArea(double individualMultipleArea) {
        this.individualMultipleArea = individualMultipleArea;
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
        this.penetration = chuantong;
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
        this.defence = dfk;
        return this;
    }

    public LivingThing facSetSpeed(long speed) {
        this.speed = speed;
        return this;
    }

    public LivingThing facSetAfk(long afk) {
        this.attack = afk;
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
                    effect.initialEffect(target);
                }
            }
        }
        target.entityEffectList.add(effect);
        effect.initialEffect(target);
    }

    public void addEffect(Effect effect) {
        for (int i = 0; i < entityEffectList.size(); i++) {
            Effect existing = entityEffectList.get(i);
            if (existing.equals(effect)) {
                if (existing.getLevel() >= effect.getLevel()) {
                    existing.setLastTime(existing.getLastTime() + effect.getLastTime());
                } else {

                    entityEffectList.set(i, effect);
                    effect.initialEffect(this);
                }
                return;
            }
        }
        entityEffectList.add(effect);
        effect.initialEffect(this);
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

    public double getPenetration() {
        return penetration;
    }

    public void setPenetration(double penetration) {
        this.penetration = penetration;
    }

    public double getHpMax() {
        return hpMax;
    }

    public void setHpMax(double hpMax) {
        this.hpMax = hpMax;
    }

    public boolean isAlive() {
        if (getHp() <= 0) {
            this.getController().setActionSignal(ActionSignal.NORMAL);
            this.getController().setSpecialAction(null);
            this.whenFightEnds();
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


    public long getAttack() {
        return attack;
    }

    public void setAttack(long attack) {
        this.attack = attack;
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


    public LivingThing facSetLevel(long level) {
        this.setLevel(level);
        return this;
    }


    public LivingThing facSetName(String name) {
        this.setName(name);
        return this;
    }


    public LivingThing facSetId(String id) {
        this.setId(id);
        return this;
    }


    public LivingThing facSetHpGrowNumber(double hpGrowNumber) {
        this.setHpGrowNumber(hpGrowNumber);
        return this;
    }


    public LivingThing facSetAtkGrowNumber(double atkGrowNumber) {
        this.setAtkGrowNumber(atkGrowNumber);
        return this;
    }


    public LivingThing facSetDfkGrowNumber(double dfkGrowNumber) {
        this.setDfkGrowNumber(dfkGrowNumber);
        return this;
    }


    public Entity facSetElementSort(ElementSort elementSort) {
        this.setElementSort(elementSort);
        return this;
    }


    public LivingThing facSetMetalManaGrowNumber(double metalManaGrowNumber) {
        this.setMetalManaGrowNumber(metalManaGrowNumber);
        return this;
    }


    public LivingThing facSetWoodManaGrowNumber(double woodManaGrowNumber) {
        this.setWoodManaGrowNumber(woodManaGrowNumber);
        return this;
    }


    public LivingThing facSetWaterManaGrowNumber(double waterManaGrowNumber) {
        this.setWaterManaGrowNumber(waterManaGrowNumber);
        return this;
    }


    public LivingThing facSetFireManaGrowNumber(double fireManaGrowNumber) {
        this.setFireManaGrowNumber(fireManaGrowNumber);
        return this;
    }


    public LivingThing facSetDirtManaGrowNumber(double dirtManaGrowNumber) {
        this.setDirtManaGrowNumber(dirtManaGrowNumber);
        return this;
    }


    public LivingThing facSetType(String type) {
        this.setType(type);
        return this;
    }
    public void showSpecialStatus(){}
    public void whenFightStart(Fight fight){}

    public LivingThing facSetManas(List<Mana> manas) {
        this.setManas(manas);
        return this;
    }

    public void whenFightEnds() {
        setPresentTurn(null);
        setHp((long) getHpMax());
        for (Effect effect : getEntityEffectList()) effect.whenLastTimeEnd(this);
        this.setEntityEffectList(new ArrayList<>());
        for (Skill skill : getController().getSkills()) skill.setNowCoolDown(0);
        for (Mana mana : getManas()) mana.setAmount(mana.getAmountMax());
    }

    /**
     *
     * 子类可重写此方法，在血量被扣减前进行修正（如锁血、免死等）。
     *
     * @param newHp 计算出的新血量（当前血量 - 伤害值）
     * @param da    伤害事件
     * @return 修正后的新血量
     */
    public long applyDamageModifiers(long newHp, DamageEvent da) {
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

    public long getDefence() {
        return defence;
    }

    public void setDefence(long defence) {
        this.defence = defence;
    }

    public long getHp() {
        return hp;
    }

    public void setHp(long hp) {
        if (hp < getHp()) {
            EventBus.post(new HpLossEvent(getHp() - hp, this));
        }
        if (hp > getHp()) {
            EventBus.post(new HpRestorationEvent(hp - getHp(), this));
        }
        this.hp = (long) Math.min(this.getHpMax(), hp);
        if (this.hp < 0) {
            this.hp = 0;
        }
    }



    public double getCriticalDMG() {
        return criticalDMG;
    }

    public void setCriticalDMG(double criticalDMG) {
        this.criticalDMG = criticalDMG;
    }



}
