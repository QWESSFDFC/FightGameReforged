package cn.gfhnv.debug_tools;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.configLoadingSystem.DataKeys;
import cn.gfhnv.game.system.configLoadingSystem.GameRules;
import cn.gfhnv.game.system.mana.Mana;

import java.util.List;

import static cn.gfhnv.debug_tools.TestKit.*;

/**
 * 自测：**五行法力池**（{@link LivingThing#initialMana()}）。
 * <p>
 * 主题：按元素属性重建法力表 —— 条数与顺序、每条上限的公式
 * （{@code 成长 * (等级-1) + 基数}），以及"<b>只有主元素拿 {@code mainBase}</b>"这条区分。
 * 这三条正是 {@code initialMana()} 里那张五行表的价值所在：改成按元素遍历之后，
 * "哪个成长系数配给哪个元素""谁拿主基数"一旦错位，数值会静默变掉。
 * <p>
 * 由 {@code TestCommandSystem#main} 按顺序调用 {@code ManaTests#runAll()}。
 *
 * @author AI（DeepSeek）生成
 */
public final class ManaTests {

    /**
     * 五行法力表的固定顺序（金木水火土）—— 与 {@link LivingThing#initialMana()} 内部遍历的顺序一致。
     */
    private static final List<ElementSort> FIVE_ELEMENTS = List.of(
            ElementSort.METAL, ElementSort.WOOD, ElementSort.WATER, ElementSort.FIRE, ElementSort.DIRT);

    /**
     * 探测等级：取 11 让 {@code 等级 - 1 = 10}，成长系数差 1 就能在数值上差 10，不会和基数混淆。
     */
    private static final long PROBE_LEVEL = 11L;

    /**
     * 法力表必须是 5 条、顺序金木水火土，每条上限 = 该元素成长 * (等级-1) + 基数；
     * 主元素拿 {@code mainBase}，其余 4 条拿 {@code otherBase}。
     */
    private static void testInitialManaRebuild() {
        section("五行法力：initialMana() 按元素重建 5 条法力");
        long mainBase = GameRules.getLong(DataKeys.Rule.Mana.MAIN_BASE);
        long otherBase = GameRules.getLong(DataKeys.Rule.Mana.OTHER_BASE);
        StringBuilder problems = new StringBuilder();
        for (ElementSort main : FIVE_ELEMENTS) {
            LivingThing probe = livingOf(main, PROBE_LEVEL);
            List<Mana> manas = probe.getManas();
            if (manas.size() != FIVE_ELEMENTS.size()) {
                problems.append(main).append("：法力 ").append(manas.size()).append(" 条（应为 5）；");
                continue;
            }
            for (int i = 0; i < FIVE_ELEMENTS.size(); i++) {
                ElementSort element = FIVE_ELEMENTS.get(i);
                Mana mana = manas.get(i);
                if (mana.getElementSort() != element) {
                    problems.append(main).append("：第 ").append(i + 1).append(" 条是 ")
                            .append(mana.getElementSort()).append("（应为 ").append(element).append("）；");
                    continue;
                }
                double expected = manaGrowOf(probe, element) * (PROBE_LEVEL - 1)
                        + (element == main ? mainBase : otherBase);
                if (Double.compare(mana.getAmountMax(), expected) != 0
                        || Double.compare(mana.getAmount(), expected) != 0) {
                    problems.append(main).append("/").append(element).append("：上限 ")
                            .append(mana.getAmountMax()).append("、当前 ").append(mana.getAmount())
                            .append("（应为 ").append(expected).append("）；");
                }
            }
        }
        check("五行法力：每个元素的实体都建出 5 条、顺序金木水火土、上限 = 成长*(等级-1) + 基数"
                        + "（只有主元素拿 mainBase=" + mainBase + "，其余拿 otherBase=" + otherBase + "）—— "
                        + problems,
                problems.isEmpty());
    }

    /**
     * 1 级时 {@code 等级-1 = 0}，每条法力就等于基数 —— 这条盯的是"等级因子被提出循环"之后没被算错。
     */
    private static void testInitialManaAtLevelOne() {
        section("五行法力：1 级时法力就等于两个基数（等级-1 = 0）");
        long mainBase = GameRules.getLong(DataKeys.Rule.Mana.MAIN_BASE);
        long otherBase = GameRules.getLong(DataKeys.Rule.Mana.OTHER_BASE);
        StringBuilder problems = new StringBuilder();
        for (ElementSort main : FIVE_ELEMENTS) {
            LivingThing probe = livingOf(main, 1L);
            for (Mana mana : probe.getManas()) {
                double expected = mana.getElementSort() == main ? mainBase : otherBase;
                if (Double.compare(mana.getAmountMax(), expected) != 0) {
                    problems.append(main).append("/").append(mana.getElementSort()).append("：上限 ")
                            .append(mana.getAmountMax()).append("（应为 ").append(expected).append("）；");
                }
            }
        }
        check("五行法力：1 级的法力上限只有基数（主元素 " + mainBase + "，其余 " + otherBase + "）—— "
                        + problems,
                problems.isEmpty());
    }

    /**
     * {@link ElementSort#UNIVERSAL} 不属于五行：它一条法力都不该有。
     * <p>
     * 这条是"不能直接遍历 {@code ElementSort.values()}"的守卫 —— 那样会给它凭空造出第 6 条法力。
     */
    private static void testUniversalGetsNoMana() {
        section("五行法力：UNIVERSAL 不建法力（不能遍历 ElementSort.values()）");
        LivingThing probe = livingOf(ElementSort.UNIVERSAL, PROBE_LEVEL);
        check("元素属性是 UNIVERSAL 时法力表为空 —— 实际 " + probe.getManas().size() + " 条",
                probe.getManas().isEmpty());
        check("法力表为空时 getMana(...) 返回 null（查询不抛异常）",
                probe.getMana(ElementSort.METAL) == null && probe.getMana(ElementSort.UNIVERSAL) == null);
    }

    /**
     * 造一个 14 参构造器铺好五行法力成长表的探针实体。
     *
     * @param element 元素属性
     * @param level   等级
     * @return 探针实体（构造器末尾已经调过 {@code initialMana()}）
     */
    private static LivingThing livingOf(ElementSort element, long level) {
        return new LivingThing("法力探针", "manaProbe", 0, 0, 0, 0, 0, 100, level, "test", 10, 5, 5, element);
    }

    /**
     * @param living  探针实体
     * @param element 五行之一
     * @return 该元素的法力成长系数（读实体自己的 getter —— 表由构造器铺，这里只验"配给谁"）
     */
    private static double manaGrowOf(LivingThing living, ElementSort element) {
        return switch (element) {
            case METAL -> living.getMetalManaGrow();
            case WOOD -> living.getWoodManaGrow();
            case WATER -> living.getWaterManaGrow();
            case FIRE -> living.getFireManaGrow();
            case DIRT -> living.getDirtManaGrow();
            // UNIVERSAL 不进循环（它没有五行法力）
            case UNIVERSAL -> 0;
        };
    }

    /**
     * 跑本主题的全部用例。
     */
    public static void runAll() {
        testInitialManaRebuild();
        testInitialManaAtLevelOne();
        testUniversalGetsNoMana();
    }

    private ManaTests() {
    }
}
