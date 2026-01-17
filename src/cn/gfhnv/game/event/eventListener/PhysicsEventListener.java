package cn.gfhnv.game.event.eventListener;

import cn.gfhnv.game.Thing;
import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.event.PhysicsStateUpdateEvent;
import cn.gfhnv.game.system.physics.type.Acceleration;
import cn.gfhnv.game.system.physics.type.Velocity;
import cn.gfhnv.game.world.World;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class PhysicsEventListener {
    @SubscribeEvent
    public void updateState(PhysicsStateUpdateEvent physicsStateUpdateEvent) {
        List<? extends Thing> things = World.getThings();
        if (things.isEmpty()) {
            return;
        }
        for (Thing thing : things) {
            if (thing.getMass().equals(BigDecimal.ZERO)) {
                thing.setMass(BigDecimal.valueOf(0.01));
            }
            thing.setAcceleration(new Acceleration(thing.getForce().getxScale().divide(thing.getMass(), RoundingMode.HALF_UP), thing.getForce().getyScale().divide(thing.getMass(), RoundingMode.HALF_UP), thing.getForce().getzScale().divide(thing.getMass(), RoundingMode.HALF_UP)));
            thing.setVelocity(new Velocity(thing.getVelocity().getxScale().add(thing.getAcceleration().getxScale().multiply(BigDecimal.valueOf(1))), thing.getVelocity().getyScale().add(thing.getAcceleration().getyScale().multiply(BigDecimal.valueOf(1))), thing.getVelocity().getzScale().add(thing.getAcceleration().getzScale().multiply(BigDecimal.valueOf(1)))));
        }
    }
}
