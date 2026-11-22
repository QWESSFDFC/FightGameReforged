package cn.gfhnv.debug_tools;

import cn.gfhnv.game.damage.Damage;
import cn.gfhnv.game.damage.DamageCalculate;
import cn.gfhnv.game.damage.DamageType;
import cn.gfhnv.game.data.DataBridge;
import cn.gfhnv.game.data.NbtCompound;
import cn.gfhnv.game.data.NbtList;
import cn.gfhnv.game.entity.DamageReductionSource;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entity.PropertyEnhanceType;
import cn.gfhnv.game.event.DamageEvent;
import cn.gfhnv.game.interfaces.IModifyDamage;
import cn.gfhnv.game.officialStuff.customEffect.flameReaverEffects.Erosion;
import cn.gfhnv.game.officialStuff.customEntity.monsters.CommonInsect;
import cn.gfhnv.game.officialStuff.customEntity.players.PlayerOne;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;

import java.util.ArrayList;

import static cn.gfhnv.debug_tools.TestKit.*;
import static cn.gfhnv.debug_tools.TestProbes.*;
import static cn.gfhnv.debug_tools.TestSupport.*;

/**
 * 自测：**战斗数值**。
 * <p>
 * 主题：伤害计算与减伤表（含承伤倍率的乘算）。
 * <p>
 * 由 {@code TestCommandSystem#main} 按顺序调用 {@code DamageTests#runAll()}。
 */
public final class DamageTests {

    private DamageTests() {
    }

    /**
     * 测试减伤：{@code LivingThing} 的多个减伤来源按<b>乘算</b>叠加
     * （50% 与 25% → 只受 37.5% 伤害），并且伤害永远不会算成负数
     * （负伤害会被 {@code getDamage} 当成治疗）。
     */
    private static void testDamageReduction() {
        section("减伤计算（乘算叠加）");
        try {
            LivingThing target = new CommonInsect(100L).copy();
            Object sourceA = new Object();
            Object sourceB = new Object();

            // 50% 与 25%：乘算 → 0.5 × 0.75 = 0.375（相加才是 0.25，那是错的）
            target.addDamageReduction(sourceA, 0.5);
            target.addDamageReduction(sourceB, 0.25);
            check("两个减伤 50% + 25% 乘算 → 承伤 0.375",
                    Math.abs(target.getDamageTakenMultiplier() - 0.375) < 1e-9);

            // 每个实体一个战斗组件：copy() 之后列表/组件都必须是独立的一份
            LivingThing componentSource = new CommonInsect(100L).copy();
            componentSource.addDamageReduction(sourceA, 0.5);
            LivingThing componentCopy = componentSource.copy();
            componentCopy.addDamageReduction(sourceB, 0.25);
            check("战斗组件随 copy() 独立：原体 1 条、副本 2 条、组件不是同一个 —— "
                            + componentSource.getDamageReductions().size() + " / "
                            + componentCopy.getDamageReductions().size(),
                    componentSource.getCombat() != componentCopy.getCombat()
                            && componentSource.getDamageReductions().size() == 1
                            && componentCopy.getDamageReductions().size() == 2);
            check("总减伤 = 1 − 承伤倍率 = 0.625",
                    Math.abs(target.getDamageAbsorbedPercent() - 0.625) < 1e-9);

            // 同一来源重复添加只算一次（技能反复触发不会越叠越多）
            target.addDamageReduction(sourceA, 0.5);
            check("同一来源重复添加不会叠两次",
                    Math.abs(target.getDamageTakenMultiplier() - 0.375) < 1e-9);

            // 移除一个来源
            target.removeDamageReduction(sourceB);
            check("移除一个来源后只剩 50% 减伤",
                    Math.abs(target.getDamageTakenMultiplier() - 0.5) < 1e-9);

            // 减伤来源要在 /data 里看得见（用户实测反馈过"只有 percent，看不出是谁给的"）
            target.clearDamageReductions();
            target.addDamageReduction(new DamageReductionSource("测试来源"), 0.25);
            NbtCompound reductionDump = DataBridge.toCompound(target);
            check("减伤来源在 /data 里带得出名字（不再是只有 percent）",
                    reductionDump.get("damageReductions") instanceof NbtList reductions
                            && reductions.size() == 1
                            && reductions.get(0) instanceof NbtCompound first
                            && "测试来源".equals(first.get("sourceName").asString())
                            && Math.abs(first.get("percent").asDouble() - 0.25) < 1e-9);
            // 按身份比较这一点不能被名字动摇：名字相同也是两个独立来源
            target.addDamageReduction(new DamageReductionSource("测试来源"), 0.1);
            check("同名但不同实例的来源仍然是两条（身份比较没被名字动摇）",
                    target.getDamageReductions().size() == 2);

            // 兼容旧写法：setDamageAbsorbedPercent(0.75) → 少受 75%
            target.clearDamageReductions();
            target.setDamageAbsorbedPercent(0.75);
            check("旧写法 setDamageAbsorbedPercent(0.75) → 承伤 0.25",
                    Math.abs(target.getDamageTakenMultiplier() - 0.25) < 1e-9);

            // 比例被夹到 [0,1]：减伤叠再多也只会压到 0，不会变成负数
            target.clearDamageReductions();
            target.addDamageReduction(sourceA, 1.5);
            check("减伤比例被夹到 1（承伤 0，不会变负数）",
                    target.getDamageTakenMultiplier() == 0);

            // 真实伤害计算：承伤 = 基础伤害 × 承伤倍率
            LivingThing attacker = new PlayerOne(125).copy();
            attacker.setCriticalRate(-1);   // 负暴击率 = 永不暴击，避免随机暴击干扰比例断言
            Skill probe = new ProbeSkill("测伤", new ArrayList<>(), 1, 1.0);
            LivingThing dummy = new CommonInsect(100L).copy();
            long base = DamageCalculate.calculate(attacker, dummy, probe);
            check("基础伤害大于 0（测试前提）", base > 0);

            dummy.addDamageReduction(sourceA, 0.5);
            long reduced = DamageCalculate.calculate(attacker, dummy, probe);
            check("减伤 50% 后伤害约为一半（取整误差 ≤ 2）",
                    Math.abs(reduced - base * 0.5) <= 2);

            dummy.clearDamageReductions();
            dummy.addDamageReduction(sourceB, 1.0);
            long zeroed = DamageCalculate.calculate(attacker, dummy, probe);
            check("减伤 100% 时伤害为 0（不是负数，也就不会变成治疗）", zeroed == 0);

            // ---- 抗性 / 穿透：抗性乘区 = 1 + 穿透 − 抗性，允许穿透把抗性压成负数 ----
            // 虫子的元素是金，金属抗性 0.95；用同族虫子当靶子，数值可控
            LivingThing metalAttacker = new CommonInsect(100L).copy();
            metalAttacker.setCriticalRate(-1);
            LivingThing metalVictim = new CommonInsect(100L).copy();
            long resistant = DamageCalculate.calculate(metalAttacker, metalVictim, probe);
            check("抗性 95% 时伤害很低但大于 0（测试前提）", resistant > 0);

            // 抗性 0.95 − 穿透 0.5 = 有效抗性 0.45 → 乘数从 0.05 变成 0.55，即 ×11
            // （用 10% 容差：resistant 是**取整后**的伤害，再乘 11 会放大取整误差）
            metalAttacker.setPenetration(0.5);
            long pierced = DamageCalculate.calculate(metalAttacker, metalVictim, probe);
            long expectedPierced = resistant * 11;
            check("穿透 50% 抵消抗性：有效抗性 0.95 → 0.45，乘数由 0.05 变 0.55（约 ×11）—— 实际 "
                            + pierced + " vs " + resistant,
                    Math.abs(pierced - expectedPierced) <= Math.max(3, expectedPierced / 10));

            // 抗性 0 + 穿透 50% → 乘区 1 + 0.5 = 1.5（穿透允许把抗性压成负数）
            metalAttacker.setPenetration(0);
            metalVictim.setMetalResistance(0);
            long noResistanceNoPen = DamageCalculate.calculate(metalAttacker, metalVictim, probe);
            metalAttacker.setPenetration(0.5);
            long noResistanceWithPen = DamageCalculate.calculate(metalAttacker, metalVictim, probe);
            check("抗性 0 + 穿透 50% → ×1.5（穿透能把抗性压成负数）—— 无穿透 " + noResistanceNoPen
                            + " / 有穿透 " + noResistanceWithPen,
                    Math.abs(noResistanceWithPen - noResistanceNoPen * 1.5) <= 2);

            // 抗性 0.5 + 穿透 0.5 = 有效抗性 0 → 正好抵消，伤害与"无抗性无穿透"一致
            metalVictim.setMetalResistance(0.5);
            metalAttacker.setPenetration(0.5);
            long exactlyCancelled = DamageCalculate.calculate(metalAttacker, metalVictim, probe);
            check("抗性 50% + 穿透 50% = 正好抵消（回到无抗性无穿透的伤害）—— 实际 " + exactlyCancelled,
                    Math.abs(exactlyCancelled - noResistanceNoPen) <= 2);

            // 抗性 0.3 + 穿透 0.5 → 有效抗性 -0.2 → ×1.2（穿透超过抗性时成为增伤）
            metalVictim.setMetalResistance(0.3);
            metalAttacker.setPenetration(0.5);
            long overPierced = DamageCalculate.calculate(metalAttacker, metalVictim, probe);
            check("穿透超过抗性：抗性 30% + 穿透 50% → 有效抗性 -20%、乘区 1.2 —— 实际 "
                            + overPierced + " vs 无抗性 " + noResistanceNoPen,
                    Math.abs(overPierced - noResistanceNoPen * 1.2) <= 2);

            metalAttacker.setPenetration(0);
            metalVictim.setMetalResistance(0);
            long neutral = DamageCalculate.calculate(metalAttacker, metalVictim, probe);
            metalVictim.setMetalResistance(-0.5);
            long weak = DamageCalculate.calculate(metalAttacker, metalVictim, probe);
            check("抗性为负（弱点）→ 受伤 ×1.5（负抗性不会被夹掉）",
                    Math.abs(weak - neutral * 1.5) <= 2);

            metalAttacker.setPenetration(0.5);
            long weakPierced = DamageCalculate.calculate(metalAttacker, metalVictim, probe);
            check("负抗性也能被穿透放大：抗性 -50% + 穿透 50% → ×2.0 —— 实际 "
                            + weakPierced + " vs 无抗性 " + neutral,
                    Math.abs(weakPierced - neutral * 2.0) <= 2);

            // ---- 伤害修正器：可以有多个，按添加顺序依次套用 ----
            LivingThing tank = new CommonInsect(100L).copy();
            IModifyDamage halve = new IModifyDamage() {
                @Override
                public long damageModify(long newHp, DamageEvent da) {
                    return newHp / 2;
                }
            };
            IModifyDamage minusHundred = new IModifyDamage() {
                @Override
                public long damageModify(long newHp, DamageEvent da) {
                    return newHp - 100;
                }
            };
            DamageEvent sample = new DamageEvent(metalAttacker, tank, probe);
            tank.setModifyDamage(halve);
            tank.addModifyDamage(minusHundred);
            check("两个修正器都要生效：1000 → 500 → 400",
                    tank.modifyIncomingDamage(1000, sample) == 400);
            check("修正器列表里有 2 个", tank.getModifyDamageList().size() == 2);

            tank.setModifyDamage(halve);
            check("setModifyDamage 是替换而不是追加（1000 → 500）",
                    tank.modifyIncomingDamage(1000, sample) == 500
                            && tank.getModifyDamageList().size() == 1);

            tank.addModifyDamage(minusHundred);
            tank.removeModifyDamage(halve);
            check("移除一个修正器后只剩另一个（1000 → 900）",
                    tank.modifyIncomingDamage(1000, sample) == 900);

            // ---- 免死机制：致死伤害在 getDamage 里被修正器拦下 ----
            // 白厄的免死、李晓焰的复活都挂在同一条链上，这里用等价的探针验证链路
            tank.clearModifyDamage();
            tank.setHp(1);
            DamageEvent lethal = new DamageEvent(metalAttacker, tank, probe);
            lethal.getDamage().setDamageAmount(9999L);
            ProbeDeathWard ward = new ProbeDeathWard();
            tank.addModifyDamage(ward);
            tank.takeDamage(lethal);
            check("免死把致死伤害拦下、血量锁在 1（这次是真实挨打，免死被消费）",
                    tank.getHp() == 1 && tank.isAlive() && ward.wasConsumed());

            // ---- 伤害试算必须是只读的：AI 预判一次不能就把免死花掉 ----
            LivingThing living = new CommonInsect(100L).copy();
            ProbeDeathWard guarded = new ProbeDeathWard();
            living.addModifyDamage(guarded);
            living.setHp(1);
            DamageEvent predictedEvent = new DamageEvent(metalAttacker, living, probe);
            predictedEvent.getDamage().setDamageAmount(9999L);
            long predicted = probe.getAnticipatedDamage(living, metalAttacker);
            check("试算时修正器确实被调用、并且能看出自己在试算（isAnticipating 为 true）",
                    guarded.wasCalledWhileAnticipating());
            check("试算走完整修正器链：预测伤害 = 0（1 血 − 9999，被锁回 1）",
                    predicted == 0);
            check("试算不改状态：免死没被花掉、试算结束后 isAnticipating() 复位",
                    guarded.wasConsumed() == false && !living.isAnticipating());
            living.takeDamage(predictedEvent);
            check("试算之后再真挨打，免死照常触发（只读预测没有偷走次数）",
                    living.getHp() == 1 && guarded.wasConsumed());
            tank.removeModifyDamage(ward);
            tank.setHp(1);
            tank.takeDamage(lethal);
            check("移除修正器后 1 血吃 9999 伤害会死（说明前面是修正器救的）",
                    tank.getHp() == 0 && !tank.isAlive());

            // ---- 血量下限：修正器只能把血往上拉，不能反过来加伤 ----
            LivingThing guardian = new CommonInsect(100L).copy();
            long guardianMaxHp = guardian.getHpMax();
            ProbeHpFloor floor = new ProbeHpFloor(0.5);
            guardian.addModifyDamage(floor);
            guardian.setHp(1);
            guardian.takeDamage(lethal);
            check("血量下限：1 血吃致死伤害后被抬到 50% 生命上限",
                    guardian.getHp() == (long) (guardianMaxHp * 0.5));
            check("满血时不触发下限（修正器只抬高、不压低）",
                    guardian.modifyIncomingDamage(guardianMaxHp, lethal) == guardianMaxHp);
            guardian.removeModifyDamage(floor);
            check("移除下限修正器后列表为空", guardian.getModifyDamageList().isEmpty());

            // ---- 白厄免死：锁血、排队与只读试算（一次性造回合的用法） ----
            testSoulscorchDeathWardDoesNotDeadlock();

            // ---- 白厄：中止变身自己收口 + 只挨一次打只反击一次（跑真实回合循环） ----
            testTimelineDeadEndEndsFight();
            testEffectTicksBeforeAction();
            testAwakenInterruptExitProtocol();

            // ---- 白厄：同场两只白厄的 AwakenEndEvent 不会互相吃掉监听器 ----
            testAwakeEndListenerOwnership();

            // ---- 无视防御：只认 IDefenceIgnore 接口，模组自写的效果一样生效 ----
            LivingThing piercer = new CommonInsect(100L).copy();
            piercer.addEffect(new ProbeDefenceIgnoreEffect(0.5, 0));
            check("无视防御：接口实现被汇总（0.5）",
                    Math.abs(piercer.getIgnoreDefencePercent() - 0.5) < 1e-9);
            // 前面的穿透用例把 metalAttacker 的穿透留在 0.5、metalVictim 的抗性留在 -0.5；
            // 这里要单独比较"无视防御"，先清回中性状态。
            metalAttacker.setPenetration(0);
            metalVictim.setMetalResistance(0);
            long normalHit = DamageCalculate.calculate(metalAttacker, metalVictim, probe);
            long ignoreHit = DamageCalculate.calculate(piercer, metalVictim, probe);
            check("无视防御确实让伤害变高", ignoreHit > normalHit);

            // 固定无视值按"占目标防御的比例"折算：与等价的纯百分比写法结果一致
            LivingThing fixedPiercer = new CommonInsect(100L).copy();
            fixedPiercer.addEffect(new ProbeDefenceIgnoreEffect(0.2, 1_000));
            double defence = metalVictim.getDefence();
            LivingThing percentPiercer = new CommonInsect(100L).copy();
            percentPiercer.addEffect(new ProbeDefenceIgnoreEffect(0.2 + 1_000.0 / defence, 0));
            long fixedHit = DamageCalculate.calculate(fixedPiercer, metalVictim, probe);
            long percentHit = DamageCalculate.calculate(percentPiercer, metalVictim, probe);
            check("无视固定值折算进百分比（防御 " + defence + "，1000 点 = "
                            + Math.round(1_000.0 / defence * 10000) / 100.0 + "%）—— 固定 " + fixedHit
                            + " / 百分比 " + percentHit,
                    Math.abs(fixedHit - percentHit) <= 1);
        } catch (Exception e) {
            fail("减伤测试抛出异常：" + e);
        }
    }

    /**
     * 伤害的<b>三段数值</b>、<b>伤害类型</b>与<b>真实伤害</b>（{@code Damage} 重做，2026-11-14）。
     * <p>
     * ① 原始值 = 倍率乘属性、不取整；② 公式结果；③ 实际掉血（被生命上限/免死影响）。
     * 另外两条是新能力：一次伤害可以带多个类型；元素是 {@code UNIVERSAL} 时按真实伤害算
     * （免抗性、免单元素增伤、免全局增伤，但<b>防御与减伤照算</b>）。
     */
    private static void testDamageStages() {
        section("伤害三段数值 + 伤害类型 + 真实伤害（Damage 重做）");
        try {
            LivingThing attacker = new CommonInsect(100L).copy();
            // 负暴击率 = 永不暴击：随机暴击会让"数值比例"的断言飘
            attacker.setCriticalRate(-1);
            attacker.setIndividualMultipleArea(1.0);
            LivingThing target = new CommonInsect(100L).copy();
            Skill probe = new ProbeSkill("测伤害三段", new ArrayList<>(), 1, 2.0);

            /* ---------- ① 原始值：倍率乘属性之后、没有任何系数 ---------- */
            Damage hit = new Damage(attacker, target, probe);
            double expectedRaw = attacker.getHpMax() * probe.getHpMagnification()
                    + attacker.getAttack() * probe.getAtkMagnification()
                    + attacker.getDefence() * probe.getDefMagnification()
                    + probe.getExtraDamage();
            check("① 原始值 = 生命×hp倍率 + 攻击×atk倍率 + 防御×def倍率 + 技能额外伤害 —— 实际 "
                            + hit.getRawDamageAmount() + "，期望 " + expectedRaw,
                    Math.abs(hit.getRawDamageAmount() - expectedRaw) < 1e-6);
            check("① 公式结果不超过原始值（暴击关掉，防御/抗性/减伤都只往下压）—— 实际 "
                            + hit.getCalculateDamageResult(),
                    hit.getCalculateDamageResult() <= (long) hit.getRawDamageAmount());

            /* ---------- ③ 结算前 appliedDamage 是 0；结算后 = 实际掉血 ---------- */
            check("③ 结算前 appliedDamage 是 0", hit.getAppliedDamage() == 0);
            LivingThing prey = new CommonInsect(100L).copy();
            long preyMaxHp = prey.getHpMax();
            prey.setHp(preyMaxHp);
            DamageEvent lethal = new DamageEvent(attacker, prey, probe);
            lethal.getDamage().setDamageAmount(9999L);
            prey.takeDamage(lethal);
            check("③ appliedDamage = 实际掉血（9999 打在 " + preyMaxHp + " 血上只掉这么多）—— 实际 "
                            + lethal.getDamage().getAppliedDamage(),
                    lethal.getDamage().getAppliedDamage() == preyMaxHp && prey.getHp() == 0);
            check("② 公式结果仍是 9999（攻击行读的是它，不是实际掉血）",
                    lethal.getDamage().getCalculateDamageResult() == 9999L);

            /* ---------- ④ 伤害类型：可以多个，全都记住；不传按战技 ---------- */
            DamageEvent typed = new DamageEvent(attacker, target, probe,
                    DamageType.CommonAttack, DamageType.AdditionAttack);
            check("④ 一次伤害可以带多个类型（两个都在）",
                    typed.getDamage().hasDamageType(DamageType.CommonAttack)
                            && typed.getDamage().hasDamageType(DamageType.AdditionAttack)
                            && typed.getDamage().getDamageTypes().size() == 2);
            check("④ 不传类型时按战技算（兼容 makeDamage(target, skill) 老写法）",
                    new DamageEvent(attacker, target, probe).getDamage().getDamageTypes()
                            .equals(java.util.EnumSet.of(DamageType.SpecialSkill)));
            check("④ 伤害来源名 = 技能名", "测伤害三段".equals(typed.getDamage().getDamageSourceName()));

            /* ---------- ⑤ 真实伤害：免抗性与全局增伤，但防御与减伤照算 ---------- */
            attacker.setEnhance(0.2);          // 全局增伤 +20%
            target.setMetalResistance(0.5);    // 施法者元素是金 → 目标的金属抗性 50%
            long normal = new Damage(attacker, target, probe).getCalculateDamageResult();
            long trueDamage = new Damage(attacker, target, probe, ElementSort.UNIVERSAL)
                    .getCalculateDamageResult();
            // 普通 = 原始 × 1.2 × 0.5 = 0.6 × 原始；真实 = 原始 × 1 × 1
            check("⑤ 真实伤害不吃抗性与全局增伤（普通 " + normal + " 应约为真实 " + trueDamage + " 的 0.6 倍）",
                    Math.abs(normal - trueDamage * 0.6) <= Math.max(2, trueDamage / 100));
            target.addDamageReduction(new Object(), 0.5);
            long trueReduced = new Damage(attacker, target, probe, ElementSort.UNIVERSAL)
                    .getCalculateDamageResult();
            check("⑤ 真实伤害照样吃减伤（50% 减伤后约为一半）—— " + trueDamage + " → " + trueReduced,
                    Math.abs(trueReduced - trueDamage * 0.5) <= Math.max(2, trueDamage / 100));
        } catch (Exception e) {
            fail("伤害三段测试抛出异常：" + e);
        }
    }

    /**
     * <b>暴击</b>（2026-11-15 第 73 批）：玩家基础暴伤 50% ⇒ 暴击 = 1.5 倍；
     * 攻击行带 {@code （暴击）} 标记；{@code Damage#isCritical()} 能读到。
     * <p>自测默认关闭暴击掷骰（见 {@code DamageCalculate#setCriticalEnabledForSelfTest}），
     * 这条用例临时打开、并用"暴击率 100%"让它必然触发。
     */
    private static void testCriticalHit() {
        section("暴击：基础暴伤 50% + 攻击行标记");
        boolean enabledBefore = cn.gfhnv.game.damage.DamageCalculate.isCriticalEnabled();
        try {
            cn.gfhnv.game.damage.DamageCalculate.setCriticalEnabledForSelfTest(true);

            LivingThing target = new cn.gfhnv.game.officialStuff.customEntity.monsters.CommonInsect(125L);
            Skill probe = new ProbeSkill("暴击探针", new ArrayList<>(), 1, 1.0);

            LivingThing normal = new PlayerOne(125);
            normal.setCriticalRate(0);
            long normalDamage = new cn.gfhnv.game.damage.Damage(normal, target, probe).getCalculateDamageResult();

            LivingThing crit = new PlayerOne(125);
            crit.setCriticalRate(1.0);      // 必然暴击
            crit.setCriticalDamage(0.5);    // 暴伤 50%
            cn.gfhnv.game.damage.Damage critDamage = new cn.gfhnv.game.damage.Damage(crit, target, probe);

            check("① 基础暴伤 50% ⇒ 暴击伤害 = 不暴击的 1.5 倍 —— 实际 " + normalDamage + " / "
                            + critDamage.getCalculateDamageResult(),
                    Math.abs(critDamage.getCalculateDamageResult() - normalDamage * 1.5) <= 2);
            check("② Damage#isCritical() 能读到暴击标记 —— 实际 " + critDamage.isCritical(),
                    critDamage.isCritical());
            check("③ 不暴击时标记是 false", !new cn.gfhnv.game.damage.Damage(normal, target, probe).isCritical());

            check("④ 攻击行带「（暴击）」标记",
                    cn.gfhnv.game.entity.EntityDisplay.attackLine(crit, target, probe,
                            critDamage.getCalculateDamageResult(), true).contains("（暴击）")
                            && !cn.gfhnv.game.entity.EntityDisplay.attackLine(normal, target, probe,
                            normalDamage, false).contains("（暴击）"));
        } catch (Exception e) {
            fail("暴击测试抛出异常：" + e);
        } finally {
            cn.gfhnv.game.damage.DamageCalculate.setCriticalEnabledForSelfTest(enabledBefore);
        }
    }

    /**
     * 暴击绝对值 / 暴击期望 / DOT 新口径（2026-11-21 第 87 批）。
     */
    private static void testCriticalAbsoluteExpectedAndDot() {
        section("暴击绝对值 / 期望 / DOT 公式");
        boolean enabledBefore = DamageCalculate.isCriticalEnabled();
        try {
            DamageCalculate.setCriticalEnabledForSelfTest(true);

            LivingThing stats = new PlayerOne(125);
            stats.setCriticalRate(0.05);
            stats.setCriticalDamage(0.5);
            stats.addPropertyEnhance(PropertyEnhanceType.CRITICAL_RATE, 0.2, 0, "自测-暴击率");
            stats.addPropertyEnhance(PropertyEnhanceType.CRITICAL_DAMAGE, 0.5, 0, "自测-暴伤");
            check("① 暴击率 +20% 是绝对值：5% + 20% = 25% —— 实际 " + stats.getCriticalRate(),
                    Math.abs(stats.getCriticalRate() - 0.25) < 1e-9);
            check("② 暴伤 +50% 是绝对值：50% + 50% = 100% —— 实际 " + stats.getCriticalDamage(),
                    Math.abs(stats.getCriticalDamage() - 1.0) < 1e-9);

            LivingThing target = new CommonInsect(100L).copy();
            Skill probe = new ProbeSkill("暴击期望探针", new ArrayList<>(), 1, 1.0);

            LivingThing critter = new PlayerOne(125);
            critter.setCriticalRate(1.0);
            critter.setCriticalDamage(0.5);
            long critTrue = new Damage(critter, target, probe, ElementSort.UNIVERSAL)
                    .getCalculateDamageResult();
            critter.setCriticalRate(0);
            long noCritTrue = new Damage(critter, target, probe, ElementSort.UNIVERSAL)
                    .getCalculateDamageResult();
            check("③ 真实伤害不能暴击（暴击率 100% 与 0% 结果相同）—— " + critTrue + " / " + noCritTrue,
                    Math.abs(critTrue - noCritTrue) <= 2);

            LivingThing expectedAttacker = new PlayerOne(125);
            expectedAttacker.setCriticalRate(0.5);
            expectedAttacker.setCriticalDamage(0.5);
            long expected = target.anticipating(() ->
                    new Damage(expectedAttacker, target, probe).getCalculateDamageResult());
            expectedAttacker.setCriticalRate(0);
            long baseline = new Damage(expectedAttacker, target, probe).getCalculateDamageResult();
            check("④ 试算用期望暴击：1 + 50% × 50% = 1.25 倍 —— 基线 " + baseline
                            + "，期望 " + expected,
                    Math.abs(expected - Math.round(baseline * 1.25)) <= 2);

            LivingThing dotUser = new PlayerOne(125);
            dotUser.setCriticalRate(1.0);
            dotUser.setCriticalDamage(0.5);
            LivingThing dotTarget = new CommonInsect(100L).copy();
            dotTarget.setHpMax(100_000L);
            dotTarget.setHp(100_000L);
            Damage dot = new Damage(dotUser, dotTarget, new Erosion(0.5, 1),
                    ElementSort.FIRE, 1_000.0, DamageType.DOT);
            check("⑤ DOT 原始值固定为 1000，但不暴击、且吃防御（结算 " + dot.getCalculateDamageResult()
                            + " < 1000）",
                    Math.abs(dot.getRawDamageAmount() - 1_000.0) < 1e-9
                            && !dot.isCritical() && dot.getCalculateDamageResult() < 1_000L);

            LivingThing victim = new CommonInsect(100L).copy();
            victim.setHpMax(100_000L);
            victim.setHp(50_000L);
            victim.setCriticalRate(1.0);   // 若 DOT 会暴击，这里会放大
            Erosion erosion = new Erosion(0.5, 1);
            long hpBefore = victim.getHp();
            erosion.comeIntoEffect(victim);
            long actualDrop = hpBefore - victim.getHp();
            long expectedDrop = new Damage(victim, victim, erosion, ElementSort.FIRE,
                    25_000.0, DamageType.DOT).getCalculateDamageResult();
            check("⑥ 【侵蚀】已接入 DOT 公式（实际掉血 = 同参数 DOT 的公式结果）—— 实际 "
                            + actualDrop + " / 期望 " + expectedDrop,
                    actualDrop == expectedDrop);
        } catch (Exception e) {
            fail("暴击绝对值/DOT 测试抛出异常：" + e);
        } finally {
            DamageCalculate.setCriticalEnabledForSelfTest(enabledBefore);
        }
    }

    /**
     * 增伤条目：damageEnhances 按元素命中；暴伤仍走 PropertyEnhance 的 DamageType 过滤。
     */
    private static void testDamageEnhanceAndTypedCritical() {
        section("damageEnhances 增伤 / typed 暴伤 / 真伤 / DOT");
        boolean enabledBefore = DamageCalculate.isCriticalEnabled();
        try {
            DamageCalculate.setCriticalEnabledForSelfTest(true);
            LivingThing target = new CommonInsect(100L).copy();
            Skill probe = new ProbeSkill("增伤探针", new ArrayList<>(), 1, 1.0);

            LivingThing user = new PlayerOne(125);
            user.setCriticalRate(0);
            long base = new Damage(user, target, probe, DamageType.AdditionAttack)
                    .getCalculateDamageResult();
            user.addDamageEnhance(null, 0.8, "自测-全属性增伤");
            long added = new Damage(user, target, probe, DamageType.AdditionAttack)
                    .getCalculateDamageResult();
            check("① 全属性增伤 +80% 对所有元素生效（1.8 倍）—— 实际 " + base + " / " + added,
                    Math.abs(added - base * 1.8) <= 2);

            LivingThing typedUser = new PlayerOne(125);
            typedUser.setCriticalRate(0);
            long typedBase = new Damage(typedUser, target, probe, DamageType.AdditionAttack)
                    .getCalculateDamageResult();
            typedUser.addDamageTypeEnhance(DamageType.AdditionAttack, 0.8, "自测-只追加");
            long typedHit = new Damage(typedUser, target, probe, DamageType.AdditionAttack)
                    .getCalculateDamageResult();
            long typedSpecial = new Damage(typedUser, target, probe, DamageType.SpecialSkill)
                    .getCalculateDamageResult();
            check("①-b 按伤害类型的增伤只命中追加攻击（追加 1.8 倍、战技不变）—— 实际 "
                            + typedBase + " / " + typedHit + " / " + typedSpecial,
                    Math.abs(typedHit - typedBase * 1.8) <= 2
                            && Math.abs(typedSpecial - typedBase) <= 2);

            LivingThing elementUser = new PlayerOne(125);
            elementUser.addDamageEnhance(ElementSort.FIRE, 0.3, "自测-火增伤");
            check("② 单元素增伤只命中对应元素（火 0.3 / 金 0）—— 实际 "
                            + elementUser.getDamageEnhance(ElementSort.FIRE) + " / "
                            + elementUser.getDamageEnhance(ElementSort.METAL),
                    Math.abs(elementUser.getDamageEnhance(ElementSort.FIRE) - 0.3) < 1e-9
                            && Math.abs(elementUser.getDamageEnhance(ElementSort.METAL)) < 1e-9);
            elementUser.addDamageEnhance(null, 0.2, "自测-全属性增伤");
            check("③ 全属性 + 元素增伤在同一个和里连加（0.3 + 0.2 = 0.5）—— 实际 "
                            + elementUser.getDamageEnhance(ElementSort.FIRE),
                    Math.abs(elementUser.getDamageEnhance(ElementSort.FIRE) - 0.5) < 1e-9);

            LivingThing crit = new PlayerOne(125);
            crit.setCriticalDamage(0.5);
            crit.setCriticalRate(0);
            crit.addPropertyEnhance(PropertyEnhanceType.CRITICAL_DAMAGE, 0.4, 0, "typed-暴伤");
            crit.addPropertyEnhance(PropertyEnhanceType.CRITICAL_DAMAGE, 0.8, 0, "typed-追加暴伤",
                    DamageType.AdditionAttack);
            long noCrit = new Damage(crit, target, probe, DamageType.AdditionAttack)
                    .getCalculateDamageResult();
            crit.setCriticalRate(1.0);
            long critHit = new Damage(crit, target, probe, DamageType.AdditionAttack)
                    .getCalculateDamageResult();
            check("④ 暴伤连加：基础 0.5 + 0.4(全) + 0.8(追加) = 2.7 倍 —— 实际 "
                            + noCrit + " / " + critHit,
                    Math.abs(critHit - noCrit * 2.7) <= 3);

            LivingThing trueUser = new PlayerOne(125);
            trueUser.setCriticalRate(1.0);
            long trueBase = new Damage(trueUser, target, probe, ElementSort.UNIVERSAL)
                    .getCalculateDamageResult();
            trueUser.addDamageEnhance(null, 1.0, "自测-真伤增伤");
            trueUser.addPropertyEnhance(PropertyEnhanceType.CRITICAL_DAMAGE, 1.0, 0, "自测-真伤暴伤");
            long trueTyped = new Damage(trueUser, target, probe, ElementSort.UNIVERSAL)
                    .getCalculateDamageResult();
            check("⑤ 真伤不吃增伤/暴伤 —— 实际 " + trueBase + " / " + trueTyped,
                    Math.abs(trueBase - trueTyped) <= 2);

            LivingThing dotter = new PlayerOne(125);
            dotter.addDamageEnhance(null, 0.5, "自测-DOT增伤");
            Damage dot = new Damage(dotter, target, probe, ElementSort.FIRE, 1_000.0, DamageType.DOT);
            LivingThing plain = new PlayerOne(125);
            Damage plainDot = new Damage(plain, target, probe, ElementSort.FIRE, 1_000.0, DamageType.DOT);
            check("⑥ DOT 吃全属性增伤、不暴击 —— 实际 " + plainDot.getCalculateDamageResult()
                            + " / " + dot.getCalculateDamageResult(),
                    !dot.isCritical()
                            && Math.abs(dot.getCalculateDamageResult()
                            - plainDot.getCalculateDamageResult() * 1.5) <= 3);
        } catch (Exception e) {
            fail("damageEnhances/typed 暴伤测试抛出异常：" + e);
        } finally {
            DamageCalculate.setCriticalEnabledForSelfTest(enabledBefore);
        }
    }

    /**
     * 跑本主题的全部用例（顺序 = 下面列的顺序）。
     */
    public static void runAll() throws Exception {

        testCriticalHit();
        testDamageReduction();
        testDamageStages();
        testCriticalAbsoluteExpectedAndDot();
        testDamageEnhanceAndTypedCritical();
    }
}
