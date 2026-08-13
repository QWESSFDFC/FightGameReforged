package cn.gfhnv.game.world;

import cn.gfhnv.game.Thing;
import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.Entity;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.mod.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 游戏世界的全局注册表与运行时容器。
 * <p>
 * 说明：
 * <ul>
 *     <li><b>things</b>：游戏运行时的对象列表（{@link Thing} 及其子类实例，如加入战斗的角色、敌人、物品等）；</li>
 *     <li><b>itemList / entityList / effectList</b>：物品、实体、效果的<b>注册表</b>（游戏内可用内容的静态登记），
 *     由模组加载（{@link cn.gfhnv.game.mod.Mod#registerItself()}）或官方内容填充；</li>
 *     <li><b>modList</b>：已加载的模组列表（由 {@link cn.gfhnv.game.mod.ModLoader} 填充）；</li>
 *     <li><b>turnTimer</b>：全局回合计数器。</li>
 * </ul>
 * 注册表存放<b>模板/可用内容</b>（如可选择的怪物、物品）；things 存放<b>运行时实例</b>。
 * 例如玩家在开局选择生物后，通过 {@link #addThing(Thing)} 将 {@code copy()} 出的实例放入 things。
 *
 * @author gfhnv
 */
public class World {
    /**
     * 全局回合计数器（每经过一个回合自动 +1）。
     */
    public static int turnTimer = 0;

    /**
     * 已加载的模组列表。
     */
    private static List<Mod> modList = new ArrayList<>();

    /**
     * 效果注册表（所有可用效果）。
     */
    private static List<Effect> effectList = new ArrayList<>();

    /**
     * 游戏运行时对象列表。
     */
    private static List<Thing> things = new ArrayList<>();

    /**
     * 实体注册表（所有可选/可用实体）。
     */
    private static List<Entity> entityList = new ArrayList<>();

    /**
     * 物品注册表（所有可选/可用物品）。
     */
    private static List<Item> itemList = new ArrayList<>();

    /**
     * 向效果注册表注册一个效果。
     *
     * @param effect 要注册的效果
     */
    public static void addEffect(Effect effect) {
        effectList.add(effect);
    }

    /**
     * 从效果注册表移除一个效果（不存在则忽略）。
     *
     * @param effect 要移除的效果
     */
    public static void removeEffect(Effect effect) {
        if (!effectList.contains(effect)) {
            return;
        }
        effectList.remove(effect);
    }

    /**
     * @return 效果注册表
     */
    public static List<Effect> getEffectList() {
        return effectList;
    }

    /**
     * 设置效果注册表。
     *
     * @param effectList 效果注册表
     */
    public static void setEffectList(List<Effect> effectList) {
        World.effectList = effectList;
    }

    /**
     * 注册一个已加载的模组。
     *
     * @param m 模组
     */
    public static void addMod(Mod m) {
        modList.add(m);
    }

    /**
     * 移除一个已加载的模组（不存在则忽略）。
     *
     * @param m 模组
     */
    public static void removeMod(Mod m) {
        if (modList.contains(m)) modList.remove(m);
    }

    /**
     * @return 已加载的模组列表
     */
    public static List<Mod> getModList() {
        return modList;
    }

    /**
     * @return 游戏运行时对象列表
     */
    public static List<Thing> getThings() {
        return things;
    }

    /**
     * 向游戏运行时对象列表添加一个对象（如选中的角色、敌人副本、物品实例等）。
     *
     * @param thing 运行时对象
     */
    public static void addThing(Thing thing) {
        things.add(thing);
    }

    /**
     * 从游戏运行时对象列表移除一个对象（不存在则忽略）。
     *
     * @param thing 运行时对象
     */
    public static void removeThing(Thing thing) {
        if (things.contains(thing)) things.remove(thing);
    }

    /**
     * 向实体注册表注册一个实体。
     *
     * @param e 实体
     */
    public static void addEntity(Entity e) {
        entityList.add(e);
    }

    /**
     * 向物品注册表注册一个物品。
     *
     * @param m 物品
     */
    public static void addItem(Item m) {
        itemList.add(m);
    }

    /**
     * 从物品注册表移除一个物品（不存在则忽略）。
     *
     * @param m 物品
     */
    public static void removeItem(Item m) {
        if (itemList.contains(m)) itemList.remove(m);
    }

    /**
     * @return 物品注册表
     */
    public static List<Item> getItemList() {
        return itemList;
    }

    /**
     * 从实体注册表移除一个实体（不存在则忽略）。
     *
     * @param e 实体
     */
    public static void removeEntity(Entity e) {
        if (entityList.contains(e)) entityList.remove(e);
    }

    /**
     * @return 实体注册表
     */
    public static List<Entity> getEntityList() {
        return entityList;
    }

    /**
     * 设置实体注册表。
     *
     * @param entgityList 实体注册表
     */
    public static void setEntityList(List<Entity> entgityList) {
        entityList = entgityList;
    }

    /**
     * 按 UUID 从运行时对象列表中查找对象。
     *
     * @param uuid 对象的 UUID
     * @return 匹配的对象；若 uuid 为空、列表为空或未找到则返回 {@code null}
     */
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

    /**
     * 从实体注册表中筛选出所有 {@link LivingThing}。
     *
     * @return 生物实体列表
     */
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