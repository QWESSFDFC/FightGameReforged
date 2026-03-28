package cn.gfhnv.game;

import cn.gfhnv.game.inventory.Inventory;
import cn.gfhnv.game.system.physics.type.Acceleration;
import cn.gfhnv.game.system.physics.type.Force;
import cn.gfhnv.game.system.physics.type.Position;
import cn.gfhnv.game.system.physics.type.Velocity;

import java.math.BigDecimal;
import java.util.Objects;

public class Thing {
    private BigDecimal mass = new BigDecimal(1);
    private Force force = new Force(new BigDecimal(0), new BigDecimal(0), new BigDecimal(0));
    private Velocity velocity = new Velocity(new BigDecimal(0), new BigDecimal(0), new BigDecimal(0));
    private Acceleration acceleration = new Acceleration(new BigDecimal(0), new BigDecimal(0), new BigDecimal(0));
    private Position position = new Position(new BigDecimal(0), new BigDecimal(0), new BigDecimal(0));
    private Inventory inventory = new Inventory(1);

    public Thing(BigDecimal mass) {
        this.mass = mass;
    }

    public Thing() {
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

    public Thing facSetInventory(Inventory inventory) {
        this.setInventory(inventory);
        return this;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public Acceleration getAcceleration() {
        return acceleration;
    }

    public void setAcceleration(Acceleration acceleration) {
        this.acceleration = acceleration;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Thing thing = (Thing) o;
        return Objects.equals(getMass(), thing.getMass()) && Objects.equals(getForce(), thing.getForce()) && Objects.equals(getVelocity(), thing.getVelocity()) && Objects.equals(getAcceleration(), thing.getAcceleration()) && Objects.equals(getInventory(), thing.getInventory());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getMass(), getForce(), getVelocity(), getAcceleration());
    }

    @Override
    public String toString() {
        return "Thing{" +
                "mass=" + mass +
                ", force=" + force +
                ", velocity=" + velocity +
                '}';
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
