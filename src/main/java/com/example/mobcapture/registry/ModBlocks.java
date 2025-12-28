package com.example.mobcapture.registry;

import com.example.mobcapture.MobCaptureMod;
import com.example.mobcapture.block.DungeonSpawnerBlock;
import com.example.mobcapture.block.RoomControllerBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = MobCaptureMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MobCaptureMod.MODID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MobCaptureMod.MODID);

    public static final RegistryObject<Block> DUNGEON_SPAWNER =
            BLOCKS.register("dungeon_spawner",
                    () -> new DungeonSpawnerBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.5f)
                            .requiresCorrectToolForDrops()
                    ));

    public static final RegistryObject<Item> DUNGEON_SPAWNER_ITEM =
            ITEMS.register("dungeon_spawner",
                    () -> new BlockItem(DUNGEON_SPAWNER.get(), new Item.Properties()));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }

    @SubscribeEvent
    public static void addCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(DUNGEON_SPAWNER_ITEM.get());
        }
    }

    public static final RegistryObject<Block> ROOM_CONTROLLER =
            BLOCKS.register("room_controller",
                    () -> new RoomControllerBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.5f)
                            .requiresCorrectToolForDrops()
                    ));

    public static final RegistryObject<Item> ROOM_CONTROLLER_ITEM =
            ITEMS.register("room_controller",
                    () -> new BlockItem(ROOM_CONTROLLER.get(), new Item.Properties()));
}
