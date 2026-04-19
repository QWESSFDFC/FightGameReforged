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
        this.amount = Math.min(amount, amountMax);
    }

    public ElementSort getElementSort() {
        return elementSort;
    }

    public void setElementSort(ElementSort elementSort) {
        this.elementSort = elementSort;
    }
}
