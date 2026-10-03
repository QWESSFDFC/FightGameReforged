package cn.gfhnv.game.system.configLoadingSystem;

import cn.gfhnv.game.entity.Entity;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.mod.Mod;
import cn.gfhnv.game.mod.config.ModConfig;
import cn.gfhnv.game.mod.config.ModConfigDocument;
import cn.gfhnv.game.mod.config.ModDataAware;
import cn.gfhnv.game.system.logSystem.LogWriter;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;
import cn.gfhnv.game.world.World;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * 配置加载器,用于加载游戏本身的配置文件。
 * <p>
 * 目前支持三类配置,都放在 {@code ./config/gameConfig/} 下(相对工作目录,请从项目根目录启动):
 * <ul>
 *     <li>{@code TagConfig.json} —— 实体与物品的 AI 标签权重({@link #loadConfig()});</li>
 *     <li>{@code EntityData.json} —— 实体数值补丁({@link #loadEntityData()},
 *     语义与键名见 {@link DataKeys});</li>
 *     <li>{@code SkillData.json} —— 技能数值补丁({@link #loadSkillData()},
 *     键 = {@code <实体完整id>#<技能名>},见 {@link SkillDataPatcher})。</li>
 * </ul>
 * <p>
 * <b>四条纪律</b>:
 * <ol>
 *     <li>补丁只作用在 {@link World} 的注册表<b>模板</b>上,选人时 {@code copy()} 出来的副本天然带上补丁值;</li>
 *     <li><b>只覆盖文件里显式写出来的键</b> —— 没写的保持构造器算出来的值(不是清零、不是报错);</li>
 *     <li><b>一个坏键不许废掉整份配置</b>:逐项容错,末尾汇总打印"哪个文件、哪个键、哪一行、为什么";</li>
 *     <li>写盘一律显式 UTF-8 —— 默认字符集在中文 Windows 上是 GBK,和读的那一侧对不上。</li>
 * </ol>
 * <p>
 * 注意:模组的配置读的是游戏自己的 {@code config/data/}(<b>模组不能自带配置文件</b>),
 * 配置分组名由 {@link #resolveModConfigId(Mod)} 决定 ——
 * <b>{@link ModConfig} 注解 &gt; {@link Mod#getMOD_ID()}</b>,
 * 写法与时机见 {@code MODDING-GUIDE.md} 的「模组配置」一节。
 *
 * @author gfhnv
 */
public class ConfigLoader {
    /**
     * 标签(Tag)配置文件的路径。
     */
    private static final File TAG_CONFIG_FILE = new File("./config/gameConfig/TagConfig.json");
    /**
     * 实体数值配置文件的路径。
     */
    private static final File ENTITY_DATA_FILE = new File("./config/gameConfig/EntityData.json");
    /**
     * 技能数值配置文件的路径。
     */
    private static final File SKILL_DATA_FILE = new File("./config/gameConfig/SkillData.json");
    /**
     * "出厂值"参考副本的路径。游戏不读它,只在首次生成默认文件时写一份,给用户对照用。
     */
    private static final File ENTITY_DATA_REFERENCE_FILE = new File("./config/gameConfig/EntityData.default.json");
    /**
     * 游戏规则(魔法数字)配置文件的路径。
     */
    private static final File GAME_RULES_FILE = new File("./config/gameConfig/GameRules.json");
    /**
     * 模组配置目录的路径({@code config/data/<模组id>.json})。
     * <p>
     * <b>模组不能自带配置文件</b>({@code MODDING-GUIDE.md} 第 11 条):配置文件一律读游戏自己的
     * {@code config/},而且<b>只读不写</b> —— 游戏不会替模组生成默认配置
     * (模组目录不是游戏的地盘,凭空写一份出来只会让人以为"这是官方要求的格式")。
     */
    private static final File MOD_DATA_DIR = new File("./config/data");
    /**
     * 默认的标签配置内容,以 JSON 字符串形式保存。
     * 当配置文件不存在或不是文件时,将使用该默认配置。
     */
    private static final String DEFAULT_TAGS_CONFIG = "{\n" +
            "  \"game_official_content:insectBoss\": {\n" +
            "    \"attack\": 2,\n" +
            "    \"heal\": 1,\n" +
            "    \"defence\": 1\n" +
            "  },\n" +
            "  \"game_official_content:commonInsect\": {\n" +
            "    \"attack\": 1,\n" +
            "    \"heal\": 0,\n" +
            "    \"defence\": 0\n" +
            "  },\n" +
            "  \"game_official_content:iceInsect\": {\n" +
            "    \"attack\": 1,\n" +
            "    \"heal\": 0,\n" +
            "    \"defence\": 0,\n" +
            "    \"control_enemies\": 9\n" +
            "  },\n" +
            "  \"game_official_content:playerOne\":{\n" +
            "    \"attack\": 2,\n" +
            "    \"heal\": 1,\n" +
            "    \"defence\": 1,\n" +
            "    \"restoration_mana\": 1,\n" +
            "    \"damage_enhance\": 1\n" +
            "  },\n" +
            "  \"game_official_content:actorLiXiaoYan\":{\n" +
            "    \"attack\": 2,\n" +
            "    \"heal\": 1,\n" +
            "    \"defence\": 1,\n" +
            "    \"restoration_mana\": 1,\n" +
            "    \"damage_enhance\": 1\n" +
            "  },\n" +
            "  \"game_official_content:aNiceSword\": {\n" +
            "    \"damage_enhance\": 2\n" +
            "  },\"game_official_content:phainon\": {\n" +
            "  \"attack\": 5,\n" +
            "  \"heal\": 0,\n" +
            "  \"defence\": 0,\n" +
            "  \"restoration_mana\": 2,\n" +
            "  \"damage_enhance\": 3\n" +
            "}\n" +
            "}";
    /**
     * 标签映射表。
     * 键为实体或物品的 id,值为该 id 对应的标签类型与标签值的映射。
     */
    private static Map<String, Map<TagType, Tag>> tagsMap = new HashMap<>();
    /**
     * 实体数值配置是否已经加载过。
     * <p>
     * 补丁里有 {@code setLevel(...)} → {@code initialMana()}(会把整份法力列表重建),
     * 跑两次会把打到一半的法力重置,所以配置加载只做一次。
     */
    private static boolean entityDataLoaded = false;
    /**
     * 技能数值配置是否已经加载过(理由同上:补丁只该在每个模板上跑一次)。
     */
    private static boolean skillDataLoaded = false;
    /**
     * 游戏规则配置是否已经加载过。
     * <p>
     * 规则表是<b>静态</b>的:加载之后 {@code GameRules} 会冻结并拒绝再次写入
     * (否则"启动时读一次"的常量会在半路变值,在静态初始化顺序上埋雷)。
     */
    private static boolean gameRulesLoaded = false;

    /**
     * 静态初始化块:确保配置文件的父目录存在,以便后续写入配置文件。
     * <p>
     * {@code config/data} 也一起建出来:模组配置是"用户自己放进去"的文件,
     * 但把一个空目录先备好,用户才知道该往哪放。
     */
    static {
        if (!TAG_CONFIG_FILE.getParentFile().exists()) TAG_CONFIG_FILE.getParentFile().mkdirs();
        if (!MOD_DATA_DIR.exists()) MOD_DATA_DIR.mkdirs();
    }

    /**
     * @return 标签配置的<b>出厂默认值</b>（{@code TagConfig.json} 缺失时照着它重建）
     * <p>
     * 关于 D4（"两处写真值"）：这份字符串与 {@code config/gameConfig/TagConfig.json}
     * 的内容在出厂时是同一份，但<b>它们不是同一个真值</b> ——
     * 这里的定位是"出厂默认值"，而那个文件是<b>用户副本</b>（用户改它是允许的，
     * 文件存在时游戏一个字都不会去动它，见 {@link #setDefaultConfig()}）。
     * 所以自测不拿它们逐字节比对（那会在用户合法地改了自己的配置之后变成假失败），
     * 只钉住出厂默认值本身的形状（7 个 id / 26 个键值对）。
     * @return 默认标签配置的 JSON 文本
     */
    public static String tagsConfigTemplate() {
        return DEFAULT_TAGS_CONFIG;
    }

    /**
     * 加载配置文件中的标签配置。
     * <p>
     * 该方法会先清空已有的标签映射表,然后检查配置文件:
     * <ul>
     *     <li>若配置文件不存在,则调用 {@link #setDefaultConfig()} 写入并使用默认配置;</li>
     *     <li>若配置文件存在但不是文件,则删除该路径后写入并使用默认配置;</li>
     *     <li>否则解析配置文件,将配置中的标签应用到对应的实体和物品上。</li>
     * </ul>
     * <b>逐项容错</b>:某个键认不出来时只跳过那一项,并在控制台上写清
     * "哪个文件、哪个键、第几行、为什么" —— 一个拼错的键不再让整份配置作废。
     *
     * @throws IOException 当读取或写入配置文件失败时抛出
     */
    public static void loadConfig() throws IOException {
        tagsMap.clear();
        if (!TAG_CONFIG_FILE.exists()) {
            ConfigOutput.noteworthy("没有检测到配置文件：" + TAG_CONFIG_FILE.getPath()
                    + "，已按出厂默认值写出一份（下次启动就不会再报这一行）");
            setDefaultConfig();
            return;
        }
        if (!TAG_CONFIG_FILE.isFile()) {
            TAG_CONFIG_FILE.delete();
            setDefaultConfig();
            return;
        }
        String text = Files.readString(TAG_CONFIG_FILE.toPath(), StandardCharsets.UTF_8);
        JSONObject jsonObject = parseObject(text, TAG_CONFIG_FILE);
        if (jsonObject == null) {
            return;
        }
        List<String> problems = new ArrayList<>();
        for (String id : jsonObject.keySet()) {
            JSONObject inner = jsonObject.optJSONObject(id);
            if (inner == null) {
                problems.add(describe(TAG_CONFIG_FILE, text, id, "它下面的内容不是对象(该是 {标签:权重})"));
                continue;
            }
            Map<TagType, Tag> map = new EnumMap<>(TagType.class);
            for (String tagType : inner.keySet()) {
                TagType type = parseTagType(tagType);
                if (type == null) {
                    problems.add(describe(TAG_CONFIG_FILE, text, tagType,
                            "没有这个标签类型(可用:" + tagNames() + ")"));
                    continue;
                }
                Object raw = inner.opt(tagType);
                if (!(raw instanceof Number number)) {
                    problems.add(describe(TAG_CONFIG_FILE, text, tagType,
                            "权重要数字,实际是 " + (raw == null ? "null" : raw.getClass().getSimpleName())));
                    continue;
                }
                map.put(type, new Tag(number.doubleValue()));
            }
            if (!map.isEmpty()) {
                tagsMap.put(id, map);
            }
        }
        for (String problem : problems) {
            ConfigOutput.problem("[配置跳过] " + problem);
            LogWriter.writeLog("[配置跳过] " + problem);
        }
        if (World.getEntityList().isEmpty()) {
            ConfigOutput.problem("[配置错误] " + TAG_CONFIG_FILE.getName()
                    + "：注册表里一个实体都没有，标签配置没有落点（游戏无法继续）");
            return;
        }//没有实体,游戏无法进行
        int entityHits = 0;
        for (Entity entity : World.getEntityList()) {
            if (tagsMap.containsKey(entity.getId())) {
                entity.setTags(tagsMap.get(entity.getId()));
                entityHits++;
                ConfigOutput.info("实体" + entity.getId() + "加载配置成功");
            }
        }
        if (World.getItemList().isEmpty()) {
            ConfigOutput.info("没有物品");
            return;
        }
        //有没有物品无所谓
        int itemHits = 0;
        for (Item item : World.getItemList()) {
            if (tagsMap.containsKey(item.getId())) {
                item.setTags(tagsMap.get(item.getId()));
                itemHits++;
                ConfigOutput.info("物品" + item.getId() + "加载配置成功.");
            }
        }
        // "命中 N 个实体、M 个物品" 是"一切正常"的播报 → 默认静默，只留一笔账，
        // 让最后那行总量汇总里出现它的名字（开 verbose 才逐条打）
        ConfigOutput.tally(TAG_CONFIG_FILE.getName(), entityHits + itemHits, problems.size(), 0,
                "命中 " + entityHits + " 个实体、" + itemHits + " 个物品");
    }

    /**
     * 加载实体数值补丁({@code config/gameConfig/EntityData.json})。
     * <p>
     * 语义(<b>补丁,不是覆盖</b>):配置文件里<b>显式写出来</b>的键才覆盖,
     * 没写的保持构造器算出来的值。文件不存在时写出一份"当前全量默认值"再读回来
     * (照 {@link #setDefaultConfig()} 的做法,<b>只在缺失时写</b>,绝不覆盖用户改过的文件)。
     * <p>
     * 补丁打在 {@link World} 注册表的<b>模板</b>上,位置就是 {@code GameMain#gameInitialize} 里
     * 紧挨着 {@link #loadConfig()} 的那一行 —— 那时官方内容与模组内容都已经注册完了。
     * <p>
     * <p>
     * <b>空文件也会被填满</b>(见 {@link #healDefaultConfigFiles}):文件在"注册表还空着"的那一刻
     * 被生成过(老版本的行为),留下 {@code {"version":1,"entities":{}}} 这种空壳 ——
     * 那种文件"存在但是空的",只判断"存在"就永远补不上。这里每轮启动都问一次
     * {@code ConfigDefaultWriter} "缺不缺键",缺就补齐;用户已经写下的值一个字都不动
     * (补的是"用户没写的键",不是"覆盖用户写的值")。
     * <p>
     * <b>本方法不抛异常</b>:文件坏了、键写错了都只打印并跳过,不让游戏起不来。
     *
     * @return 这一次是否真的应用了补丁({@code false} = 已经加载过,或者文件里没有可用的项)
     */
    public static boolean loadEntityData() {
        if (entityDataLoaded) {
            return false;
        }
        entityDataLoaded = true;
        try {
            if (!ENTITY_DATA_FILE.exists() || !ENTITY_DATA_FILE.isFile()) {
                writeDefaultConfigFiles();
            } else {
                healDefaultConfigFiles();
            }
            String text = Files.readString(ENTITY_DATA_FILE.toPath(), StandardCharsets.UTF_8);
            JSONObject root = parseObject(text, ENTITY_DATA_FILE);
            if (root == null) {
                return false;
            }
            warnAboutVersion(ENTITY_DATA_FILE, root);
            EntityDataPatcher.Report report =
                    EntityDataPatcher.apply(root, ENTITY_DATA_FILE.getName());
            report.print();
            for (String error : report.errors()) {
                LogWriter.writeLog("[配置错误] " + ENTITY_DATA_FILE.getName() + ":" + error);
            }
            return !report.appliedEntries().isEmpty();
        } catch (IOException | RuntimeException e) {
            String message = "读不了 " + ENTITY_DATA_FILE.getPath() + ":" + e.getMessage()
                    + "(配置没有生效,但游戏照常运行)";
            ConfigOutput.problem("[配置错误] " + message);
            LogWriter.writeLog("[配置错误] " + message);
            return false;
        }
    }

    /**
     * 加载技能数值补丁({@code config/gameConfig/SkillData.json})。
     * <p>
     * 语义与 {@link #loadEntityData()} 完全一致(<b>补丁,不是覆盖</b>):文件里显式写出来的键才覆盖,
     * 没写的保持构造器算出来的值;文件不存在时先写一份"当前全量默认值"再读回来
     * (只在缺失时写,绝不覆盖用户改过的文件)。
     * <p>
     * 键的形态是 {@code <实体完整id>#<技能名>} —— {@code Skill} 基类没有 id 字段,
     * 技能名就是子类构造器里 {@code super("…", …)} 的第一个参数。
     * <p>
     * <b>本方法不抛异常</b>:文件坏了、键写错了都只打印并跳过,不让游戏起不来。
     * <p>
     * {@code SkillData.json} 同样会被自愈填满,理由与 {@link #loadEntityData()} 一模一样。
     *
     * @return 这一次是否真的应用了补丁({@code false} = 已经加载过,或者文件里没有可用的项)
     */
    public static boolean loadSkillData() {
        if (skillDataLoaded) {
            return false;
        }
        skillDataLoaded = true;
        try {
            if (!SKILL_DATA_FILE.exists() || !SKILL_DATA_FILE.isFile()) {
                writeDefaultConfigFiles();
            } else {
                healDefaultConfigFiles();
            }
            String text = Files.readString(SKILL_DATA_FILE.toPath(), StandardCharsets.UTF_8);
            JSONObject root = parseObject(text, SKILL_DATA_FILE);
            if (root == null) {
                return false;
            }
            warnAboutVersion(SKILL_DATA_FILE, root);
            SkillDataPatcher.Report report = SkillDataPatcher.apply(root, SKILL_DATA_FILE.getName());
            report.print();
            for (String error : report.errors()) {
                LogWriter.writeLog("[配置错误] " + SKILL_DATA_FILE.getName() + ":" + error);
            }
            return !report.appliedEntries().isEmpty();
        } catch (IOException | RuntimeException e) {
            String message = "读不了 " + SKILL_DATA_FILE.getPath() + ":" + e.getMessage()
                    + "(配置没有生效,但游戏照常运行)";
            ConfigOutput.problem("[配置错误] " + message);
            LogWriter.writeLog("[配置错误] " + message);
            return false;
        }
    }

    /**
     * 加载游戏规则({@code config/gameConfig/GameRules.json})。
     * <p>
     * 与实体/技能补丁不同的是:这里读出来的值进的是 {@link GameRules} 那张<b>静态规则表</b>
     * (公式常量没有"对象"可以打补丁),而很多使用点是
     * {@code private static final X = GameRules.getXxx(...)} —— 也就是"那个类第一次被加载时读一次"。
     * 所以本方法<b>必须在任何实体被造出来之前</b>跑完,并在结束时调用 {@link GameRules#freeze()}
     * 把表冻结(冻结之后不再接受写入,避免"类加载顺序决定行为"这种无法复现的 bug)。
     * <p>
     * <b>本方法不抛异常</b>:文件坏了、键写错了都只打印并跳过,不让游戏起不来。
     *
     * @return 这一次是否真的应用了规则({@code false} = 已经加载过,或者文件里没有可用的项)
     */
    public static boolean loadGameRules() {
        if (gameRulesLoaded) {
            return false;
        }
        gameRulesLoaded = true;
        // 这一轮启动的补丁记账从这里开始（它是第一份被加载的配置）—— 明细在各自 print() 里累积，
        // 最后由 GameMain 在全部加载完之后打一行总量（见 ConfigOutput#printPatchSummary）
        ConfigOutput.resetTallies();
        GameRules.beginLoad();
        try {
            if (!GAME_RULES_FILE.exists() || !GAME_RULES_FILE.isFile()) {
                writeDefaultConfigFiles();
            }
            String text = Files.readString(GAME_RULES_FILE.toPath(), StandardCharsets.UTF_8);
            JSONObject root = parseObject(text, GAME_RULES_FILE);
            if (root == null) {
                return false;
            }
            warnAboutVersion(GAME_RULES_FILE, root);
            GameRulesPatcher.Report report = GameRulesPatcher.apply(root, GAME_RULES_FILE.getName());
            GameRulesPatcher.print(report);
            return !report.appliedEntries().isEmpty();
        } catch (IOException | RuntimeException e) {
            String message = "读不了 " + GAME_RULES_FILE.getPath() + ":" + e.getMessage()
                    + "(配置没有生效,但游戏照常运行)";
            ConfigOutput.problem("[配置错误] " + message);
            LogWriter.writeLog("[配置错误] " + message);
            return false;
        } finally {
            // 无论成败都冻结:规则表一旦开始被 static 常量读取,就不许再变。
            GameRules.freeze();
        }
    }

    /**
     * 解析一个模组的<b>配置分组名</b>(也就是 {@code config/data/<配置分组名>.json} 的文件名,
     * 以及这份文件里"属于它自己那一段"的键名)。
     * <p>
     * <b>优先级:注解 &gt; {@code MOD_ID}</b> —— 全项目只有这一个口径,两个
     * {@code loadModData} 入口都调它(不许各写一份):
     * <ol>
     *     <li>模组主类上有 {@link ModConfig} 注解,且 {@link ModConfig#id()} 去掉首尾空白后非空
     *     → 用<b>注解里的值</b>(去掉首尾空白):<b>即使它与 {@code MOD_ID} 不一样,
     *     配置文件名也跟着注解走</b> —— 写了注解就是"我明确声明了配置分组";</li>
     *     <li>否则退回 {@link Mod#getMOD_ID()}:现有模组都没写这个注解,
     *     行为与"注解被反射读取"之前逐位相同;</li>
     *     <li>两者都拿不到(没注解、{@code MOD_ID} 也是 {@code null} 或空串)→ 返回 {@code null},
     *     调用方<b>跳过这个模组的配置</b>(打印一行提示,不抛异常、不崩)。</li>
     * </ol>
     * <p>
     * <b>{@link ModConfig} 是真的被反射读的</b>({@code mod.getClass().getAnnotation(ModConfig.class)}),
     * 不是文档性的注解 —— 它的 {@code @Retention(RUNTIME)} 就是为此而留。
     * 也因为它<b>没有</b> {@code @Inherited},注解要写在模组主类<b>自己</b>头上,
     * 写在父类上这里读不到。
     * <p>
     * 本方法只读不写、无副作用,重复调用结果相同。
     *
     * @param mod 模组；{@code null} 直接返回 {@code null}
     * @return 配置分组名；两者都拿不到时返回 {@code null}
     */
    public static String resolveModConfigId(Mod mod) {
        if (mod == null) {
            return null;
        }
        ModConfig annotation = mod.getClass().getAnnotation(ModConfig.class);
        if (annotation != null) {
            String annotated = annotation.id();
            if (annotated != null && !annotated.isBlank()) {
                return annotated.trim();
            }
        }
        String modId = mod.getMOD_ID();
        return modId == null || modId.isEmpty() ? null : modId;
    }

    /**
     * 打印"这个模组既没有 {@link ModConfig} 注解、也没有 {@code MOD_ID}"的那一行提示
     * (两个 {@code loadModData} 入口共用一句话,别写两遍)。
     *
     * @author AI（DeepSeek）生成
     */
    private static void printNoConfigGroup() {
        ConfigOutput.noteworthy("[配置] 有个模组既没有 @ModConfig 注解、也没有 MOD_ID,读不了它的配置"
                + "(配置文件名就是配置分组名,见 MODDING-GUIDE.md 的「模组配置」一节)");
    }

    /**
     * 加载模组配置({@code config/data/<配置分组名>.json},分组名由
     * {@link #resolveModConfigId(Mod)} 决定:<b>{@link ModConfig} 注解 &gt;
     * {@link Mod#getMOD_ID()}</b>)。
     * <p>
     * <b>时机</b>:由 {@code GameStartEventListener} 在调用模组的 {@code invokeWhenLoaded()}
     * <b>之前</b>逐个模组调用 —— 模组要在"注册内容"之前就能读到配置。
     * <p>
     * 一份模组配置做两件事:
     * <ol>
     *     <li>{@code common} 段当"更高优先级的补丁"打进官方与模组模板
     *     (与 {@link #loadEntityData()} 同一套口径,只是晚一步应用,所以后者胜);</li>
     *     <li>{@code <配置分组名>} 段包成 {@link ModConfigDocument} 交给实现了
     *     {@link ModDataAware} 的模组。</li>
     * </ol>
     * <b>没有配置文件时照样调用 {@code applyConfig}</b>(文档是空的):模组不用写"有没有文件"的分支,
     * 也不会因为缺配置就整个内容消失。
     * <p>
     * <b>每个模组各自 try/catch (Throwable)</b>:一个模组的配置写错了,后面模组照常加载 ——
     * 这是刻意堵住的一条新风险路径(模组加载链本来就只 catch {@code Exception})。
     * <p>
     * <b>本方法不抛异常。</b>文件不存在时，若模组声明了
     * {@link ModDataAware#defaultConfig()}，会先按它生成一份（见 {@link #writeDefaultModConfig}）；
     * 除此之外不往 {@code config/data/} 写任何文件。
     *
     * @param mod 要加载配置的模组；{@code null} 直接忽略；
     *            注解与 {@code MOD_ID} 都没有时这个模组拿不到配置(打印一行提示后返回 {@code false})
     * @return 这个模组有没有配置文件
     */
    public static boolean loadModData(Mod mod) {
        if (mod == null) {
            return false;
        }
        String configId = resolveModConfigId(mod);
        if (configId == null) {
            printNoConfigGroup();
            return false;
        }
        return loadModData(mod, new File(MOD_DATA_DIR, configId + ".json"));
    }

    /**
     * 模组的配置文件不存在时，问模组要一份默认内容写盘（见 {@link ModDataAware#defaultConfig()}）。
     * <p>
     * 与 {@code config/gameConfig/*.json} 的自愈<b>同一套口径</b>：只在文件不存在时写，
     * 已存在的文件一个字节都不动；目录缺失时先建；写失败只报一行，不影响游戏与其它模组。
     * <p>
     * 之所以放在这里而不是"让模组自己写"：文件的生命周期归加载器管，
     * 模组只负责"声明默认值"—— 否则每个模组都要自己拼路径、自己建目录、自己处理编码。
     *
     * @param mod      模组（不实现 {@link ModDataAware} 或返回 {@code null} 时什么都不做）
     * @param file     它的配置文件
     * @param configId 配置分组名（写进 {@code {"<分组名>":{...}}} 那一层）
     * @return 写完能不能读到文件
     */
    private static boolean writeDefaultModConfig(Mod mod, File file, String configId) {
        if (!(mod instanceof ModDataAware aware)) {
            return false;
        }
        java.util.Map<String, Object> defaults;
        try {
            defaults = aware.defaultConfig();
        } catch (Throwable t) {
            // 模组自己抛的异常只中断它自己的默认配置（与 applyConfig 的口径一致）
            ConfigOutput.problem("[配置错误] " + configId + " 生成默认配置时抛了 " + t
                    + "（这个模组照常加载，只是没有默认配置文件）");
            return false;
        }
        if (defaults == null) {
            return false;
        }
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            JSONObject own = new JSONObject(defaults);
            JSONObject root = new JSONObject();
            root.put(DataKeys.VERSION, 1);
            root.put(configId, own);
            Files.writeString(file.toPath(), root.toString(2), StandardCharsets.UTF_8);
            ConfigOutput.noteworthy("[配置] " + file.getPath()
                    + " 不存在，已按模组声明的默认值生成一份（改它即可调参，删掉会再生成）");
            return file.isFile();
        } catch (IOException | RuntimeException e) {
            ConfigOutput.problem("[配置错误] 写不了 " + file.getPath() + ":" + e.getMessage()
                    + "(这个模组照常用内置默认值，不影响游戏)");
            return false;
        }
    }

    /**
     * 加载一个模组的配置,从<b>显式指定的文件</b>读。
     * <p>
     * 存在的理由是"让磁盘这条路径可被自测覆盖":{@link #loadModData(Mod)} 里的
     * {@code ./config/data/...} 是相对<b>进程工作目录</b>的,而工作目录在 JVM 启动之后改不了
     * (改 {@code user.dir} 属性没有用),所以自测没法让它读到临时目录里去。
     * <p>
     * <b>分组名同样由 {@link #resolveModConfigId(Mod)} 决定</b>(注解 &gt; {@code MOD_ID},
     * 与 {@link #loadModData(Mod)} 同一个口径):分组名拿不到时跳过这个模组(返回 {@code false},
     * 不抛异常,也不读文件)。
     *
     * @param mod  模组
     * @param file 配置文件(通常形如 {@code ./config/data/<配置分组名>.json})
     * @return 这个文件存不存在
     */
    public static boolean loadModData(Mod mod, File file) {
        String configId = resolveModConfigId(mod);
        if (configId == null) {
            printNoConfigGroup();
            return false;
        }
        JSONObject root = new JSONObject();
        boolean exists = file.isFile();
        if (!exists) {
            // 模组可以自带一份"默认配置"（可选，见 ModDataAware#defaultConfig）。
            // 与 config/gameConfig/*.json 的自愈同一套口径：**只在文件不存在时写**，已有文件一字不动。
            exists = writeDefaultModConfig(mod, file, configId);
        }
        if (exists) {
            try {
                String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                JSONObject parsed = parseObject(text, file);
                if (parsed != null) {
                    root = parsed;
                }
            } catch (IOException | RuntimeException e) {
                String message = "读不了 " + file.getPath() + ":" + e.getMessage()
                        + "(这个模组的配置没有生效,但游戏与其它模组照常运行)";
                ConfigOutput.problem("[配置错误] " + message);
                LogWriter.writeLog("[配置错误] " + message);
            }
        }
        // ① 共享区:当"更高优先级的补丁"打给官方/模组内容(这一层在 EntityData 之后应用,所以后者胜)
        JSONObject common = root.optJSONObject(ModConfigDocument.COMMON);
        if (common != null) {
            // 只写 entities 的老写法照旧（缺层时的报错也照旧）；只写 skills 的新写法不该被误报缺 entities
            boolean hasEntities = common.has(DataKeys.ENTITIES);
            boolean hasSkills = common.has(DataKeys.SKILLS);
            if (hasEntities || !hasSkills) {
                EntityDataPatcher.Report report =
                        EntityDataPatcher.apply(common, configId + ".json 的 common 段");
                report.print();
            }
            if (hasSkills) {
                SkillDataPatcher.Report skillReport =
                        SkillDataPatcher.apply(common, configId + ".json 的 common 段");
                skillReport.print();
            }
            reportRuleSectionsInCommon(common, configId);
        }
        // ② 自己的分组:交给模组(分组名 = 配置分组名,不一定是 MOD_ID:注解优先,见 resolveModConfigId)
        JSONObject own = root.optJSONObject(configId);
        ModConfigDocument document = new ModConfigDocument(configId,
                own == null ? new JSONObject() : own, common);
        if (mod instanceof ModDataAware aware) {
            try {
                aware.applyConfig(document);
            } catch (Throwable t) {
                // 必须 catch Throwable:配置是用户写的,不能让"一个模组的配置写错"
                // 变成"后面模组全不加载"(那正是 ModLoader 那批经典缺陷的形状)。
                String message = "模组 " + configId + " 读配置时抛了异常(只跳过这个模组的配置):"
                        + t.getClass().getName() + ": " + t.getMessage();
                ConfigOutput.problem("[配置错误] " + message);
                LogWriter.writeLog("[配置错误] " + message);
            }
        }
        ConfigOutput.info("[配置] " + file.getName() + (exists ? ":已读取" : ":没有这个文件(用默认值)")
                + (mod instanceof ModDataAware ? ",已交给模组" : ",模组没有实现 ModDataAware(跳过)"));
        return exists;
    }

    /**
     * 扫一遍 {@code common} 段里有没有"规则段"，有就<b>显式说做不到</b>。
     * <p>
     * <b>为什么不是静默跳过，也不是假装支持</b>：规则与实体/技能不一样 ——
     * {@code GameRules} 在游戏启动时（造任何实体之前，{@code GameMain.gameInitialize()} 的第一步）
     * 就读完并 {@code freeze()} 了，而模组配置是在那之后的 {@code GameStartEvent} 里加载的。
     * 打开写入是不可能的（很多使用点是 {@code static final X = GameRules.getDouble(...)}，
     * 表要是能中途变值，行为就取决于类加载顺序）。
     * 用户按文档在 {@code common} 里写 {@code "flameReaver": {...}} 却什么都不发生时，
     * 至少要有这一行告诉他该去哪儿改。
     *
     * @param common   共享段
     * @param configId 配置分组名（报错用）
     */
    private static void reportRuleSectionsInCommon(JSONObject common, String configId) {
        for (String section : RuleKeySpecs.sections()) {
            if (!common.has(section)) {
                continue;
            }
            String message = configId + ".json 的 common 段里有「" + section + "」，但这一段改不了："
                    + "GameRules 在开局（造任何实体之前）就已经读进内存并冻结了，"
                    + "而模组配置是在那之后才加载的。要改规则请直接改 "
                    + "config/gameConfig/GameRules.json（它才是在开局前读的那一份）。";
            ConfigOutput.noteworthy("[配置] " + message);
            LogWriter.writeLog("[配置] " + message);
        }
    }

    /**
     * 加载<b>所有</b>模组的配置({@code config/data/<配置分组名>.json},
     * 分组名见 {@link #resolveModConfigId(Mod)}:注解 &gt; {@code MOD_ID})。
     * <p>
     * 按 {@link World#getModList()} 的顺序逐个调用 {@link #loadModData(Mod)}。
     * 单个模组出错不会影响其它模组(见那个方法里的 try/catch)。
     *
     * @return 有几个模组真的有配置文件
     */
    public static int loadAllModData() {
        int hits = 0;
        for (Mod mod : World.getModList()) {
            if (loadModData(mod)) {
                hits++;
            }
        }
        if (!World.getModList().isEmpty()) {
            ConfigOutput.info("[配置] 模组配置:" + hits + " / " + World.getModList().size()
                    + " 个模组有 config/data/ 下的配置文件");
        }
        return hits;
    }

    /**
     * 写一份"当前全量默认值"到 {@code config/gameConfig/}(实体 + 技能 + 规则 + 参考副本)。
     * <p>
     * 只在文件缺失时写;用户已经改过的文件一个字都不会被动。
     * <p>
     * <b>注意它管不了"存在的空文件"</b>:{@code ConfigDefaultWriter} 对"文件不存在"写全量,
     * 对"文件存在"只补"缺的键";而 {@code writeAll} 这一侧是"已经存在就不写",
     * 所以空壳文件要靠 {@link #healDefaultConfigFiles()} 才能填满。
     */
    private static void writeDefaultConfigFiles() {
        writeDefaultConfigFiles("没有检测到配置文件,已生成一份当前全量默认值:");
    }

    /**
     * 自愈:文件<b>存在</b>时也问一次"缺不缺键",缺的就补齐(已有值一个字节不动)。
     * <p>
     * <b>为什么必须有这一步</b>:{@code EntityData.json} / {@code SkillData.json} 的历史 bug 是
     * "在错误的时间点被生成" —— 老版本只在"文件不存在"时调
     * {@link #writeDefaultConfigFiles()},而那次调用可能发生在
     * {@code GameMain#gameInitialize()} 的第一步 {@link #loadGameRules()} 里,
     * 也就是 {@code World} 注册表还空着的时候,于是落下
     * {@code {"version":1,"entities":{}}} 这种 36 字节的空壳;此后每轮启动都因为
     * "文件已经存在"再也不生成,空壳就一直空下去(用户会看到"应用 0 项、影响 0 个模板")。
     * <p>
     * 这一步跑到的时候注册表已经是满的({@link #loadEntityData()} 是
     * {@code GameMain#gameInitialize()} 里 {@code GameStartEvent} 之后才调的),
     * 所以补进去的是真正的全量值。
     * <p>
     * <b>已经完整时什么都不会写</b>({@code ConfigDefaultWriter} 自己判断):
     * 两份补丁配置都会返回 {@code false},这里连一行"已补齐"都不会打,
     * 也不会让 mtime 每次都变 —— 用户的文件若已经全,自愈是彻底无副作用的。
     * <p>
     * 注意这里<b>不调</b> {@code ConfigDefaultWriter#writeAll}:那会把参考副本
     * {@code EntityData.default.json} 无条件重写一遍。但参考副本本身<b>要在这里补</b>
     * （见 {@code ConfigDefaultWriter#writeReferenceIfNeeded}）——
     * 它就是"只由 {@code writeAll} 写"才恒为空壳的：{@code writeAll} 的触发点里第一个跑的
     * {@link #loadGameRules()} 那一刻注册表还是空的。<b>挪这一行的位置之前先读那个方法的说明。</b>
     */
    private static void healDefaultConfigFiles() {
        try {
            boolean healedEntity = ConfigDefaultWriter.writeEntityData(ENTITY_DATA_FILE);
            boolean healedSkill = ConfigDefaultWriter.writeSkillData(SKILL_DATA_FILE);
            // ⚠️ 参考副本的写入口就在这里，**顺序依赖写死**：这一刻 World 注册表已经是满的
            // （官方内容与模组内容都注册完了，玩家还没开始选人）。它是导出物，注册表空着时写
            // 只会得到一份空壳 —— 而历史上前一个写入口 writeAll 恰好是在 loadGameRules 那一刻跑的，
            // 这个坑踩过两次（EntityData.json / SkillData.json 一次，这个参考副本一次），
            // 所以位置不能挪到 loadGameRules 那一侧去。
            boolean healedReference = ConfigDefaultWriter.writeReferenceIfNeeded(
                    ENTITY_DATA_REFERENCE_FILE, ENTITY_DATA_REFERENCE_FILE.getName());
            if (!healedEntity && !healedSkill) {
                return;
            }
            ConfigOutput.noteworthy("[配置] 已有的默认配置缺键,已补齐(只补了没写的键,已有值一个字没动):"
                    + (healedEntity ? ENTITY_DATA_FILE.getName() + " " : "")
                    + (healedSkill ? SKILL_DATA_FILE.getName() : "")
                    + (healedReference ? " " + ENTITY_DATA_REFERENCE_FILE.getName() : ""));
        } catch (IOException e) {
            ConfigOutput.problem("[配置错误] 生成默认值失败:" + e.getMessage());
            LogWriter.writeLog("[配置错误] 生成默认值失败:" + e.getMessage());
        }
    }

    /**
     * 生成/补齐默认配置文件的公共部分(出错只打印,不让游戏起不来)。
     *
     * @param prefix 报告前缀(两种调用场景的话术不同)
     */
    private static void writeDefaultConfigFiles(String prefix) {
        try {
            List<String> written = ConfigDefaultWriter.writeAll(
                    ENTITY_DATA_FILE.getParentFile());
            // 只在"文件真的不存在"时走到这里（一次性事件，不是每轮启动的常态）→ 用提醒级别，
            // 免得用户"配置没了"却以为一切正常
            ConfigOutput.noteworthy(prefix
                    + (written.isEmpty() ? "(不需要写)" : String.join("、", written)));
        } catch (IOException e) {
            ConfigOutput.problem("[配置错误] 生成默认值失败:" + e.getMessage());
            LogWriter.writeLog("[配置错误] 生成默认值失败:" + e.getMessage());
        }
    }

    /**
     * 写入默认配置并加载。
     * <p>
     * 若配置文件的父目录不存在,则会先创建该目录,
     * 然后将默认配置写入配置文件,最后调用 {@link #loadConfig()} 加载配置。
     *
     * @throws IOException 当写入配置文件失败时抛出
     */
    public static void setDefaultConfig() throws IOException {
        if (!TAG_CONFIG_FILE.getParentFile().exists()) TAG_CONFIG_FILE.getParentFile().mkdirs();
        // 显式 UTF-8:默认字符集在中文 Windows 上是 GBK,而读的那一侧(:loadConfig)是 UTF-8,
        // 不显式指定就会"自己写的默认配置自己读成乱码"。
        Files.write(TAG_CONFIG_FILE.toPath(), DEFAULT_TAGS_CONFIG.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        ConfigOutput.noteworthy("使用默认配置：" + TAG_CONFIG_FILE.getPath()
                + " 已按出厂默认值写出（要改成自己的权重就改这个文件）");
        loadConfig();
    }

    /* ------------------------------------------------------------------
     * 小工具:报错定位、标签类型
     * ------------------------------------------------------------------ */

    /**
     * 解析一份配置文本(解析不了就报错并返回 {@code null})。
     *
     * @param text 文本
     * @param file 文件(报错用)
     * @return 根对象;解析失败返回 {@code null}
     */
    private static JSONObject parseObject(String text, File file) {
        try {
            return new JSONObject(text);
        } catch (JSONException e) {
            String message = file.getPath() + " 不是合法的 JSON:" + e.getMessage()
                    + "(这份配置没有生效,但游戏照常运行)";
            ConfigOutput.problem("[配置错误] " + message);
            LogWriter.writeLog("[配置错误] " + message);
            return null;
        }
    }

    /**
     * 检查配置文件的版本号(不认识的版本只警告,不拒绝加载)。
     *
     * @param file 文件
     * @param root 根对象
     */
    private static void warnAboutVersion(File file, JSONObject root) {
        if (!root.has(DataKeys.VERSION)) {
            return;
        }
        Object raw = root.opt(DataKeys.VERSION);
        Long version = EntityDataPatcher.asLong(raw);
        if (version == null || version != 1L) {
            ConfigOutput.problem("[配置警告] " + file.getPath() + " 的 " + DataKeys.VERSION
                    + " 是 " + raw + ",本版只认 1(照常加载,但字段含义可能已经变了)");
        }
    }

    /**
     * 拼一条能定位问题的报错文案:{@code 文件:行 键「x」:原因}。
     *
     * @param file   文件
     * @param text   文件内容
     * @param key    出问题的键
     * @param reason 为什么
     * @return 文案
     */
    private static String describe(File file, String text, String key, String reason) {
        return file.getPath() + ":" + lineOf(text, key) + " 键「" + key + "」:" + reason;
    }

    /**
     * 在原文里找这个键出现在第几行(找不到就返回 0)。
     * <p>
     * {@code org.json} 不报行号,所以这里按 {@code "键"} 或 {@code 键:} 在原文里找第一次出现的位置
     * —— 配置里的键都是唯一的标识符,够用了。<b>报错必须能定位</b>:
     * "配置没生效、控制台上什么都不说"是这里修掉的那个坑。
     *
     * @param text 文件内容
     * @param key  键
     * @return 行号(从 1 开始);找不到返回 0
     */
    private static int lineOf(String text, String key) {
        if (text == null || key == null) {
            return 0;
        }
        int index = text.indexOf('"' + key + '"');
        if (index < 0) {
            index = text.indexOf(key + ':');
        }
        if (index < 0) {
            return 0;
        }
        int line = 1;
        for (int i = 0; i < index && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /**
     * @param tagType 配置里写的标签名
     * @return 标签类型;认不出来返回 {@code null}(调用方只跳过这一项)
     */
    private static TagType parseTagType(String tagType) {
        if (tagType == null) {
            return null;
        }
        for (TagType type : TagType.values()) {
            if (type.name().equalsIgnoreCase(tagType.trim())) {
                return type;
            }
        }
        return null;
    }

    /**
     * @return 全部可用的标签名(报错时提示用)
     */
    private static String tagNames() {
        StringBuilder builder = new StringBuilder();
        for (TagType type : TagType.values()) {
            if (builder.length() > 0) {
                builder.append(" / ");
            }
            builder.append(type.name().toLowerCase());
        }
        return builder.toString();
    }
}
