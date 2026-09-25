package com.gfhnv.mods.drunkenSword;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.effect.EffectTags;
import cn.gfhnv.game.entity.LivingThing;

/**
 * 【宿醉】—— 酒剑仙放完大招之后的代价：防御力下降若干个回合。
 * <p>
 * 写法与官方 {@code DefenseEnhanceEffect} 同源，就三件事：
 * <ol>
 *     <li>{@link #comeIntoEffect(LivingThing)} 里把 {@code defenceEnhancePercent} 加上一个负的百分比
 *     （用 {@code isOn} 保证只加一次 —— 这个钩子每回合都会被调用）；</li>
 *     <li>{@link #whenLastTimeEnd(LivingThing)} 里把同一个值<b>对称地减回去</b>；</li>
 *     <li>要<b>先加后减必须是同一个数</b>，所以百分比存成常量、不随回合变化。</li>
 * </ol>
 * <p>
 * 已知边界（官方效果同样如此）：用 {@code /effect remove} 或"清空效果"这类手段把它强行摘掉时，
 * 框架不会调 {@link #whenLastTimeEnd(LivingThing)}，那份减益就会永久留在身上。
 * 正常玩法里它只靠回合数自然结束，碰不到这条。
 *
 * @author AI（DeepSeek）生成
 */
public class Hangover extends Effect {

    /**
     * 状态 id（进游戏时会被补成 {@code drunkenSword:hangover}）。
     */
    public static final String ID = "hangover";

    /**
     * 防御变化百分比：{@code -0.3} 表示防御力 ×0.7。
     * <p>
     * 防御力最终值按 {@code 基础防御 × (1 + 百分比) + 固定值} 计算，
     * 所以这里取负数是安全的（不会把防御压成负数）。
     */
    public static final double DEFENCE_PERCENT = -0.3;

    /**
     * 默认持续回合数。
     */
    public static final int DEFAULT_LAST_TIME = 2;

    /**
     * 记录"这份减益是否已经加到属性上"，防止重复施加。
     */
    private boolean isOn = false;

    /**
     * 构造一个默认持续 {@value #DEFAULT_LAST_TIME} 回合的【宿醉】。注册表里的模板用的就是它。
     */
    public Hangover() {
        this(DEFAULT_LAST_TIME);
    }

    /**
     * 构造一个指定持续回合数的【宿醉】。
     *
     * @param lastTime 持续回合数
     */
    public Hangover(int lastTime) {
        super(ID);
        this.setLastTime(lastTime);
        this.getEffectTagsList().add(EffectTags.NEGATIVE);
    }

    /**
     * 复制构造器。
     *
     * @param other 被复制的效果
     */
    public Hangover(Hangover other) {
        super(other.getID());
        this.setLevel(other.getLevel());
        this.setLastTime(other.getLastTime());
        this.isOn = other.isOn;
        this.getEffectTagsList().add(EffectTags.NEGATIVE);
    }

    @Override
    public Effect copy() {
        return new Hangover(this);
    }

    /**
     * 获得效果时（以及之后每回合）把减益挂到属性上，只挂一次。
     *
     * @param thing 持有者
     */
    @Override
    public void comeIntoEffect(LivingThing thing) {
        if (!isOn) {
            thing.setDefenceEnhancePercent(thing.getDefenceEnhancePercent() + DEFENCE_PERCENT);
            isOn = true;
        }
    }

    /**
     * 效果结束时把减益原样减回去。
     *
     * @param thing 持有者
     */
    @Override
    public void whenLastTimeEnd(LivingThing thing) {
        thing.setDefenceEnhancePercent(thing.getDefenceEnhancePercent() - DEFENCE_PERCENT);
        isOn = false;
    }
}
