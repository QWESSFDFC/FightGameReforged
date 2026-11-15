package cn.gfhnv.game.entity;

import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.utils.ConsoleColor;

/**
 * 实体的<b>显示文本</b>：名字的几种拼法（带阵营 / 带短标识）与攻击行的打印。
 * <p>
 * <b>为什么单独一个类</b>（2026-11-15 第 63 批，审计 P2 的"抽表现层"）：
 * 这四个方法里有三个是<b>纯字符串拼装</b>、一个是<b>唯一会往控制台打印的实体侧代码</b>。
 * 它们留在 {@link LivingThing} 上，等于让"数据 + 规则"的类去管"颜色与排版" ——
 * 而且 {@link cn.gfhnv.game.system.thinkingSystem.ThinkingControllerAI} 还在用
 * {@link #nameWithSide} 拼 AI 提示词，说明它是<b>展示层</b>而不是日志层
 * （所以本类叫 {@code EntityDisplay} 而不是 {@code CombatLogFormatter}）。
 * <p>
 * {@link LivingThing} 上的同名方法保留成<b>薄转发</b>：调用点（8 个文件 / 十余处）一个都没改，
 * 但实体类从此<b>没有一处 {@code System.out}</b>、也不再 import {@link ConsoleColor}。
 *
 * @author AI（DeepSeek）生成
 */
public final class EntityDisplay {

    /**
     * 工具类，不允许实例化。
     */
    private EntityDisplay() {
    }

    /**
     * 名字后面跟上阵营，例如 {@code 张三（我方）}、{@code 火劫卫（敌方）}。
     * <p>
     * 战斗日志里同名生物经常分属两边（典型：双方各有一只【残破容器】），
     * 光看名字分不出谁是谁，所以攻击行这类输出统一带上阵营 ——
     * 判据借 {@link Fight#sideNameOf(LivingThing)}，与回合头同一套。
     * 阵营用灰色，免得抢了伤害数字和技能名的注意力。
     * <p>
     * 不在战斗里（选人界面之类）时只返回名字。
     *
     * @param livingThing 实体
     * @return 带阵营的名字
     */
    public static String nameWithSide(LivingThing livingThing) {
        String side = sideName(livingThing);
        return side.isEmpty() ? livingThing.getName() : livingThing.getName() + "（" + ConsoleColor.dim(side) + "）";
    }

    /**
     * 名字后面跟上短标识，例如 {@code 至黑之剑，盗火行者#0a1b2c}。
     * <p>
     * <b>本方法不着色</b>：它给"整行包一个颜色"的地方用（回合头把整行包在青色里），
     * 自带颜色的话内层的复位会把整行青色掐断。按段着色的战斗日志行请用
     * {@link #nameWithUuidAndSide}。
     *
     * @param livingThing 实体
     * @return 带短标识的名字
     */
    public static String nameWithUuid(LivingThing livingThing) {
        return livingThing.getName() + "#" + livingThing.getShortUuid();
    }

    /**
     * 名字后面跟上短标识与阵营，例如 {@code 至黑之剑，盗火行者#0a1b2c（我方）}。
     * <p>
     * 颜色照 {@code 60-COMBAT.md} §5.5.2：短标识青、阵营灰、名字本身不上色。
     *
     * @param livingThing 实体
     * @return 带短标识与阵营的名字
     */
    public static String nameWithUuidAndSide(LivingThing livingThing) {
        String side = sideName(livingThing);
        return livingThing.getName() + shortUuidTag(livingThing)
                + (side.isEmpty() ? "" : "（" + ConsoleColor.dim(side) + "）");
    }

    /**
     * 打印一行攻击日志：{@code A#短标识（我方）攻击了B#短标识（敌方）  -伤害  → HP 当前/上限  【技能名】}。
     * <p>
     * 伤害标红、剩余 HP 标灰、技能名标青、短标识标青（与回合头同色）、阵营标灰
     * （着色能否生效见 {@link ConsoleColor}；不支持 ANSI 的控制台会自动退化成纯文本）。
     *
     * @param attacker 攻击者
     * @param attacked 被打的目标
     * @param skill    使用的技能；可为 {@code null}
     * @param damage   <b>应该造成的伤害</b>（伤害公式算出来的值，可能大于目标剩余血量）
     */
    public static void printAttackLine(LivingThing attacker, LivingThing attacked, Skill skill, long damage,
                                       boolean critical) {
        System.out.println(attackLine(attacker, attacked, skill, damage, critical));
    }

    /**
     * 拼一行攻击日志（不打印，给自测断言"有没有（暴击）标记"用）。
     *
     * @param attacker 攻击者
     * @param attacked 被打的目标
     * @param skill    使用的技能；可为 {@code null}
     * @param damage   应该造成的伤害
     * @param critical 这一击有没有触发暴击
     * @return 整行文本
     */
    public static String attackLine(LivingThing attacker, LivingThing attacked, Skill skill, long damage,
                                    boolean critical) {
        StringBuilder line = new StringBuilder(nameWithUuidAndSide(attacker)).append("攻击了")
                .append(nameWithUuidAndSide(attacked));
        line.append("  ").append(ConsoleColor.red("-" + damage));
        if (critical) {
            line.append(ConsoleColor.yellow("（暴击）"));
        }
        line.append("  ").append(ConsoleColor.dim(
                "→ HP " + attacked.getHp() + "/" + attacked.getHpMax()));
        if (skill != null && skill.getName() != null) {
            line.append("  ").append(ConsoleColor.cyan("【" + skill.getName() + "】"));
        }
        return line.toString();
    }

    /**
     * @param livingThing 实体
     * @return {@code #短标识}，按回合头的颜色上色（青）；关闭着色时原样返回
     */
    private static String shortUuidTag(LivingThing livingThing) {
        return ConsoleColor.cyan("#" + livingThing.getShortUuid());
    }

    /**
     * @param livingThing 实体
     * @return 这一侧的显示名（{@code 我方} / {@code 敌方}）；不在战斗里时返回空串
     */
    private static String sideName(LivingThing livingThing) {
        Fight fight = livingThing.getParticipateFight();
        return fight == null ? "" : fight.sideNameOf(livingThing);
    }
}
