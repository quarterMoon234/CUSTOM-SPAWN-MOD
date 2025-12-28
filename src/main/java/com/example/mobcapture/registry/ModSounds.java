package com.example.mobcapture.registry;

import com.example.mobcapture.MobCaptureMod;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MobCaptureMod.MODID);

    public static final RegistryObject<SoundEvent> PORTAL_ENTER =
            SOUND_EVENTS.register(
                    "portal.enter",
                    () -> SoundEvent.createVariableRangeEvent(
                            new ResourceLocation(MobCaptureMod.MODID, "portal.enter")
                    )
            );

    public static void register(IEventBus bus) {
        SOUND_EVENTS.register(bus);
    }
}
