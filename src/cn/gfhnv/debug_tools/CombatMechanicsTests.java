package cn.gfhnv.debug_tools;

import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.damage.Damage;
import cn.gfhnv.game.damage.DamageType;
import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.effect.EnergyOverflowEffect;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.DamageEvent;
import cn.gfhnv.game.event.EntityDeathEvent;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.eventListener.FightTurnPastListener;
import cn.gfhnv.game.officialStuff.customEffect.universalEffects.CriticalDamageEnhanceEffect;
import cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver;
import cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade;
import cn.gfhnv.game.officialStuff.customEntity.players.Phainon;
import cn.gfhnv.game.officialStuff.customEntity.players.PlayerOne;
import cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.BladeUnderBurial;
import cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.CalamitySoulscorchEdict;
import cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.Counterattack;
import cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.FoundationStardeathVerdict;
import cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills.UltimateAttack;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.fight.TurnEntry;
import cn.gfhnv.game.system.fight.TurnManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static cn.gfhnv.debug_tools.TestKit.*;
import static cn.gfhnv.debug_tools.TestProbes.ProbeSkill;

/**
 * 自测：<b>2026-11-21 第 82 批的内容机制</b>。
 * <ul>
 *     <li>白厄【灾厄·弑魂焚诏的反击】：主刀全体 + 额外 4 段随机单体、战技 + 追加攻击、每层 +20% 倍率；</li>
 *     <li>千冶·刃「刃下归葬」的伤害类型 = 战技 + 追加攻击；</li>
 *     <li>千冶·刃二魂：充能上限 7、我方追加攻击 +75%、我方终结技伤害视为追加攻击。</li>
 * </ul>
 */
final class CombatMechanicsTests {

    private CombatMechanicsTests() {
    }

    private static Fight fightOf(List<LivingThing> fighters, List<LivingThing> enemies) {
        return new Fight(new ArrayList<>(enemies), new ArrayList<>(), new ArrayList<>(fighters));
    }

    private static void testCounterattackMechanics() {
        section("白厄反击：全体主刀 + 4 段额外 + 战技/追加攻击类型");
        try {
            Phainon phainon = new Phainon(125);
            FlameReaver a = new FlameReaver(125L);
            FlameReaver b = new FlameReaver(125L);
            Fight fight = fightOf(List.of(phainon), List.of(a, b));
            phainon.setSoulscorch(2);
            DamageProbe probe = new DamageProbe();
            EventBus.register(probe);
            try {
                new Counterattack().comeToEffect(fight, phainon,
                        new ArrayList<>(List.of(a, b)));
                check("① 命中次数 = 全体 2 刀 + 额外 4 段 —— 实际 " + probe.damages.size(),
                        probe.damages.size() == 6);
                check("② 每一刀都是「战技 + 追加攻击」", probe.allHaveBothTypes());
                check("③ 反击结算后【弑魂之炽】清零", phainon.getSoulscorch() == 0);
            } finally {
                EventBus.unregister(probe);
            }
        } catch (Exception e) {
            fail("白厄反击测试抛出异常：" + e);
        }
    }

    private static void testCounterattackStackScaling() {
        section("白厄反击：每层【弑魂之炽】+20% 倍率");
        try {
            double withOne = firstCounterRaw(1);
            double withThree = firstCounterRaw(3);
            check("① 1 层 vs 3 层的主刀倍率比 = (1+0.6)/(1+0.2) —— 实际 "
                            + withOne + " / " + withThree,
                    Math.abs(withThree / withOne - (1 + 0.2 * 3) / (1 + 0.2 * 1)) < 1e-6);
        } catch (Exception e) {
            fail("反击层数倍率测试抛出异常：" + e);
        }
    }

    private static double firstCounterRaw(int soulscorch) {
        Phainon phainon = new Phainon(125);
        FlameReaver enemy = new FlameReaver(125L);
        Fight fight = fightOf(List.of(phainon), List.of(enemy));
        phainon.setSoulscorch(soulscorch);
        DamageProbe probe = new DamageProbe();
        EventBus.register(probe);
        try {
            new Counterattack().comeToEffect(fight, phainon, new ArrayList<>(List.of(enemy)));
        } finally {
            EventBus.unregister(probe);
        }
        return probe.damages.get(0).getRawDamageAmount();
    }

    private static void testBladeUnderBurialDamageTypes() {
        section("千冶·刃「刃下归葬」：战技 + 追加攻击");
        try {
            MortenaxBlade blade = new MortenaxBlade(125);
            FlameReaver a = new FlameReaver(125L);
            FlameReaver b = new FlameReaver(125L);
            Fight fight = fightOf(List.of(blade), List.of(a, b));
            DamageProbe probe = new DamageProbe();
            EventBus.register(probe);
            try {
                new BladeUnderBurial().comeToEffect(fight, blade, new ArrayList<>(List.of(a, b)));
                check("① 每一段都是「战技 + 追加攻击」—— 实际 " + probe.damages.size() + " 段",
                        probe.allHaveBothTypes());
            } finally {
                EventBus.unregister(probe);
            }
        } catch (Exception e) {
            fail("刃下归葬类型测试抛出异常：" + e);
        }
    }

    private static void testEidolonTwo() {
        section("千冶·刃二魂：充能 7 / 追加攻击 +75% / 终结技视为追加攻击");
        try {
            MortenaxBlade blade = new MortenaxBlade(125);
            blade.setEidolon(2);
            check("① 二魂把充能上限降到 7 —— 实际 " + blade.getChargeMax(), blade.getChargeMax() == 7);
            blade.setEidolon(0);
            check("② 非二魂仍是 9 —— 实际 " + blade.getChargeMax(), blade.getChargeMax() == 9);

            MortenaxBlade e2Blade = new MortenaxBlade(125);
            e2Blade.setEidolon(2);
            PlayerOne ally = new PlayerOne(125);
            FlameReaver enemy = new FlameReaver(125L);
            Fight fight = fightOf(List.of(e2Blade, ally), List.of(enemy));
            // 生产里由 FightStartEventListener 统一设置；这里直接构造 Fight，要自己补上
            e2Blade.setParticipateFight(fight);
            ally.setParticipateFight(fight);

            check("③ 己方追加攻击光环 = 75% —— 实际 " + ally.getTeamAdditionAttackDamageBonus(),
                    Math.abs(ally.getTeamAdditionAttackDamageBonus() - 0.75) < 1e-9);

            Skill probeSkill = new ProbeSkill("类型探针", new ArrayList<>(), 1, 1.0);
            long addition = new Damage(ally, enemy, probeSkill, DamageType.AdditionAttack)
                    .getCalculateDamageResult();
            long special = new Damage(ally, enemy, probeSkill, DamageType.SpecialSkill)
                    .getCalculateDamageResult();
            check("④ 追加攻击伤害 = 普通战技的 1.75 倍 —— 实际 " + special + " / " + addition,
                    Math.abs((double) addition / special - 1.75) < 0.01);

            // 2026-11-22：增伤一律连加。团队光环 +75% 与自身追加增伤 +80% 应得 2.55 倍，
            // 不是 1.75 × 1.8 = 3.15。
            ally.addDamageTypeEnhance(DamageType.AdditionAttack, 0.8, "自测-追加增伤");
            long stacked = new Damage(ally, enemy, probeSkill, DamageType.AdditionAttack)
                    .getCalculateDamageResult();
            check("④-b 团队光环与自身增伤连加（+75% + 80% = 2.55 倍）—— 实际 "
                            + special + " / " + stacked,
                    Math.abs((double) stacked / special - 2.55) < 0.01);

            DamageProbe probe = new DamageProbe();
            EventBus.register(probe);
            try {
                ally.makeDamage(enemy, probeSkill, DamageType.UltimateAttack);
                check("⑤ 我方终结技伤害被视为追加攻击 —— 实际类型 "
                                + probe.damages.get(0).getDamageTypes(),
                        probe.damages.get(0).hasDamageType(DamageType.UltimateAttack)
                                && probe.damages.get(0).hasDamageType(DamageType.AdditionAttack));
                probe.damages.clear();
                e2Blade.setEidolon(0);
                ally.makeDamage(enemy, probeSkill, DamageType.UltimateAttack);
                check("⑥ 没有二魂时只算终结技，不额外算追加攻击",
                        !probe.damages.get(0).hasDamageType(DamageType.AdditionAttack));
            } finally {
                EventBus.unregister(probe);
            }
        } catch (Exception e) {
            fail("千冶·刃二魂测试抛出异常：" + e);
        }
    }

    /**
     * HSRActor 迁移 + 白厄星魂基础：千冶用 HSRActor 的能量/星魂；白厄继承星魂但不使用能量。
     */
    private static void testHSRActorAndPhainonEidolons() {
        section("HSRActor 与白厄星魂：基础接线");
        try {
            MortenaxBlade blade = new MortenaxBlade(125);
            blade.setEidolon(2);
            check("① 千冶·刃 star soul 2 / 充能上限 7 —— 实际 " + blade.getEidolon()
                            + " / " + blade.getChargeMax(),
                    blade.getEidolon() == 2 && blade.getChargeMax() == 7);
            blade.setEnergy(9999);
            check("② HSRActor 能量夹在 160+80=240 —— 实际 " + blade.getEnergy(),
                    blade.getEnergy() == 240);

            Phainon phainon = new Phainon(125);
            check("③ 白厄默认星魂 0、不用 HSRActor 能量（能量仍 0）",
                    phainon.getEidolon() == 0 && phainon.getEnergy() == 0);
            phainon.setEnergy(999);
            phainon.gainEnergyOnDamaged(false);
            phainon.gainEnergyOnKill();
            phainon.gainEnergyOnCommonAttack();
            check("④ 白厄不吃普通能量：setEnergy / 受击 / 击杀 / 普攻都保持 0，也没有溢出效果 —— 实际 "
                            + phainon.getEnergy(),
                    phainon.getEnergy() == 0 && findOverflowEffect(phainon) == null);
            check("⑤ 0 魂时额外回合速度继承 100% —— 实际 "
                            + phainon.getExtraTurnSpeedRatio(),
                    Math.abs(phainon.getExtraTurnSpeedRatio() - 1.0) < 1e-9);
            phainon.setEidolon(1);
            check("⑥ 升到 1 魂后速度继承 200% —— 实际 " + phainon.getExtraTurnSpeedRatio(),
                    Math.abs(phainon.getExtraTurnSpeedRatio() - 2.0) < 1e-9);
            phainon.setEidolon(2);
            check("⑦ 升到 2 魂（穿透 +20% 已挂）——实际星魂 " + phainon.getEidolon(),
                    phainon.getEidolon() == 2);
        } catch (Exception e) {
            fail("HSRActor/星魂基础测试抛出异常：" + e);
        }
    }

    /**
     * 第 85 批：千冶·刃的溢出能量改由【能量溢出】效果记录。
     */
    private static void testEnergyOverflowEffect() {
        section("千冶·刃：溢出能量由效果记录");
        try {
            MortenaxBlade blade = new MortenaxBlade(125);
            blade.setEnergy(200);
            EnergyOverflowEffect effect = findOverflowEffect(blade);
            check("① 200 能量 = 基础 160 + 效果 40（总/溢出/等级 " + blade.getEnergy() + "/"
                            + blade.getEnergyOverflow() + "/" + (effect == null ? "无" : effect.getLevel()) + "）",
                    blade.getEnergy() == 200 && blade.getEnergyOverflow() == 40
                            && effect != null && effect.getOverflow() == 40 && effect.getLevel() == 40);

            blade.setEnergy(100);
            check("② 降回上限以内：效果被摘掉、总能量 100",
                    blade.getEnergy() == 100 && blade.getEnergyOverflow() == 0
                            && findOverflowEffect(blade) == null);

            blade.setEnergy(9999);
            check("③ 溢出上限仍是 80（总能量 240）—— 实际 " + blade.getEnergy() + "/"
                            + blade.getEnergyOverflow(),
                    blade.getEnergy() == 240 && blade.getEnergyOverflow() == 80);

            blade.setEnergy(200);
            blade.spendUltimateEnergy();
            check("④ 终结技付 160：40 回到基础值、效果摘掉 —— 实际 " + blade.getEnergy()
                            + "（溢出 " + blade.getEnergyOverflow() + "）",
                    blade.getEnergy() == 40 && blade.getEnergyOverflow() == 0
                            && findOverflowEffect(blade) == null);

            blade.setEnergy(200);
            MortenaxBlade copy = (MortenaxBlade) blade.copy();
            copy.setEnergy(220);
            check("⑤ 复制体有自己的溢出效果，不串改原体 —— 原体 " + blade.getEnergy()
                            + "、副本 " + copy.getEnergy(),
                    blade.getEnergy() == 200 && copy.getEnergy() == 220);
        } catch (Exception e) {
            fail("溢出能量效果测试抛出异常：" + e);
        }
    }

    private static EnergyOverflowEffect findOverflowEffect(LivingThing thing) {
        for (Effect effect : thing.getEntityEffectList()) {
            if (effect instanceof EnergyOverflowEffect overflow) {
                return overflow;
            }
        }
        return null;
    }

    /**
     * 第 85 批：白厄 E1 的击杀计数累积与终结技暴伤挂载（原先只有"代码已接"，没有断言）。
     */
    private static void testPhainonE1Assertions() {
        section("白厄 E1：击杀计数 / 终结技暴伤");
        Phainon phainon = new Phainon(125);
        try {
            phainon.setEidolon(1);
            FlameReaver enemy = new FlameReaver(125L);
            Fight fight = fightOf(List.of(phainon), List.of(enemy));
            phainon.setParticipateFight(fight);
            phainon.whenFightStart(fight);
            check("① 1 魂基础速度继承 200% —— 实际 " + phainon.getExtraTurnSpeedRatio(),
                    Math.abs(phainon.getExtraTurnSpeedRatio() - 2.0) < 1e-9);
            EventBus.post(new EntityDeathEvent(fight, enemy));
            check("② 每消灭 1 个敌方目标 +1.5% —— 实际 " + phainon.getExtraTurnSpeedRatio(),
                    Math.abs(phainon.getExtraTurnSpeedRatio() - 2.015) < 1e-9);
        } catch (Exception e) {
            fail("白厄 E1 击杀计数测试抛出异常：" + e);
        } finally {
            phainon.whenFightEnds();
        }

        Phainon caster = new Phainon(125);
        TurnEntry present = null;
        try {
            caster.setEidolon(1);
            caster.setCoreflame(15);
            FlameReaver enemy = new FlameReaver(125L);
            Fight fight = fightOf(List.of(caster), List.of(enemy));
            caster.setParticipateFight(fight);
            TurnManager.getTurns().clear();
            present = new TurnEntry(caster, BigDecimal.ZERO, BigDecimal.ZERO);
            FightTurnPastListener.setPresentTurn(present);
            UltimateAttack ultimate = null;
            for (Skill skill : caster.getController().getSkills()) {
                if (skill instanceof UltimateAttack candidate) {
                    ultimate = candidate;
                    break;
                }
            }
            check("③ 控制器里找得到大招", ultimate != null);
            if (ultimate != null) {
                ultimate.comeToEffect(fight, caster);
                CriticalDamageEnhanceEffect crit = null;
                for (Effect effect : caster.getEntityEffectList()) {
                    if (effect instanceof CriticalDamageEnhanceEffect candidate) {
                        crit = candidate;
                    }
                }
                check("④ 1 魂施放终结技挂上暴伤 +50% / 3 回合（0.5 + 0.5 = " + caster.getCriticalDamage()
                                + "）",
                        crit != null && crit.getLastTime() == 3
                                && Math.abs(caster.getCriticalDamage() - 1.0) < 1e-9);
            }
        } catch (Exception e) {
            fail("白厄 E1 终结技暴伤测试抛出异常：" + e);
        } finally {
            caster.whenFightEnds();
            TurnManager.getTurns().clear();
            FightTurnPastListener.setPresentTurn(null);
        }
    }

    /**
     * 第 85 批：白厄 E2 的【支柱·死星天裁】追加行动条目（原先未单独断言）。
     */
    private static void testPhainonE2Assertion() {
        section("白厄 E2：死星天裁满层追加行动条目");
        Phainon phainon = new Phainon(125);
        try {
            phainon.setEidolon(2);
            phainon.setCriticalRate(0);
            phainon.setScourge(4);
            FlameReaver enemy = new FlameReaver(125L);
            Fight fight = fightOf(List.of(phainon), List.of(enemy));
            TurnEntry present = new TurnEntry(phainon, BigDecimal.ZERO, BigDecimal.ZERO);
            FightTurnPastListener.setPresentTurn(present);
            new FoundationStardeathVerdict().comeToEffect(fight, phainon, new ArrayList<>(List.of(enemy)));
            check("① 满 4 层消耗后追加 1 条行动条目 —— 实际 " + present.getActQueue().size(),
                    present.getActQueue().size() == 1);
            if (present.getActQueue().size() == 1) {
                var act = present.getActQueue().getFirst();
                check("② 条目优先级 PRIORITY_EIDOLON_EXTRA（800）、行动者是白厄（不额外结算效果）—— 实际 "
                                + act.priority() + " / " + (act.actor() == phainon),
                        act.priority() == cn.gfhnv.game.system.fight.ActEntry.PRIORITY_EIDOLON_EXTRA
                                && act.actor() == phainon);
            }
        } catch (Exception e) {
            fail("白厄 E2 追加行动测试抛出异常：" + e);
        } finally {
            TurnManager.getTurns().clear();
            FightTurnPastListener.setPresentTurn(null);
        }
    }

    /**
     * 第 85 批：白厄星魂 4 —— 施放【灾厄·弑魂焚诏】额外获得 4 层【弑魂之炽】。
     */
    private static void testPhainonE4() {
        section("白厄 E4：灾厄·弑魂焚诏额外 4 层【弑魂之炽】");
        try {
            Phainon e3 = new Phainon(125);
            e3.setEidolon(3);
            Fight fight3 = fightOf(List.of(e3), List.of(new FlameReaver(125L)));
            new CalamitySoulscorchEdict().comeToEffect(fight3, e3,
                    new ArrayList<>(fight3.getEnemiesList()));
            check("① 3 魂：只拿基础 1 层 —— 实际 " + e3.getSoulscorch(), e3.getSoulscorch() == 1);

            Phainon e4 = new Phainon(125);
            e4.setEidolon(4);
            Fight fight4 = fightOf(List.of(e4), List.of(new FlameReaver(125L)));
            new CalamitySoulscorchEdict().comeToEffect(fight4, e4,
                    new ArrayList<>(fight4.getEnemiesList()));
            check("② 4 魂：基础 1 层 + 额外 4 层 = 5 层 —— 实际 " + e4.getSoulscorch(),
                    e4.getSoulscorch() == 5);
        } catch (Exception e) {
            fail("白厄 E4 测试抛出异常：" + e);
        } finally {
            TurnManager.getTurns().clear();
        }
    }

    /**
     * 第 85 批：白厄星魂 6 —— 火种无上限 / 开局 +6 / 死星天裁后 36% 真实伤害。
     */
    private static void testPhainonE6() {
        section("白厄 E6：火种无上限 / 开局 +6 / 36% 真实伤害");
        try {
            Phainon e5 = new Phainon(125);
            e5.setEidolon(5);
            Fight fight5 = fightOf(List.of(e5), List.of(new FlameReaver(125L)));
            e5.setParticipateFight(fight5);
            e5.whenFightStart(fight5);
            check("① 5 魂开局仍是 15（+1 被上限夹住）—— 实际 " + e5.getCoreflame(),
                    e5.getCoreflame() == 15);
            e5.whenFightEnds();

            Phainon e6 = new Phainon(125);
            e6.setEidolon(6);
            Fight fight6 = fightOf(List.of(e6), List.of(new FlameReaver(125L)));
            e6.setParticipateFight(fight6);
            e6.whenFightStart(fight6);
            check("② 6 魂开局 = 15 + 原有 1 + 星魂 6 = 22 —— 实际 " + e6.getCoreflame(),
                    e6.getCoreflame() == 22);
            e6.setCoreflame(999);
            check("③ 6 魂时 setCoreflame 不再夹上限 —— 999 → " + e6.getCoreflame(),
                    e6.getCoreflame() == 999);
            e6.whenFightEnds();

            Phainon caster = new Phainon(125);
            caster.setEidolon(6);
            caster.setCriticalRate(0);
            caster.setScourge(2);
            FlameReaver high = new FlameReaver(125L);
            high.setHpMax(1_000_000L);
            high.setHp(1_000_000L);
            FlameReaver low = new FlameReaver(125L);
            low.setHpMax(500_000L);
            low.setHp(500_000L);
            Fight fight = fightOf(List.of(caster), List.of(high, low));
            TurnEntry present = new TurnEntry(caster, BigDecimal.ZERO, BigDecimal.ZERO);
            FightTurnPastListener.setPresentTurn(present);
            DamageProbe probe = new DamageProbe();
            EventBus.register(probe);
            try {
                new FoundationStardeathVerdict().comeToEffect(fight, caster,
                        new ArrayList<>(List.of(high, low)));
            } finally {
                EventBus.unregister(probe);
            }
            long total = 0;
            Damage trueDamage = null;
            for (Damage damage : probe.damages) {
                if (damage.hasDamageType(DamageType.SystemDamage)) {
                    trueDamage = damage;
                } else if (damage.hasDamageType(DamageType.UltimateAttack)) {
                    total += damage.getAppliedDamage();
                }
            }
            check("④ 2 层【毁伤】也触发追加真实伤害（每次施放都触发）—— 总伤害 " + total,
                    total > 0 && trueDamage != null);
            if (trueDamage != null) {
                long expectedRaw = Math.round(total * FoundationStardeathVerdict.E6_TRUE_DAMAGE_RATIO);
                check("⑤ 追加伤害的原始值 = 总伤害 ×36%，真实元素、不暴击 —— 原始 "
                                + trueDamage.getRawDamageAmount() + " / 应为 " + expectedRaw,
                        Math.abs(trueDamage.getRawDamageAmount() - expectedRaw) < 1e-6
                                && trueDamage.isTrueDamage() && !trueDamage.isCritical());
                check("⑥ 走 UNIVERSAL 公式：吃防御（结算值 " + trueDamage.getCalculateDamageResult()
                                + " < 原始值），不是固定结果值",
                        trueDamage.getCalculateDamageResult() > 0
                                && trueDamage.getCalculateDamageResult() < expectedRaw);
                check("⑦ 目标是生命值最高的敌方 —— 实际 "
                                + trueDamage.getTargetEntity().getNameWithUuid() + "（应为 " + high.getNameWithUuid() + "）",
                        trueDamage.getTargetEntity() == high);
            }

            Phainon e5Caster = new Phainon(125);
            e5Caster.setEidolon(5);
            e5Caster.setCriticalRate(0);
            e5Caster.setScourge(2);
            FlameReaver dummy = new FlameReaver(125L);
            Fight fightNoE6 = fightOf(List.of(e5Caster), List.of(dummy));
            DamageProbe noE6Probe = new DamageProbe();
            EventBus.register(noE6Probe);
            try {
                new FoundationStardeathVerdict().comeToEffect(fightNoE6, e5Caster,
                        new ArrayList<>(List.of(dummy)));
            } finally {
                EventBus.unregister(noE6Probe);
            }
            boolean anyTrue = false;
            for (Damage damage : noE6Probe.damages) {
                if (damage.hasDamageType(DamageType.SystemDamage)) {
                    anyTrue = true;
                }
            }
            check("⑦ 5 魂没有追加真实伤害", !anyTrue);
        } catch (Exception e) {
            fail("白厄 E6 测试抛出异常：" + e);
        } finally {
            TurnManager.getTurns().clear();
            FightTurnPastListener.setPresentTurn(null);
        }
    }

    /**
     * 跑本主题的全部用例。
     */
    static void runAll() {
        testCounterattackMechanics();
        testCounterattackStackScaling();
        testBladeUnderBurialDamageTypes();
        testEidolonTwo();
        testHSRActorAndPhainonEidolons();
        testEnergyOverflowEffect();
        testPhainonE1Assertions();
        testPhainonE2Assertion();
        testPhainonE4();
        testPhainonE6();
    }

    /**
     * 伤害探针：记录每次 {@link DamageEvent}。
     */
    static final class DamageProbe {
        final List<Damage> damages = new ArrayList<>();

        @SubscribeEvent
        public void onDamage(DamageEvent event) {
            damages.add(event.getDamage());
        }

        boolean allHaveBothTypes() {
            for (Damage damage : damages) {
                if (!damage.hasDamageType(DamageType.SpecialSkill)
                        || !damage.hasDamageType(DamageType.AdditionAttack)) {
                    return false;
                }
            }
            return !damages.isEmpty();
        }
    }
}
