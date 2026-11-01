package dev.dubhe.map.config;

import dev.anvilcraft.lib.v2.config.util.TranslatableEnum;

/// 实体名称与标记样式独立控制，按住模式跟随玩家列表键位。
public enum RadarNameDisplay implements TranslatableEnum {
    OFF,
    ALWAYS,
    HOLD_PLAYER_LIST;

    public boolean visible(boolean playerListHeld) {
        return this == ALWAYS || this == HOLD_PLAYER_LIST && playerListHeld;
    }
}
