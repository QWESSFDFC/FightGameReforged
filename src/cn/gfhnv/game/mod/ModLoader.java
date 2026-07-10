package cn.gfhnv.game.mod;

import cn.gfhnv.game.utils.JSONHelper;
import cn.gfhnv.game.world.World;
import org.json.JSONObject;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
        // 1. 解析 main.json
        ModInformation modInfo = null;
        File mainJsonFile = new File(modFolder, "main.json");
        if (!mainJsonFile.exists() || !mainJsonFile.isFile()) {
            System.err.println("模组 [" + modFolder.getName() + "] 缺少 main.json，跳过");

            return;
        }
        try {
            JSONObject mainJson = JSONHelper.readJSONFile(mainJsonFile);
            modInfo = new ModInformation(
                    mainJson.getString("name"),
                    mainJson.getString("author"),
                    mainJson.getString("description"),
                    mainJson.getString("mainClass"),
                    mainJson.getString("version")
            );
        } catch (Exception e) {
            System.err.println("模组 [" + modFolder.getName() + "] main.json 解析失败: " + e.getMessage());

            return;
        }

        // 2. 检查 code 目录
        File codeDir = new File(modFolder, "code");
        if (!codeDir.exists() || !codeDir.isDirectory()) {
            System.err.println("模组 [" + modFolder.getName() + "] 缺少 code 目录，跳过");
            return;
        }

        // 3. 收集源码文件
        List<JavaFileObject> sourceFiles = new ArrayList<>();
        collectJavaFiles(codeDir, sourceFiles, "");
        if (sourceFiles.isEmpty()) {
            System.err.println("模组 [" + modFolder.getName() + "] code 目录下没有 .java 文件，跳过");
            return;
        }

        // 4. 准备输出目录 bin
        File outputDir = new File(modFolder, "bin");
        if (outputDir.exists()) {
            deleteDirectory(outputDir);
        }
        if (!outputDir.mkdirs()) {
            System.err.println("模组 [" + modFolder.getName() + "] 无法创建 bin 目录");
            return;
        }

        // 5. 编译源码（增加诊断输出）
        boolean compileSuccess = compileJavaFiles(sourceFiles, outputDir);
        if (!compileSuccess) {
            System.err.println("模组 [" + modFolder.getName() + "] 编译失败，跳过加载");
            return;
        }

        // 6. 使用 URLClassLoader 加载类（使用 try-with-resources，但会在块内完成所有加载）
        try (URLClassLoader classLoader = URLClassLoader.newInstance(
                new URL[]{outputDir.toURI().toURL()},
                Thread.currentThread().getContextClassLoader()
        )) {
            // 6.1 加载主类并实例化
            Class<?> mainClass = Class.forName(modInfo.getMainClass(), true, classLoader);
            Object instance = mainClass.getConstructor(ModInformation.class)
                    .newInstance(modInfo);
            if (!(instance instanceof Mod modInstance)) {
                System.err.println("模组主类未继承 Mod: " + modInfo.getMainClass());
                return;
            }

            // 6.2 【核心】预加载 bin 目录下所有其他类，存入 modInstance 的 modClasses 列表
            String mainClassName = modInfo.getMainClass(); // 主类全限定名
            Path binPath = outputDir.toPath();
            Files.walk(binPath)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".class"))
                    .forEach(p -> {
                        // 将路径转换为全限定类名
                        String relativePath = binPath.relativize(p).toString();
                        String className = relativePath.replace(File.separatorChar, '.')
                                .replace(".class", "");
                        // 跳过主类本身（模组类的 class 属性不应该有模组类本身）
                        if (className.equals(mainClassName)) {
                            return;
                        }
                        try {
                            Class<?> clazz = Class.forName(className, true, classLoader);
                            modInstance.addModClass(clazz);
                        } catch (ClassNotFoundException e) {
                            System.err.println("预加载类失败: " + className + " - " + e.getMessage());
                        }
                    });

            // 6.3 将模组添加到世界
            World.addMod(modInstance);
            System.out.println("模组 [" + modInfo.getName() + "] 加载成功！");
        } catch (Exception e) {
            System.err.println("模组 [" + modFolder.getName() + "] 加载失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void collectJavaFiles(File dir, List<JavaFileObject> sources, String pkgPrefix) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                String subPkg = pkgPrefix.isEmpty() ? file.getName() : pkgPrefix + "." + file.getName();
                collectJavaFiles(file, sources, subPkg);
            } else if (file.isFile() && file.getName().endsWith(".java")) {
                try {
                    String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                    String className = pkgPrefix.isEmpty()
                            ? file.getName().replace(".java", "")
                            : pkgPrefix + "." + file.getName().replace(".java", "");
                    sources.add(new JavaSourceCode(className, content));
                } catch (IOException e) {
                    System.err.println("读取源文件失败: " + file.getAbsolutePath());

                    e.printStackTrace();
                }
            }
        }
    }

    private static boolean compileJavaFiles(List<JavaFileObject> sources, File outputDir) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            System.err.println("无法获取 Java 编译器，请确保在 JDK 环境下运行");

            return false;
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
        List<String> options = new ArrayList<>();
        options.add("-d");
        options.add(outputDir.getAbsolutePath());
        options.add("-cp");
        options.add(System.getProperty("java.class.path"));
        JavaCompiler.CompilationTask task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                options,
                null,
                sources
        );
        boolean success = task.call();
        try {
            fileManager.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (!success) {
            System.err.println("编译错误：");


            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                System.err.format("  行 %d, %s: %s%n",
                        diagnostic.getLineNumber(),
                        diagnostic.getSource() != null ? diagnostic.getSource().getName() : "未知源",
                        diagnostic.getMessage(null));
            }
        }
        return success;
    }

    private static void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }
}