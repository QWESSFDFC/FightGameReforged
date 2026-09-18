package cn.gfhnv.game.system.command;

import cn.gfhnv.game.Thing;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * 命令来源与执行上下文：把「谁在执行」和「解析出来的参数」合到一起。
 * <p>
 * 之所以让本类继承 {@link CommandContext}，是为了让命令实现只接一个参数就能拿到全部信息：
 * <pre>{@code
 * .executes((context, source) -> {
 *     Fight fight = source.getFight();            // 当前战斗
 *     LivingThing me = source.getPlayer();        // 执行命令的角色
 *     source.sendMessage("战斗回合数：" + World.turnTimer);
 *     return 1;
 * })
 * }</pre>
 * <p>
 * 另外还承担「当前战斗」的登记职责：{@link #setCurrentFight(Fight)} 由战斗开始时调用，
 * 这样命令（以及实体选择器 {@code @a} / {@code @e}）才能知道该在哪个范围里找目标。
 *
 * @author AI（DeepSeek）生成
 */
public class CommandSource extends CommandContext implements CommandSender {

    /**
     * 当前正在进行的战斗；没有战斗（还在选人界面）时为 {@code null}。
     */
    private static Fight currentFight = null;

    /**
     * 执行命令的玩家生物；未选角色时为 {@code null}。
     */
    private final LivingThing player;

    /**
     * 来源名称。
     */
    private final String name;

    /**
     * 构造一个命令来源。
     * <p>
     * 这里先 {@code super(null)} 再 {@link #setSender} 把自己写回去，是刻意的：
     * 目的是让「来源」与「上下文」成为同一个对象，命令实现里不必再用
     * {@code context.getSource()} 绕一圈。
     * <p>
     * <b>注意</b>：{@link CommandContext} 的构造器绝不能创建控制台来源，
     * 否则「new CommandSource → super(null) → new CommandSource」会无限递归。
     * {@link CommandContext} 里因此改为惰性创建，见其 {@link CommandContext#getSender()}。
     *
     * @param player 玩家生物，可为 {@code null}
     * @param name   来源名称，可为 {@code null}（自动取名）
     */
    public CommandSource(LivingThing player, String name) {
        super(null);
        this.player = player;
        this.name = name == null || name.isBlank()
                ? (player == null ? "控制台" : player.getName())
                : name;
        // 用自身替换掉「空来源」，保证 context.getSender() / getSource() 指回本对象
        setSender(this);
    }

    /**
     * 构造一个控制台来源（没有玩家）。
     */
    public CommandSource() {
        this(null, "控制台");
    }

    /**
     * 构造一个玩家来源。
     *
     * @param player 玩家生物，可为 {@code null}
     */
    public CommandSource(LivingThing player) {
        this(player, null);
    }

    /**
     * 便捷方法：构造一个控制台来源。
     *
     * @return 控制台来源
     */
    public static CommandSource console() {
        return new CommandSource();
    }

    /**
     * @return 当前战斗；没有则返回 {@code null}
     */
    public static Fight getCurrentFight() {
        return currentFight;
    }

    /**
     * 登记当前战斗。战斗开始时调用，战斗结束时传 {@code null} 清除。
     *
     * @param fight 当前战斗，可为 {@code null}
     */
    public static void setCurrentFight(Fight fight) {
        currentFight = fight;
    }

    @Override
    public LivingThing getPlayer() {
        return player;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Fight getFight() {
        return currentFight;
    }

    @Override
    public CommandSource getSource() {
        return this;
    }

    @Override
    public void sendMessage(String message) {
        if (message == null) {
            return;
        }
        System.out.println(message);
        log(message);
    }

    @Override
    public EntitySelector.CommandSelectorContext getSelectorContext() {
        List<Thing> things = new ArrayList<>(World.getThings());
        return new EntitySelector.CommandSelectorContext(player, currentFight, things);
    }

    @Override
    public String toString() {
        return "CommandSource{" + name + ", hasPlayer=" + (player != null)
                + ", inFight=" + (currentFight != null) + "}";
    }
}
