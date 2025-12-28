package com.example.mobcapture.command;

import com.example.mobcapture.MobCaptureMod;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MobCaptureMod.MODID)
public class ModCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        // 이미 RoomCommand 등록 중이면 그대로 두고,
        // 필요하면 아래 주석 해제해서 같이 등록하세요.
        // RoomCommand.register(event.getDispatcher());

        SpawnPointCommand.register(event.getDispatcher());
    }
}
