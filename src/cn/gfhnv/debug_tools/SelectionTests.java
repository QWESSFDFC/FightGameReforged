package cn.gfhnv.debug_tools;

import cn.gfhnv.game.data.*;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.system.command.*;
import cn.gfhnv.game.system.fight.*;
import cn.gfhnv.game.world.World;

import java.util.ArrayList;
import java.util.List;

import static cn.gfhnv.debug_tools.TestKit.*;
import static cn.gfhnv.debug_tools.TestSupport.*;
import static cn.gfhnv.debug_tools.TestProbes.*;

/**
 * 自测：**流程**。
 * <p>
 * 主题：选人 / 选目标 / 开局那一套交互流程。
 * <p>
 * 由 {@code TestCommandSystem#main} 按顺序调用 {@code SelectionTests#runAll()}。
 */
public final class SelectionTests {

    /**
     * 选择实体 / 物品的输入规则（2026-10-03 用户："打全 next 太长" + "选择实体/物品时支持输入多个"）。
     * <p>
     * 这一轮的改动是<b>纯输入层</b>：{@code GameMain#startAFight} 与
     * {@code PlayerController} 的目标选择都支持
     * <ol>
     *     <li><b>缩写</b>：关键字的前缀（长度 2 到 关键字长度-1）也算命中，
     *     例如 {@code ne}/{@code nex} = {@code next}、{@code q}/{@code qu}/{@code qui} = {@code quit}；</li>
     *     <li><b>一行多个</b>：用 {@code /} 分隔，{@code 1/2/3} 一次选三个、{@code 1/2/3/y} 顺带确认。</li>
     * </ol>
     * 这里只能验它<b>与命令系统不打架</b>（后者是同一层输入里唯一会"截胡"的东西）：
     * {@code 1/2/3} 这种多选写法绝不能被当成命令，{@code q} 也不是命令
     * —— 真进 {@code startAFight} 跑就要读标准输入，那是探针的活。
     * <p>
     * ⚠️ {@code n} 的语义<b>没变</b>：选实体那一步 {@code n} = {@code next}（老约定，判断顺序里排在
     * {@code no} 前面），确认那一步 {@code n} = {@code no} —— 所以 {@code no} 被定义成"必须写全
     * 两个字母以上"（单个 {@code n} 压根不匹配 {@code no}）。
     *
     * @author AI（DeepSeek）生成
     */
    private static void testSelectionInputSyntax() {
        section("选择实体/物品的输入：缩写与一行多个");

        // ① 多选写法不能被命令系统截胡（/ 只出现在行首才是命令前缀）
        check("多选输入 1/2/3 不会被当成命令（否则选择流程永远收不到）", !CommandManager.isCommand("1/2/3"));
        check("带确认的多选输入 1/2/3/y 不会被当成命令", !CommandManager.isCommand("1/2/3/y"));
        check("目标选择的多选输入 0/1/2/next 不会被当成命令", !CommandManager.isCommand("0/1/2/next"));

        // ② 缩写不是命令：单字母 q/n/y 与两字母前缀都走游戏自己的流程
        check("缩写 q（quit）不是命令", !CommandManager.isCommand("q"));
        check("缩写 n（next/no）不是命令", !CommandManager.isCommand("n"));
        check("缩写 ne（next）不是命令", !CommandManager.isCommand("ne"));

        // ③ 命令照旧：/help 与 #help 等价，命令本身没被动过
        check("命令照旧：/help 仍然被识别为命令", CommandManager.isCommand("/help"));
        check("命令照旧：#help 仍然被识别为命令", CommandManager.isCommand("#help"));
        check("命令照旧：help 也能执行（不打印，只看结果）", CommandManager.executeResult("help").isSuccess());
    }

    /**
     * 选人 / 选对手 / 选奖励那三段输入状态机，<b>端到端</b>跑真实的
     * {@link cn.gfhnv.game.GameMain#startAFight()}（2026-10-04 加）。
     * <p>
     * <b>为什么必须端到端</b>：上一轮加的"缩写 + 一行多个"只验了
     * "{@code 1/2/3} 不是命令"，没跑过状态机本身，于是<b>确认分支整个断掉了</b>
     * —— 用户实测 {@code 1} 之后输 {@code y}，得到的是「请先输入数字选择角色」：
     * 新代码把"选好的这一批"卡在 {@code next} 后面才确认，而老行为是"数字后面紧跟的
     * {@code y} 确认的就是它"，于是 {@code y} 掉进了选择阶段的"先输数字"分支。
     * <p>
     * 这里喂的是<b>用户那份复现的逐行翻译</b>（单个确认 / 批量确认 / {@code n} 返回 /
     * {@code next} 前进 / 重复下标去重 / 越界 / 多余段丢弃）。
     * 输入行由 {@link cn.gfhnv.game.GameMain#setInputSourceForSelfTest} 注入
     * （<b>只给自测用</b>，游戏原有的输入方式一个字没改），跑完必须还原。
     *
     * @author AI（DeepSeek）生成
     */
    private static void testStartAFightSelection() {
        section("选人流程：端到端跑 startAFight（用户复现逐条钉住）");

        /* ⓪ 先把状态机本身单独钉一遍：**完全不碰标准输入**（InputReader 走"脚本自己抛异常"
         *    那一格），所以哪怕脚本写短了也只是红一条，不会把整个自测挂住。 */
        testSelectBatchPure();
        testIndividualMultipleAreaChain();
        testDeadRemovedFromAllEntities();

        // 前提：注册表里那 9 个生物模板 + 9 件物品模板就是选人界面列出来的东西
        List<LivingThing> templates = World.getLivingEntityList();
        List<Item> itemTemplates = World.getItemList();
        check("前提：注册表里有足够的生物模板可以选（至少 6 个）—— 实际 " + templates.size(),
                templates.size() >= 6);
        check("前提：注册表里有足够的物品模板可以选（至少 1 件）—— 实际 " + itemTemplates.size(),
                itemTemplates.size() >= 1);
        String firstName = templates.get(0).getName();
        String firstDesc = templates.get(0).getDescription();
        String secondName = templates.get(1).getName();
        String thirdName = templates.get(2).getName();
        String fifthName = templates.get(4).getName();
        /* ④ / ④′ 喂的是下标 5（用户复现里就是 5/5/5/y），所以断言要认下标 5 的名字。
         * 之前这里错用了 fifthName（= templates.get(4)），下标和名字差一位，断言必然假红。 */
        String indexFiveName = templates.get(5).getName();
        String fifthDesc = templates.get(4).getDescription();
        String rewardDesc = itemTemplates.get(0).getDescription();
        String rewardName = itemTemplates.get(0).getName();
        // 跑之前先记一份快照：startAFight 会把选中的副本加进 World.things（收尾要还回去）
        List<Object> thingsBefore = new ArrayList<>(World.getThings());

        LivingThing playerBefore = CommandManager.getPlayer();
        try {
            /* ①② 复现第一段：1 → y（单个确认）。老行为是"数字后面紧跟的 y 确认的就是它"。
             * ⚠️ 脚本的写法（每一段都一样）：**先把三段各喂一个 next 让外层先兜一圈**，
             *    然后才是"角色 / 对手 / 奖励"各一次「数字 → y → next」。
             *    原因：外层循环的收口条件是「fighters 与 enemies 都非空」，只选一边它会再来一轮；
             *    而输入是脚本（有尽头）—— 少写一段就会让脚本提前用完。
             *    最后那一行 next 是**故意留下的**：它让脚本跑到"下一个输入"时才知道结束了，
             *    自测据此把 SCRIPT_EXHAUSTED 当成"跑到底"而不是失败。 */
            String out = startAFightWithInput("next", "next", "next",
                    "1", "y", "next", "0", "y", "next", "0", "y", "next");
            check("① 单个确认：数字后面紧跟的 y 把角色收进队伍（复现里坏掉的那一步）", out.contains("已选:"));
            check("① 单个确认：提示语回到「输入下一个数字或next/n」（复现里被跳过的老流程）",
                    out.contains("输入下一个数字或next/n"));
            check("① 单个确认：y 不再被当成「缺数字」——「请先输入数字选择角色」一次都不出现",
                    !out.contains("请先输入数字选择角色"));
            check("① 单个确认：选中的是下标 1 那一只（打印了它的名字与介绍）",
                    out.contains(firstName) && out.contains(firstDesc));
            check("① 单个确认：整局只确认了一批角色（「已选角色:" + firstName + "」只出现一次，"
                            + "y 没有被拆成两批）—— 实际 " + countConfirmed(out, firstName) + " 次",
                    countConfirmed(out, firstName) == 1);

            /* 把"选中的东西到底加没加进队伍"钉死（输出是给人看的，这些是给机器看的） */
            List<Object> added = new ArrayList<>(World.getThings());
            added.removeAll(thingsBefore);
            check("① 单个确认：确实往 World.things 里加了东西（队伍 / 敌方 / 奖励各一批）—— 实际 "
                    + added.size() + " 个", !added.isEmpty());
            boolean templateItself = false;
            for (Object thing : added) {
                for (LivingThing template : templates) {
                    if (thing == template) {
                        templateItself = true;
                    }
                }
            }
            check("① 单个确认：加进去的是模板的副本，不是注册表模板本身（选人不能把模板交出去）",
                    !templateItself);
            /* ⚠️ 「谁是玩家」在这里已经查不了了：三个阶段跑完之后最后 setPlayer 的可能是别人，
             *    所以只钉"玩家这一格被设过、而且是个副本"。 */
            check("① 单个确认：CommandManager 认的「玩家」被设上了，而且不是注册表模板本身"
                            + "（@s / @p 以它为参照）—— 实际「"
                            + (CommandManager.getPlayer() == null ? "null"
                            : CommandManager.getPlayer().getName()) + "」",
                    CommandManager.getPlayer() != null
                            && CommandManager.getPlayer() != templates.get(0));

            /* ③ 单个确认也适配"选的是第 5 个"（复现里 5 → y 一样坏） */
            out = startAFightWithInput("next", "next", "next",
                    "5", "y", "next", "0", "y", "next", "0", "y", "next");
            check("③ 单个确认（下标 5）：白厄那一只进了队伍", out.contains("已选:"));
            check("③ 单个确认（下标 5）：打印的是下标 5 那一只的名字", out.contains(fifthName));
            check("③ 单个确认（下标 5）：没有退回「请先输入数字选择角色」",
                    !out.contains("请先输入数字选择角色"));

            /* ④ 用户复现第三段：5/5/5/y → **同一个目标选三次**（允许重复）→ 整批一次确认 → 加三个
             * ⚠️ 这里只钉"整批一次确认、重复没被当成输入错误"；"重复就是加多个"本身
             *    由 testSelectBatchPure 的 ② 钉 —— 那边用假候选表，不依赖
             *    "第 5 个模板恰好是白厄"，断言也不会因为注册表顺序变了而假红。 */
            out = startAFightWithInput("next", "next", "next",
                    "5/5/5/y", "next", "0", "y", "next", "0", "y", "next");
            check("④ 批量确认：去重没有报错（重复下标不算「输入错误」）", !out.contains("输入错误"));
            check("④ 批量确认：整批当场确认并加入队伍（打印「已选:」）", out.contains("已选:"));
            check("④ 批量确认：整行一次确认（y 没有被拆成「先确认再重来」两批）—— 「已选角色:"
                            + fifthName + "」出现 " + countConfirmed(out, indexFiveName) + " 次",
                    countConfirmed(out, indexFiveName) == 1);

            /* ④′ 重复下标的另一半：**跨行**再写一遍同一个下标，也只算一次 */
            out = startAFightWithInput("next", "next", "next",
                    "5", "5", "y", "next", "0", "y", "next", "0", "y", "next");
            /* ④′ 一行数字选完之后就进入「等确认」阶段，此时再敲数字是非法输入 ——
             *    ⚠️ 所以「跨行重选同一个下标」在当前状态机下**不可达**，走不到去重那一步。
             *    去重本身由 ② / ④（同一行内重复）钉住；这里钉的是"确认阶段敲数字 = 输入错误"。 */
            check("④′ 确认阶段再敲数字 = 输入错误（跨行重选同一个下标到不了选人那一步）—— 「已选:"
                            + indexFiveName + "」出现 " + countConfirmed(out, indexFiveName) + " 次",
                    countConfirmed(out, indexFiveName) == 1 && out.contains("输入错误"));

            /* ⑤ 一行的分隔符语义：1/2/3/y = 选三个 + 当场确认整批 */
            out = startAFightWithInput("next", "next", "next",
                    "1/2/3/y", "next", "0", "y", "next", "0", "y", "next");
            check("⑤ 一行多个：三个下标都打印了名字与介绍",
                    out.contains(firstName) && out.contains(secondName) && out.contains(thirdName)
                            && out.contains(firstDesc));
            /* ⑤ 只看**第一条**确认行（选角色那一批）：后面选对手、选奖励也各会打一条「已选:」，
             *    所以不能拿全局 countOf 数"确认了几批"。 */
            String firstConfirmLine = java.util.Arrays.stream(out.split("\n"))
                    .map(String::trim)
                    .filter(line -> line.startsWith("已选:"))
                    .findFirst()
                    .orElse("<没有已选:行>");
            String pickedThird = templates.get(3).getName();
            check("⑤ 一行多个：三个下标一次确认（确认行里三个名字都在）—— 实际第一行「"
                            + firstConfirmLine + "」，期望含 " + secondName + " / " + thirdName
                            + " / " + pickedThird,
                    firstConfirmLine.contains(secondName) && firstConfirmLine.contains(thirdName)
                            && firstConfirmLine.contains(pickedThird));

            /* ⑥ n 的语义一个字没变（老代码里 n 先匹配 next，两个阶段都是收口）、
             *    而"放弃这一批"要写全 no —— 这条就是文档 §5.10 与代码不一致过的那个点 */
            out = startAFightWithInput("next", "next", "next",
                    "1", "n", "1", "no", "1", "y", "next", "0", "y", "next", "0", "y", "next");
            check("⑥ 确认阶段：单个 n = next（跳过确认、不加入，也没有被当成「缺数字」）",
                    !out.contains("请先输入数字选择") && countConfirmed(out, firstName) == 1);
            check("⑥ 确认阶段：写全的 no = 放弃这一批（打印「放弃已选:」）",
                    out.contains("放弃已选:"));
            check("⑥ 放弃之后重选同一个下标仍然选得上 —— 确认行出现 "
                            + countConfirmed(out, firstName) + " 次",
                    countConfirmed(out, firstName) == 1 && out.contains("已选:"));

            /* ⑦ next 前进：每一段都要能被 next 收口，否则整局卡在选择界面 */
            out = startAFightWithInput("next", "next", "next",
                    "1", "y", "next", "2", "y", "next", "0", "y", "next");
            check("⑦ next 前进：三段都收口了（两段提示语都在）",
                    out.contains("接下来选择对手") && out.contains("奖励.同理"));
            check("⑦ next 前进：选择阶段敲 next 不算错误", !out.contains("输入错误"));

            /* ⑧ 越界 / 负数 / 非数字：越界的只跳过那一个、不影响同一行里其它下标 */
            out = startAFightWithInput("next", "next", "next",
                    "1/99/y", "next", "0", "y", "next", "0", "y", "next");
            check("⑧ 越界：99 被跳过并报「输入错误」，同一行的 1 照旧选上",
                    out.contains("输入错误") && out.contains(firstName));
            check("⑧ 越界：没有把整段判死（后面仍然走完了确认）", out.contains("已选:"));
            out = startAFightWithInput("next", "next", "next",
                    "2/-1/y", "next", "0", "y", "next", "0", "y", "next");
            check("⑧ 负数：同样只报「输入错误」，同一行的 2 照旧选上（整段没被判死）",
                    out.contains("输入错误") && out.contains(secondName) && out.contains("已选:"));
            // ⚠️ "abc/3" 这种「开头就不是数字」的段整段作废（与老版本一致）：换个第 3 个下标进来，
            //    否则这一段一个都没选上、外层会再来一轮，脚本就用完了
            out = startAFightWithInput("next", "next", "next",
                    "abc/3/y", "next", "0", "y", "next", "0", "y", "next");
            check("⑧ 非数字：整段按「输入错误」处理（不做部分解析），但后面同一行的 3 照旧选上",
                    out.contains("输入错误") && out.contains(thirdName) && out.contains("已选:"));

            /* ⑨ 多余段丢弃：确认一旦收口，同一行后面残留的段不许"隔一轮突然生效"
             * ⚠️ 判据不能用「那一只的名字整段不出现」—— 每轮都会重列一遍名单，
             *    名字本来就会出现（上一版就是这么假红的）。要看的是**确认行**里有没有它。 */
            out = startAFightWithInput("next", "next", "next",
                    "1/y/2", "next", "0", "y", "next", "0", "y", "next");
            check("⑨ 多余段丢弃：y 之后同一行的 2 没有被并进这一批（确认行里只有第一个）"
                            + "—— 实际「已选角色:" + firstName + "」",
                    countConfirmed(out, firstName) == 1);
            check("⑨ 多余段丢弃：确认本身照常成功", out.contains("已选:"));
            out = startAFightWithInput("next", "next", "next",
                    "1/next/2", "1", "y", "next", "0", "y", "next", "0", "y", "next");
            check("⑨ 收口后的残留段确实丢掉了：next 后面的 2 没生效（确认行里只有第一个），"
                            + "而选择阶段读到 1 时照旧算数",
                    countConfirmed(out, firstName) == 1 && out.contains("已选:"));

            /* ⑩ 奖励那一段与角色 / 对手共用同一套状态机（只有介绍那一行） */
            out = startAFightWithInput("next", "next", "next",
                    "1", "y", "next", "2", "y", "next", "0", "y", "next");
            check("⑩ 奖励段：物品打印了介绍", out.contains(rewardDesc));
            check("⑩ 奖励段：确认提示里写的是「奖励」", out.contains("已选奖励:"));
            check("⑩ 奖励段：奖励名没被单独打一行（老版本就是这样，别顺手改）",
                    !rewardName.equals(rewardDesc) && !out.contains(rewardName + "\n" + rewardName));

            /* ⑪ 什么都不选就 next：不加入任何东西、不报错，也不卡住（外层再来一轮，脚本后半段照旧能选） */
            out = startAFightWithInput("next", "next", "next",
                    "1", "y", "next", "0", "y", "next", "0", "y", "next");
            check("⑪ 空选择：next 收口不报错", !out.contains("输入错误"));
            check("⑪ 空选择：外层重新列了名单（名单列了两遍以上），第二轮照旧能选上",
                    countOf(out, "0玩家一") >= 2 && out.contains("已选:"));

            /* ⑫ 命令照旧由 readInput 提前执行掉，不会掉进选择分支 */
            out = startAFightWithInput("next", "next", "next",
                    "/help", "1", "y", "next", "0", "y", "next", "0", "y", "next");
            check("⑫ 命令照旧：选择流程里敲 /help 被当命令执行（不进「输入错误」）",
                    out.contains("可用命令") && !out.contains("输入错误") && out.contains("已选:"));

            /* ⑬ 老写法一个字没变：yes / no / next 全拼、单个数字，三段照样走得完
             *    （缩写是"加功能"，不是"改输入方式"—— 这条守住它就是守住老玩家） */
            out = startAFightWithInput("next", "next", "next",
                    "1", "yes", "next", "2", "yes", "next", "0", "yes", "next");
            check("⑬ 老写法：yes 确认照旧可用（三段各确认一次）", countOf(out, "已选:") == 3);
            out = startAFightWithInput("next", "next", "next",
                    "1", "no", "1", "yes", "next", "2", "yes", "next", "0", "yes", "next");
            check("⑬ 老写法：no 照旧是「放弃这一批」（放弃之后重选同一个下标，确认行只出现一次）—— "
                            + "「已选:" + secondName + "」出现 "
                            + countConfirmed(out, secondName) + " 次",
                    out.contains("放弃已选:") && countConfirmed(out, secondName) == 1);

            /* ⚠️ 没验的：quit / q 会 System.exit(0)，会把整个自测进程带走，所以这里不喂它
             *    （关键字判定本身由 testSelectionInputSyntax 钉住）。 */
        } finally {
            // 注入必须还原：不还原的话后面的用例会去读标准输入（NoSuchElementException）
            cn.gfhnv.game.GameMain.setInputSourceForSelfTest(null, false);
            cn.gfhnv.game.GameMain.setFightInProgressForSelfTest(false);
            CommandManager.setPlayer(playerBefore);
            // 自测跑的是"选完就返回"，这一场战斗没真正开起来，别把它留在全局状态里
            CommandManager.clearCurrentFight();
            for (Object thing : new ArrayList<>(World.getThings())) {
                if (!thingsBefore.contains(thing)) {
                    World.removeThing((cn.gfhnv.game.Thing) thing);
                }
            }
        }
        check("收尾：自测往 World.things 里加的东西都摘掉了（不给后面的用例留污染）—— 现在 "
                + World.getThings().size() + " 个", World.getThings().size() == thingsBefore.size());
    }

    /**
     * 跑本主题的全部用例（顺序 = 下面列的顺序）。
     */
    public static void runAll() throws Exception {
        testSelectionInputSyntax();
        testStartAFightSelection();
    }

    private SelectionTests() {
    }
}
