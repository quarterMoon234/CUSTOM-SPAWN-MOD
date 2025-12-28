package com.example.mobcapture.registry;

import com.example.mobcapture.MobCaptureMod;
import com.example.mobcapture.item.CaptureEggItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = MobCaptureMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MobCaptureMod.MODID);

    // 캡처알 (NBT로 빈 알 / 채워진 알 구분)
    public static final RegistryObject<Item> CAPTURE_EGG =
            ITEMS.register("capture_egg",
                    () -> new CaptureEggItem(new Item.Properties().stacksTo(16)));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    // 테스트용: 크리에이티브 탭에 표시
    @SubscribeEvent
    public static void addCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(CAPTURE_EGG.get());
        }
    }
}