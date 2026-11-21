package cn.gfhnv.game.effect;

import cn.gfhnv.game.entity.LivingThing;

/**
 * <b>【能量溢出】记录效果</b>（2026-11-21 第 85 批，作者要求）。
 * <p>
 * 千冶·刃的能量超过正常上限后，溢出的那部分不再直接存在实体字段里，而是由本效果记录：
 * 效果等级 = 当前溢出点数，并且是<b>无限持续</b>（只在能量降回上限以内时被
 * {@code HSRActor} 主动摘掉）。
 * <p>
 * <b>它为什么需要是一个效果</b>：溢出能量在游戏里本来就是一个可见状态，
 * 挂在 {@code effects} 列表里之后，{@code /data} 与回合行都能看到"现在溢出多少"，
 * 不需要再额外加一个只能读的实体字段。
 * <p>
 * ⚠️ 本效果只由 {@link cn.gfhnv.game.entity.HSRActor} 自己挂/摘/改等级，
 * 不要用 {@code /effect} 或技能再挂一个同名效果 —— 两个同名效果会让
 * {@code getEnergyOverflow()} 读到哪一条变得不确定。
 *
 * @author AI（DeepSeek）生成
 */
public class EnergyOverflowEffect extends Effect {

    /**
     * 效果 id（{@code /data} 的 {@code effects[].id} 里看到的就是它）。
     */
    public static final String ID = "energyOverflow";

    /**
     * 当前溢出点数（0 = 没有溢出；有溢出时至少 1）。
     */
    private int overflow;

    /**
     * @param overflow 溢出点数（{@code < 0} 按 0 算）
     */
    public EnergyOverflowEffect(int overflow) {
        super(ID, Math.max(1, overflow), 0);
        this.overflow = Math.max(0, overflow);
        this.getEffectTagsList().add(EffectTags.INFINITE);
        this.getEffectTagsList().add(EffectTags.POSITIVE);
        this.setName("能量溢出");
    }

    /**
     * 复制构造器：等级与溢出点数一起带走。
     *
     * @param other 被复制的效果
     */
    public EnergyOverflowEffect(EnergyOverflowEffect other) {
        super(ID, Math.max(1, other.overflow), 0);
        this.overflow = other.overflow;
        this.getEffectTagsList().add(EffectTags.INFINITE);
        this.getEffectTagsList().add(EffectTags.POSITIVE);
        this.setName("能量溢出");
    }

    /**
     * @return 当前溢出点数
     */
    public int getOverflow() {
        return overflow;
    }

    /**
     * 重设溢出点数。等级同步更新，使它和 {@code /data} 里看到的等级一致。
     *
     * @param overflow 溢出点数（{@code < 0} 按 0 算）
     */
    public void setOverflow(int overflow) {
        this.overflow = Math.max(0, overflow);
        // 效果等级从 1 起；溢出 0 时这个效果本来就会被 HSRActor 摘掉
        this.setLevel(Math.max(1, this.overflow));
    }

    @Override
    public EnergyOverflowEffect copy() {
        return new EnergyOverflowEffect(this);
    }

    /**
     * 纯记录效果：<b>没有每回合生效的内容</b>，也不打印任何东西。
     * <p>
     * 必须显式覆盖基类的占位实现 —— 否则每次效果 tick 都会打印
     * 「这里写效果具体内容........」，玩家会以为出了 bug。
     *
     * @param thing 持有者（本效果不读它）
     */
    @Override
    public void comeIntoEffect(LivingThing thing) {
        // 溢出点数由 HSRActor 在 setEnergy 时直接维护，这里什么都不做。
    }
}
