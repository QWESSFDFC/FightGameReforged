package cn.gfhnv.game.officialStuff.customEffect.flameReaverEffects;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;

/**
 * 【共祭】—— 盗火行者给【残破容器】上的标记效果。
 * <p>
 * 官方：施放【相混的道途】使【残破容器】进入【共祭】状态，
 * 之后这些容器会与盗火行者<b>一同发起攻击</b>，随后<b>被其吸收</b>
 * （按【苦痛缠绕】回血 + 获得【灾难之力】）。
 * <p>
 * 做成效果而不是容器上的一个布尔字段，有三个好处：
 * <ol>
 *     <li><b>回合递减交给框架</b>：{@code EffectEventListener} 在每个回合结束时把
 *     {@code lastTime} 减 1、归零时调 {@code whenLastTimeEnd} 并移除 ——
 *     不用自己数回合（容器重写过 {@code updateSelf}，而那个钩子是"每回合对全场所有实体"
 *     调用的，数出来的回合数会偏快）。</li>
 *     <li><b>归属明确</b>：{@link #setOrigin(String)} 记录施加者的 UUID
 *     （这里就是盗火行者的 UUID），便于查"这是谁上的共祭"。</li>
 *     <li><b>可以被驱散/清除</b>：任何"净化负面效果"的手段都能把它拿掉 ——
 *     玩家因此多了一条对抗途径。</li>
 * </ol>
 * 这是<b>角色专属</b>效果（不标 {@code UNIVERSAL}），所以 {@code /effect} 命令不能施加它。
 * <p>
 * 它同时是<b>负面</b>效果：被标记者会被召唤者回收，对容器本身不是好事 ——
 * 于是它又会落进 {@link BrokenContainer} 的"抵抗控制类负面状态"检查里。
 * 如果以后发现共祭被自己的抗性挡掉，把那个检查改成只认真正的控制类 id 即可。
 *
 * @author AI（DeepSeek）生成
 */
public class SacrificeRite extends Effect {

    /**
     * 状态 id（运行时会被补成 {@code game_official_content:sacrificeRite}）。
     */
    public static final String ID = "sacrificeRite";

    /**
     * 默认维持回合数。
     * <p>
     * 为什么至少是这个数：盗火行者上完共祭后，要等到它<b>下一次行动</b>才会吸收，
     * 中间会经过「玩家回合 → 容器自己的回合 → BOSS 回合」。
     * 而且 {@code EffectEventListener} 是每个回合对所有实体都结算一次，
     * 所以维持 N 个回合 ≈ 撑过 N 个回合。给 3 才够撑到吸收那一步。
     */
    public static final int DEFAULT_LAST_TIME = 3;

    /**
     * 构造【共祭】效果。
     *
     * @param lastTime 维持回合数
     */
    public SacrificeRite(int lastTime) {
        super(ID);
        this.setLastTime(Math.max(1, lastTime));
        this.setNegative(true);
    }

    /**
     * 复制构造器。
     *
     * @param other 被复制的效果
     */
    public SacrificeRite(SacrificeRite other) {
        super(other.getID());
        this.setLastTime(other.getLastTime());
        this.setLevel(other.getLevel());
        this.setNegative(true);
    }

    @Override
    public Effect copy() {
        return new SacrificeRite(this);
    }

    /**
     * 每回合被框架调用（{@code EffectEventListener}）。这里只打印一次状态提示，不改数值。
     *
     * @param thing 持有共祭的容器
     */
    @Override
    public void comeIntoEffect(LivingThing thing) {
        if (thing != null) {
            System.out.println("【残破容器】共祭中（剩余 " + getLastTime() + " 回合）");
        }
    }
}
