package cn.gfhnv.game.mod;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.Entity;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class Mod {
    private final String MOD_ID;
    private List<Class<?>> modClasses = new ArrayList<>();//模组加载时模组的其他类自动添加到这个里面,防止被gc回收
    private ModInformation modInformation;
    private List<Entity> entityList = new ArrayList<>();//模组各个内容先在invokeWhenLoaded方法中添加到模组的各个List中.不要弄错了List类型
    private List<Item> items = new ArrayList<>();
    private List<Effect> effects = new ArrayList<>();

    public Mod(String modID) {
        this.MOD_ID = modID;
    }

    public Mod(String MOD_ID, ModInformation modInformation) {
        this.MOD_ID = MOD_ID;
        this.modInformation = modInformation;
    }//请模组加载时把模组内容在invokeWhenLoaded方法中添加到模组的各个List中.不要学officialStuff

    public void addModClass(Class<?> clazz) {
        modClasses.add(clazz);
    }

    public Class<?> getClassByName(String name) {
        for (Class<?> clazz : modClasses) {
            if (clazz.getName().equals(name)) {
                return clazz;
            }
        }
        System.out.println(modInformation.getName() + "没有" + name);
        return null;
    }

    public ModInformation getModInformation() {
        return modInformation;
    }

    public void setModInformation(ModInformation modInformation) {
        this.modInformation = modInformation;
    }

    public List<Entity> getEntityList() {
        return entityList;
    }

    public void setEntityList(List<Entity> entityList) {
        this.entityList = entityList;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public void addItem(Item item) {
        items.add(item);
    }

    public void removeItem(Item item) {
        items.remove(item);
    }

    public void addEntity(Entity entity) {
        entityList.add(entity);
    }

    public void removeEntity(Entity entity) {
        entityList.remove(entity);
    }

    public void addEffect(Effect effect) {
        effects.add(effect);
    }

    public void removeEffect(Effect effect) {
        if (!effects.contains(effect)) {
            return;
        }
        effects.remove(effect);
    }

    public void invokeWhenLoaded() {
    }//请模组加载时把模组内容在这个方法中添加到模组的各个List中.不要学officialStuff

    public void registerItself() {
        if (!items.isEmpty()) {
            for (Item m : items) {
                if (!World.getItemList().contains(m)) {
                    World.addItem(m);
                }
            }
        }
        if (!entityList.isEmpty()) {
            for (Entity m : entityList) {
                if (!World.getEntityList().contains(m)) {
                    World.addEntity(m);
                }
            }
        }
        if (!effects.isEmpty()) {
            for (Effect m : effects) {
                if (!World.getEffectList().contains(m)) {
                    World.addEffect(m);
                }
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Mod mod = (Mod) o;
        return Objects.equals(getMOD_ID(), mod.getMOD_ID()) && Objects.equals(getEntityList(), mod.getEntityList()) && Objects.equals(getItems(), mod.getItems());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getMOD_ID(), getEntityList(), getItems());
    }

    public String getMOD_ID() {
        return MOD_ID;
    }

    public List<Class<?>> getModClasses() {
        return modClasses;
    }
}
