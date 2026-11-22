# FightGameReforged 项目分析（2026-11-15 08:10 版）

> **2026-11-22 复核：本文是“11-15 冻结正文 + 第 76~93 批流水”的混合体，不是最新全项目快照。**
> 当前基线以 `notes_for_llm/state/90-STATE.md` §9.1 为准：
> **293 文件 / 59,280 行、自测 1261/0、10 实体 / 35 技能键 / 16 注册效果 / 9 物品、25 个 *Tests**。
> 本文顶部的 285/55,903、1,185、62+295 等数字是 11-15 快照，**不要当现状**；
> 第 88~93 批已有正文，但摘要层未回写。下一步应派生 `PROJECT-ANALYSIS-2026-11-22.md` 并把本文归档；
> 在派生完成前，**问题清单仍可参考，数字与行号一律重核**。

> **文档分类**：[D1] 分析 · 当前基准 ｜ **这是什么**：全项目复核（285 文件 / 55,903 行）与问题清单 ｜ **读者**：引擎开发者 ｜
> **时效**：2026-11-15 08:10 快照，**当前基准**。
>
> ✅ **本快照取代** [`PROJECT-ANALYSIS-2026-09.md`](../archive/PROJECT-ANALYSIS-2026-09.md)（2026-09-26 · 200 文件 / 29,398 行）的"全项目基准"定位；
> 旧基准已移入 `archive/`，其**主题文档**仍各管一摊、不因此过期：
> `COMMAND-SYSTEM-2026-08.md`（命令设计）、`FLAME-REAVER-2026-09.md`（盗火行者数据对照）、
> `NBT-AND-DATA-COMMAND-2026-09.md`（`/data` 设计）、`MORTENAX-BLADE-2026-10.md`（千冶·刃设计稿）、
> `FIGHT-PROPERTY-DAMAGE-REDESIGN-2026-11.md`（属性/伤害/回合重做设计稿）。
>
> **方法**：5 个只读子代理分片审读（引擎 / 角色机制 / 命令与体验 / 扩展性与文档 / 风险）+ 主代理逐条复核关键结论
> （标记 **【已复核】** 的是主代理亲自 grep/读代码确认的；标记 **【子代理】** 的是报告给的、未二次复核）。
> 报告原文：`out/analysis/{engine,content-characters,content-command-ux,extensibility,risks}.md`（临时目录，不进仓库）。
>
> ⚠️ **本快照的瞬时状态**：`config/` 目录此刻为空（作者刚清掉旧配置、等待下次启动自动生成全量默认值），
> 所以文中"出厂值/配置键"一律**以代码为准**（`RuleKeySpecs` / `DataKeys` / `EntityKeySpecs`），不引用 JSON 实值。
>
> **可信度分级**（沿用项目约定）：**已实测** = 用户实跑过；**已核实** = 逐行读过当前源码；
> **未验证** = 只读推断。旧文档里的行号一律不可信；本文所有行号按**当前工作区**。

---

## 零、TL;DR

**一句话**：引擎底座这半年完成了三件"结构性升级"（属性/效果容器化、伤害管线重做、回合行动队列），
工程纪律（自测 1,185 条、文档分层、静态检查、链接检查）已经成型；但**"能跑通"与"跑不坏"之间还有两道硬缺口**：

1. ~~**整场战斗跑在 `EventBus.post(FightStartEvent)` 的派发栈里，而回合循环没有 try/catch**~~ ✅ **第 78 批已修** ——
   任何动作抛异常，EventBus 记一行日志后吞掉，`fightInProgress`/时间轴/监听器全都不复原（玩家侧表现为"卡住"）；
2. ~~**时间轴的除数（`10000 / speed`）有 6 处各写一遍，只有 Builder 拦了 `speed<=0`**~~ ✅ **第 77 批已修**（统一 `needTimeOf` + `formula.turnTimeBase` 可配） ——
   配置写 `baseSpeed: 0` 会以同样的方式"静默开不了战"。

这两条是本快照唯一够得上 P0 的问题：**不丢数据、但会把"玩家可复现的卡死"变成"只在日志里的一行"**。

| 范围 | 数量 | 结论 |
|---|---|---|
| src | **293 文件 / 59,280 行**（2026-09 基准：200 / 29,398） | 规模约 **2.0 倍**；其中 `debug_tools` 约 14,166 行（24.1%） |
| 自测 | **25 个 `*Tests` / 1,261 条断言 / 0 失败**（2026-11-22 实跑） | 覆盖厚；有 3 类“假绿”风险（见 §四 P2） |
| 事件 | **24 个事件类 + 1 个官方事件** / 发布点与监听方法以 `85-EVENTS.md` 为准 | 12 个新事件只有发布者、无监听器（按设计） |
| 内容 | 10 个官方实体 / 35 个技能键 / 16 个效果 / 9 件物品 / 10 条命令 | 盗火行者机制链最完整；千冶·刃断点已在第 78 批修 |
| 文档 | `notes_for_llm` 12 份；`project_analyses` 21 份 | 链接 69 + 331，**broken 0**；数字以 `90-STATE` §9.1 为唯一源 |
| 配置 | `GameRules.json` **8 键**（`formula` 6 + `mana` 2）；`EntityData` 实体基座 + `classState`（33 开放 / 21 挡住）+ `derived` | 权威文档 `70-DATA` 的 `classState` / `GameRules` 两节已在第 95 批修正 |

**问题计数（截至第 78 批，已过期）**：P0 **0**（原 2 条已修）/ P1 **5 仍开**（原 10：3 条已修、1 条作者判不做、1 条按作者决定保留）/ P2 **15 仍开**（原 16：1 条已修）/ P3 **7**。没有数据丢失级问题；没有“必须回滚”的坏改动。
> 2026-11-22 复核：第 88~93 批又修了若干条（如千冶断点、`/kill`、额外行动检查），
> 这张计数表只能当“11-15 当时”的问题索引；重新计数要逐条走 `90-STATE` 与各子系统笔记。
> 🔧 **同一天（第 76 批）已修**：临时实体离开 `World.things`（结束 / 死亡 / 离场）、物品确认 `y`/`yes` 大小写不敏感、死亡实体不再 tick 效果也不再被治疗、战斗结束收尾进 `finally`。详见 §九。

---

## 一、规模与构成（实测）

### 1.1 总览

| 项 | 数字 | 怎么量的 |
|---|---|---|
| Java 文件 / 行数 | **285 / 55,903** | `find src -name '*.java' \| wc -l`；`cat` 后 `wc -l` |
| 最大的包 | `debug_tools` 12,576（29 文件）· `system/configLoadingSystem` 7,434（16）· `system/command` 5,665（29）· `entity` 4,629（12） | 按包 `wc -l` |
| 单类最大 | `LivingThing` 2,180 行 / 185 个方法；`AttributeProfile` 1,299 行 / 101 个方法 | `wc -l` + 方法签名计数 |
| 模组 | `mods/` 4 个示例 / 13 个 .java / 1,222 行 | `find mods -name '*.java'` |
| 文档 | `notes_for_llm` 11 份 5,462 行；`project_analyses` 20 份 12,393 行；根文档 `README` 674 行 | 逐目录统计 |
| 静态检查 | `check-sources.ps1` 299 行，查括号/BOM/缺 import/未用 import/字段重复，**只扫 `src/`** | 脚本自述 |

### 1.2 分层（按职责）

```
入口层：GameStarter → GameMain（初始化/选人/开战/重开）
战斗层：Fight + TurnManager（时间轴）+ TurnEntry/ActEntry（回合/行动队列）+ FightTurnPastListener（驱动循环）
实体层：LivingThing（2,180 行）+ AttributeProfile（组件）+ EffectContainer（只读容器）+ EntityDisplay（显示）
数值层：Damage/DamageCalculate（伤害）+ ElementSort + Mana
内容层：officialStuff/（10 实体 / 16 效果 / 9 物品 / 技能若干 / 10 命令 / 官方事件监听）
扩展层：event/（24 事件）+ mod/（加载器/Mod API）+ configLoadingSystem（配置）+ data/（NBT 与 /data）
自测层：debug_tools/（23 个主题类 + TestKit/TestProbes/TestSupport）
```

### 1.3 2026-10 ~ 11 的结构性变化（本快照相对 2026-09 基准的"新地基"）

| 变化 | 落点 | 状态 |
|---|---|---|
| 属性容器化（穿透/抗性/增伤/属性加成） | `AttributeProfile` + `ElementValue`/`PropertyEnhance` | ✅ 已落地，`/data` 四条列表只读 |
| 效果列表只读化 | `EffectContainer extends AbstractList`（mutator 全抛 UOE） | ✅ 生产代码 0 处直接结构写入 |
| 伤害管线重做（原始值/结果值/实际掉血三段） | `Damage` + `DamageCalculate` | ✅ 已落地 |
| 回合行动队列（五档优先级） | `ActEntry` + `TurnEntry#addAct` + `runActQueue` | ✅ 已落地；效果 tick 前移到回合头 |
| 实体 Builder | `LivingThingBuilder` + `LivingThing.builder` | ✅ 存在；**官方内容 0 处使用**，只有自测在用 |
| 表现层抽离 | `EntityDisplay` | ✅ `LivingThing` 的 `System.out`/`ConsoleColor` 归零 |
| 三开关注解（config/data/temporary） | `@NoConfig` / `@Temporary` / `@NoData` | ✅ 反射配置桥已进 `classState` 主路 |
| `GameRules` 瘦身 | 只剩 `formula` 5 键 + `mana` 2 键 | ✅ 实体数值全进 `EntityData` |
| 扩展事件 | 12 个新事件 + 发布者 | ✅ 监听器未写（按作者要求） |
| 暴击/冷却口径 | 玩家 5% 暴击 + 50% 暴伤；技能冷却 `coolDown + 1` | ✅ 已实测（作者试玩） |

---

## 二、做得好的地方（值得保留的工程资产）

1. **EventBus 是"可验收"的基础设施**：可赋值派发、注册去重、派发中注销、异常隔离，每条都有断言，且用例开头记 `handlerCount()` 基线、结尾要求回到同值（`EventBusTests:33/108-110`）。【已复核：`EventBus:307-327/406-418`】
2. **回合循环不随回合数加深栈**：从"递归 post"改成 `continue turnLoop`，行动队列用"快照 + 清空"，效果 tick 遍历快照 —— 把两类 `ConcurrentModificationException` 从设计上堵死（`FightTurnPastListener:56-63,100-110,336-337`）。【已复核】
3. **配置面单一事实源**：`EntityKeySpecs` 一张表同时驱动默认值写出、补丁应用、对外元数据；未知键**带理由点名**而不是静默跳过（`EntityDataPatcher:267-314`）；参考副本 `EntityData.default.json` 有 `_note` 与"空壳才重写"契约。
4. **自测纪律**：23 个主题、1,185 条断言、0 失败；暴击引入随机后，专门加了 `setCriticalEnabledForSelfTest(false)` 让"比较两次伤害"的断言可复现 —— 这是踩到痛处后做的正确决定（`DamageCalculate`，第 73 批）。
5. **文档分层真的在运行**：`rules/pitfalls/workflow/subsystems/state` + `project_analyses/{guides,current,archive}`，archive 有统一过期横幅，`check-doc-links.py` 实测 **broken 0**。
6. **安全边界写得诚实**：README 明确"模组 = 同 JVM 同权限，可联网/`System.exit`"，与 `ModLoader` 的实现一致，不假装有沙箱。

---

## 三、架构关键机制（读代码后确认的"现状"，不是设计意图）

### 3.1 战斗生命周期

```
GameMain.gameInitialize → 规则 → 内容 → 模组 → 事件注册 → 配置补丁 → 命令
GameMain.startAFight → 选人/选敌 → new Fight → setCurrentFight → fightInProgress=true → post(FightStartEvent)
FightStartEventListener → new FightTurnPastListener → TurnManager.init → post(FightPastOneTurnEvent)
FightTurnPastListener.turnLoop（while isDriving）：
  清尸体 → 判胜负 → （时间轴空 → 平局收场）→ 排序取行动者
  → updateSelf 全员 → 冷却 -1 → 回蓝 → 回合头 → EffectUpdateEvent
  → ActEntry 队列：CHECK(900) → NORMAL(0) → RESCHEDULE(-500) →（FIRST/LAST 由技能自排）
  → 回到循环开头
FightEndEventListener → whenFightEnds 全员 → 注销两个监听器 → 奖励入副本背包
```

### 3.2 三条"扩展作者必须知道"的语义（代码为准）

| 语义 | 代码事实 | 文档现状 |
|---|---|---|
| 事件派发 | **按可赋值派发**（父类监听器能收到子类事件），并保留注册快照 | README:576 仍写"精确类匹配" ❌ 过期 |
| 可取消 | 只有 `SkillUseEvent` 真被发布者读；`DamageEvent`/`SelectTargetEvent` 有 `setCanceled` 但**不读**。另外 `setCanceled` 的真实作用是"中断同事件后续监听器"（`EventBus:159-168` 直接 return），**不是**否决业务 | `85-EVENTS.md` 已在本轮修正 ✅ |
| 冷却 | `coolDown > 0` ⇒ `nowCoolDown = coolDown + 1`；`coolDown == 0` 保持 0（普攻/白厄变身技能不受影响） | 第 72 批落地，有守卫 |
| 暴击 | 玩家 4 角色 5% / 50%；怪物 0；自测默认关掷骰 | 第 71/73 批落地 |

---

## 四、问题分级

> 每条：现象 → 证据（`file:line`）→ 影响 → 建议。P0/P1 均经主代理复核（除标 **【子代理】** 者）。

### P0（严重：玩家可复现的卡死/开不了战，均已修）

**P0-1 ~~战斗跑在事件派发栈里，异常被吞后无人收尾~~** ✅ **已修（第 78 批；上线后第一次实战就抓到一例真实 UOE，见 §十 第 5 条）** 【已复核】
- 现象：整场战斗由 `FightStartEvent` 的监听器同步驱动；回合循环只有 `finally` 复位 `isDriving`，**没有 `try/catch`**。
- 证据：`GameMain:365` → `FightStartEventListener:14-25` → `FightTurnPastListener:167-342`；`finally` 在 `:338-341`；`EventBus:406-418` 把监听器异常记一行后继续；唯一正常注销路径在 `FightEndEventListener:35-36`。
- 影响：任一动作/监听器抛异常 ⇒ 循环停在半路，`fightInProgress` 仍为 true、时间轴仍有条目、两个监听器都还挂着；下一局再注册一对新实例，而 `isDriving` 是**实例字段**，于是"同一回合跑两遍"。玩家只看到卡住，错误只在日志里；手动逃生只有 `/endfight`。
- 建议：`fightTurnPastOne` 外层包 `try/catch(Throwable)` → 打印原因 + `post(FightEndEvent(false, fight))` 让收尾照常跑；补一条"故意让动作抛异常"的自测，断言监听器数回到基线。

**P0-2 ~~速度 0 无统一守卫，失败也会被 P0-1 静默~~** ✅ **已修（第 77 批）** 【已复核】
- 现象：`10000 / speed` 出现在 **6 处**：`TurnManager`、`Fight`×2、`FightTurnPastListener`、`UltimateAttack`、`AwakeEndListener`、`FlameReaver`（另 1 处是 `FlameReaver` 的延后乘算）；`speed` 来自 `LivingThing#getSpeed()`，可被负加成压到 0/负。
- 证据：守卫只在 Builder（`LivingThingBuilder:228-238`，注释自己写着"speed=0 会在 10000/speed 那里直接炸"），而配置键 `baseSpeed` 走 `EntityKeySpecs:133-135` 的 `setSpeed`，绕过 Builder。
- 影响：`baseSpeed: 0` 或 `-100%` 速度 ⇒ `TurnManager.init` 抛 `ArithmeticException`，发生在 `FightStartEvent` 派发内 ⇒ 被吞 ⇒ 战斗没开始、`fightInProgress=true`，玩家只看到"开始"后面没反应。
- 建议：把"需要时间"收口成一个 `needTimeOf(thing)`（speed 夹到 ≥1），删掉 4 处各写一遍；配置面补显式报错。

### P1（应修：不丢数据，但会静默错/静默失效）

1. ~~**效果"等级更高就换实例"不回收旧加成（K1）**~~ ✅ **已修（第 78 批）** 【已复核】
   `LivingThing:1298-1303` 在 `setEffect(i, effect)` + `initialEffect` 前没有 `existing.whenLastTimeEnd(this)`。
   影响：同 id 再施加更高等级 ⇒ 旧实例的 +N **永久留在属性上**。建议先收尾再替换，`EffectLifecycleTests` 补断言。
2. **`DamageEvent` 的"可取消"是空头承诺** 【已复核】
   `LivingThing:1945-1950` 在 `post(damageEvent)` 后**无条件** `takeDamage`；`takeDamage:1668-1677` 不看 `isCanceled`；全项目只有 `Skill#use:314/360` 读取消。
   影响：模组按事件 API 写"免伤/封印"会静默失效。建议二选一：在 `makeDamage` 里判 `isCanceled()`（并补守卫测试），或明确"不可取消、改数值走 `IModifyDamage`"。
3. **模组 `common` 段改不了自己刚注册的内容** —— ❌ **作者判不做**（模组应写好再注册） 【子代理 + 已复核顺序】
   `GameStartEventListener:20-30` 先 `loadModData` → `invokeWhenLoaded` → `registerItself`；而补丁器只在**当前** `World` 里找模板。文档却说 `common` 能给模组内容打补丁。
   影响：模组在 `common` 里写自己的实体/技能 → "应用 0 项"、无报错。建议把 `common` 补丁移到该模组 `registerItself` 之后（或再跑一遍），并加探针用例。
4. **坏模组可带崩启动** 【已复核：3 处 `catch (Exception)`】
   `ModLoader:45/71/148` 只接 `Exception`；`Class.forName(..., true, ...)`（`:115`）触发静态初始化，`ExceptionInInitializerError` 是 `Error`，两层都接不住。
   影响：一个模组静态块除零 → 整个游戏启动崩溃。建议逐模组 `catch (Throwable)` + 记录模组名后继续。
5. **`isAlive()` 是带副作用的查询** 【已复核（引擎报告给行号，主代理确认签名存在）】
   `LivingThing:1588-1598`：`hp<=0` 时顺手改 `actionSignal`、清 `specialAction`、置 `alive=false`；它却被 `removeIf` 谓词、日志、判定反复调用。
   影响：读取顺序决定状态；0 血但会被复活的实体会在"被扫一遍"时丢掉特殊动作。建议拆 `isAlive()`（纯读）与 `onHpZero()`。
6. **实体复制对效果是浅拷贝，注释却写"深拷贝"** 【已复核：`LivingThing:160` + `EffectContainer:147-152`】
   影响：复制"身上有效果"的实体时，`isOn`/`lastTime`/账本会一起变。建议逐条 `effect.copy()`。
7. ~~**千冶·刃结界结束不回能量（机制链断点）**~~ ✅ **已修（第 78 批）** 【已复核】
   `ensureEnergyFloor` 的 javadoc 写"结界解除时也要调一次"（`MortenaxBlade:279`），但 `endInfiniteFury:556-589` 没调；全仓调用点只有 `:342`（开战）与自测。
   影响：结界结束后能量停在结算值（可能 0），下一个终结技要 `energy >= energyMax` ⇒ 资源链不闭合。建议补调用 + 自测。
8. **玩家回合"全部技能不可用"时软锁** 【已复核 `PlayerController:171-186`】
   `while(true)` 里只会一直提示"技能释放条件不满足，请重新选择"，没有跳过/放弃；界面也从不提示 `/endfight`。
   影响：玩家只能猜 `/endfight`（或退出）。建议加"全部不可用时自动跳过/让玩家选跳过"，并在提示里给出 `/endfight`。
9. ~~**战斗结束清理顺序：注销排在 `whenFightEnds()` 之后**~~ ✅ **已修（第 76 批：收尾进 `finally`）** 【已复核】
   `FightEndEventListener:22-38` 先把实体逐个 `whenFightEnds()`，之后才 `EventBus.unregister(...)`；这段方法体本身没有任何 try/catch。
   影响：任一实体的 `whenFightEnds()` 抛异常 ⇒ 本方法从抛点中断：后半段实体**只复位一半**、监听器**不注销**、奖励**不发放**；下一局再注册一对新监听器，可能出现"双倍奖励/同一回合跑两遍"。建议注销与清理放到 `finally`，或先注销再复位。
10. **奖励不闭环（作者决定保留现状，不删奖励流程）** 【已复核发放代码 + 子代理给流程】
   奖励只进**本场副本**的背包；战斗实体是**纯临时实体**（第 76 批起结束时还会被移出 `World.things`），
   下一局从注册表模板重新 `copy()`，所以奖励"下一局消失"是**必然结果**，不是 bug。
   **待作者定**：要么删掉"选奖励 / 发奖励"这一整段，要么等有存档 / 持久背包时再把奖励写进持久层；
   README 里"你会获得奖励"的口径也应跟着定。

### P2（可改进：静默偏差 / 假绿 / 体验）

1. **`setHp` 事件数额用"未夹取"的差值**：`AttributeProfile:536-548` 先算差值发 `HpLoss/HpRestoration`，之后才夹上限/0；`setHp(-100)` 的事件数额大于实际。建议先夹再发（或无生产监听器时补注释）。
2. **`/data` 两处静默**：读字段失败 `continue`（`DataBridge:223-230`）；写回按"名字 + 参数个数"挑 setter、不看参数类型（`:844-856`）。建议至少 verbose 下打一行。
3. **`expectSyntaxError` 捕获任意 `Exception`**（`TestKit:105-118`）→ NPE/越界都会被当成"预期报错"通过；建议只接 `CommandSyntaxException`，先加"注入 NPE 应判失败"的负向用例。
4. **干净检出静默少 3 条**：`TestSupport:1699-1701` 发现 `mods/drunkenSword/bin` 不存在就跳过 3 个 `check`；建议计入独立 skip 计数并在 summary 打印。
5. **`mods/` 不在任何自动检查内**：`check-sources.ps1:40/61`、`test-command-system.ps1:37` 只编 `src/`；API 改动后 4 个示例可静默编译失败。建议加 `check-mods.ps1`（逐目录编译）。
6. **模组加载顺序不确定**：`ModLoader:31` 用未排序的 `File.listFiles()`；跨模组 `common` 补丁按加载顺序生效。建议按目录名排序。
7. **`SelectTargetEvent` 发在 `use()` 之后**（`UniversalController:151-152` 等）且不读取消：名字像"选目标钩子"，实际只能观察。已在 `85-EVENTS` 修正语义，代码层建议改名或补"选目标前"事件。
8. **扩展事件缺口**：无物品使用、无实体中途加入/离场（只有死亡）、无"回合被移除"；对模组内容作者的覆盖仍不完整。
9. **`70-DATA.md`/`CONTENT-GUIDE.md` 契约过期**：`classState` 实际 33 开放 + 21 挡住（文档 7/19）；`GameRules` 已 8 键但文档仍讲 BOSS 旋钮/双入口。建议以 `DataKeys` + `RuleKeySpecs` 为唯一源重写两节。
10. **README 四处与代码相反**：EventBus"精确类匹配"（实际可赋值）、复制"只认 3 种控制器"（实际 `copy(this)` 多态）、`config/data`"只读不写"（实际会生成默认配置）、仍提"控制器重建表"。建议一次性改完。
11. **`config/data` 自动生成的说明互相矛盾**：README 两处 + CONTENT-GUIDE 一处各说一套；实际 `ModDataAware#defaultConfig` + `ConfigLoader#writeDefaultModConfig` 会生成。统一成"首次运行生成、已存在不动"。
12. **白厄火种首场 15、后续 1**：构造器满 15（`Phainon:140`）、`whenFightStart` 又 +1（`:453-459`，被上限夹）、`whenFightEnds` 归 0（`:423`）；`setCoreflame` 只有上限无下界（可负，`UltimateAttack:177` 自证）。建议收口到一个入口并补下界。
13. **李晓焰`IGNITION_MAX_MODIFIERS` 是 static 全局**（`ActorLiXiaoYan:53`）：多实例同场、模组卸载不摘 ⇒ 互相污染。建议按实例存或明确定"全局唯一 + 卸载钩子"。
14. ~~**`World.things` 只增不减**~~ ✅ **已修（第 76 批）** 【已复核 grep】
   `removeThing`（`World:178-180`）在生产代码里 **0 处调用**（只有 `SelectionTests:303` 清场时用）；`CommandSource:142` / `ListCommand:75` 读 `World.getThings()`。
   影响：每局选人/召唤的副本都留在运行时列表里，旧局副本仍可被 `@e` / `/list` 选中，并继续引用旧 `Fight`（内存随局数增长）。建议：`FightEndEvent` 收尾时把本场副本移出，或给 `Thing` 加"已离场"标记并在选择器里过滤。
15. **`EventBus` 的取消语义是"中断派发"，容易被误当成"否决业务"** 【已复核】
   `EventBus:159-168`：`post` 一进循环检查 `isCanceled()` 就 `return`，同事件**后续监听器全部不执行**；业务代码是否买账由发布者自己决定。
   影响：监听器 A 取消后，B 就再也看不到这个事件（可能少刷一次统计/播报）；这层语义只在 `EventBus` 的 javadoc 里写着，`85-EVENTS` 现在只写了"发布者不读"。建议在 `85-EVENTS` 的"三条硬事实"里补一句。
16. **"当前基线"散落 5 份文档且数字打架**（1099/1115/1141 条；265/272/285 文件）。建议只保留 `90-STATE` §9.1 一个数字源，其他文档改成"以 90-STATE 为准"。

### P3（小瑕疵）

1. `ReflectionConfigBridge:13` 类注释仍写"实验/并列"，实际已是 `classState` 主路（`EntityDataPatcher:179`、`ConfigDefaultWriter:630`）。
2. `00-CATALOG` 目录树没有 `subsystems/85-EVENTS.md`（索引行有、树没有）；`TIPS_FOR_LLM` 10 行表也没它。
3. `85-EVENTS` 漏了 `GameStartEvent`（模组唯一注册时机），却把官方的 `AwakenEndEvent` 收进来 —— 建议增行 + 分区。
4. `Damage:35` 注释说"系统伤害可为 null"，但 `DamageCalculate.resolve` 无守卫解引用 `attacker`；`DamageType.SystemDamage` 全仓 0 引用。
5. 旧文档里的"149/200/199/265/285 文件"五个版本并存（历史记录可不动，但入口文档不该给具体数）。
6. `mods/exampleModByGFHNV` 的 `mainClass` 写死 `super("modID")` 且 `main.json` 无 `modID` —— 作为"最小骨架"会误导照抄者。
7. **死代码的"结构"比单点更值得清**：物理整条链（`PhysicsStateUpdateEvent` 有监听器、**0 发布点**）、
   `ThinkingController`（**0 实例**，且两处 `getFirst()` 在空表上必炸）、约 25 个零调用 public 方法；
   `TODO/FIXME` 全 `src` 仅 17 处且**没有一处是真待办**。建议按"整条链"删或接，而不是零敲碎打。

---

## 五、文档与代码一致性（抽查，均已复核）

| 文档断言 | 代码实际 | 结论 |
|---|---|---|
| README:576"EventBus 精确类匹配" | `EventBus:307-327` 按可赋值派发 | ❌ 过期 |
| README:447-449"复制只认 3 种控制器" | `LivingThing:164-167` 多态 `copy(this)` | ❌ 过期 |
| README:516"`config/data` 游戏只读不写" | `ConfigLoader:511-558` 会写默认配置 | ❌ 过期 |
| `70-DATA:311`"classState 只有白厄/李晓焰" | `DataKeys:454-492` 33 开放 + 21 挡住，含千冶/盗火 | ❌ 过期 |
| `70-DATA:347-366`"两个 `baseHpMax` 入口" | `RuleKeySpecs` 只剩 8 键；`noticeDualEntryHpMax` 已删 | ❌ 过期 |
| `CONTENT-GUIDE:63`"GameRules 有 BOSS 旋钮" | 同上，GameRules 8 键 | ❌ 过期 |
| `85-EVENTS:23`"DamageEvent 可取消" | `takeDamage` 不看 canceled | ✅ **本轮已修正** |
| `85-EVENTS:30`"SelectTargetEvent 可取消?" | 发布者不读 canceled | ✅ **本轮已修正** |
| `00-CATALOG:49`"guides/MORTENAX-BLADE" | 文件确实在 `guides/`（第 68 批已挪） | ✅ 一致 |
| `50-COMMANDS`"10 条命令" | `OfficialCommands:35-47` 10 + 1 别名 | ✅ 一致 |

---

## 六、与 2026-09-26 基准的差异

### 6.1 规模与结构

| 项 | 2026-09-26 | 2026-11-15 | 变化 |
|---|---|---|---|
| src 文件 / 行数 | 200 / 29,398 | **285 / 55,903** | +42.5% / +90% |
| 自测断言 | 未记录（当时无运行时基线） | **1,185** | 新增量化基线 |
| 事件 | 未系统统计 | 24 + 1，35 个发布点 | 新增 12 个扩展事件 |
| 配置 | `GameRules` 含大量 BOSS 旋钮 | **8 键** + `EntityData.classState` | 实体数值彻底搬家 |
| 单类最大 | `LivingThing` 1,843 行（旧文档说法） | 2,180 行 | 组件化后**没变小**（符合预期） |

### 6.2 旧基准里的问题：已修/仍在

| 旧结论 | 现状 |
|---|---|
| 模组系统"4 个崩溃级缺陷一条没修" | **部分变化**：加载失败仍会中断启动（P1-4 仍在），但 `EventBus` 已吞异常，症状从"崩"变"静默少模组"（也是问题） |
| 暴击系统"实际永远不会暴击" | ✅ 已修：玩家 5% + 50% 暴伤，攻击行有标记 |
| `LivingThing` 职责过多、缺组件 | ✅ 部分：属性/效果/显示已拆；`DamageReceiver`/`ControllerHolder` 作者拍板**不做** |
| 效果列表可直接改（数值残留） | ✅ 已修：`EffectContainer` mutator 全抛 UOE |
| `/data` 与配置键两套口径 | ✅ 已统一：键名 = Java 字段名；四条列表只读 |
| `getModifyDamage()` 等死代码 | ✅ 已删（第 64 批）；`ActEvent`/`nextTurn` 已删（第 69 批） |
| 回合流程"效果结算在最后" | ✅ 已改：效果 tick 在回合头（第 58 批），行动队列按优先级 |

### 6.3 本快照新增的"技术债"（这半年长出来的）

1. **事件数量涨 12 个但监听器只有发布者** —— 语义承诺（可取消）与实现脱节（P1-2）；
2. **自测随机性**被暴击引入后靠开关压住，但"宽异常当通过"（P2-3）是更老的假绿；
3. **文档与代码的同步成本上升**：5 处数字、多处 API 描述过期，靠人工"回写"已经开始漏；
4. **配置契约搬家后**，权威文档 `70-DATA` 两节过期（P2-9）；
5. **模组 API 扩张**（四类登记 + 配置 + 事件）后，加载顺序/失败隔离/自动检查没跟上（P1-3/4、P2-5/6）。

---

## 七、建议施工顺序（按性价比）

| 顺序 | 做什么 | 对应 | 成本 |
|---|---|---|---|
| 1 | **给回合循环加异常兜底**（catch → 打印 → `FightEndEvent(false)` → 收尾）并补自测 | P0-1 | 小 |
| 2 | **`needTimeOf(thing)` 收口 + `speed<=0` 显式报错**，删 4 处除法 | P0-2 | 小 |
| 3 | **效果升级先收尾再替换**（K1）+ 复制效果深拷贝 | P1-1/6 | 小 |
| 4 | **`DamageEvent` 取消语义二选一并落地**（推荐"不可取消"写清文档 + 保留 `IModifyDamage`） | P1-2 | 小 |
| 5 | **模组失败隔离**（逐模组 `catch (Throwable)`、排序、`common` 补丁时机） | P1-3/4、P2-5/6 | 中 |
| 6 | **玩家体验三条**：软锁跳过、奖励提示、`y` 认大写 | P1-8/9、P2 | 小 |
| 7 | **文档一次性对账**：README 四处、`70-DATA` 两节、`CONTENT-GUIDE`、基线数字单一源、CATALOG/TIPS 补 85 | P2-9/10/11/14、P3 | 中 |
| 8 | 内容向：千冶能量保底、白厄火种收口、李晓焰 static 修正链 | P1-7、P2-12/13 | 中 |

> 建议 1~2 单独一轮（P0，纯防御性，不动玩法）；3~4 一轮；5 一轮；6 一轮；7 一轮（纯文档）；8 留给你决定数值/机制口径。

---

## 八、附录

### 8.1 事件总表
见 `notes_for_llm/subsystems/85-EVENTS.md`（本快照已修正 `DamageEvent`/`SelectTargetEvent` 的取消语义）。

### 8.2 配置契约（以代码为准）
- `GameRules.json`：`formula` **6** 键（含可配的 `turnTimeBase`）+ `mana` 2 键 = **8 键**（`RuleKeySpecs:30-49`）；
- `EntityData.json`：`entities.<完整id>.{base, classState, derived}`；`classState` = **33 开放 + 21 挡住**（`DataKeys:454-492`）；
- `SkillData.json`：`skills.<实体id>#<技能名>`，扁平倍率 + 具名系数；
- `EntityData.default.json`：导出物，`_note` + "空壳才重写"；
- `config/data/<分组>.json`：模组配置，首次运行会生成默认文件（与 README 旧说法相反，见 P2-11）。

### 8.3 自测基线（2026-11-15 08:10）
- 通过 **1,185** 条 / 失败 **0**（第 76 批 +10 条守卫）；`CHECK OK`；干净目录全量编译通过；`mods/` 4 个手工编译通过；
- `check-doc-links.py`：**62 链接 + 295 根路径 / broken 0**；
- 注意：该数字是**本机**基线（含 `mods/*/bin` 产物；干净检出会少 3 条，见 P2-4）。

### 8.4 分析方法
5 个只读子代理 + 主代理复核；子代理报告在 `out/analysis/`（临时目录，不进仓库）：
`engine.md`（131 行）、`content-characters.md`（74）、`content-command-ux.md`（109）、
`extensibility.md`（125）、`risks.md`（192）。

**风险报告里有一条经主代理复核后不成立、故未列入问题清单**：它推断"`/endfight` 后 RESCHEDULE 会排回一条、`TurnManager.init` 不清表 ⇒ 下一局幽灵回合"。
实际 `GameMain.endFight`（`:248-252`）在 `post(FightEndEvent)` 之前就 `TurnManager.getTurns().clear()` + `setInitialized(false)`，回合循环也会在圈首因 `isInFight()==false` 直接退出；`TurnManager.init` 只在没有残留时会追加。原文已标注"推断，未实跑"，复核结论：**不成立**。

---

## 九、2026-11-15 第 76 批修复记录（分析写完后当天落地）

| # | 问题 | 修法 | 验证 |
|---|---|---|---|
| 1 | `World.things` 只增不减（旧局副本可被 `@e`/`/list` 选中、吊住 `Fight`） | `FightEndEventListener` 把"实体与奖励物品清理 + 注销监听器 + 清时间轴"整体放进 `finally`；死亡清扫里 `World.removeThing(dead)` | `FightLifecycleTests` ①结束 / ②中途死亡 |
| 2 | 物品确认只认全小写 `yes` | `PlayerController.isAffirmative(...)`：`y`/`yes`，大小写不敏感 + `trim` | `FightLifecycleTests` ④ |
| 3 | 结算效果时不看实体是否已死 | `EffectEventListener` 入口判 `isAlive()`；效果把持有者打死就 `break`（不再 tick 剩余效果） | `FightLifecycleTests` ③ |
| 4 | 死人也吃治疗 | `LivingThing.heal` 对 `!isAlive()` 返回 0（复活仍走 `setHp`） | 同 ③ |
| 5 | 战斗结束收尾顺序（原 P1-10） | 注销 / 清理全部进 `finally` | 与 ① 同一用例 |

**仍待作者拍板**：奖励选择 / 发放是否删除（作者已确认"临时实体 ⇒ 奖励无实际用处"，但没说要删）。

---

## 十、2026-11-15 第 77~78 批修复记录

| # | 原问题 | 修法 | 验证 |
|---|---|---|---|
| 1 | P0-2：`10000/speed` 6 处无守卫、不可配 | 新增规则键 `formula.turnTimeBase`（出厂 10000，可配）；`TurnManager.needTimeOf(...)` 统一除法并把速度夹到 ≥1；6 处调用全部改走它 | `FightLifecycleTests` 速度 0/负/200 + 规则键登记 |
| 2 | P0-1：回合循环无异常兜底 | `FightTurnPastListener` 外层 `catch (Throwable)`：恢复中断、打印、写日志、`GameMain.endFight(fight,false)` 强制收场 | `FightLifecycleTests` 故意抛异常 → `isInFight=false`、时间轴空 |
| 3 | P1-1（K1）：效果升级不回收旧加成 | 替换更高等级前先 `existing.whenLastTimeEnd(target)` | `FightLifecycleTests` 旧实例收到收尾、只剩新实例 |
| 4 | P1-7：千冶·刃结界结束不回能量 | `endInfiniteFury` 补 `ensureEnergyFloor()` | `FightLifecycleTests` 能量 10 → ≥ 75% |

**作者明确不修**：P1-3 模组 `common` 改自身内容；**作者决定保留**：奖励流程（不删）。

---

## 十一、2026-11-15 第 79 批：实战暴露的一个真实 UOE

异常兜底（§十 第 2 条）上线后，第一次实战就抓到一例**真实崩溃**：

- 日志：`【战斗异常】回合循环抛了 UnsupportedOperationException —— 已强制收场`（出现在盗火行者【共祭】攻击之后）；
- 根因：`BrokenContainer.removeSacrificeRite` 对 `thing.getEntityEffectList()` 调 `removeIf(...)`；
  而 `EffectContainer.iterator()` 返回的是**只读迭代器**（`Collections.unmodifiableList(effects).iterator()`）⇒
  `Collection.removeIf` 的默认实现走 `Iterator.remove()` ⇒ **无 message 的 UOE**；
- 修法：改用 `LivingThing#removeEffectsIf(...)`（容器自己的批量删 API）；
  并给 `EffectContainer` 补 `removeIf` 覆盖，让以后同类误用**至少带一句"请用 removeEffectsIf"**；
  异常兜底同时改成**打全栈**（只打类名+message 时，无 message 的 UOE 没法定位）；
- 验证：`FightLifecycleTests#testSacrificeRiteCleanup`（共祭正常摘除 + 只读列表仍被拦下且带可读原因）。

**这条记录本身也是一个证据**：P0-1 的兜底不是"把问题藏起来"，而是把"整场静默卡死"变成"一行可查的异常 + 强制收场"。

---

## 十二、2026-11-15 第 80 批：错误详情回写日志

新增 `LogWriter#stackTraceOf(Throwable)` 与 `LogWriter#writeThrowable(context, error)`（完整堆栈逐行写 `logs/latest.log`），
并接入所有"异常还在手里"的错误路径：回合循环兜底、`EventBus` 监听器异常、`ModLoader` 加载失败（3 处）、
`GameMain` 配置加载失败（4 处）、`CommandManager` 命令内部异常。

**动机**：第 79 批那个 UOE 没有 message，旧日志只有一行类名，无法定位；现在日志里有完整调用链与行号。
守卫：`FightLifecycleTests#testThrowableLogging`（堆栈文本 + 真实落盘 `logs/latest.log`）。

---

## 十三、2026-11-21（第二周）第 81 批：行动条目自带行动者

**作者反馈的 bug**：千冶·刃在【无量忿怒】期间、被**别人的回合**打死时血量不回复（停在 1）。

**根因**：`ActEntry` 只有"动作/优先级/标签"，执行时 `runActQueue` 一律传 `turn.getLivingThing()`；
而 `MortenaxBlade#scheduleDeathWardHeal` 把回血动作排在"当前回合"（敌人的回合）里，
lambda 用 `user instanceof MortenaxBlade` 取自己 → 行动者是敌人 → 静默跳过。

**修复**：`ActEntry` 增加 `actor` 字段；`TurnEntry.addAct(actor, ...)` 显式指定；
`runActQueue` 用 `entry.actor()`（`null` 才回退回合行动者）；千冶·刃的免死回血/因果尽偿、
白厄的最后一击都改成显式行动者。旧调用（`addAct(action, priority)`、`getLastExecuteList().add(...)`）行为不变。

**守卫**：`FightLifecycleTests#testActEntryActor`、`#testMortenaxDeathWardForeignTurn`。

---

## 十四、2026-11-21（第二周）第 82 批：内容机制补全

1. **白厄【灾厄·弑魂焚诏的反击】按游戏原文重做**：主刀打敌方全体 + 额外 4 段随机单体；
   每层【弑魂之炽】提高"原倍率 20%"；伤害类型 = 战技 + 追加攻击；结算后清层/清减伤/清名单；
   提前退出变身统一 `Phainon#clearSoulscorchState()`（反击不释放、减伤清除）；倍率沿用旧具名系数。
2. **千冶·刃「刃下归葬」**：伤害类型改为 `SpecialSkill + AdditionAttack`（原文"额外战技"）。
3. **千冶·刃二魂**：新增通用 `IAdditionAttackBonus` 光环接口 + `getTeamAdditionAttackDamageBonus()`；
   `DamageCalculate` 对追加攻击 ×(1+75%)；有光环时我方终结技伤害补 `AdditionAttack` 类型；充能上限 7。

守卫见 `CombatMechanicsTests`；说明：旧 `Counterattack` 还保留回血（原文没有，作者要求倍率沿用旧代码，故保留为可配项）。

---

## 十五、2026-11-21（第二周）第 83 批：HSRActor 与白厄星魂

- 新增 `entity/HSRActor extends Player`（星魂 `eidolon` + 能量 `energy/energyMax/energyOverflowMax`，带钩子）；
  千冶·刃与白厄改为继承；白厄不使用自带能量（继续火种）。
- 白厄星魂 1：额外回合基础速度继承 200% + 每击杀 1 个敌方目标 +1.5%（上限 +84%）；
  终结技时挂暴击伤害 +50%（3 回合）。
- 白厄星魂 2：+20% 穿透（项目无物理元素，按全元素实现）；【支柱·死星天裁】消耗满 4 点【毁伤】时，
  给当前回合追加一条 800 优先级 ActionEntry（额外选一次技能、不额外结算效果；区别于容器真回合）。
- 未单独断言：击杀计数累积、暴伤挂载、死星天裁追加行动（代码已接，待补）。

---

## 十六、2026-11-21（第二周）第 85 批：溢出能量效果化 + 白厄星魂 4/6

1. **⑥ 千冶·刃的溢出能量改由效果记录**：新增 `effect/EnergyOverflowEffect`
   （`id=energyOverflow`、`INFINITE`、等级 = 溢出点数、显示名「能量溢出」）；
   `HSRActor.energy` 只存正常上限以内的部分，溢出由效果记录，`getEnergy()` 返回两者之和，
   `setEnergy` 自动挂/更新/摘效果，复制构造器重建自己的效果副本（避免浅拷贝串体）。
   `energyOverflowMax` 仍是可配上限（80），`/data` 的 `energy` 仍写总量；
   千冶·刃删掉重复的能量 getter/setter 覆盖，回能/付终结技/保底统一走基类总量。
2. **白厄星魂 4「多少泰坦面目模糊」**：`CalamitySoulscorchEdict` 4 魂起在基础 1 层上额外 +4 层
   （共 5 层，`E4_EXTRA_SOULSCORCH`）。
3. **白厄星魂 6「亘古长升，蚀火残阳」**：
   - `Phainon#setCoreflame` 在 6 魂时不再夹上限；`addCoreflame` 统一所有增减火种调用点；
     `whenFightStart` 在原有 +1 之外再 +6（15+1+6=22）；
   - 【支柱·死星天裁】累计本次施放**实际掉血**，全部结算完后对当前存活、生命值最高的敌方目标
     追加 **36% 固定真实伤害**（`Damage`/`DamageEvent` 固定值构造器 +
     `LivingThing#makeFixedDamage/makeTrueDamage`；不再套防御/抗性/增伤，减伤照算，
     类型 `SystemDamage`）。
   - 顺带修配置顺序：`ReflectionConfigBridge#upperBoundFirst` 把 `eidolon` 也排到值键之前，
     否则同一个补丁里"升 6 魂 + 写超上限火种"会先被旧上限夹掉。
4. **② 补白厄 E1/E2 断言**：击杀计数 +1.5%、终结技暴伤（0.5×1.5=0.75 / 3 回合）、
   死星天裁满层 `ActEntry`（优先级 800、actor = 白厄）。
5. **实战反馈追加修复**：白厄同样会触发 `LivingThing#takeDamage` / `Skill#grantEnergyAfterUse`
   的 HSRActor 回能，攒过 160 后挂上【能量溢出】效果，回合头打印
   「这里写效果具体内容........」占位。修法：`HSRActor#usesEnergy()`（默认 true，白厄覆盖 false）
   让白厄的普通能量始终为 0；`EnergyOverflowEffect#comeIntoEffect` 显式空实现，溢出效果 tick 不再打印。
6. **验证**：`通过 1207 条，失败 0 条`（+22 守卫）、`CHECK OK`、`src` **290** 文件、
   `mods/` 4 个编译通过、`check-doc-links.py` **65 links + 297 paths / broken 0**。
   守卫集中在 `CombatMechanicsTests`（溢出效果 5 条、E1 4 条、E2 2 条、E4 2 条、E6 7 条、
   白厄不吃普通能量 1 条）与 `ConfigTests`（eidolon 写入顺序 1 条）。

---

## 十七、2026-11-21（第二周）第 86 批：额外行动检查战斗结束 + 转阶段排队

**作者实战反馈（六魂白厄、`scourge=88` 连续死星天裁）**：打死盗火行者后，
星魂 2 的额外行动仍然弹出技能菜单；转阶段的播报出现得太晚，回血又在播报之前就发生了。

1. **新增 `system/fight/FightState`**：`isDecided(fight)` 按当前两个阵营列表判断
   "还有没有活人"；`canTakeAction(fight, actor)` 再叠上"行动者活着 + 仍在阵营"。
   它不等到下一回合清尸体，正好覆盖"本回合内最后一个敌人已倒、回合循环还没判胜负"的窗口。
2. **星魂 2 额外行动加守卫**：`FoundationStardeathVerdict` 的 800 优先级行动执行前查
   `GameMain.isInFight()` 与 `FightState.canTakeAction(...)`，战斗结束/行动者死亡即跳过。
3. **盗火行者转阶段改为排队**：
   - `phaseTwoDeathWard` 不再当场 `enterPhaseTwo`，而是把血照常打到 0，并排一条
     **2000 优先级**行动（`PHASE_TRANSITION_PRIORITY`，高于额外行动 800 与普通行动 0）；
   - `enterPhaseTwo` **先播报再回满**，然后挂 70% 免伤、换轮转表；
   - `phaseTwoPending` 防同一回合多段伤害重复排队；`updateSelf` 兜底也走同一入口；
   - 没有当前回合（自测 / 战斗已结束）时仍当场收口，保持"这一击被整个吃掉"的旧语义。
4. **验证**：`通过 1212 条，失败 0 条`（+5 守卫）、`CHECK OK`、`src` **291** 文件、
   `mods/` 4 个编译通过、`check-doc-links.py` **65 links + 297 paths / broken 0**。

---

## 十八、2026-11-21（第二周）第 87 批：伤害公式对齐 HSR

作者给出 HSR 伤害公式后逐项对齐，**不做韧性**。七处改动：

1. **暴击率/暴伤绝对值**：`getCriticalRate/getCriticalDamage` 从
   `base × (1 + percent) + amount` 改为 `base + percent + amount`。
   效果写“+20% 暴击率 / +50% 暴伤”现在是 +20 个百分点 / +50 个百分点。
2. **暴击边界与期望**：实际掷骰改 `Math.random() < rate`；`anticipating` 期间用
   `1 + 暴击率 × 暴伤`（不掷骰），真实出手仍随机。
3. **减伤/易伤拆分**：`getDamageTakenMultiplier()` 只算正减伤的 `Π(1-r)`；
   新增 `getDamageVulnerabilityMultiplier() = 1 + Σ|负减伤|`。公式分别乘两个乘区。
4. **抗性乘区**：改为 `1 + 穿透 − 抗性`，允许穿透把抗性压成负数、乘区超过 100%；
   负抗性也会被穿透放大（旧“穿透最多抵到 0”规则被推翻）。
5. **防御固定无视**：固定值折算成“占目标防御的比例”，并入 `(1 − 总减防 − 总无视防御)`，
   与 HSR 公式同形。
6. **DOT 接入公式**：新增固定原始值的 `Damage`/`DamageEvent` 构造器与
   `LivingThing#makeDotDamage`；DOT 不暴击，但吃增伤/防御/抗性/易伤/减伤/独立乘区。
   【侵蚀】从直接 `setHp`（最低留 1 点）改为火属性 DOT（可致死），来源优先按 origin
   在战斗实体里查；`Erosion.copy()` 补带 origin。
7. **真实伤害**：`ElementSort.UNIVERSAL` 免增伤/抗性，但吃防御/减伤/易伤，**且不能暴击**；
   星魂 6 的 36% 改为固定原始值走同一公式（不再绕防御）。旧的“固定结果值”构造器与
   `makeFixedDamage` 删除，避免绕过公式。`makeDamage` 现在返回 `Damage`，
   星魂 6 直接累加 `getAppliedDamage()` 得到“实际伤害”。
8. **验证**：`通过 1224 条，失败 0 条`、`CHECK OK`、`src` **291** 文件、`mods/` 4 个、
   `check-doc-links.py` **65 links + 298 paths / broken 0**。
   守卫：`DamageTests#testCriticalAbsoluteExpectedAndDot`（暴击绝对值 / 期望 / DOT）、
   `DamageTests` 的穿透与负抗性、`DamageDisplayTests` 的易伤拆分、
   `EntityCoreTests` 的绝对值、`CombatMechanicsTests` 的 E6 固定原始值。

---

## 十九、2026-11-21（第二周）第 88 批：LivingThing 战斗职责拆分

作者要求：**不要把过多战斗/伤害方法和字段继续堆在 `LivingThing`**，
每实体单独一个“战斗控制器/组件”。

1. 新增 `entity/CombatComponent`，由 `LivingThing` 在字段初始化时注入自己；
   每个实体一个，`LivingThing#getCombat()` 取。
2. 迁入：`takeDamage`、`makeDamage`（三参/两参/真伤/DOT）、`modifyIncomingDamage`、
   伤害修正器增删查、`damageReductions`/`damageModifiers`/`baseDamageReduction` 字段、
   减伤/易伤查询、`getTeamAdditionAttackDamageBonus`。
3. `LivingThing` 只留薄转发；旧 API（460 个调用点与 4 个模组）不用改。
   复制构造器调用 `CombatComponent.copyFrom(...)`，每个副本拿到独立列表。
4. `/data` 兼容：`CombatComponent` 加 `@DataFlatten`，并在 `DataBridge.isDataObject` 登记
   （不登记会被当行为对象整个跳过，`damageReductions` 会静默消失）。
5. 不搬 `AttributeProfile` / `EffectContainer` / `UniversalController` / `presentTurn` /
   `participateFight` / HP/治疗/死亡判定。
6. **`/data` 不再显示白厄的 `skills`**：该字段是变身前普通技能备份（退出变身还原控制器用），
   不是当前装备技能，也不是配置项，已加 `@NoData`；`SkillDataTests` 有守卫。
7. **验证**：`通过 1226 条，失败 0 条`、`CHECK OK`、`src` **292** 文件、
   `mods/` 4 个、`check-doc-links.py` **65 links + 298 paths / broken 0**。
   守卫：`DamageTests#testDamageReduction` 追加“copy 后组件与列表独立”1 条；
   `DataBridge` 的 `/data` `damageReductions` 断言保持；`SkillDataTests` 的 `skills` 不显示 1 条。
8. 长期规则写入 `10-RULES.md` §3.1：以后战斗/伤害逻辑走 `CombatComponent`，
   `LivingThing` 只留转发。

---

## 二十、2026-11-21（第二周）第 89 批：千冶·刃 E4/E6 + `/kill` 修复

1. **E4「锻打遗恨，后剑骨自成」**：`MortalBladeAllyEnhanceEffect` 支持构造增伤值；
   4 魂起队友【万淬心】+100%（原 50 + 额外 50），千冶·刃自己 +75% + 50% = +125%；
   退出结界按 `appliedSelfEnhance` 记的实际值撤回。
2. **E6「神若当殒，自誓弑寿瘟」**：结界期间本回合第一次受到伤害或消耗生命值额外 +1 充能
   （与【千锻魂】每次受击 +1 独立）；`MortalBladeFuryHitListener` 监听 `EffectUpdateEvent`，
   在每个目标回合头复位“本回合已触发”；`consumeHp` 也走同一入口；
   `infiniteFurySkills()` 给【千冶铸一】的倍率乘 1.5（原型配置改了也照样乘）。
3. **`/kill` 修复**：不再 `setHp(0)` + `setAlive(false)`，改为构造
   `DamageType.SystemDamage + ElementSort.UNIVERSAL` 的无限大固定原始值，
   post `DamageEvent` 后走 `takeDamage`。因此免死/转阶段/结界退出等 `IModifyDamage` 正常生效；
   千冶·刃开大时第一次 `/kill` 只退出大招，`endInfiniteFury` 清掉队友【万淬心】。
   计算器未改，外部仍可监听 `DamageEvent`。
4. **验证**：`通过 1239 条，失败 0 条`、`CHECK OK`、`src` **292** 文件、`mods/` 4 个、
   `check-doc-links.py` **65 links + 298 paths / broken 0**。
   守卫：`MortalBladeTests#testMortalBladeEidolonFourAndSix`、`#testKillCommandRespectsDeathWard`。

---

## 二十一、2026-11-21（第二周）第 90 批：外包式子代理工作流

用户要求：可以多用子代理，但不能多个子代理改同一个文件；每个子代理要有独立工作空间与临时记忆；
长结论写 md，避免上下文吃紧。

1. `10-RULES.md` §9.4 改为“能切分就多用”，新增 §9.4 1.1：
   `out/agents/<任务id>/` 工作空间、`memory/agents/<任务id>.md` 临时记忆、
   写入边界、不重叠、长结论 md、权威文档由主代理回写。
2. `30-WORKFLOW.md` 新增 §9.10 外包式子代理工作流：
   发包单 SOW 模板、承包执行、验收清单、集成收口、并行边界、何时不用外包。
3. `memory/00-INDEX.md` 增加子代理临时记忆路径。
4. 验证：`check-doc-links.py` → **66 links + 298 paths / broken 0**。本次不改代码。

---

## 二十二、2026-11-21（第二周）第 91 批：千冶·刃多段免死修复 + 转阶段优先级

作者日志：千冶·刃在【无量忿怒】中被多段攻击的第一段打到致死，结界解除、免死触发并停在 1 血；
同一动作的后续段继续打，把他打到 0；回合末回血动作执行时已死，打印“回复至 0 → 0”。

1. **`deathWardPending`**：第一段致死触发免死、退出结界、把回血排进 `PRIORITY_LAST` 后置位；
   在回血动作执行前，同一个 `IModifyDamage` 对后续致死段一律返回 1；回血动作执行时清标记。
   没有当前回合（自测 / 战斗结束）时当场回血并返回回血后的血量，不再返回 1 被 `setHp` 覆盖。
2. **ActionEntry 优先级常量**：新增 `ActEntry.PRIORITY_PHASE_TRANSITION = 2000` 与
   `PRIORITY_EIDOLON_EXTRA = 800`；盗火行者转阶段与白厄星魂 2 额外行动都改用常量。
   阶段转换 ActionEntry 明确高于额外行动。
3. **验证**：`通过 1243 条，失败 0 条`、`CHECK OK`、`src` 292 文件、`mods/` 4 个、
   `check-doc-links.py` **66 links + 298 paths / broken 0**。
   守卫：`MortalBladeTests#testDeathWardSurvivesMultiHit`，以及转阶段/E2 优先级断言。

---

## 二十三、2026-11-21（第二周）第 92 批：千冶·刃免死收口延后到回合末

作者日志：多段攻击的第一段触发免死后，“结界解除”“不会倒下”会打印在攻击行前面；
希望这三行（含回复）都排到当前回合最后，和白厄把收尾动作排进 `PRIORITY_LAST` 的口径一致。

**根因**：`IModifyDamage#damageModify` 是伤害结算中间的一步，攻击行要等它返回后才打印；
在里面直接 `endInfiniteFury()` / `System.out.println`，播报必然抢在命中日志与后续目标之前。

**改法**（`MortenaxBlade#infiniteFuryDeathWard` / `#scheduleDeathWardResolution`）：

1. 第一段致死只把 `newHp` 钳到 1、置 `deathWardPending`，不退出、不播报；
2. 退出、`不会倒下`、回血三件事一起排进当前 `TurnEntry` 的 `PRIORITY_LAST`
   （当前回合所有行动之后），收口动作按“清标记 → 退出（打印）→ 不会倒下 → 回血（打印）”执行；
3. 收口前仍在【无量忿怒】：后续致死段继续钳回 1，非致死段仍吃 50% 减伤，
   也仍触发【千锻魂】每次受击 +1（E6 每回合一次已在第一段用掉）；
4. 没有当前回合（自测 / 战斗结束）仍当场收口并返回回血后的血量。

**边界**：不改伤害公式、不改回血量（仍是 `1 + 50%` 生命上限）、不动白厄退出协议、不改 E4/E6 数值。

**验证**：`通过 1243 条，失败 0 条`、`CHECK OK`、`src` 292 文件、`mods/` 4 个、
`check-doc-links.py` **66 links + 297 paths / broken 0**。
守卫：`MortalBladeTests#testDeathWardSurvivesMultiHit` 增加了输出捕获断言：
第一段/第二段输出里没有“结界解除 / 不会倒下”，回合末输出顺序为
“结界解除 → 不会倒下 → 回复至”。

---

## 二十四、2026-11-21（第二周）第 93 批：白厄打断退出的时点核实

作者问：白厄变身被打断时是不是直接退出？如果是，改成回合末、最后一击之后再退出，
让最后一击吃到变身加成。

**核实结论：不是直接退出，当前已经是“最后一击之后才退出”。**

- `Phainon#finalizeAwakenByInterrupt()` 只有两件实事：
  ① `interruptedRemainingExtraTurns = extraTurns` 冻结倍率输入；
  ② `clearSoulscorchState()` 清【弑魂之炽】、75% 减伤与待行动名单。
  它**不发** `AwakenEndEvent`、**不**把 `awaken` 置 false、**不**摘 `AWAKEN_ENHANCE_SOURCE`
  （`EventBus.post` 那一行在代码里是注释掉的）。
- 真正退出发生在 `LastAttack#comeToEffect` 的**最后一行**：先按攻击 +80% 结算完所有目标的伤害，
  再给敌方挂速度效果，最后才 `EventBus.post(new AwakenEndEvent(...))`。
  `AwakeEndListener.end` 收到事件后才置 `awaken=false`、还原技能表/名字、按 25% 生命上限回血。
- 所以打断路径的 `LastAttack` 在挥刀时 `awaken=true`、攻击加成 `+0.8` 仍在；退出在它之后。

**这次改了什么**：只修文档/注释与测试，不改生产行为。

1. `Phainon#soulscorchDeathWard()` 与 `#finalizeAwakenByInterrupt()` 的 javadoc 原来写“当场完成退出协议 /
   就地 post 事件”，与代码不符；改成“只冻结+清层，退出由最后一击末尾发事件完成”。
2. `LastAttack#comeToEffect()` 补注释，明确“伤害结算在 `AwakenEndEvent` 之前、最后一击必须吃变身加成”。
3. `TestSupport#testAwakenInterruptExitProtocol()` 增加探针：在 `ProbeFragileDummy#takeDamage` 里记录
   攻击者白厄的 `isAwaken()` 与 `getPropertyEnhancePercent(ATTACK)`；断言命中时 `awaken=true`、
   加成 `0.8`，整圈跑完后 `awaken=false`。测试从 5 条变 6 条，并在描述里把“当场退出”改为“最后一击之后退出”。
4. `60-COMBAT` §5.7 的“退出协议由中止变身的动作自己收口”整段更正为“由最后一击自己收口”。
5. **没有改** `LastAttack` 的 `0.125`、下限 `7`、12 火种、8 个额外回合、75% 减伤，也没有改千冶·刃第 92 批的收口。

**验证**：`通过 1261 条，失败 0 条`、`CHECK OK`、`src` 293 文件 / 59,280 行、`mods/` 4 个、
`check-doc-links.py` **66 links + 297 paths / broken 0**。
