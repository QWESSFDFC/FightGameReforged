package cn.gfhnv.game.officialStuff.customCommands;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.system.command.*;

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
     * 「目标」是外层、「数值」是内层，因此先建「目标」，把「数值」挂到它上面（并给「数值」
     * 绑执行体），最后把「目标」交给根节点。
     *
     * @return 命令根节点
     */
    @Override
    protected CommandNode buildNode() {
        LiteralCommandNode root = node();

        ArgumentBuilder target = ArgumentBuilder.argumentBuilder("目标", EntityArgumentType.entities());
        ArgumentBuilder amount = target.argument("数值", LongArgumentType.longArg());
        amount.executes((context, source) -> {
            List<LivingThing> living = context.getLivingThings("目标");
            long value = context.getLong("数值", null, 0L);
            int affected = 0;
            StringBuilder detail = new StringBuilder();
            for (LivingThing one : living) {
                long before = one.getHp();
                one.setHp(before - value);
                long actual = one.getHp();
                if (detail.length() > 0) {
                    detail.append("、");
                }
                detail.append(EntityArgumentType.nameOf(one))
                        .append("（").append(before).append(" → ").append(actual).append("）");
                if (actual != before) {
                    affected++;
                }
            }
            String verb = value >= 0 ? "造成 " + value + " 点伤害" : "回复 " + (-value) + " 点生命";
            source.sendMessage("对 " + living.size() + " 个目标" + verb + "，实际变化 " + affected + " 个：" + detail);
            return affected;
        });
        root.addChild(target);

        return root;
    }
}
