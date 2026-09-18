package cn.gfhnv.game.system.command;

import cn.gfhnv.game.entity.Entity;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.skill.Skill;

import java.util.List;
import java.util.Optional;

/**
 * 命令参数类型：实体选择器（{@code @s}、{@code @a}、{@code @e[type=...]} …）。
 * <p>
 * 两种用法（对应 MC 的 {@code entity()} 与 {@code entities()}）：
 * <ul>
 *     <li>{@link #entities()}：解析成 {@link EntitySelector}，可以选中多个；</li>
 *     <li>{@link #entity()}：解析成单个 {@link Entity}，选中 0 个或 2 个以上都会报错。</li>
 * </ul>
 * <p>
 * 注意：选择器是在<b>执行时</b>（命令的 {@code executes(...)} 回调里）用 {@link CommandContext}
 * 提供的上下文求解的；解析阶段只校验语法。这样命令类拿到的永远是最新的实体列表。
 * <p>
 * 命令类中的参数可以写成三种形式，{@link CommandRegistration} 都支持：
 * <pre>{@code
 * .executes((context, source) -> {
 *     EntitySelector selector = context.getArgument("目标", EntitySelector.class);
 *     Entity single = context.getEntity("目标");                 // 自动要求恰好一个
 *     List<LivingThing> living = context.getLivingThings("目标"); // 自动过滤成生物
 * })
 * }</pre>
 *
 * @author AI（DeepSeek）生成
 */
public class EntityArgumentType implements ArgumentType<EntitySelector> {

    /**
     * 是否只允许选出一个实体。
     */
    private final boolean single;

    /**
     * 构造一个实体参数类型。
     *
     * @param single 是否只允许选出一个实体
     */
    public EntityArgumentType(boolean single) {
        this.single = single;
    }

    /**
     * @return 解析成单个实体的参数类型
     */
    public static EntityArgumentType entity() {
        return new EntityArgumentType(true);
    }

    /**
     * @return 解析成实体选择器的参数类型（可选多个）
     */
    public static EntityArgumentType entities() {
        return new EntityArgumentType(false);
    }

    /**
     * @return 是否只允许选出一个实体
     */
    public boolean isSingle() {
        return single;
    }

    @Override
    public EntitySelector parse(StringReader reader) throws CommandSyntaxException {
        String text = reader.readWord();
        // 只校验语法，具体实体在执行阶段求解
        return EntitySelector.fromString(text);
    }

    /**
     * 在给定上下文里把选择器求解成实体列表，并做「单个/多个」校验。
     *
     * @param selector 选择器
     * @param context  命令上下文
     * @return 选中的实体列表（至少一个）
     * @throws CommandSyntaxException 没有选中实体、或单个模式选中多个时抛出
     */
    public List<Entity> resolve(EntitySelector selector, CommandContext context) throws CommandSyntaxException {
        if (selector == null) {
            throw CommandSyntaxException.create("没有解析到实体选择器");
        }
        selector.resolve(context.getSelectorContext());
        if (single && selector.getTargets().size() != 1) {
            throw CommandSyntaxException.create("该参数要求恰好选中 1 个实体，但选中了 "
                    + selector.getTargets().size() + " 个：" + describe(selector.getTargets()));
        }
        return selector.getTargets();
    }

    /**
     * 把实体列表拼成可读文本（用于报错与回显）。
     *
     * @param entities 实体列表
     * @return 可读文本
     */
    public static String describe(List<? extends Entity> entities) {
        if (entities == null || entities.isEmpty()) {
            return "（空）";
        }
        StringBuilder builder = new StringBuilder();
        int index = 0;
        for (Entity entity : entities) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(nameOf(entity));
            index++;
        }
        return builder.toString();
    }

    /**
     * 取实体的显示名称。
     *
     * @param entity 实体
     * @return 名称；没有名字时退化为类名
     */
    public static String nameOf(Entity entity) {
        if (entity == null) {
            return "null";
        }
        if (entity.getName() != null && !entity.getName().isBlank()) {
            return entity.getName();
        }
        return entity.getClass().getSimpleName();
    }

    /**
     * 取生物的简短状态文本（名字 + 当前/上限生命）。
     *
     * @param livingThing 生物
     * @return 状态文本
     */
    public static String statusOf(LivingThing livingThing) {
        if (livingThing == null) {
            return "null";
        }
        return nameOf(livingThing) + " HP " + livingThing.getHp() + "/" + livingThing.getHpMax();
    }

    /**
     * 取生物当前可用的技能名列表（用于调试命令展示）。
     *
     * @param livingThing 生物
     * @return 技能名列表文本
     */
    public static String describeSkills(LivingThing livingThing) {
        if (livingThing == null || livingThing.getController() == null) {
            return "（无控制器）";
        }
        StringBuilder builder = new StringBuilder();
        int index = 0;
        for (Skill skill : livingThing.getController().getSkills()) {
            if (index > 0) {
                builder.append("、");
            }
            builder.append(skill.getName());
            index++;
        }
        return builder.length() == 0 ? "（无技能）" : builder.toString();
    }

    /**
     * 便于命令实现的可选取值：把选择器结果包成 {@link Optional}。
     *
     * @param selector 选择器
     * @param context  命令上下文
     * @return 第一个实体；没有则返回 {@link Optional#empty()}
     */
    public static Optional<Entity> first(EntitySelector selector, CommandContext context) {
        if (selector == null) {
            return Optional.empty();
        }
        List<Entity> targets = selector.getTargets();
        if (targets.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(targets.get(0));
    }

    @Override
    public String toString() {
        return single ? "entity" : "entities";
    }
}
