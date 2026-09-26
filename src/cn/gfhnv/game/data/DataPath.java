package cn.gfhnv.game.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * NBT 路径：{@code a.b[0].c}、{@code a[{k:v}].c}、{@code a[0:2].c}。
 * <p>
 * 一段可以是：
 * <ul>
 *     <li>{@link String} —— 复合标签的键 / 对象字段名；</li>
 *     <li>{@link Integer} —— 列表下标（负数不支持）；</li>
 *     <li>{@link Filter} —— {@code [{k:v}]}：挑出字段 {@code k} 等于 {@code v} 的元素
 *     （多个命中时，读的时候返回一个列表，写的时候取第一个）；</li>
 *     <li>{@link Slice} —— {@code [start:end]}：切片，两端都可以省略（{@code [1:]}、{@code [:3]}）。
 *     <b>切片只能读</b>，拿它去写会报错。</li>
 * </ul>
 *
 * @author AI（DeepSeek）生成
 */
public final class DataPath {

    /**
     * 报错时最多列出几种"该键实际出现过的取值"。
     */
    private static final int MAX_PRESENT_VALUES = 8;

    /**
     * 列表过滤段：{@code [{k:v}]}。
     *
     * @param key   要比的字段名
     * @param value 期望的值
     */
    public record Filter(String key, NbtTag value) {

        @Override
        public String toString() {
            return "{" + key + ":" + value.toSnbt() + "}";
        }
    }

    /**
     * 列表切片段：{@code [start:end]}（{@code null} 表示省略那一端）。
     *
     * @param start 起始下标（含）；{@code null} 表示从头
     * @param end   结束下标（不含）；{@code null} 表示到末尾
     */
    public record Slice(Integer start, Integer end) {

        @Override
        public String toString() {
            return "[" + (start == null ? "" : start) + ":" + (end == null ? "" : end) + "]";
        }
    }

    /**
     * 原文本（报错与回显用）。
     */
    private final String source;

    /**
     * 段列表：{@code String} / {@code Integer} / {@link Filter} / {@link Slice}。
     */
    private final List<Object> segments;

    /**
     * @param source   原文本
     * @param segments 段列表
     */
    private DataPath(String source, List<Object> segments) {
        this.source = source;
        this.segments = segments;
    }

    /**
     * @return 空路径（指向根）
     */
    public static DataPath root() {
        return new DataPath("", new ArrayList<>());
    }

    /**
     * 解析路径文本。
     *
     * @param text 例如 {@code hp}、{@code manas[0].amount}、{@code inventory.slots[{slotNumber:0L}]}
     * @return 路径
     * @throws IllegalArgumentException 语法错误
     */
    public static DataPath parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return root();
        }
        String trimmed = text.trim();
        List<Object> segments = new ArrayList<>();
        int i = 0;
        while (i < trimmed.length()) {
            char c = trimmed.charAt(i);
            if (c == '.') {
                i++;
                continue;
            }
            if (c == '[') {
                int close = matchBracket(trimmed, i);
                String inner = trimmed.substring(i + 1, close).trim();
                segments.add(parseBracket(inner, trimmed));
                i = close + 1;
                continue;
            }
            int start = i;
            while (i < trimmed.length() && trimmed.charAt(i) != '.' && trimmed.charAt(i) != '[') {
                i++;
            }
            String key = trimmed.substring(start, i).trim();
            if (key.isEmpty()) {
                throw new IllegalArgumentException("路径里有空的一段：" + trimmed);
            }
            segments.add(key);
        }
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("路径是空的：" + trimmed);
        }
        return new DataPath(trimmed, segments);
    }

    /**
     * 找与 {@code '['} 配对的 {@code ']'}（要跳过 {@code {}} 里的方括号与引号里的内容）。
     *
     * @param text  路径原文
     * @param start {@code '['} 的位置
     * @return 配对的 {@code ']'} 的位置
     * @throws IllegalArgumentException 没有闭合
     */
    private static int matchBracket(String text, int start) {
        int depth = 0;
        char quote = '\0';
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != '\0') {
                if (c == quote) {
                    quote = '\0';
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                continue;
            }
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        throw new IllegalArgumentException("路径里的 [ 没有闭合：" + text);
    }

    /**
     * 解析方括号里的内容：下标 / 过滤 / 切片。
     *
     * @param inner 方括号内的文本
     * @param whole 路径原文（报错用）
     * @return 段
     * @throws IllegalArgumentException 三种都不是
     */
    private static Object parseBracket(String inner, String whole) {
        if (inner.isEmpty()) {
            throw new IllegalArgumentException("路径里有空的 []：" + whole);
        }
        if (inner.startsWith("{")) {
            NbtCompound compound;
            try {
                compound = Snbt.parseCompound(inner);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("路径里的过滤写错了：" + e.getMessage());
            }
            if (compound.size() != 1) {
                throw new IllegalArgumentException("过滤本版只支持单键（形如 {slotNumber:0L}）：[" + inner + "]");
            }
            String key = compound.keySet().iterator().next();
            return new Filter(key, compound.get(key));
        }
        if (inner.indexOf(':') >= 0) {
            String[] parts = inner.split(":", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("切片的写法是 [start:end]：[" + inner + "]");
            }
            return new Slice(parseBound(parts[0], inner), parseBound(parts[1], inner));
        }
        try {
            return Integer.parseInt(inner);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("路径下标只能是数字、{k:v} 过滤或 [start:end] 切片：[" + inner + "]");
        }
    }

    /**
     * @param text  切片的一端
     * @param inner 方括号内文本（报错用）
     * @return 下标；空串返回 {@code null}
     * @throws IllegalArgumentException 不是整数
     */
    private static Integer parseBound(String text, String inner) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("切片的两端只能是数字或留空：[ " + inner + "]");
        }
    }

    /**
     * @return 段列表（{@code String} / {@code Integer} / {@link Filter} / {@link Slice}），只读
     */
    public List<Object> segments() {
        return Collections.unmodifiableList(segments);
    }

    /**
     * @return 是否指向根
     */
    public boolean isRoot() {
        return segments.isEmpty();
    }

    /**
     * @return 原文本
     */
    public String source() {
        return source;
    }

    /**
     * @return 用于回显/报错的文本（根路径显示成 {@code <根>}）
     */
    public String describe() {
        return isRoot() ? "<根>" : source;
    }

    /**
     * 沿路径在标签树里取值。
     *
     * @param root 根标签；{@code null} 直接返回 {@code null}
     * @return 路径指向的标签；任一段不存在则返回 {@code null}
     */
    public NbtTag get(NbtTag root) {
        return walk(root).value();
    }

    /**
     * 路径走不通时，用一句话说明"断在哪一段、为什么"。
     * <p>
     * {@link #get(NbtTag)} 只能返回 {@code null} —— {@code execute if data} 正是靠这个
     * "取不到"的语义做条件判断，所以"为什么取不到"只能另外问一次。
     * 没有它的时候，{@code /data get} 只会报"没有数据「xxx」"，
     * 用的人分不清是字段名写错、下标越界，还是 {@code [{k:v}]} 没命中
     * （实测踩过：想改一个身上还没有的效果，那条报错看起来像语法问题）。
     *
     * @param root 根标签
     * @return 原因；路径其实走得通时返回 {@code null}
     */
    public String explainMissing(NbtTag root) {
        return walk(root).reason();
    }

    /**
     * 走一遍路径：走得通就带回值，走不通就带回原因。
     *
     * @param root 根标签
     * @return 结果
     */
    private Walk walk(NbtTag root) {
        NbtTag current = root;
        for (Object segment : segments) {
            if (current == null) {
                return new Walk(null, "中间有一段是空的");
            }
            if (segment instanceof Integer index) {
                if (!(current instanceof NbtList list)) {
                    return new Walk(null, "下标 " + index + " 前面不是列表（是 " + current.type() + "）");
                }
                if (index < 0 || index >= list.size()) {
                    return new Walk(null, "下标 " + index + " 越界（这个列表有 " + list.size() + " 个元素）");
                }
                current = list.get(index);
            } else if (segment instanceof Filter filter) {
                if (!(current instanceof NbtList list)) {
                    return new Walk(null, "过滤 " + filter + " 前面不是列表（是 " + current.type() + "）");
                }
                NbtTag matched = filterOf(list, filter);
                if (matched == null) {
                    return new Walk(null, "过滤 " + filter + " 没有命中任何元素（列表里 " + filter.key()
                            + " 的取值：" + presentValues(list, filter.key()) + "）");
                }
                current = matched;
            } else if (segment instanceof Slice slice) {
                if (!(current instanceof NbtList list)) {
                    return new Walk(null, "切片 " + slice + " 前面不是列表（是 " + current.type() + "）");
                }
                current = sliceOf(list, slice);
            } else {
                if (!(current instanceof NbtCompound compound)) {
                    return new Walk(null, "「" + segment + "」前面不是复合标签（是 " + current.type() + "）");
                }
                NbtTag child = compound.get(String.valueOf(segment));
                if (child == null) {
                    return new Walk(null, "没有「" + segment + "」这一段（这一层的键有："
                            + presentKeys(compound) + "）");
                }
                current = child;
            }
        }
        return current == null ? new Walk(null, "路径指向的是空值") : new Walk(current, null);
    }

    /**
     * 走一遍路径的结果：要么拿到值，要么拿到"为什么拿不到"。
     *
     * @param value  值；失败时为 {@code null}
     * @param reason 失败原因；成功时为 {@code null}
     */
    private record Walk(NbtTag value, String reason) {
    }

    /**
     * 列出一层复合标签里有哪些键（报错用，字段名打错时最有用）。
     *
     * @param compound 复合标签
     * @return 键清单（截断）
     */
    private static String presentKeys(NbtCompound compound) {
        List<String> keys = new ArrayList<>(compound.values().keySet());
        if (keys.isEmpty()) {
            return "（一个都没有）";
        }
        if (keys.size() > MAX_PRESENT_VALUES) {
            return String.join("、", keys.subList(0, MAX_PRESENT_VALUES)) + " …（共 " + keys.size() + " 个）";
        }
        return String.join("、", keys);
    }

    /**
     * 过滤没命中时，列出这个键在列表里<b>实际出现过哪些值</b>（报错用）。
     * <p>
     * "没有命中"有两种原因：路径写错了，或者<b>你要找的那条根本不在列表里</b>
     * （典型：身上还没有那个效果）。光说"没有命中"会让人以为是语法问题，
     * 把现有取值报出来，一眼就能分辨。
     *
     * @param list 被过滤的列表
     * @param key  过滤用的键
     * @return 取值清单（去重、截断）
     */
    static String presentValues(NbtList list, String key) {
        List<String> seen = new ArrayList<>();
        for (NbtTag element : list.values()) {
            if (!(element instanceof NbtCompound compound)) {
                continue;
            }
            NbtTag value = compound.get(key);
            if (value == null) {
                continue;
            }
            String text = value.toSnbt();
            if (!seen.contains(text)) {
                seen.add(text);
            }
        }
        if (seen.isEmpty()) {
            return "元素里没有「" + key + "」这个键";
        }
        if (seen.size() > MAX_PRESENT_VALUES) {
            return String.join("、", seen.subList(0, MAX_PRESENT_VALUES)) + " …（共 " + seen.size() + " 种）";
        }
        return String.join("、", seen);
    }

    /**
     * 按过滤段挑元素：一个命中就是它本身，多个命中打包成列表，没有命中返回 {@code null}。
     *
     * @param list   列表
     * @param filter 过滤条件
     * @return 命中的元素 / 元素列表 / {@code null}
     */
    private static NbtTag filterOf(NbtList list, Filter filter) {
        List<NbtTag> matched = new ArrayList<>();
        for (NbtTag element : list.values()) {
            if (element instanceof NbtCompound compound && filter.value().equals(compound.get(filter.key()))) {
                matched.add(element);
            }
        }
        if (matched.isEmpty()) {
            return null;
        }
        if (matched.size() == 1) {
            return matched.get(0);
        }
        NbtList result = new NbtList();
        matched.forEach(result::add);
        return result;
    }

    /* ------------------------------------------------------------------
     * 在标签树上写（storage 用；实体那条路走 DataBridge 的活对象导航）
     * ------------------------------------------------------------------ */

    /**
     * 在标签树里写一个值。
     * <p>
     * 末段规则：键 → 覆盖/新建；下标 → 替换（等于 {@code size()} 就是追加）；
     * {@code {k:v}} 过滤 → 写第一个命中的元素；切片 → 拒绝（切片只能读）。
     *
     * @param root  根标签
     * @param value 新值
     * @throws IllegalArgumentException 路径断了 / 末段不支持写
     */
    public void setIn(NbtTag root, NbtTag value) {
        if (isRoot()) {
            throw new IllegalArgumentException("要写就得给一个路径（例如 killCount）");
        }
        NbtTag parent = parentOf(root);
        Object last = segments.get(segments.size() - 1);
        if (last instanceof Filter filter) {
            NbtList list = requireList(parent, filter);
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i) instanceof NbtCompound compound && filter.value().equals(compound.get(filter.key()))) {
                    list.values().set(i, value);
                    return;
                }
            }
            throw new IllegalArgumentException("过滤 " + filter + " 没有命中任何元素");
        }
        if (last instanceof Slice slice) {
            throw new IllegalArgumentException("切片 " + slice + " 只能用于读取，不能用来写");
        }
        if (last instanceof Integer index) {
            NbtList list = requireList(parent, index);
            if (index < 0 || index > list.size()) {
                throw new IllegalArgumentException("下标 " + index + " 越界（这个列表有 " + list.size() + " 个元素）");
            }
            if (index == list.size()) {
                list.add(value);
            } else {
                list.values().set(index, value);
            }
            return;
        }
        if (!(parent instanceof NbtCompound compound)) {
            throw new IllegalArgumentException("路径「" + describe() + "」前面不是复合标签（"
                    + (parent == null ? "空" : parent.type().toString()) + "），写不进去");
        }
        compound.put(String.valueOf(last), value);
    }

    /**
     * 在标签树里合并一段补丁（路径指向的必须是复合标签；根路径就是根自己）。
     *
     * @param root  根标签
     * @param patch 补丁
     * @throws IllegalArgumentException 路径不存在 / 指向的不是复合标签
     */
    public void mergeIn(NbtTag root, NbtCompound patch) {
        NbtTag target = isRoot() ? root : get(root);
        if (!(target instanceof NbtCompound compound)) {
            throw new IllegalArgumentException("路径「" + describe() + "」指向的不是复合标签（"
                    + (target == null ? "不存在" : target.type().toString()) + "），不能合并");
        }
        mergeTag(compound, patch);
    }

    /**
     * 往路径指向的列表里插入一个元素。
     *
     * @param root  根标签
     * @param index 插入位置
     * @param value 元素
     * @throws IllegalArgumentException 路径指向的不是列表 / 下标越界
     */
    public void insertIn(NbtTag root, int index, NbtTag value) {
        NbtTag target = isRoot() ? root : get(root);
        if (!(target instanceof NbtList list)) {
            throw new IllegalArgumentException("路径「" + describe() + "」指向的不是列表，不能 append/prepend/insert");
        }
        if (index < 0 || index > list.size()) {
            throw new IllegalArgumentException("下标 " + index + " 越界（这个列表有 " + list.size() + " 个元素）");
        }
        list.insert(index, value);
    }

    /**
     * 走到"父标签"（除最后一段以外的全部）。
     *
     * @param root 根标签
     * @return 最后一段所在的容器
     * @throws IllegalArgumentException 中途断了
     */
    private NbtTag parentOf(NbtTag root) {
        NbtTag current = root;
        for (int i = 0; i < segments.size() - 1; i++) {
            current = step(current, segments.get(i));
        }
        return current;
    }

    /**
     * 走一段。
     *
     * @param current 当前标签
     * @param segment 这一段
     * @return 下一层
     * @throws IllegalArgumentException 走不下去
     */
    private NbtTag step(NbtTag current, Object segment) {
        if (current == null) {
            throw new IllegalArgumentException("路径「" + describe() + "」中间断了（有一段不存在）");
        }
        if (segment instanceof Integer index) {
            NbtList list = requireList(current, index);
            if (index < 0 || index >= list.size()) {
                throw new IllegalArgumentException("路径「" + describe() + "」的下标 " + index
                        + " 越界（这个列表有 " + list.size() + " 个元素）");
            }
            return list.get(index);
        }
        if (segment instanceof Filter filter) {
            NbtList list = requireList(current, filter);
            for (NbtTag element : list.values()) {
                if (element instanceof NbtCompound compound && filter.value().equals(compound.get(filter.key()))) {
                    return element;
                }
            }
            throw new IllegalArgumentException("路径「" + describe() + "」的过滤 " + filter + " 没有命中任何元素（列表里 "
                    + filter.key() + " 的取值：" + presentValues(list, filter.key()) + "）");
        }
        if (segment instanceof Slice slice) {
            NbtList list = requireList(current, slice);
            return sliceOf(list, slice);
        }
        if (!(current instanceof NbtCompound compound)) {
            throw new IllegalArgumentException("路径「" + describe() + "」里的「" + segment + "」前面不是复合标签");
        }
        NbtTag child = compound.get(String.valueOf(segment));
        if (child == null) {
            throw new IllegalArgumentException("路径「" + describe() + "」里没有「" + segment + "」这一段");
        }
        return child;
    }

    /**
     * @param parent 期望是列表的标签
     * @param what   这一段（报错用）
     * @return 那个列表
     * @throws IllegalArgumentException 不是列表
     */
    private NbtList requireList(NbtTag parent, Object what) {
        if (!(parent instanceof NbtList list)) {
            throw new IllegalArgumentException("路径「" + describe() + "」里的 " + what + " 前面不是列表（"
                    + (parent == null ? "空" : parent.type().toString()) + "）");
        }
        return list;
    }

    /**
     * 递归合并：两边都是复合标签就往下合，否则整体替换。
     *
     * @param target 目标复合标签
     * @param patch  补丁
     */
    private static void mergeTag(NbtCompound target, NbtCompound patch) {
        for (Map.Entry<String, NbtTag> entry : patch.values().entrySet()) {
            NbtTag existing = target.get(entry.getKey());
            if (existing instanceof NbtCompound existingCompound
                    && entry.getValue() instanceof NbtCompound patchCompound) {
                mergeTag(existingCompound, patchCompound);
            } else {
                target.put(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * 按切片段取子列表（两端都会夹到合法范围）。
     *
     * @param list  列表
     * @param slice 切片
     * @return 新的列表标签（元素是同一批标签对象）
     */
    private static NbtList sliceOf(NbtList list, Slice slice) {
        int size = list.size();
        int from = slice.start() == null ? 0 : Math.max(0, Math.min(size, slice.start()));
        int to = slice.end() == null ? size : Math.max(0, Math.min(size, slice.end()));
        NbtList result = new NbtList();
        for (int i = from; i < to; i++) {
            result.add(list.get(i));
        }
        return result;
    }
}
