package cn.gfhnv.game.officialStuff.customCommands;

import cn.gfhnv.game.system.command.Command;
import cn.gfhnv.game.system.command.CommandDispatcher;

/**
 * 官方命令的统一注册入口。
 * <p>
 * 由 {@link cn.gfhnv.game.system.command.CommandManager#initialize()} 调用。
 * 模组要加自己的命令，可以仿照这里的写法：
 * <pre>{@code
 * CommandDispatcher dispatcher = CommandManager.getDispatcher();
 * dispatcher.register(new MyCommand());
 * }</pre>
 *
 * @author AI（DeepSeek）生成
 */
public final class OfficialCommands {

    /**
     * 工具类不允许实例化。
     */
    private OfficialCommands() {
    }

    /**
     * 把官方命令注册到指定调度器。
     *
     * @param dispatcher 调度器
     */
    public static void registerAll(CommandDispatcher dispatcher) {
        if (dispatcher == null) {
            return;
        }
        Command[] commands = new Command[]{
                new KillCommand(),
                new ListCommand(),
                new HurtCommand(),
                new EndFightCommand(),
                new HelpCommand(),
                new HelpCommand.Alias("?")
        };
        for (Command command : commands) {
            dispatcher.register(command);
        }
    }
}
