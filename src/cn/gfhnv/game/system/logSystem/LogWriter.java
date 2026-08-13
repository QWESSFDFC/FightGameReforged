package cn.gfhnv.game.system.logSystem;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * AI写的
 */
public class LogWriter {

    private static final Path LOG_DIR = Paths.get("./logs");
    private static final Path LATEST_LOG = LOG_DIR.resolve("latest.log");
    private static final long MAX_SIZE = 1024 * 1024 * 10;

    static {
        try {
            if (!Files.exists(LOG_DIR)) {
                Files.createDirectory(LOG_DIR);
            }

            if (Files.exists(LATEST_LOG)) {
                if (Files.size(LATEST_LOG) >= MAX_SIZE) {
                    LATEST_LOG.toFile().delete();
                } else {
                    archiveLatestLog();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("初始化日志系统失败", e);
        }
    }

    private static void archiveLatestLog() throws IOException {
        FileTime lastModified = Files.getLastModifiedTime(LATEST_LOG);
        LocalDateTime modifyTime = LocalDateTime.ofInstant(
                lastModified.toInstant(), ZoneId.systemDefault()
        );
        String timestamp = modifyTime.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path archivedLog = LOG_DIR.resolve(timestamp + ".log");
        if (Files.exists(archivedLog)) {
            int counter = 1;
            while (Files.exists(LOG_DIR.resolve(timestamp + "_" + counter + ".log"))) {
                counter++;
            }
            archivedLog = LOG_DIR.resolve(timestamp + "_" + counter + ".log");
        }
        Files.move(LATEST_LOG, archivedLog, StandardCopyOption.REPLACE_EXISTING);
    }

    public static void writeLog(String content) {
        try {
            String line = content + System.lineSeparator();
            Files.writeString(LATEST_LOG, line,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}