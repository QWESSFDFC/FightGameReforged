package cn.gfhnv.game.entity;

import cn.gfhnv.game.Thing;
import cn.gfhnv.game.inventory.Inventory;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public class Entity extends Thing {
    private long level;
    private String name;

    private String type = "entity";
    private Inventory inventory = new Inventory();

    public Entity(Entity entity) {

        this.level = entity.level;
        this.name = entity.name;
        this.setId(entity.getId());

        this.type = entity.type;

        if (entity.getTags().isEmpty()) {
            Map<TagType, Tag> newMap = new EnumMap<>(TagType.class);
            for (Map.Entry<TagType, Tag> entry : entity.getTags().entrySet()) {
                newMap.put(entry.getKey(), entry.getValue().copy());
            }
            this.setTags(newMap);
        }
    }

    public Entity() {
        super(new BigDecimal(1));
    }

    public Entity(String name, String id, long l) {
        super(new BigDecimal(1));
        this.name = name;
        this.setId(id);
        this.level = l;

    }

    public Entity(String name, String id, long l, BigDecimal bigDecimal) {
        super(bigDecimal);
        this.name = name;
        this.setId(id);
        this.level = l;


    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        Entity entity = (Entity) o;
        return Objects.equals(getName(), entity.getName()) && Objects.equals(getType(), entity.getType());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getName(), getType());
    }

    public Entity facSetLevel(long level) {
        this.setLevel(level);

        return this;
    }

    public Entity facSetName(String name) {
        this.setName(name);

        return this;
    }

    public Entity facSetId(String id) {
        this.setId(id);

        return this;
    }


    public Entity facSetType(String type) {
        this.setType(type);

        return this;
    }


    public LivingThing transToLivingTing() {
        if (this instanceof LivingThing) {
            return (LivingThing) this;
        }
        System.out.println("ERROR!TRANS FAILED! RETURN NULL.Entity.transToLivingTing!");
        return null;

    }


    public void showState() {
        System.out.println(this.getName());
        System.out.println(this.getId());
        System.out.println(this.getVelocity() + "Velocity");
        System.out.println(this.getMass() + "Mass");
        System.out.println(this.getAcceleration() + "Acceleration");
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public long getLevel() {
        return level;
    }

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


    public Inventory getInventory() {
        return inventory;
    }


    protected void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
