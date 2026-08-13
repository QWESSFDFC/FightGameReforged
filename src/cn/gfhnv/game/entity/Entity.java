package cn.gfhnv.game.entity;

import cn.gfhnv.game.Thing;
import cn.gfhnv.game.inventory.Inventory;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;

import java.util.EnumMap;
import java.util.Map;

/**
 * 实体基类。表示游戏世界中具有等级、名称、类型与背包的对象，继承自 {@link Thing}。
 * <p>
 * {@link LivingThing}（生物）继承自本类。当通过 {@link #setLevel(long)} 修改等级时，
 * 若本实体是 {@link LivingThing}，会自动按成长系数重新计算生命值、防御力、攻击力、法力等属性。
 *
 * @author gfhnv
 */
public class Entity extends Thing {
    private long level;
    private String name;

    private String type = "entity";
    private Inventory inventory = new Inventory();

    /**
     * 复制构造器（深拷贝）。复制等级、名称、id、类型与 Tag 权重表。
     *
     * @param entity 被复制的实体
     */
    public Entity(Entity entity) {

        this.level = entity.level;
        this.name = entity.name;
        this.setId(entity.getId());

        this.type = entity.type;

        if (!entity.getTags().isEmpty()) {
            Map<TagType, Tag> newMap = new EnumMap<>(TagType.class);
            for (Map.Entry<TagType, Tag> entry : entity.getTags().entrySet()) {
                newMap.put(entry.getKey(), entry.getValue().copy());
            }
            this.setTags(newMap);
        }
    }

    /**
     * 构造一个默认质量（1.0）的空实体。
     */
    public Entity() {
        super(1);
    }

    /**
     * 构造一个指定名称、id 与等级的实体。
     *
     * @param name 实体名称
     * @param id   实体唯一标识
     * @param l    等级
     */
    public Entity(String name, String id, long l) {
        super(1);
        this.name = name;
        this.setId(id);
        this.level = l;

    }

    /**
     * 构造一个指定名称、id、等级与质量的实体。
     *
     * @param name 实体名称
     * @param id   实体唯一标识
     * @param l    等级
     * @param mass 质量（物理属性）
     */
    public Entity(String name, String id, long l, double mass) {
        super(mass);
        this.name = name;
        this.setId(id);
        this.level = l;


    }


    /**
     * 链式设置等级。
     *
     * @param level 等级
     * @return 当前实体实例
     */
    public Entity facSetLevel(long level) {
        this.setLevel(level);

        return this;
    }

    /**
     * 链式设置名称。
     *
     * @param name 名称
     * @return 当前实体实例
     */
    public Entity facSetName(String name) {
        this.setName(name);

        return this;
    }

    /**
     * 链式设置 id。
     *
     * @param id 唯一标识
     * @return 当前实体实例
     */
    public Entity facSetId(String id) {
        this.setId(id);

        return this;
    }

    /**
     * 链式设置类型。
     *
     * @param type 类型
     * @return 当前实体实例
     */
    public Entity facSetType(String type) {
        this.setType(type);

        return this;
    }

    /**
     * 将当前实体转换为 {@link LivingThing}。
     *
     * @return 若当前实体是 LivingThing 则返回其引用；否则打印错误并返回 {@code null}
     */
    public LivingThing transToLivingTing() {
        if (this instanceof LivingThing) {
            return (LivingThing) this;
        }
        System.out.println("ERROR!TRANS FAILED! RETURN NULL.Entity.transToLivingTing!");
        return null;

    }


    /**
     * 在控制台打印实体的基本状态（名称、id、速度、质量、加速度）。
     */
    public void showState() {
        System.out.println(this.getName());
        System.out.println(this.getId());
        System.out.println(this.getVelocity() + "Velocity");
        System.out.println(this.getMass() + "Mass");
        System.out.println(this.getAcceleration() + "Acceleration");
    }


    /**
     * @return 实体名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置实体名称。
     *
     * @param name 名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return 实体类型
     */
    public String getType() {
        return type;
    }

    /**
     * 设置实体类型。
     *
     * @param type 类型
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * @return 实体等级
     */
    public long getLevel() {
        return level;
    }

    /**
     * 设置实体等级。若当前实体是 {@link LivingThing}，
     * 会按成长系数与等级自动重算生命值、防御、攻击、法力上限并初始化法力。
     *
     * @param level 等级
     */
    public void setLevel(long level) {
        this.level = level;
        if (this instanceof LivingThing) {
            this.transToLivingTing().setHp((long) ((level - 1) * this.transToLivingTing().getHpGrowNumber() + 200));
            this.transToLivingTing().setDefence((long) ((level - 1) * this.transToLivingTing().getDfkGrowNumber() + 200));
            this.transToLivingTing().setAttack((long) (110 + this.transToLivingTing().getAtkGrowNumber() * (level - 1)));
            ((LivingThing) this).setHpMax(this.transToLivingTing().getHp());
            this.transToLivingTing().initialMana();
        }

    }


    /**
     * @return 实体的背包
     */
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * 设置实体的背包（受保护，供子类或包内使用）。
     *
     * @param inventory 背包
     */
    protected void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

}
