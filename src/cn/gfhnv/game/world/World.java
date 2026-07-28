package cn.gfhnv.game.world;

import cn.gfhnv.game.Thing;
import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.Entity;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.mod.Mod;

import java.util.ArrayList;
import java.util.List;

//Things为游戏内运行的东西.itemList,entityList为注册表.
public class World {
    public static int turnTimer = 0;
    private static List<Mod> modList = new ArrayList<>();
    private static List<Effect> effectList = new ArrayList<>();//效果注册表
    private static List<Thing> things = new ArrayList<>();
    private static List<Entity> entityList = new ArrayList<>();//实体注册表
    private static List<Item> itemList = new ArrayList<>();//物品注册表

    public static void addEffect(Effect effect) {
        effectList.add(effect);
    }

    public static void removeEffect(Effect effect) {
        if (!effectList.contains(effect)) {
            return;
        }
        effectList.remove(effect);
    }

    public static List<Effect> getEffectList() {
        return effectList;
    }

    public static void setEffectList(List<Effect> effectList) {
        World.effectList = effectList;
    }

    public static void addMod(Mod m) {
        modList.add(m);
    }

    public static void removeMod(Mod m) {
        if (modList.contains(m)) modList.remove(m);
    }

    public static List<Mod> getModList() {
        return modList;
    }

    public static List<Thing> getThings() {
        return things;
    }

    public static void addThing(Thing thing) {
        things.add(thing);
    }

    public static void removeThing(Thing thing) {
        if (things.contains(thing)) things.remove(thing);
    }

    public static void addEntity(Entity e) {
        entityList.add(e);
    }

    public static void addItem(Item m) {
        itemList.add(m);
    }

    public static void removeItem(Item m) {
        if (itemList.contains(m)) itemList.remove(m);
    }

    public static List<Item> getItemList() {
        return itemList;
    }

    public static void removeEntity(Entity e) {
        if (entityList.contains(e)) entityList.remove(e);
    }

    public static List<Entity> getEntityList() {
        return entityList;
    }

    public static void setEntityList(List<Entity> entgityList) {
        entityList = entgityList;
    }

    public static Thing getAimedThing(String uuid) {
        if (uuid.equals("")) {
            return null;
        }
        if (things.isEmpty()) {
            return null;
        }
        for (Thing thing : things) {
            if (thing.getUUID().equals(uuid)) {
                return thing;
            }
        }
        return null;
    }

    public static List<LivingThing> getLivingEntityList() {
        List<LivingThing> livingThingList = new ArrayList<>();
        for (Entity e : entityList) {
            if (e instanceof LivingThing) {
                livingThingList.add((LivingThing) e);
            }
        }
        return livingThingList;
    }
}
