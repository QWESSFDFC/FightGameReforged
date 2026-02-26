package cn.gfhnv.game.entity;

import cn.gfhnv.game.Thing;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.mana.Mana;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Entity extends Thing {
    private int UUID = 1;
    private long level;
    private String name;
    private String id;
    private double hpGrowNumber;
    private double atkGrowNumber;
    private double dfkGrowNumber;
    private ElementSort elementSort;
    private String type = "entity";
   private double manaMax;
  private List<Mana> manas=new ArrayList<>();//一个实体可以拥有多个Mana
    public Entity(Entity entity){
        this.level=entity.level;
        this.name=entity.name;
        this.id=entity.id;
        this.hpGrowNumber=entity.hpGrowNumber;
        this.atkGrowNumber=entity.atkGrowNumber;
        this.dfkGrowNumber=entity.dfkGrowNumber;
        this.elementSort=entity.elementSort;
        this.type=entity.type;
        this.manaMax=entity.manaMax;
        if (entity.getManas().size()>0){
            manas.addAll(entity.getManas());
        }
    }

    public List<Mana> getManas() {
        return manas;
    }

    public void setManas(List<Mana> manas) {
        this.manas = manas;
    }

    public double getManaMax() {
        return manaMax;
    }

    public void setManaMax(double manaMax) {
        this.manaMax = manaMax;
    }

    public Entity() {
        super(new BigDecimal(1));
    }

    public Entity(String name, String id, long l, ElementSort h) {
        this.name = name;
        this.id = id;
        this.level = l;
        this.elementSort = h;
    }

    public Entity(String name, String id, long l, ElementSort h, BigDecimal bigDecimal) {
        super(bigDecimal);
        this.name = name;
        this.id = id;
        this.level = l;
        this.elementSort = h;
    }


    public LivingThing transToLivingTing() {
        if (this instanceof LivingThing) {
            return (LivingThing) this;
        }
        System.out.println("ERROR!TRANS FAILED! RETURN NULL.Entity.transToLivingTing!");
        return null;

    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Entity entity = (Entity) o;
        return getLevel() == entity.getLevel() && Double.compare(getHpGrowNumber(), entity.getHpGrowNumber()) == 0 && Double.compare(getAtkGrowNumber(), entity.getAtkGrowNumber()) == 0 && Double.compare(getDfkGrowNumber(), entity.getDfkGrowNumber()) == 0 && getUUID() == entity.getUUID() && Objects.equals(getName(), entity.getName()) && Objects.equals(getId(), entity.getId()) && getElementSort() == entity.getElementSort() && Objects.equals(getInventory(), entity.getInventory()) && Objects.equals(getType(), entity.getType());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getLevel(), getName(), getId(), getHpGrowNumber(), getAtkGrowNumber(), getDfkGrowNumber(), getElementSort(), getUUID(), getInventory(), getType());
    }

    public void showState() {
        System.out.println(this.getName());
        System.out.println(this.getId());
        System.out.println(this.getElementSort());
        System.out.println(this.getVelocity() + "Velocity");
        System.out.println(this.getMass() + "Mass");
        System.out.println(this.getAcceleration() + "Acceleration");
    }

    @Override
    public String toString() {
        return "Entity{" +
                "level=" + level +
                ", name='" + name + '\'' +
                ", id='" + id + '\'' +
                ", hpGrowNumber=" + hpGrowNumber +
                ", atkGrowNumber=" + atkGrowNumber +
                ", dfkGrowNumber=" + dfkGrowNumber +
                ", yuanshu='" + elementSort + '\'' +
                ", UUID=" + UUID +
                ", type='" + type + '\'' +
                '}';
    }

    public int getUUID() {
        return UUID;
    }


    public ElementSort getElementSort() {
        return elementSort;
    }

    public void setElementSort(ElementSort elementSort) {
        this.elementSort = elementSort;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getDfkGrowNumber() {
        return dfkGrowNumber;
    }

    public void setDfkGrowNumber(double dfkGrowNumber) {
        this.dfkGrowNumber = dfkGrowNumber;
    }

    public double getAtkGrowNumber() {
        return atkGrowNumber;
    }

    public void setAtkGrowNumber(double atkGrowNumber) {
        this.atkGrowNumber = atkGrowNumber;
    }

    public double getHpGrowNumber() {
        return hpGrowNumber;
    }

    public void setHpGrowNumber(double hpGrowNumber) {
        this.hpGrowNumber = hpGrowNumber;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public long getLevel() {
        return level;
    }

    public void setLevel(long level) {
        this.level = level;
        if (this instanceof LivingThing) {
            this.transToLivingTing().setHp((long) ((level - 1) * getHpGrowNumber() + 200));
            this.transToLivingTing().setDfk((long) ((level - 1) * getDfkGrowNumber() + 200));
            this.transToLivingTing().setAfk((long) (110 + getAtkGrowNumber() * (level - 1)));
            ((LivingThing) this).setHpMax(this.transToLivingTing().getHp());
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
