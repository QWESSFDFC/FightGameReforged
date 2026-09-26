package cn.gfhnv.game.officialStuff.customEntity.players;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entityController.PlayerController;
import cn.gfhnv.game.event.DamageEvent;
import cn.gfhnv.game.interfaces.IModifyDamage;
import cn.gfhnv.game.officialStuff.customEffect.actorLiXiaoYanEffects.MemorizedHp;
import cn.gfhnv.game.officialStuff.customSkill.actorLiXiaoYanSkills.CommonAttack;
import cn.gfhnv.game.officialStuff.customSkill.actorLiXiaoYanSkills.PyrohemicPumping;
import cn.gfhnv.game.officialStuff.customSkill.actorLiXiaoYanSkills.UltimateAttack;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;

import java.util.ArrayList;
import java.util.List;

/**
 * 【李晓焰】—— 火属性玩家角色：靠【燃点】层数滚伤害的自爆型法师。
 * <p>
 * 核心循环（<b>数值全在下面这组常量里，改平衡先动这里，别在技能里写魔数</b>）：
 * <ul>
 *     <li><b>【燃点】</b>：常规上限 {@value #IGNITION_MAX}，生命值低于 {@value #LOW_HP_THRESHOLD}
 *     时放宽到 {@value #LOW_HP_IGNITION_MAX}；每层提升 {@value #AREA_PER_IGNITION} 的单体倍率
 *     （在 {@link #updateSelf()} 里按增量结算）。三个技能都是"攒到上限为止"，
 *     不再像 2026-09 之前那样到 8 层就涨不动。</li>
 *     <li><b>高燃点加成</b>：达到 {@value #HIGH_IGNITION} 层后，三个技能都会附带
 *     {@value #HIGH_IGNITION_BONUS_RATE} × 生命上限 的额外伤害，判据统一用 {@link #HIGH_IGNITION}
 *     （以前三处各写死一个 8，容易改漏）。</li>
 *     <li><b>生命值锁定 + 免死</b>：大招把当前生命比例"铭记"（{@code MemorizedHp}），
 *     期间生命值不会低于该比例（见 {@link #damageModify}）；【燃点】达到 {@value #MEMORIZE_TRIGGER}
 *     层时再触发一次免死，回 {@value #MEMORIZE_HEAL_RATE} 最大生命并扣掉 {@value #MEMORIZE_COST} 层。</li>
 * </ul>
 *
 * @author AI（DeepSeek）生成
 */
public class ActorLiXiaoYan extends LivingThing {

    /**
     * 【燃点】的常规上限。
     */
    public static final int IGNITION_MAX = 10;

    /**
     * 生命值低于 {@link #LOW_HP_THRESHOLD} 时的【燃点】上限（血越少越能攒）。
     */
    public static final int LOW_HP_IGNITION_MAX = 15;

    /**
     * 生命值低于这个比例时，【燃点】上限放宽到 {@link #LOW_HP_IGNITION_MAX}。
     */
    public static final double LOW_HP_THRESHOLD = 0.5;

    /**
     * 达到这个层数后技能获得"高燃点加成"（攻击 / 战技 / 大招共用这一个判据）。
     * <p>
     * <b>注意与"攒层上限"的区别</b>：燃点可以一路攒到 {@link #IGNITION_MAX}（血少时
     * {@link #LOW_HP_IGNITION_MAX}），{@link #HIGH_IGNITION} 只是"从几层开始吃加成"。
     * 2026-09 之前三个技能写的是"涨到 8 层就涨一层扣一层"，于是普攻在 7 层永远涨不动，
     * 加成与免死几乎只能靠挨打触发 —— 已改成"攒到上限为止"。
     */
    public static final int HIGH_IGNITION = 8;

    /**
     * 高燃点（≥ {@link #HIGH_IGNITION} 层）时，每个技能额外追加的伤害：按最大生命的这个比例算。
     * <p>
     * 三个技能<b>共用</b>这一个数值 —— 以前各写一份 {@code 0.5}，改平衡时容易只改到一两个。
     */
    public static final double HIGH_IGNITION_BONUS_RATE = 0.6;

    /**
     * 每层【燃点】提供的单体倍率加成。
     */
    public static final double AREA_PER_IGNITION = 0.04;

    /**
     * 【燃点】达到这个层数时触发免死。
     */
    public static final int MEMORIZE_TRIGGER = 10;

    /**
     * 触发免死时消耗的【燃点】层数。
     */
    public static final int MEMORIZE_COST = 10;

    /**
     * 触发免死时回复的最大生命比例。
     */
    public static final double MEMORIZE_HEAL_RATE = 0.3;

    /**
     * 战斗结束（{@link #whenFightEnds()}）时回到的【燃点】层数。
     */
    public static final int RESET_IGNITION = 3;

    private int ignition = RESET_IGNITION;
    private int ignitionMax = IGNITION_MAX;
    private int lastIgnition = RESET_IGNITION;
    private double memorizedRate = -1;

    public ActorLiXiaoYan(ActorLiXiaoYan other) {
        super(other);
        this.ignition = other.ignition;
        this.ignitionMax = other.ignitionMax;
        this.lastIgnition = other.lastIgnition;
        this.memorizedRate = other.memorizedRate;
        this.setIndividualMultipleArea(other.getIndividualMultipleArea());

    }

    public ActorLiXiaoYan(long l) {
        // 防御成长 3 → 5（2026-09 加强）：原来 125 级只有 572 防，是三名角色里最低的
        super("李晓焰", "actorLiXiaoYan", 0.4, 0.0, 0.0, 0.0, 0.0, 120, l, "player", 58, 22, 5, ElementSort.FIRE);
        this.setMass(60);
        this.setDescription("这是李晓焰.");
        this.getInventory().addSlot(63);
        List<Skill> skills = new ArrayList<>();
        skills.add(new CommonAttack());
        skills.add(new PyrohemicPumping());
        skills.add(new UltimateAttack());
        this.setController(new PlayerController(skills, this));
        this.setIndividualMultipleArea(1.0 + AREA_PER_IGNITION * ignition);
        this.lastIgnition = ignition;
        this.setModifyDamage(new IModifyDamage() {
            @Override
            public long damageModify(long newHp, DamageEvent da) {
                if (!(da.getAttackedEntity() instanceof ActorLiXiaoYan victim)) {
                    return newHp;
                }
                long correctedHp = newHp;
                // ① 生命值锁定：生命值不低于"铭记"的那次比例
                if (victim.hasMemorizedHpEffect() && victim.getMemorizedRate() > 0) {
                    long minHp = (long) (victim.getHpMax() * victim.getMemorizedRate());
                    if (correctedHp < minHp) {
                        correctedHp = minHp;
                    }
                }
                // ② 免死：【燃点】攒够就免死一次，回一截血并扣掉 MEMORIZE_COST 层
                //
                // 这里必须读"正在挨打的那个实例"（victim）的燃点，不能读闭包里的 this.ignition：
                // 伤害修正器是**按引用**被复制到每个副本上的（LivingThing 的复制构造器走
                // damageModifiers.addAll），闭包捕获的永远是**模板实例**。旧写法在副本挨打时
                // 会去改模板的层数，并把副本自己的燃点算成"模板值 − 10"，实测出现过 −7。
                if (victim.getIgnition() >= MEMORIZE_TRIGGER && correctedHp <= 0) {
                    correctedHp = (long) (victim.getHpMax() * MEMORIZE_HEAL_RATE);
                    victim.setIgnition(victim.getIgnition() - MEMORIZE_COST);
                }
                return correctedHp;
            }
        });
        this.setShowSpecialMes(user -> {
            if (user instanceof ActorLiXiaoYan) {
                System.out.println("燃点层数:" + ((ActorLiXiaoYan) user).getIgnition() + "/上限:" + ((ActorLiXiaoYan) user).getIgnitionMax());
            }
        });
    }

    public int getLastIgnition() {
        return lastIgnition;
    }

    @Override
    public LivingThing copy() {
        return new ActorLiXiaoYan(this);
    }

    @Override
    public void whenFightEnds() {
        super.whenFightEnds();
        this.setIgnition(RESET_IGNITION);
    }

    public int getIgnition() {
        return ignition;
    }

    /**
     * 设置【燃点】层数，自动夹在 {@code 0} 与当前上限之间。
     * <p>
     * <b>下限也必须夹</b>：免死会一次扣掉 {@value #MEMORIZE_COST} 层，旧代码只夹了上限，
     * 扣完能变成负数（实测出现过 −7，还会把单体倍率一起拉成负的）。
     *
     * @param ignition 目标层数
     */
    public void setIgnition(int ignition) {
        this.ignition = Math.max(0, Math.min(ignition, effectiveIgnitionMax()));
    }

    /**
     * @return 当前生效的【燃点】上限：生命值低于 {@value #LOW_HP_THRESHOLD} 时是
     * {@value #LOW_HP_IGNITION_MAX}，否则 {@value #IGNITION_MAX}
     */
    public int effectiveIgnitionMax() {
        return getHp() < getHpMax() * LOW_HP_THRESHOLD ? LOW_HP_IGNITION_MAX : ignitionMax;
    }

    /**
     * @return 当前【燃点】上限（血少时更高，与 {@link #setIgnition(int)} 用的是同一个判据）
     */
    public int getIgnitionMax() {
        return effectiveIgnitionMax();
    }

    public double getMemorizedRate() {
        return memorizedRate;
    }

    public void setMemorizedRate(double memorizedRate) {
        this.memorizedRate = memorizedRate;
    }

    private boolean hasMemorizedHpEffect() {
        for (Effect e : getEntityEffectList()) {
            if (e instanceof MemorizedHp) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void updateSelf() {
        if (lastIgnition != ignition) {
            setIndividualMultipleArea(getIndividualMultipleArea()
                    + AREA_PER_IGNITION * ignition - AREA_PER_IGNITION * lastIgnition);
            lastIgnition = ignition;
        }
    }


}