package cn.gfhnv.debug_tools;

import cn.gfhnv.game.data.*;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.command.*;
import cn.gfhnv.game.system.fight.*;
import cn.gfhnv.game.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cn.gfhnv.debug_tools.TestKit.*;
import static cn.gfhnv.debug_tools.TestSupport.*;
import static cn.gfhnv.debug_tools.TestProbes.*;

/**
 * 自测：**显示**。
 * <p>
 * 主题：控制台显示字符串的编码安全（GBK 可编码）。
 * <p>
 * 由 {@code TestCommandSystem#main} 按顺序调用 {@code DisplayTests#runAll()}。
 */
public final class DisplayTests {

    /**
     * {@code /data get} 的<b>引用呈现</b>：根对象全量、被引用的实体只剩 {@code uuid}。
     * <p>
     * 起因是用户实测：{@code /execute as @e[type=FlameReaver] run data get entity @s} 打出来
     * {@code cloudOfDeathSummons:[{…一整个容器的完整 NBT…}]}、{@code lastAttacker:{…又套一层
     * 卡厄斯兰那的完整 NBT，含 skills:[…] 与 extraTurns:7…}} —— <b>引用套引用</b>，一条命令 5000+ 字符。
     * 那些被引用的实体自己有完整的字段表，想细看把它们当根再查一次就行
     * （{@code /data get entity <它> …}），没必要在别人的 dump 里再抄一遍。
     * <p>
     * 这里用探针摆两个形状：普通引用，以及<b>指回自己</b>的引用（后者同时证明这条规则
     * 在类型上就短路了、不递归 —— 真去递归会直接栈溢出）。
     * <p>
     * ⚠️ 只读导出变了，<b>写回没变</b>：{@code merge} / {@code modify} 走的是活对象，
     * 键名与语义一个都没动（这条由上面 {@code testDataCommand} 与 {@code testDataModify} 兜着）。
     */
    /**
     * <b>显示安全</b>：玩家可见的字符串必须能用 <b>GBK</b> 编出来。
     * <p>
     * <b>为什么</b>（2026-10-24 用户实跑反馈）：角色列表里显示成「千冶?刃」—— 名字里用了
     * <b>U+2022（•）</b>，那个字符**不在 GBK 字符集里**；JVM 的 stdout 是 GBK 时只能输出 {@code '?'}，
     * 连 {@code chcp 65001} 都救不了（编码是 JVM 启动时定的）。
     * <p>
     * 这一条扫<b>配置应用之后</b>的实体与技能（名字 + 描述），把"混进 GBK 编不出来的字符"拦在自测里。
     * 报错时用 {@code U+XXXX} 写坏字符 —— 直接打印它自己也会变成 {@code '?'}。
     */
    private static void testDisplayStringsAreConsoleSafe() {
        section("显示安全：实体/技能的名字与描述必须能用 GBK 编码（否则控制台显示成 ?）");

        java.nio.charset.CharsetEncoder encoder = java.nio.charset.Charset.forName("GBK").newEncoder();
        List<String> bad = new ArrayList<>();
        for (LivingThing template : World.getLivingEntityList()) {
            collectNonGbk(bad, encoder, "实体名 " + template.getId(), template.getName());
            collectNonGbk(bad, encoder, "实体描述 " + template.getId(), template.getDescription());
        }
        for (Map.Entry<String, Skill> entry
                : cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.all().entrySet()) {
            collectNonGbk(bad, encoder, "技能名 " + entry.getKey(), entry.getValue().getName());
            collectNonGbk(bad, encoder, "技能描述 " + entry.getKey(), entry.getValue().getDescription());
        }
        check("显示安全：实体与技能的名字/描述都能用 GBK 编码（非 GBK 字符在 GBK 控制台会变成 ?）—— 有问题的 "
                + bad, bad.isEmpty());
    }

    /**
     * 跑本主题的全部用例（顺序 = 下面列的顺序）。
     */
    public static void runAll() throws Exception {
        testDisplayStringsAreConsoleSafe();
    }

    private DisplayTests() {
    }
}
