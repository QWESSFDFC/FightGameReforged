# 命令系统（AI 编写）· 说明与测试指南

> 本文档由 AI 生成，仅作参考，请以源码为准。
> 命令系统的**全部新增代码与 Javadoc 都由 AI（DeepSeek）编写**，作者未逐条核对。

---

## 一、这次做了什么

参考《我的世界》Java 版（Brigadier + CommandSourceStack）把命令系统补齐，**没有引入任何第三方库**（只用了 JDK 与项目已有的 `org.json`）。

### 新增/重写的文件

```
src/cn/gfhnv/game/system/command/          ← 命令框架（17 个类）
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
├── CommandNode.java             节点基类 + CommandExecutor 函数式接口
├── LiteralCommandNode.java      字面量节点
├── ArgumentCommandNode.java     参数节点
├── ArgumentBuilder.java         链式建树（参数构建器）
├── LiteralCommandBuilder.java   链式建树（字面量构建器）
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
├── OfficialCommands.java        统一注册
├── KillCommand.java             /kill   重写（原先是空实现 + 一调用就抛异常）
├── ListCommand.java             /list
├── HurtCommand.java             /hurt
├── EndFightCommand.java         /endfight
└── HelpCommand.java             /help 与 /?

src/cn/gfhnv/debug_tools/TestCommandSystem.java   ← 自测程序（可单独运行）
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
| `/endfight` | 强制结束战斗（默认按玩家胜利结算，会发奖励） |
| `/endfight lose` | 强制结束战斗并按失败结算 |

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

1. **选人/选敌人/选奖励阶段**——输入 `/help`，应当列出 6 条命令（含 `?`）且**不会**被当成「输入错误」。
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
7. 故意写错，确认报错信息带定位与用法：
   - `/nosuch` → `未知的命令：nosuch。你是不是想输入：...`
   - `/kill` → `命令不完整：/kill <目标>`
   - `/kill @e[bad=1]` → `未知的筛选键「bad」...`
   - `/hurt @s abc` → `「abc」不是一个合法的长整数: ...<--[HERE]`
8. 输入普通文本 `yes` / `no` / `next` / `数字`，确认一切与改动前一样。

### 已经踩过的坑

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
- **★ 建树铁律：`build()` 必须作用在「要挂上去的那一层」上**
  （多级参数命令唯一出过错的地方，`/hurt <目标> <数值>` 因此丢掉了整层「目标」）：
  节点是在**父构建器的 `build()` 里**才被挂到父节点上的，所以下面两种写法差别巨大：
  ```java
  // ❌ 错：build 的是「数值」那一层，root 收到的是「数值」节点 → 「目标」整层消失
  root.addChild(argument("目标", ...).argument("数值", ...).executes(...).build());

  // ✅ 对：先搭出「数值」，再 build「目标」那一层
  ArgumentBuilder target = argument("目标", ...);
  target.argument("数值", ...).executes(...);
  root.addChild(target.build());
  ```
  症状很好认：**多参数命令只剩最后一层参数**，解析时把第一个参数值当成了后一个参数的类型
  （`/hurt @s 100` → 「`@s` 不是一个合法的长整数」）。
  单层参数的命令（`/kill <目标>`、`/list <目标>`、`/endfight <结果>`、`/help <命令名>`）
  恰好怎么写都对，所以这个坑很容易漏掉。
- **可见性规则（踩了两次）**：Java 覆写方法时**不能降低可见性**。
  最终固定为 `public build()` + `protected buildNode()`（`Command` 里两者都在，
  `ArgumentBuilder` 里只有 `public build()`）。
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

        // /my hit <目标> <数值>
        root.addChild(argument("目标", EntityArgumentType.entities())
                .argument("数值", IntegerArgumentType.integer(1, 999))
                .executes((context, source) -> {
                    List<LivingThing> targets = context.getLivingThings("目标");
                    int amount = context.getInt("数值", null, 0);
                    for (LivingThing t : targets) t.setHp(t.getHp() - amount);
                    source.sendMessage("影响了 " + targets.size() + " 个目标");
                    return targets.size();
                })
                .build());

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
