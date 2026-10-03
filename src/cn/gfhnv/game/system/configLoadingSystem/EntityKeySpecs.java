package cn.gfhnv.game.system.configLoadingSystem;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.system.ElementSort;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * <b>实体键的唯一定义处</b>：{@code EntityData.json} 里能写的每一个键都在这张表里占一行。
 * <p>
 * 三件事都从这一张表长出来，所以它们不可能互相漂：
 * <ul>
 *     <li>{@link ConfigDefaultWriter} 写默认文件（{@link SpecWriter} 遍历 {@link #BEFORE_DERIVED}
 *     与 {@link #DERIVED}）；</li>
 *     <li>{@link EntityDataPatcher} 打补丁（{@link SpecPatcher} 遍历同一批）；</li>
 *     <li>{@link DataKeys#META} / {@link DataKeys#GROUPS} 那套对外元数据
 *     （{@link #meta()} / {@link #groups()}）。</li>
 * </ul>
 * <b>顺序就是应用顺序</b>：{@code level} 排在最前（{@code Entity#setLevel} 会重算一遍三围），
 * {@code hp} 排在 {@code hpMax} 之后（{@code setHp} 会夹到 {@code getHpMax()}）。
 * <p>
 * <b>五行不再手写 switch</b>：抗性与法力成长各五行都用
 * {@link ElementSort} 循环生成（加第六个元素只需改枚举，这里自动跟上）。
 * <p>
 * <b>本类刻意不碰 {@link DataKeys} 的非编译期常量</b>（例如 {@code DataKeys.ELEMENTS}）：
 * {@link DataKeys#META} 会调用 {@link #meta()}，若这边又回头读 {@code DataKeys} 的静态字段，
 * 两个类的静态初始化就会互相等（拿到 {@code null}）。五行名单统一从 {@link ElementSort} 取，
 * "两边一致"由自测那条「五行清单与 ElementSort 的枚举值一一对应」钉住。
 *
 * @author AI（DeepSeek）生成
 */
public final class EntityKeySpecs {

    /**
     * 实体面板属性（{@link DataKeys#BASE} 那一层）与派生值之前的所有键，<b>顺序 = 应用顺序</b>。
     */
    public static final List<KeySpec<LivingThing>> BEFORE_DERIVED = buildBeforeDerived();

    /**
     * 用固定值覆盖公式结果的四个键，<b>顺序 = 应用顺序</b>（{@code hp} 必须最后）。
     */
    public static final List<KeySpec<LivingThing>> DERIVED = List.of(
            spec(DataKeys.Derived.HP_MAX, DataKeys.GROUP_DERIVED, KeySpec.Kind.LONG,
                    LivingThing::getHpMax, longs(LivingThing::setHpMax),
                    "生命上限（覆盖公式结果）"),
            spec(DataKeys.Derived.ATTACK, DataKeys.GROUP_DERIVED, KeySpec.Kind.LONG,
                    LivingThing::getAttack, longs(LivingThing::setAttack),
                    "基础攻击力（覆盖公式结果）"),
            spec(DataKeys.Derived.DEFENCE, DataKeys.GROUP_DERIVED, KeySpec.Kind.LONG,
                    LivingThing::getDefence, longs(LivingThing::setDefence),
                    "基础防御力（覆盖公式结果）"),
            spec(DataKeys.Derived.HP, DataKeys.GROUP_DERIVED, KeySpec.Kind.LONG,
                    LivingThing::getHp, longs(LivingThing::setHp),
                    "当前生命值（最后应用，会被上限夹）"));

    /**
     * 全部实体键（面板 + 派生），顺序同上。
     */
    public static final List<KeySpec<LivingThing>> ALL = buildAll();

    /**
     * 工具类，不允许实例化。
     */
    private EntityKeySpecs() {
    }

    /**
     * @return 实体键 → 元数据（{@link DataKeys#META} 就是它）
     */
    public static Map<String, DataKeys.KeyMeta> meta() {
        Map<String, DataKeys.KeyMeta> meta = new LinkedHashMap<>();
        for (KeySpec<LivingThing> key : ALL) {
            meta.put(key.name(), new DataKeys.KeyMeta(
                    key.kind().javaTypeName(), key.kind().inData(), key.note()));
        }
        return Collections.unmodifiableMap(meta);
    }

    /**
     * @return 实体键 → 分组（{@link DataKeys#groups()} 就是它）
     */
    public static Map<String, String> groups() {
        Map<String, String> groups = new LinkedHashMap<>();
        for (KeySpec<LivingThing> key : ALL) {
            groups.put(key.name(), key.group());
        }
        return Collections.unmodifiableMap(groups);
    }

    /**
     * @return 五行元素名（小写）—— 与 {@link DataKeys#ELEMENTS} 同一个来源（{@link ElementSort}）
     */
    public static List<String> elements() {
        List<String> names = new ArrayList<>();
        for (ElementSort sort : ElementSort.values()) {
            if (sort != ElementSort.UNIVERSAL) {
                names.add(sort.name().toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }

    /* ------------------------------------------------------------------
     * 表格本体
     * ------------------------------------------------------------------ */

    /**
     * @return 面板属性那张表
     */
    private static List<KeySpec<LivingThing>> buildBeforeDerived() {
        List<KeySpec<LivingThing>> keys = new ArrayList<>();
        keys.add(spec(DataKeys.Base.NAME, DataKeys.GROUP_BASE, KeySpec.Kind.STRING,
                LivingThing::getName, strings(LivingThing::setName),
                "名称（影响 @e[name=…] 与选人列表）"));
        keys.add(spec(DataKeys.Base.LEVEL, DataKeys.GROUP_BASE, KeySpec.Kind.LONG,
                LivingThing::getLevel, longs(LivingThing::setLevel),
                "等级；会触发整组派生值重算"));
        keys.add(spec(DataKeys.Base.MASS, DataKeys.GROUP_BASE, KeySpec.Kind.DOUBLE,
                LivingThing::getMass, doubles(LivingThing::setMass),
                "质量（物理属性）"));
        keys.add(spec(DataKeys.Base.TYPE, DataKeys.GROUP_BASE, KeySpec.Kind.STRING,
                LivingThing::getType, strings(LivingThing::setType),
                "类型（纯显示）"));
        keys.add(spec(DataKeys.Base.DESCRIPTION, DataKeys.GROUP_BASE, KeySpec.Kind.STRING,
                ConfigDefaultWriter::descriptionForFile, strings(LivingThing::setDescription),
                "描述文本（可能为空）"));
        keys.add(spec(DataKeys.Base.SPEED, DataKeys.GROUP_BASE, KeySpec.Kind.LONG,
                LivingThing::getSpeed, longs(LivingThing::setSpeed),
                "速度"));
        keys.add(spec(DataKeys.Base.ELEMENT_SORT, DataKeys.GROUP_BASE, KeySpec.Kind.ELEMENT,
                EntityKeySpecs::elementNameOf, EntityKeySpecs::setElement,
                "元素属性（别名 element）"));
        keys.add(spec(DataKeys.Base.HP_GROW, DataKeys.GROUP_BASE, KeySpec.Kind.DOUBLE,
                LivingThing::getHpGrow, doubles(LivingThing::setHpGrow),
                "生命成长系数（不是当前血量）"));
        keys.add(spec(DataKeys.Base.ATTACK_GROW, DataKeys.GROUP_BASE, KeySpec.Kind.DOUBLE,
                LivingThing::getAttackGrow, doubles(LivingThing::setAttackGrow),
                "攻击成长系数"));
        keys.add(spec(DataKeys.Base.DEFENCE_GROW, DataKeys.GROUP_BASE, KeySpec.Kind.DOUBLE,
                LivingThing::getDefenceGrow, doubles(LivingThing::setDefenceGrow),
                "防御成长系数"));
        for (String element : elements()) {
            keys.add(spec(element + "Resistance", DataKeys.GROUP_BASE, KeySpec.Kind.DOUBLE,
                    living -> resistanceOf(living, element),
                    doubles((living, value) -> setResistance(living, element, value)),
                    element + " 抗性"));
        }
        // manaGrow 块：一个块拆成五个扁平键，写不了单行，交给 EntityDataPatcher 的专用分支。
        // 登记它只为一件事 —— 报「未知键」时那张表认识它。
        keys.add(spec(DataKeys.Base.MANA_GROW, DataKeys.GROUP_BASE, KeySpec.Kind.MANA_BLOCK,
                null, null, "五行法力成长汇总块"));
        // 五个扁平的法力成长键：生成器写的就是它们（所以它们必须真的能被读回来）
        for (String element : elements()) {
            keys.add(spec(element + "ManaGrow", DataKeys.GROUP_BASE, KeySpec.Kind.DOUBLE,
                    living -> manaGrowOf(living, element),
                    doubles((living, value) -> setManaGrow(living, element, value)),
                    element + " 法力成长系数"));
        }
        // 背包格数：读得出值（生成器要写它），但写入要走"减格子要判空格"的专用分支
        keys.add(spec(DataKeys.Base.INVENTORY_SLOTS, DataKeys.GROUP_BASE, KeySpec.Kind.INVENTORY,
                living -> (long) living.getInventory().getSlots().size(), null,
                "背包格数（/data 里没有这个键）"));
        // 面板属性（2026-10-03 之前它们"声明了能配、其实没人读"，现在真的接上了）
        keys.add(spec(DataKeys.Temporary.CRITICAL_RATE, DataKeys.GROUP_TEMPORARY, KeySpec.Kind.DOUBLE,
                LivingThing::getCriticalRate, doubles(LivingThing::setCriticalRate),
                "基础暴击率（面板属性：copy() 会带、战斗结束不清，配了就整局生效）"));
        keys.add(spec(DataKeys.Temporary.CRITICAL_DMG, DataKeys.GROUP_TEMPORARY, KeySpec.Kind.DOUBLE,
                LivingThing::getCriticalDMG, doubles(LivingThing::setCriticalDMG),
                "暴击伤害倍率加成（面板属性，整局生效）"));
        keys.add(spec(DataKeys.Temporary.ENHANCE, DataKeys.GROUP_TEMPORARY, KeySpec.Kind.DOUBLE,
                LivingThing::getEnhance, doubles(LivingThing::setEnhance),
                "全属性增强系数（面板属性，整局生效）"));
        keys.add(spec(DataKeys.Temporary.PENETRATION, DataKeys.GROUP_TEMPORARY, KeySpec.Kind.DOUBLE,
                LivingThing::getPenetration, doubles(LivingThing::setPenetration),
                "穿透（面板属性，整局生效；五行的 *Penetration 是临时属性，不在这里）"));
        keys.add(spec(DataKeys.Temporary.DEFENSE_LOSS, DataKeys.GROUP_TEMPORARY, KeySpec.Kind.DOUBLE,
                LivingThing::getDefenseLoss, doubles(LivingThing::setDefenseLoss),
                "防御削减系数（面板属性，整局生效）"));
        return List.copyOf(keys);
    }

    /**
     * @return 面板表 + 派生表
     */
    private static List<KeySpec<LivingThing>> buildAll() {
        List<KeySpec<LivingThing>> keys = new ArrayList<>(BEFORE_DERIVED);
        keys.addAll(DERIVED);
        return List.copyOf(keys);
    }

    /* ------------------------------------------------------------------
     * 行的构造与取值小工具（让每一行都短到能一眼看完）
     * ------------------------------------------------------------------ */

    /**
     * @return 一行声明
     */
    private static KeySpec<LivingThing> spec(String name, String group, KeySpec.Kind kind,
                                             java.util.function.Function<LivingThing, Object> read,
                                             BiConsumer<LivingThing, Object> write, String note) {
        return new KeySpec<>(name, group, kind, read, write, note);
    }

    /**
     * @param write 整数写入动作
     * @return 表里那一行要的写入器
     */
    private static BiConsumer<LivingThing, Object> longs(LongWrite write) {
        return (living, value) -> write.set(living, ((Number) value).longValue());
    }

    /**
     * @param write 浮点写入动作
     * @return 表里那一行要的写入器
     */
    private static BiConsumer<LivingThing, Object> doubles(DoubleWrite write) {
        return (living, value) -> write.set(living, ((Number) value).doubleValue());
    }

    /**
     * @param write 字符串写入动作
     * @return 表里那一行要的写入器
     */
    private static BiConsumer<LivingThing, Object> strings(StringWrite write) {
        return (living, value) -> write.set(living, (String) value);
    }

    /**
     * @return 元素的数据名；没有元素返回 {@code null}（生成器会跳过这个键）
     */
    private static Object elementNameOf(LivingThing living) {
        return living.getElementSort() == null ? null : living.getElementSort().name();
    }

    /**
     * 写元素属性：必须连带重建法力列表（上限跟元素走）。
     *
     * @param living 实体
     * @param value  元素（{@link KeySpec.Kind#ELEMENT} 收敛过，一定是 {@link ElementSort}）
     */
    private static void setElement(LivingThing living, Object value) {
        living.setElementSort((ElementSort) value);
        living.initialMana();
    }

    /**
     * @param living  实体
     * @param element 元素名（小写）
     * @return 对应抗性
     */
    private static double resistanceOf(LivingThing living, String element) {
        return switch (element) {
            case "metal" -> living.getMetalResistance();
            case "wood" -> living.getWoodResistance();
            case "water" -> living.getWaterResistance();
            case "fire" -> living.getFireResistance();
            default -> living.getDirtResistance();
        };
    }

    /**
     * @param living  实体
     * @param element 元素名（小写）
     * @param value   值
     */
    private static void setResistance(LivingThing living, String element, double value) {
        switch (element) {
            case "metal" -> living.setMetalResistance(value);
            case "wood" -> living.setWoodResistance(value);
            case "water" -> living.setWaterResistance(value);
            case "fire" -> living.setFireResistance(value);
            default -> living.setDirtResistance(value);
        }
    }

    /**
     * @param living  实体
     * @param element 元素名（小写）
     * @return 该元素的法力成长系数
     */
    private static double manaGrowOf(LivingThing living, String element) {
        return switch (element) {
            case "metal" -> living.getMetalManaGrow();
            case "wood" -> living.getWoodManaGrow();
            case "water" -> living.getWaterManaGrow();
            case "fire" -> living.getFireManaGrow();
            default -> living.getDirtManaGrow();
        };
    }

    /**
     * 按元素名写法力成长系数（{@code manaGrow} 块与五个扁平键共用这一个实现）。
     *
     * @param living  实体
     * @param element 元素名（小写）
     * @param value   值
     */
    public static void setManaGrow(LivingThing living, String element, double value) {
        switch (element) {
            case "metal" -> living.setMetalManaGrow(value);
            case "wood" -> living.setWoodManaGrow(value);
            case "water" -> living.setWaterManaGrow(value);
            case "fire" -> living.setFireManaGrow(value);
            case "dirt" -> living.setDirtManaGrow(value);
            default -> {
            }
        }
    }

    /**
     * 一个 {@code long} 字段的写入动作。
     *
     * @author AI（DeepSeek）生成
     */
    @FunctionalInterface
    private interface LongWrite {

        /**
         * @param living 实体
         * @param value  值
         */
        void set(LivingThing living, long value);
    }

    /**
     * 一个 {@code double} 字段的写入动作。
     *
     * @author AI（DeepSeek）生成
     */
    @FunctionalInterface
    private interface DoubleWrite {

        /**
         * @param living 实体
         * @param value  值
         */
        void set(LivingThing living, double value);
    }

    /**
     * 一个字符串字段的写入动作。
     *
     * @author AI（DeepSeek）生成
     */
    @FunctionalInterface
    private interface StringWrite {

        /**
         * @param living 实体
         * @param value  值
         */
        void set(LivingThing living, String value);
    }
}
