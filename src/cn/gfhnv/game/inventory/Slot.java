package cn.gfhnv.game.inventory;

import cn.gfhnv.game.item.Item;

import java.util.Objects;

public class Slot {
    private final long slotNumber;
    private Item containedItem;


    public Slot(Slot slot) {
        slotNumber = slot.getSlotNumber();
        if (slot.getContainedItem() == null) return;
        setContainedItem(slot.getContainedItem().copy());
    }

    public Slot(Item containedItem, long slotNumber) {
        this.containedItem = containedItem;
        this.slotNumber = slotNumber;
    }

    public Slot() {
        slotNumber = 0;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Slot slot = (Slot) o;
        return getSlotNumber() == slot.getSlotNumber();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getSlotNumber());
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
