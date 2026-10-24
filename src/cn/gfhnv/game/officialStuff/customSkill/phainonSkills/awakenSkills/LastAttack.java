package cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.SpeedEnhanceEffect;
import cn.gfhnv.game.officialStuff.customEntity.players.Phainon;
import cn.gfhnv.game.officialStuff.customEvent.phainonEvents.AwakenEndEvent;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.skill.SkillCoefficientTunable;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.mana.Mana;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LastAttack extends Skill implements SkillCoefficientTunable {

    /**
     * 具名系数：每剩余一个额外回合，倍率乘上 {@code (1 − 这个值)}。
     */
    private static final String COEFF_PER_TURN = "lastAttackPerTurn";
    /**
     * 具名系数：被打断时最多按几个额外回合算（= 保底倍率的来源）。
     */
    private static final String COEFF_MAX_TURNS = "lastAttackMaxTurns";

    /**
     * 每剩余一个额外回合的倍率折扣（出厂 {@code 0.125} = 八分之一）。
     * <p>
     * <b>2026-10-24 起可外配</b>（用户拍板）：写进 {@code SkillData.json} 的
     * {@code lastAttackPerTurn}。改它等于改平衡。
     */
    private double lastAttackPerTurn = 0.125;

    /**
     * 倍率折扣最多按几个额外回合算（出厂 {@code 7}）。
     * <p>
     * 这是<b>用户拍定的下限</b>：{@code extraTurns = 8}（一个额外回合都没结算完就被打断）
     * 按原公式是 0 伤害，压成 7 之后"提前打断"保底 12.5%。**2026-10-24 起可外配**
     * （{@code SkillData.json} 的 {@code lastAttackMaxTurns}）。
     */
    private int lastAttackMaxTurns = 7;

    public LastAttack() {
        super("最后一击", "最后一击", 0, 13, 0, -1);
        this.setConsumedMana(new Mana(0, ElementSort.UNIVERSAL));
        this.setCoolDown(0);
        this.getTags().put(TagType.ATTACK, new Tag(1));
    }

    /**
     * 复制构造器。
     * <p>
     * {@link #copy()} 必须走这里，不能返回 {@code new LastAttack()}：那样会把倍率/冷却/消耗
     * 还原成构造器里的出厂值，于是打在模板上的配置（{@code SkillData.json}）
     * <b>永远传不到选人时复制出去的副本</b>。
     * <p>
     * ⚠️ <b>两个具名系数也要在这里手动带过去</b>：配置打在模板上、副本靠 {@code copy()}
     * 白送，漏复制就是"改了没反应"。
     *
     * @param other 被复制的技能
     */
    public LastAttack(LastAttack other) {
        super(other);
        this.lastAttackPerTurn = other.lastAttackPerTurn;
        this.lastAttackMaxTurns = other.lastAttackMaxTurns;
    }

    @Override
    public Map<String, Double> coefficientValues() {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put(COEFF_PER_TURN, lastAttackPerTurn);
        values.put(COEFF_MAX_TURNS, (double) lastAttackMaxTurns);
        return values;
    }

    @Override
    public void setCoefficientValue(String name, double value) {
        switch (name) {
            case COEFF_PER_TURN -> this.lastAttackPerTurn = value;
            case COEFF_MAX_TURNS -> this.lastAttackMaxTurns = Math.max(0, (int) value);
            default -> throw new IllegalArgumentException("最后一击没有叫「" + name + "」的具名系数");
        }
    }

    /**
     * 按"还剩几个额外回合"算出这一刀的攻击倍率。
     * <p>
     * <b>公式留在代码里、只外置两个系数</b>：{@code 基础倍率 × (1 − min(上限, 剩余) × 每回合折扣)}。
     * 抽成方法是为了<b>可测</b> —— 自测直接喂剩余回合数，不用真的打一局。
     *
     * @param remainingExtraTurns 还剩几个额外回合（{@code < 0} 按 0 算）
     * @return 这一刀的攻击倍率
     */
    public double magnificationFor(int remainingExtraTurns) {
        int used = Math.min(lastAttackMaxTurns, Math.max(0, remainingExtraTurns));
        return getAtkMagnification() * (1 - used * lastAttackPerTurn);
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        if (user instanceof Phainon phainon) {
            // 最后一击的倍率跟着「还剩多少额外回合」走：剩得越少打得越狠，走完 8 个回合时是满倍率 13。
            // 被打断的那一刀用「打断那一刻冻结下来的剩余回合数」：退出协议（AwakeEndListener#end）
            // 已经把它清零了，再读实时值会变成满倍率 13（见 Phainon#finalizeAwakenByInterrupt）。
            // 2026-10-24：两个系数（每回合折扣 / 上限 7）已外置成具名系数，公式与夹取都在
            // magnificationFor(...) 里 —— 这里不要再写死 7，否则外配的上限会被这一层盖掉。
            int remainingExtraTurns = phainon.getInterruptedRemainingExtraTurns() >= 0
                    ? phainon.getInterruptedRemainingExtraTurns()
                    : phainon.getExtraTurns();
            this.setAtkMagnification(magnificationFor(remainingExtraTurns));
        }

        for (LivingThing livingThing : enemies) {
            user.makeDamage(livingThing, this);

        }
        for (LivingThing livingThing : fight.getOpponentList(user)) {
            livingThing.addEffect(new SpeedEnhanceEffect(0.15, 1).setOrigin(user.getUUID()));
        }

        EventBus.post(new AwakenEndEvent((Phainon) user));
    }

    @Override
    public Skill copy() {
        return new LastAttack(this);
    }
}
