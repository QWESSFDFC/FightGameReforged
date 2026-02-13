package cn.gfhnv.game.event;

import cn.gfhnv.game.annotation.SubscribeEvent;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class EventBus {
    private static final Map<Class<?>, List<EventHandler>> handlers = new HashMap<>();

    public static void register(Object listener) {
        Method[] methods = listener.getClass().getDeclaredMethods();
        for (Method method : methods) {
            method.setAccessible(true);
            if (method.isAnnotationPresent(SubscribeEvent.class)) {
                Parameter[] params = method.getParameters();
                if (params.length != 1) {
                    throw new IllegalArgumentException("Event handler method must have exactly one parameter: " + method);
                }
                Class<?> eventType = params[0].getType();
                if (!Event.class.isAssignableFrom(eventType)) {
                    throw new IllegalArgumentException("Event handler parameter must be a subtype of Event: " + method);
                }
                EventHandler handler = new EventHandler(listener, method);
                handlers.computeIfAbsent(eventType, k -> new ArrayList<>()).add(handler);
            }
        }
    }

    public static void unregister(Object listener) {
        for (List<EventHandler> handlerList : handlers.values()) {
            handlerList.removeIf(handler -> handler.belongsTo(listener));
        }
    }

    public static void post(Event event) {
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

        EventHandler(Object listener, Method method) {
            this.listener = listener;
            this.method = method;
        }

        @Override
        public void accept(Event event) {
            try {
                method.invoke(listener, event);
            } catch (Exception e) {
                throw new RuntimeException("Failed to invoke event handler on " + listener, e);
            }
        }

        boolean belongsTo(Object listener) {
            return this.listener == listener;
        }
    }
}