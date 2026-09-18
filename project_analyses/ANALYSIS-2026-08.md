# FightGameReforged 整体分析（2026-08 复核版）

> ⚠️ **历史文档（第 2 轮分析 · 复核范围 112 个 Java 文件）**：
> 本文档列出的缺陷**大多已在后续轮次修复**，其中对旧结论的"纠正"也已被后续源码再次推翻。
> 请以 **[`ANALYSIS-review4.md`](ANALYSIS-review4.md)（第 4 轮，最新）** 与当前源码为准；
> 保留本文档仅供追溯分析过程。

> 本文档由 AI 通读源码后生成，仅作参考，请以源码为准。
> 复核范围：`../src` 下全部 **112 个 Java 文件（约 8318 行）**、`../mods` 两个示例模组、`../config`、`../build.gradle`、`../logs`、`../build-output`。
> 本文档与仓库内已有的 `ANALYSIS.md` / `ProjectStatus.txt` 是**不同轮次**的分析结果；本文档纠正了其中若干**已被当前源码推翻**的结论（见 §7）。

---

## 零、TL;DR —— 10 个已核实的缺陷（按严重度）

| # | 缺陷 | 位置 | 后果 |
|---|---|---|---|
| 1 | `effectTimer` 里的 `return`（应为 `continue`） | `EffectEventListener.java:20` | **一局战斗里只有效果列表的第一个 Buff 会结算**，其余永不生效也永不到期 |
| 2 | `DamageEvent` 从未被 `post` | `LivingThing.java:1745` | 受击类监听器全部失效（李晓焰"每受击 +1 燃点"不生效）+ 监听器泄漏 |
| 3 | `canUse()` 里扣蓝 | `RestorationHealthSkill.java:37` | 反复扣蓝；蓝扣光后 `PlayerController` **死循环卡死** |
| 4 | `Skill.extraDamage` 只增不减 + 整数除法 | `PyrohemicPumping.java:29` | `extraDamage` 每回合 ×1.5 指数增长，伤害数值崩坏 |
| 5 | `Skill(Skill)` 不复制 `tags` | `Skill.java:55-68` | 复制后的技能 AI 丢标签，`ThinkingControllerAI` 判断失真 |
| 6 | `getCriticalDMG()` 取错字段 | `LivingThing.java:1813` | `criticalDMGEnhanceAmount` 永不生效 |
| 7 | 回合靠递归推进 | `TurnManager.java:57` | 长战斗爆栈（约几千回合触顶） |
| 8 | `canUse/use` 两套重载冷却不一致 + `canUse(...,null)` | `Skill.java:254/292`、`UniversalController.java:90` | 冷却多算 1 回合；给模组作者埋 NPE 坑 |
| 9 | `FightTurnPastListener.presentTurn` 是 `static` | `FightTurnPastListener.java:24` | 白厄觉醒/锁血反击依赖它，非当前回合的伤害会写错对象 |
| 10 | `TurnManager.init()` 不清空 `turns`；`World.things` 只增不减 | `TurnManager.java:34-54` | 上一局残留回合跨局污染 |

另有 **4 个模组系统级缺陷**（详见 §8.1，同样会直接导致"游戏起不来"）：

| # | 缺陷 | 位置 | 后果 |
|---|---|---|---|
| M1 | 只 `catch (Exception)` 不接 `Error` | `ModLoader.java:115/138`、`:148`、`:45` | 模组静态块抛异常 → `ExceptionInInitializerError`/`NoClassDefFoundError` 穿到 `GameMain` → **启动即崩** |
| M2 | `if (m == null) return;` 应为 `continue` | `GameStartEventListener.java:18-20` | 一个坏模组让其后**所有模组静默不加载** |
| M3 | `invokeWhenLoaded()` 抛异常无人接 | `EventBus.java:92-97` + `GameMain.java:37` | 模组内容写错一行 → 启动崩溃 |
| M4 | `URLClassLoader` 用 try-with-resources 提前 `close()` | `ModLoader.java:110-151` | 只靠"全量预加载"兜底；运行期反射/`getResource` 可能失败 |

以及 **4 个内容层"玩家能直接看到"的缺陷**（详见 §6）：

| # | 缺陷 | 位置 | 后果 |
|---|---|---|---|
| C1 | `copy()` 出来的实例不复制 `modifyDamage` | `LivingThing.java:93-147` | **李晓焰的"锁血"和"燃点≥10 免死回血"完全不生效** |
| C2 | `copy()` 时 `PlayerController` 分支共享技能对象 | `LivingThing.java:127/129` | **技能冷却与 `extraDamage` 在同模板的角色间串味**；白厄开大后清空技能表会污染模板，再选该角色**技能表是空的** |
| C3 | 李晓焰普攻加算/减算判据错位 | `actorLiXiaoYanSkills/CommonAttack.java:24-40` | `extraDamage` 净变负 → 伤害项归零后**给敌人回血** |
| C4 | `ANiceSword` 未重写 `copy()` | `ANiceSword.java` + `Item.java:151` | 剑的 `comeToEffect` 在奖励路径上被剥离 → **用了没反应**（自定义道具 100% 不可达） |

---

## 一、项目定位与规模

| 维度 | 结论 |
|---|---|
| 类型 | 命令行回合制文字战斗游戏，纯 Java，**无任何游戏引擎** |
| 版本 / 构建 | v1.2.1；Gradle + `com.gradleup.shadow` 打 fat jar；另有 jpackage 产物（`../build-output/FightGameReforged/FightGameReforged.exe` + 内嵌 runtime） |
| 语言 | Java **25**（`sourceCompatibility/targetCompatibility = VERSION_25`，本机为 Zulu 25） |
| 依赖 | 唯一第三方依赖 `org.json`（Gradle 声明 20240303，`../lib` 里放的是 20231013，两者不一致） |
| 入口 | `cn.gfhnv.game.GameStarter` → `GameMain.main` |
| 代码分布 | `LivingThing.java` 1840 行，占全项目 **22%**；`Skill` 481 行；`ThinkingControllerAI` 340 行；其余多为 <150 行 |
| 许可证 | MIT；作者注明不接受 PR |

代码量分布很不均衡：**实体层一个类吃掉了整个项目的五分之一**，而战斗循环、事件总线、模组加载这些"架构"部分反而相当精简（每个 100 行上下）。这说明项目是**以数据/角色为中心长出来的**，而不是以引擎为中心设计的。

---

## 二、启动与主循环（已核实）

```
GameStarter.main
  ├─ 打印作者信息
  ├─ LogWriter.writeLog(...)        ← 静态块在此刻归档旧 latest.log
  └─ GameMain.main(args)
       ├─ gameInitialize()
       │    ├─ World.addMod(new OfficialGameContent())   ← 注意：官方内容在构造器里就 addXxx 了
       │    ├─ ModLoader.modLoaderInitialize()           ← 扫描/编译/加载 mods/
       │    ├─ EventBus.register ×4（GameStart / Effect / Physics / FightStart）
       │    ├─ EventBus.post(GameStartEvent)             ← 触发各模组 invokeWhenLoaded + registerItself
       │    └─ ConfigLoader.loadConfig()                 ← 注入 AI Tag 权重
       ├─ 读玩家名
       ├─ startAFight()                                  ← CLI 选角色/敌人/奖励
       │    └─ EventBus.post(FightStartEvent)
       └─ do/while 问是否再来一局
```

`FightStartEvent` 之后进入**递归式**战斗循环（见 §4.1）：

```
FightStartEventListener.onFightStartEvent
  ├─ new FightTurnPastListener() → EventBus.register
  ├─ new FightEndEventListener(listener) → EventBus.register
  ├─ TurnManager.init(fight)          ← 静态 turns 列表，注意它**不会先清空**
  ├─ 全员 whenFightStart(fight)
  └─ EventBus.post(FightPastOneTurnEvent)  ──┐
                                            │
        FightTurnPastListener.fightTurnPastOne（每回合）
          ├─ 剔除死者 → TurnManager.removeTheDeath
          ├─ 判负 / 判胜 → FightEndEvent（结束递归）
          ├─ sort → 取 first → presentTime 推进
          ├─ 全员 updateSelf()
          ├─ 当前行动者技能 CD -1、回蓝
          ├─ 打印状态（HP + 五行能量）
          ├─ firstExecuteList → ActionSignal 分派 → controller.act
          ├─ 排新回合（10000/speed）或按 WITHOUT_NEW_TURN 再动一次
          ├─ lastExecuteList
          ├─ EffectUpdateEvent
          └─ TurnManager.nextTurn(fight) ────┘  ← 递归，一层 = 一个回合
```

---

## 三、六大设计（哪些是真的做成了）

1. **事件总线（`event` + `annotation`）** —— 真做成了。89 行的 `EventBus` 用反射扫 `@SubscribeEvent`，按 `priority` 升序插入（默认 3，数字小优先），`post` 时复制一份 handler 列表再遍历，因此**在 handler 内 register/unregister 自身是安全的**（这一点很多人会写错，这里是对的）。
2. **行动时间轴（`TurnManager` + `TurnEntry`）** —— 真做成了，而且是全项目最漂亮的部分。以 `BigDecimal(10000/speed, 10 位小数)` 为间隔，按 `startTime + needTime` 排序，天然支持加速/延迟/额外回合。`ActionSignal` 五态（`NORMAL / SPECIAL_ACTION / WITHOUT_NEW_TURN / SKIP / SKIP_WITHOUT_NEW_TURN`）+ 首/末动作列表（`ISpecialAction`）表达力足够。
3. **五行元素 + 五行法力** —— 设计有深度但**实现有硬伤**（见 §5.3 / §5.4）。
4. **技能系统** —— 倍率 + `aims`(0 自身 / -1 全体 / N 选 N) + 冷却 + 五行消耗 + AI Tag + `getAnticipatedDamage` 预测，接口划分合理。但 `canUse/use` 存在**两个语义冲突的重载**（见 §5.2）。
5. **Utility AI（`ThinkingControllerAI`）** —— 是**完整实现**，不是雏形：权重读取 → 情境修正（会被秒杀则治疗×10/防御×5、HP>60% 且敌人≥2 攻击×1.5、总蓝<30% 回蓝×3、带负面则防御×2）→ 选策略 Tag → 技能×目标组合评分（含击杀 +30、成本惩罚）→ 执行。质量不错。
6. **模组"编译即加载"** —— 真做成了：`JavaCompiler` 编译 `code/` 到 `bin/`，`URLClassLoader` 加载，`World.addMod` 注册，`GameStartEvent` 触发填充。

---

## 四、核心缺陷详解（10 项，与 §0 表格对应）

### 4.1 `EffectEventListener` 的 `return` 让"每回合效果结算"只对**第一个**效果生效 ★最严重

```java
// src/cn/gfhnv/game/eventListener/EffectEventListener.java:19-22
if (ef.isInfinity() || event.getTurnEntry().isExtra()) {
    ef.comeIntoEffect(thing);
    return;              // ← 应该是 continue；而且它落在循环里，直接终止整轮结算
}
```

后果（可复现，不需要猜测）：

- 实体身上有 2 个以上效果时，**只有列表第一个**会被 `comeIntoEffect` / 扣持续时间 / 到期移除。第 2 个及之后的效果**永远不会生效、也不会结束**。
- 更糟的是它同时是"额外回合"分支：只要当前回合 `isExtra()==true`，**整张效果表被跳过**，且第一个效果还会被重复 `comeIntoEffect`。

`TurnEntry.isExtra` 一旦设为 true 就**再没有任何地方把它设回 false**（`UltimateAttack` 创建的额外回合全是 `setExtra(true)`，而且它作为列表元素被移除后就不存在了）——所以这个"跳过"只在白厄觉醒的额外回合里发生，但结合下一段来看仍会造成冻结穿透。

**修复**：把 `return` 改成 `continue`，并把"额外回合不减持续"和"效果每回合生效"拆成两段逻辑。

### 4.2 回合推进是**递归**的，长战斗会爆栈

`TurnManager.nextTurn` → `EventBus.post` → `FightTurnPastListener` → 末尾再 `nextTurn`。整场战斗的调用栈深度 = 回合数 × 每回合帧数（`post` 一层、`EventHandler.accept` 一层、反射 `invoke` 若干层、`act` 若干层，实测每回合约 10~15 帧）。默认 1MB 栈大约在**几千回合**量级触顶。玩家对高等级 Boss 的持久战、或 `speed` 极低导致 `10000/speed` 巨大的情况，都是现实风险。

**修复**：把 `nextTurn` 改成 `while` 循环驱动的迭代推进（保留事件发布用来通知，但不要靠它递归）。

### 4.3 `UltimateAttack.comeToEffect` 依赖静态 `presentTurn`，白厄觉醒机制整段是脆的

```java
// normalSkills/UltimateAttack.java:63
FightTurnPastListener.getPresentTurn().setActionSignal(ActionSignal.SKIP_WITHOUT_NEW_TURN);
// :110
lastestOne.getLastExecuteList().add(...)   // 追加到"刚 new 出来的"那个 entry，看起来是笔误但语义上恰好可用
// Phainon.java:67 / :119
FightTurnPastListener.getPresentTurn().getLastExecuteList().add(...)   // 锁血反击挂在当前回合
```

`FightTurnPastListener.presentTurn` 是 `static` 字段。任何"不在当前回合"的伤害（例如上一回合挂起的 `lastExecuteList`、或 AI 预测调用 `getAnticipatedDamage` 触发的 `DamageEvent`）都会写错对象，甚至 NPE。虽然 `DamageCalculate` 走的是 `CalculateDamage*Event` 而非 `DamageEvent`，目前侥幸没炸，但这是**定时炸弹**。

另外 `Phainon.java:60-67` 的 `damageModify` 闭包捕获的是 **lambda 外层的 `phainon` 变量（原始实例）**，却对 `da.getAttackedEntity()`（可能是副本）做判断和置位——两者在多角色/复制场景下不是同一个对象。

### 4.4 `Skill` 有两个语义冲突的 `canUse/use` 重载

```java
canUse(fight, user)            // 不带目标
canUse(fight, user, enemies)   // 带目标
use(fight, user)               // 冷却设为 coolDown
use(fight, user, enemies)      // 冷却设为 coolDown + 1   ← 两者不一致！
```

- **冷却不一致**：同样是"释放一次"，三参版本比二参版本多 1 回合冷却。`RestorationHealthSkill`/`UltimateAttack` 这类重写二参 `canUse` 的技能，实际由 `PlayerController` 用 `canUse(fight,user) && canUse(fight,user,null)` 组合判定，再用三参 `use` 释放 → 拿到 `coolDown+1`。
- **`UniversalController.act:90` 传 `null`**：`skill.canUse(fight, getOwner(), null)`。基类忽略该参数所以不炸；任何未来重写三参版本并解引用 `enemies` 的模组技能会立刻 NPE。这是**给模组作者埋的坑**。
- **`Skill.copy()` 不复制 `tags`**（`Skill.java:55-68`）：`name/description/倍率/aims/cd/mana/extraDamage` 都复制了，唯独 `tags` 漏了。于是"复制后的技能 AI 看不到标签"——`getSkillMainTag` 会 fallback 成 `ATTACK`。而 `UniversalController.setSkills` 恰好对每个技能调 `skill.copy()`，所以**玩家/怪物在 `Phainon` 觉醒或任何 `setSkills` 路径下都会丢技能标签**。

### 4.5 `LivingThing.getCriticalDMG()` 取错字段

```java
// LivingThing.java:1812-1814
public double getCriticalDMG() {
    return criticalDMG * (1 + criticalDMGEnhancePercent) + criticalDMGEnhancePercent;
    //                                                          ^^^^^^^^^^^^^^^^^^^^^^^^
    //                                                          应为 criticalDMGEnhanceAmount
}
```

`criticalDMGEnhanceAmount` 字段被 set/get 但**从未参与任何计算**。`CriticalDMGEnhanceEffect` 若走"固定值"路线会完全无效。伤害公式里 `criticalDamageEnhance += attacker.getCriticalDMG()`，所以这是实际战斗数值错误。

### 4.6 `Mana` 只有一个"上限"，元素属性是装饰

```java
new Mana(0, ElementSort.UNIVERSAL);   // UltimateAttack / LastAttack 里到处这么写
```

`Mana(amount, element)` 把 `amountMax = amount`。于是"消耗 0 点通用法力"写法同时意味着**上限 0**。`Skill.canUse` 的循环里，如果 `consumedMana.getElementSort()==UNIVERSAL`，它检查的是"是否找到任一 `mana.getAmount() >= 0`"，几乎恒真——所以**这个消耗检查形同虚设**。

更要紧的是 `Skill.canUse` 的循环：

```java
for (Mana mana : user.getManas()) {
    if (consumedMana == null) { canUseMana = true; break; }
    ...
}
if (canUseMana && nowCoolDown <= 0) canUse = true;
```

`user.getManas()` 为空时循环不执行 → `canUseMana` 保持 `false` → **即使技能零消耗也判为不可用**。任何没有走 `LivingThing` 完整构造器（因此没调 `initialMana()`）的实体，会直接"没行动"。

### 4.7 `canUse()` 会产生副作用（扣蓝 / 死循环）★同样严重

```java
// universalSkill/RestorationHealthSkill.java:33-44
@Override
public boolean canUse(Fight fight, LivingThing user, List<LivingThing> enemies) {
    for (Mana mana : user.getManas()) {
        if (mana.getElementSort().equals(user.getElementSort())) {
            if (mana.getAmount() >= this.neededManaScale) {
                mana.setAmount(mana.getAmount() - neededManaScale);   // ← 判定里扣蓝
                return true;
            }
        }
    }
    System.out.println("能量不足");
    return false;
}
```

`canUse` 的契约是"**能不能用**"，这里却在里面**真的扣了法力**。而 `PlayerController.act` 的流程是：

```java
if (selectedSkill.canUse(fight, getOwner()) && selectedSkill.canUse(fight, getOwner(), null)) { break; }
```

多参版本被反复调用，于是：

1. 每按一次无效输入 / 每循环一轮，就**多扣一次 `neededManaScale` 点主元素法力**。
2. 法力被扣光后 `canUse` 恒返回 `false` → 第 63 行的 `while(true)` **永远无法 `break`**，玩家被卡在"技能释放条件不满足，请重新选择"里出不来（只能 Ctrl+C）。
3. 即使一次成功，`use()` 里 `nowCoolDown <= 0` 的保护也拦不住第二次扣蓝——因为扣蓝发生在 `use()` **之前**。

顺带两个相关问题：

- `Freeze`（`universalSkill/Freeze.java:16`）声明了 `atkMagnification = 7.5` 与 `TagType.ATTACK`，但 `comeToEffect` **只加 `Frozen` 效果、不造成任何伤害**。AI 会把它当高伤攻击技能评估（`getAnticipatedDamage` 还会据此算出 7.5×攻击的"预期伤害"），导致 `ThinkingControllerAI` 的评分被系统性带偏。
- `Freeze.comeToEffect:29-30` 对 `enemies.get(0)` 无长度检查；`freeze` 是 `aims=2` 的技能，目标池只有 1 人时 `UniversalController` 会传长度 1 的列表，`get(1)` 有越界风险（有 `size()==2` 判断兜住，但 `get(0)` 本身没有兜）。

### 4.8 `Skill.extraDamage` 只增不减，会跨回合累积

`Skill.java:46` 的注释写明：

```java
private long extraDamage = 0;//多倍率时把其他倍率计算的结果加到这里.伤害计算后重置为零
```

但**全项目没有任何一处重置它**。而 `PyrohemicPumping`（李晓焰的灼血泵动）在 3 个地方改它：

```java
// PyrohemicPumping.java
:29  if (user.getHp() / user.getHpMax() <= 0.5) {          // ← 整数除法！永远为 0
         this.setExtraDamage((long) (this.getExtraDamage() * 1.5));
     }
:39  setExtraDamage((long) (this.getExtraDamage() + user.getHpMax() * 0.5));
:49  setExtraDamage((long) (this.getExtraDamage() - user.getHpMax() * 0.5));
```

后果：

- `user.getHp() / user.getHpMax()` 是 **`long / long` 的整数除法**，结果只能是 0（除非满血为 1）。`<= 0.5` 因此**恒成立**，这条"半血以下额外增伤"的判定形同虚设，而且每回合都把 `extraDamage` 乘以 1.5 → **指数增长**。
- 只要某回合 `ignition < 8` 提前 `return`（第 45 行），第 47 行的 `enhanced` 分支就被跳过 → **`- user.getHpMax() * 0.5` 不会执行**，那笔加成就永久留在技能对象上，下一次攻击继续吃。
- 因为技能对象是**每个实体私有**的（`UniversalController(skills, owner)` 直接持有传入的 list，`copy()` 又走 `new PyrohemicPumping()`），这个污染会跟着角色跨回合、甚至跨战斗（`whenFightEnds` 只重置 CD，不重置 `extraDamage`）。

同类"整数除法当小数用"的地方还有 `Phainon` 之外的若干处，建议全项目搜一遍 `getHp() / getHpMax()`。

### 4.9 `DamageEvent` 从来没有被 `post` 过 —— "每次受击获得燃点"整条链路是死的 ★

全项目 `EventBus.post(...)` 的调用点只有 12 处（已用 grep 全量核对）：

```
GameStartEvent、FightStartEvent、FightPastOneTurnEvent、FightEndEvent、
EffectUpdateEvent、SelectTargetEvent、CalculateDamageGetStatusEvent、
CalculateDamageEndEvent、HpLossEvent、HpRestorationEvent、AwakenEndEvent
```

**没有 `DamageEvent`。**

```java
// LivingThing.java:1744-1749
public void makeDamage(LivingThing attacked, Skill skill) {
    DamageEvent damageEvent = new DamageEvent(this, attacked, skill);   // ← 只 new，不 post
    System.out.print("造成了" + damageEvent.getDamage().getDamageAmount());
    attacked.getDamage(damageEvent);
}
```

于是一个**已注册但永远不会被调用**的监听器：

```java
// officialStuff/customEvent/LiXiaoYanEvents/DamageEventListener.java:10-21
@SubscribeEvent
public void getIgnition(DamageEvent event) { ... setIgnition(getIgnition() + 1); }
```

这正是李晓焰终结技描述里承诺的机制：

> 「在此期间，每受到一次攻击，获得 1 层【燃点】。」

**它不生效。** 而且 `UltimateAttack.comeToEffect:48-50` 每次释放都会 `new DamageEventListener()` 并 `EventBus.register(...)` —— 因为效果永远不过期（见 §4.1），这些监听器**每次开大都会往 `EventBus.handlers` 里堆一个**，实战几局后就是纯泄漏（虽然不会被调用）。

**修复**：`makeDamage` 里补 `EventBus.post(damageEvent)`；同时把 `DamageEvent` 的构造期副作用（构造函数里 `new Damage()` → 立刻算完伤害）改成显式 `calculate()`，否则"post 前伤害就已定格"，监听器无法参与修正。

**与 §4.1 联动**：`MemorizedHp` 的 `lastTime` 是 3，但 `effectTimer` 的 `return` 让它**永远不减**，所以 `whenLastTimeEnd` 永不触发 → 生命值锁定永不解除、监听器永不注销。玩家视角就是"锁血 Buff 挂到天荒地老"。

### 4.10 白厄觉醒技能组的数值污染与"永久无敌"
这两个问题都源于同一个反模式：**技能把自己当可变状态用，用改倍率/改字段的方式传递"本次攻击的临时参数"。**

**(a) `Counterattack` 把 `atkMagnification` 永久改成 0.3**

```java
// awakenSkills/Counterattack.java:43-55
this.setAtkMagnification(this.getAtkMagnification() * (1 + soulscorch * 0.2));
randomMag = randomMag * (1 + soulscorch * 0.2);   // randomMag 初值 0.3
for (LivingThing livingThing : enemies) { user.makeDamage(livingThing, this); }
this.setAtkMagnification(randomMag);              // ← 保存的是 0.3×倍率，不是原值
for (int i = 0; i <= 5; i++) { ... user.makeDamage(enemies.getFirst(), this); }
this.setAtkMagnification(1);                      // ← 硬编码 1，而原值是 1
```

"恰好"把 1 还原成了 1，所以目前看不出问题；但只要有人把 `Counterattack` 的初始 `atkMagnification` 从 `1` 改掉（构造函数第 15 行），**每次反击都会把倍率越改越小**。另外 `enemies.getFirst()`（第 52 行）对空列表会抛异常，而 `aims = -1` 时 `enemies` 完全可能是空列表。

**(b) `CalamitySoulscorchEdict` 的 75% 伤害吸收可能永远不被清除**

```java
// awakenSkills/CalamitySoulscorchEdict.java:48-50
if (!((Phainon) user).isAbsorbDamage())
    ((Phainon) user).setDamageAbsorbedPercent(user.getDamageAbsorbedPercent() + 0.75);
((Phainon) user).setAbsorbDamage(true);
```

对应的清除逻辑只在 `Counterattack` 里：

```java
// Counterattack.java:31-36
soulscorch = phainon.getSoulscorch();
phainon.setSoulscorch(0);                 // ← 先归零
...
if (phainon.isAbsorbDamage()) {           // ← 之后才读，但清除本身没依赖 soulscorch
    phainon.setDamageAbsorbedPercent(phainon.getDamageAbsorbedPercent() - 0.75);
    phainon.setAbsorbDamage(false);
}
```

而 `Counterattack.comeToEffect` 只能从两个地方被调用：

1. **额外回合的首动作**（`UltimateAttack.java:87-95 / 100-109`）——条件包含 `getSoulscorch() > 0`；
2. `CalamitySoulscorchEdict` 的 `lastExecuteList` 回调——条件也包含 `phainon.getSoulscorch() > 0`。

而弑魂焚诏自己每次释放都会 `setSoulscorch(+1)`（第 47 行）。于是最坏情况下：**吸收加成按 0.75 的步长叠加，清除永远赶不上叠加**（`setDamageAbsorbedPercent` 没有上限检查）。当 `damageAbsorbedPercent >= 1` 时，`DamageCalculate` 里的 `(1 - damageAbsorbed)` 变成 ≤ 0 → **伤害归零甚至为负（治疗敌人）**。这就是"看起来有 8 个额外回合的华丽变身，实战变成无敌挂"的原因。

**(c) `CalamitySoulscorchEdict:55-56` 有必炸的 NPE 路径**

```java
TurnManager.getNextTurnOf(e).setStartTime(TurnManager.getPresentTime());
```

`getNextTurnOf` 里遍历 `turns` 找不到就 `return null`，紧接着 `.setStartTime(...)`。而"让敌方全体立即行动"这个设计本身就要面对"某个敌人本回合还没排到 / 已经行动过"的情况 —— 后者必然返回 `null`。需要加空值处理与"重排回合"的逻辑，而不是直接改字段。

**(d) `getNextTurnOf` 每次调用都做一次全表 `sort()`**，而它在 `for (LivingThing e : enemies)` 里被调用两次 → 每次开大 O(n² log n)。规模小的时候无感，但这是明显可避免的开销。

### 4.11 用 `copy()` 造出来的战斗实例**丢了一半状态**（含三个玩家可见后果）★

这是本次复核**最意外**的发现。`GameMain.startAFight()` 全程用 `livingThing.copy()` 生成参战实例（`:93`、`:133`、`:173`），而 `LivingThing(LivingThing other)` 的复制清单**漏得比想象中多**。

**(a) `PlayerController` / `ThinkingControllerAI` 分支共享技能对象（不是拷贝）**

```java
// LivingThing.java:126-132
if (other.getController() instanceof PlayerController) {
    this.controller = new PlayerController(other.controller.getSkills(), this);   // ← 传的是"原 List"
} else if (other.getController() instanceof ThinkingControllerAI) {
    this.controller = new ThinkingControllerAI(other.controller.getSkills(), this); // ← 同上
} else {
    this.controller = new UniversalController(other.controller, this);            // ← 这个分支才是深拷贝
}
```

对比 `UniversalController` 的复制构造器（`:33-43`）——它会 `skills.add(skill.copy())`、还会 `iInitialize.copy()`，**是正确的**；但 `PlayerController(List, owner)` / `ThinkingControllerAI(List, owner)` 走的是 `UniversalController(List, owner)`（`:45-48`），**直接持有传入的 List，不复制**。

后果是具体的：

- 玩家选到的每个角色副本，与 `World` 里的**模板**、以及**同一模板的其它副本**共用同一批 `Skill` 对象。
- **冷却共享**：`FightTurnPastListener:73-75` 是对"当前行动者的技能表"减 CD，但技能对象是共享的 → **A 用过的技能，B 也在冷却**。
- **`extraDamage` 串味**：李晓焰的 `extraDamage`（见 §4.8 与 §6）会污染同模板的所有角色。
- **觉醒清空技能表**：`UltimateAttack:112` 在变身结束时执行 `user.getController().getSkills().clear()` —— 由于列表共享，这会**清空模板的技能表**。之后用同一模板再选一个该角色，`copy()` 出来的副本技能表是空的（"没行动"）。玩白厄 → 开大 → 打完 → 再选白厄，必然复现。
- 怪物走 `UniversalController` 分支，所以**只有玩家侧有这个问题**。

**(b) `modifyDamage` 完全没有被复制**（已在 §6.1 展开）

`LivingThing(LivingThing)` 里没有任何一处给 `this.modifyDamage` 赋值（`modifyDamage` 只在 `:1838` 的 setter 里被写过）。而 `ActorLiXiaoYan(125)` 是在构造器里 `setModifyDamage(...)` 的 → **模板上挂着锁血/复活回调，副本上却是 `IModifyDamage.DEFAULT`**。

结果：李晓焰的两个招牌机制——"生命值锁定"、"燃点 ≥10 时免死并回复 30% 生命"——**在实际游戏里完全不生效**。玩家能从数据里读到，但永远触发不了。白厄不受影响，因为它重写了 `copy()` 并在 `Phainon(Phainon)` 里重建了拦截器。

**(c) 所有 `*Enhance*` 字段、五元素穿透/增伤、`individualMultipleArea`、`showSpecialMes` 也没复制**

`LivingThing` 复制构造器复制的字段只有：抗性 ×5、`elementSort`、`description`、`speed`、`type`、`Alive`、`defenseLoss`、`enhance`、成长系数 ×8、`hp/defence/attack/hpMax`、`criticalDMG`、`getCriticalRATE`、效果列表、`penetration`、`damageAbsorbedPercent`、`manas`、`inventory`、`tags`。

**未复制**：`attackEnhancePercent/Amount`、`defenceEnhance*`、`speedEnhance*`、`hpEnhance*`、`criticalDMGEnhancePercent/Amount`、`criticalRateEnhancePercent/Amount`、`metal/wood/water/fire/dirtPenetration`、`metal/wood/water/fire/dirtDamageEnhance`、`individualMultipleArea`、`showSpecialMes`、`modifyDamage`、`extraDamage`。

后果举例：`ActorLiXiaoYan:43` 的 `setIndividualMultipleArea(1.0 + 0.04 * ignition)` 写的倍率**在副本上是 1**（构造函数默认值），于是 `updateSelf()` 里的增量修正（`:121-124`）作用在一个错误的基准上——李晓焰的"燃点越高伤害越高"整体失真。另外 `showSpecialMes` 丢失意味着**副本不再打印"燃点层数"**（白厄的 `showSpecialMes` 能工作，是因为 `Phainon(Phainon)` 里手动重建了）。

> **一句话总结**：项目里"模板 → 副本"的契约只被实现了大约七成，而且**漏掉的恰好是三个角色的核心机制**。`copy()` 是否完整，是这类"数据驱动内容 + 复制式实例化"架构最容易出事的地方，建议直接写一个"模板 vs 副本字段全量比对"的单元测试来兜住。

---

## 五、次要问题（可延后，但建议记录）

### 5.1 事件取消机制是半成品
`Event.isCanceled` 只在 `post` 入口和每个 handler 调用前检查。没有"取消后回滚已执行 handler"的能力，也没有 `setCanceled` 的任何调用点（全项目 0 处调用）。`ActEvent` 被定义了但**从未被 `post`**；`PhysicsStateUpdateEvent` 同样**从未被 `post`**，所以整个 `system/physics` + `PhysicsEventListener` 是**死代码**（`Vector`/`Force`/`Velocity`/`Acceleration`/`Position` 只在 `Thing` 里当字段存着）。

### 5.2 `Inventory` 空背包判定与奖励发放
`GameMain` 里玩家自选奖励，`FightEndEventListener` 又把**同一批奖励复制给每个存活角色**：

```java
for (LivingThing living : fighters)
    for (Item item : rewards)
        if (!living.getInventory().addItem(item.copy())) result = false;
```

3 个角色 + 2 件奖励 = 发出 6 份。`addItem` 的堆叠判等用 `Item.equals`，而 `Item.equals` 调了 `super.equals`（`Thing.equals` 按 **UUID** 比）→ **两个"同款"物品永远不相等**（`Slot.copy` 还会 `item.copy()` 再造新 UUID）。所以 `addItem` 永远走"找空格子"分支，**堆叠功能实际上从不生效**。

### 5.3 `getHpMax()` 的动态实现与 `setHp` 的钳制不对称
`getHpMax()` 每次现算 `hpMax*(1+pct)+amount`，而 `setHp` 钳到 `min(getHpMax(), hp)`。掉 Buff 时 `whenLastTimeEnd` 里 `renewHp()` 只在 `setHpEnhance*` 系列里调用——**直接改 `hpMax` 字段或让 HpEnhanceEffect 到期不会把当前 HP 压回新上限**，只有下一次 `setHp` 才会。白厄 `+270%` 生命的那段（`UltimateAttack:59-61`）依赖 `setHp(getHpMax())` 恰好补上，逻辑能跑通但对修改很脆弱。

### 5.4 输入层
- `PlayerController.useItem` 用 `SCANNER.nextInt()`，与全局的 `nextLine()` 混用 → **残留换行符**会让下一次 `nextLine()` 读到空串。
- `GameMain.startAFight` 把角色/敌人/奖励三段选择塞进一个 `while(true)`，只有 `!enemies.isEmpty() && !fighters.isEmpty()` 才 break；**奖励为空时无法结束**（除非输入 `next` 恰好通过内层 break，但外层仍要求两者非空——实际上必须选过至少一个敌人才可能退出）。
- `livingThings` 数组在循环外取快照、循环内复用；中途 `World.getEntityList()` 变化会导致索引错位/越界。
- 空输入 `Integer.parseInt("")` 的 `NumberFormatException` 被 `catch(Exception)` 吞掉，玩家只会看到"输入错误"。

### 5.5 全局静态状态
`TurnManager.turns/presentTime`、`EventBus.handlers`、`World.*`、`FightTurnPastListener.presentTurn/theDeath`、`Phainon.isListenerRegister`（`static`！）、`GameMain.userName/SCANNER` 全是静态。

具体后果：
- `TurnManager.init()` **只 `turns.add(...)`，从不清空**。正常流程靠 `FightEndEvent` 里两次 `getTurns().clear()` 兜住；一旦战斗异常退出，下一局会带着上一局的残留回合。
- `World.things` 跨局只增不减（`GameMain` 每局 `addThing`），`logs` 证明典型局只玩一两次所以没暴露。
- `Phainon.isListenerRegister` 是 `static`：**两个白厄会互相顶掉事件注册**（第一个 `whenFightStart` 置 true，第二个就不再注册 `FightStartAndSelectEventListener`）。

### 5.6 `isAlive()` 有副作用
`isAlive()` 内部会 `setActionSignal(NORMAL); setSpecialAction(null)`（`LivingThing.java:1467-1471`）。它被 `removeIf` 在每回合开头无条件调用，也被 `TurnManager.removeTheDeath` 调用。**"查询存活"会顺手清掉冰冻/特殊行动状态**——`Frozen` 设置的 `SPECIAL_ACTION` 与 `SkipTurn` 有可能被这句抹掉，属隐蔽的行为耦合。

### 5.7 日志与输出
- `LogWriter` 每次启动把 `../logs/latest.log` 改名成时间戳文件，但 `archiveLatestLog()` 只移动不清理 → `../logs` 会无限增长（当前已 9 个文件）。
- **游戏文本全部 `System.out`，与日志、调试输出混在一起**，且 `LogWriter` 只记启动时间与用户名，几乎没记游戏事件。
- 日志本身是 UTF-8（已验字节 `E7 94 A8 ...` = "用户"），用 Windows 记事本按 GBK 打开会显示乱码——不是 bug，但容易被误认为 bug。
- `LogWriter.writeLog(e.getMessage())` 在 `getMessage()==null` 时写入字符串 `"null"`。

### 5.8 死代码 / 未接线
| 位置 | 状态 |
|---|---|
| `system/physics` + `PhysicsEventListener` | 已实现，`PhysicsStateUpdateEvent` **无发布者** → 永不执行 |
| `event/ActEvent` | 已定义，**无发布者** |
| `system/useItemSystem` | **空包**（无任何文件） |
| `entityController/FixOrderController` | 实现了固定顺序队列，**无任何引用** |
| `ThinkingController` | 有设计注释，`act()` 末尾**无条件回退随机**；`getWeight` 用了却算出未使用的 `attackPos/defendPos/...`。实际生效的是 `ThinkingControllerAI` |
| `../config/gameConfig/PropertyConfig.json` | 全项目**无任何代码读取** |
| `HealthRestoreEffect:55` | `long hp2 = ...` 计算后未使用 |
| `Engine.Statistics`（Thing/Entity 中的若干 `facSetXxx`） | 大量工厂方法无调用点 |

---

## 六、内容层缺陷（官方角色 / 技能 / 物品）

### 6.1 李晓焰的普攻：加算与减算用**不同时刻**的燃点判断 → 伤害项净负、最后给敌人加血

```java
// actorLiXiaoYanSkills/CommonAttack.java
:23-27   if (user instanceof ActorLiXiaoYan) {
             if (((ActorLiXiaoYan) user).getIgnition() >= 8)          // ← 自增"之前"的燃点
                 setExtraDamage((long) (this.getExtraDamage() + user.getHpMax() * 0.5));
             enhanced = true;                                          // ← 无条件为 true
         }
:31-33   ...setIgnition(getIgnition() + 1);                           // ← 燃点自增
:34-40   if (...getIgnition() < 8) return;                            // ← 自增"之后"的燃点
         ...setIgnition(getIgnition() - 1);
         if (enhanced) setExtraDamage((long) (this.getExtraDamage() - user.getHpMax() * 0.5));
```

两侧判据对不上，于是**燃点 = 7** 时每打一次普攻就净减 `hpMax × 0.5`：

| 时刻 | 燃点 | 加算（≥8?） | 减算（<8?） | 净变化 |
|---|---|---|---|---|
| 起手 | 7 | 否（7 < 8） | — | — |
| 自增后 | 8 | — | 否（8 ≮ 8）→ 执行减算 | **−0.5×hpMax** |

而 `Skill.extraDamage` **全项目没有任何重置点**（`Skill.java:46` 注释写的"伤害计算后重置为零"是假的；`DamageCalculate.java:66` 只读不写）。125 级李晓焰 `hpMax = 7392`，单次 `0.5×hpMax = 3696`，而普攻基础伤害是 `hpMagnification(1.0) × 当前HP ≈ 7392`：

- 第 1 次：伤害 ≈ 7392 − 0 = 7392
- 第 2 次：7392 − 3696 = 3696
- 第 3 次：≈ 0（`DamageCalculate:112` **没有 `Math.max(0, ...)` 下限**）
- 第 4 次：**负数** → `LivingThing.getDamage` 里 `newHp = getHp() - 负数 = 增加` → **给敌人回血**

配合 §4.11(a) 的技能对象共享，这个污染还会跨副本、跨战斗累积。**这是目前最容易让玩家看到"打敌人反而加血"的路径。**

### 6.2 `ANiceSword` 没有重写 `copy()` → 这把剑的效果永远无法触发

```java
// customItem/ANiceSword.java —— 没有 copy() 重写
public class ANiceSword extends Item {
    public ANiceSword() { super("一把剑", "使用后增加伤害1回合", "aNiceSword"); }
    @Override public void comeToEffect(LivingThing user, Fight fight) { user.addEffect(new DamageEnhanceEffect(2, 1).setOrigin(getId())); }
}
```

`Item.copy()`（`Item.java:151-153`）返回的是 `new Item(this)` —— **基类实例，丢失子类覆写**。而奖励发放路径恰好两处都用了基类复制：

```java
// GameMain.java:173（玩家选择奖励时）
selectedItem = new Item(items[Integer.parseInt(input)]);
// FightEndEventListener.java:47（发放时）
if (!living.getInventory().addItem(item.copy())) result = false;
```

所以玩家拿到手的"一把剑"是**普通 `Item`**，`comeToEffect` 是空实现 → **用了没反应**。这与 §5.2 的堆叠失效同源（都是"基类 `copy()` 丢子类"），建议把 `Item.copy()` 改成抽象/抛异常，强制子类实现。

### 6.3 `AttackEnhance` 的 `isOn` 永远是 `false` → Buff 每回合重复叠加，只减一次

```java
// customEffect/universalEffects/AttackEnhance.java:40-45
@Override public void comeIntoEffect(LivingThing thing) {
    if (!isOn) {
        thing.setAttackEnhanceAmount(thing.getAttackEnhanceAmount() + amount);
        thing.setAttackEnhancePercent(thing.getAttackEnhancePercent() + percent);
    }
    // ← 忘了 isOn = true;
}
@Override public void whenLastTimeEnd(LivingThing thing) {
    isOn = false;
    thing.setAttackEnhanceAmount(thing.getAttackEnhanceAmount() - amount);   // 只减一次
    ...
}
```

对比同类实现：`DamageEnhanceEffect.java:67` 和 `HpEnhanceEffect.java:50` 都老老实实写了 `isOn = true`。这里漏了一行，症状是**攻击力随持续回合数线性膨胀，到期只回落一次**（永久虚高）。

### 6.4 `FightStartAndSelectEventListener.listen2` 永远不会执行

```java
// Phainon.java:274-278
public void whenFightStart(Fight fight) {
    if (!isListenerRegister()) EventBus.register(this.fightStartAndSelectEventListener);   // ← 在这里注册
    isListenerRegister = true;
}
```

`whenFightStart` 是在 `FightStartEvent` 的 handler 里被调用的（`FightStartEventListener.java:18`），而 `EventBus.post` 遍历的是**进入分发时就做好的快照**（`EventBus.java:63` `new ArrayList<>(eventHandlers)`）。因此**本事件分发期间新注册的 handler 不会收到本事件**：

```java
// FightStartAndSelectEventListener.java:27-35
@SubscribeEvent public void listen2(FightStartEvent event) { ... setExtraAbilityTier(+1); setCoreflame(+1); }
```

→ 开战时的 **+1 火种 / +1 extraAbilityTier 从未生效**。（同类的 `listen1` 监听 `SelectTargetEvent`，那时才注册、事件也更晚，所以能正常工作。）

顺带：`Phainon.java:256` 在 `whenFightEnds` 里注销了它，而 `listen1` 依赖它给白厄加火种/暴伤——这条链是通的，说明作者的意图确实是"开战就挂上"，只是漏了"事件总线不派发给新订阅者"这个语义。

### 6.5 `AwakeEndListener` 中的注销是死代码

```java
// Phainon.java:265-270 —— whenFightEnds 试图注销
for (Skill skill : getController().getSkills()) {
    if (skill instanceof UltimateAttack) { EventBus.unregister(((UltimateAttack) skill).getAwakeEndListener()); break; }
}
```

但 `UltimateAttack` 是在 `Phainon(long)` 构造器里 `new` 出来交给 `PlayerController(skillList, this)` 的；`whenFightStart` → `EventBus.register(...)` 注册的是**该实例**上的 `awakeEndListener`。而 `UltimateAttack.copy()`（`:44-46`）返回 `new UltimateAttack()` 并**自带一个新的 `AwakeEndListener`**，`UniversalController` 的深拷贝路径（`setSkills`、`UniversalController(UniversalController,owner)`）都会换掉实例 → `whenFightEnds` 遍历到的是**副本上的、从未注册过的** `AwakeEndListener`，注销一个不存在的订阅者。

又因为 §4.11(a)，白厄的 `PlayerController` 分支实际**共享**技能对象，所以这条注销"有时候碰巧对、有时候错"，行为取决于走过哪条复制路径——典型的隐式耦合。

### 6.6 `Frozen` / `SkipTurn` 的状态残留（晕眩后崩溃链路）

`Frozen.comeIntoEffect` 每次都把 controller 设成 `SPECIAL_ACTION` + `SkipTurn`，而 `FightTurnPastListener` 的两处处理不对称：

```java
// FightTurnPastListener.java:99-101
} else if (presentTurn.getActionSignal().equals(ActionSignal.SPECIAL_ACTION)) {
    presentTurn.getLivingThing().getController().getSpecialAction().execute(...);   // ← 若 getSpecialAction() 为 null 则 NPE
}
// :102-107
if (presentTurn.getActionSignal() != ActionSignal.WITHOUT_NEW_TURN && !...SKIP_WITHOUT_NEW_TURN) {
    TurnEntry turn = new TurnEntry(presentTurn.getLivingThing(), ..., TurnManager.getPresentTime());  // ← 构造器读取 controller.getActionSignal()
```

`TurnEntry` 的构造器会把 `controller.getActionSignal()` **固化**进条目（`TurnEntry.java:24`），而 `Frozen.whenLastTimeEnd` 会在效果到期时把 `actionSignal` 重置为 `NORMAL` 并把 `specialAction` 置为 `null`。只要"条目的 signal 是 `SPECIAL_ACTION`"与"controller 的 specialAction 已被清空"这两件事错位一个回合，`:100` 就会 NPE。

这个错位是可能出现的，因为 §4.1 的 `return` 让 `whenLastTimeEnd` **并不总在它该在的时候执行**（`EffectEventListener` 里 `isExtra` 分支会直接跳过整张表）。就现状而言，**冰冻是唯一会写 `SPECIAL_ACTION` 的官方效果，且它自身的结算时机又受 §4.1 影响**——两者叠加，晕眩/冰冻之后崩溃是可复现的路径，修 §4.1 时应一并把"效果到期时不该清别人的 actionSignal"改成"条目自己持有一次性动作"。

### 6.7 战斗开始时机与 `isListenerRegister` 的 `static` 竞争

`Phainon.isListenerRegister` 是 **`static`** 字段（`Phainon.java:28`）。两个白厄（或模板 + 副本）的 `whenFightStart` 会互相顶掉：第一个把它置 `true`，第二个就不注册 `FightStartAndSelectEventListener` 了。`whenFightEnds` 又把它重置为 `false`（`:254`），所以行为取决于调用顺序。应改为实例字段——或干脆把监听器注册合并进实体自己的 `select` 回调。

### 6.8 死亡实体在战斗**中途**就被 `whenFightEnds()` 处理（且随后被处理第二次）

```java
// FightTurnPastListener.java:114-117 —— 在战斗循环里，对"本回合刚死的人"调用
for (LivingThing dead : theDeath) {
    dead.whenFightEnds();          // ← 但 whenFightEnds 的语义是"战斗结束清理"
}
theDeath.clear();
```

而 `LivingThing.whenFightEnds()`（`:1727-1736`）做的是：**把 HP 回满**、清空全部效果、把所有技能冷却归零、五行法力回满。也就是说——**怪物一旦死亡，立刻被满血复活、Buff 全清、技能全好**，只是已经从 `fighterList`/`enemiesList` 里被 `removeIf` 掉了，所以不再参与胜负判定。

真正有害的是它触发的副作用链：

- `Phainon.whenFightEnds()` 会把 `coreflame = 0`、`isListenerRegister = false`、并在 `isAwaken` 时 post `AwakenEndEvent` ——**白厄只是"这一场里死了"，却会提前把自己的觉醒状态机和事件监听全部收摊**。
- `ActorLiXiaoYan.whenFightEnds()` 把燃点重置为 3。
- 之后战斗真正结束时，`FightEndEventListener.java:23-28` 又对 `getAllEntities()` **再调一次** `whenFightEnds()` → 同一实体被清理两次。

**修复方向**：把"战斗结束清理"和"实体死亡清理"拆成两个方法（`whenFightEnds()` / `onDeath()`），死亡只做该做的（掉落、亡语），不要复用结束逻辑。

### 6.9 白厄觉醒状态机的 5 个残留问题

**(a) `extraAbilityTier` 的反向回滚公式失效 → 攻击加成永久残留**

```java
// Phainon.updateSelf:294-295（每回合，增量式）
setAttackEnhancePercent(getAttackEnhancePercent() + extraAbilityTier * 0.5 - formerExtraAbilityTier * 0.5);
formerExtraAbilityTier = extraAbilityTier;

// Phainon.whenFightEnds:243-245（想回滚）
setAttackEnhancePercent(getAttackEnhancePercent() - extraAbilityTier * 0.5 + formerExtraAbilityTier * 0.5);
extraAbilityTier = 0; formerExtraAbilityTier = 0;
```

由于 `updateSelf` 每回合都把 `formerExtraAbilityTier` 同步成 `extraAbilityTier`，到 `whenFightEnds` 时两者**必然相等** → 那行加减等于 0，**什么都没回滚**；紧接着两个计数被清零，残留的 +50%（第 1 次觉醒）/**+100%**（第 2 次）**再也无法被减去**。

**(b) `coreflame` 战后归零，第二局白厄手里是 0 火种**

```java
// Phainon.whenFightEnds:246
coreflame = 0;
```

而构造器里初值是 15（`:102`），本该由 `listen2` 的"开战 +1"补——但 `listen2` 是死代码（§6.4）。**第二场战斗开始白厄放不出大招**（需要 12）。

**(c) 死亡锁血路径会让终结流程跑第二次 → `extraTurns` 变成 −1，反而加伤**

觉醒期间受到的致命伤会走 `Phainon.damageModify` 的锁血分支，往当前回合塞一个 `LastAttack`；而 `LastAttack.comeToEffect` 末尾会 `EventBus.post(new AwakenEndEvent(...))` —— **觉醒提前结束，但 `UltimateAttack` 已经排进时间轴的 8 个 `isExtra` 条目不会撤销**。白厄照样白拿剩余额外行动，并在第 8 条再次执行终结流程，于是 `extraTurns` 被减到 **−1**。而：

```java
// LastAttack.java:28
this.setAtkMagnification(getAtkMagnification() * (1 - ((Phainon) user).getExtraTurns() * 0.125));
```

`(1 - (-1) * 0.125) = 1.125` → **多 12.5% 伤害**。同一 `AwakenEndEvent` 被 post 两次。

**(d) `damageAbsorbedPercent` 的 +75% 不在收尾回滚**

`whenFightEnds:250` 与 `AwakeEndListener:23` 都只把 `absorbDamage` 置 `false`，唯一真正回滚 `damageAbsorbedPercent` 的地方是 `Counterattack:33-36`。若觉醒在"一次反击都没发生"的情况下结束，**+75% 减伤会一直挂到战斗结束**（配合 §4.10(b) 的叠加问题）。

**(e) `extraTurns` 不在 `whenFightEnds` 里复位**

只依赖 `AwakeEndListener:35`。若觉醒已因别的原因结束而 `extraTurns` 仍非 0，就会带着旧值进入下一场。

### 6.10 一次 `DamageEvent` 都发不出去，但"燃点监听器"每次开大都注册

```java
// actorLiXiaoYanSkills/UltimateAttack.java:48-51
MemorizedHp memorizedHp = new MemorizedHp();
memorizedHp.setLiXiaoYanEventListener(new DamageEventListener());
EventBus.register(memorizedHp.getLiXiaoYanEventListener());
user.addEffect(memorizedHp.setOrigin("self"));
```

`EventBus.register` 发生在 `addEffect` **之前**。而 `LivingThing.addEffect` 走 `Effect.equals`（比 `getClass()` + `isInfinity()` + `id` + `origin`）匹配已有同 id 效果——若身上已有 `MemorizedHp`，只会**延长持续时间并丢弃新实例**，那个刚注册的监听器就**永远不会被注销**（`MemorizedHp.whenLastTimeEnd` 只注销自己持有的那一个）。每次开大泄漏一个。

叠加上 §4.1 的 `return`（`MemorizedHp` 的 `lastTime` 永不递减），这个泄漏是**无上限**的。修 §4.1 与 §4.9 时应把这里的注册挪到 `addEffect` 之后，并给 `EventBus` 加"同名订阅者去重"。

### 6.11 内容层的死代码清单

| 项 | 状态 |
|---|---|
| `AttackEnhance`、`CriticalRateEnhanceEffect`、`DefenseEnhanceEffect`、`HpEnhanceEffect`、`IgnoreDefenceEffect` | **从未被实例化**（已全仓 grep：只有它们自己的 `copy()` 里出现 `new`）→ 因此 `DamageCalculate:71-78` 的"无视防御"钩子**永不可达** |
| `World.getEffectList()` | **无任何消费者** → 效果注册表是死数据；`OfficialGameContent` 注册的 4 个效果模板永不参与战斗 |
| `Effect.copy()` | 全仓**零调用点** → 基类"子类必须重写"的契约是死合同（而 `LivingThing:121` 对效果列表做的是**浅拷贝**，副本与模板共享同一个 `Effect` 实例） |
| `EffectTags.SKIP_ACTION` / `CAUSE_DAMAGE` | **零引用** |
| `EffectTags.POSITIVE` | 官方 10 个效果都 `add()` 了，但**无任何代码读取**（纯装饰） |
| `EffectTags.INFINITE` | 只有 `isInfinity()` 读，影响 `equals/hashCode` 与 §4.1 的早退分支；**官方无任何效果设置它** |
| `LivingThing.extraDamage`（public 字段，参与伤害公式） | **无任何写入点**，永远是 0 |
| `SkipTurn.java:10` | `System.out.printf(user.getName() + "跳过回合")` —— 用 `printf` 输出拼接好的字符串，玩家名里含 `%` 会抛 `UnknownFormatConversionException`；且不做任何状态变更 |

### 6.12 官方内容注册的幂等性

- **同一 Mod 实例重复 `registerItself()` → 幂等**：`Mod.java:215-237` 用 `World.*List().contains(m)` 去重，同一实例 `Thing.equals`（比 uuid）命中。
- **重复 `new OfficialGameContent()` → 不幂等**：每次都是新对象（新 uuid）但 **id 完全相同**，`contains` 因身份不等而全部判为新内容 → 注册表出现重复条目，**选人列表里会出现两份同名角色**。`Mod.removeEntity/removeItem/removeEffect` 也按身份删除，清不掉。
- **id 前缀不可重入**：`Mod.addEntity:159-164` 等方法没有"已加前缀"判断；若 `GameStartEvent` 被 post 两次，id 会变成 `game_official_content:game_official_content:xxx`，并**直接改变 `Effect.equals` 的匹配键**（id/origin）与物品堆叠判定。

---

## 七、内容层数值观察（官方角色/怪物）

等级成长公式（`Entity.setLevel` / `LivingThing` 完整构造器）统一为：

```
HP     = 200 + (等级-1) × hpGrow
防御   = 200 + (等级-1) × dfkGrow
攻击   = 110 + (等级-1) × atkGrow
法力   = 主元素 200 + grow×(等级-1)，其余 20 + grow×(等级-1)
每回合回蓝 = 等级/100 × grow + 100（主元素额外 + 等级）
```

官方内容注册时统一取 **125 级**（`OfficialGameContent`），此时各角色（数值由上述公式直接算出）：

| 实体 | 元素 | hpGrow | atkGrow | dfkGrow | speed | 125 级 HP | 125 级 攻击 | 125 级 防御 |
|---|---|---|---|---|---|---|---|---|
| `PlayerOne`（玩家一） | 土 | 36 | 29 | 5 | 120 | 4664 | 3706 | 820 |
| `ActorLiXiaoYan`（李晓焰） | 火 | 58 | 22 | 3 | 120 | 7392 | 2838 | **572** |
| `Phainon`（白厄） | 火 | 29 | 40 | 25 | 120 | 3796 | **5060** | 3300 |
| `CommonInsect`（150 级） | 金 | 30 | 5 | 9 | 90 | 4670 | 855 | 1541 |
| `IceInsect`（150 级） | 水 | 30 | 5 | 9 | 120 | 4670 | 855 | 1541 |
| `InsectBoss`（150 级） | 金 | **400** | 7 | 20 | 110 | **59800** | 1153 | 3180 |

可以看出的数值特征：

- **攻击成长远高于防御成长**（Phainon 40 vs 25，PlayerOne 29 vs 5，LiXiaoYan 22 vs 3）。伤害公式里 `atkMagnification × 攻击` 与 `hpMagnification × 当前HP` 是并列项，配合 `(等级×10+200)/(等级×10+200+防御)` 的防御衰减项——125 级时该系数约为 `1450/(1450+3300) ≈ 0.31`，**防御的边际收益偏低**，导致"堆攻击"几乎总是最优解。
- **角色定位差异靠"基数"而非"机制"体现**：白厄把 `hpGrow` 压到 29（几乎最低）来换取 `atkGrow = 40` 和 `dfkGrow = 25`；李晓焰 `hpGrow = 58`（最高）但 `dfkGrow = 3` 近乎裸奔，靠 `MemorizedHp` 锁血兜底——**这个兜底目前是坏的**（见 §4.9），所以李晓焰实战远弱于面板。
- 虫皇（`InsectBoss`）的 `hpGrow = 400` 是断崖式的（其他怪物 30，白厄 29）。150 级 = 59800 HP，且 `InsectBossSkillSummonEnemy` 描述里写明"分裂出普通虫子，**无上限**"——AI 只要加权到该技能就会持续铺场。这是目前最可能触发 §4.2 爆栈与超长战斗的对手。
- 每个 125 级角色的主元素法力上限是 `200 + 20×124 = 2680`，而李晓焰大招消耗 300 火 → **每局开场就能放 8 次大招**（还有每回合 `等级/100×20 + 100 + 125 ≈ 250` 的回复，即每回合回满近 1/10）。资源约束实际上不存在，"法力管理"这一层的设计意图没有兑现。

结论：**五行/法力这一层目前在数值上是被架空的**——既没有形成资源压力，元素克制也只体现为"取哪一套抗性/增伤/穿透字段"，没有真正的相生相克（相生相克只体现在 `initialMana` 里那套 20/10/4/1 的成长系数分配上）。

另外注意 `RestorationHealthSkill(0.1, 0.9, 0, 3, 90)`：`neededManaScale = 90` 是**硬编码在构造调用里的**，而回血量 `0.1×当前HP + 0.9×攻击` 在 125 级约 `839`。PlayerOne 的主元素上限 2680，配合 §4.7 的"`canUse` 里扣蓝"问题，这个技能本身就是**把玩家卡死循环的主要来源之一**（一次判定扣 90，来回几次就扣光）。

### 7.1 三个最具破坏性的数值问题

**(a) 冰虫的 `Freeze` 对玩家是一击必杀**

`Freeze` 的倍率是 **7.5 × 攻击**，而它只是一个"冰冻 1 回合"的控制技能。150 级冰虫攻击 855 → 基础 `7.5 × 855 = 6412`，代入防御衰减后：

| 目标 | 血量 | 无增伤时 | 落在自身 +200% 增伤窗口内 |
|---|---|---|---|
| PlayerOne | 4664 | 4326 | **12978** |
| 李晓焰 | 7392 | 4798 | **14394** |
| 白厄 | 3796 | 2180 | **6541** |

**全部超过自身血量。** 对比一下：虫皇的全部技能单次只有 1176~2588。也就是说**场上最强的一击来自一只小怪的控制技能**，而玩家的普攻倍率只有 1.0~4.0。`Freeze` 同时还带 `aims = 2` 与 2 回合冷却。

**(b) 暴击系统整体是关着的**

- **没有任何实体设置基础暴击率**（`facSetCriticalRATE` / `setGetCriticalRATE` 零调用）→ `DamageCalculate:52` 的 `Math.random() <= criticalRate` 实际是 `<= 0`，**永远不会暴击**。
- `CriticalRateEnhanceEffect` **从未被实例化**（见 §6.11）。
- 唯一被用到的 `CriticalDMGEnhanceEffect(0.3, 3)`（白厄被选为目标时挂的）因此**纯空转**。
- 再加上 §4.5 的 `getCriticalDMG()` 取错字段 → 这一整条属性链事实上不存在。

**(c) "200% 增伤"其实每次普攻都在吃，纸面倍率与实战脱节 3~4 倍**

```java
// universalSkill/CommonAttack.java:31
user.addEffect(new DamageEnhanceEffect(1, 1).setOrigin("commonAttack"));
```

`DamageEnhanceEffect(level, lastTime)` 的构造器里 `enhanceN = level * 1.0 + 1.0`（`DamageEnhanceEffect.java:39`），所以 `level = 1` 就是 **+200% 增伤**；`GunShoot` 同理。而这个效果是在**造成伤害之前**给自己挂上的 —— 于是**同一次攻击就已经吃到 3 倍**。

所有"纸面倍率"都要乘 3~4 倍来看：

| 技能 | 纸面 | 实战（含自加增伤） |
|---|---|---|
| PlayerOne 枪射击（7.5×3706） | 27795 | ≈ **24373** |
| 白厄普攻（1×5070，自带 +200%） | 5070 | ≈ 5240 |
| 白厄战技（3×5070，**无**自带增伤） | 15210 | ≈ 5240 —— **与普攻完全相等** |
| 白厄最后一击（13×5070） | 65910 | ≈ **22705（全体）** |
| 李晓焰终结技（4×当前HP×1.12） | — | ≈ **11408 × 3 目标** |

"白厄的战技和普攻伤害一模一样"就是直接的副作用：**倍率差 3 倍，但因为普攻自带 +200%，两者实际持平**。这说明"给普攻附赠增伤"这个设计正在系统性地压平所有倍率差异。

### 7.2 虫皇与自己的描述不符

`InsectBoss` 有 **59800 血却只有 1153 攻击**，玩家 3~5 回合就能打死它；描述里"分裂出普通虫子，无上限"的招牌机制**永远来不及生效**。它的自愈 `HealthRestoreEffect(2, 1)` 只有 210/次。而它一旦真的拖住了（召唤物是 **150 级满血**、4670 血 / 1541 防），又会滚雪球到 §4.2 的爆栈区间——**同一只 Boss 要么太脆、要么太拖，没有中间状态**。

### 7.3 元素克制是单边的

官方怪物全家对 **土（DIRT）抗性极高**（0.8 / 0.2 / 0.3）、对 **火（FIRE）为 0 / −0.2 / −0.1**。于是：

- PlayerOne（土）被**系统性压制**；李晓焰 / 白厄（火）严格更优。
- 三个玩家的抗性都投在火/土上，而怪物打的是金/水（玩家金抗、水抗**全是 0**）→ **玩家的抗性数值完全无用**。

另外还有个隐藏难度旋钮：**怪物 150 级 vs 玩家 125 级**，直接换来约 17% 的防御系数优势，README 里没有任何说明。

---

## 八、模组系统评价

约定清晰、错误提示友好（缺 `main.json`、缺 `code/`、编译失败分别给不同信息），编译诊断会逐条打印行号与源码名——**对纯 Java 文字游戏来说这是很实用的方案**。下面按严重度列出核实过的问题。

### 8.1 会让游戏直接崩的（模组作者视角完全不可控）

**(a) 只 `catch (Exception)`、不 `catch (Error)`** —— 一个坏模组能把游戏启动搞崩，而不是"跳过该模组"。

```java
// ModLoader.java:115 / :138 —— initialize=true
Class<?> mainClass = Class.forName(modInfo.getMainClass(), true, classLoader);
...
} catch (Exception e) { ... }        // :148 只接 Exception
```

`Class.forName(..., true, ...)` 会**立即执行静态初始化**。模组的 `<clinit>` 一旦抛异常，JVM 包装成 `ExceptionInInitializerError`（属于 `Error`，不是 `Exception`）；紧随其后的 `NoClassDefFoundError` 同样不是 `Exception`。而 `ModLoader.java:45` 的外层循环和 `GameMain.gameInitialize()`（`GameMain.java:30-44`）**都没有兜底**。只有 `:140` 的预加载 catch 了 `ClassNotFoundException`。

**结论**：主类静态块里一个 `throw`、一次除零、一个 `null` 解引用 → 游戏启动即崩，栈里只有一句"加载失败"。这是模组系统最该修的一条。

**(b) `GameStartEventListener` 用 `return` 而非 `continue`**

```java
// GameStartEventListener.java:18-20
if (m == null) {
    return;        // ← 一个 null 会让其后所有模组都不加载
}
```

模组加载顺序靠 `World.getModList()` 的插入顺序，所以这是"前面一个模组坏了，后面全部静默不生效"。

**(c) `invokeWhenLoaded()` 抛异常 → 启动崩溃**

`EventBus.EventHandler.accept`（`:92-97`）把反射异常包成 `RuntimeException` 再抛，`GameStartEvent` 的 `post` 没有任何 try/catch。模组作者在 `invokeWhenLoaded()` 里写错一行，整个游戏起不来。

### 8.2 资源与运行时问题

1. **类加载器被提前关闭**：`ModLoader.java:110-113` 用 try-with-resources，出块即 `close()`，而实例被 `World` 长期持有。现在只靠 `:123-143` 的"全量预加载所有 `.class`"兜底才能跑；`close()` 之后任何**新的** `loadClass`（含运行期反射、`getResource`、`ServiceLoader`）都可能失败。建议让 loader 常驻（存进 `Mod` 实例）。
2. **`Files.walk` 未关闭**（`:125-143`）：`Stream` 持有目录句柄，靠 GC 收；应放进 try-with-resources。
3. **`StandardJavaFileManager` 无 try/finally**（`:185` vs `:200-204`）：`task.call()` 抛异常时泄漏，Windows 上可能一直锁着 classpath 里的 jar。
4. **`deleteDirectory` 忽略返回值**（`:226`、`:230`）：bin 删不干净 → 紧随其后的 `mkdirs()` 失败 → 误报"无法创建 bin 目录"并**静默跳过该模组**。另外 `listFiles()` 会跟随 junction/符号链接，对 `bin/` 内的链接会**递归删掉链接目标**（危险）。
5. **每次启动全量重编译，不复用旧 `.class`**（`:85-107`），且**先删 `bin/` 再判断能不能编译**（`:93-96` → `:103-107`、`:179-183`）：没有缓存、没有回退。

   > **实测证据**：`mods/*/bin/*.class` 三个文件的 mtime 全是 `2026/8/13 20:41:34`，`../logs/latest.log` 是 `20:41:35` —— 同一次运行；class 文件头 `major = 69`（Java 25）。确实是"每次启动重编译"，复用不了。
   >
   > **好消息（已核实，推翻我先前的担心）**：官方 jpackage 产物**带编译器**。`../build-output/FightGameReforged/runtime/release` 显示 `JAVA_VERSION="25.0.1"`，且 `MODULES` 含 `java.compiler` **与 `jdk.compiler`**。所以"发布的 exe 版加载不了模组"**不成立**，`ToolProvider.getSystemJavaCompiler()` 在打包版里能拿到编译器。
   >
   > **仍然存在的风险**：若将来用 `jlink` 裁一个不含 `jdk.compiler` 的 runtime，或把游戏放进只读目录，模组会**永久不可用**——而那时上次能用的 `.class` 已经被删掉了。建议改成"先编译到临时目录，成功后再替换 `bin/`"。

6. **硬编码 `"./mods"`**（`:25`）：取决于进程工作目录。从别处启动 exe 会在错误位置新建一个空 `../mods` 并"成功加载 0 个模组"，只打印"mods目录创建: 成功"。`../build-output/FightGameReforged/mods` 正是一个空目录——因为路径是相对的，**`../mods` 必须紧邻 exe 放置**。
7. **模组加载失败不进日志**：`ModLoader` 全程 `System.out/err`，不接 `LogWriter`，`logs/*.log` 里查不到任何加载失败信息（实测 `latest.log` 只有 37 字节 = 一行运行时间 + 一行用户名）。
8. **`-cp` 取 `System.getProperty("java.class.path")`**（`:190`）：fat jar / jpackage 形态下该值包含 `FightGameReforged.jar`（jpackage 经 `app.mainjar` 设置），所以模组能编译到游戏类、运行期也能经父加载器解析——**这一条实测没发现问题**，但换构建方式（如模块化运行）时需重新确认。

### 8.3 编码：两处都是平台默认字符集

| 位置 | 问题 |
|---|---|
| `JSONHelper.java:17` | `new FileReader(file)` —— **平台默认字符集**（中文 Windows = GBK）。`main.json` 里的中文（如 `"name": "抽象启动词"`）在 UTF-8 机器上会乱码。且 `FileReader` 不剥 BOM，带 BOM 的 `main.json` 会让 `JSONTokener` 直接解析失败。 |
| `ModLoader.java:163` | 读 `.java` 显式指定 UTF-8（**这点是对的**），但 GBK 源码会抛 `MalformedInputException`，被 `:168` 吞掉并跳过该文件，随后编译器报"找不到符号"——**误导性极强**。 |

顺带：`JSONHelper` 拼行时 `content.append(line)` 丢掉了换行符（JSON 里字符串字面量若有换行会出错，但正常 JSON 无碍）。

### 8.4 一致性与陷阱

1. **`Mod.getClassByName` 在 `Mod(String)` 构造的模组上必 NPE**（`Mod.java:82` 用 `modInformation.getName()`，而该构造器没设 `modInformation`）。
2. **`Mod.java:42` 和 `:51` 的 Javadoc 是错的**：写"此构造器不会为内容自动添加 `MOD_ID` 前缀"——前缀只由 `addXxx()` 里的 `MOD_ID` 字段决定，与用哪个构造器无关。
3. **`addXxx()` 不幂等**：同一对象 `add` 两次得到 `"mod:mod:id"`。
4. **绕过 `addXxx()` 直接 `World.addItem(x)`**：`registerItself()` 仍会把它注册进 World，但**没有前缀** → id 与官方内容撞车。
5. **`removeItem/removeEntity/removeEffect` 只删模组自己的 List，不从 `World` 注销**（`:150-152`、`:171-173`、`:191-196`）。
6. **`registerItself()` 的去重靠 `List.contains` → `Thing.equals` 比 UUID**（`Thing.java:31/47/55/162-166`）：只对"同一个实例"成立，新实例一律重复注册。
7. **`modClasses` 预加载会把游戏自己的类塞进去**：`ModLoader.java:138` 用 `Class.forName(className, true, classLoader)`，父加载器优先委托——模组若声明了与游戏同名的类（例如 `cn.gfhnv.game.world.World`），拿到的是**游戏那份 Class**，模组无法遮蔽游戏类。
8. **模组不能带资源文件或第三方 jar**：classloader 的 URL 只有 `bin/`（`:111`），而 `bin/` 每次清空；`collectJavaFiles` 只看 `.java`（`:161`），jar 不会被收集。`code/` 既不进编译 classpath 也不进运行 classpath。
9. **`OfficialModInformation.java:7`** 的 `mainClass` 填的是简单名 `"OfficialGameContent"` 而非全限定名（该字段不参与加载，仅展示，但演示了错误示范）。
10. **两个示例模组的主类全限定名完全相同**（都是 `com.gfhnv.mods.mainClass`），靠"每模组独立 URLClassLoader"才没冲突——**这个隐式约定没有写在文档里**，模组作者很容易以为可以互相 import。

### 8.5 安全（必须写进 README）

**完全无沙箱**：`../mods` 下的 `.java` 在游戏内编译后，以 `initialize=true` 在**同一 JVM、同一权限**下立即执行。模组可以 `Runtime.exec`、任意读写删文件、联网、`System.exit`、起无限线程、在静态块里死循环挂起（加载器无超时）。

- **Java 25 已永久禁用 `SecurityManager`（JEP 486）**，所以"用策略文件隔离"这条路在 JDK 25 上是堵死的。要真隔离只能上独立进程。
- 模组可反射访问游戏私有状态（`EventBus` 自己就在 `:23` 调 `setAccessible(true)`），可清空/篡改 `World` 静态注册表。
- **模组可以用与官方相同的 `MOD_ID`**（`"game_official_content"`）注册伪内容 → 内容欺骗、`TagConfig.json` 键劫持（`ConfigLoader` 是按 id 查表的）。
- 无签名、无哈希、无来源校验；`main.json` 的 `name/author/version` 纯展示。
- 唯一正面点：`bin/` 里的 `.class` **不会**被直接信任加载（每次都先删再编译），源码是唯一信任源；副作用是"只发 `.class` 不发源码"的模组完全用不了。

**建议在 README 里明确写一句**："安装模组等于授予该模组与游戏同等的权限，请只加载你信任的源码。"

### 8.6 类隔离与线程安全（潜在）

- 每模组一个 loader、父优先委托，当前没问题；但 `World`、`EventBus.handlers`、`Mod` 的内部 List 全是无同步的静态/可变集合。当前单线程（`GameMain.java:32`）所以只是潜在问题。
- `GameStartEvent.getMods()`、`World.getModList()/getItemList()`（`:118-120`、`:177-179`）**返回活动列表本身**，不是防御性拷贝，模组可随时改全局注册表。

### 8.7 `../.gitignore` 观察

`../.gitignore` 里有两行很说明作者意图的规则：

```
./ANALYSIS.md
/ANALYSIS.md
```

即 `ANALYSIS.md` 被**刻意排除在版本控制之外**（分析报告不入库）。而本次生成的 `ANALYSIS-2026-08.md` 不在该规则内 —— 如果你想保持一致，需要自己补一行或改名。另外 `/mods/*/bin/`、`/lib/`、`/logs/`、`../build-output`、`../build-input`、`.env`、`*.key` 都已忽略，这部分是合理的。

---

## 九、与仓库内既有两份报告的差异（纠错）
| 既有说法 | 当前源码实际情况 |
|---|---|
| `ProjectStatus.txt`：「`ThinkingController` 决策框架注释丰富但实现未完成，所有 NPC 实际走随机 AI」 | **已不准确**。`ThinkingControllerAI` 是完整 Utility AI 实现；`LivingThing` 复制构造器也会按控制器类型重建它。 |
| `ANALYSIS.md`：「`EffectEventListener` 无限效果只生效不减持续；持续时间耗尽触发 `whenLastTimeEnd`」 | **漏了最关键的一句**：第 20 行有 `return`，导致上述逻辑**只对效果列表的第一个元素成立**。 |
| `ANALYSIS.md`：「`Skill` 复制构造器复制……AI 标签」类描述 | 实际 `Skill(Skill)` **没有复制 `tags`**。 |
| `ANALYSIS.md`/`ProjectStatus.txt` 未提 | `getCriticalDMG()` 取了 `criticalDMGEnhancePercent` 两次（应为 `Amount`）。 |
| `ProjectStatus.txt`：「`LivingThing` 复制构造器 tags 判断条件疑似写反(我刚修复)」 | 当前代码 `if (!other.getTags().isEmpty())` 已正确。 |
| 两份旧报告都称物理系统"预留能力" | 更准确：**无发布者，永不执行**。 |
| 两份旧报告都把模组系统整体评为"亮点/实用" | 机制描述没错，但**没有查出 §8.1 的 4 个崩溃级缺陷**（不接 `Error`、`return` 当 `continue`、`invokeWhenLoaded` 异常无兜底、loader 提前 close）。 |
| 两份旧报告都未提"模板 → 副本"的复制完整性 | 这是本次复核**最重要的新发现**：`LivingThing` 复制构造器漏拷 `modifyDamage` 等一批字段，且玩家侧共享技能对象 → 三个角色的核心机制在真实对局中失真（§4.11 / §6）。 |

两份旧报告对架构（事件总线、时间轴、模组、五行）的描述与当前源码一致，可以继续参考。

---

## 十、总体评价

**做得好的地方**

- 事件总线 + 行动时间轴 + 编译式模组加载，三个"架构级"设计**都是真的落地了**，不是画饼。100 行以内解决战斗循环骨架，对自学者来说水平相当不错。
- `ActionSignal` 五态 + 首/末动作列表这套表达力，足以自然实现"额外回合 / 跳过 / 不排新回合 / 变身期间插动作"，比常见的"每人轮流点一下"高一个层次。
- `BigDecimal` 处理行动条避免浮点排序抖动，是个有意识的正确选择。
- `ThinkingControllerAI` 的"权重 → 情境修正 → 组合评分 → 成本惩罚"链路完整，且评分项有物理学味道（目标血越低越优先、可击杀加分）。
- 代码注释里能看到作者自己写下的设计意图与"这里没写完"的诚实标注，对后续维护是加分项。
- 白厄的觉醒状态机设计得相当有想法（8 个额外回合 + 毁伤计数 + 弑魂焚诏的"让敌人立即行动换取反击"），实现虽有漏洞，但**机制创意本身是亮点**。

**主要风险**

1. **效果系统实际上只结算第一个效果**（§4.1）——会直接改变游戏行为，优先级最高。
2. **`copy()` 契约只实现了七成**（§4.11）——三个角色的核心机制（李晓焰锁血/免死、技能冷却隔离、自定义道具）都在真实对局中失真，而且症状分散、极难定位。
3. **递归驱动回合**（§4.2）——把玩法规模锁死在一个较低的回合上限内。
4. **依赖静态 `presentTurn` / `presentTurn` 式隐式上下文的白厄觉醒线 + 静态 `isListenerRegister`**（§4.3 / §6.7）——最复杂的内容却建在最不稳的地基上。
5. **`Skill` 双 `canUse/use` 语义分叉 + `copy()` 丢 `tags` + `canUse(fight,user,null)` + 无重置的 `extraDamage`**（§4.4 / §4.8）——模组作者踩坑概率极高，症状是"AI 变傻""技能莫名多冷却一回合""打敌人反而加血"这类难查的问题。
6. **数值层已经和设计意图脱节**（§7）：暴击系统整体关闭、冰虫控制技能一击必杀、普攻自带 +200% 增伤压平了所有倍率差异、元素克制是单边的。
7. **`LivingThing` 1840 行单类**：数值字段 ~120 个、`manas` 五行初始化 5 段复制粘贴（`initialMana` 里每个 case 重复 5 次几乎相同的 `new Mana(...)`）、`recoverManaEveryTurn` 用 10 个 `if` 模拟 switch。功能正确但可维护性差，且 `LivingThing(long speed)` 等"部分构造"路径会留下 `elementSort == null`、`manas` 为空的半成品对象（正是 §4.6 会踩的坑）。

**建议的修复顺序**

*第一梯队（一行改动、收益最大）*

1. `EffectEventListener:20` 的 `return` → `continue`
2. `LivingThing.makeDamage` 补 `EventBus.post(damageEvent)`（让 `DamageEvent` 监听器真正生效）
3. `RestorationHealthSkill.canUse` 去掉扣蓝副作用（否则玩家会被死循环卡住）
4. `AttackEnhance:44` 补 `isOn = true;`（一行）
5. `LivingThing.getCriticalDMG:1813` 末项改用 `criticalDMGEnhanceAmount`

*第二梯队（复制契约 / 状态污染）*

6. `LivingThing` 复制构造器补齐 `modifyDamage`、`showSpecialMes`、全部 `*Enhance*`、五元素穿透/增伤、`individualMultipleArea`；**`PlayerController`/`ThinkingControllerAI` 分支改成逐技能 `copy()`**（照抄 `UniversalController` 的复制构造器）
7. 给 `Skill.extraDamage` 加真正的"伤害计算后归零"；修 `actorLiXiaoYanSkills/CommonAttack:24-40` 的判据错位与 `PyrohemicPumping:29` 的整数除法
8. `Item.copy()` 改成抽象或抛异常，强制子类实现（修 `ANiceSword`）；`Skill.copy()` 补 `tags` 深拷贝
9. 把 `presentTurn` 从 `static` 改为显式传参（`ISpecialAction.execute` 已经带了 `fight` 和 `user`，再加个 `turn` 即可）；`Phainon.isListenerRegister` 去 `static`
10. 拆分"战斗结束清理"与"实体死亡清理"（§6.8）

*第三梯队（结构 / 工程）*

11. 模组系统的 4 个崩溃级修复（§8.1）：`catch (Throwable)` + 加载失败跳过而非崩溃；`return` → `continue`；`invokeWhenLoaded` 包 try/catch；loader 常驻不 close
12. 回合推进改迭代（消除爆栈）
13. `canUse/use` 冷却语义统一；把 `canUse(fight,user,null)` 换成真正的目标列表
14. `LivingThing` 拆分：把"五行/法力"、"增强字段"、"效果管理"抽成独立类，或至少用枚举 + 循环消重
15. 让 `TurnManager.init()` 自己 `clear()`；`World.things` 每局重置
16. `JSONHelper` 加 UTF-8 + 剥 BOM；`main.json` 校验给出可操作的错误信息
17. 数值返工：给实体设置基础暴击率、下调 `Freeze` 倍率、去掉普攻自带的 +200% 增伤、对齐玩家/怪物等级
18. 物理系统/`ActEvent`/`FixOrderController`/`PropertyConfig.json`：要么接线，要么删掉，避免"看起来有但从不生效"的误导
19. README 补三句：模组安装=授予同等权限；`../mods` 必须紧邻 exe；模组不能带资源/第三方 jar

*强烈建议加的一条工程实践*

> 写一个 **"模板 vs `copy()` 副本"的全字段比对**单元测试。本次复核里最严重、最难查的一类问题（§4.11 / §6.8 / §6.9）全部源于"复制不完整"，而这类问题靠人工 review 几乎不可能穷尽。

---

*本文档由 AI 生成，仅供参考。所有结论均已在源码中逐一核对（含行号引用）；如需更精确信息请以源码为准。*

