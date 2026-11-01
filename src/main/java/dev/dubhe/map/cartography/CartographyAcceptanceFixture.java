package dev.dubhe.map.cartography;

import dev.dubhe.map.AleeveAtlas;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

/// 仅在专用验收进程启用；请使用隔离测试存档。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID)
public final class CartographyAcceptanceFixture {
    private CartographyAcceptanceFixture() { }
    @SubscribeEvent public static void starting(ServerStartingEvent event) {
        if (System.getenv("ALEEVE_ATLAS_CARTOGRAPHY_TEST") != null) AleeveAtlas.CONFIG.sync.enabled = false;
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (System.getenv("ALEEVE_ATLAS_CARTOGRAPHY_TEST") == null || !(event.getEntity() instanceof ServerPlayer player)) return;
        player.getInventory().clearContent();
        player.getInventory().selected = 0;
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().invulnerable = true;
        player.onUpdateAbilities();
        ItemStack map = MapItem.create(player.level(), 10240, 10240, (byte) 2, true, false);
        var data = MapItem.getSavedData(map, player.level());
        for (int z = 16; z < 112; z++) for (int x = 16; x < 112; x++) data.colors[z * 128 + x] = (byte) (x < 64 ? 5 : 50);
        data.setDirty();
        MapItemSavedData.addTargetDecoration(map, new BlockPos(data.centerX + 9, 64, data.centerZ + 9), "+", MapDecorationTypes.RED_X);
        player.setItemInHand(InteractionHand.MAIN_HAND, map);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.MAP, 3));
        player.getInventory().setItem(1, new ItemStack(Items.GLASS_PANE));
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket(0));
        player.containerMenu.sendAllDataToRemote();
    }
}
