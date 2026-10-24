package cn.gfhnv.game.system.fight;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.interfaces.TargetStrategy;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.Taunt;

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
 * // → 先打嘲讽我的那个（嘲讽等级高的更前），没有嘲讽时打血量比例最低的
 * }</pre>
 * <p>
 * <b>2026-10-04 重做（用户要求）</b>：
 * <ul>
 *     <li><b>所有内置策略默认自带嘲讽感知</b> —— {@link #random()}、{@link #first()} 这些
 *     返回的<b>已经是 {@link #tauntAware} 包过的</b>。以前嘲讽只在调用方自己包了
 *     {@code tauntAware(...)} 时才生效，而没人包，所以嘲讽是个死功能。</li>
 *     <li><b>方向也改了</b>：A 给 B 施加嘲讽 → <b>B 打人时优先打 A</b>。
 *     判断依据是<b>攻击者身上</b>那条 {@link Taunt} 的
 *     {@link Effect#getOrigin() origin}（= 施加者 uuid），
 *     而<b>不是</b>"目标身上有没有嘲讽"（旧实现把方向搞反了，等于没生效）。</li>
 * </ul>
 * 需要「硬嘲讽」（强制只能打它）时，自己实现一个 {@link TargetStrategy} 直接返回嘲讽目标。
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
     * 随机顺序（控制器的默认策略）。<b>已自带嘲讽感知</b>。
     *
     * @return 策略
     */
    public static TargetStrategy random() {
        return tauntAware(randomRaw());
    }

    /**
     * 保持候选本来的顺序（一般是阵容里的先后）。<b>已自带嘲讽感知</b>。
     *
     * @return 策略
     */
    public static TargetStrategy first() {
        return tauntAware(firstRaw());
    }

    /**
     * 血量比例（当前/上限）最低的排最前。<b>已自带嘲讽感知</b>。
     *
     * @return 策略
     */
    public static TargetStrategy lowestHp() {
        return tauntAware(lowestHpRaw());
    }

    /**
     * 血量比例（当前/上限）最高的排最前。<b>已自带嘲讽感知</b>。
     *
     * @return 策略
     */
    public static TargetStrategy highestHp() {
        return tauntAware(highestHpRaw());
    }

    /**
     * 不掺嘲讽的原始随机顺序（给 {@link #tauntAware} 当底座用，避免套娃时无限递归）。
     *
     * @return 策略
     */
    private static TargetStrategy randomRaw() {
        return (fight, user, skill, candidates) -> {
            List<LivingThing> ordered = new ArrayList<>(candidates);
            Collections.shuffle(ordered);
            return ordered;
        };
    }

    /**
     * 不掺嘲讽的原始顺序（保持候选本来的先后）。
     *
     * @return 策略
     */
    private static TargetStrategy firstRaw() {
        return (fight, user, skill, candidates) -> new ArrayList<>(candidates);
    }

    /**
     * 不掺嘲讽的"血最少优先"。
     *
     * @return 策略
     */
    private static TargetStrategy lowestHpRaw() {
        return (fight, user, skill, candidates) -> {
            List<LivingThing> ordered = new ArrayList<>(candidates);
            ordered.sort(Comparator.comparingDouble(TargetStrategies::hpRatio));
            return ordered;
        };
    }

    /**
     * 不掺嘲讽的"血最多优先"。
     *
     * @return 策略
     */
    private static TargetStrategy highestHpRaw() {
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
        TargetStrategy base = fallback == null ? firstRaw() : fallback;
        return (fight, user, skill, candidates) -> {
            List<LivingThing> ordered = base.order(fight, user, skill, candidates);
            List<LivingThing> taunted = new ArrayList<>();
            List<LivingThing> rest = new ArrayList<>();
            for (LivingThing one : ordered) {
                // ⚠️ 注意方向：查的是「**user 被 one 嘲讽了吗**」，
                //    而不是「one 身上有没有嘲讽」（旧实现就是这么写反的，等于没生效）。
                if (tauntLevelFrom(user, one) > 0) {
                    taunted.add(one);
                } else {
                    rest.add(one);
                }
            }
            if (taunted.isEmpty()) {
                return ordered;
            }
            // List.sort 是稳定排序：同级嘲讽保持 fallback 的相对顺序
            taunted.sort(Comparator
                    .comparingInt((LivingThing one) -> tauntLevelFrom(user, one))
                    .reversed());
            taunted.addAll(rest);
            return taunted;
        };
    }

    /**
     * 查「<b>candidate 对 attacker 的嘲讽等级</b>」——
     * 即 attacker 身上有没有 candidate 施加的 {@link Taunt}，返回其中最高的等级。
     * <p>
     * <b>方向别搞反</b>：A 给 B 施加嘲讽 → 效果挂在 <b>B</b> 身上、origin 是 <b>A 的 uuid</b>。
     * 所以"B 该优先打 A"= 查 B 的效果列表里有没有 origin == A.uuid 的嘲讽。
     * <p>
     * 纯读，不改任何状态；{@code attacker}/{@code candidate} 任一为 {@code null} 时返回 0。
     *
     * @param attacker  正在挑目标的那个
     * @param candidate 候选目标
     * @return 嘲讽等级；没有就是 0
     */
    public static int tauntLevelFrom(LivingThing attacker, LivingThing candidate) {
        if (attacker == null || candidate == null) {
            return 0;
        }
        String candidateUuid = candidate.getUUID();
        if (candidateUuid == null) {
            return 0;
        }
        int level = 0;
        for (Effect effect : attacker.getEntityEffectList()) {
            if (effect instanceof Taunt taunt && candidateUuid.equals(effect.getOrigin())) {
                level = Math.max(level, taunt.getLevel());
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
