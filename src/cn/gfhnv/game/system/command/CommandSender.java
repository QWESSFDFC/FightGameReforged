package cn.gfhnv.game.system.command;

import cn.gfhnv.game.entity.LivingThing;

/**
 * 命令来源：谁在执行这条命令，以及命令的输出往哪里去。
 * <p>
 * 对应 MC 的 {@code CommandSourceStack}。本项目里「来源」同时承担
 * {@link CommandContext} 的职责（见 {@link CommandSource}），因此命令实现里
 * 一般直接拿 {@link CommandSource} 用，只在需要自定义输出时（例如把消息写进
 * 模组自己的界面）才实现本接口。
 * <p>
 * 需要注意：{@link #sendError(String)} 的默认实现会把消息同时写到标准错误流与
 * {@code logs/latest.log}，与项目既有日志行为一致。
 *
 * @author AI（DeepSeek）生成
 */
public interface CommandSender {

    /**
     * 输出一条普通消息（命令成功时的反馈）。
     *
     * @param message 消息
     */
    void sendMessage(String message);

    /**
     * 输出一条错误消息。默认写到标准错误流并记入日志。
     *
     * @param message 错误消息
     */
    default void sendError(String message) {
        System.err.println("[命令错误] " + message);
        log("[命令错误] " + message);
    }

    /**
     * 输出一条反馈消息（{@code sendFeedback} 与 MC 命名保持一致）。
     *
     * @param message 消息
     */
    default void sendFeedback(String message) {
        sendMessage(message);
    }

    /**
     * 写日志。
     *
     * @param message 日志内容
     */
    default void log(String message) {
        cn.gfhnv.game.system.logSystem.LogWriter.writeLog(message);
    }

    /**
     * @return 执行命令的玩家生物；控制台或未选角色时为 {@code null}
     */
    LivingThing getPlayer();

    /**
     * @return 本来源的名称（用于提示文本，如玩家名或「控制台」）
     */
    String getName();

    /**
     * 把本来源包成一个可执行的 {@link CommandSource}。
     * <p>
     * 若本对象本身就是 {@link CommandSource}，直接返回自身。
     *
     * @return 命令来源
     */
    default CommandSource getSource() {
        if (this instanceof CommandSource commandSource) {
            return commandSource;
        }
        return new CommandSource(this.getPlayer(), this.getName());
    }
}
