package cn.gfhnv.game.effect;

import cn.gfhnv.game.entity.LivingThing;

import java.util.Objects;

public class Effect {
    private String id;
    private int level;//效果等级
    private int lastTime;//seconds

    public Effect(String id, int level, int lastTime) {
        this.id = id;
        this.level = level;
        this.lastTime = lastTime;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Effect effect = (Effect) o;
        return Objects.equals(getId(), effect.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
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
        System.out.printf("这里写效果具体内容........请重写这个方法.回合更新时此方法会被调用");
    }

    public boolean ifEntityHavaThisEffect(LivingThing l) {
        if (l.getEntityEffectList() == null) return false;
        for (Effect e : l.getEntityEffectList()) {
            if (e == null) return false;
            return Objects.equals(e.getID(), id);
        }
        return false;
    }
}
