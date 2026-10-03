package com.gfhnv.mods.liXiaoYanPlus;

import cn.gfhnv.game.mod.Mod;
import cn.gfhnv.game.mod.ModInformation;
import cn.gfhnv.game.mod.config.ModConfig;
import cn.gfhnv.game.mod.config.ModConfigDocument;
import cn.gfhnv.game.mod.config.ModDataAware;
import cn.gfhnv.game.officialStuff.customEntity.players.ActorLiXiaoYan;

/**
 * 「李晓焰加强」模组的入口类 —— <b>一个"特殊机制模组"的最小范例</b>。
 * <p>
 * 它不新增任何角色、技能或物品，只做一件事：把官方角色【李晓焰】的【燃点】上限<b>抬高</b>。
 * 满血时 <i>10 → 10 + 加成</i>，低血时 <i>15 → 15 + 加成</i>
 * （低血那档在 {@code ActorLiXiaoYan#effectiveIgnitionMax()} 里现读规则表，本模组挂在同一条链的后面）。
 * <p>
 * <b>它走的是游戏留出来的扩展点，不是反射硬改</b>：
 * {@link cn.gfhnv.game.interfaces.IModifyIgnitionMax} 是"角色读【燃点】上限时经过的那条链"
 * （与伤害那边的 {@code IModifyDamage} 同形），
 * 在 {@link #invokeWhenLoaded()} 里登记一个修正器即可，游戏本体的数值一个字没动。
 * 链为空时上限就是出厂值，所以<b>删掉这个模组，手感与本模组发布前逐位相同</b>。
 * <p>
 * <b>加成可调</b>：本模组实现了 {@link ModDataAware}，读游戏自己的
 * {@code config/data/liXiaoYanPlus.json}（<b>模组不能自带配置文件</b>，见 MODDING-GUIDE 第 11 条）：
 * <pre>
 * { "version": 1, "liXiaoYanPlus": { "ignitionBonus": 8 } }
 * </pre>
 * 没有这个文件时用 {@value #DEFAULT_IGNITION_BONUS}。
 * <p>
 * <b>进游戏怎么确认生效</b>：选中李晓焰、放任意一个技能，控制台会打印
 * {@code 燃点层数:X/上限:Y}（{@code ActorLiXiaoYan} 注册的 {@code setShowSpecialMes}）——
 * 上限那一栏就是加了成之后的数。
 *
 * @author AI（DeepSeek）生成
 */
@ModConfig(id = LiXiaoYanPlusMod.MOD_ID)
public class LiXiaoYanPlusMod extends Mod implements ModDataAware {

    /**
     * 模组 ID：同时是内容 id 的前缀与配置文件名（{@code config/data/liXiaoYanPlus.json}）。
     */
    public static final String MOD_ID = "liXiaoYanPlus";

    /**
     * 玩家没写配置文件时用的【燃点】上限加成。
     */
    public static final int DEFAULT_IGNITION_BONUS = 5;

    /**
     * 当前生效的【燃点】上限加成。
     * <p>
     * 在 {@link #applyConfig(ModConfigDocument)} 里读一次；修正器每次读上限时现取它，
     * 所以这里存的是"玩家要的加成"，不是"某一刻的快照"。
     */
    private static int ignitionBonus = DEFAULT_IGNITION_BONUS;

    /**
     * 构造模组实例。加载器要求主类必须有这么一个<b>公开的、只接收 {@link ModInformation}</b> 的构造器。
     *
     * @param modInfo 由 {@code main.json} 解析出来的模组信息
     */
    public LiXiaoYanPlusMod(ModInformation modInfo) {
        super(MOD_ID, modInfo);
    }

    /**
     * 读玩家写在 {@code config/data/liXiaoYanPlus.json} 里的加成。
     * <p>
     * 游戏在 {@link #invokeWhenLoaded()} <b>之前</b>调用本方法；没有配置文件时也会调用（文档是空的），
     * 所以这里不需要写"有没有文件"的分支。
     *
     * @param cfg 本模组自己的那一段配置（路径两种写法等价：{@code ignitionBonus} / {@code liXiaoYanPlus/ignitionBonus}）
     */
    @Override
    public void applyConfig(ModConfigDocument cfg) {
        ignitionBonus = cfg.getInt("ignitionBonus", DEFAULT_IGNITION_BONUS);
    }

    /**
     * 声明本模组的默认配置 —— 游戏会在<b>配置文件不存在</b>时按它生成
     * {@code config/data/liXiaoYanPlus.json}，玩家第一次运行就有一份可改的默认值。
     * <p>
     * ⚠️ 只在文件<b>不存在</b>时生成；已存在的文件一个字节都不动，所以玩家改过的值不会被覆盖。
     * 想恢复出厂值：删掉那个文件、重启游戏即可。
     * <p>
     * 返回 {@link java.util.Map} 而不是 {@code org.json.JSONObject}，是为了让模组不依赖 {@code org.json}。
     *
     * @return 本模组自己那段配置的默认值
     */
    @Override
    public java.util.Map<String, Object> defaultConfig() {
        java.util.Map<String, Object> own = new java.util.LinkedHashMap<>();
        own.put("ignitionBonus", DEFAULT_IGNITION_BONUS);
        return own;
    }

    /**
     * 登记【燃点】上限修正器。
     * <p>
     * 修正器是<b>纯函数</b>：只按"上一步的上限 + 加成"返回值，不去改角色的任何状态
     * （读一次上限就会被调一次，写状态会变成"读一次改一次"）。
     * 下界夹到 0 是为了防"玩家把加成写成很负的数"把上限弄成负数。
     */
    @Override
    public void invokeWhenLoaded() {
        ActorLiXiaoYan.addIgnitionMaxModifier(
                (baseMax, owner) -> Math.max(0, baseMax + ignitionBonus));

        System.out.println("[" + getModInformation().getName() + "] 加载完成："
                + "李晓焰的【燃点】上限 +" + ignitionBonus
                + "（满血 " + ActorLiXiaoYan.Rule.ignitionMax() + " → "
                + (ActorLiXiaoYan.Rule.ignitionMax() + ignitionBonus) + "，低血 "
                + ActorLiXiaoYan.Rule.lowHpIgnitionMax() + " → "
                + (ActorLiXiaoYan.Rule.lowHpIgnitionMax() + ignitionBonus) + "）");
        System.out.println("[" + getModInformation().getName() + "] 想改加成：改 config/data/"
                + MOD_ID + ".json 里的 ignitionBonus 再重启游戏"
                + "（这个文件首次运行会自动生成；删掉它会按默认值 " + DEFAULT_IGNITION_BONUS + " 再生成）。");
    }
}
