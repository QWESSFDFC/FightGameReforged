package cn.gfhnv.game.officialStuff.customCommands;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.system.command.*;

import java.util.List;

/**
 * {@code /execute} —— 以指定对象的身份运行另一条命令。
 * <p>
 * 用法：
 * <pre>
 * /execute as &lt;目标&gt; run &lt;命令&gt;
 * </pre>
 * 示例：
 * <pre>
 * /execute as @e[type=CommonInsect] run kill @s        让每只普通虫「杀死自己」
 * /execute as @p run hurt @s 10                        把 10 点伤害算到最近的那个生物头上
 * /execute as @s run list                              以自己身份看一遍状态（等价于 /list）
 * /execute as @e[type=CommonInsect] run effect @s add frozen    给每只虫子挂冰冻
 * </pre>
 * <p>
 * <b>{@code as} 换掉的只有「执行者」</b>：内层命令里的 {@code @s} 指向被指定的那个目标，
 * {@code @p} / {@code @n} / {@code @r} 也改成以它为中心来找最近的生物；
 * 战斗范围（{@code @a} / {@code @e}）与原来完全一致，所以 {@code /execute as ...} 不会跑到别的战斗里去。
 * 目标有多个时，内层命令会<b>逐个目标各执行一次</b>，返回值是各次影响对象数之和（与 MC 一致）。
 * <p>
 * <b>嵌套上限</b>：{@code /execute as @s run execute as @s run ...} 这种自己套自己最多允许
 * {@value #MAX_DEPTH} 层，超过就报错 —— 否则会一路递归到 {@link StackOverflowError}，
 * 那种异常比一句报错难查得多。
 * <p>
 * <b>内层命令的失败</b>由<b>最外层</b>的 {@code execute} 包一句
 * 「以『谁』的身份执行『什么』失败：<真正的原因>」再抛出，这样多目标时能看出是哪一个目标出了问题；
 * 嵌套的里层直接原样抛出（每层都包会让报错叠成一长串，真正的原因被埋在最里面）。
 * 内层命令自己的输出（例如 {@code /list} 的表格）照常打印。
 *
 * @author AI（DeepSeek）生成
 */
public class ExecuteCommand extends Command {

    /**
     * 允许的最大嵌套层数（{@code execute} 里再套 {@code execute}）。
     */
    private static final int MAX_DEPTH = 8;

    /**
     * 当前已经嵌套了几层。
     * <p>
     * 命令系统是单线程的（都从游戏主循环的输入里进来），所以用一个静态计数就够了；
     * 每次执行都在 {@code finally} 里减回去，异常也不会把计数留在脏状态。
     */
    private static int depth = 0;

    /**
     * 构造 {@code /execute} 命令。
     */
    public ExecuteCommand() {
        super("execute");
    }

    /**
     * 以每个目标为执行者，运行 {@code run} 后面那条命令。
     *
     * @param context 命令上下文（提供 {@code 目标} 与 {@code 命令} 两个参数）
     * @param source  命令来源
     * @return 各次执行影响到的对象数量之和
     * @throws CommandSyntaxException 没有写内层命令、嵌套过深、或内层命令执行失败时抛出
     */
    private static int runAs(CommandContext context, CommandSource source) throws CommandSyntaxException {
        List<LivingThing> targets = context.getLivingThings("目标");
        String command = context.getString("命令", null);
        if (command == null || command.isBlank()) {
            throw CommandSyntaxException.create("run 后面要写一条要执行的命令，例如 /execute as @s run list");
        }
        if (depth >= MAX_DEPTH) {
            throw CommandSyntaxException.create("execute 嵌套超过 " + MAX_DEPTH
                    + " 层了，请检查是不是把 execute 套在了自己身上");
        }

        int total = 0;
        depth++;
        // 只有最外层的 execute 负责把失败包成「以谁的身份执行失败」。
        // 每层都包的话，深层嵌套的报错会叠成一长串（每层都要重复一遍内层命令文本），
        // 真正的原因被埋在最里面；内层直接原样抛出即可。
        boolean outermost = depth == 1;
        try {
            for (LivingThing target : targets) {
                // 只换执行者：战斗范围、选择器上下文都由新的 CommandSource 沿用同一个当前战斗
                CommandSource asTarget = new CommandSource(target);
                try {
                    total += CommandManager.getDispatcher().execute(command, asTarget);
                } catch (CommandSyntaxException e) {
                    if (!outermost) {
                        throw e;
                    }
                    throw CommandSyntaxException.create("以「" + EntityArgumentType.nameOf(target)
                            + "」的身份执行「" + brief(command) + "」失败：" + e.getRawMessage());
                }
            }
        } finally {
            depth--;
        }

        String who = targets.size() == 1
                ? EntityArgumentType.nameOf(targets.get(0))
                : targets.size() + " 个目标";
        source.sendMessage("已以「" + who + "」的身份执行「" + brief(command) + "」，共影响 " + total + " 个对象。");
        return total;
    }

    /**
     * 把内层命令文本压成一行短文本，用于回显与报错。
     * <p>
     * 嵌套 {@code execute} 时内层命令本身可能很长（里面又套着好几层），
     * 原样打进消息里会变成一大段；这里只留前 40 个字符。
     *
     * @param command 内层命令文本
     * @return 单行短文本
     */
    private static String brief(String command) {
        String oneLine = command.replace('\n', ' ').replace('\r', ' ').trim();
        return oneLine.length() <= 40 ? oneLine : oneLine.substring(0, 40) + "…";
    }

    /**
     * 构建命令树：{@code execute as <目标> run <命令>}。
     * <p>
     * 一层一个变量地搭：{@code as} → {@code <目标>} → {@code run} → {@code <命令>}，
     * 最内层的 {@code <命令>} 是贪婪字符串（吃掉 {@code run} 后面的整行），
     * 由执行体再交给调度器解析，这样 {@code run} 后面写什么命令都不用在树里再描述一遍。
     *
     * @return 命令根节点
     */
    @Override
    protected CommandNode buildNode() {
        LiteralCommandNode root = node();

        // 第一层只能用静态工厂建：literal() / argument() 是 ArgumentBuilder 的方法，
        // 命令根节点（LiteralCommandNode）只有 addChild 这类节点方法。
        ArgumentBuilder as = ArgumentBuilder.literalBuilder("as");
        root.addChild(as);
        ArgumentBuilder target = as.argument("目标", EntityArgumentType.entities());
        ArgumentBuilder run = target.literal("run");
        ArgumentBuilder inner = run.argument("命令", StringArgumentType.greedyString());
        inner.executes(ExecuteCommand::runAs);

        return root;
    }
}
