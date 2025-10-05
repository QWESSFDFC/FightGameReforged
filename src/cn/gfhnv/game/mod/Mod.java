package cn.gfhnv.game.mod;
import cn.gfhnv.game.entity.Entity;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.world.World;
import java.util.ArrayList;
import java.util.List;
public abstract class Mod {
    private final String MOD_ID;
    private static List<Entity> entityList=new ArrayList<>();
    private static List<Item> items=new ArrayList<>();
    public List<Entity> getEntityList() {
        return entityList;
    }
    public void setEntityList(List<Entity> entityList) {
        Mod.entityList = entityList;
    }
    public List<Item> getItems() {
        return items;
    }
    public void setItems(List<Item> items) {
        Mod.items = items;
    }
    public World registerItself(World wd){
            for (Item m:items){
                wd.addItem(m);
            }
          for (Entity m:entityList){
              wd.addEntity(m);
          }
          return wd;
      }
    public Mod(String MOD_ID) {
          this.MOD_ID=MOD_ID;
    }
    public String getMOD_ID() {
        return MOD_ID;
    }
}
