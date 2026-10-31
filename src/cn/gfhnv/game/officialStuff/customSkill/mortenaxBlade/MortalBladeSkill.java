package cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.Taunt;
import cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.mana.Mana;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;

import java.util.List;

/**
 * 千冶·刃 · <b>强化普攻</b>「淬锋，断魄」（【无量忿怒】期间替换「残锋，掠尽」）：
 * 单体，伤害 = 自身<b>生命上限的 100%</b>，命中后使目标嘲讽 1 回合。
 * <p>
 * 设计稿见 `TODO.md`（「淬锋，断魄」[单攻] + 【无量忿怒】"<b>普攻获得强化</b>"）；
 * 机制映射见 `project_analyses/MORTENAX-BLADE-2026-10.md`。英文版也把它列在强化普攻那一栏
 * （{@code A Tempered Blade Severs Souls}）。
 * <p>
 * <b>它不在普通形态的技能表里</b>：普通形态是「残锋，掠尽」+「骸骨当炉」，
 * 开结界后整表换成「淬锋，断魄」+「刃下，归葬」+「千冶铸一」——
 * 所以 {@code canUse} 也要求处于【无量忿怒】（双保险：就算被别的途径拿到也放不出来）。
 *
 * @author AI（DeepSeek）生成；设计稿作者：仓库作者
 */
public class MortalBladeSkill extends Skill {

    /**
     * 嘲讽持续回合数（设计：1 回合）。
     */
    private static final int TAUNT_LAST_TIME = 1;

    public MortalBladeSkill() {
        super("淬锋，断魄", "对指定敌方单体造成等同于千冶·刃100%生命上限的火属性伤害，并使目标陷入嘲讽状态，持续1回合。",
                1.0, 0, 0, 1);
        this.setCoolDown(0);
        this.setConsumedMana(new Mana(0, ElementSort.FIRE));
        this.getTags().put(TagType.ATTACK, new Tag(5));
    }

    /**
     * 复制构造器（理由见 {@link MortalBladeCommonAttack#MortalBladeCommonAttack(MortalBladeCommonAttack)}）。
     *
     * @param other 被复制的技能
     */
    public MortalBladeSkill(MortalBladeSkill other) {
        super(other);
    }

    @Override
    public Skill copy() {
        return new MortalBladeSkill(this);
    }

    /**
     * 门槛：千冶·刃专用、而且<b>必须在【无量忿怒】里</b>。
     *
     * @param fight 当前战斗
     * @param user  使用者
     * @return 能不能放
     */
    @Override
    public boolean canUse(Fight fight, LivingThing user) {
        return user instanceof MortenaxBlade blade && blade.isInfiniteFury() && super.canUse(fight, user);
    }

    /**
     * 带目标列表的门槛（AI 与 {@code Skill#use(...)} 走的是三参版本）。
     *
     * @param fight   当前战斗
     * @param user    使用者
     * @param targets 目标
     * @return 能不能放
     */
    @Override
    public boolean canUse(Fight fight, LivingThing user, List<LivingThing> targets) {
        return canUse(fight, user) && super.canUse(fight, user, targets);
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        for (LivingThing enemy : enemies) {
            user.makeDamage(enemy, this);
            enemy.addEffect(new Taunt(TAUNT_LAST_TIME).setOrigin(user.getUUID()));
        }
        if (user instanceof MortenaxBlade blade) {
            blade.gainEnergy(MortalBladeCommonAttack.ENERGY_GAIN);
        }
    }
}
