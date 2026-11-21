package cn.gfhnv.game.officialStuff.customEntity.players;

import cn.gfhnv.game.data.NoConfig;
import cn.gfhnv.game.data.NoData;
import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entity.Player;
import cn.gfhnv.game.entity.PropertyEnhanceType;
import cn.gfhnv.game.entityController.PlayerController;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;

/**
 * 千冶·刃（Mortenax Blade）—— 火属性、生命上限型伤害、结界 + 无量忿怒。
 * <p>
 * <b>设计稿</b>：`TODO.md` 的 15~170 行（中英两版，**以中文数值为准**）。
 * <b>施工单与机制映射</b>：`project_analyses/current/MORTENAX-BLADE-2026-10.md`。
 * <p>
 * <b>本类现在只有"属性 + 资源"这一层</b>（P0 第 1 步）：速度 107、能量（160 上限 + 80 溢出）、
 * 充能（9 点，E2 降到 7）。技能 / 结界 / 无量忿怒还没写 —— 它们分别落在
 * `customSkill/mortenaxBlade/*` 与 `customEffect/mortenaxBladeEffects/*`。
 * <p>
 * <b>资源字段为什么放在实体上</b>：与白厄的 {@code coreflame} / {@code scourge} 同一个路子 ——
 * 它们是要被 {@code /data} 看见、被 {@code EntityData.json} 的 {@code classState} 写的
 * <b>出厂数值</b>，所以登记进 {@link cn.gfhnv.game.system.configLoadingSystem.DataKeys#CLASS_CONFIG}，
 * 由 {@code setXxx} 负责钳制（写超过上限会被夹回去，不是裸写字段）。
 *
 * @author AI（DeepSeek）生成；设计稿作者：仓库作者
 */
public class MortenaxBlade extends cn.gfhnv.game.entity.HSRActor implements cn.gfhnv.game.interfaces.IAdditionAttackBonus {

    /**
     * 【千锻魂】那笔「受到的治疗量 +50%」的来源键：开结界时挂、收结界时按它摘。
     */
    public static final Object HEAL_ENHANCE_SOURCE = new Object();

    /**
     * 【千锻魂】那笔「+20% 穿透」的来源键：开结界时挂、收结界时按它摘。
     */
    public static final Object PENETRATION_ENHANCE_SOURCE = new Object();

    /**
     * 【万淬心】给队友的增伤比例（设计：我方目标造成的伤害提高 50%）——
     * 具体数值在 {@code MortalBladeAllyEnhanceEffect#ENHANCE} 里，这里只是给外部常量转个手。
     */
    public static final double ALLY_ENHANCE =
            cn.gfhnv.game.officialStuff.customEffect.mortenaxBladeEffects.MortalBladeAllyEnhanceEffect.ENHANCE;
    /**
     * 【千锻魂】减伤的来源键（乘算表里按它加/减，见 {@code addDamageReduction}）。
     */
    private static final Object KENDUAN_REDUCTION_SOURCE = new Object();
    /**
     * 「因果尽偿」的充能监听器：<b>我方每次攻击敌方后 +1 充能</b>。
     * <p>
     * 每只千冶·刃各一个（带 owner），所以同场两只也不会互相串味，也不会重复加点。
     */
    private final cn.gfhnv.game.officialStuff.customEvent.mortenaxBladeEvents.MortalBladeChargeListener
            chargeListener = new cn.gfhnv.game.officialStuff.customEvent.mortenaxBladeEvents.MortalBladeChargeListener(this);
    /**
     * 「千锻魂」的受击监听器（只在结界期间注册）。
     */
    private final cn.gfhnv.game.officialStuff.customEvent.mortenaxBladeEvents.MortalBladeFuryHitListener
            furyHitListener = new cn.gfhnv.game.officialStuff.customEvent.mortenaxBladeEvents.MortalBladeFuryHitListener(this);
    /**
     * 结界期间挂给队友的增伤效果（「万淬心」），退出时按对象身份逐个摘掉。
     */
    private final java.util.Map<LivingThing, cn.gfhnv.game.effect.Effect> allyEnhanceEffects
            = new java.util.LinkedHashMap<>();

    /* ------------------------------------------------------------------
     * 能量
     * ------------------------------------------------------------------ */
    /**
     * <b>每次触发【因果尽偿】需要的充能点数</b>（设计 9；E2「心若死灰」降到 7）。
     * <p>
     * ⚠️ <b>它不是"上限"</b>（2026-10-24 用户澄清）：充能<b>没有上限</b> —— 攒到阈值就自动触发、
     * 触发时扣掉阈值那部分，剩下的继续攒。所以"81 点充能"会连续触发 9 次额外战技
     * （中途血量不够就停）。数据键名仍叫 {@code chargeMax}（历史原因），语义是"阈值"。
     */
    private int chargeMax = 9;
    /**
     * 当前充能点（「因果尽偿」攒满后消耗掉换一次额外战技）。
     */
    private int charge;
    /**
     * 是不是在【无量忿怒】里（结界还开着）。
     */
    private boolean infiniteFury;
    /**
     * 结界倒计时（{@code null} = 没开结界）。
     */
    private cn.gfhnv.game.entity.CountdownEntity furyCountdown;
    /**
     * 充能监听器是否已经注册（每实例一份，开战时注册、战斗结束注销）。
     */
    private boolean chargeListenerRegistered;
    /**
     * 「因果尽偿」追打进行中：追打造成的伤害会再次触发受击监听器，
     * 不挡一下就会自己触发自己（递归）。
     */
    private boolean payoffBusy;
    /**
     * 本次结界实际加到自己身上的【万淬心】增伤（退出时按这个数减回去；
     * 中途升/降星魂也不会把账算错）。
     */
    @NoData
    @NoConfig("运行期记账（本次结界给自己加了多少增伤），不是模板属性")
    private double appliedSelfEnhance;
    /**
     * 星魂 6「神若当殒」：本回合是否已经用过“受伤/扣血 +1 充能”。
     * <p>
     * 每到一个目标回合的回合头（{@code EffectUpdateEvent}）复位；
     * 同一回合内第一次受伤或扣血给 1 点，之后不再给。
     */
    @NoData
    @NoConfig("运行期状态（星魂 6 的每回合一次充能），不是模板属性")
    private boolean eidolonSixChargeUsed;
    /**
     * 免死收口（退出结界 / 两行播报 / 回血）已经排进当前回合、还没执行。
     * <p>
     * 第一段致死只锁 1 血、不退出结界；后续段在收口动作执行前仍会打到 0，所以这个标记
     * 让伤害修正器一直把致死伤害压回 1，避免多段攻击在回血前把他打死。
     */
    @NoData
    @NoConfig("运行期状态（免死收口动作是否已排队），不是模板属性")
    private boolean deathWardPending;

    /**
     * 构造一个出厂状态的千冶·刃。
     * <p>
     * <b>速度 107</b>（设计稿），等级由参数给；生命 / 攻击 / 防御按规则表 + 成长系数算。
     *
     * @param l 等级
     */
    public MortenaxBlade(long l) {
        // 名字里的「·」是 U+00B7（GBK 可编码）；别用 U+2022 的「•」，那个在 GBK 控制台会显示成 ?
        super("千冶\u00b7刃", "mortenax_blade", 0.7, 0, 0, 0, 0, 107, l, "player", 60, 12, 6, ElementSort.FIRE);
        // 玩家角色的基础暴击率 5% + 基础暴伤 50%（2026-11-15 第 71/73 批，用户定；
        // 只给默认用 PlayerController 的玩家实体加，怪物 / 召唤物没有暴击机制）
        this.setCriticalRate(0.05);
        this.setCriticalDamage(0.5);
        this.setDescription("这是千冶\u00b7刃");
        // ⚠️ 控制器不能省：回合循环与 whenFightEnds 都会遍历 getController().getSkills()，
        // 没有控制器直接 NPE。技能表按设计稿顺序：普攻 → 战技 →（终结技/强化技能在 P0 第 3~4 步加入）
        this.setController(new PlayerController(normalSkills(), this));
        // 免死：结界期间受到致命攻击不倒下（见 infiniteFuryDeathWard）
        this.setModifyDamage(infiniteFuryDeathWard());
        // 回合头下面那一行：能量 / 充能 / 结界状态（照白厄 setShowSpecialMes 的套路 ——
        // 回合循环在打完 HP 与五行能量之后调它，玩家才看得见这两个自定义资源）
        this.setShowSpecialMes(user -> {
            if (!(user instanceof MortenaxBlade blade)) {
                return;
            }
            StringBuilder line = new StringBuilder("能量 ")
                    .append(blade.getEnergy()).append('/').append(blade.getEnergyMax());
            if (blade.getEnergy() == blade.getEnergyMax()) {
                line.append("（满）");
            } else if (blade.getEnergy() > blade.getEnergyMax()) {
                line.append("（溢出 ").append(blade.getEnergy() - blade.getEnergyMax()).append('/')
                        .append(blade.getEnergyOverflowMax()).append('）');
            }
            line.append("  充能 ").append(blade.getCharge()).append("（每 ")
                    .append(blade.getChargeMax()).append(" 点触发一次，够 ")
                    .append(blade.getCharge() / blade.getChargeMax()).append(" 次）");
            if (blade.isInfiniteFury()) {
                line.append("  【无量忿怒】结界开启中");
            }
            System.out.println(line);
        });
    }

    /**
     * 复制构造器：资源上限跟着模板走（配置写在模板上，副本靠 {@code copy()} 白送）。
     *
     * @param mortenaxBlade 被复制的千冶·刃
     */
    public MortenaxBlade(MortenaxBlade mortenaxBlade) {
        super(mortenaxBlade);
        this.chargeMax = mortenaxBlade.chargeMax;
        this.charge = mortenaxBlade.charge;
        this.setModifyDamage(infiniteFuryDeathWard());
    }

    /* ------------------------------------------------------------------
     * 充能
     * ------------------------------------------------------------------ */

    @Override
    public LivingThing copy() {
        return new MortenaxBlade(this);
    }


    /**
     * 满能时清掉自身负面（旧 gainEnergy 的钩子；2026-11-21 第 84 批保留）。
     */
    @Override
    public void addEnergy(long amount) {
        long before = getEnergy();
        super.addEnergy(amount);
        if (before < getEnergyMax() && getEnergy() >= getEnergyMax()) {
            this.removeEffectsIf(effect -> {
                if (effect.isNegative()) {
                    effect.whenLastTimeEnd(this);
                    return true;
                }
                return false;
            });
        }
    }

    /* ------------------------------------------------------------------
     * 战斗生命周期
     * ------------------------------------------------------------------ */

    /**
     * 回能（会触发「能量到上限时解除自身所有负面效果」）。
     * <p>
     * 与 {@link #addEnergy(long)} 是同一套语义：跨过正常上限那一次顺手清负面；
     * 保留这个方法只是为了让技能侧的写法保持"回能"这个动词。
     *
     * @param amount 回复量（{@code <= 0} 不做事）
     */
    public void gainEnergy(long amount) {
        if (amount <= 0) {
            return;
        }
        addEnergy(amount);
    }

    /* ------------------------------------------------------------------
     * 结界 / 【无量忿怒】
     * ------------------------------------------------------------------ */

    /**
     * 付掉一次终结技的能量：清掉一个 {@link #energyMax}，<b>溢出部分留下</b>
     * （对应「施放终结技后清空并恢复相应的能量值」）。
     */
    public void spendUltimateEnergy() {
        setEnergy(getEnergy() - getEnergyMax());
    }

    /**
     * 「百炼骨」的战斗开始时那一半：能量不足 75% 就补到 75%。
     * <p>
     * 结界解除时也要调一次（那一半随结界一起做，见施工单 P1）。
     */
    public void ensureEnergyFloor() {
        long floor = (long) (getEnergyMax() * 0.75);
        if (getEnergy() < floor) {
            setEnergy(floor);
        }
    }

    /**
     * @return 当前充能点
     */
    public int getCharge() {
        return charge;
    }

    /**
     * 直接设置充能点（<b>夹在 {@code 0 … chargeMax}</b>）。
     *
     * @param charge 新充能点
     */
    public void setCharge(int charge) {
        // 只夹下界：充能没有上限（触发时扣掉阈值那部分，剩下的继续攒），见 chargeMax 的注释
        this.charge = Math.max(0, charge);
    }

    /**
     * @return <b>每次触发【因果尽偿】需要的充能点数</b>（设计 9；E2 之后是 7）—— 不是上限
     */
    public int getChargeMax() {
        return chargeMax;
    }

    /**
     * 设置充能上限；当前充能会被重新夹进新范围。
     *
     * @param chargeMax 新上限（{@code < 1} 按 1 算）
     */
    public void setChargeMax(int chargeMax) {
        this.chargeMax = Math.max(1, chargeMax);
    }

    /**
     * 加充能点（<b>没有上限</b>）。
     *
     * @param amount 加点数（{@code <= 0} 只回报"现在够不够触发"）
     * @return 加完之后够不够触发一次【因果尽偿】
     */
    public boolean gainCharge(int amount) {
        if (amount > 0) {
            setCharge(charge + amount);
        }
        return charge >= chargeMax;
    }

    /**
     * 星魂 6「神若当殒，自誓弑寿瘟」：结界期间<b>本回合第一次</b>受伤或消耗生命值时 +1 充能。
     * <p>
     * 与【千锻魂】的“每次受击 +1”是两条独立来源：这里额外 +1，且同一回合最多一次；
     * {@link MortalBladeFuryHitListener} 在每个目标回合的回合头调用
     * {@link #resetEidolonSixChargeForTurn()} 复位。
     */
    public void gainEidolonSixCharge() {
        if (getEidolon() < 6 || !infiniteFury || eidolonSixChargeUsed) {
            return;
        }
        eidolonSixChargeUsed = true;
        gainCharge(1);
        requestPayoff(null);
    }

    /**
     * 星魂 6：新回合开始，允许本次回合再次触发一次“受伤/扣血 +1 充能”。
     */
    public void resetEidolonSixChargeForTurn() {
        eidolonSixChargeUsed = false;
    }

    /**
     * 开战：能量按「百炼骨」保底到 75%（能量本身在 {@code whenFightEnds} 里清过零）。
     *
     * @param fight 当前战斗
     */
    @Override
    public void whenFightStart(Fight fight) {
        super.whenFightStart(fight);
        this.ensureEnergyFloor();
        this.eidolonSixChargeUsed = false;
        this.deathWardPending = false;
        if (!chargeListenerRegistered) {
            cn.gfhnv.game.event.EventBus.register(this.chargeListener);
            this.chargeListenerRegistered = true;
        }
    }

    /**
     * 战斗结束：资源清零（与白厄 {@code whenFightEnds} 里 {@code coreflame = 0} 同一个口径）。
     * <p>
     * 上限（{@code energyMax} / {@code chargeMax}）<b>不动</b> —— 那是配置出来的出厂值。
     */
    @Override
    public void whenFightEnds() {
        // ⚠️ 顺序要紧（2026-10-25）：**先**解结界，**再** super。
        // `endInfiniteFury` 会把自己加的那几条手动减回去（治疗 +50% / 增伤 +75% / E1 穿透 +20%）；
        // 而 `super.whenFightEnds()` 里 `resetTemporary()` 会把 13 个 *Enhance* **清零** ——
        // 先 super 再解结界的话，"减 0.5"落在零点上 → 战后 `healEnhancePercent = -0.5`（治疗变减半）。
        // 结界（倒计时条目）也必须一起收：否则时间轴上会留一条指向"死掉"的条目。
        this.endInfiniteFury();
        super.whenFightEnds();
        // 充能监听器也要摘：不摘的话下一场它还挂在总线上（泄漏 + 给上一场那个对象加充能）
        cn.gfhnv.game.event.EventBus.unregister(this.chargeListener);
        this.chargeListenerRegistered = false;
        this.energy = 0;
        this.charge = 0;
        this.eidolonSixChargeUsed = false;
        this.deathWardPending = false;
        this.appliedSelfEnhance = 0;
    }

    /**
     * 解除自身所有负面效果（「能量到上限」时触发）。
     * <p>
     * 判据是 {@link Effect#isNegative()}；用 {@code removeIf} 而不是增强 for 里删 ——
     * {@code getEntityEffectList()} 返回的是活列表。
     */
    private void cleanseNegativeEffects() {
        this.removeEffectsIf(Effect::isNegative);
        System.out.println(this.getName() + "能量已满，解除了自身所有负面效果");
    }

    /**
     * <b>请求兑现「因果尽偿」</b>：够阈值、结界里、血 &gt; 1 时排进<b>当前回合的收尾队列</b>。
     * <p>
     * 所有充能来源都走这一个入口（我方攻击的监听器、受击监听器、消耗生命值），
     * 这样"什么时候追打、怎么排队"只有一份实现。
     *
     * @param fight 当前战斗（有当前回合时可以给 {@code null}；没有回合时才用得上）
     */
    public void requestPayoff(Fight fight) {
        if (!infiniteFury || getHp() <= 1 || charge < chargeMax) {
            return;
        }
        cn.gfhnv.game.system.fight.TurnEntry presentTurn =
                cn.gfhnv.game.eventListener.FightTurnPastListener.getPresentTurn();
        if (presentTurn != null) {
            presentTurn.addAct(this, (currentFight, user) -> {
                payoffNow(currentFight);
                this.gainEnergyOnSkill();
            },
                cn.gfhnv.game.system.fight.ActEntry.PRIORITY_LAST);
            return;
        }
        if (fight != null) {
            payoffNow(fight);
        }
    }

    /**
     * <b>当场兑现</b>：每够一个阈值就扣一次、回 27 能量、额外施放一次【刃下，归葬】，
     * 直到充能不够或血量掉到 1（充能没有上限，81 点能连打 9 次）。
     *
     * @param fight 当前战斗
     */
    public void payoffNow(Fight fight) {
        if (fight == null || payoffBusy) {
            return;
        }
        payoffBusy = true;
        try {
            int castTimes = 0;
            while (charge >= chargeMax && getHp() > 1) {
                cn.gfhnv.game.skill.Skill extra = null;
                for (cn.gfhnv.game.skill.Skill skill : getController().getSkills()) {
                    if (skill instanceof cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.BladeUnderBurial) {
                        extra = skill.copy();
                        break;
                    }
                }
                if (extra == null) {
                    // 技能表里没有这一招（例如刚退出结界）—— 什么都不做，别抛
                    return;
                }
                setCharge(charge - chargeMax);
                gainEnergy(cn.gfhnv.game.officialStuff.customEvent.mortenaxBladeEvents
                        .MortalBladeChargeListener.ENERGY_PER_PAYOFF);
                castTimes++;
                System.out.println(getName() + "【因果尽偿】第 " + castTimes + " 次：消耗 " + chargeMax
                        + " 点充能，回复 " + cn.gfhnv.game.officialStuff.customEvent.mortenaxBladeEvents
                        .MortalBladeChargeListener.ENERGY_PER_PAYOFF + " 点能量并额外施放一次【刃下，归葬】");
                extra.comeToEffect(fight, this, new java.util.ArrayList<>(fight.getOpponentList(this)));
                // E1「我殁之前，我仍未成形」：**每一次**额外战技都让倒计时延后 15%
                // （用户 2026-10-24 明确：一回合里连打 5 次就延后 5 次，不是"这批只延一次"）
                if (eidolon >= 1 && furyCountdown != null) {
                    furyCountdown.delayByPercent(0.15d);
                    System.out.println(getName() + "【我殁之前，我仍未成形】：倒计时延后 15%（第 "
                            + castTimes + " 次额外战技）");
                }
            }
        } finally {
            payoffBusy = false;
        }
    }

    /**
     * 消耗生命值（技能里的"扣血"都走这里）：<b>生命不足时降到 1</b>，不会把自己扣死。
     * <p>
     * 星魂 6「神若当殒」：结界期间本回合第一次消耗生命值 → 额外 +1 充能
     * （与【千锻魂】的受击 +1 是两条独立来源）。
     *
     * @param amount 想扣多少
     */
    public void consumeHp(long amount) {
        long cost = Math.min(Math.max(0L, amount), Math.max(0L, getHp() - 1));
        if (cost > 0) {
            setHp(getHp() - cost);
            gainEidolonSixCharge();
        }
    }

    /**
     * @return 命座等级（0 = 无；1 = E1；2 = E2）
     */

    /**
     * 二魂「心若死灰，而炉火未熄」：我方目标造成的追加攻击伤害 +75%。
     *
     * @return 0.75（二魂及以上）或 0
     */
    @Override
    public double getAdditionAttackDamageBonus() {
        return eidolon >= 2 ? 0.75 : 0;
    }

    /**
     * 设置命座等级并立刻应用 E2（充能上限 9 → 7）。E1 的效果在结界期间生效，不用在这里应用。
     *
     * @param eidolon 命座等级（夹在 0…6）
     */
    /**
     * 星魂变化钩子（HSRActor 的唯一入口，不再自己维护一份 eidolon 字段）。
     * <p>
     * E2「心若死灰，而炉火未熄」：充能上限 9 → 7。
     */
    @Override
    protected void onEidolonChanged() {
        this.setChargeMax(getEidolon() >= 2 ? 7 : 9);
    }

    /**
     * @return 「因果尽偿」追打是不是正在进行（受击监听器靠它防递归）
     */
    public boolean isPayoffBusy() {
        return payoffBusy;
    }

    /**
     * @param payoffBusy 追打是否进行中
     */
    public void setPayoffBusy(boolean payoffBusy) {
        this.payoffBusy = payoffBusy;
    }

    /**
     * @return 是否在【无量忿怒】里
     */
    public boolean isInfiniteFury() {
        return infiniteFury;
    }

    /**
     * 直接改状态位 —— <b>只给自测与状态机内部用</b>，正常流程走
     * {@link #beginInfiniteFury(Fight)} / {@link #endInfiniteFury()}。
     *
     * @param infiniteFury 是否在【无量忿怒】里
     */
    public void setInfiniteFury(boolean infiniteFury) {
        this.infiniteFury = infiniteFury;
    }

    /**
     * 开结界 + 进【无量忿怒】：状态位、暴击加成、换技能表、行动序列上的倒计时。
     * <p>
     * <b>倒计时是独立实体</b>（{@code CountdownEntity}）：结界到点时它调用 {@link #endInfiniteFury()}，
     * 而不是给千冶·刃自己插一个额外回合（那会和本体的回合混在一条轴上）。
     *
     * @param fight 当前战斗
     */
    public void beginInfiniteFury(Fight fight) {
        if (infiniteFury) {
            return;
        }
        this.infiniteFury = true;
        // 暴击率 +20% / 暴击伤害 +60%（不靠回合数结束：结界的结束由倒计时与免死负责）
        this.addEffect(new cn.gfhnv.game.officialStuff.customEffect.mortenaxBladeEffects.InfiniteFuryEffect(
                Integer.MAX_VALUE).setOrigin(this.getUUID()));
        // 换技能表：强化普攻 / 解放战技 / 全新终结技
        this.getController().setSkills(this.infiniteFurySkills());
        // 【千锻魂】：减伤 50% + 受到的治疗量 +50%（走现成的两条链）
        this.addDamageReduction(KENDUAN_REDUCTION_SOURCE, 0.5);
        this.addPropertyEnhance(PropertyEnhanceType.HEAL, 0.5, 0, HEAL_ENHANCE_SOURCE);
        // 【万淬心】：自己额外增伤 +75%（E4 再 +50%），队友 +50%（E4 再 +50%，队友靠专用效果）
        this.appliedSelfEnhance = 0.75 + (getEidolon() >= 4 ? 0.5 : 0);
        this.setEnhance(this.getEnhance() + this.appliedSelfEnhance);
        applyAllyEnhance(fight);
        this.eidolonSixChargeUsed = false;
        this.deathWardPending = false;
        // E1「我殁之前，我仍未成形」：结界期间 +20% 穿透（设计原文是敌方全抗 -20%）
        if (eidolon >= 1) {
            this.addPenetration(null, 0.2, PENETRATION_ENHANCE_SOURCE);
        }
        // 「千锻魂」的受击那一半：只在结界期间注册
        cn.gfhnv.game.event.EventBus.register(this.furyHitListener);
        // 倒计时（固定 70 速度）
        this.furyCountdown = new cn.gfhnv.game.entity.CountdownEntity("无量忿怒",
                cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.FornaxExCorpore.COUNTDOWN_SPEED)
                .setOnFire((f, self) -> endInfiniteFury());
        this.furyCountdown.schedule();
        System.out.println(this.getName() + "展开结界，进入【无量忿怒】");
    }

    /**
     * 结束结界、退出【无量忿怒】：撤回暴击加成、取消倒计时、把技能表换回普通形态。
     * <p>
     * <b>会幂等</b>（重复调用无副作用）—— 免死、倒计时到点、战斗结束三条路都会调它。
     */
    public void endInfiniteFury() {
        if (!infiniteFury) {
            return;
        }
        this.infiniteFury = false;
        if (this.furyCountdown != null) {
            this.furyCountdown.cancel();
            this.furyCountdown = null;
        }
        // 手动移除【无量忿怒】效果时必须自己调一次 whenLastTimeEnd：那是"把加上的数值减回去"的唯一出口，
        // 直接从列表里删会让暴击率/暴击伤害永远停在加成后的值。
        for (cn.gfhnv.game.effect.Effect effect : new java.util.ArrayList<>(this.getEntityEffectList())) {
            if (effect instanceof cn.gfhnv.game.officialStuff.customEffect.mortenaxBladeEffects.InfiniteFuryEffect) {
                effect.whenLastTimeEnd(this);
                this.removeEffect(effect);
            }
        }
        // 撤回【千锻魂】/【万淬心】/E1 那几条（加了什么就在这儿减什么，两边必须成对）
        this.removeDamageReduction(KENDUAN_REDUCTION_SOURCE);
        this.removePropertyEnhance(HEAL_ENHANCE_SOURCE);
        this.setEnhance(this.getEnhance() - this.appliedSelfEnhance);
        this.appliedSelfEnhance = 0;
        clearAllyEnhance();
        this.eidolonSixChargeUsed = false;
        // 2026-11-15 第 78 批（分析 P1-7）：javadoc 一直写着"结界解除时也要调一次"，
        // 但这里之前没调 —— 结界结束时能量停在结算值（可能 0），下一个终结技直接放不出来。
        this.ensureEnergyFloor();
        if (eidolon >= 1) {
            this.removePenetration(PENETRATION_ENHANCE_SOURCE);
        }
        cn.gfhnv.game.event.EventBus.unregister(this.furyHitListener);
        this.getController().setSkills(this.normalSkills());
        System.out.println(this.getName() + "结界解除，退出【无量忿怒】");
    }

    /**
     * @return 结界倒计时（{@code null} = 没开结界）
     */
    public cn.gfhnv.game.entity.CountdownEntity getFuryCountdown() {
        return furyCountdown;
    }

    /**
     * 给场上队友挂【万淬心】的增伤（自己那份走 {@code setEnhance}，因为"额外 +75%"是自己的）。
     *
     * @param fight 当前战斗
     */
    private void applyAllyEnhance(Fight fight) {
        clearAllyEnhance();
        if (fight == null) {
            return;
        }
        for (LivingThing ally : new java.util.ArrayList<>(fight.getOwnList(this))) {
            if (ally == this) {
                continue;
            }
            // 专用效果：只加 enhance、不播报回合数（用 DamageEnhanceEffect 会打印"2147483647 回合"，很难看）
            double enhance = ALLY_ENHANCE + (getEidolon() >= 4 ? 0.5 : 0);
            cn.gfhnv.game.effect.Effect effect =
                    new cn.gfhnv.game.officialStuff.customEffect.mortenaxBladeEffects.MortalBladeAllyEnhanceEffect(
                            enhance)
                            .setOrigin(this.getUUID());
            ally.addEffect(effect);
            allyEnhanceEffects.put(ally, effect);
        }
    }

    /**
     * 摘掉给队友挂的【万淬心】增伤（要<b>先</b>调 {@code whenLastTimeEnd} 把数值减回去再删，
     * 直接从列表里删会让"增伤"永久留在队友身上）。
     */
    private void clearAllyEnhance() {
        for (java.util.Map.Entry<LivingThing, cn.gfhnv.game.effect.Effect> entry : allyEnhanceEffects.entrySet()) {
            LivingThing ally = entry.getKey();
            cn.gfhnv.game.effect.Effect effect = entry.getValue();
            effect.whenLastTimeEnd(ally);
            ally.removeEffect(effect);
        }
        allyEnhanceEffects.clear();
    }

    /**
     * @return 普通形态的技能表：<b>残锋，掠尽</b>（普攻）+ <b>骸骨当炉，血肉即薪</b>（终结技）
     * <p>
     * ⚠️ <b>淬锋，断魄 不在这里</b> —— 它是【无量忿怒】期间的<b>强化普攻</b>（替换残锋），
     * 结界解除后跟着换回去。同理「刃下，归葬」是结界期间才解放的战技。
     */
    private java.util.List<Skill> normalSkills() {
        java.util.List<Skill> skills = new java.util.ArrayList<>();
        skills.add(new cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.MortalBladeCommonAttack());
        skills.add(new cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.FornaxExCorpore());
        return skills;
    }

    /**
     * @return 【无量忿怒】期间的技能表：<b>淬锋，断魄</b>（强化普攻）+ <b>刃下，归葬</b>（解放战技）
     * + <b>千冶铸一，万劫烬灭</b>（全新终结技）
     * <p>
     * 与普通形态的差别：残锋 → 淬锋（普攻强化）、多出刃下归葬（战技解放）、骸骨当炉 → 千冶铸一。
     * <p>
     * ⚠️ 这三招取自 {@code World#prototypeCopyOf}（登记在千冶·刃名下的原型），
     * <b>不是 {@code new}</b> —— 那样 {@code SkillData.json} 里对它们的改动一点都不会生效。
     */
    private java.util.List<Skill> infiniteFurySkills() {
        java.util.List<Skill> skills = new java.util.ArrayList<>();
        // ⚠️ 必须从**原型副本**造，不能 `new`：这三招平时不在控制器里，
        // 它们的配置值只存在于「登记在千冶·刃名下的原型」上（见 OfficialGameContent）。
        // 直接 `new` 的话，SkillData.json 里改的倍率对这一份**完全不生效**。
        skills.add(cn.gfhnv.game.world.World.prototypeCopyOf(
                cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.MortalBladeSkill.class));
        skills.add(cn.gfhnv.game.world.World.prototypeCopyOf(
                cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.BladeUnderBurial.class));
        Skill tenax = cn.gfhnv.game.world.World.prototypeCopyOf(
                cn.gfhnv.game.officialStuff.customSkill.mortenaxBlade.TenaxPerIgnem.class);
        // 星魂 6「神若当殒」：千冶铸一的伤害倍率提高到原倍率的 150%（配置改过倍率也照样乘 1.5）
        if (getEidolon() >= 6) {
            tenax.setHpMagnification(tenax.getHpMagnification() * 1.5);
            tenax.setDescription("对敌方全体造成等同于千冶·刃"
                    + Math.round(tenax.getHpMagnification() * 100) + "%生命上限的火属性伤害。");
        }
        skills.add(tenax);
        return skills;
    }

    /**
     * <b>免死</b>：结界期间受到致命攻击时不会倒下。
     * <p>
     * 形状与白厄的 {@code soulscorchDeathWard} 一致：挂一个 {@link cn.gfhnv.game.interfaces.IModifyDamage}，
     * 在伤害结算的最后一步改"这一下之后还剩多少血"。
     * <p>
     * 第一段致死只把血量钳到 1、置 {@link #deathWardPending}，<b>不在这里退出结界</b>：
     * 退出、播报和回血全部排进当前回合的 {@code PRIORITY_LAST}。这样多段攻击的后续段
     * 仍会被这个修正器钳回 1，而"结界解除 / 不会倒下 / 回复至"会出现在攻击行之后，
     * 不会抢在命中日志和队友获得火种之前。
     *
     * @return 免死处理器
     */
    private cn.gfhnv.game.interfaces.IModifyDamage infiniteFuryDeathWard() {
        return new cn.gfhnv.game.interfaces.IModifyDamage() {
            @Override
            public long damageModify(long newHp, cn.gfhnv.game.event.DamageEvent damageEvent) {
                if (damageEvent.getAttackedEntity() == MortenaxBlade.this && newHp <= 0) {
                    // 收口动作已经排队：多段攻击的后续段只锁 1 血，等回合末再退出/回血。
                    if (deathWardPending) {
                        return 1;
                    }
                    if (infiniteFury) {
                        // 有当前回合：退出 + 播报 + 回血都排进 LAST，本段与后续段都停在 1；
                        // 没有当前回合（自测 / 战斗已结束）：当场收口并返回回血后的血量。
                        boolean immediate = scheduleDeathWardResolution();
                        return immediate ? getHp() : 1;
                    }
                }
                return newHp;
            }
        };
    }

    /**
     * 把免死的「退出结界 + 不会倒下播报 + 回复 50% 生命上限」<b>排进当前回合的收尾队列</b>。
     * <p>
     * <b>为什么不能在这里直接 {@code heal(...)}</b>（2026-10-24 用户实测踩到）：
     * 这个方法是伤害结算**中间**的一步，它返回的 {@code newHp} 之后会被
     * {@code setHp(newHp)} 落账 —— 当场回的血会被那一句<b>覆盖掉</b>，
     * 表现是"免死触发了、结界也解除了，但血量还停在 1"，下一次挨打就死。
     * 白厄的 {@code soulscorchDeathWard} 也是同一个道理：它把后续动作排进
     * {@code getPresentTurn().getLastExecuteList()}，等这一击彻底结算完再执行。
     * <p>
     * 退出结界也一并延后：回合末之前 {@code infiniteFury} 保持为 {@code true}，
     * 后续段仍吃【无量忿怒】的减伤，也仍会触发【千锻魂】/E6 充能；`deathWardPending`
     * 负责阻止后续致死段把血打到 0。
     * <p>
     * 自测里没有"当前回合"（直接调 {@code modifyIncomingDamage}），那种场合退化成当场收口 ——
     * 反正没有 setHp 来覆盖它。
     */
    private boolean scheduleDeathWardResolution() {
        cn.gfhnv.game.system.fight.TurnEntry presentTurn =
                cn.gfhnv.game.eventListener.FightTurnPastListener.getPresentTurn();
        if (presentTurn == null) {
            this.deathWardPending = false;
            endInfiniteFury();
            System.out.println(getName() + "「我殁之前，我仍未成形」—— 不会倒下");
            heal((long) (getHpMax() * 0.5));
            return true;
        }
        // 这条动作的执行者是**千冶·刃本人**，不是当前回合的行动者 ——
        // 他完全可能在别人的回合里被打死（例如完整容器的共祭攻击）。
        this.deathWardPending = true;
        presentTurn.addAct(this, (fight, user) -> {
            this.deathWardPending = false;
            // 正常路径到这里仍在结界里；若已被战斗结束/倒计时提前收口，就不要重复播报。
            if (this.infiniteFury) {
                this.endInfiniteFury();
                System.out.println(this.getName() + "「我殁之前，我仍未成形」—— 不会倒下");
            }
            long before = this.getHp();
            this.heal((long) (this.getHpMax() * 0.5));
            System.out.println(this.getName() + "「我殁之前，我仍未成形」—— 回复至 "
                    + before + " → " + this.getHp());
        }, cn.gfhnv.game.system.fight.ActEntry.PRIORITY_LAST);
        return false;
    }
}
