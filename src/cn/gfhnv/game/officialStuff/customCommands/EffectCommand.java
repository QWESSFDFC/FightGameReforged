package cn.gfhnv.game.officialStuff.customCommands;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.system.command.ArgumentBuilder;
import cn.gfhnv.game.system.command.Command;
import cn.gfhnv.game.system.command.CommandContext;
import cn.gfhnv.game.system.command.CommandManager;
import cn.gfhnv.game.system.command.CommandNode;
import cn.gfhnv.game.system.command.CommandSource;
import cn.gfhnv.game.system.command.CommandSyntaxException;
import cn.gfhnv.game.system.command.EntityArgumentType;
import cn.gfhnv.game.system.command.IntegerArgumentType;
import cn.gfhnv.game.system.command.LiteralCommandNode;
import cn.gfhnv.game.system.command.WordArgumentType;
import cn.gfhnv.game.world.World;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /effect} —— 给生物添加、移除、查看效果。
 * <p>
 * 用法：
 * <pre>
 * /effect &lt;目标&gt; add &lt;效果&gt; [等级] [持续回合]     添加效果（按默认构造规则）
 * /effect &lt;目标&gt; add &lt;效果&gt;(参数,...)              添加效果（显式指定构造函数参数）
 * /effect &lt;目标&gt; remove &lt;效果&gt;                     移除指定效果（按 id 匹配）
 * /effect &lt;目标&gt; remove all                        移除全部效果
 * /effect &lt;目标&gt; list                              查看身上所有效果
 * </pre>
 * 示例：
 * <pre>
 * /effect @s add frozen                          冰冻 1 回合（默认构造规则）
 * /effect @s add damageEnhanceEffect 2 5         等级 2、持续 5 回合
 * /effect @s add AttackEnhance(0.2,3)            3 回合内攻击 +20%（2 参数 = 只给百分比）
 * /effect @s add AttackEnhance(0,2,3)            3 回合内攻击 +2 点（3 参数 = 百分比,固定值,回合）
 * /effect @s add CriticalDMGEnhanceEffect(1,5)   5 回合内暴击伤害 +100%（1.0 = 100%）
 * </pre>
 * <p>
 * <b>候选来源</b>：效果注册表 {@link World#getEffectList()}
 * （模组在 {@code invokeWhenLoaded()} 里通过 {@code Mod.addEffect(...)} 注册的效果也会自动出现），
 * 但只有带 {@link cn.gfhnv.game.effect.EffectTags#UNIVERSAL} 标签的效果
 * （即 {@link Effect#isUniversal()} 为 {@code true}）才能被添加 ——
 * 角色专属或机制性的效果（例如「记忆血量」）不会出现在候选里，也不会被施加到别人身上。
 * <p>
 * <b>效果实例的创建</b>：
 * <ul>
 *     <li>写了构造函数参数（{@code 名字(1,5)}）：在该效果的公共构造函数里找<b>唯一</b>匹配的那个，
 *     一个都没匹配上、或匹配到多个，都会报错并列出相关构造函数；</li>
 *     <li>没写参数：依次尝试 {@code (int level, int lastTime)} → {@code (int lastTime)} → {@code ()}，
 *     用最后一种时会把等级/持续回合用 setter 补上。</li>
 * </ul>
 * <b>数字的单位由构造函数决定，命令不猜</b>：通用效果里凡是「百分比 / 固定值」两种含义的数值
 * 都收在同一个 3 参数构造器 {@code (double percent, long amount, int lastTime)} 里，
 * 只写 2 个参数就一定是百分比，要固定值就写满 3 个参数，位置固定、不会有第二种解释。
 * <pre>
 * /effect @s add AttackEnhance(1,3)        3 回合内攻击 ×2（percent = 1.0）
 * /effect @s add AttackEnhance(0.2,0,3)    3 回合内攻击 +20%
 * /effect @s add AttackEnhance(0,2,3)      3 回合内攻击 +2 点
 * </pre>
 * 每次添加的回显里都会写出实际用了哪个构造函数。
 *
 * @author AI（DeepSeek）生成
 */
public class EffectCommand extends Command {

    /**
     * 构造 {@code /effect} 命令。
     */
    public EffectCommand() {
        super("effect");
    }

    /**
     * 构建命令树。
     * <p>
     * 写法：{@link ArgumentBuilder#argumentBuilder(String, cn.gfhnv.game.system.command.ArgumentType)}
     * 建出「目标」这一层，其余各层一层一个变量依次往下建，最后只把最外层交给命令根节点。
     * <p>
     * {@code 目标} 只建<b>一次</b>，{@code add / remove / list} 三条分支都挂在同一个节点上：
     * 若三条链各建一个「目标」，它们会在 {@link CommandNode#addChild(CommandNode)} 里
     * 按名字合并，后续再往其中一个上挂子分支就会挂到被丢弃的那个对象上，树里看不到。
     *
     * @return 命令根节点
     */
    @Override
    protected CommandNode buildNode() {
        LiteralCommandNode root = node();

        // 共同的外层：/effect <目标> ...
        ArgumentBuilder target = ArgumentBuilder.argumentBuilder("目标", EntityArgumentType.entities());
        root.addChild(target);

        // /effect <目标> add <效果> [等级] [持续回合]
        ArgumentBuilder add = target.literal("add");
        ArgumentBuilder effectArgument = add.argument("效果", WordArgumentType.word());
        // /effect <目标> add <效果>
        effectArgument.executes((context, source) -> addEffect(context, source, false));
        // /effect <目标> add <效果> <等级>
        ArgumentBuilder level = effectArgument.argument("等级", IntegerArgumentType.integer(1, 99));
        level.executes((context, source) -> addEffect(context, source, false));
        // /effect <目标> add <效果> <等级> <持续回合>
        ArgumentBuilder duration = level.argument("持续回合", IntegerArgumentType.integer(1, 999));
        duration.executes((context, source) -> addEffect(context, source, true));

        // /effect <目标> remove <效果>（效果填 all 或 * 表示清空）
        ArgumentBuilder remove = target.literal("remove");
        ArgumentBuilder removeArgument = remove.argument("效果", WordArgumentType.word());
        removeArgument.executes((context, source) -> removeEffect(context, source));

        // /effect <目标> list
        ArgumentBuilder list = target.literal("list");
        list.executes((context, source) -> listEffects(context, source));

        return root;
    }

    /**
     * 添加效果。
     *
     * @param context      命令上下文
     * @param source       命令来源
     * @param withDuration 是否显式给了持续回合数（没给则用效果自带的默认值）
     * @return 影响到的对象数量
     * @throws CommandSyntaxException 效果不存在、不可施加或构造失败时抛出
     */
    private static int addEffect(CommandContext context, CommandSource source, boolean withDuration)
            throws CommandSyntaxException {
        List<LivingThing> targets = context.getLivingThings("目标");
        String effectName = context.getString("效果", null);
        int level = context.getInt("等级", null, 1);
        NameSpec spec = parseNameSpec(effectName);
        Effect template = findUniversalEffect(effectName);
        int lastTime = withDuration ? context.getInt("持续回合", null, template.getLastTime())
                : (template.getLastTime() > 0 ? template.getLastTime() : 1);

        int applied = 0;
        StringBuilder detail = new StringBuilder();
        String how = null;
        for (LivingThing target : targets) {
            Created created = createEffect(template, spec, level, lastTime);
            if (how == null) {
                // 回显里写出实际用的构造函数：同一组数字可能有多种解释，写清楚省得猜
                how = created.how();
            }
            target.addEffect(target, created.effect());
            if (detail.length() > 0) {
                detail.append("、");
            }
            detail.append(EntityArgumentType.nameOf(target));
            applied++;
        }
        source.sendMessage("已对 " + applied + " 个目标添加效果 " + shortId(template)
                + (how == null ? "" : "（" + how + "）") + "：" + detail);
        return applied;
    }

    /**
     * 移除效果：{@code remove &lt;效果&gt;} 按 id 匹配，{@code remove all} 清空全部。
     *
     * @param context 命令上下文
     * @param source  命令来源
     * @return 影响到的对象数量
     * @throws CommandSyntaxException 解析失败时抛出
     */
    private static int removeEffect(CommandContext context, CommandSource source) throws CommandSyntaxException {
        List<LivingThing> targets = context.getLivingThings("目标");
        String effectName = context.getString("效果", null);

        if (effectName != null && (effectName.equalsIgnoreCase("all") || effectName.equals("*"))) {
            int count = 0;
            for (LivingThing target : targets) {
                // 先让每个效果自己收尾（把「+N 攻击」这类已经改到属性上的加成还回去），再清空列表；
                // 直接 clear() 会把加成永久留在身上。
                List<Effect> existing = new ArrayList<>(target.getEntityEffectList());
                for (Effect effect : existing) {
                    effect.whenLastTimeEnd(target);
                }
                target.getEntityEffectList().clear();
                count += existing.size();
            }
            source.sendMessage("已清空 " + targets.size() + " 个目标身上的全部效果，共移除 " + count + " 个。");
            return count;
        }

        Effect template = findUniversalEffect(effectName);
        int count = 0;
        StringBuilder detail = new StringBuilder();
        for (LivingThing target : targets) {
            List<Effect> toRemove = new ArrayList<>();
            for (Effect effect : target.getEntityEffectList()) {
                if (sameEffect(effect, template)) {
                    toRemove.add(effect);
                }
            }
            for (Effect effect : toRemove) {
                effect.whenLastTimeEnd(target);
                target.removeEffect(effect);
                count++;
            }
            if (detail.length() > 0) {
                detail.append("、");
            }
            detail.append(EntityArgumentType.nameOf(target)).append("（移除 ").append(toRemove.size()).append(" 个）");
        }
        source.sendMessage("已移除效果 " + shortId(template) + "，共 " + count + " 个：" + detail);
        return count;
    }

    /**
     * 判断一个效果实例是不是模板对应的那种效果。
     * <p>
     * 不能直接比 id 字符串：注册表里的模板 id 带模组前缀
     * （{@code game_official_content:frozenEffect}），而运行时实例的 id 是类里写死的短名
     * （{@code frozenEffect}）。所以先比类型，再比去掉前缀后的 id。
     *
     * @param effect   目标身上的效果实例
     * @param template 注册表里的模板
     * @return 是否是同一种效果
     */
    private static boolean sameEffect(Effect effect, Effect template) {
        if (effect == null || template == null) {
            return false;
        }
        if (effect.getClass() == template.getClass()) {
            return true;
        }
        String left = effect.getID() == null ? "" : effect.getID();
        String right = template.getID() == null ? "" : template.getID();
        return !left.isEmpty() && (left.equalsIgnoreCase(right)
                || shortId(left).equalsIgnoreCase(shortId(right)));
    }

    /**
     * 列出目标身上的效果。
     *
     * @param context 命令上下文
     * @param source  命令来源
     * @return 列出的目标数量
     * @throws CommandSyntaxException 解析失败时抛出
     */
    private static int listEffects(CommandContext context, CommandSource source) throws CommandSyntaxException {
        List<LivingThing> targets = context.getLivingThings("目标");
        for (LivingThing target : targets) {
            List<Effect> effects = target.getEntityEffectList();
            if (effects.isEmpty()) {
                source.sendMessage(EntityArgumentType.nameOf(target) + " 身上没有任何效果。");
                continue;
            }
            StringBuilder builder = new StringBuilder(EntityArgumentType.nameOf(target))
                    .append(" 身上的效果（").append(effects.size()).append(" 个）：");
            for (Effect effect : effects) {
                builder.append(System.lineSeparator()).append("  ").append(describe(effect));
            }
            source.sendMessage(builder.toString());
        }
        return targets.size();
    }

    /**
     * 描述一个效果实例。
     *
     * @param effect 效果
     * @return 可读文本
     */
    private static String describe(Effect effect) {
        String duration = effect.isInfinity() ? "无限" : effect.getLastTime() + " 回合";
        return effect.getID() + " 等级 " + effect.getLevel() + " 剩余 " + duration
                + (effect.isNegative() ? " [负面]" : " [正面]");
    }

    /**
     * 按玩家输入的效果名在注册表里查找模板，并允许带构造函数参数。
     * <p>
     * 支持两种写法：
     * <pre>
     * frozen                              按名字（用默认构造规则）
     * CriticalDMGEnhanceEffect(1,5)       显式指定构造函数参数
     * frozen(3)
     * AttackEnhance(0.2,3)
     * </pre>
     *
     * @param raw 玩家输入
     * @return 找到的注册表模板
     * @throws CommandSyntaxException 找不到、不是通用效果或语法错误时抛出
     */
    private static Effect findUniversalEffect(String raw) throws CommandSyntaxException {
        NameSpec spec = parseNameSpec(raw);
        for (Effect effect : World.getEffectList()) {
            if (effect == null) {
                continue;
            }
            if (matches(effect, spec.name())) {
                if (!effect.isUniversal()) {
                    throw CommandSyntaxException.create("「" + shortId(effect)
                            + "」是角色专属/机制性效果，不能用 /effect 施加。可用效果：" + describeRegistry());
                }
                return effect;
            }
        }
        throw CommandSyntaxException.create("效果注册表里没有「" + spec.name() + "」。可用效果：" + describeRegistry());
    }

    /**
     * 解析「名字(参数,...)」形式的输入。
     *
     * @param raw 玩家输入
     * @return 名字与构造函数参数文本（没写括号时参数为空列表）
     * @throws CommandSyntaxException 括号不配对时抛出
     */
    private static NameSpec parseNameSpec(String raw) throws CommandSyntaxException {
        if (raw == null || raw.isBlank()) {
            throw CommandSyntaxException.create("没有指定效果名。可用效果：" + describeRegistry());
        }
        String text = raw.trim();
        int open = text.indexOf('(');
        if (open < 0) {
            return new NameSpec(text, List.of());
        }
        if (!text.endsWith(")")) {
            throw CommandSyntaxException.create("构造函数参数没有用右括号闭合：" + text);
        }
        String name = text.substring(0, open).trim();
        String inside = text.substring(open + 1, text.length() - 1).trim();
        List<String> params = new ArrayList<>();
        if (!inside.isEmpty()) {
            for (String part : inside.split(",")) {
                String p = part.trim();
                if (!p.isEmpty()) {
                    params.add(p);
                }
            }
        }
        return new NameSpec(name, params);
    }

    /**
     * 效果名 + 可选的构造函数参数文本。
     *
     * @param name   效果 id 或类名
     * @param params 构造函数参数文本（按书写顺序）
     */
    private record NameSpec(String name, List<String> params) {
    }

    /**
     * 判断注册表里的效果是否匹配玩家输入的名字。
     *
     * @param effect 注册表里的效果
     * @param name   玩家输入
     * @return 是否匹配（id、去掉模组前缀的 id、或简单类名，均忽略大小写）
     */
    private static boolean matches(Effect effect, String name) {
        String id = effect.getID() == null ? "" : effect.getID();
        String simple = effect.getClass().getSimpleName();
        return id.equalsIgnoreCase(name)
                || shortId(id).equalsIgnoreCase(name)
                || simple.equalsIgnoreCase(name);
    }

    /**
     * 去掉 id 里的模组前缀。
     * <p>
     * 注册进 {@link World} 的效果 id 会被 {@code Mod.addEffect()} 加上 {@code MOD_ID:} 前缀
     * （官方内容是 {@code game_official_content:frozenEffect}），
     * 而玩家习惯写短名，报错提示里也只该出现短名。
     *
     * @param id 完整 id
     * @return 去掉 {@code 前缀:} 之后的 id；没有前缀则原样返回
     */
    private static String shortId(String id) {
        if (id == null) {
            return "";
        }
        int colon = id.indexOf(':');
        return colon >= 0 && colon + 1 < id.length() ? id.substring(colon + 1) : id;
    }

    /**
     * 效果实例的短 id（用于提示与回显）。
     *
     * @param effect 效果
     * @return 短 id
     */
    private static String shortId(Effect effect) {
        return effect == null ? "?" : shortId(effect.getID());
    }

    /**
     * 列出注册表里所有可用的通用效果名（用于提示与补全）。
     *
     * @return 可用效果名列表（短 id）
     */
    private static List<String> universalEffectNames() {
        List<String> names = new ArrayList<>();
        for (Effect effect : World.getEffectList()) {
            if (effect != null && effect.isUniversal()) {
                names.add(shortId(effect));
            }
        }
        return names;
    }

    /**
     * 把可用效果拼成一行提示文本。
     *
     * @return 可读文本
     */
    private static String describeRegistry() {
        List<String> names = universalEffectNames();
        if (names.isEmpty()) {
            return "（注册表里目前没有通用效果）";
        }
        return String.join("、", names);
    }

    /**
     * 创建效果实例。
     * <p>
     * 两种路径：
     * <ol>
     *     <li>玩家写了构造函数参数（{@code 名字(...)}）：在该效果的公共构造函数里找<b>唯一</b>匹配的那个，
     *     一个都没匹配上、或者匹配到多个，都会报错（见 {@link #constructWithArguments}）；</li>
     *     <li>没写参数：依次尝试 {@code (int level, int lastTime)} → {@code (int lastTime)} → {@code ()}，
     *     最后一个会把 level / lastTime 用 setter 补上。</li>
     * </ol>
     * 返回值里带一句「用了哪个构造函数」，命令回显会打出来 —— 同一组数字往往有多种解释，
     * 写清楚比让人猜好。
     *
     * @param template 注册表里的模板（用来确定具体类型）
     * @param spec     名字与构造函数参数
     * @param level    效果等级（未显式给参数时使用）
     * @param lastTime 持续回合数
     * @return 新建的效果实例 + 用了哪个构造函数
     * @throws CommandSyntaxException 构造失败时抛出
     */
    private static Created createEffect(Effect template, NameSpec spec, int level, int lastTime)
            throws CommandSyntaxException {
        Class<? extends Effect> type = template.getClass();
        if (!spec.params().isEmpty()) {
            return constructWithArguments(type, template, spec.params());
        }
        Created created = tryConstruct(type, new Class<?>[]{int.class, int.class}, new Object[]{level, lastTime});
        if (created == null) {
            created = tryConstruct(type, new Class<?>[]{int.class}, new Object[]{lastTime});
        }
        if (created == null) {
            created = tryConstruct(type, new Class<?>[]{}, new Object[]{});
            if (created != null) {
                created.effect().setLevel(level);
                created.effect().setLastTime(lastTime);
            }
        }
        if (created == null) {
            throw CommandSyntaxException.create("无法构造效果「" + shortId(template) + "」（类 "
                    + type.getSimpleName() + "）。它可用的构造函数：" + describeConstructors(type));
        }
        return new Created(created.effect(),
                created.how() + "，等级 " + level + "，持续 " + lastTime + " 回合");
    }

    /**
     * 按玩家给出的构造函数参数创建实例。
     * <p>
     * 匹配规则：参数个数相同、且每个参数都能转换成对应形参类型的构造函数才算候选。
     * <b>候选必须唯一</b>：
     * <ul>
     *     <li>一个都不匹配 → 报错并列出该效果全部可用构造函数；</li>
     *     <li>匹配到多个 → 也报错并列出这几个候选：说明这个效果的构造函数有歧义，
     *     需要在内容那边改清楚（把可能混的数值并进同一个构造函数，靠参数个数区分：
     *     {@code (double percent, long amount, int lastTime)}）。</li>
     * </ul>
     *
     * @param type      效果类型
     * @param template  注册表里的模板
     * @param arguments 参数文本
     * @return 新建实例 + 用了哪个构造函数
     * @throws CommandSyntaxException 没有匹配或有多个匹配时抛出
     */
    private static Created constructWithArguments(Class<? extends Effect> type, Effect template,
                                                  List<String> arguments) throws CommandSyntaxException {
        List<Constructor<?>> matched = new ArrayList<>();
        List<Object[]> matchedValues = new ArrayList<>();
        for (Constructor<?> constructor : type.getConstructors()) {
            Class<?>[] params = constructor.getParameterTypes();
            if (params.length != arguments.size()) {
                continue;
            }
            Object[] values = new Object[params.length];
            boolean ok = true;
            for (int i = 0; i < params.length; i++) {
                Object value = convert(arguments.get(i), params[i]);
                if (value == null) {
                    ok = false;
                    break;
                }
                values[i] = value;
            }
            if (ok) {
                matched.add(constructor);
                matchedValues.add(values);
            }
        }
        if (matched.isEmpty()) {
            throw CommandSyntaxException.create("效果「" + shortId(template) + "」没有匹配 ("
                    + String.join(", ", arguments) + ") 的构造函数。它可用的构造函数：" + describeConstructors(type));
        }
        if (matched.size() > 1) {
            StringBuilder names = new StringBuilder();
            for (Constructor<?> constructor : matched) {
                if (names.length() > 0) {
                    names.append('、');
                }
                names.append(signatureOf(constructor));
            }
            throw CommandSyntaxException.create("(" + String.join(", ", arguments) + ") 同时匹配 "
                    + matched.size() + " 个构造函数：" + names
                    + "。同一组数字有多种解释，无法确定用哪一个，请把该效果的构造函数改成不会混的写法"
                    + "（把可能混的数值并进同一个构造函数，靠参数个数区分）");
        }
        Constructor<?> constructor = matched.get(0);
        try {
            Object created = constructor.newInstance(matchedValues.get(0));
            if (created instanceof Effect effect) {
                return new Created(effect, "按构造函数 " + signatureOf(constructor)
                        + " 创建 (" + String.join(", ", arguments) + ")");
            }
        } catch (ReflectiveOperationException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw CommandSyntaxException.create("调用构造函数 " + signatureOf(constructor) + " 失败：" + cause);
        }
        throw CommandSyntaxException.create("构造函数 " + signatureOf(constructor) + " 返回的不是效果实例");
    }

    /**
     * 把一段参数文本转换成构造函数需要的类型。
     *
     * @param text   参数文本
     * @param target 目标类型
     * @return 转换结果；无法转换时返回 {@code null}
     */
    private static Object convert(String text, Class<?> target) {
        try {
            if (target == int.class || target == Integer.class) {
                return Integer.parseInt(text);
            }
            if (target == long.class || target == Long.class) {
                return Long.parseLong(text);
            }
            if (target == double.class || target == Double.class) {
                return Double.parseDouble(text);
            }
            if (target == float.class || target == Float.class) {
                return Float.parseFloat(text);
            }
            if (target == short.class || target == Short.class) {
                return Short.parseShort(text);
            }
            if (target == boolean.class || target == Boolean.class) {
                // 只认这几种写法，别的都算转换失败（免得 "abc" 被静默当成 false）
                if (text.equalsIgnoreCase("true") || text.equals("是") || text.equals("开")) {
                    return Boolean.TRUE;
                }
                if (text.equalsIgnoreCase("false") || text.equals("否") || text.equals("关")) {
                    return Boolean.FALSE;
                }
                return null;
            }
            if (target == String.class) {
                return text;
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }

    /**
     * 列出某个效果类可用的公共构造函数（用于报错提示）。
     *
     * @param type 效果类型
     * @return 形如 {@code Effect()、Effect(int,int)} 的文本
     */
    private static String describeConstructors(Class<? extends Effect> type) {
        List<String> list = new ArrayList<>();
        for (Constructor<?> constructor : type.getConstructors()) {
            list.add(signatureOf(constructor));
        }
        return list.isEmpty() ? "（没有公共构造函数）" : String.join("、", list);
    }

    /**
     * 把构造函数写成可读签名。
     *
     * @param constructor 构造函数
     * @return 形如 {@code AttackEnhance(double,int)} 的文本
     */
    private static String signatureOf(Constructor<?> constructor) {
        StringBuilder builder = new StringBuilder(constructor.getDeclaringClass().getSimpleName()).append('(');
        Class<?>[] params = constructor.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(params[i].getSimpleName());
        }
        return builder.append(')').toString();
    }

    /**
     * 尝试用指定参数表调用构造函数。
     *
     * @param type   目标类型
     * @param params 参数类型表
     * @param args   参数值
     * @return 新建实例 + 用了哪个构造函数；没有对应构造器或构造失败时返回 {@code null}
     */
    private static Created tryConstruct(Class<? extends Effect> type, Class<?>[] params, Object[] args) {
        try {
            Constructor<? extends Effect> constructor = type.getConstructor(params);
            return new Created(constructor.newInstance(args), "默认构造 " + signatureOf(constructor));
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /**
     * 「新建出来的效果实例」+「用的是哪个构造函数」。
     *
     * @param effect 实例
     * @param how    可读的构造方式描述（会打进命令回显）
     */
    private record Created(Effect effect, String how) {
    }

    /**
     * 便于外部（例如补全）查询当前可用的通用效果。
     *
     * @return 可用效果名列表
     */
    public static List<String> availableEffects() {
        return universalEffectNames();
    }

    /**
     * 把可用效果写进日志（注册完成后调用，便于排查模组效果为什么没出现）。
     */
    public static void logAvailableEffects() {
        CommandManager.log("可用效果（/effect 候选）：" + universalEffectNames());
    }
}
