package com.gfhnv.mods.drunkenSword;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.effect.EffectTags;
import cn.gfhnv.game.entity.LivingThing;

/**
 * 【醉意】—— 酒剑仙的核心资源，层数存在 {@link Effect#getLevel()} 里。
 * <p>
 * 它有<b>两个用处</b>：
 * <ol>
 *     <li>被技能读取，换算成伤害倍率与治疗量（战技 / 大招"倾泻醉意"）；</li>
 *     <li>每层提供 {@value #REDUCTION_PER_STACK} 的减伤（满 10 层 = 20%），
 *     在 {@link #comeIntoEffect(LivingThing)} 里同步到
 *     {@link LivingThing#addDamageReduction(Object, double)}。</li>
 * </ol>
 * 也就是说"醉"既是弹药也是护甲：攒着更抗打，打出去更疼但立刻变脆。
 * 这和盗火行者的【灾难之力】是同一个"资源驱动"套路，只是方向相反 ——
 * BOSS 是"吸收来的"，酒剑仙是"自己喝出来的"。
 * <p>
 * <b>为什么做成效果而不是实体上的一个 int 字段</b>：
 * 效果挂在身上可以被查看、被清除、被写进日志，也能被技能之外的系统（比如以后要做的驱散）碰到；
 * 而且不用给 {@code LivingThing} 加字段 —— 模组加不了字段，只能加内容。
 * <p>
 * <b>为什么数值放 {@code level} 而不是新字段</b>：
 * {@link Effect#equals(Object)} 只看 {@code id} / {@code origin} / 是否无限，
 * 所以改 {@code level} 不会让它被当成"另一种效果"而叠出第二份。
 * <p>
 * 它是一个<b>无限持续</b>（{@link EffectTags#INFINITE}）的机制性状态，
 * 因此不标 {@link EffectTags#UNIVERSAL} —— {@code /effect} 命令不能凭空施加它，
 * 想攒层数请用普攻或「桂花酿」。
 *
 * @author AI（DeepSeek）生成
 */
public class Drunkenness extends Effect {

    /**
     * 状态 id（进游戏时会被补成 {@code drunkenSword:drunkenness}）。
     */
    public static final String ID = "drunkenness";

    /**
     * 醉意层数上限。超过部分会被丢弃（喝多了也没用）。
     */
    public static final int MAX_STACKS = 10;

    /**
     * 每层【醉意】提供的减伤比例：满 10 层 = 20% 减伤。
     * <p>
     * 它是"酒壮怂人胆"那一半：攒层不只是为了打得更疼，也是为了挨得更少。
     * 减伤走 {@link LivingThing#addDamageReduction(Object, double)}，按<b>来源对象</b>覆盖，
     * 所以层数变化时重设一次即可，不会越叠越多；层数为 0 时该来源会被自动摘掉。
     */
    public static final double REDUCTION_PER_STACK = 0.02;

    /**
     * 【醉意】减伤在 {@link LivingThing#getDamageReductions()} 里的来源键。
     * <p>
     * 用<b>对象身份</b>区分来源（{@code LivingThing} 内部是 {@code ==} 比较），
     * 所以全项目共用一个静态实例就够了；{@code LivingThing} 的复制构造器会把
     * 这个引用一起带过去，副本身上的减伤仍然算同一个来源。
     */
    public static final Object DRUNKENNESS_DAMAGE_REDUCTION = new Object();

    /**
     * 构造 0 层的【醉意】。注册表里的模板用的就是它。
     */
    public Drunkenness() {
        super(ID);
        this.setLevel(0);
        this.setLastTime(1);
        this.getEffectTagsList().add(EffectTags.INFINITE);
        this.getEffectTagsList().add(EffectTags.POSITIVE);
    }

    /**
     * 复制构造器。{@link Effect} 的拷贝构造器只复制 id / level / lastTime，标签要自己补。
     *
     * @param other 被复制的效果
     */
    public Drunkenness(Drunkenness other) {
        super(other.getID());
        this.setLevel(other.getLevel());
        this.setLastTime(other.getLastTime());
        if (other.isInfinity()) {
            this.getEffectTagsList().add(EffectTags.INFINITE);
        }
        this.getEffectTagsList().add(EffectTags.POSITIVE);
    }

    @Override
    public Effect copy() {
        return new Drunkenness(this);
    }

    /**
     * @return 当前醉意层数
     */
    public int getStacks() {
        return getLevel();
    }

    /**
     * 覆盖醉意层数（自动夹在 0 与 {@link #MAX_STACKS} 之间）。
     *
     * @param stacks 新的层数
     */
    public void setStacks(int stacks) {
        this.setLevel(Math.max(0, Math.min(MAX_STACKS, stacks)));
    }

    /**
     * 取出实体身上的【醉意】效果（没有就返回 {@code null}）。
     *
     * @param thing 生物；可为 {@code null}
     * @return 效果实例
     */
    public static Drunkenness of(LivingThing thing) {
        if (thing == null || thing.getEntityEffectList() == null) {
            return null;
        }
        for (Effect effect : thing.getEntityEffectList()) {
            if (effect instanceof Drunkenness drunkenness) {
                return drunkenness;
            }
        }
        return null;
    }

    /**
     * @param thing 生物
     * @return 当前醉意层数（没有该效果时为 0）
     */
    public static int stacksOf(LivingThing thing) {
        Drunkenness drunkenness = of(thing);
        return drunkenness == null ? 0 : drunkenness.getStacks();
    }

    /**
     * @param thing 生物
     * @return 当前【醉意】提供的减伤比例（层数 × {@value #REDUCTION_PER_STACK}）
     */
    public static double reductionOf(LivingThing thing) {
        return stacksOf(thing) * REDUCTION_PER_STACK;
    }

    /**
     * 把减伤同步成"当前层数 × {@link #REDUCTION_PER_STACK}"。
     * <p>
     * {@link LivingThing#addDamageReduction(Object, double)} 是<b>按来源覆盖</b>的，
     * 所以这里随便调多少次都等价于"重设"，不会越叠越多；
     * 层数为 0 时传进去的是 0，那个来源会被自动摘掉。
     *
     * @param thing 生物；可为 {@code null}
     */
    private static void syncReduction(LivingThing thing) {
        if (thing == null) {
            return;
        }
        thing.addDamageReduction(DRUNKENNESS_DAMAGE_REDUCTION, reductionOf(thing));
    }

    /**
     * 加层数。身上还没有【醉意】时会顺手挂上（走 {@link LivingThing#addEffect(Effect)}，
     * 这样 id 会被补成注册表里的完整 id，和"/effect list"里看到的那条是同一条）。
     *
     * @param thing  生物
     * @param amount 增加量（≤0 时不做任何事）
     * @return 加完之后的层数
     */
    public static int add(LivingThing thing, int amount) {
        if (thing == null) {
            return 0;
        }
        Drunkenness drunkenness = of(thing);
        if (drunkenness == null) {
            drunkenness = new Drunkenness();
            // origin 记持有者自己的 UUID（醉意是自己喝出来的）：Effect#equals 用 id + origin 判等，
            // 于是同一个人再加层时认得出"还是同一条"，不会叠出第二份
            drunkenness.setOrigin(thing.getUUID());
            thing.addEffect(drunkenness);
            // addEffect 可能因为身上已经有一条等价效果而没把新实例加进去，这里重新取一次
            drunkenness = of(thing);
            if (drunkenness == null) {
                return 0;
            }
        }
        if (amount > 0) {
            drunkenness.setStacks(drunkenness.getStacks() + amount);
        }
        syncReduction(thing);
        return drunkenness.getStacks();
    }

    /**
     * 花光所有层数（技能"倾泻醉意"时用）。
     * <p>
     * 只把层数清成 0、<b>不摘掉效果本身</b>：留着一个 0 层的状态，
     * 界面与日志里都能看到"这个人现在没醉"，下次加层也不用重新创建。
     * 减伤会跟着归零（来源被摘掉），所以"把酒打光"同时也意味着"接下来更疼"。
     *
     * @param thing 生物
     * @return 被花掉的层数
     */
    public static int takeAll(LivingThing thing) {
        Drunkenness drunkenness = of(thing);
        if (drunkenness == null) {
            return 0;
        }
        int taken = drunkenness.getStacks();
        drunkenness.setStacks(0);
        syncReduction(thing);
        return taken;
    }

    /**
     * 每回合被框架调用（无限效果不会减 {@code lastTime}）。
     * <p>
     * 除了把减伤同步成当前层数之外什么都不做。这个同步是<b>幂等</b>的：
     * 万一有别的途径改了层数（{@code level} 是可以被直接 set 的），下一回合就会自己纠正回来。
     * <p>
     * <b>必须重写</b>：基类实现在这里打印
     * "这里写效果具体内容........请重写这个方法"。
     * <p>
     * 也<b>不要在这里加层数</b>：这个钩子每回合只在"持有者自己的回合"（含额外回合）触发一次，
     * 拿它当"每回合自动 +1 层"用，在额外回合里就会多算；层数应该由技能/物品显式改动，
     * 这样日志里才看得出来是谁加的。
     *
     * @param thing 持有者
     */
    @Override
    public void comeIntoEffect(LivingThing thing) {
        syncReduction(thing);
    }

    /**
     * 效果结束时的保险：把减伤来源摘掉。
     * <p>
     * 【醉意】是无限效果（{@link EffectTags#INFINITE}），正常玩法走不到这里
     * （无限效果只会被"战斗结束清效果"之类的途径整体清掉，而那时实体本身也快被丢弃了）。
     * 留着它是为了万一以后它变成有回合数的效果，不会留下一份摘不掉的减伤。
     *
     * @param thing 持有者
     */
    @Override
    public void whenLastTimeEnd(LivingThing thing) {
        thing.removeDamageReduction(DRUNKENNESS_DAMAGE_REDUCTION);
    }
}
