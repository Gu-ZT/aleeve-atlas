package dev.dubhe.map.client.waypoint;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/// 路径点传送与聊天坐标操作。
public final class WaypointActions {
    private WaypointActions() {
    }

    public static void teleport(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.connection.sendCommand("tp @s %d %d %d".formatted(
                    pos.getX(), pos.getY(), pos.getZ()));
            minecraft.gui.setScreen(null);
        }
    }

    public static void shareCoordinates(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.connection.sendChat("%d %d %d".formatted(
                    pos.getX(), pos.getY(), pos.getZ()));
            minecraft.gui.setScreen(null);
        }
    }
}
