package cn.gfhnv.game.data;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 让这个字段<b>不</b>出现在配置文件（{@code EntityData.json} 这类）里。
 * <p>
 * <b>它和 {@link NoData} 是两件事，刻意分成两个注解</b>（2026-10-03 的「反射驱动配置」实验结论）：
 * <ul>
 *     <li>{@link NoData} 管的是 <b>{@code /data} 这一面</b>：字段连 dump 都不出现。
 *     被它挡掉的字段，配置面自然也没了 —— 所以 {@code @NoData} <b>蕴含</b>「不可配置」；</li>
 *     <li>本注解管的是 <b>配置这一面</b>，而 {@code /data} 照旧看得见、改得动。
 *     用它的是「是数据、也能给人看，但<b>不该由配置文件决定</b>」的字段。</li>
 * </ul>
 * 三面的差别（{@code /data get} / {@code /data merge} / 配置文件）是两个注解的组合，不是一套：
 * <pre>
 *   字段默认            → 三面全开（这正是「加一个字段 = 加一个加载项」的关键）
 *   &#64;NoConfig          → /data 全开，配置面关闭
 *   &#64;NoData            → 三面全关
 * </pre>
 * <b>为什么不复用 {@code @NoData}</b>：两者的读者完全不同。
 * {@link DataBridge} 读 {@code @NoData}（它决定"这个字段算不算数据"），
 * 配置侧的反射桥读本注解（它决定"这个字段能不能由玩家改"）。
 * 合成一个的话，想「挡住配置」就必然连 {@code /data} 一起挡掉 ——
 * 于是 {@code uuid} / {@code alive} 这类<b>可以看、但不该配</b>的字段只能二选一：
 * 要么放开配置（危险），要么在 {@code /data} 里消失（破坏 122 条键名断言）。
 * <p>
 * <b>否定式（opt-out）是刻意的</b>：默认开放，所以「加一个 Java 字段」就自动多了一个可配置项，
 * 配置文件那边 0 处改动。代价是<b>每加一个字段都要想一次"它能给人配吗"</b> ——
 * 忘了想不会报错，只会多一个能配的键。所以本注解带一个 {@link #value()} 说明理由，
 * 自测里有一条断言要求它非空（防止有人只写注解不写理由）。
 * <p>
 * <b>不改变任何数据名</b>：本注解只过滤配置面，{@code /data} 的键名一个字都不动
 * （见 {@code notes_for_llm/70-DATA.md} §5.10：数据名是对外 API）。
 *
 * @author AI（DeepSeek）生成
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface NoConfig {

    /**
     * 为什么这个字段不该出现在配置文件里（会被自测打印出来，所以写清楚）。
     *
     * @return 理由
     */
    String value();
}
