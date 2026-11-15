package cn.gfhnv.debug_tools;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEntity.monsters.CommonInsect;
import cn.gfhnv.game.officialStuff.customEntity.players.PlayerOne;
import cn.gfhnv.game.system.fight.Fight;

import java.util.ArrayList;
import java.util.List;

import static cn.gfhnv.debug_tools.TestKit.check;
import static cn.gfhnv.debug_tools.TestKit.section;

/**
 * 自测：**显示**。
 * <p>
 * 主题：战斗日志的着色（回合头整行 vs 按段）。
 * <p>
 * 由 {@code TestCommandSystem#main} 按顺序调用 {@code CombatLogColorTests#runAll()}。
 */
public final class CombatLogColorTests {

    private CombatLogColorTests() {
    }

    /**
     * 战斗日志的颜色约定（{@code 60-COMBAT.md} §5.5.2）。
     * <p>
     * 用户 2026-10-03 实测："攻击时的短 uuid 没有颜色（应该和回合头一样）"。
     * 回合头是<b>整行</b>青色（短标识因此是青的），而攻击行 / 【侵蚀】行 / 回血行是<b>按段</b>着色的，
     * 上一轮加 {@code #短标识} 时只加了文字、没上色。这里钉住三件事：
     * <ol>
     *     <li>按段着色的那一份（{@code LivingThing#getNameWithUuidAndSide()}）里
     *     <b>短标识是青的</b>（与回合头同色）、<b>阵营还是灰的</b>（§5.5.2 第 4 点的理由）、
     *     名字本身不上色（与其它日志行一致，不引入第三种口径）；</li>
     *     <li>整行着色的那一份（{@code LivingThing#getNameWithUuid()}）<b>不带任何转义</b> ——
     *     回合头把它整行包在青色里，内层若自带复位，会把后半行"（我方）的回合 ───"的青色掐断；</li>
     *     <li>关掉着色时两者都退化成纯文本（{@code > log.txt} 里不许混进看不见的转义序列）。</li>
     * </ol>
     * 做完把着色开关恢复原样，不给后面的用例留全局状态。
     */
    private static void testCombatLogColors() {
        section("战斗日志颜色（短标识与回合头同色）");
        boolean colorBefore = cn.gfhnv.game.utils.ConsoleColor.isEnabled();
        LivingThing hero = new PlayerOne(125).copy();
        LivingThing bug = new CommonInsect(150L).copy();
        try {
            Fight fight = new Fight(new ArrayList<>(List.of(bug)), new ArrayList<>(),
                    new ArrayList<>(List.of(hero)));
            hero.setParticipateFight(fight);
            bug.setParticipateFight(fight);

            String tag = "#" + hero.getShortUuid();
            cn.gfhnv.game.utils.ConsoleColor.setEnabled(true);
            String colored = hero.getNameWithUuidAndSide();
            check("颜色：攻击行那一路（按段着色）的短标识是<b>青</b>色、与回合头同色 —— "
                            + colored.replace("\u001B", "<ESC>"),
                    colored.contains(cn.gfhnv.game.utils.ConsoleColor.CYAN + tag
                            + cn.gfhnv.game.utils.ConsoleColor.RESET)
                            && colored.startsWith(hero.getName()));
            check("颜色：阵营照旧是灰色（§5.5.2 第 4 点：免得抢伤害数字与技能名的注意力），名字本身不上色",
                    colored.contains("（" + cn.gfhnv.game.utils.ConsoleColor.DIM + "我方"
                            + cn.gfhnv.game.utils.ConsoleColor.RESET + "）")
                            && !colored.startsWith(cn.gfhnv.game.utils.ConsoleColor.CYAN));
            String plainForHeader = hero.getNameWithUuid();
            check("颜色：整行着色的那一份（回合头用它）不带任何转义，否则会把整行青色掐断 —— "
                            + plainForHeader,
                    plainForHeader.equals(hero.getName() + tag)
                            && plainForHeader.indexOf('\u001B') < 0);

            cn.gfhnv.game.utils.ConsoleColor.setEnabled(false);
            check("颜色：关掉着色后两者都退化成纯文本（重定向到文件时不混进转义序列）",
                    hero.getNameWithUuidAndSide().equals(hero.getName() + tag + "（我方）")
                            && hero.getNameWithUuid().equals(hero.getName() + tag));
        } finally {
            cn.gfhnv.game.utils.ConsoleColor.setEnabled(colorBefore);
        }
    }

    /**
     * 跑本主题的全部用例（顺序 = 下面列的顺序）。
     */
    public static void runAll() throws Exception {
        testCombatLogColors();
    }
}
