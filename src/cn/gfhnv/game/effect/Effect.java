package cn.gfhnv.game.effect;

import cn.gfhnv.game.entity.LivingThing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 效果（Buff/Debuff）基类。表示可以附加到 {@link LivingThing} 上、在战斗回合中持续生效的状态效果。
 * <p>
 * 核心属性：
 * <ul>
 *     <li><b>id</b>：效果唯一标识，用于 {@link #equals(Object)} 判定同一类效果；</li>
 *     <li><b>level</b>：效果等级，叠加时等级更高者覆盖、否则延长持续时间；</li>
 *     <li><b>lastTime</b>：持续回合数；</li>
 *     <li><b>origin</b>：效果来源（施放者/技能等）；</li>
 *     <li><b>effectTagsList</b>：效果标签（负面、无限持续等）。</li>
 * </ul>
 * <p>
 * 子类必须重写 {@link #copy()} 返回正确的副本，并根据需要重写 {@link #comeIntoEffect(LivingThing)}
 * （获得效果/每回合更新时执行）与 {@link #whenLastTimeEnd(LivingThing)}（效果结束时执行）。
 *
 * @author gfhnv
 */
public class Effect {
    private String id;
    /**
     * 效果的<b>显示名</b>（例如「破容器之赏」）。{@code null} = 没单独起名。
     * <p>
     * 它和 {@link #id} 是<b>两件事</b>：{@code id} 是效果的<b>身份</b> ——
     * {@link #equals(Object)} 用它判等、{@code /effect} 用它寻址、{@code /data} 里也是这个键；
     * {@code name} 纯粹是<b>给人看的</b>。
     * <p>
     * 为什么需要它：同一个效果类可以有多个"名字" —— 比如同一个增伤效果，由不同来源给的时候
     * 希望日志里显示「破容器之赏」而不是干巴巴的 {@code damageEnhanceEffect}。
     * 以前要靠"给每个用途单独写一个效果类"才能做到（{@code ContainerReward} 就是这么来的，
     * 其实它和 {@code DamageEnhanceEffect} 走的是同一条 {@code setEnhance} 路）。
     * <p>
     * 它会出现在 {@code /data} 里（读得到、写不了 —— {@code effects} 整个列表是只读的）。
     */
    private String name;
    private int level = 1;//效果等级
    private int lastTime;//turns
    private String origin = "null";
    private List<EffectTags> effectTagsList = new ArrayList<>();

    /**
     * 构造一个效果。
     *
     * @param id       效果唯一标识
     * @param level    效果等级
     * @param lastTime 持续回合数
     */
    public Effect(String id, int level, int lastTime) {
        this.id = id;
        this.level = level;
        this.lastTime = lastTime;
    }

    /**
     * 复制构造器。仅复制 id、level、lastTime 三个基础字段。
     *
     * @param effect 被复制的效果
     */
    public Effect(Effect effect) {
        this.id = effect.id;
        this.name = effect.name;
        this.level = effect.level;
        this.lastTime = effect.lastTime;
    }

    /**
     * 构造一个仅指定 id 的效果（等级默认 1，持续回合数为 0）。
     *
     * @param id 效果唯一标识
     */
    public Effect(String id) {
        this.id = id;
    }

    /**
     * @return 效果的标签列表（负面、无限持续等）
     */
    public List<EffectTags> getEffectTagsList() {
        return effectTagsList;
    }

    /**
     * 设置效果的标签列表。
     *
     * @param effectTagsList 效果标签列表
     */
    public void setEffectTagsList(List<EffectTags> effectTagsList) {
        this.effectTagsList = effectTagsList;
    }

    /**
     * @return 该效果是否为负面效果（Debuff）
     */
    public boolean isNegative() {
        return effectTagsList.contains(EffectTags.NEGATIVE);
    }

    /**
     * 设置该效果是否为负面效果（Debuff）。
     *
     * @param negative {@code true} 标记为负面效果，{@code false} 移除负面标记
     */
    public void setNegative(boolean negative) {
        if (negative && !effectTagsList.contains(EffectTags.NEGATIVE)) effectTagsList.add(EffectTags.NEGATIVE);
        if (!negative && effectTagsList.contains(EffectTags.NEGATIVE)) effectTagsList.remove(EffectTags.NEGATIVE);
    }

    /**
     * 效果获得时执行。默认调用 {@link #comeIntoEffect(LivingThing)}；如果子类重写此方法，
     * 需要注意避免效果生效次数异常（可能比预期多触发一次）。
     *
     * @param livingThing 获得效果的实体
     */
    public void initialEffect(LivingThing livingThing) {
        this.comeIntoEffect(livingThing);
    }

    /**
     * 复制效果。子类<b>必须</b>重写此方法返回正确的副本。
     *
     * @return 效果的深拷贝实例
     * @throws UnsupportedOperationException 基类默认不支持直接复制
     */
    public Effect copy() {
        throw new UnsupportedOperationException("子类必须重写此方法,当前类 :" + this.getClass().getName());
    }


    @Override
    public String toString() {
        return "Effect{" +
                "id='" + id + '\'' +
                ", name='" + getName() + '\'' +
                ", effectTagsList=" + effectTagsList +
                ", origin='" + origin + '\'' +
                '}';
    }

    /**
     * @return 效果唯一标识
     */
    public String getId() {
        return id;
    }

    /**
     * 设置效果唯一标识。
     *
     * @param id 效果唯一标识
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return 效果的<b>显示名</b>；没单独起名时回退到 {@link #getId()}
     * <p>
     * 回退是为了「界面上永远有东西可显示、而且 {@code /data} 里一定看得到 name」——
     * 没起名时它等于 id，起名后就是你给的那个名字。
     */
    public String getName() {
        return name == null ? id : name;
    }

    /**
     * 给效果起一个<b>显示名</b>（链式调用，与 {@link #setOrigin(String)} 同形）。
     * <p>
     * 传 {@code null} = 恢复成"回退到 id"。
     *
     * @param name 显示名
     * @return 当前效果实例
     */
    public Effect setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * @return 这个效果<b>有没有单独起过名字</b>（{@link #setName(String)} 传过非 {@code null}）
     * <p>
     * 为什么不能拿 {@link #getName()} 判断：它没起名时会回退到 id，永远不为 {@code null}。
     * 有些效果想"**起了名才播报**"（例如【破容器之赏】），就靠这个区分。
     */
    public boolean hasCustomName() {
        return name != null;
    }

    /**
     * 效果持续时间结束时执行（例如移除效果附加的属性值）。
     * <p>
     * 子类按需重写。
     *
     * @param livingThing 效果结束的实体
     */
    public void whenLastTimeEnd(LivingThing livingThing) {
    }

    public Effect facSetId(String id) {
        this.setId(id);
        return this;
    }

    public Effect facSetLevel(int level) {
        this.setLevel(level);
        return this;
    }

    public Effect facSetLastTime(int lastTime) {
        this.setLastTime(lastTime);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Effect effect = (Effect) o;
        return isInfinity() == effect.isInfinity() && Objects.equals(getId(), effect.getId()) && Objects.equals(getOrigin(), effect.getOrigin());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), isInfinity(), getOrigin());
    }

    /**
     * @return 效果唯一标识（与 {@link #getId()} 相同）
     */
    public String getID() {
        return id;
    }

    /**
     * @return 效果等级
     */
    public int getLevel() {
        return level;
    }

    /**
     * 设置效果等级。
     *
     * @param level 效果等级
     */
    public void setLevel(int level) {
        this.level = level;
    }

    /**
     * @return 效果剩余/总持续回合数
     */
    public int getLastTime() {
        return lastTime;
    }

    /**
     * 设置效果持续回合数。
     *
     * @param lastTime 持续回合数
     */
    public void setLastTime(int lastTime) {
        this.lastTime = lastTime;
    }

    /**
     * 效果生效逻辑。回合更新时此方法会被调用，子类应重写此方法实现具体效果内容。
     *
     * @param thing 携带该效果的实体
     */
    public void comeIntoEffect(LivingThing thing) {
        System.out.println("这里写效果具体内容........请重写这个方法.回合更新时此方法会被调用");
    }

    /**
     * @return 该效果是否无限持续（不因回合数结束）
     */
    public boolean isInfinity() {
        return effectTagsList.contains(EffectTags.INFINITE);
    }

    /**
     * 该效果是否为「通用效果」——即任意生物都能获得的、与角色无关的效果。
     *
     * @return 是否为可被命令施加的通用效果
     */
    public boolean isUniversal() {
        return effectTagsList.contains(EffectTags.UNIVERSAL);
    }

    /**
     * @return 效果来源（施放者/技能等）
     */
    public String getOrigin() {
        return origin;
    }

    /**
     * 设置效果来源（链式调用）。
     *
     * @param origin 效果来源
     * @return 当前效果实例
     */
    public Effect setOrigin(String origin) {
        this.origin = origin;
        return this;
    }
}
