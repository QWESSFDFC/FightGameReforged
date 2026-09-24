package cn.gfhnv.game.inventory;

import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.world.World;

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

    /**
     * 把物品放进背包。
     * <p>
     * 物品会先补全注册表 id（{@link World#applyRegisteredId(cn.gfhnv.game.Thing)}），
     * 然后：背包里已经有<b>同一种</b>物品（{@link Item#equals(Object)} 按 id 判定）→
     * 叠进那一格、{@code stackNumber} 累加（没有上限）；否则放进第一个空格子。
     *
     * @param item 要放入的物品（id 会被补成注册表里的完整 id）
     * @return 是否放进去；背包既没有同类物品、又没有空格子时返回 {@code false}
     */
    public boolean addItem(Item item) {
        if (item == null) return false;
        World.applyRegisteredId(item);
        for (Slot slot : slots) {
            if (slot.getContainedItem() == null) continue;
            if (slot.getContainedItem().equals(item)) {
                slot.getContainedItem().setStackNumber(Math.max(0, slot.getContainedItem().getStackNumber() + item.getStackNumber()));
                if (slot.getContainedItem().getStackNumber() == 0) {
                    slot.setContainedItem(null);
                }
                return true;
            }


        }
        for (Slot slot : slots) {
            if (slot.getContainedItem() == null) {
                slot.setContainedItem(item);
                return true;
            }
        }
        return false;
    }

    /**
     * 消耗<b>一个</b>指定物品：按 id 匹配到那一格，把堆叠数扣 1，扣到 0 就清空那一格。
     * <p>
     * 「使用物品」走的是这个方法（见 {@code PlayerController.useItem}）：
     * 一回合用一次只消耗一个 —— 而不是 {@link #removeItem(Item)} 那样
     * 按物品自带的 {@code stackNumber} 扣掉一整叠。
     *
     * @param item 要消耗的物品（背包里那一件，或它的同类副本）
     * @return 是否消耗成功
     */
    public boolean removeOne(Item item) {
        if (item == null) return false;
        for (Slot slot : slots) {
            if (slot.getContainedItem() == null) continue;
            if (slot.getContainedItem().equals(item)) {
                Item contained = slot.getContainedItem();
                contained.setStackNumber(Math.max(0, contained.getStackNumber() - 1));
                if (contained.getStackNumber() == 0) {
                    slot.setContainedItem(null);
                }
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
        if (item == null) return false;
        for (Slot slot : slots) {
            if (slot.getContainedItem() == null) continue;
            if (slot.getContainedItem().equals(item)) {
                slot.setContainedItem(null);

                return true;
            }
        }
        return false;
    }

    public boolean removeItem(Item item) {
        if (item == null) return false;
        for (Slot slot : slots) {
            if (slot.getContainedItem() == null) continue;
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
            if (slot.getContainedItem() == null) continue;
            slot.getContainedItem().setStackNumber(0);
            slot.setContainedItem(null);
        }
    }

    public void removeItemsAll(List<Item> items) {
        for (Item item : items) {
            for (Slot slot : slots) {
                if (slot.getContainedItem() == null) continue;
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
