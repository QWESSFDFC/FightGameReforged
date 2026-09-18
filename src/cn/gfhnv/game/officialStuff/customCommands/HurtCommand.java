package cn.gfhnv.game.officialStuff.customCommands;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.system.command.ArgumentBuilder;
import cn.gfhnv.game.system.command.Command;
import cn.gfhnv.game.system.command.CommandNode;
import cn.gfhnv.game.system.command.EntityArgumentType;
import cn.gfhnv.game.system.command.LiteralCommandNode;
import cn.gfhnv.game.system.command.LongArgumentType;

import java.util.List;

/**
 * {@code /hurt} —— 对生物造成（或回复）生命值变化。
 * <p>
 * 用法：
 * <pre>
 * /hurt @s 100                        自己掉 100 血
 * /hurt @e[type=CommonInsect] 9999    给所有普通虫造成 9999 点伤害
 * /hurt @s -50                        负数表示回血 50 点
 * </pre>
 * <p>
 * <b>实现说明</b>：直接读改写 {@link LivingThing#getHp()} / {@link LivingThing#setHp(long)}。
 * {@code setHp} 内部会发布 {@link cn.gfhnv.game.event.HpLossEvent} 或
 * {@link cn.gfhnv.game.event.HpRestorationEvent}，并把结果夹在 {@code [0, hpMax]} 之间。
 * <p>
 * 注意：本命令<b>不</b>走 {@code DamageCalculate} 的伤害公式（不带攻击力/防御力/暴击/抗性），
 * 而是直接改生命值——这是「调试/演出」用的命令，不是一次真正的攻击。
 *
 * @author AI（DeepSeek）生成
 */
public class HurtCommand extends Command {

    /**
     * 构造 {@code /hurt} 命令。
     */
    public HurtCommand() {
        super("hurt");
    }

    /**
     * 构建命令树：{@code hurt <目标> <数值>}。
     * <p>
     * <b>关键</b>：{@code build()} 只构建「调用它的那个构建器」以及它下面的子分支，
     * <b>不会</b>把自己挂到父构建器上（父子关系是在父构建器 build 时才建立的）。
     * 所以要 {@code root.addChild(...)} 的那个东西，必须是<b>要被挂上去的那一层</b>的构建器。
     * <p>
     * 因此这里分两步：先在「目标」构建器上搭出「数值」，再 build「目标」这一层。
     * 若写成 {@code root.addChild(argument("目标", ...).argument("数值", ...).build())}，
     * 传进去的其实是「数值」节点，「目标」整层会丢失。
     *
     * @return 命令根节点
     */
    @Override
    protected CommandNode buildNode() {
        LiteralCommandNode root = node();
        ArgumentBuilder targetBuilder = argument("目标", EntityArgumentType.entities());
        targetBuilder.argument("数值", LongArgumentType.longArg())
                .executes((context, source) -> {
                    List<LivingThing> targets = context.getLivingThings("目标");
                    long amount = context.getLong("数值", null, 0L);
                    int affected = 0;
                    StringBuilder detail = new StringBuilder();
                    for (LivingThing target : targets) {
                        long before = target.getHp();
                        long after = before - amount;
                        target.setHp(after);
                        long actual = target.getHp();
                        if (detail.length() > 0) {
                            detail.append("、");
                        }
                        detail.append(EntityArgumentType.nameOf(target))
                                .append("（").append(before).append(" → ").append(actual).append("）");
                        if (actual != before) {
                            affected++;
                        }
                    }
                    String verb = amount >= 0 ? "造成 " + amount + " 点伤害" : "回复 " + (-amount) + " 点生命";
                    source.sendMessage("对 " + targets.size() + " 个目标" + verb + "，实际变化 " + affected + " 个：" + detail);
                    return affected;
                });
        // 注意：build 的是「目标」那一层，它会把「数值」一起带上
        root.addChild(targetBuilder.build());
        return root;
    }
}
