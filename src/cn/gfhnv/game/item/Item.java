package cn.gfhnv.game.item;

import cn.gfhnv.game.Thing;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.system.fight.Fight;

import java.math.BigDecimal;
import java.util.Objects;

public class Item extends Thing {
    private String name;
    private String description;
    private boolean isForEnemies = false;
    private int stackNumber = 1;

    public Item(String name, String description) {
        this.name = name;
        this.description = description;

    }

    public Item(Item item) {
        this.name = item.getName();
        this.description = item.getDescription();

    }

    public Item(String name, String description, BigDecimal bigDecimal) {
        super(bigDecimal);
        this.name = name;
        this.description = description;
    }

    public Item() {

    }

    public int getStackNumber() {
        return stackNumber;
    }

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

    public boolean isForEnemies() {
        return isForEnemies;
    }

    public void setForEnemies(boolean forEnemies) {
        isForEnemies = forEnemies;
    }

    public void comeToEffect(LivingThing user, Fight fight) {

    }

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }


}
