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

public class Thing {
    private final String uuid;
    private Map<TagType, Tag> tags = new EnumMap<>(TagType.class);
    private double mass = 1;
    private Force force = new Force(0, 0, 0);
    private Velocity velocity = new Velocity(0, 0, 0);
    private Acceleration acceleration = new Acceleration(0, 0, 0);
    private Position position = new Position(0, 0, 0);
    private String id;

    public Thing(double mass) {
        this.mass = mass;
        this.uuid = UUID.randomUUID().toString();

    }

    public Thing() {
        this.uuid = UUID.randomUUID().toString();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUUID() {
        return uuid;
    }

    public Thing facSetMass(double mass) {
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

    public void setAcceleration(Vector acceleration) {
        this.acceleration = (Acceleration) acceleration;
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
        return Objects.equals(uuid, thing.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(uuid);
    }

    public Velocity getVelocity() {
        return velocity;
    }

    public void setVelocity(Vector velocity) {
        this.velocity = (Velocity) velocity;
    }

    public Force getForce() {
        return force;
    }

    public void setForce(Vector force) {
        this.force = (Force) force;
    }

    public double getMass() {
        return mass;
    }

    public void setMass(double mass) {
        this.mass = mass;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Vector position) {
        this.position = (Position) position;
    }

    public Map<TagType, Tag> getTags() {
        return tags;
    }

    public void setTags(Map<TagType, Tag> tags) {
        this.tags = tags;
    }
}
