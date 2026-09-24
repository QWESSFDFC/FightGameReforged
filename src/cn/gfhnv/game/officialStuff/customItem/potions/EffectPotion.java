package cn.gfhnv.game.officialStuff.customItem.potions;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.system.fight.Fight;

/**
 * 效果药水的基类：使用后给自己挂一个效果。
 * <p>
 * 子类只需要做三件事：
 * <ol>
 *     <li>构造器里写名称、描述、注册 id（注册时会自动加 {@code game_official_content:} 前缀）；</li>
 *     <li>实现 {@link #createEffect()} 返回要施加的效果；</li>
 *     <li>{@link #copy()} 返回 {@code new Xxx(this)} —— <b>必须走拷贝构造器</b>，
 *     它会把 id 和堆叠数一起复制；用 {@code new Xxx()} 的话副本会掉回短 id，
 *     既没法被 {@code /give} 的完整 id 认出，也没法和背包里的同种药水叠在一格。</li>
 * </ol>
 * <p>
 * 效果会带上 {@code origin = 本物品的 id}：这样「同一种药水的效果」在
 * {@link Effect#equals(Object)} 看来是同一条，喝第二瓶会延长/刷新而不是叠成两条。
 *
 * @author AI（DeepSeek）生成
 */
public abstract class EffectPotion extends Item {

    /**
     * 构造一瓶药水。
     *
     * @param name        物品名称
     * @param description 物品描述
     * @param id          注册 id（不带模组前缀）
     */
    protected EffectPotion(String name, String description, String id) {
        super(name, description, id);
    }

    /**
     * 复制构造器。
     *
     * @param other 被复制的药水
     */
    protected EffectPotion(EffectPotion other) {
        super(other);
    }

    /**
     * 创建本药水要施加的效果。
     * <p>
     * 每次使用都会新建一个实例（效果挂在身上之后是独立的，不能被两瓶药水共用）。
     *
     * @return 要施加的效果；返回 {@code null} 表示这瓶药水不产生效果
     */
    protected abstract Effect createEffect();

    /**
     * 使用药水：把 {@link #createEffect()} 的效果挂到自己身上。
     *
     * @param user  使用者
     * @param fight 当前战斗（本类不需要，但接口要求）
     */
    @Override
    public void comeToEffect(LivingThing user, Fight fight) {
        if (user == null) {
            return;
        }
        Effect effect = createEffect();
        if (effect == null) {
            return;
        }
        effect.setOrigin(getId());
        user.addEffect(effect);
    }
}
