package cn.gfhnv.game.system.configLoadingSystem;

import cn.gfhnv.game.entity.Entity;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.inventory.Slot;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.mana.Mana;
import cn.gfhnv.game.world.World;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

/**
 * 实体数据补丁器：把 {@code config/gameConfig/EntityData.json} 里<b>显式写出来</b>的键，
 * 打到 {@link World} 注册表里的实体<b>模板</b>上。
 * <p>
 * <b>三条实现纪律（每一条都对应一个真实会踩的坑）</b>：
 * <ol>
 *     <li><b>每个模板只补一次</b>：补丁里会走到 {@code setLevel} → {@code initialMana()}，
 *     而 {@code initialMana()} 会把整份法力列表重建。同一个模板补两次 = 打到一半的法力被重置；</li>
 *     <li><b>顺序固定</b>：{@code level} → 面板属性 → 背包格数 →
 *     {@code derived.hpMax/attack/defence} → {@code derived.hp}（<b>{@code hp} 必须最后</b>，
 *     因为 {@code setHp} 会夹到 {@code getHpMax()}）；</li>
 *     <li><b>逐项容错</b>：未知键 / 类型不对 / 目标不存在都不中断加载，
 *     全部收集进 {@link Report}，结尾汇总打印。</li>
 * </ol>
 * <b>派生值重算</b>：{@code setHpGrow} / {@code setAttackGrow} / {@code setDefenceGrow}
 * 只赋值、不重算三围，只有 {@code Entity#setLevel} 会重算 ——
 * 所以只写成长系数（没写 {@code level}）时补丁器会自己补一次
 * {@code setLevel(当前等级)}，否则用户会得到"改了没反应"。
 * <p>
 * <b>覆盖边界</b>：补丁只作用在注册表模板上，所以"运行时临时 {@code new} 出来的实例"
 * （例如盗火行者现场召唤的容器按召唤者的生命/攻击算出来的那一份）覆盖不到 ——
 * 那是设计，不是缺陷。
 *
 * @author AI（DeepSeek）生成
 */
public final class EntityDataPatcher {

    /**
     * 元素的法力成长汇总块名。
     */
    private static final String SECTION_MANA_GROW = DataKeys.MANA_GROW_BLOCK;
    /**
     * 面板属性键的前缀（报错与统计里显示成 {@code base.level}）。
     */
    private static final String BASE_PREFIX = DataKeys.BASE + DataKeys.SECTION_SEPARATOR;

    /**
     * 工具类，不允许实例化。
     */
    private EntityDataPatcher() {
    }

    /* ------------------------------------------------------------------
     * 入口
     * ------------------------------------------------------------------ */

    /**
     * 应用一段已经解析好的实体数据。
     *
     * @param root 根对象（形如 {@code {"version":1,"entities":{…}}}）
     * @param note 这一批配置的来源说明（写进日志与 {@link Report}）
     * @return 报告
     */
    public static Report apply(JSONObject root, String note) {
        Report report = new Report(note);
        if (root == null) {
            report.error("根不是 JSON 对象");
            return report;
        }
        JSONObject entities = root.optJSONObject(DataKeys.ENTITIES);
        if (entities == null) {
            if (root.has(DataKeys.ENTITIES)) {
                report.error("「" + DataKeys.ENTITIES + "」不是对象（它该是「完整id → 补丁」的映射）");
            } else {
                report.error("缺少「" + DataKeys.ENTITIES + "」这一层（实体数据都写在它下面）");
            }
            return report;
        }
        for (String id : entities.keySet()) {
            JSONObject patch = entities.optJSONObject(id);
            if (patch == null) {
                report.error("实体「" + id + "」下面的内容不是对象（该是 {} ）");
                continue;
            }
            List<LivingThing> targets = targetsOf(id, report);
            if (targets.isEmpty()) {
                continue;
            }
            for (LivingThing target : targets) {
                if (!report.markBound(id, target)) {
                    report.error("实体「" + id + "」在注册表里有两条同样的记录，第二份已跳过"
                            + "（配置键要用完整 id，别让两个模板共用一个键）");
                    continue;
                }
                patch(target, patch, report, id);
            }
        }
        return report;
    }

    /**
     * 按 id 找注册表里的模板。<b>只用完整 id 精确匹配</b> ——
     * 官方注册了两种 {@code BrokenContainer}（残破 / 完整），按类匹配会让它们共用一份配置。
     * <p>
     * 短名（{@code brokenContainer}）当且仅当"注册表里只有一条它的 id 以这个短名结尾"时才认。
     *
     * @param id     配置里的键
     * @param report 报告（记录"找不到"）
     * @return 命中的模板（同一个模板只出现一次）
     */
    private static List<LivingThing> targetsOf(String id, Report report) {
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
        boolean ambiguous = false;
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
            } else if (loose.get(0).getClass() != living.getClass()) {
                ambiguous = true;
            }
        }
        if (matches == 1) {
            return loose;
        }
        if (matches > 1) {
            report.error("实体「" + id + "」是短名，注册表里有 " + matches + " 条 id 以它结尾"
                    + (ambiguous ? "（连类都不一样）" : "") + " → 请写完整 id（例如 game_official_content:" + id + "）");
            return Collections.emptyList();
        }
        report.error("注册表里没有实体「" + id + "」（它可能被改名了，或者属于一个没加载的模组）");
        return Collections.emptyList();
    }

    /* ------------------------------------------------------------------
     * 补一个模板
     * ------------------------------------------------------------------ */

    /**
     * 把一条实体补丁打进模板。
     * <p>
     * 顺序：{@code level} → 面板属性 → 背包格数 → {@code derived.hpMax/attack/defence} → {@code derived.hp}。
     * 成长系数只在<b>没有</b>显式写 {@code level} 时补一次 {@code setLevel} 重算
     * （写了 {@code level} 的话，第一步那次 {@code setLevel} 已经把新成长算进去了）。
     *
     * @param target 目标模板
     * @param patch  补丁对象
     * @param report 报告
     * @param id     这个模板的 id（报错用）
     */
    public static void patch(LivingThing target, JSONObject patch, Report report, String id) {
        // ① 标量键全部交给通用补丁器（顺序 = EntityKeySpecs 那张表的顺序）
        Set<String> applied = new LinkedHashSet<>(
                SpecPatcher.patch(target, patch, EntityKeySpecs.BEFORE_DERIVED, report, id));
        // ①-b 反射兜底（2026-10-03 第 3 步）：表里没有、反射面认识的新字段在这里直接生效 ——
        //      这就是"加一个字段 = 0 处配置层改动"。表已经处理过的键会被它跳过（幂等），
        //      被 @NoConfig / CLASS_RUNTIME 挡住的键也一个字都不写（照旧报「未知键」+ 为什么）。
        //      子类自己的出厂数值（DataKeys.CLASS_CONFIG，例如白厄的 coreflame）也走这一条 ——
        //      它们进不了 EntityKeySpecs 那张通用表（那一张的行要对任意 LivingThing 成立）。
        applied.addAll(ReflectionConfigBridge.patchExtra(target, patch, report, id));
        // ② 两个"块"各走专用分支：manaGrow 要把一个块拆成五个扁平键，
        //    inventorySlots 减格子时要判"末尾格子是不是空的"（不能把物品一起删掉）
        applyManaGrowBlock(target, patch, report, id);
        applyInventorySlots(target, patch, report, id);

        // ③ 只写成长系数（没写 level）时补一次重算 —— 否则用户会得到"改了没反应"
        if (!applied.contains(DataKeys.Base.LEVEL)
                && applied.stream().anyMatch(EntityDataPatcher::touchesDerivedStats)) {
            recalculateDerivedStats(target);
        }
        // ④ 派生值最后打（hp 排在 hpMax 之后，因为 setHp 会夹到 getHpMax()）
        //    ⚠️ 必须在 SpecPatcher.patch(DERIVED) **之前**调：要提醒的是"补丁里写的这个数
        //    和规则表那个数不一样"，补丁一打下去 derived.hpMax 就已经盖掉构造值了
        noticeDualEntryHpMax(target, patch, report, id);
        SpecPatcher.patch(target, patch, EntityKeySpecs.DERIVED, report, id);
        reportUnknownKeys(patch, id, report, applied);
    }

    /* ------------------------------------------------------------------
     * "一个值两个入口"：谁赢、为什么、怎么让人看见（2026-10-03）
     * ------------------------------------------------------------------ */

    /**
     * 检查<b>同一个"血量"的两个入口是不是真的对不上</b>，对不上才发一条提醒。
     * <p>
     * <b>为什么要有这一步</b>：{@code GameRules.json} 的 {@code flameReaver.baseHpMax}
     * 是<b>构造时</b>读一次的出厂血量（{@code FlameReaver.java:282-283}），而
     * {@code EntityData.json} 的 {@code derived.hpMax} 是<b>构造之后</b>打的补丁 ——
     * 两个都写、而且写的是<b>不同的数</b>时，规则表那个数看不出效果，而此前控制台一句话都不说。
     * 这正是项目最忌讳的"两处写真值，迟早一真一假"。
     * <p>
     * <b>⚠️ 判据是"两个值不一样"，不是"两个键都被写过"</b>：生成的 {@code EntityData.json}
     * 本来就<b>一定</b>包含 {@code derived.hpMax}（它是全量 dump），而 {@code GameRules.json}
     * 本来就<b>一定</b>包含 {@code flameReaver.baseHpMax}（它也是全量 dump）——
     * 所以"两个入口都写了"是<b>出厂默认状态，不是冲突</b>，按那个判据提醒等于每次启动必然误报一次
     * （用户 2026-10-03 报的就是它：那条提醒"写了约 250 字"，却什么也没告诉他）。
     * 真正想提醒的情形只有一种：<b>他动了其中一处、另一处还停在旧值</b> ——
     * 那种情形下两个数必然对不上。
     * <p>
     * <b>语义判定（谁赢）</b>：<b>{@code EntityData.json} 赢</b>。理由是它是"最后打上去的那个",
     * 而且它就是为"改一只已经造出来的模板的血"设计的（规则表那一侧只决定构造那一刻的初值）。
     * 反过来让规则表赢会破坏"补丁永远覆盖构造值"这条全项目统一的顺序。
     * 所以这里<b>不改数值</b>,只把"谁赢了、另一个为什么没生效、想让它生效该怎么办"讲清楚。
     * <p>
     * <b>覆盖范围</b>：{@code classState} 那 7 个键是<b>故意</b>挡住的
     * （{@code DataKeys.CLASS_RUNTIME} 里的 {@code damageReductionLayers} 连 setter 都没有，
     * 补丁会报「这个键不能配置」），所以"两个入口"今天只有这两个 BOSS 的血量 ——
     * 加新的构造期规则键时，请同步扩 {@link #constructionHpRuleKeyOf} 那张表。
     *
     * @param target 目标模板
     * @param patch  实体补丁
     * @param report 报告
     * @param id     这个模板的 id（报错用）
     */
    private static void noticeDualEntryHpMax(LivingThing target, JSONObject patch, Report report, String id) {
        if (target == null || patch == null) {
            return;
        }
        // ① 这次补丁里到底写了什么 derived.hpMax（裸键 hpMax 也算 —— 那两种写法加载器都认）
        SpecPatcher.KeySource source = SpecPatcher.lookup(patch, DataKeys.Derived.HP_MAX);
        if (source == null || !(source.container().opt(source.key()) instanceof Number number)) {
            return;
        }
        // ② 规则表里有没有这个模板的"构造期血量键"
        String ruleKey = constructionHpRuleKeyOf(target);
        if (ruleKey == null) {
            return;
        }
        // ③ 判据就在这一行：补丁里的值 vs 规则表生效值。相等 = 出厂默认状态 = 不吵
        //    （想验证这条断言：两份默认文件都不手工改动时不触发，只改一处时触发）
        //    两个数都按 double 比：数量级是血条，double 表示整数完全精确
        //    （JSON 里写 80000 与 80000.0 在这里是同一个数）
        double patchValue = number.doubleValue();
        double ruleValue = GameRules.getDouble(ruleKey);
        if (patchValue == ruleValue) {
            return;
        }
        String entityId = target.getId() == null || target.getId().isEmpty() ? id : target.getId();
        report.notice("实体「" + entityId + "」的血量两个入口对不上："
                + "EntityData.json 的「" + source.prefix() + DataKeys.Derived.HP_MAX + "」"
                + "= " + render(patchValue)
                + "，GameRules.json 的「" + ruleKey + "」= " + render(ruleValue) + "。"
                + "「" + source.prefix() + DataKeys.Derived.HP_MAX + "」赢 —— 它在构造之后打补丁，"
                + "把构造时读到的那个值整个盖掉，所以你在规则表里改「" + ruleKey + "」看不出效果。"
                + "出厂默认时两个入口写的就是同一个数，这里不吵；"
                + "只有你把其中一处改了、另一处还停在旧值时才提醒。"
                + "想改这只模板的血请只留一处：改 / 删「" + source.prefix() + DataKeys.Derived.HP_MAX
                + "」（推荐），或者把「" + ruleKey + "」改回 " + render(RuleKeySpecs.number(ruleKey))
                + " 并删掉这个派生键。");
    }

    /**
     * 把血量印成<b>人看得懂的样子</b>（整数不拖小数点，比如 {@code 80000} 而不是 {@code 80000.0}）。
     *
     * @param value 数值
     * @return 文本
     */
    private static String render(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    /**
     * @param target 模板
     * @return 这个模板的"构造期血量规则键"（{@link GameRules} 里那个只在构造时读一次的键）；
     * 这个类没有这样的键时返回 {@code null}
     * <p>
     * <b>公开只给自测用</b>：自测拿它去核对"这个模板的出厂血量在规则表里是几"，
     * 从而在不读用户真实文件的前提下钉住"默认不触发提醒"这条断言。
     */
    public static String constructionHpRuleKeyOf(LivingThing target) {
        if (target instanceof cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver) {
            return DataKeys.Rule.FlameReaver.BASE_HP_MAX;
        }
        if (target instanceof cn.gfhnv.game.officialStuff.customEntity.monsters.InsectBoss) {
            return DataKeys.Rule.InsectBoss.BASE_HP;
        }
        return null;
    }

    /**
     * 按当前的等级与成长系数重算三围（面板属性）。
     * <p>
     * <b>为什么不直接调 {@code Entity#setLevel}</b>：那个方法里的顺序是
     * {@code setHp(新基础值)} → {@code setHpMax(getHp())}，而 {@code LivingThing#setHp} 会
     * <b>夹到"当前"的 {@code getHpMax()}</b> —— 于是它写的永远是旧上限，改了成长系数也看不到变化
     * （实测：125 级玩家一写 {@code hpGrow:50} 之后 {@code hpMax} 仍是 4664 而不是 6400）。
     * 这里按同一套公式先写 {@code hpMax} 再写当前血量，语义还更稳：
     * <b>当前血量只会因为上限变小被夹下来，不会因为上限变大而被补满</b>。
     * <p>
     * <b>三个基准值走规则表，不再写字面量</b>：{@code 200} / {@code 110} / {@code 200} 曾经在这里
     * 又抄了一遍，于是 {@code GameRules.json} 的 {@code formula.hpBase} 在这条路径上失效 ——
     * 用户改了规则表，只写 {@code hpGrow} 的实体却还是按旧公式算。现在两边同一个来源。
     *
     * @param target 目标模板
     */
    private static void recalculateDerivedStats(LivingThing target) {
        long level = target.getLevel();
        long hpBase = GameRules.getLong(DataKeys.Rule.Formula.HP_BASE);
        long attackBase = GameRules.getLong(DataKeys.Rule.Formula.ATTACK_BASE);
        long defenceBase = GameRules.getLong(DataKeys.Rule.Formula.DEFENCE_BASE);
        long newHpMax = (long) ((level - 1) * target.getHpGrow() + hpBase);
        target.setAttack((long) (attackBase + target.getAttackGrow() * (level - 1)));
        target.setDefence((long) ((level - 1) * target.getDefenceGrow() + defenceBase));
        target.setHpMax(newHpMax);
        target.setHp(Math.min(target.getHp(), newHpMax));
        target.initialMana();
    }

    /**
     * 扫一遍补丁里有没有"这个加载器根本不认识"的键。
     * <p>
     * 逐键处理时只读自己认识的那些，认不出来的会被静默略过 ——
     * 那正是"用户改了没反应、控制台什么都不说"的老毛病，所以这里补一次显式点名。
     * <p>
     * <b>三处改进（2026-10-03）</b>：
     * <ol>
     *     <li>报「未知键」时带上 {@link DataKeys#hintFor} 的理由 ——
     *     那些"看着像能配、其实这一版刻意不支持"的键（五元素穿透/增伤、{@code extraDamage}）
     *     现在会明说为什么，而不是让用户去猜自己是不是拼错了；</li>
     *     <li>{@code "temporary"} 这个块名单独报 ——
     *     它<b>不是</b>配置块（{@code BARE_SECTIONS} 是 {@code base} / {@code derived} /
     *     {@code classState}），
     *     以前会被报成"未知键（这个键不属于实体数据）"，而用户明明是照文档写的；</li>
     *     <li>{@code manaGrow} 块单独放行（它由 {@link #applyElementValues} 逐元素读）。</li>
     * </ol>
     *
     * @param patch   实体补丁
     * @param id      模板 id
     * @param report  报告
     * @param handled 已经被处理过的数据名（手写表 + 反射兜底；这些键不该再被报成「未知键」）
     */
    private static void reportUnknownKeys(JSONObject patch, String id, Report report, Set<String> handled) {
        for (String key : patch.keySet()) {
            if (DataKeys.BARE_SECTIONS.contains(key)) {
                JSONObject section = patch.optJSONObject(key);
                if (section == null) {
                    report.skipped(id, key, "它不是对象（{@code " + key + "} 里该是「键:值」）");
                    continue;
                }
                for (String inner : section.keySet()) {
                    if (DataKeys.Base.MANA_GROW.equals(inner)) {
                        continue;
                    }
                    if (handled.contains(inner)) {
                        continue;
                    }
                    if (!DataKeys.isConfigurable(inner)) {
                        report.skipped(id, key + DataKeys.SECTION_SEPARATOR + inner,
                                unknownReason(inner));
                    }
                }
                continue;
            }
            if (SECTION_MANA_GROW.equals(key)) {
                continue;
            }
            if (DataKeys.GROUP_TEMPORARY.equals(key)) {
                report.skipped(id, key, "「" + DataKeys.GROUP_TEMPORARY + "」不是一个配置块："
                        + "实体补丁只认 " + DataKeys.BARE_SECTIONS + " 三个子块（或直接写裸键）。"
                        + "面板属性（暴击率 / 暴击伤害 / 增强 / 穿透 / 防御削减）请写在 base 里；"
                        + "战中的临时加成请用效果与技能改。");
                continue;
            }
            if (!DataKeys.Alias.ELEMENT.equals(key) && !DataKeys.isConfigurable(key)
                    && !handled.contains(key)) {
                report.skipped(id, BASE_PREFIX + key, unknownReason(key));
            }
        }
    }

    /**
     * @param key 用户写的键
     * @return 「未知键」的完整理由（有专门说明时附上，见 {@link DataKeys#hintFor}）
     */
    private static String unknownReason(String key) {
        String hint = DataKeys.hintFor(key);
        return hint == null
                ? "未知键（这个键不属于实体数据，已经忽略）"
                : "这个键不能配置（已经忽略）：" + hint;
    }

    /**
     * 应用 {@code manaGrow} 块：{@code {"manaGrow":{"fire":20}}} 等价于 {@code {"fireManaGrow":20}}。
     * <p>
     * 一个块要拆成五个扁平键，写不进"一行一个键"的标量表，所以留一个专用分支；
     * 但它内部的元素名认不认、值是不是数字，仍然只有一处实现
     * （{@link EntityKeySpecs#setManaGrow}）。
     * <p>
     * 五个<b>扁平</b>的 {@code *ManaGrow} 键走通用补丁器 —— 它们以前声明了"能配置"、
     * 生成器也真的写进默认文件，但补丁器从来没读过（写进配置一点反应都没有）。
     *
     * @param target 目标模板
     * @param patch  补丁
     * @param report 报告
     * @param id     模板 id
     */
    private static void applyManaGrowBlock(LivingThing target, JSONObject patch, Report report, String id) {
        if (patch.has(DataKeys.MANA_GROW_BLOCK) && patch.optJSONObject(DataKeys.MANA_GROW_BLOCK) == null) {
            report.skipped(id, DataKeys.MANA_GROW_BLOCK,
                    "它不是对象（" + DataKeys.MANA_GROW_BLOCK + " 里该是「元素:成长系数」）");
            return;
        }
        SpecPatcher.KeySource source = SpecPatcher.lookupManaGrowBlock(patch);
        if (source == null) {
            return;
        }
        for (String element : source.container().keySet()) {
            String path = source.prefix() + SECTION_MANA_GROW + DataKeys.SECTION_SEPARATOR + element;
            String canonical = element.toLowerCase();
            if (!DataKeys.ELEMENTS.contains(canonical)) {
                report.skipped(id, path, "认不出这个元素（可用：" + DataKeys.ELEMENTS + "）");
                continue;
            }
            Object raw = source.container().opt(element);
            Double value = KeySpec.asDouble(raw);
            if (value == null) {
                report.skipped(id, path, "类型不对（要数字，实际是 " + KeySpec.describe(raw) + "）");
                continue;
            }
            EntityKeySpecs.setManaGrow(target, canonical, value);
            report.applied(id, path + " → " + canonical + "ManaGrow", String.valueOf(value));
        }
    }

    /**
     * @param appliedKey {@code "实体id/键路径"} 形式的应用记录（例如
     *                   {@code game_official_content:playerOne/base.hpGrow}）
     * @return 这个键是否会影响派生值（三个成长系数）
     */
    private static boolean touchesDerivedStats(String appliedKey) {
        String path = appliedKey;
        int slash = path.lastIndexOf('/');
        if (slash >= 0) {
            path = path.substring(slash + 1);
        }
        int dot = path.lastIndexOf('.');
        String key = dot >= 0 ? path.substring(dot + 1) : path;
        return DataKeys.Base.HP_GROW.equals(key)
                || DataKeys.Base.ATTACK_GROW.equals(key)
                || DataKeys.Base.DEFENCE_GROW.equals(key);
    }

    /**
     * 应用背包格数。
     * <p>
     * {@code /data} 里没有这个键 —— 背包格数由 {@code Inventory} 里的格子元素表达。
     * 加格子用 {@code addSlot}；减格子只从<b>末尾</b>开始删，遇到装着东西的格子就停下并报告
     * （不会把玩家的物品删掉）。
     *
     * @param target 目标模板
     * @param patch  补丁
     * @param report 报告
     * @param id     模板 id
     */
    private static void applyInventorySlots(LivingThing target, JSONObject patch, Report report, String id) {
        SpecPatcher.KeySource source = SpecPatcher.lookup(patch, DataKeys.Base.INVENTORY_SLOTS);
        Long wanted = source == null ? null : asLong(source.container().opt(source.key()));
        if (wanted == null) {
            if (source != null) {
                report.skipped(id, source.prefix() + DataKeys.Base.INVENTORY_SLOTS,
                        "类型不对（要整数，实际是 " + describe(source.container().opt(source.key())) + "）");
            }
            return;
        }
        String path = source.prefix() + DataKeys.Base.INVENTORY_SLOTS;
        long target64 = wanted;
        if (target64 < 0) {
            report.skipped(id, path, "背包格数不能是负数");
            return;
        }
        long current = target.getInventory().getSlots().size();
        if (current == target64) {
            report.applied(id, path, String.valueOf(target64));
            return;
        }
        if (current < target64) {
            target.getInventory().addSlot(target64 - current);
        } else {
            long toRemove = current - target64;
            List<Slot> slots = target.getInventory().getSlots();
            for (long i = 0; i < toRemove; i++) {
                Slot last = slots.get(slots.size() - 1);
                if (last.getContainedItem() != null) {
                    report.skipped(id, path,
                            "末尾的格子(" + last.getSlotNumber() + ")里装着东西，没有删（会把物品一起删掉）");
                    return;
                }
                slots.remove(slots.size() - 1);
            }
            target.getInventory().sort();
        }
        report.applied(id, path, String.valueOf(target64));
    }

    /**
     * @param text 文本
     * @return 元素枚举；认不出来返回 {@code null}
     */
    public static ElementSort elementOf(String text) {
        return KeySpec.elementOf(text);
    }

    /**
     * @return 全部元素名（报错用）
     */
    public static String elementNames() {
        return KeySpec.elementNames();
    }

    /**
     * 说明一个模板"现在长什么样"（供自测对比"补丁前后是否一致"）。
     * <p>
     * 覆盖补丁器会碰到的<b>全部</b>字段：等级、三围、成长、五行抗性与法力成长、速度、质量、
     * 类型、名称、描述、元素、背包格数与五行法力当前值。少一项，自测就会漏掉一类"改了没反应"。
     *
     * @param entity 实体；{@code null} 返回空串
     * @return 文本形式的状态
     */
    public static String stateOf(LivingThing entity) {
        if (entity == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("level=").append(entity.getLevel())
                .append("|hp=").append(entity.getHp())
                .append("|hpMax=").append(entity.getHpMax())
                .append("|attack=").append(entity.getAttack())
                .append("|defence=").append(entity.getDefence())
                .append("|speed=").append(entity.getSpeed())
                .append("|mass=").append(entity.getMass())
                .append("|type=").append(entity.getType())
                .append("|name=").append(entity.getName())
                .append("|description=").append(entity.getDescription())
                .append("|element=").append(entity.getElementSort())
                .append("|hpGrow=").append(entity.getHpGrow())
                .append("|attackGrow=").append(entity.getAttackGrow())
                .append("|defenceGrow=").append(entity.getDefenceGrow())
                .append("|fireResistance=").append(entity.getFireResistance())
                .append("|waterResistance=").append(entity.getWaterResistance())
                .append("|metalResistance=").append(entity.getMetalResistance())
                .append("|woodResistance=").append(entity.getWoodResistance())
                .append("|dirtResistance=").append(entity.getDirtResistance())
                .append("|manaGrow=");
        for (String element : DataKeys.ELEMENTS) {
            builder.append(element).append(':').append(manaGrowOf(entity, element)).append(',');
        }
        builder.append("|slots=").append(entity.getInventory().getSlots().size()).append("|manas=");
        for (Mana mana : entity.getManas()) {
            builder.append(mana.getElementSort()).append(':').append(mana.getAmount()).append(',');
        }
        return builder.toString();
    }

    /**
     * @param entity  实体
     * @param element 元素名（小写）
     * @return 该元素的法力成长系数
     */
    private static double manaGrowOf(LivingThing entity, String element) {
        return switch (element) {
            case "metal" -> entity.getMetalManaGrow();
            case "wood" -> entity.getWoodManaGrow();
            case "water" -> entity.getWaterManaGrow();
            case "fire" -> entity.getFireManaGrow();
            default -> entity.getDirtManaGrow();
        };
    }

    /**
     * @param raw JSON 里的值
     * @return 整数值；不是整数返回 {@code null}（{@code 20.0} 这种整数值的小数也认）
     */
    public static Long asLong(Object raw) {
        return KeySpec.asLong(raw);
    }

    /* ------------------------------------------------------------------
     * 类型判定
     * ------------------------------------------------------------------ */

    /**
     * @param raw JSON 里的值
     * @return 浮点值；不是数字返回 {@code null}
     */
    public static Double asDouble(Object raw) {
        return KeySpec.asDouble(raw);
    }

    /**
     * @param raw JSON 里的值
     * @return 报错用的类型描述
     */
    public static String describe(Object raw) {
        return KeySpec.describe(raw);
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
     * 一个模板的完整快照：把补丁器会碰的每个字段原样记下来，之后能原样放回去。
     * <p>
     * 存在的理由是<b>自测要能安全地拿真模板试补丁</b>：不能用"复制一份副本"代替，
     * 因为 {@link World} 的注册表里存的就是这些模板本身，副本证明不了"真模板会被改对"。
     * <p>
     * 恢复顺序与补丁顺序一致：元素 → 面板 → 等级（触发重算）→ 派生值 → 当前血量 → 背包。
     * {@code setHp} 会夹到 {@code getHpMax()}，所以 {@code hpMax} 必须排在 {@code hp} 前面。
     *
     * @param entity      被快照的实体
     * @param element     元素属性
     * @param name        名称
     * @param type        类型
     * @param level       等级
     * @param hp          当前生命值
     * @param hpMax       生命上限
     * @param attack      基础攻击力
     * @param defence     基础防御力
     * @param speed       速度
     * @param mass        质量
     * @param description 描述（可为 {@code null}）
     * @param hpGrow      生命成长
     * @param attackGrow  攻击成长
     * @param defenceGrow 防御成长
     * @param resistance  五行抗性（顺序见 {@link DataKeys#ELEMENTS}）
     * @param manaGrow    五行法力成长（顺序同上）
     * @param slotCount   背包格数
     * @author AI（DeepSeek）生成
     */
    public record Snapshot(LivingThing entity, ElementSort element, String name, String type, long level,
                           long hp, long hpMax, long attack, long defence, long speed, double mass,
                           String description, double hpGrow, double attackGrow, double defenceGrow,
                           double[] resistance, double[] manaGrow, int slotCount) {

        /**
         * 给一个实体拍快照。
         *
         * @param entity 实体
         * @return 快照
         */
        public static Snapshot of(LivingThing entity) {
            double[] resistance = new double[DataKeys.ELEMENTS.size()];
            double[] manaGrow = new double[DataKeys.ELEMENTS.size()];
            for (int i = 0; i < DataKeys.ELEMENTS.size(); i++) {
                String element = DataKeys.ELEMENTS.get(i);
                resistance[i] = switch (element) {
                    case "metal" -> entity.getMetalResistance();
                    case "wood" -> entity.getWoodResistance();
                    case "water" -> entity.getWaterResistance();
                    case "fire" -> entity.getFireResistance();
                    default -> entity.getDirtResistance();
                };
                manaGrow[i] = manaGrowOf(entity, element);
            }
            return new Snapshot(entity, entity.getElementSort(), entity.getName(), entity.getType(),
                    entity.getLevel(), entity.getHp(), entity.getHpMax(), entity.getAttack(),
                    entity.getDefence(), entity.getSpeed(), entity.getMass(), entity.getDescription(),
                    entity.getHpGrow(), entity.getAttackGrow(), entity.getDefenceGrow(),
                    resistance, manaGrow, entity.getInventory().getSlots().size());
        }

        /**
         * 把实体恢复成拍快照时的样子。
         */
        public void restore() {
            if (entity == null) {
                return;
            }
            for (int i = 0; i < DataKeys.ELEMENTS.size(); i++) {
                setResistance(DataKeys.ELEMENTS.get(i), resistance[i]);
                EntityKeySpecs.setManaGrow(entity, DataKeys.ELEMENTS.get(i), manaGrow[i]);
            }
            entity.setElementSort(element);
            entity.setName(name);
            entity.setType(type);
            entity.setSpeed(speed);
            entity.setMass(mass);
            entity.setDescription(description);
            entity.setHpGrow(hpGrow);
            entity.setAttackGrow(attackGrow);
            entity.setDefenceGrow(defenceGrow);
            entity.setLevel(level);
            entity.setHpMax(hpMax);
            entity.setAttack(attack);
            entity.setDefence(defence);
            entity.setHp(hp);
            restoreSlots();
        }

        /**
         * 把背包格数恢复成拍快照时的数量（多了就删末尾空格，少了就补）。
         */
        private void restoreSlots() {
            List<Slot> slots = entity.getInventory().getSlots();
            while (slots.size() > slotCount) {
                Slot last = slots.get(slots.size() - 1);
                if (last.getContainedItem() != null) {
                    return;
                }
                slots.remove(slots.size() - 1);
            }
            if (slots.size() < slotCount) {
                entity.getInventory().addSlot(slotCount - slots.size());
            }
            entity.getInventory().sort();
        }

        /**
         * @param element 元素名（小写）
         * @param value   抗性
         */
        private void setResistance(String element, double value) {
            switch (element) {
                case "metal" -> entity.setMetalResistance(value);
                case "wood" -> entity.setWoodResistance(value);
                case "water" -> entity.setWaterResistance(value);
                case "fire" -> entity.setFireResistance(value);
                default -> entity.setDirtResistance(value);
            }
        }
    }

    /**
     * 一次加载的结果：应用了几项、跳过了几项、为什么。
     * <p>
     * 存在的意义是 {@code ConfigLoader} 那句"配置加载失败对玩家不可见"的修复 ——
     * 结尾必须能在控制台上说清"读了几项、跳过几项、各是为什么"。
     *
     * @author AI（DeepSeek）生成
     */
    public static final class Report implements SpecPatcher.Sink {

        /**
         * 这一批配置的来源说明。
         */
        private final String note;
        /**
         * 已经打过补丁的模板（按对象身份判重，保证"每个模板只补一次"）。
         */
        private final Set<LivingThing> bound = Collections.newSetFromMap(new IdentityHashMap<>());
        /**
         * 已经应用过的条目描述（按模板 id 去重，供"改了成长要重算"判断）。
         */
        private final Set<String> appliedKeys = new LinkedHashSet<>();
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
         * 提醒（<b>不是</b>错误、也<b>不是</b>跳过）：配置本身写对了，
         * 但有一条别的入口会盖过它 —— 必须让用户看见，见 {@link #notice(String)}。
         */
        private final List<String> notices = new ArrayList<>();
        /**
         * 打过补丁的模板数。
         */
        private int patchedTemplates = 0;

        /**
         * @param note 来源说明
         */
        public Report(String note) {
            this.note = note;
        }

        /**
         * @param id     配置里的键
         * @param target 模板
         * @return 这一轮里这个模板是不是第一次被补（{@code false} = 重复，调用方应当跳过）
         */
        public boolean markBound(String id, LivingThing target) {
            if (!bound.add(target)) {
                return false;
            }
            patchedTemplates++;
            return true;
        }

        /**
         * @param id   配置里的键
         * @param path 键路径
         * @param what 做了什么
         */
        public void applied(String id, String path, String what) {
            applied.add(id + " → " + path + " = " + what);
            appliedKeys.add(id + "/" + path);
        }

        /**
         * @param id     配置里的键
         * @param path   键路径
         * @param reason 为什么跳过
         */
        public void skipped(String id, String path, String reason) {
            skipped.add(id + " → " + path + "：" + reason);
        }

        /**
         * @param message 问题描述
         */
        public void error(String message) {
            errors.add(message);
        }

        /**
         * 记一条<b>提醒</b>：配置本身没有错（值照样生效），但有一个"另一个入口也写了同一个数"的
         * 情况必须让用户看见 —— 典型就是「{@code derived.hpMax} 盖住了规则表的
         * {@code flameReaver.baseHpMax}」。
         * <p>
         * <b>为什么不复用 {@link #skipped} / {@link #error}</b>：那两条都会让
         * {@link #isClean()} 变 {@code false}、并让结尾那句"跳过 N 项"变大 ——
         * 而这个键<b>是生效的</b>，把它报成"跳过"是另一种谎话。
         *
         * @param message 提醒内容
         */
        public void notice(String message) {
            notices.add(message);
        }

        /**
         * @param path {@code 实体id/键路径}
         * @return 这个键路径是不是被应用过
         */
        public boolean hasApplied(String path) {
            return appliedKeys.contains(path);
        }

        /**
         * @param id 实体 id
         * @return 这个实体下应用过的键路径
         */
        public List<String> appliedKeys(String id) {
            List<String> result = new ArrayList<>();
            String prefix = id + "/";
            for (String key : appliedKeys) {
                if (key.startsWith(prefix)) {
                    result.add(key);
                }
            }
            return result;
        }

        /**
         * @return 打过补丁的模板数
         */
        public int patchedTemplates() {
            return patchedTemplates;
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
         * @return 提醒（配置没错、值也生效，但有另一个入口会盖过它）
         */
        public List<String> notices() {
            return Collections.unmodifiableList(notices);
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
            for (String message : notices) {
                ConfigOutput.noteworthy("[配置提醒] " + note + "：" + message);
            }
            for (String message : skipped) {
                ConfigOutput.problem("[配置跳过] " + note + "：" + message);
            }
            if (isClean()) {
                ConfigOutput.info("[配置] " + note + "：应用 " + applied.size() + " 项，跳过 0 项，"
                        + "影响 " + patchedTemplates + " 个模板");
            } else {
                ConfigOutput.info("[配置] " + note + "：应用 " + applied.size() + " 项，跳过 "
                        + (skipped.size() + errors.size()) + " 项（上面已逐条写明原因），"
                        + "影响 " + patchedTemplates + " 个模板");
            }
            // 逐条明细已经打过了（verbose），或者明细被静默 —— 两种情况下都要留一笔账，
            // 好在全部配置加载完之后汇总成那一行"应用 N 项"（见 ConfigOutput#printPatchSummary）
            ConfigOutput.patchApplied(note, applied.size(), skipped.size(), errors.size(),
                    patchedTemplates, "个模板");
        }
    }
}
