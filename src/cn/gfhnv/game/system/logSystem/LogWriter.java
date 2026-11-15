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

    /**
     * 把一个异常渲染成<b>完整堆栈</b>文本（含类名、message、每一帧、cause 链）。
     * <p>
     * 2026-11-15 第 80 批（作者要求："出现错误时详细信息回写到 log 文件"）：
     * 以前日志里只有 {@code e.getMessage()} 或 {@code e.toString()}，无 message 的异常
     * （例如只读列表被误用导致的 UOE）在日志里只剩一个类名，根本没法定位。
     *
     * @param error 异常；{@code null} 返回空串
     * @return 完整堆栈文本（多行）
     */
    public static String stackTraceOf(Throwable error) {
        if (error == null) {
            return "";
        }
        java.io.StringWriter stringWriter = new java.io.StringWriter();
        error.printStackTrace(new java.io.PrintWriter(stringWriter));
        return stringWriter.toString();
    }

    /**
     * 把"上下文 + 完整堆栈"写进日志文件（逐行写，保持与 {@link #writeLog(String)} 一致的格式）。
     * <p>
     * 写日志本身失败也**绝不**向外抛：日志系统不能反过来把游戏搞崩。
     *
     * @param context 上下文（例如"【战斗异常】强制收场"）
     * @param error   异常
     */
    public static void writeThrowable(String context, Throwable error) {
        try {
            if (context != null && !context.isEmpty()) {
                writeLog(context);
            }
            String stack = stackTraceOf(error);
            if (stack.isEmpty()) {
                return;
            }
            for (String line : stack.split("\\R")) {
                if (!line.isEmpty()) {
                    writeLog(line);
                }
            }
        } catch (Throwable ignored) {
            // 日志失败不影响游戏
        }
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