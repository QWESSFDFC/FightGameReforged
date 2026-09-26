# 命令系统（AI 编写）· 说明与测试指南

> 本文档由 AI 生成，仅作参考，请以源码为准。
> 命令系统的**全部新增代码与 Javadoc 都由 AI（DeepSeek）编写**，作者未逐条核对。

---

## 一、这次做了什么

参考《我的世界》Java 版（Brigadier + CommandSourceStack）把命令系统补齐，**没有引入任何第三方库**（只用了 JDK 与项目已有的 `org.json`）。

### 新增/重写的文件

```
src/cn/gfhnv/game/system/command/          ← 命令框架
├── ArgumentType.java            参数类型接口（@FunctionalInterface，可写 lambda）
├── LiteralArgumentType.java     字面量
├── WordArgumentType.java        单词
├── StringArgumentType.java      字符串 / 贪婪整行
├── IntegerArgumentType.java     int（可限范围）
├── LongArgumentType.java        long（可限范围）
├── DoubleArgumentType.java      double（可限范围）
├── BoolArgumentType.java        bool（额外接受 是/否、开/关）
├── EntityArgumentType.java      实体选择器（entity() / entities()）
├── EntitySelector.java          选择器语法与求解（@s @p @r @a @e @n + [type=…,name=…,limit=…,sort=…]）
├── StringReader.java            带光标的输入读取器
├── CommandSyntaxException.java  带定位的语法异常（<--[HERE]）
├── CommandNode.java             节点基类 + CommandExecutor 函数式接口（含 isLiteralNode() 能力判定）
├── LiteralCommandNode.java      字面量节点
├── ArgumentCommandNode.java     参数节点
├── ArgumentBuilder.java         构建器，同时也是节点（字面量/参数分支都用它建）
├── CommandParseHelper.java      三个数值类型共用的解析小工具（包内）
├── Command.java                 命令基类
├── CommandRegistration.java     注解式注册（@Subcommand）+ CommandBuilder
├── CommandDispatcher.java       注册表 + 递归下降解析 + 补全建议
├── CommandContext.java          按名字取参数 + 执行环境
├── CommandParameter.java        旧 API 的兼容视图（保留）
├── ParameterEntry.java          旧结构（保留）
├── CommandParameterType.java    旧枚举（保留）
├── CommandResult.java           执行结果
├── CommandSender.java           命令来源接口
├── CommandSource.java           来源 + 当前战斗登记
├── CommandManager.java          ★ 输入命令的预留入口（静态门面）
└── Subcommand.java              @Subcommand 注解

src/cn/gfhnv/game/officialStuff/customCommands/   ← 官方命令
├── OfficialCommands.java        统一注册（并把注册表里可用的效果打进日志）
├── KillCommand.java             /kill   重写
├── ListCommand.java             /list
├── HurtCommand.java             /hurt
├── EffectCommand.java           /effect 加/移除/查看效果（从效果注册表取模板）
├── ExecuteCommand.java          /execute as <目标> run <命令>（换个执行者再跑一条命令）
├── GiveCommand.java             /give <目标> <物品> [数量]（完整 id 谁都认；短名/类名只解析官方内容）
├── SummonCommand.java           /summon <实体> [阵营]（2026-09-26 新增；阵营默认我方，规则与 /give 同源）
├── DataCommand.java             /data get|merge|modify × entity|storage（2026-09-26 新增，见 NBT-AND-DATA-COMMAND-2026-09.md）
├── EndFightCommand.java         /endfight
└── HelpCommand.java             /help 与 /?

src/cn/gfhnv/debug_tools/TestCommandSystem.java   ← 自测程序（可单独运行）

src/cn/gfhnv/game/officialStuff/customItem/potions/   ← 效果药水（8 瓶，官方物品共 9 件）
├── EffectPotion.java            基类：使用后给自己挂 createEffect() 返回的效果
└── AttackPotion 等 8 个          攻击 / 防御 / 生命 / 迅捷 / 暴击 / 暴击伤害 / 穿甲 / 治疗
```

### 被改动的既有文件（改动都很小）

| 文件 | 改动 |
|---|---|
| `GameMain.java` | 调用 `CommandManager.initialize()`；新增 `readInput()` 在原来读输入的地方拦命令；新增 `isInFight()` / `endFight(Fight, boolean)`；开战时登记 `CommandManager.setCurrentFight(fight)` |
| `PlayerController.java` | 所有 `SCANNER.nextLine()` 改走新的私有方法 `nextLine()`（先问一句「是不是命令」）；`useItem` 里原来的 `SCANNER.nextInt()` 改成「读一整行再解析」（顺手修掉了换行符残留导致的输入错位） |
| `FightEndEventListener.java` | 收尾时叫停回合循环（`setDriving(false)`）并把当前战斗从命令系统注销 |
| `FightTurnPastListener.java` | 新增 `setDriving/ isDriving`；循环开头多一句「战斗是否已被命令结束」的判断 |

**输入模式没有任何变化**：玩家依旧输入 `yes / no / next / 数字`，只是**额外**多了一种输入——以 `/` 或 `#` 开头的命令。

---

## 二、预留的「输入命令的方法」

核心就是 `CommandManager` 里这几个静态方法，**下一步想在哪里接命令，加一行就够**：

```java
// 1. 只想问一句「这行是不是命令」（不执行）
if (CommandManager.isCommand(input)) { ... }

// 2. 是命令就执行并打印结果，返回是否已处理（最常用）
input = scanner.nextLine();
if (CommandManager.process(input)) {
    continue;              // 是命令 → 回到循环开头，继续读输入
}
// 不是命令 → 原来的逻辑照常执行

// 3. 只要结果码（-1 表示失败，失败信息已打印）
int affected = CommandManager.execute("/kill @s");

// 4. 要完整结果对象（不打印任何东西，适合测试/模组）
CommandResult result = CommandManager.executeResult("#list");

// 5. 整段接管输入：一直读命令行，直到输入 exit / 退出
CommandManager.runLoop(scanner, "> ");
```

其它有用的入口：

```java
CommandManager.initialize();                     // 注册官方命令（GameMain 已调用）
CommandManager.register(new MyCommand());        // 模组注册自己的命令
CommandManager.getDispatcher();                  // 拿调度器：补全建议、查命令树
CommandManager.setPlayer(livingThing);           // 告诉系统「谁是玩家」，@s/@p 才有参照
CommandManager.setCurrentFight(fight);           // 登记当前战斗，@a/@e 才有查找范围
CommandManager.clearCurrentFight();              // 战斗结束
CommandManager.setEnabled(false);                // 一键关闭命令功能（行为与改动前完全一致）
CommandManager.setLogCommands(false);            // 不把命令写进 latest.log
CommandManager.describeState();                  // 当前状态（调试）
```

---

## 三、命令用法

输入以 `/` 或 `#` 开头都行（两个前缀等价）。

| 命令 | 说明 |
|---|---|
| `/help` 或 `/?` | 列出所有命令 |
| `/help <命令名>` | 看某条命令的用法 |
| `/list` | 列出当前战斗里所有生物的状态（HP、攻防速、是否存活） |
| `/list <目标>` | 只列出选择器选中的实体 |
| `/kill <目标>` | 把目标生命值清零 |
| `/hurt <目标> <数值>` | 改生命值，正数扣血、负数回血 |
| `/effect <目标> list` | 列出目标身上的效果（id、等级、剩余回合、正面/负面） |
| `/effect <目标> add <效果> [等级] [持续回合]` | 从效果注册表取模板并施加到目标身上 |
| `/effect <目标> add <效果>(参数,…)` | 用指定的构造函数参数新建效果实例，例如 `CriticalDMGEnhanceEffect(1,5)` |
| `/effect <目标> remove <效果>` | 按 id 移除目标身上的该效果 |
| `/effect <目标> remove all` | 清空目标身上的全部效果（`*` 同义） |
| `/execute as <目标> run <命令>` | 以指定对象的身份运行另一条命令（内层 `@s` 指向它） |
| `/give <目标> <物品> [数量]` | 发物品；**官方物品**可写短名/类名，**模组物品必须写完整 id**（见下），数量默认 1 |
| `/summon <实体> [阵营]` | 往当前战斗里召唤一个生物；阵营 `our`（默认）/ `enemy`（也接受 `ally`/`foe`/`我方`/`敌方`）。名字规则同 `/give` |
| `/endfight` | 强制结束战斗（默认按玩家胜利结算，会发奖励） |
| `/endfight lose` | 强制结束战斗并按失败结算 |

**名字的命名空间规则（2026-09 起，`/give`、`/effect` 与 `/summon` 共用同一套）**：

| 写法 | 例子 | 谁能匹配 |
|---|---|---|
| 完整 id（带 `:`） | `game_official_content:aNiceSword`、`drunkenSword:osmanthusWine` | 任何内容，精确匹配 |
| 短名 | `aNiceSword`、`frozen` | **只有官方内容** |
| 简单类名 | `ANiceSword`、`Frozen` | **只有官方内容** |

判据是「谁注册的」：`OfficialGameContent#isOfficial(...)` 在模组表里找到认领该内容的模组，
看它是不是官方内容本身（**不能**用"id 里带没带冒号"判断 —— 官方内容的 id 同样带
`game_official_content:` 前缀）。没有模组认领的内容（测试里直接塞进 `World` 的临时内容）按官方处理。

写模组内容的短名会被拒绝，并提示该写的完整 id；报错列表里官方内容列短名、模组内容列完整 id
（它只能这么写）。撞名时直接报错要求写全 id，不会随手挑第一个。

**`/effect` 的效果名**：效果注册表（`World.getEffectList()`）里的名字，规则同上。
`frozen`（官方短名）、`damageEnhanceEffect`、`CriticalDMGEnhanceEffect(1,5)`、
`drunkenSword:xxx`（模组效果）。写错时会报错并列出当前所有可用的通用效果。
**角色专属/机制性效果不能通过命令施加**——判定用的是效果自身的标签
（`EffectTags.UNIVERSAL`，`Effect.isUniversal()`），没有这个标签就拒绝，
所以「官方内容里那些只属于某个角色的效果」不会被 `/effect` 挂到别人身上。
`OfficialCommands` 在注册时会把这个可用列表打进日志（`latest.log`），方便对名字。

**括号里数字的含义＝构造函数的参数个数**（不会有第二种解释）：

```
/effect @s add AttackEnhance(0.2,3)      2 个参数 = 只给百分比 → 3 回合内攻击 +20%
/effect @s add AttackEnhance(0,2,3)      3 个参数 = 百分比,固定值,回合 → 3 回合内攻击 +2 点
/effect @s add AttackEnhance(0.5,3,3)    3 回合内攻击 +50% 且 +3 点
/effect @s add CriticalDMGEnhanceEffect(1,5)    暴击伤害 +100%（1.0 = 100%）
```

同一组数字如果能同时匹配两个构造函数，命令会**直接报错并列出候选**，不会替你猜；
每次添加的回显里也会写出实际用了哪个构造函数（例如
`已对 1 个目标添加效果 attackEnhanceEffect（按构造函数 AttackEnhance(double,int) 创建 (0.2, 3)）`）。

**`/execute as <目标> run <命令>`**：只换「执行者」，不换战斗范围 ——
内层命令里的 `@s` 指向被指定的那个目标，`@p` / `@n` / `@r` 也改成以它为中心来找，
而 `@a` / `@e` 看到的仍是同一场战斗里的生物，所以不会跑到别的战斗里去。

```
/execute as @e[type=CommonInsect] run kill @s               每只普通虫杀死自己
/execute as @p run hurt @s 10                               把这 10 点伤害算到最近的生物头上
/execute as @e[type=CommonInsect] run effect @s add frozen  给每只虫子挂冰冻
/execute as @s run list                                     等价于 /list
```

- 目标有多个时**逐个各执行一次**，返回值是各次影响对象数之和（与 MC 一致）；
- 内层命令失败时，由**最外层**的 `execute` 包一句「以『谁』的身份执行『什么』失败：<真正的原因>」再抛出
  （多目标时能看出是哪一个目标出的问题）；嵌套的里层直接原样抛出 ——
  每层都包的话，深层嵌套的报错会叠成一长串，真正的原因被埋在最里面；
- `execute` 自己套自己最多 **8 层**，超过直接报错（否则会一路递归到 `StackOverflowError`）；
- 实现方式：`run` 后面是**贪婪字符串**（吃掉整行），执行体再把它交回调度器解析
  —— 所以 `run` 后面能写任意命令，命令树里不需要再描述一遍所有命令。

**`/give <目标> <物品> [数量]`**：把物品放进目标的背包。

```
/give @s aNiceSword                                短名（官方物品直接这么写）
/give @s game_official_content:aNiceSword 3        完整 id + 数量（谁都认）
/give @s ANiceSword 2                              简单类名（同样只解析官方内容）
/give @s attackPotion 3                            效果药水（8 瓶：攻击/防御/生命/迅捷/暴击/暴击伤害/穿甲/治疗）
/give @s drunkenSword:osmanthusWine 2              模组物品：必须带模组前缀
```

- 名字规则见上面的**命名空间规则**表：完整 id 谁都认，短名/类名只解析官方内容，
  写模组物品的短名会被拒绝并提示该写的完整 id；
- **数量会叠进同一格**：同种物品按注册表 id 判等（`Item.equals`），`Inventory.addItem`
  会叠到已有的那一格上，`stackNumber` 累加、没有上限 —— `/give @s aNiceSword 100` 只占 1 格；
- **使用物品只消耗 1 个**：`PlayerController.useItem` 走 `Inventory.removeOne`（扣 1 点堆叠数，
  扣到 0 清空格子），而不是 `removeItem`（那个按物品自带的 `stackNumber` 扣掉一整叠）；
- 发的是 `注册表模板.copy()`，所以物品带**完整的注册表 id**
  （这就是 `ANiceSword.copy()` 必须走拷贝构造器的原因）；
- 背包格子不够：能发多少发多少，回显里说明有几个没发出去；
  目标没有背包格子（普通虫子、冰虫子没初始化背包）→ 直接报错。

**`/summon <实体> [阵营]`**（2026-09-26 新增）：往**当前战斗**里召唤一个生物。

```
/summon CommonInsect                 不写阵营 = 我方（默认）
/summon commonInsect enemy           丢到对面去
/summon CommonInsect 敌方             中文别名也认（cmd.exe 打不出中文，Git Bash / VS Code 终端可以）
/summon completeContainer            同类的另一条模板：短名精确区分
/summon drunkenSword:drunkenSwordsman  模组角色：必须带模组前缀
```

- **名字规则与 `/give` 同源**（完整 id 谁都认；短名/类名只解析官方内容，写模组的短名会提示该写什么），
  但多了一层优先级：**完整 id > 短名 > 类名**。
  原因很实际：【残破容器】与【完整容器】是同一个类 `BrokenContainer`，短名不同、**类名相同** ——
  两层同优先级的话 `/summon brokenContainer` 会被后者的类名撞成"假歧义"。
- **阵营**：`our`（默认）/ `ally` 与我方同义，`enemy` / `foe` 与敌方同义，
  另外接受中文 `我方` / `敌方`（与 `/endfight` 的 `win`/`胜利` 是同一套做法）。
  写别的会报错并告诉你可填什么。
- **不在战斗里直接报错**（"现在不在战斗中，没法召唤"），不会静默什么都不做 ——
  本命令是往 `Fight` 的两个阵营列表里放东西，没有战斗就没有地方放。
- 召唤的是**注册表模板的 `copy()` 副本**，所以反复召唤互不影响；
  副本走 `Fight#addFighter/addEnemy` 入列（**id 补全**与**排进时间轴**都在那里面），
  之后**显式补一次入场初始化**（`setParticipateFight` + `whenFightStart`）——
  `whenFightStart` 只在开局由 `FightStartEventListener` 遍历一次，中途加入的实体收不到
  （与 `FlameReaver#summonContainer` 同款处理，自测有一条断言盯着）。
- 实体类没重写 `copy()` 时，报错会说明是哪个类（而不是把 `RuntimeException` 甩到控制台）。
- **不做数量/强度限制**：给自己叫一只 BOSS、给对面塞一只虫皇都可以，这是调试性质的命令。

**实体选择器**（写在需要目标的位置）：

```
@s                            执行者自己（玩家当前选的角色）
@p / @n / @r                  最近 / 最远 / 随机 一个生物
@a / @e                       全部生物
@e[type=CommonInsect]         按类型筛选（简单类名或中文名，大小写不敏感）
@e[name=白厄]                  按名字筛选（支持 * 通配）
@e[limit=2]                   最多 2 个
@e[sort=nearest]              排序：nearest / furthest / random / arbitrary
@e[type=CommonInsect,limit=2,sort=nearest]   可以组合
```

例子：

```
/kill @e[type=CommonInsect]
/hurt @s 100
/hurt @p -50
/list @e[type=Phainon]
/effect @s add frozen
/effect @s add DamageEnhanceEffect 2 5
/effect @s add CriticalDMGEnhanceEffect(1,5)
/effect @s remove frozenEffect
/effect @s list
/execute as @e[type=CommonInsect] run kill @s
/execute as @p run hurt @s 10
/give @s aNiceSword
/give @s game_official_content:aNiceSword 3
/endfight lose
```

---

## 四、怎么测

### 方式 A：先跑自测程序（推荐，最快）

它会自己造两个假生物、跑十几条命令并逐条核对结果，**不启动游戏、不进回合循环**，可以反复运行。

在项目根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\test-command-system.ps1
```

（脚本 `test-command-system.ps1` 做三件事：编译整个 `src` 到 `out\cmdtest` → 运行自测 →
打印结果；编译失败会直接停下并给出日志路径。）

不想用脚本的话，手动两步等价于：

```powershell
$files = Get-ChildItem -Recurse src -Filter *.java | ForEach-Object { $_.FullName }
javac -encoding UTF-8 -d out/cmdtest -classpath lib/json-20231013.jar $files
java -Dfile.encoding=UTF-8 -cp "out/cmdtest;lib/json-20231013.jar" cn.gfhnv.debug_tools.TestCommandSystem
```

IDEA 里直接在 `TestCommandSystem` 上点运行也可以。

**预期**：最后一行是 `========== 自测结束：通过 N 条，失败 0 条 ==========`，退出码 0。
任何一条 `[FAIL]` 都说明有问题，把那一行连同前后几行贴给 AI 即可。

**中文乱码怎么办**：程序已经在进程内把 `System.out` 切成 UTF-8，脚本也会先执行 `chcp 65001`。
如果仍然乱码，说明控制台字体/代码页有问题，可以先手动 `chcp 65001` 再运行，
或者把输出重定向到文件再看：`... > out\test.log 2>&1`，然后用 UTF-8 打开这个文件。

### 方式 B：进游戏手测

```powershell
gradle shadowJar
```

然后用仓库根目录的启动脚本（**推荐，尤其是想用中文选择器时**）：

```
启动游戏-UTF8.bat
```

它等价于：

```bat
chcp 65001
java -Dfile.encoding=UTF-8 -jar build\libs\FightGameReforged-1.2.1.jar
```

> **为什么需要它**：命令系统支持中文参数名与筛选值（`@e[type=虫皇]`、`@e[name=白厄]`），
> 而 Windows 的 `cmd.exe` 默认是 GBK（代码页 936）。直接 `java -jar` 启动时，
> 你敲的中文会在**进入程序之前**变成 `???`，表现为「生物名明明打印正常，但选择器就是选不中」。
> 游戏原本的输入（`yes`/`no`/`next`/数字）全是 ASCII，所以这个问题只在中文命令上暴露。
> 自测程序会打印一行「编码自检」，`CommandManager.canReceiveChinese()` 也可以在运行时自检。
>
> 不想折腾编码就用 ASCII 选择器：`@e[type=CommonInsect]`、`@e[type=InsectBoss]`、
> `@e[type=IceInsect]`、`@e[type=Phainon]`（简单类名不区分大小写，中文名只是额外支持）。
>
> **⚠️ 不要在这个 `.bat` 里写中文**：`cmd.exe` 按控制台 OEM 代码页（中文系统是 936/GBK）
> 解析批处理文件本身，文件里的 UTF-8 中文会把它**下一行**也读坏
> （现象：`'ause' 不是内部或外部命令`、`'TF-8' 不是内部或外部命令`、
> 甚至 `系统找不到指定的路径`）。所以脚本内容**全 ASCII**，
> 中文说明只放在本 `.md` 里；真正切换编码靠运行时的 `chcp 65001`。

#### 中文命令：已确认的终端限制（放弃在 cmd 里支持）

实测数据（JDK 25 / Windows 10 / cmd.exe）：

```
Charset.default = UTF-8      file.encoding  = UTF-8
native.encoding = GBK        stdin.encoding = UTF-8
System.console() = 可用       console.charset() = UTF-8   isTerminal = true

输入「虫皇」：
  System.console().readLine() → 码点 U+0030 U+0030   ❌
  System.in + defaultCharset  → 码点 U+0030 U+0030   ❌
正确值应当是 虫=U+866B 皇=U+7687
```

结论：**JVM 侧一切正常，但中文在 Windows 控制台的原生层就被替换掉了**
（不同配置下实测出现过 `??`、`��`(U+FFFD)、`PP`、空格 四种形态），
`System.in` 与 `System.console()` 两条路径都拿不到，Java 侧无法修复。
因此**不要再在 cmd 里尝试中文命令**，也**不要**为此再加解码容错
（信息已丢失，容错救不回来）。

**日常用 ASCII 选择器，功能完全等价**：

| ASCII 类名（推荐） | 对应生物 |
|---|---|
| `@e[type=PlayerOne]` | 玩家一 |
| `@e[type=ActorLiXiaoYan]` | 李晓焰 |
| `@e[type=Phainon]` | 白厄 |
| `@e[type=InsectBoss]` | 虫皇 |
| `@e[type=CommonInsect]` | 普通虫子 |
| `@e[type=IceInsect]` | 冰虫子 |

中文名/id 匹配本身**实现好了且有自测覆盖**（自测里 `@e[name=普通虫子]` 能选中虫子），
只是 cmd 送不进来；换 Windows Terminal / IDEA 运行通常可用。

1. **选人/选敌人/选奖励阶段**——输入 `/help`，应当列出 9 行命令（`kill / list / hurt / effect / execute / give / endfight / help / ?`）且**不会**被当成「输入错误」。
2. 选一个角色加入队伍后输入 `/list`，应当看到这个角色的名字与 HP。
3. 战斗开始时输入 `/list`，应当列出双方所有生物。
4. 轮到你行动时（提示「输入前方数字使用」）输入 `/hurt @s 100`，应当掉血；然后再输入技能编号，流程应当继续正常。
5. `/endfight` 应当结束战斗并回到「要不要再玩一局?」；接着选 `yes` 开**第二局**，确认回合能正常推进（这一条专门验证「回合驱动没有被卡住」）。
   > **注意**：`/endfight` 要在**玩家回合的技能选择提示**下输入。实测过的一个 bug 是——
   > 命令执行完、战斗也结算了，但 `PlayerController.act()` 还在等输入，玩家会被留在技能选择界面里。
   > 现在 `PlayerController` 每个输入点后都会检查 `fightIsOver()`，战斗一结束就立刻退出。
   > **实测结果（2026-09）**：`/endfight` 后直接回到「要不要再玩一局?」（不再要求选技能），
   > 第二局回合正常推进，第二局里用 `/kill @e[type=InsectBoss]` 击杀也能正常结算 —— 三条都通过。
   > 唯一的小瑕疵：`/endfight` 后要多按一次回车（见上面「踩过的坑」里的说明，不影响功能）。
6. 输入 `/kill @e[type=CommonInsect]` 杀死敌方小怪，战斗应当正常判定胜负。
7. **效果命令**（战斗中，随便哪个阶段有 `@s` 能选中自己就行）：
   - `/effect @s add frozen` → 提示「已对 1 个目标添加效果 frozenEffect...」
   - `/effect @s list` → 能看到 `frozenEffect 等级 1 剩余 N 回合`
   - `/effect @s add CriticalDMGEnhanceEffect(1,5)` → 按构造函数参数创建（这条专门验证「参数从括号里传」）
   - `/effect @s add AttackEnhance(0,2,3)` → 3 个参数 = 百分比,固定值,回合（验证合并后的构造函数）
   - `/effect @s remove all` 之后再 `/effect @s list`：确认攻击力等加成已经还回去了（不是只清了列表）
   - `/effect @s add memorizedHp` → 应当被拒绝并列出可用效果（角色专属效果没有 `UNIVERSAL` 标签）
   - `/effect @s remove all` → 提示清空了 N 个效果；再 `/effect @s list` 应当说「身上没有任何效果」
8. **execute 命令**（战斗中）：
   - `/execute as @e[type=CommonInsect] run hurt @s 10` → 掉血的是**虫子**，不是你自己
   - `/execute as @s run list` → 输出与 `/list` 一样
   - `/execute as @e[type=CommonInsect] run execute as @s run hurt @s 1` → 嵌套也能跑，最里层 `@s` 仍是虫子
   - `/execute as @s run` → 报错「命令不完整：/execute <目标> run」
   - 连写 9 层 `execute as @s run` → 报「execute 嵌套超过 8 层」，游戏不崩
9. **give 命令 + 效果药水**（战斗中）：
   - `/give @s aNiceSword` → 提示「已发放 aNiceSword（一把剑）×1」
   - `/give @s game_official_content:aNiceSword 3` → 再发 3 件（背包里应当是 4 件、**占 1 格**，显示 `一把剑 x4`）
   - `/give @s attackPotion 3` → 3 瓶攻击药水占 1 格
   - 轮到你行动时用一次物品 → 只消耗一件（另外 3 件还在），并挂上对应效果（`/effect @s list` 能看到）
   - `/give @e[type=CommonInsect] aNiceSword` → 报错「目标没有背包格子」（虫子没初始化背包）
   - `/give @s noSuchItem` → 报错并列出可用物品（现在应当列出 9 件）
10. **summon 命令**（战斗中；2026-09-26 新增）：
    - `/summon CommonInsect` → 提示「已在我方召唤 commonInsect（普通虫子），HP 4670/4670」，
      用 `/list` 能看到场上多了一只，而且**下个回合它真的会行动**（时间轴已排好）
    - `/summon commonInsect enemy` → 换成敌方；`/list` 里它站在对面（攻击行会标 `（敌方）`）
    - `/summon CommonInsect 敌方` → 中文别名（在 Git Bash / VS Code 终端里试；cmd 打不出中文）
    - `/summon completeContainer` → 召唤【完整容器】（20000 血），与 `brokenContainer`（12000 血）能区分开
    - `/summon BrokenContainer` → 也能用（忽略大小写命中短名 `brokenContainer`）
    - `/summon NoSuchEntity` → 报错并列出可用实体（9 条：playerOne … completeContainer）
    - `/summon CommonInsect 中间派` → 报错「只能填 our 或 enemy（也接受 我方/敌方）」
    - `/summon drunkenSword:drunkenSwordsman` → 模组角色（**装了醉剑仙模组才有**）；
      写短名 `drunkenSwordsman` 会被拒绝并提示该写的完整 id
    - 对自己人用 `/hurt` 或者让召唤物打一架，确认它**不会打自己人**
      （召唤物进的是召唤者那一侧的阵营列表，见 `Fight#getOpponentList` 的口径）
11. 故意写错，确认报错信息带定位与用法：
   - `/nosuch` → `未知的命令：nosuch。你是不是想输入：...`
   - `/kill` → `命令不完整：/kill <目标>`
   - `/kill @e[bad=1]` → `未知的筛选键「bad」...`
   - `/hurt @s abc` → `「abc」不是一个合法的长整数: ...<--[HERE]`
   - `/effect @s add frozen(1` → `构造函数参数没有用右括号闭合：frozen(1`
12. 输入普通文本 `yes` / `no` / `next` / `数字`，确认一切与改动前一样。

## 五、已经踩过的坑

- **★ `/endfight` 必须能被「正在等输入」的玩家侧感知到**（唯一的集成级 bug，实测踩到）：
  `/endfight` 多半是在**玩家回合内**执行的，而此时 `PlayerController.act()` 正卡在
  `SCANNER.nextLine()` 上等输入。命令执行完、战斗也结算完了，但 `act()` 并不知道，
  于是玩家被留在一个「已经没有战斗」的技能选择界面里：
  ```
  /endfight
  恭喜你在战斗中获得了胜利...
  本场战斗已被命令强制结束（按玩家胜利处理）。
  yes                     ← 用户以为回到主菜单了
  输入错误，请输入技能编号    ← 其实还卡在技能选择循环里
  ```
  修法：`PlayerController` 里新增 `fightIsOver()`（就是 `!GameMain.isInFight()`），
  在**每一个输入点之后**都检查一次，战斗一结束就立刻 `return`，
  让回合循环能正常退出、控制权回到主循环。目前覆盖了 5 个输入点
  （是否用物品 / 技能编号 / 目标索引 / 使用物品，以及方法入口）。
  **已知小瑕疵**：`/endfight` 之后需要**多按一次回车**——因为 `act()` 是先 `return`，
  那一次多余的空输入随后被循环消费掉时才触发 `fightIsOver()` 退出。
  功能上没有影响（不会卡死、不会错乱），要修就得给每个 `nextLine()`
  加一个「本行是结束战斗的命令」的返回机制，改动面大于收益，故保持现状。
- **★ 建树铁律：一层一个变量，最后只把最外层交给 `addChild`**
  （多级命令唯一出过错的地方，`/hurt <目标> <数值>` 与整个 `/effect` 都因此丢过层）：
  `literal(...)` / `argument(...)` 返回的是**新建出来的那个子节点**（建树时就已经挂好），
  所以把长链直接当 `addChild` 的参数，挂上去的其实是**最内层**：
  ```java
  // ❌ 错：挂上去的是「数值」那一层 → 「目标」整层不在树里
  ArgumentBuilder target = ArgumentBuilder.argumentBuilder("目标", ...).argument("数值", ...);
  root.addChild(target);

  // ✅ 对：外层留住变量，内层从外层长出来
  ArgumentBuilder target = ArgumentBuilder.argumentBuilder("目标", ...);
  ArgumentBuilder amount = target.argument("数值", ...);
  amount.executes(...);
  root.addChild(target);
  ```
  症状很好认：命令树 dump 里查不到应该有的那一层（`[effect] 子节点=[add, 效果, list]`，
  没有「目标」），运行时则是**所有带参数的写法**都报「命令无法继续解析」。
  自测里已经加了「官方命令：effect 下应挂着『目标』」等断言把这条钉住。
- **★ 判断「字面量 / 参数」不能用 `instanceof`，要用 `CommandNode.isLiteralNode()`**
  （和上一条同时踩到，是「树搭对了却依然解析不了」的第二个原因）：
  `ArgumentBuilder` 本身就是节点，既可能表示字面量分支，也可能表示参数分支，
  但它既不是 `LiteralCommandNode` 也不是 `ArgumentCommandNode`。
  解析器原先写的是 `child instanceof LiteralCommandNode` / `instanceof ArgumentCommandNode`，
  于是构建器建出来的分支**两个判断都不匹配**：字面量匹配轮跳过它，参数匹配轮也跳过它。
  现在改为能力判定（`LiteralCommandNode` → `true`，`ArgumentBuilder` → 有没有参数类型），
  补全里的三处判断同样改掉了。
- **构建器建出来的字面量分支也必须能自己吃输入**：`ArgumentBuilder.parse()` 对字面量分支
  要和 `LiteralCommandNode.parse()` 一样（跳空白 → 读一个词 → 大小写不敏感比对），
  不能只抛一句「这是字面量，不能按参数解析」——解析器是真的会调用它来吃掉这一层的。
- **同一个分支不要建两遍**：`addChild` 按名字合并同名节点（新的执行体覆盖旧的、
  子节点并进旧节点），第二次建出来的那个对象会被丢弃，再往它上面挂子分支就等于没挂。
  `/effect` 的 `add / remove / list` 因此改成从**同一个**「目标」节点上长出来，
  而不是三条链各建一个「目标」。
- **可见性规则（踩了两次）**：Java 覆写方法时**不能降低可见性**。
  最终固定为 `public build()` + `protected buildNode()`（`Command` 里两者都在）。
  `Command` 里那两个静态便捷方法 `argument(...)` / `literal(...)` 已经删掉：
  它们和继承来的实例方法 `literal(String)` / `argument(String, ArgumentType)` 同名，
  调用处极容易看错成「在建子分支」。
- **`CommandManager.sendError` 只接受 `CommandSyntaxException`**，不要传字符串
  （`CommandSender.sendError(String)` 是另一个重载）。
- **`stripPrefix()` 会 `trim()`，别拿它的结果判断「尾随空白」**：
  补全需要知道玩家是否刚敲了空格（`"kill "` 表示准备输入下一个词），
  但对 `stripPrefix()` 的结果调 `endsWith(" ")` **恒为 false**，
  于是补全永远不提示下一步。要判断原始 `input`。
- **UTF-8 BOM（Java 源码禁写）**：用 PowerShell 的 `Set-Content -Encoding UTF8` 批量替换
  Java 源码会**写入 BOM**，javac 直接报 `非法字符: '\ufeff'`，一个文件能刷出十几个错误。
  真要用就读字节去掉前 3 个字节 `EF BB BF` 再写回（仓库已全量扫描，`src` 下没有残留）。
- **UTF-8 BOM（PowerShell 脚本反而必需）**：`.ps1` 里有中文注释时必须带 BOM，
  否则 Windows PowerShell 5 按 GBK 解析，报 `The string is missing the terminator`。
- **PowerShell 5 会拆开带点的裸参数**：`java -Dfile.encoding=UTF-8 ...` 里的
  `-Dfile.encoding=UTF-8` 会被按 `.` 拆成两个参数，于是 java 报
  `找不到或无法加载主类 .encoding=UTF-8`。脚本里已改成数组传参（`& java @javaArgs`）；
  手工敲命令时用引号包住即可。
- **Gradle/IDEA 的旧 class 会骗人**：`build\classes\...` 里可能留着旧版本的
  `ArgumentBuilder.class`，用 IDEA 直接跑自测时会用到它，表现为「源码明明改了却还是旧行为」。
  排查时请用 `test-command-system.ps1`（它每次重新编译到 `out\cmdtest`），
  或在 IDEA 里先 `Build → Rebuild Project`。
- **注册表里的 id 带模组前缀，运行时实例原本是短名**（已修）：`Mod.addEffect()` 只会把
  <b>被注册的那个模板</b>改成 `game_official_content:frozenEffect`，
  而效果类构造器里写死的是短名 `frozenEffect`、技能召唤出来的生物也是短名。
  后果是「按 id 找模板 / 按 id 删效果 / 按 id 判定同类效果」全都对不上
  （`/effect @s remove frozenEffect` 会变成「成功但一个也没删」；
  `Effect.equals` 按 id 判定，还会让同种效果的叠加/刷新失效）。
  **现在改成运行时也保持完整 id**：`World` 提供
  `fullIdOf(Entity/Item/Effect)` 与 `applyRegisteredId(...)`，在三个入口调用 ——
  `World.addThing()`（选人、奖励）、`Fight.addFighter/addEnemy()`（技能召唤）、
  `LivingThing.addEffect()`（**必须在 `equals` 合并判定之前**）。
  已经是完整 id（含 `:`）的不动，所以想给实例单独起 id 就写成 `myMod:xxx`。
  命令侧的 `matches()` / `sameEffect()` 前缀容错**保留**，作为「没注册进 World 的自定义内容」的兜底；
  但提示与回显仍打印短名（`/effect list` 会显示完整 id，方便核对）。
- **自测不启动游戏，效果注册表默认是空的**：`World.getEffectList()` 由
  `OfficialGameContent.registerItself()` 填充（真实游戏在 `GameStartEvent` 之后才走这一步）。
  `/effect` 的候选全部来自这张表，不填就会报「效果注册表里没有…」，
  `templateOf()` 也会一路返回 `null`。自测里已手动走一遍同样的流程。
- **★ 物品判等必须按「注册表 id」，不能按 uuid，否则堆叠是死代码**：
  `Thing.equals` 比的是 uuid，而 uuid 是构造时生成的 `final` 字段、拷贝构造器也不复制它，
  所以每个 `copy()` 出来的副本都不相等 → `Inventory.addItem` 里那段
  「背包已有同种物品就合并堆叠」永远走不到 → 一件一格、`stackNumber` 恒为 1
  （实测：`/give` 6 把剑 = 6 格；63 格背包给 100 件只放得下 63 件）。
  现在 `Item.equals/hashCode` 改成按注册表 id 比较（没有 id 的退回按名字+描述+阵营），
  实体仍然按 uuid 比（两只同种生物是两个个体，不该合并）；
  同时「使用物品」从 `removeItem`（按物品自带的 `stackNumber` 扣一整叠）改成
  新增的 `Inventory.removeOne`（只扣 1，扣到 0 清空格子）。
  注意 `World.removeItem(Item)` / `Mod.removeItem(Item)` 用 `contains` 判等，
  改完之后传一个副本就能移除注册表模板（目前这两个方法没有调用点）。
- **★「同一个数字能匹配两个构造函数」要在内容那边消掉，不要靠命令猜**：
  通用效果原本是「百分比」和「固定值」两个 2 参数构造器
  （{@code (double percent,int)} 与 {@code (long amount,int)}），
  于是 {@code AttackEnhance(1,1)} 两个都能匹配（int 既能转 long 也能转 double），
  `getConstructors()` 的返回顺序又没有规定 —— 也就是「1 是 1.0 还是 1L」全看 JVM 心情。
  现在这 5 个效果（AttackEnhance / HpEnhanceEffect / SpeedEnhanceEffect /
  CriticalRateEnhanceEffect / CriticalDMGEnhanceEffect）改成
  **把可能混的数值并进同一个构造函数**：
  ```java
  public AttackEnhance(double percent, long amount, int lastTime)  // 完整版（3 参数）
  public AttackEnhance(double percent, int latTime) { this(percent, 0, latTime); }  // 简写（2 参数）
  ```
  参数个数不同 → 永远不会互相匹配；写 2 个参数就一定是百分比，要固定值就写满 3 个。
  命令侧也加了兜底：匹配到多个构造函数时**报错并列出候选**，不再「拿到哪个用哪个」；
  回显里会写明用了哪个构造函数。自测里有断言守护「这类效果各只有 1 个双参数 + 1 个三参数构造函数」。
- **`/effect ... remove all` 现在会让效果正常收尾**：原来直接 `clear()` 列表，
  像「+N 攻击」这种把加成写进属性的效果会**永久留在身上**（效果没了、属性还是增强过的）。
  现在会先对每个效果调 `whenLastTimeEnd()` 再清空，和单条 `remove` 的行为一致。
- **★ 通用效果的<b>每一个</b>构造函数都要加 `UNIVERSAL` 标签，拷贝构造器尤其容易漏**：
  `isUniversal()` 是标签判定（与 `isInfinity()` 同一套做法），而实体复制走的是
  `effect.copy()` → 拷贝构造器。漏一行，副本在运行时就会被当成「非通用」，
  将来任何「按通用性筛选」的逻辑都会漏掉它。10 个通用效果已全部补齐
  （`DamageEnhanceEffect` 的拷贝构造器连 `POSITIVE` 也一起补了，
  `DefenseEnhanceEffect(String,int,int)` 这个新建实例的构造器同样补了）。
  自测里加了「每种通用效果的 `copy()` 副本都保留 UNIVERSAL 标签」与「通用效果共 10 种」，
  以后新增效果忘了加标签会直接报红。

---

## 六、给自己写命令的模板

### 写法一：直接建树

```java
public class MyCommand extends Command {
    public MyCommand() { super("my"); }

    @Override
    protected CommandNode buildNode() {
        LiteralCommandNode root = node();

        // /my —— 没有参数的分支
        root.setExecutor((context, source) -> {
            source.sendMessage("你好，" + source.getName());
            return 1;
        });

        // /my <目标> <数值>：一层一个变量
        ArgumentBuilder target = ArgumentBuilder.argumentBuilder("目标", EntityArgumentType.entities());
        ArgumentBuilder amount = target.argument("数值", IntegerArgumentType.integer(1, 999));
        amount.executes((context, source) -> {
            List<LivingThing> targets = context.getLivingThings("目标");
            int value = context.getInt("数值", null, 0);
            for (LivingThing t : targets) t.setHp(t.getHp() - value);
            source.sendMessage("影响了 " + targets.size() + " 个目标");
            return targets.size();
        });
        root.addChild(target);   // 只挂最外层

        return root;
    }
}
```

注册：`CommandManager.register(new MyCommand());`（模组可以在 `invokeWhenLoaded()` 里调用）

### 写法二：注解 + 方法

```java
public class TeamCommand extends CommandRegistration {
    public TeamCommand() { super("team"); }

    @Subcommand("add")
    public CommandBuilder add(CommandBuilder builder) {
        return builder.argument("目标", EntityArgumentType.entities());
    }

    @Subcommand("add")
    public int addRun(CommandContext context, CommandSource source) throws CommandSyntaxException {
        source.sendMessage("加入队伍：" + EntityArgumentType.describe(context.getEntities("目标")));
        return 1;
    }
}
```

---

*本文档由 AI 生成，仅供参考。*
