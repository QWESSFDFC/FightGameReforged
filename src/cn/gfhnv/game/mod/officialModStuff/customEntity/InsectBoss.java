package cn.gfhnv.game.mod.officialModStuff.customEntity;

import cn.gfhnv.game.entity.LivingThing;

import java.math.BigDecimal;

import static cn.gfhnv.game.system.ElementSort.METAL;

public class InsectBoss extends LivingThing {
    public InsectBoss() {
        super("insectBoss", "insectBoss", 0.1, 0.2, 0.8, 0.2, 0.3, 150, 95, "insect", 40, 1, 2, METAL, 10, 5, 1);
        this.setMass(BigDecimal.valueOf(1250));
        this.getInventory().addSlot(63);
    }

    @Override
    public void entityAct() {
        System.out.println("Boss行动");
        onActionTaken();
    }
}
