package cn.gfhnv.game.mod.config;

import cn.gfhnv.game.mod.Mod;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个模组主类的<b>配置分组名</b>（读 {@code config/data/<id>.json} 里的哪一段）。
 * <p>
 * 用法（放在继承 {@link Mod} 的主类上，并实现 {@link ModDataAware}）：
 * <pre>
 * &#64;ModConfig(id = "drunkenSword")
 * public class DrunkenSwordMod extends Mod implements ModDataAware {
 *     &#64;Override
 *     public void applyConfig(ModConfigDocument cfg) {
 *         int stacks = cfg.getInt("drunkenSword/initialStacks", 2);
 *     }
 * }
 * </pre>
 * <b>优先级：注解 &gt; {@link Mod#getMOD_ID()}</b>。这个注解是<b>真的被反射读取</b>的
 * （{@code ConfigLoader#resolveModConfigId(Mod)} 里那句
 * {@code mod.getClass().getAnnotation(ModConfig.class)}），不是文档性的注解 ——
 * 它带 {@link RetentionPolicy#RUNTIME} 就是为了让游戏能在这里读到它：
 * <ol>
 *     <li>写了注解、且 {@link #id()} 去掉首尾空白后非空 → <b>用注解里的值</b>（去掉首尾空白）。
 *     {@code id} 与 {@code MOD_ID} 不一样时<b>以注解为准</b>：配置文件名与分组名都跟注解走；</li>
 *     <li>没写注解（或 {@code id} 是空白串）→ 退回 {@link Mod#getMOD_ID()}。
 *     现有模组都没写这个注解，行为与"注解被读取"之前完全一样；</li>
 *     <li>两者都拿不到（没写注解、{@code MOD_ID} 也是 {@code null} 或空串）→ 这个模组
 *     <b>拿不到配置</b>：游戏打印一行提示后跳过它（不抛异常、不影响别的模组），
 *     它的 {@code applyConfig} 也不会被调用。</li>
 * </ol>
 * 注解<b>不继承</b>（没有 {@code @Inherited}）：要写在模组主类<b>自己</b>头上，
 * 写在父类上游戏读不到。
 * <p>
 * <b>模组不能自带配置文件</b>：配置文件永远在游戏自己的 {@code config/data/} 下，
 * 不含模组目录里的任何文件（模组的 {@code main.json} 只负责描述模组本身）。
 *
 * @author AI（DeepSeek）生成
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ModConfig {

    /**
     * @return 配置分组名（即 {@code config/data/<id>.json} 里的文件名，不含 {@code .json}）；
     * 首尾空白会被去掉；留空（或只有空白）表示用 {@link Mod#getMOD_ID()}
     */
    String id() default "";
}
