package cn.gfhnv.game.officialStuff.customCommands;

import cn.gfhnv.game.entity.Entity;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.system.command.Command;
import cn.gfhnv.game.system.command.CommandNode;
import cn.gfhnv.game.system.command.EntityArgumentType;
import cn.gfhnv.game.system.command.EntitySelector;
import cn.gfhnv.game.system.command.LiteralCommandNode;
import cn.gfhnv.game.world.World;

import java.util.List;

/**
 * {@code /list} —— 列出当前战斗里的生物状态。
 * <p>
 * 用法：
 * <pre>
 * /list                          列出当前战斗里的所有生物
 * /list @e[type=CommonInsect]    只列出指定选择器选中的生物
 * </pre>
 * <p>
 * 本命令同时演示了「同一个命令的两条分支」（{@code list} 与 {@code list <目标>}）：
 * 基类的 {@link Command#node()} 建出根节点后，分别挂两个子节点即可。
 * 没有战斗时会退化为列出世界里已有的运行时生物。
 *
 * @author AI（DeepSeek）生成
 */
public class ListCommand extends Command {

    /**
     * 构造 {@code /list} 命令。
     */
    public ListCommand() {
        super("list");
    }

    /**
     * 构建命令树：{@code list} 与 {@code list <目标>}。
     *
     * @return 命令根节点
     */
    @Override
    protected CommandNode buildNode() {
        LiteralCommandNode root = node();

        // 分支一：list（列出全部）
        root.setExecutor((context, source) -> {
            List<LivingThing> all = context.getSelectorContext().getFightEntities();
            source.sendMessage(describe(all, context.getFight() == null ? "世界里的生物" : "当前战斗中的生物"));
            return all.size();
        });

        // 分支二：list <目标>
        root.addChild(argument("目标", EntityArgumentType.entities())
                .executes((context, source) -> {
                    List<Entity> entities = context.getEntities("目标");
                    EntitySelector selector = context.getArgument("目标", EntitySelector.class);
                    source.sendMessage("选择器 " + selector.getRawText() + " 选中了 " + entities.size() + " 个实体：");
                    for (Entity entity : entities) {
                        source.sendMessage("  " + describeOne(entity));
                    }
                    return entities.size();
                })
                .build());

        return root;
    }

    /**
     * 把生物列表拼成多行文本。
     *
     * @param livingThings 生物列表
     * @param title        标题
     * @return 文本
     */
    private static String describe(List<LivingThing> livingThings, String title) {
        StringBuilder builder = new StringBuilder(title).append("共 ").append(livingThings.size()).append(" 个：");
        for (LivingThing livingThing : livingThings) {
            builder.append(System.lineSeparator()).append("  ").append(describeOne(livingThing));
        }
        return builder.toString();
    }

    /**
     * 描述单个实体。
     *
     * @param entity 实体
     * @return 文本
     */
    private static String describeOne(Entity entity) {
        String name = EntityArgumentType.nameOf(entity);
        String type = entity.getClass().getSimpleName();
        if (entity instanceof LivingThing livingThing) {
            return name + " [" + type + "] HP " + livingThing.getHp() + "/" + livingThing.getHpMax()
                    + " 存活=" + livingThing.isAlive()
                    + " 攻击=" + livingThing.getAttack()
                    + " 防御=" + livingThing.getDefence()
                    + " 速度=" + livingThing.getSpeed();
        }
        return name + " [" + type + "]";
    }

    /**
     * 便于外部（例如模组）拿到「世界里的生物」，与游戏内命令用的是同一套判断。
     *
     * @return 世界运行时对象里的全部生物
     */
    public static List<LivingThing> worldLivingThings() {
        List<LivingThing> result = new java.util.ArrayList<>();
        for (cn.gfhnv.game.Thing thing : World.getThings()) {
            if (thing instanceof LivingThing livingThing) {
                result.add(livingThing);
            }
        }
        return result;
    }
}
