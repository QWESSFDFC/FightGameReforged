# 数据外部加载（实体数据 / 技能数据 / 魔法数字）设计（2026-10-03 版）

> **这是什么**：对用户新需求「接着写数据（实体数据、技能数据、一些魔法数字等）外部加载，
> 参考现在的 tags 加载，模组数据加载给一个接口，配置文件规定在 `config` 目录下」的**设计文档**。
> **本文一行 `src/` 代码都没改**，只新建了这一份文档。
>
> **谁要读**：下一步要写 `ConfigLoader` 扩展 / 实体补丁 / 模组配置接口的人或 AI。
> 读完这一份就够，不需要重新做一轮调研。
>
> **一句话结论**：**构造器给默认值、外部数据只做"补丁"**；
> 补丁打在 **`World` 注册表模板**上（就是 `TagConfig` 现在的时机，`GameMain.java:71`），
> 复用 `/data` 的键名当**唯一一套字段名**；模组侧给一个**新注解 `@ModConfig` + 可选接口 `ModDataAware`**，
> 配置文件写 `config/data/<modid>.json`（**模组不自带配置**，读游戏自己的 `config/`）。

---

## 零、TL;DR

| 问题 | 结论 | 关键理由 |
|---|---|---|
| **覆盖还是默认值？** | **构造器的值是"缺省值"，外部数据是"补丁"** —— 只有文件里**显式写出来**的键才覆盖 | 纯覆盖会让配置文件必须写全 13 个字段（官方 12 个模板 + 每个模组模板都要抄一遍），版本一升级就少字段；补丁语义与 `TagConfig` 完全同构 |
| **打在哪个时机？** | **`GameStartEvent` 之后、选人之前**（`ConfigLoader.loadConfig()` 现在的位置），打在 `World` 注册表**模板**上 | 这时官方 + 模组内容都已注册（`GameMain.java:68` → `:71`），选人时 `copy()` 出来的副本天然带上补丁值 |
| **能不能在构造时读？** | **不要** | 配置加载现在发生在所有实体构造**之后**（`GameMain.java:62-71`）。要在构造器里读，就得把配置加载提到 `new OfficialGameContent()` 之前，于是模组内容永远读不到配置，还要让 `LivingThing` 依赖配置系统 |
| **能不能在 `whenFightStart` 打？** | **不要**（第一版） | 契约是"每个模板**一次**"，而 `whenFightStart` 是"每个**实例**一次"；且盗火行者 / 醉剑仙已经在那儿挂开局状态（`FlameReaver.java:289`、`DrunkenSwordsman.java:148`），两件事会互相踩 |
| **模组接口叫什么？** | **`@ModConfig`（注解）+ `ModDataAware`（可选接口，唯一方法 `applyConfig(ModConfigDocument)`）** | 注解是声明式的（模组不用写样板代码），接口是可选的（不改 `Mod` 的抽象契约，现有 3 个模组一个都不用动） |
| **技能数据能不能外置？** | **能**：倍率 / 目标数 / 冷却 / 消耗 / AI 权重。**不能**：`comeToEffect` 的行为逻辑、"每层 +1.5 倍率"这类由玩法推出来的动态值 | 前者是纯数值，后者是代码；把后者塞进 JSON 只会得到一份读不懂的规则表 |
| **魔法数字的判据？** | **"会随平衡变 + 改了不影响机制 + 有名字和量纲"三条全中才外置**；结构常量（元素数 6、背包 63、`SKIP_WITHOUT_NEW_TURN`）与公式骨架（`110` / `200` / `10×等级`）**留在代码里** | 见第六节的判据表 |
| **热重载？** | **不做**。改文件 → 重启游戏 | 要热重载就得处理"已经复制出去的副本 + 已经挂上的效果"（`ENTITY-ATTRIBUTE-SPLIT-2026-10.md` 第二节那张生命周期表），代价远大于收益；README / `MODDING-GUIDE.md:56` 本来就写着"没有热重载" |
| **`/data` 键名会变吗？** | **不变**。外部数据**就用 `/data` 的数据名**（`fireResistance` / `hpGrow` / `attackGrow` …） | 一套名字两处用，改一次两边一起改；`notes_for_llm/70-DATA.md:21-22` 已定口径"数据名是对外 API" |
| **13 参构造器动吗？** | **一个字都不动** | `MODDING-GUIDE.md:163-167` / `:451` 把它写成对外 API，模组 `DrunkenSwordsman.java:94` 正在用 |
| **阶段划分** | **5 步**：①只读键名+自测 → ②实体数值 → ③技能数值 → ④BOSS/魔法数字 + 模组接口 → ⑤文档回写 | 每步自洽、可单独停、不留半成品（第七节） |
| **最大风险** | **派生值重算**：`setHpGrowNumber()` 这类 setter **不重算** `hp`/`attack`/`defence`（`LivingThing.java:674-711` 只赋值），只有 `setLevel()`（`Entity.java:208-218`）会 | 配置里写 `成长` 而不重算 = "改了没反应"；必须把"先 `setLevel` 再逐项覆盖"写成实现约定 + 自测断言 |

---

## 一、现状体检（2026-10-03 实测，复现命令见附录 A）

### 1.1 现成的加载器（这是要照抄的风格）

| 事实 | 位置 / 数字 |
|---|---|
| 唯一在跑的配置加载器 | `src/cn/gfhnv/game/system/configLoadingSystem/ConfigLoader.java`（**170 行，包内唯一文件**） |
| 读的文件 | `./config/gameConfig/TagConfig.json`（`ConfigLoader.java:32`，**相对工作目录**，不是相对 jar） |
| 读法 | `Files.readString(path, StandardCharsets.UTF_8)` + `new JSONObject(...)`（`:124`） |
| 缺文件时 | `setDefaultConfig()` 把内置字符串写盘再读回来（`:112-123`、`:164-169`） |
| 写盘编码 | `DEFAULT_TAGS_CONFIG.getBytes()`（`:166`）—— **平台默认字符集**，与 `:124` 的 UTF-8 读**不一致**（既有缺陷，见 §8.3 D7；Java 18+ 默认 UTF-8 所以现在无害） |
| 未知 tag 键的下场 | `TagType.valueOf(tagType.toUpperCase())`（`:129`）→ 拼错一个键名**整个 `loadConfig()` 抛异常**，被 `GameMain.java:72-74` 吞成一行日志 |
| 注入方式 | 遍历 `World.getEntityList()` / `getItemList()`，**按键 id 精确匹配**（`entity.getId()`，含 `game_official_content:` 前缀），命中就 `setTags(...)`（`:137-153`） |
| 注入的时机 | `GameMain.gameInitialize()` 里，紧跟 `EventBus.post(new GameStartEvent())`（`:68`）之后（`:71`） |
| 谁对它负责 | 只有 `GameMain.java:71` 一个调用点 |

**这套流程本身就是一个可用的"外部数据加载"雏形** —— 它已经证明了本项目需要的三件事都能做：
① 读 `config/` 下的 JSON；② 在正确的时机（注册表齐了、还没选人）改**模板**；
③ 改了模板，`copy()` 出来的副本自动带上（`LivingThing.java:201-207` 复制 `tags`、`Entity.java:39-45` 同理）。

### 1.2 配置目录里现在有什么

| 文件 | 内容 | 谁在读 |
|---|---|---|
| `config/gameConfig/TagConfig.json`（41 行） | 6 个 id 的 AI 权重（`attack` / `heal` / `defence` / `restoration_mana` / `damage_enhance` / `control_enemies`） | `ConfigLoader`（唯一读取方） |
| `config/gameConfig/PropertyConfig.json`（5 行，内容是一个空对象） | `{"game_official_content:insectBoss": {}}` | **全项目零读取**（已复核：`grep PropertyConfig` 只命中它自己的文件名） |

`config/` 目录**没有被 `.gitignore` 排除**（`git check-ignore -v` 返回 1），所以配置文件会随仓库走 —— 这对"默认配置要能复现"是好事。

### 1.3 数值现在写在哪（要外置的源头）

**实体**：`LivingThing.java:228` 的 13 参构造器，参数**顺序**即 API：

```java
LivingThing(String name, String id, double 火抗, double 水抗, double 金抗, double 木抗, double 土抗,
            long speed, long level, String type, double hpGrow, double atkGrow, double dfkGrow, ElementSort 元素)
```

它内部**由成长系数算出**三围（`:244-247`）：

```java
hp     = (level-1) * hpGrow  + 200
defence= (level-1) * dfkGrow + 200
attack = 110 + atkGrow * (level-1)
hpMax  = hp
```

再按元素填五行法力成长并 `initialMana()`（`:248-285`，`initialMana` 在 `:563`，**会重置整个法力列表**）。

调它的地方一共 **9 处**（全项目，含自测）：

| 文件:行 | 对象 | 关键数字 |
|---|---|---|
| `officialStuff/customEntity/players/PlayerOne.java:21` | 玩家一 | `0.3/0/0/0/0.3, 120, l, "player", 36, 29, 5, DIRT` |
| `.../players/Phainon.java:90` | 白厄 | `0.7/0/0/0/0, 120, l, "player", 29, 40, 25, FIRE` |
| `.../players/ActorLiXiaoYan.java:113` | 李晓焰 | `0.4/0/0/0/0, 120, l, "player", 58, 22, 5, FIRE` |
| `.../monsters/CommonInsect.java:15` | 普通虫子 | `0/0.1/0.95/0.5/0.8, 90, l, "insect", 30, 5, 9, METAL` |
| `.../monsters/IceInsect.java:16` | 冰虫子 | `-0.2/0.6/0.2/0.2/0.2, 120, l, "insect", 30, 5, 9, WATER` |
| `.../monsters/InsectBoss.java:23` | 虫皇 | `-0.1/0.2/0.8/0.2/0.3, 110, l, "insect", 4000, 7, 20, METAL` |
| `.../monsters/FlameReaver.java:219-220` | 盗火行者 | `0.2×5, 140, l, "boss", 120, 60, 25, FIRE` |
| `.../summons/BrokenContainer.java:284-285` | 容器 | `?, 120, owner.getLevel(), "summon", 4, 6, 3, FIRE`（生命/攻击随后按 BOSS 的 15%/40% 改写） |
| `mods/drunkenSword/.../DrunkenSwordsman.java:94-95` | 酒剑仙（模组） | `0.3/0.3/0/0/0, 130, level, "player", 34, 32, 12, FIRE` |
| `debug_tools/TestCommandSystem.java:3037-3038` | 自测探针 | `0×5, 100, 1L, "insect", 10, 10, 10, FIRE` |

**构造器之外还写死了两类数值**（只外置构造器会漏掉它们）：

| 类别 | 例子 | 位置 |
|---|---|---|
| 构造后**直接改写**的派生值 | 虫皇 / 盗火行者的 `BASE_HP_MAX = 80000` + `setHpMax` + `setHp` | `InsectBoss.java:20,24-25`；`FlameReaver.java:211,224-225` |
| 构造后设的展示值 | `setMass(60/100)`、`setDescription(...)`、`getInventory().addSlot(63)` | `PlayerOne.java:22-24` 等；`setMass` 在 `Thing.java:219`，`setDescription` 在 `LivingThing.java:1282` |

**技能**：`Skill(String name, String description, double hpMag, double atkMag, double defMag, int aims)`（`Skill.java:87`），
倍率/冷却/消耗/权重全在子类构造器里写死，例如：

```java
// mods/drunkenSword/.../RaiseCup.java:40-46
super("举杯邀月", "…", 0, ATK_MAGNIFICATION /*3.0*/, 0, 1);
this.setCoolDown(0);
this.getTags().put(TagType.ATTACK, new Tag(3));
```

**`Skill` 基类没有 id 字段**（`Skill.java:36-47` 只有 name/description/三个倍率/aims/coolDown/nowCoolDown/isForEnemies/consumedMana/extraDamage/tags）——
这是技能外置必须先解决的问题（§5 给了折中键 `实体id + 技能名`）。

### 1.4 与这次需求直接相关的既有事实（直接用，不再重复验证）

- `ThinkingControllerAI` **全项目只有一处引用**：`LivingThing.java:187` 的复制构造器分支；
  `ThinkingController` 连引用都没有 → **没有任何生物在用 Tag 权重**，`TagConfig.json` 目前对玩法**零影响**
  （`ProjectStatus` 早写过，本文 2026-10-03 重新复核：`new ThinkingController` 全局 0 命中）。
  → **"照 TagConfig 做"这件事本身不能作为"玩法会变"的证明**，第 1 阶段的验收要专门盯这一点。
- `type`（`"player"` / `"insect"` / `"boss"` / `"summon"`）**只是显示用**：全项目只在构造器里写、在复制构造器里抄，
  没有任何逻辑按它分支（选择器 `@e[type=…]` 比的是**类名 / 名字 / id**，见 `EntitySelector.java:253-263`）。
- 派生值是**现算**的：`getAttack()`（`LivingThing.java:1712`）、`getDefence()`（`2083`）、`getHpMax()`（`1638`）
  = `基础 × (1+百分比) + 固定值`。所以"改基础值"和"改增强值"是两件事，外部数据只该碰**基础值**。
- `whenFightStart` 只由 `FightStartEventListener` 在开局对 `getAllEntities()` 调**一次**（`FightStartEventListener.java:18-24`），
  中途召唤的实体由召唤方自己补调。
- 自测基线：**`通过 531 条，失败 0 条`**（2026-10-03 落地阶段 0 + 阶段 1 之后，**AI 用自带 JDK 实跑**；
  原文写的 483/0 是 2026-10-02 用户实跑的结果，本次新增 48 条 → 483 → **531**）。
  ⚠️ 原文"本次沙箱里 `java.exe` 被拒，我**没能复跑**"那句**已经过期**：
  这次 `java.exe` 与 `javac.exe` 都能跑，`check-sources.ps1` → `Java files: 206` + `CHECK OK`。

---

## 二、方案（三个必须拍板的问题，先说结论）

### 2.1 覆盖 vs 默认值 —— **选"构造器给默认值 + 外部数据是补丁"**

| 方案 | 语义 | 代价 |
|---|---|---|
| **A. 覆盖（外部是唯一真相）** | 文件里**必须**写全每个实体的每个字段，没写的按 0 处理 | 官方 12 个模板 + 每个模组模板都要抄全 13+ 个字段；加一个新字段就要改所有文件；用户想只改一个速度，得懂全部字段。**否决** |
| **B. 补丁（缺省值在代码里）** | 文件里**显式出现**的键才覆盖，没出现的保持构造器算出来的值 | 需要一份"内置默认值"清单让用户知道可改什么（**用生成器解决**，见第七节阶段 0）；同名键在两处出现，但**唯一真相仍是运行期对象**，文件只是补丁 |
| **C. 只读不写（配置文件仅作展示）** | 生成一份 `EntityData.json` 供查阅，改了不生效 | 不满足需求。**否决** |

**选 B**，并且规定三条缺省行为：

1. **文件不存在** → 用内置默认值，并写出一份**全量默认文件**（照 `ConfigLoader.setDefaultConfig()` 的做法，`:164-169`；**只在缺失时写**，绝不在每次启动时覆盖用户改过的文件 —— 生成器的这条纪律见 §8.2 风险 10）；
2. **文件存在但某个实体/技能没写** → 那个实体保持构造器算出来的值（**不是**清零、**不是**报错）；
3. **某一项类型不对**（例如 `fireResistance` 写了字符串）→ **跳过该项**，在结尾汇总打印"跳过了 N 项 + 原因"，
   **不中断**整个加载（这是对 `ConfigLoader.java:129` 那个"一个键拼错就整份配置作废"的刻意改进，见 §8.3 D2）。

> 一句话：**代码里的数值是"出厂设置"，`config/data/*.json` 是"用户改过的部分"，两者叠加后的对象才是真相。**

### 2.2 注入时机 —— **打在 `World` 注册表模板上，位置就是 `GameMain.java:71`**

三个候选逐条算代价：

| 时机 | 覆盖面 | 副本（`copy()`）怎么办 | 模组自定义实体 | 代价 / 结论 |
|---|---|---|---|---|
| **A. 构造时读** | 所有实例 | 每次 `copy()` 都重新读一遍文件 | 能（构造器自己读） | 要把配置加载提到 `GameMain.java:62` 之前 → 模组内容（`:63` 才加载）永远读不到；`LivingThing` 要 `import` 配置系统（现在的依赖方向是"配置系统认识 World"，反过来会成环）；13 参构造器的参数会被文件静默覆盖，模组作者看代码会懵。**否决** |
| **B. 注册表建好后统一打补丁**（= 现在 `TagConfig` 的时机） | 注册表里的**模板** | **白送**：副本复制的是"已经打好补丁的模板"（`LivingThing.java:152-208` 逐字段抄；`tags` 也抄，`:201-207`） | 能：模组的 `registerItself()` 在 `GameStartEvent`（`GameMain.java:68`）里跑，早于 `:71` | 唯一要求：补丁必须作用在**模板**上，且**在每个模板上只跑一次**。**推荐** |
| **C. `whenFightStart` / `FightStartEvent`** | 实例（含游戏代码里临时 `new` 出来的） | 每次开局都跑一遍 | 能 | ① "每模板一次"变成"每实例一次"，**会重复应用**（`initialMana()` 会重置法力、`setHp` 会覆盖当前血量）；② 与现有开局逻辑抢时序（`FlameReaver.java:289`、`Phainon.java:324`、`DrunkenSwordsman.java:148` 都在这里挂状态）；③ 召唤物中途入场只调一次 `whenFightStart`，语义更乱。**第一版不用**；将来若要覆盖"临时 new 出来的实例"，再单独加一段（§7 阶段 4 可选） |
| **D. 运行中随时改（热重载）** | — | — | — | **明确不做**（见 §9） |

**推荐 B**，理由三条：

1. **它就是现有 `TagConfig` 的路径**，用户"参考现在的 tags 加载"这句话直接命中；
2. **选人列表里的数值就是补丁后的数值** —— 玩家在选人时看到的血/攻/防已经是配置文件里的，符合直觉；
3. **一行构造器都不用改**，13 参构造器与所有模组照旧。

补丁**打在模板上**的三条实现纪律（这是 B 方案里唯一容易做错的地方）：

| 纪律 | 为什么 |
|---|---|
| **每个模板只补一次**（用 `IdentityHashMap` 或"配置加载只跑一次"保证） | 补丁里会有 `setLevel(...)` → `initialMana()`（`LivingThing.java:563-565` 直接 `manas = new ArrayList<>()`），跑两次会把打到一半的法力重置 |
| **顺序固定：先 `setLevel(level)` 重算派生值，再逐项覆盖** | `setHpGrowNumber` / `setAtkGrowNumber` / `setDfkGrowNumber`（`LivingThing.java:674/690/706`）**只赋值不算数**；只有 `Entity.setLevel(long)`（`Entity.java:208-218`）会重算三围 + `setHpMax` + `initialMana` |
| **当前的 `hp` 最后设** | `setHp` 会**夹到 `getHpMax()`**（`LivingThing.java:2109`，`/data` 那条规矩的来源，见 `70-DATA.md:83-85`）；顺序反了会被旧上限夹掉 |

### 2.3 模组接口 —— **`@ModConfig` 注解 + `ModDataAware` 可选接口**

```java
// 模组主类里：声明"我的数值想被 config/data/drunkenSword.json 覆盖"
@ModConfig(id = "drunkenSword")                 // 不给也行，默认用 Mod.getMOD_ID()
public class DrunkenSwordMod extends Mod implements ModDataAware {

    public DrunkenSwordMod(ModInformation info) { super("drunkenSword", info); }

    /** 配置加载完成后、注册内容之前调用一次（没有配置文件时也会调用，读到的是空文档）。 */
    @Override
    public void applyConfig(ModConfigDocument cfg) {
        DrunkenSwordsman.setConfiguredSpeed(cfg.getLong("entities/drunkenSword/attackGrow", 32));
    }

    @Override
    public void invokeWhenLoaded() { addEntity(new DrunkenSwordsman(125)); /* … */ }
}
```

| 设计点 | 取值 | 理由 |
|---|---|---|
| 注解名 | **`cn.gfhnv.game.mod.config.ModConfig`** | 与 `@SubscribeEvent` / `@DataField` 同一风格（本项目已有注解先例） |
| 接口名 | **`cn.gfhnv.game.mod.config.ModDataAware`**，唯一方法 `void applyConfig(ModConfigDocument cfg)` | **可选接口**（`instanceof` 检查）而不是往 `Mod` 上加抽象方法 → 现有 3 个模组**一个字都不用改**，`MODDING-GUIDE.md` 的"主类契约"也不破 |
| 调用时机 | `ModLoader` 装载完之后、`invokeWhenLoaded()` **之前**（实现上落在 `GameStartEvent` 里，`GameStartEventListener.java:17-23` 那个循环的前半段） | 模组要在注册内容**之前**就能读到配置（比如"注册几个技能"都可能由配置决定） |
| 读文件的位置 | **`config/data/<modid>.json`**，`<modid>` 取 `Mod.getMOD_ID()`（`Mod.java:270`） | `MODDING-GUIDE.md:364` 已定口径：**模组不能自带配置，要配置就读游戏自己的 `config/`**；`NBT-AND-DATA-COMMAND-2026-09.md` 也把 `config/modData/<modid>.json` 当过候选，这里统一成 `config/data/`（少一层目录） |
| 找不到文件时 | **照样调用 `applyConfig`**，文档是空的（所有 `getXxx(path, 默认值)` 返回默认值） | 模组作者不用写"有没有文件"的分支；**不要**因为缺配置就跳过 `invokeWhenLoaded`（那会让内容整个消失） |
| 模组能不能改**别人**的分组 | **不能**。`ModConfigDocument` 只暴露"自己的分组 + 共享区 `common/`" | 模组之间的配置互相隔离；想让模组改官方数值，走共享区（第七节阶段 4 讨论） |
| 模组自己造实体时怎么被覆盖 | 模组在 `applyConfig` 里自己把值传给构造器，或者给实体类实现 `DataKeyed`（§3.4） | 模组实体是模组自己的类，游戏不可能知道它有哪些旋钮 |

---

## 三、目录与文件布局

### 3.1 目标布局（`config/` 下一共 5 个文件）

```
config/
└── gameConfig/                     # ← 沿用现成目录，不新造一级
    ├── TagConfig.json              # 【保留】AI 权重（现状不动，见 §3.2）
    ├── PropertyConfig.json         # 【废止】零读取，见 §3.2
    ├── EntityData.json             # 【新】实体基础数据
    ├── SkillData.json              # 【新】技能数值
    └── GameRules.json              # 【新】跨实体的魔法数字（公式常量 / 系统上限 / 初始法力骨架）
config/
└── data/                           # 【新】按模组分文件（游戏侧读，模组不得自带）
    ├── drunkenSword.json
    └── <modid>.json
```

**为什么数据文件继续放 `config/gameConfig/`，而模组覆盖放 `config/data/`**：

- `gameConfig/` 是**游戏自带内容**的配置（用户要"配置文件在 config 目录下"，`ConfigLoader.java:23` 的 javadoc 也把它写成"游戏本身的配置文件"）；
- `config/data/<modid>.json` 是**模组覆盖 + 模组私有数据**，按模组分文件，冲突面为零；
- 两者用**同一套加载器、同一套键名、同一个合并顺序**（§3.3），用户不用学两套规则。

### 3.2 与现存两个文件的关系

| 文件 | 处置 | 说明 |
|---|---|---|
| `config/gameConfig/TagConfig.json` | **保留，不迁移**（本轮不动） | ① `ConfigLoader` 现在跑得好好的；② 它是**实体/物品**的 `TagType→权重`，与"实体数值"不是一类数据；③ 迁移的收益只有"文件名好看一点"，代价是要重写 `ConfigLoader` + 改动 `config/` 下已被用户看惯的文件。**唯一要加的是一条注释**：这个文件目前对玩法零影响（`ThinkingControllerAI` 无人使用，§1.4），等 AI 接线后才有意义 |
| `config/gameConfig/PropertyConfig.json` | **废止：不再读它，也不主动删** | 它现在**全项目零读取**，内容是空对象；按"不删用户文件"的惯例，加载器**不碰**它。可以在 `README.md` 的项目结构表里标注"（已废弃，见 EntityData.json）"。**不要**把实体数据塞进这个文件 —— "Property"这个名字与 `Thing` 的物理属性语义撞车 |
| 新增 `EntityData.json` / `SkillData.json` / `GameRules.json` | 新增三个常量路径 | 路径都在 `ConfigLoader` 里，**一律照 `:32` 写成 `new File("./config/...")`**，保持"相对工作目录"的现有行为（启动脚本已经在根目录跑，见 `README.md` 的启动方式） |

### 3.3 合并顺序（后写覆盖先写）

```
内置默认值（代码里的构造器值）
   ↓ 覆盖
config/gameConfig/EntityData.json · SkillData.json · GameRules.json   （游戏对自带内容的调参）
   ↓ 覆盖
config/data/<modid>.json 的 common/ 段                              （模组对官方/共享数值的调整）
   ↓ 覆盖
config/data/<modid>.json 的 <modid>/ 段 + 模组自己 applyConfig 读到的值 （模组自己的内容）
```

规则：**每次覆盖都只覆盖"显式写出来的键"**；同一层里出现两个同名 id（不同模组都想改同一只怪）
→ **按模组加载顺序后者胜，并且打印一行 `⚠ 配置冲突：X 已被 <modA> 改成 …，现被 <modB> 覆盖为 …`**，
不静默。模组加载顺序不可复现这件事在 `PROJECT-ANALYSIS-2026-09.md` §6.3（`ModLoader.java:31-37` 未排序）里已记着 ——
**配置文件不要依赖"谁先谁后"，要依赖"显式覆盖 + 冲突提示"**。

### 3.4 键是什么？（这是整个设计里最要紧的一条）

**键 = `World` 注册表里的完整 id**，与 `TagConfig` 完全一致（`ConfigLoader.java:138` 用 `entity.getId()` 精确比较，
官方内容形如 `game_official_content:playerOne`，模组内容形如 `drunkenSword:drunkenSwordsman`）。

**字段名 = `/data` 的数据名**（`notes_for_llm/70-DATA.md` §5.10 那一套）：

| 配置里的键 | 对应 `/data` 的键 | 代码位置 |
|---|---|---|
| `fireResistance` / `waterResistance` / `metalResistance` / `woodResistance` / `dirtResistance` | 同名 | `ElementProfile`（`LivingThing.java:120` 的 `elements` 组件） |
| `speed` / `level` / `type` | 同名 | `LivingThing.java:159/160/239`、`Entity.java:191` |
| `hpGrow` / `attackGrow` / `defenceGrow` | 同名 | `LivingThing.java:674/690/706` |
| `hpMax` / `hp` / `attack` / `defence` | 同名（**固定值，覆盖派生结果**） | `LivingThing.java:1647/2109/1721/2092` |
| `mass` / `description` / `inventorySlots` | `mass` / `description`（背包格数**不是** `/data` 键，用 `getInventory().addSlot(n)`） | `Thing.java:219`、`LivingThing.java:1282` |
| 五行法力成长 `metalManaGrow` … `dirtManaGrow` | 同名（`70-DATA.md:36` 那 5 个改名后的键） | `ElementProfile` + `initialMana()`（`LivingThing.java:563`） |

**用 `/data` 的名字当配置键的收益**：只维护一套名字；用户可以用 `/data get entity @s` **看到当前生效值**，
再把它抄进配置文件；改名时"改一处、两处同时生效"，自测（阶段 0）会同时盯住两者。

**代价与对策**：配置键成了对外 API（和 `/data` 一样），改名会让用户的配置文件**静默失效** →
由"阶段 0 的键名契约自测"兜住（§7 阶段 0、§8.2 风险 3）。

> 补丁器用到的 setter 全部**已经存在**，不用新增 API：
> `LivingThing.java:2076` `setSpeed`、`:1647` `setHpMax`、`:1721` `setAttack`、`:2092` `setDefence`、`:2109` `setHp`、
> `:674/:690/:706` 三个成长 setter、`:1282` `setDescription`；`Thing.java:219` `setMass`（`LivingThing` 继承它）；
> `Entity.java:191` `setType`、`:208` `setLevel`；`Skill.java:118` `setForEnemies`、`:150` `setCoolDown`、`:166` `setAims`、
> `:427/:443/:459` 三个倍率 setter、`:475` `setConsumedMana`、`:502` `setTags`。
> 唯一**没有** setter 的是"背包格数"（只能 `getInventory().addSlot(n)`，`MODDING-GUIDE.md:179`）。

**模组自定义实体怎么办**（这是用户特别问的一条）：给一个**可选的**接口/注解，两条路任选：

```java
// 路线 1（推荐）：类上标注解，游戏按"注册 id 的短名"把配置补上去
@DataKey("drunkenSwordsman")     // 新注解，语义 = "/data 与配置文件都用这个键"
public class DrunkenSwordsman extends Player { … }

// 路线 2：实现接口，自己决定怎么把值吃进去（需要重算派生值时用）
public interface DataKeyed {                 // cn.gfhnv.game.entity.DataKeyed
    void applyDataPatch(DataPatch patch);     // patch 提供 getLong/getDouble/getString + has(key)
}
```

游戏侧补丁器的查找顺序写死为：**① 类实现 `DataKeyed` → 交给它自己；
② 类上有 `@DataKey` → 反射按字段名补；③ 都没有 → 按注册表里的完整 id 匹配；
④ 还匹配不上 → 这个模板的配置项**打印一条警告后忽略**（**不是**静默）。

---

## 四、JSON schema（带注释的示例）

> 下面 4 份 JSON **都不许带注释**（`org.json` 不认 `//`）。
> 这里写成 `//` 是为了讲解；**真正写进 `config/` 的文件必须是纯 JSON**，说明文字放 `README.md`。
> 所有"缺省行为"栏讲的是**这个键不写**时会发生什么。

### 4.1 `config/gameConfig/EntityData.json`

```jsonc
{
  "version": 1,                        // int，必填建议写：将来做迁移用（缺省 = 1）
  "entities": {                        // object，键 = 注册表完整 id（MOD_ID:短名）
    "game_official_content:playerOne": {
      "base": {                        // 全部可省。只写想改的
        "name": "玩家一",              // string  缺省：构造器给的 name（改它会影响 @e[name=…] 与选人列表）
        "type": "player",              // string  缺省：构造器给的 type（纯显示，随处可改）
        "level": 125,                  // long    缺省：构造器传进来的等级（注册时是 125/150）
        "mass": 60.0,                  // double  缺省：Thing 的 1.0 或构造后 setMass 的值
        "description": "这是玩家一.",   // string  缺省：构造后 setDescription 的值
        "speed": 120,                  // long    缺省：构造器值
        "fireResistance": 0.3,         // double  缺省：构造器值（小数：0.3 = 30% 减伤，-0.2 = 弱点）
        "waterResistance": 0.0,
        "metalResistance": 0.0,
        "woodResistance": 0.0,
        "dirtResistance": 0.3,
        "element": "DIRT",             // string  缺省：构造器值；可选 METAL/WOOD/WATER/FIRE/DIRT
        "hpGrow": 36.0,                // double  缺省：构造器值（成长系数，不是当前血）
        "attackGrow": 29.0,
        "defenceGrow": 5.0,
        "manaGrow": {                  // object，可省整块；只写想改的元素
          "metal": 10, "wood": 10, "water": 10, "fire": 4, "dirt": 20
        },
        "inventorySlots": 63           // long    缺省：构造后 addSlot 的总数（0 = 不加格子）
      },
      "derived": {                     // 可省整块。**写这里的值 = 用固定值覆盖公式算出来的结果**
        "hpMax": 80000,                // long    缺省：由 level/成长 算出的 hp（虫皇/盗火行者就是这么写的）
        "attack": 4078,                // long    缺省：110 + attackGrow×(level-1)
        "defence": 1688,               // long    缺省：(level-1)×defenceGrow + 200
        "hp": 80000                    // long    缺省：= hpMax（写它一般只在"想让某个模板开局残血"时用）
      }
    },
    "game_official_content:insectBoss": {
      "derived": { "hpMax": 80000, "hp": 80000 }   // 现在是代码里的 BASE_HP_MAX，搬进这里
    }
  }
}
```

**缺省行为汇总**（照抄进 README 用）：

| 情况 | 行为 |
|---|---|
| 整个文件不存在 | 用内置默认值，并**写出一份全量默认文件**（含所有注册过的实体 id） |
| `entities` 里没有某个 id | 该实体保持构造器算出来的值 |
| 某个 id 下面空对象 `{}` | 同上，不改任何东西 |
| 某个键拼错 | 该键**忽略** + 结尾汇总打印 "未知键 xxx（实体 yyy）"；**不中断** |
| 值的类型不对 | 该项**忽略** + 汇总打印；**不中断** |
| `level` 与 `hpMax` 同时写 | 先应用 `level`（触发重算 + `initialMana()`），再应用 `derived`，最后设 `hp` |
| 只写 `hpGrow` 不写 `level` | **必须**由补丁器自动补一次 `setLevel(当前等级)` 触发重算，否则"改了没反应"（§8.2 风险 1） |

### 4.2 `config/gameConfig/SkillData.json`

```jsonc
{
  "version": 1,
  "skills": {                          // 键 = "<实体完整id>#<技能名>"（Skill 基类没有 id，只能这么定，见 §5）
    "game_official_content:playerOne#枪射击": {
      "aims": 1,                       // int     缺省：构造器值。0=自身 / -1=全体 / 正数=选 N 个
      "coolDown": 2,                   // int     缺省：setCoolDown 的值（0 = 每回合都能放）
      "hpMagnification": 0.0,          // double  缺省：构造器值
      "atkMagnification": 7.5,         // double  缺省：构造器值
      "defMagnification": 0.0,
      "consumedMana": {                // object，可省；{amount, element}；不写 = 保持构造器里的消耗
        "amount": 10, "element": "UNIVERSAL"
      },
      "forEnemies": true,              // boolean 缺省：true
      "tags": { "ATTACK": 5 }          // object  缺省：构造器里 put 的那些；**整块覆盖，不是逐项合并**
    },
    "drunkenSword:drunkenSwordsman#一剑霜寒十四州": {
      "atkMagnification": 4.0,
      "consumedMana": { "amount": 800, "element": "FIRE" }
    }
  }
}
```

### 4.3 `config/gameConfig/GameRules.json`（魔法数字）

```jsonc
{
  "version": 1,
  "formula": {                 // 伤害/面板公式的骨架常量。**改这里会让所有已有标定失效**（§6）
    "hpBase": 200,             // long   面板：hp = (level-1)×hpGrow + hpBase
    "defenceBase": 200,        // long   面板：defence = (level-1)×defenceGrow + defenceBase
    "attackBase": 110,         // long   面板：attack = attackBase + attackGrow×(level-1)
    "levelDefenceFactor": 10,  // long   伤害：×(level×10 + 200) / (level×10 + 200 + 目标防御)
    "levelDefenceBase": 200    // long   同上
  },
  "mana": {                    // 初始法力（LivingThing#initialMana）
    "mainBase": 200,           // long   主元素上限 = manaGrow×(level-1) + mainBase
    "otherBase": 20            // long   其余元素 = manaGrow×(level-1) + otherBase
  },
  "flameReaver": {             // 具体 BOSS 的机制旋钮（现在是 private static final 常量）
    "summonHpCostRate": 0.03,        // FlameReaver.java:89
    "disasterPowerAttackBonus": 0.08,// FlameReaver.java:94
    "damageReductionLayers": 2,      // FlameReaver.java:99
    "damageReductionPerLayer": 0.25, // FlameReaver.java:104
    "containerLimit": 0,             // FlameReaver.java:122（0 = 不限）
    "completeContainerChance": 0.34, // FlameReaver.java:130
    "phaseTwoDamageReduction": 0.7,  // FlameReaver.java:147
    "containerHpRatio": 0.15,        // BrokenContainer.java:209
    "containerAttackRatio": 0.4,     // BrokenContainer.java:219
    "completeContainerHpRatio": 0.25 // BrokenContainer.java:224
  }
}
```

**注意 `formula` / `mana` 这几项的特殊性**：它们的默认值**不在实体对象上**，而在代码公式里，
所以"缺省行为"只能是"不写就用代码里的字面量"。这类键要用**不同的实现路径**（静态规则表，不是对象补丁），
见 §7 阶段 3 的说明。

### 4.4 `config/data/<modid>.json`（模组侧）

```jsonc
{
  "version": 1,
  "common": {                          // 模组对"共享数值"的调整（官方实体 / GameRules / 技能）
    "entities": {
      "game_official_content:flameReaver": { "derived": { "hpMax": 90000 } }
    },
    "rules": { "flameReaver": { "containerLimit": 8 } }
  },
  "drunkenSword": {                    // 模组自己的分组：键 = 模组自己定义的路径，游戏不解释
    "initialStacks": 4,
    "skills": { "frostSword": { "requiredStacks": 3 } }
  }
}
```

模组读自己的分组用 `ModConfigDocument`：

```java
cfg.getLong("drunkenSword/initialStacks", 2);          // 路径用 / 分隔；读不到就返回默认值
cfg.getString("drunkenSword/skills/frostSword/note", "");
cfg.has("drunkenSword/initialStacks");                  // 想知道"用户到底写没写"
```

**模组分组里的字段名由模组自己定**（游戏不校验、不映射、出错也不报），
因为那是模组自己的旋钮；游戏只保证**路径查找 + 缺失返回默认值**。

---

## 五、技能数据：哪些能外置、哪些不能

`Skill` 的字段（`Skill.java:36-47`）分成三类：

| 类别 | 字段 | 能不能外置 | 说明 |
|---|---|---|---|
| **纯数值** | `hpMagnification` / `atkMagnification` / `defMagnification`（`:38-40`） | ✅ **能，且最该先做** | 就是"200% 还是 300%"，改了不影响任何逻辑 |
| **纯数值** | `aims`（`:41`）、`coolDown`（`:42`） | ✅ 能 | 注意 `aims` 的语义（0=自身 / -1=全体 / 正数=选 N 个，`Skill.java:20-22`）要写进 README |
| **纯数值** | `consumedMana`（`:45`，`Mana(amount, ElementSort)`） | ✅ 能 | 写成 `{amount, element}`；`null` = 无消耗，要有明确写法（建议 `"consumedMana": null` 或干脆不写该键） |
| **半数值** | `isForEnemies`（`:44`）、`tags`（`:47`，AI 权重） | ⚠️ 能，但**整块覆盖** | `tags` 逐项合并会让"想删掉一个 tag"变得做不到；整块覆盖更好理解 |
| **行为** | `comeToEffect(...)` / `canUse(...)` 的重写体 | ❌ **绝对不能** | 那是代码。**任何"用 JSON 描述技能逻辑"的设计都是另一种编程语言**，本项目不需要 |
| **由玩法推出来的动态值** | "每层【醉意】+1.5 倍率"、"燃点 ≥8 时额外 0.6×生命上限"（`ActorLiXiaoYan.java:42-94` 那组常量） | ✅ **系数已外置**（2026-10-03，见**第十二节**）：**算式留代码，算式里的每个数**变成技能自报的具名键；❌ **整条公式仍然不外置**（那要表达式求值器/DSL，用户没拍板，不做） | 抽象方式见 §12.3：技能实现 `SkillCoefficientTunable`，把"每层打几段 / 每层给多少倍率 / 满层收尾多大"报出来 |
| **生命周期状态** | `nowCoolDown`（`:43`）、`extraDamage`（`:46`） | ❌ 不能 | 运行期状态。`extraDamage` 还有"伤害算完清零"的约定（`:46` 注释），外置等于给用户一个坑 |

**技能怎么定位（键的设计）** —— 这是技能外置唯一的结构性难点：

- `Skill` **没有 id 字段**（`:36-47` 全部字段里没有），
  而 `Effect` / `Item` / `Entity` 都有（`Effect.getID()`、`Item.getId()`、`Thing.getId()`）；
- 控制器持有的技能实例是**每实体 `copy()` 出来的**（`MODDING-GUIDE.md:232-234`），
  所以"按对象身份定位一条技能配置"不可行；
- 因此第一版用 **`"<实体完整id>#<技能名>"`**（名字就是 `super("举杯邀月", …)` 的第一个参数）。

**这条键的风险很明确：改技能名 = 配置静默失效**（`/data` 的键名契约是同一个问题，`70-DATA.md:21-22`）。三条对策：

1. **自测固定它**：阶段 0 的自测枚举 `World.getEntityList()` 里每个 `LivingThing` 的
   `getController().getSkills()`，断言**每个技能的 `实体id#技能名` 都出现在写出的默认配置里**（还能抓到重名）；
2. **警告要显式**：配置文件里出现"实体不存在"或"技能名不存在"时，**打印 `找不到技能 xxx#yyy`**，不静默忽略；
3. **将来再升级**：真要长期做，就给 `Skill` 加一个 `id` 字段（**加字段是兼容的**：老构造器 `Skill(String name, …)`
   保留，id 默认取 name）。这是个**加法**，不影响 `MODDING-GUIDE.md` 的任何一条契约 —— 但不放进第一版，
   因为它要动 `Skill` 的复制构造器与所有 25 个技能类的构造，属于"顺带重构"，本项目明令禁止跟数值改动混在一起。

---

## 六、魔法数字：什么该外置、什么不该

**判据（三条全中才外置）**：

| # | 问题 | 是 | 否 |
|---|---|---|---|
| 1 | **会随平衡/手感变吗？**（用户改它不需要重新理解机制） | 继续问 2 | **留在代码里**，写好注释说明"为什么是这个数" |
| 2 | **改了会影响机制/结构吗？**（改了之后某段逻辑就不再成立） | **留在代码里** | 继续问 3 |
| 3 | **它有名字和量纲吗？**（能写进 JSON 的键名 + 单位） | **外置** | 留在代码里 |

按这条判据，把项目里的"数字"分四类：

| 类 | 例子 | 处置 |
|---|---|---|
| **① 平衡旋钮（该外置）** | `FlameReaver.SUMMON_HP_COST_RATE=0.03`（`:89`）、`DISASTER_POWER_ATTACK_BONUS=0.08`（`:94`）、`DAMAGE_REDUCTION_LAYERS=2`（`:99`）、`DAMAGE_REDUCTION_PER_LAYER=0.25`（`:104`）、`CONTAINER_LIMIT=0`（`:122`）、`COMPLETE_CONTAINER_CHANCE=0.34`（`:130`）、`PHASE_TWO_DAMAGE_REDUCTION=0.7`（`:147`）、`BrokenContainer.HP_RATIO=0.15`（`:209`）、`ATTACK_RATIO=0.4`（`:219`）、`COMPLETE_HP_RATIO=0.25`（`:224`）、`InsectBoss.BASE_HP_MAX=80000`（`:20`）、`ActorLiXiaoYan` 顶部那 9 个常量（`:42-94`） | **搬进 `GameRules.json` / `EntityData.json`**，代码里的 `private static final` 改成"构造时读一次配置，读不到用原值" |
| **② 结构常量（不该外置）** | `ElementSort` 的 6 个值、背包 63 格（`PlayerOne.java:24` 等）、`Skill.aims` 的 0/-1 语义、`ActionSignal` 的 5 个枚举、`TagType` 的 6 个键、效果 `lastTime` 的"额外回合不递减"规则 | **留**。改了不是"数值变了"，是"机制坏了" |
| **③ 公式骨架（本版留，只开只读的口子）** | `LivingThing.java:244-246` 的 `200/200/110`、`DamageCalculate.java:120` 的 `level*10+200`、`initialMana` 的 `+200/+20`（`LivingThing.java:567`）、`Mana` 的构造语义（`Mana.java:10-13`，传进来的 `amount` 同时当初始值与上限；`:26-30` 的 `setAmountMax` 会把超出的当前值夹回上限） | **默认留**，但**在 `GameRules.json` 里放一份"只读镜像"**（加载时把它作为默认值传进公式，用户不改就等价于现在的硬编码）。理由：它们满足判据 1、3，但**不满足判据 2** —— 改了 `110` 会让 `PROJECT-ANALYSIS-2026-09.md` §4.2 那套"每 1.0 倍率 ≈ 880 伤害"的标定全部作废。**必须先在文档里写清"改它等于重新标定"** |
| **④ 显示常量（不该外置）** | 攻击行格式（`LivingThing#printAttackLine`）、阵营标注、`ConsoleColor` 的色号、日志格式 | **留**。它们不是"数值"，是"格式"，`PROJECT-ANALYSIS-2026-09.md` §5.1 明确写着这套格式是用户定的、别改回去 |

**分界线一句话**：**"玩家会抱怨数值不平衡"的东西外置；"玩家会抱怨游戏坏了"的东西留在代码里。**

---

## 七、分阶段路线（每步自洽、可单独停、不留半成品）

每一步的**统一验收顺序**：

```powershell
# ① 静态自查（快）
powershell -ExecutionPolicy Bypass -File .\check-sources.ps1          # 期望：Java files: 203+ / CHECK OK

# ② 全量编译 + 自测（权威；AI 用自带 JDK，用户用脚本）
powershell -ExecutionPolicy Bypass -File .\test-command-system.ps1   # 期望：通过 N 条，失败 0 条

# ③ 用户进游戏：选人列表 → 打一局 → 看手感/数值
```

> 注：本次写文档的会话里 `java.exe` 被沙箱拒绝（`check-sources.ps1` 能跑，`test-command-system.ps1` 不行），
> 所以上面 ② 的"AI 自己跑"这条要在能跑 `java` 的会话里做。

---

### 阶段 0（半天，零行为变化，**建议先做**）：把键名钉死 + 写出"当前全量默认值"

> ✅ **已完成（2026-10-03，本次落地）**。落地结果、与本文的差异、踩到的坑见本节末尾的
> 「落地记录」；与原文不符的地方以「落地记录」为准。

**做什么**

1. 新增 `configLoadingSystem/DataKeys.java`：把 §3.4 那张"配置键 ↔ `/data` 数据名"的表落成常量/枚举；
2. 新增一个**只读**的生成器 `ConfigDefaultWriter`：遍历 `World.getEntityList()` / `getItemList()`（**只读**，`World.java:375/181`）
   与每个实体的 `getController().getSkills()`，把当前运行值 dump 成 `EntityData` / `SkillData` 两份**全量默认文件**，
   写到 `config/gameConfig/`（覆盖前先备份成 `*.bak`）；
3. 自测新增（`TestCommandSystem`）：
   - **键名契约**：`DataKeys.ENTITY_KEYS` 里每个名字都能在 `DataBridge.dataNames(...)`（`DataBridge.java`）里找到，
     反之 dump 出来的**标量**键也都在表里 → **`/data` 改名时这里立刻红**；
   - **技能名清单**：每个技能的 `实体id#技能名` 都出现在生成器输出里（含重名检测）；
   - **13 参构造器的参数顺序/个数**：用反射断言 `LivingThing.class.getConstructor(...)` 的参数类型序列，
     防止有人"顺手优化签名"（这条也是给模组作者的护栏）。

**不做什么**：不改任何数值、不加任何读取逻辑（生成器只读）。

**验收**：`check-sources.ps1` → 编译 → 自测（**483 → 约 490**）→ 用户看一眼新生成的两份 JSON 是否好懂。

**能停在这里吗**：能。停下 = 项目多了一份"当前数值总表"和三条护栏，零风险。

#### 落地记录（2026-10-03，已完成）

| 项 | 实际做法 |
|---|---|
| `DataKeys` | 新建（**506 行**）。四组常量（`Base` / `Derived` / `Temporary` / `ReadOnly`）+ 一份 `META`（键 → Java 类型 / 是否在 `/data` 里 / 一句话说明）+ `GROUPS`（键 → 分组）。**`META` 41 个键**（`base` 22 + `derived` 4 + `temporary` 15），**只读 27 个**，另有 2 项"不在 `/data` 里"的配置项（`manaGrow` 块与背包格数）。**没有** `ENTITY_KEYS` 这个名字（原文写的），对外入口是 `configurableKeys()` / `isConfigurable(String)` / `canonical(String, boolean)` |
| `ConfigDefaultWriter` | 新建（**553 行**，含一个 60 行的极简 JSON 排版器）。四个产物入口：`entityDataJson()` / `skillDataJson()` / `referenceJson()`（只生成文本，不碰磁盘）、`writeAll(File)`（写盘，**已存在的文件不覆盖**）。`SkillData.json` 是**只读样例**——本版游戏不读它（技能加载是阶段 2） |
| 落盘纪律 | `EntityData.json`（游戏会读）、`SkillData.json`（只读样例）、`EntityData.default.json`（"出厂值"参考副本）三份。**只在缺失时写**，一律 `Files.writeString(..., UTF_8)`。原文写的"覆盖前先备份成 `.bak`"改成了"参考副本"这一份——生成器根本不覆盖已有文件，就不需要 `.bak` |
| `ConfigLoader` 的两处既有缺陷（原文 §8.3 D2 / D5） | ✅ 顺手修了：① 写默认配置改成显式 `StandardCharsets.UTF_8`；② 标签配置改成**逐项容错**（认不出的标签类型 / 类型不对的权重只跳过那一项，不再让整份配置抛异常），报错文案带**文件路径 + 键名 + 行号**，并且同时打到控制台与 `logs/latest.log` |
| 自测新增 | `testConfigDefaultsAndPatch`（**48 条**）：键名契约 5 条 + 生成器 9 条 + dump 契约 4 条 + 构造器契约 3 条 + 补丁器 20 条 + 落盘 6 条 + 收尾 1 条。**基线 483 → 531** |
| 静态自查 | `check-sources.ps1` → `Java files: 206` / `CHECK OK` |

#### 落地时踩到的坑（原文没有的）

1. **`@DataFlatten` 会让编译器给构造器插一个合成参数，而这个参数在反射的两个口径里不一致**：
   `LivingThing.class.getDeclaredConstructors()` 里那个 13 参构造器，
   `getParameterTypes().length` 是 **13**、`getParameters().length` 是 **14**，
   而且 `getParameters()` 返回的形参名是 `arg0…arg13`（class 里没有 `MethodParameters` 属性）。
   → 护栏改成"**按形状找**"（头两个 `String`、最后一个是 `ElementSort`）+ 类型序列逐位比，
   参数名那条断言在没有真名时退化成"非空且互不相同"。**这也是本文标题里"13 参"的由来**
   （源码里的字面参数个数确实是 13，反射要多算一个）。
2. **`DataKeys` 里不能有两个同名的 `public static final String`**：`check-sources.ps1` 的
   `[DUP-FIELD]` 认的是**文件里所有常量**（不看嵌套类），`Base.MANA_GROW` 与
   `Alias.MANA_GROW` 会当场红 → 别名常量改名成 `Alias.MANA_GROW_ALIAS`。
3. **五行那 20 个字段住在 `ElementProfile` 组件里**：核对"配置键有没有对应字段"时必须
   **下潜进 `@DataFlatten` 组件**（`DataKeys.auditAgainst` 会递归一层），否则那 20 个键全报"没有对应字段"（假失败）。
4. **"dump 出来的键" 与 "`dataNames()` 列的键"是两个口径**（原文 §8.3 D10 已经警告过，
   这次实测确认）：`tags` / `effects` / `damageReductions` / `controller` / `participateFight` /
   `presentTurn` / `showSpecialMes` / `damageModifiers` 这 8 个键今天 dump 不出来
   （空集合被省略、行为/物理对象被跳过）。自测因此**两个口径都查**：拿"真的 dump 出来的键"当主口径，
   `dataNames()` 只用来核对"配置键有没有对应字段"。
5. **`description` 可能是 `null`，而 `null` 字段会被 `DataBridge` 整个跳过** → 生成器里为了判断
   "这个键到底认不认"，会临时设一个空串再原样设回去（`ConfigDefaultWriter#readDescription`）。
   这一处是生成器里**唯一**会写对象的地方，写的是同一个值，不改变任何数值。
6. **模组/角色自己的状态键**（`ignition`、`coreflame`、`phaseTwo`、`kind`……共 26 个）会出现在
   `/data` 里但不属于"实体通用属性" → 自测里逐条列进 `SUBCLASS_STATE_KEYS` 常量，
   新增一个就会让那条断言变红，逼人显式登记（而不是悄悄混进"已知的"那一堆里）。

---

### 阶段 1（1 天，低风险）：实体数据外部加载（**这一步就能交付用户想要的东西**）

> ✅ **已完成（2026-10-03，本次落地）**。落地记录见本节末尾。

**做什么**

1. `ConfigLoader` 新增 `loadEntityData()`：读 `config/gameConfig/EntityData.json`（缺失就写默认），
   对 `World.getEntityList()` 里的每个模板做**一次**补丁；
2. 补丁器 `EntityDataPatcher`（新文件）：
   - 按 §3.4 的查找顺序定位目标；
   - 顺序固定：`level` → 五行/速度/成长/类型/描述/质量 → 背包格数 → `derived` → `hp`；
   - 写 `hpGrow`/`attackGrow`/`defenceGrow` 时**自动补一次 `setLevel(...)`** 重算（§8.2 风险 1）；
   - 未知键/类型错 → 收集起来，结尾 `System.out` 汇总；
3. `GameMain.java:71` 后面加一行 `ConfigLoader.loadEntityData();`（**紧挨着现有那行**，顺序即合并顺序）。

**不做什么**：不碰技能、不碰 `GameRules`、不碰模组接口。

**验收**

| 检查 | 怎么验 |
|---|---|
| 不写配置文件时**数值一个都不变** | 自测断言"加载前后 `getHp/getAttack/getDefence/getHpMax/getSpeed/五行抗性` 全等" |
| 写 `{"entities":{"game_official_content:playerOne":{"base":{"speed":200}}}}` 后**选人列表与战斗里速度都是 200** | 自测断言 + 用户进游戏（`/list` 看速度） |
| 写 `hpGrow` 后**血量真的变了**（而不是"改了没反应"） | 自测：改 `hpGrow` → 断言 `getHpMax()` 变了 |
| 副本带上补丁值 | 自测：`copy()` 之后断言副本的每个补丁字段相等 |
| **`/data` 键名一个都没变** | 自测（阶段 0 的两条契约测试继续绿）；用户跑 `/data get entity @s` 看键数（`ActorLiXiaoYan` 应为 **62**，`70-DATA.md:81-82`） |

**能停在这里吗**：能。停下 = 实体数值已经可以外部改，技能与魔数还是硬编码（用户会明确知道）。

#### 落地记录（2026-10-03，已完成）

| 项 | 实际做法 |
|---|---|
| `EntityDataPatcher` | 新建（**995 行**）。入口 `apply(JSONObject, note)` / `applyJson(String, note)`；报告 `Report`（应用 / 跳过 / 错误三张清单 + `isClean()` + `print()`）；工具 `stateOf(LivingThing)`（给自测对比用）与 `Snapshot`（真模板的完整快照，能原样恢复） |
| 键的两种写法都认 | `{"base":{"speed":200}}` / `{"derived":{"hpMax":80000}}`（生成器写的形式）与**裸键** `{"speed":200}` 等价，同时出现时**子块优先**；`manaGrow` 块可以写在实体下、`base` 下或 `derived` 下；别名 `element` → `elementSort` |
| 定位规则 | **只用完整 id 精确匹配**（`game_official_content:brokenContainer` 与 `…:completeContainer` 是同一个类的两条模板，按类匹配会让它们共用一份配置）；短名当且仅当"注册表里只有一条 id 以它结尾"时才认，歧义时给出"注册表里有 N 条"的报错 |
| 应用顺序 | 与设计一致：`level` → 名称/质量/类型/描述/速度/元素 → 三个成长系数 → 五行抗性 → 法力成长 → 背包格数 → `derived.hpMax/attack/defence` → `derived.hp`（**`hp` 最后**） |
| 容错 | 未知键 / 类型不对 / 找不到目标：只跳过那一项并在结尾汇总（`[配置跳过] 文件:行 键「x」：原因`），**一个坏键不废整份配置**；整份 JSON 坏掉时只打印并返回，**不覆盖用户文件** |
| 每个模板只补一次 | `Report` 里一张按**对象身份**的去重表（`Collections.newSetFromMap(new IdentityHashMap<>())`），`markBound` 返回 `false` 就跳过 |
| 调用点 | `GameMain#gameInitialize` 里紧挨着 `ConfigLoader.loadConfig()` 之后新增 `loadEntityDataConfig()`，**与标签配置各自独立 try/catch**（一份坏掉不影响另一份） |

#### 落地时踩到的坑（原文没有的，**两条都是真缺陷**）

1. ★★★ **`Entity#setLevel` 重算派生值时写的是旧上限**：它的顺序是
   `setHp(新基础值)` → `setHpMax(getHp())`，而 `LivingThing#setHp`（`LivingThing.java:2116`）会
   **夹到"当前"的 `getHpMax()`** —— 所以只要新值比旧上限大，写进去的永远是旧上限：
   实测 125 级玩家一写 `hpGrow:50`，`hpMax` 仍是 **4664**（应为 `(125-1)×50+200 = 6400`）。
   **这就是 §8.2 风险 1 那个"改了没反应"的真正原因**（比原文写的"忘了调 setLevel"更深一层）。
   → 对策：补丁器**不调 `setLevel`**，改用 `recalculateDerivedStats`（按同一套公式先写 `hpMax`
   再写当前血量，顺序与 §8.2 风险 4 一致）。语义还更稳：**当前血量只会因为上限变小被夹下来，
   不会因为上限变大而被补满**。`Entity#setLevel` 本身**一个字没动**（它是公共路径，不顺手改）。
2. ★★ **补丁器最初漏了五行抗性**：`applyElementValues` 只处理了法力成长块，
   `fireResistance` 那 5 个键虽然写在默认文件里却没人读 —— 症状是"默认文件里有，改了没反应"，
   而且是**静默**的（没有跳过、没有报错）。已补上，并把"键写出来了就必须有人读"这条
   由 `testConfigDefaultsAndPatch` 的"全量默认值打回去、数值逐字段不变"那条断言兜住
   （漏读的键打回去之后 `stateOf` 就会不一样 → 立刻红）。
3. **`applyJson` 每次都会新建一份 `Report`**：所以"同一份配置连打两遍"这件事在自测里
   必须**复用同一个 `Report`**，否则去重表每次都是空的（这条只是自测口径，不是加载器的行为）。
4. **自测要在真模板上试补丁**：注册表里存的就是模板本身，只测副本证明不了"真模板会被改对"。
   → `EntityDataPatcher.Snapshot` 把补丁会碰的每个字段都记下来，用例跑完原样放回去，
   最后还有一条"所有模板都还原成补丁之前的样子"的总断言。
5. **落盘用例只碰 `out/tmpConfig`**：真 `config/` 目录在整个自测里**一个字都没动**
   （跑完 `config/gameConfig/` 下仍然只有 `TagConfig.json` 与 `PropertyConfig.json`）。
   `EntityData.json` 由**游戏启动**时首次生成（`loadEntityData()` 里"缺文件就写默认值"那一步）。
6. **`version` 一开始写成了字符串 `"1"`**：`warnAboutVersion` 拿 `asLong` 解析，读出来是 `null`
   → 每次启动都会刷一条"version 是 1，本版只认 1"的假警告。生成器改成写**数字**，
   自测加一条 `asLong(root.opt("version")) == 1L` 的断言钉住。
7. **`Entity#setLevel` 那条坑的连带影响**：`EntityDataPatcher` 因此**不调 `setLevel`**，
   而是自己按同一套公式重算（见坑 1）；只有"用户在配置里显式写 `level`"那一步仍然走 `setLevel`
   （那时用户给的是明确等级，语义上就该以他为准 —— 但要注意它同样会把 `hpMax` 写成被夹过的值，
   所以生成器给出的默认文件里 `level` 与 `derived.hpMax` 是**配对**的，删一个要想想另一个）。
8. **端到端验证过**：在临时目录里跑了一次真正的启动路径
   （`GameMain.gameInitialize()` → 生成三份文件 → 打补丁 → `实体数据配置:应用 180 项，跳过 0 项，影响 9 个模板`
   → 无异常），用的就是用户启动游戏时那条路。跑完临时目录已删除。

---


### 阶段 2（1 天，低风险）：技能数值外部加载

> ✅ **已完成（2026-10-03，本次落地）**。落地记录见本节末尾。**实际改动比原估的大**：
> 修掉了 9 个技能类的 `copy()`（见「落地时踩到的坑」第 1 条），不修的话配置根本传不到副本。

**做什么**

1. `ConfigLoader.loadSkillData()` 读 `SkillData.json`；
2. 补丁器按 `实体id#技能名` 遍历每个模板 controller 的技能（`controller.getSkills()`，`UniversalController` 里那份），
   用**现有 setter**改：`setHpMagnification` / `setAtkMagnification` / `setDefMagnification` / `setAims` /
   `setCoolDown` / `setConsumedMana` / `setForEnemies` / `setTags`；
3. **`nowCoolDown` 不写**（运行期状态），**`extraDamage` 不写**（§5 表格）。

**不做什么**：不做"技能逻辑内联"、不做"动态倍率表"（§5 最后一行）。

**验收**：把某技能倍率改成 0 → 游戏里伤害变 0；改回去 → 恢复；`check` 断言"配置里的倍率 = 运行期技能倍率"；
用户进游戏打一局看手感。

**能停在这里吗**：能。

#### 落地记录（2026-10-03，已完成）

| 项 | 实际做法 |
|---|---|
| `SkillDataPatcher` | 新建。入口 `apply(JSONObject, note)` / `applyJson(String, note)`；报告 `Report`（应用 / 跳过 / 错误三张清单 + `isClean()` + `print()`）；自测工具 `stateOf(Skill)` / `find(key)` / `all()` / `SkillSnapshot`（能原样恢复一个技能） |
| 键与文件 | 顶层 `version` + `skills`；键 = `<实体完整id>#<技能名>`。**沿用生成器已经在用的那个键形**（阶段 0 就定了），解析时只按<b>第一个</b> `#` 切分（技能名里可以有 `#`） |
| 写出去的字段 | **8 项**：`aims` / `coolDown` / `hpMagnification` / `atkMagnification` / `defMagnification` / `forEnemies` / `consumedMana` / `tags`。生成器从"只写 5 项样例"升级成"写全 8 项、且游戏真的读它" |
| `consumedMana` 的三种写法 | **不写这个键** = 保持构造器里的消耗；**写 `null`** = 取消消耗（盗火行者那一族的技能就是这么写的，它们构造器里 `setConsumedMana(null)`）；**写对象** = `{amount, element}`。原文 §5 只写了前两种，这里把三种都实现并写进自测 |
| `tags` 的写法 | `{"ATTACK":5}`；**整块覆盖**（原文就是这么定的：逐项合并会让"想删掉一个 tag"做不到） |
| 定位规则 | 与 `EntityDataPatcher` 完全同口径：完整 id 精确匹配优先，短名当且仅当"注册表里只有一条 id 以它结尾"时才认；**找不到技能时显式点名**并把它现有的技能名列出来（技能改名 = 配置静默失效，必须看得见） |
| 每个技能只补一次 | `Report` 里一张按**对象身份**的去重表（`IdentityHashMap`） |
| 调用点 | `GameMain#gameInitialize` 里紧挨着 `loadEntityDataConfig()` 之后新增 `loadSkillDataConfig()`，**各自独立 try/catch** |
| 子类自己的额外数值 | 新增可选接口 `cn.gfhnv.game.skill.NumericSkillTunable`（`getExtraNumericValue` / `setExtraNumericValue` / `extraNumericKey`）。目前只有 `RestorationHealthSkill` 实现它（`neededManaScale` = 释放门槛 90）—— 那个值住在子类私有字段里，`Skill` 基类没它的位置，不给个口子就永远配不到 |
| 自测新增 | `testSkillDataPatch`（**22 条**）：生成器 8 条 + 全量默认值 4 条 + 改倍率/消耗/权重 6 条 + 副本与去重 4 条 + 容错 6 条 + 覆盖面 1 条 + 伤害为 0 1 条 + 落盘 4 条 + 收尾 1 条 |
| 静态自查 | `check-sources.ps1` → `Java files: 208` / `CHECK OK` |

#### 落地时踩到的坑（原文没有的，**第 1 条是真的会让配置完全失效**）

1. ★★★ **9 个技能类的 `copy()` 是 `return new Xxx()`，配置永远传不到副本**。
   本项目的补丁语义是"打在**模板**上，副本靠 `copy()` 白送"（§2.2 的整个立论基础），
   而这些类的 `copy()` 返回的是**全新实例**（构造器把倍率/冷却/消耗重新装一遍）：
   `GunShoot` / `Freeze` / `InsectBossSkillSummonEnemy` / `normalSkills.NormalSkill` /
   `normalSkills.UltimateAttack` / `awakenSkills.Counterattack` / `awakenSkills.FoundationStardeathVerdict` /
   `awakenSkills.LastAttack` / `actorLiXiaoYanSkills.CommonAttack` / `PyrohemicPumping` / `UltimateAttack`。
   实测：模板上的「枪射击」已经改成 `atkMagnification:3.25`，选人时 `copy()` 出来的副本<b>仍然是 7.5</b>。
   → 对策：给这些类补**复制构造器**，并把 `copy()` 改成走复制构造器（`return new GunShoot(this)`）。
   这不只是"为了配置"——**同一个类里两个技能共用一套数值，一个 copy 带、一个 copy 不带，本来就是不一致的**。
   → 自测里加了一条**覆盖面护栏**：遍历注册表里每个技能，改一次倍率再 `copy()`，
   副本必须带着改后的值（`return new Xxx()` 会让这条立刻红）。
   ⚠️ 顺带修掉一个真缺陷：`RestorationHealthSkill` 的复制构造器**漏了 `neededManaScale`**，
   于是副本的门槛永远是 0（等于没有门槛）。
2. **`RestorationHealthSkill` 的 `neededManaScale` 不在 `Skill` 基类里**：`Skill` 只有"三个倍率 / 目标数 /
   冷却 / 消耗 / 阵营 / 权重"，这个门槛是子类自己的字段 → 见上面第 7 行（新增可选接口）。
3. **`tags` 的整块覆盖要靠"整块写出去"才自洽**：生成器原来只写 5 项标量，如果 `tags` 不写进默认文件，
   用户就不知道"默认权重是多少、要覆盖成什么"。现在写全 8 项，
   于是"把默认值打回去，数值逐字段不变"这条断言连 `tags` 一起盖住了。
4. **文档 §6.3 记录的两处现状必须原样保留**，自测专门钉住：
   冰冻的 `7.5` 倍率（`game_official_content:iceInsect#冰冻`）与普攻自带的 `DamageEnhanceEffect(1,1)`；
   后者属于**行为逻辑**（在 `comeToEffect` 里 `new` 出来的），按 §5 的表格<b>不外置</b>，
   自测断言"运行期倍率就是代码里那个数、tag 还在"。
5. **生成器与加载器必须"同一套键"**：`ConfigDefaultWriter.collectSkillPatches()` 写出去的键
   与 `SkillDataPatcher.patch()` 读的键是同一批常量 → 由"全量默认值打回去、逐字段不变"那条兜住；
   少写一个键就会立刻红（不是"静默不生效"）。
6. **浮点加减不互逆**：自测的 `copy()` 护栏最初写成 `set(原值 + 1.5)` 再 `set(probe - 1.5)` 还原，
   `0.3 + 1.5 - 1.5 = 0.30000000000000004` → 后面的"逐字段不变"断言全红。
   改成**用原值直接还原**。

---


### 阶段 3（1 天，中风险）：魔法数字 + BOSS 旋钮

> ✅ **已完成（2026-10-03，本次落地）**。落地记录见本节末尾。**原文"每个字段只在静态初始化里读一次"
> 那条做法被判为不安全并改掉了**：见「落地时踩到的坑」第 1 条。

**做什么**

1. `ConfigLoader.loadGameRules()` 读 `GameRules.json`，落成一张**静态规则表**（例如 `GameRules.getLong("formula.hpBase", 200)`）；
2. 把 §6 表格 **①** 那一列常量改成"启动时从规则表读一次，读不到用原字面量"；
   实现要点：**每个字段只在静态初始化里读一次**，不要每帧 `getLong`（避免热路径里的 Map 查询）；
3. `FlameReaver` / `BrokenContainer` / `InsectBoss` / `ActorLiXiaoYan` 顶部的 `private static final`
   **保留**（当默认值），改成 `private static final double X = GameRules.getDouble("flameReaver.summonHpCostRate", 0.03);`。

**风险控制**：这一步最容易踩"改了公式常量 → 全部标定失效"，
所以 **`formula` / `mana` 两块默认值必须与现在的字面量逐字相等**，并由自测**证伪式**验证：

```
断言：不写 GameRules.json 时，DamageCalculate.calculate(同一对实体, 同一技能) 的结果
      与"写死时代"的期望值逐位相等（可用几个固定样例的硬编码期望值）
```

**验收**：`GameRules.json` 里改 `phaseTwoDamageReduction` → 进游戏打盗火行者二阶段，伤害明显变化；
改 `formula.attackBase` → 面板攻击变化（同时**文档要警告这会作废标定**）；用户打一局。

**能停在这里吗**：能。停下 = 官方内容的数值全部可调，模组还不能。

#### 落地记录（2026-10-03，已完成）

| 项 | 实际做法 |
|---|---|
| `GameRules` | 新建（静态规则表）。`getDouble/getLong/getInt(key)` **不需要传默认值** —— 缺键时回落到 `RuleDefaults`，所以"生成器写出去的值"与"取不到的默认值"**必然是同一个字面量**（不会各抄一份而漂移）。另有 `put` / `beginLoad` / `freeze` / `isKnown` / `allKeys` / `resetForTest` |
| `RuleDefaults` | 新建：**出厂值的唯一真相**（34 个 `public static final` 常量 + `all()` / `of()` / `number()`）。原文要求"代码里的值就是默认值"，这个类就是那句话的落地：改这里等于改出厂值 |
| `GameRulesPatcher` | 新建：读 `{段: {键: 数字}}`，拼成 `段.键` 再查表。未知段 / 未知键 / 类型不对**只跳过那一项**并汇总；报告带 `appliedEntries` / `skippedEntries` / `errors` |
| 生成器 | `ConfigDefaultWriter.gameRulesJson()` 按 `GameRules.allKeys()` 分组写出去；**整数型键写整数、浮点型键写小数**（`GameRules.isIntegerKey`，靠 `RuleDefaults` 里的值类型判断），所以默认文件读起来量纲清楚 |
| 调用点 | `GameMain#gameInitialize` 的**第一行**：`loadGameRules()` 排在 `World.addMod(new OfficialGameContent())` 之前，结束时 `GameRules.freeze()` —— 见坑 1 |
| 实际接线的常量 | `LivingThing`（`formula.hpBase/defenceBase/attackBase` + `mana.mainBase/otherBase`）、`DamageCalculate`（`formula.levelDefenceFactor/levelDefenceBase`）、`FlameReaver` 的 7 个旋钮 + `baseHpMax`、`BrokenContainer` 的 3 个比例、`InsectBoss.BASE_HP_MAX`、`ActorLiXiaoYan` 的 10 个常量 |
| ~~`limits` 两项~~ **已于 2026-10-03 删除** | **只登记、不接线**：代码里没有任何"等级上限 / 背包上限"的检查点（原文附录 C 第 7 条已说明）。用户 2026-10-03 明确要求"删除 `limits.maxLevel` / `maxInventorySlots`，不加限制"，于是**整节删掉**（`DataKeys.Rule.Limits`、`RuleDefaults` 的两个常量、`GameRules.knownKeys()` 的两项、`GameRulesPatcher.SECTIONS` 的 `"limits"`、生成的默认文件里那一节）。**没有顺手加任何限制**：`knownKeys()` 31 → 29，自测里两条计数断言（`认识 29 条规则键` / `29 条规则全部生效`）跟着改，其余断言一个字没动 |
| `PHASE_TWO_HP_THRESHOLD` | **确认已被删掉，没有加回来**；自测里有一条反向断言（`isKnown("flameReaver.phaseTwoHpThreshold") == false` 且生成的文本里不含这个键） |
| 自测新增 | `testGameRules`（**21 条**）：生成器 3 条 + 覆盖面 2 条 + 全量默认值 4 条 + 公式 1 条 + 法力 1 条 + BOSS 旋钮 5 条 + 伤害 1 条 + 容错 4 条 + 冻结 1 条 + 收尾 2 条 |
| 静态自查 | `check-sources.ps1` → `Java files: 214` / `CHECK OK` |

#### 落地时踩到的坑（原文没有的，**第 1 条推翻了原文的实现要点**）

1. ★★★ **"`private static final X = GameRules.getXxx(...)`"这种写法会让配置静默失效，不能用**。
   静态常量在"**那个类第一次被加载**"时取值，而类的加载时机**不受配置文件控制**：
   - 生产路径下它"碰巧"是对的（`loadGameRules()` 排在 `new OfficialGameContent()` 之前）；
   - 但**自测里必然错**（实体类在自测代码里早就被加载过了），于是"改了 baseHpMax，新造的 BOSS 还是 80000"；
   - 更要紧的是：**一旦这个类先被别的路径加载过，用户改的配置就无声无息地不生效**，而且不可复现。
   → 对策：**改成"用的时候现读"**。`FlameReaver`/`BrokenContainer`/`InsectBoss`/`ActorLiXiaoYan` 里那一组
   全变成读取方法（`summonHpCostRate()` / `hpRatio()` / `baseHpMax()` …），
   私有常量全删；`ActorLiXiaoYan` 的 10 个 `public static final` 常量因为被三个技能类引用，
   改成 `ActorLiXiaoYan.Rule.highIgnition()` 这样的**公有读取方法**，并另立 `ActorLiXiaoYan.Defaults`
   放原来的字面量（引用它的地方只有 9 处，都在本仓库里）。
   只有**热路径**（`DamageCalculate` 的等级防御系数）保留"类加载时读一次 + static final"，
   因为那里每次伤害都查一次 Map 不划算，而 `DamageCalculate` 只可能在开局之后被加载。
   → 结论一句话：**"启动时读一次"只对"确定在配置之后才第一次使用的类"成立，别把它当成通用写法。**
2. **`GameRules` 的两种"缺省"要分清楚**：`getDouble(key)` 是"配置里没写 → 用 `RuleDefaults`"，
   与"配置里写了 0"是两回事（后者会真的返回 0）。自测里两种都覆盖（容错那条写坏值，
   生效那条写好值）。
3. **冻结是必须的**：`freeze()` 之后 `put` 一律返回 `false`。没有这一步，
   "类加载时读一次"的那些字段会在半路变值，行为取决于谁先加载 —— 那种 bug 复现不了。
4. **`PlayerOne.copy()` 不会重算三围**（复制构造器只抄派生值），所以"改了 `formula.*` 之后
   拿模板 `copy()` 一个来验面板"是验不出来的 —— 自测里改成 **`new PlayerOne(125)`**。
   这同时也说明**规则里的公式只影响"启动时造出来的模板"**，运行时新 `new` 出来的召唤物也算"新造"。
5. **减伤层数是在 `whenFightStart` 挂的，不在构造器里**：自测要验"改了层数/每层比例之后开局减伤变了"，
   必须显式调用一次 `whenFightStart`（构造器不挂，`getDamageTakenMultiplier()` 那时是 1.0）。
6. **`{@value #常量}` 在常量变成嵌套类成员之后就解析不到了**：`{@value #IGNITION_MAX}` 要改成
   `{@value Defaults#IGNITION_MAX}`（javadoc 只在构建 javadoc 时才报错，`javac` 不管，容易漏）。
7. **`check-sources.ps1` 的 `[DUP-FIELD]` 认的是文件里所有常量名**（不看嵌套类）：
   `Rule.FlameReaver.BASE_HP_MAX` 与 `Rule.InsectBoss.BASE_HP_MAX` 会当场红 →
   后者改名成 `Rule.InsectBoss.BASE_HP`（`Rule.SkillKeys.TAGS` 与 `ReadOnly.TAGS` 同理，改 `WEIGHT_TAGS`）。

---


### 阶段 4（1 天，中风险）：模组接口

> ✅ **已完成（2026-10-03，本次落地）**。落地记录见本节末尾。
> **没有按原文写"给 `mods/drunkenSword` 加一个可选示例"** —— 原因见落地记录里的"模组示例"一行。

**做什么**

1. 新增 `cn.gfhnv.game.mod.config` 包：`ModConfig`（注解）、`ModDataAware`（接口）、`ModConfigDocument`（路径读取）；
2. `ConfigLoader.loadModData()`：遍历 `World.getModList()`，读 `config/data/<modid>.json`（缺失就跳过，**不写文件** ——
   模组目录不是游戏的地盘），做两件事：① 把 `common` 段当"更高优先级的补丁"打进官方模板；
   ② 把 `<modid>` 段包成 `ModConfigDocument` 交给实现了 `ModDataAware` 的模组；
3. 调用点落在 `GameStartEventListener.java:17-23` 的循环里（`invokeWhenLoaded()` **之前**），
   用 `instanceof ModDataAware` 判断，**`Mod` 基类不改抽象契约**；
4. `DataKeyed` / `@DataKey`（§3.4）供模组自定义实体使用；
5. 更新 `MODDING-GUIDE.md`：新增一节"模组配置"（写法、路径、`applyConfig` 时机、**不能自带配置文件**那条继续保持），
   并给 `mods/drunkenSword` 加一个**可选的**示例（把 `INITIAL_STACKS` 变成可配置，演示整条链路）。

**不做什么**：不让模组改别的模组的分组；不做"模组自带 json 随模组目录走"（与 `MODDING-GUIDE.md:364` 冲突）。

**验收**：`config/data/drunkenSword.json` 写 `{"drunkenSword":{"initialStacks":4}}` → 进游戏开局醉意 4 层（`/effect list` 或 `/data get` 可验）；
删掉这个文件 → 回到 2 层且**没有任何报错**；模组**不带**这个文件时游戏照常。

**能停在这里吗**：能（这一步做完就是完整需求了）。

#### 落地记录（2026-10-03，已完成）

| 项 | 实际做法 |
|---|---|
| `ModConfig` | 新建：`@Retention(RUNTIME) @Target(TYPE)`，`String id() default ""`（留空 = 用 `Mod.getMOD_ID()`） |
| `ModDataAware` | 新建：**唯一方法** `void applyConfig(ModConfigDocument)`。**没往 `Mod` 上加任何抽象方法**（自测用反射断言 `Mod` 的抽象方法数为 0、`ModDataAware` 不是 `Mod` 的父类型） |
| `ModConfigDocument` | 新建。**文档的根就是"这个模组自己的分组"**，另外单独带一份 `common` 段。读法：`getLong/getInt/getDouble/getBoolean/getString/getStringList/has/section`，**读不到或类型不对一律返回默认值**。路径两种写法等价：`initialStacks` 与 `<自己的modid>/initialStacks`；`common/…` 从共享区找；**别的模组的分组读不到** |
| 调用点 | `GameStartEventListener` 的模组循环里，`m.invokeWhenLoaded()` **之前**一行 `ConfigLoader.loadModData(m)` |
| `DataKeyed` / `@DataKey`（原文 §3.4） | **本版没做**：它服务的是"模组自定义实体怎么被官方配置覆盖"，而模组实体的数值本来就可以在模组的 `applyConfig` 里自己喂给构造器（`ModConfigDocument` 已经够用）。原文也把它写成"两条路任选"的可选项 |
| 目录 | `ConfigLoader` 的静态块里**也 `mkdirs` 了 `config/data`**（原文 §8.3 D6 点名的那条） |
| 错误隔离 | `applyConfig` 裹 **`try/catch (Throwable)`**（含 `Error`）；整份 JSON 坏掉也只打印。自测专门造了一个"在 `applyConfig` 里抛 `AssertionError`"的假模组，断言加载链不崩 |
| 模组示例 | ⚠️ **没动 `mods/drunkenSword`**。理由：它的 `DrunkenSwordsman.INITIAL_STACKS` 已经用在别的代码里、`mods/` 又**不参与编译**（`mods/exampleModByGFHNV` 有两个同名 `mainClass`），所以改动**编译期验证不到**，与"一个模组一个字都不许改"的要求也冲突。改成了：`DrunkenSwordMod` 的类注释里加一段"这个模组没有实现 `ModDataAware`，所以没有配置文件；要加的话怎么写"，并指向 `MODDING-GUIDE.md` 的新一节 |
| 自测新增 | `testModConfig`（**19 条**）：文档语义 8 条 + 契约 3 条 + 磁盘读取 8 条（用 `out/tmpModConfig` 里的临时文件，走新加的 `loadModData(Mod, File)` 那个入口，**真 `config/` 目录一个字都不碰**） |
| 静态自查 | `check-sources.ps1` → `Java files: 214` / `CHECK OK` |

#### 落地时踩到的坑（原文没有的）

1. ★★ **`System.setProperty("user.dir", …)` 改不了相对路径的基准**：JVM 的工作目录在启动时就定了，
   `new File("./config/data/...")` 永远相对**真实工作目录**。所以"在自测里把工作目录切到临时目录"
   这条思路走不通 → 给 `ConfigLoader` 加了一个**公开的** `loadModData(Mod, File)` 重载
   （默认那个入口算好路径再委托给它），磁盘那条路径才可被自测覆盖。
   ⚠️ 自测**不能**用默认入口去写真 `config/`，否则违反"自测不碰真配置文件"的约定。
2. ★★ **文档的根到底是哪一层，第一版想错了**：`ConfigLoader` 交给模组的是**已经摘出来的那个分组**，
   所以 `ModConfigDocument` 的根就该是它；第一版写成"根 = 整份文件"，
   于是 `getInt("initialStacks", 2)` 去根里找 `"initialStacks"` —— 找不到，**永远返回默认值**。
   症状是"模组说读到了文件，但拿到的全是默认值"（最阴的一种）。
   → 现在两种写法都认（`initialStacks` 与 `<modid>/initialStacks`），
   并且 `common/…` 走单独的那一份；自测把"读自己的分组 / 读 common / 读不到别人的分组"三条都钉住。
3. **`loadModData` 的返回值语义要写清**：它返回的是"**文件在不在**"，不是"解析成功没有"。
   坏 JSON 也算"有配置文件"（内容没生效，模组拿到空文档）。自测第一版按"解析成功"断言，当场红。
4. **没有配置文件的模组照样要调 `applyConfig`**：这是刻意的（模组不用写"有没有文件"的分支），
   但要注意**不能**因此跳过 `invokeWhenLoaded()` —— 那会让模组内容整个消失。
   自测里有一条专门盯它。
5. **`config/data/` 必须由游戏侧建**：它是"用户自己往里放文件"的目录，但空目录先备好，
   用户才知道该往哪放；否则第一次写 `GameRules.json` 之类的东西时，
   `ConfigLoader` 的静态块会先 `mkdirs` 一次 `gameConfig/`，`config/data` 反倒不存在。

#### 阶段 4 的补充（2026-10-03 追加）：模组改官方机制，缺一条链 —— 已补 `IModifyIgnitionMax`

> **起因**：用户问"如果我想做一个提高李晓焰燃点上限的模组，现在的代码能否做到"。
> 查完的结论是**做不到**，于是补了一条最小的链。结论与证据（行号已核对）：

| 路线 | 能不能 | 证据 / 原因 |
|---|---|---|
| 改 `config/gameConfig/GameRules.json`（`actorLiXiaoYan.ignitionMax`） | ✅ **能，但这不是模组** | `ConfigLoader#loadGameRules()`（`ConfigLoader.java:389-418`）是 `GameMain#gameInitialize` 的**第一句**（`GameMain.java:65`），早于 `new OfficialGameContent()`（`:66`），所以官方李晓焰模板的字段初始化 `private int ignitionMax = Rule.ignitionMax();`（`ActorLiXiaoYan.java:201`）读到的是配置后的值。它是**游戏自己的配置**：模组的配置文件在 `config/data/<modid>.json`，且第 11 条坑明令模组不能自带配置（`MODDING-GUIDE.md:409`） |
| 模组注册一个 `Effect`（照 `Drunkenness`） | ❌ **读不到** | `effectiveIgnitionMax()`（`ActorLiXiaoYan.java:307-319`）只读 `getHp()` / `getHpMax()` / `Rule.lowHpThreshold()` / `Rule.lowHpIgnitionMax()` / 实例字段 `ignitionMax`，**完全不扫效果列表**；`ActorLiXiaoYan` 里唯一扫效果的地方是 `hasMemorizedHpEffect()`（`:316-323` 旧行号），而且只问"有没有 `MemorizedHp`" |
| 模组用 `ModDataAware` / `@ModConfig` 喂值 | ⚠️ **能影响别的角色，但改不到这个键** | ① `config/data/<modid>.json` 的 `common` 段**确实会打到官方模板上**（`ConfigLoader.java:486-491` → `EntityDataPatcher.apply`，按完整 id 找模板，`EntityDataPatcher.java:131-169`）——HP / 攻防 / 速度 / 抗性 / 成长都能改；② 但 `ignitionMax` **不是可配置的实体键**：`DataKeys` 里唯一的 `ignitionMax` 是规则键 `actorLiXiaoYan.ignitionMax`（`DataKeys.java:330`），实体补丁带上它会被报成"未知键（这个键不属于实体数据）"（`EntityDataPatcher.java:262-264`）；③ `common` 段**只调 `EntityDataPatcher`，从不碰 `GameRulesPatcher`**，而且 `GameRules` 在 `loadGameRules()` 的 `finally` 里就 `freeze()` 了（`ConfigLoader.java:414-417`），`loadModData` 跑到时表已冻结（`GameRules.java:188-194` 的 `put` 直接返回 `false`）；④ 模组自己那一段只进它自己的 `applyConfig`（`:493-507`），只能影响模组自己造的东西 |
| 反射硬改 `ActorLiXiaoYan.ignitionMax` | ⚠️ **技术上能，代价是真的** | 它是私有字段，**不在任何文档契约里**（`MODDING-GUIDE.md` 写成 API 的只有构造器 / setter / `copy()`），改名即**静默失效**；`check-sources.ps1` 与自测都只覆盖 `src`，模组那段没有安全网；而且它只对"模组拿得到的实例"生效。**这正是用户说的"不要模组直接反射改字段"** |

**缺的钩子一句话**：`ActorLiXiaoYan` **没有"读上限时经过一条可被外部影响的链"**。

**补的东西（最小集，2 处）**：

| 新增 API | 签名 | 为什么必须加 |
|---|---|---|
| `cn.gfhnv.game.interfaces.IModifyIgnitionMax`（新文件，13 行接口 + javadoc） | `int modifyIgnitionMax(int baseMax, LivingThing owner)`；另带 `IModifyIgnitionMax DEFAULT` | 照 `IModifyDamage`（`IModifyDamage.java:5-13`）的现成范式：**折叠语义**（后一个拿前一个的结果）、**带 `DEFAULT`**、**纯函数**。`LivingThing` 而不是 `ActorLiXiaoYan` 当参数类型，是为了不把 `interfaces` 包绑死在 `officialStuff` 上 |
| `ActorLiXiaoYan` 的 4 个静态方法 | `addIgnitionMaxModifier(IModifyIgnitionMax)` / `removeIgnitionMaxModifier(...)`（返回 boolean，按对象身份）/ `getIgnitionMaxModifiers()`（返回副本）/ `clearIgnitionMaxModifiers()`（只给自测） | 模组在 `invokeWhenLoaded()` 里登记一次就够，不必去 `World` 注册表找模板。**静态**而不是实例字段（像 `LivingThing.damageModifiers` 那样）：上限是一条**规则**（基准值来自 `Rule.ignitionMax()`），静态链不会漏掉副本与中途 `new` 出来的角色；要按实例区分也能靠 `owner` 自己判 |

`effectiveIgnitionMax()` 改成"先算基准上限 → 依次过链"（`ActorLiXiaoYan.java:307-319`）。
**链为空时返回值与改动前逐位相同** —— 这是新扩展点唯一的"零风险"依据，自测第一条就钉它。
**现有 3 个模组一个字都没改**（`mods/drunkenSword`、`mods/exampleModByGFHNV`、`mods/abstractLaunchingWords`）。

**范例模组**：`mods/liXiaoYanPlus/`（「李晓焰加强」）—— 不新增内容，只登记一个 `+N` 的修正器，
并把 `N` 做成 `config/data/liXiaoYanPlus.json` 的 `ignitionBonus`（`ModDataAware` + `@ModConfig`）。
写法与"现有扩展点速查表"见 `MODDING-GUIDE.md` 的 **§7.1**。

⚠️ **验过的 / 没验过的**（`mods/` 不在自测编译范围内，见 `notes_for_llm/30-WORKFLOW.md` 的 §6.3）：
链本身（默认逐位不变、+N 生效、`setIgnition` 跟着夹、副本也生效、折叠顺序、摘掉/清空回到出厂值）
由 `TestCommandSystem#testIgnitionMaxModifierChain` 的 **15 条**断言覆盖，基线 **630 → 645**；
模组本体只用 `javac` 单独编过（`exit 0`）并用一个跑完即删的探针验过同一条逻辑
（满血 10 → 15、低血 15 → 20、配置写 8 → 23、副本一致、清空回 15）。
**没验过**：进游戏之后的手感、控制台观感、`ModLoader` 真的把这个目录编出来的过程。

---


### 阶段 5（半天）：文档回写（第 0 条闭环）

> ✅ **已完成（2026-10-03，本次落地；阶段 2/3/4 的产物也一并回写了）**。

| 写回哪 | 写什么 |
|---|---|
| `README.md` | 项目结构表加了 `config/gameConfig/{EntityData,SkillData,GameRules}.json` 与 `config/data/`；玩法节加了"数值怎么调" |
| `MODDING-GUIDE.md` | 新一节"模组配置"（阶段 4 的产物） |
| `notes_for_llm/70-DATA.md` §5.10 | "配置键 = 数据名"这条契约（键名**没有变动**，只是把"配置文件也用这套名字"写清楚） |
| `notes_for_llm/90-STATE.md` | 新的自测基线数字（542 → **630**） |
| `notes_for_llm/30-WORKFLOW.md` | 基线数字与"四份配置文件" |
| `project_analyses/PROJECT-ANALYSIS-2026-09.md` | 在 §8.1 建议表里给这次改动补一行（做了 / 没做） |
| 本文 | 落地结果、行号漂移、踩到的坑（阶段 2/3/4 三节末尾） |

---

## 八、硬约束与风险

### 8.1 硬约束（碰了就出事）

| # | 约束 | 依据 |
|---|---|---|
| 1 | **`/data` 键名契约不能破**：`criticalRate` / `alive` / `effects` / `hpGrow` / `attackGrow` / `defenceGrow` / 五行 `…ManaGrow` 一个都不能改名；122 个 `/data` 断言点硬编码着它们 | `notes_for_llm/70-DATA.md:21-39`、`ENTITY-ATTRIBUTE-SPLIT-2026-10.md:151-154`。**本文的方案正是为了绕开它**：配置键直接用数据名，不引入第二套名字 |
| 2 | **13 参构造器签名与参数顺序不许动** | `MODDING-GUIDE.md:163-167` 与 `:451`、`mods/drunkenSword/.../DrunkenSwordsman.java:94` |
| 3 | **`setXxx` 名字不许动**（`setHpGrowNumber` / `setAtkGrowNumber` / `setDfkGrowNumber` / `setSpeed` / `setLevel` …） | 同上（文档把它们写成 API），且 `/data` 的写回是 `"set"+Java字段名` 拼出来的（`70-DATA.md:41-44`） |
| 4 | **副本语义不许变**：补丁打在**模板**上，"面板属性复制、临时属性清零"那张表（`copy()` ↔ `clearTemporaryAttributes()`）不许跟着改 | `ENTITY-ATTRIBUTE-SPLIT-2026-10.md` 阶段 0 的规则："不是面板属性的，一律清零；面板属性一律复制" |
| 5 | **483 条自测是唯一安全网**，任何一步都不许让基线下降 | `90-STATE.md` / `ENTITY-ATTRIBUTE-SPLIT-2026-10.md:361-367` |
| 6 | **不引入第三方库**（唯一依赖 `org.json`） | `10-RULES.md` §8、`PROJECT-ANALYSIS-2026-09.md` §7.3 |
| 7 | **模组不能自带配置文件**，要配置读游戏的 `config/` | `MODDING-GUIDE.md:364`（第 11 条） |
| 8 | 配置加载**失败不许让游戏起不来**：单键错 → 忽略；整份文件坏 → 用默认值 + 打印 + **不覆盖用户文件** | 对标 `ModLoader` 那批"坏模组让游戏起不来"的经典缺陷（`PROJECT-ANALYSIS-2026-09.md` §6.3） |
| 9 | **不改游戏输入方式**（命令前缀、`SCANNER.nextLine()` 那条路） | `README.md` 与 `PROJECT-ANALYSIS-2026-09.md` §2.1 第 3 点 |
| 10 | **实验代码不许进 `src/`** | `PROJECT-ANALYSIS-2026-09.md` §7.3 第 4 条 |

### 8.2 风险清单（按严重度）

| # | 风险 | 说明 | 对策 |
|---|---|---|---|
| 1 | ★★★ **派生值重算遗漏**（本设计最可能翻车的地方） | `setHpGrowNumber` / `setAtkGrowNumber` / `setDfkGrowNumber`（`LivingThing.java:674/690/706`）**只赋值**；三围只在构造器（`:244-246`）和 `Entity.setLevel`（`:208-218`）里算。用户在配置里改 `hpGrow` 会**看不到任何变化**，然后以为是 bug | 补丁器**自动补 `setLevel`**；自测专门断言"改成长 → `getHpMax()` 跟着变"；README 里写清"改成长要连带重算" |
| 2 | ★★★ **`initialMana()` 有副作用** | 它把 `manas` 整个重建（`LivingThing.java:563-565`）。补丁跑两次 = 法力被重置两次；如果补丁发生在选人之后，玩家开局的蓝会被重置 | 补丁只在**模板**上、只跑**一次**；用"配置加载只执行一次"的守卫（一个 `boolean loaded`） |
| 3 | ★★ **键名漂移** | 技能名（`"举杯邀月"`）、实体 id、字段名任何一处改名，用户的配置文件就静默失效 —— 和 `/data` 是同一个病 | 阶段 0 的三条契约自测；加载时"找不到目标"要**打印**而不是静默 |
| 4 | ★★ **`setHp` 的钳制顺序** | `setHp` 夹到 `getHpMax()`（`LivingThing.java:2109`）。若先设 `hp` 再改 `hpMax`，血会被旧上限夹掉；反之则可能"血量 > 上限" | 固定顺序：`level` → `derived.{attack,defence,hpMax}` → `derived.hp`；自测断言两条顺序都给出正确结果 |
| 5 | ★★ **公式常量外置会作废数值标定** | `PROJECT-ANALYSIS-2026-09.md` §4.2：打 150 级 BOSS、每 1.0 倍率 ≈ 880 伤害。改 `attackBase`/`levelDefenceFactor` 之后这套参考值全废 | `GameRules.json` 的 `formula`/`mana` 两块**默认值必须与字面量逐字相等**，自测用固定样例断言伤害结果不变；文档写明"改它 = 重新标定" |
| 6 | ★★ **配置层与效果层的覆盖冲突** | 盗火行者的层数减伤 / 二阶段免伤是 `whenFightStart` 重挂的（`FlameReaver.java:289`），配置改"初始层数"只是改**开局值**；玩家打掉一层后不会回到配置值 | 文档写清"配置只影响**开局**"；不要把"减伤层数"做成"每回合生效"的补丁 |
| 7 | ★ **模组配置的不可复现性** | `ModLoader.java:31-37` 按 `listFiles()` 原始顺序加载，谁先注册谁在 `registeredIdOf`（`World.java:255-275`）里胜出；两个模组改同一只怪时结果取决于文件系统顺序 | 冲突时**打印警告**并声明"后者胜"；文档建议"别两个模组改同一条" |
| 8 | ★ **配置目录的相对路径** | 所有路径都是 `./config/...`（照 `ConfigLoader.java:32`），从别的目录启动就找不到（会重新写一份默认文件） | 沿用现状；在 README 里点一句"请在项目根目录启动"（启动脚本本来就是这么做的） |
| 9 | ★ **`config/gameConfig` 里的旧文件认知负担** | 用户会问"为什么有 `TagConfig` / `PropertyConfig` / `EntityData` / `SkillData` / `GameRules` 五个文件" | README 的项目结构表逐个写一句；`PropertyConfig.json` 标注"已废弃" |
| 10 | ★ **生成器写盘覆盖用户改动** | 阶段 0 的全量默认文件生成器如果每次启动都写，会把用户改过的值冲掉 | 生成器**只在文件缺失时写**（照 `ConfigLoader.setDefaultConfig()` 的语义），并且改名成"`*.default.json`"存一份参考副本 |

### 8.3 读代码时发现的、与"外部加载"直接相关的既有缺陷 / 阻碍

（这一节是本次**新发现**的，按"有几条写几条"；行号都已用 `ReadAllLines` 核对）

| # | 缺陷 / 阻碍 | 证据 | 对外部加载的影响 |
|---|---|---|---|
| D1 | **`TagConfig` 的权重点位是死的** | `new ThinkingControllerAI` 全项目**只有 1 处**（`LivingThing.java:187`，在复制构造器里"如果原控制器是它才重建"）；没有任何生物 `setController(new ThinkingControllerAI(...))`；`ThinkingController` 连这一处都没有 | **"参考现在的 tags 加载"这件事本身不能证明玩法会变。** 阶段 1 的验收必须自己造断言（选人列表 / `/list` / `/data get`），不能靠"进游戏感觉一下" |
| D2 | **一个拼错的键让整份配置作废** | `ConfigLoader.java:129` 的 `TagType.valueOf(tagType.toUpperCase())` 会抛 `IllegalArgumentException`；`GameMain.java:72-74` 只把它吞成一行 `LogWriter` 日志 | 新加载器**必须**改成"逐项容错 + 汇总报告"（§2.1 第 3 条），否则用户写错一个键会得到"配置完全没生效"，且控制台上什么都看不到 |
| D3 | **配置加载失败对玩家不可见** | 同上：`GameMain.java:69-74` 打印"加载配置中"，异常进 `LogWriter`（`logs/latest.log`），控制台不提示 | 新加载器要在控制台**明确**打印"读了几项、跳过几项、为什么" |
| D4 | **`PropertyConfig.json` 是纯僵尸文件** | 全项目零读取（`src` 里 grep `PropertyConfig` 只命中 `ConfigLoader` 的同目录文件名）；文件内容是 `{"game_official_content:insectBoss": {}}` | 它是"数据外部加载"这件事上一次没做完的痕迹。**别在它上面继续加东西**，按 §3.2 废止 |
| D5 | **`ConfigLoader` 写读编码不一致** | `:166` 用 `DEFAULT_TAGS_CONFIG.getBytes()`（平台默认字符集）写，`:124` 用 UTF-8 读 | 新文件**一律 `Files.writeString(path, text, UTF_8)`**（或 `getBytes(UTF_8)`）。现在默认值全是 ASCII 所以没爆，一旦默认描述里出现中文（`description` 一定会）就会"自己写的文件自己读成乱码" |
| D6 | **`ConfigLoader` 的静态块只 `mkdirs` 父目录** | `:87-89` | 新增 `config/data/` 时**记得也建目录**，否则第一次写默认文件会 `IOException`（而它又被 `GameMain` 吞掉 → 又是 D3 那种"什么都没发生"） |
| D7 | **"同类模板多条"会让按类匹配失效** | `World.registeredIdOf` 是"先短名精确匹配，匹配不到退回同类第一条"（`World.java:255-275`）；官方注册了**两个 `BrokenContainer`**（残破 / 完整，`OfficialGameContent.java:60-62`） | 配置**必须**用完整 id 做键（`brokenContainer` vs 另一个）。用类名当键会让两种容器共用一份配置 —— 这是 `PROJECT-ANALYSIS-2026-09.md` §6.3 修过一次的老坑 |
| D8 | **"容器"这类实体是运行时 `new` 出来的** | `BrokenContainer.java:273-295` 按召唤者的最大生命/攻击算自己的值 | 阶段 1 的"只补注册表模板"**覆盖不到**它身上的**动态**值（那是设计，不是缺陷），但**模板上的成长/抗性**能覆盖到 —— 文档要写清这条边界，免得用户改了 `brokenContainer` 的血量发现"召唤出来的还是老样子" |
| D9 | **`ModLoader` 的兜底很薄**（与本次直接相关的一条） | `:45` / `:71` / `:148` 只 `catch (Exception)`；`ModLoader.java:110-113` 的 `URLClassLoader` 被 try-with-resources 提前关；`:115` 的静态块抛 `Error` 会穿透 | 模组接口的调用点（`GameStartEventListener.java:17-23` 那个循环）**不能**再引入"一个模组抛异常 → 后面的模组全不加载"的新风险：`applyConfig` 必须包在 try/catch 里（`catch (Throwable)`），失败只跳过这个模组的配置 |
| D10 | **`dataNames()` 与"实际能写回的键"两个口径** | `notes_for_llm/70-DATA.md:78-82`：`dataNames()` 是字段清单，有些键**永远 dump 不出来** | 阶段 0 的"键名契约自测"**不能**只比 `dataNames()`（阶段 1 实测过这类假绿，`ENTITY-ATTRIBUTE-SPLIT-2026-10.md` 阶段 1 坑 ①）：要比"**真的 dump 出来的键**"与"**配置里真的能生效的键**"两边 |

---

## 九、明确不做的事（与 `10-RULES.md` §9.3 对齐）

| 不做 | 理由 |
|---|---|
| **二进制 NBT / 实体存档 / `storage` 落盘** | 已经在 `NBT-AND-DATA-COMMAND-2026-09.md` 里评估完（结论：不划算 / 用户明确说不做），本文**不重新评估**。这次的"外部数据"是**只读的补丁**，不是存档 |
| **热重载（改文件立刻生效）** | 见 §2.2 候选 D。要做得先解决"已经复制出去的副本 + 已经挂上的效果"，代价远超收益；本项目一直是"重启才生效"（`MODDING-GUIDE.md:56`、第 13 条） |
| **模组自带配置文件** | `MODDING-GUIDE.md:364` 已定口径；类加载器的 URL 只有 `bin/` 且每次清空（`ModLoader.java:110-113`），模组目录里的 json 根本读不到 |
| **用配置文件描述技能/效果的行为逻辑** | §5：那等于发明一门脚本语言。行为留在 Java 里 |
| **让配置改 `final` 字段 / 改 `uuid` / 改 controller 类型** | `/data` 早就拒绝 `final`（`70-DATA.md:85`）；改 uuid 会毁掉判等与选择器 |
| **`/data remove`** | 用户 2026-09 明确跳过（`70-DATA.md:99`） |
| **把 `TagConfig.json` 迁进新体系** | §3.2：现状能用，迁移只有审美收益 |
| **改 `isAlive()` 判据 / 移动离场结算 / 改攻击行格式 / 改颜色默认值** | `PROJECT-ANALYSIS-2026-09.md` §8.2 的"别顺手做"清单，与本文无关但很容易被顺手带上 |
| **顺带重构 `LivingThing`** | `ENTITY-ATTRIBUTE-SPLIT-2026-10.md` 已经把阶段 2/3 留好了；**拆结构的同时改数值 = 出问题分不清是哪边**（那份文档 §6 的"千万别"第 3 条） |
| **弱点表 / 净化驱散 / 暴击系统接线** | 用户明确不做（§9.3）+ 属于别的主题 |

---

## 十、需要用户拍板的问题（本文的产出之一）

| # | 问题 | 选项 | **推荐** |
|---|---|---|---|
| Q1 | **覆盖 vs 默认值** | (a) 构造器给默认、外部只补显式写的键；(b) 外部文件是唯一真相（必须写全） | **(a)**。理由：官方 12 个模板 + 未来每个模组模板都不用抄全字段；版本升级加字段不会让老配置失效 |
| Q2 | **注入时机** | (a) 注册表建好后打在**模板**上（`GameMain.java:71` 那个位置，与 TagConfig 同源）；(b) 构造时读；(c) `whenFightStart` 打在每个实例上 | **(a)**。理由：副本白送、模组内容也在注册表里、构造器签名零改动；代价是"临时 `new` 出来的实例"覆盖不到（可接受，(c) 的重复应用风险更大） |
| Q3 | **模组接口形态** | (a) `@ModConfig` 注解 + `ModDataAware` **可选**接口（现有模组一字不改）；(b) 往 `Mod` 基类加抽象方法 `applyConfig(...)`（更"正式"，但改模组契约 + 现有 3 个模组要动） | **(a)**。理由：不破 `MODDING-GUIDE.md` 的主类契约，"不写配置的模组"零成本 |
| Q4 | **配置文件的目录与粒度** | (a) `config/gameConfig/{EntityData,SkillData,GameRules}.json` + 模组 `config/data/<modid>.json`（5+N 个文件）；(b) 全部塞进一个 `config/gameConfig/Data.json`（1 个文件） | **(a)**。理由：一个文件很快会长到几百行、diff 噪音大；分开后"想改技能就只开 SkillData"。而 `config/data/<modid>.json` 按模组分文件是与"不可复现的模组加载顺序"共存的唯一稳妥做法（§3.3） |

---

## 附录 A：复现本文所有数字与行号

> ⚠️ **先看这条坑**（`ENTITY-ATTRIBUTE-SPLIT-2026-10.md` 附录 A 记着，本文又踩了一次）：
> 中文 Windows 上 **Windows PowerShell 5 的 `Get-Content` 默认按 GBK 解码**，
> 会把行尾中文字符的尾字节和换行符一起吞掉 → **行数会少算**。
> 本文所有行号一律用 `[System.IO.File]::ReadAllLines(...)` 读出（.NET 默认 UTF-8）后核对。

```powershell
# ⓪ 规模（别从仓库根递归扫描：out\artifacts 有 3529 层自我嵌套，会卡死）
"src  .java: " + (Get-ChildItem .\src  -Recurse -Filter *.java -File).Count   # 2026-10-03 实测 214
"mods .java: " + (Get-ChildItem .\mods -Recurse -Filter *.java -File).Count   # 2026-10-03 实测 12

# ① 静态自查（AI 可跑，本次实测过）
powershell -ExecutionPolicy Bypass -File .\check-sources.ps1
#    → Java files: 224 / CHECK OK          （2026-10-03 第十一节落地之后；
#                                            阶段 2/3/4 之后是 214，阶段 0/1 之后是 206，之前是 203）

# ② 全量编译 + 自测（需要 java/javac 在 PATH；AI 可换自带 JDK：把 tool_for_llm\zulu-25\bin 加进 PATH）
powershell -ExecutionPolicy Bypass -File .\test-command-system.ps1
#    → 期望 通过 754 条，失败 0 条（2026-10-03 第十二节落地之后 AI 实跑；
#      第十一节之后是 737，阶段 2/3/4 之后是 630/677/702/707，阶段 0+1 之后是 531，
#      更早的 483/0 是用户 2026-10-02 跑的；
#      AI 侧等价命令与"别接管道"那条坑见 notes_for_llm/30-WORKFLOW.md 的 §0）
#    ⚠️ 沙箱里 `test-command-system.ps1` 跑不了（它把 javac 输出接进 Tee-Object = 被禁的管道捕获），
#       直接调 javac/java 即可（命令见 30-WORKFLOW.md §0 的等价写法）

# ③ 行号/覆盖点定位（把 -match 换成你要找的串）
$l = [System.IO.File]::ReadAllLines('.\src\cn\gfhnv\game\system\configLoadingSystem\ConfigLoader.java')
for ($i=0; $i -lt $l.Count; $i++) { if ($l[$i] -match 'TAG_CONFIG_FILE|setTags|TagType\.valueOf') { "{0,5}  {1}" -f ($i+1), $l[$i].Trim() } }

# ④ 13 参构造器的全部调用点（含跨行写法，别只匹配 super(...) 一行）
$files = @(Get-ChildItem .\src -Recurse -Filter *.java -File) + @(Get-ChildItem .\mods -Recurse -Filter *.java -File)
foreach ($f in $files) {
  $l = [System.IO.File]::ReadAllLines($f.FullName)
  for ($i=0; $i -lt $l.Count; $i++) {
    if ($l[$i] -match 'ElementSort\.[A-Z]+' -and ($l[$i] -match 'super\(' -or ($i -gt 0 -and $l[$i-1] -match 'super\('))) {
      "{0}:{1}  {2}" -f $f.Name, ($i+1), $l[$i].Trim()
    }
  }
}

# ⑤ 自测规模（口径：调用点计数，部分在循环里，所以与"通过条数"不等）
$tl = [System.IO.File]::ReadAllLines('.\src\cn\gfhnv\debug_tools\TestCommandSystem.java')
"自测行数: " + $tl.Count                                                  # 2026-10-03 实测 3152
"check            : " + ($tl | Select-String -Pattern '(?<![\w])check\(').Count            # 324
"run              : " + ($tl | Select-String -Pattern '(?<![\w])run\(').Count              # 136
"expectSyntaxError: " + ($tl | Select-String -Pattern 'expectSyntaxError\(').Count         # 11

# ⑥ 配置目录与 .gitignore（config 会被提交，所以默认配置可复现）
Get-ChildItem .\config -Recurse -File | ForEach-Object { $_.FullName }
& .\tool_for_llm\Git\cmd\git.exe check-ignore -v config\gameConfig\TagConfig.json   # 无输出 + exit=1 = 未被忽略
```

## 附录 B：本文引用的行号清单（2026-10-03 用 `ReadAllLines` 核对）

| 位置 | 行号 |
|---|---|
| `ConfigLoader.java` | 文件 170 行；`:32` 路径常量；`:112-123` 缺文件分支；`:124` UTF-8 读；`:129` `TagType.valueOf`；`:137-153` 注入；`:164-169` 写默认；`:87-89` 静态块 |
| `GameMain.java` | 文件 308 行；`:62` 官方内容；`:63` 模组加载；`:68` `GameStartEvent`；`:71` `ConfigLoader.loadConfig()`；`:72-74` 吞异常；`:76` 命令初始化；`:168/:210/:250` 三处 `copy()`；`:274/:278` 开战前补血 |
| `LivingThing.java` | 文件 2317 行；`:120` `ElementProfile` 组件；`:152-208` 复制构造器（`:201-207` 复制 tags）；`:228` **13 参构造器**；`:244-247` 三围派生；`:248-285` 元素法力成长；`:563-565` `initialMana`（重建列表）；`:674/:690/:706` 三个成长 setter（**只赋值**）；`:1282` `setDescription`；`:1298` `setController`；`:1638` `getHpMax`；`:1647` `setHpMax`；`:1712` `getAttack`；`:1721` `setAttack`；`:1907` `whenFightStart`；`:1969` `clearTemporaryAttributes`；`:1996` `whenFightEnds`；`:2083` `getDefence`；`:2092` `setDefence`；`:2099/:2109` `getHp`/`setHp`（**钳制在 2109**） |
| `Entity.java` | 文件 237 行；`:31-46` 复制构造器；`:182/:191` `getType`/`setType`；`:208-218` `setLevel`（**唯一的重算点**） |
| `Thing.java` | 文件 254 行；`:34` tags 字段；`:219` `setMass`；`:242/:251` `getTags`/`setTags` |
| `World.java` | 文件 423 行；`:65/:156/:165` 三个注册入口；`:255-275` `registeredIdOf`；`:353/:366` `applyRegisteredId` |
| `Mod.java` | 文件 280 行；`:155/:175/:196` `addItem/addEntity/addEffect`；`:221` `invokeWhenLoaded`；`:231` `registerItself`；`:270` `getMOD_ID` |
| `ModLoader.java` | 文件 232 行；`:25` `./mods`；`:31-37` 未排序遍历；`:45/:71/:148` 只 catch `Exception`；`:110-113` try-with-resources 关 loader |
| `GameStartEventListener.java` | 文件 25 行；`:17-23` 模组循环（`:18-20` `return` 当 `continue`）；`:21/:22` 调用点 |
| `FightStartEventListener.java` | 文件 28 行；`:18-24` 遍历 `getAllEntities()` 调 `whenFightStart` |
| `OfficialGameContent.java` | 文件 169 行；`:51-56` 六个人物；`:59-62` 盗火行者 + 两种容器 |
| `EntitySelector.java` | 文件 795 行；`:253-263` `type=` 实际比的是**类名/名字/id** |
| `DamageCalculate.java` | 文件 126 行；`:51` `calculate`；`:120` `level*10+200` 骨架 |
| `Skill.java` | 文件 505 行；`:36-47` **没有 id 字段**；`:55` 复制构造器；`:87` 6 参构造器；`:102` `copy()` 抛异常；setter：`:118` `setForEnemies`、`:134` `setNowCoolDown`、`:150` `setCoolDown`、`:166` `setAims`、`:427/:443/:459` 三个倍率、`:475` `setConsumedMana`、`:502` `setTags` |
| `Mana.java` | 文件 48 行；`:10-13` 构造器（`amount` 同时当初始值与上限）；`:22/:26-30` `getAmountMax`/`setAmountMax`（带钳制） |
| `FlameReaver.java` | 文件 1131 行；`:89/:94/:99/:104/:122/:130/:147` 七个旋钮；`:211` `BASE_HP_MAX`；`:219-220` 构造调用；`:289` `whenFightStart` |
| `BrokenContainer.java` | 文件 520 行；`:209/:219/:224` 三个比例；`:273/:283` 构造器（按召唤者动态取值） |
| `InsectBoss.java` | 文件 44 行；`:20` `BASE_HP_MAX`；`:23-25` 构造 + 覆盖血量 |
| `ActorLiXiaoYan.java` | 文件 231 行；`:42-94` 九个常量；`:111-113` 构造调用 |
| `PlayerOne.java` | 文件 38 行；`:21-24` 构造 + `setMass` + 背包 |
| `TestCommandSystem.java` | 文件 3152 行；`:82-105` `main` 的 14 个 `testXxx`；`:3026-3061` 召唤探针实体；`:3037-3038` 13 参构造调用 |
| `TagConfig.json` / `PropertyConfig.json` | 41 行 / 5 行 |
| `mods/drunkenSword/.../DrunkenSwordsman.java` | 文件 157 行；`:94-95` 13 参构造调用；`:148` `whenFightStart` |
| `mods/drunkenSword/.../RaiseCup.java` | 文件 84 行；`:40-46` 技能数值写死 |

## 附录 C：本文没做的事 / 不确定项

> 下面 1~2 条讲的是**写本文那次会话**（2026-10-03 上午）的状态，**已经被同日的落地会话推翻**：
> ① 阶段 0 + 阶段 1 已经改了 `src/`（5 个文件，见阶段 0 / 阶段 1 的落地记录）；
> ② 自测已经**真跑过**：`通过 531 条，失败 0 条`。留着这几条是为了说明"当时为什么只能给设计"。

1. **没有改任何 `src/`、`mods/`、`config/` 下的文件**，只新建了本文；
2. **没有跑通自测**：本次会话里 `java.exe` 被 DSH 沙箱拒绝（`check-sources.ps1` 能跑），
   所以基线 **483/0** 引用的是 **2026-10-02 用户实跑**的结果，不是我这次复跑的；
3. **没有实测过任何一种 JSON 写法**：§4 的四份 schema 是**设计**，字段名对照的是代码里的 Java 字段名与
   `/data` 数据名（以及所有 setter 都真实存在，清单见 §3.4 末尾），但"`org.json` 解析 + 反射 setter 写入"
   这条链路一次都没跑过；
   → **落地时实测了**：`EntityData.json` 那份 schema 能跑通（`base` / `derived` / `manaGrow` 都覆盖到，
   另外还接受了"裸键"写法）；`SkillData.json` 只生成不读；`GameRules.json` 本版没做。
4. **没验证"13 参构造器调用点共 9 处"之外的其它数字**：附录 A 的 ⑥ 只跑了配置目录与 `.gitignore`，
   "5 处 `super(...)` + 4 处跨行的"这个清单是逐文件读出来的（附录 B 有位置），换一种匹配口径会漂；
4. **工作量的估时是粗估**（按本项目体量校准：`ConfigLoader` 170 行、`DataCommand` 530 行这样的参照物），
   没有做原型；→ 实际两个阶段各写了 506 / 553 / 995 行的新文件，比估的多；
5. **没有验证"用户改哪几个数字最想要"** —— §6 的分类是按"判据 + 代码里的常量清单"推的，
   真正该先外置哪一批，只有用户自己知道（这正好是第十节 Q4 之外的隐含问题：先做实体还是先做技能）；
6. **不确定**：`type`（`"player"`/`"insect"`）要不要允许外部改。本文按"它可以改"来设计（因为它只是显示字段、
   全项目没有逻辑依赖它），但如果用户希望它固定，把它从 schema 里删掉即可，不影响其他任何设计；
   → 落地时**按"可以改"实现**了（`DataKeys.Base.TYPE`）；
7. ~~**不确定**：`GameRules.json` 里的 `limits`（`maxLevel` / `maxInventorySlots`）现在是**预留位**……~~
   → **已结案（2026-10-03）**：用户明确要求"删除 `limits.maxLevel` / `maxInventorySlots`，不加限制"，
   两项连同整个 `limits` 段一起删掉了（代码里本来就没有检查点，删掉零行为变化）。
   本文件 §4.3 的 schema 示例与阶段 3 落地记录的对应行都已同步。

## 附录 D：阶段 0 / 阶段 1 的实际文件清单（2026-10-03 落地）

| 文件 | 状态 | 说明 |
|---|---|---|
| `src/cn/gfhnv/game/system/configLoadingSystem/DataKeys.java` | **新增** | 配置键 ↔ `/data` 数据名的契约表（55 个可配置键 + 27 个只读键 + 元数据） |
| `src/cn/gfhnv/game/system/configLoadingSystem/ConfigDefaultWriter.java` | **新增** | 只读生成器：EntityData / SkillData / 参考副本（含极简 JSON 排版器） |
| `src/cn/gfhnv/game/system/configLoadingSystem/EntityDataPatcher.java` | **新增** | 实体数据补丁器（含 `Report` 报告与 `Snapshot` 快照） |
| `src/cn/gfhnv/game/system/configLoadingSystem/ConfigLoader.java` | 改写 | 新增 `loadEntityData()`；修掉 UTF-8 写盘与"一个坏键废整份配置"两个既有缺陷 |
| `src/cn/gfhnv/game/GameMain.java` | 小改 | `loadConfig()` 之后新增 `loadEntityDataConfig()`，两份配置各自独立 try/catch |
| `src/cn/gfhnv/debug_tools/TestCommandSystem.java` | 追加 | 新增 `testConfigDefaultsAndPatch`（48 条断言），基线 483 → **531** |

**没有动**：`mods/`（阶段 4 才做模组接口）、`config/`（自测只碰 `out/tmpConfig`；
`EntityData.json` 由游戏启动时首次生成）、`LivingThing.java`（13 参构造器与 `Entity#setLevel` 一个字没动）、
`DataBridge` / `/data` 的任何键名。

## 附录 E：阶段 2 / 3 / 4 的实际文件清单（2026-10-03 落地）

| 文件 | 状态 | 说明 |
|---|---|---|
| `src/cn/gfhnv/game/system/configLoadingSystem/SkillDataPatcher.java` | **新增** | 技能数值补丁器（键 = `实体id#技能名`，含 `Report` 与 `SkillSnapshot`） |
| `src/cn/gfhnv/game/system/configLoadingSystem/GameRules.java` | **新增** | 静态规则表（`getDouble/getLong/getInt`，缺键回落 `RuleDefaults`；有 `freeze()`） |
| `src/cn/gfhnv/game/system/configLoadingSystem/RuleDefaults.java` | **新增** | 出厂值的唯一真相（34 条规则键的字面量） |
| `src/cn/gfhnv/game/system/configLoadingSystem/GameRulesPatcher.java` | **新增** | 读 `{段:{键:数字}}` 进规则表（逐项容错 + 汇总报告） |
| `src/cn/gfhnv/game/mod/config/ModConfig.java` | **新增** | `@ModConfig(id=…)` 注解（`RUNTIME`） |
| `src/cn/gfhnv/game/mod/config/ModDataAware.java` | **新增** | 可选接口，唯一方法 `applyConfig(ModConfigDocument)` |
| `src/cn/gfhnv/game/mod/config/ModConfigDocument.java` | **新增** | 模组配置文档（路径读取 + 缺失返回默认值 + 分组隔离） |
| `src/cn/gfhnv/game/skill/NumericSkillTunable.java` | **新增** | 可选接口：子类自己的额外数值旋钮（目前只有 `RestorationHealthSkill` 实现） |
| `src/cn/gfhnv/game/system/configLoadingSystem/DataKeys.java` | 追加 | `SKILLS` / `SKILL_SEPARATOR` / `SkillKeys.*` / `Rule.*`（规则键全部登记） |
| `src/cn/gfhnv/game/system/configLoadingSystem/ConfigDefaultWriter.java` | 改写 | 技能段写全 8 项；新增 `gameRulesJson()` / `writeGameRules()`；`writeAll` 现在写 4 个文件 |
| `src/cn/gfhnv/game/system/configLoadingSystem/ConfigLoader.java` | 改写 | 新增 `loadSkillData()` / `loadGameRules()` / `loadModData(Mod)` / `loadModData(Mod, File)` / `loadAllModData()`；静态块补 `mkdirs config/data` |
| `src/cn/gfhnv/game/GameMain.java` | 小改 | `loadGameRules()` 提到**第一行**；`loadSkillDataConfig()` 紧跟实体数值之后 |
| `src/cn/gfhnv/game/eventListener/GameStartEventListener.java` | 小改 | 模组循环里 `invokeWhenLoaded()` 之前加 `ConfigLoader.loadModData(m)` |
| `src/cn/gfhnv/game/entity/LivingThing.java` | 小改 | 面板三围与 `initialMana` 的基数改读规则表（**13 参构造器签名一个字没动**） |
| `src/cn/gfhnv/game/damage/DamageCalculate.java` | 小改 | 等级防御系数改读规则表（类加载时读一次，热路径不查表） |
| `src/cn/gfhnv/game/officialStuff/customEntity/monsters/FlameReaver.java` | 小改 | 7 个旋钮 + `baseHpMax` 改成读取方法（用的时候现读） |
| `src/cn/gfhnv/game/officialStuff/customEntity/monsters/InsectBoss.java` | 小改 | `BASE_HP_MAX` 改成 `baseHpMax()` |
| `src/cn/gfhnv/game/officialStuff/customEntity/summons/BrokenContainer.java` | 小改 | `HP_RATIO`/`ATTACK_RATIO`/`COMPLETE_HP_RATIO` → `hpRatio()`/`attackRatio()`/`completeHpRatio()` |
| `src/cn/gfhnv/game/officialStuff/customEntity/players/ActorLiXiaoYan.java` | 改写 | 10 个 `public static final` → `Defaults.*`（字面量）+ `Rule.*`（生效值读取方法） |
| `src/cn/gfhnv/game/officialStuff/customSkill/**`（9 个技能类） | 小改 | 补复制构造器、`copy()` 改走复制构造器（见阶段 2 坑 1）；`RestorationHealthSkill` 顺带补 `neededManaScale` 的复制与 setter |
| `src/cn/gfhnv/debug_tools/TestCommandSystem.java` | 追加 | 新增 `testSkillDataPatch` / `testGameRules` / `testModConfig`（共 62 条断言），基线 542 → **630** |

**没有动**：`mods/`（一个字都没改）、`config/`（唯一的副作用是游戏启动时第一次生成 4 份默认文件；
自测全程只碰 `out/` 下的临时目录）、`LivingThing` 的 13 参构造器、`Entity#setLevel`、
`DataBridge` / `/data` 的任何键名、`Mod` 基类的抽象契约。

---

*本文由 AI（DeepSeek）生成，2026-10-03。所有"现状"结论均已在源码中逐一核对（附行号，清单见附录 B）；
数字的统计口径与复现命令见附录 A。真要动手时请先回写 `notes_for_llm/70-DATA.md` 与 `90-STATE.md`，
再回来更新本文。以源码为准。*

*2026-10-03 同日追加：阶段 0 与阶段 1 **已落地并实跑通过**（`通过 531 条，失败 0 条`），
落地记录与踩坑见对应两节末尾。*

*2026-10-03 同日再追加：**阶段 2 / 3 / 4 也全部落地并实跑通过**（`通过 630 条，失败 0 条`，
`Java files: 214` / `CHECK OK`）。三节末尾各有「落地记录」与「落地时踩到的坑」，
其中**两条推翻了原设计**，读代码前请先看：
① 阶段 2 坑 1 ——"`copy()` 返回全新实例"的 9 个技能类必须补复制构造器，否则配置传不到副本；
② 阶段 3 坑 1 ——"`private static final X = GameRules.getXxx(...)`"**不安全**（类加载时机不受控），
那组常量改成了"用的时候现读"。
`config/gameConfig/` 下现在有 4 份文件（`EntityData` / `SkillData` / `GameRules` + 参考副本），
`config/data/` 是模组配置目录（空目录由游戏侧建好，游戏不往里写文件）。*


---

## 阶段 5：解耦重构（2026-10-03）—— 本文件里的缺陷 D1–D10 与"配置加载解耦"那 7 条的结案

> 本节由「配置加载子系统解耦」那一轮施工后补写（方案与施工记录见
> `CONFIG-LOADING-DECOUPLING-2026-10.md`，尤其是它的第十节）。
> **本文件 §8.3 的 D1–D10 与那份文档的 D1–D7 是两批不同的缺陷**，这里一并结案，方便下次别再重开。
> 验收：`CHECK OK` / 221 文件 / 自测 **677/0**（本轮实跑）。

### 本文件 §8.3 的那 10 条

| # | 说的什么 | 现在 |
|---|---|---|
| D1 | `TagConfig` 的权重点位是死的（`ThinkingControllerAI` 全项目只有 1 处引用） | **维持原样**：方案 §6「不做的事」已定"不把 `TagConfig` 迁进新体系"（迁移只有审美收益）。本轮只给它加了一条**形状守卫**（出厂默认值 7 个 id / 26 个键值对），防止那份内嵌 JSON 被改坏 |
| D2 | 一个拼错的键让整份配置作废（`TagType.valueOf` 抛异常） | **已修**（本文件 §570 行那条记录）：标签配置改成**逐项容错**，认不出的类型/值只跳过那一项并打印"哪个文件、哪个键、第几行、为什么" |
| D3 | 配置加载失败对玩家不可见（只进 `logs/latest.log`） | **已修**：`GameMain` 的三个 `loadXxx` 各自 try/catch 并把失败打到控制台；三个补丁器结尾统一汇总"应用几项 / 跳过几项 / 各是为什么" |
| D4 | `PropertyConfig.json` 是纯僵尸文件（全项目零读取） | **维持**：不读、不删、也不往里加东西（用户文件，删除权归用户） |
| D5 | `ConfigLoader` 写读编码不一致（写用平台默认、读用 UTF-8） | **已修**：所有写盘一律显式 `StandardCharsets.UTF_8`；本轮新增的自愈写回也照这条 |
| D6 | 静态块只 `mkdirs` 父目录 | **已修**：静态块把 `config/data` 也建出来 |
| D7 | "同类模板多条"会让按类匹配失效（官方注册了两个 `BrokenContainer`） | **已修**：`EntityDataPatcher.targetsOf` 只用**完整 id 精确匹配**；短名只在"注册表里恰好一条以它结尾"时才认，否则报"是短名，注册表里有 N 条"并拒绝 |
| D8 | "容器"这类实体是运行时 `new` 出来的，补丁覆盖不到 | **维持**（这是设计不是缺陷）：补丁只作用在**注册表模板**上，运行时现场造出来的实例按召唤者算值 |
| D9 | `ModLoader` 的兜底很薄（只 `catch (Exception)`） | **本子系统这一侧已加厚**：`ConfigLoader.loadModData` 对 `applyConfig` 用 `catch (Throwable)`（一个模组配置写错不许带崩后面的模组），自测里有一条专门塞 `AssertionError` 验它；`ModLoader` 本体**没动**（不在本轮范围） |
| D10 | `dataNames()` 与"实际能写回的键"是两个口径 | **维持已修状态**：自测里有「每个 `/data` 能写的标量字段都挂着一个同名 setter —— 缺 setter 的：[]」与「写回契约：`DataBridge#merge` 走的是 setter」两条钉住 |

### 解耦文档那 7 条

| # | 一句话 | 结案 |
|---|---|---|
| **D1** | `META` 里 15 个键声明"能配置"、补丁器从来不读 | **已修**：5 个**面板属性**（`criticalRate` / `criticalDMG` / `enhance` / `penetration` / `defenseLoss`）真的接上了（表里各一行），`copy()` 会带、战斗结束不清；另外 11 个（`extraDamage` + 五元素 `*Penetration` / `*DamageEnhance`）**从 `DataKeys` 里摘掉**，写进配置会得到一句带理由的报错（见 90-STATE 的"顺手发现 3 个新缺陷"） |
| **D2** | `Temporary` 分组是个死概念，写 `"temporary": {...}` 得到误导性报错 | **已修**：那块现在会得到「「temporary」不是一个配置块：实体补丁只认 [base, derived]……」并告诉你面板属性该写哪儿 |
| **D3** | `recalculateDerivedStats` 把三围公式又写了一遍 | **已修**：`200` / `110` / `200` 改成读 `formula.hpBase` / `attackBase` / `defenceBase`；自测新增「改了 `formula.hpBase` 之后，只写 `hpGrow` 的实体也跟着变」 |
| **D4** | `DEFAULT_TAGS_CONFIG` 与 `TagConfig.json` 是同一份内容的两个副本 | **判为非缺陷 + 加守卫**：Java 里那份的定位是"出厂默认值"，`TagConfig.json` 是**用户副本**（用户改它合法，两者不同是允许的）；所以自测**不**做逐字节比对（那会在用户合法改动后变成假失败），只钉住出厂默认值本身的形状 |
| **D5** | 三份 JSON 是空的，而且不会自己变好 | **已修（自愈）**：`writeEntityData` / `writeSkillData` / `writeGameRules` 从"存在就早退"改成**缺啥补啥** —— 默认值有、文件里没有的键补进去；文件里已有的值**一个字节都不动**；内容不是合法 JSON 时**一个字节都不动**；补完没变化就不写盘。**用户那三份空文件不用手动删了** |
| **D6** | `common` 只打实体补丁，但文档教用户写 `skills` / `flameReaver` | **已修**：`common` 现在真的支持 `skills`（走 `SkillDataPatcher`）；**规则段做不到**，所以写 `formula` / `mana` / `flameReaver` / `insectBoss` / `actorLiXiaoYan` 时会打印一句明确的"这一段改不了 + 该去哪儿改"，不静默。`MODDING-GUIDE.md` 的两处假示例已订正 |
| **D7** | `SkillDataPatcher.isKnown()` 与 `patch()` 是两张平行表 | **已修**：两者都只看 `SkillKeySpecs` 一张表；`isKnown()` 从 7 行或链变成一次查表，并新增断言「`isKnown()` 认识的键 == 表里的键」 |

### 顺手发现并修掉的、属于同一个子系统的新缺陷（编号接在本文件之后）

- **D11：五行五个扁平的 `*ManaGrow` 键从来没被读过** —— 生成器写进默认文件、`META` 声明能配置，
  但补丁器只认 `manaGrow` **块**。写扁平键毫无反应。**已修 + 自测**。
- **D12：`manaGrow` 块写法其实也从来没生效过** —— 块名与键名都是 `manaGrow`，
  `lookup()` 的 `patch.has(key)` 第一步就把整份补丁当成值返回了。**已修 + 自测**
  （新增 `SpecPatcher.lookupManaGrowBlock()`）。
- **D13：零调用点死代码** —— `ConfigLoader.getEntityDataFile()` / `getSkillDataFile()` /
  `getGameRulesFile()` / `getTagsMap()` 与 `DataKeys.GROUP_DERIVED`。**已删**（删前 grep 确认）。
  > ⚠️ **2026-10-03 晚复核（见 `CONFIG-LOADING-DECOUPLING-2026-10.md` 的 §12.5.1）**：
  > 四个 getter 确实早已删干净（再 grep 仍是 0 命中）；但 **`DataKeys.GROUP_DERIVED` 的
  > "零调用点"结论已过期** —— 它现在是 `EntityKeySpecs.DERIVED` 四行的**活分组常量**（4 处使用）。
  > 同一轮另删了两个真正零引用的死物：`DataKeys.SkillKeys.ELEMENT_UNIVERSAL`
  > 与整个 `debug_tools/TestAnticipateDamage` 文件。

### 本轮**没有**做的（别以为做了）

- 热重载、二进制存档、`PropertyConfig.json` 的处理、`TagConfig` 加载器重写：**都没动**（方案里"不做的事"一节照旧有效）。
- 手感类验证（改 `config/` 里的数再打一局）：**AI 做不了**，自测只跑到"数值确实变了"这一层。
- 三份 JSON 的键名与 schema：**一个字没改**（用户手上 6 份文件里，`GameRules.json` / `TagConfig.json`
  重构后行为不变；三份空的会在下次启动时被**自愈补全**，用户已有的值不会被覆盖）。

---

## 第十一节：子类配置字段开放 + 模组内容不进游戏配置（2026-10-03，用户三条意见）

> 用户原话：「**白厄的配置是否忘了?为什么没有火种等配置?能否优化一下,角色/怪物的特殊字段也放到
> EntityData 里面,随便一提模组配置不能放在这个里面**」。
> 本轮实跑验收：`CHECK OK` / 224 文件 / 自测 **737/0**（本轮新增 28 条断言）。
> ⚠️ **`/data` 的键名一个都没改**（实测 134 个断言点照旧）；13 参构造器与现有 public
> getter/setter 签名一个都没动；`mods/` 下 4 个模组**一个字节都没动**。

### 11.1 不是"忘了"，是闸门划错了边界

`DataKeys.CLASS_STATE`（旧）是**上一轮"反射混血"切换时为了行为中性**加的闸门：
切换之后"能不能配"由反射面决定，于是那 25 个子类状态键会**默认变成可配置** ——
那等于让一份 `EntityData.json` 把 boss 打成二阶段。闸门本身是对的
（它堵的是 `70-DATA.md` §5.10 点名的**静默裸写字段**），**但它把两件事混成了一件**：

| 那一类 | 例子 | 配了能算数吗 | 结论 |
|---|---|---|---|
| **出厂数值**（开局该是多少） | `coreflame` / `coreflame_max` / `scourge` / `scourge_max` / `soulscorch` / `ignition` / `extraAbilityTier` | **能** —— 每个都有 setter，值参与计算 | 放行 |
| **运行时状态**（打起来才变的） | `isAwaken` / `phaseTwo` / `charging` / `absorbed` / `deathNotified` / `kind` / `owner` / `extraTurns` … | 不能 —— 没有 setter，或者配了会被流程改写 | 继续挡 |

判据因此从"**这个字段属于谁**"改成"**配了能不能算数**"。

### 11.2 落地：拆成两个清单，放行那一半走反射面

- `DataKeys.CLASS_CONFIG`（新，7 键）= `ignition` + `coreflame` / `coreflame_max` /
  `soulscorch` / `scourge` / `scourge_max` / `extraAbilityTier`；
- `DataKeys.CLASS_RUNTIME`（新，19 键）= 原来那批里剩下的；
- `DataKeys.CLASS_STATE`（**名字保留**）= 两者的并集，**现在只剩一个用途**：
  自测的 dump 契约口径。配置层自己不再用它做判断 —— 这正是旧代码"整批挡住"的病根。
- **放行的那批走反射兜底**（`ReflectionConfigBridge.patchExtra`），**没有**进
  `EntityKeySpecs` 那张通用表：那张表的每一行都要对**任意** `LivingThing` 成立，
  而 `coreflame` 只在 `Phainon` 上存在（表驱动的写入不分类型，会把"写一个不存在的字段"
  变成静默无效或异常）。代价说清楚：**写错模板时照旧报未知键**，不会静默。
- **必须有 setter** 这条由自测钉住（「放行的 7 个键每一个都有 setter」）——
  没有 setter 的键放行就是重开"静默裸写字段"那个洞。
- **新块名 `classState`**（`DataKeys.CLASS_SECTION`）已登记进 `BARE_SECTIONS`：
  `{"classState":{"coreflame":18}}` 与裸键 `{"coreflame":18}` **两种写法都认**，
  生成器写的是分块形式（和 `base` / `derived` 一致）。
  ⚠️ 常量**不能**叫 `CLASS_STATE`（那个名字被并集占了，同类里两个同名常量是硬错误）。

### 11.3 ⚠️ "看着能配、其实会打折扣"的四个（**这条最该给用户看**）

| 键 | 真实语义 | 用户要知道什么 |
|---|---|---|
| `ignition`（李晓焰） | `setIgnition` 夹 `0…effectiveIgnitionMax()` | **开局层数**；`whenFightEnds()` 会 `setIgnition(resetIgnition)` 回到 `GameRules` 的 `actorLiXiaoYan.resetIgnition`，所以它只在**这一局**里是初始值（机制，不是缺陷） |
| `coreflame`（白厄） | `setCoreflame` 夹到 `coreflame_max` | **基准值**：`Phainon#whenFightStart` 每局 `+1`，写 15 开局实际 16；超过上限会被**悄悄压到上限** |
| `extraAbilityTier`（白厄） | `whenFightStart` 每局 `+1`，`updateSelf` 把它折成 `+0.5%/段` 加伤 | 同上（写 3 开局是 4）；它**参与计算**，不是最终值 |
| `soulscorch`（白厄） | 觉醒期间的免死与加伤倍率 | 觉醒结束（`AwakeEndListener`）**归零** —— 只在觉醒期间有意义 |

**没被覆盖的那几个照常生效**：`coreflame_max` / `scourge` / `scourge_max` 全项目零写入点，
配多少就是多少（自测逐条实测：18 / 5 / 9 都落地了）。

### 11.4 模组内容不进 `EntityData.json` / `SkillData.json`

- 判据是**谁注册的**（`ConfigDefaultWriter.isOfficialContent`）：在 `World.getModList()` 里找到
  认领这个实体的模组，看它是不是 `OfficialGameContent`；**没有模组认领的按官方处理**
  （自测往注册表里塞的探针不能因此整段消失）。**不按 id 里的冒号判断** ——
  官方 id 同样带 `game_official_content:` 前缀。
- 技能按**拥有者实体**归口（`isOfficialSkillKey`，按第一个 `#` 切分）。
- **为什么必须分开**：① 用户手上的官方配置文件里不该躺别人的东西，删模组之后那几段会变成
  没人认领的死配置；② 模组数值被固化进游戏文件后，用户改模组默认值时会与"上次自愈写进去的
  快照"打架。模组内容有它自己的路：`config/data/<模组id>.json`。
- ⚠️ **这推翻了 90-STATE §9.1 原来那句"模组内容一并纳入是意外的好处，别修掉"** ——
  那是当时的判断，现在按用户要求改掉了。
- ⚠️ **`EntityData.default.json`（参考副本）也照同一口径过滤**，理由同上。
- **只管生成，不管加载**：用户在 `common.entities` / `common.skills` 里给模组内容写的补丁
  **仍然生效**（补丁按 id 找模板）。`liXiaoYanPlus` 那条路一个字都没动。

### 11.5 用户现有那份 `EntityData.json` 要做什么

**什么都不用做，但有两件事要知道**：

1. 他文件里**已经有** `drunkenSword:drunkenSwordsman` 与 3 个酒剑仙技能（上次自愈写进去的）。
   自愈**只补不删**（`mergeMissing` 是单向的），所以**那几段会留在原处**。
   想清干净就手动删掉 —— 删了不会报错（"注册表里没有实体"只在**你自己写了新项**时才报），
   留着也不影响（`common` 段照旧能改它）。
2. **下一次启动自愈会给 `game_official_content:phainon` 与 `actorLiXiaoYan` 各补一个
   `classState` 段**（就是这一轮新增的可配置项）。用户已有的值**一个字节都不会动**；
   补完之后那两份文件的键序会变成哈希序（既有行为，不是本轮引入的）。

### 11.6 本轮**没有**做的

- **不给模组内容自动生成配置文件**（没往 `config/data/` 写任何东西）：`config/data/` 是
  **用户手写**的地方（`common` 段是用户对官方数值的调整），自动写进去会覆盖用户的编辑；
  而且"模组作者该自己把旋钮写进 README"才是对的契约。代价已写进 `MODDING-GUIDE.md`。
- **没给 `FlameReaver` 开放任何键**：它的三个"看着像配置"的旋钮
  （`damageReductionLayers` / 各种比例）**早就在 `GameRules.json` 的 `flameReaver` 段里**
  （`RuleDefaults` / `DataKeys.Rule.FlameReaver`），再加一个 per-template 的入口会变成
  "同一个值有两个地方能改、谁赢看顺序"。`damageReductionLayers` 也因此留在
  `CLASS_RUNTIME` 里，理由由 `DataKeys.hintFor` 明确指向 `GameRules.json`。
- **没给 `ignitionMax` 开放**：它是私有的 `int ignitionMax`，没有 setter，
  而且它的基准值来自 `actorLiXiaoYan.ignitionMax`（规则表），外加
  `IModifyIgnitionMax` 修正链这个扩展点。要调上限请走那两个入口。
- 手感类验证（进游戏看火种数/燃点）：**AI 做不了**，自测只到"值确实变了 + 钳制没被绕开"。

---

## 第十二节：技能具名系数 —— "一个技能多个倍率、而且倍率可变"（2026-10-03 追加）

> **用户原话**：「白厄技能(比如 `FoundationStardeathVerdict`)有多个倍率，而且倍率还是可变的，
> 能否加入到配置文件里面?」
>
> **三句话结论**：
> 1. **系数能外置，已经做了**：新接口 `SkillCoefficientTunable`（`cn.gfhnv.game.skill`），
>    一个技能可以有 **N 个具名键**，扁平写在 `SkillData.json` 的技能对象里（与 `aims` 并列）；
> 2. **公式仍然留在代码里**：`倍率 = 基数 × (1 + 层数 × 每层增量)` 这种**算式**不外置 ——
>    做表达式求值器/DSL 是另一个子系统，**必须用户先拍板**（本文件 §九"明确不做的事"第 4 条）；
> 3. ⚠️ **但白厄那几个觉醒技能现在还配不到** —— 它们**不在注册表里**（是 `new` 出来的），
>    而配置是打在注册表模板上的。这是本轮发现的**新缺口**（§12.4），解法要用户拍板。

### 12.1 现状：`FoundationStardeathVerdict` 到底有几个倍率

`officialStuff/customSkill/phainonSkills/awakenSkills/FoundationStardeathVerdict.java`（行号为 2026-10-03 核对）：

| 行 | 数值 | 含义 | 随什么变 | 处置 |
|---|---|---|---|---|
| `:18` | `atkMagnification = 0.45` | 主伤害倍率 | 常数 | ✅ 阶段 2 就能配（`atkMagnification`） |
| `:113` | `0.2` | 施法者回血（占生命上限） | 常数 | ✅ **本轮外置** → `healRatio` |
| `:123` | `4` | 一次最多消耗几层【毁伤】 | 常数，但与"是否满层"**同一处语义** | ✅ **本轮外置** → `scourgeCostCap`（判定同步改成 `!= scourgeCostCap`） |
| `:125` | `6` | 每层【毁伤】打几段 | 常数 × **层数**（段数 = 6 × 层数） | ✅ **本轮外置** → `hitsPerScourge`（算式留代码） |
| `:147` | `6` | 满层时那记收尾的**总**倍率 | 常数 ÷ **场上存活敌人数** | ✅ **本轮外置** → `finishMagnification` |
| `:130/:138/:144/:153` | `0.45` | 出手结束的**复位字面量** | 常数 | ⚠️ **没外置**：它与构造器那个 0.45 是同一个数，本轮保持逐字不变（见 §12.5 的"看着能配、其实打折扣"） |

**"可变"的三种形态**（这一族的全部）：
① `6 × 层数`（层数 → 段数）② `6 ÷ 敌人数`（目标数 → 每目标倍率）
③ `13 × (1 − 剩余额外回合 × 0.125)`（`LastAttack`，见 §12.2）。
**本轮外置的是里面的每一个数，算式一个字没改。**

### 12.2 两族一起扫：能外置 / 不能外置

**白厄那一族**：

| 数值 | 位置 | 随什么变 | 判断 |
|---|---|---|---|
| 上面那 4 个 | `FoundationStardeathVerdict` | 常数 / 层数 / 敌人数 | ✅ 已外置 |
| `0.2` 回血、`4` 层【毁伤】 | `AwakenCommonAttack:31/36`（旧行号） | 常数 | ✅ 已外置 → `healRatio` / `scourgeGain` |
| `0.2` 回血、`0.3` 追击基准、`0.2` 每层、`6` 追击段数 | `Counterattack` | 常数 / 层数 | ✅ 已外置 → `healRatio` / `extraHitMagnification` / `magnificationPerStack` / `extraHits` |
| `+1` 层【弑魂之炽】、`0.75` 减伤 | `CalamitySoulscorchEdict` | 常数 | ✅ 已外置 → `soulscorchGain` / `absorbDamageReduction` |
| **`13 × (1 − 剩余回合 × 0.125)`，下限压到 7** | `LastAttack:19/46/47` | 剩余额外回合数 | ⏸️ **能外置，本轮没做**：`60-COMBAT.md` §5.7 写着"**改这个下限等于改平衡，先问用户**"，而外置就等于把这个平衡旋钮交出去 → **等用户拍板**（键名建议 `magnificationPerRemainingTurn` / `remainingTurnCap`） |
| `12` 火种门槛与消耗、`+0.8` 攻击增强、`+2.7` 生命增强、`8` 个额外回合、循环上界 `i<=6`、`+3` 火种 | `normalSkills/UltimateAttack` | 常数 | ⏸️ **不碰**：`8` 与 `LastAttack` 的公式、`AwakeEndListener` 的 `min(2,…)`（`10-RULES.md` §9.3 有一条"**改之前问用户**"）**耦合**；`0.8`/`2.7` 是"变身时给的面板增强"，改它等于改角色强度，属于平衡决策 |
| `+2` 火种 | `normalSkills/NormalSkill:43` | 常数 | ⏸️ 同上（火种经济与变身节奏耦合） |

**盗火行者那一族**：

| 数值 | 位置 | 判断 |
|---|---|---|
| `0.3` / `0.25` / `0.12` / `0.1` 四招的主倍率 | `CloudOfDeath:45`、`FateDrawsNear:51`、`SacrificeCloudOfDeath:26`、`SacrificeFateDrawsNear:43` | ✅ 早就可配（它们传进 `super(...)` 变成 `atkMagnification`） |
| 每层【灾难之力】的那一段倍率、收尾/起手那一击倍率 | `NecessarySuffering`（1.0 / 1.2）、`MournNotAbandon`（1.0 / 1.2），行 `:89/:100/:81/:87`（旧行号） | ✅ **本轮外置** → `damagePerStackMagnification` / `finishMagnification` / `openingMagnification` |
| 【侵蚀】的 `MIN/MAX` 比例与持续回合 | `FateDrawsNear:33/38/43`、`SacrificeFateDrawsNear:25/30/35` | ⏸️ **能外置，下一批**：它们同时喂给 `Erosion.applyTo(...)`，键名要先想清楚是"技能自己的旋钮"还是"效果自己的旋钮"（效果的数值属于效果那一层） |
| 蓄力 `+2` 层、镣锁 `2` 只、共祭窗口 `2` 回合 | `SilentLament:36/46`、`TangledPathsOfMourning:34` | ⏸️ **能外置，下一批**（纯整数旋钮，机制不耦合） |
| `NecessarySuffering` 注释里那个"每 1.0 倍率 ≈ 923 伤害" | 注释（`:30-37`） | ❌ **不是代码里的数**，是标定尺（文档），不用外置 |
| `DamageCalculate` 的 `level×10+200` 骨架 | 阶段 3 已在 `GameRules.formula` | ❌ 不该再开第二个入口 |
| "何时打谁、打几段、什么时候复位" | `comeToEffect` 的方法体 | ❌ **那是行为逻辑**，不外置 |

### 12.3 落地：`SkillCoefficientTunable`（一个技能 N 个具名键）

| 项 | 做法 |
|---|---|
| 新接口 | `cn.gfhnv.game.skill.SkillCoefficientTunable`：`Map<String,Double> coefficientValues()`（名字 → 当前值，顺序 = 默认文件书写顺序）+ `void setCoefficientValue(String, double)`；**可选接口**，不改 `Skill` 基类契约 |
| 键的形态 | **扁平**写在技能对象里（`{"hitsPerScourge":6}`），与 `aims` / `atkMagnification` 并列 —— 沿用阶段 2 已有的"技能自报键名"约定（`NumericSkillTunable.extraNumericKey()`），**没有发明第二种写法**，也没有新增块名 |
| 整数旋钮合并到同一条路 | `NumericSkillTunable`（`neededManaScale` 那一个）**继承**新接口，用两个 `default` 方法把自己适配成一个具名系数 → 补丁器与生成器**只有一条路**；"整数必须写整数"的老口径由 `applyExtraNumeric` 单独保住（顺带修好：`org.json` 把 `90.0` 读成 `BigDecimal`，老的 `asLong` 会把它误报成"类型不对"） |
| 补丁器 | `SkillDataPatcher#applyCoefficients`：只写**目标技能自己声明过**的键；`reportUnknownKeys` 的判据变成"静态表认识 **或** 这个技能声明了它" → 把 `hitsPerScourge` 写到不认识它的技能上，照样点名"未知键" |
| 生成器 | `ConfigDefaultWriter#skillPatchOf` 把系数写进 `SkillData.json`（默认文件里**看得见**才能改）；整数值写成整数（整数旋钮的写法与加它之前逐字一致）；类型不对只跳过那一项 |
| 自测快照 | `stateOf` 与 `SkillSnapshot` 都补上了系数（否则"全量默认值打回去、数值逐字段不变"与"跑完还原"两条断言会漏掉这一族字段 —— 顺带把 `neededManaScale` 从来没被快照/还原过的洞补上了） |
| 默认值 | **每个字段的初始值 = 外置之前写在方法体里的那个字面量**，逐位相同（自测第一条就钉它） |

**改动的文件（8 个 `src` + 本文件 + 3 份笔记）**：
新增 `skill/SkillCoefficientTunable.java`；改 `skill/NumericSkillTunable.java`、
6 个技能类（`FoundationStardeathVerdict` / `AwakenCommonAttack` / `Counterattack` /
`CalamitySoulscorchEdict` / `NecessarySuffering` / `MournNotAbandon`）、
`configLoadingSystem/SkillDataPatcher.java`、`ConfigDefaultWriter.java`、`SkillKeySpecs.java`（javadoc）、
`debug_tools/TestCommandSystem.java`（新用例 `testSkillCoefficients`，17 条）。
**`mods/` 下 4 个模组一个字节没动；`/data` 键名一个没动；13 参构造器与现有 getter/setter 没动；用户 `config/gameConfig/` 下 5 份 JSON 没动（自测只碰 `out/` 临时目录）。**

### 12.4 ⚠️ 新缺口：白厄的觉醒技能不在注册表里，配置到不了它们

**证据（2026-10-03 核对）**：`UltimateAttack#comeToEffect` 里是
`awakenSkills.add(new AwakenCommonAttack())` / `new CalamitySoulscorchEdict()` /
`new FoundationStardeathVerdict())`（`UltimateAttack.java:86-88`），
`CalamitySoulscorchEdict:121`、`UltimateAttack:113/128/140`、`Phainon:159` 里还有
`new Counterattack()` / `new LastAttack()`；而 `SkillDataPatcher` 只沿
`World.getEntityList() → controller.getSkills()` 找技能（阶段 2 的设计）→
**这 5 个类根本不在配置的视野里**（`SkillData.json` 里也没有它们，自测留了一条"现状记录"断言盯着）。

**为什么这一轮不动它**：修法要新增一个"没有模板的技能"的概念（原型注册表 + 键挂在哪个实体 id 下 +
运行时改成 `原型.copy()`），它会碰到**变身/额外回合那条时间轴**（`60-COMBAT.md` §5.7 全是踩过的坑），
而 AI 侧没有进游戏手测的能力 —— 属于"设计取舍 + 高风险改动"，按 `10-RULES.md` §8 **先给方案再动手**。

**三条候选（推荐 A，都要用户拍板）**：

| 方案 | 做法 | 代价 |
|---|---|---|
| **A（推荐）** | 官方新增一个**技能原型注册表**（`id → 原型技能`，白厄的 5 个觉醒技能登记在 `game_official_content:phainon` 名下），`SkillDataPatcher` / `ConfigDefaultWriter` 都去查它；运行时把 `new Xxx()` 换成 `原型.copy()` | 要动 5 处构造点；好处是"模板 + 副本"这套语义**一个字都不用改**（`copy()` 已经全部覆盖到系数） |
| B | 保留 `new`，改成"每次造出来时按配置现打一次"（`ConfigLoader` 暴露一个 `applyToRuntimeSkill(skill, entityId)`） | 与阶段 2 立的规矩"配置只打模板"冲突；默认文件里还得先有这些键，否则用户看不见键名 |
| C | 不做（觉醒技能永远硬编码） | 用户点名要配的那个技能就是它 —— 大概率不接受 |

#### 12.4.1 ✅ 落地记录（2026-10-03 晚，用户选了方案 A）

**用户原话**：「思考了一下,还是写技能注册表吧.技能id就取模组id:首字母小写类名(不过这样似乎依然有
重复风险,要不先尝试从技能获取短id,如果没有,才使用类名,不过好像可以加上包名来解决).你可以选择一个方法.」

**选定口径 = 用户的第二个方案 + 冲突 fail loud**：**显式短 id 优先 → 类名派生兜底 →
派生撞名直接报错**（不静默去重）；**完整 id = `模组id:短id`**；查找时短名 / 类名只当兜底。
理由是它与项目**已有的惯例**一致：`Mod#addEntity/addItem/addEffect` 就是
"注册时给显式短 id、完整 id = `模组id:短id`、类名只当查找兜底"（`World#registeredIdOf`）。

| 项 | 做法 |
|---|---|
| `Skill` 加 id | `getId()` / `setId()`，**复制构造器一并复制**；`null` = 还没登记也没显式写 |
| 注册表 | `World` 的**第 4 张表**：`skillPrototypes`（显式原型）+ 视图 `skillList` = 原型 ∪ 各控制器里的技能（**每次 `getSkillList()` 重建、按类去重**，同类只留第一条） |
| 登记入口 | `Mod#addSkill(Skill)`（镜像 `addEntity`：落 id + 前缀 + 撞名检查）、`Mod#addSkill(LivingThing owner, Skill)`（多一条**归属**，配置键靠它定位）、`registerItself()` 并进 `World` |
| 撞名 | `World#assignSkillId`：同一个**完整 id** 落到**另一个类**上 → 抛 `IllegalStateException`（点名两个类 + 要求 `setId`）；账本 = **本模组已登记的那批**（跨模组前缀不同，不该误报）；同类重复登记不算撞；匿名类派生不出 id 也报错 |
| 查找 | `World#findSkill`：**完整 id → 短名 → 类名 → 全限定类名**逐级退让；一级命中多个时打印一行并返回 `null`（不猜） |
| 原型 → 副本 | `World#prototypeCopyOf(Class)` = `原型.copy()`；**没登记成就报错**（不静默给出厂值实例） |
| **配置键没变** | 仍是 `<实体完整id>#<技能名>` —— id 是"注册表里那**一条原型**"的身份，配置要打的是"**某只模板手上的那份技能**"（同一个 `universalSkill.CommonAttack` 被五只模板各造一份、倍率不同，用 id 反而分不开） |
| 运行时 | **8 处 `new Xxx()` → `World.prototypeCopyOf(X.class)`**：`UltimateAttack`（3 个觉醒技能 + 2 处 `Counterattack` + 1 处 `LastAttack`）、`Phainon:159`、`CalamitySoulscorchEdict:121` |

⚠️ **真实撞名的两对（不是假设）**：`phainonSkills.normalSkills.UltimateAttack` ↔
`actorLiXiaoYanSkills.UltimateAttack`（都派生 `ultimateAttack`）、
`universalSkill.CommonAttack` ↔ `actorLiXiaoYanSkills.CommonAttack`（都派生 `commonAttack`）——
**给李晓焰那两个写了显式 id**（`liXiaoYanUltimateAttack` / `liXiaoYanCommonAttack`，写在构造器里）。
自测的 fail-loud 探针**用的就是这两个真实类**（把显式 id 置空即复现报错）。

⚠️ **变身时间轴一个字没动**：12 火种、8 个额外回合、`LastAttack` 的 `0.125` 与下限 7 全部原样
（`60-COMBAT.md` §5.7 那条"改这个下限等于改平衡，先问用户"仍然有效）。

**改动面**：新增 **0** 个文件（`src` 仍是 **225**），改 9 个既有源码 + 自测；
`/data` 键名一个没动、13 参构造器与现有 getter/setter 没动、`mods/` 4 个模组一个字没改、
用户 `config/gameConfig/` 下 5 份真实 JSON 我们一个字节没写（自测只落 `out/tmp*`）。

**验收（AI 实跑，自带 JDK / `out/llmcheck`）**：`check-sources.ps1` → `Java files: 225 / CHECK OK`；
`javac` → `exit 0`；自测 → **`通过 788 条，失败 0 条`**（769 → 788，**只涨不跌**）。
（**这行是那一轮的快照**：之后同日的「`/data` 嵌套引用只给 uuid + 攻击行带短标识」一轮把它推到
**797/0、226 个文件**，见 `notes_for_llm/90-STATE.md` 的 §9.1。）
新用例 `testSkillRegistry`（14 条）+ 改写旧"现状记录"断言（5 条）＝ +19。
关键数值对照：`SkillData.json` 里现在有那 5 个键；
把 `game_official_content:phainon#支柱-死星天裁` 的 `hitsPerScourge` 从 6 改成 1 →
`原型.copy()` 出来的副本伤害 `6120 → 1020`；把默认值原样打回原型 → `6120 = 6120`（不配时 = 现状）。
**没用到的验证**：进游戏看手感（AI 做不了）。

**改动文件**：`skill/Skill.java`、`world/World.java`、`mod/Mod.java`、
`officialStuff/OfficialGameContent.java`、`customSkill/actorLiXiaoYanSkills/{CommonAttack,UltimateAttack}.java`、
`customSkill/phainonSkills/normalSkills/UltimateAttack.java`、`customSkill/phainonSkills/awakenSkills/CalamitySoulscorchEdict.java`、
`customEntity/players/Phainon.java`、`configLoadingSystem/{SkillDataPatcher,ConfigDefaultWriter,DataKeys}.java`、
`debug_tools/TestCommandSystem.java`；对外契约写回 `notes_for_llm/70-DATA.md` **§5.10.3**。

#### 12.4.2 ✅ 第 2 轮修正：id 落在"每一份实例"上（2026-10-03，用户实测报"技能 id 不全"）

**用户实测证据**（`/execute as @e run data get entity @s`）：卡厄斯兰那的 `skills` 里
「战技」「大招」有 `id`，「普通攻击」**没有** —— `/data` 里同一张技能表是**半截有 id** 的样子。

**真因**：12.4.1 的 `World#indexSkill` 只在"某个类**第一次**进注册表视图"时落 id，
而 `UniversalController` 构造时会把技能 **`copy()` 一份**再存进控制器 ——
真正出手、真正被 `/data` dump 出来的是**副本**；`universalSkill.CommonAttack` 又被
`playerOne` / `insectBoss` / `commonInsect` / `iceInsect` / `phainon` **五只模板各造一份**，
只有第一份（`playerOne` 那份）落了 id，白厄手里那份永远是 `null`。
（这也是为什么"注册表视图 24 条条条有 id"却仍然有 6 份实例没 id —— **视图按类去重，实例没有**。）

**修法**：`World#indexSkill` 给**每一份**实例落 id；**一个类共用一个 id**，
后到的沿用视图里第一条已经落好的那个（`World#inheritSkillId`；
视图里那条自己还没 id 时〔属于没有模组认领的实体〕就地补一次，保证"一个类一个 id"不因登记顺序破掉）。

| 探针（`TestCommandSystem#testSkillIdCoverage`，只读） | 修复前 | 修复后 |
|---|---|---|
| 注册表视图 | 24 条 / 0 条没 id（**看不出问题**） | 24 条 / 0 条没 id |
| 各实体控制器里的技能**实例** | **25 份 / 6 份没 id** | 25 份 / **0 份没 id** |
| 白厄控制器三条 | `[null, …:normalSkill, …:ultimateAttack]` | `[…:commonAttack, …:normalSkill, …:ultimateAttack]` |
| 白厄副本（战斗里那份「卡厄斯兰那」）`skills` | 同上是半截 | 三条都带 id（**用户看到的那一面**） |

修复前那 6 份：`phainon` / `insectBoss` / `commonInsect` / `iceInsect` 各自的
`universalSkill.CommonAttack` + `completeContainer` 的 `SacrificeCloudOfDeath`、`SacrificeFateDrawsNear`。

⚠️ **"天生不该有 id"的两类（写进 `70-DATA.md` §5.10.3，别为了凑绿硬塞）**：
① 运行时**纯参数载体**（`FlameReaver#bossJoinsJointAttack` 的 `new CloudOfDeath()` 只借倍率算一次伤害，
不进任何技能表、不出现在 `/data`）；② 构造期留下、**没进控制器**的临时列表
（模板 `Phainon.skills` 字段 —— 它是 `AwakeEndListener` 结束变身时**还原技能表用的那份实例**，
"顺手改成控制器副本"等于动变身时间轴，**不许改**）。

**验收**：`check-sources.ps1` → `Java files: 226 / CHECK OK`；`javac` → `exit 0`；
自测 → **`通过 807 条，失败 0 条`**（797 → 807，只涨不跌；本轮 +10 =
`testSkillIdCoverage` 6 条 + `testCombatLogColors` 4 条，其中颜色那 4 条见 `60-COMBAT.md` §5.5.2）。
**改动文件**：`world/World.java`、`entity/LivingThing.java`、`debug_tools/TestCommandSystem.java`
（**新增 0 个文件**；`/data` 键名、13 参构造器、`mods/`、用户 `config/gameConfig/` 下的 5 份 JSON 全部没动）。

### 12.5 ⚠️ "看着能配、其实会打折扣"的两条（配数值的人该知道）

1. **`atkMagnification` 对这 6 个技能只在"第一次出手"生效**：它们出手结束会把倍率**复位成
   构造器里那个字面量**（`FoundationStardeathVerdict:130-153` 的 `0.45`、`Counterattack` 的 `1`、
   `NecessarySuffering`/`MournNotAbandon` 的 `1`）—— 副本被复用，第二次出手读的是复位值。
   本轮**没改**（改了就是"复位到出厂倍率"的一次重构，会和数值改动混在一起）。
2. **具名系数是"数值"，不是"机制开关"**：把 `scourgeCostCap` 从 4 改成 3，"满层收尾那一击"
   的门槛也跟着变成 3（判定已经改成 `!= scourgeCostCap`，不会再出现"永远不打收尾"）；
   但把 `hitsPerScourge` 改成 0 就等于这一招只剩收尾 —— **配置能改数，不能保证改完还平衡**。

### 12.6 自测与验收（737 → 754）

- 新用例 `TestCommandSystem#testSkillCoefficients`（**17 条**）：出厂值逐位相同（6 条）＋
  扁平键被认下来（1）＋**改了真的改变伤害**（2：死星天裁 6 段 → 1 段，`6120 → 1020`；
  盗火行者每层倍率改 0，两段伤害整段消失 `13674 → 5128`）＋默认文件里看得见（1）＋
  **把默认值原样打回来伤害逐位不变**（1：`13674 = 13674`）＋互不串味（2）＋
  写到别的技能上被点名（1）＋`copy()` 带系数（1）＋类型不对只跳过（1）＋现状记录（1）。
- **AI 实跑**（自带 JDK，`out/llmcheck`）：`check-sources.ps1` → `Java files: 225 / CHECK OK`；
  `javac` → `exit 0`；自测 → **`通过 754 条，失败 0 条`**（737 → 754，**只涨不跌**）。
- 没用到的验证：进游戏看手感/观感（AI 做不了，见 `30-WORKFLOW.md` §0 最后一条）。

### 12.7 本轮**没有**做的

- ❌ **表达式求值器 / DSL**（把整条公式写进 JSON）：另一个子系统，要用户先拍板。
- ❌ **`LastAttack` 的 `0.125` / 下限 7 外置**：`60-COMBAT.md` §5.7 要求"先问用户"。
  （2026-10-03 晚的「技能注册表」那一轮**也没有顺手接** —— 只把 `new` 换成 `原型.copy()`。）
- ~~**觉醒技能的"配得到"**（§12.4）：方案 A/B 等用户选。~~ ✅ **已落地（方案 A）**，见 §12.4.1。
- ❌ **给"表定义 + 代码生成"加任何东西**：那条路 2026-10-03 已被用户否决（`10-RULES.md` §9.3）。
- ❌ **`EntityKeySpecs` / `SkillKeySpecs` 的标量规格一行没删**（§9.3 最后一条：要删先读 §12.5 的三条阻碍）。

#### 12.7.1 本节记录的那两件**当天晚上就修了**（2026-10-03，用户原话"ok,修1,2"）

这一轮只动 `notes_for_llm/` 与本文档（用户要求"别碰 `src/`"），于是把两件**已知但没修**的记在这里
"免得下次当新发现"。**用户当轮就拍了板**，两件都已经修掉，落点见下面那张表的第三列。

| 事项 | 当时的现状 | 现在修成了什么 |
|---|---|---|
| **`EntityData.default.json` 恒为空壳**（实测 **106 字节**） | **mtime 是硬证据**：2026-10-03 晚用户重启过游戏，`EntityData.json` = `15:34:26`、`SkillData.json` = `15:37:40`、`GameRules.json` = `15:39:55`（自愈跑过、文件被补过），而 **`EntityData.default.json` 与 `TagConfig.json` 还停在 `14:44`** —— 参考副本**一次都没被重写**。原因：`writeReference` 只由 `writeAll` 调，而三个 `writeAll` 调用点里**第一个跑的是 `loadGameRules`（`GameMain:65`）**，那一刻 `World` 注册表还空着 → 参考副本天生是空的；此后 `healDefaultConfigFiles` **刻意不碰它**。⚠️ **它与本轮早些时候修掉的"应用 0 项"是同一个根因**（`loadGameRules` 早于内容注册）—— 当时只修了 `EntityData.json` / `SkillData.json` **两条**路径，**参考副本这第三条漏了** | ✅ 新增 `ConfigDefaultWriter#writeReferenceIfNeeded(file, name)`（判据：**没有 `entities` 段或它是空的**才写，**有内容一个字节都不动**），挂进 `ConfigLoader#healDefaultConfigFiles()` —— 也就是 `loadEntityData()` 那一刻，**注册表已经满了**（官方 + 模组内容都注册完、玩家还没选人）。`writeAll` 也改用它，于是"文件都全时这一轮写 0 个"（原来固定写 1 个）。**顺序依赖写进了那两处注释**（同一个坑踩过两次）。`README.md` 那句"删掉会重新生成一份全量默认值"**对它现在是真的** |
| **盗火行者血量"一个值两个入口"** | `GameRules.json` 的 `flameReaver.baseHpMax` 在**构造时**读一次（`FlameReaver.java:282-283`），`EntityData.json` 的 `derived.hpMax` 是**构造之后**打的补丁 → **有 `derived.hpMax` 时改规则表那个数看不出效果**。设计侧本来就有一条同形的纪律（§11.6 记的"不再给 `FlameReaver` 开 per-template 入口，否则同一个值两个地方能改、谁赢看顺序"），但**血量这一个已经被 `derived` 咬到了** | ✅ **语义（谁赢）**：`derived.hpMax` 赢 —— 不改顺序（那会破掉"补丁永远覆盖构造值"这条全项目统一的纪律）；改的是"**看不见**"：`EntityDataPatcher#noticeDualEntryHpMax` 在**两个入口都被显式写过**时打一行 `[配置提醒] …「derived.hpMax」赢…想改这只模板的血请只留一处…`（含规则表出厂值）。**提醒是新的第三类记账**（`Report#notices()`）：**不进** `skipped` / `errors`、**不让** `isClean()` 变 false（键真的生效了，报成"跳过"是另一种谎话）；只写一处时**刻意不吵**。**同类排查**：全项目"只在构造时读一次"的规则键里，**只有这两个**有第二个用户入口 —— `flameReaver.baseHpMax` / `insectBoss.baseHpMax`（判据登记在 `EntityDataPatcher#constructionHpRuleKeyOf`）；`formula.*` 与 `flameReaver.damageReductionLayers` 都不是（后者第二个入口是**游戏自己的 `whenFightStart`**，且它在 `CLASS_RUNTIME` 里被挡住） |
| **`CONTENT-GUIDE.md` 数字与事实复核** | 2026-10-03 晚逐条对过源码：§1.1 十三个类别、§1.2 十四条、§1.3 十二条**都在**，`CLASS_CONFIG` **7** / `CLASS_RUNTIME` **19** 与 `DataKeys` 实际清单**逐字相等**；`check-sources.ps1` → **`Java files: 225` / `CHECK OK`**（含 `mods/` 的 13 个源码是 **238**）、自测基线 **754** | ✅ 那两处数字随本轮同步：基线 **754 → 769**、§1.2 第 14 条补上"谁赢 + `[配置提醒]`"、§8 第 2 条从"没实跑验证"改成"已修，但**删掉重启**那最后一步仍只有用户能验" |

**本节原本挂着的"唯一还挂着的用户可见不一致"已消失**：`README.md` 里"删掉整个配置文件 =
下次启动重新生成一份全量默认值"那句，现在对 **4 份**文件（`EntityData.json` / `SkillData.json` /
`GameRules.json` / `EntityData.default.json`）**都成立** —— 修法让那句话成真，
所以**没有把 README 改成"实话版"**（两条路里选了"让约定成立"那条）。
⚠️ **AI 侧的不确定性**：本轮**没有启动游戏**，"删掉参考副本 → 重启 → 它被填满"这最后一步
只在自测里用 `out/` 临时目录跑过同一条代码路径（6 条断言，含与 `EntityData.json` 的口径比对）。