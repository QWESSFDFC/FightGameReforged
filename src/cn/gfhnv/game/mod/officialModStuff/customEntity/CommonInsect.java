package cn.gfhnv.game.mod.officialModStuff.customEntity;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entity.entityController.UniversalController;
import cn.gfhnv.game.entity.skill.Skill;
import cn.gfhnv.game.mod.officialModStuff.customSkill.CommonAttack;
import cn.gfhnv.game.system.ElementSort;

import java.util.ArrayList;
import java.util.List;

public class CommonInsect extends LivingThing {

    public CommonInsect(Long l) {
        super("普通虫子", "commonInsect", 0.0, 0.1, 0.95, 0.5, 0.8, 150, l, "insect", 30, 5, 9, ElementSort.METAL, 5, 2, 3);
        List<Skill> skills = new ArrayList<>();
        this.setDescription("这是普通虫子.只有普通攻击");
        skills.add(new CommonAttack(0, 0.3, 0));
        this.setController(new UniversalController(skills, this));
    }
}
