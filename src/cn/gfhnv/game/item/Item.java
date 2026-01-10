package cn.gfhnv.game.item;

import cn.gfhnv.game.Thing;

import java.math.BigDecimal;
import java.util.List;

public class Item extends Thing {
    private String name;
    private String description;
    @Override
    public String toString() {
        return "Item{" +
                "name='" + name + '\'' +
                " description='" + description + '\'' +
                '}';
    }
    public Item(String name,String description) {
        this.name = name;
        this.description = description;
    }
    public Item(String name,String description,BigDecimal bigDecimal) {
        super(bigDecimal);
        this.name = name;
        this.description = description;
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
