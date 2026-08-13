package cn.gfhnv.game.entity;

import cn.gfhnv.game.system.ElementSort;

public class Player extends LivingThing {
    public Player(String name, String id, double fireResistance, double waterResistance, double metalResistance, double woodResistance, double dirtResistance, long speed, long l, String type, double hp, double atk, double dfk, ElementSort yu) {
        super(name, id, fireResistance, waterResistance, metalResistance, woodResistance, dirtResistance, speed, l, type, hp, atk, dfk, yu);
    }

    public Player(Player other) {
        super(other);

    }

    public Player(String name, String id, long l, ElementSort u) {
        super(name, id, l, u);
    }

    @Override
    public LivingThing copy() {
        return new Player(this);
    }
}
