# 「配置加载子系统解耦」评估与改造方案（2026-10-03 版）

> **这是什么**：对用户 2026-10-03 那句话的评估 ——
> 「现在的加载代码解耦性有些差吧？`DataKeys` 写的有些离谱了。能否优化？我看到里面写了一堆定义。」
>
> **本文一行 `src/` / `mods/` / `config/` 代码都没改**，只新建了这一份文档。
>
> **谁要读**：下一个要碰 `src/cn/gfhnv/game/system/configLoadingSystem/`、
> 要给实体/技能/规则**加一个新可配置键**、或者想「顺手重构一下加载器」的 AI。
> 读完这一份就够。**先读 `EXTERNAL-DATA-LOADING-2026-10.md`**（那份是这套子系统的设计稿 +
> 阶段 0–4 的落地记录，本文不重复它的结论，只回答「结构该怎么切」）。
>
> **一句话结论**：**解耦差的不是「常量太多」，而是「同一件事被切成三张必须手写对齐的表」。**
> `DataKeys` 里那 884 行本身不是病 —— 病是它旁边还有 `ConfigDefaultWriter`（写）、
> `EntityDataPatcher`（读）、`TestCommandSystem`（校验）**各维护一份平行的键清单**，
> 而四者之间靠自测「事后对齐」。**解法是把它变成一张 `KeySpec` 表 + 一个通用补丁器**，
> 让「新增一个可配置键」从**改 5 个文件 12 处**降到**改 1 个文件 1~3 处**（逐处清单见 §2.2–§2.5）。

---

## 零、TL;DR

| 问题 | 结论 | 关键理由 |
|---|---|---|
| **「解耦差」具体差在哪？** | **差在「三张平行表 + 两个手写常量清单」**，不在「常量多」 | `DataKeys.META`（41 键）、`DataKeys.GROUPS`（41 键）必须**手写对齐**；`ConfigDefaultWriter` 另手写 27 处 `put`；`EntityDataPatcher` 另手写 27 处 `applyXxx`/`lookup`（两边**恰好一样多**，靠自测对齐，不是靠结构）。四处之间**没有任何编译期约束** |
| **新增一个可配置实体键要改几处？** | **A. 让已有字段生效：2 个文件 2 处；B. 加全新实体字段：4 个文件 9~10 处（五行类字段 5 个文件 27 处）；C. 加全新规则键：4 个文件 5~7 处**（见 §2.2 / §2.3 / §2.5 的逐处清单） | 最刺眼的是 A：`DataKeys` 里 41 个键的声明**都已经写好了**，但其中 **14 个没人读**（D1）—— 救活一个只要 2 行 |
| **有没有既有缺陷？** | **有，7 条**，其中 **2 条会静默** | ★★★ `DataKeys.META` 里 **14 个键声明「能配置」，补丁器从来不读** → 写进 `EntityData.json` **无声无息不生效**（§1.5 D1/D2）；★★★ 用户手上三份 JSON **是空的**（§1.7 D5） |
| **`*Patcher` 是手写还是表驱动？** | **手写逐键调用**，1132/865 行里**补丁逻辑只有约 85 行** | `EntityDataPatcher.patch()`（`:187-215`，29 行）+ `applyDerived()`（`:366-375`，10 行）+ 五行两个 switch（`:417-444`）就是全部；其余是 `Report`（155 行）、`Snapshot`（92 行）、类型判定（68 行）、javadoc |
| **能不能表驱动？** | **能，而且是这个子系统的正确形状** | 键的「名字 / 类型 / 分组 / 读法 / 写法」全部可声明；`SkillDataPatcher` 已经是半个表驱动（`isKnown()` 是手写或链，`:396-404`） |
| **能缩到多少行？** | `ConfigDefaultWriter` **869 → 约 320**（−63%）；`EntityDataPatcher` **1132 → 约 520**（−54%）；`SkillDataPatcher` **865 → 约 420**（−51%）；`DataKeys` **884 → 约 300**（−66%） | 估算依据见 §3.5 |
| **改 key 名吗？** | **一个字都不改**。`/data` 键名、`EntityData.json` / `SkillData.json` / `GameRules.json` 的键名、`config/data/<modid>.json` 的格式**全部冻结** | 用户已经在用真文件（§4.1）；模组契约写在 `MODDING-GUIDE.md` 里；`70-DATA.md:24-25` 写明「改名等于破坏 `/data` 脚本与将来的存档」 |
| **分几阶段？** | **5 个阶段**，阶段 0 零风险（新增键清单守卫自测，不改一行运行期代码） | 每步自洽、可单独停；阶段 0 立刻能抓到 §1.5 那 14 个假键 |
| **最大风险** | **「把四张表合并成一张」时漏掉一个键 → 从「声明了不生效」变成「彻底不存在」** | 所以阶段 0 必须先有「键清单守卫」，阶段 1 才有安全网 |

---

## 一、现状体检（2026-10-03 实测，行号/计数一律用 `ReadAllLines` 复核，复现命令见附录 A）

### 1.1 规模

| 文件 | 行数 | javadoc/注释行（约） | 真正的逻辑行（约） |
|---|---|---|---|
| `EntityDataPatcher.java` | **1132** | 约 520 | 约 610（含 `Report` 155 + `Snapshot` 92 + 类型判定 68） |
| `DataKeys.java` | **884** | 约 430 | 约 450（其中 `buildGroups`+`buildMeta` 两张表 **100 行**） |
| `ConfigDefaultWriter.java` | **869** | 约 180 | 约 690（其中内嵌 JSON 排版器 **150 行**：`:694-844`） |
| `SkillDataPatcher.java` | **865** | 约 390 | 约 475 |
| `ConfigLoader.java` | **749** | 约 250 | 约 500 |
| `RuleDefaults.java` | **232** | 约 110 | 约 120 |
| `GameRulesPatcher.java` | **226** | 约 90 | 约 136 |
| `GameRules.java` | **219** | 约 90 | 约 129 |
| **合计** | **≈5176** | | |

对照：`LivingThing.java` **2353** 行、`TestCommandSystem.java` 4877 行、`src` 共 **215** 个 `.java`、
`mods` 共 **13** 个 `.java`（`check-sources.ps1` 本轮**没能跑**：沙箱拒绝 `powershell.exe`，
见 §8 不确定项；文件数用 `Get-ChildItem` 数的，口径与脚本一致）。

### 1.2 「逐个文件、说清它承担了几种职责」

| 文件 | 承担的职责 | 哪两种是「不同变化原因却绑在一起」 |
|---|---|---|
| **`DataKeys.java`（884 行）** | ① **JSON 格式常量**（`VERSION` `ENTITIES` `SKILLS` `BASE` `DERIVED` `MANA_GROW_BLOCK` `SKILL_SEPARATOR` `SECTION_SEPARATOR`，`:73-127`）<br>② **数据键名**（`Base` `:373-422`、`Derived` `:430-447`，共 26 个 —— 这一族与 `/data` 共享契约）<br>③ **规则键名**（`Rule.*` 嵌套类 `:211-368`，**29 个**）<br>④ **键的分类**（`Temporary` `:453-479`、`ReadOnly` `:490-595`、`NotInData` `:629-630`、`Alias` `:635-648`）<br>⑤ **元数据表**（`META` `:668`，由 `buildMeta()` `:842-872` 手写 41 条）<br>⑥ **分组表**（`GROUPS` `:663`，由 `buildGroups()` `:822-837` 手写 41 条）<br>⑦ **与反射的对照逻辑**（`auditAgainst` / `auditNotes` / `collectFields` `:737-817`，**只给自测用**）<br>⑧ **类型名常量**（`TYPE_*` `:46-58`） | ★ **② 与 ⑦ 绑在一起**：对外契约（键名）与「怎么用反射核对这个契约」是两件事，后者依赖 `LivingThing` / `DataBridge`（`:3-4` 两个 import）→ **契约表被自测工具污染**，`Lobby` 之外的任何地方 import 它都会拖进反射工具链<br>★ **④ 与 ⑤⑥ 绑在一起**：分类意图（「这个键能配」）与分类结果（`META`/`GROUPS` 两张手写表）必须人工对齐<br>★ **①②③ 绑在一起**：`SkillKeys` `:140-199` 与 `Rule` `:211-368` 是**另外两套键空间**（技能键、规则键），它们跟实体键的**生命周期、加载器、变化原因完全不同**（见 `70-DATA.md:46-47`，文档自己都点明了） |
| **`EntityDataPatcher.java`（1132 行）** | ① **定位**（`targetsOf` `:131-169`，完整 id / 短名歧义）<br>② **补丁顺序**（`patch()` `:187-215`）<br>③ **派生值重算**（`recalculateDerivedStats` `:229-237`，**重写了 `LivingThing` 的三围公式**）<br>④ **五行 switch**（`resistanceSetter` `:417-425`、`setManaGrow` `:434-444`、`manaGrowOf` `:721-729`、`Snapshot.setResistance` `:842-850` —— **同一件事写了 4 遍**）<br>⑤ **背包格数**（`applyInventorySlots` `:458-496`，唯一有副作用的写入口）<br>⑥ **容错报错**（`reportUnknownKeys` `:250-276`、4 个 `applyXxx`）<br>⑦ **报告 `Report`** `:960-1114`（155 行）<br>⑧ **快照 `Snapshot`** `:760-851`（92 行，**只给自测用**）<br>⑨ **类型判定 + 文案** `asLong/asDouble/describe` `:861-904`（**被另外两个文件当公共工具用**） | ★ **①⑥ 与 ②③④⑤ 绑在一起**：「找目标 + 报错」与「怎么把值写进去」变化原因不同（前者跟注册表/命名走，后者跟属性走）<br>★ **⑧ 与前面全部绑在一起**：自测快照是**生产类里的测试专用代码**，92 行<br>★ **③ 绑住了 `LivingThing` 的公式**：`200` / `110` / `200` 三个字面量**又一次**出现在 `:231-233` —— 名义上 `GameRules.json` 已经管住了公式（`RuleDefaults.FORMULA_HP_BASE` 等），而且**同一个方法里 `:236` 的 `initialMana()` 却是走规则表的** |
| **`SkillDataPatcher.java`（865 行）** | ① 定位（`targetsOf` `:128-156` + `entitiesOf` `:165-200`，**与 `EntityDataPatcher.targetsOf` 逐行同构，24 行完全相同**）<br>② 8 个键的应用（`patch()` `:227-238`）<br>③ 三个特殊块（`applyExtraNumeric` `:251` / `applyConsumedMana` `:286` / `applyTags` `:335`）<br>④ 容错报错（`reportUnknownKeys` `:380-390` + `isKnown` `:396-404`，**手写或链**）<br>⑤ 报告 `Report` `:721-847`（127 行，**与 `EntityDataPatcher.Report` 结构相同**）<br>⑥ 快照 `SkillSnapshot` `:619-665`（**只给自测用**）<br>⑦ 自测工具 `stateOf/find/all` `:528-598` | ★ **① 与 ② 绑在一起**，且 **① 与 `EntityDataPatcher` 的同类方法重复**<br>★ **④ 的 `isKnown()` 是手写或链**：加一个技能键要**同时**改 `patch()` 与 `isKnown()`（否则它会被报成「未知键」）—— 这正是「两张手写表」的缩小版 |
| **`ConfigDefaultWriter.java`（869 行）** | ① **实体 dump**（`collectBaseValues` `:415-447` 写 **23 项** = 12 个具名 + 五抗性 5 + 五法力成长 5 + `inventorySlots` 1；13 行静态 `put` + 循环里 2 行 × 5 元素。`collectDerivedValues` `:454-472` 写 **4 项**）<br>② **技能 dump 两遍**（`collectSkillValues` `:479-517` 写 6 键【供参考副本】、`collectSkillPatches` `:528-598` 写 8 键【供 `SkillData.json`】）<br>③ **规则 dump**（`gameRulesJson` `:285-309`，遍历 `GameRules.allKeys()`）<br>④ **JSON 排版器**（内嵌 `Json` `:694-844`，150 行）<br>⑤ **落盘**（`writeAll` `:344-365` + 3 个 `writeXxx`）<br>⑥ **「出厂值」参考副本**（`referenceJson` `:137-145`） | ★★ **① 与补丁器是两张平行的键表，而且条数一模一样（各 27 处）**：生成器 13 行静态 `put` + 2 行 × 5 元素 + 4 行 `put(derived…)`；补丁器 12 处 `applyXxx` + `applyElementValues` 里的 2 × 5 + `applyDerived` 里的 4 + `applyInventorySlots`。**两边恰好对齐，靠的是自测那条「全量默认值打回去、逐字段不变」**（`TestCommandSystem.java:3013-3030`）—— 不是靠结构<br>★ **② 内部自己重复一遍**：`collectSkillValues`（`:506-511` **用字符串字面量**）与 `collectSkillPatches`（`:569-574` 用 `DataKeys.SkillKeys.*` 常量）**写的是同一批键**<br>★ **④ 与其余全部绑在一起**：150 行 JSON 排版器跟「dump 实体数值」毫无关系 |
| **`ConfigLoader.java`（749 行）** | ① 5 个路径常量 + 静态建目录（`:58-82`、`:158-161`）<br>② **TagConfig 的整套加载**（`loadConfig` `:213-288` + `DEFAULT_TAGS_CONFIG` 内嵌 41 行 JSON `:87-127` + `parseTagType` / `tagNames` / `describe` / `lineOf` `:685-748`）<br>③ 实体/技能/规则三个薄包装（`loadEntityData` `:304` / `loadSkillData` `:349` / `loadGameRules` `:392`，**三个方法结构完全相同**）<br>④ 模组配置（`resolveModConfigId` `:449` + 两个 `loadModData` `:502/:529` + `loadAllModData` `:587`）<br>⑤ 写默认文件（`writeDefaultConfigFiles` `:606-615`） | ★ **② 与 ③④ 绑在一起**：TagConfig 是**唯一还在用另一套写法**（内嵌默认 JSON 字符串 + 手写逐键循环）的加载器，跟后三份「补丁」语义的配置**没有任何共同代码**<br>★ **`DEFAULT_TAGS_CONFIG`（`:87-127`）与 `config/gameConfig/TagConfig.json` 是同一份内容的两个副本**（逐字节对比见 §1.5 D4）<br>★ **③ 三个方法逐行同构**，只是文件常量与 patcher 类不同 —— 只有 `EntityData` / `SkillData` 各自带一个 `loaded` 布尔（`:139`/`:143`），`GameRules` 那个（`:150`）语义还不一样（它同时管「冻结」） |
| **`RuleDefaults.java`（232 行）** | ① **29 个规则键的出厂值字面量**（`:28-152`）<br>② **键 → 值映射**（`build()` `:192-231`，**把上一段的常量再点名一次**） | ★ **① 与 ② 绑在一起，而且是"逐条抄一遍"**：加一个规则键要**先加常量、再在 `build()` 里加一行 `map.put(DataKeys...常量, 常量)`**，两处写同一个值 |
| **`GameRules.java`（219 行）** | ① 取值接口（`getDouble/getLong/getInt` `:63-85`）<br>② **「认识哪些键」清单**（`knownKeys()` `:108-140`，**29 行 `keys.add(...)`**）<br>③ 冻结/复位（`beginLoad/put/freeze/resetForTest` `:174-218`） | ★ **② 是第四份手写键清单**：`DataKeys.Rule.*`（29 常量）+ `RuleDefaults`（29 常量 + 29 行 map）+ `GameRules.knownKeys()`（29 行 add）+ `GameRulesPatcher.SECTIONS`（5 个段名字面量 `:34-35`）+ `ConfigDefaultWriter` 的 `"version"`（1 行）= **同一个键空间被写了 5 遍** |
| **`GameRulesPatcher.java`（226 行）** | ① 段名清单（`SECTIONS` `:34-35`，**字面量**）<br>② 逐段逐键读（`apply` `:52-92`）<br>③ 报告 `Report` `:128-225`（98 行，**第三个同构 `Report`**） | ★ **① 与 `DataKeys.Rule` 的段名重复**：`"formula"` / `"mana"` / `"flameReaver"` / `"insectBoss"` / `"actorLiXiaoYan"` 在 `DataKeys` 里是 `"formula.hpBase"` 这种**拼好的字符串**（`:220` 等），在 `GameRulesPatcher` 里**又散写一遍**。加一个段要改两处 |

### 1.3 「同一个键名在多个文件里各写了一遍」—— grep 证据

**口径**：`"键名"` 形式的**字符串字面量**（用 `[System.IO.File]::ReadAllText` + 正则 `"[A-Za-z][A-Za-z0-9_.]{2,40}"` 数，排除注释行）。

**① 每个文件的键字面量总数**

| 文件 | 字符串字面量键 | 去重后 |
|---|---|---|
| `DataKeys.java` | **120** | 102 |
| `EntityDataPatcher.java` | 28 | 12 |
| `ConfigDefaultWriter.java` | **22** | **18** |
| `GameRulesPatcher.java` | 5 | 5 |
| `SkillDataPatcher.java` | 11 | 8 |
| `GameRules.java` | **0** | 0 |
| `RuleDefaults.java` | **0** | 0 |
| `ConfigLoader.java` | 1 | 1 |

> `GameRules` / `RuleDefaults` 的 0 是**好的那一半**（键集中）—— 它们做到了「只有 `DataKeys` 写键名」。
> `ConfigDefaultWriter` 的 22 / 18 是**坏的那一半**（见下表）。

**② 同一个键名出现在哪几个文件**（表头 = 文件，格 = 该文件里这个字面量出现次数）

| 键名 | `ConfigDefaultWriter` | `DataKeys` | `EntityDataPatcher` | `SkillDataPatcher` | 结论 |
|---|---|---|---|---|---|
| `aims` | **1** | 1 | 0 | 0 | **两处写**（生成器 `:506` 与 `SkillKeys.AIMS` `:144`） |
| `coolDown` | **1** | 1 | 0 | 0 | **两处写**（`:507` / `:148`） |
| `hpMagnification` | **1** | 1 | 0 | 0 | **两处写**（`:508` / `:152`） |
| `atkMagnification` | **1** | 1 | 0 | 0 | **两处写**（`:509` / `:156`） |
| `defMagnification` | **1** | 1 | 0 | 0 | **两处写**（`:510` / `:160`） |
| `forEnemies` | **1** | 1 | 0 | 0 | **两处写**（`:511` / `:164`） |
| `amount` | 0 | **2** | 0 | **2** | **三处写**（`DataKeys :166/:172` 是 javadoc 里的示例 + 常量；`SkillDataPatcher :56/:270` 也是 javadoc 示例） |
| `element` | 0 | **3** | 0 | **2** | 同上；另在 `EntityDataPatcher` 是 `Alias.ELEMENT` 常量引用（不算字面量） |
| `version` | 0 | 1 | **1** | **1** | **三处写**（`DataKeys.VERSION` `:73`、`EntityDataPatcher :80` javadoc、`SkillDataPatcher :77` javadoc） |
| `entities` | 0 | 1 | **1** | 0 | **两处写**（`DataKeys.ENTITIES` `:77` vs `EntityDataPatcher :80` **javadoc 里手抄**） |
| `skills` | **1** | 1 | 0 | **1** | **三处写**（`ConfigDefaultWriter.SKILLS` `:70` vs `DataKeys.SKILLS` `:85` vs `SkillDataPatcher :77` javadoc） |
| `base` | 0 | 1 | **1** | 0 | **两处写**（`DataKeys.BASE` `:95` vs `EntityDataPatcher :281` 的 **javadoc 里手抄**） |
| `derived` | 0 | **2** | 0 | 0 | 同文件内两处（`DataKeys.DERIVED` `:99` 与 `buildGroups` 里的字面量 `:832`） |
| `metal`/`wood`/`water`/`fire`/`dirt` | **10** | **5** | **19** | 0 | **三个文件、四个地方**各写一份五行名（见 ③） |
| `Resistance` / `ManaGrow` / `Penetration` / `DamageEnhance` | **4** | **4** | **4** | 0 | **两处写**：`DataKeys` 用 `element + "Resistance"` 拼（`:829`），另外两个文件也各自拼（`ConfigDefaultWriter :439-440`、`EntityDataPatcher :387/:408`） |

**③ 五行那 20 个键：同一件事在 4 个地方各写一遍**（这是全项目最集中的重复）

| 写在哪 | 行号 | 形态 |
|---|---|---|
| `DataKeys.ELEMENTS` | `:67-68` | `List.of("metal","wood","water","fire","dirt")` —— **唯一该有的那一份** |
| `DataKeys.buildGroups()` | `:828-831` | 用 `element + "Resistance"` 拼键名 |
| `DataKeys.buildMeta()` | `:856-861` | 又拼一遍（**同文件内第二次**） |
| `ConfigDefaultWriter.collectBaseValues()` | `:438-441` + `resistanceOf` `:628-636` + `manaGrowOf` `:643-651` | **拼 + 两个 `switch` 各 5 个 case** |
| `EntityDataPatcher.resistanceSetter` | `:417-425` | `switch` 5 个 case |
| `EntityDataPatcher.setManaGrow` | `:434-444` | `switch` 5 个 case |
| `EntityDataPatcher.manaGrowOf` | `:721-729` | `switch` 5 个 case |
| `EntityDataPatcher.Snapshot.setResistance` | `:842-850` | `switch` 5 个 case（**同一个文件里第三次**） |

> **5 个手写 `switch` + 3 处字符串拼接描述同一件事。**
> 加第六个元素（比如「雷」）要改 **8 处**，而其中任何一处漏掉都**不会编译报错**
> —— 只会得到「配置里写了没反应」。

**④ 最刺眼的一条：`ConfigDefaultWriter` 写技能键用的是字符串字面量**

```java
// ConfigDefaultWriter.java:506-511  ← 字面量，与 DataKeys 无关
put(values, "aims", skill.getAims());
put(values, "coolDown", skill.getCoolDown());
put(values, "hpMagnification", skill.getHpMagnification());
...
// ConfigDefaultWriter.java:569-574  ← 同一个文件里，同一个意思，却用常量
scalars.put(DataKeys.SkillKeys.AIMS, Json.value(skill.getAims()));
```

同一个类里，**同一个键名有两种写法**。这就是用户说的「两处写真值，迟早一真一假」的教科书例子。

### 1.4 「两处写真值」的第五处：`TagConfig` 的内嵌默认 JSON

`ConfigLoader.DEFAULT_TAGS_CONFIG`（`:87-127`，41 行字符串，14 段）与 `config/gameConfig/TagConfig.json`（41 行）
是**同一份内容**（我把 Java 那串拼接去引号、去转义、去掉全部空白之后与 JSON 文件逐字符比对：
**26 个键值对逐个相同**，7 个 id = `insectBoss` 3 + `commonInsect` 3 + `iceInsect` 4 + `playerOne` 5 +
`actorLiXiaoYan` 5 + `aNiceSword` 1 + `phainon` 5；连 `"},"game_official_content:phainon": {`（`:120`）
这种排版怪癖都一样；复现命令见附录 A 第 ⑧ 条）。
两处写真值：改了 JSON 文件、忘了改 Java 字符串 → **删掉配置文件重新生成时，用户的改动被悄悄回退**。

### 1.5 读代码时发现的既有真缺陷（有几条写几条）

| # | 严重度 | 缺陷 | 证据（行号已用 `ReadAllLines` 核对） | 后果 |
|---|---|---|---|---|
| **D1** | ★★★ | **`DataKeys.META` 里 14 个键声明「能配置」，补丁器从来不读它们** | `META` 共 **41** 键 = `base` 22 + `derived` 4 + `temporary` **15**（`buildMeta` `:842-872`），但其中 `showSpecialMes`（`:478`）**没被 `buildMeta` 登记** → `META` 里实际是 **14 个 `temporary` 键**。而补丁器**真的会读**的只有：`patch()` 里的 12 处（`:188-206`：level / name / mass / type / description / speed / elementSort / hpGrow / attackGrow / defenceGrow）+ `applyElementValues` 里的 2 × 5（五抗性 `:387` + 五法力成长 `:390`）+ `applyDerived` 4（`:367-373`）+ `applyInventorySlots` 1（`:208`）= **26 个键**。**那 14 个（`criticalDMG` `:457`、`criticalRate` `:461`、`enhance` `:465`、`defenseLoss` `:469`、`extraDamage` `:473`、五元素 `*Penetration` / `*DamageEnhance` `:859-860`）一个都没被读** | 用户在 `EntityData.json` 里写 `{"base":{"criticalRate":0.5}}` → `reportUnknownKeys`（`:250-276`）用 `DataKeys.isConfigurable("criticalRate")`（`:694`）= `true` 判定「这是认识的键」→ **不报未知、不报跳过、不生效**。★★ **这是最阴的一类：控制台说「应用 0 项，跳过 0 项」，一切正常**。⚠ 这 14 个的**生命周期并不一样**，接上之前必须先分类（见下面的「D1 补充」） |
| **D2** | ★★ | **`Temporary` 分组名是个死概念，写出来也不生效** | `DataKeys.GROUP_TEMPORARY = "temporary"`（`:119`）在 `buildGroups` 里被用了 2 次（`:830`、`:833`），但 `BARE_SECTIONS` 只有 `{base, derived}`（`:126-127`），`EntityDataPatcher.lookup()`（`:292-320`）只认 `base` / `derived`。真正的问题在**报错混乱**：`reportUnknownKeys`（`:250-276`）两处放行 `DataKeys.isConfigurable(...)`（`:262`、`:272`）→ 写 `"criticalRate"`（裸键或 `base` 里）都会被「认识」，于是既不报错也不生效（= D1）；写 `"temporary": {...}` 这个**块**则会因为不是 `BARE_SECTIONS` 成员而被报成「未知键（这个键不属于实体数据）」 | 文档与外宣（`DataKeys` 类 javadoc `:30`：「`Temporary`：一次性的战斗加成。**能配置**」）与实现**互相矛盾**。用户按文档写就是白写，而且**写错块名时得到的是一句误导性的报错** |
| **D3** | ★★ | **`EntityDataPatcher.recalculateDerivedStats` 把三围公式又写了一遍，而且没走规则表** | `:231-233` 硬编码 `+ 200` / `110 +` / `+ 200`。而 `RuleDefaults.FORMULA_HP_BASE = 200L`（`:28`）、`FORMULA_DEFENCE_BASE = 200L`（`:32`）、`FORMULA_ATTACK_BASE = 110L`（`:36`）已经是「唯一真相」。同一文件 `:236` 调 `target.initialMana()`（走规则表 `mana.mainBase/otherBase`）—— **同一个方法里，三个常量走字面量、两个常量走规则表** | 用户改 `GameRules.json` 的 `formula.hpBase` 之后，**只写 `hpGrow` 的实体**会被这条重算覆盖成一个用旧公式算的值：改规则表在这个路径上失效。这是「同一件事两处写真值」造成的**行为不一致**，不只是可读性问题 |
| **D4** | ★★ | **`DEFAULT_TAGS_CONFIG` 与 `TagConfig.json` 是同一份内容的两个副本** | `ConfigLoader.java:87-127` vs `config/gameConfig/TagConfig.json`（41 行，逐行比对一致） | 删掉 `TagConfig.json` 时 `setDefaultConfig()`（`:625-633`）会**用 Java 里那份覆盖**，用户对 `TagConfig.json` 的改动**静默丢失**（这正是 §1.4 那条） |
| **D5** | ★★★ | **用户手上的三份 JSON 是空的（生成器没起作用）** | `config/gameConfig/EntityData.json` = `{"version":1,"entities":{}}`（4 行）；`SkillData.json` = `{"version":1,"skills":{}}`（4 行）；`EntityData.default.json` = `{"version":1,"entities":{},"skills（…）":{}}`（5 行）。而 `GameRules.json`（42 行）**是全量的**。三份空文件的 mtime 都是 `2026-10-03 13:30:34`，`GameRules.json` 是 `13:34:26`；`ConfigLoader.java` 的 mtime 是 `13:47:05`（**比它们都晚** → 之后没再启动过游戏，`config/data/` 至今不存在，也印证了这点） | 用户按 `README.md:95` 的「删掉整个文件 = 下次启动会重新生成一份全量默认值」去删，**大概率拿到的是又一份空文件** → 「出厂值参考副本」这个阶段 0 的核心交付物**对用户为零**。★ 归因**不确定**（见 §8），但**状态本身是确定且可复现的**：现在这三份文件不会被覆盖（`writeEntityData` `:239-245` 有 `file.exists()` 早退），所以**空文件会一直空下去** |
| **D6** | ★★ | **`MODDING-GUIDE.md` 里 `common` 段的官方示例，有一半字段会被静默忽略** | `ConfigLoader.loadModData`（`:529-576`）对 `common` 段**只调 `EntityDataPatcher.apply(common, …)`**（`:554`）。而 `MODDING-GUIDE.md:395-405`（§4.7 示例）与 `:464-472`（「官方数值也能改」）都写了 `"skills": {…}` 与 `"flameReaver": {…}`。`EntityDataPatcher.apply`（`:84-119`）第一件事就是 `root.optJSONObject("entities")` → 没有 `entities` 层就 `report.error("缺少「entities」这一层…")` 然后 `return`（`:90-98`） | 用户按文档在 `config/data/<modid>.json` 的 `common` 里改技能倍率 / BOSS 旋钮 → **什么都不发生**，只看到一句关于 `entities` 的报错（而且那句还指错了原因）。`MODDING-GUIDE.md:615` 自己写了「`common` 只打实体补丁，改不了 `GameRules`」——**文档前后不一致**，`:430` 与 `:462-469` 是错的 |
| **D7** | ★ | **`SkillDataPatcher.isKnown()` 是手写或链，与 `patch()` 是两张平行表** | `patch()` `:227-238` 处理 8 项；`isKnown()` `:396-404` 另写 7 个 `X.equals(name)`（**它有 7 个、`patch` 有 8 个，差的是两个块名 `consumedMana` / `tags`，由 `reportUnknownKeys` `:382-385` 单独放行**） | 将来加一个技能键，漏改 `isKnown()` → 用户写了它得到「未知键」（假报错）；漏改 `patch()` → 静默失效。**两条路都会踩，而且都没有编译期保护** |

**D1 补充：那 14 个键的「生命周期」并不一样 —— 接上之前必须先分类**

我按「谁是写入点、`copy()` 带不带、`whenFightEnds()` 清不清」逐个查了（行号已核对）：

| 类 | 键 | 住在哪 | `copy()` | 战斗结束 | 接上配置的语义 |
|---|---|---|---|---|---|
| **面板属性**（5 个） | `criticalRate`（`LivingThing.java:79` 字段 / `:1427` setter）、`criticalDMG`（`AttributeProfile.java:151` / `LivingThing.java:2170` setter）、`enhance`（`AttributeProfile.java:146`）、`penetration`（`:142`）、`defenseLoss` | `LivingThing` + `AttributeProfile` | **带**（`LivingThing.java:167` 复制 `criticalRate`；`:150` 注释说明组件里"面板属性那一半"由 `copyFrom` 带） | **不清**（`clearTemporaryAttributes()` `:2005-2023`，`:1998-2001` 明确写了"故意不在表里"） | ✅ **干净**：配在模板上 → 选人列表看得见 → 整局都生效 |
| **临时属性**（10 个） | 五元素 `*Penetration` + 五元素 `*DamageEnhance`（`AttributeProfile.java:64-105`） | `AttributeProfile` | **不带** | **清零**（`AttributeProfile.resetTemporary()` `:190-201` ← `LivingThing.clearTemporaryAttributes()` `:2020`） | ⚠ **配了只影响"开局那一刻"**：第一局结束就被清空。`AttributeProfile.java:15-16` 就是这么定义的 |
| **死字段**（1 个） | `extraDamage` | `LivingThing` | 不带 | 清零（`:2022`） | ❌ **别接**：全项目**零写入点**（`LivingThing.java:1993-1995` 自己写着"死字段"；活的那套是 `Skill#extraDamage`，另一个字段） |

> 这条分类**推翻了一句很流行的说法**：`criticalRate` / `criticalDMG` / `enhance` / `penetration`
> **不是**"临时属性"（`ENTITY-ATTRIBUTE-SPLIT-2026-10.md` 阶段 0 那份 24 个临时属性清单是
> 2026-10-03 搬迁**之前**的状态；`penetration` / `enhance` / `criticalDMG` 已经并进
> `AttributeProfile` 的"面板属性"那一半了）。**以源码为准**（`LivingThing.java:1998-2001`）。

> **另外两条「不算缺陷但值得记」的**：
>
> - `DataKeys.GROUP_DERIVED`（`:115`）**全项目只有声明处 1 次命中** —— `buildGroups()` `:832` 用的是字面量 `"derived"`。同族的 `GROUP_BASE`（`:111`）被用了 3 次。这是「同一个文件里一半用常量一半用字面量」的实例。
> - `ConfigLoader.getEntityDataFile()` / `getSkillDataFile()` / `getGameRulesFile()`（`:177/:186/:195`）与 `getTagsMap()`（`:168`）**全项目（含自测）零调用点** —— 前三个的 javadoc 写着「供自测与**将来的模组接口**使用」，那个接口没做成。

### 1.6 那 655 条自测在守什么、漏了什么

| 已有的守卫 | 位置 | 它能抓到什么 |
|---|---|---|
| `META` 与 `GROUPS` 键集合一致 | `TestCommandSystem.java:2787-2793` | 两张平行表**互相对不上** |
| 每个 `META` 键都能在 `LivingThing`（含 `@DataFlatten` 组件）找到同类型字段 | `:2816-2819` → `DataKeys.auditAgainst/auditNotes` | `/data` 改名（契约破裂） |
| 生成器写 27 键 ⇒ 每个都在 `META` 里、都在 `/data` 里 | `:2855-2874` | **生成器**多写/写歪 |
| dump 出来的键必须「能配置」或「在 `READ_ONLY` 里」 | `:2896-2914` | 新增属性忘了登记 |
| 全量默认值打回去，数值逐字段不变 | `:3013-3030` | **生成器与补丁器对不上**（漏读的键会在这里红） |
| 只改一个键 → 只应用一项、别的字段不动 | `:3036-3045` | 补丁器乱写 |

**漏了什么（这是 D1 能活到今天的原因）**：

> **反向的断言不存在** —— 没有任何一条测试说
> 「`DataKeys.META` 里声明『能配置』的**每一个**键，都必须真的能被补丁器应用」。
> 现有的第 3 条（`:2855-2874`）是「**生成器写出来的** ⊆ `META`」，方向**恰好是反的**：
> 生成器不写 `criticalRate`，所以那个方向永远绿。
> 而且第 5 条（全量默认值打回去）也抓不到 —— 因为生成器**根本不写**那 14 个键，
> 「打回去」自然什么都不变。**两条最像的守卫，恰好都绕开了这个洞。**

### 1.7 现状小结：一张图

```
                    ┌──────────────────────────────────────────┐
                    │  同一个键空间，被手写了 4~5 遍            │
                    └──────────────────────────────────────────┘

  实体键（base 12 + 五行 10 + derived 4 + temporary 14 + 块 2 ≈ 41 个可配置）
      DataKeys.Base / Derived / Temporary     ← 常量 + javadoc      （第 1 遍）
      DataKeys.buildGroups()                  ← 手写 41 条          （第 2 遍）
      DataKeys.buildMeta()                    ← 手写 41 条          （第 3 遍）
      ConfigDefaultWriter.collectXxxValues()  ← 手写 27 个 put       （第 4 遍·写）
      EntityDataPatcher.patch()/applyDerived()← 手写 27 处 applyXxx   （第 5 遍·读）

  技能键（8 + 1 动态）
      DataKeys.SkillKeys                      （第 1 遍）
      ConfigDefaultWriter.collectSkillPatches （第 2 遍，用常量）
      ConfigDefaultWriter.collectSkillValues  （第 2 遍，**用字面量**）← 同一文件两种写法
      SkillDataPatcher.patch()                （第 3 遍）
      SkillDataPatcher.isKnown()              （第 4 遍，**或链**）

  规则键（29）
      DataKeys.Rule.*                         （第 1 遍）
      RuleDefaults 常量 + build()              （第 2、3 遍）
      GameRules.knownKeys()                    （第 4 遍）
      GameRulesPatcher.SECTIONS                （第 5 遍，段名）
      ConfigDefaultWriter.gameRulesJson()      （遍历 allKeys，**唯一没手写的一处** ✅）

  对齐方式：自测（TestCommandSystem 里 100 条左右），也就是「事后对齐」，不是「结构保证」
```

---

## 二、答案：新增一个可配置键，现在要改几处

### 2.1 口径先定死

- **「处」= 需要手工编辑的位置**（一个常量声明算 1 处、一次 `map.put` 算 1 处、一次 `map.put` + 5 个 `switch` case 算 6 处）。
- **不含** `TestCommandSystem.java`（自测是安全网，不是交付物；但它**也要改**，见 2.4 末）。
- 分三种情形，因为改动量差别很大。

### 2.2 情形 A：让**已经有的**字段变成可配置（例如 `criticalRate`）

| 序 | 文件 | 要改什么 | 处数 |
|---|---|---|---|
| 1 | `EntityDataPatcher.java` | `patch()` / `applyDerived()` 里加一行 `applyDouble(target, lookup(patch, DataKeys.Temporary.CRITICAL_RATE, id, report), BASE_PREFIX, id, report, target::setCriticalRate);` | **1** |
| 2 | `ConfigDefaultWriter.java` | `collectBaseValues()` 里加一行 `put(values, DataKeys.Temporary.CRITICAL_RATE, living.getCriticalRate());` | **1** |
| | | **合计** | **2 处 / 2 个文件** |

> 也就是说：`DataKeys` 那一大堆定义**根本不用动**（键、META、GROUPS 都已经写好了）。
> **D1 的 14 个键，每个都是这个形状 —— 只要 2 行就能救活。**

### 2.3 情形 B：加**全新的**实体字段（例如 `criticalDMGLimit`）

| 序 | 文件 | 要改什么 | 处数 |
|---|---|---|---|
| 1 | `LivingThing.java` | ① 加字段 ② 加 getter ③ 加 setter（④ 若是临时属性还要进 `clearTemporaryAttributes()`） | **4** |
| 2 | `DataKeys.java` | ① 加 `Base.XXX` 常量 ② `buildGroups()` 加一行 ③ `buildMeta()` 加一行 | **3** |
| 3 | `ConfigDefaultWriter.java` | `collectBaseValues()` 加一行 `put(...)` | **1** |
| 4 | `EntityDataPatcher.java` | `patch()` 加一行 `applyXxx(...)` | **1** |
| 5 | `EntityDataPatcher.java` | 若该字段影响派生值，还要改 `touchesDerivedStats()`（`:342-353`） | **0~1** |
| 6 | 五行类字段另加 | 5 个 `switch` case × 4 个 `switch`（`resistanceSetter` / `setManaGrow` / `manaGrowOf` / `Snapshot.setResistance`）+ 生成器 2 个 `switch` | **+18** |
| | | **合计（普通字段）** | **9~10 处 / 4 个文件** |
| | | **合计（五行类字段）** | **27~28 处 / 5 个文件** |
| 7 | `TestCommandSystem.java` | ① `SUBCLASS_STATE_KEYS` 登记（若在子类上）② 各条断言 | 另有 1~3 处 |

### 2.5 情形 C：加**全新的**规则键（例如 `flameReaver.phaseThreeHpThreshold`）

| 序 | 文件 | 要改什么 | 处数 |
|---|---|---|---|
| 1 | `DataKeys.java` | `Rule.FlameReaver` 加常量 | **1** |
| 2 | `RuleDefaults.java` | ① 加 `public static final` 常量 ② `build()` 加一行 `map.put(...)` | **2** |
| 3 | `GameRules.java` | `knownKeys()` 加一行 `keys.add(...)` | **1** |
| 4 | `GameRulesPatcher.java` | 新段才要改 `SECTIONS`（`:34-35`） | **0~1** |
| 5 | `FlameReaver.java` | 加一个读取方法（例如 `phaseThreeHpThreshold()`）并接线 | **1~2** |
| | | **合计** | **5~7 处 / 4 个文件** |

> **一句话答案（拿去问用户的那个数字）**：
> **让一个「已经声明过」的键真正生效 = 改 2 个文件 2 处；
> 加一个全新的实体字段 = 改 4 个文件 9~10 处（五行类字段 5 个文件 27 处）；
> 加一条全新的规则键 = 改 4 个文件 5~7 处。**
> 而**表驱动之后，这三个数字全部变成「1 个文件 1 行」**（新字段仍要写 Java 字段与 setter，
> 但**只需要在 `KeySpec` 表里加一行**，五行类字段连 `switch` 都不用写）。

---

## 三、优化方案

### 3.1 目标结构（类名 / 职责 / 谁依赖谁）

```
cn.gfhnv.game.system.configLoadingSystem
├── KeySpec.java            【新】一条键的完整声明（record）
│      name / group / type / inData / note
│      + reader（对象 → JSON 值）+ writer（JSON 值 → 对象）   ← 一行一个键，**唯一真相**
├── EntityKeySpecs.java     【新】实体键清单（26 个可配置键 + 2 个块，把现在散在 4 处的清单收成一张表）
├── SkillKeySpecs.java      【新】技能键清单（8 + 1 动态）
├── RuleKeySpecs.java       【新】规则键清单（29，含段名与出厂值）
├── KeyTables.java          【新】三张表的只读视图 + 查表 API（原 DataKeys 的对外入口）
├── DataKeys.java           【保留，瘦身】只剩 JSON 格式常量 + 分组名 + 别名（约 60 行）
├── SpecPatcher.java        【新】通用补丁器：遍历 KeySpec，读值 → 类型校验 → 写 → 记录报告
├── SpecWriter.java         【新】通用生成器：遍历 KeySpec，读对象 → 排版写 JSON
├── PatchReport.java        【新】把三个同构的 Report 合成一个（唯一实现）
├── JsonText.java           【新/搬】把 ConfigDefaultWriter 里那 150 行 JSON 排版器搬出来
├── ConfigLoader.java       【保留】读文件 / 定位 / 时机 / 模组入口（薄壳不变）
├── GameRules.java          【保留】静态规则表（knownKeys 改为查 RuleKeySpecs）
├── RuleDefaults.java       【合并】出厂值搬进 RuleKeySpecs，本类删除
├── EntityDataPatcher.java  【瘦身】只剩「定位 + 顺序 + 快照」，逐键代码全删
├── SkillDataPatcher.java   【瘦身】同上（`isKnown()` 直接查表）
└── GameRulesPatcher.java   【瘦身】段名查表
```

**依赖方向（单向，无环）**：

```
KeySpec / *KeySpecs         ← 不依赖本项目任何类（除了 ElementSort、LivingThing 的类型引用）
        ↑
   KeyTables / DataKeys     ← 依赖 KeySpec（契约层）
        ↑
  SpecPatcher / SpecWriter  ← 依赖 KeyTables + LivingThing / Skill / GameRules（机制层）
        ↑
 EntityDataPatcher / SkillDataPatcher / GameRulesPatcher / ConfigLoader（编排层：定位、时机、报错）
        ↑
      GameMain / GameStartEventListener（调用点，**一行都不用改**）
```

**关键：`/data` 侧一个字节都不动。** `DataBridge`、`@DataFlatten`、`@DataField`、`@NoData`
全部不碰 —— 契约表（`KeySpec.name`）只是**把 `/data` 已经有的名字抄一份到一张可查的表里**，
并用阶段 0 的守卫自测钉住「两边一致」。**这一步不引入第二套真值**，因为
`KeySpec.name` 与 Java 字段名的关系由 `DataKeys.auditAgainst()` 那套反射核对
（`:737-766`）来保证 —— 它已经在跑了。

### 3.2 核心：`KeySpec` 与「表驱动」

**示意代码（不是完整实现，只给形状）**：

```java
/**
 * 一条可配置键的完整声明：名字、分组、类型、怎么读、怎么写。
 * <p>
 * 一张表驱动三件事：生成默认文件、打补丁、报「未知键」。
 * 加一个可配置键 = 在这张表里加一行（外加字段自己的 getter/setter）。
 */
public record KeySpec<T>(
        String name,              // 键名 = /data 数据名（对外契约，改名会让用户配置静默失效）
        String group,             // 分组，直接取 DataKeys.GROUP_BASE / GROUP_DERIVED / GROUP_TEMPORARY
        Kind kind,                // 值的种类，见下面的 enum
        Function<T, Object> read, // 对象 → 值（生成默认文件用；返回 null = 这个键不写出去）
        BiConsumer<T, Object> write, // 值 → 对象（打补丁用；值已由 kind 校验过）
        String note) {            // 一句话说明（就是现在 META 里的 note）

    /**
     * 值的种类：决定 JSON 里怎么序列化、补丁时怎么校验。
     * <p>
     * 前六种是标量；后三种是「有语义的块」—— 块**不该**硬塞进标量表，
     * 它们各自需要一个专门的 apply 分支（现在就是这么写的，保留）。
     */
    public enum Kind { STRING, LONG, DOUBLE, BOOLEAN, ELEMENT, MANA_BLOCK, INVENTORY, TAGS }
}
```

**实体键那张表长这样**（示意，省略部分行）：

```java
/** 实体键的唯一定义处：加一个可配置键 = 在这里加一行。 */
static final List<KeySpec<LivingThing>> ENTITY = List.of(
    spec(Base.NAME,  BASE, STRING, LivingThing::getName,  LivingThing::setName,  "名称（影响 @e[name=…]）"),
    spec(Base.LEVEL, BASE, LONG,   LivingThing::getLevel, LivingThing::setLevel, "等级；会触发整组派生值重算"),
    spec(Base.MASS,  BASE, DOUBLE, LivingThing::getMass,  LivingThing::setMass,  "质量（物理属性）"),
    // ……
    spec(Derived.HP_MAX, DERIVED, LONG, LivingThing::getHpMax, LivingThing::setHpMax, "生命上限（覆盖公式结果）"),
    spec(Temporary.CRITICAL_RATE, TEMPORARY, DOUBLE,
         LivingThing::getCriticalRate, LivingThing::setCriticalRate, "基础暴击率"),
    // 五行：用 ELEMENTS 循环生成，不再手写 5 个 switch ×4 处
    ...ELEMENTS.stream().map(e -> spec(e + "Resistance", BASE, DOUBLE,
         lt -> resistanceOf(lt, e), (lt, v) -> setResistance(lt, e, v), e + " 抗性")).toList());
```

**通用补丁器**（`SpecPatcher`，示意）：

```java
/**
 * 按表打补丁：一条键一个 KeySpec，读值 → 校验类型 → 写 → 记账。
 * <p>
 * 「未知键」「类型不对」「目标不存在」全部只跳过那一项并记账，结尾汇总 —— 一个坏键不废整份配置。
 * 写不进去的键**必须**在这里就报出来：<b>声明了却没人读的键是这套表最危险的失败模式</b>。
 */
static <T> void patch(T target, JSONObject patch, List<KeySpec<T>> specs, PatchReport report, String id) {
    Set<String> handled = new LinkedHashSet<>();
    for (KeySpec<T> key : specs) {
        // 顺序 = 表里的顺序；调用方按「level → 面板 → derived.hp 最后」把表排好序
        Object raw = lookup(patch, key.name());       // base / derived / 裸键 三种写法
        if (raw == null) continue;
        Object value = key.kind().coerce(raw);        // 类型不对 → null
        if (value == null) { report.skipped(id, key.name(), "类型不对（要 " + key.kind() + "）"); continue; }
        key.write().accept(target, value);
        report.applied(id, key.name(), String.valueOf(value));
        handled.add(key.name());
    }
    // 补丁里出现、但表里没有的键 → 显式点名（这一步现在是手写的 reportUnknownKeys）
    report.unknownKeys(id, patch, handled);
}
```

**通用生成器**（`SpecWriter`，示意）：

```java
/** 按同一张表把对象 dump 成 JSON：与 SpecPatcher 共用键清单，所以「写出去的」与「读得回来的」必然一致。 */
private static void writeEntity(JsonText json, LivingThing living) {
    for (KeySpec<LivingThing> key : EntityKeySpecs.ENTITY) {
        Object value = key.read().apply(living);
        if (value == null && !key.kind().writesNull()) continue;   // 契约：null = 不写这个键
        json.raw(key.name(), json.value(value));
    }
}
```

**这一改法直接消灭 §1.2/§1.3 里的全部重复**：

| 现在重复在哪 | 表驱动之后 |
|---|---|
| `DataKeys.buildGroups()` + `buildMeta()` 两张手写表 | `group` / `note` 就在 `KeySpec` 行里（**从 2 张表变 0 张**） |
| `ConfigDefaultWriter` 的 27 处 `put(...)`（13 + 2×5 + 4） | `SpecWriter` 遍历表（**从 27 处变 0 处**） |
| `EntityDataPatcher` 的 27 处 `applyXxx`/`lookup`（12 + 2×5 + 4 + 1） | `SpecPatcher` 遍历表（**从 27 处变 0 处**）；只有 `inventorySlots` 因为是「块 + 副作用」（删格子要判空格）保留一个专用分支 |
| 4 个五行 `switch` + 2 个生成器 `switch`（各 5 case） | `ELEMENTS.stream().map(...)` 生成（**从 30 个 case 变 0 个**） |
| `SkillDataPatcher.isKnown()` 的 7 行或链 | `specs.stream().anyMatch(...)`（**从 7 行变 1 行**） |
| `GameRules.knownKeys()` 的 29 行 `keys.add` | `RuleKeySpecs.ALL.stream().map(KeySpec::name)`（**从 29 行变 1 行**） |
| `RuleDefaults` 的 29 常量 + 29 行 `map.put` | 出厂值作为 `KeySpec` 的一个字段（`defaultValue()`） |
| `GameRulesPatcher.SECTIONS` 的 5 个字面量 | 从 `RuleKeySpecs` 里 `distinct` 出段名（**从 5 个字面量变 0**） |
| 3 个同构 `Report` 类（155 + 127 + 98 行） | `PatchReport` 一个（约 150 行，**净删约 230 行**） |
| `EntityDataPatcher` 与 `SkillDataPatcher` 里那 24 行完全相同的 `targetsOf/entitiesOf` | `Targets.of(id, report)` 一个（**净删约 40 行**） |

### 3.3 `DataKeys` 到底该怎么切

**切成 3 个（不是 5 个、也不是 10 个）**，切法按**变化原因**，不按主题：

| 新文件 | 装什么 | 变化原因 | 预估行数 |
|---|---|---|---|
| `DataKeys.java`（**瘦身保留**） | ① JSON **格式**常量：`VERSION` / `ENTITIES` / `SKILLS` / `BASE` / `DERIVED` / `MANA_GROW_BLOCK` / `SKILL_SEPARATOR` / `SECTION_SEPARATOR`；② 分组名 `GROUP_BASE`/`GROUP_DERIVED`/`GROUP_TEMPORARY`；③ 别名 `ELEMENT`；④ `ELEMENTS` 五行名单 | **改 JSON 长什么样**（版本升级、加一层结构）才动 | 约 **60** |
| `EntityKeySpecs.java`（新） | 26 个可配置实体键 + 2 个块（`manaGrow` / `inventorySlots`）的 `KeySpec`（含五行用循环生成） | **加/改实体属性**时动 | 约 **150** |
| `RuleKeySpecs.java`（新） | 29 个规则键的 `KeySpec`（含段名 + 出厂值 + 类型） | **加/改魔法数字**时动 | 约 **120** |
| `SkillKeySpecs.java`（新） | 8 个技能键 + `NumericSkillTunable` 动态键的钩子 | **加/改技能旋钮**时动 | 约 **70** |
| **合计** | | | **约 400**（现在 884 + 232 = 1116） |

**`ReadOnly` / `NotInData` 怎么办**（这是「不能直接删」的一部分）：
它们**不是配置键**，是**审计口径**（自测拿它判「dump 出来的新键有没有登记」）。
搬去 `KeyTables`（或干脆搬进自测），**不要**混进 `KeySpec` 表 —— 混进去就会重演
「META 里既有能配置的、又有不能配置的，于是 `isConfigurable` 的语义变模糊」这个坑。

**`auditAgainst` / `auditNotes` / `collectFields`（`:737-817`，81 行）**：
它们是**测试工具**，却住在对外契约类里，还让 `DataKeys` 背上 `LivingThing` / `DataBridge` 的 import。
搬到自测（`TestCommandSystem` 内部或 `debug_tools` 下的一个小类），
契约类就变成**零依赖的纯数据**。

### 3.4 谁依赖谁（改造后的调用链，一眼看完）

```
GameMain.gameInitialize()
  :65 loadGameRules()        → ConfigLoader.loadGameRules()   → GameRulesPatcher（段名查表）
                                                              → GameRules.put（键合法性查 RuleKeySpecs）
  :76 loadEntityDataConfig() → ConfigLoader.loadEntityData()  → EntityDataPatcher（定位 + 顺序 + 快照）
                                                              → SpecPatcher（逐键，表驱动）
                                                              → ConfigDefaultWriter → SpecWriter（表驱动）
  :79 loadSkillDataConfig()  → ConfigLoader.loadSkillData()   → SkillDataPatcher → SpecPatcher
  :72 GameStartEvent        → ConfigLoader.loadModData(Mod)   → EntityDataPatcher.apply(common)
```

**唯一新增的一条边是「Patcher → KeySpecs」**，其余依赖方向一字不变 →
`GameMain`、`GameStartEventListener`、`mods/`、`config/` **全部零改动**。

### 3.5 行数估算（以及估算依据）

**依据**：逐段数了「这段代码在干什么」，把「表驱动后会被删掉的」与「必须留下的」分开。

| 文件 | 现在 | 表驱动后 | 省在哪 |
|---|---|---|---|
| `ConfigDefaultWriter.java` | **869** | **约 320** | `collectBaseValues`(33) + `collectDerivedValues`(19) + `collectSkillValues`(39) + `collectSkillPatches`(32) + `skillPatchOf`(32) + `resistanceOf`/`manaGrowOf`(24) ≈ **180 行逐键采集 → 0**；`Json` 排版器 150 行 → 搬到 `JsonText`（不计入本类）；留下的是 4 个产物入口 + 落盘 + 契约输出 |
| `EntityDataPatcher.java` | **1132** | **约 520** | `patch()`(29) + `applyDerived`(10) + 五行 4 个 switch(约 45) + `applyElementValues`(26) + `reportUnknownKeys`(27) + `lookup`(29) ≈ **166 行 → 通用器里的一份**；`Report` 155 → 共用 `PatchReport`（本类不再计）；`Snapshot` 92 **保留**；定位 39 **保留**；类型判定 44 → `KeySpec.Kind` |
| `SkillDataPatcher.java` | **865** | **约 420** | `patch()`(12) + `isKnown`(9) + `reportUnknownKeys`(11) → 通用器；`entitiesOf`(36，与实体那份重复) → 共用；`Report` 127 → 共用；`SkillSnapshot` 47 **保留**；三个特殊块（mana / tags / extraNumeric，约 90）**保留**（它们是「有语义的写法」，不该硬塞进一张标量表） |
| `DataKeys.java` | **884** | **约 60** | 41 个键常量 + 2 张手写表（各 41 条）+ 审计工具全部搬走 |
| `RuleDefaults.java` | **232** | **0（删除）** | 出厂值变成 `RuleKeySpecs` 里每行的 `defaultValue` |
| `GameRules.java` | **219** | **约 150** | `knownKeys()` 29 行 → 1 行 |
| `GameRulesPatcher.java` | **226** | **约 130** | `SECTIONS` 5 字面量 → 查表；`Report` 98 → 共用 |
| `ConfigLoader.java` | **749** | **约 640** | 三个同构 `loadXxx` 收成一个泛型入口（省约 60）；`TagConfig` 那段**不动**（见 §6 不做的事） |
| **新文件** | — | **约 700** | `KeySpec` 80 + `EntityKeySpecs` 150 + `SkillKeySpecs` 70 + `RuleKeySpecs` 120 + `KeyTables` 80 + `SpecPatcher` 100 + `SpecWriter` 60 + `PatchReport` 150 + `JsonText` 150（搬来的） |
| **子系统合计** | **≈5176** | **≈3720** | **净减约 1450 行（−28%）**，而且「加一个键」从 5 文件 12 处变成 1 文件 1 行 |

> **诚实说明**：这些是**估算**，不是原型实测（本项目不允许我改 `src/`，我也**没能编译/跑自测**，
> 见 §8）。估算误差估计 ±20%。真正的收益不在行数，在 §2 那个数字：
> **「新增一个可配置键」的改动点从 9~10 处降到 1 处，从 4 个文件降到 1 个文件。**

---

## 四、硬约束

### 4.1 用户已经在用真文件了 —— 逐份说清「会怎样」

**2026-10-03 实测的 `config/` 目录状态**：

| 文件 | 行数 | 内容 | 现状 |
|---|---|---|---|
| `config/gameConfig/TagConfig.json` | 41 | 6 个 id × AI 权重 | mtime `2026-08-05`，**用户在用的老文件**（虽然对玩法零影响，见 `EXTERNAL-DATA-LOADING-2026-10.md` §1.4 D1） |
| `config/gameConfig/PropertyConfig.json` | 5 | `{"game_official_content:insectBoss": {}}` | mtime `2026-07-29`、**全项目零读取**（已废止） |
| `config/gameConfig/EntityData.json` | **4** | `{"version":1,"entities":{}}` | mtime `2026-10-03 13:30:34`，**空**（D5） |
| `config/gameConfig/SkillData.json` | **4** | `{"version":1,"skills":{}}` | 同上，**空** |
| `config/gameConfig/EntityData.default.json` | **5** | 三段全空 | 同上，**空** |
| `config/gameConfig/GameRules.json` | **42** | 29 条规则键全量 | mtime `2026-10-03 13:34:26`，**是全的** |
| `config/data/` | — | **目录不存在** | 阶段 4 之后还没启动过游戏（`mods/liXiaoYanPlus` 已就位，但 `config/data/liXiaoYanPlus.json` 从没被生成过 —— 游戏本来也不写它） |

**结论（对本次方案）**：

1. **不改任何键名 / 不改 schema ⇒ 用户已有的 6 份文件一个字都不用动**
   （`TagConfig` 41 行是唯一真的在生效的老文件；`GameRules.json` 42 行是新的、也在生效；
   其余三份是空的）。
   本文推荐的方案是**纯内部重构**，`EntityData.json` / `SkillData.json` / `GameRules.json`
   的**键名、层级、语义全部冻结**（`version` 保持 `1`，不加新版本号 —— 加了反而要处理迁移）。
2. **`GameRules.json` 是 42 行全量、且用户可能已经改过**：它里面的 29 个键
   必须**逐个字节地继续被读**。表驱动之后 `RuleKeySpecs` 的键名必须与现在
   `GameRules.knownKeys()` 的 29 个**完全一致** —— 阶段 1 的验收要专门打一遍这份真文件。
3. **那三份空文件的历史包袱**：因为 `ConfigDefaultWriter.writeEntityData` 有 `file.exists()` 早退（`:239-245`：`if (file.exists()) return false;`），
   空文件**不会自己变好**。方案里**不主动删/不主动写**用户文件（沿用 `ConfigLoader` 现有纪律）；
   但阶段 0/1 的文档与「需要用户拍板」（Q1）要明确告诉用户：
   **要拿到全量默认值参考，必须自己删掉那三份文件再启动一次。**
4. **`PropertyConfig.json` 不动**（`EXTERNAL-DATA-LOADING-2026-10.md` §3.2 已定：废止、不删、不读）。

### 4.2 对外契约清单（碰了就出事）

| # | 契约 | 依据 | 本方案怎么处理 |
|---|---|---|---|
| 1 | **`/data` 数据名**（`criticalRate` / `alive` / `effects` / `hpGrow` / `attackGrow` / `defenceGrow` / 五行 `…ManaGrow` / `…Resistance` …） | `notes_for_llm/70-DATA.md:24-25`「数据名是对外 API……改名等于破坏 `/data` 脚本与将来的存档」；`ENTITY-ATTRIBUTE-SPLIT-2026-10.md:151-154` | **一个都不改**。`KeySpec.name` 抄的就是数据名，且由 `auditAgainst` 反射核对钉住 |
| 2 | **`EntityData.json` / `SkillData.json` / `GameRules.json` 的键名与层级** | `README.md:80-86`、`MODDING-GUIDE.md:462`、`70-DATA.md:42-47`；**用户手上已有真文件** | **冻结**。`spec(Base.MANA_GROW, …)` 这类块名、`base`/`derived` 两种写法（子块优先）、裸键写法**全部保留** |
| 3 | **13 参构造器签名与参数顺序** | `MODDING-GUIDE.md:163-167` / `:451`；`mods/drunkenSword/.../DrunkenSwordsman.java:94` | **不碰**。自测已有硬断言（`TestCommandSystem.java:2957-2996`） |
| 4 | **`setXxx` 名字**（`setHpGrow` / `setAttackGrow` / `setDefenceGrow` / `setSpeed` / `setLevel` …） | 同上；且 `/data` 写回是 `"set"+字段名` 拼的（`70-DATA.md:49-55`） | **不改**。`KeySpec.write` 直接引用这些 setter |
| 5 | **模组侧 `@ModConfig` / `ModDataAware` / `config/data/<modid>.json`** | `MODDING-GUIDE.md` §4.7（`:357-475`）；`ModConfig` `:53` / `ModDataAware` `:30` / `ModConfigDocument` `:254` | **签名不改**。`resolveModConfigId` 的「注解 > MOD_ID」口径不改。⚠ `common` 段那两条**假承诺**（D6）要么修实现、要么改文档 —— **见 Q3** |
| 6 | **模组不能自带配置文件** | `MODDING-GUIDE.md:428` 规则 1 | 不碰 |
| 7 | **655 条自测是唯一安全网** | `notes_for_llm/90-STATE.md:17`；⚠ **本轮我没能实跑**（沙箱拒绝 `java`/`powershell`，见 §8） | 任何一步都不许让基线下降；**新增**的守卫要计入基线 |
| 8 | **不引入第三方库**（唯一依赖 `org.json`） | `10-RULES.md` §8；`PROJECT-ANALYSIS-2026-09.md` §7.3 | 表驱动只用 `java.util.function`（JDK 自带） |
| 9 | **配置加载失败不许让游戏起不来** | `EXTERNAL-DATA-LOADING-2026-10.md` §8.1 第 8 条 | 通用补丁器**必须**继承「逐项容错 + 结尾汇总 + 绝不抛」 |
| 10 | **补丁只打在模板上、每个模板只补一次** | 同上 §2.2；`Report.markBound` `:1003-1009` | 表驱动**不动**这一层（去重表留在编排层的 `Report` 里） |
| 11 | **`DataKeys` 的 `[DUP-FIELD]` 约束**：同一个 `.java` 文件里**不许出现两个同名的常量** | `check-sources.ps1:217-234` 的正则 `^\s*(?:public\|protected\|private)\s+…([a-z]\w*)`（**小写开头**才匹配）；`EXTERNAL-DATA-LOADING-2026-10.md` 阶段 3 坑 7（`Temporary.TAGS` → `WEIGHT_TAGS`） | 把键拆成多个文件**反而松绑**了这条约束（不同文件不冲突）。⚠ 但**表驱动后**同一个文件里会有大量 `spec(...)` 调用，**不再有常量重复问题** |

### 4.3 ⚠️ 那条坑：`Get-Content` 在中文 Windows 上少算行数

所有行号/计数一律用 `[System.IO.File]::ReadAllLines(...)`（.NET 默认 UTF-8）
或 `[System.IO.File]::ReadAllText(...)` + 正则，**没用 `Get-Content`**
（`ENTITY-ATTRIBUTE-SPLIT-2026-10.md` 附录 A 记着：`TestCommandSystem.java` 曾被数成 2547 行，真实 2805）。
本文所有数字的复现命令见附录 A。

---

## 五、分阶段路线（每步自洽、可单独停、不留半成品）

**统一验收顺序**（每一步都要走完这四格）：

```powershell
# ① 静态自查（快）
powershell -ExecutionPolicy Bypass -File .\check-sources.ps1          # 期望：Java files: 215+ / CHECK OK
# ② 全量编译 + 自测（权威）
powershell -ExecutionPolicy Bypass -File .\test-command-system.ps1   # 期望：通过 ≥655 条，失败 0 条
# ③ 用户进游戏：选人列表 → 打一局 → 看手感/数值
# ④ 回写文档（第 0 条闭环）：70-DATA.md / 90-STATE.md / EXTERNAL-DATA-LOADING-2026-10.md 的对应节
```

> ⚠️ **本次会话里 `java.exe` / `javac.exe` / `powershell.exe` 全部被沙箱拒绝**
> （`Access is denied`，见 §8），所以 ①② 我没能跑。上面的期望值是引用
> `90-STATE.md:17` 的 `655/0` 与 `EXTERNAL-DATA-LOADING-2026-10.md` 附录 A 的 `Java files: 214`
> （本轮实测 `src` 已是 **215**，多的是 `IModifyIgnitionMax.java`，见 §1.1）。

---

### 阶段 0（半天，**零风险**，立刻有收益）：给「键清单」上锁 —— 让 D1 那 14 个假键当场变红

> **为什么第一步是它**：它**一行运行期代码都不改**（只加自测 + 只加一个只读查询方法），
> 但它第一次把「声明」与「实现」钉在一起 —— 之后任何一步重构都有安全网。
> 这正是 `ENTITY-ATTRIBUTE-SPLIT-2026-10.md` 阶段 0 的设计思路（那份文档的阶段 0 也是「不拆东西，先给属性清单上锁」）。

**做什么**

1. `DataKeys` 新增一个**只读**查询（约 10 行）：
   `public static Map<String, KeyMeta> entityKeysAppliedBy(...)` —— 或者更简单：
   在 `DataKeys` 里加一张 `APPLIED_KEYS`（补丁器真的会读的键清单），由 `EntityDataPatcher` /
   `SkillDataPatcher` **主动声明**（各加一个 `public static Set<String> appliedKeys()`）。
   ⚠ **声明必须是诚实的**：`manaGrow` 块要登记成 **5 个扁平键**（`metalManaGrow` … `dirtManaGrow`），
   而不是块名 —— 否则「声明 ⊆ 实现」又会变成一句空话。实测口径：
   `EntityDataPatcher.appliedKeys()` 应当是
   `{name, level, mass, type, description, speed, elementSort, hpGrow, attackGrow, defenceGrow}`（10）+
   `{metal…dirt}Resistance`（5）+ `{metal…dirt}ManaGrow`（5）+ `{hpMax, hp, attack, defence}`（4）+
   `{inventorySlots}`（1）= **25 个键 + `manaGrow` 块名 1 个**。
2. `TestCommandSystem#testConfigDefaultsAndPatch` 追加 **3 条**断言：
   - **★ 核心那条**：「`DataKeys.META` 里 `group` 是 `base`/`derived` 的每个键，都必须在
     `EntityDataPatcher.appliedKeys()` 里（`manaGrow` 两种口径都要认）」→ 一加就红，
     **当场把 D1/D2 变成看得见的 TODO**；
   - 「`group` 是 `temporary` 的 14 个键必须在 `appliedKeys()` 里，**或者**必须写进一份
     显式的 `NOT_APPLIED_ON_PURPOSE` 白名单（每条给一句理由）」→ 逼人做选择，而不是默认忽略；
   - 「`SkillDataPatcher.isKnown()` 认识的键 == `SkillDataPatcher.patch()` 处理的键」→ 堵 D7。
3. **不加任何运行期行为**：`EntityDataPatcher` 仍然不读那 14 个键（**这是刻意的** ——
   让「14 个键」以白名单 + 理由的形式显式存在，而不是偷偷修好）。

**不做什么**：不动任何键名、不改 JSON、不重构任何类、不引入 `KeySpec`。

**验收**：`check-sources.ps1` → 编译 → 自测（**655 → 约 658**，失败 0 条）→
用户进游戏打一局（确认行为零变化）。

**能停在这里吗**：能。停下 = 项目多了一张「哪些键是假的」的显式清单 + 三条护栏，
**而且 D1 从此不会再扩大**（以后新增声明没接线就会红）。

---

### 阶段 1（1 天，低风险）：把「出厂值」收成一处 —— 消灭 D3、D4

**做什么**

1. **D3**：`EntityDataPatcher.recalculateDerivedStats()`（`:229-237`）里的 `200` / `110` /
   `200` 改成读规则表（`GameRules.getLong(DataKeys.Rule.Formula.HP_BASE)` 等）——
   **语义与现在完全一致**（`RuleDefaults` 里的值就是这三个字面量），只是让「改 `GameRules.json`
   的 `formula.hpBase` 之后、只写 `hpGrow` 的实体」也跟着变。
2. **D4**：`ConfigLoader.DEFAULT_TAGS_CONFIG`（`:87-127`）改成**从 `config/gameConfig/TagConfig.json` 的
   参考副本读**，或把它移到 `config/gameConfig/TagConfig.default.json` 只留一份
   （**二选一，见 Q2**）。⚠ **不能**改变「文件缺失时自动生成」这个行为（`README.md` 的承诺）。
3. 顺手把 `DataKeys.GROUP_DERIVED` 那个死常量用起来（`buildGroups` `:832` 的字面量 `"derived"` → 常量），
   或删掉它 —— **二选一**，别留着。

**不做什么**：不动 `KeySpec`、不动 `DataKeys` 的结构、不改任何键名。

**验收**：`check-sources.ps1` → 编译 → 自测（**约 658 → 约 662**，新增 4 条：
「重算走规则表」「改 `formula.hpBase` 后 `hpGrow` 路径跟着变」「删 `TagConfig.json` 后生成的默认值与
参考副本逐字节一致」「`buildGroups` 不再有裸字面量」）→
**用户进游戏：把 `GameRules.json` 的 `formula.hpBase` 改成 `400`，选人列表里所有人血量变化**
（这一步能顺手证明 D3 真的修好了）→ 改回去。

**能停在这里吗**：能。停下 = 两处「两写真值」消失，行为可验证。

---

### 阶段 2（2~3 天，中风险，**收益最大**）：引入 `KeySpec` 表 + 通用补丁器/生成器，**先只覆盖实体键**

**做什么**

1. 新增 `KeySpec`（record）+ `EntityKeySpecs`（26 个可配置实体键 + `manaGrow` 块 + `inventorySlots` 的表）
   + `SpecPatcher` + `SpecWriter` + `PatchReport`；
2. `ConfigDefaultWriter.collectBaseValues/collectDerivedValues`（`:415-472`）**改成遍历表**
   （删掉约 70 行 = `collectBaseValues` 33 + `collectDerivedValues` 19 + `resistanceOf` 9 + `manaGrowOf` 9）；
3. `EntityDataPatcher.patch/applyDerived/applyElementValues/reportUnknownKeys/lookup`
   **改成遍历表**（删掉约 160 行 = 29 + 10 + 26 + 27 + 29 + 四个 `switch` 37）；
4. `DataKeys.META` / `GROUPS` **改成从表派生**（`buildMeta`/`buildGroups` 那 100 行删掉）；
5. **那 14 个 `Temporary` 键此时顺手接上（各 1 行 `spec(...)`）** —— 阶段 0 的白名单清零。
   ⚠ **接哪些、按什么语义接，以 Q1 的拍板为准**（`criticalRate` / `criticalDMG` / `enhance` /
   `penetration` / `defenseLoss` 是**面板属性**：`copy()` 带、战斗结束不清，配了就整局生效；
   五元素 `*Penetration` / `*DamageEnhance` 是**临时属性**：第一局结束就被
   `AttributeProfile.resetTemporary()` 清零；`extraDamage` 是**死字段**）。
   **每一条的 `note` 里都要写清它属于哪一类**，否则又是「改了没反应」。

**不做什么**：**不碰技能、不碰规则、不碰 `TagConfig`**（各留一个阶段）。
`EntityDataPatcher.Snapshot`（92 行）与 `targetsOf`（39 行）**原样保留**（它们已经被自测依赖）。

**验收**

| 检查 | 怎么验 |
|---|---|
| 键名/JSON 逐字节不变 | 自测：拿**真** `config/gameConfig/EntityData.json`（现在那份空文件）+ 一份手工全量文件，补丁前后 `stateOf` 逐字符一致 |
| 生成器与补丁器**同一张表** | 自测（已有那条「全量默认值打回去逐字段不变」+ 新增「生成器写出的键集合 == 表的名字集合」） |
| 那 14 个键真的生效 | 自测：逐个写一个非默认值 → 断言字段真的变了（**这条是阶段 0 红→绿的翻转**） |
| 未知键仍然显式点名 | 自测：写 `{"entities":{"…":{"base":{"nope":1}}}}` → `skippedEntries` 里有它 |
| 「每个模板只补一次」没退化 | 自测（已有） |
| 五行 switch 删掉后行为不变 | 自测：5 个抗性 + 5 个法力成长逐个改值再核对（**这条现在没有，要新增**） |

**能停在这里吗**：能。停下 = 实体那一族已经完全表驱动，
技能与规则还是老写法（两者互不影响，因为文件/加载器都分开）。

---

### 阶段 3（1~2 天，中风险）：技能键 + 规则键也进表

**做什么**

1. `SkillKeySpecs` + `SkillDataPatcher.patch/isKnown/reportUnknownKeys` 改成遍历表
   （`isKnown` 从 7 行或链变 1 行）；
2. `RuleKeySpecs`（含出厂值）+ `GameRules.knownKeys()` 从表派生 + `RuleDefaults` **删除**；
   `GameRulesPatcher.SECTIONS` 从表 `distinct` 出段名；
3. 三个 `Report` 合并成 `PatchReport`；`EntityDataPatcher` 与 `SkillDataPatcher` 里重复的
   `targetsOf/entitiesOf` 合并成一个 `Targets.of(...)`；
4. `ConfigDefaultWriter` 里那 150 行 `Json` 排版器搬到 `JsonText`（纯搬家）。

**不做什么**：不动 `loadModData` 的语义、不动 `GameRules.freeze()` 的时机。

**验收**：`check-sources.ps1` → 编译 → 自测（**≥ 阶段 2 的基线**，理想情况下因为合并
Report 而**断言文案变了但条数不变**）→ 用户进游戏打一局 + 打一次盗火行者二阶段
（规则键里最容易踩的是 BOSS 旋钮）。

**能停在这里吗**：能。停下 = 三套键空间全部表驱动，`DataKeys` 已经瘦到约 60 行。

---

### 阶段 4（半天）：文档回写 + 模组契约订正

**做什么**

1. 回写 `notes_for_llm/70-DATA.md`（§5.10 的「配置键 = 数据名」那几行，说明现在只有一张表）、
   `90-STATE.md`（新基线数字）、`EXTERNAL-DATA-LOADING-2026-10.md`（追加一节「阶段 5：解耦」）、
   `README.md`（那三份空文件的处理方式）；
2. **订正 D6**：`MODDING-GUIDE.md:395-405` 与 `:464-472` 那两个 `common` 段示例里的
   `skills` / `flameReaver` 必须删掉或改成真实支持的写法（按 Q3 的结论）；
3. `MODDING-GUIDE.md` 的「模组配置」一节补一句：「`common` 段 = `EntityData.json` 的格式」。

**验收**：`check-sources.ps1` → 编译 → 自测 → **用户照着 `MODDING-GUIDE.md` 抄一个
`config/data/<modid>.json`，确认写进去的东西真的生效**（这一步只有用户能做）。

---

## 六、不做的事（与 `10-RULES.md` §9.3 对齐）

| 不做 | 理由 |
|---|---|
| **改 `/data` 的任何键名**（含「顺手订正」） | `70-DATA.md:24-25` 已定「数据名是对外 API」；`ENTITY-ATTRIBUTE-SPLIT-2026-10.md` §3.C 已经把「只改 `/data` 对外视图」评过：**收益最小、破坏最大**。本文不重开这一题 |
| **改 `EntityData.json` / `SkillData.json` / `GameRules.json` 的 schema 或键名** | 用户手上已经有真文件（§4.1）；改 schema 要写迁移层，而本项目**没有存档**这个动力（`ENTITY-ATTRIBUTE-SPLIT-2026-10.md` §4 利好第 4 条） |
| **把三份配置合并成一个文件** | `EXTERNAL-DATA-LOADING-2026-10.md` 第十节 Q4 已定：分开后 diff 噪音小、想改技能就只开一个文件 |
| **热重载（改文件立刻生效）** | 同上 §9：要处理「已复制出去的副本 + 已挂上的效果」，代价远超收益；本项目一直是「重启才生效」（`MODDING-GUIDE.md:56`） |
| **迁移 `TagConfig.json` 进新体系** | `EXTERNAL-DATA-LOADING-2026-10.md` §3.2 已定：现状能用、迁移只有审美收益。**阶段 1 只处理它那份重复的默认值（D4），不重写它的加载器** |
| **删 `PropertyConfig.json`** | 同上 §3.2：零读取、不主动删用户文件。**也不往它里面加东西** |
| **顺带重构 `LivingThing`** | `ENTITY-ATTRIBUTE-SPLIT-2026-10.md` 阶段 2/3 留着；「拆结构的同时改数值 = 出问题分不清是哪边」（那份 §6「千万别」第 3 条） |
| **改 `Entity` 的 `setLevel`** | `EXTERNAL-DATA-LOADING-2026-10.md` 阶段 1 坑 1 已定：它是公共路径，补丁器绕开它而不是改它 |
| **二进制 NBT / 实体存档 / `storage` 落盘** | `NBT-AND-DATA-COMMAND-2026-09.md` 已评估完（不划算）；`70-DATA.md:99`「`/data remove` 不做」 |
| **弱点表 / 净化驱散 / 暴击系统接线** | 用户明确不做（`10-RULES.md` §9.3） |
| **引入第三方库**（Guava / Jackson / 反射工具） | `10-RULES.md` §8；表驱动只用 JDK 自带的 `java.util.function` |
| **改游戏输入方式 / 命令前缀** | `PROJECT-ANALYSIS-2026-09.md` §2.1 |
| **实验代码进 `src/`** | 同上 §7.3 第 4 条。⚠ 本方案里的 `SpecPatcher` 必须**一次做完再进 `src/`**，不要「先放个半成品试试」 |

---

## 七、需要用户拍板的问题（最多 4 条，每条给推荐）

| # | 问题 | 选项 | **推荐** |
|---|---|---|---|
| **Q1** | **那 14 个「声明了能配置、其实从来不读」的键（`criticalRate` / `criticalDMG` / `enhance` / `penetration` / `defenseLoss` / `extraDamage` / 五元素 `*Penetration` + `*DamageEnhance`）怎么办？** | **(a) 全部接上**（语义差异见 §1.5 的「D1 补充」：前 5 个是**面板属性**、配了就整局生效；那 10 个五元素穿透/增伤是**临时属性**、第一局结束就被清零）；**(b) 全部摘掉**（从 `META` 里删，写进配置时报「未知键」+ 一句「这类值请用效果改」）；**(c) 只接面板那 5 个，其余 9 个摘掉** | **(c)**。理由：`criticalRate` / `criticalDMG` / `enhance` / `penetration` / `defenseLoss` **`copy()` 会带、战斗结束不清**（`LivingThing.java:167`、`:1998-2001`）→ 语义干净，用户改了选人列表就看得见；五元素 `*Penetration` / `*DamageEnhance` 是**临时属性**（`AttributeProfile.java:190-201`），配在 `EntityData.json` 里**打完一局就没了** —— 这是给用户挖坑；`extraDamage` 是**死字段**（`LivingThing.java:1993-1995`），接上只会多一个「改了没反应」 |
| **Q2** | **`DEFAULT_TAGS_CONFIG`（`ConfigLoader:87-127`）与 `config/gameConfig/TagConfig.json` 这份重复怎么办？** | **(a) 保留 Java 里那份**，`TagConfig.json` 只当用户文件（删了就用 Java 里的默认值重建 —— 现在的行为）；**(b) 把默认值只留一份在 `config/gameConfig/TagConfig.default.json`**，Java 侧读它；**(c) 把 TagConfig 整体纳入「补丁」语义**（文件里没写的 id 保持构造器的 tags，不重建文件） | **(a) + 加一条自测钉住两边一致**（现在 `ConfigLoader` 硬编码的那份与 JSON 文件逐字节相同，加个断言就不怕漂）。理由：**(b)** 要让 Java 读一个「出厂值文件」——那个文件被删就两头空；**(c)** 改动最大、而 `TagConfig` 目前对玩法**零影响**（`ThinkingControllerAI` 全项目只有 1 处引用，`EXTERNAL-DATA-LOADING-2026-10.md` §1.4 D1），不值得 |
| **Q3** | **`mods/<modid>.json` 的 `common` 段只打实体补丁，但 `MODDING-GUIDE.md` 的示例教用户写 `common.skills` 与 `common.flameReaver`（D6 —— 照抄不生效）** | **(a) 补实现**：`common` 段多支持 `skills`（走 `SkillDataPatcher`）与「规则段」（走 `GameRulesPatcher`，但 `GameRules` 已 `freeze()` → **规则那条做不到**）；**(b) 只修文档**：删掉两个假字段，明说「`common` 只支持 `entities`」；**(c) 折中**：`common` 支持 `entities` + `skills`，规则类明确写「做不到（开局冻结），要改走 `config/gameConfig/GameRules.json`」 | **(c)**。理由：`skills` 那条**技术上完全能做**（`SkillDataPatcher.apply` 已经存在、`loadModData` 里加一行即可，且技能的补丁时机本来就在 `GameStartEvent` 之后）；规则那条**真的做不到**（`GameRules.freeze()` 在 `loadGameRules()` 的 `finally` 里，`:417-420`），**必须**在文档里说清而不是假装支持。**`MODDING-GUIDE.md:615` 那句话已经是对的，把 `:395-405` 与 `:464-472` 改成一致即可** |
| **Q4** | **`DataKeys` 那三个「测试工具」（`auditAgainst` / `auditNotes` / `collectFields`，`:737-817`，81 行）与 `EntityDataPatcher.Snapshot`（92 行）、`SkillDataPatcher.SkillSnapshot`（47 行）这些「只给自测用」的代码放哪？** | **(a) 留在原地**（现成、自测已经依赖）；**(b) 搬进 `debug_tools`**（生产类变干净，但自测要多 import、且契约核对离契约表远了）；**(c) 只搬 `auditXxx`（反射审计）进 `debug_tools`，`Snapshot` 留下**（它是「补丁器会碰哪些字段」的知识，属于补丁器） | **(c)**。理由：`auditXxx` 让 `DataKeys` 背上 `LivingThing` + `DataBridge` 两个 import（`:3-4`），而「契约表」本该零依赖 —— 搬走之后 `DataKeys` 就能被任何地方安全引用；`Snapshot` 则**必须**跟着补丁器（它列的字段清单就是补丁器的字段清单，两处分开必然漂） |

---

## 八、本文没做的事 / 不确定项

1. **没有改任何 `src/` / `mods/` / `config/` 下的文件**，只新建了本文。
2. **★ 没有跑通自测，也没有编译**：本次会话里 `java.exe`、`javac.exe`、`powershell.exe`
   **全部被沙箱拒绝**（`Program 'java.exe' failed to run: Access is denied`），
   所以「655/0」与「`CHECK OK` / `Java files: 215`」里，
   **`src` 文件数 215 是我自己数的**（`Get-ChildItem -Recurse -Filter *.java -File`，与脚本口径一致），
   **655/0 是引用 `notes_for_llm/90-STATE.md:17`**，不是我这次复跑的。
   阶段 0 的验收必须在**能跑 `java` 的会话**里做。
3. **没有实测过 `KeySpec` 原型**：§3.2 的示意代码是**设计**，没编译过。**行数估算（§3.5）是估算**，
   误差估计 ±20%。
4. **D5（三份空文件）的归因不确定**：文件内容空是**实测确定**的
   （`ReadAllLines` 读出 4/4/5 行，字段全空），mtime 与内容都记在 §4.1。
   但**「为什么是空的」只有两条候选**，我**无法用现有证据区分**：
   - 候选一：`writeDefaultConfigFiles()`（`ConfigLoader:606-615`）在 `GameMain:76` 被调用时，
     `World.getEntityList()` 恰好为空（注册流程没跑到 / 中途异常被吞）；
   - 候选二：那三份文件是**更早一版生成器**写的（当时生成器还没实现「遍历注册表」），
     之后因为 `writeEntityData` 有 `file.exists()` 早退（`:239-245`）**再没被覆盖**。
   **不依赖归因的可复现验证（只有用户能做）**：
   ```powershell
   # ① 先备份（万一里面有你想要的东西）
   Copy-Item .\config\gameConfig\*.json .\config\gameConfig\backup_20261003\ -Force
   # ② 删掉三份（GameRules.json 是好的，别删它）
   Remove-Item .\config\gameConfig\EntityData.json, .\config\gameConfig\SkillData.json, .\config\gameConfig\EntityData.default.json
   # ③ 正常启动一次游戏（到选人列表就够），然后看这三份是不是全量的
   ```
   如果重生成的**还是空的** → 是候选一，那是一条**真缺陷**（生成器在错误的时机跑），
   要单独修（把 `writeDefaultConfigFiles()` 挪到 `GameStartEvent` 之后，或把它从
   `loadEntityData()` 里拆出来、在 `GameMain:79` 之后调一次）。
   如果是**全量的** → 是候选二，只需在 `README.md` 里写一句「早期版本生成过空文件，删掉重启即可」。
5. **没有实测 D6**（`common.skills` / `common.flameReaver` 被静默忽略）：
   证据是**静态读代码**（`ConfigLoader:552-556` 只调 `EntityDataPatcher`，
   `EntityDataPatcher.apply:90-98` 硬要求 `entities` 层）。
   自测里那份 `goodJson`（`TestCommandSystem:3941-3944`）**只测了 `common.entities`**，
   所以这条没被任何断言盖住。**验证方式**：用户往 `config/data/<某个模组>.json` 的
   `common` 里写 `"skills": {"game_official_content:iceInsect#冰冻": {"atkMagnification": 1.0}}`，
   启动后看控制台有没有「缺少 `entities` 这一层」那行。
6. **没有评估「把 `ConfigLoader` 拆成 5 个类」**：那是纯审美（它本身是薄壳 + 一个历史包袱 `TagConfig`），
   收益远小于表驱动。**不建议做**。
7. **不确定 `Temporary` 那 14 个键「接上」之后会不会有人真的用**：这需要 Q1 拍板，
   以及用户回答「你有没有想过在配置文件里改暴击率 / 增减伤」。
8. **`mods/` 的 13 个 `.java` 不在自测编译范围内**（`MODDING-GUIDE.md:624`）：
   本方案**一个字都不改模组**，所以这条不影响；但 Q3 若选 (c)（`common` 支持 `skills`），
   上线前要用 `javac` 单独编一遍两个用了 `ModDataAware` 的模组确认没回归。

---

## 九、与前序文档的关系（第 0 条闭环）

| 文档 | 关系 |
|---|---|
| `project_analyses/EXTERNAL-DATA-LOADING-2026-10.md` | **同一子系统的设计稿 + 阶段 0–4 落地记录**。本文**不重复**它的设计结论，只回答「结构该怎么切」；它的 §8.3（既有缺陷 D1–D10）本文**不重开**，本文的 D1–D7 是**读这 8 个文件时新发现的**，与那 10 条不重叠 |
| `project_analyses/ENTITY-ATTRIBUTE-SPLIT-2026-10.md` | **体例模板**（TL;DR → 体检 → 方案 → 硬约束 → 阶段 → 不做的事 → 附录）。本文的阶段 0 沿用它的「不拆东西、先上锁」范式；它的「临时属性 vs 面板属性」分类表是本文 Q1 的判据来源 |
| `notes_for_llm/10-RULES.md` §9.3 | 「不做的事」清单的来源；第 0 条的「读 → 做 → **写回**」是本文 §5 阶段 4 的由来 |
| `notes_for_llm/70-DATA.md` §5.10 | `/data` 键名契约（`:24-25`）、「配置键 = 数据名」（`:42-47`）、「技能与规则是另一套键空间」（`:46-47`）—— 本文 §1.2 把「另一套键空间」当成切分的依据 |
| `MODDING-GUIDE.md` §4.7 / §7.1 | 模组契约（`:357-475`）是本文 §4.2 第 5 条的来源；`:395-405` / `:464-472` 与 `:615` 的矛盾就是本文 D6 |
| `notes_for_llm/90-STATE.md` | 自测基线 `655/0`（`:17`）的来源；阶段 4 要回写 |
| `project_analyses/NBT-AND-DATA-COMMAND-2026-09.md` | **无关**（NBT / 存档早已评估完，别重开） |

---

## 附录 A：本文所有数字怎么复现

> ⚠️ **先看这条坑**（`ENTITY-ATTRIBUTE-SPLIT-2026-10.md` 附录 A 记着，本文照做）：
> 中文 Windows 上 **Windows PowerShell 5 的 `Get-Content` 默认按 GBK 解码**，
> 会把行尾中文字符的尾字节和换行符一起吞掉 → **行数与匹配行数都会少算**。
> 本文所有行号/计数一律用 `[System.IO.File]::ReadAllLines(...)` /
> `[System.IO.File]::ReadAllText(...)`（.NET 默认 UTF-8）读出。

```powershell
# ⓪ 规模（别从仓库根递归扫描：out\artifacts 有 3529 层自我嵌套，会卡死）
"src  .java: " + (Get-ChildItem .\src  -Recurse -Filter *.java -File).Count   # 2026-10-03 实测 215
"mods .java: " + (Get-ChildItem .\mods -Recurse -Filter *.java -File).Count   # 2026-10-03 实测 13

# ① configLoadingSystem 全部文件的行数
Get-ChildItem .\src\cn\gfhnv\game\system\configLoadingSystem -File |
  ForEach-Object { "{0,-30} {1,6}" -f $_.Name, ([System.IO.File]::ReadAllLines($_.FullName)).Count }
#   → ConfigDefaultWriter 869 / ConfigLoader 749 / DataKeys 884 / EntityDataPatcher 1132
#     GameRules 219 / GameRulesPatcher 226 / RuleDefaults 232 / SkillDataPatcher 865   （合计 5176）

# ② config/ 现状（内容 + mtime + 行数）
Get-ChildItem .\config -Recurse -File | ForEach-Object {
  "{0}  {1,4} 行  {2}" -f $_.LastWriteTime, ([System.IO.File]::ReadAllLines($_.FullName)).Count, $_.FullName }
Test-Path .\config\data      # 2026-10-03 实测 False（目录还不存在）

# ③ 每个文件里的「字符串字面量键」计数（§1.3 ①）
Get-ChildItem .\src\cn\gfhnv\game\system\configLoadingSystem -File | ForEach-Object {
  $l = [System.IO.File]::ReadAllLines($_.FullName); $lit = @()
  foreach ($line in $l) {
    $c = ($line -split '//')[0]                       # 去掉行尾注释
    foreach ($m in [regex]::Matches($c, '"([A-Za-z][A-Za-z0-9_\.]{2,40})"')) { $lit += $m.Groups[1].Value }
  }
  "{0,-30} 字面量 {1,4}  去重 {2,4}" -f $_.Name, $lit.Count, ($lit | Sort-Object -Unique).Count }
#   → DataKeys 120/102；EntityDataPatcher 28/12；ConfigDefaultWriter 22/18；
#     SkillDataPatcher 11/8；GameRulesPatcher 5/5；ConfigLoader 1/1；GameRules 0；RuleDefaults 0

# ④ 某个键名在哪些文件里被写成字面量（把 hpGrow 换成你要查的键）
$files = Get-ChildItem .\src\cn\gfhnv\game\system\configLoadingSystem -File
foreach ($k in @('aims','coolDown','hpMagnification','atkMagnification','defMagnification','forEnemies')) {
  "{0,-20}" -f $k
  foreach ($f in $files) {
    $t = [System.IO.File]::ReadAllText($f.FullName)
    $n = ([regex]::Matches($t, '"' + [regex]::Escape($k) + '"')).Count
    if ($n -gt 0) { "        {0} × {1}" -f $f.Name, $n } } }
#   → 6 个技能键各在 ConfigDefaultWriter（:506-511）与 DataKeys（:144-164）各出现 1 次

# ⑤ 五行 switch 的 5 个手写点（§1.3 ③）
Select-String -Path .\src\cn\gfhnv\game\system\configLoadingSystem\*.java -Pattern 'case "metal"' |
  ForEach-Object { "{0}:{1}  {2}" -f $_.Filename, $_.LineNumber, $_.Line.Trim() }
#   → ConfigDefaultWriter:630,645 ; EntityDataPatcher:419,436,723,777,844   （7 个 switch 头）

# ⑥ 「声明能配置」与「补丁器真的读」的差集（★ D1 的证据）
Select-String -Path .\src\cn\gfhnv\game\system\configLoadingSystem\EntityDataPatcher.java `
              -Pattern 'apply(Long|Double|String|Element|Derived|ElementValues|InventorySlots)\(' |
  ForEach-Object { "{0}  {1}" -f $_.LineNumber, $_.Line.Trim() }
#   逐行读 :187-215 与 :366-375：应用的是 base 12 + derived 4 + 五行 5 抗性 + 5 法力成长
#   而 DataKeys.buildMeta()（:842-872）登记 41 个，其中 temporary 14 个 → 差集就是 D1 那 14 个

# ⑥b 那 14 个键的生命周期（★ D1 补充的证据：面板 vs 临时 vs 死字段）
$lt = [System.IO.File]::ReadAllLines('.\src\cn\gfhnv\game\entity\LivingThing.java')
$lt[1997..2000]      # "故意不在表里的：… penetration / enhance / criticalDMG —— 它们跟着副本走"
$ap = [System.IO.File]::ReadAllLines('.\src\cn\gfhnv\game\entity\AttributeProfile.java')
$ap[189..200]        # resetTemporary() 只清 5 穿透 + 5 元素增伤

# ⑦ 生成器写出去的键数（§1.3 ②）
Select-String -Path .\src\cn\gfhnv\game\system\configLoadingSystem\ConfigDefaultWriter.java `
              -Pattern 'put\(values,|put\(derived,|scalars\.put\(' |
  ForEach-Object { "{0}  {1}" -f $_.LineNumber, $_.Line.Trim() }

# ⑧ TagConfig 两份默认值的比对（★ D4 的证据）
#    Java 侧是 41 行字符串拼接（"…" + \n + "…"），不能按行比；把引号与转义去掉再比内容。
$src  = [System.IO.File]::ReadAllText('.\src\cn\gfhnv\game\system\configLoadingSystem\ConfigLoader.java')
$java = [regex]::Match($src, 'DEFAULT_TAGS_CONFIG = (.*?);', 'Singleline').Groups[1].Value
$javaFlat = ([regex]::Matches($java, '"((?:[^"\\]|\\.)*)"') |
             ForEach-Object { $_.Groups[1].Value -replace '\\n', "`n" }) -join ''
$fileFlat = [System.IO.File]::ReadAllText('.\config\gameConfig\TagConfig.json')
"Java 侧键值对: " + ([regex]::Matches($javaFlat, '"[a-z_]+"\s*:\s*[0-9]')).Count
"JSON 侧键值对: " + ([regex]::Matches($fileFlat, '"[a-z_]+"\s*:\s*[0-9]')).Count
"两边去空白后相同: " + (($javaFlat -replace '\s', '') -eq ($fileFlat -replace '\s', ''))
#   → 都是 26 个键值对，且去掉全部空白之后逐字符相同（7 个 id：insectBoss 3 + commonInsect 3 +
#     iceInsect 4 + playerOne 5 + actorLiXiaoYan 5 + aNiceSword 1 + phainon 5 = 26）

# ⑨ 静态自查与自测（★ 本次会话里这两个都跑不了，见 §8）
powershell -ExecutionPolicy Bypass -File .\check-sources.ps1          # 期望 Java files: 215 / CHECK OK
powershell -ExecutionPolicy Bypass -File .\test-command-system.ps1   # 期望 通过 655 条，失败 0 条
```

## 附录 B：本文引用的行号清单（2026-10-03 用 `ReadAllLines` 核对）

| 位置 | 行号 |
|---|---|
| `DataKeys.java`（884 行） | `:46-58` 四个 `TYPE_*`；`:67-68` `ELEMENTS`；`:73-127` JSON 格式常量 + 分组名 + `BARE_SECTIONS`；`:140-199` `SkillKeys`；`:211-368` `Rule.*`；`:373-422` `Base`；`:430-447` `Derived`；`:453-479` `Temporary`；`:490-595` `ReadOnly`；`:609-621` `READ_ONLY`；`:629-630` `NOT_IN_DATA`；`:635-648` `Alias`；`:657` `KeyMeta`；`:663` `GROUPS`；`:668` `META`；`:679-721` 对外方法；`:737-817` 审计工具（`auditAgainst`/`collectFields`/`auditNotes`/`javaTypeName`）；`:822-837` `buildGroups`；`:842-872` `buildMeta` |
| `EntityDataPatcher.java` | `:44` 类；`:84-119` `apply`（`:90` 取 `entities`、`:92-98` 缺层报错）；`:131-169` `targetsOf`；`:187-215` `patch`；`:229-237` `recalculateDerivedStats`（**D3 的 `200`/`110`/`200` 在 `:231-233`**）；`:250-276` `reportUnknownKeys`（`:262`/`:272` 两处放行 `isConfigurable`）；`:292-320` `lookup`；`:330` `KeySource`；`:342-353` `touchesDerivedStats`；`:366-375` `applyDerived`；`:385-410` `applyElementValues`；`:417-425` `resistanceSetter`；`:434-444` `setManaGrow`；`:458-496` `applyInventorySlots`；`:510-611` 单项 `applyXxx`；`:622-643` `applyElement`；`:649-670` `elementOf`/`elementNames`；`:681-714` `stateOf`；`:721-729` `manaGrowOf`；`:760-851` `Snapshot`；`:861-904` `asLong`/`asDouble`/`describe`；`:911-946` 三个函数式接口；`:960-1114` `Report`（`:1003` `markBound`、`:1098` `print`）；`:1123-1131` `applyJson` |
| `SkillDataPatcher.java`（865 行） | `:81-115` `apply`；`:128-156` `targetsOf`；`:165-200` `entitiesOf`；`:227-238` `patch`；`:251-267` `applyExtraNumeric`；`:286-322` `applyConsumedMana`；`:335-368` `applyTags`；`:380-390` `reportUnknownKeys`；`:396-404` `isKnown`；`:420-483` 单项 `applyXxx`；`:489-513` `tagTypeOf`/`tagTypeNames`；`:528-598` `stateOf`/`find`/`all`；`:619-665` `SkillSnapshot`；`:721-847` `Report`；`:856-864` `applyJson` |
| `ConfigDefaultWriter.java`（869 行） | `:54-74` 文件/段名常量（`:70` `SKILLS = "skills"` 字面量）；`:103-145` 三个文本入口；`:152-226` 三个 `appendXxx`；`:239-275` 三个 `writeXxx`；`:285-309` `gameRulesJson`；`:320-325` `valueOfRule`；`:333-365` `writeReference`/`writeAll`；`:377-383` `writeUtf8`；`:392-401` 契约输出；`:415-447` `collectBaseValues`；`:454-472` `collectDerivedValues`；`:479-517` `collectSkillValues`（**`:506-511` 六个字面量键**）；`:528-559` `collectSkillPatches`；`:567-598` `skillPatchOf`；`:609-621` `SkillPatch`；`:628-651` `resistanceOf`/`manaGrowOf`（2 个 switch）；`:663-672` `readDescription`；`:694-844` 内嵌 `Json`；`:854-868` `dataNames`/`dump` |
| `ConfigLoader.java`（749 行） | `:58-82` 五个路径常量；`:87-127` `DEFAULT_TAGS_CONFIG`（**D4**）；`:132` `tagsMap`；`:139-150` 三个 `loaded` 标志；`:158-161` 静态块；`:168-197` 四个零调用点 getter；`:213-288` `loadConfig`；`:304-333` `loadEntityData`；`:349-377` `loadSkillData`；`:392-421` `loadGameRules`（`:417-420` `finally { freeze() }`）；`:449-462` `resolveModConfigId`；`:502-576` `loadModData(Mod,File)`（**`:552-556` 只打实体补丁 —— D6**）；`:587-599` `loadAllModData`（零调用点）；`:606-615` `writeDefaultConfigFiles`；`:625-633` `setDefaultConfig`；`:646-718` `parseObject`/`warnAboutVersion`/`describe`/`lineOf`；`:724-748` `parseTagType`/`tagNames` |
| `GameRules.java`（219 行） | `:63-85` 三个取值接口；`:91-95` `isIntegerKey`；`:108-140` `knownKeys()`（**29 行 `keys.add`**）；`:145-162` `allKeys`/`isKnown`/`current`；`:174-218` `beginLoad`/`put`/`freeze`/`resetForTest` |
| `RuleDefaults.java`（232 行） | `:28-55` 公式与法力 7 个常量；`:62-102` 盗火行者 11 个；`:109` 虫皇；`:116-152` 李晓焰 10 个；`:157` `ALL`；`:192-231` `build()`（29 行 `map.put`） |
| `GameRulesPatcher.java`（226 行） | `:34-35` `SECTIONS`（**5 个字面量段名**）；`:52-92` `apply`；`:101-109` `applyJson`；`:116-121` `print`；`:128-225` `Report` |
| `GameMain.java` | `:61-82` `gameInitialize`（`:65` `loadGameRules()`、`:66` `new OfficialGameContent()`、`:67` `ModLoader`、`:72` `GameStartEvent`、`:73` `loadTagConfig()`、`:76` `loadEntityDataConfig()`、`:79` `loadSkillDataConfig()`、`:81` `CommandManager.initialize()`）；`:90-138` 三个 `loadXxx` 包装（各自 try/catch） |
| `GameStartEventListener.java` | `:17-29` 模组循环（`:26` `loadModData`、`:27` `invokeWhenLoaded`、`:28` `registerItself`） |
| `Mod.java` | `:175-180` `addEntity`（加 `MOD_ID:` 前缀）；`:231-249` `registerItself`（真正往 `World` 里塞） |
| `TestCommandSystem.java`（4877 行） | `:2782-3210` `testConfigDefaultsAndPatch`（`:2787-2793` META/GROUPS 一致、`:2816-2819` 字段核对、`:2855-2874` 生成器⊆META、`:2896-2914` dump 契约、`:2957-2996` 13 参构造器、`:3013-3030` 全量默认值打回去、`:3036-3045` 只改一个键、`:3186-3191` 不覆盖已有文件）；`:3230-3563` `testSkillDataPatch`（`:3941-3944` 是 `testModConfig` 的 fixture —— **只测 `common.entities`**）；`:3583-3838` `testGameRules`（`:3615-3617` 认识 29 条键）；`:3839-4285` `testModConfig` |
| `config/gameConfig/` | `EntityData.json` 4 行、`SkillData.json` 4 行、`EntityData.default.json` 5 行（**三份都空**，mtime `2026-10-03 13:30:34`）；`GameRules.json` 42 行全量（mtime `13:34:26`）；`TagConfig.json` 41 行、`PropertyConfig.json` 5 行 |
| `LivingThing.java`（2353 行） | `:79` `criticalRate` 字段；`:167` 复制构造器带 `criticalRate`；`:150` 组件里"面板属性那一半"的注释；`:1418-1428` `getCriticalRate`/`setCriticalRate`；`:1998-2001` **「故意不在临时属性表里」的清单**（`penetration`/`enhance`/`criticalDMG`/抗性/法力成长）；`:2005-2023` `clearTemporaryAttributes()`；`:2032-2050` `whenFightEnds()`；`:2161-2171` `getCriticalDMG`/`setCriticalDMG` |
| `AttributeProfile.java`（544 行） | `:12-16` 面板属性 vs 临时属性的两张表；`:142` `penetration`、`:146` `enhance`、`:151` `criticalDMG`（**面板**）；`:64-105` 五元素穿透 + 五元素增伤（**临时**）；`:163` `copyFrom`；`:190-201` `resetTemporary()` |
| `MODDING-GUIDE.md` | `:357-475` §4.7 模组配置（`:395-405` **含 `common.skills`/`common.flameReaver` 的假示例**、`:424-433` 六条规则、`:460-475` 「官方数值也能改」**第二个假示例**）；`:607-628` §7.1（`:615` **正确的**「`common` 改不了 `GameRules`」） |
| `notes_for_llm/70-DATA.md` | `:24-25` 数据名是对外 API；`:42-47` 配置键 = 数据名 + 「技能与规则是另一套键空间」；`:49-58` 改名的坑与 setter 名 |

---

*本文由 AI（DeepSeek）生成，2026-10-03。所有「现状」结论均已在源码中逐条核对（附行号，清单见附录 B）；
行号与计数一律用 `[System.IO.File]::ReadAllLines` 复核（见附录 A 的坑）。
⚠️ 本次会话里 `java` / `javac` / `powershell` 被沙箱拒绝，**自测与编译都没有实跑**（见第八节第 2 条），
所以本文的「655/0」是引用 `notes_for_llm/90-STATE.md:17`，不是复跑结果；阶段 0 的验收请在能跑 `java` 的会话里做。
本文**一行 `src/` / `mods/` / `config/` 代码都没改**。真要动手时请先回写 `notes_for_llm/70-DATA.md` 与 `90-STATE.md`。*


---

## 十、阶段 0–5 落地记录（2026-10-03，AI 实做，五步各自全绿）

> **这一节是落地后补写的**（原文只到"分阶段路线"，没有施工记录）。所有数字都是本轮**实跑**的，
> 不是引用。五步各自的验收都是「`check-sources.ps1` → `CHECK OK`；`javac` 只编 `src` → exit 0；
> 自测 → 失败 0 条」，**每一步跑完才进下一步**。

### 10.1 五步与各自的验收数字

| 步 | 做了什么 | 自测 |
|---|---|---|
| 起点 | 复跑基线（确认用户给的数字） | `CHECK OK` / 215 文件 / **655/0** |
| **1** | 清死代码 + **D3**（重算走规则表）+ **D2** 报错订正 + **D4** 形状守卫 | **661/0** |
| **2** | `KeySpec` 契约层 + 实体表驱动 + **Q2**（接 5 个面板键、摘掉 11 个假键） | **668/0** |
| **3** | 技能键 + 规则键进表（**D7** 结案、`RuleDefaults` 的表删掉、段名从表派生） | **672/0** |
| **4** | **D5** 自愈（缺啥补啥）+ **D6**（`common` 支持 `skills`、规则段明确报错） | **676 → 677/0** |
| **5** | 文档回写（本文 + `EXTERNAL-DATA-LOADING` + `MODDING-GUIDE` + `90-STATE` + `30-WORKFLOW`） | 677/0（复跑） |

**最终：`CHECK OK` / `Java files: 221` / `通过 677 条，失败 0 条`。**

### 10.2 目标结构实际长成了什么样（与 §3.1 的差别）

§3.1 画的 9 个新文件，实际落地成 **7 个**，其中两个改了形状，理由写在下面：

| 计划 | 实际 | 说明 |
|---|---|---|
| `KeySpec` | ✅ 一样 | 多了一个 `Kind.INT` / `Kind.BOOLEAN`（技能的 `aims` 要判 `int` 范围、`forEnemies` 要判布尔），以及 `Kind.coerce()` 返回 `Coerced(value, problem)` —— **报错文案由"种类"给**，所以三个补丁器的措辞天然一致 |
| `EntityKeySpecs` | ✅ 一样 | 另外长出 `meta()` / `groups()`：`DataKeys.META` / `DataKeys.GROUPS` 现在**就是它**（不是"两边对齐"，是同一个对象） |
| `SkillKeySpecs` | ✅ 一样 | 6 个标量 + 2 个块（`consumedMana` / `tags`）+ 动态旋钮键 `NEEDED_MANA_SCALE` 的放行 |
| `RuleKeySpecs` | ⚠️ **形状改了** | §3.1 说"出厂值作为 `KeySpec` 的一个字段"。实际**没有硬套 `KeySpec`**：规则没有"读对象 / 写对象"这两列（默认值住在公式里、实现路径是静态表），硬套只会多两个恒为 `null` 的函数。改成一张自己的小表 `record RuleKey(key, section, defaultValue, note)` |
| `KeyTables` | ❌ **没建** | 它原本只是"三张表的只读视图"。落地时发现**没有必要**：`DataKeys` 自己就是那个入口（`META` / `groups()` / `isConfigurable` 都还在原地），多一层壳只会让"表在哪"更难找 |
| `SpecPatcher` / `SpecWriter` | ✅ 一样 | `SpecPatcher.patch()` 返回**被应用的键名集合**（调用方用它判断"要不要补一次派生值重算"）；`SpecWriter.collect()` 只产出"键 → Java 值"，排版仍归 `ConfigDefaultWriter` 的 JSON 输出器（不混职责） |
| `PatchReport` | ❌ **没建** | 三个 `Report` 的**方法签名本来就一样**，改成 `SpecPatcher.Sink` 接口即可共用通用补丁器；把三个类合成一个要动 380 行只为一个"少两个类"，收益不抵风险（详见 10.5 的"没做完的部分"） |
| `JsonText` | ❌ **没搬** | 同上：150 行 JSON 排版器搬家是纯位移，与"加一个键要改几处"这个指标无关。**它现在的唯一真相是 `ConfigDefaultWriter.Json`**，两个新类都不需要重复它 |
| `DataKeys` 瘦身 | ⚠️ **瘦了一半** | 884 → 910 行？不：`buildGroups()` + `buildMeta()` 那 100 行**删掉了**、4 个零调用点 getter 删了、`Temporary` 里 11 个假键删了；但**注解开销补回来了**（每个键的 `note` 与"为什么摘掉"的说明），所以行数没有显著下降 —— **行数不是这次的目标，"改动点数量"才是** |
| `RuleDefaults` 删除 | ⚠️ **只删了表，没删常量** | `build()`（29 行 `map.put`）+ `all()` / `of()` / `number()` **已删**；但 29 个值常量**保留** —— 它们被 `ActorLiXiaoYan` 的静态常量与 6 个类的 `{@value}` Javadoc 引用着，而 `{@value}` 要求编译期常量表达式 |

### 10.3 核心指标：新增一个可配置键，现在改几处

| 情形 | 重构前（§2 实测） | **重构后（本轮实测）** |
|---|---|---|
| 让一个已声明的键生效 | 2 文件 2 处 | **1 文件 1 行** |
| 加一个全新的实体字段 | 4 文件 **9~10 处** | **1 文件 1 行**（`EntityKeySpecs` 加一行；Java 字段与 getter/setter 本来就要写） |
| 加一个五行类字段 | 5 文件 **27~28 处** | **1 文件 1 行**（`ELEMENTS` 循环自动生成，**4 个 `switch` 全没了**） |
| 加一个技能键 | 3 文件 3~4 处（含 `isKnown()` 的或链） | **1 文件 1 行**（`SkillKeySpecs`） |
| 加一条规则键 | 4 文件 **5~7 处** | **2 文件 2 处**（`RuleDefaults` 加值常量 + `RuleKeySpecs` 加一行）—— 没做到 1 处，理由见 10.2 的 `RuleDefaults` 那一行 |

### 10.4 顺手发现并修掉的 3 个新缺陷（不在 D1–D7 里）

| # | 缺陷 | 证据 / 症状 | 修法 |
|---|---|---|---|
| **D8** | **五行五个扁平的 `*ManaGrow` 键从来没被读过** | 生成器一直把它们写进 `EntityData.json`、`META` 里也声明"能配置"，但补丁器只认 `manaGrow` **块** —— 用户按默认文件写 `"metalManaGrow":20` 一点反应都没有（和 D1 完全同一类，只是没被 §1.5 数出来） | 表里加 5 行（`ELEMENTS` 循环），补丁器自动读；新增自测「五个扁平键都能改」 |
| **D9** | **`manaGrow` 块写法其实也从来没生效过** | 块名与键名恰好都是 `manaGrow`，`lookup()` 第一步 `patch.has(key)` 就把**整份补丁**当成"值"返回了，于是块里的元素名被当成 `patch.keySet()` 去遍历 → 用户按文档写块得到一句"认不出这个元素（可用：[metal, wood, water, fire, dirt]）" | 新增 `SpecPatcher.lookupManaGrowBlock()`（**绕开** `patch.has(key)` 那一步），并写清"块名撞键名"这个坑；新增自测「`manaGrow` 块写法仍然有效」 |
| **D10** | 零调用点的死代码 | `ConfigLoader.getEntityDataFile()` / `getSkillDataFile()` / `getGameRulesFile()` / `getTagsMap()`（javadoc 写着"供自测与将来的模组接口使用"，那个接口没做成）与 `DataKeys.GROUP_DERIVED` | 全部删除（删前 `grep` 全项目确认零调用点，含自测） |

> **⚠️ D10 的复核（2026-10-03 晚，见 §12.5.1）**：四个 getter **确实已经删干净**（本轮再 grep 一次，
> 全项目 0 命中，只剩私有的 `tagsMap` 字段与它内部 5 处使用）；
> 但 **`DataKeys.GROUP_DERIVED` 的"零调用点"结论已过期** —— 它现在是
> `EntityKeySpecs.DERIVED` 四行的**活分组常量**（4 处使用）。D10 这条按"当时的判断"读即可。


### 10.5 每个阶段的踩坑（照着踩过的写）

1. **阶段 1：`META` 与 `GROUPS` 必须同时改**。摘掉 11 个假键时只改了 `buildMeta()`、忘了 `buildGroups()`，
   自测立刻红：「能配置的键数与分组表不一致：31 vs 30」—— **这条断言值 5 分钟**，它是唯一拦住"改一半"的东西。
2. **阶段 2：`DataKeys.META` 从表派生会撞上"静态初始化循环"**。`DataKeys.<clinit>` 调
   `EntityKeySpecs.meta()`，而表里那 10 个五行键要用 `DataKeys.ELEMENTS`（它不是编译期常量）——
   于是 `DataKeys` 初始化到一半又回头读自己，拿到 `null`。
   **解法**：`ELEMENTS` 与表里的五行名单**都从 `ElementSort.values()` 派生**（去掉 `UNIVERSAL`），
   两边一致性由既有的「五行清单与 ElementSort 一一对应」那条断言钉住。这条坑如果没提前想到，
   症状会是 `ExceptionInInitializerError`，很难看出是初始化顺序。
3. **阶段 2：自测里那条"反向断言"是这次最有价值的一条**。
   遍历 `EntityKeySpecs.ALL`，对每一行写回它自己的当前值，断言"报告里 applied 非空且 skipped 为空"——
   它一次把 D1 那一类"声明了没人读"全部盖住，而且方向与旧断言（生成器 ⊆ META）**正好相反**。
4. **阶段 3：`RuleDefaults` 不能整个删掉**。计划是"出厂值搬进表、本类删除"，实际动手才发现
   `ActorLiXiaoYan` 用它的 10 个常量、6 个类用 `{@value RuleDefaults#XXX}` 写 Javadoc，
   而 `{@value}` 要求编译期常量。**改成"值留在常量、映射搬进表"**，并且自测里用
   `Class.forName(...).getDeclaredMethod("all")` 断言"那张手写的 map 不许回来"。
5. **阶段 4：改自愈之前先想清楚"谁是用户改过的"**。老写法 `if (file.exists()) return false;` 改成
   "补缺"时，第一版差点写成"把默认值里的键全部覆盖回去"—— 那会把用户改过的值悄悄回退，比空文件更糟。
   最终语义是**单向合并**：默认值有、文件里没有 → 补；文件里已经有的 → **一个字节都不动**；
   补完没变化 → **不写盘**（所以 mtime 不会每次都变）。
   另外**内容不是合法 JSON 时一个字节都不动**（不替用户"修"文件）。
6. **阶段 4：`common` 段原来是"无条件打实体补丁"**。加了 `skills` 之后，如果还无条件调用
   `EntityDataPatcher.apply(common, ...)`，那么"只写 `skills` 的 common"会先收到一句假的
   "缺少 entities 这一层"。改法是只在 `has("entities") || !has("skills")` 时才走实体那条路 ——
   **保留了老写法缺层时的报错**（自测覆盖），又不会给新写法刷假错。

### 10.6 没做到 / 偏离计划的部分（诚实清单）

| 项 | 状态 | 原因 |
|---|---|---|
| `KeyTables` / `PatchReport` / `JsonText` 三个文件 | **没建** | 见 10.2；三者都是"纯位移 / 纯合并"，与"加一个键改几处"这个指标无关，而合并三个 380 行的 `Report` 要动大量报错文案，风险 > 收益 |
| `RuleDefaults` 完全删除 | **没做** | 见 10.2 / 10.5 第 4 条；值常量被游戏代码与 `{@value}` 引用 |
| 规则键"1 处" | **2 处** | 同上 |
| `DataKeys` 降到约 60 行 | **没达到** | 表拆出去了，但键的说明与"为什么不支持"的文案留在了注释里；本轮的指标是改动点数量，不是行数 |
| `ConfigLoader` 三个同构 `loadXxx` 收成一个泛型入口 | **没做** | §3.5 的估算里有这一条，但它是纯审美（三个方法各 30 行、结构同构），没有键名/契约收益 |
| `EntityDataPatcher` / `SkillDataPatcher` 的 `targetsOf` 重复（24 行） | **没合并** | 同上：合并它要引入一个新类 `Targets`，而两个 `targetsOf` 的报错文案其实**不一样**（实体说"注册表里没有实体"，技能说"实体…上没有叫…的技能"），合并会丢信息 |
| `mods/` 与 `config/` | **一个字没改** | 硬约束；本轮全程只读它们 |
| `notes_for_llm/70-DATA.md` §5.10 / `README.md` | **没回写** | `70-DATA.md` 的"配置键 = 数据名"结论**没有变化**（键名一个没动），不需要改；`README.md` 里"删掉整个文件 = 下次启动会重新生成一份全量默认值"那句话现在**终于是真的了**（D5 修的就是它），但措辞仍成立，留待下次一并润色 |

### 10.7 回滚 / 继续做的入口

- **改回去**：本轮所有改动都在 `src/cn/gfhnv/game/system/configLoadingSystem/`（3 个新表 +
  2 个通用器 + 8 个既有文件）与 `TestCommandSystem`；`/data` 契约、三份 JSON 的键名、
  13 参构造器、`mods/` 全部没动，所以**回滚的爆炸半径就是这一个包**。
- **继续做**：想继续收 §10.6 那几项，建议顺序是 ①合并三个 `Report`（先统一报错文案的措辞，
  再动结构）②`JsonText` 搬家 ③`targetsOf` 合并。
  三者都**不改变任何键名或行为**，做完各自跑一次自测即可。
- ⚠️ **`notes_for_llm/` 在 `.gitignore` 里**：本轮回写的 `90-STATE.md` / `30-WORKFLOW.md`
  **`git diff` 看不到**，别以为没改。

---

## 十一、反射驱动可行性实验（2026-10-03，AI 实做，**并列**的一条新路）

> **这一节回答的是另一个问题**：§十 把"加一个键"压到了 **1 个文件 1 行**，
> 但那一行仍然是**手写的**（键名/类型/读法/写法都得人再抄一遍，抄错不报错、只静默失效 ——
> D1 那 14 个键就是这么来的）。所以用户问的是：
> **能不能让配置直接寄生在 `DataBridge` 的反射上，于是"加一个加载项 = 加一个 Java 字段"？**
>
> **结论：三件事全部成立**。新字段在配置层的改动点是 **0 处**（表驱动路是 1 文件 1 行）。
> **但"全面切换"要还三笔债**（见 §11.6），所以实验产物是**并列的新路**，不是替换。
>
> **一句免责**：本节的所有数字都是**本轮实跑**的（`check-sources.ps1` / `javac` / 自测
> `通过 702 条，失败 0 条`），不是估算。原先的表驱动路**一件没删、一个键名没改**。

### 11.1 造了什么（5 个文件，全部是"新增"，没有第二套清单）

| 文件 | 行数级 | 干什么 |
|---|---|---|
| **新** `game/data/NoConfig.java` | 34 行 | **否定式注解**：写它 = 这个字段不进配置文件（带一句理由，自测要求非空） |
| **新** `configLoadingSystem/ReflectionConfigBridge.java` | 365 行 | 反射驱动的补丁器 + 默认值 dump + 挡板清单。**里面一个键名都没有** —— 只有"怎么用一个键" |
| **新** `debug_tools/ConfigReflectionProbeEntity.java` | 105 行 | 探针实体，带一个**全新字段** `resonanceCoefficient`（配置系统从没见过） |
| **改** `game/data/DataBridge.java` | +81 行（843 → 924，`ReadAllLines` 复核） | 只加了两个复用出口：`DataAccessor`/`accessors(Object)`（把内部那份 `dataFields` 的结果开放出来）与 `applyTag(target,name,tag)`（= `/data merge` 那条路本身，只是把异常收成一句文本）。**原有逻辑一行没动** |
| **改** `game/Thing.java` / `game/entity/LivingThing.java` | **+9 个注解**（`uuid` / `id` / `alive` / `effects` / `manas` / `participateFight` / `presentTurn` / `controller`） | 各带一句理由；探针里另加 1 个（`probeState`）—— 全项目共 **9 处 `@NoConfig`** |
| **改** `debug_tools/TestCommandSystem.java` | +257 行（1 个方法 + 257 行实现） | `testReflectionDrivenConfig()`（**25 条断言**），放在 `testConfigDefaultsAndPatch` 之后、`testSummonCommand` 之前 |

**关键设计约束（用户点名的）**：**配置层没有抄第二份 `DataBridge`**。证据是三个"同一份实现"的引用：

| 配置层需要的三件事 | 反射路用的是 | 也就是 |
|---|---|---|
| 键清单 | `DataBridge.accessors(Object)` | `dataFields()` + `dataName()`（`/data` 自己那份） |
| 默认值（读） | `DataBridge.toTag(Object)` | **`/data get` 本身** |
| 写入 | `DataBridge.applyTag(...)` → `DataBridge.apply(...)` | **`/data merge` 本身**（setter 优先、`final` 拒绝、`@DataFlatten` 组件下潜全在它里面） |

### 11.2 ① 反射驱动：配置一行不用改，字段就出现并生效 —— **成立**

**断言原文**（自测实跑输出，`testReflectionDrivenConfig`）：

```
  [信息] 补丁（键名 = /data 数据名）：{"criticalRate":0.125,"metalResistance":0.5,"speed":127}
  [信息] 记账：应用 {criticalRate=0.125, metalResistance=0.5, speed=127}，跳过 {}
  [OK] ① 反射驱动：三个键全部写进去、没有跳过项 —— [criticalRate, metalResistance, speed]
  [OK] ① 反射驱动：criticalRate 真的变了（+0.125）—— 0.0 → 0.125
  [OK] ① 反射驱动：speed 真的变了（+7）—— 120 → 127
  [OK] ① 反射驱动：metalResistance 真的变了（+0.5，字段住在 @DataFlatten 组件里）—— 0.0 → 0.5
  [OK] ① 默认值：dump 出来的 resonanceCoefficient 就是字段初始化器的值 3.5（不需要另建「出厂值」表）—— 实际 3.5d
  [OK] ① 还原：模板逐字段回到打补丁之前（自测不污染注册表）—— true
```

- **端到端形态**：`{"entities":{"<id>":{"<键>":<值>}}}` → `ReflectionConfigBridge.patch(target, 补丁)`。
  键名就是 `/data` 的数据名（`criticalRate` / `speed` / `metalResistance`），**没有第二套名字**。
- **`metalResistance` 这一条特别值**：字段住在 `@DataFlatten` 组件 `AttributeProfile` 里，
  写回时 `lookup` 返回的容器是组件、`trySetter` 在组件上找到 `setMetalResistance` ——
  **组件搬迁对配置面完全透明**（这正是 §1.2 那条"搬进组件会不会改键名"的答案）。
- **"默认值 = 构造器当时的值"**：dump 走 `DataBridge.toTag`，读的就是字段当下的值；
  探针那个刚 `new` 出来、谁都没碰过的对象 dump 出 `3.5d` = 字段初始化器写的值。
  **不需要为配置另建一张"出厂值"表**（表驱动路的 `RuleDefaults` 就是被这件事逼出来的一张表）。
- **⚠️ 一个诚实的偏差**：这条路的**集成点是新的** `ReflectionConfigBridge.patch`，
  **不是** `EntityDataPatcher.apply`（那个类按设计只认手写表）。要接进 `EntityDataPatcher` 需要
  1 处小改（`patch()` 里加一次反射桥调用 + 让 `reportUnknownKeys` 放行它处理过的键），
  **没做** —— 理由见 §11.6 第 3 条。所以"0 处改动"这个数字指的是"**加一个新字段**"，
  不是"新路已经接进正式入口"。

### 11.3 ② `@NoConfig` 挡得住、而且"默认开放" —— **成立**

**为什么并列一个新注解、不复用 `@NoData`**（这是本节唯一需要论证的设计决定）：

一个字段其实有**三面**：`/data get`（看得见）、`/data merge`（改得动）、配置文件（能配）。
`@NoData` 是**一个开关同时关掉三面**（它住在这三面的公共上游 `DataBridge.dataFields()`），
而本次要挡的字段**只想关第三面**：

| 字段 | `/data get` | `/data merge` | 配置文件 | 用 `@NoData` 会怎样 |
|---|---|---|---|---|
| `uuid` | 看得见（122 条断言里键名契约盯着它） | 改不动（`final` 拒绝） | **不该能配** | 键名从 `/data` 消失 → **破坏键名契约** |
| `alive` | 看得见（是状态，调试要看） | 改得动 | **不该能配** | 同上 |
| `presentTurn` | 看得见 | 改得动 | **不该能配** | 同上 |

所以复用 `@NoData` 会逼出一个二选一：**要么放开配置（危险），要么在 `/data` 里消失（破坏契约）**。
**并列一个新注解**则各管一面：`@NoData` 由 `DataBridge` 读（"这算不算数据"），
`@NoConfig` 由配置侧的反射桥读（"这能不能由玩家改"）。**`@NoData` 蕴含"不可配置"**（字段根本不在清单里）。

**断言原文**：

```
  [信息] LivingThing 上被 @NoConfig 挡住的字段：{alive=…, controller=…, effects=…, id=…, manas=…,
          participateFight=…, presentTurn=…, uuid=…}          ← 8 个，每个都带理由
  [OK] ② 挡板：uuid / alive / presentTurn 都在 @NoConfig 清单里（少一个就是配置面漏了）—— 实扫 [alive, controller, effects, id, manas, participateFight, presentTurn, uuid]
  [OK] ② 挡板：每个 @NoConfig 都写了理由（防止有人只加注解不加说明）—— 没写理由的 []
  [信息] 挡板记账：应用 {}，跳过 {probeState=…, alive=…, presentTurn=…, uuid=…}
  [OK] ② 挡板：uuid / alive / presentTurn / probeState 一个都没写进去 —— 跳过 [probeState, alive, presentTurn, uuid]
  [OK] ② 挡板：被挡的字段值真的没动（alive / uuid / presentTurn 逐字段比对）
  [OK] ② 默认开放：没有任何注解的字段一律在可配置清单里（resonanceCoefficient / mass / name …）—— resonanceCoefficient 在里面
  [OK] ② 默认开放：只写了 @NoConfig 的 probeState 不在可配置清单里，但 /data 那一面照旧看得见它（注解只管配置面）
```

**"默认开放"（否定式的关键）**：探针上 `probeState` 只写了 `@NoConfig`，结果
**配置面没有它、`/data` 的 `dataNames` 里照旧有它** —— 直接证明了"两个注解各管一面"，
以及"**不写注解 = 能配**"。下面 11.4 的 0 处改动就是这个性质的直接后果。

**顺手发现并当场修掉的一条真缺陷（D11）**：第一版候选规则是"非 `@NoConfig`"，
自测立刻红在 `inventory` / `damageReductions` / `damageModifiers` —— 它们**没有 setter 也不是标量**，
放进配置面只会静默退化成 `DataBridge` 的**裸写字段**（`70-DATA.md` §5.10 点名的那个坑）。
**修法**：候选规则加第二条"**必须是标量**"，非标量由规则自动排除并在挡板表里点名
（9 个：`tags` / `force` / `velocity` / `acceleration` / `position` / `inventory` / `damageReductions` /
`damageModifiers` / `showSpecialMes`），**不需要写 9 个注解**。两条断言钉住它。

### 11.4 ③ 最强的证明：造一个新字段，看要改几处 —— **0 处配置层改动**

探针 `ConfigReflectionProbeEntity.resonanceCoefficient` 是**全新字段**：
`DataKeys` 没有它的常量、`EntityKeySpecs` 没有它那一行、`ConfigDefaultWriter` 不写它、
`EntityDataPatcher` 不读它、所有既有断言也不知道它。

**断言原文**：

```
  [信息] 探针默认值 dump 里有没有 resonanceCoefficient：true，值 = 3.5d
  [OK] ③ 新字段：(a) 它出现在默认值 dump 里，且值 = 字段初始化器的值（配置系统从没见过它）
  [OK] ③ 新字段：DataKeys 那张表里**没有**它（它不可能来自手写清单）
  [OK] ③ 新字段：EntityKeySpecs 那张表里也没有它
  [信息] 探针配置（就是 EntityData.json 的形状）：{"entities":{"probe:configReflectionProbe":{"resonanceCoefficient":12.25}},"version":1}
  [信息] 探针补丁记账：应用 {resonanceCoefficient=12.25}，跳过 {}
  [OK] ③ 新字段：(b) 反射桥（= /data merge 那条路）真的把它写进去了 —— [resonanceCoefficient]
  [OK] ③ 新字段：(b) 写进 JSON 真的生效 —— resonanceCoefficient 3.5 → 12.25
  [OK] ③ 新字段：(b) 回读也看得见（dump 出来是 12.25，/data get 同一个口径）
  [信息] 对照：同一条配置走 EntityDataPatcher（手写键表）→ 应用 []，跳过 [probe:configReflectionProbe → base.resonanceCoefficient：未知键（这个键不属于实体数据，已经忽略）]
  [OK] ③ 对照：同一条配置走现在的手写表那条路会被报成「未知键」（证明探针字段真的没被任何手写清单认识）
  [OK] ③ 新字段：探针已从注册表移除（不污染后面的用例）
```

**那条"对照"是本实验最有说服力的一条**：**同一条配置**，反射路 `应用 1 项`，
手写表那条路报 `未知键`。两者之差就是"1 行 `spec(...)`"，而反射路把它消掉了。

**"改了几处"的数字（实验唯一真正重要的数字）**：

| 加一个全新的可配置实体字段 | 要改几处 | 说明 |
|---|---|---|
| **反射路（本次实验）** | **0 处配置层改动** | 只加了一个 Java 字段 `resonanceCoefficient` + 它的 getter/setter（这是"加一个属性"本来就要写的，不算配置层） |
| 表驱动路（§十 的正式实现） | **1 个文件 1 行** | `EntityKeySpecs` 里加一行 `spec(name, group, kind, read, write, note)` |
| 表驱动路（§二 重构前） | 4 个文件 9~10 处 | 五行类字段 5 个文件 27~28 处 |

### 11.5 硬约束核对（逐条）

| 约束 | 状态 | 证据 |
|---|---|---|
| `/data` 键名一个都不许变 | ✅ | `DataBridge` 原有逻辑一行没动，只**新增**两个公开方法；`@NoConfig` 不参与 `dataFields()`。自测 702/0（含既有 122 条键名契约断言） |
| 表驱动路不许删、不许改坏 | ✅ | **一行没删**；`EntityKeySpecs` / `KeySpec` / `SpecPatcher` / `SpecWriter` / `ConfigDefaultWriter` / `EntityDataPatcher` 全部原样，自测里那条"同一条配置走手写表被报未知键"正好证明它还活着 |
| `config/gameConfig/` 6 份真 JSON 一个字不许动 | ✅ | 本轮改动前后**逐一 SHA256 比对，6 份全部相同**（`EntityData.json` `0A73334B…` / `SkillData.json` `C324E3D6…` / `GameRules.json` `B6EA37A9…` / `TagConfig.json` `63DA09A5…` / `EntityData.default.json` `A7EB3C8E…` / `PropertyConfig.json` `C6CF234F…`）。自测全程在内存里解析 JSON，`config/data/` 是 13:18 就存在的旧目录 |
| 模组契约不破、`mods/` 一个字不改 | ✅ | `mods` 13 个 `.java` 一个字节没动；`@ModConfig` / `ModDataAware` / `config/data/<modid>.json` 那条路的代码没碰，`testModConfig` 全绿 |
| 不许用 `Get-Content -Raw` + `WriteAllText` 改 `.java` | ✅ | 全程用 `edit` / `write`（UTF-8 文本工具） |

### 11.6 如果"全面切换"，要付什么代价（**这是决定要不要切的关键**）

切换 = 把 §十 那张手写表换成"反射面 + 一批 `@NoConfig`"。**能拿到**：新字段 0 处改动、
"声明了没人读"这一整类 bug 在结构上不可能存在（读与写是同一个字段）、默认值不用登记。
**要还三笔债**，逐笔给数字（都是本轮实测）：

**债 1：反射面比"该给人配的"大，多出来的键要逐个判断（实测 24 个）**

```
  [数字] 全面切换的代价（反射面 vs 现在的手写表）：
         · 手写表 31 个键（含 manaGrow 块与 inventorySlots）
         · 反射面 53 个键
         · 只在表里、反射拿不到的（2 个）：[inventorySlots, manaGrow]
         · 只在反射里、表里没有的（要逐个写 @NoConfig 关掉，24 个）：
           [attackEnhanceAmount, attackEnhancePercent, criticalDMGEnhanceAmount, criticalDMGEnhancePercent,
            criticalRateEnhanceAmount, criticalRateEnhancePercent, defenceEnhanceAmount, defenceEnhancePercent,
            dirtDamageEnhance, dirtPenetration, extraDamage, fireDamageEnhance, firePenetration,
            hpEnhanceAmount, hpEnhancePercent, individualMultipleArea, metalDamageEnhance, metalPenetration,
            speedEnhanceAmount, speedEnhancePercent, waterDamageEnhance, waterPenetration,
            woodDamageEnhance, woodPenetration]
         · 其中「非标量、由规则自动排除」的 9 个（不需要注解）
         · 真要人判断的（标量但没进手写表）＝ 24 个
```

这 24 个不是随机噪声，而是**三类有语义的字段**，切换时必须逐类决定：
① 10 个五行 `*Penetration` / `*DamageEnhance` = **临时属性**（战斗结束清零，配了只影响开局那一刻）；
② 10 个 `*EnhancePercent` / `*EnhanceAmount` = **运行时加成**（效果与技能在改，不是模板属性）；
③ 4 个 `extraDamage`（**死字段**，全项目零写入点）/ `individualMultipleArea`（角色构造函数自己算的倍率）。
**这正是"否定式"的代价**：默认开放很省事，但**每个字段都要想一次"它能给人配吗"**，
而**忘了想不会报错**，只会多一个能配的键。表驱动路是反过来：**默认关闭，忘了加就配不了**（更安全，但要手写一行）。

**债 2：反射拿不到的三样东西 —— 逐条点清"现在靠谁承载"**

| 现在靠手写表承载的 | 反射面为什么拿不到 | 切换后怎么办 |
|---|---|---|
| **分组语义（`base` / `derived`）** | `SpecPatcher.lookup()` 认 `{"base":{…}}` / `{"derived":{…}}` 两个子块（`DataKeys.BARE_SECTIONS`），`EntityDataPatcher` 还靠它**定顺序**：`level` 最先、`hp` 最后（`setHp` 会夹到 `getHpMax()`）；反射只有"字段声明顺序" | **必须保留一张"子块名"小表**，并把"顺序"写进文档/断言；`hp` 必须在 `hpMax` 之后这条**反射给不了保证**（现在是靠表的顺序） |
| **默认值来源** | 表里 `read` 允许返回 `null`（= 这个键没有默认值，例如 `elementSort` 为 null 时不写）与 `ConfigDefaultWriter::descriptionForFile` 这种**改名/加工**（`description` 写文件时换个写法，`manaGrow` 写五个扁平键而不是一个块） | 加工逻辑**必须留在外面**（要么 `@NoConfig` 掉、要么在生成器里特判）。反射只能给"字段当下的值" |
| **`/data` 面比"该给人配的"大** | 见债 1 的 24 个 | 24 个 `@NoConfig`（或改成分组白名单，那就又回到手写表） |
| **`manaGrow` 块 + `inventorySlots`** | 这**两个键不在 `/data` 里**（`DataKeys.NOT_IN_DATA`）。反射面上根本没有它们 | `manaGrow` 块可以由"数据名拼接"下钻覆盖（`manaGrow.fire` == `fireManaGrow`，本次实验实现了）；**`inventorySlots` 覆盖不了** —— 它要判"末尾格子是不是空的"，**必须留一个专用分支** |
| **别名（`element` → `elementSort`）** | 反射的键名就是数据名，没有别名概念 | 要么放弃别名（**会破坏用户现有配置**），要么保留一张别名小表 |
| **派生值重算** | `setHpGrow` 只赋值，三围要 `setLevel` 才重算；反射无从判断"哪些字段碰了派生值"（现在是 `touchesDerivedStats()` 一张清单） | **必须保留**这张清单，或者改成"任何标量变更后都重算一次"（行为会变，风险大） |

**债 3：新路还没接进正式入口（1 处小改，没做）**

`EntityDataPatcher.patch()` 现在只遍历手写表；让它也走反射桥需要：
① 加一次 `ReflectionConfigBridge.patch(...)` 调用；② 让 `reportUnknownKeys` 放行反射桥处理过的键。
**为什么没做**：那会**改变现有报错行为**（`resonanceCoefficient` 这种键从"未知键"变成"静默生效"），
而用户还没决定要不要切 —— 硬约束第 2 条要求"现有的表驱动路径不许改坏"，
所以本轮**只并列了一条新路**，两条各自能跑，切换与否留给用户拍板。

### 11.7 结论（三件事逐条）

| # | 要证明的 | 结论 | 最硬的那条证据 |
|---|---|---|---|
| ① | 反射驱动：配置一行不改，字段就出现并生效 | **成立** | `{"criticalRate":0.125,"metalResistance":0.5,"speed":127}` → 三个都变（含 `@DataFlatten` 组件里的 `metalResistance`）；默认值 = 字段当下的值 |
| ② | `@NoConfig` 挡得住 + 新字段默认开放 | **成立** | `uuid`/`alive`/`presentTurn`/`probeState` 全部"跳过且值没动"；同一个 `probeState` 在 `/data` 的 `dataNames` 里照旧在 → 两个注解各管一面 |
| ③ | 造个新字段，看要改几处 | **成立，数字 = 0 处配置层改动** | 同一条配置：反射路 `应用 1 项`，手写表路 `未知键` |

**推荐（给用户拍板用）**：**不要全面切换，但可以考虑"混血"** ——
① 保留 §十 那张表管"编排"（顺序、子块、派生值重算、`inventorySlots` 这类块）；
② 把**键清单本身**（"有哪些键可配"）交给反射，表里那 26 行标量规格**删掉**、
只留 `@NoConfig` 的挡板 + 少量特判。
这样"加一个新字段"从 1 行变成 0 行，而债 1 的 24 个键会**当场在自测里现形**
（本次那条 `onlyReflection` 清单就是现成的守卫）。

### 11.8 复现命令（本节数字怎么重跑）

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force; & .\check-sources.ps1
$jdk = ".\tool_for_llm\zulu-25\bin"
$sources = @(Get-ChildItem -Recurse -Filter *.java .\src | ForEach-Object { $_.FullName })
& "$jdk\javac.exe" -encoding UTF-8 -nowarn -d out\llmcheck -classpath .\lib\json-20231013.jar $sources
# 自测（直接跑；沙箱下不要接管道、不要 > 重定向到文件——用 cmd /c 包一层可以）
cmd /c ".\tool_for_llm\zulu-25\bin\java.exe -Dfile.encoding=UTF-8 -cp .\out\llmcheck;.\lib\json-20231013.jar cn.gfhnv.debug_tools.TestCommandSystem > out\selftest.log 2>&1"
Select-String -Path out\selftest.log -Pattern "反射驱动配置" -Context 0,45   # 本节全部输出
```

### 11.9 回滚入口

新路是**并列**的，回滚 = 删 3 个新文件（`NoConfig.java` / `ReflectionConfigBridge.java` /
`ConfigReflectionProbeEntity.java`）+ 撤掉 `DataBridge` 那 81 行新增 + 撤掉 `TestCommandSystem`
里那 1 个方法与 1 行调用 + 撤掉 `Thing` / `LivingThing` 上的 8 个 `@NoConfig`（探针那个随文件一起删）。
**表驱动路与 `/data` 契约完全不受影响**（本轮没动它们一行）。

---

## 十二、「反射混血」三步落地记录（2026-10-03，用户拍板，AI 实做）

> **用户拍板的是什么**（读完 §11 之后）：**改回"反射混血"路线** ——
> **键清单交给反射，编排逻辑（顺序 / 子块 / 重算 / 块结构 / 别名）留手写**。
> 同时**否决**了「表定义 + 代码生成」（崩铁式）那条路，否决记录写在
> `TABLE-DEFINITION-CODEGEN-2026-10.md` 的文件最顶上（**别再提那条路**）。
>
> **一句话结论**：三步各自「`CHECK OK` + `javac` exit 0 + 自测失败 0 条」；
> **第 3 步只做到"接线"，"删表"没做**（理由见 §12.5，**不是漏做，是评估后判断风险 > 收益**）。
> 自测 **702 → 707/0**（+5 条断言），`Java files: 224`（**一个文件都没新增**）。
>
> **⚠️ 2026-10-03 晚补一轮（"把没有使用到的东西删了"）**：用户把「删表」的范围**重定成"只做死代码清理"**，
> 于是 §12.5 被重新走过一遍 —— 逐项 grep 数过引用之后，**标量规格仍然一行没删**
> （结论：表里的 read/write lambda 就是通用补丁器的**应用路径**，反射路是**兜底**不是替代），
> 真正删掉的是两个**零引用**的死物（`DataKeys.SkillKeys.ELEMENT_UNIVERSAL` 与
> `debug_tools/TestAnticipateDamage` 整个文件），另把参考副本的伪数据键改成 `_note` 并补齐技能段结构。
> **实测引用计数、方法与一条"别拿正则当死代码判据"的教训见 §12.5.1**；
> 参考副本那一半见 **§12.5.2**。自测 **822 → 827/0**，`Java files: 227 → 226`。

### 12.1 三步与各自的验收数字

| 步 | 做了什么 | 验收 |
|---|---|---|
| 0（硬闸） | 先证明"AI 这一侧能编译"（上一个子代理被沙箱拒过 `javac`） | `javac exit 0`（308 个 class） |
| **1** | 把 §11.6 那份**人看的** `onlyReflection` 清单变成**断言** | `CHECK OK` / 224 / **704/0** |
| **2** | 那 24 个逐个分类 → 全部 `@NoConfig` + 真理由；订正 1 句假文案 | `CHECK OK` / 224 / **705/0** |
| **3** | **接线**：`ReflectionConfigBridge.patchExtra` → `EntityDataPatcher.patch`；新增 `CLASS_STATE` 闸门；段定位断言 | `CHECK OK` / 224 / **707/0** |
| 3′ | **删表（没做）** —— 见 §12.5 | — |

三步的**代码改动**（`config/gameConfig/` 5 份真 JSON、`mods/` 4 个模组、`/data` 键名**全都没动**）：

| 文件 | 改了什么 |
|---|---|
| `TestCommandSystem.java` | 新增 `REFLECTION_ONLY_SCALAR_KEYS`（24 键现形清单）+ ⑤ 混血不变式 + ⑥ 守卫两条 + ⑦ 切换语义两条；⑤ 里那条"债还在"的断言翻成"债已还"；③ 那条"走手写表会报未知键"的对照翻成"走正式入口真的生效"；订正 1 条断言文案 |
| `LivingThing.java` | `extraDamage` / 12 个 `*Enhance*`（2 行多声明）/ `individualMultipleArea` 共 **14 个字段 + 2 条共用理由常量**写 `@NoConfig` |
| `AttributeProfile.java` | 10 个五元素临时属性（5 穿透 + 5 增伤）写 `@NoConfig`（共用 `TEMPORARY_REASON`）+ `import NoConfig` |
| `DataKeys.java` | 新增 `CLASS_STATE`（明确排除的 26 键，从自测的 `SUBCLASS_STATE_KEYS` 搬进来当**唯一真相**）+ 订正五元素那 10 条提示文案 |
| `ReflectionConfigBridge.java` | 新增 `patchExtra(...)` / `writeExtra(...)`（第 3 步的集成点） |
| `EntityDataPatcher.java` | `patch()` 里加一次兜底调用；`reportUnknownKeys(...)` 收一个 `handled` 集合（放行被处理过的键） |
| `README.md` | `PropertyConfig.json` 那一行改成"**已经不存在了**"（它 2026-10 就删了，README 还在提） |
| `TABLE-DEFINITION-CODEGEN-2026-10.md` | 顶上加了"**已评估、用户否决**"的否决记录 |

### 12.2 第 1 步：那份清单现在是断言（零行为变化）

**问题**：§11.6 那 24 个键是**打印出来给人看**的（`onlyReflection` 只是一个 `TreeSet`），
所以"加一个新字段"仍然可以悄悄多出一个可配置键。

**做法**：把那 24 个**逐个写进** `TestCommandSystem.REFLECTION_ONLY_SCALAR_KEYS`（带分类注释），
然后加两条断言：

1. **逐字相等**：`反射面 − 手写表 − @NoConfig == 这 24 个`（多一个少一个都红，实测相等）；
2. **反射面 ⊆ 允许清单**，跑**全部 9 个模板**（不只 `playerOne`）。

**允许清单的构成（三段，写清在断言旁边）**：

| 段 | 来源 | 实测规模 |
|---|---|---|
| ① 能配的 | `EntityKeySpecs.ALL` 那张手写表 | 31 键（含 `manaGrow` 块与 `inventorySlots`） |
| ② 加 `@NoConfig` 的 | 字段上的注解（**含 `@DataFlatten` 组件里的**，所以必须按**实例**口径扫） | 第 1 步 17 键 → 第 2 步 41 键（其中 9 个非标量由规则自动排除） |
| ③ 明确排除的 | `DataKeys.READ_ONLY`（37）+ `CLASS_STATE` / `SUBCLASS_STATE_KEYS`（26） | 63 键 |

**实测输出**（`out/selftest.log`）：

```
  [数字] 反射面 vs 手写表（第 1 步的实测口径 + 第 2 步之后的现状）：
         · 手写表 31 个键（含 manaGrow 块与 inventorySlots）
         · 反射面 29 个键                      ← 第 2 步之后（加注解之前是 53）
         · 只在表里、反射拿不到的（2 个）：[inventorySlots, manaGrow]
         · 只在反射里、表里没有的（第 1 步实测 24 个，第 2 步逐个写了 @NoConfig，现在是 0 个）
  [OK] ⑤ 混血不变式（第 2 步之后）：反射面（candidates 口径）== 手写表的标量键
       （31 键 − manaGrow 块 − inventorySlots）—— 29 vs 29，「只在反射里」的 0 个
  [OK] 守卫（第 2 步）：反射面 − 手写表 − @NoConfig 现在是空集 —— 实扫 []
  [OK] 守卫（第 2 步）：第 1 步现形的那 24 个键全部落在 @NoConfig 清单里 —— 实扫 [24 个]
  [OK] 守卫：每个模板的反射面都在允许清单里（新加标量字段没表态就红）—— 漏登记 []
  [守卫] 覆盖范围＝注册表里全部 9 个模板（不只 playerOne）
```

⚠️ **一条口径订正**：§11.6 把 24 个分成"10 + 10 + 4"，实际是 **10 + 12 + 2**
（`*Enhance*` 是 **12** 个：8 个百分比 + 4 个固定值；第三类只有 `extraDamage` 与
`individualMultipleArea` **两个**）。本节以实测清单为准。

### 12.3 第 2 步：那 24 个的**分类结果**（逐个判断，一个都没漏）

**结论：24 个里 `能配` = 0 个，`@NoConfig` = 24 个，`从反射面排除`（非标量规则）= 0 个。**
三个分类桶里，只有"加注解"这一个用得上 —— 因为每个键都能给出**"配了没用"或"为什么不给配"**的证据：

| 类 | 键（个） | 判断 | 依据（写进了代码注释，不是猜的） |
|---|---|---|---|
| ① 五元素穿透 / 增伤 | `metal/wood/water/fire/dirt` × `Penetration` / `DamageEnhance`（**10**） | **不能配**（`@NoConfig`） | **临时属性 + 配了不生效**：`AttributeProfile#copyFrom` **刻意不带**这 10 个（副本从干净状态开始）→ 模板上的值**进不了任何一局战斗**；每局结束还被 `resetTemporary()` 清零；全项目**零写入点**（`DamageCalculate` 只读，预留未接线） |
| ② `*Enhance*` 运行时加成 | `attack/defence/speed/hp/criticalDMG/criticalRate` × `Percent` / `Amount`（**12**） | **不能配**（`@NoConfig`） | **效果与技能"进来加、走时减"**（6 个通用强化效果 + 白厄变身自己加减），战斗结束由 `clearTemporaryAttributes()` 兜底清零；且**复制构造器不带**这 12 个 → 配在模板上不生效 |
| ③ 死字段 | `extraDamage`（**1**） | **不能配**（`@NoConfig`） | 参与伤害公式但**全项目零写入点**（永远 0）；活的那一套是 `Skill#extraDamage`，是**另一个字段** |
| ④ 派生面板倍率 | `individualMultipleArea`（**1**） | **不能配**（`@NoConfig`）—— **这一条是"判断"，不是"配了没用"** | 它是**面板属性**（复制构造器会带、战斗不清零），由**角色构造器**按玩法算（`ActorLiXiaoYan` 按燃点）→ 放开它等于让配置**覆盖角色自己的算法**，还会让默认配置文件多出一个键。要改那个算法请改角色构造器 |

**⚠️ 顺手订正的一句假文案（真缺陷）**：`DataKeys` 里给五元素穿透/增伤的提示原文是
「临时属性：第一局结束就被清零…**配在这里只影响开局那一刻**」——
**后半句是假的**：副本根本不带它，所以配了**连开局都不影响**。
现在文案改成「临时属性，而且**配了不生效**：副本刻意不带它…」，自测那条断言也跟着钉住
`配了不生效` 四个字（顺带证明"`@NoConfig` 的理由必须是真的"这条纪律能被自测抓住）。

**"否定式的代价"这次实际付了多少**：24 个字段要逐个想一次"它能给人配吗"，
但**没有一个是"想不出来"的** —— 每个都有代码里的生命周期规则当依据
（`copyFrom` / `resetTemporary` / `clearTemporaryAttributes` / 零写入点）。
这正是 §11.6 债 1 说的那件事；**写完以后它不再是债**：那份清单进了断言，新字段不表态就红。

### 12.4 第 3 步（做了什么）：把反射路**接进正式入口**

**集成点**（§11.6 债 3 说的"1 处小改"，现在做了）：

```
EntityDataPatcher.patch(target, patch, report, id)
  ① 手写表 BEFORE_DERIVED（顺序 / 类型收敛 / 报错文案 —— 一个字没变）
  ①-b ReflectionConfigBridge.patchExtra(...)   ← 本次新增：表里没有、反射面认识的键在这里生效
  ② manaGrow 块 + inventorySlots（两个块分支，没变）
  ③ 派生值重算（没变）    ④ 手写表 DERIVED（hp 最后，没变）
  ⑤ reportUnknownKeys(..., handled)            ← 收一个 handled 集合，放行被处理过的键
```

**四条性质，逐条给证据**：

| 性质 | 怎么保证的 | 证据 |
|---|---|---|
| **幂等**：表里有的键不会被打两次 | `patchExtra` 第一步就跳过 `DataKeys.isConfigurable(key)` 与别名 `element` | 自测 705→707 全绿（所有既有的"逐键打补丁"用例都没变） |
| **段定位没丢**（§11.6 债 3 点名的那件事） | 子块名是**编排信息**，反射面拿不到 —— 所以由 `EntityDataPatcher` 遍历 `BARE_SECTIONS`、把 `base.` / `derived.` 前缀传给 `writeExtra` | 新断言 ⑦：写 `{"base":{"resonanceCoefficient":7.5}}` → 记账是 `base.resonanceCoefficient` |
| **未知键照旧报** | `patchExtra` 认不出来就**一个字都不报**，静静放手交给 `reportUnknownKeys` 用它原来的文案与段前缀去说 | 既有断言（`base.nope` / `extraDamage` / `temporary` 块）全部照旧通过 |
| **新字段 0 处配置层改动 → 正式入口** | 探针 `resonanceCoefficient`（`DataKeys` / `EntityKeySpecs` / 生成器 / 补丁器都不知道它）现在经 `EntityDataPatcher.apply` 真的生效 | 新断言 ③-b：`应用 [probe:configReflectionProbe → resonanceCoefficient = 12.25]，跳过 []`（**切换前**这条配置报的是"未知键"） |

**新增的一道闸门：`DataKeys.CLASS_STATE`（为什么非有不可）**

切换之后"能不能配"由反射面决定，于是**角色 / 怪物 / 召唤物类自己的状态键**
（`coreflame` / `phaseTwo` / `kind` / `painCost` / `ignition` …26 个）会**默认变成可配置** ——
那等于一份 `EntityData.json` 能把 boss 打成二阶段、把白厄的火种改掉。
所以把自测里原本就有的 `SUBCLASS_STATE_KEYS`（它一直是"新增就登记"的清单）**搬进配置层**
当**唯一真相**（自测那个常量现在直接引用它，不再两份），由 `patchExtra` 当闸门：

- 闸门内 → 不写（照旧报"未知键"，行为与切换前**逐字相同**）；
- 依据：`phaseTwo` **连 setter 都没有**，放行它就是 `notes_for_llm/70-DATA.md` §5.10 点名的
  **"裸写字段"**那个坑 —— 配置会**静默**改掉 boss 的进程状态。新断言 ⑦ 钉住这一点。

### 12.5 第 3 步（**没做**的那一半）：删掉 `EntityKeySpecs` / `SkillKeySpecs` 的标量规格

> **2026-10-03 晚：这一节被重新走过一遍（用户要求"把没有使用到的东西删了"）。**
> **重定的范围是「只做死代码清理，不做语义迁移」**，所以下面"三条阻碍"结论文档保留，
> 但整张表**这次是逐个 grep 数过引用之后才决定留的** —— 结论：**一行都没删，而且理由比原来更强**。
> 第 4 小节是这一轮的实测引用计数。

**诚实清单：那 26 行标量规格（`EntityKeySpecs` 29 行 − `manaGrow` 块 − `inventorySlots`）一行没删。**
`EntityKeySpecs` 仍然是"键的唯一定义处"，反射面只是**兜底**（表里没有的键才走它）。

**为什么不删**（三条都是具体阻碍，不是"没时间"）：

| # | 阻碍 | 具体是什么 |
|---|---|---|
| 1 | **`note` 反射拿不到，而它被断言要求非空** | `DataKeys.META` 的 `note` 来自 `KeySpec`（自测要求每个键都有说明，`META` 还被生成器/文档用）。字段名与类型能反射，**"一句话说明"不能**。要删表就得把 note 搬到字段注解（`@ConfigNote`）里 —— 那是**另一种**每字段一行的清单，只是换了位置 |
| 2 | **三条编排真的只有手写能做**（§11.6 债 2 点过，本轮复核仍然成立） | ① `elementSort` 的写要**连带 `initialMana()`**（重建法力表）—— 通用 setter 调不出这一步；② `description` 生成时要走 `ConfigDefaultWriter::descriptionForFile`；③ 生成器的**键顺序**现在是表的顺序（`level` 最前、`hp` 最后、五行成组），改成字段声明顺序会让**默认文件与参考副本的排版变样**（`/data` 键名不受影响，但用户看得见） |
| 3 | **收益已经拿到了** | 用户要的"加一个字段 = 0 处配置层改动"**在第 3 步的接线里已经成立**（§12.4 最后一行）。删表**不再带来任何行为收益**，只带来上面 1、2 两条风险 |

#### 12.5.1 「删表」重定范围后的实测引用计数（2026-10-03 晚，逐项 grep `src` + `mods`）

**方法**：对每个候选数"全项目命中次数"（含 `mods/` 13 个源码、含自测；`check-sources.ps1` 只编 `src`，
所以 `mods/` 要单独数一遍）。**判据：只有"零引用"才删；拿不准的一律留。**

| 候选 | 引用计数 | 结论 | 依据 |
|---|---|---|---|
| **`EntityKeySpecs` 的标量规格（26 行）** | **表本身被 12 处活引用** | **全留** | `BEFORE_DERIVED`：`EntityDataPatcher:178` + `ConfigDefaultWriter:583`；`DERIVED`：`EntityDataPatcher:199` + `ConfigDefaultWriter:662`；`ALL`：`DataKeys:788/795`（→ `META`/`groups()`）+ 自测 3 处；`meta()`/`groups()`：`DataKeys` 2 处 + 自测 2 处；`setManaGrow`：`EntityDataPatcher:447/665`。**表里的 read/write lambda 就是通用补丁器的应用路径**，"反射路已经覆盖标量"说的是**兜底**，不是**替代** |
| **`SkillKeySpecs` 的标量规格（6 行）** | **`SCALARS` 被 4 处活引用** | **全留** | `SpecPatcher.patch(…, SkillKeySpecs.SCALARS, …)`（`SkillDataPatcher:271`）+ `ConfigDefaultWriter:783/795/866`；`isKnown`/`of` 遍历 `ALL`（`SkillDataPatcher:495`、自测 `:3792`） |
| `ConfigLoader.getEntityDataFile()` / `getSkillDataFile()` / `getGameRulesFile()` / `getTagsMap()` | **0**（含自测、含 `mods/`） | **早已删**（不是这一轮） | 全项目只剩私有的 `tagsMap` 字段与它内部 5 处使用；**四个 getter 在源码里已经不存在**（D10/D13 记的就是它们当时被删） |
| `DataKeys.GROUP_DERIVED` | **1 声明 + 4 使用 = 5** | **留（上一轮的"零引用"说法已过期）** | `EntityKeySpecs:49/52/55/58` 构造 `DERIVED` 时逐个用它。上一轮记的"全项目只有声明处 1 次命中"指的是**当时**；现在它是那张表的**活分组常量** |
| `DataKeys.SkillKeys.ELEMENT_UNIVERSAL` | **1（就是声明处）** | ✅ **本轮删除** | 全项目零引用：解析消耗元素走的是 `ElementSort` 枚举（`EntityDataPatcher.elementOf`），从来没人用这个字符串常量。`mods/` 也是 0 |
| `debug_tools/TestAnticipateDamage` | **1（就是自己）** | ✅ **本轮删除**（整文件 43 行） | 无 import、无引用；而它唯一的方法 `static void main()`（**缺 `public` 与 `String[] args`**）**根本不能由 `java` 启动** → 这个文件既进不了任何路径、也跑不起来。提到它的只有 `README.md` / `PROJECT-ANALYSIS-2026-09.md` / `ANALYSIS.md` 的**文字描述**（已同步改成历史记录） |
| `SkillKeySpecs.BLOCKS` / `ALL`、`DataKeys.CLASS_STATE`、`TestCommandSystem.SUBCLASS_STATE_KEYS` | 都有活引用 | **留** | `BLOCKS` 经 `buildAll()` 进 `ALL`；`ALL` 被 `isKnown`/`of` 遍历；`SUBCLASS_STATE_KEYS` 是 `DataKeys.CLASS_STATE` 的别名（上一轮刚统一成一份真相） |
| `debug_tools/actionBarTest/*`（5 个文件） | 包内自洽（`TestMain` 引用 `TestTurnManager`/`TurnPastListener`） | **留** | 与 `TestAnticipateDamage` 不同：它是**成套的回合制原型测试**，`PROJECT-ANALYSIS-2026-09.md` 明确把它列为组成部分。本轮不在点名范围内，不动 |

**⚠️ 一条方法论教训（值得记）**：**别用"正则扫 public 方法名 + 数出现次数"当死代码判据**。
本轮试过一次，报出 ~140 个"零调用"方法，**绝大多数是假阳性** ——
方法引用（`LivingThing::setHpMax`、`ConfigDefaultWriter::descriptionForFile`）与
反射调用（`DataBridge` 按字段名拼 setter）都不会长成 `name(`。
真正的判据是"**逐个 grep 计数，再人工看一眼命中处是不是方法引用**"。

#### 12.5.2 参考副本的说明键与结构完整性（2026-10-03 晚，同一轮）

`EntityData.default.json` 的**伪数据键**（键名是一整句中文
`"skills（SkillData.json 的内容，游戏不读这一段）"`）已删，改成两个动作：

1. **段名回归惯例**：技能段现在就叫 `skills`（复用 `ConfigDefaultWriter.SKILLS`，
   与 `SkillData.json` 同名同构）；说明文字搬到根上的 **`_note`**（下划线开头 = 不是数据）。
   契约见 `notes_for_llm/70-DATA.md` 的 **§5.10.5**；
2. **补齐结构**：它以前**漏了 `tags`**、而且是靠"整段不要"来表达 `consumedMana` ——
   读者分不清"这个技能不消耗法力"和"这一段我没展开"。现在技能段改用
   `appendSkillsFull(...)`（与 `SkillData.json` 同一套采集/写法），
   `consumedMana`（块写法 **或** `null` = 取消消耗）与 `tags` 都在里面；
   顺带**删掉了只服务旧写法的 `collectSkillValues()`**（43 行，改完零引用）。

**它不会被加载器读**（所以 `_note` 不会被当成"未知键"报警）：全项目读这个文件的代码只有
`ConfigDefaultWriter#hasEntities(file)`（判"有没有 `entities` 段且非空"）。
证据：`ConfigLoader` 里它只出现在路径常量 `ENTITY_DATA_REFERENCE_FILE` 与
`healDefaultConfigFiles()` 的 `writeReferenceIfNeeded(...)` 一处；
三个补丁器（`EntityDataPatcher` / `SkillDataPatcher` / `GameRulesPatcher`）的"未知键"报警
只对各自那份**真配置**生效。

**下一步若要继续删表，建议顺序**（每步单独跑一次自测，别一次动完）：

1. 把 `note` 挪成字段注解（新注解 `@ConfigNote`，否定式：不写 = 空说明 → 自测红），
   跑一次自测确认 `META` 与生成物**逐字不变**；
2. 把 `read` / `write` 换成"反射 setter + 少量特判"（`elementSort` / `description` 两个特判明写在一个方法里），
   顺序改成"`level` 最先、`hp` 最后、其余按字段声明顺序"，**跑一次自测 + 比一次生成物文本**；
3. 最后才删表里的标量行。

### 12.6 硬约束核对（第 1–3 步合计）

| 约束 | 状态 | 证据 |
|---|---|---|
| `/data` 键名一个都不许变 | ✅ | `@NoConfig` / `CLASS_STATE` **都不参与 `DataBridge.dataFields()`**（配置面与 `/data` 面各管一面）；自测 707/0（含 `/data` 那 134 个键名断言点与"dump 契约"用例） |
| `config/gameConfig/` 5 份真 JSON 一个字不许动 | ✅ | 本轮**全程只读**它（`LastWriteTime` 仍是 14:44，自测只用内存里解析的 JSON 与 `out/` 下临时目录）；顺手把 `README.md` 里那句"`PropertyConfig.json` 已废弃"改成"已经不存在了" |
| 模组契约不破、`mods/` 4 个模组一个字不许改 | ✅ | 本轮没碰 `mods/`；`testModConfig`（29 条）全绿 |
| 13 参构造器签名 / public getter-setter 不动 | ✅ | 只加注解与常量，没动任何方法签名；构造器契约断言全绿 |
| 中文注释 UTF-8、不用 `Get-Content -Raw` + `WriteAllText` 改 `.java` | ✅ | 全程用 `edit` 工具（UTF-8 文本） |
| 行尾跟邻居一致 | ✅ | 见 §12.7 的收尾（改完逐个文件核过 CRLF/LF） |
| 自测条数只能涨不能跌 | ✅ | **702 → 707**（+5），失败 0 |

### 12.7 回退方式（三步各自的回退入口）

**第 1 步（守卫）回退**：删 `REFLECTION_ONLY_SCALAR_KEYS` 与 ⑥ 那两条断言（⑤ 里那条
"债已还"的断言要同时翻回 `!onlyReflection.isEmpty()`）。**没有任何运行期影响**。

**第 2 步（24 个注解）回退**：撤掉 `LivingThing` 的 3 处注解（+2 条理由常量）、
`AttributeProfile` 的 10 处（+1 条理由常量与 `import`），并把 `DataKeys` 那 10 条文案改回原文。
⚠️ **回退前想清楚**：注解一撤，第 3 步的接线就会让这 24 个键**变成可配置**（这正是第 2 步存在的理由）。

**第 3 步（接线）回退** —— 想回到"反射路只是一条并列的新路"：

1. `EntityDataPatcher.patch`：删掉 `applied.addAll(ReflectionConfigBridge.patchExtra(...))` 那一行；
2. `reportUnknownKeys`：把签名与两处 `handled.contains(...)` 判断撤掉（回到 3 参版本）；
3. `ReflectionConfigBridge`：`patchExtra` / `writeExtra` 可以留着（没人调用就是死代码）或一起删；
4. `DataKeys.CLASS_STATE` 与自测的 `SUBCLASS_STATE_KEYS`：**建议保留**（它替的是原来那份自测清单，
   删了要把 26 个键抄回自测里）；
5. 自测里 ③-b 与 ⑦ 两类断言要翻回"报未知键"的版本。

**恢复顺序**：`check-sources.ps1` → `javac`（exit 0）→ 自测（`失败 0 条`，条数 ≥ 702）。

### 12.8 复现命令（本节数字怎么重跑）

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force; & .\check-sources.ps1
$jdk = ".\tool_for_llm\zulu-25\bin"
$sources = @(Get-ChildItem -Recurse -Filter *.java .\src | ForEach-Object { $_.FullName })
& "$jdk\javac.exe" -encoding UTF-8 -nowarn -d out\llmcheck -classpath .\lib\json-20231013.jar $sources
cmd /c "$jdk\java.exe -Dfile.encoding=UTF-8 -cp .\out\llmcheck;.\lib\json-20231013.jar cn.gfhnv.debug_tools.TestCommandSystem > out\selftest.log 2>&1"
$lines = [System.IO.File]::ReadAllLines((Resolve-Path .\out\selftest.log), [System.Text.Encoding]::UTF8)
$lines | Where-Object { $_ -match "守卫|混血不变式|切换（第 3 步）|明确排除：" }   # 本节全部输出
```