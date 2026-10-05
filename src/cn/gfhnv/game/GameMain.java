package cn.gfhnv.game;

import cn.gfhnv.game.entity.Entity;
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
import cn.gfhnv.game.system.configLoadingSystem.ConfigOutput;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.fight.TurnManager;
import cn.gfhnv.game.system.logSystem.LogWriter;
import cn.gfhnv.game.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.Set;
import java.util.function.Supplier;

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
    /**
     * 供输入行；{@code null} 表示读真标准输入（{@link #SCANNER}）。
     * <p>
     * ⚠️ <b>只由 {@link #setInputSourceForSelfTest} 设置，正常游戏流程里永远是 {@code null}</b> ——
     * 游戏原有的输入方式一个字都没改，只是把"行从哪来"抽成了一格可替换的引用。
     */
    private static SelectionInput scriptedLineSource = null;
    /**
     * ⚠️ 只给自测用：选人 / 选对手 / 选奖励三个阶段跑完一轮就直接返回，不进战斗。
     * <p>
     * 正常流程里它永远是 {@code false}，不影响 {@code startAFight()} 的任何行为。
     */
    private static boolean exitAfterSelectionForSelfTest = false;

    public static void gameInitialize() {
        // 游戏规则必须**第一个**加载，而且必须在任何实体被造出来之前：
        // 很多规则使用点是 `private static final X = GameRules.getXxx(...)`，
        // 也就是"那个类第一次被加载时读一次"。规则表加载完就冻结（见 ConfigLoader#loadGameRules）。
        loadGameRules();
        World.addMod(new OfficialGameContent());
        ModLoader.modLoaderInitialize();
        EventBus.register(new GameStartEventListener());
        EventBus.register(new EffectEventListener());
        EventBus.register(new PhysicsEventListener());
        EventBus.register(new FightStartEventListener());
        EventBus.post(new GameStartEvent());
        loadTagConfig();
        // 实体数值补丁必须紧跟标签配置：这时官方内容与模组内容都已经注册完，
        // 而玩家还没开始选人 —— 补丁打在注册表模板上，选人时 copy() 出来的副本天然带上它。
        loadEntityDataConfig();
        // 技能数值补丁同样打在模板的技能实例上（键 = 实体id#技能名），
        // 副本的技能是 copy() 出来的，所以顺序必须在选人之前。
        loadSkillDataConfig();
        // 命令系统必须在内容加载完之后初始化：这样命令里引用的注册表（World）已经是完整的
        CommandManager.initialize();
        // 配置播报的收尾：把上面每一份配置的记账汇总成**一行总量**（默认静默，见 ConfigOutput）。
        // 逐条明细在 verbose 模式下已经打过了，这一行就是"配置到底生没生效"的唯一判据。
        ConfigOutput.printPatchSummary();
    }

    /**
     * 加载游戏规则配置（{@code config/gameConfig/GameRules.json}）。
     * <p>
     * 与前几份配置各自独立 try/catch：其中一份坏掉，另外几份照常加载。
     * 它必须排在 {@code new OfficialGameContent()} 之前 —— 实体类的静态常量在类加载时就取值。
     */
    private static void loadGameRules() {
        try {
            ConfigLoader.loadGameRules();
        } catch (Exception e) {
            ConfigOutput.problem("[配置错误] 游戏规则加载失败：" + e.getMessage());
            LogWriter.writeLog("[配置错误] 游戏规则加载失败：" + e.getMessage());
        }
    }

    /**
     * 加载标签配置。任何失败都只写日志，不让游戏起不来。
     */
    private static void loadTagConfig() {
        try {
            ConfigLoader.loadConfig();
        } catch (Exception e) {
            LogWriter.writeLog(e.getMessage());
        }
    }

    /**
     * 加载实体数值配置（{@code config/gameConfig/EntityData.json}）。
     * <p>
     * 与 {@link #loadTagConfig()} 各自独立 try/catch：其中一份配置坏掉，
     * 另一份照常加载。
     */
    private static void loadEntityDataConfig() {
        try {
            ConfigLoader.loadEntityData();
        } catch (Exception e) {
            ConfigOutput.problem("[配置错误] 实体数值配置加载失败：" + e.getMessage());
            LogWriter.writeLog("[配置错误] 实体数值配置加载失败：" + e.getMessage());
        }
    }

    /**
     * 加载技能数值配置（{@code config/gameConfig/SkillData.json}）。
     * <p>
     * 与前两份配置各自独立 try/catch：其中一份坏掉，另外两份照常加载。
     */
    private static void loadSkillDataConfig() {
        try {
            ConfigLoader.loadSkillData();
        } catch (Exception e) {
            ConfigOutput.problem("[配置错误] 技能数值配置加载失败：" + e.getMessage());
            LogWriter.writeLog("[配置错误] 技能数值配置加载失败：" + e.getMessage());
        }
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
            if (input.equalsIgnoreCase("yes") || input.equalsIgnoreCase("y")) {
                GameMain.startAFight();
            }
        } while (!input.equalsIgnoreCase("no") && !input.equalsIgnoreCase("n") && !input.equalsIgnoreCase("e") && !input.equalsIgnoreCase("exit"));
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
     * <b>自测专用装配入口</b>：直接设置"是否正在战斗中"这一格。
     * <p>
     * ⚠️ <b>只给自测 / 探针用，不要在游戏流程里调用。</b>正常流程里这一格只有两处会改：
     * {@code startAFight()} 置 {@code true}、{@link #endFight} 置 {@code false}。
     * <p>
     * <b>它为什么必须存在</b>：{@code fightInProgress} 是 private，而它是
     * {@link cn.gfhnv.game.eventListener.FightTurnPastListener} 回合循环的<b>硬门</b>
     * （循环第一件事就是 {@code if (!GameMain.isInFight()) break;}）——
     * 没有这个入口，"真实回合循环"在自测里连第一圈都转不起来，
     * 于是"变身 → 挨打 → 免死 → 最后一击 → 退出变身"整条时间轴只能靠一次性探针
     * （{@code out/probe/}，跑完就删）来验，结论进不了 {@code src}。
     * <p>
     * 它只改这一格，不碰时间轴、不碰任何实体：调用方自己负责把 {@code fightInProgress}
     * 复位成 {@code false}（否则会污染同进程后面的用例）。
     *
     * @param inFight 是否把这一格摆成"战斗中"
     */
    public static void setFightInProgressForSelfTest(boolean inFight) {
        fightInProgress = inFight;
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
        InputReader inputReader = new InputReader();
        System.out.println("首先让我们从选择你的角色开始.可以选择任意数量的角色.这是角色列表.->输入角色名字前的数字来选择<-.\n输入next/n/nex/ne下一步,quit/q/qu/qui退出游戏");
        System.out.println("选择一名角色之后你可以获得其介绍,之后再输入yes/y来把其加入到队伍中,输入no返回上一步");
        System.out.println("一行可以输入多个,用 / 分隔,例如 1/2/3 一次选三个;数字后面直接跟 y 表示这一批全部确认,例如 1/2/3/y");
        // ⚠️ 外层这一圈的收口条件是「队伍与敌方都非空」：三段各自靠 next 收口，
        // 只要有一边一个都没选，它就会把这三段再走一遍（真实输入是无限流，所以这是可接受的循环）。
        // 改这里的控制流时请一并看 TestCommandSystem#testStartAFightSelection —— 那些脚本
        // 必须让"角色"与"对手"两段都选到东西，否则自测的脚本会用完、然后空转。
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
                List<LivingThing> selectedLivingThings = new ArrayList<>();
                if (!selectionOf(inputReader, selectedLivingThings, livingThings, "角色")) {
                    break;
                }
                if (selectedLivingThings.isEmpty()) {
                    // 一个都没选就 next = 放弃这一批，继续（外层会重新列名单）
                    continue;
                }
                for (LivingThing livingThing : selectedLivingThings) {
                    World.addThing(livingThing);
                    fighters.add(livingThing);
                    // 让命令系统知道「谁在玩」，@s / @p 等选择器以它为参照
                    CommandManager.setPlayer(livingThing);
                }
                System.out.println("输入下一个数字或next/n");
                // 这一批收口了：同一行后面还有段就接着处理，否则重新列一遍生物列表
                if (!inputReader.hasPending()) {
                    break;
                }
            }

            System.out.println("接下来选择对手.依然是刚才的生物列表");
            i = 0;
            for (LivingThing livingThing : World.getLivingEntityList()) {
                System.out.println(i + livingThing.getName());
                i++;
            }
            while (true) {
                List<LivingThing> selectedEnemies = new ArrayList<>();
                if (!selectionOf(inputReader, selectedEnemies, livingThings, "对手")) {
                    break;
                }
                if (selectedEnemies.isEmpty()) {
                    continue;
                }
                for (LivingThing livingThing : selectedEnemies) {
                    World.addThing(livingThing);
                    enemies.add(livingThing);
                }
                System.out.println("输入下一个数字或next/n");
                if (!inputReader.hasPending()) {
                    break;
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
                List<Item> selectedItems = new ArrayList<>();
                if (!selectionOf(inputReader, selectedItems, items)) {
                    break;
                }
                if (selectedItems.isEmpty()) {
                    continue;
                }
                for (Item item : selectedItems) {
                    World.addThing(item);
                    rewards.add(item);
                }
                System.out.println("输入下一个数字或next/n");
                if (!inputReader.hasPending()) {
                    break;
                }
            }
        }
        for (LivingThing fighter : fighters) {
            fighter.setHp((long) fighter.getHpMax());

        }
        for (LivingThing enemy : enemies) {
            enemy.setHp((long) enemy.getHpMax());

        }
        if (exitAfterSelectionForSelfTest) {
            return;
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
     * 命令失败时会打印错误并继续读，不会把玩家踢出当前流程。
     *
     * @return 一行非命令输入
     */
    private static String readInput() {
        while (true) {
            String line = scriptedLineSource == null ? SCANNER.nextLine() : scriptedLineSource.nextLine();
            if (CommandManager.isCommand(line)) {
                CommandManager.process(line);
                continue;
            }
            return line;
        }
    }

    /**
     * 供输入行给{@link InputReader}用的一行来源。
     * <p>
     * 存在的唯一理由是<b>让选人流程可自测</b>：正常游戏永远走 {@code null}（= {@link #SCANNER}），
     * 自测可以喂一段脚本化的输入，于是「输数字 → 输 y 确认 → 输 next」这条状态机
     * 能在 {@code TestCommandSystem} 里被逐条钉住，而不是只能靠进游戏手点。
     *
     * @author AI（DeepSeek）生成
     */
    @FunctionalInterface
    public interface SelectionInput {

        /**
         * 取下一行输入（不含换行符）。
         * <p>
         * ⚠️ <b>取不出下一行时必须自己抛异常</b>（脚本用完 / EOF），<b>不要返回 {@code null}</b> ——
         * {@code null} 会被当成"输入流结束"，而上层三个阶段会因此一直重来
         * （真实输入是无限流），自测就会空转成假死。
         *
         * @return 下一行输入（不含换行符）
         */
        String nextLine();
    }

    /**
     * <b>自测专用装配入口</b>：把"输入行从哪来"换成一段脚本化输入，并让
     * {@link #startAFight()} 在选人 / 选对手 / 选奖励三个阶段跑完一轮之后直接返回（不进战斗）。
     * <p>
     * ⚠️ <b>只给自测 / 探针用</b>：正常启动流程里这两个值永远是 {@code null} / {@code false}，
     * 游戏原有的输入方式（{@code SCANNER.nextLine()}、单数字、{@code y}/{@code n}/{@code next}）
     * 一个字都没改。自测跑完必须还原（传 {@code null, false}），否则会污染同进程后面的用例。
     * <p>
     * ⚠️ 传入的 {@code input} <b>读不出下一行时必须在自己的实现里抛异常</b>
     * （见 {@link SelectionInput#nextLine()}）：{@link IllegalStateException} 会一路冒出来，
     * 而不是让整个自测在无限重来里挂住。
     *
     * @param input     供输入行；{@code null} 恢复成读真标准输入
     * @param exitEarly {@code true} 表示选完就返回
     */
    public static void setInputSourceForSelfTest(SelectionInput input, boolean exitEarly) {
        scriptedLineSource = input;
        exitAfterSelectionForSelfTest = exitEarly;
    }

    /**
     * 一行输入按分隔符 {@value #SEPARATOR} 切出来的"段"：一次读一行，段用完了才读下一行。
     * <p>
     * 选人 / 选对手 / 选奖励这三处都是「输数字 → 输 y 确认 → 输 next」三步走，
     * 有了这个缓存，分段就可以当成"连着的几次输入"来用，于是
     * {@code 1/2/3/y} 能在一行里走完选三个 + 全部确认。
     * <p>
     * <b>分隔符为什么是 {@code /}</b>：它只出现在<b>行首</b>才是命令前缀
     * （{@link CommandManager#isCommand}），所以 {@code 1/2} 不会被命令系统截走；
     * 而整段输入以 {@code /} 开头的那种（{@code /kill @s}）本来就会被
     * {@link #readInput()} 当命令执行掉，永远进不到这里。
     *
     * @author AI（DeepSeek）生成
     */
    public static final class InputReader {
        /**
         * 一行的来源。生产环境是"读真标准输入"，自测是"从脚本里取下一行"；
         * 这里统一成 {@link Supplier}，于是 {@link #hasPending()} 不必再关心输入从哪来。
         */
        private final Supplier<String> lineSource;
        private final Deque<String> pending = new ArrayDeque<>();

        /**
         * 生产用：每一行都从 {@link GameMain#readInput()} 取（真标准输入 + 命令拦截）。
         */
        InputReader() {
            this(GameMain::readInput);
        }

        /**
         * 自测用：每一行都从给定的来源取。
         * <p>
         * ⚠️ 用这个构造器时，来源<b>读不出下一行就必须自己抛异常</b>（脚本用完、或
         * {@link java.io.InputStream} 到 EOF）—— 不能返回 {@code null}：
         * 下面把它当成"输入流结束"，而"输入流结束"会让上层三个阶段的
         * {@code while(true)} 一直重来（真实输入是无限流，正常游戏里不会发生），
         * 于是自测会<b>空转成假死</b>而不是红。
         *
         * @param lineSource 一行来源
         */
        InputReader(Supplier<String> lineSource) {
            this.lineSource = lineSource;
        }

        /**
         * 取下一段输入。
         * <p>
         * ⚠️ <b>它是流式的</b>：缓存空了才读一行（按 {@value #SEPARATOR} 切成段），
         * 一次调用最多读一行、绝不空转。判断"还有没有下一段"请用 {@link #hasPending()}。
         *
         * @return 下一段输入；<b>输入流也读完了才返回 {@code null}</b>（所以它不会空转）
         */
        String read() {
            if (pending.isEmpty() && !hasPending()) {
                return null;
            }
            return pending.pollFirst();
        }

        /**
         * @return <b>当前这一行</b>里还有没有没处理的段（<b>绝不读新的一行</b>）
         */
        boolean hasMoreOnCurrentLine() {
            return !pending.isEmpty();
        }

        /**
         * @return 还有没有没处理的段（会先读一行来填缓存，但<b>不消费</b>它）
         */
        boolean hasPending() {
            if (pending.isEmpty()) {
                String line = lineSource.get();
                if (line == null) {
                    // 输入流已经结束（自测的脚本用完 / 标准输入 EOF）：
                    // 上层会把它当成"这一批没选"，然后无限重来 —— 必须立刻炸，别空转。
                    // ⚠️ 这是**违反了下面那个接口的约定**（不许返回 null），所以是 IllegalStateException；
                    //    它绝不会在正常游戏里发生（真标准输入是无限流）。
                    throw new IllegalStateException(SCRIPT_EXHAUSTED_MARKER
                            + "选择流程读到输入流结束：请再喂一行输入（自测请把脚本补长）");
                }
                for (String segment : line.split(SEPARATOR)) {
                    String trimmed = segment.trim();
                    if (!trimmed.isEmpty()) {
                        pending.addLast(trimmed);
                    }
                }
            }
            return !pending.isEmpty();
        }

        /**
         * 丢掉这一行剩下的段（"结束确认"之后残留的段没有意义，丢掉才不会隔一轮突然生效）。
         */
        void discardPending() {
            pending.clear();
        }
    }

    /**
     * <b>自测专用装配入口</b>：造一个"只读给定脚本"的输入缓存。
     * <p>
     * ⚠️ <b>只给自测 / 探针用</b>（{@link SelectionFlow} 与 {@link InputReader} 都是包内可见的，
     * 外面本来够不着）：正常游戏永远走 {@code new InputReader()} 那一格（读真标准输入）。
     * 有了它，"选人状态机"可以在<b>不碰标准输入</b>的前提下被逐条断言 ——
     * 上一轮的教训正是"给自测加了读真实 stdin 的断言，把整个自测挂死"。
     * <p>
     * ⚠️ 传入的 {@code lineSource} <b>读不出下一行时必须自己抛异常</b>
     * （见 {@link SelectionInput#nextLine()}），别返回 {@code null}。
     *
     * @param lineSource 一行来源
     * @return 输入缓存
     */
    public static InputReader scriptedInputReaderForSelfTest(SelectionInput lineSource) {
        return new InputReader(lineSource::nextLine);
    }

    /**
     * <b>自测专用装配入口</b>：造一个选择状态机（它不持有任何全局状态，可以随便 new）。
     * <p>
     * ⚠️ <b>只给自测 / 探针用</b>：正常游戏里 {@link #startAFight()} 自己 new 一个。
     *
     * @return 选择状态机
     */
    public static SelectionFlow selectionFlowForSelfTest() {
        return new SelectionFlow();
    }

    /**
     * 选择实体 / 物品时的关键字。
     * <p>
     * 分隔符 {@code /}：一行里可以写多个段，例如 {@code 1/2/3}；整行以 {@code /} 开头的是命令，
     * 由 {@link #readInput()} 提前执行掉，不会走到这里。
     */
    private static final String SEPARATOR = "/";

    /**
     * {@link #setInputSourceForSelfTest} 那段脚本用完时抛出的
     * {@link IllegalStateException} 的文案前缀。
     * <p>
     * <b>为什么要有这个记号</b>：真实输入是无限流，正常游戏永远读不到"结束"；
     * 只有自测的脚本会用完。自测据此把这条路当成<b>"脚本跑到底了"</b>（可以断言）
     * 而不是"游戏出错了"—— 同时它是一路冒出来的异常，绝不会被谁悄悄吞掉。
     */
    public static final String SCRIPT_EXHAUSTED_MARKER = "[输入结束]";

    /**
     * 输入是否等于关键字（<b>忽略大小写</b>），或者等于它的<b>缩写</b>
     * （长度 2 到关键字长度-1 的前缀，例如 {@code nex}/{@code ne} → {@code next}）。
     * <p>
     * 单字母的 {@code n}/{@code y}/{@code q} 由各自的调用点显式处理，不走这里。
     * ⚠️ <b>{@code n} 两个阶段都是 {@code next}</b>（老代码里它先匹配 {@code next}）；
     * "放弃这一批"要写全 {@code no} —— 与 {@code 50-COMMANDS.md} 的 §5.10 一致。
     *
     * @param input    一段用户输入
     * @param keyword  完整关键字
     * @return 命中返回 {@code true}
     */
    private static boolean matches(String input, String keyword) {
        if (input == null) {
            return false;
        }
        String value = input.trim();
        if (value.equalsIgnoreCase(keyword)) {
            return true;
        }
        return value.length() >= 2 && value.length() < keyword.length()
                && keyword.regionMatches(true, 0, value, 0, value.length());
    }

    /**
     * 判断顺序固定为 quit → next → no → yes：{@code n} 归 {@code next}（现有约定），
     * {@code no} 必须写全两字母以上。
     */
    private static boolean isQuit(String input) {
        return matches(input, "quit") || "q".equalsIgnoreCase(input == null ? null : input.trim());
    }

    private static boolean isNext(String input) {
        return matches(input, "next") || "n".equalsIgnoreCase(input == null ? null : input.trim());
    }

    private static boolean isYes(String input) {
        return matches(input, "yes") || "y".equalsIgnoreCase(input == null ? null : input.trim());
    }

    /**
     * 确认这一步的 {@code no}（<b>必须写全两个字母</b>）。
     * <p>
     * ⚠️ <b>单个 {@code n} 不是 {@code no}</b>：老约定里"选实体那一步 {@code n} = next、
     * 确认那一步 {@code n} = no"—— 但 {@code n} 在<b>任何</b>阶段都先被 {@link #isNext} 收走，
     * 所以确认阶段它也走"跳过确认"那条路（老代码里两段 {@code if} 就是按这个顺序写的）。
     * 想放弃这一批请写 {@code no}。
     */
    private static boolean isNo(String input) {
        return matches(input, "no");
    }

    /**
     * 把一段输入解析成下标列表，允许 {@code /} 或空白作分隔，例如 {@code 1/2/3}、{@code 1 2 3}。
     *
     * @param input 一段用户输入
     * @return 解析出的下标；含非数字时返回 {@code null}（调用方按"输入错误"处理）
     */
    private static int[] parseIndices(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        String[] parts = input.trim().split("[\\s/]+");
        int[] indices = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                indices[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return indices;
    }

    /**
     * 把一批东西的名字拼成一行。
     */
    private static <T> String namesOf(List<T> things) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < things.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(displayNameOf(things.get(i)));
        }
        return builder.toString();
    }

    /**
     * 取显示名。
     * <p>
     * {@code LivingThing} 与 {@code Item} 各有各的 {@code getName()}（它们没有共同的父类方法），
     * 所以这里显式分一下。
     */
    private static String displayNameOf(Object thing) {
        if (thing instanceof LivingThing living) {
            return living.getName();
        }
        if (thing instanceof Item item) {
            return item.getName();
        }
        return String.valueOf(thing);
    }

    /**
     * 选一批实体（选人 / 选对手共用）。
     *
     * @param inputReader 输入缓存（同一行里的段接着用）
     * @param selected    出参：这一批被确认的东西（下标对应的副本）
     * @param candidates  候选表（下标就是指它的下标）
     * @param operation   这一步在选什么（角色 / 对手）
     * @return {@code true} 表示这一批被确认（调用方把它们收进队伍 / 敌方）
     */
    private static boolean selectionOf(InputReader inputReader, List<LivingThing> selected,
                                       LivingThing[] candidates, String operation) {
        SelectionFlow.Draft<LivingThing> draft = new SelectionFlow().selectBatch(
                inputReader, operation, candidates, LivingThing::getName, LivingThing::getDescription);
        selected.addAll(draft.things());
        return draft.confirmed();
    }

    /**
     * 选一批物品（只有介绍那一行，没有名字那一行）。
     *
     * @param inputReader 输入缓存
     * @param selected    出参：这一批被确认的物品
     * @param candidates  候选表
     * @return {@code true} 表示这一批被确认
     */
    private static boolean selectionOf(InputReader inputReader, List<Item> selected, Item[] candidates) {
        SelectionFlow.Draft<Item> draft = new SelectionFlow().selectBatch(
                inputReader, "奖励", candidates, Item::getName, Item::getDescription);
        selected.addAll(draft.things());
        return draft.confirmed();
    }

    /**
     * 选择实体 / 物品的那一段状态机（选人 / 选对手 / 选奖励三步共用这一份）。
     * <p>
     * 三个阶段都走「输数字 → 输 {@code y} 确认 → 输 {@code next} 前进」：
     * 一个数字选好之后<b>立刻</b>进确认（老行为），同一行里接着写的段（{@code 1/2/3/y}）
     * 当成"连着敲的几次输入"继续处理。
     * <p>
     * ⚠️ 它<b>不持有</b>输入缓存：{@link InputReader} 由调用方传进来，
     * 所以"读了一行"这件事在三个阶段之间是连续的（{@code 1/2/3/y} 后面接着写 {@code next}
     * 仍然落在同一批里）。
     *
     * @author AI（DeepSeek）生成
     */
    public static final class SelectionFlow {

        /**
         * 一批选择的中间状态：选好的副本 + 已经选过的<b>下标</b> + 是否在等确认。
         * <p>
         * ⚠️ <b>去重认的是下标，不是对象</b>：每一份副本都是 {@code copy()} 出来的新实例
         * （uuid 全新，{@code Thing#equals} 按 uuid 判等），所以"同一只选了两遍"在副本这一层
         * 根本认不出来 —— 按下标记账才是可靠的判据。
         *
         * @param things    这一批选好的副本（顺序 = 第一次选中的顺序）
         * @param chosen    这一批已经选过的下标
         * @param confirmed 是否已经被 {@code y}/{@code yes} 确认
         * @param <T>       候选类型
         * @author AI（DeepSeek）生成
         */
        public record Draft<T>(List<T> things, Set<Integer> chosen, boolean confirmed) {
        }

        /**
         * 取下一批：数字段一直攒（{@code 1/2/3}），攒好之后等 {@code y} 确认。
         * <p>
         * <b>老行为为什么必须保住</b>：一个数字选好之后紧接着输 {@code y}，
         * 那句 {@code y} 确认的就是刚刚选好的这一个 —— 确认不能被"必须先收到 {@code next}"挡住。
         * <p>
         * 一批的边界（与 {@code 50-COMMANDS.md} 的 §5.10 一致）：
         * <ul>
         *     <li>{@code y} = 确认这一批（{@code 1/2/3/y} 一次确认整批）；</li>
         *     <li>{@code next} / <b>单个 {@code n}</b> = 收口：选择阶段"不再选了"、确认阶段"跳过确认"
         *     （两个阶段都是它 —— 老代码里 {@code n} 先匹配 {@code next}）；</li>
         *     <li>{@code no}（<b>写全两个字母</b>）= 确认阶段放弃这一批；</li>
         *     <li>确认一旦收口，同一行剩下的段全部丢掉（不许"隔一轮突然生效"）。</li>
         * </ul>
         *
         * @param inputReader   输入缓存
         * @param operation     这一步在选什么（角色 / 对手 / 奖励），只用于提示语
         * @param candidates    候选表
         * @param nameOf        取显示名
         * @param descriptionOf 取介绍
         * @param <T>           候选类型
         * @return 这一批的结果（选好的副本 + 已经选过的下标 + 是否被确认）
         */
        public <T> Draft<T> selectBatch(InputReader inputReader, String operation, T[] candidates,
                                        java.util.function.Function<T, String> nameOf,
                                        java.util.function.Function<T, String> descriptionOf) {
            List<T> selected = new ArrayList<>();
            Set<Integer> chosenIndices = new LinkedHashSet<>();
            boolean pendingConfirm = false;
            while (true) {
                if (!inputReader.hasPending()) {
                    return new Draft<>(selected, chosenIndices, false);
                }
                String input = inputReader.read();
                if (input == null) {
                    // 输入流读完了（正常游戏里不会发生，自测的脚本会用完）
                    return new Draft<>(selected, chosenIndices, false);
                }
                if (isQuit(input)) {
                    System.exit(0);
                }
                if (pendingConfirm) {
                    // ⚠️ 确认这一步的顺序与选择阶段相反：先 no，再 next/yes。
                    //    单个 n 已经在上面被 isNext 收走了（老代码就是这个顺序），
                    //    所以"放弃这一批"要写全 no。
                    if (isNo(input)) {
                        inputReader.discardPending();
                        System.out.println("放弃已选:" + namesOf(selected, nameOf));
                        selected.clear();
                        chosenIndices.clear();
                        pendingConfirm = false;
                        System.out.println("输入下一个数字或next/n");
                        continue;
                    }
                    if (isNext(input)) {
                        // 跳过确认：这一批不加入（与老代码 break 出去的行为一致）
                        inputReader.discardPending();
                        selected.clear();
                        chosenIndices.clear();
                        pendingConfirm = false;
                        System.out.println("输入下一个数字或next/n");
                        continue;
                    }
                    if (isYes(input)) {
                        inputReader.discardPending();
                        System.out.println("已选:" + namesOf(selected, nameOf));
                        return new Draft<>(selected, chosenIndices, true);
                    }
                    System.out.println("输入错误");
                    System.out.println("输入yes/y确认这一批,no/n放弃这一批,next/n跳过确认,quit/q退出游戏");
                    continue;
                }
                if (isNext(input)) {
                    inputReader.discardPending();
                    // 选择阶段收到 next/n：这一批到此为止（不确认、不加入）
                    return new Draft<>(selected, chosenIndices, false);
                }
                if (isNo(input)) {
                    // 选择这一步的单个 n 已经被 isNext 吃掉；能走到这里的是 no/No 这种写全的写法
                    inputReader.discardPending();
                    System.out.println("输入下一个数字或next/n");
                    continue;
                }
                if (isYes(input)) {
                    if (selected.isEmpty()) {
                        System.out.println("请先输入数字选择" + operation);
                        continue;
                    }
                    // 老行为：数字选好之后紧接着输 y，确认的就是刚刚选好的这一批
                    inputReader.discardPending();
                    System.out.println("已选:" + namesOf(selected, nameOf));
                    return new Draft<>(selected, chosenIndices, true);
                }
                int[] indices = parseIndices(input);
                if (indices == null) {
                    System.out.println("输入错误");
                    // 一段里混进非数字就整段作废（与老版本一致），剩下的段不留着"隔一轮生效"
                    inputReader.discardPending();
                    continue;
                }
                for (int index : indices) {
                    if (index < 0 || index >= candidates.length) {
                        // 越界 / 负数只跳过这一个下标，同一行里其它下标照旧生效
                        System.out.println("输入错误");
                        continue;
                    }
                   // if (!chosenIndices.add(index)) {
                        // 重复下标静默只算一次（不报错、不刷屏；同一行里写两遍、跨行再写一遍都算）
                   //     continue;
                   // }
                    T candidate = copyOf(candidates[index]);
                    selected.add(candidate);
                    System.out.println(nameOf.apply(candidate));
                    System.out.println(descriptionOf.apply(candidate));
                }
                if (selected.isEmpty()) {
                    // 这一行一个有效下标都没有：只报错，不重新列名单
                    continue;
                }
                if (inputReader.hasMoreOnCurrentLine()) {
                    // ⚠️ 这里只能问"**当前这一行**还有没有段"：一旦顺手问 hasPending()，
                    //    它会把**下一行**读进来当缓存，于是玩家敲的那个 y 会被当成
                    //    "这一行还没结束"的下一段 —— 数字后面紧跟的 y 就永远确认不了。
                    continue;
                }
                // 这一行读完了：把"已选 …"和确认提示打出来，然后等确认
                System.out.println("已选" + operation + ":" + namesOf(selected, nameOf));
                System.out.println("输入yes/y确认这一批(" + selected.size()
                        + " 个),no/n放弃这一批,next/n跳过确认,quit/q退出游戏");
                pendingConfirm = true;
            }
        }

        /**
         * @return 新的一份副本（每一份都是新 uuid，所以 {@code contains} 靠"按 uuid 判等"认出重复）
         */
        @SuppressWarnings("unchecked")
        private static <T> T copyOf(T candidate) {
            if (candidate instanceof LivingThing living) {
                return (T) living.copy();
            }
            if (candidate instanceof Item item) {
                return (T) item.copy();
            }
            return candidate;
        }

        /**
         * 把一批东西的名字拼成一行。
         */
        private static <T> String namesOf(List<T> things, java.util.function.Function<T, String> nameOf) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < things.size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(nameOf.apply(things.get(i)));
            }
            return builder.toString();
        }
    }
}
