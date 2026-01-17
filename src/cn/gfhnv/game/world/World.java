package cn.gfhnv.game.world;
import cn.gfhnv.game.Thing;
import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.Entity;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.WorldTurnEvent;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.mod.Mod;
import cn.gfhnv.game.mod.officialModStuff.customEntity.PlayerOne;

import java.util.ArrayList;
import java.util.List;
//Things为游戏内运行的东西.itemList,entityList为注册表.
public class World {
    private static List<Mod> modList=new ArrayList<>();
    public static int turnTimer=0;
    private static List<Effect> effectList=new ArrayList<>();
    private static List<Thing> things=new ArrayList<>();
    public static void addEffect(Effect effect){
    effectList.add(effect);
}
   public    static void removeEffect(Effect effect){
    if(!effectList.contains(effect)){return;}
    effectList.remove(effect);}
    public static List<Effect> getEffectList() {
        return effectList;
    }

    public static void setEffectList(List<Effect> effectList) {
        World.effectList = effectList;
    }

    public static void addMod(Mod m){modList.add(m);}
    public static void removeMod(Mod m){
    if (modList.contains(m))modList.remove(m);}
    public static List<Mod> getModList() {return modList;}
    private static List<Entity> entityList=new ArrayList<>();
    private static List<Item> itemList=new ArrayList<>();
    public static List<Thing> getThings(){
        return things;
    }
    public static void addThing(Thing thing){
        things.add(thing);
    }
    public static void removeThing(Thing thing){
        if (things.contains(thing))things.remove(thing);
    }
    public static void addEntity(Entity e){
    entityList.add(e);
 }
 public static void addItem(Item m){itemList.add(m);}
    public static void removeItem( Item m){
        if (itemList.contains(m))itemList.remove(m);
        }
    public static List<Item> getItemList() {return itemList;}
    public static void setEntityList(List<Entity> entgityList){entityList=entgityList;}
    public static void removeEntity(Entity e){
        if (entityList.contains(e))entityList.remove(e);
 }
    public static List<Entity> getEntityList() {
        return entityList;
    }
}
