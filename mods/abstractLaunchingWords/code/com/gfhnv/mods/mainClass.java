package com.gfhnv.mods;

import cn.gfhnv.game.mod.ModInformation;

public class mainClass extends cn.gfhnv.game.mod.Mod {
    public mainClass(ModInformation modInfo) {
        super("CustomLaunchingWords", modInfo);
    }

    @Override
    public void invokeWhenLoaded() {
      System.out.println("原神牛逼！");  
      System.out.println("我是终将升起的烈阳！");
      System.out.println("Isolation!");
    }
}