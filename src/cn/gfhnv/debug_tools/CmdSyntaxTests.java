package cn.gfhnv.debug_tools;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entity.PropertyEnhanceType;
import cn.gfhnv.game.entityController.FixOrderController;
import cn.gfhnv.game.inventory.Slot;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.mod.Mod;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.Taunt;
import cn.gfhnv.game.officialStuff.customEntity.monsters.CommonInsect;
import cn.gfhnv.game.officialStuff.customEntity.players.PlayerOne;
import cn.gfhnv.game.officialStuff.customItem.ANiceSword;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.command.*;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.fight.TargetStrategies;
import cn.gfhnv.game.world.World;

import java.util.ArrayList;
import java.util.List;

import static cn.gfhnv.debug_tools.TestKit.*;
import static cn.gfhnv.debug_tools.TestProbes.*;
import static cn.gfhnv.debug_tools.TestSupport.*;

/**
 * 自测：**命令系统**。
 * <p>
 * 主题：命令的词法 / 语法：StringReader、选择器、注册与执行、固定顺序 AI。
 * <p>
 * 由 {@code TestCommandSystem#main} 按顺序调用 {@code CmdSyntaxTests#runAll()}。
 */
public final class CmdSyntaxTests {

    private CmdSyntaxTests() {
    }

    /**
     * 测试 {@link StringReader} 的读取行为。
     */
    private static void testStringReader() {
        section("StringReader 基础读取");
        try {
            // 注意：readWord() 只读到「词的末尾」，不会吃掉后面的空白。
            // 所以 "kill   @s  extra" 读完 @s 之后，getRemaining() 是「  extra」（带前导空格）。
            StringReader reader = new StringReader("kill   @s  extra");
            check("readWord 读到 kill", "kill".equals(reader.readWord()));
            check("readWord 自动跳过多余空白", "@s".equals(reader.readWord()));
            check("remain 为 extra（前导空格未消耗，比较前要 trim）",
                    "extra".equals(reader.getRemaining().trim()));

            // 想看「读完就没内容了」，用一段末尾没有空白的输入
            StringReader tail = new StringReader("kill @s");
            tail.readWord();
            tail.readWord();
            check("读完最后一个词（光标在末尾）canRead 为 false", !tail.canRead());

            // 尾部有空白时，canRead() 仍为 true —— 这是 Brigadier 的既定语义
            StringReader trailing = new StringReader("kill @s ");
            trailing.readWord();
            check("读完 kill 后还剩内容（尾部空白仍可读）", trailing.canRead());
            trailing.readWord();
            trailing.skipWhitespace();
            check("skipWhitespace 之后 canRead 为 false", !trailing.canRead());

            StringReader quoted = new StringReader("say \"hello world\" tail");
            check("readWord 读到 say", "say".equals(quoted.readWord()));
            check("readQuotedString 保留空格", "hello world".equals(quoted.readQuotedString()));
            check("引号后还能读到 tail", "tail".equals(quoted.readWord()));

            StringReader greedy = new StringReader("say  a b c  ");
            greedy.readWord();
            check("readString 吃掉整行", "a b c".equals(greedy.readString()));

            StringReader empty = new StringReader("   ");
            check("空输入 canRead 为 false", !empty.canRead());
        } catch (Exception e) {
            fail("StringReader 抛出异常：" + e);
        }
    }

    /**
     * 测试 {@link EntitySelector} 的语法解析（不求解实体）。
     */
    private static void testEntitySelectorSyntax() {
        section("实体选择器语法");
        try {
            EntitySelector selector = EntitySelector.fromString("@e[type=CommonInsect,limit=2,sort=nearest]");
            check("类型筛选解析正确", "CommonInsect".equals(selector.getTypeFilter()));
            check("limit 解析正确", selector.getLimit() == 2);
            check("sort 解析正确", selector.getSort() == EntitySelector.SortMode.NEAREST);

            EntitySelector self = EntitySelector.fromString("@s");
            check("@s 类型正确", self.getKind() == EntitySelector.SelectorKind.SELF);

            expectSyntaxError("@e[bad=1] 应当报错", () -> EntitySelector.fromString("@e[bad=1]"));
            expectSyntaxError("@x 应当报错", () -> EntitySelector.fromString("@x"));
            expectSyntaxError("不以 @ 开头应当报错", () -> EntitySelector.fromString("kill"));
        } catch (Exception e) {
            fail("选择器语法测试抛出异常：" + e);
        }
    }

    /**
     * 注册官方命令，造一场假战斗，然后跑一批命令。
     */
    private static void testRegistrationAndExecution() {
        section("命令注册与执行");

        // 效果注册表：真实游戏里由 GameMain 把 OfficialGameContent 加进 World、
        // 再在 GameStartEvent 后 registerItself() 写进 World.getEffectList()。
        // 自测不启动游戏，所以这里手动走一遍同样的流程（放在 initialize() 之前，
        // 这样注册命令时打出的「可用效果」日志里就已经有内容了）。
        // 只需要效果表被填满：选择器用的是 World.getThings()（运行时对象），不受这些模板影响。
        // 【模组表也要加】——/give 与 /effect 的"短名只解析官方内容"靠模组表判断谁注册的，
        // 不加的话官方内容会被当成"没有模组认领"，规则就退化成全都放行。
        cn.gfhnv.game.officialStuff.OfficialGameContent officialContent =
                new cn.gfhnv.game.officialStuff.OfficialGameContent();
        World.addMod(officialContent);
        officialContent.registerItself();

        // 只初始化命令系统（不调用 GameMain.gameInitialize()，避免加载模组与配置）
        CommandManager.initialize();
        check("已注册 kill", CommandManager.getRegisteredCommandNames().contains("kill"));
        check("已注册 list", CommandManager.getRegisteredCommandNames().contains("list"));
        check("已注册 hurt", CommandManager.getRegisteredCommandNames().contains("hurt"));
        check("已注册 endfight", CommandManager.getRegisteredCommandNames().contains("endfight"));
        check("已注册 help", CommandManager.getRegisteredCommandNames().contains("help"));

        // 诊断输出：把命令树打印出来。命令解析出问题时，这一小段能立刻定位到
        // 「节点没挂上」「名字不对」「executor 没绑上」中的哪一种。
        System.out.println("  --- 命令树 ---");
        System.out.println("  根节点 " + describeNode(CommandManager.getDispatcher().getRoot())
                + " 根子节点=" + CommandManager.getDispatcher().getRoot().getChildrenNames());
        for (String name : CommandManager.getRegisteredCommandNames()) {
            cn.gfhnv.game.system.command.CommandNode node = CommandManager.getDispatcher().getCommandNode(name);
            System.out.println("  [" + name + "] 自身=" + describeNode(node));
            if (node != null) {
                for (cn.gfhnv.game.system.command.CommandNode child : node.getChildren()) {
                    System.out.println("      └─ " + describeNode(child));
                }
            }
        }
        System.out.println("  --- 命令树结束 ---");

        // 造两个生物并放进一个假战斗
        // 注意：这里直接 new + copy()，绕过了模组注册，所以 id 要自己补上
        // （真实游戏里生物来自 World 注册表，官方内容会加 game_official_content: 前缀）。
        LivingThing hero = new PlayerOne(125).copy();
        LivingThing bug = new CommonInsect(150L).copy();
        hero.setId("game_official_content:playerOne");
        bug.setId("game_official_content:commonInsect");
        hero.setHp(hero.getHpMax());
        bug.setHp(bug.getHpMax());

        List<LivingThing> fighters = new ArrayList<>();
        fighters.add(hero);
        List<LivingThing> enemies = new ArrayList<>();
        enemies.add(bug);
        Fight fight = new Fight(enemies, new ArrayList<>(), fighters);

        World.addThing(hero);
        World.addThing(bug);
        CommandManager.setPlayer(hero);
        CommandManager.setCurrentFight(fight);

        // 诊断：把「解析」与「取参数」拆开验证，方便定位到底哪一步丢了参数
        diagnoseParse("kill @e[type=CommonInsect]");
        diagnoseParse("hurt @s 100");
        diagnoseParse("list @e");
        // 诊断：不经过官方命令，直接测构建器建树（最小复现）
        diagnoseBuilder();

        run("kill @e[type=CommonInsect]", true);
        check("虫子被击杀（HP=0）", bug.getHp() == 0);
        check("虫子已死亡", !bug.isAlive());

        run("hurt @s 100", true);
        check("自己掉了血", hero.getHp() < hero.getHpMax());

        long before = hero.getHp();
        run("hurt @s -50", true);
        check("负数表示回血", hero.getHp() > before);

        run("list", true);
        run("list @e", true);
        run("list @e[name=\"*虫*\"]", true);
        run("help", true);
        run("help kill", true);

        // 上面已经把自己打伤、把虫子打死过，这里把虫子「复活」一下：
        // 下面的 @e[type=CommonInsect] 用例要求它已经在战斗里存活，
        // 否则选择器会因为「筛不出任何生物」而报错（那是另一条用例在测的东西）。
        bug.setHp(bug.getHpMax());
        bug.setAlive(true);
        check("虫子已复活", bug.isAlive());

        // 预期失败的用例
        run("nosuchcommand", false);
        run("kill", false);
        run("kill @e[bad=1]", false);
        run("hurt @s abc", false);
        run("hurt @s 1 2", false);
        run("kill @e[type=\"根本没有这个类型\"]", false);

        /* 参数节点的报错不能被吞成笼统的「无法继续解析」。
         * 典型场景：选择器少写了 @（用户实测踩过），这时选择器自己那句
         * 「实体选择器必须以 @ 开头」才是最该看到的。 */
        String missingAt = errorOf("data modify entity s hp set 1");
        check("少写 @ 的选择器：报的是选择器自己的原因，而不是「无法继续解析」",
                missingAt.contains("@") && !missingAt.contains("无法继续解析"));
        // 反过来：输入已经读完时仍然要说「命令不完整 + 完整用法」，不能被参数报错顶掉
        String giveMissingItem = errorOf("give @s");
        check("命令不完整：用法提示没有被参数报错顶掉",
                giveMissingItem.contains("命令不完整")
                        && giveMissingItem.contains("/give <目标> <物品>"));

        check("前缀 # 与 / 等价（不执行也不崩）", CommandManager.isCommand("#help"));
        check("普通输入不会被当成命令", !CommandManager.isCommand("yes"));
        check("process 对普通输入返回 false", !CommandManager.process("yes"));
        check("process 对 #help 返回 true", CommandManager.process("#help"));

        // 补全：命令名前缀补全 + 参数位置提示（尾随空白表示「准备输入下一个词」）
        List<String> suggestions = CommandManager.getDispatcher().getCompletionSuggestions("k");
        check("补全 k 得到 kill", suggestions.contains("kill"));
        List<String> afterKill = CommandManager.getDispatcher().getCompletionSuggestions("kill ");
        check("kill 后面的补全给出参数提示", afterKill.contains("<目标>"));
        List<String> withoutSpace = CommandManager.getDispatcher().getCompletionSuggestions("kill");
        check("没有尾随空白时不提示下一步（与 MC 一致）", withoutSpace.isEmpty());
        List<String> afterHurtTarget = CommandManager.getDispatcher()
                .getCompletionSuggestions("hurt @s ");
        check("hurt 选完目标后提示第二个参数", afterHurtTarget.contains("<数值>"));

        // 编码自检（Windows 控制台默认 GBK，中文参数值可能在进入程序前变成 ???）
        System.out.println("      " + CommandManager.describeEncoding());

        // 类型筛选的三种写法都必须能用：ASCII 简单类名 + 中文显示名 + id
        // 注意：选择器「一个都没选中」时会抛异常（不是返回空列表），
        // 所以这段必须在虫子还活着的时候跑（上面已经把它打死过一次，这里先复活）。
        bug.setHp(bug.getHpMax());
        bug.setAlive(true);
        cn.gfhnv.game.system.command.CommandSource probe =
                new cn.gfhnv.game.system.command.CommandSource(CommandManager.getPlayer());
        check("kill 节点仍然存在（构建器未回归）",
                CommandManager.getDispatcher().getCommandNode("kill") != null);
        try {
            // fromString 会抛 CommandSyntaxException（受检异常），必须放在 try 里
            cn.gfhnv.game.system.command.EntitySelector byClass =
                    cn.gfhnv.game.system.command.EntitySelector.fromString("@e[type=CommonInsect]");
            cn.gfhnv.game.system.command.EntitySelector byName =
                    cn.gfhnv.game.system.command.EntitySelector.fromString("@e[name=普通虫子]");
            cn.gfhnv.game.system.command.EntitySelector byId =
                    cn.gfhnv.game.system.command.EntitySelector.fromString(
                            "@e[type=game_official_content:commonInsect]");
            byClass.resolve(probe.getSelectorContext());
            byName.resolve(probe.getSelectorContext());
            byId.resolve(probe.getSelectorContext());
            check("筛选：ASCII 类名 @e[type=CommonInsect] 能选中虫子", !byClass.isEmpty());
            check("筛选：中文名 @e[name=普通虫子] 能选中虫子", !byName.isEmpty());
            check("筛选：id @e[type=game_official_content:commonInsect] 能选中虫子", !byId.isEmpty());
        } catch (Exception e) {
            fail("类型/名字筛选解析失败：" + e.getMessage());
        }

        // ---- 效果命令：/effect <目标> add|remove|list ----
        check("已注册 effect", CommandManager.getRegisteredCommandNames().contains("effect"));
        run("effect @s add frozen", true);
        check("冰冻效果已挂上", hasEffect(hero, "frozenEffect"));
        run("effect @s add damageEnhanceEffect 2 5", true);
        check("增伤效果已挂上", hasEffect(hero, "damageEnhanceEffect"));
        check("增伤效果等级为 2", effectLevelOf(hero, "damageEnhanceEffect") == 2);
        run("effect @s list", true);
        run("effect @s remove frozenEffect", true);
        check("冰冻效果已移除", !hasEffect(hero, "frozenEffect"));
        run("effect @s remove all", true);
        check("清空后身上没有效果", hero.getEntityEffectList().isEmpty());

        // 角色专属 / 不存在的效果都必须被拒绝
        run("effect @s add memorizedHp", false);
        check("角色专属效果没有被挂上", !hasEffect(hero, "memorizedHp"));
        run("effect @s add noSuchEffect", false);
        run("effect @s add", false);
        run("effect @s remove", false);
        run("effect @s bad", false);

        // ---- 效果名规则：与 /give 同一套（短名只认官方，模组效果必须写完整 id）----
        // 用自测专用、且【各自一个类】的探针效果，理由有两条：
        //   ① 它们和官方效果不是同一个类，短名撞车时才能验证"官方优先、且不算歧义"；
        //   ② World#fullIdOf 是按【类】查表补全 id 的，同一个类注册两条模板会互相盖掉 id。
        Mod effectProbeMod = new Mod("effectTestMod") {
        };
        ProbeModOnlyEffect modOnlyProbe = new ProbeModOnlyEffect();
        effectProbeMod.addEffect(modOnlyProbe);
        ProbeModSameNameEffect sameNameProbe = new ProbeModSameNameEffect();
        effectProbeMod.addEffect(sameNameProbe);
        World.addMod(effectProbeMod);
        effectProbeMod.registerItself();
        try {
            run("effect @s remove all", true);
            run("effect @s add frozenEffect", true);
            check("effect：短名只解析官方内容（模组同名效果不参与，因此不算歧义）",
                    hasEffectExactId(hero, "game_official_content:frozenEffect")
                            && !hasEffectExactId(hero, "effectTestMod:frozenEffect"));

            run("effect @s remove all", true);
            run("effect @s add effectTestMod:modOnlyEffect", true);
            check("effect：模组效果写完整 id 可以施加",
                    hasEffectExactId(hero, "effectTestMod:modOnlyEffect"));

            run("effect @s remove all", true);
            CommandResult effectShortName = CommandManager.executeResult("effect @s add modOnlyEffect");
            check("effect：模组效果写短名会被拒绝", !effectShortName.isSuccess());
            check("effect：拒绝时提示该写的完整 id", effectShortName.getError() != null
                    && effectShortName.getError().getMessage() != null
                    && effectShortName.getError().getMessage().contains("effectTestMod:modOnlyEffect"));
            run("effect @s remove all", true);
        } finally {
            World.removeEffect(modOnlyProbe);
            World.removeEffect(sameNameProbe);
            World.removeMod(effectProbeMod);
        }

        // 构造函数参数语法：效果名(参数,...)
        run("effect @s add CriticalDamageEnhanceEffect(1,5)", true);
        check("按构造函数参数创建成功", hasEffect(hero, "criticalDamageEnhanceEffect"));
        run("effect @s add frozen(2)", true);
        check("单参数构造函数（frozen(2)）可用", hasEffect(hero, "frozenEffect"));
        run("effect @s remove all", true);
        run("effect @s add frozen(1,2,3)", false);
        run("effect @s add frozen(abc)", false);
        run("effect @s add frozen(1", false);

        // ---- 数字的单位：2 个参数一定是百分比，固定值必须写满 3 个参数 ----
        run("effect @s remove all", true);
        double fixedBefore = hero.getPropertyEnhanceAmount(PropertyEnhanceType.ATTACK);
        double percentBefore = hero.getPropertyEnhancePercent(PropertyEnhanceType.ATTACK);

        run("effect @s add AttackEnhance(1,2)", true);
        check("AttackEnhance(1,2) 的 1 是百分比（percent +1.0）",
                Math.abs(hero.getPropertyEnhancePercent(PropertyEnhanceType.ATTACK) - (percentBefore + 1.0)) < 1e-9);
        check("AttackEnhance(1,2) 不会动固定值",
                Math.abs(hero.getPropertyEnhanceAmount(PropertyEnhanceType.ATTACK) - fixedBefore) < 1e-9);
        run("effect @s remove all", true);
        check("remove all 会把改过的属性一起还回去",
                Math.abs(hero.getPropertyEnhanceAmount(PropertyEnhanceType.ATTACK) - fixedBefore) < 1e-9
                        && Math.abs(hero.getPropertyEnhancePercent(PropertyEnhanceType.ATTACK) - percentBefore) < 1e-9);

        run("effect @s add AttackEnhance(0,2,2)", true);
        check("AttackEnhance(0,2,2) 的 2 是固定值（amount +2）",
                Math.abs(hero.getPropertyEnhanceAmount(PropertyEnhanceType.ATTACK) - (fixedBefore + 2)) < 1e-9);
        check("AttackEnhance(0,2,2) 不会动百分比",
                Math.abs(hero.getPropertyEnhancePercent(PropertyEnhanceType.ATTACK) - percentBefore) < 1e-9);
        run("effect @s remove all", true);

        run("effect @s add AttackEnhance(0.5,3,2)", true);
        check("AttackEnhance(0.5,3,2) 两个数值都生效",
                Math.abs(hero.getPropertyEnhancePercent(PropertyEnhanceType.ATTACK) - (percentBefore + 0.5)) < 1e-9
                        && Math.abs(hero.getPropertyEnhanceAmount(PropertyEnhanceType.ATTACK) - (fixedBefore + 3)) < 1e-9);
        run("effect @s remove all", true);

        // 守护：5 个「百分比 / 固定值」效果都只允许有 1 个双参数 + 1 个三参数构造函数
        Class<?>[] percentStyle = {
                cn.gfhnv.game.officialStuff.customEffect.universalEffects.AttackEnhance.class,
                cn.gfhnv.game.officialStuff.customEffect.universalEffects.HpEnhanceEffect.class,
                cn.gfhnv.game.officialStuff.customEffect.universalEffects.SpeedEnhanceEffect.class,
                cn.gfhnv.game.officialStuff.customEffect.universalEffects.CriticalRateEnhanceEffect.class,
                cn.gfhnv.game.officialStuff.customEffect.universalEffects.CriticalDamageEnhanceEffect.class
        };
        StringBuilder confused = new StringBuilder();
        for (Class<?> type : percentStyle) {
            int two = countConstructors(type, 2);
            int three = countConstructors(type, 3);
            if (two != 1 || three != 1) {
                if (confused.length() > 0) {
                    confused.append('、');
                }
                confused.append(type.getSimpleName()).append("(2参数=").append(two)
                        .append(",3参数=").append(three).append(')');
            }
        }
        check("5 个效果各只有 1 个双参数 + 1 个三参数构造函数"
                        + (confused.length() == 0 ? "" : "（异常：" + confused + "）"),
                confused.length() == 0);

        // isUniversal 判定基于标签（与 isInfinity 一致）
        check("通用效果带 UNIVERSAL 标签",
                templateOf("frozenEffect") != null && templateOf("frozenEffect").isUniversal()
                        && templateOf("frozenEffect").getEffectTagsList()
                        .contains(cn.gfhnv.game.effect.EffectTags.UNIVERSAL));
        check("角色专属效果不带 UNIVERSAL 标签",
                templateOf("memorizedHp") != null && !templateOf("memorizedHp").isUniversal());

        // 复制出来的副本必须还是通用的：实体复制走的是 effect.copy()，
        // 拷贝构造器漏掉 UNIVERSAL 的话，副本在运行时会被当成「非通用」。
        int universalCount = 0;
        StringBuilder lostTag = new StringBuilder();
        for (cn.gfhnv.game.effect.Effect template : cn.gfhnv.game.world.World.getEffectList()) {
            if (template == null || !template.isUniversal()) {
                continue;
            }
            universalCount++;
            if (!template.copy().isUniversal()) {
                if (lostTag.length() > 0) {
                    lostTag.append("、");
                }
                lostTag.append(template.getClass().getSimpleName());
            }
        }
        check("通用效果共 11 种（实际 " + universalCount + " 种）", universalCount == 11);
        check("每种通用效果的 copy() 副本都保留 UNIVERSAL 标签"
                        + (lostTag.length() == 0 ? "" : "（丢失：" + lostTag + "）"),
                universalCount > 0 && lostTag.length() == 0);
        check("副本的 id 与模板一致",
                templateOf("frozenEffect") != null
                        && templateOf("frozenEffect").copy().getID()
                        .equalsIgnoreCase(templateOf("frozenEffect").getID()));

        // ---- /execute as <目标> run <命令> ----
        check("已注册 execute", CommandManager.getRegisteredCommandNames().contains("execute"));

        long bugHpBefore = bug.getHp();
        long heroHpBefore = hero.getHp();

        // 内层 @s 必须变成「被指定的目标」，而不是原来的玩家
        run("execute as @e[type=CommonInsect] run hurt @s 10", true);
        check("execute as：内层 @s 指向虫子（虫子掉 10 血）", bug.getHp() == bugHpBefore - 10);
        check("execute as：玩家一没有掉血", hero.getHp() == heroHpBefore);

        // 外层 @s 仍然是玩家，内层选择器照常工作
        run("execute as @s run hurt @e[type=CommonInsect] 5", true);
        check("execute as：外层 @s 仍是玩家一（由玩家一发出伤害）", bug.getHp() == bugHpBefore - 15);

        // 嵌套：里层 @s 会变成虫子
        run("execute as @e[type=CommonInsect] run execute as @s run hurt @s 1", true);
        check("execute 嵌套：最里层 @s 仍然是虫子", bug.getHp() == bugHpBefore - 16);

        // 只读的内层命令也能跑（@s 换成虫子，输出的是虫子的状态）
        run("execute as @e[type=CommonInsect] run list", true);

        // 预期失败：写错/写漏/选不中/内层命令不存在
        run("execute as @s", false);
        run("execute as @s run", false);
        run("execute run list", false);
        run("execute as @e[type=\"根本没有这个类型\"] run list", false);
        run("execute as @s run nosuchcommand", false);

        // 嵌套上限：9 层 execute 必须被挡住（不然会一路递归到 StackOverflowError）
        StringBuilder tooDeep = new StringBuilder("execute as @s run hurt @s 1");
        for (int i = 0; i < 8; i++) {
            tooDeep.insert(0, "execute as @s run ");
        }
        run(tooDeep.toString(), false);
        check("execute 嵌套上限拦住了过深调用（虫子没被多打）", bug.getHp() == bugHpBefore - 16);

        // ---- id 归一：运行时对象的 id 必须和注册表一样是完整 id ----
        // 效果：技能/命令里 new 出来的效果，挂上身之后应当带 game_official_content: 前缀
        // （靠 LivingThing.addEffect 里的 World.applyRegisteredId）
        run("effect @s add frozen", true);
        check("运行时效果的 id 被补成完整 id",
                effectOf(hero, "game_official_content:frozenEffect") != null);
        run("effect @s remove all", true);
        check("清空后效果列表为空", hero.getEntityEffectList().isEmpty());

        // 实体：技能里直接 new 出来的生物（例如 Boss 分裂），进战斗时补全 id
        LivingThing summoned = new CommonInsect(100L);
        check("刚 new 出来的生物还是短 id（对照）", "commonInsect".equals(summoned.getId()));
        fight.addEnemy(summoned);
        check("进战斗后实体的 id 被补成完整 id",
                "game_official_content:commonInsect".equals(summoned.getId()));

        // 物品：注册表模板 copy() 之后必须保住完整 id（靠 ANiceSword 的拷贝构造器）
        cn.gfhnv.game.item.Item swordTemplate = null;
        for (cn.gfhnv.game.item.Item item : World.getItemList()) {
            if (item != null && item.getId() != null && item.getId().indexOf(':') >= 0) {
                swordTemplate = item;
                break;
            }
        }
        check("物品注册表里有带前缀的模板", swordTemplate != null);
        check("物品：模板 copy() 之后仍是完整 id",
                swordTemplate != null && swordTemplate.getId().equals(swordTemplate.copy().getId()));

        // ---- /give <目标> <物品> [数量] ----
        check("已注册 give", CommandManager.getRegisteredCommandNames().contains("give"));

        int slotsBefore = itemCountOf(hero);
        int unitsBefore = unitCountOf(hero);
        run("give @s aNiceSword", true);
        check("give：短名可用（+1 件，占 1 格）",
                unitCountOf(hero) == unitsBefore + 1 && itemCountOf(hero) == slotsBefore + 1);
        check("give：发的是副本，带完整注册表 id",
                "game_official_content:aNiceSword".equals(firstItemIdOf(hero)));

        run("give @s game_official_content:aNiceSword 3", true);
        check("give：完整 id + 数量可用（+3，共 4 件）", unitCountOf(hero) == unitsBefore + 4);

        run("give @s ANiceSword 2", true);
        check("give：简单类名可用（+2，共 6 件）", unitCountOf(hero) == unitsBefore + 6);
        check("give：同种物品叠在一格（6 件只占 1 格）", itemCountOf(hero) == slotsBefore + 1);
        check("give：那一格的堆叠数是 6", firstItemStackOf(hero) == unitsBefore + 6);

        boolean allFullId = true;
        for (Slot slot : hero.getInventory().getSlots()) {
            Item item = slot.getContainedItem();
            if (item != null && !"game_official_content:aNiceSword".equals(item.getId())) {
                allFullId = false;
            }
        }
        check("give：格子里的物品带完整的注册表 id", allFullId);

        // 用一次只消耗一个：PlayerController.useItem 走的就是 comeToEffect + removeOne
        Item firstItem = firstItemOf(hero);
        if (firstItem != null) {
            hero.getInventory().removeOne(firstItem);
        }
        check("用一次只少一个（还剩 5 件，仍在同一格）",
                unitCountOf(hero) == unitsBefore + 5 && itemCountOf(hero) == slotsBefore + 1);

        // 用光之后格子会被清空
        for (int i = 0; i < 5; i++) {
            Item left = firstItemOf(hero);
            if (left == null) {
                break;
            }
            hero.getInventory().removeOne(left);
        }
        check("用光之后那一格被清空", itemCountOf(hero) == slotsBefore);
        check("物品全部消耗完", unitCountOf(hero) == unitsBefore);

        // 预期失败
        run("give @s noSuchItem", false);
        run("give @s aNiceSword 0", false);
        run("give @s", false);
        run("give @e[type=CommonInsect] aNiceSword", false);

        // ---- 命令不完整时的提示：要给出"接下来该怎么写"，而不是只回显 /give ----
        // 注意失败结果的文本在 getError().getMessage() 里（getMessage() 是成功回显，失败时为 null）
        CommandResult incompleteGive = CommandManager.executeResult("give");
        check("命令不完整时提示完整用法", !incompleteGive.isSuccess()
                && incompleteGive.getError() != null
                && incompleteGive.getError().getMessage() != null
                && incompleteGive.getError().getMessage().contains("/give <目标> <物品>"));

        // ---- 物品名规则：短名/类名只解析官方内容，模组物品必须写完整 id（模仿 MC 的命名空间）----
        // 造一个"假模组"来验证：它的物品 id 会带上 itemTestMod: 前缀
        Mod itemProbeMod = new Mod("itemTestMod") {
        };
        ANiceSword modSword = new ANiceSword();          // 与官方那把剑同短名、同类名
        itemProbeMod.addItem(modSword);
        Item modOnlyItem = new ANiceSword();
        modOnlyItem.setId("modOnlyItem");                // 短名唯一，只能靠完整 id 拿到
        modOnlyItem.setName("模组专属物品");
        itemProbeMod.addItem(modOnlyItem);
        World.addMod(itemProbeMod);
        itemProbeMod.registerItself();
        try {
            int slotsBeforeProbe = itemCountOf(hero);
            int unitsBeforeProbe = unitCountOf(hero);

            // 现在注册表里"aNiceSword"能匹配到官方 + 模组两件，但短名只该命中官方那件
            run("give @s aNiceSword", true);
            check("give：短名只解析官方内容（模组同名物品不参与，因此不算歧义）",
                    "game_official_content:aNiceSword".equals(firstItemIdOf(hero)));

            run("give @s ANiceSword", true);
            check("give：类名同样只解析官方内容",
                    "game_official_content:aNiceSword".equals(firstItemIdOf(hero)));

            CommandResult modShortName = CommandManager.executeResult("give @s modOnlyItem");
            check("give：模组物品写短名会被拒绝", !modShortName.isSuccess());
            check("give：拒绝时提示该写的完整 id", modShortName.getError() != null
                    && modShortName.getError().getMessage() != null
                    && modShortName.getError().getMessage().contains("itemTestMod:modOnlyItem"));

            run("give @s itemTestMod:modOnlyItem", true);
            check("give：模组物品写完整 id 可以发", hasItemId(hero, "itemTestMod:modOnlyItem"));

            // 把探测用的物品收回来，别影响后面的用例
            while (itemCountOf(hero) > slotsBeforeProbe) {
                Item left = firstItemOf(hero);
                if (left == null) {
                    break;
                }
                hero.getInventory().removeOne(left);
            }
            check("探测用的物品已清干净", unitCountOf(hero) == unitsBeforeProbe);
        } finally {
            World.removeItem(modSword);
            World.removeItem(modOnlyItem);
            World.removeMod(itemProbeMod);
        }

        // ---- 官方内容里的效果药水（customItem/potions/）----
        // 每件注册物品都必须能 copy() 并保住完整 id（药水各自实现了拷贝构造器）
        StringBuilder badCopy = new StringBuilder();
        for (Item template : World.getItemList()) {
            if (template == null || template.getId() == null || template.getId().indexOf(':') < 0) {
                if (badCopy.length() > 0) {
                    badCopy.append('、');
                }
                badCopy.append(template == null ? "null" : template.getClass().getSimpleName() + "(没有完整 id)");
                continue;
            }
            try {
                if (!template.getId().equals(template.copy().getId())) {
                    if (badCopy.length() > 0) {
                        badCopy.append('、');
                    }
                    badCopy.append(template.getClass().getSimpleName() + "(copy 后 id 变了)");
                }
            } catch (RuntimeException e) {
                if (badCopy.length() > 0) {
                    badCopy.append('、');
                }
                badCopy.append(template.getClass().getSimpleName() + "(copy 抛异常)");
            }
        }
        check("每件注册物品都能 copy() 且保住完整 id"
                        + (badCopy.length() == 0 ? "" : "（有问题：" + badCopy + "）"),
                badCopy.length() == 0);
        check("注册表里有 9 件物品（1 把剑 + 8 瓶药水）", World.getItemList().size() == 9);

        // 攻击药水：走一遍「使用物品」的那一步（PlayerController.useItem 做的就是 comeToEffect）
        Item attackPotion = itemTemplateOf("attackPotion");
        check("注册表里有攻击药水", attackPotion != null);
        double attackPercentBefore = hero.getPropertyEnhancePercent(PropertyEnhanceType.ATTACK);
        if (attackPotion != null) {
            attackPotion.copy().comeToEffect(hero, fight);
        }
        check("攻击药水：使用后攻击百分比 +0.2",
                Math.abs(hero.getPropertyEnhancePercent(PropertyEnhanceType.ATTACK) - (attackPercentBefore + 0.2)) < 1e-9);
        cn.gfhnv.game.effect.Effect attackEffect = effectOf(hero, "attackEnhanceEffect");
        check("攻击药水：效果来源记的是这件物品的 id",
                attackEffect != null && "game_official_content:attackPotion".equals(attackEffect.getOrigin()));
        run("effect @s remove all", true);

        // 治疗药水：先掉 500 血，再喝一瓶
        Item healingPotion = itemTemplateOf("healingPotion");
        hero.setHp(hero.getHp() - 500);
        long hpBeforePotion = hero.getHp();
        if (healingPotion != null) {
            healingPotion.copy().comeToEffect(hero, fight);
        }
        check("治疗药水：使用后回血 210",
                hero.getHp() == Math.min(hero.getHpMax(), hpBeforePotion + 210));
        run("effect @s remove all", true);

        // 药水同样能堆叠
        int potionSlotsBefore = itemCountOf(hero);
        int potionUnitsBefore = unitCountOf(hero);
        run("give @s attackPotion 3", true);
        check("药水也能堆叠（3 瓶只占 1 格）",
                itemCountOf(hero) == potionSlotsBefore + 1 && unitCountOf(hero) == potionUnitsBefore + 3);

        CommandManager.clearCurrentFight();
        CommandSource.setCurrentFight(null);
    }

    /**
     * 测试 {@link FixOrderController}（固定技能顺序 + 指定下一个技能）、
     * 目标选择策略 {@link TargetStrategies} 与嘲讽效果 {@link Taunt}。
     */
    private static void testFixOrderController() {
        section("固定顺序 AI 与目标策略");
        List<String> actionLog = new ArrayList<>();
        try {
            // 一场小战斗：玩家一（我方） vs 甲虫 / 乙虫（敌方）
            LivingThing hero = new PlayerOne(125).copy();
            LivingThing bugA = new CommonInsect(100L).copy();
            LivingThing bugB = new CommonInsect(100L).copy();
            bugA.setName("甲虫");
            bugB.setName("乙虫");
            World.addThing(hero);
            World.addThing(bugA);
            World.addThing(bugB);
            Fight fight = new Fight(new ArrayList<>(List.of(bugA, bugB)), new ArrayList<>(),
                    new ArrayList<>(List.of(hero)));

            // 三个假技能：只往日志里记一笔，不产生任何战斗效果
            List<Skill> skills = new ArrayList<>();
            skills.add(new ProbeSkill("甲招", actionLog));
            skills.add(new ProbeSkill("乙招", actionLog));
            skills.add(new ProbeSkill("丙招", actionLog));
            FixOrderController controller = new FixOrderController(skills, hero);
            hero.setController(controller);
            check("默认轮转顺序 = 技能列表顺序",
                    String.join(",", controller.getRotationNames()).equals("甲招,乙招,丙招"));
            controller.setRotationByName("甲招", "乙招", "丙招");

            // ① 轮转：甲 → 乙 → 丙 → 甲
            controller.act(fight);
            controller.act(fight);
            controller.act(fight);
            controller.act(fight);
            check("固定顺序：按 甲→乙→丙 循环（第 4 次回到甲）",
                    String.join(",", actionLog).equals("甲招,乙招,丙招,甲招"));
            Skill peeked = controller.peekNextSkill();
            check("peekNextSkill 预知下一个是乙招", peeked != null && "乙招".equals(peeked.getName()));

            // ② 指定下一个技能（插入语义：不消耗轮转）
            check("forceNextSkill 对不存在的技能返回 false", !controller.forceNextSkill("不存在招"));
            check("forceNextSkill 对存在的技能返回 true", controller.forceNextSkill("丙招"));
            Skill peekedForced = controller.peekNextSkill();
            check("peekNextSkill 优先返回指定的技能",
                    peekedForced != null && "丙招".equals(peekedForced.getName()));
            actionLog.clear();
            controller.act(fight);
            check("指定的技能先放（丙招）", String.join(",", actionLog).equals("丙招"));
            actionLog.clear();
            controller.act(fight);
            check("插入语义：放完指定的技能后回到轮转里的乙招",
                    String.join(",", actionLog).equals("乙招"));

            // ③ 替换语义：指定技能顺手吃掉轮转里的下一步
            controller.setRotationByName("甲招", "乙招", "丙招");   // 游标回到甲招
            actionLog.clear();
            controller.act(fight);
            check("替换语义：先按轮转放甲招", String.join(",", actionLog).equals("甲招"));
            actionLog.clear();
            controller.forceNextSkill("甲招", true);                 // 指定甲招，并吃掉轮转里的乙招
            controller.act(fight);
            check("替换语义：这一步放的是甲招", String.join(",", actionLog).equals("甲招"));
            actionLog.clear();
            controller.act(fight);
            check("替换语义：轮转里的乙招被吃掉，下一个是丙招",
                    String.join(",", actionLog).equals("丙招"));

            // ④ 放不出来时顺延（把乙招冷却住）
            controller.setRotationByName("甲招", "乙招", "丙招");
            Skill beta = skillNamed(controller, "乙招");
            if (beta != null) {
                beta.setNowCoolDown(3);
            }
            actionLog.clear();
            controller.act(fight);
            controller.act(fight);
            check("放不出来时顺延到下一招（乙招在冷却 → 丙招）",
                    String.join(",", actionLog).equals("甲招,丙招"));
            if (beta != null) {
                beta.setNowCoolDown(0);
            }

            // ⑤ 拷贝之后必须还是固定顺序（否则选敌人时一 copy 就退化成随机 AI）
            LivingThing clone = hero.copy();
            check("拷贝后控制器类型不变（仍是 FixOrderController）",
                    clone.getController() instanceof FixOrderController);
            FixOrderController cloneController = clone.getController() instanceof FixOrderController
                    ? (FixOrderController) clone.getController() : null;
            check("拷贝后轮转顺序保留",
                    cloneController != null
                            && String.join(",", cloneController.getRotationNames()).equals("甲招,乙招,丙招"));

            // ⑥ 目标策略：first() 恒定选候选里的第一个
            List<Skill> attackSkills = new ArrayList<>();
            attackSkills.add(new ProbeSkill("点杀", actionLog, 1));
            FixOrderController attacker = new FixOrderController(attackSkills, hero);
            hero.setController(attacker);
            attacker.setRotationByName("点杀");
            attacker.setTargetStrategy(TargetStrategies.first());
            actionLog.clear();
            attacker.act(fight);
            check("目标策略 first()：打候选里的第一个（甲虫）",
                    actionLog.size() == 1 && actionLog.get(0).equals("点杀→甲虫"));

            // 冷却语义（统一后）：coolDown == 0 = 没有冷却，释放完仍是 0（可以接着再用）；
            // coolDown > 0 时 nowCoolDown = coolDown + 1。这里同时也是「目标策略」那几条断言能连续跑的前提。
            Skill pointKill = skillNamed(attacker, "点杀");
            check("冷却语义统一：没有冷却（coolDown=0）的技能释放后 nowCoolDown 仍是 0",
                    pointKill != null && pointKill.getCoolDown() == 0 && pointKill.getNowCoolDown() == 0);

            // ⑦ 嘲讽（2026-10-04 重做）：**A 给 B 施加 → B 打人时优先打 A**。
            //    所以效果挂在**攻击者（hero）**身上、origin 记**施加者的 uuid**。
            //    旧实现把方向搞反了（查"目标身上有没有嘲讽"），嘲讽从来没生效过。
            hero.addEffect(new Taunt(1, 3).setOrigin(bugB.getUUID()));
            check("嘲讽：能从攻击者身上读出「乙虫嘲讽了我」（按 origin 认人，不是看目标身上有没有）",
                    TargetStrategies.tauntLevelFrom(hero, bugB) == 1
                            && TargetStrategies.tauntLevelFrom(hero, bugA) == 0);
            // 故意用**没包装过**的 first()：默认策略就该自带嘲讽感知（以前必须自己包 tauntAware 才生效）
            attacker.setTargetStrategy(TargetStrategies.first());
            actionLog.clear();
            attacker.act(fight);
            check("嘲讽：优先打嘲讽我的乙虫（即使它不是候选里的第一个，而且没手动包 tauntAware）",
                    actionLog.size() == 1 && actionLog.get(0).equals("点杀→乙虫"));

            // ⑧ 嘲讽等级高的更优先
            hero.addEffect(new Taunt(2, 3).setOrigin(bugA.getUUID()));
            actionLog.clear();
            attacker.act(fight);
            check("嘲讽等级高的更优先（甲虫说等级 2 > 乙虫等级 1）",
                    actionLog.size() == 1 && actionLog.get(0).equals("点杀→甲虫"));

            // ⑨ 嘲讽效果已注册（/effect 里能直接加）
            check("嘲讽效果已注册（/effect 可用）", templateOf("tauntEffect") != null);
        } catch (Exception e) {
            fail("固定顺序 AI 测试抛出异常：" + e);
        }
    }

    /**
     * 跑本主题的全部用例（顺序 = 下面列的顺序）。
     */
    public static void runAll() throws Exception {
        testStringReader();
        testEntitySelectorSyntax();
        testRegistrationAndExecution();
        testFixOrderController();
    }
}
