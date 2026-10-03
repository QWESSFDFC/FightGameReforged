package cn.gfhnv.game.skill;

import java.util.Collections;
import java.util.Map;

/**
 * 技能上的<b>整数额外数值旋钮</b>：数值住在子类的私有字段里、{@link Skill} 基类没有它的位置时，
 * 让子类实现本接口，外部配置就能改到它。
 * <p>
 * 为什么要有这个口子：{@link Skill} 基类只有"三个倍率 / 目标数 / 冷却 / 消耗 / 阵营 / 权重"
 * 这几个字段，而有些技能自己带一个数值 —— 例如
 * {@code RestorationHealthSkill} 的"释放门槛（要多少自身元素法力）"。
 * 那种值同样是纯数值、同样会随平衡变，不该被漏在配置之外。
 * <p>
 * <b>它只有一个值</b>：一个技能要暴露<b>多个</b>数值（"每层打几段"、"每层给多少倍率"…）
 * 时用它自己和 {@link SkillCoefficientTunable} 的关系看 ——
 * 本接口<b>继承</b>了 {@link SkillCoefficientTunable}，并把那一个整数旋钮
 * 适配成一个具名系数，于是配置层与默认文件生成器<b>只有一条路</b>
 * （{@link SkillCoefficientTunable}），而"整数值必须写整数"这条老口径由
 * {@code SkillDataPatcher} 单独保住。
 * <p>
 * <b>实现的约定</b>：getter / setter 都是<b>当前值</b>的读写，不许有副作用；
 * 实现类必须保证自己的 {@code copy()} 会把这个值一并复制（否则"打在模板上的配置
 * 传不到选人时复制出去的副本"）。
 *
 * @author AI（DeepSeek）生成
 */
public interface NumericSkillTunable extends SkillCoefficientTunable {

    /**
     * @return 这个技能自己的额外数值
     */
    int getExtraNumericValue();

    /**
     * 设置这个技能自己的额外数值。
     *
     * @param value 新值
     */
    void setExtraNumericValue(int value);

    /**
     * @return 这个数值在配置里叫什么（写进报错文案，用户才知道改的是哪个键）
     */
    String extraNumericKey();

    /**
     * 把这一个整数旋钮适配成具名系数（配置层只有一条路）。
     *
     * @return 只有一个键的映射：{@link #extraNumericKey()} → 当前值
     */
    @Override
    default Map<String, Double> coefficientValues() {
        return Collections.singletonMap(extraNumericKey(), (double) getExtraNumericValue());
    }

    /**
     * 把这一个整数旋钮适配成具名系数（配置层只有一条路）。
     * <p>
     * 名字不匹配时什么也不做：调用方（{@code SkillDataPatcher}）只会拿
     * {@link #coefficientValues()} 里的名字来写。
     *
     * @param name  系数名
     * @param value 新值（截断成 {@code int}）
     */
    @Override
    default void setCoefficientValue(String name, double value) {
        if (extraNumericKey().equals(name)) {
            setExtraNumericValue((int) value);
        }
    }
}
