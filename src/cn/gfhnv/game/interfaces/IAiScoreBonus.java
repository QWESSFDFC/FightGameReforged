package cn.gfhnv.game.interfaces;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.system.fight.Fight;

/**
 * 技能自己申报的 <b>「AI 评分加成」</b>：让"没有伤害但很关键"的技能也能被 AI 选中。
 * <p>
 * <b>为什么需要它</b>：{@code ThinkingControllerAI#act} 在同 Tag 的技能之间按
 * {@code evaluateAction} 打分，而那个分数是**按伤害与血量算的**（"目标血越少加越多"、
 * "能斩杀再加 30"）。于是<b>变身 / 自我强化这类 0 伤害技能永远拿不到最高分</b> ——
 * 典型例子：白厄的【大招:永劫燔世】{@code aims=0}、倍率全 0，它的目标列表是"自己"，
 * 分数只剩"权重 − 蓝耗惩罚"，永远打不过普攻。【{@code ATTACK} 权重再高也没用】——
 * 权重只决定"选哪一类 Tag"，不参与同一类里技能之间的比较。
 * <p>
 * <b>做法</b>：技能实现本接口，AI 在打分时把 {@link #aiScoreBonus} 加进 {@code bonus}。
 * 返回 <b>0 表示"这次不值得"</b>（例如火种不够、已经变身过了），这样"该不该放"的判断
 * 留在技能自己手里，AI 不需要懂每种技能的机制。
 * <p>
 * <b>与别的扩展点的关系</b>：形状与 {@link IModifyDamage} / {@link IModifyIgnitionMax} 一致
 * （一个小接口 + 一行 hook），模组也能复用；数值建议做成
 * {@link cn.gfhnv.game.skill.SkillCoefficientTunable} 的具名系数，这样能在
 * {@code SkillData.json} 里调"AI 多想放它"，不用改代码。
 * <p>
 * ⚠️ <b>它只影响"AI 选不选"</b>：不改伤害、不改命中、也不改玩家手动释放时的任何行为。
 *
 * @author AI（DeepSeek）生成
 */
public interface IAiScoreBonus {

    /**
     * @param fight  当前战斗（可能为 {@code null} —— 自测里直接算分时不建战斗）
     * @param self   施法者（AI 的持有者）
     * @param target 这次准备作用的目标（{@code aims=0} 的技能就是 {@code self} 本人）
     * @return 加多少分（0 = 不加；建议远大于普通攻击的典型分数 10，才能稳定压过它）
     */
    double aiScoreBonus(Fight fight, LivingThing self, LivingThing target);
}
