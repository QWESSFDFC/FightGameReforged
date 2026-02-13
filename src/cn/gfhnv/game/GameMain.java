package cn.gfhnv.game;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entity.Player;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.FightStartEvent;
import cn.gfhnv.game.event.GameStartEvent;
import cn.gfhnv.game.event.eventListener.EffectEventListener;
import cn.gfhnv.game.event.eventListener.GameStartEventListener;
import cn.gfhnv.game.event.eventListener.PhysicsEventListener;
import cn.gfhnv.game.event.eventListener.WorldTurnEventListener;
import cn.gfhnv.game.mod.ModLoader;
import cn.gfhnv.game.mod.officialModStuff.OfficialGameContent;
import cn.gfhnv.game.mod.officialModStuff.customEntity.InsectBoss;
import cn.gfhnv.game.mod.officialModStuff.customEntity.PlayerOne;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.fight.FightStartEventListener;
import cn.gfhnv.game.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * 代码开源 MIT　License.---------- @author gfhnv
 */
public class GameMain {
    public static void gameInitialize() {
        World.addMod(new OfficialGameContent());
        ModLoader.modLoaderInitialize();
        EventBus.register(new GameStartEventListener());
        EventBus.register(new EffectEventListener());
        EventBus.register(new WorldTurnEventListener());
        EventBus.register(new PhysicsEventListener());
        EventBus.register(new FightStartEventListener());
        EventBus.post(new GameStartEvent());
    }

    public static void main(String[] args) {
        gameInitialize();
    }
}
