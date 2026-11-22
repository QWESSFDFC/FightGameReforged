package cn.gfhnv.debug_tools;

import cn.gfhnv.game.damage.DamageType;
import cn.gfhnv.game.data.DataBridge;
import cn.gfhnv.game.data.NbtCompound;
import cn.gfhnv.game.data.NbtList;
import cn.gfhnv.game.entity.DamageReductionSource;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entity.PropertyEnhanceType;
import cn.gfhnv.game.entityController.FixOrderController;
import cn.gfhnv.game.entityController.UniversalController;
import cn.gfhnv.game.officialStuff.customEntity.players.PlayerOne;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.world.World;

import java.util.ArrayList;
import java.util.List;

import static cn.gfhnv.debug_tools.TestKit.*;
import static cn.gfhnv.debug_tools.TestProbes.ScalarSlot;
import static cn.gfhnv.debug_tools.TestSupport.*;

/**
 * 自测：**实体底座**。
 * <p>
 * 主题：实体的属性契约（面板 / 临时属性 / @NoConfig）与控制器 copy。
 * <p>
 * 由 {@code TestCommandSystem#main} 按顺序调用 {@code EntityCoreTests#runAll()}。
 */
public final class EntityCoreTests {

    private EntityCoreTests() {
    }

    /**
     * 阶段 0：给实体属性清单<b>上锁</b>（见 `project_analyses/current/ENTITY-ATTRIBUTE-SPLIT-2026-10.md` 第七节）。
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
            // 12 个按「谁在写」分两类（细节见 AttributeProfile#resetTemporary 的 javadoc）：
            //   ① 五元素穿透 / 五元素增伤、damageEnhances、propertyEnhances 都已是容器，不再走标量表；
            //   ② 原死字段 extraDamage 已于 2026-11-22 删除（活的那套是 Skill#extraDamage）。
            java.util.Set<String> temporaryAttributes = new java.util.HashSet<>();
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
                    "defenceLoss"));

            PlayerOne template = new PlayerOne(125);
            List<ScalarSlot> scalars = scalarSlotsOf(template);
            check("前提：反射扫到了实体的标量字段（不是 0 个）—— 实扫 " + scalars.size() + " 个",
                    // 门槛 20：第 49/50 批把穿透与抗性容器化之后，面板标量从 31 个降到 26 个左右
                    scalars.size() >= 20);
            // 扫描范围必须**包含 @DataFlatten 组件**：字段搬进组件之后，若这一步漏了，
            // 那些字段会从扫描里静默消失、契约断言退化成"永远通过"（假绿）。
            // 光断言"数量 ≥ 30"抓不住它，所以这里点名要求组件里的字段也在扫描结果里。
            List<String> scannedNames = new ArrayList<>();
            for (ScalarSlot slot : scalars) {
                scannedNames.add(slot.field().getName());
            }
            check("前提：扫描范围下潜进了 @DataFlatten 组件（搬走的属性没从契约里静默消失）",
                    scannedNames.containsAll(java.util.Arrays.asList(

                            "metalManaGrow", "baseCriticalDamage",
                            "alive",
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
     * 属性加成容器（{@code PropertyEnhance}）：类型 + 百分比/固定值 + <b>来源</b>。
     * <p>
     * 它替掉了原来 13 个 {@code *Enhance*} 标量。这一组盯的是"换成条目之后必须成立的四条"：
     * 同一类型多来源<b>求和</b>、同（类型+来源）重复挂是<b>更新</b>不是累加、
     * 按来源摘除是<b>幂等</b>的（摘两次也不会减成负数 —— 旧写法 K1/K2/K4 的病根）、
     * 以及<b>不复制 / 整场清空</b>这两条生命周期。
     */
    private static void testPropertyEnhanceContainer() {
        section("属性加成容器（类型 + 来源 + 幂等）");
        try {
            Object sourceA = new Object();
            Object sourceB = new Object();

            // ① 同一类型、多个来源：求和
            LivingThing probe = new PlayerOne(125);
            probe.setCriticalRate(-1);
            probe.addPropertyEnhance(PropertyEnhanceType.ATTACK, 0.1, 0, sourceA);
            probe.addPropertyEnhance(PropertyEnhanceType.ATTACK, 0.2, 0, sourceB);
            check("① 同一类型多个来源求和（+10% 与 +20% → 30%）—— 实际 "
                            + probe.getPropertyEnhancePercent(PropertyEnhanceType.ATTACK),
                    Math.abs(probe.getPropertyEnhancePercent(PropertyEnhanceType.ATTACK) - 0.3) < 1e-9);

            // ② 同（类型 + 来源）重复挂 = 更新，不累加（K1：换实例不清旧加成，旧写法会永久 +x）
            probe.addPropertyEnhance(PropertyEnhanceType.ATTACK, 0.5, 0, sourceA);
            check("② 同来源重复挂是更新而不是累加（0.1 → 0.5，总和 0.7）—— 实际 "
                            + probe.getPropertyEnhancePercent(PropertyEnhanceType.ATTACK),
                    Math.abs(probe.getPropertyEnhancePercent(PropertyEnhanceType.ATTACK) - 0.7) < 1e-9
                            && probe.getPropertyEnhances().size() == 2);

            // ③ 按来源摘除 + 幂等（K2/K4：净化/重复收尾会多走的那些路径）
            boolean first = probe.removePropertyEnhance(sourceA);
            boolean second = probe.removePropertyEnhance(sourceA);
            check("③ 按来源摘除一次到位、再摘一次是空操作（不会把数值减成负数）—— "
                            + first + " / " + second + "，剩余总和 "
                            + probe.getPropertyEnhancePercent(PropertyEnhanceType.ATTACK),
                    first && !second
                            && Math.abs(probe.getPropertyEnhancePercent(PropertyEnhanceType.ATTACK) - 0.2) < 1e-9);

            // ④ 七个类型都真的落到对应公式上（每个类型一条）
            check("④ ATTACK 条目影响 getAttack()",
                    enhanceChanges(PropertyEnhanceType.ATTACK, LivingThing::getAttack));
            check("④ DEFENCE 条目影响 getDefence()",
                    enhanceChanges(PropertyEnhanceType.DEFENCE, LivingThing::getDefence));
            check("④ SPEED 条目影响 getSpeed()",
                    enhanceChanges(PropertyEnhanceType.SPEED, LivingThing::getSpeed));
            check("④ MAX_HP 条目影响 getHpMax()",
                    enhanceChanges(PropertyEnhanceType.MAX_HP, LivingThing::getHpMax));
            check("④ CRITICAL_RATE 条目影响 getCriticalRate()",
                    enhanceChanges(PropertyEnhanceType.CRITICAL_RATE, l -> (long) (l.getCriticalRate() * 1000)));
            check("④ CRITICAL_DAMAGE 条目影响 getCriticalDamage()",
                    enhanceChanges(PropertyEnhanceType.CRITICAL_DAMAGE, l -> (long) (l.getCriticalDamage() * 1000)));
            LivingThing healProbe = new PlayerOne(125);
            healProbe.setHp(healProbe.getHpMax() / 2);
            healProbe.addPropertyEnhance(PropertyEnhanceType.HEAL, 0.5, 0, "自测-治疗");
            check("④ HEAL 条目影响 heal()（+50% 时 heal(100) 回 150）—— 实际 " + healProbe.heal(100),
                    healProbe.heal(100) == 150);

            // ⑤ copy() 不带条目（临时属性：副本从干净状态开始）
            LivingThing template = new PlayerOne(125);
            template.addPropertyEnhance(PropertyEnhanceType.ATTACK, 0.5, 0, sourceA);
            check("⑤ copy() 不带属性加成条目（它是临时属性）—— 副本 "
                            + template.copy().getPropertyEnhances().size() + " 条",
                    template.copy().getPropertyEnhances().isEmpty());

            // ⑥ 整场结束清空
            template.whenFightEnds();
            check("⑥ whenFightEnds() 清空属性加成条目 —— 剩余 " + template.getPropertyEnhances().size() + " 条",
                    template.getPropertyEnhances().isEmpty());

            // ⑦ /data 视图：条目列表看得见（空列表会被省略，这是 /data 的既有口径）
            LivingThing dumpProbe = new PlayerOne(125);
            dumpProbe.addPropertyEnhance(PropertyEnhanceType.ATTACK, 0.25, 2, "自测-数据视图");
            Object dumped = DataBridge.toCompound(dumpProbe).get("propertyEnhances");
            check("⑦ /data 里看得见 propertyEnhances 及其字段（type / percent / amount）",
                    dumped instanceof NbtList list && list.size() == 1
                            && list.get(0) instanceof NbtCompound entry
                            && "ATTACK".equals(entry.get("type").asString())
                            && Math.abs(entry.get("percent").asDouble() - 0.25) < 1e-9
                            && Math.abs(entry.get("amount").asDouble() - 2) < 1e-9);

            // ⑧ 生命上限调小：当前生命要被夹回新上限（副作用没丢）
            PlayerOne hpProbe = new PlayerOne(125);
            long capBefore = hpProbe.getHpMax();
            hpProbe.addPropertyEnhance(PropertyEnhanceType.MAX_HP, -0.5, 0, "自测-削上限");
            check("⑧ 生命上限调小之后当前生命被夹回新上限 —— " + capBefore + " → "
                            + hpProbe.getHpMax() + "，血 " + hpProbe.getHp(),
                    hpProbe.getHpMax() < capBefore && hpProbe.getHp() == hpProbe.getHpMax());
        } catch (Exception e) {
            fail("属性加成容器测试抛出异常：" + e);
        }
    }

    /**
     * typed 属性加成：同一来源可以同时挂“全伤害”与“只对追加攻击”两条；
     * 精确移除只摘对应 tag，不把另一条也删掉。
     */
    private static void testTypedPropertyEnhanceContainer() {
        section("typed 属性加成（tag 并存 / 精确摘 / /data）");
        try {
            LivingThing probe = new PlayerOne(125);
            Object source = new Object();
            probe.addPropertyEnhance(PropertyEnhanceType.ATTACK, 0.1, 0, source);
            probe.addPropertyEnhance(PropertyEnhanceType.ATTACK, 0.2, 0, source, DamageType.AdditionAttack);
            check("① 同来源同类型、不同 tag 可以并存（2 条）—— 实际 "
                            + probe.getPropertyEnhances().size(),
                    probe.getPropertyEnhances().size() == 2);
            check("② 旧读取器只读未标注条目（0.1，不是 0.3）—— 实际 "
                            + probe.getPropertyEnhancePercent(PropertyEnhanceType.ATTACK),
                    Math.abs(probe.getPropertyEnhancePercent(PropertyEnhanceType.ATTACK) - 0.1) < 1e-9);
            check("③ 按追加攻击读取时 null 与 tag 都命中（0.3）—— 实际 "
                            + probe.getPropertyEnhancePercent(PropertyEnhanceType.ATTACK,
                            java.util.Set.of(DamageType.AdditionAttack)),
                    Math.abs(probe.getPropertyEnhancePercent(PropertyEnhanceType.ATTACK,
                            java.util.Set.of(DamageType.AdditionAttack)) - 0.3) < 1e-9);
            check("④ 非追加攻击只命中 null（0.1）—— 实际 "
                            + probe.getPropertyEnhancePercent(PropertyEnhanceType.ATTACK,
                            java.util.Set.of(DamageType.SpecialSkill)),
                    Math.abs(probe.getPropertyEnhancePercent(PropertyEnhanceType.ATTACK,
                            java.util.Set.of(DamageType.SpecialSkill)) - 0.1) < 1e-9);

            boolean removedTag = probe.removePropertyEnhance(source, PropertyEnhanceType.ATTACK,
                    DamageType.AdditionAttack);
            check("⑤ 精确摘 tag 只删带 tag 的那条（剩 null 0.1）—— 实际 " + removedTag
                            + " / " + probe.getPropertyEnhances().size(),
                    removedTag && probe.getPropertyEnhances().size() == 1
                            && Math.abs(probe.getPropertyEnhancePercent(PropertyEnhanceType.ATTACK) - 0.1) < 1e-9);
            check("⑥ 精确摘 null 只删未标注条目（不会把 tag 当通配）",
                    probe.removePropertyEnhance(source, PropertyEnhanceType.ATTACK, null)
                            && probe.getPropertyEnhances().isEmpty());

            probe.addPropertyEnhance(PropertyEnhanceType.ATTACK, 0.4, 0, source, DamageType.AdditionAttack);
            probe.addPropertyEnhance(PropertyEnhanceType.ATTACK, 0.5, 0, source, DamageType.AdditionAttack);
            check("⑦ 同 tag 重复挂是更新而不是叠加（0.5）—— 实际 "
                            + probe.getPropertyEnhancePercent(PropertyEnhanceType.ATTACK,
                            java.util.Set.of(DamageType.AdditionAttack)),
                    probe.getPropertyEnhances().size() == 1
                            && Math.abs(probe.getPropertyEnhancePercent(PropertyEnhanceType.ATTACK,
                            java.util.Set.of(DamageType.AdditionAttack)) - 0.5) < 1e-9);

            Object dumped = DataBridge.toCompound(probe).get("propertyEnhances");
            check("⑧ /data 条目里看得见 damageType",
                    dumped instanceof NbtList list && list.size() == 1
                            && list.get(0) instanceof NbtCompound entry
                            && "AdditionAttack".equals(entry.get("damageType").asString()));

            probe.addDamageTypeEnhance(DamageType.AdditionAttack, 0.8, source);
            check("⑨ 按伤害类型的增伤是独立列表，且只命中对应类型 —— 实际 "
                            + probe.getDamageTypeEnhances().size() + " 条 / "
                            + probe.getDamageEnhance(cn.gfhnv.game.system.ElementSort.METAL,
                            java.util.Set.of(DamageType.AdditionAttack)),
                    probe.getDamageTypeEnhances().size() == 1
                            && DataBridge.toCompound(probe).get("damageTypeEnhances") != null
                            && Math.abs(probe.getDamageEnhance(cn.gfhnv.game.system.ElementSort.METAL,
                            java.util.Set.of(DamageType.AdditionAttack)) - 0.8) < 1e-9
                            && Math.abs(probe.getDamageEnhance(cn.gfhnv.game.system.ElementSort.METAL,
                            java.util.Set.of(DamageType.SpecialSkill))) < 1e-9);
            check("⑩ 按伤害类型的增伤也按来源整批摘",
                    probe.removeDamageTypeEnhance(source) && probe.getDamageTypeEnhances().isEmpty());
        } catch (Exception e) {
            fail("typed 属性加成测试抛出异常：" + e);
        }
    }

    /**
     * 给实体挂一条 +50% 的某类型加成，判断某个读取器算出来的值是否变大。
     *
     * @param type   属性类型
     * @param getter 读最终值的函数（返回值可能是 long 或缩小过的比值）
     * @return 加成之后读数是否严格变大
     */
    private static boolean enhanceChanges(PropertyEnhanceType type,
                                          java.util.function.ToLongFunction<LivingThing> getter) {
        LivingThing probe = new PlayerOne(125);
        // 暴击率/爆伤的出厂基础值是 0，百分比加成乘 0 还是 0 —— 先给个非零基数再比
        probe.setCriticalRate(0.2);
        probe.setCriticalDamage(1.5);
        long before = getter.applyAsLong(probe);
        probe.addPropertyEnhance(type, 0.5, 0, "自测-单类型");
        return getter.applyAsLong(probe) > before;
    }

    /**
     * 实体 Builder（{@code LivingThing.builder(...)}）：<b>行为必须与 14 参位置构造器逐个字段一致</b>，
     * 必填项缺失要当场报错，{@code manaGrows(...)} 覆盖之后法力表要按新成长重建。
     */
    private static void testLivingThingBuilder() {
        section("实体 Builder（与 14 参构造器逐字段等价）");
        try {
            // ① 同一组参数，两条路造出来的实体逐字段相等
            LivingThing viaCtor = new LivingThing("构造器", "builderProbe",
                    0.1, 0.2, 0.3, 0.4, 0.5, 120L, 125L, "测试", 10, 20, 5, ElementSort.WATER);
            LivingThing viaBuilder = LivingThing.builder("构造器", "builderProbe")
                    .level(125).speed(120).type("测试")
                    .hpGrow(10).attackGrow(20).defenceGrow(5)
                    .metalResistance(0.3).woodResistance(0.4).waterResistance(0.2)
                    .fireResistance(0.1).dirtResistance(0.5)
                    .element(ElementSort.WATER)
                    .build();
            check("① 与 14 参构造器逐字段一致（hp/上限/攻击/防御/速度/暴击/成长/抗性/元素/等级）",
                    viaCtor.getHp() == viaBuilder.getHp()
                            && viaCtor.getHpMax() == viaBuilder.getHpMax()
                            && viaCtor.getAttack() == viaBuilder.getAttack()
                            && viaCtor.getDefence() == viaBuilder.getDefence()
                            && viaCtor.getSpeed() == viaBuilder.getSpeed()
                            && viaCtor.getCriticalRate() == viaBuilder.getCriticalRate()
                            && viaCtor.getHpGrow() == viaBuilder.getHpGrow()
                            && viaCtor.getAttackGrow() == viaBuilder.getAttackGrow()
                            && viaCtor.getDefenceGrow() == viaBuilder.getDefenceGrow()
                            && viaCtor.getMetalResistance() == viaBuilder.getMetalResistance()
                            && viaCtor.getWoodResistance() == viaBuilder.getWoodResistance()
                            && viaCtor.getWaterResistance() == viaBuilder.getWaterResistance()
                            && viaCtor.getFireResistance() == viaBuilder.getFireResistance()
                            && viaCtor.getDirtResistance() == viaBuilder.getDirtResistance()
                            && viaCtor.getElementSort() == viaBuilder.getElementSort()
                            && viaCtor.getLevel() == viaBuilder.getLevel());

            // ② 法力表也一致（元素决定那张成长表；水主元素拿 mainBase）
            boolean manasEqual = viaCtor.getManas().size() == viaBuilder.getManas().size();
            if (manasEqual) {
                for (int i = 0; i < viaCtor.getManas().size(); i++) {
                    if (viaCtor.getManas().get(i).getAmountMax()
                            != viaBuilder.getManas().get(i).getAmountMax()
                            || viaCtor.getManas().get(i).getElementSort()
                            != viaBuilder.getManas().get(i).getElementSort()) {
                        manasEqual = false;
                        break;
                    }
                }
            }
            check("② 五行法力表一致（条数 / 顺序 / 每条上限）", manasEqual);

            // ③ 控制器与描述
            LivingThing controlled = LivingThing.builder("带控制器", "builderProbe2")
                    .level(1).speed(100).element(ElementSort.METAL)
                    .description("Builder 造的")
                    .controller(new FixOrderController(new ArrayList<>(), null))
                    .build();
            check("③ controller / description 被带上",
                    controlled.getController() instanceof FixOrderController
                            && "Builder 造的".equals(controlled.getDescription()));

            // ④ 缺 element 当场报错
            boolean missingElement = false;
            try {
                LivingThing.builder("缺元素", "builderProbe3").speed(100).build();
            } catch (IllegalStateException expected) {
                missingElement = expected.getMessage().contains("element");
            }
            check("④ 缺 element：当场抛 IllegalStateException（而不是造出法力表空掉的半成品）", missingElement);

            // ⑤ 缺 speed 当场报错（speed=0 会在时间轴 10000/speed 炸）
            boolean badSpeed = false;
            try {
                LivingThing.builder("没速度", "builderProbe4").element(ElementSort.METAL).build();
            } catch (IllegalStateException expected) {
                badSpeed = expected.getMessage().contains("speed");
            }
            check("⑤ 缺 speed（或 0）：当场抛 IllegalStateException", badSpeed);

            // ⑥ manaGrows(...) 覆盖 → 法力表按新成长重建（拿"默认表"的同一只做对照）
            LivingThing defaultMana = LivingThing.builder("默认法力", "builderProbe5")
                    .level(11).speed(100).element(ElementSort.METAL).build();
            double defaultMetal = defaultMana.getManas().get(0).getAmountMax();
            LivingThing custom = LivingThing.builder("自定义法力", "builderProbe6")
                    .level(11).speed(100).element(ElementSort.METAL)
                    .manaGrows(7, 1, 2, 3, 4)
                    .build();
            check("⑥ manaGrows(...) 覆盖成长表之后法力表重建（金成长 20 → 7，上限正好差 (7−20)×(等级−1)）—— "
                            + defaultMetal + " → " + custom.getManas().get(0).getAmountMax(),
                    custom.getMetalManaGrow() == 7
                            && Math.abs((custom.getManas().get(0).getAmountMax() - defaultMetal) + 130) < 1e-9);

            // ⑦ 两次 build() 是独立实例（互不影响）
            LivingThing first = LivingThing.builder("独立甲", "builderProbe7")
                    .level(1).speed(100).element(ElementSort.METAL).build();
            LivingThing second = LivingThing.builder("独立乙", "builderProbe8")
                    .level(1).speed(100).element(ElementSort.METAL).build();
            first.setHp(1);
            check("⑦ 两次 build() 是独立实例（改一只的血不影响另一只）",
                    first.getHp() == 1 && second.getHp() == second.getHpMax());
        } catch (Exception e) {
            fail("实体 Builder 测试抛出异常：" + e);
        }
    }

    /**
     * 穿透容器（{@code ElementValue}：元素 + 平值 + <b>来源</b>）。
     * <p>
     * 2026-11-14 第 49 批把 {@code penetration} 标量与 5 个 {@code *Penetration} 死标量
     * 换成了一条列表。这一组盯五件事：基础条目、全属性/单元素的口径、
     * 同来源更新、按来源摘除幂等、以及"copy 只带基础 / 整场结束只清临时"。
     */
    private static void testPenetrationContainer() {
        section("穿透容器（ElementValue：元素 + 平值 + 来源）");
        try {
            LivingThing probe = new PlayerOne(125);
            probe.setPenetration(0.25);
            check("① 配置写的是「基础条目」（getPenetration == 0.25）—— 实际 " + probe.getPenetration(),
                    Math.abs(probe.getPenetration() - 0.25) < 1e-9);

            probe.addPenetration(null, 0.1, "自测-全属性");
            probe.addPenetration(ElementSort.DIRT, 0.3, "自测-土");
            check("② 全属性临时条目叠进 getPenetration（0.25 + 0.1）—— 实际 " + probe.getPenetration(),
                    Math.abs(probe.getPenetration() - 0.35) < 1e-9);
            check("③ 单元素条目只对该元素生效（土 0.3 / 火 0）—— 实际 "
                            + probe.getElementPenetration(ElementSort.DIRT) + " / "
                            + probe.getElementPenetration(ElementSort.FIRE),
                    Math.abs(probe.getElementPenetration(ElementSort.DIRT) - 0.3) < 1e-9
                            && Math.abs(probe.getElementPenetration(ElementSort.FIRE)) < 1e-9);

            probe.addPenetration(null, 0.2, "自测-全属性");
            check("④ 同来源重复挂是更新而不是累加（0.1 → 0.2）—— 实际 " + probe.getPenetration(),
                    Math.abs(probe.getPenetration() - 0.45) < 1e-9);

            probe.removePenetration("自测-全属性");
            probe.removePenetration("自测-全属性");
            check("⑤ 按来源摘除幂等（摘两次不会把数值减成负数）—— 实际 " + probe.getPenetration(),
                    Math.abs(probe.getPenetration() - 0.25) < 1e-9);

            LivingThing template = new PlayerOne(125);
            template.setPenetration(0.4);
            template.addPenetration(null, 0.3, "自测-临时");
            LivingThing copy = template.copy();
            check("⑥ copy() 只带基础条目（临时穿透不带）—— 副本 " + copy.getPenetration(),
                    Math.abs(copy.getPenetration() - 0.4) < 1e-9);

            template.whenFightEnds();
            check("⑦ 整场结束只清临时条目、基础值还在 —— 实际 " + template.getPenetration(),
                    Math.abs(template.getPenetration() - 0.4) < 1e-9);

            check("⑧ /data 里是一条 penetrations 列表（不再是标量 penetration）",
                    DataBridge.toCompound(template).get("penetrations") != null
                            && DataBridge.toCompound(template).get("penetration") == null);
        } catch (Exception e) {
            fail("穿透容器测试抛出异常：" + e);
        }
    }

    /**
     * 抗性容器（{@code ElementValue}）：5 条基础条目 + 临时加成。
     * <p>
     * 2026-11-14 第 50 批把 5 个 {@code *Resistance} 字段换成了一条列表（与穿透共用条目类）。
     * 这一组盯：基础值读写、全属性条目叠加、单元素口径（含弱点负数）、同来源更新、
     * 摘除幂等、{@code copy} 只带基础、"整场只清临时"、以及 {@code /data} 视图。
     */
    private static void testResistanceContainer() {
        section("抗性容器（ElementValue：5 条基础 + 临时加成）");
        try {
            LivingThing probe = new PlayerOne(125);
            // 火抗基值由模板决定（PlayerOne 的构造器给了值），所以下面按"基值 + 全属性条目"来断言
            double fireBase = probe.getResistance(ElementSort.FIRE);
            probe.setResistance(ElementSort.WATER, 0.3);
            check("① 基础抗性读写（水 0.3）—— 实际 " + probe.getResistance(ElementSort.WATER)
                            + " / getWaterResistance " + probe.getWaterResistance(),
                    Math.abs(probe.getResistance(ElementSort.WATER) - 0.3) < 1e-9
                            && Math.abs(probe.getWaterResistance() - 0.3) < 1e-9);

            probe.addResistance(null, 0.1, "自测-全属性");
            check("② 全属性条目叠加进该元素（0.3 + 0.1）—— 实际 " + probe.getResistance(ElementSort.WATER),
                    Math.abs(probe.getResistance(ElementSort.WATER) - 0.4) < 1e-9);

            double metalBase = probe.getResistance(ElementSort.METAL);
            probe.addResistance(ElementSort.METAL, -0.2, "自测-金弱点");
            check("③ 单元素条目只对该元素生效，且允许负数（弱点）—— 金 " + metalBase + " → "
                            + probe.getResistance(ElementSort.METAL),
                    Math.abs(probe.getResistance(ElementSort.METAL) - (metalBase - 0.2)) < 1e-9
                            && Math.abs(probe.getResistance(ElementSort.FIRE) - (fireBase + 0.1)) < 1e-9);

            probe.addResistance(null, 0.3, "自测-全属性");
            check("④ 同来源重复挂是更新而不是累加（0.1 → 0.3）—— 实际 "
                            + probe.getResistance(ElementSort.WATER),
                    Math.abs(probe.getResistance(ElementSort.WATER) - 0.6) < 1e-9);

            probe.removeResistance("自测-全属性");
            probe.removeResistance("自测-全属性");
            check("⑤ 按来源摘除幂等（摘两次不会把数值减成负数）—— 实际 "
                            + probe.getResistance(ElementSort.WATER),
                    Math.abs(probe.getResistance(ElementSort.WATER) - 0.3) < 1e-9);

            LivingThing template = new PlayerOne(125);
            template.setResistance(ElementSort.DIRT, 0.4);
            template.addResistance(null, 0.3, "自测-临时抗性");
            LivingThing copy = template.copy();
            check("⑥ copy() 只带基础条目（临时抗性不带）—— 副本 " + copy.getResistance(ElementSort.DIRT),
                    Math.abs(copy.getResistance(ElementSort.DIRT) - 0.4) < 1e-9);

            template.whenFightEnds();
            check("⑦ 整场结束只清临时条目、基础值还在 —— 实际 " + template.getResistance(ElementSort.DIRT),
                    Math.abs(template.getResistance(ElementSort.DIRT) - 0.4) < 1e-9);

            check("⑧ /data 里是一条 resistances 列表（不再是 5 个标量键）",
                    DataBridge.toCompound(template).get("resistances") != null
                            && DataBridge.toCompound(template).get("metalResistance") == null);
        } catch (Exception e) {
            fail("抗性容器测试抛出异常：" + e);
        }
    }

    /**
     * 增伤容器（{@code ElementValue}）：全是临时条目、没有基础值。
     */
    private static void testDamageEnhanceContainer() {
        section("增伤容器（ElementValue：全是临时条目）");
        try {
            LivingThing probe = new PlayerOne(125);
            probe.addDamageEnhance(null, 0.1, "自测-全属性");
            probe.addDamageEnhance(ElementSort.FIRE, 0.3, "自测-火");
            check("① 单元素条目只对该元素生效 —— 火 " + probe.getElementDamageEnhance(ElementSort.FIRE)
                            + " / 金 " + probe.getElementDamageEnhance(ElementSort.METAL),
                    Math.abs(probe.getElementDamageEnhance(ElementSort.FIRE) - 0.3) < 1e-9
                            && Math.abs(probe.getElementDamageEnhance(ElementSort.METAL)) < 1e-9);
            probe.addDamageEnhance(ElementSort.FIRE, 0.5, "自测-火");
            check("② 同（元素 + 来源）重复挂是更新而不是累加 —— 实际 "
                            + probe.getElementDamageEnhance(ElementSort.FIRE),
                    Math.abs(probe.getElementDamageEnhance(ElementSort.FIRE) - 0.5) < 1e-9);
            probe.removeDamageEnhance("自测-火");
            probe.removeDamageEnhance("自测-火");
            check("③ 按来源摘除幂等（摘两次不会减成负数）—— 实际 "
                            + probe.getElementDamageEnhance(ElementSort.FIRE),
                    Math.abs(probe.getElementDamageEnhance(ElementSort.FIRE)) < 1e-9);
            LivingThing template = new PlayerOne(125);
            template.addDamageEnhance(null, 0.4, "自测-临时");
            check("④ copy() 不带增伤条目（它是临时属性）—— 副本 "
                            + template.copy().getDamageEnhances().size() + " 条",
                    template.copy().getDamageEnhances().isEmpty());
            template.whenFightEnds();
            check("⑤ whenFightEnds() 清空增伤条目 —— 剩余 " + template.getDamageEnhances().size() + " 条",
                    template.getDamageEnhances().isEmpty());
            template.addDamageEnhance(null, 0.2, "自测-数据视图");
            check("⑥ /data 里是一条 damageEnhances 列表（不再是 5 个标量键）",
                    DataBridge.toCompound(template).get("damageEnhances") != null
                            && DataBridge.toCompound(template).get("metalDamageEnhance") == null);
        } catch (Exception e) {
            fail("增伤容器测试抛出异常：" + e);
        }
    }

    /**
     * 效果容器（{@code EffectContainer}）：列表<b>只读</b>，结构性修改只能走 API。
     */
    private static void testEffectContainer() {
        section("效果容器（只读列表 + API）");
        try {
            PlayerOne probe = new PlayerOne(125);
            boolean addBlocked = false;
            try {
                probe.getEntityEffectList().add(
                        new cn.gfhnv.game.officialStuff.customEffect.universalEffects.Taunt(2));
            } catch (UnsupportedOperationException expected) {
                addBlocked = expected.getMessage().contains("只读");
            }
            check("① 直接 add 到列表上被拒（异常文案点明只读）", addBlocked);

            boolean clearBlocked = false;
            try {
                probe.getEntityEffectList().clear();
            } catch (UnsupportedOperationException expected) {
                clearBlocked = expected.getMessage().contains("只读");
            }
            check("② 直接 clear 被拒", clearBlocked);

            probe.addEffectDirect(new cn.gfhnv.game.officialStuff.customEffect.universalEffects.Taunt(2));
            check("③ addEffectDirect 能追加（列表只读不等于不能加效果）—— 实际 "
                            + probe.getEntityEffectList().size() + " 个",
                    probe.getEntityEffectList().size() == 1);
            check("④ 列表仍然可以正常遍历/读取（stream 不受影响）",
                    probe.getEntityEffectList().stream().anyMatch(
                            e -> e instanceof cn.gfhnv.game.officialStuff.customEffect.universalEffects.Taunt));
            boolean removed = probe.removeEffectsIf(
                    e -> e instanceof cn.gfhnv.game.officialStuff.customEffect.universalEffects.Taunt);
            check("⑤ removeEffectsIf 能批量删（净化/收尾都走它）—— 删掉 " + removed + "，剩余 "
                            + probe.getEntityEffectList().size(),
                    removed && probe.getEntityEffectList().isEmpty());
            probe.addEffectDirect(new cn.gfhnv.game.officialStuff.customEffect.universalEffects.Taunt(2));
            probe.clearEffects();
            check("⑥ clearEffects 能整表清空", probe.getEntityEffectList().isEmpty());
        } catch (Exception e) {
            fail("效果容器测试抛出异常：" + e);
        }
    }

    /**
     * <b>三个面</b>的契约（2026-11-14 第 60 批）：{@code @Temporary} = `/data` 能看能写、配置<u>不能</u>写。
     * <p>
     * 这条守卫把"临时属性"从注释变成<b>机器可查</b>的一类字段：
     * 标了 {@code @Temporary} 的字段必须在 {@code /data} 的数据名清单里、且不在可配置键里；
     * 反过来，任何一个"既想让人在配置里写、又标了临时"的字段都会在这里红。
     */
    private static void testTemporaryAnnotationContract() {
        section("三面注解：@Temporary = /data 可见、配置不可写");
        try {
            List<String> problems = new ArrayList<>();
            List<String> temporaryNames = new ArrayList<>();
            for (Class<?> type : List.of(PlayerOne.class, cn.gfhnv.game.entity.AttributeProfile.class)) {
                for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                    if (!field.isAnnotationPresent(cn.gfhnv.game.data.Temporary.class)) {
                        continue;
                    }
                    String name = cn.gfhnv.game.data.DataBridge.dataName(field);
                    temporaryNames.add(name);
                    if (cn.gfhnv.game.system.configLoadingSystem.DataKeys.isConfigurable(name)) {
                        problems.add(name + "（标了 @Temporary 却还能配）");
                    }
                    if (!cn.gfhnv.game.data.DataBridge.dataNames(LivingThing.class).contains(name)
                            && !cn.gfhnv.game.data.DataBridge.dataNames(cn.gfhnv.game.entity.AttributeProfile.class).contains(name)) {
                        problems.add(name + "（标了 @Temporary 却不在 /data 的数据名里）");
                    }
                    if (field.getAnnotation(cn.gfhnv.game.data.Temporary.class).reason().isBlank()) {
                        problems.add(name + "（@Temporary 没写理由）");
                    }
                }
            }
            check("① 标了 @Temporary 的字段：都能被 /data 看到、都不可配置、都写了理由 —— 问题 " + problems
                            + "，实扫 " + temporaryNames,
                    problems.isEmpty() && !temporaryNames.isEmpty());
            check("② 四个临时容器都在名单里（属性加成 / 穿透 / 抗性 / 增伤）—— 实际 " + temporaryNames,
                    temporaryNames.containsAll(List.of("propertyEnhances", "penetrations",
                            "resistances", "damageEnhances")));
            // /data 读得到、配置键表里查不到：用配置面那条反射（candidates）再确认一次
            List<String> inConfigFace = new ArrayList<>();
            for (cn.gfhnv.game.data.DataBridge.DataAccessor accessor
                    : cn.gfhnv.game.system.configLoadingSystem.ReflectionConfigBridge.candidates(
                    new PlayerOne(125))) {
                if (temporaryNames.contains(accessor.name())) {
                    inConfigFace.add(accessor.name());
                }
            }
            check("③ 反射配置面（candidates）里一个 @Temporary 都没有 —— 漏进来的 " + inConfigFace,
                    inConfigFace.isEmpty());
        } catch (Exception e) {
            fail("三面注解契约测试抛出异常：" + e);
        }
    }

    /**
     * 表现层搬家（2026-11-15 第 63 批）：{@code LivingThing} 的名字拼装转发到
     * {@code EntityDisplay}，两者必须逐字一致；实体类本身不再有打印代码。
     */
    private static void testEntityDisplayDelegates() {
        section("表现层：名字拼装转发到 EntityDisplay");
        try {
            PlayerOne probe = new PlayerOne(125);
            probe.setName("显示探针");
            check("① getNameWithUuid 与 EntityDisplay 一致 —— " + probe.getNameWithUuid(),
                    probe.getNameWithUuid().equals(
                            cn.gfhnv.game.entity.EntityDisplay.nameWithUuid(probe)));
            check("② 不在战斗里时 getNameWithSide 只返回名字 —— " + probe.getNameWithSide(),
                    probe.getNameWithSide().equals("显示探针")
                            && probe.getNameWithUuidAndSide()
                            .equals(cn.gfhnv.game.entity.EntityDisplay.nameWithUuidAndSide(probe)));
            check("③ 名字里带短标识（#{6} 位短 uuid）",
                    probe.getNameWithUuid().startsWith("显示探针#")
                            && probe.getNameWithUuid().length() == "显示探针#".length() + 6);
        } catch (Exception e) {
            fail("表现层转发测试抛出异常：" + e);
        }
    }

    /**
     * 玩家角色的<b>基础暴击率 5%</b>（2026-11-15 第 71 批，用户定）：
     * 只有"默认用 PlayerController 的玩家实体"有，怪物 / 召唤物保持 0（它们没有暴击机制）。
     */
    private static void testPlayerBaseCriticalRate() {
        section("玩家角色：基础暴击率 5%");
        try {
            List<String> noCrit = new ArrayList<>();
            for (LivingThing player : List.of(new PlayerOne(125),
                    new cn.gfhnv.game.officialStuff.customEntity.players.ActorLiXiaoYan(125),
                    new cn.gfhnv.game.officialStuff.customEntity.players.Phainon(125),
                    new cn.gfhnv.game.officialStuff.customEntity.players.MortenaxBlade(125))) {
                if (Math.abs(player.getCriticalRate() - 0.05) > 1e-9
                        || Math.abs(player.getCriticalDamage() - 0.5) > 1e-9) {
                    noCrit.add(player.getName() + "（暴击率 " + player.getCriticalRate()
                            + " / 暴伤 " + player.getCriticalDamage() + "）");
                }
            }
            check("① 四个玩家角色出厂基础暴击率 5% + 基础暴伤 50% —— 不符合的 " + noCrit, noCrit.isEmpty());
            LivingThing monster = new cn.gfhnv.game.officialStuff.customEntity.monsters.CommonInsect(125L);
            check("② 怪物没有暴击（保持 0）—— 实际 " + monster.getCriticalRate(),
                    Math.abs(monster.getCriticalRate()) < 1e-9);
            LivingThing withEnhance = new PlayerOne(125);
            withEnhance.addPropertyEnhance(PropertyEnhanceType.CRITICAL_RATE, 0.1, 0, "自测-暴击加成");
            // 暴击率/暴伤的“+X%”按绝对值叠加（HSR 口径）：5% + 10% = 15%。
            check("③ 暴击率百分比加成是绝对值（0.05 + 0.1）—— 实际 " + withEnhance.getCriticalRate(),
                    Math.abs(withEnhance.getCriticalRate() - 0.15) < 1e-9);
            withEnhance.addPropertyEnhance(PropertyEnhanceType.CRITICAL_DAMAGE, 0.5, 0, "自测-暴伤加成");
            check("④ 暴伤百分比加成也是绝对值（0.5 + 0.5）—— 实际 " + withEnhance.getCriticalDamage(),
                    Math.abs(withEnhance.getCriticalDamage() - 1.0) < 1e-9);
        } catch (Exception e) {
            fail("玩家暴击率测试抛出异常：" + e);
        }
    }

    /**
     * 跑本主题的全部用例（顺序 = 下面列的顺序）。
     */
    public static void runAll() throws Exception {
        testLivingThingAttributeContract();
        testPropertyEnhanceContainer();
        testTypedPropertyEnhanceContainer();
        testLivingThingBuilder();
        testPenetrationContainer();
        testResistanceContainer();
        testDamageEnhanceContainer();
        testEffectContainer();
        testTemporaryAnnotationContract();
        testEntityDisplayDelegates();
        testPlayerBaseCriticalRate();
        testControllerCopy();
    }
}
