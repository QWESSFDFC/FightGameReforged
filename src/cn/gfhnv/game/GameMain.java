package cn.gfhnv.game;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.GameStartEvent;
import cn.gfhnv.game.event.eventListener.EffectEventListener;
import cn.gfhnv.game.event.eventListener.GameStartEventListener;
import cn.gfhnv.game.event.eventListener.PhysicsEventListener;
import cn.gfhnv.game.event.eventListener.WorldTurnEventListener;
import cn.gfhnv.game.mod.officialModStuff.OfficialGameContent;
import cn.gfhnv.game.system.mod.ModLoader;
import cn.gfhnv.game.world.World;

/**
 代码开源 MIT　License.---------- @author gfhnv
 */
public class GameMain {
    public static void main(String[] args) {
        World gameWorld=new World();
        World.addMod(new OfficialGameContent());
        ModLoader.modLoaderInitialize();
        EventBus.register( new GameStartEventListener());
        EventBus.register( new EffectEventListener());
        EventBus.register( new WorldTurnEventListener());
        EventBus.register(new PhysicsEventListener());
        EventBus.post(new GameStartEvent(gameWorld));
        System.out.println(World.getModList());
        System.out.println(gameWorld.getEntityList());

      }
}
