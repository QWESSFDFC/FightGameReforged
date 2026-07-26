package cn.gfhnv.game.inventory;

import cn.gfhnv.game.item.Item;

public class Slot {
    private final long slotNumber;
    private Item containedItem;


    public Slot(Slot slot) {
        slotNumber = slot.getSlotNumber();
        setContainedItem(slot.getContainedItem().copy());
    }

    public Slot(Item containedItem, long slotNumber) {
        this.containedItem = containedItem;
        this.slotNumber = slotNumber;
    }

    public Slot() {
        slotNumber = 0;
    }

    public Slot copy() {
        return new Slot(this);
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
