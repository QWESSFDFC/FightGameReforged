package cn.gfhnv.game.item;

import cn.gfhnv.game.Thing;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * 物品基类。表示游戏中的可用物品，继承自 {@link Thing}（因此也拥有物理属性与 Tag 权重）。
 * <p>
 * 物品可以通过 {@link #comeToEffect(LivingThing, Fight)} 实现使用效果（例如回复生命、增强攻击等），
 * 并通过 {@link #copy()} 生成副本（避免不同实体共享同一可变实例）。
 * <p>
 * 字段说明：
 * <ul>
 *     <li><b>name</b>：物品名称；</li>
 *     <li><b>description</b>：物品描述；</li>
 *     <li><b>isForEnemies</b>：是否作用于敌方（默认 {@code false}，作用于己方）；</li>
 *     <li><b>stackNumber</b>：堆叠数量。</li>
 * </ul>
 * 物品注册到模组时可通过 {@link cn.gfhnv.game.mod.Mod#addItem(Item)} 自动获得 {@code MOD_ID:} 前缀。若 MOD_ID 非空，物品的 id 会被自动加上前缀。
 *
 * @author gfhnv
 */
public class Item extends Thing {
    private String name;
    private String description;
    private boolean isForEnemies = false;
    private int stackNumber = 1;

    /**
     * 构造一个物品。
     *
     * @param name        物品名称
     * @param description 物品描述
     * @param id          物品唯一标识
     */
    public Item(String name, String description, String id) {
        this.name = name;
        this.setId(id);
        this.description = description;

    }

    /**
     * 复制构造器（深拷贝）。复制名称、描述、堆叠数量、id 与 Tag 权重表。
     *
     * @param item 被复制的物品
     */
    public Item(Item item) {
        this.name = item.getName();
        this.description = item.getDescription();
        this.stackNumber = item.stackNumber;
        this.setId(item.getId());
        if (!item.getTags().isEmpty()) {
            Map<TagType, Tag> newMap = new EnumMap<>(TagType.class);
            for (Map.Entry<TagType, Tag> entry : item.getTags().entrySet()) {
                newMap.put(entry.getKey(), entry.getValue().copy());
            }
            this.setTags(newMap);
        }

    }

    /**
     * 构造一个带质量的物品。
     *
     * @param name        物品名称
     * @param description 物品描述
     * @param mass        物品质量（物理属性）
     * @param id          物品唯一标识
     */
    public Item(String name, String description, double mass, String id) {
        super(mass);
        this.setId(id);
        this.name = name;
        this.description = description;
    }

    public Item() {

    }

    /**
     * @return 物品的堆叠数量
     */
    public int getStackNumber() {
        return stackNumber;
    }

    /**
     * 设置物品的堆叠数量。
     *
     * @param stackNumber 堆叠数量
     */
    public void setStackNumber(int stackNumber) {
        this.stackNumber = stackNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        Item item = (Item) o;
        return isForEnemies() == item.isForEnemies() && Objects.equals(getName(), item.getName()) && Objects.equals(getDescription(), item.getDescription());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getName(), getDescription(), isForEnemies());
    }

    /**
     * @return 该物品是否作用于敌方。{@code true} 表示目标是敌人，{@code false} 表示目标是己方。
     */
    public boolean isForEnemies() {
        return isForEnemies;
    }

    /**
     * 设置该物品是否作用于敌方。
     *
     * @param forEnemies {@code true} 表示目标是敌人，{@code false} 表示目标是己方
     */
    public void setForEnemies(boolean forEnemies) {
        isForEnemies = forEnemies;
    }

    /**
     * 物品使用效果。子类应重写此方法实现具体的物品效果（如回血、增益等）。
     * 如果需要选择目标，请在子类中自行处理目标选择逻辑。
     *
     * @param user  使用物品的实体
     * @param fight 当前战斗上下文
     */
    public void comeToEffect(LivingThing user, Fight fight) {

    }

    /**
     * 复制物品。默认调用复制构造器 {@link #Item(Item)}；
     * 子类若持有额外可变状态，应重写此方法返回正确的副本。
     *
     * @return 物品的深拷贝实例
     */
    public Item copy() {
        return new Item(this);
    }

    public Item facSetName(String name) {
        this.setName(name);
        return this;
    }

    public Item facSetDescription(String description) {
        this.setDescription(description);
        return this;
    }

    @Override
    public String toString() {
        return "Item{" +
                "name='" + name + '\'' +
                " description='" + description + '\'' +
                '}';
    }

    /**
     * @return 物品名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置物品名称。
     *
     * @param name 物品名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return 物品描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置物品描述。
     *
     * @param description 物品描述
     */
    public void setDescription(String description) {
        this.description = description;
    }


}
