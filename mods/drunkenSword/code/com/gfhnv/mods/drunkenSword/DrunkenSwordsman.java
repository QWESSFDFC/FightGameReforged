package com.gfhnv.mods.drunkenSword;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entity.Player;
import cn.gfhnv.game.entityController.PlayerController;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;

import java.util.ArrayList;
import java.util.List;

/**
 * 「酒剑仙」—— 醉剑仙模组的新角色。
 * <p>
 * <b>机制</b>：靠普攻攒【醉意】，战技与大招把层数一次性换成伤害（见 {@link Drunkenness}）；
 * 层数本身还提供减伤，所以"攒着"是防御、"打出去"是爆发 —— 什么时候倾泻是唯一的决策点。
 * <b>面板</b>：比官方角色快（速度 {@value #SPEED}）、攻击成长高、防御偏薄 ——
 * 一个"要么先手打死人、要么被打死"的角色。
 * <p>
 * 继承 {@link Player}（而不是直接继承 {@code LivingThing}）只是为了站在"角色"那一类里；
 * 游戏里选人、选敌人读的是同一个注册表，所以这个类<b>也能被选成敌人</b>，
 * 想试刀可以直接把它丢到对面。
 * <p>
 * 控制器用 {@link PlayerController}：它是"玩家手操"的控制器，也是 {@code LivingThing}
 * 复制构造器认得的两种控制器之一（另一种是 {@code ThinkingControllerAI}）——
 * 换成自己写的控制器，复制之后会被降级成随机 AI。
 *
 * @author AI（DeepSeek）生成
 */
public class DrunkenSwordsman extends Player {

    /**
     * 注册 id（进游戏时会被补成 {@code drunkenSword:drunkenSwordsman}）。
     */
    public static final String ID = "drunkenSwordsman";

    /**
     * 速度。官方角色都是 120，这里给 130，让他更容易抢到先手。
     */
    public static final long SPEED = 130;

    /**
     * 生命成长系数：125 级时生命约 4416。
     */
    public static final double HP_GROW = 34;

    /**
     * 攻击成长系数：125 级时攻击约 4078。
     */
    public static final double ATK_GROW = 32;

    /**
     * 防御成长系数：125 级时防御约 1688（白厄是 25，属于重甲；这里是轻甲）。
     * <p>
     * 面板按 {@code 生命 = 防御 = (等级-1)×成长 + 200}、{@code 攻击 = 110 + 成长×(等级-1)} 算，
     * 所以 125 级时是 4416 血 / 4078 攻 / 1688 防。
     */
    public static final double DFK_GROW = 12;

    /**
     * 背包格子数。官方角色（玩家一 / 白厄）用的都是 63。
     */
    public static final int INVENTORY_SLOTS = 63;

    /**
     * 开局白送的【醉意】层数（{@link Drunkenness#MAX_STACKS} 以内）。
     * <p>
     * <b>给 0 也不等于"没有这条效果"</b>：{@link Drunkenness#add} 在层数为 0 时照样会把状态挂上身，
     * 所以哪怕把这里改成 0，从第 1 回合起 {@code /effect list} 里就能看到这条资源，
     * {@code /data modify entity @s entityEffectList[{id:"drunkenSword:drunkenness"}].level set N} 也就能改了
     * ——【醉意】只能由玩法产生（{@code /effect} 施加不了角色专属效果），
     * 开局挂一条可以免掉"必须先打一下普攻"这一步。
     * <p>
     * 数值取舍：大招的门槛是 {@link FrostSword#REQUIRED_STACKS} 层，
     * 给到门槛就是"开局直接能开大"；给 {@value #INITIAL_STACKS} 层则保留了攒层的过程。
     * 开局时是<b>补到</b>这个层数（已经够了就不动），不是每次都加一遍。
     */
    public static final int INITIAL_STACKS = 2;

    /**
     * 构造酒剑仙。
     * <p>
     * {@code LivingThing} 构造器的参数顺序是：
     * 名称、id、五行抗性（金木水火土之外的顺序为 火/水/金/木/土）、速度、等级、类型、
     * 生命成长、攻击成长、防御成长、元素属性。
     * <p>
     * 元素属性选火：主元素法力上限是 {@code 成长 × (等级-1) + 200}，
     * 火属性才能拿到足够放战技/大招的火法力（见 {@code Mana} 与 {@code recoverManaEveryTurn}）。
     *
     * @param level 等级（注册到注册表时用 125，与官方角色一致）
     */
    public DrunkenSwordsman(long level) {
        super("酒剑仙", ID, 0.3, 0.3, 0.0, 0.0, 0.0, SPEED, level, "player",
                HP_GROW, ATK_GROW, DFK_GROW, ElementSort.FIRE);

        this.setMass(60);
        this.setDescription("提剑也提酒壶的剑客。醉意既当弹药也当护甲 —— 每层提供 "
                + (int) (Drunkenness.REDUCTION_PER_STACK * 100) + "% 减伤，攒到 "
                + FrostSword.REQUIRED_STACKS + " 层才能放出大招；打光醉意就等于把护甲也交出去了。");
        this.getInventory().addSlot(INVENTORY_SLOTS);

        List<Skill> skills = new ArrayList<>();
        skills.add(new RaiseCup());
        skills.add(new LanternSword());
        skills.add(new FrostSword());
        this.setController(new PlayerController(skills, this));
    }

    /**
     * 复制构造器。参数类型必须写成 {@link DrunkenSwordsman}，否则
     * {@link #copy()} 里那个 {@code new} 会调到自己（无限递归）。
     *
     * @param other 被复制的角色
     */
    public DrunkenSwordsman(DrunkenSwordsman other) {
        super(other);
    }

    /**
     * 深拷贝。开局选人、进战斗、以及 {@code UniversalController} 之类的地方都会调它，
     * <b>不重写就会在开局第一步抛 {@code RuntimeException("请重写此方法..类...")}</b>。
     *
     * @return 副本
     */
    @Override
    public LivingThing copy() {
        return new DrunkenSwordsman(this);
    }

    /**
     * 开局挂上【醉意】，层数补到 {@link #INITIAL_STACKS}。
     * <p>
     * <b>为什么放在这个钩子而不是构造器</b>：构造器在"注册表模板"和每次 {@code copy()} 时都会跑，
     * 而模板是常驻的、会被反复复制，往它身上挂状态既不合适也不干净；
     * 这个钩子只在<b>真正参战</b>时由 {@code FightStartEventListener} 调一次。
     * 下一局也不用自己清：整场结束时框架会清空效果列表（{@code LivingThing#whenFightEnds}），
     * 顺带调 {@link Drunkenness#whenLastTimeEnd} 把减伤来源摘掉。
     * <p>
     * <b>语义是"补到 N 层"而不是"+N 层"</b>：已经够了就什么都不做。
     * 否则同一个实例被通知两次开局（或者以后加了"中途入场"之类的调用点）就会白送一份，
     * 症状是"开局莫名其妙有 4 层"。层数比 {@link #INITIAL_STACKS} 多时也<b>不往下扣</b>——
     * 这是"开局保底"，不该把已经攒起来的状态抹掉。
     *
     * @param fight 当前战斗上下文
     */
    @Override
    public void whenFightStart(Fight fight) {
        super.whenFightStart(fight);
        int current = Drunkenness.stacksOf(this);
        // of(...) == null 也要走一遍：Drunkenness#add 在层数为 0 时同样会把状态挂上身，
        // 这样 INITIAL_STACKS 改成 0 时【醉意】依然存在于效果列表里（能被 /data 改、能被 /effect list 看到）
        if (Drunkenness.of(this) == null || current < INITIAL_STACKS) {
            Drunkenness.add(this, Math.max(0, INITIAL_STACKS - current));
        }
    }
}
