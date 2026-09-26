package cn.gfhnv.game.officialStuff.customEffect.flameReaverEffects;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.effect.EffectTags;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.utils.ConsoleColor;

/**
 * 【侵蚀】—— 盗火行者的专属负面效果。
 * <p>
 * 官方定义：被【将尽的命数】击中的目标，<b>降低生命值的一部分转化为【侵蚀】</b>。
 * 本项目把它落成"每回合按<b>已损失生命值</b>的比例掉血"的持续伤害：
 * 血越少掉得越多，与官方"损失的生命值转化而来"的语义一致。
 * <p>
 * 掉血逻辑写在 {@link #comeIntoEffect(LivingThing)} 里：该方法由
 * {@code EffectEventListener} 在<b>持有者自己的回合结束时</b>调用一次，
 * 并把 {@code lastTime} 递减。
 * <p>
 * 这是<b>角色专属</b>效果（不标 {@link EffectTags#UNIVERSAL}），
 * 所以 {@code /effect} 命令不会把它施加给任意生物。
 *
 * @author AI（DeepSeek）生成
 */
public class Erosion extends Effect {

    /**
     * 每回合按"已损失生命值"扣除的比例。
     */
    private double rate = 0.1;

    /**
     * 用默认比例（10%）构造。
     *
     * @param lastTime 持续回合数
     */
    public Erosion(int lastTime) {
        super("erosion");
        this.setLastTime(lastTime);
        this.setNegative(true);
    }

    /**
     * 按指定比例构造。
     *
     * @param rate     每回合按已损失生命值扣除的比例
     * @param lastTime 持续回合数
     */
    public Erosion(double rate, int lastTime) {
        this(lastTime);
        this.rate = rate;
    }

    /**
     * 复制构造器。
     *
     * @param other 被复制的效果
     */
    public Erosion(Erosion other) {
        super(other.getID());
        this.setLastTime(other.getLastTime());
        this.setLevel(other.getLevel());
        this.rate = other.rate;
        this.setNegative(true);
    }

    @Override
    public Effect copy() {
        return new Erosion(this);
    }

    /**
     * @return 每回合按已损失生命值扣除的比例
     */
    public double getRate() {
        return rate;
    }

    /**
     * 设置扣除比例。
     *
     * @param rate 每回合按已损失生命值扣除的比例
     */
    public void setRate(double rate) {
        this.rate = rate;
    }

    /**
     * 取出目标身上的【侵蚀】（没有就返回 {@code null}）。
     *
     * @param target 生物；可为 {@code null}
     * @return 效果实例
     */
    public static Erosion of(LivingThing target) {
        if (target == null || target.getEntityEffectList() == null) {
            return null;
        }
        for (Effect effect : target.getEntityEffectList()) {
            if (effect instanceof Erosion erosion) {
                return erosion;
            }
        }
        return null;
    }

    /**
     * 给目标施加【侵蚀】—— <b>同一个目标身上永远只有一条</b>。
     * <p>
     * 官方【侵蚀】是"被【将尽的命数】击中后，损失的生命值转化为持续伤害"这<b>一条</b>状态，
     * 反复击中只会<b>刷新</b>它，不该并排堆好几条（堆了的话同一个回合会连跳好几次 —— 实测踩过：
     * BOSS 本体与每只共祭容器各叠一条，一回合跳 5 次）。
     * <p>
     * 所以这里<b>按目标合并，不看 origin 是谁</b>：镜像对局（两边都是盗火行者）、
     * 容器替 BOSS 出手、以后新增的来源，都只会刷新同一条。
     * 合并取"不亏"的那一边：<b>强度取较大者、剩余回合取较长者</b>
     * （被一次小伤害的侵蚀刷新时，不会把已经很强的那条冲淡）。
     *
     * @param target   被施加的目标
     * @param rate     本次的强度（按已损失生命值比例）
     * @param lastTime 本次的持续回合
     * @param origin   施加者 UUID（只作记录，不参与合并判定）
     * @return 目标身上那条【侵蚀】（合并后的实例）
     */
    public static Erosion applyTo(LivingThing target, double rate, int lastTime, String origin) {
        if (target == null) {
            return null;
        }
        Erosion existing = of(target);
        if (existing != null) {
            existing.setRate(Math.max(existing.getRate(), rate));
            existing.setLastTime(Math.max(existing.getLastTime(), lastTime));
            return existing;
        }
        Erosion fresh = new Erosion(rate, lastTime);
        fresh.setOrigin(origin);
        target.addEffect(fresh);
        // addEffect 会按【类】把 id 补成注册表里的完整 id，可能换掉实例，所以重新取一次
        Erosion applied = of(target);
        return applied == null ? fresh : applied;
    }

    /**
     * 每回合结算一次侵蚀伤害。
     * <p>
     * 伤害来源是"自身已损失的生命值"，所以不会直接把人打死：
     * 最少保留 1 点生命（否则侵蚀会变成无视一切减伤的处决）。
     *
     * @param thing 持有侵蚀的生物
     */
    @Override
    public void comeIntoEffect(LivingThing thing) {
        if (thing == null || !thing.isAlive()) {
            return;
        }
        long lost = Math.max(0, thing.getHpMax() - thing.getHp());
        if (lost <= 0) {
            return;
        }
        long damage = (long) (lost * rate);
        if (damage <= 0) {
            return;
        }
        long oldHp = thing.getHp();
        long newHp = Math.max(1, oldHp - damage);
        thing.setHp(newHp);
        // 与普通攻击行同一套格式：`【侵蚀】<目标>（阵营）  -实际掉血  → HP 当前/上限`
        // 掉血要按"实际值"打（被"至少留 1 点"夹住时，想扣的和真扣的不一样）
        System.out.println(ConsoleColor.magenta("【侵蚀】") + thing.getNameWithSide() + "  "
                + ConsoleColor.red("-" + (oldHp - newHp)) + "  "
                + ConsoleColor.dim("→ HP " + thing.getHp() + "/" + thing.getHpMax()));
    }
}
