package cn.gfhnv.game;

import cn.gfhnv.game.system.logSystem.LogWriter;

import java.sql.Time;
import java.time.LocalTime;

/**
 *
 * 代码开源 MIT　License.---------- @author gfhnv
 */
public class GameStarter {
    public static void main(String[] args) {
        System.out.println("作者gfhnv.本游戏纯属娱乐.代码开源()2025.9.1");
        System.out.println("此项目Github仓库:https://github.com/QWESSFDFC/FightGameReforged");
        Time currentTime = Time.valueOf(LocalTime.now());
        LogWriter.writeLog(currentTime.toString());
        GameMain.main(args);
    }
}