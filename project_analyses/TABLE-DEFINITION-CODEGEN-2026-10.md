# 「表定义 + 代码生成」设计方案（2026-10-03 版）

> ## ⛔ 已评估、**用户否决**（2026-10-03）
>
> **本文这条路不再实施，别再按它动手，也别再提一遍。**
> 用户在读完本文与 `CONFIG-LOADING-DECOUPLING-2026-10.md` §十一（反射实验）之后**明确否决**了
> 「表定义 + 代码生成」，改选**「反射混血」**：**键清单交给反射，编排逻辑（顺序 / 子块 / 重算 /
> 块结构 / 别名）留手写**（落地记录见 `CONFIG-LOADING-DECOUPLING-2026-10.md` §12）。
>
> **否决理由（一句话）**：它**不比现状省事** —— 那条路上"加一个字段"要**改 1 行表 + 跑一次生成器**，
> 而现状（表驱动）本来就是 1 行、反射混血路是 **0 处**；多出来的那个构建步骤
> （生成器 / 产物同步 / "产物要不要进版本库"）换来的编译期兜底，在**这个项目**的规模上不划算。
>
> **本文的用途（保留不删）**：它是这三条路（现状表驱动 / 反射混血 / 表定义代码生成）里
> **唯一一份把第三方依赖、构建步骤、产物同步都算过账的评估**（账在 **§六**，三条路的并排数字在
> **§1.3** 与 **§2.3**）。将来若有人**再次**提出这条路，**先读 §六 与本节的否决理由**；
> 要推翻这个否决，请拿出"比 §12 的 0 处更省"的数字，而不是重述动机。
> 项目第 0 条禁止重复分析已被否决的方案。

> **这是什么**：对用户 2026-10-03 那个决定的落地设计 ——
> 「要不转向表定义 + 代码生成吧，就像崩铁那样。**纯粹是闲得慌。引入第三方库也可以。**」
>
> **本文一行 `src/` / `mods/` / `config/` 代码都没改**，只新建了这一份文档。
> 动机是「想做对、做得像那么回事」，所以本文**不讨论值不值得做**，只回答**怎么设计才对**、
> 以及**真实代价是多少**。
>
> **谁要读**：下一个要碰 `src/cn/gfhnv/game/system/configLoadingSystem/`、
> 想给实体/技能/规则加键、或者想「顺手把配置层换成表驱动」的 AI。
> 前情提要在 `CONFIG-LOADING-DECOUPLING-2026-10.md`（§十 落地记录、§十一 反射实验）——
> **本文不重复那两份的结论**，只回答「表定义和 Java 类怎么对齐」。
>
> **关于「崩铁」这个类比**：我**无法核实**崩铁（或任何商业项目）内部的管线是怎么做的，
> 所以本文**不拿它当论据**。下面一律把「表定义 + 代码生成」当成一个**模式**来设计；
> 用户那句「代码里的结构体由表生成」我按字面理解成需求（见 §2.1 的 A 路），并逐条评估它在**本项目**的形状。
>
> **一句话结论**：**要做，就做 B 路的正确形状 —— 表里写的不是「名字」而是「Java 符号」，
> 于是每一列都由 `javac` 兜底；但必须承认：B 路在用户唯一在乎的那个指标
> （「加一个字段要改几处」）上既不比现状好、也不比反射好**（现状 1 行 / 反射 0 处 / 本文 1 行 + 一个构建步骤）。
> 它换来的是另外三样东西：**编译期兜底**、**表本身可读**、**同一个来源能长出文档与默认值**。
> 另外：**最大的落地风险不是设计，是依赖能不能拉下来** —— 本轮**实测：拉不到**（§1.4），
> 所以推荐的表格式是**零依赖**的（§3.2）。

---

## 零、TL;DR

| # | 问题 | 结论 | 关键理由 |
|---|---|---|---|
| **风险前提** | **新依赖到底拉不拉得下来？** | **本轮实测：拉不到**。`Invoke-WebRequest` / `curl.exe` 对 `repo1.maven.org`、`maven.aliyun.com`、`baidu.com`、`example.com` **全部失败**（DNS 能解析、TCP 443 能连上，就是收不到数据）；`gradle` 本身在本沙箱**起不来**（native 库 / daemon 两条路都被拒）。**但用户自己的机器拉得到**：`~/.gradle/caches/modules-2/files-2.1/org.json/json/` 里躺着 `20240303` 与它的 sources，那是历史上下载成功的证据 | 证据与复现命令见 §1.4 与附录 A。**结论：不要把方案押在「新依赖能拉到」上** |
| **① 对齐方式** | **选 B（手写类 + 生成绑定表），不选 A（生成整个属性类）** | A 在本项目有三个硬伤：行为（公式/`copy()`/重置/派生重算）无法生成，而**那才是 bug 的产地**；生成类会插进 `LivingThing` 的继承链（2391 行、11 个子类、460 个访问点）；生成物缺失 = **整个项目编译不过**。**B 的关键不是"生成"，是"表里写 Java 符号"** —— 见 §2.2 | §2 |
| **① 最要紧的那问** | **生成器怎么知道哪一列对应哪个 setter？** → **不靠命名约定**，表里那一列写的就是 `LivingThing::setHpGrow` 这种**方法引用**（不是字符串）。于是：成员改名/写错 → **javac 报错**；`Kind` 与 setter 类型不匹配 → **javac 报错**（`strings(...)` 包装器只吃 `String` 的 setter）。**反射路的脆是"运行期用字符串找成员"，这里每一步都是编译期符号** | §2.2 |
| **① 加一个字段改几处** | **现状 1 文件 1 行 / 反射 0 处 / 本文（B 路）1 行表 + 1 次生成器 + 1 个生成物 diff**。**B 路在这个指标上不赢**，这是必须说清楚的 | §2.3 |
| **② 表格式** | **`.xlsx` + `java.util.zip` 自读（零依赖）** | 与 POI 路**手感完全一样**（就是 Excel），但**离线风险 = 0**（本轮已证明依赖可能拉不到），产物体积 **+0 字节**。代价：只支持「一 sheet 一表、首行表头、单元格只有字符串/数字/布尔」，公式/合并单元格一律**报错退出、绝不猜** | §3 |
| **② 表放哪** | **新建 `tables/`（仓库根目录）**，**不放 `config/`** | `config/` 是**运行期游戏数据**（用户天天改、游戏直接读）；`tables/` 是**构建期源料**。混在一起就会复现本项目最阴的那类病：「我改了表，游戏怎么没变」。`.gitignore` 里**没有** `tables/` → 默认会被提交（正是我们要的） | §3.3 |
| **③ 生成器放哪** | **`src/cn/gfhnv/debug_tools/tablegen/`（跟着 `src` 一起编译）+ 根目录 `run-tablegen.ps1`**，**不是 `build.gradle` task** | 工程里**没有可用的 gradle wrapper**（§1.5 B2），而 `check-sources.ps1` 与 `test-command-system.ps1` **都完全不用 gradle**。放进 `src` 的唯一理由：**自测能重新读表**，于是「表 ↔ 生成物一致」这条守卫才存在（`debug_tools` 里已经有 `ConfigReflectionProbeEntity` 这种纯自测用的东西，先例成立） | §4.1 |
| **③ 何时跑** | **手工跑，永不自动** | 自动跑会让「表缺失 / 生成器坏了」变成**整个游戏构建失败**——用户唯一的构建命令是 `gradle shadowJar`，绝不能因为一张表就编不出 jar。「忘跑」这个风险由**自测守卫**兜（表与生成物不一致 → 自测红） | §4.2 |
| **③ 生成物进 git 吗** | **进**（`EntityKeySpecsGenerated.java` 等，与源表一起提交） | 不进的话，「克隆后 `gradle shadowJar` 能编出 jar」这条**唯一的构建路径**就断了（生成物缺席 = 编译不过 = 自测全灭）。diff 噪音用「一个表一个生成文件 + 固定行序 + 文件头写源表 SHA-256」压住 | §4.3 |
| **③ JavaPoet 值不值** | **不值**（自己拼字符串 + 用 JDK 自带的 `javax.tools.JavaCompiler` 编一遍生成物自检） | JavaPoet 又要拉一个依赖（= 回到 §1.4 的风险），而生成物只有 1~3 个结构固定的类。`javax.tools` 本项目**已经在用**（模组系统就靠它动态编译），复用零成本 | §4.5 |
| **④ 共存** | **`KeySpec` / `SpecPatcher` / `SpecWriter` / 三个 `ConfigXxxPatcher` 一个不动**；`EntityKeySpecs` 的**表格本体**换成生成物，手写文件退化成薄壳转发。`ReflectionConfigBridge` / `@NoConfig` **留着，改当"审计器"** | 生成物产出的就是同一个 `List<KeySpec<T>>` → **运行期架构零变化**、`/data` 契约零变化、模组与 6 份真 JSON 零变化。而反射路的**盲点正好是生成路的补集**（见 §5.5），留着当守卫比删掉有用 | §5 |
| **⑤ 真实代价** | 多一个**手工构建步骤**、多一份**要自己维护的生成器**（约 **600~700 行**：读表 300 + 生成 200 + 守卫 100）、多一个**二进制中间产物**（`.xlsx` 的 git diff 不可读）；换来 3 条（编译期兜底 / 表可读 / 一处真相能长出文档与默认值） | 逐条给数字见 §6 |
| **⑥ 分几阶段** | **5 步**，第 1 步**半天~1 天**、**零行为变化**、**能单独停**，而且**第 1 步就把"这条路走不走得通"验完**：把现有 31 个实体键抄成一张 `.xlsx`，生成出等价的手写表，**自测 702 条一条不改、全绿** | §7 |
| **⑦ 要拍板** | **4 条**（表管什么范围 / 表用什么格式 / 生成物与生成器的三件套决定 / `DataKeys` 要不要也生成），每条带推荐 | §8 |
| **最大风险** | **不是设计，是「表里漏了一行」——生成路的失败模式默认是静默的**（表里没有 = 这个键配不了，而**没有任何人会报错**）。反射路恰好相反（默认开放、忘了关就多一个键）。**所以最终形态是「表生成 = 真相源 + 反射 = 审计器」**，两条路互为补集 | §5.5 |

---

## 一、现状体检（2026-10-03 实测；行号/计数一律用 `[System.IO.File]::ReadAllLines`，复现命令见附录 A）

### 1.1 规模

| 指标 | 数字 | 怎么来的 |
|---|---|---|
| `src` 下 `.java` | **224** | `check-sources.ps1` 本轮实跑输出 `Java files: 224` |
| `mods` 下 `.java` | **13** | `Get-ChildItem` 计数 |
| `configLoadingSystem` 包 | **15 个文件** | 见下表 |
| 自测基线 | **`通过 702 条，失败 0 条`** | ⚠️ **引用** `notes_for_llm/90-STATE.md:17`，**本轮我没能复跑**（`javac` 被拒，见附录 C） |
| 静态自查 | **`CHECK OK` / 224 文件 / 0 问题** | ✅ **本轮实跑**（`& .\check-sources.ps1`） |
| fat jar | **`FightGameReforged-1.2.1.jar` = 740,637 字节（0.71 MB）** | `build/libs/`，mtime `2026-10-03 14:39:49` |
| fat jar 内部 | 405 条目 / 解压后 1,492,189 字节；`cn/*` 363 个 class = 1,336,452 字节；**`org/json/*` 31 个 class = 147,789 字节** | `System.IO.Compression.ZipFile` 逐条目统计 |
| `/data` 键名契约断言点 | **134**（不是文档里那个 122 —— 那是 2026-10-02 的口径，后续几轮又加了断言） | `TestCommandSystem.java:2427-2782` 三个 `/data` 测试方法里 `check(|run(|expectSyntaxError(` 的计数 |

**`configLoadingSystem` 逐文件行数**（`ReadAllLines`）：

| 文件 | 行 | 它在这次设计里的角色 |
|---|---|---|
| `DataKeys.java` | **912** | 键名常量 + 元数据入口（132 个 `public static`） |
| `ConfigDefaultWriter.java` | **903** | 写默认文件 / 出厂值参考副本（遍历表） |
| `EntityDataPatcher.java` | **809** | 实体补丁（遍历表） |
| `ConfigLoader.java` | **769** | 路径、时机、模组入口（**不碰**） |
| `SkillDataPatcher.java` | **735** | 技能补丁 |
| `ReflectionConfigBridge.java` | **439** | 反射实验（并列的一条路） |
| `EntityKeySpecs.java` | **360** | ★ **实体键表**（要换成生成物的就是它） |
| `KeySpec.java` | **276** | 一条键的声明（record + `Kind` + `coerce`） |
| `RuleKeySpecs.java` | **234** | ★ **规则键表**（29 行） |
| `GameRulesPatcher.java` | **228** | 规则补丁 |
| `GameRules.java` | **191** | 静态规则表 |
| `SkillKeySpecs.java` | **183** | ★ **技能键表**（8 行 + 2 个块） |
| `SpecPatcher.java` | **176** | 通用补丁器（遍历表） |
| `RuleDefaults.java` | **164** | 规则出厂值常量（表已删、常量留） |
| `SpecWriter.java` | **54** | 通用采集器（给默认文件生成器用） |

### 1.2 「现在的表」到底长什么样（这决定了生成器要生成什么）

**它不是数据，是 Java 表达式。** 这是本次设计最重要的一条既有事实。
`EntityKeySpecs` 里一行长这样（`EntityKeySpecs.java:139-141`）：

```java
keys.add(spec(DataKeys.Base.HP_GROW, DataKeys.GROUP_BASE, KeySpec.Kind.DOUBLE,
        LivingThing::getHpGrow, doubles(LivingThing::setHpGrow),
        "生命成长系数（每级 +本值 点生命上限）"));
```

**六个位置全部是编译期符号**：键常量（`DataKeys.Base.HP_GROW`，`static final String`）、
分组名（常量）、**值的种类**（枚举）、**读成员**（方法引用）、**写成员**（方法引用 + 类型包装器）、说明（字面量）。

**三张表的实际规模**（本轮实测）：

| 表 | 行形态 | 表行数 | 展开后的键数 |
|---|---|---|---|
| `EntityKeySpecs` | `keys.add(spec(...))` × 19（其中 **2 行是五行循环**，各产 5 个）+ `DERIVED` 的 `List.of(spec(...))` × 4 | **19 + 4** | **31**（27 面板/块 + 4 派生） |
| `SkillKeySpecs` | `SCALARS` 里 `spec(...)` × 8 + `BLOCKS` × 2 | **8 + 2** | 8 标量 + 2 块 + 1 个动态旋钮键 |
| `RuleKeySpecs` | `row(键常量, 段名, 出厂值常量, 说明)` × 29 | **29** | 29 |

**展开后的实体键 31 个** = 10 个标量（`name`/`level`/`mass`/`type`/`description`/`speed`/`elementSort`/`hpGrow`/`attackGrow`/`defenceGrow`）
+ 5 个 `…Resistance`（循环生成）+ 1 个 `manaGrow` 块 + 5 个 `…ManaGrow`（循环生成）
+ 1 个 `inventorySlots` + 5 个临时属性键（`criticalRate`/`criticalDMG`/`enhance`/`penetration`/`defenseLoss`）
+ 4 个派生（`hpMax`/`attack`/`defence`/`hp`）。
**这个 31 与 `CONFIG-LOADING-DECOUPLING-2026-10.md` §11.6 里那句「手写表 31 个键（含 `manaGrow` 块与 `inventorySlots`）」对得上** —— 两处独立数出来的。

两条**必须由生成器继承**的既有约束（踩过坑，别重踩）：

1. **生成物不许引用 `DataKeys` 的非编译期常量**。`DataKeys.<clinit>` 会调 `EntityKeySpecs.meta()`，
   如果表又回头读 `DataKeys.ELEMENTS`（它不是编译期常量），两个类的静态初始化会互相等 → `ExceptionInInitializerError`
   （`CONFIG-LOADING-DECOUPLING-2026-10.md` §10.5 第 2 条）。
   所以五行名单一律从 `ElementSort.values()` 派生；表里引用的 `DataKeys.Base.*` 等**必须是 `static final String`**，javac 会内联，不触发 `clinit`。
2. **表的顺序 = 应用顺序**：`level` 最先（`Entity#setLevel` 会重算三围）、`hp` 最后（`setHp` 会被 `getHpMax()` 夹）。
   这条**顺序语义**必须成为表里的一列（或者"表的行序即顺序"这条约定写死在生成器里，并在生成物头部注明）。

### 1.3 「加一个字段要改几处」—— 三种口径并排（用户唯一在乎的指标）

| 路 | 加一个**全新可配置实体字段** | 加一个**五行类字段** | 出处 |
|---|---|---|---|
| **重构前**（2026-10-03 之前） | 4 文件 9~10 处 | 5 文件 27~28 处 | `CONFIG-LOADING-DECOUPLING-2026-10.md` §2 |
| **现状**（手写 `KeySpec` 表，§十 落地） | **1 文件 1 行** | **1 文件 1 行**（五行循环自动跟上） | 同文档 §10.3 |
| **反射路**（已实现的并列实验） | **0 处配置层改动** | 0 处 | 同文档 §11.4 |
| **本文（B 路：表 + 代码生成）** | **1 行表 + 跑一次生成器 + 生成物 diff 1 行** | 同上（表里一行，循环也由生成器产出） | 本文 §2.3 |
| **本文（A 路：生成整个属性类）** | 1 行表 + 跑生成器，**但行为（公式/`copy()`/重置/派生重算）仍然手写** | 同上 | 本文 §2.1 |

> **必须把这句话写在这里**：**代码生成这条路，在这个指标上打不过现状，也打不过反射。**
> 现状已经是「1 文件 1 行」了。选它的理由不能是"少改几行"，只能是别的东西（§2.4 列了三条）。
> 用户已经拍板要做，本文的任务是**把它设计对**，所以下面不再重复这一条。

### 1.4 ★ 依赖到底拉不拉得下来（用户点名的第一风险，本轮实测）

**四个问题的逐条答案：**

**① `build.gradle` 有没有 `repositories {}`？配的是哪个源？**
**有**，`build.gradle:33-37`：

```groovy
repositories {
    maven { url 'https://maven.aliyun.com/repository/public' }
    maven { url 'https://maven.aliyun.com/repository/google' }
    mavenCentral()
}
```

另外**用户主目录里还有一份 `C:\Users\Administrator\.gradle\init.gradle`**（**不在仓库里**），
又给所有工程插了三个阿里云镜像：

```groovy
allprojects {
    repositories {
        maven { url 'https://maven.aliyun.com/repository/public' }
        maven { url 'https://maven.aliyun.com/repository/google' }
        maven { url 'https://maven.aliyun.com/repository/gradle-plugin' }
    }
}
```

> ⚠️ 这一条对「表定义」很关键：**构建的仓库配置有一半住在仓库外面**。
> 换台机器 / 换个人克隆，解析行为就变了。

**② 项目里的第三方 jar 放哪？手工放 `lib` 还是走依赖解析？**
**两条路同时存在，而且用的是不同版本**：

| 路径 | 用什么 | 位置 |
|---|---|---|
| **gradle 构建**（打 jar） | **走依赖解析**：`build.gradle:40` `implementation 'org.json:json:20240303'` | `~/.gradle/caches/modules-2/files-2.1/org.json/json/json-20240303.jar`（78,332 字节） |
| **自测脚本**（`test-command-system.ps1`） | **手工放的 jar**：`test-command-system.ps1:27` `lib\json-20231013.jar` | 仓库里 `lib/json-20231013.jar`（74,702 字节，mtime `2026-01-18`） |

> 也就是说 **`lib/json-20231013.jar` 是历史遗留**：`build.gradle` 根本不引用它，
> 它是自测脚本与 AI 侧 `javac` 命令的 classpath。**同一个项目对同一个库喂了两个版本**
> （20231013 vs 20240303）。这不是本次的阻碍，但它是"依赖在这项目里有两套口径"的现状证明。

**③ 能不能真的从 Maven Central 拉到新依赖？—— 实测：本会话拉不到，用户机器能拉到。**

我用四种方式探过（全部命令见附录 A）：

| 探测 | 结果 |
|---|---|
| `Resolve-DnsName repo1.maven.org` | ✅ 解析成功（走 cloudflare，返回 IPv6 `2606:4700::6812:130c`） |
| `Test-NetConnection repo1.maven.org -Port 443` | ✅ `TcpTestSucceeded=True`（**TCP 能连上**） |
| `Invoke-WebRequest -Method Head https://repo1.maven.org/.../poi-5.2.5.pom` | ❌ `基础连接已经关闭: 接收时发生错误。`（强制 TLS 1.2 后同样失败） |
| `Invoke-WebRequest` 到 `maven.aliyun.com` / `www.baidu.com` / `example.com` / `github.com` | ❌ **四个全部同样的错** → 不是源的问题，是**整个出网被拦** |
| `curl.exe -sS -I https://repo1.maven.org/...` | ❌ `curl: (35) schannel: AcquireCredentialsHandle failed: SEC_E_NO_CREDENTIALS` |
| `gradle dependencies`（临时工程，见下） | ❌ **gradle 根本起不来**：先 `Could not initialize native services / Failed to load native library 'native-platform.dll'`；把 `GRADLE_USER_HOME` 指到可写的 TEMP 后变成 `A problem occurred starting process 'Gradle build daemon'`。`--no-daemon`、`-Dorg.gradle.native=false`、去掉所有 `-D` 都试过，三次全败 |

**而「用户自己的机器拉得到」有硬证据**：`~/.gradle/caches/modules-2/files-2.1/` 里躺着

```
org.json:json            -> json-20240303.jar, json-20240303-sources.jar
com.gradleup.shadow:shadow-gradle-plugin -> shadow-gradle-plugin-8.3.0.jar (+sources)
org.ow2.asm:asm / asm-commons / asm-tree (9.7)  … 等 19 组
```

—— 这些东西**只可能是从某个仓库下载下来的**。所以：

> **结论（写进文档的实测结果）**：
> **我的沙箱里拉不到任何新依赖（网络整个被拦 + gradle 起不来）；用户的环境曾经、且大概率现在仍然拉得到**（阿里云镜像 + 缓存里有历史下载物）。
> **但任何"克隆到新机器 / 没网 / 镜像挂了"的场景都拉不到** —— 而本项目是发 GitHub 的公开仓库。
> 所以本设计**不把方案押在"能拉到新依赖"上**；能不用依赖就不用（§3.2）。
> **需要用户自己确认一次**（只有他能跑 gradle）：
> ```powershell
> gradle -q dependencies --configuration runtimeClasspath
> ```

**④ `shadowJar` 产物多大？加 POI 会涨多少？**

- **实测现状**：`FightGameReforged-1.2.1.jar` = **740,637 字节**；里面 `org/json/*` 31 个 class 占 **147,789 字节（解压后）**，
  按比例就是**大约 1/10 的 jar**（`cn/*` 363 个 class 占 1,336,452 字节）。
- **POI 会涨多少**：⚠️ **本轮给不出实测数字** —— 我拉不到 POI，本机也搜不到任何 POI jar（gradle 缓存里没有）。
  按记忆，`poi-ooxml` 会带进 `poi` + `poi-ooxml-lite` + `xmlbeans` + 若干 `commons-*`，
  是**十几 MB 量级**（相对现在 0.71 MB 的产物，是**十倍以上**）。
  **这条属于"未经实测的粗估"，请用户自己量**（命令见附录 A 第 ⑤ 条）。
- **★ 但真正要紧的不是"多大"，而是"它进不进 `shadowJar`"**：
  `shadowJar` 默认收的是 **runtime classpath**。**代码生成器是构建期工具，根本不需要进 jar** ——
  把 POI 放进一个**不参与 `runtimeClasspath` 的自定义 configuration**（或 `buildscript` classpath），
  **fat jar 增长 = 0 字节**。代价是：这需要改 `build.gradle`（就一处），而 **gradle 恰好是我本轮验证不了的那一环**。

**不需要网络的退路（三条，按推荐顺序）：**

1. **零依赖自读 `.xlsx`**（本文推荐，§3.2）—— 完全不碰依赖解析，`gradle` 坏没坏都不影响生成器；
2. **手工把 POI 的 jar 丢进 `lib/`**（自测脚本已经在用这个模式），**但 `.gitignore:42` 忽略 `/lib/`**
   → **每个克隆都要自己放一遍**，而且生成器还得知道去哪找（多一份隐式约定）；
3. **表用 CSV / JSON 存**（零依赖），代价是手感（§3.1）。

### 1.5 读代码 / 实测时发现的**既有阻碍**（有几条写几条）

| # | 严重度 | 阻碍 | 证据（行号已核对） | 对本次设计的影响 |
|---|---|---|---|---|
| **B1** | ★★★ | **`javac` 在 AI 会话里被沙箱拒绝**，所以本轮**没能编译、没能跑自测** | `test-command-system.ps1:49` 执行 `javac` 时 `Program 'javac.exe' failed to run: Access is denied`（`java.exe -version` 反而能跑）。**`check-sources.ps1` 能跑**（`Java files: 224` / `CHECK OK`） | 「702/0」是引用，不是本轮复跑；**阶段 1 的验收必须由用户跑** |
| **B1b** | ★★ | ⚠️ **我跑 `test-command-system.ps1` 时它先删了 `out\cmdtest`**（脚本 `:31-32` 先 `Remove-Item -Recurse -Force` 再 `New-Item`），随后在第 49 行因 `javac` 被拒而中止 → **`out\cmdtest` 现在是空目录**（0 文件）。`out\llmcheck`(315) / `out\consoleprobe`(169) / `out\cn`(79) 都完好 | 这是**编译产物目录**（`.gitignore:53` 忽略 `/out/`），重跑一次脚本即可重建；**声明在此，不是悄悄发生的** |
| **B2** | ★★★ | **工程里没有可用的 Gradle wrapper**：`./gradle` 下**只有** `gradle/wrapper/gradle-wrapper.properties` 一个文件，**没有 `gradlew.bat`、没有 `gradle-wrapper.jar`**；而且那份 properties **本身被 `.gitignore:60` 忽略** | 构建**完全依赖本机装的 gradle**（用户 `~/.gradle/wrapper/dists/` 里有 7 个发行版：8.9 / 8.14 / 8.14.2 / 9.2.1 / 9.3.0 / 9.6.1 / 9.7.1，工程 `.gradle/` 下有 `9.6.1` 与 `9.7.1` 两个目录） | **不能把生成器做成 gradle task 并指望它可移植**（§4.1） |
| **B3** | ★★ | **`lib/json-20231013.jar` 被 `.gitignore:42`（`/lib/`）忽略，但 `test-command-system.ps1:27-28` 硬要求它存在**（找不到就 `exit 1`） | `.gitignore:42` vs `test-command-system.ps1:27-28`。⚠️ **我无法跑 `git`**（本会话 `git` 与前几轮一样被拦），所以**不能确认它是否已被跟踪**；从 `.gitignore` 看它在忽略名单里 | **这就是"手工放 jar"这条路的现成代价**：新克隆可能跑不了自测。也正因为如此，本文推荐**零依赖**（§3.2） |
| **B4** | ★★ | **构建配置有一半在仓库外面**：`~/.gradle/init.gradle` 又插了 3 个阿里云镜像（§1.4 ①），而它**不在仓库里** | 上面引的那份 `init.gradle` 全文 | 依赖解析"在我机器上能跑"这件事**不可复现** |
| **B5** | ★ | **同一个库两个版本并存**：gradle 用 `org.json:json:20240303`（`build.gradle:40`），自测用 `lib/json-20231013.jar`（`test-command-system.ps1:27`） | 两个 jar 实测 78,332 vs 74,702 字节 | 生成器若依赖 `org.json` 就会有第三个口径；**所以生成器只用 JDK**（§3.2） |
| **B6** | ★ | **生成物必须自己保证能过 `check-sources.ps1`**：它查 **BOM**（`:90-95`）、**缺失 import**（`:126`/`:152-205`）、**未用 import**（`:208-212`）、**同文件重复字段**（`:217-234`，正则只匹配**小写开头**的字段名） | `check-sources.ps1` 本轮实跑 `CHECK OK: no brace / BOM / import / duplicate-field problems.` | **生成器要精确控制它写出的 import**——多写一个没用到的 import 就会多一条 `[UNUSED]` 告警（脚本把它算作 advisory warning，不算 problem，但会印出来） |
| **B7** | ★ | **仓库里的行尾风格是混的**：`GameMain.java` 全是 CRLF（369 行）、`KeySpec.java` 全是裸 LF（276 行） | 逐字节统计 | **生成器必须自己固定行尾**（不能跟着平台走），否则"连跑两次零 diff"在换机器时不成立（§4.4） |
| **B8** | ★ | `DataKeys.java` 已经 **912 行 / 132 个 `public static`**；三张手写表合计 **777 行**装 **68 行"数据"**（31+10+29，含块与旋钮） | §1.1 行数表 + §1.2 表行数 | 这是"值得把表搬到表格里"的**唯一**直觉证据——但注意 §1.3 那个数字并没有变好 |

---

## 二、① 表定义和 Java 类怎么对齐

### 2.1 A 路：生成整个属性类（schema 是唯一真相）

**形状**（Java 没有 partial class，所以只能是"生成的基类 + 手写的子类"）：

```
tables/entities.xlsx  ──►  EntityAttributesGenerated.java   ← 字段 + getter/setter（生成）
                                    ▲
                                    │ extends
                       LivingThing.java                     ← 公式 / copy() / 重置 / 派生重算（手写，2391 行）
```

**它在本项目的三个硬伤（逐条给证据）：**

| # | 硬伤 | 证据 |
|---|---|---|
| **A-1** | **能被生成的那一半，恰好是没 bug 的那一半**。`LivingThing` 里真正出过问题的是**行为**：`copy()` 漏字段（`PROJECT-ANALYSIS-2026-09.md` §6.1 第 11 条）、`whenFightEnds()` 不清减伤（§6.4 N3）、6 个同构公式（`getAttack` 等），以及"声明了却没人读"这一整类（D1 的 14 个键）。**这些一行都生成不了** —— 它们是逻辑，不是结构 | `ENTITY-ATTRIBUTE-SPLIT-2026-10.md:87-95`（复制与重置的痛点清单）、`LivingThing.java:1998-2001`（"故意不在临时属性表里"的那份人工清单） |
| **A-2** | **生成类要插进继承链，而这条链上有 11 个子类、460 个访问点、13 参构造器**。生成物一旦成为 `LivingThing` 的父类，字段可见性（现在 59 个字段全是 `private`）、`@DataFlatten` 组件、`@NoData`/`@NoConfig` 注解、`final` 字段、字段初始化器的默认值**全部要由表来表达** —— 表会立刻膨胀到"用 Excel 写 Java" | `ENTITY-ATTRIBUTE-SPLIT-2026-10.md:18`（59 字段全 `private`、460 访问点全走 getter/setter、13 参构造器是对外契约）、`70-DATA.md:63-80`（`@DataFlatten` 组件与数据名的关系） |
| **A-3** | **生成物缺席 = 整个项目编译不过**。这是**比 B 路严重得多**的一条：B 路生成物缺席顶多"表改了没生效"，A 路生成物缺席是 `LivingThing` 没有父类 → `src` 里 224 个文件**没有一个能编** | 本文 §4.3 的讨论；`.gitignore` 里没有 `/src/` 之外的编译根，`build.gradle:14-20` `srcDirs = ['src']` |

> **A 路唯一真正的用武之地**是**内容表**（几百上千行"怪物/技能/物品"的数据类，崩铁那种量级）。
> **本项目没有这个对应物**：官方内容一共 8 个实体 + 24 个技能 + 17 个效果 + 9 件物品，
> 而且**它们全是行为类**（每个技能一个 Java 类、有自己的 `use()`），不是数据行。
> 所以 A 路在本项目**无处落地** —— 这条不是"我们不想做"，是**没有可生成的对象**。

### 2.2 B 路：手写类 + 生成绑定表（★ 推荐）

**用户点名要求正面回答的问题：生成器怎么知道"表里这一列对应哪个 setter"？**

> ### 答案：**表里那一列写的不是名字，是 Java 符号。生成器不做任何推导。**
>
> 表里不写 `"hpGrow"`（那是字符串，要靠约定去猜 setter），而是写成三列**编译期符号**：
> **`DataKeys.Base.HP_GROW`** / **`LivingThing::getHpGrow`** / **`LivingThing::setHpGrow`**。
> 生成器的工作只是**把这几列按固定模板拼成一行 Java**，一个字符都不推导：
>
> ```
> 表列:  段 | 键常量 | 分组 | 类型 | 读成员 | 写成员 | 说明 | 模式
>   ↓ 生成器只做字符串拼接（没有映射规则、没有命名约定、没有反射）
> Java:  keys.add(spec(DataKeys.Base.HP_GROW, DataKeys.GROUP_BASE, KeySpec.Kind.DOUBLE,
>                        LivingThing::getHpGrow, doubles(LivingThing::setHpGrow), "…"));
> ```
>
> **于是「表写错了」= 编译不过**，四种错法逐一验证：
>
> | 表里写错什么 | 后果 | 谁发现 |
> |---|---|---|
> | 键常量不存在（`DataKeys.Base.HP_GROWW`） | `找不到符号` | **javac** |
> | 读成员不存在（`LivingThing::getHpGroww`） | `找不到符号` | **javac** |
> | 写成员不存在（`LivingThing::setHpGroww`） | `找不到符号` | **javac** |
> | 类型列与 setter 不匹配（`Kind.DOUBLE` 配 `strings(LivingThing::setName)`） | `不兼容的类型`（`strings(...)` 只接受 `String` 的 setter） | **javac** |
> | **类型列写错但恰好能编过**（`Kind.STRING` + `strings(LivingThing::setHpGrow)`） | 编译不过（`setHpGrow` 的形参是 `double`） | **javac** |
>
> **这就是与反射路的根本区别**，也是本次设计里唯一一条"做对了"意义上的硬收益：
> **反射路把绑定推迟到运行期、用字符串去找成员，找不到就静默跳过**（D1 那 14 个键就是这么活了很久的）；
> **生成路把绑定交给 javac**，错了就没有 `.class` 文件。
>
> **剩下唯一一处"约定"**：**表的行序 = 应用顺序**（`level` 最先、`hp` 最后，理由见 §1.2）。
> 这一条**无法由 javac 检查**，所以它必须变成**自测的一条断言**（"第 1 行必须是 `level`、最后一行必须是 `hp`"），
> 或者在生成物头部写死并在表里加一列显式序号。

**B 路要生成的东西（就这三个）：**

| 生成物 | 对应今天的 | 生成物里有什么 | 手写文件还剩什么 |
|---|---|---|---|
| `EntityKeySpecsGenerated.java` | `EntityKeySpecs`（360 行） | `BEFORE_DERIVED` / `DERIVED` / `ALL` 三张表的**表格本体**（31 行）+ 6 个行构造器（`spec`/`strings`/`longs`/`doubles`/`ints`/`bools`） | `meta()` / `groups()` / `elements()`（约 30 行转发） |
| `SkillKeySpecsGenerated.java` | `SkillKeySpecs`（183 行） | `SCALARS` / `BLOCKS` 本体（8 + 2 行） | `isKnown()` / `of()` / `ALL` 转发 |
| `RuleKeySpecsGenerated.java` | `RuleKeySpecs`（234 行） | `ALL` 本体（29 行） | `RuleKey` record / `sections()` / `row()` 转发 |

**手写文件退化成"薄壳"**，例如 `EntityKeySpecs` 最终只剩：

```java
public static final List<KeySpec<LivingThing>> BEFORE_DERIVED = EntityKeySpecsGenerated.BEFORE_DERIVED;
public static final List<KeySpec<LivingThing>> DERIVED        = EntityKeySpecsGenerated.DERIVED;
public static final List<KeySpec<LivingThing>> ALL            = EntityKeySpecsGenerated.ALL;
public static Map<String, DataKeys.KeyMeta> meta()   { return EntityKeySpecsGenerated.meta(); }
public static Map<String, String> groups()           { return EntityKeySpecsGenerated.groups(); }
public static List<String> elements()                { return EntityKeySpecsGenerated.elements(); }
```

**为什么保留这层薄壳而不是直接换掉 `EntityKeySpecs`**：调用点（`DataKeys`、`SpecPatcher`、
`ConfigDefaultWriter` 等）**一个都不用改**，而且**回滚 = `git checkout` 一个文件**（§7 阶段 1 的安全绳）。

**B 路的表要哪些列（够用且不多）：**

| 列 | 例 | 说明 |
|---|---|---|
| `段` | `beforeDerived` / `derived` | 决定进哪张表（顺序也由它 + 行序决定） |
| `模式` | `单行` / `五行循环` | 五行那 10 个键由一行 + 循环产出（今天就是这么做的） |
| `键常量` | `DataKeys.Base.HP_GROW` | **Java 表达式**，javac 兜底 |
| `分组` | `DataKeys.GROUP_BASE` | 同上 |
| `类型` | `DOUBLE` | 枚举名 |
| `读成员` | `LivingThing::getHpGrow` | **方法引用**，javac 兜底 |
| `写成员` | `LivingThing::setHpGrow` | 同上；空 = 块（`manaGrow`/`inventorySlots`） |
| `说明` | 生命成长系数… | 会成为 `KeySpec.note()`，也会被自测打印 |

> **8 列装完 31 个键。** 技能表去掉"段/分组"两列（技能键没有分组概念，见 `SkillKeySpecs:106-107` 的注释），
> 规则表把"读/写成员"两列换成"出厂值常量"（规则键是静态表，没有读写对象 —— `RuleKeySpecs:43` 的 `RuleKey` record 就是这么设计的）。

### 2.3 三路对照：加一个字段到底改几处

| | 加一个**全新可配置实体字段** | 加一个**五行类字段** | 加一条**规则键** |
|---|---|---|---|
| **A 路（生成属性类）** | 表 1 行 + 跑生成器；**行为仍要另改**（`copy()`？战斗结束清不清？要不要重算派生？公式要不要盖？） | 同左 | 不适用 |
| **B 路（本文推荐）** | **表 1 行 + 跑生成器**（生成物 diff 1 行） | **表 1 行 + 跑生成器**（五行由 `模式` 列 + 循环产出） | **表 1 行 + 跑生成器** |
| **现状（手写表）** | 1 文件 1 行 | 1 文件 1 行 | 2 文件 2 处（`RuleDefaults` 常量 + `RuleKeySpecs` 一行，见 §10.3） |
| **反射路** | 0 处配置层 | 0 处 | 不适用（反射面没有规则表） |

> **B 路对"规则键"是唯一一处真的变简单的地方**：现状是 2 处（值常量 + 表行），
> 表里可以把"出厂值常量名"也写成一列 → 生成物里直接引用 `RuleDefaults.FLAME_REAVER_CONTAINER_LIMIT`，
> 于是变成 **1 行 + 生成**。**但 `RuleDefaults` 的常量本身仍要手写**（它被 `{@value}` Javadoc 引用，
> 必须是编译期常量 —— `CONFIG-LOADING-DECOUPLING-2026-10.md` §10.5 第 4 条），所以严格说还是 **2 处**。

### 2.4 那 B 路换来什么（三条，诚实版）

1. **编译期兜底（唯一一条"更对"的收益）**：表里每一列都是编译期符号 → 表与代码不可能静默漂移。
   这是**反射路给不了的**（反射的失败模式是静默），也是**现状给不了的**（现状的 1 行是手抄的，
   抄错 `DataKeys.Base.HP_GROWW` 会编译不过 —— **等等，这条现状也有**）。
   ⚠️ **更诚实的版本**：现状那 1 行**也是编译期符号**，所以"编译期兜底"这条现状**已经具备**；
   B 路真正多出来的只是"**这 1 行现在住在表格里，而不在 360 行 Java 里**"。
2. **表本身可读、可评审**：31 行 × 8 列摊在 Excel 里，键名/类型/分组/读写成员/说明一眼看全；
   现在它们夹在 360 行 Java + 大量 Javadoc 之间。
3. **同一个来源能长出别的东西**：`TABLES.md`（键清单，给人和 AI 读）、
   `EntityData.default.json` 的**键集合与顺序**（现在是 `ConfigDefaultWriter` 按遍历顺序写的）、
   以及"哪些键进 `/data`"这份口径（现在靠 `KeySpec.Kind.inData()` 推）。
   **这三样现在分别住在三个地方，将来可以从一张表长出来。**

---

## 三、② 表定义用什么格式

### 3.1 候选对照（五个维度，用户点名的那四个）

| 格式 | 产物体积（进 `shadowJar` 吗） | 开发机依赖体积 | **离线风险** | 策划/用户改起来的手感 | 以后接 Excel 的平滑度 |
|---|---|---|---|---|---|
| **`.xlsx` + POI** | **0**（生成期依赖，不进 `runtimeClasspath` —— 但要改 `build.gradle`） | 十几 MB 量级（**未实测**） | **高**：拉不到就没有生成器（本轮**已实测拉不到**） | ★★★★★ 真 Excel | 原生 |
| **★ `.xlsx` + `java.util.zip` 自读** | **0** | **0** | **无** | ★★★★★ 真 Excel（同一份文件） | 原生（**已经在 Excel 里**） |
| **CSV** | 0 | 0 | 无 | ★★☆ Excel 能开，但：中文 Windows 下 Excel 存 CSV **默认 GBK**、单元格里的逗号/换行要转义、双击打开会把 `0012` 变 `12`、长数字变科学计数 | 要"另存为 xlsx" |
| **JSON** | 0 | 0 | 无 | ★☆ 手写 JSON 做表很难受：不能写注释、逗号要数、一行一个键还行、多列就崩 | 要转换 |
| **自定义 DSL** | 0 | 0 | 无 | ★★☆ 要自带 parser、要自带编辑器体验、要自带转义规则 | 要转换 |

### 3.2 ★ 推荐：`.xlsx` + `java.util.zip` 自读（零依赖）

**理由（按权重排序）：**

1. **它把本轮实测到的最大风险直接砍掉**：§1.4 已证明**依赖可能拉不到**（我的会话里是"整个出网被拦"），
   而 `.gitignore:42` 又忽略 `/lib/`。**零依赖 = 这条风险不存在**，而且生成器可以直接跑在任何有 JDK 的地方
   （本项目已经在假设"JDK 在 PATH 里"：`test-command-system.ps1:20-23`）。
2. **手感与 POI 路完全一样**：因为它就是同一个 `.xlsx` 文件。用户/策划在 Excel 里编辑，
   冻结首行、数据验证、批注、颜色全都能用。**将来真想上 POI，只是把"读表"那 250~300 行换掉**，
   表文件、列定义、生成器其余部分**一个字不用动**。
3. **体积 +0 字节**：生成的只是 Java 源码，不是运行时依赖。
4. **xlsx 就是 zip + XML，JDK 自带 `java.util.zip` 与 `javax.xml.parsers`** —— 不需要任何第三方库。
   本项目唯一依赖（`org.json`）也**不需要**：生成器写的是 Java 源码文本，不是 JSON。

**要如实说清的代价与坑（这一节是本文最"值钱"的部分之一）：**

| # | 坑 | 为什么会踩 | 生成器必须怎么做 |
|---|---|---|---|
| **X1** | **列号要靠单元格的 `r` 属性算，不能按出现顺序数** | xlsx 里**空单元格不出现在 XML 里**：`A1,B1,D1` 会让第 3 个 `<c>` 其实是 D 列 | 从 `r="D7"` 里解析出列字母 → 下标；**这一条错了会整张表右移，而且看起来"读到了"** |
| **X2** | **字符串走 `sharedStrings.xml`**，单元格是 `t="s"` + `<v>索引</v>` | 直接读 `<v>` 会拿到 `0`、`1`、`2` 这种索引 | 先建 `List<String>`，再按索引取；另需支持 `t="inlineStr"`（`<is><t>`） |
| **X3** | **sheet 名字与文件路径要过一层 rels** | `xl/workbook.xml` 里的 sheet 只给 `r:id`，真实路径在 `xl/_rels/workbook.xml.rels` | 遍历 `sheet1.xml`/`sheet2.xml`… 或老老实实解 rels；**表名从 `workbook.xml` 读**（这样报错能说"哪张表第几行"） |
| **X4** | **xlsx 里数字全是 double** | `1` 与 `1.0` 在 XML 里长得一样；`Long` 型的出厂值要靠列类型/正则再收一次 | 按"列类型"再收一次（和 `KeySpec.Kind.coerce()` 同一个思路，可以直接复用那张逻辑） |
| **X5** | **公式、合并单元格、多 sheet 引用一律别支持** | 支持它们 = 实现一个 Excel 计算引擎 | **遇到 `t="str"`（公式结果）、`<mergeCell>`、跨表引用就报错退出**，绝不猜。宁可"表写得不合法"也不要"静默读成别的值" |
| **X6** | **`<row>` 也有 `r` 属性**，而且行可以跳号 | 空行不出现在 XML 里 | 同上，按 `r` 定位行号（报错要用得上） |
| **X7** | **日期/时间在 xlsx 里是序列号 + 样式** | 我们**不需要日期**（表里只有键名/类型/说明） | 表里出现日期型 → 报错退出 |

**只支持这么多（这是刻意的窄）**：一个工作簿 = 一个引擎的键表集合；
一张 sheet = 一张表；**第 1 行 = 表头（列名固定，顺序随意）**；**第 2 行起 = 数据行**；
单元格只有三种：**非空文本 / 数字 / 布尔**；**A 列留空的行视为注释行跳过**（方便写分节标题）。

**表长什么样（`tables/entities.xlsx` 的实际形状）：**

| 段 | 模式 | 键常量 | 分组 | 类型 | 读成员 | 写成员 | 说明 |
|---|---|---|---|---|---|---|---|
| beforeDerived | 单行 | `DataKeys.Base.NAME` | `DataKeys.GROUP_BASE` | STRING | `LivingThing::getName` | `LivingThing::setName` | 名称（影响 @e[name=…] 与选人列表） |
| beforeDerived | 单行 | `DataKeys.Base.LEVEL` | `DataKeys.GROUP_BASE` | LONG | `LivingThing::getLevel` | `LivingThing::setLevel` | 等级；会触发整组派生值重算 |
| … | … | … | … | … | … | … | … |
| beforeDerived | 五行循环 | `element + "Resistance"` | `DataKeys.GROUP_BASE` | DOUBLE | `lt -> resistanceOf(lt, element)` | `(lt, v) -> setResistance(lt, element, v)` | （每个元素一条抗性） |
| beforeDerived | 单行 | `DataKeys.Base.MANA_GROW` | `DataKeys.GROUP_BASE` | MANA_BLOCK | … | *(空 = 块)* | 五行法力成长汇总块 |
| derived | 单行 | `DataKeys.Derived.HP_MAX` | `DataKeys.GROUP_DERIVED` | LONG | `LivingThing::getHpMax` | `LivingThing::setHpMax` | 生命上限（覆盖公式结果） |
| derived | 单行 | `DataKeys.Derived.HP` | `DataKeys.GROUP_DERIVED` | LONG | `LivingThing::getHp` | `LivingThing::setHp` | 当前生命值（最后应用，会被上限夹） |

> 「五行循环」那两行的**读写成员是 lambda 文本**（因为今天就是这么写的：
> `EntityKeySpecs.java:148-155` 的 `elements()` 循环里用 `lt -> resistanceOf(...)`）。
> **这是表里唯一一处"写 Java 代码"的列**，它同样由 javac 兜底（lambda 里的 `resistanceOf` 不存在就编译不过）。
> 如果嫌它难看，退路是"五行不循环、10 行摊开写"——表会从 31 行长到 39 行，但**每一行都是平的**。

### 3.3 表定义文件放哪个目录

**推荐：仓库根目录新建 `tables/`。**

```
FightGameReforged/
├── tables/                         ← 【新】构建期源料（会进 git，因为 .gitignore 里没有它）
│   ├── entities.xlsx               #   实体键表（31 行）
│   ├── skills.xlsx                 #   技能键表（8 + 2）
│   └── rules.xlsx                  #   规则键表（29）
├── config/                         ← 运行期游戏数据（用户天天改；游戏直接读）
│   ├── gameConfig/*.json
│   └── data/<modid>.json
└── src/                            ← 编译根（build.gradle:14-20 srcDirs = ['src']）
```

**为什么不放 `config/`（三个理由，都是本项目踩过的坑）：**

1. **`config/` 是"游戏会读、用户会改"的地方**（`ConfigLoader.java:58-82` 五个路径常量全都指向 `./config/…`）。
   把 `.xlsx` 放进去，等于制造一条**新的"改了没反应"路径** —— 而"改了没反应"正是本项目
   最阴的一类缺陷（D1 的 14 个键、D8 的五个 `*ManaGrow`、D9 的 `manaGrow` 块，三条都是它）。
2. **README 已经把 `config/` 的语义写死给用户了**（`README.md:75-99`「改完重启游戏生效」、
   `:95-96`「删掉整个文件 = 下次启动会重新生成」）。往里面塞一个"游戏不读、要跑脚本才生效"的东西，
   **直接和 README 打架**。
3. **`config/` 是运行期目录**：游戏在用户机器上跑的时候，`./config/` 是**工作目录**（README 明确要求"请在项目根目录启动游戏"）。
   构建期源料进运行期目录，迟早会被"打包发布"这条流程卷进去。

**为什么放仓库根而不是 `src/` 下**：`build.gradle:14-20` 的 `srcDirs = ['src']` 是编译根，
把 `.xlsx` 放进 `src/` 会让 `javac` 的源根里混进二进制文件（`check-sources.ps1` 也会去扫它）。

**会不会被 git 跟踪**：`.gitignore`（61 行）里**没有** `tables/`，所以**默认会被提交** —— 正是我们要的。
反过来注意三点：`/lib/`（`:42`）、`/notes_for_llm/`（`:58`）、`/out/`（`:53`）**都被忽略**，
而 `project_analyses/`、`config/`、`tables/`（新建后）**都会被跟踪**。

> ⚠️ **`.xlsx` 是二进制，git diff 读不出来**。表里"改了一行"在提交历史里**看不见**。
> 缓解手段（阶段 3 可选）：让生成器**顺带产出一份 `tables/TABLES.md`**（键清单的文本快照），
> 于是"表改了什么"能在那份 md 的 diff 里看到。代价：多一个生成物、多一处可能不同步。
> **建议先不做**（§9），等真的被 diff 噪音咬到再加。

---

## 四、③ 生成器长什么样、什么时候跑

### 4.1 放哪：`src/cn/gfhnv/debug_tools/tablegen/` + 根目录 `run-tablegen.ps1`（**不是 gradle task**）

**为什么不是 `build.gradle` task（三条，全部有证据）：**

| 理由 | 证据 |
|---|---|
| **工程里没有可用的 wrapper**，构建完全依赖本机装的 gradle；把生成器挂上 gradle，等于把"改一个键"的可行性绑在"这台机器的 gradle 还能跑"上 | §1.5 **B2**；`./gradle` 下只有 `gradle-wrapper.properties` 一个文件，而它本身被 `.gitignore:60` 忽略 |
| **本项目的两条验证路径都不用 gradle**：`check-sources.ps1`（扫 `src`）+ `test-command-system.ps1`（`javac` 编 `src` → 跑自测）。生成器挂 gradle 会变成**第三条独立的路径**，自测**看不到它** → "表 ↔ 生成物一致"这条守卫就没地方放 | `check-sources.ps1:41`（`src/`）、`test-command-system.ps1:37`（`Get-ChildItem src -Recurse *.java`） |
| **gradle task 会让"表缺失 / 生成器坏了"变成整个构建失败**：用户唯一的构建命令是 `gradle shadowJar`，它**绝不能因为一张表就编不出 jar** | `README.md:327`（`gradle shadowJar` 是唯一构建命令） |

**为什么放进 `src/`（而不是 `tools/`）—— 这是本次设计里唯一一处"为了守卫而妥协"：**

- 放进 `src/` ⇒ **自测能重新读表**，于是可以加一条断言：
  **「用生成器在内存里重新生成一遍，逐行与 `src` 里那个生成物比对，不一致就红」**。
  这条断言是**"忘了跑生成器"的唯一兜底**（否则表改了、生成物没更新，游戏照跑、谁都不知道）。
- 代价：读表器（约 250~300 行）会**随 `src` 一起进 jar**。粗算 class 文件 **10~20 KB**
  （对照：`org/json` 31 个 class = 147,789 字节，现在 jar 740,637 字节）。
- **先例成立**：`src/cn/gfhnv/debug_tools/` 里已经有 `TestCommandSystem`（5436 行）、
  `ConfigReflectionProbeEntity`（132 行）这些**纯自测用**的东西，它们也在 jar 里。
- **走 `check-sources.ps1` 的规则**（§1.5 **B6**）：生成器的源码必须无 BOM、import 精确、
  没有小写开头的重复字段名。

**目录与文件（全部是"新增"，不碰 `src` 里的既有文件）：**

```
src/cn/gfhnv/debug_tools/tablegen/
├── XlsxReader.java        【新】零依赖 xlsx 读取（约 250~300 行，坑见 §3.2）
├── TableRow.java          【新】一行表记录 + 列名常量（约 80 行）
├── TableCodeGen.java      【新】把行拼成 Java 源码（约 200 行）
├── TableGenMain.java      【新】main：读表 → 写生成物 / -check 模式（约 120 行）
└── GeneratedTable.java    【新】自测用的入口：重新生成并与 src 里的生成物比对（约 100 行）

run-tablegen.ps1           【新】根目录，与 test-command-system.ps1 同形（编译 src → 跑 main）
```

**`run-tablegen.ps1` 与 `test-command-system.ps1` 的关系**：同形（`Set-Location $PSScriptRoot` →
检查 `javac`/`java` → `javac -encoding UTF-8 -nowarn -d out\tablegen <src 全部 .java>` → 跑 main）。
**唯一的差别**：生成器**不需要 `lib/json-20231013.jar`**（零依赖），所以它比自测脚本更不容易坏
（自测脚本在 `lib/` 缺失时会 `exit 1`，见 §1.5 **B3**）。

### 4.2 什么时候跑：**手工跑，永不自动**

| 方案 | 代价（写清） |
|---|---|
| **★ 手工跑**（`.\run-tablegen.ps1`，改完表跑一次） | 代价：**会忘**。忘的后果有两种，**都不报错**：(a) 生成物过期 → 游戏照跑，新键配不了；(b) 表与生成物不一致 → 只有自测会红。**兜底**：`GeneratedTable` 那条"重新生成并比对"的断言（§4.1），跑自测就能抓到 |
| 自动跑（gradle task / `preBuild` 钩子） | 代价：**表缺失或生成器有 bug → `gradle shadowJar` 直接失败**（用户编不出 jar）；而且自动跑意味着每次构建都要读 `tables/*.xlsx`，**表成了构建的硬依赖**。另外它还绕开了项目的两条验证路径（§4.1 第 2 条） |
| 折中（gradle task 但**不挂到 build 生命周期**，只 `gradle tablegen` 手动调） | 代价：需要给 `tools`/`src` 加一个 source set + 一个 `JavaExec` 任务（改 `build.gradle`），而 gradle 恰好是我本轮验证不了的一环（§1.4 ③）。**收益 = 与手工跑完全一样**。**建议留到阶段 5，等真的嫌脚本麻烦再加** |

**收工 checklist 要加一行**（`00-CATALOG.md` 的收工 checklist 是这类约定的家）：
> 「改过 `tables/` 的，必须跑 `.\run-tablegen.ps1`，然后 `check-sources.ps1` + 自测。」

### 4.3 生成物进不进 git：**★ 进**

| 方案 | 后果 |
|---|---|
| **★ 进 git（推荐）** | 好处：**克隆后 `gradle shadowJar` 直接能出 jar**（唯一的构建路径不断）；`check-sources.ps1` 与自测脚本能直接跑；表丢了也能靠生成物继续开发。代价：**生成物改了会出现在 diff 里**（但它只在表改时才变，而且用户从不打开它） |
| 不进 git（`.gitignore` 掉） | 好处：diff 干净。代价（**不可接受**）：**克隆后编译不过** —— `EntityKeySpecs` 的薄壳引用的 `EntityKeySpecsGenerated` 不存在 → `src` 里 224 个文件**没有一个能编**，`gradle shadowJar` 失败、自测全灭。要补一句"先跑生成器"，而这句话会被忘 |

**压住 diff 噪音的四条约定（写进生成物头部 + 生成器实现里）：**

1. **一个表一个生成文件**（`entities` / `skills` / `rules`），不要把三张表塞进一个文件；
2. **行的顺序 = 表里的行序**（不排序、不按字母序、不按 `HashMap` 序）—— 表里挪一行，diff 就只显示那一行；
3. **文件头写源表的 SHA-256（前 12 位）**，**绝不写时间戳**：
   ```java
   // ============================================================================
   // 本文件由 tables/entities.xlsx 生成，请勿手工编辑（改了下次生成会覆盖）。
   // 源表 SHA-256（前 12 位）：3F9A1C7E02BD
   // 重新生成：.\run-tablegen.ps1
   // ============================================================================
   ```
   好处：看到 diff 就知道**是哪张表的哪一版**产出的；坏处：**表改了没重跑生成器时，这个哈希会对不上** ——
   那正是我们要它暴露的事（§4.1 的守卫断言就查它）；
4. **行尾固定**（用 `\n`，与 `KeySpec.java` 的现状一致）**+ UTF-8 无 BOM**（`10-RULES.md:69`：
   `.java` 带 BOM 会让 javac 报 `非法字符: '\ufeff'`）。

### 4.4 生成代码能不能手改？幂等性？

- **不许手改**：头注释写死"请勿手工编辑"，并且**守卫断言会让手改当场红**
  （手改后的文件与"重新生成的结果"不一致 → 自测失败）。这条比注释管用。
- **幂等性**：**连跑两次必须零 diff**。达成条件（缺一条就不成立）：
  ① 行的顺序完全由表决定；② 没有时间戳；③ **行尾由生成器写死**（不能跟平台走 —— 仓库里 CRLF/LF 是混的，见 §1.5 **B7**）；
  ④ 数值字面量的格式化固定（`1.0` 不能这次写 `1.0`、下次写 `1.0D`）；
  ⑤ UTF-8 无 BOM 固定。
- **验收方式**：跑两次 `run-tablegen.ps1`，`git diff` 看有没有东西（⚠️ **我跑不了 `git`**，
  用户可以 `git diff --stat`；或者用文件 SHA-256 自己比 —— 附录 A 给了命令）。

### 4.5 JavaPoet 之类的代码生成库，值不值？

**不值，三条理由：**

1. **它又是一次"拉不拉得下来"的赌博**（§1.4）。为了省掉"拼 200 行字符串"，付出的是
   "离线/换机器/镜像挂了就编不出生成器"的风险 —— 而这个项目**要发 GitHub**。
2. **生成物只有 1~3 个结构完全固定的类**，没有泛型推导、没有 import 冲突、没有名字遮蔽问题。
   JavaPoet 的价值在"生成复杂多样的类型"，这里用不上。
3. **本项目已经在用更好的自检手段**：`javax.tools.JavaCompiler`（模组系统就靠它动态编译模组源码，
   `README.md:36` / `:407-413`）。**生成器写完文件后，直接用它把生成物编一遍** ——
   编不过就当场报错退出。**这比 JavaPoet 更能保证"生成的东西是合法的 Java"**，而且零依赖。

**自己拼字符串要注意的三件事**：① import 只写用到的（`check-sources.ps1:208-212` 会报 `[UNUSED]`）；
② 字符串字面量里的 `"` 与 `\` 要转义（说明列里有中文和括号，问题不大，但 `"` 要处理）；
③ 长行换行规则要固定（否则幂等性破功）。

### 4.6 生成物长什么样（`EntityKeySpecsGenerated.java` 的头部 + 两行样例）

```java
package cn.gfhnv.game.system.configLoadingSystem;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.system.ElementSort;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

// ============================================================================
// 本文件由 tables/entities.xlsx 生成，请勿手工编辑（改了下次生成会覆盖）。
// 源表 SHA-256（前 12 位）：3F9A1C7E02BD
// 重新生成：.\run-tablegen.ps1
//
// 约定（与手写版逐条一致，改生成器时必须同步）：
//   1. 行序 = 应用顺序：level 最先（会重算三围）、hp 最后（setHp 会被 getHpMax 夹）；
//   2. 五行名单只能来自 ElementSort，不许读 DataKeys 的非编译期常量（静态初始化环）；
//   3. 键名一个字都不能改（它同时是 /data 数据名与配置文件键名）。
// ============================================================================
public final class EntityKeySpecsGenerated {

    public static final List<KeySpec<LivingThing>> BEFORE_DERIVED = buildBeforeDerived();

    public static final List<KeySpec<LivingThing>> DERIVED = List.of(
            spec(DataKeys.Derived.HP_MAX, DataKeys.GROUP_DERIVED, KeySpec.Kind.LONG,
                    LivingThing::getHpMax, longs(LivingThing::setHpMax),
                    "生命上限（覆盖公式结果）"),
            /* … 共 4 行 … */);

    public static final List<KeySpec<LivingThing>> ALL = buildAll();

    private static List<KeySpec<LivingThing>> buildBeforeDerived() {
        List<KeySpec<LivingThing>> keys = new ArrayList<>();
        keys.add(spec(DataKeys.Base.NAME, DataKeys.GROUP_BASE, KeySpec.Kind.STRING,
                LivingThing::getName, strings(LivingThing::setName),
                "名称（影响 @e[name=…] 与选人列表）"));
        keys.add(spec(DataKeys.Base.LEVEL, DataKeys.GROUP_BASE, KeySpec.Kind.LONG,
                LivingThing::getLevel, longs(LivingThing::setLevel),
                "等级；会触发整组派生值重算"));
        /* … 共 27 行（含两个五行循环）… */
        return List.copyOf(keys);
    }

    /* spec / strings / longs / doubles / ints / bools 六个行构造器：由生成器输出固定模板 */
}
```

---

## 五、④ 和已有的东西怎么共存

### 5.1 逐件处置

| 既有物 | 处置 | 为什么 |
|---|---|---|
| **`KeySpec`**（276 行，一条键的声明 + `Kind` + `coerce`） | **留，一个字不动** | 生成物产出的就是 `List<KeySpec<T>>`。**生成路换掉的是"这张表怎么来的"，不是"表长什么样"** —— 这是本次设计能"零运行期风险"的根本原因 |
| **`SpecPatcher`**（176 行）/ **`SpecWriter`**（54 行） | **留，一个字不动** | 它们遍历的是 `List<KeySpec<T>>`，不关心它是手写的还是生成的 |
| **`EntityKeySpecs`**（360 行） | **改造**：表格本体换成 `EntityKeySpecsGenerated`，本类退化成约 30 行薄壳（`ALL` / `BEFORE_DERIVED` / `DERIVED` / `meta()` / `groups()` / `elements()` 转发） | 调用点（`DataKeys`、两个 patcher、`ConfigDefaultWriter`）**零改动**；回滚 = 恢复一个文件 |
| **`SkillKeySpecs`**（183 行）/ **`RuleKeySpecs`**（234 行） | 同上（阶段 2 才做） | 同上 |
| **`ReflectionConfigBridge`**（439 行）+ **`@NoConfig`**（51 行）+ 9 处注解 + 探针（132 行） | **留，而且升级用途**：从"并列的另一条路"改成**"表 ↔ 代码 的一致性审计器"**（§5.5） | 反射路的**失败模式与生成路正好相反**，是天然的互补守卫。删掉它等于把唯一的"表里漏了字段"探测器扔掉 |
| **`EntityDataPatcher` / `SkillDataPatcher` / `GameRulesPatcher`** | **留，一个字不动** | 它们是"定位 + 顺序 + 报错"，与"表怎么来的"无关 |
| **`DataKeys`**（912 行） | **留，一个字不改**（尤其：**不生成它**，见 §8 Q4） | 它是 `/data` 契约的常量家，134 个断言点 + 两个 patcher + 默认文件生成器都按名字引用它 |
| **`RuleDefaults`**（164 行） | **留常量，不删** | 被 `ActorLiXiaoYan` 的静态常量与 6 处 `{@value}` Javadoc 引用，而 `{@value}` 要求编译期常量（`CONFIG-LOADING-DECOUPLING-2026-10.md` §10.5 第 4 条） |
| **`ConfigLoader`**（769 行） | **完全不动** | 路径、时机、模组入口都与表无关 |
| **`mods/` 的 13 个 `.java`** | **完全不动** | `@ModConfig` / `ModDataAware` / `config/data/<modid>.json` 三条路一行不碰；`MODDING-GUIDE.md` §4.7（`:357-493`）与 §7.1（`:582-646`）的契约不变 |
| **`config/gameConfig/` 下的真 JSON**（⚠️ **实测现在只有 5 份**：`EntityData.json` / `SkillData.json` / `EntityData.default.json` / `GameRules.json` / `TagConfig.json` —— **`PropertyConfig.json` 已经不在了**，而 `README.md:86` 还在提它。文档里"6 份"是 2026-10-03 上午的口径，见 `CONFIG-LOADING-DECOUPLING-2026-10.md` §11.5 那 6 个 SHA-256） | **完全不动** | 表是**定义**不是**配置**：运行期还是读同样的文件、同样的键名、同样的层级（`README.md:78-86`） |

### 5.2 `/data` 键名契约（134 个断言点）怎么保？

**核心结论：生成路根本不经过 `/data` 的运行时路径，所以契约在结构上不可能破。** 但要有三道显式守卫：

| 守卫 | 内容 | 现在有没有 |
|---|---|---|
| ① **键名不变** | 表里的"键常量"列指向的还是 `DataKeys.Base.HP_GROW` 等**既有常量**，生成器**只引用、不定义** → 常量名改不了 | 天然成立（§8 Q4 推荐的选项） |
| ② **键集合不变** | 生成物展开后的键集合必须**逐个等于** `EntityKeySpecs` 今天的 31 个 | **已有**：`TestCommandSystem` 那条「`META` 与 `GROUPS` 一致」+「每个 `META` 键都能在 `LivingThing` 找到同类型字段」 |
| ③ **生成物与表一致** | 重新生成 → 与 `src` 里的生成物逐行比对 | **新增**（本文 §4.1 的 `GeneratedTable`） |

**阶段 1 的验收就靠这套**：如果 702 条断言**一条都不改**仍然全绿，那就等于
**"生成的表 ≡ 手写的表"被 702 条断言证明了**（这是本次设计里最强的等价性证据，比任何"我检查过了"都硬）。

### 5.3 模组侧会不会受影响？

**不会。** 逐条核对：

| 模组契约 | 位置 | 本次是否触碰 |
|---|---|---|
| `@ModConfig(id=…)` > `MOD_ID` | `ConfigLoader.java:429-462` | 不碰 |
| `ModDataAware.applyConfig` 调用时机 | `GameStartEventListener` 的模组循环 | 不碰 |
| `config/data/<modid>.json` 的 `common` 段（`entities` + `skills`） | `MODDING-GUIDE.md:407-410` | 不碰；`common.entities` 打的就是同一批实体补丁，**键名不变 → 模组配置继续有效** |
| 规则段改不了（`GameRules` 开局 `freeze()`） | `MODDING-GUIDE.md:481-492`；`ConfigLoader.java:372` | 不碰 |

**唯一值得顺带做的一件事（阶段 5 可选）**：`MODDING-GUIDE.md` §4.7 那张"能写哪些键"的表，
将来可以从表生成（今天它靠人肉维护，而它的权威来源其实是 `EntityKeySpecs`）。

### 5.4 硬约束（碰了就出事）—— 逐条钉死

> 这一节是**施工时必须贴在眼前的那张纸**。每一条都写清"违反了谁会发现"：
> **【javac】** = 编译不过（好，早发现）；**【自测】** = 断言红；**【人】** = 没人会发现，只能靠纪律。

| # | 硬约束 | 违反了谁会发现 |
|---|---|---|
| 1 | **`/data` 数据名一个都不许改**（`criticalRate` / `alive` / `effects` / `hpGrow` / `attackGrow` / `defenceGrow` / 五行 `…Resistance` / `…ManaGrow` …）；表里"键常量"列**只引用 `DataKeys` 既有常量，不新增、不改名** | **【自测】**（134 个断言点 + 「每个 `META` 键都能在 `LivingThing` 找到同类型字段」） |
| 2 | **`EntityData.json` / `SkillData.json` / `GameRules.json` 的键名与层级冻结**：`base` / `derived` 两种写法、裸键写法、`manaGrow` 块、`inventorySlots`**全部保留** | **【自测】**（`testConfigDefaultsAndPatch` 的契约断言）+ 判断 1（用户配置静默失效） |
| 3 | **生成物不许手工编辑**；**连跑两次必须零 diff**（行序固定、行尾固定 `\n`、UTF-8 无 BOM、字面量格式化固定、**绝不写时间戳**） | **【自测】**（"重新生成并比对"那条）—— 手改后当场红 |
| 4 | **生成物不许引用 `DataKeys` 的非编译期常量**（例如 `DataKeys.ELEMENTS`）。五行名单只能来自 `ElementSort.values()` | **【人】**（初看是 `ExceptionInInitializerError`，很难看出是静态初始化顺序 —— `CONFIG-LOADING-DECOUPLING-2026-10.md` §10.5 第 2 条踩过） |
| 5 | **表的行序 = 应用顺序**：`level` 必须最先（`Entity#setLevel` 会重算三围）、`hp` 必须最后（`setHp` 会被 `getHpMax()` 夹） | **【自测】**（本文新增一条"第一行/最后一行"断言；**javac 查不了顺序**） |
| 6 | **生成物必须过 `check-sources.ps1`**：无 BOM（`:90-95`）、无缺失 import（`:126`/`:152-205`）、**只写用得到的 import**（`:208-212` 报 `[UNUSED]`）、无小写开头的重复字段（`:217-234`） | **【AI 可跑】**（`CHECK OK` / 逐条 warning） |
| 7 | **生成物必须进 git**（否则克隆后 `src` 编不过 → 自测全灭、`gradle shadowJar` 失败） | **【javac】**（缺席就是 224 个文件全编不过） |
| 8 | **生成器零依赖**；**不许把 `tables/*.xlsx` 的读取塞进游戏运行期**（运行期还是那几份 JSON） | **【人】**（体积与启动路径都要重算；而且会造出一种新的"改了没反应"） |
| 9 | **生成器不许改 `config/` 下的任何文件**（除了阶段 3 那个"可选且需拍板"的默认文件顺序） | **【人】**（会悄悄回退用户改过的值 —— 这正是 D5/自愈那一步差点踩的坑，§10.5 第 5 条） |
| 10 | **13 参构造器签名与参数顺序、`setXxx` 名字、`@ModConfig` / `ModDataAware` / `config/data/<modid>.json` 契约**：一个都不许动 | **【javac】**（构造器）/ **【自测】**（`testModConfig` 等） |
| 11 | **写 `.java` 一律 UTF-8 无 BOM**；**不许**用 `Get-Content -Raw` + `WriteAllText` 这类会写 BOM 的路径去批量改源码（生成器写文件时必须显式指定无 BOM 的 UTF-8） | **【javac】**（`非法字符: '\ufeff'`，一个文件能刷出十几个错） |

### 5.5 ★ 反射路的新用途：当审计器（这是本文唯一一处"1+1>2"）

两条路的失败模式**正好相反**：

| | 默认状态 | 忘了做某件事的后果 | 谁会发现 |
|---|---|---|---|
| **表驱动 / 生成路** | **默认关闭**：字段不在表里 = 配不了 | 加了个 Java 字段、忘了加表行 → **配不了，而且没有任何人报错** | ❌ 没人（除非有人去比"字段清单 vs 表清单"） |
| **反射路** | **默认开放**：不写 `@NoConfig` = 能配 | 加了个字段、忘了挡 → **多出一个能配的键**（可能不该给玩家配） | ✅ 会现形（`CONFIG-LOADING-DECOUPLING-2026-10.md` §11.6 债 1 那张 24 个键的清单就是这么来的） |

**所以最终形态建议是：**

```
tables/*.xlsx ──生成──► *KeySpecsGenerated.java ──► SpecPatcher / SpecWriter（运行期，一个字不动）
        │
        └──► 自测里跑一次 ReflectionConfigBridge 的差集报告：
             · 只出现在表里的键（今天：inventorySlots / manaGrow 两个块）→ 白名单，逐个给理由
             · 只出现在反射面里的字段（今天：24 个）→ 逐个判断"该不该给人配"
             · 两边都有的 → 必须逐个同类型（这是最强的等价性检查）
```

**收益**：把"表里漏了一行"这个生成路**唯一的静默失败模式**变成一条**会红的断言**。
**成本**：反射路已经写完了（439 行 + 51 行注解），审计模式只需要在自测里调用它的 `blocked()` / `candidates()`，
**不加一行生产代码**。

---

## 六、⑤ 真实代价（诚实，不许含糊）

### 6.1 多一个构建步骤，对"用户自己编译"意味着什么

用户今天的心智模型（`README.md:322-329`）只有两步：
`gradle shadowJar` → `java -jar build/libs/FightGameReforged-1.2.1.jar`。
**加了代码生成之后会变成三步**，而且第三步**只在改表时需要**：

```
① gradle shadowJar          （不变）
② java -jar …               （不变）
③ .\run-tablegen.ps1        （只在"改了 tables/*.xlsx"时需要；不改表就完全不存在这一步）
```

**三个必须写清的后果：**

1. **"改了表没跑生成器"是一个静默失败**：生成物过期 → 游戏照跑、新键配不了、控制台不报错。
   **兜底只有一条**：跑自测（`GeneratedTable` 的比对断言会红）。**如果不跑自测，就没人知道。**
2. **"生成器坏了"是硬阻塞**：生成器编不过 → 改不了表 → 只能手工改生成物（而下一次生成会覆盖它）。
   风险等级取决于生成器有多复杂 —— 所以本文把它压到**约 600~700 行、零依赖、单一职责**。
3. **生成器自己不在 `check-sources.ps1` 的保护范围**（它扫的是 `src`，生成器若放 `src` 里就被扫到了 ——
   这也是 §4.1 选择"放 `src/debug_tools/tablegen`"的第三个理由）。

### 6.2 生成器本身也是代码，它坏了谁修？

| 问题 | 答案 |
|---|---|
| 谁会修 | **下一个 AI**（这份文档 + 生成物头注释 + 自测断言就是交接材料）。生成器是**纯函数**：`tables/*.xlsx` → Java 源码，没有副作用、没有运行时状态，**是这套子系统里最容易测的一块** |
| 怎么知道它坏了 | 三条：① `run-tablegen.ps1` 自己会在生成后用 `javax.tools.JavaCompiler` 编一遍生成物；② 自测的"重新生成并比对"断言；③ `check-sources.ps1`（生成物进 `src` 就被扫） |
| 最坏情况 | 本项目的自测基线是 **702 条**，加上这三条守卫后，生成器坏掉**不太可能悄悄溜过去** |
| 要写的代码量 | 实测口径：读表 **250~300 行** + 生成 **200 行** + 守卫与 main **220 行** ≈ **670~720 行**（含 Javadoc；本项目 Javadoc 占比很高，`LivingThing` 是 47%、`EntityDataPatcher` 约 46%）。**纯逻辑行大约只有一半** |

### 6.3 与反射路相比：多付出什么、多得到什么

**多付出（3 条）：**

1. **多一个手工构建步骤**（反射路是 0 步：改 Java 字段就完事）；
2. **多一份要维护的中间格式**（`.xlsx` + 8 列列定义 + 生成器；反射路没有中间格式）；
3. **多两个可能漂移的地方**：表 ↔ 生成物（靠守卫）、生成物 ↔ Java 成员（靠 javac）。
   反射路只有一处可能漂移：`@NoConfig` 该不该写（**而它漂了也不报错**）。

**多得到（3 条）：**

1. **"哪些键可配"这份知识有了可读的载体**：31 行 × 8 列的表格 vs 360 行 Java。
   对一个"AI 接力开发"的项目，这一条的实际价值比看起来大 —— 它把"要读 360 行才知道有什么键"
   变成"打开一张表"；
2. **表能长出别的东西**（`TABLES.md`、默认文件的键集合、`MODDING-GUIDE.md` 的键表），
   而反射路**长不出文档**（反射面是 53 个字段的原始清单，不是"给人看的键表"）；
3. **审计能力**：表 + 反射两条路并存时，可以做**三方比对**（表 / 反射面 / `/data` 实际 dump），
   这是单走任何一条都做不到的。反射路单独跑的时候，它**没有"应该有哪些键"的对照物**。

### 6.4 依赖的长期成本（逐项）

| 项 | 推荐路（零依赖自读 xlsx） | POI 路（若坚持） |
|---|---|---|
| **`shadowJar` 体积** | **+0 字节** | **+0 字节**（前提：POI 放进**不参与 `runtimeClasspath`** 的 configuration；不改 `build.gradle` 直接写 `implementation` 的话，按记忆是**十几 MB 量级**（**未实测**，见 §1.4 ④），相对现在 0.71 MB 就是 10 倍以上） |
| **首次拉取** | 不需要 | 需要（本轮实测我的会话**拉不到**；用户机器有缓存/镜像，大概率可以） |
| **版本升级** | 无 | 需要（POI 的安全公告、`xmlbeans`/`commons-*` 的传递依赖会跟着动） |
| **许可证** | 无第三方 | POI 按记忆是 **Apache-2.0**（宽松，与项目 MIT 兼容）—— ⚠️ **本轮无法联网核对，动手前请看 jar 内 `LICENSE`/`NOTICE`**。注意 POI 依赖里的 `xmlbeans` 也是 Apache-2.0 |
| **离线 / CI** | 完全离线可用 | 无网则生成器不可用（但**只是生成器**，游戏照样能编 —— 因为生成物进 git） |
| **对 `README.md:294`「唯一依赖 org.json」那句话的影响** | 无影响 | 要改那句话（它是给用户看的对外承诺） |
| **对 `10-RULES.md` §8「不要引入新的第三方库」的影响** | 无影响 | 用户本轮已解禁；但**建议把解禁范围写清楚**（构建期工具 vs 运行期依赖），否则下个 AI 会按旧的 §8 把 POI 删掉 |

---

## 七、⑥ 分阶段路线（每步自洽、可单独停、不留半成品）

**统一验收顺序**（沿用前两份文档的四格）：

```powershell
# ① 静态自查（快；本轮实测可跑）
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force; & .\check-sources.ps1
#    期望：Java files: 224+ / CHECK OK
# ② 全量编译 + 自测（权威；★ 本轮 AI 侧跑不了，见 §1.5 B1）
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force; & .\test-command-system.ps1
#    期望：通过 702 条（阶段 1 之后 704），失败 0 条
# ③ 生成器自检（新增）
& .\run-tablegen.ps1 -Check      # 重新生成并与 src 里的生成物比对，期望「表与生成物一致」
# ④ 用户进游戏：选人列表 → 打一局 → 看手感/数值
```

---

### 阶段 1（半天~1 天，**零行为变化**，★ 这一步就把"路走不走得通"验完）

> **为什么第一步是它**：它同时验证三个**未知数**，而这三个是整条路的全部风险：
> ① 零依赖能不能读 xlsx？② 生成出来的 Java 能不能与手写表**完全等价**？③ 自测那 702 条会不会全绿？
> **三个都过了，剩下的全是体力活；任何一个过不了，就地停下、损失半天。**

**做什么**

1. **把现有 31 个实体键抄成 `tables/entities.xlsx`**（8 列，见 §2.2 的列定义）。
   ⚠️ **抄写要逐字对照** `EntityKeySpecs.java:116-182` + `:48-60`，尤其：
   - 五行那两行是**循环**（`模式 = 五行循环`），不是 10 行；
   - `manaGrow` 块的"写成员"列**留空**（`write == null` ⇒ 走专门分支）；
   - `inventorySlots` 同理（`Kind.INVENTORY`）；
   - `derived` 段必须是 `hpMax` → `attack` → `defence` → **`hp` 最后**。
2. **写 `XlsxReader`**（零依赖，`java.util.zip` + `javax.xml.parsers`），坑见 §3.2 的 X1~X7。
   **遇到公式 / 合并单元格 / 跨表引用 / 日期 → 报错退出**（宁可"表不合法"也不要"静默读错"）。
3. **写 `TableCodeGen` + `TableGenMain`**：产出 `EntityKeySpecsGenerated.java`；
   生成后**用 `javax.tools.JavaCompiler` 编一遍**（编不过就退出、不写文件）。
4. **把 `EntityKeySpecs` 改成薄壳**（§2.2）。
5. **加 2 条自测断言**（在 `testConfigDefaultsAndPatch` 里）：
   - ★ **「重新读表 → 生成 → 与 `src` 里的生成物逐行一致」**（这条是 §4.3 的守卫）；
   - ★ **「生成物展开后的键名集合 == `DataKeys.META` 的键名集合」**（这条现在**必然已经成立**，
     所以它是"等价性"的显式化；将来表被改坏时它会红）。
6. **不改任何键名、不改任何 JSON、不改 `DataKeys`、不碰模组。**

**验收**

| 检查 | 期望 | 谁验 |
|---|---|---|
| `check-sources.ps1` | `Java files: 224+` / `CHECK OK` | AI 可跑（本轮已证明） |
| 编译 + 自测 | **702 → 704 条，失败 0 条**；**旧的 702 条一条都没改** | **用户**（AI 侧 `javac` 被拒） |
| ★ 等价性 | 旧 702 条全绿 = 「生成的表 ≡ 手写的表」 | 同上（这是本阶段的核心证据） |
| 幂等 | 连跑两次 `run-tablegen.ps1` → 生成物 SHA-256 不变 | AI 可跑 |
| 用户进游戏 | 选人列表数值不变、打一局手感不变、`EntityData.json` 一个字没动 | 用户 |

**能停在这里吗**：**能，而且这是最推荐的停止点之一**。停下 = 项目多了一张真实可编辑的表 +
一个零依赖生成器 + 一条守卫，**而运行期行为一个字节都没变**；回滚 = `git checkout` 两个文件。

---

### 阶段 2（半天，低风险）：技能表与规则表也进表

**做什么**：`tables/skills.xlsx`（8 + 2 行）、`tables/rules.xlsx`（29 行），
产出 `SkillKeySpecsGenerated` / `RuleKeySpecsGenerated`，两个手写文件退化成薄壳。

**这里的两个形状差异（阶段 1 学不到，必须单独设计）**：
- **技能表没有"分组"列**（`SkillKeySpecs.java:106-107` 的注释：技能键统一挂在 `skills` 段名下）；
- **规则表没有"读/写成员"两列**，换成"出厂值常量"列（`RuleKeySpecs.java:43` 的 `RuleKey` record 只有 `key/section/defaultValue/note`）。

**验收**：`CHECK OK` → 自测 **≥ 704，失败 0 条** → `-Check` 一致 → 用户进游戏打一局 +
打一次盗火行者二阶段（规则键里最容易踩的是 BOSS 旋钮）。

**能停在这里吗**：能。停下 = 三张表全在 Excel 里。

---

### 阶段 3（半天）：`DataKeys` ↔ 表 的一致性守卫 + `TABLES.md`（可选）

**做什么**：
1. 加一条断言：「表里的键常量 → 在 `DataKeys.META` / `groups()` / `isConfigurable` 里的口径全部一致」；
2. （可选）生成 `tables/TABLES.md`：三张表的键清单 + 说明，**给人和 AI 读**，并让 `.xlsx` 的二进制 diff
   有了一份文本对照；
3. （可选，**需要用户拍板**）生成 `EntityData.default.json` 的**键集合与顺序**——
   ⚠️ 这会**改变那份文件的字节**，而它是用户手上那几份真文件之一（`config/gameConfig/`），
   所以**默认不做**，列在这里只是备选。

**验收**：`CHECK OK` → 自测 **≥ 705** → 用户进游戏。

**能停在这里吗**：能，而且**如果只想要"表是唯一真相"，这一阶段完全可以不做**。

---

### 阶段 4（1 天，中风险）：把反射路改造成审计器

**做什么**：
1. 自测里加一条「表 ↔ `ReflectionConfigBridge.candidates()` ↔ `/data` 实际 dump」的**三方差集报告**；
2. 把今天两处例外**显式化**：`inventorySlots` 与 `manaGrow` 块（`DataKeys.NOT_IN_DATA`，反射面拿不到）
   → 写进一张带理由的白名单；
3. 把 §11.6 债 1 那 **24 个"只在反射面里"的字段**逐个定性（临时属性 / 运行时加成 / 死字段 / 该不该给人配），
   结论写进表的一个新列（`配置面：开/关/理由`）—— **这一步等于把"那 24 个"永久结案**。

**不做什么**：**不切换**（不把反射接进 `EntityDataPatcher`）。这一阶段只加断言、只加一列。

**验收**：`CHECK OK` → 自测 **≥ 710** → 用户进游戏。

**能停在这里吗**：能。停下 = "表里漏一行"这个唯一的静默失败模式**被断言堵住了**。

---

### 阶段 5（半天）：文档回写 + 收工 checklist

**做什么**（第 0 条闭环）：
1. 回写 `notes_for_llm/70-DATA.md` §5.10（"配置键 = 数据名"这条结论**没变**，但补一句"键表现在由 `tables/` 生成"）；
2. 回写 `notes_for_llm/90-STATE.md` §9.1（新基线数字、`src` 文件数、生成器在哪）；
3. 回写 `notes_for_llm/00-CATALOG.md` 的**收工 checklist**（加"改过 `tables/` 必须跑 `run-tablegen.ps1`"）+
   「按任务查表」加一行「改配置键 → 先读 `tables/*.xlsx`」；
4. 回写 `notes_for_llm/10-RULES.md` §8（把"不引入第三方库"改成
   **"运行期不许引；构建期工具可以，但要零依赖优先"**，否则下个 AI 会按旧规则把生成器删掉）；
5. 回写 `README.md`：`config/` 那一节补一句"表的定义在 `tables/`，改了要跑生成器"（`README.md:75-99` 附近）；
6. 本文件追加「§十 落地记录」（照 `CONFIG-LOADING-DECOUPLING-2026-10.md` §10 的体例：实测数字 + 踩坑 + 没做到的部分）。

**验收**：`CHECK OK` → 自测 → 用户照 `README.md` 走一遍"改表 → 生成 → 重启游戏 → 数值变了"。

---

## 八、⑦ 需要用户拍板的问题（4 条，每条带推荐）

| # | 问题 | 选项 | **推荐** |
|---|---|---|---|
| **Q1** | **这张"表"管的是什么范围？** | **(a) 只管"键的声明"**（实体 31 + 技能 10 + 规则 29 = 约 70 行：键名/类型/分组/读写成员/说明）；**(b) 也管"内容"**（怪物/技能/物品各一行，由表生成内容类）；**(c) 先 (a)，以后再说 (b)** | **(a)（= 也是 (c)）**。理由：本项目的"内容"是**行为类**（24 个技能各有自己的 `use()`、17 个效果各有语义），**没有可生成的数据类**（§2.1）；而"键的声明"已经有现成的 70 行手写表可以直接搬，**阶段 1 就能验完**。选 (b) 会立刻撞上"生成类要插进 31 个内容类的继承链"，那是另一个量级的工程 |
| **Q2** | **表用什么格式？** | **(a) `.xlsx` + 零依赖自读**（`java.util.zip`，约 250~300 行）；**(b) `.xlsx` + POI**（手感一样，多一个十几 MB 量级的构建期依赖）；**(c) CSV**（最简单，但中文 Windows 下 Excel 存 CSV 默认 GBK、前导零/长数字会被吃掉） | **(a)**。理由：本轮**实测拉不到依赖**（§1.4），而 `.gitignore:42` 忽略 `/lib/`；**(a) 与 (b) 的编辑手感完全一样**（同一个 xlsx 文件），差别只在那 250 行读取器由谁写。**将来真想换 POI，只换"读表"那一层，表与生成器不动** |
| **Q3** | **生成器的三件套：放哪 / 何时跑 / 生成物进不进 git？** | **(a) 放 `src/cn/gfhnv/debug_tools/tablegen/` + `run-tablegen.ps1` 手工跑 + 生成物进 git + 自测守卫**；**(b) 放 `tools/`（独立目录）+ 手工跑 + 生成物进 git，但**没有自测守卫**；**(c) 做成 `build.gradle` task 自动跑** | **(a)**。理由：只有 (a) 能让**自测重新读表**，从而堵住"改了表没跑生成器"这个**静默失败**（§4.1）；(b) 少了守卫；(c) 会让"表缺失/生成器有 bug"直接变成 `gradle shadowJar` 失败 —— 而那是用户唯一的构建命令，且本工程的 gradle 没有可用 wrapper（§1.5 B2） |
| **Q4** | **`DataKeys`（912 行 / 132 个 `public static`）要不要也由表生成？** | **(a) 不生成**：表里"键常量"列**引用**既有常量（`DataKeys.Base.HP_GROW`），`DataKeys.java` 一个字不改；**(b) 生成**：键名常量也由表产出，`DataKeys` 退化成薄壳 | **(a)**。理由：`DataKeys` 是 `/data` 契约的常量家，**134 个断言点 + 两个 patcher + 默认文件生成器**都按名字引用它；生成它 = 把 `KeySpec`/`SpecPatcher` 那一层也拖进来，**收益为 0、爆炸半径最大**（`CONFIG-LOADING-DECOUPLING-2026-10.md` §4.2 第 1 条把"数据名是对外 API"定成了硬约束） |

---

## 九、⑧ 不做的事（与 `notes_for_llm/10-RULES.md` §9.3 对齐）

| 不做 | 理由 |
|---|---|
| **改 `/data` 的任何键名**（含"顺手订正"） | `70-DATA.md:24-25`「数据名是对外 API……改名等于破坏 `/data` 脚本与将来的存档」；134 个断言点硬编码（§1.1）。**本次设计从头到尾一个键名都不动** |
| **改 `EntityData.json` / `SkillData.json` / `GameRules.json` 的 schema 或键名** | 用户手上已经有 6 份真文件（`README.md:78-86`）；改 schema 要写迁移层，而本项目**没有存档**这个动力 |
| **生成整个属性类（A 路）** | §2.1 的三个硬伤。**本轮不做**（不是永远不做 —— 但它需要先有"内容表"，而本项目没有可生成的内容） |
| **把 `.xlsx` 挪进 `config/`** | `config/` 是运行期游戏数据（`ConfigLoader.java:58-82`），把构建期源料塞进去 = 制造新的"改了没反应"路径（§3.3） |
| **生成 `DataKeys`** | 见 Q4 |
| **动 `LivingThing`（2391 行）** | `ENTITY-ATTRIBUTE-SPLIT-2026-10.md` 的阶段 2/3 保留；「拆结构的同时改数值 = 出问题分不清是哪边」（那份 §6「千万别」第 3 条） |
| **让游戏运行期读 `.xlsx`** | 表是**构建期**源料。运行期还是那 6 份 JSON —— 否则用户会得到一种全新的"改了没反应"（游戏要读 zip+XML），而且 `shadowJar` 的体积与启动路径都要重算 |
| **热重载（改表/改 JSON 立刻生效）** | `EXTERNAL-DATA-LOADING-2026-10.md` §9 已定：要处理"已复制出去的副本 + 已挂上的效果"，代价远超收益；本项目一直是"重启才生效"（`MODDING-GUIDE.md:56`） |
| **迁移 `TagConfig.json` 进新体系 / 删 `PropertyConfig.json`** | 同 `EXTERNAL-DATA-LOADING-2026-10.md` §3.2：现状能用、迁移只有审美收益；零读取的文件**不主动删用户文件** |
| **切换反射路（把 `ReflectionConfigBridge` 接进 `EntityDataPatcher`）** | `CONFIG-LOADING-DECOUPLING-2026-10.md` §11.6 债 1/2/3 没还；**而且表 + 反射并存比任何一条单走都强**（§5.5） |
| **删反射路 / 删 `@NoConfig`** | 它的失败模式与生成路互补，是"表里漏了一行"的唯一探测器（§5.5） |
| **引第三方库（POI / JavaPoet / Guava / Jackson）** | 用户本轮解禁了这条，但**推荐路一个都不需要**（§3.2 / §4.5）；`10-RULES.md` §8 那句话建议改成"运行期不许引、构建期优先零依赖"（阶段 5） |
| **弱点表 / 净化驱散 / 暴击系统接线 / `/data remove` / 实体存档** | 用户明确不做（`10-RULES.md` §9.3） |
| **改游戏输入方式 / 命令前缀 / 模组契约** | `PROJECT-ANALYSIS-2026-09.md` §2.1；`MODDING-GUIDE.md` §4.7 |
| **把实验性半成品放进 `src/`** | `PROJECT-ANALYSIS-2026-09.md` §7.3 第 4 条。阶段 1 的生成器必须**一次做完再进 `src/`** |

---

## 附录 A：本文所有数字与实测怎么复现

> ⚠️ **先看这条坑**（`ENTITY-ATTRIBUTE-SPLIT-2026-10.md` 附录 A 记着，本文照做）：
> 中文 Windows 上 **Windows PowerShell 5 的 `Get-Content` 默认按 GBK 解码**，
> 会把行尾中文字符的尾字节和换行符一起吞掉 → **行数与匹配行数都会少算**。
> 本文所有行号/计数一律用 `[System.IO.File]::ReadAllLines(...)` / `::ReadAllText(...)`。

```powershell
# ① 规模（别从仓库根递归扫描：out\artifacts 有 3529 层自我嵌套，会卡死）
(Get-ChildItem .\src  -Recurse -Filter *.java -File).Count    # 实测 224
(Get-ChildItem .\mods -Recurse -Filter *.java -File).Count    # 实测 13

# ② 静态自查（★ 本轮实测可跑：Java files: 224 / CHECK OK）
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force; & .\check-sources.ps1

# ③ configLoadingSystem 逐文件行数（§1.1）
Get-ChildItem .\src\cn\gfhnv\game\system\configLoadingSystem -File |
  ForEach-Object { "{0,-30} {1,6}" -f $_.Name, ([System.IO.File]::ReadAllLines($_.FullName)).Count }

# ④ 三张表各有多少行、展开后多少个键（§1.2）
$p = ".\src\cn\gfhnv\game\system\configLoadingSystem\EntityKeySpecs.java"
"实体表：keys.add(spec( × " + ([regex]::Matches([System.IO.File]::ReadAllText($p),'keys\.add\(spec\(')).Count   # 19
"实体表：五行循环 × "        + ([regex]::Matches([System.IO.File]::ReadAllText($p),'for \(String element')).Count  # 2（各产 5）
"技能表：spec( 行 × "        + ([regex]::Matches([System.IO.File]::ReadAllText(".\src\cn\gfhnv\game\system\configLoadingSystem\SkillKeySpecs.java"),'(?m)^\s+spec\(')).Count
"规则表：row( 行 × "         + ([regex]::Matches([System.IO.File]::ReadAllText(".\src\cn\gfhnv\game\system\configLoadingSystem\RuleKeySpecs.java"),'(?m)^\s+row\(')).Count       # 29
#   → 实体 19 显式行（含 2 个五行循环 → 展开 +10）+ 4 派生 = 31 个键（与 §11.6 的「31 个键」一致）

# ⑤ ★ 依赖与产物：本轮的实测命令与结果（§1.4 / §6.4）
#   (a) 出网探测（四条全部失败：DNS 通、TCP 通、数据不通）
Resolve-DnsName repo1.maven.org | Select-Object -First 3
Test-NetConnection repo1.maven.org -Port 443 | Select-Object TcpTestSucceeded, RemoteAddress
foreach ($u in @('https://repo1.maven.org/maven2/org/apache/poi/poi/5.2.5/poi-5.2.5.pom',
                 'https://maven.aliyun.com/repository/public/org/apache/poi/poi/5.2.5/poi-5.2.5.pom',
                 'https://www.baidu.com','https://example.com','https://github.com')) {
  try { (Invoke-WebRequest $u -Method Head -TimeoutSec 15 -UseBasicParsing).StatusCode }
  catch { "FAIL $u -> " + $_.Exception.Message } }
& curl.exe -sS -I --max-time 30 https://repo1.maven.org/maven2/org/apache/poi/poi/5.2.5/poi-5.2.5.pom
#   (b) 用户机器的依赖缓存（证明"能拉到"这件事历史上发生过）
Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1" -Directory | ForEach-Object { $_.Name }
Get-ChildItem "$env:USERPROFILE\.gradle\init.gradle"      # ★ 仓库配置有一半住在这里（不在仓库里）
#   (c) 现状 jar 多大、里面装了什么
Get-ChildItem .\build\libs -File | ForEach-Object { "{0}  {1:N0} bytes" -f $_.Name, $_.Length }   # 740,637
Add-Type -AssemblyName System.IO.Compression.FileSystem
$z = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path .\build\libs\FightGameReforged-1.2.1.jar))
$z.Entries | Where-Object { $_.FullName -like 'org/json/*' } | Measure-Object Length -Sum   # 31 项 / 147,789
$z.Dispose()
#   (d) 想量 POI 到底多重（★ 只有用户能做：需要能拉依赖的机器）—— 别改本工程的 build.gradle，
#       在一个空目录里建两行 build.gradle/settings.gradle 再跑（本文用的就是这个办法，只是没有网）：
#         plugins { id 'java' }
#         repositories { maven { url 'https://maven.aliyun.com/repository/public' }; mavenCentral() }
#         dependencies { implementation 'org.apache.poi:poi-ooxml:5.2.5' }
#       gradle -p <那个目录> dependencies --configuration runtimeClasspath
#     然后看 jar 各多大：Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\org.apache.poi" -Recurse -Filter *.jar

# ⑥ `/data` 契约断言点的当前口径（§1.1）
$tl = [System.IO.File]::ReadAllLines('.\src\cn\gfhnv\debug_tools\TestCommandSystem.java')
($tl[2426..2781] | Select-String -Pattern 'check\(|run\(|expectSyntaxError\(').Count    # 实测 134（旧文档口径 122）
"TestCommandSystem 行数: " + $tl.Count                                                  # 5436

# ⑦ 行尾与 BOM（§1.5 B7、§4.4）
foreach ($f in @('.\src\cn\gfhnv\game\GameMain.java','.\src\cn\gfhnv\game\system\configLoadingSystem\KeySpec.java')) {
  $b = [System.IO.File]::ReadAllBytes($f); $crlf=0; $lf=0
  for ($i=0;$i -lt $b.Length;$i++) { if ($b[$i] -eq 10) { if ($i -gt 0 -and $b[$i-1] -eq 13) { $crlf++ } else { $lf++ } } }
  "{0}  CRLF={1}  裸LF={2}" -f $f, $crlf, $lf }   # GameMain CRLF=369/0；KeySpec CRLF=0/276

# ⑧ 幂等性怎么验（不跑 git 也能验，§4.4）
Get-FileHash .\src\cn\gfhnv\game\system\configLoadingSystem\EntityKeySpecsGenerated.java -Algorithm SHA256
#   跑两次 run-tablegen.ps1，两次的哈希必须相同

# ⑨ 自测（★ 本轮 AI 侧跑不了：javac 被沙箱拒绝，见 §1.5 B1）
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force; & .\test-command-system.ps1
```

## 附录 B：本文引用的行号 / 数字清单（2026-10-03 用 `ReadAllLines` 核对）

| 位置 | 关键行 |
|---|---|
| `build.gradle`（54 行） | `:14-20` `sourceSets` `srcDirs = ['src']`；`:33-37` 三个仓库（aliyun public / aliyun google / mavenCentral）；`:40` `implementation 'org.json:json:20240303'`；`:43-49` `jar` 的 `Main-Class`；`:51-54` `shadowJar` |
| `.gitignore`（61 行） | `:34` `/build/`；`:42` **`/lib/`**；`:53` `/out/`；`:58` **`/notes_for_llm/`**；`:60` **`/gradle/wrapper/gradle-wrapper.properties`**。**没有** `tables/`、`config/`、`project_analyses/` |
| `gradle/wrapper/gradle-wrapper.properties`（6 行） | `:4` `distributionUrl=https://repo.huaweicloud.com/gradle/gradle-9.7.1-bin.zip`；`./gradle` 下**只有这一个文件** |
| `test-command-system.ps1`（80 行） | `:20-25` 要求 `javac`/`java` 在 PATH；`:27-28` **硬要求 `lib\json-20231013.jar`**；`:31-32` 先删 `out\cmdtest`；`:37` 收集 `src` 全部 `.java`；`:43-50` `javac` 调用（**本轮在这里被沙箱拒绝**）；`:71` 跑 `TestCommandSystem` |
| `check-sources.ps1` | `:41` 找 `src/`；`:90-95` BOM 检查；`:126`/`:152-205` import 检查；`:208-212` **UNUSED import**；`:217-234` **DUP-FIELD**（只匹配小写开头的字段名）；`:270` `CHECK OK` |
| `ConfigLoader.java`（769 行） | `:58` `TagConfig.json`；`:62` `EntityData.json`；`:66` `SkillData.json`；`:70` `EntityData.default.json`；`:74` `GameRules.json`；`:82` `./config/data`；`:87` `DEFAULT_TAGS_CONFIG`；`:372` `loadGameRules`；`:429` `resolveModConfigId`；`:482`/`:509` 两个 `loadModData` |
| `EntityKeySpecs.java`（360 行） | `:43` `BEFORE_DERIVED`；`:48-60` `DERIVED`（4 行，`hp` 在最后）；`:65` `ALL`；`:76` `meta()`；`:88` `groups()`；`:99` `elements()`；`:116-182` 表格本体（118/121/124/127/130/133/136/139/142/145 = 10 个标量；`:148-155` 抗性循环；`:156` `manaGrow` 块；`:159-165` 法力成长循环；`:166` `inventorySlots`；`:170-182` 5 个临时键） |
| `KeySpec.java`（276 行） | `:42` `record KeySpec<T>(name, group, kind, read, write, note)`；`:59-166` `enum Kind`（STRING/LONG/INT/BOOLEAN/DOUBLE/ELEMENT/INVENTORY/MANA_BLOCK/TAGS）；`:115-117` `inData()`；`:125-165` `coerce()` |
| `SpecPatcher.java`（176 行） | `:82-105` `patch()`（`write() == null` 就跳过 → 交给块分支）；`:117-133` `lookup()`（裸键 / `base` / `derived`）；`:146-162` `lookupManaGrowBlock()`（**绕开 `patch.has(key)`**，D9 的修法）；`:169-175` `namesOf()` |
| `SkillKeySpecs.java`（183 行） | `:60` `ALL`；`:72-82` `isKnown()`（含动态旋钮键）；`:88-95` `of()`；`:100-104` `buildAll()`；`:106-107` 注释「技能键没有分组概念」 |
| `RuleKeySpecs.java`（234 行） | `:43` `record RuleKey(key, section, defaultValue, note)`；`:48-51` `isInteger()`；`:64-…` `ALL = List.of(row(…))` **29 行**；`:164-169` `sections()` |
| `ConfigDefaultWriter.java`（903 行） | `:56`/`:60`/`:64`/`:68` 四个文件名常量；`:105` `entityDataJson()`；`:139` `referenceJson()`；`:241`/`:252`/`:263` 三个 `writeXxx`；`:288` `writeOrHeal`；`:320` `mergeMissing`；`:394` `writeReference`；`:405` `writeAll`；`:476` `collectBaseValues`；`:503` `collectDerivedValues`；`:537` `collectSkillValues`；`:582` `collectSkillPatches`；`:697` `descriptionForFile`；`:728-886` 内嵌 `Json` 排版器 |
| `LivingThing.java`（2391 行） | `:85`/`:89`/`:97`/`:136`/`:145`/`:152`/`:160` 七处 `@NoConfig`（反射实验加的） |
| `Thing.java` | `:40`/`:54` 两处 `@NoConfig` |
| `DataBridge.java`（924 行） | 反射实验后从 843 涨到 924（`CONFIG-LOADING-DECOUPLING-2026-10.md` §11.1 记的是 843 → 924） |
| `NoConfig.java`（51 行）/ `ReflectionConfigBridge.java`（439 行）/ `ConfigReflectionProbeEntity.java`（132 行） | 反射实验的三个新文件（§11.1 记的是 34 / 365 / 105 行，**行数已漂**，以本轮实测为准） |
| `TestCommandSystem.java`（5436 行） | `:86` `main`；`:2427` `testDataCommand`；`:2573` `testDataModify`；`:2626` `testDataFiltersAndStorage`（**三段合 134 个断言点**）；`:2783` `testConfigDefaultsAndPatch`；`:3435` `testSkillDataPatch`；`:3812` `testGameRules`；`:4088` `testModConfig`；`:4568` `testReflectionDrivenConfig`；`:4845` `testSummonCommand` |
| `config/gameConfig/`（5 个文件） | `EntityData.json` 4 行、`SkillData.json` 4 行、`EntityData.default.json` 5 行、`GameRules.json` 42 行、`TagConfig.json` 41 行 —— **五份 mtime 全是 `2026-10-03 14:44`**（用户重启过游戏，自愈逻辑跑了）。⚠️ `PropertyConfig.json` **已经不在这个目录里了**（`README.md:86` 还写着它在） |
| `MODDING-GUIDE.md`（717 行） | `:357-493` §4.7 模组配置；`:407-410` `common` 只支持 `entities` + `skills`；`:412-426` `@ModConfig` 优先级；`:428-437` 六条规则；`:451-462` 时机；`:464-479` 共享区；`:481-492` **规则段改不了**（硬限制）；`:582-646` §7.1 特殊机制模组 |
| `README.md`（614 行） | `:75` 「改完重启游戏生效」；`:78-86` 键长什么样；`:88-99` 四条规则 + 两条边界；`:294` 「唯一依赖 `org.json:json:20240303`」；`:322-329` 唯一的构建命令 `gradle shadowJar`；`:407-413` 模组用 `javax.tools.JavaCompiler` 动态编译 |
| `notes_for_llm/70-DATA.md`（155 行） | `:24-25` **数据名是对外 API**；`:42-47` 配置键 = 数据名 + 技能/规则是另一套键空间；`:49-58` 改名时 setter 名必须跟着改（**写回退化成裸写字段**那个最阴的回归）；`:63-80` `@DataFlatten` 组件 |
| `notes_for_llm/90-STATE.md` | `:17` **自测基线 `通过 702 条，失败 0 条`**；`:36-48` 解耦五步 677/0 与反射实验 702/0；`:57` `CHECK OK` / 224 文件 |
| `notes_for_llm/10-RULES.md`（146 行） | `:65-88` 编码硬约束（`.java` 无 BOM）；`:110-130` §8 协作偏好（`:124` 「不要引入新的第三方库」**已被用户本轮解禁**）；`:134-146` §9.3 故意不做的事 |
| `CONFIG-LOADING-DECOUPLING-2026-10.md`（1259 行） | `§十`（`:922-1014`）落地记录与 §10.3 的改动点数字；`§十一`（`:1029-1259`）反射实验：`:1100`/`:1171` **122 条键名契约断言**的出处、`:1183-1199` 债 1（24 个键）、`:1208-1217` 债 2（反射拿不到的六样）、`:1235-1240` 「混血」建议 |
| `ENTITY-ATTRIBUTE-SPLIT-2026-10.md`（538 行） | `:18` 59 字段全 `private` / 460 访问点 / 11 子类；`:87-95` 复制与重置的痛点；`:180` **122 个 `/data` 断言点**的原始口径；`:475` `Get-Content` 少算行数那条坑 |

## 附录 C：本文没做的事 / 不确定项

1. **没有改任何 `src/` / `mods/` / `config/` 下的文件**，只新建了本文。
2. **★ 没有编译、没有跑自测**：本会话里 **`javac` 被沙箱拒绝**
   （`test-command-system.ps1:49` → `Program 'javac.exe' failed to run: Access is denied`），
   `java.exe` 反而能跑（`openjdk 25.0.4.1`）。
   所以本文的「`通过 702 条，失败 0 条`」是**引用** `notes_for_llm/90-STATE.md:17`，
   **不是本轮复跑**。**`check-sources.ps1` 是本轮实跑的**（`Java files: 224` / `CHECK OK`）。
   阶段 1 的验收**必须在能跑 `javac` 的会话里做**（那次会话请先重跑一遍基线）。
3. **★ 一个副作用要声明**：我跑 `test-command-system.ps1` 验证基线时，脚本在 `:31-32`
   先 `Remove-Item -Recurse -Force out\cmdtest` 再 `New-Item`，随后在 `:49` 因 `javac` 被拒而中止
   → **`out\cmdtest` 现在是空目录**（0 文件）。那是**编译产物**（`.gitignore:53` 忽略 `/out/`），
   重跑一次脚本即可重建。`out\llmcheck`(315 文件) / `out\consoleprobe`(169) / `out\cn`(79) 都完好。
4. **★ 依赖可用性只验证了"我这边不行"，没验证"用户那边行"**：出网探测（5 个域名全失败）、
   `gradle` 起不来（native 库 + daemon 两条路）、POI/JavaPoet 的 jar **本机不存在**（gradle 缓存里没有）。
   「用户能拉到」的**唯一证据**是缓存里有 `org.json:json:20240303` 等历史下载物 —— 那是**过去**成功的证据，
   不是**现在**能拉到的证明。**需要用户自己跑一次 `gradle -q dependencies` 确认**（附录 A 第 ⑤ 条）。
5. **POI 的体积影响是粗估，不是实测**：我写的是"十几 MB 量级"，
   **来源是记忆，不是本机测量**（本机没有 POI jar）。APPENDIX A 第 ⑤(d) 条给了用户自己量的办法。
6. **没有核实"崩铁"或任何商业项目的实际管线**：本文只把「表定义 + 代码生成」当模式设计，
   **没有引用任何未经核实的外部实现细节**。
7. **没有实测过生成器原型**：§3.2 的 xlsx 读取坑（X1~X7）、§4.6 的生成物样例都是**设计**，没编译过。
   行数估算（读表 250~300 / 生成 200 / 守卫 220）是**估算**，误差估计 ±30%。
8. **没有评估"表用什么列名/列顺序最顺手"**：那要真的在 Excel 里写一遍才知道，属于手感，只有用户能判断。
9. **`@NoConfig` 那 9 处注解的措辞没有复核**（本文只引用它们的存在与行号）：
   如果阶段 4 要把反射路改成审计器，"每个注解的理由是否成立"要逐个读一遍。
10. **跑不了 `git`**（本会话与前几轮一样被拦）：所以「`lib/json-20231013.jar` 到底有没有被跟踪」
    我**无法确认**，只能从 `.gitignore:42` 推断。用户可以用 `git check-ignore -v lib/json-20231013.jar` 问一句。

---

*本文由 AI（DeepSeek）生成，2026-10-03。*
