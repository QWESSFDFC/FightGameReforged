package cn.gfhnv.debug_tools;

import cn.gfhnv.game.data.*;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.inventory.Slot;
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
 * 主题：/data 读写与实体引用（含嵌套实体）。
 * <p>
 * 由 {@code TestCommandSystem#main} 按顺序调用 {@code DataCommandTests#runAll()}。
 */
public final class DataCommandTests {

    /**
     * NBT 数据层与 {@code /data} 命令（2026-09 新增）。
     * <p>
     * 覆盖三层：<b>SNBT 文本</b>（能不能解析/原样写回）、<b>路径</b>（{@code a.b[0].c}）、
     * <b>反射桥</b>（哪些字段进数据、写回会不会被 setter 钳制），最后跑两条真命令。
     * <p>
     * 这里特意断言了"<b>行为对象不进数据</b>"（{@code controller}/{@code force}）——
     * 那是这套设计最容易失控的地方：一旦让反射漫游进 controller 与闭包，
     * {@code /data get @s} 会变成几千行的内部实现倾倒。
     */
    private static void testDataCommand() {
        section("NBT 数据层与 /data 命令");

        // ① SNBT：解析、后缀、原样写回
        NbtTag parsed = Snbt.parse("{hp:20L,name:\"白厄\",alive:1b,rate:0.25d,list:[1,2,{x:3}]}");
        check("SNBT 能解析复合标签", parsed instanceof NbtCompound);
        NbtCompound compound = (NbtCompound) parsed;
        check("SNBT 的 20L 是长整", compound.get("hp") instanceof NbtLong && compound.get("hp").asLong() == 20L);
        check("SNBT 的字符串", "白厄".equals(compound.get("name").asString()));
        check("SNBT 的 1b 能当布尔读", compound.get("alive") != null && compound.get("alive").asBoolean());
        check("SNBT 的 0.25d 是双精度", compound.get("rate") != null && compound.get("rate").asDouble() == 0.25d);
        check("SNBT 的嵌套列表有 3 个元素",
                compound.get("list") instanceof NbtList list && list.size() == 3);
        check("SNBT 原样写回（键顺序与后缀都保留）",
                "{hp:20L,name:\"白厄\",alive:1b,rate:0.25d,list:[1,2,{x:3}]}".equals(parsed.toSnbt()));
        check("裸词当字符串（放宽，MC 要求加引号）", "hello".equals(Snbt.parse("hello").asString()));
        check("true 当字节 1", Snbt.parse("true").asLong() == 1L);
        expectSyntaxError("残缺的 SNBT 会报错（{hp:}）", () -> Snbt.parse("{hp:}"));
        expectSyntaxError("没闭合的复合标签会报错", () -> Snbt.parse("{hp:1"));

        // ② 路径
        NbtCompound tree = Snbt.parseCompound("{a:{b:[{c:1L},{c:2L}]}}");
        DataPath path = DataPath.parse("a.b[1].c");
        check("路径解析出 4 段", path.segments().size() == 4);
        check("路径能取到列表元素里的字段", path.get(tree) != null && path.get(tree).asLong() == 2L);
        check("路径不存在时返回 null", DataPath.parse("a.x").get(tree) == null);
        check("下标越界时返回 null", DataPath.parse("a.b[9]").get(tree) == null);
        expectSyntaxError("空下标会报错", () -> DataPath.parse("a[]"));

        // ③ 反射桥：读（数据 vs 行为）
        ActorLiXiaoYan probe = new ActorLiXiaoYan(125);
        NbtCompound data = DataBridge.toCompound(probe);
        check("数据里有 hp", data.get("hp") != null && data.get("hp").asLong() == probe.getHp());
        check("数据里有子类字段 ignition", data.get("ignition") != null && data.get("ignition").asLong() == probe.getIgnition());
        check("数据里有 name", data.get("name") != null && probe.getName().equals(data.get("name").asString()));
        // 数据名是对外 API：Java 字段名难看时用 @DataField 改名（见 TIPS §5.10）。
        // 这一批 Java 名已经订正过（2026-10-03），所以数据名与 Java 名现在一致 —— 但**数据名一个字都没变**。
        check("暴击率的数据名是 baseCriticalRate（2026-10-24 起的规范名）",
                data.get("baseCriticalRate") != null);
        check("存活标志的数据名是小写 alive（不是 Alive）",
                data.get("alive") != null && data.get("Alive") == null);
        // ⚠️ 这一条盯着**静默失效**：{@code DataBridge#trySetter} 是拿 Java 字段名拼 setter 名的
        // （{@code "set" + field.getName()}），所以字段改名时 setter 名必须跟着改 ——
        // 漏改的话 setter 找不到，写回会**退化成裸写字段**：命令照旧报成功、值也照样变，
        // 只是钳制与副作用没了（最阴的一种回归，2026-10-03 订正字段名时就差点踩上）。
        // 判据：把 setter 的名字换成反着的哨兵值 —— setter 生效时它会被**整体覆盖**，
        // 只裸写字段时哨兵还在。这里顺带把"这个字段真的能写"也验了。
        double beforeEnhance = probe.getEnhance();
        probe.setEnhance(1234.5d);
        check("写回契约：DataBridge#merge 走的是 setter（setter 生效时哨兵值被整体覆盖，"
                        + "不是只改了字段、把 setter 绕过去）",
                DataBridge.merge(probe, Snbt.parseCompound("{enhance:0.5}")) == 1
                        && probe.getEnhance() == 0.5d
                        && DataBridge.toCompound(probe).get("enhance").asDouble() == 0.5d);
        probe.setEnhance(beforeEnhance);
        check("改名之后照样能写（setter 是按 Java 字段名找的，所以 setCriticalRate 仍然命中）",
                DataBridge.merge(probe, Snbt.parseCompound("{baseCriticalRate:0.5}")) == 1
                        && probe.getCriticalRate() == 0.5d
                        && DataBridge.toCompound(probe).get("baseCriticalRate").asDouble() == 0.5d);
        check("效果列表与成长系数的数据名都换成了短名字",
                DataBridge.dataNames(probe.getClass()).containsAll(List.of(
                        "effects", "hpGrow", "attackGrow", "defenceGrow", "metalManaGrow")));
        // 上一条只钉住 criticalRate 一个；这里把规则推广到**每个**能写的标量字段。
        // 扫描口径直接借 {@code DataBridge#accessors}（= /data 看到的那一面）：
        // 它会展开 @DataFlatten 组件，于是"字段搬进属性组件"之后这条断言**不会缩水**
        // （只扫实体自己声明的话，搬走的字段会静默从覆盖面里消失 = 假绿）。
        // 判据与 DataBridge#trySetter 一致：setter 找的是**字段所在那个对象**的类
        // （组件里的字段就找组件自己的 setXxx，实体上的转发 setter 不会被 /data 用到）。
        // 两个例外（**只读**：dump 得出来、但从来不是"写入口"）：
        //   ignitionMax —— 派生值，getIgnitionMax() 按当前血量现算（低血 15 / 满血 10）；
        //   lastIgnition —— 内部记账（上一次的燃点，用来算【燃点】变动带来的面积增量）。
        // 它们本来就没有 setter，算进来只会变成假失败。
        java.util.Set<String> readOnlyScalars = new java.util.HashSet<>(
                java.util.Arrays.asList("ignitionMax", "lastIgnition"));
        List<String> missingSetters = new ArrayList<>();
        List<String> scannedWritable = new ArrayList<>();
        for (cn.gfhnv.game.data.DataBridge.DataAccessor accessor
                : cn.gfhnv.game.data.DataBridge.accessors(probe)) {
            java.lang.reflect.Field field = accessor.field();
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
            if (!isScalarType(field.getType())) continue;
            if (java.lang.reflect.Modifier.isFinal(field.getModifiers())) continue;
            if (readOnlyScalars.contains(field.getName())) continue;
            scannedWritable.add(field.getName());
            if (hasMatchingSetter(accessor.container().getClass(), field)) continue;
            missingSetters.add(field.getName());
        }
        check("前提：这条扫描真的下潜进了 @DataFlatten 组件（字段搬进组件后不会从覆盖面里静默消失）"
                        + "—— 扫到 " + scannedWritable.size() + " 个能写的标量",
                scannedWritable.contains("attackEnhancePercent")
                        && scannedWritable.contains("metalResistance"));
        check("写回契约：每个 /data 能写的标量字段都挂着一个同名 setter（字段改名时 setter 必须跟着改）"
                + "—— 缺 setter 的：" + missingSetters, missingSetters.isEmpty());
        // 这一条盯着"搬家把副作用搬丢了"：{@code setHpEnhancePercent} 原来在实体上，
        // 除了赋值还会 {@code renewHp()} 把当前生命夹回新上限。它搬进属性组件之后，
        // {@code /data merge} 的写回落点是**组件**，副作用必须跟着搬过去 ——
        // 否则改小生命增强就会留下 hp > 上限的怪状态（实测踩到过：4664 血 / 466 上限）。
        ActorLiXiaoYan hpProbe = new ActorLiXiaoYan(125);
        long hpCapBefore = hpProbe.getHpMax();
        DataBridge.merge(hpProbe, Snbt.parseCompound("{hpEnhancePercent:-0.9}"));
        check("写回契约：把生命增强改小之后当前生命被夹回新上限（副作用跟着字段搬走了）—— "
                        + hpCapBefore + " → hp " + hpProbe.getHp() + " / 上限 " + hpProbe.getHpMax(),
                hpProbe.getHpMax() < hpCapBefore && hpProbe.getHp() == hpProbe.getHpMax());
        // 这条盯着一个踩过的坑：字段搬进 @DataFlatten 组件之后，组件类型必须进
        // DataBridge#isDataObject 白名单 —— 漏了的话整个组件在 /data 里**静默消失**
        // （不报错、只是查不到）。所以这里断言的是"组件字段真的 dump 出来了一个"，
        // 而不只是"数据名清单里有它"（清单是按字段算的，加不加白名单都一样）。
        // 同时确认组件本身不是一个键（键名是**无前缀**的组件字段名）。
        check("组件里的字段照常进数据视图（@DataFlatten 没有因为白名单漏了组件类型而静默消失）",
                data.get("metalResistance") != null && data.get("attributes") == null
                        && data.get("elements") == null);
        // 从实体搬进组件的三个「全局组」面板属性，也必须是**顶层**键（不是 attributes.xxx）
        check("搬进组件的全局组面板属性仍是顶层键（penetration / enhance / baseCriticalDamage 不带前缀）",
                data.get("penetration") != null && data.get("enhance") != null
                        && data.get("baseCriticalDamage") != null);
        check("试算标志 anticipating 被 @NoData 排除（它只在试算期间为 true，dump 出来是噪音）",
                !DataBridge.dataNames(probe.getClass()).contains("anticipating"));
        check("uuid 可见（只读，因为它是 final）", data.get("uuid") != null);
        check("controller 不进数据（行为不是数据）", data.get("controller") == null);
        check("物理对象不进数据", data.get("force") == null && data.get("velocity") == null);
        check("战斗引用不进数据", data.get("participateFight") == null);
        // 这条盯着一个踩过的坑：DataBridge 里若定义了一个叫 Slot 的内部类型，
        // 会把 import 的 inventory.Slot 遮住，背包就 dump 成空壳了。
        check("背包格照常进数据（Slot 没被同名内部类遮掉）",
                data.get("inventory") instanceof NbtCompound inventory && inventory.get("slots") instanceof NbtList slots
                        && slots.size() == 63);
        // "被引用的实体只留 uuid"那条规则**只对非根对象生效**：根对象照旧全量展开。
        // 这两条与下面 testNestedEntityReference() 里那几条是一对 —— 少了这里，
        // 把 /data get entity @s 本身压成一个 uuid 也没人拦得住。
        check("根对象仍然全量展开（不是只剩一个 uuid） —— 关键数目 " + data.size(),
                data.size() > 10 && data.get("name") != null && data.get("manas") != null);
        check("根对象自己的 uuid 照旧看得见（与\"被引用的实体只给 uuid\"不冲突）",
                data.get("uuid") != null);
        // 这条盯着"参数节点被挂两次"：data 下不该出现游离的「值」子节点。
        check("/data 下没有游离的「值」子节点",
                !CommandManager.getDispatcher().getCommandNode("data").getChildrenNames().contains("值"));

        // ④ 反射桥：写（能走 setter 就被 setter 的钳制管住）
        probe.setHp(probe.getHpMax());
        DataBridge.merge(probe, Snbt.parseCompound("{hp:99999999}"));
        check("写 hp 会被 setHp 钳到生命上限", probe.getHp() == probe.getHpMax());
        // 这一条盯着"搬字段把副作用搬丢"的另一半：{@code setHp} 除了夹血，还要发
        // {@code HpLossEvent} / {@code HpRestorationEvent}（{@code HurtCommand} / {@code KillCommand}
        // 的 javadoc 明确承诺模组可以监听）。副作用现在住在属性组件里（{@code /data} 的写回落点），
        // 所以事件也要一起钉住 —— 光断言"血被夹住了"抓不住"事件没人发"。
        ProbeHpEventListener hpEvents = new ProbeHpEventListener();
        EventBus.register(hpEvents);
        try {
            ActorLiXiaoYan eventProbe = new ActorLiXiaoYan(125);
            long eventHpMax = eventProbe.getHpMax();
            DataBridge.merge(eventProbe, Snbt.parseCompound("{hp:1}"));
            int lossesAfterHit = hpEvents.losses;
            DataBridge.merge(eventProbe, Snbt.parseCompound("{hp:99999999}"));
            check("写回契约：/data 写 hp 会发 HpLossEvent（扣血）与 HpRestorationEvent（回血），"
                            + "并被夹回上限 —— 扣血事件 " + lossesAfterHit + " 次、回血事件 "
                            + hpEvents.restorations + " 次，hp " + eventProbe.getHp()
                            + " / 上限 " + eventHpMax,
                    lossesAfterHit == 1 && hpEvents.restorations == 1
                            && eventProbe.getHp() == eventHpMax);
        } finally {
            EventBus.unregister(hpEvents);
        }
        DataBridge.merge(probe, Snbt.parseCompound("{ignition:99}"));
        check("写 ignition 会被夹到上限", probe.getIgnition() == probe.getIgnitionMax());
        DataBridge.merge(probe, Snbt.parseCompound("{ignition:-5}"));
        check("写 ignition 负数会被夹到 0", probe.getIgnition() == 0);
        expectSyntaxError("写不存在的字段会报错", () -> DataBridge.merge(probe, Snbt.parseCompound("{noSuchField:1}")));
        expectSyntaxError("写 final 字段（uuid）会被拒绝", () -> DataBridge.merge(probe, Snbt.parseCompound("{uuid:\"x\"}")));

        // ⑤ 命令层（走真实解析与执行）
        LivingThing previous = CommandManager.getPlayer();
        CommandManager.setPlayer(probe);
        run("data get entity @s", true);
        run("data get entity @s hp", true);
        run("data get entity @s noSuchThing", false);
        run("data merge entity @s {hp:1}", true);
        check("命令写入真的生效了", probe.getHp() == 1L);
        run("data merge entity @s {hp: 2}", true);
        check("带空格的 NBT 也能解析（readBalanced）", probe.getHp() == 2L);
        run("data merge entity @s {noSuchField:1}", false);
        run("data merge entity @s {", false);
        run("data get entity @e[type=ThisTypeDoesNotExist]", false);
        run("data", false);
        CommandManager.setPlayer(previous);
    }

    private static void testNestedEntityReference() {
        section("被引用的实体在 /data 里只留 uuid");

        NestedReferenceProbeEntity owner = new NestedReferenceProbeEntity("引用探针", "probe:nestedReference");
        NestedReferenceProbeEntity buddy = new NestedReferenceProbeEntity("被引用者", "probe:nestedReferenceBuddy");
        owner.setBuddy(buddy);
        owner.setSelf(owner);

        // 先证明"被引用者自己是个完整对象"：把它当根 dump，字段远不止一个 uuid。
        // 否则下面那条"只给 uuid"可能只是因为 buddy 本来就没什么字段，证明不了什么。
        NbtCompound buddyAsRoot = DataBridge.toCompound(buddy);
        check("对照组：被引用的那个实体自己当根时是全量展开的（" + buddyAsRoot.size() + " 个键）",
                buddyAsRoot.size() > 10 && buddyAsRoot.get("name") != null);

        NbtCompound dump = DataBridge.toCompound(owner);
        String text = dump.toSnbt();
        System.out.println("  [信息] 引用探针的键（" + dump.size() + " 个）：" + dump.keySet());
        System.out.println("  [信息] 根里的 buddy = " + dump.get("buddy")
                + "，根里的 self = " + dump.get("self"));

        check("根对象照旧全量展开：引用探针 dump 出来 " + dump.size()
                        + " 个键（不是被压成一个 uuid）",
                dump.size() > 10 && dump.get("name") != null && dump.get("hp") != null);
        check("被引用的实体只输出 uuid（buddy 那一格只有 uuid 一个键）—— 实际 "
                        + (dump.get("buddy") == null ? "（无）" : dump.get("buddy").toSnbt()),
                dump.get("buddy") instanceof NbtCompound buddyTag && buddyTag.size() == 1
                        && buddyTag.get("uuid") != null
                        && buddyTag.get("uuid").asString().equals(buddy.getUUID()));
        check("被引用者自己的字段一个都没混进来（整份 dump 里搜不到它的名字）—— 被引用者叫「"
                        + buddy.getName() + "」",
                !text.contains(buddy.getName()));
        check("指回自己的引用也只给 uuid（这条不成立就是递归，会直接栈溢出）",
                dump.get("self") instanceof NbtCompound selfTag && selfTag.size() == 1
                        && selfTag.get("uuid") != null
                        && selfTag.get("uuid").asString().equals(owner.getUUID()));
        check("同一份数据 dump 两次逐字符一致",
                text.equals(DataBridge.toCompound(owner).toSnbt()));
        check("写法上与只读导出无关的两条：短标识取自 uuid 前 6 位、长度够区分",
                owner.getShortUuid().equals(owner.getUUID().substring(0, 6))
                        && owner.getShortUuid().length() == 6);
    }

    /**
     * 跑本主题的全部用例（顺序 = 下面列的顺序）。
     */
    public static void runAll() throws Exception {
        testDataCommand();
        testNestedEntityReference();
    }

    private DataCommandTests() {
    }
}
