package cn.gfhnv.game.mod;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.Entity;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 模组基类。所有外部模组的主类必须继承本类，并通过 {@link #invokeWhenLoaded()} 方法向模组的各个 List 中注册内容。
 * <p>
 * 模组加载流程（由 {@link ModLoader} 触发）：
 * <ol>
 *     <li>{@link ModLoader#modLoaderInitialize()} 扫描 mods 目录下的每个模组文件夹；</li>
 *     <li>解析 {@code main.json} 获取模组信息（名称、作者、主类等）；</li>
 *     <li>动态编译 {@code code/} 目录下的 .java 源码并加载类；</li>
 *     <li>实例化主类（构造器需接收 {@link ModInformation}），并将其余类写入 {@link #modClasses} 防止被 GC 回收；</li>
 *     <li>模组实例加入 {@link cn.gfhnv.game.world.World}，随后由 GameStartEvent 触发 {@link #invokeWhenLoaded()} 与 {@link #registerItself()}。</li>
 * </ol>
 * <p>
 * <b>注意事项</b>：
 * <ul>
 *     <li>推荐在 {@link #invokeWhenLoaded()} 中通过 {@link #addItem(Item)}、{@link #addEntity(cn.gfhnv.game.entity.Entity)}、{@link #addEffect(cn.gfhnv.game.effect.Effect)} 添加内容，
 *     再调用 {@link #registerItself()} 注册到游戏全局注册表（不推荐像官方内容 {@code OfficialGameContent} 那样在构造器中直接注册）；</li>
 *     <li>如 MOD_ID 非 {@code null}，通过上述 add 方法添加的内容会自动加上 {@code MOD_ID:} 前缀，避免内容 ID 冲突。</li>
 * </ul>
 *
 * @author gfhnv
 */
public abstract class Mod {
    private final String MOD_ID;
    private List<Class<?>> modClasses = new ArrayList<>();//模组加载时模组的其他类自动添加到这个里面,防止被gc回收
    private ModInformation modInformation;
    private List<Entity> entityList = new ArrayList<>();//模组各个内容先在invokeWhenLoaded方法中添加到模组的各个List中.不要弄错了List类型
    private List<Item> items = new ArrayList<>();
    private List<Effect> effects = new ArrayList<>();

    /**
     * 构造一个模组。此构造器不会为内容自动添加 {@code MOD_ID} 前缀。
     *
     * @param modID 模组的唯一 ID
     */
    public Mod(String modID) {
        this.MOD_ID = modID;
    }

    /**
     * 构造一个带模组信息的模组。此构造器不会为内容自动添加 {@code MOD_ID} 前缀。
     *
     * @param MOD_ID         模组的唯一 ID
     * @param modInformation 模组元信息（名称、作者、描述、主类、版本）
     */
    public Mod(String MOD_ID, ModInformation modInformation) {
        this.MOD_ID = MOD_ID;
        this.modInformation = modInformation;
    }//请模组加载时把模组内容在invokeWhenLoaded方法中添加到模组的各个List中.不要学officialStuff

    /**
     * 记录模组的类，使其不被 GC 回收。通常由 {@link ModLoader} 在加载时自动调用。
     *
     * @param clazz 模组内的类
     */
    public void addModClass(Class<?> clazz) {
        modClasses.add(clazz);
    }

    /**
     * 按全限定类名查找模组内的类。
     *
     * @param name 类的全限定名（如 {@code com.example.MyMod}）
     * @return 匹配的类；若不存在返回 {@code null}
     */
    public Class<?> getClassByName(String name) {
        for (Class<?> clazz : modClasses) {
            if (clazz.getName().equals(name)) {
                return clazz;
            }
        }
        System.out.println(modInformation.getName() + "没有" + name);
        return null;
    }

    /**
     * @return 模组元信息（名称、作者、描述、主类、版本）
     */
    public ModInformation getModInformation() {
        return modInformation;
    }

    /**
     * 设置模组元信息。
     *
     * @param modInformation 模组元信息
     */
    public void setModInformation(ModInformation modInformation) {
        this.modInformation = modInformation;
    }

    /**
     * @return 模组注册的实体列表
     */
    public List<Entity> getEntityList() {
        return entityList;
    }

    /**
     * 设置模组的实体列表。
     *
     * @param entityList 实体列表
     */
    public void setEntityList(List<Entity> entityList) {
        this.entityList = entityList;
    }

    /**
     * @return 模组注册的物品列表
     */
    public List<Item> getItems() {
        return items;
    }

    /**
     * 设置模组的物品列表。
     *
     * @param items 物品列表
     */
    public void setItems(List<Item> items) {
        this.items = items;
    }

    /**
     * 向模组注册一个物品。若 MOD_ID 非 {@code null}，物品 id 会被自动加上 {@code MOD_ID:} 前缀。
     *
     * @param item 要注册的物品
     */
    public void addItem(Item item) {
        items.add(item);
        if (MOD_ID == null) return;
        item.setId(this.MOD_ID + ":" + item.getId());
    }

    /**
     * 从模组中移除一个物品。
     *
     * @param item 要移除的物品
     */
    public void removeItem(Item item) {
        items.remove(item);
    }

    /**
     * 向模组注册一个实体。若 MOD_ID 非 {@code null}，实体 id 会被自动加上 {@code MOD_ID:} 前缀。
     *
     * @param entity 要注册的实体
     */
    public void addEntity(Entity entity) {
        entityList.add(entity);
        if (MOD_ID == null) return;
        entity.setId(this.MOD_ID + ":" + entity.getId());

    }

    /**
     * 从模组中移除一个实体。
     *
     * @param entity 要移除的实体
     */
    public void removeEntity(Entity entity) {
        entityList.remove(entity);
    }

    /**
     * 向模组注册一个效果。若 MOD_ID 非 {@code null}，效果 id 会被自动加上 {@code MOD_ID:} 前缀。
     *
     * @param effect 要注册的效果
     */
    public void addEffect(Effect effect) {
        effects.add(effect);
        if (MOD_ID == null) return;
        effect.setId(MOD_ID + ":" + effect.getId());
    }

    /**
     * 从模组中移除一个效果。
     *
     * @param effect 要移除的效果
     */
    public void removeEffect(Effect effect) {
        if (!effects.contains(effect)) {
            return;
        }
        effects.remove(effect);
    }

    /**
     * 模组加载完成后调用。
     * <p>
     * 模组开发者应在本方法中把模组内容添加到模组对应的 List 中（例如通过 {@link #addItem(Item)}、{@link #addEntity(cn.gfhnv.game.entity.Entity)}），
     * 而<b>不要</b>像官方内容 {@code OfficialGameContent} 那样在构造器中直接注册。
     *
     */
    public void invokeWhenLoaded() {
    }

    /**
     * 将模组内已收集的内容（物品、实体、效果）注册到游戏全局注册表
     * （{@link cn.gfhnv.game.world.World#getItemList()}、{@link cn.gfhnv.game.world.World#getEntityList()}、
     * {@link cn.gfhnv.game.world.World#getEffectList()}）。
     * <p>
     * 重复的内容不会被重复注册。此方法通常在 {@link #invokeWhenLoaded()} 之后由框架调用。
     */
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

    /**
     * @return 模组的唯一 ID
     */
    public String getMOD_ID() {
        return MOD_ID;
    }

    /**
     * @return 模组记录的类列表（用于防止模组类被 GC 回收）
     */
    public List<Class<?>> getModClasses() {
        return modClasses;
    }
}
