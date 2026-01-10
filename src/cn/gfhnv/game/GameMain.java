package cn.gfhnv.game;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.GameStartEvent;
import cn.gfhnv.game.event.eventListener.EffectEventListener;
import cn.gfhnv.game.event.eventListener.GameStartEventListener;
import cn.gfhnv.game.event.eventListener.PhysicsEventListener;
import cn.gfhnv.game.event.eventListener.WorldTurnEventListener;
import cn.gfhnv.game.mod.OfficialGameContent;
import cn.gfhnv.game.system.mod.ModLoader;
import cn.gfhnv.game.world.World;

/**
 代码开源 MIT　License.---------- @author gfhnv
 */
public class GameMain {
    public static void main(String[] args) {
        World gameWorld=new World();
        EventBus.register( new GameStartEventListener());
        EventBus.register( new EffectEventListener());
        EventBus.register( new WorldTurnEventListener());
        EventBus.register(new PhysicsEventListener());
        ModLoader.modLoaderIntialize();
        World.addMod(new OfficialGameContent());
        EventBus.post(new GameStartEvent(gameWorld));
        System.out.println(World.getModList());
        System.out.println(gameWorld.getEntityList());
         LivingThing lv1=gameWorld.getEntityList().getFirst().transToLivingTing();
         LivingThing lv2=gameWorld.getEntityList().get(1).transToLivingTing();
         lv2.setFightEntity(lv1);
         lv1.setFightEntity(lv2);
         lv2.makeDamage(lv1);
         lv1.makeDamage(lv2);
        System.out.println("2\t"+lv2.getHp());
        System.out.println("1\t"+lv1.getHp());
      }
}
