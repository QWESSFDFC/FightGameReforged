中文:
这是一个使用java编写的游戏.目前只有文字,大概能正常玩了?代码随便用,随便改.我是高中生,没时间.偶尔更新.编写此项目只是为了图一乐,发到Github上纯粹是闲的没事.目前没有使用java的相关游戏引擎,只是自己写东西,自娱自乐.......

使用方法:直接运行.jar文件.可以自己编译或者下载Release中编译好的.但是Release中版本可能落后一点.

下面是使用AI写的README.md

# FightGameReforged

一个由高中生从零编写的**命令行回合制文字战斗游戏**，纯 Java 实现，基于事件驱动架构，并支持模组加载。

## ✨ 核心特色

- **经典回合制战斗**：玩家自由组建队伍，选择敌人与奖励，手动操控每个角色释放技能。
- **五行元素体系**：引入金、木、水、火、土五种元素，并设计了对应的抗性与伤害加成机制。
- **事件驱动架构**：通过自定义 `EventBus` 和 `@SubscribeEvent` 注解解耦游戏逻辑，为扩展性打下基础。
- **内置模组系统**：可自动扫描并加载外部模组，支持动态编译 `.java` 源码，方便添加新生物、技能与物品。
- **MIT 开源许可**：代码完全开放，随意使用、修改、分发。

## 🚧 当前状态

项目仍处于**早期开发阶段**。战斗核心循环、奖励系统等功能尚未完全闭环，但它已经是一个架构清晰、可运行的技术演示，并持续迭代中。

## 🛠️ 技术栈

- **语言**：Java (JDK 17+)
- **构建工具**：IntelliJ IDEA (原生)
- **核心依赖**：无外部依赖（纯 Java 标准库）

## 🎮 快速开始

1. 克隆仓库
2. 使用 IntelliJ IDEA 打开项目
3. 运行 `cn.gfhnv.game.GameMain` 主类
4. 按照命令行提示开始游戏

> **注意**：项目包含一个示例模组 `exampleModByGFHNV`，位于 `mods/` 目录下，可作为模组开发参考。

## 🤝 贡献与反馈

欢迎提交 Issue 和 Pull Request。任何形式的反馈、建议、代码贡献都会让这个项目变得更好。

---
**作者**：一名热爱编程与游戏开发的高中生
English:
Translated by AI.
This is a game written in Java. Currently, it's text-based only, but it should be playable? Feel free to use and modify
the code however you want. I'm a high school student and don't have much time. Updates will be occasional. This project
was created just for fun, and uploading it to GitHub was purely out of boredom. Currently, no Java game engines are
used; it's just my own code for my own entertainment.......

How to use: Simply run the .jar file. You can compile it yourself or download a pre-compiled version from Releases.
However, the version in Releases might be a bit outdated.

Below is a README.md written by AI

# FightGameReforged

A **command-line turn-based text battle game** built from scratch by a high school student. Written in pure Java,
featuring an event-driven architecture and built-in mod support.

## ✨ Key Features

- **Classic Turn-Based Combat**: Assemble your party, choose enemies and rewards, and manually control each character's
  skills in tactical battles.
- **Five-Element System**: Inspired by *Wu Xing*, the game implements elemental resistances and damage bonuses across
  Metal, Wood, Water, Fire, and Earth.
- **Event-Driven Architecture**: A custom `EventBus` with `@SubscribeEvent` annotations decouples game logic and
  simplifies future expansion.
- **Mod System**: Automatically scans and loads external mods, including on-the-fly compilation of `.java` source
  files—perfect for adding new creatures, skills, or items.
- **MIT Licensed**: Fully open-source. Use, modify, and distribute the code freely.

## 🚧 Current Status

The project is in **early development**. Core features like the full combat loop and reward system are still being
implemented. However, it already serves as a cleanly architected, runnable tech demo that continues to evolve.

## 🛠️ Tech Stack

- **Language**: Java (JDK 17+)
- **Build**: IntelliJ IDEA (native)
- **Dependencies**: Zero external libraries (pure Java standard library)

## 🎮 Quick Start

1. Clone the repository
2. Open the project in IntelliJ IDEA
3. Run the main class `cn.gfhnv.game.GameMain`
4. Follow the on-screen prompts to play

> **Note**: An example mod (`exampleModByGFHNV`) is included under the `mods/` directory—use it as a reference for
> creating your own content.

## 🤝 Contributing

Issues and pull requests are warmly welcomed. Feedback, suggestions, or code contributions of any size help move this
project forward.

---
**Author**: A high school student passionate about programming and game development
