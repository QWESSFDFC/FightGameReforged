package cn.gfhnv.debug_tools;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.officialStuff.customEntity.players.PlayerOne;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.ActEntry;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.fight.TurnEntry;

import java.util.ArrayList;
import java.util.List;

import static cn.gfhnv.debug_tools.TestKit.*;
import static cn.gfhnv.debug_tools.TestSupport.entityTemplateOf;

/**
 * 自测：**战斗**。
 * <p>
 * 主题：伤害显示口径、承伤倍率语义、回合动作队列。
 * <p>
 * 由 {@code TestCommandSystem#main} 按顺序调用 {@code DamageDisplayTests#runAll()}。
 */
public final class DamageDisplayTests {

    private DamageDisplayTests() {
    }

    /**
     * <b>攻击行显示"应该造成的伤害"，不是"实际掉血"</b>（2026-10-24 用户拍板）。
     * <p>
     * 例：一刀 90000 打在只剩 400 血的敌人身上 —— 旧日志显示 {@code -400}（HP 变化量），
     * 看不出这一刀的威力；现在显示 {@code -90000}。
     * <p>
     * 这条也顺带钉住"被拦下时数字会大于 HP 差"这个刻意的行为（免死/高额免伤都会这样）。
     */

    /**
     * <b>回合动作队列要"排空"，不是"遍历一遍"</b>。
     * <p>
     * 为什么必须这样：回合循环若用 {@code Iterator} 直接遍历行动队列，
     * 而"转阶段播报"在动作执行中往同一个列表里 {@code add}，下一次 {@code next()} 会抛
     * {@code ConcurrentModificationException} → 这一回合剩下的动作全没跑、提示也丢了，
     * 还会被 EventBus 的异常隔离吞掉。正确做法是"快照 + 清空 + 再来一轮"。
     * <p>
     * 验三件事：① 执行中追加的动作**在同一个回合内**跑完；② 顺序不乱（先 A 后 B）；
     * ③ 有动作自己追加自己时**停下来报警**，不会把游戏卡死。
     */
    private static void testTurnActionQueueDrains() {
        section("回合动作队列：执行中追加的动作也要跑完（防 CME 回归）");

        PlayerOne actor = new PlayerOne(125);
        Fight fight = new Fight(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(List.of(actor)));
        cn.gfhnv.game.system.fight.TurnEntry turn =
                new cn.gfhnv.game.system.fight.TurnEntry(actor, java.math.BigDecimal.ONE, java.math.BigDecimal.ZERO);

        // ① + ②：A 跑的时候往队列里追加 B
        java.util.List<String> order = new ArrayList<>();
        turn.getLastExecuteList().add((f, u) -> {
            order.add("A");
            turn.getLastExecuteList().add((f2, u2) -> order.add("B"));
        });
        int executed = cn.gfhnv.game.eventListener.FightTurnPastListener.runExecuteList(
                fight, turn, turn.getLastExecuteList(), "收尾");
        check("队列排空：执行中追加的动作也跑了（A → B），共执行 " + executed + " 个 —— 实际 " + order,
                order.equals(List.of("A", "B")) && executed == 2);

        // 开场队列（firstExecuteList）走的是同一个排空函数 —— 单独钉一条，免得以后有人只改收尾
        java.util.List<String> opening = new ArrayList<>();
        turn.getFirstExecuteList().add((f, u) -> {
            opening.add("开场-A");
            turn.getFirstExecuteList().add((f2, u2) -> opening.add("开场-B"));
        });
        int openingExecuted = cn.gfhnv.game.eventListener.FightTurnPastListener.runExecuteList(
                fight, turn, turn.getFirstExecuteList(), "开场");
        check("队列排空：开场队列也一样（执行中追加的也跑完，A → B）—— 实际 " + opening,
                opening.equals(List.of("开场-A", "开场-B")) && openingExecuted == 2);

        // 效果 tick 遍历中给同一个对象新挂效果：不能抛 CME
        LivingThing ticked = entityTemplateOf("game_official_content:commonInsect").copy();
        ticked.addEffectDirect(new cn.gfhnv.game.officialStuff.customEffect.universalEffects.Taunt(2, 1));
        turn.getLastExecuteList().clear();
        cn.gfhnv.game.event.EffectUpdateEvent updateEvent =
                new cn.gfhnv.game.event.EffectUpdateEvent(ticked, turn);
        boolean threw = false;
        int beforeCount = ticked.getEntityEffectList().size();
        try {
            EventBus.post(updateEvent);
        } catch (Exception e) {
            threw = true;
        }
        check("效果 tick：遍历中不会因为列表被改而抛异常（tick 前后效果数 " + beforeCount + " → "
                + ticked.getEntityEffectList().size() + "）", !threw);

        // ③：动作自己追加自己 → 到达上限后强制停止并报警（不能无限循环）
        // ③（2026-10-24 用户拍板去掉了 64 轮上限）：只验"排空"这件事，
        // 不再拿"自己无限追加自己"来撞上限 —— 没有上限之后那种动作会真的死循环，
        // 属于设计错误（靠评审保证），不该写成用例。
    }

    /**
     * 回合内行动队列（{@link ActEntry}）：优先级 / 同优先级稳定 / 执行中追加 / 执行前移除 / 老两段视图。
     * <p>2026-11-14 第 59 批把"开场 / 正式行动 / 收尾"合成一条队列之后，这些性质必须钉死。
     */
    private static void testActQueuePriority() {
        section("回合行动队列（ActEntry 优先级）");
        try {
            LivingThing actor = new PlayerOne(125);
            TurnEntry turn = new TurnEntry(actor, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO);
            List<String> order = new ArrayList<>();

            turn.getActQueue().add(new ActEntry((f, u) -> {
                order.add("LOW-1");
                // 执行中追加：优先级 20，比剩下的都高，应该紧接着执行
                turn.getActQueue().add(new ActEntry((f2, u2) -> order.add("APPENDED"), 20, "appended"));
            }, 0, "low-1"));
            turn.getActQueue().add(new ActEntry((f, u) -> order.add("HIGH"), 10, "high"));
            turn.getActQueue().add(new ActEntry((f, u) -> {
                order.add("LOW-2");
                // 同优先级追加：应排在已有同优先级之后（稳定）
                turn.getActQueue().add(new ActEntry((f2, u2) -> order.add("APPENDED-SAME"), 0, "same"));
            }, 0, "low-2"));
            ActEntry removed = new ActEntry((f, u) -> order.add("REMOVED"), 5, "removed");
            turn.getActQueue().add(removed);
            // 执行前移除：直接把它从队列里摘掉
            turn.getActQueue().remove(removed);
            // 老视图：收尾动作仍然落在 LAST 档（最后执行）
            turn.getLastExecuteList().add((f, u) -> order.add("LAST"));

            cn.gfhnv.game.eventListener.FightTurnPastListener.runActQueue(null, turn);

            check("队列①：优先级大的先跑、同优先级按入队顺序、执行中追加的条目也在同一轮跑完 —— 实际 " + order,
                    order.equals(List.of("HIGH", "LOW-1", "APPENDED", "LOW-2", "APPENDED-SAME", "LAST")));
            check("队列②：执行前从队列移除的条目不会被执行", !order.contains("REMOVED"));
            check("队列③：老的两段视图仍然有效（getLastExecuteList 落在收尾档）",
                    order.indexOf("LAST") == order.size() - 1);
            check("队列④：跑完之后队列是空的", turn.getActQueue().isEmpty());
        } catch (Exception e) {
            fail("回合行动队列测试抛出异常：" + e);
        }
    }

    private static void testAttackLineShowsIntendedDamage() {
        section("攻击行：显示应该造成的伤害（溢出伤害照原值显示）");

        cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade blade =
                new cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade(125);
        LivingThing dummy = entityTemplateOf("game_official_content:commonInsect").copy();
        Fight fight = new Fight(new ArrayList<>(List.of(dummy)), new ArrayList<>(),
                new ArrayList<>(List.of(blade)));
        // 只留 400 血：这一刀会远远溢出
        dummy.setHpMax(9_000_000L);
        dummy.setHp(400L);
        java.io.PrintStream original = System.out;
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        long hpBefore = dummy.getHp();
        try {
            System.setOut(new java.io.PrintStream(buffer, true, java.nio.charset.StandardCharsets.UTF_8));
            new cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.MortalBladeCommonAttack()
                    .comeToEffect(fight, blade, List.of(dummy));
        } finally {
            System.setOut(original);
        }
        String line = buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
        String shown = null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("-(-?\\d+)").matcher(line);
        if (matcher.find()) {
            shown = matcher.group(1);
        }
        check("攻击行：显示的伤害是该打多少（4 位以上），而不是只剩 400 血的那个 400 —— 日志「"
                        + line.trim() + "」",
                shown != null && Long.parseLong(shown) > 400L);
        check("攻击行：血确实被清空了（400 → " + dummy.getHp() + "）、而且是鞭尸式的多段也不改口径",
                hpBefore == 400L && dummy.getHp() <= 0L);
    }

    /**
     * <b>承伤倍率允许 &gt; 1</b>（2026-10-24，用户拍板"让负数表达增伤"）：负的减伤 = 增伤。
     * <p>
     * 为什么改：千冶·刃的【煞火缠身】要"受到的伤害 +50%"，而旧实现把倍率夹在 {@code [0,1]}、
     * 并且在 {@code addDamageReduction} 里<b>静默丢掉</b>负数（钳制 {@code [0,1]} + 只留 {@code > 0}）
     * —— 写 {@code -0.5} 会看起来完全没效果。
     * <p>
     * 验四件事：① 负数真的进了表（不再被丢掉）；② 倍率与"总减伤"按新语义算；
     * ③ <b>下界仍是 0</b>（减伤叠满不会算出负伤害 = 治疗）；④ 行为对照：同一只木桩上
     * 挂一个 -0.5 来源之后，同一技能打出来的伤害约 ×1.5。
     */
    private static void testDamageTakenIncrease() {
        section("承伤倍率：负数 = 增伤（原来被夹在 [0,1] 且负数会被丢掉）");

        LivingThing probe = entityTemplateOf("game_official_content:commonInsect").copy();
        Object source = new Object();
        probe.addDamageReduction(source, -0.5d);
        check("增伤：负数减伤不再被丢掉（表里有一条）—— 实际 " + probe.getDamageReductions().size() + " 条",
                probe.getDamageReductions().size() == 1);
        check("增伤：-0.5 → 承伤倍率 1.5、总减伤 -0.5 —— 实际 " + probe.getDamageTakenMultiplier()
                        + " / " + probe.getDamageAbsorbedPercent(),
                Math.abs(probe.getDamageTakenMultiplier() - 1.5d) < 1e-9
                        && Math.abs(probe.getDamageAbsorbedPercent() + 0.5d) < 1e-9);

        probe.removeDamageReduction(source);
        probe.addDamageReduction(source, 1.5d);
        check("下界仍然是 0：减伤写 1.5 会被夹到 1 → 承伤倍率 0（不会算出负伤害=治疗）—— 实际 "
                        + probe.getDamageTakenMultiplier(),
                probe.getDamageTakenMultiplier() == 0d);
        probe.removeDamageReduction(source);
        probe.addDamageReduction(source, -2d);
        check("上界：增伤写 -2 会被夹到 -1 → 最多翻倍（倍率 2）—— 实际 " + probe.getDamageTakenMultiplier(),
                Math.abs(probe.getDamageTakenMultiplier() - 2d) < 1e-9);
        probe.removeDamageReduction(source);

        // ④ 行为对照：同一只木桩，挂 -0.5 之后同一个技能打出来的伤害约 ×1.5
        cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade blade =
                new cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade(125);
        LivingThing plain = entityTemplateOf("game_official_content:commonInsect").copy();
        LivingThing vulnerable = entityTemplateOf("game_official_content:commonInsect").copy();
        plain.setHpMax(9_000_000L);
        plain.setHp(plain.getHpMax());
        vulnerable.setHpMax(9_000_000L);
        vulnerable.setHp(vulnerable.getHpMax());
        Fight fight = new Fight(new ArrayList<>(List.of(plain, vulnerable)), new ArrayList<>(),
                new ArrayList<>(List.of(blade)));
        Object increase = new Object();
        vulnerable.addDamageReduction(increase, -0.5d);
        Skill attack = blade.getController().getSkills().get(0);
        long plainHp0 = plain.getHp();
        long vulnHp0 = vulnerable.getHp();
        attack.use(fight, blade, List.of(plain));
        attack.copy().use(fight, blade, List.of(vulnerable));
        long damagePlain = plainHp0 - plain.getHp();
        long damageVuln = vulnHp0 - vulnerable.getHp();
        check("行为：挂了 -0.5 增伤的木桩挨的伤害约 ×1.5 —— 实际 " + damagePlain + " vs " + damageVuln,
                damagePlain > 0 && Math.abs(damageVuln - damagePlain * 1.5) <= Math.max(3, damagePlain / 20));

        // 【煞火缠身】的"受伤 +50%"与"防御 -30%"都要在，而且到期能撤回
        LivingThing bindTarget = entityTemplateOf("game_official_content:commonInsect").copy();
        cn.gfhnv.game.officialStuff.customEffect.mortenaxBladeEffects.BalefireBindEffect bind =
                new cn.gfhnv.game.officialStuff.customEffect.mortenaxBladeEffects.BalefireBindEffect(2);
        bind.comeIntoEffect(bindTarget);
        check("煞火缠身：防御 -0.3 与 受伤 ×1.5 同时生效 —— 实际 defenceLoss=" + bindTarget.getDefenceLoss()
                        + "、倍率 " + bindTarget.getDamageTakenMultiplier(),
                Math.abs(bindTarget.getDefenceLoss() - 0.3d) < 1e-9
                        && Math.abs(bindTarget.getDamageTakenMultiplier() - 1.5d) < 1e-9);
        bind.whenLastTimeEnd(bindTarget);
        check("煞火缠身：到期两个都撤回（倍率回 1、防御削减回 0）—— 实际 "
                        + bindTarget.getDamageTakenMultiplier() + " / " + bindTarget.getDefenceLoss(),
                Math.abs(bindTarget.getDamageTakenMultiplier() - 1d) < 1e-9
                        && Math.abs(bindTarget.getDefenceLoss()) < 1e-9);
    }

    /**
     * 跑本主题的全部用例（顺序 = 下面列的顺序）。
     */
    public static void runAll() throws Exception {
        testDamageTakenIncrease();
        testActQueuePriority();
        testAttackLineShowsIntendedDamage();
        testTurnActionQueueDrains();
    }
}
