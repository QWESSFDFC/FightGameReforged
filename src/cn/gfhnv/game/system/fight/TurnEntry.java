package cn.gfhnv.game.system.fight;

import cn.gfhnv.game.entity.LivingThing;

public class TurnEntry implements Comparable<TurnEntry> {
    double oValue;
    LivingThing livingThing;

    public TurnEntry(double oValue, LivingThing livingThing) {
        this.oValue = oValue;
        this.livingThing = livingThing;
    }

    @Override
    public String toString() {
        return "TurnEntry{" +
                "oValue=" + oValue +
                ", livingThing=" + livingThing +
                '}';
    }

    public double getoValue() {
        return oValue;
    }

    public void setoValue(double oValue) {
        this.oValue = oValue;
    }

    public LivingThing getLivingThing() {
        return livingThing;
    }

    public void setLivingThing(LivingThing livingThing) {
        this.livingThing = livingThing;
    }

    @Override
    public int compareTo(TurnEntry o) {
        int cmp = Double.compare(this.oValue, o.oValue);
        if (cmp != 0) return cmp;
        if (this.livingThing.equals(o.livingThing)) {
            return 0;
        }
        return Double.compare(o.livingThing.getSpeed(), this.livingThing.getSpeed());
    }
}
