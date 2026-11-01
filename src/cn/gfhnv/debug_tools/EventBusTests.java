package cn.gfhnv.debug_tools;

import cn.gfhnv.game.data.*;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.system.command.*;
import cn.gfhnv.game.system.fight.*;

import java.util.List;

import static cn.gfhnv.debug_tools.TestKit.*;
import static cn.gfhnv.debug_tools.TestSupport.*;
import static cn.gfhnv.debug_tools.TestProbes.*;

/**
 * 自测：**基础设施**。
 * <p>
 * 主题：事件总线的派发、去重、异常隔离与审计。
 * <p>
 * 由 {@code TestCommandSystem#main} 按顺序调用 {@code EventBusTests#runAll()}。
 */
public final class EventBusTests {

    /**
     * <b>事件总线内部行为</b>（2026-10-24 重写内部实现，事件类与监听器一行没改）：
     * ① 优先级顺序 ② 注册去重 ③ 按可赋值派发 ④ 异常隔离 ⑤ 派发中注销 ⑥ 诊断入口 ⑦ `getHandlers()` 返回副本。
     * <p>
     * 只碰自测自己的假事件，<b>不动</b>游戏里那 11 个真监听器 —— 开始时记下处理器总数，结束时必须回到同一个数
     * （这也顺带证明了"注册/注销是配平的"）。
     */
    private static void testEventBusInternals() {
        section("事件总线：异常隔离 / 去重 / 继承派发 / 派发中注销 / 优先级 / 诊断");

        int baseline = EventBus.handlerCount();
        EventBus.resetDispatchErrors();

        ProbeBusEvent event = new ProbeBusEvent();
        ProbeLastListener last = new ProbeLastListener(e -> e.log.add("last"));
        ProbeMiddleListener middle = new ProbeMiddleListener(e -> e.log.add("middle"));
        ProbeFirstListener first = new ProbeFirstListener(e -> e.log.add("first"));
        // 故意按 5 → 3 → 1 的顺序注册：顺序必须由 priority 决定，不是注册顺序
        EventBus.register(last);
        EventBus.register(middle);
        EventBus.register(first);

        // ① 优先级
        EventBus.post(event);
        check("事件总线：优先级小的先跑（乱序注册也是 1 → 3 → 5）—— 实际 " + event.log,
                event.log.equals(List.of("first", "middle", "last")));

        // ② 注册去重
        event.log.clear();
        EventBus.register(first);
        EventBus.post(event);
        check("事件总线：同一个对象注册两次只跑一次（以前会跑两次）—— 实际 " + event.log,
                event.log.equals(List.of("first", "middle", "last")));

        // ③ 按"可赋值"派发
        ProbeBusSubEvent subEvent = new ProbeBusSubEvent();
        EventBus.post(subEvent);
        check("事件总线：post 子类事件时，监听父类的也收到（旧实现按精确类匹配，会静默收不到）—— 实际 "
                        + subEvent.log,
                subEvent.log.equals(List.of("first", "middle", "last")));

        // ④ 异常隔离
        ProbeFirstListener thrower = new ProbeFirstListener(e -> {
            throw new IllegalStateException("自测故意抛的异常");
        });
        EventBus.register(thrower);
        event.log.clear();
        EventBus.post(event);
        check("事件总线：一个监听器抛异常不再打断派发（后面的照样收到）—— 日志 " + event.log
                        + "、错误数 " + EventBus.dispatchErrorCount() + "、" + EventBus.recentDispatchErrors(),
                event.log.equals(List.of("first", "middle", "last"))
                        && EventBus.dispatchErrorCount() == 1);
        EventBus.unregister(thrower);
        EventBus.resetDispatchErrors();

        // ⑤ 派发中注销：killer 与 first 同为 priority 1，注册更晚 → 在 first 之后、middle 之前跑
        ProbeFirstListener killer = new ProbeFirstListener(e -> EventBus.unregister(middle));
        EventBus.register(killer);
        event.log.clear();
        EventBus.post(event);
        check("事件总线：派发中 unregister(B) 之后 B 本轮不再被调用（以前迭代副本，B 照样跑）—— 实际 "
                        + event.log,
                event.log.equals(List.of("first", "last")));
        EventBus.unregister(killer);
        EventBus.register(middle);

        // ⑥ 诊断入口
        check("事件总线：dump() 看得见挂着的监听器（漏注销的也能在这里发现）—— " + EventBus.handlerCount()
                        + " 个",
                EventBus.dump().contains("ProbeFirstListener") && EventBus.dump().contains("ProbeLastListener"));
        check("事件总线：handlerCount(假事件) == 3 —— 实际 " + EventBus.handlerCount(ProbeBusEvent.class),
                EventBus.handlerCount(ProbeBusEvent.class) == 3);

        // ⑦ getHandlers() 返回副本
        int beforeClear = EventBus.handlerCount();
        EventBus.getHandlers().clear();
        check("事件总线：getHandlers() 返回副本（清空它动不到总线内部）—— 清空前后 " + beforeClear
                        + " / " + EventBus.handlerCount(),
                EventBus.handlerCount() == beforeClear);

        // ⑧ 收尾：假监听器全摘掉，处理器总数回到基线
        EventBus.unregister(first);
        EventBus.unregister(middle);
        EventBus.unregister(last);
        EventBus.resetDispatchErrors();
        check("事件总线：自测收尾（假监听器全摘掉，处理器总数回到 " + baseline + "）—— 实际 "
                        + EventBus.handlerCount(),
                EventBus.handlerCount() == baseline && EventBus.dispatchErrorCount() == 0);
    }

    /**
     * 跑本主题的全部用例（顺序 = 下面列的顺序）。
     */
    public static void runAll() throws Exception {
        testEventBusInternals();
    }

    private EventBusTests() {
    }
}
