package com.example.mobcapture.registry;

import com.example.mobcapture.MobCaptureMod;
import com.example.mobcapture.blockentity.DungeonSpawnerBlockEntity;
import com.example.mobcapture.blockentity.RoomControllerBlockEntity;
import com.example.mobcapture.blockentity.TeleportBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MobCaptureMod.MODID);

    public static final RegistryObject<BlockEntityType<DungeonSpawnerBlockEntity>> DUNGEON_SPAWNER_BE =
            BLOCK_ENTITIES.register("dungeon_spawner",
                    () -> BlockEntityType.Builder.of(
                            DungeonSpawnerBlockEntity::new,
                            ModBlocks.DUNGEON_SPAWNER.get()
                    ).build(null));

    public static final RegistryObject<BlockEntityType<RoomControllerBlockEntity>> ROOM_CONTROLLER_BE =
            BLOCK_ENTITIES.register("room_controller",
                    () -> BlockEntityType.Builder.of(
                            RoomControllerBlockEntity::new,
                            ModBlocks.ROOM_CONTROLLER.get()
                    ).build(null));

    public static final RegistryObject<BlockEntityType<TeleportBlockEntity>> TELEPORT_BLOCK_BE =
            BLOCK_ENTITIES.register("teleport_block",
                    () -> BlockEntityType.Builder.of(
                            TeleportBlockEntity::new,
                            ModBlocks.TELEPORT_BLOCK.get()
                    ).build(null));


    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
