package cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEntity.players.Phainon;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.skill.SkillCoefficientTunable;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AwakenCommonAttack extends Skill implements SkillCoefficientTunable {

    /**
     * 具名系数：施法者回血比例（占自身生命上限）。
     */
    private static final String COEFF_HEAL_RATIO = "healRatio";
    /**
     * 具名系数：一次获得几层【毁伤】。
     */
    private static final String COEFF_SCOURGE_GAIN = "scourgeGain";

    /**
     * 施法者回血比例（占自身生命上限）。
     */
    private double healRatio = 0.2;
    /**
     * 一次获得几层【毁伤】。
     */
    private int scourgeGain = 4;

    public AwakenCommonAttack() {
        super("普通攻击-创生-血棘渡亡", "最普通的攻击.无发动条件.获得2点【毁伤】", 0, 3, 0, 3);
        this.setCoolDown(0);
        this.getTags().put(TagType.ATTACK, new Tag(1));
    }

    public AwakenCommonAttack(AwakenCommonAttack commonAttack) {
        super(commonAttack);
        this.healRatio = commonAttack.healRatio;
        this.scourgeGain = commonAttack.scourgeGain;
    }

    @Override
    public Map<String, Double> coefficientValues() {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put(COEFF_HEAL_RATIO, healRatio);
        values.put(COEFF_SCOURGE_GAIN, (double) scourgeGain);
        return values;
    }

    @Override
    public void setCoefficientValue(String name, double value) {
        switch (name) {
            case COEFF_HEAL_RATIO -> this.healRatio = value;
            case COEFF_SCOURGE_GAIN -> this.scourgeGain = (int) value;
            default -> throw new IllegalArgumentException("普通攻击-创生-血棘渡亡没有叫「" + name + "」的具名系数");
        }
    }

    @Override
    public Skill copy() {
        return new AwakenCommonAttack(this);
    }


    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        user.setHp((long) (user.getHp() + user.getHpMax() * healRatio));
        for (LivingThing livingThing : enemies) {
            user.makeDamage(livingThing, this);
        }
        if (user instanceof Phainon) {
            ((Phainon) user).addScourge(scourgeGain);
        }
    }
}
