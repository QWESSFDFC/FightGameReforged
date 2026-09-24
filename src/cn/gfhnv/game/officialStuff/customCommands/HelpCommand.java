package cn.gfhnv.game.officialStuff.customCommands;

import cn.gfhnv.game.system.command.*;

/**
 * {@code /help} —— 查看命令列表与用法。
 * <p>
 * 用法：
 * <pre>
 * /help            列出所有已注册的命令
 * /help kill       查看 kill 命令的用法与可用的下一步
 * </pre>
 * <p>
 * 说明：命令树的根字面量必须与命令名一致，因此 {@code /?} 这类别名无法挂在同一棵树上，
 * 而是由 {@link Alias} 这个独立命令类承担（见下方内部类）。
 *
 * @author AI（DeepSeek）生成
 */
public class HelpCommand extends Command {

    /**
     * 构造 {@code /help} 命令。
     */
    public HelpCommand() {
        super("help");
    }

    /**
     * 构建命令树：{@code help}、{@code help <命令名>}。
     *
     * @return 命令根节点
     */
    @Override
    protected CommandNode buildNode() {
        LiteralCommandNode root = node();

        // 分支一：help（列出全部）
        root.setExecutor((context, source) -> {
            CommandManager.printUsage(null);
            return CommandManager.getRegisteredCommandNames().size();
        });

        // 分支二：help <命令名>
        ArgumentBuilder name = ArgumentBuilder.argumentBuilder("命令名", StringArgumentType.word());
        name.executes((context, source) -> {
            CommandManager.printUsage(context.getString("命令名", null));
            return 1;
        });
        root.addChild(name);

        return root;
    }

    /**
     * {@code /?} 别名命令：与 {@code /help} 完全等价。
     * <p>
     * 单独写一个类只是为了让命令树的根字面量与实际命令名一致；
     * 需要更多别名时照抄本类即可。
     *
     * @author AI（DeepSeek）生成
     */
    public static class Alias extends Command {

        /**
         * 构造别名命令。
         *
         * @param name 别名（例如 {@code ?}）
         */
        public Alias(String name) {
            super(name);
        }

        /**
         * 构建别名命令树。
         *
         * @return 命令根节点
         */
        @Override
        protected CommandNode buildNode() {
            LiteralCommandNode root = node();

            root.setExecutor((context, source) -> {
                CommandManager.printUsage(null);
                return CommandManager.getRegisteredCommandNames().size();
            });

            ArgumentBuilder argument = ArgumentBuilder.argumentBuilder("命令名", StringArgumentType.word());
            argument.executes((context, source) -> {
                CommandManager.printUsage(context.getString("命令名", null));
                return 1;
            });
            root.addChild(argument);

            return root;
        }
    }
}
