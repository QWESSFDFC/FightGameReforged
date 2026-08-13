package cn.gfhnv.game;

import cn.gfhnv.game.system.physics.Vector;
import cn.gfhnv.game.system.physics.type.Acceleration;
import cn.gfhnv.game.system.physics.type.Force;
import cn.gfhnv.game.system.physics.type.Position;
import cn.gfhnv.game.system.physics.type.Velocity;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 游戏世界中的基础对象（Thing）。
 * <p>
 * 所有可放进游戏世界的对象（实体、物品等）都继承自本类。Thing 提供了：
 * <ul>
 *     <li><b>uuid</b>：实例化时自动生成的唯一标识，用于 {@link #equals(Object)} 判定同一对象；</li>
 *     <li><b>tags</b>：行为 Tag 权重表（供 Utility AI 思考系统决策使用）；</li>
 *     <li><b>物理属性</b>：质量（mass）、力（force）、速度（velocity）、加速度（acceleration）、位置（position），
 *     构成一个简单的牛顿力学模型；</li>
 *     <li><b>id</b>：注册表标识（可为空，通常由模组/游戏内容注册时分配，如 {@code modId:contentId}）。</li>
 * </ul>
 *
 * @author gfhnv
 */
public class Thing {
    private final String uuid;
    private Map<TagType, Tag> tags = new EnumMap<>(TagType.class);
    private double mass = 1;
    private Force force = new Force(0, 0, 0);
    private Velocity velocity = new Velocity(0, 0, 0);
    private Acceleration acceleration = new Acceleration(0, 0, 0);
    private Position position = new Position(0, 0, 0);
    private String id;

    /**
     * 构造一个指定质量的 Thing。
     *
     * @param mass 质量
     */
    public Thing(double mass) {
        this.mass = mass;
        this.uuid = UUID.randomUUID().toString();

    }

    /**
     * 构造一个默认质量（1.0）的 Thing。
     */
    public Thing() {
        this.uuid = UUID.randomUUID().toString();
    }

    @Override
    public String toString() {
        return "Thing{" +
                "tags=" + tags +
                ", id='" + id + '\'' +
                '}';
    }

    /**
     * @return 注册表标识（可为空）
     */
    public String getId() {
        return id;
    }

    /**
     * 设置注册表标识。
     *
     * @param id 注册表标识
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return 实例唯一标识（UUID）
     */
    public String getUUID() {
        return uuid;
    }

    /**
     * 链式设置质量。
     *
     * @param mass 质量
     * @return 当前 Thing 实例
     */
    public Thing facSetMass(double mass) {
        this.setMass(mass);
        return this;
    }

    /**
     * 链式设置力。
     *
     * @param force 力
     * @return 当前 Thing 实例
     */
    public Thing facSetForce(Force force) {
        this.setForce(force);
        return this;
    }

    /**
     * 链式设置速度。
     *
     * @param velocity 速度
     * @return 当前 Thing 实例
     */
    public Thing facSetVelocity(Velocity velocity) {
        this.setVelocity(velocity);
        return this;
    }

    /**
     * 链式设置加速度。
     *
     * @param acceleration 加速度
     * @return 当前 Thing 实例
     */
    public Thing facSetAcceleration(Acceleration acceleration) {
        this.setAcceleration(acceleration);
        return this;
    }

    /**
     * 链式设置位置。
     *
     * @param position 位置
     * @return 当前 Thing 实例
     */
    public Thing facSetPosition(Position position) {
        this.setPosition(position);
        return this;
    }

    /**
     * @return 加速度
     */
    public Acceleration getAcceleration() {
        return acceleration;
    }

    /**
     * 设置加速度。
     *
     * @param acceleration 加速度
     */
    public void setAcceleration(Vector acceleration) {
        this.acceleration = (Acceleration) acceleration;
    }


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Thing thing = (Thing) o;
        return Objects.equals(uuid, thing.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(uuid);
    }

    /**
     * @return 速度
     */
    public Velocity getVelocity() {
        return velocity;
    }

    /**
     * 设置速度。
     *
     * @param velocity 速度
     */
    public void setVelocity(Vector velocity) {
        this.velocity = (Velocity) velocity;
    }

    /**
     * @return 力
     */
    public Force getForce() {
        return force;
    }

    /**
     * 设置力。
     *
     * @param force 力
     */
    public void setForce(Vector force) {
        this.force = (Force) force;
    }

    /**
     * @return 质量
     */
    public double getMass() {
        return mass;
    }

    /**
     * 设置质量。
     *
     * @param mass 质量
     */
    public void setMass(double mass) {
        this.mass = mass;
    }

    /**
     * @return 位置
     */
    public Position getPosition() {
        return position;
    }

    /**
     * 设置位置。
     *
     * @param position 位置
     */
    public void setPosition(Vector position) {
        this.position = (Position) position;
    }

    /**
     * @return 行为 Tag 权重表（供 Utility AI 思考系统决策使用）
     */
    public Map<TagType, Tag> getTags() {
        return tags;
    }

    /**
     * 设置行为 Tag 权重表。
     *
     * @param tags 行为 Tag 权重表
     */
    public void setTags(Map<TagType, Tag> tags) {
        this.tags = tags;
    }
}
