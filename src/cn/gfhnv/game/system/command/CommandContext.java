package cn.gfhnv.game.system.command;

import cn.gfhnv.game.entity.Entity;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.system.fight.Fight;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 命令上下文：一次命令执行过程中「解析出来的参数」+「执行环境」的集合。
 * <p>
 * 与 MC 的 {@code CommandContext} 一样，参数按<b>名字</b>存放（而不是按下标），
 * 因此在命令树中间插入新参数不会让已有命令的取值代码错位。
 * 取值时有若干便捷方法，拿不到会给出可读的报错而不是 {@code null}：
 * <pre>{@code
 * List<LivingThing> targets = context.getLivingThings("目标");
 * int amount = context.getInt("数量", null, 0);   // 第二个参数是备用名，第三个是默认值
 * }</pre>
 * <p>
 * 本类同时也是 {@link CommandSource} 的父类，因此「上下文」和「来源」是同一个对象时
 * （绝大多数情况）可以直接调用 {@link #getFight()}、{@link #getPlayer()}。
 *
 * @author AI（DeepSeek）生成
 */
public class CommandContext {

    /**
     * 已解析的参数：参数名 → 值。用 {@link LinkedHashMap} 保持解析顺序，便于调试输出。
     */
    private final Map<String, Object> arguments = new LinkedHashMap<>();
    /**
     * 命令来源。{@link CommandSource} 会在构造时把自身写进来，因此不是 final。
     */
    private CommandSender sender;

    /**
     * 构造一个命令上下文。
     * <p>
     * <b>注意</b>：{@code sender} 为 {@code null} 时这里<b>不会立刻</b>创建控制台来源，
     * 而是在第一次真正用到来源时惰性创建（见 {@link #getSender()}）。
     * 原因：{@link CommandSource} 的构造器会调用 {@code super(null)} 再把自己写回来，
     * 如果这里立刻就 {@code new CommandSource()}，就会无限递归到 {@link StackOverflowError}。
     *
     * @param sender 命令来源，可为 {@code null}
     */
    public CommandContext(CommandSender sender) {
        this.sender = sender;
    }

    /* ------------------------------------------------------------------
     * 参数存取
     * ------------------------------------------------------------------ */

    /**
     * 把参数值转成简短可读文本。
     *
     * @param value 参数值
     * @return 可读文本
     */
    private static String describeValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof EntitySelector selector) {
            return selector.toString();
        }
        if (value instanceof Entity entity) {
            return EntityArgumentType.nameOf(entity);
        }
        return String.valueOf(value);
    }

    /**
     * 写入一个解析好的参数。
     *
     * @param name  参数名
     * @param value 参数值
     */
    public void putArgument(String name, Object value) {
        if (name == null) {
            return;
        }
        arguments.put(name, value);
    }

    /**
     * 取参数（必填）。参数不存在、类型不匹配时会抛出可读异常。
     *
     * @param name 参数名
     * @param type 期望类型
     * @param <T>  期望类型
     * @return 参数值
     * @throws CommandSyntaxException 参数缺失或类型不符时抛出
     */
    public <T> T getArgument(String name, Class<T> type) throws CommandSyntaxException {
        Object value = arguments.get(name);
        if (value == null) {
            throw CommandSyntaxException.create("缺少参数「" + name + "」（已解析的参数："
                    + arguments.keySet() + "）");
        }
        if (type != null && !type.isInstance(value)) {
            throw CommandSyntaxException.create("参数「" + name + "」应为 "
                    + type.getSimpleName() + "，实际为 " + value.getClass().getSimpleName());
        }
        return type == null ? null : type.cast(value);
    }

    /**
     * 取参数（可选）。先按名字找，找不到再按别名找。
     *
     * @param name  参数名
     * @param alias 备用名（可为 {@code null}）
     * @param type  期望类型
     * @param <T>   期望类型
     * @return 参数值；不存在返回 {@code null}
     * @throws CommandSyntaxException 类型不符时抛出
     */
    public <T> T getOptionalArgument(String name, String alias, Class<T> type) throws CommandSyntaxException {
        Object value = arguments.get(name);
        if (value == null && alias != null) {
            value = arguments.get(alias);
        }
        if (value == null) {
            return null;
        }
        if (type != null && !type.isInstance(value)) {
            throw CommandSyntaxException.create("参数「" + name + "」应为 "
                    + type.getSimpleName() + "，实际为 " + value.getClass().getSimpleName());
        }
        return type == null ? null : type.cast(value);
    }

    /**
     * 取字符串参数。
     *
     * @param name  参数名
     * @param alias 备用名
     * @return 字符串；不存在返回 {@code null}
     * @throws CommandSyntaxException 类型不符时抛出
     */
    public String getString(String name, String alias) throws CommandSyntaxException {
        return getOptionalArgument(name, alias, String.class);
    }

    /**
     * 取整数参数。
     *
     * @param name     参数名
     * @param alias    备用名
     * @param fallback 参数不存在时的默认值
     * @return 整数
     * @throws CommandSyntaxException 类型不符时抛出
     */
    public int getInt(String name, String alias, int fallback) throws CommandSyntaxException {
        Integer value = getOptionalArgument(name, alias, Integer.class);
        return value == null ? fallback : value;
    }

    /**
     * 取长整数参数。
     *
     * @param name     参数名
     * @param alias    备用名
     * @param fallback 参数不存在时的默认值
     * @return 长整数
     * @throws CommandSyntaxException 类型不符时抛出
     */
    public long getLong(String name, String alias, long fallback) throws CommandSyntaxException {
        Long value = getOptionalArgument(name, alias, Long.class);
        return value == null ? fallback : value;
    }

    /**
     * 取浮点数参数。
     *
     * @param name     参数名
     * @param alias    备用名
     * @param fallback 参数不存在时的默认值
     * @return 浮点数
     * @throws CommandSyntaxException 类型不符时抛出
     */
    public double getDouble(String name, String alias, double fallback) throws CommandSyntaxException {
        Double value = getOptionalArgument(name, alias, Double.class);
        return value == null ? fallback : value;
    }

    /**
     * 取布尔参数。
     *
     * @param name     参数名
     * @param alias    备用名
     * @param fallback 参数不存在时的默认值
     * @return 布尔值
     * @throws CommandSyntaxException 类型不符时抛出
     */
    public boolean getBoolean(String name, String alias, boolean fallback) throws CommandSyntaxException {
        Boolean value = getOptionalArgument(name, alias, Boolean.class);
        return value == null ? fallback : value;
    }

    /**
     * 取实体选择器参数，并在当前上下文里求解成实体列表。
     *
     * @param name 参数名
     * @return 选中的实体列表（至少一个）
     * @throws CommandSyntaxException 参数缺失、类型不符或没有选中实体时抛出
     */
    public List<Entity> getEntities(String name) throws CommandSyntaxException {
        return resolveSelector(name, false);
    }

    /**
     * 取实体选择器参数，并求解成<b>恰好一个</b>实体。
     *
     * @param name 参数名
     * @return 选中的实体
     * @throws CommandSyntaxException 参数缺失、类型不符、选中数量不为 1 时抛出
     */
    public Entity getEntity(String name) throws CommandSyntaxException {
        return resolveSelector(name, true).get(0);
    }

    /**
     * 取实体选择器参数，求解成实体列表，并过滤出其中的生物。
     *
     * @param name 参数名
     * @return 选中的生物列表（至少一个）
     * @throws CommandSyntaxException 参数缺失、类型不符、没有选中生物时抛出
     */
    public List<LivingThing> getLivingThings(String name) throws CommandSyntaxException {
        List<Entity> entities = resolveSelector(name, false);
        List<LivingThing> living = new ArrayList<>();
        for (Entity entity : entities) {
            if (entity instanceof LivingThing livingThing) {
                living.add(livingThing);
            }
        }
        if (living.isEmpty()) {
            throw CommandSyntaxException.create("选择器「" + name + "」选中的都不是生物（LivingThing）");
        }
        return living;
    }

    /**
     * 求解实体选择器参数。
     *
     * @param name   参数名
     * @param single 是否要求恰好一个
     * @return 实体列表
     * @throws CommandSyntaxException 求解失败时抛出
     */
    private List<Entity> resolveSelector(String name, boolean single) throws CommandSyntaxException {
        EntitySelector selector = getArgument(name, EntitySelector.class);
        selector.resolve(getSelectorContext());
        List<Entity> targets = selector.getTargets();
        if (single && targets.size() != 1) {
            throw CommandSyntaxException.create("参数「" + name + "」要求恰好 1 个实体，但选中了 "
                    + targets.size() + " 个：" + EntityArgumentType.describe(targets));
        }
        return targets;
    }

    /* ------------------------------------------------------------------
     * 执行环境
     * ------------------------------------------------------------------ */

    /**
     * @return 已解析的所有参数（只读遍历用）
     */
    public Map<String, Object> getArguments() {
        return arguments;
    }

    /**
     * 取命令来源；如果构造时没给（{@code null}），这里惰性建一个控制台来源。
     * <p>
     * 惰性化是必须的：{@link CommandSource} 的构造器会先调用 {@code super(null)}，
     * 若构造器里就创建控制台来源，就会自己 new 自己，直接 {@link StackOverflowError}。
     *
     * @return 命令来源（不会为 {@code null}）
     */
    public CommandSender getSender() {
        if (sender == null) {
            sender = new CommandSource(null, "控制台");
        }
        return sender;
    }

    /**
     * 替换命令来源。仅由 {@link CommandSource} 在构造时调用，用于把「来源」与「上下文」合并成同一个对象。
     *
     * @param sender 新的命令来源
     */
    protected void setSender(CommandSender sender) {
        if (sender != null) {
            this.sender = sender;
        }
    }

    /**
     * @return 命令来源；若来源不是 {@link CommandSource} 则包装一个（不含战斗信息）
     */
    public CommandSource getSource() {
        if (sender instanceof CommandSource commandSource) {
            return commandSource;
        }
        LivingThing player = sender.getPlayer();
        String name = sender.getName();
        return new CommandSource(player, name);
    }

    /**
     * @return 当前战斗；没有则返回 {@code null}
     */
    public Fight getFight() {
        return getSource().getFight();
    }

    /**
     * @return 执行命令的玩家生物；没有则返回 {@code null}
     */
    public LivingThing getPlayer() {
        return getSource().getPlayer();
    }

    /**
     * 构造实体选择器的求解上下文（当前战斗 + 世界里全部运行时对象）。
     *
     * @return 求解上下文
     */
    public EntitySelector.CommandSelectorContext getSelectorContext() {
        return getSource().getSelectorContext();
    }

    /**
     * @return 已解析参数的可读文本（调试用）
     */
    public String describeArguments() {
        if (arguments.isEmpty()) {
            return "（无参数）";
        }
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append('=').append(describeValue(entry.getValue()));
            first = false;
        }
        return builder.toString();
    }

    @Override
    public String toString() {
        return "CommandContext{" + describeArguments() + "}";
    }
}
