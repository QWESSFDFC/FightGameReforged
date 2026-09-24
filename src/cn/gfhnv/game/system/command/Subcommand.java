package cn.gfhnv.game.system.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注 {@link CommandRegistration} 里的「子命令」方法。
 * <p>
 * 一个注解值可能对应<b>两个</b>方法，它们靠方法签名区分：
 * <ul>
 *     <li><b>构建方法</b>：{@code public CommandBuilder xxx(CommandBuilder builder)}
 *     —— 描述这个子命令需要哪些参数/子分支；</li>
 *     <li><b>执行方法</b>：{@code public int xxx(CommandContext context, CommandSource source)}
 *     —— 真正干活；返回影响到的对象数量，返回 {@code void} 等价于返回 1。</li>
 * </ul>
 * 注解值就是子命令路径，支持多级，用空格分隔：
 * <pre>{@code
 * @Subcommand("") // 挂在命令根上
 * @Subcommand("add") // /my add
 * @Subcommand("team add")    // /my team add
 * }</pre>
 *
 * @author AI（DeepSeek）生成
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Subcommand {

    /**
     * 子命令路径。空串或 {@code "."} 表示挂在命令根上。
     *
     * @return 子命令路径
     */
    String value() default "";

    /**
     * 优先级：数字越小越先处理（构建方法先构建，执行方法先执行）。
     * <p>
     * 与项目的 {@code @SubscribeEvent(priority)} 保持一致的直觉。
     *
     * @return 优先级
     */
    int priority() default 3;

    /**
     * 说明文本，用于 {@code help} 之类的命令展示。
     *
     * @return 说明
     */
    String description() default "";
}
