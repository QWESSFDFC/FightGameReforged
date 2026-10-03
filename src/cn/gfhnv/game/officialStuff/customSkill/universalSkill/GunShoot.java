package cn.gfhnv.game.officialStuff.customSkill.universalSkill;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.DamageEnhanceEffect;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.mana.Mana;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;

import java.util.List;

public class GunShoot extends Skill {

    public GunShoot() {
        super("枪射击", "射出多发子弹.伤害基于攻击力.1冷却", 0, 7.5, 0, 2);
        this.setCoolDown(1);
        this.setConsumedMana(new Mana(10, ElementSort.UNIVERSAL));
        this.getTags().put(TagType.ATTACK, new Tag(5));
    }

    /**
     * 复制构造器。
     * <p>
     * {@link #copy()} 必须走这里，不能返回 {@code new GunShoot()}：那样会把倍率/冷却/消耗
     * 全部还原成构造器里的出厂值，于是打在模板上的配置（{@code SkillData.json}）
     * <b>永远传不到选人时复制出去的副本</b>。
     *
     * @param other 被复制的技能
     */
    public GunShoot(GunShoot other) {
        super(other);
    }

    @Override
    public Skill copy() {
        return new GunShoot(this);
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        user.addEffect(new DamageEnhanceEffect(2, 1).setOrigin("gunShoot"));
        for (LivingThing livingThing : enemies) {
            user.makeDamage(livingThing, this);
        }
    }
}
