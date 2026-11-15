package cn.gfhnv.game.event;

/**
 * <b>游戏初始化完成</b>：内容注册表、配置、命令系统都已就绪，但还没有开始选人。
 * <p>
 * <b>给扩展用</b>：模组想做"一次性注册/补丁"（往 World 里塞东西、改出厂值、预热缓存）时监听它，
 * 比在静态块里做安全得多 —— 那时 {@code OfficialGameContent} 已经加载完，不会互相踩。
 * <p>
 * 发布点：{@code GameMain#gameInitialize()} 的最后一行。
 *
 * @author AI（DeepSeek）生成
 */
public class GameInitializedEvent extends Event {
}
