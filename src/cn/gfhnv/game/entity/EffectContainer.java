package cn.gfhnv.game.entity;

import cn.gfhnv.game.effect.Effect;

import java.util.*;
import java.util.function.Predicate;

/**
 * 生物身上的<b>效果列表</b>（2026-11-14 第 57 批从 {@link LivingThing} 里拆出来的组件）。
 * <p>
 * <b>为什么要有它</b>：效果列表以前是 {@code LivingThing} 上一个裸 {@code ArrayList}，
 * 于是"从列表里直接删一个效果"这种写法散落在角色、技能、命令、自测里（审计数出 <b>13 处结构写入</b>）。
 * 那样删掉的效果<b>不会走 {@code whenLastTimeEnd}</b> —— 效果加数值的那一半就永远留在身上。
 * 现在列表只读，结构性修改只能走下面四个 API：
 * <ul>
 *     <li>{@link #addEffect(Effect)}：追加（不过滤、不合并，语义 = 原来的 {@code list.add}）；</li>
 *     <li>{@link #removeEffect(Effect)}：按引用删；</li>
 *     <li>{@link #removeEffectsIf(Predicate)}：批量删（净化类效果用；删之前自己调
 *     {@code whenLastTimeEnd}）；</li>
 *     <li>{@link #clearEffects()}：整表清空（{@code whenFightEnds} / 调试命令用）。</li>
 * </ul>
 * <b>它仍然是 {@code List}</b>：对外（{@code getEntityEffectList()} 与 {@code /data} 的 {@code effects} 键）
 * 行为一个字都没变，只是所有 mutator 会抛 {@link UnsupportedOperationException}。
 *
 * @author AI（DeepSeek）生成；设计稿作者：仓库作者
 */
public final class EffectContainer extends AbstractList<Effect> {

    /**
     * 真正的存储。
     */
    private final List<Effect> effects = new ArrayList<>();

    @Override
    public Effect get(int index) {
        return effects.get(index);
    }

    @Override
    public int size() {
        return effects.size();
    }

    @Override
    public Effect set(int index, Effect element) {
        throw new UnsupportedOperationException("效果列表只读：请用 setEffect(index, effect)");
    }

    @Override
    public boolean add(Effect effect) {
        throw new UnsupportedOperationException("效果列表只读：请用 LivingThing#addEffect/addEffectDirect");
    }

    @Override
    public void add(int index, Effect element) {
        throw new UnsupportedOperationException("效果列表只读：请用 addEffect(...)");
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException("效果列表只读：请用 removeEffect(effect)");
    }

    @Override
    public Effect remove(int index) {
        throw new UnsupportedOperationException("效果列表只读：请用 removeEffect(effect)");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("效果列表只读：请用 clearEffects()");
    }

    @Override
    public boolean addAll(java.util.Collection<? extends Effect> collection) {
        throw new UnsupportedOperationException("效果列表只读：请用 replaceAll(list)");
    }

    @Override
    public boolean removeAll(java.util.Collection<?> collection) {
        throw new UnsupportedOperationException("效果列表只读：请用 removeEffectsIf(...) / clearEffects()");
    }

    @Override
    public Iterator<Effect> iterator() {
        // 只读迭代器：绕开它做 add/remove 会抛 UOE，逼调用方走下面的 API
        return Collections.unmodifiableList(effects).iterator();
    }

    /**
     * 追加一个效果（<b>不过滤、不合并</b>；合并逻辑在 {@code LivingThing#addEffect} 里）。
     *
     * @param effect 效果
     */
    public void addEffect(Effect effect) {
        if (effect != null) {
            effects.add(effect);
        }
    }

    /**
     * 替换第 {@code index} 个效果（{@code LivingThing#addEffect} 的"等级更高就替换"用）。
     *
     * @param index  下标
     * @param effect 新效果
     */
    public void setEffect(int index, Effect effect) {
        effects.set(index, effect);
    }

    /**
     * 按引用删一个效果。
     *
     * @param effect 效果
     * @return 有没有删掉
     */
    public boolean removeEffect(Effect effect) {
        return effects.remove(effect);
    }

    /**
     * 列表 API 的 {@code removeIf} 一律拒绝（只读视图的迭代器不允许 remove）。
     * <p>
     * 2026-11-15 第 79 批：默认实现会走 {@code iterator()}（那是只读迭代器），
     * 抛的是<b>无 message 的 UOE</b>，极难定位；这里显式拦一下，至少把"该用哪个 API"写清楚。
     *
     * @param filter 判定（不会被执行）
     * @return 不会返回
     */
    @Override
    public boolean removeIf(Predicate<? super Effect> filter) {
        throw new UnsupportedOperationException("效果列表只读：请用 removeEffectsIf(...) / clearEffects()");
    }

    /**
     * 批量删。
     *
     * @param filter 判定
     * @return 有没有删掉东西
     */
    public boolean removeEffectsIf(Predicate<Effect> filter) {
        return effects.removeIf(filter);
    }

    /**
     * 整表清空。
     */
    public void clearEffects() {
        effects.clear();
    }

    /**
     * 整表替换（复制构造器 / {@code whenFightEnds} / {@code /data} 的写回都走它）。
     *
     * @param list 新的效果列表（{@code null} = 清空）
     */
    public void replaceAll(List<Effect> list) {
        effects.clear();
        if (list != null) {
            effects.addAll(list);
        }
    }

    /**
     * @return 一份快照（遍历时想改列表就用它）
     */
    public List<Effect> snapshot() {
        return new ArrayList<>(effects);
    }
}
