package cn.gfhnv.game.officialStuff.customCommands;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.OfficialGameContent;
import cn.gfhnv.game.system.command.*;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /summon} —— 往当前战斗里召唤一个生物。
 * <p>
 * 用法：
 * <pre>
 * /summon &lt;实体&gt;              召唤到&lt;我方&gt;（默认）
 * /summon &lt;实体&gt; &lt;阵营&gt;       召唤到指定阵营
 * </pre>
 * 示例：
 * <pre>
 * /summon CommonInsect              叫一只虫子来帮自己
 * /summon commonInsect enemy        把虫子丢到对面去
 * /summon drunkenSword:drunkenSwordsman   模组角色必须写完整 id
 * </pre>
 * <p>
 * <b>实体名怎么写</b>（与 {@code /give}、{@code /effect} 同一套命名空间规则，大小写不敏感）：
 * <ul>
 *     <li><b>完整 id</b>（{@code game_official_content:commonInsect}、
 *     {@code drunkenSword:drunkenSwordsman}）：任何内容都能这么写，精确匹配；</li>
 *     <li><b>短名</b>（{@code commonInsect}）与<b>简单类名</b>（{@code CommonInsect}）：
 *     <b>只解析官方内容</b> —— 模组实体必须写完整 id，否则多装几个模组就分不清是谁家的东西。</li>
 * </ul>
 * 写错会报错并列出当前所有可用实体（官方实体列短名，模组实体列它唯一能写的完整 id）；
 * 用短名指向模组实体时会专门提示该写什么。
 * <p>
 * <b>阵营怎么填</b>（大小写不敏感，默认 {@code our}）：
 * {@code our} / {@code ally} / {@code 我方} 与 {@code enemy} / {@code foe} / {@code 敌方}
 * —— 与 {@code /endfight} 一样同时接受英文和中文，中文在 cmd.exe 里可能打不出来（见 TIPS §3），
 * 所以英文是"一定能用"的那一份。
 * <p>
 * <b>召唤出来的是"活物"而不是摆设</b>：它走 {@link Fight#addFighter(LivingThing)} /
 * {@link Fight#addEnemy(LivingThing)} 入列，于是会被排进时间轴、下个回合就能行动；
 * 同时显式补一次入场初始化（接上战斗上下文 + 调 {@code whenFightStart}）——
 * 那个钩子只在开局由 {@code FightStartEventListener} 遍历一次，中途加入的实体收不到
 * （见 TIPS §5.5.1 ③，召唤物"缺一次初始化"是踩过的坑）。
 * <p>
 * 召唤的是<b>注册表模板的副本</b>（{@code copy()}），所以反复召唤不会互相影响，
 * 副本的 id 也会由 {@code addFighter/addEnemy} 补成完整 id。
 * 实体类没重写 {@code copy()} 时会报错并说明原因，不会把异常直接甩到控制台。
 * <p>
 * <b>这是调试/图一乐性质的命令</b>：不做任何数量或强度限制，把自己家的 BOSS 召唤到对面、
 * 或者给对面塞一只虫皇都是允许的。
 *
 * @author AI（DeepSeek）生成
 */
public class SummonCommand extends Command {

    /**
     * 构造 {@code /summon} 命令。
     */
    public SummonCommand() {
        super("summon");
    }

    /**
     * 解析阵营参数。
     *
     * @param text 玩家输入；{@code null} 表示没写（默认我方）
     * @return {@code true} 表示召唤到敌方
     * @throws CommandSyntaxException 填了无法识别的阵营时抛出
     */
    private static boolean parseToEnemy(String text) throws CommandSyntaxException {
        if (text == null) {
            return false;
        }
        return switch (text.trim().toLowerCase()) {
            case "our", "ally", "friend", "我方" -> false;
            case "enemy", "foe", "敌方" -> true;
            default -> throw CommandSyntaxException.create(
                    "「" + text + "」不是合法的阵营，只能填 our 或 enemy（也接受 我方/敌方）");
        };
    }

    /**
     * 召唤一个实体到指定阵营。
     *
     * @param context 命令上下文（提供 {@code 实体} 与可选的 {@code 阵营}）
     * @param source  命令来源
     * @param toEnemy 是否召唤到敌方
     * @return 影响到的对象数量（成功为 1）
     * @throws CommandSyntaxException 不在战斗里、实体名不对、或模板复制失败时抛出
     */
    private static int summon(CommandContext context, CommandSource source, boolean toEnemy)
            throws CommandSyntaxException {
        Fight fight = context.getFight();
        if (fight == null) {
            throw CommandSyntaxException.create("现在不在战斗中，没法召唤"
                    + "（本命令是把生物放进当前这场战斗的阵营列表里，没有战斗就没有地方放）");
        }
        LivingThing template = findEntity(context.getString("实体", null));
        LivingThing spawned = copyOf(template);
        if (toEnemy) {
            fight.addEnemy(spawned);
        } else {
            fight.addFighter(spawned);
        }
        // addFighter/addEnemy 只负责"进阵营列表 + 排进时间轴"；「战斗开始」那个钩子只在开局
        // 遍历 allEntities 时调一次（FightStartEventListener），**中途加进来的实体收不到**，
        // 所以这里显式补一次入场初始化 —— 与 FlameReaver#summonContainer 的做法一致。
        spawned.setParticipateFight(fight);
        spawned.whenFightStart(fight);
        // 成功回显用短名（给人看，越短越好）；"该怎么写完整 id"由报错里的可用实体列表负责
        source.sendMessage("已在" + (toEnemy ? "敌方" : "我方") + "召唤 " + World.shortIdOf(spawned)
                + "（" + spawned.getName() + "），HP " + spawned.getHp() + "/" + (long) spawned.getHpMax());
        return 1;
    }

    /**
     * 复制一份模板。
     * <p>
     * 单独包一层是为了把 {@code copy()} 的异常翻译成人话：实体基类的 {@code copy()}
     * 直接抛 {@code RuntimeException("请重写此方法..类...")}，模组作者看到的是堆栈，
     * 这里改成一句"哪个类没重写"。
     *
     * @param template 注册表里的模板
     * @return 副本
     * @throws CommandSyntaxException 复制失败时抛出
     */
    private static LivingThing copyOf(LivingThing template) throws CommandSyntaxException {
        try {
            return template.copy();
        } catch (RuntimeException e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw CommandSyntaxException.create("「" + template.getClass().getSimpleName()
                    + "」复制失败：" + reason + "（实体类必须重写 copy()，并走拷贝构造器 new Xxx(this)）");
        }
    }

    /**
     * 按「完整 id / 短名 / 简单类名」在实体注册表里找模板。
     * <p>
     * 短名与类名<b>只解析官方内容</b>；带 {@code :} 的完整 id 谁都认（见类注释）。
     * 只找 {@link LivingThing} —— 不能参战的实体（纯 {@code Entity}）没有召唤的意义。
     * <p>
     * <b>短名优先于类名</b>：类名只是"少打几个字"的便捷别名，不该把短名挤成"有歧义"。
     * 这不是理论问题 —— 【残破容器】与【完整容器】都是
     * {@link cn.gfhnv.game.officialStuff.customEntity.summons.BrokenContainer}，
     * 短名不同、类名相同，两条一起按类名匹配的话，{@code /summon brokenContainer}
     * 这种完全正确的写法会被判成歧义（自测里有一条断言盯着它）。
     *
     * @param rawName 玩家输入的实体名
     * @return 注册表里的模板
     * @throws CommandSyntaxException 名字为空、找不到、或有歧义时抛出
     */
    private static LivingThing findEntity(String rawName) throws CommandSyntaxException {
        if (rawName == null || rawName.isBlank()) {
            throw CommandSyntaxException.create("没有指定实体。可用实体：" + describeRegistry());
        }
        String name = rawName.trim();
        // 带冒号 = 玩家明确指定了命名空间，按完整 id 匹配
        boolean explicitId = name.indexOf(':') >= 0;
        List<LivingThing> byId = new ArrayList<>();
        List<LivingThing> byShortName = new ArrayList<>();
        List<LivingThing> byClassName = new ArrayList<>();
        List<LivingThing> modOnlyMatched = new ArrayList<>();
        for (LivingThing entity : World.getLivingEntityList()) {
            if (entity == null) {
                continue;
            }
            if (matchesId(entity, name)) {
                byId.add(entity);
            } else if (explicitId) {
                // 写了完整 id 却没人认领，就不再试短名/类名了（免得"打错的完整 id"命中别的实体）
                continue;
            } else if (isModContent(entity)) {
                if (matchesShortName(entity, name)) {
                    // 短名其实指向了模组实体，但规则不允许 —— 单独记下来，好在报错里告诉玩家该写什么
                    modOnlyMatched.add(entity);
                }
            } else if (matchesShortId(entity, name)) {
                byShortName.add(entity);
            } else if (matchesClassName(entity, name)) {
                byClassName.add(entity);
            }
        }
        // 三层优先级：完整 id > 短名 > 类名；同一层里命中多个才算真歧义
        List<LivingThing> matched = !byId.isEmpty() ? byId
                : !byShortName.isEmpty() ? byShortName : byClassName;
        if (matched.isEmpty()) {
            if (!modOnlyMatched.isEmpty()) {
                throw CommandSyntaxException.create("「" + name + "」是模组实体，必须写完整的 id："
                        + idsOf(modOnlyMatched) + "（官方实体才能只写短名）");
            }
            throw CommandSyntaxException.create("实体注册表里没有「" + name + "」。可用实体：" + describeRegistry());
        }
        if (matched.size() > 1) {
            throw CommandSyntaxException.create("「" + name + "」匹配到 " + matched.size()
                    + " 个实体：" + idsOf(matched) + "。请写完整的 id（含模组前缀）");
        }
        return matched.get(0);
    }

    /**
     * @param entity 注册表里的实体
     * @param name   玩家输入
     * @return 是否与完整 id 相同（忽略大小写）
     */
    private static boolean matchesId(LivingThing entity, String name) {
        return entity.getId() != null && entity.getId().equalsIgnoreCase(name);
    }

    /**
     * @param entity 注册表里的实体
     * @param name   玩家输入
     * @return 是否与短名相同（忽略大小写）
     */
    private static boolean matchesShortId(LivingThing entity, String name) {
        return World.shortIdOf(entity).equalsIgnoreCase(name);
    }

    /**
     * @param entity 注册表里的实体
     * @param name   玩家输入
     * @return 是否与简单类名相同（忽略大小写）
     */
    private static boolean matchesClassName(LivingThing entity, String name) {
        return entity.getClass().getSimpleName().equalsIgnoreCase(name);
    }

    /**
     * 只比「短名」与「简单类名」，不管是不是官方内容（用于给出更准确的报错）。
     *
     * @param entity 注册表里的实体
     * @param name   玩家输入
     * @return 是否匹配短名或类名（忽略大小写）
     */
    private static boolean matchesShortName(LivingThing entity, String name) {
        return matchesShortId(entity, name) || matchesClassName(entity, name);
    }

    /**
     * @param entity 实体
     * @return 是否由模组（非官方内容）注册
     */
    private static boolean isModContent(LivingThing entity) {
        return !OfficialGameContent.isOfficial(entity);
    }

    /**
     * 把若干实体的 id 拼成可读文本（没有 id 的退回类名）。
     *
     * @param entities 实体列表
     * @return 可读文本
     */
    private static String idsOf(List<LivingThing> entities) {
        List<String> ids = new ArrayList<>();
        for (LivingThing entity : entities) {
            ids.add(entity.getId() == null ? entity.getClass().getSimpleName() : entity.getId());
        }
        return String.join("、", ids);
    }

    /**
     * 列出注册表里所有可召唤的实体（用于报错提示）。
     * <p>
     * 官方实体显示短名（它就能这么写），模组实体显示完整 id（它只能这么写）。
     *
     * @return 可读文本
     */
    private static String describeRegistry() {
        List<String> names = new ArrayList<>();
        for (LivingThing entity : World.getLivingEntityList()) {
            if (entity != null) {
                names.add(displayNameOf(entity) + "（" + entity.getName() + "）");
            }
        }
        return names.isEmpty() ? "（注册表里目前没有可召唤的实体）" : String.join("、", names);
    }

    /**
     * @param entity 实体
     * @return 报错提示里该怎么称呼它：官方实体用短名，模组实体用完整 id
     */
    private static String displayNameOf(LivingThing entity) {
        return isModContent(entity) ? String.valueOf(entity.getId()) : World.shortIdOf(entity);
    }

    /**
     * 构建命令树：{@code summon <实体> [阵营]}。
     *
     * @return 命令根节点
     */
    @Override
    protected CommandNode buildNode() {
        LiteralCommandNode root = node();

        ArgumentBuilder entity = ArgumentBuilder.argumentBuilder("实体", WordArgumentType.word());
        root.addChild(entity);

        // 分支一：/summon <实体> —— 默认召唤到我方
        entity.executes((context, source) -> summon(context, source, false));

        // 分支二：/summon <实体> <阵营>
        ArgumentBuilder side = entity.argument("阵营", StringArgumentType.word());
        side.executes((context, source) -> summon(context, source,
                parseToEnemy(context.getString("阵营", null))));

        return root;
    }
}
