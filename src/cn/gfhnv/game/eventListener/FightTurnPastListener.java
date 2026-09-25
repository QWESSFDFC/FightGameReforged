package cn.gfhnv.game.eventListener;


import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EffectUpdateEvent;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.FightEndEvent;
import cn.gfhnv.game.event.FightPastOneTurnEvent;
import cn.gfhnv.game.interfaces.ISpecialAction;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.ActionSignal;
import cn.gfhnv.game.system.fight.TurnEntry;
import cn.gfhnv.game.system.fight.TurnManager;
import cn.gfhnv.game.world.World;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class FightTurnPastListener {
    private static List<LivingThing> theDeath = new ArrayList<>();
    private static TurnEntry presentTurn;

    /**
     * 是否正在由本监听器驱动回合循环。
     * <p>
     * 回合推进由 {@code turnLoop} 循环负责：本监听器只发一次 {@link FightPastOneTurnEvent}，
     * 之后每回合通过 {@code continue turnLoop} 回到循环开头，而不是再 post 一次事件。
     * 因此<b>栈深度不随回合数增长</b>（O(回合数) → O(1)），长战斗不会 StackOverflowError。
     * <p>
     * 若外部（例如模组）另外 post 了 {@link FightPastOneTurnEvent}，在驱动期间会被忽略：
     * 回合推进的责任在循环里，重入会打乱时间轴。
     */
    private boolean isDriving = false;

    public static TurnEntry getPresentTurn() {
        return presentTurn;
    }

    /**
     * @return 是否正在驱动回合循环
     */
    public boolean isDriving() {
        return isDriving;
    }

    /**
     * 设置是否正在驱动回合循环。
     * <p>
     * 供 {@link FightEndEventListener} 在战斗结束时叫停循环。
     * 战斗结束后 {@code isDriving} 必须复位为 {@code false}，
     * 否则下一场战斗的回合推进会被这里当作「重入」而全部忽略。
     *
     * @param driving 是否继续驱动
     */
    public void setDriving(boolean driving) {
        this.isDriving = driving;
    }

    @SubscribeEvent
    public void fightTurnPastOne(FightPastOneTurnEvent fightPastOneTurnEvent) throws InterruptedException {
        if (isDriving) {
            // 已经在驱动循环里了。正常情况下不会走到这里（循环内部只发 EffectUpdateEvent /
            // FightEndEvent，不会再发 FightPastOneTurnEvent）；真的走到就说明有外部重入，
            // 忽略即可，否则会变成"一个回合被处理两次"。
            return;
        }
        isDriving = true;
        try {
            turnLoop:
            while (isDriving) {
                // 战斗可能已被 /endfight 之类的方式结束（GameMain.isInFight() 为 false、时间轴已清空），
                // 此时立即退出，不再推进回合。
                if (!cn.gfhnv.game.GameMain.isInFight()) {
                    break;
                }

                fightPastOneTurnEvent.getFight().getFighterList().removeIf(livingThing -> {
                    if (!livingThing.isAlive()) {
                        theDeath.add(livingThing);
                        return true;
                    }
                    return false;
                });
                fightPastOneTurnEvent.getFight().getEnemiesList().removeIf(livingThing -> {
                    if (!livingThing.isAlive()) {
                        theDeath.add(livingThing);
                        return true;
                    }
                    return false;
                });
                TurnManager.removeTheDeath();
                // 死亡离场结算：走 whenLeaveFight（单个生物离场），**不是** whenFightEnds()。
                // whenFightEnds() 是整场结束时的重置（含 setHp(getHpMax())），在这里调用会把
                // 死掉的生物复活 —— 而它已经被移出阵营列表，于是变成"不在任何阵营却能继续出手"
                // 的幽灵实体。
                //
                // 时机同样要紧：结算必须**紧跟**在"移出阵营列表"之后，不能拖到本回合末尾。
                // 召唤物离场时会按死因结算（盗火行者的容器会给击杀者发"额外回合 + 增伤"），
                // 而额外回合是排成"当前时间点 + needTime = 0"的条目（FlameReaver#grantExtraTurn）。
                // 拖到回合末尾的话，这一整个回合（例如 BOSS 的回合）会先跑完：奖励迟了一整个回合，
                // 受益人还可能已经倒下 —— 那条额外回合随即被 removeTheDeath 摘掉，等于没发。
                for (LivingThing dead : theDeath) {
                    dead.whenLeaveFight(fightPastOneTurnEvent.getFight());
                }
                theDeath.clear();
                if (fightPastOneTurnEvent.getFight().getFighterList().isEmpty()) {
                    EventBus.post(new FightEndEvent(false, fightPastOneTurnEvent.getFight()));
                    break;
                }
                if (fightPastOneTurnEvent.getFight().getEnemiesList().isEmpty()) {
                    EventBus.post(new FightEndEvent(true, fightPastOneTurnEvent.getFight()));
                    break;
                }
                if (TurnManager.getTurns().isEmpty()) {
                    // 兜底:两个阵营都还有人却排不出回合了,再走下去 getFirst() 会抛
                    // NoSuchElementException。宁可少打一个回合也不要崩。
                    break;
                }
                TurnManager.sort();
                presentTurn = TurnManager.getTurns().getFirst();
                TurnManager.getTurns().remove(presentTurn);
                TurnManager.setPresentTime(presentTurn.getNeedTime().add(presentTurn.getStartTime()));
                if (presentTurn.getLivingThing() == null) {
                    continue turnLoop;   // 没有行动者，回到循环开头取下一个回合
                }
                if (!presentTurn.getLivingThing().isAlive()) {
                    continue turnLoop;
                }
                for (LivingThing entity : fightPastOneTurnEvent.getFight().getAllEntities()) {
                    if (entity != null) {
                        entity.updateSelf();
                    }
                }
                for (Skill skill : presentTurn.getLivingThing().getController().getSkills()) {
                    skill.setNowCoolDown(Math.max(0, skill.getNowCoolDown() - 1));
                }
                presentTurn.getLivingThing().recoverManaEveryTurn();
                System.out.println();
                World.turnTimer++;
                System.out.println("现在是" + presentTurn.getLivingThing().getName() + "的回合");
                System.out.println("状态:HP:" + presentTurn.getLivingThing().getHp() + "/" + presentTurn.getLivingThing().getHpMax());
                System.out.println("能量");
                System.out.println("金" + presentTurn.getLivingThing().getMetalMana().getAmount() + "/" + presentTurn.getLivingThing().getMetalMana().getAmountMax());
                System.out.println("木" + presentTurn.getLivingThing().getWoodMana().getAmount() + "/" + presentTurn.getLivingThing().getWoodMana().getAmountMax());
                System.out.println("水" + presentTurn.getLivingThing().getWaterMana().getAmount() + "/" + presentTurn.getLivingThing().getWaterMana().getAmountMax());
                System.out.println("火" + presentTurn.getLivingThing().getFireMana().getAmount() + "/" + presentTurn.getLivingThing().getFireMana().getAmountMax());
                System.out.println("土" + presentTurn.getLivingThing().getDirtMana().getAmount() + "/" + presentTurn.getLivingThing().getDirtMana().getAmountMax());
                if (!presentTurn.getFirstExecuteList().isEmpty()) {
                    for (ISpecialAction iSpecialAction : presentTurn.getFirstExecuteList()) {
                        iSpecialAction.execute(fightPastOneTurnEvent.getFight(), presentTurn.getLivingThing());
                    }
                }
                if (!presentTurn.getLivingThing().isAlive()) presentTurn.setActionSignal(ActionSignal.SKIP);
                if (presentTurn.getLivingThing().getShowSpecialMes() != null) {
                    presentTurn.getLivingThing().getShowSpecialMes().show(presentTurn.getLivingThing());
                }

                if (presentTurn.getActionSignal().equals(ActionSignal.NORMAL)) {
                    presentTurn.getLivingThing().getController().act(fightPastOneTurnEvent.getFight());
                } else if (presentTurn.getActionSignal().equals(ActionSignal.SPECIAL_ACTION)) {
                    // 判空:回合条目里的 SPECIAL_ACTION 是创建时的快照,而 specialAction 可能已被效果
                    // (如 Frozen 到期)清掉,直接调用会 NPE 并让整局游戏崩掉
                    ISpecialAction specialAction = presentTurn.getLivingThing().getController().getSpecialAction();
                    if (specialAction != null) {
                        specialAction.execute(fightPastOneTurnEvent.getFight(), presentTurn.getLivingThing());
                    }
                }
                if (presentTurn.getActionSignal() != ActionSignal.WITHOUT_NEW_TURN && !presentTurn.getActionSignal().equals(ActionSignal.SKIP_WITHOUT_NEW_TURN)) {
                    TurnEntry turn = new TurnEntry(presentTurn.getLivingThing(), BigDecimal.valueOf(10000).divide(BigDecimal.valueOf(presentTurn.getLivingThing().getSpeed()), 10, RoundingMode.HALF_UP), TurnManager.getPresentTime());
                    TurnManager.getTurns().add(turn);
                } else if (presentTurn.getActionSignal().equals(ActionSignal.WITHOUT_NEW_TURN)) {
                    presentTurn.getLivingThing().getController().act(fightPastOneTurnEvent.getFight());
                }
                if (!presentTurn.getLastExecuteList().isEmpty()) {
                    for (ISpecialAction iSpecialAction : presentTurn.getLastExecuteList()) {
                        iSpecialAction.execute(fightPastOneTurnEvent.getFight(), presentTurn.getLivingThing());
                    }
                }

                EventBus.post(new EffectUpdateEvent(presentTurn.getLivingThing(), presentTurn));
                // 本回合处理完毕，回到 turnLoop 开头取下一个回合（不再递归 post 事件）
            }
        } finally {
            // 必须复位：否则一次异常就会让这个监听器实例此后永远拒绝驱动回合
            isDriving = false;
        }
    }
}
