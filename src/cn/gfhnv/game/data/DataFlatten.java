package cn.gfhnv.game.data;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 把一个<b>组件对象</b>的字段"平铺"进父对象的复合标签，<b>不带前缀</b>。
 * <p>
 * 背景：{@link DataBridge} 默认按<b>字段名</b>平铺成键（见 {@link DataField}），
 * 所以只要把属性字段搬进别的类，{@code /data} 的键名就会跟着变
 * （{@code fireResistance} 会变成 {@code attributes.fireResistance}）——
 * 而<b>数据名是对外 API</b>，改名等于破坏 {@code /data} 脚本与将来的存档（见 TIPS §5.10）。
 * <p>
 * 用法：在被平铺的组件字段上写这个注解，组件<b>自己声明</b>的字段会被逐个并进父复合标签，
 * 键名照旧按组件字段自己的 {@link DataField} 改名（没有就用 Java 字段名）：
 * <pre>
 *     &#64;DataFlatten
 *     private final AttributeProfile attributes = new AttributeProfile();
 * </pre>
 * 于是 {@code /data get entity @s} 里仍然是 {@code fireResistance}、{@code penetration}、
 * {@code metalManaGrow}…… 而不是
 * {@code attributes.fireResistance}。<b>键名与搬字段之前完全一致</b>。
 * <p>
 * 现成例子：
 * {@link cn.gfhnv.game.entity.AttributeProfile}（五行组：5 抗性 + 5 穿透 + 5 增伤 + 5 法力成长；
 * 全局组：全属性穿透 / 全属性增伤 / 暴击伤害）。
 * <p>
 * <b>只影响"读"（对象 → 标签）</b>：写回（{@code /data merge|modify}）走的是
 * {@link DataBridge} 的字段查找（先找父对象自己，再下潜进本注解标记的组件），
 * 所以父对象只要<b>保留同名的转发 setter</b>，写回就照旧命中，键名也不变。
 * <p>
 * <b>⚠️ 用它的组件类型必须同时加进 {@link DataBridge#isDataObject} 的名单</b>：
 * 那个名单是"哪些对象算数据"的白名单，不在里面的话组件会被当成行为对象<b>整个跳过</b> ——
 * 症状是这些键在 {@code /data} 里<b>静默消失</b>（不报错、只是查不到），
 * 而 {@link DataBridge#dataNames} 照旧列出它们（那份清单是按字段算的，不看白名单）。
 * <p>
 * 与 {@link DataField} 的区别：{@code @DataField} 改<b>一个字段对外的名字</b>；
 * 本注解把<b>一整组字段</b>摊到父标签上（不产生自己的键）。
 * 想排除某个字段仍然用 {@link NoData}（组件字段上照常生效）。
 * 只展开组件<b>自己声明</b>的字段，不往组件的组件里递归。
 *
 * @author AI（DeepSeek）生成
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface DataFlatten {
}
