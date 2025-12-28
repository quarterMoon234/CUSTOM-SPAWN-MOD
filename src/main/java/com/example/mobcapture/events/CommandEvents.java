package com.example.mobcapture.events;

import com.example.mobcapture.MobCaptureMod;
import com.example.mobcapture.command.RoomCommand;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MobCaptureMod.MODID)
public class CommandEvents {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        RoomCommand.register(event.getDispatcher());
    }
}
