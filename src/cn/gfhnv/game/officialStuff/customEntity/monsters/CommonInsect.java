package cn.gfhnv.game.officialStuff.customEntity.monsters;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entityController.UniversalController;
import cn.gfhnv.game.officialStuff.customSkill.universalSkill.CommonAttack;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;

import java.util.ArrayList;
import java.util.List;

public class CommonInsect extends LivingThing {

    public CommonInsect(Long l) {
        super("普通虫子", "commonInsect", 0.0, 0.1, 0.95, 0.5, 0.8, 150, l, "insect", 30, 5, 9, ElementSort.METAL, 4, 0.5, 2);
        List<Skill> skills = new ArrayList<>();
        this.setDescription("这是普通虫子.只有普通攻击");
        skills.add(new CommonAttack(0, 0.3, 0, 1));
        this.setController(new UniversalController(skills, this));
    }


    @Override
    public LivingThing copy() {
        return new CommonInsect(this.getLevel());
    }
}
