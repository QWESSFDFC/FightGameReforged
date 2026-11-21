package cn.gfhnv.game.system.fight;

import cn.gfhnv.game.entity.LivingThing;

import java.util.List;

/**
 * <b>战斗状态判定</b>（2026-11-21 第 86 批）。
 * <p>
 * 用途：给"在回合队列里追加的动作"一个独立、可测试的判据 —— 动作真正执行时，
 * 战斗可能已经因为最后一个敌人倒下而结束了。回合循环要等到<b>下一圈</b>才清尸体、
 * 判胜负，所以本回合内追加的动作不能只看 {@code GameMain.isInFight()}（那时还是 true），
 * 必须看两个阵营里还有没有活人。
 * <p>
 * 与 {@code FightTurnPastListener} 的关系：那边仍然是唯一的胜负执行者；
 * 这里只回答"现在还能不能行动"，不结束战斗、不改时间轴。
 *
 * @author AI（DeepSeek）生成
 */
public final class FightState {

    private FightState() {
    }

    /**
     * @param side 一个阵营的列表（可为 {@code null}）
     * @return 这个阵营里是否还有活着的实体
     */
    public static boolean hasLivingSide(List<LivingThing> side) {
        if (side == null) {
            return false;
        }
        for (LivingThing thing : side) {
            if (thing != null && thing.isAlive()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 战斗是否已经分出结果：任意一方全灭（或列表为空）就为 {@code true}。
     * <p>
     * 注意它看的是<b>当前列表</b>，不等到下一回合清尸体 —— 这正是追加动作需要的时机。
     *
     * @param fight 当前战斗；{@code null} 按已结束处理
     * @return 是否已分出胜负
     */
    public static boolean isDecided(Fight fight) {
        if (fight == null) {
            return true;
        }
        return !hasLivingSide(fight.getFighterList()) || !hasLivingSide(fight.getEnemiesList());
    }

    /**
     * 某个实体现在还能不能执行回合内动作。
     * <p>
     * 需要同时满足：实体活着、仍在某一方阵营里、战斗还没分出结果。
     * 不判断"是不是轮到它"（那是回合循环的事）。
     *
     * @param fight 当前战斗（可为 {@code null}，回退到实体自己的 {@code participateFight}）
     * @param actor 要行动的实体
     * @return 能不能行动
     */
    public static boolean canTakeAction(Fight fight, LivingThing actor) {
        if (actor == null || !actor.isAlive()) {
            return false;
        }
        Fight context = fight != null ? fight : actor.getParticipateFight();
        if (context == null || isDecided(context)) {
            return false;
        }
        return context.getFighterList().contains(actor) || context.getEnemiesList().contains(actor);
    }
}
