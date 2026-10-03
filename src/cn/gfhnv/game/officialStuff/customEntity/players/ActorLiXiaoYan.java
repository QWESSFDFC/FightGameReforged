package cn.gfhnv.game.officialStuff.customEntity.players;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entityController.PlayerController;
import cn.gfhnv.game.event.DamageEvent;
import cn.gfhnv.game.interfaces.IModifyDamage;
import cn.gfhnv.game.interfaces.IModifyIgnitionMax;
import cn.gfhnv.game.officialStuff.customEffect.actorLiXiaoYanEffects.MemorizedHp;
import cn.gfhnv.game.officialStuff.customSkill.actorLiXiaoYanSkills.CommonAttack;
import cn.gfhnv.game.officialStuff.customSkill.actorLiXiaoYanSkills.PyrohemicPumping;
import cn.gfhnv.game.officialStuff.customSkill.actorLiXiaoYanSkills.UltimateAttack;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.configLoadingSystem.DataKeys;
import cn.gfhnv.game.system.configLoadingSystem.GameRules;

import java.util.ArrayList;
import java.util.List;

/**
 * 【李晓焰】—— 火属性玩家角色：靠【燃点】层数滚伤害的自爆型法师。
 * <p>
 * 核心循环（<b>数值全在下面这组常量里，改平衡先动这里，别在技能里写魔数</b>）：
 * <ul>
 *     <li><b>【燃点】</b>：常规上限 {@value Defaults#IGNITION_MAX}，生命值低于 {@value Defaults#LOW_HP_THRESHOLD}
 *     时放宽到 {@value Defaults#LOW_HP_IGNITION_MAX}；每层提升 {@value Defaults#AREA_PER_IGNITION} 的单体倍率
 *     （在 {@link #updateSelf()} 里按增量结算）。三个技能都是"攒到上限为止"，
 *     不再像 2026-09 之前那样到 8 层就涨不动。</li>
 *     <li><b>高燃点加成</b>：达到 {@value Defaults#HIGH_IGNITION} 层后，三个技能都会附带
 *     {@value Defaults#HIGH_IGNITION_BONUS_RATE} × 生命上限 的额外伤害，判据统一用 {@link Defaults#HIGH_IGNITION}
 *     （以前三处各写死一个 8，容易改漏）。</li>
 *     <li><b>生命值锁定 + 免死</b>：大招把当前生命比例"铭记"（{@code MemorizedHp}），
 *     期间生命值不会低于该比例（见 {@link #damageModify}）；【燃点】达到 {@value Defaults#MEMORIZE_TRIGGER}
 *     层时再触发一次免死，回 {@value Defaults#MEMORIZE_HEAL_RATE} 最大生命并扣掉 {@value Defaults#MEMORIZE_COST} 层。</li>
 * </ul>
 *
 * @author AI（DeepSeek）生成
 */
public class ActorLiXiaoYan extends LivingThing {

    /**
     * 生效的【燃点】上限修正链（见 {@link IModifyIgnitionMax}）。
     * <p>
     * <b>为什么是静态的</b>：上限本身是一条"规则"（基准值来自 {@link Rule#ignitionMax()}），
     * 不是某个实例身上的状态；静态链让模组在 {@code invokeWhenLoaded()} 里登记一次就够，
     * 不必去 {@code World} 注册表里找模板，也不会漏掉副本与中途 {@code new} 出来的角色。
     * 修正器拿得到 {@code owner}，要按实例区分也能自己判断。
     * <p>
     * <b>不登记任何修正器时，{@link #effectiveIgnitionMax()} 返回的仍是基准值</b>——
     * 链为空等价于没有这个扩展点。
     */
    private static final List<IModifyIgnitionMax> IGNITION_MAX_MODIFIERS = new ArrayList<>();
    private int ignition = Rule.resetIgnition();
    private int ignitionMax = Rule.ignitionMax();
    private int lastIgnition = Rule.resetIgnition();
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
        this.setIndividualMultipleArea(1.0 + Rule.areaPerIgnition() * ignition);
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
                if (victim.getIgnition() >= Rule.memorizeTrigger() && correctedHp <= 0) {
                    correctedHp = (long) (victim.getHpMax() * Rule.memorizeHealRate());
                    victim.setIgnition(victim.getIgnition() - Rule.memorizeCost());
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

    /**
     * 登记一个【燃点】上限修正器（模组在 {@code invokeWhenLoaded()} 里调用）。
     * <p>
     * {@code null} 与 {@link IModifyIgnitionMax#DEFAULT} 会被忽略 —— 前者是调用方写错了，
     * 后者是"什么都不做"，登记进去只会让链变长。
     *
     * @param modifier 修正器
     */
    public static void addIgnitionMaxModifier(IModifyIgnitionMax modifier) {
        if (modifier == null || modifier == IModifyIgnitionMax.DEFAULT) {
            return;
        }
        IGNITION_MAX_MODIFIERS.add(modifier);
    }

    /**
     * 摘掉一个修正器（<b>按对象身份</b>比较，与 {@code LivingThing#removeModifyDamage} 同口径）。
     *
     * @param modifier 修正器
     * @return 有没有摘掉
     */
    public static boolean removeIgnitionMaxModifier(IModifyIgnitionMax modifier) {
        return modifier != null && IGNITION_MAX_MODIFIERS.removeIf(each -> each == modifier);
    }

    /**
     * @return 当前链上登记着的修正器（副本；改它不影响角色）
     */
    public static List<IModifyIgnitionMax> getIgnitionMaxModifiers() {
        return new ArrayList<>(IGNITION_MAX_MODIFIERS);
    }

    /**
     * 清空修正链（<b>只给自测用</b>：用例跑完必须让角色回到"链为空"的出厂状态）。
     */
    public static void clearIgnitionMaxModifiers() {
        IGNITION_MAX_MODIFIERS.clear();
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
        this.setIgnition(Rule.resetIgnition());
    }

    public int getIgnition() {
        return ignition;
    }

    /**
     * 设置【燃点】层数，自动夹在 {@code 0} 与当前上限之间。
     * <p>
     * <b>下限也必须夹</b>：免死会一次扣掉 {@value Defaults#MEMORIZE_COST} 层，旧代码只夹了上限，
     * 扣完能变成负数（实测出现过 −7，还会把单体倍率一起拉成负的）。
     *
     * @param ignition 目标层数
     */
    public void setIgnition(int ignition) {
        this.ignition = Math.max(0, Math.min(ignition, effectiveIgnitionMax()));
    }

    /* ------------------------------------------------------------------
     * 【燃点】上限修正链（模组的扩展点，见 IModifyIgnitionMax）
     * ------------------------------------------------------------------ */

    /**
     * @return 当前生效的【燃点】上限：生命值低于 {@value Defaults#LOW_HP_THRESHOLD} 时是
     * {@value Defaults#LOW_HP_IGNITION_MAX}，否则 {@value Defaults#IGNITION_MAX}；
     * 之后还要依次经过 {@link #getIgnitionMaxModifiers() 修正链}
     */
    public int effectiveIgnitionMax() {
        int base = getHp() < getHpMax() * Rule.lowHpThreshold() ? Rule.lowHpIgnitionMax() : ignitionMax;
        int current = base;
        for (IModifyIgnitionMax modifier : IGNITION_MAX_MODIFIERS) {
            current = modifier.modifyIgnitionMax(current, this);
        }
        return current;
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
                    + Rule.areaPerIgnition() * ignition - Rule.areaPerIgnition() * lastIgnition);
            lastIgnition = ignition;
        }
    }

    /**
     * 出厂值（<b>代码里的默认</b>）。
     * <p>
     * 它们只是"这份手感原来是多少"的记录，<b>不要拿它们做判断</b> ——
     * 真正生效的值一律走 {@link Rule} 里的读取方法（配置改了之后这里不会变）。
     * 这份"字面量只写一遍"的安排让生成器写出去的默认文件与代码里的默认值不可能不一致。
     *
     * @author AI（DeepSeek）生成
     */
    public static final class Defaults {

        /**
         * 【燃点】的常规上限。
         */
        public static final int IGNITION_MAX =
                cn.gfhnv.game.system.configLoadingSystem.RuleDefaults.LI_XIAO_YAN_IGNITION_MAX;
        /**
         * 生命值偏低时的【燃点】上限。
         */
        public static final int LOW_HP_IGNITION_MAX =
                cn.gfhnv.game.system.configLoadingSystem.RuleDefaults.LI_XIAO_YAN_LOW_HP_IGNITION_MAX;
        /**
         * 判定"生命值偏低"的比例。
         */
        public static final double LOW_HP_THRESHOLD =
                cn.gfhnv.game.system.configLoadingSystem.RuleDefaults.LI_XIAO_YAN_LOW_HP_THRESHOLD;
        /**
         * 开始吃"高燃点加成"的层数。
         */
        public static final int HIGH_IGNITION =
                cn.gfhnv.game.system.configLoadingSystem.RuleDefaults.LI_XIAO_YAN_HIGH_IGNITION;
        /**
         * 高燃点时每个技能额外追加的伤害（按最大生命的比例）。
         */
        public static final double HIGH_IGNITION_BONUS_RATE =
                cn.gfhnv.game.system.configLoadingSystem.RuleDefaults.LI_XIAO_YAN_HIGH_IGNITION_BONUS_RATE;
        /**
         * 每层【燃点】提供的单体倍率加成。
         */
        public static final double AREA_PER_IGNITION =
                cn.gfhnv.game.system.configLoadingSystem.RuleDefaults.LI_XIAO_YAN_AREA_PER_IGNITION;
        /**
         * 触发免死的层数。
         */
        public static final int MEMORIZE_TRIGGER =
                cn.gfhnv.game.system.configLoadingSystem.RuleDefaults.LI_XIAO_YAN_MEMORIZE_TRIGGER;
        /**
         * 触发免死时消耗的层数。
         */
        public static final int MEMORIZE_COST =
                cn.gfhnv.game.system.configLoadingSystem.RuleDefaults.LI_XIAO_YAN_MEMORIZE_COST;
        /**
         * 触发免死时回复的最大生命比例。
         */
        public static final double MEMORIZE_HEAL_RATE =
                cn.gfhnv.game.system.configLoadingSystem.RuleDefaults.LI_XIAO_YAN_MEMORIZE_HEAL_RATE;
        /**
         * 战斗结束时回到的层数。
         */
        public static final int RESET_IGNITION =
                cn.gfhnv.game.system.configLoadingSystem.RuleDefaults.LI_XIAO_YAN_RESET_IGNITION;

        /**
         * 工具类，不允许实例化。
         */
        private Defaults() {
        }
    }

    /**
     * <b>生效值</b>：每次都现读 {@code GameRules}（读不到就是 {@link Defaults} 里的出厂值）。
     * <p>
     * <b>为什么不写成 {@code public static final} 常量</b>：静态常量在"类第一次被加载"时取值，
     * 而类的加载时机不受配置文件控制（自测里甚至必然早于配置加载），
     * 那样会让用户改的 {@code actorLiXiaoYan.*} <b>静默失效</b>。
     * 这些方法都在回合级 / 开局级的路径上，不在伤害计算那种热路径里。
     * <p>
     * 配置键见 {@link DataKeys.Rule.ActorLiXiaoYan}。
     *
     * @author AI（DeepSeek）生成
     */
    public static final class Rule {

        /**
         * 工具类，不允许实例化。
         */
        private Rule() {
        }

        /**
         * @return 生效的【燃点】常规上限
         */
        public static int ignitionMax() {
            return GameRules.getInt(DataKeys.Rule.ActorLiXiaoYan.IGNITION_MAX);
        }

        /**
         * @return 生效的"低血时【燃点】上限"
         */
        public static int lowHpIgnitionMax() {
            return GameRules.getInt(DataKeys.Rule.ActorLiXiaoYan.LOW_HP_IGNITION_MAX);
        }

        /**
         * @return 生效的"低血"判定比例
         */
        public static double lowHpThreshold() {
            return GameRules.getDouble(DataKeys.Rule.ActorLiXiaoYan.LOW_HP_THRESHOLD);
        }

        /**
         * @return 生效的"开始吃高燃点加成"的层数
         */
        public static int highIgnition() {
            return GameRules.getInt(DataKeys.Rule.ActorLiXiaoYan.HIGH_IGNITION);
        }

        /**
         * @return 生效的高燃点额外伤害比例
         */
        public static double highIgnitionBonusRate() {
            return GameRules.getDouble(DataKeys.Rule.ActorLiXiaoYan.HIGH_IGNITION_BONUS_RATE);
        }

        /**
         * @return 生效的"每层【燃点】提供的单体倍率加成"
         */
        public static double areaPerIgnition() {
            return GameRules.getDouble(DataKeys.Rule.ActorLiXiaoYan.AREA_PER_IGNITION);
        }

        /**
         * @return 生效的免死触发层数
         */
        public static int memorizeTrigger() {
            return GameRules.getInt(DataKeys.Rule.ActorLiXiaoYan.MEMORIZE_TRIGGER);
        }

        /**
         * @return 生效的免死消耗层数
         */
        public static int memorizeCost() {
            return GameRules.getInt(DataKeys.Rule.ActorLiXiaoYan.MEMORIZE_COST);
        }

        /**
         * @return 生效的免死回复比例
         */
        public static double memorizeHealRate() {
            return GameRules.getDouble(DataKeys.Rule.ActorLiXiaoYan.MEMORIZE_HEAL_RATE);
        }

        /**
         * @return 生效的"战斗结束时回到几层"
         */
        public static int resetIgnition() {
            return GameRules.getInt(DataKeys.Rule.ActorLiXiaoYan.RESET_IGNITION);
        }
    }


}
