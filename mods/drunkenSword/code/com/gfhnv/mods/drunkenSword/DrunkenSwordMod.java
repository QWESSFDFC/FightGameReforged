package com.gfhnv.mods.drunkenSword;

import cn.gfhnv.game.mod.Mod;
import cn.gfhnv.game.mod.ModInformation;

/**
 * 「醉剑仙」模组的入口类。
 * <p>
 * 模组内容一共三类，正好把模组系统支持的注册口都走一遍：
 * <ul>
 *     <li><b>实体</b>：{@link DrunkenSwordsman}（酒剑仙，可当队友也可当敌人）；</li>
 *     <li><b>效果</b>：{@link Drunkenness}（醉意，层数资源）、{@link Hangover}（宿醉，负面）；</li>
 *     <li><b>物品</b>：{@link OsmanthusWine}（桂花酿）、{@link SoberSoup}（醒酒汤）。</li>
 * </ul>
 * <p>
 * <b>为什么内容要放在 {@link #invokeWhenLoaded()}</b>：
 * 框架在 {@code GameStartEvent} 时会调用它、紧接着调用 {@link #registerItself()}，
 * 那时游戏本体（{@code OfficialGameContent}）已经注册完毕，模组内容再进注册表就不会互相覆盖。
 * 官方内容的 {@code OfficialGameContent} 是在构造器里直接注册的，
 * 它的注释自己都写着"不要学这个"。
 * <p>
 * 内容 id 会由 {@link Mod#addEntity}/{@link Mod#addItem}/{@link Mod#addEffect} 自动带上
 * {@value #MOD_ID} 前缀，所以运行时完整 id 形如 {@code drunkenSword:drunkenSwordsman}。
 *
 * @author AI（DeepSeek）生成
 */
public class DrunkenSwordMod extends Mod {

    /**
     * 模组 ID：会作为模组内容的 id 前缀（{@code drunkenSword:xxx}）。
     */
    public static final String MOD_ID = "drunkenSword";

    /**
     * 角色的模板等级。官方角色（玩家一 / 白厄 / 李晓焰）用的都是 125，这里保持一致。
     */
    public static final long CHARACTER_LEVEL = 125;

    /**
     * 构造模组实例。加载器要求主类必须有这么一个**公开的、只接收 {@link ModInformation}** 的构造器。
     *
     * @param modInfo 由 {@code main.json} 解析出来的模组信息
     */
    public DrunkenSwordMod(ModInformation modInfo) {
        super(MOD_ID, modInfo);
    }

    /**
     * 向模组的三个 List 里填内容。不需要在这里调 {@link #registerItself()}：
     * 框架会在本方法之后立刻替我们调一次。
     */
    @Override
    public void invokeWhenLoaded() {
        addEntity(new DrunkenSwordsman(CHARACTER_LEVEL));
        addEffect(new Drunkenness());
        addEffect(new Hangover());
        addItem(new OsmanthusWine());
        addItem(new SoberSoup());

        System.out.println("[" + getModInformation().getName() + "] 加载完成："
                + "角色 1 名（酒剑仙）、效果 2 个（醉意 / 宿醉）、物品 2 件（桂花酿 / 醒酒汤）");
        System.out.println("[" + getModInformation().getName() + "] 玩法：普攻攒【醉意】（每层还带 "
                + (int) (Drunkenness.REDUCTION_PER_STACK * 100) + "% 减伤），攒到 "
                + FrostSword.REQUIRED_STACKS + " 层就能放大招；"
                + "「桂花酿」快速垫层，「醒酒汤」把层数换成治疗。");
    }
}
