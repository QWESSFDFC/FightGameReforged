package cn.gfhnv.game.officialStuff.customEffect.flameReaverEffects;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;

/**
 * 【破容器之赏】—— 击杀【完整容器】者的增伤 buff。
 * <p>
 * 官方（末日幻影 3.4 融合进来的部分）：击破【完整容器】后，
 * <b>击杀者获得额外回合与增伤 buff</b>；若没被击杀，容器会被盗火行者吸收并额外充能。
 * <p>
 * 增伤走实体的 {@link LivingThing#setEnhance(double)}（伤害公式里的全属性增伤 {@code (1+enhance)}），
 * 与 {@code DamageEnhanceEffect} 同一条路 —— 到期时按同一个数值减回去，
 * 不会和别的增伤来源互相覆盖。
 *
 * @author AI（DeepSeek）生成
 */
public class ContainerReward extends Effect {

    /**
     * 状态 id（运行时会被补成 {@code game_official_content:containerReward}）。
     */
    public static final String ID = "containerReward";

    /**
     * 默认增伤比例。
     */
    public static final double DEFAULT_ENHANCE = 0.4;

    /**
     * 默认持续回合数。
     */
    public static final int DEFAULT_LAST_TIME = 3;

    /**
     * 这个效果加了多少增伤（到期时要按它减回去）。
     */
    private final double enhanceAmount;

    /**
     * 本效果是否已经把增伤加上了（防重复叠加 / 防重复打印）。
     */
    private boolean applied = false;

    /**
     * 构造【破容器之赏】。
     *
     * @param enhance  增伤比例
     * @param lastTime 持续回合数
     */
    public ContainerReward(double enhance, int lastTime) {
        super(ID);
        this.enhanceAmount = enhance;
        this.setLastTime(Math.max(1, lastTime));
    }

    /**
     * 复制构造器。
     *
     * @param other 被复制的效果
     */
    public ContainerReward(ContainerReward other) {
        super(other.getID());
        this.enhanceAmount = other.enhanceAmount;
        this.setLastTime(other.getLastTime());
        this.setLevel(other.getLevel());
        this.applied = other.applied;
    }

    @Override
    public Effect copy() {
        return new ContainerReward(this);
    }

    /**
     * @return 本效果提供的增伤比例
     */
    public double getEnhanceAmount() {
        return enhanceAmount;
    }

    /**
     * 把增伤加到持有者身上。
     * <p>
     * <b>必须重写这个方法，不能只写 {@code initialEffect}</b>：框架的
     * {@code EffectEventListener} 每个回合都会调用 {@code comeIntoEffect}，
     * 而基类里它是个会打印"请重写这个方法"的占位实现 —— 只重写 {@code initialEffect}
     * 会导致获得增伤之后<b>每回合都打印一次提示</b>（实测踩过）。
     * <p>
     * 因为基类的 {@code initialEffect} 默认就转发给 {@code comeIntoEffect}，
     * 所以 {@code addEffect} 那一刻也会走到这里；用 {@link #applied} 保证只加一次。
     *
     * @param thing 持有者
     */
    @Override
    public void comeIntoEffect(LivingThing thing) {
        if (thing == null || applied) {
            return;
        }
        applied = true;
        thing.setEnhance(thing.getEnhance() + enhanceAmount);
        System.out.println("【破容器之赏】" + thing.getName() + " 增伤 +"
                + Math.round(enhanceAmount * 100) + "%（" + getLastTime() + " 回合）");
    }

    /**
     * 到期时把增伤减回去。
     *
     * @param livingThing 效果结束的生物
     */
    @Override
    public void whenLastTimeEnd(LivingThing livingThing) {
        if (livingThing == null) {
            return;
        }
        livingThing.setEnhance(livingThing.getEnhance() - enhanceAmount);
        this.applied = false;
        System.out.println("【破容器之赏】" + livingThing.getName() + " 的增伤结束了");
    }
}
