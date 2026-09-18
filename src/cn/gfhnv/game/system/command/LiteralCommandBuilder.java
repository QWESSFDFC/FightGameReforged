package cn.gfhnv.game.system.command;

import java.util.function.Predicate;

/**
 * 注解式命令用的构建器：{@link CommandRegistration.CommandBuilder} 用不到字面量专门类，
 * 这里保留一个薄封装是为了让 {@code @Subcommand} 方法能继续写
 * {@code builder.literal("add").executes(...)} 并拿到明确类型。
 * <p>
 * 节点类型的判定完全交给 {@link ArgumentBuilder}：有参数类型就是参数节点，
 * 否则是字面量节点（见 {@link ArgumentBuilder#createNode()}）。
 *
 * @author AI（DeepSeek）生成
 */
public class LiteralCommandBuilder extends ArgumentBuilder {

    /**
     * 构造一个字面量构建器。
     *
     * @param literal 字面量文本
     */
    public LiteralCommandBuilder(String literal) {
        super(literal);
    }

    /**
     * 构造一个参数构建器（本类主要用于字面量，但保留参数构造器让子类能共用）。
     *
     * @param argumentName 参数名
     * @param type         参数类型
     */
    public LiteralCommandBuilder(String argumentName, ArgumentType<?> type) {
        super(argumentName, type);
    }

    @Override
    public LiteralCommandBuilder literal(String literal) {
        return (LiteralCommandBuilder) super.literal(literal);
    }

    /**
     * 挂一个参数分支。返回的是<b>新分支</b>（{@link ArgumentBuilder} 的契约）。
     *
     * @param argumentName 参数名
     * @param type         参数类型
     * @return 参数分支的构建器
     */
    @Override
    public ArgumentBuilder argument(String argumentName, ArgumentType<?> type) {
        return super.argument(argumentName, type);
    }

    @Override
    public LiteralCommandBuilder then(Command command) {
        super.then(command);
        return this;
    }

    @Override
    public LiteralCommandBuilder executes(CommandNode.CommandExecutor executor) {
        super.executes(executor);
        return this;
    }

    @Override
    public LiteralCommandBuilder requires(Predicate<CommandSource> predicate) {
        super.requires(predicate);
        return this;
    }

    @Override
    public LiteralCommandBuilder redirect(Command command) {
        super.redirect(command);
        return this;
    }
}
