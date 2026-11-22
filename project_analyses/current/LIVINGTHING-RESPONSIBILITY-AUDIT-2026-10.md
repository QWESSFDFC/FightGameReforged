# 「LivingThing 职责与命名」评审核查与处理建议（2026-10-25）

> **2026-11-22 复核**：后续拆分已继续：`CombatComponent` 接管伤害入口/减伤/修正器，
> `AttributeProfile` 接管属性，`LivingThing` 只剩薄转发。本文的旧行号与“未做”状态会过期；
> 当前边界看 `notes_for_llm/rules/10-RULES.md` §3.1 与 `90-STATE.md`。

> **文档分类**：[D3] 分析 · 方案评估 ｜ **这是什么**：对一份外部 AI 评审（"`LivingThing` 职责过多 / 命名不规范 / 建议拆组件"）的**逐条核查**与处理建议 ｜ **读者**：引擎开发者 ｜ **时效**：2026-10-25 快照。**核查轮**（当天早些时候）**没改任何代码**；同一天晚些的**第 41 批**把 §3 里的 **P0 三条 + P1 三条**做完了（状态列已回写），**P2/P3 一条没动**。
>
> **给下一个对话的用法**：先读 §0 和 §5，再看需要动的那一条的核查行。**不要**在没有用户点头的情况下动 §4 里那四条。

---

## 0. 结论摘要

**评审方向大体对，但"职责清单"里有一半已经搬走了**：`LivingThing` 现在 **2423 行**，
**199 处 `public` 成员声明 = 193 个方法 + 5 个构造器 + 1 个 record 头**（`:2421`），
其中约 **128 条语句是纯 getter/setter 转发**（属性与五行法力都已在 `AttributeProfile` 里），
自己只有 **10 个字段**（1 个静态 + 9 个实例：`attributes` / `entityEffectList` / `damageReductions` /
`damageModifiers` / `controller` / `participateFight` / `presentTurn` / `showSpecialMes` / `description`）。
所以它更像**一个很厚的门面**，而不是"十个职责挤在一起"——**真正住在里面的职责是 6 条**
（效果、伤害、减伤、控制器、战斗上下文、打印），评审还漏了**第 11 条：规则读取**（`GameRules` 的 6 个静态 helper，`:279-311`）。

**最硬的三条证据**：

| # | 事实 | 证据 |
|---|---|---|
| 1 | **`initialMana()` 的 5 个分支确实几乎完全重复** —— 每个分支 5 行，唯一的差别是"哪一个元素拿 `manaMainBase()`、其余拿 `manaOtherBase()`" | `LivingThing.java:639-690`（30 行 × 5 分支，结构逐字相同） |
| 2 | **14 参数构造器**（`name,id,火抗,水抗,金抗,木抗,土抗,speed,level,type,hpGrowth,atkGrowth,defGrowth,element`）—— 位置错一个就静默错配 | 定义 `LivingThing.java:192`；调用例子 `MortenaxBlade.java:67`（14 个裸参数） |
| 3 | **`getEntityEffectList()` 返回活列表**：全项目 **70 处调用**，其中 **13 处是"结构写入"**（`add` / `remove` / `clear` / `removeIf` / `set(i,…)` / `listIterator().remove()`），其余 57 处是只读遍历 | `LivingThing.java:1598`；`grep -o getEntityEffectList` 逐文件统计（子代理 C 复核） |
| 4 | **`facSet*` 链式 setter 基本没人用**：共 **33 个方法**，全项目只有 **9 个调用点 / 1 个文件**（`debug_tools/actionBarTest/TestMain.java:19,21,23`），`mods/` 与官方内容 **0 处**，`MODDING-GUIDE.md` **0 命中** | 子代理 C 复核（`grep -o facSet`） |

---

## 1. 逐条核查（✅ 已亲自量过 ｜ ❓ 待核）

### 1.1 类的规模与结构

| 项 | 实测（2026-10-25，子代理 A 复核过） |
|---|---|
| 行数 | **2423** |
| `public` 成员声明 | **199 处 = 193 个方法 + 5 个构造器 + 1 个 record 头**（`:2421`） |
| 纯转发语句 | 约 **128 条**（48 条 `return attributes.xxx()` + 80 条 `this.attributes.setXxx()`；我先前写的"134"复现不出来） |
| private / 包级 方法 | 8 / 1（`:326`） |
| 字段 | **10 个**（`:66` 静态 key + `:70/:71/:91/:92/:101/:109/:116/:124/:125`） |
| 真注解 | `@DataField` **1**、`@NoConfig` **4**、`@DataFlatten` **1** → 共 **6 处 / 5 个字段**（我先前写的"2 个 `@DataFlatten`"把 javadoc 里那句 `{@code @DataFlatten}` 也算进去了） |
| `AttributeProfile` 上的注解 | **27 个 `@NoConfig`**、0 个 `@DataField`/`@DataFlatten`（我先前写的"38"是**行内出现次数**，含 javadoc） |
| `System.out.print*` | **1** 处（`:2173`，在 `printAttackLine` 里） |
| 引用它的文件 | src **159**（含本体，外部 158）+ `mods/` 8 = **167**；`getHp/setHp` 238 处 / 45 文件、`getController()` 79 处 / 28 文件 |
| 构造器 | **5 个**：`()`:130、复制 `:143`、**14 参数**:192、`(name,id,level,element)`:260、`(long speed)`:270 |
| ⚠️ 文档口径不一致 | 项目文档一直把它叫"**十三参**构造器"（`70-DATA.md:128`、`90-STATE.md:605`、`AttributeProfile.java:543`），**实际 14 个参数** —— "参数个数都被数错"本身就是顺序风险的旁证 |

### 1.2 评审列的 10 项"职责"——实际落在哪

| 评审说 | 实际情况 | 判定 |
|---|---|---|
| 属性存储（几十个 getter/setter） | 字段在 `AttributeProfile`（`@DataFlatten`），实体只做 **134 个转发** | ⚠️ **是"门面"，不是"存储"** |
| 五行法力 | 同样在 `AttributeProfile`；`initialMana()` / `recoverManaEveryTurn()` 仍写在实体里 | ✅ 成立（**逻辑**还在实体） |
| 效果管理 | `addEffect`(1624) / `removeEffect`(1661) / `getEntityEffectList`(1598) | ✅ 成立 |
| 伤害结算 | `getDamage`(1878) / `modifyIncomingDamage` | ✅ 成立 |
| 减伤管理 | `addDamageReduction` / `getDamageTakenMultiplier` | ✅ 成立 |
| 控制器持有 | `controller` 字段 + `getController/setController` | ✅ 成立（⚠️ `@NoConfig` 是对的） |
| 战斗上下文 | `participateFight` / `presentTurn` | ✅ 成立（⚠️ 都是运行期字段） |
| 日志格式化与打印 | `printAttackLine` + `getNameWithSide` / `getNameWithUuid` / `getNameWithUuidAndSide` | ✅ 成立（4 个方法，外部也在用） |
| 数据序列化注解 | 实体上只有 **5 个**注解，`AttributeProfile` 上有 **38 个** | ❌ **不成立**（已经集中到组件上了） |
| 深拷贝 | **真正干活的是复制构造器** `:143-172`（属性组件 `:147`、控制器 `:160`、背包 `:164`、tag `:165-171`）；`copy()` 是**直接抛异常的模板方法**（11 个生产子类 + 2 探针 + 1 个模组重写）；`livingThingFactory()` **已按 P1 删除（2026-10-25）** —— 它原来是 0 调用 / 0 重写的死 API | ⚠️ **我先前把分工说反了**。深/浅：`manas` **深**（`AttributeProfile:519-522`）、`entityEffectList` **浅**（`:153`，见 `80-EFFECTS.md` K5）、`damageReductions` 浅但元素不可变（安全）、`damageModifiers` 浅且闭包共享（`60-COMBAT:302-306` 有血证） |

### 1.3 命名与风格（✅ 都已核实存在）

| 评审条目 | 事实 | 引用点数量 | 备注 |
|---|---|---|---|
| `takeDamage(DamageEvent)` 实为"受到伤害" | ✅ 存在 | `getDamage(` 全项目 31 处，但**其中 7 处是 `DamageEvent#getDamage()`（无参）**；真正的 `LivingThing#takeDamage(DamageEvent)` = **12 处**（生产 2 + 测试 9 + 覆写 1） | 与"取伤害值"的直觉冲突；`BrokenContainer.java:428` 有 `@Override` |
| `facSetDfk` / `facSetAfk` | ✅ **存在**（`LivingThing:1319` 防御 / `:1341` 攻击） | 定义 3 个、调用 **0** | ⚠️ **我一开始说"不存在"是错的**（grep 输出被 `head` 截断了）。`dfk`/`afk` 是自造缩写、**不是拼音**；而且它们是攻击/防御/穿透**唯一**的链式 setter（没有 `facSetAttack`/`facSetDefence`/`facSetPenetration`）。**2026-11-14 已改名**：`facSetDefence` / `facSetAttack` / `facSetPenetration`（第 44 批） |
| `facSetChuantong`（拼音） | ✅ **存在** | `LivingThing.java:1286`（@param 写"全属性穿透"） | 是拼音但**拼错**了：穿透 = `chuantou`，代码写 `chuantong`（更像"传统"）。同一批还有 `facSetDirtResistance` / `facSetCriticalDamage`。**2026-11-14 已改名**为 `facSetPenetration`（第 44 批） |
| `dirt` 表示土 | ✅ 全项目统一 | src 里 **63** 个标识符 + 两份 JSON 各 **20** 个键 | 改名 = 同时破坏 `/data` 键与用户配置 |
| `criticalDMG` vs `criticalDamage` | ✅ 并存，但**处在两层**：Java 侧 `getCriticalDMG` / `setCriticalDMG` / `facSetCriticalDMG`（0 调用）/ 类 `CriticalDMGEnhanceEffect` / id `criticalDMGEnhanceEffect`；**数据键**已于 2026-10-24 改成 `baseCriticalDamage` / `criticalDamageEnhancePercent` / `...Amount`，字段真名是 `baseCriticalDamage` | 当时 51 行 / 15 文件 | ✅ **已统一（2026-11-15 第 65 批）**：方法 → `getCriticalDamage/setCriticalDamage/facSetCriticalDamage`、类 → `CriticalDamageEnhanceEffect` / `CriticalDamagePotion`；**id 字符串也已改（2026-11-15 第 68 批）**：`criticalDamage*`，本项目不需要向后兼容 |
| `facSetLevel(Long)` 与 `facSetLevel(long)` 重载 | ✅ 存在，且是**同类别两个重载**：`LivingThing:1176`（`Long`）与 `:1901`（`long`，协变重写父类 `Entity:94`） | 33 个方法；调用点全项目 **9 个 / 1 文件**（`actionBarTest/TestMain.java:19,21,23`），`Long` 版 **0 调用** | ⚠️ 我一开始说"跨类"不准确。删 `Long` 版是零风险（还要删它上面重复的 Javadoc `:1161-1175`），但**属公共 API，先问用户** |
| `manaMainBase()` 里的全限定 `cn.gfhnv...GameRules` | ✅ **存在**（`:301-302`、`:309-310`，还有 2 行 Javadoc `{@value}`） | 4 行代码 + 2 行注释 | ⚠️ **我一开始说"查不到"是错的**：评审把包名写成 `cn.gfhnv.game`，实际是 `cn.gfhnv.game.system.configLoadingSystem.GameRules`（`:18/:19` 已 import，`:280/:287/:294` 就在用短名） |
| `describeReductionSource` 里全限定 `cn.gfhnv.game.item.Item` | ✅ 存在 | `LivingThing.java:336` | 一行的事 |
| 注释里带日期/拍板记录 | ✅ 存在 | 本文件 **7** 处 | ⚠️ 见 §4：**项目规矩本身就禁止"修 bug 经过"**，但允许"当前约束 + 为什么" |
| 硬编码 `"基础减伤"` | ✅ 存在，是 `DamageReductionSource` 的**来源名**（进 `/data` 与日志） | `LivingThing.java:66` | 项目**没有** i18n 机制，全中文硬编码是既定风格 |

### 1.4 `BASE_DAMAGE_REDUCTION`（静态共享 key）

`private static final DamageReductionSource BASE_DAMAGE_REDUCTION = new DamageReductionSource("基础减伤")`（`:66`），
只被旧写法 `setDamageAbsorbedPercent`（`:1685`）使用，**而它生产上没人调**（`whenFightEnds` 的注释自己写着）。
`damageReductions` 列表是**实例级**，所以现在功能上没问题。
→ 评审说"静态对象当实例级 key 是危险模式"**方向对**，但**这是全项目最轻的一条**（2 处引用、1 处死路）。

---

## 2. 评审里"说法不准确"的部分（别照抄）

1. **"数据注解侵入实体"** —— 实体上只有 5 个，属性注解（38 个）**早就在 `AttributeProfile`**。上一轮 `ENTITY-ATTRIBUTE-SPLIT-2026-10.md` 干的就是这件事。
2. **"`facSetDfk` / `facSetAfk`"** —— 不存在，评审看的是更早的版本。
3. **"`manaMainBase()` 用全限定 `GameRules`"** —— ✅ 存在（`:301-302`、`:309-310`），但评审把包名写错了：实际是 `cn.gfhnv.game.system.configLoadingSystem.GameRules`，不是 `cn.gfhnv.game.GameRules`（那个类不存在）。**我一开始说"查不到"是我的错**——搜错了包名。
4. **"属性存储几十个 getter/setter"** —— 那是**转发**，存储已经在组件里；把"门面方法多"当成"职责多"会高估重构收益。
5. **"`initialMana` 五个分支完全重复"** —— **这条对**（结构逐字相同，只有"哪个元素拿 main base"不同），**且是最容易改的一条**。

---

## 3. 建议的处理顺序（按性价比排；**"状态"列 2026-10-25 第 41 批回写**）

| 顺位 | 事项 | 为什么这个顺序 | 风险 | 依赖 | 状态（2026-10-25） |
|---|---|---|---|---|---|
| **P0** | **`initialMana()` 去重**：改成遍历**那 5 个元素**（⚠️ **不能直接 `ElementSort.values()`**）+ `(e == getElementSort() ? manaMainBase() : manaOtherBase())` | 30 行 → 8 行。⚠️ **两个前提**：① `ElementSort` 有**第 6 个常量 `UNIVERSAL`**（`ElementSort.java:8`），现在**没有分支 = 0 法力**，直接遍历 `values()` 会凭空造出第六种法力；② `:212-248` 还有**另一张** 5 分支表（数值不同），**不能一起并**。生产调用点 4 处 | 低（前提写清） | 无 | ✅ **已做**。抽了 `MANA_ELEMENTS`（5 个元素）+ `manaGrowOf(ElementSort)`；`main == null` / `UNIVERSAL` 时**表格为空**（早先"元素没设会 NPE"变成"空表"）。⚠️ **前提①真的踩到了**：第一版忘了这个 guard，UNIVERSAL 凭空多出 5 条法力，被新加的 `ManaTests` 当场抓住 |
| **P0** | **`manaMainBase()` 去全限定名**（`:301-302`、`:309-310` 共 4 行 + 2 行 Javadoc） | 同包已 import，纯风格。⚠️ 注意包是 `...configLoadingSystem`，别按评审写的 `cn.gfhnv.game` 去搜 | 极低 | 无 | ✅ **已做** |
| **P0** | **`describeReductionSource` 加 import**（`:336` 去掉全限定 `Item`） | ⚠️ 评审说"文件已 import"**不成立** —— import 段里没有 `cn.gfhnv.game.item.Item`，要**新增**一行 import | 极低 | 无 | ✅ **已做**（新增了那行 import） |
| **P1** | **删掉 `livingThingFactory()`**（`:1166-1168`） | 全项目 **0 调用 / 0 重写**，javadoc 却写"子类可重写"→ 纯死 API，删掉零风险 | 极低 | 无 | ✅ **已做** |
| **P1** | **把文档里的"十三参构造器"改成"十四参"**（`70-DATA.md:128`、`90-STATE.md:605`、`AttributeProfile.java:543`） | 参数个数写错会直接误导下一个改构造器的人 | 极低 | 无 | ✅ **已做**（3 处） |
| **P1** | **删掉 `facSetLevel(Long)` 箱子重载**（`LivingThing:1176` + 上面重复的 Javadoc `:1161-1175`） | 同类双载且 `Long` 版 **0 调用** → 零风险；留着只有"传 `null` 就 NPE"的坑 | 低 | **公共 API，先问用户** | ✅ **已做（2026-11-15 第 62 批）**：`Long` 版删除（`long` 版留着） |
| **P1** | **修那 8 行过时的 `criticalDamage` 注释/断言**（`LivingThing:46/77/145`、`AttributeProfile:27/490/563`、`DataCommandTests:149`、`ConfigTests:621`） | 旧键已废弃，注释还写着旧名会误导 | 极低 | 无（**只改注释**，不动方法/类/id） | ✅ **已做**（8 处全改成 `baseCriticalDamage`） |
| **P1** | **`facSetDfk` / `facSetAfk` / `facSetChuantong` 改名**（`Defence`/`Attack`/`Penetration`） | 自造缩写 + 拼错的拼音；但它们是这几个属性的**唯一**链式 setter，属公共 API | 低（仓库内 0 调用、`mods/` 0 引用） | **先问用户**（`10-RULES:189` 命名归用户） | ✅ **已做（2026-11-14 第 44 批）**：用户原话「修复我的拼写错误和不规范的拼写/命名」 |
| **P2** | **抽表现层**：把 4 个"取名/打印"方法搬走 | 实体只剩 1 处 `System.out`，搬走后彻底没有表现层 | 中（159 个文件引用 `LivingThing`，但只有少数调这几个方法） | 先统计这 4 个方法的调用点 | ✅ **已做（2026-11-15 第 63 批）**：搬到 `entity/EntityDisplay`（名字含 `Display` 而非 `Formatter` —— `getNameWithSide` 要给 AI 拼提示词）；`LivingThing` 的 `System.out`/`ConsoleColor` 归零 |
| **P2** | **`getEntityEffectList()` 只读化** | 现在返回活列表：**13 处结构写入**（不是 39 —— 那个数是我数错了口径）+ 57 处只读。⚠️ **返回副本比返回不可变更糟**：13 处里有 2 处是 `listIterator().remove()`（`Phainon:449` 白厄觉醒清负面、`FoundationStardeathVerdict:109` 星裁净化），改成副本会**静默失效**（不报错、机制悄悄不生效），是最像 bug 的假象 | **高**（要先把 13 处改成走 API） | 依赖 `EffectContainer` 拆分 | ✅ **已做（2026-11-14 第 57 批）**：`EffectContainer extends AbstractList`，13 处结构写入全部走 API（另修了 `EffectEventListener` 直接 `remove` 的隐藏 bug） |
| **P2** | **拆 `EffectContainer` / `DamageReceiver` / `ManaPool` / `ControllerHolder`** | 方向对，但一次拆四个会同时动伤害、效果、法力、控制器四条链 | **很高** | 每条都要单独一轮 + 全量自测 + 文档 | **部分做、其余明确不做**：`EffectContainer` ✅（第 57 批）；**`ManaPool` 判定不做**（法力表本来就在 `AttributeProfile` 里，再抽只是搬方法）；**`DamageReceiver` / `ControllerHolder` 用户拍板不做**（2026-11-15） |
| **P3** | **`BASE_DAMAGE_REDUCTION` 改成实例字段** | 最轻，但收益也最小（2 处引用，其中 1 处死路） | 低 | 无 | ✅ **已做（2026-11-15 第 64 批）**：`baseDamageReduction` 实例字段；顺手删了 `getModifyDamage()`（0 调用） |
| **不做** | 见 §4（`dirt` 改名 / 效果 id 改名 / "基础减伤" i18n / 集合直接只读化） | | | | ❌ 仍不做（`getCriticalDamage` 与类名已于第 65 批改掉；**id 字符串仍不改**） |

> 第 41 批实测：`通过 1089 条，失败 0 条`、`CHECK OK`、`src` 263 文件（新增 `ManaTests`，4 条断言）。
> **P2 一条没动** —— 按本文 §5 的约定，动手前要先由用户确认"一次只拆一个组件"。

---

## 4. 与项目约定冲突 / 建议**不做**的四条

| 建议 | 为什么不做 | 依据 |
|---|---|---|
| **`dirt` → `earth/soil`** | `dirt` 是全项目统一键名（src 63 处 + 两份 JSON 各 20 个键 + `/data` 对外契约）。改名 = **破坏用户已有配置 + 破坏 `/data` 脚本**，收益只是"英文更顺" | `70-DATA.md`：**键名 = Java 字段名**，是对外 API |
| **`getManas` / `getEntityEffectList` 直接返回 `unmodifiableList`** | 效果列表：src **61 行**用到、其中 **13 处是结构写入**（`add`/`remove`/`clear`/`removeIf`/`set(i,…)`/`listIterator().remove()`，含 2 处 `listIterator().remove()`）；`getManas` 20 处但**结构写入 0 处**。硬改会大面积编译不过；**返回副本更糟**（13 处里两处会静默不生效） | 必须先有 `EffectContainer` 才能谈封装 |
| **把 `@DataField`/`@NoConfig` 搬去"数据层"** | 搬**注解**本身无害；但把**字段**搬进新 DTO 会有三条确凿的坑：① 组件类型不在 `DataBridge#isDataObject` 白名单 → 整个组件在 `/data` **静默消失**（而 `dataNames()` 照样列得出来，自测假绿）；② 写回是按 **Java 字段名**拼 setter → 字段搬走后实体上的转发 setter 的副作用（`setHp` 的两条事件、`setHpEnhancePercent` 的 `renewHp`）**静默丢失**；③ `@DataFlatten` **不递归** → 嵌套 DTO 静默丢键 | `70-DATA.md` §5.10 + `20-MISTAKES.md` 有血证 |
| **把注释里的日期/拍板记录全删掉** | ⚠️ **一半对一半错**：`10-RULES.md §4` **明确禁止**"原本是 X，现在改成 Y / 这个 bug 被抓到过"这类**历史叙述**；但**允许并鼓励**"当前约束 + 为什么必须这么写"。正确做法是**把"历史/踩坑经过"搬进 `project_analyses/`**（这条我已经在 `80-EFFECTS.md` 里开始做了） | `10-RULES.md §4` 原文 |
| **`"基础减伤"` 走资源键（i18n）** | 项目**没有任何 i18n 机制**，全部文案硬编码中文；而且这个串是**数据键/来源名**（进 `/data`、进日志），不是界面文案 | `10-RULES.md §4`：中文注释与文案是既定风格 |

> ⚠️ **自查**：本轮（2026-10-25）我自己在 `LivingThing`/`Frozen`/`EffectEventListener` 里加了不少
> "2026-10-25 修 …（`80-EFFECTS.md` K3）"这类注释 —— 按 `10-RULES §4` 这属于**历史叙述**，
> 应该改成"当前约束 + 为什么"，把日期与踩坑经过留在 `project_analyses/` / `notes_for_llm/`。
> 这是一条**待清理项**（P1，纯注释，无行为变化）。

---

## 5. 交接给下一个对话的开工清单

**先读**：`notes_for_llm/rules/10-RULES.md`（§4 注释规范、§9.3 别做清单、§9.8 协作硬规则）→ `notes_for_llm/subsystems/70-DATA.md`（键名契约）
→ `project_analyses/current/ENTITY-ATTRIBUTE-SPLIT-2026-10.md`（上一次同类重构怎么做的）→ 本文 §3。

**开工前必须先问用户**（不许自己拍板）：
1. ~~P0 两条（`initialMana` 去重、去全限定名）要不要做~~ → **2026-10-25 第 41 批已做**（零行为变化，`ManaTests` 上锁）；
2. P1 的 `facSetLevel(Long)` 删不删（**改名与 8 处 `criticalDamage` 注释都已做完**：第 44 / 41 批）；
3. P2 的 `EffectContainer` / `getEntityEffectList` 只读化 —— **必须先确认用户接受"一次只拆一个组件"**，并**先拆效果还是先抽表现层**由用户排；
4. §4 四条一律**先问，不要顺手改**。

**改名的正确姿势**（子代理 C 核实）：`getDamage → takeDamage` **不碰任何 `/data` 键**（键来自**字段名**，`LivingThing` 上没有 `damage` 字段），
但必须同步 `BrokenContainer.java:428` 的 `@Override`；而 `check-sources.ps1` / `test-command-system.ps1` **不认任何业务名字**（只查 BOM/括号/import/重复字段、只 `javac src` + 跑 runner）。
真正会被波及的是自测里的字面量与**断言条数**（当前基线 **1089 条**）。⚠️ 但 `10-RULES §9.8-1` 与 `90-STATE:1244` 都写着：**公共 API 改名先问用户**（"纯改名收益小、风险大"）。

**红线**（项目既有约定）：
- 一次只改一条链，改完 `check-sources.ps1` → `test-command-system.ps1`（当前基线 **1089 条 / 0 失败**）→ 报数字；
- **不许**改 `/data` 键名（`70-DATA.md`）；**不许**动 `dirt`；注释只写"当前约束 + 为什么"；
- 测试按 `10-RULES.md` 第 7 条：**一个主题一个类**（`debug_tools/*Tests.java`；法力那条现在在 `ManaTests`）；
- 新增/删文件要同步 `30-WORKFLOW.md` 的 `src` 文件数（现在 **263**）。

---

## 6. 子代理复核补数（2026-10-25，只读核查）

### 6.1 每条路线的真实调用点（口径：`grep -o` 逐文件数）

| 路线 | 声明 | 生产外部调用 | 类内 | 测试 | 风险 |
|---|---|---|---|---|---|
| 1 抽表现层 | 4 方法 | **12 点 / 8 文件** | 3 | 5 | **低（建议 P0）** |
| 2a ManaPool | `initialMana` / `recoverManaEveryTurn` / `getManas` | **4 / 1 / 28** | 3 | 15 | **低（P1）** |
| 2b EffectContainer | `addEffect` / `removeEffect` / `getEntityEffectList` / `setEntityEffectList` | **21 / 1 / 70 / 2** | 1 | 37 | **高（最后做）** |
| 2c DamageReceiver | `getDamage` / `modifyIncomingDamage` / `damageReductions` 写点 | **2 / 2 / 11** | 1 | 17 | 中（可两步） |
| 2d ControllerHolder | `getController()` / `setController` | **86 处 / 29 文件**、18 | 5 | — | 中（面大语义小） |
| 3 Builder 替 facSet | 33 个方法 | **9 点 / 1 文件**（`actionBarTest/TestMain:19,21,23`） | 0 | 0 | 低（但是公共 API） |
| 4 集合封装 | 效果列表 13 处结构写入；`getManas` **0 处结构写入** | — | — | — | 效果**高** / 法力**低** |
| 5 注解/DTO | 注解定义在 `cn.gfhnv.game.data` | — | — | — | 搬注解**无害**、搬字段**中高** |
| 6 改名 | `getDamage` 真调用 **12** | — | — | — | 中（先问用户） |

### 6.2 表现层 4 个方法的调用点（路线 1 的依据）

| 方法 | 可见性 | 类内 | 生产外部 | 说明 |
|---|---|---|---|---|
| `printAttackLine` | **private** | **1**（`LT:2146`，`makeDamage` 末尾） | 0 | 搬它 = 实体彻底不打印（`System.out.print*` 1 → **0**，`ConsoleColor` 6 处 → **0**） |
| `getNameWithSide` | public | 0 | **8 点 / 6 文件** | ⚠️ `ThinkingControllerAI:194` 用它**拼 AI 提示词**，不只是日志 → 叫 `CombatLogFormatter` 名不副实，建议叫 `NameFormatter`/`DisplayText` |
| `getNameWithUuidAndSide` | public | 2（都在 `printAttackLine` 里） | **3** | 另有 2 条测试断言 |
| `getNameWithUuid` | public | 0 | **1**（`FightTurnPastListener:232` 回合头） | 另有 3 处测试引用 |

> 搬时要一起带走两个私有 helper：`shortUuidTag`（`LT:1583`）、`sideName`（`LT:1590`）；依赖都是 public，不会成环。

### 6.3 三条"顺手捡"的发现

1. **`getModifyDamage()`（`LT:2338-2349`）全项目 0 调用点** —— 真死代码，想删可以单独一条（不是装配点；装配点在 `modifyIncomingDamage:2259-2265` 的 for）。
2. **`facSet*` 33 个方法几乎没有调用者**（9 点、全在一个手工调试 `main` 里）→ "用 Builder 替掉"收益很低，**但它是公开 API**，按规矩先问用户。
3. **`damageModifiers` 用 `addAll` 浅拷贝**、修正器闭包捕获模板实例 —— `60-COMBAT.md:302-306` 有血证（李晓燃点被算成"模板值 −10"）。**拆 `DamageReceiver` 前必须先想清"副本拿到的是哪些实例"**。

### 6.4 与上次重构（`ENTITY-ATTRIBUTE-SPLIT-2026-10.md`）对照：这次会重演的 4 个坑

① 组件类型不在 `DataBridge#isDataObject` 白名单 → `/data` 里静默消失而自测假绿；
② **setter 副作用跟着字段走**（`addEffect` 自带三个副作用：`applyRegisteredId` / 同 id 合并 / `initialEffect`，搬时漏一个机制就坏）；
③ 反射契约测试只扫本类字段 → 搬集合字段必假绿；
④ `/data get` 的键顺序按字段声明顺序变（顺序不是契约，但用户会觉得"变了"）。
**上次的代价**：`LivingThing` 2584→2369、`AttributeProfile` 634→1440，**两文件合计 +587 行** —— 别指望"文件变小"。

### 6.5 命名与风格 9 条的核查结论（子代理 B）

| # | 建议 | 结论 | 依据 |
|---|---|---|---|
| 1 | `getDamage` → `takeDamage` | ✅ **已做（2026-11-15 第 62 批）**：`BrokenContainer` 覆写 + 14 处调用/文档同步 | — |
| 2 | `facSetDfk/Afk/Chuantong` 改名 | ✅ **已做（2026-11-14 第 44 批）**：改为 `facSetDefence` / `facSetAttack` / `facSetPenetration`（仓库内 0 调用，无风险） | — |
| 3 | `dirt` → `earth/soil` | **不做**（`src` 95 行 / 13 文件，两份 JSON 各 20 个键，还会碰 `"element":"DIRT"`） | `70-DATA:31-32` |
| 4 | `criticalDamage` 统一 | **半做**：方法/类/效果 id **不动**（对外 id）；只修 8 行过时注释 | `70-DATA:100-102` |
| 5 | 删 `facSetLevel(Long)` | **可做**（同类双载、`Long` 版 0 调用），但要先问用户 | — |
| 6 | `manaMainBase()` 去全限定 | **做**（P0，4 行 + 2 行注释） | — |
| 7 | `describeReductionSource` 去全限定 | **做**（P0，1 行代码 + **要新增 import**） | — |
| 8 | 注释里的开发日志 | **半做**：`LivingThing` 里共 **16 行**（带日期 7 + 无日期历史叙述 9）→ **留"当前约束"、把历史搬进本文档** | `10-RULES §4:95-105` |
| 9 | `"基础减伤"` 走 i18n | **不做**（`10-RULES §9.3:153` 明确举例它；项目无 i18n 机制；且它是**来源名**会进 `/data`） | `10-RULES:153`、`70-DATA:100` |

> ⚠️ **B 纠正了我上一条说法**：`10-RULES §4` **并没有**"注释要写为什么 + 日期 + 证据"的要求
> （要求日期的是**笔记** `20-MISTAKES:76`）。§4 的原话是"注释只写当前是什么 / 为什么必须这么写"，
> **禁止**历史叙述、**保留**有价值的当前约束。所以"清理注释"是**合规**动作，但"整段删"不行。

### 6.6 数字口径说明（三个子代理交叉核对后的结论）

同一件事在不同 grep 口径下会差出一截，引用时**必须带口径**：

| 说法 | 口径 A（行数 / `grep -c`） | 口径 B（`grep -o`，一行多次算多次） | 该用哪个 |
|---|---|---|---|
| `getEntityEffectList` | src **61 行**（+ mods 2 + 类内 1） | **70** | 谈"改动要碰多少处"用**行数 61**；谈"运行多少次"用 70 |
| 表现层调用点 | **13 个调用表达式 / 9 文件** | 12 点 / 8 文件（`ThinkingControllerAI:194` 一行两次） | 谈影响面用**表达式数 13** |
| `@DataFlatten` | 行首真注解 **1** | 字符串出现 **2**（含 javadoc） | 用**真注解 1** |
| `AttributeProfile` 注解 | 行首 `@NoConfig` **27** | 行内出现 37（含 javadoc）/ 我去重前写过 38 | 用**真注解 27** |
| `facSet*` | 方法 **33 个** | 出现 34 次（含 1 处 javadoc） | 用**方法数 33** |

> **教训**：`grep -c` 数的是"行"，`grep -o` 数的是"次"，`^    @Xxx` 才数得到"真注解"。
> 我在这一轮的初稿里就是混用了三种口径，被两个子代理各抓出 2 处。

### 6.7 仍未核 / 待补

- 三个子代理（A 职责与耦合 / B 命名逐条 / C 重构可行性）都回来了，**A 与 B 各纠正了我 4 处数字**，已在 §1.1、§1.3、§6.5、§6.6 修正并标注口径。
- `@NoConfig` 全项目 78 处里约 14 处是测试字面量，真实标注数没逐个核（`LivingThing` 与 `AttributeProfile` 这两个关键类已核）。
- **一个构建/自测都没跑**（子代理硬约束）：`javac` 是否过、`/data` dump 是否真的不变、搬走打印后日志顺序会不会变，全部未验证。

> 本文的数字都是 2026-10-25 现量的（`wc -l` / `grep -c` / `grep -o` / `grep -rl`）。
> 子代理结论**未经我复核的不进 §3**；§3 里已复核的会标"（已复核）"。

## 7. 复查记录：新对话实施之后（2026-10-25）

另一个对话按 §3 做了 P0 + 两条 P1，我用 `check-sources.ps1` / `test-command-system.ps1` / `git diff` / 逐条读码复核了全部 6 项。

### 7.1 结果

| 项 | 复核结论 | 证据 |
|---|---|---|
| `initialMana()` 去重 | ✅ **语义等价**（5 条、顺序金木水火土、只有主元素拿 `mainBase`、`UNIVERSAL` 仍为空表） | `LivingThing.java:630-660`；`MANA_ELEMENTS` 显式列 5 个元素（`List.of`），**没有**用 `values()` |
| 去全限定名 | ✅ `manaMainBase`/`manaOtherBase` 已用短名（`RuleDefaults` 已 import，javadoc `{@value}` 可解析）；`describeReductionSource` 的 `Item` 已加 import | `:19-21`、`:298-310`、`:325-336` |
| 删死 API | ✅ `livingThingFactory()` 已删；全项目只剩本文档提到它 | `grep -rn livingThingFactory src/ mods/` → 0 |
| 过时数据键 | ✅ 8 处 **纯注释/断言文案**（`LivingThing` 3、`AttributeProfile` 3、`DataCommandTests` 1、`ConfigTests` 1），方法/类/效果 id 一个没动；`AttributeProfile` 的 diff **只有 4 行注释** | `git diff HEAD -- AttributeProfile.java` |
| 文档口径 | ✅ `AttributeProfile#initPanel`、`70-DATA.md`、`90-STATE.md` 都改成"十四参" | `grep -rn 十四参` |
| 守卫 +4 | ✅ 新增 `ManaTests`（3 个用例 / 4 条断言），runner 已注册 | `ManaTests.java`（154 行）、`TestCommandSystem.java:30` |

**当时的总量**：`通过 1089 条，失败 0 条`（1085 → +4）、`CHECK OK`、`src` **263** 文件（262 → +1）。基线已同步进 `30-WORKFLOW.md:234` 与 `90-STATE.md:18`。

### 7.2 两个我特意查过、结论是"没问题"的点

1. **"去重后语义会不会变"**：`ElementSort` 有第 6 个常量 `UNIVERSAL`；新代码用 `MANA_ELEMENTS.contains(main)` 守了一道，
   而**旧代码**是 `switch (this.getElementSort())` **且没有 default 分支**（已用 `git show HEAD:` 核对）⇒ 旧行为也是"空表"。
   **唯一的差异**是 `main == null`：旧代码 NPE、新代码空表。但**构造期传 `null` 会在更早的 grow 表 `switch` 里先 NPE**（`:212`），
   所以只有"构造完再 `setElement(null)` 又调 `initialMana()`"才会不同 —— 实际不可达，且新 javadoc 已写明"还没设元素 → 空表"。
2. **`initialMana` 那张 grow 表里 `WATER` 与 `DIRT` 常常同值（都是 10），会不会测不出错配？**
   逐行算过：每个主元素下确实有**一对**元素同值（METAL：水/土；WOOD：水/火；WATER：金/木；FIRE：木/土；DIRT：金/火），
   但 `ManaTests` **对 5 个主元素各跑一遍**，任意一对元素在某个主元素下必然异值 ⇒ 任何一种稳定错配都会被抓到。**覆盖是够的。**

### 7.3 剩下的小事（都不影响正确性）

- `ENTITY-ATTRIBUTE-SPLIT-2026-10.md` 里还有 **2 处**"十三参"（历史分析文档）。按"越晚越准"可以留，顺手改也行。
- 本文 §1.2 里 `livingThingFactory` 的行号（`:1166-1168`）已随删除失效，上面已改成"已删除"。
- 可选（1 行）：给 `ManaTests` 补一条"`setElement(null)` 后 `initialMana()` 得到空表且不抛"—— 把 7.2 第 1 条钉死。
- `manaGrowOf` 的 `case UNIVERSAL -> 0` 是 switch 穷尽要求，实际不可达（`contains` 已挡）。
