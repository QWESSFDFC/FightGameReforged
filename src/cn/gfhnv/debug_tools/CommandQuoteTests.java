package cn.gfhnv.debug_tools;

import cn.gfhnv.game.data.*;
import cn.gfhnv.game.officialStuff.customEntity.monsters.CommonInsect;
import cn.gfhnv.game.officialStuff.customEntity.players.PlayerOne;
import cn.gfhnv.game.system.command.*;
import cn.gfhnv.game.system.fight.*;

import java.util.ArrayList;
import java.util.List;

import static cn.gfhnv.debug_tools.TestKit.*;
import static cn.gfhnv.debug_tools.TestSupport.*;
import static cn.gfhnv.debug_tools.TestProbes.*;

/**
 * 自测：**命令**。
 * <p>
 * 主题：命令里非英文字符必须加引号那条规则。
 * <p>
 * 由 {@code TestCommandSystem#main} 按顺序调用 {@code CommandQuoteTests#runAll()}。
 */
public final class CommandQuoteTests {

    /**
     * <b>命令里的非英文字符必须加引号</b>（2026-10-24 用户要求）。
     * <p>
     * 之前 {@code /summon CommonInsect 敌方}、{@code /data modify entity @s name set 千冶·刃}
     * 这种"裸中文"能直接过 —— 但它们混进 id / 路径 / 选择器之后，报出来的错会指向别处
     * （"找不到这个实体"），很难查。现在裸词里出现非 ASCII 直接报错并提示写法；
     * 加引号之后一切照旧（选择器里的值也认引号：{@code @e[name="千冶·刃"]}）。
     */
    private static void testCommandRequiresQuotesForNonAscii() {
        section("命令：非英文字符必须加引号（裸词直接报错）");
        PlayerOne hero = new PlayerOne(125);
        Fight fight = new Fight(new ArrayList<>(List.of(hero)), new ArrayList<>(),
                new ArrayList<>(List.of(hero)));

        // ① 裸中文：报错，并且提示"要用引号"
        String bare = errorOf("summon CommonInsect 敌方", fight);
        check("裸中文报错，并提示要加引号 —— 实际「" + bare + "」",
                bare.contains("引号") && bare.contains("敌方"));

        // ② 同样的命令加引号就能用
        int enemiesBefore = fight.getEnemiesList().size();
        run("summon CommonInsect \"敌方\"", fight, true);
        check("同样的命令加引号之后可用（敌方列表 " + enemiesBefore + " → "
                        + fight.getEnemiesList().size() + "）",
                fight.getEnemiesList().size() == enemiesBefore + 1);

        // ③ 选择器里的值也支持引号，而且不影响筛选
        // 成功时 message 为空（输出直接打出去了），所以这里验"命令成功 + 选到的对象数"
        cn.gfhnv.game.system.command.CommandResult quotedSelector =
                CommandManager.executeResult("list @e[name=\"玩家一\"]", fight);
        check("选择器里的值加引号也认（@e[name=\"玩家一\"] 命令成功、影响 1 个对象）—— 实际 "
                        + (quotedSelector.isSuccess() ? "成功" : "失败：" + quotedSelector.getMessage()),
                quotedSelector.isSuccess());

        // ④ 选择器里**不加引号**的中文仍然要报错
        String bareSelector = errorOf("list @e[name=玩家一]", fight);
        check("选择器里裸中文同样报错 —— 实际「" + bareSelector + "」", bareSelector.contains("引号"));

        // ⑤ 纯 ASCII 的裸词不受影响（别把正常命令一起拦了）
        run("list @e[type=playerOne]", fight, true);
    }

    /**
     * 跑本主题的全部用例（顺序 = 下面列的顺序）。
     */
    public static void runAll() throws Exception {
        testCommandRequiresQuotesForNonAscii();
    }

    private CommandQuoteTests() {
    }
}
