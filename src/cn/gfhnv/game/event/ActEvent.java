package cn.gfhnv.game.event;

import cn.gfhnv.game.entity.LivingThing;

public class ActEvent extends Event {
    private LivingThing actingEntity;

    public ActEvent(LivingThing actingEntity) {
        this.actingEntity = actingEntity;
    }

    public LivingThing getActingEntity() {
        return actingEntity;
    }

    public void setActingEntity(LivingThing actingEntity) {
        this.actingEntity = actingEntity;
    }
}
