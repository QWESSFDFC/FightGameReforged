package cn.gfhnv.debug_tools;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.damage.DamageType;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.DamageEvent;
import cn.gfhnv.game.entityController.FixOrderController;
import cn.gfhnv.game.event.EffectUpdateEvent;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.FightEndEvent;
import cn.gfhnv.game.event.FightPastOneTurnEvent;
import cn.gfhnv.game.eventListener.EffectEventListener;
import cn.gfhnv.game.eventListener.FightEndEventListener;
import cn.gfhnv.game.eventListener.FightTurnPastListener;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.officialStuff.customEffect.flameReaverEffects.SacrificeRite;
import cn.gfhnv.game.officialStuff.customEntity.monsters.CommonInsect;
import cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade;
import cn.gfhnv.game.officialStuff.customEntity.players.PlayerOne;
import cn.gfhnv.game.officialStuff.customEntity.summons.BrokenContainer;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.configLoadingSystem.DataKeys;
import cn.gfhnv.game.system.configLoadingSystem.GameRules;
import cn.gfhnv.game.system.fight.ActEntry;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.fight.TurnEntry;
import cn.gfhnv.game.system.fight.TurnManager;
import cn.gfhnv.game.system.logSystem.LogWriter;
import cn.gfhnv.game.world.World;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static cn.gfhnv.debug_tools.TestKit.*;

/**
 * 自测：<b>临时实体清理 + 死亡实体的结算守卫</b>（2026-11-15 第 76 批）。
 * <p>
 * 作者的两条要求：
 * <ol>
 *     <li>战斗实体是<b>纯临时实体</b> —— 战斗结束、以及战斗中途死亡/退出，都要离开 {@code World.things}；</li>
 *     <li>"结算效果等时应该想到实体是否死亡" —— 尸体不再 tick 效果、不再被治疗；</li>
 *     <li>物品确认接受 {@code y}/{@code yes}（大小写不敏感），不再只认全小写。</li>
 * </ol>
 */
final class FightLifecycleTests {

    private FightLifecycleTests() {
    }

    private static void testWorldCleanupOnFightEnd() {
        section("临时实体：战斗结束离开 World.things");
        List<Object> thingsBefore = new ArrayList<>(World.getThings());
        boolean inFightBefore = cn.gfhnv.game.GameMain.isInFight();
        try {
            PlayerOne fighter = new PlayerOne(125);
            CommonInsect enemy = new CommonInsect(125L);
            World.addThing(fighter);
            World.addThing(enemy);
            List<LivingThing> fighters = new ArrayList<>(List.of(fighter));
            List<LivingThing> enemies = new ArrayList<>(List.of(enemy));
            Item reward = new Item("奖励探针", "自测用", "lifecycleRewardProbe");
            World.addThing(reward);
            List<Item> rewards = new ArrayList<>(List.of(reward));
            Fight fight = new Fight(enemies, rewards, fighters);

            EventBus.register(new FightEndEventListener(new FightTurnPastListener()));
            cn.gfhnv.game.GameMain.setFightInProgressForSelfTest(true);
            EventBus.post(new FightEndEvent(true, fight));

            check("① 我方副本已离开 World.things", !World.getThings().contains(fighter));
            check("② 敌方副本已离开 World.things", !World.getThings().contains(enemy));
            check("③ 奖励物品也离开 World.things（同样是临时对象）", !World.getThings().contains(reward));
        } catch (Exception e) {
            fail("战斗结束清理测试抛出异常：" + e);
        } finally {
            // 还原本进程的运行时列表（其它主题共用 World）
            List<Object> now = new ArrayList<>(World.getThings());
            for (Object o : now) {
                if (!thingsBefore.contains(o)) {
                    World.removeThing((cn.gfhnv.game.Thing) o);
                }
            }
            cn.gfhnv.game.GameMain.setFightInProgressForSelfTest(inFightBefore);
        }
    }

    private static void testWorldCleanupOnDeath() {
        section("临时实体：战斗中途死亡也离开 World.things");
        List<Object> thingsBefore = new ArrayList<>(World.getThings());
        List<TurnEntry> turnsBefore = new ArrayList<>(TurnManager.getTurns());
        boolean inFightBefore = cn.gfhnv.game.GameMain.isInFight();
        FightTurnPastListener driver = new FightTurnPastListener();
        try {
            PlayerOne fighter = new PlayerOne(125);
            CommonInsect dead = new CommonInsect(125L);
            dead.setHp(0);
            World.addThing(fighter);
            World.addThing(dead);
            Fight fight = new Fight(new ArrayList<>(List.of(dead)), new ArrayList<>(),
                    new ArrayList<>(List.of(fighter)));

            TurnManager.setPresentTime(BigDecimal.ZERO);
            TurnManager.init(fight);
            EventBus.register(new FightEndEventListener(driver));
            cn.gfhnv.game.GameMain.setFightInProgressForSelfTest(true);
            EventBus.register(driver);
            EventBus.post(new FightPastOneTurnEvent(fight));

            check("① 中途死亡的副本已离开 World.things", !World.getThings().contains(dead));
            check("② 战斗结束后存活的副本也离开了（不留到下一局）", !World.getThings().contains(fighter));
        } catch (Exception e) {
            fail("死亡清理测试抛出异常：" + e);
        } finally {
            EventBus.unregister(driver);
            for (Object o : new ArrayList<>(World.getThings())) {
                if (!thingsBefore.contains(o)) {
                    World.removeThing((cn.gfhnv.game.Thing) o);
                }
            }
            cn.gfhnv.game.GameMain.setFightInProgressForSelfTest(inFightBefore);
            TurnManager.setTurns(turnsBefore);
            TurnManager.setPresentTime(null);
            TurnManager.setInitialized(false);
            FightTurnPastListener.setPresentTurn(null);
        }
    }

    private static void testDeadEntityGuards() {
        section("死亡实体：不再 tick 效果、不再被治疗");
        try {
            PlayerOne dead = new PlayerOne(125);
            dead.setHp(0);
            long healed = dead.heal(100);
            check("① 尸体不回血（heal 返回 0、hp 仍是 0）—— 实际 " + healed + " / " + dead.getHp(),
                    healed == 0 && dead.getHp() == 0);

            CountingEffect effect = new CountingEffect();
            dead.addEffect(effect);
            effect.ticks = 0;   // addEffect 的 initialEffect 不计入 tick
            EffectEventListener effectListener = new EffectEventListener();
            EventBus.register(effectListener);
            try {
                EventBus.post(new EffectUpdateEvent(dead,
                        new TurnEntry(dead, BigDecimal.ONE, BigDecimal.ZERO)));
                check("② 尸体身上的效果不 tick —— 实际 " + effect.ticks, effect.ticks == 0);

                PlayerOne alive = new PlayerOne(125);
                CountingEffect aliveEffect = new CountingEffect();
                alive.addEffect(aliveEffect);
                aliveEffect.ticks = 0;   // 同上：只数 tick
                EventBus.post(new EffectUpdateEvent(alive,
                        new TurnEntry(alive, BigDecimal.ONE, BigDecimal.ZERO)));
                check("③ 活着的实体照常 tick（对照）—— 实际 " + aliveEffect.ticks, aliveEffect.ticks == 1);
            } finally {
                EventBus.unregister(effectListener);
            }
        } catch (Exception e) {
            fail("死亡守卫测试抛出异常：" + e);
        }
    }

    private static void testItemConfirmInput() {
        section("物品确认：y / yes（大小写不敏感）");
        check("① y / Y / yes / YES / 带空格都算\"是\"",
                cn.gfhnv.game.entityController.PlayerController.isAffirmative("y")
                        && cn.gfhnv.game.entityController.PlayerController.isAffirmative("Y")
                        && cn.gfhnv.game.entityController.PlayerController.isAffirmative("yes")
                        && cn.gfhnv.game.entityController.PlayerController.isAffirmative("YES")
                        && cn.gfhnv.game.entityController.PlayerController.isAffirmative(" Yes "));
        check("② no / n / 空串 / null 都不算\"是\"",
                !cn.gfhnv.game.entityController.PlayerController.isAffirmative("no")
                        && !cn.gfhnv.game.entityController.PlayerController.isAffirmative("n")
                        && !cn.gfhnv.game.entityController.PlayerController.isAffirmative("")
                        && !cn.gfhnv.game.entityController.PlayerController.isAffirmative(null));
    }

    /**
     * 时间轴基值可配 + 速度守卫（第 77 批）：速度 0/负 不再抛 ArithmeticException，
     * 需要时间统一由 {@code TurnManager.needTimeOf} 算，基值来自 {@code GameRules} 的 {@code formula.turnTimeBase}。
     */
    private static void testNeedTimeGuard() {
        section("时间轴：速度守卫 + 基值可配");
        try {
            PlayerOne probe = new PlayerOne(125);
            probe.setSpeed(0);
            BigDecimal zeroSpeed = TurnManager.needTimeOf(probe);
            probe.setSpeed(-50);
            BigDecimal negativeSpeed = TurnManager.needTimeOf(probe);
            probe.setSpeed(200);
            BigDecimal fastSpeed = TurnManager.needTimeOf(probe);

            check("① 速度 0 不崩、按 1 算（= 基值 10000）—— 实际 " + zeroSpeed,
                    zeroSpeed.compareTo(new BigDecimal("10000")) == 0);
            check("② 负速度同样退化 —— 实际 " + negativeSpeed,
                    negativeSpeed.compareTo(new BigDecimal("10000")) == 0);
            check("③ 速度 200 ⇒ 50 —— 实际 " + fastSpeed, fastSpeed.compareTo(new BigDecimal("50")) == 0);
            check("④ 基值来自规则表 formula.turnTimeBase（出厂 10000，键已登记）",
                    GameRules.isKnown(DataKeys.Rule.Formula.TURN_TIME_BASE)
                            && GameRules.getDouble(DataKeys.Rule.Formula.TURN_TIME_BASE) == 10000d);
        } catch (Exception e) {
            fail("时间轴守卫测试抛出异常：" + e);
        }
    }

    /**
     * 效果升级回收旧加成（第 78 批，K1）：同 id 更高等级替换时，旧实例必须先 whenLastTimeEnd。
     */
    private static void testUpgradeRecyclesOldBonus() {
        section("效果升级：旧实例先收尾（K1）");
        try {
            PlayerOne target = new PlayerOne(125);
            // ⚠️ 必须同一个类：Effect#equals 要求 getClass() 相同（id 一样但类不同会被当成两条）
            UpgradeProbeEffect level1 = new UpgradeProbeEffect(1);
            target.addEffect(level1);
            int endsBefore = UpgradeProbeEffect.ends;
            UpgradeProbeEffect level2 = new UpgradeProbeEffect(2);
            target.addEffect(level2);
            check("① 替换更高等级时旧实例收到 whenLastTimeEnd —— 实际结束次数 " + UpgradeProbeEffect.ends,
                    UpgradeProbeEffect.ends == endsBefore + 1);
            check("② 列表里只剩新实例（等级 2）—— 实际 "
                            + target.getEntityEffectList().size() + " 个",
                    target.getEntityEffectList().size() == 1
                            && target.getEntityEffectList().get(0).getLevel() == 2);
        } catch (Exception e) {
            fail("效果升级测试抛出异常：" + e);
        }
    }

    /**
     * 千冶·刃：结界结束时能量保底 75%（第 78 批，原 P1-7）。
     */
    private static void testMortenaxEnergyFloor() {
        section("千冶·刃：结界结束能量保底");
        try {
            MortenaxBlade blade = new MortenaxBlade(125);
            blade.setEnergy(10);
            blade.setInfiniteFury(true);
            blade.endInfiniteFury();
            long floor = (long) (blade.getEnergyMax() * 0.75);
            check("① 结界结束能量回到 75% 保底 —— 实际 " + blade.getEnergy() + "/" + blade.getEnergyMax(),
                    blade.getEnergy() >= floor);
        } catch (Exception e) {
            fail("千冶·刃能量保底测试抛出异常：" + e);
        }
    }

    /**
     * 回合循环异常兜底（第 78 批，原 P0-1）：动作抛异常时必须强制收场，不能把状态停在半路。
     */
    private static void testTurnLoopExceptionGuard() {
        section("回合循环：异常强制收场（P0-1）");
        boolean inFightBefore = cn.gfhnv.game.GameMain.isInFight();
        List<TurnEntry> turnsBefore = new ArrayList<>(TurnManager.getTurns());
        FightTurnPastListener driver = new FightTurnPastListener();
        try {
            PlayerOne hero = new PlayerOne(125);
            Skill boom = new Skill("抛错探针", "自测故意抛错", 0, 1, 0, 1) {
                @Override
                public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
                    throw new RuntimeException("自测故意抛错");
                }

                @Override
                public Skill copy() {
                    return this;
                }
            };
            FixOrderController controller = new FixOrderController(new ArrayList<>(List.of(boom)), hero);
            controller.setRotationByName(List.of("抛错探针"));
            hero.setController(controller);

            CommonInsect enemy = new CommonInsect(125L);
            Fight fight = new Fight(new ArrayList<>(List.of(enemy)), new ArrayList<>(),
                    new ArrayList<>(List.of(hero)));
            TurnManager.setPresentTime(BigDecimal.ZERO);
            TurnManager.init(fight);

            EventBus.register(new FightEndEventListener(driver));
            cn.gfhnv.game.GameMain.setFightInProgressForSelfTest(true);
            EventBus.register(driver);
            EventBus.post(new FightPastOneTurnEvent(fight));

            check("① 异常被兜住、战斗被强制收场（isInFight=false）",
                    !cn.gfhnv.game.GameMain.isInFight());
            check("② 时间轴已清空", TurnManager.getTurns().isEmpty());
        } catch (Exception e) {
            fail("回合循环异常兜底测试抛出异常（异常逃逸 = 未兜住）：" + e);
        } finally {
            EventBus.unregister(driver);
            cn.gfhnv.game.GameMain.setFightInProgressForSelfTest(inFightBefore);
            TurnManager.setTurns(turnsBefore);
            TurnManager.setPresentTime(null);
            TurnManager.setInitialized(false);
            FightTurnPastListener.setPresentTurn(null);
        }
    }

    /**
     * 共祭清理不能踩只读效果列表（第 79 批，真实战斗回放抓到的 UOE）：
     * {@code getEntityEffectList().removeIf(...)} 会命中只读迭代器；
     * 正确入口是 {@code LivingThing#removeEffectsIf(...)}。
     */
    private static void testSacrificeRiteCleanup() {
        section("共祭清理：只读效果列表（UOE 回归）");
        try {
            cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver reaver =
                    new cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver(125L);
            BrokenContainer container = new BrokenContainer(reaver);
            container.addEffect(new SacrificeRite(3));
            boolean removed = BrokenContainer.removeSacrificeRite(container);
            check("① removeSacrificeRite 走 removeEffectsIf 正常摘掉共祭 —— 实际 " + removed
                            + "，剩余效果 " + container.getEntityEffectList().size(),
                    removed && container.getEntityEffectList().isEmpty());

            boolean threwWithMessage = false;
            try {
                container.getEntityEffectList().removeIf(effect -> true);
            } catch (UnsupportedOperationException e) {
                threwWithMessage = e.getMessage() != null && e.getMessage().contains("只读");
            }
            check("② 直接对只读列表 removeIf 仍会被拦下（且带可读原因）", threwWithMessage);
        } catch (Exception e) {
            fail("共祭清理测试抛出异常：" + e);
        }
    }

    /**
     * 错误详情回写日志（第 80 批）：完整堆栈要进 {@code logs/latest.log}，不能只剩一行 message。
     */
    private static void testThrowableLogging() {
        section("错误详情：完整堆栈回写日志");
        String stack = LogWriter.stackTraceOf(new IllegalStateException("自测boom"));
        check("① 堆栈文本含类名 / message / 帧",
                stack.contains("IllegalStateException") && stack.contains("自测boom")
                        && stack.contains("\tat "));
        try {
            LogWriter.writeThrowable("自测上下文", new IllegalStateException("自测boom"));
            String log = java.nio.file.Files.readString(java.nio.file.Paths.get("./logs/latest.log"));
            check("② writeThrowable 把上下文与帧写进 logs/latest.log",
                    log.contains("自测上下文") && log.contains("自测boom") && log.contains("\tat "));
        } catch (Exception e) {
            fail("日志回写测试抛出异常：" + e);
        }
    }

    /**
     * 行动者字段（第 81 批）：条目自带执行者，不再一律用"当前回合的行动者"。
     */
    private static void testActEntryActor() {
        section("行动队列：条目自带行动者");
        try {
            PlayerOne owner = new PlayerOne(125);
            owner.setName("回合主角");
            CommonInsect other = new CommonInsect(125L);
            other.setName("别人");
            List<String> log = new ArrayList<>();
            TurnEntry turn = new TurnEntry(owner, BigDecimal.ONE, BigDecimal.ZERO);
            turn.addAct((f, u) -> log.add("默认:" + u.getName()), ActEntry.PRIORITY_NORMAL);
            turn.addAct(other, (f, u) -> log.add("显式:" + u.getName()), ActEntry.PRIORITY_FIRST);
            Fight fight = new Fight(new ArrayList<>(List.of(other)), new ArrayList<>(),
                    new ArrayList<>(List.of(owner)));
            FightTurnPastListener.runActQueue(fight, turn);
            check("① 默认行动者 = 回合行动者 —— " + log, log.contains("默认:回合主角"));
            check("② 显式行动者优先于回合行动者 —— " + log, log.contains("显式:别人"));
        } catch (Exception e) {
            fail("行动者字段测试抛出异常：" + e);
        }
    }

    /**
     * 千冶·刃免死回血（第 81 批真 bug）：他在**别人的回合**里被打死时，
     * 回血动作必须仍然以「千冶·刃本人」为执行者（而不是那个回合的行动者）。
     */
    private static void testMortenaxDeathWardForeignTurn() {
        section("千冶·刃：别人的回合里死亡也要回血");
        TurnEntry turnBefore = FightTurnPastListener.getPresentTurn();
        try {
            MortenaxBlade blade = new MortenaxBlade(125);
            blade.setHp(1);
            blade.setInfiniteFury(true);
            CommonInsect killer = new CommonInsect(125L);
            Fight fight = new Fight(new ArrayList<>(List.of(killer)), new ArrayList<>(),
                    new ArrayList<>(List.of(blade)));
            // 关键：当前回合属于**别人**（完整容器/盗火行者），不是千冶·刃
            TurnEntry enemyTurn = new TurnEntry(killer, BigDecimal.ONE, BigDecimal.ZERO);
            FightTurnPastListener.setPresentTurn(enemyTurn);

            blade.modifyIncomingDamage(0,
                    new DamageEvent(killer, blade, null, DamageType.SpecialSkill));
            FightTurnPastListener.runActQueue(fight, enemyTurn);

            check("① 免死触发后血量恢复（不再是 1）—— 实际 " + blade.getHp() + "/" + blade.getHpMax(),
                    blade.getHp() > 1);
            check("② 结界已解除", !blade.isInfiniteFury());
        } catch (Exception e) {
            fail("免死回血测试抛出异常：" + e);
        } finally {
            FightTurnPastListener.setPresentTurn(turnBefore);
        }
    }

    /**
     * 跑本主题的全部用例。
     */
    static void runAll() {
        testWorldCleanupOnFightEnd();
        testWorldCleanupOnDeath();
        testDeadEntityGuards();
        testItemConfirmInput();
        testNeedTimeGuard();
        testUpgradeRecyclesOldBonus();
        testMortenaxEnergyFloor();
        testTurnLoopExceptionGuard();
        testSacrificeRiteCleanup();
        testThrowableLogging();
        testActEntryActor();
        testMortenaxDeathWardForeignTurn();
    }

    /**
     * 计数用效果：每次 {@code comeIntoEffect} 自增。
     */
    static final class CountingEffect extends Effect {
        int ticks;

        CountingEffect() {
            super("lifecycleCountEffect");
            setLastTime(5);
        }

        @Override
        public void comeIntoEffect(LivingThing thing) {
            ticks++;
        }
    }

    /**
     * 计数用升级探针：记录 whenLastTimeEnd 被调用几次。
     */
    static final class UpgradeProbeEffect extends Effect {
        static int ends;

        UpgradeProbeEffect(int level) {
            super("upgradeProbe", level, 5);
        }

        @Override
        public void whenLastTimeEnd(LivingThing target) {
            ends++;
        }
    }
}
