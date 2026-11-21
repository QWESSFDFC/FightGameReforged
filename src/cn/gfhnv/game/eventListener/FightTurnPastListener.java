package cn.gfhnv.game.eventListener;


import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EffectUpdateEvent;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.FightEndEvent;
import cn.gfhnv.game.event.FightPastOneTurnEvent;
import cn.gfhnv.game.interfaces.ISpecialAction;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.command.CommandManager;
import cn.gfhnv.game.system.fight.*;
import cn.gfhnv.game.system.mana.Mana;
import cn.gfhnv.game.utils.ConsoleColor;
import cn.gfhnv.game.world.World;

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

    /**
     * <b>把动作队列排空</b>：反复"取快照 → 清空 → 逐个执行"，直到队列真的空了。
     * <p>
     * <b>为什么不能直接 {@code list.iterator()} 遍历活列表</b>（2026-10-24 用户实测踩到）：
     * 动作执行期间会<b>追加</b>动作 —— 追打、免死后的最后一击、转阶段的播报……
     * 用迭代器遍历会在下一次 {@code next()} 抛 {@code ConcurrentModificationException}，
     * 表现是"这一回合剩下的动作全没跑、提示也没了"，而且会被 EventBus 的异常隔离吞掉，极难查。
     * <p>
     * 改成"快照 + 清空"之后，追加的动作进到<b>下一轮</b>，仍然在本回合内执行完，顺序也不乱。
     * <p>
     * <b>不设轮数上限</b>（用户 2026-10-24 拍板）：会往队列里追加动作的都是我们自己的机制
     * （追打、免死后的最后一击、转阶段播报），没有"自己无限追加自己"这种东西；
     * 真出现那是设计错误，应当让它暴露出来，而不是被一个上限悄悄截断。
     * 所以"技能不许在自己的执行里无限往队列塞自己"这条靠评审保证，不靠运行时兜底。
     *
     * @param fight       当前战斗
     * @param presentTurn 当前回合（执行时的 actor）
     * @param queue       要排空的队列（会被清空）
     * @param label       队列名（只用于报警文案）
     * @return 实际执行了多少个动作
     */
    public static int runExecuteList(Fight fight, TurnEntry presentTurn, List<ISpecialAction> queue,
                                     String label) {
        int executed = 0;
        while (!queue.isEmpty()) {
            List<ISpecialAction> batch = new ArrayList<>(queue);
            queue.clear();
            for (ISpecialAction action : batch) {
                action.execute(fight, presentTurn.getLivingThing());
                executed++;
            }
        }
        return executed;
    }

    /**
     * <b>排空回合内行动队列</b>（2026-11-14 第 59 批，Step 6 第二步）。
     * <p>
     * 规则就是作者定下的四条：<b>一个接一个</b>（取队首 → 执行 → 再取）、
     * <b>执行中可以追加</b>（追加的条目会继续被取到）、<b>执行前可以移除</b>（调用方直接改
     * {@link TurnEntry#getActQueue()} 即可）、<b>有优先级</b>（大的先执行，<b>同优先级按入队顺序</b>）。
     * <p>
     * 排序用 {@code List.sort}（稳定排序），所以"同优先级稳不稳定"这件事由 Java 保证，不靠约定。
     * <b>不设轮数上限</b>：会往队列里追加的都是我们自己的机制（追打、免死后的最后一击、转阶段播报），
     * 真出现"自己无限追加自己"那是设计错误，应该暴露而不是被上限悄悄截断。
     *
     * @param fight 当前战斗
     * @param turn  当前回合条目（队列在它身上）
     * @return 实际执行了多少条
     */
    public static int runActQueue(Fight fight, TurnEntry turn) {
        int executed = 0;
        List<ActEntry> queue = turn.getActQueue();
        while (!queue.isEmpty()) {
            // 稳定排序：同优先级保持入队顺序
            queue.sort(java.util.Comparator.comparingInt(ActEntry::priority).reversed());
            ActEntry entry = queue.remove(0);
            if (entry.action() == null) {
                continue;
            }
            // 2026-11-21 第 81 批：执行者取条目自己的行动者（旧条目为 null 时才回退到回合行动者）
            LivingThing actActor = entry.actor() == null ? turn.getLivingThing() : entry.actor();
            entry.action().execute(fight, actActor);
            // 每执行完一条行动条目发一次（第 74 批新增事件）：日志/AI 复盘/调试面板用
            EventBus.post(new cn.gfhnv.game.event.TurnActionExecutedEvent(fight, turn, entry));
            executed++;
        }
        return executed;
    }

    public static TurnEntry getPresentTurn() {
        return presentTurn;
    }

    /**
     * 覆盖"当前正在执行的回合条目"。
     * <p>
     * 正式流程里这个字段只由回合循环自己维护（取下一个回合时写入），
     * 但<b>技能需要在"自己的回合里"读到这个上下文</b>：例如盗火行者的
     * {@code FlameReaver#delayNextOwnTurn} —— 那时时间轴上还没有它的下个条目，
     * 只能靠改本回合的信号来阻止回合循环再排一条。
     * 自测要构造这种上下文就得能把这一格塞进去，所以留了这个入口。
     *
     * @param turn 正在执行的回合条目；{@code null} 表示"不在任何回合里"
     */
    public static void setPresentTurn(TurnEntry turn) {
        presentTurn = turn;
    }

    /**
     * 把一条法力压成短数字（只用于那行紧凑的状态栏）。
     * <p>
     * 只显示当前值不显示上限：上限是固定的（主元素 {@code 成长×(等级-1)+200}），
     * 每回合都打"616.0/616.0"这种没有任何信息量的重复文本，纯属费眼睛。
     *
     * @param mana 法力；可为 {@code null}
     * @return 当前值（取整）
     */
    private static long manaOf(Mana mana) {
        if (mana == null) {
            return 0;
        }
        return (long) mana.getAmount();
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
                // 尸体也顺便从「全体实体」列表里摘掉（/list 与 @e 读的就是这条）。
                // 上面那两个 removeIf 只清了 fighterList / enemiesList —— allEntities 一直没人清，
                // 所以打久了 /list 里全是死人和残骸。只清这一条，不动战斗逻辑读的那两个。
                fightPastOneTurnEvent.getFight().removeDeadFromAllEntities();
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
                    // 实体死亡、即将离场（第 74 批新增事件）：此时已移出阵营列表，whenLeaveFight 还没跑
                    EventBus.post(new cn.gfhnv.game.event.EntityDeathEvent(
                            fightPastOneTurnEvent.getFight(), dead));
                    dead.whenLeaveFight(fightPastOneTurnEvent.getFight());
                    // 2026-11-15 第 76 批（作者要求）：死亡/退出的临时实体同步离开 World.things，
                    // 别让 /list 与 @e 继续选中尸体、也别让它一直吊着整场 Fight。
                    World.removeThing(dead);
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
                    // 兜底：两个阵营都还有人，却排不出回合了（时间轴空了），再走下去 getFirst() 会抛
                    // NoSuchElementException。这里**必须把战斗收掉**，不能只是 break：
                    //   - 只 break 的话 fightInProgress 仍是 true、本场监听器也不会注销，
                    //     再开一局就会有两个回合监听器同时在场（玩家会看到"一回合跑两遍"）；
                    //   - 这种局面只可能是"双方都排不出下一回合"，按平局处理 ——
                    //     FightEndEvent 只有胜负两态，这里按**未胜利**结算（不给奖励），
                    //     并把原因打印出来，方便排查是哪个机制把时间轴清空了。
                    System.out.println(ConsoleColor.yellow("【战斗结束】双方都还有人，但时间轴排不出下一回合 —— "
                            + "本场按平局结算（未胜利、不发奖励）。"));
                    EventBus.post(new FightEndEvent(false, fightPastOneTurnEvent.getFight()));
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
                // 回合正式开始（第 74 批新增事件）：行动者已定、冷却已减、法力已回，回合头即将打印。
                // 监听器在这里改状态会直接出现在下面的回合头与动作里。
                EventBus.post(new cn.gfhnv.game.event.FightTurnStartEvent(
                        fightPastOneTurnEvent.getFight(), presentTurn));
                LivingThing actor = presentTurn.getLivingThing();
                // 阵营判据统一走 Fight#isOurSide：回合头、攻击行、侵蚀行、命令系统的 @s 跟随
                // 用的是同一个方法，别在这里再自己 contains 一遍（两套口径迟早会漂）。
                boolean ourSide = fightPastOneTurnEvent.getFight().isOurSide(actor);
                // 命令系统的"执行者"跟着当前行动者走：多角色队伍里，@s 才不会一直指向
                // 选人时最后选的那个角色（2026-09 实测：酒剑仙回合里 /give @s 发给了白厄）。
                CommandManager.followActor(actor, ourSide);
                // 回合头 + 状态压成两行（以前是"回合头 + 我方/敌方 + 状态 + 能量 + 五行各一行"= 7 行，
                // 每回合都刷一遍太费眼睛）。用户自己加的"我方/敌方"信息并进回合头，别丢。
                // 名字后面的"#短标识"来自 Thing#getShortUuid()，攻击行（LivingThing#getNameWithUuidAndSide）
                // 用的是同一个方法 —— 两处各写一份实现迟早一真一假。
                // 「【来源标识】」只在条目被标了 tag 时出现（例如击杀完整容器的奖励回合）：
                // 白厄变身的连击与它都是"额外回合"，不打标识根本看不出哪条先执行。
                String turnTag = presentTurn.getTag() == null ? "" : "【" + presentTurn.getTag() + "】";
                System.out.println(ConsoleColor.cyan("─── 现在是 " + actor.getNameWithUuid()
                        + "（" + (ourSide ? "我方" : "敌方") + "）的回合" + turnTag + " ───"));
                System.out.println("HP " + actor.getHp() + "/" + actor.getHpMax() + "   能量 金" + manaOf(actor.getMetalMana())
                        + " 木" + manaOf(actor.getWoodMana())
                        + " 水" + manaOf(actor.getWaterMana())
                        + " 火" + manaOf(actor.getFireMana())
                        + " 土" + manaOf(actor.getDirtMana()));
                // ★ 2026-11-14 第 58 批（Step 6 第一步）：效果结算从"回合最后一步"挪到
                // "回合头输出之后"（作者 2026-11-08 定的顺序）。语义差异只有一条：
                // 本回合的动作**看得到本回合的 tick 结果**（DoT 先扣血、加成型效果先生效），
                // 而不再是"动作跑完才结算、结算结果下一回合才用得上"。
                // 注意:这里在 firstExecute（开场动作）之前,但 firstExecute 通常只是播报/换控制器,
                // 真正会"挨 DoT 打死"的判定仍在下面 isAlive() 那一行。
                EventBus.post(new EffectUpdateEvent(presentTurn.getLivingThing(), presentTurn));
                // ★ 2026-11-14 第 59 批（Step 6 第二步）：开场 / 正式行动 / 重排 / 收尾 **合成一条队列**。
                // 默认优先级把三段顺序原样保住：FIRST(1000) > CHECK(900) > NORMAL(0) > RESCHEDULE(-500) > LAST(-1000)。
                // firstExecute / lastExecute 里原本就有的条目已经在队列里（它们走的是"带优先级视图"）。
                TurnEntry turnRef = presentTurn;
                Fight fightRef = fightPastOneTurnEvent.getFight();
                turnRef.addAct((f, u) -> {
                    if (!turnRef.getLivingThing().isAlive()) {
                        turnRef.setActionSignal(ActionSignal.SKIP);
                    }
                    if (turnRef.getLivingThing().getShowSpecialMes() != null) {
                        turnRef.getLivingThing().getShowSpecialMes().show(turnRef.getLivingThing());
                    }
                }, ActEntry.PRIORITY_CHECK);
                turnRef.addAct((f, u) -> {
                    if (turnRef.getActionSignal().equals(ActionSignal.NORMAL)) {
                        turnRef.getLivingThing().getController().act(f);
                    } else if (turnRef.getActionSignal().equals(ActionSignal.SPECIAL_ACTION)) {
                        // 判空:回合条目里的 SPECIAL_ACTION 是创建时的快照,而 specialAction 可能已被效果
                        // (如 Frozen 到期)清掉,直接调用会 NPE 并让整局游戏崩掉
                        ISpecialAction specialAction = turnRef.getLivingThing().getController().getSpecialAction();
                        if (specialAction != null) {
                            specialAction.execute(f, turnRef.getLivingThing());
                        }
                    }
                }, ActEntry.PRIORITY_NORMAL);
                turnRef.addAct((f, u) -> {
                    if (turnRef.getActionSignal() != ActionSignal.WITHOUT_NEW_TURN
                            && !turnRef.getActionSignal().equals(ActionSignal.SKIP_WITHOUT_NEW_TURN)) {
                        TurnEntry next = new TurnEntry(turnRef.getLivingThing(),
                                TurnManager.needTimeOf(turnRef.getLivingThing()),
                                TurnManager.getPresentTime());
                        TurnManager.getTurns().add(next);
                    } else if (turnRef.getActionSignal().equals(ActionSignal.WITHOUT_NEW_TURN)) {
                        turnRef.getLivingThing().getController().act(f);
                    }
                }, ActEntry.PRIORITY_RESCHEDULE);
                runActQueue(fightRef, turnRef);
                // 回合结束（第 74 批新增事件）：这条回合的行动队列已全部执行完
                EventBus.post(new cn.gfhnv.game.event.FightTurnEndEvent(fightRef, turnRef));

                // 本回合处理完毕，回到 turnLoop 开头取下一个回合（不再递归 post 事件）
            }
        } catch (Throwable error) {
            // 2026-11-15 第 78 批（分析 P0-1）：回合循环里的任何异常都不能只留给 EventBus 吞掉 ——
            // 那样 fightInProgress / 时间轴 / 监听器都会停在半路（玩家侧就是"卡住"）。
            // 这里统一强制收场：复位状态 → 清时间轴 → 发 FightEndEvent（监听器照常注销、实体照常复位）。
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            System.out.println(ConsoleColor.red("【战斗异常】回合循环抛了 "
                    + error.getClass().getSimpleName()
                    + (error.getMessage() == null ? "" : "：" + error.getMessage())
                    + " —— 已强制收场，避免整场卡死。"));
            // 全栈打到控制台：只写类名+message 时，无 message 的 UOE 根本没法定位（第 79 批的教训）
            error.printStackTrace();
            cn.gfhnv.game.system.logSystem.LogWriter.writeThrowable("[战斗异常] 强制收场", error);
            if (!cn.gfhnv.game.GameMain.endFight(fightPastOneTurnEvent.getFight(), false)) {
                // 已经不在战斗状态（例如异常发生在 endFight 之后）：仍然要发事件，让监听器/实体收尾
                EventBus.post(new cn.gfhnv.game.event.FightEndEvent(
                        false, fightPastOneTurnEvent.getFight()));
            }
        } finally {
            // 必须复位：否则一次异常就会让这个监听器实例此后永远拒绝驱动回合
            isDriving = false;
        }
    }
}
