package cn.gfhnv.game.officialStuff.customEffect.flameReaverEffects;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.effect.EffectTags;
import cn.gfhnv.game.entity.LivingThing;

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
        long newHp = Math.max(1, thing.getHp() - damage);
        System.out.println("【侵蚀】侵蚀了" + thing.getName() + "造成" + (thing.getHp() - newHp) + "点伤害");
        thing.setHp(newHp);
    }
}
