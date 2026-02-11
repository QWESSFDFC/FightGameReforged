package cn.gfhnv.game;

import cn.gfhnv.game.inventory.Inventory;
import cn.gfhnv.game.system.physics.type.Acceleration;
import cn.gfhnv.game.system.physics.type.Force;
import cn.gfhnv.game.system.physics.type.Velocity;

import java.math.BigDecimal;
import java.util.Objects;

public class Thing {
    private BigDecimal mass = new BigDecimal(1);
    private Force force = new Force(new BigDecimal(0), new BigDecimal(0), new BigDecimal(0));
    private Velocity velocity = new Velocity(new BigDecimal(0), new BigDecimal(0), new BigDecimal(0));
    private Acceleration acceleration = new Acceleration(new BigDecimal(0), new BigDecimal(0), new BigDecimal(0));
    private BigDecimal x = new BigDecimal(0);
    private BigDecimal y = new BigDecimal(0);
    private BigDecimal z = new BigDecimal(0);
    private Inventory inventory = new Inventory(1);

    public Thing(BigDecimal y, BigDecimal z, BigDecimal x, BigDecimal mass) {
        this.y = y;
        this.z = z;
        this.x = x;
        this.mass = mass;
    }

    public Thing(BigDecimal mass) {
        this.mass = mass;
    }

    public Thing() {
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
        return Objects.equals(getMass(), thing.getMass()) && Objects.equals(getForce(), thing.getForce()) && Objects.equals(getVelocity(), thing.getVelocity()) && Objects.equals(getAcceleration(), thing.getAcceleration()) && Objects.equals(getX(), thing.getX()) && Objects.equals(getY(), thing.getY()) && Objects.equals(getZ(), thing.getZ());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getMass(), getForce(), getVelocity(), getAcceleration(), getX(), getY(), getZ());
    }

    public BigDecimal getX() {
        return x;
    }

    public void setX(BigDecimal x) {
        this.x = x;
    }

    public BigDecimal getZ() {
        return z;
    }

    public void setZ(BigDecimal z) {
        this.z = z;
    }

    public BigDecimal getY() {
        return y;
    }

    public void setY(BigDecimal y) {
        this.y = y;
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
}
