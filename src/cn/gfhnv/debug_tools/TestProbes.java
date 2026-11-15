package cn.gfhnv.debug_tools;

import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.damage.DamageType;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entityController.UniversalController;
import cn.gfhnv.game.event.DamageEvent;
import cn.gfhnv.game.event.Event;
import cn.gfhnv.game.interfaces.IModifyDamage;
import cn.gfhnv.game.mod.Mod;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;

import java.util.ArrayList;
import java.util.List;

/**
 * 自测用的**探针类型**（假实体、假模组、假效果、假技能、假监听器）。
 * <p>
 * 它们以前是 {@code TestCommandSystem} 的嵌套类；2026-10-25 拆出来独立成文件 ——
 * runner 只负责"按顺序调用各个测试类"。
 */
public final class TestProbes {

    private TestProbes() {
    }

    /**
     * 可以抛异常的操作。
     *
     * @author AI（DeepSeek）生成
     */
    @FunctionalInterface
    interface ThrowingAction {

        /**
         * 执行。
         *
         * @throws Exception 任意异常
         */
        void run() throws Exception;
    }

    /**
     * 自测用的假事件（<b>不参与任何游戏逻辑</b>）。派发记录写在事件自己身上，方便断言。
     */
    static class ProbeBusEvent extends Event {
        /**
         * 派发记录（子类继承不到 private，这里故意用包内可见，方便自测直接断言）。
         */
        final List<String> log = new ArrayList<>();
    }

    /**
     * 假事件的子类：验证"post 子类事件，监听父类的也能收到"（旧实现只按精确类匹配）。
     */
    static class ProbeBusSubEvent extends ProbeBusEvent {
    }

    /**
     * priority=1 的假监听器（最先跑）。
     */
    static class ProbeFirstListener {
        final java.util.function.Consumer<ProbeBusEvent> firstAction;

        ProbeFirstListener(java.util.function.Consumer<ProbeBusEvent> firstAction) {
            this.firstAction = firstAction;
        }

        @SubscribeEvent(priority = 1)
        public void on(ProbeBusEvent event) {
            firstAction.accept(event);
        }
    }

    /**
     * priority=3 的假监听器（中间跑）。
     */
    static class ProbeMiddleListener {
        final java.util.function.Consumer<ProbeBusEvent> middleAction;

        ProbeMiddleListener(java.util.function.Consumer<ProbeBusEvent> middleAction) {
            this.middleAction = middleAction;
        }

        @SubscribeEvent(priority = 3)
        public void on(ProbeBusEvent event) {
            middleAction.accept(event);
        }
    }

    /**
     * priority=5 的假监听器（最后跑）。
     */
    static class ProbeLastListener {
        final java.util.function.Consumer<ProbeBusEvent> lastAction;

        ProbeLastListener(java.util.function.Consumer<ProbeBusEvent> lastAction) {
            this.lastAction = lastAction;
        }

        @SubscribeEvent(priority = 5)
        public void on(ProbeBusEvent event) {
            lastAction.accept(event);
        }
    }

    /**
     * 一个"标量属性槽位"：字段本身 + <b>它该挂到哪个对象上</b>。
     * <p>
     * 为什么要带 owner：属性可能住在实体的 {@code @DataFlatten} 组件里，
     * 那时候 {@code field.set(实体, 值)} 会抛
     * {@code Can not set double field …AttributeProfile.metalResistance to …PlayerOne} ——
     * 必须把值写进<b>组件实例</b>（也就是 {@code 实体.attributes.metalResistance}）。
     *
     * @param owner 承载这个字段的对象（实体自己，或它的组件）
     * @param field 字段
     */
    record ScalarSlot(Object owner, java.lang.reflect.Field field) {
    }

    /**
     * 一个"假模组"：实现了 {@link cn.gfhnv.game.mod.config.ModDataAware}，
     * 只为了验证配置加载链路，不注册任何内容。
     * <p>
     * 下面几个带 {@code @ModConfig} 注解的子类复用它这套"记下读到的值"的写法。
     * 每个注解场景要一个自己的类：{@code @ModConfig} <b>没有</b> {@code @Inherited}，
     * 注解必须写在模组主类自己头上（写在父类上游戏读不到 —— 这正是要钉住的边界之一）。
     *
     * @author AI（DeepSeek）生成
     */
    static class ProbeMod extends Mod
            implements cn.gfhnv.game.mod.config.ModDataAware {

        /**
         * applyConfig 里要不要抛一个 {@link Error}（验证"最坏情况"也不会带崩加载链）。
         */
        final boolean throwInConfig;
        /**
         * applyConfig 有没有被调用。
         */
        boolean called = false;
        /**
         * 从配置里读到的层数（读不到就是默认值 2）。
         */
        int stacks = 2;

        /**
         * @param modId 模组 id
         */
        ProbeMod(String modId) {
            this(modId, false);
        }

        /**
         * @param modId         模组 id
         * @param throwInConfig applyConfig 里是否抛异常
         */
        ProbeMod(String modId, boolean throwInConfig) {
            super(modId);
            this.throwInConfig = throwInConfig;
        }

        @Override
        public void applyConfig(cn.gfhnv.game.mod.config.ModConfigDocument config) {
            this.called = true;
            this.stacks = config.getInt("initialStacks", 2);
            if (throwInConfig) {
                throw new AssertionError("自测：故意在 applyConfig 里抛一个 Error");
            }
        }
    }

    /**
     * "没实现 ModDataAware"的假模组（用来验证"可选接口"这条契约：不实现就正常跳过）。
     *
     * @author AI（DeepSeek）生成
     */
    static final class PlainProbeMod extends Mod {

        /**
         * @param modId 模组 id
         */
        PlainProbeMod(String modId) {
            super(modId);
        }
    }

    /**
     * 带 {@link cn.gfhnv.game.mod.config.ModConfig} 注解的假模组：注解里的分组名
     * （{@code "  annotatedGroup  "}，故意带首尾空白）与构造器传进来的 {@code MOD_ID}
     * <b>故意不一样</b>，用来钉住「<b>注解 &gt; {@code MOD_ID}</b>」这条优先级，
     * 以及"注解 id 的首尾空白会被去掉"。
     *
     * @author AI（DeepSeek）生成
     */
    @cn.gfhnv.game.mod.config.ModConfig(id = "  annotatedGroup  ")
    static final class AnnotatedProbeMod extends ProbeMod {

        /**
         * @param modId 模组 id（与注解里的分组名故意不同）
         */
        AnnotatedProbeMod(String modId) {
            super(modId);
        }
    }

    /**
     * 注解 {@code id} 只有空白的假模组：钉住"空白串不算写了分组名"—— 退回 {@code MOD_ID}。
     *
     * @author AI（DeepSeek）生成
     */
    @cn.gfhnv.game.mod.config.ModConfig(id = "   ")
    static final class BlankAnnotatedProbeMod extends ProbeMod {

        /**
         * @param modId 模组 id（注解 id 只有空白时用的就是它）
         */
        BlankAnnotatedProbeMod(String modId) {
            super(modId);
        }
    }

    /**
     * 把注解里的分组名写成<b>相对路径</b>的假模组：{@code ./config/data/<注解 id>.json} 字面上正好落在
     * 自测自己的 {@code out/tmpModConfig/} 临时目录里，用来证明<b>游戏默认入口
     * （{@code loadModData(Mod)}）用的文件名也是注解 id</b>（跟 {@code MOD_ID} 走的话那个文件根本不存在），
     * 而且全程不往真 {@code config/} 里写任何文件。
     *
     * @author AI（DeepSeek）生成
     */
    @cn.gfhnv.game.mod.config.ModConfig(id = "../../out/tmpModConfig/annotatedGroup")
    static final class AnnotatedPathProbeMod extends ProbeMod {

        /**
         * @param modId 模组 id（故意取一个绝对没有配置文件的名字）
         */
        AnnotatedPathProbeMod(String modId) {
            super(modId);
        }
    }

    /**
     * 自测用的「免死」修正器。
     * <p>
     * 和白厄的免死（{@code Phainon#soulscorchDeathWard()}）同一套写法：致死伤害被拦下、
     * 血量锁 1，并用一个标记保证一场只触发一次；试算期间只算数、不消费。
     * 这里不依赖战斗上下文，所以可以脱离 {@code Fight} 单独验证链路。
     *
     * @author AI（DeepSeek）生成
     */
    static class ProbeDeathWard implements IModifyDamage {

        /**
         * 免死是否已经被消费掉（试算不算消费）。
         */
        boolean consumed = false;

        /**
         * 是否有过一次「在试算期间被调用」的记录。
         */
        boolean calledWhileAnticipating = false;

        @Override
        public long damageModify(long newHp, DamageEvent da) {
            if (newHp > 0) {
                return newHp;
            }
            if (da.getAttackedEntity().isAnticipating()) {
                calledWhileAnticipating = true;
                return 1;
            }
            consumed = true;
            return 1;
        }

        /**
         * @return 免死是否已经被消费
         */
        boolean wasConsumed() {
            return consumed;
        }

        /**
         * @return 免死是否有过「在试算期间被调用」的记录
         */
        boolean wasCalledWhileAnticipating() {
            return calledWhileAnticipating;
        }
    }

    /**
     * 自测用的「血量下限」修正器（类似李晓焰的记忆生命）。
     *
     * @author AI（DeepSeek）生成
     */
    static class ProbeHpFloor implements IModifyDamage {

        /**
         * 生命上限的比例下限。
         */
        final double rate;

        /**
         * @param rate 生命上限的比例下限（0.5 表示不低于半血）
         */
        ProbeHpFloor(double rate) {
            this.rate = rate;
        }

        @Override
        public long damageModify(long newHp, DamageEvent da) {
            long minHp = (long) (da.getAttackedEntity().getHpMax() * rate);
            return Math.max(newHp, minHp);
        }
    }

    /**
     * 自测用的「无视防御」效果。
     * <p>
     * 它<b>不是</b>官方内容里的 {@code IgnoreDefenceEffect}，只实现了
     * {@link cn.gfhnv.game.interfaces.IDefenceIgnore}：用来证明伤害计算认的是接口，
     * 模组自己写的穿甲效果一样会被算进去。
     *
     * @author AI（DeepSeek）生成
     */
    static class ProbeDefenceIgnoreEffect extends cn.gfhnv.game.effect.Effect
            implements cn.gfhnv.game.interfaces.IDefenceIgnore {

        /**
         * 无视防御的百分比。
         */
        final double percent;

        /**
         * 无视防御的固定值。
         */
        final long amount;

        /**
         * 构造效果。
         *
         * @param percent 无视防御百分比
         * @param amount  无视防御固定值
         */
        ProbeDefenceIgnoreEffect(double percent, long amount) {
            super("probeDefenceIgnoreEffect");
            this.percent = percent;
            this.amount = amount;
        }

        /**
         * 复制构造器。
         *
         * @param other 被复制的效果
         */
        ProbeDefenceIgnoreEffect(ProbeDefenceIgnoreEffect other) {
            super(other.getID());
            this.percent = other.percent;
            this.amount = other.amount;
        }

        @Override
        public double getIgnoreDefencePercent() {
            return percent;
        }

        @Override
        public long getIgnoreDefenceAmount() {
            return amount;
        }

        @Override
        public void comeIntoEffect(LivingThing thing) {
            // 标记型效果：不需要每回合做事（顺便避免基类占位实现打印提示）
        }

        @Override
        public cn.gfhnv.game.effect.Effect copy() {
            return new ProbeDefenceIgnoreEffect(this);
        }
    }

    /**
     * 自测用的大倍率真伤害技能：走正常的 {@code user.makeDamage(target, this)} 路径。
     * <p>
     * 与 {@link ProbeSkill} 的区别：那个只记日志、不产生伤害，所以驱动不了"致死伤害 → 免死"。
     * 本类服务 {@code testAwakenInterruptExitProtocol}（真实回合循环里的白厄的免死与退出协议）。
     *
     * @author AI（DeepSeek）生成
     */
    static class ProbeDamageSkill extends Skill {

        /**
         * @param atkMagnification 攻击力倍率（拉满就能打出致死伤害，与数值平衡无关）
         */
        ProbeDamageSkill(double atkMagnification) {
            super("探针-重击", "自测用假技能", 0, atkMagnification, 0, -1);
            this.setCoolDown(0);
        }

        /**
         * @param other 被复制的技能
         */
        ProbeDamageSkill(ProbeDamageSkill other) {
            super(other);
        }

        @Override
        public Skill copy() {
            return new ProbeDamageSkill(this);
        }

        @Override
        public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
            for (LivingThing target : new ArrayList<>(enemies)) {
                user.makeDamage(target, this, DamageType.SpecialSkill);
            }
        }
    }

    /**
     * 自测用的"脆皮挨打探针"：只有 2 点血，靠"它死没死"就能数出「最后一击排了几刀」。
     * <p>
     * 白厄的最后一击倍率是 {@code 13 × (1 − 剩余额外回合 × 0.125) × 攻击} ——
     * 一刀必死；同一圈排了两刀的话每一刀的倍率会对半砍，2 血反而打不掉。
     * 速度拉到 500，保证它总是<b>先手</b>行动（用例要打的是"白厄挨打"，不是"白厄打它"）。
     *
     * @author AI（DeepSeek）生成
     */
    public static class ProbeFragileDummy extends LivingThing {

        /**
         * 构造探针。
         */
        public ProbeFragileDummy() {
            super("脆皮探针", "fragileProbe", 0.0, 500.0, 0.0, 0.0, 0.0,
                    500L, 1, "insect", 2, 2, 2, ElementSort.FIRE);
            this.setHpMax(2);
            this.setHp(2);
            // 控制器不能为 null：LivingThing 的拷贝构造器会照着它重建一个
            this.setController(new UniversalController(new ArrayList<>(List.of(new ProbeDamageSkill(100000.0))), this));
        }

        /**
         * @param other 被复制的实体
         */
        public ProbeFragileDummy(ProbeFragileDummy other) {
            super(other);
        }

        @Override
        public LivingThing copy() {
            return new ProbeFragileDummy(this);
        }
    }

    /**
     * 自测用的"跑完一个回合就叫停回合循环"的开关。
     * <p>
     * 回合循环是 {@code while (isDriving)}，而每一圈末尾都会给行动者再排一个回合 ——
     * 不叫停就会一直转下去（自测会挂住）。{@code EffectUpdateEvent} 是每一圈的<b>最后一个</b>
     * 事件（在 {@code lastExecuteList} 之后），所以在它里面把
     * {@link cn.gfhnv.game.GameMain#setFightInProgressForSelfTest(boolean)} 摆成 false，
     * 循环下一圈开头的硬门就会 {@code break}。
     *
     * @author AI（DeepSeek）生成
     */
    static class ProbeStopAfterOneTurn {

        /**
         * 已经跑完的回合数。
         */
        int seenTurns = 0;

        /**
         * 收到回合末尾事件就叫停。
         *
         * @param event 回合末尾事件
         */
        @cn.gfhnv.game.annotation.SubscribeEvent
        public void onTurnEnd(cn.gfhnv.game.event.EffectUpdateEvent event) {
            seenTurns++;
            if (seenTurns >= 3) {
                cn.gfhnv.game.GameMain.setFightInProgressForSelfTest(false);
            }
        }
    }

    /**
     * 自测用的"数收到了几条血量事件"的监听器。
     * <p>
     * 它盯的是 {@code setHp} 的副作用：扣血发 {@link cn.gfhnv.game.event.HpLossEvent}、
     * 回血发 {@link cn.gfhnv.game.event.HpRestorationEvent}。这个副作用在 2026-10-24
     * 跟着 {@code hp} 字段搬进了属性组件（{@code /data} 的写回落点也就跟着变成组件），
     * 事件要是没跟着搬，只有"血被夹住了"那一半还看得见，模组的联动会**静默**失效。
     *
     * @author AI（DeepSeek）生成
     */
    static class ProbeHpEventListener {

        /**
         * 收到的扣血事件数。
         */
        int losses = 0;

        /**
         * 收到的回血事件数。
         */
        int restorations = 0;

        /**
         * @param event 扣血事件
         */
        @cn.gfhnv.game.annotation.SubscribeEvent
        public void onHpLoss(cn.gfhnv.game.event.HpLossEvent event) {
            losses++;
        }

        /**
         * @param event 回血事件
         */
        @cn.gfhnv.game.annotation.SubscribeEvent
        public void onHpRestoration(cn.gfhnv.game.event.HpRestorationEvent event) {
            restorations++;
        }
    }

    /**
     * 自测用的假技能：不产生任何战斗效果，只把「用了哪一招、打了谁」记进日志。
     *
     * @author AI（DeepSeek）生成
     */
    static class ProbeSkill extends Skill {
        /**
         * 日志（所有副本共享同一个列表）。
         */
        final List<String> log;

        /**
         * 构造一个作用于自身的假技能（{@code aims = 0}）。
         *
         * @param name 技能名
         * @param log  日志
         */
        ProbeSkill(String name, List<String> log) {
            this(name, log, 0);
        }

        /**
         * 构造一个假技能。
         *
         * @param name 技能名
         * @param log  日志
         * @param aims 目标数（0=自身；正数=选 N 个目标）
         */
        ProbeSkill(String name, List<String> log, int aims) {
            super(name, "自测用假技能", 0, 0, 0, aims);
            this.log = log;
        }

        /**
         * 构造一个用于伤害试算的假技能（带攻击力倍率）。
         *
         * @param name             技能名
         * @param log              日志
         * @param aims             目标数
         * @param atkMagnification 攻击力倍率（伤害计算里乘在攻击力上）
         */
        ProbeSkill(String name, List<String> log, int aims, double atkMagnification) {
            super(name, "自测用假技能", 0, atkMagnification, 0, aims);
            this.log = log;
        }

        /**
         * 复制构造器。
         *
         * @param other 被复制的技能
         */
        ProbeSkill(ProbeSkill other) {
            super(other);
            this.log = other.log;
        }

        @Override
        public Skill copy() {
            return new ProbeSkill(this);
        }

        @Override
        public void comeToEffect(Fight fight, LivingThing user) {
            log.add(getName());
        }

        @Override
        public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
            StringBuilder builder = new StringBuilder(getName()).append('→');
            if (enemies != null) {
                for (int i = 0; i < enemies.size(); i++) {
                    if (i > 0) {
                        builder.append(',');
                    }
                    builder.append(enemies.get(i).getName());
                }
            }
            log.add(builder.toString());
        }
    }

    /**
     * 自测用的"模组实体"探针：验证 {@code /summon} 的命名空间规则与"入场初始化补调"。
     * <p>
     * 它被注册两次（见 {@code testSummonCommand}）：一次短名叫 {@code commonInsect}
     * （与官方虫子同名，用来验证"短名只解析官方内容"），一次叫 {@code modOnlySummon}
     * （短名唯一，用来验证"模组实体必须写完整 id"）。
     * <p>
     * {@link #fightStartCalls} 是静态计数器：召唤出来的是 {@code copy()} 的副本，
     * 计数放在实例上就统计不到了。
     */
    public static class ProbeSummonEntity extends LivingThing {

        /**
         * {@link #whenFightStart(Fight)} 被调用的次数。
         */
        public static int fightStartCalls = 0;

        /**
         * 构造探针实体。
         */
        public ProbeSummonEntity() {
            super("召唤探针", "summonProbe", 0.0, 0.0, 0.0, 0.0, 0.0,
                    100, 1L, "insect", 10, 10, 10, ElementSort.FIRE);
            // 控制器不能为 null：LivingThing 的拷贝构造器会照着它重建一个（见 TIPS §5.5）
            this.setController(new UniversalController(new ArrayList<>(), this));
        }

        /**
         * 复制构造器。参数类型必须写成 {@link ProbeSummonEntity}，否则 {@link #copy()} 会调到自己。
         *
         * @param other 被复制的实体
         */
        public ProbeSummonEntity(ProbeSummonEntity other) {
            super(other);
        }

        @Override
        public LivingThing copy() {
            return new ProbeSummonEntity(this);
        }

        @Override
        public void whenFightStart(Fight fight) {
            fightStartCalls++;
        }
    }

    /**
     * 自测用的"模组效果"探针 A：短名唯一（{@code modOnlyEffect}），官方效果里没有这个名字。
     * <p>
     * 用来验证「模组效果必须写完整 id」：写短名要被拒绝并提示 {@code effectTestMod:modOnlyEffect}，
     * 写完整 id 则能正常施加。
     * <p>
     * <b>为什么必须 public + 公开无参构造器</b>：{@code /effect} 是用反射构造实例的
     * （{@code getConstructor(...).newInstance(...)}），类或构造器不可访问会直接失败。
     * <b>为什么和 {@link ProbeModSameNameEffect} 分成两个类</b>：运行时补全 id 是按【类】查表的
     * （{@code World#fullIdOf}），同一个类注册两条模板会互相盖掉对方的 id。
     *
     * @author AI（DeepSeek）生成
     */
    public static class ProbeModOnlyEffect extends cn.gfhnv.game.effect.Effect {

        /**
         * 构造探针效果（只写名字，等级/持续回合由命令用 setter 补上）。
         */
        public ProbeModOnlyEffect() {
            super("modOnlyEffect");
            this.getEffectTagsList().add(cn.gfhnv.game.effect.EffectTags.UNIVERSAL);
            this.setLastTime(1);
        }

        /**
         * 复制构造器。
         *
         * @param other 被复制的效果
         */
        public ProbeModOnlyEffect(ProbeModOnlyEffect other) {
            super(other.getID());
            this.setLevel(other.getLevel());
            this.setLastTime(other.getLastTime());
            this.getEffectTagsList().add(cn.gfhnv.game.effect.EffectTags.UNIVERSAL);
        }

        @Override
        public cn.gfhnv.game.effect.Effect copy() {
            return new ProbeModOnlyEffect(this);
        }

        @Override
        public void comeIntoEffect(LivingThing thing) {
            // 探针效果：不需要每回合做事（顺便避免基类占位实现打印提示）
        }
    }

    /**
     * 自测用的"模组效果"探针 B：短名故意与官方 {@code Frozen} 撞车（都是 {@code frozenEffect}）。
     * <p>
     * 用来验证「短名只解析官方内容，且撞名不算歧义」：{@code /effect @s add frozenEffect}
     * 必须挂上官方的冰冻，而不是报"匹配到 2 个效果"。
     * 它与官方效果<b>不是同一个类</b>，所以也不会影响官方实例按类补全 id。
     *
     * @author AI（DeepSeek）生成
     */
    public static class ProbeModSameNameEffect extends cn.gfhnv.game.effect.Effect {

        /**
         * 构造探针效果（短名与官方冰冻相同）。
         */
        public ProbeModSameNameEffect() {
            super("frozenEffect");
            this.getEffectTagsList().add(cn.gfhnv.game.effect.EffectTags.UNIVERSAL);
            this.setLastTime(1);
        }

        /**
         * 复制构造器。
         *
         * @param other 被复制的效果
         */
        public ProbeModSameNameEffect(ProbeModSameNameEffect other) {
            super(other.getID());
            this.setLevel(other.getLevel());
            this.setLastTime(other.getLastTime());
            this.getEffectTagsList().add(cn.gfhnv.game.effect.EffectTags.UNIVERSAL);
        }

        @Override
        public cn.gfhnv.game.effect.Effect copy() {
            return new ProbeModSameNameEffect(this);
        }

        @Override
        public void comeIntoEffect(LivingThing thing) {
            // 探针效果：不需要每回合做事（顺便避免基类占位实现打印提示）
        }
    }

    /**
     * 数"被 tick 了几次"的探针效果（用于钉 {@code lastTime} 的口径）。
     */
    static class ProbeCountingEffect extends cn.gfhnv.game.effect.Effect {

        /**
         * {@code comeIntoEffect} 被调用了几次（含挂上那一刻的 initialEffect）。
         */
        int applies = 0;

        ProbeCountingEffect(int lastTime) {
            super("probeCountingEffect", 1, lastTime);
        }

        @Override
        public void comeIntoEffect(cn.gfhnv.game.entity.LivingThing thing) {
            applies++;
        }

        @Override
        public cn.gfhnv.game.effect.Effect copy() {
            return new ProbeCountingEffect(getLastTime());
        }
    }
}
