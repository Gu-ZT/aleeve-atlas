package dev.dubhe.map.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.client.cache.MapCacheLifecycle;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public class AleeveAtlasCommand {
    @SubscribeEvent
    public static void onClientCommandRegister(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
            Commands.literal("aleeve_atlas")
                .then(
                    Commands.literal("cache")
                        .then(
                            Commands.literal("clear")
                                .executes(AleeveAtlasCommand::clearCache)
                        )
                )
        );
    }

    public static int clearCache(CommandContext<CommandSourceStack> context) {
        if (Minecraft.getInstance().level == null) return 0;
        MapCacheLifecycle.clearCache(Minecraft.getInstance().level);
        return 1;
    }
}
