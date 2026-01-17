package cn.gfhnv.game;

import cn.gfhnv.game.system.physics.type.Aceleration;
import cn.gfhnv.game.system.physics.type.Force;
import cn.gfhnv.game.system.physics.type.Velocity;

import java.math.BigDecimal;
import java.util.Objects;

public class Thing {
    private BigDecimal mass=new BigDecimal(0);
    private Force force=new Force(new BigDecimal(0),new BigDecimal(0),new BigDecimal(0));
    private Velocity velocity=new Velocity(new BigDecimal(0),new BigDecimal(0),new BigDecimal(0));
    private Aceleration aceleration=new Aceleration(new BigDecimal(0),new BigDecimal(0),new BigDecimal(0));

    public Aceleration getAceleration() {
        return aceleration;
    }

    public void setAceleration(Aceleration aceleration) {
        this.aceleration = aceleration;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Thing thing = (Thing) o;
        return Objects.equals(getMass(), thing.getMass()) && Objects.equals(getForce(), thing.getForce()) && Objects.equals(getVelocity(), thing.getVelocity()) && Objects.equals(getAceleration(), thing.getAceleration()) && Objects.equals(getX(), thing.getX()) && Objects.equals(getY(), thing.getY()) && Objects.equals(getZ(), thing.getZ());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getMass(), getForce(), getVelocity(), getAceleration(), getX(), getY(), getZ());
    }

    private BigDecimal x=new BigDecimal(0);
    private BigDecimal y=new BigDecimal(0);
    private BigDecimal z=new BigDecimal(0);
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
