package cn.gfhnv.debug_tools;

import cn.gfhnv.game.damage.DamageCalculate;
import cn.gfhnv.game.data.*;
import cn.gfhnv.game.entity.DamageReductionSource;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.DamageEvent;
import cn.gfhnv.game.interfaces.IModifyDamage;
import cn.gfhnv.game.officialStuff.customEntity.monsters.CommonInsect;
import cn.gfhnv.game.officialStuff.customEntity.players.PlayerOne;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.command.*;
import cn.gfhnv.game.system.fight.*;

import java.util.ArrayList;

import static cn.gfhnv.debug_tools.TestKit.*;
import static cn.gfhnv.debug_tools.TestSupport.*;
import static cn.gfhnv.debug_tools.TestProbes.*;

/**
 * 自测：**战斗数值**。
 * <p>
 * 主题：伤害计算与减伤表（含承伤倍率的乘算）。
 * <p>
 * 由 {@code TestCommandSystem#main} 按顺序调用 {@code DamageTests#runAll()}。
 */
public final class DamageTests {

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

            // ---- 抗性 / 穿透：**先抵后乘** —— 有效抗性 = 抗性 − 穿透，再乘 (1 − 有效抗性) ----
            // （2026-10-04 用户要求改的语义：穿透是"抵消除抗性"，不是"额外增伤"）
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

            // 抗性 0 时穿透**完全无效**（这就是新语义；旧行为是 ×1.5）
            metalAttacker.setPenetration(0);
            metalVictim.setMetalResistance(0);
            long noResistanceNoPen = DamageCalculate.calculate(metalAttacker, metalVictim, probe);
            metalAttacker.setPenetration(0.5);
            long noResistanceWithPen = DamageCalculate.calculate(metalAttacker, metalVictim, probe);
            check("抗性 0 时穿透**完全无效**（穿透不是额外增伤）—— 无穿透 " + noResistanceNoPen
                            + " / 有穿透 " + noResistanceWithPen,
                    Math.abs(noResistanceWithPen - noResistanceNoPen) <= 2);

            // 抗性 0.5 + 穿透 0.5 = 有效抗性 0 → 正好抵消，伤害与"无抗性无穿透"一致
            metalVictim.setMetalResistance(0.5);
            metalAttacker.setPenetration(0.5);
            long exactlyCancelled = DamageCalculate.calculate(metalAttacker, metalVictim, probe);
            check("抗性 50% + 穿透 50% = 正好抵消（回到无抗性无穿透的伤害）—— 实际 " + exactlyCancelled,
                    Math.abs(exactlyCancelled - noResistanceNoPen) <= 2);

            metalAttacker.setPenetration(0);
            metalVictim.setMetalResistance(0);
            long neutral = DamageCalculate.calculate(metalAttacker, metalVictim, probe);
            metalVictim.setMetalResistance(-0.5);
            long weak = DamageCalculate.calculate(metalAttacker, metalVictim, probe);
            check("抗性为负（弱点）→ 受伤 ×1.5（负抗性不会被夹掉）",
                    Math.abs(weak - neutral * 1.5) <= 2);

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
            tank.getDamage(lethal);
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
            living.getDamage(predictedEvent);
            check("试算之后再真挨打，免死照常触发（只读预测没有偷走次数）",
                    living.getHp() == 1 && guarded.wasConsumed());
            tank.removeModifyDamage(ward);
            tank.setHp(1);
            tank.getDamage(lethal);
            check("移除修正器后 1 血吃 9999 伤害会死（说明前面是修正器救的）",
                    tank.getHp() == 0 && !tank.isAlive());

            // ---- 血量下限：修正器只能把血往上拉，不能反过来加伤 ----
            LivingThing guardian = new CommonInsect(100L).copy();
            long guardianMaxHp = guardian.getHpMax();
            ProbeHpFloor floor = new ProbeHpFloor(0.5);
            guardian.addModifyDamage(floor);
            guardian.setHp(1);
            guardian.getDamage(lethal);
            check("血量下限：1 血吃致死伤害后被抬到 50% 生命上限",
                    guardian.getHp() == (long) (guardianMaxHp * 0.5));
            check("满血时不触发下限（修正器只抬高、不压低）",
                    guardian.modifyIncomingDamage(guardianMaxHp, lethal) == guardianMaxHp);
            guardian.removeModifyDamage(floor);
            check("移除下限修正器后列表为空", guardian.getModifyDamageList().isEmpty());

            // ---- 白厄免死：锁血、排队与只读试算（一次性造回合的用法） ----
            testSoulscorchDeathWardDoesNotDeadlock();

            // ---- 白厄：中止变身自己收口 + 只挨一次打只反击一次（跑真实回合循环） ----
            testAwakenInterruptExitProtocol();

            // ---- 白厄：同场两只白厄的 AwakenEndEvent 不会互相吃掉监听器 ----
            testAwakeEndListenerOwnership();

            // ---- 无视防御：只认 IDefenceIgnore 接口，模组自写的效果一样生效 ----
            LivingThing piercer = new CommonInsect(100L).copy();
            piercer.addEffect(new ProbeDefenceIgnoreEffect(0.5, 0));
            check("无视防御：接口实现被汇总（0.5）",
                    Math.abs(piercer.getIgnoreDefencePercent() - 0.5) < 1e-9);
            long normalHit = DamageCalculate.calculate(metalAttacker, metalVictim, probe);
            long ignoreHit = DamageCalculate.calculate(piercer, metalVictim, probe);
            check("无视防御确实让伤害变高", ignoreHit > normalHit);
        } catch (Exception e) {
            fail("减伤测试抛出异常：" + e);
        }
    }

    /**
     * 跑本主题的全部用例（顺序 = 下面列的顺序）。
     */
    public static void runAll() throws Exception {
        testDamageReduction();
    }

    private DamageTests() {
    }
}
