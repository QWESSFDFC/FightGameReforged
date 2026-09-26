package cn.gfhnv.game.officialStuff.customSkill.universalSkill;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.Frozen;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.mana.Mana;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;

import java.util.List;

public class Freeze extends Skill {
    public Freeze() {
        super("冰冻", "冰冻目标1回合", 0, 7.5, 0, 2);
        this.setCoolDown(2);
        this.setConsumedMana(new Mana(10, ElementSort.WATER));
        this.getTags().put(TagType.ATTACK, new Tag(1));
    }

    @Override
    public Skill copy() {
        return new Freeze();
    }

    /**
     * 冰冻所有被指定的目标，每个目标打一行日志。
     * <p>
     * 顺手修了两个小毛病：原来第二行用的是 {@code printf} 且格式里没有换行符，
     * 冻两个目标时那一行会和后面的输出挤在同一行；现在统一用 {@code println}。
     * 施法者与目标的名字都带上阵营（见 {@link LivingThing#getNameWithSide()}）。
     *
     * @param fight   当前战斗上下文
     * @param user    施法者
     * @param enemies 目标列表
     */
    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        for (LivingThing livingThing : enemies) {
            System.out.println(user.getNameWithSide() + "冰冻了" + livingThing.getNameWithSide());
            livingThing.addEffect(new Frozen().setOrigin(user.getUUID()));
        }
    }
}
