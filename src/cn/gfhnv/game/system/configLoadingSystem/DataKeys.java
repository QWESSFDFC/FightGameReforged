package cn.gfhnv.game.system.configLoadingSystem;

import cn.gfhnv.game.data.DataBridge;
import cn.gfhnv.game.system.ElementSort;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * 配置键 ↔ {@code /data} 数据名的对照表（对外契约）。
 * <p>
 * <b>一条铁规矩：配置文件的键名一律复用 {@code /data} 的数据名，不发明第二套名字。</b>
 * 收益是只维护一套名字，而且用户可以用 {@code /data get entity @s} 看到当前生效值，
 * 再把它抄进配置文件。代价是这些键从此也是对外 API —— 改名会让玩家的配置文件静默失效，
 * 所以由自测里的「键名契约」几条断言兜住（见 {@code TestCommandSystem#testConfigKeyContract}）。
 * <p>
 * 本表把键分成五组，并附一份 {@link #META} 元数据（Java 类型 / 是否出现在 {@code /data} 里 / 说明）：
 * <ul>
 *     <li>{@link Base}：实体面板属性（名称、等级、抗性、成长、法力成长……）；</li>
 *     <li>{@link Derived}：用固定值覆盖公式算出来的结果（{@code hpMax} / {@code attack} / {@code defence} / {@code hp}）；</li>
 *     <li>{@link Temporary}：一次性的战斗加成。能配置，但 <b>第一版不写进默认文件</b>（只该由效果/技能改）；</li>
 *     <li>{@link ReadOnly}：{@code /data} 里看得到、但配置文件<b>不许改</b>的键（身份、行为对象、状态机）；</li>
 *     <li>{@link NotInData}：配置文件里能写、但 {@code /data} 里没有对应键的项（目前只有背包格数）。</li>
 * </ul>
 * <b>别名</b>：{@link Alias#ELEMENT} 允许写 {@code element} 指代 {@code elementSort}
 * （上游设计稿用的是 {@code element}）；{@link Alias#MANA_GROW} 允许写 {@code manaGrow.metal}
 * 指代 {@code metalManaGrow}。别名只是写法上的方便，<b>规范名永远是数据名</b>，
 * 生成器写出来的也只会是规范名。
 *
 * @author AI（DeepSeek）生成
 */
public final class DataKeys {

    /**
     * 元数据里用的 Java 类型名（与 {@code LivingThing} 上的字段类型一致）。
     */
    public static final String TYPE_STRING = "String";
    /**
     * 元数据里用的 Java 类型名：{@code long}。
     */
    public static final String TYPE_LONG = "long";
    /**
     * 元数据里用的 Java 类型名：{@code double}。
     */
    public static final String TYPE_DOUBLE = "double";
    /**
     * 元数据里用的 Java 类型名：枚举 {@link ElementSort}。
     */
    public static final String TYPE_ELEMENT = "ElementSort";

    /**
     * 五行元素名（小写）—— 配置里的 {@code manaGrow} 就用这几个键。
     * <p>
     * 每个名字对应 {@code LivingThing} 上的一对属性：
     * {@code <名字>Resistance}（抗性）、{@code <名字>Penetration}（穿透）、
     * {@code <名字>DamageEnhance}（增伤）、{@code <名字>ManaGrow}（法力成长）。
     * <p>
     * <b>来源是 {@link ElementSort}</b>（去掉 {@code UNIVERSAL}）：加第六个元素只改枚举，
     * 这里与 {@link EntityKeySpecs} 的五行键自动跟上。"两边一一对应"由自测钉住。
     */
    public static final List<String> ELEMENTS = buildElements();

    /**
     * 文件顶层键：整数版本号，用于将来的配置迁移。
     */
    public static final String VERSION = "version";
    /**
     * 文件顶层键：实体分组（键 = 注册表里的完整 id）。
     */
    public static final String ENTITIES = "entities";
    /**
     * 文件顶层键：技能分组（键 = {@code <实体完整id>#<技能名>}）。
     * <p>
     * 技能<b>有 id</b>（{@code Skill#getId()}，形如 {@code game_official_content:awakenCommonAttack}），
     * 但那是"注册表里那一条原型"的身份（{@code World#findSkill} 用它查）；
     * 配置要打的是<b>某只模板手上的那份技能</b>，同一个类会被多只模板各造一份、倍率各不相同，
     * 所以键仍然是"实体 id + 技能名"。技能名就是子类构造器里 {@code super("…", …)} 的第一个参数。
     * <b>改技能名 = 配置静默失效</b>，所以加载时会显式打印"找不到技能"。
     */
    public static final String SKILLS = "skills";
    /**
     * 实体 id 与技能名之间的分隔符（{@code game_official_content:playerOne#枪射击}）。
     * <p>
     * 技能名里可以出现 {@code #}，所以解析时<b>只按第一个</b>分隔符切分。
     */
    public static final String SKILL_SEPARATOR = "#";
    /**
     * 实体分组内的子块：面板属性。
     */
    public static final String BASE = "base";
    /**
     * 实体分组内的子块：用固定值覆盖派生结果。
     */
    public static final String DERIVED = "derived";
    /**
     * 实体分组内 {@link Base#MANA_GROW} 下面的子块：只写想改的元素。
     */
    public static final String MANA_GROW_BLOCK = "manaGrow";
    /**
     * 子块的分隔符（报错与统计里显示成 {@code base.level} 这种形式）。
     */
    public static final String SECTION_SEPARATOR = ".";
    /**
     * 分组名：{@code base}（面板属性）。
     */
    public static final String GROUP_BASE = BASE;
    /**
     * 分组名：{@code derived}（派生值）。
     */
    public static final String GROUP_DERIVED = DERIVED;
    /**
     * 分组名：{@code temporary}（一次性 / 战中的加成）。
     */
    public static final String GROUP_TEMPORARY = "temporary";

    /**
     * 实体分组内的子块：角色 / 怪物类<b>自己的配置字段</b>（{@link #CLASS_CONFIG} 里那些）。
     * <p>
     * <b>为什么要单开一段</b>：这些键不属于 {@code base} / {@code derived}（那两个是
     * {@code LivingThing} 的通用面板与派生值），而且它们<b>只对某个子类存在</b> ——
     * 写进 {@code base} 会让人以为每个模板都有 {@code coreflame}。
     * <p>
     * <b>与应用顺序无关</b>：这一段由 {@link ReflectionConfigBridge#patchExtra} 在通用表之后处理，
     * 键名就是 {@code /data} 的数据名（没有别名、没有嵌套块）。
     * <p>
     * <b>常量名为什么不叫 {@code CLASS_STATE}</b>：那个名字已经被下面那份"子类状态键全集"占了，
     * 而它同时还是一段子块名 —— 同一个类里两个同名常量是硬错误
     * （{@code check-sources.ps1} 的 {@code [DUP-FIELD]} 会当场抓住）。
     * 名字里的 {@code SECTION} 就是在提醒"这是子块名，不是键清单"。
     */
    public static final String CLASS_SECTION = "classState";

    /**
     * 实体分组内的<b>通用</b>子块：{@code base} / {@code derived} / {@code classState}。
     * <p>
     * 三者都认，是因为"只改一个速度"没必要套一层 {@code base}；生成器写出来的则是分块形式。
     * <p>
     * {@code classState} 装的是子类自己的配置字段（{@link #CLASS_CONFIG}），
     * 它<b>不在</b> {@link EntityKeySpecs} 那张通用表里 —— 那一段由
     * {@link ReflectionConfigBridge#patchExtra} 处理（键名 = {@code /data} 数据名）。
     * 把它登记进来的意义有两条：报「未知键」时认得这个块名；
     * 以及它让"子类字段写在块里还是写在裸键层"两种写法都能生效
     * （{@code {"classState":{"coreflame":20}}} 与 {@code {"coreflame":20}} 等价）。
     */
    public static final Set<String> BARE_SECTIONS =
            Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(BASE, DERIVED, CLASS_SECTION)));
    /**
     * 只读键的完整清单（{@link ReadOnly} 里那些常量的集合形式）。
     * <p>
     * 给自测当口径用：{@code /data} 里 dump 出来的每个键，必须要么能配置
     * （{@link #isConfigurable}）、要么在这一份清单里 ——
     * 否则就是"新加了属性，但没登记它归谁管"。
     * <p>
     * 有一类字段刻意<b>不</b>在这里：模组/角色自己的状态（例如 {@code Phainon} 的
     * {@code coreflame} 与 {@code FlameReaver} 的 {@code phaseTwo}）。
     * 它们是那个类自己的事，游戏侧的配置表不该替它们做主
     * （自测里逐条列出，见 {@code TestCommandSystem#testConfigKeyContract}）。
     * <p>
     * 2026-10-03 起这批字段拆成两半：出厂数值（{@link #CLASS_CONFIG}）已经放行到配置面，
     * 运行时状态（{@link #CLASS_RUNTIME}）继续挡 —— 但两半都仍然<b>不</b>在这里，
     * 因为 {@code /data} 面一个键都没变（这条清单的口径是"能不能配置"，不是"能不能看"）。
     */
    public static final Set<String> READ_ONLY = buildReadOnly();
    /**
     * <b>开放配置</b>：角色 / 怪物类自己的<b>出厂数值</b>（初始层数 / 上限 / 基础段数）。
     * <p>
     * <b>为什么这批能配</b>：每一个都是"这一局开始时该是多少"的<b>输入</b>，
     * 而不是打到一半才变的状态；而且每一个都<b>有 setter</b> ——
     * 写回走 {@link cn.gfhnv.game.data.DataBridge#applyTag}（setter 优先），
     * 所以对象自己的钳制（例如 {@code setIgnition} 夹 0…上限、{@code setCoreflame} 夹到
     * {@code coreflame_max}）照旧生效，这也是它们没被 {@code CLASS_RUNTIME} 挡住的原因。
     * <p>
     * <b>哪些"看着能配、其实会打折扣"</b>（自测逐条断言，见
     * {@code TestCommandSystem#testClassStateConfigKeys}，不许藏）：
     * <ul>
     *     <li>{@code ignition}：{@code whenFightEnds()} 会 {@code setIgnition(resetIgnition)}，
     *     所以它只在<b>这一局里</b>是初始值，下一局回到 {@code GameRules} 的
     *     {@code actorLiXiaoYan.resetIgnition}（这是机制，不是缺陷）；</li>
     *     <li>{@code extraAbilityTier}：{@code Phainon#whenFightStart} 每局 +1，
     *     所以写 3 开局实际是 4（配置是"基准值"，参与计算，不是最终值）；</li>
     *     <li>{@code coreflame}：同上（每局开局 +1）；{@code coreflame} / {@code scourge} /
     *     {@code scourge_max} 的 setter 会<b>向上限夹</b>，写超过上限的数会被悄悄压到上限；</li>
     *     <li>{@code soulscorch}：觉醒结束（{@code AwakeEndListener}）归零 —— 只在觉醒期间有意义。</li>
     * </ul>
     * <b>它们为什么没进 {@link EntityKeySpecs} 那张表</b>：表里的行要对<b>任意</b>
     * {@code LivingThing} 都成立，而 {@code coreflame} 只在 {@code Phainon} 上存在
     * （表驱动的写入不分类型，会把"写一个不存在的字段"变成静默无效或异常）。
     * 这批键因此走反射兜底：键名 = {@code /data} 数据名，写回 = setter 优先，
     * 实体上没有这个字段时<b>照旧报未知键</b>。
     * <p>
     * <b>不许烂掉</b>：这里多写一个键而那个键没有 setter，自测那条
     * 「开放的子类配置键必须有 setter」会红。
     */
    public static final Set<String> CLASS_CONFIG = buildClassConfig();
    /**
     * <b>明确排除</b>：角色 / 怪物 / 召唤物类自己的<b>运行时状态</b> —— 它们在 {@code /data} 里看得见、
     * 在反射面上也是标量，但<b>不是配置该管的东西</b>（是那个类的玩法进程，由它自己的技能与流程维护）。
     * <p>
     * <b>它为什么在配置层里</b>（2026-10-03「反射混血」第 3 步）：切换之后，"能不能配"由反射面决定，
     * 于是这批键会<b>默认变成可配置</b> —— 那等于让一份 {@code EntityData.json} 能把 boss 打成二阶段、
     * 把白厄的觉醒状态直接摆成 true。这个清单就是那条闸门，也是"反射面 ⊆ 允许清单"那条守卫的第三段
     * （前两段：能配的表 + 字段上的 {@code @NoConfig}）。
     * <p>
     * <b>2026-10-03 订正（用户意见：边界划错了）</b>：原来这一个清单把
     * "出厂数值"（{@code coreflame} / {@code coreflame_max} / {@code scourge} / {@code scourge_max} /
     * {@code soulscorch} / {@code ignition} / {@code extraAbilityTier}）与
     * "运行时状态"（{@code isAwaken} / {@code phaseTwo} / {@code charging} / {@code absorbed} …）
     * 一起挡住了，于是用户"想配火种上限"这种完全正当的需求被挡在同一道闸门外。
     * 现在按"有没有 setter、值是不是开局输入"拆成 {@link #CLASS_CONFIG}（放行，走反射面）
     * 与<b>本清单</b>（继续挡）。判据不是"这个字段属于谁"，而是
     * <b>"配了能不能算数"</b>。
     * <p>
     * <b>与 {@code @NoConfig} 的分工</b>：{@code @NoConfig} 是<b>那个类自己</b>说"这个字段不进配置"；
     * 本清单是<b>配置层</b>说"这一类字段不属于我"。两者都不改变 {@code /data} 面（键名契约不受影响）。
     * <p>
     * <b>不许烂掉</b>：新增一个子类状态键时，自测那条
     * 「角色/召唤物类自己的状态键没有冒出新面孔」会红（见 {@code TestCommandSystem}）。
     */
    public static final Set<String> CLASS_RUNTIME = buildClassRuntime();
    /**
     * 子类状态键的<b>全集</b>：{@link #CLASS_CONFIG}（已放行）+ {@link #CLASS_RUNTIME}（继续挡）。
     * <p>
     * <b>它现在的用途只剩一个</b>：自测的"dump 出来的键都能配置或在只读清单里"那条断言
     * 需要一份"这些是子类自己的键、不走通用表"的名单。配置层自己<b>不再</b>用它做判断 ——
     * 放行与挡住分别由上面两个清单说了算（这一条是 2026-10-03 订正的重点：
     * 旧代码只有这一个"整批挡住"的清单，于是把用户的正当需求一起挡了）。
     *
     * @see #CLASS_CONFIG
     * @see #CLASS_RUNTIME
     */
    public static final Set<String> CLASS_STATE = buildClassState();
    /**
     * 配置文件里能写、但 {@code /data} 里没有对应键的项。
     * <p>
     * 两项：① 五行法力成长写成 {@code {"metal":20,…}} 这种嵌套块（{@code /data} 那一侧是
     * 五个扁平的 {@code *ManaGrow}）；② 背包格数（那一侧用 {@code inventory} 里的格子元素表达）。
     */
    public static final Set<String> NOT_IN_DATA = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(Base.MANA_GROW, Base.INVENTORY_SLOTS)));
    /**
     * 全部"能配置"的键 → 元数据。
     * <p>
     * 同样来自 {@link EntityKeySpecs} 那张表（键名 / 类型 / 是否在 {@code /data} 里 / 说明）。
     */
    public static final Map<String, KeyMeta> META = EntityKeySpecs.meta();
    /**
     * 那些"用户很可能想配、但这一版刻意不支持"的键 → 一句写清"为什么 + 该走哪条路"。
     * <p>
     * 这份表的存在意义是<b>不许静默</b>：写进配置文件时直接报「未知键」，同时把理由打出来。
     * 没有它的话，用户只会看到一句干巴巴的"这个键不属于实体数据"，
     * 然后去猜是自己拼错了还是游戏不认。
     */
    private static final Map<String, String> NOT_CONFIGURABLE_HINT = buildHints();
    /**
     * 全部"能配置"的键（不含别名）→ 这一条属于哪一组。
     * <p>
     * <b>它不再是手写的</b>：直接来自 {@link EntityKeySpecs} 那张唯一的表 ——
     * 所以"声明能配"与"真的能配"不可能再对不上（2026-10-03 之前它们各写一遍，
     * 于是有 15 个键声明了能配、却没人读）。
     */
    private static final Map<String, String> GROUPS = EntityKeySpecs.groups();

    /**
     * 工具类，不允许实例化。
     */
    private DataKeys() {
    }

    /**
     * @return 五行名单（从 {@link ElementSort} 里去掉 {@code UNIVERSAL}）
     */
    private static List<String> buildElements() {
        List<String> names = new ArrayList<>();
        for (ElementSort sort : ElementSort.values()) {
            if (sort != ElementSort.UNIVERSAL) {
                names.add(sort.name().toLowerCase(Locale.ROOT));
            }
        }
        return Collections.unmodifiableList(names);
    }

    /**
     * @return "能配置"的键的集合（{@code base} + {@code derived} + {@code temporary}，不含别名）
     */
    public static Set<String> configurableKeys() {
        return META.keySet();
    }

    /**
     * @return 全部"能配置"的键 → 所属分组（{@code base} / {@code derived} / {@code temporary}）
     */
    public static Map<String, String> groups() {
        return GROUPS;
    }

    /**
     * @param key 键名
     * @return 这个键是不是能配置的
     */
    public static boolean isConfigurable(String key) {
        return META.containsKey(key);
    }

    /**
     * 把用户写法解析成规范键名。
     *
     * @param key        用户写的键
     * @param inManaGrow 当前是否位于 {@code manaGrow} 块里
     * @return 规范键名；认不出来时返回 {@code null}
     */
    public static String canonical(String key, boolean inManaGrow) {
        if (key == null) {
            return null;
        }
        String name = key.trim();
        if (inManaGrow) {
            String lower = name.toLowerCase();
            return ELEMENTS.contains(lower) ? lower + "ManaGrow" : null;
        }
        if (Alias.ELEMENT.equals(name)) {
            return Base.ELEMENT_SORT;
        }
        if (Alias.MANA_GROW_ALIAS.equals(name)) {
            return Base.MANA_GROW;
        }
        return META.containsKey(name) ? name : null;
    }

    /**
     * 建立"数据名 → Java 字段"的对照，并当场检查两边是否一致。
     * <p>
     * 一致性检查是这个类存在的意义：数据名一旦被改名（{@code @DataField} 或字段名），
     * 这张表里对应的键就会<b>找不到字段</b>，于是"配置静默失效"这件事在自测里立刻变红。
     * 只读键（{@link ReadOnly}）自然找不到字段，在 {@link #auditNotes()} 里报告，不算失败。
     *
     * @param type 要核对的数据对象类型（本测试传 {@code LivingThing.class}）
     * @return 数据名 → 那个字段（同名取最靠近子类的那个）
     */
    public static Map<String, Field> auditAgainst(Class<?> type) {
        Map<String, Field> found = new LinkedHashMap<>();
        collectFields(type, found);
        return found;
    }

    /**
     * 收集一个类（含父类链、含 {@code @DataFlatten} 组件）的"数据名 → 字段"对照。
     * <p>
     * 必须下潜进组件：五行那些字段现在住在 {@link cn.gfhnv.game.entity.AttributeProfile} 里，
     * 只扫实体自己的声明会让它们全部报"没有对应字段"（假失败）。
     * <b>不递归</b>组件里再套的组件 —— 与 {@code DataBridge} 的展开规则一致。
     *
     * @param type  类
     * @param found 目标表
     */
    private static void collectFields(Class<?> type, Map<String, Field> found) {
        for (Class<?> current = type; current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                found.putIfAbsent(DataBridge.dataName(field), field);
                if (field.isAnnotationPresent(cn.gfhnv.game.data.DataFlatten.class)) {
                    collectFields(field.getType(), found);
                }
            }
        }
    }

    /**
     * @param type       要核对的数据对象类型
     * @param declaredBy {@link #auditAgainst(Class)} 的结果
     * @return 文案形式的审计结论（一行一条），交给自测打印并断言
     */
    public static List<String> auditNotes(Class<?> type, Map<String, Field> declaredBy) {
        List<String> notes = new ArrayList<>();
        for (Map.Entry<String, KeyMeta> entry : META.entrySet()) {
            String key = entry.getKey();
            Field field = declaredBy.get(key);
            if (field == null) {
                if (entry.getValue().inData()) {
                    notes.add("[配置键没有对应字段] " + key);
                }
                continue;
            }
            if (key.equals(Base.MANA_GROW)) {
                continue;
            }
            if (key.equals(Base.INVENTORY_SLOTS)) {
                continue;
            }
            String actual = javaTypeName(field.getType());
            if (!actual.equals(entry.getValue().javaType())) {
                notes.add("[类型对不上] " + key + "：表里写 " + entry.getValue().javaType()
                        + "，字段是 " + actual);
            }
        }
        return notes;
    }

    /**
     * @param type 类型
     * @return 类型名（{@code String} 统一叫 {@code String}，枚举统一叫 {@code ElementSort}）
     */
    public static String javaTypeName(Class<?> type) {
        if (type == String.class || CharSequence.class.isAssignableFrom(type)) {
            return TYPE_STRING;
        }
        if (type == long.class || type == Long.class) {
            return TYPE_LONG;
        }
        if (type == double.class || type == Double.class || type == float.class || type == Float.class) {
            return TYPE_DOUBLE;
        }
        if (type.isEnum()) {
            return TYPE_ELEMENT;
        }
        return type.getSimpleName();
    }

    /**
     * @return 只读键清单（含五行那一对"临时属性"键）
     */
    private static Set<String> buildReadOnly() {
        Set<String> readOnly = new LinkedHashSet<>(Arrays.asList(
                ReadOnly.UUID, ReadOnly.ID, ReadOnly.INVENTORY, ReadOnly.TAGS, ReadOnly.ALIVE,
                ReadOnly.MANAS, ReadOnly.CONTROLLER, ReadOnly.DAMAGE_MODIFIERS,
                ReadOnly.DAMAGE_REDUCTIONS, ReadOnly.EFFECTS, ReadOnly.PARTICIPATE_FIGHT,
                ReadOnly.PRESENT_TURN, ReadOnly.EXTRA_DAMAGE,
                ReadOnly.ATTACK_ENHANCE_PERCENT, ReadOnly.DEFENCE_ENHANCE_PERCENT,
                ReadOnly.SPEED_ENHANCE_PERCENT, ReadOnly.HP_ENHANCE_PERCENT,
                ReadOnly.ATTACK_ENHANCE_AMOUNT, ReadOnly.DEFENCE_ENHANCE_AMOUNT,
                ReadOnly.SPEED_ENHANCE_AMOUNT, ReadOnly.HP_ENHANCE_AMOUNT,
                ReadOnly.CRITICAL_DMG_ENHANCE_PERCENT, ReadOnly.CRITICAL_DMG_ENHANCE_AMOUNT,
                ReadOnly.CRITICAL_RATE_ENHANCE_PERCENT, ReadOnly.CRITICAL_RATE_ENHANCE_AMOUNT,
                ReadOnly.INDIVIDUAL_MULTIPLE_AREA,
                Temporary.SHOW_SPECIAL_MES));
        for (String element : ELEMENTS) {
            readOnly.add(element + "Penetration");
            readOnly.add(element + "DamageEnhance");
        }
        return Collections.unmodifiableSet(readOnly);
    }

    /**
     * @return 放行到配置面的那一批子类字段（出厂数值，逐个都有 setter）
     */
    private static Set<String> buildClassConfig() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
                // 李晓焰（ActorLiXiaoYan）：开局【燃点】层数
                "ignition",
                // 白厄（Phainon）：火种 / 上限、灾厄 / 上限、觉醒段数
                "coreflame", "coreflame_max", "soulscorch", "scourge", "scourge_max",
                "extraAbilityTier")));
    }

    /**
     * @return 继续挡住的那一批子类字段（运行时状态：没有 setter，或者值由流程改写）
     */
    private static Set<String> buildClassRuntime() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
                // 李晓焰（ActorLiXiaoYan）：上限与"上次燃点"由规则表与流程维护（ignitionMax 没有 setter）
                "ignitionMax", "lastIgnition", "memorizedRate",
                // 白厄（Phainon）：觉醒状态、单调计数器与技能表
                "isAwaken", "appliedExtraAbilityTier", "absorbDamage", "pendingLastAttack",
                "extraTurns", "skills",
                // 盗火行者（FlameReaver）：阶段与献祭窗口，由它的 AI 与技能维护
                "disasterPower", "sacrificeWindow", "damageReductionLayers", "phaseTwo", "charging",
                // 容器（BrokenContainer）：种类与吸收/死亡通知，由它的流程维护
                "owner", "kind", "absorbed", "deathNotified", "painCost")));
    }

    /**
     * @return 两个子类清单的并集（只给自测的 dump 契约当口径用）
     */
    private static Set<String> buildClassState() {
        Set<String> all = new LinkedHashSet<>(CLASS_CONFIG);
        all.addAll(CLASS_RUNTIME);
        return Collections.unmodifiableSet(all);
    }

    /* ------------------------------------------------------------------
     * 与 /data 的对照（自测用，不属于运行期补丁路径）
     * ------------------------------------------------------------------ */

    /**
     * @return "想配但本版刻意不支持"的键 → 一句理由
     */
    private static Map<String, String> buildHints() {
        Map<String, String> hints = new LinkedHashMap<>();
        for (String element : ELEMENTS) {
            // ⚠️ 这段文案 2026-10-03 订正过：老文案说"配在这里只影响开局那一刻"，**是错的** ——
            //    副本（复制构造器）刻意不带这 10 个（AttributeProfile#copyFrom），
            //    所以配在注册表模板上对任何一局战斗都不生效。@NoConfig 的理由必须是真的。
            hints.put(element + "Penetration",
                    "临时属性，而且配了不生效：副本刻意不带它（AttributeProfile#copyFrom）、"
                            + "每局结束 resetTemporary 清零，全项目也没有写入点。要加穿透请用效果/技能。");
            hints.put(element + "DamageEnhance",
                    "临时属性，而且配了不生效：副本刻意不带它（AttributeProfile#copyFrom）、"
                            + "每局结束 resetTemporary 清零，全项目也没有写入点。要加增伤请用效果/技能。");
        }
        hints.put(ReadOnly.EXTRA_DAMAGE, "死字段：全项目零写入点，配了不会有任何反应。");
        hints.put(Temporary.SHOW_SPECIAL_MES, "接口对象：配置里没有能表达它的写法。");
        hints.put(ReadOnly.ALIVE, "存活标志由战斗流程维护，配置里改它不算「出厂值」。");
        hints.put(ReadOnly.MANAS, "五行法力列表由 initialMana() 按法力成长重建，"
                + "要改请用 *ManaGrow。");
        hints.put(ReadOnly.EFFECTS, "效果列表要用 /effect 命令加，配置里写不了。");
        hints.put(ReadOnly.TAGS, "标签权重表住在 TagConfig.json，不在 EntityData.json 里。");
        // 2026-10-03：角色/怪物类自己的运行时状态键也要给理由。
        // 这一段以前由 CLASS_STATE 的"什么都不说"承担 —— 用户只会看到"这个键不属于实体数据"，
        // 分不清是自己拼错了还是游戏刻意不认。现在把"为什么"逐个写出来。
        for (String key : CLASS_RUNTIME) {
            hints.put(key, runtimeHint(key));
        }
        return Collections.unmodifiableMap(hints);
    }

    /**
     * @param key 子类运行时状态键
     * @return "它为什么不能配"的一句话
     */
    private static String runtimeHint(String key) {
        return switch (key) {
            case "ignitionMax" -> "上限本身是规则：基准值来自 GameRules 的 actorLiXiaoYan.ignitionMax"
                    + "（低血上限另有一条），而且这个字段没有 setter。要改上限请改 GameRules.json，"
                    + "或者用 IModifyIgnitionMax 修正链。";
            case "lastIgnition" -> "运行时状态：上一次结算时的【燃点】层数（只用来算这一回合的差值），"
                    + "配它等于伪造一份历史。要改当前层数用 ignition。";
            case "memorizedRate" -> "运行时状态：由技能打进去的记忆倍率（不是出厂值）。";
            case "isAwaken" -> "运行时状态：觉醒由大招与 AwakeEndEvent 维护，而且这个字段没有 setter"
                    + "（数据名是 awaken，写不回去）。要在开局就处于觉醒请用技能与事件。";
            case "appliedExtraAbilityTier" -> "运行时记账：上次把 extraAbilityTier 折成多少加伤"
                    + "（防重复计算用）；配了会算错。要改基准段数用 extraAbilityTier。";
            case "absorbDamage", "pendingLastAttack", "extraTurns" -> "运行时状态：变身流程中的中间量"
                    + "（吸收/待结算最后一击/剩余额外回合），由技能与事件维护。";
            case "skills" -> "技能表是对象列表：加技能要构造合法对象并挂控制器，请用角色自己的构造函数。";
            case "disasterPower", "sacrificeWindow" -> "运行时状态：盗火行者的层数与献祭窗口，"
                    + "由它的技能与 AI 维护。";
            case "damageReductionLayers" -> "运行时状态：层数减伤，开局层数请在 GameRules.json 的"
                    + " flameReaver.damageReductionLayers 里改（构造/开局各读一次）。";
            case "phaseTwo", "charging" -> "运行时状态：阶段与蓄力由血量流程与 AI 维护 ——"
                    + "配它等于让 boss 一开局就进二阶段（那是机制，不是数值）。";
            case "owner", "kind" -> "身份键：final，而且由注册表决定（配置文件的键就是完整 id）。";
            case "absorbed", "deathNotified", "painCost" -> "运行时状态：容器的吸收/死亡通知/苦痛账本，"
                    + "由战斗流程维护。";
            default -> "运行时状态：由那个类自己的玩法流程维护，配置层不该替它做主。";
        };
    }

    /**
     * @param key 用户写进配置的键
     * @return "为什么本版不支持它"的一句话；没有特别说明时返回 {@code null}
     */
    public static String hintFor(String key) {
        return key == null ? null : NOT_CONFIGURABLE_HINT.get(key.trim());
    }

    /**
     * 技能数值的键（{@code SkillData.json} 里 {@code skills} 段下面的那些）。
     * <p>
     * <b>只放"纯数值"</b>：倍率 / 目标数 / 冷却 / 消耗 / 阵营 / AI 权重。
     * <b>不放行为</b>：{@code comeToEffect} 的写法、{@code canUse} 的条件、
     * 以及"每层【醉意】+1.5 倍率"这类由玩法推出来的动态倍率 —— 那些是技能实现的一部分，
     * 用 JSON 描述它们等于发明一门脚本语言。
     * <p>
     * 生命周期状态也<b>不</b>在这里：{@code nowCoolDown}（运行期剩余冷却）
     * 与 {@code extraDamage}（伤害算完清零）写进配置只会给用户一个"改了没反应"的坑。
     */
    public static final class SkillKeys {
        /**
         * 目标数：{@code 0}=自身、{@code -1}=全体、正数=选 N 个。
         */
        public static final String AIMS = "aims";
        /**
         * 总冷却回合数（{@code 0} = 每回合都能放）。
         */
        public static final String COOL_DOWN = "coolDown";
        /**
         * 生命值倍率。
         */
        public static final String HP_MAGNIFICATION = "hpMagnification";
        /**
         * 攻击力倍率。
         */
        public static final String ATK_MAGNIFICATION = "atkMagnification";
        /**
         * 防御力倍率。
         */
        public static final String DEF_MAGNIFICATION = "defMagnification";
        /**
         * 默认作用对象是不是敌方。
         */
        public static final String FOR_ENEMIES = "forEnemies";
        /**
         * 释放消耗（{@code {"amount":10,"element":"UNIVERSAL"}}；不写 = 保持构造器里的消耗）。
         */
        public static final String CONSUMED_MANA = "consumedMana";
        /**
         * 消耗里的数量。
         */
        public static final String MANA_AMOUNT = "amount";
        /**
         * 消耗里的元素（{@link ElementSort} 的枚举名）。
         */
        public static final String MANA_ELEMENT = "element";
        /**
         * AI 行为权重表（{@code {"ATTACK":5}}）。
         * <p>
         * <b>整块覆盖，不是逐项合并</b>：逐项合并会让"想删掉一个 tag"变得做不到。
         * <p>
         * 常量名刻意<b>不叫</b> {@code TAGS}：实体那一侧已经有个只读键叫这个名字，
         * 同一个文件里出现两个同名常量是硬错误（{@code check-sources.ps1} 的 {@code [DUP-FIELD]}）。
         */
        public static final String WEIGHT_TAGS = "tags";
        /**
         * 子类自己的额外数值旋钮（{@link cn.gfhnv.game.skill.NumericSkillTunable}）。
         * <p>
         * 目前只有 {@code RestorationHealthSkill} 用它：那个技能带一个
         * {@code neededManaScale}（释放门槛 = 要多少自身元素法力），
         * 它不在 {@link cn.gfhnv.game.skill.Skill} 基类的字段里，只能由子类自己报出键名。
         * <b>只对实现了那个接口的技能有效</b> —— 别的技能写这个键会被点名"未知键"。
         */
        public static final String NEEDED_MANA_SCALE = "neededManaScale";
    }

    /**
     * 游戏规则表（{@code GameRules.json}）的键。
     * <p>
     * 与实体/技能不同，这些键的默认值<b>不在任何对象上</b>，而在代码公式里 ——
     * 所以它们的实现路径是一张静态规则表（见 {@code GameRules}），不是对象补丁。
     * <p>
     * <b>改 {@link Formula} 与 {@link Mana} 两块等于重新标定</b>：
     * {@code PROJECT-ANALYSIS-2026-09.md} §4.2 那套"每 1.0 倍率 ≈ 880 伤害"的换算常数
     * 是在现在这几个字面量下测出来的，改完就全部作废。
     */
    public static final class Rule {

        /**
         * 伤害与面板公式的骨架常量。
         */
        public static final class Formula {
            /**
             * 面板：{@code hp = (等级-1) × 生命成长 + hpBase}。
             */
            public static final String HP_BASE = "formula.hpBase";
            /**
             * 面板：{@code 防御 = (等级-1) × 防御成长 + defenceBase}。
             */
            public static final String DEFENCE_BASE = "formula.defenceBase";
            /**
             * 面板：{@code 攻击 = attackBase + 攻击成长 × (等级-1)}。
             */
            public static final String ATTACK_BASE = "formula.attackBase";
            /**
             * 伤害：{@code ×(等级×factor + base) / (等级×factor + base + 目标防御)} 里的 factor。
             */
            public static final String LEVEL_DEFENCE_FACTOR = "formula.levelDefenceFactor";
            /**
             * 伤害：上面那个式子的 base。
             */
            public static final String LEVEL_DEFENCE_BASE = "formula.levelDefenceBase";
        }

        /**
         * 初始法力骨架（{@code LivingThing#initialMana}）。
         */
        public static final class Mana {
            /**
             * 主元素上限 = {@code 成长 × (等级-1) + mainBase}。
             */
            public static final String MAIN_BASE = "mana.mainBase";
            /**
             * 其余元素上限 = {@code 成长 × (等级-1) + otherBase}。
             */
            public static final String OTHER_BASE = "mana.otherBase";
        }

        /**
         * 盗火行者与它召唤的容器的机制旋钮。
         */
        public static final class FlameReaver {
            /**
             * 每次召唤消耗自身最大生命的比例。
             */
            public static final String SUMMON_HP_COST_RATE = "flameReaver.summonHpCostRate";
            /**
             * 每层【灾难之力】提供的加伤比例。
             */
            public static final String DISASTER_POWER_ATTACK_BONUS = "flameReaver.disasterPowerAttackBonus";
            /**
             * 【永别的决绝】初始减伤层数。
             */
            public static final String DAMAGE_REDUCTION_LAYERS = "flameReaver.damageReductionLayers";
            /**
             * 【永别的决绝】每层减伤比例。
             */
            public static final String DAMAGE_REDUCTION_PER_LAYER = "flameReaver.damageReductionPerLayer";
            /**
             * 场上容器数量上限（{@code 0} = 不限）。
             */
            public static final String CONTAINER_LIMIT = "flameReaver.containerLimit";
            /**
             * 每次召唤出现【完整容器】的概率。
             */
            public static final String COMPLETE_CONTAINER_CHANCE = "flameReaver.completeContainerChance";
            /**
             * 二阶段的【高额免伤】。
             */
            public static final String PHASE_TWO_DAMAGE_REDUCTION = "flameReaver.phaseTwoDamageReduction";
            /**
             * 盗火行者的基础生命上限。
             * <p>
             * <b>只在构造时读一次</b>：它不是"公式的一部分"，而是"这个模板出厂时的血量"——
             * 想改一只已经造出来的 BOSS 的血，用 {@code EntityData.json} 的 {@code derived.hpMax}。
             */
            public static final String BASE_HP_MAX = "flameReaver.baseHpMax";
            /**
             * 容器生命占盗火行者最大生命的比例。
             */
            public static final String CONTAINER_HP_RATIO = "flameReaver.containerHpRatio";
            /**
             * 容器攻击占盗火行者攻击的比例。
             */
            public static final String CONTAINER_ATTACK_RATIO = "flameReaver.containerAttackRatio";
            /**
             * 【完整容器】生命占盗火行者最大生命的比例。
             */
            public static final String COMPLETE_CONTAINER_HP_RATIO = "flameReaver.completeContainerHpRatio";
        }

        /**
         * 虫皇的机制旋钮。
         */
        public static final class InsectBoss {
            /**
             * 基础生命上限（只在构造时读一次，语义同 {@link FlameReaver#BASE_HP_MAX}）。
             * <p>
             * 常量名刻意不同名：{@code BASE_HP_MAX} 与 {@code HP_MAX} 在本文件里都已经被占了，
             * 同一个文件里出现两个同名常量会被 {@code check-sources.ps1} 判成硬错误。
             */
            public static final String BASE_HP = "insectBoss.baseHpMax";
        }

        /**
         * 李晓焰（{@link cn.gfhnv.game.officialStuff.customEntity.players.ActorLiXiaoYan}）的机制旋钮。
         * <p>
         * 这些值原本是那个类顶部的 {@code public static final} 常量，别处也引用它们
         * （三个技能都读 {@code ActorLiXiaoYan.HIGH_IGNITION} 这类"共用判据"），
         * 所以常量本身<b>保留原名</b>，只是值改成"启动时从规则表读一次，读不到用原字面量"。
         */
        public static final class ActorLiXiaoYan {
            /**
             * 【燃点】的常规上限。
             */
            public static final String IGNITION_MAX = "actorLiXiaoYan.ignitionMax";
            /**
             * 生命值偏低时的【燃点】上限。
             */
            public static final String LOW_HP_IGNITION_MAX = "actorLiXiaoYan.lowHpIgnitionMax";
            /**
             * 判定"生命值偏低"的比例。
             */
            public static final String LOW_HP_THRESHOLD = "actorLiXiaoYan.lowHpThreshold";
            /**
             * 开始吃"高燃点加成"的层数。
             */
            public static final String HIGH_IGNITION = "actorLiXiaoYan.highIgnition";
            /**
             * 高燃点时每个技能额外追加的伤害（按最大生命的比例）。
             */
            public static final String HIGH_IGNITION_BONUS_RATE = "actorLiXiaoYan.highIgnitionBonusRate";
            /**
             * 每层【燃点】提供的单体倍率加成。
             */
            public static final String AREA_PER_IGNITION = "actorLiXiaoYan.areaPerIgnition";
            /**
             * 触发免死的层数。
             */
            public static final String MEMORIZE_TRIGGER = "actorLiXiaoYan.memorizeTrigger";
            /**
             * 触发免死时消耗的层数。
             */
            public static final String MEMORIZE_COST = "actorLiXiaoYan.memorizeCost";
            /**
             * 触发免死时回复的最大生命比例。
             */
            public static final String MEMORIZE_HEAL_RATE = "actorLiXiaoYan.memorizeHealRate";
            /**
             * 战斗结束时回到的层数。
             */
            public static final String RESET_IGNITION = "actorLiXiaoYan.resetIgnition";
        }
    }

    /**
     * 面板属性（可配置，且默认文件里会写出来）。
     */
    public static final class Base {
        /**
         * 名称（改它会影响 {@code @e[name=…]} 与选人列表）。
         */
        public static final String NAME = "name";
        /**
         * 等级。<b>它会触发整组派生值重算</b>（见 {@code Entity#setLevel}）。
         */
        public static final String LEVEL = "level";
        /**
         * 质量（物理属性）。
         */
        public static final String MASS = "mass";
        /**
         * 类型（纯显示：{@code player} / {@code insect} / {@code boss} / {@code summon}）。
         */
        public static final String TYPE = "type";
        /**
         * 描述文本。
         */
        public static final String DESCRIPTION = "description";
        /**
         * 速度。
         */
        public static final String SPEED = "speed";
        /**
         * 元素属性（金木水火土）。数据名是这个，别名见 {@link Alias#ELEMENT}。
         */
        public static final String ELEMENT_SORT = "elementSort";
        /**
         * 生命成长系数（<b>不是当前血量</b>）。
         */
        public static final String HP_GROW = "hpGrow";
        /**
         * 攻击成长系数。
         */
        public static final String ATTACK_GROW = "attackGrow";
        /**
         * 防御成长系数。
         */
        public static final String DEFENCE_GROW = "defenceGrow";
        /**
         * 五行法力成长的汇总块（{@code {"metal":20,…}}）。
         */
        public static final String MANA_GROW = "manaGrow";
        /**
         * 背包格数。<b>不是 {@code /data} 键</b> —— 它由 {@code LivingThing#getInventory()} 里的格子数表达。
         */
        public static final String INVENTORY_SLOTS = "inventorySlots";
    }

    /**
     * 派生值：写进去 = 用固定值覆盖公式算出来的结果。
     * <p>
     * 应用顺序固定为 {@code hpMax} → {@code attack} → {@code defence} → {@code hp}，
     * 因为 {@code setHp} 会夹到 {@code getHpMax()}：{@code hp} 必须最后设。
     */
    public static final class Derived {
        /**
         * 生命上限。
         */
        public static final String HP_MAX = "hpMax";
        /**
         * 当前生命值。
         */
        public static final String HP = "hp";
        /**
         * 基础攻击力。
         */
        public static final String ATTACK = "attack";
        /**
         * 基础防御力。
         */
        public static final String DEFENCE = "defence";
    }

    /**
     * <b>面板属性</b>里那几个曾经"声明了能配置、其实没人读"的键。
     * <p>
     * 它们住在 {@code LivingThing} / {@code AttributeProfile} 的"面板属性"那一半：
     * {@code copy()} 会带过去、{@code LivingThing#clearTemporaryAttributes()}
     * <b>不</b>清它们（{@code LivingThing.java} 里那张"故意不在临时属性表里"的清单写明了这一点），
     * 所以配在模板上 = 整局生效，语义干净。
     * <p>
     * <b>这里只剩这五个。</b>另外那一批曾经也写在这里的键已经被摘掉了，理由分别是：
     * <ul>
     *     <li>五元素 {@code *Penetration} / {@code *DamageEnhance} —— <b>临时属性</b>：
     *     {@code AttributeProfile#resetTemporary()} 在第一局结束时清零，配在
     *     {@code EntityData.json} 里只影响"开局那一刻"，是给用户挖的坑；</li>
     *     <li>{@code extraDamage} —— <b>死字段</b>：全项目零写入点。</li>
     * </ul>
     * 它们现在都在 {@link ReadOnly} 里（{@code /data} 看得到、配置里写了会<b>显式报未知键</b>，
     * 见 {@code EntityDataPatcher#reportUnknownKeys}）。
     */
    public static final class Temporary {
        /**
         * 基础暴击伤害倍率加成。
         */
        public static final String CRITICAL_DMG = "criticalDMG";
        /**
         * 基础暴击率。
         */
        public static final String CRITICAL_RATE = "criticalRate";
        /**
         * 全属性增强系数。
         */
        public static final String ENHANCE = "enhance";
        /**
         * 防御削减系数。
         */
        public static final String DEFENSE_LOSS = "defenseLoss";
        /**
         * 穿透（面板属性那一半，不是五行的 {@code *Penetration}）。
         * <p>
         * 注意它<b>不在</b>五行里：{@code /data} 上的裸键 {@code penetration} 是全局穿透。
         */
        public static final String PENETRATION = "penetration";
        /**
         * "是否显示特殊消息"的接口对象（{@code IShowSpecialMes}）。
         * <b>它不是可以写进配置的键</b>：类型是接口，配置里没有能表达它的写法。
         */
        public static final String SHOW_SPECIAL_MES = "showSpecialMes";
    }

    /**
     * {@code /data} 里看得到、但配置文件<b>不许改</b>的键。
     * <p>
     * 三类：① 身份（改了就毁掉判等与选择器）；② 行为对象（控制器、闭包、物理矢量、引用）；
     * ③ 状态机内部字段（跑起来才有意义，写进配置只会造成"没有反应"）。
     * <p>
     * 这张表同时是自测的口径：{@code /data} 里 dump 出来的每个键，
     * 必须要么能配置、要么在这一组里，否则就是"新增属性忘了登记"。
     */
    public static final class ReadOnly {
        /**
         * 实例唯一标识（{@code final}）。
         */
        public static final String UUID = "uuid";
        /**
         * 注册表 id（配置里它是<b>键</b>，不是值）。
         */
        public static final String ID = "id";
        /**
         * 背包对象（格子数走 {@link Base#INVENTORY_SLOTS}）。
         */
        public static final String INVENTORY = "inventory";
        /**
         * 行为标签权重表（由 {@code ConfigLoader#loadConfig} 单独加载）。
         */
        public static final String TAGS = "tags";
        /**
         * 是否存活（由战斗流程维护）。
         */
        public static final String ALIVE = "alive";
        /**
         * 五行法力列表（由 {@code initialMana()} 重建）。
         */
        public static final String MANAS = "manas";
        /**
         * 行动控制器（技能表在里面）。
         */
        public static final String CONTROLLER = "controller";
        /**
         * 受到的伤害修正器闭包。
         */
        public static final String DAMAGE_MODIFIERS = "damageModifiers";
        /**
         * 减伤来源列表。
         */
        public static final String DAMAGE_REDUCTIONS = "damageReductions";
        /**
         * 身上的效果列表（用 {@code /effect}）。
         */
        public static final String EFFECTS = "effects";
        /**
         * 当前参与的战斗引用。
         */
        public static final String PARTICIPATE_FIGHT = "participateFight";
        /**
         * 当前回合条目。
         */
        public static final String PRESENT_TURN = "presentTurn";
        /**
         * 单次额外伤害（伤害算完清零）。
         * <p>
         * <b>死字段</b>：全项目零写入点（{@code LivingThing} 里自己写着"死字段"），
         * 所以它<b>不是</b>可配置键 —— 配了不会有任何反应。活着的那一套是
         * {@code Skill#extraDamage}，是另一个字段。
         */
        public static final String EXTRA_DAMAGE = "extraDamage";
        /**
         * 攻击增强（百分比）。
         */
        public static final String ATTACK_ENHANCE_PERCENT = "attackEnhancePercent";
        /**
         * 防御增强（百分比）。
         */
        public static final String DEFENCE_ENHANCE_PERCENT = "defenceEnhancePercent";
        /**
         * 速度增强（百分比）。
         */
        public static final String SPEED_ENHANCE_PERCENT = "speedEnhancePercent";
        /**
         * 生命增强（百分比）。
         */
        public static final String HP_ENHANCE_PERCENT = "hpEnhancePercent";
        /**
         * 攻击增强（固定值）。
         */
        public static final String ATTACK_ENHANCE_AMOUNT = "attackEnhanceAmount";
        /**
         * 防御增强（固定值）。
         */
        public static final String DEFENCE_ENHANCE_AMOUNT = "defenceEnhanceAmount";
        /**
         * 速度增强（固定值）。
         */
        public static final String SPEED_ENHANCE_AMOUNT = "speedEnhanceAmount";
        /**
         * 生命增强（固定值）。
         */
        public static final String HP_ENHANCE_AMOUNT = "hpEnhanceAmount";
        /**
         * 暴击伤害增强（百分比）。
         */
        public static final String CRITICAL_DMG_ENHANCE_PERCENT = "criticalDMGEnhancePercent";
        /**
         * 暴击伤害增强（固定值）。
         */
        public static final String CRITICAL_DMG_ENHANCE_AMOUNT = "criticalDMGEnhanceAmount";
        /**
         * 暴击率增强（百分比）。
         */
        public static final String CRITICAL_RATE_ENHANCE_PERCENT = "criticalRateEnhancePercent";
        /**
         * 暴击率增强（固定值）。
         */
        public static final String CRITICAL_RATE_ENHANCE_AMOUNT = "criticalRateEnhanceAmount";
        /**
         * 单次攻击的独立倍率区（由角色自己的构造器按玩法算出来）。
         */
        public static final String INDIVIDUAL_MULTIPLE_AREA = "individualMultipleArea";
    }

    /**
     * 别名（写法方便用，规范名永远是数据名）。
     */
    public static final class Alias {
        /**
         * {@code element} → {@link Base#ELEMENT_SORT}。
         */
        public static final String ELEMENT = "element";
        /**
         * {@code manaGrow.metal} → {@code metalManaGrow}（同理 wood / water / fire / dirt）。
         * <p>
         * 别名就是 {@link Base#MANA_GROW} 那个块名，值当然一样 ——
         * <b>但常量名不能也叫 {@code MANA_GROW_BLOCK}</b>：同一个文件里出现两个同名常量是硬错误
         * （{@code check-sources.ps1} 的 {@code [DUP-FIELD]} 会当场抓住）。
         */
        public static final String MANA_GROW_ALIAS = Base.MANA_GROW;
    }

    /**
     * 一条键的元数据。
     *
     * @param javaType Java 侧类型（见本类的 {@code TYPE_*} 常量）
     * @param inData   这个键是否出现在 {@code /data} 里
     * @param note     一句话说明（会被自测打印出来，所以写清楚"是什么"）
     */
    public record KeyMeta(String javaType, boolean inData, String note) {
    }
}
