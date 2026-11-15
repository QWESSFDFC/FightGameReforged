package cn.gfhnv.game.event;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.system.fight.Fight;

/**
 * <b>实体死亡、即将离场</b>：回合循环的死亡清扫把它移出阵营列表之后、{@code whenLeaveFight} 之前。
 * <p>
 * 适合做击杀播报、掉落、成就统计。注意这时它<b>已经不在</b> {@code fighterList / enemiesList} 里了，
 * 但 {@code whenLeaveFight} 还没跑（容器离场结算就是那一步做的事）。
 * <p>
 * 发布点：{@code FightTurnPastListener#fightTurnPastOne(...)} 的死亡清扫段。
 *
 * @author AI（DeepSeek）生成
 */
public class EntityDeathEvent extends Event {

    private final Fight fight;
    private final LivingThing entity;

    public EntityDeathEvent(Fight fight, LivingThing entity) {
        this.fight = fight;
        this.entity = entity;
    }

    /**
     * @return 当前战斗
     */
    public Fight getFight() {
        return fight;
    }

    /**
     * @return 死掉的实体（{@code isAlive()} 已为 false）
     */
    public LivingThing getEntity() {
        return entity;
    }
}
