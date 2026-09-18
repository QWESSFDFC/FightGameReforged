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
 *         root.addChild(argument("目标", EntityArgumentType.entities())
 *                 .executes((context, source) -> {
 *                     List<LivingThing> targets = context.getLivingThings("目标");
 *                     for (LivingThing target : targets) target.setHp(0);
 *                     return targets.size();
 *                 })
 *                 .build());          // 子分支最后 build，再挂到根上
 *         return root;
 *     }
 * }
 * }</pre>
 *
 * <h2>多条分支的写法</h2>
 * <pre>{@code
 * LiteralCommandNode root = node();
 * root.setExecutor(...);                                  // /cmd 本身可执行
 * root.addChild(argument("目标", ...).executes(...).build()); // /cmd <目标>
 * root.addChild(literal("hand").executes(...).build());       // /cmd hand
 * }</pre>
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
     * 便捷方法：建一个参数分支构建器。
     *
     * @param argumentName 参数名
     * @param type         参数类型
     * @return 参数分支构建器
     */
    protected ArgumentBuilder argument(String argumentName, ArgumentType<?> type) {
        return ArgumentBuilder.argumentBuilder(argumentName, type);
    }

    /**
     * 便捷方法：建一个字面量分支构建器。
     *
     * @param literal 字面量文本
     * @return 字面量分支构建器
     */
    protected ArgumentBuilder literal(String literal) {
        return ArgumentBuilder.literalBuilder(literal);
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
