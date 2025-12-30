package com.example.mobcapture.command;

import com.example.mobcapture.MobCaptureMod;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MobCaptureMod.MODID)
public class ModCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        RoomCommand.register(event.getDispatcher());
        SpawnPointCommand.register(event.getDispatcher());

        // ✅ 추가
        TeleportBlockCommand.register(event.getDispatcher());
    }
}
