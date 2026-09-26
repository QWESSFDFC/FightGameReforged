package cn.gfhnv.game;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.FightEndEvent;
import cn.gfhnv.game.event.FightStartEvent;
import cn.gfhnv.game.event.GameStartEvent;
import cn.gfhnv.game.eventListener.EffectEventListener;
import cn.gfhnv.game.eventListener.FightStartEventListener;
import cn.gfhnv.game.eventListener.GameStartEventListener;
import cn.gfhnv.game.eventListener.PhysicsEventListener;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.mod.ModLoader;
import cn.gfhnv.game.officialStuff.OfficialGameContent;
import cn.gfhnv.game.system.command.CommandManager;
import cn.gfhnv.game.system.configLoadingSystem.ConfigLoader;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.fight.TurnManager;
import cn.gfhnv.game.system.logSystem.LogWriter;
import cn.gfhnv.game.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * 代码开源 MIT　License.---------- @author gfhnv
 * <p>
 * 命令系统的接入点：
 * <ul>
 *     <li>{@link #gameInitialize()} 里调用 {@link CommandManager#initialize()} 注册官方命令；</li>
 *     <li>玩家选定角色后调用 {@link CommandManager#setPlayer(LivingThing)}，
 *     {@code @s}/{@code @p} 等实体选择器才有参照物；</li>
 *     <li>读输入依旧是 {@code SCANNER.nextLine()}，只是先问一句「这行是不是命令」，
 *     命令功能关闭时行为与不接入命令系统时一致。</li>
 * </ul>
 */
public class GameMain {
    /**
     * 输入源。
     * <p>
     * <b>已知限制</b>：在 Windows 的 {@code cmd.exe} 里，中文输入（如
     * {@code /kill @e[type=虫皇]}）会在<b>原生控制台层</b>就被替换掉，
     * {@code System.in} 与 {@code System.console()} 两条路径都拿不到中文
     * （码点为 {@code U+FFFD} / 别的字符 / 空格），Java 侧无法修复。
     * 命令里请使用 ASCII 简单类名，功能完全等价：
     * {@code @e[type=InsectBoss]}（虫皇）、{@code @e[type=CommonInsect]}（普通虫子）、
     * {@code @e[type=IceInsect]}（冰虫子）、{@code @e[type=Phainon]}（白厄）、
     * {@code @e[type=PlayerOne]}（玩家一）、{@code @e[type=ActorLiXiaoYan]}（李晓焰）。
     * <p>
     * 中文匹配本身是<b>实现好且有自测覆盖</b>的（{@code @e[name=普通虫子]} 在自测里能选中），
     * 只是 {@code cmd.exe} 送不进来。换 Windows Terminal / IDEA 运行通常可用。
     */
    public static final Scanner SCANNER = new Scanner(System.in);
    public static String userName;
    /**
     * 是否正在战斗中（供命令系统与主流程共用）。
     */
    private static boolean fightInProgress = false;

    public static void gameInitialize() {
        World.addMod(new OfficialGameContent());
        ModLoader.modLoaderInitialize();
        EventBus.register(new GameStartEventListener());
        EventBus.register(new EffectEventListener());
        EventBus.register(new PhysicsEventListener());
        EventBus.register(new FightStartEventListener());
        EventBus.post(new GameStartEvent());
        try {
            System.out.println("加载配置中");
            ConfigLoader.loadConfig();
        } catch (Exception e) {
            LogWriter.writeLog(e.getMessage());
        }
        // 命令系统必须在内容加载完之后初始化：这样命令里引用的注册表（World）已经是完整的
        CommandManager.initialize();
    }

    public static void main(String[] args) {
        gameInitialize();

        String input;
        System.out.println("欢迎进入游戏!请你输入你的名字");
        userName = SCANNER.nextLine();
        LogWriter.writeLog("用户名字" + userName);
        System.out.println("好的." + userName + ".这是一款文字战斗游戏马上你可以选择你的角色和你的敌人,甚至是你的奖励");
        startAFight();
        do {
            System.out.println("要不要再玩一局?输入no/n/exit/e退出游戏.yes/y继续." + GameMain.userName);
            input = SCANNER.nextLine();
            if (CommandManager.process(input)) {
                // 这一行是命令，已经执行过了，继续问「要不要再玩一局」
                continue;
            }
            if (input.equalsIgnoreCase("yes")||input.equalsIgnoreCase("y")) {
                GameMain.startAFight();
            }
        } while (!input.equalsIgnoreCase("no")&&!input.equalsIgnoreCase("n")&&!input.equalsIgnoreCase("e")&&!input.equalsIgnoreCase("exit"));
    }

    /**
     * 是否正在战斗中。
     *
     * @return {@code true} 表示当前有一场战斗尚未结算
     */
    public static boolean isInFight() {
        return fightInProgress;
    }

    /**
     * 由命令（{@code /endfight}）调用：结束当前这场战斗。
     * <p>
     * 它只做三件事：
     * <ol>
     *     <li>清空 {@link cn.gfhnv.game.system.fight.TurnManager} 的时间轴；</li>
     *     <li>把 {@code fightInProgress} 置为 {@code false}
     *     （正在推进的战斗循环会在下一圈看到它并直接 {@code break}，因此<b>不会</b>递归）；</li>
     *     <li>发布 {@link cn.gfhnv.game.event.FightEndEvent}，
     *     让 {@code FightEndEventListener} 去调用各生物的
     *     {@code whenFightEnds()}、发放奖励、注销监听器。</li>
     * </ol>
     *
     * @param fight     要结束的战斗
     * @param playerWin 是否按「玩家获胜」处理（会正常发奖励）
     * @return {@code true} 表示确实结束了一场战斗
     */
    public static boolean endFight(Fight fight, boolean playerWin) {
        if (!fightInProgress) {
            return false;
        }
        fightInProgress = false;
        LogWriter.writeLog("战斗被命令强制结束，按玩家" + (playerWin ? "胜利" : "失败") + "处理");
        TurnManager.getTurns().clear();
        TurnManager.setIsInitialized(false);
        EventBus.post(new FightEndEvent(playerWin, fight));
        return true;
    }

    public static void startAFight() {
        List<Item> rewards = new ArrayList<>();
        List<LivingThing> enemies = new ArrayList<>();
        List<LivingThing> fighters = new ArrayList<>();
        String input = "";
        LivingThing selectedLivingThing = null;
        Item selectedItem = null;
        System.out.println("首先让我们从选择你的角色开始.可以选择任意数量的角色.这是角色列表.->输入角色名字前的数字来选择<-.\n输入next/n下一步,quit/q退出游戏");
        System.out.println("选择一名角色之后你可以获得其介绍,之后再输入yes来把其加入到队伍中,输入no返回上一步");
        while (true) {
            if (!enemies.isEmpty() && !fighters.isEmpty()) {
                break;
            }

            LivingThing[] livingThings = World.getLivingEntityList().toArray(new LivingThing[0]);
            int i = 0;
            for (LivingThing livingThing : World.getLivingEntityList()) {
                System.out.println(i + livingThing.getName());
                i++;
            }
            while (true) {
                input = readInput();
                if (input.equalsIgnoreCase("quit")||input.equalsIgnoreCase("q")) {
                    System.exit(0);
                }
                if (input.equalsIgnoreCase("next")||input.equalsIgnoreCase("n")) {
                    break;
                }
                try {
                    selectedLivingThing = livingThings[Integer.parseInt(input)].copy();
                    System.out.println(selectedLivingThing.getName());
                    System.out.println(selectedLivingThing.getDescription());
                    while (true) {
                        input = readInput();
                        if (input.equalsIgnoreCase("yes")||input.equalsIgnoreCase("y")) {
                            World.addThing(selectedLivingThing);
                            System.out.println("输入下一个数字或next/n");
                            fighters.add(selectedLivingThing);
                            // 让命令系统知道「谁在玩」，@s / @p 等选择器以它为参照
                            CommandManager.setPlayer(selectedLivingThing);

                            break;
                        }
                        if (input.equalsIgnoreCase("no")||input.equalsIgnoreCase("n")) {
                            selectedLivingThing = null;
                            System.out.println("输入下一个数字或next/n");
                            break;
                        }
                    }
                } catch (Exception e) {
                    System.out.println("输入错误");
                    selectedLivingThing = null;
                    System.out.println("输入下一个数字或next/n");
                }
            }

            System.out.println("接下来选择对手.依然是刚才的生物列表");
            i = 0;
            for (LivingThing livingThing : World.getLivingEntityList()) {
                System.out.println(i + livingThing.getName());
                i++;
            }
            while (true) {
                input = readInput();
                if (input.equalsIgnoreCase("quit")||input.equalsIgnoreCase("q")) {
                    System.exit(0);
                }
                if (input.equalsIgnoreCase("next")||input.equalsIgnoreCase("n")) {
                    break;
                }
                try {
                    selectedLivingThing = livingThings[Integer.parseInt(input)].copy();
                    System.out.println(selectedLivingThing.getName());
                    System.out.println(selectedLivingThing.getDescription());
                    while (true) {
                        input = readInput();
                        if (input.equalsIgnoreCase("yes")||input.equalsIgnoreCase("y")) {
                            World.addThing(selectedLivingThing);
                            enemies.add(selectedLivingThing);
                            System.out.println("输入下一个数字或next/n");
                            break;
                        }
                        if (input.equalsIgnoreCase("no")||input.equalsIgnoreCase("n")) {
                            selectedLivingThing = null;
                            System.out.println("输入下一个数字或next/n");
                            break;
                        }
                    }
                } catch (Exception e) {
                    System.out.println("输入错误");
                    selectedLivingThing = null;
                    System.out.println("输入下一个数字或next/n");
                }
            }

            System.out.println("奖励.同理");
            i = 0;
            Item[] items = World.getItemList().toArray(new Item[0]);
            for (Item item : World.getItemList()) {
                System.out.println(i + item.getName());
                i++;
            }
            while (true) {
                input = readInput();
                if (input.equalsIgnoreCase("quit")||input.equalsIgnoreCase("q")) {
                    System.exit(0);
                }
                if (input.equalsIgnoreCase("next")||input.equalsIgnoreCase("n")) {
                    break;
                }
                try {
                    selectedItem = items[Integer.parseInt(input)].copy();
                    System.out.println(selectedItem.getDescription());
                    while (true) {
                        input = readInput();
                        if (input.equalsIgnoreCase("yes")||input.equalsIgnoreCase("y")) {
                            World.addThing(selectedItem);
                            rewards.add(selectedItem);
                            System.out.println("输入下一个数字或next/n");
                            break;
                        }
                        if (input.equalsIgnoreCase("no")||input.equalsIgnoreCase("n")) {
                            selectedItem = null;
                            System.out.println("输入下一个数字或next/n");
                            break;
                        }
                    }
                } catch (Exception e) {
                    System.out.println("输入错误");
                    selectedItem = null;
                    System.out.println("输入下一个数字或next/n");
                }
            }
        }
        for (LivingThing fighter : fighters) {
            fighter.setHp((long) fighter.getHpMax());

        }
        for (LivingThing enemy : enemies) {
            enemy.setHp((long) enemy.getHpMax());

        }
        System.out.println("游戏开始");
        Fight fight = new Fight(enemies, rewards, fighters);
        // 命令系统需要知道「现在在打哪一场」，@a / @e 等选择器才有查找范围
        CommandManager.setCurrentFight(fight);
        fightInProgress = true;
        EventBus.post(new FightStartEvent(fight));
    }

    /**
     * 读一行输入；如果这一行是命令，就地执行并继续读下一行。
     * <p>
     * 输入模式不变：底层依旧是 {@code SCANNER.nextLine()}，只是外面套了一层命令拦截。
     * 命令失败时会打印错误并继续读，不会把玩家踢出当前流程，也不会让异常冒泡到
     * 外层那个什么都没做的 {@code catch (Exception e) { System.out.println("输入错误"); }}。
     *
     * @return 一行非命令输入
     */
    private static String readInput() {
        while (true) {
            String line = SCANNER.nextLine();
            if (CommandManager.isCommand(line)) {
                CommandManager.process(line);
                continue;
            }
            return line;
        }
    }
}
