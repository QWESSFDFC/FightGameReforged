# 「实体属性拆分」可行性 / 实用性 / 维护性评估（2026-10-02 版）

> **这是什么**：对"把实体（`LivingThing`）的属性字段拆开"这件事的一次评估 ——
> 能不能做、值不值得做、做完以后是更好维护还是更糟。**本文没有改任何代码**，是纯评估。
>
> **谁要读**：想动 `src/cn/gfhnv/game/entity/`、想给实体加属性、或者（更常见）**下一个想"顺手重构一下"的 AI**。
> 读完这一份就够，不必再重新评估一轮。
>
> **一句话结论**：**技术上很好拆，值不值得拆取决于你要的是"文件变小"还是"复制/重置不再漏字段"** ——
> 后者不拆就能先拿到，前者要付出 `/data` 契约迁移的代价。

---

## 零、TL;DR

| 维度 | 判断 | 关键理由 |
|---|---|---|
| **可行性** | **高**（但有两处对外契约卡着） | 59 个字段全是 `private`，外部 460 个访问点**全部**走 getter/setter，11 个子类里**没有一个**直接读写字段 → 搬字段的编译面几乎为零。卡人的是 `/data` 的平铺键名与模组文档写死的构造器/setter 语义 |
| **实用性** | **中低**（对玩家零收益） | 当前**没有任何功能**被这个结构卡住。真实收益是"以后的改动更安全"，而收益的大头能靠一条自测先拿到（见第七节阶段 0） |
| **维护性** | **分水岭在"留不留现有 getter"** | 保留全部现有 public getter/setter 当唯一入口 → 净改善；顺手换成 `stats.getAttack()` → 净损失（460 个调用点 + 模组 + 文档全遭殃） |
| **建议** | **先做阶段 0（半天、零风险），阶段 1 可选，阶段 2 按需** | 四步各自自洽，任何一步做完觉得不值都可以停，不留半成品 |

**这不是新想法**：`ANALYSIS-2026-08.md` 第 14 条（该文件 `:952`）已经写过
「`LivingThing` 拆分：把"五行/法力"、"增强字段"、"效果管理"抽成独立类，或至少用枚举 + 循环消重」，
当时排在**第三梯队（结构 / 工程）＝ 最低优先级**；而当前基准 `PROJECT-ANALYSIS-2026-09.md` 的 §8.1
建议表里**没有接它，也没有给否决理由**。本文因此**不重复"要不要拆"**，只回答"现在值不值得、怎么做才不亏"。

---

## 一、现状体检（2026-10-02 实测，复现命令见附录 A）

### 1.1 规模数字

| 指标 | 数字 |
|---|---|
| `LivingThing.java` 总行数 | **2262** |
| 其中 javadoc / 注释行 | **1056（47%）** |
| 空行 | 205 |
| 真正的代码行 | **≈1001（44%）** |
| 方法总数（近似） | 193 |
| 其中 `get*/is*/set*/facSet*` 访问器 | **163（84%）** |
| 实例字段 | **59 个名字**（写在 28 条声明行里，多名字挤一行） |
| getter ≈70 / setter 58 / `facSet*` 33 | 见附录 A 口径 |
| 属性访问器调用点（`src` + `mods`，点号调用） | **460**（src 440 / mods 20） |
| 子类 | **11 个**（8 直接 + 3 间接，含模组 `DrunkenSwordsman`） |

**一句话**：这个类 84% 的方法是属性访问器，真正的行为只有约 30 个方法。
所谓"实体属性"= 59 个字段 + 约 130 个访问器 + 6 个手写公式。

> 口径提醒：`PROJECT-ANALYSIS-2026-09.md` §1.3 写 `LivingThing.java` 是 2180 行（2026-10-02 实测 **2262**）；
> 文件数在它自己内部就不一致 —— §1.1 说 200 个、§7.1 说 `check-sources.ps1` 输出 183 个，
> 而 2026-10-02 实测 `src` 下有 **201** 个 `.java`（另有 `mods` 12 个）。
> **引用任何数字之前先自己跑一遍附录 A。**

### 1.2 属性现在有四种形态

| 形态 | 内容 | 规模 |
|---|---|---|
| **纯标量** | `enhance`（全局增伤）、`defenseLoss`、`individualMultipleArea`、`description` | 少数几个 |
| **「基础 + 百分比 + 固定值」三件套** | `attack` / `defence` / `speed` / `hpMax` / `criticalDMG` / `getCriticalRATE` | 6 组 |
| **五行五元组** | 5 抗性 + 5 单元素穿透 + 5 元素增伤 + 5 法力成长 | **20 个字段**，访问器约 60 个 |
| **容器 / 引用** | `manas`、`entityEffectList`、`damageReductions`、`damageModifiers`、`controller` | 5 个 |

三件套的公式是**同一个式子手写 6 遍**（`LivingThing.java:1718 / 1644 / 2013 / 2029 / 2071 / 1391`）：

```java
public long getAttack() {
    return (long) (attack * (1 + attackEnhancePercent) + attackEnhanceAmount);
}
```

五行五元组的重复是最"体力活"的一块：`ManaGrow` 这一个词在文件里出现 **111 次，且全部在 `LivingThing` 内部**
（`src/cn/gfhnv/game/entity/LivingThing.java` 一家独占，外部只有自测提过一次）。

### 1.3 属性怎么进场：13 参数**位置**构造器

`LivingThing.java:233` 的构造器有 13 个参数，前 8 个是抗性/速度/等级/类型，**后 3 个名叫 `hp`/`atk`/`defence`，
实际却是成长系数**。官方内容这样调（`officialStuff/customEntity/monsters/InsectBoss.java:23`）：

```java
super("虫皇", "insectBoss", -0.1, 0.2, 0.8, 0.2, 0.3, 110, l, "insect", 4000, 7, 20, METAL);
```

`MODDING-GUIDE.md` 把它当成**对外 API** 写进了文档（`:163-167` 与 `:451` 各一份"参数顺序"），
模组 `mods/drunkenSword/code/com/gfhnv/mods/drunkenSword/DrunkenSwordsman.java:94` 就是这么调的。

### 1.4 属性怎么复制、怎么重置（痛点都在这两条上）

- **复制**：`copy()` 构造器手写约 30 行逐字段赋值（`LivingThing.java:154` 起）。
  文档已核实的**漏复制**清单：增强类字段、五元素穿透、五元素增伤、`individualMultipleArea`、`extraDamage`
  （`PROJECT-ANALYSIS-2026-09.md` §6.1 第 11 条）。
- **重置**：`whenFightEnds()` 只有 5 行（`LivingThing.java:1951-1960`）——复位回合、补满血、清效果、
  清技能冷却、回满蓝；**不清 `damageReductions`**（同文档 §6.4 N3），也不碰增强字段与五抗/穿透/增伤。
- **副作用不对称**：`getHp()` 返回原始值、`getHpMax()` 返回合并值，而 `setHp()` 会把值**夹到合并后的上限**
  并发 `HpLossEvent` / `HpRestorationEvent`（`LivingThing.java:2044-2065`）。

### 1.5 谁按名字摸属性：只有 `/data`

`DataBridge.convert`（`src/cn/gfhnv/game/data/DataBridge.java:189-201`）遍历父类链的
`getDeclaredFields()`，**按字段名平铺成键**；改名靠字段上的 `@DataField`（`:254-260`），
写回靠 `"set" + 字段名` 拼 setter（`:695-714`）；不在 `isDataObject` 白名单（`:209-217`）里的对象**整个跳过**。

---

## 二、拆分该用的刀口：按「生命周期」分三类，而不是按主题分

当前所有已知的"漏字段"缺陷，**全部落在下面三类的边界上**：

| 类别 | 内容 | 生命周期规则 | 今天靠什么保证 |
|---|---|---|---|
| **面板属性** | `hpMax` 基础值、`attack/defence/speed`、暴击、五抗、穿透、增伤、成长系数、`elementSort` | 配置出来，一局内几乎不变 | `copy()` 手工逐字段抄 |
| **临时状态** | 当前 `hp`、`Alive`、效果表、`manas`、`damageReductions`、`damageModifiers`、`defenseLoss`、`anticipating`、战斗上下文 | 每局/每回合变，**有重置义务** | `whenFightEnds()` 手工清 5 项 |
| **派生值** | `getAttack` / `getDefence` / `getSpeed` / `getHpMax` / `getCriticalDMG` / `getGetCriticalRATE` / `getDamageTakenMultiplier` | 现算，不存 | 6 个同构公式手写 |

**证据链**：`copy()` 漏字段（§6.1 第 11 条）、`whenFightEnds()` 不清减伤（§6.4 N3）、
效果列表浅拷贝（§6.2 第 11 条），以及 `ANALYSIS-2026-08.md:959-961` 那句
「本次复核里最严重、最难查的一类问题全部源于"复制不完整"」。

> **所以"拆分"真正的价值不是让文件变小，而是把生命周期规则从"靠记性"变成"靠结构 + 靠断言"。**

---

## 三、四种拆法逐个评

### A. 字段拆成组件类（`CombatStats` / `ElementProfile` / `Growth` / `DamageTaken` …）

| 维度 | 结论 |
|---|---|
| 可行性 | **高**。字段全 `private`、外部一律走 getter（460 个访问点里没有一个直接摸字段）、无子类碰字段 → 搬字段 + 保留转发 getter，编译面几乎为零 |
| 工作内容 | 机械改写 ≈1000 行代码里的 `this.attack` → `stats.attack`；`copy()` / `whenFightEnds()` 从"手写 30 行"变成"每个组件自己 copy / reset" |
| 实用性 | **中**。新增属性不会再漏 copy/reset；五行加第六个元素从"改 4 处 × 5"变成"改 1 处"；文件能瘦到约 1000 行 |
| 维护性 | **净改善**（前提：转发 getter 全部保留）。代价是调试要在 4~5 个文件间跳 |
| 代价 | ~~`/data` 键名会从 `hp` 变成 `stats.hp`~~ **可由"扁平化"豁免**（阶段 1 实测：`@DataFlatten` 让组件字段无前缀并进父标签，键名不变）；**组件类型必须加进 `isDataObject` 白名单**（`DataBridge.java:209-217`），否则整个组件在 `/data` 里**静默消失**（不报错，只是查不到）—— ✅ **阶段 1 实测：这条真的会咬人，且自测抓不到（见第七节阶段 1 的坑 ①）** |

### B. 基础值 + 增强值收进统一的属性对象（类似 Minecraft 的 AttributeMap）

| 维度 | 结论 |
|---|---|
| 可行性 | 中高，但它动的是**语义契约**，不只是结构 |
| 最有力的理由 | `MODDING-GUIDE.md:275-288` 已经写下「想让效果改属性，有两条正路（**别去改基础值**）…… `setAttack` / `setDefence` 这类改基础值的写法不要用」。**说明这个坑已经真实咬到模组作者了** —— 属性对象化之后，"别改基础值"从"文档劝告"变成"结构上做不到" |
| 顺带能治的病 | ① 读合并值 / 写基础值的不对称（`setHp` 还会发事件并夹上限）；② `Entity.setLevel()` 从父类伸手改子类属性（`src/cn/gfhnv/game/entity/Entity.java:208-218`）—— 这两条正好是属性对象的主场 |
| 实用性 | **四种里最高** |
| 维护性 | 净改善，但**必须保住写回语义**：`/data modify entity @s attack set X` 现在走 `setAttack`（写基础值），改完还得是基础值；`/data get entity @s hp` 仍应被夹到上限 |

### C. 只改 `/data` 的对外视图（Java 一个字不动，把平铺键改成嵌套分组）

技术可行性最高，**但收益最小、破坏最大**：

- 键名是你 2026-09 刚定下的对外 API（`notes_for_llm/70-DATA.md:21-22` 明确写"改名等于破坏 `/data` 脚本与将来的存档"），
  而且刚为"键名不合理"改过一轮（`getCriticalRATE` → `criticalRate`）；
- 真要做，得给 `DataBridge` 加"扁平化 / 分组前缀"机制（现在它只会按字段名平铺），
  并改掉自测里 **122 个 `/data` 断言点**（≈全部断言调用点的 27%）；
- 现有自测**硬编码**了这些键：`criticalRate`、`hpGrow`、`attackGrow`、`defenceGrow`、`metalManaGrow`，
  以及选择器 `nbt={hpGrow:58.0d}`（`src/cn/gfhnv/debug_tools/TestCommandSystem.java:1987 / 1996 / 2154-2160`）。

**结论：除非你确实想要"分组的 `/data` 视图"这个体验，否则不值。它和 A/B 是两件事，别捆在一起做。**

### D. 按职责拆文件（属性 / 伤害 / 效果 / 打印分开，字段仍留在实体上）

**这一条按字面做不到** —— Java 没有 partial class，一个类不能拆到多个文件。能落地的只有两条退路：

1. **字段搬进组件类** = 就是 A；
2. **行为抽成静态工具类**（`LivingThingPrinter.printAttackLine(...)`、`DamageApplier.apply(...)`），字段仍留在 `LivingThing` 上。

走第 2 条：可行性高、风险低（纯搬家），但收益也小 —— 只是文件短了，`copy()` / `whenFightEnds()` 该漏还是漏。

**建议把 D 理解成"瘦身整形"，排在 A 之后，不要单独做。**

---

## 四、可行性边界：三条硬约束 + 一条利好

1. **`/data` 契约**：反射按**字段**平铺 → 字段一搬键名就变；白名单漏了组件类型就静默消失
   （`DataBridge.java:189-201`、`:209-217`、`:243-260`）。
2. **模组契约**：13 参数位置构造器被 `MODDING-GUIDE.md:163-167` 与 `:451` 当成对外 API 写进文档，
   模组 `DrunkenSwordsman.java:94` 就是这么调的 → **构造器签名与 `setXxx` 名字不能动**
   （除非同时改模组 + 文档 + 自测）。
3. **自测是唯一安全网**：`TestCommandSystem.java` 实测 **2805 行**，
   `check(` 302 + `run(` 136 + `expectSyntaxError(` 11 个调用点（文档口径基线 **417/0**），
   其中三个 `/data` 测试方法内有 **122 个断言点（≈27%）**。重构期间这些必须同步改。
4. **利好：全项目没有存档 / 序列化**（`/data storage` 只在内存，实体存档是明确跳过的）
   → **没有"旧档读不出来"的历史包袱**，改内部结构不用做兼容层。
   这是这次拆分最大的可行性优势 —— 换成有存档的项目，A/B 的成本会翻倍。

> ⚠️ 文档里的行号已经漂了（`MODDING-GUIDE.md:163` 指向的 `LivingThing.java:189` 现在实际是 `:233`，
> `:1654` 现在实际是 `:1750` 附近）。**动手时以源码为准，别照文档行号改。**

---

## 五、实用性：收益 vs 成本

**收益（按真实性排序）**

1. 消灭"复制 / 重置漏字段"这一类 bug —— 有记录、且复发过；
2. 五行 / 多元素扩展从 O(5 处) 变 O(1)；
3. `LivingThing` 从 2262 行降到约 1000 行，**单次改动的上下文压力变小** ——
   对"AI 改这份代码"这个真实工作流是实打实的收益。

**成本**

1. 一次性大改的回归风险，而唯一的"手感 / 观感"验收人是用户；
2. `/data` 契约迁移（122 个断言点 + 文档回写）；
3. 模组契约对齐（`MODDING-GUIDE.md` 要重写一节）；
4. 间接层增加：现在全部状态在一个文件里一眼可见，拆完要在 4~5 个文件之间跳。

**判定：没有"非拆不可"的理由。** 但收益的第 1 条**不拆也能先拿到**（见阶段 0）。

---

## 六、维护性：什么时候变好、什么时候变糟

**变好的三个"必须"**

1. 必须保留**全部现有 public getter/setter** 作为唯一访问路径（460 个调用点 + 模组 + 文档都靠它）；
2. 必须让每个组件**自带 `copy()` / `reset()`**，否则只是把漏字段的地点从 1 处变成 5 处；
3. `/data` 键名迁移必须**一次做完**，不要半拆（半拆会导致同一个键两个来源）。

**变糟的三个"千万别"**

1. 顺手删 / 改 getter，让调用方去写 `stats.getAttack()`；
2. 顺手清理 `facSet*` —— 33 个方法全项目只有 3 处调用（都在 `src/cn/gfhnv/debug_tools/actionBarTest/TestMain.java`），
   看着像垃圾，**但它是公开 API 且文档里有**；
3. **拆结构的同时改数值** —— 出问题时分不清是哪边引起的。

---

## 七、建议路线（分四步，每步都能单独停）

### 阶段 0（半天，零风险，**建议先做这个**）：不拆任何东西，先给属性清单上锁 —— ✅ **2026-10-03 已完成**

- 用反射写一条自测：对 `LivingThing` 的每个字段断言"`copy()` 之后值相等"
  （已知漏掉的先列白名单，等于把 §6.1 第 11 条变成**看得见的 TODO**）；
- 再断言 `whenFightEnds()` 之后每个"临时状态"字段被复位；
- 这正是 `ANALYSIS-2026-08.md:959-961` 强烈建议、**但一直没做**的那条
  （已核对：现自测 13 个 `testXxx` 方法里没有 copy 契约测试）。

**产出**：以后新增字段漏复制 / 漏重置会被自测抓住。**这就是拆分收益的大头，成本几乎为零。**

#### 落地结果（2026-10-03 实测）

测试在 `TestCommandSystem#testLivingThingAttributeContract`（10 条断言，自测 441 → **451**）。
做法不是"逐字段手写断言"，而是**反射驱动**：

1. 扫 `LivingThing.class.getDeclaredFields()` 里的**标量**字段（`double/long/int/boolean/String/enum`，
   跳过 static）—— 实测 **51 个**；
2. 给每个标量填一个"和默认值不可能撞车"的探测值（`double→7.5`、`long→4321`、
   `String→"契约探针"`、`enum→constants[1]`），再 `copy()` 逐个比对。
   **探测值不能等于默认值**，否则漏复制的字段会因为"两边都是 0"而假通过；
3. 漏掉的字段要么被修好，要么必须写进 `knownNotCopied` 白名单 ——
   白名单本身也被断言"名字都真的存在"，字段改名后不会静默失效。

**实测结论：漏复制恰好 24 个，与 §1.4 的清单一个不多一个不少**：

```
extraDamage, individualMultipleArea,
metalPenetration, woodPenetration, waterPenetration, firePenetration, dirtPenetration,
metalDamageEnhance, woodDamageEnhance, waterDamageEnhance, fireDamageEnhance, dirtDamageEnhance,
attackEnhancePercent, defenceEnhancePercent, speedEnhancePercent, hpEnhancePercent,
criticalDMGEnhancePercent, criticalDMGEnhanceAmount,
criticalRateEnhancePercent, criticalRateEnhanceAmount,
attackEnhanceAmount, defenceEnhanceAmount, speedEnhanceAmount, hpEnhanceAmount
```

两条附带结论：

- `anticipating`（只读试算标志）是**刻意**不带过去的，单独列在 `notCopiedOnPurpose` 里断言
  "副本必须是干净的新个体"；
- `whenFightEnds()` 的五条复位都成立，**只有 `damageReductions` 不清**（§6.4 N3）——
  测试里专门写了一条"【已知缺口】"断言记录现状，阶段 2 治好后请把它翻成 `isEmpty()`。

> 这 24 个字段**还没有修**（阶段 0 的定位是"上锁"，不是"修"）。
> 修它 = 在 `LivingThing(LivingThing other)` 里补 24 行；要不要现在修见下方"待用户拍板"。

#### 用户 2026-10-03 的口径：这 24 个是「临时属性」，`copy()` 刻意不带

用户拍板：那 24 个算**临时属性**（一次性 / 战中的加成），**没有必要复制**。
所以上面那份清单从"待修的漏复制"**改判为"按设计不复制"**，断言文案也跟着改成
`不复制集合与『临时属性』清单完全一致`。

**但这个决定还带出第二半**：按本文第二节的生命周期分类，"临时"的另一半是**有重置义务**。
于是又补了一组断言（24 条前提 + 1 条结论），把 24 个临时属性逐个设成非默认值，
调 `whenFightEnds()`，再逐个检查是否回归默认 ——

> ⚠️ **实测：24 个一个都没被复位**（`whenFightEnds()` 只清 5 项，见 §1.4）——
> 与 §6.4 N3「不清 `damageReductions`」同一类缺口。**已按下面的分类修完。**

#### 按「谁在写」逐个查清的分类表（2026-10-03，已落地）

查法：对 24 个字段逐个搜 `setXxx(` / 直接赋值 / getter 调用点（`src` + `mods` 全量）。

| 类 | 字段 | 谁在写 | copy() | whenFightEnds() |
|---|---|---|---|---|
| **① 效果写的临时加成**（12） | `attack/defence/speed/hp/criticalDMG/criticalRate × EnhancePercent+EnhanceAmount` | 六个通用强化效果**成对加减**：`AttackEnhance:66-76`、`DefenseEnhanceEffect:82-91`、`SpeedEnhanceEffect:70-79`、`HpEnhanceEffect:70-80`、`CriticalDMGEnhanceEffect:71-80`、`CriticalRateEnhanceEffect:70-79`；模组 `Hangover:93-105`；白厄变身自己加减（`UltimateAttack:58-59` / `AwakeEndListener:19-20` / `Phainon:292,348`） | 不带 ✓ | **清零** ← 新 |
| **② 预留·未接线**（10） | 五元素 `*Penetration` + 五元素 `*DamageEnhance` | **全项目零写入点** —— 只被 `DamageCalculate:82-103` 读来加算，只有 `/data` 上帝模式能写 | 不带 ✓ | **清零** ← 新（对现状是 no-op，为将来接线铺路） |
| **③ 死字段**（1） | `extraDamage` | **零写入点**（public 也没人赋值）。活的那套是 `Skill#extraDamage`，**另一个字段** —— `ANALYSIS-2026-08.md:686` 早已记着"永远是 0" | 不带 ✓ | **清零** ← 新 |
| **④ 面板·派生**（1） | `individualMultipleArea` | **角色自己的构造器 / 派生算法**：`ActorLiXiaoYan:122`（按【燃点】算）、`:224`（燃点变化时同步） | **复制** ← 新 | **不清** |

**落地**：

- `LivingThing` 新增 `clearTemporaryAttributes()`（23 个字段），由 `whenFightEnds()` 调用；
- `LivingThing(LivingThing other)` 补上 `individualMultipleArea` ——
  此前只有 `ActorLiXiaoYan:107` 手工打补丁（`STATUS-2026-08-review2.md:136` 当时就警告过
  "将来新角色如果有构造期增益，每个都要记得补一句"，现在这条警告作废了）；
- 测试改成两张互补的表：`panelAttributes`（必须复制、不能清零）与
  `temporaryAttributes`（不能复制、必须清零），两边都断言。

**规则定死**：**不是面板属性的，一律清零；面板属性一律复制。** 新增字段时照着这条归类即可。

**基线**：自测 441 → 451（阶段 0 本体）→ 476（临时属性重置契约）→ **481**（分类落地）
→ **482**（后续零散用例）→ **483**（阶段 1：多一条"组件字段真的进了 `/data`"的守卫）
→ **531**（外部数据加载的阶段 0 + 阶段 1，见 `EXTERNAL-DATA-LOADING-2026-10.md`）
→ **533**（阶段 1 收尾：全局组三个字段并进组件）→ **542**（订正 9 个 Java 名 + 写回契约）。




### 阶段 1（1~2 天，低风险）：只拆五行五元组 —— ✅ **2026-10 已完成**

- 20 个字段、约 60 个访问器收进一个小类或 `EnumMap<ElementSort, …>`；
- 外部 getter 全部保留转发；`/data` 键名用 `@DataField` 或给 `DataBridge` 加"扁平化"支持保住；
- 顺带能治 `Entity.setLevel()` 反向伸手改子类属性的毛病。
- ⚠️ `ElementSort` 有 **6 个**值（多一个 `UNIVERSAL`，`src/cn/gfhnv/game/system/ElementSort.java:7`），
  而 `penetration`（全属性穿透）与 5 个单元素穿透是**相加**关系（`DamageCalculate.java:58, 83-103`）——
  收进同一张表时要把"全属性"这一档一起建模，别把它漏掉。

#### 落地结果（2026-10 实测）

**做了什么**（纯搬家，一个数值 / 公式 / 结算顺序都没动）：

| 文件 | 改动 |
|---|---|
| `src/cn/gfhnv/game/entity/ElementProfile.java` | **新增**。20 个显式命名字段 + 各自的 getter/setter + `copyFrom` / `resetTemporary` |
| `src/cn/gfhnv/game/data/DataFlatten.java` | **新增**。标记"把组件字段无前缀并进父复合标签"的注解 |
| `LivingThing.java` | 20 个字段删掉 → `@DataFlatten private final ElementProfile elements`；60 个访问器 + 5 个 `facSet*Resistance` 改成转发；复制构造器 / 13 参构造器 / `clearTemporaryAttributes()` / `initialMana()` 改走组件 |
| `DataBridge.java` | `convert` 认 `@DataFlatten`；`dataNames` 展开组件；`findField` → `lookup`（返回"字段 + 它挂在哪个对象上"）；`isDataObject` 收 `ElementProfile` |
| `TestCommandSystem.java` | 反射契约测试的"标量字段扫描"改成能下潜进组件（否则搬走的 20 个字段从扫描里静默消失，断言退化成假绿） |

**关键设计：只有"读"这一侧需要扁平化。** `@DataFlatten` 让组件的字段**无前缀**并进父复合标签，
键名与搬字段之前逐个字符一致（`fireResistance`、`metalManaGrow`……），**不是** `elements.fireResistance`。
写回侧本来就走 `"set" + Java 字段名`，而 `LivingThing` 保留了全部转发 setter —— 但**光靠这一条不够**，
见下面的坑 ②。

**坑（三个，都实测踩到了）**：

1. **组件类型必须进 `DataBridge#isDataObject` 白名单**（就是本文 §3.A 与 §4.1 预警过的那条）。
   漏了的话 `convert` 对这个字段返回 `null`，整个组件在 `/data` 里**静默消失** —— 不报错、只是查不到。
   ⚠️ 更阴的是：`dataNames()` 是照着"字段清单"算的，**加不加白名单它都列得出这 20 个名字**，
   所以只断言"数据名清单里有 `metalManaGrow`"是**抓不到**这个 bug 的（实测：`dataNames` 全绿、
   `/data` 里一个都没有）。现在自测多了一条**直接断言组件字段真的 dump 出来**的用例盯着它。
2. **写回侧不是"完全不用动"**：`apply` 是先按数据名找字段、再拿**目标对象的类**反射拼
   `"set" + Java 字段名` 的。字段搬进组件之后，`findField(LivingThing.class, "metalManaGrow")`
   找不到它（它现在声明在 `ElementProfile` 上），`/data merge entity @s {metalManaGrow:99}`
   会直接报"没有数据字段" —— 也就是说**光保留转发 setter 不够，字段根本找不到**。
   所以 `findField` 改成了 `lookup`：先找实体自己（含父类链），再下潜进 `@DataFlatten` 组件，
   返回 **`DataSlot(容器, 字段, 类型)`** —— 容器是实体本身时，转发 setter 照旧命中；
   容器是组件时，读写都落在组件上（组件的 setter / 裸写字段都能用）。
   **`/data` 的键名与写回语义都没变**，只是"字段该挂在哪个对象上"这一层被显式带出来了。
3. **反射契约测试会假绿**：`scalarFieldsOf(LivingThing.class)` 只看本类声明的字段，
   搬走 20 个之后它仍然"通过"（因为扫到的字段都复制对了），但**契约其实已经缩水**。
   现在扫描会下潜进 `@DataFlatten` 组件（实测仍然是 **51 个**标量字段，与拆分前一致），
   并且 `copy()` 的"不复制 23 个临时属性"断言在**组件字段上**仍然成立。

**回归证据（自带 JDK 实跑）**：

- `check-sources.ps1` → `CHECK OK`（203 个文件）；
- `javac` 编 `src` 全部 203 个文件 → **exit 0**；
- 自测 **483 / 0**（基线 482 → 483：多出来的那 1 条就是上面坑 ① 的守卫）；
- `/data get entity @s` 的键从 42 涨到 **62**（多出来的正是这 20 个五行键），
  选择器 `nbt={hpGrow:58.0d}` 照旧命中；
- `LivingThing.java` **2335 → 2317 行**（20 个字段 + 10 行手写清零 - 新增组件字段）。

**没做的**：`Entity.setLevel()` 那条反向依赖**没动**（它属于阶段 2 的属性对象化，阶段 1 只搬家）。

#### 阶段 1 收尾（2026-10-03）：`ElementProfile` → `AttributeProfile`，全局组三个字段并进组件

阶段 1 原本只搬了"五行五元组"（20 个字段）。这一轮把它补完：

| 文件 | 改动 |
|---|---|
| `src/cn/gfhnv/game/entity/AttributeProfile.java` | `ElementProfile` **改名**而来（`git mv` 语义：旧文件已删）。类 javadoc 分两组写清：**五行组**（5 抗性 / 5 穿透 / 5 元素增伤 / 5 法力成长）与 **全局组**（`penetration` 全属性穿透、`enhance` 全属性增伤、`criticalDMG` 暴击伤害） |
| `LivingThing.java` | 删掉 `criticalDMG` / `penetration` / `enhance` 三个字段（分别从"和 5 个抗性挤在同一行"、"和 5 个元素增伤挤在同一行"、单独一行里摘出来）；`@DataFlatten private final AttributeProfile attributes`；**全部 public getter/setter 一个没删**（`getCriticalDMG()` 那 6 个公式的唯一改动是"基础值从组件里取"） |
| `DataBridge.java` / `DataFlatten.java` / `DataKeys.java` | 类型名与 javadoc 跟着改 |
| `TestCommandSystem.java` | 契约测试的扫描范围断言里点名要求组件字段在扫描结果内（光断言"数量 ≥ 30"抓不住"组件没被下潜"这种假绿） |

**生命周期归属**（严格照第二节那张表）：

- `penetration` / `enhance` / `criticalDMG` 都是**面板属性** → 由 `AttributeProfile#copyFrom` 带过去，
  `resetTemporary()` **不**碰它们；
- `criticalDMG` 此前就已经被复制（`copy()` 里那行 `this.criticalDMG = other.criticalDMG`），
  `penetration` / `enhance` 同样 **`clearTemporaryAttributes()` 里原本就没有它们** ——
  所以这次搬家**没有改变任何一条生命周期行为**，只是把"谁复制"从实体挪进了组件；
- 那 10 个五元素穿透/增伤仍然走 `AttributeProfile#resetTemporary()`，**语义一个字没变**。

**`/data` 契约的验证方式**（铁律 ①：键名一个都不许变）：`/data get entity @s` 的实测输出里
`penetration` / `enhance` / `criticalDMG` **仍然是顶层键**（不是 `attributes.penetration`），
与阶段 1 的 20 个五行键同一条路径（`@DataFlatten`）保证；用例里也新加了一条断言钉住它。

**回归证据（自带 JDK 实跑，2026-10-03）**：`CHECK OK`（206 个文件）/ `javac` exit 0 /
自测 **533 / 0**（531 → 533：新增"扫描范围下潜进组件"与"全局组三个字段仍是顶层键"各 1 条）。

#### 阶段 2.5（2026-10-03）：订正 9 个不合理的 Java 名（`/data` 键名一个字没动）

用户要求"Java 名 == 数据名"，于是 `@DataField` 从"映射"退化成"重复劳动"、跟着删掉：

| 原 Java 名 | 新 Java 名 | 数据名（锁死不变） |
|---|---|---|
| `LivingThing.getCriticalRATE` / `getGetCriticalRATE()` / `setGetCriticalRATE()` / `facSetCriticalRATE` | `criticalRate` / `getCriticalRate()` / `setCriticalRate()` / `facSetCriticalRate` | `criticalRate` |
| `hpGrowNumber` + `get/set/facSet` | `hpGrow` + 同名 | `hpGrow` |
| `atkGrowNumber` | `attackGrow` | `attackGrow` |
| `dfkGrowNumber` | `defenceGrow` | `defenceGrow` |
| `{metal,wood,water,fire,dirt}ManaGrowNumber` | `…ManaGrow` | `…ManaGrow`（5 个） |
| `Alive` | `alive` | `alive` |

**唯一还需要 `@DataField` 的是 `entityEffectList` → `effects`**（它改的是名字而不是"订正"）。

**这一轮最值得记的一条坑**：`DataBridge#trySetter` 是拿 **Java 字段名**拼 setter 名的，
所以**字段改名时 setter 名必须跟着改** —— 漏改的话 setter 找不到，`/data merge|modify`
会**静默退化成裸写字段**（命令照旧报成功、值也照样变，只是钳制与副作用没了）。
为此加了两条自测：
① **扫每一个"能写"的标量字段，要求它挂着一个同名 setter**（只读的两个例外点名列出：
`ignitionMax` 是按当前血量现算的派生值、`lastIgnition` 是内部记账）；
② **哨兵断言**：先把 `enhance` 设成 `1234.5`，再让 `DataBridge#merge` 写 `{enhance:0.5}` ——
setter 生效时哨兵被整体覆盖，只裸写字段时哨兵还在。

**回归证据（自带 JDK 实跑，2026-10-03）**：`CHECK OK` / `javac` exit 0 / 自测 **542 / 0**
（533 → 542：哨兵断言、setter 全扫各 1 条，外加 6 个改名键的 `/data modify` 实测 1 条 ——
`hpGrow`/`attackGrow`/`defenceGrow`/`metalManaGrow`/`criticalRate`/`alive` 都写得进去、且落在正确的字段上），
且实测 dump 里 `criticalRate` / `alive` / `hpGrow` / `attackGrow` / `defenceGrow` / `metalManaGrow`
键名逐个未变（选择器 `nbt={hpGrow:58.0d}` 也照旧命中）。

### 阶段 2（2~3 天，中风险）：三件套收进属性对象

- 6 组「基础 + 百分比 + 固定值」；
- **验收判据写死**：`/data modify entity @s attack set X` 仍写基础值；
  `/data get entity @s hp` 仍被夹到上限；`getHpMax()` 的合并结果与现在完全一致。

### 阶段 3（按需）：行为抽取 + 瘦身

- 打印（`printAttackLine`）、伤害结算、减伤汇总抽成协作类；没有前置需求就别做。

**每步的验收**：`check-sources.ps1` → 自带 JDK 编译 → 跑自测（**当前基线 542/0**，改 `/data` 就会涨）→ **用户进游戏打一局**。
任何一步做完觉得不值，随时停；每一步都是自洽的，不留半成品。

---

## 八、与前序文档的关系（第 0 条闭环）

| 文档 | 关系 |
|---|---|
| `ANALYSIS-2026-08.md` `:952`（第 14 条） | **最早提出**拆 `LivingThing`，排在第三梯队（最低优先级） |
| `ANALYSIS-2026-08.md` `:959-961` | 建议加"模板 vs `copy()` 副本"的全字段比对测试 —— **至今未做**，本文把它提为阶段 0 |
| `PROJECT-ANALYSIS-2026-09.md` §6.1 第 11 条 / §6.4 N3 / §6.2 第 11 条 | 本文"生命周期三类"划分的证据来源（漏复制 / 不清减伤 / 浅拷贝） |
| `PROJECT-ANALYSIS-2026-09.md` §8.1 | **没有接第 14 条**，也没给否决理由；本文补上"该不该现在做"的判断 |
| `notes_for_llm/70-DATA.md` §5.10 | 决定 A/B 成本里必须算上 `/data` 键名迁移的那句"数据名是对外 API" |
| `MODDING-GUIDE.md` `:163-167` / `:275-288` | 决定"构造器与 setter 名字不能动"，同时是 B 的最强理由 |
| `NBT-AND-DATA-COMMAND-2026-09.md` | **无关**，本文不涉及 NBT / 存档（那边的可行性早已分析完，别重开） |

---

## 九、本文没做的事 / 不确定项

- **没有动任何代码**，没有改任何一个属性，也没有跑重构原型；
- "460 个调用点"是**点号调用**口径（`x.getHp(` 这种），包含 `this.xxx(` 的内部调用，
  **不含**方法声明行；换个口径数字会变，口径与命令见附录 A；
- `getter ≈70 / setter 58` 按行首正则统计，可能把少数泛型/数组签名算漏；
- **没有实跑过任何一局游戏**来验证"重构后数值不变" —— 这件事只有用户能做，
  而阶段 2 的验收判据就是为它准备的。

---

## 附录 A：本文数字怎么复现

> ⚠️ **先看这条坑（本文自己就踩了）**：中文 Windows 上 **Windows PowerShell 5 的 `Get-Content` 默认按 GBK 解码**，
> 会把"行尾中文字符"的尾字节和换行符一起吞掉 → **行数与匹配行数都会少算**。
> 实测代价：`TestCommandSystem.java` 被数成 2547 行（真实 **2805**）、`/data` 断言段被数成 73（真实 **122**）。
> **凡是"数行数 / 数匹配行"的活，一律用 .NET 的 `ReadAllLines` / `ReadAllText`（默认 UTF-8，不需要 BOM）**；
> 纯字符串计数（如 ②）也建议显式用 UTF-8 读，避免尾字节吞掉紧随其后的 ASCII 字符。
> （只做匹配、不关心分行的统计不受影响；`pwsh` 7 没有这个毛病。）

```powershell
# ⓪ 源文件数（别从仓库根递归扫描：out\artifacts 有 3529 层自我嵌套，会卡死）
"src  .java: " + (Get-ChildItem .\src  -Recurse -Filter *.java).Count   # 2026-10-02 实测 201
"mods .java: " + (Get-ChildItem .\mods -Recurse -Filter *.java).Count   # 2026-10-02 实测 12

# ① 行数 / 注释 / 空行 / 方法数（LivingThing.java）
$f = '.\src\cn\gfhnv\game\entity\LivingThing.java'
$l = [System.IO.File]::ReadAllLines($f)   # 别用 Get-Content（见上面的坑）
"总行数      : " + $l.Count
"注释行      : " + ($l | Where-Object { $_ -match '^\s*(/\*\*|\*|\*/)' }).Count
"空行        : " + ($l | Where-Object { $_ -match '^\s*$' }).Count
"访问器方法数: " + ($l | Where-Object { $_ -match '^    public .*\b(get|set|is|facSet)\w*\(' }).Count
"方法总数    : " + ($l | Where-Object { $_ -match '^    (public|protected|private).*\(.*\)\s*\{?\s*$' }).Count

# ② 属性访问器调用点（点号调用口径；别从仓库根递归扫描，out\artifacts 有 3529 层嵌套）
$files = @(Get-ChildItem .\src -Recurse -Filter *.java) + @(Get-ChildItem .\mods -Recurse -Filter *.java)
$pats = '\.(get|set)Hp\(','\.(get|set)HpMax\(','\.(get|set)Attack\(','\.(get|set)Defence\(',
        '\.(get|set)Speed\(','\.(get|set)(CriticalDMG|GetCriticalRATE|CriticalRATE)\(',
        '\.(get|set)(Attack|Defence|Speed|Hp|CriticalDMG|CriticalRate)Enhance(Percent|Amount)\(',
        '\.(get|set)\w*Resistance\(','\.(get|set)\w*Penetration\(','\.(get|set)\w*DamageEnhance\(',
        '\.(get|set)(Manas|Mana)\(','\.(get|set)\w*(GrowNumber|Grow)\(','\.(isAlive|setAlive)\('
$utf8 = New-Object System.Text.UTF8Encoding($false)
$n = 0
foreach ($file in $files) {
  $txt = [System.IO.File]::ReadAllText($file.FullName, $utf8)
  foreach ($p in $pats) { $n += ([regex]::Matches($txt, $p)).Count }
}
"属性访问点: $n"   # 2026-10-02 实测 460（src 440 / mods 20）

# ③ 自测规模与 /data 断言密度
$tl = [System.IO.File]::ReadAllLines('.\src\cn\gfhnv\debug_tools\TestCommandSystem.java')
"自测行数          : " + $tl.Count                                                                # 2805
"check             : " + ($tl | Select-String -Pattern 'check\(').Count                           # 302
"run               : " + ($tl | Select-String -Pattern 'run\(').Count                             # 136
"expectSyntaxError : " + ($tl | Select-String -Pattern 'expectSyntaxError\(').Count               # 11
"其中 /data 三段   : " + ($tl[1950..2212] | Select-String -Pattern 'check\(|run\(|expectSyntaxError\(').Count   # 122
```

## 附录 B：行号漂移对照（2026-10-02）

| 文档里的写法 | 现在实际位置 |
|---|---|
| `LivingThing.java:189`（`MODDING-GUIDE.md:163` 引的构造器） | `LivingThing.java:233` |
| `LivingThing.java:1654`（`MODDING-GUIDE.md:190` 引的 `copy()`） | `LivingThing.java:1749` |
| `LivingThing.java:2180`（`PROJECT-ANALYSIS-2026-09.md` §1.2 的行数） | 实际 2262 |
| `TestCommandSystem.java:2605`（同上，§1.3） | 实际 **2805** |
| 源文件数：200（§1.1）/ 183（§7.1） | 实际 `src` **201** 个、`mods` 12 个 |

> 规律：**代码一改，文档里的行号立刻过期**。本文的所有行号都标了"2026-10-02 实测"，
> 引用前请重新核对；结论比行号活得久。

---

*本文由 AI（DeepSeek）生成，2026-10-02。所有结论均已在源码中逐一核对（附行号）；
数字的统计口径与复现命令见附录 A。如需更精确的信息，以源码为准。*

*2026-10-03 同日追加：阶段 0、阶段 1 与"阶段 1 收尾 / 阶段 2.5"（改名 + 订正 9 个 Java 名）
均**已落地并实跑通过**（`通过 542 条，失败 0 条`），落地记录见第七节各阶段末尾；
本文档里凡标"2026-09-26"的落地/拍板记录，日期已订正为 **2026-10-03**（属性分离是 10-03 才开始的）。*
