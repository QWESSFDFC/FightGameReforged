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
    @SubscribeEvent
    public void end(AwakenEndEvent endEvent) {
        endEvent.getPhainon().setAwaken(false);
        endEvent.getPhainon().setPendingLastAttack(false);
        endEvent.getPhainon().setName("白厄");
        endEvent.getPhainon().setAttackEnhancePercent(endEvent.getPhainon().getAttackEnhancePercent()-0.8);
        endEvent.getPhainon().setHpEnhancePercent(endEvent.getPhainon().getHpEnhancePercent()-2.7);
        endEvent.getPhainon().setHp((long) (endEvent.getPhainon().getHp()+endEvent.getPhainon().getHpMax()*0.25));
        endEvent.getPhainon().getController().setActionSignal(ActionSignal.NORMAL);
        endEvent.getPhainon().setAbsorbDamage(false);
        endEvent.getPhainon().getController().setSkills(endEvent.getPhainon().getSkills());
        endEvent.getPhainon().setShowSpecialMes(user -> {
            if (user instanceof Phainon phainon) {
                if (phainon.isAwaken()) System.out.println("毁伤数量"+phainon.getScourge()+"|||剩余额外回合数"+phainon.getExtraTurns());
                else System.out.println("当前火种数"+phainon.getCoreflame());
            }
        });

        TurnManager.getTurns().add(new TurnEntry(endEvent.getPhainon(), BigDecimal.valueOf(10000)
                .divide(BigDecimal.valueOf(endEvent.getPhainon().getSpeed()), 10, RoundingMode.HALF_UP), TurnManager.getPresentTime()));
        endEvent.getPhainon().setExtraTurns(0);
        endEvent.getPhainon().setScourge(0);
        endEvent.getPhainon().setExtraAbilityTier(Math.min(2,endEvent.getPhainon().getExtraAbilityTier()+1));
        endEvent.getPhainon().setCoreflame(0);
        endEvent.getPhainon().setSoulscorch(0);
        System.out.println("白厄再次踏上轮回....");
        EventBus.unregister(this);
    }
}
