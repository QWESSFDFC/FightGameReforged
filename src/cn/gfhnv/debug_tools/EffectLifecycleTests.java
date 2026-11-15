package cn.gfhnv.debug_tools;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entity.PropertyEnhanceType;
import cn.gfhnv.game.event.EffectUpdateEvent;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.AttackEnhance;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.Frozen;
import cn.gfhnv.game.system.fight.ActionSignal;
import cn.gfhnv.game.system.fight.TurnEntry;
import cn.gfhnv.game.system.fight.TurnManager;

import java.math.BigDecimal;

import static cn.gfhnv.debug_tools.TestKit.check;
import static cn.gfhnv.debug_tools.TestKit.section;
import static cn.gfhnv.debug_tools.TestProbes.ProbeCountingEffect;
import static cn.gfhnv.debug_tools.TestSupport.entityTemplateOf;
import static cn.gfhnv.debug_tools.TestSupport.hasEffect;

/**
 * 自测：**效果生命周期**（`lastTime` 口径 / 到期收尾 / 战斗结束后的清理）。
 * <p>
 * 主题：`Effect` 的计时与收尾 —— 2026-10-25 统一口径为"`lastTime` = 还会 tick 几次"，
 * 以及"战斗结束后临时属性不能是负值"。坑的清单见 `notes_for_llm/subsystems/80-EFFECTS.md`。
 * <p>
 * 由 {@code TestCommandSystem#main} 按顺序调用 {@code EffectLifecycleTests#runAll()}。
 */
public final class EffectLifecycleTests {

    private EffectLifecycleTests() {
    }

    /**
     * <b>`lastTime = N` ⇒ 只再 tick N 次，第 N 次 tick 当场收尾移除</b>（2026-10-25 统一口径）。
     * <p>
     * 旧口径是"先结算、后减 1、下一拍才判到期"⇒ `N` 实际活 N+1 个回合末，
     * `/effect list` 显示的"剩余 N 回合"也一直偏低一档。
     */
    private static void testLastTimeCountsTicks() {
        section("效果计时：lastTime = 还会 tick 几次");

        LivingThing dummy = entityTemplateOf("game_official_content:commonInsect").copy();
        ProbeCountingEffect effect = new ProbeCountingEffect(2);
        dummy.addEffect(effect);
        check("挂上时走 initialEffect 生效一次（不算 tick）—— 实际 " + effect.applies,
                effect.applies == 1);

        TurnEntry turn = new TurnEntry(dummy, BigDecimal.ONE, BigDecimal.ZERO);
        EventBus.post(new EffectUpdateEvent(dummy, turn));
        check("第 1 次 tick：settle 一次、lastTime 1、还在身上 —— 实际 结算 " + effect.applies
                        + "、lastTime " + effect.getLastTime(),
                effect.applies == 2 && effect.getLastTime() == 1
                        && dummy.getEntityEffectList().contains(effect));

        EventBus.post(new EffectUpdateEvent(dummy, turn));
        check("第 2 次 tick：结算完当场移除（不再多活一回合）—— 实际 结算 " + effect.applies
                        + "、身上还有吗 " + dummy.getEntityEffectList().contains(effect),
                effect.applies == 3 && !dummy.getEntityEffectList().contains(effect));
    }

    /**
     * <b>`lastTime = 1` 的冰冻只跳一次</b>。
     * <p>
     * 做法：时间轴上排两个回合条目，挂上冰冻时"下一个"被标记跳过；tick 一次（= 被跳过那回合的末尾）
     * 之后，**再下一个**不能被标记 —— 标记了就是"冻两回合"。
     */
    private static void testFrozenOneTurnSkipsOnce() {
        section("冰冻：lastTime=1 只跳一次自己的回合");

        LivingThing dummy = entityTemplateOf("game_official_content:commonInsect").copy();
        TurnManager.getTurns().clear();
        TurnManager.setPresentTime(BigDecimal.ZERO);
        TurnEntry first = new TurnEntry(dummy, BigDecimal.ONE, BigDecimal.ZERO);
        TurnEntry second = new TurnEntry(dummy, BigDecimal.TEN, BigDecimal.ZERO);
        TurnManager.getTurns().add(first);
        TurnManager.getTurns().add(second);
        TurnManager.sort();

        dummy.addEffect(new Frozen(1));
        check("挂上就标记下一次要跳过 —— 实际 first=" + first.getActionSignal()
                        + "、second=" + second.getActionSignal(),
                first.getActionSignal() == ActionSignal.SPECIAL_ACTION);

        EventBus.post(new EffectUpdateEvent(dummy, first));
        check("被跳过那回合结束时：冰冻已经到期、不再标记再下一个（否则就是冻两回合）—— 实际 身上还有吗 "
                        + hasEffect(dummy, "frozenEffect") + "、second=" + second.getActionSignal(),
                !hasEffect(dummy, "frozenEffect") && second.getActionSignal() != ActionSignal.SPECIAL_ACTION);
        TurnManager.getTurns().clear();
        TurnManager.setPresentTime(BigDecimal.ZERO);
    }

    /**
     * <b>战斗结束后临时属性不能是负值</b>。
     * <p>
     * 为什么必须这个顺序：若 `resetTemporary()` 先清零、再让效果 `whenLastTimeEnd` 减一遍，
     * 战后属性就会变负（例如 -0.2），带 `HpEnhanceEffect` 时还会把血夹到 0.8×上限。
     */
    private static void testWhenFightEndsLeavesNoNegativeEnhance() {
        section("战斗结束：效果收尾后再清零，*Enhance* 不能变负");

        LivingThing dummy = entityTemplateOf("game_official_content:commonInsect").copy();
        dummy.addEffect(new AttackEnhance(0.2, 3));
        double before = dummy.getPropertyEnhancePercent(PropertyEnhanceType.ATTACK);
        dummy.whenFightEnds();
        check("挂过攻击增益（+" + Math.round(before * 100) + "%）再结束战斗：清零而不是负值 —— 实际 "
                        + dummy.getPropertyEnhancePercent(PropertyEnhanceType.ATTACK) + "、" + dummy.getPropertyEnhancePercent(PropertyEnhanceType.MAX_HP)
                        + "、" + dummy.getPropertyEnhancePercent(PropertyEnhanceType.SPEED),
                Math.abs(dummy.getPropertyEnhancePercent(PropertyEnhanceType.ATTACK)) < 1e-9
                        && Math.abs(dummy.getPropertyEnhancePercent(PropertyEnhanceType.MAX_HP)) < 1e-9
                        && Math.abs(dummy.getPropertyEnhancePercent(PropertyEnhanceType.SPEED)) < 1e-9);
        check("效果列表也清空了 —— 实际 " + dummy.getEntityEffectList().size() + " 条",
                dummy.getEntityEffectList().isEmpty());
    }

    /**
     * 跑本主题的全部用例（顺序 = 下面列的顺序）。
     */
    public static void runAll() throws Exception {
        // tick 的监听器只在 GameMain 里注册（自测不启动游戏），这里临时挂一个，
        // 否则 EventBus.post(EffectUpdateEvent) 没人接 —— 断言会看到"什么都没发生"。
        cn.gfhnv.game.eventListener.EffectEventListener ticker =
                new cn.gfhnv.game.eventListener.EffectEventListener();
        EventBus.register(ticker);
        try {
            testLastTimeCountsTicks();
            testFrozenOneTurnSkipsOnce();
            testWhenFightEndsLeavesNoNegativeEnhance();
        } finally {
            EventBus.unregister(ticker);
        }
    }
}
