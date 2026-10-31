package cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.Taunt;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.mana.Mana;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;

import java.util.List;

/**
 * 千冶·刃 · 普攻「残锋，掠尽」：单体，伤害 = 自身<b>生命上限的 50%</b>，命中后使目标嘲讽 1 回合。
 * <p>
 * 设计稿见 `TODO.md`（「残锋，掠尽」[单攻]）；机制映射与施工单见
 * `project_analyses/MORTENAX-BLADE-2026-10.md`。
 * <p>
 * <b>为什么是 hpMagnification 而不是 atkMagnification</b>：设计里"造成等同于千冶·刃 50% 生命上限的伤害"
 * 正好对应 {@link Skill#getHpMagnification()}（`DamageCalculate:93` 读它），改配置就能调，不用改代码。
 *
 * @author AI（DeepSeek）生成；设计稿作者：仓库作者
 */
public class MortalBladeCommonAttack extends Skill {

    /**
     * 这一招回多少能量（设计稿英文版：普攻 {@code Energy gain: 20}）。
     */
    public static final long ENERGY_GAIN = 20;
    /**
     * 嘲讽持续回合数（设计：1 回合）。
     */
    private static final int TAUNT_LAST_TIME = 1;

    public MortalBladeCommonAttack() {
        super("残锋，掠尽", "对指定敌方单体造成等同于千冶·刃50%生命上限的火属性伤害，并使目标陷入嘲讽状态，持续1回合。",
                0.5, 0, 0, 1);
        this.setCoolDown(0);
        // 普攻不耗法力（设计里没有消耗；蓝耗由 战技/终结技 承担）
        this.setConsumedMana(new Mana(0, ElementSort.FIRE));
        this.getTags().put(TagType.ATTACK, new Tag(3));
    }

    /**
     * 复制构造器。
     * <p>
     * {@link #copy()} 必须走这里：配置（{@code SkillData.json}）打在<b>模板</b>上，
     * 只有走复制构造器的副本才带着那些值；返回 {@code new MortalBladeCommonAttack()} 会把倍率还原成出厂值。
     *
     * @param other 被复制的技能
     */
    public MortalBladeCommonAttack(MortalBladeCommonAttack other) {
        super(other);
    }

    /**
     * 回能：千冶·刃自己的资源，别的实体用这个技能不回能（防御性写法）。
     *
     * @param user   使用者
     * @param amount 回多少
     */
    static void gainEnergy(LivingThing user, long amount) {
        if (user instanceof cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade blade) {
            blade.gainEnergy(amount);
        }
    }

    @Override
    public Skill copy() {
        return new MortalBladeCommonAttack(this);
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        for (LivingThing enemy : enemies) {
            user.makeDamage(enemy, this);
            // 嘲讽：让被打的目标优先打千冶·刃（判据在 TargetStrategies.tauntLevelFrom）
            enemy.addEffect(new Taunt(TAUNT_LAST_TIME).setOrigin(user.getUUID()));
        }
        gainEnergy(user, ENERGY_GAIN);
    }
}
