中文:
这是一个使用java编写的游戏.目前只有文字,大概能正常玩了?代码随便用,随便改.我是高中生,没时间.偶尔更新.编写此项目只是为了图一乐,发到Github上纯粹是闲的没事.目前没有使用java的相关游戏引擎,只是自己写东西,自娱自乐.......

使用方法:直接运行.jar文件.可以自己编译或者下载Release中编译好的.但是Release中版本可能落后一点.

下面是使用AI写的README.md
FightGameReforged
一个使用 Java 编写的文字回合制战斗游戏。
玩家可以选择自己的角色、敌人和奖励，在回合制系统中与敌人战斗。
项目完全开源，使用事件驱动架构，支持模组加载。
作者为高中生，利用课余时间编写，代码随意使用和修改。

🎮 纯属娱乐，自娱自乐项目，欢迎 Star 和 Fork！

✨ 功能特点
文字交互：所有操作通过命令行完成，简单易上手。

回合制战斗：玩家与敌人轮流行动，每个单位有独立的回合时间。

角色选择：可以从预设的生物列表中选择任意数量的角色加入队伍。

敌人选择：同样可以选择任意数量的敌人作为对手。

奖励系统：战斗胜利后可以获得物品。

技能系统：每个生物拥有多个技能，玩家可以手动选择技能和目标。

事件驱动架构：使用自定义的 EventBus 处理游戏内事件，方便扩展。

模组加载：支持通过 ModLoader 加载官方或第三方模组（目前只有官方内容）。

开源协议：MIT 许可证，可自由使用、修改、分发。

🚀 如何运行
方式一：直接运行 JAR 包
确保已安装 Java 17 或更高版本。我写的时候用的Java 25.


打开命令行，执行：

bash
java -jar FightGameReforged.jar
方式二：从源码编译运行
克隆仓库：

bash
git clone https://github.com/QWESSFDFC/FightGameReforged.git
cd FightGameReforged
使用 IntelliJ IDEA 打开项目，或使用命令行编译：

bash
javac -d out -cp "lib/*" src/cn/gfhnv/game/*.java
运行主类 cn.gfhnv.game.GameStarter。

方式三：打包为可执行镜像（使用 jpackage）
项目已配置 jpackage 命令，可在 Windows 上生成带控制台的 exe 应用。
详细步骤请参考 打包指南（待补充）。

🎯 基本玩法
输入你的名字：游戏开始时会要求输入玩家名（仅用于显示）。

选择角色：从列表中选择生物加入你的队伍。输入编号查看介绍，输入 yes 确认加入，输入 no 取消，输入 next 完成选择。

选择敌人：同样方式选择你要面对的敌人。

选择奖励：选择战斗胜利后可能获得的物品（目前只有一把剑）。

战斗开始：

每个单位按速度决定行动顺序。

轮到你控制的角色时，会显示可用技能列表，输入编号选择技能。

根据技能的目标数，选择攻击目标（可多选，输入 next 提前结束选择）。

敌人会自动选择技能和目标。

战斗结束：一方全部死亡后，显示胜利或失败信息，并询问是否再玩一局。

欢迎提交 Issue 或 Pull Request 帮助改进！不一定看.

📦 模组开发
项目支持简单的模组加载。官方模组位(exampleModByGFHNV)于 仓库的mods文件夹下，包含：空气

若要开发自己的模组，可看游戏mod方面的源码，以及阅读officialStuff


📄 许可证
本项目使用 MIT 许可证。
你可以自由使用、修改、分发代码，但需保留原作者的版权声明。

English:
Translated by AI.
This is a game written in Java. Currently, it's text-based only, but it should be playable? Feel free to use and modify the code however you want. I'm a high school student and don't have much time. Updates will be occasional. This project was created just for fun, and uploading it to GitHub was purely out of boredom. Currently, no Java game engines are used; it's just my own code for my own entertainment.......

How to use: Simply run the .jar file. You can compile it yourself or download a pre-compiled version from Releases. However, the version in Releases might be a bit outdated.

Below is a README.md written by AI

FightGameReforged
A text-based turn-based combat game written in Java.
Players can choose their characters, enemies, and rewards, and battle enemies in a turn-based system.
The project is fully open-source, uses an event-driven architecture, and supports mod loading.
The author is a high school student, writing this in their spare time. The code can be used and modified freely.

🎮 Purely for entertainment, a personal fun project. Stars and Forks are welcome!

✨ Features

Text-based Interaction: All operations are done through the command line, making it simple and easy to learn.

Turn-Based Combat: Players and enemies take turns acting, with each unit having its own independent turn timer.

Character Selection: Choose any number of characters from a preset list of creatures to join your party.

Enemy Selection: Similarly, choose any number of enemies to face as opponents.

Reward System: Obtain items after winning a battle.

Skill System: Each creature has multiple skills, and players can manually choose skills and targets.

Event-Driven Architecture: Uses a custom EventBus to handle in-game events, making it easy to extend.

Mod Loading: Supports loading official or third-party mods via a ModLoader (currently only official content exists).

Open Source License: Licensed under the MIT License, allowing free use, modification, and distribution.

🚀 How to Run

Method 1: Run the JAR file directly
Ensure Java 17 or higher is installed. I used Java 25 when writing this.

Open a command line and execute:

bash
java -jar FightGameReforged.jar
Method 2: Compile and run from source
Clone the repository:

bash
git clone https://github.com/QWESSFDFC/FightGameReforged.git
cd FightGameReforged
Open the project with IntelliJ IDEA, or compile using the command line:

bash
javac -d out -cp "lib/*" src/cn/gfhnv/game/*.java
Run the main class cn.gfhnv.game.GameStarter.

Method 3: Package as an executable image (using jpackage)
The project is configured with jpackage commands, which can generate a console-based EXE application on Windows.
For detailed steps, please refer to the Packaging Guide (to be added).

🎯 Basic Gameplay

Enter your name: At the start, you'll be prompted to enter a player name (for display purposes only).

Choose your characters: Select creatures from the list to add to your party. Enter a number to view a creature's description, enter yes to confirm adding them, no to cancel, and next to finish selection.

Choose your enemies: Similarly, select the enemies you want to face.

Choose a reward: Select an item you might obtain after winning the battle (currently only a sword is available).

Battle begins:

Each unit acts in order determined by their speed.

When it's your controlled character's turn, a list of available skills will be displayed. Enter a number to choose a skill.

Based on the skill's target count, select targets (multiple selections possible, enter next to finish selecting early).

Enemies automatically choose their skills and targets.

Battle ends: When one side is completely defeated, a victory or defeat message is displayed, and you are asked if you want to play another round.

Issues or Pull Requests for improvements are welcome! (Though I might not always see them.)

📦 Mod Development
The project supports simple mod loading. The official mod (exampleModByGFHNV) is located in the repository's mods folder, containing: nothing (air).

To develop your own mod, you can look at the game's mod-related source code and read officialStuff (referring to the package cn.gfhnv.game.officialStuff).

📄 License
This project uses the MIT License.
You are free to use, modify, and distribute the code, but you must retain the original author's copyright notice.
