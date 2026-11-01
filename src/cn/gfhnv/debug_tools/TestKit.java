package cn.gfhnv.debug_tools;

import cn.gfhnv.game.data.*;
import cn.gfhnv.game.system.command.*;
import cn.gfhnv.game.system.fight.*;

import static cn.gfhnv.debug_tools.TestSupport.*;
import static cn.gfhnv.debug_tools.TestProbes.*;

/**
 * 自测的**断言底座**：通过/失败计数、{@code check}/{@code section}、命令跑批工具、UTF-8 输出。
 * <p>
 * 规则（2026-10-25 起）：**不同内容的测试写进不同的类**（一个类一个主题），
 * 共用这一份断言底座；不要再把所有自测往一个巨型类里塞。
 */
public final class TestKit {

    /**
     * 抓回来的输出最多留这么多行（防止外层空转把内存打爆；断言只看前面这些行）。
     */
    static final int MAX_CAPTURED_LINES = 200;

    /**
     * 断言失败的条数。
     */
    static int failures = 0;

    /**
     * 断言成功的条数。
     */
    static int passes = 0;

    /**
     * 把标准输出切成 UTF-8。
     * <p>
     * Windows 的 PowerShell 5 控制台默认是 GBK（代码页 936），直接打印中文会乱码。
     * 这里不改系统设置，只在进程内换掉 {@code System.out} 的编码。
     * 想彻底避免乱码，也可以在运行前执行 {@code chcp 65001}。
     */
    static void useUtf8Output() {
        try {
            java.io.PrintStream utf8 = new java.io.PrintStream(
                    new java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8");
            System.setOut(utf8);
        } catch (java.io.UnsupportedEncodingException e) {
            System.out.println("[提示] 无法把输出切换为 UTF-8，中文可能显示为乱码：" + e.getMessage());
        }
    }

    /**
     * 执行一条命令并检查「成功/失败」是否符合预期。
     *
     * @param command  命令文本（可带前缀）
     * @param expected 期望是否成功
     */
    static void run(String command, boolean expected) {
        run(command, null, expected);
    }

    /**
     * 同上，但显式指定命令所属的战斗。
     * <p>
     * {@code fight} 传 {@code null} 时沿用 {@link CommandSource} 里登记的那一场
     * —— 所以"当前战斗"是有粘性的：想测"没有战斗"的情况，先
     * {@link CommandManager#clearCurrentFight()}。
     *
     * @param command  命令文本（可带前缀）
     * @param fight    当前战斗；{@code null} 表示沿用已登记的那一场
     * @param expected 期望是否成功
     */
    static void run(String command, Fight fight, boolean expected) {
        CommandResult result = CommandManager.executeResult(command, fight);
        System.out.println("  >>> " + command + "  =>  " + result);
        if (result.isSuccess() == expected) {
            passes++;
        } else {
            fail("命令「" + command + "」期望 " + (expected ? "成功" : "失败") + "，实际相反：" + result);
        }
    }

    /**
     * 跑一条命令并返回它的错误文本（成功时返回空串），顺便打印一行。
     *
     * @param command 命令文本
     * @return 错误文本
     */
    static String errorOf(String command) {
        return errorOf(command, null);
    }

    /**
     * 同上，但显式指定命令所属的战斗。
     *
     * @param command 命令文本
     * @param fight   当前战斗；{@code null} 表示沿用已登记的那一场
     * @return 错误文本
     */
    static String errorOf(String command, Fight fight) {
        CommandResult result = CommandManager.executeResult(command, fight);
        System.out.println("  >>> " + command + "  =>  " + result);
        return result.getError() == null ? "" : result.getError().getMessage();
    }

    /**
     * 断言某个操作会抛出 {@link cn.gfhnv.game.system.command.CommandSyntaxException}。
     *
     * @param message 断言说明
     * @param action  操作
     */
    static void expectSyntaxError(String message, ThrowingAction action) {
        try {
            action.run();
            fail(message + "（但没有抛出异常）");
        } catch (Exception e) {
            System.out.println("  >>> 预期报错：" + e.getMessage());
            passes++;
        }
    }

    /**
     * 断言。
     *
     * @param message 断言说明
     * @param ok      是否成立
     */
    static void check(String message, boolean ok) {
        if (ok) {
            passes++;
            System.out.println("  [OK] " + message);
        } else {
            fail(message);
        }
    }

    /**
     * 记录一次失败。
     *
     * @param message 失败说明
     */
    static void fail(String message) {
        failures++;
        System.out.println("  [FAIL] " + message);
        // 再打一遍到 stderr：有些用例会临时劫持 System.out 去捕获游戏输出，
        // 落在捕获区里的失败会跟着那段输出一起被丢掉（日志里一条 [FAIL] 都没有，
        // 汇总却说"失败 N 条"—— 这种静默失败比失败本身更坑）。stderr 不会被那套替换。
        System.err.println("  [FAIL] " + message);
    }

    /**
     * 打印一个小节标题。
     *
     * @param title 标题
     */
    static void section(String title) {
        System.out.println();
        System.out.println("-------- " + title + " --------");
    }

    /**
     * 打印总结并按失败数决定退出码（CI / 脚本看这个）。
     */
    static void summary() {
        System.out.println("========== 自测结束：通过 " + passes + " 条，失败 " + failures + " 条 ==========");
        if (failures > 0) {
            System.exit(1);
        }
        System.exit(0);
    }

    private TestKit() {
    }
}
