package cn.gfhnv.game.data;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 让这个字段<b>不</b>出现在 {@code /data} 里。
 * <p>
 * 用在"是数据但不想给人看/不想让人改"的字段上（例如内部缓存、标记位）。
 * 注意：{@code static} 字段本来就不会被读出来，不需要这个注解。
 *
 * @author AI（DeepSeek）生成
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface NoData {
}
