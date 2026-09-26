# FightGameReforged 项目分析（第四轮 · 2026-08）

> ⚠️ **历史文档（第 4 轮 · 复核范围 118 个 Java 文件）**：
> 已被 [`PROJECT-ANALYSIS-2026-09.md`](PROJECT-ANALYSIS-2026-09.md)（2026-09-26 · 183 文件 / 22,880 行）取代 ——
> 本文的行号与"未修复"结论**都已过期**，保留仅供追溯分析过程。
> 同目录下其它 5 份分析（`ANALYSIS.md`、`ANALYSIS-2026-08.md`、`ProjectStatus.txt`、
> `STATUS-2026-08-review2/3.md`）均为更早轮次、已加历史标注；**要了解项目现状请看 `PROJECT-ANALYSIS-2026-09.md`**。
> 注意：本文档也**不包含命令系统**（那是之后新增的，见
> [`COMMAND-SYSTEM-2026-08.md`](COMMAND-SYSTEM-2026-08.md)），且此后代码又有变动，
> 最终仍以**当前源码**为准。

> 本文档由 AI 通读源码后生成，仅作参考，请以源码为准。
> 复核范围：`src/` 下全部 **118 个 Java 文件（约 8500 行）**、git 提交历史、未提交改动、`mods/`、`config/`。
> 同目录下的旧报告（`ANALYSIS.md`、`ANALYSIS-2026-08.md`、`ProjectStatus.txt`、`STATUS-2026-08-review2/3.md`）均已部分过时，**以本文档为准**。

---

## 一、项目概览

| 维度 | 现状 |
|---|---|
| 类型 | 命令行回合制文字战斗游戏，纯 Java，**无游戏引擎** |
| 版本 / 构建 | v1.2.1；Gradle + shadow 插件打 fat jar；另有 jpackage 产物（`build-output/`，runtime 已确认含 `jdk.compiler`，模组可编译） |
| 语言 | Java 25 |
| 依赖 | 仅 `org.json` |
| 代码规模 | **118 个 Java 文件 / 约 8500 行** |
| 入口 | `cn.gfhnv.game.GameStarter` → `GameMain.main` |
| 许可 | MIT，作者不接受 PR |

**代码量分布**（依旧很不均衡）：

| 文件 | 行数 |
|---|---|
| `LivingThing.java` | 1655（全项目 **19%**） |
| `Skill.java` | 448 |
| `ThinkingControllerAI.java` | 310 |
| `Phainon.java` | 343 |
| `Mod.java` / `ModLoader.java` | 238 / 197 |
| 战斗循环 / 事件总线 / 模组加载 | 各 100 行上下 |

项目仍是**以内容/角色为中心长出来的**，而不是以引擎为中心设计的——这也是"复制契约"（`copy()`）反复出问题的根源。

---

## 二、四轮修复的累计成果

第一轮报告（`ANALYSIS-2026-08.md`）列出的核心缺陷，**现已全部清零**。

### 2.1 引擎层

| 缺陷 | 状态 | 位置 |
|---|---|---|
| 效果系统只结算列表第一个效果 | ✅ `return` → `continue` | `EffectEventListener.java:21` |
| 回合递归推进 → 长战斗爆栈 | ✅ `turnLoop` 循环 + `isDriving` 重入闸 + `finally` 复位 + `turns` 空兜底 | `FightTurnPastListener.java:37/45/53/152-156` |
| `Frozen` 时机错位 → NPE + 挡不住行动 | ✅ 改到 `initialEffect` 布置；`whenLastTimeEnd` 不清 `specialAction`；`getNextTurnOf` 判空 | `Frozen.java` |
| `Skill(Skill)` 不复制 `tags` | ✅ 深拷贝 + `setTags` | `Skill.java:67-79` |
| `getCriticalDMG()` 取错字段 | ✅ 改用 `criticalDMGEnhanceAmount` | `LivingThing.java` |
| 复制构造器丢 `modifyDamage`/`showSpecialMes` | ✅ 已复制 | `LivingThing.java:119-120` |
| 玩家侧共享技能对象 | ✅ `UniversalController(List,owner)` 构造时逐技能 `copy()` | `UniversalController.java:45-50` |
| `DamageEvent` 从未 `post` | ✅ 已在 `makeDamage` 补上 | `LivingThing.java:1748` |
| `HpLossEvent` 构造器自赋值 | ✅ 补 `this.` | `HpLossEvent.java:10-11` |
| `hpMagnification` 用当前 HP | ✅ **全局改为 `getHpMax()`** | `DamageCalculate.java:59` |
| `copy()` 静默降级 | ✅ 基类改抛异常（fail-fast） | `Skill`/`Item`/`LivingThing` |
| 玩家选奖励绕过子类 `copy()` | ✅ 改 `items[...].copy()` | `GameMain.java:173` |
| 复制构造器多余的 `controller != null` 判断 | ✅ 已简化（if/else 保证非 null） | `LivingThing.java:135` |
| `IgnoreDefenceEffect` 未重写 `comeIntoEffect` | ✅ 已补空实现（不再打印基类占位文本） | `IgnoreDefenceEffect.java:49-51` |

### 2.2 内容层

| 缺陷 | 状态 |
|---|---|
| 李晓焰普攻"加算看自增前、减算看自增后"→ `extraDamage` 净变负、最后给敌人回血 | ✅ `wasHigh` 在同一时刻快照 |
| 李晓焰 `extraDamage` 加成被无条件扣回 → 从来没生效过 | ✅ 已修 |
| `PyrohemicPumping` 整数除法 `long/long <= 0.5` 恒成立 | ✅ 补 `(double)` |
| `PyrohemicPumping` 加算写在循环内 → 多目标加 N 次只扣 1 次 | ✅ 移出循环 |
| 白厄 `extraAbilityTier` 加成不生效 + 回滚恒为 0 | ✅ **本轮修复**（见 §3.1） |
| `listen2` 死代码（开战 +1 从未生效） | ✅ **本轮修复**（移入 `whenFightStart`） |
| 白厄 `scourge_max` 偏低 | ✅ 7 → 8 |

---

## 三、本轮（第四轮）的具体改动

### 3.1 白厄【额外能力】`extraAbilityTier` —— 修正我上一轮的错误结论

**我第三轮报告说"整条链路完全不产生数值影响"，这个结论是错的。** 实际是：加成生效，但**回滚失效**。

根因是"增量式记账"和"总量式回滚"不匹配：

```java
// 原来 updateSelf（增量式）
setAttackEnhancePercent(... + extraAbilityTier * 0.5 - formerExtraAbilityTier * 0.5);
formerExtraAbilityTier = extraAbilityTier;      // ← 每回合把 former 对齐 tier

// 原来 whenFightEnds（想按总量回滚）
setAttackEnhancePercent(... - extraAbilityTier * 0.5 + formerExtraAbilityTier * 0.5);  // 恒等于 0
```

`updateSelf` 每回合都让两者相等，所以回滚那行**永远是 0** → +50%/+100% 攻击会跨战斗永久残留。

**已改为"按当前总量同步"**：

```java
// 新字段：appliedExtraAbilityTier —— 已经真正加到属性上的层数

// updateSelf
setAttackEnhancePercent(getAttackEnhancePercent() + (extraAbilityTier - appliedExtraAbilityTier) * 0.5);
appliedExtraAbilityTier = extraAbilityTier;

// whenFightEnds
setAttackEnhancePercent(getAttackEnhancePercent() - appliedExtraAbilityTier * 0.5);
appliedExtraAbilityTier = 0;
extraAbilityTier = 0;
```

修复后的行为：

| 时机 | `tier` | 攻击增强 |
|---|---|---|
| 开战 | 0 → **1** | — |
| 首个回合 `updateSelf` | 1 | **+50%** |
| 第 1 次觉醒结束 | 1 → 2 | 下回合 **+100%** |
| 第 2 次觉醒结束 | 2（封顶） | 保持 +100% |
| 战斗结束 | → 0 | **退回 0** ✅ |

`AwakeEndListener:36` 的 `Math.min(2, ...)` 封顶现在真的有意义了。附带好处：`tier` 被改小时也能正确回退（原来只能单向增长）。

### 3.2 白厄"开战 +1"从死代码移入 `whenFightStart`

原 `listen2(FightStartEvent)` 永不执行——因为监听器**正是在 `whenFightStart` 里才注册**，而 `whenFightStart` 又是在 `FightStartEvent` 的 handler 内部被调用的，`EventBus.post` 遍历的是进入分发时的快照（`EventBus.java:63`）。

现已直接写入 `Phainon.whenFightStart`，并删除 `listen2` 与不再使用的 `FightStartEvent` import。

> 注：「开战 +1 火种」这项对首局无影响（开局 `coreflame = 15` 已顶上限），但**第二局开始会从 0 变成 1**——这是原来完全没有的效果。

---

## 四、`hpMagnification` 语义变更（重要）

`DamageCalculate:59` 从 `attacker.getHp()` 改成 `attacker.getHpMax()`，这是**全局语义变更**。

**受影响面很小**——现有技能几乎都用 `atkMagnification`，真正吃 `hpMagnification` 的只有李晓焰两招：

| 技能 | 满血 | 残血（30% HP） |
|---|---|---|
| 李晓焰普攻（hpMag 1.0） | 7387 → **8266** | 2216 → **8266** |
| 李晓焰灼血泵动（hpMag 2.0） | 14774 → **16532** | 4432 → **16532** |

（已代入自身 +200% 增伤、防御系数 0.717、`individualMultipleArea` 1.12）

**新定义**：`hpMagnification` = **生命上限倍率**（不再是当前生命值倍率）。

---

## 五、仍然存在的问题

### 5.1 🟡 `AttackEnhance` 漏 `isOn = true`（唯一还活着的 `isOn` 类 bug）

```java
// AttackEnhance.java:40-45
public void comeIntoEffect(LivingThing thing) {
    if (!isOn) {
        thing.setAttackEnhanceAmount(thing.getAttackEnhanceAmount() + amount);
        thing.setAttackEnhancePercent(thing.getAttackEnhancePercent() + percent);
    }
    // ← 忘了 isOn = true;
}
```

每 tick 重复加，`whenLastTimeEnd` 只减一次。对比 `DamageEnhanceEffect:67`、`HpEnhanceEffect:50` 都写了。

**当前无害**：`AttackEnhance` 从未被实例化（全仓 grep 确认）。但它是 5 个"死效果"里唯一**本身有 bug** 的，一旦被用就会出问题。

### 5.2 🟡 五个效果类从未被实例化

`AttackEnhance`、`IgnoreDefenceEffect`、`CriticalRateEnhanceEffect`、`DefenseEnhanceEffect`、`HpEnhanceEffect` —— 全仓 grep `new XxxEffect(` 只命中它们各自的 `copy()`。

后果：`DamageCalculate:71-78` 的"无视防御"钩子因此**永不可达**。`World.getEffectList()`（`Mod.addEffect` 的注册目标）也**没有任何消费者**——效果注册表是死数据。

### 5.3 🔴 暴击系统整体仍是关着的

- **没有任何实体设置基础暴击率**（`facSetCriticalRATE` / `setGetCriticalRATE` 零调用）→ `DamageCalculate:52` 的 `Math.random() <= criticalRate` 实际是 `<= 0`，**永远不会暴击**
- `CriticalRateEnhanceEffect` 从未被实例化
- 唯一被用到的 `CriticalDMGEnhanceEffect(0.3, 3)`（白厄被选为目标时挂的）**纯空转**
- `getCriticalDMG()` 字段修好了，但上游没暴击率，等于没用

**这是当前最值得决策的一项**，且与千冶·刃直接冲突——它的【无量忿怒】要给 +20% 暴击率。

### 5.4 🟡 `Skill.extraDamage` 没有重置点

```java
// Skill.java:46
private long extraDamage = 0;//多倍率时把其他倍率计算的结果加到这里.伤害计算后重置为零
```

注释说"伤害计算后重置为零"，实际 `DamageCalculate` 只读不写。本轮修复后加算/减算已配平，**不再漂移**，但这套机制完全靠"手动配对抵消"维持——任何一处忘了配对就会永久膨胀（`PyrohemicPumping` 上一轮就是这个症状）。

另：`LivingThing.extraDamage`（public 字段，参与伤害公式）**从无写入点**，永远是 0。

### 5.5 🟡 `Counterattack` 的倍率硬编码复位（脆弱）

```java
// Counterattack.java
:43  setAtkMagnification(getAtkMagnification() * (1 + soulscorch * 0.2));
:44  randomMag = randomMag * (1 + soulscorch * 0.2);   // randomMag 初值 0.3
:49  setAtkMagnification(randomMag);                   // 存的是 0.3×倍率
:55  setAtkMagnification(1);                           // 硬编码 1
```

因为构造函数里 `atkMagnification = 1`，`:55` 恰好还原原值。**只要有人改掉构造函数里的 1，每次反击都会把倍率越改越小。**

### 5.6 🟡 死亡实体在战斗中途就被 `whenFightEnds()`

```java
// FightTurnPastListener.java:145-147
for (LivingThing dead : theDeath) {
    dead.whenFightEnds();      // 语义是"战斗结束清理"
}
```

`whenFightEnds()` 会**把 HP 回满**、清空效果、技能冷却归零、法力回满。死亡实体已从胜负判定移除，所以"满血复活"本身无影响；但副作用链有问题：

- 白厄只是"这一场里死了"，却会 `coreflame = 0`、`isListenerRegister = false`、并在 `isAwaken` 时 post `AwakenEndEvent`
- 之后战斗真结束时 `FightEndEventListener:23-28` 又对 `getAllEntities()` **再调一次**

**建议**：拆成 `whenFightEnds()` 与 `onDeath()` 两个方法。

### 5.7 🟢 其余已知项

| 项 | 位置 | 说明 |
|---|---|---|
| 玩家侧复制构造器仍传原 List | `LivingThing.java:129/131` | 现在因 `UniversalController(List,owner)` 内部会 copy，**不再串味**，但不如统一走 `\|\| other.controller` 干净 |
| 复制契约仍有字段缺口 | `LivingThing.java:93-149` | `*Enhance*`、五元素穿透/增伤、`individualMultipleArea`、`extraDamage` 均不复制。**你确认过是有意的** |
| 免死与锁血语义耦合 | `ActorLiXiaoYan.java:51-60` | 顺序正确，但 `ignition -= 10` 后若锁血下限更高，血量会被拉回。有 `MemorizedHp` 时免死基本不触发 |
| 锁定时长与描述不符 | `MemorizedHp.java:13` | 构造时 `lastTime = 3`，终结技描述写"2 回合内" |
| 监听器泄漏 | `UltimateAttack.java:49` | 每次开大都 `EventBus.register(new DamageEventListener())`；若已有同 origin 的 `MemorizedHp`，`addEffect` 只续时长、丢弃新实例 → 该监听器**永不注销** |
| 燃点上限显示不准 | `ActorLiXiaoYan.java:67` | 显示 `getIgnitionMax()`（恒 10），残血实际 15 时会显示错 |
| `Phainon.isListenerRegister` 是 `static` | `Phainon.java:28` | 同场多个白厄副本只有第一个能注册监听器；任一死亡置 false 会顶掉全局 |

---

## 六、命令系统（新增，未完成）

```
system/command/
├── Command                  抽象基类：resolveInput(String) / comeToEffect(CommandParameter)
├── CommandParameter         List<ParameterEntry>
├── ParameterEntry           (CommandParameterType, Object content)
├── CommandParameterType     ENTITIES, ITEMS, ENTITY_SELECTOR, ITEM_SELECT, STRING
└── EntitySelector           List<Entity> targets（空壳）
officialStuff/customCommands/
└── KillCommand extends Command
```

### 做对的地方

- **`ParameterEntry` 用 `(类型, Object)` 组合**，而非每种类型一个子类 —— 命令参数本来就该是"类型标签 + 值"
- `CommandParameterType` 枚举留了扩展位
- `Command` 的"基类抛异常 + 子类覆写"契约，和 `Effect.copy()` / `Skill.copy()` 的新风格一致

### 缺口（按重要性）

1. **没有调度器/注册表，也没有调用点。** 全仓 grep 只命中这 6 个文件自己，`GameMain` 与 `PlayerController` 都没接进来 —— **玩家目前无法输入命令**。
2. **`KillCommand.resolveInput` 直接 `return super.resolveInput(input)`** → `Command:7` 抛 `RuntimeException`。命令一被调用就崩。
3. **`KillCommand.comeToEffect` 只校验、不执行** —— 校验通过后什么都不做。
4. **解析职责压在命令自己身上**（`resolveInput(String)`）。MC 的做法是**参数类型自己解析自己**（`ArgumentType<T>.parse`），命令只声明参数需求。
5. **`CommandParameter` 是有序 `List`，只能按下标取参数。** MC 用 `CommandContext` 按名字取，更抗改动。
6. **`EntitySelector` 没有选择器语法**（`@e`/`@p`/`@s`），也没有"从字符串解析"的入口 —— 这是 MC 命令系统最核心也最重的部分。

### 建议的最小骨架

```
CommandDispatcher / CommandRegistry   ← 命令名 → Command 的注册表 + 主入口
ArgumentType<T>                       ← 解析职责下沉
CommandContext                        ← 按名字取参数
CommandSender                         ← 谁在执行，决定输出走向
```

**最短可用路径**：先不做完整 MC 那套，只做"1 个命令 + 1 个实体选择器 + 主循环里拦截 `#` 开头的输入"，跑通一次再抽象。

---

## 七、官方内容与引擎就绪度

| 类型 | 数量 | 清单 |
|---|---|---|
| 角色 | **3** | `PlayerOne`（土）、`ActorLiXiaoYan`（火）、`Phainon`（火） |
| 怪物 | 3 | `CommonInsect`、`IceInsect`、`InsectBoss`（均 150 级） |
| 物品 | 1 | `ANiceSword` |
| 技能 | 15 个类 | 全部已重写 `copy()` ✅ |
| 效果 | 11 个类 | 其中 **5 个从未被实例化**（§5.2） |
| 命令 | 1 | `KillCommand`（空实现） |

**千冶·刃（崩铁 V4.3）尚未开始** —— `customEntity/players/` 下仍只有 3 个角色。

### 为千冶·刃准备的引擎能力盘点

| 机制 | 状态 |
|---|---|
| 伤害按生命上限结算 | ✅ 已就绪 |
| 生命损失事件 | ✅ `HpLossEvent` 已可用（暂无监听者） |
| 我方攻击后触发 | ✅ `SelectTargetEvent` |
| 致命保护 / 不死 | ✅ `IModifyDamage` |
| 变身换技能组 | ✅ `setSkills` |
| 消耗生命值且不致死 | ⚠️ 能手写，未抽成原语 |
| 暴击率 / 暴伤加成 | ❌ **全局暴击率为 0**（§5.3），需先决策 |
| **嘲讽**（改目标选择） | ❌ 未做 |
| **倒计时占时间轴**（速度 70） | ❌ 未做 |

---

## 八、健康度小结

**四轮下来，前三轮报告列出的核心缺陷已全部清零。** 引擎从"能跑但处处是坑"变成了"骨架稳固、边角待补"。

分类来看当前剩余工作：

**A. 小修（一两行）**
1. `AttackEnhance:44` 补 `isOn = true`
2. `ActorLiXiaoYan:67` 的燃点上限显示
3. `MemorizedHp` 的 `lastTime` 与描述对齐

**B. 需要拍板的设计决策**
4. **暴击系统要不要真正启用**（给实体配基础暴击率）——千冶·刃依赖它
5. 5 个死效果类：接线还是删掉
6. `Skill.extraDamage`：改成走 `DamageEvent` 传参，还是真的在 `DamageCalculate` 末尾归零

**C. 结构性**
7. 拆分"战斗结束清理"与"实体死亡清理"（§5.6）
8. 命令系统补齐调度器 + 解析下沉（§6）
9. 千冶·刃：缺嘲讽与倒计时两块引擎能力

### 一个值得警惕的反复模式

`Counterattack`、`PyrohemicPumping`、`CommonAttack` 都在用「**改技能的可变字段当临时参数**」这个反模式（`extraDamage`、`atkMagnification`）。第二轮修好的两个 bug 本质上都是它的产物——`PyrohemicPumping` 是"加 N 次扣 1 次"，`CommonAttack` 是"加算减算看不同时刻"。

**如果要做第四个角色，建议先把"本次结算的额外倍率"改成通过 `DamageEvent` 传参。** 这比之后一个个修配对可靠得多，而且千冶·刃的技能组（多段随机命中 + 追加攻击）正好是这种临时参数用得最多的一类。

---

*本文档由 AI 生成，仅供参考。所有结论均已在当前工作区源码中逐一核对（含行号引用）。*
