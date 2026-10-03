package cn.gfhnv.game.eventListener;

import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.event.GameStartEvent;
import cn.gfhnv.game.mod.Mod;
import cn.gfhnv.game.system.configLoadingSystem.ConfigLoader;

import java.util.List;

public class GameStartEventListener {
    @SubscribeEvent
    public void load(GameStartEvent ev) {
        if (ev.getMods() == null) {
            System.out.println("NULL.NO MOD.");
            return;
        }
        List<Mod> mods = ev.getMods();
        for (Mod m : mods) {
            if (m == null) {
                return;
            }
            // 配置必须在 invokeWhenLoaded() **之前**读：模组要在"注册内容"之前就能拿到配置
            // （例如"注册几个技能"都可能由配置决定）。
            // loadModData 自己把异常与 Throwable 都吞成一行日志 —— 一个模组的配置写错，
            // 不能让后面模组全不加载。
            ConfigLoader.loadModData(m);
            m.invokeWhenLoaded();
            m.registerItself();
        }
    }
}
