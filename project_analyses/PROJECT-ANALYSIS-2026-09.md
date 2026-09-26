# FightGameReforged 项目分析（2026-09-26 版）

> ✅ **当前基准文档**（2026-09-26 · 复核范围：`src/` 下全部 **199 个 Java 文件 / 28,820 行**、`mods/` 3 个模组、`config/`、`build.gradle` 与根目录脚本）。
> 它取代了这几份的整体分析定位：[`ANALYSIS-review4.md`](ANALYSIS-review4.md)（第 4 轮 · 118 文件 / ~8500 行）、
> [`ANALYSIS-2026-08.md`](ANALYSIS-2026-08.md)、[`STATUS-2026-08-review2.md`](STATUS-2026-08-review2.md)、
> [`STATUS-2026-08-review3.md`](STATUS-2026-08-review3.md)、[`ProjectStatus.txt`](ProjectStatus.txt)、[`ANALYSIS.md`](ANALYSIS.md)。
> **不**取代：[`COMMAND-SYSTEM-2026-08.md`](COMMAND-SYSTEM-2026-08.md)（命令系统的设计细节）、
> [`FLAME-REAVER-2026-09.md`](FLAME-REAVER-2026-09.md)（盗火行者的官方数据对照与逐条实现记录）、
> [`NBT-AND-DATA-COMMAND-2026-09.md`](NBT-AND-DATA-COMMAND-2026-09.md)（NBT 与 `/data` 的**可行性分析**，
> 尚未实现）——这几份仍是各自主题的权威文档。
>
> **可信度分级**（沿用 `TIPS_FOR_LLM.md` §0 的约定；**2026-09-26 起 AI 已能本地编译 + 跑自测**，见该节）：
> **已实测** = 用户亲自跑过并反馈过；**已核实** = 逐行读过当前源码确认；**未验证** = 仅读码推断，没进过游戏。
> 本文所有"仍在/已修复"的结论都按**当前源码**重新核对过，旧文档里的行号一律不可信。

---

## 零、TL;DR

**一句话**：项目从"能跑的原型"长成了"带命令系统 + 一个完整 BOSS + 模组体系的文字游戏"（代码量是 2026-08 的 2.7 倍），
但**框架层的老账只还了一半**：13 条框架缺陷里 4 条已修、7 条部分修复、2 条仍在；
**模组系统的 4 个崩溃级缺陷一条都没修**；内容层 16 项里 9 项已修。

| 范围 | 已修复 | 部分修复 | 仍在 |
|---|---|---|---|
| 框架层（旧 §4.x/§5.x，13 条） | 4 | 7 | 2 |
| 内容层（旧 §6.x，16 项） | 10 | 1 | 5 |
| 模组系统（旧 §8.x，11 组） | 2（README 权限提示、id 归一） | 1（编码风险已降低） | 8 |
| 数值观察（旧 §7.1–§7.3，5 条） | 1（暴击系统关闭，已确认） | 2 | 2 |
| 本次**新发现** | 3 条已修（§6.4 N1/N2/N8） | — | 6 条（§6.4） |

**2026-09 这一轮的产出**（都是新增，不是修旧账）：

1. **命令系统**（29 文件 / 4647 行）：MC 风格节点树 + 选择器 + 命名空间规则，且**没有改动游戏原有输入方式**；
2. **盗火行者**从"设计稿"变成可打通的完整 BOSS（两阶段、两种容器、共祭协同、吸收、镣锁复活、破容器之赏）；
3. **模组「酒剑仙」** + `MODDING-GUIDE.md`（AI 与人都能照着写模组）；
4. **输出层重做**：每回合 7 行 → 2 行、颜色（`ConsoleColor`）、攻击行集中打印、**对象阵营标注**（与回合头同一判据）；
5. **四份文档同步** + 两道自检脚本（`check-sources.ps1` / `test-command-system.ps1`）。

**已实测（2026-09-26 用户实跑，全部正常）**：`test-command-system.ps1` **252/0**、
控制台**颜色与阵营标注**的实际观感、盗火行者实战（共祭 / 二阶段 / 破容器之赏）、
模组「酒剑仙」（含**满层大招**与**醒酒汤**）。同日修掉了 §4.4 的两处过期 javadoc。

**2026-09-26 第二轮修复（代码已改完，待实跑）**：李晓焰【燃点】三连（§6.4 N1/N2 + §6.2 内容层新发现）
与 `World.applyRegisteredId` 的 id 归一（§6.3）；随后按用户拍板的 **A 套餐加强了李晓焰**
（燃点攒到上限为止 / 高燃点额外伤害 0.6 / 战技自伤 15% / 防御成长 5）并修掉战技的 ×1.5 累乘。
自测新增 10 条断言（李晓焰 5 + id 归一 2 + `@s` 跟随 3）→ **新基线 262**。
另外第三次实跑又暴露了一个命令系统的问题并当场修掉：**`@s` 指向的是"选人时最后选的角色"**，
而不是"当前正在操作的角色"（§6.4 N8 / §3.1）。
随后按用户要求做了 **NBT 数据层 + `/data`（第一版：只读 `get` + 写回 `merge`）**——
新包 `game/data/` 14 文件 1509 行 + `DataCommand`，自测 +39 条（基线 252 → **301**）。

**最该先处理的三件事**：

1. **模组系统的 4 个崩溃级缺陷**（§6.3 模组表前 4 行）—— 一个坏模组能让游戏起不来，而且**失败不进日志**；
   想让别人写模组，这是地基。
2. **`FightEndEventListener.java:45-55` 的奖励按人头重复发放**（§8.1）—— 3 人 2 奖励 = 发 6 份，
   玩家可见；是"设计还是 bug"需要你拍板（多人时想发几份）。
3. **两类潜伏型问题**：`whenFightEnds` 不清 `damageReductions`（§6.4 N3）、
   白厄注销的是副本监听器（N4）、`Effect.copy()` 与效果列表浅拷贝（§6.2 §6.11）
   —— 现在没炸是因为没人踩，但都属于"一旦有人写带状态的效果就会连环出问题"。

---

## 一、规模与结构（2026-09-26 快照）

### 1.1 关键数字

| 维度 | 现状 |
|---|---|
| 类型 | 命令行回合制文字战斗游戏，纯 Java，**无游戏引擎** |
| 语言 / 构建 | Java 25（`build.gradle` 的 `targetCompatibility`）；Gradle + shadow 插件打 fat jar |
| 产物 | `build/libs/FightGameReforged-1.2.1.jar`（`archiveFileName` 写死，版本号来自 `build.gradle`） |
| 依赖 | 仅 `org.json:json:20240303`（自测脚本用本地 `lib/json-20231013.jar`，`lib/` 已 gitignore） |
| 入口 | `cn.gfhnv.game.GameStarter` → `GameMain.main` |
| 源码根 | `src/`（不是 `src/main/java`，见 `build.gradle` 的 `srcDirs`） |
| 代码量 | `src` **199 文件 / 28,820 行**；`mods` 12 文件 / 750 行 |
| 与 2026-08 对比 | review4 那轮是 118 文件 / ~8500 行 → **+81 文件 / +17,800 行（≈3.1 倍）**，增量几乎全在命令系统、盗火行者、自测、模组与 NBT 数据层 |
| 许可 | MIT（`LICENSE.txt`），作者不接受 PR |

### 1.2 代码分布（按包，前 15 大）

| 包 | 文件 | 行数 | 说明 |
|---|---|---|---|
| `game.system.command` | 29 | 4714 | **命令系统（AI 编写）**，占全项目 19% |
| `game.entity` | 3 | 2440 | `Entity` / `LivingThing`（`LivingThing.java` 单文件 2180 行） |
| `debug_tools` | 5 | 2189 | 自测与试算（`TestCommandSystem.java` 2144 行） |
| `officialStuff.customCommands` | 10 | 2054 | 官方命令实现（9 个命令 + `?` 别名 + 注册入口，含 `DataCommand`） |
| `game.data` | 15 | 2178 | **NBT 数据层（2026-09 新增）**：`NbtTag` 体系 / `Snbt` / `DataPath` / `DataBridge` |
| `officialStuff.customEntity.monsters` | 4 | 973 | 含 `FlameReaver.java` 865 行 |
| `officialStuff.customEffect.universalEffects` | 11 | 938 | 通用效果（`Frozen` / `Taunt` / 各类增伤…） |
| `officialStuff.customSkill.flameReaverSkills` | 10 | 847 | BOSS 技能表 |
| `entityController` | 3 | 781 | 玩家 / 固定顺序 / 通用控制器 |
| `game.mod` | 4 | 608 | 模组基类与加载器 |
| `game`（根包） | 3 | 583 | `GameStarter` / `GameMain` |
| `officialStuff.customEntity.players` | 3 | 542 | 白厄 / 李晓焰 / `PlayerOne` |
| `officialStuff.customEffect.flameReaverEffects` | 5 | 536 | 侵蚀 / 苦痛缠绕 / 共祭 / 镣锁 / 破容器之赏 |
| `game.system.fight` | 5 | 511 | `Fight` / `TurnManager` / `TurnEntry` |
| `game.skill` | 1 | 505 | `Skill`（技能基类，单文件） |
| `game.system.thinkingSystem` | 4 | 486 | AI 决策与标签（`Tag` / `TagType`） |
| 其余 31 个包 | 88 | 5363 | 事件、效果、物品、世界、日志、配置、物理、接口… |

### 1.3 热点文件（改动风险的集中地）

| 行数 | 文件 | 备注 |
|---|---|---|
| 2180 | `entity/LivingThing.java` | 属性、伤害、效果、打印、控制器全在这里；**改任何战斗行为都会碰它** |
| 2391 | `debug_tools/TestCommandSystem.java` | **379 条断言**（含 NBT/`/data` 的 113 条；`check`/`run`/`expectSyntaxError` 共 200+ 调用点，部分在循环里） |
| 865 | `officialStuff/customEntity/monsters/FlameReaver.java` | 一个 BOSS 的完整状态机 |
| 719 | `officialStuff/customCommands/EffectCommand.java` | `/effect` 的参数、候选与命名空间校验 |
| 642 | `system/command/EntitySelector.java` | `@s`/`@p`/`@e[type=…]` 选择器与中文编码兜底 |
| 505 | `skill/Skill.java` | 冷却、目标数、倍率、`copy()` |
| 491 | `system/command/CommandDispatcher.java` | 节点树注册与解析 |
| 476 | `system/command/CommandManager.java` | 入口、提示、编码自检 |
| 435 | `officialStuff/customEntity/summons/BrokenContainer.java` | 容器（两种形态 + 共祭 + 击杀记账） |
| 406 | `world/World.java` | 物品/实体/效果/模组注册表 |

### 1.4 官方内容清单（已核实）

| 类别 | 数量 | 内容 |
|---|---|---|
| 玩家角色 | 3 | `Phainon`（白厄）、`ActorLiXiaoYan`（李晓焰）、`PlayerOne` |
| 怪物 | 4 | `FlameReaver`（盗火行者）、`InsectBoss`（虫皇）、`CommonInsect`、`IceInsect` |
| 召唤物 | 1 | `BrokenContainer`（残破 / 完整两种形态） |
| 技能 | 25 个类 | 通用 4（`CommonAttack`/`Freeze`/`GunShoot`/`RestorationHealthSkill`）、盗火行者 10（含基类 `FlameReaverSkill`）、白厄 7、李晓焰 3、虫皇 1 |
| 效果 | 17 | 通用 11、盗火行者 5（侵蚀/苦痛缠绕/共祭/镣锁/破容器之赏）、李晓焰 1（`MemorizedHp`） |
| 物品 | 9 | 全部是药水（攻击/暴击率/暴击伤害/防御/效果/治疗/生命/穿透/速度） |
| 命令 | 9 + 1 | `kill` / `list` / `hurt` / `effect` / `data` / `execute` / `give` / `endfight` / `help`（+ `?` 别名） |

### 1.5 模组与文档

| 模组 | 内容 | 状态 |
|---|---|---|
| `mods/drunkenSword` | 「酒剑仙」：1 角色 + 3 技能 + 2 效果 + 2 物品（9 个 java 文件；`mods/` 合计 12 文件 / 750 行） | AI 编写，**部分未实测**（见 §4.3） |
| `mods/abstractLaunchingWords` | 作者自带的"抽象启动词"模组（1 文件） | 作者原有 |
| `mods/exampleModByGFHNV` | 官方最小示例（2 文件） | 作者原有 |

| 文档 | 定位 |
|---|---|
| `README.md` | 玩家视角：怎么玩、命令、模组、项目结构 |
| `MODDING-GUIDE.md` | 模组作者视角：目录约定、`Mod` API、内容类型、完整示例 |
| `TIPS_FOR_LLM.md` | **AI 交接文档**（76 KB）：硬约束、踩过的坑、当前状态；⚠️ **被 `.gitignore` 排除，不在仓库里** |
| `project_analyses/*` | 各轮分析（本文是当前基准）；专题有 `COMMAND-SYSTEM-2026-08.md`（命令系统）、`FLAME-REAVER-2026-09.md`（BOSS 数据）、**`NBT-AND-DATA-COMMAND-2026-09.md`（NBT / `/data`，已实现只读+写回的第一版）** |
| `check-sources.ps1` / `test-command-system.ps1` | 静态自查 / 全量编译 + 自测 |

---

## 二、运行时骨架：一局战斗是怎么走完的

（本节全部**已核实**，行号对应 2026-09-26 的源码。）

### 2.1 启动与输入

1. `GameStarter.main` 打印横幅（作者 / GitHub 地址）→ `GameMain.main`。
2. `GameMain` 读玩家名字（`GameMain.SCANNER = new Scanner(System.in)`，`GameMain.java:54`）→ 选角色 / 选对手 / 选奖励（多轮"输入数字或 next"）→ 打印"游戏开始"。
3. **命令系统不改输入模式**（用户的第一条硬要求）：只是在原有输入循环里插一句判断 ——
   `GameMain.java:91`（主循环）与 `PlayerController.java:49`（战斗中选技能时的输入），
   都是 `if (CommandManager.process(line)) { continue; }`，不是命令就完全按原逻辑走。

### 2.2 回合循环（`FightTurnPastListener.fightTurnPastOne`）

现在是**一个循环，不是递归**（`turnLoop: while (isDriving)`，`:123-124`；方法尾 `finally` 保证复位 `isDriving`，`:242-245`）。
进入循环的开关是 `EventBus.post(new FightPastOneTurnEvent(fight))`，由 `FightStartEventListener` 在开局时发出。

每轮做的事（顺序即代码顺序，`:124-241`）：

1. `GameMain.isInFight()` 为假 → 立即退出（`/endfight` 之类的强制结束走这里）；
2. 两个阵营列表各 `removeIf(!isAlive())`，死者进 `theDeath`（`:131-144`）；
3. `TurnManager.removeTheDeath()` 摘掉时间轴上死者的条目（`:145`）；
4. **立刻**对每个死者调 `whenLeaveFight(fight)` 做离场结算（`:156-159`）——时机见 §2.7；
5. 任一阵营空 → `EventBus.post(FightEndEvent)` 并退出循环；
6. 时间轴空（两个阵营都还有人却排不出回合）→ 兜底退出，避免 `getFirst()` 抛异常；
7. `TurnManager.sort()` → 取 `getFirst()` 作为 `presentTurn`，从表里移除，并把"当前时间"推到
   `startTime + needTime`（`:173-176`）；
8. 行动者为 `null` 或已死 → `continue` 取下一个；
9. 对 `fight.getAllEntities()` 每个实体调 `updateSelf()`（**这是每回合都会跑的"帧更新"**）；
10. 该行动者的每个技能 `nowCoolDown--`（下限 0）→ `recoverManaEveryTurn()`；
11. 打印**回合头 + 状态两行**（见 §5.1）；
12. `firstExecuteList` 逐个执行 → 若行动者已死则改 `ActionSignal.SKIP` → 打印 `getShowSpecialMes()`；
13. 按 `ActionSignal` 决定动作（见 2.4）；
14. 排下一个回合条目 / 处理"不产生新回合"（`:227-232`）；
15. `lastExecuteList` 逐个执行 → `EventBus.post(new EffectUpdateEvent(...))` → 回到循环开头。

**关键点**：循环内部只发 `EffectUpdateEvent` / `FightEndEvent`，**不再**重入 `FightPastOneTurnEvent`；
即使有外部重入也会被 `isDriving` 拦掉（`:115-120`），"一个回合被处理两次"因此不会发生。

### 2.3 时间轴（`TurnManager` / `TurnEntry`）

- `TurnEntry(行动者, needTime, startTime)`，排序键是 **`startTime + needTime`**，同时间按**速度降序**
  （`TurnManager.java:28-31`）；`needTime = 10000 / 速度`（`RoundingMode.HALF_UP`，10 位小数）。
- 进入战斗时 `TurnManager.init(fight)` 给每个实体排一条；**中途加入的实体**由 `Fight.addFighter/addEnemy`
  补一条（`Fight.java:64-83`，入列前会 `World.applyRegisteredId` 补全 id）。
- 调速 API：`advanceByPercent/advanceByAmount`（提前）、`delayByPercent/delayByAmount`（延后）、
  `getNextTurnOf(entity)`（找某个实体"下一次行动"的条目，找不到返回 `null`）。
- `getPresentTime()` **不会返回 `null`**（兜底 `ZERO`），原因写在它的 javadoc 里：手动加实体的场合没人调 `init()`，
  而 `sort()` 会对 `startTime` 做加法。

### 2.4 动作信号（`ActionSignal`）与"额外回合"

| 信号 | 语义 | 循环里的处理 |
|---|---|---|
| `NORMAL` | 正常出手 | `controller.act(fight)`，然后**排一个新回合** |
| `SPECIAL_ACTION` | 出手内容换成 `controller.getSpecialAction()` | 执行它（**先判空**，`:222-225`），再排新回合 |
| `WITHOUT_NEW_TURN` | 出手但**不产生新回合** | 额外调一次 `controller.act(fight)`，不排新回合 |
| `SKIP` | 跳过 | 既不行动也不排新回合（`SKIP` 走第 14 步的分支之外） |
| `SKIP_WITHOUT_NEW_TURN` | 跳过且不排新回合 | 同上，用于"把这一回合让出去"（例如延后 100%） |

"额外回合"的做法（白厄与盗火行者的容器奖励都是这一套）：**新建一条 `needTime = 0` 的回合条目**，
`startTime = 当前时间`，于是它必然排在下一个被取到；并且**必须** `.setExtra(true)`
（`FlameReaver#grantExtraTurn`，`:649-652`）—— `isExtra` 决定"这个回合结束时效果不递减 `lastTime`"，
漏了就变成"额外回合白送对面一轮 buff 到期"（实测踩过）。

### 2.5 伤害与减伤

- 出手统一走 `LivingThing#makeDamage(target, skill)`：构造并 `post` 一个 `DamageEvent`，然后取
  `hpBefore - hpAfter` 打印攻击行（**整行只有这一处打印**，见 §5.1）。
- 伤害公式（写在 `MODDING-GUIDE.md`，与 `damage/DamageCalculate` 对应）：
  `(生命×生命倍率 + 攻击×攻击倍率 + 防御×防御倍率 + 额外伤害) × (1+增伤) × (1−抗性) × (1+穿透)
  × 承伤倍率 × 等级防御衰减 × 单体倍率 × 暴击倍率`。
- 减伤是**乘算叠加**：`getDamageTakenMultiplier() = Π(1 − 每个来源)`，夹在 `[0,1]`；
  增删用 `addDamageReduction(source, percent)` / `removeDamageReduction(source)`，**按来源对象的身份**区分，
  同一来源重复添加是幂等的（`TIPS_FOR_LLM.md` §5.6）。
  盗火行者因此准备了**两个不同的键**：`DAMAGE_REDUCTION_KEY`（层数减伤）与 `PHASE_TWO_REDUCTION_KEY`（阶段免伤），
  共用一个键会互相覆盖（`FlameReaver.java:63` 与 `:71` 两个常量）。

### 2.6 效果结算（改效果前必读）

- `updateSelf()` 是"每个实体的每回合帧更新"（所有实体都跑）；`Effect#comeIntoEffect(owner)` 只在
  **持有者自己的回合**（含额外回合）触发一次。
- `Effect#equals/hashCode` 只看 `id + isInfinity + origin`，**`level` 不参与判等** ——
  所以"层数"这类数值放 `level`，而不要指望用 `level` 区分两个效果实例。
- 同一个 `id` 但 `origin` 不同 = 两个效果；想"同一来源重复施加只刷新"，就得让 `origin` 一致
  （【侵蚀】的 `origin` 记施加者 UUID，就是为这个）。

### 2.7 离场与战斗结束

- `whenLeaveFight(fight)` = 单个生物离场（死亡 / 被吸收），`whenFightEnds()` = 整场结束的重置（含回满血）。
  **两者不能混用**：在离场处调 `whenFightEnds()` 会把死掉的生物复活，而它已被移出阵营列表，
  于是变成"不属于任何阵营却能继续出手"的幽灵实体。
- 离场结算的**时机**必须紧跟"移出阵营列表"（`:146-159`），不能拖到回合末尾：
  容器离场要给击杀者发"额外回合 + 增伤"，而额外回合排在**当前时间点**，
  拖到回合末尾会被 `removeTheDeath()` 摘掉 = 奖励等于没发（2026-09 实测踩过）。
- 死亡判定的依据仍是 `isAlive()`（血量 ≤ 0）；改它会牵动一串逻辑，别顺手改（`FLAME-REAVER-2026-09.md` §8.4.1）。

### 2.8 写好了但没接线的东西（README 的清单 + 本文逐条核对）

| 东西 | 现状（2026-09-26 核对） |
|---|---|
| `ThinkingControllerAI`（Utility AI） | 已实现，但**没有任何生物在使用**：生物构造器普遍用 `new UniversalController(...)`；`config/gameConfig/TagConfig.json` 的权重因此对玩法**没有影响** |
| 暴击系统 | **实战中永远不会暴击**（已核实）：`DamageCalculate.java:55` 判 `Math.random() <= criticalRate`，而基础暴击率来自 `getCriticalRATE` 字段，唯一入口 `facSetCriticalRATE()` / `setGetCriticalRATE()` **没有任何实体调用过**（只有自测把它设成 `-1` 来关掉随机性）；连基础爆伤 `facSetCriticalDMG()` 也没人调 |
| 物理系统 `system/physics/` | 数据结构齐全，但 `PhysicsStateUpdateEvent` **全项目没有任何 `post`**（只有 `PhysicsEventListener` 在等它）→ 永不触发（已核实） |
| `ActEvent` | 只有类定义：无发布方、无监听器（已核实） |
| `TurnManager.nextTurn(fight)` | 定义在 `TurnManager.java:56`，**没有任何调用方**（已核实）；推进由回合循环自己 `continue` |
| `system/useItemSystem/` | 空包；实际物品逻辑写在 `PlayerController.useItem()`，`UniversalController.useItem()` 是空方法 |
| `FixOrderController` | ✅ **已上线**：盗火行者与容器按预设顺序出招（`setSkipUnusable(true)`，用不了就顺延）；`copy()` 的控制器重建表也认它，不会降级成随机 |
| `debug_tools/TestAnticipateDamage` | 入口写成 `static void main()`（`:13`，缺 `public` 和 `String[] args`）→ **不能用 `java` 直接跑**（已核实） |

这些都不影响正常游玩，但**在对外宣传"有 AI 决策系统"之前得先接线**。

---

## 三、命令系统（2026-09 的主线）

规模：`game/system/command` 29 文件 4647 行 + 官方命令 9 文件 1480 行。设计细节见
[`COMMAND-SYSTEM-2026-08.md`](COMMAND-SYSTEM-2026-08.md)，这里只记录**当前实际形态**与后续补的规矩。

### 3.1 入口与写法（已核实）

- 前缀：`/`（`CommandManager.PREFIX`）与 `#`（`ALT_PREFIX`），**两者等价**；
  判定 = 去掉首尾空白后第一个字符是其中之一（`CommandManager.java:128-129`）。
- 可用入口：`isCommand`（只判断）、`process`（执行并打印，返回是否已处理，**推荐**）、
  `execute`（返回影响对象数）、`executeResult`（返回 `CommandResult`，不打印）、`runLoop`（整段接管输入）。
- 命令写法与 MC 一致：`/kill @e[type=CommonInsect]`、`/hurt @s 100`、`/help`、`#list`。
- **`@s` / `@p` 跟着"当前行动者"走**（2026-09-26 修）：回合循环每轮取到行动者后调
  `CommandManager.followActor(actor, ourSide)`（判据是新的 `Fight#isOurSide`），
  **只在我方回合切换**。修之前 `setPlayer` 只在选人时调用一次，多角色队伍里
  最后选的角色会一直占着"玩家"的位置（用户实测：酒剑仙回合里 `/give @s …` 发给了白厄）。
- **`/data`**（2026-09-26 新增）：`/data get entity <目标> [路径]` 与 `/data merge entity <目标> <NBT>`，
  背后是新的 `game.data` 包（NBT 标签 + SNBT 文本 + 路径 + 反射桥）。
  规矩与红线集中在 `TIPS_FOR_LLM.md` §5.10，可行性分析见
  [`NBT-AND-DATA-COMMAND-2026-09.md`](NBT-AND-DATA-COMMAND-2026-09.md)。
  它给命令系统补了一块新能力：**读词器新增 `StringReader#readBalanced()`**
  （括号内的空格不断词），所以参数里终于能出现 `{hp: 20}` 这种带空格的写法。

### 3.2 架构（自造，没有第三方库）

| 层 | 类 | 职责 |
|---|---|---|
| 节点树 | `CommandNode` / `LiteralCommandNode` / `ArgumentCommandNode` | 字面量节点 + 参数节点，支持嵌套子命令 |
| 参数类型 | `ArgumentType` 及 `Bool`/`Integer`/`Long`/`Double`/`String`/`Word` 实现 | 参数解析与候选 |
| 选择器 | `EntityArgumentType` + `EntitySelector` | `@s` / `@p` / `@e[type=…]`，含中文筛选值 |
| 解析 | `StringReader` / `CommandParseHelper` / `CommandDispatcher` | 逐段匹配，失败抛 `CommandSyntaxException` |
| 执行 | `CommandRegistration` / `CommandContext` / `CommandResult` | 反射调用 `@Subcommand` 方法，把结果/错误装进 `CommandResult` |
| 门面 | `CommandManager` | 前缀判定、错误提示、编码自检、当前玩家与战斗登记 |
| 注册 | `@Command` / `@Subcommand` 注解 | 官方 `OfficialCommands.registerAll`，模组照抄 |

### 3.3 命名空间规则（用户明确要求"严格模仿 MC"）

- **官方内容**：短名即可（`/give @s healingPotion`、`/effect @s frozenEffect`）。
- **模组内容**：必须写完整 id `modid:名字`（`/give @s modOnlyItem` 会被拒绝并提示写全）。
- 判定在 `OfficialGameContent`：`MOD_ID` 常量 + `isOfficial(Item)` / `isOfficial(Effect)`（按 `World.getModList()` 判断归属）。
- **成功回显保持短名**（用户指定：回显不改，只有"输入侧"严格）。
- 报错时会同时列出"官方短名清单"和"模组完整 id 清单"，避免对着错误名字猜。

### 3.4 提示规范

- 命令不完整（例如单敲 `/give`）→ `命令不完整` + **完整用法**，用法由 `CommandNode#getSuggestedUsage()`
  拼出后续参数（`CommandManager.java:245` 附近），所以看到的是 `/give <目标> <物品> [数量]` 而不是干巴巴的 `/give`。
- `officialStuff/customCommands/HelpCommand` 提供 `/help` 与 `?`。
- 中文参数（如 `@e[type=虫皇]`）在 Windows GBK 控制台下可能到不了 JVM：`CommandManager` 有一套
  "编码兜底 + 自检"（`describeEncoding()`），**只针对 Windows 的 GBK 场景**，UTF-8 的 Linux/Termux 上不会触发。

### 3.5 与游戏状态的耦合点

- `CommandManager.setPlayer(LivingThing)`：`@s` / `@p` 的中心。**选人流程**设一次（多角色时最后选的会占位），
  战斗中由回合循环的 `followActor(actor, ourSide)` 每轮切到当前行动者（见 §3.1）。
- `CommandManager` 里的"是否在战斗中"由 `FightStartEventListener` / `FightEndEventListener` 维护，
  `/endfight` 会把它置回去并让回合循环在下一轮开头退出（§2.2 第 1 步）。
- `/endfight` 之类在**循环内部**改状态是被允许的：循环每轮开头都会重新检查，不会崩。

### 3.6 已知边界（不是缺陷，只是别误会）

- `mods/` 下的代码**不在** `check-sources.ps1` 的检查范围里（脚本只扫 `src`），模组代码的错误只能靠编译时才暴露。
- 命令系统的自测（`TestCommandSystem`）**不经过真实输入**，它直接调 `CommandManager.process/executeResult`，
  所以"控制台有没有把中文送进来"这一层它测不到（`CommandManager.java:519-522` 自己写了这个限制）。

---

## 四、内容层（2026-09 新增）

### 4.1 盗火行者（`FlameReaver`）—— 数值全在文件顶部

| 常量 | 值 | 含义 |
|---|---|---|
| `SUMMON_HP_COST_RATE` | 0.03 | 每次召唤消耗自身最大生命的比例（记进【苦痛缠绕】的账） |
| `DISASTER_POWER_ATTACK_BONUS` | 0.08 | 每层【灾难之力】的加伤（**不设上限**，用户指定） |
| `DAMAGE_REDUCTION_LAYERS` | 2 | 【永别的决绝】初始减伤层数 |
| `DAMAGE_REDUCTION_PER_LAYER` | 0.25 | 每层减伤（**同一来源**按 `layers × 0.25`，2 层 = 50%，不是相乘的 43.75%） |
| `CONTAINER_LIMIT` | **0** | 场上容器上限；`0` = 不限制（2026-09 用户指定取消上限） |
| `COMPLETE_CONTAINER_CHANCE` | 0.34 | 召唤时出【完整容器】的概率 |
| `PHASE_TWO_HP_THRESHOLD` | 0.5 | 血量 ≤50% 切二阶段 |
| `PHASE_TWO_DAMAGE_REDUCTION` | 0.7 | 二阶段免伤（**另一个来源**，与层数减伤乘算） |
| `JOINT_ATTACK_TARGETS` | 3 | 共祭"一同攻击"的共同目标数上限（官方"主目标及相邻"的映射） |

容器（`BrokenContainer`）：`HP_RATIO = 0.15`（残破容器生命 = BOSS 最大生命的 15%）、
`ATTACK_RATIO = 0.4`（攻击 = BOSS 攻击的 40%）、`COMPLETE_HP_RATIO = 0.25`（完整容器更厚）；
`CONTROL_EFFECT_IDS = {"frozenEffect"}` 对应官方的"抵抗控制类负面状态"；自己维护 `alive` 布尔量与
`lastAttacker`（用来判"完整容器被谁击杀"）。

机制要点（**已实测**，`FLAME-REAVER-2026-09.md` 有逐条记录）：

- **苦痛缠绕**是一本挂在 BOSS 身上的账（`PainEntanglement`，无限持续）：召唤时按消耗的生命记账，吸收时按账回血
  —— 玩家杀掉容器 = 这笔账永远收不回来，这是"清召唤物"的核心收益。
- **共祭**是挂在容器身上的效果（`SacrificeRite`，`origin` = BOSS 的 UUID）：一同发起攻击时**打同一个目标**
  （目标由 BOSS 选一次，最多 3 个），BOSS 本人也参与这一击；被吸收时按批次回收
  （`cloudOfDeathSummons` / `fateDrawsNearSummons` 分开记账，避免"容器只活一轮"）。
- **吸收**只走【幽冥的悼念】：场上处于共祭的容器被一次性吸收 → 回血 + 涨灾难之力（击杀 ≠ 吸收）。
- **二阶段**：70% 免伤 + 换轮转表 + 【沉默的悲叹】蓄力 → 【莫因舍弃而哭泣】；
  【镣锁】容器在受到致命攻击时由 `tryReviveLocked()` **重新召唤一只新的**（官方原文就是"重新召唤"，不是把原体捞回来）
  —— 条件是"灾难之力 ≥ 1 且自己的血量够付代价"。
- **完整容器**是奖励线：击杀者拿【破容器之赏】（增伤）+ **额外回合**（`setExtra(true)`）；放着不管则被吸收并额外充能。

### 4.2 伤害标定（改倍率前必读）

打 150 级 BOSS、0 减伤层时，**每 1.0 技能倍率 ≈ 880 伤害**（已实测）。参考值：
4 层大招 14.0 倍率 = 17249（叠了 +40% 破容器之赏）；二阶段同名招式因为 70% 免伤只剩 5174。
**含 50% 层数减伤时同样的招式只有一半**，比较两次实测伤害前先确认减伤层数与阶段。

### 4.3 模组「酒剑仙」（`mods/drunkenSword`）

| 项 | 值 |
|---|---|
| 角色 | 「酒剑仙」125 级，速度 130，生命成长 34 / 攻击成长 32 / 防御成长 12，背包 63 格 |
| 普攻「举杯邀月」 | 3.0 倍率 + 获得 2 层【醉意】，无消耗无冷却 |
| 战技「醉里挑灯看剑」 | 2.0 倍率 + 每层 +1.5 倍率，消耗 300 火法力，每层回 3% 生命，冷却 0 |
| 大招「一剑霜寒十四州」 | 需 ≥4 层【醉意】，4.0 倍率 + 每层 +2.5 倍率，消耗 800 火法力，冷却 0 |
| 效果【醉意】 | `isInfinity`，层数存 `level`（上限 10），每层 2% 减伤（满层 20%，来源键 `DRUNKENNESS_DAMAGE_REDUCTION`），origin = 持有者 UUID |
| 效果【宿醉】 | `Hangover`，默认 2 回合 |
| 物品「桂花酿」 | +4 层【醉意】+ 回 5% 最大生命 |
| 物品「醒酒汤」 | 清空全部【醉意】+ 每层回 4% 最大生命 |

**未实测**：满 10 层大招、醒酒汤、以及"层数上限夹取"在实战里的手感。

### 4.4 文档与代码不一致（本文新发现，两处 —— 已于 2026-09-26 修正）

| 位置 | 当时写的 | 实际 |
|---|---|---|
| `FlameReaver` 类注释（原 `:42`，改后见 `:42-54`） | ~~"未实现（第二步）：完整容器、二阶段、【沉默的悲叹】、【镣锁】与【为我设奠】复活。"~~ | **全部已实现**（`enterPhaseTwo` `:1009`、`LockedRite`、`SilentLament`、`tryReviveLocked` `:586`、`Kind.COMPLETE`） |
| `BrokenContainer` 类注释（原 `:28`） | ~~"【为我设奠】（阶段二）……**本次未实现**"~~ | 已实现，逻辑在 `FlameReaver#tryReviveLocked`，由 `:910-915` 的致命攻击分支调用 |

两处都只是**注释过期**，不影响运行；但会误导下一个读代码的 AI/人（这类文档漂移正是本项目最贵的一类 bug）。
**✅ 已于 2026-09-26 修正**：`FlameReaver` 的类注释换成了"已全部实现"的逐条清单（完整容器 / 二阶段 / 沉默的悲叹 / 镣锁 / 为我设奠复活，
都带方法名），`BrokenContainer` 那条改为指向 `FlameReaver#tryReviveLocked` 并说明"重新召唤的是一只**新**容器"。

---

## 五、输出层（2026-09 重做）

### 5.1 格式（"费眼睛"之后定的，别改回旧的）

| 位置 | 现在的格式 |
|---|---|
| 回合头 | `─── 现在是 <名字>#<uuid前6>（我方/敌方）的回合 ───`（青色） |
| 状态行 | `HP 当前/上限   能量 金X 木Y 水Z 火W 土V`（原来每回合 7 行 → 现在 2 行） |
| 攻击行 | `<攻击者>（我方）攻击了<目标>（敌方）  -<伤害>  → HP 当前/上限  【技能名】` |
| 伤害类效果 | `【侵蚀】<目标>（阵营）  -N  → HP x/y`（洋红前缀） |
| 关键事件 | 召唤 / 额外回合 / 二阶段 = 黄，吸收回血 = 绿 |

要点：

1. **攻击行整行只由 `LivingThing#makeDamage` 打印**（`printAttackLine`）。技能里一句都不用写：
   自己 `print("A攻击了B")` 会重复，漏了就会得到一行没有主语的 `  -1109  → HP …`
   （官方 `Counterattack` 的 6 次追加攻击就这么漏过，被用户实测抓到）。
2. 伤害显示的是**实际掉血**（`hpBefore - hpAfter`），不是算出来的值 —— 伤害修正器可能把这一击拦下，
   显示实际值才能和后面的 HP 对上。
3. **阵营标注统一走 `Fight#sideNameOf(entity)`**（在 `fighterList` 里就是我方），
   显示侧是 `LivingThing#getNameWithSide()`（灰色）。回合头、攻击行、侵蚀行、冰冻、回血、
   AI 决策行、完整容器击杀者都用这一套；**判据只有一处**，别在各处再 `contains` 一遍。
   典型场景：双方各有一只同名【残破容器】，不带阵营根本分不清谁打谁。
4. **故意没标**阵营的地方：BOSS 自家叙事（召唤/吸收/二阶段/镣锁/延后——出现的都是同一侧）
   与控制器里"没行动/顺延/无法决策"这类单人叙事。

### 5.2 颜色（`utils/ConsoleColor`，无第三方库）

- 常量：亮青（回合头/技能名）、亮红（伤害）、亮绿（治疗/吸收）、亮黄（关键事件）、
  亮洋红（侵蚀）、灰（HP/次要信息）。
- 开关：`-Ddsh.color=auto|on|off`（默认 `auto`）。
- `auto` 的判定链（**已核实**，`ConsoleColor#detect`）：
  1. 明确 `on`/`off` 直接返回；
  2. `NO_COLOR` 非空（no-color.org 约定）→ 关；`TERM=dumb` → 关；
  3. 有终端证据（`WT_SESSION` / `TERM_PROGRAM` / `TERMINAL_EMULATOR` / `ConEmuANSI` / `ANSICON` / `MSYSTEM`）
     → 开；
  4. 否则要求"标准输出真的是终端"（`System.console().isTerminal()`，Java 22+）—— 用来识别
     `> log.txt`、`| tee` 这类重定向，免得把转义序列写进文件。
- **那条"重定向就关色"的闸不能写成无条件 `if (!isTerminal()) return false;`**：
  Windows 的 MinTTY（Git Bash）是"用管道模拟 pty"，原生程序问出来永远不是 tty，一刀切会把 Git Bash 的颜色误杀。
- 跨平台：ANSI 是 POSIX 终端的老标准，Linux / macOS / Termux 天然支持，类里没有任何平台专属代码；
  Termux 已有 `openjdk-25` 包，本项目的 Java 25 产物可以直接跑（把游戏目录放 `$HOME`，别放 `/sdcard`）。

### 5.3 日志文件

`system/logSystem/LogWriter` 写 `./logs/latest.log`（超过 10 MB 自动归档成时间戳文件，`System.lineSeparator()`）。
**战斗行只进 stdout，不进日志文件** —— `CommandSender` 那条 `log()` 链路才会写文件
（命令回显、错误、初始化信息）。所以"想留一份战斗记录"只能靠重定向终端输出。

---

## 六、缺陷复核（对照 `ANALYSIS-2026-08.md` 的清单）

### 6.1 框架层（旧 §4.1–§4.11、§5.2、§5.6）

| # | 旧文档的条目 | 2026-09-26 结论 | 证据（当前源码） |
|---|---|---|---|
| 1 | §4.1 效果结算只对**第一个**效果生效 | ✅ **已修复** | `EffectEventListener.java:17-30` 已是 `while (iterator.hasNext())` + `continue`（原来是 `return`） |
| 2 | §4.2 回合靠**递归**推进 → 长战斗爆栈 | ✅ **已修复** | `FightTurnPastListener.java:123-124` 的 `turnLoop: while (isDriving)`；`TurnManager.nextTurn()` 已无调用点 |
| 3 | §4.3 `UltimateAttack` 依赖**静态** `presentTurn` | ⚠️ **部分修复** | 静态字段仍在（`FightTurnPastListener.java:26`）、两处仍无判空（`phainonSkills/normalSkills/UltimateAttack.java:63`、`Phainon.java:149`）；但"捕获了外层实例"的老 bug 已修（`Phainon.java:141-142` 用 `instanceof` 模式 + `isAnticipating()` 护栏） |
| 4 | §4.4 `Skill` 两个语义冲突的 `canUse`/`use` 重载 | ⚠️ **部分修复** | 冷却已统一（`Skill.java:288` 与 `:328` 都是 `setNowCoolDown(getCoolDown())`）、`tags` 已复制（`:67-73`）；但 `UniversalController.java:115` 仍给三参 `canUse` 传 `null` |
| 5 | §4.5 `getCriticalDMG()` 取错字段 | ✅ **已修复** | `LivingThing.java:1996`：`criticalDMG * (1 + criticalDMGEnhancePercent) + criticalDMGEnhanceAmount` |
| 6 | §4.6 `Mana` 只有一个上限、元素是装饰 | ⚠️ **部分修复** | 元素已不是装饰（`Skill.java:189-200` 按 `elementSort` 选池扣费、`LivingThing.java:529-561` 主元素上限 +200）；但构造器仍把"消耗 0"与"上限 0"绑在一起（`Mana.java:13`，`setAmountMax` 全项目零调用）——该形态当前不可达 |
| 7 | §4.7 `canUse()` 有副作用（扣蓝） | ❌ **仍在** | `RestorationHealthSkill.java:37` 在判定里扣蓝；`PlayerController.java:118` + `:184` 一次施法会走 2~3 次扣费路径 → **一次治疗实扣约 3 倍**。"蓝扣光必死循环"不成立（还能改选别的技能），但技能全不可用时 `while(true)` 没有出口 |
| 8 | §4.8 `Skill.extraDamage` 只增不减 | ⚠️ **部分修复** | 整数除法与"加算/减算配对"已修（`CommonAttack.java:24-45`、`PyrohemicPumping.java:41-58`）；但**全项目仍无重置点**，`PyrohemicPumping.java:34` 的 `×1.5` 没有上限 —— 与 `Skill.java:46` 注释"伤害计算后重置为零"**自相矛盾** |
| 9 | §4.9 `DamageEvent` 从未被 `post` | ✅ **已修复** | `LivingThing.java:1903-1904`（`makeDamage` 里 post），`DamageEventListener` 现在真的会被调用；"每受击 +1 燃点"链路已通 |
| 10 | §4.10 白厄觉醒数值污染 / "永久无敌" | ⚠️ **部分修复** | 无敌已修（`CalamitySoulscorchEdict.java:50` + 同源幂等 + 比例夹到 `[0,1]`；`Counterattack.java:35` 按来源移除）；**未修**：`Counterattack.java:55` 硬编码 `setAtkMagnification(1)`、`:52` 的 `enemies.getFirst()` 遇空表必炸、`CalamitySoulscorchEdict.java:56-57` 对可能为 `null` 的 `getNextTurnOf()` 直接解引用 |
| 11 | §4.11 `copy()` 出来的实例丢一半状态 | ⚠️ **部分修复** | 已复制：技能（逐技能 `copy()`）、`showSpecialMes`、`damageModifiers`、`damageReductions`；**未复制**：攻/防/速/生/暴的 Enhance 类字段、五元素穿透与增伤、`individualMultipleArea`、`extraDamage`（`LivingThing.java:111-170`） |
| 12 | §5.2 `Inventory` 空背包判定与奖励 | ⚠️ **部分修复** | 堆叠改为按注册表 id 判等（`Item.java:122-137`）、`ANiceSword` 已重写 `copy()`（`:22`/`:33`）；**未修**：`FightEndEventListener.java:45-55` 仍给**每个存活角色**发全部奖励（3 人 2 奖励 = 6 份） |
| 13 | §5.6 `isAlive()` 有副作用 | ❌ **仍在**（旧描述偏重） | `LivingThing.java:1586-1591`：HP ≤ 0 时会复位 `ActionSignal` 并清 `specialAction`；并不是旧文档说的"每回合无条件抹掉" |

### 6.2 内容层（旧 §6.1–§6.12）

| # | 旧文档的条目 | 2026-09-26 结论 | 证据（当前源码） |
|---|---|---|---|
| 1 | §6.1 李晓焰普攻加算/减算判据错位（给敌人回血） | ✅ **已修复** | `actorLiXiaoYanSkills/CommonAttack.java:24-28` 用 `wasHigh` 快照判断 + `:43-45` 只扣回确实加过的那次；`DamageCalculate.java:124` 还有 `Math.max(0, damage)` 兜底 → 负伤害已不可能 |
| 2 | §6.2 `ANiceSword` 未重写 `copy()` | ✅ **已修复** | `ANiceSword.java:32-34`；奖励与选择流程改走 `item.copy()`（`FightEndEventListener.java:53`、`GameMain.java:250`） |
| 3 | §6.3 `AttackEnhance.isOn` 恒为 false（每回合重复叠加） | ✅ **已修复** | `AttackEnhance.java:69` `isOn = true;`；同类 7 个强化效果都已是"`if (!isOn)` 守卫 + copy 传递 + 到期复位"的写法 |
| 4 | §6.4 `FightStartAndSelectEventListener.listen2` 永不执行 | ✅ **已修复** | 该类只剩 `:10-11 listen(SelectTargetEvent)`（`listen2` 已删）；开战加成内联进 `Phainon.java:328-329` |
| 5 | §6.5 `AwakeEndListener` 的注销是死代码 | ❌ **仍在**（目前无害） | `Phainon.java:314-318` 注销的是 `UniversalController.setSkills`（`:95-101`）复制出来的**副本**监听器；危害被 `AwakeEndListener.java:39` 的自注销抵消 |
| 6 | §6.6 `Frozen`/`SkipTurn` 状态残留（晕眩后崩溃链路） | ✅ **已修复** | 三个方向都堵了：`Frozen.java:114-119` 到期只复位信号（注释说明**故意**不清 `specialAction`）、`FightTurnPastListener.java:219-225` 执行前判空、`EffectEventListener` 的 `return`→`continue` |
| 7 | §6.7 `isListenerRegister` 的 `static` 竞争 | ❌ **仍在** | `Phainon.java:38` / `:303` / `:325-326`；影响面已缩小（开战加成内联后，只剩 `SelectTargetEvent` 那一条监听器可能被顶掉） |
| 8 | §6.8 死亡实体在战斗中途被 `whenFightEnds()` 处理两次 | ✅ **已修复** | 循环里改调 `whenLeaveFight`（`FightTurnPastListener.java:156-158`），`whenFightEnds` 只在战后调一次（`FightEndEventListener.java:29-34`）。注：`Fight.allEntities` 从不剔除死者，所以战后它们仍会被清一次 —— 那是设计，不是重复处理 |
| 9a | §6.9 觉醒 `extraAbilityTier` 永久残留 | ✅ **已修复** | `Phainon.java:56` 记 `appliedExtraAbilityTier`，回滚量与累计施加量一致（`:291-293`、`:347-349`） |
| 9b | §6.9 第二局白厄没有火种 | ✅ **已修复** | 每局角色是注册表模板的副本，模板 `coreflame = 15`、开战 `:328-329` 再 +1 |
| 9c | §6.9 锁血中断导致终结技跑第二次、`extraTurns = -1` | ✅ **已修复** | `Phainon.java:148` 先 `clearAwakenExtraTurns()`（按引用从时间轴摘除）、`:142` 的 `pendingLastAttack` 防重复排队；`LastAttack.java:33-34` 还夹了 `Math.min(7, extraTurns)` |
| 9d | §6.9 "+75% 减伤"不在收尾回滚 | ❌ **仍在** | 加：`CalamitySoulscorchEdict.java:50`；删：只有 `Counterattack.java:33-36`。觉醒一次反击都没发生就结束时，减伤留到战斗结束（`whenFightEnds` 也不清 `damageReductions`，见 §6.4 N3） |
| 9e | §6.9 `extraTurns` 不在 `whenFightEnds` 复位 | ❌ **仍在** | `Phainon.java:288-320` 全段没有 `setExtraTurns(0)`，唯一清零点是 `AwakeEndListener.java:35`（依赖事件被派发到） |
| 10 | §6.10 燃点监听器每次开大都注册 | ✅ **已修复**（2026-09-26） | 原症状：每次开大都 `EventBus.register(new DamageEventListener())`，而 `LivingThing#addEffect` 合并同 `id + origin` 效果时会**丢弃新实例** → 第 N 次开大后每次受击 +N 层燃点。现在只在"身上没有锁定效果"时注册，且监听器绑定自己的效果实例、发现它不在身上就自注销（`UltimateAttack#applyMemorizedHp` + `DamageEventListener`） |
| 11 | §6.11 内容层死代码清单 | ⚠️ **部分修复** | 已活：通用强化效果、9 种药水、无视防御钩子、`SkipTurn`、`isInfinity` 效果；仍死：`EffectTags` 的 `SKIP_ACTION`/`CAUSE_DAMAGE` 全项目零引用、`POSITIVE` 只写不读、`LivingThing.extraDamage` 无写入点、`Effect.copy()` 运行期零调用（`LivingThing.java:141` 效果列表仍是浅拷贝） |
| 12 | §6.12 官方内容注册的幂等性 | ❌ **仍在** | `Mod.java:231-253` 靠 `contains` 去重（同一实例幂等 ✓），但 `:155-200` 的 `addItem/addEntity/addEffect` 每次都拼 `MOD_ID + ":" + id`；重复 `new OfficialGameContent()`（新 UUID、同 id）仍会重复入表并叠前缀 |

**内容层新发现**：`actorLiXiaoYanSkills/UltimateAttack.java:42` 的 `enhanced = true;` 无条件为真，而 `:58` 又有 `if (getIgnition() < 8) return;` 的提前返回 —— **燃点恰好为 7 时开大，会净减 `0.5 × 生命上限` 的额外伤害**。普攻与战技后来都用 `wasHigh` 快照修过（§6.1），唯独大招没跟上。
**✅ 2026-09-26 已修**：改用 `wasHigh` 快照配对（与普攻/战技一致），并把三个技能里写死的 `8`/`0.5` 收进 `ActorLiXiaoYan` 顶部的常量组（`HIGH_IGNITION` / `MEMORIZE_*`…），避免再次改漏一处。

### 6.3 模组系统与数值（旧 §7、§8）

#### 模组系统（旧 §8.1–§8.5）

| 旧条目 | 2026-09-26 结论 | 证据 / 说明 |
|---|---|---|
| §8.1(a) 模组静态块抛错 → **启动即崩**（只 `catch (Exception)`） | ❌ **仍在** | `ModLoader.java:115` 的 `Class.forName(name, true, loader)` 会执行静态块，而 `:45`/`:148` 只接 `Exception`，`Error` 会穿透到 `GameMain` |
| §8.1(b) 一个坏模组让**其后所有模组静默不加载** | ❌ **仍在** | `GameStartEventListener.java:18-20`：`if (m == null) { return; }`（应为 `continue`） |
| §8.1(c) `invokeWhenLoaded()` 抛异常无人接 | ❌ **仍在** | `EventBus.java:92-97` 抛 `RuntimeException`，而 `GameMain.java:68` 的 `EventBus.post(new GameStartEvent())` 没有 try/catch |
| §8.1(d) `URLClassLoader` 被 try-with-resources 提前 `close()` | ❌ **仍在** | `ModLoader.java:110-113`；只靠 `:123-143` 的"全量预加载"兜底 |
| §8.2 资源与运行时（`Files.walk` 未关、`StandardJavaFileManager` 无 finally、`delete()` 不检查返回值、每次删 `bin/` 全量重编译、硬编码 `./mods`、加载失败不进日志） | ❌ **全部仍在** | `ModLoader.java:125` / `:185-204` / `:226-230` / `:93-107` / `:25` / `:46`、`:149` |
| §8.3 编码用平台默认字符集 | ⚠️ **风险已降低** | `JSONHelper.java:17` 仍是 `new FileReader(file)`，但 JDK 18+ 默认 UTF-8（JEP 400）且启动脚本/README 都显式 `-Dfile.encoding=UTF-8`。**另一处更阴**：`ModLoader.java:163` 按 UTF-8 读 `.java`，GBK 编码的模组源码会被静默跳过，然后编译器报"找不到符号" |
| §8.4 一致性与陷阱（`getClassByName` 在 `Mod(String)` 上 NPE、两处 javadoc 说反、`addXxx` 不幂等、绕过 `addXxx` 直接进注册表、`removeXxx` 不从注册表注销、父优先委托无法遮蔽游戏类、模组不能带资源或第三方 jar、`OfficialModInformation` 是错误示范、两个示例模组主类同名） | ❌ **全部仍在** | `Mod.java:82`/`:42`/`:51`/`:156-158`/`:231-253`/`:166-189`；`ModLoader.java:110-138`/`:161`；`OfficialModInformation.java:7`；`mods/*/code/com/gfhnv/mods/mainClass.java` |
| §8.4 ★ **`World.applyRegisteredId` 按 class 归一 id** | ✅ **已修复**（2026-09-26） | 原症状：`World.java:247-254` 取**第一个同类模板**的 id，而官方注册了两个同类不同 id 的容器（`OfficialGameContent.java:59-61` → `BrokenContainer.java:233-241` 的 `Kind.BROKEN`/`Kind.COMPLETE`）→ 完整容器的运行期 id 被改写成 `brokenContainer`。现在 `registeredIdOf` **先按短名精确匹配**，匹配不到才退回"同类第一条"，实体/物品/效果三个入口共用；自测新增 2 条断言 |
| §8.4 内容注册顺序不可复现 | ❌ **仍在** | `ModLoader.java:31-37` 按 `modDir.listFiles()` 的原始顺序（未排序）加载；谁先注册谁在 `registeredIdOf` 里胜出 |
| §8.5 安全（无沙箱、同 JVM 同权限、可反射改游戏状态、可用官方 `MOD_ID` 注册伪内容、无签名校验） | ❌ **仍在** | `ModLoader.java:115-117`；`EventBus.java:23` `setAccessible(true)`；`Mod.java:56-58` 无 MOD_ID 唯一性校验。JDK 25 已禁用 SecurityManager（JEP 486），要隔离只能走独立进程 |
| §8.5 "README 必须写权限提示" | ✅ **已落地** | `README.md:333-334`、`MODDING-GUIDE.md:369-370` |

#### 数值观察（旧 §7.1–§7.3）

| 旧条目 | 2026-09-26 结论 | 证据 / 说明 |
|---|---|---|
| §7.1(a) 冰虫 `Freeze` 的 7.5 倍率"一击必杀" | ⚠️ **部分修复** | `Freeze.java:16` 的 7.5 倍率还在，但 `:39-44` 只 `addEffect(new Frozen())`、**从不调 `makeDamage`** → 实际零伤害。副作用：AI 的预期伤害仍按 7.5 倍率估算，判断会偏 |
| §7.1(b) 暴击系统整体关闭 | ✅ **结论成立**（本文独立核实） | `DamageCalculate.java:55` 判 `Math.random() <= criticalRate`，而基础暴击率无人设置（`facSetCriticalRATE()` 全项目零调用）→ 恒为 0 |
| §7.1(c) 普攻自带 +200% 增伤 | ❌ **仍在** | `CommonAttack.java:31` 在 `:33` 造成伤害**之前**给自己挂 `DamageEnhanceEffect(1, 1)`（= ×3）；`GunShoot.java:31` 同理 → 技能倍率的差距被系统性压平 |
| §7.2 虫皇与描述不符 | ⚠️ **部分修复** | `InsectBoss.java:20` 的 `BASE_HP_MAX = 80000`（不再是 59800），旧的"3~5 回合打死"已不准；"高血低攻 + 无上限铺场"仍成立 |
| §7.3 元素克制是单边的 | ❌ **仍在** | 怪物土抗高、火抗低（`InsectBoss.java:23`、`CommonInsect.java:15`、`IceInsect.java:16`），而玩家三个角色的金/水抗全是 0（`PlayerOne.java:21`、`ActorLiXiaoYan.java:35`、`Phainon.java:89`）；加上怪物 150 级对玩家 125 级 |

### 6.4 本次复核**新发现**的问题（旧文档里没有的）

| # | 问题 | 证据 | 影响 |
|---|---|---|---|
| N1 | 李晓焰"燃点 ≥10 免死"触发后燃点被设成**负数** | `ActorLiXiaoYan.java:59` 的 `setIgnition(ignition - 10)` 读的是闭包外层（模板实例）的 `ignition`，而 modifier 是引用共享的（`LivingThing.java:138` `damageModifiers.addAll`） | 副本触发免死后燃点变成 `3 − 10 = −7`，此后攒层数要重新爬 —— **✅ 2026-09-26 已修**：改读 `victim.getIgnition()`，并给 `setIgnition` 补上下限夹取（原来只夹上限） |
| N2 | 重复开大会**泄漏监听器**，并使"每受击 +N 燃点"变成 N 倍 | `actorLiXiaoYanSkills/UltimateAttack.java:49` 每次都 `EventBus.register(new DamageEventListener())`，而 `LivingThing.java:1430-1432` 在已有 `MemorizedHp` 时会丢弃新效果；`EventBus.java:100-102` 是按身份注销 | 第 N 次开大后，每次受击燃点 +N —— **✅ 2026-09-26 已修**：只在没有锁定时注册 + 监听器绑定自己的效果实例、失联即自注销 |
| N3 | `whenFightEnds()` **不清减伤** | `LivingThing.java:1876-1885`；`AwakeEndListener.java:23` / `Phainon.java:298` 只把 `absorbDamage` 置 false | 觉醒期间一次反击都没发生就结束战斗时，弑魂焚诏的 75% 减伤会一直挂到战斗结束 |
| N4 | `Phainon.java:316` 注销的可能是**副本**监听器 | 它从 `getController().getSkills()` 里取 `AwakeEndListener`，而 `UniversalController.setSkills`（`:95-101`）与 `UltimateAttack.copy()` 都会换实例 | 觉醒被打断的场景下注销不掉，监听器泄漏 |
| N5 | 模组物品忘了重写 `copy()` 时，玩家只看到"输入错误" | `Item.java:182-184` 现在是 `throw new RuntimeException(...)`，而 `GameMain.java:250` 选奖励时对每个注册物品调 `copy()`，异常被 `:266` 的 `catch (Exception)` 吞掉 | 真正的报错原因（"请重写 copy()"）永远到不了玩家眼前 —— 这正是 `MODDING-GUIDE.md` 反复强调要重写 `copy()` 的原因 |
| N6 | `mods/drunkenSword/main.json` 里的 `"modID"` 是**死字段** | `ModLoader.java:65-69` 只读 `name`/`author`/`description`/`mainClass`/`version`；真前缀来自 `DrunkenSwordMod.java:45` 的 `super(MOD_ID, modInfo)`。而 `MODDING-GUIDE.md:74` 还把它当成"允许的多余键"的示例 | 照抄示例会以为改 json 就能改命名空间 |
| N7 | `ConfigLoader` 的写读编码不一致 | `ConfigLoader.java:166` 用平台默认字符集 `getBytes()` 写出，`:124` 用 UTF-8 读回 | 当前默认配置全是 ASCII 所以无害；一旦默认值里出现中文，程序会把自己写的文件读成乱码 |
| N8 | **`@s` / `@p` 指向的不是"当前操作的角色"**（2026-09-26 由用户实跑日志发现） | `CommandManager.setPlayer` 只在 `GameMain.java:177-178` 的选人流程里调用，多角色队伍里**最后选的角色一直占位**；战斗中的输入（`PlayerController.nextLine`）不会切换它 | 实测：酒剑仙的回合里 `/give @s drunkenSword:osmanthusWine` 发给了白厄/卡厄斯兰那（同一 uuid `#512d5d`）—— **✅ 已修**：回合循环调 `CommandManager.followActor(actor, side)`，只在我方回合把我方行动者设为 `@s`（§3.1） |
| N9 | 已死目标仍会被打一下，日志出现 `-0  → HP 0/12000` | `LivingThing#makeDamage` 不检查目标是否已死；同一批反击的目标列表是"出手前"算好的（用户日志里【灾厄-弑魂焚诏的反击】打了两下 0 血残破容器） | 纯观感问题（伤害确实是 0），但"打尸体"的日志很扎眼；**未修**（要不要"跳过已死目标"是行为决定，等用户拍板） |

> 说明：旧文档把"额外回合里有限时长效果不递减 `lastTime`"当成缺陷，**那条是设计决定**（`EffectEventListener.java:19-21`，见 `TIPS_FOR_LLM.md` §5.7），不是新问题 —— 已从缺陷清单里去掉。

---

## 七、工程化与协作约定

### 7.1 两道自检

| 脚本 | 做什么 | 现状 |
|---|---|---|
| `check-sources.ps1` | **静态**自查：括号平衡、UTF-8 BOM、缺 import、多余 import、字段重复（可选：重复方法签名） | 纯 ASCII（避免中文在控制台被搞坏）、不写任何文件；跑一次 = `Java files: 183` + `CHECK OK` |
| `test-command-system.ps1` | `javac` 全量编译 `src` 到 `out/cmdtest` → 跑 `cn.gfhnv.debug_tools.TestCommandSystem` | 编译成功即"全量编译检查"；断言基线 **379/0**（301 由用户实跑确认，之后 AI 又加 78 条并自己跑过；需要 `lib/json-20231013.jar`） |

`check-sources.ps1` 是"快而窄"的第一道（括号 / BOM / import / 字段重复，比 javac 快，
而且能报 javac 不报的"未使用 import"）；`test-command-system.ps1` 是"权威"那道
（全量 javac + 301 条断言）。**AI 从 2026-09-26 起也能自己跑这两件事**：
项目自带 JDK（`tool_for_llm/zulu-25`），做法见 `TIPS_FOR_LLM.md` §0。

### 7.2 文档体系与同步责任

- `README.md`：玩家视角的入口，含"各个系统都在哪"的对照表；
- `MODDING-GUIDE.md`：模组作者视角（含攻击行格式、`Mod` API、完整示例）；
- `TIPS_FOR_LLM.md`：AI 视角的坑清单（**每次踩坑都要回写这里**）；
- `project_analyses/`：本文及历史轮次。
- 约定：**改了行为就要同步这四份**，尤其是 `TIPS_FOR_LLM.md` 与 `README.md` 里的格式说明。

### 7.3 AI 的硬约束（写在这里以免下一个人重犯）

1. **编译与自测**：2026-09-26 起 AI **可以**用项目自带的 JDK（`tool_for_llm/zulu-25`）
   编译 `src` 并跑自测（做法见 `TIPS_FOR_LLM.md` §0），所以"我已本地跑过 301/0"是可信的说法；
   **但 `gradle shadowJar` 打包、`git` 操作、启动游戏仍然由用户执行**，
   手感/平衡/观感也只有用户能判断；
2. 不要引入新的第三方库（唯一依赖 `org.json`）；
3. 代码要写 javadoc、文件头署名 `@author AI（DeepSeek）生成`；
4. 不要动 `src/`（除非用户明确要求），实验代码放 `mods/`；
5. 平衡性 / API 变更**先问用户**。

### 7.4 `.gitignore` 里被排除的东西（容易踩）

| 排除项 | 影响 |
|---|---|
| `/TIPS_FOR_LLM.md` | **最有价值的交接文档不在仓库里** —— 换机器、换协作者、`git clone` 都看不到它 |
| `/ANALYSIS.md`、`/fight-log.txt`、`/diff-tmp.txt`、`sources.txt`、`/consoleprobe/`、`/out/` | AI 的临时产物 |
| `/logs/`、`/lib/`、`/build/`、`/mods/*/bin/` | 运行/构建产物（`lib/json-*.jar` 因此不在仓库里，自测脚本要求本地存在） |

`project_analyses/` 里的文档**没有**被排除，本文会被正常提交。

---

## 八、结论与下一步建议

### 8.1 按性价比排序的建议（每条都标了风险）

| 优先级 | 建议 | 为什么 | 风险 / 代价 |
|---|---|---|---|
| ✅ 已完成 | ~~`gradle shadowJar` 重打 jar + 跑 `test-command-system.ps1`~~ | 2026-09-26 用户实跑：**252/0**，且进游戏看过颜色与阵营标注 | — |
| ✅ 已完成 | ~~修两处**过期 javadoc**：`FlameReaver.java:42`、`BrokenContainer.java:28`~~ | 2026-09-26 已改成"已全部实现"的清单（§4.4） | 只改注释，零风险 |
| ✅ 已完成 | ~~NBT 数据层 + `/data`~~（用户要"图一乐"，分析见 `NBT-AND-DATA-COMMAND-2026-09.md`） | 新包 `game/data/` 15 文件 2572 行 + `DataCommand` 530 行 + `StringReader#readBalanced()`；`get` / `merge` / `modify`（set/merge/append/prepend/insert）× `entity`/`storage`；只 dump"数据"不 dump"行为"，写回走 setter 优先（`hp` 会被钳制） | 自测 252 → **379**（AI 已本地编译并跑过 379/0）；还支持路径 `{k:v}` 过滤与 `[a:b]` 切片、选择器 `nbt={…}`、`execute if data`、内存版 `storage`；路径走不通时会报出"过滤里有哪些取值 / 这一层有哪些键 / 下标越界几个"；`remove`（用户跳过）与存档**未做** |
| ★★★ | 模组系统的 4 个崩溃级缺陷（§6.3 前 4 行） | 坏模组 `Error` 穿透 = 启动即崩；`return` 当 `continue` = 后面所有模组静默消失；`invokeWhenLoaded` 无兜底 = 写错一行游戏起不来；loader 提前 close = 运行期隐患 | 都在 `ModLoader` / `GameStartEventListener` / `EventBus`，**改动集中、可自测** |
| ✅ 已完成 | ~~李晓焰燃点三连~~（§6.4 N1/N2 + §6.2 内容层新发现） | 2026-09-26 修完：免死读 `victim.getIgnition()` + `setIgnition` 夹下限；监听器只在无锁定时注册并绑定自己的效果实例；大招改用 `wasHigh` 快照配对。顺带把三个技能的魔数收进常量组 | 这轮 +7 条断言（连同后面 `@s` 的 3 条，总基线 252 → **262**） |
| ✅ 已完成 | ~~`World.applyRegisteredId` 按 class 归一 id~~（§6.3） | 2026-09-26 改成"**先短名精确匹配**，匹配不到再退回同类第一条"（实体/物品/效果共用），完整容器不再被写成 `brokenContainer` | 新增 2 条断言；语义变化只影响"同类多模板"这一种情况 |
| ✅ 已完成 | ~~**加强李晓焰**~~（用户拍板 A 套餐，2026-09-26） | 顺手治好了那条手感问题：燃点改为**攒到上限为止**（10 / 血少 15）——普攻原来在 7 层就净零，导致"高燃点加成（≥8）"与"10 层免死"几乎只能靠挨打触发；另外高燃点额外伤害 0.5 → **0.6 ×生命上限**（三个技能改为共用常量）、战技自伤 20% → **15%**、防御成长 3 → **5（572 → 820）** | 全部旋钮集中在 `ActorLiXiaoYan` 顶部常量组；自测断言覆盖下限/上限行为，**攒层速度与免死频率要进游戏验证** |
| ✅ 已完成 | ~~战技「灼血泵动」的 ×1.5 累乘~~（§6.1 第 8 条） | 旧写法把 ×1.5 累乘在 `Skill#extraDamage` 上，而全项目没有重置点（基数为 0 时又完全无效）；现在**本次算出来、伤害算完清零** | 低血倍率变成"乘在高燃点加成上"，<8 层时等于没用（已在 TIPS 里标注） |
| ★★ | `FightEndEventListener.java:45-55` 奖励按人头重复发放 | 3 人 2 奖励 = 发 6 份，玩家可见 | 是"设计还是 bug"需要你拍板（多人时想发几份） |
| ✅ 已完成 | ~~`@s` / `@p` 指错对象~~（§6.4 N8） | 2026-09-26 用户实跑日志发现：多角色队伍里"玩家"一直是选人时最后选的那个 → 回合循环改为调 `CommandManager.followActor(actor, ourSide)`（判据 `Fight#isOurSide`），只在我方回合切换 | 新增 3 条断言；`/execute as` 的一次性 source 语义不受影响 |
| ★ | 已死目标仍会被打一下（日志出现 `-0  → HP 0/12000`，§6.4 N9） | 观感问题：反击的候选列表是出手前算好的，容器中途倒下后剩下的几下会打在尸体上 | 修法之一是 `makeDamage` 里跳过已死目标 —— **会改变"受击类效果是否触发"**，需你拍板 |
| ★ | `Effect.copy()` 与效果列表浅拷贝（§6.2 §6.11） | 现在靠"所有效果都不重写 `copy()`"侥幸成立，谁写一个带状态的效果就会串味 | 改动面较大，建议等真有需求时再做 |
| ★ | 文档卫生三件：`TIPS_FOR_LLM.md` 被 gitignore、`MODDING-GUIDE.md:74` 的 `modID` 示例会误导、`mods/` 不进静态检查 | 交接文档丢了最贵；示例教错人；模组代码没有任何自动检查 | 前两件是纯配置/文档；第三件要给 `check-sources.ps1` 加个 `-IncludeMods` 开关 |
| ★ | `Item.copy()` 抛异常时补一句"请重写 copy()"（§6.4 N5） | 现在模组作者只看到"输入错误"，查不出原因 | 改 `GameMain` 的一处 catch，低风险 |

### 8.2 明确**不要**顺手做的事（TIPS_FOR_LLM 里都有血泪）

- 别改 `isAlive()` 的判据、别把离场结算挪到回合末尾（`whenLeaveFight` 必须紧跟"移出阵营列表"）；
- 别把颜色改成默认开启（旧版 cmd 会打出满屏转义序列）；也别把"重定向就关色"写成无条件 `!isTerminal()`；
- 别在攻击行/技能里自己 `print("A攻击了B")`（整行由 `makeDamage` 打印）；
- 别引入第三方库（唯一依赖 `org.json`）；
- 别在 `src/` 里做实验（实验代码放 `mods/`）；
- 别把 `Level` 当两个效果的区分依据（`Effect#equals` 只看 `id + isInfinity + origin`）。

### 8.3 还没被任何一局游戏验证过的东西

| 内容 | 状态 |
|---|---|
| 颜色（`ConsoleColor`）与**阵营标注** | ✅ **已实测正常**（2026-09-26，游戏中实际观感） |
| 模组「酒剑仙」的满 10 层大招、醒酒汤 | ✅ **已实测正常**（2026-09-26） |
| 盗火行者实战：共祭一同攻击 / 二阶段 / 破容器之赏 | ✅ **已实测正常**（2026-09-26 再次确认） |
| 盗火行者"不设上限的容器"在**超长**战斗里的表现（时间轴条目与日志量） | 结构上会显著变长；只跑过有限几局，没有长时间拉锯的记录 |
| `mods/` 里模组的编译（`ModLoader` 走进程内 `ToolProvider`） | ✅ 已实测可用（`drunkenSword` 编译、加载、进选人列表都正常） |
| Linux / Termux 上跑整套 | **未实测**（Termux 已有 `openjdk-25`，理论上 `java -jar` 直接可用，见 §5.2） |

---

## 附录 A：本文结论怎么复现

```powershell
# ① 静态自查（AI 也能跑）：应当输出 Java files: 183 + CHECK OK
powershell -ExecutionPolicy Bypass -File .\check-sources.ps1

# ② 全量编译 + 自测（用户执行）：应当 通过 379 / 失败 0
powershell -ExecutionPolicy Bypass -File .\test-command-system.ps1

# ③ 打包后进游戏（用户执行）
gradle shadowJar
.\启动游戏-UTF8.bat
```

规模数字的复现（PowerShell，按包统计行数）：

```powershell
Get-ChildItem .\src -Recurse -Filter *.java -File |
  ForEach-Object {
    $p = (Select-String -LiteralPath $_.FullName -Pattern '^package\s+([\w\.]+);' -List).Matches[0].Groups[1].Value
    [pscustomobject]@{ Pkg = $p; Lines = (Get-Content -LiteralPath $_.FullName).Count }
  } | Group-Object Pkg | Sort-Object { ($_.Group | Measure-Object Lines -Sum).Sum } -Descending |
  ForEach-Object { "{0,4} 个 {1,7} 行  {2}" -f $_.Count, ($_.Group | Measure-Object Lines -Sum).Sum, $_.Name }
```

## 附录 B：文件清单（按包）

| 包 | 文件 |
|---|---|
| `game`（根） | `GameStarter`、`GameMain`、`GameStarter` 辅助 |
| `game.entity` | `Entity`、`LivingThing` |
| `game.entityController` | `PlayerController`、`FixOrderController`、`UniversalController` |
| `game.event` / `game.eventListener` | 15 个事件类 + 6 个监听器（回合、开始、结束、效果更新…） |
| `game.effect` | `Effect`、`EffectTags` |
| `game.skill` / `game.item` / `game.inventory` | `Skill`、`Item`、`Inventory` |
| `game.world` | `World`（物品/实体/效果/模组注册表） |
| `game.mod` | `Mod`、`ModLoader`、`ModInformation`、`ModList` |
| `game.system.command` | 29 个类（见 §3.2） |
| `game.system.fight` | `Fight`、`TurnManager`、`TurnEntry`、`ActionSignal`、`ISpecialAction` |
| `game.system.thinkingSystem` | `ThinkingController`、`ThinkingControllerAI`、`Tag`、`TagType` |
| `game.system.configLoadingSystem` / `logSystem` / `mana` / `physics` | 配置加载、日志、法力、物理（其余为小类） |
| `game.damage` / `game.interfaces` / `game.annotation` | 伤害计算与接口、注解 |
| `game.officialStuff` | 官方内容（见 §1.4）；`OfficialGameContent` 是统一注册入口 |
| `debug_tools` | `TestCommandSystem`、`TestAnticipateDamage`、`actionBarTest/*` |

---

> 本文由 AI（DeepSeek）通读 2026-09-26 的源码后生成，仅作参考，**以源码为准**。
> 与本文结论冲突时：先看源码，再回写 `TIPS_FOR_LLM.md`，最后回来改本文（三处别只改一处）。
