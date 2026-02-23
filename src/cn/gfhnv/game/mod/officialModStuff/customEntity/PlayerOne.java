package cn.gfhnv.game.mod.officialModStuff.customEntity;

import cn.gfhnv.game.entity.Player;
import cn.gfhnv.game.entity.entityController.UniversalController;
import cn.gfhnv.game.entity.skill.Skill;
import cn.gfhnv.game.mod.officialModStuff.customSkill.CommonAttack;
import cn.gfhnv.game.system.ElementSort;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class PlayerOne extends Player {
    public PlayerOne(long l) {
        super("玩家一", "player_one", 0.3, 0.0, 0.0, 0.0, 0.3, 120, l, "player", 9, 25, 5, ElementSort.DIRT, 10, 5, 0);
        this.setMass(BigDecimal.valueOf(60));
        this.setDescription("这是玩家一.目前只有普通攻击");
        this.getInventory().addSlot(63);
        List<Skill> skills = new ArrayList<>();
        skills.add(new CommonAttack(0, 1, 0));
        this.setController(new UniversalController(skills, this));
    }


}
