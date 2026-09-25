package cn.gfhnv.game.officialStuff.customCommands;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.inventory.Inventory;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.officialStuff.OfficialGameContent;
import cn.gfhnv.game.system.command.*;
import cn.gfhnv.game.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /give} —— 给生物发物品。
 * <p>
 * 用法：
 * <pre>
 * /give &lt;目标&gt; &lt;物品&gt;            发 1 个
 * /give &lt;目标&gt; &lt;物品&gt; &lt;数量&gt;     发 N 个
 * </pre>
 * 示例：
 * <pre>
 * /give @s aNiceSword
 * /give @s game_official_content:aNiceSword 3
 * /give @p ANiceSword 2
 * </pre>
 * <p>
 * <b>物品名怎么写</b>（模仿 MC 的命名空间规则，大小写不敏感）：
 * <ul>
 *     <li><b>完整 id</b>（{@code game_official_content:aNiceSword}、{@code drunkenSword:osmanthusWine}）：
 *     任何内容都能这么写，精确匹配；</li>
 *     <li><b>短名</b>（{@code aNiceSword}）与<b>简单类名</b>（{@code ANiceSword}）：
 *     <b>只解析官方内容</b> —— 模组物品必须写完整 id，否则多装几个模组就分不清是谁家的东西。</li>
 * </ul>
 * 写错会报错并列出当前所有可用物品（官方物品列短名，模组物品列它唯一能写的完整 id）；
 * 用短名指向模组物品时会专门提示该写什么。
 * <p>
 * <b>数量会叠进同一格</b>：同一种物品（{@link Item#equals(Object)} 按注册表 id 判定）在背包里共用一格，
 * 堆叠数累加、没有上限 —— 所以 {@code /give @s aNiceSword 100} 只占 1 格（背包一共 63 格）。
 * 背包里已经有的同种物品会继续往上叠。
 * 使用物品时只消耗 1 个（{@code Inventory.removeOne}），不会把整叠一起扣掉。
 * <p>
 * 格子不够时能发多少发多少，剩下的会在回显里说明；
 * 目标没有背包格子（例如普通虫子没初始化背包）时直接报错。
 *
 * @author AI（DeepSeek）生成
 */
public class GiveCommand extends Command {

    /**
     * 单次最多发放的数量。
     */
    private static final int MAX_COUNT = 999;

    /**
     * 构造 {@code /give} 命令。
     */
    public GiveCommand() {
        super("give");
    }

    /**
     * 执行发放。
     *
     * @param context 命令上下文（提供 {@code 目标} 与可选的 {@code 数量}）
     * @param source  命令来源
     * @param count   每个目标发放的数量
     * @return 实际发放出去的总数量
     * @throws CommandSyntaxException 物品不存在、名字有歧义、或一个也没发出去时抛出
     */
    private static int give(CommandContext context, CommandSource source, int count) throws CommandSyntaxException {
        List<LivingThing> targets = context.getLivingThings("目标");
        Item template = findItem(context.getString("物品", null));

        StringBuilder detail = new StringBuilder();
        int addedTotal = 0;
        int failedTotal = 0;
        int noSlotTargets = 0;
        for (LivingThing target : targets) {
            Inventory inventory = target.getInventory();
            if (inventory == null || inventory.getSlots().isEmpty()) {
                // 只有初始化过背包的生物（玩家角色、虫皇）才有格子
                noSlotTargets++;
                continue;
            }
            int added = 0;
            for (int i = 0; i < count; i++) {
                // 发的是副本：物品会带完整的注册表 id；同种物品会在 addItem 里叠进同一格
                if (!inventory.addItem(template.copy())) {
                    break;
                }
                added++;
            }
            addedTotal += added;
            failedTotal += count - added;
            if (detail.length() > 0) {
                detail.append("、");
            }
            detail.append(EntityArgumentType.nameOf(target)).append("（+").append(added).append("）");
        }

        if (addedTotal == 0) {
            if (noSlotTargets == targets.size()) {
                throw CommandSyntaxException.create("目标没有背包格子，给不了物品（目前只有玩家角色和虫皇初始化了背包）："
                        + detail);
            }
            throw CommandSyntaxException.create("背包放不下（格子已满），一件都没发出去：" + detail);
        }

        // 成功回显用短名（给人看，越短越好）；"该怎么写完整 id"由报错里的可用物品列表负责
        // —— 用户 2026-09 拍板，别为了"和 /effect 一致"改成完整 id。
        StringBuilder message = new StringBuilder("已发放 ")
                .append(World.shortIdOf(template)).append("（").append(template.getName()).append("）×")
                .append(addedTotal).append("：").append(detail);
        if (failedTotal > 0) {
            message.append("；有 ").append(failedTotal).append(" 个因背包放不下未发放");
        }
        if (noSlotTargets > 0) {
            message.append("；有 ").append(noSlotTargets).append(" 个目标没有背包格子，已跳过");
        }
        source.sendMessage(message.toString());
        return addedTotal;
    }

    /**
     * 按「完整 id / 短名 / 简单类名」在物品注册表里找模板。
     * <p>
     * 短名与类名<b>只解析官方内容</b>；带 {@code :} 的完整 id 谁都认（见类注释）。
     *
     * @param rawName 玩家输入的物品名
     * @return 注册表里的模板
     * @throws CommandSyntaxException 名字为空、找不到、或有歧义时抛出
     */
    private static Item findItem(String rawName) throws CommandSyntaxException {
        if (rawName == null || rawName.isBlank()) {
            throw CommandSyntaxException.create("没有指定物品。可用物品：" + describeRegistry());
        }
        String name = rawName.trim();
        // 带冒号 = 玩家明确指定了命名空间，按完整 id 匹配
        boolean explicitId = name.indexOf(':') >= 0;
        List<Item> matched = new ArrayList<>();
        List<Item> modOnlyMatched = new ArrayList<>();
        for (Item item : World.getItemList()) {
            if (item == null) {
                continue;
            }
            if (matches(item, name, explicitId)) {
                matched.add(item);
            } else if (!explicitId && isModContent(item) && matchesShortName(item, name)) {
                // 短名其实指向了模组物品，但规则不允许 —— 单独记下来，好在报错里告诉玩家该写什么
                modOnlyMatched.add(item);
            }
        }
        if (matched.isEmpty()) {
            if (!modOnlyMatched.isEmpty()) {
                throw CommandSyntaxException.create("「" + name + "」是模组物品，必须写完整的 id："
                        + idsOf(modOnlyMatched) + "（官方物品才能只写短名）");
            }
            throw CommandSyntaxException.create("物品注册表里没有「" + name + "」。可用物品：" + describeRegistry());
        }
        if (matched.size() > 1) {
            throw CommandSyntaxException.create("「" + name + "」匹配到 " + matched.size()
                    + " 个物品：" + idsOf(matched) + "。请写完整的 id（含模组前缀）");
        }
        return matched.get(0);
    }

    /**
     * 判断注册表里的物品是否匹配玩家输入的名字。
     *
     * @param item       注册表里的物品
     * @param name       玩家输入
     * @param explicitId 玩家是否写了完整 id（输入里带 {@code :}）
     * @return 是否匹配
     */
    private static boolean matches(Item item, String name, boolean explicitId) {
        String id = item.getId() == null ? "" : item.getId();
        if (explicitId) {
            return id.equalsIgnoreCase(name);
        }
        // 短名/类名只解析官方内容：模组物品必须写完整 id
        return !isModContent(item) && matchesShortName(item, name);
    }

    /**
     * 只比「短名」与「简单类名」，不管是不是官方内容（用于给出更准确的报错）。
     *
     * @param item 注册表里的物品
     * @param name 玩家输入
     * @return 是否匹配短名或类名（忽略大小写）
     */
    private static boolean matchesShortName(Item item, String name) {
        return World.shortIdOf(item).equalsIgnoreCase(name)
                || item.getClass().getSimpleName().equalsIgnoreCase(name);
    }

    /**
     * @param item 物品
     * @return 是否由模组（非官方内容）注册
     */
    private static boolean isModContent(Item item) {
        return !OfficialGameContent.isOfficial(item);
    }

    /**
     * 把若干物品的 id 拼成可读文本（没有 id 的退回类名）。
     *
     * @param items 物品列表
     * @return 可读文本
     */
    private static String idsOf(List<Item> items) {
        List<String> ids = new ArrayList<>();
        for (Item item : items) {
            ids.add(item.getId() == null ? item.getClass().getSimpleName() : item.getId());
        }
        return String.join("、", ids);
    }

    /**
     * 列出注册表里所有可用物品（用于报错提示）。
     * <p>
     * 官方物品显示短名（它就能这么写），模组物品显示完整 id（它只能这么写）。
     *
     * @return 可读文本
     */
    private static String describeRegistry() {
        List<String> names = new ArrayList<>();
        for (Item item : World.getItemList()) {
            if (item != null) {
                names.add(displayNameOf(item) + "（" + item.getName() + "）");
            }
        }
        return names.isEmpty() ? "（注册表里目前没有物品）" : String.join("、", names);
    }

    /**
     * @param item 物品
     * @return 报错提示里该怎么称呼它：官方物品用短名，模组物品用完整 id
     */
    private static String displayNameOf(Item item) {
        return isModContent(item) ? String.valueOf(item.getId()) : World.shortIdOf(item);
    }

    /**
     * 构建命令树：{@code give <目标> <物品> [数量]}。
     *
     * @return 命令根节点
     */
    @Override
    protected CommandNode buildNode() {
        LiteralCommandNode root = node();

        ArgumentBuilder target = ArgumentBuilder.argumentBuilder("目标", EntityArgumentType.entities());
        root.addChild(target);

        ArgumentBuilder item = target.argument("物品", WordArgumentType.word());
        // 分支一：/give <目标> <物品> —— 默认 1 个
        item.executes((context, source) -> give(context, source, 1));
        // 分支二：/give <目标> <物品> <数量>
        ArgumentBuilder count = item.argument("数量", IntegerArgumentType.integer(1, MAX_COUNT));
        count.executes((context, source) -> give(context, source, context.getInt("数量", null, 1)));

        return root;
    }
}
