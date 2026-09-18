package cn.gfhnv.game.system.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 命令树节点：一条命令被拆成「字面量」与「参数」交替的树，解析时沿树往下走。
 * <p>
 * 结构参考《我的世界》Java 版 Brigadier：
 * <pre>{@code
 * kill            （字面量节点，不可执行）
 *  └─ <目标>      （参数节点，绑定执行体，可执行）
 * give
 *  ├─ <目标>
 *  │   └─ <物品>
 *  └─ hand        （字面量节点，可执行）
 * }</pre>
 * <p>
 * 每个节点记录：
 * <ul>
 *     <li>{@link #getChildren()}：子节点（按名字注册，解析时逐个尝试）；</li>
 *     <li>{@link #isExecutable()}：是否绑定了执行体（只有可执行节点才能作为命令结尾，除非有 {@link #getRedirect()}）；</li>
 *     <li>{@link #getRequires()}：是否允许执行（权限/条件判定，例如「战斗进行中才允许」）；</li>
 *     <li>{@link #getUsage()}：这个分支的用法文本（报错时展示）。</li>
 * </ul>
 *
 * @author AI（DeepSeek）生成
 */
public abstract class CommandNode {

    /**
     * 子节点表：字面量名（或参数名）→ 节点。使用 {@link LinkedHashMap} 保证注册顺序稳定，
 * 这样解析与提示的顺序对用户是可预期的。
     */
    private final Map<String, CommandNode> children = new LinkedHashMap<>();

    /**
     * 父节点；根节点为 {@code null}。
     */
    private CommandNode parent;

    /**
     * 执行体；{@code null} 表示该节点本身不可执行（只能继续往下走）。
     */
    private CommandExecutor executor;

    /**
     * 是否允许执行。
     */
    private Predicate<CommandSource> requires = source -> true;

    /**
     * 是否为「重定向」节点：解析到该节点后，把剩余输入交给另一个命令去解析。
     */
    private Command redirect;

    /**
     * 注册一个子节点。若同名子节点已存在，则把新节点的子节点与执行体合并过去，
     * 这样模组可以用「同名命令续写分支」的方式扩展已有命令。
     *
     * @param node 子节点
     */
    public void addChild(CommandNode node) {
        if (node == null) {
            return;
        }
        CommandNode existing = children.get(node.getName());
        if (existing == null) {
            node.setParent(this);
            children.put(node.getName(), node);
            return;
        }
        if (node.getExecutor() != null) {
            existing.setExecutor(node.getExecutor());
        }
        if (node.getRedirect() != null) {
            existing.setRedirect(node.getRedirect());
        }
        for (CommandNode child : node.getChildren()) {
            existing.addChild(child);
        }
    }

    /**
     * @return 节点名（字面量文本或参数名）
     */
    public abstract String getName();

    /**
     * 尝试从读取器当前位置匹配本节点。
     *
     * @param reader  输入读取器
     * @param context 命令上下文（参数会被写入其中）
     * @throws CommandSyntaxException 本节点匹配失败时抛出
     */
    public abstract void parse(StringReader reader, CommandContext context) throws CommandSyntaxException;

    /**
     * 本节点在用法提示里的样子。
     *
     * @return 例如 {@code kill} 或 {@code <目标>}
     */
    public abstract String getUsageText();

    /**
     * @return 子节点列表（按注册顺序）
     */
    public List<CommandNode> getChildren() {
        return new ArrayList<>(children.values());
    }

    /**
     * 按名字取子节点。
     *
     * @param name 子节点名
     * @return 子节点；不存在返回 {@code null}
     */
    public CommandNode getChild(String name) {
        if (name == null) {
            return null;
        }
        return children.get(name);
    }

    /**
     * @return 子节点的名字列表（按注册顺序）
     */
    public List<String> getChildrenNames() {
        return new ArrayList<>(children.keySet());
    }

    /**
     * @return 父节点；根节点为 {@code null}
     */
    public CommandNode getParent() {
        return parent;
    }

    /**
     * 设置父节点。
     *
     * @param parent 父节点
     */
    public void setParent(CommandNode parent) {
        this.parent = parent;
    }

    /**
     * @return 是否绑定了执行体
     */
    public boolean isExecutable() {
        return executor != null || redirect != null;
    }

    /**
     * @return 执行体；没有则返回 {@code null}
     */
    public CommandExecutor getExecutor() {
        return executor;
    }

    /**
     * 绑定执行体。
     *
     * @param executor 执行体
     */
    public void setExecutor(CommandExecutor executor) {
        this.executor = executor;
    }

    /**
     * @return 是否允许执行
     */
    public Predicate<CommandSource> getRequires() {
        return requires;
    }

    /**
     * 设置执行条件。
     *
     * @param requires 条件
     */
    public void setRequires(Predicate<CommandSource> requires) {
        this.requires = requires == null ? source -> true : requires;
    }

    /**
     * @return 重定向到的命令；没有则返回 {@code null}
     */
    public Command getRedirect() {
        return redirect;
    }

    /**
     * 设置重定向命令。
     *
     * @param redirect 目标命令
     */
    public void setRedirect(Command redirect) {
        this.redirect = redirect;
    }

    /**
     * 从根节点一路走回本节点，拼出完整用法文本。
     * <p>
     * 根节点的 {@link #getUsageText()} 是 {@code "/"}（它本身就是那个斜杠），
     * 所以这里要跳过它，否则会拼出 {@code // kill} 这种双斜杠。
     *
     * @return 例如 {@code /kill <目标>}
     */
    public String getFullUsage() {
        List<String> parts = new ArrayList<>();
        CommandNode current = this;
        while (current != null) {
            String text = current.getUsageText();
            if (text != null && !text.isEmpty() && !"/".equals(text)) {
                parts.add(0, text);
            }
            current = current.getParent();
        }
        if (parts.isEmpty()) {
            return "/";
        }
        return "/" + String.join(" ", parts);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + getName()
                + ", executable=" + isExecutable()
                + ", children=" + getChildrenNames() + "}";
    }

    /**
     * 命令执行体：真正干活的地方。
     * <p>
     * 与 MC 的 {@code Command} 函数式接口一致：返回值为「影响到的对象数量」，
     * 例如杀了 3 个生物就返回 3；返回 0 表示成功但没有实际影响。
     * 抛出 {@link CommandSyntaxException} 表示执行失败（会被统一打印成错误信息）。
     *
     * @author AI（DeepSeek）生成
     */
    @FunctionalInterface
    public interface CommandExecutor {

        /**
         * 执行命令。
         *
         * @param context 命令上下文（可取参数、当前战斗、执行者）
         * @param source  命令来源（用于输出消息）
         * @return 影响到的对象数量
         * @throws CommandSyntaxException 执行失败时抛出
         */
        int run(CommandContext context, CommandSource source) throws CommandSyntaxException;
    }
}
