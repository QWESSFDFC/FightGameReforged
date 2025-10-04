package cn.gfhnv.game.event;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import cn.gfhnv.game.annotation.SubscribeEvent;

public class EventBus {
    private static Map<Class<?>, List<Consumer<Event>>> handlers=new HashMap<>();

    public static void register(Object listener) {
        // 扫描对象中所有带@SubscribeEvent注解的方法
        // 并添加到handlers映射中
        Method[] methods=listener.getClass().getDeclaredMethods();
        for (Method method:methods){
            method.setAccessible(true);
            if (method.isAnnotationPresent(SubscribeEvent.class)) {
                Parameter[] p=method.getParameters();
                Class<?> eventType=p[0].getType();

                Consumer<Event> handler = event -> {
                    try {
                        method.invoke(listener, event);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to invoke event handler", e);
                    }
                };
                handlers.computeIfAbsent(eventType, k -> new ArrayList<>())
                        .add(handler);
            }

        }
    }
    public static void post(Event event) {
        Class<?> eventType = event.getClass();
        if(handlers.containsKey(eventType)) {
            for(Consumer<Event> handler : handlers.get(eventType)) {
                handler.accept(event);
            }
        }
    }
    public static void clear(){
        handlers.clear();
    }
}

