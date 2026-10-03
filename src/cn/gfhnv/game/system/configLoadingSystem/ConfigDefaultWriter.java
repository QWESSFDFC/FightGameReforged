package cn.gfhnv.game.system.configLoadingSystem;

import cn.gfhnv.game.data.DataBridge;
import cn.gfhnv.game.entity.Entity;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.mod.Mod;
import cn.gfhnv.game.officialStuff.OfficialGameContent;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.skill.SkillCoefficientTunable;
import cn.gfhnv.game.system.mana.Mana;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;
import cn.gfhnv.game.world.World;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * 只读的"当前全量默认值"生成器：把运行期对象上的数值 dump 成两份配置样例。
 * <p>
 * <b>它只读，不改任何对象</b>（除了一处刻意的例外：为了判断 {@code description} 这类
 * "值为 null、因此 dump 不出来"的键到底认不认，它会临时设一个空串再原样设回去）。
 * 生成的文件是<b>样例</b>：用户可以照着改，也可以整段删掉 ——
 * 没写出来的键一律保持构造器算出来的值（补丁语义，见
 * {@code project_analyses/EXTERNAL-DATA-LOADING-2026-10.md}）。
 * <p>
 * <b>四份产物</b>：
 * <ul>
 *     <li>{@link #ENTITY_FILE_NAME}：实体数值（{@code config/gameConfig/EntityData.json}，游戏会读它）；</li>
 *     <li>{@link #SKILL_FILE_NAME}：技能数值（{@code config/gameConfig/SkillData.json}，<b>游戏也读它</b>，
 *     见 {@link SkillDataPatcher}）；</li>
 *     <li>{@link #RULES_FILE_NAME}：游戏规则（{@code config/gameConfig/GameRules.json}，<b>游戏也读它</b>，
 *     见 {@link GameRulesPatcher}）；</li>
 *     <li>{@link #REFERENCE_FILE_NAME}：上面几份的合并参考副本 {@code EntityData.default.json}
 *     —— 用户改过 {@code EntityData.json} 之后，想对照"出厂值是多少"就看它。</li>
 * </ul>
 * <b>只写官方内容</b>（2026-10-03，用户意见"模组配置不能放在这个里面"）：
 * 模组注册的实体与它们的技能<b>不</b>进这两份文件，判据见 {@link #isOfficialContent(Entity)}。
 * 模组内容走它自己的路：{@code config/data/<模组id>.json}
 * （{@code @ModConfig} / {@code MOD_ID} 决定文件名，见 {@code ConfigLoader#loadModData}）。
 * <p>
 * <b>写盘纪律</b>：<b>绝不覆盖</b>用户已有的那几份配置（"缺不缺"由调用方判断）。
 * 所有写盘一律显式 UTF-8 ——
 * {@code String#getBytes()} 用的是平台默认字符集（中文 Windows 上是 GBK），
 * 而读的那一侧是 UTF-8，不显式指定就会"自己写的文件自己读成乱码"。
 *
 * @author AI（DeepSeek）生成
 */
public final class ConfigDefaultWriter {

    /**
     * 实体数据文件名。
     */
    public static final String ENTITY_FILE_NAME = "EntityData.json";
    /**
     * 技能数据文件名。
     */
    public static final String SKILL_FILE_NAME = "SkillData.json";
    /**
     * 参考副本文件名（"出厂值"备份，游戏不读它）。
     */
    public static final String REFERENCE_FILE_NAME = "EntityData.default.json";
    /**
     * 游戏规则文件名。
     */
    public static final String RULES_FILE_NAME = "GameRules.json";
    /**
     * 技能分组名（{@code SkillData.json} 里的顶层键）。
     */
    public static final String SKILLS = "skills";
    /**
     * 参考副本根上的说明键（下划线开头 = <b>给人看的，不是数据</b>）。
     * <p>
     * JSON 没有注释语法，而参考副本又是唯一需要"说清它是什么、哪些东西它表达不了"的文件，
     * 所以按惯例用一个下划线开头的键承载说明文字。
     * <b>不许拿键名当注释</b>（曾经有一个键叫 {@code "skills（SkillData.json 的内容，游戏不读这一段）"}，
     * 它看起来像数据、其实是一句话）。
     * <p>
     * 参考副本<b>不被任何加载器读取</b>（唯一读它的代码是
     * {@link #writeReferenceIfNeeded(File, String)} 那句"有没有 entities"的判断），
     * 所以这个键不会被当成"未知键"报警。
     */
    public static final String NOTE = "_note";

    /**
     * 上一次 {@link #entityDataJson()} 写出来的键（<b>实体完整 id → 该实体写出去的键清单</b>）。
     * <p>
     * 给自测当契约用：这些是"配置里真的能生效的键"，
     * 自测会拿它们和 {@code DataBridge} 实际 dump 出来的键对照。
     */
    private static Map<String, List<String>> lastEntityKeys = new LinkedHashMap<>();
    /**
     * 上一次 {@link #skillDataJson()} 写出来的键（<b>{@code 实体完整id#技能名} → 键清单</b>）。
     */
    private static Map<String, List<String>> lastSkillKeys = new LinkedHashMap<>();

    /**
     * 工具类，不允许实例化。
     */
    private ConfigDefaultWriter() {
    }

    /* ------------------------------------------------------------------
     * 生成（不碰磁盘）
     * ------------------------------------------------------------------ */

    /**
     * 生成实体数值 JSON（全量默认值）。
     *
     * @return 文本（UTF-8 语义，缩进 2 空格，行尾 {@code \n}）
     */
    public static String entityDataJson() {
        Json json = new Json();
        json.beginObject();
        json.raw(DataKeys.VERSION, "1");
        appendEntities(json);
        json.endObject();
        return json.text();
    }

    /**
     * 生成技能数值 JSON（全量默认值）。
     * <p>
     * 这一份是<b>游戏真的会读</b>的配置（见 {@code ConfigLoader#loadSkillData()} 与
     * {@link SkillDataPatcher}）：把某个技能的倍率改成 0，游戏里那个技能就打不出伤害。
     * <p>
     * 每个技能写全 8 项：倍率 3 个、{@code aims}、{@code coolDown}、{@code forEnemies}、
     * {@code consumedMana}、{@code tags}。缺省不写的键一律保持构造器里的值（补丁语义）。
     *
     * @return 文本
     */
    public static String skillDataJson() {
        Json json = new Json();
        json.beginObject();
        json.raw(DataKeys.VERSION, "1");
        appendSkillsFull(json, SKILLS);
        json.endObject();
        return json.text();
    }

    /**
     * 生成"参考副本"：实体全量 + 技能全量放在一个文件里。
     * <p>
     * <b>它要如实反映全量结构</b>（"出厂值参考"的定位）：技能那一段与
     * {@link #skillDataJson()} 用同一套采集与写法，所以
     * {@code consumedMana} 与 {@code tags} 两个块也在里面 ——
     * 参考副本的读者要靠它看出"这个技能到底消不消耗法力、AI 权重是多少"。
     * <p>
     * 根上的 {@link #NOTE} 是说明键（下划线开头 = 不是数据）。
     *
     * @return 文本
     */
    public static String referenceJson() {
        Json json = new Json();
        json.beginObject();
        json.raw(NOTE, Json.quote(referenceNote()));
        json.raw(DataKeys.VERSION, "1");
        appendEntities(json);
        appendSkillsFull(json, SKILLS);
        json.endObject();
        return json.text();
    }

    /**
     * 参考副本 {@link #NOTE} 里的说明文字。
     * <p>
     * 三件事必须说清：<b>它是什么</b>（导出物，不是配置）、<b>游戏读不读它</b>（不读）、
     * 以及<b>它表达不了什么</b>。最后一条是刻意留的坑位说明：
     * 一个键"没出现在这里"可能有两种意思 —— "出厂值就是这个" 与 "这一段我没展开"，
     * 而参考副本的职责是让人分得清这两件事。
     *
     * @return 说明文字
     */
    private static String referenceNote() {
        return "这是「出厂值参考副本」，由游戏按当前注册表自动导出，游戏不读它（改它没有任何效果）。"
                + "要改数值请改同目录的 " + ENTITY_FILE_NAME + "（实体）与 " + SKILL_FILE_NAME + "（技能）。"
                + "技能段的键与 " + SKILL_FILE_NAME + " 完全同构：标量之外还含 consumedMana"
                + "（写成 null = 这个技能不消耗法力）与 tags（AI 权重块）两个嵌套块。";
    }

    /**
     * 把 {@code entities} 段写进当前对象。
     * <p>
     * <b>只写官方内容</b>（{@link #isOfficialContent(Entity)}）：模组注册的实体走它自己的路
     * （{@code config/data/<模组id>.json}），不进游戏自己的这两份配置。
     * <p>
     * 每个实体写三段：{@code base}（通用面板）→ {@code classState}（子类自己的出厂数值，
     * 只写这个模板真的有的键，例如白厄的 {@code coreflame}）→ {@code derived}（派生值）。
     * 两段通用段来自 {@code EntityKeySpecs}，{@code classState} 来自反射面
     * （见 {@link ReflectionConfigBridge#classStateValues}）—— 子类字段进不了那张通用表。
     *
     * @param json 输出器（当前位于根对象里）
     */
    private static void appendEntities(Json json) {
        Map<String, Map<String, String>> base = collectBaseValues();
        Map<String, Map<String, String>> classState = collectClassStateValues();
        Map<String, Map<String, String>> derived = collectDerivedValues();
        json.key(DataKeys.ENTITIES).beginObject();
        for (Map.Entry<String, Map<String, String>> entry : base.entrySet()) {
            json.key(entry.getKey()).beginObject();
            json.key(DataKeys.BASE).beginObject();
            for (Map.Entry<String, String> field : entry.getValue().entrySet()) {
                json.raw(field.getKey(), field.getValue());
            }
            json.endObject();
            Map<String, String> entityClassState = classState.get(entry.getKey());
            if (entityClassState != null && !entityClassState.isEmpty()) {
                json.key(DataKeys.CLASS_SECTION).beginObject();
                for (Map.Entry<String, String> field : entityClassState.entrySet()) {
                    json.raw(field.getKey(), field.getValue());
                }
                json.endObject();
            }
            Map<String, String> entityDerived = derived.get(entry.getKey());
            if (entityDerived != null) {
                json.key(DataKeys.DERIVED).beginObject();
                for (Map.Entry<String, String> field : entityDerived.entrySet()) {
                    json.raw(field.getKey(), field.getValue());
                }
                json.endObject();
            }
            json.endObject();
        }
        json.endObject();
    }

    /**
     * 把技能段写进当前对象（{@code consumedMana} 与 {@code tags} 两个嵌套块一起写）。
     * <p>
     * 用 {@link #collectSkillPatches()} 而不是另做一份"简版"：
     * {@code SkillData.json} 与参考副本的技能段因此<b>逐键同构</b>，
     * 不会出现"这一份里有 {@code tags}、那一份里没有"的两种口径。
     *
     * @param json 输出器（当前位于根对象里）
     * @param name 段名
     */
    private static void appendSkillsFull(Json json, String name) {
        json.key(name).beginObject();
        for (Map.Entry<String, SkillPatch> entry : collectSkillPatches().entrySet()) {
            json.key(entry.getKey()).beginObject();
            SkillPatch patch = entry.getValue();
            for (Map.Entry<String, String> field : patch.scalars().entrySet()) {
                json.raw(field.getKey(), field.getValue());
            }
            json.key(DataKeys.SkillKeys.CONSUMED_MANA);
            if (patch.consumedMana() == null) {
                json.rawValue("null");
            } else {
                json.beginObject();
                for (Map.Entry<String, String> field : patch.consumedMana().entrySet()) {
                    json.raw(field.getKey(), field.getValue());
                }
                json.endObject();
            }
            json.key(DataKeys.SkillKeys.WEIGHT_TAGS).beginObject();
            for (Map.Entry<String, String> field : patch.tags().entrySet()) {
                json.raw(field.getKey(), field.getValue());
            }
            json.endObject();
            json.endObject();
        }
        json.endObject();
    }

    /* ------------------------------------------------------------------
     * 写盘
     * ------------------------------------------------------------------ */

    /**
     * 写 {@code EntityData.json}：<b>缺啥补啥</b>（见 {@link #writeOrHeal}）。
     *
     * @param file 目标文件
     * @return 实际写盘了吗（{@code false} = 文件已经全了，什么都没做）
     * @throws IOException 写不进去时抛出（调用方负责报告，不要让游戏起不来）
     */
    public static boolean writeEntityData(File file) throws IOException {
        return writeOrHeal(file, ENTITY_FILE_NAME, entityDataJson());
    }

    /**
     * 写 {@code SkillData.json}：同样缺啥补啥。
     *
     * @param file 目标文件
     * @return 实际写盘了吗
     * @throws IOException 写不进去时抛出
     */
    public static boolean writeSkillData(File file) throws IOException {
        return writeOrHeal(file, SKILL_FILE_NAME, skillDataJson());
    }

    /**
     * 写 {@code GameRules.json}：同样缺啥补啥。
     *
     * @param file 目标文件
     * @return 实际写盘了吗
     * @throws IOException 写不进去时抛出
     */
    public static boolean writeGameRules(File file) throws IOException {
        return writeOrHeal(file, RULES_FILE_NAME, gameRulesJson());
    }

    /**
     * 写一份配置，<b>缺啥补啥</b>（D5 的修法）。
     * <p>
     * <b>为什么不是"存在就早退"</b>：老写法是 {@code if (file.exists()) return false;}，
     * 于是"空文件会一直空下去" —— 用户手上那三份
     * （{@code EntityData.json} / {@code SkillData.json} / {@code EntityData.default.json}）
     * 就是这么来的，而且照 {@code README} 删掉重建也未必拿得到全量值。
     * <p>
     * <b>为什么不是"直接覆盖"</b>：那是把用户改过的东西悄悄回退掉，比空文件更糟。
     * 所以这里只做一个方向的合并：<b>默认值里有的键、文件里没有 → 补进去；文件里已经有的值 → 一个字节都不动</b>
     * （包括用户自己加的、加载器不认识的键，也原样留着 —— 自愈不是"清理"）。
     * <p>
     * <b>已经完整时不会重写</b>：合并结果为"没补任何东西"时直接返回 {@code false}，
     * 于是"补全之后再启动"不会反复改用户的文件，也不会让 mtime 每次都变。
     *
     * @param file     目标文件
     * @param fileName 文件名（日志用）
     * @param defaults 当前全量默认值（JSON 文本）
     * @return 实际写盘了吗
     * @throws IOException 写不进去时抛出
     */
    private static boolean writeOrHeal(File file, String fileName, String defaults) throws IOException {
        if (!file.exists()) {
            writeUtf8(file, defaults);
            return true;
        }
        String existing = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        JSONObject existingRoot;
        try {
            existingRoot = new JSONObject(existing);
        } catch (JSONException e) {
            // 读不懂就不动它：用户手写的文件坏了，让他自己看得见，不要替他"修"成别的样子
            ConfigOutput.problem("[配置错误] " + fileName + " 现在的内容不是合法 JSON，跳过自愈"
                    + "（这个文件一个字节都没动）：" + e.getMessage());
            return false;
        }
        if (!mergeMissing(existingRoot, new JSONObject(defaults))) {
            return false;
        }
        // 补过东西才重写；重写走 org.json 的标准排版，所以**对象里的键顺序会变成哈希序**
        // （{@code entities} 会跑到 {@code version} 前面、{@code derived} 会跑到 {@code base} 前面），
        // 但**键与值一个不改** —— 只在"文件本来就不全"时发生一次，之后稳定。
        // 想保留"生成器排好的顺序"是做不到的：org.json 的 JSONObject 是 HashMap 支持的，
        // 遍历它拿不到插入顺序，因此重新拼装也只会拼出另一份哈希序（试过，见 D5 之外的记录）。
        writeUtf8(file, existingRoot.toString(2));
        ConfigOutput.noteworthy("[配置] " + fileName + " 缺项已补齐（只补了没写的键，已有值一个字没动）");
        return true;
    }

    /**
     * 把默认值里有、目标里没有的键补进去（<b>已有值一律不动</b>）。
     *
     * @param target   目标对象（会被就地修改）
     * @param defaults 默认值对象
     * @return 补过东西吗
     */
    private static boolean mergeMissing(JSONObject target, JSONObject defaults) {
        boolean changed = false;
        for (String key : defaults.keySet()) {
            if (!target.has(key)) {
                target.put(key, defaults.get(key));
                changed = true;
                continue;
            }
            Object want = defaults.get(key);
            Object have = target.opt(key);
            if (want instanceof JSONObject wantObject && have instanceof JSONObject haveObject
                    && mergeMissing(haveObject, wantObject)) {
                changed = true;
            }
        }
        return changed;
    }

    /**
     * 生成游戏规则 JSON（{@link GameRules} 那张表的当前值，按段分组）。
     * <p>
     * <b>写法</b>：段名 → 键名 → 数值。段和键的顺序与 {@link GameRules#allKeys()} 一致
     * （它就是按"段.键"的字符串拼出来的），所以同一份状态下生成出来的文本逐字节一致。
     *
     * @return 文本
     */
    public static String gameRulesJson() {
        Json json = new Json();
        json.beginObject();
        json.raw(DataKeys.VERSION, "1");
        Map<String, Map<String, String>> sections = new LinkedHashMap<>();
        for (String key : GameRules.allKeys()) {
            int dot = key.indexOf(DataKeys.SECTION_SEPARATOR);
            if (dot <= 0) {
                continue;
            }
            String section = key.substring(0, dot);
            String name = key.substring(dot + 1);
            sections.computeIfAbsent(section, ignored -> new LinkedHashMap<>())
                    .put(name, valueOfRule(key));
        }
        for (Map.Entry<String, Map<String, String>> entry : sections.entrySet()) {
            json.key(entry.getKey()).beginObject();
            for (Map.Entry<String, String> field : entry.getValue().entrySet()) {
                json.raw(field.getKey(), field.getValue());
            }
            json.endObject();
        }
        json.endObject();
        return json.text();
    }

    /**
     * 取一条规则键的当前值，并序列化成 JSON 数字。
     * <p>
     * 类型要在这里定死：整数型键写成 {@code 1}，浮点型键写成 {@code 0.34} ——
     * 写成 {@code 1.0} 也能读，但默认文件是给人对照的，能一眼看出量纲更好。
     *
     * @param key 规则键
     * @return 序列化后的值
     */
    private static String valueOfRule(String key) {
        if (GameRules.isIntegerKey(key)) {
            return Json.value(GameRules.getLong(key));
        }
        return Json.value(GameRules.getDouble(key));
    }

    /**
     * 无条件写一份参考副本 {@code EntityData.default.json}（覆盖已有内容 —— 它只是给用户对照用的备份）。
     * <p>
     * ⚠️ <b>调用时机决定内容，不能随便调</b>：它把当前 {@link World} 注册表 dump 出来，
     * 所以<b>注册表空着的时候调用它，写出来的就是一份空壳</b>
     * （{@code {"version":1,"entities":{},…}}）。真实启动里注册表要等到
     * {@code GameMain#gameInitialize()} 的 {@code World.addMod(new OfficialGameContent())}
     * 与 {@code GameStartEvent}（模组注册内容）之后才是满的 ——
     * 而那一刻之前 {@code loadGameRules()} 已经跑过了。
     * 需要"只在注册表满了之后写"的语义请用 {@link #writeReferenceIfNeeded(File, String)}。
     *
     * @param file 目标文件
     * @throws IOException 写不进去时抛出
     */
    public static void writeReference(File file) throws IOException {
        writeUtf8(file, referenceJson());
    }

    /**
     * <b>缺内容才写</b>参考副本：文件里有 {@code entities} 段且<b>不是空的</b>就一个字节都不动。
     * <p>
     * <b>为什么单独有这个方法</b>：参考副本的写入口原来只有 {@link #writeAll(File)}，
     * 而 {@code writeAll} 在真实启动里只由"检测到配置缺失"那条路触发，
     * 三个触发点里<b>第一个跑的是 {@code loadGameRules()}</b>（{@code GameMain:65}）——
     * 那时 {@link World} 注册表还空着，于是它天生就是一份空壳，
     * 之后再没有任何代码重写它（自愈刻意不碰它），
     * 而 {@code README.md} 写着"删掉整个文件 = 下次启动会重新生成一份全量默认值"。
     * <p>
     * <b>所以本方法必须在"注册表已经满了之后"调用</b> —— 现在挂在
     * {@code ConfigLoader#healDefaultConfigFiles()} 里（{@link #writeEntityData(File)} 之后），
     * 也就是 {@code loadEntityData()} 那一刻：官方内容与模组内容都已注册完、玩家还没开始选人。
     * <b>动它的调用位置之前先想清楚这一点</b>（这个坑踩过两次：
     * 一次是 {@code EntityData.json} / {@code SkillData.json} 恒为空壳，一次就是这个参考副本）。
     * <p>
     * 判据与另外三份自愈配置不同：那三份要"缺键就补"（用户的文件里可能有他改过的值），
     * 而参考副本是<b>纯导出、用户不该改它</b>，所以"空壳 → 重写全量，非空 → 一个字不动"。
     * 这样既救得回空壳，又不会每次启动都改它的 mtime。
     *
     * @param file     目标文件
     * @param fileName 文件名（日志用）
     * @return 实际写盘了吗（{@code false} = 已经有内容，什么都没做）
     * @throws IOException 写不进去时抛出
     */
    public static boolean writeReferenceIfNeeded(File file, String fileName) throws IOException {
        if (hasEntities(file)) {
            return false;
        }
        writeReference(file);
        ConfigOutput.noteworthy("[配置] " + fileName + "（出厂值参考副本）按当前注册表重写了一 —— "
                + "它里面没有实体（文件不存在 / 是空壳 / 不是合法 JSON）才会走这一步；"
                + "它是导出物，别拿它当配置改，要改数值请改 " + ENTITY_FILE_NAME + "。");
        return true;
    }

    /**
     * @param file 文件
     * @return 这个文件里有 {@code entities} 段、而且<b>至少有一个实体</b>
     */
    private static boolean hasEntities(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        try {
            JSONObject root = new JSONObject(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            JSONObject entities = root.optJSONObject(DataKeys.ENTITIES);
            return entities != null && !entities.isEmpty();
        } catch (IOException | JSONException e) {
            // 读不动 / 读不懂 → 当成"没有内容"，让它被重写（它是导出物，不需要保护）
            return false;
        }
    }

    /**
     * 把三份默认文件与参考副本一起写到目录里。
     *
     * @param directory 目标目录（不存在会被创建）
     * @return 实际写出去的文件（按写入顺序；已存在而跳过的不会出现在里面）
     * @throws IOException 写不进去时抛出
     */
    public static List<String> writeAll(File directory) throws IOException {
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("建不了目录：" + directory.getPath());
        }
        List<String> written = new ArrayList<>();
        File entityFile = new File(directory, ENTITY_FILE_NAME);
        if (writeEntityData(entityFile)) {
            written.add(entityFile.getPath());
        }
        File skillFile = new File(directory, SKILL_FILE_NAME);
        if (writeSkillData(skillFile)) {
            written.add(skillFile.getPath());
        }
        // 参考副本走"缺内容才写"那一条：空壳会被救回来，而"已经有内容"的不会被反复重写
        // （老写法是无条件覆盖，于是每次进这条路都要改它的 mtime）。
        File referenceFile = new File(directory, REFERENCE_FILE_NAME);
        if (writeReferenceIfNeeded(referenceFile, REFERENCE_FILE_NAME)) {
            written.add(referenceFile.getPath());
        }
        File rulesFile = new File(directory, RULES_FILE_NAME);
        if (writeGameRules(rulesFile)) {
            written.add(rulesFile.getPath());
        }
        return written;
    }

    /**
     * 显式按 UTF-8 写一个文本文件。
     * <p>
     * <b>必须显式</b>：{@code String#getBytes()} 用的是平台默认字符集，
     * 在中文 Windows 上是 GBK，而读的那一侧用的是 UTF-8 —— 自己写的文件自己读成乱码。
     *
     * @param file 目标文件
     * @param text 内容
     * @throws IOException 写不进去时抛出
     */
    public static void writeUtf8(File file, String text) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("建不了目录：" + parent.getPath());
        }
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
    }

    /* ------------------------------------------------------------------
     * 契约：上一次写出去的键（自测用）
     * ------------------------------------------------------------------ */

    /**
     * @return 实体 id → 写出去的键清单
     */
    public static Map<String, List<String>> lastEntityKeys() {
        return Collections.unmodifiableMap(lastEntityKeys);
    }

    /**
     * @return {@code 实体id#技能名} → 写出去的键清单
     */
    public static Map<String, List<String>> lastSkillKeys() {
        return Collections.unmodifiableMap(lastSkillKeys);
    }

    /* ------------------------------------------------------------------
     * 采集
     * ------------------------------------------------------------------ */

    /**
     * 采集每个实体模板的面板属性。
     * <p>
     * 键的顺序固定为：面板标量 → 五行抗性 → 五行法力成长 → 背包格数，
     * 这样同一份运行状态下生成出来的文本永远逐字节一致。
     * <p>
     * <b>只采集官方内容</b>（{@link #isOfficialContent(Entity)}）—— 见
     * {@link #isOfficialContent(Entity)} 的说明。
     *
     * @return 实体 id → （键 → 已序列化的 JSON 值）
     */
    private static Map<String, Map<String, String>> collectBaseValues() {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        lastEntityKeys = new LinkedHashMap<>();
        for (Entity entity : World.getEntityList()) {
            if (!(entity instanceof LivingThing living) || !isOfficialContent(entity)) {
                continue;
            }
            String id = entity.getId();
            if (id == null || id.isEmpty()) {
                continue;
            }
            Map<String, String> values = serialize(
                    SpecWriter.collect(living, EntityKeySpecs.BEFORE_DERIVED));
            result.put(id, values);
            lastEntityKeys.put(id, new ArrayList<>(values.keySet()));
        }
        return result;
    }

    /**
     * 采集每个实体模板的<b>子类配置字段</b>（{@code classState} 段）。
     * <p>
     * 与另外两个采集器不同，这一段<b>不是照表生成的</b>：{@code EntityKeySpecs} 那张表里的行
     * 要对任意 {@code LivingThing} 成立，而 {@code coreflame} 只在 {@code Phainon} 上存在。
     * 所以这里问的是<b>反射面</b>（键名 = {@code /data} 数据名 + 当前值），
     * 键清单由 {@link DataKeys#CLASS_CONFIG} 划定 —— 于是"只给有这个字段的模板写"是自动的。
     * <p>
     * 只采集官方内容，与 {@link #collectBaseValues()} 同一个口径。
     *
     * @return 实体 id → （键 → 已序列化的 JSON 值）；没有子类配置字段的模板不会出现在里面
     */
    private static Map<String, Map<String, String>> collectClassStateValues() {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (Entity entity : World.getEntityList()) {
            if (!(entity instanceof LivingThing living) || !isOfficialContent(entity)) {
                continue;
            }
            String id = entity.getId();
            if (id == null || id.isEmpty()) {
                continue;
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (Map.Entry<String, cn.gfhnv.game.data.NbtTag> entry
                    : ReflectionConfigBridge.classStateValues(living).entrySet()) {
                values.put(entry.getKey(), Json.value(classStateJavaValue(entry.getValue())));
            }
            if (!values.isEmpty()) {
                result.put(id, values);
            }
        }
        return result;
    }

    /**
     * 把子类配置字段的标签还原成"Java 侧自然值"，交给 {@link Json#value(Object)} 序列化。
     * <p>
     * <b>为什么要这一步</b>：标签的文本形式是 SNBT（{@code 15L} 这种带后缀），直接写进 JSON 会得到
     * 一个<b>字符串</b> {@code "15L"} —— 加载器读回来就是类型不对。
     * 整数走 {@link cn.gfhnv.game.data.NbtTag#asLong()}（写出去没有小数点），
     * 其余走 {@link cn.gfhnv.game.data.NbtTag#asDouble()}，布尔与字符串照原样。
     *
     * @param tag 标签
     * @return Java 侧自然值
     */
    private static Object classStateJavaValue(cn.gfhnv.game.data.NbtTag tag) {
        return switch (tag.type()) {
            case BYTE -> tag.asBoolean();
            case INT, LONG -> tag.asLong();
            case DOUBLE -> tag.asDouble();
            default -> tag.asString();
        };
    }

    /**
     * 采集每个实体模板的派生值（{@code hpMax} / {@code attack} / {@code defence} / {@code hp}）。
     * <p>
     * 与上面同一个来源（{@link EntityKeySpecs#DERIVED}），所以"写出去的"与"读得回来的"
     * 必然是同一批键。只采集官方内容，口径同上。
     *
     * @return 实体 id → （键 → 值），顺序固定
     */
    private static Map<String, Map<String, String>> collectDerivedValues() {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (Entity entity : World.getEntityList()) {
            if (!(entity instanceof LivingThing living) || !isOfficialContent(entity)) {
                continue;
            }
            String id = entity.getId();
            if (id == null || id.isEmpty()) {
                continue;
            }
            result.put(id, serialize(SpecWriter.collect(living, EntityKeySpecs.DERIVED)));
        }
        return result;
    }

    /* ------------------------------------------------------------------
     * 官方 / 模组的分界（2026-10-03）
     * ------------------------------------------------------------------ */

    /**
     * <b>这个实体是不是官方内容</b>（决定它进不进游戏自己的 {@code EntityData.json}）。
     * <p>
     * <b>为什么要有这条界线</b>（用户意见："模组配置不能放在这个里面"）：
     * 自愈生成的是<b>游戏自己的</b>配置 —— 它认的键、写的默认值都是游戏侧的口径。
     * 模组内容混进来会有两个后果：① 用户的官方配置文件里躺着别人的东西，
     * 删模组之后那几段就成了没人认领的死配置；② 模组的数值被固化进游戏文件，
     * 用户改模组默认值时会与"上次自愈写进去的快照"打架。
     * 模组内容有它自己的路：{@code config/data/<模组id>.json}
     * （{@code @ModConfig} 注解 / {@code MOD_ID} 决定文件名，见 {@code ConfigLoader#loadModData}）。
     * <p>
     * <b>判据是"谁注册的"，不是"id 里有没有冒号"</b>（与
     * {@code OfficialGameContent.isOfficial(Entity)} 同一口径）：官方内容的 id 同样带前缀
     * （{@code game_official_content:playerOne}），按冒号判断会把官方当成模组。
     * <b>没有模组认领的实体按官方处理</b>（自测往 {@code World} 里塞的探针就是这种），
     * 保持宽松 —— 那样临时内容不会因为"没人认领"而整段消失。
     * <p>
     * <b>它只管生成，不管加载</b>：用户自己在 {@code EntityData.json} 里给模组实体写补丁
     * 仍然会生效（补丁器按 id 找模板，与这一段无关）。
     *
     * @param entity 实体；可为 {@code null}
     * @return 是不是官方内容
     */
    public static boolean isOfficialContent(Entity entity) {
        if (entity == null) {
            return false;
        }
        for (Mod mod : World.getModList()) {
            if (mod == null) {
                continue;
            }
            for (Entity owned : mod.getEntityList()) {
                if (owned == entity) {
                    return mod instanceof OfficialGameContent;
                }
            }
        }
        return true;
    }

    /**
     * @param key {@code 实体完整id#技能名} 形态的键
     * @return 这个技能是不是挂在官方实体上的（不是的话不进 {@code SkillData.json}）
     */
    public static boolean isOfficialSkillKey(String key) {
        if (key == null) {
            return false;
        }
        int separator = key.indexOf(DataKeys.SKILL_SEPARATOR);
        if (separator <= 0) {
            return false;
        }
        String entityId = key.substring(0, separator);
        for (Entity entity : World.getEntityList()) {
            if (entityId.equals(entity.getId())) {
                return isOfficialContent(entity);
            }
        }
        return false;
    }

    /**
     * 把"键 → Java 值"排成"键 → 已序列化的 JSON 值"。
     *
     * @param values {@link SpecWriter} 采出来的值
     * @return 同一顺序的序列化结果
     */
    private static Map<String, String> serialize(Map<String, Object> values) {
        Map<String, String> text = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            text.put(entry.getKey(), Json.value(entry.getValue()));
        }
        return text;
    }

    /**
     * 采集每个模板的技能数值（标量 + 消耗块 + 权重块）。
     * <p>
     * <b>一份实现供两个读者</b>：{@link #skillDataJson()}（游戏真的会读它）与
     * {@link #referenceJson()}（出厂值参考副本）—— 两边的技能段因此逐键同构。
     * <p>
     * <b>只采集官方实体的技能</b>（{@link #isOfficialContent(Entity)}）：模组实体的技能走模组自己的
     * {@code config/data/<模组id>.json}。
     *
     * @return {@code 实体id#技能名} → 补丁内容
     */
    private static Map<String, SkillPatch> collectSkillPatches() {
        Map<String, SkillPatch> result = new LinkedHashMap<>();
        lastSkillKeys = new LinkedHashMap<>();
        for (Entity entity : World.getEntityList()) {
            if (!(entity instanceof LivingThing living) || living.getController() == null
                    || !isOfficialContent(entity)) {
                continue;
            }
            String entityId = entity.getId();
            if (entityId == null || entityId.isEmpty()) {
                continue;
            }
            List<Skill> skills = living.getController().getSkills();
            if (skills == null) {
                continue;
            }
            for (Skill skill : skills) {
                if (skill == null) {
                    continue;
                }
                String key = entityId + DataKeys.SKILL_SEPARATOR + skill.getName();
                int suffix = 2;
                String unique = key;
                while (result.containsKey(unique)) {
                    unique = key + "#" + suffix;
                    suffix++;
                }
                result.put(unique, skillPatchOf(skill));
                lastSkillKeys.put(unique, new ArrayList<>(result.get(unique).keys()));
            }
            // 注册表里挂在这只模板名下的原型（白厄的觉醒技能）：键与控制器技能共用一套，
            // 写给用户看的默认文件里因此看得见它们（改得动的前提是看得见）
            for (Skill prototype : SkillDataPatcher.prototypesOf(living)) {
                String key = entityId + DataKeys.SKILL_SEPARATOR + prototype.getName();
                if (result.containsKey(key)) {
                    continue;
                }
                result.put(key, skillPatchOf(prototype));
                lastSkillKeys.put(key, new ArrayList<>(result.get(key).keys()));
            }
        }
        return result;
    }

    /**
     * 把一个技能实例的数值拍成一份补丁内容。
     *
     * @param skill 技能
     * @return 补丁内容（键的顺序固定，保证同一状态下生成的文本逐字节一致）
     */
    private static SkillPatch skillPatchOf(Skill skill) {
        // 同一张表：SkillKeySpecs.SCALARS（与补丁器读的是同一批键）
        Map<String, String> scalars = serialize(SpecWriter.collect(skill, SkillKeySpecs.SCALARS));
        Map<String, String> mana = null;
        Mana consumed = skill.getConsumedMana();
        if (consumed != null) {
            // 无消耗的技能写 JSON 的 null（加载器认这个写法 = 保持"不消耗"），
            // 而不是省略这个键 —— 省略会让"这个技能到底消不消耗"在默认文件里看不出来。
            mana = new LinkedHashMap<>();
            mana.put(DataKeys.SkillKeys.MANA_AMOUNT, Json.value(consumed.getAmount()));
            mana.put(DataKeys.SkillKeys.MANA_ELEMENT,
                    Json.value(consumed.getElementSort() == null ? null
                            : consumed.getElementSort().name()));
        }
        Map<String, String> tags = new LinkedHashMap<>();
        for (Map.Entry<TagType, Tag> entry : skill.getTags().entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                tags.put(entry.getKey().name(), Json.value(entry.getValue().getWeight()));
            }
        }
        // 子类自己的数值也写出来，否则那些值就是"能改但默认文件里看不见"：
        // 整数旋钮（例如生命值恢复技能的"释放门槛"）与技能自报的具名系数
        // （"每层打几段"、"每层给多少倍率"…）走的是同一条路 —— 后者继承前者。
        for (Map.Entry<String, Double> entry
                : coefficientValuesOf(skill).entrySet()) {
            scalars.put(entry.getKey(), Json.value(coefficientText(entry.getValue())));
        }
        return new SkillPatch(scalars, mana, tags);
    }

    /**
     * @param skill 技能
     * @return 这个技能自己报出来的具名系数（不支持时是空表）
     */
    private static Map<String, Double> coefficientValuesOf(Skill skill) {
        if (skill instanceof SkillCoefficientTunable tunable) {
            return tunable.coefficientValues();
        }
        return Collections.emptyMap();
    }

    /**
     * 把一个系数的当前值写成 JSON 文本。
     * <p>
     * <b>整数值写成整数</b>（{@code 90} 而不是 {@code 90.0}），两个理由：
     * <ol>
     *     <li>整数旋钮（{@code NumericSkillTunable}）那一侧的写法与加具名系数之前<b>逐字一致</b>；</li>
     *     <li>{@code org.json} 把带小数点的数读成 {@link java.math.BigDecimal}，
     *     而 {@code KeySpec.asLong} 只认 {@code Double}/{@code Float} 形式的整数值 ——
     *     写成 {@code 90.0} 会让"要整数"那条检查误报"类型不对"。</li>
     * </ol>
     *
     * @param value 系数值
     * @return 写进 JSON 的值（整数用 {@link Long}，否则用 {@link Double}）
     */
    private static Object coefficientText(double value) {
        if (!Double.isInfinite(value) && !Double.isNaN(value) && value == Math.floor(value)
                && Math.abs(value) < 9.0e15) {
            return (long) value;
        }
        return value;
    }

    /**
     * @param living  实体
     * @param element 元素名（小写）
     * @return 对应法力成长系数
     */
    private static double manaGrowOf(LivingThing living, String element) {
        return switch (element) {
            case "metal" -> living.getMetalManaGrow();
            case "wood" -> living.getWoodManaGrow();
            case "water" -> living.getWaterManaGrow();
            case "fire" -> living.getFireManaGrow();
            default -> living.getDirtManaGrow();
        };
    }

    /**
     * 读描述文本（{@link EntityKeySpecs} 那张表里 {@code description} 这一行的"读法"）。
     * <p>
     * {@code description} 可能是 {@code null}，而 {@code DataBridge} 会把值为 {@code null} 的字段
     * 整个跳过 —— 那样它在"实际 dump 出来的键"里就看不见了，契约断言会误判成"这个键不在 /data 里"。
     * 所以这里临时设一个空串问一次"这个键到底认不认"，再原样设回去。
     *
     * @param living 实体
     * @return 描述文本；确实没有时返回 {@code null}（生成器会跳过这个键）
     */
    public static String descriptionForFile(LivingThing living) {
        String previous = living.getDescription();
        if (previous != null && !previous.isEmpty()) {
            return previous;
        }
        living.setDescription("");
        boolean known = dump(living).values().containsKey(DataKeys.Base.DESCRIPTION);
        living.setDescription(previous);
        return known ? previous : null;
    }

    /**
     * 把一个值放进表里（{@code null} 跳过）。
     *
     * @param values 目标表
     * @param key    键
     * @param value  值
     */
    private static void put(Map<String, String> values, String key, Object value) {
        if (value == null) {
            return;
        }
        values.put(key, Json.value(value));
    }

    /**
     * @param type 类
     * @return {@code /data} 认得的字段名（含 {@code @DataField} 改名后的、含 {@code @DataFlatten} 组件展开的）
     */
    public static List<String> dataNames(Class<?> type) {
        return DataBridge.dataNames(type);
    }

    /**
     * 把对象 dump 成复合标签（自测的对照口径之一：字段清单）。
     *
     * @param object 对象
     * @return 标签
     */
    public static cn.gfhnv.game.data.NbtCompound dump(Object object) {
        cn.gfhnv.game.data.NbtTag tag = DataBridge.toTag(object);
        return tag instanceof cn.gfhnv.game.data.NbtCompound compound
                ? compound : new cn.gfhnv.game.data.NbtCompound();
    }

    /* ------------------------------------------------------------------
     * 与 /data 的对照（自测用）
     * ------------------------------------------------------------------ */

    /**
     * 一个技能的补丁内容：标量键 + 消耗块 + 权重块。
     *
     * @param scalars      标量键（倍率 / 目标数 / 冷却 / 阵营）
     * @param consumedMana 消耗块（{@code amount} + {@code element}）；<b>{@code null} = 这个技能不消耗法力</b>
     *                     （写出去是 JSON 的 {@code null}，加载器认这个写法）
     * @param tags         权重块（{@code 标签类型 → 权重}）
     * @author AI（DeepSeek）生成
     */
    private record SkillPatch(Map<String, String> scalars, Map<String, String> consumedMana,
                              Map<String, String> tags) {

        /**
         * @return 这份补丁写出去的全部键（标量在前，两个块名在后）
         */
        List<String> keys() {
            List<String> keys = new ArrayList<>(scalars.keySet());
            keys.add(DataKeys.SkillKeys.CONSUMED_MANA);
            keys.add(DataKeys.SkillKeys.WEIGHT_TAGS);
            return keys;
        }
    }

    /**
     * 极简 JSON 输出器：只做本项目需要的两件事 —— 转义字符串、按固定缩进排版。
     * <p>
     * 用途只是让生成出来的文件"人能读"，所以不追求通用性；解析那一侧一律交给 {@code org.json}。
     *
     * @author AI（DeepSeek）生成
     */
    private static final class Json {

        /**
         * 缩进用的空格数。
         */
        private static final String INDENT = "  ";
        /**
         * 输出缓冲。
         */
        private final StringBuilder builder = new StringBuilder();
        /**
         * 当前缩进层级。
         */
        private int depth = 0;
        /**
         * 当前这一层还没有成员（写完 {@code {} } 时不用换行）。
         */
        private boolean fresh = true;

        /**
         * @param value 值
         * @return 序列化后的 JSON 文本
         */
        static String value(Object value) {
            if (value instanceof String text) {
                return quote(text);
            }
            if (value instanceof Boolean flag) {
                return flag.toString();
            }
            if (value instanceof Double || value instanceof Float) {
                return String.valueOf(((Number) value).doubleValue());
            }
            if (value instanceof Number number) {
                return String.valueOf(number.longValue());
            }
            return quote(String.valueOf(value));
        }

        /**
         * @param text 文本
         * @return 带引号、已转义的 JSON 字符串
         */
        static String quote(String text) {
            StringBuilder out = new StringBuilder("\"");
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                switch (c) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> {
                        if (c < 0x20 || c == 0x7f) {
                            out.append(String.format("\\u%04x", (int) c));
                        } else {
                            out.append(c);
                        }
                    }
                }
            }
            return out.append('"').toString();
        }

        /**
         * @return 全部输出
         */
        String text() {
            return builder.toString();
        }

        /**
         * 开始一个对象（写在成员位置上）。
         *
         * @return 自己
         */
        Json beginObject() {
            builder.append('{');
            depth++;
            fresh = true;
            return this;
        }

        /**
         * 结束当前对象。
         *
         * @return 自己
         */
        Json endObject() {
            depth--;
            if (fresh) {
                builder.append('}');
            } else {
                builder.append('\n').append(INDENT.repeat(depth)).append('}');
            }
            fresh = false;
            return this;
        }

        /**
         * 写一个键（后面必须紧跟一个值或一个 {@link #beginObject()}）。
         *
         * @param key 键
         * @return 自己
         */
        Json key(String key) {
            before();
            builder.append(quote(key)).append(": ");
            return this;
        }

        /**
         * 写一个已经序列化好的字段。
         *
         * @param key   键
         * @param value 序列化后的值
         * @return 自己
         */
        Json raw(String key, String value) {
            key(key);
            builder.append(value);
            fresh = false;
            return this;
        }

        /**
         * 在成员位置上直接写一段已经序列化好的值（用于 {@code null} 这种没有键的值）。
         *
         * @param value 序列化后的值
         * @return 自己
         */
        Json rawValue(String value) {
            builder.append(value);
            fresh = false;
            return this;
        }

        /**
         * 成员之间的换行、缩进与逗号。
         */
        private void before() {
            if (fresh) {
                builder.append('\n');
            } else {
                builder.append(",\n");
            }
            builder.append(INDENT.repeat(depth));
            fresh = false;
        }
    }
}
