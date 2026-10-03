package cn.gfhnv.debug_tools;

import cn.gfhnv.game.data.DataBridge;
import cn.gfhnv.game.data.NbtTag;
import cn.gfhnv.game.data.NoConfig;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.system.ElementSort;

import java.util.Map;

/**
 * 「反射驱动配置」实验的探针实体：一个带<b>全新字段</b>的 {@link LivingThing}。
 * <p>
 * <b>为什么要有它</b>：实验要回答的最后一个问题是"加一个可配置项要改几处"。
 * 光看代码回答不了 —— 得真的造一个字段出来，然后看有几处非改不可。
 * <p>
 * <b>{@link #resonanceCoefficient} 是全新的</b>：它不在这里之外的任何地方出现 ——
 * {@code DataKeys} 没有它的常量、{@code EntityKeySpecs} 没有它那一行、
 * {@code ConfigDefaultWriter} 不写它、{@code EntityDataPatcher} 不读它、
 * {@code TestCommandSystem} 的所有断言也不知道它。
 * 于是"它在默认值 dump 里出现、写进 JSON 能生效"这件事<b>只可能</b>由反射带来。
 * <p>
 * <b>对比组</b>：同一个类的 {@link #probeState} 写了 {@code @NoConfig}
 * （模拟"这个字段不该给人配"），用来证明否定式注解真的挡得住。
 * <p>
 * 它不进 {@code mods/}、不改 {@code config/}、不是官方内容 —— 纯自测探针。
 *
 * @author AI（DeepSeek）生成
 */
public class ConfigReflectionProbeEntity extends LivingThing {

    /**
     * 注册表 id。故意用 {@code probe:} 前缀（不是任何模组的 id），
     * 这样即使它漏在注册表里也不会被当官方内容。
     */
    public static final String PROBE_ID = "probe:configReflectionProbe";

    /**
     * 探针字段的默认值（构造成员初始化器写进去的那个值）。
     * <p>
     * 断言要证明的是：<b>默认值就是构造当时的字段值</b> —— 不需要在别处为它登记"出厂值"。
     */
    public static final double DEFAULT_RESONANCE = 3.5;

    /**
     * <b>全新字段</b>：配置系统从没见过它，也没有任何手写清单知道它。
     * <p>
     * 故意做成 {@code double} + 普通 getter/setter：这是最常见的一种"可配置数值"形状
     * （{@code criticalRate} / {@code mass} 就是这一类）。
     */
    private double resonanceCoefficient = DEFAULT_RESONANCE;

    /**
     * 只给 {@code /data} 看、不给配置写的字段（模拟 {@code alive} 那种"能看不能配"）。
     * <p>
     * 它的存在唯一目的是证明 {@code @NoConfig} 挡住了配置面、却没动 {@code /data} 面。
     */
    @NoConfig("探针：故意挡住配置面，用来证明 /data 面不受影响")
    private int probeState = 7;

    /**
     * 默认构造器（{@link LivingThing} 有无参构造器）。
     */
    public ConfigReflectionProbeEntity() {
        super();
        this.setName("配置反射探针");
        this.setId(PROBE_ID);
        this.setElementSort(ElementSort.METAL);
    }

    /**
     * @return 这个对象上"能被配置文件写"的键（= {@code /data} 面 − {@code @NoConfig}）
     */
    public static java.util.List<String> configurableKeys() {
        java.util.List<String> keys = new java.util.ArrayList<>();
        for (DataBridge.DataAccessor accessor
                : cn.gfhnv.game.system.configLoadingSystem.ReflectionConfigBridge.candidates(probe())) {
            keys.add(accessor.name());
        }
        return keys;
    }

    /**
     * 造一个探针实例（每次都是新的，避免测试之间互相污染）。
     *
     * @return 探针
     */
    public static ConfigReflectionProbeEntity probe() {
        return new ConfigReflectionProbeEntity();
    }

    /**
     * 把探针"出厂状态"的默认值 dump 出来（读法与 {@code /data get} 同一份实现）。
     *
     * @return 数据名 → 标签
     */
    public static Map<String, NbtTag> defaultDump() {
        return cn.gfhnv.game.system.configLoadingSystem.ReflectionConfigBridge.defaults(probe());
    }

    /**
     * @return 探针的当前值
     */
    public double getResonanceCoefficient() {
        return resonanceCoefficient;
    }

    /* ------------------------------------------------------------------
     * 探针自己的小工具（让断言读起来像证据，而不是像在调反射）
     * ------------------------------------------------------------------ */

    /**
     * @param resonanceCoefficient 新值
     */
    public void setResonanceCoefficient(double resonanceCoefficient) {
        this.resonanceCoefficient = resonanceCoefficient;
    }

    /**
     * @return 探针状态（只读那一面）
     */
    public int getProbeState() {
        return probeState;
    }

    /**
     * @param probeState 新值
     */
    public void setProbeState(int probeState) {
        this.probeState = probeState;
    }
}
