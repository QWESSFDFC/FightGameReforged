package cn.gfhnv.game.system.configLoadingSystem;

import cn.gfhnv.game.data.DataBridge;
import cn.gfhnv.game.data.NbtCompound;
import cn.gfhnv.game.data.NbtTag;
import cn.gfhnv.game.data.NoConfig;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.*;

/**
 * <b>反射驱动的配置补丁器（实验，与 {@link KeySpec} 那条表驱动路<b>并列</b>）</b>。
 * <p>
 * <b>它解决的是什么</b>：表驱动把"加一个键"从 5 个文件压到 1 个文件 1 行，但那一行
 * 仍然是<b>手写的</b> —— 一张表加一行 {@code spec(...)}，键名、类型、读法、写法都得人再抄一遍。
 * 抄错的后果不是编译错误，是"声明了却没人读"（D1 那 14 个键就是这么来的）。
 * 本类换一条路：<b>配置面直接寄生在 {@link DataBridge} 的反射之上</b> ——
 * 可配置的键 == {@code /data} 看得见的键，于是"加一个加载项 = 加一个 Java 字段"。
 * <p>
 * <b>为什么不是"又抄了一份 DataBridge"</b>（这是整个实验的成败判据）：
 * <ul>
 *     <li>键清单来自 {@link DataBridge#accessors(Object)} —— 与本类无关的第三方代码写不出来；</li>
 *     <li>默认值来自 {@link DataBridge#toTag(Object)}，也就是 {@code /data get} 本身；</li>
 *     <li>写入走 {@link DataBridge#applyTag(Object, String, NbtTag)}，也就是 {@code /data merge}
 *     本身（setter 优先、{@code final} 拒绝、{@link cn.gfhnv.game.data.DataFlatten} 组件下潜
 *     全部由它负责）。</li>
 * </ul>
 * 本类里<b>一个键名都没有</b>，只有"怎么用一个键"的逻辑 —— 所以它不可能与 {@code /data} 漂移。
 * <p>
 * <b>两个注解的分工</b>（一个字段有三面：{@code /data get} / {@code /data merge} / 配置文件）：
 * <pre>
 *   默认          → 三面全开
 *   &#64;NoConfig    → /data 全开，配置面关闭（例如 uuid / alive / presentTurn）
 *   &#64;NoData      → 三面全关（例如 anticipating）
 * </pre>
 * <b>否定式是刻意的</b>：不写注解 = 能配，所以新字段零成本地变成可配置项。
 * 代价见 {@link NoConfig} 的 javadoc（每加一个字段要想一次"能不能给人配"）。
 * <p>
 * <b>本类刻意不做的事</b>（切换前必须先决定，见实验结论）：
 * <ul>
 *     <li><b>不排序、不分组</b>：应用顺序就是字段的声明顺序（父类在前），没有 {@code level} 最先、
 *     {@code hp} 最后这种编排 —— 表驱动那边靠"表的顺序 = 应用顺序"承载，反射拿不到；</li>
 *     <li><b>不重算派生值</b>：写成长系数后要不要补一次 {@code setLevel}，反射无从判断；</li>
 *     <li><b>不认识 {@code inventorySlots}</b>：那个键<b>不在</b> {@code /data} 里
 *     （{@code DataKeys.NOT_IN_DATA} 两项之一），反射面上根本没有它。</li>
 * </ul>
 *
 * @author AI（DeepSeek）生成
 */
public final class ReflectionConfigBridge {

    /**
     * 工具类，不允许实例化。
     */
    private ReflectionConfigBridge() {
    }

    /* ------------------------------------------------------------------
     * 对外 API
     * ------------------------------------------------------------------ */

    /**
     * 这个对象上<b>能被配置文件写</b>的字段，两条规则：
     * <ol>
     *     <li><b>没有 {@link NoConfig}</b>（否定式：不写注解 = 能配）；</li>
     *     <li><b>是标量或 {@link NbtTag}</b> —— 一条配置文件只能写"一个值"。</li>
     * </ol>
     * <p>
     * 第 ② 条是刻意的收窄，而且<b>它有代价</b>：{@code /data} 能改的集合键
     * （{@code inventory}、{@code damageReductions} 这些）在配置面被排除掉了 ——
     * 那些键本来也没有 setter，硬塞进去只会变成"静默退化成裸写字段"（
     * {code notes_for_llm/70-DATA.md} §5.10 点名的坑）。
     * <p>
     * {@code elementSort}（枚举）在标量里，所以它<b>在</b>面内，但键名是 {@code elementSort} 而不是
     * {@code element} —— 别名是键表那条路承载的东西，反射面拿不到。
     *
     * @param target 对象
     * @return 访问器（顺序 = 字段声明顺序，父类在前）
     */
    public static List<DataBridge.DataAccessor> candidates(Object target) {
        List<DataBridge.DataAccessor> candidates = new ArrayList<>();
        for (DataBridge.DataAccessor accessor : DataBridge.accessors(target)) {
            if (accessor.field().isAnnotationPresent(NoConfig.class)) {
                continue;
            }
            if (!isScalar(accessor.field())) {
                continue;
            }
            candidates.add(accessor);
        }
        return candidates;
    }

    /**
     * 被 {@link #candidates} 的任一条件排除掉的字段（自测与文档用：把"挡了什么、为什么"打成一张表）。
     *
     * @param target 对象
     * @return 数据名 → 理由
     */
    public static Map<String, String> blocked(Object target) {
        Map<String, String> blocked = new LinkedHashMap<>();
        for (DataBridge.DataAccessor accessor : DataBridge.accessors(target)) {
            NoConfig annotation = accessor.field().getAnnotation(NoConfig.class);
            if (annotation != null) {
                blocked.put(accessor.name(), annotation.value());
                continue;
            }
            if (!isScalar(accessor.field())) {
                blocked.put(accessor.name(), "非标量（" + accessor.field().getType().getSimpleName()
                        + "）：一条配置只能写一个值，集合/对象用 /data 或专门的命令");
            }
        }
        return blocked;
    }

    /**
     * @param field 字段
     * @return 这个字段是不是"一个值"（数字 / 布尔 / 字符 / 字符串 / 枚举 / {@link NbtTag}）
     */
    public static boolean isScalar(Field field) {
        if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
            return false;
        }
        Class<?> type = field.getType();
        return type.isPrimitive() || Number.class.isAssignableFrom(type) || type == Boolean.class
                || type == Character.class || CharSequence.class.isAssignableFrom(type) || type.isEnum()
                || NbtTag.class.isAssignableFrom(type);
    }

    /**
     * <b>默认值 dump</b>：这个对象上可配置键的当前值（按数据名排序，读法与 {@code /data get} 同一份实现）。
     * <p>
     * 只列<b>真的 dump 得出来</b>的键：{@link DataBridge#toTag(Object)} 会省略空集合 / 行为对象
     * （{@code DataKeys.READ_ONLY} 里那些就是这么消失的），所以这个口径
     * 恰好是"用户能写、写了能生效"的集合 —— 比 {@code dataNames()} 那个字段清单严格。
     *
     * @param target 对象
     * @return 数据名 → 标签（值的形式与 {@code /data get} 完全一致）
     */
    public static Map<String, NbtTag> defaults(Object target) {
        NbtCompound dump = DataBridge.toCompound(target);
        Map<String, NbtTag> values = new TreeMap<>();
        for (DataBridge.DataAccessor accessor : candidates(target)) {
            NbtTag value = dump.get(accessor.name());
            if (value != null) {
                values.put(accessor.name(), value);
            }
        }
        return values;
    }

    /**
     * <b>子类配置字段的默认值</b>：这个对象上属于 {@link DataKeys#CLASS_CONFIG} 的键 → 当前值。
     * <p>
     * <b>为什么需要它</b>（2026-10-03，用户意见"白厄的火种为什么没有配置"）：
     * {@link #defaults} 的口径是"可配置的标量键"，但默认值文件的 {@code base} / {@code derived}
     * 两段是<b>照着 {@code EntityKeySpecs} 那张通用表</b>生成的 —— 子类字段进不了那张表
     * （表里的行要对任意 {@code LivingThing} 成立，而 {@code coreflame} 只在 {@code Phainon} 上存在）。
     * 结果就是"能配、但默认文件里看不见"，用户只能靠文档猜键名。这个方法把那一段补上，
     * 由 {@link ConfigDefaultWriter} 写进 {@code classState} 块。
     * <p>
     * <b>顺序固定</b>（按 {@link DataKeys#CLASS_CONFIG} 的声明顺序）：同一份运行状态下生成的文本
     * 逐字节一致，自测那条"写出来的键都能读回来"才有意义。
     * <p>
     * 只列<b>真的 dump 得出来</b>的键（口径与 {@link #defaults} 相同）：实体上没有这个字段时
     * （例如给 {@code playerOne} 找 {@code coreflame}）它自然不在返回里 ——
     * 这正是"这一段只给有它的模板写"的实现方式。
     *
     * @param target 对象
     * @return 数据名 → 标签（顺序 = {@link DataKeys#CLASS_CONFIG} 的顺序）
     */
    public static Map<String, NbtTag> classStateValues(Object target) {
        Map<String, NbtTag> values = new LinkedHashMap<>();
        if (target == null) {
            return values;
        }
        NbtCompound dump = DataBridge.toCompound(target);
        Set<String> onSurface = new LinkedHashSet<>();
        for (DataBridge.DataAccessor accessor : candidates(target)) {
            onSurface.add(accessor.name());
        }
        for (String key : DataKeys.CLASS_CONFIG) {
            if (!onSurface.contains(key)) {
                continue;
            }
            NbtTag value = dump.get(key);
            if (value != null) {
                values.put(key, value);
            }
        }
        return values;
    }

    /**
     * 把一份 {@code {"键":值}} 对象打到一个对象上（<b>键名就是 {@code /data} 的数据名</b>）。
     * <p>
     * 与 {@code EntityDataPatcher} 的差别只在编排：这里<b>没有</b>键表、没有顺序编排、没有派生值重算。
     * 逐键容错保留（一个坏键不废整份配置）。
     *
     * @param target 目标对象
     * @param patch  补丁（{@code {"speed":200,"criticalRate":0.5}}）
     * @return 记账
     */
    public static Applied patch(Object target, JSONObject patch) {
        Map<String, String> applied = new LinkedHashMap<>();
        Map<String, String> skipped = new LinkedHashMap<>();
        if (target == null) {
            skipped.put("", "目标对象是 null");
            return new Applied(applied, skipped);
        }
        if (patch == null) {
            skipped.put("", "补丁是 null");
            return new Applied(applied, skipped);
        }
        Set<String> names = new LinkedHashSet<>();
        for (DataBridge.DataAccessor accessor : candidates(target)) {
            names.add(accessor.name());
        }
        write(target, patch, names, "", applied, skipped);
        return new Applied(applied, skipped);
    }

    /* ------------------------------------------------------------------
     * 与手写表的集成（第 3 步：把反射路接进 EntityDataPatcher）
     * ------------------------------------------------------------------ */

    /**
     * <b>兜底补丁（第 3 步切换的入口）</b>：把补丁里<b>手写表不认识、但反射面认识</b>的键写进去。
     * <p>
     * <b>它接在哪</b>：{@code EntityDataPatcher.patch} 里，手写表那条路<b>跑完之后</b>。
     * 于是：
     * <ul>
     *     <li>表里的键（含别名、{@code manaGrow} 块、{@code inventorySlots}）仍然由表处理 ——
     *     <b>顺序、子块、派生值重算、报错文案一个字都没变</b>；</li>
     *     <li>表里没有的键，只要它是"反射面上的标量、且没被 {@code @NoConfig} / 明确排除挡住"，
     *     就在这里直接生效 —— 这就是"加一个字段 = 0 处配置层改动"；</li>
     *     <li>两边都不认的键<b>照旧报「未知键」</b>（本方法一个字都不报：认不出来就静静放手，
     *     让 {@code EntityDataPatcher.reportUnknownKeys} 用它原来的文案与段前缀去说）。</li>
     * </ul>
     * <p>
     * <b>2026-10-03 订正</b>："明确排除"这一条<b>不再是整批子类字段</b>，而是按
     * "这个值配了能不能算数"拆成两半（见 {@link DataKeys#CLASS_CONFIG} 与
     * {@link DataKeys#CLASS_RUNTIME}）：出厂数值（{@code coreflame} / {@code scourge} /
     * {@code ignition} …）走本方法的放行路，运行时状态（{@code isAwaken} / {@code phaseTwo} …）
     * 继续被 {@link DataKeys#CLASS_RUNTIME} 挡住，而且现在会带上
     * {@link DataKeys#hintFor} 的"为什么"。
     * <b>为什么段前缀（{@code base.} / {@code derived.}）由调用方给</b>：子块名是编排信息
     * （{@link DataKeys#BARE_SECTIONS}），反射面拿不到 —— 所以本方法收一个"段 → 容器"的入口，
     * 由调用方遍历子块、把前缀传进来。键名本身仍然是 {@code /data} 的数据名。
     * <p>
     * <b>幂等</b>：表已经处理过的键在这里被跳过（{@link DataKeys#isConfigurable}），
     * 所以同一个键永远不会被打两次补丁。
     *
     * @param target 目标对象
     * @param patch  补丁（整份实体补丁）
     * @param sink   记账口
     * @param id     目标 id（报错用）
     * @return 被本方法写进去的<b>数据名</b>集合（调用方用它放行"未知键"的报错）
     */
    public static Set<String> patchExtra(Object target, JSONObject patch, SpecPatcher.Sink sink, String id) {
        Set<String> applied = new LinkedHashSet<>();
        if (target == null || patch == null) {
            return applied;
        }
        Set<String> names = new LinkedHashSet<>();
        for (DataBridge.DataAccessor accessor : candidates(target)) {
            names.add(accessor.name());
        }
        for (String section : DataKeys.BARE_SECTIONS) {
            JSONObject block = patch.optJSONObject(section);
            if (block == null) {
                continue;
            }
            writeExtra(target, block, names, section + DataKeys.SECTION_SEPARATOR, sink, id, applied);
        }
        writeExtra(target, patch, names, "", sink, id, applied);
        return applied;
    }

    /**
     * 遍历一层（裸键那一层，或 {@code base} / {@code derived} 子块），把"表不认识、反射认识"的键写进去。
     *
     * @param target  目标对象
     * @param object  这一层的对象
     * @param names   可配置的数据名（= 反射面）
     * @param prefix  段前缀（{@code base.} / {@code derived.} / 空串）
     * @param sink    记账口
     * @param id      目标 id
     * @param applied 记账：被本方法写进去的数据名
     */
    private static void writeExtra(Object target, JSONObject object, Set<String> names, String prefix,
                                   SpecPatcher.Sink sink, String id, Set<String> applied) {
        for (String key : object.keySet()) {
            Object raw = object.opt(key);
            if (raw == null || JSONObject.NULL.equals(raw) || raw instanceof JSONObject) {
                // 块（子块 / 数据对象 / 块写法）不在这里处理：表那一侧各有专门分支
                continue;
            }
            if (DataKeys.isConfigurable(key) || DataKeys.Alias.ELEMENT.equals(key)) {
                continue;   // 表的事，已经做过了（幂等）
            }
            if (DataKeys.CLASS_RUNTIME.contains(key)) {
                continue;   // 明确排除：角色/怪物自己的运行时状态键（配置层刻意不管这一批）
            }
            if (!names.contains(key)) {
                continue;   // 反射面也不认识 → 交给 reportUnknownKeys 用它原来的文案去报
            }
            String problem = DataBridge.applyTag(target, key, toTag(raw));
            if (problem != null) {
                sink.skipped(id, prefix + key, problem);
                continue;
            }
            sink.applied(id, prefix + key, String.valueOf(raw));
            applied.add(key);
        }
    }

    /* ------------------------------------------------------------------
     * 逐键写入
     * ------------------------------------------------------------------ */

    /**
     * 把一个 JSON 对象里的键逐个写进目标对象。
     *
     * @param target  目标对象
     * @param object  补丁对象
     * @param names   目标对象上可配置的数据名（预先算好，避免每个键都反射一遍）
     * @param prefix  报错用的前缀（形如 {@code base.}）
     * @param applied 记账：写成功的
     * @param skipped 记账：跳过 / 失败的
     */
    private static void write(Object target, JSONObject object, Set<String> names, String prefix,
                              Map<String, String> applied, Map<String, String> skipped) {
        for (String key : object.keySet()) {
            Object raw = object.opt(key);
            if (raw == null || JSONObject.NULL.equals(raw)) {
                continue;
            }
            String path = prefix + key;
            if (!(raw instanceof JSONObject block)) {
                applyOne(target, key, path, names, raw, applied, skipped);
                continue;
            }
            applyBlock(target, key, path, names, block, applied, skipped);
        }
    }

    /**
     * 写一个标量（或标量列表）键。
     *
     * @param target  目标对象
     * @param key     JSON 里的键
     * @param path    报错用的键路径
     * @param names   可配置的数据名
     * @param raw     JSON 原始值
     * @param applied 记账：写成功的
     * @param skipped 记账：跳过 / 失败的
     */
    private static void applyOne(Object target, String key, String path, Set<String> names, Object raw,
                                 Map<String, String> applied, Map<String, String> skipped) {
        if (!names.contains(key)) {
            skipped.put(path, notConfigurableReason(key));
            return;
        }
        String problem = DataBridge.applyTag(target, key, toTag(raw));
        if (problem != null) {
            skipped.put(path, problem);
            return;
        }
        applied.put(path, String.valueOf(raw));
    }

    /**
     * 写一个"块"值（JSON 对象）。
     * <p>
     * 两条路，都不是本类新造的规矩：
     * <ol>
     *     <li>那个键本身就是个数据对象（例如 {@code manas}）→ 整块交给 {@link DataBridge} 合并；</li>
     *     <li>那个键不是数据对象（例如 {@code manaGrow}）→ 按<b>数据名拼接</b>下钻：
     *     {@code manaGrow.fire} == {@code fireManaGrow}。这是"键名即数据名"这一条约定的自然推论，
     *     所以这里<b>没有</b>任何块名清单。</li>
     * </ol>
     * 一个块整体塞得进去、但 {@code /data} 里<b>没有</b>对应键的（{@link DataKeys#NOT_IN_DATA}
     * 那两项，今天只有 {@code manaGrow}），直接报出来而不是硬塞 ——
     * 硬塞进去的话它就是个没人读的键，正是本实验要消灭的那种静默失效。
     *
     * @param target  目标对象
     * @param key     JSON 里的键
     * @param path    报错用的键路径
     * @param names   可配置的数据名
     * @param block   块
     * @param applied 记账：写成功的
     * @param skipped 记账：跳过 / 失败的
     */
    private static void applyBlock(Object target, String name, String path, Set<String> names,
                                   JSONObject block, Map<String, String> applied,
                                   Map<String, String> skipped) {
        if (blockPrefixExists(names, name) && !isCompoundField(target, name)) {
            write(target, block, names, path + ".", applied, skipped);
            return;
        }
        if (names.contains(name) && isCompoundField(target, name)) {
            String problem = DataBridge.applyTag(target, name, toTag(block));
            if (problem == null) {
                applied.put(path, String.valueOf(block));
            } else {
                skipped.put(path, problem);
            }
            return;
        }
        if (DataKeys.NOT_IN_DATA.contains(name)) {
            skipped.put(path, "「" + name + "」是「配置文件里有、/data 里没有」的项"
                    + "（写不进反射面，块写法见 DataKeys.NOT_IN_DATA 的说明）");
            return;
        }
        skipped.put(path, notConfigurableReason(name));
    }

    /**
     * @param target 目标对象
     * @param name   数据名
     * @return 这个字段在数据视图里是不是一个复合标签（"块"）
     */
    private static boolean isCompoundField(Object target, String name) {
        NbtTag tag = DataBridge.toTag(target);
        return tag instanceof NbtCompound compound && compound.get(name) instanceof NbtCompound;
    }

    /**
     * @param names 可配置的数据名
     * @param key   块名
     * @return 有没有 {@code key} 打头的数据名（{@code manaGrow} → {@code manaGrowXxx}）
     */
    private static boolean blockPrefixExists(Set<String> names, String key) {
        for (String name : names) {
            if (name.startsWith(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param key 用户写的键
     * @return "为什么这个键配不了"的完整理由（借 {@link DataKeys#hintFor} 把 D1 那套说明也用上）
     */
    private static String notConfigurableReason(String key) {
        String hint = DataKeys.hintFor(key);
        return hint == null
                ? "「" + key + "」不是这个对象的可配置字段（配置面 = /data 面 − @NoConfig）"
                : "这个键不能配置（已经忽略）：" + hint;
    }

    /**
     * 把一个 JSON 值包成标签（{@link DataBridge#applyTag} 认的类型收敛与 {@code /data merge} 同一套）。
     *
     * @param raw JSON 值
     * @return 标签
     */
    private static NbtTag toTag(Object raw) {
        if (raw instanceof Boolean flag) {
            return NbtTag.of(flag);
        }
        if (raw instanceof Integer || raw instanceof Long || raw instanceof Short || raw instanceof Byte) {
            return NbtTag.of(((Number) raw).longValue());
        }
        if (raw instanceof Number number) {
            return NbtTag.of(number.doubleValue());
        }
        if (raw instanceof JSONObject object) {
            return toCompound(object);
        }
        if (raw instanceof org.json.JSONArray array) {
            cn.gfhnv.game.data.NbtList list = new cn.gfhnv.game.data.NbtList();
            for (int i = 0; i < array.length(); i++) {
                list.add(toTag(array.opt(i)));
            }
            return list;
        }
        return NbtTag.of(String.valueOf(raw));
    }

    /**
     * @param object JSON 对象
     * @return 复合标签
     */
    private static NbtCompound toCompound(JSONObject object) {
        NbtCompound compound = new NbtCompound();
        for (String key : object.keySet()) {
            Object raw = object.opt(key);
            if (raw == null || JSONObject.NULL.equals(raw)) {
                continue;
            }
            compound.put(key, toTag(raw));
        }
        return compound;
    }

    /* ------------------------------------------------------------------
     * 诊断（自测与"切换要付什么代价"那张表用）
     * ------------------------------------------------------------------ */

    /**
     * @param target 对象
     * @return 可配置的键数 / {@code /data} 的字段数 / 被 {@code @NoConfig} 挡住的键数
     */
    public static String census(Object target) {
        int fields = DataBridge.dataNames(target.getClass()).size();
        List<DataBridge.DataAccessor> candidates = candidates(target);
        return "可配置 " + candidates.size() + " 个（字段清单 " + fields
                + " 个，其中 " + blocked(target).size() + " 个被 @NoConfig 挡住）";
    }

    /**
     * 文件级（而不是实例级）的"挡板"清单：把类上所有 {@code @NoConfig} 字段的数据名连同理由打出来。
     * <p>
     * 与 {@link #blocked(Object)} 的区别：那个按<b>实例</b>算（能看见 {@link DataFlatten} 组件展开后的
     * 名字），这个按<b>类</b>算（只看本类与父类自己声明的字段）。自测两条都用，互为对照。
     *
     * @param type 类
     * @return 数据名 → 理由（按数据名排序）
     */
    public static Map<String, String> blockedOf(Class<?> type) {
        Map<String, String> blocked = new TreeMap<>();
        for (Map.Entry<Field, String> entry : annotated(type).entrySet()) {
            blocked.put(DataBridge.dataName(entry.getKey()), entry.getValue());
        }
        return blocked;
    }

    /**
     * "不可配置的字段"在源码里的原始清单（{@link Field} → 理由），供自测核对
     * "每个 {@code @NoConfig} 都写了理由"。
     *
     * @param type 类
     * @return 字段 → 理由（按继承顺序，父类在前）
     */
    public static Map<Field, String> annotated(Class<?> type) {
        Map<Field, String> annotated = new LinkedHashMap<>();
        for (Class<?> current = type; current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                NoConfig annotation = field.getAnnotation(NoConfig.class);
                if (annotation != null) {
                    annotated.put(field, annotation.value());
                }
            }
        }
        return Collections.unmodifiableMap(annotated);
    }

    /**
     * 一次补丁的记账。
     *
     * @param applied 写成功的（键路径 → 值的文本形式）
     * @param skipped 跳过 / 失败的（键路径 → 原因）
     * @author AI（DeepSeek）生成
     */
    public record Applied(Map<String, String> applied, Map<String, String> skipped) {

        /**
         * @return 有没有出错
         */
        public boolean isClean() {
            return skipped.isEmpty();
        }
    }
}
