package cn.gfhnv.game.mod;
import cn.gfhnv.game.entity.Entity;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.world.World;
import java.util.ArrayList;
import java.util.List;
public abstract class Mod {
    private final String MOD_ID;
    private List<Entity> entityList=new ArrayList<>();
    private List<Item> items=new ArrayList<>();
    public List<Entity> getEntityList() {
        return entityList;
    }
    public void setEntityList(List<Entity> entityList) {
        this.entityList = entityList;
    }
    public List<Item> getItems() {
        return items;
    }
    public void addItem(Item item) {items.add(item);}
    public void removeItem(Item item) {items.remove(item);}
    public void addEntity(Entity entity) {entityList.add(entity);}
    public void removeEntity(Entity entity) {entityList.remove(entity);}
    public void setItems(List<Item> items) {
        this.items = items;
    }
    public void registerItself(){
        for (Item m:items){
            if (!World.getItemList().contains(m)) {
                World.addItem(m);
            }
        }
        for (Entity m:entityList){
            if (!World.getEntityList().contains(m)) {
                World.addEntity(m);
            }
        }
    }
    public Mod(String MOD_ID) {
          this.MOD_ID=MOD_ID;
    }
    public String getMOD_ID() {
        return MOD_ID;
    }
}
