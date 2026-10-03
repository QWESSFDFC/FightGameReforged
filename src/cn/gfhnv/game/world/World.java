package cn.gfhnv.game.world;

import cn.gfhnv.game.Thing;
import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.Entity;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.mod.Mod;
import cn.gfhnv.game.skill.Skill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 游戏世界的全局注册表与运行时容器。
 * <p>
 * 说明：
 * <ul>
 *     <li><b>things</b>：游戏运行时的对象列表（{@link Thing} 及其子类实例，如加入战斗的角色、敌人、物品等）；</li>
 *     <li><b>itemList / entityList / effectList / skillList</b>：物品、实体、效果、技能的<b>注册表</b>
 *     （游戏内可用内容的静态登记），由模组加载（{@link cn.gfhnv.game.mod.Mod#registerItself()}）
 *     或官方内容填充；</li>
 *     <li><b>modList</b>：已加载的模组列表（由 {@link cn.gfhnv.game.mod.ModLoader} 填充）；</li>
 *     <li><b>turnTimer</b>：全局回合计数器。</li>
 * </ul>
 * 注册表存放<b>模板/可用内容</b>（如可选择的怪物、物品）；things 存放<b>运行时实例</b>。
 * 例如玩家在开局选择生物后，通过 {@link #addThing(Thing)} 将 {@code copy()} 出的实例放入 things。
 *
 * @author gfhnv
 */
public class World {
    /**
     * 全局回合计数器（每经过一个回合自动 +1）。
     */
    public static int turnTimer = 0;

    /**
     * 已加载的模组列表。
     */
    private static List<Mod> modList = new ArrayList<>();

    /**
     * 效果注册表（所有可用效果）。
     */
    private static List<Effect> effectList = new ArrayList<>();

    /**
     * 游戏运行时对象列表。
     */
    private static List<Thing> things = new ArrayList<>();

    /**
     * 实体注册表（所有可选/可用实体）。
     */
    private static List<Entity> entityList = new ArrayList<>();

    /**
     * 物品注册表（所有可选/可用物品）。
     */
    private static List<Item> itemList = new ArrayList<>();

    /**
     * <b>显式登记</b>的技能原型（{@link Mod#addSkill(Skill)} / {@link #addSkill(Skill)}）。
     * <p>
     * 只有"不在任何实体控制器里"的技能才需要登记到这里 —— 最典型的是白厄那 5 个
     * <b>觉醒技能</b>：它们是 {@code UltimateAttack} 出手时 {@code new} 出来的，
     * 配置层沿"实体 → 控制器 → 技能"根本看不到它们。登记成原型之后，
     * 运行时改成 {@link #prototypeCopyOf(Class)}（= {@code 原型.copy()}），
     * 配置打出来的值就跟着副本走。
     */
    private static List<Skill> skillPrototypes = new ArrayList<>();

    /**
     * 技能注册表<b>视图</b>：{@link #skillPrototypes 显式原型} + 各实体控制器里的技能。
     * <p>
     * 每次 {@link #getSkillList()} 都按当前注册表重建（见 {@link #rebuildSkillList()}），
     * 所以实体晚一点注册进来也能立刻查到。
     */
    private static List<Skill> skillList = new ArrayList<>();

    /**
     * 技能原型 → 它归属的实体模板。
     * <p>
     * 配置的键是 {@code <实体完整id>#<技能名>}，而原型不在控制器里，
     * 所以必须记下"它归哪只模板"才写得进 {@code SkillData.json}（见 {@link Mod#addSkill(LivingThing, Skill)}）。
     */
    private static Map<Skill, LivingThing> skillOwners = new LinkedHashMap<>();

    /**
     * 向效果注册表注册一个效果。
     *
     * @param effect 要注册的效果
     */
    public static void addEffect(Effect effect) {
        effectList.add(effect);
    }

    /**
     * 从效果注册表移除一个效果（不存在则忽略）。
     *
     * @param effect 要移除的效果
     */
    public static void removeEffect(Effect effect) {
        if (!effectList.contains(effect)) {
            return;
        }
        effectList.remove(effect);
    }

    /**
     * @return 效果注册表
     */
    public static List<Effect> getEffectList() {
        return effectList;
    }

    /**
     * 设置效果注册表。
     *
     * @param effectList 效果注册表
     */
    public static void setEffectList(List<Effect> effectList) {
        World.effectList = effectList;
    }

    /**
     * 注册一个已加载的模组。
     *
     * @param m 模组
     */
    public static void addMod(Mod m) {
        modList.add(m);
    }

    /**
     * 移除一个已加载的模组（不存在则忽略）。
     *
     * @param m 模组
     */
    public static void removeMod(Mod m) {
        if (modList.contains(m)) modList.remove(m);
    }

    /**
     * @return 已加载的模组列表
     */
    public static List<Mod> getModList() {
        return modList;
    }

    /**
     * @return 游戏运行时对象列表
     */
    public static List<Thing> getThings() {
        return things;
    }

    /**
     * 向游戏运行时对象列表添加一个对象（如选中的角色、敌人副本、物品实例等）。
     * <p>
     * 入表前会把 id 补成注册表里的完整 id（见 {@link #applyRegisteredId(Thing)}），
     * 这样「选出来的角色/物品」与注册表模板的 id 始终一致。
     *
     * @param thing 运行时对象
     */
    public static void addThing(Thing thing) {
        applyRegisteredId(thing);
        things.add(thing);
    }

    /**
     * 从游戏运行时对象列表移除一个对象（不存在则忽略）。
     *
     * @param thing 运行时对象
     */
    public static void removeThing(Thing thing) {
        if (things.contains(thing)) things.remove(thing);
    }

    /**
     * 向实体注册表注册一个实体。
     *
     * @param e 实体
     */
    public static void addEntity(Entity e) {
        entityList.add(e);
    }

    /**
     * 向物品注册表注册一个物品。
     *
     * @param m 物品
     */
    public static void addItem(Item m) {
        itemList.add(m);
    }

    /**
     * 从物品注册表移除一个物品（不存在则忽略）。
     *
     * @param m 物品
     */
    public static void removeItem(Item m) {
        if (itemList.contains(m)) itemList.remove(m);
    }

    /**
     * @return 物品注册表
     */
    public static List<Item> getItemList() {
        return itemList;
    }

    /**
     * 从实体注册表移除一个实体（不存在则忽略）。
     *
     * @param e 实体
     */
    public static void removeEntity(Entity e) {
        if (entityList.contains(e)) entityList.remove(e);
    }

    /* ------------------------------------------------------------------
     * id 归一：把运行时实例的「短 id」补成注册表里的完整 id
     * ------------------------------------------------------------------ */

    /**
     * 取 id 里 {@code 前缀:} 之后的部分（用于显示，例如
     * {@code game_official_content:aNiceSword} → {@code aNiceSword}）。
     * <p>
     * 注册表与运行时数据里存的都是完整 id，但玩家在命令里通常只写短名，
     * 所以回显与报错都打印短名；判断相等时两种写法都要认（见
     * {@code EffectCommand.matches} / {@code GiveCommand.matches}）。
     *
     * @param id 完整 id；可为 {@code null}
     * @return 短名；{@code null} 返回空串，没有冒号则原样返回
     */
    public static String shortIdOf(String id) {
        if (id == null) {
            return "";
        }
        int colon = id.indexOf(':');
        return colon >= 0 && colon + 1 < id.length() ? id.substring(colon + 1) : id;
    }

    /**
     * 取运行时实体的短名。
     *
     * @param thing 实体或物品；可为 {@code null}
     * @return 短名（{@code null} 返回 {@code "?"}）
     */
    public static String shortIdOf(Thing thing) {
        return thing == null ? "?" : shortIdOf(thing.getId());
    }

    /**
     * 取运行时效果的短名。
     *
     * @param effect 效果；可为 {@code null}
     * @return 短名（{@code null} 返回 {@code "?"}）
     */
    public static String shortIdOf(Effect effect) {
        return effect == null ? "?" : shortIdOf(effect.getID());
    }

    /**
     * 在注册表里找出"运行时对象的 id 应该补成哪一个完整 id"。
     * <p>
     * <b>先按短名精确匹配，匹配不到才退回"同类第一条模板"</b>。
     * 只按类取第一条是不够的：同一个类可以注册多种形态，例如【残破容器】与【完整容器】
     * 都是 {@link cn.gfhnv.game.officialStuff.customEntity.summons.BrokenContainer}，
     * 只按类取第一条会把完整容器的 id 改写成 {@code brokenContainer}，
     * 于是按 id 找内容的地方（{@code /give}、选择器筛选、效果合并判定）全都指向了错的形态。
     * <p>
     * 退回按类匹配是为了兼容"id 由代码临时生成、注册表里没有同名模板"的运行时对象。
     *
     * @param type    运行时对象的类型（精确比较，父类不算）
     * @param shortId 运行时对象当前的 id（调用方已确认它不含 {@code :}）
     * @param things  注册表列表
     * @param idOf    从注册表元素上取 id 的方式
     * @param <T>     注册表元素类型
     * @return 完整 id；注册表里没有同类模板时返回 {@code null}
     */
    private static <T> String registeredIdOf(Class<?> type, String shortId, List<T> things,
                                             java.util.function.Function<T, String> idOf) {
        String sameId = null;
        String sameClass = null;
        for (T candidate : things) {
            if (candidate == null || candidate.getClass() != type) {
                continue;
            }
            String candidateId = idOf.apply(candidate);
            if (candidateId == null) {
                continue;
            }
            if (sameClass == null) {
                sameClass = candidateId;
            }
            if (sameId == null && shortId != null && shortId.equals(shortIdOf(candidateId))) {
                sameId = candidateId;
            }
        }
        return sameId != null ? sameId : sameClass;
    }

    /**
     * 把一个运行时实体的 id 补成注册表里的完整 id（带 {@code MOD_ID:} 前缀）。
     * <p>
     * <b>为什么需要它</b>：加前缀这一步只发生在 {@link Mod#addEntity(Entity)} 里，
     * 而它作用的是<b>被注册的那个模板</b>；代码里直接 {@code new Xxx(...)} 出来的实例，
     * id 是构造函数里写死的短名（例如 {@code iceInsect}）。
     * 于是一个「Boss 分裂出来的冰虫」和注册表里的冰虫会是两个不同的 id，
     * 按 id 比较的地方（选择器筛选、效果合并判定等）就会对不上。
     * <p>
     * 已经是完整 id（含 {@code :}）、id 为空、或注册表里没有同类模板时，原样返回。
     *
     * @param entity 运行时实体
     * @return 完整 id
     */
    public static String fullIdOf(Entity entity) {
        if (entity == null) {
            return null;
        }
        String id = entity.getId();
        if (id == null || id.indexOf(':') >= 0) {
            return id;
        }
        String registered = registeredIdOf(entity.getClass(), id, entityList, Thing::getId);
        return registered == null ? id : registered;
    }

    /**
     * 把一个运行时物品的 id 补成注册表里的完整 id。
     *
     * @param item 运行时物品
     * @return 完整 id
     * @see #fullIdOf(Entity)
     */
    public static String fullIdOf(Item item) {
        if (item == null) {
            return null;
        }
        String id = item.getId();
        if (id == null || id.indexOf(':') >= 0) {
            return id;
        }
        String registered = registeredIdOf(item.getClass(), id, itemList, Thing::getId);
        return registered == null ? id : registered;
    }

    /**
     * 把一个运行时效果的 id 补成注册表里的完整 id。
     *
     * @param effect 运行时效果
     * @return 完整 id
     * @see #fullIdOf(Entity)
     */
    public static String fullIdOf(Effect effect) {
        if (effect == null) {
            return null;
        }
        String id = effect.getID();
        if (id == null || id.indexOf(':') >= 0) {
            return id;
        }
        String registered = registeredIdOf(effect.getClass(), id, effectList, Effect::getID);
        return registered == null ? id : registered;
    }

    /**
     * 直接把运行时对象的 id 改成 {@link #fullIdOf(Entity)} 给出的完整 id。
     * <p>
     * 供「对象进入游戏世界」的入口调用（{@link #addThing(Thing)}、
     * {@code Fight.addFighter/addEnemy}、{@code LivingThing.addEffect}），
     * 这样无论实例是在哪里 new 出来的，进入游戏后都带完整的注册表 id。
     * <p>
     * 只补<b>不含 {@code :}</b> 的 id；想给某个实例单独指定 id，写成带 {@code :} 的形式即可
     * （例如 {@code myMod:bossCopy1}），本方法不会动它。
     *
     * @param thing 运行时实体或物品；可为 {@code null}
     */
    public static void applyRegisteredId(Thing thing) {
        if (thing instanceof Entity entity) {
            entity.setId(fullIdOf(entity));
        } else if (thing instanceof Item item) {
            item.setId(fullIdOf(item));
        }
    }

    /**
     * 直接把运行时效果的 id 改成 {@link #fullIdOf(Effect)} 给出的完整 id。
     *
     * @param effect 运行时效果；可为 {@code null}
     */
    public static void applyRegisteredId(Effect effect) {
        if (effect != null) {
            effect.setId(fullIdOf(effect));
        }
    }

    /* ------------------------------------------------------------------
     * 技能注册表（2026-10-03 新增）
     * ------------------------------------------------------------------ */

    /**
     * 取 id 里 {@code 前缀:} 之后的部分，供技能 id 显示用。
     *
     * @param skill 技能；可为 {@code null}
     * @return 短名（{@code null} 返回 {@code "?"}）
     * @see #shortIdOf(String)
     */
    public static String shortIdOf(Skill skill) {
        return skill == null ? "?" : shortIdOf(skill.getId());
    }

    /**
     * 向技能注册表登记一个技能<b>原型</b>。
     * <p>
     * 只有"不在任何实体控制器里"的技能才需要走这里，而且运行时必须用
     * {@link #prototypeCopyOf(Class)} 取副本（不能直接用原型本身：原型是配置打补丁的那一份，
     * 也是全场共用的那一份）。落地用法见 {@link Mod#addSkill(LivingThing, Skill)}。
     * <p>
     * 本方法只负责<b>入表</b>；id 的派生与前缀由 {@link Mod#addSkill(Skill)} 落
     * （模组内容要加 {@code MOD_ID:} 前缀，World 这一层不知道是谁登记的）。
     *
     * @param skill 技能原型
     */
    public static void addSkill(Skill skill) {
        if (skill != null && !skillPrototypes.contains(skill)) {
            skillPrototypes.add(skill);
        }
    }

    /**
     * 从技能注册表移除一个原型（不存在则忽略）。
     *
     * @param skill 技能原型
     */
    public static void removeSkill(Skill skill) {
        skillPrototypes.remove(skill);
        skillOwners.remove(skill);
    }

    /**
     * 记下"这个技能原型归哪只实体模板"。
     * <p>
     * 配置的键是 {@code <实体完整id>#<技能名>}，原型不在控制器里，只能靠这条归属关系
     * 才出现在那只模板的配置里（见 {@code SkillDataPatcher} / {@code ConfigDefaultWriter}）。
     *
     * @param prototype 技能原型
     * @param owner     归属的实体模板；传 {@code null} 表示取消归属
     */
    public static void bindSkillOwner(Skill prototype, LivingThing owner) {
        if (prototype == null) {
            return;
        }
        if (owner == null) {
            skillOwners.remove(prototype);
            return;
        }
        skillOwners.put(prototype, owner);
    }

    /**
     * @param skill 技能
     * @return 这个技能原型的归属模板；没有归属（例如控制器里的技能）返回 {@code null}
     */
    public static LivingThing skillOwnerOf(Skill skill) {
        return skillOwners.get(skill);
    }

    /**
     * @param skill 技能
     * @return 归属模板的完整 id（配置键里 {@code #} 前面那一段）；没有归属返回 {@code null}
     */
    public static String skillOwnerIdOf(Skill skill) {
        LivingThing owner = skillOwners.get(skill);
        return owner == null ? null : fullIdOf(owner);
    }

    /**
     * @return 技能注册表视图（显式原型 + 控制器里的技能，按类去重）；每次调用都重建
     */
    public static List<Skill> getSkillList() {
        rebuildSkillList();
        return skillList;
    }

    /**
     * 按名字在技能注册表里找一个技能。
     * <p>
     * 与 {@code /summon brokenContainer} 同一口径，<b>从精确到宽松逐级退让</b>：
     * <ol>
     *     <li>完整 id（{@code game_official_content:awakenCommonAttack}）；</li>
     *     <li>短名（{@code awakenCommonAttack}）；</li>
     *     <li>类名（{@code AwakenCommonAttack}）；</li>
     *     <li>全限定类名（{@code cn.gfhnv...AwakenCommonAttack}）。</li>
     * </ol>
     * 某一级命中多个时<b>不猜</b>：打印一行并返回 {@code null}（同一个类名可以出现在两个包里，
     * 那种情况就该写完整 id）。
     *
     * @param name 名字；{@code null} / 空白返回 {@code null}
     * @return 唯一命中的技能；找不到或有歧义返回 {@code null}
     */
    public static Skill findSkill(String name) {
        if (name == null) {
            return null;
        }
        String key = name.trim();
        if (key.isEmpty()) {
            return null;
        }
        List<Skill> byId = new ArrayList<>();
        List<Skill> byShortId = new ArrayList<>();
        List<Skill> byClassName = new ArrayList<>();
        List<Skill> byQualifiedName = new ArrayList<>();
        for (Skill skill : getSkillList()) {
            if (key.equals(skill.getId())) {
                byId.add(skill);
            }
            if (key.equals(shortIdOf(skill.getId()))) {
                byShortId.add(skill);
            }
            if (key.equals(skill.getClass().getSimpleName())) {
                byClassName.add(skill);
            }
            if (key.equals(skill.getClass().getName())) {
                byQualifiedName.add(skill);
            }
        }
        for (List<Skill> tier : List.of(byId, byShortId, byClassName, byQualifiedName)) {
            if (tier.isEmpty()) {
                continue;
            }
            if (tier.size() == 1) {
                return tier.getFirst();
            }
            StringBuilder classes = new StringBuilder();
            for (Skill skill : tier) {
                classes.append(classes.length() == 0 ? "" : " / ").append(skill.getClass().getName());
            }
            System.out.println("[技能] 「" + key + "」在技能注册表里有 " + tier.size() + " 条（"
                    + classes + "）—— 请写完整 id");
            return null;
        }
        return null;
    }

    /**
     * 取某个技能类原型的<b>副本</b>（{@code 原型.copy()}）。
     * <p>
     * 运行时造技能一律走这里，不要 {@code new Xxx()}：配置（{@code SkillData.json}）打在
     * <b>原型</b>上，只有 {@code copy()} 出来的副本才带着那些值 ——
     * 这正是"每个技能类都必须有复制构造器"那条纪律的由来。
     * <p>
     * 找不到原型时<b>直接报错</b>（fail loud），不要静默给一个出厂值的实例：
     * 那会变成"配置改了没反应"，是这个项目最讨厌的一类故障。
     *
     * @param type 技能类（精确比较，父类不算）
     * @return 原型的副本
     * @throws IllegalStateException 注册表里没有这个类的原型
     */
    public static Skill prototypeCopyOf(Class<? extends Skill> type) {
        for (Skill prototype : skillPrototypes) {
            if (prototype.getClass() == type) {
                return prototype.copy();
            }
        }
        throw new IllegalStateException("技能注册表里没有 " + type.getName()
                + " 的原型 —— 它必须先被登记（Mod#addSkill），运行时才能按 原型.copy() 取到带配置的副本");
    }

    /**
     * 给一个技能落 id：<b>显式 id 优先，没有才按类名派生</b>，再加 {@code 模组id:} 前缀。
     * <p>
     * <b>撞名 = 报错</b>：落下来的完整 id 落到<b>另一个类</b>上时抛异常，逼作者写显式 id。
     * 项目里真实存在的两对是 {@code universalSkill.CommonAttack} / {@code actorLiXiaoYanSkills.CommonAttack}
     * 与两个包各自的 {@code UltimateAttack} —— 它们的类名一模一样，派生出来必然撞。
     * （同一个类造出很多份实例不算撞：参数化的技能类本来就该共用一个 id。）
     * <p>
     * 已经是完整 id（含 {@code :}）的原样保留，所以技能类可以自己写死
     * {@code setId("myMod:xxx")}。
     *
     * @param skill      技能
     * @param modId      认领它的模组 id（{@link Mod#getMOD_ID()}）；为空则什么都不做
     * @param registered 已经落过 id 的那批技能（撞名账本）：
     *                   {@link Mod#addSkill(Skill)} 传本模组自己的表，
     *                   {@link #rebuildSkillList()} 传正在拼的注册表视图
     * @throws IllegalStateException 完整 id 与另一个类撞名
     */
    public static void assignSkillId(Skill skill, String modId, List<Skill> registered) {
        if (skill == null || modId == null || modId.isEmpty()) {
            return;
        }
        String declared = skill.getId();
        String fullId;
        if (declared != null && declared.indexOf(':') >= 0) {
            fullId = declared;
        } else {
            String shortId = declared == null || declared.isEmpty() ? deriveSkillShortId(skill) : declared;
            fullId = modId + ":" + shortId;
        }
        for (Skill other : registered) {
            if (other == null || other == skill || !fullId.equals(other.getId())) {
                continue;
            }
            if (other.getClass() != skill.getClass()) {
                throw new IllegalStateException("技能 id 撞名：" + fullId + " 同时属于 "
                        + other.getClass().getName() + " 与 " + skill.getClass().getName()
                        + "（类名派生出来的 id 一样）—— 请给其中一个写显式 id：在它的构造器里 setId(\"自己的短名\")");
            }
        }
        skill.setId(fullId);
    }

    /**
     * 按类名派生技能短 id（类名首字母小写）：{@code AwakenCommonAttack} → {@code awakenCommonAttack}。
     *
     * @param skill 技能
     * @return 短 id
     * @throws IllegalStateException 匿名类 / 局部类没有类名，派生不出来
     */
    private static String deriveSkillShortId(Skill skill) {
        String simple = skill.getClass().getSimpleName();
        if (simple.isEmpty()) {
            throw new IllegalStateException("技能类 " + skill.getClass().getName()
                    + " 没有类名（匿名类 / 局部类），派生不出 id —— 请给它写显式 id");
        }
        return Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
    }

    /**
     * 重建技能注册表视图。
     * <p>
     * 两条来源：① 显式登记的原型（先来，顺序稳定）；② 各实体控制器里的技能。
     * <b>每个类只登记第一条</b> —— 注册表要的是"原型"，而
     * {@code universalSkill.CommonAttack} 这种参数化技能会被造出很多份（各自倍率不同），
     * 这跟 {@link #registeredIdOf} 的"先短名、再退回同类第一条"是同一个口径。
     * <p>
     * 视图按类去重，但 <b>id 会落在每一份实例上</b>（见 {@link #indexSkill}）：
     * 控制器里那份副本才是运行时真正出手的那一份。
     */
    private static void rebuildSkillList() {
        skillList.clear();
        for (Skill prototype : skillPrototypes) {
            indexSkill(prototype, null);
        }
        for (Entity entity : entityList) {
            if (!(entity instanceof LivingThing living) || living.getController() == null
                    || living.getController().getSkills() == null) {
                continue;
            }
            String modId = modIdOfEntity(entity);
            for (Skill skill : living.getController().getSkills()) {
                indexSkill(skill, modId);
            }
        }
    }

    /**
     * 把一个技能并进注册表视图（同类只留第一条），顺带落 id。
     * <p>
     * <b>id 落在每一份实例上，不是只落在"同类第一条"上</b>：控制器里存的是技能的<b>副本</b>
     * （{@code UniversalController} 构造那一刻就 {@code copy()} 了一份），同一个类又会被多只模板
     * 各造一份（{@code universalSkill.CommonAttack} 有 5 份）。只给第一条落 id 的话，
     * 其余几份在 {@code /data get} 里就是一个没有 {@code id} 的技能，看上去像"没登记"。
     * <b>一个类共用一个 id</b>（与 {@link #assignSkillId} 的撞名口径一致）：后到的那几份
     * 直接沿用第一条已经落好的 id。
     *
     * @param skill 技能
     * @param modId 认领它的模组 id；{@code null} 表示没有模组认领（id 保持原样，不补前缀）
     */
    private static void indexSkill(Skill skill, String modId) {
        if (skill == null) {
            return;
        }
        for (Skill known : skillList) {
            if (known.getClass() == skill.getClass()) {
                inheritSkillId(skill, known, modId);
                return;
            }
        }
        if (modId != null) {
            assignSkillId(skill, modId, skillList);
        }
        skillList.add(skill);
    }

    /**
     * 同一个类的第 2…n 份实例沿用该类的 id（视图里那条同类技能就是它的出处）。
     * <p>
     * 视图里那条自己还没有 id 时（它属于一只没有模组认领的实体）就地补一次 ——
     * "一个类一个 id"这条不能因为登记顺序而破掉。
     *
     * @param skill 后到的实例
     * @param known 视图里同类的第一条
     * @param modId 认领它的模组 id；{@code null} 表示没有模组认领
     */
    private static void inheritSkillId(Skill skill, Skill known, String modId) {
        if (skill.getId() != null) {
            return;
        }
        if (known.getId() == null && modId != null) {
            assignSkillId(known, modId, skillList);
        }
        skill.setId(known.getId());
    }

    /**
     * @param entity 实体
     * @return 认领这只实体的模组 id（判据与 {@code ConfigDefaultWriter#isOfficialContent} 相同：
     * 在模组表里找谁的表里有它）；没有模组认领返回 {@code null}
     */
    private static String modIdOfEntity(Entity entity) {
        for (Mod mod : modList) {
            if (mod == null || mod.getMOD_ID() == null || mod.getMOD_ID().isEmpty()) {
                continue;
            }
            for (Entity owned : mod.getEntityList()) {
                if (owned == entity) {
                    return mod.getMOD_ID();
                }
            }
        }
        return null;
    }

    /**
     * @return 实体注册表
     */
    public static List<Entity> getEntityList() {
        return entityList;
    }

    /**
     * 设置实体注册表。
     *
     * @param entgityList 实体注册表
     */
    public static void setEntityList(List<Entity> entgityList) {
        entityList = entgityList;
    }

    /**
     * 按 UUID 从运行时对象列表中查找对象。
     *
     * @param uuid 对象的 UUID
     * @return 匹配的对象；若 uuid 为空、列表为空或未找到则返回 {@code null}
     */
    public static Thing getAimedThing(String uuid) {
        if (uuid.equals("")) {
            return null;
        }
        if (things.isEmpty()) {
            return null;
        }
        for (Thing thing : things) {
            if (thing.getUUID().equals(uuid)) {
                return thing;
            }
        }
        return null;
    }

    /**
     * 从实体注册表中筛选出所有 {@link LivingThing}。
     *
     * @return 生物实体列表
     */
    public static List<LivingThing> getLivingEntityList() {
        List<LivingThing> livingThingList = new ArrayList<>();
        for (Entity e : entityList) {
            if (e instanceof LivingThing) {
                livingThingList.add((LivingThing) e);
            }
        }
        return livingThingList;
    }
}