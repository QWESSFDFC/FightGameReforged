package cn.gfhnv.game.effect;

import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.entity.LivingThing;

import java.util.Objects;

public class Effect {
    private String id;
    private int level;
    private int lastTime;
    public String getID() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
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
    @SubscribeEvent
    public boolean ifEntityHavaThisEffect(LivingThing l){
        if (l.getEntityEffectList()==null) return false;
        for (Effect e:l.getEntityEffectList()){
            if (e==null) return false;
            return Objects.equals(e.getID(), id);
        }
        return false;
    }
}
