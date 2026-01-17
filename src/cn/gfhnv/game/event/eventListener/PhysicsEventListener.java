package cn.gfhnv.game.event.eventListener;
import cn.gfhnv.game.Thing;
import cn.gfhnv.game.annotation.SubscribeEvent;
import cn.gfhnv.game.entity.Entity;
import cn.gfhnv.game.event.PhysicsStateUpdateEvent;
import cn.gfhnv.game.system.physics.Vector;
import cn.gfhnv.game.system.physics.type.Aceleration;
import cn.gfhnv.game.system.physics.type.Velocity;
import cn.gfhnv.game.world.World;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
public class PhysicsEventListener {
    @SubscribeEvent
    public void updateState(PhysicsStateUpdateEvent physicsStateUpdateEvent){
        List<?extends Thing> things=World.getEntityList();
        if  (things.isEmpty()){return;}
        for (Thing thing:things){
            thing.setAceleration(new Aceleration(thing.getForce().getxScale().divide(thing.getMass()),thing.getForce().getyScale().divide(thing.getMass()),thing.getForce().getzScale().divide(thing.getMass())));
            thing.setVelocity(new Velocity(thing.getVelocity().getxScale().add(thing.getAceleration().getxScale().multiply(BigDecimal.valueOf(1))),thing.getVelocity().getyScale().add(thing.getAceleration().getyScale().multiply(BigDecimal.valueOf(1))),thing.getVelocity().getzScale().add(thing.getAceleration().getzScale().multiply(BigDecimal.valueOf(1)))));
        }
    }
}
