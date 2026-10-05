package cn.gfhnv.game.officialStuff.customEvent.phainonEvents;

import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.officialStuff.customEntity.players.Phainon;
import cn.gfhnv.game.system.fight.ActionSignal;
import cn.gfhnv.game.system.fight.TurnEntry;
import cn.gfhnv.game.system.fight.TurnManager;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class AwakeEndListener {
    /**
     * 这个监听器服务的那个白厄。
     * <p>
     * {@link AwakenEndEvent} 是<b>广播</b>事件：同场有两只白厄时，A 发的事件会把
     * <b>总线上所有</b> {@code AwakeEndListener} 都调用一遍。而本监听器是<b>一次性</b>的
     * （处理完就 {@link EventBus#unregister}），没有归属判断的话，A 的事件会顺手把
     * B 的监听器也"用掉" → B 的变身<b>永远结束不了</b>（技能表停在觉醒三条、
     * 免死无限武装、每挨一次打就重排一记【最后一击】）。
     * <p>
     * 所以 {@link #end(AwakenEndEvent)} 的<b>第一件事</b>就是认领：不是自己的事件直接
     * {@code return}，<b>连注销都不碰</b> —— 早退发生在注销之前，别人的事件就吃不掉
     * 这个监听器的注销资格，自我注销因此变得安全（同一次变身只会结束一次）。
     *
     * @author AI（DeepSeek）生成
     */
    private final Phainon owner;

    /**
     * @param owner 这个监听器服务的白厄（在 {@code UltimateAttack#comeToEffect} 变身时传入）
     */
    public AwakeEndListener(Phainon owner) {
        this.owner = owner;
    }

    @SubscribeEvent
    public void end(AwakenEndEvent endEvent) {
        // 认领：不是我的白厄就不是我的退出协议。
        // owner 为 null 时不拦（广播语义），注册了却忘了给归属的监听器仍然会被正常收掉，
        // 否则它会留在总线上当永久药渣。
        if (owner != null && endEvent.getPhainon() != owner) {
            return;
        }
        endEvent.getPhainon().setAwaken(false);
        endEvent.getPhainon().setPendingLastAttack(false);
        // 连同"这一刀是不是被打断的那一刀"一起清掉：留着会让下一次免死误用上一次的剩余回合数
        endEvent.getPhainon().setInterruptedRemainingExtraTurns(-1);
        endEvent.getPhainon().setName("白厄");
        endEvent.getPhainon().setAttackEnhancePercent(endEvent.getPhainon().getAttackEnhancePercent() - 0.8);
        endEvent.getPhainon().setHpEnhancePercent(endEvent.getPhainon().getHpEnhancePercent() - 2.7);
        endEvent.getPhainon().setHp((long) (endEvent.getPhainon().getHp() + endEvent.getPhainon().getHpMax() * 0.25));
        endEvent.getPhainon().getController().setActionSignal(ActionSignal.NORMAL);
        endEvent.getPhainon().setAbsorbDamage(false);
        endEvent.getPhainon().getController().setSkills(endEvent.getPhainon().getSkills());
        endEvent.getPhainon().setShowSpecialMes(user -> {
            if (user instanceof Phainon phainon) {
                if (phainon.isAwaken())
                    System.out.println("毁伤数量" + phainon.getScourge() + "|||剩余额外回合数" + phainon.getExtraTurns());
                else System.out.println("当前火种数" + phainon.getCoreflame());
            }
        });

        TurnManager.getTurns().add(new TurnEntry(endEvent.getPhainon(), BigDecimal.valueOf(10000)
                .divide(BigDecimal.valueOf(endEvent.getPhainon().getSpeed()), 10, RoundingMode.HALF_UP), TurnManager.getPresentTime()));
        endEvent.getPhainon().setExtraTurns(0);
        endEvent.getPhainon().setExtraAbilityTier(Math.min(2, endEvent.getPhainon().getExtraAbilityTier() + 1));
        endEvent.getPhainon().setSoulscorch(0);
        System.out.println("白厄再次踏上轮回....");
        EventBus.unregister(this);
    }
}
