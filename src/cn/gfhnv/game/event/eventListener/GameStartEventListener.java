package cn.gfhnv.game.event.eventListener;
import java.util.List;
import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.event.GameStartEvent;
import cn.gfhnv.game.mod.Mod;
import cn.gfhnv.game.world.World;
public class GameStartEventListener {
    @SubscribeEvent
    public void load(GameStartEvent ev){
        if (ev.getMods()==null){
            System.out.println("NULL.NO MOD.");
            return;
        }
      List<Mod> mods = ev.getMods();
      World world=ev.getWorld();
        for(Mod m:mods){
            if (m==null){
                return;
            }       
            m.registerItself(world);
        }
    }
}
