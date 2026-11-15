package cn.gfhnv.debug_tools;

import static cn.gfhnv.debug_tools.TestKit.summary;
import static cn.gfhnv.debug_tools.TestKit.useUtf8Output;

/**
 * 自测的**入口 / 调度器**：按主题分给各个 {@code *Tests} 类，这里只"按顺序跑一遍"。
 * <p>
 * 规则（2026-10-25 起）：<b>不同内容的测试写进不同的类</b>，一个类一个主题；
 * 断言底座在 {@link TestKit}，共用工具在 {@link TestSupport}，探针类型在 {@link TestProbes}。
 * <p>
 * 加新测试：找到对应主题的 {@code *Tests} 类 → 加一个 {@code private static void testXxx()}
 * → 在它的 {@code runAll()} 里按位置调用；<b>主题是新的</b>就新建一个 {@code XxxTests} 类，
 * 并在下面的 {@code main} 里加一行。不要动 {@code TestKit}（那是所有类共用的契约）。
 */
public final class TestCommandSystem {

    private TestCommandSystem() {
    }

    /**
     * 测试入口：按顺序跑所有主题（顺序有意义 —— 配置类测试会改全局状态）。
     *
     * @param args 忽略
     */
    public static void main(String[] args) throws Exception {
        // 2026-11-15 第 73 批：关掉暴击掷骰 —— 玩家角色有 5% 暴击率之后，
        // "比较两次伤害"的断言会随掷骰间歇性失败；专门验证暴击的用例会自己临时打开。
        cn.gfhnv.game.damage.DamageCalculate.setCriticalEnabledForSelfTest(false);
        useUtf8Output();
        System.out.println("========== 命令系统自测开始 ==========");

        CmdSyntaxTests.runAll();
        DamageTests.runAll();
        FlameReaverTests.runAll();
        EntityCoreTests.runAll();
        ManaTests.runAll();
        CharacterTests.runAll();
        SelectionTests.runAll();
        DataCommandTests.runAll();
        EventBusTests.runAll();
        DisplayTests.runAll();
        CombatCoreTests.runAll();
        ExtensionEventTests.runAll();
        FightLifecycleTests.runAll();
        MortalBladeTests.runAll();
        DamageDisplayTests.runAll();
        CommandQuoteTests.runAll();
        DataModifyTests.runAll();
        EffectLifecycleTests.runAll();
        ConfigTests.runAll();
        SkillDataTests.runAll();
        CombatLogColorTests.runAll();
        GameRulesTests.runAll();
        ConfigOutputTests.runAll();
        SummonTests.runAll();

        summary();
    }
}
