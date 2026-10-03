package cn.gfhnv.game.mod.config;

/**
 * <b>可选</b>接口：模组想读自己的配置就实现它，游戏会在配置加载完成后、
 * {@link cn.gfhnv.game.mod.Mod#invokeWhenLoaded()} <b>之前</b>调用一次 {@link #applyConfig}。
 * <p>
 * <b>为什么是可选接口而不是往 {@code Mod} 上加抽象方法</b>：改 {@code Mod} 的抽象契约
 * 会让现有模组全部编译不过；可选接口让"不写配置的模组"零成本 ——
 * 现有模组一个字都不用改，游戏侧 {@code instanceof} 判一下就跳过。
 * <p>
 * <b>三条约定</b>：
 * <ol>
 *     <li>没有配置文件时<b>照样调用</b> {@link #applyConfig}，文档是空的（所有读取都返回默认值）——
 *     模组作者不用写"有没有文件"的分支；</li>
 *     <li>模组只能读<b>自己的分组</b>与共享区 {@code common/}，读不到别的模组的分组；</li>
 *     <li>本方法抛出的任何异常（含 {@link Error}）只会中断<b>这个模组</b>的配置，
 *     其它模组照常加载 —— 但请自己 catch 并打印，别静默吞掉。</li>
 * </ol>
 *
 * @author AI（DeepSeek）生成
 */
public interface ModDataAware {

    /**
     * 读自己的配置。<b>没有配置文件时也会被调用</b>，此时文档里什么都没有。
     *
     * @param config 这个模组的配置文档（路径用 {@code /} 分隔，读不到就返回你给的默认值）
     */
    void applyConfig(ModConfigDocument config);

    /**
     * 本模组想自动生成的<b>默认配置</b>（可选）。
     * <p>
     * 默认返回 {@code null} = "本模组没有默认配置可生成"，所以<b>现有模组零改动</b>。
     * 返回非 {@code null} 时，游戏只在这个模组的配置文件<b>不存在</b>时把它写盘
     * （{@code config/data/<分组名>.json}），写完照常读回来交给 {@link #applyConfig}
     * —— 于是"第一次运行就有一份可改的默认配置"，与 {@code config/gameConfig/*.json} 的行为一致。
     * <p>
     * <b>为什么返回 {@code Map} 而不是 {@code org.json.JSONObject}</b>：
     * 模组不该为了"声明两个默认值"而依赖 {@code org.json}。游戏侧会把它包成
     * {@code {"version":1,"<分组名>":{...}}} 再写盘。
     * <p>
     * ⚠️ <b>只在文件不存在时写</b>；已存在的文件<b>一个字节都不动</b>（玩家改过的值不会被覆盖）。
     * 写失败只报一行，不影响游戏与其它模组。
     *
     * @return 本模组自己的那段默认配置（键值对），或 {@code null} 表示不生成
     */
    default java.util.Map<String, Object> defaultConfig() {
        return null;
    }
}
