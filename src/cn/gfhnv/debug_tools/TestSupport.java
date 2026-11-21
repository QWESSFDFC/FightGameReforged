package cn.gfhnv.debug_tools;

import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.data.DataFlatten;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entity.PropertyEnhanceType;
import cn.gfhnv.game.entityController.FixOrderController;
import cn.gfhnv.game.event.DamageEvent;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.eventListener.FightTurnPastListener;
import cn.gfhnv.game.interfaces.IModifyIgnitionMax;
import cn.gfhnv.game.interfaces.ISpecialAction;
import cn.gfhnv.game.inventory.Slot;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.mod.Mod;
import cn.gfhnv.game.officialStuff.customEntity.monsters.CommonInsect;
import cn.gfhnv.game.officialStuff.customEntity.players.ActorLiXiaoYan;
import cn.gfhnv.game.officialStuff.customEntity.players.Phainon;
import cn.gfhnv.game.officialStuff.customEntity.players.PlayerOne;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.command.CommandManager;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.fight.TurnEntry;
import cn.gfhnv.game.system.fight.TurnManager;
import cn.gfhnv.game.world.World;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static cn.gfhnv.debug_tools.TestKit.*;
import static cn.gfhnv.debug_tools.TestProbes.*;

/**
 * 自测的**共用工具**：造实体、找效果、解析技能名、读配置、抓输出……
 * <p>
 * 只放"给多个测试类用"的东西；只服务一个测试类的辅助方法放在那个类里。
 * <p>
 * 被别的用例<b>调用</b>的那种"子用例"（例如 {@code testDeadRemovedFromAllEntities}）也在这里 ——
 * 它们是测试步骤，不是一个独立主题。
 */
public final class TestSupport {

    /**
     * 角色 / 召唤物类<b>自己的</b>状态键（不是 {@code LivingThing} 的通用属性，也不是配置该管的东西）。
     * <p>
     * 它们会出现在 {@code /data} 里，所以"dump 出来的键都能配置或在只读清单里"那条断言
     * 必须把它们排除；这里逐条列出来，是为了让"新增了一个子类状态键"这件事被人看见
     * （而不是悄悄混进"已知的"那一堆里）。
     * <p>
     * <b>2026-10-03 起它是"两个清单的并集"</b>（{@code DataKeys.CLASS_STATE} =
     * {@code CLASS_CONFIG} 已放行 + {@code CLASS_RUNTIME} 继续挡）：这一份口径只服务"dump 契约"
     * —— 配置层放不放行由那两个清单分别负责，别再用这一份去判断能不能配。
     */
    static final java.util.Set<String> SUBCLASS_STATE_KEYS =
            cn.gfhnv.game.system.configLoadingSystem.DataKeys.CLASS_STATE;

    /**
     * <b>第 1 步的「现形清单」</b>：反射面（只算标量）里<b>手写表没有、也还没写
     * {@code @NoConfig}</b> 的那些键。
     * <p>
     * 口径（{@code 2026-10-03} 实测，{@code playerOne} 模板）：反射面 53 个标量键 − 手写表 31 个键
     * （含 {@code manaGrow} 块与 {@code inventorySlots} 这两个"表里有、反射面没有"的项）
     * = <b>29 个表里有的</b>，剩下的<b>这 24 个</b>就是"多出来的"。
     * <p>
     * 它从一份"人看的清单"变成了<b>断言</b>：第 ⑤ 组守卫要求这个集合与
     * {@code 反射面 − 手写表 − @NoConfig} <b>逐字相等</b> —— 多一个少一个都变红。
     * 于是"新加了一个标量字段"或"表里删了一行"这两件事都不可能悄悄发生。
     * <p>
     * 分类结果（第 2 步，逐条给了理由）：<b>没有一个该开放</b> ——
     * 10 个五元素 {@code *Penetration} / {@code *DamageEnhance}（临时属性，副本都不带）、
     * 12 个 {@code *Enhance*}（效果与技能在战斗中加减的运行时加成，战斗结束清零）、
     * {@code extraDamage}（死字段）、{@code individualMultipleArea}（角色构造器算出来的派生面板倍率）。
     * 所以第 2 步给这 24 个逐个写 {@code @NoConfig}，写完这条断言就翻成
     * "这 24 个<b>全部</b>在 {@code @NoConfig} 清单里"（清单继续留着当分类记录）。
     */
    static final java.util.Set<String> REFLECTION_ONLY_SCALAR_KEYS =
            new java.util.TreeSet<>(Arrays.asList(
                    // ② 2026-11-14 起 13 个 *Enhance* 标量已搬进 `propertyEnhances` 条目列表，
                    //    不再是标量字段（所以不在这张"反射面多出来的标量键"清单里）
                    // ③ 死字段：全项目零写入点（活的那套是 Skill#extraDamage）
                    "extraDamage",
                    // ④ 派生面板倍率：由角色构造器算（清了会永久削弱该角色）
                    "individualMultipleArea"));

    private TestSupport() {
    }

    /**
     * <b>效果结算前移</b>（2026-11-14 第 58 批，Step 6 第一步）：
     * 回合头输出之后先 tick 效果，再跑「开场动作」与正式行动。
     * <p>
     * 判据用<b>顺序</b>而不是数值:探针效果在 {@code comeIntoEffect} 里记一个 {@code TICK},
     * 开场动作里记一个 {@code FIRST};新顺序必须是 {@code [TICK, FIRST]}。
     * 老实现（tick 在回合最后一步）会记成 {@code [FIRST, TICK]}，这条断言就会红。
     */

    /**
     * 白厄免死自测（2026-10 用户实测：变身 + 灾厄-弑魂焚诏击败完整容器拿到额外回合之后，
     * 敌人打他一直是 {@code -0}、HP 停在 1 不动，等于<b>白厄无敌</b>）。
     * <p>
     * 钉住三件事：<b>标志未置位时照常锁血排队</b>、<b>同一回合里重复致死不再排第二刀</b>、
     * 以及<b>试算期间只读</b>（不排队、不改任何状态）。
     * <p>
     * ⚠️ <b>「那一击排晚了就永久锁血」那套死锁兜底已经整条撤掉</b>（它没能在任何场景里被复现，
     * 而且在复现场景里一次都没救回来；见 {@code 60-COMBAT.md} 的 §5.7）——
     * 连带撤掉了它的 4 条断言（旧 ③④ 两组与旧 ⑤ 里"回合引用一起清"那半条）。
     * 真正修掉「白厄无敌」的是<b>退出协议改由"最后一击"自己收口</b>：
     * 中止变身只调用 {@code Phainon#finalizeAwakenByInterrupt()} 冻结剩余回合数并清【弑魂之炽】，
     * 最后一击挥完才发 {@code AwakenEndEvent}。见
     * {@link #testAwakenInterruptExitProtocol()} 那条跑真实回合循环的用例。
     * <p>
     * ⚠️ 这条测试不碰任何数值：{@code LastAttack} 的 {@code 0.125} 与下限 {@code 7} 原样不动。
     *
     * @author AI（DeepSeek）生成
     */
    static void testSoulscorchDeathWardDoesNotDeadlock() {
        System.out.println();
        System.out.println("-------- 白厄免死：锁血、排队与只读试算 --------");
        Phainon wardPhainon = new Phainon(125);
        // 监听器必须带着归属（owner）：AwakenEndEvent 是广播事件，见 AwakeEndListener#owner
        cn.gfhnv.game.officialStuff.customEvent.phainonEvents.AwakeEndListener awakeEndListener =
                new cn.gfhnv.game.officialStuff.customEvent.phainonEvents.AwakeEndListener(wardPhainon);
        try {
            // 免死排队时会 World.prototypeCopyOf(LastAttack.class)，原型必须在注册表里
            World.getSkillList();

            wardPhainon.setName("免死探针");
            LivingThing wardDummy = new CommonInsect(100L).copy();
            wardDummy.setName("挨打探针");
            List<LivingThing> wardEnemies = new ArrayList<>();
            wardEnemies.add(wardDummy);
            List<LivingThing> wardFighters = new ArrayList<>();
            wardFighters.add(wardPhainon);
            Fight wardFight = new Fight(wardEnemies, new ArrayList<>(), wardFighters);

            Skill lethalProbe = new ProbeSkill("致死探针", new ArrayList<>(), 1, 1.0);
            DamageEvent wardHit = new DamageEvent(wardDummy, wardPhainon, lethalProbe);
            wardHit.getDamage().setDamageAmount(9999L);

            wardPhainon.setAwaken(true);
            wardPhainon.setHp(1);
            wardPhainon.setPendingLastAttack(false);
            wardPhainon.setExtraTurns(8);
            wardPhainon.setInterruptedRemainingExtraTurns(-1);

            // ① 正常路径：标志没置位 → 锁 1 血 + 把最后一击排进"当前回合" + 当场走完退出协议
            TurnEntry wardTurn = new TurnEntry(wardPhainon, BigDecimal.ZERO, BigDecimal.ZERO);
            FightTurnPastListener.setPresentTurn(wardTurn);
            int queuedBefore = wardTurn.getLastExecuteList().size();
            wardPhainon.takeDamage(wardHit);
            check("免死：标志未置位时锁 1 血、置位标志、把最后一击排进当前回合",
                    wardPhainon.getHp() == 1
                            && wardPhainon.isPendingLastAttack()
                            && wardTurn.getLastExecuteList().size() == queuedBefore + 1);
            check("免死：中止变身时当场冻结「打断那一刻还剩几个额外回合」（这里 8 —— 冻结的是实时值，"
                            + "压到 7 的是 LastAttack 里的公式）",
                    wardPhainon.getInterruptedRemainingExtraTurns() == 8);

            // ② 同一回合里第二次致死：不再排第二刀（这条是老行为，不能被兜底改掉）
            int queuedAfterFirst = wardTurn.getLastExecuteList().size();
            wardPhainon.takeDamage(wardHit);
            check("免死：同一回合里重复致死只锁血、不再排第二刀（老行为保留）",
                    wardPhainon.getHp() == 1
                            && wardTurn.getLastExecuteList().size() == queuedAfterFirst);

            // ③ 试算期间只读：不排队、也不改任何状态
            boolean pendingBeforeAnticipate = wardPhainon.isPendingLastAttack();
            long anticipatedHp = wardPhainon.anticipating(
                    () -> wardPhainon.modifyIncomingDamage(0, wardHit));
            check("免死：试算期间只返回锁 1 的血量，不动任何状态",
                    anticipatedHp == 1
                            && wardPhainon.isPendingLastAttack() == pendingBeforeAnticipate
                            && wardTurn.getLastExecuteList().size() == queuedAfterFirst);

            // ④ 那一击真的挥出去之后，标志必须被清掉（正常路径的收口）
            wardPhainon.setAwaken(true);
            EventBus.register(awakeEndListener);
            for (ISpecialAction action : wardTurn.getLastExecuteList()) {
                action.execute(wardFight, wardPhainon);
            }
            check("免死：最后一击挥完之后 AwakeEndListener 把标志与冻结值一起清掉",
                    !wardPhainon.isPendingLastAttack()
                            && wardPhainon.getInterruptedRemainingExtraTurns() == -1
                            && !wardPhainon.isAwaken());
        } catch (Exception e) {
            fail("白厄免死死锁测试抛出异常：" + e);
        } finally {
            // 收尾：把监听器与"当前回合"还回去，别渗进后面的用例
            EventBus.unregister(awakeEndListener);
            FightTurnPastListener.setPresentTurn(null);
        }
    }

    /**
     * <b>时间轴排不出回合时必须收掉战斗</b>（2026-11-15 第 70 批，A3）：
     * 两边都还有人、但回合列表是空的时候，回合循环要发 {@code FightEndEvent} 并退出，
     * 不能只 {@code break} —— 只 break 会留下 "fightInProgress 仍为 true + 本场监听器没注销"。
     */
    static void testTimelineDeadEndEndsFight() {
        System.out.println();
        System.out.println("-------- 回合流程：时间轴排不出回合时收掉战斗（A3） --------");
        boolean inFightBefore = cn.gfhnv.game.GameMain.isInFight();
        List<TurnEntry> turnsBefore = new ArrayList<>(TurnManager.getTurns());
        boolean[] ended = {false};
        Object probe = new Object() {
            @SubscribeEvent
            public void onEnd(cn.gfhnv.game.event.FightEndEvent event) {
                ended[0] = !event.isPlayerWin();
            }
        };
        FightTurnPastListener driver = new FightTurnPastListener();
        try {
            LivingThing fighter = new PlayerOne(125);
            fighter.setName("平局探针");
            fighter.getController().getSkills().clear();
            LivingThing enemy = new cn.gfhnv.game.officialStuff.customEntity.monsters.CommonInsect(125L);
            enemy.getController().getSkills().clear();
            cn.gfhnv.game.system.fight.Fight fight = new cn.gfhnv.game.system.fight.Fight(
                    new ArrayList<>(List.of(enemy)), new ArrayList<>(), new ArrayList<>(List.of(fighter)));

            TurnManager.setTurns(new ArrayList<>());     // ★ 空时间轴：两边都活着但排不出回合
            TurnManager.setPresentTime(BigDecimal.ZERO);
            TurnManager.setInitialized(false);

            EventBus.register(probe);
            EventBus.register(new cn.gfhnv.game.eventListener.FightEndEventListener(driver));
            cn.gfhnv.game.GameMain.setFightInProgressForSelfTest(true);
            EventBus.register(driver);
            EventBus.post(new cn.gfhnv.game.event.FightPastOneTurnEvent(fight));

            check("时间轴排不出回合：发了 FightEndEvent（未胜利结算）而不是只 break —— 实际 ended="
                    + ended[0], ended[0]);
            check("时间轴排不出回合：这一场结束后回合列表是空的", TurnManager.getTurns().isEmpty());
        } catch (Exception e) {
            fail("时间轴兜底测试抛出异常：" + e);
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

    static void testEffectTicksBeforeAction() {
        System.out.println();
        System.out.println("-------- 回合流程：效果结算在回合头、动作之前（Step 6 第一步） --------");
        boolean inFightBefore = cn.gfhnv.game.GameMain.isInFight();
        List<TurnEntry> turnsBefore = new ArrayList<>(TurnManager.getTurns());
        List<String> order = new ArrayList<>();
        try {
            World.getSkillList();
            LivingThing actor = new PlayerOne(125);
            actor.setName("顺序探针");
            // 技能表清空：这一回合不去读标准输入
            actor.getController().getSkills().clear();

            cn.gfhnv.game.effect.Effect probe = new cn.gfhnv.game.effect.Effect("orderProbe") {
                @Override
                public void comeIntoEffect(LivingThing thing) {
                    order.add("TICK");
                }
            };
            probe.setLastTime(3);
            actor.addEffectDirect(probe);

            LivingThing enemy = new cn.gfhnv.game.officialStuff.customEntity.monsters.CommonInsect(125L);
            enemy.setName("顺序探针靶子");
            enemy.getController().getSkills().clear();

            List<LivingThing> fighters = new ArrayList<>();
            fighters.add(actor);
            List<LivingThing> enemies = new ArrayList<>();
            enemies.add(enemy);
            cn.gfhnv.game.system.fight.Fight fight = new cn.gfhnv.game.system.fight.Fight(enemies,
                    new ArrayList<>(), fighters);

            TurnManager.setTurns(new ArrayList<>());
            TurnManager.setPresentTime(BigDecimal.ZERO);
            TurnManager.setInitialized(false);
            TurnEntry turn = new TurnEntry(actor, BigDecimal.ZERO, BigDecimal.ZERO);
            // 开场动作：在"动作之前"跑，用它当"动作之前"的标记
            turn.getFirstExecuteList().add((f, user) -> order.add("FIRST"));
            TurnManager.getTurns().add(turn);
            TurnManager.getTurns().add(new TurnEntry(enemy, BigDecimal.ZERO, BigDecimal.ZERO));

            EventBus.register(new ProbeStopAfterOneTurn());
            // 效果 tick 由 EffectEventListener 执行；自测进程里它不一定已注册，这里显式挂一个
            cn.gfhnv.game.eventListener.EffectEventListener ticker =
                    new cn.gfhnv.game.eventListener.EffectEventListener();
            EventBus.register(ticker);
            cn.gfhnv.game.GameMain.setFightInProgressForSelfTest(true);
            FightTurnPastListener driver = new FightTurnPastListener();
            EventBus.register(driver);
            EventBus.post(new cn.gfhnv.game.event.FightPastOneTurnEvent(fight));
            EventBus.unregister(driver);
            EventBus.unregister(ticker);

            check("效果结算前移：tick 发生在开场动作之前（顺序 " + order + "）",
                    order.size() >= 2 && "TICK".equals(order.get(0)) && "FIRST".equals(order.get(1)));
        } catch (Exception e) {
            fail("效果结算前移测试抛出异常：" + e);
        } finally {
            cn.gfhnv.game.GameMain.setFightInProgressForSelfTest(inFightBefore);
            TurnManager.setTurns(turnsBefore);
            TurnManager.setPresentTime(null);
            TurnManager.setInitialized(false);
            FightTurnPastListener.setPresentTurn(null);
        }
    }

    /**
     * <b>「只挨一次打 → 只出一刀」+「最后一击之后才退出变身」</b>：跑<b>真实回合循环</b>的守卫
     * （2026-10 用户实测的「白厄无敌」+「每挨一次打就放一次【最后一击】」）。
     * <p>
     * 场景与用户那条日志同形：白厄变身（{@code extraTurns = 3}）→ 敌方行动打出致死伤害 →
     * 免死把血锁在 1 → <b>中止变身</b>（时间轴上那 8 条额外回合被摘掉）。
     * <p>
     * 钉住三件事：
     * <ol>
     *     <li><b>退出必须由最后一击自己收口</b>：{@code finalizeAwakenByInterrupt()} 只冻结剩余回合数、
     *     清【弑魂之炽】与 75% 减伤；它<b>不</b>发 {@code AwakenEndEvent}，也不改 {@code awaken}。
     *     最后一击先按攻击 +80% 挥刀，全部结算完才在 {@code LastAttack#comeToEffect} 末尾发事件退出。
     *     这样既不会因为摘掉额外回合而漏掉退出，也不会让最后一击提前失去变身加成；</li>
     *     <li><b>一次挨打只出一刀</b>：那一刀排在"当前回合收尾队列"上，同一圈循环只执行一次
     *     （挨打探针只有 2 点血：一刀 2600 必死。若同一圈排了两刀，各自的倍率会对半砍到 1300、
     *     它反而活着 —— 于是"排了几刀"变成一个能观测的数字）；</li>
     *     <li><b>变身退掉之后不再复发</b>：最后一击末尾的退出协议跑完 {@code isAwaken} 就是 false，
     *     免死不再武装 —— 所以不存在"每挨一次打就再放一次【最后一击】"。</li>
     * </ol>
     * <p>
     * 这条用例能进 {@code src} 靠的是两个<b>自测专用装配入口</b>：
     * {@link cn.gfhnv.game.GameMain#setFightInProgressForSelfTest(boolean)}（回合循环的硬门）
     * 与 {@link Phainon#forSelfTest(Phainon)}（换成不读标准输入的控制器）。
     * <p>
     * ⚠️ <b>它一个数值都没改</b>：{@code LastAttack} 的 {@code 0.125}、下限 {@code 7}、
     * 12 火种、8 个额外回合、75% 减伤全部原样（探针自己的倍率与血量只服务这条用例）。
     *
     * @author AI（DeepSeek）生成
     */
    static void testAwakenInterruptExitProtocol() {
        System.out.println();
        System.out.println("-------- 白厄：最后一击之后才退出变身 + 只挨一次打只反击一次（真实回合循环） --------");
        boolean inFightBefore = cn.gfhnv.game.GameMain.isInFight();
        List<TurnEntry> turnsBefore = new ArrayList<>(TurnManager.getTurns());
        cn.gfhnv.game.officialStuff.customEvent.phainonEvents.AwakeEndListener probeListener = null;
        Phainon awakened = null;
        LivingThing victim = null;
        // 最后一击挥出时记下白厄的状态：awaken 必须还是 true、攻击 +80% 必须还在。
        // 这两个值只在「最后一击真的打中探针」那一刻采，晚采（回合跑完）就区分不出是否提前退出。
        final boolean[] lastAttackSawAwaken = {false};
        final double[] lastAttackAttackEnhance = {Double.NaN};
        try {
            // 免死里会 World.prototypeCopyOf(LastAttack.class)，原型必须在注册表里
            World.getSkillList();

            awakened = Phainon.forSelfTest(new Phainon(125));
            probeListener = new cn.gfhnv.game.officialStuff.customEvent.phainonEvents.AwakeEndListener(awakened);
            // 技能表清空 → 他这一圈既不读标准输入、也不会去动挨打探针（这条用例要打的是"他挨打"）
            awakened.getController().getSkills().clear();
            awakened.setName("变身白厄");
            awakened.setAwaken(true);
            awakened.setExtraTurns(3);
            // 变身那一套数值加成必须一起补上：UltimateAttack 变身时 +0.8 攻击 / +2.7 生命，
            // 退出协议的 AwakeEndListener 结束时会各减回去。只摆 isAwaken 不摆这两项的话，
            // 退出时会把生命加成减成 -2.7 → getHpMax() 变成负数（实测 -6453），免死锁血也救不回来。
            awakened.addPropertyEnhance(PropertyEnhanceType.ATTACK, 0.8, 0, Phainon.AWAKEN_ENHANCE_SOURCE);
            awakened.addPropertyEnhance(PropertyEnhanceType.MAX_HP, 2.7, 0, Phainon.AWAKEN_ENHANCE_SOURCE);

            // 挨打探针只有 2 点血：只要它活过这一圈，就说明"最后一击"被排了不止一次
            victim = new ProbeFragileDummy() {
                @Override
                public void takeDamage(DamageEvent da) {
                    if (da.getAttacker() instanceof Phainon attacker) {
                        lastAttackSawAwaken[0] = attacker.isAwaken();
                        lastAttackAttackEnhance[0] = attacker.getPropertyEnhancePercent(PropertyEnhanceType.ATTACK);
                    }
                    super.takeDamage(da);
                }
            };
            victim.setName("挨打探针");

            List<LivingThing> fighters = new ArrayList<>();
            fighters.add(awakened);
            List<LivingThing> enemies = new ArrayList<>();
            enemies.add(victim);
            Fight fight = new Fight(enemies, new ArrayList<>(), fighters);

            TurnManager.setTurns(new ArrayList<>());
            TurnManager.setPresentTime(BigDecimal.ZERO);
            TurnManager.setInitialized(false);
            // 时间轴：两人都排在 0，探针速度 500 先手（用例要打的是"白厄挨打"，不是"白厄打它"）。
            // ⚠️ 手摆列表时**必须把两边都排进去** —— 只排白厄的话探针永远轮不到，这一圈根本打不起来。
            TurnManager.getTurns().add(new TurnEntry(awakened, BigDecimal.ZERO, BigDecimal.ZERO));
            TurnManager.getTurns().add(new TurnEntry(victim, BigDecimal.ZERO, BigDecimal.ZERO));

            awakened.setHp(1);
            awakened.setSoulscorch(3);
            awakened.setAbsorbDamage(true);
            awakened.addDamageReduction(Phainon.SOULSCORCH_DAMAGE_REDUCTION, 0.75);

            check("中止变身①：开跑前白厄确实在变身中、免死还没被消费、挨打探针还活着",
                    awakened.isAwaken() && !awakened.isPendingLastAttack() && victim.isAlive());

            EventBus.register(probeListener);
            EventBus.register(new ProbeStopAfterOneTurn());

            cn.gfhnv.game.GameMain.setFightInProgressForSelfTest(true);
            FightTurnPastListener driver = new FightTurnPastListener();
            EventBus.register(driver);
            EventBus.post(new cn.gfhnv.game.event.FightPastOneTurnEvent(fight));
        } catch (Exception e) {
            fail("中止变身退出协议测试抛出异常：" + e);
        } finally {
            // 收尾：这一格是回合循环的硬门，不还回去会渗进后面的用例
            cn.gfhnv.game.GameMain.setFightInProgressForSelfTest(inFightBefore);
            EventBus.unregister(probeListener);
            TurnManager.setTurns(turnsBefore);
            TurnManager.setPresentTime(null);
            TurnManager.setInitialized(false);
            FightTurnPastListener.setPresentTurn(null);
        }
        if (awakened == null || victim == null) {
            return;
        }

        // ② 最后一击挥出时还在变身里（吃 +80% 攻击），挥完才由 LastAttack 发事件退出
        check("最后一击②：挥出时仍在变身中、攻击 +80% 还在（不是提前退出）—— 实际 awaken="
                        + lastAttackSawAwaken[0] + "、攻击加成=" + lastAttackAttackEnhance[0],
                lastAttackSawAwaken[0]
                        && Math.abs(lastAttackAttackEnhance[0] - 0.8) < 1e-9);
        check("最后一击②：退出发生在它之后 —— 跑完这一圈时已退掉变身（isAwaken 为 false）",
                !awakened.isAwaken() && awakened.getAwakenExtraTurns().isEmpty());
        check("中止变身②：名字也还原成「白厄」（不是只清了标志）",
                "白厄".equals(awakened.getName()));

        // ③ 一次挨打只出一刀：那把刀砍在 2 点血的挨打探针上，一刀就够；砍两刀的话倍率对半、它反而活着
        check("只挨一次打③：挨打探针被那一刀砍倒（一刀 2600 > 2 血；排了两刀就会对半成 1300，它反而活着）",
                !victim.isAlive());
        check("只挨一次打③：免死标志没有停在 true（停了就是永久锁 1 血 = 白厄无敌）",
                !awakened.isPendingLastAttack());
        check("只挨一次打③：白厄自己也没被打死（免死先把这一击拦在 1 血，"
                        + "再由退出协议按 +25% 回血 —— 所以最终血不是恰好 1）—— 实际 "
                        + awakened.getHp() + " 血",
                awakened.isAlive() && awakened.getHp() > 0);
        check("只挨一次打③：变身已经退掉之后再挨打也不会再排一刀（觉醒状态没了，免死不再武装）",
                !awakened.isAwaken() && !awakened.isPendingLastAttack());
    }

    /**
     * <b>同场两只白厄：一只的 {@code AwakenEndEvent} 不许把另一只的监听器吃掉。</b>
     * <p>
     * {@code AwakenEndListener} 是<b>一次性</b>的（处理完就 {@code EventBus.unregister(this)}），
     * 而 {@code AwakenEndEvent} 是<b>广播</b>事件 —— 两只白厄同时变身时，A 发的事件会把总线上
     * <b>两个</b>监听器都调用一遍。没有归属判断的话两个都会执行退出协议并各自注销，
     * 于是 B 的监听器在没轮到自己时就被"用掉"：<b>B 的变身永不结束</b>
     * （技能表停在觉醒三条、免死无限武装、每挨一次打就重排一记【最后一击】、奖励回合被耗尽）。
     * <p>
     * 钉住三件事：
     * <ol>
     *     <li>对 A 发事件 → 只结束 A，B 仍然在变身（<b>本轮核心守卫</b>）；</li>
     *     <li>之后再对 B 发 → B 也结束（证明 B 的监听器还在总线上）；</li>
     *     <li>同一个白厄：同一个事件走两遍退出协议不会被执行两次
     *     （{@code +25% 血}、{@code tier+1}、排新回合这三处不幂等）。</li>
     * </ol>
     * {@code end()} 里会按速度往时间轴上排一条新回合，所以时间轴要快照 + 还原。
     *
     * @author AI（DeepSeek）生成
     */
    static void testAwakeEndListenerOwnership() {
        System.out.println();
        System.out.println("-------- 白厄变身：同场两只白厄的 AwakeEndEvent 不会互相吃掉监听器 --------");
        List<TurnEntry> turnsBefore = new ArrayList<>(TurnManager.getTurns());
        BigDecimal presentTimeBefore = TurnManager.getPresentTime();
        Phainon first = new Phainon(125);
        Phainon second = new Phainon(125);
        // ③ 单独用一只"干净"的白厄：①② 那两只已经被退出协议改过加成（各减一次 0.8 / 2.7），
        // 再拿它们算 +25% 回血会被变小的 baseHpMax 夹掉，看不出"有没有重复回血"。
        Phainon solo = new Phainon(125);
        first.setName("白厄甲");
        second.setName("白厄乙");
        solo.setName("白厄丙");
        cn.gfhnv.game.officialStuff.customEvent.phainonEvents.AwakeEndListener listenerOfFirst = null;
        cn.gfhnv.game.officialStuff.customEvent.phainonEvents.AwakeEndListener listenerOfSecond = null;
        cn.gfhnv.game.officialStuff.customEvent.phainonEvents.AwakeEndListener listenerOfSolo = null;
        try {
            // 摆成"两只白厄同时变身中"（退出协议会各减回 0.8 攻击 / 2.7 生命，所以加成也要一起摆）
            for (Phainon phainon : List.of(first, second)) {
                phainon.setAwaken(true);
                phainon.setExtraTurns(8);
                phainon.setInterruptedRemainingExtraTurns(-1);
                phainon.addPropertyEnhance(PropertyEnhanceType.ATTACK, 0.8, 0, Phainon.AWAKEN_ENHANCE_SOURCE);
                phainon.addPropertyEnhance(PropertyEnhanceType.MAX_HP, 2.7, 0, Phainon.AWAKEN_ENHANCE_SOURCE);
            }
            first.setSoulscorch(3);
            second.setSoulscorch(5);
            TurnManager.setTurns(new ArrayList<>());
            TurnManager.setPresentTime(BigDecimal.ZERO);

            // 各持一个监听器（= UltimateAttack#comeToEffect 变身时各注册一个），都带上归属
            listenerOfFirst = new cn.gfhnv.game.officialStuff.customEvent.phainonEvents.AwakeEndListener(first);
            listenerOfSecond = new cn.gfhnv.game.officialStuff.customEvent.phainonEvents.AwakeEndListener(second);
            EventBus.register(listenerOfFirst);
            EventBus.register(listenerOfSecond);

            // ① 只对 A 发事件：A 结束、B 一动不动（这条是本轮核心守卫）
            EventBus.post(new cn.gfhnv.game.officialStuff.customEvent.phainonEvents.AwakenEndEvent(first));
            check("两只白厄：对 A 发 AwakenEndEvent 之后，只有 A 退出变身（B 仍在变身）",
                    !first.isAwaken() && second.isAwaken());
            check("两只白厄：B 一个字段都没被动过（名字/额外回合/毁伤/免死标志全保持）",
                    "白厄乙".equals(second.getName())
                            && second.getExtraTurns() == 8
                            && second.getSoulscorch() == 5
                            && !second.isPendingLastAttack());

            // ② 之后再对 B 发：B 的监听器还在总线上（没被 A 的事件吃掉），照样结束
            EventBus.post(new cn.gfhnv.game.officialStuff.customEvent.phainonEvents.AwakenEndEvent(second));
            check("两只白厄：之后再对 B 发 AwakenEndEvent，B 也退出变身（B 的监听器没被 A 的事件吃掉）",
                    !second.isAwaken() && "白厄".equals(second.getName()) && second.getSoulscorch() == 0);

            // ③ 同一个白厄：同一个事件走两遍，退出协议只执行一次（三处不幂等的效果不重复加）
            // ⚠️ 这一只用**新鲜**的白厄，并把生命加成摆成 5.4：退出协议减 2.7 之后还剩 +2.7，
            //    上限不塌（塌了就会把血夹到 0，那正是第二遍执行时的样子 —— 实测 hp=0、baseHpMax=-6453）。
            //    「回血」读的是**减加成之前**的上限（退出协议先把加成减掉、再按旧上限回血），
            //    回完之后再被**新上限**夹一次 —— 期望值按这个顺序算，不然会误判。
            // 5.4 = 变身那份 2.7 + 额外 2.7（后者用不同来源，退出协议只摘变身那份）
            solo.addPropertyEnhance(PropertyEnhanceType.MAX_HP, 2.7, 0, Phainon.AWAKEN_ENHANCE_SOURCE);
            solo.addPropertyEnhance(PropertyEnhanceType.MAX_HP, 2.7, 0, "自测-额外生命加成");
            solo.setAwaken(true);
            solo.setExtraTurns(8);
            listenerOfSolo = new cn.gfhnv.game.officialStuff.customEvent.phainonEvents.AwakeEndListener(solo);
            EventBus.register(listenerOfSolo);
            int extraAbilityTierBefore = solo.getExtraAbilityTier();
            long hpMaxForHeal = solo.getHpMax();
            // 先摆成半血：不然 25% 回血会被上限夹掉，看不出有没有重复加
            solo.setHp(hpMaxForHeal / 2);
            long hpBefore = solo.getHp();
            int turnsBeforeSecondPost = TurnManager.getTurns().size();
            EventBus.post(new cn.gfhnv.game.officialStuff.customEvent.phainonEvents.AwakenEndEvent(solo));
            EventBus.post(new cn.gfhnv.game.officialStuff.customEvent.phainonEvents.AwakenEndEvent(solo));
            long hpMaxAfterEnd = solo.getHpMax();
            long expectedHp = Math.min(hpMaxAfterEnd, hpBefore + (long) (hpMaxForHeal * 0.25));
            check("同一只白厄：同一个事件发两遍，退出协议只执行一次 —— tier 不 += 2、"
                            + "不重复 +25% 血（再执行一次会多回一次血）、不重复排回合 —— 实测 tier "
                            + extraAbilityTierBefore + "→" + solo.getExtraAbilityTier() + "、hp "
                            + hpBefore + "→" + solo.getHp() + "（期望 " + expectedHp + "）",
                    solo.getExtraAbilityTier() == Math.min(2, extraAbilityTierBefore + 1)
                            && solo.getHp() == expectedHp
                            && TurnManager.getTurns().size() == turnsBeforeSecondPost + 1);
        } catch (Exception e) {
            fail("两只白厄的监听器归属测试抛出异常：" + e);
        } finally {
            // 注销必须拿**注册时那一个实例**（EventBus 按对象身份匹配）；已经自我注销过的再注销一次无害
            EventBus.unregister(listenerOfFirst);
            EventBus.unregister(listenerOfSecond);
            EventBus.unregister(listenerOfSolo);
            TurnManager.setTurns(turnsBefore);
            TurnManager.setPresentTime(presentTimeBefore);
        }
    }

    /**
     * 把一组生物的名字拼成一行，供诊断输出用。
     *
     * @param things 生物列表
     * @return 形如 {@code [甲, 乙]} 的字符串
     */
    static String describeNames(List<LivingThing> things) {
        if (things == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < things.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(things.get(i) == null ? "null" : things.get(i).getName());
        }
        return builder.append(']').toString();
    }

    /**
     * 从控制器里按名字取技能实例（取的是控制器自己持有的那一份）。
     *
     * @param controller 控制器
     * @param name       技能名
     * @return 技能；找不到返回 {@code null}
     */
    static Skill skillNamed(FixOrderController controller, String name) {
        for (Skill skill : controller.getSkills()) {
            if (skill != null && name.equals(skill.getName())) {
                return skill;
            }
        }
        return null;
    }

    /**
     * 判断生物身上是否有指定 id 的效果。
     * <p>
     * 比较时会去掉模组前缀：运行时效果的 id 已经被
     * {@link cn.gfhnv.game.world.World#applyRegisteredId(cn.gfhnv.game.effect.Effect)}
     * 补成了完整 id（{@code game_official_content:frozenEffect}），
     * 而用例里写的是短名（{@code frozenEffect}），两种写法都要能匹配上。
     *
     * @param livingThing 生物
     * @param effectId    效果 id（短名或完整 id）
     * @return 是否存在
     */
    static boolean hasEffect(LivingThing livingThing, String effectId) {
        return effectOf(livingThing, effectId) != null;
    }

    /**
     * 取生物身上指定 id 的效果实例。
     *
     * @param livingThing 生物
     * @param effectId    效果 id（短名或完整 id）
     * @return 效果；找不到返回 {@code null}
     */
    static cn.gfhnv.game.effect.Effect effectOf(LivingThing livingThing, String effectId) {
        for (cn.gfhnv.game.effect.Effect effect : livingThing.getEntityEffectList()) {
            if (effect == null || effect.getID() == null) {
                continue;
            }
            String id = effect.getID();
            if (effectId.equalsIgnoreCase(id) || effectId.equalsIgnoreCase(shortIdOf(id))) {
                return effect;
            }
        }
        return null;
    }

    /**
     * 判断生物身上有没有<b>精确</b> id 的效果（用来区分"官方"与"模组"的同名效果）。
     *
     * @param livingThing 生物
     * @param fullId      完整注册表 id
     * @return 是否存在
     */
    static boolean hasEffectExactId(LivingThing livingThing, String fullId) {
        for (cn.gfhnv.game.effect.Effect effect : livingThing.getEntityEffectList()) {
            if (effect != null && fullId.equals(effect.getID())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 取生物身上指定 id 的效果等级。
     *
     * @param livingThing 生物
     * @param effectId    效果 id（短名或完整 id）
     * @return 等级；不存在返回 -1
     */
    static int effectLevelOf(LivingThing livingThing, String effectId) {
        cn.gfhnv.game.effect.Effect effect = effectOf(livingThing, effectId);
        return effect == null ? -1 : effect.getLevel();
    }

    /**
     * 去掉 id 里的模组前缀（{@code game_official_content:xxx} → {@code xxx}）。
     *
     * @param id 完整 id
     * @return 短名
     */
    static String shortIdOf(String id) {
        if (id == null) {
            return "";
        }
        int colon = id.indexOf(':');
        return colon >= 0 && colon + 1 < id.length() ? id.substring(colon + 1) : id;
    }

    /**
     * 数背包里占了几格（每格只要有东西就 +1，不看堆叠数量）。
     *
     * @param livingThing 生物
     * @return 占用的格子数
     */
    static int itemCountOf(LivingThing livingThing) {
        int count = 0;
        for (Slot slot : livingThing.getInventory().getSlots()) {
            if (slot != null && slot.getContainedItem() != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * 数背包里一共有几件物品（把每格的堆叠数加起来）。
     * <p>
     * 同种物品会叠在一格，所以「件数」与「格数」是两个不同的量：
     * 给 6 把剑 → 1 格、6 件。
     *
     * @param livingThing 生物
     * @return 物品件数
     */
    static int unitCountOf(LivingThing livingThing) {
        int count = 0;
        for (Slot slot : livingThing.getInventory().getSlots()) {
            if (slot != null && slot.getContainedItem() != null) {
                count += slot.getContainedItem().getStackNumber();
            }
        }
        return count;
    }

    /**
     * 取背包里第一件物品的堆叠数量。
     *
     * @param livingThing 生物
     * @return 堆叠数量；背包为空返回 0
     */
    static int firstItemStackOf(LivingThing livingThing) {
        Item item = firstItemOf(livingThing);
        return item == null ? 0 : item.getStackNumber();
    }

    /**
     * 取背包里第一件物品（按格子顺序）。
     *
     * @param livingThing 生物
     * @return 物品；背包为空返回 {@code null}
     */
    static Item firstItemOf(LivingThing livingThing) {
        for (Slot slot : livingThing.getInventory().getSlots()) {
            if (slot != null && slot.getContainedItem() != null) {
                return slot.getContainedItem();
            }
        }
        return null;
    }

    /**
     * 取背包里第一件物品的 id。
     *
     * @param livingThing 生物
     * @return 物品 id；背包为空返回空串
     */
    static String firstItemIdOf(LivingThing livingThing) {
        Item item = firstItemOf(livingThing);
        return item == null || item.getId() == null ? "" : item.getId();
    }

    /**
     * 判断背包里有没有<b>精确</b> id 的物品（用来区分"官方"与"模组"的同名物品）。
     *
     * @param livingThing 生物
     * @param fullId      完整注册表 id
     * @return 是否存在
     */
    static boolean hasItemId(LivingThing livingThing, String fullId) {
        for (Slot slot : livingThing.getInventory().getSlots()) {
            Item item = slot == null ? null : slot.getContainedItem();
            if (item != null && fullId.equals(item.getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从物品注册表里按「完整 id / 短名 / 简单类名」找模板（大小写不敏感）。
     *
     * @param name 物品名
     * @return 模板；找不到返回 {@code null}
     */
    static Item itemTemplateOf(String name) {
        for (Item item : World.getItemList()) {
            if (item == null || item.getId() == null) {
                continue;
            }
            if (item.getId().equalsIgnoreCase(name)
                    || shortIdOf(item.getId()).equalsIgnoreCase(name)
                    || item.getClass().getSimpleName().equalsIgnoreCase(name)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 从效果注册表里取指定 id 的模板。
     *
     * @param effectId 效果 id
     * @return 模板；找不到返回 {@code null}
     */
    static cn.gfhnv.game.effect.Effect templateOf(String effectId) {
        for (cn.gfhnv.game.effect.Effect effect : cn.gfhnv.game.world.World.getEffectList()) {
            if (effect == null) {
                continue;
            }
            String id = effect.getID() == null ? "" : effect.getID();
            // 注册进 World 的 id 带模组前缀（game_official_content:frozenEffect），
            // 所以除了全等，还要按「冒号后的后半段」与简单类名各匹配一次。
            int colon = id.indexOf(':');
            String shortId = colon >= 0 && colon + 1 < id.length() ? id.substring(colon + 1) : id;
            if (effectId.equalsIgnoreCase(id) || effectId.equalsIgnoreCase(shortId)
                    || effectId.equalsIgnoreCase(effect.getClass().getSimpleName())) {
                return effect;
            }
        }
        return null;
    }

    /**
     * 描述一个命令树节点（诊断用）。
     *
     * @param node 节点，可为 {@code null}
     * @return 可读描述
     */
    static String describeNode(cn.gfhnv.game.system.command.CommandNode node) {
        if (node == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(node.getClass().getSimpleName())
                .append("(name=").append(node.getName()).append(')');
        builder.append(" usage=").append(node.getUsageText());
        builder.append(" 可执行=").append(node.isExecutable());
        builder.append(" 子节点=").append(node.getChildrenNames());
        return builder.toString();
    }

    /**
     * 诊断：不经过任何官方命令，直接用构建器搭一棵 {@code test <甲> <乙>} 的树，
     * 看嵌套的那一层是否真的挂上去了。
     * <p>
     * 这是「最小复现」：如果这里正常而官方命令不正常，问题就出在命令类<b>怎么用</b>构建器；
     * 如果这里也不正常，问题就在构建器本身。
     */
    static void diagnoseBuilder() {
        System.out.println("  --- 构建器诊断（最小复现）---");
        cn.gfhnv.game.system.command.CommandDispatcher.setDebugParsing(true);
        try {
            // 最小复现：test <甲> <乙>
            // 关键：一层一个变量 —— argumentBuilder(...) 建出「甲」，
            // 再对它调 .argument("乙") 就把「乙」挂到「甲」下（返回值是新建出来的子节点）。
            cn.gfhnv.game.system.command.ArgumentBuilder child =
                    cn.gfhnv.game.system.command.ArgumentBuilder.argumentBuilder(
                            "甲", cn.gfhnv.game.system.command.WordArgumentType.word());
            cn.gfhnv.game.system.command.ArgumentBuilder grand =
                    child.argument("乙", cn.gfhnv.game.system.command.IntegerArgumentType.integer());

            // 构建器本身就是节点，直接当作树的根来检查
            cn.gfhnv.game.system.command.CommandNode rootNode = child;
            System.out.println("      最小复现的完整树（从根往下）：");
            dumpTree(rootNode, 6);

            // 断言：这条链必须建出 甲 → 乙 两层
            cn.gfhnv.game.system.command.CommandNode inner = rootNode.getChild("乙");
            check("构建器：甲节点下应挂着「乙」", inner != null);
            check("构建器：乙节点的父节点应是「甲」",
                    inner != null && inner.getParent() != null && "甲".equals(inner.getParent().getName()));
            check("构建器：链式结果的类型就是节点", rootNode instanceof cn.gfhnv.game.system.command.ArgumentBuilder);
            check("构建器：乙分支也已建出（不是懒加载）", grand != null && grand == inner);

            // 断言：官方 hurt 命令的树必须是 hurt → 目标 → 数值
            cn.gfhnv.game.system.command.CommandNode hurtNode =
                    CommandManager.getDispatcher().getCommandNode("hurt");
            check("官方命令：hurt 下应挂着「目标」",
                    hurtNode != null && hurtNode.getChildrenNames().contains("目标"));
            cn.gfhnv.game.system.command.CommandNode targetNode =
                    hurtNode == null ? null : hurtNode.getChild("目标");
            check("官方命令：「目标」下应挂着「数值」",
                    targetNode != null && targetNode.getChildrenNames().contains("数值"));

            // 断言：官方 effect 命令的树必须是 effect → 目标 → add/remove/list
            cn.gfhnv.game.system.command.CommandNode effectNode =
                    CommandManager.getDispatcher().getCommandNode("effect");
            check("官方命令：effect 下应挂着「目标」",
                    effectNode != null && effectNode.getChildrenNames().contains("目标"));
            cn.gfhnv.game.system.command.CommandNode effectTarget =
                    effectNode == null ? null : effectNode.getChild("目标");
            check("官方命令：effect 的「目标」下应挂着 add、remove、list",
                    effectTarget != null
                            && effectTarget.getChild("add") != null
                            && effectTarget.getChild("remove") != null
                            && effectTarget.getChild("list") != null);
            check("官方命令：effect 的「目标/list」应可执行",
                    effectTarget != null && effectTarget.getChild("list") != null
                            && effectTarget.getChild("list").isExecutable());
            check("官方命令：effect 的「目标/add」下应挂着「效果」",
                    effectTarget != null && effectTarget.getChild("add") != null
                            && effectTarget.getChild("add").getChild("效果") != null);
            check("官方命令：effect 的「效果」下应挂着「等级」",
                    effectTarget != null && effectTarget.getChild("add") != null
                            && effectTarget.getChild("add").getChild("效果") != null
                            && effectTarget.getChild("add").getChild("效果").getChild("等级") != null);
        } catch (Exception e) {
            System.out.println("      构建器诊断异常：" + e);
        } finally {
            cn.gfhnv.game.system.command.CommandDispatcher.setDebugParsing(false);
        }
    }

    /**
     * 递归打印一棵命令树（诊断用）。
     *
     * @param node   根
     * @param indent 缩进空格数
     */
    static void dumpTree(cn.gfhnv.game.system.command.CommandNode node, int indent) {
        StringBuilder pad = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            pad.append(' ');
        }
        System.out.println("      " + pad + "└─ " + describeNode(node));
        for (cn.gfhnv.game.system.command.CommandNode child : node.getChildren()) {
            dumpTree(child, indent + 4);
        }
    }

    /**
     * 诊断：直接走一次调度器的「解析」阶段，然后直接查上下文里的参数。
     * <p>
     * 它把「解析出节点」与「取参数」两件事分开报告，
     * 这样就能区分「参数没解析出来」和「参数解析了但取不到」两种情况。
     * 注意：本方法不执行命令，因此不会改动任何生物。
     *
     * @param command 命令文本
     */
    static void diagnoseParse(String command) {
        System.out.println("  --- 解析诊断：" + command + " ---");
        cn.gfhnv.game.system.command.CommandDispatcher dispatcher = CommandManager.getDispatcher();
        cn.gfhnv.game.system.command.CommandSource source =
                new cn.gfhnv.game.system.command.CommandSource(CommandManager.getPlayer());
        cn.gfhnv.game.system.command.CommandDispatcher.setDebugParsing(true);
        try {
            cn.gfhnv.game.system.command.CommandNode node = dispatcher.parse(command, source);
            System.out.println("      解析结果节点：" + describeNode(node));
            System.out.println("      节点用法：" + node.getFullUsage());
            System.out.println("      解析出的参数：" + source.describeArguments());
        } catch (Exception e) {
            System.out.println("      解析失败：" + e.getMessage());
            System.out.println("      此时参数表：" + source.describeArguments());
        } finally {
            cn.gfhnv.game.system.command.CommandDispatcher.setDebugParsing(false);
        }
    }

    /**
     * 数一个类里「参数个数为 count」的公共构造函数有几个。
     * <p>
     * 用来守护「同一个数字不会同时匹配两个构造函数」这条约定：
     * 通用效果里的百分比 / 固定值已经合并成 {@code (double percent, long amount, int lastTime)}，
     * 所以这类效果应当只有 1 个双参数构造器（只给百分比）和 1 个三参数构造器（百分比 + 固定值）。
     *
     * @param type  类
     * @param count 参数个数
     * @return 个数
     */
    static int countConstructors(Class<?> type, int count) {
        int found = 0;
        for (java.lang.reflect.Constructor<?> constructor : type.getConstructors()) {
            if (constructor.getParameterCount() == count) {
                found++;
            }
        }
        return found;
    }

    /**
     * @param owner 目标类
     * @return {@code owner} <b>自己声明</b>的标量实例字段（跳过 static、容器与各种引用）
     */
    static List<java.lang.reflect.Field> scalarFieldsOf(Class<?> owner) {
        List<java.lang.reflect.Field> result = new ArrayList<>();
        for (java.lang.reflect.Field field : owner.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            if (isScalarType(field.getType())) result.add(field);
        }
        return result;
    }

    /**
     * 实体身上的全部"标量属性槽位"：{@code LivingThing} 自己声明的 + 各 {@link DataFlatten} 组件里的。
     * <p>
     * 属性搬进组件之后，字段的"声明位置"从实体变成了组件 —— 但本测试要盯的是
     * "<b>属性有没有被复制 / 被复位</b>"，跟它住在哪个类里无关。少了这一步，
     * 搬走的字段会从扫描里静默消失，契约断言就会退化成"永远通过"（假绿）。
     * <p>
     * 范围刻意与 {@code LivingThing} 的字段清单一致：父类 {@code Entity} 的
     * {@code uuid} / {@code mass} 不在本契约里（{@code uuid} 本来就是 final、副本刻意换新的），
     * 顺手扫进来会变成两条假的"漏复制"。
     *
     * @param entity 实体实例
     * @return 每个标量属性一个槽位（顺序：实体自己的在前，然后是各组件）
     */
    static List<ScalarSlot> scalarSlotsOf(LivingThing entity) {
        List<ScalarSlot> result = new ArrayList<>();
        for (java.lang.reflect.Field field : scalarFieldsOf(LivingThing.class)) {
            result.add(new ScalarSlot(entity, field));
        }
        for (Class<?> current = entity.getClass();
             current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (java.lang.reflect.Field field : current.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                if (!field.isAnnotationPresent(DataFlatten.class)) continue;
                if (isScalarType(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object component = field.get(entity);
                    if (component == null) continue;
                    for (java.lang.reflect.Field inner : scalarFieldsOf(component.getClass())) {
                        result.add(new ScalarSlot(component, inner));
                    }
                } catch (ReflectiveOperationException | RuntimeException e) {
                    check("反射应当能读到组件「" + field.getType().getSimpleName() + "」，实际抛了：" + e, false);
                }
            }
        }
        return result;
    }

    /**
     * @param type 字段类型
     * @return 是否属于"标量"（能被反射直接填探测值、也是漏复制缺陷的实际发生地）
     */
    static boolean isScalarType(Class<?> type) {
        return type == double.class || type == long.class || type == int.class
                || type == boolean.class || type == String.class || type.isEnum();
    }

    /**
     * @param value 字段值
     * @return 是否是该类型的"默认值"（临时属性复位之后应该长成的样子）
     */
    static boolean isDefaultValue(Object value) {
        if (value instanceof Double d) return d == 0d;
        if (value instanceof Long l) return l == 0L;
        if (value instanceof Integer i) return i == 0;
        if (value instanceof Boolean b) return !b;
        return value == null;
    }

    /**
     * 给标量字段挑一个"和默认值不可能撞车"的探测值。
     * <p>
     * 值本身不重要，<b>关键是它不能等于字段的默认值</b> —— 否则漏复制的字段会因为
     * "两边都是默认值"而假通过。所以这里用一组一眼能认出来的常量。
     *
     * @param type 字段类型
     * @return 探测值
     */
    static Object probeValueFor(Class<?> type) {
        if (type == double.class) return 7.5d;
        if (type == long.class) return 4321L;
        if (type == int.class) return 4321;
        if (type == boolean.class) return true;
        if (type == String.class) return "契约探针";
        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            return constants.length > 1 ? constants[1] : constants[0];
        }
        return null;
    }

    /**
     * {@code target} 上是否挂着一个"能接住这个字段"的 setter。
     * <p>
     * 判据与 {@code DataBridge#trySetter} 完全一致：方法名 = {@code "set" + 字段名（首字母大写）}、
     * 只收一个参数、参数类型与字段类型相同。
     *
     * @param target 目标类（必须是字段所在类本身或它的子类 —— setter 都是 public 继承可见的）
     * @param field  字段
     * @return 有就返回 {@code true}
     */
    static boolean hasMatchingSetter(Class<?> target, java.lang.reflect.Field field) {
        String expected = "set" + Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
        for (java.lang.reflect.Method method : target.getMethods()) {
            if (method.getName().equals(expected)
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == field.getType()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 【燃点】上限修正链（{@link cn.gfhnv.game.interfaces.IModifyIgnitionMax}）—— 2026-10-03 新增的模组扩展点。
     * <p>
     * 模组「李晓焰加强」（{@code mods/liXiaoYanPlus}）就是靠它提高上限的，而 {@code mods/} 不参与自测编译，
     * 所以<b>这一步必须把"链本身"钉死</b>：① 链为空时数值与出厂值逐位相同（不能因为加了扩展点就变数值）；
     * ② 登记一个修正器之后，上限、{@code setIgnition} 的夹取、副本三者同时跟着变；
     * ③ 链是折叠语义（后一个拿前一个的结果）；④ 摘掉/清空之后回到出厂值。
     */
    static void testIgnitionMaxModifierChain() {
        section("李晓焰：燃点上限修正链（模组扩展点）");

        ActorLiXiaoYan li = new ActorLiXiaoYan(125);
        int baseNormal = ActorLiXiaoYan.Defaults.IGNITION_MAX;
        int baseLow = ActorLiXiaoYan.Defaults.LOW_HP_IGNITION_MAX;

        // ① 出厂状态：链是空的，数值必须与"没有这个扩展点"时逐位相同
        check("燃点链：出厂时链是空的", ActorLiXiaoYan.getIgnitionMaxModifiers().isEmpty());
        li.setHp(li.getHpMax());
        check("燃点链：链为空时满血上限逐位等于出厂值（" + baseNormal + "）",
                li.getIgnitionMax() == baseNormal);
        li.setHp(li.getHpMax() / 4);
        check("燃点链：链为空时低血上限逐位等于出厂值（" + baseLow + "）",
                li.getIgnitionMax() == baseLow);

        // ② 登记一个 +5 的修正器：满血与低血两条分支都要跟着涨
        IModifyIgnitionMax plusFive =
                (baseMax, owner) -> baseMax + 5;
        ActorLiXiaoYan.addIgnitionMaxModifier(plusFive);
        check("燃点链：登记 null 与 DEFAULT 会被忽略（链长仍是 1）", ignoredModifiersAreRejected());
        check("燃点链：低血上限 = 出厂值 + 5（" + (baseLow + 5) + "）",
                li.getIgnitionMax() == baseLow + 5);
        li.setHp(li.getHpMax());
        check("燃点链：满血上限 = 出厂值 + 5（" + (baseNormal + 5) + "）",
                li.getIgnitionMax() == baseNormal + 5);
        li.setIgnition(9999);
        check("燃点链：setIgnition 夹的是修正后的上限（不是出厂值）",
                li.getIgnition() == baseNormal + 5);
        li.setHp(li.getHpMax() / 4);
        check("燃点链：副本也看得到链（静态链不用重新登记、copy() 不会漏）",
                ((ActorLiXiaoYan) li.copy()).getIgnitionMax() == baseLow + 5);

        // ③ 折叠语义：第二个修正器拿到的是第一个的返回值（(出厂值 + 5) × 2，不是 出厂值 × 2 + 5）
        IModifyIgnitionMax doubleIt = (baseMax, owner) -> baseMax * 2;
        ActorLiXiaoYan.addIgnitionMaxModifier(doubleIt);
        check("燃点链：两个修正器按登记顺序折叠（(低血上限 + 5) × 2 = " + ((baseLow + 5) * 2) + "）",
                li.getIgnitionMax() == (baseLow + 5) * 2);
        check("燃点链：修正器拿得到 owner（就是正在读上限的那个角色）",
                ownerPassedToModifierIsTheReader());
        check("燃点链：读上限不会把修正器消费掉（重复读结果一样）",
                li.getIgnitionMax() == (baseLow + 5) * 2 && li.getIgnitionMax() == (baseLow + 5) * 2);

        // ④ 摘掉与清空：必须回到出厂值，否则后面的用例看到的是被污染的角色
        check("燃点链：按对象身份摘掉第一个修正器", ActorLiXiaoYan.removeIgnitionMaxModifier(plusFive));
        check("燃点链：摘掉之后只剩 ×2（" + (baseLow * 2) + "）", li.getIgnitionMax() == baseLow * 2);
        check("燃点链：同一个修正器摘第二次返回 false（没有静默重复摘）",
                !ActorLiXiaoYan.removeIgnitionMaxModifier(plusFive));
        ActorLiXiaoYan.clearIgnitionMaxModifiers();
        check("燃点链收尾：清空之后链是空的，上限回到出厂值（后面用例看到干净状态）",
                ActorLiXiaoYan.getIgnitionMaxModifiers().isEmpty() && li.getIgnitionMax() == baseLow);
    }

    /**
     * @return {@code null} 与 {@link IModifyIgnitionMax#DEFAULT} 是不是都登记不进去
     */
    static boolean ignoredModifiersAreRejected() {
        int before = ActorLiXiaoYan.getIgnitionMaxModifiers().size();
        ActorLiXiaoYan.addIgnitionMaxModifier(null);
        ActorLiXiaoYan.addIgnitionMaxModifier(IModifyIgnitionMax.DEFAULT);
        return ActorLiXiaoYan.getIgnitionMaxModifiers().size() == before;
    }

    /**
     * @return 修正器收到的 {@code owner} 是不是就是读上限的那个角色实例
     */
    static boolean ownerPassedToModifierIsTheReader() {
        ActorLiXiaoYan probe = new ActorLiXiaoYan(125);
        List<ActorLiXiaoYan> seen = new ArrayList<>();
        IModifyIgnitionMax recorder = (baseMax, owner) -> {
            if (owner instanceof ActorLiXiaoYan actor) {
                seen.add(actor);
            }
            return baseMax;
        };
        ActorLiXiaoYan.addIgnitionMaxModifier(recorder);
        try {
            probe.getIgnitionMax();
            return seen.size() == 1 && seen.get(0) == probe;
        } finally {
            ActorLiXiaoYan.removeIgnitionMaxModifier(recorder);
        }
    }

    /**
     * <b>尸体清理</b>：{@code allEntities} 要摘掉死者（{@code /list} 与 {@code @e} 读的就是这条），
     * 而战斗逻辑读的 {@code fighterList} / {@code enemiesList} <b>不动</b>。
     * <p>
     * 不清的话，打久了（尤其盗火行者那种一直召唤容器的 BOSS）{@code /list} 里全是死人和残骸。
     */
    static void testDeadRemovedFromAllEntities() {
        section("尸体清理：allEntities 摘掉死者，fighterList 不动");

        LivingThing aliveProbe =
                new cn.gfhnv.game.officialStuff.customEntity.players.PlayerOne(100).copy();
        LivingThing deadProbe =
                new cn.gfhnv.game.officialStuff.customEntity.monsters.CommonInsect(100L).copy();

        java.util.List<LivingThing> fighters = new java.util.ArrayList<>();
        fighters.add(aliveProbe);
        java.util.List<LivingThing> enemies = new java.util.ArrayList<>();
        enemies.add(deadProbe);
        cn.gfhnv.game.system.fight.Fight fight =
                new cn.gfhnv.game.system.fight.Fight(enemies, new java.util.ArrayList<>(), fighters);
        fight.setAllEntities(new java.util.ArrayList<>(java.util.List.of(aliveProbe, deadProbe)));

        // ⚠️ 必须把血打到 0，不能只 setAlive(false)：isAlive() 里"hp > 0 就强制 alive = true"，
        //    直接设标志会被下一次 isAlive() 覆盖回去。
        deadProbe.setHp(0);
        int removed = fight.removeDeadFromAllEntities();
        check("allEntities：死者被摘掉（返回摘掉了 " + removed + " 个）",
                removed == 1 && fight.getAllEntities().size() == 1);
        check("allEntities：留下的是活的那个", fight.getAllEntities().contains(aliveProbe));
        check("fighterList / enemiesList **不动**（那是战斗逻辑在读的，半路摘人会改变技能打谁）",
                fight.getFighterList().contains(aliveProbe)
                        && fight.getEnemiesList().contains(deadProbe));
    }

    static void testIndividualMultipleAreaChain() {
        section("单体伤害倍率：多来源乘数（与减伤同形）");

        cn.gfhnv.game.entity.LivingThing probe =
                new cn.gfhnv.game.officialStuff.customEntity.players.PlayerOne(100).copy();
        probe.setIndividualMultipleArea(2.0);

        Object sourceA = new Object();
        Object sourceB = new Object();

        check("乘区①：没有来源时 = 面板基础值（行为与以前逐位相同）",
                Math.abs(probe.getIndividualMultipleArea() - 2.0) < 1e-9);

        probe.addIndividualMultipleArea(sourceA, 1.5);
        check("乘区②：挂一个来源 → 基础 × 1.5 —— 实际 " + probe.getIndividualMultipleArea(),
                Math.abs(probe.getIndividualMultipleArea() - 3.0) < 1e-9);

        probe.addIndividualMultipleArea(sourceB, 2.0);
        check("乘区③：两个来源是**连乘**（2.0 × 1.5 × 2.0 = 6.0，不是相加的 5.5）—— 实际 "
                        + probe.getIndividualMultipleArea(),
                Math.abs(probe.getIndividualMultipleArea() - 6.0) < 1e-9);

        probe.addIndividualMultipleArea(sourceA, 0.5);
        check("乘区④：同一个来源再挂一次是**覆盖**（不是叠加）—— 实际 "
                        + probe.getIndividualMultipleArea(),
                Math.abs(probe.getIndividualMultipleArea() - 2.0) < 1e-9);

        // ⚠️ 先摘再拼消息：直接写在 check 的两个参数里的话，消息会在 remove 之前算出来，
        //    失败时打出的"实际"是**删除前**的值 —— 那种消息比没有还坑。
        boolean removedB = probe.removeIndividualMultipleArea(sourceB);
        check("乘区⑤：能按来源摘掉其中一个（2.0 × 0.5 = 1.0）—— 实际 "
                        + probe.getIndividualMultipleArea(),
                removedB && Math.abs(probe.getIndividualMultipleArea() - 1.0) < 1e-9);

        probe.addIndividualMultipleArea(sourceB, 3.0);
        probe.clearIndividualMultipleAreaModifiers();
        check("乘区⑥：clear 之后只剩面板基础值",
                Math.abs(probe.getIndividualMultipleArea() - 2.0) < 1e-9);

        probe.addIndividualMultipleArea(sourceA, 4.0);
        probe.whenFightEnds();
        check("乘区⑦：whenFightEnds() 清掉**来源乘数**，但面板基础值不清（清了就是永久削弱角色）"
                        + "—— 实际 " + probe.getIndividualMultipleArea(),
                Math.abs(probe.getIndividualMultipleArea() - 2.0) < 1e-9);

        // 来源乘数跟着副本走（复制构造器原来 `addAll` 那一行就是唯一保证）——
        // 那张表搬进属性组件之后这条要一起跟过去，否则副本会丢掉"挂着的那几层加成"。
        probe.addIndividualMultipleArea(sourceA, 1.5);
        LivingThing areaCopy = probe.copy();
        check("乘区⑧：来源乘数**跟着副本走**（copy() 之后副本的生效值也含这层加成）—— 实际 "
                        + areaCopy.getIndividualMultipleArea(),
                Math.abs(areaCopy.getIndividualMultipleArea() - 3.0) < 1e-9);
    }

    static void testSelectBatchPure() {
        section("选择状态机（纯函数级：不读标准输入）");

        // 用一个不依赖真实实体的候选表：状态机只认"下标 → 候选"，与候选是什么无关
        String[] candidates = {"甲", "乙", "丙", "丁", "戊", "己"};

        // ① 单个数字 + y：老行为（数字后面紧跟的 y 确认的就是它）
        var draft = selectBatch(candidates, "1", "y");
        check("① 纯：1 之后紧跟 y 就确认了（不是「要先 next 才确认」）", draft.confirmed());
        check("① 纯：确认的就是下标 1 那一个 —— 实际 " + draft.things(),
                draft.things().equals(List.of("乙")));

        // ② 重复下标（同一行里写三遍）：**允许重复** —— 写几遍就加几个
        draft = selectBatch(candidates, "2/2/2/y");
        check("② 纯：同一行里同一个下标写三遍 = 加三个（允许重复选同一个目标）—— 实际 " + draft.things(),
                draft.things().equals(List.of("丙", "丙", "丙")));
        check("② 纯：账本记的是下标（Set 会折叠成一个 2，但那只是账本，不再影响重复加入）",
                draft.chosen().equals(java.util.Set.of(2)));

        // ②′ 一行数字选完就进「等确认」，此时再敲数字是非法输入 —— 所以"跨行重复"到不了选人这一步
        draft = selectBatch(candidates, "3", "3", "y");
        check("②′ 纯：确认阶段再敲数字 = 输入错误，不会再添一个 —— 实际 " + draft.things(),
                draft.things().equals(List.of("丁")));

        // ③ 越界 / 负数只跳过那一个，同一行里其它下标照旧生效
        draft = selectBatch(candidates, "1/99/-1/3/y");
        check("③ 纯：越界与负数各跳一个，1 与 3 照旧选上 —— 实际 " + draft.things(),
                draft.confirmed() && draft.things().equals(List.of("乙", "丁")));

        // ④ 非数字整段作废（与老版本一致），不影响后面的段
        draft = selectBatch(candidates, "abc", "4/y");
        check("④ 纯：一段混进非数字就整段作废，下一行照旧能选 —— 实际 " + draft.things(),
                draft.confirmed() && draft.things().equals(List.of("戊")));

        // ⑤ 收口的两种写法：单个 n / next = 「跳过确认」（两个阶段都是它）；
        //    写全的 no = 确认阶段放弃这一批
        draft = selectBatch(candidates, "1", "n");
        check("⑤ 纯：确认阶段的单个 n 是「跳过确认」（老代码里 n 先匹配 next）—— 不加入、不报错",
                !draft.confirmed() && draft.things().isEmpty());
        draft = selectBatch(candidates, "1", "no");
        check("⑤ 纯：确认阶段写全的 no 是「放弃这一批」", !draft.confirmed()
                && draft.things().isEmpty());
        draft = selectBatch(candidates, "n");
        check("⑤ 纯：选择阶段的 n 是 next（这一批不加入、也不报错）",
                !draft.confirmed() && draft.things().isEmpty());

        // ⑥ 多余段丢弃：y 之后同一行剩下的段不许"隔一轮突然生效"
        draft = selectBatch(candidates, "1/y/5");
        check("⑥ 纯：y 之后的 5 被丢掉 —— 实际 " + draft.things(),
                draft.things().equals(List.of("乙")));

        // ⑦ 空批：next 收口，什么都不选也不是错误
        draft = selectBatch(candidates, "next");
        check("⑦ 纯：空选择收口（不确认、没有东西）",
                !draft.confirmed() && draft.things().isEmpty());

        // ⑧ YES 全大写 / yes 全拼照旧（老写法不能被缩写改写坏）
        draft = selectBatch(candidates, "1", "YES");
        check("⑧ 纯：YES 全大写照旧确认",
                draft.confirmed() && draft.things().equals(List.of("乙")));
    }

    /**
     * <b>不读标准输入</b>地跑一次选择状态机：输入行从 {@code lines} 里取，取完就由
     * {@link cn.gfhnv.game.GameMain#scriptedInputReaderForSelfTest} 那条装配入口抛
     * {@link GameMain.ScriptExhausted}（<b>不是</b> {@code NoSuchElementException}：
     * 这里把它当成"输入到此为止"，于是脚本写短了只会红一条，不会把自测挂住）。
     *
     * @param candidates 候选表
     * @param lines      依次喂进去的输入行
     * @return 这一批的结果（选好的副本 + 已经选过的下标 + 是否被确认）
     */
    static cn.gfhnv.game.GameMain.SelectionFlow.Draft<String> selectBatch(
            String[] candidates, String... lines) {
        java.util.Deque<String> scripted = new java.util.ArrayDeque<>(Arrays.asList(lines));
        try {
            var reader = cn.gfhnv.game.GameMain.scriptedInputReaderForSelfTest(() -> {
                String line = scripted.pollFirst();
                if (line == null) {
                    // ⚠️ 绝不返回 null（那是违反约定）：用 IllegalStateException 让"脚本用完"
                    //    能在这里被认出来，而不是变成"上层的 bug"被吞掉
                    throw new IllegalStateException("纯函数用例的脚本用完");
                }
                return line;
            });
            return cn.gfhnv.game.GameMain.selectionFlowForSelfTest()
                    .selectBatch(reader, "角色", candidates, s -> s, s -> "介绍-" + s);
        } catch (IllegalStateException e) {
            // 输入到此为止：这一批还没等到确认（与端到端那条"脚本用完 = 正常收尾"同一个口径）
            return new cn.gfhnv.game.GameMain.SelectionFlow.Draft<>(
                    List.of(), java.util.Set.of(), false);
        }
    }

    /**
     * 用一段脚本化的输入行跑一遍 {@link cn.gfhnv.game.GameMain#startAFight()}，把它的输出抓回来。
     * <p>
     * ⚠️ 一律走"选完三个阶段就返回"那条自测专用开关：真进战斗就会开始跑回合循环。
     * <p>
     * <b>脚本用完 = 正常收尾，不是失败</b>：{@code startAFight} 的外层循环是
     * "选到角色与对手都非空为止"，正常游戏靠无限的标准输入收口；脚本是有尽头的，
     * 读到尽头时 {@link cn.gfhnv.game.GameMain.InputReader} 会抛
     * {@code NoSuchElementException}（带 {@link cn.gfhnv.game.GameMain#SCRIPT_EXHAUSTED_MARKER} 记号）。
     * 这里把带记号的那一种**当成"这一局跑到底了"**、把输出照常返回给断言；
     * <b>不带记号的异常仍然是失败</b>（那是别处漏出来的 EOF）。
     * <p>
     * ⚠️ 所以脚本最后要故意多留一行（多喂一个 {@code next}）：不留的话，
     * "三段都收口"那一步就会以异常的形式结束，断言看到的输出会缺最后几行。
     * <p>
     * 输出只保留前 {@value #MAX_CAPTURED_LINES} 行：脚本用完而外层还在循环时，日志会瞬间涨到
     * 上千万行（把自测的内存打爆），断言要看的都是前面那些行。
     *
     * @param lines 依次喂进去的输入行
     * @return 这一段流程打印的前若干行（可能被截断）
     */
    static String startAFightWithInput(String... lines) {
        java.util.Deque<String> scripted = new java.util.ArrayDeque<>(Arrays.asList(lines));
        cn.gfhnv.game.GameMain.setInputSourceForSelfTest(() -> {
            String line = scripted.pollFirst();
            if (line == null) {
                // ⚠️ 绝不返回 null：那会被当成"输入流结束"，上层会一直重来 → 自测空转成假死
                throw new java.util.NoSuchElementException(
                        cn.gfhnv.game.GameMain.SCRIPT_EXHAUSTED_MARKER + "自测脚本用完了");
            }
            return line;
        }, true);
        java.io.ByteArrayOutputStream sink = new java.io.ByteArrayOutputStream();
        java.io.PrintStream original = System.out;
        java.io.PrintStream capturing = null;
        try {
            capturing = new java.io.PrintStream(new java.io.OutputStream() {
                int lineCount = 0;

                @Override
                public void write(int b) {
                    if (lineCount >= MAX_CAPTURED_LINES) {
                        return;
                    }
                    sink.write(b);
                    if (b == '\n') {
                        lineCount++;
                    }
                }
            }, true, "UTF-8");
            System.setOut(capturing);
            cn.gfhnv.game.GameMain.startAFight();
        } catch (RuntimeException e) {
            // 「剧本用完」是**测试自己喂的行不够**，不是被测代码的失败。
            // ⚠️ 以前这里只 catch IllegalStateException，而输入脚本在另一条路径上抛的是
            //    NoSuchElementException（消息同样是 SCRIPT_EXHAUSTED_MARKER）—— 于是那两处
            //    被误记成失败；又因为 System.out 被劫持去捕获游戏输出，连 [FAIL] 都没进日志，
            //    只在汇总里剩下"失败 2 条"。现在按**消息**判，两种异常一视同仁。
            boolean scriptExhausted = String.valueOf(e.getMessage())
                    .startsWith(cn.gfhnv.game.GameMain.SCRIPT_EXHAUSTED_MARKER);
            if (!scriptExhausted) {
                fail("startAFight 抛出异常：" + e);
            }
        } catch (Exception e) {
            fail("startAFight 抛出异常：" + e);
        } finally {
            // ⚠️ 必须先 flush 再还原：这个包装流自己带缓冲区（autoflush 只对 println 生效），
            //    不 flush 就会丢掉尾巴上几行 —— 实测"确认提示 / 已选"那几行就是这么消失的，
            //    断言会以"少了最后几行"的形式假红。
            if (capturing != null) {
                capturing.flush();
            }
            System.setOut(original);
        }
        // ⚠️ 必须整段按 UTF-8 解码：按字节逐个拼字符串会把中文拆成乱码（断言会全部落空）
        return sink.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * 数一段文本里出现了几次某个词（用来钉"重复下标只算一次""选了两次"这类事）。
     *
     * @param text 文本
     * @param word 要找的词
     * @return 出现次数
     */
    static int countOf(String text, String word) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(word, from);
            if (at < 0) {
                return count;
            }
            count++;
            from = at + word.length();
        }
    }

    /**
     * 数「确认了这一批」出现了几次：{@code 已选:名字} 算一次，但<b>不算</b>「放弃已选:名字」那一句。
     * <p>
     * <b>为什么要减一次</b>：放弃那句打的是 {@code 放弃已选:…}，它<b>包含</b> {@code 已选:…} 这个子串 ——
     * 直接用 {@link #countOf} 会把「放弃」也算成「确认」。而"这一批到底确认了几次"是选人这组用例的
     * 核心断言（去重、多余段丢弃、放弃后重选都靠它），数错就会把对的代码判成错的。
     * <p>
     * 顺带说明：确认行打的是 {@code 已选:名字}，而"这一行选完了、等你确认"那一步打的是
     * {@code 已选角色:名字} —— 两者只在<b>不同阶段</b>出现，所以断言确认次数必须认 {@code 已选:}。
     *
     * @param text 文本
     * @param name 那一批里的第一个名字
     * @return 确认次数（放弃不算）
     */
    static int countConfirmed(String text, String name) {
        int count = 0;
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            // 只认「已选:」开头的确认行，并且这一行里出现了这个名字。
            // 这样批量确认（已选:玩家一、冰虫子）与单个确认（已选:冰虫子）都算数，
            // 而「放弃已选:…」与「已选角色:…」都不会被误数进来。
            if (trimmed.startsWith("已选:") && trimmed.contains(name)) {
                count++;
            }
        }
        if (count == 0) {
            // 没数到就把实际出现的「已选…」行打出来 —— 下次失败不用再猜是名字对不上还是流程没走到
            for (String line : text.split("\n")) {
                if (line.trim().startsWith("已选")) {
                    System.out.println("    [countConfirmed] 实际确认行：「" + line.trim() + "」");
                }
            }
        }
        return count;
    }

    /**
     * 把 {@code str} 里 GBK 编不出来的字符记进 {@code bad}（用 {@code U+XXXX} 记，别把坏字符本身打印出来）。
     *
     * @param bad     收集箱
     * @param encoder GBK 编码器
     * @param what    这是谁（报错用）
     * @param str     要检查的字符串
     */
    static void collectNonGbk(List<String> bad, java.nio.charset.CharsetEncoder encoder,
                              String what, String str) {
        if (str == null) {
            return;
        }
        for (int i = 0; i < str.length(); i++) {
            String ch = str.substring(i, i + 1);
            if (!encoder.canEncode(ch)) {
                bad.add(what + " 含 U+" + String.format("%04X", (int) str.charAt(i)));
            }
        }
    }

    /**
     * @param blade 千冶·刃
     * @param skill 要测的技能
     * @return 能量满时这个技能的 AI 加分 &gt; 0
     */
    static boolean nonzeroEnergyBonus(
            cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade blade, Skill skill) {
        blade.setEnergy(blade.getEnergyMax());
        boolean ok = skill instanceof cn.gfhnv.game.interfaces.IAiScoreBonus tunable
                && tunable.aiScoreBonus(null, blade, blade) > 0;
        blade.setEnergy(0);
        return ok;
    }

    /**
     * 抓住 {@code showSpecialMes} 打出来的那一行（它直接 {@code System.out.println}，
     * 这里临时换掉 stdout 收回来）。
     *
     * @param user 要显示的对象
     * @return 那一行（没有输出就是空串）
     */
    static String captureShowSpecialMes(LivingThing user) {
        java.io.PrintStream original = System.out;
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        try {
            System.setOut(new java.io.PrintStream(buffer, true, java.nio.charset.StandardCharsets.UTF_8));
            user.getShowSpecialMes().show(user);
        } catch (Exception e) {
            return "（抛了 " + e + "）";
        } finally {
            System.setOut(original);
        }
        return buffer.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
    }

    /**
     * @param blade 千冶·刃
     * @param skill 要测的技能
     * @return 结界内（能量也满）时这个技能的 AI 加分 &gt; 0
     */
    static boolean inFuryBonus(
            cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade blade, Skill skill) {
        blade.setInfiniteFury(true);
        blade.setEnergy(blade.getEnergyMax());   // 千冶铸一还要能量满
        boolean ok = skill instanceof cn.gfhnv.game.interfaces.IAiScoreBonus tunable
                && tunable.aiScoreBonus(null, blade, blade) > 0;
        blade.setInfiniteFury(false);
        blade.setEnergy(0);
        return ok;
    }

    /**
     * @param coreflameCost 火种门槛
     * @return 配了该门槛的大招实例（不改注册表，纯局部对象）
     */
    static cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills.UltimateAttack
    ultimateWithCost(int coreflameCost) {
        cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills.UltimateAttack skill =
                new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills.UltimateAttack();
        skill.setCoefficientValue("coreflameCost", coreflameCost);
        return skill;
    }

    /**
     * @param turns 额外回合数
     * @return 配了该回合数的大招实例（不改注册表，纯局部对象）
     */
    static cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills.UltimateAttack
    ultimateWithTurns(int turns) {
        cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills.UltimateAttack skill =
                new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills.UltimateAttack();
        skill.setCoefficientValue("awakenExtraTurns", turns);
        return skill;
    }

    /**
     * 让【支柱-死星天裁】对一只满血木桩打一次（【毁伤】1 层 = 只打 {@code hitsPerScourge} 段），
     * 返回木桩掉的血。
     * <p>
     * 木桩的血量写成 1000 万：段数一多就会把普通目标打死，而"目标死了就停手"
     * 会让两次对照的伤害被血量上限截断，比不出系数的作用。
     *
     * @param skill 技能（带着要验的那份系数）
     * @param user  施法者（白厄）
     * @return 木桩掉的血
     */
    static long verdictDamage(
            cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.FoundationStardeathVerdict
                    skill,
            cn.gfhnv.game.officialStuff.customEntity.players.Phainon user) {
        LivingThing dummy = new PlayerOne(125).copy();
        dummy.setName("死星天裁木桩");
        dummy.setHpMax(10_000_000L);
        dummy.setHp(10_000_000L);
        Fight fight = new Fight(new ArrayList<>(List.of(dummy)), new ArrayList<>(),
                new ArrayList<>(List.of(user)));
        user.setScourge(1);
        long before = dummy.getHp();
        skill.comeToEffect(fight, user, new ArrayList<>(List.of(dummy)));
        return before - dummy.getHp();
    }

    /**
     * 让【却是必要的苦难】对一只木桩打一次（先给 2 层【灾难之力】= 2 段 + 一记收尾），
     * 返回木桩掉的血。
     *
     * @param skill  技能（带着要验的那份系数）
     * @param reaver 盗火行者
     * @param wall   木桩
     * @return 木桩掉的血
     */
    static long sufferingDamage(
            cn.gfhnv.game.officialStuff.customSkill.flameReaverSkills.NecessarySuffering skill,
            cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver reaver, LivingThing wall) {
        reaver.addDisasterPower(2);
        Fight fight = new Fight(new ArrayList<>(List.of(reaver)), new ArrayList<>(),
                new ArrayList<>(List.of(wall)));
        long before = wall.getHp();
        skill.comeToEffect(fight, reaver, fight.getOpponentList(reaver));
        return before - wall.getHp();
    }

    /**
     * @return 当前"强制 verbose"的值（自测前后原样还原，别把状态留给别的用例）
     */
    static Boolean forcedVerbose() {
        try {
            java.lang.reflect.Field field = cn.gfhnv.game.system.configLoadingSystem.ConfigOutput.class
                    .getDeclaredField("forced");
            field.setAccessible(true);
            return (Boolean) field.get(null);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /**
     * @return 现在是不是被自测强制成"详细播报"（判定同上）
     */
    static boolean isVerboseForced() {
        return Boolean.TRUE.equals(forcedVerbose());
    }

    /**
     * 新造一个模板的副本，用来观察"现在的规则值算出来的面板"。
     * <p>
     * 必须用副本而不是注册表里的模板：模板是在规则加载之前造出来的，它的三围已经定型；
     * 规则里的 {@code formula.*} 只影响"之后新造出来的实体"（这正是设计里写的边界）。
     *
     * @param id 实体完整 id
     * @return 新造的副本
     */
    static LivingThing entityLoadProbe(String id) {
        LivingThing template = entityTemplateOf(id);
        return template == null ? null : template.copy();
    }

    /**
     * 递归删掉一个目录（不存在就什么都不做）。
     * <p>
     * 只给自测的临时目录用：这类用例要在真实文件系统上验证"只在缺失时写盘"，
     * 所以必须自己收拾干净（成果目录是 gitignore 的 {@code out/}）。
     *
     * @param directory 目录
     */
    static void deleteDirectory(java.io.File directory) {
        java.io.File[] children = directory.listFiles();
        if (children != null) {
            for (java.io.File child : children) {
                deleteDirectory(child);
            }
        }
        if (directory.exists() && !directory.delete()) {
            fail("临时目录删不掉：" + directory.getPath());
        }
    }

    /**
     * 按完整 id 取注册表里的实体模板。
     *
     * @param id 完整 id
     * @return 模板（找不到直接让断言失败，返回 {@code null}）
     */
    static LivingThing entityTemplateOf(String id) {
        for (LivingThing living : World.getLivingEntityList()) {
            if (id.equals(living.getId())) {
                return living;
            }
        }
        fail("注册表里找不到实体模板「" + id + "」");
        return null;
    }

    /**
     * <b>真模组</b>（{@code mods/drunkenSword}）的实体与技能也不进游戏自己的配置。
     * <p>
     * 上一条用例用的是一个最小的假模组；这一条换成真的：直接复用模组自己的编译产物
     * （{@code mods/drunkenSword/bin}，<b>不重编、不改 {@code mods/} 下任何一个字节</b>），
     * 走真实的 {@code Mod#invokeWhenLoaded()} + {@code registerItself()}。
     * <p>
     * 产物不存在时（干净的检出）只打印一行信息跳过 —— 这条用例的价值在"有模组可加载"的环境里，
     * 而它<b>不是</b>唯一证据：上一条用例在任何环境下都会跑。
     */
    static void testRealModContentIsNotInGameConfig() {
        java.io.File bin = new java.io.File("./mods/drunkenSword/bin");
        if (!bin.isDirectory()) {
            System.out.println("  [信息] 没有 mods/drunkenSword/bin（模组未编译），跳过真模组那条检查");
            return;
        }
        Mod mod = null;
        try (java.net.URLClassLoader loader = java.net.URLClassLoader.newInstance(
                new java.net.URL[]{bin.toURI().toURL()},
                Thread.currentThread().getContextClassLoader())) {
            Class<?> mainClass = Class.forName("com.gfhnv.mods.drunkenSword.DrunkenSwordMod",
                    true, loader);
            mod = (Mod) mainClass.getConstructor(cn.gfhnv.game.mod.ModInformation.class)
                    .newInstance(new cn.gfhnv.game.mod.ModInformation("酒剑仙", "自测", "验证用",
                            "com.gfhnv.mods.drunkenSword.DrunkenSwordMod", "1"));
            cn.gfhnv.game.world.World.addMod(mod);
            mod.invokeWhenLoaded();
            mod.registerItself();
            StringBuilder owned = new StringBuilder();
            for (cn.gfhnv.game.entity.Entity entity : mod.getEntityList()) {
                owned.append(entity.getId()).append(' ');
            }
            check("真模组：酒剑仙被注册进注册表（id = " + owned.toString().trim() + "）",
                    !mod.getEntityList().isEmpty()
                            && cn.gfhnv.game.world.World.getEntityList()
                            .contains(mod.getEntityList().get(0)));

            String entityJson = cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter
                    .entityDataJson();
            String skillJson = cn.gfhnv.game.system.configLoadingSystem.ConfigDefaultWriter
                    .skillDataJson();
            check("真模组：EntityData.json 里没有 {game_official_content 之外} 的模组内容（酒剑仙）—— "
                            + "含 drunkenSword = " + entityJson.contains("drunkenSword"),
                    !entityJson.contains("drunkenSword"));
            check("真模组：SkillData.json 里没有酒剑仙的技能（三个技能名一个都不该出现）",
                    !skillJson.contains("醉里挑灯看剑") && !skillJson.contains("举杯邀月")
                            && !skillJson.contains("一剑霜寒十四州"));
            check("真模组：官方内容照旧写出去（playerOne 两边都在）",
                    entityJson.contains("game_official_content:playerOne")
                            && skillJson.contains("game_official_content:playerOne"));
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException e) {
            check("真模组：加载 mods/drunkenSword 失败（这条红了说明模组契约被弄坏了）—— "
                    + e.getClass().getSimpleName() + ": " + e.getMessage(), false);
        } finally {
            if (mod != null) {
                for (cn.gfhnv.game.entity.Entity entity : new ArrayList<>(mod.getEntityList())) {
                    cn.gfhnv.game.world.World.removeEntity(entity);
                }
                cn.gfhnv.game.world.World.removeMod(mod);
            }
        }
    }

    /**
     * 读一个私有字段的值（自测里只为"没被动过"这件事取个快照）。
     *
     * @param target 对象
     * @param name   字段名
     * @return 值；读不到返回 {@code null}
     */
    static Object valueOfField(Object target, String name) {
        for (Class<?> current = target.getClass(); current != null && current != Object.class;
             current = current.getSuperclass()) {
            try {
                java.lang.reflect.Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (ReflectiveOperationException | RuntimeException e) {
                continue;
            }
        }
        return null;
    }

    /**
     * @param fight 战斗
     * @return 最后加进我方阵营的那个实体的 id；我方列表为空时返回 {@code null}
     */
    static String lastFighterId(Fight fight) {
        List<LivingThing> fighters = fight.getFighterList();
        return fighters.isEmpty() ? null : fighters.get(fighters.size() - 1).getId();
    }
}
