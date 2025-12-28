package com.example.mobcapture;

import com.example.mobcapture.registry.ModBlockEntities;
import com.example.mobcapture.registry.ModBlocks;
import com.example.mobcapture.registry.ModItems;
import com.example.mobcapture.registry.ModSounds;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(MobCaptureMod.MODID)
public class MobCaptureMod {
    public static final String MODID = "mobcapture";

    public MobCaptureMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.register(modBus);
        ModBlocks.register(modBus);
        ModBlockEntities.register(modBus);
        ModSounds.register(modBus);

        // ✅ 크리에이티브 탭 이벤트 등록
        modBus.addListener(this::addCreative);
    }

    // ✅ 크리에이티브 탭에 아이템 추가
    private void addCreative(BuildCreativeModeTabContentsEvent event) {

        // 원하는 탭 선택 (건축 블록 탭이 가장 무난)
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {

            // 🟦 스폰포인트 (DungeonSpawner)
            event.accept(ModBlocks.DUNGEON_SPAWNER_ITEM.get());

            // 🟥 Room Controller
            event.accept(ModBlocks.ROOM_CONTROLLER_ITEM.get());
        }
    }
}
