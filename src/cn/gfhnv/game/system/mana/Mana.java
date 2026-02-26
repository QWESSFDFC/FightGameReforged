package cn.gfhnv.game.system.mana;

import cn.gfhnv.game.system.ElementSort;

public class Mana {
    private double amount;
    private ElementSort elementSort;

    public Mana(double amount, ElementSort elementSort) {
        this.amount = amount;
        this.elementSort = elementSort;
    }
      public Mana(Mana mana){
        this.amount = mana.amount;
        this.elementSort = mana.elementSort;
      }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public ElementSort getElementSort() {
        return elementSort;
    }

    public void setElementSort(ElementSort elementSort) {
        this.elementSort = elementSort;
    }
}
