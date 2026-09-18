package cn.gfhnv.game.system.command;

/**
 * 参数节点：调用 {@link ArgumentType} 解析出一段输入，并以名字存入 {@link CommandContext}。
 * <p>
 * 举例：{@code .argument("目标", EntityArgumentType.entity())} 会生成一个名为
 * {@code 目标} 的参数节点，玩家输入 {@code @s} 时，{@link EntitySelector} 会被
 * 存进上下文，命令实现里用 {@code context.getArgument("目标", EntitySelector.class)} 取出。
 *
 * @author AI（DeepSeek）生成
 */
public class ArgumentCommandNode extends CommandNode {

    /**
     * 参数名（也是命令实现中取值的键）。
     */
    private final String name;

    /**
     * 参数类型。
     */
    private final ArgumentType<?> type;

    /**
     * 构造一个参数节点。
     *
     * @param name 参数名
     * @param type 参数类型
     */
    public ArgumentCommandNode(String name, ArgumentType<?> type) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("参数名不能为空");
        }
        if (type == null) {
            throw new IllegalArgumentException("参数类型不能为空：" + name);
        }
        this.name = name;
        this.type = type;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * @return 参数类型
     */
    public ArgumentType<?> getType() {
        return type;
    }

    @Override
    public void parse(StringReader reader, CommandContext context) throws CommandSyntaxException {
        Object value = type.parse(reader);
        CommandDispatcher.debugParse("参数节点「" + name + "」解析出 "
                + (value == null ? "null" : value.getClass().getSimpleName()) + " = " + value);
        context.putArgument(name, value);
        CommandDispatcher.debugParse("写入后参数表=" + context.getArguments());
    }

    @Override
    public String getUsageText() {
        return "<" + name + ">";
    }
}
