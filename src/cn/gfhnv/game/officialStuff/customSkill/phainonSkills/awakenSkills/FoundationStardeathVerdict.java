package cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEntity.players.Phainon;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.skill.SkillCoefficientTunable;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;

import java.util.*;

public class FoundationStardeathVerdict extends Skill implements SkillCoefficientTunable {

    /**
     * 具名系数：施法者回血比例（占自身生命上限）。
     */
    private static final String COEFF_HEAL_RATIO = "healRatio";
    /**
     * 具名系数：一次最多消耗几层【毁伤】。
     */
    private static final String COEFF_SCOURGE_COST_CAP = "scourgeCostCap";
    /**
     * 具名系数：每层【毁伤】打几段。
     */
    private static final String COEFF_HITS_PER_SCOURGE = "hitsPerScourge";
    /**
     * 具名系数：满层时那一记收尾的<b>总</b>倍率（按场上存活的敌人数量摊开）。
     */
    private static final String COEFF_FINISH_MAGNIFICATION = "finishMagnification";

    /**
     * 施法者回血比例（占自身生命上限）。
     */
    private double healRatio = 0.2;
    /**
     * 一次最多消耗几层【毁伤】。
     * <p>
     * 它同时决定"什么时候接那一记收尾"：消耗到的层数<b>等于</b>这个上限时才算"满层"。
     * 两处必须一起看，只改一处会让收尾那一击要么永远不打、要么提前打。
     */
    private int scourgeCostCap = 4;
    /**
     * 每层【毁伤】打几段。
     */
    private int hitsPerScourge = 6;
    /**
     * 满层时那一记收尾的总倍率（按场上存活的敌人数量摊开）。
     */
    private double finishMagnification = 6;

    public FoundationStardeathVerdict() {
        super("支柱-死星天裁", "解除自身所有负面效果，随后造成最多等同于卡厄斯兰那1170%攻击力的火属性伤害。", 0, 0.45, 0, -1);
        this.setCoolDown(0);
        this.getTags().put(TagType.ATTACK, new Tag(5));
    }

    /**
     * 复制构造器。
     * <p>
     * {@link #copy()} 必须走这里，不能返回 {@code new FoundationStardeathVerdict()}：
     * 那样会把倍率/冷却/权重还原成构造器里的出厂值，于是打在模板上的配置
     * （{@code SkillData.json}）<b>永远传不到选人时复制出去的副本</b>。
     *
     * @param other 被复制的技能
     */
    public FoundationStardeathVerdict(FoundationStardeathVerdict other) {
        super(other);
        this.healRatio = other.healRatio;
        this.scourgeCostCap = other.scourgeCostCap;
        this.hitsPerScourge = other.hitsPerScourge;
        this.finishMagnification = other.finishMagnification;
    }

    @Override
    public Map<String, Double> coefficientValues() {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put(COEFF_HEAL_RATIO, healRatio);
        values.put(COEFF_SCOURGE_COST_CAP, (double) scourgeCostCap);
        values.put(COEFF_HITS_PER_SCOURGE, (double) hitsPerScourge);
        values.put(COEFF_FINISH_MAGNIFICATION, finishMagnification);
        return values;
    }

    @Override
    public void setCoefficientValue(String name, double value) {
        switch (name) {
            case COEFF_HEAL_RATIO -> this.healRatio = value;
            case COEFF_SCOURGE_COST_CAP -> this.scourgeCostCap = (int) value;
            case COEFF_HITS_PER_SCOURGE -> this.hitsPerScourge = (int) value;
            case COEFF_FINISH_MAGNIFICATION -> this.finishMagnification = value;
            default -> throw new IllegalArgumentException("支柱-死星天裁没有叫「" + name + "」的具名系数");
        }
    }

    @Override
    public boolean canUse(Fight fight, LivingThing user, List<LivingThing> enemies) {
        if (user instanceof Phainon phainon) {
            return phainon.getScourge() >= 1;
        }
        return false;
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        List<LivingThing> e = new ArrayList<>(enemies);
        user.setHp((long) (user.getHp() + user.getHpMax() * healRatio));
        ListIterator<Effect> listedIterator = user.getEntityEffectList().listIterator();
        while (listedIterator.hasNext()) {
            Effect effect = listedIterator.next();
            if (effect.isNegative()) {
                effect.whenLastTimeEnd(user);
                listedIterator.remove();
            }
        }
        if (user instanceof Phainon phainon) {
            int scourge = Math.min(phainon.getScourge(), scourgeCostCap);//消耗的数量
            phainon.setScourge(phainon.getScourge() - scourge);
            int attackTimes = hitsPerScourge * scourge;

            for (int i = 1; i <= attackTimes; i++) {
                e.removeIf(livingThing -> !livingThing.isAlive());
                if (e.isEmpty()) {
                    this.setAtkMagnification(0.45);
                    return;
                }
                Collections.shuffle(e);
                LivingThing livingThing = e.getFirst();
                user.makeDamage(livingThing, this);
            }
            if (scourge != scourgeCostCap) {
                this.setAtkMagnification(0.45);
                return;
            }
            // 收尾那一击只在"真的消耗满了"时打；上限被配成 0 或敌人列表为空时它没有意义
            // （摊开到 0 个目标会算出无穷倍率），所以直接跳过。
            if (e.isEmpty()) {
                this.setAtkMagnification(0.45);
                return;
            }
            this.setAtkMagnification(finishMagnification / e.size());
            for (LivingThing livingThing : e) {
                user.makeDamage(livingThing, this);
            }

        }
        this.setAtkMagnification(0.45);
    }

    @Override
    public Skill copy() {
        return new FoundationStardeathVerdict(this);
    }
}
