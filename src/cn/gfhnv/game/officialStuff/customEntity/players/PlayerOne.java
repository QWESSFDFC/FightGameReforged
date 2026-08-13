package cn.gfhnv.game.officialStuff.customEntity.players;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entity.Player;
import cn.gfhnv.game.entityController.PlayerController;
import cn.gfhnv.game.officialStuff.customSkill.universalSkill.CommonAttack;
import cn.gfhnv.game.officialStuff.customSkill.universalSkill.GunShoot;
import cn.gfhnv.game.officialStuff.customSkill.universalSkill.RestorationHealthSkill;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;

import java.util.ArrayList;
import java.util.List;

public class PlayerOne extends Player {
    public PlayerOne(PlayerOne playerOne) {
        super(playerOne);
    }

    public PlayerOne(long l) {
        super("玩家一", "playerOne", 0.3, 0.0, 0.0, 0.0, 0.3, 120, l, "player", 36, 29, 5, ElementSort.DIRT);
        this.setMass(60);
        this.setDescription("这是玩家一.");
        this.getInventory().addSlot(63);
        List<Skill> skills = new ArrayList<>();
        skills.add(new GunShoot());
        skills.add(new RestorationHealthSkill(0.1, 0.9, 0, 3, 90));
        skills.add(new CommonAttack(0, 1, 0, 1));
        this.setController(new PlayerController(skills, this));
    }


    @Override
    public LivingThing copy() {
        return new PlayerOne(this);
    }

}
