package cn.gfhnv.debug_tools;

import cn.gfhnv.game.data.*;
import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.SelectTargetEvent;
import cn.gfhnv.game.interfaces.IModifyDamage;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.Taunt;
import cn.gfhnv.game.officialStuff.customEntity.players.PlayerOne;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.command.*;
import cn.gfhnv.game.system.fight.*;

import java.util.ArrayList;
import java.util.List;

import static cn.gfhnv.debug_tools.TestKit.*;
import static cn.gfhnv.debug_tools.TestSupport.*;
import static cn.gfhnv.debug_tools.TestProbes.*;

/**
 * 自测：**官方角色**。
 * <p>
 * 主题：千冶·刃的全部机制（资源 / 技能 / 结界 / 充能 / 追打 / 星魂）。
 * <p>
 * 由 {@code TestCommandSystem#main} 按顺序调用 {@code MortalBladeTests#runAll()}。
 */
public final class MortalBladeTests {

    /**
     * <b>千冶·刃的结界 / 【无量忿怒】</b>（P0 第 3 步，2026-10-24）：终结技「骸骨当炉，血肉即薪」开结界，
     * 倒计时（固定 70 速度）到点或免死时解除。
     * <p>
     * 验六件事：① 门槛（能量满、不在结界里）；② 施放时敌方全体【煞火缠身】防御 -30%；
     * ③ 扣 20% 生命上限（血不够降到 1）；④ 进【无量忿怒】后暴击率/暴击伤害真的涨了、
     * 技能表换了、倒计时在轴上；⑤ 退出时**全部回落**（加成不能留在身上）、倒计时摘掉；
     * ⑥ 免死：致命伤不会倒下、回 50% 生命上限、并退出结界。
     */
    private static void testInfiniteFuryZone() {
        section("千冶·刃的结界与【无量忿怒】：终结技 / 倒计时 / 免死");

        cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade blade =
                new cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade(125);
        LivingThing enemyA = entityTemplateOf("game_official_content:commonInsect").copy();
        LivingThing enemyB = entityTemplateOf("game_official_content:commonInsect").copy();
        Fight fight = new Fight(new ArrayList<>(List.of(enemyA, enemyB)), new ArrayList<>(),
                new ArrayList<>(List.of(blade)));
        Skill ultimate = null;
        for (Skill skill : blade.getController().getSkills()) {
            if (skill instanceof cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.FornaxExCorpore) {
                ultimate = skill;
            }
        }
        check("技能表里有终结技「骸骨当炉，血肉即薪」", ultimate != null);
        if (ultimate == null) {
            return;
        }

        check("门槛：能量不满（0/160）时放不出来", !ultimate.canUse(fight, blade, null)
                && !ultimate.canUse(fight, blade));
        blade.setEnergy(blade.getEnergyMax());
        check("门槛：能量满了就能放（160/160）", ultimate.canUse(fight, blade, null));
        check("门槛：不是千冶·刃的人放不出来（专属技能）", !ultimate.canUse(fight, enemyA, null));

        long hpBefore = blade.getHp();
        long expectCost = (long) (blade.getHpMax() * cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.FornaxExCorpore.HP_COST_RATIO);
        ultimate.use(fight, blade, List.of(blade));
        check("骸骨当炉：开结界那一招<b>不回血</b>（额外回血只挂在千冶铸一上）—— 实际 "
                        + blade.getHp() + " / " + blade.getHpMax(),
                blade.getHp() < blade.getHpMax());
        check("施放：扣掉生命上限 20% —— 实际掉了 " + (hpBefore - blade.getHp()) + "（应 " + expectCost + "）",
                Math.abs((hpBefore - blade.getHp()) - expectCost) <= 1);
        // 付 160，然后按设计稿的 Energy gain 回 5 → 净剩 5
        check("施放：付掉 160 能量，再回 5（Energy gain）—— 实际 " + blade.getEnergy(),
                blade.getEnergy() == 5);

        List<String> noBind = new ArrayList<>();
        double expectLoss = cn.gfhnv.game.officialStuff.customEffect.mortenaxBladeEffects.BalefireBindEffect.DEFENCE_LOSS;
        for (LivingThing enemy : List.of(enemyA, enemyB)) {
            if (Math.abs(enemy.getDefenseLoss() - expectLoss) > 1e-9) {
                noBind.add(enemy.getName() + "=" + enemy.getDefenseLoss());
            }
        }
        check("施放：敌方全体【煞火缠身】→ 防御削减 30% —— 不符的 " + noBind, noBind.isEmpty());

        check("结界：状态位打开了，暴击率 +20% / 暴击伤害 +60% —— 实际 "
                        + blade.isInfiniteFury() + "、" + blade.getCriticalRateEnhancePercent()
                        + " / " + blade.getCriticalDMGEnhancePercent(),
                blade.isInfiniteFury()
                        && Math.abs(blade.getCriticalRateEnhancePercent()
                        - cn.gfhnv.game.officialStuff.customEffect.mortenaxBladeEffects.InfiniteFuryEffect.CRITICAL_RATE_BONUS) < 1e-9
                        && Math.abs(blade.getCriticalDMGEnhancePercent()
                        - cn.gfhnv.game.officialStuff.customEffect.mortenaxBladeEffects.InfiniteFuryEffect.CRITICAL_DAMAGE_BONUS) < 1e-9);
        check("结界：倒计时上了时间轴（速度 70，标签/名字带「无量忿怒」）—— 实际 "
                        + (blade.getFuryCountdown() == null ? "null" : blade.getFuryCountdown().getEntry()),
                blade.getFuryCountdown() != null && blade.getFuryCountdown().isScheduled());

        // 结界期间技能表换成"强化普攻 / 解放战技 / 全新终结技"
        List<String> furySkillNames = new ArrayList<>();
        for (Skill skill : blade.getController().getSkills()) {
            furySkillNames.add(skill.getName());
        }
        check("结界期间：技能表换成【无量忿怒】那三条（淬锋，断魄=强化普攻 / 刃下，归葬 / 千冶铸一）—— 实际 "
                        + furySkillNames,
                furySkillNames.size() == 3
                        && furySkillNames.get(0).contains("淬锋")
                        && furySkillNames.get(1).contains("刃下")
                        && furySkillNames.get(2).contains("千冶铸一"));

        Skill burial = blade.getController().getSkills().get(1);
        check("刃下，归葬：全体（aims=-1）、倍率 72% 生命上限、额外 4 段 ×24% —— 实际 aims="
                        + burial.getAims() + "、hpMag=" + burial.getHpMagnification(),
                burial.getAims() == -1
                        && Math.abs(burial.getHpMagnification()
                        - cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.BladeUnderBurial.MAIN_MAGNIFICATION) < 1e-9
                        && cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.BladeUnderBurial.EXTRA_HITS == 4
                        && Math.abs(cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.BladeUnderBurial.EXTRA_HIT_MAGNIFICATION - 0.24) < 1e-9);

        long hpBeforeBurial = blade.getHp();
        long expectBurialCost = (long) (blade.getHpMax()
                * cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.BladeUnderBurial.HP_COST_RATIO);
        long enemyHpBefore = enemyA.getHp();
        burial.use(fight, blade, new ArrayList<>(List.of(enemyA, enemyB)));
        check("刃下，归葬：扣 10% 生命上限（应 " + expectBurialCost + "）—— 实际 " + (hpBeforeBurial - blade.getHp()),
                Math.abs((hpBeforeBurial - blade.getHp()) - expectBurialCost) <= 1);
        check("刃下，归葬：敌人真的掉血（全体 + 4 段随机）—— 虫子 " + enemyHpBefore + " → " + enemyA.getHp(),
                enemyA.getHp() < enemyHpBefore);

        Skill tenax = blade.getController().getSkills().get(2);
        blade.setEnergy(blade.getEnergyMax());
        check("千冶铸一：结界内 + 能量满才能放（倍率 350% 生命上限）—— 实际 hpMag=" + tenax.getHpMagnification(),
                tenax.canUse(fight, blade, null)
                        && Math.abs(tenax.getHpMagnification()
                        - cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.TenaxPerIgnem.MAGNIFICATION) < 1e-9);
        blade.setHp(blade.getHpMax() / 2);
        long hpBeforeTenax = blade.getHp();
        tenax.use(fight, blade, new ArrayList<>(List.of(enemyA, enemyB)));
        check("千冶铸一：付掉 160 再回 5（Energy gain）—— 实际 " + blade.getEnergy(),
                blade.getEnergy() == 5);
        check("千冶铸一：额外机制——打完生命恢复至上限（" + hpBeforeTenax + " → " + blade.getHp()
                        + " / " + blade.getHpMax() + "）",
                hpBeforeTenax < blade.getHpMax() && blade.getHp() == blade.getHpMax());

        // 免死：直接走一遍伤害修正（不建整场战斗，逻辑本身与 IModifyDamage 一致）
        // 先把血调到明确的位置，否则"回 50% 上限"会被上限夹住看不出数值
        blade.setHp(100);
        long hpBeforeWard = blade.getHp();
        long expectHeal = (long) (blade.getHpMax() * 0.5);
        long newHp = blade.modifyIncomingDamage(0, new cn.gfhnv.game.event.DamageEvent(
                enemyA, blade, blade.getController().getSkills().get(0)));
        check("免死：致命一击不倒下（血量被压到 1）、退出结界、回 50% 生命上限 —— 实际 "
                        + newHp + "、" + blade.isInfiniteFury() + "、" + blade.getHp() + "/" + blade.getHpMax(),
                newHp == 1 && !blade.isInfiniteFury()
                        && Math.abs(blade.getHp() - (hpBeforeWard + expectHeal)) <= 2);
        check("退出：暴击加成全部回落、倒计时摘掉、技能表换回普通（3 条）—— 实际 "
                        + blade.getCriticalRateEnhancePercent() + " / " + blade.getCriticalDMGEnhancePercent()
                        + "、" + (blade.getFuryCountdown() == null) + "、" + blade.getController().getSkills().size(),
                Math.abs(blade.getCriticalRateEnhancePercent()) < 1e-9
                        && Math.abs(blade.getCriticalDMGEnhancePercent()) < 1e-9
                        && blade.getFuryCountdown() == null
                        && blade.getController().getSkills().size() == 2);

        // 免死的回血必须"排进当前回合收尾"，不能当场回 —— 当场回会被伤害结算的 setHp 覆盖
        blade.setEnergy(blade.getEnergyMax());
        blade.setHp(blade.getHpMax() - 10);
        ultimate.use(fight, blade, List.of(blade));   // 重新开结界
        long hpBeforeWardAgain = blade.getHp();
        TurnEntry fakeTurn = new TurnEntry(blade, java.math.BigDecimal.ONE, java.math.BigDecimal.ZERO);
        cn.gfhnv.game.eventListener.FightTurnPastListener.setPresentTurn(fakeTurn);
        long wardHp = blade.modifyIncomingDamage(0, new cn.gfhnv.game.event.DamageEvent(
                enemyA, blade, blade.getController().getSkills().get(0)));
        check("免死：伤害结算当场只返回 1 血（不在这一刻回血，否则会被 setHp 覆盖）—— 实际 "
                        + wardHp + "，血量还停在 " + blade.getHp(),
                wardHp == 1 && blade.getHp() == hpBeforeWardAgain);
        check("免死：回血排进了当前回合的收尾队列（1 条）—— 实际 " + fakeTurn.getLastExecuteList().size(),
                fakeTurn.getLastExecuteList().size() == 1);
        // 模拟回合循环：这一击彻底结算完（setHp(1)）之后，再跑收尾队列
        blade.setHp(wardHp);
        for (cn.gfhnv.game.interfaces.ISpecialAction action : fakeTurn.getLastExecuteList()) {
            action.execute(fight, blade);
        }
        check("免死：收尾队列跑完之后血真的回来了（≈ 50% 上限）—— 实际 "
                        + blade.getHp() + " / " + blade.getHpMax(),
                Math.abs(blade.getHp() - (1 + (long) (blade.getHpMax() * 0.5))) <= 2);
        cn.gfhnv.game.eventListener.FightTurnPastListener.setPresentTurn(null);
        blade.setHp(blade.getHpMax());

        blade.endInfiniteFury();
        check("退出：重复调用幂等（加成不会被减成负数）—— 实际 " + blade.getCriticalRateEnhancePercent(),
                Math.abs(blade.getCriticalRateEnhancePercent()) < 1e-9);
    }

    /**
     * <b>千冶·刃给 AI 用的时候</b>（P0 第 6 步，用户决定 #2"要 AI 可控"）：
     * 三个关键技能都实现了 {@code IAiScoreBonus}，而且只在"该放的时候"加分。
     * <p>
     * 不加这一层的话，思维 AI 的评分是按伤害算的，0 伤害的终结技（开结界）永远打不过普攻 ——
     * 白厄的大招当初就是这个坑（见 {@code IAiScoreBonus} 接口注释）。
     */
    private static void testMortalBladeAiPriority() {
        section("千冶·刃给 AI 用：三个关键技能的优先分");

        cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade blade =
                new cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade(125);
        cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.FornaxExCorpore ultimate =
                new cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.FornaxExCorpore();
        cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.TenaxPerIgnem tenax =
                new cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.TenaxPerIgnem();
        cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.BladeUnderBurial burial =
                new cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.BladeUnderBurial();

        boolean ultimateWithEnergy = nonzeroEnergyBonus(blade, ultimate);
        check("AI 评分：开结界的终结技——能量不满时 0 分、满了加分（" + ultimateWithEnergy + "）",
                ultimate.aiScoreBonus(null, blade, blade) == 0 && ultimateWithEnergy);
        boolean tenaxInFury = inFuryBonus(blade, tenax);
        check("AI 评分：结界里的 350% 终结技——结界外 0 分、结界内 + 能量满" + tenaxInFury + " 分",
                tenax.aiScoreBonus(null, blade, blade) == 0 && tenaxInFury);
        boolean burialInFury = inFuryBonus(blade, burial);
        check("AI 评分：解禁战技——结界外 0 分、结界内加分（" + burialInFury + "）",
                burial.aiScoreBonus(null, blade, blade) == 0 && burialInFury);
        check("AI 评分：对非千冶·刃一律 0（专属技能不给别人加分）—— 实际 "
                        + ultimate.aiScoreBonus(null, entityTemplateOf("game_official_content:commonInsect"),
                        entityTemplateOf("game_official_content:commonInsect")),
                ultimate.aiScoreBonus(null, entityTemplateOf("game_official_content:commonInsect"),
                        entityTemplateOf("game_official_content:commonInsect")) == 0d);
    }

    /**
     * <b>充能监听器</b>（P0 第 6 步，「因果尽偿」前半段）：<b>我方</b>打<b>敌方</b>时千冶·刃 +1 充能，
     * 敌方打我们不给；战斗结束必须注销（不注销下一场还挂在总线上）。
     */
    /**
     * <b>【因果尽偿】的"连续触发"</b>（2026-10-24 用户澄清）：充能<b>没有上限</b>，
     * 够一次阈值就扣一次、打一次，剩下的继续 —— 81 点、阈值 9 → 连打 9 次；
     * 中途血量掉到 1 就停。E2 把阈值降到 7（7 点触发一次）。
     */
    private static void testMortalBladeChargeLoop() {
        section("【因果尽偿】连续触发：81 充能 → 连打 9 次，血不够就停，E2 阈值 7");

        cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade blade =
                new cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade(125);
        LivingThing enemy = entityTemplateOf("game_official_content:commonInsect").copy();
        enemy.setHpMax(90_000_000L);
        enemy.setHp(enemy.getHpMax());
        PlayerOne ally = new PlayerOne(125);
        Fight fight = new Fight(new ArrayList<>(List.of(enemy)), new ArrayList<>(),
                new ArrayList<>(List.of(blade, ally)));

        blade.whenFightStart(fight);
        blade.beginInfiniteFury(fight);
        blade.setHp(blade.getHpMax());
        TurnEntry loopTurn = new TurnEntry(ally, java.math.BigDecimal.ONE, java.math.BigDecimal.ZERO);
        cn.gfhnv.game.eventListener.FightTurnPastListener.setPresentTurn(loopTurn);

        // 81 点充能、阈值 9、满血：应当连打 9 次（每次扣 10% 生命上限 = 764）
        int threshold = blade.getChargeMax();
        blade.setCharge(threshold * 9);
        long hpBefore = blade.getHp();
        EventBus.post(new SelectTargetEvent(ally, new ArrayList<>(List.of(enemy)), fight));
        for (cn.gfhnv.game.interfaces.ISpecialAction action
                : new ArrayList<>(loopTurn.getLastExecuteList())) {
            action.execute(fight, ally);
        }
        cn.gfhnv.game.eventListener.FightTurnPastListener.setPresentTurn(null);
        // 81 点手灌 + 这次事件自己给的 1 点 = 82；扣 9 次 × 9 = 81 → 剩 1
        // 81 手灌 + 1（这次事件自己给的）= 82；扣 9×9 = 81 → 剩 1
        check("连续触发：81(+1) 充能 / 阈值 9 → 连打 9 次，扣掉 81 点（剩 1）—— 实际 " + blade.getCharge(),
                blade.getCharge() == 1);
        check("连续触发：每次扣 10% 生命上限（9 次 = 9 × " + (blade.getHpMax() / 10)
                        + "），血量 " + hpBefore + " → " + blade.getHp(),
                hpBefore == blade.getHpMax()
                        && blade.getHp() == blade.getHpMax() - 9 * (blade.getHpMax() / 10));

        // 血不够就停：把血调到只够打 1 次
        blade.endInfiniteFury();
        blade.beginInfiniteFury(fight);
        blade.setHp(blade.getHpMax() / 10 + 10);
        blade.setCharge(threshold * 9);
        int chargeBeforeStop = blade.getCharge();
        TurnEntry stopTurn = new TurnEntry(ally, java.math.BigDecimal.ONE, java.math.BigDecimal.ZERO);
        cn.gfhnv.game.eventListener.FightTurnPastListener.setPresentTurn(stopTurn);
        EventBus.post(new SelectTargetEvent(ally, new ArrayList<>(List.of(enemy)), fight));
        for (cn.gfhnv.game.interfaces.ISpecialAction action
                : new ArrayList<>(stopTurn.getLastExecuteList())) {
            action.execute(fight, ally);
        }
        cn.gfhnv.game.eventListener.FightTurnPastListener.setPresentTurn(null);
        // 血 774：第 1 次扣 764 → 剩 10（> 1，还能再打）→ 第 2 次扣到 1 → 停；所以是 2 次
        check("连续触发：血量不够就停（774 血只够打 2 次，扣 2 个阈值）—— 充能 "
                        + chargeBeforeStop + " → " + blade.getCharge() + "、血 " + blade.getHp(),
                blade.getCharge() == chargeBeforeStop + 1 - 2 * threshold && blade.getHp() == 1);

        // E1：一回合里连打 N 次 → 倒计时延后 N 次（每次 ×1.15）
        blade.setEidolon(1);
        blade.setHp(blade.getHpMax());
        blade.setCharge(threshold * 3);
        java.math.BigDecimal countdownBefore = blade.getFuryCountdown().getEntry().getNeedTime();
        TurnEntry delayTurn = new TurnEntry(ally, java.math.BigDecimal.ONE, java.math.BigDecimal.ZERO);
        cn.gfhnv.game.eventListener.FightTurnPastListener.setPresentTurn(delayTurn);
        EventBus.post(new SelectTargetEvent(ally, new ArrayList<>(List.of(enemy)), fight));
        for (cn.gfhnv.game.interfaces.ISpecialAction action
                : new ArrayList<>(delayTurn.getLastExecuteList())) {
            action.execute(fight, ally);
        }
        cn.gfhnv.game.eventListener.FightTurnPastListener.setPresentTurn(null);
        double expected = countdownBefore.doubleValue() * Math.pow(1.15d, 3);
        double actual = blade.getFuryCountdown().getEntry().getNeedTime().doubleValue();
        check("E1：连打 3 次额外战技 → 倒计时延后 3 次（" + countdownBefore + " → " + actual
                        + "，期望 ≈ " + Math.round(expected) + "）",
                Math.abs(actual - expected) < 1e-6);
        blade.setEidolon(0);

        // E2：阈值 7 → 同样 81 点能打 11 次（血量足够的话）
        blade.endInfiniteFury();
        blade.setEidolon(2);
        check("E2：阈值降到 7 —— 实际 " + blade.getChargeMax(), blade.getChargeMax() == 7);
        blade.beginInfiniteFury(fight);
        blade.setHp(blade.getHpMax());
        blade.setCharge(81);
        TurnEntry e2Turn = new TurnEntry(ally, java.math.BigDecimal.ONE, java.math.BigDecimal.ZERO);
        cn.gfhnv.game.eventListener.FightTurnPastListener.setPresentTurn(e2Turn);
        EventBus.post(new SelectTargetEvent(ally, new ArrayList<>(List.of(enemy)), fight));
        for (cn.gfhnv.game.interfaces.ISpecialAction action
                : new ArrayList<>(e2Turn.getLastExecuteList())) {
            action.execute(fight, ally);
        }
        cn.gfhnv.game.eventListener.FightTurnPastListener.setPresentTurn(null);
        check("E2：81 充能 / 阈值 7 → 打 9 次后被血量卡住（9 × 764 = 6876，剩 764 > 1 能再打一次 → 到 1 停）—— 充能剩 "
                        + blade.getCharge() + "、血 " + blade.getHp(),
                blade.getCharge() < 7 || blade.getHp() <= 1);
        blade.endInfiniteFury();
        blade.setEidolon(0);
        blade.setCharge(0);
        blade.setHp(blade.getHpMax());
    }

    /**
     * <b>能量的获得途径</b>（2026-10-24）：之前只有【因果尽偿】那 27 点，等于"能量只减不增"。
     * 现在按设计稿英文版的 {@code Energy gain}：普攻 +20、战技 +30、终结技 +5。
     * <p>
     * 顺带验 E1 的另一半：天赋追打之后【无量忿怒】倒计时延后 15%。
     */
    private static void testMortalBladeEnergyGain() {
        section("千冶·刃的能量获得：普攻 +20 / 战技 +30 / 终结技 +5");

        cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade blade =
                new cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade(125);
        LivingThing enemy = entityTemplateOf("game_official_content:commonInsect").copy();
        enemy.setHpMax(9_000_000L);
        enemy.setHp(enemy.getHpMax());
        Fight fight = new Fight(new ArrayList<>(List.of(enemy)), new ArrayList<>(),
                new ArrayList<>(List.of(blade)));
        blade.setEnergy(0);
        Skill common = new cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.MortalBladeCommonAttack();
        long before = blade.getEnergy();
        common.use(fight, blade, List.of(enemy));
        check("能量途径：普攻回 20（" + before + " → " + blade.getEnergy() + "）",
                Math.abs(blade.getEnergy() - before
                        - cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.MortalBladeCommonAttack.ENERGY_GAIN) <= 0);

        blade.setInfiniteFury(true);
        blade.setEnergy(0);
        Skill burial = new cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.BladeUnderBurial();
        burial.use(fight, blade, new ArrayList<>(List.of(enemy)));
        check("能量途径：战技回 30（0 → " + blade.getEnergy() + "）",
                blade.getEnergy()
                        == cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.BladeUnderBurial.ENERGY_GAIN);

        // 强化普攻也走 20
        blade.setEnergy(0);
        Skill enhanced = new cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.MortalBladeSkill();
        enhanced.use(fight, blade, List.of(enemy));
        check("能量途径：强化普攻也回 20（0 → " + blade.getEnergy() + "）", blade.getEnergy() == 20L);

        // 能量满时清负面（gainEnergy 里的钩子，之前没有"自然涨能"的途径所以没被真正触发过）
        blade.setInfiniteFury(false);
        blade.setEnergy(blade.getEnergyMax() - 10);
        blade.getEntityEffectList().add(new cn.gfhnv.game.officialStuff.customEffect.universalEffects.Taunt(1));
        common.use(fight, blade, List.of(enemy));
        check("能量途径：普攻把能量顶到上限时顺手清掉自身负面（嘲讽被清）—— 能量 " + blade.getEnergy()
                        + "、剩余负面 " + blade.getEntityEffectList().stream()
                        .filter(Effect::isNegative).count(),
                blade.getEnergy() >= blade.getEnergyMax()
                        && blade.getEntityEffectList().stream().noneMatch(Effect::isNegative));

        // E1：天赋追打之后倒计时延后 15%
        // ⚠️ 充能监听器是 whenFightStart 注册的，不调它就没有"追打"这条链
        blade.whenFightStart(fight);
        blade.setEidolon(1);
        blade.beginInfiniteFury(fight);
        java.math.BigDecimal beforeDelay = blade.getFuryCountdown().getEntry().getNeedTime();
        blade.setCharge(blade.getChargeMax() - 1);
        PlayerOne ally = new PlayerOne(125);
        fight.getFighterList().add(ally);
        EventBus.post(new SelectTargetEvent(ally, new ArrayList<>(List.of(enemy)), fight));
        check("E1：天赋追打之后倒计时延后 15%（" + beforeDelay + " → "
                        + blade.getFuryCountdown().getEntry().getNeedTime() + "）",
                Math.abs(blade.getFuryCountdown().getEntry().getNeedTime().doubleValue()
                        - beforeDelay.doubleValue() * 1.15) < 1e-6);

        blade.endInfiniteFury();
        blade.setEidolon(0);
        blade.setEnergy(0);
        blade.setCharge(0);
        blade.setHp(blade.getHpMax());
    }

    /**
     * <b>P1 的三条被动</b>（2026-10-24）：【千锻魂】减伤 50% + 受到治疗 +50% + 挨打给充能、
     * 【万淬心】队友增伤 50%（自己额外 +75%）、E1 穿透 +20%、E2 充能上限 9 → 7。
     * <p>
     * 都是"结界期间才有"的加成，所以重点验两件事：<b>结界里加上</b>、<b>退出时撤回</b>
     * —— 撤不干净就会变成永久的常驻加成（这类 bug 在别的效果上踩过）。
     */
    private static void testMortalBladePassives() {
        section("千冶·刃的被动：【千锻魂】/【万淬心】/ E1 / E2");

        cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade blade =
                new cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade(125);
        PlayerOne ally = new PlayerOne(125);
        LivingThing enemy = entityTemplateOf("game_official_content:commonInsect").copy();
        enemy.setHpMax(9_000_000L);
        enemy.setHp(enemy.getHpMax());
        Fight fight = new Fight(new ArrayList<>(List.of(enemy)), new ArrayList<>(),
                new ArrayList<>(List.of(blade, ally)));

        double baseEnhance = blade.getEnhance();
        double baseHeal = blade.getHealEnhancePercent();
        double baseMultiplier = blade.getDamageTakenMultiplier();
        double basePenetration = blade.getPenetration();
        // 队友的 enhance 要在开结界**之前**记 —— 否则记到的是"已经加过 50%"的值
        double allyEnhanceBefore = ally.getEnhance();
        blade.setEidolon(1);
        blade.beginInfiniteFury(fight);

        check("【千锻魂】：结界里受到的伤害减半（承伤倍率 " + baseMultiplier + " → "
                        + blade.getDamageTakenMultiplier() + "）",
                Math.abs(blade.getDamageTakenMultiplier() - baseMultiplier * 0.5) < 1e-9);
        check("【千锻魂】：受到的治疗量 +50%（" + baseHeal + " → " + blade.getHealEnhancePercent() + "）",
                Math.abs(blade.getHealEnhancePercent() - (baseHeal + 0.5)) < 1e-9);
        check("【万淬心】：自己额外增伤 +75%（" + baseEnhance + " → " + blade.getEnhance() + "）",
                Math.abs(blade.getEnhance() - (baseEnhance + 0.75)) < 1e-9);
        boolean allyHasEffect = ally.getEntityEffectList().stream().anyMatch(effect ->
                effect instanceof cn.gfhnv.game.officialStuff.customEffect.mortenaxBladeEffects.MortalBladeAllyEnhanceEffect);
        check("【万淬心】：队友挂上了增伤效果，且增伤真的进了他的 enhance（" + allyEnhanceBefore + " → "
                        + ally.getEnhance() + "）",
                allyHasEffect && Math.abs(ally.getEnhance() - (allyEnhanceBefore + 0.5)) < 1e-9);
        check("E1：结界里 +20% 穿透（" + basePenetration + " → " + blade.getPenetration() + "）",
                Math.abs(blade.getPenetration() - (basePenetration + 0.2)) < 1e-9);

        // 挨打：攻击者缠身 + 自己 +1 充能
        blade.setCharge(0);
        Skill anySkill = blade.getController().getSkills().get(0);
        EventBus.post(new cn.gfhnv.game.event.DamageEvent(enemy, blade, anySkill));
        boolean enemyBound = enemy.getEntityEffectList().stream().anyMatch(effect ->
                effect instanceof cn.gfhnv.game.officialStuff.customEffect.mortenaxBladeEffects.BalefireBindEffect);
        check("【千锻魂】：挨打后攻击者陷入【煞火缠身】、自己 +1 充能 —— 充能 " + blade.getCharge()
                        + "、攻击者缠身 " + enemyBound,
                enemyBound && blade.getCharge() == 1);

        blade.endInfiniteFury();
        blade.setEnhance(0);
        check("退出结界：减伤 / 治疗加成 / 自己的增伤 / 穿透 / 队友的增伤 <b>全部撤回</b> —— 实际 "
                        + blade.getDamageTakenMultiplier() + " / " + blade.getHealEnhancePercent()
                        + " / " + blade.getEnhance() + " / " + blade.getPenetration()
                        + " / 队友 " + ally.getEnhance(),
                Math.abs(blade.getDamageTakenMultiplier() - baseMultiplier) < 1e-9
                        && Math.abs(blade.getHealEnhancePercent() - baseHeal) < 1e-9
                        && Math.abs(blade.getEnhance()) < 1e-9
                        && Math.abs(blade.getPenetration() - basePenetration) < 1e-9
                        && Math.abs(ally.getEnhance() - allyEnhanceBefore) < 1e-9);

        blade.setEidolon(2);
        check("E2：命座 2 时充能上限 9 → 7 —— 实际 " + blade.getChargeMax(), blade.getChargeMax() == 7);
        blade.setEidolon(0);
        check("E2：撤掉命座后充能上限回到 9 —— 实际 " + blade.getChargeMax(), blade.getChargeMax() == 9);
        blade.setCharge(0);
    }

    /**
     * <b>「因果尽偿」</b>（充能满了之后的兑现，2026-10-24）：结界期间、血量 &gt; 1 时，
     * 充能满 → <b>消耗全部充能 + 回复 27 能量 + 额外施放一次【刃下，归葬】</b>。
     * <p>
     * 修之前的表现（用户日志）：充能涨到 9/9 之后就卡住，只在每次我方出手后打一行"充能已满" ——
     * 等于这个资源没有出口。现在满了就兑现。
     */
    private static void testMortalBladeChargePayoff() {
        section("千冶·刃的【因果尽偿】：充能满 → 消耗 9 点 + 回能 + 额外一次战技");

        cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade blade =
                new cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade(125);
        LivingThing enemy = entityTemplateOf("game_official_content:commonInsect").copy();
        enemy.setHpMax(9_000_000L);
        enemy.setHp(enemy.getHpMax());
        PlayerOne ally = new PlayerOne(125);
        Fight fight = new Fight(new ArrayList<>(List.of(enemy)), new ArrayList<>(),
                new ArrayList<>(List.of(blade, ally)));

        blade.whenFightStart(fight);
        // 追打取的是"技能表里那份带配置的【刃下，归葬】"，所以必须真的开结界（换表）；
        // 只 setInfiniteFury(true) 的话表里还是普通形态那两条，追打会被静默跳过
        blade.beginInfiniteFury(fight);
        blade.setHp(blade.getHpMax());
        // 追打现在排进"当前回合的收尾队列"（不在别人技能执行中插入），所以这里造一个当前回合
        TurnEntry payoffTurn = new TurnEntry(ally, java.math.BigDecimal.ONE, java.math.BigDecimal.ZERO);
        cn.gfhnv.game.eventListener.FightTurnPastListener.setPresentTurn(payoffTurn);
        blade.setCharge(blade.getChargeMax() - 1);
        blade.setEnergy(0);
        long enemyHpBefore = enemy.getHp();
        EventBus.post(new SelectTargetEvent(ally, new ArrayList<>(List.of(enemy)), fight));
        check("因果尽偿：够阈值就排进收尾队列（充能还没扣）—— 充能 " + blade.getCharge()
                        + "、队列 " + payoffTurn.getLastExecuteList().size() + " 条",
                blade.getCharge() == blade.getChargeMax()
                        && payoffTurn.getLastExecuteList().size() == 1);
        check("因果尽偿：追打**还没打**（要等这一回合结算完）—— 能量 " + blade.getEnergy()
                        + "、血量 " + enemyHpBefore + " → " + enemy.getHp(),
                blade.getEnergy() == 0L && enemy.getHp() == enemyHpBefore);
        // 模拟回合收尾：跑队列
        for (cn.gfhnv.game.interfaces.ISpecialAction action : new ArrayList<>(payoffTurn.getLastExecuteList())) {
            action.execute(fight, ally);
        }
        cn.gfhnv.game.eventListener.FightTurnPastListener.setPresentTurn(null);
        check("因果尽偿：扣掉一个阈值（9 → 0）—— 实际 " + blade.getCharge(), blade.getCharge() == 0);
        // 27 是【因果尽偿】的回复；那一次追打的战技自己又回 30（Energy gain）
        check("因果尽偿：回复 27 点能量（加上追打战技自己的 30）—— 实际 " + blade.getEnergy(),
                blade.getEnergy() == 27L
                        + cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.BladeUnderBurial.ENERGY_GAIN);
        check("因果尽偿：额外放了一次【刃下，归葬】（敌人掉血 + 自己扣 10% 生命上限）—— 敌人 "
                        + enemyHpBefore + " → " + enemy.getHp() + "，自己 " + blade.getHp(),
                enemy.getHp() < enemyHpBefore && blade.getHp() < blade.getHpMax());

        // ① 不在结界里就不兑现：只加充能，不消耗
        blade.endInfiniteFury();
        blade.setCharge(blade.getChargeMax() - 1);
        blade.setEnergy(0);
        EventBus.post(new SelectTargetEvent(ally, new ArrayList<>(List.of(enemy)), fight));
        check("因果尽偿：不在结界里时不兑现（充能停在满值、能量不回）—— 实际 充能 "
                        + blade.getCharge() + "、能量 " + blade.getEnergy(),
                blade.getCharge() == blade.getChargeMax() && blade.getEnergy() == 0);

        // ② 追打取的必须是"技能表里那份带配置的"技能（和白厄反击同款：拿表内实例的副本，
        //    不是 new 出来的出厂值）—— 否则 SkillData.json 里改的倍率对追打不生效。
        //    验证手法：把表里那份的倍率改到很大（100 倍生命上限），追打那一下就会打出远高于默认的伤害。
        blade.beginInfiniteFury(fight);
        double tableMagnification = -1;
        for (Skill skill : blade.getController().getSkills()) {
            if (skill instanceof cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.BladeUnderBurial) {
                tableMagnification = skill.getHpMagnification();
                skill.setHpMagnification(100.0d);
            }
        }
        blade.setHp(blade.getHpMax());
        TurnEntry hugeTurn = new TurnEntry(ally, java.math.BigDecimal.ONE, java.math.BigDecimal.ZERO);
        cn.gfhnv.game.eventListener.FightTurnPastListener.setPresentTurn(hugeTurn);
        blade.setCharge(blade.getChargeMax() - 1);
        blade.setEnergy(0);
        long hpBeforeHuge = enemy.getHp();
        EventBus.post(new SelectTargetEvent(ally, new ArrayList<>(List.of(enemy)), fight));
        for (cn.gfhnv.game.interfaces.ISpecialAction action : new ArrayList<>(hugeTurn.getLastExecuteList())) {
            action.execute(fight, ally);
        }
        cn.gfhnv.game.eventListener.FightTurnPastListener.setPresentTurn(null);
        long hugeDamage = hpBeforeHuge - enemy.getHp();
        check("因果尽偿：追打用的是技能表里那份（把表里的倍率改成 100 → 追打打出超大伤害）—— 实际 "
                + hugeDamage, hugeDamage > 100_000L);
        for (Skill skill : blade.getController().getSkills()) {
            if (skill instanceof cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.BladeUnderBurial) {
                skill.setHpMagnification(tableMagnification);
            }
        }
        blade.endInfiniteFury();
        blade.setHp(blade.getHpMax());

        blade.setCharge(0);
        blade.whenFightEnds();
    }

    /**
     * <b>能量显示</b>：回合头下面那一行（{@code setShowSpecialMes}）要能看见能量与充能 ——
     * 用户的五行"能量"是法力，不是千冶·刃的资源；这两个资源不显示就等于没有。
     */
    private static void testMortalBladeEnergyDisplay() {
        section("千冶·刃的能量显示：回合头下面那行看得出能量 / 充能");

        cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade blade =
                new cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade(125);
        check("显示：千冶·刃装了 showSpecialMes（回合循环会调它）", blade.getShowSpecialMes() != null);
        blade.setEnergy(120);
        blade.setCharge(3);
        String line = captureShowSpecialMes(blade);
        check("显示：能量 120/160、充能 3（每 9 点触发一次）—— 实际「" + line + "」",
                line.contains("能量 120/160") && line.contains("充能 3")
                        && line.contains("每 9 点触发一次"));

        blade.setEnergy(blade.getEnergyMax());
        String full = captureShowSpecialMes(blade);
        check("显示：能量满时标出来（满）—— 实际「" + full + "」", full.contains("（满）"));

        blade.setEnergy(blade.getEnergyMax() + 40);
        String overflow = captureShowSpecialMes(blade);
        check("显示：溢出时标出溢出量（+40/80）—— 实际「" + overflow + "」", overflow.contains("溢出 40/80"));

        blade.setInfiniteFury(true);
        String fury = captureShowSpecialMes(blade);
        check("显示：结界期间标出【无量忿怒】—— 实际「" + fury + "」", fury.contains("无量忿怒"));
        blade.setInfiniteFury(false);
        blade.setEnergy(0);
        blade.setCharge(0);
    }

    private static void testMortalBladeCharge() {
        section("千冶·刃的充能：我方攻击敌方 → +1，敌方打我们 → 不加，战后注销");

        cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade blade =
                new cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade(125);
        LivingThing enemy = entityTemplateOf("game_official_content:commonInsect").copy();
        PlayerOne ally = new PlayerOne(125);
        Fight fight = new Fight(new ArrayList<>(List.of(enemy)), new ArrayList<>(),
                new ArrayList<>(List.of(blade, ally)));

        int before = EventBus.handlerCount(SelectTargetEvent.class);
        blade.whenFightStart(fight);
        check("充能：开战时注册了自己的监听器（+1）—— 处理器 " + before + " → "
                        + EventBus.handlerCount(SelectTargetEvent.class),
                EventBus.handlerCount(SelectTargetEvent.class) == before + 1);

        blade.setCharge(0);
        EventBus.post(new SelectTargetEvent(ally, new ArrayList<>(List.of(enemy)), fight));
        check("充能：我方（队友）攻击敌方 → 千冶·刃 +1 —— 实际 " + blade.getCharge(),
                blade.getCharge() == 1);

        EventBus.post(new SelectTargetEvent(enemy, new ArrayList<>(List.of(blade)), fight));
        check("充能：敌方打我们 → 不加（只认我方出手）—— 实际 " + blade.getCharge(),
                blade.getCharge() == 1);

        blade.whenFightEnds();
        check("充能：战斗结束注销监听器（处理器回到 " + before + "）—— 实际 "
                        + EventBus.handlerCount(SelectTargetEvent.class),
                EventBus.handlerCount(SelectTargetEvent.class) == before);
        blade.setCharge(0);

        // 顺带验一下"监听器自己不重复加满之后还加"：加到上限就停住
        blade.whenFightStart(fight);
        blade.setCharge(blade.getChargeMax() - 1);
        EventBus.post(new SelectTargetEvent(ally, new ArrayList<>(List.of(enemy)), fight));
        EventBus.post(new SelectTargetEvent(ally, new ArrayList<>(List.of(enemy)), fight));
        check("充能：够一次触发就不再加（队列里有兑现，这里只验没被夹掉）—— 实际 "
                        + blade.getCharge() + "，阈值 " + blade.getChargeMax(),
                blade.getCharge() >= blade.getChargeMax());
        blade.whenFightEnds();
    }

    /**
     * <b>千冶·刃的两个单攻</b>（P0 第 2 步，2026-10-24）：「残锋，掠尽」50% 生命上限 / 「淬锋，断魄」100%，
     * 命中后都使目标嘲讽 1 回合。
     * <p>
     * 设计稿见 `TODO.md`，施工单见 `project_analyses/MORTENAX-BLADE-2026-10.md`。
     * 这里验四件事：① 技能表出厂就是两条（普攻 + 终结技，**强化普攻不在普通形态里**）；② 倍率走的是**生命上限**口径（`hpMagnification`，
     * 攻击/防御倍率全 0）；③ **行为对照**：同一只木桩上 100% 的伤害约等于 50% 的两倍
     * （证明倍率真的进了伤害计算，而不只是"读到了"）；④ 命中挂嘲讽、`origin` 指向千冶·刃。
     */
    private static void testMortalBladeSkills() {
        section("千冶·刃的两条单攻：生命上限口径 + 命中嘲讽");

        cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade blade =
                new cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade(125);
        List<Skill> kit = blade.getController().getSkills();
        check("技能表出厂是两条（普攻「残锋，掠尽」+ 终结技「骸骨当炉，血肉即薪」）—— 实际 "
                        + kit.size() + " 条：" + kit.get(0).getName() + " / " + kit.get(1).getName(),
                kit.size() == 2
                        && kit.get(0).getName().contains("残锋")
                        && kit.get(1).getName().contains("骸骨当炉"));

        Skill common = kit.get(0);
        // 「淬锋，断魄」不在普通形态里 —— 它是【无量忿怒】期间的**强化普攻**，直接造一个来验倍率
        Skill skill = new cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.MortalBladeSkill();
        check("倍率走生命上限口径（hpMagnification 0.5 / 1.0，atk 与 def 倍率全 0）—— 实际 "
                        + common.getHpMagnification() + "/" + common.getAtkMagnification()
                        + " 与 " + skill.getHpMagnification() + "/" + skill.getAtkMagnification(),
                Math.abs(common.getHpMagnification() - 0.5d) < 1e-9
                        && Math.abs(skill.getHpMagnification() - 1.0d) < 1e-9
                        && common.getAtkMagnification() == 0 && skill.getAtkMagnification() == 0);

        check("强化普攻「淬锋，断魄」：普通形态放不出来（它只在【无量忿怒】的技能表里）—— 实际 "
                        + skill.canUse(null, blade, null),
                !skill.canUse(null, blade) && !skill.canUse(null, blade, null));
        blade.setInfiniteFury(true);
        check("强化普攻「淬锋，断魄」：结界里就能放（倍率 100% 生命上限，atk 倍率 0）—— 实际 "
                        + skill.canUse(null, blade, null) + "、" + skill.getHpMagnification(),
                skill.canUse(null, blade, null)
                        && Math.abs(skill.getHpMagnification() - 1.0d) < 1e-9
                        && skill.getAtkMagnification() == 0);
        blade.setInfiniteFury(false);

        // 木桩：把血上限调大，免得两刀都"刚好打死"看不出倍率差
        LivingThing dummyA = entityTemplateOf("game_official_content:commonInsect").copy();
        LivingThing dummyB = entityTemplateOf("game_official_content:commonInsect").copy();
        dummyA.setHpMax(9_000_000L);
        dummyA.setHp(dummyA.getHpMax());
        dummyB.setHpMax(9_000_000L);
        dummyB.setHp(dummyB.getHpMax());
        Fight fight = new Fight(new ArrayList<>(List.of(dummyA, dummyB)), new ArrayList<>(),
                new ArrayList<>(List.of(blade)));

        long hpA0 = dummyA.getHp();
        long hpB0 = dummyB.getHp();
        common.use(fight, blade, List.of(dummyA));
        // 淬锋是【无量忿怒】的强化普攻（有门槛），这里临时开一下结界再放
        blade.setInfiniteFury(true);
        skill.use(fight, blade, List.of(dummyB));
        blade.setInfiniteFury(false);
        long dmgA = hpA0 - dummyA.getHp();
        long dmgB = hpB0 - dummyB.getHp();
        check("伤害比 ≈ 1:2（同一只木桩上 50% vs 100% 生命上限）—— 实际 " + dmgA + " / " + dmgB,
                dmgA > 0 && Math.abs(dmgB - 2 * dmgA) <= Math.max(2, dmgA / 20));

        List<String> missingTaunt = new ArrayList<>();
        for (LivingThing dummy : List.of(dummyA, dummyB)) {
            boolean found = dummy.getEntityEffectList().stream().anyMatch(effect ->
                    effect instanceof cn.gfhnv.game.officialStuff.customEffect.universalEffects.Taunt
                            && blade.getUUID().equals(effect.getOrigin()));
            if (!found) {
                missingTaunt.add(dummy.getName());
            }
        }
        check("嘲讽：被打的目标身上有 Taunt、而且 origin 指向千冶·刃 —— 缺 " + missingTaunt,
                missingTaunt.isEmpty());

        // copy() 带着配置走（配置打在模板上，副本靠 copy() 白送）
        Skill patched = skill.copy();
        patched.setHpMagnification(0.72d);
        check("copy() 带着配置走：副本改了倍率不影响模板 —— 副本 " + patched.getHpMagnification()
                        + "、模板 " + skill.getHpMagnification(),
                Math.abs(patched.getHpMagnification() - 0.72d) < 1e-9
                        && Math.abs(skill.getHpMagnification() - 1.0d) < 1e-9);
    }

    /**
     * <b>千冶·刃的资源层</b>（P0 第 1 步，2026-10-24）：能量（160 上限 + 80 溢出）与充能（9 点）。
     * <p>
     * 设计稿见 `TODO.md`，施工单见 `project_analyses/MORTENAX-BLADE-2026-10.md`。
     * 这里只验"资源本身"：夹取 / 溢出保留 / 满能解负面 / 开战保底 75% / 战斗结束清零 / 速度 107 /
     * {@code classState} 能配。技能与结界还没写。
     */
    private static void testMortenaxBladeResources() {
        section("千冶·刃：能量（160+80 溢出）与充能（9 点）");

        cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade blade =
                new cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade(125);
        check("属性：速度 107（设计稿）—— 实际 " + blade.getSpeed(), blade.getSpeed() == 107L);
        check("资源出厂：能量 0/160、溢出上限 80、充能 0/9 —— 实际 "
                        + blade.getEnergy() + "/" + blade.getEnergyMax() + "、溢出 "
                        + blade.getEnergyOverflowMax() + "、充能 " + blade.getCharge() + "/" + blade.getChargeMax(),
                blade.getEnergy() == 0 && blade.getEnergyMax() == 160
                        && blade.getEnergyOverflowMax() == 80
                        && blade.getCharge() == 0 && blade.getChargeMax() == 9);

        blade.setEnergy(999);
        check("能量：夹在 0…160+80（写 999 → 240）—— 实际 " + blade.getEnergy(), blade.getEnergy() == 240);
        blade.setEnergy(200);
        blade.spendUltimateEnergy();
        check("能量：终结技付掉 160、溢出留下（200 → 40）—— 实际 " + blade.getEnergy(), blade.getEnergy() == 40);

        blade.setEnergy(0);
        blade.gainEnergy(150);
        check("能量：不到上限时不触发解负面（0 + 150 = 150 < 160）—— 实际 " + blade.getEnergy(),
                blade.getEnergy() == 150);

        blade.getEntityEffectList().add(new cn.gfhnv.game.officialStuff.customEffect.universalEffects.Taunt(1));
        blade.gainEnergy(10);
        check("能量：攒到上限时解除自身所有负面效果（150 + 10 → 160，嘲讽被清）—— 剩余负面数 "
                        + blade.getEntityEffectList().stream().filter(Effect::isNegative).count(),
                blade.getEnergy() == 160 && blade.getEntityEffectList().isEmpty());

        blade.setEnergy(0);
        blade.ensureEnergyFloor();
        check("能量：开战保底 75%（0 → 120），已经够高时不动（130 还是 130）—— 实际 "
                + blade.getEnergy(), blade.getEnergy() == 120);
        blade.setEnergy(130);
        blade.ensureEnergyFloor();
        check("能量：保底不会把已有的量压下去", blade.getEnergy() == 130);

        blade.setCharge(99);
        boolean full = blade.gainCharge(1);
        check("充能：**没有上限**（写 99 → 100，不夹）+ 够阈值回报 true —— 实际 "
                        + blade.getCharge() + "，阈值 " + blade.getChargeMax(),
                blade.getCharge() == 100 && full);
        blade.setChargeMax(7);
        check("充能：E2 把**阈值**降到 7（充能点数本身不动）—— 实际 " + blade.getCharge()
                        + "，阈值 " + blade.getChargeMax(),
                blade.getCharge() == 100 && blade.getChargeMax() == 7);

        blade.setChargeMax(9);
        blade.whenFightStart(null);
        blade.setCharge(5);
        blade.whenFightEnds();
        check("生命周期：战斗结束资源清零、上限不动 —— 实际能量 " + blade.getEnergy()
                        + "、充能 " + blade.getCharge() + "、上限 " + blade.getEnergyMax() + "/" + blade.getChargeMax(),
                blade.getEnergy() == 0 && blade.getCharge() == 0
                        && blade.getEnergyMax() == 160 && blade.getChargeMax() == 9);

        // classState 要在**模板**上验（配置永远打在模板上，副本靠 copy() 白送）
        cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade template =
                (cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade)
                        entityTemplateOf("game_official_content:mortenax_blade");
        long oldEnergyMax = template.getEnergyMax();
        int oldChargeMax = template.getChargeMax();
        cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.Report report =
                cn.gfhnv.game.system.configLoadingSystem.EntityDataPatcher.applyJson(
                        "{\"entities\":{\"game_official_content:mortenax_blade\":{\"classState\":"
                                + "{\"energyMax\":200,\"chargeMax\":7,\"energy\":99}}}}", "自测-千冶刃资源");
        check("classState：能量 / 上限 / 充能上限 可配（写 99 / 200 / 7）—— 应用 " + report.appliedEntries()
                        + "，跳过 " + report.skippedEntries(),
                report.isClean() && template.getEnergyMax() == 200
                        && template.getChargeMax() == 7 && template.getEnergy() == 99);
        // 还原成出厂值（模板全局共享，别污染后面的用例）
        template.setEnergyMax(oldEnergyMax);
        template.setChargeMax(oldChargeMax);
        template.setEnergy(0);
        template.setCharge(0);
    }

    /**
     * <b>结界形态的技能表必须"吃配置"</b>（2026-10-25）：那三条技能是
     * {@code World.prototypeCopyOf(...)} 出来的副本，不是 {@code new} 出来的出厂值 ——
     * 所以 {@code SkillData.json} 里改倍率，战斗里那一份要跟着变。
     * <p>
     * 验法：把原型上的倍率改成一个显眼的值 → 开结界 → 表里那份必须是同一个值 → 还原。
     */
    private static void testInfiniteFurySkillsTakeConfig() {
        section("结界技能表吃配置（原型副本，不是 new）");

        Skill prototype = cn.gfhnv.game.system.configLoadingSystem.SkillDataPatcher.find(
                "game_official_content:mortenax_blade#刃下，归葬");
        check("原型表里能找到【刃下，归葬】", prototype != null);
        if (prototype == null) {
            return;
        }
        double original = prototype.getHpMagnification();
        prototype.setHpMagnification(0.5d);
        try {
            cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade blade =
                    new cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade(125);
            blade.beginInfiniteFury(new Fight(new ArrayList<>(), new ArrayList<>(),
                    new ArrayList<>(List.of(blade))));
            double inTable = -1;
            for (Skill skill : blade.getController().getSkills()) {
                if (skill instanceof cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.BladeUnderBurial) {
                    inTable = skill.getHpMagnification();
                }
            }
            check("把原型的倍率改成 0.5 → 结界里那份也是 0.5（证明是原型副本）—— 实际 " + inTable,
                    Math.abs(inTable - 0.5d) < 1e-9);
            blade.endInfiniteFury();
        } finally {
            prototype.setHpMagnification(original);
        }
    }

    /**
     * 跑本主题的全部用例（顺序 = 下面列的顺序）。
     */
    public static void runAll() throws Exception {
        testMortenaxBladeResources();
        testMortalBladeSkills();
        testInfiniteFuryZone();
        testMortalBladeAiPriority();
        testMortalBladeCharge();
        testMortalBladeEnergyDisplay();
        testMortalBladeChargePayoff();
        testMortalBladePassives();
        testMortalBladeEnergyGain();
        testMortalBladeChargeLoop();
        testInfiniteFurySkillsTakeConfig();
    }

    private MortalBladeTests() {
    }
}
