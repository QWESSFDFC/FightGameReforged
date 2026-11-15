package cn.gfhnv.debug_tools;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entity.PropertyEnhanceType;
import cn.gfhnv.game.officialStuff.customEntity.players.PlayerOne;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.ActionSignal;
import cn.gfhnv.game.system.fight.TurnEntry;
import cn.gfhnv.game.system.fight.TurnManager;

import java.util.ArrayList;
import java.util.List;

import static cn.gfhnv.debug_tools.TestKit.*;
import static cn.gfhnv.debug_tools.TestProbes.ProbeSkill;

/**
 * 自测：**战斗底座**。
 * <p>
 * 主题：战斗底座的通用件：倒计时实体、治疗量加成。
 * <p>
 * 由 {@code TestCommandSystem#main} 按顺序调用 {@code CombatCoreTests#runAll()}。
 */
public final class CombatCoreTests {

    private CombatCoreTests() {
    }

    /**
     * <b>治疗量加成</b>（{@code healEnhancePercent}，2026-10-24 加的第 13 个运行时加成）+ 统一治疗入口
     * {@link LivingThing#heal(long)}。
     * <p>
     * 为什么要它：以前治疗散落在各处写着 {@code setHp(getHp() + n)}，千冶·刃的「千锻魂」
     * （结界期间受到的治疗量 +50%）无处生效。现在凡治疗都走 {@code heal(long)}，
     * 实际回血 = {@code 原值 × (1 + healEnhancePercent)} 并夹在上限内。
     * <p>
     * 验四件事：出厂 0 时行为逐位不变（回多少就是多少）/ 加成生效 / 按上限夹取 / 每局结束清零（临时属性）。
     */
    private static void testHealEnhancePercent() {
        section("治疗量加成：heal(long) 走 healEnhancePercent");

        PlayerOne probe = new PlayerOne(125);
        probe.setHp(probe.getHpMax() / 2);
        check("治疗量加成：出厂时一条条目都没有（不配就是老行为）",
                probe.getPropertyEnhancePercent(PropertyEnhanceType.HEAL) == 0d
                        && probe.getPropertyEnhances().isEmpty());

        long before = probe.getHp();
        long healed = probe.heal(100);
        check("治疗量加成：0% 时 heal(100) 就回 100 —— 实际 " + healed,
                healed == 100 && probe.getHp() == before + 100);

        probe.addPropertyEnhance(PropertyEnhanceType.HEAL, 0.5d, 0, "自测-治疗量加成");
        healed = probe.heal(100);
        check("治疗量加成：+50% 时 heal(100) 回 150 —— 实际 " + healed, healed == 150);

        probe.setHp(probe.getHpMax() - 10);
        healed = probe.heal(1000);
        check("治疗量加成：按上限夹取（只回 10，不会溢出上限）—— 实际回 " + healed
                        + "，血 " + probe.getHp() + "/" + probe.getHpMax(),
                healed == 10 && probe.getHp() == probe.getHpMax());

        probe.whenFightEnds();
        check("治疗量加成：每局结束清零（它是临时属性，不是面板属性）—— 实际 "
                + probe.getPropertyEnhancePercent(PropertyEnhanceType.HEAL), probe.getPropertyEnhancePercent(PropertyEnhanceType.HEAL) == 0d);

        check("治疗量加成：heal(0) / 负数 一律回 0（不会把自己治成负血）",
                probe.heal(0) == 0 && probe.heal(-500) == 0);
    }

    /**
     * <b>通用倒计时实体</b>（{@code CountdownEntity}，2026-10-24 为千冶·刃的结界做的）：
     * 在时间轴上占一个回合、到点执行回调、然后自己消失。
     * <p>
     * 验的是"接进回合循环不会崩、不会自己行动、能被延后/摘掉"这四件事 ——
     * 回合循环里逐行对着的约束见 {@code CountdownEntity} 的类注释（控制器不能 null /
     * {@code SKIP_WITHOUT_NEW_TURN} / {@code setExtra(true)}）。
     */
    private static void testCountdownEntity() {
        section("通用倒计时实体：占一个回合 → 到点回调 → 自己消失");

        cn.gfhnv.game.entity.CountdownEntity countdown =
                new cn.gfhnv.game.entity.CountdownEntity("无量忿怒", 70);
        check("倒计时：控制器不是 null（回合循环会遍历 controller.getSkills()，空指针会崩）",
                countdown.getController() != null && countdown.getController().getSkills().isEmpty());
        check("倒计时：名字/速度按参数来（速度 70）—— 实际 " + countdown.getName() + " / " + countdown.getSpeed(),
                countdown.getName().contains("无量忿怒") && countdown.getSpeed() == 70L);

        // ⚠️ isAlive() 是按 hp <= 0 判死的（不是看 alive 标志），hp=0 的倒计时会被
        // TurnManager#removeTheDeath() 每回合摘掉 —— 结界就永远不会到点（2026-10-24 实测踩到）
        check("倒计时：有正 HP，所以 isAlive() 为真（否则会被 removeTheDeath 从时间轴摘掉）—— 实际 hp="
                        + countdown.getHp() + "、alive=" + countdown.isAlive(),
                countdown.getHp() > 0 && countdown.isAlive());
        countdown.schedule();
        TurnManager.removeTheDeath();
        check("倒计时：removeTheDeath() 之后仍然在时间轴上（否则结界到不了点）",
                countdown.isScheduled());
        countdown.cancel();

        TurnEntry entry = countdown.schedule();
        double expectNeed = 10000d / 70d;
        check("倒计时：排进时间轴、时间 = 当前 + 10000/70（≈ 142.857）—— 实际 "
                        + entry.getNeedTime() + "，在轴上 " + countdown.isScheduled(),
                countdown.isScheduled()
                        && Math.abs(entry.getNeedTime().doubleValue() - expectNeed) < 1e-6);
        check("倒计时：条目是 extra + SKIP_WITHOUT_NEW_TURN（不被当成'下一次正常行动'、也不自己行动）—— 实际 "
                        + entry.isExtra() + " / " + entry.getActionSignal(),
                entry.isExtra() && entry.getActionSignal() == ActionSignal.SKIP_WITHOUT_NEW_TURN);

        countdown.delayByPercent(0.15d);
        check("倒计时：延后 15% → 剩余时间 ×1.15 —— 实际 " + entry.getNeedTime(),
                Math.abs(entry.getNeedTime().doubleValue() - expectNeed * 1.15d) < 1e-6);

        boolean[] fired = new boolean[]{false};
        countdown.setOnFire((fight, self) -> fired[0] = true);
        entry.getLastExecuteList().get(0).execute(null, countdown);
        check("倒计时：到点执行回调、并把自己从时间轴摘掉 —— 回调 " + fired[0]
                        + "、还在轴上 " + countdown.isScheduled(),
                fired[0] && !countdown.isScheduled() && !TurnManager.getTurns().contains(entry));

        countdown.cancel();
        check("倒计时：cancel() 可重复调用（已经触发过再摘一次不该抛）", !countdown.isScheduled());
    }

    /**
     * 冷却语义（2026-11-15 第 72 批，B1）：<b>有冷却的 +1，没冷却的保持 0</b>。
     * <p>
     * 白厄变身后的整套技能、普攻都是 {@code coolDown == 0} —— 它们**不能**因为这次改动多出冷却。
     */
    private static void testCooldownSemantics() {
        section("冷却语义：有冷却 +1、无冷却保持 0");
        try {
            LivingThing user = new PlayerOne(125);
            LivingThing enemy = new cn.gfhnv.game.officialStuff.customEntity.monsters.CommonInsect(125L);
            List<LivingThing> fighters = new ArrayList<>();
            fighters.add(user);
            List<LivingThing> enemies = new ArrayList<>();
            enemies.add(enemy);
            cn.gfhnv.game.system.fight.Fight fight = new cn.gfhnv.game.system.fight.Fight(
                    enemies, new ArrayList<>(), fighters);

            Skill noCooldown = new ProbeSkill("无冷却", new ArrayList<>());
            noCooldown.setCoolDown(0);
            boolean used = noCooldown.use(fight, user);
            check("① coolDown=0（普攻 / 白厄变身技能那类）：释放后 nowCoolDown 仍是 0 —— 实际 used="
                            + used + "，nowCoolDown=" + noCooldown.getNowCoolDown(),
                    used && noCooldown.getNowCoolDown() == 0);

            Skill hasCooldown = new ProbeSkill("冷却二", new ArrayList<>());
            hasCooldown.setCoolDown(2);
            boolean used2 = hasCooldown.use(fight, user);
            check("② coolDown=2：释放后 nowCoolDown = 3（+1）—— 实际 used=" + used2
                            + "，nowCoolDown=" + hasCooldown.getNowCoolDown(),
                    used2 && hasCooldown.getNowCoolDown() == 3);

            check("③ 白厄变身技能确实声明了 coolDown=0（靠上一条的 guard 才不会多出冷却）",
                    new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.AwakenCommonAttack()
                            .getCoolDown() == 0);
        } catch (Exception e) {
            fail("冷却语义测试抛出异常：" + e);
        }
    }

    /**
     * 跑本主题的全部用例（顺序 = 下面列的顺序）。
     */
    public static void runAll() throws Exception {

        testCooldownSemantics();
        testCountdownEntity();
        testHealEnhancePercent();
    }
}
