package cn.gfhnv.game.officialStuff.customEffect.flameReaverEffects;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.effect.EffectTags;
import cn.gfhnv.game.entity.LivingThing;

/**
 * 【苦痛缠绕】—— 盗火行者身上的一本"代价-回收"账本。
 * <p>
 * 官方：施放【亡死的黑云】或【将尽的命数】时<b>消耗生命值召唤【残破容器】</b>，
 * 并使<b>消耗的生命值转化为【苦痛缠绕】</b>；之后吸收容器时，
 * <b>回复转化为【苦痛缠绕】的生命值</b>。
 * <p>
 * 所以它是一笔"借出去的血"：
 * <ul>
 *     <li>{@link #add(LivingThing, long)}：召唤时按实际消耗叠加；</li>
 *     <li>{@link #take(LivingThing, long)}：吸收时按需取用（返回真正取到的量）。</li>
 * </ul>
 * <p>
 * <b>为什么做成效果</b>（与 {@link SacrificeRite} 同理）：
 * <ol>
 *     <li>账本挂在实体身上，而不是散在 BOSS 的一个字段里 —— 谁都能查、能显示、能被清除；</li>
 *     <li>{@code origin} 记录记账来源；</li>
 *     <li>它是<b>无限持续</b>（{@link EffectTags#INFINITE}）的机制性状态：
 *     {@code EffectEventListener} 遇到无限效果只调 {@code comeIntoEffect}、<b>不减 lastTime</b>，
 *     所以不会被回合数消耗掉。</li>
 * </ol>
 * 与其他机制性效果一样，<b>不标 {@code UNIVERSAL}</b>：{@code /effect} 命令不能施加它。
 *
 * @author AI（DeepSeek）生成
 */
public class PainEntanglement extends Effect {

    /**
     * 状态 id（运行时会被补成 {@code game_official_content:painEntanglement}）。
     */
    public static final String ID = "painEntanglement";

    /**
     * 构造【苦痛缠绕】。
     * <p>
     * {@code level} 承载"账本数值"。之所以不放新字段：{@link Effect} 的
     * {@code equals/hashCode} 只看 id / origin / 是否无限，所以 {@code level} 变化
     * 不会让它被当成"另一种效果"而叠加出第二份。
     */
    public PainEntanglement() {
        super(ID);
        this.setLevel(0);
        this.setLastTime(1);
        this.getEffectTagsList().add(EffectTags.INFINITE);
    }

    /**
     * 复制构造器。
     *
     * @param other 被复制的效果
     */
    public PainEntanglement(PainEntanglement other) {
        super(other.getID());
        this.setLevel(other.getLevel());
        this.setLastTime(other.getLastTime());
        if (other.isInfinity()) {
            this.getEffectTagsList().add(EffectTags.INFINITE);
        }
    }

    @Override
    public Effect copy() {
        return new PainEntanglement(this);
    }

    /**
     * @return 当前账本数值（还没回收的生命值）
     */
    public long getPain() {
        return getLevel();
    }

    /**
     * 覆盖账本数值。
     *
     * @param pain 账本数值
     */
    public void setPain(long pain) {
        this.setLevel((int) Math.max(0, Math.min(Integer.MAX_VALUE, pain)));
    }

    /**
     * 把实体身上的【苦痛缠绕】效果取出来（没有就返回 {@code null}）。
     *
     * @param thing 生物
     * @return 效果实例
     */
    public static PainEntanglement of(LivingThing thing) {
        if (thing == null || thing.getEntityEffectList() == null) {
            return null;
        }
        for (Effect effect : thing.getEntityEffectList()) {
            if (effect instanceof PainEntanglement pain) {
                return pain;
            }
        }
        return null;
    }

    /**
     * @return 实体当前的【苦痛缠绕】账本数值（没有该效果时为 0）
     */
    public static long amountOf(LivingThing thing) {
        PainEntanglement pain = of(thing);
        return pain == null ? 0 : pain.getPain();
    }

    /**
     * 记账：把 {@code amount} 加进账本。
     *
     * @param thing  生物
     * @param amount 增加量（≤0 时不做任何事）
     */
    public static void add(LivingThing thing, long amount) {
        if (thing == null || amount <= 0) {
            return;
        }
        PainEntanglement pain = of(thing);
        if (pain == null) {
            pain = new PainEntanglement();
            // origin 记持有者（就是记账的那个实体）的 UUID，与其他机制性效果保持一致。
            // 这里是直接进效果列表的（不走 addEffect，免得触发 initialEffect），所以 origin 要自己设。
            pain.setOrigin(thing.getUUID());
            thing.getEntityEffectList().add(pain);
        }
        pain.setPain(pain.getPain() + amount);
    }

    /**
     * 取用账本：尽量取出 {@code want}，返回<b>实际取到</b>的量。
     * <p>
     * 吸收容器时用它 —— 取多少就回多少血，账本不够就只回账本那么多。
     *
     * @param thing 生物
     * @param want  想取用的量
     * @return 实际取到的量（账本不够时小于 {@code want}）
     */
    public static long take(LivingThing thing, long want) {
        if (thing == null || want <= 0) {
            return 0;
        }
        PainEntanglement pain = of(thing);
        if (pain == null) {
            return 0;
        }
        long taken = Math.min(pain.getPain(), want);
        pain.setPain(pain.getPain() - taken);
        return taken;
    }

    /**
     * 每回合被框架调用（无限效果不会减 {@code lastTime}）。
     *
     * @param thing 持有者
     */
    @Override
    public void comeIntoEffect(LivingThing thing) {
        // 机制性账本：不需要每回合做事，也不打印，免得刷屏
    }
}
