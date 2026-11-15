package cn.gfhnv.game.system.mana;

import cn.gfhnv.game.system.ElementSort;

public class Mana {
    private double amount;
    private double amountMax;
    private ElementSort elementSort;

    public Mana(double amount, ElementSort elementSort) {
        this.amount = amount;
        this.elementSort = elementSort;
        this.amountMax = amount;
    }

    public Mana(Mana mana) {
        this.amount = mana.amount;
        this.elementSort = mana.elementSort;
        this.amountMax = mana.amountMax;
    }

    public double getAmountMax() {
        return amountMax;
    }

    public void setAmountMax(double amountMax) {
        this.amountMax = amountMax;
        if (this.amount > amountMax) {
            this.amount = amountMax;
        }
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        double before = this.amount;
        this.amount = Math.min(amount, amountMax);
        if (this.amount != before) {
            // 法力变化（第 74 批新增事件）：回蓝、技能扣蓝都经过这里；Mana 不持有拥有者
            cn.gfhnv.game.event.EventBus.post(
                    new cn.gfhnv.game.event.ManaChangedEvent(this, before, this.amount));
        }
    }

    public ElementSort getElementSort() {
        return elementSort;
    }

    public void setElementSort(ElementSort elementSort) {
        this.elementSort = elementSort;
    }
}
