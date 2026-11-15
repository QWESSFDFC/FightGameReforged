package cn.gfhnv.debug_tools;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.mod.Mod;
import cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver;
import cn.gfhnv.game.officialStuff.customEntity.players.ActorLiXiaoYan;
import cn.gfhnv.game.system.command.CommandManager;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.fight.TurnManager;
import cn.gfhnv.game.world.World;

import java.util.ArrayList;
import java.util.List;

import static cn.gfhnv.debug_tools.TestKit.*;
import static cn.gfhnv.debug_tools.TestProbes.ProbeSummonEntity;
import static cn.gfhnv.debug_tools.TestSupport.lastFighterId;

/**
 * 自测：**命令**。
 * <p>
 * 主题：/summon 命令。
 * <p>
 * 由 {@code TestCommandSystem#main} 按顺序调用 {@code SummonTests#runAll()}。
 */
public final class SummonTests {

    private SummonTests() {
    }

    /**
     * {@code /summon}：默认我方、可指定敌方、命名空间规则、入场初始化补齐。
     * <p>
     * 放在最后跑：它会把两个探测实体注册进 {@code World}（实体注册表），
     * 后面的用例再查"可用实体"列表就会被它们污染。
     */
    private static void testSummonCommand() {
        section("召唤：/summon");

        FlameReaver boss = new FlameReaver(150);
        ActorLiXiaoYan hero = new ActorLiXiaoYan(125);
        List<LivingThing> enemies = new ArrayList<>();
        enemies.add(boss);
        List<LivingThing> fighters = new ArrayList<>();
        fighters.add(hero);
        Fight fight = new Fight(enemies, new ArrayList<>(), fighters);

        LivingThing previous = CommandManager.getPlayer();
        CommandManager.setPlayer(hero);

        int fightersBefore = fight.getFighterList().size();
        int enemiesBefore = fight.getEnemiesList().size();
        int turnsBefore = TurnManager.getTurns().size();

        /* ① 不写阵营 = 我方 */
        run("summon CommonInsect", fight, true);
        check("summon：不写阵营时默认加进我方", fight.getFighterList().size() == fightersBefore + 1);
        check("summon：敌方列表没被顺手加人", fight.getEnemiesList().size() == enemiesBefore);
        LivingThing summoned = fight.getFighterList().get(fight.getFighterList().size() - 1);
        check("summon：召唤出来的副本被补成完整 id",
                "game_official_content:commonInsect".equals(lastFighterId(fight)));
        boolean isTemplateItself = false;
        for (LivingThing registered : World.getLivingEntityList()) {
            if (registered == summoned) {
                isTemplateItself = true;
            }
        }
        check("summon：召唤的是注册表模板的副本，不是模板本身", !isTemplateItself);
        check("summon：进了 allEntities", fight.getAllEntities().contains(summoned));
        check("summon：排进了时间轴（下个回合就能行动）", TurnManager.getTurns().size() == turnsBefore + 1);
        check("summon：接上了战斗上下文（participateFight）", summoned.getParticipateFight() == fight);

        /* ② 指定阵营：英文与中文都认 */
        run("summon commonInsect enemy", fight, true);
        check("summon：enemy 进敌方列表", fight.getEnemiesList().size() == enemiesBefore + 1);
        run("summon CommonInsect \"敌方\"", fight, true);
        check("summon：中文「敌方」也认", fight.getEnemiesList().size() == enemiesBefore + 2);
        run("summon CommonInsect \"我方\"", fight, true);
        check("summon：中文「我方」也认", fight.getFighterList().size() == fightersBefore + 2);
        run("summon CommonInsect ally", fight, true);
        check("summon：ally 也当我方", fight.getFighterList().size() == fightersBefore + 3);

        /* ③ 错误分支与名字的三种写法 */
        run("summon", fight, false);
        run("summon NoSuchEntity", fight, false);
        String badSide = errorOf("summon CommonInsect \"中间派\"", fight);
        check("summon：阵营填错时报错里给出可填的值", badSide.contains("our") && badSide.contains("enemy"));
        // 类名写法：BrokenContainer 忽略大小写正好等于短名 brokenContainer，于是命中残破容器
        run("summon BrokenContainer", fight, true);
        check("summon：类名写法可用（命中短名与它忽略大小写相同的那条）",
                "game_official_content:brokenContainer".equals(lastFighterId(fight)));
        run("summon brokenContainer", fight, true);
        check("summon：短名精确命中残破容器",
                "game_official_content:brokenContainer".equals(lastFighterId(fight)));
        run("summon completeContainer", fight, true);
        check("summon：同一类的另一条模板也能用短名召唤（不会被类名层挤掉）",
                "game_official_content:completeContainer".equals(lastFighterId(fight)));

        /* ④ 命名空间规则：短名/类名只解析官方内容，模组实体必须写完整 id */
        Mod summonProbeMod = new Mod("entityTestMod") {
        };
        ProbeSummonEntity sameNameAsOfficial = new ProbeSummonEntity();
        sameNameAsOfficial.setId("commonInsect");   // 与官方虫子同短名（不同类，所以不会抢它的 id）
        summonProbeMod.addEntity(sameNameAsOfficial);
        ProbeSummonEntity modOnly = new ProbeSummonEntity();
        modOnly.setId("modOnlySummon");             // 短名唯一，只能靠完整 id 拿到
        summonProbeMod.addEntity(modOnly);
        World.addMod(summonProbeMod);
        summonProbeMod.registerItself();

        int fightersBeforeProbe = fight.getFighterList().size();
        ProbeSummonEntity.fightStartCalls = 0;
        run("summon commonInsect", fight, true);
        check("summon：短名只解析官方内容（模组同名实体不参与，因此不算歧义）",
                "game_official_content:commonInsect".equals(lastFighterId(fight)));
        String modShortName = errorOf("summon modOnlySummon", fight);
        check("summon：模组实体写短名会被拒绝并提示该写的完整 id",
                modShortName.contains("entityTestMod:modOnlySummon"));
        run("summon entityTestMod:modOnlySummon", fight, true);
        check("summon：模组实体写完整 id 可以召唤",
                fight.getFighterList().size() == fightersBeforeProbe + 2);
        check("summon：入场时补调了 whenFightStart（中途加入的实体收不到框架那一次）",
                ProbeSummonEntity.fightStartCalls == 1);
        // 用类名找同类模板：两个探针的短名都跟类名不一样，于是按类名命中 2 个，
        // 报错里把两个可写的完整 id 都列出来（写哪一个都能召唤成功）
        String ambiguous = errorOf("summon ProbeSummonEntity", fight);
        check("summon：一个名字命中同类的多条模板时，报错里列出全部可写的完整 id",
                ambiguous.contains("entityTestMod:commonInsect")
                        && ambiguous.contains("entityTestMod:modOnlySummon"));

        /* ⑤ 不在战斗里要明确报错，而不是静默什么都不做 */
        CommandManager.clearCurrentFight();
        String noFight = errorOf("summon CommonInsect");
        check("summon：不在战斗里时明确报错", noFight.contains("不在战斗中"));

        CommandManager.setPlayer(previous);
    }

    /**
     * 跑本主题的全部用例（顺序 = 下面列的顺序）。
     */
    public static void runAll() throws Exception {
        testSummonCommand();
    }
}
