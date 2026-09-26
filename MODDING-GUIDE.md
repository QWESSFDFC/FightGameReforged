# 模组编写指南（人 & AI 都能照着做）

> 这份文档同时面向**人类开发者**和**AI 助手**：正文按"怎么做"组织，需要的精确签名、
> 行号、坑与自检手段都写进去了；第 8 节是给 AI 的压缩版契约，可以直接整段丢给 AI 当上下文。
>
> - 适用版本：**Java 25 + 当前源码**（`src/cn/gfhnv`，182 个 java 文件）；
> - 本文与源码不一致时，**以源码为准**；
> - 配套阅读：
>   [`README.md`](README.md)（怎么玩、各个系统在哪）、
>   [`project_analyses/COMMAND-SYSTEM-2026-08.md`](project_analyses/COMMAND-SYSTEM-2026-08.md)（命令系统）、
>   [`project_analyses/ANALYSIS-2026-08.md`](project_analyses/ANALYSIS-2026-08.md) **§8 模组系统评价**
>   （深度体检：4 个"会让游戏起不来"的缺陷、安全边界、"能做什么/不能做什么"）、
>   [`project_analyses/ANALYSIS-review4.md`](project_analyses/ANALYSIS-review4.md)（最新一轮整体复核）。
>
> 官方示例：`mods/exampleModByGFHNV/`、`mods/abstractLaunchingWords/`（只打印一行，最小骨架）。
> 完整实战示例：**`mods/drunkenSword/`**（新角色 + 2 效果 + 2 物品 + 3 个技能，见第 7 节）。

---

## 0. 30 秒速览

一个模组就是 `mods/` 下的一个文件夹：

```
mods/我的模组/
├── main.json                              必须。5 个字段缺一不可
├── code/                                  必须。你的 .java 源码
│   └── com/example/mymod/mainClass.java   包名必须和 code/ 下的目录层级一致
└── bin/                                   游戏自动生成/覆盖，别手改，可以删
```

主类必须 `extends cn.gfhnv.game.mod.Mod`，并且有一个**公开的、只接收 `ModInformation`** 的构造器；
内容在 `invokeWhenLoaded()` 里用 `addEntity` / `addItem` / `addEffect` 填进去。

```java
package com.example.mymod;

import cn.gfhnv.game.mod.Mod;
import cn.gfhnv.game.mod.ModInformation;

/** 我的模组。 */
public class mainClass extends Mod {
    public mainClass(ModInformation modInfo) {
        super("myModId", modInfo);          // 第一个参数是模组 ID，会成为内容 ID 的前缀
    }

    @Override
    public void invokeWhenLoaded() {
        addEntity(new MyHero(125));
        addEffect(new MyEffect());
        addItem(new MySword());
    }
}
```

改完源码 → **重启游戏**（没有热重载）→ 控制台会打印加载结果。

---

## 1. 目录与 `main.json`

### 1.1 `main.json` 字段

`ModLoader` 用 `getString` 逐个读取，**少任何一个都会抛异常并跳过该模组**（`ModLoader.java:62-75`）：

| 字段 | 含义 | 注意 |
|---|---|---|
| `name` | 模组显示名 | 中文没问题 |
| `author` | 作者 | 纯展示 |
| `description` | 描述 | 纯展示 |
| `mainClass` | 主类**全限定名** | **不含 `code` 这一层**：`code/com/example/mymod/mainClass.java` → `com.example.mymod.mainClass` |
| `version` | 版本 | 纯展示 |

多写别的键是允许的（官方示例里就有个 `description_about_writing_mods`，本模组写了 `modID` / `guide`），
解析器只取上面 5 个。

`main.json` 用 **UTF-8 无 BOM** 保存。`JSONHelper.readJSONFile` 走的是 `FileReader`
（`JSONHelper.java:17`），Java 18 起（JEP 400）默认字符集就是 UTF-8，本项目又在启动脚本/gradle 里
显式加了 `-Dfile.encoding=UTF-8`（`启动游戏-UTF8.bat:31`、`build.gradle:28`），所以中文名不会乱码。

### 1.2 源码目录与包名

`ModLoader.collectJavaFiles`（`ModLoader.java:154-175`）**递归**扫描 `code/` 下的 `.java`，
并按**目录层级**推断类名：

```
code/com/gfhnv/mods/drunkenSword/RaiseCup.java   →  类名 com.gfhnv.mods.drunkenSword.RaiseCup
```

所以文件里的 `package` 声明**必须和目录对得上**，否则类会编译到别的位置，
`mainClass` 找不到就会打印"加载失败"。

- 一个文件一个类，**类名 = 文件名**（不要一个文件塞两个 public 类）；
- `.java` 用 **UTF-8 无 BOM**：加载器读文件时显式指定了 UTF-8（`ModLoader.java:163`，这点是对的），
  但 GBK 源码会抛 `MalformedInputException` 并被吞掉、跳过该文件，随后编译器只会报
  "找不到符号" —— 症状极具误导性。BOM 则会让 javac 报非法字符。
- **别让不同模组用同一个包名**：仓库里两个官方示例的主类都叫 `com.gfhnv.mods.mainClass`，
  靠"每个模组一个独立 `URLClassLoader`"才没出事。这是隐式约定，新模组请用自己的包名。

### 1.3 内容 ID 会带模组前缀

`Mod.addEntity/addItem/addEffect` 会把内容的 id 改成 `<模组ID>:<原id>`（`Mod.java:139-184`）：

```java
super("drunkenSword", modInfo);   // MOD_ID
addEntity(new DrunkenSwordsman(125));   // 注册 id：drunkenSword:drunkenSwordsman
```

运行时 `new` 出来的实例只有短 id，但**进游戏世界时会被自动补全**：
`LivingThing.addEffect` → `World.applyRegisteredId` → `World.fullIdOf`（`World.java:336-353`）。
补全只对**不含 `:`** 的 id 生效 —— 想给某个实例单独起 id，写成 `myMod:bossCopy1` 就不会被动。
这件事很重要：`Effect.equals` 是按 **id + origin + 是否无限** 判定的，
不补全就会和"注册表里那条"被当成两种效果，叠加/刷新全部失效。

---

## 2. 加载流程（什么时候发生什么）

```
GameMain.gameInitialize()                                    GameMain.java:61
 ├─ World.addMod(new OfficialGameContent())                  官方内容（在构造器里就注册了）
 ├─ ModLoader.modLoaderInitialize()                          扫描 mods/ → 编译 → 实例化主类 → World.addMod
 ├─ EventBus.register(...)                                   注册全局监听器
 ├─ EventBus.post(new GameStartEvent())                      → GameStartEventListener（:11）
 │    └─ 对每个模组：invokeWhenLoaded()  然后 registerItself()   内容在这里进注册表
 ├─ ConfigLoader.loadConfig()
 └─ CommandManager.initialize()                              官方命令
接着才是"选角色 / 选敌人 / 选奖励"（都读 World 注册表）→ 开战
```

要点：

- 模组内容是在**选人之前**注册好的，所以新角色会直接出现在选人列表里；
  同一个注册表既用于"我方"也用于"敌方"，所以**新角色/新怪物哪一边都能选**；
- 想注册命令：在 `invokeWhenLoaded()` 里 `CommandManager.register(new MyCommand())`
  —— 它写进全局调度器（`CommandManager.java:409`），而 `GameStartEvent` 发生在
  `CommandManager.initialize()` 之前，顺序没问题；
- `bin/` 每次加载都会先删掉重建（`ModLoader.java:93-100`），所以"只发 `.class` 不发源码"的模组用不了；
- **编译失败只会跳过该模组**，游戏本体照常跑：控制台会打印 `行 N, 文件: 原因`（`ModLoader.java:205-215`）。

---

## 3. `Mod` 基类速查

| 方法 | 作用 | 备注 |
|---|---|---|
| `addEntity(Entity)` | 加入模组实体表 | 自动加 `MOD_ID:` 前缀 |
| `addItem(Item)` | 加入模组物品表 | 同上 |
| `addEffect(Effect)` | 加入模组效果表 | 同上 |
| `removeEntity/removeItem/removeEffect` | 从模组表里移除 | **只删模组自己的 List，不会从 `World` 注销**（`Mod.java:150-196`） |
| `registerItself()` | 把三个表注册进 `World` | 框架在 `invokeWhenLoaded()` 之后自动调用；按 `contains` 去重 |
| `invokeWhenLoaded()` | 你填内容的地方 | 不要在这里调 `registerItself()` |
| `getModInformation()` | 拿到 `name/author/...` | 打印日志时用 |
| `getMOD_ID()` | 模组 ID | |
| `getClassByName(String)` | 按全限定名找模组内的类 | ⚠️ 用 `new MyMod("id")`（不传 `ModInformation`）构造时它会 NPE（`Mod.java:82`）；用双参构造器即可 |

---

## 4. 写内容

### 4.1 实体（角色 / 怪物 / 召唤物）

继承 `Player`（角色向）或直接继承 `LivingThing`/`Entity`。构造器参数顺序（`LivingThing.java:189`）：

```java
super(名称, id, 火抗, 水抗, 金抗, 木抗, 土抗, 速度, 等级, 类型,
      生命成长系数, 攻击成长系数, 防御成长系数, 元素属性);
```

- 抗性是**小数**（`0.3` = 30% 减伤，`-0.2` = 弱点多吃 20%）；
- 三个"成长系数"决定面板：`生命 = (等级-1)×成长 + 200`、`防御` 同理、`攻击 = 110 + 成长×(等级-1)`；
  官方角色用 125 级（玩家一 36/29/5、白厄 29/40/25、李晓焰 58/22/3），怪物常用 150 级；
- `类型`（`"player"` / `"insect"` …）**只是显示用的标签**，没有任何逻辑按它分支，判阵营要用
  `Fight#getOwnList/getOpponentList`；
- `元素属性` 决定主元素法力（上限 `成长×(等级-1) + 200`，其余元素 `+20`）与回蓝，
  技能消耗哪种法力就选哪个元素；
- **一定要 `setController(...)`**，否则行动时没有控制器；角色用 `new PlayerController(skills, this)`，
  怪物用 `new UniversalController(skills, this)`（随机 AI）或 `ThinkingControllerAI`；
- 背包：`getInventory().addSlot(63)`（官方角色都是 63 格）；
- **必须重写 `copy()`** 并写一个参数类型是本类的拷贝构造器：

```java
public DrunkenSwordsman(DrunkenSwordsman other) { super(other); }

@Override
public LivingThing copy() { return new DrunkenSwordsman(this); }
```

不重写的下场是**开局选人第一步就抛** `RuntimeException: 请重写此方法..类xxx`
（基类 `LivingThing.copy()` 就是抛异常，`LivingThing.java:1654`）。

### 4.2 技能

```java
public class RaiseCup extends Skill {
    public RaiseCup() {
        super("举杯邀月", "描述", 生命倍率, 攻击倍率, 防御倍率, 目标数);
        setCoolDown(0);                                   // 0 = 每回合都能放
        setConsumedMana(new Mana(120, ElementSort.FIRE));  // 不设 = 无消耗（null）
        getTags().put(TagType.ATTACK, new Tag(3));        // 只给 Utility AI 用
    }
    public RaiseCup(RaiseCup other) { super(other); }
    @Override public Skill copy() { return new RaiseCup(this); }

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        for (LivingThing target : enemies) {
            user.makeDamage(target, this);     // 伤害 + 攻击行（含技能名）都由它打印
        }
    }
}
```

> **攻击行不要自己打**：`makeDamage` 会打印整行
> `A（我方）攻击了B（敌方）  -伤害  → HP 当前/上限  【技能名】`（伤害标红、HP 标灰、
> 技能名标青、阵营标灰；不支持 ANSI 的控制台自动退化成纯文本，见 `utils/ConsoleColor`）。
> 阵营由 `Fight#sideNameOf` 判定（在 `fighterList` 里就是我方），与回合头同一套；
> 召唤物忘了加进召唤者那一侧时，这里也会跟着显示成敌人 —— 正好当了报警器。
> 自己再 `print("A攻击了B")` 就会重复；漏了又会得到一行没有主语的 `  -1109  → HP …`
> （官方代码里 `Counterattack` 就这样漏过）。

- **目标数**：`0` = 自己（控制器会调 `use(fight, user)` 两个参数的重载）、
  `-1` = 全体、正数 = 需要选 N 个目标；
- **`isForEnemies`** 默认 `true`（打敌方）；给友方用的技能记得 `setForEnemies(false)`；
- **伤害公式**（`DamageCalculate.java:114-124`）：
  `(生命×生命倍率 + 攻击×攻击倍率 + 防御×防御倍率 + 额外伤害) × (1+增伤) × (1−抗性)×(1+穿透)
  × 承伤倍率 × 等级防御衰减 × 单体倍率 × 暴击倍率`，
  其中"等级防御衰减" = `(等级×10+200) / (等级×10+200+目标防御)`。
  按公式估算：125 级角色（攻击力 4000~5000）打 150 级 BOSS（防御约 3900）时，
  **每 1.0 技能倍率约 1100~1400 点伤害**（这是抗性与 BOSS 自身减伤之前的数）；
  官方技能量级：玩家一普攻 1.0、白厄战技 3.0、枪射击 7.5、白厄最后一击 13；
- **不要在自己的 `comeToEffect` 里改自己的 `atkMagnification`**：控制器持有的技能实例是长期复用的，
  改字段等于永久变强。要"按层数改倍率"就 `Skill strike = this.copy(); strike.setAtkMagnification(...)`
  再用 `strike` 打（本模组的 `LanternSword` 就是这么写的）；
- **重写 `canUse` 时不能解引用 `enemies`**：控制器判断可用性时会传 `null`
  （`UniversalController.java:115`、`PlayerController.java:118` 都是 `canUse(fight, owner, null)`），
  两个重载都会被调用，只读 `user` 身上的状态即可（`FrostSword` 的写法）；
- 条件式技能建议做成"**放了没效果**"而不是"拒绝施放"（见 `TIPS_FOR_LLM.md` §5.8 第 2 条），
  否则 AI 会连着几回合放不出招。

### 4.3 效果

```java
public class Drunkenness extends Effect {
    public Drunkenness() {
        super("drunkenness");                 // id
        setLevel(0);                          // 想存数值就放 level
        setLastTime(1);
        getEffectTagsList().add(EffectTags.INFINITE);   // 无限持续：框架不减 lastTime
    }
    public Drunkenness(Drunkenness other) { super(other.getID()); /* 手动复制自己的字段 */ }
    @Override public Effect copy() { return new Drunkenness(this); }

    @Override
    public void comeIntoEffect(LivingThing thing) { /* 每回合被调；必须重写 */ }
}
```

- **`copy()` 必须重写**，且拷贝构造器要自己搬字段（`Effect(Effect)` 只复制 id/level/lastTime）；
- **`comeIntoEffect` 必须重写**：基类实现在这里打印
  `这里写效果具体内容........请重写这个方法`，漏掉就会每回合刷屏；
- 想"进场加成、退场还原"就照 `whenLastTimeEnd` 写，并用一个 `boolean isOn` 防止每回合重复叠加
  （官方 `DefenseEnhanceEffect`、本模组 `Hangover` 都是这个套路）。
  ⚠️ 被 `/effect remove` 之类强行摘掉时不会走 `whenLastTimeEnd`，那份加成会留在身上 —— 官方效果同样如此；
- **`equals/hashCode` 只看 `id` / `origin` / 是否无限**（`Effect.java:171-180`）：
  数值请放 `level`，额外状态请自己存字段但别指望影响判等；
- `EffectTags`：`INFINITE`（无限）、`NEGATIVE`/`POSITIVE`（显示用）、`UNIVERSAL`（**通用效果**，
  标了才能被 `/effect` 命令施加）、`SKIP_ACTION`/`CAUSE_DAMAGE`（目前全项目没人用）；
- **`comeIntoEffect` 只在"持有者自己的回合"触发**：`FightTurnPastListener:177` 每个回合只对
  **当前行动者**发一次 `EffectUpdateEvent` —— 这跟 `updateSelf()` 完全不同，
  后者每回合会对**全场所有实体**各调一次（`LivingThing.java:978-995`，那是"帧更新"钩子）。
  但要注意**额外回合也会触发它**（`EffectEventListener:19`），所以别拿它"数回合"，
  要按回合计时请用 `lastTime`。

**想让效果改属性，有两条正路**（别去改基础值）：

1. **加减伤**：`thing.addDamageReduction(来源对象, 0.2)`（`LivingThing.java:1479-1488`）。
   它<b>按对象身份</b>覆盖，所以同一个来源重复调用等价于"重设"，不会越叠越多；
   传 0 会把该来源自动摘掉。来源键请写成静态常量对象
   （官方 `Phainon.SOULSCORCH_DAMAGE_REDUCTION`、本模组 `Drunkenness.DRUNKENNESS_DAMAGE_REDUCTION`），
   多个来源之间是**乘算**叠加（50% + 25% → 只受 37.5%）。
   注意 `LivingThing` 的复制构造器会把减伤条目一起复制（`LivingThing.java:142`），
   所以副本上仍是同一个来源、不会变成两条。
2. **改攻/防/速/生命上限**：`thing.setAttackEnhancePercent(...)` / `setDefenceEnhancePercent(...)` 等，
   并在 `whenLastTimeEnd` 里用同一个数**对称减回**（官方 `DefenseEnhanceEffect`、本模组 `Hangover`）。

`setAttack` / `setDefence` 这类**改基础值**的写法不要用：效果结束时很难还原，
而且会和你自己后来的其它加成互相污染。

### 4.4 物品

```java
public class OsmanthusWine extends Item {
    public OsmanthusWine() { super("桂花酿", "描述", "osmanthusWine"); }
    public OsmanthusWine(OsmanthusWine other) { super(other); }
    @Override public Item copy() { return new OsmanthusWine(this); }

    @Override
    public void comeToEffect(LivingThing user, Fight fight) { /* 使用效果 */ }
}
```

- 玩家在回合里选择使用物品 → `PlayerController.useItem` 调 `comeToEffect(user, fight)`，
  成功后再 `removeOne`（`PlayerController.java:233-235`）；
- **物品靠 id 判等**（`Item.equals`，`Item.java:122-137`）：副本请走拷贝构造器把 id 带过去，
  否则既不能和背包里的同种物品叠成一格，`/give` 的完整 id 也认不出它
  （而且模组物品的命令写法就是完整 id）；
- 想复用官方那套"药水 = 挂一个效果"，可以 `extends cn.gfhnv.game.officialStuff.customItem.potions.EffectPotion`
  并实现 `createEffect()`（那是个 `abstract` 基类，不是必须用的）；
- 目标选择要自己写：`isForEnemies()` 只影响默认展示，真正的取目标逻辑在你的 `comeToEffect` 里。

### 4.5 命令（可选）

```java
public class MyCommand extends cn.gfhnv.game.system.command.Command {
    public MyCommand() { super("mycmd"); }

    @Override
    protected CommandNode buildNode() {
        return node().executes(ctx -> { /* ... */ return 1; });
    }
}
// invokeWhenLoaded() 里：
CommandManager.register(new MyCommand());
```

命令树的完整写法（节点类型、参数类型、选择器、补全、返回值语义）见
`officialStuff/customCommands/` 下的 9 个官方命令与
[`project_analyses/COMMAND-SYSTEM-2026-08.md`](project_analyses/COMMAND-SYSTEM-2026-08.md)。

### 4.6 监听器（可选，但很有用）

```java
public class MyListener {
    @SubscribeEvent
    public void onDamage(DamageEvent ev) { /* ... */ }
}
// 记得注册：EventBus.register(new MyListener());
```

`EventBus` 有两个必须知道的特性（`EventBus.java:21、60`）：

1. **精确类匹配**：`post` 按 `event.getClass()` 查表，注册父类事件的监听器收不到子类事件；
2. **不扫父类方法**：注册时用 `getDeclaredMethods()`，写在父类里的 `@SubscribeEvent` 不生效。

优先级默认 `3`（数字越小越先执行，同优先级按注册顺序）；`Event#setCanceled(true)` 会跳过后续监听器。

---

## 5. 踩坑清单（症状 → 原因 → 做法）

| # | 症状 | 原因 | 做法 |
|---|---|---|---|
| 1 | 选完角色立刻 `RuntimeException: 请重写此方法..类xxx` | 实体/技能/物品没重写 `copy()` | 每个内容类都写 `copy()` + 本类参数的拷贝构造器 |
| 2 | 一进战斗就 NPE，栈里有你的 `canUse` | 控制器用 `canUse(fight, owner, null)` 探路 | `canUse` 里不要解引用 `enemies` |
| 3 | 每回合刷 `这里写效果具体内容........请重写这个方法` | `Effect` 子类没重写 `comeIntoEffect` | 重写它（哪怕空实现） |
| 4 | 效果叠了两条 / 数值改了却像换了个效果 | `Effect.equals` 只看 id + origin + 是否无限 | 数值放 `level`；不要靠新字段区分 |
| 5 | 技能用着用着伤害越打越高 | 在 `comeToEffect` 里改了技能自己的倍率，而实例是复用的 | 用 `copy()` 的副本改倍率，或改完立刻改回来 |
| 6 | 自定义效果/物品 `/effect`、`/give` 找不到 | id 没进注册表，或没走 `addEffect`（少了一次 id 补全），或**用短名去找模组内容**（只认完整 id） | 在 `invokeWhenLoaded()` 里注册；施加效果走 `thing.addEffect(...)`；命令里写 `modId:名字` |
| 7 | 自定义控制器进战斗后变成随机 AI | `LivingThing` 复制构造器只重建 `PlayerController` / `ThinkingControllerAI` / `FixOrderController`，其余按 `UniversalController` 重建（`LivingThing.java:145-154`） | 用官方三种之一，或接受降级 |
| 8 | 控制台只说"编译失败"，还报"找不到符号" | 源码不是 UTF-8（被跳过）或包名/目录不一致 | 存成 UTF-8 无 BOM；`package` 与目录严格对应 |
| 9 | 别的模组莫名其妙不加载 | `GameStartEventListener` 遇到 `null` 模组会 `return` 而不是 `continue`（`GameStartEventListener.java:18-20`，见 ANALYSIS-2026-08 §8.1-M2） | 别在 `mods/` 放半成品目录；删掉不用的模组文件夹 |
| 10 | 游戏启动直接崩，且不是你的模组报错 | 加载器只 `catch (Exception)`，主类静态块抛 `Error`（如 `ExceptionInInitializerError`）会穿出去 | 别在静态块里做危险操作（读文件、开线程、除零） |
| 11 | 想给模组带图片/配置/第三方 jar | 类加载器的 URL 只有 `bin/`，且每次清空；`collectJavaFiles` 只收 `.java` | 不支持。要配置就读游戏自己的 `config/` |
| 12 | 想覆盖游戏里的类（让 `World` 变成自己的） | 类加载是父优先委托，游戏类永远优先 | 不支持；只能新增内容 |
| 13 | 改了源码没生效 | 没有热重载 | 重启游戏 |
| 14 | 加载失败，`logs/latest.log` 里什么都没有 | `ModLoader` 全程只用 `System.out/err`，不写 `LogWriter`（见 ANALYSIS-2026-08 §8.2-7） | 看控制台输出 |

**安全提醒**：模组源码是在**同一个 JVM、同一权限**下编译并立即执行的，没有沙箱
（可以读写文件、联网、`System.exit`）。**安装模组 = 授予该模组与游戏同等的权限，请只加载你信得过的源码。**

**给 AI 的提醒**：2026-09-26 起，项目自带 JDK（`tool_for_llm/zulu-25`），
AI 助手**可以**自己编译 `src` 与跑自测（见 `TIPS_FOR_LLM.md` §0），
但**模组代码不在自测范围内**（`test-command-system.ps1` 只编 `src`），所以写完模组后仍请让用户：
① 跑一次游戏看控制台；② 或在游戏里用 `/give @s <模组ID>:<物品>`、`/effect @s list`、
`/data get entity @s` 验证内容确实注册进去了（**模组内容只能写完整 id**，见下面第 6 节的命令说明）。

---

## 6. 调试与自检

| 手段 | 说明 |
|---|---|
| 控制台 | 加载成功会打印 `模组 [名字] 加载成功！`；失败会打印 `加载模组失败 [目录]: 原因` + 编译诊断 `行 N, 文件: 原因` |
| 日志 | `logs/latest.log` 只有游戏自己的日志，**模组加载信息不在里面** |
| 游戏内命令 | `/list` 看场上生物、`/effect <目标> list` 看效果、`/kill`、`/hurt`、`/endfight`；发物品用 `/give @s <模组ID>:<物品名>` —— **模组内容必须写完整 id**（如 `drunkenSword:osmanthusWine`），短名/类名只解析官方内容 |
| 静态自查 | 仓库根的 `check-sources.ps1` **只扫 `src/`**；`test-command-system.ps1` 也只编译 `src/`。模组代码不在它们的覆盖范围里 |
| 手工核对（AI 可做） | ① 括号配对/无 BOM；② `package` 与目录一致；③ `main.json` 能被 JSON 解析、5 个字段齐全、`mainClass` 文件存在；④ 每个 import 都真实用上 |

想让 `check-sources.ps1` 顺带扫模组，可以临时把它的 `$root` 换成两个目录（脚本里那行是
`$root = Join-Path $PSScriptRoot 'src'`）：

```powershell
$repo = (Get-Location).Path
$src  = Get-Content -Raw .\check-sources.ps1
# 注意路径用单引号套单引号转义，PowerShell 5.1 下请用 -Encoding UTF8 读文件
$src  = $src.Replace('$root = Join-Path $PSScriptRoot ''src''',
                     '$root = @((Join-Path $PSScriptRoot ''src''), (Join-Path $PSScriptRoot ''mods\你的模组\code''))')
$src  = $src.Replace('if (-not (Test-Path $root)) {', 'if ($false) {')
& ([scriptblock]::Create("`$PSScriptRoot = '$repo'; " + $src))
```

---

## 7. 完整实战示例：`mods/drunkenSword/`「醉剑仙」

一个"攒资源 → 一次性倾泻"的角色，正好覆盖三类内容。

| 文件 | 内容 |
|---|---|
| `main.json` | 模组信息 |
| `code/com/gfhnv/mods/drunkenSword/DrunkenSwordMod.java` | 主类：注册全部内容 |
| `.../DrunkenSwordsman.java` | 角色「酒剑仙」（125 级、火属性、速度 130、生命/攻击/防御成长 34/32/12；**开局自带 `INITIAL_STACKS` 层醉意**，走 `whenFightStart`） |
| `.../Drunkenness.java` | 【醉意】层数资源（`INFINITE` 效果，数值存 `level`，上限 10；每层还提供 2% 减伤） |
| `.../Hangover.java` | 【宿醉】负面效果（2 回合、防御 −30%，进场上调/退场对称减回） |
| `.../RaiseCup.java` | 普攻「举杯邀月」：单体 300%，自身 +2 层【醉意】，无消耗无冷却 |
| `.../LanternSword.java` | 战技「醉里挑灯看剑」：花光层数，单体 (200% + 每层 150%)，每层回 3% 最大生命；300 火法力 / 无冷却 |
| `.../FrostSword.java` | 大招「一剑霜寒十四州」：**需 ≥4 层**，花光层数，全体 (400% + 每层 250%)，自身获 2 回合【宿醉】；800 火法力 / 无冷却 |
| `.../OsmanthusWine.java` | 物品【桂花酿】：+4 层醉意，回 5% 最大生命 |
| `.../SoberSoup.java` | 物品【醒酒汤】：清空醉意，每层回 4% 最大生命 |

**玩法**：开局自带 2 层（`INITIAL_STACKS`）→ 普攻垫层（每层还带 2% 减伤）→
攒到 4 层以上放大招（满 10 层是 2900% 全体）→
打光醉意等于把护甲也交出去，随后还有 2 回合【宿醉】（防御 −30%），所以要么先手秒人、要么先喝醒酒汤保命。
三个技能都**没有冷却**（和官方角色一致），唯一的门槛是醉意层数与法力。
**所有数值都是各文件顶部的 `public static final` 常量**，手感不对只改数字。

这个示例特意演示了几件事：层数资源用效果实现、技能按层数临时改倍率（`copy()` 副本）、
`canUse` 安全地读自身状态、物品把层数换成治疗、一个"进场加成/退场还原"的负面效果，
以及效果通过 `addDamageReduction(source, percent)` 挂减伤（按来源覆盖、幂等同步）。

---

## 8. 给 AI 的压缩契约（可直接当上下文）

**必须满足的硬性条件**

1. `main.json`：UTF-8 无 BOM，含 `name` / `author` / `description` / `mainClass` / `version`；
   `mainClass` = `code/` 之后的路径 + 类名（不含 `code`）。
2. 主类：`public class X extends cn.gfhnv.game.mod.Mod`，**public** 构造器 `X(ModInformation)`，
   构造器里 `super("<modId>", modInfo)`；内容放 `invokeWhenLoaded()`，用 `addEntity/addItem/addEffect`，
   **不要**在里面调 `registerItself()`。
3. 每个 `.java`：UTF-8 无 BOM、`package` 与目录一致、一个文件一个 public 类；
   所有内容类都要有 `copy()` + 本类参数的拷贝构造器。
4. 只用 JDK + 仓库已有代码（唯一第三方依赖是 `org.json`）；不要新增依赖。

**常用签名（照抄）**

```java
// 实体
LivingThing(String name, String id, double 火抗, double 水抗, double 金抗, double 木抗, double 土抗,
            long speed, long level, String type, double hpGrow, double atkGrow, double dfkGrow, ElementSort element)
setMass(double) / setDescription(String) / getInventory().addSlot(long) / setController(Controller)
// 技能
Skill(String name, String description, double hpMag, double atkMag, double defMag, int aims)
setCoolDown(int) / setConsumedMana(Mana) / getTags().put(TagType.ATTACK, new Tag(int))
boolean canUse(Fight, LivingThing, List<LivingThing>)   // 第三参可能为 null
void comeToEffect(Fight, LivingThing, List<LivingThing>)
user.makeDamage(target, skill) / user.setHp(user.getHp() + n) / user.getHpMax() / user.getName() / user.isAlive()
// 效果
Effect(String id) / setLevel(int) / setLastTime(int) / getEffectTagsList().add(EffectTags.X)
Effect copy() / void comeIntoEffect(LivingThing) / void whenLastTimeEnd(LivingThing)
thing.addEffect(Effect) / thing.getEntityEffectList() / thing.removeEffect(Effect)
// 物品
Item(String name, String description, String id) / Item(Item)
void comeToEffect(LivingThing user, Fight fight) / Item copy()
// 法力
new Mana(double amount, ElementSort)    // 不设置 consumedMana = 无消耗
// 命令
CommandManager.register(Command...)     // Command: protected CommandNode buildNode()
```

**不能做的事**：不能改 `src/` 里任何类；不能带资源文件/第三方 jar；不能覆盖游戏类；
不能热重载（重启才生效）；不能假设有编译器可用（写完必须让用户实跑）。

---

## 9. 相关文档索引

| 想了解 | 看哪里 |
|---|---|
| 怎么玩、命令怎么用、项目结构 | [`README.md`](README.md) |
| 命令系统的实现（含返回值、选择器、补全） | [`project_analyses/COMMAND-SYSTEM-2026-08.md`](project_analyses/COMMAND-SYSTEM-2026-08.md) |
| 模组系统的深度体检（崩溃级缺陷、安全边界、类加载细节） | [`project_analyses/ANALYSIS-2026-08.md`](project_analyses/ANALYSIS-2026-08.md) §8 |
| 整体架构与最新复核 | [`project_analyses/ANALYSIS-review4.md`](project_analyses/ANALYSIS-review4.md) |
| 给 AI 的工作约定、框架坑、当前进度 | `TIPS_FOR_LLM.md`（**在 `.gitignore` 里，不随仓库发布**） |
| 官方内容怎么写（角色/技能/效果/物品的完整实现） | `src/cn/gfhnv/game/officialStuff/` |
| 官方命令怎么写 | `src/cn/gfhnv/game/officialStuff/customCommands/` |

---

*本文档由 AI（DeepSeek）生成，基于对 `src/`、`mods/` 与 `project_analyses/` 的实际阅读；
如与源码不符，以源码为准。*
