package cn.gfhnv.game.interfaces;

/**
 * 嘲讽标记：实现它的效果会让持有者成为「优先被打」的目标。
 * <p>
 * 框架只认这个接口，<b>不认识具体是哪个效果</b>
 * （官方内容里是 {@code cn.gfhnv.game.officialStuff.customEffect.universalEffects.Taunt}），
 * 所以模组可以写自己的嘲讽效果，只要实现本接口就会被目标策略认出来
 * （见 {@link cn.gfhnv.game.system.fight.TargetStrategies#tauntAware}）。
 *
 * @author AI（DeepSeek）生成
 */
public interface ITaunt {

    /**
     * @return 嘲讽优先级：越大越优先被打；同一生物身上有多个嘲讽时取最大值
     */
    int getTauntLevel();
}
