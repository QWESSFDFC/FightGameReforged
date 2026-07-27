package cn.gfhnv.game.system.configLoadingSystem;

import java.io.File;
import java.util.Objects;

public class ConfigLoader {//只加载游戏本身设置(目前只有tag设置存在config.json中).模组的自己写加载方法,
    public static final File CONFIF_DIR = new File("./config");

    public static void loadConfig() {
        if (!CONFIF_DIR.exists()) {
            CONFIF_DIR.mkdir();
            return;
        }
        if (CONFIF_DIR.exists() & CONFIF_DIR.isFile()) {
            CONFIF_DIR.delete();
            loadConfig();
            return;
        }
        if (Objects.requireNonNull(CONFIF_DIR.listFiles()).length == 0) {
            setDefaultConfig();
            return;
        }


    }

    public static void setDefaultConfig() {

    }
}
