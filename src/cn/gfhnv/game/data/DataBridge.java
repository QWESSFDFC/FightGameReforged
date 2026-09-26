package cn.gfhnv.game.data;

import cn.gfhnv.game.Thing;
import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.inventory.Inventory;
import cn.gfhnv.game.inventory.Slot;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.mana.Mana;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 游戏对象 ⇄ NBT 标签的桥。
 * <p>
 * <b>方向一（读，{@link #toTag(Object)}）</b>：把对象当数据看，反射出字段做成标签树。
 * 只认这几类"数据对象"：{@link Thing}（实体/物品）、{@link Effect}、{@link Slot}、{@link Inventory}、
 * {@link Mana}、{@link Skill}、{@link LivingThing.DamageReduction}。
 * 其余一律<b>跳过</b> —— controller、监听器、{@code IModifyDamage} 闭包、物理对象、{@code Fight} 引用
 * 都是"行为"不是"数据"，dump 出来只会淹没真正的信息。
 * <p>
 * <b>方向二（写，{@link #merge(Object, NbtCompound)} 与 {@link #setAt}）</b>：把标签写回对象。
 * 规则：复合标签遇到对象就递归合并，其它类型整体替换；<b>能走 setter 就走 setter</b>
 * （这样 {@code hp} 会被 {@code setHp} 的钳制管住、{@code ignition} 会被夹到 0~上限），
 * 找不到 setter 才裸写字段。{@code final} 字段一律拒绝（{@code uuid} 被改掉会毁掉判等）。
 * <p>
 * <b>有意为之的取舍</b>：
 * <ul>
 *     <li>空的复合/列表会被省略（否则 63 个空背包格子会把输出淹掉）；</li>
 *     <li>同一个对象只展开一次（用身份表防环，也顺便避免了 {@code DamageReduction.source} 那种重复引用）；</li>
 *     <li>集合最多展开 {@value #MAX_ELEMENTS} 个元素、递归最深 {@value #MAX_DEPTH} 层；</li>
 *     <li>对象列表（{@code List<Effect>} 之类）<b>不能</b>通过 {@code /data} 增删元素 —— 那会绕开
 *     {@code addEffect} 的 id 补全与合并语义，请继续用 {@code /effect}。标量列表（数字/字符串/枚举）可以增删。</li>
 * </ul>
 *
 * @author AI（DeepSeek）生成
 */
public final class DataBridge {

    /**
     * 递归的最大深度。
     */
    private static final int MAX_DEPTH = 8;

    /**
     * 集合最多展开多少元素。
     */
    private static final int MAX_ELEMENTS = 64;

    /**
     * 工具类，不允许实例化。
     */
    private DataBridge() {
    }

    /* ------------------------------------------------------------------
     * 读：对象 → 标签
     * ------------------------------------------------------------------ */

    /**
     * 把一个对象转成标签树（不会改动对象）。
     *
     * @param object 对象；{@code null} 返回 {@code null}
     * @return 标签
     */
    public static NbtTag toTag(Object object) {
        return convert(object, new IdentityHashMap<>(), 0);
    }

    /**
     * 把一个对象转成复合标签（根不是对象时返回空复合标签）。
     *
     * @param object 对象
     * @return 复合标签
     */
    public static NbtCompound toCompound(Object object) {
        NbtTag tag = toTag(object);
        return tag instanceof NbtCompound compound ? compound : new NbtCompound();
    }

    /**
     * 列出某个类"会被 {@code /data} 看到的字段"（可用的数据名，按继承顺序）。
     *
     * @param type 类
     * @return 字段的数据名
     */
    public static List<String> dataNames(Class<?> type) {
        List<String> names = new ArrayList<>();
        for (Field field : dataFields(type)) {
            names.add(dataName(field));
        }
        return names;
    }

    /**
     * 内部：对象 → 标签。
     *
     * @param value   值
     * @param visited 已展开对象的身份表（防环）
     * @param depth   当前深度
     * @return 标签；无法表示时返回 {@code null}（调用方跳过）
     */
    private static NbtTag convert(Object value, IdentityHashMap<Object, Boolean> visited, int depth) {
        if (value == null || depth > MAX_DEPTH) {
            return null;
        }
        if (value instanceof Boolean flag) {
            return new NbtByte(flag);
        }
        if (value instanceof Byte number) {
            return new NbtByte(number);
        }
        if (value instanceof Short || value instanceof Integer) {
            return new NbtInt(((Number) value).intValue());
        }
        if (value instanceof Long number) {
            return new NbtLong(number);
        }
        if (value instanceof Float || value instanceof Double) {
            return new NbtDouble(((Number) value).doubleValue());
        }
        if (value instanceof BigDecimal number) {
            return new NbtDouble(number.doubleValue());
        }
        if (value instanceof Character character) {
            return new NbtString(String.valueOf(character));
        }
        if (value instanceof CharSequence text) {
            return new NbtString(text.toString());
        }
        if (value instanceof Enum<?> constant) {
            return new NbtString(constant.name());
        }
        if (value instanceof Collection<?> collection) {
            NbtList list = new NbtList();
            int count = 0;
            for (Object element : collection) {
                if (count >= MAX_ELEMENTS) {
                    break;
                }
                NbtTag child = convert(element, visited, depth + 1);
                if (child != null) {
                    list.add(child);
                    count++;
                }
            }
            return list.isEmpty() ? null : list;
        }
        if (value instanceof Map<?, ?> map) {
            NbtCompound compound = new NbtCompound();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                NbtTag child = convert(entry.getValue(), visited, depth + 1);
                if (child != null) {
                    compound.put(String.valueOf(entry.getKey()), child);
                }
            }
            return compound.isEmpty() ? null : compound;
        }
        Class<?> type = value.getClass();
        if (type.isArray()) {
            NbtList list = new NbtList();
            int length = Math.min(java.lang.reflect.Array.getLength(value), MAX_ELEMENTS);
            for (int i = 0; i < length; i++) {
                NbtTag child = convert(java.lang.reflect.Array.get(value, i), visited, depth + 1);
                if (child != null) {
                    list.add(child);
                }
            }
            return list.isEmpty() ? null : list;
        }
        if (!isDataObject(value)) {
            return null;
        }
        if (visited.put(value, Boolean.TRUE) != null) {
            return null;
        }
        NbtCompound compound = new NbtCompound();
        for (Field field : dataFields(type)) {
            Object fieldValue;
            try {
                field.setAccessible(true);
                fieldValue = field.get(value);
            } catch (ReflectiveOperationException | RuntimeException e) {
                continue;
            }
            NbtTag child = convert(fieldValue, visited, depth + 1);
            if (child != null) {
                compound.put(dataName(field), child);
            }
        }
        return compound;
    }

    /**
     * @param value 值
     * @return 这个对象是否属于"数据对象"（见类注释；不在名单里的对象整个跳过）
     */
    private static boolean isDataObject(Object value) {
        return value instanceof Thing
                || value instanceof Effect
                || value instanceof Slot
                || value instanceof Inventory
                || value instanceof Mana
                || value instanceof Skill
                || value instanceof LivingThing.DamageReduction;
    }

    /**
     * @param type 类
     * @return 从父类到子类的全部"可数据化"字段
     */
    private static List<Field> dataFields(Class<?> type) {
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            hierarchy.add(0, current);
        }
        List<Field> fields = new ArrayList<>();
        for (Class<?> current : hierarchy) {
            for (Field field : current.getDeclaredFields()) {
                if (isDataField(field)) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    /**
     * @param field 字段
     * @return 这个字段是否参与数据化
     */
    private static boolean isDataField(Field field) {
        if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
            return false;
        }
        return !field.isAnnotationPresent(NoData.class);
    }

    /**
     * @param field 字段
     * @return 对外数据名（{@link DataField} 改名优先）
     */
    public static String dataName(Field field) {
        DataField annotation = field.getAnnotation(DataField.class);
        if (annotation != null && !annotation.value().isEmpty()) {
            return annotation.value();
        }
        return field.getName();
    }

    /* ------------------------------------------------------------------
     * 路径：在"活的对象图"上走（/data modify 用）
     * ------------------------------------------------------------------ */

    /**
     * 沿路径取<b>活的值</b>（不是标签副本）：字段值、列表元素、映射值都能取。
     *
     * @param root 根对象
     * @param path 路径
     * @return 路径指向的值；根路径返回 {@code root} 本身
     * @throws IllegalArgumentException 路径中间断了（字段不存在 / 下标越界 / 中间是标量）
     */
    public static Object valueAt(Object root, DataPath path) {
        if (root == null) {
            throw new IllegalArgumentException("目标对象是 null");
        }
        Object cursor = root;
        for (Object segment : path.segments()) {
            cursor = child(cursor, segment, path);
        }
        return cursor;
    }

    /**
     * 沿路径写一个值（末段仍然<b>setter 优先</b>）。
     * <p>
     * 与 {@link #merge(Object, NbtCompound)} 的区别：那个只能改根对象上的字段，
     * 这个能改到 {@code entityEffectList[0].level} 这种深处。
     *
     * @param root  根对象
     * @param path  路径
     * @param value 新值
     * @throws IllegalArgumentException 路径不存在 / 末段不允许写
     */
    public static void setAt(Object root, DataPath path, NbtTag value) {
        DataSlot slot = slotAt(root, path);
        if (slot.key() instanceof Field field) {
            apply(slot.container(), dataName(field), value);
            return;
        }
        if (slot.key() instanceof Integer index) {
            List<Object> list = rawList(slot.container());
            if (index == list.size()) {
                list.add(toJavaValue(value));
            } else {
                list.set(index, convertTo(list.get(index) == null ? Object.class : list.get(index).getClass(),
                        value, "[" + index + "]"));
            }
            return;
        }
        Map<String, Object> map = rawMap(slot.container());
        String key = String.valueOf(slot.key());
        Object existing = map.get(key);
        map.put(key, existing == null ? toJavaValue(value) : convertTo(existing.getClass(), value, key));
    }

    /**
     * 沿路径把一段补丁合并进"路径指向的那个对象"。
     *
     * @param root  根对象
     * @param path  路径
     * @param patch 补丁
     * @return 实际处理了几个键
     * @throws IllegalArgumentException 路径指向的不是可合并的对象
     */
    public static int mergeAt(Object root, DataPath path, NbtCompound patch) {
        Object target = valueAt(root, path);
        if (target == null) {
            throw new IllegalArgumentException("路径「" + path.describe() + "」指向的是空值，没法合并");
        }
        if (!isDataObject(target)) {
            throw new IllegalArgumentException("路径「" + path.describe() + "」指向的是 "
                    + target.getClass().getSimpleName() + "（不是可以合并的数据对象）");
        }
        return merge(target, patch);
    }

    /**
     * 沿路径往"路径指向的那个列表"里插入一个元素。
     * <p>
     * <b>只允许标量列表</b>（数字 / 字符串 / 布尔 / 枚举）。对象列表（例如 {@code List<Effect>}）
     * 会被拒绝：往里面塞对象需要构造出合法的游戏对象，本版做不到，请用专门的命令（效果用 {@code /effect}）。
     *
     * @param root  根对象
     * @param path  路径（指向列表本身）
     * @param index 插入位置（{@code 0} 到 {@code size()}；{@code append} 传 size、{@code prepend} 传 0）
     * @param value 元素
     * @throws IllegalArgumentException 路径指向的不是列表 / 是对象列表 / 下标越界
     */
    public static void insertAt(Object root, DataPath path, int index, NbtTag value) {
        Object target = valueAt(root, path);
        if (!(target instanceof List<?> list)) {
            throw new IllegalArgumentException("路径「" + path.describe() + "」指向的不是列表，不能 append/prepend/insert");
        }
        if (index < 0 || index > list.size()) {
            throw new IllegalArgumentException("下标 " + index + " 越界（这个列表有 " + list.size() + " 个元素）");
        }
        Class<?> elementType = null;
        for (Object element : list) {
            if (element != null) {
                elementType = element.getClass();
                break;
            }
        }
        if (elementType != null && isDataObject(elementType) && !isScalar(elementType)) {
            throw new IllegalArgumentException("路径「" + path.describe() + "」是对象列表（"
                    + elementType.getSimpleName() + "），本版不支持增删：请用专门的命令（效果用 /effect）");
        }
        Object converted = elementType == null || elementType == Object.class
                ? toJavaValue(value) : convertTo(elementType, value, "列表元素");
        rawList(list).add(index, converted);
    }

    /**
     * 路径末端的"可写位置"：父容器 + 键（{@link Field} / {@link Integer} 下标 / 映射键）。
     * <p>
     * <b>名字特意不叫 {@code Slot}</b>：本文件 import 了 {@link cn.gfhnv.game.inventory.Slot}
     * （背包格，是要暴露的数据对象），内部再定义一个同名 record 会把它<b>遮住</b> ——
     * 症状是背包在 {@code /data get} 里变成空壳（2026-09 实测踩过，自测断言现在盯着它）。
     *
     * @param container 父容器
     * @param key       键
     * @param type      末段声明的类型（拿不到时为 {@code null}）
     */
    private record DataSlot(Object container, Object key, Class<?> type) {
    }

    /**
     * 沿路径走到"父容器"，把最后一段作为键返回。
     *
     * @param root 根对象
     * @param path 路径
     * @return 末段的槽位
     * @throws IllegalArgumentException 路径断了
     */
    private static DataSlot slotAt(Object root, DataPath path) {
        List<Object> segments = path.segments();
        if (path.isRoot()) {
            throw new IllegalArgumentException("要改东西就得给一个路径（例如 hp、manas[0].amount）");
        }
        Object cursor = root;
        for (int i = 0; i < segments.size() - 1; i++) {
            cursor = child(cursor, segments.get(i), path);
        }
        Object last = segments.get(segments.size() - 1);
        if (last instanceof DataPath.Filter filter) {
            if (!(cursor instanceof List<?> list)) {
                throw new IllegalArgumentException("路径「" + path.describe() + "」的最后一段是 " + filter
                        + " 过滤，但它前面不是列表");
            }
            for (int i = 0; i < list.size(); i++) {
                if (matchesFilter(list.get(i), filter)) {
                    return new DataSlot(list, i, list.get(i) == null ? null : list.get(i).getClass());
                }
            }
            throw new IllegalArgumentException("路径「" + path.describe() + "」的过滤 " + filter + " 没有命中任何元素（列表里 "
                    + filter.key() + " 的取值：" + presentValues(list, filter) + "）");
        }
        if (last instanceof DataPath.Slice slice) {
            throw new IllegalArgumentException("路径「" + path.describe() + "」里的切片 " + slice
                    + " 只能用于读取，不能用来写");
        }
        if (last instanceof Integer index) {
            if (!(cursor instanceof List<?> list)) {
                throw new IllegalArgumentException("路径「" + path.describe() + "」的最后一段是下标，但它前面不是列表");
            }
            if (index < 0 || index > list.size()) {
                throw new IllegalArgumentException("下标 " + index + " 越界（这个列表有 " + list.size() + " 个元素）");
            }
            return new DataSlot(list, index,
                    index < list.size() && list.get(index) != null ? list.get(index).getClass() : null);
        }
        if (cursor == null) {
            throw new IllegalArgumentException("路径「" + path.describe() + "」中间遇到了空值");
        }
        String name = String.valueOf(last);
        if (cursor instanceof Map<?, ?> map) {
            Object existing = map.get(name);
            return new DataSlot(map, name, existing == null ? null : existing.getClass());
        }
        Field field = findField(cursor.getClass(), name);
        if (field == null) {
            throw new IllegalArgumentException("「" + cursor.getClass().getSimpleName() + "」没有数据字段「" + name
                    + "」（用 /data get 看看有哪些）");
        }
        return new DataSlot(cursor, field, field.getType());
    }

    /**
     * 往下走一段。
     *
     * @param cursor  当前值
     * @param segment 这一段（{@code String} 键 或 {@code Integer} 下标）
     * @param path    原路径（报错用）
     * @return 下一层的值
     * @throws IllegalArgumentException 走不下去
     */
    private static Object child(Object cursor, Object segment, DataPath path) {
        if (cursor == null) {
            throw new IllegalArgumentException("路径「" + path.describe() + "」中间遇到了空值");
        }
        if (segment instanceof DataPath.Filter filter) {
            return childOfFilter(cursor, filter, path);
        }
        if (segment instanceof DataPath.Slice slice) {
            throw new IllegalArgumentException("路径「" + path.describe() + "」里的切片 " + slice
                    + " 只能用于读取，不能用来写");
        }
        if (segment instanceof Integer index) {
            if (!(cursor instanceof List<?> list)) {
                throw new IllegalArgumentException("路径「" + path.describe() + "」里有下标，但它前面不是列表（"
                        + cursor.getClass().getSimpleName() + "）");
            }
            if (index < 0 || index >= list.size()) {
                throw new IllegalArgumentException("路径「" + path.describe() + "」的下标 " + index
                        + " 越界（这个列表有 " + list.size() + " 个元素）");
            }
            return list.get(index);
        }
        String name = String.valueOf(segment);
        if (cursor instanceof Map<?, ?> map) {
            if (!map.containsKey(name)) {
                throw new IllegalArgumentException("路径「" + path.describe() + "」在映射里找不到键「" + name + "」");
            }
            return map.get(name);
        }
        Field field = findField(cursor.getClass(), name);
        if (field == null) {
            throw new IllegalArgumentException("「" + cursor.getClass().getSimpleName() + "」没有数据字段「" + name
                    + "」（用 /data get 看看有哪些）");
        }
        try {
            field.setAccessible(true);
            return field.get(cursor);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalArgumentException("读字段「" + name + "」失败：" + rootMessage(e));
        }
    }

    /**
     * 在活列表里按 {@code {k:v}} 找<b>第一个</b>命中的元素。
     * <p>
     * 判断用的是同一套"数据视图"（把元素转成标签再比字段），所以
     * {@code /data get} 里能看到的字段，路径里就能拿来过滤。
     *
     * @param cursor  当前值（必须是列表）
     * @param filter  过滤条件
     * @param path    原路径（报错用）
     * @return 命中的元素
     * @throws IllegalArgumentException 不是列表 / 没有命中
     */
    private static Object childOfFilter(Object cursor, DataPath.Filter filter, DataPath path) {
        if (!(cursor instanceof List<?> list)) {
            throw new IllegalArgumentException("路径「" + path.describe() + "」里有 " + filter + " 过滤，但它前面不是列表（"
                    + cursor.getClass().getSimpleName() + "）");
        }
        for (Object element : list) {
            if (matchesFilter(element, filter)) {
                return element;
            }
        }
        throw new IllegalArgumentException("路径「" + path.describe() + "」的过滤 " + filter + " 没有命中任何元素（列表里 "
                + filter.key() + " 的取值：" + presentValues(list, filter) + "）");
    }

    /**
     * 活列表版的 {@link DataPath#presentValues}：把元素按数据视图转成标签后再收集取值。
     * <p>
     * 只在报错路径上调用，所以"整个元素转成标签"这点开销无所谓。
     *
     * @param list   活列表
     * @param filter 过滤条件
     * @return 取值清单
     */
    private static String presentValues(List<?> list, DataPath.Filter filter) {
        NbtList tags = new NbtList();
        for (Object element : list) {
            if (element == null) {
                continue;
            }
            NbtTag tag = element instanceof NbtTag already ? already
                    : convert(element, new IdentityHashMap<>(), 0);
            if (tag != null) {
                tags.add(tag);
            }
        }
        return DataPath.presentValues(tags, filter.key());
    }

    /**
     * @param element 元素（游戏对象或标签）
     * @param filter  过滤条件
     * @return 这个元素（按数据视图）是否满足过滤
     */
    private static boolean matchesFilter(Object element, DataPath.Filter filter) {
        NbtTag tag = element instanceof NbtTag already ? already : convert(element, new IdentityHashMap<>(), 0);
        return tag instanceof NbtCompound compound && filter.value().equals(compound.get(filter.key()));
    }

    /**
     * 把一个标签转成"Java 侧的自然值"（元素类型未知时用：映射的值、空列表的元素）。
     *
     * @param value 标签
     * @return Java 值
     * @throws IllegalArgumentException 复合/列表标签没有目标类型可推断
     */
    private static Object toJavaValue(NbtTag value) {
        return switch (value.type()) {
            case BYTE -> value.asBoolean();
            case INT -> (int) value.asLong();
            case LONG -> value.asLong();
            case DOUBLE -> value.asDouble();
            case STRING -> value.asString();
            case LIST, COMPOUND -> throw new IllegalArgumentException("这里推断不出 " + value.type()
                    + " 该变成什么 Java 类型（本版只支持往标量位置塞数字/字符串/布尔）");
        };
    }

    /**
     * @param type 类型
     * @return 是否标量（数字 / 布尔 / 字符串 / 枚举）
     */
    private static boolean isScalar(Class<?> type) {
        return type.isPrimitive() || Number.class.isAssignableFrom(type) || type == Boolean.class
                || type == Character.class || CharSequence.class.isAssignableFrom(type) || type.isEnum();
    }

    /**
     * @param container 列表
     * @return 同一个列表，但当成 {@code List<Object>} 用
     */
    @SuppressWarnings("unchecked")
    private static List<Object> rawList(Object container) {
        return (List<Object>) container;
    }

    /**
     * @param container 映射
     * @return 同一个映射，但键当成 {@code String}
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> rawMap(Object container) {
        return (Map<String, Object>) container;
    }

    /* ------------------------------------------------------------------
     * 写：标签 → 对象（一条路径走 setter 优先）
     * ------------------------------------------------------------------ */

    /**
     * 把一段补丁合并进对象。
     * <p>
     * 复合标签 + 目标字段也是数据对象 → 递归合并；其它情况整体替换该字段。
     *
     * @param target 目标对象
     * @param patch  补丁
     * @return 实际处理了几个键
     * @throws IllegalArgumentException 字段不存在 / 是 final / 类型不支持
     */
    public static int merge(Object target, NbtCompound patch) {
        if (target == null) {
            throw new IllegalArgumentException("目标对象是 null");
        }
        int applied = 0;
        for (Map.Entry<String, NbtTag> entry : patch.values().entrySet()) {
            apply(target, entry.getKey(), entry.getValue());
            applied++;
        }
        return applied;
    }

    /**
     * 合并单个键。
     *
     * @param target 目标对象
     * @param key    数据名
     * @param value  新值
     */
    private static void apply(Object target, String key, NbtTag value) {
        Field field = findField(target.getClass(), key);
        if (field == null) {
            throw new IllegalArgumentException("「" + target.getClass().getSimpleName() + "」没有数据字段「" + key
                    + "」（用 /data get 看看有哪些）");
        }
        if (Modifier.isFinal(field.getModifiers())) {
            throw new IllegalArgumentException("字段「" + key + "」是 final，不能通过 /data 修改");
        }
        Object current;
        try {
            field.setAccessible(true);
            current = field.get(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            current = null;
        }
        if (value instanceof NbtCompound compound && current != null && isDataObject(current)) {
            merge(current, compound);
            return;
        }
        if (trySetter(target, field, value)) {
            return;
        }
        try {
            field.setAccessible(true);
            field.set(target, convertTo(field.getType(), value, key));
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("写入字段「" + key + "」失败：" + rootMessage(e));
        }
    }

    /**
     * 按数据名找字段（含 {@link DataField} 改名）。
     *
     * @param type 类
     * @param name 数据名
     * @return 字段；找不到返回 {@code null}
     */
    private static Field findField(Class<?> type, String name) {
        for (Field field : dataFields(type)) {
            if (dataName(field).equals(name)) {
                return field;
            }
        }
        return null;
    }

    /**
     * 尝试走 setter（守住对象自己的钳制与副作用）。
     *
     * @param target 目标对象
     * @param field  字段
     * @param value  新值
     * @return 是否已经写成功
     */
    private static boolean trySetter(Object target, Field field, NbtTag value) {
        String name = field.getName();
        String setterName = "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        Method setter = null;
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                setter = method;
                break;
            }
        }
        if (setter == null) {
            return false;
        }
        Object converted = convertTo(setter.getParameterTypes()[0], value, name);
        try {
            setter.invoke(target, converted);
            return true;
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("调用 " + setterName + "() 失败：" + rootMessage(e));
        }
    }

    /**
     * 把一个标签转成目标类型。
     *
     * @param type  目标类型
     * @param value 标签
     * @param key   字段名（报错用）
     * @return 转换后的值
     * @throws IllegalArgumentException 类型不支持（例如直接改一个对象列表）
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object convertTo(Class<?> type, NbtTag value, String key) {
        if (type == long.class || type == Long.class) {
            return value.asLong();
        }
        if (type == int.class || type == Integer.class) {
            long number = value.asLong();
            if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("字段「" + key + "」是 int，" + number + " 放不下");
            }
            return (int) number;
        }
        if (type == short.class || type == Short.class) {
            return (short) value.asLong();
        }
        if (type == byte.class || type == Byte.class) {
            return (byte) value.asLong();
        }
        if (type == double.class || type == Double.class) {
            return value.asDouble();
        }
        if (type == float.class || type == Float.class) {
            return (float) value.asDouble();
        }
        if (type == boolean.class || type == Boolean.class) {
            return value.asBoolean();
        }
        if (type == String.class || type == CharSequence.class) {
            return value.asString();
        }
        if (type.isEnum()) {
            String text = value.asString();
            try {
                return Enum.valueOf((Class<? extends Enum>) type.asSubclass(Enum.class), text);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("枚举「" + type.getSimpleName() + "」里没有「" + text + "」");
            }
        }
        if (NbtTag.class.isAssignableFrom(type)) {
            return value;
        }
        if (Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type) || type.isArray()) {
            throw new IllegalArgumentException("字段「" + key + "」是列表/映射/数组，本版不支持整段替换："
                    + "对象列表请用专门的命令（例如 /effect），标量列表请用 /data modify 的 append/prepend/insert");
        }
        throw new IllegalArgumentException("字段「" + key + "」的类型是 " + type.getSimpleName()
                + "，本版不支持直接改（可以读，不能写）");
    }

    /**
     * @param e 异常
     * @return 最内层异常的消息
     */
    private static String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
