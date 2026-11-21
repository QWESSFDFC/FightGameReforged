package cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills;

import cn.gfhnv.game.damage.DamageType;
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

    /**
     * 星魂 6「亘古长升，蚀火残阳」：本次攻击总伤害值转为真实伤害的比例。
     */
    public static final double E6_TRUE_DAMAGE_RATIO = 0.36;

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        List<LivingThing> e = new ArrayList<>(enemies);
        user.heal((long) (user.getHpMax() * healRatio));
        user.removeEffectsIf(effect -> {
            if (effect.isNegative()) {
                effect.whenLastTimeEnd(user);
                return true;
            }
            return false;
        });
        if (user instanceof Phainon phainon) {
            int scourge = Math.min(phainon.getScourge(), scourgeCostCap);//消耗的数量
            phainon.setScourge(phainon.getScourge() - scourge);
            int attackTimes = hitsPerScourge * scourge;
            // 星魂 2：单次施放消耗【毁伤】达到上限（4 点）时，给当前回合追加一条高优先级行动：
            // 让卡厄斯兰那「额外选一次技能」，但不额外触发效果结算（区别于完整容器的真回合）。
            if (phainon.getEidolon() >= 2 && scourge == scourgeCostCap) {
                cn.gfhnv.game.system.fight.TurnEntry present =
                        cn.gfhnv.game.eventListener.FightTurnPastListener.getPresentTurn();
                if (present != null) {
                    present.addAct(phainon, (f, u) -> {
                        // 这一条排在当前回合里，真正执行时战斗可能已经结束（最后一个敌人已倒）。
                        // 回合循环要到下一圈才清尸体/判胜负，所以这里必须自己看"双方还有没有活人"。
                        if (!cn.gfhnv.game.GameMain.isInFight()
                                || !cn.gfhnv.game.system.fight.FightState.canTakeAction(f, u)) {
                            return;
                        }
                        if (u.getController() != null) {
                            u.getController().act(f);
                        }
                    }, cn.gfhnv.game.system.fight.ActEntry.PRIORITY_EIDOLON_EXTRA);
                }
            }

            long totalApplied = 0;
            for (int i = 1; i <= attackTimes; i++) {
                e.removeIf(livingThing -> !livingThing.isAlive());
                if (e.isEmpty()) {
                    break;
                }
                Collections.shuffle(e);
                LivingThing livingThing = e.getFirst();
                totalApplied += appliedDamage(user, livingThing);
            }
            // 收尾那一击只在"真的消耗满了"时打；上限被配成 0 或敌人列表为空时它没有意义
            // （摊开到 0 个目标会算出无穷倍率），所以直接跳过。
            if (scourge == scourgeCostCap && !e.isEmpty()) {
                this.setAtkMagnification(finishMagnification / e.size());
                for (LivingThing livingThing : e) {
                    totalApplied += appliedDamage(user, livingThing);
                }
            }
            this.setAtkMagnification(0.45);
            // 星魂 6：攻击后对生命值最高的敌方目标追加 36% 真实伤害。
            // 放在倍率复位之后：这一击是固定值，不依赖当前技能的 atkMagnification。
            if (phainon.getEidolon() >= 6) {
                dealE6TrueDamage(fight, phainon, totalApplied);
            }
        }
        this.setAtkMagnification(0.45);
    }

    /**
     * 打一刀并把"实际掉了多少血"算进本次施放的总伤害。
     * <p>
     * 用 HP 前后差而不是 {@code Damage#getCalculateDamageResult()}：星魂 6 的文案是
     * "本次攻击总伤害值"，本项目对"实际造成"的口径就是 {@code appliedDamage}（会被生命值夹取）。
     *
     * @param user   施法者
     * @param target 目标
     * @return 实际掉血量（≥ 0）
     */
    private long appliedDamage(LivingThing user, LivingThing target) {
        // makeDamage 返回这一刀的 Damage；appliedDamage 就是"实际掉血"（已夹生命值/免死/转阶段）。
        return Math.max(0, user.makeDamage(target, this, DamageType.UltimateAttack).getAppliedDamage());
    }

    /**
     * 星魂 6：对当前存活、生命值最高的敌方目标追加一次固定原始值的真实伤害。
     * <p>
     * 数值 = 本次施放总伤害 × 36%，作为<b>原始值</b>走 UNIVERSAL 公式：
     * 免增伤/抗性，吃防御/减伤/易伤，且不暴击（与项目对真实伤害的边界一致）。
     * 没有伤害 / 没有存活目标 / 6 魂未解锁时不做事。
     *
     * @param fight      当前战斗
     * @param phainon    施法者
     * @param totalDamage 本次施放累计的实际伤害
     */
    private void dealE6TrueDamage(Fight fight, Phainon phainon, long totalDamage) {
        if (totalDamage <= 0) {
            return;
        }
        LivingThing target = null;
        for (LivingThing candidate : fight.getOpponentList(phainon)) {
            if (candidate == null || !candidate.isAlive()) {
                continue;
            }
            if (target == null || candidate.getHp() > target.getHp()) {
                target = candidate;
            }
        }
        if (target == null) {
            return;
        }
        long amount = Math.round(totalDamage * E6_TRUE_DAMAGE_RATIO);
        if (amount <= 0) {
            return;
        }
        phainon.makeTrueDamage(target, this, amount, DamageType.SystemDamage);
    }

    @Override
    public Skill copy() {
        return new FoundationStardeathVerdict(this);
    }
}
