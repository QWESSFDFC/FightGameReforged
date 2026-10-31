package cn.gfhnv.game.officialStuff.customEvent.mortenaxBladeEvents;

import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.event.DamageEvent;
import cn.gfhnv.game.officialStuff.customEffect.mortenaxBladeEffects.BalefireBindEffect;
import cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade;

/**
 * 千冶·刃 · 「千锻魂」的<b>受击那一半</b>：结界期间挨打时，
 * <b>攻击者陷入【煞火缠身】</b>，并且千冶·刃<b>获得 1 点充能</b>。
 * <p>
 * 设计稿见 `TODO.md`（「千锻魂」：结界期间被敌方攻击的概率提高，自身受到的伤害降低 50%，
 * 受到的治疗量提高 50%，且受到攻击后使对应目标陷入【煞火缠身】并使千冶·刃获得 1 点充能）。
 * 减伤与治疗那两半在 {@code MortenaxBlade#beginInfiniteFury} 里加减，这里只管"挨打之后"。
 * <p>
 * <b>只在结界期间注册</b>（{@code beginInfiniteFury} 注册、{@code endInfiniteFury} 注销），
 * 这样"结界期间"这个条件不用在回调里判断，也不会在普通形态下白白参与派发。
 *
 * @author AI（DeepSeek）生成；设计稿作者：仓库作者
 */
public class MortalBladeFuryHitListener {

    /**
     * 服务的那一只千冶·刃。
     */
    private final MortenaxBlade owner;

    /**
     * @param owner 服务的那一只千冶·刃
     */
    public MortalBladeFuryHitListener(MortenaxBlade owner) {
        this.owner = owner;
    }

    /**
     * 被伤害事件触发：自己挨打 → 攻击者缠身 + 自己 +1 充能。
     *
     * @param event 伤害事件
     */
    @SubscribeEvent
    public void listen(DamageEvent event) {
        if (owner == null || event == null) {
            return;
        }
        if (event.getAttackedEntity() != owner) {
            return;
        }
        cn.gfhnv.game.entity.LivingThing attacker = event.getAttacker();
        if (attacker == null || attacker == owner) {
            return;
        }
        // 追打的伤害也会走到这里 —— 兑现过程中不再叠充能，否则可能自己触发自己（递归）
        if (owner.isPayoffBusy()) {
            return;
        }
        attacker.addEffect(new BalefireBindEffect(
                cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.FornaxExCorpore.BALEFIRE_LAST_TIME)
                .setOrigin(owner.getUUID()));
        // 「千锻魂」：受到攻击后获得 1 点充能
        //（「神若当殒」那条"受伤/扣血也给充能"是星魂 6，用户拍板不做）
        owner.gainCharge(1);
        owner.requestPayoff(null);
    }
}
