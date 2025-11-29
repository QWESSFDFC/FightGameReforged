package cn.gfhnv.game.event;
import cn.gfhnv.game.mod.Mod;
import cn.gfhnv.game.world.World;

import java.util.List;
public class GameStartEvent extends Event {
    private List<Mod> mods;
    private World world;
    public GameStartEvent(){}
    public void setMods(List<Mod> mods) {
        this.mods = mods;
    }
    public World getWorld() {
        return world;
    }
    public void setWorld(World world) {
        this.world = world;
    }
    public GameStartEvent(World world) {
        this.mods = World.getModList();
        this.world = world;
    }
    public List<Mod> getMods() {
        if (mods==null) {
            return new java.util.ArrayList<>();
        }
        return mods;
    }
    public GameStartEvent(List<Mod> mods){
        this.mods=mods;
    }
}
