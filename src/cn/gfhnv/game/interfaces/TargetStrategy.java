package cn.gfhnv.game.interfaces;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;

import java.util.List;

/**
 * 目标选择策略：决定「这一次技能打谁」。
 * <p>
 * 之所以做成接口，是为了让控制器不关心具体规则：随机、先打第一个、先打血最少的、
 * 优先打带嘲讽的……都可以自由组合（内置实现见
 * {@link cn.gfhnv.game.system.fight.TargetStrategies}），模组也可以自己实现一个塞给控制器。
 * <p>
 * <b>契约</b>：只负责<b>排序</b>，不负责截取数量 —— 技能要几个目标由控制器按
 * {@link Skill#getAims()} 决定（{@code 0}=自身、{@code -1}=全体、正数=排在前面的 N 个）。
 *
 * @author AI（DeepSeek）生成
 */
@FunctionalInterface
public interface TargetStrategy {

    /**
     * 把候选目标按「最想打的排最前」排序。
     *
     * @param fight      当前战斗
     * @param user       技能使用者
     * @param skill      正在释放的技能
     * @param candidates 候选目标（控制器已按 {@link Skill#isForEnemies()} 分好阵营）
     * @return 排好序的新列表；<b>不要</b>修改 {@code candidates}，也不要返回它的视图
     */
    List<LivingThing> order(Fight fight, LivingThing user, Skill skill, List<LivingThing> candidates);
}
