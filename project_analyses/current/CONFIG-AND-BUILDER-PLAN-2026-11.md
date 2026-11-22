# 配置加载 / 实体 Builder 改造提案（2026-11-14）

> **2026-11-22 状态：已落地，作为“待拍板 / 施工计划”已失效。**
> 四项待拍板已在 2026-11-15 提交 `ed5098a` 一并落地：`@Temporary` 三面注解、
> `GameRules` 瘦到 8 键、`LivingThing.builder(...)` 静态入口、`facSet*` 保留。
> **现行事实**看 `notes_for_llm/subsystems/70-DATA.md`、`30-WORKFLOW.md` 与
> `project_analyses/current/PROJECT-ANALYSIS-2026-11-15.md`；本文只保留设计史与验收要点。

> **文档分类**：[D3] 方案 ｜ **这是什么**：作者 2026-11-14 提的四件事（给实体加 Builder、`GameRules` 不该装实体数值、
> 配置加载耦合太重、能不能"注解驱动 + 和 `/data` 统一口径"）的**现状核对 + 改造提案 + 施工顺序**
> ｜ **读者**：作者 ｜ **时效**：2026-11-14 快照；**下方“只提方案，没改代码”已过期**。

---

## 0. 先回答"能不能"：**能，而且已经有一半了**

配置面**现在就是注解驱动的**，只是注解只表达了"能/不能"，没表达"写到哪个面"：

| 注解 | 现在管什么 | 位置 |
|---|---|---|
| `@DataField("effects")` | **改数据名**（字段名 ≠ `/data` 键时用） | `game/data/DataField.java` |
| `@DataFlatten` | 组件字段**无前缀并进**父对象的 `/data`（`attributes` 就是这么平的） | `game/data/DataFlatten.java` |
| `@NoConfig("理由")` | **不进配置面**（`/data` 照旧看得见） | `game/data/NoConfig.java` |
| `@NoData` | 连 `/data` 都不进（`host`、`anticipating`） | `game/data/NoData.java` |

键的**读法/写法**还有一张手写表 `EntityKeySpecs`（31 个键）+ 反射兜底（`ReflectionConfigBridge`）——
"反射混血"路线是 2026-10 就定下的（见 `CONFIG-LOADING-DECOUPLING-2026-10.md` §12）。
所以**不需要从零搞一套注解系统**，要做的是把注解的**表达力**补齐（下面 §2.A）。

---

## 1. 现状里确实不合理的三点（都有数字）

1. **`GameRules.json` 里 22/29 个键是"某一只实体"的数值**：
   `flameReaver` 11 个、`actorLiXiaoYan` 10 个、`insectBoss` 1 个；真正全局的只有
   `formula` 5 + `mana` 2。而实体数值本来就有自己的家（`EntityData.json` 的 `base` / `derived` / `classState`），
   于是同一个数有两个入口 —— 已经踩过：**血量两个入口**（`derived.baseHpMax` 赢、规则表改了看不出效果），
   `ConfigTests` 里专门有一组"两个入口对不上就提醒"的断言（2026-10-04 加的补丁式提醒）。
2. **两套键表**：`EntityKeySpecs` 手写 31 键（读法/写法都手抄一遍）+ 反射兜底 29 键 +
   `@NoConfig` 42 键白名单。加一个字段要"改 1 行表"，但**改表的人得同时想清楚三个面**（配置/`/data`/只读）。
3. **`/data` 与配置面口径不一致**：配置面 = `/data` 面 − `@NoConfig`；而 `@NoConfig` 现在**混装两种东西** ——
   ① 真的不该配（`controller`、`uuid`、`participateFight`）；② **临时属性**（五行穿透/增伤、13 个 `*Enhance*`、
   `extraDamage`）—— 后者其实"可以 `/data` 改、只是不该进配置文件"，但注解分不出来，于是 `/data` 也写不了。

---

## 2. 提案：三个面用一个注解表达（A）→ 再把实体数值搬回家（B）→ Builder（C）

### A. 注解补齐"面"的语义（核心）

把 `@NoConfig` 拆成三个正交的开关（一个注解，三个属性）：

```java
@DataField(name = "hp", config = true, data = true, temporary = false)
private long hp;

@NoConfig(reason = "运行期上下文")          // 等价于 config = false, data = true
private Fight participateFight;

@Temporary(reason = "效果挂上来的加成")      // config = false, data = true, temporary = true
private final List<PropertyEnhance> propertyEnhances;
```

- **`config`**：进不进配置文件（`EntityData.json` 的键）；
- **`data`**：进不进 `/data`（现在由 `@NoData` 管，保持兼容）；
- **`temporary`**：**只允许 `/data` 写、不进配置**（本次要的新能力）——
  正好对上作者说的"`/data` 可以改临时属性"。

键名仍然 = 数据名（`@DataField` 已经支持改名）；反射枚举字段就能生成三层清单，
`EntityKeySpecs` 那张手写表**只保留"读法/写法有特殊语义"的键**（`manaGrow` 块、`inventorySlots`、
`element` 要连带重建法力表这类）。

**风险**：反射面与手写表的一致性守卫（现在 29 vs 29）会变成"注解清单 vs 反射面"，
那组断言要重写（`ConfigTests` 第 ⑤ 组）；`@NoConfig` 42 处要逐条判定"是①还是②"。

### B. `GameRules` 只留全局规则，22 个实体数值搬回实体配置

| 现在在 `GameRules.json` | 搬到哪 |
|---|---|
| `flameReaver.*`（11 个：血、容器比例、减伤层数、召唤消耗…） | `EntityData.json` 的 `flameReaver` 模板（`base` / `derived` / `classState`） |
| `actorLiXiaoYan.*`（10 个：燃点上限、记忆消耗、复活比例…） | 同上（`classState`） |
| `insectBoss.baseHpMax` | 同上（`derived.baseHpMax`） |
| `formula.*`、`mana.*` | **留在 GameRules**（它们是公式标定，不是某只实体） |

**做法**：`EntityDataPatcher` 在**构造之后**打补丁（已经有 `recalculateDerivedStats`），
所以这些值不必须在构造期读。要改的读法：`FlameReaver`/`ActorLiXiaoYan` 里那些
`Rule.xxx()` **静态读表**改成**字段读值**（构造时给默认值、补丁后覆盖）。
**好处**：删掉"血量两个入口"那套提醒与测试，配置里"这只怪多强"只看一个文件。
**风险**：`Rule.xxx()` 如果被 `static final` 常量缓存（例如 `DamageCalculate` 的两个常量），
搬家时会漏；`Mod` 也可能读规则表（`MODDING-GUIDE` 有"改规则要改 GameRules"的说法，要同步改文档）。

### C. 实体 Builder（至少给实体加）

现在的写法是 **14 参位置构造器** + 33 个 `facSet*` 链式 setter（两套并存）。提议：

```java
LivingThing blade = LivingThing.builder("千冶·刃", "mortenax_blade")
        .level(125).element(ElementSort.DIRT)
        .hpGrow(36).attackGrow(29).defenceGrow(5)
        .resistances(0, 0, 0.2, 0.3, 0.3)
        .manaGrow(10, 1, 4, 10, 20)
        .controller(new ThinkingControllerAI())
        .build();
```

- `builder(name, id)` 返回一个 `LivingThingBuilder`；`build()` 内部**仍然走那 14 参构造器**
  （保证"构造期铺值顺序、法力初始化时机"一个字不变）；
- **`facSet*` 不删**（模组在用、`MODDING-GUIDE` 写着），Builder 只是把"位置参数"换成"具名设置"；
- 子类不用改：Builder 只造基类骨架，子类仍用自己的构造器（或者给 `LivingThingBuilder` 加 `class` 参数，
  用反射 `new` 子类——**先不做**，反射造实体会绕开子类构造器）。

---

## 3. 施工顺序（每步可独立停，建议按这个顺序）

| 步 | 做什么 | 为什么排这里 | 风险 |
|---|---|---|---|
| **0** | 读完本文，拍板 A/B/C 的顺序与取舍 | 三件事互相牵连（B 依赖 A 的配置面语义） | — |
| **1（C）** | **实体 Builder**：新增 `LivingThingBuilder` + 自测（≥6 条：与 14 参构造器结果逐字段相等、法力表一致、`copy()` 正常） | 纯增量、零行为变化；能立刻替换掉最难读的 14 参调用 | 低 |
| **2（A）** | **注解三开关**：`@DataField` 扩 `config/data/temporary`；`@NoConfig` 42 处逐条分类；`/data` 允许写临时属性 | `/data` 与配置面口径统一的前提 | 中（守卫要重写，`PropertyEnhance` 会是第一个受益者） |
| **3（B）** | **`GameRules` 瘦身**：22 个实体数值搬进 `EntityData`；删"血量两个入口"提醒 | 依赖第 2 步（键要能落在实体模板上） | 中高（要动 3 只实体的读法 + 模组文档） |

---

## 4. 需要作者拍板的点

1. **A 的语义**：`temporary` 是不是就等于"**`/data` 能写、配置不能写**"？
   （另一种读法是"配置能写、`/data` 只读"，那对效果调参没用，我倾向前者。）
2. **B 的边界**：`GameRules` 里的 `formula.*` / `mana.*` 留不留在规则表？
   （我的建议：留 —— 它们改的是"公式标定"，不是某只实体。）
3. **C 的形态**：`LivingThing.builder(name, id)` 这种**静态入口**，还是 `new LivingThingBuilder(name, id)`？
   命名归你（`10-RULES` §8）。
4. **删不删 `facSet*`**：Builder 落地之后，33 个链式 setter 还留吗？（留 = 两套 API 并存；删 = 模组要改。）
