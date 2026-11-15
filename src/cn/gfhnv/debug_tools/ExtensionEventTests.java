package cn.gfhnv.debug_tools;

import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.event.*;
import cn.gfhnv.game.eventListener.FightTurnPastListener;
import cn.gfhnv.game.officialStuff.customEntity.monsters.CommonInsect;
import cn.gfhnv.game.officialStuff.customEntity.players.PlayerOne;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.command.CommandManager;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.fight.TurnManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static cn.gfhnv.debug_tools.TestKit.*;
import static cn.gfhnv.debug_tools.TestProbes.ProbeSkill;

/**
 * 自测：<b>扩展事件的发布者</b>（2026-11-15 第 74 批）。
 * <p>
 * 这一批只加"事件 + 发布者"，不写监听器 —— 所以这里用探针监听器逐条验证
 * "发布点真的会发"，而不是验证某个已有机制。覆盖：
 * <ul>
 *     <li>{@link SkillUseEvent}（含<b>可取消</b>语义）；</li>
 *     <li>{@link EntityHealEvent} / {@link ManaChangedEvent}；</li>
 *     <li>{@link EffectAppliedEvent} / {@link EffectRemovedEvent}；</li>
 *     <li>{@link FightTurnStartEvent} / {@link FightTurnEndEvent} / {@link TurnActionExecutedEvent}；</li>
 *     <li>{@link EntityDeathEvent}；</li>
 *     <li>{@link CommandExecutedEvent}。</li>
 * </ul>
 * 未覆盖：{@code GameInitializedEvent}（只在启动时发一次）、{@code RewardGrantedEvent}（要赢一场带奖励的战斗）——
 * 两者的发布点都已接上，见各自 javadoc。
 */
final class ExtensionEventTests {

    /**
     * 工具类。
     */
    private ExtensionEventTests() {
    }

    private static void testSkillUseEvent() {
        section("扩展事件：SkillUseEvent（含可取消）");
        try {
            PlayerOne user = new PlayerOne(125);
            CommonInsect enemy = new CommonInsect(125L);
            Fight fight = new Fight(new ArrayList<>(List.of(enemy)), new ArrayList<>(),
                    new ArrayList<>(List.of(user)));
            List<String> log = new ArrayList<>();
            Skill skill = new ProbeSkill("事件探针", log);
            Probe probe = new Probe();
            EventBus.register(probe);
            try {
                check("① 释放成功时会发 SkillUseEvent —— 实际 " + probe.skillUse,
                        skill.use(fight, user) && probe.skillUse == 1);

                probe.cancelSkill = true;
                List<String> logBefore = new ArrayList<>(log);
                boolean used = skill.use(fight, user);
                check("② 取消 SkillUseEvent ⇒ 技能不生效、use 返回 false —— 实际 used=" + used
                                + "，nowCoolDown=" + skill.getNowCoolDown(),
                        !used && skill.getNowCoolDown() == 0
                                && log.equals(logBefore) && probe.skillUse == 2);
            } finally {
                EventBus.unregister(probe);
            }
        } catch (Exception e) {
            fail("SkillUseEvent 测试抛出异常：" + e);
        }
    }

    private static void testHealAndManaEvents() {
        section("扩展事件：EntityHealEvent / ManaChangedEvent");
        try {
            PlayerOne user = new PlayerOne(125);
            user.setHp(user.getHpMax() / 2);
            Probe probe = new Probe();
            EventBus.register(probe);
            try {
                long healed = user.heal(100);
                check("① 治疗会发 EntityHealEvent（申请量/实际回血量都对）—— 实际 " + healed
                                + " / " + (probe.lastHeal == null ? "null" : probe.lastHeal.getHealed()),
                        probe.heal == 1 && probe.lastHeal != null
                                && probe.lastHeal.getRequested() == 100
                                && probe.lastHeal.getHealed() == healed);

                double before = user.getFireMana().getAmount();
                user.getFireMana().setAmount(before - 50);
                check("② 法力变化会发 ManaChangedEvent（元素 FIRE、前后值对）—— 实际 " + probe.manaChanged,
                        probe.manaChanged == 1 && probe.lastMana != null
                                && probe.lastMana.getElementSort() == cn.gfhnv.game.system.ElementSort.FIRE
                                && probe.lastMana.getBefore() == before
                                && probe.lastMana.getAfter() == before - 50);
            } finally {
                EventBus.unregister(probe);
            }
        } catch (Exception e) {
            fail("治疗/法力事件测试抛出异常：" + e);
        }
    }

    private static void testEffectEvents() {
        section("扩展事件：EffectAppliedEvent / EffectRemovedEvent");
        try {
            PlayerOne user = new PlayerOne(125);
            Effect effect = new ProbeEffect();
            effect.setLastTime(3);
            Probe probe = new Probe();
            EventBus.register(probe);
            try {
                user.addEffect(effect);
                user.removeEffect(effect);
                check("① 挂效果发 EffectAppliedEvent —— 实际 " + probe.effectApplied,
                        probe.effectApplied == 1);
                check("② 单条移除发 EffectRemovedEvent —— 实际 " + probe.effectRemoved
                                + "，还能读到效果 id " + (probe.lastRemoved == null ? "null"
                                : probe.lastRemoved.getEffect().getId()),
                        probe.effectRemoved == 1 && probe.lastRemoved != null
                                && probe.lastRemoved.getTarget() == user);
            } finally {
                EventBus.unregister(probe);
            }
        } catch (Exception e) {
            fail("效果事件测试抛出异常：" + e);
        }
    }

    private static void testTurnEventsAndDeath() {
        section("扩展事件：回合开始/结束/行动");
        boolean inFightBefore = cn.gfhnv.game.GameMain.isInFight();
        List<cn.gfhnv.game.system.fight.TurnEntry> turnsBefore = new ArrayList<>(TurnManager.getTurns());
        Probe probe = new Probe();
        FightTurnPastListener driver = new FightTurnPastListener();
        try {
            PlayerOne user = new PlayerOne(125);
            user.setName("事件探针");
            CommonInsect enemy = new CommonInsect(125L);
            Fight fight = new Fight(new ArrayList<>(List.of(enemy)), new ArrayList<>(),
                    new ArrayList<>(List.of(user)));

            TurnManager.setPresentTime(BigDecimal.ZERO);
            TurnManager.init(fight);

            EventBus.register(probe);
            EventBus.register(new cn.gfhnv.game.eventListener.FightEndEventListener(driver));
            cn.gfhnv.game.GameMain.setFightInProgressForSelfTest(true);
            EventBus.register(driver);
            EventBus.post(new FightPastOneTurnEvent(fight));

            check("① 回合开始会发 FightTurnStartEvent —— 实际 " + probe.turnStart, probe.turnStart >= 1);
            check("② 每条行动条目执行完发 TurnActionExecutedEvent —— 实际 " + probe.turnAction, probe.turnAction >= 3);
            check("③ 回合结束会发 FightTurnEndEvent —— 实际 " + probe.turnEnd, probe.turnEnd >= 1);
        } catch (Exception e) {
            fail("回合/死亡事件测试抛出异常：" + e);
        } finally {
            EventBus.unregister(probe);
            EventBus.unregister(driver);
            cn.gfhnv.game.GameMain.setFightInProgressForSelfTest(inFightBefore);
            TurnManager.setTurns(turnsBefore);
            TurnManager.setPresentTime(null);
            TurnManager.setInitialized(false);
            FightTurnPastListener.setPresentTurn(null);
        }
    }

    private static void testDeathEvent() {
        section("扩展事件：EntityDeathEvent");
        boolean inFightBefore = cn.gfhnv.game.GameMain.isInFight();
        List<cn.gfhnv.game.system.fight.TurnEntry> turnsBefore = new ArrayList<>(TurnManager.getTurns());
        Probe probe = new Probe();
        FightTurnPastListener driver = new FightTurnPastListener();
        try {
            PlayerOne user = new PlayerOne(125);
            CommonInsect enemy = new CommonInsect(125L);
            Fight fight = new Fight(new ArrayList<>(List.of(enemy)), new ArrayList<>(),
                    new ArrayList<>(List.of(user)));
            enemy.setHp(0);   // 一开始就是死的 → 回合循环第一件事就是死亡清扫

            TurnManager.setPresentTime(BigDecimal.ZERO);
            TurnManager.init(fight);

            EventBus.register(probe);
            EventBus.register(new cn.gfhnv.game.eventListener.FightEndEventListener(driver));
            cn.gfhnv.game.GameMain.setFightInProgressForSelfTest(true);
            EventBus.register(driver);
            EventBus.post(new FightPastOneTurnEvent(fight));

            check("① 死亡清扫发 EntityDeathEvent（带战斗与实体）—— 实际 " + probe.death
                            + "，" + (probe.lastDeath == null ? "null" : probe.lastDeath.getEntity().getName()),
                    probe.death == 1 && probe.lastDeath != null
                            && probe.lastDeath.getFight() == fight && !probe.lastDeath.getEntity().isAlive());
        } catch (Exception e) {
            fail("EntityDeathEvent 测试抛出异常：" + e);
        } finally {
            EventBus.unregister(probe);
            EventBus.unregister(driver);
            cn.gfhnv.game.GameMain.setFightInProgressForSelfTest(inFightBefore);
            TurnManager.setTurns(turnsBefore);
            TurnManager.setPresentTime(null);
            TurnManager.setInitialized(false);
            FightTurnPastListener.setPresentTurn(null);
        }
    }

    private static void testCommandExecutedEvent() {
        section("扩展事件：CommandExecutedEvent");
        Probe probe = new Probe();
        EventBus.register(probe);
        try {
            CommandManager.execute("/help");
            check("① 命令执行完会发 CommandExecutedEvent（成功/失败都发）—— 实际 " + probe.command,
                    probe.command >= 1);
        } catch (Exception e) {
            fail("命令事件测试抛出异常：" + e);
        } finally {
            EventBus.unregister(probe);
        }
    }

    /**
     * 跑本主题的全部用例。
     */
    static void runAll() {
        testSkillUseEvent();
        testHealAndManaEvents();
        testEffectEvents();
        testCommandExecutedEvent();
        testTurnEventsAndDeath();
        testDeathEvent();
    }

    /**
     * 探针：把收到的每种事件记成计数 / 最近一次实例。
     */
    static final class Probe {
        int skillUse;
        int heal;
        int manaChanged;
        int effectApplied;
        int effectRemoved;
        int turnStart;
        int turnEnd;
        int turnAction;
        int death;
        int command;
        boolean cancelSkill;
        EntityHealEvent lastHeal;
        ManaChangedEvent lastMana;
        EffectRemovedEvent lastRemoved;
        EntityDeathEvent lastDeath;

        @SubscribeEvent
        public void onSkillUse(SkillUseEvent event) {
            skillUse++;
            if (cancelSkill) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public void onHeal(EntityHealEvent event) {
            heal++;
            lastHeal = event;
        }

        @SubscribeEvent
        public void onMana(ManaChangedEvent event) {
            manaChanged++;
            lastMana = event;
        }

        @SubscribeEvent
        public void onEffectApplied(EffectAppliedEvent event) {
            effectApplied++;
        }

        @SubscribeEvent
        public void onEffectRemoved(EffectRemovedEvent event) {
            effectRemoved++;
            lastRemoved = event;
        }

        @SubscribeEvent
        public void onTurnStart(FightTurnStartEvent event) {
            turnStart++;
        }

        @SubscribeEvent
        public void onTurnEnd(FightTurnEndEvent event) {
            turnEnd++;
        }

        @SubscribeEvent
        public void onTurnAction(TurnActionExecutedEvent event) {
            turnAction++;
        }

        @SubscribeEvent
        public void onDeath(EntityDeathEvent event) {
            death++;
            lastDeath = event;
        }

        @SubscribeEvent
        public void onCommand(CommandExecutedEvent event) {
            command++;
        }
    }

    /**
     * 简单效果（只为了让 addEffect / removeEffect 有东西可挂）。
     */
    static final class ProbeEffect extends Effect {
        ProbeEffect() {
            super("extensionEventProbe");
        }

    }
}
