package cn.gfhnv.game.system.command;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 命令构建器：用链式写法描述一条命令的树结构。
 *
 * <h2>它<b>不是</b>节点</h2>
 * 本类刻意<b>不继承</b> {@link CommandNode}：它只是「攒参数、攒子节点、最后填充成一个节点」的
 * 小工具。父子关系只在 {@link CommandNode#addChild(CommandNode)} 一处建立，
 * 因此不会出现「中间层丢失 / 父指针为 null」：
 *
 * <pre>{@code
 * ArgumentBuilder builder = new ArgumentBuilder("kill");           // 只负责攒东西
 * CommandNode root = builder.literal("hand").executes(...).build(); // 一次性填充成节点
 * }</pre>
 *
 * <h2>链式规则</h2>
 * <ul>
 *     <li>{@link #literal(String)} / {@link #argument(String, ArgumentType)} / {@link #then(Command)}：
 *     <b>返回新分支的构建器</b>（挂到本构建器上，但还没建节点）；</li>
 *     <li>{@link #executes(CommandExecutor)} / {@link #requires(Predicate)} / {@link #redirect(Command)}：
 *     作用在<b>本构建器</b>上，返回自身；</li>
 *     <li>{@link #build()}：把本构建器（连同它下面已经攒好的子树）填充成一个真正的节点。
 *     <b>必须对「要挂到父节点上的那一层」调用 build()</b>，详见 {@link #build()} 的说明。</li>
 * </ul>
 *
 * @author AI（DeepSeek）生成
 */
public class ArgumentBuilder {

    /**
     * 本分支名（字面量文本或参数名）。
     */
    private final String name;

    /**
     * 参数类型；{@code null} 表示本分支是字面量。
     */
    private ArgumentType<?> argumentType;

    /**
     * 已攒下的子分支。
     */
    private final List<ArgumentBuilder> children = new ArrayList<>();

    /**
     * 已挂上的子命令（整棵子树）。
     */
    private final List<Command> commands = new ArrayList<>();

    /**
     * 执行体。
     */
    private CommandNode.CommandExecutor executor;

    /**
     * 执行条件。
     */
    private Predicate<CommandSource> requires = source -> true;

    /**
     * 重定向目标。
     */
    private Command redirect;

    /**
     * 是否已经 build 过（防止「先 build 再加子节点」这种顺序错误静默丢东西）。
     */
    private boolean built = false;

    /**
     * 构造一个<b>字面量</b>分支构建器。
     *
     * @param literal 字面量文本
     */
    public ArgumentBuilder(String literal) {
        if (literal == null || literal.isBlank()) {
            throw new IllegalArgumentException("字面量不能为空");
        }
        this.name = literal;
        this.argumentType = null;
    }

    /**
     * 构造一个<b>参数</b>分支构建器。
     *
     * @param argumentName 参数名
     * @param type         参数类型
     */
    public ArgumentBuilder(String argumentName, ArgumentType<?> type) {
        if (argumentName == null || argumentName.isBlank()) {
            throw new IllegalArgumentException("参数名不能为空");
        }
        if (type == null) {
            throw new IllegalArgumentException("参数类型不能为空：" + argumentName);
        }
        this.name = argumentName;
        this.argumentType = type;
    }

    /**
     * 静态工厂：建一个字面量构建器。
     *
     * @param literal 字面量文本
     * @return 构建器
     */
    public static ArgumentBuilder literalBuilder(String literal) {
        return new ArgumentBuilder(literal);
    }

    /**
     * 静态工厂：建一个参数构建器。
     *
     * @param argumentName 参数名
     * @param type         参数类型
     * @return 构建器
     */
    public static ArgumentBuilder argumentBuilder(String argumentName, ArgumentType<?> type) {
        return new ArgumentBuilder(argumentName, type);
    }

    /* ------------------------------------------------------------------
     * 攒结构
     * ------------------------------------------------------------------ */

    /**
     * 挂一个字面量分支。
     *
     * @param literal 字面量文本
     * @return 新分支的构建器
     */
    public ArgumentBuilder literal(String literal) {
        ArgumentBuilder child = new ArgumentBuilder(literal);
        return addChildBuilder(child);
    }

    /**
     * 挂一个参数分支。
     *
     * @param argumentName 参数名
     * @param type         参数类型
     * @return 新分支的构建器
     */
    public ArgumentBuilder argument(String argumentName, ArgumentType<?> type) {
        ArgumentBuilder child = new ArgumentBuilder(argumentName, type);
        return addChildBuilder(child);
    }

    /**
     * 把一个构建器挂到本构建器下（子类建自身类型的分支时用这个）。
     *
     * @param child 子构建器
     * @param <B>   子构建器类型
     * @return 同一个子构建器
     */
    protected final <B extends ArgumentBuilder> B addChildBuilder(B child) {
        checkNotBuilt();
        children.add(child);
        return child;
    }

    /**
     * 挂一整棵子命令树。
     *
     * @param command 子命令
     * @return 自身
     */
    public ArgumentBuilder then(Command command) {
        if (command != null) {
            checkNotBuilt();
            commands.add(command);
        }
        return this;
    }

    /**
     * 给本分支绑定执行体。
     *
     * @param executor 执行体
     * @return 自身
     */
    public ArgumentBuilder executes(CommandNode.CommandExecutor executor) {
        this.executor = executor;
        return this;
    }

    /**
     * 给本分支设置执行条件。
     *
     * @param predicate 条件
     * @return 自身
     */
    public ArgumentBuilder requires(Predicate<CommandSource> predicate) {
        this.requires = predicate == null ? source -> true : predicate;
        return this;
    }

    /**
     * 把剩余输入重定向给另一个命令解析。
     *
     * @param command 目标命令
     * @return 自身
     */
    public ArgumentBuilder redirect(Command command) {
        this.redirect = command;
        return this;
    }

    /* ------------------------------------------------------------------
     * 读取状态
     * ------------------------------------------------------------------ */

    /**
     * @return 本分支名（字面量文本或参数名）
     */
    public String getName() {
        return name;
    }

    /**
     * @return 参数类型；不是参数分支时返回 {@code null}
     */
    public ArgumentType<?> getArgumentType() {
        return argumentType;
    }

    /**
     * @return 是否是参数分支
     */
    public boolean isArgument() {
        return argumentType != null;
    }

    /**
     * @return 本分支是否已经绑定了执行体
     */
    public boolean isExecutable() {
        return executor != null || redirect != null;
    }

    /**
     * @return 本分支自己的执行体（不含子分支）；没绑则返回 {@code null}
     */
    public CommandNode.CommandExecutor getOwnExecutor() {
        return executor;
    }

    /**
     * @return 用法文本，例如 {@code kill} 或 {@code <目标>}
     */
    public String getUsageText() {
        return argumentType == null ? name : "<" + name + ">";
    }

    /**
     * @return 已攒下的子分支（只读用途）
     */
    public List<ArgumentBuilder> getChildBuilders() {
        return new ArrayList<>(children);
    }

    /**
     * @return 已攒下的子分支名
     */
    public List<String> getChildNames() {
        List<String> names = new ArrayList<>();
        for (ArgumentBuilder child : children) {
            names.add(child.getName());
        }
        return names;
    }

    /* ------------------------------------------------------------------
     * 构建
     * ------------------------------------------------------------------ */

    /**
     * 把本构建器填充成一个真正的节点（含全部子分支与子命令）。
     * <p>
     * 本方法只能成功执行一次：再次调用会抛异常，避免「build 之后又加子节点」这种
     * 静默丢结构的写法。
     *
     * @return 节点（参数分支得到 {@link ArgumentCommandNode}，字面量分支得到 {@link LiteralCommandNode}）
     */
    public CommandNode build() {
        checkNotBuilt();
        built = true;
        CommandNode node = createNode();
        node.setExecutor(executor);
        node.setRequires(requires);
        if (redirect != null) {
            node.setRedirect(redirect);
        }
        for (ArgumentBuilder child : children) {
            node.addChild(child.build());
        }
        for (Command command : commands) {
            node.addChild(command.build());
        }
        CommandDispatcher.debugParse("build：构建器「" + getUsageText() + "」→ 节点 "
                + node.getClass().getSimpleName() + "(" + node.getName() + ")，子节点="
                + node.getChildrenNames());
        return node;
    }

    /**
     * 子类覆写点：创建「本分支对应的空节点」。
     *
     * @return 空节点
     */
    protected CommandNode createNode() {
        if (argumentType != null) {
            return new ArgumentCommandNode(name, argumentType);
        }
        return new LiteralCommandNode(name);
    }

    /**
     * 检查构建顺序：已经 build 过就不允许再改结构。
     */
    private void checkNotBuilt() {
        if (built) {
            throw new IllegalStateException(
                    "构建器「" + name + "」已经 build() 过了，不能再添加子分支。"
                            + "请先搭完整棵树，最后再调用 build()（命令树的根节点必须最后 build）。");
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + getUsageText()
                + ", 可执行=" + (executor != null)
                + ", 子分支=" + getChildNames() + "}";
    }
}
