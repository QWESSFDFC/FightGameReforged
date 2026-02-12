package com.gfhnv.mods;

import cn.gfhnv.game.entity.Player;
import cn.gfhnv.game.system.ElementSort;

import java.math.BigDecimal;

public class PlayerTwo extends Player {
    public PlayerTwo() {
        super("PLAYER", "player_two", 0.8, 0.2, 0.5, 0.6, 0.99, 122, 125, "player", 22, 55, 55, ElementSort.WOOD, 15, 5, 1);
        this.setMass(BigDecimal.valueOf(60));
        this.getInventory().addSlot(63);
    }
}
