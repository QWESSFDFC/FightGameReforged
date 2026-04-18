
package cn.gfhnv.game.system.save;

import cn.gfhnv.game.logSystem.LogWriter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class SaveSystem {
    private static final Path SAVE_DIR = Paths.get("./save");//save文件夹内存放save1/save2.....
    static {
        if (!Files.exists(SAVE_DIR)) {
            try {
                Files.createDirectory(SAVE_DIR);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if (Files.exists(SAVE_DIR)) {
            for (File file : Objects.requireNonNull(SAVE_DIR.toFile().listFiles())) {
                if (isASave(file)) {
                    //加载
                    LogWriter.writeLog("可用存档 " + file.getName());
                }
            }


        }
    }
    private static boolean isASave(File file) {
        if (!Files.isDirectory(file.toPath())) return false;
        if (!Files.isReadable(file.toPath())) return false;
        if (!Files.isWritable(file.toPath())) return false;
        boolean a=false;
        for (File file1: Objects.requireNonNull(file.listFiles())) {
            if (file1.getName().endsWith(".json")&&file1.getName().startsWith("save")) {a=true;break;}
        }
        return file.getName().startsWith("save")&&a;//应该名为save1/save2/......
    }
}