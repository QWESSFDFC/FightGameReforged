package cn.gfhnv.game.officialStuff.customSkill.flameReaverSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entityController.FixOrderController;
import cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;

import java.math.BigDecimal;
import java.util.List;

/**
 * 【沉默的悲叹】—— 二阶段的蓄力招式。
 * <p>
 * 官方：获得【灾难之力】并进入【沉默的悲叹】状态，<b>自身的下次行动延后 100%</b>，
 * 下次行动时施放【莫因舍弃而哭泣】。
 * <p>
 * 本项目实现：
 * <ol>
 *     <li>获得若干层【灾难之力】（蓄力期间攒层，所以接下来那一记大招更重）；</li>
 *     <li>把<b>自己下一个回合</b>往后推 100%（走 {@code FlameReaver#delayNextOwnTurn} ——
 *     自己在回合里的时候时间轴上还没有下个条目，所以那里是"自己排一个双倍间隔的下回合、
 *     并让本回合不再另排"）；</li>
 *     <li>让控制器<b>强制下一招</b>放【莫因舍弃而哭泣】。</li>
 * </ol>
 * <b>为什么是"延后"而不是"跳过"</b>：官方写的是"下次行动时施放"，也就是照常行动、只是被推迟 ——
 * 延后到时间轴后面对玩家是实打实的收益（多一个回合的窗口），与自己跳过自己完全不同。
 *
 * @author AI（DeepSeek）生成
 */
public class SilentLament extends FlameReaverSkill {

    /**
     * 蓄力时获得的【灾难之力】层数。
     */
    private static final int CHARGE_DISASTER_POWER = 2;

    /**
     * 自身下次行动被延后的比例（1.0 = 延后 100%，即需要两倍时间）。
     */
    private static final BigDecimal DELAY_PERCENT = BigDecimal.ONE;

    /**
     * 蓄力时召唤几只处于【镣锁】状态的容器（官方「迷失的共祭」）。
     */
    private static final int LOCKED_CONTAINER_COUNT = 2;

    /**
     * 构造技能：作用于自身。
     */
    public SilentLament() {
        super("沉默的悲叹", "获得【灾难之力】并进入蓄力状态，自身下次行动延后，下次行动时施放【莫因舍弃而哭泣】。", 0, 0);
    }

    /**
     * 复制构造器。
     *
     * @param other 被复制的技能
     */
    public SilentLament(SilentLament other) {
        super(other);
    }

    @Override
    public Skill copy() {
        return new SilentLament(this);
    }

    /**
     * 只有二阶段、且当前没在蓄力时才放得出来。
     */
    @Override
    public boolean canUse(Fight fight, LivingThing user) {
        return user instanceof FlameReaver reaver && reaver.isPhaseTwo() && !reaver.isCharging();
    }

    @Override
    public boolean canUse(Fight fight, LivingThing user, List<LivingThing> enemies) {
        return super.canUse(fight, user, enemies) && canUse(fight, user);
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user) {
        if (!(user instanceof FlameReaver reaver)) {
            return;
        }
        System.out.println(reaver.getName() + "进入【沉默的悲叹】状态，开始蓄力");
        reaver.addDisasterPower(CHARGE_DISASTER_POWER);
        reaver.setCharging(true);
        reaver.delayNextOwnTurn(DELAY_PERCENT);
        // 官方「迷失的共祭」：施放【沉默的悲叹】时，消耗生命值召唤处于【镣锁】状态的残破容器
        reaver.summonLockedContainers(fight, LOCKED_CONTAINER_COUNT);
        if (reaver.getController() instanceof FixOrderController controller) {
            controller.forceNextSkill("莫因舍弃而哭泣");
        }
    }
}