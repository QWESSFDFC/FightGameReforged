# 模组编写指南（人 & AI 都能照着做）

> 这份文档同时面向**人类开发者**和**AI 助手**：正文按"怎么做"组织，需要的精确签名、
> 行号、坑与自检手段都写进去了；第 8 节是给 AI 的压缩版契约，可以直接整段丢给 AI 当上下文。
>
> - 适用版本：**Java 25 + 当前源码**（`src/cn/gfhnv`，215 个 java 文件）；
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
> 特殊机制示例：**`mods/liXiaoYanPlus/`**（不新增任何内容，只提高官方角色李晓焰的【燃点】上限，
> 演示"用扩展点改官方机制"，见 §7.1）。

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

想给模组加"玩家可调的数值"（初始层数、技能门槛、掉落概率……）→ 见 **§4.7 模组配置**：
主类上加 `@ModConfig(id = "myModId")`、`implements ModDataAware`，配置读 `config/data/myModId.json`。
配置分组名的优先级是 **注解 > `MOD_ID`**（不写注解就用 `MOD_ID`；两者都没有就跳过这个模组的配置）。
**模组不能自带配置文件**（第 11 条坑），接口是**可选**的，不实现就一切照旧。

> ⚠️ **`config/gameConfig/EntityData.json` / `SkillData.json` 里没有你的内容**（2026-10-03 起）：
> 那两份是**游戏自己**的配置，只装官方内容。你的实体与技能的数值走
> **你自己的** `config/data/<配置分组名>.json`。理由与代价见 §4.7 的第二条警告。

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

## 4.7 模组配置（可选，2026-10-03 新增）

想让玩家能调你模组的数值（初始层数、技能门槛、掉落概率……），用**注解 + 可选接口**：

```java
package com.gfhnv.mods.drunkenSword;

import cn.gfhnv.game.mod.Mod;
import cn.gfhnv.game.mod.ModInformation;
import cn.gfhnv.game.mod.config.ModConfig;
import cn.gfhnv.game.mod.config.ModConfigDocument;
import cn.gfhnv.game.mod.config.ModDataAware;

@ModConfig(id = "drunkenSword")          // 优先级：注解 > MOD_ID；不写注解就退回 Mod.getMOD_ID()
public class DrunkenSwordMod extends Mod implements ModDataAware {

    /** 玩家在配置里写的初始【醉意】层数（读不到就是 2）。 */
    private static int initialStacks = 2;

    public DrunkenSwordMod(ModInformation info) { super("drunkenSword", info); }

    /** 游戏在加载你的内容之前调用一次；没有配置文件时也会调用（文档是空的）。 */
    @Override
    public void applyConfig(ModConfigDocument cfg) {
        initialStacks = cfg.getInt("initialStacks", 2);   // 路径用 / 分隔，读不到就返回默认值
    }

    @Override
    public void invokeWhenLoaded() {
        addEntity(new DrunkenSwordsman(CHARACTER_LEVEL, initialStacks));
        // ……
    }
}
```

玩家在 `config/data/drunkenSword.json` 里这么写：

```json
{
  "version": 1,
  "common": {
    "entities": { "game_official_content:flameReaver": { "derived": { "hpMax": 90000 } } },
    "skills": { "game_official_content:flameReaver#灾厄-弑魂焚诏": { "coolDown": 5 } }
  },
  "drunkenSword": {
    "initialStacks": 4,
    "skills": { "frostSword": { "requiredStacks": 3 } }
  }
}
```
> ⚠️ `common` 段只支持 **`entities` + `skills`** 两段（格式分别与
> `config/gameConfig/EntityData.json` / `SkillData.json` 一样）。
> **规则段做不到**：写 `"formula"` / `"mana"` / `"flameReaver"` / `"insectBoss"` /
> `"actorLiXiaoYan"` 会在控制台得到一句明确的"这一段改不了"，原因见 §4.7 末尾。

> ⚠️ **模组内容不会进游戏自己的那两份配置文件**（2026-10-03 起，用户要求）：
> `config/gameConfig/EntityData.json` 与 `SkillData.json` **只装官方内容**
> （判据是"谁注册的"：`ConfigDefaultWriter.isOfficialContent`）。
> 你注册的实体与它们的技能**不会**被自愈写进去 —— 那两份是**游戏自己**的配置，
> 混进别人的东西会让"删了模组以后剩下没人认领的死配置"。
> 你的数值走**你自己的** `config/data/<配置分组名>.json`（就是这一节讲的那条路）。
> **代价说清楚**：游戏**不会**替你生成模组那一段的样例值，
> 你的旋钮要写进你的文档 / README；游戏侧的 `/data get entity <你的完整id>` 也能看当前值。
> 附带好处：**游戏仍会应用**你在 `common.entities` / `common.skills` 里给模组内容写的补丁
> （补丁按 id 找模板，与"生成哪一份"无关），所以 `common` 段照旧能当"改模组默认值"的入口。

### 配置分组名怎么定（优先级：**注解 > `MOD_ID`**）

配置文件是 `config/data/<配置分组名>.json`，文件里属于你的那一段也叫 `<配置分组名>`。
这个分组名由 `ConfigLoader.resolveModConfigId(Mod)` 一个地方决定（两个加载入口都调它，不会两处不一致）：

| 模组主类上 | 用的分组名 | 说明 |
|---|---|---|
| `@ModConfig(id = "drunkenSword")` | `drunkenSword` | `id` 的**首尾空白会被去掉**；**即使与 `MOD_ID` 不同也以注解为准**（配置文件名跟注解走） |
| `@ModConfig(id = "   ")`（只有空白） | `MOD_ID` | 空白串不算"写了分组名"，与没写注解一样退回 `MOD_ID` |
| 没写注解 | `MOD_ID` | 现有模组走的就是这条，行为与"注解被读取"之前完全一样 |
| 没写注解 + `MOD_ID` 是 `null`/空串 | —— | 这个模组**拿不到配置**：打印一行提示后跳过它（不抛异常、不影响别的模组），它的 `applyConfig` 也不会被调用 |

这个注解是**真的被反射读的**（`ConfigLoader.resolveModConfigId(Mod)` 里那句 `mod.getClass().getAnnotation(ModConfig.class)`），
不是文档性的注解 —— 它带 `@Retention(RUNTIME)` 正是为此；也因为它**没有** `@Inherited`，
注解要写在主类**自己**头上（写在父类上游戏读不到）。

### 规则（六条）

| # | 规则 |
|---|---|
| 1 | **配置文件在游戏自己的 `config/data/<配置分组名>.json`**，分组名默认就是模组 id（`MOD_ID`），写了 `@ModConfig(id = …)` 就以注解为准（**注解 > `MOD_ID`**，见上表）。**模组不能自带配置文件**（第 11 条坑：类加载器的 URL 里根本没有你的目录）。 |
| 2 | **接口是可选的**：不实现 `ModDataAware` 就完全不读配置，游戏只打印一行"跳过"，**不报错**。现有模组一个字都不用改。 |
| 3 | **`common` 段是共享区**：它是"模组对官方数值的调整"，会被当成一层补丁打给官方内容（在 `config/gameConfig/EntityData.json` **之后**应用，所以后者胜）。格式与 `EntityData.json` 完全一样。 |
| 4 | **你自己那一段的字段名由你定**：游戏不校验、不映射、出错也不报 —— 那是你的旋钮。游戏只保证"路径查找 + 缺失返回默认值"。 |
| 5 | **读不到别的模组的分组**：`ModConfigDocument` 只暴露"你自己的分组 + `common/`"。模组之间的配置互相隔离。 |
| 6 | **没有配置文件时照样调用 `applyConfig`**，文档是空的（所有 `getXxx(path, 默认值)` 都返回你给的默认值）。不用写"有没有文件"的分支；也**不要**因为没配置就跳过 `invokeWhenLoaded()`（那会让内容整个消失）。 |

### `ModConfigDocument` 速查

| 方法 | 说明 |
|---|---|
| `getInt` / `getLong` / `getDouble` / `getBoolean` / `getString` | `(路径, 默认值)`；**读不到或类型不对一律返回默认值**，不抛异常 |
| `getStringList(路径, 默认值)` | 读 JSON 数组的字符串（非字符串元素被跳过） |
| `has(路径)` | 用户到底写没写这个路径（能区分"写了 0"和"没写"） |
| `section(路径)` | 取一个子对象（不存在时返回空文档，用起来不用判空） |

**路径两种写法等价**：`"initialStacks"` 与 `"drunkenSword/initialStacks"`（文档的根**就是**你自己那一段）；
`"common/xxx"` 从共享区找。路径用 `/` 分隔，越界/不存在都返回默认值。

### 首次运行自动生成默认配置（可选，2026-10-03 新增）

实现了 `ModDataAware` 的模组可以再实现一个**可选**方法，让游戏在**配置文件不存在**时
自动生成一份可改的默认配置 —— 玩家第一次运行就有一份能照着改的文件，不用自己去猜格式：

```java
@Override
public java.util.Map<String, Object> defaultConfig() {
    java.util.Map<String, Object> own = new java.util.LinkedHashMap<>();
    own.put("ignitionBonus", DEFAULT_IGNITION_BONUS);
    return own;   // 游戏会包成 {"version":1,"<分组名>":{...}} 写进 config/data/<分组名>.json
}
```

- **返回 `Map` 而不是 `org.json.JSONObject`** —— 模组不该为了声明两个默认值而依赖 `org.json`。
- **只在文件不存在时写**；已存在的文件**一个字节都不动**（玩家改过的值不会被覆盖）。
- **不实现这个方法**（或返回 `null`）= 不生成，行为与以前完全一样 —— **现有模组零改动**。
- 想恢复出厂值：删掉那个文件、重启游戏，会按你声明的默认值再生成。
- **写盘归加载器管**（建目录、UTF-8、报错口径），模组只负责"声明默认值" ——
  否则每个模组都要自己拼路径、自己建目录、自己处理编码。
- 抛异常只中断**你自己**的默认配置，游戏与其它模组照常运行（与 `applyConfig` 同一个口径）。

### 时机

```
GameStartEvent
  └─ 逐个模组：ConfigLoader.loadModData(这个模组)   ← 你的 applyConfig 在这里被调用
       ├─ 先打 common 段（如果写了）
       └─ 再 applyConfig(你自己的那一段)
  └─ 紧接着：你的 invokeWhenLoaded() → registerItself()
```

**每个模组各自 `try/catch (Throwable)`**：你的 `applyConfig` 抛异常只会跳过**你自己**的配置，
其它模组与游戏照常（这是刻意堵住的"一个模组出错 → 后面的模组全不加载"那条路）。

### 官方数值也能改（走共享区）

改官方**实体 / 技能**数值，用 `common` 段 —— 格式与 `config/gameConfig/` 下对应的文件一模一样：

```json
{
  "common": {
    "entities": { "game_official_content:insectBoss": { "derived": { "hpMax": 50000 } } },
    "skills":   { "game_official_content:insectBoss#分裂": { "coolDown": 5 } }
  }
}
```

⚠️ `common.entities` 必须带 **`entities` 这一层**（少了它只会打印一行"缺少 `entities` 这一层"，不生效）；
`common.skills` 同理要带 `skills` 这一层。
⚠️ 两个模组改同一条时**后面的赢**（模组加载顺序来自 `listFiles()`，不可复现）—— 所以别两个模组改同一个键。

**❌ 规则（`GameRules`）改不了 —— 这是硬限制，不是漏做**：

```json
{ "common": { "flameReaver": { "containerLimit": 8 } } }   ← 不生效
```

`GameRules` 在游戏启动的**第一步**（造任何实体之前）就读进内存并 `freeze()` 了，
而模组配置是在那之后的 `GameStartEvent` 里加载的；而且很多使用点是
`static final X = GameRules.getDouble(...)`（类加载时读一次），表要是能中途变值，
行为就取决于"哪个类先被加载"，这种 bug 无法复现。
所以写规则段时控制台会明确说一句「这一段改不了」并告诉你该去哪儿改（`config/gameConfig/GameRules.json`），
**不会静默忽略**。

---

## 5. 踩坑清单（症状 → 原因 → 做法）

| # | 症状 | 原因 | 做法 |
|---|---|---|---|
| 1 | 选完角色立刻 `RuntimeException: 请重写此方法..类xxx` | 实体/技能/物品没重写 `copy()` | 每个内容类都写 `copy()` + 本类参数的拷贝构造器 |
| 1b | 数值改了却没生效（尤其是"配置里改了、游戏里还是老数值"） | `copy()` 里写的是 `return new MySkill();` —— 那会**重新装一遍构造器里的出厂值**，所以复制出来的副本永远是新数值 | `copy()` 必须走本类的拷贝构造器（`return new MySkill(this);`），并且**拷贝构造器里要把你自己的每个字段都复制一遍** |
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
| 15 | 想改官方角色/官方机制的数值，反射硬改私有字段"能跑"但一升级就静默失效 | 私有字段名**不在任何契约里**（`MODDING-GUIDE.md` 只把构造器、setter、`copy()` 写成 API），改名后没有任何提示，`src` 的自测也管不到模组 | 走**扩展点链**（§7.1 的 `IModifyIgnitionMax` / `IModifyDamage` / `damageReductions` / `IDefenceIgnore`），或用 `config/data/<你的modid>.json` 的 `common` 段；确实没有链的机制，就在 `src/` 里照 §7.1 的形状加一条（默认值必须逐位不变） |

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

## 7.1 特殊机制模组：改官方角色的数值 / 上限（`mods/liXiaoYanPlus/`「李晓焰加强」）

> **这是 2026-10-03 新增的一条路**：不动 `src/`、不反射、不新增内容，
> 只把游戏留出来的**扩展点链**挂上一个修正器。

| 文件 | 内容 |
|---|---|
| `main.json` | 模组信息（`modID` = `liXiaoYanPlus`） |
| `code/com/gfhnv/mods/liXiaoYanPlus/LiXiaoYanPlusMod.java` | 主类：登记一个【燃点】上限修正器 + 读自己的配置 |

主类全文就三件事（照抄这个骨架）：

```java
@ModConfig(id = LiXiaoYanPlusMod.MOD_ID)
public class LiXiaoYanPlusMod extends Mod implements ModDataAware {

    public static final String MOD_ID = "liXiaoYanPlus";
    public static final int DEFAULT_IGNITION_BONUS = 5;
    private static int ignitionBonus = DEFAULT_IGNITION_BONUS;

    public LiXiaoYanPlusMod(ModInformation modInfo) { super(MOD_ID, modInfo); }

    /** 游戏在 invokeWhenLoaded() 之前调用；没有配置文件时也会调用（文档是空的）。 */
    @Override
    public void applyConfig(ModConfigDocument cfg) {
        ignitionBonus = cfg.getInt("ignitionBonus", DEFAULT_IGNITION_BONUS);
    }

    /** 只登记一个修正器：不改任何内容，只把上限抬高。 */
    @Override
    public void invokeWhenLoaded() {
        ActorLiXiaoYan.addIgnitionMaxModifier((baseMax, owner) -> Math.max(0, baseMax + ignitionBonus));
    }
}
```

玩家想调加成，在游戏自己的 `config/data/liXiaoYanPlus.json` 里写（**模组不能自带配置文件**）：

```json
{ "version": 1, "liXiaoYanPlus": { "ignitionBonus": 8 } }
```

### 现有扩展点速查

| 想影响什么 | 挂哪里 | 语义 |
|---|---|---|
| **【燃点】上限**（李晓焰） | `ActorLiXiaoYan.addIgnitionMaxModifier(IModifyIgnitionMax)` | 每次读上限依次过链；链为空 = 出厂值。`int modifyIgnitionMax(int baseMax, LivingThing owner)` |
| 受到的伤害 | `thing.addModifyDamage(IModifyDamage)` / `setModifyDamage(...)` | 同上，`long damageModify(long newHp, DamageEvent da)` |
| 减伤百分比 | `thing.addDamageReduction(来源对象, 0.2)` | **按来源对象身份**覆盖、多个来源乘算；传 0 摘掉 |
| 无视防御 | 让效果实现 `IDefenceIgnore` | 伤害计算只认接口，不认具体是哪个效果（`IDefenceIgnore` 的类注释就是这么写的） |
| 官方实体 / 技能的数值 | `config/data/<你的modid>.json` 的 **`common` 段** | 见 §4.7；**`common` 支持 `entities` + `skills`**（2026-10-03 起 `skills` 真的生效了），**但改不了 `GameRules`**（那张表在开局就冻结了，写规则段会得到一句明确报错） |
| **模组内容自己的数值** | **也是** `config/data/<你的modid>.json`，写在**你自己的分组**那一段 | ⚠️ **不会**出现在 `config/gameConfig/EntityData.json` / `SkillData.json` 里（那两份只装官方内容，2026-10-03 起）—— 见 §4.7 的第二条警告 |
| 官方角色的**其它**机制（没有现成链的） | ❌ 做不到 | 只能反射硬改私有字段 —— 字段名不在任何契约里、改名即静默失效、自测覆盖不到。**要长期做就在 `src/` 里加一条链**（照 `IModifyIgnitionMax` 的形状），别用反射 |

### 三条纪律

1. **数值默认值必须逐位不变**：链为空时 `effectiveIgnitionMax()` 返回的就是基准上限，
   所以"没人挂修正器"与"没有这个扩展点"完全等价。加扩展点时自测第一条就钉这个；
2. **修正器必须是纯函数**：每次读上限都会被调一次（`getIgnitionMax()` 与 `setIgnition()` 都走它），
   在里面改角色状态会变成"读一次改一次"；
3. **`mods/` 不在自测编译范围内**（见 §6 的表格）：新加的扩展点要在 `src/` 里有断言，
   模组自己那份只能靠 `javac` 单编一次 + 进游戏看控制台。

> ⚠️ **别指望自愈会给你生成样例值**（2026-10-03 起）：`config/gameConfig/EntityData.json` /
> `SkillData.json` 只装官方内容（`ConfigDefaultWriter.isOfficialContent` 按"谁注册的"判），
> 模组实体与它们的技能从那两份里被排除了。**你模组自己的旋钮要在你的文档里写清楚**；
> 想知道当前值可以进游戏敲 `/data get entity <你的完整id>`（例如
> `drunkenSword:drunkenSwordsman`）。`common` 段仍然能改模组内容（补丁按 id 找模板）。

**进游戏怎么确认生效**：选中李晓焰放任意技能，控制台会打印 `燃点层数:X/上限:Y`（`setShowSpecialMes`），
上限那一栏就是加成之后的数。

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
// 模组配置（可选；见 §4.7）
@ModConfig(id = "myModId") class MyMod extends Mod implements ModDataAware   // 分组名：注解 > MOD_ID
void applyConfig(ModConfigDocument cfg)  // invokeWhenLoaded() 之前调用；没有配置文件时也会调用
cfg.getInt/getLong/getDouble/getBoolean/getString("路径", 默认值) / cfg.has("路径") / cfg.section("路径")
// 改官方机制的扩展点（见 §7.1）
ActorLiXiaoYan.addIgnitionMaxModifier((baseMax, owner) -> baseMax + 5)   // 提高燃点上限
thing.addModifyDamage(...) / thing.addDamageReduction(...) / 效果实现 IDefenceIgnore
```

**不能做的事**：不能改 `src/` 里任何类（想影响官方内容，走上面那几个扩展点，
或者 `config/data/<你的modid>.json` 的 `common` 段）；不能带资源文件/第三方 jar；不能覆盖游戏类；
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
| **游戏自己的数值怎么调**（实体 / 技能 / 魔法数字） | [`README.md`](README.md) 的「数值怎么调」一节；设计文档 [`project_analyses/EXTERNAL-DATA-LOADING-2026-10.md`](project_analyses/EXTERNAL-DATA-LOADING-2026-10.md) |

---

*本文档由 AI（DeepSeek）生成，基于对 `src/`、`mods/` 与 `project_analyses/` 的实际阅读；
如与源码不符，以源码为准。*
