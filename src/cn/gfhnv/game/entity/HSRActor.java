package cn.gfhnv.game.entity;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.effect.EnergyOverflowEffect;
import cn.gfhnv.game.system.ElementSort;

import java.util.ArrayList;

/**
 * <b>「星穹铁道式角色」基类</b>（2026-11-21 第 83 批，作者要求）：带<b>星魂</b>与<b>能量</b>两套通用设定。
 * <p>
 * 目前只有两名角色属于它：
 * <ul>
 *     <li><b>千冶·刃</b>：直接使用这里的能量（{@code energy / energyMax / energyOverflowMax}）与星魂；</li>
 *     <li><b>白厄</b>：也继承星魂，但<b>不使用</b>这里的能量 —— 他的"特殊能量"是【火种】
 *     （{@code coreflame}，见 {@code Phainon}）。</li>
 * </ul>
 * 能量语义：{@code energy} 只存<b>正常上限以内</b>的那部分（0…{@code energyMax}），
 * 超出上限的溢出点数由 {@link EnergyOverflowEffect} 记录（最多
 * {@code energyOverflowMax} 点）。{@link #getEnergy()} 返回"基础 + 溢出"的总量，
 * 所以外部读能量时不需要区分两部分。
 *
 * @author AI（DeepSeek）生成
 */
public class HSRActor extends Player {

    /**
     * 星魂上限（0 = 没点，6 = 满魂）。
     */
    public static final int EIDOLON_MAX = 6;
    /**
     * 普攻回能（2026-11-21 作者定：一次 +20）。
     */
    public static final long ENERGY_ON_COMMON_ATTACK = 20;
    /**
     * 战技回能（一次 +30）。
     */
    public static final long ENERGY_ON_SKILL = 30;
    /**
     * 受击回能（单体 +10）。
     */
    public static final long ENERGY_ON_DAMAGED = 10;
    /**
     * 受 AOE 回能（+5）。
     */
    public static final long ENERGY_ON_AOE_DAMAGED = 5;
    /**
     * 击杀回能（+10）。
     */
    public static final long ENERGY_ON_KILL = 10;
    /**
     * 星魂等级（0…6）。
     */
    protected int eidolon;
    /**
     * 当前能量<b>正常上限以内</b>的部分（0…{@link #energyMax}）。
     * 超出上限的溢出点数不在这里，见 {@link #energyOverflowEffect}。
     */
    protected long energy;
    /**
     * 能量正常上限。
     */
    protected long energyMax = 160;
    /**
     * 溢出上限：最多攒到 {@code energyMax + energyOverflowMax}。
     */
    protected long energyOverflowMax = 80;
    /**
     * 当前挂在身上的【能量溢出】效果；{@code null} = 没有溢出。
     * <p>
     * 它是溢出点数的<b>唯一真实来源</b>：{@link #getEnergy()} 每次都从效果上读，
     * 不在实体里再存一份，避免"效果显示 40、实体认为 35"这种两套账。
     */
    private EnergyOverflowEffect energyOverflowEffect;

    public HSRActor(String name, String id, double fireResistance, double waterResistance,
                    double metalResistance, double woodResistance, double dirtResistance,
                    long speed, long l, String type, double hp, double atk, double dfk, ElementSort yu) {
        super(name, id, fireResistance, waterResistance, metalResistance, woodResistance,
                dirtResistance, speed, l, type, hp, atk, dfk, yu);
    }

    public HSRActor(HSRActor other) {
        super(other);
        this.eidolon = other.eidolon;
        this.energy = other.energy;
        this.energyMax = other.energyMax;
        this.energyOverflowMax = other.energyOverflowMax;
        // super(other) 是浅拷贝效果表：先把和原体共用的【能量溢出】摘掉，
        // 再按原体的溢出点数挂一份自己的副本 —— 否则两只角色会互相改对方的溢出层数。
        for (Effect effect : new ArrayList<>(getEntityEffectList())) {
            if (effect instanceof EnergyOverflowEffect) {
                removeEffect(effect);
            }
        }
        this.energyOverflowEffect = null;
        syncOverflowEffect(other.getEnergyOverflow());
    }
    public HSRActor(String name, String id, long l, ElementSort u) {
        super(name, id, l, u);
    }

    /**
     * @return 星魂等级（0…6）
     */
    public int getEidolon() {
        return eidolon;
    }

    /**
     * 设置星魂等级（夹在 0…6）。子类可以覆盖 {@link #onEidolonChanged()} 做联动。
     *
     * @param eidolon 星魂等级
     */
    public void setEidolon(int eidolon) {
        int clamped = Math.max(0, Math.min(EIDOLON_MAX, eidolon));
        if (clamped == this.eidolon) {
            return;
        }
        this.eidolon = clamped;
        this.onEidolonChanged();
    }

    /**
     * 星魂变化后的钩子（星魂效果的挂/摘都写在这里）。
     */
    protected void onEidolonChanged() {
    }

    /**
     * 普攻回能。
     */
    public void gainEnergyOnCommonAttack() {
        addEnergy(ENERGY_ON_COMMON_ATTACK);
    }

    /**
     * 战技回能。
     */
    public void gainEnergyOnSkill() {
        addEnergy(ENERGY_ON_SKILL);
    }

    /**
     * 受击回能。
     *
     * @param aoe 是不是 AOE 伤害（是则只回 5）
     */
    public void gainEnergyOnDamaged(boolean aoe) {
        addEnergy(aoe ? ENERGY_ON_AOE_DAMAGED : ENERGY_ON_DAMAGED);
    }

    /**
     * 击杀回能。
     */
    public void gainEnergyOnKill() {
        addEnergy(ENERGY_ON_KILL);
    }

    /**
     * 这个角色是否使用本类的能量。
     * <p>
     * 默认 {@code true}；白厄覆盖成 {@code false} —— 他的"特殊能量"是【火种】，
     * 不应该因为挨打/击杀/放技能悄悄攒出普通能量（更不该挂上【能量溢出】效果）。
     *
     * @return 是否使用 HSRActor 的能量
     */
    protected boolean usesEnergy() {
        return true;
    }

    /**
     * @return 当前能量总量（正常上限以内的部分 + 溢出部分）
     */
    public long getEnergy() {
        if (!usesEnergy()) {
            return 0;
        }
        return energy + getEnergyOverflow();
    }

    /**
     * 设置当前能量总量（夹在 {@code 0 … energyMax + energyOverflowMax}）。
     * <p>
     * 超过 {@code energyMax} 的部分写进 {@link EnergyOverflowEffect}；
     * 降回上限以内时那个效果会被摘掉。不使用能量的角色（白厄）始终保持 0。
     *
     * @param energy 新的能量总量
     */
    public void setEnergy(long energy) {
        if (!usesEnergy()) {
            this.energy = 0;
            syncOverflowEffect(0);
            return;
        }
        long top = energyMax + energyOverflowMax;
        long clamped = Math.max(0, Math.min(top, energy));
        this.energy = Math.min(energyMax, clamped);
        syncOverflowEffect(clamped - this.energy);
    }

    /**
     * @return 当前溢出点数（0 = 没有溢出）
     */
    public long getEnergyOverflow() {
        if (!usesEnergy()) {
            return 0;
        }
        if (energyOverflowEffect == null || !getEntityEffectList().contains(energyOverflowEffect)) {
            return 0;
        }
        return energyOverflowEffect.getOverflow();
    }

    /**
     * 增加能量（可为负；结果按 {@link #setEnergy(long)} 夹取）。
     *
     * @param amount 增量
     */
    public void addEnergy(long amount) {
        setEnergy(getEnergy() + amount);
    }

    /**
     * 把溢出点数同步到效果上：0 → 摘掉效果；正数 → 挂/更新自己的效果。
     *
     * @param overflow 溢出点数（已夹在 0…{@code energyOverflowMax}）
     */
    private void syncOverflowEffect(long overflow) {
        if (overflow <= 0) {
            if (energyOverflowEffect != null && getEntityEffectList().contains(energyOverflowEffect)) {
                removeEffect(energyOverflowEffect);
            }
            energyOverflowEffect = null;
            return;
        }
        if (energyOverflowEffect == null || !getEntityEffectList().contains(energyOverflowEffect)) {
            energyOverflowEffect = new EnergyOverflowEffect((int) overflow);
            addEffectDirect(energyOverflowEffect);
        } else {
            energyOverflowEffect.setOverflow((int) overflow);
        }
    }

    /**
     * @return 能量正常上限
     */
    public long getEnergyMax() {
        return energyMax;
    }

    /**
     * @param energyMax 能量正常上限（负值按 0 算）；当前能量会被重新夹进新范围
     */
    public void setEnergyMax(long energyMax) {
        this.energyMax = Math.max(0, energyMax);
        setEnergy(getEnergy());
    }

    /**
     * @return 溢出上限
     */
    public long getEnergyOverflowMax() {
        return energyOverflowMax;
    }

    /**
     * @param energyOverflowMax 溢出上限（负值按 0 算）；当前能量会被重新夹进新范围
     */
    public void setEnergyOverflowMax(long energyOverflowMax) {
        this.energyOverflowMax = Math.max(0, energyOverflowMax);
        setEnergy(getEnergy());
    }
}
