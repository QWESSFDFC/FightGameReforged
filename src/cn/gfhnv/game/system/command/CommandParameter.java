package cn.gfhnv.game.system.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 命令参数容器：旧版命令系统的核心数据结构，现在作为 {@link CommandContext} 的<b>兼容视图</b>保留。
 * <p>
 * 旧版要求命令自己把输入字符串切成 {@code List<ParameterEntry>} 再按下标取值；
 * 新设计把解析下沉到了 {@link ArgumentType}，参数改为<b>按名字</b>存放在 {@link CommandContext} 里，
 * 因此本类现在的职责只剩「让仍引用它的代码继续编译、继续按顺序拿到参数」。
 * <p>
 * 新写的命令请直接用 {@link #getContext()}（或干脆用 {@code executes((context, source) -> ...)}）：
 * <pre>{@code
 * List<LivingThing> targets = parameter.getContext().getLivingThings("目标");
 * }</pre>
 * 不要再依赖 {@link #getParameters()} 的下标顺序。
 *
 * @author AI（DeepSeek）生成
 */
public class CommandParameter {

    /**
     * 本次执行对应的上下文。
     */
    private final CommandContext context;

    /**
     * 旧的顺序参数列表（由上下文按解析顺序生成）。
     */
    private final List<ParameterEntry> parameters = new ArrayList<>();

    /**
     * 构造一个空参数容器（无上下文）。
     */
    public CommandParameter() {
        this.context = null;
    }

    /**
     * 用上下文构造参数容器。
     *
     * @param context 命令上下文
     */
    public CommandParameter(CommandContext context) {
        this.context = context;
        if (context != null) {
            for (Map.Entry<String, Object> entry : context.getArguments().entrySet()) {
                parameters.add(new ParameterEntry(guessType(entry.getValue()), entry.getValue()));
            }
        }
    }

    /**
     * 猜一个参数值对应的旧版类型标签，仅用于兼容 {@link ParameterEntry}。
     *
     * @param value 参数值
     * @return 类型标签
     */
    private static CommandParameterType guessType(Object value) {
        if (value instanceof EntitySelector) {
            return CommandParameterType.ENTITY_SELECTOR;
        }
        if (value instanceof cn.gfhnv.game.entity.Entity) {
            return CommandParameterType.ENTITIES;
        }
        if (value instanceof cn.gfhnv.game.item.Item) {
            return CommandParameterType.ITEMS;
        }
        return CommandParameterType.STRING;
    }

    /**
     * @return 命令上下文；旧构造器创建的对象返回 {@code null}
     */
    public CommandContext getContext() {
        return context;
    }

    /**
     * @return 按解析顺序排列的参数列表
     */
    public List<ParameterEntry> getParameters() {
        return parameters;
    }

    /**
     * 按名字取参数（推荐用法，转发给上下文）。
     *
     * @param name 参数名
     * @param type 期望类型
     * @param <T>  期望类型
     * @return 参数值
     * @throws CommandSyntaxException 参数缺失或类型不符时抛出
     */
    public <T> T get(String name, Class<T> type) throws CommandSyntaxException {
        if (context == null) {
            throw CommandSyntaxException.create("本参数容器没有上下文，无法按名字取值：" + name);
        }
        return context.getArgument(name, type);
    }

    @Override
    public String toString() {
        return "CommandParameter{" + (context == null ? "无上下文" : context.describeArguments()) + "}";
    }
}
