package cn.gfhnv.game.system.command;

import java.util.List;

/**
 * 命令执行结果：把「执行是否成功 / 影响到了几个对象 / 反馈文本」打包返回。
 * <p>
 * 与 MC 的 {@code CommandResult} 语义一致：{@link #getResult()} 为影响到的对象数量，
 * 等于 0 时视为「命令执行了，但没有产生影响」（例如 {@code @e[type=不存在]} 没选中东西）。
 * <p>
 * 本类是不可变的值对象，通过 {@link #success(int, String)} / {@link #failure(String)} 创建。
 *
 * @author AI（DeepSeek）生成
 */
public class CommandResult {

    /**
     * 影响到的对象数量。
     */
    private final int result;

    /**
     * 反馈文本（可为 {@code null}）。
     */
    private final String message;

    /**
     * 执行过程中产生的错误；成功时为 {@code null}。
     */
    private final CommandSyntaxException error;

    /**
     * 解析出的参数说明（调试用，可为 {@code null}）。
     */
    private final String arguments;

    /**
     * 构造一个执行结果。
     *
     * @param result    影响到的对象数量
     * @param message   反馈文本
     * @param error     错误
     * @param arguments 参数说明
     */
    private CommandResult(int result, String message, CommandSyntaxException error, String arguments) {
        this.result = result;
        this.message = message;
        this.error = error;
        this.arguments = arguments;
    }

    /**
     * 成功结果。
     *
     * @param result  影响到的对象数量
     * @param message 反馈文本
     * @return 执行结果
     */
    public static CommandResult success(int result, String message) {
        return new CommandResult(result, message, null, null);
    }

    /**
     * 失败结果。
     *
     * @param error 错误
     * @return 执行结果
     */
    public static CommandResult failure(CommandSyntaxException error) {
        return new CommandResult(-1, null, error, null);
    }

    /**
     * 补上参数说明（链式）。
     *
     * @param arguments 参数说明
     * @return 新的执行结果
     */
    public CommandResult withArguments(String arguments) {
        return new CommandResult(result, message, error, arguments);
    }

    /**
     * @return 影响到的对象数量；失败时为 -1
     */
    public int getResult() {
        return result;
    }

    /**
     * @return 反馈文本；可为 {@code null}
     */
    public String getMessage() {
        return message;
    }

    /**
     * @return 错误；成功时为 {@code null}
     */
    public CommandSyntaxException getError() {
        return error;
    }

    /**
     * @return 解析出的参数说明；可为 {@code null}
     */
    public String getArguments() {
        return arguments;
    }

    /**
     * @return 是否成功
     */
    public boolean isSuccess() {
        return error == null;
    }

    /**
     * 把结果转成一行可读文本（用于测试与调试输出）。
     *
     * @return 可读文本
     */
    @Override
    public String toString() {
        if (error != null) {
            return "失败：" + error.getMessage();
        }
        return "成功（影响 " + result + " 个对象）" + (message == null ? "" : "：" + message);
    }

    /**
     * 便捷方法：把若干消息拼成一行。
     *
     * @param lines 消息行
     * @return 拼接结果
     */
    public static String join(List<String> lines) {
        return String.join("；", lines);
    }
}
