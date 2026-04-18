package cn.gfhnv.game.inventory;

import cn.gfhnv.game.item.Item;
import org.json.JSONObject;

public class Slot {
    private final long slotNumber;
    private Item containedItem;

    public Slot(Item containedItem, long slotNumber) {
        this.containedItem = containedItem;
        this.slotNumber = slotNumber;
    }

    public Slot() {
 slotNumber=0;
    }


    public Item getContainedItem() {
        return containedItem;
    }

    public void setContainedItem(Item containedItem) {
        this.containedItem = containedItem;
    }

    public long getSlotNumber() {
        return slotNumber;
    }
}
