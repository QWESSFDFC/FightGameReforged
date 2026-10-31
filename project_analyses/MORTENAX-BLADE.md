# 千冶·刃（Mortenax Blade）

> **文档分类**：[C] 内容 ｜ **这是什么**：角色文档：千冶·刃的玩法、机制、调参、验证、已知差异 ｜ **读者**：玩家 / 内容作者 ｜ **时效**：随角色更新（2026-10-24 起）

> 一个**以生命上限为倍率**、靠"开结界换技能表"打爆发的火属性角色。
> 代码：`src/cn/gfhnv/game/officialStuff/customEntity/players/MortenaxBlade.java`、
> `customSkill/mortenaxBlade/`、`customEffect/mortenaxBladeEffects/`、
> `customEvent/mortenaxBladeEvents/`。
> 设计稿与施工记录：`TODO.md`、`project_analyses/MORTENAX-BLADE-2026-10.md`。
>
> 本文写给两类人：**想玩它的人**（§1~§6）和**想改它的人**（§7~§10）。

---

## 1. 一句话玩法

> 平常只放普攻攒能量 → 能量满 160 放【骸骨当炉，血肉即薪】**开结界** → 结界期间技能表整张换掉、
> 拿到 350% 生命上限的群攻终结技 → 结界被倒计时（70 速）或免死打断时退出，然后重复。

它**不是**"越打越强"的角色，而是**"窗口期爆发"**：结界开着的十来个行动内要把伤害打完。

---

## 2. 基础数值

| 项 | 值 | 说明 |
|---|---|---|
| 属性 | **火** | 抗性表 / 元素克制都按火算 |
| **速度** | **107** | 快于多数敌人；结界倒计时固定 70 速，**比本体慢**（所以倒计时通常在 1~2 个自己的回合后才到） |
| 生命 / 攻击 / 防御 | 按等级 + 规则表算 | 倍率走**生命上限**，所以**堆血就是堆伤害** |
| 名字 | `千冶·刃` | 中间的 `·` 是 U+00B7（GBK 可编码；别改成 U+2022 的 `•`，控制台会显示成 `?`） |

> ⚠️ **它的"倍率"单位是生命上限**（`hpMagnification`），不是攻击力。
> `EntityData.json` 里动 `baseHpMax` 会同时影响**血量与全部伤害**；动 `baseAttack` 对它的技能**几乎没用**。

---

## 3. 技能表：普通形态 vs 结界形态

结界一开，**整张技能表会被换掉**（`beginInfiniteFury` / `endInfiniteFury`）：

| 槽位 | 普通形态（2 条） | 【无量忿怒】形态（3 条） |
|---|---|---|
| 0 | **残锋，掠尽**（普攻，50% 生命上限，嘲讽 1 回合） | **淬锋，断魄**（强化普攻，100% 生命上限，嘲讽 1 回合） |
| 1 | **骸骨当炉，血肉即薪**（终结技，开结界） | **刃下，归葬**（战技，72% 全体 + 4×24% 随机，解锁） |
| 2 | — | **千冶铸一，万劫烬灭**（全新终结技，350% 全体） |

**为什么普通形态只有 2 条**：淬锋是"强化普攻"，它**只存在于结界形态**（所以普通形态下 `canUse` 直接返回 false）。

### 3.1 各技能的细节

**残锋，掠尽**（`MortalBladeCommonAttack`）
- 单体 50% 生命上限；命中使目标【嘲讽】1 回合；回能 **+20**。

**淬锋，断魄**（`MortalBladeSkill`）
- 单体 100% 生命上限；嘲讽 1 回合；回能 **+20**；**门槛：必须处于【无量忿怒】**。

**刃下，归葬**（`BladeUnderBurial`）
- 先扣**自身生命上限 10%**（不足则降到 1），再 72% 全体 + **4 段**各 24% 打随机单体；回能 **+30**。
- **门槛：结界内 且 血 > 1**（设计原文如此）。

**骸骨当炉，血肉即薪**（`FornaxExCorpore`）—— 开结界
- 全体挂【煞火缠身】（**防御 -30%、受伤 +50%**，2 回合）；
- 扣**自身生命上限 20%**（不足则降到 1）；
- **付 160 能量**（溢出部分保留），回能 **+5**；
- 进【无量忿怒】：暴击率 +20%、暴击伤害 +60%、换技能表、行动序列上生成**倒计时**；
- AI 权重 `aiScoreBonus = 100`（够能量必放）。

**千冶铸一，万劫烬灭**（`TenaxPerIgnem`）—— 结界里的终结技
- 全体 **350%** 生命上限；付 160 能量后回能 **+5**；
- **额外机制（作者追加，设计稿没有）**：打完**生命恢复至上限**（续航）；
- 门槛：结界内 + 能量满。AI 权重 100。

---

## 4. 结界（【无量忿怒】）

开着的时候有 **7 条**加成，全部由 `beginInfiniteFury` 加上、`endInfiniteFury` 原样撤回：

| # | 效果 | 实现 |
|---|---|---|
| 1 | 暴击率 +20% / 暴击伤害 +60% | `InfiniteFuryEffect` |
| 2 | 技能表换成结界那三条 | `PlayerController#setSkills` |
| 3 | **【千锻魂】受伤 -50%** | `addDamageReduction(来源键, 0.5)` |
| 4 | **【千锻魂】受到治疗 +50%** | `healEnhancePercent +0.5` |
| 5 | **【千锻魂】受击 → 攻击者挂【煞火缠身】+ 自己 +1 充能** | `MortalBladeFuryHitListener`（只在结界期间注册） |
| 6 | **【万淬心】队友伤害 +50% / 自己额外 +75%** | `MortalBladeAllyEnhanceEffect` / `setEnhance` |
| 7 | **E1：自身穿透 +20%**（设计原文是"敌方全抗 -20%"，按作者决定改成加穿透） | `setPenetration` |

### 4.1 结界怎么结束（两条路）

1. **倒计时到点**：`CountdownEntity`（名字 `倒计时：无量忿怒`，固定 **70 速**）在自己的回合执行解除。
   > ⚠️ 倒计时是"假实体"，但它**必须有正 HP** —— `LivingThing#isAlive()` 是按 `hp <= 0` 判死的，
   > HP=0 的话 `TurnManager#removeTheDeath()` 每回合都会把它的条目摘掉，结界就**永远不会到点**。
2. **免死触发**：结界期间受到致命一击 → **不会倒下**（血锁 1）→ 解除结界 + 回复 **50% 生命上限**。
   > ⚠️ 回血必须**排进当前回合的收尾队列**（`lastExecuteList`）。在伤害修正器里当场 `heal` 会被
   > 之后的 `setHp(newHp)` 覆盖 —— 表现是"免死触发了、结界也解了，但血还停在 1"。

---

## 5. 两个资源

### 5.1 能量（`energy` / `energyMax` / `energyOverflowMax`）

| 项 | 值 |
|---|---|
| 上限 | **160** |
| 溢出上限 | **+80**（所以 `/data` 里能看到 240） |
| 保底 | **战斗开始时 / 结界解除时，能量不足 75% 就立刻补到 75%** |
| 满能 | 能量**涨到上限的那一刻**解除自身**所有负面效果** |
| 获得途径 | 普攻 +20 / 强化普攻 +20 / 战技 +30 / 两个终结技各 +5 / 【因果尽偿】+27 |

> ⚠️ 回合头第一行打印的"能量 金/木/水/火/土"是**五行法力**，**不是**它的能量。
> 它的能量与充能打印在**第二行**（`setShowSpecialMes`）：
> `能量 120/160  充能 3（每 9 点触发一次，够 0 次）`

### 5.2 充能（`charge` / `chargeMax`）

- **没有上限**：可以攒到 81、9999。
- `chargeMax` 是**每次触发需要的点数**（**9**；**E2 星魂降到 7**），不是上限。
- 够一次就扣一次阈值、打一次追打，**剩下的继续攒** →
  **81 点能连打 9 次**，中途血量掉到 1 就停（每次追打都要付 10% 生命上限）。
- 获得途径（结界期间）：**我方目标攻击敌方 +1**（【因果尽偿】）、**被打 +1**（【千锻魂】）。

---

## 6. 星魂

| 星魂 | 效果 | 状态 |
|---|---|---|
| **E1**「我殁之前，我仍未成形」 | 结界期间自身**穿透 +20%**；放完追打后**倒计时延后 15%** | ✅ 已实现 |
| **E2**「心若死灰，而炉火未熄」 | 充能**阈值 9 → 7** | ✅ 已实现 |
| ~~E4~~「锻打遗恨，后剑骨自成」 | 【万淬心】队友增伤再 +50% | ❌ 不做（星魂只做到 2 魂） |
| ~~E6~~「神若当殒，自誓弑寿瘟」 | 受伤/扣血给充能（每回合一次）+ 千冶铸一 ×1.5 | ❌ 不做（星魂只做到 2 魂） |

用 `/data` 调：`/data modify entity @s eidolon set 1`（0 = 无星魂，范围 0…6，只认 1 和 2）。

---

## 7. 怎么调（全是配置，不用改代码）

### 7.1 `config/gameConfig/EntityData.json`（实体数值）

```json
{"entities": {"game_official_content:mortenax_blade": {
  "base":   {"baseHpMax": 7640, "baseSpeed": 107},
  "derived":{ "hp": 7640, "baseHpMax": 7640 },
  "classState": {"energyMax": 160, "energyOverflowMax": 80, "chargeMax": 9, "eidolon": 0}
}}}
```

- **`baseHpMax` 是它最重要的一个数**：血量与全部伤害一起变。
- `classState` 里的键与 `/data get entity @s` **同名**：`energy` / `energyMax` / `energyOverflowMax` / `charge` / `chargeMax` / `eidolon`。
- 运行时的 `infiniteFury` / `chargeListenerRegistered` / `payoffBusy` **不许在配置里写**（会被挡掉）。

### 7.2 `config/gameConfig/SkillData.json`（技能数值）

键是 `<实体完整id>#<技能名>`，例如：

```json
{"skills": {"game_official_content:mortenax_blade#刃下，归葬": {"hpMagnification": 0.72}}}
```

- 能调：三个倍率 / 目标数 / 冷却 / 法力消耗 / 阵营 / AI 权重。
- ⚠️ 千冶·刃的技能**都把倍率写在 `hpMagnification`**（不是 `atkMagnification`）。
- ⚠️ 改【刃下，归葬】的倍率**追打也会跟着变**（追打取的是技能表里那份的副本，不是 `new` 出来的出厂值）。

### 7.3 速查：我想……该改哪

| 想做的事 | 改哪里 |
|---|---|
| 改伤害 | `SkillData.json` 的 `hpMagnification`（或 `EntityData.json` 的 `baseHpMax`） |
| 改结界持续多久 | 代码里的 `FornaxExCorpore.COUNTDOWN_SPEED`（70 → 更大 = 更久） |
| 改一次追打的能量 | `MortalBladeChargeListener.ENERGY_PER_PAYOFF`（27） |
| 改追打次数 | `chargeMax`（阈值小 = 同样的充能打更多次） |
| 开 E1 / E2 | `eidolon` = 1 / 2 |

---

## 8. AI

- 它用 `PlayerController`，所以**玩家不在场时由 AI 接管**。
- 三个技能带 `IAiScoreBonus`：
  - 骸骨当炉 **100**（能量够就先开结界）
  - 千冶铸一 **100**（结界里能量够就打大招收尾）
  - 刃下归葬 **60**（结界里优先战技）
- 另外在 `TagConfig.json` 里有 `game_official_content:mortenax_blade: {"attack": 5, "defence": 2}`。

---

## 9. 怎么验证（自测）

```powershell
# 静态自查
.\check-sources.ps1                       # 期望：CHECK OK（文件数见 30-WORKFLOW.md）
# 全量编译 + 自测
.\test-command-system.ps1                 # 期望：失败 0 条（基线见 notes_for_llm/30-WORKFLOW.md）
```

千冶·刃的守卫（`TestCommandSystem` 里搜 `MortalBlade`）：

| 用例 | 验什么 |
|---|---|
| `testMortenaxBladeResources` | 能量 / 充能 / 75% 保底 / 满能清负面 / E2 阈值 |
| `testMortalBladeSkills` | 5 个技能的倍率口径、嘲讽、`canUse` 门槛 |
| `testInfiniteFuryZone` | 结界 7 条加成的加/撤、倒计时、免死、技能表切换 |
| `testMortalBladeAiPriority` | 三个 AI 权重 |
| `testMortalBladeCharge` | 充能监听器（我方打敌方 +1、敌方打我们不加、战后注销） |
| `testDamageTakenIncrease` | 负数减伤 = 增伤（【煞火缠身】的"受伤 +50%"靠它） |
| `testMortalBladeEnergyDisplay` | 回合头那行显示 |
| `testMortalBladePassives` | 千锻魂 / 万淬心 / E1 的加与撤 |
| `testMortalBladeEnergyGain` | 能量获得途径 20/20/30/5 |
| `testMortalBladeChargePayoff` | 追打：排队、取表里那份、回 27 能量 |
| `testMortalBladeChargeLoop` | 81 充能 → 连打 9 次、血不够就停 |

---

## 10. 已知差异 / 没做的事（诚实清单）

| 项 | 现状 | 为什么 |
|---|---|---|
| 【千锻魂】"被敌方攻击的概率提高" | ❌ 没做 | 要在 `TargetStrategies` 里加威胁权重，会碰**所有** AI 的选目标逻辑；现在用嘲讽近似 |
| 【万淬心】"有虚无命途队友则队友终结技 +75%" | ❌ 没做 | 项目里**没有"命途"这个概念**；实现的是"否则"那一支（自身 +75%） |
| E4 / E6 | ❌ 不做 | 星魂只做到 2 魂（作者拍板） |
| "额外回合开始时自动追打"（白厄反击的模式②） | ❌ 没做 | 千冶·刃的追打只走"回合收尾队列"；白厄的反击还有"额外回合开始时提前放"那一支 |
| 数值取中文版 | ✅ | 【煞火缠身】-30%/+50%、千冶铸一 350%、刃下 72%+4×24%（英文版分别是 -32%/+54%、378%、79.2%+4×26.4%） |

### 10.1 两条"改它之前必须知道"的坑

1. **额外攻击一律排队**：追打不能在别人技能结算中间插进去，要
   `getPresentTurn().getLastExecuteList().add(...)`，等这一回合结算完再打 —— 否则目标列表正在被遍历、
   日志顺序错乱、二次事件归属不清。
2. **占位实体必须有正 HP**：`CountdownEntity` 只靠 `hp > 0` 才算"活着"（`isAlive()` 是按 HP 判的），
   否则时间轴每回合把它当尸体摘掉。

---

## 11. 相关文件

| 文件 | 作用 |
|---|---|
| `officialStuff/customEntity/players/MortenaxBlade.java` | 实体：资源、结界状态机、技能表、显示、`consumeHp` |
| `officialStuff/customSkill/mortenaxBlade/MortalBladeCommonAttack.java` | 残锋，掠尽 |
| `…/MortalBladeSkill.java` | 淬锋，断魄（强化普攻） |
| `…/BladeUnderBurial.java` | 刃下，归葬 |
| `…/FornaxExCorpore.java` | 骸骨当炉，血肉即薪（开结界） |
| `…/TenaxPerIgnem.java` | 千冶铸一，万劫烬灭 |
| `officialStuff/customEffect/mortenaxBladeEffects/InfiniteFuryEffect.java` | 结界里的暴击加成 |
| `…/BalefireBindEffect.java` | 【煞火缠身】（防御 -30%、受伤 +50%） |
| `…/MortalBladeAllyEnhanceEffect.java` | 【万淬心】给队友的增伤 |
| `officialStuff/customEvent/mortenaxBladeEvents/MortalBladeChargeListener.java` | 【因果尽偿】我方攻击 → +1 充能 |
| `…/MortalBladeFuryHitListener.java` | 【千锻魂】受击 → 缠身 + +1 充能 |
| `entity/CountdownEntity.java` | 通用倒计时实体（结界到点解除） |
| `project_analyses/MORTENAX-BLADE-2026-10.md` | 施工单与决策记录 |
