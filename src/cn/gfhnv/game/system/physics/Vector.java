package cn.gfhnv.game.system.physics;

import java.math.BigDecimal;

public class Vector {
    private BigDecimal xScale;
    private BigDecimal yScale;
    private BigDecimal zScale;

    public Vector(BigDecimal xScale, BigDecimal yScale, BigDecimal zScale) {
        this.xScale = xScale;
        this.yScale = yScale;
        this.zScale = zScale;
    }

    public static Vector composition(Vector v1, Vector v2) {
        return new Vector(v2.getxScale().add(v1.getxScale()), v2.getyScale().add(v1.getyScale()), v2.getzScale().add(v1.getzScale()));
    }

    @Override
    public String toString() {
        return "Vector{" +
                "xScale=" + xScale +
                ", yScale=" + yScale +
                ", zScale=" + zScale +
                '}';
    }

    public BigDecimal getxScale() {
        return xScale;
    }

    public BigDecimal getyScale() {
        return yScale;
    }

    public BigDecimal getzScale() {
        return zScale;
    }
}
