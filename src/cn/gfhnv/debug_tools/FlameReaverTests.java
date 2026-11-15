package cn.gfhnv.debug_tools;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.DamageEvent;
import cn.gfhnv.game.officialStuff.customEntity.monsters.CommonInsect;
import cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver;
import cn.gfhnv.game.officialStuff.customEntity.players.PlayerOne;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;

import java.util.ArrayList;
import java.util.List;

import static cn.gfhnv.debug_tools.TestKit.*;
import static cn.gfhnv.debug_tools.TestProbes.ProbeSkill;
import static cn.gfhnv.debug_tools.TestSupport.describeNames;

/**
 * 自测：**官方怪物**。
 * <p>
 * 主题：盗火行者的阵营判定与二阶段切换。
 * <p>
 * 由 {@code TestCommandSystem#main} 按顺序调用 {@code FlameReaverTests#runAll()}。
 */
public final class FlameReaverTests {

    private FlameReaverTests() {
    }

    /**
     * 盗火行者的阵营判定自测。
     * <p>
     * 钉住一个实测踩到的 bug：召唤物【残破容器】与 BOSS 同属敌方阵营，
     * 所以任何"打对面"的代码都必须按 <b>user 所在阵营的对面</b> 取目标。
     * 曾经写成 {@code Fight#getOpponentList(user)}（它在 user 不在敌方列表时返回 enemiesList），
     * 导致目标里混进 BOSS 自己的召唤物 —— 日志表现为"BOSS 打自己的容器"、
     * "BOSS 被自己的【侵蚀】烧"。正确写法见
     * {@code FlameReaverSkill#fightingSideOf(Fight, LivingThing)}。
     *
     * @author AI（DeepSeek）生成
     */
    private static void testFlameReaverFactions() {
        System.out.println();
        System.out.println("-------- 盗火行者：阵营与目标 --------");
        try {
            FlameReaver boss =
                    new FlameReaver(150);
            LivingThing hero = new PlayerOne(125).copy();
            hero.setName("测试玩家");
            List<LivingThing> enemies = new ArrayList<>();
            enemies.add(boss);
            List<LivingThing> fighters = new ArrayList<>();
            fighters.add(hero);
            Fight fight = new Fight(enemies, new ArrayList<>(), fighters);

            cn.gfhnv.game.officialStuff.customEntity.summons.BrokenContainer container =
                    boss.summonContainer(fight);
            check("召唤容器成功（测试前提）", container != null);
            check("容器进了敌方阵营",
                    container != null && fight.getEnemiesList().contains(container));
            check("容器不在我方阵营",
                    container != null && !fight.getFighterList().contains(container));

            // 先把几个列表的真实内容打出来：这两个框架方法的命名有歧义，
            // 光看名字判断会来回改错（已经错过两次），必须靠实测输出定死。
            System.out.println("  [诊断] enemiesList = " + describeNames(fight.getEnemiesList()));
            System.out.println("  [诊断] fighterList = " + describeNames(fight.getFighterList()));
            System.out.println("  [诊断] getOpponentList(BOSS) = " + describeNames(fight.getOpponentList(boss)));
            System.out.println("  [诊断] getOwnList(BOSS)      = " + describeNames(fight.getOwnList(boss)));
            System.out.println("  [诊断] getOpponentList(容器) = " + describeNames(fight.getOpponentList(container)));
            System.out.println("  [诊断] getOwnList(容器)      = " + describeNames(fight.getOwnList(container)));

            // ① 语义自证（实测输出见上面的 [诊断]，别再靠方法名猜）：
            //    getOpponentList(entity) = entity 对面的实体列表
            //    getOwnList(entity)      = entity 自己一侧的实体列表（BOSS 用 = 含它自己的召唤物）
            List<LivingThing> oppositeOfBoss = fight.getOpponentList(boss);
            List<LivingThing> sameSideAsBoss = fight.getOwnList(boss);
            check("getOpponentList(BOSS) = 对面（只有玩家，不含自己人）",
                    oppositeOfBoss.size() == 1 && oppositeOfBoss.contains(hero));
            check("getOwnList(BOSS) = 自己一侧（含 BOSS 自己与它的容器）",
                    sameSideAsBoss.contains(boss)
                            && container != null && sameSideAsBoss.contains(container));
            check("自己一侧里没有玩家",
                    !sameSideAsBoss.contains(hero));

            // ② BOSS 要打的就是对面 —— 直接就是玩家队伍，不会混进召唤物
            check("BOSS 用 getOpponentList 取目标不会打到自己人",
                    !oppositeOfBoss.contains(container));

            // ③ 容器取目标同样是"它的对面"
            List<LivingThing> containerTargets = fight.getOpponentList(container);
            check("容器取目标 = 玩家队伍（不误伤 BOSS）",
                    containerTargets.size() == 1 && containerTargets.contains(hero)
                            && !containerTargets.contains(boss));

            // ④ 遍历"自己的召唤物"必须用 getOwnList + 类型过滤（就像 FlameReaver#getAliveContainers）
            List<LivingThing> ownSideContainers = new ArrayList<>();
            for (LivingThing each : sameSideAsBoss) {
                if (each instanceof cn.gfhnv.game.officialStuff.customEntity.summons.BrokenContainer) {
                    ownSideContainers.add(each);
                }
            }
            check("用 getOwnList + 类型过滤能筛出自己的容器",
                    ownSideContainers.size() == 1 && ownSideContainers.contains(container));

            // ⑤ 完整容器的奖励：额外回合 + 增伤 buff（官方末日幻影 3.4 的机制）
            //    额外回合的实现是"在【当前时间点】给受益者插一个 needTime=0 的回合条目"，
            //    所以断言就查这条目：必须是他的、而且立刻可执行（时间点 = 现在）。
            int entriesBefore = cn.gfhnv.game.system.fight.TurnManager.getTurns().size();
            // presentTime 在自测里是 null（没跑 TurnManager.init），实现会兜底成 ZERO，
            // 断言这边用同样的归一化，否则 compareTo(null) 会 NPE
            java.math.BigDecimal now = cn.gfhnv.game.system.fight.TurnManager.getPresentTime();
            if (now == null) {
                now = java.math.BigDecimal.ZERO;
            }
            boss.grantExtraTurn(hero);
            List<cn.gfhnv.game.system.fight.TurnEntry> turns =
                    cn.gfhnv.game.system.fight.TurnManager.getTurns();
            check("额外回合：时间轴上多了一个回合条目",
                    turns.size() == entriesBefore + 1);

            cn.gfhnv.game.system.fight.TurnEntry granted = null;
            for (cn.gfhnv.game.system.fight.TurnEntry entry : turns) {
                if (entry.getLivingThing() == hero
                        && entry.getNeedTime().compareTo(java.math.BigDecimal.ZERO) == 0
                        && entry.getStartTime().compareTo(now) == 0) {
                    granted = entry;
                }
            }
            check("额外回合：那一条属于受益者，且 needTime=0 / startTime=now（立刻可执行）",
                    granted != null);
            check("额外回合：排完序后它在队首（所以下一圈就会被取出来行动）",
                    !turns.isEmpty() && turns.getFirst() == granted);
            // 必须是 isExtra：额外回合不推进身上效果的剩余回合（EffectEventListener 按这个标记走）
            check("额外回合：标记了 isExtra（与白厄的额外回合同一个约定）",
                    granted != null && granted.isExtra());

            // ⑥ 2026-10-03 用户要求"这个奖励的额外回合应该直接行动（排在前面）"：
            //    光排在 presentTime 上不够 —— 时间打平时 sort() 原先按速度排，
            //    "立即行动"会被场上更快的单位抢走。所以给回合加了优先级。
            check("额外回合：带了 PRIORITY_EXTRA（时间打平时压过普通回合先手）",
                    granted != null
                            && granted.getPriority() == cn.gfhnv.game.system.fight.TurnEntry.PRIORITY_EXTRA);

            List<cn.gfhnv.game.system.fight.TurnEntry> timelineBefore =
                    new ArrayList<>(cn.gfhnv.game.system.fight.TurnManager.getTurns());
            LivingThing fastNormal = new CommonInsect(100L).copy();
            fastNormal.setName("快的普通回合");
            fastNormal.setSpeed(999);
            LivingThing slowPriority = new CommonInsect(100L).copy();
            slowPriority.setName("慢的奖励回合");
            slowPriority.setSpeed(1);
            cn.gfhnv.game.system.fight.TurnEntry normalEntry = new cn.gfhnv.game.system.fight.TurnEntry(
                    fastNormal, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO);
            cn.gfhnv.game.system.fight.TurnEntry priorityEntry = new cn.gfhnv.game.system.fight.TurnEntry(
                    slowPriority, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO)
                    .setPriority(cn.gfhnv.game.system.fight.TurnEntry.PRIORITY_EXTRA);
            cn.gfhnv.game.system.fight.TurnManager.getTurns().add(normalEntry);
            cn.gfhnv.game.system.fight.TurnManager.getTurns().add(priorityEntry);
            cn.gfhnv.game.system.fight.TurnManager.sort();
            // 只比这两条的相对顺序：时间轴上还有别的条目，不能拿 getFirst() 断言
            check("排序：时间相同时优先级大的先执行（优先级压过速度：速度 1 的奖励回合排到速度 999 前面）",
                    cn.gfhnv.game.system.fight.TurnManager.getTurns().indexOf(priorityEntry)
                            < cn.gfhnv.game.system.fight.TurnManager.getTurns().indexOf(normalEntry));

            // 优先级也打平时，快的先动 —— 老口径（TurnManager.init 全体同起点）不能被改坏
            priorityEntry.setPriority(cn.gfhnv.game.system.fight.TurnEntry.PRIORITY_NORMAL);
            cn.gfhnv.game.system.fight.TurnManager.sort();
            check("排序：优先级也相同时，速度快的先动（老口径保留）",
                    cn.gfhnv.game.system.fight.TurnManager.getTurns().indexOf(normalEntry)
                            < cn.gfhnv.game.system.fight.TurnManager.getTurns().indexOf(priorityEntry));

            // 优先级压不过时间：晚到的奖励回合不能越过时间更早的普通回合
            normalEntry.setNeedTime(java.math.BigDecimal.ONE);      // time = 1
            priorityEntry.setNeedTime(java.math.BigDecimal.TEN);    // time = 10
            priorityEntry.setPriority(cn.gfhnv.game.system.fight.TurnEntry.PRIORITY_EXTRA);
            cn.gfhnv.game.system.fight.TurnManager.sort();
            check("排序：优先级压不过时间（时间更晚的奖励回合仍然排在后面）",
                    cn.gfhnv.game.system.fight.TurnManager.getTurns().indexOf(normalEntry)
                            < cn.gfhnv.game.system.fight.TurnManager.getTurns().indexOf(priorityEntry));

            // 收尾：把探测条目摘掉，时间轴还原成进来时的样子
            cn.gfhnv.game.system.fight.TurnManager.getTurns().clear();
            cn.gfhnv.game.system.fight.TurnManager.getTurns().addAll(timelineBefore);
            cn.gfhnv.game.system.fight.TurnManager.sort();

            // ⑥ 增伤 buff：走 setEnhance（伤害公式的 (1+enhance)），并且只加一次
            //    先用一个干净的容器来量"一次奖励加了多少"
            LivingThing rewardProbe = new PlayerOne(125).copy();
            rewardProbe.setName("奖励探针");
            double enhanceBefore = rewardProbe.getEnhance();
            boss.grantContainerReward(rewardProbe);
            check("完整容器奖励：增伤按「破容器之赏」的比例加上去了（+0.4）",
                    Math.abs(rewardProbe.getEnhance() - (enhanceBefore + 0.4)) < 1e-9);
            double afterFirst = rewardProbe.getEnhance();
            // 再调一次"获得时"的钩子，验证 isOn 标记挡住了重复叠加。
            // ⚠️ 按【类 + 有没有起名】找，别按 id 找：效果进 addEffect 之后 id 会被框架
            //    按类补成 `game_official_content:damageEnhanceEffect`，
            //    真正区分「破容器之赏」和普通增伤的是**显示名**。
            cn.gfhnv.game.effect.Effect rewardEffect = null;
            for (cn.gfhnv.game.effect.Effect each : rewardProbe.getEntityEffectList()) {
                if (each instanceof cn.gfhnv.game.officialStuff.customEffect.universalEffects.DamageEnhanceEffect
                        && each.hasCustomName()) {
                    rewardEffect = each;
                    each.comeIntoEffect(rewardProbe);
                }
            }
            check("完整容器奖励：效果确实是 `DamageEnhanceEffect`，而且带上了名字「破容器之赏」",
                    rewardEffect instanceof cn.gfhnv.game.officialStuff.customEffect.universalEffects.DamageEnhanceEffect
                            && "破容器之赏".equals(rewardEffect.getName())
                            && rewardEffect.hasCustomName());
            check("完整容器奖励：重复触发不会叠加（isOn 标记）",
                    Math.abs(rewardProbe.getEnhance() - afterFirst) < 1e-9);

            // ⑦ 【沉默的悲叹】的"下次行动延后 100%"：两种路径都真的生效
            //    路径 A：时间轴上没有自己的回合（= 正在自己的回合里）→ 自己排一个双倍间隔的，
            //           并把本回合信号设成 SKIP_WITHOUT_NEW_TURN，免得循环再排一条正常间隔的
            cn.gfhnv.game.system.fight.TurnManager.getTurns()
                    .removeIf(t -> t != null && t.getLivingThing() == boss);
            java.math.BigDecimal normalNeed = java.math.BigDecimal.valueOf(10000)
                    .divide(java.math.BigDecimal.valueOf(boss.getSpeed()), 10, java.math.RoundingMode.HALF_UP);
            cn.gfhnv.game.system.fight.TurnEntry fakePresentTurn =
                    new cn.gfhnv.game.system.fight.TurnEntry(boss, normalNeed, java.math.BigDecimal.ZERO);
            cn.gfhnv.game.eventListener.FightTurnPastListener.setPresentTurn(fakePresentTurn);
            boss.delayNextOwnTurn(java.math.BigDecimal.ONE);
            cn.gfhnv.game.system.fight.TurnEntry delayed =
                    cn.gfhnv.game.system.fight.TurnManager.getNextTurnOf(boss);
            check("延后：时间轴上没有自己的回合时，自己排一条（间隔 ×2）",
                    delayed != null && delayed.getNeedTime()
                            .compareTo(normalNeed.multiply(java.math.BigDecimal.TWO)) == 0);
            check("延后：本回合信号被设成 SKIP_WITHOUT_NEW_TURN（否则循环还会再排一条）",
                    fakePresentTurn.getActionSignal() == cn.gfhnv.game.system.fight.ActionSignal.SKIP_WITHOUT_NEW_TURN);

            //    路径 B：时间轴上已经有自己的回合 → 直接把那一条的间隔再乘 2
            java.math.BigDecimal beforeDelay = delayed == null ? java.math.BigDecimal.ZERO : delayed.getNeedTime();
            boss.delayNextOwnTurn(java.math.BigDecimal.ONE);
            check("延后：已有待执行回合时，把那一条的间隔再乘 2",
                    delayed != null && delayed.getNeedTime()
                            .compareTo(beforeDelay.multiply(java.math.BigDecimal.TWO)) == 0);

            // 收尾：把这两条探测用的时间轴条目与自己塞的"当前回合"都还原掉
            cn.gfhnv.game.system.fight.TurnManager.getTurns()
                    .removeIf(t -> t != null && t.getLivingThing() == boss);
            cn.gfhnv.game.eventListener.FightTurnPastListener.setPresentTurn(null);

            // ⑧ 时间轴条目查询（getNextTurnOf）的三条约定：跳过额外回合、判空、不崩
            //    背景：旧实现返回「该实体排序后最早的一条」，而额外回合的 needTime=0，
            //    天然最早 —— 于是「灾厄-弑魂焚诏」让敌方全体立即行动时，会把对方手里的
            //    奖励回合（击杀完整容器 / 变身连击）当成「下次行动」顶掉。
            cn.gfhnv.game.system.fight.TurnManager.getTurns()
                    .removeIf(t -> t != null && t.getLivingThing() == hero);
            check("getNextTurnOf：不在时间轴上的生物返回 null（调用方必须自己判空）",
                    cn.gfhnv.game.system.fight.TurnManager.getNextTurnOf(hero) == null);
            check("getNextTurnOf：参数为 null 时也返回 null，不再抛 NPE",
                    cn.gfhnv.game.system.fight.TurnManager.getNextTurnOf(null) == null);

            java.math.BigDecimal heroNeed = java.math.BigDecimal.valueOf(10000)
                    .divide(java.math.BigDecimal.valueOf(hero.getSpeed()), 10, java.math.RoundingMode.HALF_UP);
            cn.gfhnv.game.system.fight.TurnEntry heroNormal =
                    new cn.gfhnv.game.system.fight.TurnEntry(hero, heroNeed, java.math.BigDecimal.ZERO);
            cn.gfhnv.game.system.fight.TurnEntry heroExtra =
                    new cn.gfhnv.game.system.fight.TurnEntry(hero, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO)
                            .setExtra(true);
            cn.gfhnv.game.system.fight.TurnManager.getTurns().add(heroNormal);
            cn.gfhnv.game.system.fight.TurnManager.getTurns().add(heroExtra);
            cn.gfhnv.game.system.fight.TurnManager.sort();
            check("额外回合的 needTime=0 确实排在同名生物的正常回合前面（这就是它会被顶掉的原因）",
                    cn.gfhnv.game.system.fight.TurnManager.getTurns().indexOf(heroExtra)
                            < cn.gfhnv.game.system.fight.TurnManager.getTurns().indexOf(heroNormal));
            check("getNextTurnOf：跳过额外回合条目，返回的是正常那条",
                    cn.gfhnv.game.system.fight.TurnManager.getNextTurnOf(hero) == heroNormal);

            // ⑨ 灾厄-弑魂焚诏「使敌方全体立即行动」：目标没有条目时现排一条（旧写法直接 NPE）
            cn.gfhnv.game.system.fight.TurnManager.getTurns()
                    .removeIf(t -> t != null && t.getLivingThing() == hero);
            cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.CalamitySoulscorchEdict edict =
                    new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.CalamitySoulscorchEdict();
            boolean edictSurvived = true;
            try {
                edict.comeToEffect(fight, boss, new ArrayList<>(java.util.List.of(hero)));
            } catch (RuntimeException e) {
                edictSurvived = false;
                System.out.println("  [诊断] 灾厄-弑魂焚诏 抛了 " + e);
            }
            check("灾厄-弑魂焚诏：目标不在时间轴上时现排一条，不再解引用 null（旧写法必 NPE）",
                    edictSurvived);
            cn.gfhnv.game.system.fight.TurnEntry scheduled =
                    cn.gfhnv.game.system.fight.TurnManager.getNextTurnOf(hero);
            check("灾厄-弑魂焚诏：现排的那条 needTime=0（才真的做得到「立即行动」）",
                    scheduled != null && scheduled.getNeedTime().compareTo(java.math.BigDecimal.ZERO) == 0);

            //    手里有额外回合时：改的必须是正常回合，额外回合条目一个字段都不能动
            cn.gfhnv.game.system.fight.TurnManager.getTurns()
                    .removeIf(t -> t != null && t.getLivingThing() == hero);
            cn.gfhnv.game.system.fight.TurnEntry keepNormal =
                    new cn.gfhnv.game.system.fight.TurnEntry(hero,
                            heroNeed.multiply(java.math.BigDecimal.TWO), java.math.BigDecimal.ZERO);
            cn.gfhnv.game.system.fight.TurnEntry keepExtra =
                    new cn.gfhnv.game.system.fight.TurnEntry(hero, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO)
                            .setExtra(true);
            cn.gfhnv.game.system.fight.TurnManager.getTurns().add(keepNormal);
            cn.gfhnv.game.system.fight.TurnManager.getTurns().add(keepExtra);
            edict.comeToEffect(fight, boss, new ArrayList<>(java.util.List.of(hero)));
            check("灾厄-弑魂焚诏：被拉到 now 的是正常回合，额外回合条目原封不动",
                    keepNormal.getNeedTime().compareTo(java.math.BigDecimal.ZERO) == 0
                            && keepExtra.getNeedTime().compareTo(java.math.BigDecimal.ZERO) == 0
                            && keepExtra.getStartTime().compareTo(java.math.BigDecimal.ZERO) == 0
                            && cn.gfhnv.game.system.fight.TurnManager.getNextTurnOf(hero) == keepNormal);

            // ⑩ 反击（灾厄-弑魂焚诏的反击）不再打尸体、不再在空表上炸
            LivingThing counterProbe = new PlayerOne(125).copy();
            counterProbe.setName("反击探针");
            counterProbe.setHp(100);
            long probeHpBefore = counterProbe.getHp();
            LivingThing corpseProbe = new PlayerOne(125).copy();
            corpseProbe.setName("尸体探针");
            corpseProbe.setHp(0);
            check("尸体探针确实被判定为已倒下（测试前提）", !corpseProbe.isAlive());

            boolean counterSurvivedEmpty = true;
            try {
                new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.Counterattack()
                        .comeToEffect(fight, counterProbe, new ArrayList<>());
            } catch (RuntimeException e) {
                counterSurvivedEmpty = false;
                System.out.println("  [诊断] 反击空表抛了 " + e);
            }
            check("反击：敌方列表为空时不抛 NoSuchElementException（旧的 enemies.getFirst() 必炸）",
                    counterSurvivedEmpty);

            java.util.List<LivingThing> counterTargets = new ArrayList<>();
            counterTargets.add(corpseProbe);
            // 另外两只也全设成 0 血：这里刻意不放活人 —— hero / boss 是这场战斗的实体，
            // 让它们挨一顿反击会污染后面用例的断言
            for (int i = 0; i < 2; i++) {
                LivingThing anotherCorpse = new PlayerOne(125).copy();
                anotherCorpse.setName("尸体探针" + (i + 2));
                anotherCorpse.setHp(0);
                counterTargets.add(anotherCorpse);
            }
            java.util.List<LivingThing> counterTargetsSnapshot = new ArrayList<>(counterTargets);
            new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.Counterattack()
                    .comeToEffect(fight, counterProbe, counterTargets);
            check("反击：不再就地 shuffle 调用方传进来的列表（现在只打自己抽的副本）",
                    counterTargets.equals(counterTargetsSnapshot));
            check("反击：目标全倒下时一个都不打（自身回血照旧生效，说明没在过滤处提前 return）",
                    corpseProbe.getHp() == 0 && counterProbe.getHp() > probeHpBefore);

            // 收尾：清掉这几个探测用的时间轴条目，别影响后面的用例
            cn.gfhnv.game.system.fight.TurnManager.getTurns()
                    .removeIf(t -> t != null && t.getLivingThing() == hero);

            // ⑧ 【侵蚀】按目标合并：不同来源重复施加只刷新同一条（否则一回合会连跳好几次）
            LivingThing erosionProbe = new PlayerOne(125).copy();
            erosionProbe.setName("侵蚀探针");
            cn.gfhnv.game.officialStuff.customEffect.flameReaverEffects.Erosion firstErosion =
                    cn.gfhnv.game.officialStuff.customEffect.flameReaverEffects.Erosion
                            .applyTo(erosionProbe, 0.05, 3, "来源甲");
            cn.gfhnv.game.officialStuff.customEffect.flameReaverEffects.Erosion secondErosion =
                    cn.gfhnv.game.officialStuff.customEffect.flameReaverEffects.Erosion
                            .applyTo(erosionProbe, 0.03, 2, "来源乙");
            int erosionCount = 0;
            for (cn.gfhnv.game.effect.Effect each : erosionProbe.getEntityEffectList()) {
                if (each instanceof cn.gfhnv.game.officialStuff.customEffect.flameReaverEffects.Erosion) {
                    erosionCount++;
                }
            }
            check("侵蚀：不同来源重复施加只留一条（刷新，而不是并列好几条）",
                    firstErosion != null && firstErosion == secondErosion && erosionCount == 1);
            check("侵蚀：刷新时强度取较大者、持续回合取较长者",
                    secondErosion != null && Math.abs(secondErosion.getRate() - 0.05) < 1e-9
                            && secondErosion.getLastTime() == 3);

            // ⑨ 共祭那一轮：BOSS 自己也参与攻击，而且所有容器打<b>同一批目标</b>
            //    （以前容器各自随机挑目标、BOSS 完全不攻击 —— 用户 2026-09 实测指出）
            List<LivingThing> jointTargets = boss.pickJointTargets(fight);
            check("共祭：共同目标由 BOSS 选一次（对面只有测试玩家时就是 1 个）",
                    jointTargets.size() == 1 && jointTargets.contains(hero));
            long hpBeforeJoint = hero.getHp();
            int absorbedByJoint = boss.absorbSacrificedContainers(fight, new ArrayList<>(List.of(container)));
            check("共祭：BOSS 本体也参与了这一轮攻击（测试玩家掉血），随后容器被吸收",
                    absorbedByJoint == 1 && hero.getHp() < hpBeforeJoint);

            // ⑩ 召唤物的阵营要跟着召唤者走：盗火行者也能被选成玩家角色（镜像对局），
            //    那时它的容器必须进【我方】—— 否则 getOpponentList(容器) 会落到 else 分支
            //    返回【我方】，变成"自家容器打自家队伍"，还会给自家 BOSS 叠一条 origin 不同的侵蚀。
            FlameReaver allyBoss = new FlameReaver(150);
            fight.addFighter(allyBoss);
            cn.gfhnv.game.officialStuff.customEntity.summons.BrokenContainer allyContainer =
                    allyBoss.summonContainer(fight);
            check("召唤物阵营：玩家侧 BOSS 召唤的容器进【我方】",
                    allyContainer != null && fight.getFighterList().contains(allyContainer));
            check("召唤物阵营：它的对手是【敌方】（不会打自己人）",
                    allyContainer != null && fight.getOpponentList(allyContainer).contains(boss)
                            && !fight.getOpponentList(allyContainer).contains(allyBoss));
            // 收尾：这两个探测实体排在时间轴上的回合条目也清掉
            cn.gfhnv.game.system.fight.TurnManager.getTurns().removeIf(t -> t != null
                    && (t.getLivingThing() == allyBoss || t.getLivingThing() == allyContainer));
        } catch (Exception e) {
            fail("阵营测试抛出异常：" + e);
        }
    }

    /**
     * 盗火行者的<b>切阶段保护</b>（2026-10-03 用户要求）：
     * 二阶段的触发从"血量比例掉到 50%"改成<b>血条第一次被清空</b> ——
     * 那一发致命伤害被整个吃掉（不会死），生命回满，并拿到 70% 免伤。
     * 保护<b>只有一次</b>：二阶段里再被清空血条就真的倒下。
     */
    private static void testFlameReaverPhaseTwo() {
        section("盗火行者：切阶段保护（血条第一次清空时进二阶段）");
        try {
            cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver reaver =
                    new cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver(125);
            LivingThing attacker = new PlayerOne(125).copy();
            attacker.setCriticalRate(-1);   // 永不暴击，避免随机数干扰
            Skill probe = new ProbeSkill("测伤", new ArrayList<>(), 1, 1.0);

            check("前提：满血、且还没进二阶段（构造出来就是阶段一）",
                    reaver.getHp() == reaver.getHpMax() && !reaver.isPhaseTwo());

            // ① 血条打到 1，再来一发致死伤害 —— 应该被拦下并切成二阶段
            reaver.setHp(1);
            DamageEvent lethal = new DamageEvent(attacker, reaver, probe);
            lethal.getDamage().setDamageAmount(9999L);
            reaver.takeDamage(lethal);
            check("致命伤害被挡下：没死，而且进二阶段了",
                    reaver.isPhaseTwo() && reaver.isAlive());
            check("进二阶段时生命回满（不是锁 1 血）",
                    reaver.getHp() == reaver.getHpMax());
            check("进二阶段后拿到 70% 免伤（承伤倍率 0.3）",
                    Math.abs(reaver.getDamageTakenMultiplier() - 0.3) < 1e-9);

            // ② 试算必须是只读的：AI 预判一次不能就把这次保护花掉
            cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver anticipatedTarget =
                    new cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver(125);
            anticipatedTarget.setHp(1);
            DamageEvent anticipated = new DamageEvent(attacker, anticipatedTarget, probe);
            anticipated.getDamage().setDamageAmount(9999L);
            long anticipatedHp = anticipatedTarget.anticipating(() ->
                    anticipatedTarget.modifyIncomingDamage(
                            anticipatedTarget.getHp() - anticipated.getDamage().getDamageAmount(),
                            anticipated));
            check("试算期间只报「最多打到 1 血」：不切阶段、不回血",
                    anticipatedHp == 1 && !anticipatedTarget.isPhaseTwo()
                            && anticipatedTarget.getHp() == 1);
            check("预判之后保护还在（试算没把它消费掉）",
                    !anticipatedTarget.isPhaseTwo());
            anticipatedTarget.takeDamage(anticipated);
            check("预判过之后真挨一下，才切阶段并回满",
                    anticipatedTarget.isPhaseTwo()
                            && anticipatedTarget.getHp() == anticipatedTarget.getHpMax());

            // ③ 保护只有一次：二阶段里再被清空血条就真的倒下
            reaver.setHp(1);
            DamageEvent secondLethal = new DamageEvent(attacker, reaver, probe);
            secondLethal.getDamage().setDamageAmount(9999L);
            reaver.takeDamage(secondLethal);
            check("保护只有一次：二阶段里再被清空血条就真的倒下",
                    !reaver.isAlive() && reaver.getHp() == 0);

            // ④ 兜底路径：绕过伤害结算直接把血设成 0，updateSelf 也会补上切阶段
            cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver bypassed =
                    new cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver(125);
            bypassed.setHp(0);
            bypassed.updateSelf();
            check("绕过伤害结算的扣血（直接 setHp(0)）也被 updateSelf 兜底切阶段并回满",
                    bypassed.isPhaseTwo() && bypassed.getHp() == bypassed.getHpMax());
        } catch (Exception e) {
            check("盗火行者的切阶段逻辑不该抛异常，实际抛了：" + e, false);
            e.printStackTrace();
        }
    }

    /**
     * 跑本主题的全部用例（顺序 = 下面列的顺序）。
     */
    public static void runAll() throws Exception {
        testFlameReaverFactions();
        testFlameReaverPhaseTwo();
    }
}
