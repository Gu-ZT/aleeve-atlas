package dev.dubhe.map.client.waypoint;

import java.util.UUID;

public class Waypoint {
    public String id = UUID.randomUUID().toString();
    public String name = "New Waypoint";
    public String dimension = "minecraft:overworld";
    public int x;
    public int y = 64;
    public int z;
    public int color = 0xFF55FF;
    public boolean enabled = true;

    public Waypoint copy() {
        Waypoint copy = new Waypoint();
        copy.id = this.id;
        copy.name = this.name;
        copy.dimension = this.dimension;
        copy.x = this.x;
        copy.y = this.y;
        copy.z = this.z;
        copy.color = this.color;
        copy.enabled = this.enabled;
        return copy;
    }
}

