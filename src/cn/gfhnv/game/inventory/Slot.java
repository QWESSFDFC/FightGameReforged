package cn.gfhnv.game.inventory;

import cn.gfhnv.game.item.Item;

public class Slot {
    private Item containedItem;
    private boolean isBanned=false;
    private final long slotNumber;
    public Slot(Item containedItem,long slotNumber) {
        this.containedItem = containedItem;
        this.slotNumber = slotNumber;
    }

    public Item getContainedItem() {
        return containedItem;
    }

    public void setContainedItem(Item containedItem) {
        this.containedItem = containedItem;
    }

    public boolean isBanned() {
        return isBanned;
    }

    public void setBanned(boolean banned) {
        isBanned = banned;
    }

    public long getSlotNumber() {
        return slotNumber;
    }
}
