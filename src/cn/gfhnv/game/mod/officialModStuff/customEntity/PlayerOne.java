package cn.gfhnv.game.mod.officialModStuff.customEntity;

import cn.gfhnv.game.entity.Player;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.utils.JSONHelper;

import java.math.BigDecimal;

public class PlayerOne extends Player {
    public PlayerOne() {
        super("PLAYER", "player_one", 0.3, 0.0, 0.0, 0.0, 0.3, 134, 180, "player", 9, 25, 5, ElementSort.DIRT, 10, 5, 0);
        this.setMass(BigDecimal.valueOf(60));
        this.getInventory().addSlot(63);
    }
}
