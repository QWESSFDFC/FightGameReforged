中文:
这是一个使用java编写的游戏.目前只有文字,大概能正常玩了?代码随便用,随便改.我是高中生,没时间.偶尔更新.编写此项目只是为了图一乐,发到Github上纯粹是闲的没事.目前没有使用java的相关游戏引擎,只是自己写东西,自娱自乐.......
暂不接受 Pull Request。如果你有改进想法，请 fork 本仓库后自行修改，自由使用。我只想自己写点东西玩玩.
使用方法:直接运行.jar文件.可以自己编译或者下载Release中编译好的.但是Release中版本可能落后一点.
我使用了AI(DeepSeek)写了部分代码.本项目的Javadoc都是ai写的，而且我没有核对，可能有问题.
其中命令系统（`system/command/` 与 `officialStuff/customCommands/` 和模组）的代码、Javadoc 与说明文档
全部由 AI 编写，详见下面的命令系统一节与 `project_analyses/COMMAND-SYSTEM-2026-08.md`。
ai代码具体标注
行动系统我写了第一版(可以正常运行),ai修复了bug,更新了架构(我写的是事件递归).
盗火行者是ai写的代码.
白厄第一版是我写的.ai改了bug
李晓焰(这也是这个项目第一个复杂角色)是ai提供的设计,我写的,之后ai修了bug.
技能也是我写的 
AI代码里面好像都标注了(作者DeepSeek)
思考系统（`system/thinkingSystem/`）也是 AI 写的，其中 `ThinkingControllerAI` 目前还没有任何生物在用，
实际生效的还是随机行动的 `UniversalController`。
感觉ai编程太好使了!可以实现自己不会的东西.说起来,这个项目ai代码含量还挺高的.不过我个人感觉无所谓.很多有技术力的东西都是ai写的.我是根据玩过的游戏提出的需求,ai实现
下面是使用AI写的README.md

> 📌 说明：下面这一部分（到「许可证」为止）由 AI 通读**当前源码**后重写，
> 依据是代码本身（v1.2.1，`src/` 下 149 个 .java 文件）。
> 作者只会偶尔抽空核对，**如有出入请以源码为准**；项目结构一节还可能列漏文件夹。

---

# FightGameReforged

一个由高中生从零编写的**命令行回合制文字战斗游戏** —— 纯 Java 实现，事件驱动架构，时间轴回合制，支持动态编译加载模组。

---

## 📖 项目简介

这是一个基于 Java 的命令行回合制战斗游戏。玩家可以组建队伍、选择敌人与奖励，手动操控角色释放技能，在文字界面中体验策略对战的乐趣。

项目采用事件驱动架构，通过自定义 `EventBus` 和 `@SubscribeEvent` 注解解耦游戏逻辑；回合推进不是简单的"你一下我一下"，而是基于速度的行动时间轴（`TurnManager`），所以"加速、延迟、额外回合"这类机制实现起来很自然。同时内置模组系统，支持在运行时用 `javax.tools.JavaCompiler` 动态编译 `mods/` 下的 `.java` 源码并加载外部模组，方便添加新生物、技能与物品。

作者是一名热爱编程与游戏开发的高中生，写这个项目纯粹为了图一乐。代码随便用，随便改，欢迎 fork。暂不接受 Pull Request。

---

## ✨ 核心特色

- **经典回合制战斗**：玩家自由组建队伍、挑选敌人与奖励，手动操控每个角色释放技能。
- **时间轴行动条**：`TurnManager` 用 `BigDecimal` 以 `10000 / 速度` 作为行动间隔，靠"推进 / 延迟"实现加速减速与额外回合，而不是固定轮流。
- **五行元素体系**：金、木、水、火、土（`ElementSort`），每个生物有对应的元素抗性、增伤与穿透，同时五种元素各自是一份独立的 Mana 资源。（注意：**没有"相生相克"的循环克制表**，伤害只看「攻击者自身元素」对应的那一条抗性；抗性可以是负数，负抗性就是弱点。）
- **事件驱动架构**：自定义 `EventBus` + `@SubscribeEvent(priority = ...)` 注解，通过反射注册监听器。（默认优先级 `3`，数字越小越优先，最小 `0`；事件支持 `cancel`。）
- **内置模组系统**：自动扫描 `mods/` 目录，解析 `main.json`，动态编译 `code/` 下的源码并加载，方便添加新生物、技能与物品。
- **Utility AI 控制器**：`ThinkingControllerAI` 基于 Tag 权重系统做决策——每个实体拥有独立的 Tag 权重（体现性格），结合实时情境（血量、蓝量、负面效果、预测伤害等）计算每个行为的得分，选出最优解。**注意：目前还没有任何生物实际使用它**，官方怪物走的仍是随机 AI。
- **命令系统**（AI 编写，参考《我的世界》Java 版）：输入以 `/` 或 `#` 开头的命令即可调试战斗，支持实体选择器、参数类型、错误定位与 Tab 补全建议。
- **MIT 开源许可**：代码完全开放，随意使用、修改、分发。

---

## 🎮 怎么玩

1. 启动后先输入你的名字。
2. 依次**选角色 → 选敌人 → 选奖励**，三个环节都是同一套操作：
   - 输入列表里**名字前面的数字**选中一项，会打印它的介绍；
   - 输入 `yes` 加入队伍 / 加入敌方 / 加入奖励，输入 `no` 返回上一步；
   - 输入 `next` 进入下一个环节，输入 `quit` 直接退出游戏。
   （角色和敌人可以选任意多个，选够之后会自动进入下一环节。）
3. 战斗开始后，轮到你的角色行动时：
   - 先问 `是否使用物品?(yes/no)`，用物品**不占用**本回合释放技能的机会；
   - 然后列出可用技能，**输入技能前面的数字**使用，同时会显示剩余冷却；
   - 需要选目标时输入目标前的数字，多目标就反复输入，最后输入 `next` 结束选择（至少选 1 个，不能重复选同一个）。
4. 一局打完会问 `要不要再玩一局?`，输入 `yes` 继续、`no` 退出。

任何时候都可以直接敲命令（见下一节），命令和上面的普通输入互不干扰。

---

## ⌨️ 命令系统

> 这一部分（含代码与文档）由 AI（DeepSeek）编写，作者未逐条核对。

在游戏原有输入方式**完全不变**的前提下，额外支持以 `/` 或 `#` 开头的命令（两个前缀等价）。例如战斗中轮到你行动时，可以直接输入命令，然后继续正常选择技能。

内置命令：

| 命令 | 说明 |
|---|---|
| `/help` 或 `/?` | 列出所有命令 |
| `/help <命令名>` | 查看某条命令的用法 |
| `/list [目标]` | 列出当前战斗中的生物状态（HP/攻防速/存活） |
| `/kill <目标>` | 把目标生命值清零 |
| `/hurt <目标> <数值>` | 改生命值，正数扣血、负数回血 |
| `/effect <目标> list` | 列出目标身上的效果（等级/剩余回合/正面负面） |
| `/effect <目标> add <效果> [等级] [持续回合]` | 加效果，效果模板取自效果注册表（模组效果要写完整 id） |
| `/effect <目标> add <效果>(参数,…)` | 按构造函数参数新建效果实例，如 `AttackEnhance(0.2,3)` |
| `/effect <目标> remove <效果>\|all` | 移除某个效果，`all` 清空 |
| `/execute as <目标> run <命令>` | **以指定对象的身份**运行另一条命令（内层 `@s` 指向它） |
| `/data get entity <目标> [路径]` | 看实体的「数据」（NBT）：不给路径就 dump 全部，给路径只取一个值，如 `hp`、`effects[0].level` |
| `/data merge entity <目标> <NBT>` | 按 NBT 合并改字段，如 `{hp:100}`、`{ignition:12}`（走 setter，所以会被钳制） |
| `/data modify entity <目标> <路径> set\|merge\|append\|prepend\|insert <值>` | 改到深处：路径能带下标、`{k:v}` 过滤与 `[a:b]` 切片 |
| `/data … storage <存储位> …` | 同一套操作作用在**内存里的全局数据**上（退出游戏就没了） |
| `/execute if data entity\|storage … run <命令>` | 条件执行：那条数据存在才跑后面的命令 |
| `/give <目标> <物品> [数量]` | 发物品（数量默认 1） |
| `/summon <实体> [阵营]` | 往当前战斗里召唤一个生物；阵营 `our`（默认）/ `enemy` |
| `/endfight [win\|lose]` | 强制结束战斗（默认按玩家胜利结算） |

物品名与效果名都遵循**「官方内容可以写短名，模组内容必须写完整 id」**（模仿 MC 的命名空间，大小写不敏感）：

```
/give @s aNiceSword                                短名（官方物品直接这么写）
/give @s game_official_content:aNiceSword 3        完整 id + 数量（谁都认）
/give @s ANiceSword 2                              类名（同样只解析官方内容）
/give @s attackPotion                              效果药水（见下）
/give @s drunkenSword:osmanthusWine 2              模组物品：必须带模组前缀
```

写模组物品的短名会被拒绝，并提示该写的完整 id；敲不完整的命令（例如只敲 `/give`）会直接给出
`/give <目标> <物品> [数量]` 这样的完整用法。

`/summon` 用**同一套命名规则**，并且可以选择召唤到哪一边（不写就是自己这边）：

```
/summon CommonInsect                叫一只普通虫子来帮自己
/summon commonInsect enemy          把虫子丢到对面去
/summon completeContainer 敌方       中文的「我方/敌方」也认
/summon drunkenSword:drunkenSwordsman   模组角色：必须带模组前缀
```

召唤出来的是**注册表模板的副本**，会正常排进时间轴（下个回合就能行动），
所以给自己叫一只 BOSS 或者给对面塞一只虫皇都是可以的 —— 这是调试性质的命令，不做强度限制。

官方物品一共 9 件：一把剑（`aNiceSword`）+ 8 瓶**效果药水**（使用后给自己挂一个效果）。
除治疗药水是立刻回血外，其余 7 瓶都是持续 3 回合的增益：

| 物品 | 效果 |
|---|---|
| `attackPotion` 攻击药水 | 攻击 +20% |
| `defensePotion` 防御药水 | 防御 +30% |
| `hpPotion` 生命药水 | 生命上限 +20% |
| `speedPotion` 迅捷药水 | 速度 +20% |
| `criticalRatePotion` 暴击药水 | 暴击率 +20% |
| `criticalDMGPotion` 暴击伤害药水 | 暴击伤害 +50% |
| `piercingPotion` 穿甲药水 | 无视目标 50% 防御 |
| `healingPotion` 治疗药水 | 立刻回复 210 点生命 |

同种物品**叠在一格**（按注册表 id 判定，堆叠数没有上限），所以 `/give @s aNiceSword 100` 只占 1 格；
使用物品时只消耗 1 个，不会把整叠一起扣掉。背包格子不够时能发多少发多少，
回显里会说明有几个没发出去；目标没有背包格子（例如普通虫子）会直接报错。

`/execute as` 只换「执行者」，不换战斗范围（和 MC 一样）：

```
/execute as @e[type=CommonInsect] run kill @s          让每只普通虫杀死自己
/execute as @p run hurt @s 10                          把这 10 点伤害算到最近的生物头上
/execute as @e[type=CommonInsect] run effect @s add frozen   给每只虫子挂冰冻
/execute as @s run list                                以自己身份看状态（等价于 /list）
```

目标有多个时会**逐个各执行一次**，返回值是各次影响数之和；`execute` 自己套自己最多 8 层，
超过会报错（不会递归到栈溢出）。

### NBT 与 `/data`（实验性，模仿 MC）

游戏对象可以像 MC 那样被"当数据看"——用一套 NBT 标签（复合/列表/数字/字符串）表示：

```
/data get entity @s                        dump 自己的全部数据（一整个复合标签）
/data get entity @s hp                     只取一个字段
/data get entity @e[type=FlameReaver] disasterPower
/data get entity @s effects[0].level       列表里的元素也能取
/data get entity @s inventory.slots[{slotNumber:0L}]     {k:v} 过滤：挑出字段匹配的那个元素
/data get entity @s manas[0:2]             切片：取前两个（切片只能读）
/data merge entity @s {hp:100}             改字段
/data merge entity @s {ignition: 12}       括号里可以有空格
/data modify entity @s manas[3].amount set 500        改到深处（路径带下标）
/data modify entity @s effects[0].level set 3         列表元素里的字段也能改
/data modify entity @s effects[0].effectTagsList append POSITIVE   往标量列表里塞一个
/data merge storage 我的计数 {kill:1}       内存里的全局数据（退出就没了）
/data modify storage 我的计数 kill set 5
/execute if data entity @s hp run list     条件执行：有 hp 这个数据才跑
/execute if data storage 我的计数 kill run hurt @s 1
```

选择器里也能按数据筛（`nbt=`），并且是**标签精确比较**：

```
@e[nbt={level:125L}]              按 long 字段筛（写 125 会当成 int，筛不到）
@e[type=FlameReaver,nbt={phaseTwo:1b}]   type= 与 nbt= 都要满足
```

几条与 MC 一致/不一致的规矩：

- 数字后缀沿用 MC：`20L`（长整）、`1b`（字节/布尔）、`0.25d`（双精度）；
- **字符串可以不写引号**（`{name:白厄}` 能解析）——这条是放宽，方便手敲；
- **只认"数据"不认"行为"**：controller、监听器、物理对象、事件回调这些**不会**出现在数据里，
  否则 `@s` 会 dump 出几千行内部实现（技能表也因此改不到，它挂在 controller 上）；
- **写回能走 setter 就走 setter**：`{hp:99999999}` 会被生命上限钳住，回显里能看到真实结果；
- `final` 字段（如 `uuid`）只读；**对象列表不能增删**（效果请用 `/effect`），
  标量列表（数字/字符串/枚举）可以用 `append`/`prepend`/`insert`；
- **过滤只支持单键**、**切片只能读**、`/data remove` **没做**（想删东西用专门的命令）；
- `storage` 是**内存**的：退出游戏就清空（存档这块明确不做）。

细节与红线见 `TIPS_FOR_LLM.md` §5.10，可行性分析见 `project_analyses/NBT-AND-DATA-COMMAND-2026-09.md`。

效果名可以写注册表里的 **id** 或**类名**（大小写不敏感）：`frozen`、`frozenEffect`、
`damageEnhanceEffect`、`CriticalDMGEnhanceEffect(1,5)`、`taunt`（嘲讽：让对手优先打你，
配合怪物 AI 的 `TargetStrategies.tauntAware(...)` 生效）。
和 `/give` 同一套命名空间规则：**官方效果写短名即可，模组效果必须写完整 id**
（例如 `drunkenSword:xxx`），写短名会被拒绝并提示该写什么。
角色专属/机制性效果（没有 `EffectTags.UNIVERSAL` 标签）**不能**用 `/effect` 施加，
写错名字时会报错并列出当前所有可用的通用效果。

括号里的数字含义由**参数个数**决定，不会有第二种解释：

```
/effect @s add AttackEnhance(0.2,3)     2 个参数 = 只给百分比 → 3 回合内攻击 +20%
/effect @s add AttackEnhance(0,2,3)     3 个参数 = 百分比,固定值,回合 → 3 回合内攻击 +2 点
/effect @s add AttackEnhance(0.5,3,3)   3 回合内攻击 +50% 且 +3 点
/effect @s add CriticalDMGEnhanceEffect(1,5)   暴击伤害 +100%（1.0 = 100%）
```

同一组数字如果能同时匹配两个构造函数，命令会**直接报错并列出候选**，不会替你猜；
每次添加的回显里也会写出实际用了哪个构造函数。

实体选择器（写在需要目标的位置）：

```
@s                            执行者自己（玩家当前选的角色）
@p / @n / @r                  最近 / 最远 / 随机 一个生物
@a / @e                       全部生物（含自己队伍，与 MC 语义一致）
@e[type=InsectBoss]           按类型筛选（简单类名，不区分大小写）
@e[name=*虫*]                  按名字筛选（支持 * 通配）
@e[type=CommonInsect,limit=2,sort=nearest]    可组合：type / name / limit / sort
```

**⚠️ cmd.exe 里请使用 ASCII 类名**（中文控制台输入会被 Windows 原生层丢掉，实测 `System.in` 与
`System.console()` 两条路径都拿不到，Java 侧无法修复）。对应关系：

| ASCII 类名 | 生物 | | ASCII 类名 | 生物 |
|---|---|---|---|---|
| `PlayerOne` | 玩家一 | | `InsectBoss` | 虫皇 |
| `ActorLiXiaoYan` | 李晓焰 | | `CommonInsect` | 普通虫子 |
| `Phainon` | 白厄 | | `IceInsect` | 冰虫子 |

中文名与 id 匹配本身是**实现好且有自测覆盖**的（`@e[name=普通虫子]` 在自测里能选中虫子），
只是 cmd 送不进来；换 Windows Terminal / IDEA 运行通常可用。

想给自己的模组加命令，看 `project_analyses/COMMAND-SYSTEM-2026-08.md`：
那里有完整说明、两种注册写法（直接建树 / `@Subcommand` 注解）与踩坑清单。
一行接入现有代码也只要：`if (CommandManager.process(input)) { continue; }`

---

## 🛠️ 技术栈

| 项目 | 说明 |
|---|---|
| 语言 | Java（JDK 25） |
| 构建工具 | Gradle（`com.gradleup.shadow` 8.3.0 打 fat jar） |
| 唯一依赖 | `org.json:json:20240303` |
| 版本 | 1.2.1 |
| 程序入口 | `cn.gfhnv.game.GameStarter` → `cn.gfhnv.game.GameMain.main` |
| 游戏引擎 | 没有，全部手写（事件总线、时间轴、物理、命令系统都是自制的） |

编译时统一使用 UTF-8（`build.gradle` 里对 `JavaCompile` 和 `JavaExec` 都做了设置），因为项目里有大量中文文本。

---

## 🚀 快速开始

### 方式一：直接运行 JAR（推荐）

1. 前往 Releases 下载最新 `.jar` 文件。
2. 在终端中执行：

   ```bash
   java -jar FightGameReforged.jar
   ```

   或者在本仓库根目录用启动脚本（会先 `chcp 65001` 切到 UTF-8 控制台，并在 `build\libs\` 下自动找 jar）：

   ```bat
   启动游戏-UTF8.bat
   ```

⚠️ Release 中的版本可能略落后于主分支，如需最新特性请参考方式二。

### 方式二：从源码编译运行

```bash
git clone https://github.com/QWESSFDFC/FightGameReforged.git
cd FightGameReforged
gradle shadowJar            # 产物：build/libs/FightGameReforged-1.2.1.jar
java -Dfile.encoding=UTF-8 -jar build/libs/FightGameReforged-1.2.1.jar
```

也可以直接用 IntelliJ IDEA 打开项目，运行主类 `cn.gfhnv.game.GameStarter`。
模组功能需要 **JDK**（而不是只装 JRE）才能动态编译 `.java` 源码。

### 方式三：免安装的 Windows exe（jpackage，非官方发布方式）

作者本地用 `jpackage` 打过一份带运行时镜像的 app-image：

```bat
build-output\FightGameReforged\FightGameReforged.exe
```

对应的打包命令在 `build-output\build.bat` 里（输入目录 `build-input\`，主 jar 为 `FightGameReforged.jar`）。
`build-output/` 与 `build-input/` 都在 `.gitignore` 里，属于本地产物，仓库中不一定有。

### 自带一份示例模组

📁 `mods/` 下有两个可以直接参考的示例模组：

- `exampleModByGFHNV` —— 最小模组骨架（`Mod` 子类 + 调用另一个类的方法）；
- `abstractLaunchingWords` —— 只重写 `invokeWhenLoaded()` 打印几行"抽象启动词"。

---

## 🧱 写一个自己的模组（怎么用模组系统）

> 📘 **完整写法见 [`MODDING-GUIDE.md`](MODDING-GUIDE.md)**（目录约定、`Mod` API、
> 实体/技能/效果/物品各自的模板、14 条踩坑清单、可照抄的完整示例）。
> 下面只是最小骨架。

模组就是 `mods/` 下的一个文件夹，结构固定：

```
mods/我的模组/
├── main.json                 # 模组信息（必须有）
├── code/                     # 你的 .java 源码，包名要和目录层级对上
│   └── com/example/mymod/mainClass.java
└── bin/                      # 编译输出，游戏会自动生成/覆盖，别手改
```

`main.json` 五个字段**都必须有**（`mainClass` 要写完整包名，且**不包含** `code` 这一层）：

```json
{
  "name": "我的模组",
  "author": "你",
  "description": "描述",
  "mainClass": "com.example.mymod.mainClass",
  "version": "1.0"
}
```

主类必须继承 `cn.gfhnv.game.mod.Mod`，并且提供一个**接收 `ModInformation` 的构造器**：

```java
package com.example.mymod;

import cn.gfhnv.game.mod.Mod;
import cn.gfhnv.game.mod.ModInformation;

public class mainClass extends Mod {
    public mainClass(ModInformation modInfo) {
        super("myModId", modInfo);          // 第一个参数是模组 ID，会作为内容 ID 的前缀
    }

    @Override
    public void invokeWhenLoaded() {
        // 在这里把内容加进模组自己的 List，不要像官方内容那样在构造器里直接注册
        // addEntity(...) / addItem(...) / addEffect(...)
        // 想加命令：CommandManager.register(new MyCommand());
    }
}
```

加载流程：`ModLoader` 扫描 `mods/` → 读 `main.json` → 用 `javax.tools.JavaCompiler`
把 `code/` 下所有 `.java` **编译到 `bin/`（每次加载都会先删掉重建）** → 用 `URLClassLoader`
加载主类并实例化 → 加入 `World` → 收到 `GameStartEvent` 时调用 `invokeWhenLoaded()` 与 `registerItself()`。

几个注意点：

- **必须用 JDK 运行**（`ToolProvider.getSystemJavaCompiler()` 返回 `null` 时会提示"请确保在 JDK 环境下运行"）；
- 内容 ID 会自动加上 `<模组ID>:` 前缀（例如 `myModId:mySword`），避免和别的内容撞名；
- 模组内容是在**每次开一局之前**就注册进全局注册表的，改完源码重启游戏即可生效，没有热重载；
- 某个模组编译失败只会打印错误并跳过它，不会影响其它模组和游戏本体。

**写模组最容易踩的坑**：

- **`copy()` 必须重写**。`LivingThing` / `Skill` / `Item` 的基类 `copy()` 是
  `throw new RuntimeException("请重写此方法..类" + ...)`，官方子类全都重写了；
  你的实体/技能/物品子类忘了重写，一进战斗就会炸（开局选人是靠 `copy()` 生成实例的）。
- **自定义控制器会被"降级"**。`LivingThing` 的复制构造器只认 `PlayerController`、
  `ThinkingControllerAI` 和 `FixOrderController`，其它一律按 `UniversalController` 重建——
  所以自己写的控制器子类复制后会变成随机控制器。
- **别指望给父类事件注册监听器**。`EventBus` 是精确类匹配，`@SubscribeEvent` 也只扫本类方法（见下方"各个系统都在哪"）。
- **`canUse(fight, user, null)` 真的会传 `null`**：控制器判断"这招能不能放"时第三个参数就是
  `null`，重写 `canUse` 时别解引用它。

### 已经写好的模组示例

`mods/drunkenSword/`（「醉剑仙」）是一个可直接照抄的完整例子：新角色「酒剑仙」+
【醉意】层数资源 + 2 个效果 + 2 件物品，全部内容都在 `invokeWhenLoaded()` 里注册。
文件清单、数值与玩法见 [`MODDING-GUIDE.md`](MODDING-GUIDE.md) §7。

> ⚠️ 模组的源码是在**同一个 JVM、同一权限**下编译并立刻执行的，没有沙箱
> （可以读写文件、联网、`System.exit`）。**安装模组 = 授予该模组与游戏同等的权限，请只加载你信得过的源码。**

---

![运行示意图](./screenshots/图1.PNG)

---

## 📁 项目结构

```
FightGameReforged/
├── src/cn/gfhnv/
│   ├── game/
│   │   ├── GameStarter.java      # 程序入口（打印作者信息 + 写日志）
│   │   ├── GameMain.java         # 主流程：读名字 / 选角色敌人奖励 / 开战 / 再玩一局
│   │   ├── Thing.java            # 最底层基类（物理属性 + Tag 权重 + UUID）
│   │   ├── annotation/           # 自定义注解（@SubscribeEvent，默认优先级 3）
│   │   ├── damage/               # 伤害计算与元素抗性/穿透（DamageCalculate 一站式公式）
│   │   ├── effect/               # 战斗效果基类（Buff / Debuff）
│   │   ├── entity/               # Entity / LivingThing（1843 行的核心大杂烩）/ Player
│   │   ├── entityController/     # 控制器：PlayerController（玩家）/ UniversalController（随机）/ FixOrderController
│   │   ├── event/                # 事件总线与事件定义（EventBus + 各 XxxEvent）
│   │   ├── eventListener/        # 监听器：开局 / 开战 / 回合推进 / 结束 / 效果 / 物理
│   │   ├── interfaces/           # IModifyDamage、IDefenceIgnore、ITaunt、TargetStrategy、IShowSpecialMes、ISpecialAction、IInitialize
│   │   ├── inventory/            # 背包与格子（Slot，支持堆叠合并）
│   │   ├── item/                 # 物品基类
│   │   ├── mod/                  # 模组加载器（ModLoader / Mod / ModInformation / JavaSourceCode）
│   │   ├── officialStuff/        # 官方内容：8 个生物（含 BOSS 盗火行者与残破容器）、24 个技能类、17 个效果、9 件物品（1 把剑 + 8 瓶效果药水）、官方命令
│   │   ├── skill/                # 技能基类（倍率 / 目标数 / 冷却 / 消耗 Mana / Tag）
│   │   ├── system/               # 各类子系统（详见下方）
│   │   │   ├── command/          # 命令系统（AI 编写：参数类型 / 选择器 / 命令树 / 调度器 / 补全）
│   │   │   ├── configLoadingSystem/  # 读取 config/gameConfig/*.json，给实体注入 AI Tag 权重
│   │   │   ├── fight/            # Fight / TurnManager（时间轴）/ TurnEntry / ActionSignal
│   │   │   ├── logSystem/        # LogWriter（日志滚动归档）
│   │   │   ├── mana/             # 五行 Mana 资源
│   │   │   ├── physics/          # 手写的简单牛顿力学（Vector + Force/Velocity/Acceleration/Position）
│   │   │   ├── thinkingSystem/   # Tag / TagType / ThinkingController / ThinkingControllerAI（Utility AI）
│   │   │   ├── ElementSort.java  # 金木水火土（+ UNIVERSAL）
│   │   │   └── useItemSystem/    # 空包（"使用物品"的系统还没做，预留位置）
│   │   ├── data/                 # NBT 数据层（NbtTag / Snbt / DataPath / DataBridge）—— /data 命令用它
│   │   ├── utils/                # JSONHelper（org.json 薄封装）、ConsoleColor（ANSI 着色，不引第三方库）
│   │   └── world/                # World：全局注册表（实体/物品/效果/模组/运行时对象）
│   └── debug_tools/              # 调试与自测程序（不需要玩就能跑：命令系统自测、预期伤害试算、行动条实验）
├── mods/                # 外部模组目录（两个示例模组 + 「醉剑仙」完整示例；各模组的 bin/ 是编译产物）
├── config/gameConfig/   # TagConfig.json（AI Tag 权重）/ PropertyConfig.json
├── project_analyses/    # 分析文档与命令系统说明（当前基准：PROJECT-ANALYSIS-2026-09.md；其余为历史轮次）
├── screenshots/         # 运行截图
├── out/                 # javac/gradle 的临时输出（自测脚本用它）
├── MODDING-GUIDE.md             # 模组编写指南（目录约定 / API / 模板 / 踩坑 / 完整示例）
├── test-command-system.ps1      # 命令系统自测脚本（编译整个 src + 跑全部自测断言）
├── 启动游戏-UTF8.bat             # 启动脚本（切 UTF-8 控制台；内容纯 ASCII）
├── build.gradle / gradle.properties
└── README.md
可能还有没有列出的文件夹
```

---

## 🧩 各个系统都在哪

| 想改什么 | 去看哪里 |
|---|---|
| 战斗回合怎么推进 | `eventListener/FightTurnPastListener.java`、`system/fight/TurnManager.java` |
| 伤害公式与元素抗性 | `damage/DamageCalculate.java`（公式写在类注释里） |
| 角色/怪物/技能/物品 | `officialStuff/` 下对应的 `customXxx/` 子包 |
| 事件有哪些、谁在监听 | `event/`、`eventListener/` |
| 加载外部模组 | `mod/ModLoader.java`、`mod/Mod.java` |
| 想写一个自己的模组 | `MODDING-GUIDE.md`（完整指南）、`mods/drunkenSword/`（可照抄的示例） |
| 命令怎么写 | `system/command/`、`officialStuff/customCommands/`、`project_analyses/COMMAND-SYSTEM-2026-08.md` |
| NBT / 数据读写（`/data`） | `data/`（`NbtTag`/`Snbt`/`DataPath`/`DataBridge`）、`officialStuff/customCommands/DataCommand.java`、`TIPS_FOR_LLM.md` §5.10 |
| AI 怎么做决策 | `system/thinkingSystem/`、`config/gameConfig/TagConfig.json` |
| 想给项目做体检 | `project_analyses/PROJECT-ANALYSIS-2026-09.md`（**当前基准**，含旧缺陷的逐条复核）；其余几份是历史轮次，注意看文档开头的时效说明 |
| 想做 NBT / `/data` 命令 / 存档 | `project_analyses/NBT-AND-DATA-COMMAND-2026-09.md`（可行性分析，**尚未实现**） |

**写监听器前请先知道 EventBus 的三个特点**（踩坑预警）：

- **精确类匹配**：`EventBus.post()` 是按 `event.getClass()` 查表的，注册父类事件的监听器**收不到子类事件**；
- **不扫描父类方法**：注册时用的是 `getDeclaredMethods()`，所以监听器**不支持继承**（写在父类里的 `@SubscribeEvent` 不会生效）；
- 优先级默认 `3`、数字越小越先执行，同优先级按注册顺序；`Event.setCanceled(true)` 之后当前和后续监听器都会被跳过。
- 顺带一提：目前全项目 10 处 `@SubscribeEvent` **全都没写 priority**（都是默认值），所以"优先级"暂时还没有实际使用案例。

### 回合是怎么走的（简版）

`FightStartEvent` → `FightTurnPastListener` 进入 `turnLoop` 循环：

```
剔除死者 → 判定胜负（任一方空则发 FightEndEvent 并退出）
  → 按「开始时间 + 需要时间」排序，取出最近要行动的那个
  → 推进当前时间 → 全员 updateSelf() / 技能冷却 -1 / 回蓝
  → 打印状态 → 执行首动作 → 控制器 act() → 安置新回合 → 执行末动作
  → 发布 EffectUpdateEvent → 回到循环开头
```

栈深度**不随回合数增长**（是循环而非递归），所以长战斗不会爆栈。

整场战斗是**同步**跑完的：`GameMain.startAFight()` 发出 `FightStartEvent` 之后，
控制权就交给了这个循环，一直打到分出胜负才返回主菜单（所以战斗命令才有机会"插队"进输入流程）。

供外部（例如模组）改回合节奏的钩子：`ActionSignal`（NORMAL / SPECIAL_ACTION / WITHOUT_NEW_TURN / SKIP / SKIP_WITHOUT_NEW_TURN）与 `TurnManager` 的 `advanceByPercent` / `delayByAmount` 等。

---

## 🧠 关于 AI 决策系统（`thinkingSystem`）

> ⚠️ 现状要先说清楚：**官方生物目前一个都没用上 Utility AI**。
> `PlayerOne`、`ActorLiXiaoYan`、`Phainon` 用 `PlayerController`（玩家手操），
> `CommonInsect`、`IceInsect`、`InsectBoss` 用 `UniversalController`（随机选技能 + 随机选目标）。
> `ThinkingController` 是没写完的半成品（算完一堆权重后依旧直接 `super.act()`）；
> 真正写完的是 AI 写的 `ThinkingControllerAI`，但还没接到任何生物身上。

`ThinkingControllerAI` 的设计思路（AI 写的，作者未逐条核对）：

1. 没有 Tag 或没有可用技能 → 回退到父类的随机行动；
2. 读取实体自己的 Tag 权重（`ATTACK` / `DEFENCE` / `HEAL` / `RESTORATION_MANA` / `DAMAGE_ENHANCE`），这些权重来自 `config/gameConfig/TagConfig.json`，体现"性格"；
3. 结合实时情境动态修正权重：
   - 预测到自己下回合会被秒杀 → 治疗权重 ×10、防御 ×5；
   - 血量 >60% 且敌人 ≥2 → 攻击权重 ×1.5；
   - 总蓝量 <30% → 回蓝权重 ×3；
   - 身上有负面效果 → 防御权重 ×2；
4. 选出权重最高的策略 Tag，再从可用技能里挑出属于该 Tag 的技能；
5. 对"技能 × 目标"组合逐个打分（`evaluateAction`）：基础分 = Tag 权重，再按目标残血程度、能否击杀、是否已有增伤 Buff、蓝耗等加减分，取最高分的组合执行。

这种设计的优势在于：

- **解耦**：Tag 权重与行动逻辑分离，新增行为只要加 Tag 和分数计算。
- **可扩展**：支持"心情指数"、临时修正、随机扰动等进阶玩法。
- **好调试**：决策过程可以打印成日志，方便定位"它为什么这么打"。

想让它真正生效，把生物构造器里的 `new UniversalController(...)` 换成
`new ThinkingControllerAI(...)`（并在 `TagConfig.json` 里给它配上 Tag）即可。
注意在此之前，`TagConfig.json` 里的权重对实际玩法**没有任何影响**。

### 一些"写好了但还没接上线"的东西

| 东西 | 现状 |
|---|---|
| `ThinkingControllerAI` | Utility AI 已实现，但没有任何生物在用（见上） |
| `ThinkingController` | 没写完，`act()` 算完权重后直接 `super.act()`，等同死代码 |
| `FixOrderController` | **现在已接上线**：盗火行者与残破容器按预设顺序出招（开了 `setSkipUnusable(true)`，用不了的招顺延）；复制构造器也能识别它（`LivingThing` 的控制器重建表），不会再降级成随机控制器 |
| `system/useItemSystem/` | 空包；实际的物品使用逻辑写在 `PlayerController.useItem()` 里，`UniversalController.useItem()` 是空方法 |
| 物理系统（`system/physics/`） | 数据结构齐全，但 `PhysicsStateUpdateEvent` 全项目从未被 post，`PhysicsEventListener` 因此从不触发 |
| `ActEvent` | 既没有发布方，也没有监听器 |
| `TurnManager.nextTurn()` | 只 post 一个 `FightPastOneTurnEvent`，没有任何调用方（推进由回合循环自己 `continue`） |
| 暴击系统 | 没有任何实体设置基础暴击率 → `Math.random() <= 0`，**实际永远不会暴击** |

### 顺手发现的小毛病（不影响玩，但改的时候别踩）

- `Skill.use()` 两个重载设冷却的方式不一致：三参版是 `coolDown + 1`，两参版是 `coolDown`，同一个技能走哪条路结果会差 1 回合。
- `debug_tools/TestAnticipateDamage` 的入口写成了 `static void main()`（缺 `public` 和 `String[] args`），**不能用 `java` 直接跑**；其它几个调试类没有这个问题。
- 如果出现"双方都还有人、但时间轴排不出回合"的情况，回合循环会兜底 `break`——**不发 `FightEndEvent`**，此时 `fightInProgress` 仍为 `true`、本场监听器也没注销，再开一局理论上会有两个回合监听器同时在场。（代码注释自称"宁可少打一个回合也不要崩"。）

---

## 🤝 贡献与反馈

报告 Bug / 提出建议：欢迎提交 Issue，我会尽量抽空查看，但可能无法及时响应或修复（毕竟学业繁忙,学校太不做人了）。

代码贡献：暂不接受 Pull Request。如果你有改进想法，请 fork 本仓库后自行修改，自由使用。

---

## 📄 许可证

本项目采用 MIT License 开源协议，代码完全开放，随意使用、修改、分发。

---

作者：一名热爱编程与游戏开发的高中生 | 项目始于 2025 年 9 月 1 日
