package cn.gfhnv.debug_tools;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEntity.monsters.CommonInsect;
import cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver;
import cn.gfhnv.game.officialStuff.customEntity.players.ActorLiXiaoYan;
import cn.gfhnv.game.officialStuff.customEntity.players.PlayerOne;
import cn.gfhnv.game.officialStuff.customEntity.summons.BrokenContainer;
import cn.gfhnv.game.system.command.CommandManager;
import cn.gfhnv.game.world.World;

import static cn.gfhnv.debug_tools.TestKit.check;
import static cn.gfhnv.debug_tools.TestKit.section;
import static cn.gfhnv.debug_tools.TestSupport.testIgnitionMaxModifierChain;

/**
 * 自测：**官方角色**。
 * <p>
 * 主题：官方角色的专属机制（李晓焰的燃点、目标策略的跟随）。
 * <p>
 * 由 {@code TestCommandSystem#main} 按顺序调用 {@code CharacterTests#runAll()}。
 */
public final class CharacterTests {

    private CharacterTests() {
    }

    /**
     * 李晓焰的【燃点】修复 + {@code World#fullIdOf} 的"同类多模板"问题（2026-09）。
     * <p>
     * 三处症状里，这里能断言的是两处（燃点下限、同类多模板 id）；
     * "大招在 7 层时倒扣额外伤害"与"重复开大泄漏监听器"属于技能内部行为，
     * 靠"加算/减算都用 wasHigh 快照"和"监听器绑定自己的效果实例"这两个写法保证，自测覆盖不到。
     */
    private static void testActorLiXiaoYan() {
        section("李晓焰：燃点下限与同类多模板 id 归一");

        ActorLiXiaoYan li = new ActorLiXiaoYan(125);

        // 免死会一次扣掉 MEMORIZE_COST 层：扣完必须夹在 0
        // （旧代码只夹上限，副本挨打时会把自己算成"模板值 − 10"，实测出现过 −7）
        li.setIgnition(li.getMemorizeTrigger());
        li.setIgnition(li.getIgnition() - li.getMemorizeCost());
        check("燃点扣到 0 而不是负数", li.getIgnition() == 0);
        li.setIgnition(-5);
        check("燃点被显式设成负数时夹到 0", li.getIgnition() == 0);

        // 上限随血量变化：满血 10，半血以下 15（getIgnitionMax 与 setIgnition 必须用同一判据）
        li.setHp(li.getHpMax());
        check("满血时燃点上限 = IGNITION_MAX", li.getIgnitionMax() == ActorLiXiaoYan.Defaults.IGNITION_MAX);
        li.setHp(li.getHpMax() / 4);
        check("低血时燃点上限 = LOW_HP_IGNITION_MAX", li.getIgnitionMax() == ActorLiXiaoYan.Defaults.LOW_HP_IGNITION_MAX);
        li.setIgnition(100);
        check("低血时燃点能一路设到上限", li.getIgnition() == ActorLiXiaoYan.Defaults.LOW_HP_IGNITION_MAX);

        testIgnitionMaxModifierChain();

        // 同一个类注册两条模板（【残破容器】/【完整容器】）时，
        // 运行时实例必须按"短名相同"补全 id，不能拿同类第一条硬套
        FlameReaver owner = new FlameReaver(150);
        BrokenContainer broken = new BrokenContainer(owner);
        BrokenContainer complete = new BrokenContainer(owner, BrokenContainer.Kind.COMPLETE);
        check("残破容器 id 归一到 brokenContainer",
                World.fullIdOf(broken).endsWith(":brokenContainer"));
        check("完整容器 id 归一到 completeContainer（不被同类第一条盖掉）",
                World.fullIdOf(complete).endsWith(":completeContainer"));
    }

    /**
     * 命令系统的 {@code @s} / {@code @p} 要跟着"当前行动者"走（2026-09 实测问题）。
     * <p>
     * 背景：{@link CommandManager#setPlayer} 只在 {@code GameMain} 的选人流程里调用，
     * 多角色队伍里<b>最后选的那个会一直占着"玩家"的位置</b> —— 日志里表现为
     * "酒剑仙的回合里敲 {@code /give @s …}，东西发给了白厄/卡厄斯兰那"。
     */
    private static void testFollowActor() {
        section("命令系统的 @s 跟随当前行动者");

        LivingThing previous = CommandManager.getPlayer();
        LivingThing ourFighter = new PlayerOne(125).copy();
        LivingThing enemy = new CommonInsect(150L).copy();

        CommandManager.setPlayer(ourFighter);
        CommandManager.followActor(enemy, true);
        check("我方行动者接手后，@s 指向它", CommandManager.getPlayer() == enemy);
        CommandManager.followActor(ourFighter, false);
        check("敌方回合不改 @s（否则 /hurt @s 会打到对面）", CommandManager.getPlayer() == enemy);
        CommandManager.followActor(null, true);
        check("行动者为 null 时不动 @s", CommandManager.getPlayer() == enemy);

        CommandManager.setPlayer(previous);
    }

    /**
     * 跑本主题的全部用例（顺序 = 下面列的顺序）。
     */
    public static void runAll() throws Exception {
        testActorLiXiaoYan();
        testFollowActor();
    }
}
