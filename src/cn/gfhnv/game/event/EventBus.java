package cn.gfhnv.game.event;

import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.system.logSystem.LogWriter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 事件总线（2026-10-24 重写内部实现，<b>事件类与监听器一行都不用改</b>）。
 * <p>
 * <b>对外契约一个字没动</b>：{@link #register(Object)} / {@link #unregister(Object)} /
 * {@link #post(Event)} / {@link #clear()} / {@link #getHandlers()} 的签名、{@link SubscribeEvent#priority()}
 * 的语义（数字越小越先跑）、{@link Event#isCanceled()} 的行为全部保留。
 * 11 个 {@code @SubscribeEvent} 方法、14 个事件类、19 个 {@code post} 调用点<b>零改动</b>。
 * <p>
 * <b>这一版修掉的四件事</b>：
 * <ol>
 *     <li><b>异常隔离</b>：单个监听器抛异常不再打断整条派发链（以前直接
 *     {@code throw new RuntimeException}，后面的监听器<b>收不到事件</b>）；现在记进
 *     {@link #dispatchErrorCount()} + {@link #recentDispatchErrors()} 再继续，
 *     自测用它断言"一条都没抛过"。</li>
 *     <li><b>注册去重</b>：同一个对象 + 同一个方法注册两次只保留一条（以前会跑两次）。</li>
 *     <li><b>按"可赋值"派发</b>：{@code post(new SubEvent())} 也会送到监听 {@code BaseEvent} 的监听器
 *     （以前只认 {@code event.getClass()} 精确匹配 —— 今天 14 个事件都直接继承 {@link Event}，
 *     所以行为逐位不变；但以后加事件继承不会再"静默收不到"）。</li>
 *     <li><b>派发中途注销生效</b>：{@code post} 里 A 把 B 注销掉，B <b>这一轮就不再被调用</b>
 *     （以前迭代的是副本，B 照样跑）。</li>
 * </ol>
 * <b>顺带</b>：注册/注销后重建"事件类 → 不可变数组"的快照，{@code post} 只读数组
 * （以前每次 {@code post} 都 {@code new ArrayList} 一份，{@code DamageEvent} 这种热路径白送垃圾）；
 * 新增 {@link #dump()} / {@link #handlerCount()} / {@link #audit(String)} 三个可诊断入口
 * （以前挂了多少监听器、谁漏了注销，完全看不见）。
 * <p>
 * ⚠️ <b>没做的事</b>：不引入弱引用自动清理（事件没触发就被 GC = 行为隐式消失，比泄漏更难查）、
 * 不做异步派发（单人回合制，只会引入竞态）、不删 {@link Event#setCanceled(boolean)}
 * （全项目 0 次调用，删了是破坏性改动）。
 *
 * @author AI（DeepSeek）生成；2026-10-24 重写内部实现
 */
public class EventBus {

    /**
     * 事件类型 → 这个类型上注册的处理器（按优先级 + 注册顺序排好）。
     */
    private static final Map<Class<?>, List<EventHandler>> handlers = new HashMap<>();

    /**
     * 派发快照：事件类型 → 它和它所有父类上的处理器（已按 priority 稳定排序）。
     * <p>
     * <b>只在注册/注销/清空后重建</b>，{@link #post(Event)} 只读它 —— 于是每次派发不再分配列表。
     */
    private static final Map<Class<?>, List<EventHandler>> dispatchCache = new HashMap<>();

    /**
     * 注册序号（同一优先级内按它保序，与旧实现的"插入到同优先级末尾"等价）。
     */
    private static long sequence = 0;

    /**
     * 派发过程中被监听器抛出来的异常次数（只增不减，{@link #resetDispatchErrors()} 可清）。
     */
    private static int dispatchErrorCount = 0;

    /**
     * 最近若干次派发异常的摘要（带监听器类名 + 方法名 + 异常类型，够定位就行）。
     */
    private static final List<String> recentDispatchErrors = new ArrayList<>();

    /**
     * 最多记住几条派发异常（不无限涨）。
     */
    private static final int MAX_REMEMBERED_ERRORS = 8;

    /**
     * 工具类，不允许实例化。
     */
    private EventBus() {
    }

    /**
     * 注册一个监听器对象：把它身上所有 {@link SubscribeEvent} 方法登记进总线。
     * <p>
     * 同一个对象 + 同一个方法<b>重复注册只会保留一条</b>（2026-10-24 起）。
     *
     * @param listener 监听器对象（方法可以不是 {@code public}）
     * @throws IllegalArgumentException 方法参数不是"有且只有一个 {@link Event} 子类"
     */
    public static void register(Object listener) {
        Method[] methods = listener.getClass().getDeclaredMethods();
        for (Method method : methods) {
            method.setAccessible(true);
            if (method.isAnnotationPresent(SubscribeEvent.class)) {
                Parameter[] params = method.getParameters();
                if (params.length != 1) {
                    LogWriter.writeLog("只能有且只有一个参数:" + method);
                    throw new IllegalArgumentException("只能有且只有一个参数:" + method);
                }
                Class<?> eventType = params[0].getType();
                if (!Event.class.isAssignableFrom(eventType)) {
                    LogWriter.writeLog("参数只能是event或者其子类:" + method);
                    throw new IllegalArgumentException("参数只能是event或者其子类:" + method);
                }

                SubscribeEvent annotation = method.getAnnotation(SubscribeEvent.class);
                int priority = annotation.priority();

                List<EventHandler> list = handlers.computeIfAbsent(eventType, k -> new ArrayList<>());
                if (containsSameHandler(list, listener, method)) {
                    // 同一个对象 + 同一个方法注册两次：以前会跑两次，现在只留一条
                    continue;
                }
                EventHandler handler = new EventHandler(listener, method, priority, sequence++);

                // 按优先级升序插入（数字越小越优先；同优先级按注册顺序，与旧实现一致）
                int index = 0;
                while (index < list.size() && list.get(index).priority <= priority) {
                    index++;
                }
                list.add(index, handler);
                invalidateDispatchCache();
            }
        }
    }

    /**
     * 注销一个监听器对象在总线上的所有方法。
     * <p>
     * <b>正在派发时调用也生效</b>：被摘掉的那条会被标记为"已死"，本轮剩下的派发直接跳过它。
     *
     * @param listener 监听器对象（按<b>引用相等</b>判断）
     */
    public static void unregister(Object listener) {
        boolean changed = false;
        for (List<EventHandler> handlerList : handlers.values()) {
            for (EventHandler handler : handlerList) {
                if (handler.belongsTo(listener)) {
                    handler.alive = false;
                    changed = true;
                }
            }
            if (handlerList.removeIf(handler -> handler.belongsTo(listener))) {
                changed = true;
            }
        }
        if (changed) {
            invalidateDispatchCache();
        }
    }

    /**
     * 派发一个事件。
     * <p>
     * 送给"这个事件类型 + 它所有父类"上注册的处理器，按 {@link SubscribeEvent#priority()} 升序；
     * 单个处理器抛异常<b>不影响后面的处理器</b>（记进 {@link #dispatchErrorCount()}）。
     * 事件一旦 {@link Event#isCanceled() 被取消}，剩下的处理器就不再执行（保留旧语义）。
     *
     * @param event 事件
     */
    public static void post(Event event) {
        if (event == null || event.isCanceled()) {
            return;
        }
        for (EventHandler handler : dispatchListOf(event.getClass())) {
            if (event.isCanceled()) {
                return;
            }
            if (!handler.alive) {
                continue;
            }
            handler.dispatch(event);
        }
    }

    /**
     * 清空总线：所有监听器都摘掉（同时把它们的"存活标记"置死）。
     */
    public static void clear() {
        for (List<EventHandler> handlerList : handlers.values()) {
            for (EventHandler handler : handlerList) {
                handler.alive = false;
            }
        }
        handlers.clear();
        invalidateDispatchCache();
    }

    /**
     * @return 内部表的<b>副本</b>（改它不影响总线；{@code 0} 处调用，保留是为了不破坏既有的公开签名）
     */
    public static Map<Class<?>, List<EventHandler>> getHandlers() {
        Map<Class<?>, List<EventHandler>> copy = new LinkedHashMap<>();
        for (Map.Entry<Class<?>, List<EventHandler>> entry : handlers.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return copy;
    }

    /**
     * @return 当前挂着的处理器总数（所有事件类型加起来）
     */
    public static int handlerCount() {
        int total = 0;
        for (List<EventHandler> handlerList : handlers.values()) {
            total += handlerList.size();
        }
        return total;
    }

    /**
     * @param eventType 事件类型
     * @return 这个类型上直接注册的处理器数量（不含父类的）
     */
    public static int handlerCount(Class<?> eventType) {
        List<EventHandler> list = handlers.get(eventType);
        return list == null ? 0 : list.size();
    }

    /**
     * 把"现在总线上有什么"打成多行文本（事件类型 → 监听器类名#方法名 + 优先级）。
     * <p>
     * 漏了 {@code unregister} 的监听器在这里看得见 —— 以前完全没法观测。
     *
     * @return 可读的清单（空总线是 {@code "(空)"}）
     */
    public static String dump() {
        if (handlers.isEmpty()) {
            return "(空)";
        }
        StringBuilder builder = new StringBuilder();
        List<Class<?>> types = new ArrayList<>(handlers.keySet());
        types.sort(Comparator.comparing(Class::getName));
        for (Class<?> type : types) {
            builder.append(type.getSimpleName()).append(" ← ");
            List<String> names = new ArrayList<>();
            for (EventHandler handler : handlers.get(type)) {
                names.add(handler.describe());
            }
            builder.append(String.join("，", names)).append('\n');
        }
        return builder.toString();
    }

    /**
     * 打一行"体检"：带一个标签 + 各事件类型的监听器数量。
     * <p>
     * 用法：开战前打一次、战斗结束后再打一次，两次数量不一致就是有监听器没注销。
     *
     * @param tag 标签（例如 {@code "战斗结束"}）
     * @return 单行摘要
     */
    public static String audit(String tag) {
        StringBuilder builder = new StringBuilder();
        builder.append("[事件总线] ").append(tag == null ? "" : tag)
                .append("：共 ").append(handlerCount()).append(" 个处理器");
        List<Class<?>> types = new ArrayList<>(handlers.keySet());
        types.sort(Comparator.comparing(Class::getName));
        for (Class<?> type : types) {
            builder.append("，").append(type.getSimpleName())
                    .append('=').append(handlerCount(type));
        }
        return builder.toString();
    }

    /**
     * @return 派发过程中监听器抛异常的累计次数（自测用它断言"一条都没抛过"）
     */
    public static int dispatchErrorCount() {
        return dispatchErrorCount;
    }

    /**
     * @return 最近几次派发异常的摘要（最多 {@value #MAX_REMEMBERED_ERRORS} 条）
     */
    public static List<String> recentDispatchErrors() {
        return new ArrayList<>(recentDispatchErrors);
    }

    /**
     * 把派发异常的计数清零（<b>只给自测用</b>：每个用例开始前清一次，结束时断言为 0）。
     */
    public static void resetDispatchErrors() {
        dispatchErrorCount = 0;
        recentDispatchErrors.clear();
    }

    /**
     * @param list     某个事件类型上已注册的处理器
     * @param listener 监听器对象
     * @param method   处理方法
     * @return 是否已经注册过"同一个对象的同一个方法"
     */
    private static boolean containsSameHandler(List<EventHandler> list, Object listener, Method method) {
        for (EventHandler handler : list) {
            if (handler.belongsTo(listener) && handler.method.equals(method)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 取某个事件类型的派发快照（含父类上的处理器），必要时重建。
     *
     * @param eventType 事件类型
     * @return 只读的处理器列表（按优先级 + 注册顺序）
     */
    private static List<EventHandler> dispatchListOf(Class<?> eventType) {
        List<EventHandler> cached = dispatchCache.get(eventType);
        if (cached != null) {
            return cached;
        }
        List<EventHandler> merged = new ArrayList<>();
        // 从事件类自己往上走：事件类自己的处理器 + 父类的（父类里目前只有 Event，没人注册）
        for (Class<?> type = eventType;
             type != null && Event.class.isAssignableFrom(type);
             type = type.getSuperclass()) {
            List<EventHandler> list = handlers.get(type);
            if (list != null) {
                merged.addAll(list);
            }
        }
        // List.sort 是稳定排序：同优先级保持注册顺序（与旧实现的插入位置等价）
        merged.sort(Comparator.comparingInt(handler -> handler.priority));
        List<EventHandler> snapshot = Collections.unmodifiableList(merged);
        dispatchCache.put(eventType, snapshot);
        return snapshot;
    }

    /**
     * 注册/注销/清空之后调用：把派发快照全部作废（下次派发按需重建）。
     */
    private static void invalidateDispatchCache() {
        dispatchCache.clear();
    }

    /**
     * 记一次派发异常：计数 + 留一条摘要 + 写日志（<b>绝不往外抛</b>，否则又变成打断派发）。
     *
     * @param handler 出事的处理器
     * @param event   当时的事件
     * @param error   异常
     */
    private static void recordDispatchError(EventHandler handler, Event event, Throwable error) {
        dispatchErrorCount++;
        String summary = handler.describe() + " 处理 " + event.getClass().getSimpleName()
                + " 时抛了 " + error.getClass().getSimpleName()
                + (error.getMessage() == null ? "" : "：" + error.getMessage());
        if (recentDispatchErrors.size() >= MAX_REMEMBERED_ERRORS) {
            recentDispatchErrors.remove(0);
        }
        recentDispatchErrors.add(summary);
        try {
            LogWriter.writeLog("[事件总线] " + summary);
        } catch (Throwable ignored) {
            // 日志本身失败（例如文件被占用）也绝不能影响派发
        }
    }

    /**
     * 一条"监听器 → 方法"的登记项。
     */
    private static class EventHandler implements Consumer<Event> {

        /**
         * 监听器对象（比对用引用相等）。
         */
        final Object listener;

        /**
         * 处理方法。
         */
        final Method method;

        /**
         * 优先级（数字越小越先跑）。
         */
        final int priority;

        /**
         * 注册序号（同优先级内保序）。
         */
        final long sequence;

        /**
         * 是否还活着：注销后置假，本轮派发剩下的部分会跳过它。
         */
        volatile boolean alive = true;

        EventHandler(Object listener, Method method, int priority, long sequence) {
            this.listener = listener;
            this.method = method;
            this.priority = priority;
            this.sequence = sequence;
        }

        @Override
        public void accept(Event event) {
            dispatch(event);
        }

        /**
         * 真正调用监听器方法：<b>异常就地吞掉并记档</b>，不再向外抛。
         *
         * @param event 事件
         */
        void dispatch(Event event) {
            if (event.isCanceled()) {
                return;
            }
            try {
                method.invoke(listener, event);
            } catch (InvocationTargetException e) {
                // 反射会把监听器自己的异常包一层，这里剥出来，日志里才看得见真正的栈
                recordDispatchError(this, event, e.getTargetException());
            } catch (Throwable e) {
                recordDispatchError(this, event, e);
            }
        }

        /**
         * @param listener 监听器对象
         * @return 这条登记项是不是属于它
         */
        boolean belongsTo(Object listener) {
            return this.listener == listener;
        }

        /**
         * @return 人类可读的描述（{@code 类名#方法名(priority)}）
         */
        String describe() {
            return listener.getClass().getSimpleName() + "#" + method.getName() + "(p" + priority + ")";
        }
    }
}
