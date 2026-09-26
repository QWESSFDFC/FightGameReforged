# NBT 与 `/data` 命令可行性分析（2026-09-26）

> 本文是**分析/设计**，不是实现计划 —— 用户明确说"先不做，分析"。
> 所有"现状"结论都按 2026-09-26 的源码核实过；行号会随改动漂移，改代码后请重新核对。
> 可信度分级沿用 `TIPS_FOR_LLM.md` §0：**已核实** = 逐行读过当前源码；**估算** = 按本项目体量校准出的数量级。

---

## 零、结论速览

| 目标 | 可行性 | 工作量（估算） | 建议 |
|---|---|---|---|
| **A. 只读 `/data get`**（看一眼实体状态） | ★★★★★ | ≈400-600 行，半天到一天 | **先做这个** |
| **B. 完整 `/data`**（get / merge / modify / remove） | ★★★★ | +700-1100 行 | 可行，但**必须先把"可写字段白名单"定死** |
| **C. 真 NBT**（二进制格式 + 实体存档） | ★★ | +1000 行以上，且要重建对象图 | **不划算**，除非同时要做存档系统 |
| **D. `storage`**（全局/模组数据的持久化） | ★★★★★ | ≈150-250 行 | **最划算的一块**：模组目前完全没法存数据 |
| 折中：不做通用 NBT，只做小工具 | ★★★★★ | `/list` 详细模式 ≈50 行；`Mod#getStorage()` ≈100-150 行 | 如果只是"想看状态 + 想让模组存点东西"，这条更省 |

> **进展（2026-09-26，同日实现）**：用户拍板"划不划算无所谓，图一乐"，**已实现**：
> `game/data/`（`NbtTag` 体系 / `Snbt` 文本 / `DataPath` / `DataBridge` 反射桥 / `DataStorage` / 两个注解，
> 15 文件 2178 行）、`/data get|merge|modify`×`entity|storage`、
> 路径的 **`{k:v}` 过滤**与 **`[a:b]` 切片**（切片只读）、选择器的 **`nbt={…}`** 筛选、
> **`execute if data`**、`StringReader#readBalanced()`；
> 自测从 252 涨到 **379**（AI 用项目自带 JDK 自己跑过 379/0）。
> **与本文原建议的四处差异**：① 数据来源没有走"显式白名单注解"，而是
> **"数据对象的全部字段都暴露"**（`DataBridge#isDataObject` 用类型名单兜住边界，
> 只有 `@DataField`/`@NoData` 两个逃生口）——因为用户要的是"酷"，全量 dump 更接近 MC 的手感；
> ② **跳过**了 `storage` 的**落盘**（用户："存档没有必要做"）→ 现在的 storage 只在内存里；
> ③ **`/data remove` 不做**（用户明确说不做）；
> ④ 过滤只支持**单键**、切片只支持读取（写路径上明确拒绝）。
> **下一步（未做）**：多键过滤、`execute store`、`execute as` 与 `if` 串联、存档。
>
> **追加（2026-09-26 晚）**：用户实跑时撞上"`/data modify` 一个身上还没有的效果 → 报错只说
> 没有命中任何元素"，看起来像语法错。已把**路径报错诊断**补齐：`DataPath#walk`
> 同时带回"值"与"失败原因"，`get` 取回值、新增 `explainMissing` 取回原因，
> 于是两条路（`/data get` 走标签树、`/data modify` 走活对象）都能说出
> **过滤里有哪些取值 / 这一层有哪些键 / 这个列表有几个元素**。
> 自测 372 → **379**（AI 跑过 379/0）。

**一句话**：`/data` 命令高度可行（现有命令系统已经给了 6-7 成的管道）；
NBT 作为**通用数据模型**也划算（它顺手解决"模组没有持久化"和"没有存档"两件事）；
NBT 作为**磁盘二进制格式**不划算（这个项目没有跨工具交换的需求，`org.json` 已经在依赖里）。
最该先做的，其实是**最不像 NBT 的那一小块**：给模组一个能落盘的 JSON 存储位。

---

## 一、前提：为什么不能"直接照搬 MC"

MC 里 NBT 能当真相，是因为**实体就是一堆数据**：行为写在代码里，状态全在 NBT 里，存档就是"把 NBT 写盘、读回来重建对象"。

本项目正相反 —— **实体是活对象**：

| 类 | 字段数（已核实，粗略） | 里面装着什么 |
|---|---|---|
| `Thing` | 17 | uuid（`final`）、tags、质量、**物理对象**（force / velocity / acceleration / position，各自还是一个对象） |
| `Entity` | 4 | 名字、id、描述、等级 |
| `LivingThing` | ≈59 | 属性、法力、**效果列表**、**技能列表 + controller**、背包、伤害修正器列表、减伤表、`showSpecialMes` 回调… |
| `FlameReaver` | +17 | 灾难之力、共祭窗口、两批召唤账本、阶段标志、蓄力标志、减伤层数… |
| `ActorLiXiaoYan` | +4 | 燃点、铭记比例、lastIgnition… |
| `Effect` / `Skill` / `Item` / `Inventory` / `Slot` / `Mana` | 5 / 13 / 4 / 1 / 2 / 3 | 效果带 origin 与生命周期；技能带冷却与倍率；背包是对象列表 |

也就是说：**一个 `LivingThing` 实例 ≈ 80 个字段，其中一部分是"行为"而不是"数据"** ——
controller（`PlayerController` / `FixOrderController`）、技能实例（带冷却状态）、
`IModifyDamage` **匿名类闭包**（李晓焰的免死就是它）、`showSpecialMes` lambda、
`EventBus` 里的监听器引用、以及 `Thing.uuid`（`final`）。

**推论（决定了后面所有设计）**：本项目的"实体 NBT"只能是**视图或快照**，
不可能是唯一真相。写回去时必须走访问器、并且只允许一部分字段 —— 否则会绕开钳制与合并语义。

---

## 二、现状盘点（已核实）

| 项 | 现状 |
|---|---|
| 序列化 | **完全没有**：全项目 grep 不到 `serialize` / `NBT` / `Serializable` / `ObjectOutputStream` |
| JSON 工具 | `utils/JSONHelper.java` **只有 1 个方法**（`readJSONFile`，只读，还丢换行） |
| 存档 | **没有**。落盘的只有 `config/gameConfig/{TagConfig,PropertyConfig}.json` 与 `logs/latest.log` |
| 模组持久化 | **没有**。`Mod` 只能注册内容；`ModLoader` 每次启动**先删 `bin/` 再全量重编译**（`ModLoader.java:93-107`） |
| 命令系统 | 节点树 / 参数类型 / 选择器 / 用法提示 / `<--[HERE]` 报错 / `/execute as` 都在 → `/data` 的管道已有约 6-7 成（§四） |
| 选择器 | 支持 `type= name= limit=/count= sort=`（`EntitySelector.java:125-128`），**没有 `nbt=`** |
| `/execute` | **只有 `as`**（`ExecuteCommand.java:140-144`），没有 `if` / `unless` / `store` |
| 反射先例 | `/effect` 按"构造函数参数个数"挑构造器；`EventBus` 用 `method.setAccessible(true)` → 风格上不缺先例 |
| 读词能力 | `StringReader.readWord()` **遇到空格就断**；`readString()` 支持引号 → `data merge @s {Health: 20}` 这种带空格的写法**现在解析不了**（见 §五 风险 3） |

---

## 三、目标 A/B/C/D 分别要做什么

### A. 只读 `/data get entity <目标> [路径]`
- 需要：字段 → tag 的 dump（反射）+ 白名单 + 路径求值 + 打印（可上色）
- 不需要：写回、类型强转表、磁盘格式
- 难点只有一个：**白名单**。不设白名单的话 `@s` 会 dump 出 80+ 个字段，
  里面还夹着物理对象和 controller 的内部列表，等于把"内部实现"当成"数据"暴露出去
- 为什么值得：本项目最缺的正是"看一眼某个实体的状态"。现在只能靠用户贴日志、
  AI 靠日志反推（这几轮修 bug 全是这么干的）—— 一个 `/data get @s` 能省掉大量来回

### B. 完整 `/data`（get / merge / modify / remove）
在 A 之上还要：
- **写回**（`merge` 合并、`modify set/append/insert/prepend/merge`、`remove`）
- 类型强转：`long` / `double` / `boolean` / `String` / enum（`ElementSort`）/ `UUID` 字符串 / 效果与物品列表
- 与既有语义对齐（这是**最容易出事**的地方，逐条列在 §五）
- `/data get` 的 `scale` 参数（MC 有）可以不做

### C. 真 NBT（磁盘格式 + 实体存档）
额外要做：tag 的二进制读写（big-endian + gzip，约 200-300 行）、
每个类的 `toTag` / `fromTag`、以及**对象图的重新接线**：

- `FlameReaver` 的 `cloudOfDeathSummons` / `fateDrawsNearSummons`（批次账本，指向容器对象）
- `Phainon` 的 `pendingLastAttack`、觉醒状态机
- `ActorLiXiaoYan` 的匿名 `IModifyDamage`（构造器里 new 的，没法从数据里"读出来"，只能 `fromTag` 后重新接线）
- 技能的冷却、controller 的类型与技能表、`EventBus` 的注册状态

**结论**：C 的价值几乎全在"存档系统"，`/data` 用不到它。而这个项目现在**没有存档**，
所以 C 应该作为一个独立立项（"要不要做存档"）来讨论，而不是 NBT 的附带品。

### D. `storage`（全局 tag + 落盘）
MC 的 `/data ... storage <id>` 在本项目的对应物：一个全局 tag 树，可选落盘成
`config/modData/<modid>.json` 或 `saves/data.json`。

- 为什么最划算：**模组现在没有任何持久化手段** —— 想记一个"通关次数"、一个"上次选的难度"都做不到；
  而 `World.getModList()` 已经提供了"内容归属哪个模组"的判据，`Mod` 加一个 `getStorage()` 就能用
- 成本远低于实体 NBT：没有对象图、没有不变量要守、没有行为要复原
- 注意：`ModLoader` 每次启动重编译模组（缓存全清），所以"模组自己的状态"**必须**存在游戏侧的文件里，
  不能指望模组目录

---

## 四、现成能复用的零件（判断"可行"的主要依据）

| 需要的能力 | 现成的 | 位置 |
|---|---|---|
| 命令树 / 子命令 | `CommandNode` / `LiteralCommandNode` / `ArgumentCommandNode` / `ArgumentBuilder` | `system/command/` |
| 自定义参数语法 | `ArgumentType<T>#parse(StringReader)` —— 选择器就是"读一个词、内部再解析" | `EntityArgumentType.java:162`、`EntitySelector.fromString` |
| 选择器 | `@s`/`@p`/`@a`/`@e[type=…]` + 排序/限量（**`@s` 现在还会跟着当前行动者**） | `EntitySelector` |
| 执行者切换 | `/execute as <目标> run <命令>`（`/data` 与它的组合是 MC 的常用套路） | `ExecuteCommand` |
| 建议与报错 | 补全、`getSuggestedUsage()`、`<--[HERE]` 定位 | `CommandDispatcher` / `CommandManager` |
| 反射构造对象 | `/effect` 已按构造函数签名挑构造器 | `EffectCommand` |
| 权限/开关 | `CommandManager.setEnabled(false)` 可整体关闭命令 | `CommandManager` |
| 落盘 | `ConfigLoader` 的读写套路（虽然它写读编码不一致，见整体分析 §6.4 N7） | `ConfigLoader` |

也就是说：**要新写的只有"数据层"与"路径层"**，命令层的骨架不用重造。

---

## 五、关键设计决策（建议这么定）

| 决策点 | 建议 | 理由 |
|---|---|---|
| 数据来源 | **注解白名单 + 反射桥**（默认忽略，显式 `@DataField("Health")` 才暴露），特殊类型单独映射（效果列表 / 背包 / 法力 / enum / UUID） | 纯反射 dump 会把 80 个字段（含物理对象、controller）全暴露；给 20+ 个内容类手写 `toTag/fromTag` 又太贵 |
| 数据名 | 把"数据名"当**对外 API**（默认取 Java 字段名，可自定义），改名必须写进 `TIPS_FOR_LLM.md` | `/data merge @s {hp:1}`、存档文件都会随改名**静默失效** |
| 写回方式 | 一律走**访问器表**（`setHp` / `addEffect` / `setIgnition` / `setMemorizedRate`…），**不裸写字段** | 见下面"必须守住的语义" |
| 目标类型 | 先 `entity` + `storage`；**不做 `block`** | 本项目没有方块实体，背包格子也不是世界坐标 |
| 路径语法 | 先做 `a.b[0].c`；`{}` 过滤与 `[start:end]` 切片放后面 | 语法越大，与 `readWord` 的冲突面越大（风险 3） |
| 读词 | 新增 `readCompound()`（读到配对的 `}` / `]` / 引号结束），**不要改老的 `readWord`** | 老方法是所有命令的公共依赖，改了等于全量回归 |
| 磁盘格式 | **JSON**（`org.json` 已在依赖里），不实现 MC 的二进制 NBT | 没有跨工具交换需求时，二进制只是负担与 bug 来源 |
| `final` 字段 | 白名单排除（`Thing.uuid` 等） | 非静态 `final` 反射**能写**（`EventBus` 就是先例），写坏 uuid 会毁掉判等与选择器 |
| 实体可写范围 | 只暴露"玩家/模组真的会调的量"：HP/法力/效果/背包/自定义计数器；**不暴露** controller、技能冷却、物理、减伤表 | 边界写进文档，否则每次都要重新吵一遍 |

### 必须守住的语义（写回时的坑，全部已核实）

1. `hp` 的钳制在 `setHp` 里（`LivingThing.java:1986` `Math.min(getHpMax(), hp)`）；
   `getHpMax()` 是**动态**的（`hpMax × (1+百分比) + 固定值`，`:1568`）—— 裸写 `hpMax` 只会改"基础值"，
   而且 `hp` 可能超过新的上限 → 必须走 setter；
2. 效果进列表要经 `LivingThing.addEffect`：它负责 `World.applyRegisteredId`（**必须在 `equals` 判定之前**）
   与"同 id+origin 合并刷新"；
3. `Effect#equals/hashCode` 只看 `id + isInfinity + origin`，**`level` 不参与判等** →
   `/data merge` 往效果列表塞一条同 id 的效果会变成"合并"而不是新增；
4. `LivingThing.copy()` 里 `damageModifiers` / 技能实例是**共享引用** →
   对副本写数据可能影响模板或别人（李晓焰的 −7 就是这么来的）；
5. `Effect.lastTime` 的递减由回合末的 `EffectEventListener` 负责，额外回合**不递减**（`isExtra`）→
   `/data merge` 改 `lastTime` 是安全的，但别指望"改成 0 就立刻消失"（要到本回合末结算）。

---

## 六、工作量估算（按本项目体量校准）

| 模块 | 估算 | 参照物 |
|---|---|---|
| tag 模型（Compound / List / 数字 / String / 与 JSON 互转） | 250-350 行 | `Skill.java` 505 行 |
| 反射桥 + 白名单 + 特殊类型映射 | 300-500 行 | `EntitySelector.java` 642 行 |
| 路径解析与求值（get/set/merge/append/remove） | 300-500 行 | `CommandDispatcher.java` 491 行 |
| `/data` 命令本体（子命令 + 建议 + 报错） | 200-300 行 | `GiveCommand.java` 229 行 |
| `storage` + 落盘 | 100-150 行 | `ConfigLoader.java` 170 行 |
| 自测（round-trip / 路径 / 边界 / 写回不变量） | 150-250 行 | 现有 301 条断言 |
| **合计（A+B+D，不含 C）** | **≈1300-2050 行** | 相当于再造 2-3 个 `/effect`（719 行） |
| 只做 A | ≈400-600 行 | 半天到一天 |
| 只做 D | ≈150-250 行 | — |
| C（二进制 + 实体存档） | +1000 行以上，且需要"存档设计"独立立项 | — |

---

## 七、风险清单（按严重度）

| # | 风险 | 说明 | 对策 |
|---|---|---|---|
| 1 | ★★★ **反射写坏不变量** | 见 §五 的五条 | 访问器表 + 自测断言（"写 hp 会被钳制""写效果会走 addEffect"） |
| 2 | ★★★ **数据名漂移** | 字段/数据名一改，所有 `/data` 脚本与存档静默失效 | 数据名当 API；改名写进 TIPS；`/data get` 输出里带上名字 |
| 3 | ★★ **`{}` 语法与 `readWord` 冲突** | `{Health: 20}` 带空格就断词；`readWord` 是所有命令的公共依赖 | 新增 `readCompound()`，不动老方法 |
| 4 | ★★ **"能写什么"的边界争议** | 能改技能冷却吗？能改阶段标志吗？ | 白名单写进文档，默认"不给" |
| 5 | ★★ **存档一致性** | 存的是"视图快照"，读回来要 `fromTag` 重新接线（匿名闭包、监听器、账本引用） | 先只存 `storage`，实体存档最后做 |
| 6 | ★ 白名单维护成本 | 每加一个字段要记得标注 | 默认忽略（fail-safe），而不是默认暴露 |
| 7 | ★ 选择器 `nbt={...}` 的解析/转义（引号、中文值） | 中文值在 GBK 控制台还有老问题 | 放在第 3 步，复用 `EntitySelector` 的 key=value 解析 |

---

## 八、如果换更便宜的做法（可能更贴合现状）

| 真实需求 | 便宜做法 | 成本 |
|---|---|---|
| "想看一眼实体状态" | `/list` 加详细模式（现在只打 HP/攻/防/速，`ListCommand.java:55-66`） | ≈50 行 |
| "想让模组存点数据" | `Mod#getStorage()` 返回 `JSONObject`，由游戏侧落盘 `config/modData/<modid>.json` | ≈100-150 行 |
| "想做存档" | 先只存局外数据（storage + 玩家偏好），实体存档最后做 | ≈200 行起 |
| "想让模组自己定义数据" | 模组直接写自己的 JSON 文件（`Mod` 里已经能拿到 `MOD_ID` 与工作目录） | ≈0 行（约定即可） |

---

## 九、建议路线（如果决定做）

1. **第 0 步（最划算，建议先做）**：`Mod#getStorage()` + JSON 落盘 ——
   顺手解决"模组没法存数据"，**完全不碰实体**，风险接近零；
2. **第 1 步**：只读 `/data get entity <目标> [路径]`（白名单注解 + 反射 dump + 路径求值）；
3. **第 2 步**：`/data merge | modify | remove`（写回走访问器表）+ 自测断言守住 §五 的五条语义；
4. **第 3 步**：选择器 `nbt={...}` 过滤 +（新能力）`execute if data`；
5. **第 4 步（独立立项）**：把 tag 模型接到存档系统，实体存档最后做。

**不建议**：一开始就照抄 MC 的二进制 NBT 格式；也不建议在没有白名单的情况下开放写回。

---

## 十、一句话结论

> 可行。但要**把"和 MC 一样"拆成三件事**：
> `/data` 命令（现有命令系统已给了 6-7 成管道，只读版几乎白送）、
> NBT 作为通用数据模型（可行且划算，顺带解决"模组没有持久化"与"没有存档"）、
> NBT 作为磁盘二进制格式（**不划算**，除非要跨工具交换）。
> 而最该先做的那一步，恰好是最不像 NBT 的：**给模组一个能落盘的 JSON 存储位**。

---

> 本文由 AI（DeepSeek）通读 2026-09-26 的源码后写成，仅作参考，**以源码为准**。
> 真要动手时请先回写 `TIPS_FOR_LLM.md`（数据名/白名单/踩坑），再回来更新本文。
