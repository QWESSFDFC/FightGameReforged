package cn.gfhnv.game.effect;

import cn.gfhnv.game.entity.LivingThing;

import java.util.Objects;

public class Effect {
    private String id;
    private int level = 1;//效果等级
    private int lastTime;//turns
    private boolean isInfinity = false;
    private String origin = "null";
    private boolean negative=false;

    public boolean isNegative() {
        return negative;
    }

    public void setNegative(boolean negative) {
        this.negative = negative;
    }

    public Effect(String id, int level, int lastTime) {
        this.id = id;
        this.level = level;
        this.lastTime = lastTime;
    }

    public Effect(Effect effect) {
        this.id = effect.id;
        this.level = effect.level;
        this.lastTime = effect.lastTime;
    }

    public Effect(String id) {
        this.id = id;
    }

    public void initialEffect(LivingThing livingThing) {//获得效果时执行.默认执行生效方法.如果需要,可以重写.可能导致效果生效次数多一次
        this.comeIntoEffect(livingThing);
    }

    public Effect copy() {
        throw new UnsupportedOperationException("子类必须重写此方法,当前类 :" + this.getClass().getName());
    }

    @Override
    public String toString() {
        return "Effect{" +
                "id='" + id + '\'' +
                ", level=" + level +
                ", lastTime=" + lastTime +
                '}';
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void whenLastTimeEnd(LivingThing livingThing) {
    }

    public Effect facSetId(String id) {
        this.setId(id);
        return this;
    }

    public Effect facSetLevel(int level) {
        this.setLevel(level);
        return this;
    }

    public Effect facSetLastTime(int lastTime) {
        this.setLastTime(lastTime);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Effect effect = (Effect) o;
        return isInfinity() == effect.isInfinity() && Objects.equals(getId(), effect.getId()) && Objects.equals(getOrigin(), effect.getOrigin());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), isInfinity(), getOrigin());
    }

    public String getID() {
        return id;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getLastTime() {
        return lastTime;
    }

    public void setLastTime(int lastTime) {
        this.lastTime = lastTime;
    }

    public void comeIntoEffect(LivingThing thing) {
        System.out.println("这里写效果具体内容........请重写这个方法.回合更新时此方法会被调用");
    }

    public boolean isInfinity() {
        return isInfinity;
    }

    public void setInfinity(boolean infinity) {
        isInfinity = infinity;
    }

    public String getOrigin() {
        return origin;
    }

    public Effect setOrigin(String origin) {
        this.origin = origin;
        return this;
    }
}
