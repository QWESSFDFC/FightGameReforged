package cn.gfhnv.game.officialStuff.customCommands;

import cn.gfhnv.game.damage.DamageSource;
import cn.gfhnv.game.damage.DamageType;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.DamageEvent;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.command.*;

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
 * <b>实现说明</b>：不再直接 {@code setHp(0)}，而是构造一个<b>无限大的系统类型伤害</b>
 * （{@link DamageType#SystemDamage} + {@link ElementSort#UNIVERSAL}）并照常广播
 * {@link DamageEvent}、调用 {@link LivingThing#takeDamage(DamageEvent)}。
 * 这样外部监听器仍能看到伤害事件，免死 / 转阶段 / 结界退出等 {@code IModifyDamage}
 * 也能正常生效 —— 例如千冶·刃开大时第一次 {@code /kill} 只会退出大招，不会直接死亡。
 * <p>
 * 系统伤害仍走现有伤害计算器（不改公式）；原始值取一个足够大的固定值，
 * 目标有 100% 减伤或免死时按各自机制处理。
 *
 * @author AI（DeepSeek）生成
 */
public class KillCommand extends Command {

    /**
     * `/kill` 的固定原始值：远大于正常生命上限，又不会让 long 运算溢出。
     */
    private static final double KILL_DAMAGE = 1.0e18;

    /**
     * 构造 {@code /kill} 命令。
     */
    public KillCommand() {
        super("kill");
    }

    /**
     * 构建命令树：{@code kill <目标>}。
     * <p>
     * 命令树用「显式分步」的方式搭：先建出这一层的节点，再往它上面挂下一层，
     * 最后把这一层交给父节点 —— 每一层的父子关系都直白可见。
     *
     * @return 命令根节点
     */
    @Override
    protected CommandNode buildNode() {
        LiteralCommandNode root = node();

        ArgumentBuilder target = ArgumentBuilder.argumentBuilder("目标", EntityArgumentType.entities());
        target.executes((context, source) -> {
            List<LivingThing> living = context.getLivingThings("目标");
            int count = 0;
            StringBuilder killed = new StringBuilder();
            for (LivingThing one : living) {
                long before = one.getHp();
                // 系统伤害也走 DamageEvent：外部可监听、免死/转阶段/结界退出能生效。
                // 攻击者取目标自己 —— 这不是“自己打自己”的普通攻击，只是给伤害一个非空来源实体；
                // 千冶·刃的受击监听会因此跳过【千锻魂】的攻击者缠身（attacker == owner），
                // 但星魂 6 的“受伤 +1 充能”仍会在 attacker 判空之前触发。
                DamageEvent damageEvent = new DamageEvent(one, one,
                        DamageSource.named("kill"), ElementSort.UNIVERSAL,
                        KILL_DAMAGE, DamageType.SystemDamage);
                EventBus.post(damageEvent);
                one.takeDamage(damageEvent);
                long after = one.getHp();
                if (killed.length() > 0) {
                    killed.append("、");
                }
                killed.append(EntityArgumentType.nameOf(one))
                        .append("（").append(before).append(" → ").append(after).append("）");
                if (one.isAlive()) {
                    killed.append("【被免死/免伤拦下】");
                }
                count++;
            }
            source.sendMessage("已对 " + count + " 个目标造成击杀伤害：" + killed);
            return count;
        });
        root.addChild(target);

        return root;
    }
}
