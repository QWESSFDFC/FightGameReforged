package cn.gfhnv.game.event;

import cn.gfhnv.game.world.World;

public class PhysicsStateUpdateEvent extends Event {
    private final World world;
    public PhysicsStateUpdateEvent(World world) {
        this.world = world;
    }

    public World getWorld() {
        return world;
    }
}
