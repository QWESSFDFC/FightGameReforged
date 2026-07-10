package com.gfhnv.mods;

import com.gfhnv.mods.test;
import cn.gfhnv.game.mod.ModInformation;

public class mainClass extends cn.gfhnv.game.mod.Mod {
    public mainClass(ModInformation modInfo) {
        super("modID", modInfo);
    }

    @Override
    public void invokeWhenLoaded() {
        System.out.println("Mod Loaded!!!!!!");
        test.print();//可以调用其他类方法
        //重写此方法,添加实体和物品等,这是为了保证其他类被编译

    }
}