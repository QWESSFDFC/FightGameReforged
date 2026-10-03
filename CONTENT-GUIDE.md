# CONTENT-GUIDE.md —— 往游戏里加内容（实体 / 技能 / 规则 / 模组）与"配置能改到什么"

> **这份文档给谁看**：**往游戏里加东西的人** —— 也就是"既是策划又是程序"的你，以及下一个接手
> `src/` 的 AI。它回答两个问题：
> 1. **今天这套外部数据加载，现在到底能用了什么、什么是打折扣的？**（第一节，先看这个）
> 2. **加一个新实体 / 新技能 / 新规则 / 新模组时，配置要怎么走？**（第二~五节，照着做）
>
> **它不重复已有的两份文档**：
> - `MODDING-GUIDE.md` 是给**模组作者**的（模组目录、`main.json`、`Mod` API、四类内容模板）；
> - `project_analyses/EXTERNAL-DATA-LOADING-2026-10.md` 与 `CONFIG-LOADING-DECOUPLING-2026-10.md`
>   是**设计与施工记录**（为什么这么做、踩了什么坑、每一步的验收数字）。
>
> 本文是**操作手册**：从"我要加 X"出发，给到"改哪几个文件 → 跑什么命令 → 看到什么才算成功"。
>
> **日期**：2026-10-03。**当时的基线**：`check-sources.ps1` → `Java files: 226` / `CHECK OK`；
> 自测（`test-command-system.ps1`）→ **`通过 827 条，失败 0 条`**
> （本文初稿时是 225 文件 / 769；之后「技能注册表」+19 → 788，
> 再之后「`/data` 嵌套引用只给 uuid + 攻击行带短标识」+9 → 797，
> 「技能 id 覆盖率 + 短标识颜色」+10 → 807，
> 「收敛启动播报 + 修血量提醒误报 + 扫探针」+15 → 822（那一轮新增 1 个文件 → 227），
> 最后「死代码清理 + 参考副本 `_note`」+5 → **827**（那一轮**净减 1 个文件** → **226**））。
>
> ⚠️ **2026-10-03 起启动播报变了**：正常启动**只剩一行汇总**（下面 §1.0 / §1.4 / §2.6 / §3.3 里
> 那些"看到 `[配置] xxx.json：应用 N 项…`"的说法，现在统一指**那一行汇总里的对应数字**）；
> 想看逐条明细要开 `-Ddsh.config.verbose=true`（详见 `notes_for_llm/70-DATA.md` 的 **§5.10.4**）。
>
> ⚠️ **2026-10-03 晚起参考副本 `EntityData.default.json` 的格式变了**（与"能不能配"无关，但读它时要知道）：
> 根上多了说明键 **`_note`**、技能段段名回正成 `skills`、并补齐了 `tags` 与 `consumedMana` 两个块
> —— 契约见 `notes_for_llm/70-DATA.md` 的 **§5.10.5**。
> **你手上那份（14798 字节）不会自动变**（它已经有内容 → 导出物一个字节都不动），
> 旧的中文伪键要等你自己删掉整个文件、让下次启动重写才会消失。

---

## 一、现在什么能用了、什么打折扣、什么配不到

### 1.0 一句话总览

**能用了。** 数值现在真的能从 `config/` 里改，而且**已经由你实跑验证过链路**。
2026-10-03 起**正常启动不再逐份播报**，改成结尾一行总量（你那次的数字长这样）：

```
[配置] 已加载 GameRules.json 29 项、TagConfig.json 命中 6 个实体、1 个物品、EntityData.json 307 项、SkillData.json 281 项，应用 624 项，跳过 0 项，影响 10 个模板 / 33 个技能
```

（`EntityDataPatcher.Report#print` 与 `ConfigOutput#printPatchSummary` 一起打出这一行。
你那两次实跑分别落在 `config/gameConfig/EntityData.json`（9892 字节）与
`SkillData.json`（10210 字节）上；改 `flameReaver` 的 `derived.hpMax: 7` 之后战斗里就是 `HP 7/7`。
⚠️ **想再看到逐份的「应用 N 项」明细**：`-Ddsh.config.verbose=true`，见 §1.4 第 1 条。）

三份配置文件、各管一段：

| 文件 | 管什么 | 键是什么 | 谁读它 |
|---|---|---|---|
| `config/gameConfig/EntityData.json` | **实体数值**（面板 / 派生 / 子类出厂值） | `entities` → **注册表完整 id**（`game_official_content:playerOne`） | `ConfigLoader.loadEntityData()` → `EntityDataPatcher` |
| `config/gameConfig/SkillData.json` | **技能数值**（三个倍率 / 目标数 / 冷却 / 消耗 / 权重 / 具名系数） | `skills` → **`<实体完整id>#<技能名>`** | `ConfigLoader.loadSkillData()` → `SkillDataPatcher` |
| `config/gameConfig/GameRules.json` | **跨实体的魔法数字**（面板公式基座 / 初始法力 / BOSS 旋钮 / 李晓焰规则） | **`段.键`**（`flameReaver.containerLimit`） | `ConfigLoader.loadGameRules()` → `GameRules`（静态表） |

加载时机（顺序即合并顺序，`GameMain.java:61-82`）：

```
loadGameRules()          ← 必须第一个：很多规则是"类加载时读一次"
World.addMod(官方内容)    ← 官方模板进注册表
ModLoader.模组初始化
EventBus.post(GameStartEvent)  → 每个模组 invokeWhenLoaded() → registerItself()
loadTagConfig()          ← TagConfig.json（AI 权重，目前对玩法零影响）
loadEntityDataConfig()   ← 打在"模板"上，每个模板一次
loadSkillDataConfig()    ← 同样打在模板的技能实例上
CommandManager.initialize()
```

> **一句话**：配置是**补丁**，不是覆盖。文件里**显式写出来**的键才生效，没写的保持代码里的值；
> 补丁打在**注册表模板**上，选人时 `copy()` 出来的副本白送（所以一定要在选人之前加载完）。

### 1.1 能用 ✅（写进去就生效，建议放心用）

| # | 类别 | 具体键 | 证据 |
|---|---|---|---|
| 1 | **面板基础值** | `name` / `level` / `mass` / `type` / `description` / `speed` / `elementSort`（别名 `element`） / `hpGrow` / `attackGrow` / `defenceGrow` | `EntityKeySpecs.java:118-147`（表里逐行声明的就是"能配"的唯一定义处）；你实跑"应用 300 项"即它们 |
| 2 | **五行抗性 ×5** | `fireResistance` / `waterResistance` / `metalResistance` / `woodResistance` / `dirtResistance` | `EntityKeySpecs.java:148-153`（**由 `ElementSort` 循环生成**，不再手写 `switch`） |
| 3 | **五行法力成长 ×5（扁平）** | `fireManaGrow` / `waterManaGrow` / `metalManaGrow` / `woodManaGrow` / `dirtManaGrow` | `EntityKeySpecs.java:159-164`；⚠️ 这一族**曾经声明了能配、其实没人读**（D8），现在真接上了 |
| 4 | **`manaGrow` 块** | `{"manaGrow":{"fire":20,…}}`（写在实体下、`base` 下或 `derived` 下都认） | `EntityDataPatcher.java:317-343`；⚠️ 它**曾经从来没生效过**（块名撞键名，D9），现在有专用分支 |
| 5 | **背包格数** | `inventorySlots`（`/data` 里**没有**这个键；减格子只从末尾删、遇到装着东西的格子会拒绝并报告） | `EntityKeySpecs.java:166-168`（登记）+ `EntityDataPatcher.java:374-409`（唯一有副作用的写入口） |
| 6 | **派生值（用固定值覆盖公式）** | `hpMax` / `attack` / `defence` / `hp` | `EntityKeySpecs.java:48-60`，**顺序 = 应用顺序**：`hp` 必须最后（`setHp` 会夹到 `getHpMax()`，`LivingThing.java:2241`） |
| 7 | **5 个面板属性** | `criticalRate` / `criticalDMG` / `enhance` / `penetration` / `defenseLoss` | `EntityKeySpecs.java:170-184`；**整局生效**（副本会带、战斗结束不清）。⚠️ 它们曾经是 D1 那批"声明了没人读"的假键，现在真接上了 |
| 8 | **子类出厂值（`classState` 段，7 个）** | `ignition`（李晓焰）/ `coreflame` `coreflame_max` `soulscorch` `scourge` `scourge_max` `extraAbilityTier`（白厄） | 走**反射面**（`ReflectionConfigBridge.patchExtra`，`EntityDataPatcher.java:184`），因为这张通用表要求每行对**任意** `LivingThing` 成立，而 `coreflame` 只在白厄上有。`{"classState":{"coreflame":18}}` 与裸键 `{"coreflame":18}` 都认 |
| 9 | **技能标量 6 个** | `aims`（0=自身 / -1=全体 / 正数=前 N 个）/ `coolDown` / `hpMagnification` / `atkMagnification` / `defMagnification` / `forEnemies` | `SkillKeySpecs.java:42-54` |
| 10 | **技能两个块** | `consumedMana`（`{"amount":120,"element":"FIRE"}`，写 `null` = 取消消耗）/ `tags`（AI 权重，**整块覆盖**） | `SkillKeySpecs.java:59-63` + `SkillDataPatcher.java:335/384` |
| 11 | **技能具名系数（N 个）** | 例：死星天裁的 `healRatio` / `scourgeCostCap` / `hitsPerScourge` / `finishMagnification`；反击的 `healRatio` / `extraHitMagnification` / `magnificationPerStack` / `extraHits`；`neededManaScale` | **扁平**写在技能对象里，与 `aims` 并列；名字由技能自己报（`SkillCoefficientTunable.coefficientValues()`）。**算式留代码，系数进配置** |
| 12 | **规则键 29 条**（5 段） | `formula.*`（5）/ `mana.*`（2）/ `flameReaver.*`（11）/ `insectBoss.baseHpMax`（1）/ `actorLiXiaoYan.*`（10）。⚠️ 其中 `flameReaver.baseHpMax` 与 `insectBoss.baseHpMax` **同时**也被 `EntityData.json` 的 `derived.hpMax` 管，两个入口都写时**派生键赢**并打印一行 `[配置提醒]`（§1.2 第 14 条） | `GameRules.json`（你现在手上那份 42 行是**全量**的）；取值接口是 `GameRules.getLong/getDouble/getInt` |
| 13 | **模组配置** | `config/data/<模组id>.json` 的自己的分组 + `common` 共享区 | 见第五节；`common` **支持 `entities` 与 `skills`**，**不支持规则段**（会打印一句明确的"改不了 + 去哪儿改"） |
| 14 | **白厄那 5 个觉醒技能**（2026-10-03 起 ✅） | 键与别的技能一样：`game_official_content:phainon#支柱-死星天裁` / `#灾厄-弑魂焚诏` / `#灾厄-弑魂焚诏的反击` / `#普通攻击-创生-血棘渡亡` / `#最后一击`（**具名系数与标量都能改**） | 它们登记成了"挂在白厄名下"的**技能原型**，运行时按 `原型.copy()` 取副本；见 §3.1 第 2 条与 `70-DATA.md` §5.10.3 |

### 1.2 配了打折扣 ⚠️（会生效，但不是你想的那个数）

| # | 键 / 写法 | 打了几折 | 证据 |
|---|---|---|---|
| 1 | **`atkMagnification`（6 个技能）** | **只在第一次出手生效**：这些技能出手结束会把倍率**复位成构造器里那个字面量**（`FoundationStardeathVerdict.java:130/138/144/153` 都是 `setAtkMagnification(0.45)`），副本是复用的，第二次出手读的是复位值 | `project_analyses/EXTERNAL-DATA-LOADING-2026-10.md` §12.5 第 1 条；本轮**没改** |
| 2 | **`ignition`（李晓焰）** | **只是"这一局的初始值"**：`whenFightEnds()` 会 `setIgnition(resetIgnition)` 回到规则表值（你那份 `GameRules.json` 里是 **999**，而 `setIgnition` 会夹到上限，所以实际是满层） | `ActorLiXiaoYan.java:286-288` + `:303-305`（夹 `0…effectiveIgnitionMax()`） |
| 3 | **`coreflame`（白厄）** | **是"基准值"，开局 `+1`**：`Phainon#whenFightStart` 每局 `+1`（`Phainon.java:330`）；写 15 开局实际 16 | `Phainon.java:324-330` |
| 4 | **`extraAbilityTier`（白厄）** | **同上，开局 `+1`**，而且它**参与计算**（`×0.5%/段` 加伤），不是最终值 | `Phainon.java:329` + `:349` |
| 5 | **`soulscorch`（白厄）** | **只在觉醒期间有意义**：觉醒结束（`AwakeEndListener`）归零 | `Phainon.java:297` |
| 6 | **`coreflame` / `scourge` / `scourge_max` 的超上限值** | 写超过上限的数会被 setter **悄悄压到上限**（`setCoreflame` = `Math.min(coreflame_max, coreflame)`） | `Phainon.java:268-270` |
| 7 | **`formula.*` / `mana.*`（5+2 条）** | 改了**等于重新标定**：项目里有一套"每 1.0 倍率 ≈ 880 伤害"的标定（醉剑仙实测），改 `attackBase` 会让它全部作废。**能改，改前想清楚** | `EXTERNAL-DATA-LOADING-2026-10.md` §6 判据表的第 ③ 类 |
| 8 | **写 `level` 与 `derived.*` 一起** | 顺序是 `level` → 面板 → 背包 → `derived` → `hp`，所以 `derived` 一定赢。只写成长系数（不写 `level`）时补丁器会**自动补一次重算**，不会"改了没反应" | `EntityDataPatcher.java:175-198`；重算走 `GameRules` 的 `formula.*`，不再写死 `200/110/200`（`:216-227`） |
| 9 | **容器类数值跟着 BOSS 缩** | 【残破容器】的生命/攻击是**按召唤者最大生命的百分比**算的，你把 BOSS 的 `hpMax` 调成 7 → 容器生命也变成 `7×15% ≈ 1` | `90-STATE.md` §9.1 的三条设计事实 ① |
| 10 | **"把血调低"≠"一击秒"** | 盗火行者**血条第一次被清空**时必定进二阶段并**回满**、再叠 70% 免伤（伤害修正器拦下那一击，日志里显示 `-0`） | `60-COMBAT.md` §5.8「切阶段保护」 |
| 11 | **`derived.hp` 不必写** | 战斗开始本来就会 `setHp(hpMax)`；改 `hpMax` 就够了 | `90-STATE.md` §9.1 设计事实 ② |
| 12 | **白厄那 4 个 `classState` 键每局结束会被"直接赋 0"** | `Phainon#whenFightEnds()` 里是 `coreflame = 0; scourge = 0; soulscorch = 0; extraAbilityTier = 0;`（**直接写字段，不走 setter**，`Phainon.java:293-297`）。**这其实是好事**：配置值只在**模板**上，副本打完一局回到 0、**下一局重新从模板 `copy()` 拿你的配置值** —— 所以配置**不会一局一局累加** | `Phainon.java:288-298`（模板不受影响：`whenFightEnds` 只对**参战副本**调） |
| 13 | **`name` 是"变身时会被改写"的键** | 白厄开大招会把**显示名**改成「卡厄斯兰那」（`UltimateAttack.java:80`），打完一局**没有恢复**（`Phainon#whenFightEnds` 里只重置数值，不动名字）。所以配置里写 `name` 要当心：它影响 `@e[name=…]` 选择器与 `/data` 输出 | `UltimateAttack.java:80` + `Phainon.java:288-321` |
| 14 | **同一个数有两处能改时会"谁后谁赢"**（典型：盗火行者 / 虫皇的血） | `GameRules.json` 的 `flameReaver.baseHpMax`（`insectBoss.baseHpMax` 同理）是**构造时**读一次的出厂值（`FlameReaver.java:282-283`、`InsectBoss.java:35-36`）；而 `EntityData.json` 的 `derived.hpMax` **在构造之后**打补丁 → **`EntityData` 赢**。⚠️ **2026-10-03 第 2 轮起只在"两处的值真的对不上"时才打一行 `[配置提醒]`**（提醒里**同时给出两个数**，一眼看得出哪一处没跟上）：因为两份文件都是**全量 dump**，出厂时它们本来就写着同一个数 —— 不按值判、按"写没写"判会**每次启动必然误报**。两处都改、但改成同一个数时也不吵；**改一处、另一处停在旧值**才会响（它是**提醒**不是错误，键照样生效，且**永远打**、不受 verbose 开关影响） | `FlameReaver.java:282-283` + `EntityDataPatcher` 的 `noticeDualEntryHpMax` / `constructionHpRuleKeyOf`；契约见 `notes_for_llm/70-DATA.md` §5.10.2 |

### 1.3 配不到 ❌

| # | 想要的东西 | 为什么配不到 | 出路 |
|---|---|---|---|
| 1 | ~~**白厄的觉醒技能**~~ ✅ **2026-10-03 起配得到了**（见 §1.1 第 14 条） | ~~它们是 `UltimateAttack#comeToEffect` 里 `new` 出来的、不在注册表里~~ → 现在登记成**技能原型**（挂在 `game_official_content:phainon` 名下），运行时改成 `原型.copy()` | 见 §3.1 第 2 条；落地记录在 `EXTERNAL-DATA-LOADING-2026-10.md` §12.4（方案 A，**已落地**） |
| 2 | **`LastAttack` 的 `0.125` / 下限 `7`** | 技术上**已经可以外置**（技能具名系数那条路已经通了），**刻意没接** | `60-COMBAT.md` §5.7 写着"改这个下限等于改平衡，**先问用户**"。你点头就能接 |
| 3 | **五元素 穿透 / 增伤 ×10**（`firePenetration` / `woodDamageEnhance` …） | **临时属性，而且配了不生效**：副本（`AttributeProfile#copyFrom`）**刻意不带**这 10 个 + 每局结束 `resetTemporary()` 清零 + 全项目零写入点 | 要加穿透/增伤请用**效果或技能**（`DamageEnhanceEffect` 那一族） |
| 4 | **12 个 `*Enhance*` 运行时加成**（`attackEnhancePercent` … `criticalRateEnhanceAmount`） | 效果与技能"进来加、走时减"，且**复制构造器不带** → 配在模板上对任何一局都不生效 | 同上，用效果/技能 |
| 5 | **`extraDamage`** | **死字段**：参与伤害公式但全项目**零写入点**（活的那套是 `Skill#extraDamage`，另一个字段） | 不要用 |
| 6 | **`individualMultipleArea`** | **派生面板倍率**：由**角色自己的构造器**按玩法算（李晓焰按燃点）。这一条是**判断**（不是"配了没用"） | 要改那个算法请改角色构造器 |
| 7 | **`ignitionMax`（燃点上限）** | 私有 `int`，**没有 setter**；基准值来自规则表 + `IModifyIgnitionMax` 修正链 | 走**规则表** `actorLiXiaoYan.ignitionMax`，或走**修正链**（范例模组 `mods/liXiaoYanPlus/`） |
| 8 | **`phaseTwo` / `isAwaken` / `charging` / `absorbed` / `kind` / `owner` …（`CLASS_RUNTIME` 19 个）** | 是**运行时状态**，不是出厂输入；放行它等于让一份 `EntityData.json` 把 boss 打成二阶段、把白厄的觉醒直接摆成 true | 机制上不该配。判据是**"配了能不能算数"**，不是"这个字段属于谁" |
| 9 | **`temporary` 这个块名** | **它不是配置块**（`BARE_SECTIONS` 只有 `base` / `derived` / `classState`） | 面板属性写 `base` 里；战中的临时加成用效果/技能 |
| 10 | **`formula` 之外的整条公式 / 技能行为逻辑** | 那是**代码**。用 JSON 描述技能行为等于发明一门脚本语言 | **不做**（用户没拍板；见 §9.3 与 `EXTERNAL-DATA-LOADING-2026-10.md` 第五节） |
| 11 | **模组内容出现在 `EntityData.json` / `SkillData.json`** | 2026-10-03 起**刻意分开**：判据是**谁注册的**（`ConfigDefaultWriter.isOfficialContent`），不按 id 里的冒号 | 模组走 `config/data/<模组id>.json`（第五节）。⚠️ 你现有文件里**已经有**酒剑仙那几段（上次自愈写进去的）—— 自愈**只补不删**，所以它们会留着；想清干净手动删即可 |
| 12 | **热重载** | **不做**：要处理"已经复制出去的副本 + 已经挂上的效果" | 改文件 → **重启游戏** |

### 1.4 怎么自己判断"这个键到底生不生效"

三个办法，从快到慢：

1. **看启动日志里那一行汇总**：
   `[配置] 已加载 … EntityData.json 307 项 …，应用 624 项，跳过 0 项 …`。
   - **跳过 > 0** → 上面跟着每一条的原因（类型不对 / 未知键 / 值超范围），这是**最直接的信号**；
   - **某一份文件一项都没算** → 文件里没有**这个加载器认识**的键（空文件、或者键名拼错但恰好被当成"不属于实体数据"）；
   - 注意：**"未知键"会显式点名**，不再静默（`EntityDataPatcher` 的 `reportUnknownKeys`）；
   - ⚠️ **想看逐份的明细**（原来那三行 `应用 N 项，跳过 0 项，影响 M 个模板 / 技能`）：
     启动参数加 **`-Ddsh.config.verbose=true`**（写在 `-jar` 之前），或用环境变量
     `DSH_CONFIG_VERBOSE=true`，或建一个 `config/dsh-verbose.txt` 写一行 `config=true`
     （双击 bat 启动时只能用最后一种）。开关只影响"一切正常"的成功播报 ——
     **跳过 / 错误 / 提醒永远打**，与开关无关。
2. **用 `/data get entity @s` 对照**：**配置键就是 `/data` 的数据名**（一套名字两处用）。
   你在战斗里能 `get` 到的键，基本就是能写进 `EntityData.json` 的键（`@NoConfig` 挡住的除外）。
3. **看默认配置文件里有没有这个键**：生成器写出来的键**一定读得回来**（自测有断言钉着这一点）。
   官方 **9 个模板 / 25 个技能**都在 `EntityData.json` / `SkillData.json` 里
   （⚠️ 你手上那两份现在还多一个酒剑仙 —— 那是**上一轮自愈**写进去的历史内容，见下面第 3 条）；
   **`EntityData.default.json` 是"出厂值参考副本"**（游戏不读它，只给你对照"原来是多少"）。

> ⚠️ **一条查证过的坑（别按 README 那句话去拿它）**：`config/gameConfig/EntityData.default.json`
> 现在是 **5 行的空壳**（`{"version":1,"entities":{},"skills（SkillData.json 的内容，游戏不读这一段）":{}}`）。
> 原因是一条**链**：`writeReference`（写这个文件的那一步）**只由 `writeAll` 调用**，而 `writeAll` 只在
> "**检测到配置缺失**"时走（`ConfigLoader.loadEntityData/loadSkillData/loadGameRules` 的三个
> `if (文件不存在) writeDefaultConfigFiles()`）；**而这三个里第一个跑的是 `loadGameRules`
> （`GameMain.java:65`），那一刻 `World` 注册表还是空的** —— 于是它生成的"出厂值参考副本"
> 天生就是空的，之后再没有任何代码重写它（`healDefaultConfigFiles` 刻意**不**碰它）。
> **所以"删掉它、重启一次"拿到的还是空壳**（README 里那句"删掉会重新生成一份全量默认值"
> 对这一份**不成立**）。**要看"出厂值是多少"，请直接看** `EntityData.json` / `SkillData.json`
> —— 那两份才是自愈真正在维护的。这一条属于**已知瑕疵**，不是这次改出来的。

---

## 二、加一个**新实体**：完整步骤

### 2.0 先看结论

**加一个官方实体 ≈ 3 步**：① 写类（构造器 + getter/setter + `copy()`）→ ② 在 `OfficialGameContent`
里 `addEntity(...)` → ③ 跑自测 + 重启游戏看默认配置里有没有它。
**配置层一个字都不用改** —— 它会**自动**出现在 `EntityData.json` 里。

### 2.1 第 1 步：写实体类

| 项 | 怎么做 | **为什么必须这么做** |
|---|---|---|
| **构造器** | 调 13 参构造器 `LivingThing(name, id, 火抗, 水抗, 金抗, 木抗, 土抗, speed, level, type, hpGrow, atkGrow, dfkGrow, ElementSort)`（`LivingThing.java:309`） | 参数的**顺序就是 API**：模组 `DrunkenSwordsman` 正在用，`MODDING-GUIDE.md` 把它写成对外契约。**不要"顺手优化签名"**（自测有一条反射断言钉住形状） |
| **`copy()`** | 必须重写并走**复制构造器**（`LivingThing(LivingThing)`），**不要**写 `return new Xxx()` | 基类的 `copy()` 直接抛异常；`UniversalController` 构造时就逐技能 `copy()`，**忘了写＝开局崩**。写 `return new Xxx()` 会让**配置静默失效**（打在模板上的值传不到副本）—— 9 个技能类踩过这个坑 |
| **`@NoConfig`** | 只给"**不应该给人配**"的字段加，并且**每个都要写真理由** | 默认是**开放**的：不写注解 = 这个标量字段自动变成可配置项（反射面兜底）。所以**每加一个标量字段，你要想一次"能不能给人配"**；写 `@NoConfig("…")` 就是回答"不能，因为……" |

**`/data` 自动可见吗？** 是 —— 只要字段是 `Thing` / `LivingThing` 上的（或 `@DataFlatten`
组件里的）标量字段，`/data get entity @s` 就看得到，**数据名默认就是 Java 字段名**。
配置面也自动跟着可见（同一套名字）。想改名用 `@DataField("新名")`，想从 `/data` 排除用 `@NoData`。

> ⚠️ **一个例外，别当成"字段不见了"**（2026-10-03 定）：如果你的字段是**指向另一个实体**的
> （`LivingThing` / `BrokenContainer` 那种），它在 `/data get entity @s` 里**只显示
> `{uuid:"…"}`** —— 因为"被引用的实体"只导出身份，根对象才全量展开
> （`70-DATA.md` 的 §5.10.1.1）。键名照旧、写回照旧，只是嵌套引用那一格不再倒出一整棵字段树。
> 想查那个实体自己的字段，把它当根再查一次：`/data get entity <它> hp`。

**三个注解的分工**（一个字段有三面：`/data get` / `/data merge` / 配置文件）：

```
默认          → 三面全开
@NoConfig     → /data 全开，配置面关闭（例如 uuid / alive / presentTurn / 12 个 *Enhance*）
@NoData       → 三面全关（例如 anticipating）
```

**加字段的次序（很重要）**：

```
① 写 Java 字段（+ getter）
② 加 setter                          ← 见下面 2.2，这一步漏了会静默降级
③ 想一次"能不能给人配" → 不能就写 @NoConfig("真理由")
④ 判断它是面板属性还是临时属性（见 2.3）→ 临时属性要进 clearTemporaryAttributes()
```

### 2.2 必须有 setter —— 没有会怎样？

**写回是按 Java 字段名拼 setter 名的**：`"set" + 首字母大写 + 字段名`
（`DataBridge.java:833-853`）。找不到 setter 时，它不是报错，而是**退化成裸写字段**
（`DataBridge.java:770-778`）—— 于是：

- 配置/`/data merge` **照旧报"应用成功"、值也照样变**；
- 但对象自己的**钳制与副作用全没了**：`setHp` 不再夹到上限（`LivingThing.java:2241`）、
  `setIgnition` 不再夹 `0…上限`（`ActorLiXiaoYan.java:303-305`）、`setCoreflame` 不再压到上限；
- **最阴的一种回归**：`final` 字段会被直接拒绝（抛错），而**非 final 但没 setter** 的字段
  **一个字都不说**。

所以自测里钉着两条：**「每个 `/data` 能写的标量字段都挂着一个同名 setter」**（只读的两个例外
在用例里点名列出）与**一条哨兵断言**（证明 `DataBridge#merge` 走的是 setter 而不是裸写字段）。
**新加字段时，这两条断言会替你抓住漏写的 setter。**

### 2.3 面板属性 vs 临时属性 —— 今天建立的那条规则

**规则一句话：不是面板属性的一律清零，面板属性一律复制。**

| | **面板属性** | **临时属性** |
|---|---|---|
| 定义 | "出厂就该是多少"的输入 | 打到一半才变、由效果/技能给的一次性加成 |
| `copy()` 带不带 | **带**（复制构造器逐字段抄） | **不带**（副本从干净状态开始） |
| `whenFightEnds()` 清不清 | **不清** | **清**（`clearTemporaryAttributes()`，`LivingThing.java:2094-2112`） |
| 例 | 5 抗性 / 5 法力成长 / `criticalRate` / `criticalDMG` / `enhance` / `penetration` / `defenseLoss` / `individualMultipleArea` | 12 个 `*Enhance*`、五元素穿透/增伤 ×10、`extraDamage` |
| 配置面 | **能配**（写一次整局生效） | **配了不生效**（`@NoConfig` 挡住，并给理由） |

**实现上的两个位置是互补的，改一个必须看另一个**（源码里就写着这句话，`LivingThing.java:2064-2068`）：

1. **复制构造器**（`LivingThing.java:240-288`）：只抄面板属性 → `attributes.copyFrom(other.attributes)`
   （`AttributeProfile.java:199`，**刻意不带**那 10 个五元素临时属性）；
2. **`clearTemporaryAttributes()`**（`LivingThing.java:2094-2112`）：把效果写的 12 个
   `*Enhance*`、`attributes.resetTemporary()`（`AttributeProfile.java:226-237`）与死字段
   `extraDamage` 清零。

**判断口诀**：这个值是"**开局该是多少**"（→ 面板，复制 + 不清），还是"**打起来才变**"
（→ 临时，不复制 + 清零）？**拿不准就问自己"副本该不该带它"** —— 带了就是面板。

### 2.4 注册进 `OfficialGameContent` → 自愈会自动把它 dump 进 `EntityData.json`

在 `OfficialGameContent` 的构造器里加一行 `this.addEntity(new 你的类(等级));`
（范例见 `OfficialGameContent.java:51-62`）。**注册是三件事的唯一入口**：

1. **进选人列表 / `/summon` 候选**；
2. **`World.applyRegisteredId` 能按类把运行时实例的短 id 补成完整 id**
   （`game_official_content:xxx`）—— 现场 `new` 出来的召唤物靠这个；
3. **自愈生成器会把它的当前全量值写进 `EntityData.json`**
   （`ConfigDefaultWriter.collectBaseValues` 遍历 `World.getEntityList()`）。

**⚠️ 官方实体的 id 会自动带 `game_official_content:` 前缀**（`OfficialGameContent.MOD_ID`），
所以配置里的键是 `game_official_content:你的短id`；而运行时 `new` 出来的实例只有短 id，
进游戏时由 `World.applyRegisteredId` 按**类**查表补全。

**自愈的语义（三条，必须记牢）**：

- **只在缺失时写**：文件不存在 → 写一份全量；文件存在 → **只补"默认值里有、文件里没有"的键**；
- **已有的值一个字节都不动**（包括你自己加的、加载器不认识的键）；文件内容不是合法 JSON 时**一个字都不动**；
- 补完没变化 → **不写盘**（所以 mtime 不会每次启动都变）。
- ⚠️ **副作用**：一旦真的补过键，重写走 `org.json` 的标准排版，**对象里的键顺序会变成哈希序**
  （这不是 bug，是 `JSONObject` 由 `HashMap` 支撑的必然结果；键与值一个不改）。

> **实战含义**：你加一个实体 → 重启游戏 → `EntityData.json` 里就多出它的 `base` / `classState` /
> `derived` 三段，**键名完整、值就是当前出厂值**，你照着改即可，不用去猜键名。

### 2.5 子类特殊字段：`CLASS_CONFIG`（开放）vs `CLASS_RUNTIME`（挡）的判据

**判据不是"这个字段属于谁"，而是"配了能不能算数"**（`DataKeys.java:662-731`）：

| 清单 | 内容 | 为什么 |
|---|---|---|
| **`DataKeys.CLASS_CONFIG`**（7 个，**开放**） | `ignition` / `coreflame` / `coreflame_max` / `soulscorch` / `scourge` / `scourge_max` / `extraAbilityTier` | 每一个都是"**这一局开始时该是多少**"的**输入**，而且**每个都有 setter** → 对象自己的钳制照旧生效。写法：`{"classState":{"coreflame":18}}` 或裸键 `{"coreflame":18}` |
| **`DataKeys.CLASS_RUNTIME`**（19 个，**继续挡**） | `phaseTwo` / `isAwaken` / `charging` / `absorbed` / `owner` / `kind` / `deathNotified` / `extraTurns` / `ignitionMax` / `lastIgnition` / `memorizedRate` / `appliedExtraAbilityTier` / `absorbDamage` / `pendingLastAttack` / `skills` / `disasterPower` / `sacrificeWindow` / `damageReductionLayers` / `painCost` | 是**玩法进程**（由它自己的技能与流程维护），不是出厂输入。典型：`phaseTwo` **连 setter 都没有**，放行它就是"配置静默改掉 boss 的进程状态" |

> ⚠️ **它们为什么走"反射面"而不是进 `EntityKeySpecs` 那张通用表**：那张表的每一行都要对**任意**
> `LivingThing` 成立，而 `coreflame` 只在 `Phainon` 上存在（表驱动的写入不分类型，会把"写一个不存在的
> 字段"变成静默无效或异常）。所以放行的那批由 `ReflectionConfigBridge.patchExtra` 处理，
> **写错模板时照旧报未知键**，不会静默。

**怎么加一个新的子类字段**（例如给某个新角色加 `rage`）：

1. 在类里写 `private int rage;` + `getRage()` + **`setRage(int)`**（钳制写在 setter 里）；
2. 想一次"**这是出厂数值还是运行时状态**"：
   - **出厂数值 + 有 setter** → 把它加进 `DataKeys.CLASS_CONFIG`（一行）；
   - **运行时状态** → 加进 `DataKeys.CLASS_RUNTIME`（一行）；
3. 跑自测。**三条断言会替你检查**：
   - 「**放行的子类配置键必须有 setter**」（**漏 setter 就是重开"静默裸写字段"那个洞**）；
   - 「**角色/召唤物类自己的状态键没有冒出新面孔**」（新键必须显式登记，不许悄悄混进去）；
   - 「**反射面 ⊆ 允许清单**」（跑全部 9 个模板，不只 `playerOne`）。
4. 生成器**只给真的有这个字段的模板写这一段**（`ReflectionConfigBridge.classStateValues`
   的口径是"实体上 dump 得出来才写"）—— 所以给白厄加的东西不会跑到玩家一身上。

### 2.6 加完之后怎么验证

**① 自测（AI 也能跑，最快）**：

```powershell
# 静态自查（快）
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force; & .\check-sources.ps1
#   期望：Java files: 226（新增文件会 +1，删文件会 -1） / CHECK OK

# 权威：全量编译 + 自测
powershell -ExecutionPolicy Bypass -File .\test-command-system.ps1
#   期望：通过 N 条，失败 0 条（N 只能涨不能跌；当前基线 827）
```

自测会替你验：**新字段能配**、**全量默认值打回去数值逐字段不变**、**`copy()` 带得过去**、
**`/data` 键名没变**、**构造器形状没变**。**AI 侧不启动游戏**，所以下面第 ② 条只有你能做。

**② 进游戏看什么**（AI 做不了）：

1. 启动日志看那一行汇总：`… EntityData.json N 项 …，应用 … 项，跳过 0 项 …`，
   且模板数 **M 比之前 +1**（新实体被自愈写进去了）。⚠️ 要逐份的明细就开
   `-Ddsh.config.verbose=true`（见 §1.4 第 1 条）；
2. 打开 `config/gameConfig/EntityData.json`，**新实体的那一段应当自动出现**，键名就是你要的；
3. **改一个显眼的数**（例如 `derived.hpMax` 改小）→ 重启 → 选它打一局 → 看 `HP x/y`
   是否等于你写的数（这正是你验证盗火行者 `hpMax: 7` 的那条路）；
4. 面板类的（速度 / 抗性）可以 `/list` 或 `/data get entity @s` 直接看。

---

## 三、加一个**新技能**：完整步骤

### 3.0 先看结论

**加一个技能 ≈ 3 步**：① 写技能类（**构造器给默认值** + **`copy()` 走复制构造器**）→
② 挂到某个实体的控制器上（**不在控制器里的技能要登记成技能原型**，见 §3.1 第 2 条）→
③ 跑自测 + 重启游戏看 `SkillData.json` 里有没有它。
**id 不用你操心**：注册表会按"显式 id 优先 / 类名派生 / 撞名 fail loud"落，
但**注册表覆盖到的技能必须条条有 id**（2026-10-03 第 2 轮起自测有探针钉它，见 §3.1 第 4 条）。
**你要外置的数值全都"零配置层改动"**：标量走 `SkillKeySpecs` 那张表，自报的名字走反射面。

### 3.1 四条硬要求（漏一条就静默失效）

| # | 要求 | 漏了会怎样 | 证据 |
|---|---|---|---|
| 1 | ⚠️ **`copy()` 必须带当前数值** —— 走复制构造器，**不要** `return new Xxx()` | 配置**静默失效**：打在模板上的值**永远传不到副本**（实测过：模板改 3.25、副本还是 7.5）。**9 个技能类踩过这个坑** | `MODDING-GUIDE.md`「技能实例是复用的」；`SkillCoefficientTunable` 的 javadoc 把这条写成实现约定 |
| 2 | ⚠️ **技能要"看得见"**：在**控制器里**，或者**登记成技能原型** | **配不到**：配置沿 `World.getEntityList() → controller.getSkills()` 找技能（`SkillDataPatcher.java` 的 `targetsOf` / `all`），**`new` 出来的不在视野里** | 白厄那 5 个觉醒技能原来就是这个 ❌，**2026-10-03 已修**（见下） |
| 3 | **默认值 = 外置之前写在方法体里的那个字面量，逐位相同** | 数值会在你没察觉的时候漂一下 | 自测第一条就钉这个（"出厂值逐位相同"） |
| 4 | ⚠️ **必须有 id**：不用自己拼、也不用在构造器里塞字符串 —— 只要**让注册表看得见**（第 2 条），`World#indexSkill` 就会给**每一份实例**落上"显式 id 优先 / 否则类名派生 + `模组id:` 前缀"的 id | 那份技能在 `/data get` 里就是**没有 `id` 字段**的一条（同一份内容看起来"登记了一半"），按 id 找它的地方也找不到它 | 用户实测（2026-10-03）：卡厄斯兰那的 `skills` 是 `[null, …:normalSkill, …:ultimateAttack]` —— 「普通攻击」的类被别的模板先登记过，它这份就没 id；**已修**，自测探针 `testSkillIdCoverage` 现在断言"25 份实例 / 0 份没 id" |

> **第 2 条的两条路（2026-10-03 起）**：
> ① **挂在实体控制器上**（绝大多数技能）—— 注册是**跟着实体走的**，键就是实体的完整 id：
> `game_official_content:phainon#战技:黎明创世,地辟天开`；
> ② **登记成"技能原型"**（只有"技能自己现场 `new`"的那类才需要，例如觉醒技能、临时召唤）——
> 在官方内容里写 `this.addSkill(phainon, new FoundationStardeathVerdict())`（模组里同理，
> **归属模板必须传**，否则配置不知道该挂在谁名下），运行时改成
> `World.prototypeCopyOf(FoundationStardeathVerdict.class)`（**= 原型.copy()**）。
> ⚠️ **原型不能直接拿去用**（它是全场共用、也是配置打补丁的那一份）；
> ⚠️ **技能 id = `模组id:短id`**（显式 id 优先、否则类名首字母小写），
> **派生出来撞名会直接报错**（项目里有两对同名类就是这么解决的，见 `70-DATA.md` §5.10.3）；
> ⚠️ **id 落在每一份实例上**（控制器里存的是副本，同一个类还有好几份）——
> **一个类共用一个 id**，别指望"同类第一条有 id 就够了"（§3.1 第 4 条）。

### 3.2 数值怎么外置：先分类，再动手

| 数值长什么样 | 放哪 | 例子 |
|---|---|---|
| **`Skill` 基类已有的槽位** | **什么都不用做**，`SkillData.json` 里天然能配（`aims` / `coolDown` / `hpMagnification` / `atkMagnification` / `defMagnification` / `forEnemies` / `consumedMana` / `tags`） | 盗火行者四招的主倍率（它们传进 `super(...)` 变成 `atkMagnification`） |
| **技能自己的 1 个整数旋钮** | 实现 **`NumericSkillTunable`**（`getExtraNumericValue` / `setExtraNumericValue` / `extraNumericKey`） | `RestorationHealthSkill` 的"释放门槛"；键名默认 `neededManaScale` |
| **技能自己的 N 个具名系数** | 实现 **`SkillCoefficientTunable`**：`coefficientValues()` 返回"名字 → 当前值"（**用 `LinkedHashMap`**，顺序 = 默认文件里的书写顺序）、`setCoefficientValue(name, value)` | 死星天裁 4 个（`healRatio` / `scourgeCostCap` / `hitsPerScourge` / `finishMagnification`）、反击 4 个、血棘渡亡 2 个、弑魂焚诏 2 个、盗火行者两招 2 个 |
| **整条公式 / 行为逻辑** | **留在代码里**（`comeToEffect` 的方法体） | "每层打几段 = `hitsPerScourge × 层数`" —— **算式留代码，算式里的每个数**才进配置 |
| **运行期状态**（`nowCoolDown` / `extraDamage`） | **不外置** | `extraDamage` 还有"伤害算完清零"的约定，外置等于给用户一个坑 |

**`NumericSkillTunable` 与 `SkillCoefficientTunable` 的关系**：前者**继承**后者，用两个 `default`
方法把自己适配成一个具名系数 → 配置层与生成器**只有一条路**（"整数值必须写整数"这条老口径由
`SkillDataPatcher#applyExtraNumeric` 单独保住）。

**具名系数的五条纪律**（`SkillCoefficientTunable` 的 javadoc，照做即可）：

1. `coefficientValues()` 返回**当前值**，**不许有副作用**；顺序用 `LinkedHashMap`；
2. 每个名字对应子类里的**一个字段**，字段初始值 = 出厂值，**逐位相同**；
3. **`copy()` 必须把这些值一并复制**（写在复制构造器里）；
4. **整数型系数的取整由实现自己负责**：`SkillCoefficientTunable` 那条路写 `4.5` 会得到 `4`
   （`(int) value`）；而**老的整数旋钮** `NumericSkillTunable` 那条路**必须写整数**
   （`90` 与 `90.0` 都认，`90.5` 报"类型不对"，`SkillDataPatcher.java:287-289`）；
5. 键的形态是**扁平**写在技能对象里（与 `aims` 并列），**不要另造一个子块**。

**外置前后对照**（`FoundationStardeathVerdict` 是真的这么写的，可以直接照抄形状）：

```java
// ① 名字是常量，避免拼错
private static final String COEFF_HEAL_RATIO = "healRatio";
// ② 值住在字段里，初始值 = 外置前写在方法体里的那个字面量
private double healRatio = 0.2;
// ③ 复制构造器带上它（关键的一步）
private FoundationStardeathVerdict(FoundationStardeathVerdict other) {
    super(other);
    this.healRatio = other.healRatio;
    // … 其余系数逐个抄
}
// ④ 自报家门
@Override
public Map<String, Double> coefficientValues() {
    Map<String, Double> values = new LinkedHashMap<>();
    values.put(COEFF_HEAL_RATIO, healRatio);
    // … 其余系数逐个 put（顺序 = 默认文件里的顺序）
    return values;
}
// ⑤ 写回
@Override
public void setCoefficientValue(String name, double value) {
    switch (name) {
        case COEFF_HEAL_RATIO -> this.healRatio = value;
        // …
        default -> { }
    }
}
// ⑥ 用的时候读字段，而不是读字面量
user.setHp((long) (user.getHp() + user.getHpMax() * healRatio));
```

**配置侧的样子**（用户在 `SkillData.json` 里看到的就是这个）：

```json
"game_official_content:phainon#死星天裁": {
  "aims": 1, "coolDown": 0,
  "hpMagnification": 0, "atkMagnification": 0.45, "defMagnification": 0,
  "forEnemies": true,
  "consumedMana": { "amount": 200, "element": "FIRE" },
  "tags": {"ATTACK": 5},
  "healRatio": 0.2, "scourgeCostCap": 4, "hitsPerScourge": 6, "finishMagnification": 6
}
```

> **别做 DSL**：把**公式**写进 JSON（`"倍率": "6 * 层数"`）等于发明一门脚本语言，要表达式求值器。
> 那是另一个子系统，**用户没拍板，不做**（`10-RULES.md` §9.3 + `EXTERNAL-DATA-LOADING-2026-10.md` 第五节）。

### 3.3 加完之后怎么验证

**① 自测**（同上两条命令）。`testSkillCoefficients`（17 条）会验：

- **出厂值逐位相同**（6 条）；
- **改了真的改变伤害**（死星天裁 6 段 → 1 段，`6120 → 1020`；盗火行者每层倍率改 0，
  两段伤害整段消失 `13674 → 5128`）；
- **默认文件里看得见**（生成器会写出这个键，否则用户没法发现它）；
- **把默认值原样打回来，伤害逐位不变**（`13674 = 13674`）；
- **写到不认识它的技能上会被点名"未知键"**（不会静默忽略）；
- **`copy()` 带系数**、**类型不对只跳过**。

`testSkillIdCoverage`（6 条，2026-10-03 第 2 轮加）会验 **id 覆盖率**：注册表视图条条有 id、
id 两两不同、**每一份控制器技能实例**都有 id（探针会把没 id 的逐条打印出来，2026-10-03 修复前是 6 份）、
白厄副本（战斗里那份「卡厄斯兰那」）`skills` 里三条都带 id。**加了新技能之后这条会替你看住 id**。

**② 进游戏看什么**：

1. 启动日志看那一行汇总里 `SkillData.json` 那一段：**`… 项`的数目比之前 +N**；
   ⚠️ 要逐份的 `应用 N 项，跳过 0 项，影响 M 个技能` 就开 `-Ddsh.config.verbose=true`；
2. 打开 `SkillData.json`，**新技能的键应当自动出现**：`<实体完整id>#<技能名>`；
3. **改一个显眼的倍率**（`atkMagnification` 或某个具名系数）→ 重启 → 打一局看伤害有没有变；
4. ⚠️ **改技能名 = 配置静默失效**（键里含技能名）。补丁器会显式点名
   **「实体 X 上没有叫 Y 的技能（技能改名会让这条配置静默失效，所以这里显式点名）—— 它有：…」**，
   所以改名之后你会在日志里**看到一句明确的报错**，不会摸不着头脑；
5. 顺手 `/data get entity @s skills`：**每一条都应当有 `id`**（`<模组id>:<类名首字母小写>`）——
   没有就是 §3.1 第 4 条那个坑（注册表覆盖不到它）。

---

## 四、加一个**新规则键 / 新魔法数字**：改哪几处

**判据（三条全中才外置）**：① 会随平衡变？② 改了**不影响机制结构**？③ 有名字和量纲？
—— **"玩家会抱怨数值不平衡"的东西外置；"玩家会抱怨游戏坏了"的东西留在代码里。**

**结构常量**（元素数 6、背包 63、`SKILL_WITHOUT_NEW_TURN`、`aims` 的 0/-1 语义）与
**格式常量**（攻击行格式、颜色）**留在代码里**。

**加一条规则键 = 改 2 个文件 2 处**（已经压到很小了）：

| 序 | 文件 | 改什么 |
|---|---|---|
| 1 | `RuleDefaults.java` | 加一个 `public static final` **值常量**（出厂值的唯一真相） |
| 2 | `RuleKeySpecs.java` | 加一行 `RuleKey`（键名 / 段名 / 出厂值 / 一句话说明） |

**新段（新的 BOSS 之类）才要**动 `GameRulesPatcher.SECTIONS`（段名现在从表里 `distinct` 出来，
多数情况不用改）。然后在**用它的那个类**里加一个读取方法并接线。

⚠️ **一条实测过的坑**：**不要**写 `private static final double X = GameRules.getXxx(...)`。
静态常量在"**类第一次被加载**"时取值，而**类加载时机不受配置控制** → 配置静默失效。
**正确做法是"用的时候现读"**（`FlameReaver` / `BrokenContainer` / `InsectBoss` 都已经改成读取方法）。

⚠️ **`GameRules` 在开局就冻结**（`GameMain.gameInitialize()` 的第一步）—— 所以**模组改不了它**，
在 `common` 段里写规则段会得到一句明确的"这一段改不了 + 去哪儿改"（`ConfigLoader.java:597-609`）。

---

## 五、加一个**模组**：配置怎么走

**这一节只讲"配置"，模组本身的写法看 `MODDING-GUIDE.md`。**

### 5.1 三条契约

1. **配置文件在游戏自己的 `config/data/<配置分组名>.json`**，**模组不能自带配置文件**
   （类加载器的 URL 里根本没有模组目录）。
2. **分组名 = 注解 > `MOD_ID`**：主类上写 `@ModConfig(id = "xxx")` 就用 `xxx`（去掉首尾空白）；
   不写就退回 `Mod.getMOD_ID()`；两者都拿不到 → 这个模组拿不到配置（打印一行提示后跳过，
   **不影响别的模组**）。注解**不继承**（没有 `@Inherited`），要写在**主类自己**头上。
3. **模组内容不进游戏自己的 `EntityData.json` / `SkillData.json`**（判据 = 谁注册的）。

### 5.2 两种用途，两种写法

```java
// 主类：声明 + 实现可选接口 ModDataAware
@ModConfig(id = "myMod")
public class MyMod extends Mod implements ModDataAware {

    /** 配置加载完成后、注册内容之前调用一次（没有配置文件时也会调用，读到的是空文档）。 */
    @Override
    public void applyConfig(ModConfigDocument cfg) {
        int stacks = cfg.getInt("initialStacks", 2);        // 我自己的分组（文档的根）
        double rate = cfg.getDouble("common/globalRate", 1.0); // 共享区
    }

    @Override
    public void invokeWhenLoaded() { addEntity(new MyCharacter(125)); }
}
```

`config/data/myMod.json`：

```jsonc
{
  "version": 1,
  "common": {                        // 共享区：对【官方/共享】数值的调整（一层补丁）
    "entities": { "game_official_content:flameReaver": { "derived": { "hpMax": 90000 } } },
    "skills":   { "game_official_content:phainon#普通攻击:逐火救世,行则将至": { "atkMagnification": 2.0 } }
    // ⚠️ 规则段（formula / mana / flameReaver / insectBoss / actorLiXiaoYan）改不了，会打印明确报错
  },
  "myMod": {                         // 我自己的分组：字段名由我自己定，游戏不校验
    "initialStacks": 4
  }
}
```

**`ModConfigDocument` 速查**（`mod/config/ModConfigDocument.java`）：

| 方法 | 说明 |
|---|---|
| `getLong/getInt/getDouble/getBoolean/getString(path, 默认值)` | **读不到就返回你给的默认值，不抛异常** |
| `getStringList(path, 默认值)` | 读 JSON 数组（非字符串元素跳过） |
| `has(path)` | 用户**到底写没写**这个路径 |
| `section(path)` | 以某个路径为根的子文档（没有时返回空文档，不用判空） |
| 路径写法 | `initialStacks` 与 `myMod/initialStacks` **等价**；`common/…` 读共享区；**别的模组的分组读不到**（隔离） |

### 5.3 时机与容错

- **时机**：`ModLoader` 装载完之后、`invokeWhenLoaded()` **之前**（落在 `GameStartEvent` 里）。
  所以模组可以"**按配置决定注册几个技能**"。
- **共享区 `common` 比 `EntityData.json` 晚应用 → 后者胜**（"模组对官方数值的调整"优先级更高）。
- **`applyConfig` 裹在 `catch (Throwable)` 里**：一个模组配置写错**不会带崩后面的模组**
  （这条是刻意的，`ConfigLoader.java:566-577`）。
- **没有配置文件时照样调用 `applyConfig`**（文档是空的）：模组不用写"有没有文件"的分支。
- **能力上限**：`common` 支持 `entities` + `skills`，**不支持规则段**；模组改**官方机制**
  （不新增内容、只改数值/上限）要走**扩展点链**，范例是 `mods/liXiaoYanPlus/`（见 `MODDING-GUIDE.md` §7.1）。

---

## 六、速查表：我要加 X → 改哪几个文件 → 跑什么命令 → 看什么算成功

| 我要加… | 改哪些文件（配置层改动） | 命令 | 看什么才算成功 |
|---|---|---|---|
| **新实体（官方）** | ① 新实体类（构造器 + getter/**setter** + `copy()`）② 子类字段要开放就加进 `DataKeys.CLASS_CONFIG` / `CLASS_RUNTIME`（一行）③ `OfficialGameContent` 加一行 `addEntity`。**配置层 = 0 处**（标量自动可见） | `check-sources.ps1` → `test-command-system.ps1` | `CHECK OK`；`通过 N 条，失败 0 条`；进游戏日志 `EntityData.json：…影响 M 个模板`（M +1），文件里自动多出它的三段 |
| **新实体（模组）** | 模组自己的 `code/` 里写类；主类 `invokeWhenLoaded()` 里 `addEntity`。配置走 `config/data/<分组名>.json`，**不进** `EntityData.json` | `javac` 单独编那个模组目录（`MODDING-GUIDE.md` §6） | 控制台 `模组 [名字] 加载成功！`；它**不**出现在 `EntityData.json` 里（这是对的） |
| **新技能** | ① 新技能类（构造器默认值 + **`copy()` 走复制构造器**）② 要多个旋钮就实现 `SkillCoefficientTunable`（或 `NumericSkillTunable`）③ 挂到某实体的控制器上（**不在控制器里就登记成技能原型**：`addSkill(owner, skill)` + 运行时 `World.prototypeCopyOf(X.class)`）。**配置层 = 0 处** | 同上 | `SkillData.json` 里自动多出 `<实体id>#<技能名>` 那一段（含你自报的系数名）；改一个数重启后伤害真的变。⚠️ 技能 id 撞名（同一个类名出现在两个包）会**注册时就报错**，照提示写 `setId("自己的短名")` |
| **一条新规则键** | ① `RuleDefaults.java` 加值常量 ② `RuleKeySpecs.java` 加一行 | 同上 | `GameRules.json` 自愈补出新键；`GameRules.getXxx` 读到的值跟着变 |
| **一个模组 + 它的配置** | ① `@ModConfig(id=…)` + `implements ModDataAware` ② 用户手写 `config/data/<分组名>.json` | 单独编模组 + 重启游戏 | 控制台 `[配置] <文件名>.json:已读取,已交给模组`（**这行算"一切正常"，默认静默**，要看得开 `-Ddsh.config.verbose=true`）；改 `common` 里的官方数值真的生效 |
| **纯调数值（不加内容）** | **只改 `config/gameConfig/` 下的 JSON**，一个字代码都不用动 | 重启游戏 | 结尾那一行汇总里出现 `应用 N 项，跳过 0 项`；进战斗看数值 |
| **验证"加一个字段要不要动配置层"** | 在实体类上加一个标量字段（不写 `@NoConfig`）→ 配置层**一行都不用改** | 自测 | 「反射面 ⊆ 允许清单」不红 → 说明它自动进了可配置面 |

---

## 七、这份文档与其他文档的关系 / 每次读过哪几份 notes

**写这份文档的那一轮读过的 `notes_for_llm/`，以及哪条结论影响了本文**：

| 文件 | 哪条结论影响了本文 |
|---|---|
| `00-CATALOG.md` | 「按任务查表」里**没有**"加内容"这一行 → 本文建完已在那里登记一行（否则下个 AI 找不到它） |
| `10-RULES.md`（第 0 条 + §9.3） | §9.3 三条直接进了本文：**表定义 + 代码生成已被否决**（§3.2 不写它）、**`EntityKeySpecs` / `SkillKeySpecs` 的标量规格一行没删**（§3.1 说的是"零配置层改动"而不是"表已经删了"）、**`LastAttack` 的 `0.125`/下限 7 与 `setExtraAbilityTier` 无上限都"改之前问用户"**（§1.3 第 2 条） |
| `70-DATA.md` §5.10 | **配置键就是 `/data` 的数据名**（一套名字两处用）+ **必须有 setter**（写回按 Java 字段名拼 `setXxx`，漏了会静默退化成裸写字段）→ 本文 §1.4 第 2 条与 §2.2 |
| `60-COMBAT.md` §5.6 / §5.7 / §5.8 | ① 切阶段保护 → §1.2 第 10 条（"把血调低"≠"一击秒"）；② `LastAttack` 那条"改下限先问用户" → §1.3 第 2 条；③ 容器按召唤者百分比算 → §1.2 第 9 条 |
| `30-WORKFLOW.md` §0 / §0.1 / §6 | 命令怎么跑（`Set-ExecutionPolicy -Scope Process Bypass` + `& .\check-sources.ps1`；**不要接管道**）、基线怎么读 → 本文所有"跑什么命令" |
| `90-STATE.md` §9.1 | **用户实跑的原文数字**（`应用 300 项…影响 10 个模板` / `应用 225 项…影响 28 个技能` / `HP 7/7`，那时还是逐份播报）→ 本文 §1.0 的"能用了"证据；"改 `hpMax` 就够了"、"容器跟着缩"三条设计事实 → §1.2 |
| `70-DATA.md` **§5.10.4**（2026-10-03 新增） | **配置加载的输出策略 + `dsh.config.verbose` 开关**（哪几行留、哪几行收敛成一行汇总）→ 本文 §1.0 / §1.4 / §2.6 / §3.3 / §6 里所有"看什么算成功" |
| `project_analyses/EXTERNAL-DATA-LOADING-2026-10.md` | §11.3 四个"打折扣"的键 → §1.2；§12.4 觉醒技能配不到 → §1.3 第 1 条；§12.5 `atkMagnification` 复位 → §1.2 第 1 条；阶段 2「`copy()` 必须带数值」→ §3.1 |
| `project_analyses/CONFIG-LOADING-DECOUPLING-2026-10.md` | §12.2/§12.3 那 24 个键的分类（10 临时 + 12 `*Enhance*` + `extraDamage` + `individualMultipleArea`）→ §1.3 第 3~6 条；§12.5 三条阻碍 → §3.1 的"表还没删" |

**2026-10-03 第 2 轮（收敛启动播报 + 修血量提醒误报 + 扫探针）又读了哪几份、哪条结论影响了改动**：

| 文件 | 哪条结论影响了这一轮 |
|---|---|
| `00-CATALOG.md` 开工 checklist 第 6 条 | "回复里要交代读了哪几份、哪条结论影响了改动" → 就是本表 |
| `10-RULES.md` §9.3 | **`EntityKeySpecs`/`SkillKeySpecs` 的标量规格没删**、**表定义+代码生成已否决**、**`LastAttack` 的 `0.125`/下限 7 与 `setExtraAbilityTier` 无上限"改之前问用户"** → 本轮**一行数值都没碰**，输出层与探针是仅有的改动面 |
| `70-DATA.md` §5.10.2 | **"配置键就是 `/data` 数据名 + 改数据名 = 破坏契约"** → 本轮**没有新增/改名任何数据名**（134 个断言点照旧）；同一节的"血量两个入口"正是本轮要修的误报源头 |
| `30-WORKFLOW.md` §0 / §0.1 / §6 | **沙箱里不能接管道**（`cmd /c "… > out\x.log 2>&1"` + `Get-Content -Tail`）、**不要用 PowerShell 字符串读写改 `.java`**、**验证脚本别往 `src` 里植入错误** → 本轮全部照做，临时探针只落 `out/` 且用完即删 |
| `60-COMBAT.md` §5.5.2（日志约定） | **要标明"对象来源"的输出统一走 `getNameWithSide()` / 短标识**，且有"**故意没加**"的判据（同一侧/同一只的续报不加）→ 本轮**没有动任何战斗日志**，收敛的只有启动播报；那条"故意的判据"也正是"哪些该留、哪些该收敛"的同一把尺子 |
| `90-STATE.md` §9.1 | **上一轮收官 `822/0`、`src` 227 个文件**；**最后一轮「死代码清理 + 参考副本 `_note`」收官 `827/0`、`src` 226 个文件**（净减 1：删了 `debug_tools/TestAnticipateDamage.java`） |
| `20-MISTAKES.md`「配置加载」分组 | ⚠️ **"同一个根因有几条产出路径时，修一条要回头数一遍还有几条"** → 本轮修"每次启动必响的提醒"时逐条复核了**既有断言**（那条"只写 `derived.hpMax` 不吵"的老断言按新判据**本来就该吵**，已改成写规则表生效值） |
| `70-DATA.md` §5.10.5（最后一轮新增） | **参考副本 `EntityData.default.json` 的结构契约**：说明文字一律走根上的 **`_note`**（下划线开头 = 不是数据）、**键名里不许出现非 ASCII**（守卫断言盯着）、**技能段与 `SkillData.json` 逐键同构**、`consumedMana` 的 `null`（= 取消消耗）是**有语义的值** |

**与其它文档的分工**：

| 想知道 | 读 |
|---|---|
| **往游戏里加内容 / 配置能改到什么** | **本文** |
| 写模组（目录、`main.json`、`Mod` API、四类内容模板、14 条踩坑） | `MODDING-GUIDE.md` |
| 玩家视角：数值怎么调、命令怎么用、目录结构 | `README.md`（「🎛️ 数值怎么调」一节） |
| 这套配置系统**为什么**长这样、每一步的验收数字、踩过的坑 | `project_analyses/EXTERNAL-DATA-LOADING-2026-10.md`、`CONFIG-LOADING-DECOUPLING-2026-10.md` |
| `/data` 与数据名契约 | `notes_for_llm/70-DATA.md` §5.10 |

---

## 八、不确定项 / 本文没做的事（诚实清单）

1. **"手感 / 平衡"类结论本文一条都没有**。所有"能用了"都指**链路通了**（写文件 → 重启 → 数值变了），
   而且**只有盗火行者 `hpMax: 7` 那一条是你亲自实跑过的**。改配置之后"打起来爽不爽"只有你能判断。
2. ~~**`EntityData.default.json` 恒为空壳（本文 §1.4 末尾那条）。结论我读过代码、是确定的**
   （`writeAll` 只在 `loadGameRules` 那一刻跑一次，而那时注册表还空着），
   **但我没有实跑验证"删掉它重启"的结果** —— 建议你顺手试一次，若真是空壳，
   README 里那句话就该改掉（那属于 `README.md`，本文不改它）。~~
   **✅ 2026-10-03 已修**：它现在由 `ConfigLoader#healDefaultConfigFiles()` 在**注册表已经满了之后**
   按"**空壳才写、有内容一个字节不动**"补（`ConfigDefaultWriter#writeReferenceIfNeeded`），
   所以 **README 里"删掉会重新生成一份全量默认值"对这一份是真的**。
   ⚠️ **仍然只有用户能验最后一步**：真正"删掉它 → 重启游戏"看它有没有被填满 —— AI 不启动游戏，
   只在自测里用 `out/` 临时目录跑过同一条代码路径（自测 6 条）。
3. **你手上那份 `EntityData.json` 里还有 `drunkenSword:drunkenSwordsman`** 与 3 个酒剑仙技能
   （上一轮自愈写进去的）。自愈**只补不删**，所以它们会留着；**它们不影响任何东西**
   （`common` 段照旧能给它打补丁）。想清干净就手动删那几段。
4. ~~**白厄的觉醒技能仍然配不到**（§1.3 第 1 条），三条候选方案**等你拍板**。~~
   **✅ 2026-10-03 当天已落地**（你选了方案 A）：它们登记成技能原型、运行时走 `原型.copy()`，
   `SkillData.json` 里现在有那 5 个键（§1.1 第 14 条、§3.1 第 2 条）。
   ⚠️ **仍然只有你能验最后一步**：重启游戏看 `SkillData.json` 里有没有那 5 个键、
   改一个倍率打一局看伤害变没变 —— AI 不启动游戏，只在自测里做了数值对照
   （`hitsPerScourge` 6 → 1，伤害 `6120 → 1020`；把默认值打回去逐位相同）。
   ⚠️ **变身时间轴一个字没动**（12 火种 / 8 个额外回合 / `LastAttack` 的公式与下限全部原样）。
5. **`LastAttack` 的 `0.125` / 下限 7、`UltimateAttack` 的 12 火种 / 8 个额外回合**：
   技术上可以外置，**刻意没接**（要先问你，因为改它等于改平衡）。
6. **`EntityKeySpecs` / `SkillKeySpecs` 的标量规格一行没删** —— 本文说的"零配置层改动"指的是
   "新字段靠反射兜底自动生效"，**不是**"那张表已经不存在了"。要删表先读
   `CONFIG-LOADING-DECOUPLING-2026-10.md` §12.5 的三条阻碍。
7. **`mods/liXiaoYanPlus/` 的配置我没跑过**：它在你的实跑里"燃点上限 +5 ✅"，
   但 `config/data/liXiaoYanPlus.json` 这个文件**游戏不会替你生成**（`config/data/` 是你手写的地方）。

---

*本文由 AI（DeepSeek）生成，2026-10-03。*
