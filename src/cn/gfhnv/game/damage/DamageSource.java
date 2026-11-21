package cn.gfhnv.game.damage;

/**
 * 伤害的<b>来源</b>（"为什么挨这一下"）。
 * <p>
 * 与 {@link Damage#getSourceEntity()} 是<b>两件事</b>：那个回答"谁打的"，这个回答"用什么打的"。
 * 技能实现它（名字就是技能名）；DOT / 系统伤害也各自实现（效果名、"系统伤害"）。
 * <p>
 * 目前只有一个消费场景：日志与审计要一个可读名字。它<b>不参与</b>任何数值计算。
 *
 * @author AI（DeepSeek）生成；设计稿作者：仓库作者
 */
public interface DamageSource {

    /**
     * @param name 可读名字
     * @return 一个只有名字的来源（没有对应技能/效果对象时用）
     */
    static DamageSource named(String name) {
        return () -> name;
    }

    /**
     * @return 可读的来源名（技能名 / 效果名 / "系统伤害"）
     */
    String sourceName();
}
