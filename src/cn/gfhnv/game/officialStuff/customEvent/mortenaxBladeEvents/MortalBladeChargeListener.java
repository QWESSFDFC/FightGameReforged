package cn.gfhnv.game.officialStuff.customEvent.mortenaxBladeEvents;

import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.SelectTargetEvent;
import cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade;

import java.util.List;

/**
 * 千冶·刃 · 「因果尽偿」前半段：<b>我方目标每次攻击敌方后，千冶·刃获得 1 点充能</b>。
 * <p>
 * 设计稿见 `TODO.md`（「因果尽偿」[辅助]：结界期间我方每次攻击敌方后使对应目标陷入【煞火缠身】
 * 并使千冶·刃获得 1 点充能；9 点换一次额外战技 —— 后半段那部分是 P1）。
 * <p>
 * <b>为什么带 {@code owner}</b>：与白厄那个监听器的教训一样 —— 一个共享监听器"处理事件里的所有目标"
 * 看着能用，但绑定关系是碰巧的（谁先注册谁的服务对象）。带 owner + 每实例各注册一个，
 * 任何一只被打断/离场都只影响自己（见 {@code 20-MISTAKES.md} 那条"把监听器只为自己的 owner 服务当前提"）。
 *
 * @author AI（DeepSeek）生成；设计稿作者：仓库作者
 */
public class MortalBladeChargeListener {

    /**
     * 「因果尽偿」触发一次回复的能量（设计稿英文版：27 点）。
     */
    public static final long ENERGY_PER_PAYOFF = 27;
    /**
     * 服务的那一只千冶·刃。
     */
    private final MortenaxBlade owner;

    /**
     * @param owner 服务的那一只千冶·刃
     */
    public MortalBladeChargeListener(MortenaxBlade owner) {
        this.owner = owner;
    }

    /**
     * 被"选中目标"事件触发：<b>我方</b>打<b>敌方</b>才给充能（敌方打我们不给）。
     *
     * @param event 选中目标事件
     */
    @SubscribeEvent
    public void listen(SelectTargetEvent event) {
        if (owner == null || event.getFight() == null) {
            return;
        }
        LivingThing selector = event.getSelector();
        List<LivingThing> targets = event.getTargets();
        if (selector == null || targets == null || targets.isEmpty()) {
            return;
        }
        // 判据统一走 Fight#isOurSide（回合头、攻击行、命令系统用的是同一个方法）
        boolean allyAttack = event.getFight().isOurSide(selector);
        boolean hitEnemy = event.getFight().isOurSide(owner) && !allyAttack
                ? false
                : targets.stream().anyMatch(target -> !event.getFight().isOurSide(target));
        if (!allyAttack || !hitEnemy) {
            return;
        }
        owner.gainCharge(1);
        // 兑现（扣阈值 / 回能 / 追打 / E1 延后倒计时）统一由 owner 做：这里只"请求"，
        // 它会排进当前回合的收尾队列，等这一回合结算完再打
        owner.requestPayoff(event.getFight());
    }
}
