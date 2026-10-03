package cn.gfhnv.game.interfaces;

import cn.gfhnv.game.entity.LivingThing;

/**
 * 【燃点】上限修正器：角色每次读取【燃点】上限时，都会依次经过登记在
 * {@code ActorLiXiaoYan} 上的这一条链。
 * <p>
 * 伤害那边有一个同形的 {@link IModifyDamage}（{@code LivingThing} 的 {@code damageModifiers}），
 * 这里是它的"上限版"：<b>链为空时返回的就是角色自己的基准上限</b>，
 * 所以不登记任何修正器 = 与没有这个接口时的数值逐位相同。
 * <p>
 * <b>这是给模组用的扩展点</b>：模组在 {@code invokeWhenLoaded()} 里
 * {@code ActorLiXiaoYan.addIgnitionMaxModifier(...)} 登记一个，就能提高（或压低）李晓焰的燃点上限，
 * 不需要反射、也不改游戏里任何一行数值。
 * <p>
 * <b>必须是无状态的纯函数</b>：它每次读上限都会被调用（{@code getIgnitionMax} / {@code setIgnition}
 * 都走这条路），在里面改角色状态会变成"读一次改一次"。
 *
 * @author AI（DeepSeek）生成
 */
public interface IModifyIgnitionMax {

    /**
     * 什么都不做的修正器：未登记任何修正器时，链上等价于只有它一个。
     */
    IModifyIgnitionMax DEFAULT = (baseMax, owner) -> baseMax;

    /**
     * 修正【燃点】上限。<b>链式语义</b>：链上的每个修正器拿到的都是上一个修正器的返回值，
     * 第一个拿到的就是角色自己算出来的基准上限。
     *
     * @param baseMax 上一步的上限（第一个修正器拿到的是基准上限）
     * @param owner   正在读取上限的那个角色
     * @return 修正后的上限
     */
    int modifyIgnitionMax(int baseMax, LivingThing owner);
}
