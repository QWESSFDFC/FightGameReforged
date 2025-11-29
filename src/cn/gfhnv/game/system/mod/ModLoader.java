package cn.gfhnv.game.system.mod;
import cn.gfhnv.game.mod.Mod;
import cn.gfhnv.game.world.World;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
public class ModLoader {
    public static void modLoaderIntialize(){
        List<Mod> preToLoadMods =new ArrayList<>();
        File modDir=new File("./mods");
        if (!modDir.exists()){
            boolean s= modDir.mkdir();
            System.out.println("mods目录创建: " + (s ? "成功" : "失败"));
        }
    }
}
