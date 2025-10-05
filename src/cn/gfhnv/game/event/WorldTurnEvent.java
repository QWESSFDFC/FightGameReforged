package cn.gfhnv.game.event;
import cn.gfhnv.game.world.World;
public class WorldTurnEvent extends Event{
    private final World world;
    public WorldTurnEvent(World world){this.world=world;}
    public World getWorld() {
        return world;
    }
}
