package cn.gfhnv.game.inventory;

import cn.gfhnv.game.item.Item;

import java.util.ArrayList;
import java.util.List;

public class Inventory {
    private List<Slot> slots = new ArrayList<>();
    public boolean addItem(Item item) {
        for (Slot slot : slots) {
            if (slot.getContainedItem()==null){
                slot.setContainedItem(item);
                return true;
            }
        }
        return false;
    }
    public boolean removeItem(Item item) {
        for (Slot slot : slots) {
            if (slot.getContainedItem()==item){
                slot.setContainedItem(null);
                return true;
            }
        }
        return false;
    }
    public void removeItems(Item[] items) {
        for (Item item : items) {
            for (Slot slot : slots) {
                if (slot.getContainedItem()==item){
                    slot.setContainedItem(null);
                }
            }
        }
    }
    public void clear() {for (Slot slot : slots) slot.setContainedItem(null);}
    public void removeItems(List<Item> items) {
        for (Item item : items) {
            for (Slot slot : slots) {
                if (slot.getContainedItem()==item){
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
    public Inventory(long slotNumbers) {
        for (long i = 0; i < slotNumbers; i++) {
            slots.add(new Slot(null,i));
        }
    }
  public void removeSlot(long slotNumber) {
        this.slots.removeIf(slot -> slot.getSlotNumber() == slotNumber);
  }
  public void addSlot(long slotNumbers) {
        for (long i = 0; i < slotNumbers; i++) {
            this.slots.add(new Slot(null,i+slots.size() - 1));
        }
  }
    public List<Slot> getSlots() {
        return slots;
    }

    public void setSlots(List<Slot> slots) {
        this.slots = slots;
    }
}
