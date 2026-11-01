package cn.gfhnv.debug_tools;

import cn.gfhnv.game.data.*;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEntity.monsters.FlameReaver;
import cn.gfhnv.game.officialStuff.customEntity.players.ActorLiXiaoYan;
import cn.gfhnv.game.system.command.*;
import cn.gfhnv.game.system.fight.*;

import java.util.ArrayList;
import java.util.List;

import static cn.gfhnv.debug_tools.TestKit.*;
import static cn.gfhnv.debug_tools.TestSupport.*;
import static cn.gfhnv.debug_tools.TestProbes.*;

/**
 * 自测：**命令**。
 * <p>
 * 主题：/data modify 与筛选、存储。
 * <p>
 * 由 {@code TestCommandSystem#main} 按顺序调用 {@code DataModifyTests#runAll()}。
 */
public final class DataModifyTests {

    /**
     * {@code /data modify}：改到路径深处（列表元素里的字段、映射、标量列表的增删）。
     * <p>
     * 这里刻意<b>不</b>去改 {@code skills[...]}：{@code controller} 不在"数据对象"名单里
     * （它是行为不是数据），所以技能表本来就不可达 —— 这是设计，不是 bug。
     */
    private static void testDataModify() {
        section("NBT 路径写入与 /data modify");

        LivingThing previous = CommandManager.getPlayer();
        ActorLiXiaoYan probe = new ActorLiXiaoYan(125);
        CommandManager.setPlayer(probe);

        // ① 深处写入：列表元素里的字段（manas[3] = 火法力）
        run("data modify entity @s manas[3].amount set 100", true);
        check("modify set 能改到 manas[3].amount", probe.getManas().get(3).getAmount() == 100.0d);
        run("data modify entity @s hp set 1", true);
        check("modify set 走 setter（hp 被钳制后确实是 1）", probe.getHp() == 1L);
        run("data modify entity @s ignition set 7", true);
        check("modify set 改 ignition", probe.getIgnition() == 7);
        run("data modify entity @s ignition set 12345", true);
        check("modify set 同样被 setIgnition 夹住", probe.getIgnition() == probe.getIgnitionMax());

        // ② 给身上挂一个效果，再改"效果列表里的元素"
        run("effect @s add frozen", true);
        check("测试前提：身上有一个效果", probe.getEntityEffectList().size() == 1);
        run("data modify entity @s effects[0].level set 3", true);
        check("modify set 能改到 effects[0].level", probe.getEntityEffectList().get(0).getLevel() == 3);

        // ③ 标量列表允许增删（枚举也算标量）；对象列表必须被拒
        int tagsBefore = probe.getEntityEffectList().get(0).getEffectTagsList().size();
        run("data modify entity @s effects[0].effectTagsList append POSITIVE", true);
        check("modify append 往枚举列表里加了一个",
                probe.getEntityEffectList().get(0).getEffectTagsList().size() == tagsBefore + 1);
        run("data modify entity @s effects[0].effectTagsList insert 0 POSITIVE", true);
        check("modify insert 插到了开头",
                probe.getEntityEffectList().get(0).getEffectTagsList().size() == tagsBefore + 2);
        run("data modify entity @s effects[0].effectTagsList prepend POSITIVE", true);
        check("modify prepend 也生效",
                probe.getEntityEffectList().get(0).getEffectTagsList().size() == tagsBefore + 3);
        run("data modify entity @s effects append {id:\"x\"}", false);

        // ④ 各种"应该报错"的情况
        run("data modify entity @s noSuch.path set 1", false);
        run("data modify entity @s manas[99].amount set 1", false);
        run("data modify entity @s manas set 1", false);
        run("data modify entity @s ignition merge {x:1}", false);
        run("data modify entity @s manas[3].amount set abc", false);
        run("data modify entity @s", false);
        run("data remove entity @s hp", false);

        run("effect @s remove all", true);
        CommandManager.setPlayer(previous);
    }

    /**
     * {@code {k:v}} 过滤、{@code [a:b]} 切片、{@code nbt=} 选择器、{@code storage}、{@code execute if data}
     * —— 2026-10-03 第二批（用户要的"MC 味道"剩下的部分）。
     */
    private static void testDataFiltersAndStorage() {
        section("NBT 过滤/切片/storage/if data");

        /* ① 路径：过滤与切片（纯标签层） */
        NbtCompound tree = Snbt.parseCompound(
                "{items:[{id:\"a\",n:1},{id:\"b\",n:2},{id:\"c\",n:3}]}");
        DataPath filtered = DataPath.parse("items[{id:\"b\"}].n");
        check("过滤 + 取字段", filtered.get(tree) != null && filtered.get(tree).asLong() == 2);
        check("过滤没命中 → null", DataPath.parse("items[{id:\"z\"}]").get(tree) == null);
        check("切片 [0:2] 拿到前两个",
                DataPath.parse("items[0:2]").get(tree) instanceof NbtList first && first.size() == 2);
        check("切片 [1:] 拿到后两个",
                DataPath.parse("items[1:]").get(tree) instanceof NbtList rest && rest.size() == 2);
        check("切片 [:] 拿到全部",
                DataPath.parse("items[:]").get(tree) instanceof NbtList all && all.size() == 3);
        expectSyntaxError("过滤只支持单键", () -> DataPath.parse("items[{id:\"a\",n:1}]"));
        DataPath.parse("items[{id:\"a\"}].n").setIn(tree, new NbtInt(99));
        check("过滤路径能定位到元素并写进去", DataPath.parse("items[{id:\"a\"}].n").get(tree).asLong() == 99);
        expectSyntaxError("切片只能读不能写", () -> DataPath.parse("items[0:1]").setIn(tree, new NbtInt(1)));

        /* ② storage（内存里的全局数据） */
        DataStorage.clear();
        run("data merge storage aiTest {count:1,name:\"测试\"}", true);
        run("data get storage aiTest count", true);
        run("data modify storage aiTest count set 7", true);
        check("storage：set 生效", DataStorage.of("aiTest").get("count") != null
                && DataStorage.of("aiTest").get("count").asLong() == 7);
        check("storage：merge 保留没提到的键", "测试".equals(DataStorage.of("aiTest").get("name").asString()));
        run("data merge storage aiTest {list:[]}", true);
        run("data modify storage aiTest list append 1", true);
        run("data modify storage aiTest list append 2", true);
        check("storage：append 进了列表", DataStorage.of("aiTest").get("list") instanceof NbtList list
                && list.size() == 2);
        run("data modify storage aiTest list insert 0 0", true);
        check("storage：insert 插到了开头",
                DataStorage.of("aiTest").get("list") instanceof NbtList after
                        && after.get(0).asLong() == 0 && after.size() == 3);
        run("data get storage noSuchStore", false);
        run("data get storage aiTest noSuchKey", false);
        run("data modify storage aiTest count append 3", false);

        /* ③ 选择器 nbt=：比的是"数据视图"，而且是标签精确比较 */
        FlameReaver boss = new FlameReaver(150);
        ActorLiXiaoYan hero = new ActorLiXiaoYan(125);
        List<LivingThing> enemies = new ArrayList<>();
        enemies.add(boss);
        List<LivingThing> fighters = new ArrayList<>();
        fighters.add(hero);
        Fight fight = new Fight(enemies, new ArrayList<>(), fighters);

        CommandResult byLevel = CommandManager.executeResult("list @e[nbt={level:125L}]", fight);
        check("nbt=：按长整字段筛出 125 级的目标", byLevel.isSuccess() && byLevel.getResult() == 1);
        // 注意：筛不到时选择器会报「没有选中任何生物」，所以这里断言的是"失败"而不是"影响 0 个"
        CommandResult byInt = CommandManager.executeResult("list @e[nbt={level:125}]", fight);
        check("nbt=：125 与 125L 不是同一个标签，所以筛不到（选择器报没选中）", !byInt.isSuccess());
        CommandResult byGrow = CommandManager.executeResult("list @e[nbt={hpGrow:58.0d}]", fight);
        check("nbt=：按 double 字段筛", byGrow.isSuccess() && byGrow.getResult() == 1);
        CommandResult byBoth = CommandManager.executeResult(
                "list @e[type=ActorLiXiaoYan,nbt={hpGrow:58.0d}]", fight);
        check("nbt= 能和 type= 一起用（都对才选中）", byBoth.isSuccess() && byBoth.getResult() == 1);
        CommandResult byMismatch = CommandManager.executeResult(
                "list @e[type=FlameReaver,nbt={hpGrow:58.0d}]", fight);
        check("nbt= 与 type= 都写对才选中（BOSS 不是 58 成长）", !byMismatch.isSuccess());

        /* ④ execute if data */
        LivingThing previous = CommandManager.getPlayer();
        CommandManager.setPlayer(hero);
        long before = hero.getHp();
        run("execute if data entity @s level run hurt @s 1", true);
        check("if data：条件成立时内层命令真的跑了", hero.getHp() == before - 1);
        run("execute if data entity @s noSuchField run hurt @s 1", true);
        check("if data：条件不成立时内层命令没有跑", hero.getHp() == before - 1);
        run("execute if data storage aiTest count run data modify storage aiTest count set 42", true);
        check("if data storage：条件成立后存储位被改", DataStorage.of("aiTest").get("count").asLong() == 42);
        run("execute if data storage aiTest noSuchKey run data modify storage aiTest count set 0", true);
        check("if data storage：条件不成立时不改", DataStorage.of("aiTest").get("count").asLong() == 42);
        run("execute if data entity @s", false);

        /* ⑤ 实体侧：过滤读得到、切片只读不写 */
        run("effect @s add frozen", true);
        run("data get entity @s effects[{id:\"game_official_content:frozenEffect\"}].level", true);
        run("data get entity @s manas[0:2]", true);
        run("data modify entity @s manas[0:2] set 1", false);
        run("data get entity @s manas[{amount:516.0d}].amount", true);
        run("effect @s remove all", true);

        /* ⑥ 路径走不通时，报错要说清"断在哪一段、为什么"。
         * 只说"没有数据 / 没有命中"的话，用的人分不清是字段名打错、下标越界，还是过滤没命中
         * ——典型场景：想 /data modify 一个身上还没有的效果，报错看起来像是命令语法不对。
         * 两条路要分别覆盖：/data get 走标签树，/data modify 走活对象。 */
        run("effect @s add frozen", true);
        String getMiss = errorOf("data get entity @s effects[{id:\"no:suchEffect\"}].level");
        check("get 过滤没命中：报错里列出了该列表现有的 id",
                getMiss.contains("game_official_content:frozenEffect"));
        String modifyMiss = errorOf("data modify entity @s effects[{id:\"no:suchEffect\"}].level set 1");
        check("modify 过滤没命中：报错里也列出了该列表现有的 id",
                modifyMiss.contains("game_official_content:frozenEffect"));
        String fieldMiss = errorOf("data get entity @s noSuchField");
        check("get 字段名打错：报错里列出这一层有哪些键",
                fieldMiss.contains("没有「noSuchField」") && fieldMiss.contains("uuid"));
        String indexMiss = errorOf("data get entity @s manas[99].amount");
        check("get 下标越界：报错里说出这个列表有几个元素", indexMiss.contains("5 个元素"));
        // 反向确认：命中的那条不会因为多了这段提示而改变行为
        run("data get entity @s effects[{id:\"game_official_content:frozenEffect\"}].level", true);
        run("effect @s remove all", true);

        /* ⑦ 2026-10-03 订正 9 个 Java 名之后，**`/data modify` 必须还能写这几个键**。
         * 这是改名最容易出事的地方：写回是按 Java 字段名拼 setter 名的，setter 名字没跟上就
         * 静默退化成"裸写字段"（命令照旧成功、值也变，只是钳制与副作用没了）。 */
        ActorLiXiaoYan renameProbe = new ActorLiXiaoYan(125);
        LivingThing renamePrevious = CommandManager.getPlayer();
        CommandManager.setPlayer(renameProbe);
        run("data modify entity @s hpGrow set 61", true);
        run("data modify entity @s attackGrow set 23", true);
        run("data modify entity @s defenceGrow set 6", true);
        run("data modify entity @s metalManaGrow set 7", true);
        run("data modify entity @s baseCriticalRate set 0.25", true);
        run("data modify entity @s alive set 0b", true);
        // alive 要**直接读字段**：isAlive() 会按 getHp() 现算并覆盖字段（满血 → 永远 true），
        // 拿它当判据就等于没测（这条只想证明"这个键写得进去、而且走的是 setter"）。
        boolean aliveField = false;
        try {
            // alive 2026-10-04 搬进了 AttributeProfile（用户确认它是"状态"），
            // 所以要先拿到组件实例、再去组件里读字段。
            java.lang.reflect.Field attributesField = LivingThing.class.getDeclaredField("attributes");
            attributesField.setAccessible(true);
            Object profile = attributesField.get(renameProbe);
            java.lang.reflect.Field aliveFieldRef = cn.gfhnv.game.entity.AttributeProfile.class
                    .getDeclaredField("alive");
            aliveFieldRef.setAccessible(true);
            aliveField = profile != null && aliveFieldRef.getBoolean(profile);
        } catch (ReflectiveOperationException e) {
            check("反射读不到 AttributeProfile.alive 字段：" + e, false);
        }
        check("订正 Java 名之后 /data modify 仍能写这几个键（各自落在正确的字段上）",
                renameProbe.getHpGrow() == 61.0
                        && renameProbe.getAttackGrow() == 23.0
                        && renameProbe.getDefenceGrow() == 6.0
                        && renameProbe.getMetalManaGrow() == 7.0
                        && renameProbe.getCriticalRate() == 0.25d
                        && !aliveField);
        CommandManager.setPlayer(renamePrevious);

        CommandManager.setPlayer(previous);
    }

    /**
     * 跑本主题的全部用例（顺序 = 下面列的顺序）。
     */
    public static void runAll() throws Exception {
        testDataModify();
        testDataFiltersAndStorage();
    }

    private DataModifyTests() {
    }
}
