package cn.gfhnv.game.data;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 把这个字段暴露给 {@code /data}（并可以改一个对外的名字）。
 * <p>
 * 默认规则是"**数据对象的所有字段都暴露**"（见 {@link DataBridge}），所以这个注解只用在两种情况：
 * <ul>
 *     <li>Java 字段名不好看/会误导（例如 {@code entityEffectList} → {@code effects}）；</li>
 *     <li>想给某个字段起个稳定的对外名字（**数据名是对外 API**：改名会让 {@code /data} 脚本与存档失效）。</li>
 * </ul>
 * 想排除某个字段用 {@link NoData}。
 *
 * @author AI（DeepSeek）生成
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface DataField {

    /**
     * 对外显示的名字。
     *
     * @return 名字；留空表示直接用 Java 字段名
     */
    String value() default "";
}
