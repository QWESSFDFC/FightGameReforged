package com.gfhnv.mods;

import cn.gfhnv.game.mod.ModInformation;

public class mainClass extends cn.gfhnv.game.mod.Mod {
    public mainClass(ModInformation modInfo) {
        super("modID", modInfo);
    }

    @Override
    public void invokeWhenLoaded() {
        this.addEntity(new PlayerTwo());
    }
}