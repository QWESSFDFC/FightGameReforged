package cn.gfhnv.debug_tools;

import cn.gfhnv.game.data.*;
import cn.gfhnv.game.entity.DamageReductionSource;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entityController.FixOrderController;
import cn.gfhnv.game.entityController.UniversalController;
import cn.gfhnv.game.officialStuff.customEntity.players.ActorLiXiaoYan;
import cn.gfhnv.game.officialStuff.customEntity.players.PlayerOne;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.command.*;
import cn.gfhnv.game.system.fight.*;
import cn.gfhnv.game.world.World;

import java.util.ArrayList;
import java.util.List;

import static cn.gfhnv.debug_tools.TestKit.*;
import static cn.gfhnv.debug_tools.TestSupport.*;
import static cn.gfhnv.debug_tools.TestProbes.*;

/**
 * 自测：**实体底座**。
 * <p>
 * 主题：实体的属性契约（面板 / 临时属性 / @NoConfig）与控制器 copy。
 * <p>
 * 由 {@code TestCommandSystem#main} 按顺序调用 {@code EntityCoreTests#runAll()}。
 */
public final class EntityCoreTests {

    /**
     * 阶段 0：给实体属性清单<b>上锁</b>（见 `project_analyses/ENTITY-ATTRIBUTE-SPLIT-2026-10.md` 第七节）。
     * <p>
     * 这是"拆分实体属性"这件事<b>收益最大的那一步，且零风险</b> —— 不搬任何字段，
     * 只用反射把两条生命周期规则变成看得见的断言：
     * <ol>
     *     <li>{@link LivingThing#copy()} 必须把每个<b>标量属性</b>都带过去；</li>
     *     <li>{@link LivingThing#whenFightEnds()} 必须把临时状态复位。</li>
     * </ol>
     * 做法是把实体的每个标量字段都填上一个"和默认值不可能撞车"的探测值，再 {@code copy()}
     * 逐个比对 —— 漏掉的字段不会静默通过，要么修好，要么<b>显式</b>写进
     * {@code knownNotCopied}（那串名字就是看得见的 TODO）。
     * <p>
     * 以后新增一个属性字段却忘了在复制构造器里带上，这条测试会立刻变红。
     */
    private static void testLivingThingAttributeContract() {
        section("实体属性契约（阶段 0）：copy() 全字段 + whenFightEnds() 复位");
        try {
            // 「临时属性」：用户 2026-10-03 拍板 —— 这些是<b>一次性 / 战中的加成</b>，
            // copy() 刻意<b>不</b>带过去；对应地 whenFightEnds() 必须把它们清零（两张表互补）。
            // 23 个按「谁在写」分三类（2026-10-03 逐个查过写入点，细节见
            // AttributeProfile#resetTemporary 的 javadoc）：
            //   ① 效果写的 13 个 *Enhance*（六个通用强化效果成对加减 + 白厄变身自己加减 +
            //      2026-10-24 加的治疗量加成 healEnhancePercent）
            //   ② 预留未接线的 10 个（五元素穿透 / 五元素增伤，全项目零写入点）
            //   ③ 死字段 1 个（extraDamage —— 活的那套是 Skill#extraDamage，另一个字段）
            java.util.Set<String> temporaryAttributes = new java.util.HashSet<>(java.util.Arrays.asList(
                    "extraDamage",
                    "metalPenetration", "woodPenetration", "waterPenetration",
                    "firePenetration", "dirtPenetration",
                    "metalDamageEnhance", "woodDamageEnhance", "waterDamageEnhance",
                    "fireDamageEnhance", "dirtDamageEnhance",
                    "attackEnhancePercent", "defenceEnhancePercent", "speedEnhancePercent",
                    "hpEnhancePercent", "criticalDamageEnhancePercent", "criticalDamageEnhanceAmount",
                    "criticalRateEnhancePercent", "criticalRateEnhanceAmount",
                    "attackEnhanceAmount", "defenceEnhanceAmount",
                    "speedEnhanceAmount", "hpEnhanceAmount", "healEnhancePercent"));
            // 刻意<b>不</b>复制的另一类：只读试算标志。副本必须是一个干净的新个体
            java.util.Set<String> notCopiedOnPurpose = new java.util.HashSet<>(
                    java.util.Arrays.asList("anticipating"));
            // 「面板属性」：与临时属性互补的那一半 —— 配置 / 构造出来、跟着副本走，
            // <b>必须复制</b>、<b>绝不能清零</b>（清成 0 会永久削弱该角色）。
            // 面板组那 9 个基础值是 2026-10-24 搬进 AttributeProfile 的（第 3 步），
            // individualMultipleArea 由角色按【燃点】算出来（ActorLiXiaoYan:122/224）。
            // ⚠️ hp 是这里唯一"复制 + 每局补满"的：whenFightEnds 自己 setHp(getHpMax())，
            // 所以下面"不清零"那张表要把它排除（它是**故意**被改的，不是被清零）。
            java.util.Set<String> panelAttributes = new java.util.HashSet<>(java.util.Arrays.asList(
                    "hp", "baseHpMax", "baseAttack", "baseDefence", "baseSpeed", "baseCriticalRate",
                    "hpGrow", "attackGrow", "defenceGrow", "element",
                    "defenceLoss", "individualMultipleArea"));

            PlayerOne template = new PlayerOne(125);
            List<ScalarSlot> scalars = scalarSlotsOf(template);
            check("前提：反射扫到了实体的标量字段（不是 0 个）—— 实扫 " + scalars.size() + " 个",
                    scalars.size() >= 30);
            // 扫描范围必须**包含 @DataFlatten 组件**：字段搬进组件之后，若这一步漏了，
            // 那些字段会从扫描里静默消失、契约断言退化成"永远通过"（假绿）。
            // 光断言"数量 ≥ 30"抓不住它，所以这里点名要求组件里的字段也在扫描结果里。
            List<String> scannedNames = new ArrayList<>();
            for (ScalarSlot slot : scalars) {
                scannedNames.add(slot.field().getName());
            }
            check("前提：扫描范围下潜进了 @DataFlatten 组件（搬走的属性没从契约里静默消失）",
                    scannedNames.containsAll(java.util.Arrays.asList(
                            "metalResistance", "metalPenetration", "metalDamageEnhance",
                            "metalManaGrow", "penetration", "enhance", "baseCriticalDamage",
                            "attackEnhancePercent", "hpEnhanceAmount", "alive",
                            "hp", "baseHpMax", "baseAttack", "baseDefence", "baseSpeed", "baseCriticalRate",
                            "hpGrow", "attackGrow", "defenceGrow", "element")));

            for (ScalarSlot slot : scalars) {
                if (notCopiedOnPurpose.contains(slot.field().getName())) continue;
                slot.field().setAccessible(true);
                slot.field().set(slot.owner(), probeValueFor(slot.field().getType()));
            }
            LivingThing copied = template.copy();
            List<ScalarSlot> copiedScalars = scalarSlotsOf(copied);

            List<String> missed = new ArrayList<>();
            List<String> unexpectedlyMissed = new ArrayList<>();
            for (int i = 0; i < scalars.size(); i++) {
                ScalarSlot before = scalars.get(i);
                ScalarSlot after = copiedScalars.get(i);
                before.field().setAccessible(true);
                after.field().setAccessible(true);
                Object expected = before.field().get(before.owner());
                Object actual = after.field().get(after.owner());
                boolean same = expected == null ? actual == null : expected.equals(actual);
                if (!same) {
                    missed.add(before.field().getName());
                    if (!temporaryAttributes.contains(before.field().getName())) {
                        unexpectedlyMissed.add(before.field().getName());
                    }
                }
            }
            check("copy()：没有『临时属性』之外的新漏字段（新属性忘了复制会被这条抓住）—— 新漏 "
                    + unexpectedlyMissed, unexpectedlyMissed.isEmpty());
            check("copy()：不复制集合与『临时属性』清单完全一致（不带 " + missed.size() + " / 清单 "
                            + temporaryAttributes.size() + "）—— 实际不带的：" + missed,
                    missed.size() == temporaryAttributes.size() && temporaryAttributes.containsAll(missed));

            check("copy()：面板属性 individualMultipleArea 被带过去了（它不在临时属性表里）",
                    copied.getIndividualMultipleArea() == template.getIndividualMultipleArea());

            // 法力表是**深拷贝**：副本必须拿到**另一批** Mana 对象。
            // 浅拷贝的话模板与副本会共享同一批蓝条 —— 选人时改一个人的蓝，另一个也跟着变，
            // 而且模板会被一局一局地改掉（这条在字段搬进组件时最容易丢）。
            PlayerOne manaTemplate = new PlayerOne(125);
            LivingThing manaCopy = manaTemplate.copy();
            double manaBefore = manaCopy.getManas().get(0).getAmount();
            manaTemplate.getManas().get(0).setAmount(123.0d);
            check("copy()：法力表是深拷贝（副本的 Mana 是另一批对象，改模板的蓝不会传到副本）—— "
                            + "模板 " + manaTemplate.getManas().get(0).getAmount()
                            + " / 副本 " + manaCopy.getManas().get(0).getAmount(),
                    manaTemplate.getManas().size() == manaCopy.getManas().size()
                            && manaTemplate.getManas().get(0) != manaCopy.getManas().get(0)
                            && manaCopy.getManas().get(0).getAmount() == manaBefore
                            && manaTemplate.getManas().get(0).getAmount() == 123.0d);

            List<String> staleNames = new ArrayList<>();
            for (String name : temporaryAttributes) {
                boolean found = false;
                for (ScalarSlot slot : scalars) {
                    if (slot.field().getName().equals(name)) found = true;
                }
                if (!found) staleNames.add(name);
            }
            check("copy()：临时属性清单里的名字都真的存在（字段改名后清单不会静默失效）—— 失效的 "
                    + staleNames, staleNames.isEmpty());

            for (int i = 0; i < scalars.size(); i++) {
                ScalarSlot before = scalars.get(i);
                if (!notCopiedOnPurpose.contains(before.field().getName())) continue;
                ScalarSlot after = copiedScalars.get(i);
                after.field().setAccessible(true);
                check("copy()：只读试算标志「" + before.field().getName() + "」刻意不带过去（副本要是干净的新个体）",
                        Boolean.FALSE.equals(after.field().get(after.owner())));
            }

            // ---- whenFightEnds()：临时状态必须复位 ----
            PlayerOne resetProbe = new PlayerOne(125);
            long hpMaxBefore = resetProbe.getHpMax();
            resetProbe.setHp(1);
            resetProbe.setPresentTurn(new cn.gfhnv.game.system.fight.TurnEntry(
                    resetProbe, java.math.BigDecimal.ONE, java.math.BigDecimal.ZERO));
            resetProbe.addDamageReduction(new DamageReductionSource("阶段0探针"), 0.5);
            for (cn.gfhnv.game.system.mana.Mana mana : resetProbe.getManas()) mana.setAmount(0);
            resetProbe.whenFightEnds();

            check("whenFightEnds()：生命补满", resetProbe.getHp() == hpMaxBefore);
            check("whenFightEnds()：当前回合被复位", resetProbe.getPresentTurn() == null);
            check("whenFightEnds()：效果列表被清空", resetProbe.getEntityEffectList().isEmpty());
            check("whenFightEnds()：法力回满", resetProbe.getManas().stream()
                    .allMatch(mana -> mana.getAmount() == mana.getAmountMax()));
            // 【已知缺口】PROJECT-ANALYSIS-2026-09 §6.4 N3：减伤没被清。
            // 这条断言记录的是"现状"，不是"应该" —— 阶段 2 把属性收进对象、各组件自带 reset() 之后，
            // 请把它翻成 isEmpty()，并同步删掉这段注释。
            check("whenFightEnds()：减伤被清空（技能挂上来的那些不走效果生命周期，不清会渗进下一局）",
                    resetProbe.getDamageReductions().isEmpty());

            // 用户 2026-10-03 问的："效果结算时不会重复减吗？"
            // **不会**：removeDamageReduction 是按【来源身份】在列表上 removeIf，
            // 不是数值累减 —— 所以"效果先摘自己的 + 统一清空"怎么叠加都不会重复扣。
            // 把两条路都走一遍，断言承伤倍率回到干净的 1.0（没残留、也没被减过头）。
            LivingThing reductionProbe = new PlayerOne(125);
            DamageReductionSource sharedReduction = new DamageReductionSource("重复扣探针");
            reductionProbe.addDamageReduction(sharedReduction, 0.5);
            reductionProbe.removeDamageReduction(sharedReduction);   // 效果自己的 whenLastTimeEnd 走这条
            reductionProbe.clearDamageReductions();                  // whenFightEnds 走这条
            reductionProbe.clearDamageReductions();                  // 再清一遍也不翻车
            check("减伤清理是幂等的：效果先摘 + 统一清空 + 再清一遍 → 列表空、承伤倍率回到 1.0（没有「重复减」）",
                    reductionProbe.getDamageReductions().isEmpty()
                            && reductionProbe.getDamageTakenMultiplier() == 1.0);

            // ---- 「临时属性」的重置契约 ----
            // 用户 2026-10-03 拍板：上面那 24 个是临时属性，所以 copy() 不带。
            // 但"临时"的另一半是<b>有重置义务</b>（见 ENTITY-ATTRIBUTE-SPLIT-2026-10 第二节的生命周期分类）：
            // 整场结束时它们必须回到默认值，否则会渗进下一局。
            PlayerOne temporaryProbe = new PlayerOne(125);
            List<ScalarSlot> temporarySlots = scalarSlotsOf(temporaryProbe);
            for (ScalarSlot slot : temporarySlots) {
                if (!temporaryAttributes.contains(slot.field().getName())) continue;
                slot.field().setAccessible(true);
                slot.field().set(slot.owner(), probeValueFor(slot.field().getType()));
            }
            for (ScalarSlot slot : temporarySlots) {
                if (!temporaryAttributes.contains(slot.field().getName())) continue;
                slot.field().setAccessible(true);
                check("前提：临时属性「" + slot.field().getName() + "」确实被设成了非默认值",
                        !isDefaultValue(slot.field().get(slot.owner())));
            }
            temporaryProbe.whenFightEnds();
            List<String> notReset = new ArrayList<>();
            for (ScalarSlot slot : temporarySlots) {
                if (!temporaryAttributes.contains(slot.field().getName())) continue;
                slot.field().setAccessible(true);
                if (!isDefaultValue(slot.field().get(slot.owner()))) notReset.add(slot.field().getName());
            }
            check("whenFightEnds()：临时属性全部复位（没复位的 = 会渗进下一局的）—— 没复位的 "
                    + notReset, notReset.isEmpty());

            // 面板属性反过来：整场结束**不能**把它们清掉，否则那个角色被永久削弱。
            // ⚠️ hp 排除在外：whenFightEnds 会 setHp(getHpMax()) 把它**故意补满**
            // （那条断言在上面："whenFightEnds()：生命补满"）。
            PlayerOne panelProbe = new PlayerOne(125);
            panelProbe.setIndividualMultipleArea(7.5d);
            List<ScalarSlot> panelSlots = scalarSlotsOf(panelProbe);
            java.util.Map<String, Object> panelBefore = new java.util.LinkedHashMap<>();
            for (ScalarSlot slot : panelSlots) {
                String name = slot.field().getName();
                if (!panelAttributes.contains(name) || "hp".equals(name)) continue;
                slot.field().setAccessible(true);
                Object probe = probeValueFor(slot.field().getType());
                slot.field().set(slot.owner(), probe);
                panelBefore.put(name, probe);
            }
            check("前提：面板属性都设成了非默认值（下面那条「不清零」才有信息量）—— 实设 "
                            + panelBefore.size() + " 个：" + panelBefore.keySet(),
                    panelBefore.size() >= 9);
            panelProbe.whenFightEnds();
            List<String> cleared = new ArrayList<>();
            for (ScalarSlot slot : panelSlots) {
                String name = slot.field().getName();
                if (!panelBefore.containsKey(name)) continue;
                slot.field().setAccessible(true);
                if (!panelBefore.get(name).equals(slot.field().get(slot.owner()))) cleared.add(name);
            }
            check("whenFightEnds()：面板属性（含 elementSort）一个都不清（清了就是永久削弱角色）—— 被清掉的 "
                    + cleared, cleared.isEmpty());
            check("whenFightEnds()：面板属性 individualMultipleArea 不被清零（清了就是永久削弱角色）",
                    panelProbe.getIndividualMultipleArea() == 7.5d);
            check("whenFightEnds()：hp 是面板组里唯一被**故意**改的（补满到上限，不是清零）—— hp "
                            + panelProbe.getHp() + " / 上限 " + panelProbe.getHpMax(),
                    panelProbe.getHp() == panelProbe.getHpMax());
        } catch (Exception e) {
            check("实体属性契约测试不该抛异常，实际抛了：" + e, false);
            e.printStackTrace();
        }
    }

    /**
     * 控制器复制的契约（2026-10-24）：副本的控制器必须<b>还是原来那种</b>、技能是<b>新实例</b>、
     * 而且<b>绑在副本上</b>。
     * <p>
     * <b>为什么要有这条</b>：以前这段逻辑是 {@code LivingThing} 复制构造器里的一串
     * {@code instanceof}（玩家 / 思维 AI / 固定顺序 / 通用各来一支）—— 每加一种控制器都得回去改那个类，
     * 漏改就<b>静默退化成随机 AI</b>（固定顺序的 BOSS 一进战斗就乱打，而且不报错）。
     * 现在每种控制器重写 {@link UniversalController#copy(LivingThing)}，
     * 所以这里按<b>类</b>一一比对 —— 以后谁加了新控制器却不重写 {@code copy()}，这条会红。
     */
    private static void testControllerCopy() {
        section("控制器复制：类型不变 + 技能是新实例 + 绑到新实体");

        // ① 全注册表扫一遍：每个模板 copy() 之后控制器类名必须一致
        List<String> changed = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (LivingThing template : World.getLivingEntityList()) {
            if (template.getController() == null) {
                continue;
            }
            LivingThing copy = template.copy();
            String before = template.getController().getClass().getSimpleName();
            String after = copy.getController() == null ? "null" : copy.getController().getClass().getSimpleName();
            if (!seen.contains(before)) {
                seen.add(before);
            }
            if (!before.equals(after)) {
                changed.add(template.getId() + "：" + before + " → " + after);
            }
        }
        check("控制器复制：注册表里每个模板 copy() 之后控制器**类型不变**"
                        + "（漏重写 copy() 就会静默退化成随机 AI）—— 覆盖 " + seen + "，变了 " + changed,
                !seen.isEmpty() && changed.isEmpty());

        // ② 技能是新实例：冷却 / 消耗记在副本自己那份上，不会污染模板
        PlayerOne skillTemplate = new PlayerOne(125);
        LivingThing skillCopy = skillTemplate.copy();
        check("控制器复制：副本拿到**另一批**技能实例（冷却记在自己那份上，不污染模板）",
                !skillCopy.getController().getSkills().isEmpty()
                        && skillCopy.getController().getSkills().get(0)
                        != skillTemplate.getController().getSkills().get(0));

        // ③ 控制器必须绑在**副本**上（绑错的话技能与效果会算到原实体身上）
        check("控制器复制：控制器绑在**副本**上（owner 指回新实体）",
                skillCopy.getController().getOwner() == skillCopy
                        && skillTemplate.getController().getOwner() == skillTemplate);

        // ④ 固定顺序的轮转表也得跟过去（类型对但轮转丢了 = 打法还是变了）
        LivingThing bossTemplate = entityTemplateOf("game_official_content:flameReaver");
        if (bossTemplate != null && bossTemplate.getController() instanceof FixOrderController original) {
            LivingThing bossCopy = bossTemplate.copy();
            FixOrderController copied = bossCopy.getController() instanceof FixOrderController fix ? fix : null;
            check("控制器复制：固定顺序的轮转表跟着走（副本仍按同一顺序出手）—— 轮转 "
                            + (copied == null ? "（副本已退化成别的控制器！）" : copied.getRotationNames()),
                    copied != null && copied.getRotationNames().equals(original.getRotationNames()));
        }
    }

    /**
     * 跑本主题的全部用例（顺序 = 下面列的顺序）。
     */
    public static void runAll() throws Exception {
        testLivingThingAttributeContract();
        testControllerCopy();
    }

    private EntityCoreTests() {
    }
}
