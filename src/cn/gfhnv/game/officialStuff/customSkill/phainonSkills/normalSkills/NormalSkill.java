package cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEntity.players.Phainon;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.mana.Mana;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;

import java.util.List;

public class NormalSkill extends Skill {
    public NormalSkill() {
        super("战技:黎明创世,地辟天开", "description", 0, 3, 0, 3);
        this.setCoolDown(0);
        this.setConsumedMana(new Mana(120, ElementSort.FIRE));
        this.getTags().put(TagType.ATTACK, new Tag(5));
    }

    /**
     * 复制构造器。
     * <p>
     * {@link #copy()} 必须走这里，不能返回 {@code new NormalSkill()}：那样会把倍率/冷却/消耗
     * 全部还原成构造器里的出厂值，于是打在模板上的配置（{@code SkillData.json}）
     * <b>永远传不到选人时复制出去的副本</b>。
     *
     * @param other 被复制的技能
     */
    public NormalSkill(NormalSkill other) {
        super(other);
    }

    @Override
    public Skill copy() {
        return new NormalSkill(this);
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        if (user instanceof Phainon) {
            ((Phainon) user).setCoreflame(Math.min(((Phainon) user).getCoreflame_max(), ((Phainon) user).getCoreflame() + 2));
            System.out.println(user.getName() + "获得了两个火种");
        }
        for (LivingThing livingThing : enemies) {
            user.makeDamage(livingThing, this);

        }
    }
}
