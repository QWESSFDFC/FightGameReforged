package cn.gfhnv.game.system.command;

import cn.gfhnv.game.Thing;
import cn.gfhnv.game.entity.Entity;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * 实体选择器：一组被命令选中的实体。
 * <p>
 * 支持《我的世界》Java 版风格的写法：
 * <pre>{@code
 * @s                     执行者自己（玩家选的生物）
 * @p                     离执行者最近的生物
 * @n                     离执行者最远的生物
 * @r                     随机一个生物
 * @a                     全部生物（等价于 @e[type=LivingThing]）
 * @e                     全部生物
 * @e[type=Phainon]       按类型筛选（简单类名，大小写不敏感，也支持中文名）
 * @e[name=白厄]           按名字筛选（支持 * 通配）
 * @e[limit=2,sort=nearest]
 * @e[type=CommonInsect,limit=1]
 * }</pre>
 * <p>
 * 中括号里的筛选键可以组合，用逗号分隔；未知键会直接报错，避免写错了却静默忽略。
 * 「最近的生物」按 {@link Thing#getPosition()} 计算距离；若目标没有有效坐标，
 * 则退化为「列表顺序」。
 * <p>
 * 建议通过 {@link EntityArgumentType#entity()} / {@link EntityArgumentType#entities()}
 * 使用本类，而不要在命令里手写字符串解析。
 *
 * @author AI（DeepSeek）生成
 */
public class EntitySelector {

    /**
     * 选择器类型。不同来源（{@code @} 后面的一个字符）对应不同语义。
     */
    public enum SelectorKind {
        /**
         * {@code @s}：执行者自己（没有玩家时退化为全部生物的当前战斗列表中的第一个）。
         */
        SELF,
        /**
         * {@code @p}：离执行者最近的生物。
         */
        NEAREST_PLAYER,
        /**
         * {@code @r}：随机一个生物。
         */
        RANDOM,
        /**
         * {@code @a}：全部生物。
         */
        ALL,
        /**
         * {@code @e}：全部生物（可带筛选）。
         */
        ALL_ENTITIES,
        /**
         * {@code @n}：离执行者最远的生物。
         */
        FURTHEST
    }

    /**
     * 多结果时的排序方式（对应 {@code sort=}）。
     */
    public enum SortMode {
        /**
         * 按距离由近到远。
         */
        NEAREST,
        /**
         * 按距离由远到近。
         */
        FURTHEST,
        /**
         * 随机顺序。
         */
        RANDOM,
        /**
         * {@code arbitrary}：保持列表原本的顺序。
         */
        ARBITRARY
    }

    /**
     * 选择器类型（{@code @} 后面的字符决定）。
     */
    private SelectorKind kind = SelectorKind.ALL_ENTITIES;

    /**
     * 类型筛选（{@code type=}），{@code null} 表示不筛选。
     */
    private String typeFilter = null;

    /**
     * 名字筛选（{@code name=}），{@code null} 表示不筛选。
     */
    private String nameFilter = null;

    /**
     * 数量上限（{@code limit=}），负数表示不限制。
     */
    private int limit = -1;

    /**
     * 排序方式。
     */
    private SortMode sort = SortMode.NEAREST;

    /**
     * 解析出的实体列表（由 {@link #resolve} 填充）。
     */
    private List<Entity> targets = new ArrayList<>();

    /**
     * 未被解析时保留的原始文本（用于报错与调试输出）。
     */
    private String rawText = "";

    /**
     * 构造一个空选择器。
     */
    public EntitySelector() {
    }

    /* ------------------------------------------------------------------
     * 解析
     * ------------------------------------------------------------------ */

    /**
     * 从文本解析选择器语法（不解析出实体，只解析「怎么选」）。
     * <p>
     * 解析完成后必须调用 {@link #resolve(CommandSelectorContext)} 才会得到实体列表。
     *
     * @param text 选择器文本，如 {@code @e[type=Phainon,limit=1]}
     * @return 解析后的选择器
     * @throws CommandSyntaxException 语法错误时抛出
     */
    public static EntitySelector fromString(String text) throws CommandSyntaxException {
        if (text == null) {
            throw CommandSyntaxException.create("实体选择器不能为空");
        }
        EntitySelector selector = new EntitySelector();
        selector.rawText = text;
        String trimmed = text.trim();
        if (!trimmed.startsWith("@")) {
            throw CommandSyntaxException.create("实体选择器必须以 @ 开头，例如 @s、@a、@e[type=XXX]");
        }
        if (trimmed.length() < 2) {
            throw CommandSyntaxException.create("@ 后面必须跟选择器字符，例如 s、p、r、a、e、n");
        }

        char kindChar = Character.toLowerCase(trimmed.charAt(1));
        selector.kind = kindOf(kindChar, trimmed);

        // 解析中括号里的筛选条件
        int bracketStart = trimmed.indexOf('[');
        if (bracketStart < 0) {
            return selector;
        }
        if (!trimmed.endsWith("]")) {
            throw CommandSyntaxException.create("选择器的中括号没有闭合：" + trimmed
                    + "（码点 " + cn.gfhnv.game.system.command.CommandManager.codePointsOf(trimmed) + "）");
        }
        String inside = trimmed.substring(bracketStart + 1, trimmed.length() - 1);
        for (String pair : inside.split(",")) {
            String item = pair.trim();
            if (item.isEmpty()) {
                continue;
            }
            int equalsIndex = item.indexOf('=');
            if (equalsIndex <= 0 || equalsIndex == item.length() - 1) {
                throw CommandSyntaxException.create("筛选条件必须写成 键=值 的形式，但读到「" + item + "」");
            }
            String key = item.substring(0, equalsIndex).trim().toLowerCase();
            String value = item.substring(equalsIndex + 1).trim();
            switch (key) {
                case "type" -> selector.typeFilter = value;
                case "name" -> selector.nameFilter = value;
                case "limit", "count" -> selector.limit = parseLimit(value);
                case "sort" -> selector.sort = parseSort(value);
                default -> throw CommandSyntaxException.create(
                        "未知的筛选键「" + key + "」，可用：type、name、limit、sort");
            }
        }
        return selector;
    }

    /**
     * 把选择器字符转换成类型。
     *
     * @param kindChar 选择器字符（已转小写）
     * @param raw      原始文本（用于报错）
     * @return 选择器类型
     * @throws CommandSyntaxException 字符不认识时抛出
     */
    private static SelectorKind kindOf(char kindChar, String raw) throws CommandSyntaxException {
        return switch (kindChar) {
            case 's' -> SelectorKind.SELF;
            case 'p' -> SelectorKind.NEAREST_PLAYER;
            case 'r' -> SelectorKind.RANDOM;
            case 'a' -> SelectorKind.ALL;
            case 'e' -> SelectorKind.ALL_ENTITIES;
            case 'n' -> SelectorKind.FURTHEST;
            default -> throw CommandSyntaxException.create(
                    "不支持的实体选择器「@" + kindChar + "」（在 " + raw + " 中），可用：@s @p @r @a @e @n");
        };
    }

    /**
     * 解析 {@code limit=} 的值。
     *
     * @param value 文本
     * @return 数量上限
     * @throws CommandSyntaxException 不是合法整数时抛出
     */
    private static int parseLimit(String value) throws CommandSyntaxException {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                throw CommandSyntaxException.create("limit 不能是负数：" + value);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw CommandSyntaxException.create("limit 必须是整数，但读到「" + value + "」");
        }
    }

    /**
     * 解析 {@code sort=} 的值。
     *
     * @param value 文本
     * @return 排序方式
     * @throws CommandSyntaxException 不认识的值时抛出
     */
    private static SortMode parseSort(String value) throws CommandSyntaxException {
        return switch (value.toLowerCase()) {
            case "nearest" -> SortMode.NEAREST;
            case "furthest" -> SortMode.FURTHEST;
            case "random" -> SortMode.RANDOM;
            case "arbitrary" -> SortMode.ARBITRARY;
            default -> throw CommandSyntaxException.create(
                    "未知的排序方式「" + value + "」，可用：nearest、furthest、random、arbitrary");
        };
    }

    /* ------------------------------------------------------------------
     * 求解
     * ------------------------------------------------------------------ */

    /**
     * 按当前选择器设置，从上下文里求解出具体实体列表。
     * <p>
     * 结果会写入 {@link #getTargets()}。
     *
     * @param context 求解上下文（提供执行者、当前战斗、世界里全部运行时对象）
     * @throws CommandSyntaxException 一个实体都没选中时抛出
     */
    public void resolve(CommandSelectorContext context) throws CommandSyntaxException {
        CommandSelectorContext ctx = context == null ? CommandSelectorContext.EMPTY : context;
        List<Entity> candidates = new ArrayList<>(switch (kind) {
            case SELF -> {
                List<Entity> single = new ArrayList<>();
                if (ctx.getPlayer() != null) {
                    single.add(ctx.getPlayer());
                } else {
                    // 没有玩家（例如还在选人界面）时退化为「当前战斗里的第一个生物」，
                    // 再退化为世界里的第一个生物；都没有就交给下面的空判断统一报错。
                    List<LivingThing> all = ctx.getFightEntities();
                    if (!all.isEmpty()) {
                        single.add(all.get(0));
                    }
                }
                yield single;
            }
            case ALL, ALL_ENTITIES -> ctx.getFightEntities();
            case NEAREST_PLAYER, FURTHEST, RANDOM -> ctx.getFightEntities();
        });

        // 筛选
        candidates.removeIf(entity -> !matchesType(entity) || !matchesName(entity));

        // 排序
        switch (kind) {
            case NEAREST_PLAYER -> sortByDistance(candidates, ctx.getPlayer(), false);
            case FURTHEST -> sortByDistance(candidates, ctx.getPlayer(), true);
            case RANDOM -> {
                if (candidates.size() > 1) {
                    java.util.Collections.shuffle(candidates, new Random());
                }
            }
            default -> {
                if (sort != SortMode.ARBITRARY) {
                    sortByDistance(candidates, ctx.getPlayer(), sort == SortMode.FURTHEST);
                }
            }
        }

        int effectiveLimit = limit;
        if (kind == SelectorKind.NEAREST_PLAYER || kind == SelectorKind.FURTHEST || kind == SelectorKind.RANDOM) {
            if (effectiveLimit < 0) {
                effectiveLimit = 1;
            }
        }
        if (effectiveLimit >= 0 && candidates.size() > effectiveLimit) {
            candidates = new ArrayList<>(candidates.subList(0, effectiveLimit));
        }

        if (candidates.isEmpty()) {
            throw CommandSyntaxException.create("选择器「" + rawText + "」没有选中任何生物"
                    + describeEmptyReason(ctx));
        }
        this.targets = candidates;
    }

    /**
     * 为空结果补充一句「为什么是空的」，避免玩家面对「没有选中任何生物」一头雾水。
     *
     * @param ctx 求解上下文
     * @return 补充说明（可能为空串）
     */
    private String describeEmptyReason(CommandSelectorContext ctx) {
        List<LivingThing> all = ctx.getFightEntities();
        if (all.isEmpty()) {
            if (ctx.getFight() == null) {
                return "（当前没有战斗，世界里也没有已加入的运行时生物）";
            }
            return "（当前战斗里的生物已经全部死亡，或者还没有加入任何生物）";
        }
        if (typeFilter != null || nameFilter != null) {
            StringBuilder filter = new StringBuilder();
            if (typeFilter != null) {
                filter.append("type=").append(typeFilter);
            }
            if (nameFilter != null) {
                if (filter.length() > 0) {
                    filter.append(",");
                }
                filter.append("name=").append(nameFilter);
            }
            return "（筛选条件 " + filter + " 没有命中，当前战斗里的生物有 "
                    + EntityArgumentType.describe(all) + "）";
        }
        return "（当前战斗里的生物有 " + EntityArgumentType.describe(all) + "）";
    }

    /**
     * 把列表按到「参照实体」的距离排序。
     *
     * @param list       待排序列表
     * @param reference  参照实体（可为 {@code null}）
     * @param furthest   {@code true} 表示由远到近，{@code false} 表示由近到远
     */
    private static void sortByDistance(List<Entity> list, Entity reference, boolean furthest) {
        if (list.size() < 2) {
            return;
        }
        double refX = 0;
        double refY = 0;
        double refZ = 0;
        boolean hasReference = reference != null;
        if (hasReference) {
            refX = reference.getPosition().getX();
            refY = reference.getPosition().getY();
            refZ = reference.getPosition().getZ();
        }
        final double rx = refX;
        final double ry = refY;
        final double rz = refZ;
        Comparator<Entity> comparator = Comparator.comparingDouble(entity -> {
            if (!hasReference || entity == null) {
                return 0;
            }
            double dx = entity.getPosition().getX() - rx;
            double dy = entity.getPosition().getY() - ry;
            double dz = entity.getPosition().getZ() - rz;
            return dx * dx + dy * dy + dz * dz;
        });
        if (furthest) {
            comparator = comparator.reversed();
        }
        list.sort(comparator);
    }

    /**
     * 判断实体是否通过 {@code type=} 筛选。
     * <p>
     * 依次尝试：简单类名（不区分大小写）→ 全限定类名 → 显示名（中文名）→ id →
     * 简单类名包含关系。若原样都没命中，还会尝试一组「编码容错」的候选写法
     * （见 {@link #encodingFallbacks(String)}），用来对付 Windows 控制台把中文
     * 输入弄成乱码的情况（例如 {@code 虫皇} 被发成 {@code pp}）。
     *
     * @param entity 实体
     * @return 是否通过
     */
    private boolean matchesType(Entity entity) {
        if (typeFilter == null || typeFilter.isEmpty()) {
            return true;
        }
        if (entity == null) {
            return false;
        }
        for (String filter : candidatesOf(typeFilter)) {
            if (matchesTypeExactly(entity, filter)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断实体是否精确匹配某个类型筛选词。
     *
     * @param entity 实体
     * @param filter 筛选词
     * @return 是否匹配
     */
    private static boolean matchesTypeExactly(Entity entity, String filter) {
        String simpleName = entity.getClass().getSimpleName();
        String fullName = entity.getClass().getName();
        String displayName = entity.getName() == null ? "" : entity.getName();
        String id = entity.getId() == null ? "" : entity.getId();
        return simpleName.equalsIgnoreCase(filter)
                || fullName.equalsIgnoreCase(filter)
                || displayName.equalsIgnoreCase(filter)
                || id.equalsIgnoreCase(filter)
                || simpleName.toLowerCase().contains(filter.toLowerCase());
    }

    /**
     * 生成一组「编码容错候选」。
     * <p>
     * Windows 控制台（cmd.exe，代码页 936）与 JVM 的字符集不一致时，
     * 玩家输入的中文可能被错误解码。这里把原串按「平台默认字符集」和「UTF-8」等几种方式
     * 互相重解释，得到几个候选写法，只要其中任意一个能对上生物的类型/名字就算命中。
     * <p>
     * <b>能力边界</b>：
     * <ul>
     *     <li>有效：字节还在、只是解码方式错了（例如 UTF-8 字节被按 GBK 解出来）；</li>
     *     <li>无效：输入通道已经把中文替换成 {@code ?} 或 {@code \uFFFD}（U+FFFD）——
     *     这时信息已经丢失，任何重解释都救不回来（cmd.exe 里就是这种情况）。</li>
     * </ul>
     * 所以命令行里请优先使用 ASCII 简单类名（{@code @e[type=InsectBoss]}），
     * 或用 Windows Terminal / IDEA 运行以获得可靠的中文输入。
     *
     * @param raw 玩家输入的筛选词
     * @return 候选写法（第一个元素永远是原串本身）
     */
    private static List<String> candidatesOf(String raw) {
        List<String> candidates = new ArrayList<>();
        candidates.add(raw);
        // 整串已经是替换字符时没有任何还原余地，直接返回原串
        if (raw.indexOf('\uFFFD') >= 0 || raw.indexOf('?') >= 0) {
            return candidates;
        }
        try {
            java.nio.charset.Charset platform = java.nio.charset.Charset.defaultCharset();
            candidates.add(new String(raw.getBytes(platform), java.nio.charset.StandardCharsets.UTF_8));
            candidates.add(new String(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8), platform));
            candidates.add(new String(raw.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1),
                    java.nio.charset.StandardCharsets.UTF_8));
            candidates.add(new String(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    java.nio.charset.StandardCharsets.ISO_8859_1));
        } catch (RuntimeException ignored) {
            // 某些字符集不支持时忽略，原串仍然可用
        }
        return candidates;
    }

    /**
     * 判断实体是否通过 {@code name=} 筛选（支持 {@code *} 通配）。
     * <p>
     * 与 {@link #matchesType(Entity)} 一样，会尝试编码容错候选（见 {@link #candidatesOf(String)}）。
     *
     * @param entity 实体
     * @return 是否通过
     */
    private boolean matchesName(Entity entity) {
        if (nameFilter == null || nameFilter.isEmpty()) {
            return true;
        }
        if (entity == null || entity.getName() == null) {
            return false;
        }
        for (String pattern : candidatesOf(nameFilter)) {
            if (matchesNameExactly(entity.getName(), pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断生物名是否匹配某个模式（支持 {@code *} 通配）。
     *
     * @param name    生物名
     * @param pattern 模式
     * @return 是否匹配
     */
    private static boolean matchesNameExactly(String name, String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return false;
        }
        if (pattern.contains("*")) {
            String regex = java.util.regex.Pattern.quote(pattern).replace("*", "\\E.*\\Q");
            return name.matches(regex);
        }
        return name.equalsIgnoreCase(pattern);
    }

    /* ------------------------------------------------------------------
     * 常规访问器
     * ------------------------------------------------------------------ */

    /**
     * @return 解析出的实体列表；未调用过 {@link #resolve} 时为空列表
     */
    public List<Entity> getTargets() {
        return targets;
    }

    /**
     * 设置解析出的实体列表。
     *
     * @param targets 实体列表
     */
    public void setTargets(List<Entity> targets) {
        this.targets = targets == null ? new ArrayList<>() : targets;
    }

    /**
     * 把选中结果按 {@link LivingThing} 过滤出来（击杀、加血等只对生物有意义）。
     *
     * @return 生物列表
     */
    public List<LivingThing> getLivingTargets() {
        List<LivingThing> living = new ArrayList<>();
        for (Entity entity : targets) {
            if (entity instanceof LivingThing livingThing) {
                living.add(livingThing);
            }
        }
        return living;
    }

    /**
     * @return 选择器类型
     */
    public SelectorKind getKind() {
        return kind;
    }

    /**
     * @return 类型筛选条件；没有则为 {@code null}
     */
    public String getTypeFilter() {
        return typeFilter;
    }

    /**
     * @return 名字筛选条件；没有则为 {@code null}
     */
    public String getNameFilter() {
        return nameFilter;
    }

    /**
     * @return 数量上限；负数表示不限制
     */
    public int getLimit() {
        return limit;
    }

    /**
     * @return 排序方式
     */
    public SortMode getSort() {
        return sort;
    }

    /**
     * @return 原始选择器文本
     */
    public String getRawText() {
        return rawText;
    }

    /**
     * @return 本次选择命中的实体数量
     */
    public int size() {
        return targets.size();
    }

    /**
     * @return 结果是否为空
     */
    public boolean isEmpty() {
        return targets.isEmpty();
    }

    /**
     * 直接把文本解析成实体列表（{@link #fromString} + {@link #resolve} 的组合）。
     *
     * @param text    选择器文本
     * @param context 求解上下文
     * @return 选中的实体列表
     * @throws CommandSyntaxException 语法错误或没有选中实体时抛出
     */
    public static List<Entity> resolveText(String text, CommandSelectorContext context) throws CommandSyntaxException {
        EntitySelector selector = fromString(text);
        selector.resolve(context);
        return selector.getTargets();
    }

    @Override
    public String toString() {
        return "EntitySelector{" + rawText + ", targets=" + targets.size() + "}";
    }

    /**
     * 选择器的求解上下文：告诉选择器「谁是执行者」「当前在打哪一场」。
     * <p>
     * 本类只做数据承载，不含逻辑；由 {@link CommandContext#getSelectorContext()} 提供实例。
     *
     * @author AI（DeepSeek）生成
     */
    public static class CommandSelectorContext {

        /**
         * 空上下文（没有玩家、没有战斗、世界里也没有运行时对象）。
         */
        public static final CommandSelectorContext EMPTY = new CommandSelectorContext(null, null, new ArrayList<>());

        /**
         * 执行命令的生物（通常是玩家选的角色），可为 {@code null}。
         */
        private final LivingThing player;

        /**
         * 当前战斗，可为 {@code null}（例如在选人界面执行命令）。
         */
        private final Fight fight;

        /**
         * 世界里的全部运行时对象。
         */
        private final List<Thing> allThings;

        /**
         * 构造一个求解上下文。
         *
         * @param player    执行者，可为 {@code null}
         * @param fight     当前战斗，可为 {@code null}
         * @param allThings 世界运行时对象列表
         */
        public CommandSelectorContext(LivingThing player, Fight fight, List<Thing> allThings) {
            this.player = player;
            this.fight = fight;
            this.allThings = allThings == null ? new ArrayList<>() : allThings;
        }

        /**
         * @return 执行者；可能为 {@code null}
         */
        public LivingThing getPlayer() {
            return player;
        }

        /**
         * @return 当前战斗；可能为 {@code null}
         */
        public Fight getFight() {
            return fight;
        }

        /**
         * @return 当前战斗中的全部生物；没有战斗时返回世界里的全部生物
         */
        public List<LivingThing> getFightEntities() {
            List<LivingThing> result = new ArrayList<>();
            if (fight != null && fight.getAllEntities() != null) {
                for (LivingThing livingThing : fight.getAllEntities()) {
                    if (livingThing != null) {
                        result.add(livingThing);
                    }
                }
            }
            if (result.isEmpty()) {
                for (Thing thing : allThings) {
                    if (thing instanceof LivingThing livingThing) {
                        result.add(livingThing);
                    }
                }
            }
            return result;
        }

        /**
         * @return 世界里全部运行时对象中，属于指定类型的对象
         * @param <T> 目标类型
         * @param type 目标类型
         */
        public <T> List<T> getThingsOfType(Class<T> type) {
            List<T> result = new ArrayList<>();
            for (Thing thing : allThings) {
                if (type.isInstance(thing)) {
                    result.add(type.cast(thing));
                }
            }
            return result;
        }

        /**
         * 从世界注册表里按 id 查一个物品（用于 {@code give} 之类的命令）。
         *
         * @param id 物品 id
         * @return 物品模板；找不到返回 {@code null}
         */
        public cn.gfhnv.game.item.Item findItemById(String id) {
            if (id == null || id.isBlank()) {
                return null;
            }
            for (cn.gfhnv.game.item.Item item : World.getItemList()) {
                if (id.equalsIgnoreCase(item.getId()) || id.equalsIgnoreCase(item.getName())) {
                    return item;
                }
            }
            return null;
        }
    }
}
