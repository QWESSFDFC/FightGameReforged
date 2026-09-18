中文:
这是一个使用java编写的游戏.目前只有文字,大概能正常玩了?代码随便用,随便改.我是高中生,没时间.偶尔更新.编写此项目只是为了图一乐,发到Github上纯粹是闲的没事.目前没有使用java的相关游戏引擎,只是自己写东西,自娱自乐.......
暂不接受 Pull Request。如果你有改进想法，请 fork 本仓库后自行修改，自由使用。我只想自己写点东西玩玩.
使用方法:直接运行.jar文件.可以自己编译或者下载Release中编译好的.但是Release中版本可能落后一点.
我使用了AI(DeepSeek)写了部分代码.本项目的Javadoc都是ai写的，而且我没有核对，可能有问题.
其中**命令系统**（`system/command/` 与 `officialStuff/customCommands/`）的代码、Javadoc 与说明文档
全部由 AI 编写，详见下面的「命令系统」一节与 `project_analyses/COMMAND-SYSTEM-2026-08.md`。

下面是使用AI写的README.md


---

FightGameReforged

一个由高中生从零编写的命令行回合制文字战斗游戏 —— 纯 Java 实现，事件驱动架构，支持模组加载。

---

📖 项目简介

这是一个基于 Java 的命令行回合制战斗游戏。玩家可以组建队伍、选择敌人、手动操控角色释放技能，在文字界面中体验策略对战的乐趣。

项目采用事件驱动架构，通过自定义 EventBus 和 @SubscribeEvent 注解解耦游戏逻辑，为后续扩展打下基础。同时内置了模组系统，支持动态编译
.java 源码并加载外部模组，方便添加新生物、技能与物品。

作者是一名热爱编程与游戏开发的高中生，写这个项目纯粹为了图一乐。代码随便用，随便改，欢迎 fork.
暂不接受 Pull Request。如果你有改进想法，请 fork 本仓库后自行修改，自由使用。

---

✨ 核心特色

· 经典回合制战斗：玩家自由组建队伍，选择敌人与奖励，手动操控每个角色释放技能。
· 五行元素体系：引入金、木、水、火、土五种元素，设计了对应的抗性与伤害加成机制。
· 事件驱动架构：通过自定义 EventBus 和 @SubscribeEvent(priority=...)或者@SubscribeEvent 注解解耦游戏逻辑，为扩展性打下基础。默认优先级3.数字越小,优先级越高,数字最小是0.
· 内置模组系统：可自动扫描并加载外部模组，支持动态编译 .java 源码，方便添加新生物、技能与物品。
· Utility AI 控制器：非玩家角色基于 Tag 权重系统进行决策——每个实体拥有独立的 Tag 权重（体现性格），结合实时情境（血量等）计算行动得分，选出最优行为。
· 命令系统（AI 编写，参考《我的世界》Java 版）：输入 `/` 或 `#` 开头的命令即可调试战斗，支持实体选择器、参数类型、错误定位与 Tab 补全建议。
· MIT 开源许可：代码完全开放，随意使用、修改、分发。

---

⌨️ 命令系统

> 这一部分（含代码与文档）由 AI（DeepSeek）编写，作者未逐条核对。

在游戏原有输入方式**完全不变**的前提下，额外支持以 `/` 或 `#` 开头的命令。例如战斗中轮到你行动时，可以直接输入命令，然后继续正常选择技能。

内置命令：

| 命令 | 说明 |
|---|---|
| `/help` 或 `/?` | 列出所有命令 |
| `/help <命令名>` | 查看某条命令的用法 |
| `/list [目标]` | 列出当前战斗中的生物状态（HP/攻防速/存活） |
| `/kill <目标>` | 把目标生命值清零 |
| `/hurt <目标> <数值>` | 改生命值，正数扣血、负数回血 |
| `/endfight [win\|lose]` | 强制结束战斗（默认按玩家胜利结算） |

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

🛠️ 技术栈

项目 说明
语言 Java (JDK 25+)
构建工具 Gradle
核心依赖 org.json

---

🚀 快速开始

方式一：直接运行 JAR（推荐）

1. 前往 Releases 下载最新 .jar 文件
2. 在终端中执行：
   ```bash
   java -jar FightGameReforged.jar
   ```

⚠️ Release 中的版本可能略落后于主分支，如需最新特性请参考方式二。

方式二：从源码编译运行

1. 克隆仓库：
   ```bash
   git clone https://github.com/QWESSFDFC/FightGameReforged.git
   ```
2. 使用 IntelliJ IDEA 打开项目
3. 运行主类：cn.gfhnv.game.GameStarter
4. 按照命令行提示开始游戏

📁 项目包含2个示例模组 exampleModByGFHNV和abstractLaunchingWords,位于 mods/ 目录下，可作为模组开发参考。

---
![运行示意图](./screenshots/图1.PNG)

📁 项目结构

```
FightGameReforged/
├── src/cn/gfhnv/game/
│   ├── annotation/      # 自定义注解（如 @SubscribeEvent）
│   ├── damage/          # 伤害计算与元素克制系统
│   ├── effect/          # 战斗效果（Buff/Debuff）
│   ├── entity/          # 实体（角色、怪物）
│   ├── entityController/# 控制器（不含 Utility AI 决策系统）
│   ├── event/           # 事件总线与事件定义
│   ├── inventory/       # 背包系统
│   ├── item/            # 物品定义
│   ├── mod/             # 模组加载器
│   ├── officialStuff/   # 官方内容（预设角色/技能/命令）
|   └── system           #战斗系统,思考系统,log系统,mana,甚至是简单的物理
│       └── command/     # 命令系统（AI 编写，参考 MC：参数类型/选择器/命令树/调度器）
├── mods/                # 外部模组存放目录
├── project_analyses/    # 分析文档与命令系统说明
├── test-command-system.ps1      # 命令系统自测脚本（编译 + 运行，55+ 条断言）
├── 启动游戏-UTF8.bat             # 启动脚本（切 UTF-8 控制台；内容纯 ASCII）
└── README.md
可能还有没有列出的文件夹
```

---
这个功能没写完 
🧠 关于 AI 决策系统（ThinkingController）

非玩家实体的行为由 ThinkingController 控制，其核心机制基于 效用型 AI（Utility AI）：

· 每个实体拥有独立的 Tag 权重表（如攻击、防御、治疗、回蓝、增伤等），体现其性格。
· 每回合控制器会读取实体当前的 Tag 权重，并结合实时情境因子（血量百分比、蓝量、敌人距离、元素克制等）计算每个行动的最终得分。
· 系统自动选出得分最高的行动执行，实现智能且风格各异的 NPC 行为。

这种设计的优势在于：

· 解耦：Tag 权重与行动逻辑分离，新增行为只需添加对应 Tag 和分数计算。
· 可调试：决策过程可打印为日志，方便定位 AI 行为异常的原因。
· 可扩展：支持“心情指数”、临时修正、随机扰动等进阶玩法。

---

🤝 贡献与反馈

报告 Bug / 提出建议：欢迎提交 Issue，我会尽量抽空查看，但可能无法及时响应或修复（毕竟学业繁忙,学校太不做人了）。

代码贡献：暂不接受 Pull Request。如果你有改进想法，请 fork 本仓库后自行修改，自由使用。

---

📄 许可证

本项目采用 MIT License 开源协议，代码完全开放，随意使用、修改、分发。

---

作者：一名热爱编程与游戏开发的高中生 | 项目始于 2025 年 9 月 1 日


English
This is a game written in Java. For now, it's text‑only, and I guess it's mostly playable? Use and modify the code however you like. I'm a high school student, so I don't have much time. I update it occasionally. I wrote this project just for fun, and uploading it to GitHub was purely because I had nothing better to do. It doesn't use any Java game engine — I just write my own stuff for my own amusement.
The Javadoc in this project is all AI-generated, and I haven't reviewed it, so it may contain errors.

**Pull Requests are not accepted at this time.** If you have ideas for improvement, please fork the repository and modify it for your own use. I just want to write a little something and enjoy myself.

How to run: directly execute the .jar file. You can compile it yourself or download the pre‑built version from Releases. However, the Release version may lag slightly behind.
I used AI(DeepSeek) to write some codes.
Below is the README.md written with the help of AI.

---

# FightGameReforged

A command‑line turn‑based text battle game written from scratch by a high school student — pure Java implementation, event‑driven architecture, with mod loading support.

---

## 📖 Project Overview

This is a Java‑based command‑line turn‑based battle game. Players can form a team, choose enemies, manually control characters to cast skills, and experience the fun of strategic combat in a text interface.

The project adopts an event‑driven architecture, decoupling game logic through a custom EventBus and @SubscribeEvent annotations, laying a foundation for future expansions. It also includes a built‑in mod system that supports dynamic compilation of .java source files and loading of external mods, making it easy to add new creatures, skills, and items.

The author is a high school student passionate about programming and game development. This project was written purely for fun. The code is free to use and modify — you are welcome to fork it.  
**Pull Requests are not accepted.** If you have improvements, please fork the repository and modify it for your own use.

---

## ✨ Core Features

- **Classic turn‑based combat**: freely build your team, choose enemies and rewards, and manually control each character's skill usage.
- **Five‑element system**: incorporates Metal, Wood, Water, Fire, and Earth elements, with corresponding resistance and damage bonus mechanics.
- **Event‑driven architecture**: decouples game logic via a custom EventBus and @SubscribeEvent or @SubscribeEvent(priority=x) annotations, enhancing extensibility.Default priority is 3.The smaller the number is, the higher the priority is, and the smallest number is 0.
- **Built‑in mod system**: automatically scans and loads external mods, supports dynamic compilation of .java source files, facilitating the addition of new creatures, skills, and items.
- **Utility AI controller**: non‑player characters make decisions based on a Tag weight system — each entity has its own Tag weights (reflecting personality), combined with real‑time context (HP, etc.) to compute action scores and select the optimal behavior.
- **MIT open‑source license**: code is fully open, free to use, modify, and distribute.
- **Command system** (AI‑written, modelled on Minecraft Java Edition): type a command starting with `/` or `#` to inspect or tweak a fight — entity selectors, typed arguments, located error messages, and tab‑completion suggestions.

---

## ⌨️ Command System

> This section (code and docs) was written by AI (DeepSeek) and has not been reviewed line by line by the author.

The game's original input flow is **unchanged**; commands are simply an extra input form starting with `/` or `#`. During your turn you can run a command and then continue picking skills as usual.

Built‑in commands:

| Command | Description |
|---|---|
| `/help` or `/?` | List all commands |
| `/help <name>` | Show usage of one command |
| `/list [target]` | List living things in the current fight (HP / atk / def / speed / alive) |
| `/kill <target>` | Set the target's HP to zero |
| `/hurt <target> <amount>` | Change HP; positive damages, negative heals |
| `/endfight [win\|lose]` | Force‑end the fight (defaults to a player win) |

Entity selectors (used wherever a target is expected):

```
@s                            the executor (the character you picked)
@p / @n / @r                  nearest / furthest / random one
@a / @e                       every living thing (includes your own team, same as MC)
@e[type=InsectBoss]           filter by type (simple class name, case‑insensitive)
@e[name=*虫*]                  filter by name (supports * wildcards)
@e[type=CommonInsect,limit=2,sort=nearest]    combinable: type / name / limit / sort
```

**⚠️ In `cmd.exe`, use ASCII class names.** Chinese console input is dropped by the Windows
native console layer (verified: both `System.in` and `System.console()` fail to receive it, so it
cannot be fixed on the Java side). Mapping:

| ASCII class name | Creature | | ASCII class name | Creature |
|---|---|---|---|---|
| `PlayerOne` | 玩家一 | | `InsectBoss` | 虫皇 |
| `ActorLiXiaoYan` | 李晓焰 | | `CommonInsect` | 普通虫子 |
| `Phainon` | 白厄 | | `IceInsect` | 冰虫子 |

Matching by Chinese name or by id **is implemented and covered by self‑tests**
(`@e[name=普通虫子]` selects the insect in the test suite) — it is only `cmd.exe` that cannot
deliver the characters. Windows Terminal or running from an IDE usually works.

To add your own commands, see `project_analyses/COMMAND-SYSTEM-2026-08.md`
(full guide, two registration styles — plain tree building or `@Subcommand` annotations — and a
list of pitfalls). Hooking it into existing code takes one line:
`if (CommandManager.process(input)) { continue; }`

---

## 🛠️ Tech Stack

| Item          | Description    |
|---------------|----------------|
| Language      | Java (JDK 25+) |
| Build tool    | Gradle         |
| Dependencies  | org.json       |

---

## 🚀 Quick Start

### Option 1: Run the JAR directly (recommended)

1. Go to [Releases](https://github.com/QWESSFDFC/FightGameReforged/releases) and download the latest `.jar` file.
2. Execute in your terminal:
   ```bash
   java -jar FightGameReforged.jar
   ```

⚠️ The Release version may be slightly behind the main branch. For the latest features, refer to Option 2.

### Option 2: Compile and run from source

1. Clone the repository:
   ```bash
   git clone https://github.com/QWESSFDFC/FightGameReforged.git
   ```
2. Open the project with IntelliJ IDEA.
3. Run the main class: `cn.gfhnv.game.GameStarter`.
4. Follow the command‑line prompts to start the game.

📁 The project includes two example mods, `exampleModByGFHNV` and `abstractLaunchingWords`, located in the `mods/` directory, which can serve as references for mod development.

---

![Screenshot](./screenshots/图1.PNG)

---

## 📁 Project Structure

```
FightGameReforged/
├── src/cn/gfhnv/game/
│   ├── annotation/      # Custom annotations (e.g., @SubscribeEvent)
│   ├── damage/          # Damage calculation and element counter system
│   ├── effect/          # Battle effects (Buffs/Debuffs)
│   ├── entity/          # Entities (characters, monsters)
│   ├── entityController/# Controllers (excluding Utility AI decision system)
│   ├── event/           # Event bus and event definitions
│   ├── inventory/       # Inventory system
│   ├── item/            # Item definitions
│   ├── mod/             # Mod loader
│   ├── officialStuff/   # Official content (preset characters/skills/commands)
|   └── system           # Battle system, thinking system, log system, mana, and even simple physics
│       └── command/     # Command system (AI-written, MC-style: argument types/selectors/tree/dispatcher)
├── mods/                # Directory for external mods
├── project_analyses/    # Analysis docs and the command system guide
├── test-command-system.ps1      # Command system self-test (compiles + runs, 55+ assertions)
├── 启动游戏-UTF8.bat             # Launcher (switches console to UTF-8; file content is pure ASCII)
└── README.md
There may be additional folders not listed here.
```

---
This function is unfinished.
## 🧠 About the AI Decision System (ThinkingController)

The behavior of non‑player entities is controlled by `ThinkingController`, whose core mechanism is based on **Utility AI**:

- Each entity has its own Tag weight table (e.g., attack, defend, heal, restore mana, amplify damage, etc.), reflecting its personality.
- Each turn, the controller reads the entity's current Tag weights and combines them with real‑time situational factors (HP percentage, mana, enemy distance, element counter, etc.) to compute a final score for every possible action.
- The system automatically selects the action with the highest score, enabling intelligent and varied NPC behavior.

**Advantages of this design:**

- **Decoupling**: Tag weights are separated from action logic; adding a new action only requires adding the corresponding Tag and score calculation.
- **Debuggability**: The decision process can be printed as logs, making it easy to identify why an AI behaves unexpectedly.
- **Extensibility**: Supports advanced features like "mood index", temporary modifiers, random perturbations, etc.

---

## 🤝 Feedback & Communication

- **Bug reports / Suggestions**: Welcome to open an Issue. I will try to read them when I have time, but I may not respond quickly or fix everything (school keeps me very busy – it's brutal).
- **Code contributions**: **Pull Requests are not accepted.** If you have improvements, please fork the repository and modify it for your own use.

---

## 📄 License

This project is licensed under the MIT License. The code is fully open, free to use, modify, and distribute.

---

**Author**: a high school student passionate about programming and game development | Project started on September 1, 2025


