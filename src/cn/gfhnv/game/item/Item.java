package cn.gfhnv.game.item;

import cn.gfhnv.game.Thing;

import java.math.BigDecimal;

public class Item extends Thing {
    private String name;
    private String description;


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
