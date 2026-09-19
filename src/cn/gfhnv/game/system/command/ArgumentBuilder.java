package cn.gfhnv.game.system.command;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 命令构建器，同时也是它描述的那个节点。
 * <p>
 * 一条命令树用「一层一个变量」的写法搭出来，每个构建器<b>自己就是一个可挂载的节点</b>：
 *
 * <pre>{@code
 * LiteralCommandNode root = node();
 * ArgumentBuilder target = ArgumentBuilder.argumentBuilder("目标", EntityArgumentType.entities());
 * ArgumentBuilder add = target.literal("add");
 * ArgumentBuilder effect = add.argument("效果", WordArgumentType.word());
 * effect.executes((context, source) -> { ... });
 * root.addChild(target);
 * }</pre>
 *
 * <h2>设计要点</h2>
 * <ul>
 *     <li>父子关系只在 {@link CommandNode#addChild(CommandNode)} 一处建立。
 *     因为构建器本身就是节点，<b>不需要</b>再调用任何 build 方法；</li>
 *     <li>每个节点只描述<b>自己</b>：名字、类型、执行体、执行条件；</li>
 *     <li>建树请<b>一层一个变量、最后再把最外层挂上去</b>，不要把长链直接当
 *     {@code addChild} 的参数：那样挂上去的是最内层，中间的层不会进树；</li>
 *     <li>需要让调用处类型更明确时可以用 {@link #toNode()}，它返回自身。</li>
 * </ul>
 *
 * <h2>链式规则</h2>
 * <ul>
 *     <li>{@link #literal(String)} / {@link #argument(String, ArgumentType)}：建一个子分支、
 *     挂到本节点上，并返回那个<b>子分支</b>（后续调用作用在子分支上）；</li>
 *     <li>{@link #then(Command)}：挂一整棵子命令树；</li>
 *     <li>{@link #executes(CommandExecutor)} / {@link #requires(Predicate)} / {@link #redirect(Command)}：
 *     作用在<b>本节点</b>上并返回自身。</li>
 * </ul>
 *
 * @author AI（DeepSeek）生成
 */
public class ArgumentBuilder extends CommandNode {

    /**
     * 本分支名（字面量文本或参数名）。
     */
    private final String name;

    /**
     * 参数类型；{@code null} 表示本分支是字面量。
     */
    private final ArgumentType<?> argumentType;

    /**
     * 构造一个<b>字面量</b>分支。
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
     * 构造一个<b>参数</b>分支。
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
     * 静态工厂：建一个字面量分支构建器。
     *
     * @param literal 字面量文本
     * @return 构建器
     */
    public static ArgumentBuilder literalBuilder(String literal) {
        return new ArgumentBuilder(literal);
    }

    /**
     * 静态工厂：建一个参数分支构建器。
     *
     * @param argumentName 参数名
     * @param type         参数类型
     * @return 构建器
     */
    public static ArgumentBuilder argumentBuilder(String argumentName, ArgumentType<?> type) {
        return new ArgumentBuilder(argumentName, type);
    }

    /* ------------------------------------------------------------------
     * CommandNode 契约
     * ------------------------------------------------------------------ */

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getUsageText() {
        return argumentType == null ? name : "<" + name + ">";
    }

    @Override
    public void parse(StringReader reader, CommandContext context) throws CommandSyntaxException {
        if (argumentType == null) {
            // 字面量分支：行为与 LiteralCommandNode.parse 完全一致（大小写不敏感地比对当前这个词）。
            // 构建器建出来的字面量分支也是节点，解析器会真的调用它来吃掉这一层输入。
            reader.skipWhitespace();
            int start = reader.getCursor();
            String word = reader.readWord();
            if (!word.equalsIgnoreCase(name)) {
                throw CommandSyntaxException.at(reader, start,
                        "此处需要「" + name + "」，实际读到「" + word + "」");
            }
            return;
        }
        Object value = argumentType.parse(reader);
        context.putArgument(name, value);
    }

    @Override
    public boolean isLiteralNode() {
        return argumentType == null;
    }

    /**
     * @return 参数类型；字面量分支返回 {@code null}
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
     * @return 本节点自己的执行体（不含子节点）；没绑定则返回 {@code null}
     */
    public CommandNode.CommandExecutor getOwnExecutor() {
        return getExecutor();
    }

    /* ------------------------------------------------------------------
     * 建树
     * ------------------------------------------------------------------ */

    /**
     * 建一个下级字面量分支，挂到本节点上，并返回那个分支。
     *
     * @param literal 字面量文本
     * @return 新分支（本身也是节点，可直接继续链式或交给 addChild）
     */
    public ArgumentBuilder literal(String literal) {
        ArgumentBuilder child = new ArgumentBuilder(literal);
        addChild(child);
        return child;
    }

    /**
     * 建一个下级参数分支，挂到本节点上，并返回那个分支。
     *
     * @param argumentName 参数名
     * @param type         参数类型
     * @return 新分支
     */
    public ArgumentBuilder argument(String argumentName, ArgumentType<?> type) {
        ArgumentBuilder child = new ArgumentBuilder(argumentName, type);
        addChild(child);
        return child;
    }

    /**
     * 挂上一整棵子命令树。
     *
     * @param command 子命令
     * @return 自身
     */
    public ArgumentBuilder then(Command command) {
        if (command != null) {
            addChild(command.build());
        }
        return this;
    }

    /**
     * 给本节点绑定执行体。
     *
     * @param executor 执行体
     * @return 自身
     */
    public ArgumentBuilder executes(CommandNode.CommandExecutor executor) {
        setExecutor(executor);
        return this;
    }

    /**
     * 给本节点设置执行条件。
     *
     * @param predicate 条件
     * @return 自身
     */
    public ArgumentBuilder requires(Predicate<CommandSource> predicate) {
        setRequires(predicate);
        return this;
    }

    /**
     * 把剩余输入重定向给另一个命令解析。
     *
     * @param command 目标命令
     * @return 自身
     */
    public ArgumentBuilder redirect(Command command) {
        setRedirect(command);
        return this;
    }

    /**
     * @return 本构建器自身（它已经是节点，可直接用于 {@code addChild}）
     */
    public CommandNode toNode() {
        return this;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + getUsageText()
                + ", 可执行=" + isExecutable()
                + ", 子节点=" + getChildrenNames() + "}";
    }

    /**
     * 便于子类在需要时列出自己的子分支（调试用）。
     *
     * @return 子节点名列表
     */
    public List<String> childNames() {
        return new ArrayList<>(getChildrenNames());
    }
}
