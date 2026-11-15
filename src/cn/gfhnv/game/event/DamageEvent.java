package cn.gfhnv.game.event;

import cn.gfhnv.game.damage.Damage;
import cn.gfhnv.game.damage.DamageType;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.skill.Skill;

/**
 * 一次伤害的广播事件。{@link #getDamage()} 里带着三段数值（原始 / 计算 / 实际）
 * 与伤害类型，见 {@code Damage} 的类注释。
 * <p>
 * <b>时序</b>：{@code new Damage(...)} 在构造里就把公式算完了（它内部会发
 * {@code CalculateDamageGetStatusEvent} / {@code CalculateDamageEndEvent}），
 * 所以本事件被 post 出来时 {@code calculateDamageResult} <b>已经定下来</b>。
 * <p>
 * 但结算是在事件<b>之后</b>做的（{@code LivingThing#getDamage} 读的是同一个对象），
 * 所以监听者<b>可以</b>调 {@code getDamage().setDamageAmount(...)} 覆盖这一击 ——
 * 自测正是用它造"必死一击"；正常玩法里不该有人这么做。
 *
 * @author gfhnv；AI（DeepSeek）加了伤害类型
 */
public class DamageEvent extends Event {
    private final Damage damage;
    private final LivingThing attacker;
    private final LivingThing attackedEntity;
    private final Skill skill;

    /**
     * @param attacker       伤害来源实体
     * @param attackedEntity 受害实体
     * @param skill          造成伤害的技能（可为 {@code null}）
     * @param damageTypes    伤害类型（不传按 {@link DamageType#SpecialSkill} 算）
     */
    public DamageEvent(LivingThing attacker, LivingThing attackedEntity, Skill skill,
                       DamageType... damageTypes) {
        this.attacker = attacker;
        this.attackedEntity = attackedEntity;
        this.skill = skill;
        damage = new Damage(attacker, attackedEntity, skill, damageTypes);
    }

    /**
     * @return 这次伤害（三段数值 + 类型 + 来源）
     */
    public Damage getDamage() {
        return damage;
    }

    /**
     * @return 伤害来源实体（谁打的）
     */
    public LivingThing getAttacker() {
        return attacker;
    }

    /**
     * @return 受害实体（谁挨的）
     */
    public LivingThing getAttackedEntity() {
        return attackedEntity;
    }

    /**
     * @return 造成伤害的技能；{@code null} = 不是技能伤害
     */
    public Skill getSkill() {
        return skill;
    }
}
