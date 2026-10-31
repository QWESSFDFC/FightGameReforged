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
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.fight.ActionSignal;
import cn.gfhnv.game.system.fight.TurnEntry;
import cn.gfhnv.game.system.fight.TurnManager;
import cn.gfhnv.game.system.mana.Mana;
import cn.gfhnv.game.utils.ConsoleColor;
import cn.gfhnv.game.world.World;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FightTurnPastListener {
    private static List<LivingThing> theDeath = new ArrayList<>();
    private static TurnEntry presentTurn;

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
                runExecuteList(fightPastOneTurnEvent.getFight(), presentTurn,
                        presentTurn.getFirstExecuteList(), "开场");
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
                runExecuteList(fightPastOneTurnEvent.getFight(), presentTurn,
                        presentTurn.getLastExecuteList(), "收尾");

                EventBus.post(new EffectUpdateEvent(presentTurn.getLivingThing(), presentTurn));
                // 本回合处理完毕，回到 turnLoop 开头取下一个回合（不再递归 post 事件）
            }
        } finally {
            // 必须复位：否则一次异常就会让这个监听器实例此后永远拒绝驱动回合
            isDriving = false;
        }
    }
}
