package cn.gfhnv.game.entity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import cn.gfhnv.game.inventory.Inventory;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.system.ElementSort;
public class Entity {
    public LivingThing transToLivingTing(){
        if (this instanceof LivingThing){
            return (LivingThing) this;
        }
        System.out.println("ERROR!TRANS FAILED! RETURN NULL.Entity.transToLivingTing!");
        return null;
    }
    private long level;
    private String name;
    private double x,y,z;
    private String id;
    private double hpGrowNumber;
    private double atkGrowNumber;
    private double dfkGrowNumber;
    private ElementSort yuanshu;
    private final int UUID=1;
    @Override
    public String toString() {
        return "Entity{" +
                "level=" + level +
                ", name='" + name + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", z=" + z +
                ", id='" + id + '\'' +
                ", hpGrowNumber=" + hpGrowNumber +
                ", atkGrowNumber=" + atkGrowNumber +
                ", dfkGrowNumber=" + dfkGrowNumber +
                ", yuanshu='" + yuanshu + '\'' +
                ", UUID=" + UUID +
                ", inventory=" + inventory +
                ", type='" + type + '\'' +
                '}';
    }
    public int getUUID() {
        return UUID;
    }
    public Map<Item, Integer> getInventory() {
        return inventory;
    }
    public ElementSort getYuanshu() {
        return yuanshu;
    }
    public void setYuanshu(ElementSort yuanshu) {
        this.yuanshu = yuanshu;
    }
    private Map<Item, Integer> inventory= new HashMap<>();
    private String type="entity";
    public void giveThingToEntity(Entity e,Item i,Integer ss){
        if (e.inventory.getOrDefault(i,null)!=null){
            e.inventory.replace(i, inventory.get(i),inventory.get(i)+ ss);
            return;
        }
        e.inventory.put(i,ss);
    }
    public String getName() {
        return name;
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
    public void setName(String name) {
        this.name = name;
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
    }
    public double getX() {
        return x;
    }
    public void setX(double x) {
        this.x = x;
    }
    public double getY() {
        return y;
    }
    public void setY(double y) {
        this.y = y;
    }
    public double getZ() {
        return z;
    }
    public void setZ(double z) {
        this.z = z;
    }
    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public Entity(){}
    public Entity(String name, double x, double y, double z, String id,long l,ElementSort h) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.id = id;
        this.level=l;
        this.yuanshu=h;
    }
}
