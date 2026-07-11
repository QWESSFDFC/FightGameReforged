package cn.gfhnv.game.eventListener;

import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.event.GameStartEvent;
import cn.gfhnv.game.mod.Mod;

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
            m.invokeWhenLoaded();
            m.registerItself();
        }
    }
}
