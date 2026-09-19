package cn.gfhnv.game.officialStuff.customCommands;

import cn.gfhnv.game.GameMain;
import cn.gfhnv.game.system.command.ArgumentBuilder;
import cn.gfhnv.game.system.command.Command;
import cn.gfhnv.game.system.command.CommandManager;
import cn.gfhnv.game.system.command.CommandNode;
import cn.gfhnv.game.system.command.CommandSource;
import cn.gfhnv.game.system.command.CommandSyntaxException;
import cn.gfhnv.game.system.command.LiteralCommandNode;
import cn.gfhnv.game.system.command.StringArgumentType;
import cn.gfhnv.game.system.fight.Fight;

/**
 * {@code /endfight} —— 立刻结束当前战斗，回到选人/选敌人的流程。
 * <p>
 * 用法：
 * <pre>
 * /endfight           按「玩家胜利」结束（会正常发奖励）
 * /endfight lose      按「玩家失败」结束（不发奖励）
 * </pre>
 * <p>
 * <b>实现说明</b>：本命令<b>不会</b>在战斗循环里递归地推进回合，它只做两件事：
 * <ol>
 *     <li>清空 {@link cn.gfhnv.game.system.fight.TurnManager} 的时间轴；</li>
 *     <li>调用 {@link GameMain#endFight(Fight, boolean)}，由它置位「战斗已结束」并发布
 *     {@link cn.gfhnv.game.event.FightEndEvent}，收尾逻辑交给
 *     {@code FightEndEventListener}（调用 {@code whenFightEnds()}、发奖励、注销监听器、
 *     复位 {@code FightTurnPastListener.isDriving}）。</li>
 * </ol>
 * 正在推进的回合循环会在下一圈看到状态变化并直接 {@code break}，
 * 控制权回到 {@link GameMain#main(String[])} 的主循环，因此既不会递归、
 * 也不会让下一场战斗的回合推不动。
 *
 * @author AI（DeepSeek）生成
 */
public class EndFightCommand extends Command {

    /**
     * 构造 {@code /endfight} 命令。
     */
    public EndFightCommand() {
        super("endfight");
    }

    /**
     * 构建命令树：{@code endfight} 与 {@code endfight <结果>}。
     *
     * @return 命令根节点
     */
    @Override
    protected CommandNode buildNode() {
        LiteralCommandNode root = node();

        // 分支一：endfight —— 默认按玩家胜利结算
        root.setExecutor((context, source) -> finishFight(true, source));

        // 分支二：endfight <结果> —— 可以指定按胜利还是失败结算
        ArgumentBuilder result = ArgumentBuilder.argumentBuilder("结果", StringArgumentType.word());
        result.executes((context, source) -> {
            String text = context.getString("结果", null);
            if (text == null) {
                return finishFight(true, source);
            }
            boolean win = switch (text.toLowerCase()) {
                case "win", "胜利", "yes", "true" -> true;
                case "lose", "failure", "失败", "no", "false" -> false;
                default -> throw CommandSyntaxException.create(
                        "「" + text + "」不是合法的结果，只能填 win 或 lose（也接受 胜利/失败）");
            };
            return finishFight(win, source);
        });
        root.addChild(result);

        return root;
    }

    /**
     * 结束当前战斗。
     *
     * @param playerWin 是否按玩家胜利处理
     * @param source    命令来源
     * @return 影响到的对象数量（成功为 1）
     */
    private static int finishFight(boolean playerWin, CommandSource source) {
        if (!GameMain.isInFight()) {
            source.sendMessage("当前没有正在进行的战斗。");
            return 0;
        }
        Fight fight = source.getFight();
        if (fight == null) {
            source.sendMessage("命令系统里没有登记当前战斗，无法结束。");
            return 0;
        }
        CommandManager.clearCurrentFight();
        GameMain.endFight(fight, playerWin);
        source.sendMessage("本场战斗已被命令强制结束（按玩家" + (playerWin ? "胜利" : "失败") + "处理）。");
        return 1;
    }
}
