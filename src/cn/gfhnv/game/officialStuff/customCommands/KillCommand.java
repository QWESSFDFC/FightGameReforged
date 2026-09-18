package cn.gfhnv.game.officialStuff.customCommands;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.system.command.Command;
import cn.gfhnv.game.system.command.CommandNode;
import cn.gfhnv.game.system.command.EntityArgumentType;
import cn.gfhnv.game.system.command.LiteralCommandNode;

import java.util.List;

/**
 * {@code /kill} —— 击杀生物。
 * <p>
 * 用法（与 MC 的 {@code /kill} 类似）：
 * <pre>
 * /kill @s                             击杀自己
 * /kill @e[type=CommonInsect]          击杀所有普通虫
 * /kill @p                             击杀离自己最近的一个
 * /kill @a[limit=2,sort=nearest]       击杀最近的两个
 * </pre>
 * <p>
 * <b>实现说明</b>：直接 {@code setHp(0)}。{@link LivingThing#setHp(long)} 会发布
 * {@link cn.gfhnv.game.event.HpLossEvent}（模组可以监听该事件做联动），
 * {@link LivingThing#isAlive()} 会在下一次读取时把该生物标记为死亡并清掉它的行动信号，
 * 战斗循环随后会把它从时间轴里剔除，因此不会破坏回合推进。
 *
 * @author AI（DeepSeek）生成
 */
public class KillCommand extends Command {

    /**
     * 构造 {@code /kill} 命令。
     */
    public KillCommand() {
        super("kill");
    }

    /**
     * 构建命令树：{@code kill <目标>}。
     * <p>
     * 注意顺序：<b>子分支先 build，最后再把它挂到根节点上</b>。
     *
     * @return 命令根节点
     */
    @Override
    protected CommandNode buildNode() {
        LiteralCommandNode root = node();
        root.addChild(argument("目标", EntityArgumentType.entities())
                .executes((context, source) -> {
                    List<LivingThing> targets = context.getLivingThings("目标");
                    int count = 0;
                    StringBuilder killed = new StringBuilder();
                    for (LivingThing target : targets) {
                        long before = target.getHp();
                        target.setHp(0);
                        target.setAlive(false);
                        if (killed.length() > 0) {
                            killed.append("、");
                        }
                        killed.append(EntityArgumentType.nameOf(target))
                                .append("（").append(before).append(" → 0）");
                        count++;
                    }
                    source.sendMessage("已击杀 " + count + " 个目标：" + killed);
                    return count;
                })
                .build());
        return root;
    }
}
