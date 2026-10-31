package cn.gfhnv.game.officialStuff.customEffect.mortenaxBladeEffects;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.effect.EffectTags;
import cn.gfhnv.game.entity.LivingThing;

/**
 * 千冶·刃 · 【无量忿怒】状态：暴击率 +20%、暴击伤害 +60%（设计稿）。
 * <p>
 * <b>形状照抄 {@link cn.gfhnv.game.officialStuff.customEffect.universalEffects.CriticalDMGEnhanceEffect}</b>：
 * 进来加、到期减，`isOn` 防重复进出。区别只是它同时改**两样**（暴击率 + 暴击伤害），
 * 而且它是"结界期间"的载体 —— 免死、换技能表这些逻辑挂在别处（终结技与 {@code MortenaxBlade}），
 * 效果本身只管这两个数值。
 * <p>
 * ⚠️ 它**不负责**结界本身：结界由 {@code CountdownEntity}（倒计时）与
 * {@code MortenaxBlade#setInfiniteFury(boolean)}（状态位）一起表达。
 *
 * @author AI（DeepSeek）生成；设计稿作者：仓库作者
 */
public class InfiniteFuryEffect extends Effect {

    /**
     * 暴击率加成（设计：+20%）。
     */
    public static final double CRITICAL_RATE_BONUS = 0.2;

    /**
     * 暴击伤害加成（设计：+60%）。
     */
    public static final double CRITICAL_DAMAGE_BONUS = 0.6;

    /**
     * 是否已经"加过"（防重复进出把数值加两遍）。
     */
    private boolean isOn = false;

    /**
     * 构造一个持续 {@code lastTime} 回合的【无量忿怒】。
     * <p>
     * 传 {@code Integer.MAX_VALUE} 表示"不靠回合数结束"—— 结界的结束由倒计时与免死负责，
     * 那种场合用 {@@code whenLastTimeEnd} 之外的路径移除（见 {@code MortenaxBlade#endInfiniteFury()}）。
     *
     * @param lastTime 持续回合数
     */
    public InfiniteFuryEffect(int lastTime) {
        super("infiniteFuryEffect");
        this.getEffectTagsList().add(EffectTags.UNIVERSAL);
        this.getEffectTagsList().add(EffectTags.POSITIVE);
        this.setName("无量忿怒");
        this.setLastTime(lastTime);
    }

    /**
     * 复制构造器。
     *
     * @param other 被复制的效果
     */
    public InfiniteFuryEffect(InfiniteFuryEffect other) {
        super(other.getID());
        this.getEffectTagsList().add(EffectTags.UNIVERSAL);
        this.getEffectTagsList().add(EffectTags.POSITIVE);
        this.setName("无量忿怒");
        this.setLastTime(other.getLastTime());
        this.setLevel(other.getLevel());
        this.setOrigin(other.getOrigin());
        this.isOn = other.isOn;
    }

    @Override
    public Effect copy() {
        return new InfiniteFuryEffect(this);
    }

    @Override
    public void comeIntoEffect(LivingThing thing) {
        if (!isOn) {
            thing.setCriticalRateEnhancePercent(thing.getCriticalRateEnhancePercent() + CRITICAL_RATE_BONUS);
            thing.setCriticalDMGEnhancePercent(thing.getCriticalDMGEnhancePercent() + CRITICAL_DAMAGE_BONUS);
        }
        isOn = true;
    }

    @Override
    public void whenLastTimeEnd(LivingThing thing) {
        thing.setCriticalRateEnhancePercent(thing.getCriticalRateEnhancePercent() - CRITICAL_RATE_BONUS);
        thing.setCriticalDMGEnhancePercent(thing.getCriticalDMGEnhancePercent() - CRITICAL_DAMAGE_BONUS);
        isOn = false;
    }
}
