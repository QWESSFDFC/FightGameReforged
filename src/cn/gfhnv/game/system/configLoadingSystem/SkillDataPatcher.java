package cn.gfhnv.game.system.configLoadingSystem;

import cn.gfhnv.game.entity.Entity;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.skill.NumericSkillTunable;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.skill.SkillCoefficientTunable;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.mana.Mana;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;
import cn.gfhnv.game.world.World;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

/**
 * 技能数值补丁器：把 {@code config/gameConfig/SkillData.json} 里<b>显式写出来</b>的键，
 * 打到 {@link World} 注册表模板的技能实例上。
 * <p>
 * <b>键 = {@code <实体完整id>#<技能名>}</b>：控制器持有的技能实例是"每个实体 {@code copy()} 出来的一份"，
 * 所以"按对象身份定位一条技能配置"不可行。技能名就是子类构造器里
 * {@code super("举杯邀月", …)} 的第一个参数。
 * <p>
 * <b>技能现在有 id（{@link Skill#getId()}）了，为什么键还是技能名？</b>因为 id 是"注册表里那一条原型"
 * 的身份（{@link World#findSkill(String)} 用它查），而配置要打的是<b>某只模板手上的那份技能</b> ——
 * 同一个类（例如 {@code universalSkill.CommonAttack}）会被五只模板各造一份、倍率各不相同，
 * 用 id 反而分不开。所以两套名字各管一段：<b>id 管"查找"，实体 id + 技能名管"配置"</b>。
 * <p>
 * 技能注册表的另一条来源见 {@link #prototypesOf}：不在控制器里的<b>技能原型</b>（白厄的觉醒技能）
 * 按归属关系挂在某只模板名下，用的是同一套键。
 * <p>
 * <b>能外置的只有纯数值</b>（倍率 / 目标数 / 冷却 / 消耗 / 阵营 / AI 权重 / 具名系数）：
 * <ul>
 *     <li>{@code comeToEffect} / {@code canUse} 的写法是<b>代码</b>，不外置；</li>
 *     <li>技能自己算出来的动态倍率（"按【灾难之力】层数决定打几段"）<b>算式本身</b>不外置，
 *     但算式里的每个<b>系数</b>可以 —— 技能实现 {@link SkillCoefficientTunable} 把
 *     "每层打几段 / 每层给多少倍率 / 满层那记收尾多大"报出来，
 *     它们就以<b>扁平具名键</b>的形式写在技能对象里（见 {@link #applyCoefficients}）；</li>
 *     <li>{@code nowCoolDown}（运行期剩余冷却）与 {@code extraDamage}（伤害算完清零）
 *     是运行期状态，写进配置只会造成"改了没反应"。</li>
 * </ul>
 * <b>三条实现纪律</b>：
 * <ol>
 *     <li><b>每个技能实例只补一次</b> —— 同一个技能对象在注册表里只挂在一个模板上，
 *     但"人工构造的配置里出现两遍"同样要被挡住（用对象身份去重）；</li>
 *     <li><b>改倍率不会自动重算任何东西</b>：倍率是"出招那一刻被读取的数"，
 *     所以没有 {@code EntityDataPatcher} 那种"派生值重算"问题 —— 但改了之后
 *     <b>已经复制出去的副本</b>不会跟着变（补丁只作用在模板上，副本是在选人时从模板复制的）；</li>
 *     <li><b>逐项容错</b>：未知键 / 类型不对 / 找不到技能都只跳过那一项，结尾汇总打印。</li>
 * </ol>
 *
 * @author AI（DeepSeek）生成
 */
public final class SkillDataPatcher {

    /**
     * 消耗块（{@code {"amount":10,"element":"UNIVERSAL"}}）。
     */
    private static final String SECTION_MANA = DataKeys.SkillKeys.CONSUMED_MANA;
    /**
     * 报错文案里的分隔符（{@code consumedMana.amount}）。
     */
    private static final String SECTION_SEPARATOR = DataKeys.SECTION_SEPARATOR;

    /**
     * 工具类，不允许实例化。
     */
    private SkillDataPatcher() {
    }

    /* ------------------------------------------------------------------
     * 入口
     * ------------------------------------------------------------------ */

    /**
     * 应用一份已经解析好的技能数据。
     *
     * @param root 根对象（形如 {@code {"version":1,"skills":{…}}}）
     * @param note 这一批配置的来源说明（写进日志与 {@link Report}）
     * @return 报告
     */
    public static Report apply(JSONObject root, String note) {
        Report report = new Report(note);
        if (root == null) {
            report.error("根不是 JSON 对象");
            return report;
        }
        JSONObject skills = root.optJSONObject(DataKeys.SKILLS);
        if (skills == null) {
            if (root.has(DataKeys.SKILLS)) {
                report.error("「" + DataKeys.SKILLS + "」不是对象（它该是「实体id#技能名 → 数值」的映射）");
            } else {
                report.error("缺少「" + DataKeys.SKILLS + "」这一层（技能数值都写在它下面）");
            }
            return report;
        }
        for (String key : skills.keySet()) {
            JSONObject patch = skills.optJSONObject(key);
            if (patch == null) {
                report.error("技能「" + key + "」下面的内容不是对象（该是 {} ）");
                continue;
            }
            List<Skill> targets = targetsOf(key, report);
            if (targets.isEmpty()) {
                continue;
            }
            for (Skill target : targets) {
                if (!report.markBound(key, target)) {
                    report.error("技能「" + key + "」在同一个实体下匹配到了同一个技能对象两次，第二份已跳过");
                    continue;
                }
                patch(target, patch, report, key);
            }
        }
        return report;
    }

    /**
     * 按 {@code 实体id#技能名} 找注册表模板上的技能实例。
     * <p>
     * 实体那一半的匹配口径与 {@link EntityDataPatcher} 完全一致（<b>先用完整 id 精确匹配</b>，
     * 短名当且仅当"注册表里只有一条 id 以它结尾"时才认）—— 官方注册了两种
     * {@code BrokenContainer}，按类或按短名乱匹配会让它们共用一份配置。
     *
     * @param key    配置里的键（{@code 实体id#技能名}）
     * @param report 报告（记录"找不到"）
     * @return 命中的技能实例
     */
    private static List<Skill> targetsOf(String key, Report report) {
        int split = key.indexOf(DataKeys.SKILL_SEPARATOR);
        if (split <= 0 || split == key.length() - 1) {
            report.error("技能键「" + key + "」的写法不对（该是「实体完整id#技能名」，例如"
                    + " game_official_content:playerOne#枪射击）");
            return Collections.emptyList();
        }
        String entityId = key.substring(0, split);
        String skillName = key.substring(split + 1);
        List<LivingThing> owners = entitiesOf(entityId, report);
        if (owners.isEmpty()) {
            return Collections.emptyList();
        }
        List<Skill> found = new ArrayList<>();
        Set<String> knownNames = new LinkedHashSet<>();
        for (LivingThing owner : owners) {
            for (Skill skill : skillsOf(owner)) {
                knownNames.add(skill.getName());
                if (skillName.equals(skill.getName())) {
                    found.add(skill);
                }
            }
            // 注册表里挂在这只模板名下的原型（见 prototypesOf）：
            // 觉醒技能这类"技能自己现场造出来"的内容不在控制器里，只有这条路找得到
            for (Skill prototype : prototypesOf(owner)) {
                knownNames.add(prototype.getName());
                if (skillName.equals(prototype.getName())) {
                    found.add(prototype);
                }
            }
        }
        if (found.isEmpty()) {
            report.error("实体「" + entityId + "」上没有叫「" + skillName + "」的技能"
                    + "（技能改名会让这条配置静默失效，所以这里显式点名）—— 它有：" + knownNames);
        }
        return found;
    }

    /**
     * 按 id 找注册表里的实体模板（口径与 {@link EntityDataPatcher} 一致：完整 id 优先）。
     *
     * @param id     配置里写的实体 id
     * @param report 报告（记录"找不到"与"短名歧义"）
     * @return 命中的模板
     */
    private static List<LivingThing> entitiesOf(String id, Report report) {
        List<LivingThing> exact = new ArrayList<>();
        for (Entity entity : World.getEntityList()) {
            if (entity instanceof LivingThing living && id.equals(entity.getId())) {
                exact.add(living);
            }
        }
        if (!exact.isEmpty()) {
            return exact;
        }
        List<LivingThing> loose = new ArrayList<>();
        int matches = 0;
        for (Entity entity : World.getEntityList()) {
            if (!(entity instanceof LivingThing living)) {
                continue;
            }
            String registered = entity.getId();
            if (registered == null || !World.shortIdOf(registered).equals(id)) {
                continue;
            }
            matches++;
            if (loose.isEmpty()) {
                loose.add(living);
            }
        }
        if (matches == 1) {
            return loose;
        }
        if (matches > 1) {
            report.error("实体「" + id + "」是短名，注册表里有 " + matches + " 条 id 以它结尾"
                    + " → 请写完整 id（例如 game_official_content:" + id + "）");
            return Collections.emptyList();
        }
        report.error("注册表里没有实体「" + id + "」（它可能被改名了，或者属于一个没加载的模组）");
        return Collections.emptyList();
    }

    /**
     * @param owner 实体模板
     * @return 这个模板控制器里的技能（没有控制器时返回空表）
     */
    private static List<Skill> skillsOf(LivingThing owner) {
        if (owner.getController() == null || owner.getController().getSkills() == null) {
            return Collections.emptyList();
        }
        return owner.getController().getSkills();
    }

    /**
     * 技能注册表里<b>挂在这只模板名下</b>的技能原型（{@code Mod#addSkill(owner, skill)}）。
     * <p>
     * 觉醒技能这一类是技能自己（{@code UltimateAttack}）在变身那一刻现场造出来的，
     * <b>不在任何控制器里</b>，配置沿"实体 → 控制器 → 技能"看不到它们；
     * 登记成原型之后就有了两个落点，这条是"配置怎么配到它们"的那一条。
     * 键与控制器技能<b>共用一套</b>（{@code <实体完整id>#<技能名>}），所以没有新的键格式。
     *
     * @param owner 实体模板
     * @return 归属这只模板的原型（顺序 = 登记顺序）
     */
    public static List<Skill> prototypesOf(LivingThing owner) {
        List<Skill> found = new ArrayList<>();
        if (owner == null) {
            return found;
        }
        for (Skill prototype : World.getSkillList()) {
            if (World.skillOwnerOf(prototype) == owner) {
                found.add(prototype);
            }
        }
        return found;
    }

    /* ------------------------------------------------------------------
     * 补一个技能
     * ------------------------------------------------------------------ */

    /**
     * 把一条技能补丁打进技能实例。
     * <p>
     * 顺序无所谓（这几个字段互不影响），但写死成一个固定顺序，方便报告可读。
     *
     * @param target 目标技能
     * @param patch  补丁对象
     * @param report 报告
     * @param key    配置里的键（报错用）
     */
    public static void patch(Skill target, JSONObject patch, Report report, String key) {
        // 标量键全部交给通用补丁器（顺序 = SkillKeySpecs.SCALARS 那张表的顺序）
        SpecPatcher.patch(target, patch, SkillKeySpecs.SCALARS, report, key);
        applyCoefficients(target, patch, report, key);
        applyConsumedMana(target, patch, report, key);
        applyTags(target, patch, report, key);
        reportUnknownKeys(patch, key, report, target);
    }

    /**
     * 应用子类自己的<b>具名系数</b>（{@link SkillCoefficientTunable}）。
     * <p>
     * 键是<b>扁平</b>写在技能对象里的（与 {@code aims} / {@code atkMagnification} 并列），
     * 名字由技能自己报出来 —— 于是"一个技能多个具名系数"既不需要新的块名，
     * 也不需要在这张键表里登记：某个技能认识哪些系数，由它自己的
     * {@link SkillCoefficientTunable#coefficientValues()} 说了算。
     * <p>
     * <b>整数旋钮（{@link NumericSkillTunable}）保持老口径</b>：它那个键必须写整数，
     * 写小数照旧报"类型不对"。两个接口本就是同一条路（前者继承后者），
     * 这里只是把那个键从通用分支里摘出来，免得同一个键被写两遍、报两遍。
     *
     * @param target 目标技能
     * @param patch  补丁
     * @param report 报告
     * @param key    配置里的键
     */
    private static void applyCoefficients(Skill target, JSONObject patch, Report report, String key) {
        if (!(target instanceof SkillCoefficientTunable tunable)) {
            return;
        }
        Set<String> intKnobKeys = new LinkedHashSet<>();
        if (target instanceof NumericSkillTunable intKnob) {
            intKnobKeys.add(intKnob.extraNumericKey());
            applyExtraNumeric(target, patch, report, key);
        }
        for (Map.Entry<String, Double> entry : tunable.coefficientValues().entrySet()) {
            String name = entry.getKey();
            if (intKnobKeys.contains(name) || !patch.has(name)) {
                continue;
            }
            Object raw = patch.opt(name);
            Double value = EntityDataPatcher.asDouble(raw);
            if (value == null) {
                report.skipped(key, name, "类型不对（要数字，实际是 " + EntityDataPatcher.describe(raw) + "）");
                continue;
            }
            tunable.setCoefficientValue(name, value);
            report.applied(key, name, entry.getValue() + " → " + value);
        }
    }

    /**
     * 应用子类的<b>整数</b>额外数值旋钮（{@link NumericSkillTunable}）。
     * <p>
     * 只有实现了那个接口的技能才认这个键 —— 别的技能写了它会被
     * {@link #reportUnknownKeys} 点名，而不是静默忽略。
     * <p>
     * <b>类型口径</b>：必须是<b>整数值</b>（{@code 90} 与 {@code 90.0} 都认，{@code 90.5} 报"类型不对"）。
     * 这里刻意不用 {@code EntityDataPatcher.asLong}：{@code org.json} 把带小数点的数读成
     * {@code BigDecimal}，而 {@code asLong} 只认 {@code Double}/{@code Float} 形式的整数值。
     *
     * @param target 目标技能
     * @param patch  补丁
     * @param report 报告
     * @param key    配置里的键
     */
    private static void applyExtraNumeric(Skill target, JSONObject patch, Report report, String key) {
        if (!(target instanceof NumericSkillTunable tunable)) {
            return;
        }
        String name = tunable.extraNumericKey();
        if (!patch.has(name)) {
            return;
        }
        Object raw = patch.opt(name);
        // 用 asDouble 而不是 asLong：org.json 把带小数点的数读成 BigDecimal，
        // 而 KeySpec.asLong 只认 Double/Float 形式的整数值 —— 那会让 90.0 被误报成"类型不对"。
        // 判定口径不变：必须是整数值（90 与 90.0 都行，90.5 照旧拒绝）。
        Double value = EntityDataPatcher.asDouble(raw);
        if (value == null || Double.isInfinite(value) || value != Math.floor(value)
                || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            report.skipped(key, name, "类型不对（要整数，实际是 " + EntityDataPatcher.describe(raw) + "）");
            return;
        }
        tunable.setExtraNumericValue(value.intValue());
        report.applied(key, name, String.valueOf(value.longValue()));
    }

    /**
     * 应用消耗块 {@code {"amount":10,"element":"UNIVERSAL"}}。
     * <p>
     * <b>三种写法三种语义</b>（与 {@code org.json} 的"缺键 / null / 对象"区分一致）：
     * <ul>
     *     <li><b>不写这个键</b> → 保持构造器里的消耗，一个字都不动；</li>
     *     <li><b>写成 {@code null}</b> → 取消消耗（{@code setConsumedMana(null)}；
     *     默认文件里"不消耗法力"的技能就是这么写的，例如盗火行者那一族）；</li>
     *     <li><b>写成对象</b> → 按块里的数量与元素造一个 {@link Mana}。
     *     {@code Mana} 的构造语义是"传进来的 amount 同时当初始值与上限"，这里照那个语义造。</li>
     * </ul>
     *
     * @param target 目标技能
     * @param patch  补丁
     * @param report 报告
     * @param key    配置里的键
     */
    private static void applyConsumedMana(Skill target, JSONObject patch, Report report, String key) {
        if (!patch.has(SECTION_MANA)) {
            return;
        }
        if (patch.isNull(SECTION_MANA)) {
            target.setConsumedMana(null);
            report.applied(key, SECTION_MANA, "取消消耗");
            return;
        }
        JSONObject mana = patch.optJSONObject(SECTION_MANA);
        if (mana == null) {
            report.skipped(key, SECTION_MANA,
                    "它不是对象（该是 {amount:数字, element:元素名}；想取消消耗请写 null）");
            return;
        }
        Object rawAmount = mana.opt(DataKeys.SkillKeys.MANA_AMOUNT);
        Double amount = EntityDataPatcher.asDouble(rawAmount);
        if (amount == null) {
            report.skipped(key, SECTION_MANA + SECTION_SEPARATOR + DataKeys.SkillKeys.MANA_AMOUNT,
                    "类型不对（要数字，实际是 " + EntityDataPatcher.describe(rawAmount) + "）");
            return;
        }
        Object rawElement = mana.opt(DataKeys.SkillKeys.MANA_ELEMENT);
        if (!(rawElement instanceof String text)) {
            report.skipped(key, SECTION_MANA + SECTION_SEPARATOR + DataKeys.SkillKeys.MANA_ELEMENT,
                    "类型不对（要元素名字符串，实际是 " + EntityDataPatcher.describe(rawElement) + "）");
            return;
        }
        ElementSort element = EntityDataPatcher.elementOf(text);
        if (element == null) {
            report.skipped(key, SECTION_MANA + SECTION_SEPARATOR + DataKeys.SkillKeys.MANA_ELEMENT,
                    "没有这个元素（可用：" + EntityDataPatcher.elementNames() + "）");
            return;
        }
        target.setConsumedMana(new Mana(amount, element));
        report.applied(key, SECTION_MANA, amount + " " + element.name());
    }

    /**
     * 应用 AI 权重表 {@code {"ATTACK":5}}。
     * <p>
     * <b>整块覆盖</b>：写了这个键，技能原来的 tags 就被整表替换 ——
     * 逐项合并会让"想删掉一个 tag"变得做不到。
     *
     * @param target 目标技能
     * @param patch  补丁
     * @param report 报告
     * @param key    配置里的键
     */
    private static void applyTags(Skill target, JSONObject patch, Report report, String key) {
        if (!patch.has(DataKeys.SkillKeys.WEIGHT_TAGS)) {
            return;
        }
        JSONObject tags = patch.optJSONObject(DataKeys.SkillKeys.WEIGHT_TAGS);
        if (tags == null) {
            report.skipped(key, DataKeys.SkillKeys.WEIGHT_TAGS, "它不是对象（该是 {标签类型:权重}）");
            return;
        }
        Map<TagType, Tag> parsed = new EnumMap<>(TagType.class);
        boolean any = false;
        for (String tagName : tags.keySet()) {
            TagType type = tagTypeOf(tagName);
            if (type == null) {
                report.skipped(key, DataKeys.SkillKeys.WEIGHT_TAGS + SECTION_SEPARATOR + tagName,
                        "没有这个标签类型（可用：" + tagTypeNames() + "）");
                continue;
            }
            Object raw = tags.opt(tagName);
            Double weight = EntityDataPatcher.asDouble(raw);
            if (weight == null) {
                report.skipped(key, DataKeys.SkillKeys.WEIGHT_TAGS + SECTION_SEPARATOR + tagName,
                        "权重要数字，实际是 " + EntityDataPatcher.describe(raw));
                continue;
            }
            parsed.put(type, new Tag(weight));
            any = true;
        }
        if (!any) {
            return;
        }
        target.setTags(parsed);
        report.applied(key, DataKeys.SkillKeys.WEIGHT_TAGS, parsed.size() + " 项");
    }

    /**
     * 扫一遍补丁里有没有"这个加载器根本不认识"的键。
     * <p>
     * 逐键处理时只读自己认识的那些，认不出来的会被静默略过 —— 那正是
     * "用户改了没反应、控制台什么都不说"的老毛病，所以这里补一次显式点名。
     * <p>
     * <b>具名系数要放行</b>：它们不在静态表里（每个技能类一套名字，表里放不下），
     * 所以判据是"静态表认识 <b>或</b> 这个技能自己声明了它"——
     * 于是把 {@code hitsPerScourge} 写到不认识它的技能上，照样会被点名。
     *
     * @param patch  技能补丁
     * @param key    配置里的键
     * @param report 报告
     * @param target 目标技能（拿它声明的具名系数名当白名单）
     */
    private static void reportUnknownKeys(JSONObject patch, String key, Report report, Skill target) {
        Set<String> declared = new LinkedHashSet<>();
        if (target instanceof SkillCoefficientTunable tunable) {
            declared.addAll(tunable.coefficientValues().keySet());
        }
        for (String name : patch.keySet()) {
            if (isKnown(name) || declared.contains(name)) {
                continue;
            }
            report.skipped(key, name, "未知键（它不属于技能数值，已经忽略）");
        }
    }

    /**
     * @param name 键
     * @return 这个键是不是技能数值里认识的那几个
     * <p>
     * <b>D7 结案</b>：它曾经是一串手写的 {@code X.equals(name)}，与
     * {@link #patch} 里手写的 {@code applyXxx} 是两张平行表 —— 加一个技能键要同时改两处，
     * 漏改这里会得到"写了它却说未知键"的假报错。现在两者都只看 {@link SkillKeySpecs} 那一张表。
     */
    public static boolean isKnown(String name) {
        return SkillKeySpecs.isKnown(name);
    }

    /**
     * @param tagName 配置里写的标签名
     * @return 标签类型；认不出来返回 {@code null}（调用方只跳过这一项）
     */
    private static TagType tagTypeOf(String tagName) {
        if (tagName == null) {
            return null;
        }
        for (TagType type : TagType.values()) {
            if (type.name().equalsIgnoreCase(tagName.trim())) {
                return type;
            }
        }
        return null;
    }

    /**
     * @return 全部可用的标签名（报错时提示用）
     */
    private static String tagTypeNames() {
        StringBuilder builder = new StringBuilder();
        for (TagType type : TagType.values()) {
            if (builder.length() > 0) {
                builder.append(" / ");
            }
            builder.append(type.name().toLowerCase());
        }
        return builder.toString();
    }

    /* ------------------------------------------------------------------
     * 自测用的读取与对比
     * ------------------------------------------------------------------ */

    /**
     * 说明一个技能"现在长什么样"（供自测对比"补丁前后是否一致"）。
     * <p>
     * 覆盖补丁器会碰到的<b>全部</b>字段，少一项自测就会漏掉一类"改了没反应"。
     * <b>不含</b> {@code nowCoolDown} 与 {@code extraDamage} —— 它们是运行期状态，配置管不到。
     *
     * @param skill 技能；{@code null} 返回空串
     * @return 文本形式的状态
     */
    public static String stateOf(Skill skill) {
        if (skill == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("name=").append(skill.getName())
                .append("|aims=").append(skill.getAims())
                .append("|coolDown=").append(skill.getCoolDown())
                .append("|hpMag=").append(skill.getHpMagnification())
                .append("|atkMag=").append(skill.getAtkMagnification())
                .append("|defMag=").append(skill.getDefMagnification())
                .append("|forEnemies=").append(skill.isForEnemies())
                .append("|mana=");
        Mana mana = skill.getConsumedMana();
        builder.append(mana == null ? "null"
                : mana.getAmount() + "/" + mana.getAmountMax() + "@" + mana.getElementSort());
        builder.append("|tags=");
        List<String> names = new ArrayList<>();
        for (Map.Entry<TagType, Tag> entry : skill.getTags().entrySet()) {
            names.add(entry.getKey().name() + ":" + entry.getValue().getWeight());
        }
        Collections.sort(names);
        builder.append(names);
        builder.append("|coefficients=");
        if (skill instanceof SkillCoefficientTunable tunable) {
            builder.append(tunable.coefficientValues());
        } else {
            builder.append("[]");
        }
        return builder.toString();
    }

    /**
     * 按 {@code 实体完整id#技能名} 取一个技能实例（自测用）。
     *
     * @param key 键
     * @return 技能；找不到返回 {@code null}
     */
    public static Skill find(String key) {
        return find(key, new Report("查找"));
    }

    /**
     * 按 {@code 实体完整id#技能名} 取一个技能实例。
     *
     * @param key    键
     * @param report 报告（记录"找不到"）
     * @return 技能；找不到返回 {@code null}
     */
    public static Skill find(String key, Report report) {
        List<Skill> found = targetsOf(key, report);
        return found.isEmpty() ? null : found.get(0);
    }

    /**
     * 把技能表里每个技能的 {@code 实体id#技能名} 列出来（自测与报错提示用）。
     *
     * @return 键 → 技能实例（顺序 = 注册表顺序）
     */
    public static Map<String, Skill> all() {
        Map<String, Skill> result = new java.util.LinkedHashMap<>();
        for (Entity entity : World.getEntityList()) {
            if (!(entity instanceof LivingThing living)) {
                continue;
            }
            String entityId = entity.getId();
            if (entityId == null || entityId.isEmpty()) {
                continue;
            }
            for (Skill skill : skillsOf(living)) {
                if (skill != null) {
                    result.putIfAbsent(entityId + DataKeys.SKILL_SEPARATOR + skill.getName(), skill);
                }
            }
            for (Skill prototype : prototypesOf(living)) {
                result.putIfAbsent(entityId + DataKeys.SKILL_SEPARATOR + prototype.getName(), prototype);
            }
        }
        return result;
    }

    /**
     * 一次性解析 + 应用（给自测用的入口）。
     *
     * @param json 配置文本
     * @param note 来源说明
     * @return 报告；JSON 本身解析不了时返回一个只带错误的报告
     */
    public static Report applyJson(String json, String note) {
        try {
            return apply(new JSONObject(json), note);
        } catch (JSONException e) {
            Report report = new Report(note);
            report.error("不是合法的 JSON：" + e.getMessage());
            return report;
        }
    }


    /* ------------------------------------------------------------------
     * 报告
     * ------------------------------------------------------------------ */

    /**
     * 一个技能的完整快照：把补丁器会碰的每个字段原样记下来，之后能原样放回去。
     * <p>
     * 存在的理由是<b>自测要能安全地拿模板上的真技能试补丁</b>：只测副本证明不了
     * "模板会被改对"，而模板上的技能又是"选人时被 {@code copy()} 出去的那一份"，
     * 改坏了会影响后面所有用例。
     *
     * @param skill            被快照的技能
     * @param aims             目标数
     * @param coolDown         总冷却
     * @param hpMagnification  生命倍率
     * @param atkMagnification 攻击倍率
     * @param defMagnification 防御倍率
     * @param forEnemies       默认作用对象
     * @param manaAmount       消耗数量（没有消耗时为 {@code null}）
     * @param manaElement      消耗元素（没有消耗时为 {@code null}）
     * @param tags             权重表（复制一份）
     * @param coefficients     具名系数（名字 → 值；不支持具名系数的技能是空表）
     * @author AI（DeepSeek）生成
     */
    public record SkillSnapshot(Skill skill, int aims, int coolDown, double hpMagnification,
                                double atkMagnification, double defMagnification, boolean forEnemies,
                                Double manaAmount, ElementSort manaElement, Map<TagType, Tag> tags,
                                Map<String, Double> coefficients) {

        /**
         * 给一个技能拍快照。
         *
         * @param skill 技能
         * @return 快照
         */
        public static SkillSnapshot of(Skill skill) {
            Mana mana = skill.getConsumedMana();
            Map<TagType, Tag> tags = new EnumMap<>(TagType.class);
            for (Map.Entry<TagType, Tag> entry : skill.getTags().entrySet()) {
                tags.put(entry.getKey(), entry.getValue().copy());
            }
            Map<String, Double> coefficients = new java.util.LinkedHashMap<>();
            if (skill instanceof SkillCoefficientTunable tunable) {
                coefficients.putAll(tunable.coefficientValues());
            }
            return new SkillSnapshot(skill, skill.getAims(), skill.getCoolDown(),
                    skill.getHpMagnification(), skill.getAtkMagnification(), skill.getDefMagnification(),
                    skill.isForEnemies(), mana == null ? null : mana.getAmount(),
                    mana == null ? null : mana.getElementSort(), tags, coefficients);
        }

        /**
         * 把技能恢复成拍快照时的样子。
         */
        public void restore() {
            if (skill == null) {
                return;
            }
            skill.setAims(aims);
            skill.setCoolDown(coolDown);
            skill.setHpMagnification(hpMagnification);
            skill.setAtkMagnification(atkMagnification);
            skill.setDefMagnification(defMagnification);
            skill.setForEnemies(forEnemies);
            if (manaAmount == null || manaElement == null) {
                skill.setConsumedMana(null);
            } else {
                skill.setConsumedMana(new Mana(manaAmount, manaElement));
            }
            Map<TagType, Tag> copy = new EnumMap<>(TagType.class);
            for (Map.Entry<TagType, Tag> entry : tags.entrySet()) {
                copy.put(entry.getKey(), entry.getValue().copy());
            }
            skill.setTags(copy);
            if (skill instanceof SkillCoefficientTunable tunable) {
                for (Map.Entry<String, Double> entry : coefficients.entrySet()) {
                    tunable.setCoefficientValue(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    /**
     * 一次技能加载的结果：应用了几项、跳过了几项、为什么。
     * <p>
     * 与 {@link EntityDataPatcher.Report} 同一口径：一个坏键不许废掉整份配置，
     * 但也不能静默 —— 控制台上必须说清"读了几项、跳过几项、各是为什么"。
     *
     * @author AI（DeepSeek）生成
     */
    public static final class Report implements SpecPatcher.Sink {

        /**
         * 这一批配置的来源说明。
         */
        private final String note;
        /**
         * 已经打过补丁的技能（按对象身份判重，保证"每个技能只补一次"）。
         */
        private final Set<Skill> bound = Collections.newSetFromMap(new IdentityHashMap<>());
        /**
         * 应用成功的条目。
         */
        private final List<String> applied = new ArrayList<>();
        /**
         * 被跳过的条目。
         */
        private final List<String> skipped = new ArrayList<>();
        /**
         * 整份配置层面的问题（文件坏了、目标找不到……）。
         */
        private final List<String> errors = new ArrayList<>();
        /**
         * 打过补丁的技能数。
         */
        private int patchedSkills = 0;

        /**
         * @param note 来源说明
         */
        public Report(String note) {
            this.note = note;
        }

        /**
         * @param key    配置里的键
         * @param target 技能
         * @return 这一轮里这个技能是不是第一次被补（{@code false} = 重复，调用方应当跳过）
         */
        public boolean markBound(String key, Skill target) {
            if (!bound.add(target)) {
                return false;
            }
            patchedSkills++;
            return true;
        }

        /**
         * @param key  配置里的键
         * @param path 键路径
         * @param what 做了什么
         */
        public void applied(String key, String path, String what) {
            applied.add(key + " → " + path + " = " + what);
        }

        /**
         * @param key    配置里的键
         * @param path   键路径
         * @param reason 为什么跳过
         */
        public void skipped(String key, String path, String reason) {
            skipped.add(key + " → " + path + "：" + reason);
        }

        /**
         * @param message 问题描述
         */
        public void error(String message) {
            errors.add(message);
        }

        /**
         * @return 打过补丁的技能数
         */
        public int patchedSkills() {
            return patchedSkills;
        }

        /**
         * @return 应用成功的条目
         */
        public List<String> appliedEntries() {
            return Collections.unmodifiableList(applied);
        }

        /**
         * @return 被跳过的条目
         */
        public List<String> skippedEntries() {
            return Collections.unmodifiableList(skipped);
        }

        /**
         * @return 整份配置层面的问题
         */
        public List<String> errors() {
            return Collections.unmodifiableList(errors);
        }

        /**
         * @return 是否一切正常（没有错误、也没有跳过项）
         */
        public boolean isClean() {
            return errors.isEmpty() && skipped.isEmpty();
        }

        /**
         * 把结果打到控制台：<b>错在哪里必须一眼看得见</b>（永远打），
         * "一切正常"的汇总行只在 verbose 时打（默认静默，见 {@link ConfigOutput}）。
         */
        public void print() {
            for (String message : errors) {
                ConfigOutput.problem("[配置错误] " + note + "：" + message);
            }
            for (String message : skipped) {
                ConfigOutput.problem("[配置跳过] " + note + "：" + message);
            }
            if (isClean()) {
                ConfigOutput.info("[配置] " + note + "：应用 " + applied.size() + " 项，跳过 0 项，"
                        + "影响 " + patchedSkills + " 个技能");
            } else {
                ConfigOutput.info("[配置] " + note + "：应用 " + applied.size() + " 项，跳过 "
                        + (skipped.size() + errors.size()) + " 项（上面已逐条写明原因），"
                        + "影响 " + patchedSkills + " 个技能");
            }
            // 留一笔账给"全部配置加载完"之后那一行总量汇总（见 ConfigOutput#printPatchSummary）
            ConfigOutput.patchApplied(note, applied.size(), skipped.size(), errors.size(),
                    patchedSkills, "个技能");
        }
    }
}
