package cn.gfhnv.game.system.command;

import cn.gfhnv.game.system.logSystem.LogWriter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 基于「注解 + 方法」的命令注册方式：把若干个方法拼成一棵命令树。
 * <p>
 * 用法：继承本类，在带 {@link Subcommand} 注解的方法里描述树结构并返回
 * {@link CommandBuilder}；执行方法则用「同样的注解值 + {@code (CommandContext, CommandSource)} 参数」。
 * 构建/执行方法可以写在同一个类里，也可以分开：
 *
 * <pre>{@code
 * public class GiveCommand extends CommandRegistration {
 *     public GiveCommand() { super("give"); }
 *
 *     @Subcommand("")
 *     public CommandBuilder give(CommandBuilder builder) {
 *         return builder.argument("目标", EntityArgumentType.entities())
 *                       .argument("物品", WordArgumentType.word());
 *     }
 *
 *     @Subcommand("")
 *     public int giveRun(CommandContext context, CommandSource source) throws CommandSyntaxException {
 *         String itemId = context.getString("物品", null);
 *         source.sendMessage("给 " + context.getEntity("目标").getName() + " 一个 " + itemId);
 *         return 1;
 *     }
 * }
 * }</pre>
 *
 * <h2>规则</h2>
 * <ol>
 *     <li><b>构建方法</b>：有 {@link Subcommand} 注解、<b>恰好一个</b> {@link CommandBuilder} 参数、
 *     返回类型为 {@link CommandBuilder}（或 void）；</li>
 *     <li><b>执行方法</b>：有 {@link Subcommand} 注解、参数表为
 *     {@code (CommandContext, CommandSource)}（第一个也可以是 {@code CommandContext} 的子类，
 *     例如 {@link CommandSource}）、返回 {@code int} 或 {@code void}；</li>
 *     <li>注解值就是<b>子命令路径</b>：{@code "add"}、{@code "team add"}、空串或 {@code "."} 表示
 *     挂在命令根上；</li>
 *     <li>同一个路径上的多个执行方法会按 {@link Subcommand#priority()} 依次执行（数字小的先），
 *     返回值相加；</li>
 *     <li>方法可以是 {@code public}/{@code protected}/{@code private}，也可以是 {@code static}；
 *     非 static 方法由本类在构建时创建的那个实例来调用。</li>
 * </ol>
 * <p>
 * <b>注意</b>：本类在 {@link CommandDispatcher} 里注册时，会额外用自己的无参构造器再创建一个实例
 * 专门用于构建（避免与执行用的实例共享状态），因此子类<b>必须提供可访问的无参构造器</b>。
 *
 * @author AI（DeepSeek）生成
 */
public abstract class CommandRegistration extends Command {

    /**
     * 用于执行命令的实例（注册时传进来的那个对象）。
     */
    private final Object instance;

    /**
     * 构造一个注解式命令。
     *
     * @param name     命令名
     * @param instance 执行命令时使用的实例（通常是 {@code this}）
     */
    public CommandRegistration(String name, Object instance) {
        super(name);
        this.instance = instance == null ? this : instance;
    }

    /**
     * 构造一个注解式命令，执行实例为 {@code this}。
     *
     * @param name 命令名
     */
    public CommandRegistration(String name) {
        this(name, null);
    }

    /**
     * @return 执行命令时使用的实例
     */
    protected Object getInstance() {
        return instance;
    }

    /**
     * 扫描注解方法，拼出完整命令树。
     *
     * @return 命令根节点
     */
    @Override
    protected CommandNode buildNode() {
        CommandBuilder root = new CommandBuilder(getName());
        Object builderInstance = createBuilderInstance();
        List<Method> executeMethods = new ArrayList<>();
        List<Method> subBuilders = new ArrayList<>();

        for (Method method : instance.getClass().getDeclaredMethods()) {
            Subcommand annotation = method.getAnnotation(Subcommand.class);
            if (annotation == null) {
                continue;
            }
            method.setAccessible(true);
            if (isBuilderMethod(method)) {
                subBuilders.add(method);
            } else if (isExecuteMethod(method)) {
                executeMethods.add(method);
            } else {
                throw new IllegalStateException("@" + Subcommand.class.getSimpleName() + " 方法签名不合法："
                        + method.getName() + "。构建方法应为 (CommandBuilder) -> CommandBuilder，"
                        + "执行方法应为 (CommandContext, CommandSource) -> int");
            }
        }

        subBuilders.sort(Comparator.comparingInt(m -> m.getAnnotation(Subcommand.class).priority()));
        for (Method method : subBuilders) {
            // 注意用 ArgumentBuilder 接：literal()/argument() 的契约是「返回分支构建器」，
            // 具体类型可能是 CommandBuilder，也可能只是基类型，用父类型最稳。
            ArgumentBuilder target = route(root, method.getAnnotation(Subcommand.class).value());
            Object caller = Modifier.isStatic(method.getModifiers()) ? null : builderInstance;
            invokeBuilder(method, caller, target);
        }
        executeMethods.sort(Comparator.comparingInt(m -> m.getAnnotation(Subcommand.class).priority()));
        for (Method method : executeMethods) {
            bindExecutor(root, method.getAnnotation(Subcommand.class).value(), method);
        }

        if (root.childNames().isEmpty() && !hasExecutor(root)) {
            LogWriter.writeLog("命令 " + getName() + " 没有任何 @Subcommand 方法");
        }
        // 根节点本身就是可挂载的节点，直接返回
        return root;
    }

    /**
     * 判断构建器（或它下面的某个分支）是否已经绑了执行体。
     *
     * @param builder 构建器
     * @return 是否有执行体
     */
    private static boolean hasExecutor(ArgumentBuilder builder) {
        if (builder.isExecutable()) {
            return true;
        }
        for (CommandNode child : builder.getChildren()) {
            if (child instanceof ArgumentBuilder childBuilder && hasExecutor(childBuilder)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 创建一个专门用于「构建」的实例。
     * <p>
     * 默认复用执行实例（要求它有无参构造器）。若执行实例是通过带参构造器创建的，
     * 子类可以覆写本方法返回任意实例——只要能调用到它的构建方法即可。
     *
     * @return 构建用实例
     */
    protected Object createBuilderInstance() {
        try {
            java.lang.reflect.Constructor<?> constructor = instance.getClass().getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException e) {
            // 没有无参构造器时退而求其次：直接用执行实例来构建
            return instance;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法创建命令构建实例：" + instance.getClass().getName(), e);
        }
    }

    /**
     * 判断是否为「构建方法」。
     *
     * @param method 方法
     * @return 是否为构建方法
     */
    private static boolean isBuilderMethod(Method method) {
        Class<?>[] parameters = method.getParameterTypes();
        if (parameters.length != 1 || !CommandBuilder.class.isAssignableFrom(parameters[0])) {
            return false;
        }
        Class<?> returnType = method.getReturnType();
        return returnType == void.class || CommandBuilder.class.isAssignableFrom(returnType);
    }

    /**
     * 判断是否为「执行方法」。
     *
     * @param method 方法
     * @return 是否为执行方法
     */
    private static boolean isExecuteMethod(Method method) {
        Class<?>[] parameters = method.getParameterTypes();
        if (parameters.length != 2) {
            return false;
        }
        if (!CommandContext.class.isAssignableFrom(parameters[0])) {
            return false;
        }
        if (!CommandSource.class.isAssignableFrom(parameters[1])) {
            return false;
        }
        Class<?> returnType = method.getReturnType();
        return returnType == void.class || returnType == int.class || returnType == Integer.class;
    }

    /**
     * 调用一个构建方法。方法自己会用 {@code builder.literal(...)/argument(...)} 往树上挂分支，
     * 因此这里不需要再做合并。
     *
     * @param method   构建方法
     * @param target   调用目标（静态方法传 {@code null}）
     * @param argument 传入的构建器
     */
    private void invokeBuilder(Method method, Object target, ArgumentBuilder argument) {
        try {
            method.invoke(target, argument);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("构建命令 " + getName() + " 时，方法 " + method.getName() + " 抛出异常", cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法调用构建方法 " + method.getName(), e);
        }
    }

    /**
     * 按注解值（子命令路径）在树里走出/建出对应分支。
     *
     * @param root 根构建器
     * @param path 子命令路径，空串或 {@code "."} 表示根
     * @return 路径末端的构建器
     */
    private static ArgumentBuilder route(ArgumentBuilder root, String path) {
        ArgumentBuilder current = root;
        if (path == null || path.isBlank() || path.equals(".")) {
            return current;
        }
        for (String segment : path.trim().split("\\s+")) {
            if (segment.isBlank()) {
                continue;
            }
            current = current.literal(segment);
        }
        return current;
    }

    /**
     * 为某个路径分支绑定执行方法。
     *
     * @param root   根构建器
     * @param path   子命令路径
     * @param method 执行方法
     */
    private void bindExecutor(ArgumentBuilder root, String path, Method method) {
        ArgumentBuilder target = route(root, path);
        Method callTarget = method;
        Object receiver = Modifier.isStatic(method.getModifiers()) ? null : instance;
        String commandName = getFullName();
        CommandNode.CommandExecutor previous = target.getOwnExecutor();

        target.executes((context, source) -> {
            int mine = invokeExecutor(callTarget, receiver, context, source);
            int theirs = previous == null ? 0 : previous.run(context, source);
            return mine + theirs;
        });
        LogWriter.writeLog("注册命令执行方法：" + commandName + " " + callTarget.getName());
    }

    /**
     * 反射调用一个执行方法。
     *
     * @param method   执行方法
     * @param receiver 调用目标（静态方法传 {@code null}）
     * @param context  命令上下文
     * @param source   命令来源
     * @return 影响到的对象数量
     * @throws CommandSyntaxException 方法内部抛出的语法异常
     */
    private static int invokeExecutor(Method method, Object receiver, CommandContext context, CommandSource source)
            throws CommandSyntaxException {
        try {
            Object result = method.invoke(receiver, context, source);
            if (result instanceof Integer value) {
                return value;
            }
            return 1;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CommandSyntaxException syntaxException) {
                throw syntaxException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw CommandSyntaxException.create("执行命令时出错：" + (cause == null ? e : cause));
        } catch (ReflectiveOperationException e) {
            throw CommandSyntaxException.create("无法调用命令方法 " + method.getName() + "：" + e);
        }
    }

    /**
     * 命令构建器：用于 {@link Subcommand} 方法的参数与返回值。
     * <p>
     * 它就是 {@link ArgumentBuilder}，只是把链式方法的返回类型收窄成 {@code CommandBuilder}，
     * 让注解式命令的构建方法写起来类型更明确：
     * <pre>{@code
     * @Subcommand("add")
     * public CommandBuilder add(CommandBuilder builder) {
     *     return builder.argument("数量", IntegerArgumentType.integer(1, 99));
     * }
     * }</pre>
     * 节点类型由「有没有参数类型」决定：有就是参数节点，没有就是字面量节点。
     *
     * @author AI（DeepSeek）生成
     */
    public static class CommandBuilder extends ArgumentBuilder {

        /**
         * 构造一个<b>字面量</b>分支构建器。
         *
         * @param name 字面量文本
         */
        public CommandBuilder(String name) {
            super(name);
        }

        /**
         * 构造一个<b>参数</b>分支构建器。
         *
         * @param argumentName 参数名
         * @param type         参数类型
         */
        public CommandBuilder(String argumentName, ArgumentType<?> type) {
            super(argumentName, type);
        }

        @Override
        public CommandBuilder then(Command command) {
            super.then(command);
            return this;
        }

        @Override
        public CommandBuilder executes(CommandNode.CommandExecutor executor) {
            super.executes(executor);
            return this;
        }

        @Override
        public CommandBuilder requires(java.util.function.Predicate<CommandSource> predicate) {
            super.requires(predicate);
            return this;
        }

        @Override
        public CommandBuilder redirect(Command command) {
            super.redirect(command);
            return this;
        }

        /**
         * 建一个下级字面量分支（自动挂到本节点上）。
         *
         * @param literal 字面量文本
         * @return 新分支
         */
        @Override
        public CommandBuilder literal(String literal) {
            CommandBuilder child = new CommandBuilder(literal);
            addChild(child);
            return child;
        }

        /**
         * 建一个下级参数分支（自动挂到本节点上）。
         *
         * @param argumentName 参数名
         * @param argumentType 参数类型
         * @return 新分支
         */
        @Override
        public CommandBuilder argument(String argumentName, ArgumentType<?> argumentType) {
            CommandBuilder child = new CommandBuilder(argumentName, argumentType);
            addChild(child);
            return child;
        }
    }
}
