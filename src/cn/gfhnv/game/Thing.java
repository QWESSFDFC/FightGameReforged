package cn.gfhnv.game;

import cn.gfhnv.game.system.physics.type.Acceleration;
import cn.gfhnv.game.system.physics.type.Force;
import cn.gfhnv.game.system.physics.type.Position;
import cn.gfhnv.game.system.physics.type.Velocity;
import cn.gfhnv.game.system.thinkingSystem.Tag;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Thing {
    private final String uuid;
    private BigDecimal mass = new BigDecimal(1);
    private Force force = new Force(new BigDecimal(0), new BigDecimal(0), new BigDecimal(0));
    private Velocity velocity = new Velocity(new BigDecimal(0), new BigDecimal(0), new BigDecimal(0));
    private Acceleration acceleration = new Acceleration(new BigDecimal(0), new BigDecimal(0), new BigDecimal(0));
    private Position position = new Position(new BigDecimal(0), new BigDecimal(0), new BigDecimal(0));
    private List<Tag> tags = new ArrayList<>();

    public Thing(BigDecimal mass) {
        this.mass = mass;
        this.uuid = UUID.randomUUID().toString();
    }

    public Thing() {
        this.uuid = UUID.randomUUID().toString();
    }

    public Thing facSetTag(Tag tag) {
        if (this.tags.contains(tag)) {
            return this;
        }
        this.tags.add(tag);
        return this;
    }

    public String getUUID() {
        return uuid;
    }

    public Thing facSetMass(BigDecimal mass) {
        this.setMass(mass);
        return this;
    }

    public Thing facSetForce(Force force) {
        this.setForce(force);
        return this;
    }

    public Thing facSetVelocity(Velocity velocity) {
        this.setVelocity(velocity);
        return this;
    }

    public Thing facSetAcceleration(Acceleration acceleration) {
        this.setAcceleration(acceleration);
        return this;
    }

    public Thing facSetPosition(Position position) {
        this.setPosition(position);
        return this;
    }


    public Acceleration getAcceleration() {
        return acceleration;
    }

    public void setAcceleration(Acceleration acceleration) {
        this.acceleration = acceleration;
    }


    @Override
    public String toString() {
        return "Thing{" +
                "mass=" + mass +
                ", force=" + force +
                ", velocity=" + velocity +
                ", acceleration=" + acceleration +
                ", position=" + position +
                ", uuid='" + uuid + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Thing thing = (Thing) o;
        return Objects.equals(getMass(), thing.getMass()) && Objects.equals(getForce(), thing.getForce()) && Objects.equals(getVelocity(), thing.getVelocity()) && Objects.equals(getAcceleration(), thing.getAcceleration()) && Objects.equals(getPosition(), thing.getPosition()) && Objects.equals(uuid, thing.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getMass(), getForce(), getVelocity(), getAcceleration(), getPosition(), uuid);
    }

    public Velocity getVelocity() {
        return velocity;
    }

    public void setVelocity(Velocity velocity) {
        this.velocity = velocity;
    }

    public Force getForce() {
        return force;
    }

    public void setForce(Force force) {
        this.force = force;
    }

    public BigDecimal getMass() {
        return mass;
    }

    public void setMass(BigDecimal mass) {
        this.mass = mass;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }
}
