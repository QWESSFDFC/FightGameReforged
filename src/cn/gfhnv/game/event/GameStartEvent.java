package cn.gfhnv.game.event;
import cn.gfhnv.game.mod.Mod;
import cn.gfhnv.game.world.World;

import java.util.ArrayList;
import java.util.List;
public class GameStartEvent extends Event {
    private List<Mod> mods;

    public GameStartEvent(){
        this.mods = World.getModList();
    }
    public void setMods(List<Mod> mods) {
        this.mods = mods;
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
