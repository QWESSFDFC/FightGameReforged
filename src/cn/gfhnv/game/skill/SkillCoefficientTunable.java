package cn.gfhnv.game.skill;

import java.util.Map;

/**
 * 技能的<b>具名系数</b>：一个技能可以有多个旋钮，每个旋钮都有自己的名字。
 * <p>
 * <b>为什么需要它</b>：{@link Skill} 基类只有"三个倍率 / 目标数 / 冷却 / 消耗 / 阵营 / 权重"，
 * 而技能自己的算法里还有一批写死的数 —— "每层【毁伤】打几段"、"满层时那记收尾的总倍率"、
 * "每层【弑魂之炽】给多少倍率"、"施法者回多少血"……它们同样是纯数值、同样会随平衡变，
 * 却住在 {@code comeToEffect} 的方法体里，配置改不到。
 * <p>
 * <b>外置的是系数，不是公式</b>：{@code 倍率 = 基数 × (1 + 层数 × 每层增量)} 这种<b>算式</b>
 * 留在代码里（它是技能实现的一部分，还牵着"打谁、打几段、什么时候复位"），
 * 配置只能改 {@code 基数} / {@code 每层增量} 这样的<b>数</b>。
 * 用配置文件描述技能的行为逻辑等于发明一门脚本语言，本项目不做（见
 * {@code project_analyses/EXTERNAL-DATA-LOADING-2026-10.md} 第五节）。
 * <p>
 * <b>键的形态</b>：与 {@link NumericSkillTunable} 同一个约定 —— 名字由技能自己报出来，
 * 写在 {@code SkillData.json} 的技能对象里、<b>扁平</b>放着（与 {@code aims} /
 * {@code atkMagnification} 并列），不是另一个子块。于是"一个技能多个具名系数"
 * 就是同一个技能对象里多个键。
 * <p>
 * <b>实现的约定</b>：
 * <ul>
 *     <li>{@link #coefficientValues()} 返回的是<b>当前值</b>，不许有副作用；
 *     顺序 = 默认文件里的书写顺序（用 {@link java.util.LinkedHashMap}）；</li>
 *     <li>每个名字对应子类里的<b>一个字段</b>，字段的初始值就是出厂值 ——
 *     它必须与"把系数写进方法体"时的字面量<b>逐位相同</b>；</li>
 *     <li>实现类必须保证自己的 {@code copy()} 会把这些值一并复制（写在复制构造器里）：
 *     配置是打在<b>模板</b>上的，副本靠 {@code copy()} 白送，
 *     漏复制就会变成"改了没反应"；</li>
 *     <li>整数型系数的取整由实现自己负责（配置里写 {@code 4.5} 会得到 {@code 4}）。</li>
 * </ul>
 *
 * @author AI（DeepSeek）生成
 */
public interface SkillCoefficientTunable {

    /**
     * @return 这个技能认识哪些具名系数、当前各是多少（名字 → 值）
     */
    Map<String, Double> coefficientValues();

    /**
     * 写一个具名系数的当前值。
     *
     * @param name  系数名（必须是 {@link #coefficientValues()} 里已有的键）
     * @param value 新值
     */
    void setCoefficientValue(String name, double value);
}
