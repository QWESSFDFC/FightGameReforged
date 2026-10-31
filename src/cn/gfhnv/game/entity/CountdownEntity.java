package cn.gfhnv.game.entity;

import cn.gfhnv.game.entityController.UniversalController;
import cn.gfhnv.game.interfaces.ISpecialAction;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.ActionSignal;
import cn.gfhnv.game.system.fight.TurnEntry;
import cn.gfhnv.game.system.fight.TurnManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;

/**
 * <b>通用「倒计时实体」</b>：在时间轴上占一个回合，到点时执行一段回调，然后自己消失。
 * <p>
 * <b>为什么不用「额外回合」凑</b>：白厄的变身连击是"给自己插几个额外回合"，那种条目的行动者是白厄本人；
 * 而"结界倒计时"必须是**另一个行动者** —— 否则它会和本体的回合混在同一条轴上（排序、延后、
 * 被 {@code getNextTurnOf} 当成"下一次正常行动"都会互相污染）。
 * <p>
 * <b>它是什么</b>：一个 {@link LivingThing} 子类（时间轴只认 {@code LivingThing} 当行动者），
 * 但<b>不进</b> {@code Fight} 的 {@code fighterList} / {@code enemiesList}，所以：
 * 不会被选中、不会被攻击、不参与胜负判定、也不在 {@code getAllEntities()} 里跑 {@code updateSelf}。
 * <p>
 * <b>用法</b>（三行）：
 * <pre>{@code
 * CountdownEntity countdown = new CountdownEntity("无量忿怒", 70).setOnFire((fight, self) -> endZone());
 * countdown.schedule();          // 排进时间轴（当前时间 + 10000/速度）
 * countdown.cancel();            // 提前结束时摘掉（免死 / 驱散 / 战斗结束）
 * countdown.delayByPercent(0.15); // 延后 15%（E1 的"倒计时行动延后"）
 * }</pre>
 * <p>
 * <b>接进回合循环时必须满足的三件事</b>（{@code FightTurnPastListener} 里逐行对着的）：
 * <ol>
 *     <li><b>控制器不能是 {@code null}</b> —— 回合开始会遍历
 *     {@code getLivingThing().getController().getSkills()} 递减冷却，空指针会崩；
 *     这里用空的 {@link UniversalController}（技能表为空，永远不会自己行动）；</li>
 *     <li><b>{@code ActionSignal.SKIP_WITHOUT_NEW_TURN}</b> —— 不调 {@code act}、也不给自己排下一回合，
 *     只在 {@code lastExecuteList} 里执行回调；</li>
 *     <li><b>{@code setExtra(true)}</b> —— 让 {@link TurnManager#getNextTurnOf} 不把它当成
 *     "这个生物的下一次正常行动"（否则延后/冰冻那套会改到倒计时上）。</li>
 * </ol>
 * <p>
 * ⚠️ <b>两个已知的观感副作用</b>（都不影响正确性）：
 * 回合头会打印它的 HP 与五行能量（它没有法力表，全是 0）；
 * 它的回合里 {@code CommandManager.followActor} 会把 {@code @s} 指向它（本回合不接受任何输入，
 * 所以只有"正好在这回合敲命令"才看得出来）。
 *
 * @author AI（DeepSeek）生成
 */
public class CountdownEntity extends LivingThing {

    /**
     * 显示用的标签（例如"无量忿怒"），回合头与 {@code dump} 里看得见。
     */
    private final String label;

    /**
     * 到点时执行什么（允许为 {@code null}：只占一个回合，什么都不做）。
     */
    private ISpecialAction onFire;

    /**
     * 排进时间轴的那一条（{@code null} = 还没排 / 已经摘掉）。
     */
    private TurnEntry entry;

    /**
     * @param label 标签（会拼进名字与 id，例如 {@code "无量忿怒"} → {@code "countdown:无量忿怒"}）
     * @param speed 倒计时的固定速度（千冶·刃的结算是 70）
     */
    public CountdownEntity(String label, long speed) {
        super("倒计时：" + label, "countdown:" + label, 1L, ElementSort.FIRE);
        this.label = label;
        this.setSpeed(speed);
        // ⚠️ **必须有正 HP**：LivingThing#isAlive() 是按 hp <= 0 判死的（不是看 alive 标志），
        // 而 TurnManager#removeTheDeath() 每回合都会把"行动者已死"的条目摘掉 ——
        // HP 默认 0 的话，倒计时刚排上时间轴就被摘掉，结界永远不会到点（2026-10-24 实测踩到）。
        this.setHpMax(1L);
        this.setHp(1L);
        // 回合循环会遍历 controller.getSkills()，不能是 null
        this.setController(new UniversalController(new ArrayList<>(), this));
        this.setDescription("倒计时实体（不进战斗阵营，只占时间轴）");
    }

    /**
     * @param onFire 到点时要执行的动作
     * @return 自己（便于链式书写）
     */
    public CountdownEntity setOnFire(ISpecialAction onFire) {
        this.onFire = onFire;
        return this;
    }

    /**
     * 排进时间轴：时间 = 当前时间 + {@code 10000 / 速度}（与 {@link TurnManager#init} 同一套公式）。
     *
     * @return 排进去的那一条（已经排过就返回原来那条，不会重复排）
     */
    public TurnEntry schedule() {
        if (entry != null) {
            return entry;
        }
        BigDecimal needTime = BigDecimal.valueOf(10000)
                .divide(BigDecimal.valueOf(getSpeed()), 10, RoundingMode.HALF_UP);
        entry = new TurnEntry(this, needTime, TurnManager.getPresentTime())
                .setExtra(true)
                .setTag("倒计时")
                .setActionSignal(ActionSignal.SKIP_WITHOUT_NEW_TURN)
                .addLastSpecialAction((fight, self) -> fire());
        TurnManager.getTurns().add(entry);
        TurnManager.sort();
        return entry;
    }

    /**
     * 把倒计时从时间轴上摘掉（提前结束结界、目标死亡、战斗结束都该调它）。可重复调用。
     */
    public void cancel() {
        if (entry == null) {
            return;
        }
        TurnManager.getTurns().remove(entry);
        entry = null;
        this.setAlive(false);
    }

    /**
     * 延后倒计时（按比例放大剩余时间），对应"倒计时行动延后 15%"。
     *
     * @param percent 0.15 = 延后 15%
     */
    public void delayByPercent(double percent) {
        if (entry == null || percent == 0) {
            return;
        }
        TurnManager.delayByPercent(BigDecimal.valueOf(percent), entry);
        TurnManager.sort();
    }

    /**
     * 到点：先把自己从时间轴上摘掉（它只该响一次），再执行回调。
     * <p>
     * 正常路径上回合循环已经先 {@code remove} 掉这条了（它把条目从轴上取下来才执行），
     * 这里的 {@code remove} 是<b>防御性</b>的：自测直接调回调、或者别的代码路径触发时，
     * 不能让"已触发的倒计时"还留在轴上。
     */
    private void fire() {
        TurnEntry fired = this.entry;
        this.entry = null;
        if (fired != null) {
            TurnManager.getTurns().remove(fired);
        }
        this.setAlive(false);
        if (onFire != null) {
            onFire.execute(null, this);
        }
    }

    /**
     * @return 排进时间轴的那一条；{@code null} = 没排或已摘掉
     */
    public TurnEntry getEntry() {
        return entry;
    }

    /**
     * @return 是否还在时间轴上
     */
    public boolean isScheduled() {
        return entry != null && TurnManager.getTurns().contains(entry);
    }

    /**
     * @return 标签
     */
    public String getLabel() {
        return label;
    }

    /**
     * 倒计时不该被 {@code copy()} 出去参战 —— 真被复制了也只当一个新的空倒计时（没人会用它）。
     *
     * @return 一个新的同名倒计时
     */
    @Override
    public LivingThing copy() {
        return new CountdownEntity(label, getSpeed());
    }

    /**
     * 战斗结束时兜底摘掉：否则时间轴上会留一条指向"死掉"的条目的幽灵回合。
     */
    @Override
    public void whenFightEnds() {
        super.whenFightEnds();
        cancel();
    }
}
