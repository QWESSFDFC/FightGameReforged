package cn.gfhnv.game.inventory;

import cn.gfhnv.game.item.Item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class Inventory {
    private List<Slot> slots = new ArrayList<>();

    //背包有slot/格子.格子中才放物品
    public Inventory(long slotNumbers) {
        for (long i = 0; i < slotNumbers; i++) {
            slots.add(new Slot(null, i));
        }
    }

    public Inventory() {
    }

    public Inventory(Inventory inventory) {
        for (Slot slot : inventory.getSlots()) {
            slots.add(slot.copy());
        }

    }

    public Inventory copy() {
        return new Inventory(this);
    }

    public boolean addItem(Item item) {
        for (Slot slot : slots) {
            if (slot.getContainedItem().equals(item)) {
                slot.getContainedItem().setStackNumber(Math.max(0, slot.getContainedItem().getStackNumber() + item.getStackNumber()));
                if (slot.getContainedItem().getStackNumber() == 0) {
                    slot.setContainedItem(null);
                }
                return true;
            }
            if (slot.getContainedItem() == null) {
                slot.setContainedItem(item);
                return true;
            }

        }
        return false;
    }

    public void sort() {
        if (slots == null || slots.isEmpty()) return;
        slots.sort(Comparator.comparingLong(Slot::getSlotNumber));
    }

    public boolean removeItemAll(Item item) {
        for (Slot slot : slots) {
            if (slot.getContainedItem()==null) continue;
            if (slot.getContainedItem().equals(item)) {
                slot.setContainedItem(null);

                return true;
            }
        }
        return false;
    }

    public boolean removeItem(Item item) {
        for (Slot slot : slots) {
            if (slot.getContainedItem()==null) continue;
            if (slot.getContainedItem().equals(item)) {
                slot.getContainedItem().setStackNumber(Math.max(0, slot.getContainedItem().getStackNumber() - item.getStackNumber()));

                if (slot.getContainedItem().getStackNumber() == 0) {
                    slot.setContainedItem(null);

                }
                return true;
            }
        }
        return false;
    }


    public void clear() {
        for (Slot slot : slots) {
            if (slot.getContainedItem()==null) continue;
            slot.getContainedItem().setStackNumber(0);
            slot.setContainedItem(null);
        }
    }

    public void removeItemsAll(List<Item> items) {
        for (Item item : items) {
            for (Slot slot : slots) {
                if (slot.getContainedItem()==null) continue;
                if (slot.getContainedItem().equals(item)) {
                    slot.setContainedItem(null);
                }
            }
        }
    }

    public void addItem(Item[] items) {
        for (Item item : items) {
            this.addItem(item);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Inventory inventory = (Inventory) o;
        return Objects.equals(getSlots(), inventory.getSlots());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getSlots());
    }

    public void removeSlot(long slotNumber) {
        this.slots.removeIf(slot -> slot.getSlotNumber() == slotNumber);
    }

    public void addSlot(long slotNumbers) {
        long startIndex = slots.size();
        for (long i = 0; i < slotNumbers; i++) {
            slots.add(new Slot(null, startIndex + i));
        }
        sort();
    }

    public List<Slot> getSlots() {
        return slots;
    }

    public void setSlots(List<Slot> slots) {
        this.slots = slots;
    }
}
