package com.example.mobcapture.registry;

import com.example.mobcapture.MobCaptureMod;
import com.example.mobcapture.block.DungeonSpawnerBlock;
import com.example.mobcapture.block.RoomControllerBlock;
import com.example.mobcapture.block.TeleportBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {

    // 블록 레지스트리
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MobCaptureMod.MODID);

    // 블록아이템 레지스트리
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MobCaptureMod.MODID);

    /* ===================== BLOCKS ===================== */

    public static final RegistryObject<Block> DUNGEON_SPAWNER =
            BLOCKS.register("dungeon_spawner",
                    () -> new DungeonSpawnerBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.EMERALD)
                            .strength(2.0f, 6.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()
                    )
            );

    public static final RegistryObject<Block> ROOM_CONTROLLER =
            BLOCKS.register("room_controller",
                    () -> new RoomControllerBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_CYAN)
                            .strength(2.0f, 6.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()
                    )
            );

    /* ===================== BLOCK ITEMS ===================== */

    public static final RegistryObject<Item> DUNGEON_SPAWNER_ITEM =
            ITEMS.register("dungeon_spawner",
                    () -> new BlockItem(DUNGEON_SPAWNER.get(), new Item.Properties())
            );

    public static final RegistryObject<Item> ROOM_CONTROLLER_ITEM =
            ITEMS.register("room_controller",
                    () -> new BlockItem(ROOM_CONTROLLER.get(), new Item.Properties())
            );

    /* ===================== REGISTER ===================== */

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }

    public static final RegistryObject<Block> TELEPORT_BLOCK =
            BLOCKS.register("teleport_block",
                    () -> new TeleportBlock(BlockBehaviour.Properties.of()
                            .strength(2.0f)
                            .lightLevel(state -> 15)  // ✅ 항상 발광 (0~15)
                            .noOcclusion()
                    ));

    public static final RegistryObject<Item> TELEPORT_BLOCK_ITEM =
            ITEMS.register("teleport_block",
                    () -> new BlockItem(TELEPORT_BLOCK.get(), new Item.Properties()));

}
