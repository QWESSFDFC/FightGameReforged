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
    private double metalManaGrowNumber;
    private double woodManaGrowNumber;
    private double waterManaGrowNumber;
    private double fireManaGrowNumber;
    private double dirtManaGrowNumber;

    public double getFireManaGrowNumber() {
        return fireManaGrowNumber;
    }

    public void setFireManaGrowNumber(double fireManaGrowNumber) {
        this.fireManaGrowNumber = fireManaGrowNumber;
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

    public double getDirtManaGrowNumber() {
        return dirtManaGrowNumber;
    }

    public void setDirtManaGrowNumber(double dirtManaGrowNumber) {
        this.dirtManaGrowNumber = dirtManaGrowNumber;
    }

    private String type = "entity";
  private List<Mana> manas=new ArrayList<>();//一个实体可以拥有多个Mana
    public Entity(Entity entity){
        this.level=entity.level;
        this.name=entity.name;
        this.id=entity.id;
        this.hpGrowNumber=entity.hpGrowNumber;
        this.atkGrowNumber=entity.atkGrowNumber;
        this.dfkGrowNumber=entity.dfkGrowNumber;
        this.elementSort=entity.elementSort;
        this.metalManaGrowNumber=entity.metalManaGrowNumber;
        this.woodManaGrowNumber=entity.woodManaGrowNumber;
        this.waterManaGrowNumber=entity.waterManaGrowNumber;
        this.fireManaGrowNumber=entity.fireManaGrowNumber;
        this.dirtManaGrowNumber=entity.fireManaGrowNumber;
        this.type=entity.type;
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
        switch (this.getElementSort()){
            case METAL ->{
                this.setMetalManaGrowNumber(20);
                this.setWoodManaGrowNumber(4);
                this.setWaterManaGrowNumber(10);
                this.setFireManaGrowNumber(1);
                this.setDirtManaGrowNumber(10);
            }
            case WOOD ->{
                this.setMetalManaGrowNumber(1);
                this.setWoodManaGrowNumber(20);
                this.setWaterManaGrowNumber(10);
                this.setFireManaGrowNumber(10);
                this.setDirtManaGrowNumber(4);
            }
            case WATER ->{
                this.setMetalManaGrowNumber(10);
                this.setWoodManaGrowNumber(10);
                this.setWaterManaGrowNumber(20);
                this.setFireManaGrowNumber(4);
                this.setDirtManaGrowNumber(1);
            }
            case FIRE ->{
                this.setMetalManaGrowNumber(4);
                this.setWoodManaGrowNumber(10);
                this.setWaterManaGrowNumber(1);
                this.setFireManaGrowNumber(20);
                this.setDirtManaGrowNumber(10);
            }
            case DIRT ->{
                this.setMetalManaGrowNumber(10);
                this.setWoodManaGrowNumber(1);
                this.setWaterManaGrowNumber(4);
                this.setFireManaGrowNumber(10);
                this.setDirtManaGrowNumber(20);
            }
        }
        this.initialMana();
    }


    public LivingThing transToLivingTing() {
        if (this instanceof LivingThing) {
            return (LivingThing) this;
        }
        System.out.println("ERROR!TRANS FAILED! RETURN NULL.Entity.transToLivingTing!");
        return null;

    }
     public void initialMana(){
        this.manas=new ArrayList<>();
        switch (this.getElementSort()){
            case METAL ->{manas.add(new Mana(this.metalManaGrowNumber*(level-1)+200,ElementSort.METAL));
                          manas.add(new Mana(this.woodManaGrowNumber*(level-1)+20,ElementSort.WOOD));
                          manas.add(new Mana(this.waterManaGrowNumber*(level-1)+20,ElementSort.WATER));
                          manas.add(new Mana(this.fireManaGrowNumber*(level-1)+20,ElementSort.FIRE));
                          manas.add(new Mana(this.dirtManaGrowNumber*(level-1)+20,ElementSort.DIRT));
            }
            case WOOD -> {manas.add(new Mana(this.metalManaGrowNumber*(level-1)+20,ElementSort.METAL));
                manas.add(new Mana(this.woodManaGrowNumber*(level-1)+200,ElementSort.WOOD));
                manas.add(new Mana(this.waterManaGrowNumber*(level-1)+20,ElementSort.WATER));
                manas.add(new Mana(this.fireManaGrowNumber*(level-1)+20,ElementSort.FIRE));
                manas.add(new Mana(this.dirtManaGrowNumber*(level-1)+20,ElementSort.DIRT));
            }
            case WATER -> {manas.add(new Mana(this.metalManaGrowNumber*(level-1)+20,ElementSort.METAL));
                manas.add(new Mana(this.woodManaGrowNumber*(level-1)+20,ElementSort.WOOD));
                manas.add(new Mana(this.waterManaGrowNumber*(level-1)+200,ElementSort.WATER));
                manas.add(new Mana(this.fireManaGrowNumber*(level-1)+20,ElementSort.FIRE));
                manas.add(new Mana(this.dirtManaGrowNumber*(level-1)+20,ElementSort.DIRT));
            }
            case FIRE -> {manas.add(new Mana(this.metalManaGrowNumber*(level-1)+20,ElementSort.METAL));
                manas.add(new Mana(this.woodManaGrowNumber*(level-1)+20,ElementSort.WOOD));
                manas.add(new Mana(this.waterManaGrowNumber*(level-1)+20,ElementSort.WATER));
                manas.add(new Mana(this.fireManaGrowNumber*(level-1)+200,ElementSort.FIRE));
                manas.add(new Mana(this.dirtManaGrowNumber*(level-1)+20,ElementSort.DIRT));
            }
            case DIRT -> {manas.add(new Mana(this.metalManaGrowNumber*(level-1)+20,ElementSort.METAL));
                manas.add(new Mana(this.woodManaGrowNumber*(level-1)+20,ElementSort.WOOD));
                manas.add(new Mana(this.waterManaGrowNumber*(level-1)+20,ElementSort.WATER));
                manas.add(new Mana(this.fireManaGrowNumber*(level-1)+20,ElementSort.FIRE));
                manas.add(new Mana(this.dirtManaGrowNumber*(level-1)+200,ElementSort.DIRT));
            }
        }

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
        this.initialMana();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
