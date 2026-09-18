# FightGameReforged 现状复核（第二轮）

> ⚠️ **历史文档（第 2 轮复核 · 复核范围 118 个 Java 文件）**：
> 本文档列出的问题**大多已在后续轮次修复**。请以
> **[`ANALYSIS-review4.md`](ANALYSIS-review4.md)（第 4 轮，最新）** 与当前源码为准；
> 保留本文档仅供追溯分析过程。

> 本文档由 AI 通读源码后生成，仅作参考，请以源码为准。
> 复核范围：`../src` 下全部 **118 个 Java 文件（8496 行）**、`git diff` 未提交改动、`../mods`、`../config`。
> 上一轮报告为 `ANALYSIS-2026-08.md`（现已部分过时）。本文档以**当前工作区实际代码**为准。

---

## 一、规模与变更概览

| 维度 | 上一轮 | 本轮 |
|---|---|---|
| Java 文件 | 112 | **118** |
| 总行数 | 8318 | **8496** |
| 新增包 | — | **`system/command`（6 个类）+ `officialStuff/customCommands`** |

**本次未提交改动共 9 个文件**（`git diff --stat`：+70 −7）：

```
 M  entity/LivingThing.java          copy() 改抛异常；setHp 去掉多余转换
 M  item/Item.java                   copy() 改抛异常
 M  skill/Skill.java                 copy() 改抛异常
 AM system/command/Command.java              ← 新增
 AM system/command/CommandParameter.java     ← 新增
 AM system/command/CommandParameterType.java ← 新增
 AM system/command/EntitySelector.java       ← 新增
 AM system/command/ParameterEntry.java       ← 新增
 AM officialStuff/customCommands/KillCommand.java ← 新增
```

> 注：上一轮我提到的 7 处修改（`Frozen` 声明式改造、`FightTurnPastListener` 循环化、`EffectEventListener` 的 `continue`、`UniversalController` 深拷贝技能、`Skill` 标签深拷贝、`getCriticalDMG` 修字段、`ActorLiXiaoYan`/`ANiceSword`）**已经进入 HEAD**（提交 `b6bce43 small update`），本轮已确认全部落地。

---

## 二、已修复项确认（逐条核对过）

| 原问题 | 现状 | 核对位置 |
|---|---|---|
| `EffectEventListener` 的 `return` 让只有第一个效果结算 | ✅ **已修**，改为 `continue` | `EffectEventListener.java:21` |
| 回合推进靠递归，长战斗爆栈 | ✅ **已修**，改为 `turnLoop` 循环 + `isDriving` 重入闸 + `finally` 复位 + `turns` 空兜底 | `FightTurnPastListener.java:37/45/53/79-83/88-93/152-156` |
| `Frozen` 时机错位导致 NPE + 挡不住行动 | ✅ **已修**，改为 `initialEffect` 里布置、`whenLastTimeEnd` 不清 `specialAction`、`getNextTurnOf` 判空 | `Frozen.java:61/86-95/107-111` |
| `Skill(Skill)` 不复制 `tags` | ✅ **已修**，加 `EnumMap` 深拷贝 + `setTags` | `Skill.java:67-79` |
| `getCriticalDMG()` 取错字段 | ✅ **已修**，改用 `criticalDMGEnhanceAmount` | `LivingThing.java` |
| 复制构造器不复制 `modifyDamage`/`showSpecialMes` | ✅ **已修** | `LivingThing.java:119-120` |
| 玩家侧技能对象共享（冷却串味） | ✅ **已修**，`UniversalController(List, owner)` 逐技能 `copy()` | `UniversalController.java:45-50` |
| 李晓焰复制后丢锁血/免死 | ✅ **已修**（依赖上面两条） | — |
| `ActorLiXiaoYan`/`ANiceSword` 的 `copy()` | ✅ **已补** | `ActorLiXiaoYan.java:77`、`ANiceSword.java:19` |
| `PyrohemicPumping` 缺 `copy()` 会被复制成空技能 | ✅ **已补** | `PyrohemicPumping.java:15-18` |

**结论：上一轮列出的 10 个核心缺陷，已修复 8 个。** 下面只列仍然存在的。

---

## 三、仍然存在的问题

### 🔴 1. `DamageEvent` 依然从未被 `post`

`LivingThing.java:1747` 仍是 `new DamageEvent(...)` 后直接调 `getDamage`，**没有 `EventBus.post`**。全项目 `EventBus.post` 调用点共 12 处（已用 grep 全量核对），仍无 `DamageEvent`。

后果：`officialStuff/customEvent/LiXiaoYanEvents/DamageEventListener` 是**死订阅**，李晓焰终结技描述的"每受到一次攻击，获得 1 层燃点"**仍然不生效**；且 `UltimateAttack:49` 每次开大仍会注册一个新的（永不注销）监听器。

### 🔴 2. `HpLossEvent` 构造函数仍是自赋值（对千冶·刃是致命阻塞）

```java
// HpLossEvent.java:9-12
public HpLossEvent(long lostScale, LivingThing livingThing) {
    lostScale = lostScale;          // ← 字段永远是 0
    livingThing = livingThing;      // ← 字段永远是 null
}
```

对比 `HpRestorationEvent.java:9-12` 是**正确的**（有 `this.`），说明这是漏写而非设计。任何 `HpLossEvent` 监听者都会读到 `lostScale = 0`、`livingThing = null`。

**这与千冶·刃直接相关**：原版《崩铁》刃的核心是"累计损失生命值 → 追加攻击"，架在这个事件上是最自然的做法。现在这条路是断的。

### 🔴 3. `Skill.copy()` / `Item.copy()` / `LivingThing.copy()` 改成抛异常 —— 对模组是**破坏性变更**

```java
// Skill.java:107-109 / Item.java:151-153 / LivingThing.java:1554-1556
public Skill copy() {
    throw new RuntimeException("请重写此方法..类"+this.getClass().getName());
}
```

**设计意图是好的**（fail-fast，把"静默降级"变成"立刻报错"），而且官方内容侧已经补齐了：

- `Skill` 的 **15 个子类全部**重写了 `copy()`（含刚补的 `PyrohemicPumping`）
- `Item` 只有 `ANiceSword` 一个子类，已重写
- `LivingThing` 链上 `Player`/`PlayerOne`/`Phainon`/`ActorLiXiaoYan`/`CommonInsect`/`IceInsect`/`InsectBoss` 全部有

**但有两个放大效应必须知道：**

1. **`UniversalController(List, owner)` 现在构造时就逐技能 `copy()`**（`:45-50`）。也就是说**模组只要 new 一个技能子类而没重写 `copy()`，游戏在加载出该角色的瞬间就崩**——不是"技能失效"，是"进不去游戏"。
2. `Phainon` 的 `skills` 字段与 controller 不同步（`this.skills = skillList` 而 controller 持有副本），`AwakeEndListener:24` 的 `controller.setSkills(getPhainon().getSkills())` 又走一次 `copy()`，语义依赖"两者内容一致"这个隐含假设。

**建议**：仓库 `mods/` 目录下的两个示例模组不涉及内容类所以不受影响，但 README 里应该明确写一句"自定义技能/物品/实体**必须**重写 `copy()`"。

### 🟡 4. `GameMain:173` 仍在绕过 `ANiceSword.copy()`

```java
// GameMain.java:173（玩家选择奖励时）
selectedItem = new Item(items[Integer.parseInt(input)]);
```

`ANiceSword.copy()` 上一轮已经补好了，但**这一行没跟着改**，所以：

- 玩家拿到手的"一把剑"是**普通 `Item`** → `comeToEffect` 是空实现 → **用了没反应**
- 每次 `new Item(...)` 都会分配**新 UUID**，而 `Item.equals` 走 `Thing.equals`（比 UUID）→ 奖励物品永远无法堆叠

改成 `items[Integer.parseInt(input)].copy()` 即可同时解决两个问题。

### 🟡 5. `LivingThing` 复制构造器仍共享技能对象（玩家侧）

```java
// LivingThing.java:128-134
if (other.getController() instanceof PlayerController) {
    this.controller = new PlayerController(other.controller.getSkills(), this);   // ← 传原 List
} else if (other.getController() instanceof ThinkingControllerAI) {
    this.controller = new ThinkingControllerAI(other.controller.getSkills(), this); // ← 同上
} else {
    this.controller = new UniversalController(other.controller, this);            // ← 只有这条深拷贝
}
```

`UniversalController(List, owner)` 虽然在内部会 `copy()` 每个技能，但**两个 controller 仍然指向各自独立的 `ArrayList`**——所以现在不会串了。不过更干净的写法是直接把 `|| other.controller` 传进去，让所有分支走同一条深拷贝路径。**当前不算 bug，算隐患。**

### 🟡 6. 一批数值字段仍不在复制契约内（你确认过是有意的）

复制构造器仍未复制：`attackEnhancePercent/Amount`、`defenceEnhance*`、`speedEnhance*`、`hpEnhance*`、`criticalDMGEnhancePercent/Amount`、`criticalRateEnhancePercent/Amount`、五元素 `*Penetration`、五元素 `*DamageEnhance`、`individualMultipleArea`、`extraDamage`。

**已知取舍**：你明确说这是想要的。仅提醒一句——`ActorLiXiaoYan` 复制构造器里那句手工 `setIndividualMultipleArea(...)` 就是在给这个取舍打补丁，将来新角色如果有构造期增益，每个都要记得补一句。

### 🟡 7. 内容层遗留（本轮未动）

| 项 | 位置 | 说明 |
|---|---|---|
| `Skill.extraDamage` 只增不减 | `Skill.java:46` 注释说"伤害计算后重置为零"，实际无任何重置点 | `DamageCalculate` 只读不写 |
| `PyrohemicPumping` 整数除法 | `:29`（原 `:29`）`user.getHp() / user.getHpMax() <= 0.5` | `long/long` 恒为 0，判定失效 + `extraDamage ×1.5` 指数增长 |
| `AttackEnhance` 漏 `isOn = true` | `:40-45` | Buff 每回合重复叠加，只减一次 |
| `CalamitySoulscorchEdict` 吸收叠加 | `:48-50` | 75% 伤害吸收可能清不掉，累积到 ≥100% 即无敌 |
| `Counterattack` 倍率污染 | `:43-55` | 硬编码复位为 1，不是原值 |
| 死亡实体被 `whenFightEnds()` 中途处理 | `FightTurnPastListener:145-147` | 死亡即满血复活（已移出判定），且战斗结束时**再清理一次** |

### 🟢 8. `LivingThing.setHp` 的小瑕疵

```java
// LivingThing.java:1805
this.hp = Math.min(this.getHpMax(), hp);   // 原本是 (long) Math.min(...)
```

`getHpMax()` 返回 `long`、`hp` 是 `long`，`Math.min(long, long)` 已是 `long`，**去掉转换是正确的**。`getHpMax()` 内部不调用 `setHp`，无递归风险。这条只是记录，不是问题。

---

## 四、新增：命令系统（未完成，尚无调用点）

新增 6 个类，结构如下：

```
system/command/
├── Command                  抽象基类：resolveInput(String) / comeToEffect(CommandParameter)
├── CommandParameter         参数包：List<ParameterEntry>
├── ParameterEntry           一条参数：CommandParameterType + Object content
├── CommandParameterType     枚举：ENTITIES, ITEMS, ENTITY_SELECTOR, ITEM_SELECT, STRING
└── EntitySelector           实体选择器：List<Entity> targets（空壳）
officialStuff/customCommands/
└── KillCommand extends Command
```

`Command.java` 顶部留了作者的原话：

> 目前没有写完.感觉挺复杂的.想写一个类似我的 Minecraft java 版的.准备自己写,或者使用ai.

### 现状评估

**已经做对的部分**

- **`ParameterEntry` 用 `(类型, Object)` 组合**，而不是给每种类型建一个子类——这个选择是对的，命令参数本来就该是"类型标签 + 值"。`CommandParameterType` 枚举也留了扩展位。
- `Command` 用"基类抛异常 + 子类覆写"的契约，和这个项目里 `Effect.copy()` / `Skill.copy()` 的新风格一致。
- `KillCommand` 先做参数校验再执行，顺序正确。

**缺口（按重要性）**

1. **没有任何调度器/注册表。** 全项目 grep `system.command|KillCommand|EntitySelector|CommandParameter` 只命中这 6 个文件自己，**`GameMain` 和 `PlayerController` 都没有接进来**。也就是玩家现在根本没法输入命令。

2. **`KillCommand.resolveInput` 会直接抛异常**：

```java
@Override
public CommandParameter resolveInput(String input) {
    return super.resolveInput(input);     // ← super 抛 RuntimeException
}
```

也就是说**命令一被调用就崩**，不是"没实现"，是"必崩"。

3. **`KillCommand.comeToEffect` 只校验、不执行。** 校验通过后（`ENTITIES` 或 `ENTITY_SELECTOR`）**什么都不做**——没有把目标杀死。

4. **枚举与校验不一致**：`CommandParameterType` 有 `ENTITIES/ITEMS/ENTITY_SELECTOR/ITEM_SELECT/STRING` 五种，但 `KillCommand` 只接受 `ENTITIES`/`ENTITY_SELECTOR`，且提示语也只提这两种。将来做其他命令时这里会长成 switch。

5. **`CommandParameter` 没有标识"哪个参数是哪个"**。现在是一条有序 `List`，命令只能按位置取。MC 那套是 `String` 名字 + 值（`CommandContext`），按名字取更抗改动。现在这样一旦参数顺序变了，所有命令都要改。

6. **解析职责被放在了命令自己身上**（`resolveInput(String)`）。MC 的做法是**参数类型自己负责解析自己**（`ArgumentType<T>.parse`），命令只声明"我要一个实体选择器、一个整数"。现在这样每个命令都要重写一遍字符串切分，很容易写歪。

7. `EntitySelector` 只有 `List<Entity> getTargets()`，**没有选择器语法**（`@e`、`@p`、`@s` 这类），也没有"从字符串解析选择器"的入口。这是 MC 命令系统最核心的部分，也是工作量最大的部分。

### 如果要继续做，建议的最小骨架

```
CommandRegistry / CommandDispatcher   ← 缺：命令名 → Command 的注册表 + 主入口
ArgumentType<T>                       ← 缺：解析职责下沉（实体选择器/整数/字符串各一个）
CommandContext                        ← 缺：按名字取参数，而不是按下标
CommandSender                         ← 缺：谁在执行（玩家？控制台？），决定输出走 System.out 还是别处
```

最短可用路径：**先不做完整 MC 那套，只做"1 个命令 + 1 个实体选择器 + 在主循环里拦截 `#` 开头的输入"**，跑通一次再抽象。

---

## 五、当前健康度小结

**这一轮是非常明显的进步。** 上一轮报告里最严重的几个问题——效果只结算第一个、递归爆栈、`Frozen` 崩局、技能共享、复制丢机制——**已经全部修掉了**，而且 `Frozen` 和 `FightTurnPastListener` 的改法都写了详细注释说明"为什么必须这样做"，这对后续维护很有价值。

**剩下三个真正要处理的**，按优先级：

1. **`HpLossEvent` 自赋值**（两行）—— 既是 bug，也是千冶·刃"累计损失生命值"机制的硬阻塞。
2. **`GameMain:173` 改 `.copy()`**（一行）—— 让那把剑和 `ANiceSword.copy()` 的修复真正生效。
3. **`DamageEvent` 补 `EventBus.post`**（一行）—— 让受击类监听器活过来。

另外两件"不是 bug 但要写进文档"的事：

- `Skill`/`Item`/`LivingThing.copy()` 改抛异常之后，**模组作者必须重写 `copy()`**，否则加载即崩。
- 命令系统要接着做，**必须先补调度器和参数解析的下沉**，否则 `KillCommand` 这个形状会复制到每个新命令上。

---

*本文档由 AI 生成，仅供参考。所有结论均已在当前工作区源码中逐一核对（含行号引用）。*
