package cn.gfhnv.game.officialStuff.customCommands;

import cn.gfhnv.game.data.DataBridge;
import cn.gfhnv.game.data.DataPath;
import cn.gfhnv.game.data.DataStorage;
import cn.gfhnv.game.data.NbtCompound;
import cn.gfhnv.game.data.NbtList;
import cn.gfhnv.game.data.NbtTag;
import cn.gfhnv.game.data.Snbt;
import cn.gfhnv.game.entity.Entity;
import cn.gfhnv.game.system.command.ArgumentBuilder;
import cn.gfhnv.game.system.command.Command;
import cn.gfhnv.game.system.command.CommandContext;
import cn.gfhnv.game.system.command.CommandNode;
import cn.gfhnv.game.system.command.CommandSource;
import cn.gfhnv.game.system.command.CommandSyntaxException;
import cn.gfhnv.game.system.command.EntityArgumentType;
import cn.gfhnv.game.system.command.IntegerArgumentType;
import cn.gfhnv.game.system.command.LiteralCommandNode;
import cn.gfhnv.game.system.command.StringReader;
import cn.gfhnv.game.system.command.WordArgumentType;

import java.util.List;

/**
 * {@code /data} —— 像 MC 那样读写"NBT 数据"。
 * <p>
 * 两种目标：
 * <ul>
 *     <li><b>{@code entity <目标>}</b>：游戏对象的数据视图（反射桥，写回走 setter 优先）；</li>
 *     <li><b>{@code storage <存储位>}</b>：内存里的全局数据（{@link DataStorage}，
 *     <b>退出游戏就没了</b> —— 用户明确说存档不必做）。</li>
 * </ul>
 * 用法：
 * <pre>
 * /data get entity @s                                看自己的全部数据（一整个复合标签）
 * /data get entity @s hp                             看某一个字段
 * /data get entity @s manas[3].amount                列表元素里的字段
 * /data get entity @s inventory.slots[{slotNumber:0L}]        {k:v} 过滤
 * /data get entity @s manas[0:2]                     切片（只读）
 * /data merge entity @s {hp:100}                     改字段（走 setter，会被钳制）
 * /data modify entity @s ignition set 7              改到深处
 * /data modify entity @s effects[0].effectTagsList append POSITIVE
 * /data merge storage 计数 {kill:1}                  内存存储位
 * /data modify storage 计数 kill set 5
 * </pre>
 * <p>
 * <b>写法上的放宽</b>（与 MC 的差别，都是故意的）：字符串可以不加引号（{@code {name:白厄}}）；
 * 空列表/空复合标签不显示；过滤本版只支持单键。
 *
 * @author AI（DeepSeek）生成
 */
public class DataCommand extends Command {

    /**
     * 构造 {@code /data} 命令。
     */
    public DataCommand() {
        super("data");
    }

    /**
     * 构建命令树。
     *
     * @return 命令根节点
     */
    @Override
    protected CommandNode buildNode() {
        LiteralCommandNode root = node();

        /* ================= get ================= */
        ArgumentBuilder get = ArgumentBuilder.literalBuilder("get");

        ArgumentBuilder getEntity = get.literal("entity");
        ArgumentBuilder getTarget = getEntity.argument("目标", EntityArgumentType.entities());
        getTarget.executes((context, source) -> {
            List<Entity> entities = requireEntities(context, "目标");
            for (Entity entity : entities) {
                source.sendMessage(EntityArgumentType.nameOf(entity) + " 的实体数据："
                        + DataBridge.toCompound(entity).toSnbt());
            }
            return entities.size();
        });
        ArgumentBuilder getPath = getTarget.argument("路径", DataCommand::readPath);
        getPath.executes((context, source) -> {
            List<Entity> entities = requireEntities(context, "目标");
            DataPath path = context.getArgument("路径", DataPath.class);
            for (Entity entity : entities) {
                NbtCompound data = DataBridge.toCompound(entity);
                NbtTag value = path.get(data);
                if (value == null) {
                    throw CommandSyntaxException.create(EntityArgumentType.nameOf(entity) + " 没有数据「" + path.describe()
                            + "」：" + path.explainMissing(data));
                }
                source.sendMessage(EntityArgumentType.nameOf(entity) + " 的 " + path.describe() + " = " + value.toSnbt());
            }
            return entities.size();
        });

        ArgumentBuilder getStorage = get.literal("storage");
        ArgumentBuilder getStorageId = getStorage.argument("存储位", WordArgumentType.word());
        getStorageId.executes((context, source) -> {
            String id = context.getArgument("存储位", String.class);
            NbtCompound store = DataStorage.get(id);
            if (store == null) {
                throw CommandSyntaxException.create("没有存储位「" + id + "」（用 /data merge storage " + id + " {…} 建一个）");
            }
            source.sendMessage("存储位「" + id + "」的数据：" + store.toSnbt());
            return 1;
        });
        ArgumentBuilder getStoragePath = getStorageId.argument("路径", DataCommand::readPath);
        getStoragePath.executes((context, source) -> {
            String id = context.getArgument("存储位", String.class);
            DataPath path = context.getArgument("路径", DataPath.class);
            NbtCompound store = DataStorage.get(id);
            NbtTag value = store == null ? null : path.get(store);
            if (value == null) {
                throw CommandSyntaxException.create("存储位「" + id + "」里没有「" + path.describe() + "」："
                        + (store == null ? "没有这个存储位" : path.explainMissing(store)));
            }
            source.sendMessage("存储位「" + id + "」的 " + path.describe() + " = " + value.toSnbt());
            return 1;
        });
        root.addChild(get);

        /* ================= merge ================= */
        ArgumentBuilder merge = ArgumentBuilder.literalBuilder("merge");

        ArgumentBuilder mergeEntity = merge.literal("entity");
        ArgumentBuilder mergeTarget = mergeEntity.argument("目标", EntityArgumentType.entities());
        ArgumentBuilder mergeValue = mergeTarget.argument("NBT", DataCommand::readCompound);
        mergeValue.executes((context, source) -> {
            List<Entity> entities = requireEntities(context, "目标");
            NbtCompound patch = context.getArgument("NBT", NbtCompound.class);
            int applied = 0;
            for (Entity entity : entities) {
                NbtCompound before = DataBridge.toCompound(entity);
                int changed;
                try {
                    changed = DataBridge.merge(entity, patch);
                } catch (IllegalArgumentException e) {
                    throw CommandSyntaxException.create(EntityArgumentType.nameOf(entity) + "：" + e.getMessage());
                }
                NbtCompound after = DataBridge.toCompound(entity);
                source.sendMessage(EntityArgumentType.nameOf(entity) + " 合并 " + changed + " 个键："
                        + describeChanges(before, after, patch));
                applied += changed;
            }
            return applied;
        });

        ArgumentBuilder mergeStorage = merge.literal("storage");
        ArgumentBuilder mergeStorageId = mergeStorage.argument("存储位", WordArgumentType.word());
        ArgumentBuilder mergeStorageValue = mergeStorageId.argument("NBT", DataCommand::readCompound);
        mergeStorageValue.executes((context, source) -> {
            String id = context.getArgument("存储位", String.class);
            NbtCompound patch = context.getArgument("NBT", NbtCompound.class);
            NbtCompound store = DataStorage.of(id);
            NbtCompound before = copyOf(store);
            DataPath.root().mergeIn(store, patch);
            source.sendMessage("存储位「" + id + "」合并 " + patch.size() + " 个键："
                    + describeChanges(before, store, patch));
            return patch.size();
        });
        root.addChild(merge);

        /* ================= modify ================= */
        ArgumentBuilder modify = ArgumentBuilder.literalBuilder("modify");

        ArgumentBuilder modifyEntity = modify.literal("entity");
        ArgumentBuilder modifyTarget = modifyEntity.argument("目标", EntityArgumentType.entities());
        ArgumentBuilder modifyPath = modifyTarget.argument("路径", DataCommand::readPath);

        // entity: set
        ArgumentBuilder setValue = modifyPath.literal("set").argument("值", DataCommand::readTag);
        setValue.executes((context, source) -> {
            List<Entity> entities = requireEntities(context, "目标");
            DataPath path = context.getArgument("路径", DataPath.class);
            NbtTag value = context.getArgument("值", NbtTag.class);
            for (Entity entity : entities) {
                NbtTag before = readTagAt(entity, path);
                try {
                    DataBridge.setAt(entity, path, value);
                } catch (IllegalArgumentException e) {
                    throw CommandSyntaxException.create(EntityArgumentType.nameOf(entity) + "：" + e.getMessage());
                }
                source.sendMessage(EntityArgumentType.nameOf(entity) + " 的 " + path.describe()
                        + "：" + text(before) + " → " + text(readTagAt(entity, path)));
            }
            return entities.size();
        });
        // entity: merge
        ArgumentBuilder mergeValueAt = modifyPath.literal("merge").argument("NBT", DataCommand::readCompound);
        mergeValueAt.executes((context, source) -> {
            List<Entity> entities = requireEntities(context, "目标");
            DataPath path = context.getArgument("路径", DataPath.class);
            NbtCompound patch = context.getArgument("NBT", NbtCompound.class);
            for (Entity entity : entities) {
                NbtTag before = readTagAt(entity, path);
                int changed;
                try {
                    changed = DataBridge.mergeAt(entity, path, patch);
                } catch (IllegalArgumentException e) {
                    throw CommandSyntaxException.create(EntityArgumentType.nameOf(entity) + "：" + e.getMessage());
                }
                NbtTag after = readTagAt(entity, path);
                String detail = (before instanceof NbtCompound beforeCompound && after instanceof NbtCompound afterCompound)
                        ? describeChanges(beforeCompound, afterCompound, patch)
                        : "（" + path.describe() + "）" + text(before) + " → " + text(after);
                source.sendMessage(EntityArgumentType.nameOf(entity) + " 的 " + path.describe()
                        + " 合并 " + changed + " 个键：" + detail);
            }
            return entities.size();
        });
        // entity: append / prepend / insert
        addEntityListOperation(modifyPath, "append", Integer.MAX_VALUE);
        addEntityListOperation(modifyPath, "prepend", 0);
        ArgumentBuilder insertIndex = modifyPath.literal("insert").argument("下标", IntegerArgumentType.integer());
        ArgumentBuilder insertValue = insertIndex.argument("值", DataCommand::readTag);
        insertValue.executes((context, source) -> {
            List<Entity> entities = requireEntities(context, "目标");
            DataPath path = context.getArgument("路径", DataPath.class);
            int index = context.getArgument("下标", Integer.class);
            NbtTag value = context.getArgument("值", NbtTag.class);
            for (Entity entity : entities) {
                runEntityListOperation(entity, path, "insert", index, value, source);
            }
            return entities.size();
        });

        /* ---- storage：同一套操作，但作用在内存里的标签上 ---- */
        ArgumentBuilder modifyStorage = modify.literal("storage");
        ArgumentBuilder modifyStorageId = modifyStorage.argument("存储位", WordArgumentType.word());
        ArgumentBuilder modifyStoragePath = modifyStorageId.argument("路径", DataCommand::readPath);

        ArgumentBuilder storageSet = modifyStoragePath.literal("set").argument("值", DataCommand::readTag);
        storageSet.executes((context, source) -> {
            String id = context.getArgument("存储位", String.class);
            DataPath path = context.getArgument("路径", DataPath.class);
            NbtTag value = context.getArgument("值", NbtTag.class);
            NbtCompound store = DataStorage.of(id);
            NbtTag before = path.get(store);
            try {
                path.setIn(store, value);
            } catch (IllegalArgumentException e) {
                throw CommandSyntaxException.create("存储位「" + id + "」：" + e.getMessage());
            }
            source.sendMessage("存储位「" + id + "」的 " + path.describe()
                    + "：" + text(before) + " → " + text(path.get(store)));
            return 1;
        });
        ArgumentBuilder storageMerge = modifyStoragePath.literal("merge").argument("NBT", DataCommand::readCompound);
        storageMerge.executes((context, source) -> {
            String id = context.getArgument("存储位", String.class);
            DataPath path = context.getArgument("路径", DataPath.class);
            NbtCompound patch = context.getArgument("NBT", NbtCompound.class);
            NbtCompound store = DataStorage.of(id);
            NbtTag before = path.get(store);
            try {
                path.mergeIn(store, patch);
            } catch (IllegalArgumentException e) {
                throw CommandSyntaxException.create("存储位「" + id + "」：" + e.getMessage());
            }
            source.sendMessage("存储位「" + id + "」的 " + path.describe() + " 合并 " + patch.size() + " 个键："
                    + text(before) + " → " + text(path.get(store)));
            return patch.size();
        });
        addStorageListOperation(modifyStoragePath, "append", Integer.MAX_VALUE);
        addStorageListOperation(modifyStoragePath, "prepend", 0);
        ArgumentBuilder storageInsertIndex = modifyStoragePath.literal("insert").argument("下标", IntegerArgumentType.integer());
        ArgumentBuilder storageInsertValue = storageInsertIndex.argument("值", DataCommand::readTag);
        storageInsertValue.executes((context, source) -> {
            String id = context.getArgument("存储位", String.class);
            DataPath path = context.getArgument("路径", DataPath.class);
            int index = context.getArgument("下标", Integer.class);
            NbtTag value = context.getArgument("值", NbtTag.class);
            NbtCompound store = DataStorage.of(id);
            int before = listSizeOf(path.get(store));
            int position = index == Integer.MAX_VALUE ? before : index;
            try {
                path.insertIn(store, position, value);
            } catch (IllegalArgumentException e) {
                throw CommandSyntaxException.create("存储位「" + id + "」：" + e.getMessage());
            }
            source.sendMessage("存储位「" + id + "」的 " + path.describe() + " insert："
                    + before + " → " + listSizeOf(path.get(store)) + " 个元素");
            return 1;
        });

        root.addChild(modify);
        return root;
    }

    /* ------------------------------------------------------------------
     * 实体：列表增删
     * ------------------------------------------------------------------ */

    /**
     * 往 {@code modify entity <目标> <路径>} 底下挂一条"塞元素"的分支。
     * <p>
     * <b>没有返回值</b>：节点在 {@code parent.literal(...)} 时就挂好了，调用方不需要（也不应该）
     * 再把它挂到别处。
     *
     * @param parent     父节点
     * @param literal    {@code append} / {@code prepend}
     * @param fixedIndex 固定插入位置
     */
    private static void addEntityListOperation(ArgumentBuilder parent, String literal, int fixedIndex) {
        ArgumentBuilder value = parent.literal(literal).argument("值", DataCommand::readTag);
        value.executes((context, source) -> {
            List<Entity> entities = requireEntities(context, "目标");
            DataPath path = context.getArgument("路径", DataPath.class);
            NbtTag tag = context.getArgument("值", NbtTag.class);
            for (Entity entity : entities) {
                runEntityListOperation(entity, path, literal, fixedIndex, tag, source);
            }
            return entities.size();
        });
    }

    /**
     * 往 {@code modify storage <存储位> <路径>} 底下挂一条"塞元素"的分支。
     *
     * @param parent     父节点
     * @param literal    {@code append} / {@code prepend}
     * @param fixedIndex 固定插入位置
     */
    private static void addStorageListOperation(ArgumentBuilder parent, String literal, int fixedIndex) {
        ArgumentBuilder value = parent.literal(literal).argument("值", DataCommand::readTag);
        value.executes((context, source) -> {
            String id = context.getArgument("存储位", String.class);
            DataPath path = context.getArgument("路径", DataPath.class);
            NbtTag tag = context.getArgument("值", NbtTag.class);
            NbtCompound store = DataStorage.of(id);
            int before = listSizeOf(path.get(store));
            int position = fixedIndex == Integer.MAX_VALUE ? before : fixedIndex;
            try {
                path.insertIn(store, position, tag);
            } catch (IllegalArgumentException e) {
                throw CommandSyntaxException.create("存储位「" + id + "」：" + e.getMessage());
            }
            source.sendMessage("存储位「" + id + "」的 " + path.describe() + " " + literal + "："
                    + before + " → " + listSizeOf(path.get(store)) + " 个元素");
            return 1;
        });
    }

    /**
     * 对实体执行一次列表插入并回显"元素个数变化"。
     *
     * @param entity 目标实体
     * @param path   路径（指向列表）
     * @param what   操作名（回显用）
     * @param index  插入位置；{@link Integer#MAX_VALUE} 表示追加到末尾
     * @param value  元素
     * @param source 输出
     * @throws CommandSyntaxException 路径不是标量列表 / 下标越界
     */
    private static void runEntityListOperation(Entity entity, DataPath path, String what, int index, NbtTag value,
                                               CommandSource source) throws CommandSyntaxException {
        int before = listSizeAt(entity, path);
        int position = index == Integer.MAX_VALUE ? before : index;
        try {
            DataBridge.insertAt(entity, path, position, value);
        } catch (IllegalArgumentException e) {
            throw CommandSyntaxException.create(EntityArgumentType.nameOf(entity) + "：" + e.getMessage());
        }
        source.sendMessage(EntityArgumentType.nameOf(entity) + " 的 " + path.describe()
                + " " + what + "：" + before + " → " + listSizeAt(entity, path) + " 个元素");
    }

    /* ------------------------------------------------------------------
     * 小工具
     * ------------------------------------------------------------------ */

    /**
     * @param context  命令上下文
     * @param argument 参数名
     * @return 选中的实体
     * @throws CommandSyntaxException 一个都没选中
     */
    private static List<Entity> requireEntities(CommandContext context, String argument) throws CommandSyntaxException {
        List<Entity> entities = context.getEntities(argument);
        if (entities.isEmpty()) {
            throw CommandSyntaxException.create("没有选中任何实体（用 /list 看看场上有谁）");
        }
        return entities;
    }

    /**
     * 把"补丁里那几个键的旧值 → 新值"拼成一段回显。
     * <p>
     * 重新读一遍而不是直接回显补丁：{@code setHp} 这类 setter 会钳制数值，
     * 回显真实结果才能看出"你写的 999999 被夹成了 7392"。
     *
     * @param before 合并前的数据
     * @param after  合并后的数据
     * @param patch  补丁
     * @return 文本
     */
    private static String describeChanges(NbtCompound before, NbtCompound after, NbtCompound patch) {
        StringBuilder builder = new StringBuilder();
        for (String key : patch.keySet()) {
            if (builder.length() > 0) {
                builder.append('、');
            }
            NbtTag oldValue = before.get(key);
            NbtTag newValue = after.get(key);
            builder.append(key).append('：').append(oldValue == null ? "（无）" : oldValue.toSnbt())
                    .append(" → ").append(newValue == null ? "（无）" : newValue.toSnbt());
        }
        return builder.length() == 0 ? "（空补丁）" : builder.toString();
    }

    /**
     * @param root 根对象
     * @param path 路径
     * @return 路径指向的列表有几个元素；不是列表返回 {@code -1}
     */
    private static int listSizeAt(Object root, DataPath path) {
        try {
            return listSizeOf(DataBridge.valueAt(root, path));
        } catch (IllegalArgumentException e) {
            return -1;
        }
    }

    /**
     * 数一个值里有几个元素。
     * <p>
     * <b>两种都要认</b>：实体那条路拿到的是<b>活的</b> {@code java.util.List}（反射读出来的字段值），
     * storage 那条路拿到的是 {@link NbtList}。2026-09 只认后者，导致
     * {@code /data modify entity … append} 一律报"下标 -1 越界"（自测抓到）。
     *
     * @param value 值（活列表 / 标签列表 / 别的什么）
     * @return 元素个数；不是列表返回 {@code -1}
     */
    private static int listSizeOf(Object value) {
        if (value instanceof NbtList tagList) {
            return tagList.size();
        }
        return value instanceof List<?> list ? list.size() : -1;
    }

    /**
     * 浅拷贝一份复合标签（回显"合并前"的样子；只拷一层就够，因为合并只会动到这一层）。
     *
     * @param source 原标签
     * @return 新复合标签
     */
    private static NbtCompound copyOf(NbtCompound source) {
        NbtCompound copy = new NbtCompound();
        for (String key : source.keySet()) {
            copy.put(key, source.get(key));
        }
        return copy;
    }

    /**
     * 读路径上的值并转成标签（回显"旧值"用；取不到返回 {@code null}）。
     *
     * @param root 根对象
     * @param path 路径
     * @return 标签；路径不通时返回 {@code null}
     */
    private static NbtTag readTagAt(Object root, DataPath path) {
        try {
            return DataBridge.toTag(DataBridge.valueAt(root, path));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * @param tag 标签
     * @return 显示文本（{@code null} 显示成"（无）"）
     */
    private static String text(NbtTag tag) {
        return tag == null ? "（无）" : tag.toSnbt();
    }

    /**
     * 解析路径参数（{@code hp}、{@code manas[0].amount}、{@code slots[{slotNumber:0L}]}）。
     * <p>
     * <b>公开</b>是为了让 {@code /execute if data …} 复用同一条路径语法（别在那边再写一份）。
     *
     * @param reader 输入读取器
     * @return 路径
     * @throws CommandSyntaxException 路径语法错误
     */
    public static DataPath readPath(StringReader reader) throws CommandSyntaxException {
        String text = reader.readWord();
        try {
            return DataPath.parse(text);
        } catch (IllegalArgumentException e) {
            throw CommandSyntaxException.at(reader, e.getMessage());
        }
    }

    /**
     * 解析 NBT 参数（{@code {hp:20}}，允许括号内带空格）。
     *
     * @param reader 输入读取器
     * @return 复合标签
     * @throws CommandSyntaxException SNBT 语法错误 / 根不是复合标签
     */
    private static NbtCompound readCompound(StringReader reader) throws CommandSyntaxException {
        NbtTag tag = readTag(reader);
        if (!(tag instanceof NbtCompound compound)) {
            throw CommandSyntaxException.at(reader, "这里需要一个复合标签（用 { } 包起来），实际拿到的是 " + tag.type());
        }
        return compound;
    }

    /**
     * 解析任意 NBT 值（数字 / 字符串 / 列表 / 复合标签），供 {@code modify ... set} 用。
     *
     * @param reader 输入读取器
     * @return 标签
     * @throws CommandSyntaxException SNBT 语法错误
     */
    private static NbtTag readTag(StringReader reader) throws CommandSyntaxException {
        String text = reader.readBalanced();
        try {
            return Snbt.parse(text);
        } catch (IllegalArgumentException e) {
            throw CommandSyntaxException.at(reader, e.getMessage());
        }
    }
}
