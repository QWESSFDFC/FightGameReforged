package cn.gfhnv.debug_tools;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entityController.UniversalController;
import cn.gfhnv.game.system.ElementSort;

import java.util.ArrayList;

/**
 * 自测探针：<b>被引用的实体</b>在 {@code /data} 输出里长什么样。
 * <p>
 * <b>为什么要有它</b>：{@link cn.gfhnv.game.data.DataBridge} 的读方向有一条规则 ——
 * <b>根</b>对象全量展开，而它身上指向另一个实体的字段只输出 {@code {uuid:"…"}}。
 * 游戏里"引用套引用"是真实存在的（{@code FlameReaver.cloudOfDeathSummons}、
 * {@code BrokenContainer.lastAttacker}），但官方模板不好在自测里摆出想要的形状
 * （列表里塞几只、引用指回自己）；用一个字段形状最简单的探针，断言才读得出证据。
 * <p>
 * 两个字段各盯一件事：
 * <ul>
 *     <li>{@link #buddy}：普通引用 —— 断言它<b>只有 uuid</b>，自己那 60 多个字段一个都不许冒出来；</li>
 *     <li>{@link #self}：<b>指回自己</b>的引用 —— 这条同时证明"非根实体不给 uuid 之外的字段"
 *     是从类型上短路掉的（不递归），否则这里会直接栈溢出。</li>
 * </ul>
 * 它不进 {@code mods/}、不改 {@code config/}、不是官方内容 —— 纯自测探针。
 *
 * @author AI（DeepSeek）生成
 */
public class NestedReferenceProbeEntity extends LivingThing {

    /**
     * 普通引用：一个"被引用的实体"。
     */
    private LivingThing buddy;

    /**
     * 指回自己的引用（防环那条路的探针）。
     */
    private LivingThing self;

    /**
     * 造一个探针实体；名字与 id 用来在断言里认出它。
     *
     * @param name 显示名
     * @param id   注册表 id
     */
    public NestedReferenceProbeEntity(String name, String id) {
        super(name, id, 0.0, 0.0, 0.0, 0.0, 0.0,
                100, 1L, "insect", 10, 10, 10, ElementSort.FIRE);
        // 控制器不能为 null：LivingThing 的拷贝构造器会照着它重建一个
        this.setController(new UniversalController(new ArrayList<>(), this));
        this.self = this;
    }

    /**
     * @return 被引用的那个实体
     */
    public LivingThing getBuddy() {
        return buddy;
    }

    /**
     * @param buddy 被引用的那个实体
     */
    public void setBuddy(LivingThing buddy) {
        this.buddy = buddy;
    }

    /**
     * @return 指回自己的那个引用
     */
    public LivingThing getSelf() {
        return self;
    }

    /**
     * @param self 指回自己的那个引用
     */
    public void setSelf(LivingThing self) {
        this.self = self;
    }
}
