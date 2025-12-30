package com.example.mobcapture.command;

import com.example.mobcapture.MobCaptureMod;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MobCaptureMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        // ✅ 둘 다 반드시 등록
        RoomCommand.register(event.getDispatcher());
        SpawnPointCommand.register(event.getDispatcher());
    }
}
