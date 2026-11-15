package cn.gfhnv.game.event;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;

/**
 * <b>技能即将生效</b>（已经过了 {@code canUse}：目标、冷却、法力都满足）。
 * <p>
 * <b>可取消</b>：{@code setCanceled(true)} 等于"这一击不生效"——
 * 不扣法力、不进冷却、不产生任何效果，{@link Skill#use} 返回 {@code false}。
 * 适合做沉默/封印/消耗类扩展。
 * <p>
 * 发布点：{@code Skill#use(...)} 的两个重载，均在 {@code canUse} 通过之后、{@code comeToEffect} 之前。
 *
 * @author AI（DeepSeek）生成
 */
public class SkillUseEvent extends Event {

    private final Fight fight;
    private final LivingThing user;
    private final Skill skill;

    public SkillUseEvent(Fight fight, LivingThing user, Skill skill) {
        this.fight = fight;
        this.user = user;
        this.skill = skill;
    }

    /**
     * @return 当前战斗
     */
    public Fight getFight() {
        return fight;
    }

    /**
     * @return 施法者
     */
    public LivingThing getUser() {
        return user;
    }

    /**
     * @return 即将释放的技能
     */
    public Skill getSkill() {
        return skill;
    }
}
