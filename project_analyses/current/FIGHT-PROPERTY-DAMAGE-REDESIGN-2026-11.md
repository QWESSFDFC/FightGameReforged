# 「属性 / 伤害 / 回合流程」重设计方案记录（2026-11-08 用户口述）

> **文档分类**：[D3] 分析 · 方案记录 ｜ **这是什么**：作者 2026-11-08 提出的**新数据结构方案**（`FightProperty` + 四个容器 + 重做的 `Damage` + `ActEntry` 行动队列）的**逐字记录**，以及它与现状的对照、必须先定的语义、成本与施工顺序 ｜ **读者**：作者 + 下一个接手的设计者 ｜ **时效**：2026-11-08 快照。**同日第二轮作者已回答 9 条（§7.1 已回写）；本文只记录与核对，没改任何代码。**

> **给下一个对话的用法**：§1 是作者原话（**别改写、别"顺手优化"**）；§2 是"每个新类 ↔ 现在在哪"；
> **§3 是必须先回答的问题清单**（不回答就动手 = 猜，本项目最大的事故来源）；§5 是成本，§6 是建议顺序。
>
> 与既有文档的关系：这是 `ENTITY-ATTRIBUTE-SPLIT-2026-10.md` 阶段 2 之后**真正的下一步**
> （方案 B「基础值 + 增强值收进统一属性对象」，那份文档评"四种里实用性最高"），
> 加上 `LIVINGTHING-RESPONSIBILITY-AUDIT-2026-10.md` §3 的 P2（拆组件）。**不是重新评估一轮**。

---

## 1. 作者原话（逐字记录，2026-11-08）

```text
我写了一个属性,伤害重置方案,你记一下,然后我们一起讨论.

类 FightProperty
 字段 baseXXXX(攻击力,生命值,生命值上限,防御力,速度,暴击率,暴击伤害,速度)
ManaPool(一个单独的对象)
EffectContainer(一个单独的对象)
PropertyEnhanceContainer(List,放PropertyEnhance)
 DamageEnhanceContainer(List,放DamageEnhance)
 类PropertyEnhance
字段amount,percent,type(标记提升的属性类型,类型使用枚举,同时,baseXX和XX 是两种类型)
  类DamageEnhance
字段amount,percent,elementSort,damageType
  类 Damage更新
字段 damageType,damageSourceEntity,rawDamageAmount(倍率乘上相应属性后的数值,没有计算防御,减伤等,也可以直接造成固定数值伤害 ),damageSource(伤害原因,方式 ),damageElementSort,appliedDamage,damageTargetEntity(实际对实体造成的,可以使用实体生命值变化量来获得),calculateDamageResult(经过伤害计算后的)
 类DamageType
 DOT,CommonAttack,SpecialSkill,UltimateAttack,AddtionAttack,SystemDamage
一个伤害可以有多个 damageType,计算伤害时应该算上,都生效.
同时,如果伤害元素是UNIVERSAY,代表这次伤害是"真实伤害",无视属性抗性,不会受到元素增伤那个的影响.
同时我准备更改一个回合的流程.效果结算放在回合头输出之后.回合内的行动也使用queue,可以写一个actEntry,有优先级,firstExecute和lastExecute都可以改成一个actEntry,普通行动也可以.
```

> 原话里 `baseXXXX` 的括号里 **"速度"出现了两次** —— 作者 2026-11-08 已确认是**手误**（见 §2.1.1）；
> `UNIVERSAY` 应为 `UNIVERSAL`。

---

## 2. 方案结构（规范化）

### 2.1 类的分解

| 新类 | 职责 | 装什么 |
|---|---|---|
| `FightProperty`（= 今天的 `AttributeProfile`，名字二选一） | 战斗属性**总容器**（一个实体一份） | `baseXxx` 基础值 + 成长 + 下面六个容器（字段清单见 2.1.1） |
| `ManaPool` | 五行法力 | 5 条 `Mana`（主/其余上限规则不变） |
| `EffectContainer` | 身上的效果 | 效果列表 + 增/删/合并语义 |
| `PropertyEnhanceContainer` | 属性加成 | `List<PropertyEnhance>` |
| `DamageEnhanceContainer` | 伤害加成 | `List<DamageEnhance>` |
| **`PenetrationContainer`** | **抗性抵消（穿透）**（作者 2026-11-08 追加） | `List<Penetration>` |
| **`ResistanceContainer`** | **五行抗性**（作者 2026-11-08 追加"抗性用一样的处理方式"） | `List<Resistance>`（见 2.1.3） |
| `PropertyEnhance` | 一条属性加成 | `amount` / `percent` / `type` |
| `DamageEnhance` | 一条伤害加成 | `amount` / `percent` / `elementSort` / `damageType` |
| **`Penetration`** | **一条穿透** | `amount` / `elementSort` / `source`（见 2.1.2） |
| **`Resistance`** | **一条抗性** | 与 `Penetration` **字段完全相同**（见 2.1.3） |
| `Damage`（重做） | **一次伤害的载体** | 见 2.3 |
| `DamageType` | 伤害类型枚举 | `DOT, CommonAttack, SpecialSkill, UltimateAttack, AdditionAttack, SystemDamage` |
| `ActEntry`（新） | 回合内的一个行动 | 优先级 + 动作；**普通行动**也是一个 `ActEntry` |

### 2.1.1 `FightProperty` 的字段清单（作者 2026-11-08 已定）

| 组 | 字段 | 说明 |
|---|---|---|
| **面板基础值（7 个）** | `baseAttack`(攻击力) / `baseHp`(生命值) / `baseHpMax`(生命值上限) / `baseDefence`(防御力) / `baseSpeed`(速度) / `baseCriticalRate`(暴击率) / `baseCriticalDamage`(暴击伤害) | 原话里"速度"写了两遍 = 手误，实际 7 个 |
| **穿透** | **不是标量**：一个对象 + 一个容器（作者 2026-11-08 第二轮改的，见 §2.1.2） | ⚠️ 与今天的**可配键** `penetration` 有冲突，两条出路见 §2.1.2 的 b) |
| **五行抗性** | **也不是标量**：一个对象 + 一个容器（作者 2026-11-08 第三轮："那些抗性,可以使用一样的处理方式"，见 §2.1.3） | ⚠️ 5 个抗性都是**可配键**（`EntityKeySpecs:149-151`，`EntityData.json` 10 个模板 × 5 个键全有） |
| **成长类** | `hpGrow` / `attackGrow` / `defenceGrow` | 保留在 `FightProperty`（作者原话"成长类也放到FightProperty"） |
| **其余（今天挂在 `AttributeProfile` 上，本方案没提，默认原地不动）** | `element`、`defenceLoss`、`individualMultipleArea`、`alive`、`anticipating`、`manas`(→ 搬到 `ManaPool`) | 见 §7.2 的 A2 |

> **今天 10 个"五元素临时属性"的下场**（2026-11-08 实测读写点）：
> - **5 个 `*Penetration`（单元素穿透）：全项目 0 个写入点**，只有 `DamageCalculate` 各读 1 处
>   ⇒ 改由 `Penetration` 条目表达（`elementSort = 该元素`）。它们**本来就是死字段**，
>   容器化之后反而第一次有了真实写法；
> - **5 个 `*DamageEnhance`（单元素增伤）：也是 0 个写入点**，只有 `DamageCalculate:107-131` 各读 1 处
>   ⇒ 正好搬进 `DamageEnhance(elementSort=该元素, damageType=null)`，**迁移不丢任何数值**。
>
> **5 个抗性**不一样：它们是**活的**面板属性（构造器赋值 `LivingThing:199-203`、可配、`DamageCalculate` 各读 1 处），
> 所以容器化要保留"基础抗性"这一层，见 §2.1.3。

### 2.1.2 `PenetrationContainer`（作者 2026-11-08 追加：穿透也对象化）

作者的意图：**穿透和 `DamageEnhance` 用同一套做法** —— 一个对象 + 一个 `Container` 字段，
而不是 `FightProperty` 上的一个标量。

#### a) `Penetration` 的字段（建议形状）

| 字段 | 类型 | 含义 |
|---|---|---|
| `amount` | `double` | **平值**：`有效抗性 = max(0, 抗性 − amount)`（今天就是这个公式，`DamageCalculate:144-147`） |
| `elementSort` | `ElementSort` | `null` = **全属性**（对应今天的全局 `penetration`）；具体元素 = 只对该元素生效（对应今天那 5 个死字段） |
| `source` | `Object` | 摘除用的来源键（与 §7.2 A1 一致；不带它就只能"加回去"） |

**建议不加 `percent`**：今天的穿透是"抗性 − 穿透"的**平值**抵消；
"无视 50% 抗性"是另一个机制（改的是抗性本身，不是穿透），现在没有任何地方需要它。

#### b) ⚠️ 与既有契约的冲突（这条要作者拍板）

`penetration` 今天**不是**普通字段，它是**可配键**：

| 证据 | 内容 |
|---|---|
| `EntityKeySpecs:179-181` | `spec(DataKeys.Temporary.PENETRATION, GROUP_TEMPORARY, Kind.DOUBLE, LivingThing::getPenetration, doubles(LivingThing::setPenetration), "穿透（面板属性，整局生效…）")` |
| `config/gameConfig/EntityData.json` | **10 个模板里都有** `"penetration": 0` |
| `70-DATA.md` §5.10 | 键名 = Java 字段名，是对外 API |

⚠️ **但"配置键 = 字段名"这条只对 `/data` 成立**：配置键是 `EntityKeySpecs` 里**手写的字符串** +
读写 lambda（读 `LivingThing::getPenetration`、写 `setPenetration`），**不是反射字段名**。
所以"搬进容器"**不会让 `EntityData.json` 失效** —— 只要保留（或改写）那两个 lambda：

- `getPenetration()` 的含义改成"`Σ` 命中的条目"（求和口径）；
- `setPenetration(v)` 的含义改成"替换基础条目"。

两条出路：

| 方案 | 做法 | 代价 | 我的建议 |
|---|---|---|---|
| **D1** | 保留一个标量 `basePenetration`（继续吃 `penetration` 这个键），**另加** `PenetrationContainer` 只装临时穿透；公式 = `basePenetration + Σ(命中条目)` | 有"基础 + 条目"两层；穿透这边**很便宜**（只有 1 个键） | 单独看穿透，D1 最省事 |
| **D2** | 纯容器：基础值也是一条 `source = BASE` 的条目，`getPenetration`/`setPenetration` 改成"求和 / 写基础条目" | `/data` 键消失（变 list）、`70-DATA.md` 契约要改、`ConfigTests` 面板键断言要改 | **推荐与抗性统一走 D2**（理由见 §2.1.3 的 c)：抗性有 5 个键，混着来会留下五份同样的问题） |

**生命周期**（这条不定清楚就会出"加成没撤掉"那一类账；D1/D2 都适用 —— D2 只是把"基础值"也做成条目）：

| 东西 | 生命周期 | 今天对应物 |
|---|---|---|
| 基础穿透（D1 的标量 / D2 的 `source = BASE` 条目） | **面板属性**：`copyFrom` 跟着副本走、`resetTemporary()` **不清** | 今天的全局 `penetration`（`resetTemporary` 确实不清它） |
| 其余条目（技能/效果挂的） | **临时属性**：`copyFrom` **不带**、`resetTemporary()` **清空**、效果到期时按 `source` 摘 | 今天 5 个 `*Penetration` 就在清零清单里（`80-EFFECTS.md` §2） |

#### c) 元素匹配的统一口径（建议）

`Penetration.elementSort` 和 `DamageEnhance.elementSort` 是**同一套语义**，建议统一成：

- `null` = **全属性 / 全元素**（不做元素判断，永远命中）；
- 具体元素 = 只对**该元素**的伤害生效；
- ⚠️ **`UNIVERSAL` 不表示"全元素"** —— 它已经被"真实伤害标记"占用了（§3.4），
  拿它当"全元素"会造成第二个多义枚举值。

### 2.1.3 `ResistanceContainer`（作者 2026-11-08 第三轮："那些抗性,可以使用一样的处理方式"）

#### a) 和穿透共用一个形状

`Resistance` 与 `Penetration` 的字段**完全相同**：

| 字段 | 类型 | 在抗性这边的含义 |
|---|---|---|
| `amount` | `double` | 抗性值（`0.3` = 30%）；可以是**负数**（弱点，今天 `DamageCalculate` 允许抗性 ≤ 0） |
| `elementSort` | `ElementSort` | `null` = **全属性抗性**；具体元素 = 只对那种元素生效 |
| `source` | `Object` | 摘除用的来源键 |

⇒ **建议两处共用一个类**（名字待定：`ElementValue` / `ElementModifier`），
只靠**两个不同的容器字段**区分"这是抗性还是穿透"。理由：字段一模一样，
分成两个类以后必然会被写成两份几乎相同的求和代码。

#### b) 基础抗性 = 5 条"基础条目"，不是 5 个字段

| 层 | 今天 | 容器化之后 |
|---|---|---|
| 基础抗性 | 5 个字段 `metalResistance` …（构造器赋值 `LivingThing:199-203`） | 5 条 `source = BASE`（或 `null`）的条目，`elementSort` 各一种 |
| 临时抗性加成 | **今天不存在**（除配置外没有写入点） | 新能力：效果可以给"火抗 +0.2"，到期按 `source` 摘 |
| 读取 | 5 个 `switch` 分支（`DamageCalculate:106-135`） | 一句求和：命中元素的条目相加 |

#### c) ⚠️ 为什么这里**不能**照搬 D1（保留 5 个标量）

穿透只有 **1** 个可配键；抗性有 **5** 个（`EntityKeySpecs:149-151`，`EntityData.json` 里 **10 个模板 × 5 个键**）。
照搬 D1 会留下 5 个"基础抗性标量 + 一个容器"的混合体，等于把今天的问题原样保留五份。
**建议：抗性走"纯容器 + 基础条目"**（D2 形态），配置键照旧工作 —— 原因见下面这条：

#### d) 关键事实：配置键**不是**反射出来的，改名/搬家不会破配置

| 面 | 怎么来的 | 搬进容器后 |
|---|---|---|
| **配置键**（`EntityData.json` 的 `metalResistance` 等） | `EntityKeySpecs:149-151` 里**手写的字符串** + 读写 lambda（`living -> resistanceOf(...)` / `doubles((l,v) -> setResistance(...))`） | **键名一个字节不变**；只要 `getResistance(element)` 求和、`setResistance(element, v)` 写"基础条目"，配置照旧生效 |
| **`/data` 键** | 反射**字段名**（可用 `@DataField` 固定） | 5 个键会变成一条 list（**这是唯一要认的代价**，见 §5.1）；想保旧键就用 `@DataField` + 一个派生的只读视图（成本见 §5.1 的两条出路） |

> 这条事实同样适用于穿透：D2 的风险**不在配置文件**，而在 `/data` 视图与
> `EntityKeySpecs` 的 lambda 要改写（`LivingThing::getPenetration` 变成"求和/写基础条目"）。

#### e) 还没定的两点（见 §7.2 的 A6/A7）

1. **抗性叠加是"相加"吗？**（今天只有配置一个来源，所以没有规则。建议相加：`0.3 + 0.2 = 0.5`；
   如果要做"覆盖/取最大"，容器就得多记一个优先级字段）
2. **基础条目算不算在求和里**？（建议算，这样 `getResistance(element)` 只有一个口径；
   `resetTemporary()` 只删非基础条目）

### 2.2 `PropertyEnhance.type` 的两类取值

作者原话：「类型使用枚举,同时,baseXX 和 XX 是两种类型」⇒ 枚举里**同时**有
`BASE_ATTACK` 与 `ATTACK` 这类成对的常量：

- `ATTACK` = 加在**最终值**上（今天 `getAttack()` 里那个 `+percent / +amount`）；
- `BASE_ATTACK` = 加在**基础值**上（今天**没有**这个概念，是新能力）。

⚠️ `BASE_XX` 的**具体语义还没定**（见 §7 的 A3）：是"读基础值时叠加"，还是"直接改字段"；
以及被 `Entity.setLevel()` 用 "grow × (等级-1) + 基数" 重算时要不要保留。

### 2.3 `Damage` 的六个字段与四个数值

| 字段 | 含义（作者原话） |
|---|---|
| `damageType` | 伤害类型；**一个伤害可以有多个，计算时都生效**。**由技能在造成伤害时手动传入**（2026-11-08 定） |
| `damageSourceEntity` | 伤害来源实体 |
| `damageTargetEntity` | 受害实体 |
| `damageElementSort` | 伤害元素（`UNIVERSAL` ⇒ **真实伤害**，边界见 2.3.1） |
| `damageSource` | 伤害原因 / 方式 |
| `rawDamageAmount` | **倍率乘上相应属性后的数值**；没有计算防御、减伤等；**也可以直接造成固定数值伤害** |
| `calculateDamageResult` | **经过伤害计算后**的数值 |
| `appliedDamage` | **实际**对实体造成的数值（可以用生命值变化量获得） |

#### 2.3.1 真实伤害的边界（作者 2026-11-08 定）

`damageElementSort == ElementSort.UNIVERSAL` ⇒ 真实伤害：

| 环节 | 普通伤害 | 真实伤害 |
|---|---|---|
| 属性抗性 `(1 − 有效抗性)` | 乘 | **不乘**（按 1） |
| 单元素增伤 `*DamageEnhance` | 取对应元素那条 | **不取** |
| **全局增伤 `(1 + enhance)`** | 乘 | **不乘**（原话"移除.全局增伤."） |
| **防御公式** `(等级×10+200)/(…)` | 乘 | **照乘**（原话"防御、减伤不要免"） |
| **减伤 / 承伤倍率** | 乘 | **照乘**（同上） |
| 单体倍率 `individualMultipleArea` | 乘 | 照乘（用户没提，默认保留，见 §7 A4） |
| 暴击倍率 | 乘 | 照乘（同上） |

落地形态：`DamageCalculate` 里 `enhance` 那一段改成
`(isTrueDamage ? 1 : 1 + enhance)`、抗性乘数改成 `(isTrueDamage ? 1 : resistanceMultiplier)`；
**不需要动防御与减伤那两段**。⚠️ 今天 `UNIVERSAL` 走 `default` 分支：抗性已是 0、单元素增伤已不叠，
**只有全局 `enhance` 还在乘**（`DamageCalculate:88/151`）⇒ 这一条的净改动只有**一处**。

### 2.4 回合流程（`ActEntry` 队列的语义，作者 2026-11-08 定）

1. **效果结算挪到回合头输出之后**（现在是**回合最后一步**，`FightTurnPastListener:265`）；
2. 回合内**所有**行动走同一个 `ActEntry` 队列（`firstExecute` / `lastExecute` / **普通行动** 都是条目）：

| 规则 | 作者原话 | 落地要点 |
|---|---|---|
| 一个接一个执行 | "同时只执行一个,一个一个地执行" | 队列循环取队首 → 执行 → 再取下一条；**不复用今天的"整批快照 + 清空"** |
| 执行中可追加 | "可以在执行时往队列里面加新的,不会影响正在执行的" | 追加只影响**待执行**的那些，不打断当前这一个 |
| 执行前可移除 | "可以在执行前把要执行的移出队列" | 取队首之后、执行之前允许把它摘掉（对应今天的 `SKIP` / 条件失效） |
| 优先级 | "可以写一个actEntry,有优先级" | 队列按优先级取队首；⚠️ **同优先级用入队顺序**（否则同一批动作会随机化），见 §7 A5 |

---

## 3. 与现状的对照（每个新类 ↔ 今天在哪）

| 方案 | 今天的实现 | 差距 |
|---|---|---|
| `FightProperty` | `AttributeProfile`（**1462 行**，`@DataFlatten` 挂在 `LivingThing.attributes` 上，键名无前缀并进 `/data`） | 名字/职责基本对得上；但它今天**同时**装：9 个面板基础值 + 3 个成长 + 13 个 `*Enhance*` 标量 + 五抗/五穿透/五元素增伤/五法力成长 + `element` / `manas` / `defenceLoss` / `individualMultipleArea` / `alive` / `anticipating` |
| `ManaPool` | `AttributeProfile.manas` + `LivingThing.initialMana()` / `recoverManaEveryTurn()` | 数据已在组件，**逻辑**还在实体（2026-10-25 第 41 批刚把 `initialMana` 去重成 8 行；`ManaTests` 4 条断言可以直接当验收判据） |
| `EffectContainer` | `LivingThing.entityEffectList` + `addEffect` / `removeEffect` / `getEntityEffectList` | `/data` 里叫 `effects`；**70 处调用、其中 13 处结构写入**（`LIVINGTHING-AUDIT` §3 P2，含 2 处 `listIterator().remove()`） |
| `PropertyEnhanceContainer` | **13 个 `*Enhance*` 标量**（`AttributeProfile`）+ 5 个 `*Penetration` + 5 个 `*DamageEnhance` | ← **本方案最大的一步，也是唯一会改 `/data` 视图的一步**（见 §5.1） |
| `DamageEnhanceContainer` | **不存在**。最接近的是 `DamageEnhanceEffect`：它把 `enhanceN` 加到**全局增伤** `enhance` 标量上（`DamageEnhanceEffect:76`、`DamageCalculate:88/151`） | 新能力：按**元素**、按**伤害类型**分别加成。今天的"普攻 ×3"就是这条路（`DamageEnhanceEffect(1,1)` ⇒ `enhanceN = 2.0`） |
| `Damage` | `damage/Damage.java`（**43 行**）：`attacker` / `attackedEntity` / `damageAmount` / `skill` | 要扩成 4 个数值 + 类型集 + 来源；**今天只有 1 个数值**，而且它身兼三职（见 §3.1） |
| `DamageType` | **不存在**。今天的"类型"只隐含在 `Skill` 子类里 | 新增枚举 + **谁声明类型**的规则（`Skill` 的 3 个来源：类默认值 / 构造参数 / `SkillData.json` 新键） |
| `ActEntry` | `TurnEntry.firstExecuteList` / `lastExecuteList`（`List<ISpecialAction>`）+ **普通行动**直接 `controller.act()` | 三处统一成一个队列；`ActionSignal` 的 5 个值要一起收敛（见 §3.3） |
| 效果结算位置 | `FightTurnPastListener:265`（`EffectUpdateEvent` 在**回合最末**） | 要挪到回合头（`:232-238`）之后 |

### 3.1 今天那一个 `damageAmount` 身兼三职

`DamageCalculate.calculate()` 的返回值一落地就同时被当成：

| 用途 | 证据 |
|---|---|
| "这一刀应该造成多少"（日志显示） | `LivingThing.makeDamage:2134` → `printAttackLine(..., getDamageAmount())`；注释 `:2130-2133` 写清"显示**应该造成的伤害**，不是实际掉血"（**2026-10-24 用户拍板**） |
| "这一刀实际扣多少血"（结算） | `LivingThing.getDamage:1867`：`newHp = getHp() - da.getDamage().getDamageAmount()`，然后过 `modifyIncomingDamage`（免死/下限） |
| "AI 预测的伤害"（决策） | `Skill.getAnticipatedDamage:390-397` 用同一条链算差值，包在 `anticipating()` 里 |

⇒ **方案的 `rawDamageAmount` / `calculateDamageResult` / `appliedDamage` 三分离，正好把这三件事拆开**，这是本方案最实的一个收益（不是"看起来整齐"）。

### 3.2 `DamageCalculate` 里三段公式，正好对应方案的三个数值

`DamageCalculate:148-159`：

```text
base   = hp*hpMag + atkMag*attack + dfkMag*attackerDefence + skill.extraDamage + attacker.extraDamage   ← rawDamageAmount
damage = base × (1+增伤) × 抗性乘数 × 承伤倍率 × 等级防御比 × 单体倍率 × 暴击倍率                        ← calculateDamageResult
```

`appliedDamage` 今天**没有保存**，只在 `getDamage` 里作为 `hp - newHp` 一闪而过；日志刻意显示前者、不显示它。

穿透那一段（`DamageCalculate:100-147`）在容器化之后变成一句求和 —— 今天要 `switch` 五次、再加全局一次：

```text
penetration = attacker.penetrations 里 elementSort 为 null 或命中本次伤害元素的 amount 之和
有效抗性     = 抗性 > 0 ? max(0, 抗性 − max(0, penetration)) : 抗性      ← 两个夹紧照旧，一个字不改
```

抗性那一段同理（`DamageCalculate:106-135` 的 5 个分支）：

```text
resistance = target.resistances 里 elementSort 为 null 或命中本次伤害元素的 amount 之和
```

⇒ 容器化之后，`DamageCalculate` 里那个 **5 分支的 `switch`**（同时读抗性/穿透/元素增伤）会**整段消失**，
换成 3 次求和 + 1 段公式。这是纯收益：今天每加一种元素就要改 4 处 × 5 行。

### 3.3 `ActionSignal`（5 个值）是行动队列的前置问题

`ActionSignal`：`NORMAL` / `SPECIAL_ACTION` / `WITHOUT_NEW_TURN` / `SKIP` / `SKIP_WITHOUT_NEW_TURN`
（`FightTurnPastListener:241-261`、`FlameReaver:1226`、`UltimateAttack:203/218/245/251`、`Frozen:103`、`CountdownEntity:109`）。

回合循环今天的分支：

```text
回合头 → firstExecute(开场队列) → 存活检查 → showSpecialMes
      → NORMAL? controller.act() : SPECIAL_ACTION? specialAction.execute()
      → 排下一条 TurnEntry（SKIP_WITHOUT_NEW_TURN / WITHOUT_NEW_TURN 则不排）
      → lastExecute(收尾队列) → EffectUpdateEvent（效果 tick）
```

"普通行动也变成 `ActEntry`"意味着 **`:246-261` 这整段（含"排下一条回合"）要变成一个或几个条目**，
`FlameReaver#delayNextOwnTurn`（靠 `SKIP_WITHOUT_NEW_TURN` 阻止循环再排一条）这类机制的写法会跟着变。

### 3.4 `ElementSort.UNIVERSAL` 今天身兼两义

| 语义 | 位置 |
|---|---|
| "消耗法力**不限元素**"（技能的 `consumedMana`） | `Skill:230/267/314/354` 4 处 |
| 伤害侧的"无元素"（抗性按 0、不叠单元素增伤/穿透，但**全局 `enhance` 仍乘**） | `DamageCalculate:106-135`（`default` 分支）+ `:88/151` |
| 五行法力**不建第 6 条** | `LivingThing.initialMana`（2026-10-25 第 41 批有断言） |

方案的"UNIVERSAL = 真实伤害"是**第三种语义**。⚠️ 作者 2026-11-08 的选择是**保留**这个写法
（"如果伤害元素是 UNIVERSAL，代表这次伤害是真实伤害"）⇒ 现状与决定一起记在这里，风险留档：
`consumedMana` 那 4 处判断的是**法力**语义，改伤害侧时**一处都不许碰**；
反过来，以后若有人给"无元素伤害"再赋新含义，要在 `Damage` 上加显式字段（如 `isTrueDamage()`）而不是复用枚举。

---

## 4. 这个方案能治的现存坑（不是新问题，是已有账）

| 坑 | 今天为什么存在 | 新结构怎么治 |
|---|---|---|
| **K1** `addEffect` 等级更高时换实例、**不回收旧加成** → 永久 +2 | 加成是**标量**，"谁加的"没记账，只能靠"加回去" | `PropertyEnhance` 带来源后可以**按来源精确摘除**（`removeIf(source)`），不依赖 `isOn` 守卫 |
| **K2** `cleanseNegativeEffects` 用 `removeIf` 跳过收尾 → 永久 −30% 防御 | 同上 | 同上 |
| **K4** 9 个 `whenLastTimeEnd` **都没有 `isOn` 守卫** | 收尾是"再减一次同样的数"，多调一次就变负 | 容器里"按来源删条目"，重复删是幂等的 |
| `DamageCalculate` 里 5 个 `switch` 分支（五行各一次） | 元素 × 属性写在公式里 | `PropertyEnhance`/`DamageEnhance` 按 `elementSort` 匹配，公式只剩一条 |
| `Erosion` 的"至少留 1 点"是手写夹取（`:162-164`） | 没有统一的"实际伤害"钩子 | 交给 `DamageModifier`（`appliedDamage` 阶段），日志与结算读同一个数 |

> 依据：`notes_for_llm/subsystems/80-EFFECTS.md` §3 的 K1/K2/K4、`60-COMBAT.md` §5.6。
> **这几条都是"标量 + 成对加减"造成的**，所以容器化不是审美问题。

---

## 5. 成本与风险

### 5.1 `/data` 视图会变（唯一的一条红线级风险）

**三批键会动**：

| 键 | 今天的状态 | 容器化之后 |
|---|---|---|
| **13 个 `*Enhance*`**（`attackEnhancePercent`、`hpEnhanceAmount`、`healEnhancePercent`…） | `@NoConfig` ⇒ **配置里写不了**，但在 `/data get entity @s` 里**是顶层键** | 整批消失，换成一条列表 |
| **5 个单元素穿透**（`metalPenetration`…） | `@NoConfig`、**全项目 0 写入点**、`DamageCalculate` 各读 1 处 | 整批消失，改由 `Penetration` 条目表达 |
| **全局 `penetration`** | **可配键**（`EntityKeySpecs:179`、`EntityData.json` 里 10 个模板都有） | **看 §2.1.2 的 b) 怎么定**：D1 保留（键不动）、D2 删掉（键消失） |
| **5 个抗性**（`metalResistance`…） | **可配键**（`EntityKeySpecs:149`、`EntityData.json` 10 个模板 × 5） | 变成一条 `resistances` 列表；**配置键照旧工作**（§2.1.3 的 d)），只有 `/data` 键会变 |

依据与代价：`notes_for_llm/subsystems/70-DATA.md` §5.10（键名 = 对外 API）、自测里的 `dump 契约` 断言、
以及用户自己的 `/data` 脚本。

**两条出路（要先选一条）**：
1. **接受视图变化**（推荐，反正这些键是只读、且配置侧不受影响）：改 `70-DATA.md` 的键名契约 + 改断言；
2. **保住旧键**：`DataBridge` 加"派生只读键"机制（按容器内容现算这些键）。成本高、且需要新的白名单逻辑。

### 5.2 要改的代码面（粗估）

| 面 | 量 | 说明 |
|---|---|---|
| 13 个通用增强效果 | 13 个类 × 2 个方法 | `AttackEnhance` / `DefenseEnhanceEffect` / `SpeedEnhanceEffect` / `HpEnhanceEffect` / `CriticalRateEnhanceEffect` / `CriticalDamageEnhanceEffect` / `DamageEnhanceEffect` 等，全部 `setXxx(getXxx()+n)` → 容器增删 |
| 角色自己加减的笔数 | ≥4 处 | `Phainon`（变身的 5 笔）、`AwakeEndListener`、`UltimateAttack`、`InfiniteFuryEffect`、`MortalBladeAllyEnhanceEffect`、`MortenaxBlade` |
| 派生公式 | 6 条 | `getAttack` / `getDefence` / `getSpeed` / `getHpMax` / `getCriticalRate` / `getCriticalDamage`，全部同构 `base*(1+percent)+amount`，**容器化后仍是一条公式** |
| 伤害侧 | 4 个文件 + **21 个生产** `makeDamage` 调用点（+2 处探针） | `Damage` / `DamageCalculate` / `DamageEvent` / `LivingThing` + 各技能 |
| 穿透容器 | 1 个新类 + 1 个容器字段 + `DamageCalculate:100-147` | 那段是"全局 1 次 + `switch` 里 5 次 `penetration +=`" → 一句求和；**5 个死字段收编，0 个生产写入点要迁移** |
| 抗性容器 | 同上（与穿透共用一个类）+ 构造器 5 行赋值改成 5 条基础条目 + `EntityKeySpecs` 的 `resistanceOf`/`setResistance` 两个 helper 改成求和/写基础条目 | 5 个抗性是**活字段**：构造器、配置、`DamageCalculate` 都要跟着改；`/data` 那 5 个键会变（§5.1） |
| 回合侧 | `TurnEntry` / `TurnManager` / `FightTurnPastListener` / `EffectEventListener` + **25 处测试** | 测试大量直接摸 `getLastExecuteList()` / `getFirstExecuteList()`（`MortalBladeTests` 11 处、`TestSupport` 6 处、`DamageDisplayTests` 7 处、`CombatCoreTests` 1 处） |
| 反射清单 | 2 张表 | `EntityCoreTests.panelAttributes` / `temporaryAttributes`（23 个临时属性名）要改成"容器清单"—— 它们现在是**按字段名**上锁的 |

### 5.3 不能动的东西（既有约定）

- **14 参位置构造器**（`MODDING-GUIDE.md` 当对外 API，`mods/DrunkenSwordsman` 在用）—— 已有断言逐位钉住参数类型序列；
- `facSet*` 33 个链式 setter（公共 API，仓库内几乎没人用）；
- **`/data` 键名**里那些**能配**的（`EntityData.json` 的那一批）—— 本方案不该碰它们；
  其中 **`penetration`（1 个键）与 5 个抗性键**正是两个容器要正面对上的东西：
  配置**文件**不受影响（键是手写字符串 + lambda，见 §2.1.3 的 d)），受影响的是 **`/data` 视图**（§5.1）。

---

## 6. 建议的施工顺序（每步都能单独停）

> 原则：**先做"纯增量、不改对外视图"的**，最后做"改视图 + 改平衡"的。

| 步 | 做什么 | 为什么排这里 | 风险 | 状态 |
|---|---|---|---|---|
| **0** | 把 §7.2 剩下的 9 条（A0~A8）定下来 | 它们决定容器的形状（尤其 **A0 穿透/抗性走哪条**、A1 的"带不带来源"、A6/A7 的抗性语义） | — | ✅ **已定**（2026-11-14 用户："4 条都可以按你说的做" ⇒ A0=D2、A1=带、A6=相加、A7=算；A8 合并） |
| **1** | **`Damage` 重做**（4 个数值 + 类型集 + 来源）+ `DamageType` 枚举 + `makeDamage` 加类型参数 | 纯增量：`damageAmount` 保留成兼容 getter 即可；**不动 `/data`**；收益立刻能看到（日志/AI/免死都用得上"实际伤害"） | 低 | ✅ **已完成**（2026-11-14 第 42 批，见 §9） |
| **2** | **`PropertyEnhanceContainer`**（13 个标量 → 列表，含"带来源"） | 收益最大（治 K1/K2/K4），但是**唯一改 `/data` 视图**的一步 | 中 | ✅ **已完成**（2026-11-14 第 45 批，见 §10） |
| **3** | **`PenetrationContainer` + `ResistanceContainer`**（共用一个条目类）+ `DamageCalculate` 那个 5 分支 `switch` 改成求和 | 两个容器一起做才划算：它们动的是**同一段代码**（`DamageCalculate:100-147`）；顺手把 5 个死穿透字段收编 | 低中（`/data` 只有那 5 个死键 + 抗性键会变） | ⬜ 未做 |
| **4** | **`DamageEnhanceContainer`** | 依赖第 1 步的 `DamageType`、第 2/3 步的对象形状；顺带把 5 个单元素增伤搬进来 | 中 | ⬜ 未做 |
| **5** | **`EffectContainer` / `ManaPool` 独立成对象** | `LIVINGTHING-AUDIT` 的 P2；`ManaTests` 已经是现成验收判据 | 中高 | ⬜ 未做 |
| **6** | **回合流程**（效果结算前移 + `ActEntry` 队列） | 改动面最大、且**要重跑实测**（不是改数值，见 §8），必须最后单独一轮 | 高 |

---

## 7. 问题清单：已定 / 还差

### 7.1 已定（作者 2026-11-08 回答，**可以动手的那部分**）

| # | 原问题 | 决定 |
|---|---|---|
| **Q1** | `baseXXXX` 里"速度"写了两遍 | **手误**。基础值是 **7 个**（攻击力/生命值/生命值上限/防御力/速度/暴击率/暴击伤害），见 §2.1.1 |
| — | 穿透放哪（**第一轮**） | 一个标量 `basePenetration` |
| — | 穿透放哪（**第二轮改口**，2026-11-08 当天） | **不用标量**：改成"一个对象 + 一个 `Container` 字段"，做法与 `DamageEnhance` 一致 ⇒ 见 §2.1.2 |
| — | 五抗放哪（**第三轮**，2026-11-08 当天） | **"那些抗性,可以使用一样的处理方式"** ⇒ 5 个抗性也改成对象 + 容器（`ResistanceContainer`），见 §2.1.3 |
| — | 成长类放哪 | **放 `FightProperty`**（`hpGrow` / `attackGrow` / `defenceGrow`） |
| **Q10** | 真实伤害的边界 | **防御、减伤不免**；**全局增伤移除**（不乘）。加上原本就免的抗性/单元素增伤 ⇒ 完整边界见 §2.3.1 |
| **Q13** | `lastTime` 要不要普遍 +1 | **不要**。数值一个字都不改（但 tick 窗口会前移，副作用见 §8） |
| **Q12** | `DamageType` 由谁声明 | **技能在造成伤害时手动传入**（不做类默认值、不进 `SkillData.json`） |
| **Q15** | `ActEntry` 队列语义 | 一个接一个执行；执行中可追加（不影响正在执行的那一个）；执行前可把要执行的移出队列；带优先级 ⇒ 见 §2.4 |
| **Q11** | `UNIVERSAL` 一值多义 | **保留**用 `damageElementSort == UNIVERSAL` 表示真实伤害；法力语义那 4 处不许碰（§3.4） |
| **B** | 类名叫 `FightProperty` 还是 `AttributeProfile` | **两个都行**（作者原话）。建议**保留 `AttributeProfile`**：`AttributeProfile` 只出现在 **10 个文件**里，改名是可选的美化，不是必需项 |

### 7.2 还差的（A 组剩下的 **9** 条 —— 不定就写不了容器）

| # | 问题 | 我的建议（你不反对就按这个做） |
|---|---|---|
| **A0** | **穿透/抗性走哪条路**：**D1**（保留标量 + 容器只装临时加成）还是 **D2**（纯容器，基础值也是一条"基础条目"）？ | **穿透：D1 或 D2 都便宜**（只有 1 个可配键，且配置键是手写字符串，不受影响）；**抗性：建议 D2**（5 个键如果都保留标量，等于把问题原样留五份，§2.1.3 的 c)）。⇒ 合起来建议：**抗性纯容器、穿透跟抗性统一（也纯容器）**，两条都用"基础条目"表达基础值 |
| **A1** | `PropertyEnhance` / `DamageEnhance` / `Penetration` / `Resistance` **带不带 `source`（来源）**？ | **带**。否则 K1/K2/K4 三个真坑一条都治不了（`80-EFFECTS.md` §3），容器化就只剩"好看" |
| **A2** | `element` / `defenceLoss` / `individualMultipleArea` 还留在 `FightProperty` 里吧？ | **原地不动**。只有 `manas` 搬去 `ManaPool`；5 个单元素穿透 + 5 个抗性由条目表达、5 个单元素增伤搬进 `DamageEnhance`（见 §2.1.1 的实测读写点） |
| **A3** | `BASE_XX` 增强的语义：读基础值时叠加，还是直接改字段？被 `setLevel` 重算时保不保留？ | **读基础值时叠加**（`getBaseAttack() = 字段 + ΣbaseEnhance`），并且**`setLevel` 重算不碰增强条目** —— 这样"改等级"不会把加成冲掉 |
| **A4** | 单体倍率 `individualMultipleArea`、暴击，真实伤害还算不算？ | **算**（你只说了免抗性/元素增伤/全局增伤；这两个是"这一击有多重"的乘数，不算元素增益） |
| **A5** | 条目要不要 `percent` 字段？ | **先不要**。穿透是"抗性 − 平值"（`DamageCalculate:144`），抗性是平值相加；"无视 50% 抗性"是另一个机制（动的是抗性本身），要的话是一个字段的事，但别现在加 |
| **A6** | **抗性怎么叠加**：相加（`0.3 + 0.2 = 0.5`）？取最大？还是后来的覆盖前面的？（今天只有配置一个来源，**没有现成规则**） | **相加**。与穿透/增伤的求和口径一致；做"覆盖/取最大"就得多记一个优先级字段，收益不明 |
| **A7** | **基础值算不算在求和里**（抗性/穿透都一样）：`getResistance(element)` 要不要把"基础条目"也加进去？ | **算**。这样读取只有一个口径；`resetTemporary()` 只删"非基础条目"（`copyFrom` 只带基础条目） |
| **A8** | `Resistance` 和 `Penetration` **共用一个类**（如 `ElementValue`）还是各写一个？ | **共用一个**。字段完全一样（`amount` / `elementSort` / `source`），分成两个类必然写出两份几乎相同的求和代码 |

### 7.3 可以边做边定（不挡第一步）

- `DamageEnhance` 的匹配规则（`damageType` 空 = 全类型？`elementSort` 空 = 全元素？多个命中相加还是相乘？）；
- 一个 `Damage` 多个 `damageType` 时，同一条 `DamageEnhance` 命中多个类型算几份；
- `appliedDamage` 是否严格 = `setHp` 前后差；`calculateDamageResult` 取整前/后；
- `damageSource`（"伤害原因/方式"）的具体类型（`Skill` / `Effect` / 新建 `DamageSource`）；
- 额外回合（`isExtra`）还保不保留"照样触发、但不计时"（`80-EFFECTS.md` §2 第 4 条）；
- `ActionSignal` 那 5 个值怎么收敛（普通行动进队列后，`SKIP_WITHOUT_NEW_TURN` 这类还有没有必要）；
- `EffectContainer` 是否同时解决"效果列表只读化"（`LIVINGTHING-AUDIT` P2，13 处结构写入）；
- `ManaPool` 是否顺势接走 `recoverManaEveryTurn()`。

---

## 8. 本文没做的事 / 不确定项

- **没改任何代码**，也没动 `TODO.md` 以外的施工排序（§6 是建议，不是排期）。
- **容器化会削弱两条现成守卫**（容易假绿，得一起改）：
  1. `EntityCoreTests` 的「属性契约」是**按标量字段**反射上锁的 —— 抗性/穿透/13 个 `*Enhance*`
     搬进容器之后，它们**不再被这条测试覆盖**，必须新增"条目级"断言（基础条目被 `copyFrom` 带走、
     临时条目被 `resetTemporary` 清空、按 `source` 摘除）；
  2. `ConfigTests` 的「混血不变式：反射面 == 手写表的标量键」—— 6 个"只有手写表、没有标量字段"的键
     （`penetration` + 5 个抗性，若走 D2）会让 29 vs 29 变成 23 vs 29，**这条断言必须改**，
     否则它会以一个看不懂的方式变红。
- `appliedDamage` 与"HP 变化量"是否**恒等**没验证：`setHp` 会把值夹到 `[0, 上限]`，且 `renewHp()` 会在上限变化时夹血 ——
  夹取发生在**别的时机**，所以"实际伤害"严格来说要看 `setHp` 前后差（不是"想扣多少"）。
- **`lastTime` 不 +1 的连带影响**（照作者决定，数值不改）：tick **次数不变**，但**窗口整体后移一个回合**。
  两处会真的变：
  1. 在**持有者自己回合里**挂上的效果，今天会在**当回合末**再 tick 一次（所以"挂上就给两次"的效果存在），
     前移之后**这次没有了** ⇒ 只看 `initialEffect` 的效果不受影响，**靠每回合 tick 加层/跳伤害的效果少一跳**
     （例：`PainEntanglement` 用 `level` 当账本、`Erosion` 每回合跳伤害、`HealthRestoreEffect`）；
  2. DOT 从"回合末"挪到"回合头" ⇒ 可以在行动者出手**之前**打死它，
     `FightTurnPastListener:241` 的存活检查必须挪到效果结算之后，否则死人还会出手一次。
  ⇒ 这条**不是重调数值**，是**重跑一遍实测**（`90-STATE.md` §9.6 那种）。
- 回合流程前移的**平衡影响**没法静态评估：需要作者按新口径重跑实测。
- 21 个生产 `makeDamage` 调用点里，有几个是"打给同一个人多次"（连击/追打），
  换成 `Damage` 对象后**每次一段伤害都有自己的 `appliedDamage`**，日志格式要不要跟着变，没定。

---

## 9. 实现记录（Step 1 已完成，2026-11-14 第 42 批）

> 实测：`通过 1099 条，失败 0 条`（基线 1089 → **+10**）、`CHECK OK`、`src` 263 → **265 文件**。
> **Step 2~6 一条没动**（见 §6 的状态列）。

### 9.1 落地了什么

| 改动 | 文件 |
|---|---|
| 新增 `DamageType` 枚举（6 个常量） | `src/cn/gfhnv/game/damage/DamageType.java` |
| 新增 `DamageSource` 接口（`Skill` 实现它，`sourceName()` = 技能名） | `src/cn/gfhnv/game/damage/DamageSource.java`、`skill/Skill.java` |
| `Damage` 重写：三段数值 + 类型集 + 来源 + `isTrueDamage()` | `damage/Damage.java` |
| `DamageCalculate` 拆两段：`resolve(Damage, Skill)` 先算原始值再套公式，新增 `Result` 记录与 `calculate(attacker, target, skill)` 兼容入口 | `damage/DamageCalculate.java` |
| `getDamage` 里写回 `appliedDamage`（`setHp` 前后差） | `entity/LivingThing.java#getDamage` |
| `makeDamage` 三参版（类型可变参）+ 两参兼容重载（按 `SpecialSkill`） | `entity/LivingThing.java` |
| `DamageEvent` 加可变参类型 | `event/DamageEvent.java` |
| 试算写 `appliedDamage` | `skill/Skill#getAnticipatedDamage` |
| **21 个调用点全部显式传类型**（19 个文件，含 `mods/` 3 处） | 各技能类 |
| 守卫 +10 条 | `debug_tools/DamageTests#testDamageStages` |

### 9.2 三个实现上的决定（原方案没写死，这里补上）

1. **原始值用 `double`**，不取整：`0.24 × 生命上限` 这种经常不是整数，提前取整会让最终伤害漂移
   （旧实现是 `double base` → 最后一次 `(long)`，保持一致）。
2. **两段在同一个方法里做完**（`resolve`），不拆成"先建对象、再算"两次调用：
   `Skill#extraDamage` 是技能**临时**改的（李晓焰普攻/大招加减、`PyrohemicPumping` 置零），
   中间插一脚就会读错。
3. **`makeDamage` 保留两参兼容重载**（按 `SpecialSkill` 算）：`MODDING-GUIDE.md` 把
   `user.makeDamage(target, this)` 写成对外 API，直接删签名会让模组编译不过。
   新代码一律显式传；仓库内 21 处已经全传了，两参版由测试守着（`DamageTests` 第 7 条）。

### 9.3 ⚠️ 留给 Step 4 的一个坑（现在不痛，接伤害加成时会痛）

`Skill#getAnticipatedDamage(...)` 做 AI 试算时**只知道技能、不知道伤害类型**
（类型是 `makeDamage` 那一刻手动传的）。今天类型不参与数值，所以预测准确；
但 **Step 4 一旦让 `DamageEnhance` 按类型匹配，AI 就会按默认的 `SpecialSkill` 预判一条普攻/大招**，
预测值与实际值开始漂 —— 解法二选一：
① 给 `Skill` 加 `getDamageTypes()`（默认空 = 未声明），试算读它、`makeDamage` 用传入值覆盖；
② 让 `getAnticipatedDamage` 也收一个 `DamageType...` 参数，由调用方（AI）传。
**Step 4 开工前必须先选一条。**

**2026-11-14 第 56 批：已按选项 ① 落地** —— `Skill#getDamageTypes()` 默认返回 `{SpecialSkill}`，
`getAnticipatedDamage` 用它构造 `DamageEvent`；子类知道自己是什么就覆盖它（不改任何方法签名）。
**注**：`DamageType` 目前仍**不参与数值**（只用于事件标签与日志），所以这条改动是"让试算的标签是对的"，
不会改变任何预测数值；将来若让 `DamageEnhance` 按类型匹配，这条通路已经就位。

### 9.4 顺手修的工具 bug

`check-sources.ps1` 头部承诺"纯 ASCII"，但之前被加进了 **6 行中文注释** ——
Windows PowerShell 5 按 GBK 解析无 BOM 的 `.ps1`，直接抛
`Unexpected token ')' in expression or statement`，整个脚本跑不起来。
已把那 6 行换成英文注释（恢复纯 ASCII），`powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\check-sources.ps1`
实测 `CHECK OK`。踩坑记录见 `notes_for_llm/pitfalls/20-MISTAKES.md` 的「环境 / 工具链」。

---

## 10. 实现记录：Step 2（2026-11-14 第 45 批）

**实测**：`通过 1101 条，失败 0 条`、`CHECK OK`、`src` 267 文件、`mods/` 4 个编译通过。

| 原计划 | 实际做法 | 偏差与理由 |
|---|---|---|
| 独立的 `PropertyEnhanceContainer` 类 | `AttributeProfile` 里一个 `List<PropertyEnhance>` + `add/remove/percentOf/amountOf` | 独立类会让 `/data` 变成 `propertyEnhances:{entries:[…]}` 双层嵌套；先保平铺。方法已经收在一处，以后要抽出去很便宜 |
| `PropertyEnhance.type` 同时有 `BASE_XX` 与 `XX` | 只有 7 个"最终值"类型 | `BASE_XX` 现在没有使用者，加了就是死代码；等真有机制要改基础值时再加 |
| `/data` 13 个键 → 一条列表 | 已实现：`propertyEnhances:[{type, percent, amount}]`（`source` 不进 dump，空列表按既有口径省略） | — |

**顺带治掉的坑**（`80-EFFECTS.md` 的 K1/K2/K4）：同（类型+来源）重复挂是**更新**、按来源摘除**幂等**，
所以"换实例留残留 / 净化跳过收尾 / 收尾无守卫"这三类不再需要靠 `isOn` 兜。

**留下的钩子**：`PropertyEnhance.source` 现在是 `Object`（按身份比较）。
Step 4 做 `DamageEnhance` 时应当用同一套"条目 + 来源"的形状，别再发明第二套。
