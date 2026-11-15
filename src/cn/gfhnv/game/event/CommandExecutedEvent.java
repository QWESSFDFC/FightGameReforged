package cn.gfhnv.game.event;

import cn.gfhnv.game.system.command.CommandResult;

/**
 * <b>一条命令执行完毕</b>（成功或失败都发）。
 * <p>
 * 适合做命令审计、模组命令的链式触发。事件在命令真正跑完之后发布，不能取消。
 * <p>
 * 发布点：{@code CommandManager#executeResult(String, Fight)}。
 *
 * @author AI（DeepSeek）生成
 */
public class CommandExecutedEvent extends Event {

    private final String input;
    private final CommandResult result;

    public CommandExecutedEvent(String input, CommandResult result) {
        this.input = input;
        this.result = result;
    }

    /**
     * @return 原始输入（含前缀，例如 {@code /data get entity @s}）
     */
    public String getInput() {
        return input;
    }

    /**
     * @return 执行结果（含成功/失败、影响对象数、报错）
     */
    public CommandResult getResult() {
        return result;
    }
}
