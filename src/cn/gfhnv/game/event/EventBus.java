package cn.gfhnv.game.event;

import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.system.logSystem.LogWriter;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * AI写的
 */
public class EventBus {
    private static final Map<Class<?>, List<EventHandler>> handlers = new HashMap<>();

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

                EventHandler handler = new EventHandler(listener, method, priority);

                // 按优先级升序插入列表（数字越小越优先）
                List<EventHandler> list = handlers.computeIfAbsent(eventType, k -> new ArrayList<>());
                int index = 0;
                while (index < list.size() && list.get(index).priority <= priority) {
                    index++;
                }
                list.add(index, handler);
            }
        }
    }

    public static void unregister(Object listener) {
        for (List<EventHandler> handlerList : handlers.values()) {
            handlerList.removeIf(handler -> handler.belongsTo(listener));
        }
    }

    public static void post(Event event) {
        if (event.isCanceled()) return;
        Class<?> eventType = event.getClass();
        List<EventHandler> eventHandlers = handlers.get(eventType);
        if (eventHandlers != null) {
            List<EventHandler> copy = new ArrayList<>(eventHandlers);
            for (EventHandler handler : copy) {
                handler.accept(event);
            }
        }
    }

    public static void clear() {
        handlers.clear();
    }

    public static Map<Class<?>, List<EventHandler>> getHandlers() {
        return handlers;
    }

    private static class EventHandler implements Consumer<Event> {
        final Object listener;
        final Method method;
        final int priority;

        EventHandler(Object listener, Method method, int priority) {
            this.listener = listener;
            this.method = method;
            this.priority = priority;
        }

        @Override
        public void accept(Event event) {
            if (event.isCanceled()) return;
            try {
                method.invoke(listener, event);
            } catch (Exception e) {
                LogWriter.writeLog(e.getMessage());
                throw new RuntimeException("Failed to invoke event handler on " + listener, e);
            }
        }

        boolean belongsTo(Object listener) {
            return this.listener == listener;
        }
    }
}