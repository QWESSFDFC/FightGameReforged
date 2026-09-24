package cn.gfhnv.game.system.fight;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.interfaces.ITaunt;
import cn.gfhnv.game.interfaces.TargetStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 内置的目标选择策略，以及它们之间的组合方式。
 * <p>
 * 每个策略只做「排序」，不关心技能要几个目标（那是控制器的活），所以可以随意套娃：
 * <pre>{@code
 * controller.setTargetStrategy(TargetStrategies.tauntAware(TargetStrategies.lowestHp()));
 * // → 先打带嘲讽的（嘲讽等级高的更前），没有嘲讽时打血量比例最低的
 * }</pre>
 * <p>
 * 嘲讽本身只是一个<b>标记</b>（效果实现 {@link ITaunt}），本类只认接口不认具体效果，
 * 因此模组自定义的嘲讽一样生效；需要「硬嘲讽」（强制只能打它）时，
 * 可以自己实现一个 {@link TargetStrategy} 直接返回嘲讽目标。
 *
 * @author AI（DeepSeek）生成
 */
public final class TargetStrategies {

    /**
     * 工具类不允许实例化。
     */
    private TargetStrategies() {
    }

    /**
     * 随机顺序（控制器的默认策略，与旧行为一致）。
     *
     * @return 策略
     */
    public static TargetStrategy random() {
        return (fight, user, skill, candidates) -> {
            List<LivingThing> ordered = new ArrayList<>(candidates);
            Collections.shuffle(ordered);
            return ordered;
        };
    }

    /**
     * 保持候选本来的顺序（一般是阵容里的先后）。
     *
     * @return 策略
     */
    public static TargetStrategy first() {
        return (fight, user, skill, candidates) -> new ArrayList<>(candidates);
    }

    /**
     * 血量比例（当前/上限）最低的排最前。
     *
     * @return 策略
     */
    public static TargetStrategy lowestHp() {
        return (fight, user, skill, candidates) -> {
            List<LivingThing> ordered = new ArrayList<>(candidates);
            ordered.sort(Comparator.comparingDouble(TargetStrategies::hpRatio));
            return ordered;
        };
    }

    /**
     * 血量比例（当前/上限）最高的排最前。
     *
     * @return 策略
     */
    public static TargetStrategy highestHp() {
        return (fight, user, skill, candidates) -> {
            List<LivingThing> ordered = new ArrayList<>(candidates);
            ordered.sort(Comparator.comparingDouble(TargetStrategies::hpRatio).reversed());
            return ordered;
        };
    }

    /**
     * 装饰器：带嘲讽的目标排到最前面；同优先级时保持 {@code fallback} 给出的相对顺序。
     * <p>
     * 注意这是<b>软</b>嘲讽（排前面），不是「只能打它」：范围技能照样会打到别人。
     *
     * @param fallback 没有嘲讽目标时使用的策略；为 {@code null} 时等价于 {@link #first()}
     * @return 策略
     */
    public static TargetStrategy tauntAware(TargetStrategy fallback) {
        TargetStrategy base = fallback == null ? first() : fallback;
        return (fight, user, skill, candidates) -> {
            List<LivingThing> ordered = base.order(fight, user, skill, candidates);
            List<LivingThing> taunted = new ArrayList<>();
            List<LivingThing> rest = new ArrayList<>();
            for (LivingThing one : ordered) {
                if (tauntLevelOf(one) > 0) {
                    taunted.add(one);
                } else {
                    rest.add(one);
                }
            }
            if (taunted.isEmpty()) {
                return ordered;
            }
            // List.sort 是稳定排序：同级嘲讽保持 fallback 的相对顺序
            taunted.sort(Comparator.comparingInt(TargetStrategies::tauntLevelOf).reversed());
            taunted.addAll(rest);
            return taunted;
        };
    }

    /**
     * 查一个生物身上的嘲讽等级（取最高的那个 {@link ITaunt} 效果）。
     * <p>
     * 嘲讽是「效果标记」而不是生物属性，所以任何实现 {@link ITaunt} 的效果都算数，
     * 也不需要生物类配合。
     *
     * @param thing 生物；可为 {@code null}
     * @return 嘲讽等级；没有嘲讽返回 0
     */
    public static int tauntLevelOf(LivingThing thing) {
        if (thing == null) {
            return 0;
        }
        int level = 0;
        for (Effect effect : thing.getEntityEffectList()) {
            if (effect instanceof ITaunt taunt) {
                level = Math.max(level, taunt.getTauntLevel());
            }
        }
        return level;
    }

    /**
     * 血量比例。
     *
     * @param thing 生物
     * @return 当前生命 / 生命上限；上限为 0 时返回 0
     */
    private static double hpRatio(LivingThing thing) {
        long max = thing.getHpMax();
        return max <= 0 ? 0 : (double) thing.getHp() / max;
    }
}
