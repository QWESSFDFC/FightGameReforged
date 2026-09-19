package cn.gfhnv.game.system.command;

import java.util.List;

/**
 * 命令基类：一条命令 = 一个名字 + 一棵子树。
 * <p>
 * 这是一个很薄的抽象：子类只需要在 {@link #buildNode()} 里用
 * {@link ArgumentBuilder} 搭出自己的树并返回根节点。
 *
 * <h2>写法（推荐）</h2>
 * <pre>{@code
 * public class KillCommand extends Command {
 *     public KillCommand() { super("kill"); }
 *
 *     @Override
 *     protected LiteralCommandNode buildNode() {
 *         LiteralCommandNode root = node();
 *
 *         // 先建这一层的节点，绑好执行体，再交给父节点
 *         ArgumentBuilder target = ArgumentBuilder.argumentBuilder(
 *                 "目标", EntityArgumentType.entities());
 *         target.executes((context, source) -> {
 *             List<LivingThing> targets = context.getLivingThings("目标");
 *             for (LivingThing t : targets) t.setHp(0);
 *             return targets.size();
 *         });
 *         root.addChild(target);
 *
 *         return root;
 *     }
 * }
 * }</pre>
 *
 * <h2>多层与多分支</h2>
 * <pre>{@code
 * // 多层：外层建好之后，把内层挂到它上面，最后把外层交给 root
 * ArgumentBuilder outer = ArgumentBuilder.argumentBuilder("目标", ...);
 * ArgumentBuilder inner = outer.argument("数值", ...);
 * inner.executes(...);
 * root.addChild(outer);
 *
 * // 多分支：分支都从【同一个】外层节点上长出来
 * ArgumentBuilder target = ArgumentBuilder.argumentBuilder("目标", ...);
 * ArgumentBuilder add = target.literal("add");
 * ArgumentBuilder addArgument = add.argument("效果", ...);
 * addArgument.executes(...);
 * ArgumentBuilder remove = target.literal("remove");
 * remove.executes(...);
 * root.addChild(target);
 * }</pre>
 *
 * <h2>建树规则</h2>
 * <p>
 * {@code literal(...)} / {@code argument(...)} 返回的是<b>新建出来的那个子节点</b>，
 * 所以不要把长链直接当 {@code addChild} 的参数
 * （{@code root.addChild(argumentBuilder("目标", ...).literal("add"))} 挂上去的是 {@code add} 那一层，
 * 外面的 {@code 目标} 根本不在树里），也不要把同一个分支建两遍
 * （同名节点会在 {@code addChild} 里合并，第二次建出来的那个对象会被丢弃）。
 * 一层一个变量、最后只挂最外层，树一定是对的。
 *
 * @author AI（DeepSeek）生成
 */
public abstract class Command {

    /**
     * 命令名（命令树的第一个字面量）。
     */
    private final String commandName;

    /**
     * 构造一条命令。
     *
     * @param name 命令名
     */
    public Command(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("命令名不能为空");
        }
        this.commandName = name;
    }

    /**
     * @return 命令名
     */
    public String getName() {
        return commandName;
    }

    /**
     * 从根到本命令的完整名字（子命令会带上级名字，用空格分隔）。
     *
     * @return 例如 {@code give hand}
     */
    public String getFullName() {
        return String.join(" ", getPath());
    }

    /**
     * 从根到本命令的路由片段。默认只有命令名一段，子命令类可以覆写追加。
     *
     * @return 路由片段列表
     */
    public List<String> getPath() {
        return List.of(commandName);
    }

    /**
     * 构建命令树。
     *
     * @return 命令的根节点
     */
    public final CommandNode build() {
        CommandNode root = buildNode();
        if (root == null) {
            throw new IllegalStateException("命令 " + commandName + " 的 buildNode() 返回了 null");
        }
        return root;
    }

    /**
     * 子类实现：搭出本命令的树并返回根节点。
     *
     * @return 命令的根节点
     */
    protected abstract CommandNode buildNode();

    /**
     * 建一个以命令名命名的字面量根节点。
     *
     * @return 字面量节点
     */
    protected LiteralCommandNode node() {
        return new LiteralCommandNode(commandName);
    }

    /**
     * 用法文本。
     *
     * @return 例如 {@code /kill}
     */
    public String usage() {
        return "/" + getFullName();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + getFullName() + "}";
    }
}
