package cn.gfhnv.game.officialStuff.customSkill.universalSkill;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.skill.NumericSkillTunable;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.mana.Mana;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;

import java.util.List;

public class RestorationHealthSkill extends Skill implements NumericSkillTunable {
    private int neededManaScale;

    public RestorationHealthSkill(double hpMagnification, double atkMagnification, double defMagnification, int aims, int neededManaScale) {
        super("生命值恢复", "恢复生命值.1冷却", hpMagnification, atkMagnification, defMagnification, aims);
        this.neededManaScale = neededManaScale;
        this.setForEnemies(false);
        this.getTags().put(TagType.HEAL, new Tag(1));
        this.setCoolDown(1);
    }

    public RestorationHealthSkill(RestorationHealthSkill restorationHealthSkill) {
        super(restorationHealthSkill);
        // 这个字段是"释放门槛"，与 Skill 基类那几个字段一样属于"技能数值"：
        // 不复制的话，选人时 copy() 出来的副本门槛永远是 0（等于没有门槛），
        // 而打在模板上的配置也就永远传不到副本。
        this.neededManaScale = restorationHealthSkill.getNeededManaScale();
    }

    @Override
    public Skill copy() {
        return new RestorationHealthSkill(this);
    }

    /**
     * @return 释放本技能所需的自身元素法力（由 {@link #canUse} 直接扣除）
     */
    public int getNeededManaScale() {
        return neededManaScale;
    }

    /**
     * 设置释放本技能所需的自身元素法力。
     *
     * @param neededManaScale 门槛值
     */
    public void setNeededManaScale(int neededManaScale) {
        this.neededManaScale = neededManaScale;
    }

    @Override
    public int getExtraNumericValue() {
        return neededManaScale;
    }

    @Override
    public void setExtraNumericValue(int value) {
        this.neededManaScale = value;
    }

    @Override
    public String extraNumericKey() {
        return "neededManaScale";
    }

    @Override
    public boolean canUse(Fight fight, LivingThing user, List<LivingThing> enemies) {
        for (Mana mana : user.getManas()) {
            if (mana.getElementSort().equals(user.getElementSort())) {
                if (mana.getAmount() >= this.neededManaScale) {
                    mana.setAmount(mana.getAmount() - neededManaScale);
                    return true;
                }
            }
        }
        System.out.println("能量不足");
        return false;
    }

    /**
     * 给每个目标回血并打一行日志。
     * <p>
     * 施法者与目标的名字都带阵营<b>与短标识</b>（见 {@link LivingThing#getNameWithUuidAndSide()}）：
     * 治疗对象可能是我方也可能被拿去奶对面，不带阵营看不出来；而同名实例（镜像对局里两边都是
     * "至黑之剑，盗火行者"）还得靠短标识才分得出是谁给谁回的血。格式与回合头、攻击行一致。
     *
     * @param fight   当前战斗上下文
     * @param user    施法者
     * @param enemies 目标列表
     */
    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        long restoredHP = (long) (user.getHp() * this.getHpMagnification() + user.getDefence() * this.getDefMagnification() + user.getAttack() * this.getAtkMagnification());
        for (LivingThing e : enemies) {
            e.setHp(e.getHp() + restoredHP);
            System.out.println(user.getNameWithUuidAndSide() + "为" + e.getNameWithUuidAndSide() + "恢复了" + restoredHP + "点生命值");
        }

    }
}
