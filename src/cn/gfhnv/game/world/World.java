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
     * <p>
     * 入表前会把 id 补成注册表里的完整 id（见 {@link #applyRegisteredId(Thing)}），
     * 这样「选出来的角色/物品」与注册表模板的 id 始终一致。
     *
     * @param thing 运行时对象
     */
    public static void addThing(Thing thing) {
        applyRegisteredId(thing);
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

    /* ------------------------------------------------------------------
     * id 归一：把运行时实例的「短 id」补成注册表里的完整 id
     * ------------------------------------------------------------------ */

    /**
     * 取 id 里 {@code 前缀:} 之后的部分（用于显示，例如
     * {@code game_official_content:aNiceSword} → {@code aNiceSword}）。
     * <p>
     * 注册表与运行时数据里存的都是完整 id，但玩家在命令里通常只写短名，
     * 所以回显与报错都打印短名；判断相等时两种写法都要认（见
     * {@code EffectCommand.matches} / {@code GiveCommand.matches}）。
     *
     * @param id 完整 id；可为 {@code null}
     * @return 短名；{@code null} 返回空串，没有冒号则原样返回
     */
    public static String shortIdOf(String id) {
        if (id == null) {
            return "";
        }
        int colon = id.indexOf(':');
        return colon >= 0 && colon + 1 < id.length() ? id.substring(colon + 1) : id;
    }

    /**
     * 取运行时实体的短名。
     *
     * @param thing 实体或物品；可为 {@code null}
     * @return 短名（{@code null} 返回 {@code "?"}）
     */
    public static String shortIdOf(Thing thing) {
        return thing == null ? "?" : shortIdOf(thing.getId());
    }

    /**
     * 取运行时效果的短名。
     *
     * @param effect 效果；可为 {@code null}
     * @return 短名（{@code null} 返回 {@code "?"}）
     */
    public static String shortIdOf(Effect effect) {
        return effect == null ? "?" : shortIdOf(effect.getID());
    }

    /**
     * 在注册表里找出"运行时对象的 id 应该补成哪一个完整 id"。
     * <p>
     * <b>先按短名精确匹配，匹配不到才退回"同类第一条模板"</b>。
     * 只按类取第一条是不够的：同一个类可以注册多种形态，例如【残破容器】与【完整容器】
     * 都是 {@link cn.gfhnv.game.officialStuff.customEntity.summons.BrokenContainer}，
     * 只按类取第一条会把完整容器的 id 改写成 {@code brokenContainer}，
     * 于是按 id 找内容的地方（{@code /give}、选择器筛选、效果合并判定）全都指向了错的形态。
     * <p>
     * 退回按类匹配是为了兼容"id 由代码临时生成、注册表里没有同名模板"的运行时对象。
     *
     * @param type    运行时对象的类型（精确比较，父类不算）
     * @param shortId 运行时对象当前的 id（调用方已确认它不含 {@code :}）
     * @param things  注册表列表
     * @param idOf    从注册表元素上取 id 的方式
     * @param <T>     注册表元素类型
     * @return 完整 id；注册表里没有同类模板时返回 {@code null}
     */
    private static <T> String registeredIdOf(Class<?> type, String shortId, List<T> things,
                                             java.util.function.Function<T, String> idOf) {
        String sameId = null;
        String sameClass = null;
        for (T candidate : things) {
            if (candidate == null || candidate.getClass() != type) {
                continue;
            }
            String candidateId = idOf.apply(candidate);
            if (candidateId == null) {
                continue;
            }
            if (sameClass == null) {
                sameClass = candidateId;
            }
            if (sameId == null && shortId != null && shortId.equals(shortIdOf(candidateId))) {
                sameId = candidateId;
            }
        }
        return sameId != null ? sameId : sameClass;
    }

    /**
     * 把一个运行时实体的 id 补成注册表里的完整 id（带 {@code MOD_ID:} 前缀）。
     * <p>
     * <b>为什么需要它</b>：加前缀这一步只发生在 {@link Mod#addEntity(Entity)} 里，
     * 而它作用的是<b>被注册的那个模板</b>；代码里直接 {@code new Xxx(...)} 出来的实例，
     * id 是构造函数里写死的短名（例如 {@code iceInsect}）。
     * 于是一个「Boss 分裂出来的冰虫」和注册表里的冰虫会是两个不同的 id，
     * 按 id 比较的地方（选择器筛选、效果合并判定等）就会对不上。
     * <p>
     * 已经是完整 id（含 {@code :}）、id 为空、或注册表里没有同类模板时，原样返回。
     *
     * @param entity 运行时实体
     * @return 完整 id
     */
    public static String fullIdOf(Entity entity) {
        if (entity == null) {
            return null;
        }
        String id = entity.getId();
        if (id == null || id.indexOf(':') >= 0) {
            return id;
        }
        String registered = registeredIdOf(entity.getClass(), id, entityList, Thing::getId);
        return registered == null ? id : registered;
    }

    /**
     * 把一个运行时物品的 id 补成注册表里的完整 id。
     *
     * @param item 运行时物品
     * @return 完整 id
     * @see #fullIdOf(Entity)
     */
    public static String fullIdOf(Item item) {
        if (item == null) {
            return null;
        }
        String id = item.getId();
        if (id == null || id.indexOf(':') >= 0) {
            return id;
        }
        String registered = registeredIdOf(item.getClass(), id, itemList, Thing::getId);
        return registered == null ? id : registered;
    }

    /**
     * 把一个运行时效果的 id 补成注册表里的完整 id。
     *
     * @param effect 运行时效果
     * @return 完整 id
     * @see #fullIdOf(Entity)
     */
    public static String fullIdOf(Effect effect) {
        if (effect == null) {
            return null;
        }
        String id = effect.getID();
        if (id == null || id.indexOf(':') >= 0) {
            return id;
        }
        String registered = registeredIdOf(effect.getClass(), id, effectList, Effect::getID);
        return registered == null ? id : registered;
    }

    /**
     * 直接把运行时对象的 id 改成 {@link #fullIdOf(Entity)} 给出的完整 id。
     * <p>
     * 供「对象进入游戏世界」的入口调用（{@link #addThing(Thing)}、
     * {@code Fight.addFighter/addEnemy}、{@code LivingThing.addEffect}），
     * 这样无论实例是在哪里 new 出来的，进入游戏后都带完整的注册表 id。
     * <p>
     * 只补<b>不含 {@code :}</b> 的 id；想给某个实例单独指定 id，写成带 {@code :} 的形式即可
     * （例如 {@code myMod:bossCopy1}），本方法不会动它。
     *
     * @param thing 运行时实体或物品；可为 {@code null}
     */
    public static void applyRegisteredId(Thing thing) {
        if (thing instanceof Entity entity) {
            entity.setId(fullIdOf(entity));
        } else if (thing instanceof Item item) {
            item.setId(fullIdOf(item));
        }
    }

    /**
     * 直接把运行时效果的 id 改成 {@link #fullIdOf(Effect)} 给出的完整 id。
     *
     * @param effect 运行时效果；可为 {@code null}
     */
    public static void applyRegisteredId(Effect effect) {
        if (effect != null) {
            effect.setId(fullIdOf(effect));
        }
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