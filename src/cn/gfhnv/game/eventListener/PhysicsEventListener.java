package cn.gfhnv.game.eventListener;

import cn.gfhnv.game.Thing;
import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.event.PhysicsStateUpdateEvent;
import cn.gfhnv.game.system.physics.Vector;
import cn.gfhnv.game.system.physics.type.Acceleration;
import cn.gfhnv.game.system.physics.type.Position;

import java.util.List;

public class PhysicsEventListener {
    @SubscribeEvent
    public void updateState(PhysicsStateUpdateEvent physicsStateUpdateEvent) {
        List<? extends Thing> things = physicsStateUpdateEvent.getFight().getAllEntities();
        if (things.isEmpty()) {
            return;
        }
        for (Thing thing : things) {
            if (thing.getMass() == 0) {
                thing.setMass(0.01);
            }
            thing.setAcceleration(new Acceleration(thing.getForce().getX() / thing.getMass(), thing.getForce().getY() / thing.getMass(), thing.getForce().getZ() / thing.getMass()));
            thing.setVelocity(Vector.composition(thing.getVelocity(), new Vector(thing.getAcceleration().getX(), thing.getAcceleration().getY(), thing.getAcceleration().getZ())));
            Position displacement = new Position(thing.getVelocity().getX(), thing.getVelocity().getY(), thing.getVelocity().getZ());
            thing.setPosition(Vector.composition(thing.getPosition(), displacement));
            thing.setForce(new Vector(0, 0, 0));

        }
    }
}
