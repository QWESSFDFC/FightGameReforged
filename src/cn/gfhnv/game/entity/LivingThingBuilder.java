package cn.gfhnv.game.entity;

import cn.gfhnv.game.entityController.UniversalController;
import cn.gfhnv.game.system.ElementSort;

/**
 * 生物构造器（Builder）：把 {@link LivingThing} 那个 <b>14 参数位置构造器</b>换成具名链式调用。
 * <p>
 * 为什么要有它：14 个位置参数（名字、id、五种抗性、速度、等级、类型、三个成长系数、元素）里
 * 有 <b>6 个 double 连在一起</b>，顺序错一个不会报错，只会让这只怪悄悄变强/变弱；
 * 而 {@code fireResistance / waterResistance / metalResistance / woodResistance / dirtResistance}
 * 的顺序还是"火水金木土"（不是金木水火土），每次手写都要回去数一遍。
 * <p>
 * <b>它不改变任何行为</b>：{@link #build()} 内部仍然调用那一个 14 参构造器，
 * 所以"构造期铺值顺序、法力初始化时机"与手写构造器逐个字节一致。Builder 只负责三件事：
 * ① 具名赋值；② 缺必填项时<b>当场报错</b>而不是造出半成品；③ 把"元素决定法力成长表"这类隐含规则
 * 收在一处（想覆盖就用 {@link #manaGrows(double, double, double, double, double)}）。
 * <p>
 * 用法：
 * <pre>{@code
 * LivingThing blade = LivingThing.builder("千冶·刃", "mortenax_blade")
 *         .level(125)
 *         .element(ElementSort.DIRT)
 *         .speed(120)
 *         .hpGrow(36).attackGrow(29).defenceGrow(5)
 *         .metalResistance(0).woodResistance(0).waterResistance(0.2)
 *         .fireResistance(0.3).dirtResistance(0.3)
 *         .controller(new ThinkingControllerAI())
 *         .build();
 * }</pre>
 * <b>什么时候别用它</b>：造<b>子类</b>（{@code new FlameReaver(...)} 这类）时用子类自己的构造器；
 * Builder 只造 {@link LivingThing} 本体，不认识子类的额外字段。
 *
 * @author AI（DeepSeek）生成；设计稿作者：仓库作者
 */
public final class LivingThingBuilder {

    private final String name;
    private final String id;
    private long level = 1;
    private ElementSort element;
    private long speed;
    private String type = "entity";
    private String description;
    private UniversalController controller;
    private double hpGrow;
    private double attackGrow;
    private double defenceGrow;
    private double metalResistance;
    private double woodResistance;
    private double waterResistance;
    private double fireResistance;
    private double dirtResistance;
    private double metalManaGrow;
    private double woodManaGrow;
    private double waterManaGrow;
    private double fireManaGrow;
    private double dirtManaGrow;
    private boolean manaGrowSpecified;

    /**
     * @param name 名称（必填）
     * @param id   唯一标识（必填）
     */
    LivingThingBuilder(String name, String id) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Builder：name 不能为空");
        }
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Builder：id 不能为空");
        }
        this.name = name;
        this.id = id;
    }

    /**
     * @param level 等级（默认 1）
     * @return 当前 Builder
     */
    public LivingThingBuilder level(long level) {
        this.level = level;
        return this;
    }

    /**
     * @param element 元素属性（<b>必填</b>：它决定五行法力成长表与伤害元素）
     * @return 当前 Builder
     */
    public LivingThingBuilder element(ElementSort element) {
        this.element = element;
        return this;
    }

    /**
     * @param speed 速度（<b>必填且必须 &gt; 0</b>：时间轴要拿它做除数）
     * @return 当前 Builder
     */
    public LivingThingBuilder speed(long speed) {
        this.speed = speed;
        return this;
    }

    /**
     * @param type 类型（默认 {@code "entity"}，只影响显示/统计）
     * @return 当前 Builder
     */
    public LivingThingBuilder type(String type) {
        this.type = type;
        return this;
    }

    /**
     * @param description 描述
     * @return 当前 Builder
     */
    public LivingThingBuilder description(String description) {
        this.description = description;
        return this;
    }

    /**
     * @param controller 行动控制器
     * @return 当前 Builder
     */
    public LivingThingBuilder controller(UniversalController controller) {
        this.controller = controller;
        return this;
    }

    /**
     * @param hpGrow 生命成长系数
     * @return 当前 Builder
     */
    public LivingThingBuilder hpGrow(double hpGrow) {
        this.hpGrow = hpGrow;
        return this;
    }

    /**
     * @param attackGrow 攻击成长系数
     * @return 当前 Builder
     */
    public LivingThingBuilder attackGrow(double attackGrow) {
        this.attackGrow = attackGrow;
        return this;
    }

    /**
     * @param defenceGrow 防御成长系数
     * @return 当前 Builder
     */
    public LivingThingBuilder defenceGrow(double defenceGrow) {
        this.defenceGrow = defenceGrow;
        return this;
    }

    /**
     * @param metalResistance 金抗性
     * @return 当前 Builder
     */
    public LivingThingBuilder metalResistance(double metalResistance) {
        this.metalResistance = metalResistance;
        return this;
    }

    /**
     * @param woodResistance 木抗性
     * @return 当前 Builder
     */
    public LivingThingBuilder woodResistance(double woodResistance) {
        this.woodResistance = woodResistance;
        return this;
    }

    /**
     * @param waterResistance 水抗性
     * @return 当前 Builder
     */
    public LivingThingBuilder waterResistance(double waterResistance) {
        this.waterResistance = waterResistance;
        return this;
    }

    /**
     * @param fireResistance 火抗性
     * @return 当前 Builder
     */
    public LivingThingBuilder fireResistance(double fireResistance) {
        this.fireResistance = fireResistance;
        return this;
    }

    /**
     * @param dirtResistance 土抗性
     * @return 当前 Builder
     */
    public LivingThingBuilder dirtResistance(double dirtResistance) {
        this.dirtResistance = dirtResistance;
        return this;
    }

    /**
     * 覆盖"元素决定的五行法力成长表"。
     * <p>
     * 默认（不调本方法）用 14 参构造器那张表：主元素 20，其余按相邻关系取 1 / 4 / 10。
     * 调了之后按这里的五个值重建法力表（{@link LivingThing#initialMana()}）。
     *
     * @param metal 金法力成长
     * @param wood  木法力成长
     * @param water 水法力成长
     * @param fire  火法力成长
     * @param dirt  土法力成长
     * @return 当前 Builder
     */
    public LivingThingBuilder manaGrows(double metal, double wood, double water, double fire, double dirt) {
        this.metalManaGrow = metal;
        this.woodManaGrow = wood;
        this.waterManaGrow = water;
        this.fireManaGrow = fire;
        this.dirtManaGrow = dirt;
        this.manaGrowSpecified = true;
        return this;
    }

    /**
     * 造出生物实例。
     * <p>
     * <b>必填没给会当场抛 {@link IllegalStateException}</b>（而不是造出 speed=0 / element=null 的半成品 ——
     * speed=0 会在时间轴 {@code 10000 / speed} 那里直接炸，element=null 会让法力表整个空掉）。
     *
     * @return 新的生物实例
     */
    public LivingThing build() {
        if (this.element == null) {
            throw new IllegalStateException("Builder：element 必填（它决定五行法力成长表与伤害元素）");
        }
        if (this.speed <= 0) {
            throw new IllegalStateException("Builder：speed 必填且必须 > 0（时间轴要拿它做除数）");
        }
        LivingThing thing = new LivingThing(this.name, this.id,
                this.fireResistance, this.waterResistance, this.metalResistance,
                this.woodResistance, this.dirtResistance,
                this.speed, this.level, this.type,
                this.hpGrow, this.attackGrow, this.defenceGrow, this.element);
        if (this.manaGrowSpecified) {
            thing.setMetalManaGrow(this.metalManaGrow);
            thing.setWoodManaGrow(this.woodManaGrow);
            thing.setWaterManaGrow(this.waterManaGrow);
            thing.setFireManaGrow(this.fireManaGrow);
            thing.setDirtManaGrow(this.dirtManaGrow);
            thing.initialMana();
        }
        if (this.description != null) {
            thing.setDescription(this.description);
        }
        if (this.controller != null) {
            thing.setController(this.controller);
        }
        return thing;
    }
}
