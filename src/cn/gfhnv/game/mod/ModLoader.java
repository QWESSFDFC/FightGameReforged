package cn.gfhnv.game.mod;

import cn.gfhnv.game.utils.JSONHelper;
import org.json.JSONObject;

import javax.tools.JavaCompiler;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

public class ModLoader {

    public static void modLoaderInitialize() {
        System.out.println("正在初始化模组加载器...");
        File modDir = new File("./mods");
        if (!modDir.exists()) {
            boolean s = modDir.mkdir();
            System.out.println("mods目录创建: " + (s ? "成功" : "失败"));
        }
        File[] modFiles = modDir.listFiles();
        if (modFiles == null) {
            System.out.println("mods目录为空或无法访问");
            return;
        }
        for (File modFile : modFiles) {
            try {
                if (modFile.isDirectory()) {
                    // 只处理文件夹格式的模组
                    loadFolderModItself(modFile);

                } else {
                    System.out.println("跳过不支持的文件: " + modFile.getName());
                }
            } catch (Exception e) {
                System.err.println("加载模组失败 [" + modFile.getName() + "]: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private static void loadFolderModItself(File modFolder) {
        System.out.println("Loading " + modFolder.getName());

    }
}
