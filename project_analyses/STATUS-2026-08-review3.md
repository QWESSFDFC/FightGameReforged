# FightGameReforged 现状复核（第三轮 · 2026-08）

> ⚠️ **历史文档（第 3 轮复核 · 复核范围 118 个 Java 文件）**：
> 本文档列出的问题**大多已在第 4 轮修复**（本文档自称的"以本文档为准"已不再成立）。
> 请以 **[`ANALYSIS-review4.md`](ANALYSIS-review4.md)（第 4 轮，最新）** 与当前源码为准；
> 保留本文档仅供追溯分析过程。

> 本文档由 AI 通读源码后生成，仅作参考，请以源码为准。
> 复核范围：`../src` 下全部 **118 个 Java 文件（8499 行）**、git 提交历史、未提交改动、`../mods`、`../config`。
> 前两轮报告：`ANALYSIS-2026-08.md`、`STATUS-2026-08-review2.md`（均部分过时，以本文档为准）。

---

## 一、本轮变更（按提交倒序）

| 提交 | 内容 |
|---|---|
| `36d76c3` | 修复 bug、加固；`../.gitignore`、`GameMain`、`LivingThing`、`HpLossEvent`、`Item`、`KillCommand`、`ActorLiXiaoYan.CommonAttack`、`PyrohemicPumping`、`Skill` |
| `ae9911f` | `DamageCalculate` —— **`hpMagnification` 改为按生命上限结算** |
| `62068e7` | `FightTurnPastListener` 循环化；`Phainon.scourge_max` 7 → 8 |
| `23b2c0b` / `b6bce43` | 上一轮的 `Frozen`、`EffectEventListener`、`UniversalController`、`Skill` 标签等修复 |

**当前未提交**：仅 `PyrohemicPumping.java`（本轮修复）与 `system/command/` 5 个新类。

---

## 二、已修复项（逐条核对过）

### 2.1 引擎层

| 原问题 | 现状 | 位置 |
|---|---|---|
| 效果系统只结算列表第一个效果 | ✅ `return` → `continue` | `EffectEventListener.java:21` |
| 回合递归推进 → 长战斗爆栈 | ✅ `turnLoop` 循环 + `isDriving` 重入闸 + `finally` 复位 + `turns` 空兜底 | `FightTurnPastListener.java:37/45/53/152-156` |
| `Frozen` 时机错位 → NPE + 挡不住行动 | ✅ 改到 `initialEffect` 布置；`whenLastTimeEnd` 不清 `specialAction`；`getNextTurnOf` 判空 | `Frozen.java:61/86-95/107-111` |
| `Skill(Skill)` 不复制 `tags` | ✅ 深拷贝 + `setTags` | `Skill.java:67-79` |
| `getCriticalDMG()` 取错字段 | ✅ 改用 `criticalDMGEnhanceAmount` | `LivingThing.java` |
| 复制构造器丢 `modifyDamage`/`showSpecialMes` | ✅ 已复制 | `LivingThing.java:119-120` |
| `UniversalController(List, owner)` 共享技能对象 | ✅ 构造时逐技能 `copy()` | `UniversalController.java:45-50` |
| `DamageEvent` 从未 `post` | ✅ **已在 `makeDamage` 里补上** | `LivingThing.java:1748` |
| `HpLossEvent` 构造器自赋值 | ✅ 补 `this.` | `HpLossEvent.java:10-11` |
| `hpMagnification` 用当前 HP → 自伤角色越打越弱 | ✅ **全局改为 `getHpMax()`** | `DamageCalculate.java:59` |
| `Skill`/`Item`/`LivingThing.copy()` 静默降级 | ✅ 基类改抛异常（fail-fast） | 三处 |
| `GameMain:173` 绕过 `ANiceSword.copy()` | ✅ 改为 `items[...].copy()` | `GameMain.java:173` |

### 2.2 内容层

| 原问题 | 现状 |
|---|---|
| 李晓焰普攻"加算看自增前、减算看自增后"→ `extraDamage` 净变负、最后给敌人回血 | ✅ **本轮修复**（`wasHigh` 在同一时刻快照） |
| 李晓焰 `extraDamage` 那笔加成被无条件扣回 → 从来没生效过 | ✅ **本轮修复** |
| `PyrohemicPumping` 整数除法 `long/long <= 0.5` 恒成立 | ✅ 补 `(double)` |
| `PyrohemicPumping` 加算写在循环内 → 多目标加 N 次只扣 1 次 | ✅ **本轮修复**（移出循环） |
| 白厄"弑魂焚诏永久无敌" | ⚠️ **推翻我上一轮的判断**：`Counterattack` 在 boss 行动前触发并扣回 0.75，链条实际是通的。见 §4.3 |
| 白厄 `scourge_max` 偏低 | ✅ 7 → 8 |

---

## 三、`hpMagnification` 语义变更的影响（需要知道）

`DamageCalculate:59` 从 `attacker.getHp()` 改成 `attacker.getHpMax()`，这是**全局语义变更**，不只是给千冶·刃铺路。

**受影响范围很小**——因为现有技能几乎都用 `atkMagnification`。真正用 `hpMagnification` 的只有李晓焰两招：

| 技能 | 满血 | 残血（30% HP） |
|---|---|---|
| 李晓焰普攻（hpMag 1.0） | 7387 → **8266** | 2216 → **8266** |
| 李晓焰灼血泵动（hpMag 2.0） | 14774 → **16532** | 4432 → **16532** |

（已代入自身 +200% 增伤、防御系数 0.717、`individualMultipleArea` 1.12）

**净效果**：满血略增（+12%），残血从"几乎打不动"变成"输出不衰减"。对李晓焰是纯加强，而且让"背水输出"的设计意图终于成立。

**要记住的新定义**：`hpMagnification` = **生命上限倍率**（不再是当前生命值倍率）。这个定义更清晰，也是千冶·刃需要的。

---

## 四、仍然存在的问题

### 4.1 `Skill.extraDamage` 依然没有任何重置点

```java
// Skill.java:46
private long extraDamage = 0;//多倍率时把其他倍率计算的结果加到这里.伤害计算后重置为零
```

注释说"伤害计算后重置为零"，实际 `DamageCalculate` 只读不写。本轮修复后，加算/减算已经配平，所以**不再会漂移成负数**——但有两处遗留：

1. **`PyrohemicPumping:35-37` 的 `×1.5` 是净增长且永不归零**：每次"残血 + 额外伤害为 0"时触发，效果是 `0 → 0`；为 0 时×1.5 依然是 0……

   > 等一下，这里要精确：`×1.5` 作用于**当前值**。时序是 `×1.5` → `+0.5×hpMax` → 扣回 `-0.5×hpMax`，所以战斗内永远净 0。**实际无害**。但这是"靠配对抵消"维持的，任何一处忘了配对就会永久膨胀。
2. **`LivingThing.extraDamage`（public 字段）参与伤害公式但从无写入点**，永远是 0。

**建议**：要么删掉 `extraDamage` 这套（改成走 `DamageEvent` 传参），要么在 `DamageCalculate` 末尾真的 `skill.setExtraDamage(0)`。后者一行，能让注释不再骗人。

### 4.2 `AttackEnhance` 漏 `isOn = true`（当前是死代码）

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

每 tick 都会重复加，而 `whenLastTimeEnd` 只减一次。对比 `DamageEnhanceEffect:67` 和 `HpEnhanceEffect:50` 都写了 `isOn = true`。

**当前无害**，因为 `AttackEnhance` **从未被实例化**（全仓 grep：只在它自己的 `copy()` 里出现 `new`）。但一旦有人用它就会出问题。

**同类死效果共 5 个**（都从未被实例化）：

| 效果类 | 后果 |
|---|---|
| `AttackEnhance` | 漏 `isOn = true` |
| `IgnoreDefenceEffect` | **未重写 `comeIntoEffect`**，被 tick 会打印基类占位文本 `Effect.java:227`；且 `DamageCalculate:71-78` 的"无视防御"钩子因此永不可达 |
| `CriticalRateEnhanceEffect` | 见 §4.4（暴击系统） |
| `DefenseEnhanceEffect` | 无 |
| `HpEnhanceEffect` | 无 |

> 顺带：`Mod.addEffect` 注册进 `World.getEffectList()`，但该列表**无任何消费者**——效果注册表是死数据。

### 4.3 白厄：`Counterattack` 的倍率硬编码复位（脆弱，但当前不出错）

```java
// Counterattack.java
:43  this.setAtkMagnification(this.getAtkMagnification() * (1 + soulscorch * 0.2));
:44  randomMag = randomMag * (1 + soulscorch * 0.2);   // randomMag 初值 0.3
:49  this.setAtkMagnification(randomMag);              // ← 保存的是 0.3×倍率，不是原值
:55  this.setAtkMagnification(1);                      // ← 硬编码 1
```

`Counterattack` 的构造器里 `atkMagnification = 1`，所以 `:55` 恰好还原了原值。**只要有人把构造函数里的 1 改掉，每次反击都会把倍率越改越小。**

修法：把 `:49` 存的值和 `:55` 还原的值统一成"进入方法时先存一份原值"。

### 4.4 暴击系统整体仍是关着的

- **没有任何实体设置基础暴击率**（`facSetCriticalRATE` / `setGetCriticalRATE` 零调用）→ `DamageCalculate:52` 的 `Math.random() <= criticalRate` 实际是 `<= 0`，**永远不会暴击**
- `CriticalRateEnhanceEffect` 从未被实例化
- 唯一被用到的 `CriticalDMGEnhanceEffect(0.3, 3)`（白厄被选为目标时挂的）**纯空转**
- `getCriticalDMG()` 字段修好了，但上游没暴击率，等于没用

**这对千冶·刃有直接影响**——它的【无量忿怒】要给"+20% 暴击率"，但那会是全局第一个真正生效的暴击来源。要么给实体配基础暴击率，要么接受"只有开大期间才暴击"。

### 4.5 `Phainon.extraAbilityTier` 的回滚公式是空的（但无害）

```java
// Phainon.updateSelf:294-295（每回合）
setAttackEnhancePercent(... + extraAbilityTier * 0.5 - formerExtraAbilityTier * 0.5);
formerExtraAbilityTier = extraAbilityTier;      // 每回合都把两者对齐

// Phainon.whenFightEnds:243-245（想回滚）
setAttackEnhancePercent(... - extraAbilityTier * 0.5 + formerExtraAbilityTier * 0.5);  // 恒为 0
extraAbilityTier = 0; formerExtraAbilityTier = 0;
```

由于两者每回合都被对齐，到 `whenFightEnds` 时必然相等，那行加减等于 0，**什么都没回滚**。

但**当前无害**：`extraAbilityTier` 只在两处增加——`FightStartAndSelectEventListener.listen2`（**死代码**，见 §4.6）和 `AwakeEndListener:36`（封顶 2，且 `whenFightEnds` 会清零）。所以 `updateSelf` 里 `+0 - 0` 也是 0。

**结论**：整条 `extraAbilityTier` 链路目前完全不产生任何数值影响。要么修好 `listen2` 让它真正生效，要么删掉。

### 4.6 `FightStartAndSelectEventListener.listen2` 依然是死代码

它在 `whenFightStart` 里注册（`Phainon.java:276`），而 `whenFightStart` 是在 `FightStartEvent` 的 handler 里被调用的（`FightStartEventListener:18`），`EventBus.post` 遍历的是**进入分发时的快照**（`EventBus.java:63`）→ **本事件分发期间新注册的 handler 收不到本事件**。

所以"开战 +1 火种 / +1 extraAbilityTier"从未生效。（同类的 `listen1` 监听 `SelectTargetEvent`，事件更晚，所以正常工作。）

### 4.7 死亡实体在战斗中途就被 `whenFightEnds()`

```java
// FightTurnPastListener.java:145-147
for (LivingThing dead : theDeath) {
    dead.whenFightEnds();      // 语义是"战斗结束清理"，却在这里被调用
}
```

`LivingThing.whenFightEnds()` 会**把 HP 回满**、清空效果、技能冷却归零、法力回满。死亡实体已经从胜负判定里移除，所以"满血复活"本身无影响；但副作用链有问题：

- 白厄只是"这一场里死了"，却会 `coreflame = 0`、`isListenerRegister = false`、并在 `isAwaken` 时 post `AwakenEndEvent`
- 之后战斗真结束时 `FightEndEventListener:23-28` 又对 `getAllEntities()` **再调一次**

**建议**：把"战斗结束清理"和"实体死亡清理"拆成两个方法。

### 4.8 其余已知项

| 项 | 位置 | 说明 |
|---|---|---|
| 玩家侧复制构造器仍传原 List | `LivingThing.java:129/131` | `PlayerController`/`ThinkingControllerAI` 分支传 `other.controller.getSkills()`。现在因为 `UniversalController(List,owner)` 内部会 copy，**不再串味**，但不如统一走 `|| other.controller` 干净 |
| 复制契约仍有字段缺口 | `LivingThing.java:93-149` | `*Enhance*`、五元素穿透/增伤、`individualMultipleArea`、`extraDamage` 都不复制。**你确认过是有意的** |
| 免死与锁血语义耦合 | `ActorLiXiaoYan.java:51-60` | 顺序正确，但 `ignition -= 10` 后若锁血下限更高，血量会被拉回。有 `MemorizedHp` 时免死基本不触发 |
| 锁定时长与描述不符 | `MemorizedHp.java:13` | 构造时 `lastTime = 3`，而终结技描述写"2 回合内" |
| 监听器泄漏 | `UltimateAttack.java:49` | 每次开大都 `EventBus.register(new DamageEventListener())`；若身上已有同 origin 的 `MemorizedHp`，`addEffect` 只续时长、丢弃新实例 → 该监听器**永不注销** |
| 燃点上限显示不准 | `ActorLiXiaoYan.java:67` | 显示 `getIgnitionMax()`（恒 10），残血实际 15 时会显示错 |
| `SkipTurn` 用 `printf` | `SkipTurn.java:11` | 已修为 `println` ✅ |

---

## 五、命令系统（新增，未完成）

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

`Command.java` 顶部留有作者原话："目前没有写完……想写一个类似我的世界 java 版的。"

### 做对的地方

- **`ParameterEntry` 用 `(类型, Object)` 组合**，而不是每种类型建一个子类 —— 命令参数本来就该是"类型标签 + 值"
- `CommandParameterType` 枚举留了扩展位
- `Command` 的"基类抛异常 + 子类覆写"契约，和 `Effect.copy()` / `Skill.copy()` 的新风格一致

### 缺口（按重要性）

1. **没有任何调度器/注册表，也没有调用点。** 全仓 grep 只命中这 6 个文件自己，`GameMain` 和 `PlayerController` 都没接进来 —— **玩家现在无法输入命令**。
2. **`KillCommand.resolveInput` 直接 `return super.resolveInput(input)`** → `Command:7` 抛 `RuntimeException`。命令一被调用就崩。
3. **`KillCommand.comeToEffect` 只校验、不执行** —— 校验通过后什么都不做，没杀任何东西。
4. **解析职责压在命令自己身上**（`resolveInput(String)`）。MC 的做法是**参数类型自己解析自己**（`ArgumentType<T>.parse`），命令只声明"我要一个实体选择器、一个整数"。现在这样每个命令都要重写一遍字符串切分。
5. **`CommandParameter` 是有序 `List`，只能按下标取参数。** MC 那套是 `CommandContext` 按名字取，更抗改动。
6. **`EntitySelector` 没有选择器语法**（`@e` / `@p` / `@s`），也没有"从字符串解析"的入口 —— 这是 MC 命令系统最核心也最重的部分。

### 建议的最小骨架

```
CommandDispatcher / CommandRegistry   ← 缺：命令名 → Command 的注册表 + 主入口
ArgumentType<T>                       ← 缺：解析职责下沉
CommandContext                        ← 缺：按名字取参数
CommandSender                         ← 缺：谁在执行，决定输出走向
```

**最短可用路径**：先不做完整 MC 那套，只做"1 个命令 + 1 个实体选择器 + 主循环里拦截 `#` 开头的输入"，跑通一次再抽象。

---

## 六、官方内容现状

| 类型 | 数量 | 清单 |
|---|---|---|
| 角色 | **3** | `PlayerOne`（土）、`ActorLiXiaoYan`（火）、`Phainon`（火） |
| 怪物 | 3 | `CommonInsect`、`IceInsect`、`InsectBoss`（均 150 级） |
| 物品 | 1 | `ANiceSword` |
| 技能 | 15 个类 | 全部已重写 `copy()` ✅ |
| 效果 | 11 个类 | 其中 **5 个从未被实例化**（§4.2） |
| 命令 | 1 | `KillCommand`（空实现） |

**千冶·刃尚未开始**（`customEntity/players/` 下仍只有 3 个角色）。

### 为千冶·刃准备的引擎能力盘点

| 机制 | 状态 |
|---|---|
| 伤害按生命上限结算 | ✅ **已就绪**（本轮 `DamageCalculate` 改动） |
| 生命损失事件 | ✅ `HpLossEvent` 已可用（无监听者） |
| 我方攻击后触发 | ✅ `SelectTargetEvent` |
| 致命保护 / 不死 | ✅ `IModifyDamage` |
| 暴击率 / 暴伤加成 | ⚠️ Effect 类有，但**全局暴击率是 0**（§4.4） |
| 变身换技能组 | ✅ `setSkills` |
| 消耗生命值且不致死 | ⚠️ 能手写，未抽成原语 |
| **嘲讽**（改目标选择） | ❌ 未做 |
| **倒计时占时间轴**（速度 70） | ❌ 未做 |

---

## 七、健康度小结

**三轮下来进步非常明显。** 第一轮报告里列的核心缺陷——效果只结算第一个、递归爆栈、`Frozen` 崩局、技能对象共享、复制丢机制、`DamageEvent` 死订阅、`HpLossEvent` 损坏——**现在全部清零**。

**当前真正需要处理的，只剩三类：**

**A. 一行/两行能修的**
1. `AttackEnhance:44` 补 `isOn = true`（虽然是死代码，但补了才安全）
2. 给 `IgnoreDefenceEffect` 补 `comeIntoEffect`（否则被 tick 会打印基类占位文本）
3. `ActorLiXiaoYan:67` 的燃点上限显示

**B. 设计决策类（需要你拍板）**
4. 暴击系统要不要真正启用（给实体配基础暴击率）？——**千冶·刃依赖它**
5. `extraAbilityTier` 那条链路：修好 `listen2` 让它生效，还是整体删掉？
6. `Skill.extraDamage` 那套：删掉改走 `DamageEvent` 传参，还是在 `DamageCalculate` 末尾真的归零？

**C. 结构性**
7. 拆分"战斗结束清理"与"实体死亡清理"（§4.7）
8. 命令系统补齐调度器与参数解析下沉（§5）

**一个反复出现的模式值得注意**：`Counterattack`、`PyrohemicPumping`、`CommonAttack` 都在用"**改技能的可变字段当临时参数**"这个反模式（`extraDamage`、`atkMagnification`）。本轮修好的两个 bug 本质上都是它的产物。如果哪天要做第四个角色，建议先把"本次结算的额外伤害/倍率"改成通过 `DamageEvent` 传参——这比一个个修配对可靠得多。

---

*本文档由 AI 生成，仅供参考。所有结论均已在当前工作区源码中逐一核对（含行号引用）。*
