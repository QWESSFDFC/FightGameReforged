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
