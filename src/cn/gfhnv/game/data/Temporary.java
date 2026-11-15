package cn.gfhnv.game.data;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>第三个面</b>：只让 {@code /data} 写、<b>不进配置文件</b>，而且语义上属于"临时属性"（2026-11-14 第 60 批）。
 * <p>
 * 一个字段有<b>三个面</b>，本包用三个注解各管一面：
 * <table border="1">
 *     <caption>三个面</caption>
 *     <tr><th>注解</th><th>配置文件</th><th>{@code /data} 读</th><th>{@code /data} 写</th></tr>
 *     <tr><td>（不写）</td><td>能配</td><td>能看</td><td>能写</td></tr>
 *     <tr><td>{@link NoConfig}</td><td>不能配</td><td>能看</td><td>能写</td></tr>
 *     <tr><td>{@link Temporary}</td><td><b>不能配</b></td><td>能看</td><td><b>能写</b></td></tr>
 *     <tr><td>{@link NoData}</td><td>不能配</td><td>看不见</td><td>不能写</td></tr>
 * </table>
 * <p>
 * <b>它和 {@link NoConfig} 的区别只有"语义标签"</b>：配置面两者都关，{@code /data} 两者都开。
 * 那为什么还要它？因为"临时属性"和"运行期上下文（uuid / 当前回合 / 控制器）"是两种完全不同的东西，
 * 以前它们都写 {@code @NoConfig}，于是自测分不清"这个键该不该出现在配置文件里"与"这个键是一次性加成"。
 * 有了这个标签，自测可以断言：<b>标了 {@code @Temporary} 的字段必须能被 {@code /data} 看到、且不能配</b>
 * （见 {@code EntityCoreTests#testTemporaryAnnotationContract}）。
 * <p>
 * <b>它不改变可写性</b>：{@code /data} 能不能写仍然看"有没有 setter / 是不是 final"，
 * 与这个注解无关（所以容器字段用 {@code addXxx(...)} 这类 API 改，而不是 {@code /data merge}）。
 *
 * @author AI（DeepSeek）生成；设计稿作者：仓库作者
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Temporary {

    /**
     * 为什么它是临时属性（自测会打印这句话，所以写清楚"谁加的、什么时候清"）。
     *
     * @return 理由
     */
    String reason();
}
