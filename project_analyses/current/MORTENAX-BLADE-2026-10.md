# 「千冶·刃」角色实现评估与施工单（2026-10-24 版）

> **文档分类**：[D2] 分析 · 已落地 ｜ **这是什么**：千冶·刃的实现评估与施工单（P0/P1 全部落地） ｜ **读者**：引擎开发者 / 内容作者 ｜ **时效**：2026-10-24 快照；已实现

> **这是什么**：把 `TODO.md` 里作者整理的**千冶·刃（`Mortenax Blade`）设计稿**落到这个引擎上的
> 可行性评估 + 施工单。**本文不是设计稿**（设计稿在 `TODO.md`，中英两版，**以中文数值为准**），
> 而是回答"这套 kit 里哪些引擎已经支持、哪些要新建、按什么顺序做、代价多大"。
>
> **谁要读**：要动 `officialStuff/customEntity/players/MortenaxBlade.java`、要加"结界/倒计时/能量"
> 这类机制的人，以及**下一个想从头实现这个角色的 AI**。读完这一份就够。
>
> **一句话结论**：**能做，而且没有一处需要改核心公式** —— 但设计里 5 条机制的载体
> （能量 / 充能 / 结界 / 倒计时 / 治疗量加成）引擎里原本都没有，底座已经先落地 2 个
> （`CountdownEntity`、`healEnhancePercent`），剩下 3 个按 P0 施工单做。

---

## 零、TL;DR

| 维度 | 判断 | 关键理由 |
|---|---|---|
| **可行性** | **高** | 伤害按生命上限算（`hpMagnification`）已有、嘲讽已有、换技能表已有先例（白厄觉醒）、免死已有先例、时间轴可插"假实体" |
| **代价** | **中**：约 8 个新类 + 1 个实体改写 | 大头是"结界状态机"和"每回合触发的天赋"，不是数值 |
| **风险最高的一处** | **"受到的伤害提高 50%"** | `LivingThing#getDamageTakenMultiplier()` **夹在 `[0,1]`**，负数减伤表达不了增伤（见 §三.3） |
| **建议** | **P0 骨架 → P1 天赋 → P2 命座（只做 E1/E2）** | 每期都能单独验、单独停 |

**已经落地的底座（本文写的当天完成，有自测）**：

| 底座 | 文件 | 自测 |
|---|---|---|
| 通用倒计时实体 | `src/cn/gfhnv/game/entity/CountdownEntity.java`（新增） | `testCountdownEntity`（7 条） |
| 治疗量加成 + 统一治疗入口 | `AttributeProfile#healEnhancePercent`（第 13 个运行时加成）、`LivingThing#heal(long)` | `testHealEnhancePercent`（6 条） |

---

## 一、设计稿拆成 12 条机制 × 引擎现状

> 设计原文见 `TODO.md:15-170`。**数值以中文版为准**（用户 2026-10-24 拍板）。

| # | 机制（设计原文摘要） | 引擎现状 | 结论 |
|---|---|---|---|
| 1 | 伤害按**生命上限的 %** 算（50% / 100% / 350% / 72% + 24%×4） | `Skill#hpMagnification` → `DamageCalculate:93` | ✅ 直接用 |
| 2 | 两个单体普攻，命中后使目标**嘲讽 1 回合** | `Taunt` 效果 + `TargetStrategies.tauntAware`（默认策略已自带嘲讽感知） | ✅ 直接用 |
| 3 | 终结技展开**结界**，结界期间换状态 | 没有"zone"概念 | ⚠️ `Effect` 挂状态 + `CountdownEntity` 做倒计时 |
| 4 | 【无量忿怒】暴击率 +20%、暴击伤害 +60% | `criticalRateEnhancePercent` / `criticalDamageEnhancePercent` | ✅ 直接用 |
| 5 | 强化普攻、**解放战技**、获得**全新终结技** | 白厄觉醒就是 `getController().setSkills(...)` 换表 | ✅ 有先例（`UltimateAttack#comeToEffect`） |
| 6 | 受到致命攻击**不死**：解除结界、退出状态、回 50% 生命 | 白厄免死（`Phainon#soulscorchDeathWard` 锁 1 血 + `pendingLastAttack`） | ✅ 有先例 |
| 7 | 倒计时**固定 70 速度**，回合开始解除结界 | 时间轴只认 `LivingThing` 当行动者 | ✅ **底座已完成**（`CountdownEntity`） |
| 8 | 消耗生命上限 20% / 10% 施放，血不足降到 1 | `setHp` 有夹取；盗火行者已有"消耗生命召唤"的写法 | ✅ 可做 |
| 9 | 【煞火缠身】防御 **-30%**、受到伤害 **+50%** | 防御：`defenceLoss` ✓；受伤 +50%：**见 §三.3** | ⚠️ 一半要拍板 |
| 10 | 千锻魂：减伤 50% + **受到治疗 +50%** + 被打给充能（❌"被攻击概率提高"用户拍板不做） | 减伤：`damageReductions`（乘算）✓；治疗：**底座已完成**（`healEnhancePercent`） | ✅ 可做 |
| 11 | 万淬心：**我方目标伤害 +50%** | 可以给队友挂 `DamageEnhanceEffect`（先例：破容器之赏） | ✅ 可做（写法略糙，见 §五.2） |
| 12 | 我殁之前（E1）：敌方**全属性抗性 -20%** | 抗性字段没有 modifier 链 | ✅ **改为给自己加 `penetration` +20%**（用户拍板，见 §四.6） |

---

## 二、引擎里原本没有、需要新建的五样东西

| # | 缺什么 | 为什么现有机制顶不上 | 现在怎么办 |
|---|---|---|---|
| 1 | **能量 / 充能** | 游戏里只有五行 mana（回合头打印的"能量"其实是 mana，`FightTurnPastListener:198`）。设计的 160 能量上限、80 溢出、9/7 充能全是新资源 | 仿白厄 `coreflame`：实体自己的 int 字段 + `DataKeys.ClassConfig` 放行 + `/data` 键（用户拍板"能量用新字段"） |
| 2 | **结界（zone）** | 没有"某状态持续期间"的通用载体 | `Effect`（扛"结界期间"判定）+ `CountdownEntity`（扛"到点结束"） |
| 3 | **倒计时** | 白厄的额外回合行动者是**本体**，和结界倒计时混在一条轴上会互相污染（排序 / 延后 / `getNextTurnOf`） | ✅ **已完成**：`CountdownEntity`，不进 `fighterList`/`enemiesList` |
| 4 | **受到的治疗量 +N%** | 全项目没有治疗倍率钩子（治疗就是 `setHp(hp + n)`） | ✅ **已完成**：第 13 个运行时加成 `healEnhancePercent` + `LivingThing#heal(long)` |
| 5 | **受到的伤害 +N%** | `getDamageTakenMultiplier()` 夹在 `[0,1]`，负数表达不了增伤 | ⚠️ 见 §三.3，**本条待拍板** |

---

## 三、三处需要特别小心的地方（逐条带证据）

### 3.1 接进回合循环的倒计时有三条硬约束

`FightTurnPastListener:176、198、213-228` 逐行核过：

1. **控制器不能是 `null`** —— 回合开始会遍历 `getLivingThing().getController().getSkills()` 递减冷却，
   空指针会直接崩；→ 给一个空的 `UniversalController`。
2. **`ActionSignal.SKIP_WITHOUT_NEW_TURN`** —— 不调 `act`、也不给自己排下一回合，
   只在 `lastExecuteList` 里执行回调（`CountdownEntity#schedule()` 已设）。
3. **`setExtra(true)`** —— 否则 `TurnManager#getNextTurnOf` 会把它当成"这个生物的下一次正常行动"，
   "延后 / 冰冻"那套就会改到倒计时头上。
4. **（2026-10-24 补，踩过）必须有正 HP** —— `LivingThing#isAlive()` 是按 `hp <= 0` 判死的，
   `TurnManager#removeTheDeath()` 每回合摘"行动者已死"的条目：HP=0 的倒计时刚上轴就被摘掉，
   结界永远不会到点（`CountdownEntity` 构造器里 `setHpMax(1); setHp(1);`）。

已知观感副作用（不影响正确性）：回合头会打印它的 HP 与全 0 的五行能量；它的回合里
`CommandManager.followActor` 会把 `@s` 指向它。

### 3.2 "结界期间"的判定不要写成"倒计时实体还活着"

倒计时的生命周期和结界未必同步（免死会**提前**解除结界 → 条目被 `cancel()`，
但"结界期间"的判定还可能在别的代码路径里被读）。**判据应该是实体自己身上的状态位 / Effect**，
`CountdownEntity` 只负责"到点触发一次回调"。

### 3.3 "受到的伤害 +50%" —— 唯一一处可能逼着改核心公式

```java
// LivingThing:1704-1710
public double getDamageTakenMultiplier() {
    double multiplier = 1;
    for (DamageReduction reduction : damageReductions) {
        multiplier *= (1 - reduction.percent());
    }
    return Math.max(0, Math.min(1, multiplier));   // ← 夹在 [0,1]
}
```

**负数减伤表达不了增伤**（`1 - (-0.5) = 1.5` 会被 `Math.min(1, …)` 夹回 1）。三条路：

| 方案 | 做法 | 代价 |
|---|---|---|
| A（最小） | 只做防御 -30%，"+50% 受伤"先不做 | 少一条机制，但不碰核心公式 |
| **B（✅ 2026-10-24 已采纳）** | **不新增列表**，直接把"负数减伤 = 增伤"做进现有那张表：`addDamageReduction` 钳制改 `[-1,1]` 且不再丢负数；`getDamageTakenMultiplier` 去掉上界 | 影响**所有**伤害计算 —— 已跑全量回归（1022 条全绿），`percent` 语义定成"正=减伤（最多减到 0）、负=增伤（最多翻倍）" |
| C | 算在攻击方头上（给攻击者临时加增伤） | 语义漂移（"目标易伤"变成"攻击者增伤"），多目标时不对 |

**决定：走 B**（用户 2026-10-24 拍板）。⚠️ 改这个语义必须<b>同时</b>改两处：
`addDamageReduction` 的钳制与"判零"、`getDamageTakenMultiplier` 的上夹取 ——
只改后者，负数会在入口就被静默丢掉（本轮就是这么踩的，见 `20-MISTAKES.md`）。

---

## 四、用户已拍板的七条（2026-10-24）

| # | 决定 | 落点 |
|---|---|---|
| 1 | **数值以中文为准**（50% / 100% / 350% / 72% + 24%×4、防御 -30%、受伤 +50%、2 回合…） | 技能常量 + `SkillData.json` |
| 2 | **要 AI 可控** | 技能实现 `IAiScoreBonus` + `TagConfig.json` 补条目（**当前没有他的条目** → 挂 AI 会"没有思维标签，随机行动"） |
| 3 | **能量用新字段** | 仿白厄 `coreflame`：`energy` / 溢出上限 80 / 充能 `charge` —— ⚠️ **充能没有上限**，`chargeMax` 是"每次触发需要的点数"（9，E2 → 7），81 点能连打 9 次。**获得途径**（设计稿英文版 `Energy gain`）：普攻 +20、强化普攻 +20、战技 +30、两个终结技各 +5；外加【因果尽偿】+27 |
| 4 | **写一个通用倒计时实体** | ✅ 已完成 |
| 5 | **治疗量 +50% 要做**，加"治疗量加成"战斗属性字段 | ✅ 已完成（方案 A：第 13 个运行时加成） |
| 6 | **敌方全抗 -20% 改成加穿透** | 用现成 `penetration`（全属性穿透）给自己，**不动**敌方抗性字段 |
| 7 | **星魂只做到 2 魂** | E1（我殁之前）+ E2（心若死灰：充能上限 9 → 7）；E4 / E6 不做 |

---

## 五、施工单

### P0 骨架（能打完一局）

| 文件 | 内容 |
|---|---|
| ~~`MortenaxBlade`（改写现有 35 行骨架）~~ ✅ **2026-10-24 已完成** | 速度 **107**、`energy` / `energyMax` / `energyOverflowMax` / `charge` / `chargeMax` 五键登记 `CLASS_CONFIG`、`/data` 可见、`classState` 可配、开战 75% 保底、满能解负面、付终结技保溢出、战斗结束清零；守卫 `testMortenaxBladeResources`（12 条） |
| ~~`MortalBladeCommonAttack`（残锋，掠尽）~~ ✅ **2026-10-24 已完成** | 单体，`hpMagnification = 0.5`，命中挂 `Taunt(1)`；实测伤害 1851。**普通形态的普攻** |
| ~~`MortalBladeSkill`（淬锋，断魄）~~ ✅ **2026-10-24 已完成** | 单体，`hpMagnification = 1.0`，命中挂 `Taunt(1)`；实测 3703（正好 1:2）。⚠️ **它是【无量忿怒】的强化普攻**（用户 2026-10-24 纠正；英文版 `A Tempered Blade Severs Souls` 也是强化普攻栏），**不在普通形态技能表里**，且 `canUse` 要求处于结界 |
| ~~`FornaxExCorpore`（骸骨当炉，血肉即薪）~~ ✅ **2026-10-24 已完成** | 全体【煞火缠身】→ 扣 20% 生命上限 → 付 160 能量 → `beginInfiniteFury`（换技能表 + 暴击效果 + 70 速倒计时）；免死走 `IModifyDamage` |
| ~~`TenaxPerIgnem`（千冶铸一，万劫烬灭）~~ ✅ **2026-10-24 已完成** | 全体，`hpMagnification = 3.5`；门槛：结界内 + 能量满；施放后付掉 160。**额外机制（用户追加，设计稿没有）**：打完**生命恢复至上限**（续航）；⚠️ 只挂这一招，开结界的「骸骨当炉」不回血 |
| ~~`BladeUnderBurial`（刃下，归葬）~~ ✅ **2026-10-24 已完成**（追打与白厄反击同款：取**表里带配置的那份**的副本 + 直接 `comeToEffect`，不走 `use()`） | 全体 0.72 + 4 段随机单体 0.24；扣 10% 生命上限；门槛：结界内且血 > 1 |
| ~~`InfiniteFuryEffect`（无量忿怒）~~ ✅ **2026-10-24 已完成** | 暴击率 +0.2 / 暴击伤害 +0.6，进出成对加减 |
| ~~`BalefireBindEffect`（煞火缠身）~~ ✅ **2026-10-24 已完成（防御那半）** | 防御 -30%（`defenceLoss`）；⚠️ **"+50% 受伤"仍未做**（§三.3 等你拍板） |
| ~~`BladeChargeListener`~~ ✅ **2026-10-24 已完成** | `MortalBladeChargeListener`（带 owner、每实例一个、战后注销）：我方打敌方 → +1 充能 |

**P0 状态（2026-10-24）：全部完成** —— `testMortenaxBladeResources` / `testMortalBladeSkills` /
`testInfiniteFuryZone` / `testMortalBladeAiPriority` / `testMortalBladeCharge` 共 34 条守卫，
基线 `通过 1015 条，失败 0 条`、`src` 236 文件。~~唯一没做的一条是"煞火缠身：受到伤害 +50%"~~
**2026-10-24 已补齐**（§三.3 采纳方案 B：负数减伤 = 增伤），P0 的功能面没有欠账。

### P1 天赋层（✅ 2026-10-24 已完成）

千锻魂（受击给充能 + 减伤 50% + 治疗 +50%）、万淬心（队友 `DamageEnhanceEffect`）、
因果尽偿（9 充能 → 回能 + 额外战技）、80 溢出能量与"能量满则清自身负面"、E1 的"倒计时延后 15%"
（`CountdownEntity#delayByPercent(0.15)` 就是为它准备的）。

### P2 命座（只 E1 / E2）

E1 = 自身 `penetration +0.2` + 天赋额外战技后倒计时延后 15%；E2 = 充能上限 9 → 7。
E4 / E6 **不做**（用户拍板）。

### 自测清单（预计 +25 条）

能量/充能边界（0 / 上限 / 溢出 80）、结界开关与倒计时联动（进场 → 到点 → 退出且攻击力回落）、
免死只触发一次、换技能表后的技能集合、`/data` 键、AI 评分与 `TagConfig` 生效、
E1 / E2 数值、配置项生效（`SkillData.json` 改倍率后伤害真的变）。
每条都要有"改之前是错的"的反例 —— 例如"结界到点后暴击伤害必须降回原值"。

---

## 六、风险与取舍（诚实清单）

1. **平衡**：这一套增益（我方 +50% 伤害、敌方全抗 -20%、自身 50% 减伤 + 治疗 +50% + 暴击 +20%/+60%）
   叠起来比现有所有角色都猛。**本文不动数值**，但建议 P0 先不接"我方增伤 / 全抗削减"。
2. **两个 "12 → 13"**：治疗量加成变成第 13 个运行时加成后，`REFLECTION_ONLY_SCALAR_KEYS`、
   `temporaryAttributes`、"12 个 `*Enhance*`"的文字都一起改了 —— 以后再加第 14 个请照这条链改。
3. **`mods/` 里 3 处治疗没走 `heal(long)`**（醉剑仙两个物品 + 灯笼剑）：拿不到治疗加成，行为与以前一致。
4. **AI 权重缺失**：`TagConfig.json` 今天**没有**千冶·刃的条目，P0 必须补，否则他当 AI 就是"随机行动"。
5. **速度 107 vs 125**：设计写 107，代码与 `EntityData.json` 现在是 125 —— 改的时候**两处一起改**，
   否则配置会盖掉代码。
6. **观感**：倒计时的回合里 `@s` 指向它；回合头会多打一行它自己的 HP/能量。

---

## 七、本文没做的事 / 不确定项

1. **没开始写角色本体**：本文只落地了两个底座（`CountdownEntity` + `healEnhancePercent`），
   `MortenaxBlade.java` 仍是 35 行骨架。
2. **"受到的伤害 +50%"没定**（§三.3 的 A/B/C 三选一，建议 A）。
3. **数值只按中文版**；英文版（55% / 110% / 378% / 79.2% + 26.4%×4，还有 Lv7 / Lv12 与 Break）
   **没有采用**，也没核对哪一版更新。
4. **"按生命上限算伤害"的暴击/穿透口径**：`DamageCalculate` 已有，但"生命上限 % 型技能吃到哪些乘区"
   我没逐个核对（做 P0 第一个技能时顺手验一编）。
5. **命座 E4 / E6、额外能力"锻打遗恨"**：按用户拍板不做。

---

## 附录 A：本文数字怎么复现

```powershell
# 现状基线（本文写作时）
.\check-sources.ps1                      # CHECK OK / Java files: 228
java -cp .\out\llmcheck;.\lib\json-20231013.jar cn.gfhnv.debug_tools.TestCommandSystem
#   → 自测结束：通过 971 条，失败 0 条

# 关键代码位置
#   时间轴排序        src/cn/gfhnv/game/system/fight/TurnManager.java:36-47
#   回合循环          src/cn/gfhnv/game/eventListener/FightTurnPastListener.java:162-240
#   承伤倍率（夹 0..1） src/cn/gfhnv/game/entity/LivingThing.java:1704-1710
#   生命上限型伤害     src/cn/gfhnv/game/damage/DamageCalculate.java:93
#   换技能表的先例     src/cn/gfhnv/game/officialStuff/customSkill/phainonSkills/normalSkills/UltimateAttack.java:88-91
#   免死的先例        src/cn/gfhnv/game/officialStuff/customEntity/players/Phainon.java（soulscorchDeathWard）
```

---

*本文由 AI（DeepSeek）生成，2026-10-24。设计稿作者：仓库作者（`TODO.md`）。*
