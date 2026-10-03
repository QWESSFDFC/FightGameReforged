package cn.gfhnv.game.system.configLoadingSystem;

import cn.gfhnv.game.skill.Skill;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * <b>技能键的唯一定义处</b>：{@code SkillData.json} 里每个技能能写的键都在这张表里占一行。
 * <p>
 * <b>它替掉了两张平行的手写表</b>（这正是评估文档里的 D7）：
 * {@code SkillDataPatcher.patch()} 曾经手写 6 处 {@code applyXxx}，
 * {@code SkillDataPatcher.isKnown()} 又手写 7 个 {@code X.equals(name)} ——
 * 加一个技能键要同时改这两处，漏改 {@code isKnown()} 会得到一个"假报错"（写了它却说未知键），
 * 漏改 {@code patch()} 则静默失效，两条路都没有编译期保护。
 * 现在两者都只看这张表。
 * <p>
 * <b>两个块不进标量路径</b>：{@code consumedMana}（写成 {@code null} = 取消消耗，是一段有语义的
 * 三态逻辑）与 {@code tags}（整块覆盖）由 {@code SkillDataPatcher} 的专用分支处理 ——
 * 它们登记在表里只为一件事：报"未知键"时只有一张表。
 * <p>
 * <b>动态键</b>：技能自己报出来的键<b>不</b>进这张静态表 —— 每个技能类一套名字，表里放不下。
 * 两条：
 * <ul>
 *     <li>{@link cn.gfhnv.game.skill.NumericSkillTunable} 的<b>一个整数旋钮</b>
 *     （键名由 {@code extraNumericKey()} 给出，默认 {@link DataKeys.SkillKeys#NEEDED_MANA_SCALE}）：
 *     由 {@link #isKnown(String)} 单独放行，并由 {@code applyExtraNumeric} 分支处理；</li>
 *     <li>{@link cn.gfhnv.game.skill.SkillCoefficientTunable} 的<b>具名系数</b>
 *     （"每层打几段"、"每层给多少倍率"…）：名字由
 *     {@code coefficientValues()} 给出，<b>静态方法认不出来</b>（它拿不到"这是哪个技能"），
 *     所以由 {@code SkillDataPatcher#reportUnknownKeys} 拿<b>目标技能自己声明的名字</b>当白名单 ——
 *     写到不认识它的技能上照样会被点名"未知键"。</li>
 * </ul>
 *
 * @author AI（DeepSeek）生成
 */
public final class SkillKeySpecs {

    /**
     * 技能数值的<b>标量</b>键（顺序 = 应用顺序 = 默认文件里的书写顺序）。
     */
    public static final List<KeySpec<Skill>> SCALARS = List.of(
            spec(DataKeys.SkillKeys.AIMS, KeySpec.Kind.INT,
                    Skill::getAims, ints(Skill::setAims), "能选几个目标"),
            spec(DataKeys.SkillKeys.COOL_DOWN, KeySpec.Kind.INT,
                    Skill::getCoolDown, ints(Skill::setCoolDown), "冷却回合数"),
            spec(DataKeys.SkillKeys.HP_MAGNIFICATION, KeySpec.Kind.DOUBLE,
                    Skill::getHpMagnification, doubles(Skill::setHpMagnification), "按施法者生命上限算的倍率"),
            spec(DataKeys.SkillKeys.ATK_MAGNIFICATION, KeySpec.Kind.DOUBLE,
                    Skill::getAtkMagnification, doubles(Skill::setAtkMagnification), "按施法者攻击算的倍率"),
            spec(DataKeys.SkillKeys.DEF_MAGNIFICATION, KeySpec.Kind.DOUBLE,
                    Skill::getDefMagnification, doubles(Skill::setDefMagnification), "按施法者防御算的倍率"),
            spec(DataKeys.SkillKeys.FOR_ENEMIES, KeySpec.Kind.BOOLEAN,
                    Skill::isForEnemies, booleans(Skill::setForEnemies), "是否对敌方使用"));

    /**
     * 技能数值里的两个<b>块</b>（{@code write == null}：由专用分支处理）。
     */
    public static final List<KeySpec<Skill>> BLOCKS = List.of(
            spec(DataKeys.SkillKeys.CONSUMED_MANA, KeySpec.Kind.MANA_BLOCK,
                    null, null, "消耗块（写成 null = 取消消耗）"),
            spec(DataKeys.SkillKeys.WEIGHT_TAGS, KeySpec.Kind.TAGS,
                    null, null, "AI 权重块（整块覆盖）"));

    /**
     * 全部技能键（标量 + 两个块），只给"报未知键"与自测用。
     */
    public static final List<KeySpec<Skill>> ALL = buildAll();

    /**
     * 工具类，不允许实例化。
     */
    private SkillKeySpecs() {
    }

    /**
     * @param name 配置里写的键
     * @return 这个键是不是技能数值里认识的那几个（含动态旋钮键 {@link DataKeys.SkillKeys#NEEDED_MANA_SCALE}）
     */
    public static boolean isKnown(String name) {
        if (DataKeys.SkillKeys.NEEDED_MANA_SCALE.equals(name)) {
            return true;
        }
        for (KeySpec<Skill> key : ALL) {
            if (key.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param name 键
     * @return 表里那一行（动态旋钮键没有行，返回 {@code null}）
     */
    public static KeySpec<Skill> of(String name) {
        for (KeySpec<Skill> key : ALL) {
            if (key.name().equals(name)) {
                return key;
            }
        }
        return null;
    }

    /**
     * @return 表 + 两个块
     */
    private static List<KeySpec<Skill>> buildAll() {
        List<KeySpec<Skill>> keys = new java.util.ArrayList<>(SCALARS);
        keys.addAll(BLOCKS);
        return List.copyOf(keys);
    }

    /**
     * @return 一行声明（技能键没有分组概念，统一挂在 {@code skills} 段名下）
     */
    private static KeySpec<Skill> spec(String name, KeySpec.Kind kind,
                                       java.util.function.Function<Skill, Object> read,
                                       BiConsumer<Skill, Object> write, String note) {
        return new KeySpec<>(name, DataKeys.SKILLS, kind, read, write, note);
    }

    /**
     * @param write {@code int} 写入动作
     * @return 表里那一行要的写入器
     */
    private static BiConsumer<Skill, Object> ints(IntWrite write) {
        return (skill, value) -> write.set(skill, (Integer) value);
    }

    /**
     * @param write 浮点写入动作
     * @return 表里那一行要的写入器
     */
    private static BiConsumer<Skill, Object> doubles(DoubleWrite write) {
        return (skill, value) -> write.set(skill, ((Number) value).doubleValue());
    }

    /**
     * @param write 布尔写入动作
     * @return 表里那一行要的写入器
     */
    private static BiConsumer<Skill, Object> booleans(BooleanWrite write) {
        return (skill, value) -> write.set(skill, (Boolean) value);
    }

    /**
     * 一个 {@code int} 字段的写入动作。
     *
     * @author AI（DeepSeek）生成
     */
    @FunctionalInterface
    private interface IntWrite {

        /**
         * @param skill 技能
         * @param value 值
         */
        void set(Skill skill, int value);
    }

    /**
     * 一个 {@code double} 字段的写入动作。
     *
     * @author AI（DeepSeek）生成
     */
    @FunctionalInterface
    private interface DoubleWrite {

        /**
         * @param skill 技能
         * @param value 值
         */
        void set(Skill skill, double value);
    }

    /**
     * 一个 {@code boolean} 字段的写入动作。
     *
     * @author AI（DeepSeek）生成
     */
    @FunctionalInterface
    private interface BooleanWrite {

        /**
         * @param skill 技能
         * @param value 值
         */
        void set(Skill skill, boolean value);
    }
}
